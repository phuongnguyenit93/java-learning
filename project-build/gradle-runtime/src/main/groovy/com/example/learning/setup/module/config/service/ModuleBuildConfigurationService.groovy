package com.example.learning.setup.module.config.service

import com.example.learning.generated.settings.ModuleListEnum
import com.example.learning.setup.module.config.model.ModuleType
import com.example.learning.utils.ModuleProjectUtils
import com.example.learning.utils.ProjectDependencyUtils
import com.example.learning.utils.ProjectPluginUtils
import com.example.learning.utils.ProjectPropertyUtils
import org.gradle.api.Project
import org.gradle.api.logging.Logger


class ModuleBuildConfigurationService {

    private final Logger logger


    ModuleBuildConfigurationService(
            Logger logger
    ) {

        this.logger =
                logger
    }


    // ========================================================
    // Common setup
    // ========================================================

    void configureCommon(
            Project project
    ) {

        // ====================================================
        // Common plugins
        // ====================================================

        ProjectPluginUtils.apply(
                project,
                'java-library'
        )


        ProjectPluginUtils.apply(
                project,
                'org.springframework.boot'
        )


        ProjectPluginUtils.apply(
                project,
                'io.spring.dependency-management'
        )


        ProjectPluginUtils.apply(
                project,
                'io.freefair.lombok'
        )


        // ====================================================
        // Common module dependencies
        // ====================================================

        configureModuleDependencies(
                project
        )


        logger.info(
                '[MODULE-BUILD] Common setup completed: {}',
                project.path
        )
    }


    // ========================================================
    // Type-specific setup
    // ========================================================

    void configureByType(
            Project project,
            ModuleType moduleType
    ) {

        switch (moduleType) {

            case ModuleType.SERVLET:

                configureRunnableApplication(
                        project,
                        ModuleType.SERVLET,
                        'org.springframework.boot:spring-boot-starter-web'
                )

                return


            case ModuleType.REACTIVE:

                configureRunnableApplication(
                        project,
                        ModuleType.REACTIVE,
                        'org.springframework.boot:spring-boot-starter-webflux'
                )

                return


            case ModuleType.LIBRARY:

                configureLibrary(
                        project
                )

                return


            case ModuleType.PLATFORM:

                configurePlatform(
                        project
                )

                return
        }
    }


    // ========================================================
    // Runnable application
    // ========================================================

    private void configureRunnableApplication(
            Project project,
            ModuleType moduleType,
            String webStarter
    ) {

        // ====================================================
        // Web stack
        // ====================================================

        ProjectDependencyUtils.addExternal(
                project,
                'implementation',
                webStarter
        )


        // ====================================================
        // Swagger
        // ====================================================

        configureSwaggerDependency(
                project,
                moduleType
        )


        configureExecutionContextDependency(
                project,
                moduleType
        )


        // ====================================================
        // Database consumer
        // ====================================================

        configureDatabaseDependencies(
                project
        )


        logger.info(
                '[MODULE-BUILD] Configured {}: {}',
                moduleType,
                project.path
        )
    }


    private void configureSwaggerDependency(
            Project project,
            ModuleType moduleType
    ) {

        if (
                !ProjectPropertyUtils.isEnabled(
                        project,
                        'BUILD_SWAGGER'
                )
        ) {

            return
        }


        ModuleListEnum swaggerAdapter


        switch (moduleType) {

            case ModuleType.SERVLET:

                swaggerAdapter =
                        ModuleListEnum.GLOBAL_SWAGGER_SERVLET

                break


            case ModuleType.REACTIVE:

                swaggerAdapter =
                        ModuleListEnum.GLOBAL_SWAGGER_REACTIVE

                break


            default:

                return
        }


        Project swaggerProject =
                ModuleProjectUtils.findByServiceName(
                        project,
                        swaggerAdapter.name()
                )


        ProjectDependencyUtils.addProject(
                project,
                'implementation',
                swaggerProject
        )


        logger.lifecycle(
                '📘 [MODULE-BUILD] [{}] Swagger -> {}',
                project.path,
                swaggerProject.path
        )
    }


