package com.example.learning.task.sheets.service

import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.*
import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.*
import org.gradle.api.GradleException

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * One local Excel workbook -> a NEW Google Sheets tab in the given spreadsheet.
 * No gid input, no mutation/deletion of any pre-existing tab.
 */
class ModuleSheetExcelImportService {
    // Keep request payloads comfortably under the Google Sheets ~2 MB guideline,
    // while avoiding dozens of API writes for content-heavy module tabs.
    private static final int MAX_BATCH_BYTES = 1_250_000
    private final SheetsMetadataReaderService reader

    ModuleSheetExcelImportService(SheetsMetadataReaderService reader = new SheetsMetadataReaderService()) {
        this.reader = reader
    }

    Map execute(File rootDirectory, File moduleDirectory, String fileUrl,
                String language, boolean apply) {
        File root = rootDirectory.canonicalFile
        File module = moduleDirectory.canonicalFile
        Path modulesRoot = new File(root, 'module').canonicalFile.toPath()
        if (module.toPath() == modulesRoot || !module.toPath().startsWith(modulesRoot) ||
                !module.isDirectory()) {
            throw new GradleException('Excel import must run inside a registered module/: ' + module)
        }
        String location = root.toPath().relativize(module.toPath()).toString()
                .replace(File.separatorChar, '/' as char)
        RoadmapSheetImportService.resolveDestination(root, location)
        String lang = ModuleSheetExcelExportService.normalizeLanguage(language)
        File input = new File(module, 'content/sheet/' + lang + '/module.xlsx')
        validatePath(module.toPath(), input.toPath())

        // Preflight every cell, format, merge, dimension and module binding BEFORE
        // acquiring write scope or creating a tab; failures cannot leave empty tabs.
        Map plan = prepare(input, location)
        Map context = apply ? reader.openSpreadsheetForWrite(fileUrl) :
                reader.openSpreadsheet(fileUrl)
        String newTitle = availableTitle(plan.title as String, context.tabs as List<Map>)
        List<List<Request>> batches = partition(plan.requests as List<Request>)
        Map result = [status: apply ? 'IMPORTED' : 'DRY_RUN', location: location,
                      file: input, tab: newTitle, language: lang, cells: plan.cells,
                      batches: batches.size(), gid: null, tabUrl: null]
        if (!apply) return result

        Integer created = null
        try {
            created = addTab(context, newTitle, plan.rows as int, plan.columns as int,
                    plan.frozenRows as int, plan.frozenColumns as int)
            if (created == null || created < 0) {
                throw new GradleException('Google Sheets did not return the new tab gid.')
            }
            batches.each { List<Request> batch ->
                batch.each { Request item ->
                    ModuleSheetExcelImportService.setSheetId(item, created)
                }
                sendBatch(context, batch)
            }
            verifyNewTab(context, created, newTitle, location)
            result.gid = created.toString()
            result.tabUrl = 'https://docs.google.com/spreadsheets/d/' +
                    context.spreadsheetId + '/edit#gid=' + created
            return result
        }
        catch (Exception failure) {
            if (created != null) {
                try {
                    deleteTab(context, created)
                }
                catch (Exception rollback) {
                    throw new GradleException('Excel import failed and rollback FAILED for new gid=' +
                            created + '. Manually inspect the NEW tab. Original error: ' +
                            failure.message + '. Rollback error: ' + rollback.message, failure)
                }
            }
            throw new GradleException('Excel import aborted; pre-existing tabs were not modified. ' +
                    (created == null ? 'No tab was created. ' : 'New tab was rolled back. ') +
                    'Cause: ' + failure.message, failure)
        }
    }

