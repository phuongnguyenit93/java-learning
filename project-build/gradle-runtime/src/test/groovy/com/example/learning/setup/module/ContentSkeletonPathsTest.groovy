package com.example.learning.setup.module

import com.example.learning.setup.module.interview.service.InterviewStructureService
import com.example.learning.setup.module.quiz.service.QuizStructureService
import com.example.learning.setup.module.readme.service.ReadmeStructureService
import com.example.learning.setup.module.reference.service.ReferenceStructureService
import com.example.learning.setup.module.roadmap.service.RoadmapStructureService
import com.example.learning.setup.root.readmeMetadata.service.ReadmeMetadataSyncService
import org.gradle.testfixtures.ProjectBuilder
import org.yaml.snakeyaml.Yaml
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.junit.Assert.*

class ContentSkeletonPathsTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder()

    @Test
    void createsLearningContentFoldersWithoutQuizInterviewOrRoadmapSkeletons() {
        File moduleDir = temporary.newFolder('module')
        def project = ProjectBuilder.builder().withProjectDir(moduleDir).build()
        project.ext.MODULE_LANGUAGE = 'vi,en'

        new ReadmeStructureService(project.logger).setup(project)
        for (String language in ['vi', 'en']) {
            File doc = new File(
                    moduleDir,
                    "content/readme/docs/${language}/menu/1.Basic/knowledge/Basic.md"
            )
            assertTrue(doc.parentFile.mkdirs())
            doc.setText('# Basic\n\n## <a id="basic-001">Overview</a>\n', 'UTF-8')
        }

        new QuizStructureService(project.logger).setup(project)
        new InterviewStructureService(project.logger).setup(project)
        new RoadmapStructureService(project.logger).setup(project)
        new ReferenceStructureService(project.logger).setup(project)

        assertTrue(new File(moduleDir, 'README.md').isFile())
        assertTrue(new File(moduleDir, 'content/reference/references.yml').isFile())
        assertFalse(new File(moduleDir, 'content/quiz').exists())
        assertFalse(new File(moduleDir, 'content/interview').exists())
        assertTrue(new File(moduleDir, 'content/roadmap').isDirectory())
        for (String language in ['vi', 'en']) {
            assertTrue(new File(moduleDir, "content/readme/docs/${language}/BASE.md").isFile())
            assertTrue(new File(moduleDir, "content/readme/docs/${language}/LIST.md").isFile())
            assertFalse(new File(moduleDir, "content/quiz/${language}").exists())
            assertFalse(new File(moduleDir, "content/interview/${language}").exists())
            assertFalse(new File(moduleDir, "content/roadmap/${language}").exists())
            assertFalse(new File(moduleDir, "content/video/${language}").exists())
        }
        for (String obsolete in [
                'readme', 'roadmap', 'reference', 'video',
                'src/main/resources/quiz', 'src/main/resources/interview',
                'src/main/resources/readme'
        ]) {
            assertFalse("Unexpected legacy skeleton: ${obsolete}",
                    new File(moduleDir, obsolete).exists())
        }
    }

    @Test
    void roadmapSetupOnlyCreatesDirectoryAndNeverTouchesExistingAuthoredRoadmaps() {
        File moduleDir = temporary.newFolder('authored-roadmap-module')
        def project = ProjectBuilder.builder().withProjectDir(moduleDir).build()
        project.ext.MODULE_LANGUAGE = 'vi,en'
        File authored = new File(moduleDir, 'content/roadmap/vi/roadmap.yml')
        assertTrue(authored.parentFile.mkdirs())
        String original = '# authored manually without generated markers\nroadmap: []\n'
        authored.setText(original, 'UTF-8')

        def setup = new RoadmapStructureService(project.logger)
        setup.setup(project)
        setup.setup(project)
        assertEquals(original, authored.getText('UTF-8'))
        assertFalse(new File(moduleDir, 'content/roadmap/en').exists())

        File emptyModule = temporary.newFolder('new-roadmap-module')
        def newProject = ProjectBuilder.builder().withProjectDir(emptyModule).build()
        new RoadmapStructureService(newProject.logger).setup(newProject)
        assertTrue(new File(emptyModule, 'content/roadmap').isDirectory())
        assertEquals(0, new File(emptyModule, 'content/roadmap').listFiles().length)

        File noPropertiesModule = temporary.newFolder('no-properties-roadmap-module')
        def noPropertiesProject = ProjectBuilder.builder()
                .withProjectDir(noPropertiesModule).build()
        new RoadmapStructureService(noPropertiesProject.logger).setup(noPropertiesProject)
        assertTrue(new File(noPropertiesModule, 'content/roadmap').isDirectory())
        assertEquals(0, new File(noPropertiesModule, 'content/roadmap').listFiles().length)
    }

    @Test
    void roadmapSetupDoesNotOverwriteConflictingContentFile() {
        File moduleDir = temporary.newFolder('conflicting-roadmap-module')
        def project = ProjectBuilder.builder().withProjectDir(moduleDir).build()
        File content = new File(moduleDir, 'content')
        assertTrue(content.mkdirs())
        File conflicting = new File(content, 'roadmap')
        conflicting.setText('existing important file', 'UTF-8')
        try {
            new RoadmapStructureService(project.logger).setup(project)
            fail('Expected file/directory conflict')
        } catch (org.gradle.api.GradleException expected) {
            assertTrue(expected.message.contains('not a directory'))
        }
        assertEquals('existing important file', conflicting.getText('UTF-8'))
    }

    @Test
    void referenceSetupCreatesOnceAndNeverReplacesAuthoredContent() {
        File moduleDir = temporary.newFolder('reference-module')
        def project = ProjectBuilder.builder().withProjectDir(moduleDir).build()
        def setup = new ReferenceStructureService(project.logger)
        File references = new File(moduleDir, 'content/reference/references.yml')

        setup.setup(project)
        assertTrue(references.isFile())
        byte[] skeleton = references.bytes
        setup.setup(project)
        assertArrayEquals(skeleton, references.bytes)

        String authored = 'references:\n  - title: Existing source\n    url: https://example.org\n'
        references.setText(authored, 'UTF-8')
        setup.setup(project)
        assertEquals(authored, references.getText('UTF-8'))
    }

    @Test
    void readmeSetupKeepsAuthoredLanguagesAndDoesNotPruneEmptyOtherLanguageTrees() {
        File moduleDir = temporary.newFolder('readme-module')
        def project = ProjectBuilder.builder().withProjectDir(moduleDir).build()
        project.ext.MODULE_LANGUAGE = 'vi'
        File enMenu = new File(moduleDir, 'content/readme/docs/en/menu')
        assertTrue(enMenu.mkdirs())
        File englishBase = new File(enMenu.parentFile, 'BASE.md')
        englishBase.setText('# Existing English draft\n', 'UTF-8')
        File viBase = new File(moduleDir, 'content/readme/docs/vi/BASE.md')

        def setup = new ReadmeStructureService(project.logger)
        setup.setup(project)
        assertTrue(viBase.isFile())
        viBase.setText('# Vietnamese authored introduction\n', 'UTF-8')
        setup.setup(project)

        assertEquals('# Vietnamese authored introduction\n', viBase.getText('UTF-8'))
        assertEquals('# Existing English draft\n', englishBase.getText('UTF-8'))
        assertTrue('Unused language trees are not deleted during sync', enMenu.isDirectory())
    }

    @Test
    void quizAndInterviewLegacySetupOnlyValidatesPathsAndPreservesAuthoredFiles() {
        File moduleDir = temporary.newFolder('authored-module')
        def project = ProjectBuilder.builder().withProjectDir(moduleDir).build()
        project.ext.MODULE_LANGUAGE = 'vi,en'

        File quizFile = new File(moduleDir, 'content/quiz/vi/question.yml')
        File interviewFile = new File(moduleDir, 'content/interview/vi/question.yml')
        assertTrue(quizFile.parentFile.mkdirs())
        assertTrue(interviewFile.parentFile.mkdirs())
        String quizContent = '# existing hand-authored quiz without schema markers\nquestions: []\n'
        String interviewContent = '# existing hand-authored interview\nquestions: []\n'
        quizFile.setText(quizContent, 'UTF-8')
        interviewFile.setText(interviewContent, 'UTF-8')

        def quizSetup = new QuizStructureService(project.logger)
        def interviewSetup = new InterviewStructureService(project.logger)
        quizSetup.setup(project)
        interviewSetup.setup(project)
        quizSetup.setup(project)
        interviewSetup.setup(project)

        assertEquals(quizContent, quizFile.getText('UTF-8'))
        assertEquals(interviewContent, interviewFile.getText('UTF-8'))
        assertFalse(new File(moduleDir, 'content/quiz/en').exists())
        assertFalse(new File(moduleDir, 'content/interview/en').exists())

        // Neither setup creates a legacy folder on a new module.
        File newModule = temporary.newFolder('new-question-module')
        def newProject = ProjectBuilder.builder().withProjectDir(newModule).build()
        new QuizStructureService(newProject.logger).setup(newProject)
        new InterviewStructureService(newProject.logger).setup(newProject)
        assertFalse(new File(newModule, 'content').exists())
    }

    @Test
    void refusesReplacingExistingFilesWithFeatureFolders() {
        File moduleDir = temporary.newFolder('conflicting-module')
        def project = ProjectBuilder.builder().withProjectDir(moduleDir).build()
        File content = new File(moduleDir, 'content')
        assertTrue(content.mkdirs())
        File quizPath = new File(content, 'quiz')
        File interviewPath = new File(content, 'interview')
        quizPath.setText('preserve quiz', 'UTF-8')
        interviewPath.setText('preserve interview', 'UTF-8')
        try {
            new QuizStructureService(project.logger).setup(project)
            fail('Expected a file/directory conflict')
        } catch (org.gradle.api.GradleException expected) {
            assertTrue(expected.message.contains('not a directory'))
        }
        try {
            new InterviewStructureService(project.logger).setup(project)
            fail('Expected a file/directory conflict')
        } catch (org.gradle.api.GradleException expected) {
            assertTrue(expected.message.contains('not a directory'))
        }
        assertEquals('preserve quiz', quizPath.getText('UTF-8'))
        assertEquals('preserve interview', interviewPath.getText('UTF-8'))
    }

    @Test
    void buildsKnowledgeMetadataOutsideRuntimeResources() {
        File rootDirectory = temporary.newFolder('repository')
        File moduleDir = new File(rootDirectory, 'module/example')
        assertTrue(moduleDir.mkdirs())
        new File(moduleDir, 'gradle.properties').setText(
                'MODULE_LANGUAGE=vi\n', 'UTF-8'
        )
        File markdown = new File(
                moduleDir, 'content/readme/docs/vi/menu/1.Basic/knowledge/Basic.md'
        )
        assertTrue(markdown.parentFile.mkdirs())
        markdown.setText(
                '# Basic\n\n## <a id="basic-001">Overview</a>\n\nDescription\n' +
                '## <a id="basic-002">New section</a>\n\nDescription\n',
                'UTF-8'
        )
        File existingMetadata = new File(
                moduleDir,
                'content/readme/docs/vi/menu/1.Basic/metadata/knowledge-metadata.yml'
        )
        assertTrue(existingMetadata.parentFile.mkdirs())
        existingMetadata.setText('''sections:
  basic-001:
    difficulty: ADVANCED


    isImportant: true
''', 'UTF-8')
        File advanced = new File(
                moduleDir, 'content/readme/docs/vi/menu/2.Advanced/knowledge/Advanced.md'
        )
        assertTrue(advanced.parentFile.mkdirs())
        advanced.setText('# Advanced\n\n## <a id="advanced-001">Overview</a>\n',
                'UTF-8')

        def rootProject = ProjectBuilder.builder()
                .withProjectDir(rootDirectory).build()
        def moduleProject = ProjectBuilder.builder()
                .withParent(rootProject).withProjectDir(moduleDir).build()
        moduleProject.extensions.extraProperties.set('MODULE_LANGUAGE', 'vi')
        assertTrue('Test child must be registered in the Gradle hierarchy',
                rootProject.allprojects.contains(moduleProject))
        assertTrue('The test child must belong to module/',
                moduleProject.projectDir.canonicalPath.startsWith(
                        new File(rootDirectory, 'module').canonicalPath
                ))
        assertEquals(['vi'], com.example.learning.utils.ProjectPropertyUtils
                .getStringList(moduleProject, 'MODULE_LANGUAGE'))
        def projectResolver = ReadmeMetadataSyncService.getDeclaredMethod(
                'resolveRealModuleProjects',
                org.gradle.api.Project.class, File.class
        )
        projectResolver.accessible = true
        def eligible = projectResolver.invoke(
                null, rootProject, new File(rootDirectory, 'module')
        )
        assertTrue('Project must be eligible for metadata sync: ' + eligible,
                eligible.contains(moduleProject))
        def languageResolver = ReadmeMetadataSyncService.getDeclaredMethod(
                'resolveLanguageSources', org.gradle.api.Project.class
        )
        languageResolver.accessible = true
        def languages = languageResolver.invoke(null, moduleProject)
        assertEquals('Expected one Knowledge language source', 1, languages.size())

        // ProjectBuilder's synthetic hierarchy can differ from a real multi-project
        // Gradle settings evaluation. Exercise each chapter-local metadata plan
        // and the safe writer directly to verify persisted location and flags.
        def service = new ReadmeMetadataSyncService(rootProject.logger)
        def planMethod = ReadmeMetadataSyncService.declaredMethods.find {
            it.name == 'buildChapterWritePlans'
        }
        assertNotNull('Chapter metadata planner must be available', planMethod)
        planMethod.accessible = true
        def plans = planMethod.invoke(service, moduleProject, languages.first())
        assertEquals('One metadata file per chapter', 2, plans.size())
        def writer = ReadmeMetadataSyncService.getDeclaredMethod(
                'writeSafelyIfChanged', File.class, String.class
        )
        writer.accessible = true
        for (def plan in plans) {
            if (plan.outputFile == existingMetadata) {
                assertTrue('Existing authored flags must be retained',
                        plan.content.contains(''))
            }
            assertTrue(writer.invoke(null, plan.outputFile, plan.content))
        }

        File metadata = new File(
                moduleDir,
                'content/readme/docs/vi/menu/1.Basic/metadata/knowledge-metadata.yml'
        )
        File secondMetadata = new File(
                moduleDir,
                'content/readme/docs/vi/menu/2.Advanced/metadata/knowledge-metadata.yml'
        )
        assertTrue('Knowledge metadata must live beside its chapter', metadata.isFile())
        assertTrue('Each chapter must have its own metadata', secondMetadata.isFile())
        Map first = new Yaml().load(metadata.getText('UTF-8')) as Map
        Map second = new Yaml().load(secondMetadata.getText('UTF-8')) as Map
        assertEquals(['sections'] as Set, first.keySet())
        assertEquals(['sections'] as Set, second.keySet())
        assertEquals(['basic-001', 'basic-002'] as Set, first.sections.keySet())
        assertEquals(['advanced-001'] as Set, second.sections.keySet())
        assertEquals([difficulty: 'ADVANCED', isImportant: true], first.sections['basic-001'])
        assertEquals([difficulty: 'BASIC', isImportant: false], first.sections['basic-002'])
        assertEquals([difficulty: 'BASIC', isImportant: false], second.sections['advanced-001'])
        assertFalse(new File(
                moduleDir, 'content/readme/metadata/vi/knowledge-metadata.yml'
        ).exists())
        assertFalse(new File(
                moduleDir, 'src/main/resources/readme/vi/knowledge-metadata.yml'
        ).exists())

        // Quoted booleans must fail strict metadata validation; they must not
        // be silently coerced or replace existing human-edited review flags.
        for (String flag in ['isImportant']) {
            Map invalid = new LinkedHashMap(first)
            invalid.sections = new LinkedHashMap(first.sections)
            invalid.sections['basic-001'] = new LinkedHashMap(first.sections['basic-001'])
            invalid.sections['basic-001'][flag] = 'true'
            metadata.setText(new Yaml().dump(invalid), 'UTF-8')
            try {
                planMethod.invoke(service, moduleProject, languages.first())
                fail('String flag ' + flag + ' must be rejected')
            }
            catch (java.lang.reflect.InvocationTargetException expected) {
                assertTrue(expected.cause instanceof org.gradle.api.GradleException)
                assertTrue(expected.cause.message,
                        expected.cause.message.contains(
                                'Expected YAML boolean true or false'))
            }
        }
    }
}
