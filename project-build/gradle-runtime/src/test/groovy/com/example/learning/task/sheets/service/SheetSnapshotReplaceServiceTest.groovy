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
        File english = new File(module, 'video/en/menu/1.Class/Class.md')
        File otherModule = new File(root, 'module/other/video/vi/menu/unrelated.md')
        File readmeBase = new File(module, 'readme/vi/BASE.md')
        for (File preserved : [english, otherModule, readmeBase]) {
            assertTrue(preserved.parentFile.mkdirs())
            preserved.setText('preserve', 'UTF-8')
        }
        File menu = new File(module, 'video/vi/menu')
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

        File video = new File(root, 'module/example/video/vi/menu')
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
