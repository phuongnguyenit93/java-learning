package com.example.learning.setup.root.structure.service

import com.example.learning.setup.module.readme.service.ReadmeKnowledgeParser
import com.example.learning.setup.module.quiz.service.QuizQuestionSchemaService
import com.example.learning.utils.GradleBuildUtils
import com.example.learning.utils.ProjectPropertyUtils
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.yaml.snakeyaml.Yaml

import java.nio.file.Files
import java.nio.file.Path


class PortalQuizProjectionService {

    private static final String OUTPUT_DIRECTORY =
            'project-portal/build/generated/portal-data/module'


    private static final String QUIZ_DIRECTORY =
            'src/main/resources/quiz'


    private static final String QUESTION_FILE =
            'question.yml'


    private final Logger logger

    private final QuizQuestionSchemaService schemaService

    private final ReadmeKnowledgeParser knowledgeParser

    private final Yaml yamlReader


    PortalQuizProjectionService(
            Logger logger
    ) {

        this.logger =
                logger


        this.schemaService =
                new QuizQuestionSchemaService()


        this.knowledgeParser =
                new ReadmeKnowledgeParser(
                        logger
                )


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
                            'BUILD_QUIZ'
                    )
            ) {
                return
            }


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
                        "Duplicate Portal Quiz route identifier detected: ${routeId}"
                )
            }


            sources.each {
                LanguageSource source ->

                schemaService.validateQuestionFile(
                        source.questionFile
                )


                validateQuestionRelations(
                        moduleProject,
                        routeId,
                        source
                )


                String relativeOutput =
                        "${routeId}/quiz/${source.language}/${QUESTION_FILE}"


                if (outputs.containsKey(relativeOutput)) {

                    throw new GradleException(
                            "Duplicate Portal Quiz output detected: ${relativeOutput}"
                    )
                }


                outputs[
                        relativeOutput
                ] =
                        Files.readAllBytes(
                                source.questionFile.toPath()
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
                '❓ [PORTAL-QUIZ] Generated {} Quiz files.',
                outputs.size()
        )
    }


    private void validateQuestionRelations(
            Project moduleProject,
            String routeId,
            LanguageSource source
    ) {

        Object parsed =
                yamlReader.load(
                        source.questionFile.getText(
                                'UTF-8'
                        )
                )


        if (!(parsed instanceof Map)) {
            return
        }


        Object rawQuestions =
                (parsed as Map)[
                        'questions'
                ]


        if (!(rawQuestions instanceof Collection)) {
            return
        }


        Path readmeMenuPath =
                new File(
                        moduleProject.projectDir,
                        "readme/${source.language}/menu"
                )
                        .toPath()
                        .toAbsolutePath()
                        .normalize()


        Map<String, Set<String>> readmeSectionCache =
                new LinkedHashMap<>()


        Map<String, Object> apiDescriptions =
                loadApiDescriptions(
                        moduleProject,
                        source.language
                )


        (rawQuestions as Collection)
                .eachWithIndex {
                    Object rawQuestion,
                    int index ->

                    if (!(rawQuestion instanceof Map)) {
                        return
                    }


                    Map question =
                            rawQuestion as Map


                    String questionId =
                            asTrimmedString(
                                    question[
                                            'id'
                                    ]
                            )


                    if (questionId.isBlank()) {
                        questionId = "questions[${index}]"
                    }


                    validateReadmeRelation(
                            routeId,
                            source.language,
                            questionId,
                            question[
                                    'readmeRelated'
                            ],
                            readmeMenuPath,
                            readmeSectionCache
                    )


                    validateApiRelation(
                            routeId,
                            source.language,
                            questionId,
                            question[
                                    'apiRelated'
                            ],
                            apiDescriptions
                    )
                }
    }


    private void validateReadmeRelation(
            String routeId,
            String language,
            String questionId,
            Object rawRelation,
            Path readmeMenuPath,
            Map<String, Set<String>> sectionCache
    ) {

        if (!(rawRelation instanceof Map)) {
            return
        }


        Map relation =
                rawRelation as Map


        String file =
                asTrimmedString(
                        relation[
                                'file'
                        ]
                )


        String anchor =
                asTrimmedString(
                        relation[
                                'anchor'
                        ]
                )


        if (
                file.isBlank() &&
                        anchor.isBlank()
        ) {
            return
        }


        if (
                file.isBlank() ||
                        anchor.isBlank()
        ) {

            warnUnresolvedRelation(
                    routeId,
                    language,
                    questionId,
                    'readmeRelated',
                    "file='${file}', anchor='${anchor}'",
                    'both file and anchor must be filled together'
            )


            return
        }


        Path targetPath =
                readmeMenuPath
                        .resolve(
                                file
                        )
                        .normalize()


        if (
                !targetPath.startsWith(
                        readmeMenuPath
                ) ||
                        !targetPath.toFile().isFile()
        ) {

            warnUnresolvedRelation(
                    routeId,
                    language,
                    questionId,
                    'readmeRelated',
                    "file='${file}', anchor='${anchor}'",
                    'README file was not found inside readme/{language}/menu'
            )


            return
        }


        String cacheKey =
                targetPath
                        .toAbsolutePath()
                        .normalize()
                        .toString()


        Set<String> sectionIds =
                sectionCache[
                        cacheKey
                ]


        if (sectionIds == null) {

            sectionIds =
                    knowledgeParser
                            .parse(
                                    targetPath.toFile()
                            )
                            .findAll {
                                ReadmeKnowledgeParser.SectionCandidate candidate ->

                                candidate.valid
                            }
                            .collect {
                                ReadmeKnowledgeParser.SectionCandidate candidate ->

                                candidate.id
                            }
                            .toSet()


            sectionCache[
                    cacheKey
            ] = sectionIds
        }


        if (!sectionIds.contains(anchor)) {

            warnUnresolvedRelation(
                    routeId,
                    language,
                    questionId,
                    'readmeRelated',
                    "file='${file}', anchor='${anchor}'",
                    'Knowledge anchor was not found in the README file'
            )
        }
    }


    private void validateApiRelation(
            String routeId,
            String language,
            String questionId,
            Object rawRelation,
            Map<String, Object> apiDescriptions
    ) {

        if (!(rawRelation instanceof Map)) {
            return
        }


        Map relation =
                rawRelation as Map


        String controller =
                asTrimmedString(
                        relation[
                                'controller'
                        ]
                )


        String methodSignature =
                asTrimmedString(
                        relation[
                                'methodSignature'
                        ]
                )


        if (
                controller.isBlank() &&
                        methodSignature.isBlank()
        ) {
            return
        }


        if (
                controller.isBlank() ||
                        methodSignature.isBlank()
        ) {

            warnUnresolvedRelation(
                    routeId,
                    language,
                    questionId,
                    'apiRelated',
                    "controller='${controller}', methodSignature='${methodSignature}'",
                    'both controller and methodSignature must be filled together'
            )


            return
        }


        Object rawController =
                apiDescriptions[
                        controller
                ]


        if (!(rawController instanceof Map)) {

            warnUnresolvedRelation(
                    routeId,
                    language,
                    questionId,
                    'apiRelated',
                    "controller='${controller}', methodSignature='${methodSignature}'",
                    'controller was not found in api-descriptions.yml'
            )


            return
        }


        Object rawMethod =
                (rawController as Map)[
                        methodSignature
                ]


        if (!(rawMethod instanceof Map)) {

            warnUnresolvedRelation(
                    routeId,
                    language,
                    questionId,
                    'apiRelated',
                    "controller='${controller}', methodSignature='${methodSignature}'",
                    'method signature was not found in api-descriptions.yml'
            )


            return
        }


        Object usage =
                (rawMethod as Map)[
                        'usage'
                ]


        if (
                !(usage instanceof Boolean) ||
                        !(usage as Boolean)
        ) {

            warnUnresolvedRelation(
                    routeId,
                    language,
                    questionId,
                    'apiRelated',
                    "controller='${controller}', methodSignature='${methodSignature}'",
                    'API exists but usage is not true, so it is not exposed in Portal API Docs'
            )
        }
    }


    private Map<String, Object> loadApiDescriptions(
            Project moduleProject,
            String language
    ) {

        File apiDescriptionFile =
                new File(
                        moduleProject.projectDir,
                        "src/main/resources/swagger/${language}/api-descriptions.yml"
                )


        if (!apiDescriptionFile.isFile()) {
            return new LinkedHashMap<>()
        }


        Object parsed =
                yamlReader.load(
                        apiDescriptionFile.getText(
                                'UTF-8'
                        )
                )


        return parsed instanceof Map
                ? parsed as Map<String, Object>
                : new LinkedHashMap<>()
    }


    private void warnUnresolvedRelation(
            String routeId,
            String language,
            String questionId,
            String relationType,
            String relationValue,
            String reason
    ) {

        logger.warn(
                '[QUIZ-RELATION] Unresolved {} for {}/{}/{}: {}. Reason: {}.',
                relationType,
                routeId,
                language,
                questionId,
                relationValue,
                reason
        )
    }


    private static String asTrimmedString(
            Object value
    ) {

        return value == null
                ? ''
                : value.toString().trim()
    }


    private List<LanguageSource> resolveLanguageSources(
            Project moduleProject
    ) {

        File quizDirectory =
                new File(
                        moduleProject.projectDir,
                        QUIZ_DIRECTORY
                )


        if (!quizDirectory.isDirectory()) {
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

                    File questionFile =
                            new File(
                                    quizDirectory,
                                    "${language}/${QUESTION_FILE}"
                            )


                    if (!questionFile.isFile()) {

                        logger.warn(
                                '[PORTAL-QUIZ] Skip declared language {} for {} because question.yml does not exist.',
                                language,
                                moduleProject.path
                        )


                        return
                    }


                    result.add(
                            new LanguageSource(
                                    language,
                                    questionFile
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
                        "Unable to create Portal Quiz output directory: ${parent.absolutePath}"
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
                            segments[1] == 'quiz' &&
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

            if (!file.delete() && file.exists()) {

                throw new GradleException(
                        "Unable to remove stale Portal Quiz file: ${file.absolutePath}"
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

