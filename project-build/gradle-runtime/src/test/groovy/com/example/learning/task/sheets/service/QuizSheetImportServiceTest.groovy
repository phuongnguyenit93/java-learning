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
        assertEquals([anchor: ''], result.questions[0].readmeRelated)
        assertEquals(['1.OOPBasic','2.Encapsulation'], result.byChapter.keySet().toList())
        String yaml = QuizSheetImportService.renderYaml(result.questions as List<Map>)
        assertTrue(yaml.startsWith('# <quiz-schema>'))
        assertEquals(result.questions, new Yaml().load(yaml).questions)
    }

    @Test
    void blankQuizChaptersAreAbsentFromSnapshotAndOrphansFail() {
        assertEquals('READY',
                QuizSheetImportService.collectQuestions(GROUPS, rowsWith([:])).status)
        assertEquals([:], QuizSheetImportService.collectQuestions([], rowsWith([:])).byChapter)
        Map partial = QuizSheetImportService.collectQuestions(GROUPS, rowsWith([20: [authored('Only one')]]))
        assertEquals(['1.OOPBasic'], partial.byChapter.keySet().toList())
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
        File chapter = new File(target, '1.OOPBasic')
        assertTrue(new File(chapter, 'knowledge').mkdirs())
        File existing = new File(chapter, 'quiz/question.yml')
        assertTrue(existing.parentFile.mkdirs())
        existing.setText('OLD', 'UTF-8')
        File english = new File(root, 'module/sandbox/demo/content/readme/docs/en/menu/1.OOPBasic/quiz/question.yml')
        assertTrue(english.parentFile.mkdirs())
        english.setText('EN', 'UTF-8')
        Map dry = ChapterQuestionSnapshotService.replace(root,
                new File(root, 'module/sandbox/demo'), 'quiz', ['1.OOPBasic':'NEW'], false)
        assertEquals('DRY_RUN', dry.status)
        assertEquals('OLD', existing.getText('UTF-8'))
        assertEquals('UPDATED', ChapterQuestionSnapshotService.replace(root,
                new File(root, 'module/sandbox/demo'), 'quiz',
                ['1.OOPBasic': 'NEW'], true).status)
        assertEquals('NEW', existing.getText('UTF-8'))
        assertEquals('EN', english.getText('UTF-8'))
    }

    private static void expectInvalid(String expected, Closure action) {
        try { action.call(); fail('Expected failure') }
        catch (GradleException e) { assertTrue(e.message, e.message.contains(expected)) }
    }
}
