package com.scrollmeter.app.devtools

import java.io.File

/**
 * The debug log on disk: app-private `files/debug/recording.csv`, the column line plus one
 * [DebugCsv.row] per logged event, written through on every event. ColorOS's memory guard kills
 * the service's process under memory pressure (measured 2026-09-23, ADR-017); the system restarts
 * the service within seconds, and this file carries the run across the restart until Clear.
 *
 * Not thread-safe on its own: [DebugEventLog] calls it under its lock.
 */
class DebugRecordingFile(private val file: File) {
    var rowCount: Int = countRows()
        private set

    fun append(row: String) {
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.writeText(DebugCsv.COLUMNS + "\n")
        }
        file.appendText(row + "\n")
        rowCount++
    }

    fun clear() {
        file.delete()
        rowCount = 0
    }

    /** Data lines, oldest first; a line cut short by a kill mid-write is dropped. */
    fun readRows(): List<String> {
        if (!file.exists()) return emptyList()
        val columns = DebugCsv.COLUMNS.count { it == ',' }
        return file.readLines().drop(1).filter { line -> line.count { it == ',' } >= columns }
    }

    private fun countRows(): Int =
        if (file.exists()) file.useLines { lines -> (lines.count() - 1).coerceAtLeast(0) } else 0
}
