package com.example.learning.task.sheets.service.excel

import groovy.json.JsonSlurper
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.gradle.api.GradleException
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

import java.nio.file.Path
import java.security.MessageDigest

/** Shared checked input and Excel cell contract for all STEP 12 phases. */
class ModuleExcelContext implements Closeable {
    static final int MAX_CODEPOINTS = 30000
    static final int MAX_UTF16 = 32767
    final File module
    final String location
    final String language
    final String serviceName
    XSSFWorkbook workbook
    XSSFSheet sheet
    CellStyle bodyStyle
    CellStyle headerStyle
    List<Map> chapters = []
    Map<String, Map> assessment = [:]
    int writtenCells = 0

    ModuleExcelContext(File module, String location, String language, String serviceName) {
        this.module = module
        this.location = location
        this.language = language
        this.serviceName = serviceName
    }

    File file(String relative) {
        File target = new File(module, relative)
        if (!target.canonicalFile.toPath().startsWith(module.canonicalFile.toPath())) {
            throw new GradleException('Unsafe module XLSX source/destination: ' + relative)
        }
        return target
    }

    String read(String relative) {
        File source = file(relative)
        if (!source.isFile()) throw new GradleException('Missing STEP 12 input: ' + relative)
        return source.getText('UTF-8')
    }

    static Map yaml(String source, String label) {
        try {
            LoaderOptions options = new LoaderOptions()
            options.allowDuplicateKeys = false
            Object value = new Yaml(new SafeConstructor(options)).load(source)
            if (!(value instanceof Map)) throw new GradleException('Not a YAML map: ' + label)
            return value as Map
        } catch (Exception e) {
            throw new GradleException('Invalid STEP 12 YAML ' + label + ': ' + e.message, e)
        }
    }

    static String sha(byte[] bytes) {
        return MessageDigest.getInstance('SHA-256').digest(bytes).encodeHex().toString()
    }

    static List<String> chunks(String source, int count) {
        if (count < 1) throw new GradleException('Invalid XLSX fragment count.')
        String value = source ?: ''
        List<String> result = []
        int pos = 0
        while (pos < value.length()) {
            int from = pos
            int codepoints = 0
            int utf16 = 0
            while (pos < value.length()) {
                int size = Character.charCount(value.codePointAt(pos))
                if (codepoints == MAX_CODEPOINTS || utf16 + size > MAX_UTF16) break
                pos += size
                utf16 += size
                codepoints++
            }
            if (result.size() == count) {
                throw new GradleException('STEP 12 XLSX fragment capacity exceeded: ' +
                        value.length() + ' UTF-16 units; cells=' + count)
            }
            result.add(value.substring(from, pos))
        }
        while (result.size() < count) result.add('')
        return result
    }

    void fragments(int row, int col, int rows, int cols, String value) {
        List<String> pieces = chunks(value, rows * cols)
        pieces.eachWithIndex { String item, int i ->
            if (item) set(row + i.intdiv(cols), col + i % cols, item)
        }
    }

    String joined(int row, int col, int rows, int cols) {
        List<String> parts = []
        for (int r = row; r < row + rows; r++) {
            for (int c = col; c < col + cols; c++) parts.add(text(r, c))
        }
        return parts.join('')
    }

    void set(int row, int col, String value) {
        if (row < 1 || row > 1000 || col < 1 || col > 29) {
            throw new GradleException('STEP 12 cell outside A1:AC1000: ' + row + '/' + col)
        }
        if (value == null || value.length() > MAX_UTF16 ||
                value.codePointCount(0, value.length()) > MAX_CODEPOINTS) {
            throw new GradleException('STEP 12 oversized/null value at ' + row + '/' + col)
        }
        Row targetRow = sheet.getRow(row - 1) ?: sheet.createRow(row - 1)
        targetRow.heightInPoints = ExcelWorkbookService.ROW_HEIGHT_POINTS
        Cell cell = targetRow.getCell(col - 1) ?: targetRow.createCell(col - 1)
        if (cell.cellType != CellType.BLANK && cell.stringCellValue != value) {
            throw new GradleException('STEP 12 conflicting cell writer ' + row + '/' + col)
        }
        cell.setCellValue(value)
        if (row in [1, 2, 17, 18, 19]) {
            if (headerStyle) cell.cellStyle = headerStyle
        } else if (bodyStyle) {
            cell.cellStyle = bodyStyle
        }
        writtenCells++
    }

    String text(int row, int col) {
        Cell cell = sheet.getRow(row - 1)?.getCell(col - 1)
        if (cell == null || cell.cellType == CellType.BLANK) return ''
        if (cell.cellType != CellType.STRING) {
            throw new GradleException('Non-text STEP 12 cell ' + row + '/' + col)
        }
        return cell.stringCellValue
    }

    @Override
    void close() { workbook?.close() }
}
