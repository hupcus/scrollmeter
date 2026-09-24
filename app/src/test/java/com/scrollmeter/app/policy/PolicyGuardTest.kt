package com.scrollmeter.app.policy

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test

/**
 * Fails the build when a source change widens what ScrollMeter reads or may do (CLAUDE.md hard
 * rules, spec §5, §29). Scans the app's source sets (main, debug, release) as text — not the
 * tests, which have to name the forbidden things to forbid them. What the build pulls in from
 * libraries is checked on the merged manifests by tools/check_manifest_policy.py in CI.
 *
 * Relaxing any rule here needs an ADR in docs/measurement-decisions.md and Honza's explicit OK.
 */
class PolicyGuardTest {
    private val appDir: File = listOf(File("src"), File("app/src")).first { it.isDirectory }.parentFile ?: File(".")
    private val sourceSets = listOf("main", "debug", "release").map { File(appDir, "src/$it") }.filter { it.isDirectory }
    private val sources: List<File> = sourceSets.flatMap { set ->
        set.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "java", "xml") }.toList()
    }

    @Test
    fun sourcesAreFound() {
        assertWithMessage("no sources under ${appDir.absolutePath}").that(sources.size).isAtLeast(10)
    }

    /** Anywhere in the app: no network, no package enumeration, no background presence, no wider accessibility. */
    @Test
    fun noForbiddenCapabilitiesAnywhere() {
        val forbidden = listOf(
            "android.permission.INTERNET",
            "ACCESS_NETWORK_STATE",
            "QUERY_ALL_PACKAGES",
            "FOREGROUND_SERVICE",
            "startForeground(",
            "SYSTEM_ALERT_WINDOW",
            "FLAG_RETRIEVE_INTERACTIVE_WINDOWS",
            "flagRetrieveInteractiveWindows",
            "FLAG_REQUEST_TOUCH_EXPLORATION_MODE",
            "flagRequestTouchExplorationMode",
            "FLAG_SEND_MOTION_EVENTS",
            "flagSendMotionEvents",
            "FLAG_REQUEST_FILTER_KEY_EVENTS",
            "flagRequestFilterKeyEvents",
            "canRetrieveWindowContent=\"true\"",
        )
        val hits = sources.flatMap { file ->
            val code = stripComments(file.readText(), file.extension)
            forbidden.filter { it in code }.map { "${file.relativeTo(appDir)}: $it" }
        }
        assertWithMessage("forbidden capability in sources").that(hits).isEmpty()
    }

    /**
     * Code that touches accessibility types may only read numbers and identifiers: never text,
     * content descriptions, source nodes, window trees, and never act on other apps.
     */
    @Test
    fun accessibilityCodeReadsNoContent() {
        val accessibilityFiles = sources.filter { file ->
            file.extension != "xml" && file.readText().let { "android.view.accessibility" in it || "android.accessibilityservice" in it }
        }
        assertWithMessage("expected the parser and the service to be scanned")
            .that(accessibilityFiles.map { it.name })
            .containsAtLeast("AccessibilityEventParser.kt", "ScrollAccessibilityService.kt")
        val forbidden = listOf(
            Regex("""\.text\b"""),
            Regex("""getText\("""),
            Regex("""beforeText"""),
            Regex("""contentDescription"""),
            Regex("""getSource\("""),
            Regex("""\.source\b"""),
            Regex("""getRecord\(|recordCount|getRecordCount"""),
            Regex("""AccessibilityNodeInfo"""),
            Regex("""rootInActiveWindow|getRootInActiveWindow"""),
            Regex("""\bwindows\b|getWindows\("""),
            Regex("""findFocus\("""),
            Regex("""performGlobalAction|dispatchGesture|performAction\("""),
            Regex("""parcelableData|getParcelableData"""),
            // Runtime widening would override the XML pins checked below.
            Regex("""setServiceInfo\(|serviceInfo\s*=[^=]"""),
        )
        val hits = accessibilityFiles.flatMap { file ->
            val code = stripComments(file.readText(), file.extension)
            forbidden.filter { it.containsMatchIn(code) }.map { "${file.name}: ${it.pattern}" }
        }
        assertWithMessage("accessibility code reads or acts on screen content").that(hits).isEmpty()
    }

    /**
     * The measurement engine, the aggregation pipeline and the time-in-app logic stay pure Kotlin so
     * they run on the JVM (D7). In usage/ only the two platform adapters may touch Android.
     */
    @Test
    fun pureKotlinPackagesHaveNoAndroidImports() {
        val main = File(appDir, "src/main/java/com/scrollmeter/app")
        val pure = listOf("measurement", "aggregation", "data/model", "usage", "format", "insights", "notifications", "export", "onboarding").flatMap { dir ->
            File(main, dir).walkTopDown().filter { it.extension == "kt" }.toList()
        }.filterNot { it.name in USAGE_PLATFORM_ADAPTERS || it.name in PHASE6_PLATFORM_ADAPTERS } + File(main, "data/DataEraser.kt")
        assertWithMessage("expected the pure packages to be scanned").that(pure.map { it.name })
            .containsAtLeast(
                "ScrollMeasurementEngine.kt", "ScrollPipeline.kt", "ForegroundTimeAggregator.kt", "UsageSyncer.kt", "DistanceFormatter.kt",
                "DistanceComparisonProvider.kt", "NotificationRules.kt", "CsvExporter.kt", "DataEraser.kt", "OnboardingFlow.kt",
            )
        val hits = pure.filter { file -> file.readLines().any { it.startsWith("import android.") || it.startsWith("import androidx.") } }
            .map { it.name }
        assertWithMessage("pure packages must not import Android").that(hits).isEmpty()
    }

    @Test
    fun accessibilityConfigListensToScrollEventsOnly() {
        val config = File(appDir, "src/main/res/xml/accessibility_service_config.xml")
        val root = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(config).documentElement
        fun attr(name: String) = root.getAttributeNS(ANDROID_NS, name)

        assertWithMessage("accessibilityEventTypes").that(attr("accessibilityEventTypes")).isEqualTo("typeViewScrolled")
        assertWithMessage("canRetrieveWindowContent").that(attr("canRetrieveWindowContent")).isEqualTo("false")
        assertWithMessage("isAccessibilityTool").that(attr("isAccessibilityTool")).isEqualTo("false")
        assertWithMessage("accessibilityFlags").that(attr("accessibilityFlags")).isEqualTo("flagDefault")
        assertWithMessage("packageNames must not be set to specific apps").that(attr("packageNames")).isEmpty()
    }

    /**
     * Permissions are an allowlist: PACKAGE_USAGE_STATS for time in app (ADR-021), POST_NOTIFICATIONS
     * for the optional notifications (ADR-030); anything else needs an ADR and Honza's OK.
     */
    @Test
    fun mainManifestRequestsOnlyAllowedPermissions() {
        val root = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File(appDir, "src/main/AndroidManifest.xml")).documentElement
        val requested = listOf("uses-permission", "uses-permission-sdk-23").flatMap { tag ->
            root.getElementsByTagName(tag).let { nodes ->
                (0 until nodes.length).map { (nodes.item(it) as org.w3c.dom.Element).getAttributeNS(ANDROID_NS, "name") }
            }
        }
        assertWithMessage("uses-permission outside the allowlist").that(requested - ALLOWED_PERMISSIONS).isEmpty()
        val otherManifests = sourceSets.filter { it.name != "main" }.map { File(it, "AndroidManifest.xml") }.filter { it.isFile }
        otherManifests.forEach { assertWithMessage("uses-permission in ${it.path}").that(it.readText()).doesNotContain("<uses-permission") }
    }

    /** ADR-027: no backup and no device-to-device transfer — every domain excluded in both sections. */
    @Test
    fun nothingLeavesThePhoneThroughBackupOrTransfer() {
        val manifest = File(appDir, "src/main/AndroidManifest.xml").readText()
        assertThat(manifest).contains("android:allowBackup=\"false\"")
        assertThat(manifest).contains("android:dataExtractionRules=\"@xml/data_extraction_rules\"")
        val rules = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(appDir, "src/main/res/xml/data_extraction_rules.xml")).documentElement
        for (section in listOf("cloud-backup", "device-transfer")) {
            val element = rules.getElementsByTagName(section).item(0) as org.w3c.dom.Element
            assertWithMessage("$section includes something").that(element.getElementsByTagName("include").length).isEqualTo(0)
            val excluded = element.getElementsByTagName("exclude").let { nodes ->
                (0 until nodes.length).map { nodes.item(it) as org.w3c.dom.Element }
                    .filter { it.getAttribute("path") == "." }.map { it.getAttribute("domain") }
            }
            assertWithMessage("$section excluded domains").that(excluded).containsAtLeast("root", "file", "database", "sharedpref", "external")
        }
    }

    /**
     * ADR-008, ADR-030: package visibility only for apps with a launcher entry and for the home
     * screen — two MAIN intent queries, no data, nothing else.
     */
    @Test
    fun packageVisibilityIsTheLauncherAndHomeQueriesOnly() {
        val root = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File(appDir, "src/main/AndroidManifest.xml")).documentElement
        val queries = root.getElementsByTagName("queries")
        assertThat(queries.length).isEqualTo(1)
        val children = queries.item(0).childNodes.let { nodes ->
            (0 until nodes.length).map { nodes.item(it) }.filterIsInstance<org.w3c.dom.Element>()
        }
        assertWithMessage("<queries> children").that(children.map { it.tagName }).containsExactly("intent", "intent")
        fun names(intent: org.w3c.dom.Element, tag: String) = intent.getElementsByTagName(tag).let { nodes ->
            (0 until nodes.length).map { (nodes.item(it) as org.w3c.dom.Element).getAttributeNS(ANDROID_NS, "name") }
        }
        children.forEach { intent ->
            assertThat(names(intent, "action")).containsExactly("android.intent.action.MAIN")
            assertThat(intent.getElementsByTagName("data").length).isEqualTo(0)
        }
        assertThat(children.flatMap { names(it, "category") })
            .containsExactly("android.intent.category.LAUNCHER", "android.intent.category.HOME")
    }

    /**
     * ADR-030: every provider is a FileProvider that is not exported, grants per-share read access
     * and names its paths in the manifest — the static `getUriForFile` reads nothing else, so a
     * provider without the meta-data fails on the first share. The main one reaches cache/exports/ only.
     */
    @Test
    fun providersAreClosedFileProvidersWithTheirPathsInTheManifest() {
        val builder = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
        val providers = sourceSets.map { File(it, "AndroidManifest.xml") }.filter { it.isFile }.flatMap { manifest ->
            builder.parse(manifest).getElementsByTagName("provider").let { nodes -> (0 until nodes.length).map { nodes.item(it) as org.w3c.dom.Element } }
        }
        assertThat(providers).isNotEmpty()
        providers.forEach { provider ->
            val name = provider.getAttributeNS(ANDROID_NS, "name")
            assertWithMessage("$name exported").that(provider.getAttributeNS(ANDROID_NS, "exported")).isEqualTo("false")
            assertWithMessage("$name grantUriPermissions").that(provider.getAttributeNS(ANDROID_NS, "grantUriPermissions")).isEqualTo("true")
            val paths = provider.getElementsByTagName("meta-data").let { nodes -> (0 until nodes.length).map { nodes.item(it) as org.w3c.dom.Element } }
                .filter { it.getAttributeNS(ANDROID_NS, "name") == "android.support.FILE_PROVIDER_PATHS" }
            assertWithMessage("$name FILE_PROVIDER_PATHS meta-data").that(paths).hasSize(1)
        }
        val exportPaths = builder.parse(File(appDir, "src/main/res/xml/export_paths.xml")).documentElement.childNodes.let { nodes ->
            (0 until nodes.length).map { nodes.item(it) }.filterIsInstance<org.w3c.dom.Element>()
        }
        assertThat(exportPaths.map { it.tagName to it.getAttribute("path") }).containsExactly("cache-path" to "exports/")
    }

    /** The one class allowed to read usage events; everything else gets samples from it (ADR-021). */
    @Test
    fun onlyUsageEventsSourceReadsUsageEvents() {
        val readers = sources.filter { it.extension == "kt" && "android.app.usage" in stripComments(it.readText(), "kt") }.map { it.name }
        assertThat(readers).containsExactly("UsageEventsSource.kt")
    }

    /**
     * Spec §30, ADR-032: the accessibility settings open only after the prominent disclosure. One place
     * builds that intent — MainActivity — and hands it only to the onboarding and the gated NavHost.
     */
    @Test
    fun onlyMainActivityOpensTheAccessibilitySettings() {
        // The constants and their string values ("android.settings.ACCESSIBILITY_SETTINGS", the details page).
        val opener = Regex("""ACCESSIBILITY_SETTINGS|ACCESSIBILITY_DETAILS_SETTINGS|android\.settings\.ACCESSIBILITY""")
        val openers = sources.filter { it.extension in setOf("kt", "java", "xml") && opener.containsMatchIn(stripComments(it.readText(), it.extension)) }
        assertThat(openers.map { it.name }).containsExactly("MainActivity.kt")
        val navHost = stripComments(sources.single { it.name == "ScrollMeterNavHost.kt" }.readText(), "kt")
        assertThat(navHost).contains("AccessibilityGate.route(")
        assertWithMessage("the NavHost passes the ungated callback on").that(rawCallbackPassedOn(navHost)).isEmpty()
    }

    @Test
    fun theGateGuardSeesARawCallbackPassedOn() {
        val gated = "fun X(onOpenAccessibilitySettings: () -> Unit) { Dashboard(onOpenAccessibilitySettings = gated); onOpenAccessibilitySettings() }"
        assertThat(rawCallbackPassedOn(gated)).isEmpty()
        assertThat(rawCallbackPassedOn("Dashboard(onOpenAccessibilitySettings = onOpenAccessibilitySettings)")).hasSize(1)
        assertThat(rawCallbackPassedOn("Settings(open = onOpenAccessibilitySettings, x)")).hasSize(1)
    }

    /**
     * CLAUDE.md: every tunable constant lives in MeasurementConfig. A `const val` whose name the ADR
     * log mentions is a tunable by definition — declaring it anywhere else fails.
     */
    @Test
    fun constantsNamedInTheAdrLogLiveInMeasurementConfig() {
        val adrNames = Regex("""\b[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+\b""").findAll(File(appDir.canonicalFile.parentFile, "docs/measurement-decisions.md").readText())
            .map { it.value }.toSet()
        val declaration = Regex("""const val ([A-Z][A-Z0-9_]+)\b""")
        val misplaced = sources.filter { it.extension == "kt" && it.name != "MeasurementConfig.kt" }.flatMap { file ->
            declaration.findAll(stripComments(file.readText(), "kt")).map { it.groupValues[1] }.filter { it in adrNames }
                .map { "${file.relativeTo(appDir)}: $it" }
        }
        assertWithMessage("ADR tunables declared outside MeasurementConfig").that(misplaced).isEmpty()
        assertThat(adrNames).containsAtLeast("RECORD_MIN_PRIOR_DAYS", "RECORD_MIN_MM", "SESSION_RETENTION_DAYS")
    }

    @Test
    fun commentStrippingDoesNotHideCodeAfterAStringWithSlashes() {
        val code = stripComments("val u = \"https://x\"; val leak = event.text // why\n/* block .source */ val c = '\"'; x()", "kt")
        assertThat(code).contains("event.text")
        assertThat(code).contains("x()")
        assertThat(code).doesNotContain("why")
        assertThat(code).doesNotContain(".source")
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

        /**
         * The NavHost's ungated callback may be declared (`name:`), used as a parameter name of another
         * screen (`name =`) and called (`name(`) — any other use hands it on past the gate.
         */
        fun rawCallbackPassedOn(code: String): List<String> =
            Regex("""onOpenAccessibilitySettings(?!\s*[(:=])""").findAll(code).map { it.value }.toList()
        val ALLOWED_PERMISSIONS = setOf("android.permission.PACKAGE_USAGE_STATS", "android.permission.POST_NOTIFICATIONS")
        val USAGE_PLATFORM_ADAPTERS = setOf("UsageEventsSource.kt", "UsageAccessChecker.kt")

        /** The Android ends of notifications and export; their rules and formats stay pure. */
        val PHASE6_PLATFORM_ADAPTERS = setOf("AndroidNotificationPoster.kt", "CsvExportWriter.kt", "ExportFileProvider.kt")

        /**
         * Comments may name forbidden things to explain why they are absent; only code counts —
         * string literals included, so a `//` inside a string does not hide the rest of the line.
         */
        fun stripComments(text: String, extension: String): String {
            if (extension == "xml") return text.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
            val out = StringBuilder(text.length)
            var quote: String? = null // the delimiter of the literal we are in: ", \"\"\" or '
            var i = 0
            while (i < text.length) {
                val c = text[i]
                when {
                    quote != null && quote != RAW && c == '\\' && i + 1 < text.length -> {
                        out.append(c).append(text[i + 1])
                        i += 2
                    }
                    quote != null && text.startsWith(quote, i) -> {
                        out.append(quote)
                        i += quote.length
                        quote = null
                    }
                    quote != null -> out.append(text[i++])
                    text.startsWith("//", i) -> while (i < text.length && text[i] != '\n') i++
                    text.startsWith("/*", i) -> i = text.indexOf("*/", i + 2).let { if (it < 0) text.length else it + 2 }
                    text.startsWith(RAW, i) -> {
                        quote = RAW
                        out.append(RAW)
                        i += RAW.length
                    }
                    c == '"' || c == '\'' -> {
                        quote = c.toString()
                        out.append(text[i++])
                    }
                    else -> out.append(text[i++])
                }
            }
            return out.toString()
        }

        private const val RAW = "\"\"\""
    }
}
