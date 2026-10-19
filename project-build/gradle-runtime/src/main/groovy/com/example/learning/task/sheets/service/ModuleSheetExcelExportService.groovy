package com.example.learning.task.sheets.service

import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.*
import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.*
import org.apache.poi.xssf.usermodel.*
import org.gradle.api.GradleException

import java.nio.file.*
import java.util.regex.Pattern

/** Read-only, one-tab Google Sheets snapshot to module/content/sheet/{language}/module.xlsx. */
class ModuleSheetExcelExportService {
    private static final Pattern DOC = Pattern.compile('^/spreadsheets/d/([A-Za-z0-9_-]+)(?:/.*)?$')
    private static final Pattern GID = Pattern.compile('(?:^|[&#?])gid=([0-9]+)(?:$|[&#])')
    private final SheetsMetadataReaderService reader

    ModuleSheetExcelExportService(SheetsMetadataReaderService reader = new SheetsMetadataReaderService()) {
        this.reader = reader
    }

    Map execute(File rootDirectory, File moduleDirectory, String fileURL,
                String sheetURL, String language, boolean apply) {
        Map selected = validateUrls(fileURL, sheetURL)
        String lang = normalizeLanguage(language)
        File root = rootDirectory.canonicalFile
        File module = moduleDirectory.canonicalFile
        Path moduleRoot = new File(root, 'module').canonicalFile.toPath()
        if (module.toPath() == moduleRoot || !module.toPath().startsWith(moduleRoot) ||
                !module.isDirectory()) {
            throw new GradleException('Excel export must run inside a real module/: ' + module)
        }
        String expected = root.toPath().relativize(module.toPath()).toString()
                .replace(File.separatorChar, '/' as char)
        RoadmapSheetImportService.resolveDestination(root, expected)
        File output = new File(module, 'content/sheet/' + lang + '/module.xlsx')
        Path dest = output.toPath()
        Path cursor = module.toPath()
        for (Path segment : module.toPath().relativize(dest)) {
            cursor = cursor.resolve(segment)
            if (Files.isSymbolicLink(cursor)) {
                throw new GradleException('Excel destination contains a symlink: ' + cursor)
            }
        }
        if (Files.exists(dest, LinkOption.NOFOLLOW_LINKS) &&
                !Files.isRegularFile(dest, LinkOption.NOFOLLOW_LINKS)) {
            throw new GradleException('Excel destination is not a regular file: ' + dest)
        }
        Map context = reader.openTab(fileURL, selected.gid as String)
        if (context.spreadsheetId != selected.document || context.sheetId != selected.gid) {
            throw new GradleException('Tab identity mismatch after URL verification.')
        }
        if (context.moduleLocation != expected) {
            throw new GradleException('Excel export I3 mismatch: module=' + expected +
                    ', Sheet=' + context.moduleLocation + ', gid=' + selected.gid)
        }
        Spreadsheet response = loadGrid(context)
        List tabs = response.sheets ?: []
        if (tabs.size() != 1 || tabs[0].properties?.sheetId?.toString() != selected.gid) {
            throw new GradleException('Google API returned unexpected tab(s).')
        }
        Map stats = [cells: 0, unsupportedValidations: 0]
        XSSFWorkbook book = new XSSFWorkbook()
        try {
            render(book, tabs[0], stats)
            if (apply) saveVerified(dest, book, stats.cells as int)
            return [status: apply ? 'EXPORTED' : 'DRY_RUN', tab: context.tabTitle,
                    gid: selected.gid, location: expected, language: lang, file: output,
                    cells: stats.cells, unsupportedValidations: stats.unsupportedValidations]
        }
        finally { book.close() }
    }

