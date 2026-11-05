package com.example.learning.task.sheets.service

import com.example.learning.task.sheets.service.excel.ModuleExcelContext
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest
import com.google.api.services.sheets.v4.model.DeleteSheetRequest
import com.google.api.services.sheets.v4.model.Request
import groovy.json.JsonSlurper
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.gradle.api.GradleException

import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.regex.Matcher
import java.util.regex.Pattern

/** Prime-only STEP_FINAL cleanup: remove the verified module tab after STEP 12 and Git QA. */
class ModuleOnlineSheetCleanupService {
    private static final Pattern SPREADSHEET_PATH =
            Pattern.compile('^/spreadsheets/d/([A-Za-z0-9_-]+)(?:/.*)?$')
    private static final Pattern MAPPING_ENTRY =
            Pattern.compile('^(.+?) sheet id : ([0-9]+)$')

    private final SheetsMetadataReaderService reader

    ModuleOnlineSheetCleanupService(
            SheetsMetadataReaderService reader = new SheetsMetadataReaderService()) {
        this.reader = reader
    }

    Map execute(File rootDirectory, File moduleDirectory, String fileUrl,
                String sheetId, boolean apply) {
        File root = rootDirectory.canonicalFile
        File module = moduleDirectory.canonicalFile
        File moduleRoot = new File(root, 'module').canonicalFile
        if (!module.isDirectory() || module == moduleRoot ||
                !module.toPath().startsWith(moduleRoot.toPath()) ||
                !new File(module, 'gradle.properties').isFile()) {
            throw new GradleException('STEP_FINAL Sheet cleanup requires a real module Gradle project.')
        }
        String location = root.toPath().relativize(module.toPath())
                .toString().replace(File.separatorChar, '/' as char)
        String currentStep = ModuleCurrentStepService.read(module)
        if (!(currentStep in ['12', 'FINAL'])) {
            throw new GradleException('STEP_FINAL Sheet cleanup requires CURRENT_STEP 12 or FINAL; found ' +
                    currentStep)
        }
        Map master = new JsonSlurper().parse(new File(module, 'master.json')) as Map
        String serviceName = master.SERVICE_NAME?.VALUE as String
        List languages = master.MODULE_LANGUAGE?.VALUE as List
        if (!serviceName?.trim() || !languages ||
                languages.toSet().size() != languages.size() ||
                languages.any { !(it instanceof String) || !(it ==~ /[a-z]{2}/) }) {
            throw new GradleException('Invalid module languages or SERVICE_NAME for STEP_FINAL cleanup.')
        }
        List<Map> workbooks = verifyWorkbooks(module, location, serviceName, languages)

        String gid = sheetId?.trim()
        if (!(gid ==~ /(?:0|[1-9][0-9]*)/)) {
            throw new GradleException('sheetID must be the numeric Google Sheets tab gid.')
        }
        int targetId
        try {
            targetId = Integer.parseInt(gid)
        } catch (NumberFormatException ignored) {
            throw new GradleException('sheetID is outside the supported Google Sheets gid range.')
        }
        String spreadsheetId = extractSpreadsheetId(fileUrl)
        String expectedTitle = findMappedTitle(root, spreadsheetId, gid)
        if (expectedTitle.equalsIgnoreCase('Template')) {
            throw new GradleException('Refusing to delete the shared Template tab.')
        }

        Map context = apply ? reader.openSpreadsheetForWrite(fileUrl) :
                reader.openSpreadsheet(fileUrl)
        if (context.spreadsheetId != spreadsheetId) {
            throw new GradleException('Sheet cleanup spreadsheet identity mismatch.')
        }
        List<Map> tabs = context.tabs as List<Map>
        List<Map> candidates = tabs.findAll { it.gid?.toString() == gid }
        if (candidates.size() > 1) {
            throw new GradleException('Duplicate Sheet gid in Google metadata; refusing cleanup.')
        }
        Map result = [status: 'DRY_RUN', module: location,
                      spreadsheetId: spreadsheetId, gid: gid, tab: expectedTitle,
                      workbooks: workbooks]
        if (!candidates) {
            result.status = 'ALREADY_ABSENT'
            return result
        }
        Map target = candidates.first()
        if (target.title != expectedTitle || target.moduleLocation != location) {
            throw new GradleException('STEP_FINAL Sheet cleanup tab title/I3 mismatch: ' +
                    'the mapped tab does not belong to this module.')
        }
        if (tabs.size() <= 1) {
            throw new GradleException('Refusing to delete the last worksheet in a spreadsheet.')
        }
        if (!apply) return result

        // One explicit deleteSheet request: the spreadsheet file and other gids remain intact.
        deleteTab(context, targetId)
        List<Map> after = readbackTabs(context)
        Set<String> survivors = tabs.findAll { it.gid?.toString() != gid }
                .collect { it.gid.toString() + '\u0000' + it.title.toString() } as Set<String>
        Set<String> actual = after.collect {
            it.gid.toString() + '\u0000' + it.title.toString()
        } as Set<String>
        if (after.any { it.gid?.toString() == gid } || survivors != actual ||
                after.size() != survivors.size()) {
            throw new GradleException('STEP_FINAL Google Sheets deletion readback FAILED; ' +
                    'inspect the tab list before retrying.')
        }
        workbooks.each { Map item ->
            if (ModuleExcelContext.sha(new File(item.path as String).bytes) != item.sha256) {
                throw new GradleException('Offline workbook changed during Sheet cleanup: ' +
                        item.path)
            }
        }
        result.status = 'DELETED'
        return result
    }

