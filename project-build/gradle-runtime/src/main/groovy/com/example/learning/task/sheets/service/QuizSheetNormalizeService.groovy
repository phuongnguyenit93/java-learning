package com.example.learning.task.sheets.service

import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.*
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import com.example.learning.utils.schema.validate.ContractSchemaValidateUtils
import org.gradle.api.GradleException
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/**
 * Deterministic Quiz normalization on the SAME Google Sheet:
 * Author-only Quiz YAML -> canonical Quiz YAML (ID, governance and links).
 * Accepts JSON-compatible source but always emits YAML.
 * No AI and no module filesystem writes.
 */
class QuizSheetNormalizeService {
    static final int CELL_LIMIT = 30000
    private final SheetsMetadataReaderService reader

    QuizSheetNormalizeService(
            SheetsMetadataReaderService reader = new SheetsMetadataReaderService()
    ) {
        this.reader = reader
    }

    Map execute(String fileUrl, String sheetId, boolean apply) {
        Map context = apply
                ? reader.openTabForWrite(fileUrl, sheetId)
                : reader.openTab(fileUrl, sheetId)
        Sheets client = context.client as Sheets
        String id = context.spreadsheetId as String
        int gid = Integer.parseInt(context.sheetId as String)
        String title = context.tabTitle as String
        String tab = "'" + title.replace("'", "''") + "'"

        Map<Integer, Map<Integer, CellData>> snapshot = fetch(client, id, tab, gid)
        checkHeader(snapshot)
        Map menu = ReadmeSheetImportService.parseMenu(menuRows(snapshot))
        if (menu.status == 'SKIPPED_EMPTY_MENU') {
            return report('SKIPPED_EMPTY_MENU', context, 0, 0, 0, 0, 0, 0, 'N/A')
        }
        if (menu.status != 'READY') {
            throw new GradleException('Quiz normalization requires complete filenames: ' + menu.status)
        }
        List<Map> groups = menu.groups as List<Map>
        Set<Integer> parents = groups.collect { (it.parentRow as Number).intValue() } as Set
        snapshot.each { Integer zeroRow, Map<Integer, CellData> cells ->
            int row = zeroRow + 1
            if (row >= 20 && !parents.contains(row) &&
                    (19..22).any { int col -> occupied(cells[col]) }) {
                throw new GradleException('Orphan Quiz data at non-parent row ' + row)
            }
        }

        List<Integer> blankParents = groups.findAll { Map parent ->
            fragments(snapshot, (parent.parentRow as Number).intValue()).every { it.isEmpty() }
        }.collect { (it.parentRow as Number).intValue() }
        if (blankParents.size() == groups.size()) {
            return report('SKIPPED_EMPTY_QUIZ', context, groups.size(), 0, 0,
                    0, 0, 0, 'N/A')
        }
        if (!blankParents.isEmpty()) {
            throw new GradleException('Incomplete Quiz: missing parent rows ' + blankParents)
        }

        List<Map> updates = []
        int populated = 0
        int questionCount = 0
        groups.eachWithIndex { Map parent, int index ->
            int row = (parent.parentRow as Number).intValue()
            List<String> oldPieces = fragments(snapshot, row)
            if (oldPieces.every { it.isEmpty() }) return
            populated++
            String filename = parent.filename as String
            String expectedFile = (index + 1) + '.' +
                    filename.substring(0, filename.length() - 3) + '/' + filename
            String stem = filename.substring(0, filename.length() - 3)
            Map normalized = normalizeYaml(joinFragments(oldPieces, row), expectedFile, stem, row)
            questionCount += (normalized.questions as List).size()
            List<String> newPieces = splitFragments(
                    renderYaml(normalized), row
            )
            if (oldPieces != newPieces) {
                (19..22).each { int col ->
                    if (protectedMetadata(snapshot[row - 1]?.get(col))) {
                        throw new GradleException('Quiz target has formula/note/validation/chip at row ' + row)
                    }
                }
                updates.add([row: row, values: newPieces, file: expectedFile])
            }
        }
        if (!apply || updates.isEmpty()) {
            return report(apply ? 'UNCHANGED' : 'DRY_RUN',
                    context, groups.size(), populated, updates.size(),
                    populated - updates.size(), questionCount, 0,
                    apply ? 'PASS' : 'N/A')
        }

        // All rows are validated before writing. Recheck all source/target
        // cells immediately before one atomic batchUpdate.
        if (fingerprint(snapshot) != fingerprint(fetch(client, id, tab, gid))) {
            throw new GradleException('Sheet changed during Quiz preflight; no write dispatched.')
        }
        List<Request> requests = updates.collect { Map update ->
            int row = update.row as int
            List<CellData> newCells = (update.values as List<String>).collect { String text ->
                text ? new CellData().setUserEnteredValue(
                        new ExtendedValue().setStringValue(text)) : new CellData()
            }
            new Request().setUpdateCells(new UpdateCellsRequest()
                    .setRange(new GridRange().setSheetId(gid)
                            .setStartRowIndex(row - 1).setEndRowIndex(row)
                            .setStartColumnIndex(19).setEndColumnIndex(23))
                    .setRows([new RowData().setValues(newCells)])
                    .setFields('userEnteredValue'))
        }
        client.spreadsheets().batchUpdate(id,
                new BatchUpdateSpreadsheetRequest().setRequests(requests)).execute()

        Map<Integer, Map<Integer, CellData>> after = fetch(client, id, tab, gid)
        updates.each { Map update ->
            int row = update.row as int
            List<String> actual = fragments(after, row)
            if (actual != update.values) {
                throw new GradleException(
                        'Exact Quiz readback FAILED at T' + row + ':W' + row +
                                '; write may have occurred.'
                )
            }
            String storedFile = update.file as String
            String stem = storedFile.substring(storedFile.lastIndexOf('/') + 1, storedFile.length() - 3)
            normalizeYaml(joinFragments(actual, row), storedFile, stem, row)
        }
        return report('NORMALIZED', context, groups.size(), populated,
                updates.size(), populated - updates.size(), questionCount,
                updates.size(), 'PASS')
    }

