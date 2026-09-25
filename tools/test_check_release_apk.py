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
    5:9:void log(java.lang.String):20:24 -> a
com.scrollmeter.app.accessibility.ScrollAccessibilityService -> n5.q:
# {"id":"sourceFile","fileName":"ScrollAccessibilityService.kt"}
    1:9:void log(java.lang.String):200:208 -> a
com.scrollmeter.app.AppGraph -> n5.e:
com.scrollmeter.app.calibration.CalibrationRepository$Companion -> q5.c:
# {"id":"sourceFile","fileName":"CalibrationRepository.kt"}
    int q5.CalibrationRepository$Companion.$r8$classId -> d
    1:1:void q5.CalibrationRepository$Companion.<init>(int):0:0 -> <init>
    6:7:void androidx.profileinstaller.ProfileInstaller$2.onDiagnosticReceived(int,java.lang.Object):141:141 -> a
      # {"id":"com.android.tools.r8.residualsignature","signature":"()V"}
    9:11:void androidx.profileinstaller.ProfileInstaller$2.onResultReceived(int,java.lang.Object):150:150 -> b
    1:4:com.scrollmeter.app.calibration.CalibrationState read(androidx.datastore.preferences.core.Preferences):40:43 -> d
com.scrollmeter.app.ui.Screen -> r7.a:
    3:4:int androidx.core.util.Helper.clamp(int):12:12 -> a
    3:4:void show():30 -> a
"""

DEXDUMP = """Processing 'app-release.apk'...
Class #0            -
  Class descriptor  : 'La1/b;'
07c150:                                        |[07c150] a1.b.a:(Ljava/lang/String;)V
07c160: 7120 eb05 0300                         |0250: invoke-static {v3, v0}, Landroid/util/Log;.e:(Ljava/lang/String;Ljava/lang/String;)I // method@05eb
Class #1            -
  Class descriptor  : 'Ln5/e;'
0a71e0:                                        |[0a71e0] n5.e.b:()V
0a71f0: 7120 1234 6200                         |0010: invoke-static {v2}, Ln5/q;.a:(Ljava/lang/String;)V // method@1234
Class #2            -
  Class descriptor  : 'Ln5/q;'
0a71f8:                                        |[0a71f8] n5.q.a:(Ljava/lang/String;)V
0a71fc: 7120 ea05 6200                         |0082: invoke-static {v2, v6}, Landroid/util/Log;.d:(Ljava/lang/String;Ljava/lang/String;)I // method@05ea
Class #3            -
  Class descriptor  : 'Lq5/c;'
1cbf84:                                        |[1cbf84] q5.c.a:()V
1cbfa6: 7120 c706 1000                         |0009: invoke-static {v0, v1}, Landroid/util/Log;.d:(Ljava/lang/String;Ljava/lang/String;)I // method@06c7
1cbfd0:                                        |[1cbfd0] q5.c.b:(ILjava/lang/Object;)V
1cc048: 7120 c706 2100                         |0034: invoke-static {v1, v2}, Landroid/util/Log;.d:(Ljava/lang/String;Ljava/lang/String;)I // method@06c7
1cbd98:                                        |[1cbd98] q5.c.d:(Lc4/b;)Lq5/k;
1cbdb0: 6e20 eb0f 1000                         |0004: invoke-virtual {v0, v1}, Lc4/b;.c:(Lc4/d;)Ljava/lang/Object; // method@0feb
"""

BADGING = """package: name='com.scrollmeter.app' versionCode='1' versionName='0.1.0'
sdkVersion:'29'
targetSdkVersion:'36'
application-label:'ScrollMeter'
"""


class DeobfuscationTest(unittest.TestCase):
    def test_reads_class_lines_only(self):
        names = tool.deobfuscation_map(MAPPING)
        self.assertEqual(names["n5.q"], "com.scrollmeter.app.accessibility.ScrollAccessibilityService")
        self.assertEqual(names["q5.c"], "com.scrollmeter.app.calibration.CalibrationRepository$Companion")
        self.assertNotIn("a", names)
        self.assertNotIn("<init>", names)


class LogCallTest(unittest.TestCase):
    def test_finds_the_calling_methods_as_named_in_the_dex(self):
        self.assertEqual(tool.methods_calling_log(DEXDUMP), {("a1.b", "a"), ("n5.q", "a"), ("q5.c", "a"), ("q5.c", "b")})

    def test_a_call_to_another_method_is_not_a_log_call(self):
        self.assertNotIn(("n5.e", "b"), tool.methods_calling_log(DEXDUMP))

    def test_every_log_call_is_reported_library_code_too(self):
        # q5.c is one of our classes with ProfileInstaller$2 merged in (R8 horizontal class merging).
        self.assertEqual(tool.log_calls(DEXDUMP, MAPPING), [
            "a1.b.a (in androidx.core.graphics.TypefaceCompatApi28Impl)",
            "n5.q.a (in com.scrollmeter.app.accessibility.ScrollAccessibilityService)",
            "q5.c.a (in com.scrollmeter.app.calibration.CalibrationRepository$Companion)",
            "q5.c.b (in com.scrollmeter.app.calibration.CalibrationRepository$Companion)",
        ])

    def test_a_dex_without_log_calls_passes(self):
        without = "\n".join(line for line in DEXDUMP.splitlines() if "Landroid/util/Log;" not in line)
        self.assertEqual(tool.log_calls(without, MAPPING), [])

    def test_an_unmapped_class_keeps_its_name(self):
        dump = "|[000100] com.scrollmeter.app.Kept.run:()V\n invoke-static {v0}, Landroid/util/Log;.i:()I\n"
        self.assertEqual(tool.log_calls(dump, ""), ["com.scrollmeter.app.Kept.run (in com.scrollmeter.app.Kept)"])

    def test_the_log_class_referenced_outside_an_invoke_is_ignored(self):
        dump = "|[000100] n5.q.a:()V\n const-class v0, Landroid/util/Log;\n"
        self.assertEqual(tool.methods_calling_log(dump), set())


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
            dump = DEXDUMP[:DEXDUMP.index("Class #3")]
            found = tool.violations(apk, MAPPING, dump, BADGING + "application-debuggable\n")
            self.assertEqual(found, ["a1.b.a (in androidx.core.graphics.TypefaceCompatApi28Impl) calls android.util.Log",
                                     "n5.q.a (in com.scrollmeter.app.accessibility.ScrollAccessibilityService) calls android.util.Log",
                                     "the APK is debuggable", "classes.dex contains 'recording.csv'"])


if __name__ == "__main__":
    unittest.main()
