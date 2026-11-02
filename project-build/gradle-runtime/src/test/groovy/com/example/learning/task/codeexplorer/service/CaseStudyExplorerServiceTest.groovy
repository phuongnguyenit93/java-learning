package com.example.learning.task.codeexplorer.service

import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.junit.Assert.*

class CaseStudyExplorerServiceTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder()

    @Test
    void scopesMethodImportsAndFilesAndReindexesNavigation() {
        Map fixture = fixture()
        def generator = new CaseStudyExplorerService()
        def dry = generator.execute(fixture.root, fixture.module, false, false, 1, 1)
        assertEquals('DRY_RUN', dry.status)
        assertEquals(1, dry.cases)
        assertFalse(new File(fixture.module, 'build/code-explorer/casestudy-pilot').exists())

        def applied = generator.execute(fixture.root, fixture.module, true, false, 1, 1)
        assertFalse(applied.complete)
        File scope = new File(fixture.module, 'build/code-explorer/casestudy-pilot/groups/1/cases/1')
        Map index = json(new File(scope, 'index.json'))
        assertEquals(3, index.files.size())
        assertTrue(index.files*.path.any { it.endsWith('/controller/Group1Controller.java') })
        assertTrue(index.files*.path.any { it.endsWith('/service/Group1Service.java') })
        assertTrue(index.files*.path.any { it.endsWith('/model/group1/case1/Patient.java') })
        assertFalse(index.files*.path.any { it.contains('/case2/') })

        String controller = new File(scope,
                'files/demo/casestudy/controller/Group1Controller.java').getText('UTF-8')
        String service = new File(scope,
                'files/demo/casestudy/service/Group1Service.java').getText('UTF-8')
        assertTrue(controller.contains('executeCase1()'))
        assertFalse(controller.contains('executeCase2()'))
        assertTrue(service.contains('executeCase1()'))
        assertFalse(service.contains('executeCase2()'))
        assertFalse(service.contains('import demo.casestudy.model.group1.case2.Other;'))
        assertTrue(service.contains('import demo.casestudy.model.group1.case1.Patient;'))

        def symbols = json(new File(scope, 'symbols.json')).symbols
        def refs = json(new File(scope, 'references.json')).references
        Map controllerCall = refs.find { it.kind == 'METHOD_CALL' && it.text == 'executeCase1' }
        assertEquals('RESOLVED', controllerCall.status)
        Map target = symbols.find { it.id == controllerCall.targets[0] }
        assertTrue(target.file.endsWith('/service/Group1Service.java'))
        assertEquals(target.line, service.readLines().findIndexOf {
            it.contains('void executeCase1(')
        } + 1)
        assertTrue(refs.any { it.text == 'Patient' && it.status == 'RESOLVED' })
        assertEquals(1, json(new File(fixture.module,
                'build/code-explorer/casestudy-pilot/index.json')).cases.size())
    }

    @Test
    void fullRunHasEveryCaseAndRejectsInvalidSelection() {
        Map fixture = fixture()
        def generator = new CaseStudyExplorerService()
        Map complete = generator.execute(fixture.root, fixture.module, true, true)
        assertEquals(2, complete.cases)
        assertTrue(complete.complete)
        File published = new File(fixture.root,
                'build/learning-data/java/module/DEMO/code-explorer/casestudy')
        assertEquals(2, json(new File(published, 'index.json')).cases.size())
        assertTrue(new File(published, 'groups/1/cases/2/index.json').isFile())
        try {
            generator.execute(fixture.root, fixture.module, true, true, 1, 1)
            fail('Pilot publish must fail')
        } catch (GradleException error) {
            assertTrue(error.message.contains('pilot cannot overwrite'))
        }
        try {
            generator.execute(fixture.root, fixture.module, true, false, 1, 3)
            fail('Unknown case must fail')
        } catch (GradleException error) {
            assertTrue(error.message.contains('unknown groupID/caseIndex'))
        }
    }

    private Map fixture() {
        File root = temp.newFolder('repo')
        File module = new File(root, 'module/demo')
        assertTrue(module.mkdirs())
        put(module, 'gradle.properties', 'IS_MODULE=TRUE')
        put(module, 'master.json',
                '{"SERVICE_NAME":{"VALUE":"DEMO"},"JAVA_BASE_PACKAGE":{"VALUE":"demo"}}')
        put(module, 'content/code-explorer/casestudy.yml',
                'version: 1\ntype: CASE_STUDY_EXPLORER\n')
        put(module, 'content/casestudy/vi/casestudy.yml', '''caseStudy:
  - groupID: 1
    cases:
      - situation: first
      - situation: second
''')
        put(module, 'src/main/java/demo/casestudy/controller/Group1Controller.java', '''package demo.casestudy.controller;
import demo.casestudy.service.Group1Service;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
@RequestMapping("/case-studies/group/1")
public class Group1Controller {
    private final Group1Service service;
    public Group1Controller(Group1Service service) { this.service = service; }
    @GetMapping("/case/1/execute")
    public void executeCase1() { service.executeCase1(); }
    @GetMapping("/case/2/execute")
    public void executeCase2() { service.executeCase2(); }
}
''')
        put(module, 'src/main/java/demo/casestudy/service/Group1Service.java', '''package demo.casestudy.service;
import demo.casestudy.model.group1.case1.Patient;
import demo.casestudy.model.group1.case2.Other;
public class Group1Service {
    public void executeCase1() { new Patient().run(); }
    public void executeCase2() { new Other().run(); }
}
''')
        put(module, 'src/main/java/demo/casestudy/model/group1/case1/Patient.java',
                'package demo.casestudy.model.group1.case1; public class Patient { public void run() {} }')
        put(module, 'src/main/java/demo/casestudy/model/group1/case2/Other.java',
                'package demo.casestudy.model.group1.case2; public class Other { public void run() {} }')
        return [root:root, module:module]
    }

    private static void put(File module, String path, String content) {
        File file = new File(module, path)
        assertTrue(file.parentFile.isDirectory() || file.parentFile.mkdirs())
        file.setText(content, 'UTF-8')
    }

    private static Map json(File path) {
        new JsonSlurper().parse(path) as Map
    }
}
