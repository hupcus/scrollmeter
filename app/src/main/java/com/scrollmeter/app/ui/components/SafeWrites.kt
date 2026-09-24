package com.scrollmeter.app.ui.components

import android.content.Context
import android.widget.Toast
import com.scrollmeter.app.R
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Spec §61: a setting that cannot be written (a DataStore I/O error) says so instead of taking the
 * app down — an exception escaping a `rememberCoroutineScope` launch is uncaught. [onFailure] runs
 * after the message, e.g. to re-enable a button.
 */
fun CoroutineScope.launchWrite(context: Context, onFailure: () -> Unit = {}, write: suspend () -> Unit): Job = launch {
    try {
        write()
    } catch (e: IOException) {
        Toast.makeText(context, R.string.settings_write_failed, Toast.LENGTH_LONG).show()
        onFailure()
    }
}
