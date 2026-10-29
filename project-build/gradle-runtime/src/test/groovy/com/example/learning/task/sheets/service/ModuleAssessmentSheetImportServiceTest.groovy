package com.example.learning.task.sheets.service

import org.gradle.api.GradleException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.yaml.snakeyaml.Yaml

import static org.junit.Assert.*

class ModuleAssessmentSheetImportServiceTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder()

    private static final Set<String> IDS = ['oop-basic-001', 'class-design-001'] as Set

    private static Map<String, String> sources() {
        return [
                interview: '''interviewQuestion:
  - groupID: 1
    groupName: Basic
    questions:
      - question: First question?
        knowledge: First background.
        readmeRelated:
          anchorList: [oop-basic-001, class-design-001]
      - question: Second question?
        knowledge: Second background.
        readmeRelated:
          anchorList: []
''',
                casestudy: '''caseStudy:
  - groupID: 1
    groupName: Practice
    cases:
      - situation: Broken payment model.
        knowledge: Invariants.
        resolve: Guard state.
        readmeRelated:
          anchorList: [oop-basic-001]
''',
                quiz: '''quiz:
  - groupID: 1
    groupName: Basics
    questions:
      - question: Correct principle?
        readmeRelated:
          anchorList: [class-design-001]
        answers:
          - {id: A, answer: A text, explanation: A wrong}
          - {id: B, answer: B text, explanation: B correct}
          - {id: C, answer: C text, explanation: C wrong}
          - {id: D, answer: D text, explanation: D wrong}
        correctAnswerId: B
'''
        ]
    }

    private static Map<String, List<List<Object>>> fragments() {
        return [
                interview: [['''groupID: 1
answers:
  - questionIndex: 2
    answer: Answer TWO.
  - questionIndex: 1
    answer: Answer ONE.
'''], [], []],
                casestudy: [['''groupID: 1
codes:
  - caseIndex: 1
    code: |-
      ```java
      record Money(int cents) {}
      ```
'''], [], []]
        ]
    }

    @Test
    void mergesByGroupAndOneBasedIndexWhileKeepingModuleWideGroupsAndLinks() {
        Map<String, String> result = ModuleAssessmentSheetImportService.assemble(sources(), IDS, fragments())
        assertEquals(['interview', 'casestudy', 'quiz'] as Set, result.keySet() as Set)
        Map interview = new Yaml().load(result.interview) as Map
        List questions = interview.interviewQuestion[0].questions
        assertEquals('Answer ONE.', questions[0].answer)
        assertEquals('Answer TWO.', questions[1].answer)
        assertEquals(['oop-basic-001', 'class-design-001'], questions[0].readmeRelated.anchorList)
        assertEquals([], questions[1].readmeRelated.anchorList)
        assertEquals('First background.', questions[0].knowledge)
        Map caseStudy = new Yaml().load(result.casestudy) as Map
        assertTrue(caseStudy.caseStudy[0].cases[0].code.contains('record Money'))
        Map quiz = new Yaml().load(result.quiz) as Map
        assertEquals('B', quiz.quiz[0].questions[0].correctAnswerId)
        assertEquals(4, quiz.quiz[0].questions[0].answers.size())
        assertFalse(quiz.quiz[0].questions[0].containsKey('id'))
    }

    @Test
    void refusesMissingDuplicateOrWrongGroupSupplementsAndUnknownAnchors() {
        Map broken = fragments()
        broken.interview = [['''groupID: 1
answers:
  - questionIndex: 1
    answer: First.
  - questionIndex: 1
    answer: Duplicate.
'''], [], []]
        expectFailure { ModuleAssessmentSheetImportService.assemble(sources(), IDS, broken) }

        broken = fragments()
        broken.casestudy = [['''groupID: 2
codes:
  - caseIndex: 1
    code: Wrong group.
'''], [], []]
        expectFailure { ModuleAssessmentSheetImportService.assemble(sources(), IDS, broken) }

        broken = fragments()
        broken.interview = [['''groupID: 1
answers:
  - questionIndex: 1
    answer: Missing second.
'''], [], []]
        expectFailure { ModuleAssessmentSheetImportService.assemble(sources(), IDS, broken) }

        expectFailure { ModuleAssessmentSheetImportService.assemble(sources(), ['other-001'] as Set, fragments()) }
        Map invalid = sources()
        invalid.quiz = invalid.quiz.replace('correctAnswerId: B', 'correctAnswerId: Z')
        expectFailure { ModuleAssessmentSheetImportService.assemble(invalid, IDS, fragments()) }
    }

    @Test
    void rejectsGapsOversizedCellsFormulasAndNonMaxFill() {
        expectFailure { ModuleAssessmentSheetImportService.concatenate([['a', '', 'b']], 1, 3, 'test') }
        expectFailure { ModuleAssessmentSheetImportService.concatenate([['a', 'b']], 1, 2, 'test') }
        expectFailure { ModuleAssessmentSheetImportService.concatenate([['=1+1']], 1, 1, 'test') }
        expectFailure { ModuleAssessmentSheetImportService.concatenate([['x' * 30001]], 1, 1, 'test') }
        assertEquals('a' * 30000 + 'b', ModuleAssessmentSheetImportService.concatenate(
                [['a' * 30000, 'b']], 1, 2, 'max-fill'))
        // A full UTF-16 cell may be shorter than 30,000 Unicode code points.
        assertEquals(('🌟' * 16383) + '🌟', ModuleAssessmentSheetImportService.concatenate(
                [['🌟' * 16383, '🌟']], 1, 2, 'utf16-max-fill'))
    }

    @Test
    void atomicallyCleansAllThreeViDirectoriesButLeavesEnglishAndKnowledgeUntouched() {
        File root = temporary.newFolder('repo')
        File module = new File(root, 'module/example')
        Map<String, String> rendered = ModuleAssessmentSheetImportService.assemble(sources(), IDS, fragments())
        Map<File, Map<String, String>> targets = new LinkedHashMap<>()
        rendered.each { String type, String yaml ->
            File dir = new File(module, "content/${type}/vi")
            targets[dir] = [(type + '.yml'): yaml]
            File old = new File(dir, 'legacy/old.yml')
            assertTrue(old.parentFile.mkdirs())
            old.setText('legacy', 'UTF-8')
            File english = new File(module, "content/${type}/en/english.yml")
            assertTrue(english.parentFile.mkdirs())
            english.setText('keep', 'UTF-8')
        }
        File knowledge = new File(module, 'content/readme/docs/vi/menu/1.Basic/knowledge/Basic.md')
        assertTrue(knowledge.parentFile.mkdirs())
        knowledge.setText('keep', 'UTF-8')

        assertEquals('DRY_RUN', SheetSnapshotReplaceService.replaceAll(root, targets, false).status)
        targets.each { File dir, Map expected -> assertTrue(new File(dir, 'legacy/old.yml').isFile()) }
        Map applied = SheetSnapshotReplaceService.replaceAll(root, targets, true)
        assertEquals('UPDATED', applied.status)
        assertEquals(3, applied.clearedOldFiles)
        targets.each { File dir, Map expected ->
            assertEquals(expected, SheetSnapshotReplaceService.readTree(dir))
            assertFalse(new File(dir, 'legacy').exists())
            assertEquals('keep', new File(dir.parentFile, 'en/english.yml').getText('UTF-8'))
        }
        assertEquals('keep', knowledge.getText('UTF-8'))
        assertEquals('UNCHANGED', SheetSnapshotReplaceService.replaceAll(root, targets, true).status)
    }

    private static void expectFailure(Closure action) {
        try {
            action.call()
            fail('Expected invalid assessment input to fail before mutation')
        } catch (GradleException expected) { assertNotNull(expected.message) }
    }
}
