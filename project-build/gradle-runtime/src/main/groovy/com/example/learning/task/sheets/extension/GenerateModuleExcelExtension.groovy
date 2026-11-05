package com.example.learning.task.sheets.extension

import org.gradle.api.provider.ListProperty

/** STEP 12: optional selection of languages declared by this module. */
abstract class GenerateModuleExcelExtension {
    abstract ListProperty<String> getLanguageList()
}
