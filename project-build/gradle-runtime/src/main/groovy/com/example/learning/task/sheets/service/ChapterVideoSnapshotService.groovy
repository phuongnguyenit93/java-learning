package com.example.learning.task.sheets.service

import com.example.learning.utils.schema.validate.VideoYamlContentUtils
import org.gradle.api.GradleException

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Replaces only content/readme/docs/vi/menu/<chapter>/video/video-script.yml.
 * Preflight and staging cover every chapter before publication; previously
 * published bytes are restored if any write, removal or verification fails.
 * Legacy content/video/vi/menu/<chapter>/video-script.yml is removed only
 * after the chapter snapshot was successfully committed and read back.
 */
class ChapterVideoSnapshotService {
    private static final String CHAPTER = /[1-9][0-9]*\.[A-Za-z][A-Za-z0-9]*/

    static Map<String, Object> replace(File root, File module,
                                       Map<String, String> chapters, boolean apply,
                                       Closure afterMutation = null) {
        if (chapters == null) {
            throw new GradleException('Video chapter snapshot cannot be null.')
        }
        Path rootPath = root.toPath().toAbsolutePath().normalize()
        Path modulePath = module.toPath().toAbsolutePath().normalize()
        checkLinks(rootPath)
        checkLinks(modulePath)
        Path moduleRoot = rootPath.resolve('module')
        if (!modulePath.startsWith(moduleRoot) || modulePath == moduleRoot ||
                !Files.isDirectory(modulePath)) {
            throw new GradleException('Unsafe Video module: ' + module)
        }
        File menu = new File(module, 'content/readme/docs/vi/menu')
        File legacyMenu = new File(module, 'content/video/vi/menu')
        checkLinks(menu.toPath())
        checkLinks(legacyMenu.toPath())
        if (menu.exists() && !menu.isDirectory()) {
            throw new GradleException('Video Knowledge menu is not a directory: ' + menu)
        }
        if (legacyMenu.exists() && !legacyMenu.isDirectory()) {
            throw new GradleException('Legacy Video menu is not a directory: ' + legacyMenu)
        }

        Map<File, byte[]> expected = new LinkedHashMap<>()
        chapters.each { String chapter, String body ->
            if (chapter == null || !(chapter ==~ CHAPTER) || body == null) {
                throw new GradleException('Invalid Video chapter: ' + chapter)
            }
            File folder = new File(menu, chapter)
            requireKnowledge(folder)
            File output = new File(folder, 'video/' + VideoYamlContentUtils.FILENAME)
            checkLinks(output.toPath())
            VideoYamlContentUtils.parse(body)
            expected[output.absoluteFile] = body.getBytes('UTF-8')
        }

        Map<File, byte[]> previous = new LinkedHashMap<>()
        if (menu.isDirectory()) {
            children(menu).each { File folder ->
                checkLinks(folder.toPath())
                File videoFolder = new File(folder, 'video')
                if (!(folder.name ==~ CHAPTER)) {
                    if (videoFolder.exists()) {
                        throw new GradleException('Orphan Video chapter: ' + folder)
                    }
                    return
                }
                checkLinks(videoFolder.toPath())
                if (videoFolder.exists() && !videoFolder.isDirectory()) {
                    throw new GradleException('Video path is not a directory: ' + videoFolder)
                }
                if (!videoFolder.isDirectory()) return
                children(videoFolder).each { File child ->
                    checkLinks(child.toPath())
                }
                File owned = new File(videoFolder, VideoYamlContentUtils.FILENAME)
                if (owned.exists()) {
                    requireKnowledge(folder)
                    readOwned(previous, owned)
                }
            }
        }

        // All legacy Video files are migration-owned, but arbitrary non-Video
        // files and orphan chapter paths must never be silently deleted.
        Set<File> legacyFiles = new LinkedHashSet<>()
        if (legacyMenu.isDirectory()) {
            def entries = Files.walk(legacyMenu.toPath())
            try {
                entries.forEach { Path entry ->
                    checkLinks(entry)
                    if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) return
                    Path relative = legacyMenu.toPath().relativize(entry)
                    if (relative.nameCount != 2 ||
                            !(relative.getName(0).toString() ==~ CHAPTER) ||
                            relative.getName(1).toString() != VideoYamlContentUtils.FILENAME) {
                        throw new GradleException('Orphan or unexpected legacy Video file: ' + entry)
                    }
                    requireKnowledge(new File(menu, relative.getName(0).toString()))
                    File owned = entry.toFile().absoluteFile
                    readOwned(previous, owned)
                    legacyFiles.add(owned)
                }
            }
            finally { entries.close() }
        }
        expected.keySet().each { File file ->
            if (!previous.containsKey(file) && file.exists()) {
                throw new GradleException('Video destination occupied by non-file: ' + file)
            }
        }

