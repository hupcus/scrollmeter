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

    /** The measurement engine stays pure Kotlin so it runs on the JVM (D7). */
    @Test
    fun measurementPackageHasNoAndroidImports() {
        val measurement = File(appDir, "src/main/java/com/scrollmeter/app/measurement")
        val hits = measurement.walkTopDown().filter { it.extension == "kt" }
            .filter { file -> file.readLines().any { it.startsWith("import android.") || it.startsWith("import androidx.") } }
            .map { it.name }.toList()
        assertWithMessage("measurement/ must not import Android").that(hits).isEmpty()
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

    @Test
    fun mainManifestRequestsNoPermissions() {
        val manifest = File(appDir, "src/main/AndroidManifest.xml").readText()
        assertWithMessage("uses-permission in the main manifest").that(manifest).doesNotContain("<uses-permission")
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
