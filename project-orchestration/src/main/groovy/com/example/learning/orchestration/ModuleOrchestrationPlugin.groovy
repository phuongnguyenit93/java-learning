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
        // Roadmap setup is only needed by modules with learning Docs or an
        // existing Roadmap directory; runtime support modules do not receive
        // learning skeletons just because Gradle is configured.
        // ====================================================

        boolean hasLearningDocs =
                new File(project.projectDir, 'content/readme/docs').isDirectory()
        if (hasLearningDocs ||
                new File(project.projectDir, 'content/roadmap').exists()) {
            project.pluginManager.apply(
                    ProjectPluginEnum.ROADMAP_SETUP_PLUGIN.id
            )
        }

        // ====================================================
        // Reference setup
        //
        // Reference is a shared module-level learning artifact.
        // It is not localized by MODULE_LANGUAGE.
        // ====================================================

        if (hasLearningDocs ||
                new File(project.projectDir, 'content/reference').exists()) {
            project.pluginManager.apply(
                    ProjectPluginEnum.REFERENCE_SETUP_PLUGIN.id
            )
        }

        // ====================================================
        // README setup is idempotent for established README + Docs modules.
        // Do not synthesize a root README for a Docs-only module during sync.
        // ====================================================

        if (hasLearningDocs &&
                new File(project.projectDir, 'README.md').isFile()) {
            project.pluginManager.apply(
                    ProjectPluginEnum.README_SETUP_PLUGIN.id
            )
        } else {
            project.logger.info(
                    '[PROJECT-ORCHESTRATION] Skip README setup for {}: no established README.md and Docs pair.',
                    project.path
            )
        }

        // ====================================================
        // Video Script YAML is imported explicitly from Google Sheets;
        // no Video skeleton setup plugin runs on Gradle Sync.
        // ====================================================

        project.logger.info(
                '[PROJECT-ORCHESTRATION] Video skeleton setup not required for {}.',
                project.path
        )

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

        // Quiz/Interview chapters are authored by their importers.
        // Legacy setup plugins are intentionally not applied: they must
        // not recreate empty content/quiz or content/interview directories.

        // ====================================================
        // Execution Context setup
        // ====================================================

        if ('SERVLET'.equalsIgnoreCase(
                ProjectPropertyUtils.getString(project, 'MODULE_TYPE'))) {

            project.pluginManager.apply(
                    ProjectPluginEnum.EXECUTION_CONTEXT_SETUP_PLUGIN.id
            )
        }
        else {

            project.logger.info(
                '[PROJECT-ORCHESTRATION] Skip servlet-only EXECUTION CONTEXT setup for {}.',
                    project.path
            )
        }

        // ====================================================
        // Task setup
        //
        // The single module-level dispatch point for task plugins,
        // including SHEETS -> importSheetsContent. Task types are
        // discovered from gradle-runtime's module-task-list.json.
        // No Google Sheets API wiring is done by orchestration.
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
