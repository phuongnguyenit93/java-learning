package com.example.learning.task.sheets.service

import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.*
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.nio.file.Files
import java.nio.file.Path

/**
 * Deterministic Interview normalization on the SAME Google Sheet:
 * YAML question/answer -> YAML with canonical contract fields.
 * Accepts old JSON Sheet cells for compatibility, but always emits YAML.
 * No AI and no module filesystem writes.
 */
class InterviewSheetNormalizeService {
    static final int CELL_LIMIT = 30000
    private final SheetsMetadataReaderService reader

    InterviewSheetNormalizeService(
            SheetsMetadataReaderService reader = new SheetsMetadataReaderService()
    ) {
        this.reader = reader
    }

    Map execute(String fileUrl, String sheetId, boolean apply, File repositoryRoot = null) {
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
            throw new GradleException('Interview normalization requires complete filenames: ' + menu.status)
        }
        List<Map> groups = menu.groups as List<Map>
        Set<Integer> parents = groups.collect { (it.parentRow as Number).intValue() } as Set
        snapshot.each { Integer zeroRow, Map<Integer, CellData> cells ->
            int row = zeroRow + 1
            if (row >= 20 && !parents.contains(row) &&
                    (15..18).any { int col -> occupied(cells[col]) }) {
                throw new GradleException('Orphan Interview data at non-parent row ' + row)
            }
        }

        List<Map> updates = []
        int populated = 0
        int questionCount = 0
        int summaryQuestionCount = 0
        Map<File, String> stagingReads = new LinkedHashMap<>()
        groups.eachWithIndex { Map parent, int index ->
            int row = (parent.parentRow as Number).intValue()
            List<String> oldPieces = fragments(snapshot, row)
            File summary = repositoryRoot == null ? null :
                    summaryStagingFile(repositoryRoot, context.moduleLocation as String,
                            (index + 1) + '.' + (parent.filename as String).replaceFirst(/\.md$/, ''))
            if (oldPieces.every { it.isEmpty() }) {
                if (summary?.isFile()) {
                    throw new GradleException('Summary staging has no Interview source at row ' + row)
                }
                return
            }
            populated++
            String filename = parent.filename as String
            String expectedFile = (index + 1) + '.' +
                    filename.substring(0, filename.length() - 3) + '/knowledge/' + filename
            Map normalized = normalizeYaml(joinFragments(oldPieces, row), expectedFile, row)
            if (summary?.isFile()) {
                String authored = summary.getText('UTF-8')
                stagingReads[summary] = authored
                normalized = mergeSummaryQuestions(renderYaml(normalized), authored, expectedFile, row)
            }
            questionCount += (normalized.questions as List).size()
            summaryQuestionCount += ((normalized.summaryQuestions ?: []) as List).size()
            List<String> newPieces = splitFragments(
                    renderYaml(normalized), row
            )
            if (oldPieces != newPieces) {
                (15..18).each { int col ->
                    if (protectedMetadata(snapshot[row - 1]?.get(col))) {
                        throw new GradleException('Interview target has formula/note/validation/chip at row ' + row)
                    }
                }
                updates.add([row: row, values: newPieces, file: expectedFile])
            }
        }
        if (!apply || updates.isEmpty()) {
            Map response = report(apply ? 'UNCHANGED' : 'DRY_RUN',
                    context, groups.size(), populated, updates.size(),
                    populated - updates.size(), questionCount, 0,
                    apply ? 'PASS' : 'N/A')
            response.summaryQuestions = summaryQuestionCount
            return response
        }

