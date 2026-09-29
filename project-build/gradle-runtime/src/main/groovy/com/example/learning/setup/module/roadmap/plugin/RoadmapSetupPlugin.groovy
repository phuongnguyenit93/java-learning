package com.example.learning.setup.module.roadmap.plugin

import com.example.learning.setup.module.roadmap.service.RoadmapStructureService
import org.gradle.api.Plugin
import org.gradle.api.Project


class RoadmapSetupPlugin
        implements Plugin<Project> {

    @Override
    void apply(
            Project project
    ) {

        new RoadmapStructureService(
                project.logger
        ).setup(
                project
        )
    }
}
