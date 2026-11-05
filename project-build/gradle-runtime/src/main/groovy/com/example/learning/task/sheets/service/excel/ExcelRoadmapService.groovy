package com.example.learning.task.sheets.service.excel

import org.gradle.api.GradleException

/** Phase 3: source is authored content/roadmap/<lang>/roadmap.yml. */
class ExcelRoadmapService {
    void fill(ModuleExcelContext ctx) {
        Map doc = ctx.yaml(ctx.read('content/roadmap/' + ctx.language + '/roadmap.yml'),
                'Roadmap ' + ctx.language)
        if (!(doc.roadmap instanceof List)) throw new GradleException('Missing Roadmap list.')
        (doc.roadmap as List).eachWithIndex { Object item, int i ->
            if (!(item instanceof Map) || !(item.title instanceof String) ||
                    !(item.purpose instanceof String) || !(item.objectives instanceof List) ||
                    (item.objectives as List).any { !(it instanceof String) }) {
                throw new GradleException('Invalid Roadmap item ' + (i + 1))
            }
            ctx.set(3 + i, 6, item.title as String)
            ctx.set(3 + i, 7, item.purpose as String)
            ctx.set(3 + i, 8, (item.objectives as List).join('\n'))
        }
    }
}
