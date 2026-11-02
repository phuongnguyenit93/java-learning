package com.example.learning.task.swagger.service

import org.junit.Test
import org.yaml.snakeyaml.Yaml
import java.nio.file.Files

import static org.junit.Assert.*

class SwaggerCaseStudyEnrichmentServiceTest {
    @Test
    void dryRunApplyIdempotencyAndAuthoredContentProtection() {
        File root = Files.createTempDirectory('swagger-enrich-test-').toFile()
        try {
            File source = new File(root, 'content/casestudy/vi/casestudy.yml')
            File api = new File(root, 'src/main/resources/swagger/vi/api-descriptions.yml')
            File controller = new File(root, 'src/main/resources/swagger/vi/controller-description.yml')
            Map item = [situation: 'A < B & C', resolve: 'Use <records>', knowledge: 'SRP & OOP',
                        readmeRelated: [anchorList: ['class-001', 'solid-002']]]
            write(source, [caseStudy: [[groupID: 1, groupName: 'Nhóm <1>', cases: [item]]]])
            Map method = [summary: 'Chưa có tiêu đề', description: 'Chưa có mô tả chi tiết',
                          usage: true, methodName: 'executeCase1',
                          mappingPaths: ['/case-studies/group/1/case/1/execute'],
                          readmeRelated: [file: '', anchor: '']]
            write(api, [Group1Controller: ['executeCase1()': method]])
            write(controller, [Group1Controller: [description:
                    '<h2>Group1 Controller</h2>\n<p>Đây là mô tả</p>', readmeRelated: [file: '']]])
            byte[] beforeApi = Files.readAllBytes(api.toPath())

            def service = new SwaggerCaseStudyEnrichmentService()
            Map preview = service.execute(root, ['vi', 'en'], false)
            assertEquals('DRY_RUN', preview.mode)
            assertEquals(2, preview.filesChanged)
            assertArrayEquals(beforeApi, Files.readAllBytes(api.toPath()))
            assertEquals('SKIPPED_NO_LOCALIZED_CASE_STUDY', preview.languages.en.status)

            Map applied = service.execute(root, ['vi', 'en'], true)
            assertEquals('APPLY', applied.mode)
            Map entry = ((read(api).Group1Controller as Map)['executeCase1()']) as Map
            assertEquals('A < B & C', entry.summary)
            assertEquals('<p><strong>Hướng xử lý:</strong> Use &lt;records&gt;</p>\n' +
                    '<p><strong>Kiến thức liên quan:</strong> SRP &amp; OOP</p>', entry.description)
            assertEquals([anchorList: ['class-001', 'solid-002']], entry.readmeRelated)
            assertEquals('<h2>Nhóm &lt;1&gt;</h2>', read(controller).Group1Controller.description)
            assertFalse(read(controller).Group1Controller.containsKey('readmeRelated'))
            assertEquals(0, service.execute(root, ['vi', 'en'], false).filesChanged)

            entry.summary = 'Human-authored title'
            write(api, [Group1Controller: ['executeCase1()': entry]])
            service.execute(root, ['vi', 'en'], true)
            assertEquals('Human-authored title', read(api).Group1Controller['executeCase1()'].summary)
        } finally {
            root.deleteDir()
        }
    }

    @Test
    void mismatchedSwaggerMethodFailsBeforeAnyWrite() {
        File root = Files.createTempDirectory('swagger-enrich-mismatch-').toFile()
        try {
            File source = new File(root, 'content/casestudy/vi/casestudy.yml')
            File api = new File(root, 'src/main/resources/swagger/vi/api-descriptions.yml')
            File controller = new File(root, 'src/main/resources/swagger/vi/controller-description.yml')
            write(source, [caseStudy: [[groupID: 1, groupName: 'Group', cases: [
                    [situation: 'S', resolve: 'R', knowledge: 'K', readmeRelated: [anchorList: []]]
            ]]]])
            write(api, [Group1Controller: ['other()': [usage: true]]])
            write(controller, [Group1Controller: [description: 'Original']])
            String snapshot = api.getText('UTF-8')
            try {
                new SwaggerCaseStudyEnrichmentService().execute(root, ['vi'], true)
                fail('Expected fail closed on missing method')
            } catch (org.gradle.api.GradleException expected) {
                assertTrue(expected.message.contains('Missing/ambiguous method'))
            }
            assertEquals(snapshot, api.getText('UTF-8'))
            assertEquals('Original', read(controller).Group1Controller.description)
        } finally {
            root.deleteDir()
        }
    }

    @Test
    void controllerReadmeRelatedIsRemovedRegardlessOfItsPreviousContent() {
        File root = Files.createTempDirectory('swagger-controller-cleanup-').toFile()
        try {
            File source = new File(root, 'content/casestudy/vi/casestudy.yml')
            File api = new File(root, 'src/main/resources/swagger/vi/api-descriptions.yml')
            File controller = new File(root, 'src/main/resources/swagger/vi/controller-description.yml')
            write(source, [caseStudy: [[groupID: 1, groupName: 'Group One', cases: [
                    [situation: 'S', resolve: 'R', knowledge: 'K',
                     readmeRelated: [anchorList: []]]
            ]]]])
            write(api, [Group1Controller: ['executeCase1()': [
                    usage: true, methodName: 'executeCase1',
                    mappingPaths: ['/case-studies/group/1/case/1/execute'],
                    summary: 'Chưa có tiêu đề', description: 'Chưa có mô tả chi tiết',
                    readmeRelated: [anchorList: []]]]])
            write(controller, [Group1Controller: [
                    description: '<h2>Group One</h2>',
                    readmeRelated: [file: 'old.md', anchor: 'old-knowledge']]])
            def service = new SwaggerCaseStudyEnrichmentService()
            assertEquals(2, service.execute(root, ['vi'], false).filesChanged)
            service.execute(root, ['vi'], true)
            assertFalse(read(controller).Group1Controller.containsKey('readmeRelated'))
            assertEquals(0, service.execute(root, ['vi'], false).filesChanged)
        } finally {
            root.deleteDir()
        }
    }

    private static void write(File file, Map data) {
        file.parentFile.mkdirs()
        file.setText(new Yaml().dump(data), 'UTF-8')
    }

    private static Map read(File file) {
        new Yaml().load(file.getText('UTF-8')) as Map
    }
}
