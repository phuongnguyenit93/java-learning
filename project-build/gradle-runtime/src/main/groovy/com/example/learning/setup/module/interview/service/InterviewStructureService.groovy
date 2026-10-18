package com.example.learning.setup.module.interview.service

import com.example.learning.utils.ProjectPropertyUtils
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger

/**
 * BUILD_INTERVIEW only provisions the module content directory. Authored
 * question.yml files belong to the Sheet importer/content workflow and must
 * never be created or rewritten as a side effect of Gradle configuration.
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
        if (!directory.isDirectory()) {
            if (!directory.mkdirs() && !directory.isDirectory()) {
                throw new GradleException("Unable to create Interview content directory: ${directory.absolutePath}")
            }
            logger.lifecycle('[INTERVIEW-STRUCTURE] Created directory: {}', directory.absolutePath)
        }
    }
}
