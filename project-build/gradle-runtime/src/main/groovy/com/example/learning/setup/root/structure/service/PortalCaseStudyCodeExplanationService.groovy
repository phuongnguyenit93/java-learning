package com.example.learning.setup.root.structure.service

import org.gradle.api.GradleException
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/** Read-only module/language Swagger enrichment for Case Study portal groups. */
final class PortalCaseStudyCodeExplanationService {
    private static final List<String> CASE_FIELDS = [
            'id', 'title', 'description', 'detailDescription', 'input',
            'status', 'response', 'observations'
    ].asImmutable()
    private static final Set<String> EXECUTION_PLACEHOLDERS = ([
            'Mô tả cách hoạt động của API này. Thay đoạn này bằng HTML code.',
            'Description of how this API works. Replace this with HTML code.'
    ] as Set<String>).asImmutable()

    static Map<Integer, Map<Integer, Map>> load(File module, String language, List<Map> groups) {
        if (!(language ==~ /[a-z]{2,8}(-[a-z]{2,8})?/)) return [:]
        File swagger = new File(module, 'src/main/resources/swagger/' + language)
        Map executions = readOptional(new File(swagger, 'api-execution.yml'))
        Map cases = readOptional(new File(swagger, 'api-cases.yml'))
        Map descriptions = readOptional(new File(swagger, 'api-descriptions.yml'))
        if (executions.isEmpty() && cases.isEmpty()) return [:]

        Map<Integer, Map<Integer, Map>> result = new LinkedHashMap<>()
        groups.each { Map group ->
            int groupId = (group.groupID as Number).intValue()
            String controller = 'Group' + groupId + 'Controller'
            Map executionMethods = asMap(executions[controller])
            Map caseMethods = asMap(cases[controller])
            Map descriptionMethods = asMap(descriptions[controller])
            if (executionMethods.isEmpty() && caseMethods.isEmpty()) return

            Map<Integer, Map> groupExplanations = new LinkedHashMap<>()
            (group.cases as List).eachWithIndex { Object unused, int offset ->
                int index = offset + 1
                String methodName = 'executeCase' + index
                String route = '/case-studies/group/' + groupId + '/case/' + index + '/execute'
                Set<String> signatures = new LinkedHashSet<>()
                [executionMethods, caseMethods, descriptionMethods].each { Map entries ->
                    entries.keySet().each { Object key ->
                        if (key instanceof String && exactSignature(key as String, methodName)) {
                            signatures.add(key as String)
                        }
                    }
                }
                // Ambiguous overloads cannot be safely attached to an index.
                if (signatures.size() != 1) return
                String signature = signatures.first()
                // Swagger descriptions are the authoritative Java mapping scan.
                // Without an exact verified route, neither execution nor cases
                // can be attributed to this Case Study index.
                Map descriptor = descriptionMethods[signature] instanceof Map ?
                        descriptionMethods[signature] as Map : null
                if (descriptor == null ||
                        !matchesRoute(descriptor, methodName, route) ||
                        descriptor.usage != true) return
                Map execution = asMap(executionMethods[signature])
                Map caseEntry = asMap(caseMethods[signature])
                if (!caseEntry.isEmpty() && !matchesRoute(caseEntry, methodName, route)) return

                Map explanation = new LinkedHashMap()
                if (execution.usage != false && meaningfulExecution(execution.execution)) {
                    explanation.howItWorksHtml = (execution.execution as String).trim()
                }
                if (caseEntry.usage != false && caseEntry.cases instanceof List) {
                    List<Map> situations = []
                    Set<String> ids = new LinkedHashSet<>()
                    (caseEntry.cases as List).each { Object item ->
                        Map selected = selectSituation(item)
                        if (selected && ids.add(selected.id as String)) situations.add(selected)
                    }
                    if (!situations.isEmpty()) explanation.situations = situations
                }
                if (!explanation.isEmpty()) groupExplanations[index] = explanation
            }
            if (!groupExplanations.isEmpty()) result[groupId] = groupExplanations
        }
        return result
    }

    private static boolean exactSignature(String signature, String methodName) {
        signature ==~ (java.util.regex.Pattern.quote(methodName) + /\([^()]*\)/)
    }

