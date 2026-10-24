"""Build the offline violin banks and low-range piano fallback.

Arco Vib (sustained) and Spiccato (short) samples come from a pinned VSCO 2 CE
revision. The low-range fallback reuses the existing Salamander piano samples
for MIDI 21-54. Source WAVs are cached outside the repo. Dependencies:
numpy, scipy, soundfile. Run from the workspace root:

    python tools/prepare_violin.py --cache <directory>
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import math
from pathlib import Path
import statistics
import time
import urllib.parse
import urllib.request

import numpy as np
from scipy.signal import resample_poly
import soundfile as sf

import piano_gain

REVISION = "440300901dfe9275fd84e0b7763af1f8443ae62e"
REPO = "sgossner/VSCO-2-CE"
ARCO_DIR = "Strings/Solo Violin/Arco Vib"
SPIC_DIR = "Strings/Solo Violin/spic"
ARCO_ROOTS = [55, 57, 60, 64, 67, 69, 72, 76, 79, 81, 84, 88, 91, 93, 96]
SPIC_ROOTS = [55, 57, 60, 64, 67, 69, 72, 76, 79, 84]
SPIC_LOW = 21
SPIC_HIGH = 96
ARCO_LAYERS = [("soft", "p", 1, 63), ("loud", "f", 64, 127)]
SPIC_LAYERS = [("soft", "v1", 1, 63), ("loud", "v2", 64, 127)]
SOURCE_RATE = 44100
OUTPUT_RATE = 44100
DURATION_LOW = 4.5
DURATION_MIDDLE = 4.0
DURATION_HIGH = 3.5
SPIC_DURATION = 1.5
ARCO_TAIL_FADE = .12
SPIC_TAIL_FADE = .08
ARCO_RELEASE = .05
SPIC_RELEASE = .03
OUTPUT_GAIN = 1.0
SOFT_TRIM_THRESHOLD = .25
SOFT_TRIM_LIMIT = .7
SOFT_FADE_IN = .015
NATURAL_LOW = min(ARCO_ROOTS)
NATURAL_HIGH = max(ARCO_ROOTS)
LOW_PIANO_LOW = 21
LOW_PIANO_HIGH = 55 - 1
ARCO_ATTRIBUTION = """VSCO 2 Community Edition (CE) \u2014 Solo Violin

Arco Vib (sustained) and Spiccato (short) samples.
Recorded by Sam Gossner & Simon Dalzell.
Sample cutting by Elan Hickler / Soundemote.
Source: https://github.com/sgossner/VSCO-2-CE
Pinned revision: {revision}

License: Creative Commons CC0 1.0 Universal (public domain dedication).
License URL: https://creativecommons.org/publicdomain/zero/1.0/
Full license: LICENSE.txt in this directory.

Low-range fallback (MIDI 21-54) uses the bundled Salamander Grand Piano samples:
Original piano recordings by Alexander Holm (Yamaha C5), mapped by kinwie.
Source: https://github.com/sfzinstruments/SalamanderGrandPiano
License: Creative Commons Attribution 3.0 Unported (CC BY 3.0).
Full piano license: included in LICENSE.txt in this directory.

