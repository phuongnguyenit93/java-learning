package com.example.learning.setup.settings.preset.plugin

import com.example.learning.setup.settings.preset.model.ModuleSetupInfo
import com.example.learning.setup.settings.preset.service.ModuleConfigurationSyncService
import com.example.learning.setup.settings.preset.service.SettingInfoGeneratorService
import com.example.learning.setup.settings.preset.service.SettingScannerService
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging


class PresetSetupPlugin
        implements Plugin<Settings> {

    @Override
    void apply(
            Settings settings
    ) {

        Logger logger =
                Logging.getLogger(
                        PresetSetupPlugin
                )


        File rootDirectory =
                settings.rootDir


        logger.info(
                '[PRESET-SETUP] Scan modules from: {}',
                rootDirectory.absolutePath
        )


        // ====================================================
        // Services
        // ====================================================

        SettingScannerService scannerService =
                new SettingScannerService()


        ModuleConfigurationSyncService configurationSyncService =
                new ModuleConfigurationSyncService(
                        logger
                )


        SettingInfoGeneratorService infoGeneratorService =
                new SettingInfoGeneratorService(
                        logger
                )


        // ====================================================
        // Scan modules
        // ====================================================

        List<ModuleSetupInfo> modules =
                scannerService.scan(
                        rootDirectory
                )


        logger.lifecycle(
                '🔍 [PRESET-SETUP] Found {} modules.',
                modules.size()
        )


        // ====================================================
        // Include + sync
        // ====================================================

        modules.eachWithIndex {
            ModuleSetupInfo module,
            int index ->

                // ============================================
                // Include Gradle project
                // ============================================

                settings.include(
                        ":${module.modulePath}"
                )


                // ============================================
                // Sync configuration
                // ============================================

                Map<String, Map> state =
                        configurationSyncService.sync(
                                module.directory
                        )


                Map master =
                        state.master


                Map properties =
                        state.properties


                // ============================================
                // Collect project information
                // ============================================

                infoGeneratorService.collect(
                        module,
                        master,
                        properties
                )


                // ============================================
                // Logging
                // ============================================

                String serviceName =
                        master
                                .SERVICE_NAME
                                ?.VALUE
                                ?.toString()
                                ?.trim()


                if (
                        serviceName == null ||
                                serviceName.isBlank()
                ) {

                    serviceName =
                            module.directory.name
                }


                logger.info(
                        '[PRESET-SETUP] [{}] {} -> :{}',
                        index + 1,
                        serviceName,
                        module.modulePath
                )
        }


        // ====================================================
        // Generated global information
        // ====================================================

        infoGeneratorService.writeGeneratedFiles(
                rootDirectory
        )


        logger.lifecycle(
                '✨ [PRESET-SETUP] Loaded {} modules.',
                modules.size()
        )
    }
}