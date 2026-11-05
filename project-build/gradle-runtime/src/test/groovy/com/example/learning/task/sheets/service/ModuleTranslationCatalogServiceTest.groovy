package com.example.learning.task.sheets.service

import org.gradle.api.GradleException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.junit.Assert.*

class ModuleTranslationCatalogServiceTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder()

    @Test
    void summaryTranslationInventoryAndProtectedIdsAreChecked() {
        Map f = fixture()
        File module = f.module as File
        String viSection = new org.yaml.snakeyaml.Yaml().dump([
                version: 1, type: 'LEARNING_ROADMAP', title: 'Mục tiêu',
                roadmapHeading: 'Mạch liên kết bài học',
                roadmap: [[id: 'basic-001', order: 1, title: 'Bài học',
                           description: 'Nội dung.', transition: null]],
                comparisonMarkdown: '''## 2. Bảng đối chiếu các bài học

| Tiêu chí | Bài 1 |
| --- | --- |
| Kết quả | Nội dung |
| Ví dụ | Account |
| Phạm vi | Java |
| Kiểm tra | Object |'''
        ])
        String summaryYaml = 'summaries:\n  - id: basic-001\n    title: Bài học\n    summary: "## Nội dung"\n'
        for (String lang : ['vi', 'en']) {
            write(module, "content/readme/docs/${lang}/menu/1.Basic/summary/section.yml",viSection)
            write(module, "content/readme/docs/${lang}/menu/1.Basic/summary/summaries.yml",summaryYaml)
        }
        List<Map> files = ModuleTranslationCatalogService.inventory(module)
        assertTrue(files.any { it.type == 'SUMMARY_SECTION' })
        assertTrue(files.any { it.type == 'SUMMARY_LESSONS' })
        byte[] source = summaryYaml.getBytes('UTF-8')
        byte[] translated = summaryYaml.replace('Bài học','Lesson')
                .replace('Nội dung','Contents').getBytes('UTF-8')
        ModuleTranslationValidationService.compare('SUMMARY_LESSONS', source, translated)
        try {
            ModuleTranslationValidationService.compare('SUMMARY_LESSONS', source,
                    summaryYaml.replace('basic-001','other-001').getBytes('UTF-8'))
            fail('Summary ID should be protected')
        } catch (GradleException expected) {
            assertTrue(expected.message.contains('Protected translation field'))
        }
    }


    @Test
    void preparesModuleWideWorkerJobsAndValidatesOnlyTranslatableFields() {
        Map f = fixture()
        def service = new ModuleTranslationCatalogService()
        Map dry = service.execute(f.root as File, f.module as File, 'trial', 'PREPARE', false)
        assertEquals('DRY_RUN', dry.status)
        assertEquals(7, dry.total)
        assertFalse((dry.manifest as File).exists())
        Map prepared = service.execute(f.root as File, f.module as File,
                'trial', 'PREPARE', true)
        assertEquals('PREPARED', prepared.status)
        assertTrue((prepared.manifest as File).isFile())
        assertEquals(7, prepared.jobCount)
        assertEquals('NORMAL', prepared.workerMode)
        assertEquals(3, prepared.maxWorkers)
        List<Map> common = (prepared.jobs as List<Map>).findAll {
            it.rule == 'STEP_11_WORKER_COMMON.md'
        }
        assertEquals(3, common.size())
        assertTrue((prepared.jobs as List<Map>).every { it.files.size() == 1 })
        assertEquals(1, (prepared.jobs as List<Map>).count {
            it.rule == 'STEP_11_WORKER_CASE_STUDY.md'
        })
        assertEquals('module/demo', new groovy.json.JsonSlurper()
                .parse(prepared.manifest as File).module)
        assertEquals('TARGET_SNAPSHOT', new groovy.json.JsonSlurper()
                .parse(prepared.manifest as File).baselinePhase)
        File quiz = new File(f.module as File,
                'content/quiz/en/quiz.yml')
        quiz.setText(quiz.getText('UTF-8').replace('Câu hỏi', 'English question')
                .replace('Giải thích', 'Explanation'), 'UTF-8')
        File doc = new File(f.module as File,
                'content/readme/docs/en/menu/1.Basic/knowledge/Basic.md')
        doc.setText(doc.getText('UTF-8').replace('Bài học', 'Learning section')
                .replace('Nội dung', 'English explanation'), 'UTF-8')

        Map checked = service.execute(f.root as File, f.module as File,
                'trial', 'VALIDATE', false)
        assertEquals('VALIDATED_STRUCTURE', checked.status)
        assertEquals(7, checked.total)
        assertEquals(2, checked.translated)
        assertEquals(5, checked.pending)
        assertEquals(7, checked.jobCount)
        assertTrue(checked.semanticQaRequired)
        File snapshot = new File(f.module as File,
                'build/step11/trial/baseline/content/quiz/en/quiz.yml')
        assertTrue(snapshot.getText('UTF-8').contains('Câu hỏi'))
        assertFalse(snapshot.getText('UTF-8').contains('English question'))
        File work = ModuleTranslationCatalogService.workFile(f.root as File,
                'module/demo', 'trial', 'content/quiz/en/quiz.yml')
        assertTrue(work.isFile())
        assertEquals(snapshot.getText('UTF-8'), work.getText('UTF-8'))
    }

    @Test
    void resolvesTypedRulesOnlyFromDedicatedStep11WorkerFolder() {
        Map f = fixture()
        File root = f.root as File
        File module = f.module as File
        List<Map> jobs = ModuleTranslationCatalogService.workerJobs(
                ModuleTranslationCatalogService.inventory(module))
        Set<String> rules = jobs.collect { it.rule as String } as Set
        rules.each { String rule ->
            if (rule != 'STEP_11_WORKER_COMMON.md') {
                write(root, 'module-generate-agent/worker/STEP_11_WORKER/' + rule,
                        '# Rule')
            }
        }
        // An outdated rule in worker/ must not substitute for the new path.
        write(root, 'module-generate-agent/worker/STEP_11_WORKER_COMMON.md',
                '# Obsolete path')
        GradleException missing = assertThrows(GradleException) {
            new ModuleTranslationCatalogService().execute(root, module,
                    'rule-location', 'PREPARE', false)
        }
        assertTrue(missing.message.contains('STEP11_WORKER_RULE_MISSING'))
        assertTrue(missing.message.contains(
                'STEP_11_WORKER' + File.separator + 'STEP_11_WORKER_COMMON.md'))

        write(root, 'module-generate-agent/worker/STEP_11_WORKER/' +
                'STEP_11_WORKER_COMMON.md', '# Canonical rule')
        Map prepared = new ModuleTranslationCatalogService().execute(
                root, module, 'rule-location', 'PREPARE', false)
        assertEquals('DRY_RUN', prepared.status)
        assertEquals(jobs.size(), prepared.jobCount)
        assertTrue((prepared.jobs as List<Map>).every {
            !(it.rule as String).contains('/')
        }) // Stable manifest values for already-prepared runs.
    }

    @Test
    void workerModesRequireExplicitPositiveCountForSpecialAndNormalCapsAtThree() {
        Map f = fixture()
        def service = new ModuleTranslationCatalogService()
        assertEquals(3, service.execute(f.root as File, f.module as File,
                'mode-normal', 'PREPARE', false).maxWorkers)
        assertEquals(2, service.execute(f.root as File, f.module as File,
                'mode-normal2', 'PREPARE', false, 'NORMAL', 2).maxWorkers)
        assertThrows(GradleException) {
            service.execute(f.root as File, f.module as File,
                    'special-no-n', 'PREPARE', false, 'SPECIAL', null)
        }
        assertThrows(GradleException) {
            service.execute(f.root as File, f.module as File,
                    'special-zero', 'PREPARE', false, 'SPECIAL', 0)
        }
        assertThrows(GradleException) {
            service.execute(f.root as File, f.module as File,
                    'normal-four', 'PREPARE', false, 'NORMAL', 4)
        }
        Map special = service.execute(f.root as File, f.module as File,
                'special-four', 'PREPARE', true, 'SPECIAL', 4)
        assertEquals(4, special.maxWorkers)
        assertEquals('SPECIAL', special.workerMode)
        Map manifest = new groovy.json.JsonSlurper().parse(special.manifest as File) as Map
        assertEquals(4, manifest.maxWorkers)
        assertEquals('SPECIAL', manifest.workerMode)
    }

    @Test
    void summaryPairIsRequiredButEachFileGetsItsOwnWorkerJob() {
        Map f = fixture()
        File module = f.module as File
        for (String lang : ['vi', 'en']) {
            write(module, "content/readme/docs/${lang}/menu/1.Basic/summary/section.yml",
                    'title: Tiêu đề\nroadmap: []\n')
            write(module, "content/readme/docs/${lang}/menu/1.Basic/summary/summaries.yml",
                    'summaries: []\n')
        }
        List<Map> jobs = ModuleTranslationCatalogService.workerJobs(
                ModuleTranslationCatalogService.inventory(module))
        List<Map> summaries = jobs.findAll {
            it.rule == 'STEP_11_WORKER_SUMMARY.md'
        }
        assertEquals(2, summaries.size())
        assertTrue(summaries.every {
            it.files.size() == 1 && it.id.contains('/menu/1.Basic/summary/')
        })
        assertEquals(9, jobs.size())

        new File(module,
                'content/readme/docs/vi/menu/1.Basic/summary/summaries.yml').delete()
        new File(module,
                'content/readme/docs/en/menu/1.Basic/summary/summaries.yml').delete()
        GradleException ex = assertThrows(GradleException) {
            ModuleTranslationCatalogService.workerJobs(
                    ModuleTranslationCatalogService.inventory(module))
        }
        assertTrue(ex.message.contains('INCOMPLETE_SUMMARY_PAIR'))
    }

    @Test
    void moduleWideCaseStudyTranslatesProseButLocksReadmeRelationsAndCodeFences() {
        String vi = '''caseStudy:
  - groupID: 1
    groupName: Tư duy thiết kế
    cases:
      - situation: Tình huống thực tế
        readmeRelated: {anchorList: [basic-001]}
        resolve: Giải pháp
        knowledge: Nguyên lý
        code: |-
          ## Mô tả
          ```java
          class Keep {}
          ```
'''
        String en = vi.replace('Tư duy thiết kế', 'Design thinking')
                .replace('Tình huống thực tế', 'Real scenario')
                .replace('Giải pháp', 'Solution')
                .replace('Nguyên lý', 'Principle')
                .replace('Mô tả', 'Explanation')
        ModuleTranslationValidationService.compare('MODULE_CASE_STUDY',
                vi.getBytes('UTF-8'), en.getBytes('UTF-8'))
        assertThrows(GradleException) {
            ModuleTranslationValidationService.compare('MODULE_CASE_STUDY',
                    vi.getBytes('UTF-8'),
                    en.replace('basic-001', 'wrong-anchor').getBytes('UTF-8'))
        }
        assertThrows(GradleException) {
            ModuleTranslationValidationService.compare('MODULE_CASE_STUDY',
                    vi.getBytes('UTF-8'),
                    en.replace('class Keep', 'class Broken').getBytes('UTF-8'))
        }
        assertFalse(ModuleTranslationValidationService.editable('MODULE_QUIZ',
                'quiz[0].questions[1].correctAnswerId'))
        assertTrue(ModuleTranslationValidationService.editable('MODULE_INTERVIEW',
                'interviewQuestion[0].questions[2].knowledge'))
        assertTrue(ModuleTranslationValidationService.editable('MODULE_CASE_STUDY',
                'caseStudy[0].cases[1].resolve'))
    }

    @Test
    void rejectModificationToTechnicalQuestionFields() {
        Map f = fixture()
        def service = new ModuleTranslationCatalogService()
        service.execute(f.root as File, f.module as File, 'review1', 'PREPARE', true)
        File quiz = new File(f.module as File, 'content/quiz/en/quiz.yml')
        quiz.setText(quiz.getText('UTF-8').replace('correctAnswerId: A',
                'correctAnswerId: B'), 'UTF-8')
        try {
            service.execute(f.root as File, f.module as File, 'review1', 'VALIDATE', false)
            fail('Expected protected correctAnswerId guard')
        } catch (GradleException error) {
            assertTrue(error.message.contains('correctAnswerId'))
        }
    }

    @Test
    void prepareCopiesAlreadyTranslatedTargetExactlyInsteadOfRequiringViClone() {
        Map f = fixture()
        File root = f.root as File
        File module = f.module as File
        File source = new File(module,
                'content/readme/docs/en/menu/1.Basic/knowledge/Basic.md')
        source.setText(source.getText('UTF-8').replace('Nội dung',
                'English description'), 'UTF-8')
        String before = ModuleTranslationCatalogService.sha(source.bytes)
        assertNotEquals(ModuleTranslationCatalogService.sha(new File(module,
                'content/readme/docs/vi/menu/1.Basic/knowledge/Basic.md').bytes),
                before)
        ModuleTranslationCatalogService catalog = new ModuleTranslationCatalogService()
        Map dry = catalog.execute(root, module, 'newrun', 'PREPARE', false)
        assertEquals('DRY_RUN', dry.status)
        assertFalse((dry.manifest as File).exists())
        Map prepared = catalog.execute(root, module, 'newrun', 'PREPARE', true)
        assertEquals('PREPARED', prepared.status)
        assertEquals('TARGET_SNAPSHOT', new groovy.json.JsonSlurper()
                .parse(prepared.manifest as File).baselinePhase)
        File work = ModuleTranslationCatalogService.workFile(root, 'module/demo',
                'newrun', 'content/readme/docs/en/menu/1.Basic/knowledge/Basic.md')
        assertEquals(before, ModuleTranslationCatalogService.sha(work.bytes))
        assertEquals(before, ModuleTranslationCatalogService.sha(source.bytes))
        File baseline = new File(module, 'build/step11/newrun/baseline/' +
                'content/readme/docs/en/menu/1.Basic/knowledge/Basic.md')
        assertEquals(before, ModuleTranslationCatalogService.sha(baseline.bytes))
        Map progress = new ModuleTranslationProgressService().initialize(root,
                module, 'newrun')
        assertEquals('CREATED', progress.result)
        assertEquals(prepared.jobCount, progress.pending)
        assertEquals(0, catalog.execute(root, module, 'newrun',
                'VALIDATE', false).translated)
        assertThrows(GradleException) {
            catalog.execute(root, module, 'newrun', 'PREPARE', true)
        }
    }

    @Test
    void prepareStagesExistingTargetEvenWhenItNeedsStructuralRepair() {
        Map f = fixture()
        File root = f.root as File
        File module = f.module as File
        File quiz = new File(module, 'content/quiz/en/quiz.yml')
        quiz.setText(quiz.getText('UTF-8')
                .replace('correctAnswerId: A', 'correctAnswerId: B'), 'UTF-8')
        String expected = ModuleTranslationCatalogService.sha(quiz.bytes)
        ModuleTranslationCatalogService catalog = new ModuleTranslationCatalogService()
        Map prepared = catalog.execute(root, module, 'repair', 'PREPARE', true)
        assertEquals('PREPARED', prepared.status)
        File staged = ModuleTranslationCatalogService.workFile(root,
                'module/demo', 'repair', 'content/quiz/en/quiz.yml')
        assertEquals(expected, ModuleTranslationCatalogService.sha(staged.bytes))
        assertEquals('CREATED', new ModuleTranslationProgressService()
                .initialize(root, module, 'repair').result)
        // Validation is still strict, but it happens AFTER extraction.
        assertThrows(GradleException) {
            catalog.execute(root, module, 'repair', 'VALIDATE', false)
        }
    }

    @Test
    void rejectChangesToProtectedMarkdownStructures() {
        String original = '''# Hướng dẫn
## <a id="intro-001">Giới thiệu</a>
- Câu đầu
- Câu hai
Liên kết [đọc thêm](https://example.org/docs).
~~~java
System.out.println("KEEP");
~~~
'''.stripIndent()
        String good = original.replace('Hướng dẫn', 'Guide')
                .replace('Giới thiệu', 'Introduction')
                .replace('Câu đầu', 'First sentence')
                .replace('Câu hai', 'Second sentence')
                .replace('đọc thêm', 'read more')
        ModuleTranslationValidationService.compare('README',
                original.getBytes('UTF-8'), good.getBytes('UTF-8'))
        String badLink = good.replace('https://example.org/docs',
                'https://example.org/other')
        try {
            ModuleTranslationValidationService.compare('README',
                    original.getBytes('UTF-8'), badLink.getBytes('UTF-8'))
            fail('Expected changed URL rejection')
        } catch (GradleException error) {
            assertTrue(error.message.contains('link destinations'))
        }
        String badCode = good.replace('KEEP', 'CHANGED')
        try {
            ModuleTranslationValidationService.compare('README',
                    original.getBytes('UTF-8'), badCode.getBytes('UTF-8'))
            fail('Expected code fence rejection')
        } catch (GradleException error) {
            assertTrue(error.message.contains('fenced code'))
        }
    }

    @Test
    void whitelistAllowsOnlyExpectedYamlTextFields() {
        assertTrue(ModuleTranslationValidationService.editable('ROADMAP',
                'roadmap[0].relatedModules[0].note'))
        assertTrue(ModuleTranslationValidationService.editable('REFERENCE',
                'references[0].description'))
        assertTrue(ModuleTranslationValidationService.editable('REFERENCE',
                'references[0].title'))
        assertFalse(ModuleTranslationValidationService.editable('REFERENCE',
                'references[0].url'))
        assertFalse(ModuleTranslationValidationService.editable('ROADMAP',
                'roadmap[0].relatedModules[0].routeId'))
        assertTrue(ModuleTranslationValidationService.editable('QUIZ',
                'questions[0].answers[3].explanation'))
        assertFalse(ModuleTranslationValidationService.editable('QUIZ',
                'questions[0].correctAnswerId'))
        assertTrue(ModuleTranslationValidationService.editable(
                'SWAGGER_API_CASES', 'Controller.method().cases[0].description'))
        ['source', 'program', 'expect.results', 'expect.conclusion'].each { String field ->
            assertTrue('Observation text should be translatable: ' + field,
                    ModuleTranslationValidationService.editable(
                            'SWAGGER_API_CASES',
                            'Controller.method().cases[0].observations[1].' + field))
        }
        ['type', 'expect.keyword', 'expect.unknownField'].each { String field ->
            assertFalse('Observation technical field should be protected: ' + field,
                    ModuleTranslationValidationService.editable(
                            'SWAGGER_API_CASES',
                            'Controller.method().cases[0].observations[1].' + field))
        }
        assertFalse(ModuleTranslationValidationService.editable(
                'SWAGGER_API_CASES', 'Controller.method().cases[0].response'))
        assertTrue(ModuleTranslationValidationService.editable(
                'SWAGGER_API_DESCRIPTIONS', 'DemoController.execute(String).params[0].summary'))
        assertTrue(ModuleTranslationValidationService.editable(
                'SWAGGER_API_DESCRIPTIONS', 'DemoController.execute(String).params[0].description'))
        assertFalse(ModuleTranslationValidationService.editable(
                'SWAGGER_API_DESCRIPTIONS', 'DemoController.execute(String).params[0].name'))
    }

    @Test
    void swaggerObservationsAllowProseTranslationButProtectCodeMatchersAndSymbols() {
        String vi = '''Group1Controller:
  executeCase9(String):
    methodName: executeCase9
    cases:
      - id: case_001
        title: Kiểm tra CPU
        description: Chạy workload
        detailDescription: "<p>Chạy <code>compute(2000000)</code>.</p>"
        input:
          queryParams: {mode: cpu}
        status: null
        response: null
        observations:
          - type: LOG
            source: Log tại Group1Service.executeCase9
            program: Terminal hoặc IDE
            expect:
              keyword: "workload completed; result="
              results: "<p>Tìm <code>workload completed; result=</code>.</p>"
              conclusion: "<p>Không đo heap.</p>"
          - type: JFR
            source: Bản ghi jdk.ExecutionSample
            program: Java Mission Control
            expect:
              keyword: jdk.ExecutionSample
              results: "<p>Xem <code>jdk.ExecutionSample</code> để xác minh.</p>"
              conclusion: "<p>Không kết luận bottleneck.</p>"
'''
        String en = vi.replace('Log tại ', 'Log from ')
                .replace('Terminal hoặc IDE', 'Terminal or IDE')
                .replace('Tìm <code>workload completed; result=</code>.',
                        'Find <code>workload completed; result=</code>.')
                .replace('Không đo heap.', 'This does not measure heap memory.')
                .replace('Bản ghi jdk.ExecutionSample', 'Recording of jdk.ExecutionSample')
                .replace('Xem <code>jdk.ExecutionSample</code> để xác minh.',
                        'Inspect <code>jdk.ExecutionSample</code> to verify.')
                .replace('Không kết luận bottleneck.', 'Do not infer a bottleneck.')
        ModuleTranslationValidationService.compare('SWAGGER_API_CASES',
                vi.getBytes('UTF-8'), en.getBytes('UTF-8'))

        GradleException typeError = assertThrows(GradleException) {
            ModuleTranslationValidationService.compare('SWAGGER_API_CASES',
                    vi.getBytes('UTF-8'),
                    en.replace('type: LOG', 'type: METRIC').getBytes('UTF-8'))
        }
        assertTrue(typeError.message.contains('Protected translation field'))

        GradleException keywordError = assertThrows(GradleException) {
            ModuleTranslationValidationService.compare('SWAGGER_API_CASES',
                    vi.getBytes('UTF-8'),
                    en.replace('keyword: "workload completed; result="',
                            'keyword: "changed"').getBytes('UTF-8'))
        }
        assertTrue(keywordError.message.contains('Protected translation field'))

        GradleException codeError = assertThrows(GradleException) {
            ModuleTranslationValidationService.compare('SWAGGER_API_CASES',
                    vi.getBytes('UTF-8'),
                    en.replace('<code>jdk.ExecutionSample</code>',
                            '<code>jdk.OtherEvent</code>').getBytes('UTF-8'))
        }
        assertTrue(codeError.message.contains('HTML code literals'))

        GradleException symbolError = assertThrows(GradleException) {
            ModuleTranslationValidationService.compare('SWAGGER_API_CASES',
                    vi.getBytes('UTF-8'),
                    en.replace('Group1Service.executeCase9',
                            'Group1Service.otherMethod').getBytes('UTF-8'))
        }
        assertTrue(symbolError.message.contains('qualified technical identifiers'))
    }

    @Test
    void referenceTranslationAllowsDisplayTextButProtectsSourceUrlAndEntryOrder() {
        String vi = '''references:
  - title: Tài liệu tham khảo
    url: https://example.org/reference
    description: Giải thích ngắn
'''
        String en = vi.replace('Tài liệu tham khảo', 'Reference document')
                .replace('Giải thích ngắn', 'Short explanation')
        ModuleTranslationValidationService.compare('REFERENCE',
                vi.getBytes('UTF-8'), en.getBytes('UTF-8'))
        assertThrows(GradleException) {
            ModuleTranslationValidationService.compare('REFERENCE',
                    vi.getBytes('UTF-8'), en.replace('https://example.org/reference',
                            'https://example.org/other').getBytes('UTF-8'))
        }
        assertThrows(GradleException) {
            ModuleTranslationValidationService.compare('REFERENCE',
                    vi.getBytes('UTF-8'), 'references: []\n'.getBytes('UTF-8'))
        }
    }

    @Test
    void catalogIncludesNestedAndLegacyRootKnowledgeButIgnoresAuxiliaryChapterFiles() {
        Map f = fixture()
        File module = f.module as File
        write(module, 'content/readme/docs/vi/menu/1.Basic/chapter-links.md',
                '# Not a Knowledge article')
        write(module, 'content/readme/docs/en/menu/1.Basic/extra.md',
                '# English-only auxiliary file')
        write(module, 'content/readme/docs/vi/menu/1.Basic/Legacy.md',
                '# Legacy Knowledge location is not classified')
        write(module, 'content/readme/docs/vi/menu/LegacyRoot.md',
                '# Legacy root Knowledge')
        write(module, 'content/readme/docs/en/menu/LegacyRoot.md',
                '# Legacy root Knowledge')
        List<Map> inventory = ModuleTranslationCatalogService.inventory(module)
        assertEquals(8, inventory.size())
        assertEquals(2, inventory.count { it.type == 'README' })
        assertEquals(1, inventory.count { it.type == 'MODULE_QUIZ' })
        assertEquals(1, inventory.count { it.type == 'MODULE_INTERVIEW' })
        assertEquals(1, inventory.count { it.type == 'MODULE_CASE_STUDY' })
        assertTrue(inventory.any { it.enPath ==
                'content/readme/docs/en/menu/1.Basic/knowledge/Basic.md' })
        assertTrue(inventory.any { it.enPath ==
                'content/readme/docs/en/menu/LegacyRoot.md' })
        assertFalse(inventory.any { it.enPath?.contains('chapter-links') ||
                it.enPath?.contains('Legacy.md') || it.enPath?.contains('extra.md') })
    }

    @Test
    void rejectsPerChapterMetadataTranslationsEvenIfQuestionsAreValid() {
        Map f = fixture()
        File module = f.module as File
        def service = new ModuleTranslationCatalogService()
        service.execute(f.root as File, module, 'metadata-guard', 'PREPARE', true)
        File metadata = new File(module,
                'content/readme/docs/en/menu/1.Basic/metadata/knowledge-metadata.yml')
        metadata.setText(metadata.getText('UTF-8').replace('BASIC', 'ADVANCED'), 'UTF-8')
        try {
            service.execute(f.root as File, module, 'metadata-guard', 'VALIDATE', false)
            fail('Chapter metadata must remain byte identical to VI')
        } catch (GradleException expected) {
            assertTrue(expected.message.contains('Protected EN file changed'))
        }
    }

    @Test
    void videoDifferencesNeverEnterStep14InventoryOrBlockMandatoryTranslation() {
        Map f = fixture()
        File module = f.module as File
        File enVideo = new File(module,
                'content/readme/docs/en/menu/1.Basic/video/video-script.yml')
        assertTrue(enVideo.delete())
        write(module, 'content/readme/docs/en/menu/2.Orphan/video/video-script.yml',
                'English script authored after STEP 15')

        def service = new ModuleTranslationCatalogService()
        List<Map> inventory = ModuleTranslationCatalogService.inventory(module)
        assertEquals(7, inventory.size())
        assertFalse(inventory.any { it.enPath?.contains('/video/') })
        assertEquals('PREPARED', service.execute(f.root as File, module,
                'video-independent', 'PREPARE', true).status)
        assertEquals('VALIDATED_STRUCTURE', service.execute(f.root as File, module,
                'video-independent', 'VALIDATE', false).status)
    }

    private Map fixture() {
        File root = temp.newFolder('repo')
        File module = new File(root, 'module/demo')
        assertTrue(module.mkdirs())
        write(module, 'gradle.properties', '# module')
        [
                'content/readme/docs/vi/BASE.md': '# Giới thiệu',
                'content/readme/docs/vi/LIST.md':
                        '# README MODULE STRUCTURE (VI)\n* [Basic](content/readme/docs/vi/menu/1.Basic/knowledge/Basic.md)',
                'content/readme/docs/vi/menu/1.Basic/knowledge/Basic.md':
                        '# Chương\n## <a id="basic-001">Bài học</a>\nNội dung giải thích',
                'content/readme/docs/vi/menu/1.Basic/metadata/knowledge-metadata.yml':
                        'sections:\n  basic-001: {difficulty: BASIC}',
                'content/reference/vi/references.yml':
                        'references:\n  - title: Tài liệu\n    url: https://example.org/reference\n    description: Mô tả nguồn',
                'content/roadmap/vi/roadmap.yml':
                        'roadmap:\n  - id: stable\n    title: Tiêu đề\n    purpose: Mục tiêu\n    objectives: [Hiểu nguyên lý]',
                'content/readme/docs/vi/menu/1.Basic/quiz/question.yml': '''questions:
  - id: stable-id
    question: Câu hỏi?
    answers:
      - {id: A, answer: Đúng, explanation: Giải thích đúng}
      - {id: B, answer: Sai, explanation: Giải thích sai}
      - {id: C, answer: Không, explanation: Giải thích không}
      - {id: D, answer: Khác, explanation: Giải thích khác}
    correctAnswerId: A


    readmeRelated: {anchor: basic-001}
    apiRelated: {controller: "", methodSignature: ""}
''',
                'content/readme/docs/vi/menu/1.Basic/questions/interview.yml': '''questions:
  - question: Câu phỏng vấn?
    answer: Trả lời thực hành


    readmeRelated: {anchor: basic-001}
    apiRelated: {controller: "", methodSignature: ""}
''',
                'content/readme/docs/vi/menu/1.Basic/questions/summary.yml': '''questions:
  - question: Câu hỏi tổng hợp?
    answer: Đáp án tự đánh giá.
''',
                'content/readme/docs/vi/menu/1.Basic/video/video-script.yml':
                        'title: Tiêu đề\nsections:\n  - title: Bài học\n    items:\n      - {type: SCENE, time: "00:00-00:10", visual: Hình ảnh, script: Thuyết minh, purpose: Mục đích}',
                'content/quiz/vi/quiz.yml': '''quiz:
  - groupID: 1
    groupName: Nền tảng
    questions:
      - question: Câu hỏi?
        readmeRelated: {anchorList: [basic-001]}
        answers:
          - {id: A, answer: Đúng, explanation: Giải thích đúng}
          - {id: B, answer: Sai, explanation: Giải thích sai}
          - {id: C, answer: Không, explanation: Giải thích không}
          - {id: D, answer: Khác, explanation: Giải thích khác}
        correctAnswerId: A
''',
                'content/interview/vi/interview.yml': '''interviewQuestion:
  - groupID: 1
    groupName: Phỏng vấn
    questions:
      - question: Câu phỏng vấn?
        readmeRelated: {anchorList: [basic-001]}
        knowledge: Nguyên lý
        answer: Trả lời thực hành
''',
                'content/casestudy/vi/casestudy.yml': '''caseStudy:
  - groupID: 1
    groupName: Trường hợp
    cases:
      - situation: Tình huống thực tế
        readmeRelated: {anchorList: [basic-001]}
        resolve: Cách giải quyết
        knowledge: Kiến thức liên quan
        code: |-
          ## Hướng dẫn
          \u0060\u0060\u0060java
          class Keep {}
          \u0060\u0060\u0060
''',
        ].each { String viPath, String content ->
            write(module, viPath, content)
            String enPath = viPath.replace('/vi/', '/en/')
            String english = viPath.endsWith('/LIST.md')
                    ? content.replace('content/readme/docs/vi/menu/',
                            'content/readme/docs/en/menu/')
                            .replace('README MODULE STRUCTURE (VI)',
                                    'README MODULE STRUCTURE (EN)')
                    : content
            write(module, enPath, english)
        }
        return [root: root, module: module]
    }

    private static void write(File root, String path, String value) {
        File file = new File(root, path)
        if (!file.parentFile.isDirectory()) assertTrue(file.parentFile.mkdirs())
        file.setText(value, 'UTF-8')
    }
}
