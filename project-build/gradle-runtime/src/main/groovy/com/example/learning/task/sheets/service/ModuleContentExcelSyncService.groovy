package com.example.learning.task.sheets.service

import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.util.WorkbookUtil
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.gradle.api.GradleException
import org.yaml.snakeyaml.Yaml
import com.example.learning.utils.schema.validate.ContractSchemaValidateUtils

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * STEP 15: reverse module-local Sheet importers to an OFFLINE English Excel.
 * The original VI workbook supplies row positions, styles and validation;
 * EN module files supply only their corresponding authored content.
 *
 * This does NOT upload anything to Google Sheets. The separate existing
 * importExcelFromModuleSheet task creates a new Google tab from the result.
 */
class ModuleContentExcelSyncService {
    static final Set<String> PARTS = [
            'INTRODUCTION', 'README', 'ROADMAP', 'QUIZ', 'INTERVIEW', 'VIDEO',
            'REFERENCE', 'SUMMARY', 'ALL'
    ] as Set
    private static final Pattern SECTION = Pattern.compile(
            '(?m)^##\\s+<a\\s+id="([^"]+)">([^\\r\\n]*?)</a>\\s*$')
    private static final Pattern DETAILS_OPEN = Pattern.compile(
            '(?s)^\\s*<details>\\s*<summary>[^<\\r\\n]*</summary>\\s*')
    private static final Pattern DETAILS_END = Pattern.compile(
            '(?s)\\s*</details>\\s*(?:- \\[[^\\r\\n]+\\]\\(#back-to-top\\)\\s*)?(?:---\\s*)?$')
    private static final int CHUNK_SIZE = 30000
    private static final DataFormatter FORMATTER = new DataFormatter(Locale.ROOT)

    Map execute(File rootDirectory, File moduleDirectory, String part, boolean apply) {
        return execute(rootDirectory, moduleDirectory, part, apply, false, null)
    }

    /**
     * STEP 15 internal workflow. Render against the canonical VI workbook,
     * never against stale EN Excel; write to a temporary build artifact, not
     * the final exported EN snapshot.
     */
    Map execute(File rootDirectory, File moduleDirectory, String part, boolean apply,
                boolean forceViBaseline, File outputOverride) {
        String requested = part?.trim()?.toUpperCase(Locale.ROOT)
        if (!PARTS.contains(requested)) {
            throw new GradleException('Invalid module-to-Excel part: ' + part)
        }
        File root = rootDirectory.canonicalFile
        File module = moduleDirectory.canonicalFile
        Path moduleRoot = new File(root, 'module').canonicalFile.toPath()
        if (!module.isDirectory() || module.toPath() == moduleRoot ||
                !module.toPath().startsWith(moduleRoot) ||
                !new File(module, 'gradle.properties').isFile()) {
            throw new GradleException('Module-to-Excel requires a real Gradle module.')
        }
        String location = root.toPath().relativize(module.toPath())
                .toString().replace(File.separatorChar, '/' as char)
        RoadmapSheetImportService.resolveDestination(root, location)
        File base = new File(module, 'content/sheet/vi/module.xlsx')
        File target = outputOverride ?: new File(module, 'content/sheet/en/module.xlsx')
        verifyLocalPath(module.toPath(), base.toPath())
        verifyLocalPath(module.toPath(), target.toPath())
        File source = forceViBaseline ? base : (target.isFile() ? target : base)
        if (!source.isFile()) {
            throw new GradleException('Run STEP 12 first: missing VI module.xlsx.')
        }
        Map report = [part: requested, source: source, output: target,
                      chapters: 0, items: 0, changedCells: 0]
        source.withInputStream { input ->
            new XSSFWorkbook(input).withCloseable { XSSFWorkbook book ->
                if (book.numberOfSheets != 1) {
                    throw new GradleException('Excel source must have exactly one worksheet.')
                }
                XSSFSheet sheet = book.getSheetAt(0)
                if (text(sheet, 3, 9) != location) {
                    throw new GradleException('Excel I3 does not match selected module: ' +
                            location)
                }
                List<Map> groups = menu(sheet)
                report.chapters = groups.size()
                String[] selected = requested == 'ALL'
                        ? ['INTRODUCTION','README','ROADMAP','INTERVIEW','QUIZ','REFERENCE','SUMMARY']
                        : [requested]
                selected.each { String name ->
                    switch (name) {
                        case 'INTRODUCTION':
                            introduction(module, sheet, report)
                            break
                        case 'README':
                            readme(module, sheet, groups, report)
                            break
                        case 'ROADMAP':
                            roadmap(module, sheet, report)
                            break
                        case 'INTERVIEW':
                            questions(module, sheet, groups, 'interview', 16, report)
                            break
                        case 'QUIZ':
                            questions(module, sheet, groups, 'quiz', 20, report)
                            break
                        case 'VIDEO':
                            video(module, sheet, groups, report)
                            break
                        case 'REFERENCE':
                            reference(module, sheet, report)
                            break
                        case 'SUMMARY':
                            summary(module, sheet, groups, report)
                            break
                    }
                }
                if (!target.isFile()) {
                    sheetName(sheet, book)
                }
                report.status = apply ? 'EXPORTED' : 'DRY_RUN'
                if (apply) saveVerified(book, target, location)
            }
        }
        return report
    }

