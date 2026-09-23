#!/usr/bin/env python3
"""Analyse ScrollMeter debug CSV exports (Phase 1 GO/NO-GO).

Usage:
    python3 tools/analyze_debug_csv.py <csv-or-dir> [...]        # per-app compatibility table
    python3 tools/analyze_debug_csv.py --testlist <logcat.txt>   # accuracy from TESTLIST lines

Per package it reports the share of DIRECT / FALLBACK / SUPERSEDED / UNMEASURABLE / OUTLIER /
EXCLUDED events, the counted distance, duplicate candidates under the spec §70 rule (same package +
window_id + dx + dy within 5 ms) and the largest event against the 4 x diagonal outlier limit
(spec §11). Output is Markdown, ready for docs/accuracy-testing.md. Standard library only.
"""

from __future__ import annotations

import argparse
import csv
import math
import re
import statistics
import sys
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path

DEDUP_WINDOW_MS = 5  # spec §70; mirrors MeasurementConfig.DEDUP_WINDOW_MS
SOURCES = ("DIRECT_DELTA", "FALLBACK_POSITION", "SUPERSEDED_BY_DIRECT", "UNMEASURABLE", "OUTLIER_REJECTED", "EXCLUDED")


@dataclass
class Row:
    uptime_ms: int
    package: str
    window_id: int
    class_name: str
    dx: int
    dy: int
    used_dx: int
    used_dy: int
    distance_mm: float
    source: str

    @property
    def used_px(self) -> float:
        return math.hypot(self.used_dx, self.used_dy)


@dataclass
class Export:
    path: Path
    header: dict[str, str]
    rows: list[Row]

    @property
    def max_event_px(self) -> float | None:
        value = self.header.get("max_event_px")
        return float(value) if value else None


@dataclass
class AppStats:
    package: str
    rows: list[Row] = field(default_factory=list)
    max_event_px: float | None = None

    def count(self, source: str) -> int:
        return sum(1 for r in self.rows if r.source == source)

    @property
    def measurable(self) -> int:
        """Events that could carry distance; a superseded one is a second copy of counted motion (ADR-020)."""
        return len(self.rows) - self.count("EXCLUDED") - self.count("SUPERSEDED_BY_DIRECT")

    @property
    def counted_mm(self) -> float:
        return sum(r.distance_mm for r in self.rows if r.source in ("DIRECT_DELTA", "FALLBACK_POSITION"))

    @property
    def coverage(self) -> float | None:
        """Share of measurable events that yielded pixels (direct or fallback)."""
        if self.measurable == 0:
            return None
        return (self.count("DIRECT_DELTA") + self.count("FALLBACK_POSITION")) / self.measurable

    @property
    def undefined_delta_events(self) -> int:
        return sum(1 for r in self.rows if r.dx == -1 and r.dy == -1)

    def event_sizes(self) -> list[float]:
        return sorted(r.used_px for r in self.rows if r.source in ("DIRECT_DELTA", "FALLBACK_POSITION", "OUTLIER_REJECTED"))

    @property
    def superseded_mm(self) -> float:
        return sum(r.distance_mm for r in self.rows if r.source == "SUPERSEDED_BY_DIRECT")

    def duplicate_candidates(self) -> list[tuple[Row, Row]]:
        return duplicate_candidates(self.rows)


def parse_header(line: str) -> dict[str, str]:
    return dict(re.findall(r"(\w+)=(\S+)", line))


def read_export(path: Path) -> Export:
    with path.open(newline="", encoding="utf-8") as handle:
        first = handle.readline()
        header = parse_header(first) if first.startswith("#") else {}
        if not first.startswith("#"):
            handle.seek(0)
        rows = [
            Row(
                uptime_ms=int(r["uptime_ms"]),
                package=r["package"],
                window_id=int(r["window_id"]),
                class_name=r["class_name"],
                dx=int(r["dx_px"]),
                dy=int(r["dy_px"]),
                used_dx=int(r["used_dx_px"]),
                used_dy=int(r["used_dy_px"]),
                distance_mm=float(r["distance_mm"]),
                source=r["source"],
            )
            for r in csv.DictReader(handle)
        ]
    return Export(path, header, rows)


def duplicate_candidates(rows: list[Row]) -> list[tuple[Row, Row]]:
    """Spec §70: same package + window_id + dx + dy, uptime difference <= 5 ms, non-zero delta."""
    by_key: dict[tuple, list[Row]] = defaultdict(list)
    for r in rows:
        if r.source == "EXCLUDED" or (r.dx == 0 and r.dy == 0) or (r.dx == -1 and r.dy == -1):
            continue
        by_key[(r.package, r.window_id, r.dx, r.dy)].append(r)
    pairs = []
    for group in by_key.values():
        group.sort(key=lambda r: r.uptime_ms)
        for earlier, later in zip(group, group[1:]):
            if later.uptime_ms - earlier.uptime_ms <= DEDUP_WINDOW_MS:
                pairs.append((earlier, later))
    return pairs


def collect(paths: list[Path]) -> tuple[list[Export], dict[str, AppStats]]:
    files: list[Path] = []
    for p in paths:
        files.extend(sorted(p.glob("*.csv")) if p.is_dir() else [p])
    exports = [read_export(f) for f in files]
    apps: dict[str, AppStats] = {}
    for export in exports:
        for r in export.rows:
            stats = apps.setdefault(r.package or "(none)", AppStats(r.package or "(none)"))
            stats.rows.append(r)
            stats.max_event_px = export.max_event_px or stats.max_event_px
    return exports, apps


