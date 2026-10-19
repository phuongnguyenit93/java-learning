package com.example.learning.task.sheets.extension

import org.gradle.api.provider.Property

/** A spreadsheet URL, an explicit tab URL (gid), and a language folder. */
abstract class ExportModuleSheetToExcelExtension {
    abstract Property<String> getFileURL()
    abstract Property<String> getSheetURL()
    abstract Property<String> getLanguage()
}
