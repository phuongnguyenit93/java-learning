package com.example.learning.task.intellij.plugin

import com.example.learning.task.intellij.task.IntellijConfigTemplateTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

class IntellijTaskPlugin
        implements Plugin<Project> {

    @Override
    void apply(
            Project project
    ) {

        // ==========================================
        // Module metadata
        // ==========================================

        String applicationName =
                project.findProperty(
                        'APPLICATION_NAME'
                )
                        ?.toString()
                        ?.trim()


        String moduleId =
                project.findProperty(
                        'MODULE_ID'
                )
                        ?.toString()
                        ?.trim()


        String mainClass =
                project.findProperty(
                        'MAIN_CLASS_PATH'
                )
                        ?.toString()
                        ?.trim()


        // ==========================================
        // Validate metadata
        // ==========================================

        if (
                applicationName == null ||
                        applicationName.isBlank() ||
                        moduleId == null ||
                        moduleId.isBlank() ||
                        mainClass == null ||
                        mainClass.isBlank()
        ) {

            project.logger.info(
                    '[INTELLIJ] Skip {} because application metadata is incomplete.',
                    project.path
            )

            return
        }


        // ==========================================
        // Base package
        // ==========================================

        if (
                !project.gradle
                        .extensions
                        .extraProperties
                        .has('basePackage')
        ) {

            project.logger.info(
                    '[INTELLIJ] Skip {} because basePackage is not defined.',
                    project.path
            )

            return
        }


        String basePackage =
                project.gradle
                        .extensions
                        .extraProperties
                        .get('basePackage')
                        .toString()


        String packagePath =
                basePackage.replace(
                        '.',
                        '/'
                )


        // ==========================================
        // Spring Boot application class
        // ==========================================

        File applicationFile =
                project.file(
                        "src/main/java/${packagePath}/${applicationName}.java"
                )


        if (!applicationFile.exists()) {

            project.logger.info(
                    '[INTELLIJ] Skip {} because application class does not exist: {}',
                    project.path,
                    applicationFile.absolutePath
            )

            return
        }


        // ==========================================
        // IntelliJ template
        // ==========================================

        String templateContent =
                loadTemplateResource(
                        'intellij/run_config_intellij_springboot.xml'
                )


        // ==========================================
        // .env
        // ==========================================

        File envFile =
                new File(
                        project.projectDir,
                        '.env'
                )

        // ==========================================
        // IntelliJ run config output
        // ==========================================

        File runConfigFile =
                new File(
                        project.rootProject.rootDir,
                        ".run/${applicationName}.run.xml"
                )


        // ==========================================
        // Register
        // ==========================================

        project.tasks.register(
                'generateIntellijRunConfig',
                IntellijConfigTemplateTask
        ) { task ->

            task.group =
                    'intellij'

            task.description =
                    'Generate IntelliJ Spring Boot run configuration.'


            // --------------------------------------
            // Application
            // --------------------------------------

            task.applicationName.set(
                    applicationName
            )

            task.moduleId.set(
                    moduleId
            )

            task.mainClass.set(
                    mainClass
            )


            // --------------------------------------
            // Environment
            // --------------------------------------

            task.envPath.set(
                    envFile.absolutePath
            )
            

            // --------------------------------------
            // Template
            // --------------------------------------

            task.templateContent.set(
                    templateContent
            )


            // --------------------------------------
            // Output
            // --------------------------------------

            task.runConfigFile.set(
                    runConfigFile
            )
        }
    }

    private static String loadTemplateResource(
            String resourcePath
    ) {

        InputStream inputStream =
                IntellijTaskPlugin
                        .class
                        .classLoader
                        .getResourceAsStream(
                                resourcePath
                        )


        if (inputStream == null) {

            throw new GradleException(
                    """
Unable to find IntelliJ template resource:

${resourcePath}
""".stripIndent()
            )
        }


        try {

            return inputStream.getText(
                    'UTF-8'
            )
        }
        finally {

            inputStream.close()
        }
    }
}