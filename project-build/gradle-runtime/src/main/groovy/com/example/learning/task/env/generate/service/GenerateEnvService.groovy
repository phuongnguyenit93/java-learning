package com.example.learning.task.env.generate.service

import com.example.learning.utils.GradleBuildUtils
import com.example.learning.utils.ModuleProjectUtils
import com.example.learning.utils.ProjectPropertyUtils
import org.gradle.api.Project

import java.util.regex.Pattern


class GenerateEnvService {

    private static final Pattern ENV_PATTERN =
            Pattern.compile(
                    '\\$\\{\\s*([A-Z0-9_]+)(?::.*)?\\s*\\}'
            )


    private static final List<String> GRADLE_OVERRIDE_PROPERTIES =
            [
                    'BUILD_SWAGGER',
                    'MODULE_LANGUAGE'
            ]


    private final Project project


    GenerateEnvService(
            Project project
    ) {

        this.project =
                project
    }


    void generate(
            String serviceName,
            File applicationFile,
            File dockerComposeFile,
            File envFile,
            File envExampleFile
    ) {

        def logger =
                project.logger


        logger.lifecycle(
                '=================================================='
        )

        logger.lifecycle(
                '🔍 GENERATE / UPDATE ENV FROM EFFECTIVE APPLICATION.YML'
        )

        logger.lifecycle(
                '=================================================='
        )


        // ====================================================
        // 1. Find module
        // ====================================================

        Project baseProject =
                ModuleProjectUtils.findByServiceName(
                        project,
                        serviceName
                )


        logger.lifecycle(
                '🌐 [ENV-SYNC] Đang đồng bộ hóa môi trường cho: {}',
                serviceName
        )


        logger.lifecycle(
                '   📥 Application config: {}',
                applicationFile.absolutePath
        )


        // ====================================================
        // 2. Scan master ENV keys
        // ====================================================

        List<File> sourceFiles =
                [
                        applicationFile
                ]


        if (dockerComposeFile != null) {

            sourceFiles.add(
                    dockerComposeFile
            )
        }


        Set<String> masterKeys =
                collectMasterKeys(
                        sourceFiles
                )


        logger.lifecycle(
                '   🧩 Tìm thấy {} biến môi trường từ merged YAML/Docker.',
                masterKeys.size()
        )


        // ====================================================
        // 3. Resolve automatic override values
        // ====================================================

        Map<String, String> overrideProperties =
                new LinkedHashMap<>(
                        resolve(
                                baseProject
                        )
                )


        addGradleOverrides(
                overrideProperties
        )


        logger.lifecycle(
                '   🧩 Master keys: {}, override candidates: {}',
                masterKeys.size(),
                overrideProperties.size()
        )


        // ====================================================
        // 4. Generate .env
        // ====================================================

        generateEnvFile(
                envFile,
                serviceName,
                masterKeys,
                overrideProperties
        )


        // ====================================================
        // 5. Generate .env.example
        // ====================================================

        generateExampleFile(
                envExampleFile,
                masterKeys
        )


        logger.lifecycle(
                '✅ Done! File .env và .env.example đã được cập nhật.'
        )

        logger.lifecycle(
                '--------------------------------------------------'
        )
    }


    // ========================================================
    // Scan ENV keys
    // ========================================================

    private static Set<String> collectMasterKeys(
            Collection<File> files
    ) {

        Set<String> keys =
                new LinkedHashSet<>()


        files.each {
            File file ->

                if (
                        file == null ||
                                !file.exists()
                ) {

                    return
                }


                String content =
                        file.getText(
                                'UTF-8'
                        )


                def matcher =
                        ENV_PATTERN.matcher(
                                content
                        )


                while (matcher.find()) {

                    keys.add(
                            matcher.group(
                                    1
                            )
                    )
                }
        }


        return keys
    }


    // ========================================================
    // Gradle overrides
    // ========================================================

    private void addGradleOverrides(
            Map<String, String> overrides
    ) {

        GRADLE_OVERRIDE_PROPERTIES.each {
            String propertyName ->

                if (
                        overrides.containsKey(
                                propertyName
                        )
                ) {

                    return
                }


                String value


                if (propertyName == 'MODULE_LANGUAGE') {

                    value =
                            ProjectPropertyUtils.getStringList(
                                    project,
                                    propertyName
                            ).join(',')
                }
                else {

                    value =
                            ProjectPropertyUtils.getString(
                                    project,
                                    propertyName
                            )
                }


                if (
                        value == null ||
                                value.isBlank()
                ) {
                    return
                }


                overrides[
                        propertyName
                ] = value
        }
    }


    // ========================================================
    // .env
    // ========================================================

    private void generateEnvFile(
            File targetFile,
            String serviceName,
            Set<String> masterKeys,
            Map<String, String> overrideProperties
    ) {

        Map<String, String> currentContent =
                readCurrentEnv(
                        targetFile
                )


        int updatedCount =
                0


        List<String> newLines =
                [
                        '# ----------------------------------------------------------------',
                        "# Auto-generated for: ${serviceName}",
                        "# Sync Date: ${new Date().format('yyyy-MM-dd HH:mm:ss')}",
                        '# ----------------------------------------------------------------'
                ]


        masterKeys
                .sort()
                .each {
                    String key ->

                        String finalValue =
                                ''


                        if (
                                overrideProperties.containsKey(
                                        key
                                )
                        ) {

                            finalValue =
                                    overrideProperties[
                                            key
                                    ] ?: ''

                            updatedCount++
                        }
                        else if (
                                currentContent.containsKey(
                                        key
                                )
                        ) {

                            finalValue =
                                    currentContent[
                                            key
                                    ] ?: ''
                        }


                        newLines.add(
                                "${key}=${finalValue}"
                        )
                }


        targetFile.setText(
                newLines.join('\n') + '\n',
                'UTF-8'
        )


        project.logger.lifecycle(
                '   📝 Generated: {} ({} keys, {} auto-filled)',
                targetFile.name,
                masterKeys.size(),
                updatedCount
        )
    }


