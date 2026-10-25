package com.example.learning.setup.module.quiz.service

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
        File directory = new File(project.projectDir, 'content/quiz')
        if (directory.exists() && !directory.isDirectory()) {
            throw new GradleException("Quiz content path is not a directory: ${directory.absolutePath}")
        }
        // Existing legacy content is retained for migration/review, not mutated.
        logger.info('[QUIZ-STRUCTURE] Preserve legacy content for {} without creating folders.',
                project.path)
    }
}
