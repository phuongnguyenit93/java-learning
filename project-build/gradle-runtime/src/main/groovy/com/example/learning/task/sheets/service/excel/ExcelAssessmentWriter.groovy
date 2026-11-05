package com.example.learning.task.sheets.service.excel

import org.gradle.api.GradleException
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml

/** Pure inverse of STEP 6/7: move answer/code into the Phase 4 group blocks. */
class ExcelAssessmentWriter {
    static String render(Map doc) {
        DumperOptions options = new DumperOptions()
        options.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        options.allowUnicode = true
        options.indent = 2
        options.width = 120
        options.splitLines = false
        return new Yaml(options).dump(doc)
    }

    void fill(ModuleExcelContext ctx, String kind, String root, String listKey,
              int mainCol, int mainWidth, String supplement, String indexField,
              String field) {
        String path = 'content/' + kind + '/' + ctx.language + '/' + kind + '.yml'
        Map original = ctx.yaml(ctx.read(path), path)
        if (original.keySet() != ([root] as Set) ||
                !(original[root] instanceof List) || (original[root] as List).isEmpty()) {
            throw new GradleException('Invalid module-wide ' + kind + ' groups.')
        }
        Set<String> knowledgeIds = ctx.chapters.collectMany { Map ch ->
            (ch.children as List<Map>).collect { it.id as String }
        } as Set<String>
        List<Map> mainGroups = []
        (original[root] as List).eachWithIndex { Object raw, int g ->
            if (!(raw instanceof Map)) throw new GradleException('Invalid assessment group.')
            Map group = raw as Map
            if (group.groupID != g + 1 || !(group.groupName instanceof String) ||
                    !(group[listKey] instanceof List) || (group[listKey] as List).isEmpty()) {
                throw new GradleException('Invalid ' + kind + ' group ' + (g + 1))
            }
            List<Map> items = []
            List<Map> details = []
            (group[listKey] as List).eachWithIndex { Object rawItem, int index ->
                if (!(rawItem instanceof Map)) throw new GradleException('Invalid assessment item.')
                Map item = rawItem as Map
                if (!(item.readmeRelated instanceof Map) ||
                        !(item.readmeRelated.anchorList instanceof List) ||
                        (item.readmeRelated.anchorList as List).any {
                            !(it instanceof String) || !knowledgeIds.contains(it)
                        } || (item.readmeRelated.anchorList as List).toSet().size() !=
                        (item.readmeRelated.anchorList as List).size()) {
                    throw new GradleException('Invalid ' + kind + ' Knowledge links.')
                }
                Map plain = new LinkedHashMap(item)
                if (field) {
                    Object value = plain.remove(field)
                    if (!(value instanceof String) || !value.trim()) {
                        throw new GradleException('Missing ' + kind + ' ' + field +
                                ' in group ' + (g + 1))
                    }
                    details.add([(indexField): index + 1, (field): value])
                }
                items.add(plain)
            }
            mainGroups.add([groupID: group.groupID, groupName: group.groupName,
                            (listKey): items])
            if (field) {
                String phase4 = render([groupID: group.groupID, (supplement): details])
                int row = 27 + 3 * g
                ctx.fragments(row, mainCol, 3, 3, phase4)
            }
        }
        ctx.fragments(20, mainCol, 5, mainWidth, render([(root): mainGroups]))
        ctx.assessment[kind] = original
    }
}
