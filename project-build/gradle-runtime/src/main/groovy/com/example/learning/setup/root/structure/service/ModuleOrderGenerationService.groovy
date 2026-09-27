package com.example.learning.setup.root.structure.service

import org.gradle.api.GradleException
import org.gradle.api.logging.Logger

import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream


class ModuleOrderGenerationService {

    private final Logger logger

    private final ModuleOrderService moduleOrderService


    ModuleOrderGenerationService(
            Logger logger
    ) {

        this.logger =
                logger


        this.moduleOrderService =
                new ModuleOrderService(
                        logger
                )
    }


    void sync(
            File rootDirectory,
            String requestedPath
    ) {

        File moduleDirectory =
                new File(
                        rootDirectory,
                        'module'
                )


        if (!moduleDirectory.isDirectory()) {

            throw new GradleException(
                    """
Project module directory was not found:

${moduleDirectory.absolutePath}
""".stripIndent()
            )
        }


        String normalizedPath =
                normalizeRequestedPath(
                        requestedPath
                )


        if (normalizedPath.isBlank()) {

            syncExistingOrderFiles(
                    moduleDirectory
            )


            return
        }


        syncDirectory(
                moduleDirectory,
                resolveTargetDirectory(
                        moduleDirectory,
                        normalizedPath
                )
        )
    }


    private void syncExistingOrderFiles(
            File moduleDirectory
    ) {

        List<File> orderFiles =
                findExistingOrderFiles(
                        moduleDirectory
                )


        if (orderFiles.isEmpty()) {

            logger.lifecycle(
                    'ℹ️ [MODULE-ORDER] No existing {} files found. Use --path=<relative-parent-path> to create one.',
                    ModuleOrderService.FILE_NAME
            )


            return
        }


        orderFiles.each {
            File orderFile ->

            syncDirectory(
                    moduleDirectory,
                    orderFile.parentFile
            )
        }
    }


    private void syncDirectory(
            File moduleDirectory,
            File targetDirectory
    ) {

        List<File> children =
                moduleOrderService.listDirectChildren(
                        targetDirectory
                )


        File orderFile =
                new File(
                        targetDirectory,
                        ModuleOrderService.FILE_NAME
                )


        Map<String, Integer> existingOrders =
                orderFile.isFile()
                        ? moduleOrderService.readOrders(
                        orderFile
                )
                        : [:]


        Map<String, Integer> synchronizedOrders =
                new LinkedHashMap<>()


        children.each {
            File child ->

                synchronizedOrders[
                        child.name
                ] =
                        existingOrders.containsKey(
                                child.name
                        )
                                ? existingOrders[
                                child.name
                        ]
                                : null
        }


        String content =
                render(
                        synchronizedOrders
                )


        writeIfChanged(
                orderFile,
                content
        )


        String relativeParent =
                normalizePath(
                        moduleDirectory
                                .toPath()
                                .toAbsolutePath()
                                .normalize()
                                .relativize(
                                        targetDirectory
                                                .toPath()
                                                .toAbsolutePath()
                                                .normalize()
                                )
                                .toString()
                )


        logger.lifecycle(
                '🌳 [MODULE-ORDER] Synced {} direct child(ren) for module/{}.',
                synchronizedOrders.size(),
                relativeParent
        )
    }


    private static File resolveTargetDirectory(
            File moduleDirectory,
            String requestedPath
    ) {

        File targetDirectory =
                new File(
                        moduleDirectory,
                        requestedPath
                )


        Path modulePath =
                moduleDirectory
                        .toPath()
                        .toAbsolutePath()
                        .normalize()


        Path targetPath =
                targetDirectory
                        .toPath()
                        .toAbsolutePath()
                        .normalize()


        if (!targetPath.startsWith(modulePath)) {

            throw new GradleException(
                    """
Module order path must stay inside the module directory.

Requested path:
${requestedPath}
""".stripIndent()
            )
        }


        File normalizedTarget =
                targetPath.toFile()


        if (!normalizedTarget.isDirectory()) {

            throw new GradleException(
                    """
Module order parent directory was not found:

${normalizedTarget.absolutePath}
""".stripIndent()
            )
        }


        return normalizedTarget
    }


    private static List<File> findExistingOrderFiles(
            File moduleDirectory
    ) {

        List<File> result =
                []


        Stream<Path> paths =
                Files.walk(
                        moduleDirectory.toPath()
                )


        try {

            paths
                    .filter {
                        Path path ->

                        Files.isRegularFile(
                                path
                        ) &&
                                path.fileName
                                        .toString() ==
                                ModuleOrderService.FILE_NAME
                    }
                    .forEach {
                        Path path ->

                        result.add(
                                path.toFile()
                        )
                    }
        }
        finally {
            paths.close()
        }


        return result.sort {
            File left,
            File right ->

            left.absolutePath <=> right.absolutePath
        }
    }


    private static String normalizeRequestedPath(
            String requestedPath
    ) {

        String normalized =
                normalizePath(
                        requestedPath ?: ''
                )
                        .trim()
                        .replaceAll(
                                '^/+',
                                ''
                        )


        if (normalized == 'module') {
            return '.'
        }


        if (normalized.startsWith('module/')) {

            normalized =
                    normalized.substring(
                            'module/'.length()
                    )
        }


        return normalized
    }


    private static String render(
            Map<String, Integer> orders
    ) {

        List<Map.Entry<String, Integer>> entries =
                orders
                        .entrySet()
                        .toList()
                        .sort {
                            Map.Entry<String, Integer> left,
                            Map.Entry<String, Integer> right ->

                                Integer leftOrder =
                                        left.value


                                Integer rightOrder =
                                        right.value


                                if (
                                        leftOrder != null &&
                                                rightOrder != null
                                ) {

                                    int orderCompare =
                                            leftOrder <=> rightOrder


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


                                int ignoreCase =
                                        left.key.compareToIgnoreCase(
                                                right.key
                                        )


                                return ignoreCase != 0
                                        ? ignoreCase
                                        : left.key <=> right.key
                        }


        List<String> lines =
                [
                        'version: 1',
                        'children:'
                ]


        if (entries.isEmpty()) {
            return 'version: 1\nchildren: {}\n'
        }


        entries.each {
            Map.Entry<String, Integer> entry ->

                lines.add(
                        "  ${renderYamlKey(entry.key)}:"
                )


                lines.add(
                        entry.value == null
                                ? '    order:'
                                : "    order: ${entry.value}"
                )
        }


        return lines.join('\n') + '\n'
    }


    private static String renderYamlKey(
            String value
    ) {

        if (value ==~ /[A-Za-z0-9._-]+/) {
            return value
        }


        return "'${value.replace("'", "''")}'"
    }


    private static void writeIfChanged(
            File targetFile,
            String content
    ) {

        if (
                targetFile.isFile() &&
                        targetFile.getText(
                                'UTF-8'
                        ) == content
        ) {
            return
        }


        targetFile.setText(
                content,
                'UTF-8'
        )
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
