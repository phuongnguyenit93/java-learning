package com.example.learning.setup.root.structure.service

import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.yaml.snakeyaml.Yaml


class ModuleOrderService {

    static final String FILE_NAME =
            'module-order.yml'


    private static final int SCHEMA_VERSION =
            1


    private static final Set<String> EXCLUDED_DIRECTORIES =
            [
                    '.gradle',
                    '.idea',
                    'build',
                    'bin',
                    'out',
                    'target',
                    'node_modules',
                    '.git',
                    'src',
                    'readme',
                    'roadmap',
                    'video'
            ] as Set


    private final Logger logger


    ModuleOrderService(
            Logger logger
    ) {

        this.logger =
                logger
    }


    List<File> getOrderedChildren(
            File directory
    ) {

        List<File> children =
                listDirectChildren(
                        directory
                )


        File orderFile =
                new File(
                        directory,
                        FILE_NAME
                )


        if (!orderFile.isFile()) {
            return children
        }


        Map<String, Integer> orders =
                readOrders(
                        orderFile
                )


        Set<String> childNames =
                children
                        .collect {
                            File child ->

                            child.name
                        }
                        .toSet()


        orders
                .keySet()
                .findAll {
                    String configuredName ->

                    !childNames.contains(
                            configuredName
                    )
                }
                .sort()
                .each {
                    String staleName ->

                    logger.warn(
                            '⚠️ [MODULE-ORDER] Ignore stale child "{}" from {}.',
                            staleName,
                            orderFile.absolutePath
                    )
                }


        return children.sort {
            File left,
            File right ->

                Integer leftOrder =
                        orders[
                                left.name
                        ]


                Integer rightOrder =
                        orders[
                                right.name
                        ]


                if (
                        leftOrder != null &&
                                rightOrder != null
                ) {

                    int orderCompare =
                            leftOrder <=>
                                    rightOrder


                    if (orderCompare != 0) {
                        return orderCompare
                    }
                }
                else if (leftOrder != null) {
                    return -1
                }
                else if (rightOrder != null) {
                    return 1
                }


                return compareNames(
                        left.name,
                        right.name
                )
        }
    }


    List<File> listDirectChildren(
            File directory
    ) {

        File[] directories =
                directory.listFiles(
                        {
                            File file ->

                                file.isDirectory() &&
                                        !EXCLUDED_DIRECTORIES.contains(
                                                file.name
                                        )
                        } as FileFilter
                )


        if (directories == null) {

            throw new GradleException(
                    """
Unable to read directory:

${directory.absolutePath}
""".stripIndent()
            )
        }


        return directories
                .toList()
                .sort {
                    File left,
                    File right ->

                    compareNames(
                            left.name,
                            right.name
                    )
                }
    }


    Map<String, Integer> readOrders(
            File orderFile
    ) {

        if (!orderFile.isFile()) {
            return [:]
        }


        Object parsed


        try {

            parsed =
                    new Yaml()
                            .load(
                                    orderFile.getText(
                                            'UTF-8'
                                    )
                            )
        }
        catch (Exception exception) {

            throw new GradleException(
                    """
Unable to parse module order file:

${orderFile.absolutePath}

Reason:
${exception.message}
""".stripIndent(),
                    exception
            )
        }


        if (!(parsed instanceof Map)) {

            throw invalidFile(
                    orderFile,
                    'YAML root must be an object.'
            )
        }


        Map root =
                parsed as Map


        Object rawVersion =
                root.version


        if (
                !(rawVersion instanceof Number) ||
                        (rawVersion as Number).intValue() != SCHEMA_VERSION ||
                        (rawVersion as Number).doubleValue() != SCHEMA_VERSION
        ) {

            throw invalidFile(
                    orderFile,
                    "version must be integer ${SCHEMA_VERSION}."
            )
        }


        Object rawChildren =
                root.children


        if (!(rawChildren instanceof Map)) {

            throw invalidFile(
                    orderFile,
                    'children must be an object.'
            )
        }


        Map<String, Integer> result =
                new LinkedHashMap<>()


        (rawChildren as Map).each {
            Object rawName,
            Object rawEntry ->

                String name =
                        rawName
                                ?.toString()
                                ?.trim()


                if (
                        name == null ||
                                name.isBlank()
                ) {

                    throw invalidFile(
                            orderFile,
                            'children contains a blank child name.'
                    )
                }


                if (!(rawEntry instanceof Map)) {

                    throw invalidFile(
                            orderFile,
                            "children.${name} must be an object containing order."
                    )
                }


                Object rawOrder =
                        (rawEntry as Map).order


                result[
                        name
                ] =
                        parseOrder(
                                orderFile,
                                name,
                                rawOrder
                        )
        }


        return result
    }


    private static Integer parseOrder(
            File orderFile,
            String childName,
            Object rawOrder
    ) {

        if (rawOrder == null) {
            return null
        }


        if (!(rawOrder instanceof Number)) {

            throw invalidFile(
                    orderFile,
                    "children.${childName}.order must be an integer or null."
            )
        }


        Number number =
                rawOrder as Number


        long longValue =
                number.longValue()


        if (
                number.doubleValue() != longValue ||
                        longValue < 0 ||
                        longValue > Integer.MAX_VALUE
        ) {

            throw invalidFile(
                    orderFile,
                    "children.${childName}.order must be a non-negative integer."
            )
        }


        return (int) longValue
    }


    private static int compareNames(
            String left,
            String right
    ) {

        int ignoreCase =
                left.compareToIgnoreCase(
                        right
                )


        return ignoreCase != 0
                ? ignoreCase
                : left <=> right
    }


    private static GradleException invalidFile(
            File orderFile,
            String reason
    ) {

        return new GradleException(
                """
Invalid module order file:

${orderFile.absolutePath}

Reason:
${reason}
""".stripIndent()
        )
    }
}
