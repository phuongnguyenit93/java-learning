package com.example.learning.task.sheets.service

import org.gradle.api.GradleException
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

/** Structural/protected-field guard. Prime still owns semantic translation QA. */
class ModuleTranslationValidationService {
    private static final Pattern CODE = Pattern.compile(
            '(?ms)^([ \\t]*)(\\x60{3,}|\\x7E{3,})[^\\r\\n]*\\R.*?^\\1\\2[ \\t]*(?=\\R|$)')
    private static final Pattern INLINE_CODE = Pattern.compile('\\x60+[^\\x60\\r\\n]+\\x60+')
    private static final Pattern HTML_TAG = Pattern.compile('<[^>]+>')
    private static final Pattern LINKS = Pattern.compile('!?\\[[^\\]\\r\\n]*\\]\\(([^)\\r\\n]+)\\)')
    private static final Pattern ANCHORS = Pattern.compile(
            '<a\\s+id\\s*=\\s*["\\x27]([^"\\x27]+)["\\x27]', Pattern.CASE_INSENSITIVE)
    private static final Pattern URL = Pattern.compile('https?://[^\\s<>")\\x27]+')
    private static final Pattern PLACEHOLDER = Pattern.compile(
            '(?:\\$\\{[^}]+\\}|\\{\\{[^}]+\\}\\}|%[a-zA-Z0-9.\\-]*[sdif])')

    static void compare(String type, byte[] vi, byte[] en) {
        String source = decode(vi)
        String target = decode(en)
        if (type in ['README', 'INTRODUCTION']) {
            markdown(source, target)
        } else if (type ==~ /QUIZ|INTERVIEW|SUMMARY_QUESTIONS|PRACTICE|ROADMAP|VIDEO|SUMMARY_SECTION|SUMMARY_LESSONS|SWAGGER_.+/) {
            yaml(type, source, target)
        } else {
            throw new GradleException('Unknown translation type: ' + type)
        }
    }

