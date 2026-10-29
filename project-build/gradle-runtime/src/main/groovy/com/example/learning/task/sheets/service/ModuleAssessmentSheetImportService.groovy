package com.example.learning.task.sheets.service

import com.google.api.services.sheets.v4.Sheets
import org.gradle.api.GradleException
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/**
 * STEP 6 and 7 use exactly the same code-only, module-wide importer.
 * Sheet Phase 2/3 provides three grouped documents. Phase 4 supplies Interview
 * answers and Case Study Markdown/code, joined by (groupID, 1-based item index).
 * No legacy chapter importer, AI, Portal projection, or Sheet writes are used.
 */
class ModuleAssessmentSheetImportService {
    private static final int CELL_CODEPOINT_LIMIT = 30000
    private static final int CELL_UTF16_LIMIT = 32767
    private static final Map<String, Map> TYPES = [
            interview: [root: 'interviewQuestion', list: 'questions', label: 'question',
                        supplement: 'answers', index: 'questionIndex', field: 'answer',
                        range: 'P20:R24', col: 'P', lastCol: 'R', output: 'interview.yml'],
            casestudy: [root: 'caseStudy', list: 'cases', label: 'situation',
                        supplement: 'codes', index: 'caseIndex', field: 'code',
                        range: 'S20:U24', col: 'S', lastCol: 'U', output: 'casestudy.yml'],
            quiz: [root: 'quiz', list: 'questions', label: 'question',
                   range: 'V20:W24', output: 'quiz.yml']
    ].asImmutable()

    Map<String, Object> execute(File root, Map<String, Object> context, boolean apply) {
        RoadmapSheetImportService.resolveDestination(root, context.moduleLocation as String)
        File module = new File(root.canonicalFile, context.moduleLocation as String).canonicalFile
        if (!module.isDirectory() || !new File(module, 'master.json').isFile()) {
            throw new GradleException('Missing selected module/master.json: ' + module)
        }
        Sheets client = context.client as Sheets
        String spreadsheetId = context.spreadsheetId as String
        String tab = "'${(context.tabTitle as String).replace("'", "''")}'"
        int rowLimit = (context.rowCount ?: 0) as int
        if (rowLimit < 27 || (context.columnCount ?: 0) < 23) {
            throw new GradleException('STEP 6/7 grouped Sheet range is outside selected tab grid.')
        }
        List<String> mainRanges = TYPES.values()*.range.collect { "${tab}!${it}" }
        mainRanges.add("${tab}!A20:B${rowLimit}")
        List<List<List<Object>>> source = fetch(client, spreadsheetId, mainRanges)
        Map<String, String> main = new LinkedHashMap<>()
        int offset = 0
        TYPES.each { String kind, Map spec ->
            main[kind] = concatenate(source[offset++], 5,
                    kind == 'quiz' ? 2 : 3, "${kind} ${spec.range}")
        }
        Set<String> lessonIds = catalogLessonIds(source[offset])
        Map<String, Map> parsed = parseMain(main, lessonIds)

        List<String> phaseRanges = []
        ['interview', 'casestudy'].each { String kind ->
            Map spec = TYPES[kind]
            int lastRow = 26 + 3 * ((parsed[kind][spec.root] as List).size())
            if (lastRow > rowLimit) {
                throw new GradleException("${kind} Phase4 groups exceed Sheet rowCount ${rowLimit}.")
            }
            // Include trailing rows to detect stale/unowned extra Phase 4 groups.
            phaseRanges.add("${tab}!${spec.col}27:${spec.lastCol}${rowLimit}")
        }
        List<List<List<Object>>> phaseValues = fetch(client, spreadsheetId, phaseRanges)
        Map<String, String> outputs = assemble(main, lessonIds,
                [interview: phaseValues[0], casestudy: phaseValues[1]])
        Map<File, Map<String, String>> targets = new LinkedHashMap<>()
        outputs.each { String kind, String yaml ->
            File destination = new File(module, "content/${kind}/vi")
            targets[destination] = [(TYPES[kind].output as String): yaml]
        }
        // Stages and verifies all three files before CLEANing any owned VI tree;
        // the snapshot utility handles rollback and exact readback as one unit.
        Map result = SheetSnapshotReplaceService.replaceAll(root, targets, apply)
        return [tab: context.tabTitle, gid: context.sheetId,
                location: context.moduleLocation, directories: targets.keySet()*.path,
                groupCounts: TYPES.collectEntries { String kind, Map spec ->
                    [(kind): (parsed[kind][spec.root] as List).size()]
                },
                itemCounts: TYPES.collectEntries { String kind, Map spec ->
                    [(kind): (parsed[kind][spec.root] as List).sum(0) { Map group ->
                        (group[spec.list] as List).size()
                    }]
                }] + result
    }

