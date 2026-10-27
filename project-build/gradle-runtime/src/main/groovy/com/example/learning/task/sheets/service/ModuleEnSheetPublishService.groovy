package com.example.learning.task.sheets.service

import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.*
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.gradle.api.GradleException

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * STEP 15, module-local and code-only. Duplicate a canonical Google Template,
 * then write only cell values. Never recreate the sheet formatting from Excel.
 *
 * The deterministic sheetId and persisted module-local mapping allow recovery
 * even if execution terminates between Google's duplicate and local checkpoint.
 */
class ModuleEnSheetPublishService {
    private final SheetsMetadataReaderService reader
    private final ModuleContentExcelSyncService workbookService
    private final ModuleSheetExcelExportService exporter
    private static final int MAX_WRITE_BYTES = 1_000_000

    ModuleEnSheetPublishService(
            SheetsMetadataReaderService reader = new SheetsMetadataReaderService(),
            ModuleContentExcelSyncService workbookService = new ModuleContentExcelSyncService(),
            ModuleSheetExcelExportService exporter = new ModuleSheetExcelExportService()) {
        this.reader = reader
        this.workbookService = workbookService
        this.exporter = exporter
    }

    Map execute(File rootFolder, File moduleFolder, String spreadsheetUrl, String viGid,
                String phase, boolean apply) {
        File root = rootFolder.canonicalFile
        File module = moduleFolder.canonicalFile
        String location = ModuleEnSheetPublishService.locate(root, module)
        if (!(viGid ==~ /\d+/)) throw new GradleException('STEP 15 requires numeric VI gid.')
        String selected = phase?.trim()?.toUpperCase(Locale.ROOT)
        if (!(selected in ['CLONE', 'SYNC', 'VERIFY', 'EXPORT', 'PUBLISH'])) {
            throw new GradleException('Unknown STEP 15 phase: ' + phase)
        }

        File statePath = new File(module, 'content/sheet/en/publish-state.json')
        File draft = new File(module, 'build/step15/en-upload.xlsx')
        File baseline = new File(module, 'content/sheet/vi/module.xlsx')
        [statePath, draft, baseline].each {
            ModuleEnSheetPublishService.ensureSafe(module, it)
        }
        if (!baseline.isFile()) throw new GradleException('STEP 12 VI Excel is required.')
        String sourceHash = ModuleEnSheetPublishService.shaSources(module)
        Map vi = reader.openTab(spreadsheetUrl, viGid)
        if (vi.moduleLocation != location) {
            throw new GradleException('VI Sheet I3 does not match module: ' + location)
        }
        Map context = apply ? reader.openSpreadsheetForWrite(spreadsheetUrl)
                : reader.openSpreadsheet(spreadsheetUrl)
        if (context.spreadsheetId != vi.spreadsheetId) {
            throw new GradleException('VI tab and target spreadsheet mismatch.')
        }
        Map template = ((context.tabs ?: []) as List<Map>).find {
            it.title == 'Template'
        }
        if (template == null || !(template.gid ==~ /\d+/)) {
            throw new GradleException('Canonical Template tab not found.')
        }
        // Strictly reject duplicate Template tabs if provider metadata is anomalous.
        if (((context.tabs ?: []) as List<Map>).count { it.title == 'Template' } != 1) {
            throw new GradleException('Template title is not unique.')
        }
        verifyTemplate(context, template)
        if (ModuleEnSheetPublishService.hasPracticeSource(module) &&
                (template.columns as int) < 33) {
            throw new GradleException('STEP 15 Practice requires Template A:AG (33 columns).')
        }

        String title = ModuleEnSheetPublishService.enTitle(vi.tabTitle as String)
        int gid = ModuleEnSheetPublishService.deterministicGid(
                context.spreadsheetId as String, location)
        Map state = ModuleEnSheetPublishService.loadState(statePath)
        String viStatus = ModuleCurrentStepService.read(module)
        if (!(viStatus in ['14','15','FINAL'])) {
            throw new GradleException('STEP 14 has not completed in module master.json: ' + viStatus)
        }
        ModuleEnSheetPublishService.verifyState(state,
                context.spreadsheetId as String, viGid, location,
                title, gid, sourceHash)
        Map found = ((context.tabs ?: []) as List<Map>).find { it.gid == gid.toString() }
        Map sameName = ((context.tabs ?: []) as List<Map>).find {
            it.title?.toString()?.equalsIgnoreCase(title)
        }
        if ((found != null && found.title != title) ||
                (sameName != null && sameName.gid != gid.toString())) {
            throw new GradleException('EN tab name/gid collision; refusing to clone again.')
        }
        Map result = [status: apply ? 'APPLIED' : 'DRY_RUN', phase: selected,
                      module: location, gid: gid.toString(), tab: title,
                      existing: found != null, state: statePath.absolutePath,
                      sourceSha256: sourceHash, url: spreadsheetUrl + '#gid=' + gid]
        if (selected in ['PUBLISH','EXPORT'] && state.phase == 'EXPORTED' &&
                viStatus in ['14', '15', 'FINAL'] && found != null) {
            verifyIdentity(context, gid, title, location)
            File finished = new File(module, 'content/sheet/en/module.xlsx')
            ModuleEnSheetPublishService.requireFinishedExcel(finished)
            result.status = 'ALREADY_PUBLISHED'
            result.excel = finished.absolutePath
            return result
        }
        if (selected == 'CLONE') {
            if (!apply) return result
            cloneAndPersist(context, template, found, statePath,
                    state, result, location, viGid)
            return result
        }

        // Publish phases never assume that a tab exists until we have checked.
        if (selected == 'PUBLISH' && apply) {
            cloneAndPersist(context, template, found, statePath,
                    state, result, location, viGid)
            context = reader.openSpreadsheetForWrite(spreadsheetUrl)
            found = ((context.tabs ?: []) as List<Map>).find { it.gid == gid.toString() }
        }
        if (selected == 'PUBLISH' && !apply && found == null) {
            result.next = 'CLONE_EN_TAB'
            return result
        }
        if (found == null) {
            throw new GradleException('EN tab has not been cloned; run CLONE first.')
        }
        verifyIdentity(context, gid, title, location)

        if (selected in ['SYNC', 'PUBLISH']) {
            ModuleEnSheetPublishService.assertSource(module, sourceHash)
            Map workbook = workbookService.execute(root, module, 'ALL', apply, true, draft)
            result.workbook = workbook.status
            result.changedCells = workbook.changedCells
            if (apply) {
                ModuleEnSheetPublishService.assertSource(module, sourceHash)
                writeEnContent(context, gid, title, location, draft, baseline)
                ModuleEnSheetPublishService.saveState(statePath,
                        ModuleEnSheetPublishService.stateData(result,
                                context.spreadsheetId as String, viGid, 'SYNCED'))
            }
            if (selected == 'SYNC') return result
        }

        if (selected in ['VERIFY', 'PUBLISH', 'EXPORT']) {
            if (!apply && selected == 'PUBLISH') {
                // Read-only PREVIEW of the existing tab cannot assert that the
                // pending content is uploaded; report this without publishing.
                result.status = 'DRY_RUN'
                result.next = 'SYNC_AND_VERIFY'
                return result
            }
            if (selected in ['VERIFY', 'PUBLISH']) {
                ModuleEnSheetPublishService.assertSource(module, sourceHash)
                Map checked = verifyEnContent(context, gid, title, location,
                        draft, baseline)
                result.verified = checked.cells
                if (apply) ModuleEnSheetPublishService.saveState(statePath,
                        ModuleEnSheetPublishService.stateData(result,
                                context.spreadsheetId as String, viGid, 'VERIFIED'))
                if (selected == 'VERIFY') return result
            }
            if (selected == 'EXPORT' && state.phase != 'VERIFIED' &&
                    state.phase != 'EXPORTED') {
                throw new GradleException('EN tab must pass content verification before export.')
            }
            if (selected == 'EXPORT') {
                ModuleEnSheetPublishService.assertSource(module, sourceHash)
                verifyEnContent(context, gid, title, location, draft, baseline)
            }
            if (apply) {
                if (selected in ['PUBLISH','EXPORT']) {
                    // Only Prime may advance CURRENT_STEP in master.json after QA.
                }
                Map exported = exporter.execute(root, module, spreadsheetUrl,
                        result.url as String, 'en', true)
                result.excel = exported.file.absolutePath
                result.exportedCells = exported.cells
                // Verify a valid offline snapshot; no J3 writes/reads.
                ModuleEnSheetPublishService.requireFinishedExcel(
                        new File(module, 'content/sheet/en/module.xlsx'))
                ModuleEnSheetPublishService.saveState(statePath,
                        ModuleEnSheetPublishService.stateData(result,
                                context.spreadsheetId as String, viGid, 'EXPORTED'))
            }
        }
        return result
    }

