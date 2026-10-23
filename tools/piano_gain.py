"""Offline loudness calibration shared by the piano bank build/check tools.

The bank keeps the original PCM16 WAV files. Only the per-sample ``gain`` in
``instrument.json`` changes. Measurements use the BS.1770 48 kHz K-weighting
pre-filter and RLB filter. A short attack window dominates the metric so that
high notes do not get boosted just because their early RMS is low.
"""
from __future__ import annotations

import array
import json
import math
import statistics
import wave
from dataclasses import dataclass
from pathlib import Path

OUTPUT_RATE = 48000
ATTACK_SECONDS = 0.05
SUSTAIN_SECONDS = 0.25
ATTACK_WEIGHT = 0.7
SUSTAIN_WEIGHT = 0.3
MAX_GAIN = 10.0
PEAK_WINDOW_SECONDS = 0.05
FULL_PEAK_SECONDS = 0.5

# BS.1770 48 kHz coefficients.
_B1 = (1.53512485958697, -2.69169618940638, 1.19839281085285)
_A1 = (1.0, -1.69065929318241, 0.73248077421585)
_B2 = (1.0, -2.0, 1.0)
_A2 = (1.0, -1.99004745483398, 0.99007225036621)


@dataclass(frozen=True)
class Measurement:
    attack_energy: float
    sustain_energy: float
    attack_peak: float
    full_peak: float


@dataclass(frozen=True)
class Calibration:
    gains: dict[tuple[int, str], float]
    report: dict


def _biquad(values: list[float], b: tuple[float, ...], a: tuple[float, ...]) -> list[float]:
    out = [0.0] * len(values)
    x1 = x2 = y1 = y2 = 0.0
    b0, b1, b2 = b
    _, a1, a2 = a
    for index, value in enumerate(values):
        filtered = b0 * value + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2, x1 = x1, value
        y2, y1 = y1, filtered
        out[index] = filtered
    return out


def _read_wav(path: Path) -> tuple[int, int, array.array]:
    with wave.open(str(path), "rb") as source:
        if source.getsampwidth() != 2:
            raise ValueError(f"{path.name}: expected 16-bit PCM")
        rate = source.getframerate()
        channels = source.getnchannels()
        frames = source.getnframes()
        pcm = array.array("h")
        pcm.frombytes(source.readframes(frames))
    return rate, channels, pcm


