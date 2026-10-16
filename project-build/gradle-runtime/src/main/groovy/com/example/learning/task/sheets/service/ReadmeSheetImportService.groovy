package com.example.learning.task.sheets.service

import com.example.learning.task.readme.internalMenu.service.GenerateInternalReadmeMenuService
import com.google.api.services.sheets.v4.Sheets
import org.gradle.api.GradleException

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Import a two-level Sheet menu AND its Knowledge Markdown as a single
 * source-of-truth snapshot. Parent A rows use B as the AI-authored filename;
 * child B rows use C:M as optional, contiguous Markdown fragments.
 *
 * No AI call is performed here. Only the module's readme/vi/menu is managed.
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
            return [
                    status: parsed.status, tab: session.tabTitle,
                    gid: session.sheetId, location: session.moduleLocation,
                    groups: parsed.groups.size(), lessons: parsed.lessons,
                    missingRows: parsed.missingRows, directory: destination
            ]
        }

        int lastRow = parsed.lastRow as int
        // Do not download large lesson bodies until every filename is present.
        List<List<Object>> knowledge = (client.spreadsheets().values()
                .get(spreadsheetId, "${tab}!C20:M${lastRow}")
                .execute().values ?: []) as List<List<Object>>
        Map<String, String> documents = buildDocuments(
                parsed.groups as List<Map<String, Object>>, knowledge
        )
        Map<String, Object> sync =
                syncDocuments(repositoryRoot, destination, documents, apply)
        return [
                status: sync.status, tab: session.tabTitle,
                gid: session.sheetId, location: session.moduleLocation,
                groups: parsed.groups.size(), lessons: parsed.lessons,
                directory: destination,
                clearedOldFiles: sync.clearedOldFiles
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
                if (title.contains('\n') || title.contains('<')) {
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
                if (groups.isEmpty() || value.contains('\n') || value.contains('<')) {
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
     * C20:M<lastRow> is zero-based by row 20. Missing answers are fine, but
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
            StringBuilder markdown = new StringBuilder("# ${group.title}\n")
            Map<String, String> rawBodies = new LinkedHashMap<>()
            (group.children as List<Map>).eachWithIndex { Map lesson, int index ->
                int row = lesson.row as int
                List<Object> fragments = row - 20 < knowledgeRows.size()
                        ? (knowledgeRows[row - 20] ?: [])
                        : []
                if (fragments.size() > 11) {
                    throw new GradleException("Unexpected Knowledge data after M${row}.")
                }
                boolean sawEmpty = false
                StringBuilder answer = new StringBuilder()
                (0..<11).each { int col ->
                    String fragment = col < fragments.size() && fragments[col] != null
                            ? fragments[col].toString() : ''
                    if (!fragment) {
                        sawEmpty = true
                    }
                    else {
                        if (sawEmpty) {
                            throw new GradleException(
                                    "Knowledge fragments have a gap at row ${row}, C:M."
                            )
                        }
                        answer.append(fragment)
                    }
                }
                String anchor = "${slug}-${String.format('%03d', index + 1)}"
                markdown.append("\n## <a id=\"${anchor}\">${lesson.title}</a>\n")
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

    static String slugFromStem(String stem) {
        if (!(stem ==~ /(?i)[a-z][a-z0-9]*/)) {
            throw new GradleException("Invalid Markdown file stem: '${stem}'.")
        }
        return stem.replaceAll(/([A-Z]+)([A-Z][a-z])/, '$1-$2')
                .replaceAll(/([a-z0-9])([A-Z])/, '$1-$2')
                .toLowerCase(Locale.ROOT)
    }

    static File resolveMenuDirectory(File root, String moduleLocation) {
        File roadmap = RoadmapSheetImportService.resolveDestination(root, moduleLocation)
        File module = new File(root.canonicalFile, moduleLocation).canonicalFile
        File menu = new File(module, 'readme/vi/menu').canonicalFile
        if (!menu.toPath().startsWith(module.toPath()) ||
                !roadmap.toPath().startsWith(module.toPath())) {
            throw new GradleException("Unsafe README destination: ${menu}.")
        }
        return menu
    }

    /**
     * Full-snapshot synchronization: permanently delete the entire existing
     * readme/vi/menu tree BEFORE installing the replacement. Never merge
     * files one by one: stale files/subfolders would survive. No backup of
     * the old menu is created.
     *
     * Never clear on DRY_RUN, skipped/invalid Sheet data or UNCHANGED.
     * Generate + verify the complete replacement BEFORE clearing.
     */
    static Map<String, Object> syncDocuments(
            File repositoryRoot,
            File menuDirectory,
            Map<String, String> expected,
            boolean apply
    ) {
        Path moduleRoot = new File(repositoryRoot.canonicalFile, 'module')
                .canonicalFile.toPath()
        Path targetPath = menuDirectory.canonicalFile.toPath()
        List<String> parts = []
        targetPath.each { Path part -> parts.add(part.toString()) }
        if (!targetPath.startsWith(moduleRoot) || parts.size() < 4 ||
                parts.takeRight(3) != ['readme', 'vi', 'menu'] ||
                targetPath == moduleRoot || Files.isSymbolicLink(menuDirectory.toPath())) {
            throw new GradleException("Unsafe README menu deletion target: ${menuDirectory}")
        }
        if (menuDirectory.exists() && !menuDirectory.isDirectory()) {
            throw new GradleException("README menu destination is not a directory: ${menuDirectory}")
        }
        if (expected == null || expected.isEmpty()) {
            throw new GradleException(
                    'Refusing to clear README menu without a complete, nonempty replacement snapshot.'
            )
        }
        Map<String, String> previous = readTree(menuDirectory)
        if (previous == expected) {
            return [status: 'UNCHANGED', clearedOldFiles: 0]
        }
        if (!apply) {
            return [status: 'DRY_RUN', clearedOldFiles: 0]
        }

        Path staged = null
        boolean deletedOldMenu = false
        try {
            Files.createDirectories(menuDirectory.parentFile.toPath())
            staged = Files.createTempDirectory(menuDirectory.parentFile.toPath(), '.menu-sheet-stage-')
            expected.each { String name, String content ->
                File file = new File(staged.toFile(), name)
                if (!file.canonicalFile.toPath().startsWith(staged.toFile().canonicalFile.toPath())) {
                    throw new GradleException("Unsafe staged README path: ${name}")
                }
                Files.createDirectories(file.parentFile.toPath())
                Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8))
            }
            if (readTree(staged.toFile()) != expected) {
                throw new GradleException('Staged README exact readback failed.')
            }
            if (readTree(menuDirectory) != previous) {
                throw new GradleException('README menu changed during preflight; aborting.')
            }
            if (menuDirectory.exists()) {
                // The replacement is fully staged and verified. The user
                // explicitly requires permanent deletion, without backups.
                deletedOldMenu = true
                deleteTree(menuDirectory)
            }
            if (Files.exists(menuDirectory.toPath())) {
                throw new GradleException(
                        "README menu was not cleared before replacement: ${menuDirectory}"
                )
            }
            Files.move(staged, menuDirectory.toPath(), StandardCopyOption.ATOMIC_MOVE)
            staged = null
            if (readTree(menuDirectory) != expected) {
                throw new GradleException('Installed README readback failed.')
            }
            return [
                    status: previous.isEmpty() ? 'IMPORTED' : 'UPDATED',
                    clearedOldFiles: previous.size()
            ]
        }
        catch (Exception error) {
            String message = "Unable to sync README menu ${menuDirectory}: ${error.message}"
            if (deletedOldMenu) {
                message += ' Old menu deletion was requested and cannot be undone.'
                if (staged != null && Files.exists(staged)) {
                    message += " Verified new content remains at ${staged}."
                }
            }
            throw new GradleException(message, error)
        }
        finally {
            // Keep prepared new content if installation failed AFTER deleting
            // the previous menu; this is not a backup of the old content.
            if (!deletedOldMenu && staged != null && Files.exists(staged)) {
                deleteTree(staged.toFile())
            }
        }
    }

    static Map<String, String> readTree(File root) {
        Map<String, String> result = new LinkedHashMap<>()
        if (!root.exists()) {
            return result
        }
        if (!root.isDirectory()) {
            throw new GradleException("Not a README directory: ${root}")
        }
        Path base = root.canonicalFile.toPath()
        def stream = Files.walk(root.toPath())
        try {
            stream.filter { Path file -> Files.isRegularFile(file) }.forEach { Path file ->
                if (!file.toFile().canonicalFile.toPath().startsWith(base)) {
                    throw new GradleException("README path escapes directory: ${file}")
                }
                String relative = root.toPath().relativize(file).toString()
                        .replace(File.separatorChar, '/' as char)
                result[relative] = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
            }
        }
        finally {
            stream.close()
        }
        return result
    }

    private static void deleteTree(File root) {
        def stream = Files.walk(root.toPath())
        try {
            stream.sorted(Comparator.reverseOrder()).forEach { Path item ->
                Files.deleteIfExists(item)
            }
        }
        finally {
            stream.close()
        }
    }

    private static String cell(List<List<Object>> rows, int row, int col) {
        return row < rows.size() ? cellText(rows[row], col) : ''
    }

    private static String cellText(List<Object> row, int col) {
        return row != null && col < row.size() && row[col] != null
                ? row[col].toString().trim() : ''
    }
}
