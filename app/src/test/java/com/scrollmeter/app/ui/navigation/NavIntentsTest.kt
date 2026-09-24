package com.scrollmeter.app.ui.navigation

import android.content.Intent
import android.os.Bundle
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Another app must not open a screen of ours through Navigation's deep-link extras. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NavIntentsTest {
    @Test
    fun navigationDeepLinkExtrasAreRemovedAndOthersKept() {
        val intent = Intent(Intent.ACTION_MAIN)
            .putExtra("android-support-nav:controller:deepLinkIds", intArrayOf(1, 2))
            .putExtra("android-support-nav:controller:deepLinkArgs", arrayListOf<Bundle>())
            .putExtra("devtool", "testlist")
        val scrubbed = NavIntents.scrubbed(intent)
        assertThat(scrubbed.extras!!.keySet()).containsExactly("devtool")
    }

    @Test
    fun anIntentWithoutExtrasIsUntouched() {
        val intent = Intent(Intent.ACTION_MAIN)
        assertThat(NavIntents.scrubbed(intent).extras).isNull()
    }
}
