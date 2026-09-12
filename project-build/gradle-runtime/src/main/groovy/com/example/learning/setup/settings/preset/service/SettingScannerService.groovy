package com.example.learning.setup.settings.preset.service

import com.example.learning.setup.settings.preset.model.ModuleSetupInfo

import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream


class SettingScannerService {


    List<ModuleSetupInfo> scan(
            File rootDirectory
    ) {

        List<ModuleSetupInfo> modules =
                []


        Path rootPath =
                rootDirectory
                        .toPath()
                        .toAbsolutePath()
                        .normalize()


        Stream<Path> paths =
                Files.walk(
                        rootPath
                )


        try {

            paths
                    .filter {
                        Path path ->

                            Files.isRegularFile(
                                    path
                            )
                    }
                    .filter {
                        Path path ->

                            path.fileName
                                    .toString() ==
                                    'gradle.properties'
                    }
                    .filter {
                        Path path ->

                            /*
                             * Root gradle.properties
                             * không phải module.
                             */
                            path
                                    .toAbsolutePath()
                                    .normalize() !=
                                    rootPath.resolve(
                                            'gradle.properties'
                                    )
                    }
                    .filter {
                        Path path ->

                            !containsExcludedDirectory(
                                    rootPath,
                                    path
                            )
                    }
                    .forEach {
                        Path propertiesPath ->

                            File propertiesFile =
                                    propertiesPath.toFile()


                            File moduleDirectory =
                                    propertiesFile.parentFile


                            Properties properties =
                                    loadProperties(
                                            propertiesFile
                                    )


                            String relativePath =
                                    normalizePath(
                                            rootPath
                                                    .relativize(
                                                            moduleDirectory
                                                                    .toPath()
                                                                    .toAbsolutePath()
                                                                    .normalize()
                                                    )
                                                    .toString()
                                    )


                            String modulePath =
                                    relativePath.replace(
                                            '/',
                                            ':'
                                    )


                            modules.add(
                                    new ModuleSetupInfo(
                                            directory:
                                                    moduleDirectory,

                                            gradleProperties:
                                                    properties,

                                            relativePath:
                                                    relativePath,

                                            modulePath:
                                                    modulePath
                                    )
                            )
                    }
        }
        finally {

            paths.close()
        }


        /*
         * Deterministic order.
         *
         * Generated catalogs:
         *
         * - ModuleListEnum
         * - DatabaseListEnum
         *
         * sẽ luôn ổn định giữa các lần chạy.
         */
        modules.sort {
            ModuleSetupInfo left,
            ModuleSetupInfo right ->

                left.relativePath <=>
                        right.relativePath
        }


        return modules
    }


    private static Properties loadProperties(
            File propertiesFile
    ) {

        Properties properties =
                new Properties()


        propertiesFile.withInputStream {
            InputStream inputStream ->

                properties.load(
                        inputStream
                )
        }


        return properties
    }


    private static boolean containsExcludedDirectory(
            Path rootPath,
            Path path
    ) {

        Path relativePath =
                rootPath.relativize(
                        path
                )


        for (
                Path segment :
                        relativePath
        ) {

            String name =
                    segment.toString()


            if (
                    name == 'build' ||
                            name == '.gradle'
            ) {

                return true
            }
        }


        return false
    }


    private static String normalizePath(
            String path
    ) {

        return path.replace(
                '\\',
                '/'
        )
    }
}