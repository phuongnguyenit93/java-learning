package com.example.learning.setup.module.quiz.service

import com.example.learning.utils.ProjectPropertyUtils
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.yaml.snakeyaml.Yaml

import java.nio.file.Files
import java.nio.file.StandardCopyOption


class QuizStructureService {

    private static final String COMMENT_START =
            '# <quiz-schema>'


    private static final String COMMENT_END =
            '# </quiz-schema>'


    private final Logger logger

    private final QuizQuestionSchemaService schemaService


    QuizStructureService(
            Logger logger
    ) {

        this.logger =
                logger


        this.schemaService =
                new QuizQuestionSchemaService()
    }


    void setup(
            Project project
    ) {

        if (
                !ProjectPropertyUtils.isEnabled(
                        project,
                        'BUILD_QUIZ'
                )
        ) {

            logger.info(
                    '[QUIZ-STRUCTURE] Skip {} because BUILD_QUIZ != TRUE.',
                    project.path
            )


            return
        }


        List<String> languages =
                ProjectPropertyUtils
                        .getStringList(
                                project,
                                'MODULE_LANGUAGE'
                        )
                        .collect {
                            String language ->

                            language.toLowerCase(
                                    Locale.ROOT
                            )
                        }
                        .unique()


        if (languages.isEmpty()) {

            logger.info(
                    '[QUIZ-STRUCTURE] No MODULE_LANGUAGE configured for {}.',
                    project.path
            )


            return
        }


        languages.each {
            String language ->

            File questionFile =
                    new File(
                            project.projectDir,
                            "src/main/resources/quiz/${language}/question.yml"
                    )


            synchronizeQuestionFile(
                    questionFile,
                    language
            )
        }
    }


    private void synchronizeQuestionFile(
            File questionFile,
            String language
    ) {

        if (
                questionFile.exists() &&
                        !questionFile.isFile()
        ) {

            throw new GradleException(
                    "Quiz question path exists but is not a file: ${questionFile.absolutePath}"
            )
        }


        if (!questionFile.exists()) {

            ensureParentDirectory(
                    questionFile
            )


            writeSafely(
                    questionFile,
                    schemaService.renderSkeleton(
                            language
                    )
            )


            logger.lifecycle(
                    '✨ [QUIZ-STRUCTURE] Created: {}',
                    questionFile.absolutePath
            )


            return
        }


        String current =
                questionFile.getText(
                        'UTF-8'
                )


        String generatedComment =
                schemaService.renderCommentBlock(
                        language
                )


        String updated =
                replaceGeneratedComment(
                        current,
                        generatedComment,
                        questionFile
                )


        updated =
                synchronizeRequiredQuestionFields(
                        updated,
                        questionFile
                )


        if (current == updated) {

            logger.info(
                    '[QUIZ-STRUCTURE] UP-TO-DATE: {}',
                    questionFile.absolutePath
            )


            return
        }


        writeSafely(
                questionFile,
                updated
        )


        logger.lifecycle(
                '📝 [QUIZ-STRUCTURE] Synchronized schema/template fields: {}',
                questionFile.absolutePath
        )
    }


