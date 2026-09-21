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

        return getStringList(
                project,
                propertyName
        )
    }


    static List<String> getStringList(
            Project project,
            String propertyName
    ) {

        Object rawValue =
                project.findProperty(
                        propertyName
                )


        if (rawValue == null) {

            return []
        }


        Collection<?> rawValues


        if (rawValue instanceof Collection) {

            rawValues =
                    rawValue as Collection<?>
        }
        else if (rawValue.getClass().isArray()) {

            rawValues =
                    (rawValue as Object[])
                            .toList()
        }
        else {

            String text =
                    rawValue
                            .toString()
                            .trim()


            if (text.isBlank()) {
                return []
            }


            rawValues =
                    text.split(',')
                            .toList()
        }


        return rawValues
                .collect {
                    Object value ->

                        value
                                ?.toString()
                                ?.trim()
                }
                .findAll {
                    String value ->

                        value != null &&
                                !value.isBlank()
                }
                .unique()
    }
}
