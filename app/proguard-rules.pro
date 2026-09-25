# Release shrinking rules. The default Android optimize rules plus the consumer rules shipped
# by AndroidX (Room, Compose) cover the MVP; add app-specific keeps here only with a reason.

# No logcat output from release builds at all (ADR-034): our code logs only behind BuildConfig.DEBUG,
# and the libraries' diagnostics are not worth a line in a shared log on a privacy-first app.
# isLoggable() is assumed false, so the guarded blocks go too. tools/check_release_apk.py verifies it.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
    public static int println(...);
    public static boolean isLoggable(java.lang.String, int) return false;
}
