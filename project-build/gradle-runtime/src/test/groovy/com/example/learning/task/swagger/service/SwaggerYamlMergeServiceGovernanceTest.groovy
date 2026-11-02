package com.example.learning.task.swagger.service

import org.junit.Test

import static org.junit.Assert.*

class SwaggerYamlMergeServiceGovernanceTest {

    @Test
    void obsoleteFieldsAreRemovedFromInactiveAndActiveApiEntries() {
        Map oldActive = [summary: 'Unchanged', aiGenerated: true, reviewed: false]
        Map oldInactive = [summary: 'Historical', aiGenerated: false, reviewed: true]
        Map data = [DemoController: ['get()': oldActive, 'old()': oldInactive]]

        def method = SwaggerYamlMergeService.getDeclaredMethod('markAllApiUsage', Map, Boolean.TYPE)
        method.accessible = true
        method.invoke(null, data, false)

        [oldActive, oldInactive].each { Map entry ->
            assertEquals(['summary', 'usage'] as Set, entry.keySet())
            assertEquals(false, entry.usage)
        }
        assertEquals('Unchanged', oldActive.summary)
        assertEquals('Historical', oldInactive.summary)
    }
}
