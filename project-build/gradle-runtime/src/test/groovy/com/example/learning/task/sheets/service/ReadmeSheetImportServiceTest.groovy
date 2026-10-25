package com.example.learning.task.sheets.service

import com.example.learning.task.readme.internalMenu.service.GenerateInternalReadmeMenuService
import com.example.learning.setup.root.readmeMetadata.service.ReadmeMetadataSyncService
import org.gradle.api.logging.Logging
import org.yaml.snakeyaml.Yaml
import org.gradle.api.GradleException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.junit.Assert.*

class ReadmeSheetImportServiceTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder()

    @Test
    void metadataFromKnowledgeIdsAndDifficultyIgnoresExistingReviewFlags() {
        File root = temporary.newFolder('metadata-import')
        File module = new File(root, 'module/test-module')
        assertTrue(module.mkdirs())
        List<Map> groups = [[title:'Chương OOP', filename:'OOPBasic.md', parentRow:20,
                             children:[[title:'Bài một',row:21],[title:'Bài hai',row:22]]]]
        Map<String, String> docs = ReadmeSheetImportService.buildDocuments(groups,
                [[], ['Nội dung một\n```md\n## <a id="fake">Example only</a>\n```'],
                 ['Nội dung hai']])
        Map levels = ReadmeSheetImportService.buildSectionDifficulties(groups,
                [[], ['ADVANCED'], []])
        assertEquals(['ADVANCED', ''], levels['1.OOPBasic/knowledge/OOPBasic.md'])
        def service = new ReadmeMetadataSyncService(Logging.getLogger(ReadmeSheetImportServiceTest))
        String prepared = service.prepareImportedMetadata(module, docs, levels)
        Map metadata = new Yaml().load(prepared) as Map
        Map file = metadata['1.OOPBasic/knowledge/OOPBasic.md'] as Map
        assertEquals(['oop-basic-001','oop-basic-002'], file.keySet().toList())
        assertEquals('ADVANCED', file['oop-basic-001'].difficulty)
        assertEquals('BASIC', file['oop-basic-002'].difficulty)
        assertEquals(true, file['oop-basic-001'].aiGenerated)
        assertEquals(false, file['oop-basic-001'].reviewed)
        assertEquals(false, file['oop-basic-001'].isImportant)
        assertEquals('DRY_RUN', service.syncImportedMetadata(module, prepared, false).status)
        assertEquals('UPDATED', service.syncImportedMetadata(module, prepared, true).status)

        File metadataFile = new File(module,
                'content/readme/docs/vi/menu/1.OOPBasic/metadata/knowledge-metadata.yml')
        Map existing = new Yaml().load(metadataFile.getText('UTF-8')) as Map
        assertEquals(['sections'] as Set, existing.keySet())
        existing.sections['oop-basic-001'].reviewed = true
        existing.sections['oop-basic-001'].aiGenerated = false
        existing.sections['oop-basic-001'].isImportant = true
        metadataFile.setText(new Yaml().dump(existing),'UTF-8')
        String refreshed = service.prepareImportedMetadata(module,docs,levels)
        Map updated = new Yaml().load(refreshed) as Map
        assertEquals(false,updated['1.OOPBasic/knowledge/OOPBasic.md']['oop-basic-001'].reviewed)
        assertEquals(true,updated['1.OOPBasic/knowledge/OOPBasic.md']['oop-basic-001'].aiGenerated)
        assertEquals(false,updated['1.OOPBasic/knowledge/OOPBasic.md']['oop-basic-001'].isImportant)
        assertEquals('ADVANCED',updated['1.OOPBasic/knowledge/OOPBasic.md']['oop-basic-001'].difficulty)
        assertEquals('BASIC',updated['1.OOPBasic/knowledge/OOPBasic.md']['oop-basic-002'].difficulty)
        // Explicit TRUE/FALSE in AB is the only source; blank uses false.
        def importances = ReadmeSheetImportService.buildSectionImportances(groups,
                [[], [false], [true]])
        String overridden = service.prepareImportedMetadata(module, docs, levels, importances)
        Map flagged = new Yaml().load(overridden) as Map
        assertEquals(false, flagged['1.OOPBasic/knowledge/OOPBasic.md']['oop-basic-001'].isImportant)
        assertEquals(true, flagged['1.OOPBasic/knowledge/OOPBasic.md']['oop-basic-002'].isImportant)
    }

    @Test
    void invalidSheetDifficultyFailsBeforeAnyMetadataWrite() {
        List<Map> groups = [[filename:'Basics.md',children:[[row:21]]]]
        try {
            ReadmeSheetImportService.buildSectionDifficulties(groups,[[],['EXPERT']])
            fail('Unexpected Difficulty accepted')
        } catch (GradleException expected) {
            assertTrue(expected.message.contains('O21'))
        }
    }

    @Test
    void validatesSheetImportanceAndKeepsBlankForHumanMetadata() {
        List<Map> groups = [[filename:'Basics.md',children:[[row:21],[row:22],[row:23]]]]
        Map flags = ReadmeSheetImportService.buildSectionImportances(groups,
                [[], [true], ['FALSE'], []])
        assertEquals([true, false, null], flags['1.Basics/knowledge/Basics.md'])
        try {
            ReadmeSheetImportService.buildSectionImportances(groups,
                    [[], ['yes'], [], []])
            fail('Unexpected isImportant accepted')
        } catch (GradleException expected) {
            assertTrue(expected.message.contains('AB21'))
        }
    }

    @Test
    void requiresA20AndEveryParentFilenameBeforeReadingKnowledge() {
        assertEquals('SKIPPED_EMPTY_MENU', ReadmeSheetImportService.parseMenu([
                ['Menu Cấp 1', 'Menu Cấp 2'], []
        ]).status)
        assertEquals('SKIPPED_NO_MENU_SCHEMA', ReadmeSheetImportService.parseMenu([]).status)
        Map missing = ReadmeSheetImportService.parseMenu([
                ['Menu Cấp 1', 'Menu Cấp 2'],
                ['Tư duy OOP', 'OOPBasic.md'],
                ['', 'OOP là gì?'],
                ['Lớp', ''],
                ['', 'Class là gì?']
        ])
        assertEquals('SKIPPED_MISSING_FILENAME', missing.status)
        assertEquals([22], missing.missingRows)
        assertEquals(2, missing.groups.size())
        assertEquals(2, missing.lessons)
        assertEquals('SKIPPED_MISSING_FILENAME', ReadmeSheetImportService.parseMenu([
                ['Menu Cấp 1', 'Menu Cấp 2'],
                ['A', ''], ['', 'Một bài'],
                ['B', 'invalid-file-name.md'], ['', 'Hai bài']
        ]).status)

        expectFailure {
            ReadmeSheetImportService.parseMenu([
                    ['Menu Cấp 1', 'Menu Cấp 2'],
                    ['A', 'Class.md'], ['', 'One'],
                    ['B', 'class.md'], ['', 'Two']
            ])
        }
        expectFailure {
            ReadmeSheetImportService.parseMenu([
                    ['Menu Cấp 1', 'Menu Cấp 2'],
                    ['A', '../Injected.md'], ['', 'One']
            ])
        }
        expectFailure {
            ReadmeSheetImportService.parseMenu([
                    ['Menu Cấp 1', 'Menu Cấp 2'],
                    ['A', 'HTTPServer.md'], ['', 'One'],
                    ['B', 'HttpServer.md'], ['', 'Two']
            ])
        }
    }

    @Test
    void generatesPascalCaseAnchorsAndKeepsEmptyAnswers() {
        Map menu = ReadmeSheetImportService.parseMenu([
                ['Menu Cấp 1', 'Menu Cấp 2'],
                ['Tư duy OOP', 'OOPBasic.md'],
                ['', 'OOP là gì?'],
                ['', 'Đối tượng là gì?'],
                ['', 'Chưa có bài'],
                ['Lớp', 'Class.md'],
                ['', 'Cấu trúc lớp']
        ])
        assertEquals('READY', menu.status)
        // Knowledge rows begin at row 20; the first/parent rows are empty.
        String exactAnswer = '# OOP là gì?\n\nNội dung ở D21.\n\n\n```md\n## <a id="fake">Demo anchor</a>\n```'
        List<List<Object>> knowledge = [
                [], ['# OOP là gì?\n\nNội', ' dung ở D21.\n\n\n```md\n## <a id="fake">Demo anchor</a>\n```'],
                ['**Object** là gì?'], [],
                [], ['Ví dụ Java']
        ]
        Map<String, String> docs = ReadmeSheetImportService.buildDocuments(
                menu.groups as List<Map<String, Object>>, knowledge
        )
        assertEquals(['1.OOPBasic/knowledge/OOPBasic.md', '2.Class/knowledge/Class.md'], docs.keySet().toList())
        String oop = docs['1.OOPBasic/knowledge/OOPBasic.md']
        assertTrue(oop.contains('id="oop-basic-001"'))
        assertTrue(oop.contains('id="oop-basic-002"'))
        assertTrue(oop.contains('id="oop-basic-003"'))
        assertTrue(oop.contains(exactAnswer))
        assertEquals(1, oop.count('id="oop-basic-001"'))
        assertTrue(oop.contains('<summary>Xem chi tiết</summary>'))
        assertTrue(oop.contains('## Menu'))
        assertTrue(oop.contains('- [Chưa có bài](#oop-basic-003)'))
        assertTrue(docs['2.Class/knowledge/Class.md'].contains('id="class-001"'))
        assertEquals(docs, ReadmeSheetImportService.buildDocuments(
                menu.groups as List<Map<String, Object>>, knowledge
        ))

        assertEquals('http-server', ReadmeSheetImportService.slugFromStem('HTTPServer'))
        assertEquals('xml-http-request', ReadmeSheetImportService.slugFromStem('XMLHttpRequest'))
        assertEquals('class-and-object', ReadmeSheetImportService.slugFromStem('ClassAndObject'))
    }

    @Test
    void acceptsJavaGenericSyntaxInTitlesAndEscapesInjectedHtml() {
        Map menu = ReadmeSheetImportService.parseMenu([
                ['Menu Cấp 1', 'Menu Cấp 2'],
                ['API <T> & Java', 'GenericAPI.md'],
                ['', 'Diamond <> và Map<K,List<V>>'],
                ['', 'List<String>[] và <script>alert(1)</script>']
        ])
        assertEquals('READY', menu.status)
        Map<String, String> docs = ReadmeSheetImportService.buildDocuments(
                menu.groups as List<Map<String, Object>>,
                [[], ['Nội dung một'], ['Nội dung hai']]
        )
        String result = docs['1.GenericAPI/knowledge/GenericAPI.md']
        assertTrue(result.contains('# API &lt;T&gt; &amp; Java'))
        assertTrue(result.contains('Diamond &lt;&gt; và Map&lt;K,List&lt;V&gt;&gt;'))
        assertTrue(result.contains('List&lt;String&gt;&#91;&#93;'))
        assertTrue(result.contains('&lt;script&gt;alert(1)&lt;/script&gt;'))
        assertFalse(result.contains('<script>'))
        assertTrue(result.contains('id="generic-api-001"'))
        assertTrue(result.contains('id="generic-api-002"'))
    }

    @Test
    void rejectsNonContiguousKnowledgeFragments() {
        Map menu = ReadmeSheetImportService.parseMenu([
                ['Menu Cấp 1', 'Menu Cấp 2'],
                ['Lớp', 'Class.md'], ['', 'Một bài']
        ])
        expectFailure {
            ReadmeSheetImportService.buildDocuments(
                    menu.groups as List<Map<String, Object>>,
                    [[], ['C', '', 'E']]
            )
        }
    }

    @Test
    void preservesTwelfthKnowledgeCellNWithoutTruncation() {
        Map menu = ReadmeSheetImportService.parseMenu([
                ['Menu Cấp 1', 'Menu Cấp 2'],
                ['Đóng gói', 'Encapsulation.md'],
                ['', 'Đóng gói có tác dụng gì?']
        ])
        List<String> chunks = (0..<12).collect { int col ->
            "[SECTION-${col}]-" + ('đ' * (col == 11 ? 19 : 2))
        }
        Map<String, String> result = ReadmeSheetImportService.buildDocuments(
                menu.groups as List<Map<String, Object>>,
                [[], chunks]
        )
        String readme = result['1.Encapsulation/knowledge/Encapsulation.md']
        assertNotNull(readme)
        assertTrue(readme.contains(chunks.join('')))
        assertTrue(readme.contains(chunks.last()))

        // Missing M but present N must fail rather than silently skipping N.
        List<String> broken = new ArrayList<>(chunks)
        broken[10] = ''
        expectFailure {
            ReadmeSheetImportService.buildDocuments(
                    menu.groups as List<Map<String, Object>>, [[], broken]
            )
        }

        // C:N is exactly twelve columns; a 13th fragment must not spill into O.
        expectFailure {
            ReadmeSheetImportService.buildDocuments(
                    menu.groups as List<Map<String, Object>>,
                    [[], chunks + ['must-not-overlap-difficulty-O']]
            )
        }
    }

    @Test
    void knowledgeOnlySnapshotPreservesChapterSiblingsAndReplacesOnlyKnowledge() {
        File root = temporary.newFolder('repo')
        File menu = ReadmeSheetImportService.resolveMenuDirectory(root, 'module/example')
        File legacy = new File(menu, '1.Legacy/knowledge/Legacy.md')
        assertTrue(legacy.parentFile.mkdirs())
        legacy.setText('Old README content', 'UTF-8')
        File nested = new File(menu, '1.Legacy/knowledge/subfolder/Orphan.md')
        assertTrue(nested.parentFile.mkdirs())
        nested.setText('stale nested content', 'UTF-8')
        File unrelated = new File(menu, '1.Legacy/chapter-links.md')
        unrelated.setText('stale temporary content', 'UTF-8')
        File video = new File(menu, '1.Legacy/video-script.yml')
        video.setText('human-owned Video stays', 'UTF-8')
        Map<String, String> next = [
                '1.OOPBasic/knowledge/OOPBasic.md': '# Tư duy OOP\n\n## <a id="oop-basic-001">Lesson</a>\n'
        ]
        assertEquals('DRY_RUN', ReadmeSheetImportService.syncDocuments(root, menu, next, false).status)
        assertTrue(legacy.isFile())
        assertTrue(nested.isFile())
        assertTrue(unrelated.isFile())
        assertFalse(new File(root, '.gradle/sheet-readme-backups').exists())

        Map result = ReadmeSheetImportService.syncDocuments(root, menu, next, true)
        assertEquals('UPDATED', result.status)
        assertEquals(2, result.clearedOldFiles)
        assertFalse(legacy.exists())
        assertFalse(nested.exists())
        assertTrue(unrelated.exists())
        assertEquals('stale temporary content', unrelated.getText('UTF-8'))
        assertEquals('human-owned Video stays', video.getText('UTF-8'))
        assertTrue(new File(menu, '1.OOPBasic/knowledge/OOPBasic.md').isFile())
        assertFalse(new File(root, '.gradle/sheet-readme-backups').exists())
        assertEquals('UNCHANGED', ReadmeSheetImportService.syncDocuments(root, menu, next, true).status)
        assertEquals('# Tư duy OOP\n\n## <a id="oop-basic-001">Lesson</a>\n',
                new File(menu, '1.OOPBasic/knowledge/OOPBasic.md').getText('UTF-8'))

        Map<String, String> revised = ['1.Class/knowledge/Class.md': 'New lesson']
        Map updated = ReadmeSheetImportService.syncDocuments(root, menu, revised, true)
        assertEquals('UPDATED', updated.status)
        assertEquals(1, updated.clearedOldFiles)
        assertTrue(new File(menu, '1.Class/knowledge/Class.md').isFile())
        assertTrue(unrelated.exists())
        assertTrue(video.exists())
        assertFalse(new File(root, '.gradle/sheet-readme-backups').exists())
    }

    @Test
    void emptySheetSnapshotClearsOnlyKnowledge() {
        File root = temporary.newFolder('empty-snapshot')
        File menu = ReadmeSheetImportService.resolveMenuDirectory(root, 'module/example')
        File old = new File(menu, '1.Old/knowledge/Old.md')
        assertTrue(old.parentFile.mkdirs())
        old.setText('Original remains intact', 'UTF-8')
        File sibling = new File(menu, '1.Old/chapter-links.md')
        sibling.setText('keep this', 'UTF-8')
        assertEquals('DRY_RUN',
                ReadmeSheetImportService.syncDocuments(root, menu, [:], false).status)
        assertTrue(old.isFile())
        Map cleared = ReadmeSheetImportService.syncDocuments(root, menu, [:], true)
        assertEquals('CLEARED_EMPTY', cleared.status)
        assertEquals(1, cleared.clearedOldFiles)
        assertFalse(old.exists())
        assertTrue(sibling.isFile())
        assertEquals('keep this', sibling.getText('UTF-8'))
        assertFalse(new File(root, '.gradle/sheet-readme-backups').exists())
    }

    @Test
    void sheetReadmeReplacesKnowledgeAndMetadataIncludingStaleChaptersAndLegacy() {
        File root = temporary.newFolder('fresh-readme-metadata')
        File menu = ReadmeSheetImportService.resolveMenuDirectory(root, 'module/example')
        File oldKnowledge = new File(menu, '2.Removed/knowledge/Old.md')
        assertTrue(oldKnowledge.parentFile.mkdirs())
        oldKnowledge.setText('OLD KNOWLEDGE', 'UTF-8')
        File oldMetadata = new File(menu, '2.Removed/metadata/knowledge-metadata.yml')
        assertTrue(oldMetadata.parentFile.mkdirs())
        oldMetadata.setText('sections: {stale: {reviewed: true}}', 'UTF-8')
        File oldNewChapterMetadata = new File(menu, '1.Basic/metadata/obsolete.yml')
        assertTrue(oldNewChapterMetadata.parentFile.mkdirs())
        oldNewChapterMetadata.setText('OLD EXTRA', 'UTF-8')
        File interview = new File(menu, '2.Removed/interview/question.yml')
        assertTrue(interview.parentFile.mkdirs())
        interview.setText('keep interview', 'UTF-8')
        File oldLegacy = new File(root, 'module/example/content/readme/metadata/vi/knowledge-metadata.yml')
        assertTrue(oldLegacy.parentFile.mkdirs())
        oldLegacy.setText('OLD LEGACY METADATA', 'UTF-8')
        Map nextKnowledge = ['1.Basic/knowledge/Basic.md':
                '# Basics\n\n## <a id="basic-001">First</a>\n']
        def metadata = new ReadmeMetadataSyncService(Logging.getLogger(ReadmeSheetImportServiceTest))
        String prepared = metadata.prepareImportedMetadata(new File(root, 'module/example'),
                nextKnowledge, ['1.Basic/knowledge/Basic.md': ['INTERMEDIATE']])
        Map nextMetadata = metadata.importedChapterMetadataFiles(prepared)
        assertEquals('DRY_RUN', ReadmeSheetImportService.syncDocuments(
                root, menu, nextKnowledge, false, nextMetadata).status)
        assertTrue(oldKnowledge.exists())
        assertEquals('UPDATED', ReadmeSheetImportService.syncDocuments(
                root, menu, nextKnowledge, true, nextMetadata).status)
        assertFalse(oldKnowledge.exists())
        assertFalse(oldMetadata.exists())
        assertFalse(oldNewChapterMetadata.exists())
        assertFalse(oldLegacy.exists())
        assertEquals('keep interview', interview.getText('UTF-8'))
        assertEquals(nextKnowledge['1.Basic/knowledge/Basic.md'],
                new File(menu, '1.Basic/knowledge/Basic.md').getText('UTF-8'))
        Map fresh = new Yaml().load(new File(menu,
                '1.Basic/metadata/knowledge-metadata.yml').getText('UTF-8')) as Map
        assertEquals(['basic-001'] as Set, fresh.sections.keySet())
        assertEquals('INTERMEDIATE', fresh.sections['basic-001'].difficulty)
        assertEquals(false, fresh.sections['basic-001'].reviewed)
    }

    @Test
    void refusesUnsafeMenuDeletionTarget() {
        File root = temporary.newFolder('unsafe-delete')
        File unrelated = new File(root, 'not-a-menu')
        assertTrue(unrelated.mkdirs())
        File data = new File(unrelated, 'important.txt')
        data.setText('keep', 'UTF-8')
        expectFailure {
            ReadmeSheetImportService.syncDocuments(
                root, unrelated, ['1.Class/knowledge/Class.md': 'new'], true
            )
        }
        assertEquals('keep', data.getText('UTF-8'))
    }

    @Test
    void newInternalMenuRendererMatchesLegacyFileGenerator() {
        File root = temporary.newFolder('renderer')
        File readme = new File(root, 'content/readme/docs/vi/menu/1.Class/Class.md')
        assertTrue(readme.parentFile.mkdirs())
        String raw = '# Lớp\n\n## <a id="class-001">Lớp là gì?</a>\n\nNội dung\n'
        readme.setText(raw, 'UTF-8')
        GenerateInternalReadmeMenuService service = new GenerateInternalReadmeMenuService()
        String rendered = service.render(raw, 'vi')
        service.generate(readme)
        assertEquals(rendered, readme.getText('UTF-8'))
        assertEquals(rendered, service.render(rendered, 'vi'))
    }

    private static void expectFailure(Closure action) {
        try {
            action.call()
            fail('Expected GradleException')
        }
        catch (GradleException expected) {
            // Invalid data must not be imported.
        }
    }
}
