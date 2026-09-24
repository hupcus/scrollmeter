package com.scrollmeter.app.policy

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.w3c.dom.Element

/**
 * Czech (`values/`) and English (`values-en/`) stay in step (D17, ADR-032): the same keys, the same
 * format arguments — a missing `%1$s` crashes `getString` only at run time, in one language. And the
 * prominent disclosure stays word for word what spec §30 says (Google Play: the text the review saw).
 */
class TranslationsTest {
    private val appDir: File = listOf(File("src"), File("app/src")).first { it.isDirectory }.canonicalFile.parentFile
    private val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()

    private class Res(val strings: Map<String, String>, val plurals: Map<String, Map<String, String>>)

    private fun read(dir: String): Res {
        val root = builder.parse(File(appDir, "src/main/res/$dir/strings.xml")).documentElement
        fun elements(tag: String) = root.getElementsByTagName(tag).let { n -> (0 until n.length).map { n.item(it) as Element } }
            .filter { it.getAttribute("translatable") != "false" }
        val strings = elements("string").associate { it.getAttribute("name") to it.textContent }
        val plurals = elements("plurals").associate { p ->
            p.getAttribute("name") to p.getElementsByTagName("item").let { n -> (0 until n.length).map { n.item(it) as Element } }
                .associate { it.getAttribute("quantity") to it.textContent }
        }
        return Res(strings, plurals)
    }

    private val cs = read("values")
    private val en = read("values-en")

    private fun args(text: String): List<String> = Regex("""%(\d+\$)?[sdf]|%%""").findAll(text).map { it.value }.sorted().toList()

    @Test
    fun englishHasEveryCzechKeyAndNothingElse() {
        assertThat(cs.strings.size).isAtLeast(200)
        assertThat(en.strings.keys).containsExactlyElementsIn(cs.strings.keys)
        assertThat(en.plurals.keys).containsExactlyElementsIn(cs.plurals.keys)
    }

    @Test
    fun formatArgumentsMatch() {
        val mismatched = cs.strings.filter { (key, text) -> args(text) != args(en.strings.getValue(key)) }.keys
        assertWithMessage("format arguments differ").that(mismatched).isEmpty()
        cs.plurals.forEach { (key, items) ->
            assertWithMessage("$key other").that(args(en.plurals.getValue(key).getValue("other"))).isEqualTo(args(items.getValue("other")))
        }
    }

    @Test
    fun pluralsCarryTheQuantitiesEachLanguageUses() {
        cs.plurals.forEach { (key, items) -> assertWithMessage(key).that(items.keys).containsAtLeast("one", "few", "many", "other") }
        en.plurals.forEach { (key, items) -> assertWithMessage(key).that(items.keys).containsAtLeast("one", "other") }
    }

    /** Numbers follow the language of the strings (ADR-028): Czech decimal comma, English point. */
    @Test
    fun numberLocaleIsTheFolderLanguage() {
        assertThat(cs.strings["number_locale"]).isEqualTo("cs")
        assertThat(en.strings["number_locale"]).isEqualTo("en")
    }

    @Test
    fun theDisclosureIsTheSpecTextWordForWord() {
        val spec = File(appDir.parentFile, "docs/SPEC.md").readText()
        val section = spec.substring(spec.indexOf("# 30. Google Play Accessibility disclosure"), spec.indexOf("# 31. Onboarding"))
            .lines().joinToString(" ") { it.removePrefix(">").trim() }
        listOf(
            "disclosure_title", "disclosure_why", "disclosure_what", "disclosure_not", "disclosure_local",
            "disclosure_accept", "disclosure_open_settings",
        ).forEach { key ->
            assertWithMessage("$key is not in spec §30").that(section).contains(cs.strings.getValue(key))
        }
    }

    /** Spec §29: the privacy screen opens with the spec's sentence. */
    @Test
    fun thePrivacyIntroIsTheSpecText() {
        val spec = File(appDir.parentFile, "docs/SPEC.md").readText()
        val section = spec.substring(spec.indexOf("# 29. Privacy"), spec.indexOf("# 30. Google Play"))
            .lines().joinToString(" ") { it.removePrefix(">").trim() }
        assertThat(section).contains(cs.strings.getValue("privacy_intro"))
    }
}
