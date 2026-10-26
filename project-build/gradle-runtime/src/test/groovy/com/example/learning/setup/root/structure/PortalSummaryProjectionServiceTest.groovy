package com.example.learning.setup.root.structure

import com.example.learning.setup.root.structure.service.PortalSummaryProjectionService
import groovy.json.JsonSlurper
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.yaml.snakeyaml.Yaml

import static org.junit.Assert.*

class PortalSummaryProjectionServiceTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder()

    @Test
    void projectsChapterSummaryAndRemovesStaleProjectionOnly() {
        File root = tmp.newFolder('repo')
        File moduleDir = new File(root,'module/example')
        assertTrue(moduleDir.mkdirs())
        new File(moduleDir,'gradle.properties').setText('IS_MODULE=TRUE\n','UTF-8')
        def project = ProjectBuilder.builder().withProjectDir(root).build()
        def module = ProjectBuilder.builder().withParent(project).withProjectDir(moduleDir).build()
        module.extensions.extraProperties.set('MODULE_LANGUAGE','vi')
        module.extensions.extraProperties.set('SERVICE_NAME','EXAMPLE')
        File dir = new File(moduleDir,'content/readme/docs/vi/menu/1.OOPBasic/summary')
        assertTrue(dir.mkdirs())
        new File(dir,'section.yml').setText(new Yaml().dump([
                version: 1, type: 'LEARNING_ROADMAP', title: 'Tổng kết',
                roadmapHeading: 'Mạch liên kết bài học',
                roadmap: [[id: 'oop-basic-001', order: 1, title: 'OOP là gì?',
                           description: 'Nội dung định hướng.', transition: null]],
                comparisonMarkdown: '''## 2. Bảng đối chiếu các bài học

| Tiêu chí | Bài 1 |
| --- | --- |
| Kết quả | Định hướng |
| Ví dụ | Account |
| Phạm vi | Java |
| Kiểm tra | Object |'''
        ]), 'UTF-8')
        File yaml = new File(dir,'summaries.yml')
        yaml.setText('''summaries:
  - id: oop-basic-001
    title: OOP là gì?
    summary: |-
      ## Mục đích
      Nội dung định hướng
''','UTF-8')
        PortalSummaryProjectionService service = new PortalSummaryProjectionService(project.logger)
        service.generate(project)
        File result = new File(root,'build/learning-data/java/module/EXAMPLE/summary/vi')
        File index = new File(result,'index.json')
        File content = new File(result,'content/OOPBasic/summary.json')
        assertTrue(index.isFile())
        assertTrue(content.isFile())
        assertEquals(1,(new JsonSlurper().parse(index) as Map).sectionCount)
        Map rendered = new JsonSlurper().parse(content) as Map
        assertEquals('LEARNING_ROADMAP',rendered.section.type)
        assertEquals('oop-basic-001',rendered.section.roadmap[0].id)
        assertEquals('oop-basic-001',rendered.summaries[0].id)
        service.generate(project)
        assertTrue(index.exists())
        assertTrue(yaml.delete())
        assertTrue(new File(dir,'section.yml').delete())
        service.generate(project)
        assertFalse(index.exists())
        assertFalse(content.exists())
    }
}
