package com.scrollmeter.app.data

import com.google.common.truth.Truth.assertThat
import com.scrollmeter.app.data.model.DateRange
import java.time.LocalDate
import org.junit.Test

/** Spec §19: weeks run Monday–Sunday, months are calendar months. */
class DateRangeTest {
    @Test
    fun weekIsMondayToSunday() {
        assertThat(DateRange.week(LocalDate.parse("2026-09-24"))).isEqualTo(DateRange(LocalDate.parse("2026-09-21"), LocalDate.parse("2026-09-27")))
        assertThat(DateRange.week(LocalDate.parse("2026-09-21")).from).isEqualTo(LocalDate.parse("2026-09-21"))
        assertThat(DateRange.week(LocalDate.parse("2026-09-27")).from).isEqualTo(LocalDate.parse("2026-09-21"))
    }

    @Test
    fun monthIsTheCalendarMonth() {
        assertThat(DateRange.month(LocalDate.parse("2028-02-10"))).isEqualTo(DateRange(LocalDate.parse("2028-02-01"), LocalDate.parse("2028-02-29")))
    }

    @Test
    fun keysAreIsoDates() {
        assertThat(DateRange.day(LocalDate.parse("2026-01-05")).fromKey).isEqualTo("2026-01-05")
    }

    @Test
    fun allCoversEveryPlausibleDayAsAStringRange() {
        assertThat(DateRange.ALL.fromKey < "2000-01-01").isTrue()
        assertThat(DateRange.ALL.toKey > "2999-12-31").isTrue()
    }
}
