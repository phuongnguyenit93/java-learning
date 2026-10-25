package com.example.learning.task.sheets.service

import groovy.json.JsonOutput
import org.gradle.api.GradleException
import org.junit.Test
import org.yaml.snakeyaml.Yaml

import static org.junit.Assert.*

class InterviewSheetNormalizeServiceTest {
    private static final String FILE = '1.OOPBasic/knowledge/OOPBasic.md'

    @Test
    void authoredYamlPreservesMarkdownAnswersAndNormalizesDeterministically() {
        String source = '''questions:
  - question: "Composition khác inheritance thế nào?"
    answer: |-
      **Composition** tổ hợp hành vi qua các object.

      ```java
      class Engine {}
      ```
  - question: "Khi nào dùng interface?"
    answer: "Khi cần hợp đồng hành vi rõ ràng."
'''
        Map normalized = InterviewSheetNormalizeService.normalizeYaml(source, FILE, 20)
        assertEquals(2, normalized.questions.size())
        assertTrue(normalized.questions[0].answer.contains('```java'))
        assertEquals([anchor: ''], normalized.questions[0].readmeRelated)
        String yaml = InterviewSheetNormalizeService.renderYaml(normalized)
        assertEquals(normalized, new Yaml().load(yaml))
        assertEquals(yaml, InterviewSheetNormalizeService.renderYaml(
                InterviewSheetNormalizeService.normalizeYaml(yaml, FILE, 20)))
        assertFalse(yaml.trim().startsWith('{'))
    }

    @Test
    void legacyJsonIsReadButNormalizedOutputIsYaml() {
        String json = JsonOutput.toJson([questions: [[question: 'Q', answer: 'A']]])
        Map normalized = InterviewSheetNormalizeService.normalizeYaml(json, FILE, 20)
        assertEquals('Q', normalized.questions[0].question)
        assertTrue(InterviewSheetNormalizeService.renderYaml(normalized).startsWith('questions:'))
    }

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
        assertEquals([anchor: ''], result.questions[0].readmeRelated)
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
        assertEquals('oop-invariant', normalized.questions[0].readmeRelated.anchor)
        assertFalse(normalized.questions[0].readmeRelated.containsKey('file'))
        String serialized = JsonOutput.prettyPrint(JsonOutput.toJson(normalized)) + '\n'
        assertEquals(normalized, InterviewSheetNormalizeService.normalizeJson(serialized, FILE, 20))
        assertEquals(serialized, JsonOutput.prettyPrint(JsonOutput.toJson(
                InterviewSheetNormalizeService.normalizeJson(serialized, FILE, 20))) + '\n')
    }

    @Test
    void migratesLegacyReadmePathWithoutChangingHumanFields() {
        Map source = [questions: [[question: 'Q', answer: '**Markdown**',
                aiGenerated: false, reviewed: true,
                readmeRelated: [file: '1.OOPBasic/OOPBasic.md', anchor: 'existing-anchor'],
                apiRelated: [controller: 'Api', methodSignature: 'run()']]]]
        Map result = InterviewSheetNormalizeService.normalizeYaml(
                JsonOutput.toJson(source), FILE, 20)
        assertFalse(result.questions[0].readmeRelated.containsKey('file'))
        assertEquals('existing-anchor', result.questions[0].readmeRelated.anchor)
        assertEquals(false, result.questions[0].aiGenerated)
        assertEquals(true, result.questions[0].reviewed)
        assertEquals('**Markdown**', result.questions[0].answer)
        assertEquals('Api', result.questions[0].apiRelated.controller)
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
