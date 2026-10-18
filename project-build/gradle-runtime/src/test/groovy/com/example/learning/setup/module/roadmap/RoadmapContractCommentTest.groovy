package com.example.learning.setup.module.roadmap

import com.example.learning.setup.module.roadmap.service.RoadmapSchemaService
import com.example.learning.utils.schema.ContractSchemaLoaderUtils
import org.gradle.api.GradleException
import org.junit.Test

import static org.junit.Assert.*

class RoadmapContractCommentTest {
    @Test
    void commentsComeFromPinnedJsonSchemaAndLocalizedGuidance() {
        Map contract = ContractSchemaLoaderUtils.read('roadmap')
        def renderer = new RoadmapSchemaService()
        ['vi', 'en'].each { String lang ->
            String comment = renderer.renderCommentBlock(lang)
            assertTrue(comment.startsWith('# <roadmap-schema>\n'))
            assertTrue(comment.endsWith('# </roadmap-schema>'))
            assertTrue(comment.contains(contract['x-title'][lang]))
            assertTrue(comment.contains(contract.properties.roadmap['x-description'][lang]))
            Map fields = contract.properties.roadmap.items.properties as Map
            fields.each { String key, Object field ->
                assertTrue("Missing field ${key}", comment.contains("# - ${key} ["))
                assertTrue("Missing localized description for ${key}",
                        comment.contains(field['x-description'][lang]))
            }
            contract['x-guidance'].thinkingFramework.each { Map section ->
                assertTrue(comment.contains(section.title[lang]))
                section.questions.each { Map question ->
                    assertTrue(comment.contains(question[lang]))
                }
            }
            contract['x-guidance'].principles.values().each { Map principle ->
                assertTrue(comment.contains(principle[lang]))
            }
        }
        assertNull(getClass().classLoader.getResource('roadmap/roadmap-schema.yml'))
    }

    @Test
    void rejectsUnavailableCommentLanguagesRatherThanInventingGuidance() {
        try {
            new RoadmapSchemaService().renderCommentBlock('fr')
            fail('Expected missing localization')
        } catch (GradleException expected) {
            assertTrue(expected.message.contains('no text for language'))
        }
    }
}
