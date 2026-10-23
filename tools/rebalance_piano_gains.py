"""Recompute the bundled piano bank's per-sample gains from the existing WAVs.

This does not modify any WAV file, so the manifest SHA-256 hashes stay valid.
Run from the workspace root:

    python tools/rebalance_piano_gains.py --dry-run
    python tools/rebalance_piano_gains.py
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import piano_gain


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--bank", type=Path, default=Path("app/src/main/assets/piano"))
    parser.add_argument("--target-layer", default="medium")
    parser.add_argument("--report", type=Path)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    manifest = piano_gain.load_manifest(args.bank)
    report = piano_gain.apply(manifest, args.bank, args.target_layer)
    text = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.report:
        args.report.write_text(text, encoding="utf-8", newline="\n")
    if args.dry_run:
        print(text)
        return
    piano_gain.write_manifest(args.bank, manifest)
    print(text)


if __name__ == "__main__":
    main()