    static Map normalizeYaml(String raw, String file, String filenameStem, int row = 0) {
        Object parsed
        try {
            // JSON authored by previous STEPs is compatible input; prefer
            // its original parser to preserve JSON string escape semantics.
            parsed = raw.trim().startsWith('{')
                    ? new JsonSlurper().parseText(raw)
                    : new Yaml(new SafeConstructor(new LoaderOptions())).load(raw)
        }
        catch (Exception e) {
            throw new GradleException('Invalid Quiz YAML/legacy JSON row ' + row + ': ' + e.message, e)
        }
        if (!(parsed instanceof Map) || (parsed as Map).keySet() != (['questions'] as Set) ||
                !(parsed.questions instanceof List) || (parsed.questions as List).isEmpty()) {
            throw new GradleException('Quiz row ' + row + ': root must contain only nonempty questions[].')
        }
        String prefix = filenameStem.toLowerCase(Locale.ROOT)
        if (!(prefix ==~ /[a-z][a-z0-9]*/)) {
            throw new GradleException('Invalid Quiz filename stem at row ' + row + ': ' + filenameStem)
        }
        List<String> keys = ['id', 'question', 'answers', 'correctAnswerId',
                             'aiGenerated', 'reviewed', 'readmeRelated', 'apiRelated']
        List<Map> items = []
        Set<String> usedIds = new LinkedHashSet<>()
        (parsed.questions as List).each { Object obj ->
            if (obj instanceof Map && (obj as Map).containsKey('id')) {
                Object candidate = (obj as Map).id
                if (!(candidate instanceof String) || !candidate.trim() || !usedIds.add(candidate)) {
                    throw new GradleException('Quiz row ' + row + ': duplicate/invalid existing question ID.')
                }
            }
        }
        int nextNumber = 1
        (parsed.questions as List).eachWithIndex { Object obj, int index ->
            String at = 'Quiz row ' + row + ' question #' + (index + 1)
            if (!(obj instanceof Map) || !(obj as Map).keySet().every { it in keys }) {
                throw new GradleException(at + ': unknown field/non-object.')
            }
            Map q = obj as Map
            ['question'].each { String field ->
                if (!(q[field] instanceof String) || (q[field] as String).trim().isEmpty()) {
                    throw new GradleException(at + ': ' + field + ' must be nonblank string.')
                }
            }
            if (!(q.answers instanceof List) || (q.answers as List).size() != 4) {
                throw new GradleException(at + ': answers must have exactly four entries.')
            }
            Set<String> ids = new LinkedHashSet<>()
            List<Map> answers = (q.answers as List).collect { Object value ->
                if (!(value instanceof Map) || (value as Map).keySet() != (['id','answer','explanation'] as Set)) {
                    throw new GradleException(at + ': answer needs only id, answer and explanation.')
                }
                Map answer = value as Map
                if (!(answer.id instanceof String) || !ids.add(answer.id as String) ||
                        !(answer.answer instanceof String) || !answer.answer.trim() ||
                        !(answer.explanation instanceof String) || !answer.explanation.trim()) {
                    throw new GradleException(at + ': invalid answer ID/text/explanation.')
                }
                return [id: answer.id, answer: answer.answer, explanation: answer.explanation]
            }
            if (ids != (['A','B','C','D'] as Set) ||
                    !(q.correctAnswerId instanceof String) || !ids.contains(q.correctAnswerId)) {
                throw new GradleException(at + ': need A/B/C/D and valid correctAnswerId.')
            }
            String questionId = q.id as String
            if (!questionId) {
                while (usedIds.contains(prefix + '-' + String.format('%03d', nextNumber))) nextNumber++
                questionId = prefix + '-' + String.format('%03d', nextNumber++)
                usedIds.add(questionId)
            }
            boolean ai = flag(q, 'aiGenerated', true, at)
            boolean reviewed = flag(q, 'reviewed', false, at)
            Map readme = relation(q, 'readmeRelated', 'file', 'anchor', at)
            Map api = relation(q, 'apiRelated', 'controller', 'methodSignature', at)
            if (readme.file && readme.file != file) {
                throw new GradleException(at + ': conflicting README file path; not overwritten.')
            }
            items.add([
                    id: questionId, question: q.question,
                    aiGenerated: ai, reviewed: reviewed,
                    readmeRelated: [file: file, anchor: readme.anchor],
                    apiRelated: [controller: api.controller,
                                 methodSignature: api.methodSignature],
                    answers: answers, correctAnswerId: q.correctAnswerId
            ])
        }
        Map normalized = [questions: items]
        ContractSchemaValidateUtils.validateData('quiz', normalized,
                ContractSchemaValidateUtils.LEGACY_QUESTION_LINKS)
        return normalized
    }

