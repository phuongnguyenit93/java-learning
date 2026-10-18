package com.example.learning.utils.schema.validate

import com.example.learning.setup.module.quiz.service.QuizQuestionSchemaService
import com.example.learning.setup.module.interview.service.InterviewQuestionSchemaService
import org.gradle.api.GradleException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.junit.Assert.*

class QuestionContractSchemaUtilsTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder()

    @Test
    void packagesPinnedContractsWithoutShippingLegacyYaml() {
        ['quiz', 'interview', 'common'].each { String name ->
            String resource = "learning-contract/schemas/${name}.schema.json"
            InputStream bundled = getClass().classLoader.getResourceAsStream(resource)
            assertNotNull("Missing bundled contract: ${resource}", bundled)
            byte[] actual
            try {
                actual = bundled.bytes
            } finally {
                bundled.close()
            }
            File source = new File("../../learning-contract/schemas/${name}.schema.json")
            assertTrue("Pinned source missing: ${source}", source.isFile())
            assertArrayEquals("Bundled schema differs from pinned submodule: ${name}",
                    source.bytes, actual)
        }
        assertNull(getClass().classLoader.getResource('quiz/question-schema.yml'))
        assertNull(getClass().classLoader.getResource('interview/question-schema.yml'))
    }

    @Test
    void retainsQuizFieldsDefaultsAndFourAnswerRules() {
        Map descriptor = QuestionContractSchemaUtils.load('quiz')
        assertEquals(3, descriptor.version)
        assertEquals('Cấu trúc câu hỏi Quiz', descriptor.title.vi)
        assertEquals('questions', descriptor.document.root.name)
        assertEquals('id', descriptor.document.root.uniqueBy)
        Map fields = descriptor.document.root.item.fields as Map
        assertEquals(['id', 'question', 'aiGenerated', 'reviewed', 'readmeRelated',
                      'apiRelated', 'correctAnswerId', 'answers'] as Set, fields.keySet() as Set)
        assertEquals(true, fields.aiGenerated.default)
        assertEquals(false, fields.reviewed.default)
        assertEquals(4, fields.answers.size)
        assertEquals('id', fields.answers.uniqueBy)
        assertEquals(['A', 'B', 'C', 'D'], fields.answers.item.fields.id.values)

        def service = new QuizQuestionSchemaService()
        assertTrue(service.renderCommentBlock('vi').contains('Cấu trúc câu hỏi Quiz'))
        assertTrue(service.renderCommentBlock('en').contains('Quiz Question Structure'))
        assertTrue(service.renderSkeleton('vi').endsWith('questions: []\n'))
        Map question = [
                id: 'quiz-1', question: 'Một câu hỏi hợp lệ',
                aiGenerated: true, reviewed: false,
                readmeRelated: [file: '', anchor: ''],
                apiRelated: [controller: '', methodSignature: ''],
                answers: ['A', 'B', 'C', 'D'].collect { String id ->
                    [id: id, answer: 'Option ' + id, explanation: 'Explained ' + id]
                },
                correctAnswerId: 'A'
        ]
        File file = temporary.newFile('quiz-question.yml')
        file.setText(new org.yaml.snakeyaml.Yaml().dump([questions: [question]]), 'UTF-8')
        service.validateQuestionFile(file)
        question.answers[3].id = 'A'
        file.setText(new org.yaml.snakeyaml.Yaml().dump([questions: [question]]), 'UTF-8')
        expectInvalid { service.validateQuestionFile(file) }
        question.answers[3].id = 'D'
        question.answers.remove(3)
        file.setText(new org.yaml.snakeyaml.Yaml().dump([questions: [question]]), 'UTF-8')
        expectInvalid { service.validateQuestionFile(file) }
    }

    @Test
    void retainsInterviewDefaultsAndFileOnlyKnowledgeRelations() {
        Map descriptor = QuestionContractSchemaUtils.load('interview')
        assertEquals(2, descriptor.version)
        assertEquals('Cấu trúc câu hỏi Interview', descriptor.title.vi)
        Map fields = descriptor.document.root.item.fields as Map
        assertEquals(['question', 'answer', 'aiGenerated', 'reviewed',
                      'readmeRelated', 'apiRelated'] as Set, fields.keySet() as Set)
        assertEquals(true, fields.aiGenerated.default)
        assertEquals(false, fields.reviewed.default)

        def service = new InterviewQuestionSchemaService()
        assertTrue(service.renderCommentBlock('vi').contains('Cấu trúc câu hỏi Interview'))
        assertTrue(service.renderSkeleton('en').endsWith('questions: []\n'))
        File file = temporary.newFile('interview-question.yml')
        Map item = [question: 'Why?', answer: 'Because.',
                    aiGenerated: true, reviewed: false,
                    readmeRelated: [file: '1.Basic/Basic.md', anchor: ''],
                    apiRelated: [controller: '', methodSignature: '']]
        file.setText(new org.yaml.snakeyaml.Yaml().dump([questions: [item]]), 'UTF-8')
        service.validateQuestionFile(file)
        item.answer = '  '
        file.setText(new org.yaml.snakeyaml.Yaml().dump([questions: [item]]), 'UTF-8')
        expectInvalid { service.validateQuestionFile(file) }
    }

    @Test
    void rejectsInvalidQuestionContractNames() {
        expectInvalid { QuestionContractSchemaUtils.load('unknown') }
    }

    @Test
    void keepsExistingCommentsForCosmeticChangesButDetectsStructuralChanges() {
        String initial = new QuizQuestionSchemaService().renderCommentBlock('vi')
        String cosmetic = initial.replace('Cấu trúc câu hỏi Quiz', 'Mô tả cũ trong module')
        cosmetic = cosmetic.replace('ID ổn định và duy nhất của câu hỏi.',
                'ID câu hỏi được mô tả theo cách cũ.')
        assertTrue(QuestionContractSchemaUtils.hasEquivalentCommentStructure(
                cosmetic, initial, '# <quiz-schema>', '# </quiz-schema>'))
        String structural = initial.replace('questions [kiểu: list; bắt buộc;',
                'questions [kiểu: list; tùy chọn;')
        assertFalse(QuestionContractSchemaUtils.hasEquivalentCommentStructure(
                structural, initial, '# <quiz-schema>', '# </quiz-schema>'))
        assertFalse(QuestionContractSchemaUtils.hasEquivalentCommentStructure(
                'questions: []', initial, '# <quiz-schema>', '# </quiz-schema>'))
    }

    private static void expectInvalid(Closure work) {
        try {
            work.call()
            fail('Expected invalid question contract or content to be rejected.')
        } catch (GradleException expected) {
            assertFalse(expected.message.isBlank())
        }
    }
}
