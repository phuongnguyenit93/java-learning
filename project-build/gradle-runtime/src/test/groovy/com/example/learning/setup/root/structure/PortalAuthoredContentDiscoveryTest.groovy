package com.example.learning.setup.root.structure

import com.example.learning.setup.root.structure.service.PortalReferenceProjectionService
import com.example.learning.setup.root.structure.service.PortalRoadmapProjectionService
import com.example.learning.setup.root.structure.service.ProjectStructureService
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.yaml.snakeyaml.Yaml

import static org.junit.Assert.*

class PortalAuthoredContentDiscoveryTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder()

    @Test
    void publishesRoadmapAndReferencesFromContentWithoutBuildFlags() {
        File rootDir = temporary.newFolder('repo')
        write(rootDir,
                'project-build/gradle-runtime/src/main/resources/local-run/fingerprint-v2-roots.txt',
                '# No shared Git inputs needed for this fixture\n')
        File moduleDir = new File(rootDir, 'module/example')
        assertTrue(moduleDir.mkdirs())
        new File(moduleDir, 'gradle.properties').setText('IS_MODULE=TRUE\n', 'UTF-8')
        write(moduleDir, 'master.json', '''{"CURRENT_STEP":{"VALUE":"0"},
                "SERVICE_NAME":{"VALUE":"EXAMPLE"},
                "MODULE_LANGUAGE":{"VALUE":["vi"]}}''')
        Project rootProject = ProjectBuilder.builder().withProjectDir(rootDir).build()
        Project module = ProjectBuilder.builder().withParent(rootProject)
                .withProjectDir(moduleDir).build()
        module.extensions.extraProperties.set('MODULE_LANGUAGE', 'vi')
        module.extensions.extraProperties.set('SERVICE_NAME', 'EXAMPLE')

        File roadmap = write(moduleDir, 'content/roadmap/vi/roadmap.yml', 'roadmap: []\n')
        File englishRoadmap = write(moduleDir, 'content/roadmap/en/roadmap.yml',
                new Yaml().dump([roadmap: [[id: 'en', title: 'EN', purpose: 'Learning']]]))
        File reference = write(moduleDir, 'content/reference/vi/references.yml', 'references: []\n')
        File englishReference = write(moduleDir, 'content/reference/en/references.yml',
                new Yaml().dump([references: [
                        [title: 'English specification', url: 'https://example.org/en']
                ]]))

        assertFalse(PortalRoadmapProjectionService.hasAuthoredRoadmap(roadmap))
        assertFalse(PortalReferenceProjectionService.hasAuthoredReferences(reference))
        assertEquals([] as Set, roadmapLanguages(moduleDir, ['vi']))
        assertEquals([] as Set, referenceLanguages(moduleDir, ['vi']))

        File staleRoadmap = published(rootDir, 'EXAMPLE/roadmap/vi/roadmap.yml')
        File staleReference = published(rootDir, 'EXAMPLE/reference/vi/references.yml')
        File oldSharedReference = published(rootDir, 'EXAMPLE/reference/references.yml')
        File staleEnglishReference = published(rootDir, 'EXAMPLE/reference/en/references.yml')
        assertTrue(staleRoadmap.parentFile.mkdirs())
        staleRoadmap.setText('stale roadmap', 'UTF-8')
        assertTrue(staleReference.parentFile.mkdirs())
        staleReference.setText('stale references', 'UTF-8')
        oldSharedReference.setText('retired shared references', 'UTF-8')
        assertTrue(staleEnglishReference.parentFile.mkdirs())
        staleEnglishReference.setText('stale English references', 'UTF-8')
        new PortalRoadmapProjectionService(rootProject.logger).generate(rootProject)
        new PortalReferenceProjectionService(rootProject.logger).generate(rootProject)
        assertFalse(staleRoadmap.exists())
        assertFalse(staleReference.exists())
        assertFalse(oldSharedReference.exists())
        assertFalse(staleEnglishReference.exists())
        assertFalse(published(rootDir, 'EXAMPLE/roadmap/en/roadmap.yml').exists())

        roadmap.setText(new Yaml().dump([roadmap: [
                [id: 'foundations', title: 'Foundations', purpose: 'Learn the foundation']
        ]]), 'UTF-8')
        reference.setText(new Yaml().dump([references: [
                [title: 'Official specification', url: 'https://example.org/spec']
        ]]), 'UTF-8')
        assertTrue(PortalRoadmapProjectionService.hasAuthoredRoadmap(roadmap))
        assertTrue(PortalReferenceProjectionService.hasAuthoredReferences(reference))
        assertEquals(['vi'] as Set, roadmapLanguages(moduleDir, ['vi']))
        assertEquals(['vi'] as Set, referenceLanguages(moduleDir, ['vi']))
        new PortalRoadmapProjectionService(rootProject.logger).generate(rootProject)
        assertTrue('Nonempty Roadmap must be published without BUILD_ROADMAP',
                staleRoadmap.isFile())
        new PortalReferenceProjectionService(rootProject.logger).generate(rootProject)
        assertEquals(roadmap.getText('UTF-8'), staleRoadmap.getText('UTF-8'))
        assertEquals(reference.getText('UTF-8'), staleReference.getText('UTF-8'))
        assertFalse('English Reference is not published while MODULE_LANGUAGE only declares vi',
                staleEnglishReference.exists())
        assertFalse('Only declared MODULE_LANGUAGE is published',
                published(rootDir, 'EXAMPLE/roadmap/en/roadmap.yml').exists())
        assertTrue(englishRoadmap.isFile())

        write(moduleDir, 'master.json', '''{"CURRENT_STEP":{"VALUE":"0"},
                "SERVICE_NAME":{"VALUE":"EXAMPLE"},
                "MODULE_LANGUAGE":{"VALUE":["vi","en"]}}''')
        module.extensions.extraProperties.set('MODULE_LANGUAGE', 'vi,en')
        assertEquals(['vi', 'en'] as Set, referenceLanguages(moduleDir, ['vi', 'en']))
        new PortalReferenceProjectionService(rootProject.logger).generate(rootProject)
        assertEquals(englishReference.getText('UTF-8'), staleEnglishReference.getText('UTF-8'))

        new ProjectStructureService(rootProject.logger).generatePortalData(rootProject)
        Map catalog = new groovy.json.JsonSlurper().parse(
                new File(rootDir, 'build/learning-data/java/module-catalog.json')) as Map
        Map publishedModule = catalog.root.children.find { it.routeId == 'EXAMPLE' } as Map
        assertEquals([
                vi: '/module/EXAMPLE/reference/vi/references.yml',
                en: '/module/EXAMPLE/reference/en/references.yml'
        ], publishedModule.reference)

        englishReference.setText('references: []\n', 'UTF-8')
        new PortalReferenceProjectionService(rootProject.logger).generate(rootProject)
        assertFalse('Empty English references remove stale published asset',
                staleEnglishReference.exists())
        new ProjectStructureService(rootProject.logger).generatePortalData(rootProject)
        catalog = new groovy.json.JsonSlurper().parse(
                new File(rootDir, 'build/learning-data/java/module-catalog.json')) as Map
        publishedModule = catalog.root.children.find { it.routeId == 'EXAMPLE' } as Map
        assertEquals([vi: '/module/EXAMPLE/reference/vi/references.yml'],
                publishedModule.reference)
    }

    @Test
    void ignoresCommentOnlySkeletonSources() {
        File root = temporary.newFolder('comment-only')
        File roadmap = write(root, 'roadmap.yml', '# comments only\n')
        File references = write(root, 'references.yml', '# comments only\n')
        assertFalse(PortalRoadmapProjectionService.hasAuthoredRoadmap(roadmap))
        assertFalse(PortalReferenceProjectionService.hasAuthoredReferences(references))
    }

    @Test
    void rejectsInvalidNonemptyAuthoredDocuments() {
        File root = temporary.newFolder('invalid-sources')
        File roadmap = write(root, 'roadmap.yml',
                'roadmap:\n  - id: milestone\n    title: Missing purpose\n')
        File references = write(root, 'references.yml',
                'references:\n  - title: Missing URL\n')
        try {
            PortalRoadmapProjectionService.hasAuthoredRoadmap(roadmap)
            fail('Invalid nonempty Roadmap must fail schema validation.')
        } catch (GradleException expected) {
            assertTrue(expected.message.contains('roadmap'))
        }
        try {
            PortalReferenceProjectionService.hasAuthoredReferences(references)
            fail('Invalid nonempty Reference must fail schema validation.')
        } catch (GradleException expected) {
            assertTrue(expected.message.contains('reference'))
        }
    }

    private static File write(File root, String relative, String value) {
        File target = new File(root, relative)
        assertTrue(target.parentFile.isDirectory() || target.parentFile.mkdirs())
        target.setText(value, 'UTF-8')
        return target
    }

    private static File published(File root, String relative) {
        return new File(root, 'build/learning-data/java/module/' + relative)
    }

    private static Set roadmapLanguages(File moduleDir, List<String> languages) {
        def method = ProjectStructureService.getDeclaredMethod('findRoadmapLanguages', File, List)
        method.accessible = true
        return method.invoke(null, moduleDir, languages) as Set
    }

    private static Set referenceLanguages(File moduleDir, List<String> languages) {
        def method = ProjectStructureService.getDeclaredMethod('findReferenceLanguages', File, List)
        method.accessible = true
        return method.invoke(null, moduleDir, languages) as Set
    }
}
