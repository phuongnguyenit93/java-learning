package com.example.learning.task.sheets.service

import com.example.learning.setup.module.roadmap.service.RoadmapSchemaService
import com.google.api.services.sheets.v4.Sheets
import org.gradle.api.GradleException
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Imports the sheet's Roadmap Title/Purpose/Objectives into a Vietnamese
 * roadmap.yml. It is deliberately independent of Gradle module discovery:
 * the verified Sheet I3 path determines the documentation destination.
 *
 * No AI generation, mutation of Google Sheets or module metadata is involved.
 * Google Sheets is authoritative for the complete Roadmap milestone list.
 */
class RoadmapSheetImportService {

    private static final String MENU_HEADING = 'Khu vực Menu'
    private static final List<String> EXPECTED_HEADERS =
            ['Tiêu đề', 'Mục đích', 'Mục tiêu học tập'].asImmutable()
    private static final Pattern ROADMAP_LINE =
            Pattern.compile('(?m)^roadmap:[^\\r\\n]*(?:\\r?\\n|$)')

    Map<String, Object> execute(
            File repositoryRoot,
            String fileUrl,
            String tabGid,
            boolean apply
    ) {
        Map<String, Object> session =
                new SheetsMetadataReaderService().openTab(fileUrl, tabGid)
        return execute(repositoryRoot, session, apply)
    }

    /**
     * Shared entrypoint for module-local and root batch orchestration.
     * The caller owns tab authentication/selection and may reuse a Sheets client.
     */
    Map<String, Object> execute(
            File repositoryRoot,
            Map<String, Object> session,
            boolean apply
    ) {
        File destination =
                resolveDestination(repositoryRoot, session.moduleLocation as String)

        Sheets client = session.client as Sheets
        String spreadsheetId = session.spreadsheetId as String
        String quotedTab = "'${(session.tabTitle as String).replace("'", "''")}'"

        // A blank F3:H3 means this tab has not started its Roadmap.
        // Check the first data row before scanning the rest of the tab.
        List<List<Object>> firstRows = (client.spreadsheets().values()
                .get(spreadsheetId, "${quotedTab}!F2:H3")
                .execute().values ?: []) as List<List<Object>>
        List<String> headers = (0..<3).collect {
            int column -> cell(firstRows, 0, column)
        }
        if (headers != EXPECTED_HEADERS) {
            throw new GradleException(
                    "Roadmap header mismatch at F2:H2: ${headers}; expected ${EXPECTED_HEADERS}."
            )
        }
        if (isFirstMilestoneEmpty(firstRows)) {
            return [
                    tab       : session.tabTitle,
                    gid       : session.sheetId,
                    location  : session.moduleLocation,
                    file      : destination,
                    milestones: 0,
                    status    : 'SKIPPED_EMPTY'
            ]
        }

        // One batch request for the remaining Roadmap data and actual Menu
        // boundary instead of two independent reads per tab.
        def batch = client.spreadsheets().values()
                .batchGet(spreadsheetId)
                .setRanges(["${quotedTab}!A1:A500", "${quotedTab}!F2:H500"])
                .execute()
        List<List<Object>> aColumn =
                (batch.valueRanges[0]?.values ?: []) as List<List<Object>>

        List<Integer> menuRows = []
        aColumn.eachWithIndex { List<Object> row, int index ->
            if (row && row[0]?.toString()?.trim() == MENU_HEADING) {
                menuRows.add(index + 1)
            }
        }
        if (menuRows.size() != 1 || menuRows[0] < 5) {
            throw new GradleException(
                    "Expected one '${MENU_HEADING}' boundary after Roadmap in A1:A500; found ${menuRows}."
            )
        }

        int roadmapEnd = menuRows[0] - 1
        List<List<Object>> roadmapValues =
                ((batch.valueRanges[1]?.values ?: []) as List<List<Object>>)
                        .take(roadmapEnd - 1)

        List<String> fullHeaders = (0..<3).collect {
            int column -> cell(roadmapValues, 0, column)
        }
        if (fullHeaders != EXPECTED_HEADERS) {
            throw new GradleException(
                    "Roadmap header changed during import: ${fullHeaders}; expected ${EXPECTED_HEADERS}."
            )
        }

        List<Map<String, Object>> milestones =
                convertRoadmap(roadmapValues.drop(1))
        if (milestones.isEmpty()) {
            throw new GradleException(
                    "Roadmap is empty at F3:H${roadmapEnd}; nothing to import."
            )
        }

        File existingFile = destination
        String before = existingFile.isFile()
                ? existingFile.getText('UTF-8')
                : null
        if (existingFile.exists() && !existingFile.isFile()) {
            throw new GradleException("Roadmap destination is not a file: ${destination}")
        }

        Map<String, Object> sync =
                syncRoadmap(repositoryRoot, destination, before, milestones, apply)

        return [
                tab       : session.tabTitle,
                gid       : session.sheetId,
                location  : session.moduleLocation,
                file      : destination,
                milestones: milestones.size(),
                status    : sync.status,
                backup    : sync.backup
        ]
    }

