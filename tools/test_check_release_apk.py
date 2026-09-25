"""Unit tests for tools/check_release_apk.py — run: python3 -m unittest discover -s tools -p 'test_*.py'"""

import tempfile
import unittest
import zipfile
from pathlib import Path

import check_release_apk as tool

MAPPING = """# compiler: R8
# {"id":"com.android.tools.r8.mapping","version":"2.2"}
androidx.core.graphics.TypefaceCompatApi28Impl -> a1.b:
    java.lang.Class sFontFamily -> a
    1:4:void <init>():10:13 -> <init>
com.scrollmeter.app.accessibility.ScrollAccessibilityService -> n5.q:
    1:9:void log(java.lang.String):200:208 -> a
com.scrollmeter.app.AppGraph -> n5.e:
"""

DEXDUMP = """Processing 'app-release.apk'...
Class #0            -
  Class descriptor  : 'La1/b;'
      insns size    : 5 16-bit code units
07c150:                                        |[07c150] a1.b.a:()V
07c160: 7120 eb05 0300                         |0250: invoke-static {v3, v0}, Landroid/util/Log;.e:(Ljava/lang/String;Ljava/lang/String;)I // method@05eb
Class #1            -
  Class descriptor  : 'Ln5/e;'
0a71f0: 7120 1234 6200                         |0010: invoke-static {v2}, Ln5/q;.a:(Ljava/lang/String;)V // method@1234
Class #2            -
  Class descriptor  : 'Ln5/q;'
0a71fc: 7120 ea05 6200                         |0082: invoke-static {v2, v6}, Landroid/util/Log;.d:(Ljava/lang/String;Ljava/lang/String;)I // method@05ea
"""

BADGING = """package: name='com.scrollmeter.app' versionCode='1' versionName='0.1.0'
sdkVersion:'28'
targetSdkVersion:'36'
application-label:'ScrollMeter'
"""


class DeobfuscationTest(unittest.TestCase):
    def test_reads_class_lines_only(self):
        names = tool.deobfuscation_map(MAPPING)
        self.assertEqual(names["n5.q"], "com.scrollmeter.app.accessibility.ScrollAccessibilityService")
        self.assertEqual(names["a1.b"], "androidx.core.graphics.TypefaceCompatApi28Impl")
        self.assertNotIn("a", names)
        self.assertNotIn("<init>", names)


class LogCallTest(unittest.TestCase):
    def test_finds_the_calling_classes_as_named_in_the_dex(self):
        self.assertEqual(tool.classes_calling_log(DEXDUMP), {"a1.b", "n5.q"})

    def test_a_call_to_another_class_is_not_a_log_call(self):
        self.assertNotIn("n5.e", tool.classes_calling_log(DEXDUMP))

    def test_only_app_classes_are_reported_by_their_original_name(self):
        self.assertEqual(tool.app_classes_calling_log(DEXDUMP, MAPPING),
                         ["com.scrollmeter.app.accessibility.ScrollAccessibilityService"])

    def test_library_logging_passes(self):
        library_only = DEXDUMP.split("Class #1")[0]
        self.assertEqual(tool.app_classes_calling_log(library_only, MAPPING), [])

    def test_an_unmapped_app_class_is_still_caught(self):
        # A class R8 kept under its own name has no mapping line of its own in this sample.
        dump = "  Class descriptor  : 'Lcom/scrollmeter/app/Kept;'\n invoke-static {v0}, Landroid/util/Log;.i:()I\n"
        self.assertEqual(tool.app_classes_calling_log(dump, ""), ["com.scrollmeter.app.Kept"])

    def test_the_log_class_referenced_outside_an_invoke_is_ignored(self):
        dump = "  Class descriptor  : 'Ln5/q;'\n const-class v0, Landroid/util/Log;\n"
        self.assertEqual(tool.classes_calling_log(dump), set())


class BadgingTest(unittest.TestCase):
    def test_release_badging_is_not_debuggable(self):
        self.assertFalse(tool.is_debuggable(BADGING))

    def test_debuggable_flag_is_caught(self):
        self.assertTrue(tool.is_debuggable(BADGING + "application-debuggable\n"))


class ForbiddenLiteralTest(unittest.TestCase):
    def _apk(self, directory: str, dex: bytes) -> Path:
        path = Path(directory) / "app.apk"
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr("classes.dex", b"dex\n035\0" + dex)
            archive.writestr("res/raw/recording.csv", b"not dex, ignored")
        return path

    def test_clean_dex_passes(self):
        with tempfile.TemporaryDirectory() as directory:
            self.assertEqual(tool.forbidden_literals(self._apk(directory, b"scrollmeter.db")), [])

    def test_debug_recording_path_is_caught(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = self._apk(directory, b"\x0dfiles/debug\x00\x0drecording.csv\x00")
            self.assertEqual(tool.forbidden_literals(apk), ["classes.dex contains 'recording.csv'",
                                                            "classes.dex contains 'files/debug'"])

    def test_violations_combine_every_check(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = self._apk(directory, b"recording.csv")
            found = tool.violations(apk, MAPPING, DEXDUMP, BADGING + "application-debuggable\n")
            self.assertEqual(found, ["com.scrollmeter.app.accessibility.ScrollAccessibilityService calls android.util.Log",
                                     "the APK is debuggable", "classes.dex contains 'recording.csv'"])


if __name__ == "__main__":
    unittest.main()
