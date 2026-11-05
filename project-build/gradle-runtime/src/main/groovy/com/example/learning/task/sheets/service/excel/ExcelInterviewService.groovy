package com.example.learning.task.sheets.service.excel

/** Phase 8: Interview main P20:R24 + answers P27:R by groupID. */
class ExcelInterviewService {
    void fill(ModuleExcelContext ctx) {
        new ExcelAssessmentWriter().fill(ctx, 'interview', 'interviewQuestion',
                'questions', 16, 3, 'answers', 'questionIndex', 'answer')
    }
}