    /**
     * Replace previously generated Roadmap values with the complete Sheet
     * snapshot, including IDs derived from the current titles. Do not carry
     * stale milestones or relationships not represented in the Sheet.
     * UPDATE creates a verified, ignored on-disk backup BEFORE replacement.
     */
    static Map<String, Object> syncRoadmap(
            File repositoryRoot,
            File destination,
            String before,
            List<Map<String, Object>> milestones,
            boolean apply
    ) {
        String action = determineAction(before, milestones)
        if (action == 'UNCHANGED') {
            return [status: 'UNCHANGED', backup: null]
        }
        String proposed = buildYaml(before, milestones)
        if (!apply) {
            return [status: 'DRY_RUN', backup: null]
        }

        // Abort if another process changed the destination after preflight.
        String latest = destination.isFile()
                ? destination.getText('UTF-8')
                : null
        if (latest != before) {
            throw new GradleException("Roadmap changed during import preflight: ${destination}")
        }

        File backup = null
        if (action == 'UPDATE') {
            backup = backupExistingRoadmap(repositoryRoot, destination, before)
        }
        // Check once more following backup creation and before replacement.
        latest = destination.isFile()
                ? destination.getText('UTF-8')
                : null
        if (latest != before) {
            throw new GradleException("Roadmap changed after backup was made: ${destination}")
        }

        writeAtomically(destination, proposed)
        Map parsed = readYaml(destination.getText('UTF-8'))
        if (parsed.roadmap != milestones) {
            throw new GradleException("Roadmap exact YAML data readback failed: ${destination}")
        }
        return [status: action == 'UPDATE' ? 'UPDATED' : 'IMPORTED', backup: backup]
    }

    /**
     * firstRows represents F2:H3. Google Sheets may omit entirely empty
     * trailing rows or cells; whitespace-only values are also empty.
     * A partially populated F3:H3 must NOT be skipped: validation will fail.
     */
    static boolean isFirstMilestoneEmpty(List<List<Object>> firstRows) {
        return (0..<3).every { int column ->
            !cell(firstRows ?: [], 1, column)
        }
    }