    /** Range is one selected tab; field mask excludes other sheets and unused metadata. */
    protected Spreadsheet loadGrid(Map context) {
        Sheets client = context.client as Sheets
        String tab = "'" + (context.tabTitle as String).replace("'", "''") + "'"
        return client.spreadsheets().get(context.spreadsheetId as String)
                .setRanges([tab])
                .setFields('sheets(properties(sheetId,title,gridProperties(frozenRowCount,' +
                        'frozenColumnCount)),merges,data(startRow,startColumn,' +
                        'rowData(values(userEnteredValue,effectiveValue,effectiveFormat,note,' +
                        'dataValidation)),rowMetadata(pixelSize,hiddenByUser),' +
                        'columnMetadata(pixelSize,hiddenByUser)))').execute()
    }

    static Map validateUrls(String fileURL, String sheetURL) {
        URI file = parseURL(fileURL, 'fileURL')
        URI tab = parseURL(sheetURL, 'sheetURL')
        String id = docId(file, 'fileURL')
        if (id != docId(tab, 'sheetURL')) {
            throw new GradleException('sheetURL must be a tab inside fileURL.')
        }
        def match = GID.matcher((tab.rawQuery ?: '') + '#' + (tab.rawFragment ?: ''))
        if (!match.find()) throw new GradleException('sheetURL requires a numeric #gid=...')
        BigInteger gid = new BigInteger(match.group(1))
        if (gid > Integer.MAX_VALUE) throw new GradleException('gid exceeds int range.')
        return [document: id, gid: gid.toString()]
    }

    private static URI parseURL(String input, String label) {
        try {
            URI uri = new URI(input?.trim())
            if (uri.scheme != 'https' || uri.host != 'docs.google.com' ||
                    uri.port != -1 || uri.userInfo != null) throw new IllegalArgumentException()
            return uri
        }
        catch (Exception ignored) {
            throw new GradleException(label + ' must be an HTTPS docs.google.com URL.')
        }
    }

    private static String docId(URI uri, String label) {
        def match = DOC.matcher(uri.path ?: '')
        if (!match.matches()) throw new GradleException(label + ' must be a spreadsheet URL.')
        return match.group(1)
    }

    static String normalizeLanguage(String language) {
        String lang = language?.trim()?.toLowerCase(Locale.ROOT)
        if (!(lang ==~ /[a-z]{2}(?:-[a-z]{2})?/)) {
            throw new GradleException('Invalid language: ' + language)
        }
        return lang
    }

