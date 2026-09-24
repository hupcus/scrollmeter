package com.scrollmeter.app.export

import com.google.common.truth.Truth.assertThat
import com.scrollmeter.app.data.model.ExportAppDay
import com.scrollmeter.app.data.model.ExportDay
import org.junit.Test

/** Spec §28: header, escaping, numbers; ADR-021 extra columns; unknown = empty. */
class CsvExporterTest {
    private fun lines(csv: String): List<String> {
        assertThat(csv).startsWith(CsvExporter.BOM)
        assertThat(csv).endsWith("\r\n")
        return csv.removePrefix(CsvExporter.BOM).removeSuffix("\r\n").split("\r\n")
    }

    @Test
    fun perAppFollowsTheSpecExampleAndAddsTheTimeColumns() {
        val row = ExportAppDay("2026-09-23", "com.instagram.android", 187_450.0, 1_263, 14, 3, 0, 2_700_000, 900_000)
        val csv = lines(CsvExporter.perApp(listOf(row)) { "Instagram" })
        assertThat(csv[0]).isEqualTo(
            "date,package_name,app_name,distance_mm,distance_m,event_count,fallback_count,unmeasurable_count,outlier_count,foreground_ms,active_scroll_ms",
        )
        assertThat(csv[1]).isEqualTo("2026-09-23,com.instagram.android,Instagram,187450,187.45,1280,14,3,0,2700000,900000")
    }

    @Test
    fun unknownValuesAreEmptyNeverZero() {
        val timeOnly = ExportAppDay("2026-09-23", "com.google.android.youtube", null, null, null, null, null, 600_000, null)
        assertThat(lines(CsvExporter.perApp(listOf(timeOnly)) { "YouTube" })[1])
            .isEqualTo("2026-09-23,com.google.android.youtube,YouTube,,,,,,,600000,")
    }

    @Test
    fun namesAreQuotedAndFormulasDefused() {
        assertThat(CsvExporter.field("Mapy, navigace")).isEqualTo("\"Mapy, navigace\"")
        assertThat(CsvExporter.field("Say \"hi\"")).isEqualTo("\"Say \"\"hi\"\"\"")
        assertThat(CsvExporter.field("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"")
        assertThat(CsvExporter.field("-2+3")).isEqualTo("'-2+3")
        assertThat(CsvExporter.field("Nastavení")).isEqualTo("Nastavení")
        assertThat(CsvExporter.field("")).isEqualTo("")
    }

    @Test
    fun dailySummaryUsesMetresWithADot() {
        val csv = lines(CsvExporter.daily(listOf(ExportDay("2026-09-23", 1_234_567.0, 4_200), ExportDay("2026-09-24", 5.0, 1))))
        assertThat(csv).containsExactly("date,total_distance_m,events", "2026-09-23,1234.57,4200", "2026-09-24,0.01,1").inOrder()
    }
}
