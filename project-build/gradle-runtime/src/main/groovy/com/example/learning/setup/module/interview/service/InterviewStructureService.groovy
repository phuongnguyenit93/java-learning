package com.example.learning.setup.module.interview.service

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger

/**
 * Chapter Interview question.yml belongs beside the chapter Knowledge under
 * content/readme/docs/<lang>/menu/<chapter>/interview/. The Sheet importer
 * creates that folder when its chapter has authored content. Never recreate
 * legacy content/interview during Gradle configuration.
 */
class InterviewStructureService {
    private final Logger logger

    InterviewStructureService(Logger logger) {
        this.logger = logger
    }

    void setup(Project project) {
        File directory = new File(project.projectDir, 'content/interview')
        if (directory.exists() && !directory.isDirectory()) {
            throw new GradleException("Interview content path is not a directory: ${directory.absolutePath}")
        }
        // Existing legacy content is retained for migration/review, not mutated.
        logger.info('[INTERVIEW-STRUCTURE] Preserve legacy content for {} without creating folders.',
                project.path)
    }
}
