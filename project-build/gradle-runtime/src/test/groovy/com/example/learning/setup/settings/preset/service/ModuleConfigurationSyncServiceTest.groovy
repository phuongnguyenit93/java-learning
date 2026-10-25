package com.example.learning.setup.settings.preset.service

import groovy.json.JsonSlurper
import org.gradle.api.logging.Logging
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.junit.Assert.*

class ModuleConfigurationSyncServiceTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder()

    @Test
    void defaultCurrentStepIsStringAndExistingValueSurvivesSync() {
        File dir = temporary.newFolder('progress-module')
        def service = new ModuleConfigurationSyncService(
                Logging.getLogger(ModuleConfigurationSyncServiceTest))
        service.sync(dir)
        File master = new File(dir, 'master.json')
        Map root = new JsonSlurper().parse(master) as Map
        assertEquals('string', root.CURRENT_STEP.TYPE)
        assertEquals('0', root.CURRENT_STEP.VALUE)
        root.CURRENT_STEP.VALUE = '12A'
        master.setText(groovy.json.JsonOutput.prettyPrint(
                groovy.json.JsonOutput.toJson(root)) + '\n', 'UTF-8')
        service.sync(dir)
        assertEquals('12A', (new JsonSlurper().parse(master) as Map).CURRENT_STEP.VALUE)
    }

    @Test
    void writesNewlineOnNewOrChangedJsonButDoesNotChurnLegacyFiles() {
        File module = temporary.newFolder('module')
        def service = new ModuleConfigurationSyncService(
                Logging.getLogger(ModuleConfigurationSyncServiceTest))
        service.sync(module)

        ['master.json', 'properties.json'].each { String name ->
            File json = new File(module, name)
            String original = json.getText('UTF-8')
            assertTrue(name + ' should end with a newline', original.endsWith('\n'))
            json.setText(original.substring(0, original.length() - 1), 'UTF-8')
            assertFalse(json.getText('UTF-8').endsWith('\n'))
        }

        Map valuesBefore = [
                master: new JsonSlurper().parse(new File(module, 'master.json')),
                properties: new JsonSlurper().parse(new File(module, 'properties.json'))
        ]
        service.sync(module)

        ['master.json', 'properties.json'].each { String name ->
            File json = new File(module, name)
            assertFalse(name + ' must not be rewritten for newline-only changes',
                    json.getText('UTF-8').endsWith('\n'))
        }
        assertEquals(valuesBefore.master, new JsonSlurper().parse(
                new File(module, 'master.json')))
        assertEquals(valuesBefore.properties, new JsonSlurper().parse(
                new File(module, 'properties.json')))

        // A real content mismatch must trigger a canonical write with final LF.
        File master = new File(module, 'master.json')
        Map altered = new JsonSlurper().parse(master) as Map
        String removed = altered.keySet().iterator().next() as String
        altered.remove(removed)
        master.setText(groovy.json.JsonOutput.prettyPrint(
                groovy.json.JsonOutput.toJson(altered)), 'UTF-8')
        service.sync(module)
        assertTrue('changed master.json must get final LF', master.getText('UTF-8').endsWith('\n'))
        assertEquals(valuesBefore.master, new JsonSlurper().parse(master))
    }

    @Test
    void preservesExistingCrLfFilesWhenOnlyLineEndingsDiffer() {
        File module = temporary.newFolder('crlf-module')
        def service = new ModuleConfigurationSyncService(
                Logging.getLogger(ModuleConfigurationSyncServiceTest))
        service.sync(module)

        Map<String, byte[]> before = [:]
        ['master.json', 'properties.json'].each { String name ->
            File json = new File(module, name)
            json.setText(json.getText('UTF-8').replace('\n', '\r\n'), 'UTF-8')
            before[name] = json.bytes
        }
        service.sync(module)

        before.each { String name, byte[] content ->
            assertArrayEquals(name + ' should not be rewritten', content,
                    new File(module, name).bytes)
        }
    }
}
