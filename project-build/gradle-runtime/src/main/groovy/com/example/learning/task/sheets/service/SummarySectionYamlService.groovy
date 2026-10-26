package com.example.learning.task.sheets.service

import org.gradle.api.GradleException
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

import java.util.regex.Pattern

/** Reversible STEP 5 chapter AC Markdown <-> structured roadmap YAML. */
class SummarySectionYamlService {
    private static final Pattern STEP = Pattern.compile(
            '(?m)^###\\s+(?:Bài|Lesson)\\s+(\\d+)\\s*[—–-]\\s*(.+?)\\s*$')
    private static final Pattern FLOW = Pattern.compile('(?m)^\\s*↓\\s+(.+?)\\s*$')

    static Map fromMarkdown(String source, List<String> ids) {
        String markdown = source.replace('\r\n', '\n').trim()
        def head = (markdown =~ /(?m)^#\s+(.+)\s*$/)
        def first = (markdown =~ /(?m)^##\s+1\.\s+(.+)\s*$/)
        def second = (markdown =~ /(?m)^##\s+2\.\s+(.+)\s*$/)
        if (!head.find() || head.start() != 0 || !first.find() || !second.find() ||
                first.start() <= head.end() || second.start() <= first.end() ||
                !markdown.substring(head.end(), first.start()).trim().isEmpty()) {
            throw new GradleException('Summary chapter needs # title, ## 1. flow and ## 2. comparison.')
        }
        String flow = markdown.substring(first.end(), second.start()).trim()
        def matches = STEP.matcher(flow)
        List<Map> headings = []
        while (matches.find()) {
            headings.add([start: matches.start(), end: matches.end(),
                          order: Integer.parseInt(matches.group(1)),
                          title: matches.group(2).trim()])
        }
        if (headings.size() != ids.size() || headings.isEmpty() ||
                !flow.substring(0, headings[0].start as int).trim().isEmpty()) {
            throw new GradleException('Summary roadmap step count does not match Knowledge IDs.')
        }
        List<Map> steps = []
        headings.eachWithIndex { Map entry, int i ->
            if (entry.order != i + 1) throw new GradleException('Summary roadmap order mismatch.')
            int end = i + 1 < headings.size() ? headings[i + 1].start as int : flow.length()
            String body = flow.substring(entry.end as int, end).trim()
            def arrow = FLOW.matcher(body)
            String description
            Map transition = null
            if (arrow.find()) {
                description = body.substring(0, arrow.start()).trim()
                String after = body.substring(arrow.end()).trim()
                if (i == headings.size() - 1 || after) {
                    throw new GradleException('Invalid Summary roadmap transition at lesson ' + (i + 1))
                }
                transition = [to: ids[i + 1], description: arrow.group(1).trim()]
            } else {
                description = body
                if (i < headings.size() - 1) {
                    throw new GradleException('Missing Summary roadmap transition at lesson ' + (i + 1))
                }
            }
            if (!description || (transition != null && !transition.description)) {
                throw new GradleException('Empty Summary roadmap text at lesson ' + (i + 1))
            }
            steps.add([id: ids[i], order: entry.order, title: entry.title,
                       description: description, transition: transition])
        }
        Map value = [version: 1, type: 'LEARNING_ROADMAP', title: head.group(1).trim(),
                     roadmapHeading: first.group(1).trim(), roadmap: steps,
                     comparisonMarkdown: markdown.substring(second.start()).trim()]
        validate(value, ids)
        return value
    }

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
            row.tokenize('|').size() != ids.size() + 1
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

    static String toMarkdown(Map value, List<String> ids, String language = 'vi') {
        validate(value, ids)
        List<String> lines = ['# ' + value.title, '', '## 1. ' + value.roadmapHeading, '']
        (value.roadmap as List).each { Map step ->
            lines.add('### ' + (language == 'en' ? 'Lesson' : 'Bài') +
                    ' ' + step.order + ' — ' + step.title)
            lines.add('')
            lines.add((step.description as String).trim())
            lines.add('')
            if (step.transition != null) {
                lines.add('↓ ' + (step.transition.description as String).trim())
                lines.add('')
            }
        }
        lines.add((value.comparisonMarkdown as String).trim())
        return lines.join('\n').trim() + '\n'
    }
}