    /** Original worksheet row numbers; do not invent row locations. */
    static List<Map> menu(XSSFSheet sheet) {
        if (text(sheet, 19, 1) != 'Menu Cấp 1' ||
                text(sheet, 19, 2) != 'Menu Cấp 2') {
            throw new GradleException('Invalid Sheet menu headers A19:B19.')
        }
        List<Map> groups = []
        boolean ended = false
        for (int i = 20; i <= sheet.lastRowNum + 1; i++) {
            String title = text(sheet, i, 1)
            String second = text(sheet, i, 2)
            if (!title && !second) {
                if (!groups.isEmpty()) ended = true
                continue
            }
            if (ended) {
                // Other fields can be populated beyond the last menu row.
                if (title || second) throw new GradleException('Menu A/B has a gap at row ' + i)
            }
            boolean childWithId = title && MenuLessonIdService.isLessonId(title) &&
                    ! (second ==~ /(?i)[a-z][a-z0-9]*\.md/) && !groups.isEmpty()
            if (title && !childWithId) {
                if (!second || !(second ==~ /(?i)[a-z][a-z0-9]*\.md/)) {
                    throw new GradleException('Bad chapter filename at B' + i)
                }
                String stem = second.substring(0, second.length() - 3)
                groups.add([row: i, title: title, filename: second,
                            baseId: text(sheet, i, 3),
                            source: groups.size() + 1 + '.' + stem + '/' + second,
                            knowledgeSource: groups.size() + 1 + '.' + stem +
                                    '/knowledge/' + second,
                            children: []])
            } else {
                if (groups.isEmpty()) throw new GradleException('Orphan lesson B' + i)
                groups.last().children.add([row: i, title: second,
                                            id: childWithId ? title : null])
            }
        }
        if (!groups) throw new GradleException('No Menu Cấp 1 in baseline workbook.')
        return groups
    }

    private static void introduction(File module, XSSFSheet sheet, Map report) {
        File file = required(new File(module, 'content/readme/docs/en/BASE.md'))
        set(sheet, 17, 2, file.getText('UTF-8'), report)
        report.items++
    }

    /** Shared Reference: same canonical YAML in VI and EN. */
    private static void reference(File module, XSSFSheet sheet, Map report) {
        String header = text(sheet, 2, 12)
        if (header && header != 'Reference') {
            throw new GradleException('Unexpected Reference schema at L2.')
        }
        File source = new File(module, 'content/reference/references.yml')
        if (!source.isFile()) {
            if (text(sheet, 3, 12)) throw new GradleException('Reference source missing but L3 is authored.')
            return
        }
        String yaml = source.getText('UTF-8')
        // Skeleton comments are not authored, but YAML may have leading comments.
        Object parsed = new Yaml().load(yaml)
        if (parsed == null) return
        ReferenceSheetImportService.parseReference(yaml)
        set(sheet, 2, 12, 'Reference', report)
        set(sheet, 3, 12, yaml, report)
        report.items++
    }

