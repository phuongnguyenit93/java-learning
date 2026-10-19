package com.example.learning.task.sheets.extension

import org.gradle.api.provider.Property

/** The destination spreadsheet and the local Excel snapshot language. */
abstract class ImportExcelFromModuleSheetExtension {
    abstract Property<String> getFileUrl()
    abstract Property<String> getLanguage()
}
