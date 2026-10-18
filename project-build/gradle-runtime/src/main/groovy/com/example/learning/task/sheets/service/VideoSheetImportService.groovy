package com.example.learning.task.sheets.service

import com.google.api.services.sheets.v4.Sheets
import org.gradle.api.GradleException


/**
 * Deterministic Video Script migration. Does NOT invoke AI or infer content.
 * The Sheet supplies the whole Markdown, and the README relative path supplies
 * the exact video path. On APPLY, the entire old video/vi/menu tree is replaced.
 */
class VideoSheetImportService {
    private static final int CELL_LIMIT = 30000
    Map<String, Object> execute(File root, Map<String, Object> context, boolean apply) {
        File readme = ReadmeSheetImportService.resolveMenuDirectory(
                root, context.moduleLocation as String
        )
        Sheets sheets = context.client as Sheets
        String id = context.spreadsheetId as String
        String tab = "'" + (context.tabTitle as String).replace("'", "''") + "'"
        List<List<Object>> menu = (sheets.spreadsheets().values()
                .get(id, tab + '!A19:B').execute().values ?: []) as List<List<Object>>
        Map parsed = ReadmeSheetImportService.parseMenu(menu)
        if (parsed.status != 'READY') {
            if (parsed.status == 'SKIPPED_EMPTY_MENU') {
                File emptyDirectory = new File(
                        readme.parentFile.parentFile.parentFile, 'video/vi/menu'
                )
                return sync(emptyDirectory, [], [:], apply, root) + [
                        tab: context.tabTitle, gid: context.sheetId,
                        location: context.moduleLocation, directory: emptyDirectory
                ]
            }
            return [status: parsed.status, videoCount: 0, writtenVideos: 0,
                    skippedEmptyVideos: 0, missingRows: parsed.missingRows ?: []]
        }
        List<Map> groups = parsed.groups as List<Map>
        List<List<Object>> video = (sheets.spreadsheets().values()
                .get(id, tab + '!N1:R' + (groups.size() + 2))
                .execute().values ?: []) as List<List<Object>>
        File directory = new File(readme.parentFile.parentFile.parentFile, 'video/vi/menu')
        Map<String, String> scripts = parseSheet(groups, video)
        return sync(directory, groups, scripts, apply, root) + [
                tab: context.tabTitle, gid: context.sheetId,
                directory: directory, location: context.moduleLocation
        ]
    }

    /**
     * Rows start at N1. Empty video scripts clear obsolete files during APPLY.
     * If at least one script is populated, the complete topic schema is
     * mandatory, including N values for all parents (not just scripted ones).
     */
    static Map<String, String> parseSheet(List<Map> groups, List<List<Object>> video) {
        Map<String, String> scripts = new LinkedHashMap<>()
        boolean hasContent = (0..<groups.size()).any { int i ->
            (1..4).any { int c -> raw(row(video, i + 2), c) }
        }
        if (!hasContent) return scripts
        if (raw(row(video, 0), 0).trim() != 'Video' ||
                raw(row(video, 1), 0).trim() != 'Chủ đề' ||
                !(1..4).every { int c ->
                    raw(row(video, 1), c).trim() == 'Video Script'
                }) {
            throw new GradleException('Invalid N1:R2 Video headers.')
        }
        groups.eachWithIndex { Map group, int i ->
            int sheetRow = i + 3
            List<Object> cells = row(video, i + 2)
            if (raw(cells, 0).trim() != group.title) {
                throw new GradleException('Video topic N' + sheetRow +
                        ' does not exactly match Menu Cấp 1.')
            }
            StringBuilder content = new StringBuilder()
            boolean gap = false
            (1..4).each { int c ->
                String part = raw(cells, c)
                if (!part) {
                    gap = true
                }
                else {
                    if (gap) {
                        throw new GradleException('Noncontiguous Video fragments at row ' + sheetRow)
                    }
                    if (part.codePointCount(0, part.length()) > CELL_LIMIT) {
                        throw new GradleException('Video chunk exceeds 30000 characters at row ' + sheetRow)
                    }
                    content.append(part)
                }
            }
            if (content.length() > 0) {
                String filename = group.filename as String
                String stem = filename.substring(0, filename.length() - 3)
                String relative = (i + 1) + '.' + stem + '/' + filename
                validateVideo(content.toString(), group.title as String,
                        (group.children as List<Map>).collect { it.title as String })
                scripts[relative] = content.toString()
            }
        }
        return scripts
    }

