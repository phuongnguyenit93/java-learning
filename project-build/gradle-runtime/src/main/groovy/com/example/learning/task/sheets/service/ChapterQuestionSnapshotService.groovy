package com.example.learning.task.sheets.service

import org.gradle.api.GradleException

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * A full-sheet Interview/Quiz import owns only menu/<chapter>/<kind>/question.yml.
 * All sources are validated and staged before modifying a single owned file.
 * An IOException during publication restores the exact previous bytes.
 * Knowledge, metadata, other languages and Video are never modified.
 */
class ChapterQuestionSnapshotService {
    static Map<String, Object> replace(
            File root, File module, String kind,
            Map<String, String> chapters, boolean apply
    ) {
        if (!(kind in ['interview', 'quiz']) || chapters == null) {
            throw new GradleException('Unsupported chapter question snapshot type.')
        }
        File repo = root.canonicalFile
        File moduleRoot = new File(repo, 'module').canonicalFile
        File owner = module.canonicalFile
        if (!owner.toPath().startsWith(moduleRoot.toPath()) ||
                owner.toPath() == moduleRoot.toPath()) {
            throw new GradleException('Unsafe module for question snapshot: ' + module)
        }
        File menu = new File(owner, 'content/readme/docs/vi/menu').canonicalFile
        if (!menu.toPath().startsWith(owner.toPath())) {
            throw new GradleException('Unsafe question menu: ' + menu)
        }
        Map<File, byte[]> expected = new LinkedHashMap<>()
        chapters.each { String chapter, String content ->
            if (!(chapter ==~ /[0-9]+\.[^\/.][^\\/]*[A-Za-z0-9_]$/) ||
                    chapter.contains('..') || content == null) {
                throw new GradleException('Invalid question chapter: ' + chapter)
            }
            File file = new File(menu, chapter + '/' + kind + '/question.yml').canonicalFile
            if (!file.toPath().startsWith(menu.toPath())) {
                throw new GradleException('Question path escaped menu: ' + file)
            }
            File chapterRoot = new File(menu, chapter)
            if (!new File(chapterRoot, 'knowledge').isDirectory()) {
                throw new GradleException('Missing Knowledge chapter for question import: ' + chapterRoot)
            }
            expected[file] = content.getBytes('UTF-8')
        }
        Map<File, byte[]> original = new LinkedHashMap<>()
        if (menu.isDirectory()) {
            menu.listFiles().findAll { it.isDirectory() }.each { File chapter ->
                File folder = new File(chapter, kind)
                if (!chapter.canonicalFile.toPath().startsWith(menu.toPath()) ||
                        !folder.canonicalFile.toPath().startsWith(menu.toPath())) {
                    throw new GradleException('Linked question chapter escapes menu: ' + chapter)
                }
                File owned = new File(folder, 'question.yml')
                if (owned.isFile()) {
                    if (!owned.canonicalFile.toPath().startsWith(menu.toPath())) {
                        throw new GradleException('Linked question target escaped menu: ' + owned)
                    }
                    original[owned.canonicalFile] = Files.readAllBytes(owned.toPath())
                }
                if (folder.exists() && !folder.isDirectory()) {
                    throw new GradleException('Question folder is not a directory: ' + folder)
                }
            }
        }
        // The pre-migration file is also an owned source, but never delete it
        // before the entire replacement is complete.
        File old = new File(owner, 'content/' + kind + '/vi/question.yml')
        if (old.isFile()) {
            if (!old.canonicalFile.toPath().startsWith(owner.toPath())) {
                throw new GradleException('Linked legacy question target escaped module: ' + old)
            }
            original[old.canonicalFile] = Files.readAllBytes(old.toPath())
        }
        expected.keySet().each { File file ->
            if (!original.containsKey(file) && file.exists()) {
                throw new GradleException('Unexpected target question path: ' + file)
            }
        }
        Set<File> changed = (original.keySet() + expected.keySet()) as Set<File>
        changed = changed.findAll { File f ->
            !Arrays.equals(original[f], expected[f])
        } as Set<File>
        if (!apply) {
            return [status: changed.isEmpty() ? 'UNCHANGED' : 'DRY_RUN',
                    expectedFiles: expected.size(), clearedOldFiles: 0,
                    chapterCount: chapters.size()]
        }
        // No permission to delete directories, no recursive cleanup. Stage
        // every new file and verify its bytes before replacing any prior file.
        Map<File, Path> prepared = [:]
        try {
            expected.each { File file, byte[] body ->
                Files.createDirectories(file.parentFile.toPath())
                Path stage = Files.createTempFile(file.parentFile.toPath(), '.chapter-question-', '.tmp')
                Files.write(stage, body)
                if (!Arrays.equals(Files.readAllBytes(stage), body)) {
                    throw new GradleException('Question stage readback failed: ' + file)
                }
                prepared[file] = stage
            }
            original.each { File file, byte[] body ->
                if (!file.isFile() || !Arrays.equals(Files.readAllBytes(file.toPath()), body)) {
                    throw new GradleException('Question source changed during preflight: ' + file)
                }
            }
            expected.each { File file, byte[] body ->
                if (!original.containsKey(file) && file.exists()) {
                    throw new GradleException('Question destination occupied during preflight: ' + file)
                }
            }
            List<File> touched = []
            try {
                expected.each { File file, byte[] body ->
                    touched.add(file)
                    Files.move(prepared[file], file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    prepared.remove(file)
                }
                (original.keySet() - expected.keySet()).each { File file ->
                    touched.add(file)
                    Files.delete(file.toPath())
                }
                expected.each { File file, byte[] bytes ->
                    if (!Arrays.equals(Files.readAllBytes(file.toPath()), bytes)) {
                        throw new GradleException('Question exact readback failed: ' + file)
                    }
                }
            }
            catch (Exception error) {
                touched.reverseEach { File file ->
                    if (original.containsKey(file)) {
                        Files.createDirectories(file.parentFile.toPath())
                        Files.write(file.toPath(), original[file])
                    } else Files.deleteIfExists(file.toPath())
                }
                throw error
            }
            return [status: changed.isEmpty() ? 'UNCHANGED' : 'UPDATED',
                    expectedFiles: expected.size(),
                    clearedOldFiles: (original.keySet() - expected.keySet()).size(),
                    chapterCount: chapters.size()]
        }
        finally {
            prepared.values().each { Path path -> Files.deleteIfExists(path) }
        }
    }
}
