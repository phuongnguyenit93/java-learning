package com.example.learning.task.sheets.service

import groovy.json.JsonOutput
import org.gradle.api.GradleException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.yaml.snakeyaml.Yaml

import static org.junit.Assert.*

class QuizSheetImportServiceTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder()

    private static final List<Map> GROUPS = [
            [filename: 'OOPBasic.md', parentRow: 20],
            [filename: 'Encapsulation.md', parentRow: 23]
    ]

    private static String authored(String title) {
        JsonOutput.toJson([questions: [[question: title, answers: [
                [id: 'A', answer: 'A', explanation: 'Vì A.'],
                [id: 'B', answer: 'B', explanation: 'Vì B.'],
                [id: 'C', answer: 'C', explanation: 'Vì C.'],
                [id: 'D', answer: 'D', explanation: 'Vì D.']
        ], correctAnswerId: 'C']]])
    }

    private static List<List<Object>> rowsWith(Map<Integer, List<String>> content) {
        List<List<Object>> rows = [
                ['Quiz'],
                ['Quiz YAML', 'Quiz YAML', 'Quiz YAML', 'Quiz YAML']
        ]
        (20..24).each { int r -> rows.add(content[r] ?: []) }
        rows
    }

    @Test
    void importsCompleteChaptersAndWritesSchemaValidQuiz() {
        String one = authored('Lúc runtime?')
        Map result = QuizSheetImportService.collectQuestions(GROUPS, rowsWith([
                20: [one.substring(0, 22), one.substring(22)],
                23: [authored('Lúc compile?')]
        ]))
        assertEquals('READY', result.status)
        assertEquals(2, result.chapterCount)
        assertEquals(['oopbasic-001', 'encapsulation-001'], result.questions*.id)
        assertEquals('1.OOPBasic/OOPBasic.md', result.questions[0].readmeRelated.file)
        assertEquals('2.Encapsulation/Encapsulation.md', result.questions[1].readmeRelated.file)
        String yaml = QuizSheetImportService.renderYaml(result.questions as List<Map>)
        assertTrue(yaml.startsWith('# <quiz-schema>'))
        assertEquals(result.questions, new Yaml().load(yaml).questions)
    }

    @Test
    void blocksIncompleteChapterOrOrphanDataAndSkipsAllBlank() {
        assertEquals('SKIPPED_EMPTY_QUIZ',
                QuizSheetImportService.collectQuestions(GROUPS, rowsWith([:])).status)
        expectInvalid('Incomplete Quiz') {
            QuizSheetImportService.collectQuestions(GROUPS, rowsWith([20: [authored('Only one')]]))
        }
        expectInvalid('non-parent row') {
            QuizSheetImportService.collectQuestions(GROUPS, rowsWith([
                    20: [authored('One')], 21: ['orphan'], 23: [authored('Two')]
            ]))
        }
        expectInvalid('Quiz headers') {
            def rows = rowsWith([20: [authored('One')], 23: [authored('Two')]])
            rows[1][2] = 'Interview YAML'
            QuizSheetImportService.collectQuestions(GROUPS, rows)
        }
    }

    @Test
    void snapshotReplacesOnlyQuizViAndLeavesOtherLanguageUntouched() {
        File root = tmp.newFolder('repository')
        File target = QuizSheetImportService.resolveDirectory(root, 'module/sandbox/demo')
        assertTrue(target.mkdirs())
        new File(target, 'question.yml').setText('OLD', 'UTF-8')
        File english = new File(target.parentFile, 'en/question.yml')
        assertTrue(english.parentFile.mkdirs())
        english.setText('EN', 'UTF-8')
        Map dry = SheetSnapshotReplaceService.replace(root, target,
                ['question.yml': 'NEW'], false)
        assertEquals('DRY_RUN', dry.status)
        assertEquals('OLD', new File(target, 'question.yml').getText('UTF-8'))
        assertEquals('UPDATED', SheetSnapshotReplaceService.replace(root, target,
                ['question.yml': 'NEW'], true).status)
        assertEquals('NEW', new File(target, 'question.yml').getText('UTF-8'))
        assertEquals('EN', english.getText('UTF-8'))
    }

    private static void expectInvalid(String expected, Closure action) {
        try { action.call(); fail('Expected failure') }
        catch (GradleException e) { assertTrue(e.message, e.message.contains(expected)) }
    }
}
