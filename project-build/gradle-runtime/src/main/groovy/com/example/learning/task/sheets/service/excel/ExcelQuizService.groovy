package com.example.learning.task.sheets.service.excel

/** Phase 10: module-wide Quiz V20:W24, no Phase 4. */
class ExcelQuizService {
    void fill(ModuleExcelContext ctx) {
        new ExcelAssessmentWriter().fill(ctx, 'quiz', 'quiz',
                'questions', 22, 2, null, null, null)
    }
}
