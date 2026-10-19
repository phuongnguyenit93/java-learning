package com.example.learning.task.sheets.service

import com.google.api.services.sheets.v4.model.*
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.gradle.api.GradleException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.junit.Assert.*

class ModuleSheetExcelExportServiceTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder()
    private static final String FILE = 'https://docs.google.com/spreadsheets/d/testSpreadsheet/edit'
    private static final String TAB = FILE + '#gid=42'

    @Test
    void urlsMustBelongToTheSameSpreadsheetAndSelectNumericGid() {
        assertEquals('42', ModuleSheetExcelExportService.validateUrls(FILE, TAB).gid)
        assertEquals('42', ModuleSheetExcelExportService.validateUrls(FILE,
                FILE + '?gid=42').gid)
        ['https://docs.google.com/spreadsheets/d/another/edit#gid=42',
         FILE + '#gid=abc', FILE].each { invalid ->
            expectFailure { ModuleSheetExcelExportService.validateUrls(FILE, invalid) }
        }
        expectFailure {
            ModuleSheetExcelExportService.validateUrls(FILE,
                    'https://docs.google.com.evil.com/spreadsheets/d/testSpreadsheet/edit#gid=42')
        }
        assertEquals('vi', ModuleSheetExcelExportService.normalizeLanguage(' VI '))
        ['../../secret', 'vi/../../x', '', 'v'].each {
            expectFailure { ModuleSheetExcelExportService.normalizeLanguage(it) }
        }
    }

    @Test
    void dryRunIsReadOnlyAndApplyWritesOneWorksheetWithDataStylesAndMergedCells() {
        File root = tmp.newFolder('repo')
        File module = new File(root, 'module/demo')
        assertTrue(module.mkdirs())
        def source = spreadsheet([
                row([text('Giới thiệu', true), text('Dòng một\nDòng hai')]),
                row([text('Merge'), new CellData()]),
                row([number(3.5), bool(true), formula(25d)])
        ])
        source.sheets[0].setMerges([new GridRange().setSheetId(42)
                .setStartRowIndex(1).setEndRowIndex(2)
                .setStartColumnIndex(0).setEndColumnIndex(2)])
        def service = fixture(source, 'module/demo')
        Map dry = service.execute(root, module, FILE, TAB, 'vi', false)
        assertEquals('DRY_RUN', dry.status)
        assertEquals(6, dry.cells)
        File file = new File(module, 'content/sheet/vi/module.xlsx')
        assertFalse(file.exists())

        Map apply = service.execute(root, module, FILE, TAB, 'VI', true)
        assertEquals('EXPORTED', apply.status)
        assertTrue(file.isFile())
        file.withInputStream { input ->
            new XSSFWorkbook(input).withCloseable { excel ->
                assertEquals(1, excel.numberOfSheets)
                assertEquals('OOP', excel.getSheetAt(0).sheetName)
                assertEquals('Giới thiệu', excel.getSheetAt(0).getRow(0).getCell(0).stringCellValue)
                assertTrue(excel.getFontAt(
                        excel.getSheetAt(0).getRow(0).getCell(0).cellStyle.fontIndex).bold)
                assertEquals('Dòng một\nDòng hai',
                        excel.getSheetAt(0).getRow(0).getCell(1).stringCellValue)
                assertEquals(3.5d, excel.getSheetAt(0).getRow(2).getCell(0).numericCellValue, 0.0001)
                assertEquals(true, excel.getSheetAt(0).getRow(2).getCell(1).booleanCellValue)
                assertEquals(CellType.NUMERIC, excel.getSheetAt(0).getRow(2).getCell(2).cellType)
                assertEquals(25d, excel.getSheetAt(0).getRow(2).getCell(2).numericCellValue, 0.0001)
                assertEquals(1, excel.getSheetAt(0).numMergedRegions)
                assertEquals(1, excel.getSheetAt(0).paneInformation.horizontalSplitPosition)
            }
        }
    }

    @Test
    void wrongModuleOrMalformedCellNeverOverwritesOldExcel() {
        File root = tmp.newFolder('guard-repo')
        File module = new File(root, 'module/demo')
        assertTrue(module.mkdirs())
        File file = new File(module, 'content/sheet/vi/module.xlsx')
        assertTrue(file.parentFile.mkdirs())
        file.bytes = [10, 20, 30] as byte[]
        def wrong = fixture(spreadsheet([row([text('hi')])]), 'module/other')
        expectFailure { wrong.execute(root, module, FILE, TAB, 'vi', true) }
        assertArrayEquals([10, 20, 30] as byte[], file.bytes)

        def overflow = fixture(spreadsheet([row([text('z' * 32768)])]), 'module/demo')
        expectFailure { overflow.execute(root, module, FILE, TAB, 'vi', true) }
        assertArrayEquals([10, 20, 30] as byte[], file.bytes)
    }

    @Test
    void rejectsUnexpectedTabsAndUnresolvedFormulas() {
        File root = tmp.newFolder('source-check')
        File module = new File(root, 'module/demo')
        assertTrue(module.mkdirs())
        def extra = spreadsheet([row([text('ok')])])
        extra.sheets.add(new com.google.api.services.sheets.v4.model.Sheet()
                .setProperties(new SheetProperties().setSheetId(43).setTitle('Other')))
        expectFailure { fixture(extra, 'module/demo').execute(root, module, FILE, TAB, 'vi', false) }
        def badFormula = spreadsheet([row([new CellData()
                .setUserEnteredValue(new ExtendedValue().setFormulaValue('=A2'))])])
        expectFailure { fixture(badFormula, 'module/demo').execute(root, module, FILE, TAB, 'vi', true) }
    }

    private static ModuleSheetExcelExportService fixture(Spreadsheet source, String location) {
        def reader = new SheetsMetadataReaderService() {
            @Override Map<String, Object> openTab(String url, String gid) {
                assertEquals('42', gid)
                return [spreadsheetId: 'testSpreadsheet', sheetId: '42',
                        tabTitle: 'OOP', moduleLocation: location]
            }
        }
        return new ModuleSheetExcelExportService(reader) {
            @Override protected Spreadsheet loadGrid(Map context) { return source }
        }
    }

    private static Spreadsheet spreadsheet(List<RowData> rows) {
        def props = new SheetProperties().setSheetId(42).setTitle('OOP')
                .setGridProperties(new GridProperties().setFrozenRowCount(1))
        def sheet = new com.google.api.services.sheets.v4.model.Sheet()
                .setProperties(props).setData([new GridData().setStartRow(0)
                .setStartColumn(0).setRowData(rows)])
        return new Spreadsheet().setSheets([sheet])
    }

    private static RowData row(List<CellData> cells) { new RowData().setValues(cells) }
    private static CellData text(String content, boolean bold = false) {
        def cell = new CellData().setUserEnteredValue(new ExtendedValue().setStringValue(content))
        if (bold) cell.effectiveFormat = new CellFormat().setTextFormat(new TextFormat().setBold(true))
        return cell
    }
    private static CellData number(double value) {
        new CellData().setUserEnteredValue(new ExtendedValue().setNumberValue(value))
    }
    private static CellData bool(boolean value) {
        new CellData().setUserEnteredValue(new ExtendedValue().setBoolValue(value))
    }
    private static CellData formula(double cached) {
        new CellData().setUserEnteredValue(new ExtendedValue().setFormulaValue('=5*5'))
                .setEffectiveValue(new ExtendedValue().setNumberValue(cached))
    }
    private static void expectFailure(Closure work) {
        try { work.call(); fail('Expected GradleException') }
        catch (GradleException expected) { /* intended fail-closed behavior */ }
    }
}
