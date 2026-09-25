package com.scrollmeter.app.format

import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Test

/** PLAN Phase 4: an unknown time is "—", never "0 min"; pace needs at least a minute. */
class TimeFormatterTest {
    private val cs = Locale.forLanguageTag("cs")
    private val min = 60_000L

    @Test
    fun durations() {
        assertThat(TimeFormatter.duration(null)).isEqualTo("—")
        assertThat(TimeFormatter.duration(0)).isEqualTo("< 1 min")
        assertThat(TimeFormatter.duration(59_999)).isEqualTo("< 1 min")
        assertThat(TimeFormatter.duration(12 * min + 59_000)).isEqualTo("12 min")
        assertThat(TimeFormatter.duration(60 * min - 1)).isEqualTo("59 min")
        assertThat(TimeFormatter.duration(60 * min)).isEqualTo("1 h")
        assertThat(TimeFormatter.duration(65 * min)).isEqualTo("1 h 5 min")
        assertThat(TimeFormatter.duration(-1)).isEqualTo("—")
    }

    /** ADR-036: from 24 h the days come first; zero parts are left out. */
    @Test
    fun daysHoursMinutes() {
        val hour = 60 * min
        assertThat(TimeFormatter.duration(23 * hour + 59 * min)).isEqualTo("23 h 59 min")
        assertThat(TimeFormatter.duration(24 * hour - 1)).isEqualTo("23 h 59 min")
        assertThat(TimeFormatter.duration(24 * hour)).isEqualTo("1 d")
        assertThat(TimeFormatter.duration(25 * hour + min)).isEqualTo("1 d 1 h 1 min")
        assertThat(TimeFormatter.duration(24 * hour + 5 * min)).isEqualTo("1 d 5 min")
        assertThat(TimeFormatter.duration(25 * hour)).isEqualTo("1 d 1 h")
        assertThat(TimeFormatter.duration(48 * hour)).isEqualTo("2 d")
        assertThat(TimeFormatter.duration(47 * hour + 59 * min)).isEqualTo("1 d 23 h 59 min")
    }

    @Test
    fun pace() {
        assertThat(TimeFormatter.pace(120_000.0, 10 * min, cs)).isEqualTo("12 m/min")
        assertThat(TimeFormatter.pace(45_000.0, 10 * min, cs)).isEqualTo("4,5 m/min")
        assertThat(TimeFormatter.pace(45_000.0, 59_000, cs)).isEqualTo("—")
        assertThat(TimeFormatter.pace(null, 10 * min, cs)).isEqualTo("—")
        assertThat(TimeFormatter.pace(45_000.0, null, cs)).isEqualTo("—")
    }
}
