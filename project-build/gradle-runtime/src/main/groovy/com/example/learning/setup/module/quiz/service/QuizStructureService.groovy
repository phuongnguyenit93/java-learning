package com.example.learning.setup.module.quiz.service

import com.example.learning.utils.ProjectPropertyUtils
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger

/**
 * Chapter Quiz question.yml belongs beside the chapter Knowledge under
 * content/readme/docs/<lang>/menu/<chapter>/quiz/. The Sheet importer
 * creates that folder when its chapter has authored content. Never recreate
 * legacy content/quiz during Gradle configuration.
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
        // Existing legacy content is retained for migration/review, not mutated.
    }
}
