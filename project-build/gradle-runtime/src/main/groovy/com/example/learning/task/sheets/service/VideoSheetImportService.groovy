package com.example.learning.task.sheets.service

import com.example.learning.utils.schema.validate.VideoYamlContentUtils
import com.google.api.services.sheets.v4.Sheets
import org.gradle.api.GradleException

/**
 * Imports optional Video Script YAML from X:AA on each Menu Level 1 parent.
 * The Sheet owns only the VI chapter/video/video-script.yml files. A blank
 * Video column snapshot clears those files without touching other content.
 */
class VideoSheetImportService {
    private static final int CELL_LIMIT = 30000

    Map<String, Object> execute(File root, Map<String, Object> context, boolean apply) {
        File menu = ReadmeSheetImportService.resolveMenuDirectory(
                root, context.moduleLocation as String)
        File module = new File(root.canonicalFile, context.moduleLocation as String).absoluteFile
        Sheets sheets = context.client as Sheets
        String id = context.spreadsheetId as String
        String tab = "'" + (context.tabTitle as String).replace("'", "''") + "'"
        Map base = [tab: context.tabTitle, gid: context.sheetId,
                    location: context.moduleLocation, directory: menu]

        def metadata = sheets.spreadsheets().get(id)
                .setFields('sheets(properties(sheetId,gridProperties(columnCount)))')
                .execute()
        def selected = (metadata.sheets ?: []).find {
            it.properties?.sheetId?.toString() == context.sheetId?.toString()
        }
        if (!selected) {
            throw new GradleException("Video tab gid=${context.sheetId} no longer exists.")
        }
        if ((selected.properties?.gridProperties?.columnCount ?: 0) < 27) {
            return base + [status: 'SKIPPED_NO_VIDEO_SCHEMA', videoCount: 0,
                           writtenVideos: 0, skippedEmptyVideos: 0,
                           pendingVideos: 0, clearedOldFiles: 0]
        }

        List<List<Object>> menuRows = (sheets.spreadsheets().values()
                .get(id, tab + '!A19:B').execute().values ?: []) as List<List<Object>>
        Map parsed = ReadmeSheetImportService.parseMenu(menuRows)
        if (!(parsed.status in ['READY', 'SKIPPED_EMPTY_MENU'])) {
            return base + [status: parsed.status, videoCount: 0,
                           writtenVideos: 0, skippedEmptyVideos: 0,
                           pendingVideos: 0, clearedOldFiles: 0,
                           missingRows: parsed.missingRows ?: []]
        }
        List<Map> groups = parsed.groups as List<Map>
        List<List<Object>> rows = (sheets.spreadsheets().values()
                .get(id, tab + '!X19:AA').execute().values ?: []) as List<List<Object>>
        // Video is optional. A sheet with no Video headers/data is not an
        // instruction to delete authored files. Four present headers and
        // empty parent cells, however, are an intentional empty snapshot.
        if (rows.every { List<Object> cells -> (0..<4).every { !raw(cells, it) } }) {
            return base + [status: 'SKIPPED_NO_VIDEO_SCHEMA', videoCount: 0,
                           writtenVideos: 0, skippedEmptyVideos: 0,
                           pendingVideos: 0, clearedOldFiles: 0]
        }
        Map<String, String> scripts = parseSheet(groups, rows)
        return base + sync(root, module, groups, scripts, apply)
    }

    /** The Video range starts at X19; X20 is the actual first parent row. */
    static Map<String, String> parseSheet(List<Map> groups, List<List<Object>> video) {
        if (!(0..<4).every { int c -> raw(row(video, 0), c).trim() == 'Video Script' }) {
            throw new GradleException('Invalid Video headers at X19:AA19.')
        }
        Set<Integer> parents = groups.collect { (it.parentRow as Number).intValue() } as Set
        video.drop(1).eachWithIndex { List<Object> cells, int offset ->
            int sheetRow = offset + 20
            if (!parents.contains(sheetRow) && (0..<4).any { int c -> raw(cells, c) }) {
                throw new GradleException("Orphan Video data at X${sheetRow}:AA${sheetRow}.")
            }
        }
        Map<String, String> scripts = new LinkedHashMap<>()
        groups.eachWithIndex { Map group, int index ->
            int sheetRow = group.parentRow as int
            if (sheetRow < 20) {
                throw new GradleException("Invalid Video parent row: ${sheetRow}")
            }
            List<Object> cells = row(video, sheetRow - 19)
            StringBuilder content = new StringBuilder()
            boolean gap = false
            (0..<4).each { int c ->
                String part = raw(cells, c)
                if (!part) gap = true
                else {
                    if (gap) {
                        throw new GradleException("Noncontiguous Video fragments at X${sheetRow}:AA${sheetRow}.")
                    }
                    if (part.codePointCount(0, part.length()) > CELL_LIMIT) {
                        throw new GradleException("Video chunk exceeds 30000 Unicode characters at row ${sheetRow}.")
                    }
                    content.append(part)
                }
            }
            if (content.length() == 0) return
            String filename = group.filename as String
            if (!(filename ==~ /(?i)[a-z][a-z0-9]*\.md/)) {
                throw new GradleException("Invalid Knowledge filename for Video at B${sheetRow}: ${filename}")
            }
            String chapter = "${index + 1}.${filename.substring(0, filename.length() - 3)}"
            validateVideo(content.toString(), group.title as String,
                    (group.children as List<Map>).collect { it.title as String })
            scripts[chapter] = content.toString()
        }
        return scripts
    }

    static Map validateVideo(String content, String title, List<String> expectedSections) {
        Map validated = VideoYamlContentUtils.parse(content, title, expectedSections)
        return [sections: validated.sections.size(), scenes: validated.scenes,
                transitions: validated.transitions]
    }

    static Map sync(File root, File module, List<Map> groups,
                    Map<String, String> scripts, boolean apply) {
        Map result = ChapterVideoSnapshotService.replace(root, module, scripts, apply)
        int unchanged = result.unchangedFiles as int
        return result + [videoCount: scripts.size(),
                         skippedEmptyVideos: groups.size() - scripts.size(),
                         unchangedVideos: unchanged,
                         writtenVideos: apply ? scripts.size() : 0,
                         pendingVideos: apply ? 0 : result.pendingFiles,
                         filePaths: scripts.keySet().collect { String chapter ->
                             new File(module,
                                     "content/readme/docs/vi/menu/${chapter}/video/${VideoYamlContentUtils.FILENAME}").toString()
                         }]
    }

    private static List<Object> row(List<List<Object>> rows, int index) {
        return index < rows.size() ? (rows[index] ?: []) : []
    }

    private static String raw(List<Object> data, int index) {
        return index < data.size() && data[index] != null ? data[index].toString() : ''
    }
}
