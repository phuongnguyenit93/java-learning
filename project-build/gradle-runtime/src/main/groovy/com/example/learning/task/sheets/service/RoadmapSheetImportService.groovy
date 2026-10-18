package com.example.learning.task.sheets.service

import com.example.learning.utils.schema.validate.ContractSchemaValidateUtils
import com.example.learning.setup.module.roadmap.service.RoadmapSchemaService
import com.google.api.services.sheets.v4.Sheets
import org.gradle.api.GradleException
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml

import java.text.Normalizer

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
            String before = destination.isFile() ? destination.getText('UTF-8') : null
            Map empty = syncRoadmap(repositoryRoot, destination, before, [], apply)
            return [
                    tab       : session.tabTitle,
                    gid       : session.sheetId,
                    location  : session.moduleLocation,
                    file      : destination,
                    milestones: 0,
                    status    : empty.status == 'CLEARED_EMPTY'
                            ? 'CLEARED_EMPTY' : 'SKIPPED_EMPTY',
                    clearedOldFiles: empty.clearedOldFiles
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
                clearedOldFiles: sync.clearedOldFiles
        ]
    }

    /**
     * Sheet is the ONLY source: rebuild the entire roadmap/vi/ directory.
     * Never preserve old YAML metadata, relations, or create backups.
     */
    static Map<String, Object> syncRoadmap(
            File repositoryRoot,
            File destination,
            String before,
            List<Map<String, Object>> milestones,
            boolean apply
    ) {
        Map<String, String> files = milestones.isEmpty() ? [:] :
                [(destination.name): buildYaml(milestones)]
        if ((destination.isFile() ? destination.getText('UTF-8') : null) != before) {
            throw new GradleException("Roadmap changed during import preflight: ${destination}")
        }
        return SheetSnapshotReplaceService.replace(
                repositoryRoot, destination.parentFile, files, apply
        )
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
                    "${location}/content/roadmap/vi/roadmap.yml"
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
     * Render a new YAML document exclusively from the current Sheet.
     * Never parse, retain or merge the existing module file.
     */
    static String buildYaml(List<Map<String, Object>> milestones) {
        ContractSchemaValidateUtils.validateData('roadmap', [roadmap: milestones])
        String prefix = new RoadmapSchemaService().renderCommentBlock('vi') + '\n\n'
        DumperOptions options = new DumperOptions()
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK)
        options.setAllowUnicode(true)
        options.setIndent(2)
        options.setIndicatorIndent(2)
        options.setIndentWithIndicator(true)
        options.setWidth(120)
        return prefix + new Yaml(options).dump([roadmap: milestones])
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