def pct(value: float | None) -> str:
    return "—" if value is None else f"{value * 100:.1f} %"


def report(exports: list[Export], apps: dict[str, AppStats], own_prefix: str = "com.scrollmeter.app") -> str:
    lines = []
    if exports and exports[0].header:
        h = exports[0].header
        lines.append(
            f"Device `{h.get('device', '?')}` · {h.get('width_px')}×{h.get('height_px')} px · "
            f"xdpi {h.get('xdpi')} / ydpi {h.get('ydpi')} · outlier limit {h.get('max_event_px')} px · "
            f"{len(exports)} file(s)"
        )
        lines.append("")
    lines.append(
        "| Package | Events | Direct | Fallback | Superseded (m) | Unmeasurable | Outlier | Excluded | (-1,-1) | "
        "Coverage | Counted m | Median / p95 / max event px | Max / limit | Dup. candidates (≤5 ms) |"
    )
    lines.append("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|")
    for stats in sorted(apps.values(), key=lambda s: -len(s.rows)):
        if stats.package.startswith(own_prefix) and stats.measurable == 0:
            continue
        sizes = stats.event_sizes()
        if sizes:
            p95 = sizes[min(len(sizes) - 1, math.ceil(0.95 * len(sizes)) - 1)]
            size_text = f"{statistics.median(sizes):.0f} / {p95:.0f} / {sizes[-1]:.0f}"
            ratio = f"{sizes[-1] / stats.max_event_px:.2f}" if stats.max_event_px else "—"
        else:
            size_text, ratio = "—", "—"
        dups = stats.duplicate_candidates()
        dup_mm = sum(later.distance_mm for _, later in dups if later.source in ("DIRECT_DELTA", "FALLBACK_POSITION"))
        dup_text = f"{len(dups)} ({dup_mm / 1000:.3f} m)" if dups else "0"
        superseded = stats.count("SUPERSEDED_BY_DIRECT")
        superseded_text = f"{superseded} ({stats.superseded_mm / 1000:.2f})" if superseded else "0"
        lines.append(
            f"| `{stats.package}` | {len(stats.rows)} | {stats.count('DIRECT_DELTA')} | {stats.count('FALLBACK_POSITION')} | "
            f"{superseded_text} | {stats.count('UNMEASURABLE')} | {stats.count('OUTLIER_REJECTED')} | {stats.count('EXCLUDED')} | "
            f"{stats.undefined_delta_events} | {pct(stats.coverage)} | {stats.counted_mm / 1000:.2f} | {size_text} | "
            f"{ratio} | {dup_text} |"
        )
    outliers = [r for s in apps.values() for r in s.rows if r.source == "OUTLIER_REJECTED"]
    if outliers:
        lines.append("")
        lines.append("Rejected outliers (largest first):")
        for r in sorted(outliers, key=lambda r: -r.used_px)[:10]:
            lines.append(f"- `{r.package}` win {r.window_id} `{r.class_name}` dx={r.used_dx} dy={r.used_dy} ({r.used_px:.0f} px)")
    classes = defaultdict(set)
    for s in apps.values():
        for r in s.rows:
            if r.source != "EXCLUDED":
                classes[s.package].add(r.class_name)
    lines.append("")
    lines.append("View classes emitting scroll events:")
    for pkg in sorted(classes):
        lines.append(f"- `{pkg}`: " + ", ".join(f"`{c}`" for c in sorted(classes[pkg])))
    return "\n".join(lines)


TESTLIST = re.compile(
    r"TESTLIST (?:surface=(?P<surface>\w+) )?gt_x_px=(?P<gx>[\d.]+) gt_y_px=(?P<gy>[\d.]+) eng_x_px=(?P<ex>\d+) "
    r"eng_y_px=(?P<ey>\d+) gt_mm=(?P<gmm>[\d.]+) eng_mm=(?P<emm>[\d.]+) eng_events=(?P<n>\d+)"
)


def testlist_accuracy(text: str) -> dict[str, float] | None:
    """The last TESTLIST line of a logcat dump → ground truth vs measured, with the % error (spec §38)."""
    matches = list(TESTLIST.finditer(text))
    if not matches:
        return None
    m = matches[-1]
    gt_mm, eng_mm = float(m["gmm"]), float(m["emm"])
    return {
        "gt_x_px": float(m["gx"]),
        "gt_y_px": float(m["gy"]),
        "eng_x_px": float(m["ex"]),
        "eng_y_px": float(m["ey"]),
        "gt_mm": gt_mm,
        "eng_mm": eng_mm,
        "events": float(m["n"]),
        "abs_error_mm": abs(eng_mm - gt_mm),
        "pct_error": abs(eng_mm - gt_mm) / gt_mm * 100 if gt_mm else float("nan"),
    }


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("paths", nargs="*", type=Path, help="debug CSV files or directories of them")
    parser.add_argument("--testlist", type=Path, help="logcat dump with TESTLIST lines")
    args = parser.parse_args(argv)
    if args.testlist:
        result = testlist_accuracy(args.testlist.read_text(encoding="utf-8", errors="replace"))
        if result is None:
            print("no TESTLIST line found", file=sys.stderr)
            return 1
        print(" ".join(f"{k}={v:.3f}" for k, v in result.items()))
        return 0
    if not args.paths:
        parser.error("give CSV files/directories or --testlist")
    exports, apps = collect(args.paths)
    print(report(exports, apps))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
