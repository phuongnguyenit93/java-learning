package com.example.learning.setup.module

import groovy.json.JsonSlurper
import org.junit.Test

import static org.junit.Assert.*

/**
 * Authoring capabilities are discovered from validated source artifacts.
 * They must not be reintroduced as switches in the canonical master template.
 */
class ContentBuildFlagRetirementTest {
    private static final Set<String> RETIRED = [
            'BUILD_INTERVIEW', 'BUILD_QUIZ', 'BUILD_VIDEO',
            'BUILD_ROADMAP', 'BUILD_REFERENCE', 'BUILD_METADATA',
            'BUILD_README'
    ] as Set

    @Test
    void canonicalMasterTemplateDoesNotContainRetiredContentFlags() {
        Map config = resourceJson('automation/master.json') as Map
        assertTrue('Unexpected content build flags: ' + config.keySet().intersect(RETIRED),
                config.keySet().intersect(RETIRED).isEmpty())

        // Genuine runtime/dependency feature controls remain independent.
        assertTrue(config.containsKey('BUILD_SWAGGER'))
        assertTrue(config.containsKey('BUILD_EXECUTION_CONTEXT'))
        assertTrue(config.containsKey('BUILD_ENV'))
    }

    @Test
    void readmeTaskRegistrationDoesNotRequireContentBuildFlag() {
        List<Map> tasks = resourceJson('task/module-task-list.json') as List<Map>
        Map readme = tasks.find { Map task -> task.type == 'readme' }
        assertNotNull(readme)
        assertEquals('', readme.propCheck)
        assertFalse(tasks.any { Map task -> RETIRED.contains(task.propCheck?.toString()) })
    }

    private static Object resourceJson(String resource) {
        InputStream stream = ContentBuildFlagRetirementTest.classLoader
                .getResourceAsStream(resource)
        assertNotNull('Missing resource ' + resource, stream)
        return stream.withCloseable { InputStream input ->
            new JsonSlurper().parse(input, 'UTF-8')
        }
    }
}
