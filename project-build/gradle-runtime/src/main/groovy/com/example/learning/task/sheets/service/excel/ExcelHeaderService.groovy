package com.example.learning.task.sheets.service.excel

import org.apache.poi.ss.usermodel.DataValidationConstraint
import org.apache.poi.ss.util.CellRangeAddressList
import org.apache.poi.xssf.usermodel.XSSFDataValidationHelper

/** Phase 2: deterministic labels. AB stays reserved, with no isImportant data. */
class ExcelHeaderService {
    void fill(ModuleExcelContext ctx) {
        String roadNote = '\nLưu ý là roadmap này để sơ đồ chứ không phải để viết markdown document nên hãy ngắn ngắn ít ít thâu \nDưới 10 mục'
        String menuNote = '\nDùng để viết markdown document , nên phải đầy đủ và chi tiết\nMenu cấp 1 viết ngắn gọn thâu do là để tiêu đề'
        [
                [1,1,'Chủ đề'], [1,2,ctx.serviceName], [1,6,'Roadmap'],
                [1,9,'Module Project'],
                [2,1,'ROAD 1'], [2,2,'Thiết kế cho tôi roadmap 1 cấp của'],
                [2,3,'Thiết kế cho tôi roadmap 1 cấp của ' + ctx.serviceName +
                        roadNote], [2,4,roadNote],
                [3,1,'ROAD 2'], [3,2,'Thiết kế cho tôi menu 2 cấp của'],
                [3,3,'Thiết kế cho tôi menu 2 cấp của ' + ctx.serviceName +
                        menuNote], [3,4,menuNote],
                [2,6,'Tiêu đề'], [2,7,'Mục đích'], [2,8,'Mục tiêu học tập'],
                [2,9,'Location'], [3,9,ctx.location],
                [2,10,'Current STEP'], [2,11,'Special Note'], [2,12,'Reference'],
                [17,1,'Giới thiệu'], [18,1,'Khu vực Menu'],
                [18,16,'Interview'], [18,19,'CaseStudy'], [18,22,'Quiz'],
                [18,24,'Video'], [18,29,'Summary'],
                [19,1,'Menu Cấp 1'], [19,2,'Menu Cấp 2'],
                [19,15,'Difficulty'], [19,29,'Summary YAML']
        ].each { List item -> ctx.set(item[0] as int, item[1] as int, item[2] as String) }
        (3..14).each { ctx.set(19, it, 'Câu trả lời') }
        (16..18).each { ctx.set(19, it, 'Interview YAML') }
        (19..21).each { ctx.set(19, it, 'CaseStudy YAML') }
        (22..23).each { ctx.set(19, it, 'Quiz YAML') }
        (24..27).each { ctx.set(19, it, 'Video Script') }
        // O20:O700 uses the same difficulty domain as the canonical metadata.
        def helper = new XSSFDataValidationHelper(ctx.sheet)
        def constraint = helper.createExplicitListConstraint(
                ['BASIC', 'INTERMEDIATE', 'ADVANCED'] as String[])
        def validation = helper.createValidation(constraint,
                new CellRangeAddressList(19, 699, 14, 14))
        validation.setShowErrorBox(true)
        ctx.sheet.addValidationData(validation)
    }
}
