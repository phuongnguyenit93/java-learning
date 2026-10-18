package com.example.learning.task.sheets.service

import com.example.learning.task.readme.internalMenu.service.GenerateInternalReadmeMenuService
import com.example.learning.setup.root.readmeMetadata.service.ReadmeMetadataSyncService
import com.google.api.services.sheets.v4.Sheets
import org.gradle.api.GradleException


/**
 * Import a two-level Sheet menu AND its Knowledge Markdown as a single
 * source-of-truth snapshot. Parent A rows use B as the AI-authored filename;
 * child B rows use C:N as optional, contiguous Markdown fragments.
 *
 * No AI call is performed here. Module content/readme/docs/vi/menu is the
 * Sheet-owned snapshot; the existing README metadata service synchronizes
 * the matching vi/knowledge-metadata.yml using generated section IDs and O.
 */
class ReadmeSheetImportService {

    private static final String MENU_HEADER = 'Menu Cấp 1'
    private static final String LESSON_HEADER = 'Menu Cấp 2'
    private static final String FILENAME_PATTERN = /(?i)[a-z][a-z0-9]*\.md/

    Map<String, Object> execute(
            File repositoryRoot,
            Map<String, Object> session,
            boolean apply
    ) {
        File destination = resolveMenuDirectory(
                repositoryRoot, session.moduleLocation as String
        )
        Sheets client = session.client as Sheets
        String spreadsheetId = session.spreadsheetId as String
        String tab = "'${(session.tabTitle as String).replace("'", "''")}'"
        List<List<Object>> menuRows = (client.spreadsheets().values()
                .get(spreadsheetId, "${tab}!A19:B")
                .execute().values ?: []) as List<List<Object>>

        Map<String, Object> parsed = parseMenu(menuRows)
        if (parsed.status != 'READY') {
            Map<String, Object> cleared = parsed.status == 'SKIPPED_EMPTY_MENU'
                    ? syncDocuments(repositoryRoot, destination, [:], apply)
                    : [status: parsed.status, clearedOldFiles: 0]
            return [
                    status: cleared.status == 'CLEARED_EMPTY' ?
                            'CLEARED_EMPTY' : parsed.status, tab: session.tabTitle,
                    gid: session.sheetId, location: session.moduleLocation,
                    groups: parsed.groups.size(), lessons: parsed.lessons,
                    missingRows: parsed.missingRows, directory: destination,
                    clearedOldFiles: cleared.clearedOldFiles
            ]
        }

        int lastRow = parsed.lastRow as int
        // Do not download large lesson bodies until every filename is present.
        List<List<Object>> knowledge = (client.spreadsheets().values()
                .get(spreadsheetId, "${tab}!C20:N${lastRow}")
                .execute().values ?: []) as List<List<Object>>
        Map<String, String> documents = buildDocuments(
                parsed.groups as List<Map<String, Object>>, knowledge
        )
        // O is reserved for per-lesson Difficulty from STEP 5. Prepare
        // metadata before replacing any README file, so invalid values or
        // conflicting metadata cannot destroy a previous content snapshot.
        List<List<Object>> difficultyRows = (client.spreadsheets().values()
                .get(spreadsheetId, "${tab}!O20:O${lastRow}")
                .setValueRenderOption('FORMULA')
                .execute().values ?: []) as List<List<Object>>
        Map<String, List<String>> difficulties = buildSectionDifficulties(
                parsed.groups as List<Map<String, Object>>, difficultyRows)
        File moduleDir = destination.parentFile.parentFile.parentFile.parentFile.parentFile
        ReadmeMetadataSyncService metadataService =
                new ReadmeMetadataSyncService(org.gradle.api.logging.Logging.getLogger(ReadmeSheetImportService))
        String preparedMetadata = metadataService.prepareImportedMetadata(
                moduleDir, documents, difficulties)
        Map<String, Object> sync =
                syncDocuments(repositoryRoot, destination, documents, apply)
        Map metadataSync = metadataService.syncImportedMetadata(
                moduleDir, preparedMetadata, apply)
        return [
                status: sync.status, tab: session.tabTitle,
                gid: session.sheetId, location: session.moduleLocation,
                groups: parsed.groups.size(), lessons: parsed.lessons,
                directory: destination,
                clearedOldFiles: sync.clearedOldFiles,
                metadataStatus: metadataSync.status,
                metadataFile: metadataSync.file
        ]
    }