        List<File> changedTargets = expected.keySet().findAll { File file ->
            !Arrays.equals(previous[file], expected[file])
        }.toList()
        List<File> stale = previous.keySet().findAll { File file -> !expected.containsKey(file) }.toList()
        List<File> obsoleteCurrent = stale.findAll { !legacyFiles.contains(it) }
        int pending = changedTargets.size() + stale.size()
        int unchanged = expected.size() - changedTargets.size()
        String status = pending == 0 ? 'UNCHANGED' :
                expected.isEmpty() ? 'CLEARED_EMPTY' :
                previous.isEmpty() ? 'IMPORTED' : 'UPDATED'
        if (!apply || pending == 0) {
            return [status: apply ? status : pending == 0 ? 'UNCHANGED' : 'DRY_RUN',
                    expectedFiles: expected.size(), clearedOldFiles: 0,
                    pendingFiles: pending, unchangedFiles: unchanged,
                    chapterCount: chapters.size()]
        }

        Map<File, Path> staged = new LinkedHashMap<>()
        Set<Path> createdDirs = new LinkedHashSet<>()
        List<File> touched = []
        try {
            // Stage all modified destinations before committing even one.
            changedTargets.each { File file ->
                Path parent = file.parentFile.toPath()
                checkLinks(parent)
                if (!Files.exists(parent)) createdDirs.add(parent)
                Files.createDirectories(parent)
                Path temporary = Files.createTempFile(parent, '.chapter-video-', '.tmp')
                staged[file] = temporary
                Files.write(temporary, expected[file])
                if (!Arrays.equals(Files.readAllBytes(temporary), expected[file])) {
                    throw new GradleException('Video stage readback failed: ' + file)
                }
            }
            // Reject concurrent edits between the read-only preflight and commit.
            previous.each { File file, byte[] bytes ->
                checkLinks(file.toPath())
                if (!file.isFile() || !Arrays.equals(Files.readAllBytes(file.toPath()), bytes)) {
                    throw new GradleException('Video source changed during preflight: ' + file)
                }
            }
            expected.keySet().each { File file ->
                checkLinks(file.toPath())
                if (!previous.containsKey(file) && file.exists()) {
                    throw new GradleException('Video destination occupied during preflight: ' + file)
                }
            }

            changedTargets.each { File file ->
                touched.add(file)
                Files.move(staged[file], file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                staged.remove(file)
                afterMutation?.call(file)
            }
            obsoleteCurrent.each { File file ->
                touched.add(file)
                Files.delete(file.toPath())
                afterMutation?.call(file)
            }
            expected.each { File file, byte[] bytes ->
                if (!Arrays.equals(Files.readAllBytes(file.toPath()), bytes)) {
                    throw new GradleException('Video snapshot readback failed: ' + file)
                }
            }
            // The new snapshot is complete. Legacy removals are last and still
            // participate in rollback if any deletion or verification fails.
            legacyFiles.each { File file ->
                touched.add(file)
                Files.delete(file.toPath())
                afterMutation?.call(file)
            }
            return [status: status, expectedFiles: expected.size(),
                    clearedOldFiles: stale.size(), pendingFiles: 0,
                    unchangedFiles: unchanged, chapterCount: chapters.size()]
        }
        catch (Exception error) {
            touched.reverseEach { File file ->
                try {
                    if (previous.containsKey(file)) {
                        Files.createDirectories(file.parentFile.toPath())
                        Files.write(file.toPath(), previous[file])
                    } else {
                        Files.deleteIfExists(file.toPath())
                    }
                }
                catch (Exception restoreError) { error.addSuppressed(restoreError) }
            }
            throw new GradleException('Unable to commit Video chapter snapshot: ' + error.message, error)
        }
        finally {
            staged.values().each { Path file -> Files.deleteIfExists(file) }
            createdDirs.toList().reverseEach { Path dir ->
                if (Files.isDirectory(dir)) {
                    try { Files.delete(dir) }
                    catch (java.nio.file.DirectoryNotEmptyException ignored) { }
                }
            }
        }
    }

    private static void readOwned(Map<File, byte[]> into, File file) {
        checkLinks(file.toPath())
        if (!Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            throw new GradleException('Video destination occupied by non-file: ' + file)
        }
        into[file.absoluteFile] = Files.readAllBytes(file.toPath())
    }

    private static void requireKnowledge(File chapter) {
        checkLinks(chapter.toPath())
        File knowledge = new File(chapter, 'knowledge')
        checkLinks(knowledge.toPath())
        if (!knowledge.isDirectory()) {
            throw new GradleException('Missing Knowledge chapter for Video import: ' + chapter)
        }
    }

    private static void checkLinks(Path path) {
        Path current = path.toAbsolutePath().normalize()
        while (current != null) {
            if (Files.isSymbolicLink(current)) {
                throw new GradleException('Linked Video path is forbidden: ' + current)
            }
            current = current.parent
        }
    }

    private static File[] children(File directory) {
        File[] result = directory.listFiles()
        if (result == null) {
            throw new GradleException('Cannot inspect Video directory: ' + directory)
        }
        return result
    }
}
