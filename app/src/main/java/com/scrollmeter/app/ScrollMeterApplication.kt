package com.scrollmeter.app

import android.app.Application

/**
 * Owns the manual dependency graph (no DI framework — ADR-003). Components reach it through
 * `application as ScrollMeterApplication`, including the accessibility service.
 */
class ScrollMeterApplication : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
}
