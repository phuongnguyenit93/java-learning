package com.example.learning.task.sheets.service

import groovy.json.JsonOutput
import org.gradle.api.GradleException
import org.junit.Test
import org.yaml.snakeyaml.Yaml

import static org.junit.Assert.*

class QuizSheetNormalizeServiceTest {
    private static final String FILE = '1.OOPBasic/knowledge/OOPBasic.md'

    private static Map authored(String label = 'Tình huống Java?') {
        return [question: label, answers: [
                [id: 'A', answer: 'Giả thuyết A', explanation: 'Sai vì nguyên nhân A.'],
                [id: 'B', answer: 'Giả thuyết B', explanation: 'Đúng vì cơ chế B.'],
                [id: 'C', answer: 'Giả thuyết C', explanation: 'Sai vì nguyên nhân C.'],
                [id: 'D', answer: 'Giả thuyết D', explanation: 'Sai vì nguyên nhân D.']
        ], correctAnswerId: 'B']
    }

    private static Map normalize(Map document) {
        QuizSheetNormalizeService.normalizeYaml(
                JsonOutput.toJson(document), FILE, 'OOPBasic', 20)
    }

    @Test
    void addsStableFilenameIdsAndFullDefaults() {
        Map result = normalize([questions: [authored('Đầu tiên?'), authored('Thứ hai?')]])
        assertEquals(['oopbasic-001', 'oopbasic-002'], result.questions*.id)
        assertEquals(['id', 'question', 'readmeRelated',
                      'apiRelated', 'answers', 'correctAnswerId'], result.questions[0].keySet().toList())
        assertEquals([anchor: ''], result.questions[0].readmeRelated)
        assertEquals([controller: '', methodSignature: ''], result.questions[0].apiRelated)
        assertEquals(['A', 'B', 'C', 'D'], result.questions[0].answers*.id)
        assertEquals('B', result.questions[0].correctAnswerId)
        String serialized = QuizSheetNormalizeService.renderYaml(result)
        assertEquals(result, new Yaml().load(serialized))
        assertEquals(serialized, QuizSheetNormalizeService.renderYaml(
                QuizSheetNormalizeService.normalizeYaml(serialized, FILE, 'OOPBasic', 20)))
    }

    @Test
    void legacySheetFlagsAreReadButRemovedFromQuizSnapshot() {
        Map old = authored() + [aiGenerated: false, reviewed: true]
        Map normalized = normalize([questions: [old]])
        assertEquals(['id', 'question', 'readmeRelated', 'apiRelated',
                'answers', 'correctAnswerId'] as Set,
                normalized.questions[0].keySet())
        assertFalse(QuizSheetNormalizeService.renderYaml(normalized).contains('aiGenerated'))
        assertFalse(QuizSheetNormalizeService.renderYaml(normalized).contains('reviewed'))
    }

    @Test
    void preservesExistingIdsAndHumanEditedFlagsOnReorder() {
        Map first = authored('Một') + [id: 'oopbasic-012', readmeRelated: [file: FILE, anchor: 'some-anchor'],
                apiRelated: [controller: 'DemoController', methodSignature: 'run()']]
        Map second = authored('Hai') + [id: 'custom-human-id']
        Map normalized = normalize([questions: [second, first, authored('Mới')]])
        assertEquals(['custom-human-id', 'oopbasic-012', 'oopbasic-001'], normalized.questions*.id)
        assertEquals('some-anchor', normalized.questions[1].readmeRelated.anchor)
        assertEquals('DemoController', normalized.questions[1].apiRelated.controller)
    }

    @Test
    void migratesLegacyReadmePathButPreservesQuizIdentityAndReview() {
        Map source = authored() + [id: 'manually-edited', readmeRelated: [file: '1.OOPBasic/OOPBasic.md', anchor: 'old-anchor'],
                apiRelated: [controller: 'Catalog', methodSignature: 'lookup()']]
        Map result = normalize([questions: [source]])
        assertEquals('manually-edited', result.questions[0].id)
        assertEquals([anchor: 'old-anchor'], result.questions[0].readmeRelated)
        assertEquals('Catalog', result.questions[0].apiRelated.controller)
    }

    @Test
    void rejectsDuplicateIdsBadOptionsAndMissingExplanation() {
        expectInvalid([questions: [authored() + [id: 'duplicate'],
                                   authored('Next') + [id: 'duplicate']]], 'duplicate')
        Map three = authored(); three.answers = three.answers.take(3)
        expectInvalid([questions: [three]], 'exactly four')
        Map duplicate = authored(); duplicate.answers[3].id = 'C'
        expectInvalid([questions: [duplicate]], 'invalid answer ID')
        Map missing = authored(); missing.answers[0].explanation = ''
        expectInvalid([questions: [missing]], 'invalid answer ID')
        Map badKey = authored() + [extra: 'nope']
        expectInvalid([questions: [badKey]], 'unknown field')
        Map wrong = authored(); wrong.correctAnswerId = 'E'
        expectInvalid([questions: [wrong]], 'correctAnswerId')
        expectInvalid([questions: []], 'nonempty')
        expectInvalid([questions: [authored() + [readmeRelated: [file: 'wrong.md', anchor: '']]]],
                'conflicting README')
    }

    @Test
    void unicodeSplitsWithoutBreakingCodePoints() {
        String payload = ('🎓' * 30001) + ('😀' * 30003)
        List<String> pieces = QuizSheetNormalizeService.splitFragments(payload, 20)
        assertEquals(4, pieces.size())
        assertEquals(payload, QuizSheetNormalizeService.joinFragments(pieces, 20))
        try {
            QuizSheetNormalizeService.splitFragments('a' * 120001, 20)
            fail('capacity must fail')
        }
        catch (GradleException e) { assertTrue(e.message.contains('exceeds')) }
    }

    private static void expectInvalid(Map source, String message) {
        try {
            normalize(source)
            fail('Expected invalid Quiz: ' + source)
        }
        catch (GradleException e) { assertTrue(e.message, e.message.contains(message)) }
    }
}
