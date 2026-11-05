package com.example.learning.task.sheets.service.excel

/** Phase 11: reserved X:AA. Video becomes supported in a later iteration. */
class ExcelVideoService {
    void fill(ModuleExcelContext ctx) {
        for (int row = 20; row <= 1000; row++) {
            for (int col = 24; col <= 27; col++) {
                if (ctx.text(row, col)) {
                    throw new IllegalStateException('Reserved Video X:AA must be empty.')
                }
            }
        }
    }
}
