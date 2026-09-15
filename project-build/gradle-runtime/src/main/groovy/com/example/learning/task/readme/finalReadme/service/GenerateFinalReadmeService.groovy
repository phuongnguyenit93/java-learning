package com.example.learning.task.readme.finalReadme.service

import org.gradle.api.logging.Logger

class GenerateFinalReadmeService {

    private static final String README_DIRECTORY =
            'readme'

    private static final String MENU_DIRECTORY =
            'menu'

    private static final String BASE_FILE_NAME =
            'BASE.md'

    private static final String LIST_FILE_NAME =
            'LIST.md'


    private final Logger logger


    GenerateFinalReadmeService(
            Logger logger
    ) {

        this.logger =
                logger
    }


    void generate(
            File projectDirectory,
            Collection<String> languages
    ) {

        languages.each { String language ->

            generateLanguage(
                    projectDirectory,
                    language
            )
        }
    }


    private void generateLanguage(
            File projectDirectory,
            String language
    ) {

        logger.lifecycle(
                ''
        )

        logger.lifecycle(
                '[README] Processing language: {}',
                language.toUpperCase()
        )


        File languageDirectory =
                new File(
                        projectDirectory,
                        "${README_DIRECTORY}/${language}"
                )


        File menuDirectory =
                new File(
                        languageDirectory,
                        MENU_DIRECTORY
                )


        File baseFile =
                new File(
                        languageDirectory,
                        BASE_FILE_NAME
                )


        File listFile =
                new File(
                        languageDirectory,
                        LIST_FILE_NAME
                )


        validateSource(
                language,
                menuDirectory,
                baseFile
        )


        int fileCount =
                generateMenuList(
                        projectDirectory,
                        language,
                        menuDirectory,
                        listFile
                )


        File finalReadme =
                combineFinalReadme(
                        projectDirectory,
                        language,
                        baseFile,
                        listFile
                )


        logger.lifecycle(
                '[README] {} Markdown files added to {}',
                fileCount,
                LIST_FILE_NAME
        )

        logger.lifecycle(
                '[README] Generated: {}',
                finalReadme.absolutePath
        )
    }


    private static void validateSource(
            String language,
            File menuDirectory,
            File baseFile
    ) {

        if (
                !menuDirectory.exists() ||
                        !menuDirectory.isDirectory()
        ) {

            throw new IllegalStateException(
                    """
README menu directory does not exist.

Language:
${language}

Expected:

${menuDirectory.absolutePath}
"""
            )
        }


        if (
                !baseFile.exists() ||
                        !baseFile.isFile()
        ) {

            throw new IllegalStateException(
                    """
README base file does not exist.

Language:
${language}

Expected:

${baseFile.absolutePath}
"""
            )
        }
    }


    private static int generateMenuList(
            File projectDirectory,
            String language,
            File menuDirectory,
            File listFile
    ) {

        StringBuilder content =
                new StringBuilder()


        content.append(
                "# 📂 README MODULE STRUCTURE (${language.toUpperCase()})"
        )

        content.append(
                '\n\n'
        )


        Counter counter =
                new Counter()


        buildTree(
                projectDirectory,
                menuDirectory,
                '',
                content,
                counter
        )


        /*
         * LIST.md là generated file.
         *
         * Luôn overwrite toàn bộ để đảm bảo:
         *
         * run 1 lần
         * =
         * run N lần
         */
        listFile.setText(
                content.toString(),
                'UTF-8'
        )


        return counter.value
    }


    private static void buildTree(
            File projectDirectory,
            File currentDirectory,
            String indent,
            StringBuilder content,
            Counter counter
    ) {

        List<File> files =
                getSortedFiles(
                        currentDirectory
                )


        files.each { File file ->

            /*
             * Bỏ qua hidden files/folders.
             */
            if (
                    file.name.startsWith('.')
            ) {

                return
            }


            if (file.isDirectory()) {

                if (
                        containsMarkdownFile(
                                file
                        )
                ) {

                    content.append(
                            "${indent}* **${file.name}**\n"
                    )


                    buildTree(
                            projectDirectory,
                            file,
                            indent + '    ',
                            content,
                            counter
                    )
                }


                return
            }


            if (
                    !file.isFile() ||
                            !file.name.endsWith('.md')
            ) {

                return
            }


            String fileName =
                    file.name.substring(
                            0,
                            file.name.length() - '.md'.length()
                    )


            String relativePath =
                    projectDirectory
                            .toPath()
                            .relativize(
                                    file.toPath()
                            )
                            .toString()
                            .replace(
                                    File.separator,
                                    '/'
                            )
                            .replace(
                                    ' ',
                                    '%20'
                            )


            content.append(
                    "${indent}* [${fileName}](${relativePath})\n"
            )


            counter.value++
        }
    }


    private static List<File> getSortedFiles(
            File directory
    ) {

        File[] children =
                directory.listFiles()


        if (
                children == null ||
                        children.length == 0
        ) {

            return []
        }


        return children
                .toList()
                .sort { File first, File second ->

                    Integer firstNumber =
                            extractLeadingNumber(
                                    first.name
                            )


                    Integer secondNumber =
                            extractLeadingNumber(
                                    second.name
                            )


                    if (
                            firstNumber != null &&
                                    secondNumber != null
                    ) {

                        int numberCompare =
                                firstNumber <=>
                                        secondNumber


                        if (numberCompare != 0) {

                            return numberCompare
                        }
                    }


                    return first.name <=>
                            second.name
                }
    }


    private static Integer extractLeadingNumber(
            String name
    ) {

        def matcher =
                name =~ /^(\d+)/


        if (!matcher.find()) {
            return null
        }


        return matcher
                .group(1)
                .toInteger()
    }


    private static boolean containsMarkdownFile(
            File directory
    ) {

        File[] children =
                directory.listFiles()


        if (
                children == null ||
                        children.length == 0
        ) {

            return false
        }


        return children.any { File child ->

            if (
                    child.name.startsWith('.')
            ) {

                return false
            }


            if (
                    child.isFile() &&
                            child.name.endsWith('.md')
            ) {

                return true
            }


            if (child.isDirectory()) {

                return containsMarkdownFile(
                        child
                )
            }


            return false
        }
    }


    private static File combineFinalReadme(
            File projectDirectory,
            String language,
            File baseFile,
            File listFile
    ) {

        String outputName =
                language == 'en'
                        ? 'README.md'
                        : "README.${language}.md"


        File outputFile =
                new File(
                        projectDirectory,
                        outputName
                )


        String baseContent =
                baseFile.getText(
                        'UTF-8'
                )
                        .trim()


        String listContent =
                listFile.getText(
                        'UTF-8'
                )
                        .trim()


        String finalContent =
                listContent +
                        '\n\n' +
                        baseContent +
                        '\n'


        /*
         * Final README cũng luôn được rebuild hoàn toàn.
         */
        outputFile.setText(
                finalContent,
                'UTF-8'
        )


        return outputFile
    }


    private static class Counter {

        int value = 0
    }
}