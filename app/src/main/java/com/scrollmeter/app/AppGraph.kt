package com.scrollmeter.app

import android.content.Context

/**
 * Lazy singletons shared by the UI and the accessibility service. Constructors stay plain so
 * every piece can be built directly in tests.
 */
class AppGraph(context: Context) {
    val appContext: Context = context.applicationContext
}
