package com.example.learning.task.sheets.service

import com.example.learning.utils.VideoYamlContentService
import com.google.api.services.sheets.v4.Sheets
import org.gradle.api.GradleException

/**
 * Imports authored Video Script YAML from P:S on the actual Menu Level 1 row.
 * The Sheet owns the complete content/video/vi/menu snapshot. Empty scripts
 * remove their obsolete files and APPLY replaces all prior Video files.
 * No AI inference, no module skeleton generation and no metadata merger.
 */
class VideoSheetImportService {
    private static final int CELL_LIMIT = 30000

    Map<String, Object> execute(File root, Map<String, Object> context, boolean apply) {
        File readme = ReadmeSheetImportService.resolveMenuDirectory(
                root, context.moduleLocation as String)
        File directory = new File(readme.parentFile.parentFile.parentFile.parentFile, 'video/vi/menu')
        Sheets sheets = context.client as Sheets
        String id = context.spreadsheetId as String
        String tab = "'" + (context.tabTitle as String).replace("'", "''") + "'"
        List<List<Object>> menu = (sheets.spreadsheets().values()
                .get(id, tab + '!A19:B').execute().values ?: []) as List<List<Object>>
        Map parsed = ReadmeSheetImportService.parseMenu(menu)
        if (parsed.status != 'READY') {
            if (parsed.status == 'SKIPPED_EMPTY_MENU') {
                return sync(directory, [], [:], apply, root) + [
                        tab: context.tabTitle, gid: context.sheetId,
                        location: context.moduleLocation, directory: directory]
            }
            return [status: parsed.status, videoCount: 0, writtenVideos: 0,
                    skippedEmptyVideos: 0, missingRows: parsed.missingRows ?: []]
        }
        List<Map> groups = parsed.groups as List<Map>
        List<List<Object>> video = (sheets.spreadsheets().values()
                .get(id, tab + '!P19:S' + (parsed.lastRow as int))
                .execute().values ?: []) as List<List<Object>>
        Map<String, String> scripts = parseSheet(groups, video)
        return sync(directory, groups, scripts, apply, root) + [
                tab: context.tabTitle, gid: context.sheetId,
                directory: directory, location: context.moduleLocation]
    }

    /**
     * P19:S19 are the four Video Script headers; P20:S20 is parent row 20.
     * For every Menu Level 1 use group.parentRow, never the group ordinal.
     */
    static Map<String, String> parseSheet(List<Map> groups, List<List<Object>> video) {
        if (!(0..<4).every { int c -> raw(row(video, 0), c).trim() == 'Video Script' }) {
            throw new GradleException('Invalid Video headers at P19:S19.')
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
                if (!part) {
                    gap = true
                }
                else {
                    if (gap) {
                        throw new GradleException("Noncontiguous Video fragments at P${sheetRow}:S${sheetRow}.")
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
            String stem = filename.substring(0, filename.length() - 3)
            String relative = "${index + 1}.${stem}/${VideoYamlContentService.FILENAME}"
            validateVideo(content.toString(), group.title as String,
                    (group.children as List<Map>).collect { it.title as String })
            scripts[relative] = content.toString()
        }
        return scripts
    }

    static Map validateVideo(String content, String title, List<String> expectedSections) {
        Map validated = VideoYamlContentService.parse(content, title, expectedSections)
        return [sections: validated.sections.size(), scenes: validated.scenes,
                transitions: validated.transitions]
    }

    static Map sync(File videoDirectory, List<Map> groups,
                    Map<String, String> scripts, boolean apply, File repositoryRoot) {
        Map<String, String> previous = SheetSnapshotReplaceService.readTree(videoDirectory)
        int unchanged = scripts.count { String name, String content ->
            previous[name] == content
        }
        Map result = SheetSnapshotReplaceService.replace(
                repositoryRoot, videoDirectory, scripts, apply)
        return result + [
                videoCount: scripts.size(),
                skippedEmptyVideos: groups.size() - scripts.size(),
                unchangedVideos: unchanged,
                writtenVideos: apply ? scripts.size() : 0,
                pendingVideos: apply ? 0 :
                        (scripts.count { String name, String content ->
                            previous[name] != content
                        } + previous.keySet().count { String name ->
                            !scripts.containsKey(name)
                        }),
                filePaths: scripts.keySet().collect { String name ->
                    new File(videoDirectory, name).toString()
                }
        ]
    }

    private static List<Object> row(List<List<Object>> rows, int index) {
        return index < rows.size() ? (rows[index] ?: []) : []
    }

    private static String raw(List<Object> data, int index) {
        return index < data.size() && data[index] != null ? data[index].toString() : ''
    }
}
