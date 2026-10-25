package com.example.learning.setup.root.structure.service

import com.example.learning.utils.schema.validate.ContractSchemaValidateUtils
import com.example.learning.utils.GradleBuildUtils
import com.example.learning.utils.ProjectPropertyUtils
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.yaml.snakeyaml.Yaml

import java.nio.file.Files
import java.nio.file.Path


class PortalRoadmapProjectionService {

    private static final String OUTPUT_DIRECTORY =
            LearningDataPaths.MODULE_DIRECTORY

    private static final String ROADMAP_DIRECTORY =
            'content/roadmap'

    private static final String ROADMAP_FILE =
            'roadmap.yml'

    private final Logger logger


    PortalRoadmapProjectionService(
            Logger logger
    ) {
        this.logger = logger
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


            List<LanguageSource> sources =
                    resolveLanguageSources(
                            moduleProject
                    )

            if (sources.isEmpty()) {
                return
            }


            String routeId =
                    resolveRouteId(
                            moduleProject
                    )


            if (!routeIds.add(routeId)) {
                throw new GradleException(
                        "Duplicate Portal Roadmap route identifier detected: ${routeId}"
                )
            }


            sources.each {
                LanguageSource source ->

                String relativeOutput =
                        "${routeId}/roadmap/${source.language}/${ROADMAP_FILE}"


                if (outputs.containsKey(relativeOutput)) {
                    throw new GradleException(
                            "Duplicate Portal Roadmap output detected: ${relativeOutput}"
                    )
                }


                outputs[relativeOutput] =
                        Files.readAllBytes(
                                source.roadmapFile.toPath()
                        )
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
                '🗺️ [PORTAL-ROADMAP] Generated {} Roadmap files.',
                outputs.size()
        )
    }


    private List<LanguageSource> resolveLanguageSources(
            Project moduleProject
    ) {

        File roadmapDirectory =
                new File(
                        moduleProject.projectDir,
                        ROADMAP_DIRECTORY
                )


        if (!roadmapDirectory.isDirectory()) {
            return []
        }


        List<LanguageSource> result = []


        ProjectPropertyUtils
                .getStringList(
                        moduleProject,
                        'MODULE_LANGUAGE'
                )
                .collect {
                    String language ->

                    language.toLowerCase(
                            Locale.ROOT
                    )
                }
                .unique()
                .each {
                    String language ->

                    File roadmapFile =
                            new File(
                                    roadmapDirectory,
                                    "${language}/${ROADMAP_FILE}"
                            )


                    if (!roadmapFile.isFile()) {
                        logger.warn(
                                '[PORTAL-ROADMAP] Skip declared language {} for {} because roadmap.yml does not exist.',
                                language,
                                moduleProject.path
                        )

                        return
                    }


                    if (hasAuthoredRoadmap(roadmapFile)) {
                        result.add(new LanguageSource(language, roadmapFile))
                    }
                }


        return result
    }

    /**
     * Validate authored Roadmap YAML and require at least one actual milestone.
     * A comment-only file or an empty roadmap array is a skeleton.
     */
    static boolean hasAuthoredRoadmap(File roadmapFile) {
        if (!roadmapFile.isFile()) return false
        Object authored
        try {
            authored = new Yaml().load(roadmapFile.getText('UTF-8'))
        } catch (Exception exception) {
            throw new GradleException("Invalid Roadmap YAML: ${roadmapFile.absolutePath}", exception)
        }
        if (authored == null) return false
        ContractSchemaValidateUtils.validateData('roadmap', authored)
        return authored instanceof Map &&
                (authored as Map).roadmap instanceof List &&
                !((authored as Map).roadmap as List).isEmpty()
    }


    private static List<Project> resolveRealModuleProjects(
            Project rootProject,
            File moduleRoot
    ) {

        // Gradle ProjectBuilder on Windows can return 8.3 short paths for
        // child projectDir while rootProject.projectDir uses the long path.
        // Compare canonical paths rather than their different spellings.
        Path moduleRootPath =
                moduleRoot.canonicalFile.toPath()


        return rootProject
                .allprojects
                .findAll {
                    Project candidate ->

                    Path projectPath =
                            candidate.projectDir.canonicalFile.toPath()


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
                        "Unable to create Portal Roadmap output directory: ${parent.absolutePath}"
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
                    "Portal Roadmap output path is not a directory: ${outputDirectory.absolutePath}"
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
                            segments[1] == 'roadmap' &&
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
                        "Unable to remove stale Portal Roadmap file: ${file.absolutePath}"
                )
            }
        }
    }


    private static class LanguageSource {

        final String language
        final File roadmapFile


        LanguageSource(
                String language,
                File roadmapFile
        ) {
            this.language = language
            this.roadmapFile = roadmapFile
        }
    }
}