    private String synchronizeRequiredQuestionFields(
            String content,
            File questionFile
    ) {

        Object parsed


        try {

            parsed =
                    new Yaml()
                            .load(
                                    content
                            )
        }
        catch (Exception exception) {

            throw new GradleException(
                    "Unable to synchronize Quiz template fields because YAML cannot be parsed: ${questionFile.absolutePath}",
                    exception
            )
        }


        if (!(parsed instanceof Map)) {

            throw new GradleException(
                    "Quiz YAML root must be an object: ${questionFile.absolutePath}"
            )
        }


        Object rawQuestions =
                (parsed as Map)[
                        'questions'
                ]


        if (!(rawQuestions instanceof Collection)) {

            throw new GradleException(
                    "Quiz YAML 'questions' must be a list: ${questionFile.absolutePath}"
            )
        }


        List<Map> questions = []


        (rawQuestions as Collection)
                .eachWithIndex {
                    Object question,
                    int index ->

                    if (!(question instanceof Map)) {

                        throw new GradleException(
                                "Quiz question at index ${index} must be an object: ${questionFile.absolutePath}"
                        )
                    }


                    questions.add(
                            question as Map
                    )
                }


        if (questions.isEmpty()) {
            return content
        }


        Map<String, Object> defaults =
                schemaService.requiredQuestionFieldDefaults()


        List<List<Map.Entry<String, Object>>> missingFields =
                questions.collect {
                    Map question ->

                    defaults
                            .entrySet()
                            .findAll {
                                Map.Entry<String, Object> entry ->

                                !question.containsKey(
                                        entry.key
                                )
                            }
                            .toList()
                }


        if (
                missingFields.every {
                    it.isEmpty()
                }
        ) {
            return content
        }


        String newline =
                content.contains('\r\n')
                        ? '\r\n'
                        : '\n'


        List<String> lines =
                content
                        .split(
                                '\\r?\\n',
                                -1
                        )
                        .toList()


        List<Integer> questionStarts = []


        lines.eachWithIndex {
            String line,
            int index ->

            if (
                    line ==~ /^  - [A-Za-z][A-Za-z0-9]*:.*$/
            ) {
                questionStarts.add(
                        index
                )
            }
        }


        if (
                questionStarts.size() !=
                        questions.size()
        ) {

            throw new GradleException(
                    "Quiz template synchronization found ${questions.size()} parsed questions but ${questionStarts.size()} question blocks: ${questionFile.absolutePath}"
            )
        }


        for (
                int questionIndex = questions.size() - 1;
                questionIndex >= 0;
                questionIndex--
        ) {

            List<Map.Entry<String, Object>> missing =
                    missingFields[
                            questionIndex
                    ]


            if (missing.isEmpty()) {
                continue
            }


            int blockEnd =
                    questionIndex + 1 < questionStarts.size()
                            ? questionStarts[
                                    questionIndex + 1
                            ]
                            : lines.size()


            int insertAt =
                    blockEnd


            while (
                    insertAt > questionStarts[
                            questionIndex
                    ] + 1 &&
                            lines[
                                    insertAt - 1
                            ].isBlank()
            ) {
                insertAt--
            }


            List<String> generatedLines =
                    missing.collectMany {
                        Map.Entry<String, Object> entry ->

                        schemaService.renderYamlDefaultLines(
                                entry.key,
                                entry.value,
                                2
                        )
                    }


            lines.addAll(
                    insertAt,
                    generatedLines
            )
        }


        return lines.join(
                newline
        )
    }


    private static String replaceGeneratedComment(
            String content,
            String generatedComment,
            File questionFile
    ) {

        int start =
                content.indexOf(
                        COMMENT_START
                )


        int end =
                content.indexOf(
                        COMMENT_END
                )


        if (
                (start >= 0) !=
                        (end >= 0)
        ) {

            throw new GradleException(
                    "Malformed generated Quiz schema comment markers: ${questionFile.absolutePath}"
            )
        }


        if (
                start >= 0 &&
                        end < start
        ) {

            throw new GradleException(
                    "Malformed generated Quiz schema comment marker order: ${questionFile.absolutePath}"
            )
        }


        if (start < 0) {

            return generatedComment +
                    '\n\n' +
                    content
        }


        int endExclusive =
                end +
                        COMMENT_END.length()


        return content.substring(
                0,
                start
        ) +
                generatedComment +
                content.substring(
                        endExclusive
                )
    }


    private static void ensureParentDirectory(
            File file
    ) {

        File parent =
                file.parentFile


        if (
                !parent.isDirectory() &&
                        !parent.mkdirs() &&
                        !parent.isDirectory()
        ) {

            throw new GradleException(
                    "Unable to create Quiz directory: ${parent.absolutePath}"
            )
        }
    }


    private static void writeSafely(
            File file,
            String content
    ) {

        ensureParentDirectory(
                file
        )


        File temporaryFile =
                new File(
                        file.parentFile,
                        "${file.name}.tmp"
                )


        temporaryFile.setText(
                content,
                'UTF-8'
        )


        try {

            Files.move(
                    temporaryFile.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            )
        }
        finally {

            if (temporaryFile.exists()) {
                temporaryFile.delete()
            }
        }
    }
}
