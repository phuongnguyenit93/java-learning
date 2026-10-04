package com.example.learning.setup.root.structure.service

import com.example.learning.utils.GradleBuildUtils
import com.example.learning.utils.ProjectPropertyUtils
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.yaml.snakeyaml.Yaml

import java.nio.file.Files
import java.nio.file.Path


class PortalReferenceProjectionService {

    private static final String OUTPUT_DIRECTORY =
            'project-portal/build/generated/portal-data/module'


    private static final String REFERENCE_FILE =
            'reference/references.yml'


    private final Logger logger

    private final Yaml yamlReader


    PortalReferenceProjectionService(
            Logger logger
    ) {

        this.logger =
                logger


        this.yamlReader =
                new Yaml()
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
                    "Project module directory was not found: ${moduleRoot.absolutePath}"
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

            if (
                    !ProjectPropertyUtils.isEnabled(
                            moduleProject,
                            'BUILD_REFERENCE'
                    )
            ) {
                return
            }


            File referenceFile =
                    new File(
                            moduleProject.projectDir,
                            REFERENCE_FILE
                    )


            if (!referenceFile.isFile()) {
                return
            }


            if (!validateAndHasReferences(referenceFile)) {
                return
            }


            String routeId =
                    resolveRouteId(
                            moduleProject
                    )


            if (!routeIds.add(routeId)) {
                throw new GradleException(
                        "Duplicate Portal Reference route identifier detected: ${routeId}"
                )
            }


            String relativeOutput =
                    "${routeId}/${REFERENCE_FILE}"


            outputs[relativeOutput] =
                    Files.readAllBytes(
                            referenceFile.toPath()
                    )
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
                '📚 [PORTAL-REFERENCE] Generated {} Reference files.',
                outputs.size()
        )
    }


    private boolean validateAndHasReferences(
            File referenceFile
    ) {

        Object document


        try {
            document =
                    yamlReader.load(
                            referenceFile.getText('UTF-8')
                    )
        }
        catch (Exception exception) {

            throw new GradleException(
                    "Invalid Reference YAML: ${referenceFile.absolutePath}",
                    exception
            )
        }


        if (document == null) {
            return false
        }


        if (!(document instanceof Map)) {
            throw new GradleException(
                    "Reference document must be a map: ${referenceFile.absolutePath}"
            )
        }


        Object referencesValue =
                (document as Map).get('references')


        if (referencesValue == null) {
            return false
        }


        if (!(referencesValue instanceof List)) {
            throw new GradleException(
                    "Reference 'references' must be a list: ${referenceFile.absolutePath}"
            )
        }


        List references =
                referencesValue as List


        references.eachWithIndex {
            Object value,
            int index ->

            if (!(value instanceof Map)) {
                throw new GradleException(
                        "Reference entry at index ${index} must be a map: ${referenceFile.absolutePath}"
                )
            }


            Map entry =
                    value as Map


            requireNonBlankString(
                    entry.get('title'),
                    "references[${index}].title",
                    referenceFile
            )


            requireNonBlankString(
                    entry.get('url'),
                    "references[${index}].url",
                    referenceFile
            )


            Object description =
                    entry.get('description')


            if (
                    description != null &&
                            !(description instanceof String)
            ) {
                throw new GradleException(
                        "Reference references[${index}].description must be a string when present: ${referenceFile.absolutePath}"
                )
            }
        }


        return !references.isEmpty()
    }


    private static void requireNonBlankString(
            Object value,
            String path,
            File referenceFile
    ) {

        if (
                !(value instanceof String) ||
                        (value as String).trim().isEmpty()
        ) {
            throw new GradleException(
                    "Reference ${path} must be a non-blank string: ${referenceFile.absolutePath}"
            )
        }
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


            File parent =
                    outputFile.parentFile


            if (
                    !parent.isDirectory() &&
                            !parent.mkdirs() &&
                            !parent.isDirectory()
            ) {
                throw new GradleException(
                        "Unable to create Portal Reference output directory: ${parent.absolutePath}"
                )
            }


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


    private static void removeStaleFiles(
            File outputDirectory,
            Set<String> expectedRelativePaths
    ) {

        if (!outputDirectory.exists()) {
            return
        }


        if (!outputDirectory.isDirectory()) {
            throw new GradleException(
                    "Portal Reference output path is not a directory: ${outputDirectory.absolutePath}"
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


            List<String> segments =
                    relativePath
                            .split('/')
                            .toList()


            if (
                    segments.size() >= 3 &&
                            segments[1] == 'reference' &&
                            !expectedRelativePaths.contains(
                                    relativePath
                            )
            ) {
                staleFiles.add(
                        file
                )
            }
        }


        staleFiles.each {
            File file ->

            if (
                    !file.delete() &&
                            file.exists()
            ) {
                throw new GradleException(
                        "Unable to remove stale Portal Reference file: ${file.absolutePath}"
                )
            }
        }
    }
}