    /**
     * Only write below the repository's module/ directory, regardless of
     * whether a Gradle module exists there. Reject traversal and symlink
     * escape using canonical paths before creating any directories.
     */
    static File resolveDestination(File repositoryRoot, String location) {
        if (!location || !location.startsWith('module/') ||
                location.contains('\\') || location.contains(':') ||
                location.startsWith('/')) {
            throw new GradleException("Unsafe Module Location at I3: '${location}'.")
        }
        List<String> segments = location.split('/', -1).toList()
        if (segments.size() < 2 || segments.any {
            String part -> !(part ==~ /[A-Za-z0-9._-]+/) ||
                    part == '.' || part == '..'
        }) {
            throw new GradleException("Unsafe Module Location at I3: '${location}'.")
        }

        try {
            File moduleRoot = new File(repositoryRoot.canonicalFile, 'module').canonicalFile
            File destination = new File(
                    repositoryRoot.canonicalFile,
                    "${location}/roadmap/vi/roadmap.yml"
            ).canonicalFile
            if (!destination.toPath().startsWith(moduleRoot.toPath()) ||
                    destination.toPath() == moduleRoot.toPath()) {
                throw new GradleException(
                        "Roadmap destination escapes repository module/: ${destination}"
                )
            }
            return destination
        }
        catch (IOException exception) {
            throw new GradleException(
                    "Could not validate Roadmap destination for I3 '${location}'.",
                    exception
            )
        }
    }

    /**
     * The first input row is F3:H3 (headers have already been removed).
     * Reject incomplete and non-contiguous milestones; do not silently
     * truncate or rewrite wording supplied by the authoring worker.
     */
    static List<Map<String, Object>> convertRoadmap(List<List<Object>> rows) {
        List<Map<String, Object>> result = []
        Set<String> ids = new LinkedHashSet<>()
        boolean finished = false

        rows.eachWithIndex { List<Object> row, int index ->
            String title = textAt(row, 0)
            String purpose = textAt(row, 1)
            String objectiveText = textAt(row, 2)
            if (!title && !purpose && !objectiveText) {
                if (!result.isEmpty()) {
                    finished = true
                }
                return
            }
            if (finished) {
                throw new GradleException(
                        "Roadmap has a gap before row ${index + 3}; milestones must be contiguous."
                )
            }
            if (!title || !purpose || !objectiveText) {
                throw new GradleException(
                        "Incomplete Roadmap milestone at F${index + 3}:H${index + 3}."
                )
            }
            List<String> objectives = objectiveText.split(/\r\n|\r|\n/, -1)
                    .collect { String objective -> objective.trim() }
                    .findAll { String objective -> !objective.isEmpty() }
            if (objectives.isEmpty()) {
                throw new GradleException(
                        "Roadmap objectives are empty at H${index + 3}."
                )
            }
            String id = idFromTitle(title)
            if (!ids.add(id)) {
                throw new GradleException(
                        "Duplicate Roadmap ID '${id}' derived from title in F${index + 3}."
                )
            }
            result.add([
                    id        : id,
                    title     : title,
                    purpose   : purpose,
                    objectives: objectives
            ])
        }
        return result
    }

    static String idFromTitle(String title) {
        String ascii = Normalizer.normalize(
                title.replace('Đ', 'D').replace('đ', 'd'),
                Normalizer.Form.NFD
        ).replaceAll('\\p{M}+', '').toLowerCase(Locale.ROOT)
        String id = ascii.replaceAll('[^a-z0-9]+', '-')
                .replaceAll('^-+|-+$', '')
        if (!id) {
            throw new GradleException(
                    "Cannot derive a nonblank Roadmap ID from title '${title}'."
            )
        }
        return id
    }

    /**
     * The complete Sheet snapshot is authoritative (including generated IDs).
     * An exact semantic match is idempotent. Any difference is an update.
     */
    static String determineAction(
            String existing,
            List<Map<String, Object>> milestones
    ) {
        if (existing == null) {
            return 'CREATE'
        }
        Map document = readYaml(existing)
        if (!(document.roadmap instanceof List)) {
            throw new GradleException('Existing roadmap.yml does not contain a Roadmap list.')
        }
        if (document.keySet() != ['roadmap'].toSet() ||
                !ROADMAP_LINE.matcher(existing).find()) {
            throw new GradleException(
                    'Existing roadmap.yml has unexpected root structure; refusing to replace unrelated YAML.'
            )
        }
        if (document.roadmap == milestones) {
            return 'UNCHANGED'
        }
        return document.roadmap.isEmpty() ? 'CREATE' : 'UPDATE'
    }

