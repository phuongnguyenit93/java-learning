package com.example.learning.task.sheets.service

import org.gradle.api.GradleException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.junit.Assert.*

class FillIntroductionServiceTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder()

    @Test void dryRunApplyAndPreserveWhenSourceIsEmpty() {
        File root = temporary.newFolder('repo')
        File module = new File(root, 'module/example')
        assertTrue(module.mkdirs())
        new File(module, 'gradle.properties').text = ''
        File output = new File(module, 'content/readme/docs/vi/BASE.md')
        String markdown = '# Module Introduction\n\nLearning goals.\n'

        assertEquals('DRY_RUN', FillIntroductionService.sync(root, module, markdown, false).status)
        assertFalse(output.exists())
        assertEquals('IMPORTED', FillIntroductionService.sync(root, module, markdown, true).status)
        assertEquals(markdown, output.getText('UTF-8'))
        assertEquals('UNCHANGED', FillIntroductionService.sync(root, module, markdown, true).status)
        assertEquals('SKIPPED_EMPTY_INTRODUCTION',
                FillIntroductionService.sync(root, module, '', true).status)
        assertEquals(markdown, output.getText('UTF-8'))

        String updated = '# New Introduction\n'
        assertEquals('DRY_RUN', FillIntroductionService.sync(root, module, updated, false).status)
        assertEquals(markdown, output.getText('UTF-8'))
        assertEquals('UPDATED', FillIntroductionService.sync(root, module, updated, true).status)
        assertEquals(updated, output.getText('UTF-8'))
    }

    @Test void rejectsUnsafeDestinationAndOversizedIntroduction() {
        File root = temporary.newFolder('repository')
        File outside = temporary.newFolder('outside')
        try {
            FillIntroductionService.sync(root, outside, '# Unsafe', true)
            fail('Should reject other directories')
        } catch (GradleException expected) {
            assertTrue(expected.message.contains('Invalid module destination'))
        }
        File module = new File(root, 'module/valid')
        assertTrue(module.mkdirs())
        new File(module, 'gradle.properties').text = ''
        try {
            FillIntroductionService.sync(root, module, 'x' * 30001, true)
            fail('Should reject oversized B17')
        } catch (GradleException expected) {
            assertTrue(expected.message.contains('30000'))
        }
    }
}
