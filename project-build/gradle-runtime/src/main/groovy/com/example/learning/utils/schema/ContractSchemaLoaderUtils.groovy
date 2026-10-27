package com.example.learning.utils.schema

import groovy.json.JsonSlurper
import org.gradle.api.GradleException

/** Reads pinned contracts from packaged resources, never from a cwd-relative path. */
final class ContractSchemaLoaderUtils {
    static final Map<String, String> NAMES = [
            quiz: 'quiz', interview: 'interview', summaryQuestions: 'summary-questions', roadmap: 'roadmap',
            reference: 'reference', videoAuthoring: 'video-authoring',
            videoScript: 'video-script', videoIndex: 'video-index',
            apiDocs: 'api-docs', knowledge: 'knowledge',
            knowledgeSearch: 'knowledge-search', moduleCatalog: 'module-catalog',
            manifest: 'manifest', common: 'common'
    ].asImmutable()

    private ContractSchemaLoaderUtils() {}

    static String resourcePath(String name) {
        String base = NAMES[name]
        if (base == null) throw new GradleException("Unknown contract schema '${name}'")
        return "learning-contract/schemas/${base}.schema.json"
    }

    static Map read(String name) {
        String path = resourcePath(name)
        InputStream source = ContractSchemaLoaderUtils.class.classLoader.getResourceAsStream(path)
        if (source == null) throw new GradleException("Missing packaged schema: ${path}")
        try {
            Object value = new JsonSlurper().parse(new InputStreamReader(source, 'UTF-8'))
            if (!(value instanceof Map) || value['$schema'] !=
                    'https://json-schema.org/draft/2020-12/schema') {
                throw new GradleException("Unsupported or malformed schema: ${path}")
            }
            return value as Map
        } finally {
            source.close()
        }
    }
}