    private static void validatePath(Path module, Path file) {
        Path current = module
        for (Path item : module.relativize(file)) {
            current = current.resolve(item)
            if (Files.isSymbolicLink(current)) {
                throw new GradleException('Refusing symlinked Excel source: ' + current)
            }
        }
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new GradleException('Missing or invalid offline Excel file: ' + file)
        }
    }

    /** Select non-destructively; deterministic suffix when the original name exists. */
    static String availableTitle(String desired, List<Map> tabs) {
        if (!desired?.trim()) throw new GradleException('Excel worksheet name is blank.')
        Set<String> existing = (tabs ?: []).collect {
            (it.title ?: '').toString().toLowerCase(Locale.ROOT)
        } as Set<String>
        if (!existing.contains(desired.toLowerCase(Locale.ROOT))) return desired
        for (int i = 1; i <= 9999; i++) {
            String suffix = ' (import ' + i + ')'
            String truncated = desired.take(100 - suffix.length()).trim()
            String candidate = truncated + suffix
            if (!existing.contains(candidate.toLowerCase(Locale.ROOT))) return candidate
        }
        throw new GradleException('No free tab title for Excel worksheet: ' + desired)
    }

    /** Testable pure preflight: no Google API or local mutation. */
    static Map prepare(File file, String expectedModuleLocation) {
        try {
            return file.withInputStream { input ->
                new XSSFWorkbook(input).withCloseable { XSSFWorkbook book ->
                    if (book.numberOfSheets != 1) {
                        throw new GradleException('module.xlsx must contain exactly one worksheet.')
                    }
                    XSSFSheet sheet = book.getSheetAt(0)
                    def binding = sheet.getRow(2)?.getCell(8)
                    if (binding == null || binding.cellType != CellType.STRING ||
                            binding.stringCellValue != expectedModuleLocation) {
                        throw new GradleException('Excel I3 mismatch: expected module=' +
                                expectedModuleLocation + ', actual=' +
                                (binding?.toString() ?: '[blank]'))
                    }
                    return createPlan(book, sheet)
                }
            }
        }
        catch (GradleException error) { throw error }
        catch (Exception error) {
            throw new GradleException('Cannot read/validate module.xlsx: ' + error.message, error)
        }
    }

    private static Map createPlan(XSSFWorkbook book, XSSFSheet sheet) {
        List<Request> requests = []
        int maxColumn = Math.max(9, sheet.getDefaultColumnWidth())
        int cellCount = 0
        int lastRow = sheet.lastRowNum
        if (lastRow >= 1_000_000) throw new GradleException('Excel grid exceeds Google Sheets size limits.')

        for (Row row : sheet) {
            if (row.rowNum >= 1_000_000) throw new GradleException('Row exceeds Google Sheets limit.')
            if (row.lastCellNum < 0) continue
            int first = Integer.MAX_VALUE, last = -1
            Map<Integer, CellData> mapped = [:]
            for (Cell cell : row) {
                if (cell.columnIndex >= 18278) {
                    throw new GradleException('Column exceeds Google Sheets limit.')
                }
                CellData data = toGoogleCell(book, cell)
                if (data == null) continue
                first = Math.min(first, cell.columnIndex)
                last = Math.max(last, cell.columnIndex)
                mapped[cell.columnIndex] = data
                if (cell.cellType != CellType.BLANK) cellCount++
            }
            if (last >= 0) {
                maxColumn = Math.max(maxColumn, last + 1)
                List<CellData> cells = (first..last).collect { int col ->
                    mapped[col] ?: new CellData()
                }
                requests.add(new Request().setUpdateCells(new UpdateCellsRequest()
                        .setStart(new GridCoordinate().setSheetId(0)
                                .setRowIndex(row.rowNum).setColumnIndex(first))
                        .setRows([new RowData().setValues(cells)])
                        .setFields('userEnteredValue,userEnteredFormat,note')))
            }
            float height = row.heightInPoints
            if (height != sheet.defaultRowHeightInPoints || row.zeroHeight) {
                DimensionProperties properties = new DimensionProperties()
                List<String> fields = []
                if (height != sheet.defaultRowHeightInPoints) {
                    properties.pixelSize = Math.round(height * 96f / 72f)
                    fields.add('pixelSize')
                }
                if (row.zeroHeight) {
                    properties.hiddenByUser = true
                    fields.add('hiddenByUser')
                }
                requests.add(dimension('ROWS', row.rowNum, properties, fields.join(',')))
            }
        }
        for (int i = 0; i < sheet.numMergedRegions; i++) {
            CellRangeAddress merge = sheet.getMergedRegion(i)
            maxColumn = Math.max(maxColumn, merge.lastColumn + 1)
            lastRow = Math.max(lastRow, merge.lastRow)
        }
        for (int col = 0; col < maxColumn; col++) {
            boolean hidden = sheet.isColumnHidden(col)
            boolean customWidth = sheet.getColumnWidth(col) != sheet.defaultColumnWidth * 256
            if (!hidden && !customWidth) continue
            DimensionProperties props = new DimensionProperties()
            List<String> fields = []
            if (customWidth) {
                props.pixelSize = Math.max(1, Math.round(sheet.getColumnWidthInPixels(col)))
                fields.add('pixelSize')
            }
            if (hidden) { props.hiddenByUser = true; fields.add('hiddenByUser') }
            requests.add(dimension('COLUMNS', col, props, fields.join(',')))
        }
        for (int i = 0; i < sheet.numMergedRegions; i++) {
            CellRangeAddress merge = sheet.getMergedRegion(i)
            requests.add(new Request().setMergeCells(new MergeCellsRequest()
                    .setMergeType('MERGE_ALL').setRange(new GridRange().setSheetId(0)
                    .setStartRowIndex(merge.firstRow).setEndRowIndex(merge.lastRow + 1)
                    .setStartColumnIndex(merge.firstColumn).setEndColumnIndex(merge.lastColumn + 1))))
        }
        for (DataValidation dv : sheet.dataValidations) {
            String[] options = dv.validationConstraint?.explicitListValues
            if (dv.validationConstraint?.validationType !=
                    DataValidationConstraint.ValidationType.LIST || !options) {
                throw new GradleException('Unsupported Excel dropdown formula/rule; ' +
                        'import aborted to avoid silent data loss.')
            }
            BooleanCondition cond = new BooleanCondition().setType('ONE_OF_LIST')
                    .setValues(options.collect { new ConditionValue().setUserEnteredValue(it) })
            for (CellRangeAddress range : dv.regions.cellRangeAddresses) {
                lastRow = Math.max(lastRow, range.lastRow)
                maxColumn = Math.max(maxColumn, range.lastColumn + 1)
                requests.add(new Request().setSetDataValidation(new SetDataValidationRequest()
                        .setRange(new GridRange().setSheetId(0)
                                .setStartRowIndex(range.firstRow).setEndRowIndex(range.lastRow + 1)
                                .setStartColumnIndex(range.firstColumn)
                                .setEndColumnIndex(range.lastColumn + 1))
                        .setRule(new DataValidationRule().setCondition(cond)
                                .setStrict(true).setShowCustomUi(true))))
            }
        }
        def pane = sheet.paneInformation
        return [title: sheet.sheetName,
                rows: Math.max(1000, lastRow + 1),
                columns: Math.max(26, maxColumn),
                frozenRows: pane?.isFreezePane() ? (pane.horizontalSplitPosition as int) : 0,
                frozenColumns: pane?.isFreezePane() ? (pane.verticalSplitPosition as int) : 0,
                cells: cellCount, requests: requests]
    }

    private static Request dimension(String dimension, int index,
                                     DimensionProperties props, String fields) {
        return new Request().setUpdateDimensionProperties(new UpdateDimensionPropertiesRequest()
                .setRange(new DimensionRange().setSheetId(0)
                        .setDimension(dimension).setStartIndex(index).setEndIndex(index + 1))
                .setProperties(props).setFields(fields))
    }

    private static CellData toGoogleCell(XSSFWorkbook book, Cell cell) {
        if (cell.cellType == CellType.FORMULA || cell.cellType == CellType.ERROR) {
            throw new GradleException('Cannot safely import live formula/error at ' +
                    cell.address + '; export static values instead.')
        }
        CellData result = new CellData()
        switch (cell.cellType) {
            case CellType.STRING:
                result.userEnteredValue = new ExtendedValue().setStringValue(cell.stringCellValue)
                break
            case CellType.NUMERIC:
                result.userEnteredValue = new ExtendedValue().setNumberValue(cell.numericCellValue)
                break
            case CellType.BOOLEAN:
                result.userEnteredValue = new ExtendedValue().setBoolValue(cell.booleanCellValue)
                break
            case CellType.BLANK: break
            default: throw new GradleException('Unsupported Excel type at ' + cell.address)
        }
        def comment = cell.getCellComment()
        if (comment?.string?.string) result.note = comment.string.string
        CellFormat format = toGoogleFormat(book, cell.cellStyle)
        if (format != null) result.userEnteredFormat = format
        return (result.userEnteredValue != null || result.note != null ||
                result.userEnteredFormat != null) ? result : null
    }

    private static CellFormat toGoogleFormat(XSSFWorkbook book, CellStyle style) {
        if (style == null) return null
        CellFormat format = new CellFormat()
        XSSFFont font = book.getFontAt(style.fontIndexAsInt)
        TextFormat text = new TextFormat()
        if (font.fontName) text.fontFamily = font.fontName
        if (font.fontHeightInPoints > 0) text.fontSize = font.fontHeightInPoints as int
        if (font.bold) text.bold = true
        if (font.italic) text.italic = true
        if (font.strikeout) text.strikethrough = true
        if (font.color != null && font instanceof XSSFFont) {
            def foreground = rgb(font.getXSSFColor())
            if (foreground != null) text.foregroundColor = foreground
        }
        format.textFormat = text
        if (style instanceof XSSFCellStyle &&
                style.fillPattern == FillPatternType.SOLID_FOREGROUND) {
            def background = rgb(style.fillForegroundXSSFColor)
            if (background != null) format.backgroundColor = background
        }
        if (style.alignment != null) {
            format.horizontalAlignment = style.alignment.name()
        }
        if (style.verticalAlignment != null) {
            format.verticalAlignment = style.verticalAlignment.name() == 'CENTER' ?
                    'MIDDLE' : style.verticalAlignment.name()
        }
        format.wrapStrategy = style.wrapText ? 'WRAP' : 'CLIP'
        String numberPattern = style.dataFormatString
        if (numberPattern && numberPattern != 'General') {
            format.numberFormat = new NumberFormat().setPattern(numberPattern)
                    .setType(DateUtil.isADateFormat(style.dataFormat, numberPattern) ? 'DATE' : 'NUMBER')
        }
        Borders borders = new Borders()
        borders.top = border(style.borderTop)
        borders.bottom = border(style.borderBottom)
        borders.left = border(style.borderLeft)
        borders.right = border(style.borderRight)
        format.borders = borders
        return format
    }

    private static com.google.api.services.sheets.v4.model.Border border(BorderStyle style) {
        switch (style) {
            case BorderStyle.THIN: return new com.google.api.services.sheets.v4.model.Border().setStyle('SOLID')
            case BorderStyle.MEDIUM: return new com.google.api.services.sheets.v4.model.Border().setStyle('SOLID_MEDIUM')
            case BorderStyle.THICK: return new com.google.api.services.sheets.v4.model.Border().setStyle('SOLID_THICK')
            case BorderStyle.DOTTED: return new com.google.api.services.sheets.v4.model.Border().setStyle('DOTTED')
            case BorderStyle.DASHED: return new com.google.api.services.sheets.v4.model.Border().setStyle('DASHED')
            case BorderStyle.DOUBLE: return new com.google.api.services.sheets.v4.model.Border().setStyle('DOUBLE')
            default: return null
        }
    }

    private static com.google.api.services.sheets.v4.model.Color rgb(XSSFColor source) {
        byte[] data = source?.RGB
        if (data == null || data.length < 3) return null
        return new com.google.api.services.sheets.v4.model.Color()
                .setRed(((data[0] & 0xff) / 255.0d) as Float)
                .setGreen(((data[1] & 0xff) / 255.0d) as Float)
                .setBlue(((data[2] & 0xff) / 255.0d) as Float)
    }

    static List<List<Request>> partition(List<Request> requests) {
        List<List<Request>> batches = []
        List<Request> current = []
        int currentBytes = 0
        requests.each { Request item ->
            int bytes = item.toString().getBytes('UTF-8').length
            if (!current.isEmpty() && currentBytes + bytes > MAX_BATCH_BYTES) {
                batches.add(current)
                current = []
                currentBytes = 0
            }
            current.add(item)
            currentBytes += bytes
        }
        if (!current.isEmpty()) batches.add(current)
        return batches
    }

    private static void setSheetId(Request request, int id) {
        if (request.updateCells) request.updateCells.start.sheetId = id
        else if (request.updateDimensionProperties) request.updateDimensionProperties.range.sheetId = id
        else if (request.mergeCells) request.mergeCells.range.sheetId = id
        else if (request.setDataValidation) request.setDataValidation.range.sheetId = id
        else throw new GradleException('Unsupported preflight Sheets request.')
    }

    protected int addTab(Map ctx, String title, int rows, int cols, int frozenRows, int frozenCols) {
        Sheets client = ctx.client as Sheets
        SheetProperties sheet = new SheetProperties().setTitle(title)
                .setGridProperties(new GridProperties().setRowCount(rows).setColumnCount(cols)
                        .setFrozenRowCount(frozenRows).setFrozenColumnCount(frozenCols))
        BatchUpdateSpreadsheetResponse response = client.spreadsheets()
                .batchUpdate(ctx.spreadsheetId as String,
                        new BatchUpdateSpreadsheetRequest().setRequests(
                                [new Request().setAddSheet(new AddSheetRequest().setProperties(sheet))]))
                .execute()
        return response.replies?.first()?.addSheet?.properties?.sheetId
    }

    protected void sendBatch(Map ctx, List<Request> batch) {
        (ctx.client as Sheets).spreadsheets().batchUpdate(ctx.spreadsheetId as String,
                new BatchUpdateSpreadsheetRequest().setRequests(batch)).execute()
    }

    protected void verifyNewTab(Map ctx, int gid, String title, String expected) {
        Sheets client = ctx.client as Sheets
        String quoted = "'" + title.replace("'", "''") + "'"
        def response = client.spreadsheets().get(ctx.spreadsheetId as String)
                .setFields('sheets(properties(sheetId,title))').execute()
        if (!(response.sheets ?: []).any {
            it.properties?.sheetId == gid && it.properties?.title == title
        }) throw new GradleException('Created tab metadata readback failed.')
        def values = client.spreadsheets().values()
                .get(ctx.spreadsheetId as String, quoted + '!I3').execute().values
        if (!values || !values[0] || values[0][0]?.toString() != expected) {
            throw new GradleException('New tab I3 readback failed.')
        }
    }

    protected void deleteTab(Map ctx, int gid) {
        (ctx.client as Sheets).spreadsheets().batchUpdate(ctx.spreadsheetId as String,
                new BatchUpdateSpreadsheetRequest().setRequests(
                        [new Request().setDeleteSheet(new DeleteSheetRequest().setSheetId(gid))]))
                .execute()
    }
}
