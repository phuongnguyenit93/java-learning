package com.example.learning.setup.module.quiz.service

import com.example.learning.utils.ProjectPropertyUtils
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger

/**
 * BUILD_QUIZ only provisions the module content directory. Authored
 * question.yml files belong to the Sheet importer/content workflow and must
 * never be created or rewritten as a side effect of Gradle configuration.
 */
class QuizStructureService {
    private final Logger logger

    QuizStructureService(Logger logger) {
        this.logger = logger
    }

    void setup(Project project) {
        if (!ProjectPropertyUtils.isEnabled(project, 'BUILD_QUIZ')) {
            logger.info('[QUIZ-STRUCTURE] Skip {} because BUILD_QUIZ != TRUE.', project.path)
            return
        }

        File directory = new File(project.projectDir, 'content/quiz')
        if (directory.exists() && !directory.isDirectory()) {
            throw new GradleException("Quiz content path is not a directory: ${directory.absolutePath}")
        }
        if (!directory.isDirectory()) {
            if (!directory.mkdirs() && !directory.isDirectory()) {
                throw new GradleException("Unable to create Quiz content directory: ${directory.absolutePath}")
            }
            logger.lifecycle('[QUIZ-STRUCTURE] Created directory: {}', directory.absolutePath)
        }
    }
}
