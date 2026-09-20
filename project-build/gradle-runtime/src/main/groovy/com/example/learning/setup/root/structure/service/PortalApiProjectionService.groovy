package com.example.learning.setup.root.structure.service

import com.example.learning.utils.GradleBuildUtils
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger

import java.nio.file.Files
import java.nio.file.Path


class PortalApiProjectionService {

    private static final String OUTPUT_DIRECTORY =
            'project-portal/build/generated/portal-data/module'


    private static final String SWAGGER_DIRECTORY =
            'src/main/resources/swagger'


    private static final List<String> API_FILES =
            [
                    'api-descriptions.yml',
                    'api-execution.yml',
                    'api-params.yml',
                    'controller-description.yml'
            ]


    private final Logger logger


    PortalApiProjectionService(
            Logger logger
    ) {

        this.logger =
                logger
    }


    void generate(
            Project rootProject
    ) {

        File moduleRoot =
                new File(
                        rootProject.projectDir,
                        'module'
                )


        if (!moduleRoot.isDirectory()) {

            throw new GradleException(
                    """
Project module directory was not found:

${moduleRoot.absolutePath}
""".stripIndent()
            )
        }


        File outputDirectory =
                new File(
                        rootProject.projectDir,
                        OUTPUT_DIRECTORY
                )


        Map<String, byte[]> outputs =
                new LinkedHashMap<>()


        Set<String> routeIds =
                new LinkedHashSet<>()


        resolveRealModuleProjects(
                rootProject,
                moduleRoot
        ).each {
            Project moduleProject ->

                List<LanguageSource> languageSources =
                        resolveLanguageSources(
                                moduleProject.projectDir
                        )


                if (languageSources.isEmpty()) {
                    return
                }


                String routeId =
                        resolveRouteId(
                                moduleProject
                        )


                if (!routeIds.add(routeId)) {

                    throw new GradleException(
                            """
Duplicate Portal API route identifier detected:

${routeId}

Route identifiers must be unique for modules that expose complete Swagger API metadata.
""".stripIndent()
                    )
                }


                languageSources.each {
                    LanguageSource source ->

                        API_FILES.each {
                            String fileName ->

                                File sourceFile =
                                        new File(
                                                source.directory,
                                                fileName
                                        )


                                String relativeOutput =
                                        "${routeId}/api/${source.language}/${fileName}"


                                putOutput(
                                        outputs,
                                        relativeOutput,
                                        Files.readAllBytes(
                                                sourceFile.toPath()
                                        )
                                )
                        }
                }
        }


        writeOutputs(
                outputDirectory,
                outputs
        )


        removeStaleFiles(
                outputDirectory,
                outputs.keySet()
        )


        logger.lifecycle(
                '🔌 [PORTAL-API] Generated {} Swagger metadata files.',
                outputs.size()
        )
    }


    private List<LanguageSource> resolveLanguageSources(
            File moduleDirectory
    ) {

        File swaggerDirectory =
                new File(
                        moduleDirectory,
                        SWAGGER_DIRECTORY
                )


        if (!swaggerDirectory.isDirectory()) {
            return []
        }


        File[] languageDirectories =
                swaggerDirectory.listFiles(
                        {
                            File file ->

                                file.isDirectory()
                        } as FileFilter
                )


        if (languageDirectories == null) {

            throw new GradleException(
                    """
Unable to read module Swagger directory:

${swaggerDirectory.absolutePath}
""".stripIndent()
            )
        }


        List<LanguageSource> result = []


        languageDirectories
                .toList()
                .sort {
                    File left,
                    File right ->

                        left.name <=> right.name
                }
                .each {
                    File languageDirectory ->

                        List<String> missingFiles =
                                API_FILES.findAll {
                                    String fileName ->

                                        !new File(
                                                languageDirectory,
                                                fileName
                                        ).isFile()
                                }


                        if (!missingFiles.isEmpty()) {

                            logger.warn(
                                    '[PORTAL-API] Skip incomplete Swagger metadata directory {}. Missing: {}',
                                    languageDirectory.absolutePath,
                                    missingFiles.join(', ')
                            )


                            return
                        }


                        result.add(
                                new LanguageSource(
                                        languageDirectory.name,
                                        languageDirectory
                                )
                        )
                }


        return result
    }


    private static List<Project> resolveRealModuleProjects(
            Project rootProject,
            File moduleRoot
    ) {

        Path moduleRootPath =
                moduleRoot
                        .toPath()
                        .toAbsolutePath()
                        .normalize()


        return rootProject
                .allprojects
                .findAll {
                    Project candidate ->

                        Path projectPath =
                                candidate
                                        .projectDir
                                        .toPath()
                                        .toAbsolutePath()
                                        .normalize()


                        projectPath.startsWith(
                                moduleRootPath
                        ) &&
                                new File(
                                        candidate.projectDir,
                                        'gradle.properties'
                                ).isFile()
                }
                .sort {
                    Project left,
                    Project right ->

                        left.projectDir.absolutePath <=>
                                right.projectDir.absolutePath
                }
    }


