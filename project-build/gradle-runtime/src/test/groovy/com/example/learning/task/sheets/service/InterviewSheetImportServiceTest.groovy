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
                ['Interview JSON', 'Interview JSON', 'Interview JSON', 'Interview JSON']
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
                aiGenerated: false, reviewed: true,
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
        assertEquals('1.OOPBasic/OOPBasic.md', imported.questions[0].readmeRelated.file)
        assertEquals('', imported.questions[0].readmeRelated.anchor)
        assertEquals(true, imported.questions[0].aiGenerated)
        assertEquals(false, imported.questions[0].reviewed)
        assertEquals(true, imported.questions[1].reviewed)
        assertEquals(false, imported.questions[1].aiGenerated)
        assertEquals('defining', imported.questions[1].readmeRelated.anchor)
        assertEquals('DemoController', imported.questions[1].apiRelated.controller)

        String yaml = InterviewSheetImportService.renderYaml(imported.questions as List<Map>)
        assertTrue(yaml.startsWith('# <interview-schema>'))
        Map decoded = new Yaml().load(yaml) as Map
        assertEquals(imported.questions, decoded.questions)
        assertTrue(yaml.contains('Tại sao OOP?'))
    }

    @Test
    void blankOrPartialChaptersDoNotRemoveOtherQuestions() {
        assertEquals('SKIPPED_EMPTY_INTERVIEW',
                InterviewSheetImportService.collectQuestions(CHAPTERS, rowsWith([:])).status)
        Map imported = InterviewSheetImportService.collectQuestions(CHAPTERS,
                rowsWith([23: [JsonOutput.toJson([questions: [
                        [question: 'Question', answer: 'Answer']
                ]])]]))
        assertEquals(1, imported.chapterCount)
        assertEquals('2.Encapsulation/Encapsulation.md',
                imported.questions[0].readmeRelated.file)
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
    void onlyInterviewDirectoryIsReplacedOnApply() {
        File root = tmp.newFolder('repository')
        String location = 'module/sandbox/demo'
        File directory = InterviewSheetImportService.resolveDirectory(root, location)
        assertTrue(directory.mkdirs())
        new File(directory, 'question.yml').text = 'old interview'
        File roadmap = new File(root, location + '/content/roadmap/vi/roadmap.yml')
        assertTrue(roadmap.parentFile.mkdirs())
        roadmap.text = 'existing roadmap'
        String yaml = InterviewSheetImportService.renderYaml([[
                question: 'Q', answer: 'A', aiGenerated: true, reviewed: false,
                readmeRelated: [file: '1.OOPBasic/OOPBasic.md', anchor: ''],
                apiRelated: [controller: '', methodSignature: '']
        ]])

        Map dryRun = SheetSnapshotReplaceService.replace(
                root, directory, ['question.yml': yaml], false
        )
        assertEquals('DRY_RUN', dryRun.status)
        assertEquals('old interview', new File(directory, 'question.yml').text)

        Map applied = SheetSnapshotReplaceService.replace(
                root, directory, ['question.yml': yaml], true
        )
        assertEquals('UPDATED', applied.status)
        assertEquals(yaml, new File(directory, 'question.yml').getText('UTF-8'))
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
