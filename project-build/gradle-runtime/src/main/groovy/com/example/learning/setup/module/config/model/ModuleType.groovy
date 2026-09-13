package com.example.learning.setup.module.config.model


enum ModuleType {

    APPLICATION(
            true,
            true
    ),

    LIBRARY(
            true,
            false
    ),

    PLATFORM(
            false,
            false
    )


    /**
     * Module có Java source structure hay không.
     */
    final boolean hasJavaSource


    /**
     * Module có Spring Boot main application hay không.
     */
    final boolean runnable


    ModuleType(
            boolean hasJavaSource,
            boolean runnable
    ) {

        this.hasJavaSource =
                hasJavaSource

        this.runnable =
                runnable
    }


    /**
     * Resolve MODULE_TYPE.
     *
     * null / blank / invalid
     * → return null
     *
     * Không throw exception vì orchestration
     * chỉ cần skip module setup.
     */
    static ModuleType resolve(
            Object rawValue
    ) {

        String value =
                rawValue
                        ?.toString()
                        ?.trim()


        if (
                value == null ||
                        value.isBlank()
        ) {

            return null
        }


        return values()
                .find {
                    ModuleType type ->

                        type.name()
                                .equalsIgnoreCase(
                                        value
                                )
                }
    }
}