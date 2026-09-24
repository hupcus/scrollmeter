package com.scrollmeter.app.apps

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** A route argument that is not a package name must never reach the screen as an app label. */
class PackageNamesTest {
    @Test
    fun realPackageNamesPass() {
        assertThat(PackageNames.isValid("com.android.chrome")).isTrue()
        assertThat(PackageNames.isValid("com.google.android.apps.nexuslauncher")).isTrue()
        assertThat(PackageNames.isValid("org.telegram.messenger_web")).isTrue()
    }

    @Test
    fun anythingElseFails() {
        assertThat(PackageNames.isValid("Váš účet byl napaden, volejte 123")).isFalse()
        assertThat(PackageNames.isValid("chrome")).isFalse()
        assertThat(PackageNames.isValid("com..chrome")).isFalse()
        assertThat(PackageNames.isValid("com.1chrome")).isFalse()
        assertThat(PackageNames.isValid("a." + "b".repeat(300))).isFalse()
        assertThat(PackageNames.isValid("")).isFalse()
    }
}
