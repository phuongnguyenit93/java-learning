package com.example.learning.setup.root.database.service

import com.example.learning.generated.settings.DatabaseListEnum
import org.gradle.api.GradleException
import org.gradle.api.Project


class DatabaseProjectResolverService {

    DatabaseListEnum resolveFromProperty(
            Project rootProject
    ) {

        String databaseName =
                rootProject
                        .findProperty(
                                'DATABASE'
                        )
                        ?.toString()
                        ?.trim()


        if (
                databaseName == null ||
                        databaseName.isBlank()
        ) {

            throw new GradleException(
                    """
Missing DATABASE.

Please execute with:

-PDATABASE=MONGODB
""".stripIndent()
            )
        }


        DatabaseListEnum database =
                DatabaseListEnum.findByName(
                        databaseName
                )


        if (database == null) {

            String available =
                    DatabaseListEnum
                            .values()
                            .collect {
                                it.name()
                            }
                            .join(
                                    ', '
                            )


            throw new GradleException(
                    """
Database '${databaseName}' does not exist.

Available databases:
${available}
""".stripIndent()
            )
        }


        return database
    }


    DatabaseListEnum resolveByName(
            String databaseName
    ) {

        DatabaseListEnum database =
                DatabaseListEnum.findByName(
                        databaseName
                )


        if (database == null) {

            throw new GradleException(
                    "Database '${databaseName}' does not exist."
            )
        }


        return database
    }


    Project resolveProject(
            Project rootProject,
            DatabaseListEnum database
    ) {

        Project databaseProject =
                rootProject.findProject(
                        database.modulePath
                )


        if (databaseProject == null) {

            throw new GradleException(
                    """
Unable to resolve database module.

Database:
${database.name()}

Module name:
${database.moduleName}

Module path:
${database.modulePath}
""".stripIndent()
            )
        }


        return databaseProject
    }
}