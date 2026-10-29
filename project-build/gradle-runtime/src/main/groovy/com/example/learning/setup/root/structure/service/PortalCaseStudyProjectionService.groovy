package com.example.learning.setup.root.structure.service

import org.gradle.api.Project
import org.gradle.api.logging.Logger

/** Publish module-level Case Study groups using the same deterministic projection. */
class PortalCaseStudyProjectionService {
    private final Logger logger

    PortalCaseStudyProjectionService(Logger logger) {
        this.logger = logger
    }

    void generate(Project rootProject) {
        ChapterQuestionProjectionSupport.generate(rootProject, 'casestudy', logger)
    }
}
