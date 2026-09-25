package com.scrollmeter.app.format

import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Test

/** Spec §25: a custom goal in m or km, within 10 m … 100 km. */
class GoalInputTest {
    private val cs = Locale.forLanguageTag("cs")

    @Test
    fun metresAndKilometresWithEitherSeparator() {
        assertThat(GoalInput.parseMm("750", kilometres = false, cs)).isEqualTo(750_000.0)
        assertThat(GoalInput.parseMm("1,5", kilometres = true, cs)).isEqualTo(1_500_000.0)
        assertThat(GoalInput.parseMm("1.5", kilometres = true, cs)).isEqualTo(1_500_000.0)
        assertThat(GoalInput.parseMm(" 2 ", kilometres = true, cs)).isEqualTo(2_000_000.0)
    }

    @Test
    fun outsideTheRangeOrNotANumberIsRejected() {
        assertThat(GoalInput.parseMm("5", kilometres = false, cs)).isNull()
        assertThat(GoalInput.parseMm("101", kilometres = true, cs)).isNull()
        assertThat(GoalInput.parseMm("abc", kilometres = false, cs)).isNull()
        assertThat(GoalInput.parseMm("5 km", kilometres = false, cs)).isNull()
        assertThat(GoalInput.parseMm("", kilometres = false, cs)).isNull()
        assertThat(GoalInput.parseMm("1,2,3", kilometres = false, cs)).isNull()
    }
}