This mobile sound bank is derived from the above instruments.
Changes: selected Arco Vib p/f and Spiccato v1/v2 rr1; trimmed Arco tails to
4.5/4.0/3.5 seconds and Spiccato tails to 1.5 seconds; shortened the soft Arco
onset; resampled everything to 44.1 kHz; applied a K-weighted per-key
attack/sustain loudness calibration; and converted to 16-bit PCM WAV. Arco and
Spiccato use a 0.05 / 0.03 second release and ignore the piano sustain pedal.
The original authors do not endorse this application. The sample licenses do
not apply to the unrelated application source code.
"""


def notes(root):
    names = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"]
    return f"{names[root % 12]}{root // 12 - 1}"


def arco_source(root, suffix):
    return f"LLVln_ArcoVib_{notes(root)}_{suffix}.wav"


def spic_source(root, suffix):
    return f"LLVln_spic_{notes(root)}_{suffix}_rr1.wav"


def duration(root):
    if root <= 60:
        return DURATION_LOW
    if root <= 72:
        return DURATION_MIDDLE
    return DURATION_HIGH


def resample(data, rate_out):
    divisor = math.gcd(SOURCE_RATE, rate_out)
    return resample_poly(data, rate_out // divisor, SOURCE_RATE // divisor).astype(np.float32)


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def download(url, path):
    if path.exists():
        return path
    for attempt in range(3):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": "mini-piano-player-build"})
            with urllib.request.urlopen(request, timeout=120) as response:
                data = response.read()
            path.write_bytes(data)
            return path
        except Exception:
            if attempt == 2:
                raise
            time.sleep(attempt + 1)


def zones(roots, low=21, high=108):
    result = []
    for index, root in enumerate(roots):
        zone_low = low if index == 0 else (roots[index - 1] + root) // 2 + 1
        zone_high = high if index == len(roots) - 1 else (root + roots[index + 1]) // 2
        result.append((root, zone_low, zone_high))
    return result


def normalize(data, target_rms=.12):
    rms = float(np.sqrt(np.mean(np.square(data))))
    if rms > 0:
        data *= target_rms / rms
    peak = float(np.max(np.abs(data)))
    if peak > .95:
        data *= .95 / peak
    return data


def trim_soft_onset(data, rate):
    window = max(1, int(rate * .01))
    mono = data.mean(axis=1)
    frames = len(mono) // window
    if frames < 1:
        return data
    rms = np.sqrt(np.mean(mono[:frames * window].reshape(frames, window) ** 2, axis=1))
    peak = float(rms.max())
    if peak <= 0:
        return data
    index = np.flatnonzero(rms >= peak * SOFT_TRIM_THRESHOLD)
    offset = 0 if len(index) == 0 else int(index[0]) * window
    offset = min(offset, int(rate * SOFT_TRIM_LIMIT), max(0, len(data) - int(rate * .5)))
    if offset <= 0:
        return data
    result = data[offset:].copy()
    fade = min(len(result), int(rate * SOFT_FADE_IN))
    result[:fade] *= np.linspace(0, 1, fade, dtype=np.float32)[:, None]
    return result


def read_prepared(cache, source_dir, source_name, seconds, rate_out, tail_fade):
    url = f"https://raw.githubusercontent.com/{REPO}/{REVISION}/{urllib.parse.quote(source_dir)}/{urllib.parse.quote(source_name)}"
    path = download(url, cache / source_name)
    data, rate = sf.read(path, dtype="float32", always_2d=True)
    if rate != SOURCE_RATE or data.shape[1] not in (1, 2):
        raise ValueError(f"Unexpected upstream format: {source_name}, {rate}, {data.shape}")
    data = data[:min(len(data), int(seconds * rate))]
    data = resample(data, rate_out)
    fade = min(len(data), int(rate_out * tail_fade))
    data[-fade:] *= np.linspace(1, 0, fade, dtype=np.float32)[:, None]
    return path, data


def make_entries(manifest_entries, root, low, high, layer, source_path, source_file, data, rate_out, destination):
    name = f"note_{root:03d}_{layer}.wav"
    output = destination / name
    sf.write(output, data, rate_out, subtype="PCM_16")
    manifest_entries.append({
        "file": name,
        "layer": layer,
        "rootMidi": root,
        "lowMidi": low,
        "highMidi": high,
        "rate": rate_out,
        "channels": int(data.shape[1]),
        "frames": int(len(data)),
        "gain": 1.0,
        "sourceFile": source_file,
        "sourceSha256": sha256(source_path),
        "sha256": sha256(output),
    })


def calibrate(entries, destination, target_layer, max_gain=40):
    calibration = piano_gain.calibrate(entries, destination, target_layer=target_layer, max_gain=max_gain)
    for entry in entries:
        entry["gain"] = round(calibration.gains[(int(entry["rootMidi"]), str(entry["layer"]))], 6)
    return calibration


def gain_metadata(calibration):
    return {
        "version": 1,
        "method": "bs1770-attack70-sustain30",
        "attackSeconds": piano_gain.ATTACK_SECONDS,
        "sustainSeconds": piano_gain.SUSTAIN_SECONDS,
        "attackWeight": piano_gain.ATTACK_WEIGHT,
        "sustainWeight": piano_gain.SUSTAIN_WEIGHT,
        "targetLayer": calibration.report["targetLayer"],
        "maxGain": calibration.report["maxGain"],
    }


def refresh_report(entries, destination, report):
    max_gain = max_attack = max_full = 0.0
    for entry in entries:
        measurement = piano_gain.measure(destination / entry["file"])
        gain = float(entry["gain"])
        max_gain = max(max_gain, gain)
        max_attack = max(max_attack, measurement.attack_peak * gain)
        max_full = max(max_full, measurement.full_peak * gain)
    report["maxGainAfter"] = round(max_gain, 6)
    report["maxAttackPeakAfter"] = round(max_attack, 6)
    report["maxFullPeakAfter"] = round(max_full, 6)


def apply_peak_limit(entries, report, peak_limit=.9):
    peak = float(report.get("maxFullPeakAfter", 0))
    if peak > peak_limit:
        scale = peak / peak_limit
        for entry in entries:
            entry["gain"] = round(float(entry["gain"]) / scale, 6)
        report["maxFullPeakAfter"] = round(peak / scale, 6)
        report["globalAttenuation"] = round(scale, 6)


def layer_median(entries, destination, layer):
    values = []
    for entry in entries:
        if entry["layer"] != layer:
            continue
        measurement = piano_gain.measure(destination / entry["file"])
        values.append(piano_gain.metric(measurement) * float(entry["gain"]) ** 2)
    return statistics.median(values)


def prepare_arco(cache, destination):
    def prepare(item):
        root, low, high = item
        path_p, data_p = read_prepared(cache, ARCO_DIR, arco_source(root, "p"), duration(root), OUTPUT_RATE, ARCO_TAIL_FADE)
        path_f, data_f = read_prepared(cache, ARCO_DIR, arco_source(root, "f"), duration(root), OUTPUT_RATE, ARCO_TAIL_FADE)
        data_p = normalize(trim_soft_onset(data_p, OUTPUT_RATE))
        data_f = normalize(data_f)
        return root, low, high, path_p, path_f, data_p, data_f

    with ThreadPoolExecutor(max_workers=4) as executor:
        prepared = list(executor.map(prepare, zones(ARCO_ROOTS)))
    for old in destination.glob("note_*.wav"):
        old.unlink()
    entries = []
    for root, low, high, path_p, path_f, data_p, data_f in prepared:
        make_entries(entries, root, low, high, "soft", path_p, arco_source(root, "p"), data_p, OUTPUT_RATE, destination)
        make_entries(entries, root, low, high, "loud", path_f, arco_source(root, "f"), data_f, OUTPUT_RATE, destination)
    calibration = calibrate(entries, destination, "loud")
    report = calibration.report
    apply_peak_limit(entries, report)
    refresh_report(entries, destination, report)
    manifest = {
        "version": 2,
        "format": "wav",
        "name": "VSCO 2 CE Solo Violin - Arco Vib mobile edition",
        "author": "Sam Gossner & Simon Dalzell",
        "sampleCutting": "Elan Hickler / Soundemote",
        "license": "CC0 1.0",
        "licenseUrl": "https://creativecommons.org/publicdomain/zero/1.0/",
        "source": "https://github.com/sgossner/VSCO-2-CE",
        "revision": REVISION,
        "sampleRate": OUTPUT_RATE,
        "channels": 2,
        "velocityLayers": len(ARCO_LAYERS),
        "layers": [{"name": name, "low": low, "high": high} for name, _, low, high in ARCO_LAYERS],
        "releaseSeconds": ARCO_RELEASE,
        "sustainPedal": False,
        "outputGain": OUTPUT_GAIN,
        "naturalLowMidi": NATURAL_LOW,
        "naturalHighMidi": NATURAL_HIGH,
        "coverageLowMidi": 21,
        "coverageHighMidi": 108,
        "gainApplied": 1.0,
        "gainCalibrated": True,
        "changes": "Selected Arco Vib p/f layers; shortened the soft layer's slow onset; roots G3-C7 zoned across MIDI 21-108; 44.1 kHz; 120 ms tail fade; K-weighted per-key 50 ms attack / 250 ms sustain loudness calibration; PCM16 WAV. Notes outside G3-C7 are pitch shifted. High notes use a pitch-dependent low-pass tone control.",
        "toneStartMidi": 84,
        "toneEndMidi": 108,
        "toneStartHz": 16000,
        "toneEndHz": 5500,
        "samples": entries,
        "gainCalibration": gain_metadata(calibration),
    }
    return manifest, report


def prepare_spic(cache, destination):
    def prepare(item):
        root, low, high = item
        path_soft, data_soft = read_prepared(cache, SPIC_DIR, spic_source(root, "v1"), SPIC_DURATION, OUTPUT_RATE, SPIC_TAIL_FADE)
        path_loud, data_loud = read_prepared(cache, SPIC_DIR, spic_source(root, "v2"), SPIC_DURATION, OUTPUT_RATE, SPIC_TAIL_FADE)
        return root, low, high, path_soft, path_loud, normalize(data_soft), normalize(data_loud)

    with ThreadPoolExecutor(max_workers=4) as executor:
        prepared = list(executor.map(prepare, zones(SPIC_ROOTS, SPIC_LOW, SPIC_HIGH)))
    for old in destination.glob("note_*.wav"):
        old.unlink()
    entries = []
    for root, low, high, path_soft, path_loud, data_soft, data_loud in prepared:
        make_entries(entries, root, low, high, "soft", path_soft, spic_source(root, "v1"), data_soft, OUTPUT_RATE, destination)
        make_entries(entries, root, low, high, "loud", path_loud, spic_source(root, "v2"), data_loud, OUTPUT_RATE, destination)
    calibration = calibrate(entries, destination, "loud")
    report = calibration.report
    apply_peak_limit(entries, report)
    refresh_report(entries, destination, report)
    manifest = {
        "version": 2,
        "format": "wav",
        "name": "VSCO 2 CE Solo Violin - Spiccato mobile edition",
        "author": "Sam Gossner & Simon Dalzell",
        "sampleCutting": "Elan Hickler / Soundemote",
        "license": "CC0 1.0",
        "licenseUrl": "https://creativecommons.org/publicdomain/zero/1.0/",
        "source": "https://github.com/sgossner/VSCO-2-CE",
        "revision": REVISION,
        "sampleRate": OUTPUT_RATE,
        "channels": 2,
        "velocityLayers": len(SPIC_LAYERS),
        "layers": [{"name": name, "low": low, "high": high} for name, _, low, high in SPIC_LAYERS],
        "releaseSeconds": SPIC_RELEASE,
        "sustainPedal": False,
        "outputGain": OUTPUT_GAIN,
        "naturalLowMidi": min(SPIC_ROOTS),
        "naturalHighMidi": max(SPIC_ROOTS),
        "coverageLowMidi": SPIC_LOW,
        "coverageHighMidi": SPIC_HIGH,
        "toneStartMidi": 84,
        "toneEndMidi": SPIC_HIGH,
        "toneStartHz": 16000,
        "toneEndHz": 8000,
        "gainApplied": 1.0,
        "gainCalibrated": True,
        "changes": "Selected Spiccato v1/v2 rr1; roots G3-C6 zoned across MIDI 21-96 with low/high transposition; 44.1 kHz; 1.5 s trim with 80 ms fade; K-weighted loudness calibration; high-note low-pass; PCM16 WAV.",
        "samples": entries,
        "gainCalibration": gain_metadata(calibration),
    }
    return manifest, report


def prepare_low_piano(destination):
    root = Path(__file__).resolve().parents[1]
    source_dir = root / "app/src/main/assets/piano"
    source = json.loads((source_dir / "instrument.json").read_text(encoding="utf-8"))
    entries = []
    for entry in source["samples"]:
        low = int(entry["lowMidi"])
        high = int(entry["highMidi"])
        if low > LOW_PIANO_HIGH:
            continue
        copy = dict(entry)
        copy["lowMidi"] = max(LOW_PIANO_LOW, low)
        copy["highMidi"] = min(LOW_PIANO_HIGH, high)
        if copy["rootMidi"] < copy["lowMidi"] or copy["rootMidi"] > copy["highMidi"]:
            continue
        entries.append(copy)
    for layer in {str(entry["layer"]) for entry in entries}:
        covered = set()
        for entry in entries:
            if entry["layer"] == layer:
                covered.update(range(entry["lowMidi"], entry["highMidi"] + 1))
        missing = set(range(LOW_PIANO_LOW, LOW_PIANO_HIGH + 1)) - covered
        if missing:
            raise ValueError(f"low piano layer {layer} missing {sorted(missing)}")
    manifest = {
        "version": 2,
        "format": "wav",
        "name": "Salamander Grand Piano - low-range fallback",
        "author": source.get("author", "Alexander Holm"),
        "license": source.get("license", "CC BY 3.0"),
        "licenseUrl": source.get("licenseUrl", "https://creativecommons.org/licenses/by/3.0/"),
        "source": source.get("source", "https://github.com/sfzinstruments/SalamanderGrandPiano"),
        "revision": source.get("revision"),
        "sampleRate": source.get("sampleRate", 48000),
        "channels": source.get("channels", 2),
        "velocityLayers": source.get("velocityLayers", 3),
        "layers": source.get("layers"),
        "releaseSeconds": 0,
        "sustainPedal": True,
        "naturalLowMidi": LOW_PIANO_LOW,
        "naturalHighMidi": LOW_PIANO_HIGH,
        "coverageLowMidi": LOW_PIANO_LOW,
        "coverageHighMidi": LOW_PIANO_HIGH,
        "gainCalibrated": source.get("gainCalibrated", True),
        "changes": "Subset of the bundled Salamander bank covering MIDI 21-54; sample files are shared with assets/piano.",
        "samples": entries,
    }
    if "gainCalibration" in source:
        manifest["gainCalibration"] = source["gainCalibration"]
    destination.mkdir(parents=True, exist_ok=True)
    (destination / "instrument.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n"
    )
    for name in ("ATTRIBUTION.txt", "LICENSE.txt"):
        (destination / name).write_bytes((source_dir / name).read_bytes())
    return manifest


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--cache", type=Path, required=True)
    parser.add_argument("--arco-report", type=Path,
                        default=Path(__file__).resolve().parents[1] / "docs/audio/violin-calibration.json")
    parser.add_argument("--spic-report", type=Path,
                        default=Path(__file__).resolve().parents[1] / "docs/audio/violin-spic-calibration.json")
    args = parser.parse_args()
    args.cache.mkdir(parents=True, exist_ok=True)
    root = Path(__file__).resolve().parents[1]
    assets = root / "app/src/main/assets"
    arco_destination = assets / "violin"
    spic_destination = assets / "violin-spic"
    low_destination = assets / "piano-low"
    arco_destination.mkdir(parents=True, exist_ok=True)
    spic_destination.mkdir(parents=True, exist_ok=True)

    license_url = f"https://raw.githubusercontent.com/{REPO}/{REVISION}/LICENSE"
    license_path = download(license_url, args.cache / "VSCO-2-CE-LICENSE.txt")

    arco_manifest, arco_report = prepare_arco(args.cache, arco_destination)
    spic_manifest, spic_report = prepare_spic(args.cache, spic_destination)
    low_manifest = prepare_low_piano(low_destination)

    arco_reference = layer_median(arco_manifest["samples"], arco_destination, "loud")
    spic_reference = layer_median(spic_manifest["samples"], spic_destination, "loud")
    if arco_reference > 0 and spic_reference > 0:
        scale = math.sqrt(arco_reference / spic_reference)
        for entry in spic_manifest["samples"]:
            entry["gain"] = round(float(entry["gain"]) * scale, 6)
        spic_report["maxFullPeakAfter"] = round(float(spic_report.get("maxFullPeakAfter", 0)) * scale, 6)
        apply_peak_limit(spic_manifest["samples"], spic_report)
        refresh_report(spic_manifest["samples"], spic_destination, spic_report)

    combined_attribution = ARCO_ATTRIBUTION.format(revision=REVISION)
    for destination in (arco_destination, spic_destination):
        (destination / "ATTRIBUTION.txt").write_text(combined_attribution, encoding="utf-8", newline="\n")
        (destination / "LICENSE.txt").write_bytes(
            license_path.read_bytes() + b"\n\n" + (assets / "piano/LICENSE.txt").read_bytes()
        )
        (destination / "instrument.json").write_text(
            json.dumps(arco_manifest if destination == arco_destination else spic_manifest,
                       ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8", newline="\n",
        )

    args.arco_report.parent.mkdir(parents=True, exist_ok=True)
    args.arco_report.write_text(json.dumps(arco_report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    args.spic_report.parent.mkdir(parents=True, exist_ok=True)
    args.spic_report.write_text(json.dumps(spic_report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(f"Wrote Arco {len(arco_manifest['samples'])} samples, Spic {len(spic_manifest['samples'])} samples, "
          f"low piano {len(low_manifest['samples'])} entries.", flush=True)


if __name__ == "__main__":
    main()