        // All rows are validated before writing. Recheck all source/target
        // cells immediately before one atomic batchUpdate.
        if (fingerprint(snapshot) != fingerprint(fetch(client, id, tab, gid))) {
            throw new GradleException('Sheet changed during Interview preflight; no write dispatched.')
        }
        stagingReads.each { File source, String original ->
            if (!source.isFile() || source.getText('UTF-8') != original) {
                throw new GradleException('Summary staging changed during Interview preflight: ' + source)
            }
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
                            .setStartColumnIndex(15).setEndColumnIndex(19))
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
                        'Exact Interview readback FAILED at P' + row + ':S' + row +
                                '; write may have occurred.'
                )
            }
            normalizeYaml(joinFragments(actual, row), update.file as String, row)
        }
        Map response = report('NORMALIZED', context, groups.size(), populated,
                updates.size(), populated - updates.size(), questionCount,
                updates.size(), 'PASS')
        response.summaryQuestions = summaryQuestionCount
        return response
    }

    private static File summaryStagingFile(File root, String location, String chapter) {
        // Validate I3 with the canonical module-location guard (which returns
        // a Roadmap file), then resolve the parent module explicitly.
        RoadmapSheetImportService.resolveDestination(root.canonicalFile, location)
        File module = new File(root.canonicalFile, location).canonicalFile
        if (!module.isDirectory() || !new File(module, 'gradle.properties').isFile()) {
            throw new GradleException('STEP 9 Summary staging requires a real Gradle module: ' + location)
        }
        File stageRoot = new File(module, 'build/step9/summary')
        File candidate = new File(stageRoot, chapter + '/summary.yml')
        Path modulePath = module.canonicalFile.toPath()
        Path selected = candidate.toPath().toAbsolutePath().normalize()
        if (!selected.startsWith(modulePath) || chapter.contains('..')) {
            throw new GradleException('Unsafe STEP 9 Summary staging path: ' + candidate)
        }
        Path cursor = modulePath
        for (Path segment : modulePath.relativize(selected)) {
            cursor = cursor.resolve(segment)
            if (Files.isSymbolicLink(cursor)) {
                throw new GradleException('Linked STEP 9 Summary staging path: ' + cursor)
            }
        }
        return candidate
    }

    static Map normalizeYaml(String raw, String file, int row = 0) {
        Object parsed
        try {
            // JSON authored by previous STEPs is compatible input; prefer
            // its original parser to preserve JSON string escape semantics.
            parsed = raw.trim().startsWith('{')
                    ? new JsonSlurper().parseText(raw)
                    : new Yaml(new SafeConstructor(new LoaderOptions())).load(raw)
        }
        catch (Exception e) {
            throw new GradleException('Invalid Interview YAML/legacy JSON row ' + row + ': ' + e.message, e)
        }
        if (!(parsed instanceof Map) || !(parsed as Map).keySet().every { it in ['questions','summaryQuestions'] } ||
                !(parsed.questions instanceof List) || (parsed.questions as List).isEmpty()) {
            throw new GradleException('Interview row ' + row + ': root must contain nonempty questions[] and optional summaryQuestions[].')
        }
        // Read historical Sheet cells but NEVER write these retired flags.
        List<String> legacyOnly = ['aiGenerated', 'reviewed']
        List<String> keys = ['question', 'answer',
                             'readmeRelated', 'apiRelated'] + legacyOnly
        List<Map> items = []
        (parsed.questions as List).eachWithIndex { Object obj, int index ->
            String at = 'Interview row ' + row + ' question #' + (index + 1)
            if (!(obj instanceof Map) || !(obj as Map).keySet().every { it in keys }) {
                throw new GradleException(at + ': unknown field/non-object.')
            }
            Map q = obj as Map
            legacyOnly.each { String flagName ->
                if (q.containsKey(flagName) && !(q[flagName] instanceof Boolean)) {
                    throw new GradleException(at + ': legacy field ' + flagName + ' must be boolean.')
                }
            }
            ['question', 'answer'].each { String field ->
                if (!(q[field] instanceof String) || (q[field] as String).trim().isEmpty()) {
                    throw new GradleException(at + ': ' + field + ' must be nonblank string.')
                }
            }
            Map readme = relation(q, 'readmeRelated', 'file', 'anchor', at)
            Map api = relation(q, 'apiRelated', 'controller', 'methodSignature', at)
            String legacyFile = file.replace('/knowledge/', '/')
            if (readme.file && readme.file != file && readme.file != legacyFile) {
                throw new GradleException(at + ': conflicting README file path; not overwritten.')
            }
            items.add([
                    question: q.question, answer: q.answer,
                    readmeRelated: [anchor: readme.anchor],
                    apiRelated: [controller: api.controller,
                                 methodSignature: api.methodSignature]
            ])
        }
        Map result = [questions: items]
        if ((parsed as Map).containsKey('summaryQuestions')) {
            if (!(parsed.summaryQuestions instanceof List) || (parsed.summaryQuestions as List).isEmpty()) {
                throw new GradleException('Interview row ' + row + ': summaryQuestions must be a nonempty list.')
            }
            List<Map> summaries = []
            (parsed.summaryQuestions as List).eachWithIndex { Object value, int index ->
                String at = 'Interview row ' + row + ' summary question #' + (index + 1)
                if (!(value instanceof Map) || (value as Map).keySet() != (['question','answer'] as Set) ||
                        !['question','answer'].every { String field ->
                            (value as Map)[field] instanceof String && (value as Map)[field].trim()
                        }) {
                    throw new GradleException(at + ': expected only nonblank question/answer strings.')
                }
                summaries.add([question: value.question, answer: value.answer])
            }
            result.summaryQuestions = summaries
        }
        return result
    }

    /**
     * Pure-code merge of two independently authored sources. Never lets a
     * Summary worker rewrite existing Interview text, order or relations.
     * The summary source contains only summaryQuestions[], not Interview data.
     */
    static Map mergeSummaryQuestions(String interviewYaml, String summaryYaml,
                                     String file, int row = 0) {
        Map interview = normalizeYaml(interviewYaml, file, row)
        Object raw
        try {
            LoaderOptions options = new LoaderOptions()
            options.allowDuplicateKeys = false
            raw = new Yaml(new SafeConstructor(options)).load(summaryYaml)
        } catch (Exception error) {
            throw new GradleException('Invalid isolated Summary YAML row ' + row, error)
        }
        if (!(raw instanceof Map) || (raw as Map).keySet() != (['summaryQuestions'] as Set)) {
            throw new GradleException('Isolated Summary YAML must contain only summaryQuestions[].')
        }
        Map normalized = normalizeYaml(renderYaml([questions: interview.questions,
                summaryQuestions: (raw as Map).summaryQuestions]), file, row)
        if (interview.containsKey('summaryQuestions') &&
                interview.summaryQuestions != normalized.summaryQuestions) {
            throw new GradleException('Existing Summary questions differ; refusing overwrite.')
        }
        return normalized
    }

    /** Retain the old entry point for existing Gradle callers/tests. */
    @Deprecated
    static Map normalizeJson(String raw, String file, int row = 0) {
        return normalizeYaml(raw, file, row)
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
            throw new GradleException('Interview row ' + row + ' exceeds four 30000-char cells.')
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
                    throw new GradleException('Noncontiguous or oversized Interview row ' + row)
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
        if (!info || (info.properties.gridProperties.columnCount ?: 0) < 19) {
            throw new GradleException('INTERVIEW SCHEMA: tab does not have columns P:S.')
        }
        Spreadsheet grid = client.spreadsheets().get(id)
                .setRanges([tab + '!A19:B', tab + '!P18:S'])
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
        List<String> labels = (15..18).collect { string(data[18]?.get(it)) }
        if (string(data[17]?.get(15)) != 'Interview' ||
                !(labels.unique().size() == 1 && labels[0] in ['Interview YAML', 'Interview JSON']) ||
                string(data[18]?.get(0)) != 'Menu Cấp 1' ||
                string(data[18]?.get(1)) != 'Menu Cấp 2') {
            throw new GradleException('INTERVIEW SCHEMA: incorrect A/B/P:S headers.')
        }
    }

    private static List<List<Object>> menuRows(Map<Integer, Map<Integer, CellData>> data) {
        int end = Math.max(19, (data.keySet().findAll { it >= 18 }.max() ?: 19) as int)
        return (18..end).collect { int r ->
            [string(data[r]?.get(0)), string(data[r]?.get(1))]
        }
    }

    private static List<String> fragments(Map<Integer, Map<Integer, CellData>> data, int row) {
        return (15..18).collect { int col ->
            CellData cell = data[row - 1]?.get(col)
            if (cell?.userEnteredValue && cell.userEnteredValue.stringValue == null) {
                throw new GradleException('Interview row ' + row + ' contains non-text/formula.')
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