    private void configureExecutionContextDependency(
            Project project,
            ModuleType moduleType
    ) {

        if (
                !ProjectPropertyUtils.isEnabled(
                        project,
                        'BUILD_EXECUTION_CONTEXT'
                )
        ) {

            return
        }


        String adapterServiceName


        switch (moduleType) {

            case ModuleType.SERVLET:

                adapterServiceName =
                        'GLOBAL_EXECUTION_CONTEXT_SERVLET'

                break


            case ModuleType.REACTIVE:

                project.logger.warn(
                        '[MODULE-BUILD] BUILD_EXECUTION_CONTEXT is enabled for REACTIVE {}, but a reactive adapter has not been implemented yet.',
                        project.path
                )

                return


            default:

                return
        }


        Project adapterProject =
                ModuleProjectUtils.findByServiceName(
                        project,
                        adapterServiceName
                )


        ProjectDependencyUtils.addProject(
                project,
                'implementation',
                adapterProject
        )


        project.logger.lifecycle(
                '🧭 [MODULE-BUILD] [{}] Execution Context -> {}',
                project.path,
                adapterProject.path
        )
    }


    // ========================================================
    // LIBRARY
    // ========================================================

    private void configureLibrary(
            Project project
    ) {

        configureNonRunnableModule(
                project
        )


        logger.info(
                '[MODULE-BUILD] Configured LIBRARY: {}',
                project.path
        )
    }


    // ========================================================
    // PLATFORM
    // ========================================================

    private void configurePlatform(
            Project project
    ) {

        configureNonRunnableModule(
                project
        )


        logger.info(
                '[MODULE-BUILD] Configured PLATFORM: {}',
                project.path
        )
    }


    // ========================================================
    // Non-runnable
    // ========================================================

    private void configureNonRunnableModule(
            Project project
    ) {

        project.tasks
                .named(
                        'bootJar'
                )
                .configure {
                    enabled =
                            false
                }


        project.tasks
                .named(
                        'jar'
                )
                .configure {
                    enabled =
                            true
                }


        project.tasks
                .named(
                        'bootRun'
                )
                .configure {
                    enabled =
                            false
                }
    }


    // ========================================================
    // Database consumer
    // ========================================================

    private void configureDatabaseDependencies(
            Project project
    ) {

        if (
                !ProjectPropertyUtils.isEnabled(
                        project,
                        'USE_DATABASE'
                )
        ) {

            return
        }


        List<String> databaseList =
                ProjectPropertyUtils.getCsvList(
                        project,
                        'DATABASE_LIST'
                )


        if (databaseList.isEmpty()) {

            logger.info(
                    '[MODULE-BUILD] USE_DATABASE=TRUE but DATABASE_LIST is empty: {}',
                    project.path
            )

            return
        }


        ProjectDependencyUtils.addExternal(
                project,
                'implementation',
                'org.springframework.boot:spring-boot-starter-validation'
        )


        databaseList.each {
            String databaseServiceName ->

                try {

                    Project databaseProject =
                            ModuleProjectUtils.findByServiceName(
                                    project,
                                    databaseServiceName
                            )


                    ProjectDependencyUtils.addProject(
                            project,
                            'implementation',
                            databaseProject
                    )


                    logger.lifecycle(
                            '🗄️ [MODULE-BUILD] [{}] Database -> {}',
                            project.path,
                            databaseProject.path
                    )
                }
                catch (Exception exception) {

                    logger.error(
                            '❌ [MODULE-BUILD] Unable to resolve database module {} for {}: {}',
                            databaseServiceName,
                            project.path,
                            exception.message
                    )
                }
        }
    }


    // ========================================================
    // MODULE_DEPEND_LIST
    // ========================================================

    private void configureModuleDependencies(
            Project project
    ) {

        List<String> moduleList =
                ProjectPropertyUtils.getCsvList(
                        project,
                        'MODULE_DEPEND_LIST'
                )


        if (moduleList.isEmpty()) {
            return
        }


        moduleList.each {
            String serviceName ->

                Project dependencyProject =
                        ModuleProjectUtils.findByServiceName(
                                project,
                                serviceName
                        )


                ProjectDependencyUtils.addProject(
                        project,
                        'implementation',
                        dependencyProject
                )


                logger.lifecycle(
                        '🔗 [MODULE-BUILD] [{}] implementation -> {}',
                        project.path,
                        dependencyProject.path
                )
        }
    }
}
