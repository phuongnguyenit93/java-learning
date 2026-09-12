package com.example.learning.module.task.plugin

import com.example.learning.generated.root.ProjectPluginEnum
import com.example.learning.module.task.service.TaskGradleGeneratorService
import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project


class TaskSetupPlugin
        implements Plugin<Project> {

    private static final String MODULE_TASK_LIST_RESOURCE =
            'task/module-task-list.json'

    private static final String TASK_EXTENSION_LIST_RESOURCE =
            'task/task-extension-list.json'


    @Override
    void apply(
            Project project
    ) {

        /*
         * Chỉ setup những project thực sự là module.
         *
         * Các project container / namespace như:
         *
         * :module
         * :module:microservice
         *
         * nếu không có gradle.properties thì bỏ qua.
         */
        if (!isModuleProject(project)) {

            project.logger.info(
                    '[TASK-DISPATCHER] Skip {} because it is not a module project.',
                    project.path
            )

            return
        }


        List<Map> moduleDefinitions =
                loadModuleDefinitions()


        Map<String, List<Map>> extensionDefinitions =
                loadExtensionDefinitions()


        /*
         * USE_TASK không còn được check ở đây.
         *
         * ProjectOrchestrationPlugin chịu trách nhiệm quyết định
         * TaskSetupPlugin có được apply hay không.
         *
         * Khi code chạy tới đây:
         *
         * USE_TASK đã được orchestration xác nhận là TRUE.
         */
        List<String> enabledTypes =
                resolveEnabledTypes(
                        project,
                        moduleDefinitions
                )


        /*
         * Apply các manual task plugin được enable
         * cho project hiện tại.
         */
        enabledTypes.each {
            String type ->

                applyDefinition(
                        project,
                        type
                )
        }


        /*
         * task.gradle được generate trực tiếp ngay trong
         * configuration phase khi plugin được apply.
         */
        generateTaskGradle(
                project,
                enabledTypes,
                extensionDefinitions
        )
    }


    private static void generateTaskGradle(
            Project project,
            Collection<String> enabledTypes,
            Map<String, List<Map>> extensionDefinitions
    ) {

        TaskGradleGeneratorService generatorService =
                new TaskGradleGeneratorService()


        String content =
                generatorService.render(
                        project.path,
                        enabledTypes,
                        extensionDefinitions
                )


        File targetFile =
                new File(
                        project.projectDir,
                        'task.gradle'
                )


        if (
                targetFile.parentFile != null &&
                        !targetFile.parentFile.exists()
        ) {

            targetFile.parentFile.mkdirs()
        }


        /*
         * Vì đây không còn là Gradle Task nên không có
         * native UP-TO-DATE check.
         *
         * Ta tự check rendered content để:
         *
         * - không ghi file nếu nội dung không đổi
         * - không thay đổi timestamp không cần thiết
         * - không làm IntelliJ nhận nhầm file đã thay đổi
         */
        if (
                targetFile.exists() &&
                        targetFile.getText(
                                'UTF-8'
                        ) == content
        ) {

            project.logger.info(
                    '[TASK-GRADLE] UP-TO-DATE: {}',
                    targetFile.absolutePath
            )

            return
        }


        targetFile.setText(
                content,
                'UTF-8'
        )


        project.logger.lifecycle(
                '📝 [TASK-GRADLE] Generated: {}',
                targetFile.absolutePath
        )
    }


    private static List<String> resolveEnabledTypes(
            Project project,
            List<Map> definitions
    ) {

        List<String> enabledTypes =
                []


        definitions.each {
            Map definition ->

                String type =
                        definition.type
                                ?.toString()
                                ?.trim()


                if (
                        type == null ||
                                type.isBlank()
                ) {

                    throw new GradleException(
                            'module-task-list.json contains a definition without type.'
                    )
                }


                String propCheck =
                        definition.propCheck
                                ?.toString()
                                ?.trim()


                /*
                 * propCheck rỗng:
                 *
                 * plugin/task type này luôn được enable
                 * khi TaskSetupPlugin đã được apply.
                 */
                boolean alwaysEnabled =
                        propCheck == null ||
                                propCheck.isBlank()


                if (
                        alwaysEnabled ||
                                isEnabled(
                                        project,
                                        propCheck
                                )
                ) {

                    enabledTypes.add(
                            type.toUpperCase(
                                    Locale.ROOT
                            )
                    )

                    return
                }


                project.logger.info(
                        '[TASK-DISPATCHER] Skip {} because {} != TRUE',
                        type,
                        propCheck
                )
        }


        return enabledTypes.unique()
    }


    private static void applyDefinition(
            Project project,
            String jsonType
    ) {

        String type =
                jsonType
                        ?.trim()


        if (
                type == null ||
                        type.isBlank()
        ) {

            throw new GradleException(
                    'Task type must not be blank.'
            )
        }


        /*
         * Ví dụ:
         *
         * readme
         *      ↓
         * README_TASK_PLUGIN
         *
         * intellij
         *      ↓
         * INTELLIJ_TASK_PLUGIN
         *
         * some-task
         *      ↓
         * SOME_TASK_TASK_PLUGIN
         */
        String enumName =
                "${type.replace('-', '_').toUpperCase(Locale.ROOT)}_TASK_PLUGIN"


        ProjectPluginEnum pluginDefinition


        try {

            pluginDefinition =
                    ProjectPluginEnum.valueOf(
                            enumName
                    )
        }
        catch (IllegalArgumentException ignored) {

            throw new GradleException(
                    """
Unable to resolve task plugin.

Task type:
${type}

Expected ProjectPlugin enum:
${enumName}
""".stripIndent()
            )
        }


        project.logger.lifecycle(
                '✅ [{}] Apply {}',
                type.toLowerCase(Locale.ROOT),
                pluginDefinition.id
        )


        project.pluginManager.apply(
                pluginDefinition.id
        )
    }


    private static List<Map> loadModuleDefinitions() {

        Object data =
                loadJsonResource(
                        MODULE_TASK_LIST_RESOURCE
                )


        if (!(data instanceof List)) {

            throw new GradleException(
                    "${MODULE_TASK_LIST_RESOURCE} must contain a JSON array."
            )
        }


        return data as List<Map>
    }


    private static Map<String, List<Map>> loadExtensionDefinitions() {

        Object data =
                loadJsonResource(
                        TASK_EXTENSION_LIST_RESOURCE
                )


        if (!(data instanceof Map)) {

            throw new GradleException(
                    "${TASK_EXTENSION_LIST_RESOURCE} must contain a JSON object."
            )
        }


        return data as Map<String, List<Map>>
    }


    private static Object loadJsonResource(
            String resourceName
    ) {

        InputStream stream =
                TaskSetupPlugin
                        .classLoader
                        .getResourceAsStream(
                                resourceName
                        )


        if (stream == null) {

            throw new GradleException(
                    "${resourceName} resource not found."
            )
        }


        stream.withCloseable {
            InputStream inputStream ->

                return new JsonSlurper()
                        .parse(
                                new InputStreamReader(
                                        inputStream,
                                        'UTF-8'
                                )
                        )
        }
    }


    /*
     * Helper này vẫn cần giữ.
     *
     * Nó không còn dùng cho USE_TASK,
     * nhưng vẫn được dùng cho propCheck:
     *
     * BUILD_README
     * BUILD_SWAGGER
     * BUILD_YML
     * ...
     */
    private static boolean isEnabled(
            Project project,
            String propertyName
    ) {

        return project
                .findProperty(
                        propertyName
                )
                ?.toString()
                ?.trim()
                ?.equalsIgnoreCase(
                        'TRUE'
                ) ?: false
    }


    private static boolean isModuleProject(
            Project project
    ) {

        File modulePropertiesFile =
                new File(
                        project.projectDir,
                        'gradle.properties'
                )


        return modulePropertiesFile.isFile()
    }
}