    /** Pure transformation, also used by tests without a Sheet connection. */
    static Map<String, String> assemble(Map<String, String> main,
                                        Set<String> lessonIds,
                                        Map<String, List<List<Object>>> phase4) {
        Map<String, Map> documents = parseMain(main, lessonIds)
        Map<String, String> output = new LinkedHashMap<>()
        TYPES.each { String kind, Map spec ->
            List<Map> groups = documents[kind][spec.root] as List<Map>
            if (spec.supplement) {
                List<List<Object>> cells = phase4[kind]
                if (cells == null) {
                    throw new GradleException("Missing Phase 4 for ${kind}.")
                }
                if (cells.size() > groups.size() * 3 &&
                        cells.drop(groups.size() * 3).any { List<Object> r -> r.any { it != null && it.toString() } }) {
                    throw new GradleException("Unexpected extra Phase 4 rows for ${kind}.")
                }
                groups.each { Map group ->
                    int groupId = group.groupID as int
                    String block = concatenate(cells.subList(
                            Math.min((groupId - 1) * 3, cells.size()),
                            Math.min(groupId * 3, cells.size())),
                            3, 3, "${kind} Phase4 group ${groupId}")
                    Map supplement = parseYaml(block, "${kind} Phase4 group ${groupId}")
                    if (supplement.keySet() != (['groupID', spec.supplement] as Set) ||
                            supplement.groupID != groupId ||
                            !(supplement[spec.supplement] instanceof List)) {
                        throw new GradleException("Invalid ${kind} Phase4 group ${groupId} envelope.")
                    }
                    List items = group[spec.list] as List
                    List answerEntries = supplement[spec.supplement] as List
                    if (answerEntries.size() != items.size()) {
                        throw new GradleException("${kind} group ${groupId}: Phase4 count does not match items.")
                    }
                    Set<Integer> seen = new LinkedHashSet<>()
                    answerEntries.each { Object raw ->
                        if (!(raw instanceof Map)) throw new GradleException("Invalid ${kind} Phase4 entry.")
                        Map entry = raw as Map
                        int index = entry[spec.index] instanceof Integer ? entry[spec.index] as int : -1
                        if (entry.keySet() != ([spec.index, spec.field] as Set) ||
                                index < 1 || index > items.size() || !seen.add(index) ||
                                !text(entry[spec.field])) {
                            throw new GradleException("Invalid/duplicate ${kind} Phase4 index ${index} in group ${groupId}.")
                        }
                        (items[index - 1] as Map)[spec.field] = entry[spec.field]
                    }
                    if (seen.size() != items.size()) {
                        throw new GradleException("${kind} group ${groupId} has missing Phase4 indices.")
                    }
                }
            }
            DumperOptions options = new DumperOptions()
            options.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            options.allowUnicode = true
            options.indent = 2
            options.width = 120
            String rendered = new Yaml(options).dump(documents[kind])
            // Validate serialized bytes too (not only the parsed source).
            Map confirmed = parseYaml(rendered, "${kind} output")
            if (confirmed != documents[kind]) {
                throw new GradleException("${kind} serialized readback differs from merged source.")
            }
            output[kind] = rendered
        }
        return output
    }

    private static Map<String, Map> parseMain(Map<String, String> source, Set<String> lessonIds) {
        if (lessonIds == null || lessonIds.isEmpty()) throw new GradleException('Missing Knowledge A-child ID catalog.')
        Map<String, Map> parsed = new LinkedHashMap<>()
        TYPES.each { String kind, Map spec ->
            Map document = parseYaml(source[kind], "${kind} Phase2/3")
            if (document.keySet() != ([spec.root] as Set) ||
                    !(document[spec.root] instanceof List) || (document[spec.root] as List).isEmpty()) {
                throw new GradleException("Invalid ${kind} root/group list.")
            }
            List groups = document[spec.root] as List
            groups.eachWithIndex { Object rawGroup, int g ->
                if (!(rawGroup instanceof Map)) throw new GradleException("Invalid ${kind} group.")
                Map group = rawGroup as Map
                if (group.keySet() != (['groupID', 'groupName', spec.list] as Set) ||
                        group.groupID != g + 1 || !text(group.groupName) ||
                        !(group[spec.list] instanceof List) || (group[spec.list] as List).isEmpty()) {
                    throw new GradleException("Invalid/duplicate ${kind} groupID/groupName/items at position ${g + 1}.")
                }
                (group[spec.list] as List).eachWithIndex { Object rawItem, int i ->
                    if (!(rawItem instanceof Map)) throw new GradleException("Invalid ${kind} item ${g + 1}/${i + 1}.")
                    Map item = rawItem as Map
                    Set expected = kind == 'interview'
                            ? ['question', 'knowledge', 'readmeRelated'] as Set
                            : kind == 'casestudy'
                            ? ['situation', 'resolve', 'knowledge', 'readmeRelated'] as Set
                            : ['question', 'readmeRelated', 'answers', 'correctAnswerId'] as Set
                    if (item.keySet() != expected || !text(item[spec.label]) ||
                            (kind != 'quiz' && !text(item.knowledge)) ||
                            (kind == 'casestudy' && !text(item.resolve))) {
                        throw new GradleException("Invalid ${kind} content/fields at ${g + 1}/${i + 1}.")
                    }
                    Map related = item.readmeRelated instanceof Map ? item.readmeRelated as Map : null
                    if (related == null || related.keySet() != (['anchorList'] as Set) ||
                            !(related.anchorList instanceof List)) {
                        throw new GradleException("Invalid ${kind} readmeRelated at ${g + 1}/${i + 1}.")
                    }
                    List anchors = related.anchorList as List
                    if (anchors.any { !(it instanceof String) || !lessonIds.contains(it) } ||
                            anchors.toSet().size() != anchors.size()) {
                        throw new GradleException("Unresolved/duplicate ${kind} Knowledge anchor at ${g + 1}/${i + 1}.")
                    }
                    if (kind == 'quiz') {
                        List answers = item.answers instanceof List ? item.answers as List : []
                        if (answers.size() != 4 ||
                                answers.collect { it instanceof Map ? it.id : null } != ['A', 'B', 'C', 'D'] ||
                                !(item.correctAnswerId in ['A', 'B', 'C', 'D']) ||
                                answers.any { !(it instanceof Map) ||
                                        it.keySet() != (['id','answer','explanation'] as Set) ||
                                        !text(it.answer) || !text(it.explanation) }) {
                            throw new GradleException("Invalid ${kind} options at ${g + 1}/${i + 1}.")
                        }
                    }
                }
            }
            parsed[kind] = document
        }
        return parsed
    }