    /**
     * Menu rows start at A19:B19 (header). A20 is the first parent.
     * Missing B filename at ANY parent skips this importer, without writes.
     */
    static Map<String, Object> parseMenu(List<List<Object>> rows) {
        // The first data row is the quick skip contract, even on a tab
        // whose Menu header has not been prepared yet.
        if (!cell(rows, 1, 0)) {
            return [status: 'SKIPPED_EMPTY_MENU', groups: [], lessons: 0, missingRows: []]
        }
        if (cell(rows, 0, 0) != MENU_HEADER ||
                cell(rows, 0, 1) != LESSON_HEADER) {
            throw new GradleException(
                    "Invalid Menu headers at A19:B19; expected '${MENU_HEADER}', '${LESSON_HEADER}'."
            )
        }
        List<Map<String, Object>> groups = []
        List<Integer> missingRows = []
        boolean gap = false
        int lessonCount = 0
        int lastRow = 19

        rows.drop(1).eachWithIndex { List<Object> row, int index ->
            int sheetRow = index + 20
            String title = cellText(row, 0)
            String value = cellText(row, 1)
            if (!title && !value) {
                gap = true
                return
            }
            if (gap) {
                throw new GradleException(
                        "Menu has a gap before row ${sheetRow}; expected contiguous A/B rows."
                )
            }
            lastRow = sheetRow
            if (title) {
                if (title.contains('\n') || title.contains('\r')) {
                    throw new GradleException("Unsafe Menu Cấp 1 title at A${sheetRow}.")
                }
                if (!value) {
                    missingRows.add(sheetRow)
                }
                groups.add([
                        title: title, filename: value, parentRow: sheetRow,
                        children: []
                ])
            }
            else {
                if (groups.isEmpty() || value.contains('\n') || value.contains('\r')) {
                    throw new GradleException("Invalid Menu Cấp 2 at B${sheetRow}.")
                }
                List<Map<String, Object>> children =
                        groups.last().children as List<Map<String, Object>>
                children.add([title: value, row: sheetRow])
                lessonCount++
            }
        }
        // Filename availability is a prerequisite, not a partial import.
        if (!missingRows.isEmpty()) {
            return [
                    status: 'SKIPPED_MISSING_FILENAME', groups: groups,
                    lessons: lessonCount, missingRows: missingRows
            ]
        }
        Set<String> filenames = new LinkedHashSet<>()
        Set<String> slugs = new LinkedHashSet<>()
        groups.each { Map group ->
            String filename = group.filename as String
            if (!(filename ==~ FILENAME_PATTERN)) {
                throw new GradleException(
                        "Invalid English Markdown filename '${filename}' at B${group.parentRow}. " +
                                'Use a short alphanumeric PascalCase name ending .md.'
                )
            }
            if (!filenames.add(filename.toLowerCase(Locale.ROOT))) {
                throw new GradleException(
                        "Duplicate Menu filename '${filename}' at B${group.parentRow}."
                )
            }
            String slug = slugFromStem(filename.substring(0, filename.length() - 3))
            if (!slugs.add(slug)) {
                throw new GradleException(
                        "Duplicate README section anchor prefix '${slug}' at B${group.parentRow}."
                )
            }
            if ((group.children as List).isEmpty()) {
                throw new GradleException(
                        "Menu Cấp 1 at A${group.parentRow} has no Menu Cấp 2."
                )
            }
        }
        return [
                status: 'READY', groups: groups, lessons: lessonCount,
                missingRows: [], lastRow: lastRow
        ]
    }

    /**
     * C20:N<lastRow> is zero-based by row 20. Missing answers are fine, but
     * fragments may not have holes. Raw fragments concatenate with NO joiner.
     */
    static Map<String, String> buildDocuments(
            List<Map<String, Object>> groups,
            List<List<Object>> knowledgeRows
    ) {
        Map<String, String> result = new LinkedHashMap<>()
        GenerateInternalReadmeMenuService renderer =
                new GenerateInternalReadmeMenuService()
        groups.eachWithIndex { Map group, int groupIndex ->
            String filename = group.filename as String
            String stem = filename.substring(0, filename.length() - 3)
            String slug = slugFromStem(stem)
            String relative = "${groupIndex + 1}.${stem}/${filename}"
            StringBuilder markdown = new StringBuilder("# ${escapeMenuTitle(group.title)}\n")
            Map<String, String> rawBodies = new LinkedHashMap<>()
            (group.children as List<Map>).eachWithIndex { Map lesson, int index ->
                int row = lesson.row as int
                List<Object> fragments = row - 20 < knowledgeRows.size()
                        ? (knowledgeRows[row - 20] ?: [])
                        : []
                if (fragments.size() > 12) {
                    throw new GradleException("Unexpected Knowledge data after N${row}.")
                }
                boolean sawEmpty = false
                StringBuilder answer = new StringBuilder()
                (0..<12).each { int col ->
                    String fragment = col < fragments.size() && fragments[col] != null
                            ? fragments[col].toString() : ''
                    if (!fragment) {
                        sawEmpty = true
                    }
                    else {
                        if (sawEmpty) {
                            throw new GradleException(
                                    "Knowledge fragments have a gap at row ${row}, C:N."
                            )
                        }
                        answer.append(fragment)
                    }
                }
                String anchor = "${slug}-${String.format('%03d', index + 1)}"
                markdown.append("\n## <a id=\"${anchor}\">${escapeMenuTitle(lesson.title)}</a>\n")
                if (answer.length() > 0) {
                    // Use an opaque marker while the existing internal-menu
                    // renderer normalizes layout. Replace AFTER rendering to
                    // preserve lesson Unicode, whitespace and code fences
                    // verbatim, including headings that resemble anchors.
                    String marker = "COSREADME${UUID.randomUUID().toString().replace('-', '')}BODY"
                    rawBodies[marker] = answer.toString()
                    markdown.append('\n').append(marker)
                }
                markdown.append('\n')
            }
            String rendered = renderer.render(markdown.toString(), 'vi')
            rawBodies.each { String marker, String body ->
                rendered = rendered.replace(marker, body)
            }
            result[relative] = rendered
        }
        return result
    }

