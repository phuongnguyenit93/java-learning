package com.example.learning.setup.module.video.plugin

import com.example.learning.setup.module.video.service.VideoStructureService
import org.gradle.api.Plugin
import org.gradle.api.Project


class VideoSetupPlugin
        implements Plugin<Project> {

    @Override
    void apply(
            Project project
    ) {

        VideoStructureService service =
                new VideoStructureService(
                        project.logger
                )


        service.setup(
                project
        )
    }
}
