package com.example.learning.task.sheets.service.excel

import com.example.learning.task.sheets.service.ReferenceSheetImportService

/** Phase 4: Reference stays one authored YAML string at L3. */
class ExcelReferenceService {
    void fill(ModuleExcelContext ctx) {
        String authored = ctx.read('content/reference/' + ctx.language + '/references.yml')
        ReferenceSheetImportService.parseReference(authored)
        ctx.set(3, 12, authored)
    }
}
