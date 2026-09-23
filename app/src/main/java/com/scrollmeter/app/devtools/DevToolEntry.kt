package com.scrollmeter.app.devtools

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import com.scrollmeter.app.AppGraph

/**
 * A developer screen offered from the home screen. The list comes from `DevTools`, which has
 * one implementation in src/debug and an empty one in src/release, so no debug screen, raw
 * event log or logcat line ever ships in a release build (ADR-010).
 */
class DevToolEntry(
    /** Stable id; a debug build opens the tool directly with `am start … --es devtool <key>`. */
    val key: String,
    @get:StringRes val titleRes: Int,
    val content: @Composable (graph: AppGraph, onBack: () -> Unit) -> Unit,
)
