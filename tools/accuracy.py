#!/usr/bin/env python3
"""Ground truth CSV vs measured CSV → per-row error, MAE and MAPE (spec §38).

Both files are CSVs with a header row. Rows are paired by a key column (default `test`); the
distance columns default to `ground_truth_mm` and `measured_mm`. A key present in only one file
is reported and makes the exit code 1 — a silently dropped run would flatter the result.
A row with zero ground truth counts for MAE but has no percentage error, so it is left out of MAPE.

`tools/device_accuracy.py --csv-out DIR` writes such a pair (`ground_truth.csv`, `measured.csv`).

Usage:
    python3 tools/accuracy.py GROUND_TRUTH.csv MEASURED.csv [--key test]
        [--truth-col ground_truth_mm] [--measured-col measured_mm] [--markdown]
"""

from __future__ import annotations

import argparse
import csv
import statistics
import sys
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Comparison:
    key: str
    truth_mm: float
    measured_mm: float

    @property
    def abs_error_mm(self) -> float:
        return abs(self.measured_mm - self.truth_mm)

    @property
    def pct_error(self) -> float | None:
        """|measured − truth| / truth × 100; undefined without ground truth."""
        return self.abs_error_mm / self.truth_mm * 100 if self.truth_mm > 0 else None

    @property
    def signed_pct(self) -> float | None:
        return (self.measured_mm - self.truth_mm) / self.truth_mm * 100 if self.truth_mm > 0 else None


def load(path: Path, key: str, column: str) -> dict[str, float]:
    """One value per key; a missing column or a repeated key is an error, not a guess."""
    with path.open(newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        missing = [c for c in (key, column) if c not in (reader.fieldnames or [])]
        if missing:
            raise ValueError(f"{path}: missing column(s) {missing}")
        values: dict[str, float] = {}
        for row in reader:
            k = row[key].strip()
            if k in values:
                raise ValueError(f"{path}: key {k!r} appears twice")
            values[k] = float(row[column])
        return values


def compare(truth: dict[str, float], measured: dict[str, float]) -> tuple[list[Comparison], list[str], list[str]]:
    """Paired rows in ground-truth order, keys missing from `measured`, keys missing from `truth`."""
    rows = [Comparison(k, truth[k], measured[k]) for k in truth if k in measured]
    return rows, [k for k in truth if k not in measured], [k for k in measured if k not in truth]


def mae(rows: list[Comparison]) -> float | None:
    return statistics.mean(r.abs_error_mm for r in rows) if rows else None


def mape(rows: list[Comparison]) -> float | None:
    pct = [p for p in (r.pct_error for r in rows) if p is not None]
    return statistics.mean(pct) if pct else None


def summary(rows: list[Comparison]) -> str:
    m, p = mae(rows), mape(rows)
    return (f"{len(rows)} runs · MAE {'—' if m is None else f'{m:.2f} mm'} · "
            f"MAPE {'—' if p is None else f'{p:.2f} %'}")


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("ground_truth", type=Path)
    parser.add_argument("measured", type=Path)
    parser.add_argument("--key", default="test")
    parser.add_argument("--truth-col", default="ground_truth_mm")
    parser.add_argument("--measured-col", default="measured_mm")
    parser.add_argument("--markdown", action="store_true", help="per-row table as Markdown")
    args = parser.parse_args(argv)

    try:
        truth = load(args.ground_truth, args.key, args.truth_col)
        measured = load(args.measured, args.key, args.measured_col)
    except (OSError, ValueError) as e:
        print(f"error: {e}", file=sys.stderr)
        return 2
    rows, no_measurement, no_truth = compare(truth, measured)

    if args.markdown:
        print("| Test | Ground truth mm | Measured mm | Error mm | Error % |")
        print("|---|---|---|---|---|")
    for r in rows:
        pct = "—" if r.signed_pct is None else f"{r.signed_pct:+.2f}"
        if args.markdown:
            print(f"| {r.key} | {r.truth_mm:.2f} | {r.measured_mm:.2f} | {r.abs_error_mm:.2f} | {pct} |")
        else:
            print(f"{r.key}: truth {r.truth_mm:.2f} mm · measured {r.measured_mm:.2f} mm · error {pct} %")
    print(summary(rows))
    for label, keys in (("no measurement for", no_measurement), ("no ground truth for", no_truth)):
        if keys:
            print(f"{label}: {', '.join(keys)}", file=sys.stderr)
    return 1 if no_measurement or no_truth or not rows else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
