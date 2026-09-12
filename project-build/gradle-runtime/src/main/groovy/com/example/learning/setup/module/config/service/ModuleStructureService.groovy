package com.example.learning.module.config.service

import com.example.learning.module.config.model.ModuleType
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger


class ModuleStructureService {

    private final Logger logger


    ModuleStructureService(
            Logger logger
    ) {

        this.logger =
                logger
    }


    void setup(
            Project project,
            ModuleType moduleType
    ) {

        switch (moduleType) {

            case ModuleType.APPLICATION:

                setupJavaStructure(
                        project
                )

                setupApplication(
                        project
                )

                return


            case ModuleType.LIBRARY:

                setupJavaStructure(
                        project
                )

                return


            case ModuleType.PLATFORM:

                logger.info(
                        '[MODULE-STRUCTURE] Skip physical structure for PLATFORM: {}',
                        project.path
                )

                return
        }
    }


    // ========================================================
    // Java structure
    // ========================================================

    private void setupJavaStructure(
            Project project
    ) {

        String basePackage =
                getBasePackage(
                        project
                )


        String packagePath =
                basePackage.replace(
                        '.',
                        '/'
                )


        List<String> directories =
                [
                        "src/main/java/${packagePath}",
                        'src/main/resources'
                ]


        directories.each {
            String relativePath ->

                File directory =
                        new File(
                                project.projectDir,
                                relativePath
                        )


                if (directory.exists()) {
                    return
                }


                if (!directory.mkdirs()) {

                    throw new GradleException(
                            """
Unable to create module directory:

${directory.absolutePath}
""".stripIndent()
                    )
                }


                logger.lifecycle(
                        '✨ [MODULE-STRUCTURE] Created: {}',
                        directory.absolutePath
                )
        }
    }


    // ========================================================
    // Application
    // ========================================================

    private void setupApplication(
            Project project
    ) {

        String basePackage =
                getBasePackage(
                        project
                )


        String serviceName =
                project
                        .findProperty(
                                'SERVICE_NAME'
                        )
                        ?.toString()
                        ?.trim()


        if (
                serviceName == null ||
                        serviceName.isBlank()
        ) {

            serviceName =
                    project.name
        }


        String className =
                toApplicationClassName(
                        serviceName
                )


        // ====================================================
        // Application metadata
        // ====================================================

        project
                .extensions
                .extraProperties
                .set(
                        'APPLICATION_NAME',
                        className
                )


        project
                .extensions
                .extraProperties
                .set(
                        'MAIN_CLASS_PATH',
                        "${basePackage}.${className}"
                )


        project
                .extensions
                .extraProperties
                .set(
                        'MODULE_ID',
                        "${project.rootProject.name}${project.path.replace(':', '.')}"
                )


        project
                .extensions
                .extraProperties
                .set(
                        'ENV_PATH',
                        new File(
                                project.projectDir,
                                '.env'
                        ).absolutePath
                )


        // ====================================================
        // Main Java class
        // ====================================================

        String packagePath =
                basePackage.replace(
                        '.',
                        '/'
                )


        File applicationFile =
                new File(
                        project.projectDir,
                        "src/main/java/${packagePath}/${className}.java"
                )


        /*
         * Human-owned sau lần generate đầu.
         *
         * Tuyệt đối không overwrite.
         */
        if (applicationFile.exists()) {

            logger.info(
                    '[MODULE-STRUCTURE] Main class already exists: {}',
                    applicationFile.absolutePath
            )

            return
        }


        renderApplicationClass(
                applicationFile,
                basePackage,
                className
        )
    }


    private void renderApplicationClass(
            File applicationFile,
            String basePackage,
            String className
    ) {

        try {

            String renderedContent =
                    renderApplicationSource(
                            basePackage,
                            className
                    )


            applicationFile.setText(
                    renderedContent,
                    'UTF-8'
            )


            logger.lifecycle(
                    '🚀 [MODULE-STRUCTURE] Generated main class: {}',
                    applicationFile.absolutePath
            )
        }
        catch (Exception exception) {

            throw new GradleException(
                    """
Unable to generate application main class:

${applicationFile.absolutePath}

Reason:
${exception.message}
""".stripIndent(),
                    exception
            )
        }
    }

    private static String renderApplicationSource(
            String basePackage,
            String className
    ) {

        return """package ${basePackage};

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@SpringBootApplication
public class ${className} extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(${className}.class, args);
    }
}
"""
    }


    // ========================================================
    // Helpers
    // ========================================================

    private static String toApplicationClassName(
            String serviceName
    ) {

        return serviceName
                .toLowerCase(
                        Locale.ROOT
                )
                .split('_')
                .findAll {
                    String part ->

                        !part.isBlank()
                }
                .collect {
                    String part ->

                        part.capitalize()
                }
                .join('') +
                'Application'
    }


    private static String getBasePackage(
            Project project
    ) {

        def extraProperties =
                project
                        .gradle
                        .extensions
                        .extraProperties


        if (
                !extraProperties.has(
                        'basePackage'
                )
        ) {

            throw new GradleException(
                    'Global property basePackage was not found.'
            )
        }


        String basePackage =
                extraProperties
                        .get(
                                'basePackage'
                        )
                        ?.toString()
                        ?.trim()


        if (
                basePackage == null ||
                        basePackage.isBlank()
        ) {

            throw new GradleException(
                    'Global property basePackage must not be blank.'
            )
        }


        return basePackage
    }
}