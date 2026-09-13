package com.example.learning.task.readme.plugin

import com.example.learning.task.readme.translate.config.TranslationProvider
import com.example.learning.task.readme.extension.MarkdownTranslationExtension
import com.example.learning.task.readme.translate.service.path.MarkdownPathResolver
import com.example.learning.task.readme.translate.task.TranslateMarkdownTask
import com.example.learning.task.readme.extension.GenerateInternalReadmeMenuExtension
import com.example.learning.task.readme.internalMenu.task.GenerateInternalReadmeMenuTask
import com.example.learning.task.readme.extension.GenerateFinalReadmeExtension
import com.example.learning.task.readme.finalReadme.task.GenerateFinalReadmeTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider

class ReadmeTaskPlugin
        implements Plugin<Project> {

    @Override
    void apply(
            Project project
    ) {

        GenerateInternalReadmeMenuExtension internalMenuExtension =
                project.extensions.create(
                        'generateInternalReadmeMenu',
                        GenerateInternalReadmeMenuExtension
                )

        project.tasks.register('generateInternalReadmeMenu', GenerateInternalReadmeMenuTask) { task ->

            task.group = 'documentation'
            task.description = 'Generate internal navigation menu and collapsible sections for a README file.'
            task.location.set(internalMenuExtension.location)
        }

        GenerateFinalReadmeExtension finalReadmeExtension =
                project.extensions.create(
                        'generateFinalReadme',
                        GenerateFinalReadmeExtension
                )

        project.tasks.register('generateFinalReadme', GenerateFinalReadmeTask) { task ->

            task.group = 'documentation'
            task.description = 'Generate LIST.md and final README files for configured languages.'
            task.languages.set(finalReadmeExtension.languages)
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
