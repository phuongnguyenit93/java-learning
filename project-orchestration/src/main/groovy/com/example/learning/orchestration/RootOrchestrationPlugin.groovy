package com.example.learning.orchestration

import com.example.learning.setup.root.catalog.plugin.CatalogSetupPlugin
import com.example.learning.setup.root.cleanup.plugin.CleanupSetupPlugin
import com.example.learning.setup.root.database.plugin.DatabaseSetupPlugin
import com.example.learning.setup.root.readmeMetadata.plugin.ReadmeMetadataSetupPlugin
import com.example.learning.setup.root.structure.plugin.StructureSetupPlugin
import com.example.learning.task.sheets.task.GenerateModuleContentFromSheetByFilesTask
import com.example.learning.task.sheets.task.NormalizeInterviewQuestionsFromSheetTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project


class RootOrchestrationPlugin
        implements Plugin<Project> {

    @Override
    void apply(
            Project project
    ) {

        if (
                project !=
                        project.rootProject
        ) {

            throw new GradleException(
                    """
RootOrchestrationPlugin must only be applied to root project.

Current project:
${project.path}
""".stripIndent()
            )
        }


        project.pluginManager.apply(
                CatalogSetupPlugin
        )


        project.pluginManager.apply(
                StructureSetupPlugin
        )


        project.pluginManager.apply(
                ReadmeMetadataSetupPlugin
        )


        project.pluginManager.apply(
                DatabaseSetupPlugin
        )

        project.pluginManager.apply(
                CleanupSetupPlugin
        )

        // Root-only: discovers all tabs except Template, no Gradle extension.
        project.tasks.register(
                'generateModuleContentFromSheetByFiles',
                GenerateModuleContentFromSheetByFilesTask
        ) { task ->
            task.group = 'sheets'
            task.description =
                    'Import all spreadsheet tabs except Template (default DRY_RUN).'
            task.fileUrl.convention(
                    project.providers.gradleProperty('sheetFileUrl')
            )
            task.mode.convention(
                    project.providers.gradleProperty('sheetMode').orElse('DRY_RUN')
            )
        }

        project.tasks.register(
                'normalizeInterviewQuestionsFromSheet',
                NormalizeInterviewQuestionsFromSheetTask
        ) { task ->
            task.group = 'sheets'
            task.description =
                    'Normalize Interview JSON T:W into full schema on one tab (default DRY_RUN).'
            task.fileUrl.convention(
                    project.providers.gradleProperty('fileUrl')
                            .orElse(project.providers.gradleProperty('sheetFileUrl'))
            )
            task.sheetId.convention(
                    project.providers.gradleProperty('sheetID')
                            .orElse(project.providers.gradleProperty('sheetId'))
            )
            task.mode.convention(
                    project.providers.gradleProperty('interviewMode').orElse('DRY_RUN')
            )
        }
    }
}
