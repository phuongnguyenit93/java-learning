package com.example.learning.utils

import org.gradle.api.Project


final class ProjectPropertyUtils {

    private ProjectPropertyUtils() {
    }


    static String getString(
            Project project,
            String propertyName
    ) {

        return project
                .findProperty(
                        propertyName
                )
                ?.toString()
                ?.trim()
    }


    static boolean isEnabled(
            Project project,
            String propertyName
    ) {

        return getString(
                project,
                propertyName
        )?.equalsIgnoreCase(
                'TRUE'
        ) ?: false
    }


    static List<String> getCsvList(
            Project project,
            String propertyName
    ) {

        String rawValue =
                getString(
                        project,
                        propertyName
                )


        if (
                rawValue == null ||
                        rawValue.isBlank()
        ) {

            return []
        }


        return rawValue
                .split(',')
                .collect {
                    String value ->

                        value.trim()
                }
                .findAll {
                    String value ->

                        !value.isBlank()
                }
    }
}