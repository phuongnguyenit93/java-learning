package com.example.learning.setup.root.structure.service

import com.example.learning.setup.module.interview.service.InterviewQuestionSchemaService
import com.example.learning.utils.GradleBuildUtils
import com.example.learning.utils.ProjectPropertyUtils
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger

import java.nio.file.Files
import java.nio.file.Path


class PortalInterviewProjectionService {

    private static final String OUTPUT_DIRECTORY =
            'project-portal/build/generated/portal-data/module'

    private static final String INTERVIEW_DIRECTORY =
            'src/main/resources/interview'

    private static final String QUESTION_FILE =
            'question.yml'

    private final Logger logger
    private final InterviewQuestionSchemaService schemaService


    PortalInterviewProjectionService(
            Logger logger
    ) {
        this.logger = logger
        this.schemaService = new InterviewQuestionSchemaService()
    }


    void generate(
            Project rootProject
    ) {

        File moduleRoot = new File(rootProject.projectDir, 'module')
        if (!moduleRoot.isDirectory()) {
            throw new GradleException(
                    "Project module directory was not found: ${moduleRoot.absolutePath}"
            )
        }

        File outputDirectory = new File(rootProject.projectDir, OUTPUT_DIRECTORY)
        Map<String, byte[]> outputs = new LinkedHashMap<>()
        Set<String> routeIds = new LinkedHashSet<>()

        resolveRealModuleProjects(rootProject, moduleRoot).each {
            Project moduleProject ->

            if (!ProjectPropertyUtils.isEnabled(moduleProject, 'BUILD_INTERVIEW')) {
                return
            }

            List<LanguageSource> sources = resolveLanguageSources(moduleProject)
            if (sources.isEmpty()) {
                return
            }

            String routeId = resolveRouteId(moduleProject)
            if (!routeIds.add(routeId)) {
                throw new GradleException(
                        "Duplicate Portal Interview route identifier detected: ${routeId}"
                )
            }

            sources.each {
                LanguageSource source ->

                schemaService.validateQuestionFile(source.questionFile)

                String relativeOutput =
                        "${routeId}/interview/${source.language}/${QUESTION_FILE}"

                if (outputs.containsKey(relativeOutput)) {
                    throw new GradleException(
                            "Duplicate Portal Interview output detected: ${relativeOutput}"
                    )
                }

                outputs[relativeOutput] =
                        Files.readAllBytes(source.questionFile.toPath())
            }
        }

        writeOutputs(outputDirectory, outputs)
        removeStaleFiles(outputDirectory, outputs.keySet())

        logger.lifecycle(
                '🎤 [PORTAL-INTERVIEW] Generated {} Interview files.',
                outputs.size()
        )
    }


    private List<LanguageSource> resolveLanguageSources(
            Project moduleProject
    ) {

        File interviewDirectory =
                new File(moduleProject.projectDir, INTERVIEW_DIRECTORY)

        if (!interviewDirectory.isDirectory()) {
            return []
        }

        List<LanguageSource> result = []

        ProjectPropertyUtils
                .getStringList(moduleProject, 'MODULE_LANGUAGE')
                .collect {
                    String language ->
                    language.toLowerCase(Locale.ROOT)
                }
                .unique()
                .each {
                    String language ->

                    File questionFile =
                            new File(
                                    interviewDirectory,
                                    "${language}/${QUESTION_FILE}"
                            )

                    if (!questionFile.isFile()) {
                        logger.warn(
                                '[PORTAL-INTERVIEW] Skip declared language {} for {} because question.yml does not exist.',
                                language,
                                moduleProject.path
                        )
                        return
                    }

                    result.add(new LanguageSource(language, questionFile))
                }

        return result
    }


    private static List<Project> resolveRealModuleProjects(
            Project rootProject,
            File moduleRoot
    ) {

        Path moduleRootPath = moduleRoot.toPath().toAbsolutePath().normalize()

        return rootProject
                .allprojects
                .findAll {
                    Project candidate ->

                    Path projectPath =
                            candidate.projectDir.toPath().toAbsolutePath().normalize()

                    projectPath.startsWith(moduleRootPath) &&
                            new File(candidate.projectDir, 'gradle.properties').isFile()
                }
                .sort {
                    Project left,
                    Project right ->
                    left.projectDir.absolutePath <=> right.projectDir.absolutePath
                }
    }


    private static String resolveRouteId(
            Project moduleProject
    ) {
        String serviceName = moduleProject.findProperty('SERVICE_NAME')?.toString()?.trim()
        return serviceName != null && !serviceName.isBlank()
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

            File outputFile = new File(outputDirectory, relativePath)
            File parent = outputFile.parentFile

            if (!parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
                throw new GradleException(
                        "Unable to create Portal Interview output directory: ${parent.absolutePath}"
                )
            }

            if (
                    outputFile.isFile() &&
                            Arrays.equals(
                                    Files.readAllBytes(outputFile.toPath()),
                                    content
                            )
            ) {
                return
            }

            Files.write(outputFile.toPath(), content)
        }
    }


    private static void removeStaleFiles(
            File outputDirectory,
            Set<String> expectedRelativePaths
    ) {
        if (!outputDirectory.exists()) {
            return
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
                                            file.toPath().toAbsolutePath().normalize()
                                    )
                                    .toString()
                    )

            List<String> segments = relativePath.split('/').toList()

            if (
                    segments.size() >= 3 &&
                            segments[1] == 'interview' &&
                            !expectedRelativePaths.contains(relativePath)
            ) {
                staleFiles.add(file)
            }
        }

        staleFiles.each {
            File file ->

            if (!file.delete() && file.exists()) {
                throw new GradleException(
                        "Unable to remove stale Portal Interview file: ${file.absolutePath}"
                )
            }
        }
    }


    private static class LanguageSource {
        final String language
        final File questionFile

        LanguageSource(
                String language,
                File questionFile
        ) {
            this.language = language
            this.questionFile = questionFile
        }
    }
}
