package com.scrollmeter.app.devtools

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.scrollmeter.app.AppGraph
import com.scrollmeter.app.BuildConfig
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Writes the recorded events to app-specific external storage — no storage permission, and
 * `adb pull /sdcard/Android/data/<applicationId>/files/debug/` collects every export.
 */
object DebugExport {
    private val fileTime = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT)

    fun write(context: Context, graph: AppGraph, log: DebugEventLog): File {
        val results = log.snapshot()
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "debug").apply { mkdirs() }
        val app = DebugCsv.dominantPackage(results, graph.ownPackage)
        val file = File(dir, "scrollmeter-${fileTime.format(LocalDateTime.now())}-$app.csv")
        file.writeText(
            DebugCsv.build(
                results = results,
                display = graph.displayMetricsProvider.read(),
                device = "${Build.MANUFACTURER} ${Build.MODEL} API${Build.VERSION.SDK_INT}",
                appVersion = BuildConfig.VERSION_NAME,
                overflowed = log.counts.value.overflowed,
            ),
        )
        return file
    }

    fun shareIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.devtools.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/csv")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, file.name)
    }
}
