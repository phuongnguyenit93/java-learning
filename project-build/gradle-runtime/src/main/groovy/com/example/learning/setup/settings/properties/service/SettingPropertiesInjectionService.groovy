package com.example.learning.setup.settings.properties.service

import groovy.json.JsonSlurper
import org.gradle.api.Project
import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.Logger


class SettingPropertiesInjectionService {

    private final Logger logger


    SettingPropertiesInjectionService(
            Logger logger
    ) {

        this.logger =
                logger
    }


    /**
     * Inject properties cho toàn bộ subproject sau khi
     * Gradle đã load xong project hierarchy.
     *
     * Thứ tự:
     *
     * 1. master.json
     * 2. properties.json
     *
     * Vì properties.json được xử lý sau nên nếu có cùng key,
     * VALUE từ properties.json sẽ thắng.
     */
    void injectAll(
            Gradle gradle
    ) {

        List<Project> projects =
                gradle
                        .rootProject
                        .subprojects
                        .toList()
                        .sort {
                            Project left,
                            Project right ->

                                left.path <=>
                                        right.path
                        }


        projects.each {
            Project project ->

                injectProject(
                        project
                )
        }
    }


    /**
     * Inject master.json + properties.json của một project
     * vào ExtraPropertiesExtension.
     */
    void injectProject(
            Project project
    ) {

        List<File> filesToInject =
                [
                        new File(
                                project.projectDir,
                                'master.json'
                        ),

                        new File(
                                project.projectDir,
                                'properties.json'
                        )
                ]


        int injectedCount =
                0


        filesToInject.each {
            File jsonFile ->

                injectedCount +=
                        injectFile(
                                project,
                                jsonFile
                        )
        }


        if (injectedCount > 0) {

            logger.info(
                    '[PROJECT-PROPERTIES] Injected {} properties into {}',
                    injectedCount,
                    project.path
            )
        }
    }


    /**
     * Đọc một JSON file và inject toàn bộ VALUE.
     *
     * Return số lượng property đã inject.
     */
    private int injectFile(
            Project project,
            File jsonFile
    ) {

        /*
         * Các project container như:
         *
         * :module
         * :module:microservice
         *
         * có thể tồn tại trong Gradle hierarchy nhưng
         * không có master.json/properties.json.
         *
         * Khi đó skip.
         */
        if (
                !jsonFile.isFile() ||
                        jsonFile.length() == 0
        ) {

            return 0
        }


        try {

            Object parsed =
                    new JsonSlurper()
                            .parse(
                                    jsonFile
                            )


            if (!(parsed instanceof Map)) {

                logger.error(
                        '[PROJECT-PROPERTIES] JSON root must be an object: {}',
                        jsonFile.absolutePath
                )

                return 0
            }


            return injectJson(
                    project,
                    parsed as Map,
                    jsonFile
            )
        }
        catch (Exception exception) {

            /*
             * Giữ behavior cũ:
             *
             * một file JSON lỗi không làm toàn bộ
             * Settings phase chết.
             */
            logger.error(
                    '❌ [PROJECT-PROPERTIES] Failed to load {} for {}: {}',
                    jsonFile.name,
                    project.path,
                    exception.message
            )


            return 0
        }
    }


    /**
     * Hỗ trợ cả hai structure hiện tại.
     *
     * Flat:
     *
     * "SERVICE_NAME": {
     *     "VALUE": "account-service"
     * }
     *
     *
     * Nested:
     *
     * "DATABASE_MODULE": {
     *     "DATABASE_MODULE_NAME": {
     *         "VALUE": "account_db"
     *     }
     * }
     */
    private int injectJson(
            Project project,
            Map json,
            File sourceFile
    ) {

        int injectedCount =
                0


        json.each {
            Object rawKey,
            Object data ->

                String key =
                        rawKey.toString()


                // ==========================================
                // Flat structure
                // ==========================================

                if (
                        data instanceof Map &&
                                data.containsKey(
                                        'VALUE'
                                )
                ) {

                    setProperty(
                            project,
                            key,
                            data.VALUE,
                            sourceFile
                    )


                    injectedCount++

                    return
                }


                // ==========================================
                // Nested structure
                // ==========================================

                if (!(data instanceof Map)) {
                    return
                }


                data.each {
                    Object rawSubKey,
                    Object subData ->

                        if (
                                !(subData instanceof Map) ||
                                        !subData.containsKey(
                                                'VALUE'
                                        )
                        ) {

                            return
                        }


                        String subKey =
                                rawSubKey.toString()


                        setProperty(
                                project,
                                subKey,
                                subData.VALUE,
                                sourceFile
                        )


                        injectedCount++
                }
        }


        return injectedCount
    }


    private void setProperty(
            Project project,
            String propertyName,
            Object value,
            File sourceFile
    ) {

        project
                .extensions
                .extraProperties
                .set(
                        propertyName,
                        value
                )


        logger.debug(
                '[PROJECT-PROPERTIES] {} -> {} = {} ({})',
                project.path,
                propertyName,
                value,
                sourceFile.name
        )
    }
}