    private static List<Map> verifyWorkbooks(File module, String location,
                                              String serviceName, List languages) {
        return languages.collect { String language ->
            File file = new File(module, 'content/sheet/' + language + '/module.xlsx')
            if (!file.canonicalFile.toPath().startsWith(module.toPath()) ||
                    !Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                throw new GradleException('STEP 12 offline workbook missing: ' + file)
            }
            try {
                Files.newInputStream(file.toPath()).withCloseable { stream ->
                    new XSSFWorkbook(stream).withCloseable { XSSFWorkbook book ->
                        if (book.numberOfSheets != 1) {
                            throw new GradleException('STEP 12 workbook must contain one sheet: ' + file)
                        }
                        def sheet = book.getSheetAt(0)
                        if (sheet.getRow(0)?.getCell(1)?.toString() != serviceName ||
                                sheet.getRow(2)?.getCell(8)?.toString() != location ||
                                sheet.getRow(18)?.getCell(28)?.toString() != 'Summary YAML') {
                            throw new GradleException('STEP 12 workbook identity/schema mismatch: ' +
                                    file)
                        }
                    }
                }
            } catch (GradleException exception) {
                throw exception
            } catch (Exception exception) {
                throw new GradleException('Cannot validate STEP 12 workbook: ' + file, exception)
            }
            [language: language, path: file.absolutePath,
             sha256: ModuleExcelContext.sha(file.bytes)]
        }
    }

    private static String extractSpreadsheetId(String url) {
        URI uri
        try {
            uri = new URI(url?.trim())
        } catch (Exception ignored) {
            throw new GradleException('fileUrl must be a valid Google Sheets document URL.')
        }
        if (uri.scheme != 'https' || uri.host != 'docs.google.com') {
            throw new GradleException('fileUrl must be an HTTPS docs.google.com spreadsheet URL.')
        }
        Matcher matcher = SPREADSHEET_PATH.matcher(uri.path ?: '')
        if (!matcher.matches()) {
            throw new GradleException('fileUrl does not identify a Google spreadsheet.')
        }
        return matcher.group(1)
    }

    private static String findMappedTitle(File root, String spreadsheetId, String gid) {
        File mapping = new File(root, 'module-generate-agent/module-generate-info.txt')
        if (!mapping.isFile()) {
            throw new GradleException('Missing module-generate-info.txt for Sheet cleanup.')
        }
        String activeSpreadsheet = null
        boolean nextUrl = false
        List<String> matches = []
        mapping.eachLine('UTF-8') { String raw ->
            String line = raw.trim()
            if (line == 'Excel Page URL') {
                activeSpreadsheet = null
                nextUrl = true
            } else if (nextUrl && line) {
                activeSpreadsheet = extractSpreadsheetId(line)
                nextUrl = false
            } else if (activeSpreadsheet == spreadsheetId) {
                Matcher item = MAPPING_ENTRY.matcher(line)
                if (item.matches() && item.group(2) == gid) {
                    matches.add(item.group(1).trim())
                }
            }
        }
        if (matches.size() != 1 || !matches.first()) {
            throw new GradleException('Expected exactly one mapping for this spreadsheet/gid ' +
                    'in module-generate-info.txt.')
        }
        return matches.first()
    }

    protected void deleteTab(Map context, int gid) {
        (context.client as Sheets).spreadsheets()
                .batchUpdate(context.spreadsheetId as String,
                        new BatchUpdateSpreadsheetRequest().setRequests(
                                [new Request().setDeleteSheet(
                                        new DeleteSheetRequest().setSheetId(gid))]))
                .execute()
    }

    protected List<Map> readbackTabs(Map context) {
        def metadata = (context.client as Sheets).spreadsheets()
                .get(context.spreadsheetId as String)
                .setFields('sheets(properties(sheetId,title))').execute()
        return (metadata.sheets ?: []).collect { sheet ->
            [gid: sheet.properties?.sheetId?.toString(),
             title: sheet.properties?.title ?: '']
        }
    }
}
