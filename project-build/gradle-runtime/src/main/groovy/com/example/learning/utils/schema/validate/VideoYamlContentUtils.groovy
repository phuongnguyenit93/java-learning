package com.example.learning.utils.schema.validate

import org.gradle.api.GradleException
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/**
 * Shared validation of authored Video Script YAML. The Sheet importer and
 * Portal projector must enforce the same content rules before writing output.
 * Publication-only fields (moduleId, language, categoryId, sourcePath,
 * section order) are derived by the projector rather than authored.
 */
class VideoYamlContentUtils {
    static final String FILENAME = 'video-script.yml'
    private static final String CLOCK = /([0-9]{2,}):([0-9]{2})[–-]([0-9]{2,}):([0-9]{2})/

    static Map parse(String source, String expectedTitle = null,
                     List<String> expectedSections = null) {
        Object parsed
        try {
            LoaderOptions options = new LoaderOptions()
            options.setAllowDuplicateKeys(false)
            parsed = new Yaml(new SafeConstructor(options)).load(source)
        }
        catch (Exception error) {
            throw new GradleException('Invalid Video Script YAML: ' + error.message, error)
        }
        if (!(parsed instanceof Map)) {
            throw new GradleException('Video Script YAML must be a mapping.')
        }
        Map doc = parsed as Map
        // Structural rules are owned by the source-authoring contract. This
        // is deliberately NOT the published Portal video-script schema.
        ContractSchemaValidateUtils.validateData('videoAuthoring', doc)
        String title = requiredText(doc.title, 'Video title')
        if (expectedTitle != null && title != expectedTitle) {
            throw new GradleException("Video title '${title}' does not match Menu Cấp 1 '${expectedTitle}'.")
        }
        if (!(doc.video instanceof Map)) {
            throw new GradleException('Video publication metadata must be a mapping.')
        }
        if (!(doc.video.url instanceof String)) {
            throw new GradleException('Video video.url must be a string (empty until published).')
        }
        if (!(doc.sections instanceof List) || (doc.sections as List).isEmpty()) {
            throw new GradleException('Video requires at least one section.')
        }
        List<String> sectionNames = []
        List<Map> sections = []
        int nextTime = 0
        int scenes = 0
        int transitions = 0
        (doc.sections as List).eachWithIndex { Object rawSection, int sectionIndex ->
            if (!(rawSection instanceof Map)) {
                throw new GradleException("Video section ${sectionIndex + 1} must be a mapping.")
            }
            Map section = rawSection as Map
            String heading = requiredText(section.title, "Section ${sectionIndex + 1} title")
            sectionNames.add(heading)
            if (!(section.items instanceof List) || (section.items as List).isEmpty()) {
                throw new GradleException("Video section '${heading}' needs Scenes.")
            }
            List<Map> items = []
            (section.items as List).eachWithIndex { Object rawItem, int itemIndex ->
                if (!(rawItem instanceof Map)) {
                    throw new GradleException("Video item ${itemIndex + 1} in '${heading}' must be a mapping.")
                }
                Map item = rawItem as Map
                String type = requiredText(item.type, 'Video item type')
                if (!(type in ['SCENE', 'TRANSITION'])) {
                    throw new GradleException("Invalid Video item type: ${type}")
                }
                if (item.containsKey('title') && !(item.title instanceof String)) {
                    throw new GradleException('Video item title must be a string.')
                }
                String time = requiredText(item.time, 'Video item time')
                def match = time =~ /\A${CLOCK}\z/
                if (!match.matches()) {
                    throw new GradleException("Invalid Video time: ${time}")
                }
                int startMinute = Integer.parseInt(match.group(2))
                int endMinute = Integer.parseInt(match.group(4))
                int start = Integer.parseInt(match.group(1)) * 60 + startMinute
                int end = Integer.parseInt(match.group(3)) * 60 + endMinute
                if (startMinute > 59 || endMinute > 59 || start != nextTime || end <= start) {
                    throw new GradleException("Noncontiguous Video time: ${time}")
                }
                nextTime = end
                Map validated = [
                        type   : type,
                        time   : time,
                        visual : requiredText(item.visual, 'Video visual'),
                        script : requiredText(item.script, 'Video script'),
                        purpose: requiredText(item.purpose, 'Video purpose')
                ]
                if (item.containsKey('title') && !item.title.isBlank()) {
                    validated.title = item.title
                }
                if (type == 'SCENE') scenes++
                else transitions++
                items.add(validated)
            }
            if (!items.any { Map item -> item.type == 'SCENE' }) {
                throw new GradleException("Video section '${heading}' lacks a Scene.")
            }
            if (sectionIndex > 0 && items.first().type != 'TRANSITION') {
                throw new GradleException("Video section '${heading}' must start with a Transition.")
            }
            sections.add([order: sectionIndex + 1, title: heading, items: items])
        }
        if (expectedSections != null && sectionNames != expectedSections) {
            throw new GradleException('Video sections do not exactly match Menu Cấp 2 titles/order.')
        }
        return [
                title: title,
                video: [url: doc.video.url],
                sections: sections,
                scenes: scenes,
                transitions: transitions
        ]
    }

    static String categoryId(File yamlFile) {
        if (yamlFile.name != FILENAME) {
            throw new GradleException("Unexpected Video file: ${yamlFile}")
        }
        if (yamlFile.parentFile.name != 'video') {
            throw new GradleException("Video Script must belong to chapter/video/: ${yamlFile}")
        }
        String parentName = yamlFile.parentFile.parentFile.name
        def match = parentName =~ /\A[1-9][0-9]*\.([A-Za-z][A-Za-z0-9]*)\z/
        if (!match.matches()) {
            throw new GradleException("Video directory must match the Knowledge chapter path: ${parentName}")
        }
        return match.group(1)
    }

    private static String requiredText(Object value, String label) {
        if (!(value instanceof String) || (value as String).isBlank()) {
            throw new GradleException("${label} must be a nonblank string.")
        }
        return value as String
    }

}
