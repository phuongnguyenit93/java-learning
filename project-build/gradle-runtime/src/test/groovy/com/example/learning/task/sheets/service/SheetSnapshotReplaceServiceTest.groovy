package com.example.learning.task.sheets.service

import org.gradle.api.GradleException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.junit.Assert.*

class SheetSnapshotReplaceServiceTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder()

    @Test
    void cleansOnlyOwnedViTreesWithoutTouchingEnglishOrModuleFiles() {
        File root = temporary.newFolder('isolation')
        File module = new File(root, 'module/example')
        File english = new File(module, 'content/video/en/menu/1.Class/Class.md')
        File otherModule = new File(root, 'module/other/content/video/vi/menu/unrelated.md')
        File readmeBase = new File(module, 'content/readme/docs/vi/BASE.md')
        for (File preserved : [english, otherModule, readmeBase]) {
            assertTrue(preserved.parentFile.mkdirs())
            preserved.setText('preserve', 'UTF-8')
        }
        File menu = new File(module, 'content/video/vi/menu')
        File stale = new File(menu, 'old/nested/stale.md')
        assertTrue(stale.parentFile.mkdirs())
        stale.setText('obsolete', 'UTF-8')
        Map<String, String> expected = ['1.Class/Class.md': '# New video\n']

        assertEquals('DRY_RUN', SheetSnapshotReplaceService.replace(
                root, menu, expected, false).status)
        assertTrue(stale.isFile())
        Map updated = SheetSnapshotReplaceService.replace(
                root, menu, expected, true)
        assertEquals('UPDATED', updated.status)
        assertEquals(1, updated.clearedOldFiles)
        assertFalse(stale.exists())
        assertEquals(expected, SheetSnapshotReplaceService.readTree(menu))
        assertEquals('UNCHANGED', SheetSnapshotReplaceService.replace(
                root, menu, expected, true).status)
        assertEquals(expected, SheetSnapshotReplaceService.readTree(menu))

        for (File preserved : [english, otherModule, readmeBase]) {
            assertEquals('preserve', preserved.getText('UTF-8'))
        }
        assertFalse(new File(root, '.gradle/sheet-video-backups').exists())
    }

    @Test
    void rejectsInvalidTargetAndInvalidSnapshotWithoutDeletingAnything() {
        File root = temporary.newFolder('invalid')
        File protectedFile = new File(root, 'module/example/metadata/protected.txt')
        assertTrue(protectedFile.parentFile.mkdirs())
        protectedFile.setText('do not delete', 'UTF-8')
        assertFailure {
            SheetSnapshotReplaceService.replace(
                    root, protectedFile.parentFile,
                    ['safe.md': 'new'], true
            )
        }
        assertEquals('do not delete', protectedFile.getText('UTF-8'))

        File video = new File(root, 'module/example/content/video/vi/menu')
        File authored = new File(video, 'keep.md')
        assertTrue(authored.parentFile.mkdirs())
        authored.setText('old script', 'UTF-8')
        for (String path in ['../escape.md', '/absolute.md', 'one\\two.md']) {
            assertFailure {
                SheetSnapshotReplaceService.replace(
                        root, video, [(path): 'new script'], true
                )
            }
        }
        assertEquals('old script', authored.getText('UTF-8'))
    }

    @Test
    void forbidsReplacingEntireReadmeMenuButAllowsKnowledgeOnly() {
        File root = temporary.newFolder('knowledge-ownership')
        File chapter = new File(root, 'module/example/content/readme/docs/vi/menu/1.Class')
        File knowledge = new File(chapter, 'knowledge')
        assertTrue(knowledge.mkdirs())
        File links = new File(chapter, 'chapter-links.md')
        links.setText('Independent Markdown', 'UTF-8')
        File objectives = new File(chapter, 'lesson-objectives.yml')
        objectives.setText('Independent YAML', 'UTF-8')
        File stale = new File(knowledge, 'Old.md')
        stale.setText('Old Knowledge', 'UTF-8')

        assertFailure {
            SheetSnapshotReplaceService.replace(root, chapter.parentFile,
                    ['1.Class/knowledge/Class.md': '# New'], true)
        }
        assertTrue(stale.isFile())
        Map result = SheetSnapshotReplaceService.replace(root, knowledge,
                ['Class.md': '# New Knowledge'], true)
        assertEquals('UPDATED', result.status)
        assertFalse(stale.exists())
        assertEquals('# New Knowledge', new File(knowledge, 'Class.md').getText('UTF-8'))
        assertEquals('Independent Markdown', links.getText('UTF-8'))
        assertEquals('Independent YAML', objectives.getText('UTF-8'))
    }

    @Test
    void legacyModuleWideQuizAndInterviewViAreOwnedButOtherLanguagesAreNot() {
        File root = temporary.newFolder('legacy-question-protection')
        ['quiz', 'interview'].each { String kind ->
            File directory = new File(root, "module/example/content/${kind}/vi")
            assertTrue(directory.mkdirs())
            File source = new File(directory, 'question.yml')
            source.setText('questions: []', 'UTF-8')
            File stale = new File(directory, 'old-questions.yml')
            stale.setText('STALE', 'UTF-8')
            File english = new File(root, "module/example/content/${kind}/en/question.yml")
            assertTrue(english.parentFile.mkdirs())
            english.setText('EN', 'UTF-8')
            assertEquals('CLEARED_EMPTY', SheetSnapshotReplaceService.replace(
                    root, directory, [:], true).status)
            assertFalse(source.exists())
            assertFalse(stale.exists())
            assertEquals('EN', english.getText('UTF-8'))
        }
    }

    @Test
    void cleanAllKindsBeforeFillAndRollbackEveryDirectoryAfterInjectedFailure() {
        File root = temporary.newFolder('multi-snapshot')
        File chapter = new File(root, 'module/example/content/readme/docs/vi/menu/1.Basic')
        Map<File, Map<String, String>> next = new LinkedHashMap<>()
        ['knowledge', 'metadata', 'interview', 'quiz', 'video'].each { String kind ->
            File folder = new File(chapter, kind)
            assertTrue(folder.mkdirs())
            new File(folder, 'stale.txt').setText('OLD-' + kind, 'UTF-8')
            String file = kind == 'knowledge' ? 'Basic.md' :
                    kind == 'metadata' ? 'knowledge-metadata.yml' :
                    kind == 'video' ? 'video-script.yml' : 'question.yml'
            next[folder] = [(file): 'NEW-' + kind]
        }
        File unaffected = new File(chapter, 'chapter-links.md')
        unaffected.setText('PRESERVE', 'UTF-8')
        assertEquals('DRY_RUN', SheetSnapshotReplaceService.replaceAll(root, next, false).status)
        try {
            SheetSnapshotReplaceService.replaceAll(root, next, true) { String phase, File folder ->
                if (phase == 'FILL' && folder.name == 'metadata') {
                    throw new IOException('Simulated failure after some clean/fill operations')
                }
            }
            fail('Expected rollback')
        } catch (GradleException expected) {
            assertTrue(expected.message.contains('Unable to clean/fill'))
        }
        next.keySet().each { File folder ->
            assertEquals(['stale.txt': 'OLD-' + folder.name], SheetSnapshotReplaceService.readTree(folder))
        }
        assertEquals('PRESERVE', unaffected.getText('UTF-8'))
        assertEquals('UPDATED', SheetSnapshotReplaceService.replaceAll(root, next, true).status)
        next.each { File folder, Map<String, String> expected ->
            assertEquals(expected, SheetSnapshotReplaceService.readTree(folder))
        }
        assertEquals('PRESERVE', unaffected.getText('UTF-8'))
        assertEquals('UNCHANGED', SheetSnapshotReplaceService.replaceAll(root, next, true).status)
    }

    private static void assertFailure(Closure action) {
        try {
            action.call()
            fail('Expected GradleException')
        }
        catch (GradleException ignored) {
            // Validation takes place before deleting the source tree.
        }
    }
}
