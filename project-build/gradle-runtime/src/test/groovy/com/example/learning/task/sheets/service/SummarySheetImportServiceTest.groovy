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
        rows[1] = ['Summary Markdown']
        return rows
    }

    @Test
    void createsSectionAndSummariesYamlAndCleansStaleChapterWithoutTouchingKnowledge() {
        Map all = SummarySheetImportService.prepare(GROUPS, ac([
                20:'# OOP summary\n', 21:'## What is OOP?\n', 22:'**Objects**\n',
                23:'# Encapsulation\n', 24:'## Definition\n'
        ]))
        assertEquals(['section.md','summaries.yml'] as Set, all['1.OOPBasic'].keySet())
        assertEquals('# OOP summary\n', all['1.OOPBasic']['section.md'])
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
        assertEquals(['section.md','summaries.yml'] as Set,
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
        old[1] = ['Learning Objectives Markdown']
        assertTrue(SummarySheetImportService.prepare(GROUPS, old).isEmpty())
    }
}
