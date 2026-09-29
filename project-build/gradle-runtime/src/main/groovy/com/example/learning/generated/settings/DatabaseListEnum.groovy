package com.example.learning.generated.settings


enum DatabaseListEnum {

    MONGODB(
            'DATABASE_MONGODB',
            'NOSQL',
            'module:infrastructure:system:database:engine:document:mongodb'
    ),

    MYSQL(
            'DATABASE_MYSQL',
            'SQL',
            'module:infrastructure:system:database:engine:relational:mysql'
    ),

    ORACLE(
            'DATABASE_ORACLE',
            'SQL',
            'module:infrastructure:system:database:engine:relational:oracle'
    ),

    POSTGRESQL(
            'DATABASE_POSTGRESQL',
            'SQL',
            'module:infrastructure:system:database:engine:relational:postgresql'
    );


    final String moduleName

    final String databaseType

    final String modulePath


    DatabaseListEnum(
            String moduleName,
            String databaseType,
            String modulePath
    ) {

        this.moduleName =
                moduleName

        this.databaseType =
                databaseType

        this.modulePath =
                modulePath
    }


    static DatabaseListEnum findByName(
            String value
    ) {

        if (
                value == null ||
                        value.isBlank()
        ) {

            return null
        }


        String normalized =
                value.trim()


        return values().find {
            DatabaseListEnum database ->

                database
                        .name()
                        .equalsIgnoreCase(
                                normalized
                        )
        }
    }
}
