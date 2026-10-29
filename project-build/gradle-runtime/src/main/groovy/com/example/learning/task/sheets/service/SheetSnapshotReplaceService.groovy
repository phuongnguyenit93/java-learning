package com.example.learning.task.sheets.service

import org.gradle.api.GradleException

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Sheet is authoritative for an entire owned directory. Stage everything first,
 * CLEAN every owned directory, then FILL and read back. Old trees exist only
 * as temporary rollback material until a successful commit; no old content is
 * merged into the new snapshot, and no AI is involved.
 */
class SheetSnapshotReplaceService {
    private static final List<List<String>> ALLOWED_SUFFIXES = [
            ['content', 'reference'],
            ['content', 'roadmap', 'vi'],
            ['content', 'readme', 'metadata', 'vi'],
            ['content', 'interview', 'vi'],
            ['content', 'quiz', 'vi'],
            ['content', 'casestudy', 'vi'],
            ['content', 'video', 'vi'],
            ['content', 'video', 'vi', 'menu']
    ].asImmutable()

    static Map<String, Object> replace(
            File repositoryRoot, File directory,
            Map<String, String> expected, boolean apply
    ) {
        return replaceAll(repositoryRoot, [(directory): expected], apply)
    }

    /** All targets are preflighted, cleaned and filled in a single transaction. */
    static Map<String, Object> replaceAll(
            File repositoryRoot, Map<File, Map<String, String>> targets,
            boolean apply, Closure afterMutation = null
    ) {
        File root = repositoryRoot.canonicalFile
        File moduleRoot = new File(root, 'module').canonicalFile
        if (targets == null) throw new GradleException('Null Sheet snapshot targets.')
        Map<File, Map<String, String>> planned = new LinkedHashMap<>()
        targets.each { File rawDirectory, Map<String, String> expected ->
            if (rawDirectory == null || expected == null) {
                throw new GradleException('Null Sheet snapshot destination or content.')
            }
            File directory = rawDirectory.canonicalFile
            Path targetPath = directory.toPath()
            if (!targetPath.startsWith(moduleRoot.toPath()) ||
                    targetPath == moduleRoot.toPath()) {
                throw new GradleException('Unsafe Sheet snapshot destination: ' + directory)
            }
            List<String> parts = []
            targetPath.each { Path part -> parts.add(part.toString()) }
            boolean chapterFolder = parts.size() > 8 &&
                    parts.takeRight(7).take(5) ==
                            ['content', 'readme', 'docs', 'vi', 'menu'] &&
                    (parts[-2] ==~ /[0-9]+\.[A-Za-z0-9][A-Za-z0-9 _.-]*/) &&
                    !parts[-2].contains('..') &&
                    parts[-1] in ['knowledge', 'metadata', 'interview', 'questions', 'quiz', 'video', 'summary', 'practice']
            boolean roadmapFolder = ALLOWED_SUFFIXES.any { List<String> suffix ->
                parts.size() > suffix.size() + 1 && parts.takeRight(suffix.size()) == suffix
            }
            if (!(chapterFolder || roadmapFolder) || hasSymlink(rawDirectory.toPath())) {
                throw new GradleException('Refusing to clean non-owned or linked path: ' + directory)
            }
            if (directory.exists() && !directory.isDirectory()) {
                throw new GradleException('Sheet output is not a directory: ' + directory)
            }
            expected.each { String relative, String body ->
                if (body == null || !relative || relative.startsWith('/') ||
                        relative.contains('\\') || relative.contains(':') ||
                        relative.split('/').any { it in ['', '.', '..'] }) {
                    throw new GradleException('Invalid Sheet snapshot filename: ' + relative)
                }
            }
            if (planned.containsKey(directory)) {
                throw new GradleException('Duplicate Sheet snapshot directory: ' + directory)
            }
            planned[directory] = expected
        }
        List<File> directories = planned.keySet().toList()
        directories.eachWithIndex { File directory, int index ->
            directories.drop(index + 1).each { File other ->
                if (directory.toPath().startsWith(other.toPath()) ||
                        other.toPath().startsWith(directory.toPath())) {
                    throw new GradleException('Overlapping Sheet snapshot directories: ' + directory)
                }
            }
        }
        Map<File, Map<String, String>> previous = new LinkedHashMap<>()
        planned.each { File directory, Map<String, String> ignored ->
            previous[directory] = readTree(directory)
        }
        int oldCount = previous.values().sum(0) { it.size() } as int
        int expectedCount = planned.values().sum(0) { it.size() } as int
        boolean unchanged = previous == planned
        if (!apply || planned.isEmpty() || (unchanged && planned.keySet().every { !it.exists() })) {
            return [status: unchanged ? 'UNCHANGED' : 'DRY_RUN',
                    clearedOldFiles: 0, expectedFiles: expectedCount]
        }
        Map<File, Path> staged = new LinkedHashMap<>()
        Map<File, Path> rollback = new LinkedHashMap<>()
        boolean completed = false
        try {
            // Build the complete next snapshot before touching any existing output.
            planned.each { File directory, Map<String, String> expected ->
                if (!expected.isEmpty()) {
                    Files.createDirectories(directory.parentFile.toPath())
                    Path stage = Files.createTempDirectory(directory.parentFile.toPath(), '.sheet-stage-')
                    staged[directory] = stage
                    expected.each { String relative, String body ->
                        File output = new File(stage.toFile(), relative)
                        if (!output.canonicalFile.toPath().startsWith(stage.toFile().canonicalFile.toPath())) {
                            throw new GradleException('Sheet snapshot path escaped stage: ' + relative)
                        }
                        Files.createDirectories(output.parentFile.toPath())
                        Files.write(output.toPath(), body.getBytes(StandardCharsets.UTF_8))
                    }
                    if (readTree(stage.toFile()) != expected) {
                        throw new GradleException('Sheet stage readback failed: ' + directory)
                    }
                }
            }
            previous.each { File directory, Map<String, String> before ->
                if (readTree(directory) != before) {
                    throw new GradleException('Sheet snapshot changed during preflight: ' + directory)
                }
            }
            // CLEAN phase: no owned directory retains the old contents.
            planned.keySet().each { File directory ->
                if (directory.exists()) {
                    Path backup = Files.createTempDirectory(directory.parentFile.toPath(), '.sheet-rollback-')
                    Files.delete(backup)
                    Files.move(directory.toPath(), backup, StandardCopyOption.ATOMIC_MOVE)
                    rollback[directory] = backup
                    afterMutation?.call('CLEAN', directory)
                }
            }
            // FILL phase: publish only the already verified source-of-truth bytes.
            planned.each { File directory, Map<String, String> expected ->
                if (!expected.isEmpty()) {
                    Files.move(staged.remove(directory), directory.toPath(), StandardCopyOption.ATOMIC_MOVE)
                    afterMutation?.call('FILL', directory)
                }
            }
            planned.each { File directory, Map<String, String> expected ->
                if (readTree(directory) != expected) {
                    throw new GradleException('Sheet snapshot exact readback failed: ' + directory)
                }
            }
            completed = true
            return [status: unchanged ? 'UNCHANGED' : expectedCount == 0 ? 'CLEARED_EMPTY'
                    : oldCount == 0 ? 'IMPORTED' : 'UPDATED',
                    clearedOldFiles: oldCount, expectedFiles: expectedCount]
        }
        catch (Exception error) {
            // Roll back all owned folders, including already filled chapters.
            planned.keySet().toList().reverseEach { File directory ->
                try {
                    if (rollback.containsKey(directory)) {
                        if (directory.exists()) deleteTree(directory)
                        Files.move(rollback.remove(directory), directory.toPath(), StandardCopyOption.ATOMIC_MOVE)
                    } else if (!previous[directory].isEmpty() || directory.exists()) {
                        // A directory with no previous content may have been created by FILL.
                        if (directory.exists() && previous[directory].isEmpty()) deleteTree(directory)
                    }
                } catch (Exception restoreError) { error.addSuppressed(restoreError) }
            }
            throw new GradleException('Unable to clean/fill Sheet snapshot: ' + error.message, error)
        }
        finally {
            staged.values().each { Path stage ->
                if (Files.exists(stage)) deleteTree(stage.toFile())
            }
            // Only delete rollback snapshots after success. Failed restores keep
            // their bytes available for manual recovery rather than destroying them.
            if (completed) rollback.values().each { Path old ->
                if (Files.exists(old)) deleteTree(old.toFile())
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
