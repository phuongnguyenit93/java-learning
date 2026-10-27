package com.example.learning.task.sheets.service

import org.gradle.api.GradleException
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/** Validate authored chapter YAML before the Sheet snapshot is written as-is. */
class SummarySectionYamlService {
    static Map read(String source, List<String> ids) {
        LoaderOptions options = new LoaderOptions()
        options.allowDuplicateKeys = false
        Object value
        try {
            value = new Yaml(new SafeConstructor(options)).load(source)
        } catch (Exception error) {
            throw new GradleException('Invalid section.yml YAML: ' + error.message, error)
        }
        if (!(value instanceof Map)) throw new GradleException('section.yml must be a map.')
        validate(value as Map, ids)
        return value as Map
    }

    static void validate(Map value, List<String> ids) {
        if (value.keySet() != (['version','type','title','roadmapHeading','roadmap','comparisonMarkdown'] as Set) ||
                value.version != 1 || value.type != 'LEARNING_ROADMAP' ||
                !(value.title instanceof String) || !value.title.trim() ||
                !(value.roadmapHeading instanceof String) || !value.roadmapHeading.trim() ||
                !(value.comparisonMarkdown instanceof String) ||
                !(value.comparisonMarkdown ==~ /(?s)^##\s+2\.\s+.+/) ||
                !value.comparisonMarkdown.contains('|') ||
                !(value.roadmap instanceof List) || value.roadmap.size() != ids.size()) {
            throw new GradleException('Invalid section.yml root schema or lesson count.')
        }
        List<String> tableRows = (value.comparisonMarkdown as String).readLines()
                .collect { it.trim() }.findAll { it.startsWith('|') && it.endsWith('|') }
        if (tableRows.size() < 6 || tableRows.any { String row ->
            comparisonColumnCount(row) != ids.size() + 1
        } || !(tableRows[0].toLowerCase(Locale.ROOT).contains('bài 1') ||
                tableRows[0].toLowerCase(Locale.ROOT).contains('lesson 1')) ||
                !tableRows[1].replaceAll(/[|:\-\s]/, '').isEmpty()) {
            throw new GradleException('Invalid chapter comparison table columns/shape.')
        }
        (value.roadmap as List).eachWithIndex { Object entry, int i ->
            if (!(entry instanceof Map)) throw new GradleException('Invalid roadmap lesson.')
            Map item = entry as Map
            if (item.keySet() != (['id','order','title','description','transition'] as Set) ||
                    item.id != ids[i] || item.order != i + 1 ||
                    !(item.title instanceof String) || !item.title.trim() ||
                    !(item.description instanceof String) || !item.description.trim()) {
                throw new GradleException('Invalid roadmap lesson at index ' + i)
            }
            // Roadmap cards must contain only the lesson description. The UI
            // already renders transitions and the comparison table separately.
            if ((item.description as String) =~ /(?m)^\s*\*\*Mối liên hệ:\*\*/ ||
                    (item.description as String).contains('[↓ Xem bảng đối chiếu các bài học]')) {
                throw new GradleException('Redundant chapter conclusion/link in roadmap lesson at index ' + i)
            }
            if (i == ids.size() - 1) {
                if (item.transition != null) throw new GradleException('Last roadmap step has a transition.')
            } else if (!(item.transition instanceof Map) ||
                    (item.transition as Map).keySet() != (['to', 'description'] as Set) ||
                    item.transition.to != ids[i + 1] ||
                    !(item.transition.description instanceof String) ||
                    !item.transition.description.trim()) {
                throw new GradleException('Invalid roadmap transition at index ' + i)
            }
        }
    }

    /** GFM escaped pipes are cell content, not column separators. */
    private static int comparisonColumnCount(String row) {
        int count = 1
        int backslashes = 0
        for (int index = 1; index < row.length() - 1; index++) {
            char character = row.charAt(index)
            if (character == ('|' as char) && backslashes % 2 == 0) count++
            backslashes = character == ('\\' as char) ? backslashes + 1 : 0
        }
        return count
    }

}
