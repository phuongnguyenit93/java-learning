package com.example.learning.task.sheets.service

import com.example.learning.utils.schema.validate.ContractSchemaValidateUtils
import com.google.api.services.sheets.v4.Sheets
import org.gradle.api.GradleException
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/**
 * STEP 8, code-only. Sheet AD:AG owns all VI chapter scenarios.
 * Never invokes an LLM, edits the Sheet, or imports a partial chapter list.
 */
class PracticeSheetImportService {
    private static final int SHEET_COLS = 33
    private static final int CODE_POINT_LIMIT = 30_000
    private static final int EXCEL_UTF16_LIMIT = 32_767

    Map<String,Object> execute(File root, Map<String,Object> context, boolean apply) {
        File module = new File(root.canonicalFile, context.moduleLocation as String).canonicalFile
        RoadmapSheetImportService.resolveDestination(root, context.moduleLocation as String)
        Sheets client = context.client as Sheets
        String spreadsheetId = context.spreadsheetId as String
        String tab = "'" + (context.tabTitle as String).replace("'", "''") + "'"
        def meta = client.spreadsheets().get(spreadsheetId)
                .setFields('sheets(properties(sheetId,gridProperties(columnCount)))').execute()
        def selected = (meta.sheets ?: []).find {
            it.properties?.sheetId?.toString() == context.sheetId?.toString()
        }
        if (!selected || (selected.properties?.gridProperties?.columnCount ?: 0) < SHEET_COLS) {
            throw new GradleException('STEP 8 requires AD:AG (33 columns) on the exact gid.')
        }
        List<List<Object>> menuRows = (client.spreadsheets().values()
                .get(spreadsheetId, tab + '!A19:B').execute().values ?: []) as List<List<Object>>
        Map menu = ReadmeSheetImportService.parseMenu(menuRows)
        if (menu.status != 'READY') {
            throw new GradleException('STEP 8 requires a complete chapter menu: ' + menu.status)
        }
        List<List<Object>> cells = (client.spreadsheets().values()
                .get(spreadsheetId, tab + '!AD18:AG')
                .setValueRenderOption('FORMULA').execute().values ?: []) as List<List<Object>>
        Map collected = collect((menu.groups ?: []) as List<Map>, cells)
        Map<File, Map<String,String>> targets = new LinkedHashMap<>()
        File base = new File(module, 'content/readme/docs/vi/menu')
        if (!base.isDirectory()) {
            throw new GradleException('Missing module VI Knowledge menu: ' + base)
        }
        File[] directories = base.listFiles()
        if (directories == null) throw new GradleException('Cannot list Practice chapter menu.')
        directories.findAll { it.isDirectory() && new File(it, 'practice').exists() }.each { File chapter ->
            targets[new File(chapter, 'practice')] = [:]
        }
        (collected.files as Map<String,Map<String,String>>).each { String chapter, Map<String,String> files ->
            File chapterPath = new File(base, chapter)
            if (!new File(chapterPath, 'knowledge').isDirectory()) {
                throw new GradleException('Missing Knowledge folder for Practice chapter: ' + chapter)
            }
            targets[new File(chapterPath, 'practice')] = files
        }
        if (apply) {
            // A worker may still be editing AD:AG. Fail closed on any Sheet
            // change after preflight rather than importing stale authored data.
            List<List<Object>> currentMenu = (client.spreadsheets().values()
                    .get(spreadsheetId, tab + '!A19:B').execute().values ?: []) as List<List<Object>>
            List<List<Object>> currentPractice = (client.spreadsheets().values()
                    .get(spreadsheetId, tab + '!AD18:AG')
                    .setValueRenderOption('FORMULA').execute().values ?: []) as List<List<Object>>
            if (currentMenu != menuRows || currentPractice != cells) {
                throw new GradleException('STEP 8 Sheet changed during Practice preflight; no files written.')
            }
        }
        Map result = SheetSnapshotReplaceService.replaceAll(root, targets, apply)
        return [tab: context.tabTitle, gid: context.sheetId, location: context.moduleLocation,
                chapterCount: collected.chapterCount, scenarioCount: collected.scenarioCount,
                outputFolders: targets.keySet().size()] + result
    }

    /** Rows include AD18:AG19 headers followed by the YAML at the parent rows. */
    static Map collect(List<Map> groups, List<List<Object>> rows) {
        if (groups == null || groups.isEmpty()) {
            throw new GradleException('STEP 8 requires at least one chapter.')
        }
        if (raw(row(rows, 0), 0).trim() != 'Real-world Practice' ||
                (0..3).any { raw(row(rows, 1), it).trim() != 'Practice YAML' }) {
            throw new GradleException('Invalid STEP 8 headers AD18:AG19.')
        }
        Set<Integer> parents = groups.collect { (it.parentRow as Number).intValue() } as Set<Integer>
        rows.drop(2).eachWithIndex { List<Object> values, int i ->
            int sheetRow = i + 20
            if (!parents.contains(sheetRow) && (0..3).any { int c -> raw(values, c) }) {
                throw new GradleException('Orphan STEP 8 data at AD' + sheetRow + ':AG' + sheetRow)
            }
        }
        Map<String,Map<String,String>> output = new LinkedHashMap<>()
        int total = 0
        groups.eachWithIndex { Map group, int index ->
            int parent = (group.parentRow as Number).intValue()
            List<Object> fragments = row(rows, parent - 18)
            StringBuilder whole = new StringBuilder()
            boolean gap = false
            (0..3).each { int c ->
                String part = raw(fragments, c)
                if (!part) gap = true
                else {
                    if (gap || part.codePointCount(0, part.length()) > CODE_POINT_LIMIT ||
                            part.length() > EXCEL_UTF16_LIMIT) {
                        throw new GradleException('Invalid Practice fragments at AD' + parent + ':AG' + parent)
                    }
                    whole.append(part)
                }
            }
            if (!whole.length()) {
                throw new GradleException('STEP 8 incomplete: missing Practice YAML at AD' + parent)
            }
            Map document = parse(whole.toString(), parent)
            ContractSchemaValidateUtils.validateData('practiceSheet', document)
            List<Map> scenarios = document.scenarios as List<Map>
            if (scenarios.collect { it.id }.toSet().size() != scenarios.size()) {
                throw new GradleException('Duplicate scenario IDs at chapter row ' + parent)
            }
            String filename = group.filename as String
            String chapter = "${index + 1}.${filename.substring(0, filename.length() - 3)}"
            // Keep the exact authored source (including scalar style, comments,
            // formatting and scenario order) after validating the entire document.
            // The importer is code-only and must never rewrite authored answers.
            output[chapter] = ['scenarios.yml': whole.toString()]
            total += scenarios.size()
        }
        return [chapterCount: groups.size(), scenarioCount: total, files: output]
    }

    static Map parse(String text, int parentRow) {
        Object parsed
        try {
            LoaderOptions options = new LoaderOptions()
            options.allowDuplicateKeys = false
            parsed = new Yaml(new SafeConstructor(options)).load(text)
        } catch (Exception error) {
            throw new GradleException('Invalid STEP 8 YAML at row ' + parentRow, error)
        }
        if (!(parsed instanceof Map)) {
            throw new GradleException('STEP 8 root must be a YAML mapping at row ' + parentRow)
        }
        return parsed as Map
    }

    private static List<Object> row(List<List<Object>> values, int offset) {
        return offset < values.size() ? (values[offset] ?: []) : []
    }
    private static String raw(List<Object> values, int index) {
        return index < values.size() && values[index] != null ? values[index].toString() : ''
    }
}
