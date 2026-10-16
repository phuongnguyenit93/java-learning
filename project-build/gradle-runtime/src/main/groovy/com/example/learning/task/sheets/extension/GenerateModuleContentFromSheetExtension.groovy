package com.example.learning.task.sheets.extension

import org.gradle.api.provider.Property

/**
 * Module-local configuration for the Google Sheets content generator.
 * sheetId is the tab gid; fileUrl identifies the spreadsheet document.
 */
abstract class GenerateModuleContentFromSheetExtension {

    abstract Property<String> getSheetId()

    abstract Property<String> getFileUrl()
}
