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
 * No AI call is performed here. The VI knowledge/ and metadata/ directories
 * are cleaned and refilled together from Sheet data, never merged with the
 * previous module. Interview/Quiz/Video and other siblings are not touched.
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
                    ? syncDocuments(repositoryRoot, destination, [:], apply, [:])
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
        MenuLessonIdService.validate(parsed.groups as List<Map>, knowledge)
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
        // AB is a per-lesson optional STEP 5 authoring field. Blank cells
        // preserve human-edited metadata; explicit TRUE/FALSE override it.
        List<List<Object>> importanceRows = (client.spreadsheets().values()
                .get(spreadsheetId, "${tab}!AB20:AB${lastRow}")
                .setValueRenderOption('UNFORMATTED_VALUE')
                .execute().values ?: []) as List<List<Object>>
        Map<String, List<Boolean>> importances = buildSectionImportances(
                parsed.groups as List<Map<String, Object>>, importanceRows)
        File moduleDir = destination.parentFile.parentFile.parentFile.parentFile.parentFile
        ReadmeMetadataSyncService metadataService =
                new ReadmeMetadataSyncService(org.gradle.api.logging.Logging.getLogger(ReadmeSheetImportService))
        String preparedMetadata = metadataService.prepareImportedMetadata(
                moduleDir, documents, difficulties, importances)
        Map<String, String> metadataDocuments =
                metadataService.importedChapterMetadataFiles(preparedMetadata)
        Map<String, Object> sync = syncDocuments(
                repositoryRoot, destination, documents, apply, metadataDocuments)
        return [
                status: sync.status, tab: session.tabTitle,
                gid: session.sheetId, location: session.moduleLocation,
                groups: parsed.groups.size(), lessons: parsed.lessons,
                directory: destination,
                clearedOldFiles: sync.clearedOldFiles,
                metadataStatus: sync.status,
                metadataFile: metadataDocuments.isEmpty() ? null :
                        new File(destination, metadataDocuments.keySet().first())
        ]
    }

    /**
     * Menu rows start at A19:B19 (header). A20 is the first parent.
     * Missing B filename at ANY parent skips this importer, without writes.
     */
    static Map<String, Object> parseMenu(List<List<Object>> rows) {
        boolean hasMenuHeader = cell(rows, 0, 0) == MENU_HEADER &&
                cell(rows, 0, 1) == LESSON_HEADER
        if (!hasMenuHeader && rows.every { List<Object> cells ->
            cells == null || cells.every { it == null || !it.toString().trim() }
        }) {
            return [status: 'SKIPPED_NO_MENU_SCHEMA', groups: [], lessons: 0, missingRows: []]
        }
        if (!hasMenuHeader) {
            throw new GradleException(
                    "Invalid Menu headers at A19:B19; expected '${MENU_HEADER}', '${LESSON_HEADER}'."
            )
        }
        if (!cell(rows, 1, 0)) {
            if (cell(rows, 1, 1) || rows.drop(2).any { List<Object> row ->
                (row ?: []).any { Object cell -> cell != null && cell.toString().trim() }
            }) {
                throw new GradleException('Menu has data after empty first parent row A20.')
            }
            return [status: 'SKIPPED_EMPTY_MENU', groups: [], lessons: 0, missingRows: []]
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
            // A child may now hold a stable ID. B on a parent holds a .md filename.
            boolean childWithId = title && MenuLessonIdService.isLessonId(title) &&
                    ! (value ==~ FILENAME_PATTERN) && !groups.isEmpty()
            if (title && !childWithId) {
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
                if (groups.isEmpty() || !value ||
                        value.contains('\n') || value.contains('\r')) {
                    throw new GradleException("Invalid Menu Cấp 2 at B${sheetRow}.")
                }
                List<Map<String, Object>> children =
                        groups.last().children as List<Map<String, Object>>
                children.add([title: value, row: sheetRow,
                              id: childWithId ? title : null])
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
            String relative = "${groupIndex + 1}.${stem}/knowledge/${filename}"
            StringBuilder markdown = new StringBuilder("# ${escapeMenuTitle(group.title)}\n")
            Map<String, String> rawBodies = new LinkedHashMap<>()
            (group.children as List<Map>).each { Map lesson ->
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
                String anchor = lesson.id as String
                if (!MenuLessonIdService.isLessonId(anchor) || !anchor.startsWith(slug + '-')) {
                    throw new GradleException("Missing or invalid lesson ID at A${row}.")
                }
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
            String source = "${index + 1}.${stem}/knowledge/${filename}"
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

    /** A blank AB cell means the new snapshot uses default false. */
    static Map<String, List<Boolean>> buildSectionImportances(
            List<Map<String, Object>> groups, List<List<Object>> rows) {
        Map<String, List<Boolean>> result = new LinkedHashMap<>()
        groups.eachWithIndex { Map group, int index ->
            String filename = group.filename as String
            String stem = filename.substring(0, filename.length() - 3)
            String source = "${index + 1}.${stem}/knowledge/${filename}"
            result[source] = (group.children as List<Map>).collect { Map child ->
                int offset = (child.row as int) - 20
                List<Object> entry = offset >= 0 && offset < rows.size()
                        ? (rows[offset] ?: []) : []
                Object value = entry ? entry[0] : null
                if (value == null || (value instanceof String && value.trim().isEmpty())) {
                    return null
                }
                if (value instanceof Boolean) {
                    return value
                }
                if (value instanceof String && value.trim().equalsIgnoreCase('TRUE')) {
                    return true
                }
                if (value instanceof String && value.trim().equalsIgnoreCase('FALSE')) {
                    return false
                }
                throw new GradleException("Invalid isImportant at AB${child.row}: '${value}'. Use TRUE or FALSE.")
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
     * Chapter snapshots: Knowledge and optionally its derived Metadata, all
     * chapters in ONE clean/fill transaction. A null metadata map means that
     * the legacy knowledge-only API must not touch metadata.
     * Never replace the whole menu tree or a chapter folder: those directories
     * contain independent content owned by other importers and people.
     *
     * Legacy <chapter>/<chapter>.md content must be migrated first. Refuse to
     * generate a duplicate instead of silently deleting or leaving stale data.
     */
    static Map<String, Object> syncDocuments(
            File repositoryRoot,
            File menuDirectory,
            Map<String, String> expected,
            boolean apply,
            Map<String, String> metadataDocuments = null
    ) {
        File moduleRoot = new File(repositoryRoot.canonicalFile, 'module').canonicalFile
        File menu = menuDirectory.canonicalFile
        if (!menu.toPath().startsWith(moduleRoot.toPath()) ||
                !menu.toPath().toString().replace('\\', '/')
                        .endsWith('/content/readme/docs/vi/menu')) {
            throw new GradleException('Unsafe Knowledge menu destination: ' + menu)
        }
        Map<String, Map<String, String>> byChapter = new LinkedHashMap<>()
        expected.each { String relative, String body ->
            def match = (relative =~ /\A([0-9]+\.[A-Za-z][A-Za-z0-9]*)\/knowledge\/([A-Za-z][A-Za-z0-9]*\.md)\z/)
            if (!match.matches() || body == null) {
                throw new GradleException('Invalid Knowledge-only snapshot path: ' + relative)
            }
            String chapter = match.group(1)
            String filename = match.group(2)
            if (!byChapter.containsKey(chapter)) byChapter[chapter] = [:]
            byChapter[chapter][filename] = body
        }
        if (menu.isDirectory()) {
            menu.listFiles().findAll { File file -> file.isDirectory() &&
                    (file.name ==~ /[0-9]+\..+/) }.each { File chapter ->
                // A chapter may also contain authored YAML/Markdown siblings.
                // Only a nested knowledge/ directory belongs to this task.
                File[] legacy = chapter.listFiles()?.findAll {
                    it.isFile() &&
                            it.name.toLowerCase(Locale.ROOT).endsWith('.md') &&
                            !it.name.equalsIgnoreCase('chapter-links.md')
                }
                if (legacy) {
                    throw new GradleException('Legacy Knowledge must move into knowledge/ first: ' +
                            legacy.first())
                }
                if (new File(chapter, 'knowledge').exists() &&
                        !byChapter.containsKey(chapter.name)) byChapter[chapter.name] = [:]
            }
        }
        Map<File, Map<String, String>> targets = new LinkedHashMap<>()
        byChapter.each { String chapter, Map<String, String> docs ->
            targets[new File(menu, chapter + '/knowledge')] = docs
        }
        if (metadataDocuments != null) {
            Map<String, Map<String, String>> metadataByChapter = new LinkedHashMap<>()
            metadataDocuments.each { String relative, String content ->
                def match = (relative =~ /\A([0-9]+\.[A-Za-z][A-Za-z0-9]*)\/metadata\/knowledge-metadata\.yml\z/)
                if (!match.matches() || content == null) {
                    throw new GradleException('Invalid Sheet Metadata snapshot path: ' + relative)
                }
                metadataByChapter[match.group(1)] = ['knowledge-metadata.yml': content]
            }
            if (menu.isDirectory()) {
                menu.listFiles().findAll { File chapter -> chapter.isDirectory() &&
                        (chapter.name ==~ /[0-9]+\.[A-Za-z0-9][A-Za-z0-9 _.-]*/) &&
                        new File(chapter, 'metadata').exists() }.each { File chapter ->
                    if (!metadataByChapter.containsKey(chapter.name)) {
                        metadataByChapter[chapter.name] = [:]
                    }
                }
            }
            metadataByChapter.each { String chapter, Map<String, String> files ->
                targets[new File(menu, chapter + '/metadata')] = files
            }
            // Legacy module-wide VI Metadata is obsolete, never a source of truth.
            File module = menu.parentFile.parentFile.parentFile.parentFile.parentFile
            File legacy = new File(module, 'content/readme/metadata/vi')
            if (legacy.exists()) targets[legacy] = [:]
        }
        Map result = SheetSnapshotReplaceService.replaceAll(repositoryRoot, targets, apply)
        return result + [writtenFiles: apply ? expected.size() : 0]
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
