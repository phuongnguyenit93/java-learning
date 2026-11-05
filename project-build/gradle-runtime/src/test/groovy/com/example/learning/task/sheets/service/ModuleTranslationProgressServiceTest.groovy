package com.example.learning.task.sheets.service

import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.junit.Assert.*

class ModuleTranslationProgressServiceTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder()

    @Test
    void survivesLostPrimeContextPreventsDuplicateJobsAndDeletesOnlyAfterCheckpoint() {
        Map f = fixture()
        File root = f.root as File
        File module = f.module as File
        String run = 'recover-2026'
        Map prepared = new ModuleTranslationCatalogService().execute(
                root, module, run, 'PREPARE', true, 'NORMAL', 2)
        assertEquals(3, prepared.jobCount)
        ModuleTranslationProgressService tracker = new ModuleTranslationProgressService()
        Map created = tracker.initialize(root, module, run)
        assertEquals('CREATED', created.result)
        File journal = created.path as File
        assertTrue(journal.path.replace('\\', '/')
                .contains('/module-generate-agent/temp/step11/module/demo/recover-2026.json'))
        assertEquals(3, created.pending)
        assertEquals(0, created.revision)
        assertEquals('EXISTING', tracker.initialize(root, module, run).result)
        assertEquals(0, tracker.execute(root, module, run, 'SHOW').revision)

        Map first = tracker.execute(root, module, run, 'ASSIGN',
                'COMMON', 'worker-01', '', 0)
        assertEquals('ASSIGNED', first.jobs.find { it.id == 'COMMON' }.status)
        assertEquals(1, first.active)
        // Simulate Prime context loss: a new service reads persisted state.
        ModuleTranslationProgressService resumed = new ModuleTranslationProgressService()
        Map afterRestart = resumed.execute(root, module, run, 'SHOW')
        assertEquals(['COMMON'], afterRestart.recoveryRequired)
        assertEquals('worker-01', afterRestart.jobs.find { it.id == 'COMMON' }.workerId)
        assertThrows(GradleException) {
            resumed.execute(root, module, run, 'ASSIGN',
                    'COMMON', 'worker-02', '', afterRestart.revision as Integer)
        }
        assertThrows(GradleException) {
            resumed.execute(root, module, run, 'REQUEUE',
                    'COMMON', '', '', afterRestart.revision as Integer)
        }
        Map requeued = resumed.execute(root, module, run, 'REQUEUE',
                'COMMON', '', 'Confirmed worker-01 terminated; no writer remains.',
                afterRestart.revision as Integer)
        assertEquals('PENDING', requeued.jobs.find { it.id == 'COMMON' }.status)
        assertEquals(1, requeued.jobs.find { it.id == 'COMMON' }.attempts)
        Map reassigned = resumed.execute(root, module, run, 'ASSIGN',
                'COMMON', 'worker-02', '', requeued.revision as Integer)
        assertEquals(2, reassigned.jobs.find { it.id == 'COMMON' }.attempts)

        translate(module, 'content/readme/docs/en/BASE.md',
                'Giới thiệu', 'Introduction')
        Map reported = resumed.execute(root, module, run, 'REPORT',
                'COMMON', 'worker-02', 'Translated and read back three files',
                reassigned.revision as Integer)
        assertEquals('REPORTED', reported.jobs.find { it.id == 'COMMON' }.status)
        assertEquals(1, reported.reported)
        assertThrows(GradleException) {
            resumed.execute(root, module, run, 'COMPLETE', '', '', '',
                    reported.revision as Integer)
        }
        assertThrows(GradleException) {
            resumed.execute(root, module, run, 'QA_PASS',
                    'COMMON', '', '', reported.revision as Integer)
        }
        Map passed = resumed.execute(root, module, run, 'QA_PASS',
                'COMMON', '', 'Prime semantic/structure QA reviewed; no omitted fields.',
                reported.revision as Integer)
        assertEquals(1, passed.qaPass)
        assertEquals(2, passed.pending)

        Map at = passed
        Map quizJob = (at.jobs as List<Map>).find {
            (it.rule as String).contains('QUIZ')
        }
        at = resumed.execute(root, module, run, 'ASSIGN',
                quizJob.id as String, 'worker-03', '', at.revision as Integer)
        translate(module, 'content/quiz/en/quiz.yml', 'Câu hỏi', 'English question')
        at = resumed.execute(root, module, run, 'REPORT',
                quizJob.id as String, 'worker-03', '', at.revision as Integer)
        // A worker cannot modify the file after reporting and silently pass QA.
        translate(module, 'content/quiz/en/quiz.yml', 'English question', 'English question revised')
        final int beforeDrift = at.revision as int
        assertThrows(GradleException) {
            resumed.execute(root, module, run, 'QA_PASS', quizJob.id as String,
                    '', 'Prime reviewed', beforeDrift)
        }
        assertEquals(beforeDrift, resumed.execute(root, module, run, 'SHOW').revision)
        at = resumed.execute(root, module, run, 'REQUEUE', quizJob.id as String,
                '', 'Worker terminated; drift needs new report/QA.',
                at.revision as Integer)
        at = resumed.execute(root, module, run, 'ASSIGN',
                quizJob.id as String, 'worker-04', '', at.revision as Integer)
        at = resumed.execute(root, module, run, 'REPORT',
                quizJob.id as String, 'worker-04', '', at.revision as Integer)
        at = resumed.execute(root, module, run, 'QA_PASS',
                quizJob.id as String, '', 'Prime reviewed revised quiz prose.',
                at.revision as Integer)

        Map interviewJob = (at.jobs as List<Map>).find {
            (it.rule as String).contains('INTERVIEW')
        }
        at = resumed.execute(root, module, run, 'ASSIGN',
                interviewJob.id as String, 'worker-05', '', at.revision as Integer)
        // If unchanged content is *truly* nontranslatable, Prime must explicitly
        // provide reason. The journal never guesses this from identical bytes.
        at = resumed.execute(root, module, run, 'REPORT',
                interviewJob.id as String, 'worker-05', '', at.revision as Integer)
        at = resumed.execute(root, module, run, 'SKIP_NO_TRANSLATABLE_TEXT',
                interviewJob.id as String, '', 'Prime reviewed; no translatable text (test fixture).',
                at.revision as Integer)
        assertEquals(3, at.qaPass)
        assertEquals(0, at.pending)
        assertThrows(GradleException) {
            resumed.execute(root, module, run, 'COMPLETE', '', '', '',
                    at.revision as Integer)
        }
        assertTrue(journal.isFile()) // master checkpoint has not been written.

        write(module, 'master.json',
                '{"CURRENT_STEP":{"TYPE":"string","VALUE":"11"}}')
        File abandonedStage = new File(journal.parentFile,
                '.step11-progress-abandoned.tmp')
        abandonedStage.setText('incomplete historical temp staging', 'UTF-8')
        Map complete = resumed.execute(root, module, run, 'COMPLETE',
                '', '', '', at.revision as Integer)
        assertEquals('REMOVED_AFTER_STEP11_PASS', complete.result)
        assertFalse(journal.exists())
        assertFalse(abandonedStage.exists())
        assertTrue((prepared.manifest as File).exists()) // immutable evidence kept.
        assertThrows(GradleException) {
            resumed.execute(root, module, run, 'SHOW')
        }
    }

    @Test
    void rejectsLostLedgerWhenTranslationAlreadyStartedAndRequiresCurrentRevision() {
        Map f = fixture()
        File root = f.root as File
        File module = f.module as File
        String run = 'missing-ledger'
        new ModuleTranslationCatalogService().execute(root, module,
                run, 'PREPARE', true)
        translate(module, 'content/quiz/en/quiz.yml', 'Câu hỏi', 'English question')
        ModuleTranslationProgressService service = new ModuleTranslationProgressService()
        GradleException exception = assertThrows(GradleException) {
            service.initialize(root, module, run)
        }
        assertTrue(exception.message.contains('RECOVERY_REQUIRED'))
        assertFalse(new File(root, 'module-generate-agent/temp/step11/module/demo/' +
                run + '.json').exists())
    }

    @Test
    void enforcesWorkerPoolAndOptimisticRevisionAcrossSessions() {
        Map f = fixture()
        File root = f.root as File
        File module = f.module as File
        String run = 'max-pool'
        new ModuleTranslationCatalogService().execute(root, module,
                run, 'PREPARE', true, 'NORMAL', 1)
        ModuleTranslationProgressService service = new ModuleTranslationProgressService()
        Map at = service.initialize(root, module, run)
        List ids = at.jobs*.id
        at = service.execute(root, module, run, 'ASSIGN',
                ids[0] as String, 'worker-01', '', at.revision as Integer)
        final int stale = 0
        assertThrows(GradleException) {
            service.execute(root, module, run, 'ASSIGN',
                    ids[1] as String, 'worker-02', '', stale)
        }
        assertThrows(GradleException) {
            service.execute(root, module, run, 'ASSIGN',
                    ids[1] as String, 'worker-02', '', at.revision as Integer)
        }
        at = service.execute(root, module, run, 'REPORT',
                ids[0] as String, 'worker-01', '', at.revision as Integer)
        at = service.execute(root, module, run, 'QA_PASS',
                ids[0] as String, '', 'Reviewed by Prime.', at.revision as Integer)
        at = service.execute(root, module, run, 'ASSIGN',
                ids[1] as String, 'worker-02', '', at.revision as Integer)
        assertEquals(1, at.active)
    }

    @Test
    void refusesParallelRunIdsForOneModuleButKeepsOtherModulesIsolated() {
        Map f = fixture()
        File root = f.root as File
        File module = f.module as File
        ModuleTranslationProgressService tracker = new ModuleTranslationProgressService()
        new ModuleTranslationCatalogService().execute(
                root, module, 'first-run', 'PREPARE', true)
        tracker.initialize(root, module, 'first-run')
        GradleException error = assertThrows(GradleException) {
            tracker.preflightPrepare(root, module, 'second-run')
        }
        assertTrue(error.message.contains('OTHER_RUN_ACTIVE'))
        assertTrue(new File(root,
                'module-generate-agent/temp/step11/module/demo/first-run.json').isFile())
        File another = new File(root, 'module/another')
        assertTrue(another.mkdirs())
        write(another, 'gradle.properties', '# another module')
        tracker.preflightPrepare(root, another, 'second-run')
    }

    private Map fixture() {
        File root = tmp.newFolder('repo')
        File module = new File(root, 'module/demo')
        assertTrue(module.mkdirs())
        assertTrue(new File(root, 'module-generate-agent').mkdirs())
        write(module, 'gradle.properties', '# fixture')
        write(module, 'master.json', '{"CURRENT_STEP":{"TYPE":"string","VALUE":"10"}}')
        Map<String, String> sources = [
                'content/readme/docs/vi/BASE.md': '# Giới thiệu\n',
                'content/readme/docs/vi/LIST.md': '# Overview\n',
                'content/reference/vi/references.yml': '''
references:
  - title: Tài liệu
    url: https://example.org/reference
    description: Mô tả nguồn
'''.trim(),
                'content/roadmap/vi/roadmap.yml': '''
roadmap:
  - id: stable
    title: Tiêu đề
    purpose: Mục tiêu
    objectives: [Hiểu nguyên lý]
'''.trim(),
                'content/quiz/vi/quiz.yml': '''
quiz:
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
'''.trim(),
                'content/interview/vi/interview.yml': '''
interviewQuestion:
  - groupID: 1
    groupName: Phỏng vấn
    questions:
      - question: Câu phỏng vấn?
        readmeRelated: {anchorList: [basic-001]}
        knowledge: Nguyên lý
        answer: Trả lời thực hành
'''.trim()
        ]
        sources.each { String path, String value ->
            write(module, path, value)
            write(module, path.replace('/vi/', '/en/'), value)
        }
        ModuleTranslationCatalogService.workerJobs(
                ModuleTranslationCatalogService.inventory(module)).each { Map job ->
            write(root, 'module-generate-agent/worker/' + job.rule, '# fixture rule')
        }
        return [root: root, module: module]
    }

    private static void translate(File module, String path, String before, String after) {
        File file = new File(module, path)
        assertTrue(file.isFile())
        String source = file.getText('UTF-8')
        assertTrue(source.contains(before))
        file.setText(source.replace(before, after), 'UTF-8')
    }

    private static void write(File module, String relative, String source) {
        File file = new File(module, relative)
        if (!file.parentFile.exists()) assertTrue(file.parentFile.mkdirs())
        file.setText(source, 'UTF-8')
    }
}
