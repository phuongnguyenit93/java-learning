package com.example.learning.task.sheets.service

import org.gradle.api.GradleException
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.yaml.snakeyaml.Yaml

import static org.junit.Assert.*

class PracticeSheetImportServiceTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder()
    static final String SCENARIO = '''scenarios:
  - id: scenario-01
    type: ENGINEERING
    title: Phân tích hotfix
    sourceKind: REALISTIC_SIMULATION
    context: |-
      Một bản production cần vá lỗi.
    problem: Lịch phát hành bị xung đột.
    analysis:
      possibleCauses: Lịch sử nhánh bị tách.
      application: Xác định đúng release branch.
      tradeoffs: Cân nhắc merge và cherry-pick.
    exercise:
      type: WORKFLOW_DESIGN
      requirements: [Thiết kế hotfix]
      deliverables: [Sơ đồ commit]
      acceptanceCriteria: [Không phát hành chức năng chưa hoàn tất]
      hints: []
      referenceSolution: |-
        Tạo nhánh từ bản release đang chạy, rồi đưa fix trở lại main.
'''

    private static List<List<Object>> rows(int count = 3) {
        List<List<Object>> rows = (0..<count).collect { [] as List<Object> }
        rows[0] = ['Real-world Practice']
        rows[1] = ['Practice YAML', 'Practice YAML', 'Practice YAML', 'Practice YAML']
        return rows
    }

    @Test
    void importsOneVerbatimFileForWholeChapter() {
        List<List<Object>> cells = rows()
        cells[2] = [SCENARIO]
        Map data = PracticeSheetImportService.collect(
                [[parentRow: 20, filename: 'Basic.md']], cells)
        assertEquals(1, data.chapterCount)
        assertEquals(1, data.scenarioCount)
        assertEquals(['scenarios.yml'] as Set, data.files['1.Basic'].keySet() as Set)
        assertEquals(SCENARIO, data.files['1.Basic']['scenarios.yml'])
        assertEquals(1, (new Yaml().load(data.files['1.Basic']['scenarios.yml']).scenarios as List).size())
    }

    @Test
    void joinsMultipleFragmentsWithoutAddingSeparatorOrReformatting() {
        String longer = SCENARIO.replace('Một bản production cần vá lỗi.',
                'Một bản production cần vá lỗi.\n' + ('      Diễn giải nhiều ký tự có dấu.\n' * 1100))
        int split = longer.lastIndexOf('\n', 29900) + 1
        assertTrue(split > 0)
        List<List<Object>> cells = rows()
        cells[2] = [longer.substring(0, split), longer.substring(split)]
        Map data = PracticeSheetImportService.collect(
                [[parentRow: 20, filename: 'Basic.md']], cells)
        assertEquals(longer, data.files['1.Basic']['scenarios.yml'])
    }

    @Test
    void rejectsDraftDuplicateIdsAndOrphanRowsBeforeImport() {
        List<List<Object>> cells = rows()
        cells[2] = ['''scenarios:
  - id: scenario-01
    type: ENGINEERING
    title: Draft
    sourceKind: REALISTIC_SIMULATION
    exercise: {type: WORKFLOW_DESIGN}
''']
        expectReject(cells, 'schema')
        cells[2] = [SCENARIO.replace('  - id: scenario-01', '  - id: scenario-01') +
                SCENARIO.substring('scenarios:\n'.length())]
        expectReject(cells, 'Duplicate')
        cells[2] = [SCENARIO]
        cells.add(['Orphan'])
        expectReject(cells, 'Orphan')
    }

    private static void expectReject(List<List<Object>> cells, String expected) {
        try {
            PracticeSheetImportService.collect(
                    [[parentRow: 20, filename: 'Basic.md']], cells)
            fail('Expected invalid Practice content')
        } catch (GradleException error) {
            assertTrue(error.message, error.message.toLowerCase().contains(expected.toLowerCase()) ||
                    (expected == 'schema' && error.message.contains('Practice')))
        }
    }

    @Test
    void enRoundtripKeepsOneFileAndGreedyFragments() {
        String s = SCENARIO
        assertEquals([s, '', '', ''], ModuleContentExcelSyncService.splitPractice(s))
        String longer = s.replace('Một bản production cần vá lỗi.',
                'Một bản production cần vá lỗi.\n' + ('      Nội dung kiểm thử có dấu.\n' * 1250))
        List<String> chunks = ModuleContentExcelSyncService.splitPractice(longer)
        assertEquals(longer, chunks.join(''))
        assertTrue(chunks[0].length() > 29000 && chunks[0].length() <= 30000)
        assertTrue(chunks[0].endsWith('\n'))
        assertTrue(chunks[1].length() > 0)
        assertEquals(['',''], chunks.subList(2,4))
    }

    @Test
    void applyAtomicallyReplacesLegacyPerScenarioFilesWithSingleScenariosYaml() {
        File root = temp.newFolder('repo')
        File chapter = new File(root, 'module/demo/content/readme/docs/vi/menu/1.Basic')
        File practice = new File(chapter, 'practice')
        assertTrue(practice.mkdirs())
        new File(practice, 'scenario-01.yml').setText('legacy', 'UTF-8')
        Map expected = ['scenarios.yml': SCENARIO]
        Map dry = SheetSnapshotReplaceService.replaceAll(root, [(practice): expected], false)
        assertEquals('DRY_RUN', dry.status)
        assertTrue(new File(practice, 'scenario-01.yml').exists())
        Map applied = SheetSnapshotReplaceService.replaceAll(root, [(practice): expected], true)
        assertEquals('UPDATED', applied.status)
        assertFalse(new File(practice, 'scenario-01.yml').exists())
        assertEquals(SCENARIO, new File(practice, 'scenarios.yml').getText('UTF-8'))
    }
}