    private void cloneAndPersist(Map context, Map template, Map found, File statePath,
                                 Map state, Map report, String location, String viGid) {
        int gid = Integer.parseInt(report.gid as String)
        String title = report.tab as String
        if (found == null) {
            int created = duplicate(context, Integer.parseInt(template.gid as String), gid, title)
            if (created != gid) throw new GradleException('DuplicateSheet returned unexpected gid.')
        }
        // Capture mapping BEFORE writing I3/B1; retry can recover if these writes fail.
        if (!state) {
            ModuleEnSheetPublishService.saveState(statePath,
                    ModuleEnSheetPublishService.stateData(report,
                            context.spreadsheetId as String, viGid, 'CLONED'))
        }
        Map latest = reader.openSpreadsheetForWrite(
                'https://docs.google.com/spreadsheets/d/' + context.spreadsheetId + '/edit')
        Map tab = ((latest.tabs ?: []) as List<Map>).find { it.gid == gid.toString() }
        if (!tab || tab.title != title) {
            throw new GradleException('Cloned tab could not be verified.')
        }
        if (tab.moduleLocation && tab.moduleLocation != location) {
            throw new GradleException('Cloned EN tab has unexpected I3: ' + tab.moduleLocation)
        }
        writeValues(latest, gid, [
                [r: 0, c: 1, value: title],
                [r: 2, c: 8, value: location]
        ])
        verifyIdentity(latest, gid, title, location)
        verifyClonedFormatting(latest, [title: title, gid: gid.toString(),
                rows: template.rows, columns: template.columns])
        report.existing = found != null
    }