    private static void render(XSSFWorkbook book, def source, Map stats) {
        def sheet = book.createSheet(WorkbookUtil.createSafeSheetName(
                source.properties?.title ?: 'Sheet'))
        def frozen = source.properties?.gridProperties
        if ((frozen?.frozenColumnCount ?: 0) > 0 || (frozen?.frozenRowCount ?: 0) > 0) {
            sheet.createFreezePane(frozen?.frozenColumnCount ?: 0, frozen?.frozenRowCount ?: 0)
        }
        Map<String, XSSFCellStyle> styles = [:]
        Map<String, XSSFFont> fonts = [:]
        (source.data ?: []).each { GridData block ->
            int startRow = block.startRow ?: 0
            int startCol = block.startColumn ?: 0
            (block.columnMetadata ?: []).eachWithIndex { DimensionProperties meta, int i ->
                int col = startCol + i
                if (col >= 16384) throw new GradleException('Excel column limit exceeded.')
                if (meta.pixelSize != null) {
                    sheet.setColumnWidth(col, Math.min(65280,
                            Math.max(256, Math.round(meta.pixelSize / 7d * 256) as int)))
                }
                if (meta.hiddenByUser == true) sheet.setColumnHidden(col, true)
            }
            (block.rowData ?: []).eachWithIndex { RowData data, int i ->
                int rowNo = startRow + i
                if (rowNo >= 1048576) throw new GradleException('Excel row limit exceeded.')
                def row = null
                (data.values ?: []).eachWithIndex { CellData cell, int j ->
                    int col = startCol + j
                    if (col >= 16384) throw new GradleException('Excel column limit exceeded.')
                    if (cell == null || (cell.userEnteredValue == null && cell.effectiveValue == null &&
                            cell.effectiveFormat == null && cell.note == null &&
                            cell.dataValidation == null)) return
                    if (row == null) row = sheet.getRow(rowNo) ?: sheet.createRow(rowNo)
                    def target = row.createCell(col)
                    ExtendedValue value = cell.userEnteredValue
                    if (value?.formulaValue != null) {
                        value = cell.effectiveValue
                        if (value == null || value.errorValue != null) {
                            throw new GradleException('Formula cannot be snapshotted: ' + coord(rowNo, col))
                        }
                    }
                    if (value?.stringValue != null) {
                        if (value.stringValue.length() > 32767) {
                            throw new GradleException('Excel 32767 char limit at ' + coord(rowNo, col))
                        }
                        target.setCellValue(value.stringValue)
                        stats.cells++
                    }
                    else if (value?.numberValue != null) {
                        target.setCellValue(value.numberValue as double)
                        stats.cells++
                    }
                    else if (value?.boolValue != null) {
                        target.setCellValue(value.boolValue as boolean)
                        stats.cells++
                    }
                    else if (value?.errorValue != null) {
                        throw new GradleException('Sheet error at ' + coord(rowNo, col))
                    }
                    if (cell.effectiveFormat != null) {
                        String key = cell.effectiveFormat.toString()
                        if (!styles.containsKey(key)) styles[key] = createStyle(book, cell.effectiveFormat, fonts)
                        target.cellStyle = styles[key]
                    }
                    if (cell.note) {
                        def helper = book.creationHelper
                        def comment = sheet.createDrawingPatriarch().createCellComment(helper.createClientAnchor())
                        comment.string = helper.createRichTextString(cell.note)
                        target.cellComment = comment
                    }
                    if (cell.dataValidation != null) applyList(sheet, cell.dataValidation, rowNo, col, stats)
                }
                DimensionProperties meta = (block.rowMetadata ?: []).size() > i ? block.rowMetadata[i] : null
                if (meta != null && (meta.pixelSize != null || meta.hiddenByUser == true)) {
                    if (row == null) row = sheet.getRow(rowNo) ?: sheet.createRow(rowNo)
                    if (meta.pixelSize != null) row.heightInPoints = meta.pixelSize * 72f / 96f
                    if (meta.hiddenByUser == true) row.zeroHeight = true
                }
            }
        }
        (source.merges ?: []).each { GridRange range ->
            if (range.sheetId != null && range.sheetId != source.properties.sheetId) {
                throw new GradleException('Merge references another tab.')
            }
            int r1 = range.startRowIndex ?: 0, r2 = (range.endRowIndex ?: r1 + 1) - 1
            int c1 = range.startColumnIndex ?: 0, c2 = (range.endColumnIndex ?: c1 + 1) - 1
            if (r1 < r2 || c1 < c2) {
                sheet.addMergedRegion(new CellRangeAddress(r1, r2, c1, c2))
            }
        }
    }

