package com.scrollmeter.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Keeps the JVM unit-test task alive from Phase 0; the measurement engine tests arrive in Phase 1. */
class BuildSanityTest {
    @Test
    fun applicationIdBaseIsStable() {
        assertThat(BuildConfig.APPLICATION_ID).startsWith("com.scrollmeter.app")
    }
}
