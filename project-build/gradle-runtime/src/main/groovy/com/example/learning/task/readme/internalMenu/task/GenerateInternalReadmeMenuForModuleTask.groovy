package com.example.learning.task.readme.internalMenu.task

import com.example.learning.task.readme.internalMenu.service.GenerateInternalReadmeMenuService
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(
        because = 'Updates all README menu Markdown files in place for configured module languages'
)
abstract class GenerateInternalReadmeMenuForModuleTask
        extends DefaultTask {

    @Input
    abstract ListProperty<String> getLanguages()


    @TaskAction
    void generate() {

        List<String> configuredLanguages =
                languages
                        .getOrElse([])
                        .collect {
                            String language ->

                                language
                                        ?.trim()
                                        ?.toLowerCase(
                                                Locale.ROOT
                                        )
                        }
                        .findAll {
                            String language ->

                                language != null &&
                                        !language.isBlank()
                        }
                        .unique()


        if (configuredLanguages.isEmpty()) {

            throw new GradleException(
                    '''
Internal README menu module languages are not configured.

Configure MODULE_LANGUAGE in master.json, for example:

"MODULE_LANGUAGE": {
    "TYPE": "list",
    "VALUE": ["vi", "en"]
}
'''.stripIndent()
            )
        }


        File projectDirectory =
                project.projectDir
                        .canonicalFile


        GenerateInternalReadmeMenuService service =
                new GenerateInternalReadmeMenuService()


        int generatedCount = 0


        configuredLanguages.each {
            String language ->

                File menuDirectory =
                        new File(
                                projectDirectory,
                                "readme/${language}/menu"
                        )
                                .canonicalFile


                GenerateInternalReadmeMenuForModuleTask.validateMenuDirectory(
                        projectDirectory,
                        language,
                        menuDirectory
                )


                List<File> markdownFiles =
                        GenerateInternalReadmeMenuForModuleTask.collectMarkdownFiles(
                                menuDirectory
                        )


                if (markdownFiles.isEmpty()) {

                    throw new GradleException(
                            """
No README menu Markdown files were found.

Language:
${language}

Menu directory:
${menuDirectory.absolutePath}
""".stripIndent()
                    )
                }


                logger.lifecycle(
                        '[README] Generating internal menus for {}: {} files',
                        language.toUpperCase(
                                Locale.ROOT
                        ),
                        markdownFiles.size()
                )


                markdownFiles.each {
                    File markdownFile ->

                        try {

                            service.generate(
                                    markdownFile
                            )
                        } catch (Exception exception) {

                            throw new GradleException(
                                    """
Failed to generate internal README menu.

Language:
${language}

File:
${markdownFile.absolutePath}

Reason:
${exception.message}
""".stripIndent(),
                                    exception
                            )
                        }


                        generatedCount++


                        logger.lifecycle(
                                '   ✅ {}',
                                projectDirectory
                                        .toPath()
                                        .relativize(
                                                markdownFile.toPath()
                                        )
                                        .toString()
                        )
                }
        }


        logger.lifecycle(
                'Internal README menus generated for module {}: {} files',
                project.name,
                generatedCount
        )
    }


    private static void validateMenuDirectory(
            File projectDirectory,
            String language,
            File menuDirectory
    ) {

        if (
                !menuDirectory
                        .toPath()
                        .startsWith(
                                projectDirectory.toPath()
                        )
        ) {

            throw new GradleException(
                    """
README menu directory must be inside projectDir.

Language:
${language}

Resolved:
${menuDirectory.absolutePath}
""".stripIndent()
            )
        }


        if (
                !menuDirectory.exists() ||
                        !menuDirectory.isDirectory()
        ) {

            throw new GradleException(
                    """
README menu directory does not exist.

Language:
${language}

Expected:
${menuDirectory.absolutePath}
""".stripIndent()
            )
        }
    }


    private static List<File> collectMarkdownFiles(
            File menuDirectory
    ) {

        List<File> result = []


        GenerateInternalReadmeMenuForModuleTask.collectMarkdownFilesRecursive(
                menuDirectory,
                result
        )


        return result.sort {
            File first,
            File second ->

                String firstPath =
                        menuDirectory
                                .toPath()
                                .relativize(
                                        first.toPath()
                                )
                                .toString()
                                .replace(
                                        File.separator,
                                        '/'
                                )


                String secondPath =
                        menuDirectory
                                .toPath()
                                .relativize(
                                        second.toPath()
                                )
                                .toString()
                                .replace(
                                        File.separator,
                                        '/'
                                )


                firstPath <=>
                        secondPath
        }
    }


    private static void collectMarkdownFilesRecursive(
            File directory,
            List<File> result
    ) {

        File[] children =
                directory.listFiles()


        if (
                children == null ||
                        children.length == 0
        ) {

            return
        }


        children
                .findAll {
                    File child ->

                        !child.name.startsWith('.')
                }
                .sort {
                    File first,
                    File second ->

                        first.name <=>
                                second.name
                }
                .each {
                    File child ->

                        if (child.isDirectory()) {

                            GenerateInternalReadmeMenuForModuleTask.collectMarkdownFilesRecursive(
                                    child,
                                    result
                            )

                            return
                        }


                        if (
                                child.isFile() &&
                                        child.name
                                                .toLowerCase(
                                                        Locale.ROOT
                                                )
                                                .endsWith('.md')
                        ) {

                            result.add(
                                    child.canonicalFile
                            )
                        }
                }
    }
}
