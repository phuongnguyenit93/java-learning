package com.example.learning.task.sheets.service.excel

import com.example.learning.task.sheets.service.MenuLessonIdService
import com.example.learning.task.sheets.service.ModuleContentExcelSyncService
import com.example.learning.task.sheets.service.ReadmeSheetImportService
import org.gradle.api.GradleException

import java.util.regex.Pattern

/** Phase 6: LIST order + Knowledge anchors + per-chapter Difficulty metadata. */
class ExcelKnowledgeService {
    void fill(ModuleExcelContext ctx) {
        String menuPath = 'content/readme/docs/' + ctx.language + '/menu/'
        String list = ctx.read('content/readme/docs/' + ctx.language + '/LIST.md')
        def matcher = Pattern.compile('menu/(\\d+\\.[^/\\)]+)/knowledge/([^/\\)]+\\.md)')
                .matcher(list)
        List<Map> entries = []
        while (matcher.find()) {
            entries.add([chapter: matcher.group(1), filename: matcher.group(2)])
        }
        if (!entries || entries.collect { it.chapter }.toSet().size() != entries.size()) {
            throw new GradleException('Missing/duplicate Knowledge chapters in LIST.md.')
        }
        Set<String> ids = [] as Set
        Set<String> bases = [] as Set
        int row = 20
        entries.eachWithIndex { Map entry, int i ->
            String name = entry.chapter as String
            if (!name.startsWith((i + 1).toString() + '.')) {
                throw new GradleException('Knowledge LIST chapter order differs: ' + name)
            }
            String stem = (entry.filename as String).replaceFirst(/(?i)\.md$/, '')
            String base = ReadmeSheetImportService.slugFromStem(stem)
            if (!bases.add(base)) throw new GradleException('Duplicate chapter base ID: ' + base)
            String source = ctx.read(menuPath + name + '/knowledge/' + entry.filename)
            Map parsed = ModuleContentExcelSyncService.parseMarkdown(source)
            Map metadata = ctx.yaml(ctx.read(menuPath + name +
                    '/metadata/knowledge-metadata.yml'), 'metadata ' + name)
            Map sections = metadata.sections instanceof Map ?
                    metadata.sections as Map : metadata
            List<Map> lessons = parsed.sections as List<Map>
            if (!lessons || sections.size() != lessons.size()) {
                throw new GradleException('Knowledge/metadata count mismatch: ' + name)
            }
            int parent = row++
            ctx.set(parent, 1, parsed.title as String)
            ctx.set(parent, 2, entry.filename as String)
            ctx.set(parent, 3, base)
            (1..3).each { int col ->
                ctx.sheet.getRow(parent - 1).getCell(col - 1).cellStyle = ctx.headerStyle
            }
            List<Map> children = []
            lessons.each { Map lesson ->
                String id = lesson.id as String
                if (!MenuLessonIdService.isLessonId(id) ||
                        !id.startsWith(base + '-') || !ids.add(id)) {
                    throw new GradleException('Invalid/duplicate Knowledge ID: ' + id)
                }
                Map detail = sections[id] as Map
                if (!(detail instanceof Map) ||
                        !(detail.difficulty in ['BASIC','INTERMEDIATE','ADVANCED'])) {
                    throw new GradleException('Invalid Difficulty for ' + id)
                }
                ctx.set(row, 1, id)
                ctx.set(row, 2, lesson.title as String)
                ctx.fragments(row, 3, 1, 12, lesson.body as String)
                ctx.set(row, 15, detail.difficulty as String)
                children.add([row: row, id: id, title: lesson.title])
                row++
            }
            ctx.chapters.add([row: parent, name: name, filename: entry.filename,
                              base: base, children: children])
        }
        if (row > 1001) throw new GradleException('Knowledge exceeds worksheet 1000 rows.')
    }
}
