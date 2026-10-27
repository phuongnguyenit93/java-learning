package com.example.learning.task.sheets.plugin

import com.example.learning.task.sheets.extension.GenerateModuleContentFromSheetByLocationExtension
import com.example.learning.task.sheets.export.plugin.ModuleSheetExcelExportPlugin
import com.example.learning.task.sheets.importexcel.plugin.ModuleSheetExcelImportPlugin
import com.example.learning.task.sheets.task.GenerateModuleContentFromSheetByLocationTask
import com.example.learning.task.sheets.task.FillIntroductionTask
import com.example.learning.task.sheets.task.SyncModuleContentToExcelTask
import com.example.learning.task.sheets.task.CloneModuleViToEnTask
import com.example.learning.task.sheets.task.ModuleTranslationAuditTask
import com.example.learning.task.sheets.task.ModuleEnSheetPublishTask
import com.example.learning.task.sheets.task.PublishOptionalVideoEnTask
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

        project.pluginManager.apply(ModuleSheetExcelExportPlugin)
        project.pluginManager.apply(ModuleSheetExcelImportPlugin)

        project.tasks.register('cloneModuleViToEn', CloneModuleViToEnTask) { task ->
            task.group = 'sheets'
            task.description = 'STEP 13: replace module EN content with validated VI structure (DRY_RUN by default).'
            task.mode.convention(project.providers.gradleProperty('sheetMode').orElse('DRY_RUN'))
        }

        [
                prepareModuleTranslation: 'PREPARE',
                validateModuleTranslation: 'VALIDATE'
        ].each { String taskName, String operation ->
            project.tasks.register(taskName, ModuleTranslationAuditTask) { task ->
                task.group = 'sheets'
                task.description = 'STEP 14: ' + operation +
                        ' translation files (never translates text or writes Google Sheets).'
                task.operation.convention(operation)
                task.mode.convention(project.providers.gradleProperty('translationMode')
                        .orElse('DRY_RUN'))
                task.runId.convention(project.providers.gradleProperty('translationRunId')
                        .orElse(''))
            }
        }

        // STEP 15: deterministic module EN publishing; the template is cloned
        // first so that formatting/conditional formatting remain native.
        [
                cloneModuleTemplateToEnTab: 'CLONE',
                syncModuleEnToSheet: 'SYNC',
                validateModuleEnSheet: 'VERIFY',
                exportPublishedEnSheet: 'EXPORT',
                publishModuleEnSheet: 'PUBLISH'
        ].each { String taskName, String phase ->
            project.tasks.register(taskName, ModuleEnSheetPublishTask) { task ->
                task.group = 'sheets'
                task.description = 'STEP 15 ' + phase + ' (code only, one module, ' +
                        'DRY_RUN by default).'
                task.phase.convention(phase)
                task.fileUrl.set(project.providers.gradleProperty('fileUrl')
                        .orElse(project.providers.gradleProperty('fileURL')))
                task.viGid.set(project.providers.gradleProperty('viGid'))
                task.mode.convention(project.providers.gradleProperty('sheetMode').orElse('DRY_RUN'))
            }
        }

        // Legacy reverse exporters are still available as separate offline tasks.
        project.tasks.register('publishOptionalVideoEn', PublishOptionalVideoEnTask) { task ->
            task.group = 'sheets'
            task.description = 'Optional post-FINAL Video EN only; sync X:AA and re-export EN Excel.'
            task.fileUrl.set(project.providers.gradleProperty('fileUrl')
                    .orElse(project.providers.gradleProperty('fileURL')))
            task.viGid.set(project.providers.gradleProperty('viGid'))
            task.mode.convention(project.providers.gradleProperty('sheetMode').orElse('DRY_RUN'))
        }

        [
                syncModuleIntroductionToExcel: 'INTRODUCTION',
                syncModuleRoadmapToExcel: 'ROADMAP',
                syncModuleReadmeToExcel: 'README',
                syncModuleInterviewToExcel: 'INTERVIEW',
                syncModuleQuizToExcel: 'QUIZ',
                syncModuleVideoToExcel: 'VIDEO',
                syncModuleReferenceToExcel: 'REFERENCE',
                syncModuleSummaryToExcel: 'SUMMARY',
                syncModuleContentToExcel: 'ALL'
        ].each { String name, String part ->
            project.tasks.register(name, SyncModuleContentToExcelTask) { task ->
                task.group = 'sheets'
                task.description = 'Sync module EN ' + part + ' into content/sheet/en/module.xlsx (DRY_RUN by default).'
                task.part.convention(part)
                task.mode.convention(project.providers.gradleProperty('sheetMode').orElse('DRY_RUN'))
            }
        }

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
                generateModuleInterviewFromSheetByLocation: 'INTERVIEW',
                generateModuleQuizFromSheetByLocation: 'QUIZ',
                generateModuleReferenceFromSheetByLocation: 'REFERENCE',
                generateModuleSummaryFromSheetByLocation: 'SUMMARY'
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