    protected int duplicate(Map ctx, int templateGid, int newGid, String title) {
        Request request = new Request().setDuplicateSheet(new DuplicateSheetRequest()
                .setSourceSheetId(templateGid).setNewSheetId(newGid).setNewSheetName(title))
        def response = (ctx.client as Sheets).spreadsheets().batchUpdate(
                ctx.spreadsheetId as String,
                new BatchUpdateSpreadsheetRequest().setRequests([request])).execute()
        Integer id = response.replies?.first()?.duplicateSheet?.properties?.sheetId
        if (id == null) throw new GradleException('DuplicateSheet did not return gid.')
        return id
    }

    protected void verifyIdentity(Map context, int gid, String title, String location) {
        Map tab = reader.readTab(reader.openSpreadsheet(
                'https://docs.google.com/spreadsheets/d/' + context.spreadsheetId + '/edit'),
                gid.toString())
        if (tab.tabTitle != title || tab.moduleLocation != location) {
            throw new GradleException('EN tab I3/title readback mismatch.')
        }
    }

    protected void verifyTemplate(Map ctx, Map template) {
        inspectNativeFormatting(ctx, template)
    }

    private static boolean hasPracticeSource(File module) {
        File chapters = new File(module, 'content/readme/docs/vi/menu')
        if (!chapters.isDirectory()) return false
        File[] dirs = chapters.listFiles()
        return dirs != null && dirs.any { File chapter ->
            chapter.isDirectory() && new File(chapter, 'practice/scenarios.yml').isFile()
        }
    }

    protected void verifyClonedFormatting(Map ctx, Map cloned) {
        inspectNativeFormatting(ctx, cloned)
    }

