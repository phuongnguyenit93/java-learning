package com.example.learning.setup.root.contractSkeleton

import com.example.learning.setup.root.contractSkeleton.service.ContractSkeletonSyncService
import groovy.json.JsonOutput
import org.gradle.api.GradleException
import org.gradle.api.logging.Logging
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.yaml.snakeyaml.Yaml

import static org.junit.Assert.*

class ContractSkeletonSyncServiceTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder()

    @Test
    void syncsOnlyAuthoringSchemasWithoutModuleOrTaskAndIsIdempotent() {
        File root = temp.newFolder('repository')
        prepareContract(root)
        def service = new ContractSkeletonSyncService(Logging.getLogger(getClass()))
        assertEquals([total: 4, updated: 4], service.sync(root))

        for (String kind in ['quiz', 'interview', 'roadmap']) {
            String filename = kind == 'roadmap' ? 'roadmap.yml' : "${kind}.yml"
            File output = new File(root, "project-build/skeleton/${kind}/${filename}")
            assertTrue(output.isFile())
            assertTrue(output.getText('UTF-8').startsWith(
                    ContractSkeletonSyncService.GENERATED_HEADER))
            assertTrue(output.getText('UTF-8').contains('# language: en'))
            Map parsed = new Yaml().load(output.getText('UTF-8')) as Map
            assertEquals([(kind == 'roadmap' ? 'roadmap' : 'questions'): []], parsed)
            assertTrue(output.getText('UTF-8').contains('authoring'))
            assertFalse(new File(root, "project-build/skeleton/${kind}/en").exists())
            assertFalse(new File(root, "project-build/skeleton/${kind}/vi").exists())
            assertFalse(new File(root, "project-build/skeleton/${kind}/question.yml").exists())
        }
        File reference = new File(root, 'project-build/skeleton/reference/references.yml')
        assertTrue(reference.isFile())
        assertEquals([references: []], new Yaml().load(reference.getText('UTF-8')))
        assertFalse(new File(root, 'project-build/skeleton/video-script').exists())
        assertFalse(new File(root, 'module').exists())
        long previous = new File(
                root, 'project-build/skeleton/roadmap/roadmap.yml'
        ).lastModified()
        assertEquals([total: 4, updated: 0], service.sync(root))
        assertEquals(previous, new File(
                root, 'project-build/skeleton/roadmap/roadmap.yml'
        ).lastModified())
    }

    @Test
    void updatesOnlyChangedSchemaAndRefusesOverwritingHumanOwnedFile() {
        File root = temp.newFolder('changes')
        prepareContract(root)
        def service = new ContractSkeletonSyncService(Logging.getLogger(getClass()))
        service.sync(root)
        File contractQuiz = new File(root, 'learning-contract/schemas/quiz.schema.json')
        Map schema = new groovy.json.JsonSlurper().parse(contractQuiz) as Map
        schema['x-description']['en'] = 'Updated authoring description'
        contractQuiz.setText(JsonOutput.prettyPrint(JsonOutput.toJson(schema)), 'UTF-8')
        assertEquals([total: 4, updated: 1], service.sync(root))
        assertTrue(new File(
                root, 'project-build/skeleton/quiz/quiz.yml'
        ).getText('UTF-8').contains('Updated authoring description'))

        File human = new File(root, 'project-build/skeleton/quiz/quiz.yml')
        human.setText('questions: []\n# authored manually\n', 'UTF-8')
        try {
            service.sync(root)
            fail('Expected overwrite protection')
        } catch (GradleException expected) {
            assertTrue(expected.message.contains('Refusing to overwrite'))
        }
        assertTrue(human.getText('UTF-8').contains('authored manually'))
    }

    @Test
    void migratesPreviousBilingualGeneratedFilesAndProtectsManualLegacyFiles() {
        File root = temp.newFolder('legacy')
        prepareContract(root)
        def service = new ContractSkeletonSyncService(Logging.getLogger(getClass()))
        File oldEnglish = new File(root, 'project-build/skeleton/quiz/en/question.yml')
        File oldVietnamese = new File(root, 'project-build/skeleton/quiz/vi/question.yml')
        assertTrue(oldEnglish.parentFile.mkdirs())
        assertTrue(oldVietnamese.parentFile.mkdirs())
        oldEnglish.setText(ContractSkeletonSyncService.GENERATED_HEADER +
                '\n# language: en\nquestions: []\n', 'UTF-8')
        oldVietnamese.setText('questions: []\n# manually maintained\n', 'UTF-8')
        try {
            service.sync(root)
            fail('Expected protection of old manually maintained content')
        } catch (GradleException expected) {
            assertTrue(expected.message.contains('Refusing to remove non-generated'))
        }
        assertFalse(new File(root, 'project-build/skeleton/quiz/quiz.yml').exists())
        assertTrue(oldVietnamese.exists())

        oldVietnamese.setText(ContractSkeletonSyncService.GENERATED_HEADER +
                '\n# language: vi\nquestions: []\n', 'UTF-8')
        assertEquals([total: 4, updated: 4], service.sync(root))
        assertFalse(oldEnglish.exists())
        assertFalse(oldVietnamese.exists())
        assertFalse(oldEnglish.parentFile.exists())
        assertFalse(oldVietnamese.parentFile.exists())
        assertTrue(new File(root, 'project-build/skeleton/quiz/quiz.yml').isFile())
        assertEquals([total: 4, updated: 0], service.sync(root))
    }

    @Test
    void renamesGeneratedQuestionSkeletonsWithoutRemovingHumanOwnedFiles() {
        File root = temp.newFolder('old-question-names')
        prepareContract(root)
        def service = new ContractSkeletonSyncService(Logging.getLogger(getClass()))
        File oldQuiz = new File(root, 'project-build/skeleton/quiz/question.yml')
        File oldInterview = new File(root, 'project-build/skeleton/interview/question.yml')
        assertTrue(oldQuiz.parentFile.mkdirs())
        assertTrue(oldInterview.parentFile.mkdirs())
        oldQuiz.setText(ContractSkeletonSyncService.GENERATED_HEADER +
                '\nquestions: []\n', 'UTF-8')
        oldInterview.setText('questions: []\n# human owned\n', 'UTF-8')
        try {
            service.sync(root)
            fail('Expected legacy manual-file protection')
        } catch (GradleException expected) {
            assertTrue(expected.message.contains('Refusing to remove non-generated'))
        }
        assertFalse(new File(root, 'project-build/skeleton/quiz/quiz.yml').exists())
        assertTrue(oldQuiz.isFile())
        assertTrue(oldInterview.isFile())

        oldInterview.setText(ContractSkeletonSyncService.GENERATED_HEADER +
                '\nquestions: []\n', 'UTF-8')
        assertEquals([total: 4, updated: 4], service.sync(root))
        assertFalse(oldQuiz.exists())
        assertFalse(oldInterview.exists())
        assertTrue(new File(root, 'project-build/skeleton/quiz/quiz.yml').isFile())
        assertTrue(new File(root, 'project-build/skeleton/interview/interview.yml').isFile())
        assertEquals([total: 4, updated: 0], service.sync(root))
    }

    private static void prepareContract(File root) {
        File schemas = new File(root, 'learning-contract/schemas')
        assertTrue(schemas.mkdirs())
        Map docs = [:]
        ['quiz', 'interview', 'roadmap', 'reference'].each { String name ->
            docs[name] = "schemas/${name}.schema.json"
        }
        write(new File(root, 'learning-contract/contract.json'), [documents: docs])
        write(new File(schemas, 'common.schema.json'), ['$defs': [:]])
        for (String name in ['quiz', 'interview', 'roadmap']) {
            String key = name == 'roadmap' ? 'roadmap' : 'questions'
            write(new File(schemas, "${name}.schema.json"), [
                    type: 'object',
                    'x-description': [vi: 'Nội dung tác giả', en: 'Authoring contract'],
                    properties: [(key): [
                            type: 'array', default: [],
                            items: [type: 'object', required: ['title'],
                                    properties: [title: [type: 'string', default: '',
                                            'x-description': [vi: 'Tên', en: 'Title']]]
                            ]
                    ]]
            ])
        }
        write(new File(schemas, 'reference.schema.json'), [
                type: 'object',
                required: ['references'],
                properties: [references: [
                        type: 'array',
                        items: [type: 'object', required: ['title', 'url'],
                                properties: [
                                        title: [type: 'string', description: 'Reference title'],
                                        url: [type: 'string', description: 'Reference URL'],
                                        description: [type: 'string']
                                ]]
                ]]
        ])
    }

    private static void write(File file, Map json) {
        file.setText(JsonOutput.prettyPrint(JsonOutput.toJson(json)), 'UTF-8')
    }
}
