package com.scrollmeter.app.export

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ExportAttemptTest {
    @Test
    fun successAndFailures() = runTest {
        assertThat(attemptExport { }).isTrue()
        assertThat(attemptExport { throw IOException("disk full") }).isFalse()
        assertThat(attemptExport { throw SecurityException("provider refused") }).isFalse()
    }

    /** Back or rotation during an export: no "Export se nepovedl" for a screen that is gone. */
    @Test
    fun aCancelledExportIsNotReportedAsFailed() = runTest {
        var result: Boolean? = null
        val started = CompletableDeferred<Unit>()
        val job = launch {
            result = attemptExport {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        started.await()
        job.cancel()
        job.join()
        assertThat(job.isCancelled).isTrue()
        assertThat(result).isNull()
    }
}
