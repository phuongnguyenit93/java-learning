package com.example.learning.setup.root.readmeMetadata.service

import org.gradle.api.logging.Logging
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.yaml.snakeyaml.Yaml

import static org.junit.Assert.*

class ReadmeMetadataSyncServiceTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder()

    @Test
    void syncingLegacySectionsDiscardsOnlyObsoleteGovernanceMetadata() {
        Map section = [difficulty: 'ADVANCED',
                aiGenerated: false, reviewed: true]
        def method = ReadmeMetadataSyncService.getDeclaredMethod(
                'ensureRequiredFields', Map, String, String, String, String)
        method.accessible = true
        method.invoke(null, section, 'module', 'vi', '1.Basic/knowledge/Basic.md', 'basic-001')
        assertEquals([difficulty: 'ADVANCED'], section)
    }

    @Test
    void importIgnoresLegacyMetadataAndBuildsDefaultsFromSheet() {
        File module = tmp.newFolder('module')
        File metadata = new File(module,
                'content/readme/metadata/vi/knowledge-metadata.yml')
        assertTrue(metadata.parentFile.mkdirs())
        metadata.setText('''Lesson.md:
  existing-id:
    difficulty: ADVANCED


    humanNote: preserve-this
''', 'UTF-8')

        ReadmeMetadataSyncService service = new ReadmeMetadataSyncService(
                Logging.getLogger(ReadmeMetadataSyncServiceTest))
        String source = '2.Discovery Server/knowledge/Lesson.md'
        String markdown = '## <a id="existing-id">Existing knowledge</a>\n'
        String result = service.prepareImportedMetadata(module,
                [(source): markdown], [(source): [null]])
        Map root = new Yaml().load(result)

        assertEquals([source] as Set, root.keySet())
        assertEquals([difficulty: 'BASIC'], root[source]['existing-id'])
    }

    @Test
    void oldLegacyPathDoesNotInfluenceImportedMetadata() {
        File module = tmp.newFolder('otherModule')
        File metadata = new File(module,
                'content/readme/metadata/vi/knowledge-metadata.yml')
        assertTrue(metadata.parentFile.mkdirs())
        metadata.setText('''2.Spring_Task_Executor/Lesson.md:
  stable-id:
    difficulty: INTERMEDIATE


2.Spring_Task_Executor/knowledge/Lesson.md:
  stable-id:
    humanNote: newer-path
''', 'UTF-8')

        ReadmeMetadataSyncService service = new ReadmeMetadataSyncService(
                Logging.getLogger(ReadmeMetadataSyncServiceTest))
        String source = '2.Spring_Task_Executor/Lesson.md'
        String result = service.prepareImportedMetadata(module,
                [(source): '## <a id="stable-id">Valid</a>\n'],
                [(source): [null]])
        Map root = new Yaml().load(result)
        Map section = root['2.Spring_Task_Executor/knowledge/Lesson.md']['stable-id']
        assertEquals('BASIC', section.difficulty)
        assertEquals(['difficulty'] as Set, section.keySet())
        assertFalse(section.containsKey('humanNote'))
    }

    @Test
    void scansOnlyDirectChapterKnowledgeMarkdown() {
        File menu = tmp.newFolder('menu')
        new File(menu, 'Legacy.md').setText('# legacy\n', 'UTF-8')
        File chapter = new File(menu, '2.Discovery Server')
        assertTrue(chapter.mkdir())
        new File(chapter, 'Old.md').setText('# old\n', 'UTF-8')
        File knowledge = new File(chapter, 'knowledge')
        assertTrue(knowledge.mkdir())
        File article = new File(knowledge, 'Lesson.md')
        article.setText('# knowledge\n', 'UTF-8')
        new File(knowledge, 'image.png').setText('image', 'UTF-8')

        def scanner = ReadmeMetadataSyncService.getDeclaredMethod(
                'collectMarkdownFiles', File)
        scanner.accessible = true
        List<File> found = scanner.invoke(null, menu) as List<File>
        assertEquals([article.canonicalFile], found*.canonicalFile)
    }

    @Test
    void importWritesFreshSectionsWrapperIntoEachChapter() {
        File module = tmp.newFolder('chapters')
        ReadmeMetadataSyncService service = new ReadmeMetadataSyncService(
                Logging.getLogger(ReadmeMetadataSyncServiceTest))
        String first = '1.First/knowledge/First.md'
        String second = '2.Second/knowledge/Second.md'
        Map docs = [(first): '## <a id="first-id">First</a>\n',
                    (second): '## <a id="second-id">Second</a>\n']
        Map levels = [(first): ['BASIC'], (second): ['ADVANCED']]
        String prepared = service.prepareImportedMetadata(module, docs, levels)
        assertEquals('DRY_RUN', service.syncImportedMetadata(module, prepared, false).status)
        assertEquals('UPDATED', service.syncImportedMetadata(module, prepared, true).status)

        File firstFile = new File(module,
                'content/readme/docs/vi/menu/1.First/metadata/knowledge-metadata.yml')
        File secondFile = new File(module,
                'content/readme/docs/vi/menu/2.Second/metadata/knowledge-metadata.yml')
        Map a = new Yaml().load(firstFile.getText('UTF-8'))
        Map b = new Yaml().load(secondFile.getText('UTF-8'))
        assertEquals(['sections'] as Set, a.keySet())
        assertEquals(['sections'] as Set, b.keySet())
        assertEquals(['first-id'] as Set, a.sections.keySet())
        assertEquals(['second-id'] as Set, b.sections.keySet())
        assertEquals('ADVANCED', b.sections['second-id'].difficulty)
        firstFile.setText(new Yaml().dump(a), 'UTF-8')
        Map result = new Yaml().load(service.prepareImportedMetadata(module, docs, levels))
        assertEquals('DRY_RUN',
                service.syncImportedMetadata(module, prepared, false).status)
        assertFalse(new File(module,
                'content/readme/metadata/vi/knowledge-metadata.yml').exists())
    }

    @Test
    void importedMetadataDoesNotReadMalformedOldChapterFile() {
        File module = tmp.newFolder('strict-wrapper')
        File file = new File(module,
                'content/readme/docs/vi/menu/1.Basic/metadata/knowledge-metadata.yml')
        assertTrue(file.parentFile.mkdirs())
        file.setText('''basic-001:
  difficulty: ADVANCED


''', 'UTF-8')
        ReadmeMetadataSyncService service = new ReadmeMetadataSyncService(
                Logging.getLogger(ReadmeMetadataSyncServiceTest))
        String source = '1.Basic/knowledge/Basic.md'
        Map fresh = new Yaml().load(service.prepareImportedMetadata(module,
                [(source): '## <a id="basic-001">Basic</a>\n'],
                [(source): ['']]))
        assertEquals('BASIC', fresh[source]['basic-001'].difficulty)
        assertTrue(file.getText('UTF-8').startsWith('basic-001:'))
    }
}
