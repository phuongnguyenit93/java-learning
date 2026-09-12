package com.example.learning.setup.settings.preset.service

import com.example.learning.setup.settings.preset.model.ModuleSetupInfo
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger


class SettingInfoGeneratorService {

    private final Logger logger


    /*
     * Legacy/shared state vẫn được giữ lại vì có getter
     * và có thể đang được các component khác sử dụng.
     */
    private final List<HashMap> moduleProperties =
            []


    private final HashMap<String, Map<String, String>> moduleKeys =
            new LinkedHashMap<>()


    /*
     * Typed catalog source cho ModuleListEnum.
     *
     * Không lưu toàn bộ gradle.properties như module-info.json cũ.
     * Chỉ giữ metadata thực sự cần thiết.
     */
    private final Map<String, Map> moduleCatalog =
            new LinkedHashMap<>()


    /*
     * Typed catalog source cho DatabaseListEnum.
     */
    private final Map<String, Map> databaseInfo =
            new LinkedHashMap<>()


    SettingInfoGeneratorService(
            Logger logger
    ) {

        this.logger =
                logger
    }


    // ========================================================
    // Collect
    // ========================================================

    void collect(
            ModuleSetupInfo module,
            Map master,
            Map properties
    ) {

        String moduleName =
                resolveModuleName(
                        module,
                        master
                )


        collectModuleProperties(
                module,
                moduleName
        )


        collectModuleCatalog(
                module,
                moduleName,
                master,
                properties
        )


        collectDatabaseInfo(
                module,
                moduleName,
                master,
                properties
        )
    }


    // ========================================================
    // Module name
    // ========================================================

    private String resolveModuleName(
            ModuleSetupInfo module,
            Map master
    ) {

        String moduleName =
                stringValue(
                        master[
                                'SERVICE_NAME'
                        ]
                )


        if (moduleName.isBlank()) {

            moduleName =
                    module.directory.name


            logger.warn(
                    '⚠️ [GRADLE-RUNTIME] SERVICE_NAME is blank for module {}. ' +
                            'Fallback to directory name: {}',
                    module.modulePath,
                    moduleName
            )
        }


        validateEnumConstant(
                moduleName,
                'SERVICE_NAME'
        )


        return moduleName
    }


    // ========================================================
    // Legacy module properties
    // ========================================================

    private void collectModuleProperties(
            ModuleSetupInfo module,
            String moduleName
    ) {

        HashMap<String, String> propMap =
                new LinkedHashMap<>()


        module.gradleProperties.each {
            Object key,
            Object value ->

                propMap[
                        key.toString()
                ] =
                        value?.toString()
        }


        /*
         * Giữ compatibility với ext cũ.
         */
        propMap[
                'MODULE_PATH'
        ] =
                module.modulePath


        propMap[
                'RELATIVE_PATH'
        ] =
                module.relativePath


        /*
         * allModuleKeys trước đây chỉ lấy key
         * thực sự tồn tại trong gradle.properties.
         *
         * Không đưa MODULE_PATH / RELATIVE_PATH
         * vào đây để tránh thay đổi behavior.
         */
        module.gradleProperties
                .keySet()
                .each {
                    Object rawKey ->

                        String key =
                                rawKey.toString()


                        if (
                                !moduleKeys.containsKey(
                                        key
                                )
                        ) {

                            moduleKeys[
                                    key
                            ] =
                                    new LinkedHashMap<>()
                        }


                        moduleKeys[
                                key
                        ][
                                moduleName
                        ] =
                                module.relativePath
                }


        moduleProperties.add(
                propMap
        )
    }


    // ========================================================
    // Module catalog
    // ========================================================

    private void collectModuleCatalog(
            ModuleSetupInfo module,
            String moduleName,
            Map master,
            Map properties
    ) {

        String moduleType =
                stringValue(
                        master[
                                'MODULE_TYPE'
                        ]
                )


        String description =
                stringValue(
                        master[
                                'SERVICE_NAME_DESCRIBE'
                        ]
                )


        boolean isModuleDepend =
                booleanValue(
                        master[
                                'IS_MODULE_DEPEND'
                        ]
                )


        // ====================================================
        // MODULE_DEPEND_LIST
        // ====================================================

        Map moduleDependGroup =
                properties[
                        'MODULE_DEPEND'
                ] instanceof Map
                        ? properties[
                        'MODULE_DEPEND'
                ] as Map
                        : [:]


        List<String> moduleDependList =
                listValue(
                        moduleDependGroup[
                                'MODULE_DEPEND_LIST'
                        ]
                )


        moduleCatalog[
                moduleName
        ] =
                [
                        MODULE_PATH       :
                                module.modulePath
                                        ?: '',

                        RELATIVE_PATH     :
                                module.relativePath
                                        ?: '',

                        MODULE_TYPE       :
                                moduleType,

                        DESCRIPTION       :
                                description,

                        IS_MODULE_DEPEND  :
                                isModuleDepend,

                        MODULE_DEPEND_LIST:
                                moduleDependList
                ]
    }


