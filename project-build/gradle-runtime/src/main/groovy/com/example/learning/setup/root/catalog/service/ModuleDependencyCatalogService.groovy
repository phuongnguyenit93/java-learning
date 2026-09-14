package com.example.learning.setup.root.catalog.service

import com.example.learning.utils.ModuleProjectUtils
import com.example.learning.utils.ProjectPropertyUtils
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
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

        File outputFile =
                new File(
                        rootProject.projectDir,
                        'project-build/gradle-runtime/src/main/resources/module/module-depend.json'
                )


        ensureParentDirectory(
                outputFile
        )


        // ====================================================
        // Collect
        // ====================================================

        Map<String, Object> existingCatalog =
                readExistingCatalog(
                        outputFile
                )


        Map<String, Object> moduleDependList =
                collectModuleDependencies(
                        rootProject,
                        existingCatalog
                )

        // ====================================================
        // Output
        // ====================================================


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
            Project rootProject,
            Map<String, Object> existingCatalog
    ) {

        Set<Project> moduleProjects =
                ModuleProjectUtils.getModules(
                        rootProject
                )


        boolean allModulesEvaluated =
                moduleProjects.every {
                    Project moduleProject ->

                        moduleProject.state.executed
                }


        Map<String, Object> result =
                new LinkedHashMap<>()


        /*
         * Gradle configuration-on-demand có thể chỉ evaluate một phần project graph.
         * Nếu lúc đó rebuild catalog từ đầu, dependencies của các module chưa evaluate
         * sẽ bị nhìn như rỗng và entry cũ bị ghi mất.
         *
         * Full evaluation:
         *   rebuild catalog từ source of truth hiện tại.
         *
         * Partial evaluation:
         *   giữ catalog cũ và chỉ refresh những module thực sự đã evaluate.
         */
        if (!allModulesEvaluated) {

            result.putAll(
                    existingCatalog
            )


            logger.info(
                    '[MODULE-DEPENDENCY-CATALOG] Partial project evaluation detected. Preserve entries of non-evaluated modules.'
            )
        }


        moduleProjects.each {
            Project moduleProject ->

                if (!moduleProject.state.executed) {
                    return
                }


                String serviceName =
                        getServiceName(
                                moduleProject
                        )


                if (
                        !ProjectPropertyUtils.isEnabled(
                                moduleProject,
                                'IS_MODULE_DEPEND'
                        )
                ) {

                    result.remove(
                            serviceName
                    )

                    return
                }


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
                getServiceName(
                        moduleProject
                )


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


    private static String getServiceName(
            Project moduleProject
    ) {

        return ProjectPropertyUtils.getString(
                moduleProject,
                'SERVICE_NAME'
        ) ?: moduleProject.name
    }


    // ========================================================
    // Existing catalog
    // ========================================================

    private static Map<String, Object> readExistingCatalog(
            File outputFile
    ) {

        if (!outputFile.isFile()) {
            return new LinkedHashMap<>()
        }


        try {

            Object parsed =
                    new JsonSlurper().parseText(
                            outputFile.getText(
                                    'UTF-8'
                            )
                    )


            if (!(parsed instanceof Map)) {

                throw new GradleException(
                        "Module dependency catalog root must be a JSON object: ${outputFile.absolutePath}"
                )
            }


            return new LinkedHashMap<String, Object>(
                    (Map<String, Object>) parsed
            )
        }
        catch (GradleException exception) {
            throw exception
        }
        catch (Exception exception) {

            throw new GradleException(
                    "Unable to read existing module dependency catalog: ${outputFile.absolutePath}",
                    exception
            )
        }
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