    private static XSSFCellStyle createStyle(XSSFWorkbook book, CellFormat format,
                                              Map<String, XSSFFont> fonts) {
        XSSFCellStyle style = book.createCellStyle()
        if (format.backgroundColor != null) {
            style.setFillForegroundColor(rgb(format.backgroundColor))
            style.fillPattern = FillPatternType.SOLID_FOREGROUND
        }
        def text = format.textFormat
        if (text != null) {
            String key = text.toString()
            if (!fonts.containsKey(key)) {
                XSSFFont font = book.createFont()
                if (text.fontFamily) font.fontName = text.fontFamily
                if (text.fontSize) font.fontHeightInPoints = text.fontSize as short
                font.bold = text.bold == true
                font.italic = text.italic == true
                font.strikeout = text.strikethrough == true
                if (text.foregroundColor != null) font.setColor(rgb(text.foregroundColor))
                fonts[key] = font
            }
            style.setFont(fonts[key])
        }
        if (format.horizontalAlignment) {
            try { style.alignment = HorizontalAlignment.valueOf(format.horizontalAlignment) }
            catch (IllegalArgumentException ignored) { }
        }
        if (format.verticalAlignment) {
            String value = format.verticalAlignment == 'MIDDLE' ? 'CENTER' : format.verticalAlignment
            try { style.verticalAlignment = VerticalAlignment.valueOf(value) }
            catch (IllegalArgumentException ignored) { }
        }
        style.wrapText = format.wrapStrategy == 'WRAP'
        if (format.numberFormat?.pattern) {
            style.dataFormat = book.createDataFormat().getFormat(format.numberFormat.pattern)
        }
        ['top': 'Top', 'bottom': 'Bottom', 'left': 'Left', 'right': 'Right'].each { side, label ->
            def border = format.borders?."$side"
            BorderStyle type = borderType(border?.style)
            if (type != BorderStyle.NONE) {
                style.invokeMethod('setBorder' + label, type)
                if (border.color != null) {
                    style.invokeMethod('set' + label + 'BorderColor', rgb(border.color))
                }
            }
        }
        return style
    }

    private static BorderStyle borderType(String value) {
        switch (value) {
            case 'SOLID': return BorderStyle.THIN
            case 'SOLID_MEDIUM': return BorderStyle.MEDIUM
            case 'SOLID_THICK': return BorderStyle.THICK
            case 'DASHED': return BorderStyle.DASHED
            case 'DOTTED': return BorderStyle.DOTTED
            case 'DOUBLE': return BorderStyle.DOUBLE
            default: return BorderStyle.NONE
        }
    }

    private static XSSFColor rgb(def color) {
        int r = Math.max(0, Math.min(255, Math.round((color.red ?: 0d) * 255) as int))
        int g = Math.max(0, Math.min(255, Math.round((color.green ?: 0d) * 255) as int))
        int b = Math.max(0, Math.min(255, Math.round((color.blue ?: 0d) * 255) as int))
        return new XSSFColor(new java.awt.Color(r, g, b), null)
    }

    private static void applyList(def sheet, def rule, int r, int c, Map stats) {
        if (rule.condition?.type != 'ONE_OF_LIST') { stats.unsupportedValidations++; return }
        String[] options = (rule.condition.values ?: []).collect { it.userEnteredValue ?: '' } as String[]
        if (!options || options.any { it.contains(',') } || options.join(',').length() > 255) {
            stats.unsupportedValidations++; return
        }
        def helper = sheet.dataValidationHelper
        def validation = helper.createValidation(helper.createExplicitListConstraint(options),
                new CellRangeAddressList(r, r, c, c))
        validation.showErrorBox = rule.strict == true
        sheet.addValidationData(validation)
    }

    private static String coord(int r, int c) {
        return CellReference.convertNumToColString(c) + (r + 1)
    }

    private static void saveVerified(Path target, XSSFWorkbook book, int expectedCount) {
        Files.createDirectories(target.parent)
        Path temp = Files.createTempFile(target.parent, '.sheet-excel-', '.xlsx')
        try {
            Files.newOutputStream(temp).withCloseable { out -> book.write(out) }
            Files.newInputStream(temp).withCloseable { stream ->
                new XSSFWorkbook(stream).withCloseable { actual ->
                    if (actual.numberOfSheets != 1) {
                        throw new GradleException('XLSX verification expected one worksheet.')
                    }
                    int count = 0
                    actual.getSheetAt(0).each { row ->
                        row.each { cell -> if (cell.cellType != CellType.BLANK) count++ }
                    }
                    if (count != expectedCount) {
                        throw new GradleException('XLSX verification count mismatch: ' + count +
                                '/' + expectedCount)
                    }
                }
            }
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)
        }
        finally { Files.deleteIfExists(temp) }
    }
}
