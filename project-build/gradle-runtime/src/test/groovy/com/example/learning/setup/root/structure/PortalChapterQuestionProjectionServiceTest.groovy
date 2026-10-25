package com.example.learning.setup.root.structure

import com.example.learning.setup.root.structure.service.PortalInterviewProjectionService
import com.example.learning.setup.root.structure.service.PortalQuizProjectionService
import com.example.learning.setup.root.structure.service.ProjectStructureService
import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.yaml.snakeyaml.Yaml

import static org.junit.Assert.*

class PortalChapterQuestionProjectionServiceTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder()

    @Test
    void projectsPerChapterQuestionsAndAddsResolvedKnowledgeFileWithoutEditingSource() {
        Fixture fixture = fixture()
        lesson(fixture.moduleDir, '1.Basics', 'First.md', 'local-first')
        lesson(fixture.moduleDir, '2.Advanced', 'Deep/Nested.md', 'local-second')
        writeYaml(fixture.moduleDir, '1.Basics/quiz/question.yml',
                [questions: [quizQuestion('quiz-1', 'local-first')]])
        writeYaml(fixture.moduleDir, '2.Advanced/quiz/question.yml',
                [questions: [quizQuestion('quiz-2', 'local-second')]])
        writeYaml(fixture.moduleDir, '2.Advanced/interview/question.yml',
                [questions: [interviewQuestion('local-second')]])
        writeYaml(fixture.moduleDir, '3.Empty/quiz/question.yml', [questions: []])
        writeYaml(fixture.moduleDir, '3.Empty/interview/question.yml', [questions: []])

        String source = new File(fixture.moduleDir,
                'content/readme/docs/vi/menu/2.Advanced/quiz/question.yml').getText('UTF-8')
        Map authoredQuiz = new Yaml().load(source) as Map
        assertEquals('Nonempty Quiz fixture must round-trip as YAML', 1,
                (authoredQuiz.questions as List).size())
        assertEquals('quiz-2', authoredQuiz.questions[0].id)
        new PortalQuizProjectionService(fixture.root.logger).generate(fixture.root)
        new PortalInterviewProjectionService(fixture.root.logger).generate(fixture.root)

        Map quizIndex = readJson(fixture.root, 'DEMO/quiz/vi/index.json')
        assertEquals(2, quizIndex.totalQuestions)
        assertEquals(['1.Basics', '2.Advanced'], quizIndex.chapters*.id)
        assertEquals(['menu/1.Basics/question.yml', 'menu/2.Advanced/question.yml'],
                quizIndex.chapters*.path)
        assertEquals([1, 1], quizIndex.chapters*.questionCount)
        assertEquals('2.Advanced', quizIndex.chapters[1].title)
        Map quiz = readYaml(fixture.root, 'DEMO/quiz/vi/menu/2.Advanced/question.yml')
        assertEquals('2.Advanced/knowledge/Deep/Nested.md',
                quiz.questions[0].readmeRelated.file)
        assertEquals('local-second', quiz.questions[0].readmeRelated.anchor)

        Map interviewIndex = readJson(fixture.root, 'DEMO/interview/vi/index.json')
        assertEquals(1, interviewIndex.totalQuestions)
        assertEquals(['2.Advanced'], interviewIndex.chapters*.id)
        Map interview = readYaml(fixture.root, 'DEMO/interview/vi/menu/2.Advanced/question.yml')
        assertEquals('2.Advanced/knowledge/Deep/Nested.md',
                interview.questions[0].readmeRelated.file)
        assertEquals(source, new File(fixture.moduleDir,
                'content/readme/docs/vi/menu/2.Advanced/quiz/question.yml').getText('UTF-8'))
        assertFalse('empty chapter must not publish Quiz content',
                published(fixture.root, 'DEMO/quiz/vi/menu/3.Empty/question.yml').exists())
        assertFalse('empty chapter must not publish Interview content',
                published(fixture.root, 'DEMO/interview/vi/menu/3.Empty/question.yml').exists())

        // An obsolete root-level YAML must be cleaned while unrelated output survives.
        File stale = published(fixture.root, 'DEMO/quiz/vi/question.yml')
        stale.parentFile.mkdirs()
        stale.text = 'obsolete'
        File video = published(fixture.root, 'DEMO/video/vi/index.json')
        video.parentFile.mkdirs()
        video.text = 'video data'
        new PortalQuizProjectionService(fixture.root.logger).generate(fixture.root)
        assertFalse(stale.exists())
        assertEquals('video data', video.text)

        def counts = ProjectStructureService.getDeclaredMethod(
                'findQuestionCounts', File, List, boolean, String)
        counts.accessible = true
        assertEquals([vi: 2], counts.invoke(null, fixture.moduleDir, ['vi'], true, 'quiz'))
        assertEquals([vi: 1], counts.invoke(null, fixture.moduleDir, ['vi'], true, 'interview'))
        assertEquals([:], counts.invoke(null, fixture.moduleDir, ['vi'], false, 'quiz'))
    }

    @Test
    void rejectsDuplicateQuizIdsAcrossChapters() {
        Fixture fixture = fixture()
        lesson(fixture.moduleDir, '1.Basics', 'One.md', 'one')
        lesson(fixture.moduleDir, '2.Advanced', 'Two.md', 'two')
        writeYaml(fixture.moduleDir, '1.Basics/quiz/question.yml',
                [questions: [quizQuestion('same-id', 'one')]])
        writeYaml(fixture.moduleDir, '2.Advanced/quiz/question.yml',
                [questions: [quizQuestion('same-id', 'two')]])
        expectFailure('Duplicate Quiz id') {
            new PortalQuizProjectionService(fixture.root.logger).generate(fixture.root)
        }
    }

    @Test
    void rejectsAmbiguousAnchorsEvenWithinOneMarkdownFile() {
        Fixture fixture = fixture()
        File article = new File(fixture.moduleDir,
                'content/readme/docs/vi/menu/1.Basics/knowledge/One.md')
        article.parentFile.mkdirs()
        article.setText('# Title\n\n## <a id="duplicated">One</a>\n\n' +
                '## <a id="duplicated">Two</a>\n', 'UTF-8')
        writeYaml(fixture.moduleDir, '1.Basics/interview/question.yml',
                [questions: [interviewQuestion('duplicated')]])
        expectFailure('ambiguous') {
            new PortalInterviewProjectionService(fixture.root.logger).generate(fixture.root)
        }
    }

    @Test
    void rejectsMissingAndCrossChapterAnchors() {
        Fixture fixture = fixture()
        lesson(fixture.moduleDir, '1.Basics', 'One.md', 'one')
        lesson(fixture.moduleDir, '2.Advanced', 'Two.md', 'two')
        writeYaml(fixture.moduleDir, '1.Basics/quiz/question.yml',
                [questions: [quizQuestion('cross', 'two')]])
        expectFailure('cross-chapter') {
            new PortalQuizProjectionService(fixture.root.logger).generate(fixture.root)
        }
        writeYaml(fixture.moduleDir, '1.Basics/quiz/question.yml',
                [questions: [quizQuestion('missing', 'not-found')]])
        expectFailure('missing') {
            new PortalQuizProjectionService(fixture.root.logger).generate(fixture.root)
        }
    }

    private Fixture fixture() {
        File rootDir = temporary.newFolder('repo')
        File moduleDir = new File(rootDir, 'module/demo')
        assertTrue(moduleDir.mkdirs())
        new File(moduleDir, 'gradle.properties').text = 'IS_MODULE=TRUE\n'
        Project root = ProjectBuilder.builder().withProjectDir(rootDir).build()
        Project module = ProjectBuilder.builder().withParent(root).withProjectDir(moduleDir).build()
        module.extensions.extraProperties.set('MODULE_LANGUAGE', 'vi')
        module.extensions.extraProperties.set('SERVICE_NAME', 'DEMO')
        module.extensions.extraProperties.set('BUILD_QUIZ', 'TRUE')
        module.extensions.extraProperties.set('BUILD_INTERVIEW', 'TRUE')
        return new Fixture(root, moduleDir)
    }

    private static Map quizQuestion(String id, String anchor) {
        return [id: id, question: 'A question?', answers: ['A', 'B', 'C', 'D'].collect {
            // SnakeYAML emits a Groovy-specific !!GStringImpl tag for GString
            // values, which its safe loader intentionally refuses to parse.
            String answer -> [id: answer, answer: 'Answer ' + answer,
                              explanation: 'Explanation ' + answer]
        }, correctAnswerId: 'A', aiGenerated: true, reviewed: false,
                readmeRelated: [anchor: anchor],
                apiRelated: [controller: '', methodSignature: '']]
    }

    private static Map interviewQuestion(String anchor) {
        return [question: 'Interview question?', answer: 'Answer with examples.',
                aiGenerated: true, reviewed: false,
                readmeRelated: [anchor: anchor],
                apiRelated: [controller: '', methodSignature: '']]
    }

    private static void lesson(File moduleDir, String chapter, String filename, String anchor) {
        File markdown = new File(moduleDir,
                "content/readme/docs/vi/menu/${chapter}/knowledge/${filename}")
        assertTrue(markdown.parentFile.isDirectory() || markdown.parentFile.mkdirs())
        markdown.setText("# Test\n\n## <a id=\"${anchor}\">Example</a>\n\nSome content.\n", 'UTF-8')
    }

    private static void writeYaml(File moduleDir, String path, Map contents) {
        File source = new File(moduleDir, "content/readme/docs/vi/menu/${path}")
        assertTrue(source.parentFile.isDirectory() || source.parentFile.mkdirs())
        String yaml = new Yaml().dump(contents)
        assertFalse('Test YAML must not contain Groovy object type tags',
                yaml.contains('!!org.codehaus.groovy'))
        // Validate generated fixtures before invoking the projection; every
        // failure scenario must reach the intended anchor/id check.
        assertEquals(contents, new Yaml().load(yaml))
        source.setText(yaml, 'UTF-8')
    }

    private static File published(Project root, String relative) {
        return new File(root.projectDir, "build/learning-data/java/module/${relative}")
    }

    private static Map readYaml(Project root, String relative) {
        return new Yaml().load(published(root, relative).getText('UTF-8')) as Map
    }

    private static Map readJson(Project root, String relative) {
        return new JsonSlurper().parse(published(root, relative)) as Map
    }

    private static void expectFailure(String fragment, Closure action) {
        try {
            action.call()
            fail('Expected a GradleException')
        } catch (GradleException expected) {
            assertTrue(expected.message, expected.message.contains(fragment))
        }
    }

    private static class Fixture {
        final Project root
        final File moduleDir

        Fixture(Project root, File moduleDir) {
            this.root = root
            this.moduleDir = moduleDir
        }
    }
}
