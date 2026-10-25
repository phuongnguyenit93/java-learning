package com.example.learning.task.sheets.service

import com.example.learning.utils.schema.validate.VideoYamlContentUtils
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.Spreadsheet
import groovy.json.JsonSlurper
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.gradle.api.GradleException

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Optional post-STEP_FINAL Video EN sync. Never re-runs STEP 15's full Sheet upload.
 * Only the existing EN tab's four Video columns on parent rows can be written.
 */
class ModuleOptionalVideoEnPublishService extends ModuleEnSheetPublishService {
    private final SheetsMetadataReaderService reader
    private final ModuleSheetExcelExportService exporter

    ModuleOptionalVideoEnPublishService(
            SheetsMetadataReaderService reader = new SheetsMetadataReaderService(),
            ModuleSheetExcelExportService exporter = new ModuleSheetExcelExportService()) {
        this.reader = reader
        this.exporter = exporter
    }

    Map execute(File repository, File moduleDirectory, String fileUrl, String viGid,
                boolean apply) {
        File root = repository.canonicalFile
        File module = moduleDirectory.canonicalFile
        Path moduleBase = new File(root, 'module').canonicalFile.toPath()
        if (!module.toPath().startsWith(moduleBase) ||
                module.toPath() == moduleBase ||
                !new File(module, 'gradle.properties').isFile()) {
            throw new GradleException('Optional Video EN must target a real module.')
        }
        String location = root.toPath().relativize(module.toPath()).toString()
                .replace(File.separatorChar, '/' as char)
        Map vi = reader.openTab(fileUrl, viGid)
        if (vi.moduleLocation != location || ModuleCurrentStepService.read(module) != 'FINAL') {
            throw new GradleException('VI module/gid mismatch or STEP_FINAL not completed in master.json.')
        }
        File checkpoint = safe(module, 'content/sheet/en/publish-state.json')
        if (!checkpoint.isFile()) throw new GradleException('STEP 15 EN publish state missing.')
        Object stored = new JsonSlurper().parse(checkpoint)
        if (!(stored instanceof Map)) throw new GradleException('Invalid EN publish state.')
        Map state = stored as Map
        String enTitle = ModuleEnSheetPublishService.enTitle(vi.tabTitle as String)
        int enGid = ModuleEnSheetPublishService.deterministicGid(
                vi.spreadsheetId as String, location)
        if (state.phase != 'EXPORTED' ||
                state.module != location || state.viGid?.toString() != viGid ||
                state.spreadsheetId != vi.spreadsheetId ||
                state.enGid?.toString() != enGid.toString() ||
                state.enTitle != enTitle) {
            throw new GradleException('STEP 15 EN state not finalized or identity mismatch.')
        }
        Map context = apply ? reader.openSpreadsheetForWrite(fileUrl)
                : reader.openSpreadsheet(fileUrl)
        Map tab = ((context.tabs ?: []) as List<Map>).find {
            it.gid?.toString() == enGid.toString() && it.title == enTitle
        }
        if (!tab || tab.moduleLocation != location) {
            throw new GradleException('Existing STEP 15 EN tab not found at pinned gid.')
        }
        List<Map> changes = plan(module)
        if (!changes) throw new GradleException('No authored VI/EN Video to publish.')
        if (changes.any { Map cell -> (cell.r as int) >= (tab.rows as int) ||
                (cell.c as int) >= (tab.columns as int) }) {
            throw new GradleException('EN tab Video range exceeds grid.')
        }
        String fingerprint = snapshot(module)
        List<List<Object>> before = readGrid(context, enTitle, changes)
        // Video EN was optional at STEP 15. Existing authored EN cells are not overwritten
        // unless they already match the proposed data; a conflicting editor is a blocker.
        changes.eachWithIndex { Map entry, int i ->
            Object old = before[i]?.first()
            if (old != null && old.toString() != '' &&
                    old.toString() != entry.value?.toString()) {
                throw new GradleException('Existing EN Video content differs at ' +
                        ModuleEnSheetPublishService.a1(entry.r as int, entry.c as int))
            }
        }
        checkFormulas(context, enTitle, enGid, changes)
        Map report = [status: 'DRY_RUN', module: location, viGid: viGid,
                      enGid: enGid.toString(), cells: changes.size(), chapters:
                              changes.collect { it.r }.unique().size()]
        if (!apply) return report
        if (snapshot(module) != fingerprint) {
            throw new GradleException('Optional Video sources changed before APPLY.')
        }
        // Re-read the same cells immediately before writing to avoid silent overwrites.
        if (readGrid(context, enTitle, changes) != before) {
            throw new GradleException('EN Video cells changed concurrently.')
        }
        writeValues(context, enGid, changes)
        List<List<Object>> after = readGrid(context, enTitle, changes)
        changes.eachWithIndex { Map entry, int i ->
            if ((after[i]?.first() ?: '').toString() != (entry.value ?: '').toString()) {
                throw new GradleException('EN Video readback failed at ' +
                        ModuleEnSheetPublishService.a1(entry.r as int, entry.c as int))
            }
        }
        if (snapshot(module) != fingerprint) {
            throw new GradleException('Optional Video source drift after EN Sheet update.')
        }
        Map result = exporter.execute(root, module, fileUrl,
                fileUrl + '#gid=' + enGid, 'en', true)
        File workbook = result.file as File
        workbook.withInputStream { stream ->
            new XSSFWorkbook(stream).withCloseable { XSSFWorkbook book ->
                if (book.numberOfSheets != 1) {
                    throw new GradleException('Optional Video EN Excel structure invalid.')
                }
                changes.each { Map entry ->
                    if (ModuleContentExcelSyncService.text(book.getSheetAt(0),
                            (entry.r as int) + 1, (entry.c as int) + 1) != entry.value) {
                        throw new GradleException('Optional Video EN Excel drift at ' +
                                ModuleEnSheetPublishService.a1(entry.r as int, entry.c as int))
                    }
                }
            }
        }
        List<List<Object>> ending = readGrid(context, enTitle,
                [[r:1,c:9], [r:2,c:9]])
        if (ending != status || readStatus(reader.openTab(fileUrl, viGid)) != 'STEP 15') {
            throw new GradleException('STEP 15 status changed during optional Video publish.')
        }
        report.status = 'PUBLISHED'
        report.excel = result.file.absolutePath
        return report
    }