    /** STEP 14 translated Summary is projected into the same AC parent/child rows. */
    private static void summary(File module, XSSFSheet sheet, List<Map> groups, Map report) {
        String label = text(sheet, 18, 29)
        String header = text(sheet, 19, 29)
        if (!label && !header) {
            File viMenu = new File(module, 'content/readme/docs/vi/menu')
            if (viMenu.isDirectory() && viMenu.listFiles()?.any { File chapter ->
                chapter.isDirectory() &&
                        new File(chapter, 'summary/summaries.yml').isFile()
            }) {
                throw new GradleException('VI Summary exists but Excel lacks AC18:AC19 schema.')
            }
            return
        }
        // STEP 12's older VI workbook may still carry the Markdown label.
        // Export upgrades its EN copy to YAML without modifying the VI source.
        if (!([label,header] in [['Summary','Summary YAML'],
                ['Learning Objectives','Learning Objectives YAML'],
                ['Summary','Summary Markdown'],
                ['Learning Objectives','Learning Objectives Markdown']])) {
            throw new GradleException('Invalid Summary headers in VI Excel AC18:AC19.')
        }
        File enMenu = new File(module, 'content/readme/docs/en/menu')
        groups.each { Map group ->
            String chapter = group.source.toString().replaceFirst('/[^/]+$', '')
            File section = new File(enMenu, chapter + '/summary/section.yml')
            File summary = new File(enMenu, chapter + '/summary/summaries.yml')
            File viSummary = new File(module, 'content/readme/docs/vi/menu/' +
                    chapter + '/summary/summaries.yml')
            if (!section.isFile() || !summary.isFile()) {
                if (viSummary.isFile() || text(sheet, group.row as int, 29) ||
                        (group.children as List<Map>).any { text(sheet, it.row as int, 29) }) {
                    throw new GradleException('Missing translated EN Summary for chapter ' + chapter)
                }
                return
            }
            Map doc = parseYaml(summary)
            if (!(doc.summaries instanceof List) ||
                    (doc.summaries as List).size() != (group.children as List).size()) {
                throw new GradleException('EN Summary lesson count mismatch: ' + chapter)
            }
            List<String> ids = (group.children as List<Map>).collect { Map child ->
                child.id as String
            }
            String chapterYaml = section.getText('UTF-8')
            SummarySectionYamlService.read(chapterYaml, ids)
            set(sheet, group.row as int, 29,
                    SummarySectionYamlService.toAuthored(chapterYaml, ids), report)
            (group.children as List<Map>).eachWithIndex { Map child, int i ->
                Map item = (doc.summaries as List)[i] as Map
                String id = ids[i]
                if (item.id != id || !(item.title instanceof String) ||
                        !(item.summary instanceof String) || !item.summary.trim()) {
                    throw new GradleException('Invalid EN Summary lesson ' + id)
                }
                set(sheet, child.row as int, 29, item.summary as String, report)
                report.items++
            }
        }
        set(sheet, 18, 29, 'Summary', report)
        set(sheet, 19, 29, 'Summary YAML', report)
    }

    private static void readme(File module, XSSFSheet sheet, List<Map> groups, Map report) {
        File menu = new File(module, 'content/readme/docs/en/menu')
        groups.each { Map group ->
            File file = required(new File(menu, group.knowledgeSource as String))
            String chapter = group.source.toString().replaceFirst('/[^/]+$', '')
            File metadataFile = required(new File(menu,
                    chapter + '/metadata/knowledge-metadata.yml'))
            Map metadata = parseYaml(metadataFile)
            Map byAnchor = metadata.sections instanceof Map
                    ? metadata.sections as Map : metadata
            Map parsed = parseMarkdown(file.getText('UTF-8'))
            List<Map> sections = parsed.sections as List<Map>
            List<Map> lessons = group.children as List<Map>
            if (sections.size() != lessons.size()) {
                throw new GradleException('EN lesson count differs from VI Excel at ' +
                        group.knowledgeSource + ': ' + sections.size() + '/' + lessons.size())
            }
            set(sheet, group.row as int, 1, parsed.title as String, report)
            sections.eachWithIndex { Map section, int i ->
                String expected = lessons[i].id as String
                if (!MenuLessonIdService.isLessonId(expected) ||
                        !expected.startsWith(group.baseId.toString() + '-')) {
                    throw new GradleException('Missing/invalid baseline lesson ID at A' +
                            lessons[i].row)
                }
                if (section.id != expected) {
                    throw new GradleException('EN README anchor mismatch: expected ' +
                            expected + ', found ' + section.id + ' (' + group.knowledgeSource + ')')
                }
                int row = lessons[i].row as int
                set(sheet, row, 2, section.title as String, report)
                fragments(sheet, row, 3, 12, section.body as String, report)
                Map detail = byAnchor[expected] instanceof Map
                        ? byAnchor[expected] as Map : [:]
                if (!detail) throw new GradleException('Missing EN metadata for ' + expected)
                String difficulty = detail.difficulty?.toString() ?: ''
                if (!(difficulty in ['BASIC', 'INTERMEDIATE', 'ADVANCED'])) {
                    throw new GradleException('Invalid EN difficulty for ' + expected)
                }
                set(sheet, row, 15, difficulty, report)
                report.items++
            }
        }
    }

