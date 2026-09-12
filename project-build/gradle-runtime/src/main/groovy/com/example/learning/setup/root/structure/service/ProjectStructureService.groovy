package com.example.learning.setup.root.structure.service

import com.example.learning.utils.GradleBuildUtils
import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger


class ProjectStructureService {

    private static final Set<String> EXCLUDED_DIRECTORIES =
            [
                    '.gradle',
                    '.idea',
                    'build',
                    'bin',
                    'out',
                    'target',
                    'node_modules',
                    '.git',
                    'src',
                    'readme'
            ] as Set


    private static final String TEXT_STRUCTURE_PATH =
            'project-build/gradle-runtime/src/main/resources/structure/module-structure.txt'


    private static final String MARKDOWN_STRUCTURE_PATH =
            'STRUCTURE.md'


    private final Logger logger


    ProjectStructureService(
            Logger logger
    ) {

        this.logger =
                logger
    }


    void generate(
            Project rootProject
    ) {

        // ====================================================
        // Source
        // ====================================================

        File moduleDirectory =
                new File(
                        rootProject.projectDir,
                        'module'
                )


        if (!moduleDirectory.isDirectory()) {

            throw new GradleException(
                    """
Project module directory was not found:

${moduleDirectory.absolutePath}
""".stripIndent()
            )
        }


        // ====================================================
        // Generate content
        // ====================================================

        String textContent =
                generateTextStructure(
                        moduleDirectory
                )


        String markdownContent =
                generateMarkdownStructure(
                        rootProject,
                        moduleDirectory
                )


        // ====================================================
        // Outputs
        // ====================================================

        File textOutput =
                new File(
                        rootProject.projectDir,
                        TEXT_STRUCTURE_PATH
                )


        File markdownOutput =
                new File(
                        rootProject.projectDir,
                        MARKDOWN_STRUCTURE_PATH
                )


        // ====================================================
        // Write
        // ====================================================

        writeIfChanged(
                textOutput,
                textContent
        )


        writeIfChanged(
                markdownOutput,
                markdownContent
        )
    }


    // ========================================================
    // TXT structure
    // ========================================================

    private String generateTextStructure(
            File moduleDirectory
    ) {

        StringBuilder builder =
                new StringBuilder()


        builder
                .append(
                        moduleDirectory.name
                )
                .append(
                        '\n'
                )


        appendTextChildren(
                moduleDirectory,
                '',
                builder
        )


        return builder.toString()
    }


    private void appendTextChildren(
            File directory,
            String prefix,
            StringBuilder builder
    ) {

        List<File> children =
                getSubDirectories(
                        directory
                )


        children.eachWithIndex {
            File child,
            int index ->

                boolean last =
                        index == children.size() - 1


                String displayName =
                        resolveDisplayName(
                                child
                        )


                builder
                        .append(
                                prefix
                        )
                        .append(
                                last
                                        ? '└── '
                                        : '├── '
                        )
                        .append(
                                displayName
                        )
                        .append(
                                '\n'
                        )


                appendTextChildren(
                        child,
                        prefix +
                                (
                                        last
                                                ? '    '
                                                : '│   '
                                ),
                        builder
                )
        }
    }


    private String resolveDisplayName(
            File directory
    ) {

        String displayName =
                directory.name


        File masterFile =
                new File(
                        directory,
                        'master.json'
                )


        if (!masterFile.isFile()) {
            return displayName
        }


        Map master =
                readMasterFile(
                        masterFile
                )


        String serviceName =
                getMasterValue(
                        master,
                        'SERVICE_NAME'
                )


        String description =
                getMasterValue(
                        master,
                        'SERVICE_NAME_DESCRIBE'
                )


        String moduleDepend =
                getMasterValue(
                        master,
                        'IS_MODULE_DEPEND'
                )


        if (
                serviceName != null &&
                        !serviceName.isBlank()
        ) {

            displayName +=
                    " [${serviceName}]"
        }


        if (
                'TRUE'.equalsIgnoreCase(
                        moduleDepend
                )
        ) {

            displayName +=
                    ' - [DEPEND]'
        }


        if (
                description != null &&
                        !description.isBlank()
        ) {

            displayName +=
                    " : ${description}"
        }


        return displayName
    }


    // ========================================================
    // Markdown structure
    // ========================================================

    private String generateMarkdownStructure(
            Project rootProject,
            File moduleDirectory
    ) {

        StringBuilder builder =
                new StringBuilder()


        builder.append(
                '# Project Structure\n\n'
        )


        builder.append(
                'Sử dụng mũi tên để đóng/mở các phân cấp module.\n\n'
        )


        String rootRelativePath =
                buildRelativeLink(
                        rootProject,
                        moduleDirectory
                )


        builder.append(
                '<details open>\n'
        )


        builder.append(
                "  <summary><b><a href='${rootRelativePath}'>${moduleDirectory.name} (Root)</a></b></summary>\n"
        )


        appendMarkdownChildren(
                rootProject,
                moduleDirectory,
                builder
        )


        builder.append(
                '</details>\n'
        )


        return builder.toString()
    }


