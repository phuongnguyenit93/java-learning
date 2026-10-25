package com.example.learning.task.sheets.service

import org.gradle.api.GradleException

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Sheet is authoritative for the entire owned directory. Prepare and verify
 * the replacement BEFORE permanently deleting the old tree. No backup or AI.
 */
class SheetSnapshotReplaceService {
    private static final List<List<String>> ALLOWED_SUFFIXES = [
            ['content', 'roadmap', 'vi'],
            ['content', 'video', 'vi', 'menu']
    ].asImmutable()

    static Map<String, Object> replace(
            File repositoryRoot, File directory,
            Map<String, String> expected, boolean apply
    ) {
        File root = repositoryRoot.canonicalFile
        File moduleRoot = new File(root, 'module').canonicalFile
        Path targetPath = directory.canonicalFile.toPath()
        if (expected == null || !targetPath.startsWith(moduleRoot.toPath()) ||
                targetPath == moduleRoot.toPath()) {
            throw new GradleException('Unsafe Sheet snapshot destination: ' + directory)
        }
        List<String> parts = []
        targetPath.each { Path part -> parts.add(part.toString()) }
        boolean knowledgeFolder = parts.size() > 8 &&
                parts.takeRight(7).take(5) ==
                        ['content', 'readme', 'docs', 'vi', 'menu'] &&
                (parts[-2] ==~ /[0-9]+\.[A-Za-z][A-Za-z0-9]*/) &&
                parts[-1] == 'knowledge'
        if (!(knowledgeFolder || ALLOWED_SUFFIXES.any { List<String> suffix ->
            parts.size() > suffix.size() + 1 && parts.takeRight(suffix.size()) == suffix
        }) || hasSymlink(directory.toPath())) {
            throw new GradleException('Refusing to clean non-owned or linked path: ' + directory)
        }
        if (directory.exists() && !directory.isDirectory()) {
            throw new GradleException('Sheet output is not a directory: ' + directory)
        }
        expected.each { String relative, String body ->
            if (body == null || !relative || relative.startsWith('/') ||
                    relative.contains('\\') ||
                    relative.split('/').any { it in ['', '.', '..'] }) {
                throw new GradleException('Invalid Sheet snapshot filename: ' + relative)
            }
        }
        Map<String, String> previous = readTree(directory)
        if (!apply) {
            return [status: previous == expected ? 'UNCHANGED' : 'DRY_RUN',
                    clearedOldFiles: 0, expectedFiles: expected.size()]
        }
        Path staged = null
        boolean deleted = false
        try {
            Files.createDirectories(directory.parentFile.toPath())
            staged = Files.createTempDirectory(
                    directory.parentFile.toPath(), '.sheet-snapshot-stage-')
            expected.each { String relative, String body ->
                File output = new File(staged.toFile(), relative)
                if (!output.canonicalFile.toPath().startsWith(staged.toFile().canonicalFile.toPath())) {
                    throw new GradleException('Sheet snapshot path escaped stage: ' + relative)
                }
                Files.createDirectories(output.parentFile.toPath())
                Files.write(output.toPath(), body.getBytes(StandardCharsets.UTF_8))
            }
            if (readTree(staged.toFile()) != expected ||
                    readTree(directory) != previous) {
                throw new GradleException('Sheet snapshot verification/preflight failed: ' + directory)
            }
            if (directory.exists()) {
                deleted = true
                deleteTree(directory)
            }
            if (Files.exists(directory.toPath())) {
                throw new GradleException('Unable to clean old Sheet output: ' + directory)
            }
            Files.move(staged, directory.toPath(), StandardCopyOption.ATOMIC_MOVE)
            staged = null
            if (readTree(directory) != expected) {
                throw new GradleException('Sheet snapshot exact readback failed: ' + directory)
            }
            return [
                    status: expected.isEmpty() ? 'CLEARED_EMPTY'
                            : (previous == expected ? 'UNCHANGED'
                            : previous.isEmpty() ? 'IMPORTED' : 'UPDATED'),
                    clearedOldFiles: previous.size(), expectedFiles: expected.size()
            ]
        }
        catch (Exception error) {
            String detail = deleted
                    ? ' Previous output was permanently deleted and cannot be restored.' : ''
            if (deleted && staged != null && Files.exists(staged)) {
                detail += ' Prepared new files remain at ' + staged + '.'
            }
            throw new GradleException(
                    'Unable to replace Sheet snapshot ' + directory + ': ' +
                            error.message + '.' + detail, error
            )
        }
        finally {
            if (!deleted && staged != null && Files.exists(staged)) {
                deleteTree(staged.toFile())
            }
        }
    }

    static Map<String, String> readTree(File directory) {
        Map<String, String> result = new LinkedHashMap<>()
        if (!directory.exists()) return result
        if (!directory.isDirectory() || hasSymlink(directory.toPath())) {
            throw new GradleException('Unsafe Sheet snapshot directory: ' + directory)
        }
        Path base = directory.canonicalFile.toPath()
        def stream = Files.walk(directory.toPath())
        try {
            stream.forEach { Path entry ->
                if (Files.isSymbolicLink(entry)) {
                    throw new GradleException('Symlink inside Sheet output: ' + entry)
                }
                if (Files.isRegularFile(entry)) {
                    if (!entry.toFile().canonicalFile.toPath().startsWith(base)) {
                        throw new GradleException('Unsafe Sheet output file: ' + entry)
                    }
                    String relative = directory.toPath().relativize(entry).toString()
                            .replace(File.separatorChar, '/' as char)
                    result[relative] = new String(
                            Files.readAllBytes(entry), StandardCharsets.UTF_8)
                }
            }
        }
        finally { stream.close() }
        return result
    }

    private static boolean hasSymlink(Path path) {
        Path cursor = path.toAbsolutePath().normalize()
        while (cursor != null) {
            if (Files.isSymbolicLink(cursor)) return true
            cursor = cursor.parent
        }
        return false
    }

    private static void deleteTree(File directory) {
        def stream = Files.walk(directory.toPath())
        try {
            stream.sorted(Comparator.reverseOrder()).forEach { Path path ->
                Files.delete(path)
            }
        }
        finally { stream.close() }
    }
}
