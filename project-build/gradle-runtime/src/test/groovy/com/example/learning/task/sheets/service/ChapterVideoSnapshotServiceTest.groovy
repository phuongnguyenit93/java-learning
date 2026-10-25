package com.example.learning.task.sheets.service

import org.gradle.api.GradleException
import org.junit.Rule
import org.junit.Test
import org.junit.Assume
import org.junit.rules.TemporaryFolder

import java.nio.file.Files

import static org.junit.Assert.*

class ChapterVideoSnapshotServiceTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder()

    private static File chapter(File module, String name) {
        File folder = new File(module, 'content/readme/docs/vi/menu/' + name)
        assertTrue(new File(folder, 'knowledge').mkdirs())
        return folder
    }

    private static File script(File chapter) {
        return new File(chapter, 'video/video-script.yml')
    }

    @Test
    void validatesWholeModuleAndChangesOnlyVideoFiles() {
        File root = temp.newFolder('safe-video')
        File module = new File(root, 'module/demo')
        File one = chapter(module, '1.OOPBasic')
        File two = chapter(module, '2.Class')
        File authored = script(one)
        assertTrue(authored.parentFile.mkdirs())
        authored.setText('old video', 'UTF-8')
        File stale = script(two)
        assertTrue(stale.parentFile.mkdirs())
        stale.setText('stale video', 'UTF-8')
        File readme = new File(one, 'knowledge/OOPBasic.md')
        readme.setText('Knowledge content', 'UTF-8')
        File metadata = new File(one, 'metadata/knowledge-metadata.yml')
        assertTrue(metadata.parentFile.mkdirs())
        metadata.setText('knowledge: protected', 'UTF-8')
        File interview = new File(one, 'interview/question.yml')
        assertTrue(interview.parentFile.mkdirs())
        interview.setText('interview: protected', 'UTF-8')
        File quiz = new File(one, 'quiz/question.yml')
        assertTrue(quiz.parentFile.mkdirs())
        quiz.setText('quiz: protected', 'UTF-8')
        File en = new File(module, 'content/readme/docs/en/menu/1.OOPBasic/video/video-script.yml')
        assertTrue(en.parentFile.mkdirs())
        en.setText('english video', 'UTF-8')

        Map<String, String> next = ['1.OOPBasic': VideoSheetImportServiceTest.SCRIPT]
        Map dry = ChapterVideoSnapshotService.replace(root, module, next, false)
        assertEquals('DRY_RUN', dry.status)
        assertEquals('old video', authored.getText('UTF-8'))
        assertTrue(stale.exists())
        Map applied = ChapterVideoSnapshotService.replace(root, module, next, true)
        assertEquals('UPDATED', applied.status)
        assertEquals(2, applied.clearedOldFiles)
        assertEquals(VideoSheetImportServiceTest.SCRIPT, authored.getText('UTF-8'))
        assertFalse(stale.exists())
        assertEquals('Knowledge content', readme.getText('UTF-8'))
        assertEquals('knowledge: protected', metadata.getText('UTF-8'))
        assertEquals('interview: protected', interview.getText('UTF-8'))
        assertEquals('quiz: protected', quiz.getText('UTF-8'))
        assertEquals('english video', en.getText('UTF-8'))
        assertEquals('UNCHANGED', ChapterVideoSnapshotService.replace(root, module, next, true).status)

        assertEquals('CLEARED_EMPTY', ChapterVideoSnapshotService.replace(root, module, [:], true).status)
        assertFalse(authored.exists())
        assertEquals('Knowledge content', readme.getText('UTF-8'))
        assertEquals('english video', en.getText('UTF-8'))
        assertEquals('UNCHANGED', ChapterVideoSnapshotService.replace(root, module, [:], true).status)
    }

    @Test
    void missingNewKnowledgeBlocksButStaleOrphanVideoIsCleaned() {
        File root = temp.newFolder('missing-video')
        File module = new File(root, 'module/demo')
        File valid = chapter(module, '1.OOPBasic')
        File existing = script(valid)
        assertTrue(existing.parentFile.mkdirs())
        existing.setText('preserve', 'UTF-8')
        shouldFail('Missing Knowledge chapter') {
            ChapterVideoSnapshotService.replace(root, module,
                    ['2.Missing': VideoSheetImportServiceTest.SCRIPT], true)
        }
        assertEquals('preserve', existing.getText('UTF-8'))

        File orphan = new File(module, 'content/readme/docs/vi/menu/3.Orphan/video/video-script.yml')
        assertTrue(orphan.parentFile.mkdirs())
        orphan.setText('orphan', 'UTF-8')
        assertEquals('UPDATED', ChapterVideoSnapshotService.replace(root, module,
                ['1.OOPBasic': VideoSheetImportServiceTest.SCRIPT], true).status)
        assertEquals(VideoSheetImportServiceTest.SCRIPT, existing.getText('UTF-8'))
        assertFalse(orphan.exists())
    }

    @Test
    void legacyVideoDirectoryIsEntirelyCleanedWhenSheetImported() {
        File root = temp.newFolder('legacy-video')
        File module = new File(root, 'module/demo')
        File one = chapter(module, '1.OOPBasic')
        File legacy = new File(module, 'content/video/vi/menu/1.OOPBasic/video-script.yml')
        assertTrue(legacy.parentFile.mkdirs())
        legacy.setText('legacy bytes', 'UTF-8')
        File extra = new File(legacy.parentFile, 'my-notes.md')
        extra.setText('unowned bytes', 'UTF-8')
        assertEquals('DRY_RUN', ChapterVideoSnapshotService.replace(root, module,
                ['1.OOPBasic': VideoSheetImportServiceTest.SCRIPT], false).status)
        assertEquals('legacy bytes', legacy.getText('UTF-8'))
        Map applied = ChapterVideoSnapshotService.replace(root, module,
                ['1.OOPBasic': VideoSheetImportServiceTest.SCRIPT], true)
        assertEquals('UPDATED', applied.status)
        assertEquals(VideoSheetImportServiceTest.SCRIPT, script(one).getText('UTF-8'))
        assertFalse(legacy.exists())
        assertFalse(extra.exists())
    }

    @Test
    void rollbackRestoresExistingBytesAndLegacyAfterPartialFailure() {
        File root = temp.newFolder('rollback-video')
        File module = new File(root, 'module/demo')
        File one = chapter(module, '1.OOPBasic')
        File two = chapter(module, '2.Class')
        File current = script(one)
        assertTrue(current.parentFile.mkdirs())
        current.setText('previous bytes', 'UTF-8')
        File stale = script(two)
        assertTrue(stale.parentFile.mkdirs())
        stale.setText('stale bytes', 'UTF-8')
        File legacy = new File(module, 'content/video/vi/menu/1.OOPBasic/video-script.yml')
        assertTrue(legacy.parentFile.mkdirs())
        legacy.setText('legacy bytes', 'UTF-8')
        shouldFail('Unable to clean/fill Sheet snapshot') {
            ChapterVideoSnapshotService.replace(root, module,
                    ['1.OOPBasic': VideoSheetImportServiceTest.SCRIPT], true) { File file ->
                if (file.canonicalFile == current.canonicalFile)
                    throw new IOException('Injected failure after first write')
            }
        }
        assertEquals('previous bytes', current.getText('UTF-8'))
        assertEquals('stale bytes', stale.getText('UTF-8'))
        assertEquals('legacy bytes', legacy.getText('UTF-8'))
        shouldFail('Unable to clean/fill Sheet snapshot') {
            ChapterVideoSnapshotService.replace(root, module,
                    ['1.OOPBasic': VideoSheetImportServiceTest.SCRIPT], true) { File file ->
                if (file.canonicalFile == legacy.canonicalFile)
                    throw new IOException('Injected failure after legacy removal')
            }
        }
        assertEquals('previous bytes', current.getText('UTF-8'))
        assertEquals('stale bytes', stale.getText('UTF-8'))
        assertEquals('legacy bytes', legacy.getText('UTF-8'))
    }

    @Test
    void validatesYamlAndCleansUnexpectedOldVideoFilesAndDirectories() {
        File root = temp.newFolder('invalid-video')
        File module = new File(root, 'module/demo')
        File one = chapter(module, '1.OOPBasic')
        File two = chapter(module, '2.Class')
        File occupied = script(two)
        assertTrue(occupied.mkdirs())
        shouldFail('Invalid Video Script YAML') {
            ChapterVideoSnapshotService.replace(root, module,
                    ['1.OOPBasic': 'title: [invalid'], true)
        }
        assertFalse(script(one).exists())
        assertEquals('IMPORTED', ChapterVideoSnapshotService.replace(root, module,
                ['1.OOPBasic': VideoSheetImportServiceTest.SCRIPT,
                 '2.Class': VideoSheetImportServiceTest.SCRIPT], true).status)
        assertEquals(VideoSheetImportServiceTest.SCRIPT, occupied.getText('UTF-8'))
    }

    @Test
    void refusesLinkedVideoDirectoryWithoutFollowingIt() {
        File root = temp.newFolder('linked-video')
        File module = new File(root, 'module/demo')
        File one = chapter(module, '1.OOPBasic')
        File external = temp.newFolder('external-video')
        new File(external, 'video-script.yml').setText('external bytes', 'UTF-8')
        File linked = new File(one, 'video')
        try {
            Files.createSymbolicLink(linked.toPath(), external.toPath())
        }
        catch (Exception unsupportedOnHost) {
            Assume.assumeNoException('Symlink creation is unavailable on this host', unsupportedOnHost)
        }
        shouldFail('Linked Video path') {
            ChapterVideoSnapshotService.replace(root, module,
                    ['1.OOPBasic': VideoSheetImportServiceTest.SCRIPT], true)
        }
        assertEquals('external bytes', new File(external, 'video-script.yml').getText('UTF-8'))
    }

    private static void shouldFail(String message, Closure closure) {
        try {
            closure.call()
            fail('Expected Video snapshot rejection')
        }
        catch (GradleException error) {
            assertTrue(error.message, error.message.contains(message))
        }
    }
}
