package com.example.learning.task.sheets.service

import org.gradle.api.GradleException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.junit.Assert.*

class VideoSheetImportServiceTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder()

    private static final String SCRIPT = '''---
video:
  url: ""
---

# Tư duy OOP

## Khái niệm OOP

<!-- VIDEO_SECTION -->

### Scene 1 — Bắt đầu

**Time:** 00:00–00:30

**Visual:**

Sơ đồ trạng thái.

**Script:**

Lời thoại đầy đủ.

**Purpose:**

Giới thiệu chương.

## Lợi ích

<!-- VIDEO_SECTION -->

### Transition

**Time:** 00:30–00:40

**Visual:**

Sơ đồ chuyển tiếp.

**Script:**

Câu kết nối.

**Purpose:**

Nối hai mục.

### Scene 1 — Tiếp tục

**Time:** 00:40–01:00

**Visual:**

Ví dụ Java.

**Script:**

Giải thích tiếp.

**Purpose:**

Hoàn thành bài.
'''

    private static List<Map> groups() {
        return [
                [title: 'Tư duy OOP', filename: 'OOPBasic.md',
                 children: [[title: 'Khái niệm OOP'], [title: 'Lợi ích']]],
                [title: 'Lớp', filename: 'Class.md',
                 children: [[title: 'Cấu trúc lớp']]]
        ]
    }

    private static List<List<Object>> rows(
            String firstScript, List<Object> firstParts = null
    ) {
        List<Object> fragments = firstParts ?: (firstScript ? [firstScript] : [])
        return [
                ['Video'], ['Chủ đề', 'Video Script', 'Video Script', 'Video Script', 'Video Script'],
                ['Tư duy OOP'] + fragments,
                ['Lớp']
        ]
    }

    @Test
    void mapsExactlyOneParentToOneVideoAndPreservesChunks() {
        assertEquals([:], VideoSheetImportService.parseSheet(groups(), rows('')))
        List<List<Object>> sheet = rows('ignored', [
                SCRIPT.substring(0, 99), SCRIPT.substring(99)
        ])
        Map<String, String> scripts = VideoSheetImportService.parseSheet(groups(), sheet)
        assertEquals(['1.OOPBasic/OOPBasic.md'], scripts.keySet().toList())
        assertEquals(SCRIPT, scripts.values().first())
        assertEquals([sections: 2, scenes: 2, transitions: 1],
                VideoSheetImportService.validateVideo(
                        SCRIPT, 'Tư duy OOP', ['Khái niệm OOP', 'Lợi ích']))
    }

    @Test
    void rejectsMalformedSheetAndVideoBeforeAnyWrite() {
        expectFailure {
            VideoSheetImportService.parseSheet(groups(), rows('ignored', [
                    SCRIPT.substring(0, 10), '', SCRIPT.substring(10)
            ]))
        }
        expectFailure {
            VideoSheetImportService.parseSheet(groups(), [
                    ['Video'], ['Chủ đề', 'Video Script', 'Video Script', 'Video Script', 'Video Script'],
                    ['Sai chủ đề', SCRIPT], ['Lớp']
            ])
        }
        expectFailure {
            VideoSheetImportService.validateVideo(
                    SCRIPT.replace('## Lợi ích', '## Chủ đề khác'),
                    'Tư duy OOP', ['Khái niệm OOP', 'Lợi ích'])
        }
        expectFailure {
            VideoSheetImportService.validateVideo(
                    SCRIPT.replace('00:40–01:00', '00:42–01:00'),
                    'Tư duy OOP', ['Khái niệm OOP', 'Lợi ích'])
        }
        expectFailure {
            VideoSheetImportService.validateVideo(
                    SCRIPT.replace('**Purpose:**', '**Missing:**'),
                    'Tư duy OOP', ['Khái niệm OOP', 'Lợi ích'])
        }
    }

    @Test
    void dryRunApplyAndRerunCleanEntireVideoMenu() {
        File root = temp.newFolder('new-video')
        File videos = new File(root, 'module/example/video/vi/menu')
        Map<String, String> source = ['1.OOPBasic/OOPBasic.md': SCRIPT]
        Map dry = VideoSheetImportService.sync(videos, groups(), source, false, root)
        assertEquals('DRY_RUN', dry.status)
        assertEquals(1, dry.pendingVideos)
        assertEquals(1, dry.skippedEmptyVideos)
        assertFalse(videos.exists())
        Map apply = VideoSheetImportService.sync(videos, groups(), source, true, root)
        assertEquals('IMPORTED', apply.status)
        File file = new File(videos, '1.OOPBasic/OOPBasic.md')
        assertEquals(SCRIPT, file.getText('UTF-8'))
        assertFalse(new File(videos, '2.Class/Class.md').exists())
        assertEquals('UNCHANGED',
                VideoSheetImportService.sync(videos, groups(), source, true, root).status)
        assertEquals('CLEARED_EMPTY',
                VideoSheetImportService.sync(videos, groups(), [:], true, root).status)
        assertFalse(file.exists())
        assertEquals([:], SheetSnapshotReplaceService.readTree(videos))
    }

    @Test
    void fullSnapshotDiscardsSkeletonAndPreviouslyPublishedUrl() {
        File root = temp.newFolder('skeleton')
        File menu = new File(root, 'module/example/video/vi/menu')
        File file = new File(menu, '1.OOPBasic/OOPBasic.md')
        assertTrue(file.parentFile.mkdirs())
        String skeleton = '''---
video:
  url: "https://example.org/recording"
---

# Tư duy OOP

<!--
VIDEO SCRIPT FORMAT
Scene, Time, Visual, Script, Purpose.
-->

## Khái niệm OOP

<!-- VIDEO_SECTION -->

## Lợi ích

<!-- VIDEO_SECTION -->
'''
        file.setText(skeleton, 'UTF-8')
        Map source = ['1.OOPBasic/OOPBasic.md': SCRIPT]
        assertEquals('DRY_RUN',
                VideoSheetImportService.sync(menu, groups(), source, false, root).status)
        assertEquals(skeleton, file.getText('UTF-8'))
        assertEquals('UPDATED',
                VideoSheetImportService.sync(menu, groups(), source, true, root).status)
        String imported = file.getText('UTF-8')
        assertEquals(SCRIPT, imported)
        assertFalse(imported.contains('https://example.org/recording'))
        assertFalse(imported.contains('VIDEO SCRIPT FORMAT'))
        assertTrue(imported.contains('### Transition'))
        assertEquals('UNCHANGED',
                VideoSheetImportService.sync(menu, groups(), source, true, root).status)
        assertEquals(imported, file.getText('UTF-8'))
    }

    @Test
    void authoredConflictsAndOrphansAreDeletedWithoutBackup() {
        File root = temp.newFolder('conflict')
        File menu = new File(root, 'module/example/video/vi/menu')
        File file = new File(menu, '1.OOPBasic/OOPBasic.md')
        assertTrue(file.parentFile.mkdirs())
        String authored = SCRIPT.replace('Lời thoại đầy đủ.', 'Lời thoại đã sửa.')
        file.setText(authored, 'UTF-8')
        File orphan = new File(menu, 'legacy/obsolete.md')
        assertTrue(orphan.parentFile.mkdirs())
        orphan.setText('stale', 'UTF-8')
        assertEquals('DRY_RUN', VideoSheetImportService.sync(
                menu, groups(), ['1.OOPBasic/OOPBasic.md': SCRIPT], false, root).status)
        assertEquals(authored, file.getText('UTF-8'))
        assertEquals('UPDATED', VideoSheetImportService.sync(
                menu, groups(), ['1.OOPBasic/OOPBasic.md': SCRIPT], true, root).status)
        assertEquals(SCRIPT, file.getText('UTF-8'))
        assertFalse(orphan.exists())
        assertFalse(new File(root, '.gradle/sheet-video-backups').exists())
    }

    private static void expectFailure(Closure action) {
        try {
            action.call()
            fail('Expected deterministic GradleException')
        }
        catch (GradleException ignored) {
            // Reject invalid schema, content or destination with no write.
        }
    }
}
