package com.scrollmeter.app.export

import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Spec §61: a failed export says so (false) and leaves the data alone. A cancelled one — the screen
 * left mid-way (back, rotation) — is no failure: the cancellation goes on up and nothing is shown.
 * `CancellationException` is an `IllegalStateException`, so it has to be let through first.
 */
suspend fun attemptExport(block: suspend () -> Unit): Boolean = try {
    block()
    true
} catch (e: CancellationException) {
    throw e
} catch (e: IOException) {
    false
} catch (e: RuntimeException) {
    false
}
