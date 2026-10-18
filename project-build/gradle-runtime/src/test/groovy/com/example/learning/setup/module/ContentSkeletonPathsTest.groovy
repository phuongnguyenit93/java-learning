package com.example.learning.setup.module

import com.example.learning.setup.module.interview.service.InterviewStructureService
import com.example.learning.setup.module.quiz.service.QuizStructureService
import com.example.learning.setup.module.readme.service.ReadmeStructureService
import com.example.learning.setup.module.reference.service.ReferenceStructureService
import com.example.learning.setup.module.roadmap.service.RoadmapStructureService
import com.example.learning.setup.module.video.service.VideoStructureService
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
    void createsEveryLearningSkeletonUnderContentNotRuntimeResources() {
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
        new VideoStructureService(project.logger).setup(project)

        assertTrue(new File(moduleDir, 'README.md').isFile())
        assertTrue(new File(moduleDir, 'content/reference/references.yml').isFile())
        for (String language in ['vi', 'en']) {
            assertTrue(new File(moduleDir, "content/readme/docs/${language}/BASE.md").isFile())
            assertTrue(new File(moduleDir, "content/readme/docs/${language}/LIST.md").isFile())
            assertTrue(new File(moduleDir, "content/quiz/${language}/question.yml").isFile())
            assertTrue(new File(moduleDir, "content/interview/${language}/question.yml").isFile())
            assertTrue(new File(moduleDir, "content/roadmap/${language}/roadmap.yml").isFile())
            assertTrue(new File(moduleDir, "content/video/${language}/menu/1.Basic/Basic.md").isFile())
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
    void buildsKnowledgeMetadataOutsideRuntimeResources() {
        File rootDirectory = temporary.newFolder('repository')
        File moduleDir = new File(rootDirectory, 'module/example')
        assertTrue(moduleDir.mkdirs())
        new File(moduleDir, 'gradle.properties').setText(
                'MODULE_LANGUAGE=vi\n', 'UTF-8'
        )
        File markdown = new File(
                moduleDir, 'content/readme/docs/vi/menu/1.Basic/Basic.md'
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