    /** Align O<child> values with the same deterministic H2/ID order. */
    static Map<String, List<String>> buildSectionDifficulties(
            List<Map<String, Object>> groups, List<List<Object>> difficultyRows) {
        Map<String, List<String>> result = new LinkedHashMap<>()
        groups.eachWithIndex { Map group, int index ->
            String filename = group.filename as String
            String stem = filename.substring(0, filename.length() - 3)
            String source = "${index + 1}.${stem}/${filename}"
            result[source] = (group.children as List<Map>).collect { Map child ->
                int offset = (child.row as int) - 20
                List<Object> entry = offset >= 0 && offset < difficultyRows.size()
                        ? (difficultyRows[offset] ?: []) : []
                String value = entry ? (entry[0]?.toString()?.trim()?.toUpperCase(Locale.ROOT) ?: '') : ''
                if (value && !ReadmeMetadataSyncService.ALLOWED_DIFFICULTIES.contains(value)) {
                    throw new GradleException("Invalid Difficulty at O${child.row}: '${value}'.")
                }
                return value
            }
        }
        return result
    }

    static String slugFromStem(String stem) {
        if (!(stem ==~ /(?i)[a-z][a-z0-9]*/)) {
            throw new GradleException("Invalid Markdown file stem: '${stem}'.")
        }
        return stem.replaceAll(/([A-Z]+)([A-Z][a-z])/, '$1-$2')
                .replaceAll(/([a-z0-9])([A-Z])/, '$1-$2')
                .toLowerCase(Locale.ROOT)
    }

    /** Titles are plain Sheet text, including Java generic syntax like List<T>. */
    private static String escapeMenuTitle(String title) {
        return title.replace('&', '&amp;')
                .replace('<', '&lt;')
                .replace('>', '&gt;')
                .replace('"', '&quot;')
                .replace("'", '&#39;')
                .replace('[', '&#91;')
                .replace(']', '&#93;')
    }

    static File resolveMenuDirectory(File root, String moduleLocation) {
        File roadmap = RoadmapSheetImportService.resolveDestination(root, moduleLocation)
        File module = new File(root.canonicalFile, moduleLocation).canonicalFile
        File menu = new File(module, 'content/readme/docs/vi/menu').canonicalFile
        if (!menu.toPath().startsWith(module.toPath()) ||
                !roadmap.toPath().startsWith(module.toPath())) {
            throw new GradleException("Unsafe README destination: ${menu}.")
        }
        return menu
    }

    /**
     * Sheet-only full snapshot of content/readme/docs/vi/menu. All previous files,
     * subdirectories and stale artifacts are permanently deleted on APPLY,
     * even when the regenerated documents are byte-identical. No backup.
     */
    static Map<String, Object> syncDocuments(
            File repositoryRoot,
            File menuDirectory,
            Map<String, String> expected,
            boolean apply
    ) {
        return SheetSnapshotReplaceService.replace(
                repositoryRoot, menuDirectory, expected, apply
        )
    }

    static Map<String, String> readTree(File root) {
        return SheetSnapshotReplaceService.readTree(root)
    }

    private static String cell(List<List<Object>> rows, int row, int col) {
        return row < rows.size() ? cellText(rows[row], col) : ''
    }

    private static String cellText(List<Object> row, int col) {
        return row != null && col < row.size() && row[col] != null
                ? row[col].toString().trim() : ''
    }
}