def _channels_at_48k(rate: int, channels: int, pcm: array.array, seconds: float) -> list[list[float]]:
    frames = min(len(pcm) // channels, max(1, int(rate * seconds)))
    values = [[pcm[index] / 32768.0 for index in range(channel, frames * channels, channels)] for channel in range(channels)]
    if rate == OUTPUT_RATE:
        return values
    ratio = OUTPUT_RATE / rate
    count = max(1, int(len(values[0]) * ratio))
    return [[channel[min(len(channel) - 1, int(index / ratio))] for index in range(count)] for channel in values]


def _energy(values: list[list[float]], seconds: float) -> float:
    count = max(1, int(OUTPUT_RATE * seconds))
    total = 0.0
    samples = 0
    for channel in values:
        filtered = _biquad(_biquad(channel, _B1, _A1), _B2, _A2)
        frames = min(len(filtered), count)
        total += sum(value * value for value in filtered[:frames])
        samples += frames
    return total / samples if samples else 0.0


def _peak(values: list[list[float]], seconds: float) -> float:
    count = min(len(values[0]), max(1, int(OUTPUT_RATE * seconds)))
    return max((abs(value) for channel in values for value in channel[:count]), default=0.0)


def measure(path: Path) -> Measurement:
    rate, channels, pcm = _read_wav(path)
    values = _channels_at_48k(rate, channels, pcm, FULL_PEAK_SECONDS)
    return Measurement(
        attack_energy=_energy(values, ATTACK_SECONDS),
        sustain_energy=_energy(values, SUSTAIN_SECONDS),
        attack_peak=_peak(values, PEAK_WINDOW_SECONDS),
        full_peak=_peak(values, FULL_PEAK_SECONDS),
    )


def metric(measurement: Measurement) -> float:
    return ATTACK_WEIGHT * measurement.attack_energy + SUSTAIN_WEIGHT * measurement.sustain_energy


def _layer(entry: dict) -> str:
    return str(entry.get("layer", "loud"))


def _root(entry: dict) -> int:
    return int(entry["rootMidi"])


def _smooth_by_layer(gains: dict[tuple[int, str], float], layers: tuple[str, ...],
                     smoothing: tuple[float, ...]) -> dict[tuple[int, str], float]:
    result = dict(gains)
    radius = len(smoothing) // 2
    for layer in layers:
        roots = sorted(root for root, candidate in gains if candidate == layer)
        values = [gains[(root, layer)] for root in roots]
        for index, root in enumerate(roots):
            total = 0.0
            weight_sum = 0.0
            for offset, weight in enumerate(smoothing):
                neighbour = index + offset - radius
                if 0 <= neighbour < len(values):
                    total += math.log(values[neighbour]) * weight
                    weight_sum += weight
            result[(root, layer)] = math.exp(total / weight_sum)
    return result


def _layer_trim(gains: dict[tuple[int, str], float], measurements: dict[tuple[int, str], Measurement],
                layers: tuple[str, ...], target: float) -> dict[tuple[int, str], float]:
    result = dict(gains)
    for layer in layers:
        values = [metric(measurements[(root, layer)]) * gains[(root, layer)] ** 2
                  for root, candidate in gains if candidate == layer]
        if not values:
            continue
        trim = math.sqrt(target / statistics.median(values))
        for root, candidate in gains:
            if candidate == layer:
                result[(root, layer)] = gains[(root, layer)] * trim
    return result


def calibrate(entries: list[dict], base_dir: Path, target_layer: str = "medium", max_gain: float = MAX_GAIN,
              smoothing: tuple[float, ...] | None = None) -> Calibration:
    """Return new per-sample gains and a JSON-friendly before/after report."""
    measurements: dict[tuple[int, str], Measurement] = {}
    old_gains: dict[tuple[int, str], float] = {}
    for entry in entries:
        key = (_root(entry), _layer(entry))
        measurements[key] = measure(base_dir / entry["file"])
        gain = float(entry.get("gain", 1.0))
        if not math.isfinite(gain) or gain <= 0:
            raise ValueError(f"invalid gain for {entry['file']}")
        old_gains[key] = gain

    layers = tuple(dict.fromkeys(_layer(entry) for entry in entries))
    reference = target_layer if target_layer in layers else layers[-1]
    target = statistics.median(
        metric(measurements[key]) * old_gains[key] ** 2
        for key in old_gains if key[1] == reference
    )
    if target <= 0:
        raise ValueError("no usable calibration reference")

    gains = {key: math.sqrt(target / metric(measurement)) for key, measurement in measurements.items()}
    if smoothing is not None:
        gains = _smooth_by_layer(gains, layers, smoothing)
    gains = _layer_trim(gains, measurements, layers, target)
    gains = {key: min(max_gain, max(0.01, value)) for key, value in gains.items()}

    def summary(candidate: dict[tuple[int, str], float]) -> dict:
        result = {}
        for layer in layers:
            combined = [10 * math.log10(max(metric(measurements[(root, layer)]) * candidate[(root, layer)] ** 2, 1e-18))
                        for root, named in candidate if named == layer]
            attack = [10 * math.log10(max(measurements[(root, layer)].attack_energy * candidate[(root, layer)] ** 2, 1e-18))
                      for root, named in candidate if named == layer]
            sustain = [10 * math.log10(max(measurements[(root, layer)].sustain_energy * candidate[(root, layer)] ** 2, 1e-18))
                       for root, named in candidate if named == layer]
            result[layer] = {
                "metricSpreadDb": round(max(combined) - min(combined), 3),
                "attackSpreadDb": round(max(attack) - min(attack), 3),
                "sustainSpreadDb": round(max(sustain) - min(sustain), 3),
                "medianMetricDb": round(statistics.median(combined), 3),
                "medianAttackDb": round(statistics.median(attack), 3),
                "medianSustainDb": round(statistics.median(sustain), 3),
            }
        return result

    before = {key: old_gains[key] for key in old_gains}
    report = {
        "sampleRate": OUTPUT_RATE,
        "attackSeconds": ATTACK_SECONDS,
        "sustainSeconds": SUSTAIN_SECONDS,
        "attackWeight": ATTACK_WEIGHT,
        "sustainWeight": SUSTAIN_WEIGHT,
        "targetLayer": reference,
        "maxGain": max_gain,
        "before": summary(before),
        "after": summary(gains),
        "maxGainAfter": round(max(gains.values()), 6),
        "maxAttackPeakAfter": round(max(measurements[key].attack_peak * gains[key] for key in gains), 6),
        "maxFullPeakAfter": round(max(measurements[key].full_peak * gains[key] for key in gains), 6),
    }
    return Calibration(gains=gains, report=report)


def apply(manifest: dict, base_dir: Path, target_layer: str = "medium") -> dict:
    entries = manifest["samples"]
    calibration = calibrate(entries, base_dir, target_layer)
    for entry in entries:
        entry["gain"] = round(calibration.gains[(_root(entry), _layer(entry))], 6)
    manifest["gainCalibration"] = {
        "version": 1,
        "method": "bs1770-attack70-sustain30",
        "attackSeconds": ATTACK_SECONDS,
        "sustainSeconds": SUSTAIN_SECONDS,
        "attackWeight": ATTACK_WEIGHT,
        "sustainWeight": SUSTAIN_WEIGHT,
        "targetLayer": calibration.report["targetLayer"],
        "maxGain": calibration.report["maxGain"],
    }
    return calibration.report


def load_manifest(base_dir: Path) -> dict:
    return json.loads((base_dir / "instrument.json").read_text(encoding="utf-8"))


def write_manifest(base_dir: Path, manifest: dict) -> None:
    (base_dir / "instrument.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
        newline="\n",
    )