    static String renderYaml(Map normalized) {
        DumperOptions options = new DumperOptions()
        options.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        options.allowUnicode = true
        options.indent = 2
        options.width = 120
        return new Yaml(options).dump(normalized)
    }

    private static boolean flag(Map item, String key, boolean fallback, String at) {
        if (!item.containsKey(key)) return fallback
        if (!(item[key] instanceof Boolean)) {
            throw new GradleException(at + ': ' + key + ' must be boolean.')
        }
        return item[key] as boolean
    }

    private static Map relation(Map item, String name, String left, String right, String at) {
        if (!item.containsKey(name)) return [(left): '', (right): '']
        Object value = item[name]
        if (!(value instanceof Map) ||
                !(value as Map).keySet().every { it in [left, right] } ||
                !([left, right].every { String field ->
                    !(value as Map).containsKey(field) || (value as Map)[field] instanceof String
                })) {
            throw new GradleException(at + ': invalid relation ' + name)
        }
        return [(left): ((value as Map)[left] ?: ''),
                (right): ((value as Map)[right] ?: '')]
    }

    static List<String> splitFragments(String text, int row = 0) {
        int codepoints = text.codePointCount(0, text.length())
        if (codepoints > 4 * CELL_LIMIT) {
            throw new GradleException('Quiz row ' + row + ' exceeds four 30000-char cells.')
        }
        List<String> out = []
        int start = 0
        while (start < text.length()) {
            int count = Math.min(CELL_LIMIT, text.codePointCount(start, text.length()))
            int end = text.offsetByCodePoints(start, count)
            out.add(text.substring(start, end))
            start = end
        }
        while (out.size() < 4) out.add('')
        return out
    }

    static String joinFragments(List<String> parts, int row = 0) {
        boolean emptySeen = false
        StringBuilder result = new StringBuilder()
        parts.each { String part ->
            if (part.isEmpty()) emptySeen = true
            else {
                if (emptySeen || part.codePointCount(0, part.length()) > CELL_LIMIT) {
                    throw new GradleException('Noncontiguous or oversized Quiz row ' + row)
                }
                result.append(part)
            }
        }
        return result.toString()
    }

