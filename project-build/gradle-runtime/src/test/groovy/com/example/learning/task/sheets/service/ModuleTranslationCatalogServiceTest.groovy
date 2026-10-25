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
        String viSection = '# Mục tiêu\n\n## Nội dung\n'
        String summaryYaml = 'summaries:\n  - id: basic-001\n    title: Bài học\n    summary: "## Nội dung"\n'
        for (String lang : ['vi', 'en']) {
            write(module, "content/readme/docs/${lang}/menu/1.Basic/summary/section.md",viSection)
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
    void preparesOneJobPerFileAndValidatesOnlyTranslatableFields() {
        Map f = fixture()
        def service = new ModuleTranslationCatalogService()
        Map dry = service.execute(f.root as File, f.module as File, 'trial', 'PREPARE', false)
        assertEquals('DRY_RUN', dry.status)
        assertEquals(6, dry.total)
        assertFalse((dry.manifest as File).exists())
        Map prepared = service.execute(f.root as File, f.module as File,
                'trial', 'PREPARE', true)
        assertEquals('PREPARED', prepared.status)
        assertTrue((prepared.manifest as File).isFile())
        assertEquals('module/demo', new groovy.json.JsonSlurper()
                .parse(prepared.manifest as File).module)
        File quiz = new File(f.module as File,
                'content/readme/docs/en/menu/1.Basic/quiz/question.yml')
        quiz.setText(quiz.getText('UTF-8').replace('Câu hỏi', 'English question')
                .replace('Giải thích', 'Explanation'), 'UTF-8')
        File doc = new File(f.module as File,
                'content/readme/docs/en/menu/1.Basic/knowledge/Basic.md')
        doc.setText(doc.getText('UTF-8').replace('Bài học', 'Learning section')
                .replace('Nội dung', 'English explanation'), 'UTF-8')

        Map checked = service.execute(f.root as File, f.module as File,
                'trial', 'VALIDATE', false)
        assertEquals('VALIDATED_STRUCTURE', checked.status)
        assertEquals(6, checked.total)
        assertEquals(2, checked.translated)
        assertEquals(4, checked.pending)
        assertTrue(checked.semanticQaRequired)
        File snapshot = new File(f.module as File,
                'build/step14/trial/baseline/content/readme/docs/en/menu/1.Basic/quiz/question.yml')
        assertTrue(snapshot.getText('UTF-8').contains('Câu hỏi'))
        assertFalse(snapshot.getText('UTF-8').contains('English question'))
    }

    @Test
    void rejectModificationToTechnicalQuestionFields() {
        Map f = fixture()
        def service = new ModuleTranslationCatalogService()
        service.execute(f.root as File, f.module as File, 'review1', 'PREPARE', true)
        File quiz = new File(f.module as File,
                'content/readme/docs/en/menu/1.Basic/quiz/question.yml')
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
    void cannotStartNewTranslationBaselineAfterTranslationHasBegun() {
        Map f = fixture()
        File source = new File(f.module as File,
                'content/readme/docs/en/menu/1.Basic/knowledge/Basic.md')
        source.setText(source.getText('UTF-8').replace('Nội dung',
                'English description'), 'UTF-8')
        try {
            new ModuleTranslationCatalogService().execute(f.root as File,
                    f.module as File, 'newrun', 'PREPARE', true)
            fail('Translated module must not be baselined as STEP 12A clone')
        } catch (GradleException error) {
            assertTrue(error.message.contains('clone not intact'))
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
        assertFalse(ModuleTranslationValidationService.editable('ROADMAP',
                'roadmap[0].relatedModules[0].routeId'))
        assertTrue(ModuleTranslationValidationService.editable('QUIZ',
                'questions[0].answers[3].explanation'))
        assertFalse(ModuleTranslationValidationService.editable('QUIZ',
                'questions[0].correctAnswerId'))
        assertTrue(ModuleTranslationValidationService.editable(
                'SWAGGER_API_CASES', 'Controller.method().cases[0].description'))
        assertFalse(ModuleTranslationValidationService.editable(
                'SWAGGER_API_CASES', 'Controller.method().cases[0].response'))
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
        assertEquals(7, inventory.size())
        assertEquals(2, inventory.count { it.type == 'README' })
        assertEquals(1, inventory.count { it.type == 'QUIZ' })
        assertEquals(1, inventory.count { it.type == 'INTERVIEW' })
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
        assertEquals(6, inventory.size())
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
                        'sections:\n  basic-001: {difficulty: BASIC, reviewed: false}',
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
    aiGenerated: true
    reviewed: false
    readmeRelated: {anchor: basic-001}
    apiRelated: {controller: "", methodSignature: ""}
''',
                'content/readme/docs/vi/menu/1.Basic/interview/question.yml': '''questions:
  - question: Câu phỏng vấn?
    answer: Trả lời thực hành
    aiGenerated: true
    reviewed: false
    readmeRelated: {anchor: basic-001}
    apiRelated: {controller: "", methodSignature: ""}
''',
                'content/readme/docs/vi/menu/1.Basic/video/video-script.yml':
                        'title: Tiêu đề\nsections:\n  - title: Bài học\n    items:\n      - {type: SCENE, time: "00:00-00:10", visual: Hình ảnh, script: Thuyết minh, purpose: Mục đích}',
                'src/main/resources/swagger/vi/api-params.yml':
                        'request:\n  summary: Tóm tắt\n  description: Mô tả\n  usage: true'
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
