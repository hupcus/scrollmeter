package com.scrollmeter.app.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import com.scrollmeter.app.apps.AppInfoProvider
import com.scrollmeter.app.data.repository.ScrollRepository
import java.io.File
import java.io.IOException

/** Both files' contents, built once per export. */
class ExportFiles(val perApp: String, val daily: String)

/**
 * Spec §28, D15: builds the CSVs from stored data and hands them out through the Storage Access
 * Framework (the user picks where each file goes, `CreateDocument`) or the share sheet (a
 * non-exported `FileProvider` over `cache/exports/` only). No storage permission. Blocking — call
 * from an I/O dispatcher. What the service has not flushed yet (≤ 10 s) is not in the files.
 */
class CsvExportWriter(
    private val context: Context,
    private val repository: ScrollRepository,
    private val appInfo: AppInfoProvider,
) {
    private val dir get() = File(context.cacheDir, EXPORT_DIR)

    suspend fun build(): ExportFiles {
        val labels = HashMap<String, String>()
        val perApp = CsvExporter.perApp(repository.exportAppDays()) { pkg -> labels.getOrPut(pkg) { appInfo.label(pkg) } }
        return ExportFiles(perApp, CsvExporter.daily(repository.exportDays()))
    }

    /**
     * Writes [content] to the document the user just created through SAF (`CreateDocument` always
     * makes a new one). A failed write deletes it — a half file where the user chose would pass for
     * an export.
     */
    @Throws(IOException::class)
    fun write(uri: Uri, content: String) {
        try {
            val out = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("no output stream for the chosen file")
            out.use { it.write(content.toByteArray(Charsets.UTF_8)) }
        } catch (e: IOException) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            throw e
        }
    }

    /** Both files in the app's cache, offered to whichever app the user picks in the share sheet. */
    @Throws(IOException::class)
    fun shareIntent(files: ExportFiles): Intent {
        deleteCached()
        val dir = dir.apply { mkdirs() }
        val uris = listOf(CsvExporter.PER_APP_FILE to files.perApp, CsvExporter.DAILY_FILE to files.daily).map { (name, content) ->
            val file = File(dir, name).apply { writeText(content, Charsets.UTF_8) }
            FileProvider.getUriForFile(context, authority(context), file)
        }
        val send = Intent(Intent.ACTION_SEND_MULTIPLE)
            .setType("text/csv")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, null)
    }

    /** Old shared copies — on every export and with "Smazat všechna data". */
    fun deleteCached() {
        dir.listFiles()?.forEach { it.delete() }
    }

    companion object {
        const val EXPORT_DIR = "exports"

        /** Declared in the main manifest (ADR-030); `res/xml/export_paths.xml` exposes `cache/exports/` only. */
        fun authority(context: Context) = "${context.packageName}.exports"
    }
}
