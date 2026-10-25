package com.example.learning.task.readme

import com.example.learning.task.readme.finalReadme.service.GenerateFinalReadmeService
import com.example.learning.task.readme.internalMenu.task.GenerateInternalReadmeMenuForModuleTask
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.junit.Assert.*

class ReadmeKnowledgeFolderTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder()

    @Test
    void finalReadmeListsOnlyKnowledgeFilesAndFlattensStorageFolder() {
        File projectDir = temp.newFolder('module')
        write(projectDir, 'content/readme/docs/vi/BASE.md', '# Module intro\n')
        write(projectDir, 'content/readme/docs/vi/menu/00.Overview.md', '# Overview\n')
        write(projectDir, 'content/readme/docs/vi/menu/1.Chapter/chapter-links.md',
                '# Navigation\n')
        write(projectDir, 'content/readme/docs/vi/menu/1.Chapter/Legacy.md', '# Legacy\n')
        write(projectDir, 'content/readme/docs/vi/menu/1.Chapter/knowledge/Main.md',
                '# Main\n')
        write(projectDir, 'content/readme/docs/vi/menu/1.Chapter/knowledge/deep/Nested.md',
                '# Nested\n')
        write(projectDir, 'content/readme/docs/vi/menu/1.Chapter/knowledge/chapter-links.md',
                '# Navigation in knowledge\n')
        write(projectDir, 'content/readme/docs/vi/menu/2.Empty/chapter-links.md',
                '# Navigation only\n')

        def project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        def service = new GenerateFinalReadmeService(project.logger)
        service.generate(projectDir, ['vi'])

        File listFile = new File(projectDir, 'content/readme/docs/vi/LIST.md')
        String list = listFile.getText('UTF-8')
        assertTrue(list.contains('* **1.Chapter**'))
        assertTrue(list.contains('menu/00.Overview.md'))
        assertTrue(list.contains('1.Chapter/knowledge/Main.md'))
        assertTrue(list.contains('1.Chapter/knowledge/deep/Nested.md'))
        assertTrue(list.contains('* **deep**'))
        assertFalse(list.contains('* **knowledge**'))
        assertFalse(list.contains('Legacy.md'))
        assertFalse(list.contains('chapter-links.md'))
        assertFalse(list.contains('2.Empty'))
        assertEquals(3, list.readLines().count { it.contains('](') })

        File finalReadme = new File(projectDir, 'README.vi.md')
        assertTrue(finalReadme.isFile())
        assertTrue(finalReadme.getText('UTF-8').contains(list.trim()))
        service.generate(projectDir, ['vi'])
        assertEquals(list, listFile.getText('UTF-8'))
    }

    @Test
    void internalMenuTouchesOnlyKnowledgeMarkdownAndPreservesSectionIds() {
        File projectDir = temp.newFolder('taskModule')
        File rootKnowledge = write(projectDir,
                'content/readme/docs/vi/menu/00.Overview.md',
                '# Overview\n\n## <a id="overview-anchor">Root</a>\n\nRoot body\n')
        File legacy = write(projectDir,
                'content/readme/docs/vi/menu/1.Chapter/Legacy.md', '# Legacy\n')
        File links = write(projectDir,
                'content/readme/docs/vi/menu/1.Chapter/chapter-links.md', '# Links\n')
        File innerLinks = write(projectDir,
                'content/readme/docs/vi/menu/1.Chapter/knowledge/chapter-links.md',
                '# Internal links\n')
        File first = write(projectDir,
                'content/readme/docs/vi/menu/1.Chapter/knowledge/Main.md',
                '# Main\n\n## <a id="original-anchor">First</a>\n\nBody one\n')
        File nested = write(projectDir,
                'content/readme/docs/vi/menu/1.Chapter/knowledge/deep/Nested.md',
                '# Nested\n\n## <a id="nested-anchor">Second</a>\n\nBody two\n')

        def project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        def task = project.tasks.create('internalMenu', GenerateInternalReadmeMenuForModuleTask)
        task.languages.set(['vi'])
        task.generate()

        assertEquals('# Legacy\n', legacy.getText('UTF-8'))
        assertEquals('# Links\n', links.getText('UTF-8'))
        assertEquals('# Internal links\n', innerLinks.getText('UTF-8'))
        assertTrue(first.getText('UTF-8').contains('original-anchor'))
        assertTrue(nested.getText('UTF-8').contains('nested-anchor'))
        assertTrue(rootKnowledge.getText('UTF-8').contains('overview-anchor'))
        assertTrue(rootKnowledge.getText('UTF-8').contains('## Menu'))
        assertTrue(first.getText('UTF-8').contains('## Menu'))
        assertTrue(nested.getText('UTF-8').contains('## Menu'))
    }

    private static File write(File root, String path, String value) {
        File file = new File(root, path)
        assertTrue(file.parentFile.isDirectory() || file.parentFile.mkdirs())
        file.setText(value, 'UTF-8')
        return file
    }
}
