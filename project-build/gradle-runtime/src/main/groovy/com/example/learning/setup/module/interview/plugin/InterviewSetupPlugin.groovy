package com.example.learning.setup.module.interview.plugin

import com.example.learning.setup.module.interview.service.InterviewStructureService
import org.gradle.api.Plugin
import org.gradle.api.Project


class InterviewSetupPlugin
        implements Plugin<Project> {

    @Override
    void apply(
            Project project
    ) {

        new InterviewStructureService(
                project.logger
        ).setup(
                project
        )
    }
}
