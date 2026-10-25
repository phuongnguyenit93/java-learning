package com.example.learning.task.sheets.service

import com.google.api.services.sheets.v4.model.Request
import groovy.json.JsonSlurper
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.gradle.api.GradleException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.StandardCopyOption

import static org.junit.Assert.*

class ModuleEnSheetPublishServiceTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder()
    private static final String URL = 'https://docs.google.com/spreadsheets/d/synthetic/edit'

    @Test
    void clonesExactlyOncePersistsGidAndNeverChangesExistingTabs() {
        Map f = fixture()
        List<String> events = []
        List<Map> tabs = [
                [gid:'0', title:'Template', moduleLocation:'', rows:700, columns:28],
                [gid:'111', title:'OOP', moduleLocation:'module/demo', rows:700, columns:28]
        ]
        ModuleEnSheetPublishService service = mocked(tabs, events)
        Map preview = service.execute(f.root, f.module, URL, '111', 'CLONE', false)
        assertEquals('DRY_RUN', preview.status)
        assertFalse(new File(f.module,'content/sheet/en/publish-state.json').exists())
        assertTrue(events.isEmpty())
        Map created = service.execute(f.root, f.module, URL, '111', 'CLONE', true)
        assertEquals('APPLIED', created.status)
        assertEquals('OOP EN', created.tab)
        assertEquals(false, created.existing)
        assertEquals(1, events.count { it.startsWith('duplicate:') })
        assertTrue(events.any { it == 'write:2' })
        Map saved = new JsonSlurper().parse(new File(f.module,
                'content/sheet/en/publish-state.json')) as Map
        assertEquals(created.gid, saved.enGid)
        assertEquals('CLONED', saved.phase)
        Map replay = service.execute(f.root, f.module, URL, '111', 'CLONE', true)
        assertTrue(replay.existing)
        assertEquals(1, events.count { it.startsWith('duplicate:') })
    }

    @Test
    void failsClosedOnEnGidOrTitleCollision() {
        Map f = fixture()
        int gid = ModuleEnSheetPublishService.deterministicGid('synthetic', 'module/demo')
        List<Map> tabs = [
                [gid:'0', title:'Template', moduleLocation:'', rows:700, columns:28],
                [gid:'111', title:'OOP', moduleLocation:'module/demo', rows:700, columns:28],
                [gid:gid.toString(), title:'OTHER USER TAB', moduleLocation:'module/other']
        ]
        try {
            mocked(tabs, []).execute(f.root, f.module, URL, '111', 'CLONE', true)
            fail('Must not overwrite a collision')
        } catch (GradleException error) {
            assertTrue(error.message.contains('collision'))
        }
        assertFalse(new File(f.module,'content/sheet/en/publish-state.json').exists())
    }

    @Test
    void changingSourceAfterCloneStopsReuseInsteadOfCreatingAnotherTab() {
        Map f = fixture()
        List<String> events = []
        List<Map> tabs = [
                [gid:'0', title:'Template', moduleLocation:'', rows:700, columns:28],
                [gid:'111', title:'OOP', moduleLocation:'module/demo', rows:700, columns:28]
        ]
        def service = mocked(tabs, events)
        service.execute(f.root, f.module, URL, '111', 'CLONE', true)
        new File(f.module, 'content/readme/docs/en/BASE.md').text += 'changed'
        try {
            service.execute(f.root, f.module, URL, '111', 'CLONE', true)
            fail('Expected source drift blocker')
        } catch (GradleException error) {
            assertTrue(error.message.contains('sourceSha256'))
        }
        assertEquals(1, events.count { it.startsWith('duplicate:') })
    }

    @Test
    void videoAuthoredAfterStep15DoesNotChangeMandatoryPublishFingerprint() {
        Map f = fixture()
        List<String> events = []
        List<Map> tabs = [
                [gid:'0', title:'Template', moduleLocation:'', rows:700, columns:28],
                [gid:'111', title:'OOP', moduleLocation:'module/demo', rows:700, columns:28]
        ]
        def service = mocked(tabs, events)
        service.execute(f.root, f.module, URL, '111', 'CLONE', true)
        File video = new File(f.module,
                'content/readme/docs/en/menu/1.Basic/video/video-script.yml')
        assertTrue(video.parentFile.mkdirs())
        video.text = 'English Video authored after STEP 15'
        assertEquals('APPLIED', service.execute(f.root, f.module,
                URL, '111', 'CLONE', true).status)
        video.text = 'Revised English Video'
        assertEquals('APPLIED', service.execute(f.root, f.module,
                URL, '111', 'CLONE', true).status)
        assertEquals(1, events.count { it.startsWith('duplicate:') })
    }

    @Test
    void detectsDriftWithinChapterInterviewSource() {
        Map f = fixture()
        File question = new File(f.module,
                'content/readme/docs/en/menu/1.Basic/interview/question.yml')
        assertTrue(question.parentFile.mkdirs())
        question.setText('questions:\n  - question: Original question\n', 'UTF-8')
        List<String> events = []
        List<Map> tabs = [
                [gid:'0', title:'Template', moduleLocation:'', rows:700, columns:28],
                [gid:'111', title:'OOP', moduleLocation:'module/demo', rows:700, columns:28]
        ]
        def service = mocked(tabs, events)
        service.execute(f.root, f.module, URL, '111', 'CLONE', true)
        question.setText('questions:\n  - question: Updated question\n', 'UTF-8')
        try {
            service.execute(f.root, f.module, URL, '111', 'CLONE', true)
            fail('Chapter question source drift must invalidate the stored mapping')
        } catch (GradleException expected) {
            assertTrue(expected.message.contains('sourceSha256'))
        }
        assertEquals(1, events.count { it.startsWith('duplicate:') })
    }

    @Test
    void preservesOnlyContentCellsWhileLeavingFormattingAndHeadersUntouched() {
        Map f = fixture()
        File en = new File(f.module, 'build/step15/upload.xlsx')
        assertTrue(en.parentFile.mkdirs())
        File vi = new File(f.module, 'content/sheet/vi/module.xlsx')
        vi.withInputStream { stream ->
            new XSSFWorkbook(stream).withCloseable { XSSFWorkbook book ->
                def sheet = book.getSheetAt(0)
                sheet.getRow(19).getCell(0).setCellValue('English chapter')
                sheet.getRow(20).getCell(1).setCellValue('English lesson')
                sheet.getRow(20).createCell(2).setCellValue('English knowledge')
                sheet.getRow(19).createCell(23).setCellValue('Optional Video EN')
                en.withOutputStream { book.write(it) }
            }
        }
        List<Map> plan = ModuleEnSheetPublishService.contentPlan(en, vi)
        assertTrue(plan.any { it.r==19 && it.c==0 && it.value=='English chapter' })
        assertTrue(plan.any { it.r==20 && it.c==1 && it.value=='English lesson' })
        assertTrue(plan.any { it.r==20 && it.c==2 && it.value=='English knowledge' })
        assertTrue(plan.any { it.r==2 && it.c==5 && it.value=='Milestone' })
        assertFalse(plan.any { it.r==2 && it.c==9 }) // Legacy Excel progress is never written
        assertTrue(plan.any { it.r==18 && it.c==0 && it.value=='Chapter' })
        assertTrue(plan.any { it.r==18 && it.c==15 && it.value=='Interview YAML' })
        assertFalse(plan.any { it.r==18 && it.c==14 }) // Difficulty header preserved
        assertFalse(plan.any { it.r==19 && it.c in (23..26) }) // Video is optional
        List<Request> requests = []
        def service = new ModuleEnSheetPublishService() {
            @Override
            protected void sendBatch(Map context, List<Request> batch) {
                requests.addAll(batch)
            }
        }
        service.writeValues([spreadsheetId:'synthetic'], 333, plan)
        assertTrue(requests.every {
            it.updateCells != null &&
                    it.updateCells.fields == 'userEnteredValue' &&
                    it.updateCells.start.sheetId == 333
        })
        assertTrue(requests.size() < plan.size()) // coalesced rows
    }

    @Test
    void titleAndGidAreDeterministicAndDoNotReadGithubOrAi() {
        int gid = ModuleEnSheetPublishService.deterministicGid('synthetic','module/demo')
        assertTrue(gid > 0)
        assertEquals(gid, ModuleEnSheetPublishService.deterministicGid(
                'synthetic', 'module/demo'))
        assertEquals('OOP EN', ModuleEnSheetPublishService.enTitle('OOP'))
        assertEquals('AB33', ModuleEnSheetPublishService.a1(32,27))
        assertEquals('A1', ModuleEnSheetPublishService.a1(0,0))
    }

    @Test
    void fullPublishIsCodeOnlyAndRetryDoesNotDuplicateOrReupload() {
        Map f = fixture()
        List<Map> tabs = [
                [gid:'0', title:'Template', moduleLocation:'',rows:700,columns:28],
                [gid:'111', title:'OOP',moduleLocation:'module/demo',rows:700,columns:28]
        ]
        Map<String,Object> cells = [:]
        List<String> operations = []
        int enGid = ModuleEnSheetPublishService.deterministicGid('synthetic','module/demo')
        SheetsMetadataReaderService reader = new SheetsMetadataReaderService() {
            @Override Map<String,Object> openTab(String url, String gid) {
                return [spreadsheetId:'synthetic',sheetId:gid,tabTitle:'OOP',
                        moduleLocation:'module/demo',client:null]
            }
            @Override Map<String,Object> openSpreadsheet(String url) {
                return [spreadsheetId:'synthetic',tabs:tabs,client:null]
            }
            @Override Map<String,Object> openSpreadsheetForWrite(String url) {
                return openSpreadsheet(url)
            }
        }
        ModuleContentExcelSyncService workbooks = new ModuleContentExcelSyncService() {
            @Override
            Map execute(File root, File module, String part, boolean apply,
                        boolean forceVi, File target) {
                assertEquals('ALL',part)
                assertTrue(forceVi)
                if (apply) {
                    Files.createDirectories(target.parentFile.toPath())
                    Files.copy(new File(module,'content/sheet/vi/module.xlsx').toPath(),
                            target.toPath(),StandardCopyOption.REPLACE_EXISTING)
                }
                operations.add('workbook')
                return [status: apply?'EXPORTED':'DRY_RUN',changedCells:12]
            }
        }
        ModuleSheetExcelExportService exporter = new ModuleSheetExcelExportService() {
            @Override
            Map execute(File root,File module,String fileURL,String sheetURL,
                        String lang,boolean apply) {
                assertTrue(apply)
                assertEquals('en',lang)
                assertTrue(sheetURL.endsWith('#gid='+enGid))
                File output = new File(module,'content/sheet/en/module.xlsx')
                Files.createDirectories(output.parentFile.toPath())
                new XSSFWorkbook().withCloseable { XSSFWorkbook book ->
                    book.createSheet('OOP EN')
                    output.withOutputStream { book.write(it) }
                }
                operations.add('export')
                return [file:output,cells:120]
            }
        }
        ModuleEnSheetPublishService service = new ModuleEnSheetPublishService(
                reader,workbooks,exporter) {
            @Override protected void verifyTemplate(Map ctx, Map template) { }
            @Override protected void verifyClonedFormatting(Map ctx, Map tab) { }
            @Override protected int duplicate(Map ctx,int template,int gid,String title) {
                operations.add('duplicate')
                tabs.add([gid:gid.toString(),title:title,moduleLocation:'',rows:700,columns:29])
                return gid
            }
            @Override protected void verifyIdentity(Map ctx,int gid,String title,String module) {
                assertEquals(title,tabs.find{it.gid==gid.toString()}.title)
                assertEquals(module,tabs.find{it.gid==gid.toString()}.moduleLocation)
            }
            @Override protected void writeValues(Map ctx,int gid,List<Map> changes) {
                changes.each { Map c ->
                    cells[gid+':'+c.r+':'+c.c] = c.value
                    if (c.r == 2 && c.c == 8)
                        tabs.find{it.gid==gid.toString()}.moduleLocation = c.value
                }
                operations.add('write')
            }
            @Override protected List<List<Object>> readGrid(Map ctx,String title,
                                                              List<Map> requested) {
                String gid = title=='OOP'?'111':enGid.toString()
                requested.collect { Map c ->
                    def value = c.r==1 && c.c==9 ? 'Current STEP' :
                            c.r==2 && c.c==9 && gid=='111' ?
                                    (cells['111:2:9'] ?: 'STEP 14') :
                                    cells[gid+':'+c.r+':'+c.c]
                    value == null ? [] : [value]
                }
            }
        }
        Map completed = service.execute(f.root,f.module,URL,'111','PUBLISH',true)
        assertEquals('APPLIED',completed.status)
        assertEquals(1,operations.count{it=='duplicate'})
        assertEquals(1,operations.count{it=='export'})
        assertFalse(cells.containsKey('111:2:9'))
        assertFalse(cells.containsKey(enGid+':2:9'))
        assertEquals('EXPORTED',new JsonSlurper().parse(new File(f.module,
                'content/sheet/en/publish-state.json')).phase)
        int afterWrites = operations.size()
        Map replay = service.execute(f.root,f.module,URL,'111','PUBLISH',true)
        assertEquals('ALREADY_PUBLISHED',replay.status)
        assertEquals(afterWrites,operations.size())
    }

    private static ModuleEnSheetPublishService mocked(List<Map> tabs, List<String> events) {
        SheetsMetadataReaderService reader = new SheetsMetadataReaderService() {
            @Override Map<String,Object> openTab(String url, String gid) {
                return [spreadsheetId:'synthetic', sheetId:gid,
                        tabTitle:'OOP', moduleLocation:'module/demo', client:null]
            }
            @Override Map<String,Object> openSpreadsheet(String url) {
                return [spreadsheetId:'synthetic', tabs:tabs, client:null]
            }
            @Override Map<String,Object> openSpreadsheetForWrite(String url) {
                return openSpreadsheet(url)
            }
        }
        return new ModuleEnSheetPublishService(reader) {
            @Override protected void verifyTemplate(Map ctx, Map template) {
                assertEquals('Template', template.title)
            }
            @Override protected void verifyClonedFormatting(Map ctx, Map tab) {
                assertEquals('OOP EN', tab.title)
            }
            @Override protected int duplicate(Map ctx, int templateGid, int newGid,
                                              String title) {
                events.add('duplicate:' + newGid)
                assertEquals(0,templateGid)
                tabs.add([gid:newGid.toString(),title:title,moduleLocation:''])
                return newGid
            }
            @Override protected void writeValues(Map context, int gid, List<Map> changes) {
                events.add('write:' + changes.size())
                tabs.find { it.gid == gid.toString() }.moduleLocation = 'module/demo'
            }
            @Override protected void verifyIdentity(Map context, int gid, String title,
                                                    String location) {
                assertEquals(title, tabs.find { it.gid == gid.toString() }.title)
                assertEquals(location, tabs.find { it.gid == gid.toString() }.moduleLocation)
            }
        }
    }

    private Map fixture() {
        File root = temp.newFolder('repo')
        File module = new File(root, 'module/demo')
        assertTrue(module.mkdirs())
        new File(module, 'gradle.properties').text = '# module'
        new File(module, 'master.json').setText('''{
  "CURRENT_STEP": {"TYPE": "string", "VALUE": "14"}
}''', 'UTF-8')
        File vi = new File(module, 'content/sheet/vi/module.xlsx')
        assertTrue(vi.parentFile.mkdirs())
        new XSSFWorkbook().withCloseable { XSSFWorkbook book ->
            def sheet = book.createSheet('OOP')
            sheet.createRow(2).createCell(8).setCellValue('module/demo')
            def roadmap = sheet.getRow(2)
            roadmap.createCell(5).setCellValue('Milestone')
            roadmap.createCell(6).setCellValue('Purpose')
            roadmap.createCell(7).setCellValue('Objective')
            sheet.createRow(18).createCell(0).setCellValue('Menu Cấp 1')
            sheet.getRow(18).createCell(1).setCellValue('Menu Cấp 2')
            sheet.createRow(19).createCell(0).setCellValue('Chương tiếng Việt')
            sheet.getRow(19).createCell(1).setCellValue('Basic.md')
            sheet.createRow(20).createCell(1).setCellValue('Bài tiếng Việt')
            vi.withOutputStream { book.write(it) }
        }
        File en = new File(module, 'content/readme/docs/en/BASE.md')
        assertTrue(en.parentFile.mkdirs())
        en.text = '# Vietnamese clone'
        return [root:root,module:module]
    }
}
