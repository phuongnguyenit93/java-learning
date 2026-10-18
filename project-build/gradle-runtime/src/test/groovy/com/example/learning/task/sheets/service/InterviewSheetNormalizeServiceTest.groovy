package com.example.learning.task.sheets.service

import groovy.json.JsonOutput
import org.gradle.api.GradleException
import org.junit.Test

import static org.junit.Assert.*

class InterviewSheetNormalizeServiceTest {
    private static final String FILE = '1.OOPBasic/OOPBasic.md'

    @Test
    void rawQuestionAnswerGetsCanonicalDefaults() {
        String source = JsonOutput.toJson([
                questions: [
                        [question: 'Vì sao composition hữu ích?', answer: 'Vì tránh ràng buộc kế thừa cứng.'],
                        [question: 'Giải thích encapsulation?', answer: 'Bảo vệ invariant.']
                ]
        ])
        Map result = InterviewSheetNormalizeService.normalizeJson(source, FILE, 20)
        assertEquals(2, result.questions.size())
        assertEquals(['question', 'answer', 'aiGenerated', 'reviewed',
                      'readmeRelated', 'apiRelated'], result.questions[0].keySet().toList())
        assertEquals(true, result.questions[0].aiGenerated)
        assertEquals(false, result.questions[0].reviewed)
        assertEquals([file: FILE, anchor: ''], result.questions[0].readmeRelated)
        assertEquals([controller: '', methodSignature: ''], result.questions[0].apiRelated)
        assertEquals('Giải thích encapsulation?', result.questions[1].question)
    }

    @Test
    void repeatedNormalizationPreservesReviewedAndRelatedFields() {
        Map existing = [questions: [[
                question: 'Tình huống?', answer: 'Giải thích.',
                aiGenerated: false, reviewed: true,
                readmeRelated: [file: FILE, anchor: 'oop-invariant'],
                apiRelated: [controller: 'OOPController', methodSignature: 'explain()']
        ]]]
        String original = JsonOutput.toJson(existing)
        Map normalized = InterviewSheetNormalizeService.normalizeJson(original, FILE, 20)
        assertEquals(existing, normalized)
        String serialized = JsonOutput.prettyPrint(JsonOutput.toJson(normalized)) + '\n'
        assertEquals(normalized, InterviewSheetNormalizeService.normalizeJson(serialized, FILE, 20))
        assertEquals(serialized, JsonOutput.prettyPrint(JsonOutput.toJson(
                InterviewSheetNormalizeService.normalizeJson(serialized, FILE, 20))) + '\n')
    }

    @Test
    void rejectsChangedReadmePathAndUnknownFields() {
        assertInvalid([questions: [[question: 'Q', answer: 'A',
                readmeRelated: [file: 'wrong.md', anchor: '']]]], 'conflicting README')
        assertInvalid([questions: [[question: 'Q', answer: 'A', other: 'extra']]], 'unknown field')
        assertInvalid([questions: [[question: 'Q', answer: ' ']]], 'nonblank string')
        assertInvalid([questions: [[question: 'Q', answer: 'A', reviewed: 'false']]], 'must be boolean')
        assertInvalid([questions: [[question: 'Q', answer: 'A',
                apiRelated: [controller: 5]]]], 'invalid relation')
        assertInvalid([questions: []], 'nonempty questions')
    }

    @Test
    void unicodeChunksRoundTripAndRejectOverflow() {
        String text = ('😀' * 29999) + 'abc' + ('🎓' * 30002)
        List<String> pieces = InterviewSheetNormalizeService.splitFragments(text, 20)
        assertEquals(4, pieces.size())
        assertEquals(30000, pieces[0].codePointCount(0, pieces[0].length()))
        assertEquals(text, InterviewSheetNormalizeService.joinFragments(pieces, 20))
        try {
            InterviewSheetNormalizeService.splitFragments('x' * 120001, 20)
            fail('capacity should have failed')
        }
        catch (GradleException expected) {
            assertTrue(expected.message.contains('exceeds'))
        }
        try {
            InterviewSheetNormalizeService.joinFragments(['abc', '', 'def', ''], 20)
            fail('fragment gap should have failed')
        }
        catch (GradleException expected) {
            assertTrue(expected.message.contains('Noncontiguous'))
        }
    }

    private static void assertInvalid(Map data, String snippet) {
        try {
            InterviewSheetNormalizeService.normalizeJson(JsonOutput.toJson(data), FILE, 20)
            fail('Invalid JSON data was accepted: ' + data)
        }
        catch (GradleException expected) {
            assertTrue(expected.message, expected.message.contains(snippet))
        }
    }
}
