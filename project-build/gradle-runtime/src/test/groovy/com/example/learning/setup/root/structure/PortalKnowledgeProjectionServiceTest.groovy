package com.example.learning.setup.root.structure

import com.example.learning.setup.root.structure.service.PortalKnowledgeProjectionService
import com.example.learning.utils.ProjectPropertyUtils
import groovy.json.JsonSlurper
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.junit.Assert.*

class PortalKnowledgeProjectionServiceTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder()

    @Test
    void projectsOnlyKnowledgeMarkdownRecursivelyWithoutKnowledgeFolderOrChangedAnchors() {
        File root = temp.newFolder('repo')
        File moduleDir = new File(root, 'module/example')
        assertTrue(moduleDir.mkdirs())
        new File(moduleDir, 'gradle.properties').text = 'IS_MODULE=TRUE\n'

        def rootProject = ProjectBuilder.builder().withProjectDir(root).build()
        def module = ProjectBuilder.builder().withParent(rootProject)
                .withProjectDir(moduleDir).build()
        module.extensions.extraProperties.set('MODULE_LANGUAGE', 'vi')
        module.extensions.extraProperties.set('SERVICE_NAME', 'EXAMPLE')
        assertTrue(rootProject.allprojects.contains(module))
        assertEquals(['vi'], ProjectPropertyUtils.getStringList(module, 'MODULE_LANGUAGE'))

        write(moduleDir, 'content/readme/docs/vi/menu/00.Overview.md',
                '# Overview\n\n## <a id="overview-anchor">Overview section</a>\n\nOverview body\n')
        write(moduleDir, 'content/readme/docs/vi/menu/1.Chapter/chapter-links.md',
                '# Chapter links\n')
        write(moduleDir, 'content/readme/docs/vi/menu/1.Chapter/Legacy.md',
                '# Legacy\n\n## <a id="legacy-anchor">Legacy</a>\n')
        write(moduleDir, 'content/readme/docs/vi/menu/1.Chapter/knowledge/Lesson.md',
                '# Lesson title\n\nIntroduction\n\n## <a id="lesson-anchor">Lesson</a>\n\nKnowledge body\n')
        write(moduleDir, 'content/readme/docs/vi/menu/1.Chapter/knowledge/deep/Other.md',
                '# Other\n\n## <a id="other-anchor">Other</a>\n\nNested body\n')
        write(moduleDir, 'content/readme/docs/vi/menu/1.Chapter/knowledge/chapter-links.md',
                '# Not knowledge\n')
        write(moduleDir, 'content/readme/docs/vi/menu/2.NoKnowledge/chapter-links.md',
                '# Navigation only\n')

        File menuDir = new File(moduleDir, 'content/readme/docs/vi/menu')
        File knowledgeFile = new File(menuDir, '1.Chapter/knowledge/Lesson.md')
        def knowledgeFilter = PortalKnowledgeProjectionService.getDeclaredMethod(
                'isKnowledgeMarkdownFile', File, File)
        knowledgeFilter.accessible = true
        assertTrue('knowledge file filter', knowledgeFilter.invoke(null, menuDir, knowledgeFile))
        def contains = PortalKnowledgeProjectionService.getDeclaredMethod(
                'containsMarkdownFile', File, File)
        contains.accessible = true
        assertTrue('knowledge directory discovery', contains.invoke(null, menuDir, menuDir))

        def moduleResolver = PortalKnowledgeProjectionService.getDeclaredMethod(
                'resolveRealModuleProjects', org.gradle.api.Project, File)
        moduleResolver.accessible = true
        assertTrue('real module discovery', (moduleResolver.invoke(null, rootProject,
                new File(root, 'module')) as Collection).contains(module))

        def sourceResolver = PortalKnowledgeProjectionService.getDeclaredMethod(
                'resolveLanguageSources', org.gradle.api.Project)
        sourceResolver.accessible = true
        assertEquals('knowledge language discovery', 1,
                (sourceResolver.invoke(null, module) as Collection).size())

        def service = new PortalKnowledgeProjectionService(rootProject.logger)
        def sources = sourceResolver.invoke(null, module) as Collection
        def projectLanguage = sources.first()
        def projection = PortalKnowledgeProjectionService.declaredMethods.find {
            it.name == 'generateLanguageProjection'
        }
        projection.accessible = true
        Map<String, String> outputs = [:]
        Map<String, byte[]> assets = [:]
        projection.invoke(service, module, 'EXAMPLE', projectLanguage, outputs, assets)
        String indexJson = outputs['EXAMPLE/knowledge/vi/index.json']
        assertNotNull('Knowledge projection index must be produced', indexJson)
        Map index = new JsonSlurper().parseText(indexJson) as Map
        assertEquals(3, index.categoryCount)
        assertEquals(3, index.sectionCount)
        assertEquals(2, index.tree.size())
        assertTrue(index.tree.any { it.kind == 'FOLDER' && it.title == '1.Chapter' })
        assertFalse(hasKnowledgeFolder(index.tree))

        List<Map> categories = collectCategories(index.tree)
        assertEquals(['00.Overview', 'Lesson', 'Other'], categories*.id)
        assertEquals(['00.Overview.md',
                      '1.Chapter/knowledge/Lesson.md',
                      '1.Chapter/knowledge/deep/Other.md'], categories*.sourcePath)
        assertEquals(['overview-anchor', 'lesson-anchor', 'other-anchor'],
                categories.collectMany { Map category -> category.sections*.id })

        outputs.clear()
        projection.invoke(service, module, 'EXAMPLE', projectLanguage, outputs, assets)
        assertEquals(3, (new JsonSlurper().parseText(
                outputs['EXAMPLE/knowledge/vi/index.json']) as Map).categoryCount)
    }

    private static List<Map> collectCategories(Collection nodes) {
        nodes.collectMany { Map node ->
            node.kind == 'CATEGORY' ? [node] : collectCategories(node.children ?: [])
        }
    }

    private static boolean hasKnowledgeFolder(Collection nodes) {
        nodes.any { Map node ->
            (node.kind == 'FOLDER' && node.title == 'knowledge') ||
                    hasKnowledgeFolder(node.children ?: [])
        }
    }

    private static void write(File moduleDir, String path, String content) {
        File file = new File(moduleDir, path)
        assertTrue(file.parentFile.isDirectory() || file.parentFile.mkdirs())
        file.setText(content, 'UTF-8')
    }
}
