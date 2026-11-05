package com.example.learning.task.sheets.service.excel

import org.apache.poi.ss.util.WorkbookUtil
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.xssf.usermodel.XSSFWorkbook

/** Phase 1: compact, fixed-size cells; long content remains intact but does not wrap. */
class ExcelWorkbookService {
    // Excel stores row height in points (60 CSS pixels at 96 DPI = 45 points).
    static final float ROW_HEIGHT_POINTS = 45f
    // Approximately 230 px using Calibri 11; POI stores column widths in 1/256 characters.
    static final int COLUMN_WIDTH_CHARACTERS = 32

    void fill(ModuleExcelContext ctx) {
        ctx.workbook = new XSSFWorkbook()
        ctx.sheet = ctx.workbook.createSheet(
                WorkbookUtil.createSafeSheetName(ctx.serviceName).take(31))
        // No frozen or split panes: users can scroll every row and column freely.
        ctx.sheet.defaultRowHeightInPoints = ROW_HEIGHT_POINTS
        ctx.bodyStyle = ctx.workbook.createCellStyle()
        ctx.bodyStyle.wrapText = false
        ctx.bodyStyle.verticalAlignment = VerticalAlignment.TOP
        ctx.headerStyle = ctx.workbook.createCellStyle()
        ctx.headerStyle.wrapText = false
        ctx.headerStyle.verticalAlignment = VerticalAlignment.TOP
        ctx.headerStyle.fillForegroundColor = IndexedColors.PALE_BLUE.index
        ctx.headerStyle.fillPattern = FillPatternType.SOLID_FOREGROUND
        def bold = ctx.workbook.createFont()
        bold.bold = true
        ctx.headerStyle.setFont(bold)
        // Set every schema column A:AC to the same bounded width, including AB.
        (0..<29).each { int col ->
            ctx.sheet.setColumnWidth(col, COLUMN_WIDTH_CHARACTERS * 256)
        }
    }
}