    private static String resolveRouteId(
            Project moduleProject
    ) {

        String serviceName =
                moduleProject
                        .findProperty(
                                'SERVICE_NAME'
                        )
                        ?.toString()
                        ?.trim()


        return serviceName != null &&
                !serviceName.isBlank()
                ? serviceName
                : moduleProject.projectDir.name
    }


    private static void putOutput(
            Map<String, byte[]> outputs,
            String relativePath,
            byte[] content
    ) {

        if (outputs.containsKey(relativePath)) {

            throw new GradleException(
                    """
Duplicate Portal API output detected:

${relativePath}
""".stripIndent()
            )
        }


        outputs[relativePath] =
                content
    }


    private static void writeOutputs(
            File outputDirectory,
            Map<String, byte[]> outputs
    ) {

        outputs.each {
            String relativePath,
            byte[] content ->

                File outputFile =
                        new File(
                                outputDirectory,
                                relativePath
                        )


                ensureParentDirectory(
                        outputFile
                )


                if (
                        outputFile.isFile() &&
                                Arrays.equals(
                                        Files.readAllBytes(
                                                outputFile.toPath()
                                        ),
                                        content
                                )
                ) {

                    return
                }


                Files.write(
                        outputFile.toPath(),
                        content
                )
        }
    }


    private static void ensureParentDirectory(
            File outputFile
    ) {

        if (
                outputFile.exists() &&
                        !outputFile.isFile()
        ) {

            throw new GradleException(
                    """
Portal API output path is not a file:

${outputFile.absolutePath}
""".stripIndent()
            )
        }


        File parent =
                outputFile.parentFile


        if (
                !parent.isDirectory() &&
                        !parent.mkdirs() &&
                        !parent.isDirectory()
        ) {

            throw new GradleException(
                    """
Unable to create Portal API output directory:

${parent.absolutePath}
""".stripIndent()
            )
        }
    }


    private static void removeStaleFiles(
            File outputDirectory,
            Set<String> expectedRelativePaths
    ) {

        if (!outputDirectory.exists()) {
            return
        }


        if (!outputDirectory.isDirectory()) {

            throw new GradleException(
                    """
Portal API output path is not a directory:

${outputDirectory.absolutePath}
""".stripIndent()
            )
        }


        List<File> staleFiles = []


        outputDirectory.eachFileRecurse {
            File file ->

                if (!file.isFile()) {
                    return
                }


                String relativePath =
                        GradleBuildUtils.normalizePath(
                                outputDirectory
                                        .toPath()
                                        .toAbsolutePath()
                                        .normalize()
                                        .relativize(
                                                file
                                                        .toPath()
                                                        .toAbsolutePath()
                                                        .normalize()
                                        )
                                        .toString()
                        )


                if (
                        isApiOutputPath(relativePath) &&
                                !expectedRelativePaths.contains(relativePath)
                ) {

                    staleFiles.add(
                            file
                    )
                }
        }


        staleFiles.each {
            File file ->

                if (!file.delete() && file.exists()) {

                    throw new GradleException(
                            """
Unable to remove stale Portal API file:

${file.absolutePath}
""".stripIndent()
                    )
                }
        }


        removeEmptyDirectories(
                outputDirectory,
                outputDirectory
        )
    }


    private static boolean isApiOutputPath(
            String relativePath
    ) {

        List<String> pathSegments =
                relativePath
                        .split('/')
                        .toList()


        return pathSegments.size() >= 3 &&
                pathSegments[1] == 'api'
    }


    private static void removeEmptyDirectories(
            File rootDirectory,
            File directory
    ) {

        File[] children =
                directory.listFiles()


        if (children == null) {

            throw new GradleException(
                    """
Unable to read Portal API output directory:

${directory.absolutePath}
""".stripIndent()
            )
        }


        children
                .findAll {
                    File child ->

                        child.isDirectory()
                }
                .each {
                    File child ->

                        removeEmptyDirectories(
                                rootDirectory,
                                child
                        )
                }


        if (directory == rootDirectory) {
            return
        }


        String[] remaining =
                directory.list()


        if (
                remaining != null &&
                        remaining.length == 0 &&
                        !directory.delete() &&
                        directory.exists()
        ) {

            throw new GradleException(
                    """
Unable to remove empty Portal API directory:

${directory.absolutePath}
""".stripIndent()
            )
        }
    }


    private static class LanguageSource {

        final String language
        final File directory


        LanguageSource(
                String language,
                File directory
        ) {

            this.language = language
            this.directory = directory
        }
    }
}
