package com.example.learning.task.swagger.service

import com.example.learning.task.swagger.model.SwaggerScanResult
import com.example.learning.task.swagger.model.SwaggerApiMetadata
import com.example.learning.task.swagger.model.SwaggerDescriptionDefault
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.yaml.snakeyaml.Yaml

import static org.junit.Assert.*

class SwaggerYamlMergeServiceGovernanceTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder()

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

    @Test
    void controllerDescriptionsDiscardReadmeRelatedButKeepAuthoredDescription() {
        Map controllers = [
                Group1Controller: [
                        description: '<h2>My custom group</h2>',
                        readmeRelated: [file: 'legacy.md', anchor: 'old']
                ]
        ]
        SwaggerScanResult scan = new SwaggerScanResult([Group1Controller: [:]], [])
        def defaults = new SwaggerDescriptionDefault('s', 'd', false, '', '', 'Paragraph', '')
        def merge = SwaggerYamlMergeService.getDeclaredMethod(
                'mergeControllerDescriptionData', Map, SwaggerScanResult, SwaggerDescriptionDefault)
        merge.accessible = true
        merge.invoke(null, controllers, scan, defaults)
        assertEquals([description: '<h2>My custom group</h2>'], controllers.Group1Controller)
    }

    @Test
    void methodParamsRetainAuthoredProseAndMatchSourceOrderWithoutGlobalNames() {
        def merge = SwaggerYamlMergeService.getDeclaredMethod(
                'mergeMethodParameters', Object, Collection, SwaggerDescriptionDefault)
        merge.accessible = true
        def defaults = new SwaggerDescriptionDefault('Untitled', 'To be described',
                false, '', '', '', '')
        def old = [[name: 'mode', summary: 'Chế độ chạy',
                    description: 'CPU hoặc allocation'],
                   [name: 'removed', summary: 'Should go away']]

        List result = merge.invoke(null, old, ['other', 'mode'], defaults) as List
        assertEquals(['other', 'mode'], result.collect { it.name })
        assertEquals('Untitled', result[0].summary)
        assertEquals('To be described', result[0].description)
        assertEquals('Chế độ chạy', result[1].summary)
        assertEquals('CPU hoặc allocation', result[1].description)
        assertFalse(result[1].containsKey('usage'))
        assertEquals(2, result.size())
        assertEquals('removed', old[1].name)

        List again = merge.invoke(null, result, ['other', 'mode'], defaults) as List
        assertEquals(result, again)
    }

    @Test
    void methodParamsRejectLegacyNamesAndDuplicateMetadata() {
        def merge = SwaggerYamlMergeService.getDeclaredMethod(
                'mergeMethodParameters', Object, Collection, SwaggerDescriptionDefault)
        merge.accessible = true
        def defaults = new SwaggerDescriptionDefault('Untitled', 'To be described',
                false, '', '', '', '')
        for (invalid in [['mode'], [[name: 'mode'], [name: 'mode']]]) {
            try {
                merge.invoke(null, invalid, ['mode'], defaults)
                fail('Legacy or duplicate params must be rejected')
            } catch (java.lang.reflect.InvocationTargetException error) {
                assertTrue(error.cause instanceof IllegalStateException)
            }
        }
    }

    @Test
    void regeneratePreservesPerMethodDescriptionAndNeverCreatesSeparateParameterFile() {
        File dir = temp.newFolder('swagger-method-params')
        File descriptions = new File(dir, 'api-descriptions.yml')
        descriptions.setText('''DemoController:
  run(String):
    params:
      - name: mode
        summary: Human summary
        description: Human description
''', 'UTF-8')
        SwaggerApiMetadata method = new SwaggerApiMetadata(
                'DemoController', 'run(String)', 'run', ['mode'],
                'com/example/DemoController.java', '@GetMapping',
                ['GET'], ['/demo/run'], [], [])
        SwaggerScanResult scan = new SwaggerScanResult(
                [DemoController: ['run(String)': method]], ['mode'])
        def defaults = new SwaggerDescriptionDefault(
                'Default summary', 'Default description', false,
                '', '', 'Controller', 'Execution')
        def service = new SwaggerYamlMergeService()
        service.merge(dir, scan, defaults)
        Map initial = new Yaml().load(descriptions.getText('UTF-8')) as Map
        assertEquals('Human summary',
                initial.DemoController['run(String)'].params[0].summary)
        assertEquals('Human description',
                initial.DemoController['run(String)'].params[0].description)
        assertEquals('mode',
                initial.DemoController['run(String)'].params[0].name)
        assertFalse(new File(dir, 'api-params.yml').exists())
        service.merge(dir, scan, defaults)
        Map repeated = new Yaml().load(descriptions.getText('UTF-8')) as Map
        assertEquals(initial, repeated)
        assertFalse(new File(dir, 'api-params.yml').exists())
    }
}
