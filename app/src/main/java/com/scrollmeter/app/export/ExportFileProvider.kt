package com.scrollmeter.app.export

import androidx.core.content.FileProvider

/**
 * The share sheet's provider (D15, ADR-030): its own class so it cannot merge with the debug
 * build's `FileProvider`. Its paths (`cache/exports/` only) are the manifest meta-data, not a
 * constructor argument: the static `FileProvider.getUriForFile` reads the meta-data alone, so a
 * provider configured in code fails the moment a file is shared.
 */
class ExportFileProvider : FileProvider()
