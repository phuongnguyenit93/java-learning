package com.example.learning.task.sheets.service

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.gradle.api.GradleException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.junit.Assert.*

class ModuleContentExcelSyncServiceTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder()

    @Test
    void reversesAllPartsIntoSingleEnglishWorkbookWithoutChangingVietnameseBaseline() {
        Map f = fixture()
        File source = f.source as File
        byte[] original = source.bytes
        def service = new ModuleContentExcelSyncService()
        Map dry = service.execute(f.root as File, f.module as File, 'ALL', false)
        assertEquals('DRY_RUN', dry.status)
        assertEquals(1, dry.chapters)
        assertFalse((f.target as File).exists())
        assertArrayEquals(original, source.bytes)

        Map applied = service.execute(f.root as File, f.module as File, 'ALL', true)
        assertEquals('EXPORTED', applied.status)
        assertTrue((f.target as File).isFile())
        assertArrayEquals(original, source.bytes)
        (f.target as File).withInputStream { input ->
            new XSSFWorkbook(input).withCloseable { book ->
                assertEquals(1, book.numberOfSheets)
                assertEquals('Demo EN', book.getSheetAt(0).sheetName)
                def sheet = book.getSheetAt(0)
                assertEquals('module/demo', ModuleContentExcelSyncService.text(sheet, 3, 9))
                assertEquals('# English introduction', ModuleContentExcelSyncService.text(sheet, 17, 2))
                assertEquals('English chapter', ModuleContentExcelSyncService.text(sheet, 20, 1))
                assertEquals('English lesson', ModuleContentExcelSyncService.text(sheet, 21, 2))
                assertEquals('English body', ModuleContentExcelSyncService.text(sheet, 21, 3))
                assertEquals('ADVANCED', ModuleContentExcelSyncService.text(sheet, 21, 15))
                assertTrue(sheet.getRow(20).getCell(27).booleanCellValue)
                assertEquals('English roadmap', ModuleContentExcelSyncService.text(sheet, 3, 6))
                assertEquals('Understand the example', ModuleContentExcelSyncService.text(sheet, 3, 7))
                assertTrue(ModuleContentExcelSyncService.text(sheet, 20, 16).contains('English interview?'))
                assertTrue(ModuleContentExcelSyncService.text(sheet, 20, 20).contains('English quiz?'))
                assertEquals('', ModuleContentExcelSyncService.text(sheet, 20, 24))
                assertEquals('unchanged background', ModuleContentExcelSyncService.text(sheet, 1, 1))
            }
        }
        // Video must be opt-in; the standalone VIDEO part still works later.
        assertEquals('EXPORTED', service.execute(
                f.root as File, f.module as File, 'VIDEO', true).status)
        (f.target as File).withInputStream { input ->
            new XSSFWorkbook(input).withCloseable { book ->
                assertTrue(ModuleContentExcelSyncService.text(
                        book.getSheetAt(0), 20, 24).contains('English video script'))
            }
        }
        Map replay = service.execute(f.root as File, f.module as File, 'QUIZ', false)
        assertEquals('DRY_RUN', replay.status)
        assertEquals(0, replay.changedCells)
        Map allReplay = service.execute(f.root as File, f.module as File, 'ALL', false)
        assertEquals('DRY_RUN', allReplay.status)
        assertEquals(0, allReplay.changedCells)
    }

    @Test
    void refusesAmbiguousRelationsBeforeReplacingExistingWorkbook() {
        Map f = fixture()
        def service = new ModuleContentExcelSyncService()
        service.execute(f.root as File, f.module as File, 'ALL', true)
        byte[] original = (f.target as File).bytes
        File quiz = new File(f.module as File, 'content/quiz/en/question.yml')
        quiz.setText(quiz.getText('UTF-8')
                .replace('1.Basic/knowledge/Basic.md', '99.Wrong/knowledge/Wrong.md'), 'UTF-8')
        try {
            service.execute(f.root as File, f.module as File, 'QUIZ', true)
            fail('Expected unknown chapter link failure')
        } catch (GradleException expected) {
            assertTrue(expected.message.contains('Cannot assign quiz'))
        }
        assertArrayEquals(original, (f.target as File).bytes)
    }

    @Test
    void requiresExpectedAnchorAndKeepsExistingWorkbookIntact() {
        Map f = fixture()
        def service = new ModuleContentExcelSyncService()
        service.execute(f.root as File, f.module as File, 'ALL', true)
        byte[] old = (f.target as File).bytes
        File readme = new File(f.module as File,
                'content/readme/docs/en/menu/1.Basic/knowledge/Basic.md')
        readme.setText(readme.getText('UTF-8').replace('basic-001', 'changed-001'), 'UTF-8')
        try {
            service.execute(f.root as File, f.module as File, 'README', true)
            fail('Expected anchor identity failure')
        } catch (GradleException expected) {
            assertTrue(expected.message.contains('anchor mismatch'))
        }
        assertArrayEquals(old, (f.target as File).bytes)
    }

    @Test
    void doesNotTreatOtherChapterMarkdownAsKnowledge() {
        Map f = fixture()
        File module = f.module as File
        write(module, 'content/readme/docs/en/menu/1.Basic/chapter-links.md',
                '# Auxiliary chapter links without Knowledge anchors')
        write(module, 'content/readme/docs/en/menu/1.Basic/Basic.md',
                '# Old location should not be used')

        Map result = new ModuleContentExcelSyncService().execute(
                f.root as File, module, 'ALL', true)
        assertEquals('EXPORTED', result.status)
        (f.target as File).withInputStream { input ->
            new XSSFWorkbook(input).withCloseable { book ->
                assertEquals('English body',
                        ModuleContentExcelSyncService.text(book.getSheetAt(0), 21, 3))
            }
        }
    }

    @Test
    void chunksUnicodeWithoutBreakingSurrogatePairs() {
        String emoji = '\uD83D\uDE80'
        String payload = ('A' * 29999) + emoji + 'end'
        List<String> pieces = ModuleContentExcelSyncService.split(payload, 4)
        assertEquals(payload, pieces.join(''))
        assertEquals(30000, pieces[0].codePointCount(0, pieces[0].length()))
        assertEquals(4, pieces.size())
        try {
            ModuleContentExcelSyncService.split('x' * 120001, 4)
            fail('Expected cell fragment limit')
        } catch (GradleException expected) {
            assertTrue(expected.message.contains('exceeds'))
        }
    }

    private Map fixture() {
        File root = tmp.newFolder('repo')
        File module = new File(root, 'module/demo')
        assertTrue(module.mkdirs())
        new File(module, 'gradle.properties').text = '# fixture'
        File source = new File(module, 'content/sheet/vi/module.xlsx')
        assertTrue(source.parentFile.mkdirs())
        new XSSFWorkbook().withCloseable { book ->
            def sheet = book.createSheet('Demo')
            cell(sheet, 1, 1, 'unchanged background')
            cell(sheet, 3, 9, 'module/demo')
            cell(sheet, 19, 1, 'Menu Cấp 1')
            cell(sheet, 19, 2, 'Menu Cấp 2')
            cell(sheet, 20, 1, 'Chương tiếng Việt')
            cell(sheet, 20, 2, 'Basic.md')
            cell(sheet, 21, 2, 'Bài tiếng Việt')
            cell(sheet, 3, 6, 'Roadmap VI')
            cell(sheet, 3, 7, 'Purpose VI')
            cell(sheet, 3, 8, 'Objective VI')
            source.withOutputStream { book.write(it) }
        }
        write(module, 'content/readme/docs/en/BASE.md', '# English introduction')
        write(module, 'content/readme/docs/en/menu/1.Basic/knowledge/Basic.md', '''
<a id="back-to-top"></a>

# English chapter

## Menu
- [English lesson](#basic-001)

## <a id="basic-001">English lesson</a>

<details>
<summary>Click for details</summary>

English body

</details>

- [Back to top](#back-to-top)
---
'''.trim() + '\n')
        write(module, 'content/readme/metadata/en/knowledge-metadata.yml', '''
1.Basic/knowledge/Basic.md:
  basic-001:
    difficulty: ADVANCED
    isImportant: true
'''.trim())
        write(module, 'content/roadmap/en/roadmap.yml', '''
roadmap:
  - id: roadmap-stable
    title: English roadmap
    purpose: Understand the example
    objectives: [Objective A, Objective B]
    relatedKnowledge: [Basic]
'''.trim())
        write(module, 'content/interview/en/question.yml', '''
questions:
  - question: English interview?
    answer: The answer
    readmeRelated: {file: "1.Basic/knowledge/Basic.md", anchor: "basic-001"}
'''.trim())
        write(module, 'content/quiz/en/question.yml', '''
questions:
  - id: quiz-001
    question: English quiz?
    answers:
      - {id: A, answer: Yes, explanation: correct}
      - {id: B, answer: No, explanation: incorrect}
      - {id: C, answer: Maybe, explanation: incorrect}
      - {id: D, answer: None, explanation: incorrect}
    correctAnswerId: A
    readmeRelated: {file: "1.Basic/knowledge/Basic.md", anchor: "basic-001"}
'''.trim())
        write(module, 'content/video/en/menu/1.Basic/video-script.yml', '''
title: English video script
sections:
  - title: English lesson
'''.trim())
        return [root: root, module: module, source: source,
                target: new File(module, 'content/sheet/en/module.xlsx')]
    }

    private static void write(File root, String relative, String contents) {
        File output = new File(root, relative)
        assertTrue(output.parentFile.isDirectory() || output.parentFile.mkdirs())
        output.setText(contents, 'UTF-8')
    }

    private static void cell(def sheet, int row, int column, String value) {
        def r = sheet.getRow(row - 1) ?: sheet.createRow(row - 1)
        (r.getCell(column - 1) ?: r.createCell(column - 1)).setCellValue(value)
    }
}
