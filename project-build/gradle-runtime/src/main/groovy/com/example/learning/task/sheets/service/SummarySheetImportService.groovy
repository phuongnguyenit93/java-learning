package com.example.learning.task.sheets.service

import com.google.api.services.sheets.v4.Sheets
import org.gradle.api.GradleException
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml

/**
 * STEP 5's reviewed chapter AC YAML is authoritative. Validate it, then write
 * its bytes as-is to section.yml. Child AC Markdown becomes summaries.yml.
 */
class SummarySheetImportService {
    static final int MAX_CELL = 30000
    private static final List<List<String>> HEADERS = [
            ['Summary', 'Summary YAML'],
            ['Learning Objectives', 'Learning Objectives YAML']
    ].asImmutable()

    Map<String, Object> execute(File repositoryRoot, Map<String, Object> context, boolean apply) {
        File menu = ReadmeSheetImportService.resolveMenuDirectory(
                repositoryRoot, context.moduleLocation as String)
        Sheets sheets = context.client as Sheets
        String spreadsheet = context.spreadsheetId as String
        String quotedTab = "'${(context.tabTitle as String).replace("'", "''")}'"
        Map base = [tab: context.tabTitle, gid: context.sheetId,
                    location: context.moduleLocation, directory: menu]
        def metadata = sheets.spreadsheets().get(spreadsheet)
                .setFields('sheets(properties(sheetId,gridProperties(columnCount)))').execute()
        def selected = (metadata.sheets ?: []).find {
            it.properties?.sheetId?.toString() == context.sheetId?.toString()
        }
        if (!selected) throw new GradleException('Missing Summary tab gid=' + context.sheetId)
        if ((selected.properties?.gridProperties?.columnCount ?: 0) < 29) {
            return base + [status: 'SKIPPED_NO_SUMMARY_SCHEMA']
        }
        List<List<Object>> menuRows = (sheets.spreadsheets().values()
                .get(spreadsheet, quotedTab + '!A19:B')
                .setValueRenderOption('FORMULA').execute().values ?: []) as List<List<Object>>
        Map parsed = ReadmeSheetImportService.parseMenu(menuRows)
        if (!(parsed.status in ['READY', 'SKIPPED_EMPTY_MENU'])) {
            return base + [status: parsed.status, missingRows: parsed.missingRows ?: []]
        }
        if (parsed.status == 'READY') {
            int last = parsed.lastRow as int
            List<List<Object>> parentBases = (sheets.spreadsheets().values()
                    .get(spreadsheet, quotedTab + "!C20:C${last}")
                    .setValueRenderOption('FORMULA').execute().values ?: []) as List<List<Object>>
            MenuLessonIdService.validate(parsed.groups as List<Map>, parentBases)
        }
        List<List<Object>> acRows = (sheets.spreadsheets().values()
                .get(spreadsheet, quotedTab + '!AC18:AC')
                .setValueRenderOption('FORMULA').execute().values ?: []) as List<List<Object>>
        Map<String, Map<String, String>> output = prepare(parsed.groups as List<Map>, acRows)
        Map<File, Map<String, String>> targets = new LinkedHashMap<>()
        if (menu.isDirectory()) {
            File[] chapters = menu.listFiles()
            if (chapters == null) throw new GradleException('Cannot list Summary menu: ' + menu)
            chapters.findAll { it.isDirectory() &&
                    (it.name ==~ /[0-9]+\.[A-Za-z0-9][A-Za-z0-9 _.-]*/) &&
                    !it.name.contains('..') && new File(it, 'summary').exists() }
                    .each { targets[new File(it, 'summary')] = [:] }
        }
        output.each { String chapter, Map<String, String> files ->
            File home = new File(menu, chapter)
            // A Summary must not invent a Knowledge chapter that does not exist.
            if (!new File(home, 'knowledge').isDirectory()) {
                throw new GradleException('Missing Knowledge before Summary import: ' + home)
            }
            targets[new File(home, 'summary')] = files
        }
        Map result = SheetSnapshotReplaceService.replaceAll(repositoryRoot, targets, apply)
        return base + result + [chapters: output.size(),
                                 lessons: output.values().sum(0) { 1 }]
    }

    /** AC18/19 + cells from row20; blank uninitialized AC is never destructive. */
    static Map<String, Map<String, String>> prepare(List<Map> groups, List<List<Object>> rows) {
        String header = cell(rows, 0)
        String detail = cell(rows, 1)
        if (!header && !detail && rows.every { List<Object> v -> !(v ?: []).any { it != null && it.toString() } }) {
            throw new GradleException('Summary AC column exists but has no schema; import refused.')
        }
        if (![header, detail].with { HEADERS.contains(it) }) {
            throw new GradleException('Invalid Summary headers at AC18/AC19.')
        }
        Set<Integer> permitted = [] as Set
        groups.each { Map group ->
            permitted.add(group.parentRow as int)
            (group.children as List<Map>).each { permitted.add(it.row as int) }
        }
        rows.drop(2).eachWithIndex { List<Object> value, int i ->
            int row = i + 20
            if (!permitted.contains(row) && cell(rows, i + 2)) {
                throw new GradleException('Summary AC contains an orphan at AC' + row)
            }
        }
        Map<String, Map<String, String>> prepared = new LinkedHashMap<>()
        DumperOptions options = new DumperOptions()
        options.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        options.width = 120
        options.prettyFlow = true
        Yaml writer = new Yaml(options)
        groups.eachWithIndex { Map group, int idx ->
            String filename = group.filename as String
            String stem = filename.substring(0, filename.length() - 3)
            String chapter = "${idx + 1}.${stem}"
            String section = body(rows, group.parentRow as int)
            List<Map> lessons = []
            (group.children as List<Map>).each { Map lesson ->
                String summary = body(rows, lesson.row as int)
                lessons.add([id: lesson.id,
                        title: lesson.title as String, summary: summary])
            }
            if (!section && lessons.every { !it.summary }) return
            if (!section || lessons.any { !it.summary }) {
                throw new GradleException('Incomplete Summary AC for chapter ' + chapter)
            }
            String materialized = SummarySectionYamlService.materialize(section, lessons)
            prepared[chapter] = [
                    'section.yml': materialized,
                    'summaries.yml': writer.dump([summaries: lessons])
            ]
        }
        return prepared
    }

    private static String body(List<List<Object>> rows, int sheetRow) {
        String value = cell(rows, sheetRow - 18)
        if (value.codePointCount(0, value.length()) > MAX_CELL || value.length() > 32767) {
            throw new GradleException('Summary cell AC' + sheetRow + ' exceeds Excel/Sheet limit.')
        }
        return value
    }

    private static String cell(List<List<Object>> rows, int index) {
        List<Object> cells = index < rows.size() ? (rows[index] ?: []) : []
        return cells ? (cells[0]?.toString() ?: '') : ''
    }
}
