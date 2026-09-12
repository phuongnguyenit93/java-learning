package com.example.learning.setup.root.database.service

import org.gradle.api.GradleException
import org.gradle.api.Project


class DatabaseEnvironmentService {

    Map<String, String> load(
            Project databaseProject
    ) {

        File envFile =
                new File(
                        databaseProject.projectDir,
                        '.env'
                )


        if (!envFile.isFile()) {

            throw new GradleException(
                    """
Database environment file was not found.

Module:
${databaseProject.path}

Expected:
${envFile.absolutePath}
""".stripIndent()
            )
        }


        Map<String, String> environment =
                new LinkedHashMap<>()


        envFile.eachLine(
                'UTF-8'
        ) {
            String line,
            int lineNumber ->

                String normalized =
                        line.trim()


                if (
                        normalized.isBlank() ||
                                normalized.startsWith(
                                        '#'
                                )
                ) {

                    return
                }


                String[] parts =
                        normalized.split(
                                '=',
                                2
                        )


                if (parts.length != 2) {

                    throw new GradleException(
                            """
Invalid .env entry.

File:
${envFile.absolutePath}

Line:
${lineNumber}

Content:
${line}
""".stripIndent()
                    )
                }


                String key =
                        parts[0].trim()


                String value =
                        parts[1].trim()


                environment[
                        key
                ] =
                        value
        }


        return environment
    }


    String requireValue(
            Map<String, String> environment,
            String key
    ) {

        String value =
                environment[
                        key
                ]


        if (
                value == null ||
                        value.isBlank()
        ) {

            throw new GradleException(
                    "Missing required environment variable '${key}'."
            )
        }


        return value
    }
}