    // ========================================================
    // Database catalog
    // ========================================================

    private void collectDatabaseInfo(
            ModuleSetupInfo module,
            String moduleName,
            Map master,
            Map properties
    ) {

        boolean databaseEnabled =
                booleanValue(
                        master[
                                'BUILD_DATABASE_MODULE'
                        ]
                )


        if (!databaseEnabled) {
            return
        }


        Object databaseModuleObject =
                properties[
                        'DATABASE_MODULE'
                ]


        if (!(databaseModuleObject instanceof Map)) {
            return
        }


        Map databaseModule =
                databaseModuleObject as Map


        String databaseName =
                stringValue(
                        databaseModule[
                                'DATABASE_MODULE_NAME'
                        ]
                )


        if (databaseName.isBlank()) {
            return
        }


        validateEnumConstant(
                databaseName,
                'DATABASE_MODULE_NAME'
        )


        String databaseType =
                stringValue(
                        databaseModule[
                                'DATABASE_MODULE_TYPE'
                        ]
                )


        if (
                databaseInfo.containsKey(
                        databaseName
                )
        ) {

            String existingModule =
                    databaseInfo[
                            databaseName
                    ].MODULE_NAME


            throw new GradleException(
                    """
Database '${databaseName}' already exists.

Duplicate modules:
 - ${existingModule}
 - ${moduleName}
""".stripIndent()
            )
        }


        databaseInfo[
                databaseName
        ] =
                [
                        MODULE_NAME  :
                                moduleName,

                        DATABASE_TYPE:
                                databaseType,

                        MODULE_PATH  :
                                module.modulePath
                ]
    }


    // ========================================================
    // Write generated files
    // ========================================================

    void writeGeneratedFiles(
            File rootDirectory
    ) {

        writeModuleEnum(
                rootDirectory
        )


        writeDatabaseEnum(
                rootDirectory
        )
    }


    // ========================================================
    // Existing public state
    // ========================================================

    List<HashMap> getModuleProperties() {

        return moduleProperties
    }


    HashMap<String, Map<String, String>> getModuleKeys() {

        return moduleKeys
    }


    // ========================================================
    // ModuleListEnum
    // ========================================================

    private void writeModuleEnum(
            File rootDirectory
    ) {

        File targetFile =
                new File(
                        rootDirectory,
                        'project-build/gradle-runtime/src/main/groovy/com/example/learning/generated/settings/ModuleListEnum.groovy'
                )


        writeTextIfChanged(
                targetFile,
                renderModuleEnum()
        )
    }


