"""Unit tests for tools/analyze_debug_csv.py — run: python3 -m unittest discover -s tools -p 'test_*.py'"""

import tempfile
import unittest
from pathlib import Path

import analyze_debug_csv as tool

HEADER = (
    "# scrollmeter-debug app=0.1.0 device=OnePlus_CPH2399_API34 width_px=1080 height_px=2400 xdpi=403.411 "
    "ydpi=401.052 density_dpi=480 mm_per_px_x=0.062963 mm_per_px_y=0.063333 scale_method=DISPLAY_METRICS "
    "diagonal_px=2631.8 max_event_px=10527.2 rows=6 overflowed=0\n"
    "timestamp,uptime_ms,package,window_id,class_name,dx_px,dy_px,scroll_x,scroll_y,max_scroll_x,max_scroll_y,"
    "used_dx_px,used_dy_px,distance_mm,source,status\n"
)
ROWS = [
    "2026-09-23T10:00:00.000,1000,com.a,5,RecyclerView,0,100,0,0,0,0,0,100,6.3333,DIRECT_DELTA,accepted",
    "2026-09-23T10:00:00.003,1003,com.a,5,RecyclerView,0,100,0,0,0,0,0,100,6.3333,DIRECT_DELTA,accepted",
    "2026-09-23T10:00:00.050,1050,com.a,5,RecyclerView,0,100,0,0,0,0,0,100,6.3333,DIRECT_DELTA,accepted",
    "2026-09-23T10:00:00.100,1100,com.b,9,WebView,-1,-1,0,300,0,0,0,300,19.0,FALLBACK_POSITION,accepted",
    "2026-09-23T10:00:00.200,1200,com.b,9,WebView,-1,-1,0,300,0,0,0,0,0.0,UNMEASURABLE,rejected",
    "2026-09-23T10:00:00.300,1300,com.b,9,ListView,0,12000,0,0,0,0,0,12000,760.0,OUTLIER_REJECTED,rejected",
    "2026-09-23T10:00:00.400,1400,com.a,5,WebView,-1,-1,0,450,0,0,0,150,9.5,SUPERSEDED_BY_DIRECT,rejected",
]


class AnalyzeDebugCsvTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.csv = Path(self.dir.name) / "run.csv"
        self.csv.write_text(HEADER + "\n".join(ROWS) + "\n", encoding="utf-8")

    def tearDown(self):
        self.dir.cleanup()

    def test_reads_header_and_rows(self):
        export = tool.read_export(self.csv)
        self.assertAlmostEqual(export.max_event_px, 10527.2)
        self.assertEqual(len(export.rows), 7)

    def test_a_truncated_last_line_is_skipped(self):
        with self.csv.open("a", encoding="utf-8") as handle:
            handle.write("2026-09-23T10:00:00.500,1500,com.a,5\n")
        self.assertEqual(len(tool.read_export(self.csv).rows), 7)

    def test_per_app_counts_and_coverage(self):
        _, apps = tool.collect([Path(self.dir.name)])
        a, b = apps["com.a"], apps["com.b"]
        self.assertEqual(a.count("DIRECT_DELTA"), 3)
        self.assertAlmostEqual(a.counted_mm, 18.9999, places=3)
        self.assertAlmostEqual(a.coverage, 1.0)  # the superseded copy is not a coverage gap
        self.assertAlmostEqual(a.superseded_mm, 9.5)
        self.assertEqual(b.undefined_delta_events, 2)
        self.assertAlmostEqual(b.coverage, 1 / 3)

    def test_duplicate_rule_is_same_window_same_delta_within_5_ms(self):
        _, apps = tool.collect([self.csv])
        pairs = apps["com.a"].duplicate_candidates()
        self.assertEqual(len(pairs), 1)  # 1000→1003 is a candidate, 1003→1050 is not
        self.assertEqual(pairs[0][1].uptime_ms, 1003)
        self.assertEqual(apps["com.b"].duplicate_candidates(), [])

    def test_report_mentions_outlier_and_ratio(self):
        exports, apps = tool.collect([self.csv])
        text = tool.report(exports, apps)
        self.assertIn("| `com.a` | 4 | 3 | 0 | 1 (0.01) | 0 | 0 | 0 | 1 | 100.0 % |", text)
        self.assertIn("dx=0 dy=12000", text)
        self.assertIn("1.14", text)  # 12000 / 10527.2

    def test_testlist_accuracy_uses_the_last_line(self):
        log = (
            "D ScrollMeter: TESTLIST gt_x_px=0.0 gt_y_px=100.0 eng_x_px=0 eng_y_px=90 gt_mm=6.333 eng_mm=5.700 eng_events=1\n"
            "D ScrollMeter: TESTLIST surface=VIEW gt_x_px=0.0 gt_y_px=1000.0 eng_x_px=0 eng_y_px=990 gt_mm=63.333 eng_mm=62.700 eng_events=9\n"
        )
        result = tool.testlist_accuracy(log)
        self.assertAlmostEqual(result["gt_y_px"], 1000.0)
        self.assertAlmostEqual(result["pct_error"], 0.633 / 63.333 * 100, places=3)
        self.assertIsNone(tool.testlist_accuracy("nothing"))


if __name__ == "__main__":
    unittest.main()
