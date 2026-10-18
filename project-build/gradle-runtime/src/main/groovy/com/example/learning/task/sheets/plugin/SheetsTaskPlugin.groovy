package com.example.learning.task.sheets.plugin

import com.example.learning.task.sheets.extension.GenerateModuleContentFromSheetByLocationExtension
import com.example.learning.task.sheets.task.GenerateModuleContentFromSheetByLocationTask
import com.example.learning.task.sheets.task.FillIntroductionTask
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Registered by TaskSetupPlugin only for modules whose USE_TASK flag
 * is enabled by ModuleOrchestrationPlugin.
 *
 * Credentials are resolved at task execution; I3 must match the module.
 */
class SheetsTaskPlugin
        implements Plugin<Project> {

    @Override
    void apply(
            Project project
    ) {

        GenerateModuleContentFromSheetByLocationExtension extension =
                project.extensions.create(
                        'generateModuleContentFromSheetByLocation',
                        GenerateModuleContentFromSheetByLocationExtension
                )

        project.tasks.register(
                'generateModuleContentFromSheetByLocation',
                GenerateModuleContentFromSheetByLocationTask
        ) { task ->

            task.group =
                    'sheets'

            task.description =
                    'Import selected module Roadmap, README, Video and Interview from Sheet (default DRY_RUN).'

            task.modulePath.set(
                    project.path
            )

            task.sheetId.set(
                    project.providers.gradleProperty('sheetID')
                            .orElse(project.providers.gradleProperty('sheetId'))
                            .orElse(extension.sheetId)
            )

            task.fileUrl.set(
                    project.providers.gradleProperty('fileUrl')
                            .orElse(project.providers.gradleProperty('sheetFileUrl'))
                            .orElse(extension.fileUrl)
            )
            task.mode.convention(
                    project.providers.gradleProperty('sheetMode').orElse('DRY_RUN')
            )
            task.importPart.convention('ALL')
        }

        project.tasks.register('fillIntroduction', FillIntroductionTask) { task ->
            task.group = 'sheets'
            task.description = 'Fill only content/readme/docs/vi/BASE.md from Sheet B17 (default DRY_RUN).'
            task.sheetId.set(project.providers.gradleProperty('sheetID')
                    .orElse(project.providers.gradleProperty('sheetId'))
                    .orElse(extension.sheetId))
            task.fileUrl.set(project.providers.gradleProperty('fileUrl')
                    .orElse(project.providers.gradleProperty('sheetFileUrl'))
                    .orElse(extension.fileUrl))
            task.mode.convention(project.providers.gradleProperty('sheetMode').orElse('DRY_RUN'))
        }

        [
                generateModuleRoadmapFromSheetByLocation: 'ROADMAP',
                generateModuleReadmeFromSheetByLocation : 'README',
                generateModuleVideoFromSheetByLocation  : 'VIDEO',
                generateModuleInterviewFromSheetByLocation: 'INTERVIEW'
        ].each { String taskName, String part ->
            project.tasks.register(
                    taskName, GenerateModuleContentFromSheetByLocationTask
            ) { task ->
                task.group = 'sheets'
                task.description =
                        "Import ONLY the selected module ${part} from Sheet (default DRY_RUN)."
                task.modulePath.set(project.path)
                task.sheetId.set(
                        project.providers.gradleProperty('sheetID')
                                .orElse(project.providers.gradleProperty('sheetId'))
                                .orElse(extension.sheetId)
                )
                task.fileUrl.set(
                        project.providers.gradleProperty('fileUrl')
                                .orElse(project.providers.gradleProperty('sheetFileUrl'))
                                .orElse(extension.fileUrl)
                )
                task.mode.convention(
                        project.providers.gradleProperty('sheetMode').orElse('DRY_RUN')
                )
                task.importPart.convention(part)
            }
        }
    }
}
