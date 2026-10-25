package com.example.learning.setup.module

import com.example.learning.setup.module.interview.service.InterviewStructureService
import com.example.learning.setup.module.quiz.service.QuizStructureService
import com.example.learning.setup.module.readme.service.ReadmeStructureService
import com.example.learning.setup.module.reference.service.ReferenceStructureService
import com.example.learning.setup.module.roadmap.service.RoadmapStructureService
import com.example.learning.setup.root.readmeMetadata.service.ReadmeMetadataSyncService
import org.gradle.testfixtures.ProjectBuilder
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
        for (String feature in [
                'BUILD_README', 'BUILD_QUIZ', 'BUILD_INTERVIEW',
                'BUILD_ROADMAP', 'BUILD_REFERENCE', 'BUILD_VIDEO'
        ]) {
            project.extensions.extraProperties.set(feature, 'TRUE')
        }

        new ReadmeStructureService(project.logger).setup(project)
        for (String language in ['vi', 'en']) {
            File doc = new File(
                    moduleDir,
                    "content/readme/docs/${language}/menu/1.Basic/Basic.md"
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
        assertTrue(new File(moduleDir, 'content/quiz').isDirectory())
        assertTrue(new File(moduleDir, 'content/interview').isDirectory())
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
        project.ext.BUILD_ROADMAP = 'TRUE'
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
        newProject.ext.BUILD_ROADMAP = 'TRUE'
        new RoadmapStructureService(newProject.logger).setup(newProject)
        assertTrue(new File(emptyModule, 'content/roadmap').isDirectory())
        assertEquals(0, new File(emptyModule, 'content/roadmap').listFiles().length)

        File disabledModule = temporary.newFolder('disabled-roadmap-module')
        def disabledProject = ProjectBuilder.builder().withProjectDir(disabledModule).build()
        disabledProject.ext.BUILD_ROADMAP = 'FALSE'
        new RoadmapStructureService(disabledProject.logger).setup(disabledProject)
        assertFalse(new File(disabledModule, 'content').exists())
    }

    @Test
    void roadmapSetupDoesNotOverwriteConflictingContentFile() {
        File moduleDir = temporary.newFolder('conflicting-roadmap-module')
        def project = ProjectBuilder.builder().withProjectDir(moduleDir).build()
        project.ext.BUILD_ROADMAP = 'TRUE'
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
    void quizAndInterviewSetupOnlyEnsureDirectoriesAndPreserveAuthoredFiles() {
        File moduleDir = temporary.newFolder('authored-module')
        def project = ProjectBuilder.builder().withProjectDir(moduleDir).build()
        project.ext.MODULE_LANGUAGE = 'vi,en'
        project.ext.BUILD_QUIZ = 'TRUE'
        project.ext.BUILD_INTERVIEW = 'TRUE'

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

        // BUILD is a feature switch: FALSE does not create even the folder.
        File disabledModule = temporary.newFolder('disabled-module')
        def disabledProject = ProjectBuilder.builder().withProjectDir(disabledModule).build()
        disabledProject.ext.BUILD_QUIZ = 'FALSE'
        disabledProject.ext.BUILD_INTERVIEW = 'FALSE'
        new QuizStructureService(disabledProject.logger).setup(disabledProject)
        new InterviewStructureService(disabledProject.logger).setup(disabledProject)
        assertFalse(new File(disabledModule, 'content').exists())
    }

    @Test
    void refusesReplacingExistingFilesWithFeatureFolders() {
        File moduleDir = temporary.newFolder('conflicting-module')
        def project = ProjectBuilder.builder().withProjectDir(moduleDir).build()
        project.ext.BUILD_QUIZ = 'TRUE'
        project.ext.BUILD_INTERVIEW = 'TRUE'
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
                '# Basic\n\n## <a id="basic-001">Overview</a>\n\nDescription\n',
                'UTF-8'
        )

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
        // Gradle settings evaluation. Exercise the actual metadata write plan and
        // safe writer directly to verify the persisted location.
        def service = new ReadmeMetadataSyncService(rootProject.logger)
        def planMethod = ReadmeMetadataSyncService.declaredMethods.find {
            it.name == 'buildWritePlan'
        }
        assertNotNull(planMethod)
        planMethod.accessible = true
        def plan = planMethod.invoke(service, moduleProject, languages.first())
        def writer = ReadmeMetadataSyncService.getDeclaredMethod(
                'writeSafelyIfChanged', File.class, String.class
        )
        writer.accessible = true
        assertTrue(writer.invoke(null, plan.outputFile, plan.content))

        File metadata = new File(
                moduleDir, 'content/readme/metadata/vi/knowledge-metadata.yml'
        )
        assertTrue('Knowledge metadata must live under content', metadata.isFile())
        assertTrue(metadata.getText('UTF-8').contains('difficulty: BASIC'))
        assertFalse(new File(
                moduleDir, 'src/main/resources/readme/vi/knowledge-metadata.yml'
        ).exists())
    }
}