    private String renderModuleEnum() {

        List<Map.Entry<String, Map>> entries =
                moduleCatalog
                        .entrySet()
                        .toList()
                        .sort {
                            Map.Entry<String, Map> left,
                            Map.Entry<String, Map> right ->

                                left.key <=>
                                        right.key
                        }


        String enumConstants


        if (entries.isEmpty()) {

            enumConstants =
                    '    ;'
        }
        else {

            enumConstants =
                    entries
                            .collect {
                                Map.Entry<String, Map> entry ->

                                    String moduleName =
                                            entry.key


                                    validateEnumConstant(
                                            moduleName,
                                            'SERVICE_NAME'
                                    )


                                    Map info =
                                            entry.value


                                    String modulePath =
                                            escapeGroovyString(
                                                    info.MODULE_PATH
                                                            ?.toString()
                                                            ?: ''
                                            )


                                    String relativePath =
                                            escapeGroovyString(
                                                    info.RELATIVE_PATH
                                                            ?.toString()
                                                            ?: ''
                                            )


                                    String moduleType =
                                            escapeGroovyString(
                                                    info.MODULE_TYPE
                                                            ?.toString()
                                                            ?: ''
                                            )


                                    String description =
                                            escapeGroovyString(
                                                    info.DESCRIPTION
                                                            ?.toString()
                                                            ?: ''
                                            )


                                    boolean isModuleDepend =
                                            info.IS_MODULE_DEPEND ==
                                                    true


                                    String moduleDependList =
                                            renderStringList(
                                                    info.MODULE_DEPEND_LIST
                                            )


                                    return [
                                            "    ${moduleName}(",
                                            "            '${modulePath}',",
                                            "            '${relativePath}',",
                                            "            '${moduleType}',",
                                            "            '${description}',",
                                            "            ${isModuleDepend},",
                                            "            ${moduleDependList}",
                                            '    )'
                                    ].join(
                                            '\n'
                                    )
                            }
                            .join(
                                    ',\n\n'
                            ) +
                            ';'
        }


        return """\
package com.example.learning.generated.settings


enum ModuleListEnum {

${enumConstants}


    final String modulePath

    final String relativePath

    final String moduleType

    final String description

    final boolean isModuleDepend

    final List<String> moduleDependList


    ModuleListEnum(
            String modulePath,
            String relativePath,
            String moduleType,
            String description,
            boolean isModuleDepend,
            List<String> moduleDependList
    ) {

        this.modulePath =
                modulePath

        this.relativePath =
                relativePath

        this.moduleType =
                moduleType

        this.description =
                description

        this.isModuleDepend =
                isModuleDepend

        this.moduleDependList =
                (
                        moduleDependList
                                ?: []
                ).asImmutable()
    }


    static ModuleListEnum findByName(
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
            ModuleListEnum module ->

                module
                        .name()
                        .equalsIgnoreCase(
                                normalized
                        )
        }
    }
}
"""
    }


    // ========================================================
    // DatabaseListEnum
    // ========================================================

    private void writeDatabaseEnum(
            File rootDirectory
    ) {

        File targetFile =
                new File(
                        rootDirectory,
                        'project-build/gradle-runtime/src/main/groovy/com/example/learning/generated/settings/DatabaseListEnum.groovy'
                )


        writeTextIfChanged(
                targetFile,
                renderDatabaseEnum()
        )
    }


    private String renderDatabaseEnum() {

        List<Map.Entry<String, Map>> entries =
                databaseInfo
                        .entrySet()
                        .toList()
                        .sort {
                            Map.Entry<String, Map> left,
                            Map.Entry<String, Map> right ->

                                left.key <=>
                                        right.key
                        }


        String enumConstants


        if (entries.isEmpty()) {

            enumConstants =
                    '    ;'
        }
        else {

            enumConstants =
                    entries
                            .collect {
                                Map.Entry<String, Map> entry ->

                                    String databaseName =
                                            entry.key


                                    validateEnumConstant(
                                            databaseName,
                                            'DATABASE_MODULE_NAME'
                                    )


                                    Map info =
                                            entry.value


                                    String moduleName =
                                            escapeGroovyString(
                                                    info.MODULE_NAME
                                                            ?.toString()
                                                            ?: ''
                                            )


                                    String databaseType =
                                            escapeGroovyString(
                                                    info.DATABASE_TYPE
                                                            ?.toString()
                                                            ?: ''
                                            )


                                    String modulePath =
                                            escapeGroovyString(
                                                    info.MODULE_PATH
                                                            ?.toString()
                                                            ?: ''
                                            )


                                    return [
                                            "    ${databaseName}(",
                                            "            '${moduleName}',",
                                            "            '${databaseType}',",
                                            "            '${modulePath}'",
                                            '    )'
                                    ].join(
                                            '\n'
                                    )
                            }
                            .join(
                                    ',\n\n'
                            ) +
                            ';'
        }


        return """\
package com.example.learning.generated.settings


enum DatabaseListEnum {

${enumConstants}


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
"""
    }


    // ========================================================
    // Typed VALUE
    // ========================================================

    /**
     * Resolve VALUE dựa theo TYPE trong master.json /
     * properties.json.
     *
     * string:
     *     blank -> ''
     *
     * boolean:
     *     blank / invalid -> false
     *
     * list:
     *     blank -> []
     *     CSV -> trim + remove blank + unique
     */
    private static Object resolveTypedValue(
            Object definitionObject
    ) {

        if (!(definitionObject instanceof Map)) {
            return null
        }


        Map definition =
                definitionObject as Map


        String type =
                definition
                        .TYPE
                        ?.toString()
                        ?.trim()
                        ?.toLowerCase()


        Object rawValue =
                definition[
                        'VALUE'
                ]


        switch (type) {

            case 'boolean':

                if (rawValue instanceof Boolean) {
                    return rawValue
                }


                return rawValue
                        ?.toString()
                        ?.trim()
                        ?.equalsIgnoreCase(
                                'TRUE'
                        ) ?: false


            case 'list':

                return parseListValue(
                        rawValue
                )


            case 'string':

                return rawValue
                        ?.toString()
                        ?.trim()
                        ?: ''


            default:

                /*
                 * Unknown TYPE:
                 * giữ behavior an toàn như string.
                 */
                return rawValue
                        ?.toString()
                        ?.trim()
                        ?: ''
        }
    }