    /** Ensure the Google-native dropdown and conditional color rules survived. */
    private static void inspectNativeFormatting(Map ctx, Map tab) {
        if ((tab.rows as int) < 700 || (tab.columns as int) < 29) {
            throw new GradleException('Template/EN grid requires 700 rows and 29 columns (AC Summary).')
        }
        String name = ModuleEnSheetPublishService.quote(tab.title as String)
        List<String> ranges = ['O20','O21','O700','AB20','AB700'].collect {
            name + '!' + it
        }
        Spreadsheet response = (ctx.client as Sheets).spreadsheets()
                .get(ctx.spreadsheetId as String).setRanges(ranges)
                .setFields('sheets(properties(sheetId,title),conditionalFormats,' +
                        'data(startRow,startColumn,rowData(values(dataValidation))))')
                .execute()
        def sheet = (response.sheets ?: []).find {
            it.properties?.sheetId?.toString() == tab.gid.toString()
        }
        if (sheet == null || (sheet.data ?: []).size() < 5) {
            throw new GradleException('Template/EN Google-native grid readback failed.')
        }
        sheet.data.take(3).each { item ->
            def rule = item.rowData?.first()?.values?.first()?.dataValidation
            List options = rule?.condition?.values?.collect { it.userEnteredValue }
            if (rule?.condition?.type != 'ONE_OF_LIST' ||
                    options != ['BASIC','INTERMEDIATE','ADVANCED'] ||
                    rule.strict != true || rule.showCustomUi != true) {
                throw new GradleException('Template Difficulty dropdown invalid.')
            }
        }
        sheet.data.drop(3).take(2).each { item ->
            def rule = item.rowData?.first()?.values?.first()?.dataValidation
            List options = rule?.condition?.values?.collect { it.userEnteredValue }
            if (rule?.condition?.type != 'ONE_OF_LIST' ||
                    options != ['TRUE','FALSE'] || rule.strict != true) {
                throw new GradleException('Template isImportant dropdown invalid.')
            }
        }
        Set<String> colors = (sheet.conditionalFormats ?: []).findResults { rule ->
            def scoped = (rule.ranges ?: []).find { range ->
                range.sheetId?.toString() == tab.gid.toString() &&
                        range.startRowIndex == 19 && range.endRowIndex == 700 &&
                        range.startColumnIndex == 14 && range.endColumnIndex == 15
            }
            scoped && rule.booleanRule?.condition?.type == 'TEXT_EQ'
                    ? rule.booleanRule.condition.values?.first()?.userEnteredValue
                    : null
        } as Set<String>
        if (!colors.containsAll(['BASIC','INTERMEDIATE','ADVANCED'])) {
            throw new GradleException('Template Difficulty color rules invalid.')
        }
    }

    private void writeEnContent(Map context, int gid, String title, String location,
                                File draft, File baseline) {
        List<Map> changes = ModuleEnSheetPublishService.contentPlan(draft, baseline)
        Map tab = ((context.tabs ?: []) as List<Map>).find { it.gid == gid.toString() }
        if (!tab || changes.any { Map entry ->
            (entry.r as int) >= (tab.rows as int) ||
                    (entry.c as int) >= (tab.columns as int)
        }) {
            throw new GradleException('EN Sheet grid is smaller than required content ranges.')
        }
        writeValues(context, gid, changes)
    }

