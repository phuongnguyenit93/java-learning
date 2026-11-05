package com.example.learning.task.sheets.service.excel

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.gradle.api.GradleException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml

import static org.junit.Assert.*

class ModuleExcelGenerationServiceTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder()

    @Test
    void rendersBothLanguagesOfflineAndPreservesCanonicalAssessment() {
        Map fixture = fixture()
        File module = fixture.module as File
        File oldVi = new File(module, 'content/sheet/vi/module.xlsx')
        File en = new File(module, 'content/sheet/en/module.xlsx')
        assertFalse(en.exists())
        Map dry = new ModuleExcelGenerationService().execute(
                fixture.root as File, module, [], false)
        assertEquals('DRY_RUN', dry.status)
        assertEquals(['vi','en'], dry.files*.language)
        assertFalse(oldVi.exists())
        assertFalse(en.exists())

        Map result = new ModuleExcelGenerationService().execute(
                fixture.root as File, module, [], true)
        assertEquals('EXPORTED', result.status)
        assertTrue(oldVi.isFile())
        assertTrue(en.isFile())
        [oldVi,en].eachWithIndex { File file, int i ->
            new XSSFWorkbook(new ByteArrayInputStream(file.bytes)).withCloseable { book ->
                assertEquals(1, book.numberOfSheets)
                def sheet = book.getSheetAt(0)
                assertNull('Workbook must not freeze any rows or columns',
                        sheet.getPaneInformation())
                assertEquals(45f, sheet.defaultRowHeightInPoints, 0.01f)
                [0, 1, 14, 27, 28].each { int col ->
                    assertEquals(32 * 256, sheet.getColumnWidth(col))
                }
                [0, 1, 16, 18, 19, 20, 26].each { int row ->
                    assertEquals(45f, sheet.getRow(row).heightInPoints, 0.01f)
                }
                assertFalse(sheet.getRow(0).getCell(0).cellStyle.wrapText)
                assertFalse(sheet.getRow(19).getCell(15).cellStyle.wrapText)
                assertFalse(sheet.getRow(20).getCell(2).cellStyle.wrapText)
                assertEquals('DEMO', text(sheet,1,2))
                assertEquals('module/demo', text(sheet,3,9))
                assertEquals('', text(sheet,3,10))
                assertEquals('basic-001', text(sheet,21,1))
                assertEquals('BASIC', text(sheet,21,15))
                assertEquals('Full lesson body ' + (i == 0 ? 'vi' : 'en'),
                        text(sheet,21,3))
                assertEquals('', text(sheet,19,28))
                assertEquals('', text(sheet,20,24))
                assertEquals('Summary YAML', text(sheet,19,29))
                assertTrue(text(sheet,20,29).contains('roadmap:'))
                assertEquals('Lesson summary ' + (i == 0 ? 'vi' : 'en'),
                        text(sheet,21,29))
                assertTrue(text(sheet,20,16).contains('interviewQuestion:'))
                assertTrue(text(sheet,27,16).contains('groupID: 1'))
                assertTrue(text(sheet,27,19).contains('groupID: 1'))
                assertEquals('', text(sheet,20,28))
            }
        }
        byte[] before = oldVi.bytes
        new ModuleExcelGenerationService().execute(
                fixture.root as File, module, ['en'], true)
        assertArrayEquals(before, oldVi.bytes)
    }

    @Test
    void failuresBeforeCommitDoNotTouchPreviousWorkbooks() {
        Map fixture = fixture()
        File module = fixture.module as File
        new ModuleExcelGenerationService().execute(fixture.root as File, module, [], true)
        File vi = new File(module, 'content/sheet/vi/module.xlsx')
        File en = new File(module, 'content/sheet/en/module.xlsx')
        byte[] viBefore = vi.bytes
        byte[] enBefore = en.bytes
        File summary = new File(module,
                'content/readme/docs/en/menu/1.Basic/summary/summaries.yml')
        summary.setText('summaries: []\n', 'UTF-8')
        try {
            new ModuleExcelGenerationService().execute(
                    fixture.root as File, module, [], true)
            fail('Missing EN Summary should be rejected')
        } catch (GradleException expected) {
            assertTrue(expected.message.contains('Summary lesson count mismatch'))
        }
        assertArrayEquals(viBefore, vi.bytes)
        assertArrayEquals(enBefore, en.bytes)
        try {
            new ModuleExcelGenerationService().execute(
                    fixture.root as File, module, ['de'], false)
            fail('Undeclared languages must be rejected')
        } catch (GradleException expected) {
            assertTrue(expected.message.contains('languageList'))
        }
    }

    @Test
    void maxFillSplitsOnBothUnicodeLimitsWithoutDataLoss() {
        String emoji = '\uD83D\uDE80'
        String source = 'a' * 29999 + emoji + 'z' * 20
        List<String> pieces = ModuleExcelContext.chunks(source, 2)
        assertEquals(source, pieces.join(''))
        assertEquals(30000, pieces[0].codePointCount(0, pieces[0].length()))
        assertTrue(pieces[0].length() <= 32767)
        String utf16Bound = emoji * 16384
        List<String> byUtf16 = ModuleExcelContext.chunks(utf16Bound, 2)
        assertEquals(32766, byUtf16[0].length())
        assertEquals(2, byUtf16.size())
        assertEquals(utf16Bound, byUtf16.join(''))
        try {
            ModuleExcelContext.chunks('a' * 60001, 2)
            fail('Oversized payload should be rejected')
        } catch (GradleException expected) {
            assertTrue(expected.message.contains('capacity'))
        }
    }

    private Map fixture() {
        File root = tmp.newFolder('repo')
        File module = new File(root, 'module/demo')
        assertTrue(module.mkdirs())
        put(module, 'gradle.properties', '# module')
        put(module, 'master.json', '''{
          "SERVICE_NAME": {"TYPE": "string", "VALUE": "DEMO"},
          "MODULE_LANGUAGE": {"TYPE": "list", "VALUE": ["vi", "en"]},
          "CURRENT_STEP": {"TYPE": "string", "VALUE": "11"}
        }''')
        ['vi','en'].each { String lang ->
            String label = lang
            put(module, 'content/readme/docs/' + lang + '/LIST.md',
                    '* **1.Basic**\n    * [Basic](content/readme/docs/' +
                            lang + '/menu/1.Basic/knowledge/Basic.md)\n')
            put(module, 'content/readme/docs/' + lang + '/BASE.md',
                    '# Introduction ' + lang)
            put(module, 'content/readme/docs/' + lang + '/menu/1.Basic/knowledge/Basic.md',
                    '''<a id="back-to-top"></a>
# Chapter ''' + label + '''

## Menu
- [Lesson](#basic-001)

## <a id="basic-001">Lesson</a>

<details>
<summary>Details</summary>

Full lesson body ''' + label + '''

</details>
''')
            put(module, 'content/readme/docs/' + lang +
                    '/menu/1.Basic/metadata/knowledge-metadata.yml',
                    'sections:\n  basic-001: {difficulty: BASIC}\n')
            Map section = [
                    version:1, type:'LEARNING_ROADMAP',
                    title:'Chapter ' + lang, roadmapHeading:'Learning flow',
                    roadmap:[[id:'basic-001',order:1,title:'Lesson',
                              description:'Learn one thing.',transition:null]],
                    comparisonMarkdown: '''## 2. Comparison

| Criteria | Bài 1 |
| --- | --- |
| Skill | Basic |
| Example | Demo |
| Scope | Java |
| Check | Output |'''
            ]
            put(module, 'content/readme/docs/' + lang +
                    '/menu/1.Basic/summary/section.yml', yaml(section))
            put(module, 'content/readme/docs/' + lang +
                    '/menu/1.Basic/summary/summaries.yml',
                    yaml([summaries:[[id:'basic-001',title:'Lesson',
                                      summary:'Lesson summary ' + lang]]]))
            put(module, 'content/roadmap/' + lang + '/roadmap.yml',
                    yaml([roadmap:[[id:'fundamentals',title:'Milestone ' + lang,
                                    purpose:'Purpose',objectives:['Learn OOP']]]]))
            put(module, 'content/reference/' + lang + '/references.yml',
                    yaml([references:[[title:'Java spec',url:'https://dev.java/learn/oop/',
                                       description:'Reference ' + lang]]]))
            put(module, 'content/interview/' + lang + '/interview.yml',
                    yaml([interviewQuestion:[[groupID:1,groupName:'Group',
                         questions:[[question:'Question ' + lang,knowledge:'Concept',
                                     readmeRelated:[anchorList:['basic-001']],
                                     answer:'Answer ' + lang]]]]]))
            put(module, 'content/casestudy/' + lang + '/casestudy.yml',
                    yaml([caseStudy:[[groupID:1,groupName:'Group',
                         cases:[[situation:'Situation ' + lang,resolve:'Resolve',
                                 knowledge:'Concept',
                                 readmeRelated:[anchorList:['basic-001']],
                                 code:'### Code ' + lang]]]]]))
            put(module, 'content/quiz/' + lang + '/quiz.yml',
                    yaml([quiz:[[groupID:1,groupName:'Group',
                        questions:[[question:'Quiz ' + lang,
                                    readmeRelated:[anchorList:['basic-001']],
                                    answers:['A','B','C','D'].collect { String id ->
                                        [id:id,answer:'Choice ' + id,
                                         explanation:'Explanation ' + id]
                                    },correctAnswerId:'A']]]]]))
        }
        return [root:root,module:module]
    }

    private static String yaml(Map value) {
        DumperOptions options = new DumperOptions()
        options.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        options.allowUnicode = true
        return new Yaml(options).dump(value)
    }

    private static void put(File base, String path, String content) {
        File file = new File(base, path)
        assertTrue(file.parentFile.isDirectory() || file.parentFile.mkdirs())
        file.setText(content, 'UTF-8')
    }

    private static String text(def sheet, int row, int col) {
        return sheet.getRow(row - 1)?.getCell(col - 1)?.stringCellValue ?: ''
    }
}
