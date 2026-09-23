"""Unit tests for tools/check_manifest_policy.py — run: python3 -m unittest discover -s tools -p 'test_*.py'"""

import unittest

import check_manifest_policy as tool

GOOD = """<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.scrollmeter.app">
  <uses-permission android:name="com.scrollmeter.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"/>
  <application android:allowBackup="false">
    <service android:name="com.scrollmeter.app.accessibility.ScrollAccessibilityService"
        android:exported="false" android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"/>
    %s
  </application>
</manifest>"""
PROVIDER = '<provider android:name="androidx.core.content.FileProvider" android:authorities="x.devtools.files"/>'


class CheckManifestPolicyTest(unittest.TestCase):
    def test_clean_manifest_passes(self):
        self.assertEqual(tool.violations(GOOD % "", release=True), [])

    def test_library_permission_is_caught(self):
        bad = GOOD.replace("<application", '<uses-permission android:name="android.permission.INTERNET"/><application')
        self.assertEqual(tool.violations(bad % "", release=True), ["forbidden permission android.permission.INTERNET"])

    def test_foreground_service_types_are_caught_by_prefix(self):
        bad = GOOD.replace("<application", '<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC"/><application')
        self.assertEqual(len(tool.violations(bad % "", release=False)), 1)

    def test_exported_service_is_caught(self):
        bad = GOOD.replace('android:exported="false"', 'android:exported="true"')
        self.assertIn("accessibility service is not exported=false", tool.violations(bad % "", release=False))

    def test_file_provider_is_allowed_in_debug_only(self):
        self.assertEqual(tool.violations(GOOD % PROVIDER, release=False), [])
        self.assertEqual(len(tool.violations(GOOD % PROVIDER, release=True)), 1)


if __name__ == "__main__":
    unittest.main()