    private static String stringValue(
            Object definition
    ) {

        Object value =
                resolveTypedValue(
                        definition
                )


        if (value == null) {
            return ''
        }


        return value.toString()
    }


    private static boolean booleanValue(
            Object definition
    ) {

        Object value =
                resolveTypedValue(
                        definition
                )


        if (!(value instanceof Boolean)) {
            return false
        }


        return value as boolean
    }


    private static List<String> listValue(
            Object definition
    ) {

        Object value =
                resolveTypedValue(
                        definition
                )


        if (!(value instanceof Collection)) {
            return []
        }


        return value
                .collect {
                    Object item ->

                        item
                                ?.toString()
                                ?.trim()
                }
                .findAll {
                    String item ->

                        item != null &&
                                !item.isBlank()
                }
                .unique()
    }


    private static List<String> parseListValue(
            Object rawValue
    ) {

        if (rawValue == null) {
            return []
        }


        Collection sourceValues


        if (rawValue instanceof Collection) {

            sourceValues =
                    rawValue as Collection
        }
        else {

            String rawText =
                    rawValue
                            .toString()
                            .trim()


            if (rawText.isBlank()) {
                return []
            }


            sourceValues =
                    rawText.split(
                            ','
                    ).toList()
        }


        LinkedHashSet<String> values =
                new LinkedHashSet<>()


        sourceValues.each {
            Object rawItem ->

                String item =
                        rawItem
                                ?.toString()
                                ?.trim()


                if (
                        item != null &&
                                !item.isBlank()
                ) {

                    values.add(
                            item
                    )
                }
        }


        return values.toList()
    }


    // ========================================================
    // Render helpers
    // ========================================================

    private static String renderStringList(
            Object rawValue
    ) {

        if (!(rawValue instanceof Collection)) {
            return '[]'
        }


        List<String> values =
                (rawValue as Collection)
                        .collect {
                            Object item ->

                                item
                                        ?.toString()
                                        ?.trim()
                        }
                        .findAll {
                            String item ->

                                item != null &&
                                        !item.isBlank()
                        }
                        .unique()


        if (values.isEmpty()) {
            return '[]'
        }


        return '[' +
                values
                        .collect {
                            String value ->

                                "'${escapeGroovyString(value)}'"
                        }
                        .join(
                                ', '
                        ) +
                ']'
    }


    private static void validateEnumConstant(
            String value,
            String sourceName
    ) {

        if (
                value == null ||
                        !value.matches(
                                '[A-Za-z_][A-Za-z0-9_]*'
                        )
        ) {

            throw new GradleException(
                    """
Invalid enum constant '${value}'.

Source:
${sourceName}

Allowed format:
[A-Za-z_][A-Za-z0-9_]*

Examples:
 - MAPSTRUCT
 - DATABASE_MONGODB
 - GLOBAL_EXCEPTION_HANDLER
""".stripIndent()
            )
        }
    }


    private static String escapeGroovyString(
            String value
    ) {

        return value
                .replace(
                        '\\',
                        '\\\\'
                )
                .replace(
                        "'",
                        "\\'"
                )
    }


    // ========================================================
    // Generated file writer
    // ========================================================

    private void writeTextIfChanged(
            File targetFile,
            String content
    ) {

        String rendered =
                content.trim() + '\n'


        if (
                targetFile.exists() &&
                        !targetFile.isFile()
        ) {

            throw new GradleException(
                    """
Generated file path exists but is not a file:

${targetFile.absolutePath}
""".stripIndent()
            )
        }


        if (
                targetFile.isFile() &&
                        targetFile.getText(
                                'UTF-8'
                        ).trim() ==
                        rendered.trim()
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
Unable to create generated source directory:

${targetFile.parentFile.absolutePath}
""".stripIndent()
            )
        }


        targetFile.setText(
                rendered,
                'UTF-8'
        )


        logger.lifecycle(
                '📝 [GRADLE-RUNTIME] Generated: {}',
                targetFile.absolutePath
        )
    }
}