package com.scrollmeter.app.format

import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Test

/** Spec §25, ADR-036: a custom limit in m or km, within 10 m … 100 km. */
class LimitInputTest {
    private val cs = Locale.forLanguageTag("cs")

    @Test
    fun metresAndKilometresWithEitherSeparator() {
        assertThat(LimitInput.parseMm("750", kilometres = false, cs)).isEqualTo(750_000.0)
        assertThat(LimitInput.parseMm("1,5", kilometres = true, cs)).isEqualTo(1_500_000.0)
        assertThat(LimitInput.parseMm("1.5", kilometres = true, cs)).isEqualTo(1_500_000.0)
        assertThat(LimitInput.parseMm(" 2 ", kilometres = true, cs)).isEqualTo(2_000_000.0)
    }

    @Test
    fun outsideTheRangeOrNotANumberIsRejected() {
        assertThat(LimitInput.parseMm("5", kilometres = false, cs)).isNull()
        assertThat(LimitInput.parseMm("101", kilometres = true, cs)).isNull()
        assertThat(LimitInput.parseMm("abc", kilometres = false, cs)).isNull()
        assertThat(LimitInput.parseMm("5 km", kilometres = false, cs)).isNull()
        assertThat(LimitInput.parseMm("", kilometres = false, cs)).isNull()
        assertThat(LimitInput.parseMm("1,2,3", kilometres = false, cs)).isNull()
    }
}
