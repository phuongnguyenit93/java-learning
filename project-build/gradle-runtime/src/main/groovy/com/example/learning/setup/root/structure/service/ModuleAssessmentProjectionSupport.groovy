package com.example.learning.setup.root.structure.service

import groovy.json.JsonOutput
import org.gradle.api.GradleException
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.constructor.SafeConstructor

/**
 * Read-only projection of STEP 6/7 module-wide assessments into independent
 * group documents and a compact group index. The authored YAML is untouched;
 * knowledge anchorList is deliberately not resolved at this stage.
 */
final class ModuleAssessmentProjectionSupport {
    static final Map<String, Map> KINDS = [
            interview: [root: 'interviewQuestion', items: 'questions'],
            quiz: [root: 'quiz', items: 'questions'],
            casestudy: [root: 'caseStudy', items: 'cases']
    ].asImmutable()

    static List<Map> groups(File source, String kind) {
        Map spec = KINDS[kind]
        if (spec == null) throw new GradleException("Unknown assessment kind: ${kind}")
        Object parsed
        try {
            LoaderOptions options = new LoaderOptions()
            options.setAllowDuplicateKeys(false)
            parsed = new Yaml(new SafeConstructor(options)).load(source.getText('UTF-8'))
        } catch (Exception exception) {
            throw new GradleException("Unable to read module ${kind} YAML: ${source}", exception)
        }
        if (!(parsed instanceof Map) || !(parsed[spec.root] instanceof List) ||
                (parsed as Map).keySet() != [spec.root] as Set) {
            throw new GradleException("Invalid module ${kind} document root: ${source}")
        }
        List input = parsed[spec.root] as List
        if (input.isEmpty()) throw new GradleException("Empty module ${kind} groups: ${source}")
        List<Map> result = []
        input.eachWithIndex { Object value, int index ->
            if (!(value instanceof Map) ||
                    !(value.groupID instanceof Number) || value.groupID.intValue() != index + 1 ||
                    !(value.groupName instanceof String) || !value.groupName.trim() ||
                    !(value[spec.items] instanceof List) || (value[spec.items] as List).isEmpty()) {
                throw new GradleException("Invalid module ${kind} group ${index + 1}: ${source}")
            }
            (value[spec.items] as List).eachWithIndex { Object item, int itemIndex ->
                if (!(item instanceof Map) ||
                        !(item.readmeRelated instanceof Map) ||
                        !(item.readmeRelated.anchorList instanceof List) ||
                        !(item.readmeRelated.anchorList as List).every { it instanceof String }) {
                    throw new GradleException("Invalid ${kind} group ${index + 1} item ${itemIndex + 1}")
                }
                if (kind == 'quiz' && (!(item.answers instanceof List) ||
                        (item.answers as List).size() != 4 ||
                        (item.answers as List)*.id != ['A', 'B', 'C', 'D'] ||
                        !(item.correctAnswerId in ['A', 'B', 'C', 'D']))) {
                    throw new GradleException("Invalid Quiz answers in group ${index + 1}")
                }
            }
            result.add(value as Map)
        }
        return result
    }

    static void addOutputs(Map<String, String> outputs, File source,
                           String routeId, String kind, String language) {
        Map spec = KINDS[kind]
        List<Map> data = groups(source, kind)
        DumperOptions options = new DumperOptions()
        options.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        options.indent = 2
        options.width = 120
        Yaml yaml = new Yaml(options)
        List<Map> entries = []
        int total = 0
        data.each { Map group ->
            int id = (group.groupID as Number).intValue()
            List items = group[spec.items] as List
            String relative = "${routeId}/${kind}/${language}/groups/${id}.yml"
            if (outputs.containsKey(relative)) throw new GradleException("Duplicate Portal output: ${relative}")
            outputs[relative] = yaml.dump([(spec.items): items])
            entries.add([id: "group-${id}".toString(), title: group.groupName,
                         questionCount: items.size(), path: "groups/${id}.yml".toString()])
            total += items.size()
        }
        String indexPath = "${routeId}/${kind}/${language}/index.json"
        if (outputs.containsKey(indexPath)) throw new GradleException("Duplicate Portal output: ${indexPath}")
        outputs[indexPath] = JsonOutput.prettyPrint(JsonOutput.toJson([
                mode: 'groups', chapters: entries, totalQuestions: total
        ])) + '\n'
    }

    private ModuleAssessmentProjectionSupport() {}
}
