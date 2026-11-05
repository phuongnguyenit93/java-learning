package com.example.learning.task.sheets.service

import groovy.json.JsonOutput
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.gradle.api.GradleException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.junit.Assert.*

class ModuleOnlineSheetCleanupServiceTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder()

    private Map fixture() {
        File root = folder.newFolder('repo')
        File module = new File(root, 'module/demo')
        assertTrue(module.mkdirs())
        new File(module, 'gradle.properties').text = 'USE_TASK=TRUE\n'
        new File(module, 'master.json').text = JsonOutput.toJson([
                CURRENT_STEP: [TYPE: 'string', VALUE: '12'],
                SERVICE_NAME: [VALUE: 'DEMO'],
                MODULE_LANGUAGE: [VALUE: ['vi', 'en']]
        ])
        File instructions = new File(root, 'module-generate-agent')
        assertTrue(instructions.mkdirs())
        new File(instructions, 'module-generate-info.txt').text =
                'Excel Page URL\n' +
                'https://docs.google.com/spreadsheets/d/sheet-doc-123\n' +
                'Demo Tab sheet id : 123\n' +
                'Other Tab sheet id : 456\n'
        ['vi', 'en'].each { String lang ->
            File output = new File(module, 'content/sheet/' + lang + '/module.xlsx')
            assertTrue(output.parentFile.mkdirs())
            new XSSFWorkbook().withCloseable { XSSFWorkbook book ->
                def sheet = book.createSheet('DEMO')
                sheet.createRow(0).createCell(1).setCellValue('DEMO')
                sheet.createRow(2).createCell(8).setCellValue('module/demo')
                sheet.createRow(18).createCell(28).setCellValue('Summary YAML')
                output.withOutputStream { stream -> book.write(stream) }
            }
        }
        FakeReader reader = new FakeReader()
        reader.sheet = [spreadsheetId: 'sheet-doc-123', tabs: [
                [gid: '123', title: 'Demo Tab', moduleLocation: 'module/demo'],
                [gid: '456', title: 'Other Tab', moduleLocation: 'module/other'],
                [gid: '0', title: 'Template', moduleLocation: '']
        ]]
        [root: root, module: module, reader: reader,
         cleanup: new FakeCleanup(reader),
         url: 'https://docs.google.com/spreadsheets/d/sheet-doc-123']
    }

    @Test
    void dryRunDoesNotDeleteAnyTab() {
        Map f = fixture()
        Map result = f.cleanup.execute(f.root, f.module, f.url, '123', false)
        assertEquals('DRY_RUN', result.status)
        assertEquals(['en', 'vi'], (result.workbooks*.language as List).sort())
        assertEquals(0, f.cleanup.deletions)
        assertEquals(3, f.reader.sheet.tabs.size())
        assertEquals(1, f.reader.readCalls)
        assertEquals(0, f.reader.writeCalls)
    }

    @Test
    void applyDeletesOnlyMappedGidAndPreservesTemplateAndOtherTabs() {
        Map f = fixture()
        Map result = f.cleanup.execute(f.root, f.module, f.url, '123', true)
        assertEquals('DELETED', result.status)
        assertEquals(1, f.cleanup.deletions)
        assertEquals(['456', '0'], f.reader.sheet.tabs*.gid)
        assertEquals(1, f.reader.writeCalls)
    }

    @Test
    void repeatedCleanupOfAbsentGidIsSafe() {
        Map f = fixture()
        assertEquals('DELETED', f.cleanup.execute(f.root, f.module, f.url, '123', true).status)
        assertEquals('ALREADY_ABSENT',
                f.cleanup.execute(f.root, f.module, f.url, '123', true).status)
        assertEquals(1, f.cleanup.deletions)
    }

    @Test
    void incorrectModuleLocationBlocksDeletion() {
        Map f = fixture()
        f.reader.sheet.tabs[0].moduleLocation = 'module/another'
        expectBlock(f, 'title/I3 mismatch')
    }

    @Test
    void changedMappedTabTitleBlocksDeletion() {
        Map f = fixture()
        f.reader.sheet.tabs[0].title = 'Some Other Tab'
        expectBlock(f, 'title/I3 mismatch')
    }

    @Test
    void missingWorkbookBlocksBeforeAnyGoogleWrite() {
        Map f = fixture()
        assertTrue(new File(f.module, 'content/sheet/en/module.xlsx').delete())
        expectBlock(f, 'offline workbook missing')
        assertEquals(0, f.reader.writeCalls)
    }

    @Test
    void wrongCheckpointBlocksDeletion() {
        Map f = fixture()
        File master = new File(f.module, 'master.json')
        master.text = master.text.replace('"12"', '"11"')
        expectBlock(f, 'CURRENT_STEP 12 or FINAL')
        assertEquals(0, f.reader.writeCalls)
    }

    @Test
    void finalCheckpointAllowsSafeRerunForCleanupRecovery() {
        Map f = fixture()
        File master = new File(f.module, 'master.json')
        master.text = master.text.replace('"12"', '"FINAL"')
        assertEquals('DELETED',
                f.cleanup.execute(f.root, f.module, f.url, '123', true).status)
        assertEquals('ALREADY_ABSENT',
                f.cleanup.execute(f.root, f.module, f.url, '123', true).status)
        assertEquals(1, f.cleanup.deletions)
    }

    @Test
    void differentMappingGidBlocksBeforeAnyGoogleWrite() {
        Map f = fixture()
        expectBlock(f, 'exactly one mapping', '987')
        assertEquals(0, f.reader.writeCalls)
    }

    @Test
    void neverDeletesTheLastRemainingTab() {
        Map f = fixture()
        f.reader.sheet.tabs = [f.reader.sheet.tabs[0]]
        expectBlock(f, 'last worksheet')
        assertEquals(['123'], f.reader.sheet.tabs*.gid)
    }

    @Test
    void neverDeletesTemplateEvenIfItIsMappedToModule() {
        Map f = fixture()
        new File(f.root, 'module-generate-agent/module-generate-info.txt').text =
                'Excel Page URL\n' +
                'https://docs.google.com/spreadsheets/d/sheet-doc-123\n' +
                'Template sheet id : 123\n'
        f.reader.sheet.tabs[0].title = 'Template'
        expectBlock(f, 'shared Template tab')
        assertEquals(0, f.reader.writeCalls)
    }

    private static void expectBlock(Map fixture, String expected, String gid = '123') {
        try {
            fixture.cleanup.execute(fixture.root, fixture.module, fixture.url, gid, true)
            fail('Expected cleanup to block')
        } catch (GradleException exception) {
            assertTrue(exception.message, exception.message.contains(expected))
        }
        assertEquals(0, fixture.cleanup.deletions)
    }

    private static class FakeReader extends SheetsMetadataReaderService {
        Map sheet
        int readCalls = 0
        int writeCalls = 0

        @Override
        Map<String, Object> openSpreadsheet(String url) {
            readCalls++
            return sheet
        }

        @Override
        Map<String, Object> openSpreadsheetForWrite(String url) {
            writeCalls++
            return sheet
        }
    }

    private static class FakeCleanup extends ModuleOnlineSheetCleanupService {
        int deletions = 0

        FakeCleanup(FakeReader reader) {
            super(reader)
        }

        @Override
        protected void deleteTab(Map context, int gid) {
            deletions++
            context.tabs.removeAll { it.gid == gid.toString() }
        }

        @Override
        protected List<Map> readbackTabs(Map context) {
            context.tabs.collect { [gid: it.gid, title: it.title] }
        }
    }
}
