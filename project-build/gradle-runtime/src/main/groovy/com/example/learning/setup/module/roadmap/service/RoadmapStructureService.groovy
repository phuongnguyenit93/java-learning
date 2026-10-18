package com.example.learning.setup.module.roadmap.service

import com.example.learning.utils.ProjectPropertyUtils
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger

/**
 * BUILD_ROADMAP provisions only content/roadmap. Creating roadmap.yml for a
 * language and maintaining its content belong exclusively to the authoring
 * and Sheet import workflow, never to Gradle configuration / IDE sync.
 */
class RoadmapStructureService {
    private final Logger logger

    RoadmapStructureService(Logger logger) {
        this.logger = logger
    }

    void setup(Project project) {
        if (!ProjectPropertyUtils.isEnabled(project, 'BUILD_ROADMAP')) {
            logger.info('[ROADMAP-STRUCTURE] Skip {} because BUILD_ROADMAP != TRUE.', project.path)
            return
        }

        File directory = new File(project.projectDir, 'content/roadmap')
        if (directory.exists() && !directory.isDirectory()) {
            throw new GradleException("Roadmap content path is not a directory: ${directory.absolutePath}")
        }
        if (!directory.isDirectory()) {
            if (!directory.mkdirs() && !directory.isDirectory()) {
                throw new GradleException("Unable to create Roadmap content directory: ${directory.absolutePath}")
            }
            logger.lifecycle('[ROADMAP-STRUCTURE] Created directory: {}', directory.absolutePath)
        }
    }
}