    private static Map parseYaml(String source, String label) {
        if (!source?.trim()) throw new GradleException("Empty YAML: ${label}.")
        try {
            LoaderOptions options = new LoaderOptions()
            options.allowDuplicateKeys = false
            options.maxAliasesForCollections = 50
            Object document = new Yaml(new SafeConstructor(options)).load(source)
            if (!(document instanceof Map)) throw new GradleException("Expected YAML mapping: ${label}.")
            return document as Map
        } catch (Exception error) {
            throw new GradleException("Invalid YAML ${label}: ${error.message}", error)
        }
    }

    /** Preserve every fragment byte. Reject gaps, formulas and non-MAX-FILL chunks. */
    static String concatenate(List<List<Object>> grid, int rows, int cols, String label) {
        StringBuilder joined = new StringBuilder()
        boolean gap = false
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                String value = row < (grid?.size() ?: 0) && col < (grid[row]?.size() ?: 0) && grid[row][col] != null
                        ? grid[row][col].toString() : ''
                if (!value) { gap = true; continue }
                if (gap || value.startsWith('=') ||
                        value.codePointCount(0, value.length()) > CELL_CODEPOINT_LIMIT ||
                        value.length() > CELL_UTF16_LIMIT) {
                    throw new GradleException("Noncontiguous, formula, or oversized ${label} fragment at ${row}/${col}.")
                }
                if (joined.length() > 0 &&
                        previousChunkCodePoints(grid, cols, row, col) < CELL_CODEPOINT_LIMIT &&
                        previousChunkUtf16(grid, cols, row, col) +
                                Character.charCount(value.codePointAt(0)) <= CELL_UTF16_LIMIT) {
                    throw new GradleException("Non-MAX-FILL ${label} fragments.")
                }
                joined.append(value)
            }
        }
        return joined.toString()
    }

    private static int previousChunkCodePoints(List<List<Object>> grid, int cols, int row, int col) {
        int ordinal = row * cols + col - 1
        String prev = grid[ordinal.intdiv(cols)][ordinal % cols].toString()
        return prev.codePointCount(0, prev.length())
    }

    private static int previousChunkUtf16(List<List<Object>> grid, int cols, int row, int col) {
        int ordinal = row * cols + col - 1
        return grid[ordinal.intdiv(cols)][ordinal % cols].toString().length()
    }

    static Set<String> catalogLessonIds(List<List<Object>> rows) {
        Set<String> ids = new LinkedHashSet<>()
        (rows ?: []).eachWithIndex { List<Object> row, int i ->
            String id = row?.size() > 0 ? (row[0] ?: '').toString().trim() : ''
            String title = row?.size() > 1 ? (row[1] ?: '').toString().trim() : ''
            if (id && title && !title.toLowerCase(Locale.ROOT).endsWith('.md')) {
                if (!(id ==~ /[a-z0-9]+(?:-[a-z0-9]+)*-[0-9]{3,}/) || !ids.add(id)) {
                    throw new GradleException("Invalid/duplicate A-child ID at row ${i + 20}: ${id}.")
                }
            }
        }
        if (ids.isEmpty()) throw new GradleException('No canonical Knowledge A-child IDs found.')
        return ids
    }

    private static boolean text(Object value) {
        return value instanceof String && !value.toString().trim().isEmpty()
    }

    private static List<List<List<Object>>> fetch(Sheets client, String spreadsheetId, List<String> ranges) {
        def response = client.spreadsheets().values().batchGet(spreadsheetId)
                .setRanges(ranges).setValueRenderOption('FORMULA').execute()
        if ((response.valueRanges ?: []).size() != ranges.size()) {
            throw new GradleException('Sheet assessment batch returned incomplete ranges.')
        }
        return response.valueRanges.collect { range ->
            (range.values ?: []) as List<List<Object>>
        }
    }
}
