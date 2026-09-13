package com.example.learning.orchestration

import com.example.learning.generated.root.ProjectPluginEnum
import com.example.learning.utils.ProjectPluginUtils
import com.example.learning.utils.ProjectPropertyUtils
import com.example.learning.utils.ModuleProjectUtils
import org.gradle.api.Plugin
import org.gradle.api.Project


class ModuleOrchestrationPlugin
        implements Plugin<Project> {

    private static final String SEPARATOR =
            '------------------------------------------------------------'

    @Override
    void apply(
            Project project
    ) {

        // ========================================================
        // Real module guard
        // ========================================================

        if (
                !ModuleProjectUtils.isModule(
                        project
                )
        ) {

            project.logger.info(
                    '[PROJECT-ORCHESTRATION] Skip intermediate project: {}',
                    project.path
            )

            return
        }

        project.logger.info(
                '[PROJECT-ORCHESTRATION] Setup project: {}',
                project.path
        )

        // ====================================================
        // Start
        // ====================================================

        String serviceName =
                ProjectPropertyUtils.getString(
                        project,
                        'SERVICE_NAME'
                ) ?: project.name

        logStart(
                project,
                serviceName
        )


        // ====================================================
        // Khai báo extension cho việc implementation các module depends
        // ====================================================

        project.pluginManager.apply(
                ProjectPluginEnum.DEPENDENCY_SETUP_PLUGIN.id
        )

        // ====================================================
        // Base module setup
        // ====================================================

        /*
         * Mọi subproject đều là module.
         *
         * ModuleSetupPlugin luôn được apply để đảm bảo
         * các baseline Gradle plugins/configurations tồn tại:
         *
         * - implementation
         * - api
         * - developmentOnly
         * - compileOnly
         * - runtimeOnly
         * ...
         *
         * MODULE_TYPE chỉ quyết định specialized setup
         * bên trong ModuleSetupPlugin.
         */
        project.pluginManager.apply(
                ProjectPluginEnum.CONFIG_SETUP_PLUGIN.id
        )

        // ====================================================
        // YML setup
        //
        // Apply cho tất cả real module.
        // BUILD_YML chỉ quyết định việc initialize application.yml.
        // ====================================================

        project.pluginManager.apply(
                ProjectPluginEnum.YML_SETUP_PLUGIN.id
        )

        // ====================================================
        // ENV structure setup
        // ====================================================

        boolean buildEnv =
                ProjectPropertyUtils.isEnabled(
                        project,
                        'BUILD_ENV'
                )


        boolean buildEnvProfile =
                ProjectPropertyUtils.isEnabled(
                        project,
                        'BUILD_ENV_PROFILE'
                )


        if (
                buildEnv ||
                        buildEnvProfile
        ) {

            project.pluginManager.apply(
                    ProjectPluginEnum.ENV_SETUP_PLUGIN.id
            )
        }
        else {

            project.logger.info(
                    '[PROJECT-ORCHESTRATION] Skip ENV structure setup for {}.',
                    project.path
            )
        }

        // ====================================================
        // README setup
        // ====================================================

        if (
                ProjectPropertyUtils.isEnabled(
                        project,
                        'BUILD_README'
                )
        ) {

            project.pluginManager.apply(
                    ProjectPluginEnum.README_SETUP_PLUGIN.id
            )
        }
        else {

            project.logger.info(
                    '[PROJECT-ORCHESTRATION] Skip README setup for {} because BUILD_README != TRUE.',
                    project.path
            )
        }

        // ====================================================
        // Swagger setup
        // ====================================================

        if (
                ProjectPropertyUtils.isEnabled(
                        project,
                        'BUILD_SWAGGER'
                )
        ) {

            project.pluginManager.apply(
                    ProjectPluginEnum.SWAGGER_SETUP_PLUGIN.id
            )
        } else {

            project.logger.info(
                    '[PROJECT-ORCHESTRATION] Skip SWAGGER setup for {} because BUILD_SWAGGER != TRUE.',
                    project.path
            )
        }

        // ====================================================
        // Task setup
        // ====================================================

        if (
                ProjectPropertyUtils.isEnabled(
                        project,
                        'USE_TASK'
                )
        ) {
            ProjectPluginUtils.apply(project,ProjectPluginEnum.TASK_SETUP_PLUGIN.id)
        }
        else {

            project.logger.info(
                    '[PROJECT-ORCHESTRATION] Skip TASK setup for {} because USE_TASK != TRUE.',
                    project.path
            )
        }

        // ====================================================
        // Docker setup
        // ====================================================

        File dockerComposeFile =
                new File(
                        project.projectDir,
                        'docker-compose.yml'
                )


        if (dockerComposeFile.isFile()) {

            project.pluginManager.apply(
                    ProjectPluginEnum.DOCKER_SETUP_PLUGIN.id
            )
        }


        // ====================================================
        // End
        // ====================================================

        logEnd(
                project,
                serviceName
        )
    }

    // ========================================================
    // Logging
    // ========================================================

    private static void logStart(
            Project project,
            String serviceName
    ) {

        project.logger.lifecycle('')
        project.logger.lifecycle(SEPARATOR)

        project.logger.lifecycle(
                '🏗️ [ORCHESTRATION] Service: {}',
                serviceName
        )

        project.logger.lifecycle(
                '📦 [ORCHESTRATION] Project: {}',
                project.path
        )

        project.logger.lifecycle(SEPARATOR)
    }


    private static void logEnd(
            Project project,
            String serviceName
    ) {

        project.logger.lifecycle(SEPARATOR)

        project.logger.lifecycle(
                '✅ [ORCHESTRATION] Completed: {}',
                serviceName
        )

        project.logger.lifecycle(SEPARATOR)
        project.logger.lifecycle('')
    }
}
