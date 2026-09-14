package com.example.learning.setup.module.readme.plugin

import com.example.learning.setup.module.readme.service.ReadmeStructureService
import org.gradle.api.Plugin
import org.gradle.api.Project


class ReadmeSetupPlugin
        implements Plugin<Project> {

    @Override
    void apply(
            Project project
    ) {

        ReadmeStructureService service =
                new ReadmeStructureService(
                        project.logger
                )


        service.setup(
                project
        )
    }
}
