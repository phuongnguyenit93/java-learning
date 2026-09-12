package com.example.learning.setup.root.cleanup.service

import com.example.learning.generated.settings.ModuleListEnum
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.stream.Stream


class ProjectCleanupService {

    private static final Set<String> EXCLUDED_DIRECTORIES =
            [
                    '.gradle',
                    'build',
                    '.git',
                    '.idea'
            ] as Set


    private final Logger logger


    ProjectCleanupService(
            Logger logger
    ) {

        this.logger =
                logger
    }


    // ========================================================
    // Cleanup files
    // ========================================================

    int cleanupFiles(
            File rootDirectory,
            String fileName,
            String projectName
    ) {

        String normalizedFileName =
                validateSimpleName(
                        fileName,
                        'FileName'
                )


        File scopeDirectory =
                resolveScopeDirectory(
                        rootDirectory,
                        projectName
                )


        List<Path> files =
                findFiles(
                        scopeDirectory,
                        normalizedFileName
                )


        if (files.isEmpty()) {

            logger.lifecycle(
                    "✨ [CLEANUP] No file named '{}' found in: {}",
                    normalizedFileName,
                    scopeDirectory.absolutePath
            )

            return 0
        }


        int deletedCount =
                0


        files.each {
            Path file ->

                deletePath(
                        file,
                        'file'
                )


                deletedCount++
        }


        return deletedCount
    }


    // ========================================================
    // Cleanup empty folders
    // ========================================================

    int cleanupEmptyFolders(
            File rootDirectory,
            String folderName,
            String projectName
    ) {

        String normalizedFolderName =
                validateSimpleName(
                        folderName,
                        'FolderName'
                )


        File scopeDirectory =
                resolveScopeDirectory(
                        rootDirectory,
                        projectName
                )


        List<Path> directories =
                findEmptyDirectories(
                        scopeDirectory,
                        normalizedFolderName
                )


        if (directories.isEmpty()) {

            logger.lifecycle(
                    "✨ [CLEANUP] No empty folder named '{}' found in: {}",
                    normalizedFolderName,
                    scopeDirectory.absolutePath
            )

            return 0
        }


        int deletedCount =
                0


        directories.each {
            Path directory ->

                /*
                 * Chỉ các directory đã được xác định là rỗng
                 * tại thời điểm scan mới xuất hiện trong list.
                 *
                 * Không thực hiện recursive / bottom-up cleanup.
                 */
                deletePath(
                        directory,
                        'folder'
                )


                deletedCount++
        }


        return deletedCount
    }


    // ========================================================
    // Scope
    // ========================================================

    private File resolveScopeDirectory(
            File rootDirectory,
            String projectName
    ) {

        File normalizedRootDirectory =
                rootDirectory
                        .toPath()
                        .toAbsolutePath()
                        .normalize()
                        .toFile()


        String normalizedProjectName =
                projectName
                        ?.trim()


        // ====================================================
        // Root scope
        // ====================================================

        if (
                normalizedProjectName == null ||
                        normalizedProjectName.isBlank()
        ) {

            logger.warn(
                    """
⚠️ [CLEANUP] ProjectName was not provided.

Cleanup scope:
${normalizedRootDirectory.absolutePath}

The entire root project will be scanned.

To limit cleanup to one module, use:

-PProjectName=<SERVICE_NAME>
""".stripIndent()
            )


            return normalizedRootDirectory
        }


        // ====================================================
        // Module scope
        // ====================================================

        ModuleListEnum module =
                ModuleListEnum
                        .values()
                        .find {
                            ModuleListEnum value ->

                                /*
                                 * Exact + case-sensitive.
                                 */
                                value.name() ==
                                        normalizedProjectName
                        }


        if (module == null) {

            throw new GradleException(
                    """
Unknown ProjectName:

${normalizedProjectName}

ProjectName must exactly match a SERVICE_NAME
available in ModuleListEnum.

Example:

-PProjectName=KAFKA
-PProjectName=ORDER_SERVICE
-PProjectName=DATABASE_MONGODB

Matching is case-sensitive.
""".stripIndent()
            )
        }


        File scopeDirectory =
                new File(
                        normalizedRootDirectory,
                        module.relativePath
                )
                        .toPath()
                        .toAbsolutePath()
                        .normalize()
                        .toFile()


        if (!scopeDirectory.isDirectory()) {

            throw new GradleException(
                    """
Cleanup project directory does not exist:

ProjectName:
${normalizedProjectName}

Relative path:
${module.relativePath}

Resolved path:
${scopeDirectory.absolutePath}
""".stripIndent()
            )
        }


        logger.lifecycle(
                '[CLEANUP] Scope: {} -> {}',
                normalizedProjectName,
                scopeDirectory.absolutePath
        )


        return scopeDirectory
    }


    // ========================================================
    // Find files
    // ========================================================

