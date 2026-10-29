package com.example.learning.task.sheets.service

import org.gradle.api.GradleException
import org.junit.Test

import static org.junit.Assert.*

class MenuLessonIdServiceTest {
    private static List<List<Object>> sheet(List<List<Object>> values) {
        return [['Menu Cấp 1', 'Menu Cấp 2']] + values
    }

    @Test
    void fillsOnlyMissingChildrenAndLeavesParentAndExistingIdsUntouched() {
        Map menu = ReadmeSheetImportService.parseMenu(sheet([
                ['Tư duy OOP', 'OOPBasic.md'],
                ['', 'First'], ['', 'Second'], ['', 'Third'],
                ['Class', 'Class.md'], ['', 'Fourth']
        ]))
        assertEquals('READY', menu.status)
        Map<Integer, String> writes = MenuLessonIdService.plan(menu.groups, [
                ['oop-basic'], [], [], [], ['class'], []
        ])
        assertEquals([21:'oop-basic-001', 22:'oop-basic-002',
                      23:'oop-basic-003', 25:'class-001'], writes)
        assertFalse(writes.containsKey(20))
        assertFalse(writes.containsKey(24))

        Map populated = ReadmeSheetImportService.parseMenu(sheet([
                ['Tư duy OOP', 'OOPBasic.md'],
                ['oop-basic-001', 'First'], ['oop-basic-002', 'Second'],
                ['oop-basic-003', 'Third'],
                ['Class', 'Class.md'], ['class-001', 'Fourth']
        ]))
        assertEquals([:], MenuLessonIdService.plan(populated.groups, [
                ['oop-basic'], [], [], [], ['class'], []
        ]))
        MenuLessonIdService.validate(populated.groups, [
                ['oop-basic'], [], [], [], ['class'], []
        ])
    }

    @Test
    void preservesExistingIdsOnReorderAndAllocatesNewIdForInsertedLesson() {
        Map menu = ReadmeSheetImportService.parseMenu(sheet([
                ['Tư duy OOP', 'OOPBasic.md'],
                ['oop-basic-002', 'Swapped second lesson'],
                ['', 'New inserted lesson'],
                ['oop-basic-001', 'Swapped first lesson']
        ]))
        assertEquals([22:'oop-basic-003'], MenuLessonIdService.plan(
                menu.groups, [['oop-basic'], [], [], []]))
    }

    @Test
    void rejectsMismatchedBaseDuplicateAndMalformedChildBeforeWriting() {
        Map menu = ReadmeSheetImportService.parseMenu(sheet([
                ['Tư duy OOP', 'OOPBasic.md'],
                ['oop-basic-001', 'One'],
                ['oop-basic-001', 'Duplicate']
        ]))
        failure { MenuLessonIdService.plan(menu.groups, [['oop-basic'], [], []]) }
        failure { MenuLessonIdService.plan(menu.groups, [['wrong-base'], [], []]) }
        failure { MenuLessonIdService.validate(menu.groups, [['oop-basic'], [], []]) }
        Map missing = ReadmeSheetImportService.parseMenu(sheet([
                ['Tư duy OOP', 'OOPBasic.md'], ['', 'One']
        ]))
        failure { MenuLessonIdService.validate(missing.groups, [['oop-basic'], []]) }
    }

    @Test
    void materializedSummaryUsesRowIdentityNotOrdinalSuffix() {
        String authored = '''version: 1
type: LEARNING_ROADMAP
title: Chapter flow
roadmapHeading: Sequence
roadmap:
  - description: Second lesson first in the new order.
    transition: {description: Continue}
  - description: First lesson second in the new order.
    transition: null
comparisonMarkdown: |-
  ## 2. Lesson comparison

  | Criteria | Bài 1 | Bài 2 |
  | --- | --- | --- |
  | Name | Second | First |
  | Problem | A | B |
  | Value | C | D |
  | Thinking | E | F |
'''
        List<Map> lessons = [
                [id:'oop-basic-002', title:'Second'],
                [id:'oop-basic-001', title:'First']
        ]
        String full = SummarySectionYamlService.materialize(authored, lessons)
        Map section = SummarySectionYamlService.read(full,
                ['oop-basic-002','oop-basic-001'])
        assertEquals(['oop-basic-002','oop-basic-001'], section.roadmap*.id)
        assertEquals('oop-basic-001', section.roadmap[0].transition.to)
        assertEquals(['Second','First'], section.roadmap*.title)
        String reversed = SummarySectionYamlService.toAuthored(full,
                ['oop-basic-002','oop-basic-001'])
        assertFalse(reversed.contains('id: oop-basic'))
        assertEquals(section, SummarySectionYamlService.read(
                SummarySectionYamlService.materialize(reversed, lessons),
                ['oop-basic-002','oop-basic-001']))
    }

    private static void failure(Closure check) {
        try {
            check.call()
            fail('Expected invalid Sheet contract to be rejected.')
        } catch (GradleException expected) {
            assertNotNull(expected.message)
        }
    }
}