    /**
     * Compose an exact, VALUE-ONLY update plan from the VI-sheet-shaped EN
     * workbook. All template userEnteredFormat, validations, and notes remain.
     * The old Sheet J3 is outside all content ownership; progress lives in master.json.
     */
    static List<Map> contentPlan(File translatedExcel, File viExcel) {
        return translatedExcel.withInputStream { InputStream input ->
            new XSSFWorkbook(input).withCloseable { XSSFWorkbook book ->
                viExcel.withInputStream { InputStream viStream ->
                    new XSSFWorkbook(viStream).withCloseable { XSSFWorkbook original ->
                        def sheet = book.getSheetAt(0)
                        def before = original.getSheetAt(0)
                        List<Map> plan = []
                        // All content rows are source-defined by the VI snapshot.
                        int lastMenu = 19
                        for (int row = 19; row <= before.lastRowNum; row++) {
                            String a = ModuleContentExcelSyncService.text(before, row + 1, 1)
                            String b = ModuleContentExcelSyncService.text(before, row + 1, 2)
                            if (!a && !b && lastMenu < row) break
                            if (a || b) lastMenu = row
                        }
                        for (int row = 19; row <= lastMenu; row++) {
                            for (int col = 0; col <= 28; col++) {
                                if (!(col in [0,1,14,27] || (col >= 2 && col <= 13) ||
                                        (col >= 15 && col <= 22) || col == 28)) continue
                                plan.add([r: row, c: col,
                                          value: cellValue(sheet.getRow(row)?.getCell(col))])
                            }
                        }
                        for (int row = 2; row <= before.lastRowNum; row++) {
                            if (row > 2 && [5,6,7].every {
                                ModuleContentExcelSyncService.text(before, row + 1, it + 1).isEmpty()
                            }) break
                            for (int col = 5; col <= 7; col++) {
                                plan.add([r: row, c: col,
                                          value: cellValue(sheet.getRow(row)?.getCell(col))])
                            }
                        }
                        plan.add([r: 16, c: 1, value: cellValue(sheet.getRow(16)?.getCell(1))])
                        // Reference is shared across languages and has no AI translation.
                        plan.add([r: 1, c: 11, value: 'Reference'])
                        plan.add([r: 2, c: 11, value: cellValue(sheet.getRow(2)?.getCell(11))])
                        plan.add([r: 17, c: 28, value: 'Summary'])
                        plan.add([r: 18, c: 28, value: 'Summary YAML'])
                        // Fixed labels are translated by code, not by an AI service.
                        Map<String,String> labels = [
                                'A1':'Topic', 'A17':'Introduction', 'A18':'Menu',
                                'A19':'Chapter', 'B19':'Lesson',
                                'F2':'Title', 'G2':'Purpose', 'H2':'Learning objectives'
                        ]
                        labels.each { String address, String value ->
                            def ref = new org.apache.poi.ss.util.CellReference(address)
                            plan.add([r: ref.row, c: ref.col as int, value: value])
                        }
                        (2..13).each { int c ->
                            plan.add([r:18,c:c,value:'Answer'])
                        }
                        (15..18).each { int c ->
                            plan.add([r:18,c:c,value:'Interview YAML'])
                        }
                        (19..22).each { int c ->
                            plan.add([r:18,c:c,value:'Quiz YAML'])
                        }
                        (23..26).each { int c ->
                            plan.add([r:18,c:c,value:'Video Script'])
                        }
                        // VI-only authoring prompt cells are not EN learning content.
                        (1..2).each { int r ->
                            (0..3).each { int c ->
                                plan.add([r:r,c:c,value:null])
                            }
                        }
                        return plan
                    }
                }
            }
        }
    }

    private static Object cellValue(Cell cell) {
        if (cell == null || cell.cellType == CellType.BLANK) return null
        switch (cell.cellType) {
            case CellType.STRING: return cell.stringCellValue
            case CellType.BOOLEAN: return cell.booleanCellValue
            case CellType.NUMERIC: return cell.numericCellValue
            default: throw new GradleException('Invalid upload cell type at ' + cell.address)
        }
    }

    protected void writeValues(Map context, int gid, List<Map> changes) {
        List<Request> batch = []
        int size = 0
        // Coalesce adjacent cells into one request per contiguous row span.
        List<List<Map>> spans = []
        changes.sort { Map a, Map b ->
            ((a.r as int) <=> (b.r as int)) ?: ((a.c as int) <=> (b.c as int))
        }.each { Map entry ->
            if (!spans || (spans.last().last().r != entry.r) ||
                    ((spans.last().last().c as int) + 1 != (entry.c as int))) {
                spans.add([])
            }
            spans.last().add(entry)
        }
        spans.each { List<Map> entries ->
            List<CellData> cells = entries.collect { Map entry ->
                CellData cell = new CellData()
                if (entry.value != null) {
                    ExtendedValue value = new ExtendedValue()
                    if (entry.value instanceof Boolean) value.boolValue = entry.value as Boolean
                    else if (entry.value instanceof Number) value.numberValue =
                            (entry.value as Number).doubleValue()
                    else value.stringValue = entry.value.toString()
                    cell.userEnteredValue = value
                }
                return cell
            }
            Request request = new Request().setUpdateCells(new UpdateCellsRequest()
                    .setStart(new GridCoordinate().setSheetId(gid)
                            .setRowIndex(entries[0].r as int)
                            .setColumnIndex(entries[0].c as int))
                    .setRows([new RowData().setValues(cells)])
                    .setFields('userEnteredValue'))
            int bytes = request.toString().getBytes(StandardCharsets.UTF_8).length
            if (size + bytes > MAX_WRITE_BYTES && batch) {
                sendBatch(context, batch)
                batch = []
                size = 0
            }
            batch.add(request)
            size += bytes
        }
        if (batch) sendBatch(context, batch)
    }