    private List<Path> findFiles(
            File scopeDirectory,
            String targetName
    ) {

        Path scopePath =
                scopeDirectory
                        .toPath()
                        .toAbsolutePath()
                        .normalize()


        List<Path> result =
                []


        try {

            Files.walkFileTree(
                    scopePath,
                    new SimpleFileVisitor<Path>() {

                        @Override
                        FileVisitResult preVisitDirectory(
                                Path directory,
                                BasicFileAttributes attributes
                        ) {

                            if (
                                    directory != scopePath &&
                                            isExcludedDirectory(
                                                    directory
                                            )
                            ) {

                                return FileVisitResult.SKIP_SUBTREE
                            }


                            return FileVisitResult.CONTINUE
                        }


                        @Override
                        FileVisitResult visitFile(
                                Path file,
                                BasicFileAttributes attributes
                        ) {

                            if (
                                    attributes.isRegularFile() &&
                                            file.fileName
                                                    .toString() ==
                                            targetName
                            ) {

                                result.add(
                                        file
                                )
                            }


                            return FileVisitResult.CONTINUE
                        }
                    }
            )
        }
        catch (Exception exception) {

            throw new GradleException(
                    """
Unable to scan cleanup files:

${scopeDirectory.absolutePath}

Reason:
${exception.message}
""".stripIndent(),
                    exception
            )
        }


        result.sort {
            Path left,
            Path right ->

                left.toString() <=>
                        right.toString()
        }


        return result
    }


    // ========================================================
    // Find empty directories
    // ========================================================

    private List<Path> findEmptyDirectories(
            File scopeDirectory,
            String targetName
    ) {

        Path scopePath =
                scopeDirectory
                        .toPath()
                        .toAbsolutePath()
                        .normalize()


        List<Path> result =
                []


        try {

            Files.walkFileTree(
                    scopePath,
                    new SimpleFileVisitor<Path>() {

                        @Override
                        FileVisitResult preVisitDirectory(
                                Path directory,
                                BasicFileAttributes attributes
                        ) {

                            // ==================================
                            // Excluded subtree
                            // ==================================

                            if (
                                    directory != scopePath &&
                                            isExcludedDirectory(
                                                    directory
                                            )
                            ) {

                                return FileVisitResult.SKIP_SUBTREE
                            }


                            // ==================================
                            // Target folder
                            // ==================================

                            if (
                                    directory != scopePath &&
                                            directory.fileName
                                                    .toString() ==
                                            targetName &&
                                            isDirectoryEmpty(
                                                    directory
                                            )
                            ) {

                                /*
                                 * Folder đang rỗng ngay tại
                                 * thời điểm scan.
                                 */
                                result.add(
                                        directory
                                )


                                return FileVisitResult.SKIP_SUBTREE
                            }


                            return FileVisitResult.CONTINUE
                        }
                    }
            )
        }
        catch (Exception exception) {

            throw new GradleException(
                    """
Unable to scan cleanup folders:

${scopeDirectory.absolutePath}

Reason:
${exception.message}
""".stripIndent(),
                    exception
            )
        }


        result.sort {
            Path left,
            Path right ->

                left.toString() <=>
                        right.toString()
        }


        return result
    }


    // ========================================================
    // Directory helpers
    // ========================================================

    private static boolean isExcludedDirectory(
            Path directory
    ) {

        return EXCLUDED_DIRECTORIES.contains(
                directory
                        .fileName
                        .toString()
        )
    }


    private static boolean isDirectoryEmpty(
            Path directory
    ) {

        Stream<Path> entries =
                Files.list(
                        directory
                )


        try {

            return !entries
                    .findAny()
                    .isPresent()
        }
        finally {

            entries.close()
        }
    }


    // ========================================================
    // Delete
    // ========================================================

    private void deletePath(
            Path path,
            String type
    ) {

        try {

            Files.delete(
                    path
            )
        }
        catch (Exception exception) {

            throw new GradleException(
                    """
Unable to delete cleanup ${type}:

${path.toAbsolutePath()}

Reason:
${exception.message}
""".stripIndent(),
                    exception
            )
        }


        logger.lifecycle(
                '🗑️ [CLEANUP] Deleted {}: {}',
                type,
                path.toAbsolutePath()
        )
    }


    // ========================================================
    // Validation
    // ========================================================

    private static String validateSimpleName(
            String value,
            String propertyName
    ) {

        String normalized =
                value
                        ?.trim()


        if (
                normalized == null ||
                        normalized.isBlank()
        ) {

            throw new GradleException(
                    """
Missing required Gradle property:

-P${propertyName}=<name>
""".stripIndent()
            )
        }


        /*
         * Hai task này làm việc theo NAME,
         * không phải relative path.
         */
        if (
                normalized.contains('/') ||
                        normalized.contains('\\')
        ) {

            throw new GradleException(
                    """
${propertyName} must contain a name only.

Invalid value:
${normalized}

Do not provide a relative or absolute path.
""".stripIndent()
            )
        }


        return normalized
    }
}