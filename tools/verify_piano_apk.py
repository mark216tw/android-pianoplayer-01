"""Verify the built APK contains every bundled sample bank and its notices."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

BANKS = {
    "piano": {"manifest": "piano", "samples": "piano", "count": 90, "layers": 3,
              "coverage": (21, 108), "attribution": b"Alexander Holm", "license": b"Attribution 3.0"},
    "violin": {"manifest": "violin", "samples": "violin", "count": 30, "layers": 2,
               "coverage": (21, 108), "tone": (84, 108), "outputGain": 1.0,
               "attribution": b"VSCO 2 Community Edition", "license": b"CC0 1.0",
               "license_extra": b"Attribution 3.0"},
    "violin-spic": {"manifest": "violin-spic", "samples": "violin-spic", "count": 20, "layers": 2,
                    "coverage": (21, 96), "tone": (84, 96), "outputGain": 1.0,
                    "attribution": b"VSCO 2 Community Edition", "license": b"CC0 1.0",
                    "license_extra": b"Attribution 3.0"},
    "piano-low": {"manifest": "piano-low", "samples": "piano", "count": 36, "layers": 3,
                  "coverage": (21, 54), "attribution": b"Alexander Holm", "license": b"Attribution 3.0"},
}


def verify(apk, bank, expected):
    manifest_prefix = f"assets/{expected['manifest']}/"
    sample_prefix = f"assets/{expected['samples']}/"
    manifest = json.loads(apk.read(manifest_prefix + "instrument.json"))
    entries = manifest["samples"]
    if len(entries) != expected["count"]:
        raise ValueError(f"{bank}: incomplete sample bank")
    low, high = expected["coverage"]
    if (manifest.get("coverageLowMidi", 21), manifest.get("coverageHighMidi", 108)) != (low, high):
        raise ValueError(f"{bank}: unexpected coverage")
    if "tone" in expected and (manifest.get("toneStartMidi"), manifest.get("toneEndMidi")) != expected["tone"]:
        raise ValueError(f"{bank}: unexpected tone range")
    if "outputGain" in expected and abs(float(manifest.get("outputGain", 1)) - expected["outputGain"]) > 1e-9:
        raise ValueError(f"{bank}: unexpected output gain")
    raw = compressed = 0
    layers = {}
    for entry in entries:
        data = apk.read(sample_prefix + entry["file"])
        if hashlib.sha256(data).hexdigest() != entry["sha256"]:
            raise ValueError(f"{bank}: sample hash mismatch: {entry['file']}")
        info = apk.getinfo(sample_prefix + entry["file"])
        raw += info.file_size
        compressed += info.compress_size
        layers.setdefault(entry.get("layer", "loud"), set()).update(range(entry["lowMidi"], entry["highMidi"] + 1))
    if len(layers) != expected["layers"] or any(covered != set(range(low, high + 1)) for covered in layers.values()):
        raise ValueError(f"{bank}: incomplete key coverage")
    attribution = apk.read(manifest_prefix + "ATTRIBUTION.txt")
    license_text = apk.read(manifest_prefix + "LICENSE.txt")
    if expected["attribution"] not in attribution or expected["license"] not in license_text:
        raise ValueError(f"{bank}: missing sample attribution/license")
    if "license_extra" in expected and expected["license_extra"] not in license_text:
        raise ValueError(f"{bank}: missing combined sample license")
    print(f"Verified {bank}: {len(entries)} note samples, {len(layers)} velocity layers, "
          f"MIDI {low}-{high}, SHA-256, attribution and {manifest['license']}.")
    print(f"  {bank} assets: {raw / 1024**2:.2f} MiB raw; stored in APK: {compressed / 1024**2:.2f} MiB; "
          f"decoded 16-bit PCM: {sum(e['frames'] * e.get('channels', 2) * 2 for e in entries) / 1024**2:.2f} MiB.")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", nargs="?", type=Path, default=Path("app/build/outputs/apk/debug/app-debug.apk"))
    args = parser.parse_args()
    with zipfile.ZipFile(args.apk) as apk:
        for bank, expected in BANKS.items():
            verify(apk, bank, expected)
        print(f"APK total: {args.apk.stat().st_size / 1024**2:.2f} MiB.")


if __name__ == "__main__":
    main()