    /**
     * Structural validation only. No assessment of Java correctness, narration,
     * creative quality or intended meaning. Fenced examples are not headings.
     */
    static Map validateVideo(String content, String title, List<String> expectedH2) {
        String normalized = content.replace('\r\n', '\n').replace('\r', '\n')
        def front = normalized =~ /(?s)\A---\n(.*?)\n---\n/
        if (!front.find()) throw new GradleException('Video frontmatter is missing or unclosed.')
        String yaml = front.group(1)
        if (!(yaml ==~ /video:\n  url: (?:\"[^\"\n]*\"|\'[^\'\n]*\')/)) {
            throw new GradleException('Invalid Video video.url frontmatter.')
        }
        List<String> h1 = []
        List<String> h2 = []
        List<Map> items = []
        Map item = null
        String field = null
        boolean fenced = false
        String fenceKind = ''
        String body = normalized.substring(front.end()).replaceAll(/(?s)<!--.*?-->/, '')
        body.readLines().each { String line ->
            String trim = line.trim()
            if (trim.startsWith('~~~') || trim.startsWith('\u0060\u0060\u0060')) {
                String kind = trim.substring(0, 3)
                if (!fenced) { fenced = true; fenceKind = kind }
                else if (kind == fenceKind) fenced = false
                if (item != null && field != null) item.fields[field].append(line).append('\n')
                return
            }
            if (fenced) {
                if (item != null && field != null) item.fields[field].append(line).append('\n')
                return
            }
            def one = line =~ /^#\s+(.+?)\s*$/
            def two = line =~ /^##\s+(.+?)\s*$/
            def three = line =~ /^###\s+(.+?)\s*$/
            def named = line =~ /^\*\*(Time|Visual|Script|Purpose):\*\*\s*(.*)$/
            if (one.matches()) {
                h1.add(one.group(1)); item = null; field = null
            }
            else if (two.matches()) {
                h2.add(two.group(1)); item = null; field = null
            }
            else if (three.matches()) {
                String heading = three.group(1)
                String type = heading ==~ /(?i)Scene\s+\d+(?:\s+[—-]\s*.+)?/
                        ? 'SCENE' : (heading ==~ /(?i)Transition(?:\s+[—-]\s*.+)?/
                        ? 'TRANSITION' : null)
                if (type == null || h2.isEmpty())
                    throw new GradleException('Invalid Video Scene/Transition: ' + heading)
                item = [type: type, section: h2.size(), order: [],
                        fields: [Time: new StringBuilder(), Visual: new StringBuilder(),
                                 Script: new StringBuilder(), Purpose: new StringBuilder()]]
                items.add(item); field = null
            }
            else if (named.matches()) {
                if (item == null) throw new GradleException('Video field outside Scene.')
                field = named.group(1)
                if (item.order.contains(field)) throw new GradleException('Duplicate Video field: ' + field)
                item.order.add(field)
                item.fields[field].append(named.group(2)).append('\n')
            }
            else if (item != null && field != null) {
                item.fields[field].append(line).append('\n')
            }
        }
        if (fenced || h1 != [title] || h2 != expectedH2)
            throw new GradleException('Video H1/H2 do not exactly match Menu or code fence is unclosed.')
        if (items.isEmpty()) throw new GradleException('Video lacks Scenes.')
        (1..expectedH2.size()).each { int index ->
            List<Map> section = items.findAll { it.section == index }
            if (!section.any { it.type == 'SCENE' } ||
                    (index > 1 && (section.isEmpty() || section.first().type != 'TRANSITION')))
                throw new GradleException('Video section ' + index + ' lacks Scene/leading Transition.')
        }
        int lastTime = 0
        items.each { Map entry ->
            if (entry.order != ['Time', 'Visual', 'Script', 'Purpose'] ||
                    entry.fields.values().any { !it.toString().trim() })
                throw new GradleException('Video item has incomplete/incorrectly ordered fields.')
            String time = entry.fields.Time.toString().trim()
            time = time.replace('\u0060', '')
            def match = time =~ /^(\d{2,}):(\d{2})[–-](\d{2,}):(\d{2})$/
            if (!match.matches()) throw new GradleException('Invalid Video time: ' + time)
            int start = Integer.parseInt(match.group(1)) * 60 + Integer.parseInt(match.group(2))
            int end = Integer.parseInt(match.group(3)) * 60 + Integer.parseInt(match.group(4))
            if (Integer.parseInt(match.group(2)) > 59 || Integer.parseInt(match.group(4)) > 59 ||
                    start != lastTime || end <= start)
                throw new GradleException('Noncontiguous Video time: ' + time)
            lastTime = end
        }
        return [sections: h2.size(), scenes: items.count { it.type == 'SCENE' },
                transitions: items.count { it.type == 'TRANSITION' }]
    }

    /**
     * The current Sheet is the complete Video source of truth. APPLY replaces
     * the entire video/vi/menu tree, including obsolete authored files and
     * empty-chapter skeletons, without preserving old metadata or backups.
     */
    static Map sync(
            File videoDirectory, List<Map> groups,
            Map<String, String> scripts, boolean apply, File repositoryRoot
    ) {
        Map<String, String> previous = SheetSnapshotReplaceService.readTree(videoDirectory)
        int unchanged = scripts.count { String name, String content ->
            previous[name] == content
        }
        Map result = SheetSnapshotReplaceService.replace(
                repositoryRoot, videoDirectory, scripts, apply
        )
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