    /** Strictly recognize generated top Menu/details; never guess a section boundary. */
    static Map parseMarkdown(String markdown) {
        String normalized = markdown.replace('\r\n', '\n').replace('\r', '\n')
        Matcher h1 = Pattern.compile('(?m)^# ([^\\n]+)$').matcher(normalized)
        if (!h1.find()) throw new GradleException('EN README has no H1 title.')
        Matcher matcher = SECTION.matcher(normalized)
        List<Map> sections = []
        List<Integer> ends = []
        while (matcher.find()) {
            sections.add([id: matcher.group(1), title: unescape(matcher.group(2)),
                          bodyStart: matcher.end(), start: matcher.start()])
            ends.add(matcher.start())
        }
        if (!sections) throw new GradleException('EN README has no anchored sections.')
        sections.eachWithIndex { Map section, int i ->
            String tail = normalized.substring(section.bodyStart as int,
                    i + 1 < sections.size() ? sections[i + 1].start as int : normalized.length())
            Matcher open = DETAILS_OPEN.matcher(tail)
            if (!open.find()) {
                throw new GradleException('Expected generated <details> for ' + section.id)
            }
            tail = tail.substring(open.end())
            Matcher close = DETAILS_END.matcher(tail)
            if (!close.find()) {
                throw new GradleException('Missing generated </details> for ' + section.id)
            }
            section.body = tail.substring(0, close.start()).replaceFirst('^\\n+', '')
                    .replaceFirst('\\n+$', '')
            section.remove('bodyStart')
            section.remove('start')
        }
        return [title: unescape(h1.group(1)), sections: sections]
    }

    private static String unescape(String value) {
        return value.replace('&lt;', '<').replace('&gt;', '>').replace('&quot;', '"')
                .replace('&#39;', "'").replace('&#91;', '[').replace('&#93;', ']')
                .replace('&amp;', '&')
    }

    private static void roadmap(File module, XSSFSheet sheet, Map report) {
        File source = new File(module, 'content/roadmap/en/roadmap.yml')
        if (!source.isFile()) {
            if (new File(module, 'content/roadmap/vi/roadmap.yml').isFile()) {
                throw new GradleException('Missing EN roadmap source.')
            }
            // Optional disabled Roadmap must not leak VI content into EN.
            for (int r = 3; r <= sheet.lastRowNum + 1; r++) {
                if (!text(sheet,r,6) && !text(sheet,r,7) && !text(sheet,r,8)) break
                (6..8).each { int col -> set(sheet,r,col,'',report) }
            }
            return
        }
        Map document = parseYaml(source)
        List items = document.roadmap instanceof List ? document.roadmap as List : []
        if (!items) throw new GradleException('Empty EN Roadmap.')
        int count = 0
        for (int r = 3; r <= sheet.lastRowNum + 1; r++) {
            if (!text(sheet, r, 6) && !text(sheet, r, 7) && !text(sheet, r, 8)) break
            count++
        }
        if (count != items.size()) {
            throw new GradleException('Roadmap VI/EN milestone count mismatch: ' +
                    count + '/' + items.size())
        }
        items.eachWithIndex { Object entry, int i ->
            if (!(entry instanceof Map) || !(entry.objectives instanceof List) ||
                    !(entry.title instanceof String) || !(entry.purpose instanceof String)) {
                throw new GradleException('Invalid EN Roadmap milestone #' + (i + 1))
            }
            set(sheet, 3 + i, 6, entry.title, report)
            set(sheet, 3 + i, 7, entry.purpose, report)
            set(sheet, 3 + i, 8, entry.objectives.join('\n'), report)
            report.items++
        }
        // Roadmap relatedKnowledge/relatedModules/dependsOn are NOT representable
        // in F:H. Keep the module YAML canonical: never regenerate it from this Excel.
    }

