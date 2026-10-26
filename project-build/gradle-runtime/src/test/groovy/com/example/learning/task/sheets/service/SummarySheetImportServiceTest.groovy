package com.example.learning.task.sheets.service

import org.gradle.api.GradleException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.yaml.snakeyaml.Yaml

import static org.junit.Assert.*

class SummarySheetImportServiceTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder()

    private static final List<Map> GROUPS = [
            [filename:'OOPBasic.md', parentRow:20,
             children:[[row:21,title:'Là gì?'],[row:22,title:'Class & Object']]],
            [filename:'Encapsulation.md', parentRow:23,
             children:[[row:24,title:'Encapsulation']]]
    ]

    private static List<List<Object>> ac(Map<Integer, String> values) {
        List<List<Object>> rows = (18..26).collect { int row ->
            values.containsKey(row) ? [values[row]] : []
        }
        rows[0] = ['Summary']
        rows[1] = ['Summary YAML']
        return rows
    }

    private static String section(String title, List<String> ids) {
        List<Map> steps = ids.withIndex().collect { String id, int index ->
            [id: id, order: index + 1, title: 'Lesson ' + (index + 1),
             description: 'Describe ' + id,
             transition: index + 1 < ids.size()
                     ? [to: ids[index + 1], description: '**Next lesson**'] : null]
        }
        String header = '| Tiêu chí | ' + (1..ids.size()).collect { 'Bài ' + it }.join(' | ') + ' |'
        String separator = '| --- | ' + ids.collect { '---' }.join(' | ') + ' |'
        String rows = ['Kỹ năng','Ví dụ','Phạm vi','Kiểm tra'].collect { String type ->
            '| ' + type + ' | ' + ids.collect { 'Nội dung' }.join(' | ') + ' |'
        }.join('\n')
        new Yaml().dump([version: 1,type:'LEARNING_ROADMAP',title:title,
                roadmapHeading:'Mạch liên kết bài học',roadmap:steps,
                comparisonMarkdown:'## 2. Bảng đối chiếu các bài học\n\n' + header +
                        '\n' + separator + '\n' + rows])
    }

    @Test
    void createsSectionAndSummariesYamlAndCleansStaleChapterWithoutTouchingKnowledge() {
        String originalYaml = section('OOP summary', ['oop-basic-001','oop-basic-002'])
        Map all = SummarySheetImportService.prepare(GROUPS, ac([
                20:originalYaml, 21:'## What is OOP?\n', 22:'**Objects**\n',
                23:section('Encapsulation', ['encapsulation-001']), 24:'## Definition\n'
        ]))
        assertEquals(['section.yml','summaries.yml'] as Set, all['1.OOPBasic'].keySet())
        assertEquals(originalYaml, all['1.OOPBasic']['section.yml'])
        Map chapterYaml = new Yaml().load(all['1.OOPBasic']['section.yml']) as Map
        assertEquals('LEARNING_ROADMAP', chapterYaml.type)
        assertEquals(['oop-basic-001', 'oop-basic-002'], chapterYaml.roadmap*.id)
        assertEquals('oop-basic-002', chapterYaml.roadmap[0].transition.to)
        assertNull(chapterYaml.roadmap[1].transition)
        assertTrue(chapterYaml.comparisonMarkdown.contains('| Kỹ năng | Nội dung | Nội dung |'))
        Map yaml = new Yaml().load(all['1.OOPBasic']['summaries.yml']) as Map
        assertEquals(['oop-basic-001','oop-basic-002'], yaml.summaries*.id)
        assertEquals(['Là gì?','Class & Object'], yaml.summaries*.title)
        assertEquals('**Objects**\n', yaml.summaries[1].summary)

        File root = tmp.newFolder('repo')
        File menu = new File(root, 'module/demo/content/readme/docs/vi/menu')
        Map<File, Map<String, String>> targets = [:]
        all.each { String chapter, Map<String,String> output ->
            File home = new File(menu, chapter)
            File knowledge = new File(home, 'knowledge/Existing.md')
            assertTrue(knowledge.parentFile.mkdirs())
            knowledge.setText('keep', 'UTF-8')
            File owned = new File(home, 'summary')
            assertTrue(owned.mkdirs())
            new File(owned, 'old.md').setText('old', 'UTF-8')
            targets[owned] = output
        }
        File stale = new File(menu, '3.Removed/summary/old.md')
        assertTrue(stale.parentFile.mkdirs())
        stale.setText('stale', 'UTF-8')
        targets[stale.parentFile] = [:]
        assertEquals('DRY_RUN', SheetSnapshotReplaceService.replaceAll(root, targets, false).status)
        assertTrue(stale.exists())
        assertEquals('UPDATED', SheetSnapshotReplaceService.replaceAll(root, targets, true).status)
        assertFalse(stale.exists())
        assertEquals(['section.yml','summaries.yml'] as Set,
                new File(menu, '1.OOPBasic/summary').list().toList().toSet())
        assertEquals('keep', new File(menu, '1.OOPBasic/knowledge/Existing.md').getText('UTF-8'))
    }

    @Test
    void blocksPartialAndOrphanAcButAcceptsLegacyHeaders() {
        try {
            SummarySheetImportService.prepare(GROUPS, ac([20:'Only section']))
            fail('Partial Summary must not be imported')
        } catch (GradleException expected) {
            assertTrue(expected.message.contains('Incomplete Summary'))
        }
        try {
            SummarySheetImportService.prepare(GROUPS, ac([25:'orphan']))
            fail('Orphan Summary accepted')
        } catch (GradleException expected) {
            assertTrue(expected.message.contains('orphan'))
        }
        List<List<Object>> old = ac([:])
        old[0] = ['Learning Objectives']
        old[1] = ['Learning Objectives YAML']
        assertTrue(SummarySheetImportService.prepare(GROUPS, old).isEmpty())
    }

    @Test
    void refusesMarkdownAndWrongRoadmapIdsBeforeSnapshotMutation() {
        try {
            SummarySheetImportService.prepare(GROUPS, ac([
                    20:'# OOP Summary\n\n## 1. Mạch liên kết bài học',
                    21:'one', 22:'two',
                    23:section('Encapsulation',['encapsulation-001']),24:'one'
            ]))
            fail('Unstructured chapter content was accepted.')
        } catch (GradleException expected) {
            assertTrue(expected.message.contains('section.yml') ||
                    expected.message.contains('Invalid'))
        }
        try {
            SummarySheetImportService.prepare(GROUPS, ac([
                    20:section('OOP summary',['wrong-001','wrong-002']),
                    21:'one',22:'two',23:section('Encapsulation',['encapsulation-001']),24:'one'
            ]))
            fail('Incorrect Knowledge IDs were accepted.')
        } catch (GradleException expected) {
            assertTrue(expected.message.contains('Invalid roadmap lesson'))
        }
    }
}