    protected void sendBatch(Map context, List<Request> requests) {
        (context.client as Sheets).spreadsheets().batchUpdate(
                context.spreadsheetId as String,
                new BatchUpdateSpreadsheetRequest().setRequests(requests)).execute()
    }

    protected Map verifyEnContent(Map context, int gid, String title, String location,
                                  File expectedWorkbook, File viWorkbook) {
        if (!expectedWorkbook.isFile()) throw new GradleException('Missing expected EN workbook.')
        List<Map> expected = ModuleEnSheetPublishService.contentPlan(
                expectedWorkbook, viWorkbook)
        // Compare exact typed values; a partial batch upload must not count as PASS.
        List<List<Object>> actual = readGrid(context, title, expected)
        expected.eachWithIndex { Map entry, int i ->
            Object observed = (actual[i] && actual[i][0] != null) ? actual[i][0] : null
            Object wanted = entry.value
            boolean same = (wanted == null || wanted == '') ?
                    (observed == null || observed == '') :
                    (wanted instanceof Number && observed instanceof Number ?
                            (new BigDecimal(wanted.toString()).compareTo(
                                    new BigDecimal(observed.toString())) == 0) :
                            wanted.toString() == observed?.toString())
            if (!same) {
                throw new GradleException('EN Sheet mismatch at row=' +
                        ((entry.r as int) + 1) + ' col=' + ((entry.c as int) + 1))
            }
        }
        return [cells: expected.size()]
    }

    protected List<List<Object>> readGrid(Map context, String title, List<Map> expected) {
        // Fetch contiguous ranges using values.batchGet in bounded requests.
        Sheets client = context.client as Sheets
        List<List<Object>> values = []
        expected.collate(100).each { List<Map> group ->
            List<String> addresses = group.collect { Map entry ->
                ModuleEnSheetPublishService.quote(title) + '!' +
                        ModuleEnSheetPublishService.a1(entry.r as int, entry.c as int)
            }
            def response = client.spreadsheets().values().batchGet(
                    context.spreadsheetId as String).setRanges(addresses)
                    .setValueRenderOption('UNFORMATTED_VALUE').execute()
            response.valueRanges.each { item ->
                values.add(item.values?.first() ?: [])
            }
        }
        return values
    }

    static String a1(int r, int c) {
        int i = c + 1
        String col = ''
        while (i > 0) {
            int rem = (i - 1) % 26
            col = Character.toString((char) (65 + rem)) + col
            i = (i - 1).intdiv(26)
        }
        return col + (r + 1)
    }

    static String enTitle(String viTitle) {
        String title = (viTitle ?: '').trim()
        if (!title || title == 'Template' || title.endsWith(' EN') ||
                title.length() > 95) {
            throw new GradleException('Invalid VI tab title for EN suffix: ' + title)
        }
        return title + ' EN'
    }

    static int deterministicGid(String spreadsheetId, String location) {
        byte[] value = MessageDigest.getInstance('SHA-256').digest(
                ('STEP15:' + spreadsheetId + ':' + location).getBytes(StandardCharsets.UTF_8))
        return (((value[0] & 0x7f) << 24) | ((value[1] & 0xff) << 16) |
                ((value[2] & 0xff) << 8) | (value[3] & 0xff)) ?: 1
    }

    private static String locate(File root, File module) {
        Path base = new File(root, 'module').canonicalFile.toPath()
        if (module.toPath() == base || !module.toPath().startsWith(base) ||
                !new File(module, 'gradle.properties').isFile()) {
            throw new GradleException('STEP 15 must run on one real module.')
        }
        String path = root.toPath().relativize(module.toPath())
                .toString().replace(File.separatorChar, '/' as char)
        RoadmapSheetImportService.resolveDestination(root, path)
        return path
    }

