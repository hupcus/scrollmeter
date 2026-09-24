package com.scrollmeter.app.insights

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** D19: the share of the time in app spent scrolling. */
class ScrollShareTest {
    @Test
    fun shareIsRoundedAndClamped() {
        assertThat(ScrollShare.percent(90_000, 600_000)).isEqualTo(15)
        assertThat(ScrollShare.percent(700_000, 600_000)).isEqualTo(100)
        assertThat(ScrollShare.percent(0, 600_000)).isEqualTo(0)
    }

    @Test
    fun unknownOrTooShortHasNoShare() {
        assertThat(ScrollShare.percent(null, 600_000)).isNull()
        assertThat(ScrollShare.percent(10_000, null)).isNull()
        assertThat(ScrollShare.percent(10_000, 59_999)).isNull()
    }
}
