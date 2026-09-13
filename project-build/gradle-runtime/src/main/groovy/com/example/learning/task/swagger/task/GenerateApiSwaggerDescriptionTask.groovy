package com.example.learning.task.swagger.task

import com.example.learning.task.swagger.config.SwaggerDescriptionDefaultConfig
import com.example.learning.task.swagger.model.SwaggerDescriptionDefault
import com.example.learning.task.swagger.model.SwaggerScanResult
import com.example.learning.task.swagger.service.SwaggerJavaSourceScanner
import com.example.learning.task.swagger.service.SwaggerYamlMergeService
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(
        because = 'Swagger YAML contains both generated and human-maintained fields'
)
abstract class GenerateApiSwaggerDescriptionTask
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
                        }
                        .findAll {
                            it != null &&
                                    !it.isBlank()
                        }
                        .unique()


        if (configuredLanguages.isEmpty()) {

            throw new GradleException(
                    """
Swagger languages are not configured.

Example:

generateApiSwaggerDescription {

    languages = [
        'vi',
        'en'
    ]
}
"""
            )
        }


        /*
         * Validate default configuration trước.
         *
         * Nếu một language chưa có default,
         * fail trước khi động vào YAML.
         */
        Map<String, SwaggerDescriptionDefault> defaultsByLanguage =
                configuredLanguages.collectEntries {
                    String language -> [(language): SwaggerDescriptionDefaultConfig.get(language)]
                }


        File sourceDirectory =
                new File(
                        project.projectDir,
                        'src/main/java'
                )


        if (
                !sourceDirectory.exists() ||
                        !sourceDirectory.isDirectory()
        ) {

            logger.lifecycle(
                    '[SWAGGER] SKIP - Java source directory not found: {}',
                    sourceDirectory.absolutePath
            )

            return
        }


        File resourceDirectory =
                new File(
                        project.projectDir,
                        'src/main/resources'
                )


        SwaggerJavaSourceScanner scanner =
                new SwaggerJavaSourceScanner()


        SwaggerScanResult scanResult =
                scanner.scan(
                        sourceDirectory
                )


        logger.lifecycle(
                '[SWAGGER] Found {} API methods and {} parameter names.',
                scanResult.apiCount,
                scanResult.parameterNames.size()
        )


        SwaggerYamlMergeService mergeService =
                new SwaggerYamlMergeService()


        configuredLanguages.each {
            String language ->

                File swaggerDirectory =
                        new File(
                                resourceDirectory,
                                "swagger/${language}"
                        )


                mergeService.merge(
                        swaggerDirectory,
                        scanResult,
                        defaultsByLanguage[
                                language
                        ]
                )


                logger.lifecycle(
                        '[SWAGGER] Updated language: {}',
                        language
                )
        }
    }
}