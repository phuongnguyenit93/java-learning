package com.example.learning.setup.module.quiz.plugin

import com.example.learning.setup.module.quiz.service.QuizStructureService
import org.gradle.api.Plugin
import org.gradle.api.Project


class QuizSetupPlugin
        implements Plugin<Project> {

    @Override
    void apply(
            Project project
    ) {

        QuizStructureService service =
                new QuizStructureService(
                        project.logger
                )


        service.setup(
                project
        )
    }
}
