package com.example.learning.task.sheets.service.excel

/** Phase 9: Case Study main S20:U24 + codes S27:U by groupID. */
class ExcelCaseStudyService {
    void fill(ModuleExcelContext ctx) {
        new ExcelAssessmentWriter().fill(ctx, 'casestudy', 'caseStudy',
                'cases', 19, 3, 'codes', 'caseIndex', 'code')
    }
}
