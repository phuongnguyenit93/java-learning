package com.example.learning.task.sheets.service

import com.example.learning.utils.schema.validate.VideoYamlContentUtils
import org.gradle.api.GradleException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.junit.Assert.*

class VideoSheetImportServiceTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder()

    static final String SCRIPT = '''title: "Tư duy OOP"
video:
  url: ""
sections:
  - title: "Khái niệm OOP"
    items:
      - type: SCENE
        title: "Bắt đầu"
        time: "00:00–00:30"
        visual: |-
          Sơ đồ trạng thái.
        script: |-
          Lời thoại đầy đủ.
        purpose: |-
          Giới thiệu chương.
  - title: "Lợi ích"
    items:
      - type: TRANSITION
        time: "00:30–00:40"
        visual: |-
          Sơ đồ chuyển tiếp.
        script: |-
          Câu kết nối.
        purpose: |-
          Nối hai mục.
      - type: SCENE
        title: "Tiếp tục"
        time: "00:40–01:00"
        visual: |-
          Ví dụ Java.
        script: |-
          Giải thích tiếp.
        purpose: |-
          Hoàn thành bài.
'''

    private static List<Map> groups() {
        return [
                [title: 'Tư duy OOP', filename: 'OOPBasic.md', parentRow: 20,
                 children: [[title: 'Khái niệm OOP'], [title: 'Lợi ích']]],
                [title: 'Lớp', filename: 'Class.md', parentRow: 26,
                 children: [[title: 'Cấu trúc lớp']]]
        ]
    }

    private static List<List<Object>> rows(List<Object> first = []) {
        List<List<Object>> result = [['Video Script', 'Video Script', 'Video Script', 'Video Script']]
        result.add(first)
        (0..<5).each { result.add([]) }
        return result
    }

    @Test
    void mapsActualParentRowNotGroupOrdinalAndPreservesExactYamlFragments() {
        assertEquals([:], VideoSheetImportService.parseSheet(groups(), rows()))
        Map<String, String> scripts = VideoSheetImportService.parseSheet(groups(), rows([
                SCRIPT.substring(0, 91), SCRIPT.substring(91)
        ]))
        assertEquals(['1.OOPBasic'], scripts.keySet().toList())
        assertEquals(SCRIPT, scripts.values().first())
        assertEquals([sections: 2, scenes: 2, transitions: 1],
                VideoSheetImportService.validateVideo(
                        SCRIPT, 'Tư duy OOP', ['Khái niệm OOP', 'Lợi ích']))
        assertEquals('OOPBasic', VideoYamlContentUtils.categoryId(
                new File('1.OOPBasic/video/video-script.yml')))
    }

    @Test
    void rejectsMalformedYamlWrongParentTitleOrSectionTimeBeforeWrites() {
        shouldFail { VideoSheetImportService.parseSheet(groups(),
                [['old header'], [SCRIPT]]) }
        shouldFail { VideoSheetImportService.parseSheet(groups(), rows([
                SCRIPT.substring(0, 15), '', SCRIPT.substring(15)
        ])) }
        shouldFail { VideoSheetImportService.parseSheet(groups(), [
                ['Video Script', 'Video Script', 'Video Script', 'Video Script'],
                [SCRIPT], ['orphan content']
        ]) }
        shouldFail { VideoSheetImportService.parseSheet(groups(), rows([
                SCRIPT.replace('Tư duy OOP', 'Sai chủ đề')
        ])) }
        shouldFail { VideoSheetImportService.validateVideo(
                SCRIPT.replace('title: "Lợi ích"', 'title: "Sai"'),
                'Tư duy OOP', ['Khái niệm OOP', 'Lợi ích']) }
        shouldFail { VideoSheetImportService.validateVideo(
                SCRIPT.replace('00:40–01:00', '00:42–01:00'),
                'Tư duy OOP', ['Khái niệm OOP', 'Lợi ích']) }
        shouldFail { VideoSheetImportService.validateVideo(
                SCRIPT.replace('type: TRANSITION', 'type: SCENE'),
                'Tư duy OOP', ['Khái niệm OOP', 'Lợi ích']) }
        shouldFail { VideoSheetImportService.validateVideo(
                SCRIPT.replace('purpose: |-\n          Giới thiệu chương.',
                        'purpose: ""'),
                'Tư duy OOP', ['Khái niệm OOP', 'Lợi ích']) }
        shouldFail { VideoSheetImportService.validateVideo(
                'title: [invalid\n', 'Tư duy OOP', ['Khái niệm OOP', 'Lợi ích']) }
    }

    @Test
    void dryRunApplyRerunAndClearOnlyOwnedVideoFiles() {
        File root = temp.newFolder('new-video')
        File module = new File(root, 'module/example')
        File menu = new File(module, 'content/readme/docs/vi/menu')
        groups().eachWithIndex { Map group, int i ->
            assertTrue(new File(menu, "${i + 1}.${(group.filename as String)[0..-4]}/knowledge").mkdirs())
        }
        Map<String, String> source = ['1.OOPBasic': SCRIPT]
        assertEquals('DRY_RUN', VideoSheetImportService.sync(
                root, module, groups(), source, false).status)
        assertFalse(new File(menu, '1.OOPBasic/video').exists())
        assertEquals('IMPORTED', VideoSheetImportService.sync(
                root, module, groups(), source, true).status)
        File file = new File(menu, '1.OOPBasic/video/video-script.yml')
        assertEquals(SCRIPT, file.getText('UTF-8'))
        assertEquals('UNCHANGED', VideoSheetImportService.sync(
                root, module, groups(), source, true).status)
        assertEquals('CLEARED_EMPTY', VideoSheetImportService.sync(
                root, module, groups(), [:], true).status)
        assertFalse(file.exists())
        assertTrue(new File(menu, '1.OOPBasic/knowledge').isDirectory())
    }

    @Test
    void validVideoSchemaWithNoMenuCreatesEmptyAuthoritativeSnapshot() {
        assertEquals([:], VideoSheetImportService.parseSheet([], [
                ['Video Script', 'Video Script', 'Video Script', 'Video Script']]))
    }

    @Test
    void legacyOwnedFilesAreRemovedAfterChapterImportAndEnglishPreserved() {
        File root = temp.newFolder('legacy-video')
        File module = new File(root, 'module/example')
        File menu = new File(module, 'content/readme/docs/vi/menu')
        assertTrue(new File(menu, '1.OOPBasic/knowledge').mkdirs())
        File published = new File(menu, '1.OOPBasic/video/video-script.yml')
        File old = new File(module, 'content/video/vi/menu/1.OOPBasic/video-script.yml')
        assertTrue(old.parentFile.mkdirs())
        old.setText(SCRIPT.replace('url: ""', 'url: "https://old.example/video"'), 'UTF-8')
        File otherLanguage = new File(root, 'module/example/content/video/en/menu/1.OOPBasic/video-script.yml')
        assertTrue(otherLanguage.parentFile.mkdirs())
        otherLanguage.setText('preserve', 'UTF-8')
        Map<String, String> source = ['1.OOPBasic': SCRIPT]
        assertEquals('UPDATED', VideoSheetImportService.sync(
                root, module, groups(), source, true).status)
        assertEquals(SCRIPT, published.getText('UTF-8'))
        assertFalse(old.exists())
        assertEquals('preserve', otherLanguage.getText('UTF-8'))
        assertFalse(new File(root, '.gradle/sheet-video-backups').exists())
    }

    @Test
    void refusesInvalidYAMLDocumentShapeAndUnsafeCategory() {
        shouldFail { VideoYamlContentUtils.parse('sections: []') }
        shouldFail { VideoYamlContentUtils.parse(
                SCRIPT.replace('00:00–00:30', '00:60–00:99')) }
        shouldFail { VideoYamlContentUtils.parse(
                SCRIPT.replace('video:\n', 'metadata: true\nvideo:\n')) }
        shouldFail { VideoYamlContentUtils.categoryId(
                new File('nonordinal/video-script.yml')) }
    }

    private static void shouldFail(Closure closure) {
        try {
            closure.call()
            fail('Expected invalid Video YAML rejection')
        }
        catch (GradleException expected) {
            assertNotNull(expected.message)
        }
    }
}
