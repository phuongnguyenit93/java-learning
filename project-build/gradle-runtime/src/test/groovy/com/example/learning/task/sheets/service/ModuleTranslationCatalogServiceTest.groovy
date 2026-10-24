package com.example.learning.task.sheets.service

import org.gradle.api.GradleException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.junit.Assert.*

class ModuleTranslationCatalogServiceTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder()

    @Test
    void preparesOneJobPerFileAndValidatesOnlyTranslatableFields() {
        Map f = fixture()
        def service = new ModuleTranslationCatalogService()
        Map dry = service.execute(f.root as File, f.module as File, 'trial', 'PREPARE', false)
        assertEquals('DRY_RUN', dry.status)
        assertEquals(5, dry.total)
        assertFalse((dry.manifest as File).exists())
        Map prepared = service.execute(f.root as File, f.module as File,
                'trial', 'PREPARE', true)
        assertEquals('PREPARED', prepared.status)
        assertTrue((prepared.manifest as File).isFile())
        assertEquals('module/demo', new groovy.json.JsonSlurper()
                .parse(prepared.manifest as File).module)
        File quiz = new File(f.module as File, 'content/quiz/en/question.yml')
        quiz.setText(quiz.getText('UTF-8').replace('Câu hỏi', 'English question')
                .replace('Giải thích', 'Explanation'), 'UTF-8')
        File doc = new File(f.module as File,
                'content/readme/docs/en/menu/1.Basic/Basic.md')
        doc.setText(doc.getText('UTF-8').replace('Bài học', 'Learning section')
                .replace('Nội dung', 'English explanation'), 'UTF-8')

        Map checked = service.execute(f.root as File, f.module as File,
                'trial', 'VALIDATE', false)
        assertEquals('VALIDATED_STRUCTURE', checked.status)
        assertEquals(5, checked.total)
        assertEquals(2, checked.translated)
        assertEquals(3, checked.pending)
        assertTrue(checked.semanticQaRequired)
        File snapshot = new File(f.module as File,
                'build/step14/trial/baseline/content/quiz/en/question.yml')
        assertTrue(snapshot.getText('UTF-8').contains('Câu hỏi'))
        assertFalse(snapshot.getText('UTF-8').contains('English question'))
    }

    @Test
    void rejectModificationToTechnicalQuestionFields() {
        Map f = fixture()
        def service = new ModuleTranslationCatalogService()
        service.execute(f.root as File, f.module as File, 'review1', 'PREPARE', true)
        File quiz = new File(f.module as File, 'content/quiz/en/question.yml')
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
                'content/readme/docs/en/menu/1.Basic/Basic.md')
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

    private Map fixture() {
        File root = temp.newFolder('repo')
        File module = new File(root, 'module/demo')
        assertTrue(module.mkdirs())
        write(module, 'gradle.properties', '# module')
        [
                'content/readme/docs/vi/BASE.md': '# Giới thiệu',
                'content/readme/docs/vi/LIST.md':
                        '# README MODULE STRUCTURE (VI)\n* [Basic](content/readme/docs/vi/menu/1.Basic/Basic.md)',
                'content/readme/docs/vi/menu/1.Basic/Basic.md':
                        '# Chương\n## <a id="basic-001">Bài học</a>\nNội dung giải thích',
                'content/readme/metadata/vi/knowledge-metadata.yml':
                        '1.Basic/Basic.md:\n  basic-001: {difficulty: BASIC, reviewed: false}',
                'content/roadmap/vi/roadmap.yml':
                        'roadmap:\n  - id: stable\n    title: Tiêu đề\n    purpose: Mục tiêu\n    objectives: [Hiểu nguyên lý]',
                'content/quiz/vi/question.yml': '''questions:
  - id: stable-id
    question: Câu hỏi?
    answers:
      - {id: A, answer: Đúng, explanation: Giải thích đúng}
      - {id: B, answer: Sai, explanation: Giải thích sai}
      - {id: C, answer: Không, explanation: Giải thích không}
      - {id: D, answer: Khác, explanation: Giải thích khác}
    correctAnswerId: A
    readmeRelated: {file: "1.Basic/Basic.md", anchor: "basic-001"}
''',
                'content/video/vi/menu/1.Basic/video-script.yml':
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