    private static void questions(File module, XSSFSheet sheet, List<Map> groups,
                                  String kind, int firstColumn, Map report) {
        File menu = new File(module, 'content/readme/docs/en/menu')
        boolean anyChapter = groups.any { Map group ->
            String chapter = group.source.toString().replaceFirst('/[^/]+$', '')
            File file = kind == 'interview'
                    ? new File(menu, chapter + '/questions/interview.yml')
                    : new File(menu, chapter + '/quiz/question.yml')
            file.isFile()
        }
        groups.each { Map group ->
            String chapter = group.source.toString().replaceFirst('/[^/]+$', '')
            String relative = kind == 'interview' ? 'questions/interview.yml' : 'quiz/question.yml'
            File yamlFile = new File(menu, chapter + '/' + relative)
            File vi = new File(module, 'content/readme/docs/vi/menu/' +
                    chapter + '/' + relative)
            if (!yamlFile.isFile()) {
                if (vi.isFile() || (kind == 'quiz' && anyChapter)) {
                    throw new GradleException('Missing EN ' + kind + ' for chapter ' + chapter)
                }
                fragments(sheet, group.row as int, firstColumn, 4, '', report)
                return
            }
            Map document = parseYaml(yamlFile)
            List items = document.questions instanceof List ? document.questions as List : []
            if (!items) throw new GradleException('Empty EN ' + kind + ' YAML: ' + yamlFile)
            File knowledge = required(new File(menu, group.knowledgeSource as String))
            Set<String> anchors = (parseMarkdown(knowledge.getText('UTF-8'))
                    .sections as List<Map>).collect { it.id.toString() } as Set
            items.eachWithIndex { Object item, int index ->
                if (!(item instanceof Map) || !(item.readmeRelated instanceof Map) ||
                        (item.readmeRelated as Map).containsKey('file')) {
                    throw new GradleException('Invalid chapter ' + kind +
                            ' readmeRelated #' + (index + 1) + ': ' + yamlFile)
                }
                String anchor = (item.readmeRelated.anchor ?: '').toString().trim()
                if (anchor && !anchors.contains(anchor)) {
                    throw new GradleException('Unresolved chapter ' + kind +
                            ' readmeRelated anchor #' + (index + 1) + ': ' + anchor)
                }
            }
            Map output = [questions: items]
            if (kind == 'interview') {
                File summaryFile = new File(menu, chapter + '/questions/summary.yml')
                File viSummary = new File(module,
                        'content/readme/docs/vi/menu/' + chapter + '/questions/summary.yml')
                if (!summaryFile.isFile() && viSummary.isFile()) {
                    throw new GradleException('Missing EN Summary Questions for chapter ' + chapter)
                }
                if (summaryFile.isFile()) {
                    Map summaryDocument = parseYaml(summaryFile)
                    com.example.learning.utils.schema.validate.ContractSchemaValidateUtils
                            .validateData('summaryQuestions', summaryDocument)
                    output.summaryQuestions = summaryDocument.questions
                    report.items += (summaryDocument.questions as List).size()
                }
            }
            String yamlText = new Yaml().dump(output)
            fragments(sheet, group.row as int, firstColumn, 4, yamlText, report)
            report.items += items.size()
        }
    }

    private static void video(File module, XSSFSheet sheet, List<Map> groups, Map report) {
        File root = new File(module, 'content/readme/docs/en/menu')
        if (!root.isDirectory()) {
            File vi = new File(module, 'content/readme/docs/vi/menu')
            boolean authoredVi = vi.isDirectory() && vi.listFiles()?.any { File chapter ->
                chapter.isDirectory() && new File(chapter, 'video/video-script.yml').isFile()
            }
            if (authoredVi) {
                throw new GradleException('Missing EN video tree.')
            }
            groups.each { Map group ->
                fragments(sheet, group.row as int, 24, 4, '', report)
            }
            return
        }
        groups.each { Map group ->
            String path = group.source.toString().replaceFirst('/[^/]+$', '/video/video-script.yml')
            File file = new File(root, path)
            if (!file.isFile()) {
                if (new File(module, 'content/readme/docs/vi/menu/' + path).isFile()) {
                    throw new GradleException('Missing EN Video for chapter: ' + path)
                }
                // A chapter may not have an authored Video even when enabled.
                fragments(sheet, group.row as int, 24, 4, '', report)
                return
            }
            fragments(sheet, group.row as int, 24, 4, file.getText('UTF-8'), report)
            report.items++
        }
    }

