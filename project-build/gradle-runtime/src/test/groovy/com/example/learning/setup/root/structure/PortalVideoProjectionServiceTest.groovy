package com.example.learning.setup.root.structure

import com.example.learning.setup.root.structure.service.PortalVideoProjectionService
import com.example.learning.setup.root.structure.service.ProjectStructureService
import com.example.learning.utils.ProjectPropertyUtils
import com.example.learning.task.sheets.service.VideoSheetImportServiceTest
import groovy.json.JsonSlurper
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.junit.Assert.*

class PortalVideoProjectionServiceTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder()

    @Test
    void projectsDifferentYamlChaptersToSeparateCategoryIdsAndPreservesJsonContract() {
        File root = temp.newFolder('repo')
        File moduleDir = new File(root, 'module/example')
        assertTrue(moduleDir.mkdirs())
        new File(moduleDir, 'gradle.properties').setText('IS_MODULE=TRUE\n', 'UTF-8')
        def rootProject = ProjectBuilder.builder().withProjectDir(root).build()
        def module = ProjectBuilder.builder().withParent(rootProject)
                .withProjectDir(moduleDir).build()
        module.extensions.extraProperties.set('MODULE_LANGUAGE', 'vi')
        module.extensions.extraProperties.set('SERVICE_NAME', 'EXAMPLE')
        assertTrue(rootProject.allprojects.contains(module))
        assertEquals(['vi'], ProjectPropertyUtils.getStringList(module, 'MODULE_LANGUAGE'))

        File first = new File(moduleDir, 'content/readme/docs/vi/menu/1.OOPBasic/video/video-script.yml')
        File second = new File(moduleDir, 'content/readme/docs/vi/menu/2.Class/video/video-script.yml')
        assertTrue(first.parentFile.mkdirs())
        assertTrue(second.parentFile.mkdirs())
        first.setText(VideoSheetImportServiceTest.SCRIPT, 'UTF-8')
        second.setText(VideoSheetImportServiceTest.SCRIPT.replace(
                'Tư duy OOP', 'Lớp'), 'UTF-8')
        // Neither obsolete module-level sources nor unrelated chapter files
        // are authoring Video paths after migration.
        File legacy = new File(moduleDir, 'content/video/vi/menu/3.Legacy/video-script.yml')
        assertTrue(legacy.parentFile.mkdirs())
        legacy.setText(VideoSheetImportServiceTest.SCRIPT, 'UTF-8')
        File unrelated = new File(moduleDir,
                'content/readme/docs/vi/menu/3.Unrelated/knowledge/video-script.yml')
        assertTrue(unrelated.parentFile.mkdirs())
        unrelated.setText('not a Video YAML', 'UTF-8')
        def videoLanguages = ProjectStructureService.getDeclaredMethod(
                'findVideoLanguages', File, List)
        videoLanguages.accessible = true
        assertEquals(['vi'] as Set, videoLanguages.invoke(null, moduleDir, ['vi']) as Set)
        assertTrue((videoLanguages.invoke(null, moduleDir, ['en']) as Set).isEmpty())
        assertTrue(first.isFile())
        assertTrue(second.isFile())
        assertTrue(rootProject.allprojects.any { p ->
            p.projectDir.toPath().toAbsolutePath().normalize().startsWith(
                    new File(root, 'module').toPath().toAbsolutePath().normalize()) &&
                    new File(p.projectDir, 'gradle.properties').isFile()
        })

        def projector = new PortalVideoProjectionService(rootProject.logger)
        projector.generate(rootProject)

        File resultRoot = new File(root, 'build/learning-data/java/module/EXAMPLE/video/vi')
        File indexFile = new File(resultRoot, 'index.json')
        assertTrue("Expected ${indexFile}, actual files: " +
                new File(root, 'build/learning-data/java').listFiles()?.toList(),
                indexFile.isFile())
        Map index = new JsonSlurper().parse(indexFile) as Map
        assertEquals(2, index.videoCount)
        assertEquals(['OOPBasic', 'Class'], index.items*.categoryId)
        assertEquals(['/module/EXAMPLE/video/vi/content/OOPBasic/script.json',
                      '/module/EXAMPLE/video/vi/content/Class/script.json'],
                index.items*.content)
        for (String category in ['OOPBasic', 'Class']) {
            File scriptFile = new File(resultRoot, "content/${category}/script.json")
            assertTrue(scriptFile.isFile())
            Map script = new JsonSlurper().parse(scriptFile) as Map
            assertEquals(category, script.categoryId)
            assertEquals(2, script.sections.size())
            assertEquals([1, 2], script.sections*.order)
            assertEquals(['SCENE', 'TRANSITION', 'SCENE'],
                    script.sections.collectMany { Map s -> s.items*.type })
            assertTrue(script.sourcePath.endsWith('/video-script.yml'))
            assertTrue(script.sourcePath.endsWith('/video/video-script.yml'))
            assertEquals('', script.video.url)
        }
        projector.generate(rootProject)
        assertEquals(2, (new JsonSlurper().parse(indexFile) as Map).videoCount)
        assertTrue(first.delete())
        assertTrue(second.delete())
        projector.generate(rootProject)
        assertFalse(indexFile.exists())
        assertFalse(new File(resultRoot, 'content/OOPBasic/script.json').exists())
        assertTrue((videoLanguages.invoke(null, moduleDir, ['vi']) as Set).isEmpty())
    }
}