    private static Map<Integer, Map<Integer, CellData>> fetch(
            Sheets client, String id, String tab, int gid
    ) {
        Spreadsheet meta = client.spreadsheets().get(id)
                .setFields('sheets(properties(sheetId,gridProperties(columnCount)))').execute()
        def info = (meta.sheets ?: []).find { it.properties?.sheetId == gid }
        if (!info || (info.properties.gridProperties.columnCount ?: 0) < 23) {
            throw new GradleException('QUIZ SCHEMA: tab does not have columns T:W.')
        }
        Spreadsheet grid = client.spreadsheets().get(id)
                .setRanges([tab + '!A19:B', tab + '!T18:W'])
                .setIncludeGridData(true)
                .setFields('sheets(properties(sheetId),data(startRow,startColumn,' +
                        'rowData(values(userEnteredValue,effectiveValue,formattedValue,note,dataValidation,chipRuns))))')
                .execute()
        def sheet = (grid.sheets ?: []).find { it.properties?.sheetId == gid }
        Map<Integer, Map<Integer, CellData>> map = [:].withDefault { [:] }
        (sheet?.data ?: []).each { GridData block ->
            (block.rowData ?: []).eachWithIndex { RowData row, int i ->
                (row.values ?: []).eachWithIndex { CellData cell, int j ->
                    map[(block.startRow ?: 0) + i][(block.startColumn ?: 0) + j] = cell
                }
            }
        }
        return map
    }

    private static void checkHeader(Map<Integer, Map<Integer, CellData>> data) {
        List<String> labels = (19..22).collect { string(data[18]?.get(it)) }
        if (string(data[17]?.get(19)) != 'Quiz' ||
                !(labels.unique().size() == 1 && labels[0] == 'Quiz YAML') ||
                string(data[18]?.get(0)) != 'Menu Cấp 1' ||
                string(data[18]?.get(1)) != 'Menu Cấp 2') {
            throw new GradleException('QUIZ SCHEMA: incorrect A/B/T:W headers.')
        }
    }

    private static List<List<Object>> menuRows(Map<Integer, Map<Integer, CellData>> data) {
        int end = Math.max(19, (data.keySet().findAll { it >= 18 }.max() ?: 19) as int)
        return (18..end).collect { int r ->
            [string(data[r]?.get(0)), string(data[r]?.get(1))]
        }
    }

    private static List<String> fragments(Map<Integer, Map<Integer, CellData>> data, int row) {
        return (19..22).collect { int col ->
            CellData cell = data[row - 1]?.get(col)
            if (cell?.userEnteredValue && cell.userEnteredValue.stringValue == null) {
                throw new GradleException('Quiz row ' + row + ' contains non-text/formula.')
            }
            return cell?.userEnteredValue?.stringValue ?: ''
        }
    }

    private static boolean protectedMetadata(CellData cell) {
        return cell && ((cell.userEnteredValue &&
                cell.userEnteredValue.stringValue == null) ||
                cell.note != null || cell.dataValidation != null ||
                (cell.chipRuns != null && !cell.chipRuns.isEmpty()))
    }

    private static boolean occupied(CellData cell) {
        return cell && (cell.userEnteredValue != null ||
                cell.effectiveValue != null || cell.note != null ||
                cell.dataValidation != null ||
                (cell.chipRuns != null && !cell.chipRuns.isEmpty()))
    }

    private static String string(CellData cell) {
        return cell?.userEnteredValue?.stringValue ?: cell?.formattedValue ?: ''
    }

    private static Map fingerprint(Map<Integer, Map<Integer, CellData>> data) {
        return data.collectEntries { Integer row, Map<Integer, CellData> cells ->
            [(row): cells.collectEntries { Integer col, CellData cell ->
                [(col): [cell?.userEnteredValue?.toString(),
                         cell?.effectiveValue?.toString(), cell?.formattedValue,
                         cell?.note, cell?.dataValidation?.toString(), cell?.chipRuns?.toString()]]
            }]
        }
    }

    private static Map report(String status, Map context, int scanned, int populated,
                              int changed, int unchanged, int questions, int written,
                              String readback) {
        return [status: status, tab: context.tabTitle, gid: context.sheetId,
                scanned: scanned, populated: populated, changed: changed,
                unchanged: unchanged, questions: questions,
                written: written, readback: readback]
    }
}
