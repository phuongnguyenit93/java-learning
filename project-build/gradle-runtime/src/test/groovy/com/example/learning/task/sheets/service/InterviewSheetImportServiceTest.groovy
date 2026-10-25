package com.example.learning.task.sheets.service

import groovy.json.JsonOutput
import org.gradle.api.GradleException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.yaml.snakeyaml.Yaml

import static org.junit.Assert.*

class InterviewSheetImportServiceTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder()

    private static final List<Map> CHAPTERS = [
            [title: 'Tư duy OOP', filename: 'OOPBasic.md', parentRow: 20,
             children: [[title: 'Bài 1', row: 21]]],
            [title: 'Đóng gói', filename: 'Encapsulation.md', parentRow: 23,
             children: [[title: 'Bài 2', row: 24]]]
    ]

    private static List<List<Object>> rowsWith(
            Map<Integer, List<String>> chapterFragments
    ) {
        List<List<Object>> rows = [
                ['Interview'],
                ['Interview YAML', 'Interview YAML', 'Interview YAML', 'Interview YAML']
        ]
        (20..24).each { int sheetRow ->
            rows.add(chapterFragments[sheetRow] ?: [])
        }
        return rows
    }

    @Test
    void combinesBothChaptersAndRendersValidModuleYaml() {
        String raw = JsonOutput.toJson([questions: [
                [question: 'Tại sao OOP?', answer: 'Có thể tổ chức trạng thái và hành vi.']
        ]])
        String annotated = JsonOutput.toJson([questions: [[
                question: 'Khi nào dùng encapsulation?',
                answer: 'Khi cần bảo vệ invariant và API.',
                readmeRelated: [file: '2.Encapsulation/Encapsulation.md', anchor: 'defining'],
                apiRelated: [controller: 'DemoController', methodSignature: 'show()']
        ]]])
        List<List<Object>> rows = rowsWith([
                20: [raw.substring(0, raw.length() / 2 as int),
                     raw.substring(raw.length() / 2 as int)],
                23: [annotated]
        ])

        Map imported = InterviewSheetImportService.collectQuestions(CHAPTERS, rows)
        assertEquals('READY', imported.status)
        assertEquals(2, imported.chapterCount)
        assertEquals(2, imported.questions.size())
        assertEquals([anchor: ''], imported.questions[0].readmeRelated)
        assertEquals(['1.OOPBasic','2.Encapsulation'], imported.byChapter.keySet().toList())
        assertEquals('', imported.questions[0].readmeRelated.anchor)
        assertEquals('defining', imported.questions[1].readmeRelated.anchor)
        assertEquals('DemoController', imported.questions[1].apiRelated.controller)

        String yaml = InterviewSheetImportService.renderYaml(imported.questions as List<Map>)
        assertTrue(yaml.startsWith('# <interview-schema>'))
        Map decoded = new Yaml().load(yaml) as Map
        assertEquals(imported.questions, decoded.questions)
        assertTrue(yaml.contains('Tại sao OOP?'))
    }

    @Test
    void importsAuthoredYamlAndPreservesMultilineMarkdownAnswers() {
        String yaml = '''questions:
  - question: Vì sao dùng encapsulation?
    answer: |-
      Bảo vệ **invariant** của đối tượng.

      ```java
      private int balance;
      ```
'''
        Map imported = InterviewSheetImportService.collectQuestions(CHAPTERS,
                rowsWith([20: [yaml]]))
        assertEquals('READY', imported.status)
        assertEquals(1, imported.chapterCount)
        assertTrue(imported.questions[0].answer.contains('```java'))
        assertEquals([anchor: ''], imported.questions[0].readmeRelated)
        assertEquals(imported.questions, new Yaml().load(
                InterviewSheetImportService.renderYaml(imported.questions as List<Map>)).questions)
    }

    @Test
    void acceptsLegacyJsonHeaderAndRejectsMixedHeaderLabels() {
        List<List<Object>> legacy = rowsWith([
                20: [JsonOutput.toJson([questions: [[question: 'Q', answer: 'A']]])]
        ])
        legacy[1] = ['Interview JSON', 'Interview JSON', 'Interview JSON', 'Interview JSON']
        Map imported = InterviewSheetImportService.collectQuestions(CHAPTERS, legacy)
        assertEquals('READY', imported.status)
        assertEquals('Q', imported.questions[0].question)

        legacy[1][3] = 'Interview YAML'
        expectFailure('Interview headers') {
            InterviewSheetImportService.collectQuestions(CHAPTERS, legacy)
        }
    }

    @Test
    void blankChaptersAreAuthoritativeEmptySnapshots() {
        assertEquals('READY',
                InterviewSheetImportService.collectQuestions(CHAPTERS, rowsWith([:])).status)
        assertEquals([:], InterviewSheetImportService.collectQuestions([], rowsWith([:])).byChapter)
        Map imported = InterviewSheetImportService.collectQuestions(CHAPTERS,
                rowsWith([23: [JsonOutput.toJson([questions: [
                        [question: 'Question', answer: 'Answer']
                ]])]]))
        assertEquals(1, imported.chapterCount)
        assertEquals(['2.Encapsulation'], imported.byChapter.keySet().toList())
    }

    @Test
    void malformedInputOrOrphanFragmentsFailBeforeWriting() {
        expectFailure('non-parent row') {
            InterviewSheetImportService.collectQuestions(CHAPTERS,
                    rowsWith([21: ['unexpected']]))
        }
        expectFailure('Invalid Interview fragments') {
            InterviewSheetImportService.collectQuestions(CHAPTERS,
                    rowsWith([20: ['{"questions":', '', '[]}']]))
        }
        expectFailure('README file path') {
            InterviewSheetImportService.collectQuestions(CHAPTERS,
                    rowsWith([20: [JsonOutput.toJson([questions: [[
                            question: 'Q', answer: 'A',
                            readmeRelated: [file: 'unrelated.md', anchor: '']
                    ]]])]]))
        }
        expectFailure('JSON') {
            InterviewSheetImportService.collectQuestions(CHAPTERS,
                    rowsWith([20: ['{invalid json']]))
        }
        expectFailure('Interview headers') {
            List<List<Object>> invalid = rowsWith([20: [
                    JsonOutput.toJson([questions: [[question: 'Q', answer: 'A']]])
            ]])
            invalid[1] = ['wrong header']
            InterviewSheetImportService.collectQuestions(CHAPTERS, invalid)
        }
    }

    @Test
    void onlyChapterInterviewIsReplacedOnApply() {
        File root = tmp.newFolder('repository')
        String location = 'module/sandbox/demo'
        File directory = InterviewSheetImportService.resolveDirectory(root, location)
        File chapter = new File(directory, '1.OOPBasic')
        assertTrue(new File(chapter, 'knowledge').mkdirs())
        File existing = new File(chapter, 'interview/question.yml')
        assertTrue(existing.parentFile.mkdirs())
        existing.text = 'old interview'
        File metadata = new File(chapter, 'metadata/knowledge-metadata.yml')
        assertTrue(metadata.parentFile.mkdirs())
        metadata.text = 'preserve metadata'
        File roadmap = new File(root, location + '/content/roadmap/vi/roadmap.yml')
        assertTrue(roadmap.parentFile.mkdirs())
        roadmap.text = 'existing roadmap'
        String yaml = InterviewSheetImportService.renderYaml([[
                question: 'Q', answer: 'A', readmeRelated: [anchor: ''],
                apiRelated: [controller: '', methodSignature: '']
        ]])

        Map dryRun = ChapterQuestionSnapshotService.replace(
                root, new File(root, location), 'interview', ['1.OOPBasic': yaml], false)
        assertEquals('DRY_RUN', dryRun.status)
        assertEquals('old interview', existing.text)

        Map applied = ChapterQuestionSnapshotService.replace(
                root, new File(root, location), 'interview', ['1.OOPBasic': yaml], true)
        assertEquals('UPDATED', applied.status)
        assertEquals(yaml, existing.getText('UTF-8'))
        assertEquals('preserve metadata', metadata.text)
        assertEquals('existing roadmap', roadmap.text)
    }

    private static void expectFailure(String message, Closure work) {
        try {
            work.call()
            fail('Expected failure containing: ' + message)
        }
        catch (GradleException e) {
            assertTrue(e.message, e.message.contains(message))
        }
    }
}
