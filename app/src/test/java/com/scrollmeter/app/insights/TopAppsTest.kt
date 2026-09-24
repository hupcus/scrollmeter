package com.scrollmeter.app.insights

import com.google.common.truth.Truth.assertThat
import com.scrollmeter.app.data.model.AppSummary
import org.junit.Test

/** Spec §21: top apps today, the rest as "Ostatní". */
class TopAppsTest {
    private fun app(pkg: String, mm: Double?, fg: Long? = null, active: Long? = null) =
        AppSummary(pkg, mm, active, fg, null, null, null, null)

    @Test
    fun appsWithoutDistanceAreLeftOutAndTheRestIsSorted() {
        val top = TopApps.of(listOf(app("video", null, fg = 900_000), app("b", 5.0), app("a", 50.0), app("zero", 0.0)))
        assertThat(top.apps.map { it.packageName }).containsExactly("a", "b").inOrder()
        assertThat(top.other).isNull()
    }

    @Test
    fun theTailBecomesOneOtherRow() {
        val apps = (1..7).map { app("p$it", it * 10.0, fg = if (it == 1) null else 60_000, active = 1_000) }
        val top = TopApps.of(apps)
        assertThat(top.apps.map { it.packageName }).containsExactly("p7", "p6", "p5", "p4").inOrder()
        assertThat(top.other!!.distanceMm).isEqualTo(60.0) // p3 + p2 + p1
        assertThat(top.other!!.foregroundMs).isEqualTo(120_000)
        assertThat(top.other!!.activeScrollMs).isEqualTo(3_000)
    }

    @Test
    fun oneAppMoreThanTheLimitIsShownByName() {
        val top = TopApps.of((1..5).map { app("p$it", it * 10.0) })
        assertThat(top.apps).hasSize(5)
        assertThat(top.other).isNull()
    }

    @Test
    fun unknownTimesStayUnknownInOther() {
        val top = TopApps.of((1..6).map { app("p$it", it * 10.0) })
        assertThat(top.other!!.foregroundMs).isNull()
        assertThat(top.other!!.activeScrollMs).isNull()
    }
}