    private static void ensureSafe(File module, File file) {
        Path base = module.toPath()
        Path path = file.toPath().toAbsolutePath().normalize()
        if (!path.startsWith(base)) throw new GradleException('Path escapes module: ' + file)
        Path part = base
        for (Path segment : base.relativize(path)) {
            part = part.resolve(segment)
            if (Files.isSymbolicLink(part)) {
                throw new GradleException('Symlink in STEP 15 path: ' + part)
            }
        }
    }

    private static String shaSources(File module) {
        MessageDigest digest = MessageDigest.getInstance('SHA-256')
        List<String> files = ['content/sheet/vi/module.xlsx']
        for (String area : ['content/readme/docs/en', 'content/roadmap/en',
                            'src/main/resources/swagger/en']) {
            File parent = new File(module, area)
            if (!parent.exists()) continue
            def stream = Files.walk(parent.toPath())
            try {
                stream.forEach { Path child ->
                    String relative = module.toPath().relativize(child).toString()
                            .replace(File.separatorChar, '/' as char)
                    // Video scripts have their own optional publish transaction.
                    if (relative ==~ /content\/readme\/docs\/en\/menu\/[^\/]+\/video(?:\/.*)?/) return
                    if (Files.isSymbolicLink(child)) {
                        throw new GradleException('Symlink in translation tree: ' + child)
                    }
                    if (Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS)) {
                        files.add(relative)
                    }
                }
            } finally { stream.close() }
        }
        files.sort().each { String relative ->
            digest.update(relative.getBytes(StandardCharsets.UTF_8))
            digest.update(Files.readAllBytes(new File(module, relative).toPath()))
        }
        return digest.digest().encodeHex().toString()
    }

    private static void assertSource(File module, String expected) {
        if (shaSources(module) != expected) {
            throw new GradleException('VI Excel or EN source changed during STEP 15.')
        }
    }

    private static void requireFinishedExcel(File file) {
        if (!file.isFile()) {
            throw new GradleException('Published EN Excel is missing.')
        }
        try {
            file.withInputStream { stream ->
                new XSSFWorkbook(stream).withCloseable { XSSFWorkbook xlsx ->
                    if (xlsx.numberOfSheets != 1) {
                        throw new GradleException('Published EN Excel workbook structure invalid.')
                    }
                }
            }
        } catch (GradleException error) {
            throw error
        } catch (Exception error) {
            throw new GradleException('Published EN Excel could not be read.', error)
        }
    }

    private static Map loadState(File file) {
        if (!file.exists()) return [:]
        if (!file.isFile()) throw new GradleException('STEP 15 state is not a file.')
        Object result = new JsonSlurper().parse(file)
        if (!(result instanceof Map)) throw new GradleException('Invalid STEP 15 state.')
        return result as Map
    }

    private static void verifyState(Map state, String spreadsheet, String viGid,
                                    String location, String title, int enGid,
                                    String sourceHash) {
        if (!state) return
        Map wanted = [spreadsheetId: spreadsheet, viGid: viGid,
                      module: location, enTitle: title, enGid: enGid.toString(),
                      sourceSha256: sourceHash]
        wanted.each { String key, Object value ->
            if (state[key]?.toString() != value.toString()) {
                throw new GradleException('STEP 15 stored mapping/source changed: ' + key)
            }
        }
    }

    private static Map stateData(Map result, String spreadsheet, String viGid,
                                 String phase) {
        return [schema: 1, module: result.module, spreadsheetId: spreadsheet,
                viGid: viGid, enGid: result.gid, enTitle: result.tab,
                sourceSha256: result.sourceSha256, phase: phase]
    }

    private static void saveState(File file, Map document) {
        Files.createDirectories(file.parentFile.toPath())
        Path temp = Files.createTempFile(file.parentFile.toPath(), '.step15-', '.json')
        try {
            Files.write(temp, JsonOutput.prettyPrint(JsonOutput.toJson(document))
                    .getBytes(StandardCharsets.UTF_8))
            Files.move(temp, file.toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temp) }
    }

    private static String quote(String title) {
        return "'" + title.replace("'", "''") + "'"
    }
}
