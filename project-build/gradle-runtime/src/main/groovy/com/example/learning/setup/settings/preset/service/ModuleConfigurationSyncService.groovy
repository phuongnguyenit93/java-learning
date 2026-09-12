package com.example.learning.setup.settings.preset.service

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger


class ModuleConfigurationSyncService {

    private static final String MASTER_RESOURCE =
            'automation/master.json'


    private static final String PROPERTIES_RESOURCE =
            'automation/properties.json'


    private final Logger logger


    /*
     * Canonical schemas.
     *
     * Được load đúng một lần từ gradle-runtime resources.
     */
    private final Map masterTemplate

    private final Map propertiesTemplate


    ModuleConfigurationSyncService(
            Logger logger
    ) {

        this.logger =
                logger


        this.masterTemplate =
                loadJsonResource(
                        MASTER_RESOURCE
                )


        this.propertiesTemplate =
                loadJsonResource(
                        PROPERTIES_RESOURCE
                )
    }


    // ========================================================
    // Sync module
    // ========================================================

    Map<String, Map> sync(
            File moduleDirectory
    ) {

        File targetMasterFile =
                new File(
                        moduleDirectory,
                        'master.json'
                )


        File targetPropertiesFile =
                new File(
                        moduleDirectory,
                        'properties.json'
                )


        // ====================================================
        // master.json
        // ====================================================

        Map master =
                syncMasterJson(
                        targetMasterFile
                )


        // ====================================================
        // properties.json
        //
        // properties phụ thuộc vào master vừa được sync.
        // ====================================================

        Map properties =
                syncPropertiesJson(
                        targetPropertiesFile,
                        master
                )


        return [
                master    : master,
                properties: properties
        ]
    }


    // ========================================================
    // Master
    // ========================================================

    private Map syncMasterJson(
            File targetMasterFile
    ) {

        Map targetJson =
                [:]


        if (
                targetMasterFile.exists() &&
                        targetMasterFile.length() > 0
        ) {

            try {

                Object parsed =
                        new JsonSlurper()
                                .parse(
                                        targetMasterFile
                                )


                if (parsed instanceof Map) {

                    targetJson =
                            parsed as Map
                }
                else {

                    logger.warn(
                            '⚠️ [GRADLE-RUNTIME] Invalid JSON root. Reset master file: {}',
                            targetMasterFile.absolutePath
                    )
                }
            }
            catch (Exception ignored) {

                /*
                 * malformed module master.json
                 * → reset từ canonical template.
                 */

                targetJson =
                        [:]


                logger.warn(
                        '⚠️ [GRADLE-RUNTIME] Invalid JSON. Reset master file: {}',
                        targetMasterFile.absolutePath
                )
            }
        }


        Map updatedJson =
                new LinkedHashMap()


        masterTemplate.each {
            Object key,
            Object rawTemplateData ->

                if (!(rawTemplateData instanceof Map)) {

                    updatedJson[
                            key
                    ] =
                            rawTemplateData

                    return
                }


                Map newItem =
                        new LinkedHashMap(
                                rawTemplateData as Map
                        )


                Object existingItem =
                        targetJson[
                                key
                        ]


                Object existingValue =
                        existingItem instanceof Map
                                ? existingItem.VALUE
                                : null


                /*
                 * VALUE thuộc module/human.
                 *
                 * DESCRIPTION / TYPE / GROUP /...
                 * thuộc canonical template.
                 */
                if (
                        existingValue != null &&
                                existingValue
                                        .toString() != ''
                ) {

                    newItem.VALUE =
                            existingValue
                }


                updatedJson[
                        key
                ] =
                        newItem
        }


        writeJsonIfChanged(
                targetMasterFile,
                updatedJson,
                '✅ [GRADLE-RUNTIME] Synced master'
        )


        return updatedJson
    }


    // ========================================================
    // Properties
    // ========================================================

