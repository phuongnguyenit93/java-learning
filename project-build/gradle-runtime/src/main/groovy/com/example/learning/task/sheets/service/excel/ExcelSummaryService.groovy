package com.example.learning.task.sheets.service.excel

import com.example.learning.task.sheets.service.SummarySectionYamlService
import org.gradle.api.GradleException

/** Phase 7: full authored section.yml at AC parent and one summary per lesson. */
class ExcelSummaryService {
    void fill(ModuleExcelContext ctx) {
        String base = 'content/readme/docs/' + ctx.language + '/menu/'
        ctx.chapters.each { Map chapter ->
            String path = base + chapter.name + '/summary/'
            String section = ctx.read(path + 'section.yml')
            Map summaries = ctx.yaml(ctx.read(path + 'summaries.yml'), path)
            List<Map> children = chapter.children as List<Map>
            List<String> ids = children.collect { it.id as String }
            SummarySectionYamlService.read(section, ids)
            if (!(summaries.summaries instanceof List) ||
                    summaries.summaries.size() != children.size()) {
                throw new GradleException('Summary lesson count mismatch: ' + path)
            }
            // Preserve the original full document, including links and authored formatting.
            ctx.set(chapter.row as int, 29, section)
            children.eachWithIndex { Map child, int i ->
                Object raw = (summaries.summaries as List)[i]
                if (!(raw instanceof Map) || raw.id != child.id ||
                        !(raw.summary instanceof String) || !raw.summary.trim() ||
                        !(raw.title instanceof String)) {
                    throw new GradleException('Invalid Summary ' + child.id)
                }
                ctx.set(child.row as int, 29, raw.summary as String)
            }
        }
    }
}