    private void appendMarkdownChildren(
            Project rootProject,
            File directory,
            StringBuilder builder
    ) {

        List<File> children =
                getSubDirectories(
                        directory
                )


        if (children.isEmpty()) {
            return
        }


        builder.append(
                '<ul>\n'
        )


        children.each {
            File child ->

                String relativePath =
                        buildRelativeLink(
                                rootProject,
                                child
                        )


                List<File> childDirectories =
                        getSubDirectories(
                                child
                        )


                builder.append(
                        '<li>\n'
                )


                if (!childDirectories.isEmpty()) {

                    builder.append(
                            '<details>\n'
                    )


                    builder.append(
                            "  <summary><b><a href='${relativePath}'>📁 ${child.name}</a></b></summary>\n"
                    )


                    appendMarkdownChildren(
                            rootProject,
                            child,
                            builder
                    )


                    builder.append(
                            '</details>\n'
                    )
                }
                else {

                    builder.append(
                            "  <a href='${relativePath}'>🪄 ${child.name}</a>\n"
                    )
                }


                builder.append(
                        '</li>\n'
                )
        }


        builder.append(
                '</ul>\n'
        )
    }


    // ========================================================
    // Directory
    // ========================================================

    private static List<File> getSubDirectories(
            File directory
    ) {

        File[] directories =
                directory.listFiles(
                        {
                            File file ->

                                file.isDirectory() &&
                                        !EXCLUDED_DIRECTORIES.contains(
                                                file.name
                                        )
                        } as FileFilter
                )


        if (directories == null) {

            throw new GradleException(
                    """
Unable to read directory:

${directory.absolutePath}
""".stripIndent()
            )
        }


        return directories
                .toList()
                .sort {
                    File left,
                    File right ->

                        left.name <=>
                                right.name
                }
    }


    // ========================================================
    // Master
    // ========================================================

    private static Map readMasterFile(
            File masterFile
    ) {

        try {

            return new JsonSlurper()
                    .parse(
                            masterFile
                    ) as Map
        }
        catch (Exception exception) {

            throw new GradleException(
                    """
Unable to read module master file:

${masterFile.absolutePath}

Reason:
${exception.message}
""".stripIndent(),
                    exception
            )
        }
    }


    private static String getMasterValue(
            Map master,
            String key
    ) {

        Object value =
                master
                        ?.get(
                                key
                        )
                        ?.get(
                                'VALUE'
                        )


        return value
                ?.toString()
                ?.trim()
    }


    // ========================================================
    // Link
    // ========================================================

    private static String buildRelativeLink(
            Project rootProject,
            File target
    ) {

        String relativePath =
                rootProject
                        .projectDir
                        .toPath()
                        .toAbsolutePath()
                        .normalize()
                        .relativize(
                                target
                                        .toPath()
                                        .toAbsolutePath()
                                        .normalize()
                        )
                        .toString()


        return './' +
                GradleBuildUtils.normalizePath(
                        relativePath
                )
    }


    // ========================================================
    // Output
    // ========================================================

    private void writeIfChanged(
            File outputFile,
            String content
    ) {

        if (
                outputFile.exists() &&
                        !outputFile.isFile()
        ) {

            throw new GradleException(
                    """
Project structure output path is not a file:

${outputFile.absolutePath}
""".stripIndent()
            )
        }


        if (
                outputFile.isFile() &&
                        outputFile.getText(
                                'UTF-8'
                        ) == content
        ) {

            logger.info(
                    '[PROJECT-STRUCTURE] UP-TO-DATE: {}',
                    outputFile.absolutePath
            )

            return
        }


        File parentDirectory =
                outputFile.parentFile


        if (
                !parentDirectory.isDirectory() &&
                        !parentDirectory.mkdirs() &&
                        !parentDirectory.isDirectory()
        ) {

            throw new GradleException(
                    """
Unable to create project structure output directory:

${parentDirectory.absolutePath}
""".stripIndent()
            )
        }


        try {

            outputFile.setText(
                    content,
                    'UTF-8'
            )
        }
        catch (Exception exception) {

            throw new GradleException(
                    """
Unable to write project structure file:

${outputFile.absolutePath}

Reason:
${exception.message}
""".stripIndent(),
                    exception
            )
        }


        logger.lifecycle(
                '🌳 [PROJECT-STRUCTURE] Generated: {}',
                outputFile.absolutePath
        )
    }
}