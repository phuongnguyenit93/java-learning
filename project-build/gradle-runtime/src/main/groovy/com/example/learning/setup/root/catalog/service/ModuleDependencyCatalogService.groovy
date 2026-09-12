package com.example.learning.setup.root.catalog.service

import com.example.learning.utils.ModuleProjectUtils
import com.example.learning.utils.ProjectPropertyUtils
import groovy.json.JsonOutput
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.logging.Logger


class ModuleDependencyCatalogService {

    private static final List<String> DEPENDENCY_CONFIGURATIONS =
            [
                    'api',
                    'developmentOnly',
                    'annotationProcessor'
            ]


    private final Logger logger


    ModuleDependencyCatalogService(
            Logger logger
    ) {

        this.logger =
                logger
    }


    void generate(
            Project rootProject
    ) {

        // ====================================================
        // Collect
        // ====================================================

        Map<String, Object> moduleDependList =
                collectModuleDependencies(
                        rootProject
                )

        // ====================================================
        // Output
        // ====================================================

        File outputFile =
                new File(
                        rootProject.projectDir,
                        'project-build/gradle-runtime/src/main/resources/module/module-depend.json'
                )


        ensureParentDirectory(
                outputFile
        )


        String newContent =
                JsonOutput.prettyPrint(
                        JsonOutput.toJson(
                                moduleDependList
                        )
                ) +
                        '\n'


        // ====================================================
        // Idempotency
        // ====================================================

        if (
                outputFile.isFile() &&
                        outputFile.getText('UTF-8') == newContent
        ) {

            logger.info(
                    '[MODULE-DEPENDENCY-CATALOG] UP-TO-DATE: {}',
                    outputFile.absolutePath
            )

            return
        }


        // ====================================================
        // Write
        // ====================================================

        try {

            outputFile.setText(
                    newContent,
                    'UTF-8'
            )
        }
        catch (Exception exception) {

            throw new GradleException(
                    """
Unable to write module dependency catalog:

${outputFile.absolutePath}

Reason:
${exception.message}
""".stripIndent(),
                    exception
            )
        }


        logger.lifecycle(
                '📦 [MODULE-DEPENDENCY-CATALOG] Generated {} module(s): {}',
                moduleDependList.size(),
                outputFile.absolutePath
        )
    }


    // ========================================================
    // Collect
    // ========================================================

    private Map<String, Object> collectModuleDependencies(
            Project rootProject
    ) {

        Map<String, Object> result =
                new LinkedHashMap<>()


        ModuleProjectUtils
                .getModules(
                        rootProject
                )
                .findAll {
                    Project moduleProject ->

                        ProjectPropertyUtils.isEnabled(
                                moduleProject,
                                'IS_MODULE_DEPEND'
                        )
                }
                .each {
                    Project moduleProject ->

                        collectModule(
                                moduleProject,
                                result
                        )
                }


        return result
    }


    private void collectModule(
            Project moduleProject,
            Map<String, Object> result
    ) {

        String serviceName =
                ProjectPropertyUtils.getString(
                        moduleProject,
                        'SERVICE_NAME'
                ) ?: moduleProject.name


        String description =
                ProjectPropertyUtils.getString(
                        moduleProject,
                        'SERVICE_NAME_DESCRIBE'
                )


        Map<String, Object> moduleInfo =
                new LinkedHashMap<>()


        /*
         * Giữ nguyên metadata của legacy implementation.
         */
        moduleInfo['DESCRIBE'] =
                description


        DEPENDENCY_CONFIGURATIONS.each {
            String configurationName ->

                List<String> dependencies =
                        collectConfigurationDependencies(
                                moduleProject,
                                configurationName
                        )


                if (!dependencies.isEmpty()) {

                    moduleInfo[configurationName] =
                            dependencies
                }
        }


        result[serviceName] =
                moduleInfo
    }


    // ========================================================
    // Gradle dependencies
    // ========================================================

    private static List<String> collectConfigurationDependencies(
            Project project,
            String configurationName
    ) {

        def configuration =
                project.configurations.findByName(
                        configurationName
                )


        if (configuration == null) {
            return []
        }


        DependencySet dependencySet =
                configuration.dependencies


        if (dependencySet.isEmpty()) {
            return []
        }


        return dependencySet.collect {
            Dependency dependency ->

                formatDependency(
                        dependency
                )
        }
    }


    private static String formatDependency(
            Dependency dependency
    ) {

        String dependencyPath =
                "${dependency.group}:${dependency.name}"


        if (
                dependency.version != null &&
                        !dependency.version.isBlank()
        ) {

            dependencyPath +=
                    ":${dependency.version}"
        }


        return dependencyPath
    }


    // ========================================================
    // Directory
    // ========================================================

    private static void ensureParentDirectory(
            File outputFile
    ) {

        File parentDirectory =
                outputFile.parentFile


        if (parentDirectory.isDirectory()) {
            return
        }


        if (
                !parentDirectory.mkdirs() &&
                        !parentDirectory.isDirectory()
        ) {

            throw new GradleException(
                    """
Unable to create module dependency catalog directory:

${parentDirectory.absolutePath}
""".stripIndent()
            )
        }
    }
}