    private static boolean matchesRoute(Map data, String methodName, String route) {
        data.methodName == methodName &&
                data.mappingPaths instanceof List &&
                (data.mappingPaths as List).size() == 1 &&
                data.mappingPaths[0] == route
    }

    private static boolean meaningfulExecution(Object value) {
        if (!(value instanceof String)) return false
        String html = (value as String).trim()
        if (!html || EXECUTION_PLACEHOLDERS.any { html.contains(it) }) return false
        String text = html.replaceAll(/(?s)<[^>]*>/, '').trim()
        text && !(text ==~ /(?i)(todo[:\s].*|chưa có.*|not provided.*|to be described.*)/)
    }

    private static Map selectSituation(Object raw) {
        if (!(raw instanceof Map)) return [:]
        Map original = raw as Map
        if (!meaningfulString(original.id) || !meaningfulString(original.title) ||
                isTodo(original.title)) return [:]
        boolean context = ['description', 'detailDescription'].any {
            meaningfulString(original[it]) && !isTodo(original[it])
        }
        boolean input = original.input instanceof Map &&
                (original.input as Map).any { Object key, Object value ->
                    value instanceof Map ? !value.isEmpty() : value != null && value != ''
                }
        boolean observations = original.observations instanceof List &&
                (original.observations as List).any { it instanceof Map && !it.isEmpty() }
        if (!context && !input && !observations) return [:]

        Map selected = new LinkedHashMap()
        CASE_FIELDS.each { String key ->
            Object value = original[key]
            if (value == null) return
            if (key in ['id', 'title', 'description', 'detailDescription']) {
                if (meaningfulString(value) && !isTodo(value)) selected[key] = value.trim()
            } else if (key == 'input') {
                if (value instanceof Map) selected[key] = safeData(value)
            } else if (key == 'observations') {
                List valid = (value instanceof List ? value : []).findAll { Object entry ->
                    entry instanceof Map && entry.expect instanceof Map &&
                            meaningfulString(entry.type) &&
                            ['keyword', 'results', 'conclusion'].every {
                                meaningfulString(entry.expect[it])
                            }
                }.collect { Map entry ->
                    Map observation = new LinkedHashMap()
                    ['type', 'source', 'program'].each { String field ->
                        if (meaningfulString(entry[field])) observation[field] = entry[field].trim()
                    }
                    observation.expect = new LinkedHashMap()
                    ['keyword', 'results', 'conclusion'].each { String field ->
                        observation.expect[field] = entry.expect[field].trim()
                    }
                    observation
                }
                if (!valid.isEmpty()) selected[key] = valid
            } else {
                selected[key] = safeData(value)
            }
        }
        return selected
    }

    private static boolean meaningfulString(Object value) {
        value instanceof String && !(value as String).trim().isEmpty()
    }

    private static boolean isTodo(Object value) {
        value instanceof String &&
                ((value as String).trim() ==~ /(?is)\s*(<p>)?\s*TODO\s*:.*/)
    }

    private static Object safeData(Object value) {
        if (value instanceof Map) {
            Map data = new LinkedHashMap()
            (value as Map).each { Object key, Object entry ->
                if (key instanceof String) data[key] = safeData(entry)
            }
            return data
        }
        if (value instanceof List) return (value as List).collect { safeData(it) }
        return value instanceof String || value instanceof Number ||
                value instanceof Boolean ? value : null
    }

    private static Map asMap(Object value) {
        value instanceof Map ? value as Map : [:]
    }

    private static Map readOptional(File file) {
        if (!file.isFile()) return [:]
        try {
            LoaderOptions options = new LoaderOptions()
            options.allowDuplicateKeys = false
            Object result = new Yaml(new SafeConstructor(options)).load(file.getText('UTF-8'))
            if (!(result instanceof Map)) throw new GradleException('Invalid Swagger YAML: ' + file)
            return result as Map
        } catch (Exception error) {
            throw new GradleException('Unable to parse Swagger YAML: ' + file, error)
        }
    }

    private PortalCaseStudyCodeExplanationService() {}
}
