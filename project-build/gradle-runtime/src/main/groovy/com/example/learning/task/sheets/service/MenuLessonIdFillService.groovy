package com.example.learning.task.sheets.service

import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest
import com.google.api.services.sheets.v4.model.ValueRange
import org.gradle.api.GradleException

/** Prime-only STEP 5 operation. All writes are confined to blank A child cells. */
class MenuLessonIdFillService {
    Map<String, Object> execute(Map<String, Object> context, boolean apply) {
        Sheets client = context.client as Sheets
        String id = context.spreadsheetId as String
        String tab = "'${(context.tabTitle as String).replace("'", "''")}'"
        List<List<Object>> rows = read(client, id, "${tab}!A19:B")
        Map parsed = ReadmeSheetImportService.parseMenu(rows)
        if (parsed.status != 'READY') {
            throw new GradleException('Cannot fill lesson IDs: Menu status=' + parsed.status)
        }
        int last = parsed.lastRow as int
        List<List<Object>> bases = read(client, id, "${tab}!C20:C${last}")
        Map<Integer, String> writes = MenuLessonIdService.plan(parsed.groups as List<Map>, bases)
        Map<String, Object> result = [
                status: writes.isEmpty() ? 'UNCHANGED' : (apply ? 'UPDATED' : 'DRY_RUN'),
                tab: context.tabTitle, gid: context.sheetId,
                location: context.moduleLocation, chapters: parsed.groups.size(),
                lessons: parsed.lessons, changed: writes.size(), cells: writes.collect {
                    int row, String value -> "A${row}=${value}"
                }
        ]
        if (writes.isEmpty()) return result

        // A blank displayed value can still carry a formula, note, chip or
        // other human-owned cell content. Fail before issuing any updates.
        def grid = client.spreadsheets().get(id)
                .setRanges(["${tab}!A20:A${last}"])
                .setIncludeGridData(true)
                .setFields('sheets(properties(sheetId),data(startRow,startColumn,' +
                        'rowData(values(userEnteredValue,note,chipRuns,textFormatRuns))))')
                .execute()
        def selected = (grid.sheets ?: []).find {
            it.properties?.sheetId?.toString() == context.sheetId?.toString()
        }
        if (selected == null || !(selected.data ?: [])) {
            throw new GradleException('Cannot verify A-child cell ownership before ID fill.')
        }
        writes.each { int row, String ignored ->
            int rowIndex = row - 1
            def block = selected.data.find { chunk ->
                int begin = chunk.startRow ?: 0
                rowIndex >= begin && rowIndex < begin + (chunk.rowData?.size() ?: 0)
            }
            def cell = block == null ? null :
                    block.rowData[rowIndex - (block.startRow ?: 0)]?.values?.first()
            if (cell?.userEnteredValue != null || cell?.note ||
                    cell?.chipRuns || cell?.textFormatRuns) {
                throw new GradleException("Occupied/formula/note/chip at A${row}; refusing to fill.")
            }
        }
        if (!apply) return result

        // Catch a concurrent menu edit before attempting the first write.
        if (read(client, id, "${tab}!A19:B") != rows ||
                read(client, id, "${tab}!C20:C${last}") != bases) {
            throw new GradleException('Concurrent Menu change detected; refusing to fill lesson IDs.')
        }
        List<ValueRange> updates = writes.collect { int row, String value ->
            new ValueRange().setRange("${tab}!A${row}")
                    .setMajorDimension('ROWS').setValues([[value]])
        }
        client.spreadsheets().values().batchUpdate(id,
                new BatchUpdateValuesRequest().setValueInputOption('RAW').setData(updates)).execute()

        List<List<Object>> finalMenu = read(client, id, "${tab}!A19:B")
        Map after = ReadmeSheetImportService.parseMenu(finalMenu)
        if (after.status != 'READY') {
            throw new GradleException('Lesson ID write readback: invalid Menu structure.')
        }
        MenuLessonIdService.validate(after.groups as List<Map>,
                read(client, id, "${tab}!C20:C${last}"))
        writes.each { int row, String expected ->
            String actual = finalMenu.size() > row - 19 &&
                    finalMenu[row - 19]?.size() > 0
                    ? finalMenu[row - 19][0]?.toString() : null
            if (actual != expected) {
                throw new GradleException("Lesson ID readback mismatch at A${row}.")
            }
        }
        return result
    }

    private static List<List<Object>> read(Sheets client, String id, String range) {
        return (client.spreadsheets().values().get(id, range)
                .setValueRenderOption('FORMULA').execute().values ?: []) as List<List<Object>>
    }
}
