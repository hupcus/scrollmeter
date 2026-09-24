package com.scrollmeter.app.ui.settings

import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.scrollmeter.app.AppGraph
import com.scrollmeter.app.R
import com.scrollmeter.app.export.CsvExporter
import com.scrollmeter.app.export.ExportFiles
import com.scrollmeter.app.ui.components.ScreenHeader
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Spec §28, D15: save either file where the user chooses (Storage Access Framework — no storage
 * permission), or share both. The files are built from the stored data at the moment of the tap.
 */
@Composable
fun ExportScreen(graph: AppGraph, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val writer = graph.csvExportWriter
    var busy by remember { mutableStateOf(false) }

    fun launchExport(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            val ok = try {
                block()
                true
            } catch (e: IOException) {
                false
            } catch (e: RuntimeException) {
                // Spec §61: a failed export says so and leaves the data alone.
                false
            }
            busy = false
            if (!ok) Toast.makeText(context, R.string.export_failed, Toast.LENGTH_LONG).show()
        }
    }

    fun save(uri: Uri?, pick: (ExportFiles) -> String) {
        uri ?: return
        launchExport {
            withContext(Dispatchers.IO) { writer.write(uri, pick(writer.build())) }
            Toast.makeText(context, R.string.export_saved, Toast.LENGTH_SHORT).show()
        }
    }

    val savePerApp = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { save(it) { f -> f.perApp } }
    val saveDaily = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { save(it) { f -> f.daily } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(stringResource(R.string.export_title), onBack)
        Text(stringResource(R.string.export_intro), style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = { savePerApp.launch(CsvExporter.PER_APP_FILE) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.export_save_per_app))
        }
        OutlinedButton(onClick = { saveDaily.launch(CsvExporter.DAILY_FILE) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.export_save_daily))
        }
        Button(
            onClick = {
                launchExport {
                    val intent = withContext(Dispatchers.IO) { writer.shareIntent(writer.build()) }
                    try {
                        context.startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        throw IOException("no app to share with", e)
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.export_share)) }
    }
}
