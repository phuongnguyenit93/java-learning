package com.example.learning.orchestration

import com.example.learning.setup.settings.preset.plugin.PresetSetupPlugin
import com.example.learning.setup.settings.properties.plugin.PropertiesSetupPlugin
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings


class SettingsOrchestrationPlugin
        implements Plugin<Settings> {


    @Override
    void apply(
            Settings settings
    ) {

        settings.pluginManager.apply(
                PresetSetupPlugin
        )

        settings.pluginManager.apply(
                PropertiesSetupPlugin
        )
    }
}