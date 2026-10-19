package com.example.learning.task.sheets.service

import com.google.api.services.sheets.v4.model.Request
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.gradle.api.GradleException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.junit.Assert.*

class ModuleSheetExcelImportServiceTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder()
    private static final String URL = 'https://docs.google.com/spreadsheets/d/synthetic/edit'

    @Test
    void dryRunValidatesExcelAndPlansOnlyOneNewTabWithoutWrites() {
        def setup = createModule()
        makeWorkbook(setup.file, 'module/demo')
        List<String> actions = []
        def service = fakeService(actions)
        Map report = service.execute(setup.root, setup.module, URL, 'VI', false)
        assertEquals('DRY_RUN', report.status)
        assertEquals('OOP (import 2)', report.tab)
        assertEquals(3, report.cells)
        assertNull(report.gid)
        assertTrue(report.batches >= 1)
        assertEquals([], actions)
        assertTrue(setup.file.exists())
    }

    @Test
    void applyReturnsGeneratedGidAndOnlyWritesNewTab() {
        def setup = createModule()
        makeWorkbook(setup.file, 'module/demo')
        List<String> actions = []
        def service = fakeService(actions)
        Map report = service.execute(setup.root, setup.module, URL, 'vi', true)
        assertEquals('IMPORTED', report.status)
        assertEquals('2345', report.gid)
        assertEquals(URL + '#gid=2345', report.tabUrl)
        assertEquals('create:OOP (import 2)', actions[0])
        assertTrue(actions.find { it.startsWith('write:2345:') } != null)
        assertEquals('verify:2345:module/demo', actions.last())
        assertFalse(actions.contains('delete:2345'))
    }

    @Test
    void failedWriteRollsBackOnlyTheFreshTab() {
        def setup = createModule()
        makeWorkbook(setup.file, 'module/demo')
        List<String> actions = []
        def service = fakeService(actions, true)
        try {
            service.execute(setup.root, setup.module, URL, 'vi', true)
            fail('write must fail')
        }
        catch (GradleException expected) {
            assertTrue(expected.message.contains('rolled back'))
        }
        assertTrue(actions.contains('delete:2345'))
        assertTrue(setup.file.exists())
    }

    @Test
    void rejectsWrongI3MissingFileAndMultipleSheetsBeforeGoogleWrites() {
        def setup = createModule()
        List<String> actions = []
        def service = fakeService(actions)
        try {
            service.execute(setup.root, setup.module, URL, 'vi', true)
            fail('missing Excel must fail')
        }
        catch (GradleException ignored) { }

        makeWorkbook(setup.file, 'module/wrong')
        try {
            service.execute(setup.root, setup.module, URL, 'vi', true)
            fail('I3 mismatch must fail')
        }
        catch (GradleException expected) {
            assertTrue(expected.message.contains('I3 mismatch'))
        }
        assertEquals([], actions)

        makeWorkbook(setup.file, 'module/demo', true)
        try {
            service.execute(setup.root, setup.module, URL, 'vi', true)
            fail('more than one worksheet must fail')
        }
        catch (GradleException expected) {
            assertTrue(expected.message.contains('exactly one worksheet'))
        }
        assertEquals([], actions)
    }

    @Test
    void formulasAreRejectedToAvoidDivergence() {
        def setup = createModule()
        makeWorkbook(setup.file, 'module/demo')
        setup.file.withInputStream { input ->
            new XSSFWorkbook(input).withCloseable { workbook ->
                workbook.getSheetAt(0).getRow(0).getCell(0).cellFormula = '1+1'
                setup.file.withOutputStream { output -> workbook.write(output) }
            }
        }
        try {
            ModuleSheetExcelImportService.prepare(setup.file, 'module/demo')
            fail('Unresolved Excel formula must fail')
        }
        catch (GradleException expected) {
            assertTrue(expected.message.contains('formula'))
        }
    }

    @Test
    void collisionTitleIsCaseInsensitiveAndDoesNotOverwrite() {
        assertEquals('OOP', ModuleSheetExcelImportService.availableTitle('OOP', []))
        assertEquals('OOP (import 1)', ModuleSheetExcelImportService.availableTitle(
                'OOP', [[title: 'oop']]))
        assertEquals('OOP (import 3)', ModuleSheetExcelImportService.availableTitle(
                'OOP', [[title: 'OOP'], [title: 'OOP (import 1)'], [title: 'oop (import 2)']]))
    }

    private Map createModule() {
        File root = temporary.newFolder('repo')
        File module = new File(root, 'module/demo')
        assertTrue(module.mkdirs())
        return [root: root, module: module,
                file: new File(module, 'content/sheet/vi/module.xlsx')]
    }

    private static void makeWorkbook(File file, String i3, boolean extraSheet = false) {
        assertTrue(file.parentFile.isDirectory() || file.parentFile.mkdirs())
        new XSSFWorkbook().withCloseable { book ->
            def sheet = book.createSheet('OOP')
            def row = sheet.createRow(0)
            def title = row.createCell(0)
            title.setCellValue('Tiêu đề')
            def style = book.createCellStyle()
            style.wrapText = true
            title.cellStyle = style
            row.createCell(1).setCellValue('Đa dòng\nNội dung')
            sheet.createRow(2).createCell(8).setCellValue(i3)
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 1))
            sheet.createFreezePane(0, 1)
            if (extraSheet) book.createSheet('Second')
            file.withOutputStream { stream -> book.write(stream) }
        }
    }

    private static ModuleSheetExcelImportService fakeService(List<String> actions,
                                                               boolean failWrite = false) {
        SheetsMetadataReaderService reader = new SheetsMetadataReaderService() {
            @Override Map<String, Object> openSpreadsheet(String url) {
                assertEquals(URL, url)
                return [spreadsheetId: 'synthetic', tabs:
                        [[title: 'OOP'], [title: 'OOP (import 1)']]]
            }
            @Override Map<String, Object> openSpreadsheetForWrite(String url) {
                return openSpreadsheet(url)
            }
        }
        return new ModuleSheetExcelImportService(reader) {
            @Override protected int addTab(Map ctx, String title, int rows, int cols,
                                           int frozenRows, int frozenCols) {
                actions.add('create:' + title)
                assertTrue(rows >= 3)
                assertTrue(cols >= 9)
                assertEquals(1, frozenRows)
                return 2345
            }
            @Override protected void sendBatch(Map ctx, List<Request> requests) {
                actions.add('write:' + requests[0].updateCells?.start?.sheetId + ':' +
                        requests.size())
                assertTrue(requests.size() > 0)
                requests.each { Request request ->
                    if (request.updateCells != null) {
                        assertEquals(2345, request.updateCells.start.sheetId)
                    }
                }
                if (failWrite) throw new IllegalStateException('Simulated Sheets API failure')
            }
            @Override protected void verifyNewTab(Map ctx, int gid, String title, String location) {
                actions.add('verify:' + gid + ':' + location)
            }
            @Override protected void deleteTab(Map ctx, int gid) {
                actions.add('delete:' + gid)
            }
        }
    }
}
