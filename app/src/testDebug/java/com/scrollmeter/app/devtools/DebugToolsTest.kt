package com.scrollmeter.app.devtools

import com.google.common.truth.Truth.assertThat
import com.scrollmeter.app.calibration.DisplaySnapshot
import com.scrollmeter.app.measurement.MeasurementResult
import com.scrollmeter.app.measurement.MeasurementSettings
import com.scrollmeter.app.measurement.MeasurementSource
import com.scrollmeter.app.measurement.OWN_PACKAGE
import com.scrollmeter.app.measurement.ScrollMeasurementEngine
import com.scrollmeter.app.measurement.TestPhone
import com.scrollmeter.app.measurement.sample
import java.io.File
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Debug-only event log and CSV (spec §34, §70). Lives in testDebug because the classes are debug-only. */
class DebugToolsTest {
    private val engine = ScrollMeasurementEngine(OWN_PACKAGE, MeasurementSettings(), TestPhone.display)
    private val display = DisplaySnapshot(TestPhone.WIDTH_PX, TestPhone.HEIGHT_PX, TestPhone.XDPI, TestPhone.YDPI, TestPhone.DENSITY_DPI)

    @get:Rule
    val tmp = TemporaryFolder()

    private fun recordingFile() = DebugRecordingFile(File(tmp.root, "debug/recording.csv"))

    private fun result(dy: Int, pkg: String? = "com.example.feed", uptimeMs: Long = 0): MeasurementResult =
        engine.process(sample(dy = dy, packageName = pkg, uptimeMs = uptimeMs))

    @Test
    fun logKeepsTheNewestVisibleEventsAndRecordsMore() {
        val log = DebugEventLog(OWN_PACKAGE, visibleCapacity = 3, recordingCapacity = 5)
        (1..7).forEach { log.onResult(result(dy = it, uptimeMs = it.toLong())) }

        assertThat(log.visible.value.map { it.sample.deltaY }).containsExactly(7, 6, 5).inOrder()
        assertThat(log.snapshot().map { it.sample.deltaY }).containsExactly(3, 4, 5, 6, 7).inOrder()
        assertThat(log.counts.value.overflowed).isEqualTo(2)
        assertThat(log.counts.value.bySource[MeasurementSource.DIRECT_DELTA]).isEqualTo(7)
    }

    @Test
    fun pauseStopsRecordingAndClearEmptiesEverything() {
        val log = DebugEventLog(OWN_PACKAGE)
        log.onResult(result(dy = 10))
        log.setPaused(true)
        log.onResult(result(dy = 20))
        assertThat(log.snapshot()).hasSize(1)

        log.setPaused(false)
        log.clear()
        assertThat(log.snapshot()).isEmpty()
        assertThat(log.visible.value).isEmpty()
        assertThat(log.counts.value.total).isEqualTo(0)
    }

    @Test
    fun ownExcludedEventsStayOutOfTheLog() {
        val log = DebugEventLog(OWN_PACKAGE)
        log.onResult(result(dy = 10, pkg = OWN_PACKAGE))
        assertThat(log.snapshot()).isEmpty()
    }

    @Test
    fun csvHasGeometryHeaderColumnsAndOneRowPerEvent() {
        val results = listOf(result(dy = 1200, uptimeMs = 5), result(dy = 20_000, uptimeMs = 9))
        val csv = DebugCsv.build(results, display, device = "OnePlus CPH2399", appVersion = "0.1.0", overflowed = 0, zone = ZoneOffset.UTC)
        val lines = csv.trim().lines()

        assertThat(lines[0]).startsWith("# scrollmeter-debug")
        assertThat(lines[0]).contains("xdpi=403.411")
        assertThat(lines[0]).contains("max_event_px=10527.2")
        assertThat(lines[1]).isEqualTo(DebugCsv.COLUMNS)
        assertThat(lines).hasSize(4)
        assertThat(lines[2].split(',')).hasSize(DebugCsv.COLUMNS.split(',').size)
        assertThat(lines[2]).contains(",1200,")
        assertThat(lines[2]).endsWith("DIRECT_DELTA,accepted")
        assertThat(lines[3]).endsWith("OUTLIER_REJECTED,rejected")
    }

    /** ColorOS kills the process under memory pressure; the next process must continue the same run. */
    @Test
    fun recordingFileCarriesTheRunAcrossANewProcess() {
        val first = DebugEventLog(OWN_PACKAGE, file = recordingFile())
        listOf(100, 200, 300).forEach { first.onResult(result(dy = it)) }

        val second = DebugEventLog(OWN_PACKAGE, file = recordingFile())
        assertThat(second.counts.value.persisted).isEqualTo(3)
        assertThat(second.counts.value.recorded).isEqualTo(0) // RAM starts empty
        second.onResult(result(dy = 400))
        val rows = second.exportRows()
        assertThat(rows).hasSize(4)
        assertThat(rows.map { it.split(',')[6] }).containsExactly("100", "200", "300", "400").inOrder()

        second.clear()
        assertThat(second.exportRows()).isEmpty()
        assertThat(recordingFile().rowCount).isEqualTo(0)
    }

    @Test
    fun aRowCutShortByAKillIsDroppedOnRead() {
        val file = File(tmp.root, "debug/recording.csv").apply { parentFile.mkdirs() }
        file.writeText(DebugCsv.COLUMNS + "\n" + DebugCsv.row(result(dy = 100)) + "\n" + "2026-09-23T21:47:32.465,1000,com.a,5\n")
        assertThat(DebugRecordingFile(file).readRows()).hasSize(1)
    }

    @Test
    fun exportFromRowsNamesTheDominantApp() {
        val rows = listOf(result(dy = 1, pkg = "a.b"), result(dy = 2, pkg = "a.b"), result(dy = 3, pkg = "c.d")).map { DebugCsv.row(it) }
        assertThat(DebugCsv.dominantPackageOfRows(rows, OWN_PACKAGE)).isEqualTo("a.b")
    }

    @Test
    fun csvFieldsDefuseFormulasAndQuoteSeparators() {
        assertThat(DebugCsv.field("android.widget.FrameLayout")).isEqualTo("android.widget.FrameLayout")
        assertThat(DebugCsv.field("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"")
        assertThat(DebugCsv.field("-2+3")).isEqualTo("\"'-2+3\"")
        assertThat(DebugCsv.field("a,b")).isEqualTo("\"a,b\"")
        assertThat(DebugCsv.field(null)).isEmpty()
    }

    @Test
    fun dominantPackageIgnoresOurOwn() {
        val results = listOf(result(dy = 1, pkg = OWN_PACKAGE), result(dy = 1, pkg = OWN_PACKAGE), result(dy = 1, pkg = "a.b"))
        assertThat(DebugCsv.dominantPackage(results, OWN_PACKAGE)).isEqualTo("a.b")
        assertThat(DebugCsv.dominantPackage(emptyList(), OWN_PACKAGE)).isEqualTo("none")
    }
}
