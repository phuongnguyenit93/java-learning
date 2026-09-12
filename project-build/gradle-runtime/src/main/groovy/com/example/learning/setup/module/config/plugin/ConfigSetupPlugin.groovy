package com.example.learning.module.config.plugin

import com.example.learning.module.config.model.ModuleType
import com.example.learning.module.config.service.ModuleBuildConfigurationService
import com.example.learning.module.config.service.ModuleStructureService
import org.gradle.api.Plugin
import org.gradle.api.Project


class ConfigSetupPlugin
        implements Plugin<Project> {


    @Override
    void apply(
            Project project
    ) {
        ModuleBuildConfigurationService buildConfigurationService =
                new ModuleBuildConfigurationService(
                        project.logger
                )


        ModuleStructureService structureService =
                new ModuleStructureService(
                        project.logger
                )


        // ====================================================
        // Common setup
        // ====================================================

        /*
         * Luôn chạy cho mọi module,
         * kể cả MODULE_TYPE blank / invalid.
         */
        buildConfigurationService.configureCommon(
                project
        )


        // ====================================================
        // Resolve MODULE_TYPE
        // ====================================================

        ModuleType moduleType =
                ModuleType.resolve(
                        project.findProperty(
                                'MODULE_TYPE'
                        )
                )


        /*
         * Blank hoặc invalid:
         *
         * common setup vẫn đã hoàn thành.
         *
         * Chỉ skip phần specialized setup.
         */
        if (moduleType == null) {

            project.logger.info(
                    '[MODULE-SETUP] {} MODULE_TYPE is blank or invalid -> common setup only.',
                    project.path
            )

            return
        }


        project.logger.lifecycle(
                '🏗️ [MODULE-SETUP] {} -> {}',
                project.path,
                moduleType
        )


        // ====================================================
        // Structure
        // ====================================================

        structureService.setup(
                project,
                moduleType
        )


        // ====================================================
        // Type-specific Gradle configuration
        // ====================================================

        buildConfigurationService.configureByType(
                project,
                moduleType
        )
    }
}