    private static Map<String, String> readCurrentEnv(
            File targetFile
    ) {

        Map<String, String> result =
                new LinkedHashMap<>()


        if (!targetFile.exists()) {
            return result
        }


        targetFile.eachLine(
                'UTF-8'
        ) {
            String line ->

                String trimmed =
                        line.trim()


                if (
                        trimmed.isEmpty() ||
                                trimmed.startsWith('#') ||
                                !line.contains('=')
                ) {

                    return
                }


                String[] parts =
                        line.split(
                                '=',
                                2
                        )


                String key =
                        parts[
                                0
                        ].trim()


                String value =
                        parts.length > 1
                                ? parts[
                                1
                        ].trim()
                                : ''


                result[
                        key
                ] = value
        }


        return result
    }


    // ========================================================
    // .env.example
    // ========================================================

    private void generateExampleFile(
            File exampleFile,
            Set<String> masterKeys
    ) {

        List<String> lines =
                [
                        '# Sample values - Please fill manually',

                        *masterKeys
                                .sort()
                                .collect {
                                    String key ->

                                        String exampleValue =
                                                key == 'MODULE_LANGUAGE'
                                                        ? ProjectPropertyUtils
                                                        .getStringList(
                                                                project,
                                                                'MODULE_LANGUAGE'
                                                        )
                                                        .join(',')
                                                        : 'your-value-here'


                                        "${key}=${exampleValue}"
                                }
                ]


        exampleFile.setText(
                lines.join('\n') + '\n',
                'UTF-8'
        )
    }


    // ========================================================
    // Resolve module paths
    // ========================================================

    private Map<String, String> resolve(
            Project baseProject
    ) {

        Project rootProject =
                project.rootProject


        def logger =
                rootProject.logger


        logger.lifecycle(
                '=================================================='
        )

        logger.lifecycle(
                '🔍 TÌM KIẾM VÀ THIẾT LẬP CÁC PATH CỦA MODULE ĐỂ DÙNG CHO DOCKER-COMPOSE'
        )

        logger.lifecycle(
                '=================================================='
        )


        // ====================================================
        // 1. Current service
        // ====================================================

        String moduleName =
                ProjectPropertyUtils.getString(
                        baseProject,
                        'SERVICE_NAME'
                )


        // ====================================================
        // 2. Dependency services
        // ====================================================

        List<String> dependencyServices =
                ProjectPropertyUtils.getCsvList(
                        baseProject,
                        'BUILD_ENV_PATH_MODULE_DEPEND'
                )


        LinkedHashSet<String> requiredServices =
                new LinkedHashSet<>(
                        dependencyServices
                )


        if (
                moduleName != null &&
                        !moduleName.isBlank()
        ) {

            requiredServices.add(
                    moduleName
            )
        }


        logger.lifecycle(
                '🔍 [ENV-PATH] Thu thập path cho: {}',
                moduleName
        )


        // ====================================================
        // 3. Find projects
        // ====================================================

        List<Project> projects =
                ModuleProjectUtils.findByServiceNameList(
                        project,
                        requiredServices
                )


        Map<String, String> result =
                new LinkedHashMap<>()


        // ====================================================
        // 4. Root
        // ====================================================

        String rootPath =
                GradleBuildUtils.normalizePath(
                        rootProject
                                .rootDir
                                .absolutePath
                )


        result[
                'PROJECT_ROOT'
        ] = rootPath


        logger.lifecycle(
                '   + Setting: PROJECT_ROOT -> {}',
                rootPath
        )


        // ====================================================
        // 5. Module paths
        // ====================================================

        projects.each {
            Project service ->

                String currentServiceName =
                        ProjectPropertyUtils.getString(
                                service,
                                'SERVICE_NAME'
                        )


                if (
                        currentServiceName == null ||
                                currentServiceName.isBlank()
                ) {

                    return
                }


                String modulePath =
                        service.path


                String absolutePath =
                        GradleBuildUtils.normalizePath(
                                service
                                        .projectDir
                                        .absolutePath
                        )


                String relativePath =
                        GradleBuildUtils.normalizePath(
                                rootProject
                                        .rootDir
                                        .toPath()
                                        .relativize(
                                                service
                                                        .projectDir
                                                        .toPath()
                                        )
                                        .toString()
                        )


                result[
                        "${currentServiceName}_ABSOLUTE_PATH"
                ] = absolutePath


                result[
                        "${currentServiceName}_RELATIVE_PATH"
                ] = relativePath


                result[
                        "${currentServiceName}_MODULE_PATH"
                ] = modulePath


                logger.lifecycle(
                        '   + Found: {} -> {}',
                        currentServiceName,
                        relativePath
                )
        }


        logger.lifecycle(
                '✨ [COMPLETED] Đã tạo {} ENV path variables cho {}.',
                result.size(),
                moduleName
        )


        logger.lifecycle(
                '--------------------------------------------------'
        )


        return result
    }
}
