package com.example.learning.task.sheets.service

import org.gradle.api.GradleException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.junit.Assert.*

class ModuleLanguageCloneServiceTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder()

    @Test
    void dryRunThenApplyReplacesEveryOwnedEnDirectoryAndKeepsViAndExcel() {
        Map f = fixture()
        File module = f.module as File
        write(module, 'content/video/en/menu/1.Basic/video-script.yml',
                'old optional EN video — must not be replaced')
        def service = new ModuleLanguageCloneService()
        Map beforeVi = snapshotVi(module)
        Map dry = service.execute(f.root as File, module, false)
        assertEquals('DRY_RUN', dry.status)
        assertEquals(6, dry.groups)
        assertEquals('old English', file(module, 'content/readme/docs/en/menu/Old.md').text)
        assertEquals(beforeVi, snapshotVi(module))
        assertEquals('Excel VI unaffected', file(module, 'content/sheet/vi/module.xlsx').text)
        assertEquals('Excel EN unaffected', file(module, 'content/sheet/en/module.xlsx').text)
        assertEquals('old optional EN video — must not be replaced', file(module,
                'content/video/en/menu/1.Basic/video-script.yml').text)

        Map applied = service.execute(f.root as File, module, true)
        assertEquals('CLONED', applied.status)
        assertNotNull(applied.backup)
        assertFalse(file(module, 'content/readme/docs/en/menu/Old.md').exists())
        assertEquals('Vietnamese knowledge body', file(module,
                'content/readme/docs/en/menu/1.Basic/Basic.md').text.readLines().last())
        assertTrue(file(module, 'content/readme/docs/en/LIST.md').text
                .contains('content/readme/docs/en/menu/1.Basic/Basic.md'))
        assertFalse(file(module, 'content/readme/docs/en/LIST.md').text
                .contains('content/readme/docs/vi/menu/'))
        assertEquals(beforeVi, snapshotVi(module))
        assertEquals('Excel VI unaffected', file(module, 'content/sheet/vi/module.xlsx').text)
        assertEquals('Excel EN unaffected', file(module, 'content/sheet/en/module.xlsx').text)
        assertEquals('old optional EN video — must not be replaced', file(module,
                'content/video/en/menu/1.Basic/video-script.yml').text)
        assertEquals('old English', file(new File(applied.backup as String),
                '00/content/readme/docs/en/menu/Old.md').text)
        assertEquals('UNCHANGED', service.execute(f.root as File, module, false).status)
        assertEquals('UNCHANGED', service.execute(f.root as File, module, true).status)
    }

    @Test
    void danglingViReadmeRelationBlocksDryRunAndApplyBeforeDeletingEnglish() {
        Map f = fixture()
        File module = f.module as File
        File quiz = file(module, 'content/quiz/vi/question.yml')
        quiz.text = quiz.text.replace('basic-001', 'nonexistent-001')
        [false, true].each { boolean apply ->
            try {
                new ModuleLanguageCloneService().execute(f.root as File, module, apply)
                fail('Expected dangling readmeRelated rejection')
            } catch (GradleException error) {
                assertTrue(error.message.contains('Unresolved readmeRelated'))
            }
        }
        assertEquals('old English', file(module, 'content/readme/docs/en/menu/Old.md').text)
    }

    @Test
    void simulatedFailureDuringCommitRollsBackAllPreviousEnglishSubtrees() {
        Map f = fixture()
        File module = f.module as File
        String original = file(module, 'content/readme/docs/en/menu/Old.md').text
        def service = new ModuleLanguageCloneService() {
            @Override
            protected void afterReplacement(int completed) {
                if (completed == 2) throw new GradleException('injected commit failure')
            }
        }
        try {
            service.execute(f.root as File, module, true)
            fail('Expected rollback')
        } catch (GradleException error) {
            assertTrue(error.message.contains('injected commit failure'))
        }
        assertEquals(original, file(module, 'content/readme/docs/en/menu/Old.md').text)
        assertFalse(file(module, 'content/readme/docs/en/menu/1.Basic/Basic.md').exists())
        assertEquals('old metadata', file(module,
                'content/readme/metadata/en/knowledge-metadata.yml').text)
        assertEquals('old English roadmap', file(module,
                'content/roadmap/en/roadmap.yml').text)
    }

    @Test
    void invalidViAnchorMetadataBlocksReplacement() {
        Map f = fixture()
        File module = f.module as File
        File metadata = file(module, 'content/readme/metadata/vi/knowledge-metadata.yml')
        metadata.text = metadata.text.replace('basic-001:', 'wrong-001:')
        try {
            new ModuleLanguageCloneService().execute(f.root as File, module, true)
            fail('Expected metadata mismatch')
        } catch (GradleException error) {
            assertTrue(error.message.contains('metadata does not match'))
        }
        assertEquals('old English', file(module, 'content/readme/docs/en/menu/Old.md').text)
    }

    @Test
    void invalidRoadmapModuleRouteBlocksReplacement() {
        Map f = fixture()
        File module = f.module as File
        File roadmap = file(module, 'content/roadmap/vi/roadmap.yml')
        roadmap.text = roadmap.text.replace('DEMO_ROUTE', 'MISSING_ROUTE')
        try {
            new ModuleLanguageCloneService().execute(f.root as File, module, true)
            fail('Expected related module route blocker')
        } catch (GradleException error) {
            assertTrue(error.message.contains('relatedModules.routeId'))
        }
        assertEquals('old English', file(module, 'content/readme/docs/en/menu/Old.md').text)
    }

    private Map fixture() {
        File root = tmp.newFolder('repo')
        File module = file(root, 'module/demo')
        assertTrue(module.mkdirs())
        write(module, 'gradle.properties', '# Gradle module')
        write(module, 'master.json', '{"SERVICE_NAME":{"VALUE":"DEMO_ROUTE"}}')
        write(module, 'content/readme/docs/vi/BASE.md', '# Vietnamese introduction')
        write(module, 'content/readme/docs/vi/LIST.md',
                '# README MODULE STRUCTURE (VI)\n\n' +
                '* **1.Basic**\n    * [Basic](content/readme/docs/vi/menu/1.Basic/Basic.md)\n')
        write(module, 'content/readme/docs/vi/menu/1.Basic/Basic.md',
                '# Basic\n\n## <a id="basic-001">Vietnamese lesson</a>\n\nVietnamese knowledge body')
        write(module, 'content/readme/metadata/vi/knowledge-metadata.yml', '''
1.Basic/Basic.md:
  basic-001:
    difficulty: BASIC
    aiGenerated: true
    reviewed: false
    isImportant: false
'''.trim())
        write(module, 'content/roadmap/vi/roadmap.yml', '''
roadmap:
  - id: basic
    title: Vietnamese milestone
    purpose: Purpose of this milestone
    objectives: [Learn one thing]
    relatedKnowledge: [Basic]
    relatedModules:
      - routeId: DEMO_ROUTE
        label: Demo
        note: Illustrate a stable module route
'''.trim())
        write(module, 'content/quiz/vi/question.yml', '''
questions:
  - id: quiz-001
    question: A Vietnamese question?
    answers:
      - {id: A, answer: One, explanation: Correct}
      - {id: B, answer: Two, explanation: Wrong}
      - {id: C, answer: Three, explanation: Wrong}
      - {id: D, answer: Four, explanation: Wrong}
    correctAnswerId: A
    aiGenerated: true
    reviewed: false
    readmeRelated: {file: "1.Basic/Basic.md", anchor: "basic-001"}
    apiRelated: {controller: ExampleController, methodSignature: "demo()"}
'''.trim())
        write(module, 'content/interview/vi/question.yml', '''
questions:
  - question: Vietnamese interview question?
    answer: Vietnamese answer
    aiGenerated: true
    reviewed: false
    readmeRelated: {file: "1.Basic/Basic.md", anchor: "basic-001"}
    apiRelated: {controller: "", methodSignature: ""}
'''.trim())
        write(module, 'content/video/vi/menu/1.Basic/video-script.yml', '''
title: Vietnamese title
video:
  url: ''
sections:
  - title: Vietnamese lesson
    items:
      - type: SCENE
        time: 00:00-00:10
        visual: Vietnamese visual
        script: Vietnamese narration
        purpose: Vietnamese purpose
'''.trim())
        write(module, 'src/main/resources/swagger/vi/api-descriptions.yml', '''
ExampleController:
  "demo()":
    summary: Vietnamese summary
    readmeRelated: {file: "1.Basic/Basic.md", anchor: "basic-001"}
'''.trim())
        write(module, 'content/readme/docs/en/menu/Old.md', 'old English')
        write(module, 'content/readme/metadata/en/knowledge-metadata.yml', 'old metadata')
        write(module, 'content/roadmap/en/roadmap.yml', 'old English roadmap')
        write(module, 'src/main/resources/swagger/en/old.yml', 'old swagger')
        write(module, 'content/sheet/vi/module.xlsx', 'Excel VI unaffected')
        write(module, 'content/sheet/en/module.xlsx', 'Excel EN unaffected')
        return [root: root, module: module]
    }

    private static File file(File base, String relative) {
        return new File(base, relative)
    }

    private static void write(File root, String relative, String contents) {
        File target = file(root, relative)
        if (!target.parentFile.exists()) assertTrue(target.parentFile.mkdirs())
        target.setText(contents, 'UTF-8')
    }

    private static Map snapshotVi(File module) {
        return ModuleLanguageCloneService.OWNED.collectEntries { String area ->
            [(area): ModuleLanguageCloneService.readTree(file(module, area + '/vi'))
                    .collectEntries { String path, byte[] bytes ->
                        [(path): bytes.encodeBase64().toString()]
                    }]
        }
    }
}
