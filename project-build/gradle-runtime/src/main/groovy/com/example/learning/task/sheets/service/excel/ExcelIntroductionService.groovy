package com.example.learning.task.sheets.service.excel

/** Phase 5: Introduction remains authored Markdown. */
class ExcelIntroductionService {
    void fill(ModuleExcelContext ctx) {
        ctx.set(17, 2, ctx.read('content/readme/docs/' + ctx.language + '/BASE.md'))
    }
}
