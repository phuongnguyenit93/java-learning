package com.example.learning.module.task.service

class TaskGradleGeneratorService {

    String render(
            String modulePath,
            Collection<String> enabledTypes,
            Map<String, List<Map>> extensionDefinitions
    ) {

        StringBuilder content =
                new StringBuilder()


        appendHeader(
                content,
                modulePath
        )


        int extensionCount = 0


        enabledTypes.each {
            String rawType ->

                String type =
                        rawType
                                .trim()
                                .toUpperCase(
                                        Locale.ROOT
                                )


                List<Map> extensions =
                        extensionDefinitions[
                                type
                        ] ?: []


                if (extensions.isEmpty()) {
                    return
                }


                appendTypeHeader(
                        content,
                        type
                )


                extensions.each {
                    Map extension ->

                        appendExtension(
                                content,
                                extension
                        )


                        extensionCount++
                }
        }


        if (extensionCount == 0) {

            content.append(
                    '// No extension DSL is enabled for this module.\n'
            )
        }


        return content
                .toString()
                .trim() +
                '\n'
    }


    private static void appendHeader(
            StringBuilder content,
            String modulePath
    ) {

        content.append(
                """/*
 * ============================================================
 * AUTO-GENERATED TASK DSL REFERENCE
 * ============================================================
 *
 * Module:
 * ${modulePath}
 *
 * Generated from:
 *
 * - module-task-list.json
 * - task-extension-list.json
 *
 * This file is rebuilt automatically.
 * Do not store manual configuration here.
 *
 * Copy the DSL blocks you need into build.gradle.
 * ============================================================
 */

"""
        )
    }


    private static void appendTypeHeader(
            StringBuilder content,
            String type
    ) {

        content.append(
                """// ============================================================
// ${type}
// ============================================================

"""
        )
    }


    private static void appendExtension(
            StringBuilder content,
            Map extension
    ) {

        String extensionName =
                extension.name
                        ?.toString()
                        ?.trim()


        if (
                extensionName == null ||
                        extensionName.isBlank()
        ) {

            return
        }


        String extensionClass =
                extension.extension
                        ?.toString()
                        ?.trim()


        String extensionPath =
                extension.path
                        ?.toString()
                        ?.trim()


        content.append(
                "// Extension: ${extensionClass ?: 'Unknown'}\n"
        )


        content.append(
                "// Path: ${extensionPath ?: 'Unknown'}\n"
        )


        content.append(
                "${extensionName} {\n"
        )


        Map fields =
                extension.fields instanceof Map
                        ? extension.fields as Map
                        : [:]


        fields.each {
            Object fieldName,
            Object fieldType ->

                String name =
                        fieldName.toString()


                String type =
                        fieldType
                                ?.toString()
                                ?.trim() ?: 'Object'


                content.append(
                        "    ${name} = ${defaultValueFor(type)} // ${type}\n"
                )
        }


        content.append(
                '}\n\n'
        )
    }


    private static String defaultValueFor(
            String type
    ) {

        String normalizedType =
                type
                        .replaceAll(
                                /\s+/,
                                ''
                        )


        if (
                normalizedType.startsWith(
                        'List<'
                ) ||
                        normalizedType == 'List' ||
                        normalizedType.startsWith(
                                'Set<'
                        ) ||
                        normalizedType == 'Set'
        ) {

            return '[]'
        }


        if (
                normalizedType.startsWith(
                        'Map<'
                ) ||
                        normalizedType == 'Map'
        ) {

            return '[:]'
        }


        if (
                normalizedType in [
                        'String',
                        'CharSequence'
                ]
        ) {

            return "''"
        }


        if (
                normalizedType in [
                        'Boolean',
                        'boolean'
                ]
        ) {

            return 'false'
        }


        if (
                normalizedType in [
                        'Integer',
                        'int',
                        'Long',
                        'long',
                        'Short',
                        'short',
                        'Byte',
                        'byte'
                ]
        ) {

            return '0'
        }


        if (
                normalizedType in [
                        'Double',
                        'double',
                        'Float',
                        'float',
                        'BigDecimal'
                ]
        ) {

            return '0.0'
        }


        return 'null'
    }
}