    static String buildYaml(
            String existing,
            List<Map<String, Object>> milestones
    ) {
        String prefix
        if (existing == null) {
            prefix = new RoadmapSchemaService().renderCommentBlock('vi') + '\n\n'
        }
        else {
            Matcher match = ROADMAP_LINE.matcher(existing)
            if (!match.find() ||
                    existing.substring(0, match.start()).readLines()
                            .any { String line -> line.trim() && !line.trim().startsWith('#') }) {
                throw new GradleException(
                        'Unexpected content before the Roadmap root; refusing to overwrite.'
                )
            }
            prefix = existing.substring(0, match.start())
        }

        DumperOptions options = new DumperOptions()
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK)
        options.setAllowUnicode(true)
        options.setIndent(2)
        options.setIndicatorIndent(2)
        options.setIndentWithIndicator(true)
        options.setWidth(120)
        return prefix + new Yaml(options).dump([roadmap: milestones])
    }

    private static File backupExistingRoadmap(
            File repositoryRoot,
            File destination,
            String original
    ) {
        try {
            Path root = repositoryRoot.canonicalFile.toPath()
            Path relativePath = root.relativize(destination.canonicalFile.toPath())
            File backupDirectory = new File(
                    repositoryRoot.canonicalFile,
                    ".gradle/sheet-roadmap-backups/${relativePath.parent.toString().replace(File.separatorChar, '/' as char)}"
            )
            Files.createDirectories(backupDirectory.toPath())
            String timestamp = DateTimeFormatter.ofPattern('yyyyMMdd-HHmmss-SSS')
                    .withZone(ZoneOffset.UTC).format(Instant.now())
            File backup = new File(
                    backupDirectory,
                    "roadmap-${timestamp}-${UUID.randomUUID()}.yml"
            )
            byte[] originalBytes = Files.readAllBytes(destination.toPath())
            if (new String(originalBytes, StandardCharsets.UTF_8) != original) {
                throw new GradleException("Roadmap changed before backup: ${destination}")
            }
            Files.write(
                    backup.toPath(), originalBytes,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE
            )
            if (!Arrays.equals(Files.readAllBytes(backup.toPath()), originalBytes)) {
                throw new GradleException("Roadmap backup readback failed: ${backup}")
            }
            return backup
        }
        catch (IOException exception) {
            throw new GradleException(
                    "Could not back up existing Roadmap before replacing ${destination}.",
                    exception
            )
        }
    }

    private static Map readYaml(String content) {
        Object document
        try {
            document = new Yaml(new SafeConstructor(new LoaderOptions())).load(content)
        }
        catch (Exception exception) {
            throw new GradleException('Invalid existing Roadmap YAML.', exception)
        }
        if (!(document instanceof Map)) {
            throw new GradleException('Existing roadmap.yml must contain a YAML mapping.')
        }
        return document as Map
    }

    private static void writeAtomically(File destination, String content) {
        Path temporary = null
        try {
            Files.createDirectories(destination.parentFile.toPath())
            temporary = Files.createTempFile(
                    destination.parentFile.toPath(), 'roadmap-import-', '.tmp'
            )
            Files.write(temporary, content.getBytes(StandardCharsets.UTF_8))
            Files.move(
                    temporary,
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            )
        }
        catch (IOException exception) {
            throw new GradleException(
                    "Unable to atomically write Roadmap: ${destination}.",
                    exception
            )
        }
        finally {
            if (temporary != null) {
                Files.deleteIfExists(temporary)
            }
        }
    }

    private static String cell(List<List<Object>> rows, int row, int col) {
        return row < rows.size() ? textAt(rows[row], col) : ''
    }

    private static String textAt(List<Object> row, int col) {
        return row != null && col < row.size() && row[col] != null
                ? row[col].toString().trim()
                : ''
    }
}