    private Map syncPropertiesJson(
            File targetPropertiesFile,
            Map masterJson
    ) {

        Map currentModuleProperties =
                [:]


        if (
                targetPropertiesFile.exists() &&
                        targetPropertiesFile.length() > 0
        ) {

            try {

                Object parsed =
                        new JsonSlurper()
                                .parse(
                                        targetPropertiesFile
                                )


                if (parsed instanceof Map) {

                    currentModuleProperties =
                            parsed as Map
                }
                else {

                    logger.info(
                            '[GRADLE-RUNTIME] Invalid properties.json root. Regenerate: {}',
                            targetPropertiesFile.absolutePath
                    )
                }
            }
            catch (Exception ignored) {

                /*
                 * malformed properties.json
                 * → coi như rỗng
                 * → regenerate.
                 */

                currentModuleProperties =
                        [:]


                logger.info(
                        '[GRADLE-RUNTIME] Invalid properties.json. Regenerate: {}',
                        targetPropertiesFile.absolutePath
                )
            }
        }


        // ====================================================
        // Active groups
        // ====================================================

        List<String> activeGroups =
                masterJson
                        .values()
                        .findAll {
                            Object rawItem ->

                                if (!(rawItem instanceof Map)) {
                                    return false
                                }


                                Map item =
                                        rawItem as Map


                                return item.VALUE
                                        ?.toString()
                                        ?.equalsIgnoreCase(
                                                'TRUE'
                                        ) &&
                                        item.GROUP != null
                        }
                        .collect {
                            Map item ->

                                item.GROUP
                                        .toString()
                        }
                        .unique()


        // ====================================================
        // Generate properties
        // ====================================================

        Map newModuleProperties =
                new LinkedHashMap()


        activeGroups.each {
            String groupName ->

                if (
                        !propertiesTemplate.containsKey(
                                groupName
                        )
                ) {

                    return
                }


                Object rawGroup =
                        propertiesTemplate[
                                groupName
                        ]


                if (!(rawGroup instanceof Map)) {
                    return
                }


                Map generatedGroup =
                        new LinkedHashMap()


                (rawGroup as Map).each {
                    Object settingKey,
                    Object rawSettingData ->

                        if (!(rawSettingData instanceof Map)) {

                            generatedGroup[
                                    settingKey
                            ] =
                                    rawSettingData

                            return
                        }


                        Map settingData =
                                new LinkedHashMap(
                                        rawSettingData as Map
                                )


                        Object currentGroup =
                                currentModuleProperties[
                                        groupName
                                ]


                        Object currentSetting =
                                currentGroup instanceof Map
                                        ? currentGroup[
                                        settingKey
                                ]
                                        : null


                        Object existingValue =
                                currentSetting instanceof Map
                                        ? currentSetting.VALUE
                                        : null


                        /*
                         * VALUE thuộc module/human.
                         *
                         * Các metadata còn lại thuộc
                         * canonical properties template.
                         */
                        if (
                                existingValue != null &&
                                        existingValue
                                                .toString() != ''
                        ) {

                            settingData.VALUE =
                                    existingValue
                        }


                        generatedGroup[
                                settingKey
                        ] =
                                settingData
                }


                newModuleProperties[
                        groupName
                ] =
                        generatedGroup
        }


        writeJsonIfChanged(
                targetPropertiesFile,
                newModuleProperties,
                '⚙️ [GRADLE-RUNTIME] Generated properties'
        )


        return newModuleProperties
    }


    // ========================================================
    // Canonical resource loader
    // ========================================================

    private static Map loadJsonResource(
            String resourcePath
    ) {

        InputStream inputStream =
                ModuleConfigurationSyncService
                        .class
                        .classLoader
                        .getResourceAsStream(
                                resourcePath
                        )


        if (inputStream == null) {

            throw new GradleException(
                    """
Unable to find gradle-runtime automation resource:

${resourcePath}
""".stripIndent()
            )
        }


        try {

            Object parsed =
                    new JsonSlurper()
                            .parse(
                                    inputStream
                            )


            if (!(parsed instanceof Map)) {

                throw new GradleException(
                        """
Automation resource root must be a JSON object:

${resourcePath}
""".stripIndent()
                )
            }


            return new LinkedHashMap(
                    parsed as Map
            )
        }
        catch (GradleException exception) {

            throw exception
        }
        catch (Exception exception) {

            throw new GradleException(
                    """
Unable to parse gradle-runtime automation resource:

${resourcePath}
""".stripIndent(),
                    exception
            )
        }
        finally {

            inputStream.close()
        }
    }


    // ========================================================
    // Writer
    // ========================================================

    private void writeJsonIfChanged(
            File targetFile,
            Object content,
            String message
    ) {

        String newContent =
                JsonOutput.prettyPrint(
                        JsonOutput.toJson(
                                content
                        )
                )


        if (
                targetFile.exists() &&
                        !targetFile.isFile()
        ) {

            throw new GradleException(
                    """
Configuration path exists but is not a file:

${targetFile.absolutePath}
""".stripIndent()
            )
        }


        if (
                targetFile.isFile() &&
                        targetFile.getText(
                                'UTF-8'
                        ).trim() ==
                        newContent.trim()
        ) {

            logger.info(
                    '[GRADLE-RUNTIME] UP-TO-DATE: {}',
                    targetFile.absolutePath
            )

            return
        }


        if (
                targetFile.parentFile != null &&
                        !targetFile.parentFile.exists() &&
                        !targetFile.parentFile.mkdirs()
        ) {

            throw new GradleException(
                    """
Unable to create configuration directory:

${targetFile.parentFile.absolutePath}
""".stripIndent()
            )
        }


        targetFile.setText(
                newContent,
                'UTF-8'
        )


        logger.lifecycle(
                '{}: {}',
                message,
                targetFile.absolutePath
        )
    }
}