    private static Map parseYaml(File file) {
        Object result
        try { result = new Yaml().load(file.getText('UTF-8')) }
        catch (Exception error) {
            throw new GradleException('Invalid YAML in ' + file + ': ' + error.message, error)
        }
        if (!(result instanceof Map)) {
            throw new GradleException('Expected YAML mapping in ' + file)
        }
        return result as Map
    }

    static List<String> split(String text, int count) {
        String value = text ?: ''
        if (value.codePointCount(0, value.length()) > CHUNK_SIZE * count) {
            throw new GradleException('Text exceeds ' + count + ' Excel fragments.')
        }
        List<String> result = []
        int offset = 0
        while (offset < value.length()) {
            int n = Math.min(CHUNK_SIZE, value.codePointCount(offset, value.length()))
            int next = value.offsetByCodePoints(offset, n)
            result.add(value.substring(offset, next))
            offset = next
        }
        while (result.size() < count) result.add('')
        return result
    }

    private static void fragments(XSSFSheet sheet, int row, int col, int count,
                                  String value, Map report) {
        split(value, count).eachWithIndex { String part, int index ->
            set(sheet, row, col + index, part, report)
        }
    }

    static String text(XSSFSheet sheet, int row, int column) {
        Cell cell = sheet.getRow(row - 1)?.getCell(column - 1)
        if (cell == null || cell.cellType == CellType.BLANK) return ''
        return FORMATTER.formatCellValue(cell)
    }

    private static void set(XSSFSheet sheet, int row, int column, Object value, Map report) {
        Row targetRow = sheet.getRow(row - 1) ?: sheet.createRow(row - 1)
        Cell cell = targetRow.getCell(column - 1) ?: targetRow.createCell(column - 1)
        if (cell.cellType == CellType.FORMULA) {
            throw new GradleException('Refusing to replace formula at row ' + row +
                    ', column ' + column)
        }
        if (value instanceof Boolean && cell.cellType == CellType.BOOLEAN &&
                cell.booleanCellValue == value) return
        String actual = text(sheet, row, column)
        String expected = value == null ? '' : value.toString()
        if (actual == expected) return
        if (expected.length() > 32767) {
            throw new GradleException('Excel cell exceeds 32767 chars at row ' + row)
        }
        if (value instanceof Boolean) cell.setCellValue(value)
        else cell.setCellValue(expected)
        report.changedCells = (report.changedCells as int) + 1
    }

    private static void sheetName(XSSFSheet sheet, XSSFWorkbook book) {
        String proposed = sheet.sheetName + ' EN'
        book.setSheetName(0, WorkbookUtil.createSafeSheetName(proposed).take(31))
    }

    private static File required(File file) {
        if (!file.isFile()) throw new GradleException('Missing EN source: ' + file)
        return file
    }

    private static void verifyLocalPath(Path module, Path path) {
        Path current = module
        if (!path.normalize().startsWith(module)) {
            throw new GradleException('Excel path escapes module: ' + path)
        }
        for (Path segment : module.relativize(path)) {
            current = current.resolve(segment)
            if (Files.isSymbolicLink(current)) {
                throw new GradleException('Symlinked Excel path forbidden: ' + current)
            }
        }
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) &&
                !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new GradleException('Not a regular Excel file: ' + path)
        }
    }

    private static void saveVerified(XSSFWorkbook book, File file, String location) {
        Files.createDirectories(file.parentFile.toPath())
        Path tmp = Files.createTempFile(file.parentFile.toPath(), '.module-en-', '.xlsx')
        try {
            Files.newOutputStream(tmp).withCloseable { book.write(it) }
            Files.newInputStream(tmp).withCloseable { input ->
                new XSSFWorkbook(input).withCloseable { check ->
                    if (check.numberOfSheets != 1 ||
                            text(check.getSheetAt(0), 3, 9) != location) {
                        throw new GradleException('English Excel readback failed.')
                    }
                }
            }
            try {
                Files.move(tmp, file.toPath(), StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING)
            } catch (java.nio.file.AtomicMoveNotSupportedException error) {
                throw new GradleException('Atomic Excel replace not supported; original preserved.', error)
            }
        } finally {
            Files.deleteIfExists(tmp)
        }
    }
}
