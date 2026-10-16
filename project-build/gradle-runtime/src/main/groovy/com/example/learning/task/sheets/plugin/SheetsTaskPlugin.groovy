package com.example.learning.task.sheets.plugin

import com.example.learning.task.sheets.extension.GenerateModuleContentFromSheetExtension
import com.example.learning.task.sheets.task.GenerateModuleContentFromSheetTask
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Registered by TaskSetupPlugin only for modules whose USE_TASK flag
 * is enabled by ModuleOrchestrationPlugin.
 *
 * The task uses ADC at execution time and reads Sheets metadata only.
 */
class SheetsTaskPlugin
        implements Plugin<Project> {

    @Override
    void apply(
            Project project
    ) {

        GenerateModuleContentFromSheetExtension extension =
                project.extensions.create(
                        'generateModuleContentFromSheet',
                        GenerateModuleContentFromSheetExtension
                )

        project.tasks.register(
                'generateModuleContentFromSheet',
                GenerateModuleContentFromSheetTask
        ) { task ->

            task.group =
                    'sheets'

            task.description =
                    'Verify read-only Google Sheets access for a module (content generation pending).'

            task.modulePath.set(
                    project.path
            )

            task.sheetId.set(
                    extension.sheetId
            )

            task.fileUrl.set(
                    extension.fileUrl
            )
        }
    }
}
