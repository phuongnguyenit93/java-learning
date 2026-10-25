package com.example.learning.setup.module.interview.service

import com.example.learning.utils.ProjectPropertyUtils
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
        if (!ProjectPropertyUtils.isEnabled(project, 'BUILD_INTERVIEW')) {
            logger.info('[INTERVIEW-STRUCTURE] Skip {} because BUILD_INTERVIEW != TRUE.', project.path)
            return
        }

        File directory = new File(project.projectDir, 'content/interview')
        if (directory.exists() && !directory.isDirectory()) {
            throw new GradleException("Interview content path is not a directory: ${directory.absolutePath}")
        }
        // Existing legacy content is retained for migration/review, not mutated.
    }
}
