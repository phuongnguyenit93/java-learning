package com.example.learning.task.sheets.service

import org.gradle.api.GradleException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.yaml.snakeyaml.Yaml

import static org.junit.Assert.*

class ReferenceSheetImportServiceTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder()

    @Test
    void validatesReferenceYamlAndCleansOnlyModuleReferenceDirectory() {
        File root = tmp.newFolder('repo')
        File module = new File(root, 'module/sandbox/demo')
        File owned = new File(module, 'content/reference')
        assertTrue(owned.mkdirs())
        new File(owned, 'obsolete.yml').setText('old', 'UTF-8')
        File unaffected = new File(module, 'content/readme/docs/vi/BASE.md')
        assertTrue(unaffected.parentFile.mkdirs())
        unaffected.setText('untouched', 'UTF-8')
        assertEquals(owned.canonicalFile, ReferenceSheetImportService.directory(
                root, 'module/sandbox/demo').canonicalFile)
        String yaml = '''references:
  - title: Java Specification
    url: https://docs.oracle.com/javase/specs/
    description: Official specification
'''
        assertEquals(1, ReferenceSheetImportService.parseReference(yaml).references.size())
        assertEquals('DRY_RUN', SheetSnapshotReplaceService.replace(
                root, owned, ['references.yml': yaml], false).status)
        assertEquals('UPDATED', SheetSnapshotReplaceService.replace(
                root, owned, ['references.yml': yaml], true).status)
        assertEquals(['references.yml'], owned.list().toList())
        assertEquals(yaml, new File(owned, 'references.yml').getText('UTF-8'))
        assertEquals('untouched', unaffected.getText('UTF-8'))
        assertEquals([], ReferenceSheetImportService.parseReference('references: []').references)
    }

    @Test
    void rejectsInvalidReferenceSchemaWithoutMutation() {
        ['not-a-map', 'references: null', 'references: {}',
         'references:\n  - title: MissingUrl', 'references:\n  - title: X\n    url: 2'].each { String text ->
            try {
                ReferenceSheetImportService.parseReference(text.replace('\\n', '\n'))
                fail('Expected invalid Reference YAML: ' + text)
            } catch (GradleException expected) {
                assertTrue(expected.message != null && !expected.message.isEmpty())
            }
        }
    }
}