    private static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder().decode(
                    java.nio.ByteBuffer.wrap(bytes)).toString()
        } catch (Exception error) {
            throw new GradleException('Translation is not valid UTF-8.', error)
        }
    }

    private static void markdown(String vi, String en) {
        identical('Markdown anchor IDs', groups(ANCHORS, vi, 1), groups(ANCHORS, en, 1))
        identical('Markdown fenced code', matches(CODE, vi), matches(CODE, en))
        identical('Markdown inline code', matches(INLINE_CODE, vi), matches(INLINE_CODE, en))
        identical('Markdown link destinations', groups(LINKS, vi, 1), groups(LINKS, en, 1))
        identical('Markdown HTML tags/attributes', matches(HTML_TAG, vi),
                matches(HTML_TAG, en))
        identical('Markdown heading hierarchy', headingLevels(vi), headingLevels(en))
        identical('Markdown list markers', listMarkers(vi), listMarkers(en))
        identical('Markdown table shapes', tableColumns(vi), tableColumns(en))
        lockedTokens(vi, en, 'Markdown')
        if (en.trim().isEmpty() && !vi.trim().isEmpty()) {
            throw new GradleException('Translated Markdown is empty.')
        }
    }

    private static List<Integer> headingLevels(String value) {
        String clean = CODE.matcher(value).replaceAll('')
        return clean.readLines().findResults { String line ->
            def m = (line =~ /^(#{1,6})\s+\S.*/)
            m.matches() ? m.group(1).length() : null
        }
    }

    private static List<String> listMarkers(String value) {
        String clean = CODE.matcher(value).replaceAll('')
        return clean.readLines().findResults { String line ->
            def m = (line =~ /^\s*([-*+]|\d+\.)\s+\S.*/)
            if (!m.matches()) return null
            m.group(1).matches('\\d+\\.') ? 'ORDERED' : 'BULLET'
        }
    }

    private static List<Integer> tableColumns(String value) {
        String clean = CODE.matcher(value).replaceAll('')
        return clean.readLines().findResults { String line ->
            String s = line.trim()
            s.startsWith('|') && s.endsWith('|') ? s.count('|') - 1 : null
        }
    }

    private static void yaml(String type, String vi, String en) {
        Map original = parse(vi, 'VI')
        Map translated = parse(en, 'EN')
        identical('YAML schema comment prefix', leadingComments(vi), leadingComments(en))
        compareNode(type, original, translated, '')
    }

    private static Map parse(String text, String label) {
        try {
            LoaderOptions options = new LoaderOptions()
            options.allowDuplicateKeys = false
            Object root = new Yaml(new SafeConstructor(options)).load(text)
            if (!(root instanceof Map)) throw new GradleException(label + ' YAML must be a mapping.')
            return root as Map
        } catch (GradleException error) {
            throw error
        } catch (Exception error) {
            throw new GradleException(label + ' YAML parse failed: ' + error.message, error)
        }
    }

    private static List<String> leadingComments(String text) {
        List<String> comments = []
        for (String line : text.replace('\r\n', '\n').readLines()) {
            if (line.trim().isEmpty() || line.trim().startsWith('#')) {
                comments.add(line)
            } else break
        }
        return comments
    }

    private static void compareNode(String type, Object a, Object b, String path) {
        if (a instanceof Map) {
            if (!(b instanceof Map)) fail(path)
            Map before = a as Map
            Map after = b as Map
            if (before.keySet().toList() != after.keySet().toList()) {
                throw new GradleException('YAML keys/order changed: ' + path)
            }
            before.each { Object key, Object value ->
                compareNode(type, value, after[key], path ? path + '.' + key : key.toString())
            }
            return
        }
        if (a instanceof List) {
            if (!(b instanceof List) || a.size() != b.size()) {
                throw new GradleException('YAML list size/order changed: ' + path)
            }
            a.eachWithIndex { Object value, int index ->
                compareNode(type, value, (b as List)[index], path + '[' + index + ']')
            }
            return
        }
        if (editable(type, path)) {
            if (!(a instanceof String) || !(b instanceof String)) {
                throw new GradleException('Translatable scalar type changed: ' + path)
            }
            if ((a as String).trim() && !(b as String).trim()) {
                throw new GradleException('Translated scalar was erased: ' + path)
            }
            if ((type == 'SUMMARY_LESSONS' && path.endsWith('.summary')) ||
                    (type == 'SUMMARY_SECTION' && (path == 'comparisonMarkdown' ||
                            path ==~ /roadmap\[\d+\]\.description/ ||
                            path ==~ /roadmap\[\d+\]\.transition\.description/))) {
                markdown(a as String, b as String)
            } else {
                lockedTokens(a as String, b as String, path)
            }
        } else if (a != b || a?.getClass() != b?.getClass()) {
            throw new GradleException('Protected translation field changed: ' + path)
        }
    }

    static boolean editable(String type, String path) {
        switch (type) {
            case 'SUMMARY_LESSONS':
                return path ==~ /summaries\[\d+\]\.(title|summary)/
            case 'SUMMARY_SECTION':
                return path in ['title', 'roadmapHeading', 'comparisonMarkdown'] ||
                        path ==~ /roadmap\[\d+\]\.(title|description|transition\.description)/
            case 'QUIZ':
                return path ==~ /questions\[\d+\]\.(question|answers\[\d+\]\.(answer|explanation))/
            case 'INTERVIEW':
                return path ==~ /questions\[\d+\]\.(question|answer)/
            case 'SUMMARY_QUESTIONS':
                return path ==~ /questions\[\d+\]\.(question|answer)/
            case 'PRACTICE':
                return path ==~ /scenarios\[\d+\]\.(title|context|problem|analysis\.(possibleCauses|application|tradeoffs)|exercise\.(requirements|deliverables|acceptanceCriteria|hints)\[\d+\]|exercise\.referenceSolution)/
            case 'ROADMAP':
                return path ==~ /roadmap\[\d+\]\.(title|purpose|objectives\[\d+\]|relatedModules\[\d+\]\.(label|note))/
            case 'VIDEO':
                return path == 'title' ||
                        path ==~ /sections\[\d+\]\.title/ ||
                        path ==~ /sections\[\d+\]\.items\[\d+\]\.(title|visual|script|purpose)/
            case 'SWAGGER_CONTROLLER_DESCRIPTION':
                return path ==~ /[^.]+\.description/
            case 'SWAGGER_API_DESCRIPTIONS':
                return path ==~ /[^.]+\..+\.(summary|description)/
            case 'SWAGGER_API_EXECUTION':
                return path ==~ /[^.]+\..+\.execution/
            case 'SWAGGER_API_CASES':
                return path ==~ /[^.]+\..+\.cases\[\d+\]\.(title|description|detailDescription)/
            case 'SWAGGER_API_PARAMS':
                return path ==~ /[^.]+\.(summary|description)/
            default:
                return false
        }
    }

    private static void lockedTokens(String original, String translated, String place) {
        identical(place + ' inline code', matches(INLINE_CODE, original),
                matches(INLINE_CODE, translated))
        identical(place + ' code fences', matches(CODE, original), matches(CODE, translated))
        identical(place + ' HTML tags', matches(HTML_TAG, original),
                matches(HTML_TAG, translated))
        identical(place + ' URLs', matches(URL, original), matches(URL, translated))
        identical(place + ' placeholders', matches(PLACEHOLDER, original),
                matches(PLACEHOLDER, translated))
    }

    private static List<String> matches(Pattern pattern, String text) {
        def matcher = pattern.matcher(text)
        List<String> result = []
        while (matcher.find()) result.add(matcher.group())
        result
    }

    private static List<String> groups(Pattern pattern, String text, int group) {
        def matcher = pattern.matcher(text)
        List<String> result = []
        while (matcher.find()) result.add(matcher.group(group))
        result
    }

    private static void identical(String label, Object a, Object b) {
        if (a != b) throw new GradleException('Protected ' + label + ' changed.')
    }

    private static void fail(String path) {
        throw new GradleException('YAML node type changed: ' + path)
    }
}
