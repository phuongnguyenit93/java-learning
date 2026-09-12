package com.example.learning.utils

import org.gradle.api.Project


final class ProjectPluginUtils {

    private ProjectPluginUtils() {
    }


    static void apply(
            Project project,
            String pluginId
    ) {

        project
                .pluginManager
                .apply(
                        pluginId
                )
    }


    static boolean has(
            Project project,
            String pluginId
    ) {

        return project
                .pluginManager
                .hasPlugin(
                        pluginId
                )
    }
}