    /** Pure plan; only X:AA on actual Menu Cấp 1 rows, never another Sheet cell. */
    static List<Map> plan(File module) {
        File baseline = safe(module, 'content/sheet/vi/module.xlsx')
        if (!baseline.isFile()) throw new GradleException('VI workbook not exported after Video.')
        return baseline.withInputStream { stream ->
            new XSSFWorkbook(stream).withCloseable { XSSFWorkbook book ->
                if (book.numberOfSheets != 1) throw new GradleException('Invalid VI workbook.')
                def sheet = book.getSheetAt(0)
                List<Map> chapters = ModuleContentExcelSyncService.menu(sheet)
                List<Map> plan = []
                int authored = 0
                chapters.each { Map chapter ->
                    String relative = (chapter.source as String)
                            .replaceFirst('/[^/]+$', '/video/video-script.yml')
                    File viFile = safe(module, 'content/readme/docs/vi/menu/' + relative)
                    File enFile = safe(module, 'content/readme/docs/en/menu/' + relative)
                    if (viFile.isFile() != enFile.isFile()) {
                        throw new GradleException('VI/EN Video coverage differs: ' + relative)
                    }
                    if (!viFile.isFile()) return
                    authored++
                    String text = enFile.getText('UTF-8')
                    String viText = viFile.getText('UTF-8')
                    String sheetVideo = (24..27).collect { int col ->
                        ModuleContentExcelSyncService.text(sheet, chapter.row as int, col)
                    }.join('')
                    if (viText != sheetVideo) {
                        throw new GradleException('Stale VI Excel Video source: ' + relative)
                    }
                    Map source = VideoYamlContentUtils.parse(viText,
                            chapter.title as String,
                            (chapter.children as List<Map>).collect { it.title as String })
                    Map translated = VideoYamlContentUtils.parse(text)
                    if (source.video.url != translated.video.url ||
                            source.sections.size() != translated.sections.size()) {
                        throw new GradleException('Video EN technical structure mismatch: ' + relative)
                    }
                    source.sections.eachWithIndex { Map section, int s ->
                        List<Map> originalItems = section.items as List<Map>
                        List<Map> translatedItems = translated.sections[s].items as List<Map>
                        if (originalItems.size() != translatedItems.size() ||
                                originalItems.collect { [it.type, it.time] } !=
                                        translatedItems.collect { [it.type, it.time] }) {
                            throw new GradleException('Video EN scene/timing mismatch: ' + relative)
                        }
                    }
                    List<String> parts = ModuleContentExcelSyncService.split(text, 4)
                    parts.eachWithIndex { String part, int index ->
                        plan.add([r: (chapter.row as int) - 1,
                                  c: 23 + index, value: part])
                    }
                }
                if (!authored) throw new GradleException('No VI Video scripts for optional sync.')
                // Detect orphan EN or VI files (a renamed chapter must not be silently lost).
                for (String lang : ['vi', 'en']) {
                    File menu = safe(module, 'content/readme/docs/' + lang + '/menu')
                    if (!menu.isDirectory()) continue
                    Set<String> expected = chapters.collect { Map chapter ->
                        (chapter.source as String).replaceFirst('/[^/]+$', '/video/video-script.yml')
                    } as Set<String>
                    def paths = Files.walk(menu.toPath())
                    try {
                        paths.forEach { Path path ->
                            String relative = menu.toPath().relativize(path).toString()
                                    .replace(File.separatorChar, '/' as char)
                            // Other README, Knowledge, Quiz and Interview files
                            // live in this tree and are outside Video ownership.
                            if (!(relative ==~ /[^\/]+\/video(?:\/.*)?/) &&
                                    !relative.endsWith('/video-script.yml')) return
                            if (Files.isSymbolicLink(path)) {
                                throw new GradleException('Symlink Video forbidden: ' + path)
                            }
                            if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                                if (!expected.contains(relative)) {
                                    throw new GradleException('Orphan ' + lang + ' Video: ' + relative)
                                }
                            }
                        }
                    } finally { paths.close() }
                }
                return plan
            }
        }
    }

    private static File safe(File module, String relative) {
        Path base = module.canonicalFile.toPath()
        Path target = base.resolve(relative).normalize()
        if (!target.startsWith(base)) throw new GradleException('Path escapes module.')
        Path cursor = base
        for (Path segment : base.relativize(target)) {
            cursor = cursor.resolve(segment)
            if (Files.isSymbolicLink(cursor)) {
                throw new GradleException('Symlink forbidden: ' + cursor)
            }
        }
        return target.toFile()
    }

    private static String snapshot(File module) {
        MessageDigest sha = MessageDigest.getInstance('SHA-256')
        for (String folder : ['content/readme/docs/vi/menu',
                              'content/readme/docs/en/menu']) {
            File directory = safe(module, folder)
            if (!directory.isDirectory()) continue
            def paths = Files.walk(directory.toPath())
            try {
                List<Path> files = []
                paths.forEach { Path file ->
                    if (file.fileName.toString() == 'video-script.yml') files.add(file)
                }
                files.sort { Path file -> file.toString() }.each { Path file ->
                    File video = safe(module, module.toPath().relativize(file).toString())
                    if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                        sha.update(video.absolutePath.getBytes('UTF-8'))
                        sha.update(video.bytes)
                    }
                }
            } finally { paths.close() }
        }
        sha.update(safe(module, 'content/sheet/vi/module.xlsx').bytes)
        return sha.digest().encodeHex().toString()
    }

    protected void checkFormulas(Map context, String title, int gid, List<Map> cells) {
        Sheets client = context.client as Sheets
        String range = "'" + title.replace("'", "''") + "'!X20:AA"
        int endRow = cells.collect { (it.r as int) + 1 }.max() as int
        Spreadsheet data = client.spreadsheets().get(context.spreadsheetId as String)
                .setRanges([range + endRow])
                .setFields('sheets(properties(sheetId),data(startRow,startColumn,' +
                        'rowData(values(userEnteredValue))))').execute()
        def selected = (data.sheets ?: []).find { it.properties?.sheetId == gid }
        if (selected == null) throw new GradleException('EN formula preflight failed.')
        for (def block : (selected.data ?: [])) {
            (block.rowData ?: []).eachWithIndex { row, int idx ->
                (row.values ?: []).eachWithIndex { value, int col ->
                    if (value.userEnteredValue?.formulaValue != null) {
                        throw new GradleException('Formula in protected EN Video range.')
                    }
                }
            }
        }
    }
}
