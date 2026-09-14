package com.example.learning.setup.module.env.plugin

import com.example.learning.setup.module.env.service.EnvStructureService
import org.gradle.api.Plugin
import org.gradle.api.Project


class EnvSetupPlugin
        implements Plugin<Project> {

    @Override
    void apply(
            Project project
    ) {

        EnvStructureService service =
                new EnvStructureService(
                        project.logger
                )


        service.setup(
                project
        )
    }
}
