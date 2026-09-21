package com.example.learning.task.readme.finalReadme.task

import com.example.learning.task.readme.finalReadme.service.GenerateFinalReadmeService
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(
        because = 'Generates README files directly inside the project directory'
)
abstract class GenerateFinalReadmeTask
        extends DefaultTask {

    @Input
    abstract ListProperty<String> getLanguages()


    @TaskAction
    void generate() {

        List<String> configuredLanguages =
                languages
                        .getOrElse([])
                        .collect {
                            it?.trim()
                                    ?.toLowerCase(
                                            Locale.ROOT
                                    )
                        }
                        .findAll {
                            it != null &&
                                    !it.isBlank()
                        }
                        .unique()


        if (configuredLanguages.isEmpty()) {

            throw new GradleException(
                    """
Final README languages are not configured.

Configure MODULE_LANGUAGE in master.json, for example:

"MODULE_LANGUAGE": {
    "TYPE": "list",
    "VALUE": ["vi", "en"]
}
"""
            )
        }


        logger.lifecycle(
                '=================================================='
        )

        logger.lifecycle(
                'BUILD FINAL README'
        )

        logger.lifecycle(
                'Project: {}',
                project.name
        )

        logger.lifecycle(
                'Languages: {}',
                configuredLanguages.join(', ')
        )

        logger.lifecycle(
                '=================================================='
        )


        GenerateFinalReadmeService service =
                new GenerateFinalReadmeService(
                        logger
                )


        service.generate(
                project.projectDir,
                configuredLanguages
        )


        logger.lifecycle(
                'Final README generation completed for: {}',
                project.name
        )
    }
}
