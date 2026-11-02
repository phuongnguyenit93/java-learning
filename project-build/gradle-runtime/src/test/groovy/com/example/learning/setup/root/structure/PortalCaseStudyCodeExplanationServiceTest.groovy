package com.example.learning.setup.root.structure

import com.example.learning.setup.root.structure.service.ModuleAssessmentProjectionSupport
import com.example.learning.setup.root.structure.service.PortalCaseStudyCodeExplanationService
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.yaml.snakeyaml.Yaml

import static org.junit.Assert.*

class PortalCaseStudyCodeExplanationServiceTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder()

    @Test
    void publishesOnlyMatchingGroupIndexAndAuthoredScenariosWithoutInventingResponses() {
        File module = temporary.newFolder('module')
        Map group1 = group(1, 9)
        Map group2 = group(2, 9)
        String route = '/case-studies/group/1/case/9/execute'
        write(module, 'src/main/resources/swagger/vi/api-descriptions.yml', [
                Group1Controller: ['executeCase9(String)': [methodName: 'executeCase9',
                                                             mappingPaths: [route], usage: true]],
                Group2Controller: ['executeCase9()': [methodName: 'executeCase9',
                                                     mappingPaths: ['/case-studies/group/2/case/9/execute'], usage: true]]
        ])
        write(module, 'src/main/resources/swagger/vi/api-execution.yml', [
                Group1Controller: ['executeCase9(String)': [execution: '<p>Runs the CPU workload.</p>', usage: true]],
                Group2Controller: ['executeCase9()': [execution: '<p>Description of how this API works. Replace this with HTML code.</p>']]
        ])
        write(module, 'src/main/resources/swagger/vi/api-cases.yml', [
                Group1Controller: ['executeCase9(String)': [
                        methodName: 'executeCase9', mappingPaths: [route], usage: true,
                        cases: [
                                [id: 'case_001', title: 'CPU branch', description: 'Pass mode=cpu',
                                 detailDescription: '<p>Checks stdout.</p>',
                                 input: [pathParams: [:], queryParams: [mode: 'cpu'], headers: [:], body: null],
                                 status: null, response: null,
                                 observations: [[type: 'LOG', source: 'stdout', program: 'Terminal',
                                                 expect: [keyword: 'completed', results: '<p>Line appears.</p>',
                                                          conclusion: '<p>Completed.</p>']]]],
                                [id: 'case_002', title: 'Missing parameter',
                                 description: 'Omit mode and inspect the thrown exception',
                                 input: [pathParams: [:], queryParams: [:], headers: [:], body: null]],
                                [id: 'case_003', title: 'TODO: Write a short title',
                                 description: 'TODO: Explain this case', input: [:]]
                        ]
                ]]
        ])
        File source = write(module, 'content/casestudy/vi/casestudy.yml',
                [caseStudy: [group1, group2]])
        String original = source.getText('UTF-8')
        Map<String, String> outputs = [:]
        ModuleAssessmentProjectionSupport.addOutputs(outputs, source, 'DEMO', 'casestudy', 'vi', module)
        Map first = new Yaml().load(outputs['DEMO/casestudy/vi/groups/1.yml']) as Map
        Map second = new Yaml().load(outputs['DEMO/casestudy/vi/groups/2.yml']) as Map
        Map explanation = first.cases[8].codeExplanation
        assertEquals('<p>Runs the CPU workload.</p>', explanation.howItWorksHtml)
        assertEquals(['case_001', 'case_002'], explanation.situations*.id)
        assertEquals('cpu', explanation.situations[0].input.queryParams.mode)
        assertEquals('completed', explanation.situations[0].observations[0].expect.keyword)
        assertFalse(explanation.situations[0].containsKey('status'))
        assertFalse(explanation.situations[0].containsKey('response'))
        assertFalse(first.cases[0].containsKey('codeExplanation'))
        assertFalse(second.cases[8].containsKey('codeExplanation'))
        assertEquals(original, source.getText('UTF-8'))
        assertEquals(original, new File(module, 'content/casestudy/vi/casestudy.yml').getText('UTF-8'))
        // Published documents are deterministic across repeated projections.
        Map<String, String> rerun = [:]
        ModuleAssessmentProjectionSupport.addOutputs(rerun, source, 'DEMO', 'casestudy', 'vi', module)
        assertEquals(outputs, rerun)
    }

    @Test
    void preservesOptionalHowItWorksAndSkipsGeneratedPlaceholdersAndMissingFiles() {
        File module = temporary.newFolder('module')
        Map group = group(1, 2)
        Map explanations = PortalCaseStudyCodeExplanationService.load(module, 'vi', [group])
        assertTrue(explanations.isEmpty())
        write(module, 'src/main/resources/swagger/vi/api-execution.yml', [
                Group1Controller: [
                        'executeCase1()': [execution: '<p>Mô tả cách hoạt động của API này. Thay đoạn này bằng HTML code.</p>'],
                        'executeCase2()': [execution: '<p>Actual execution.</p>']
                ]
        ])
        write(module, 'src/main/resources/swagger/vi/api-descriptions.yml', [
                Group1Controller: [
                        'executeCase1()': [methodName: 'executeCase1', usage: true,
                                           mappingPaths: ['/case-studies/group/1/case/1/execute']],
                        'executeCase2()': [methodName: 'executeCase2', usage: true,
                                           mappingPaths: ['/case-studies/group/1/case/2/execute']]
                ]
        ])
        explanations = PortalCaseStudyCodeExplanationService.load(module, 'vi', [group])
        assertFalse(explanations[1]?.containsKey(1))
        assertEquals('<p>Actual execution.</p>', explanations[1][2].howItWorksHtml)
        assertFalse(explanations[1][2].containsKey('situations'))
        // A different language must not borrow the Vietnamese contents.
        assertTrue(PortalCaseStudyCodeExplanationService.load(module, 'en', [group]).isEmpty())
    }

    @Test
    void rejectsAmbiguousSignaturesAndMismatchedRouteWithoutCrossGroupFallback() {
        File module = temporary.newFolder('module')
        Map group1 = group(1, 1)
        Map group2 = group(2, 1)
        write(module, 'src/main/resources/swagger/vi/api-execution.yml', [
                Group1Controller: ['executeCase1()': [execution: '<p>Zero arguments.</p>'],
                                   'executeCase1(String)': [execution: '<p>One argument.</p>']],
                Group2Controller: ['executeCase1()': [execution: '<p>Other group.</p>']]
        ])
        write(module, 'src/main/resources/swagger/vi/api-descriptions.yml', [
                Group1Controller: ['executeCase1()': [methodName: 'executeCase1',
                                                     mappingPaths: ['/case-studies/group/1/case/1/execute']]],
                Group2Controller: ['executeCase1()': [methodName: 'executeCase1',
                                                     mappingPaths: ['/case-studies/group/1/case/1/execute']]]
        ])
        Map result = PortalCaseStudyCodeExplanationService.load(module, 'vi', [group1, group2])
        assertTrue(result.isEmpty())
    }

    @Test
    void scenariosNeedRealFieldsAndMatchingSignatureMetadata() {
        File module = temporary.newFolder('module')
        write(module, 'src/main/resources/swagger/vi/api-cases.yml', [
                Group1Controller: ['executeCase1(String)': [methodName: 'executeCase1',
                                                            mappingPaths: ['/case-studies/group/1/case/1/execute'],
                                                            cases: [
                                                                    [id: 'case_001', title: 'TODO: Write title', input: [:]],
                                                                    [id: 'case_002', title: 'A real input',
                                                                     input: [queryParams: [mode: 'test']], status: 200,
                                                                     response: [done: true]]
                                                            ]]]
        ])
        write(module, 'src/main/resources/swagger/vi/api-descriptions.yml', [
                Group1Controller: ['executeCase1(String)': [
                        methodName: 'executeCase1', usage: true,
                        mappingPaths: ['/case-studies/group/1/case/1/execute']]]
        ])
        Map result = PortalCaseStudyCodeExplanationService.load(module, 'vi', [group(1, 1)])
        assertEquals(['case_002'], result[1][1].situations*.id)
        assertEquals(200, result[1][1].situations[0].status)
        assertEquals([done: true], result[1][1].situations[0].response)
        assertFalse(result[1][1].containsKey('howItWorksHtml'))
    }

    @Test
    void skipsMissingOrInactiveDescriptionEvenWhenExecutionExists() {
        File module = temporary.newFolder('module')
        write(module, 'src/main/resources/swagger/vi/api-execution.yml', [
                Group1Controller: ['executeCase1()': [execution: '<p>Real execution.</p>']]
        ])
        assertTrue(PortalCaseStudyCodeExplanationService.load(module, 'vi',
                [group(1, 1)]).isEmpty())
        write(module, 'src/main/resources/swagger/vi/api-descriptions.yml', [
                Group1Controller: ['executeCase1()': [methodName: 'executeCase1',
                                                      mappingPaths: ['/case-studies/group/1/case/1/execute'],
                                                      usage: false]]
        ])
        assertTrue(PortalCaseStudyCodeExplanationService.load(module, 'vi',
                [group(1, 1)]).isEmpty())
    }

    private static Map group(int id, int count) {
        [groupID: id, groupName: 'Group ' + id,
         cases: (1..count).collect { [situation: 'Situation', resolve: 'Resolve', knowledge: 'Knowledge',
                                      code: 'Code', readmeRelated: [anchorList: []]] }]
    }

    private static File write(File module, String path, Map yaml) {
        File file = new File(module, path)
        assertTrue(file.parentFile.isDirectory() || file.parentFile.mkdirs())
        file.setText(new Yaml().dump(yaml), 'UTF-8')
        file
    }
}
