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
    void exportsReferenceAndTranslatedSummaryWithoutChangingVietnameseExcel() {
        Map f = fixture()
        File module = f.module as File
        File viWorkbook = f.source as File
        new XSSFWorkbook(new ByteArrayInputStream(viWorkbook.bytes)).withCloseable { book ->
            def s = book.getSheetAt(0)
            cell(s, 2, 12, 'Reference')
            cell(s, 3, 12, 'references: []')
            cell(s, 18, 29, 'Summary')
            cell(s, 19, 29, 'Summary YAML')
            cell(s, 20, 29, '# VI section')
            cell(s, 21, 29, '## VI lesson')
            viWorkbook.withOutputStream { book.write(it) }
        }
        byte[] sourceBefore = viWorkbook.bytes
        write(module, 'content/reference/references.yml', '''references:
  - title: Language Specification
    url: https://docs.oracle.com/javase/specs/
''')
        String structured = new org.yaml.snakeyaml.Yaml().dump([
                version: 1, type: 'LEARNING_ROADMAP', title: 'English section',
                roadmapHeading: 'Learning flow',
                roadmap: [[id: 'basic-001', order: 1, title: 'Basic',
                           description: 'Understand the basics.', transition: null]],
                comparisonMarkdown: '''## 2. Comparison

| Criteria | Bài 1 |
| --- | --- |
| Skills | Basics |
| Example | Account |
| Scope | Java |
| Verify | Object |'''
        ])
        write(module, 'content/readme/docs/vi/menu/1.Basic/summary/section.yml', structured)
        write(module, 'content/readme/docs/vi/menu/1.Basic/summary/summaries.yml',
                'summaries:\n  - {id: basic-001, title: VN, summary: Lesson VI}\n')
        write(module, 'content/readme/docs/en/menu/1.Basic/summary/section.yml', structured)
        write(module, 'content/readme/docs/en/menu/1.Basic/summary/summaries.yml',
                'summaries:\n  - {id: basic-001, title: English, summary: Lesson EN}\n')
        Map result = new ModuleContentExcelSyncService().execute(
                f.root as File, module, 'ALL', true)
        assertEquals('EXPORTED', result.status)
        assertArrayEquals(sourceBefore, viWorkbook.bytes)
        (f.target as File).withInputStream { input ->
            new XSSFWorkbook(input).withCloseable { book ->
                def s = book.getSheetAt(0)
                assertTrue(ModuleContentExcelSyncService.text(s, 3, 12)
                        .contains('Language Specification'))
                assertEquals('Summary', ModuleContentExcelSyncService.text(s, 18, 29))
                assertEquals('Summary YAML', ModuleContentExcelSyncService.text(s, 19, 29))
                assertEquals(structured, ModuleContentExcelSyncService.text(s, 20, 29))
                assertEquals('Lesson EN', ModuleContentExcelSyncService.text(s, 21, 29))
            }
        }
        File summaryEn = new File(module,
                'content/readme/docs/en/menu/1.Basic/summary/summaries.yml')
        summaryEn.setText(summaryEn.getText('UTF-8').replace('basic-001', 'other-001'), 'UTF-8')
        try {
            new ModuleContentExcelSyncService().execute(f.root as File, module,
                    'SUMMARY', false)
            fail('Invalid EN Summary ID accepted')
        } catch (GradleException expected) {
            assertTrue(expected.message.contains('Invalid EN Summary lesson'))
        }
    }

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
                assertTrue(ModuleContentExcelSyncService.text(sheet, 20, 16).contains('summaryQuestions:'))
                assertTrue(ModuleContentExcelSyncService.text(sheet, 20, 16).contains('English summary?'))
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
        File quiz = new File(f.module as File,
                'content/readme/docs/en/menu/1.Basic/quiz/question.yml')
        quiz.setText(quiz.getText('UTF-8')
                .replace('anchor: basic-001', 'anchor: unknown-001'), 'UTF-8')
        try {
            service.execute(f.root as File, f.module as File, 'QUIZ', true)
            fail('Expected unknown chapter link failure')
        } catch (GradleException expected) {
            assertTrue(expected.message.contains('Unresolved chapter quiz readmeRelated anchor'))
        }
        assertArrayEquals(original, (f.target as File).bytes)
    }

    @Test
    void videoExportChecksMissingEnglishWhenVietnameseVideoExists() {
        Map f = fixture()
        File module = f.module as File
        write(module, 'content/readme/docs/vi/menu/1.Basic/video/video-script.yml',
                'title: Vietnamese Video')
        File en = new File(module,
                'content/readme/docs/en/menu/1.Basic/video/video-script.yml')
        assertTrue(en.delete())
        try {
            new ModuleContentExcelSyncService().execute(f.root as File, module,
                    'VIDEO', false)
            fail('Explicit Video export must detect an untranslated VI chapter')
        } catch (GradleException error) {
            assertTrue(error.message.contains('Missing EN Video'))
        }
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
    void exportsTwoChaptersSeparatelyAndRejectsCrossChapterQuestionAnchors() {
        Map f = fixture()
        File module = f.module as File
        File baseline = f.source as File
        new XSSFWorkbook(new ByteArrayInputStream(baseline.bytes)).withCloseable { book ->
            def sheet = book.getSheetAt(0)
            cell(sheet, 22, 1, 'Chương thứ hai')
            cell(sheet, 22, 2, 'More.md')
            cell(sheet, 23, 2, 'Bài thứ hai')
            baseline.withOutputStream { book.write(it) }
        }
        write(module, 'content/readme/docs/en/menu/2.More/knowledge/More.md', '''
# English second chapter

## <a id="more-001">English second lesson</a>

<details>
<summary>Click for details</summary>

Second knowledge body

</details>
'''.trim())
        write(module, 'content/readme/docs/en/menu/2.More/metadata/knowledge-metadata.yml', '''
sections:
  more-001: {difficulty: INTERMEDIATE, isImportant: false}
'''.trim())
        File secondQuiz = new File(module,
                'content/readme/docs/en/menu/2.More/quiz/question.yml')
        write(module, 'content/readme/docs/en/menu/2.More/quiz/question.yml', '''
questions:
  - id: more-quiz-001
    question: Second chapter quiz?
    answers:
      - {id: A, answer: One, explanation: Explanation A}
      - {id: B, answer: Two, explanation: Explanation B}
      - {id: C, answer: Three, explanation: Explanation C}
      - {id: D, answer: Four, explanation: Explanation D}
    correctAnswerId: A


    readmeRelated: {anchor: more-001}
    apiRelated: {controller: "", methodSignature: ""}
'''.trim())
        write(module, 'content/readme/docs/en/menu/2.More/questions/interview.yml', '''
questions:
  - question: Second chapter interview?
    answer: Second chapter answer


    readmeRelated: {anchor: more-001}
    apiRelated: {controller: "", methodSignature: ""}
'''.trim())
        def service = new ModuleContentExcelSyncService()
        assertEquals('EXPORTED', service.execute(f.root as File, module, 'ALL', true).status)
        (f.target as File).withInputStream { input ->
            new XSSFWorkbook(input).withCloseable { book ->
                def sheet = book.getSheetAt(0)
                assertEquals('Second knowledge body', ModuleContentExcelSyncService.text(sheet, 23, 3))
                assertEquals('INTERMEDIATE', ModuleContentExcelSyncService.text(sheet, 23, 15))
                assertTrue(ModuleContentExcelSyncService.text(sheet, 22, 20).contains('Second chapter quiz?'))
                assertTrue(ModuleContentExcelSyncService.text(sheet, 22, 16).contains('Second chapter interview?'))
            }
        }
        byte[] published = (f.target as File).bytes
        secondQuiz.setText(secondQuiz.getText('UTF-8').replace('more-001', 'basic-001'), 'UTF-8')
        try {
            service.execute(f.root as File, module, 'QUIZ', true)
            fail('Cross-chapter anchor should be rejected')
        } catch (GradleException expected) {
            assertTrue(expected.message.contains('Unresolved chapter quiz readmeRelated anchor'))
        }
        assertArrayEquals(published, (f.target as File).bytes)
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
        write(module, 'content/readme/docs/en/menu/1.Basic/metadata/knowledge-metadata.yml', '''
sections:
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
        write(module, 'content/readme/docs/en/menu/1.Basic/questions/interview.yml', '''
questions:
  - question: English interview?
    answer: The answer
    readmeRelated: {anchor: basic-001}
'''.trim())
        write(module, 'content/readme/docs/en/menu/1.Basic/questions/summary.yml', '''
questions:
  - question: English summary?
    answer: An independent answer.
'''.trim())
        write(module, 'content/readme/docs/en/menu/1.Basic/quiz/question.yml', '''
questions:
  - id: quiz-001
    question: English quiz?
    answers:
      - {id: A, answer: Yes, explanation: correct}
      - {id: B, answer: No, explanation: incorrect}
      - {id: C, answer: Maybe, explanation: incorrect}
      - {id: D, answer: None, explanation: incorrect}
    correctAnswerId: A
    readmeRelated: {anchor: basic-001}
'''.trim())
        write(module, 'content/readme/docs/en/menu/1.Basic/video/video-script.yml', '''
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
