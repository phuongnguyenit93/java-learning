package com.example.learning.setup.module.reference.plugin

import com.example.learning.setup.module.reference.service.ReferenceStructureService
import org.gradle.api.Plugin
import org.gradle.api.Project


class ReferenceSetupPlugin
        implements Plugin<Project> {

    @Override
    void apply(
            Project project
    ) {

        new ReferenceStructureService(
                project.logger
        ).setup(
                project
        )
    }
}
