package com.example.learning.setup.settings.properties.plugin

import com.example.learning.setup.settings.properties.service.SettingPropertiesInjectionService
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging


class PropertiesSetupPlugin
        implements Plugin<Settings> {

    private static final List<String> PROFILES =
            [
                    'dev',
                    'loc',
                    'prd'
            ]


    @Override
    void apply(
            Settings settings
    ) {

        Logger logger =
                Logging.getLogger(
                        PropertiesSetupPlugin
                )


        // ====================================================
        // Service
        // ====================================================

        SettingPropertiesInjectionService propertiesInjectionService =
                new SettingPropertiesInjectionService(
                        logger
                )


        // ====================================================
        // Global properties
        // ====================================================

        settings
                .gradle
                .extensions
                .extraProperties
                .set(
                        'profiles',
                        new ArrayList<>(
                                PROFILES
                        )
                )


        // ====================================================
        // Project properties
        // ====================================================

        /*
         * Sau khi Settings evaluation hoàn tất:
         *
         * - các module đã được PresetSetupPlugin include
         * - Gradle Project objects đã tồn tại
         * - inject VALUE từ master.json / properties.json
         *
         * trước khi project build scripts cần sử dụng.
         */
        settings
                .gradle
                .projectsLoaded {
                    Gradle gradle ->

                        propertiesInjectionService.injectAll(
                                gradle
                        )
                }


        logger.lifecycle(
                '✨ [PROPERTIES-SETUP] Profiles: {}',
                PROFILES.join(
                        ', '
                )
        )
    }
}