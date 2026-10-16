package com.example.learning.task.sheets.service

import com.example.learning.task.readme.internalMenu.service.GenerateInternalReadmeMenuService
import org.gradle.api.GradleException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.junit.Assert.*

class ReadmeSheetImportServiceTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder()

    @Test
    void requiresA20AndEveryParentFilenameBeforeReadingKnowledge() {
        assertEquals('SKIPPED_EMPTY_MENU', ReadmeSheetImportService.parseMenu([
                ['Menu Cấp 1', 'Menu Cấp 2'], []
        ]).status)
        assertEquals('SKIPPED_EMPTY_MENU', ReadmeSheetImportService.parseMenu([]).status)
        Map missing = ReadmeSheetImportService.parseMenu([
                ['Menu Cấp 1', 'Menu Cấp 2'],
                ['Tư duy OOP', 'OOPBasic.md'],
                ['', 'OOP là gì?'],
                ['Lớp', ''],
                ['', 'Class là gì?']
        ])
        assertEquals('SKIPPED_MISSING_FILENAME', missing.status)
        assertEquals([22], missing.missingRows)
        assertEquals(2, missing.groups.size())
        assertEquals(2, missing.lessons)
        assertEquals('SKIPPED_MISSING_FILENAME', ReadmeSheetImportService.parseMenu([
                ['Menu Cấp 1', 'Menu Cấp 2'],
                ['A', ''], ['', 'Một bài'],
                ['B', 'invalid-file-name.md'], ['', 'Hai bài']
        ]).status)

        expectFailure {
            ReadmeSheetImportService.parseMenu([
                    ['Menu Cấp 1', 'Menu Cấp 2'],
                    ['A', 'Class.md'], ['', 'One'],
                    ['B', 'class.md'], ['', 'Two']
            ])
        }
        expectFailure {
            ReadmeSheetImportService.parseMenu([
                    ['Menu Cấp 1', 'Menu Cấp 2'],
                    ['A', '../Injected.md'], ['', 'One']
            ])
        }
        expectFailure {
            ReadmeSheetImportService.parseMenu([
                    ['Menu Cấp 1', 'Menu Cấp 2'],
                    ['A', 'HTTPServer.md'], ['', 'One'],
                    ['B', 'HttpServer.md'], ['', 'Two']
            ])
        }
    }

    @Test
    void generatesPascalCaseAnchorsAndKeepsEmptyAnswers() {
        Map menu = ReadmeSheetImportService.parseMenu([
                ['Menu Cấp 1', 'Menu Cấp 2'],
                ['Tư duy OOP', 'OOPBasic.md'],
                ['', 'OOP là gì?'],
                ['', 'Đối tượng là gì?'],
                ['', 'Chưa có bài'],
                ['Lớp', 'Class.md'],
                ['', 'Cấu trúc lớp']
        ])
        assertEquals('READY', menu.status)
        // Knowledge rows begin at row 20; the first/parent rows are empty.
        String exactAnswer = '# OOP là gì?\n\nNội dung ở D21.\n\n\n```md\n## <a id="fake">Demo anchor</a>\n```'
        List<List<Object>> knowledge = [
                [], ['# OOP là gì?\n\nNội', ' dung ở D21.\n\n\n```md\n## <a id="fake">Demo anchor</a>\n```'],
                ['**Object** là gì?'], [],
                [], ['Ví dụ Java']
        ]
        Map<String, String> docs = ReadmeSheetImportService.buildDocuments(
                menu.groups as List<Map<String, Object>>, knowledge
        )
        assertEquals(['1.OOPBasic/OOPBasic.md', '2.Class/Class.md'], docs.keySet().toList())
        String oop = docs['1.OOPBasic/OOPBasic.md']
        assertTrue(oop.contains('id="oop-basic-001"'))
        assertTrue(oop.contains('id="oop-basic-002"'))
        assertTrue(oop.contains('id="oop-basic-003"'))
        assertTrue(oop.contains(exactAnswer))
        assertEquals(1, oop.count('id="oop-basic-001"'))
        assertTrue(oop.contains('<summary>Xem chi tiết</summary>'))
        assertTrue(oop.contains('## Menu'))
        assertTrue(oop.contains('- [Chưa có bài](#oop-basic-003)'))
        assertTrue(docs['2.Class/Class.md'].contains('id="class-001"'))
        assertEquals(docs, ReadmeSheetImportService.buildDocuments(
                menu.groups as List<Map<String, Object>>, knowledge
        ))

        assertEquals('http-server', ReadmeSheetImportService.slugFromStem('HTTPServer'))
        assertEquals('xml-http-request', ReadmeSheetImportService.slugFromStem('XMLHttpRequest'))
        assertEquals('class-and-object', ReadmeSheetImportService.slugFromStem('ClassAndObject'))
    }

    @Test
    void rejectsNonContiguousKnowledgeFragments() {
        Map menu = ReadmeSheetImportService.parseMenu([
                ['Menu Cấp 1', 'Menu Cấp 2'],
                ['Lớp', 'Class.md'], ['', 'Một bài']
        ])
        expectFailure {
            ReadmeSheetImportService.buildDocuments(
                    menu.groups as List<Map<String, Object>>,
                    [[], ['C', '', 'E']]
            )
        }
    }

    @Test
    void dryRunThenApplyPermanentlyDeletesOldMenuBeforeReplacement() {
        File root = temporary.newFolder('repo')
        File menu = ReadmeSheetImportService.resolveMenuDirectory(root, 'module/example')
        File legacy = new File(menu, '1.Legacy/Legacy.md')
        assertTrue(legacy.parentFile.mkdirs())
        legacy.setText('Old README content', 'UTF-8')
        File nested = new File(menu, '1.Legacy/subfolder/Orphan.md')
        assertTrue(nested.parentFile.mkdirs())
        nested.setText('stale nested content', 'UTF-8')
        File unrelated = new File(menu, 'readme-generated.tmp')
        unrelated.setText('stale temporary content', 'UTF-8')
        Map<String, String> next = [
                '1.OOPBasic/OOPBasic.md': '# Tư duy OOP\n\n## <a id="oop-basic-001">Lesson</a>\n'
        ]
        assertEquals('DRY_RUN', ReadmeSheetImportService.syncDocuments(root, menu, next, false).status)
        assertTrue(legacy.isFile())
        assertTrue(nested.isFile())
        assertTrue(unrelated.isFile())
        assertFalse(new File(root, '.gradle/sheet-readme-backups').exists())

        Map result = ReadmeSheetImportService.syncDocuments(root, menu, next, true)
        assertEquals('UPDATED', result.status)
        assertEquals(3, result.clearedOldFiles)
        assertEquals(next, ReadmeSheetImportService.readTree(menu))
        assertFalse(legacy.exists())
        assertFalse(nested.exists())
        assertFalse(unrelated.exists())
        assertEquals(['1.OOPBasic'], menu.list().toList())
        assertFalse(new File(root, '.gradle/sheet-readme-backups').exists())
        assertEquals('UNCHANGED', ReadmeSheetImportService.syncDocuments(root, menu, next, true).status)
        assertEquals(next, ReadmeSheetImportService.readTree(menu))

        Map<String, String> revised = ['1.Class/Class.md': 'New lesson']
        Map updated = ReadmeSheetImportService.syncDocuments(root, menu, revised, true)
        assertEquals('UPDATED', updated.status)
        assertEquals(1, updated.clearedOldFiles)
        assertEquals(revised, ReadmeSheetImportService.readTree(menu))
        assertFalse(new File(root, '.gradle/sheet-readme-backups').exists())
    }

    @Test
    void refusesToClearMenuWithoutReplacementSnapshot() {
        File root = temporary.newFolder('empty-snapshot')
        File menu = ReadmeSheetImportService.resolveMenuDirectory(root, 'module/example')
        File old = new File(menu, '1.Old/Old.md')
        assertTrue(old.parentFile.mkdirs())
        old.setText('Original remains intact', 'UTF-8')
        expectFailure {
            ReadmeSheetImportService.syncDocuments(root, menu, [:], true)
        }
        assertEquals('Original remains intact', old.getText('UTF-8'))
    }

    @Test
    void refusesUnsafeMenuDeletionTarget() {
        File root = temporary.newFolder('unsafe-delete')
        File unrelated = new File(root, 'not-a-menu')
        assertTrue(unrelated.mkdirs())
        File data = new File(unrelated, 'important.txt')
        data.setText('keep', 'UTF-8')
        expectFailure {
            ReadmeSheetImportService.syncDocuments(
                    root, unrelated, ['1.Class/Class.md': 'new'], true
            )
        }
        assertEquals('keep', data.getText('UTF-8'))
    }

    @Test
    void newInternalMenuRendererMatchesLegacyFileGenerator() {
        File root = temporary.newFolder('renderer')
        File readme = new File(root, 'readme/vi/menu/1.Class/Class.md')
        assertTrue(readme.parentFile.mkdirs())
        String raw = '# Lớp\n\n## <a id="class-001">Lớp là gì?</a>\n\nNội dung\n'
        readme.setText(raw, 'UTF-8')
        GenerateInternalReadmeMenuService service = new GenerateInternalReadmeMenuService()
        String rendered = service.render(raw, 'vi')
        service.generate(readme)
        assertEquals(rendered, readme.getText('UTF-8'))
        assertEquals(rendered, service.render(rendered, 'vi'))
    }

    private static void expectFailure(Closure action) {
        try {
            action.call()
            fail('Expected GradleException')
        }
        catch (GradleException expected) {
            // Invalid data must not be imported.
        }
    }
}
