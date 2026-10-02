package com.example.learning.task.readme.plugin

import com.example.learning.task.readme.translate.config.TranslationProvider
import com.example.learning.task.readme.extension.MarkdownTranslationExtension
import com.example.learning.task.readme.translate.service.path.MarkdownPathResolver
import com.example.learning.task.readme.translate.task.TranslateMarkdownTask
import com.example.learning.task.readme.extension.GenerateInternalReadmeMenuForFileExtension
import com.example.learning.task.readme.internalMenu.task.GenerateInternalReadmeMenuForFileTask
import com.example.learning.task.readme.internalMenu.task.GenerateInternalReadmeMenuForModuleTask
import com.example.learning.task.readme.finalReadme.task.GenerateFinalReadmeTask
import com.example.learning.utils.ProjectPropertyUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider

class ReadmeTaskPlugin
        implements Plugin<Project> {

    @Override
    void apply(
            Project project
    ) {

        GenerateInternalReadmeMenuForFileExtension internalMenuForFileExtension =
                project.extensions.create(
                        'generateInternalReadmeMenuForFile',
                        GenerateInternalReadmeMenuForFileExtension
                )

        project.tasks.register('generateInternalReadmeMenuForFile', GenerateInternalReadmeMenuForFileTask) { task ->

            task.group = 'documentation'
            task.description = 'Generate internal navigation menu and collapsible sections for one README Markdown file.'
            task.location.set(internalMenuForFileExtension.location)
        }

        project.tasks.register('generateInternalReadmeMenuForModule', GenerateInternalReadmeMenuForModuleTask) { task ->

            task.group = 'documentation'
            task.description = 'Generate internal README menus for all menu Markdown files in MODULE_LANGUAGE.'
            task.languages.set(
                    project.provider {
                        ProjectPropertyUtils.getStringList(
                                project,
                                'MODULE_LANGUAGE'
                        )
                    }
            )
        }

        project.tasks.register('generateFinalReadme', GenerateFinalReadmeTask) { task ->

            task.group = 'documentation'
            task.description = 'Generate LIST.md and final README files for MODULE_LANGUAGE.'
            task.languages.set(
                    project.provider {
                        ProjectPropertyUtils.getStringList(
                                project,
                                'MODULE_LANGUAGE'
                        )
                    }
            )
        }


        def markdownTranslationExtension = project.extensions.create('markdownTranslation', MarkdownTranslationExtension)
        markdownTranslationExtension.provider.convention('azure')

        project.tasks.register('translateMarkdown', TranslateMarkdownTask) { task ->

            MarkdownPathResolver pathResolver = new MarkdownPathResolver()

            task.group = 'documentation'
            task.description = 'Translate Markdown using Azure Translator'
            task.inputLanguage.set(markdownTranslationExtension.inputLanguage)
            task.outputLanguage.set(markdownTranslationExtension.outputLanguage)
            task.fileLocation.set(markdownTranslationExtension.fileLocation)

            // ======================================
            // Provider
            // ======================================

            task.provider.set(
                    markdownTranslationExtension.provider.map { provider ->
                        TranslationProvider.from(provider)
                    } as Provider<? extends TranslationProvider>
            )

            task.inputFile.set(
                    project.layout.file(
                            project.provider {
                                pathResolver.resolve(
                                        project.projectDir,
                                        markdownTranslationExtension
                                                .inputLanguage
                                                .get(),
                                        markdownTranslationExtension
                                                .fileLocation
                                                .get()
                                )
                            }
                    )
            )


            task.outputFile.set(
                    project.layout.file(
                        project.provider {
                            pathResolver.resolve(
                                    project.projectDir,
                                    markdownTranslationExtension
                                            .outputLanguage
                                            .get(),
                                    markdownTranslationExtension
                                            .fileLocation
                                            .get()
                            )
                        }
                    )
            )
        }





    }
}
