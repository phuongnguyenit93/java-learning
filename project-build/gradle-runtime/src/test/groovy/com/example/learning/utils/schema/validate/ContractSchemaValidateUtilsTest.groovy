package com.example.learning.utils.schema.validate

import org.gradle.api.GradleException
import org.junit.Test

import static org.junit.Assert.*

class ContractSchemaValidateUtilsTest {
    private static String shouldFail(Class expectedType, Closure operation) {
        try {
            operation.call()
            fail("Expected ${expectedType.simpleName}")
        } catch (Exception error) {
            if (!expectedType.isInstance(error)) throw error
            return error.message
        }
        return ''
    }
    @Test
    void loadsAllBundledPublishedAndAuthoringSchemas() {
        ['quiz','interview','roadmap','reference','videoAuthoring','videoIndex',
         'videoScript','apiDocs','knowledge','knowledgeSearch',
         'moduleCatalog','manifest','common'].each {
            ContractSchemaValidateUtils.validateSchema(it)
        }
    }

    @Test
    void strictRelationIsDifferentFromLegacyQuestionMode() {
        Map quiz = [questions: [[
            id:'1',question:'Question?',aiGenerated:true,reviewed:false,
            readmeRelated:[file:'1.Chapter/Chapter.md'],
            apiRelated:[controller:'',methodSignature:''],
            correctAnswerId:'A',
            answers:['A','B','C','D'].collect { String letter ->
                [id:letter,answer:"Answer ${letter}",explanation:"Explain ${letter}"]
            }
        ]]]
        ContractSchemaValidateUtils.validateData('quiz',quiz,
                ContractSchemaValidateUtils.LEGACY_QUESTION_LINKS)
        shouldFail(GradleException) { ContractSchemaValidateUtils.validateData('quiz',quiz) }
        quiz.questions[0].readmeRelated = [anchor: 'chapter-1']
        ContractSchemaValidateUtils.validateData('quiz',quiz)
        quiz.questions[0].answers[3].id='A'
        shouldFail(GradleException) { ContractSchemaValidateUtils.validateData('quiz',quiz) }
    }

    @Test
    void extensionsAndRoadmapConstraints() {
        Map doc = [roadmap:[[id:'a',title:'A',purpose:'P'],
                             [id:'b',title:'B',purpose:'P']]]
        ContractSchemaValidateUtils.validateData('roadmap',doc)
        doc.roadmap[1].id='a'
        String error = shouldFail(GradleException) {
            ContractSchemaValidateUtils.validateData('roadmap',doc)
        }
        assertTrue(error.contains('x-uniqueBy'))
    }

    @Test
    void rejectsBadReferenceAndBadType() {
        ContractSchemaValidateUtils.validateData('reference',[references:[]])
        String error = shouldFail(GradleException) {
            ContractSchemaValidateUtils.validateData('reference',
                    [references:[[title:'Valid',url:12]]])
        }
        assertTrue(error.contains('url'))
    }

    @Test
    void distinguishesVideoSourceSchemaFromPublishedProjection() {
        Map authored = [title:'Title',video:[url:''],sections:[[
                title:'Chapter',items:[[
                    type:'SCENE',title:'Beginning',time:'00:00-00:05',
                    visual:'Visual',script:'Narration',purpose:'Objective'
                ]]
        ]]]
        ContractSchemaValidateUtils.validateData('videoAuthoring',authored)
        shouldFail(GradleException) {
            ContractSchemaValidateUtils.validateData('videoScript',authored)
        }
        Map projected = [version:1,moduleId:'oop',language:'vi',
                         categoryId:'OOPBasic',title:'Title',sourcePath:'1.OOPBasic/video-script.yml',
                         video:[url:''], sections:[[
                            order:1,title:'Chapter',items:authored.sections[0].items
                         ]]]
        ContractSchemaValidateUtils.validateData('videoScript',projected)
        authored.sections[0].items[0].unknownField='unsafe'
        shouldFail(GradleException) {
            ContractSchemaValidateUtils.validateData('videoAuthoring',authored)
        }
    }
}
