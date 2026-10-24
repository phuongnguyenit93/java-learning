package com.example.learning.task.swagger.service

import com.example.learning.task.swagger.model.SwaggerApiMetadata
import com.example.learning.task.swagger.model.SwaggerScanResult
import org.junit.Rule
import org.junit.Test
import org.junit.Assume
import org.junit.rules.TemporaryFolder
import org.yaml.snakeyaml.Yaml

import static org.junit.Assert.*

class SwaggerApiCasesMergeServiceTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder()

    @Test
    void scansRealControllerMappingsIntoDistinctCaseSkeletons() {
        // Gradle 8.x embeds JavaParser 3.17 ahead of the declared newer
        // dependency, and that older enum has no JAVA_21. Run this scanner
        // integration check only with a compatible Gradle/JavaParser runtime.
        Assume.assumeTrue(
                com.github.javaparser.ParserConfiguration.LanguageLevel.values()
                        .any { it.name() == 'JAVA_21' })
        File source = temporary.newFolder('java-src')
        File controller = new File(source, 'DemoController.java')
        controller.setText('''
            package example;
            import org.springframework.web.bind.annotation.*;
            @RestController
            @RequestMapping("/demo")
            class DemoController {
                @GetMapping("/items/{id}")
                String find(@PathVariable String id) { return id; }
                @PostMapping("/items")
                String create(@RequestBody String body) { return body; }
            }
        ''', 'UTF-8')

        SwaggerScanResult scan = new SwaggerJavaSourceScanner().scan(source)
        assertEquals(2, scan.apiCount)

        File output = temporary.newFolder('real-scan-output')
        new SwaggerApiCasesMergeService().merge(output, scan)
        Map cases = read(output).DemoController
        assertEquals(['GET'], cases['find(String)'].httpMethods)
        assertEquals(['/demo/items/{id}'], cases['find(String)'].mappingPaths)
        assertEquals(['POST'], cases['create(String)'].httpMethods)
        assertEquals(['/demo/items'], cases['create(String)'].mappingPaths)
        assertEquals('case_001', cases['find(String)'].cases[0].id)
        assertEquals('case_001', cases['create(String)'].cases[0].id)
    }

    @Test
    void generatesAnEditableCaseForEachApiWithoutGuessingItsResponse() {
        File dir = temporary.newFolder('swagger-en')
        new SwaggerApiCasesMergeService().merge(dir, scan('create(CreateRequest)'))

        Map data = read(dir)
        Map api = data.DemoController['create(CreateRequest)']
        assertEquals(['POST'], api.httpMethods)
        assertEquals(['/demo/create'], api.mappingPaths)
        assertEquals(true, api.usage)
        assertEquals(1, api.cases.size())
        assertEquals('case_001', api.cases[0].id)
        assertTrue(api.cases[0].title.startsWith('TODO: '))
        assertTrue(api.cases[0].title.contains('this case'))
        assertTrue(api.cases[0].description.startsWith('TODO: '))
        assertEquals('<p>TODO: Use HTML to explain why this case produces this result.</p>',
                api.cases[0].detailDescription)
        assertEquals([pathParams: [:], queryParams: [:], headers: [:], body: null],
                api.cases[0].input)
        assertNull(api.cases[0].status)
        assertNull(api.cases[0].response)
    }

    @Test
    void rerunPreservesAuthoredCasesAndMarksDeletedApisUnused() {
        File dir = temporary.newFolder('swagger-en')
        SwaggerApiCasesMergeService service = new SwaggerApiCasesMergeService()
        service.merge(dir, scan('create(CreateRequest)'))

        Map content = read(dir)
        Map edited = content.DemoController['create(CreateRequest)']
        edited.cases[0].title = 'Valid checkout'
        edited.cases[0].description = 'A verified example'
        edited.cases[0].detailDescription =
                '<p>The checkout succeeds after validation.</p><ol><li>Service accepts the order.</li></ol>'
        edited.cases[0].input.body = [quantity: 2]
        edited.cases[0].status = 200
        edited.cases[0].response = [accepted: true]
        edited.cases << [id: 'case_002', title: 'Other scenario', status: 422,
                         response: [error: 'invalid']]
        File output = new File(dir, 'api-cases.yml')
        output.setText(new Yaml().dump(content), 'UTF-8')

        service.merge(dir, scan('create(CreateRequest)', 'cancel(String)'))
        List mergedCases = read(dir).DemoController['create(CreateRequest)'].cases
        assertEquals(edited.cases[0], mergedCases[0])
        Map upgradedOther = new LinkedHashMap(mergedCases[1] as Map)
        String guidance = upgradedOther.remove('detailDescription') as String
        assertEquals(edited.cases[1], upgradedOther)
        assertEquals('<p>TODO: Use HTML to explain why this case produces this result.</p>', guidance)
        String rerun = output.getText('UTF-8')
        service.merge(dir, scan('create(CreateRequest)', 'cancel(String)'))
        assertEquals(rerun, output.getText('UTF-8'))

        service.merge(dir, scan('cancel(String)'))
        assertEquals(false, read(dir).DemoController['create(CreateRequest)'].usage)
        assertEquals('Valid checkout',
                read(dir).DemoController['create(CreateRequest)'].cases[0].title)
    }

    @Test
    void addsMissingHtmlGuidanceWithoutChangingExistingLegacyOrAuthoredFields() {
        File dir = temporary.newFolder('swagger-vi')
        SwaggerApiCasesMergeService service = new SwaggerApiCasesMergeService()
        service.merge(dir, scan('create(CreateRequest)', 'cancel(String)'))
        Map content = read(dir)
        def cases = content.DemoController['create(CreateRequest)'].cases
        // A legacy case generated before detailDescription was introduced.
        cases[0].title = ''
        cases[0].description = ''
        cases[0].remove('detailDescription')

        def authored = content.DemoController['cancel(String)'].cases[0]
        authored.title = 'An authored scenario'
        authored.description = 'Keep this exact description'
        authored.remove('detailDescription')
        new File(dir, 'api-cases.yml').setText(new Yaml().dump(content), 'UTF-8')

        service.merge(dir, scan('create(CreateRequest)', 'cancel(String)'))
        Map after = read(dir)
        def updated = after.DemoController['create(CreateRequest)'].cases[0]
        assertEquals('', updated.title)
        assertEquals('', updated.description)
        assertEquals('<p>TODO: Viết bằng HTML, giải thích vì sao case này có kết quả như trên.</p>',
                updated.detailDescription)
        assertNull(updated.status)
        assertNull(updated.response)
        def updatedAuthored = after.DemoController['cancel(String)'].cases[0]
        assertEquals(authored.title, updatedAuthored.title)
        assertEquals(authored.description, updatedAuthored.description)
        assertEquals(authored.id, updatedAuthored.id)
        assertEquals(authored.input, updatedAuthored.input)
        assertEquals(authored.status, updatedAuthored.status)
        assertEquals(authored.response, updatedAuthored.response)
        assertEquals('<p>TODO: Viết bằng HTML, giải thích vì sao case này có kết quả như trên.</p>',
                updatedAuthored.detailDescription)

        String firstPass = new File(dir, 'api-cases.yml').getText('UTF-8')
        service.merge(dir, scan('create(CreateRequest)', 'cancel(String)'))
        assertEquals(firstPass, new File(dir, 'api-cases.yml').getText('UTF-8'))
    }

    @Test
    void addsOnlyMissingDetailDescriptionToPopulatedCasesInBothLanguages() {
        for (String language : ['vi', 'en']) {
            File dir = temporary.newFolder("authored-cases-${language}")
            SwaggerApiCasesMergeService service = new SwaggerApiCasesMergeService()
            service.merge(dir, scan('create(CreateRequest)'))

            Map document = read(dir)
            Map api = document.DemoController['create(CreateRequest)']
            Map authoredWithoutDetail = [
                    id         : 'author_custom_id',
                    title      : 'Already authored title',
                    description: 'Keep the existing explanation',
                    input      : [pathParams: [id: 'item-3'],
                                  queryParams: [verbose: true],
                                  headers: [Authorization: 'Bearer example'],
                                  body: [quantity: 2, items: ['one', 'two']]],
                    status     : 422,
                    response   : [error: [code: 'INVALID_QUANTITY', fields: ['quantity']]],
                    authorNote : 'Do not remove unknown authored fields'
            ]
            api.cases = [
                    authoredWithoutDetail,
                    [id: 'existing_html', title: 'Preserve existing HTML',
                     detailDescription: '<section>Hand-written HTML</section>',
                     input: [body: 'original'], status: 200, response: [ok: true]],
                    [id: 'existing_null', title: 'Preserve present null',
                     detailDescription: null, status: 204],
                    [id: 'existing_empty', title: 'Preserve present empty string',
                     detailDescription: '', description: 'Do not replace empty HTML']
            ]
            Map preservedFields = (Map) new Yaml().load(new Yaml().dump(authoredWithoutDetail))
            File output = new File(dir, 'api-cases.yml')
            output.setText(new Yaml().dump(document), 'UTF-8')

            service.merge(dir, scan('create(CreateRequest)'))
            List cases = read(dir).DemoController['create(CreateRequest)'].cases

            Map upgraded = new LinkedHashMap(cases[0] as Map)
            String guidance = upgraded.remove('detailDescription') as String
            assertEquals(preservedFields, upgraded)
            assertTrue(guidance.startsWith('<p>TODO:'))
            assertEquals(language == 'vi'
                    ? '<p>TODO: Viết bằng HTML, giải thích vì sao case này có kết quả như trên.</p>'
                    : '<p>TODO: Use HTML to explain why this case produces this result.</p>', guidance)
            assertEquals(api.cases[1], cases[1])
            assertEquals(api.cases[2], cases[2])
            assertEquals(api.cases[3], cases[3])

            String firstPass = output.getText('UTF-8')
            service.merge(dir, scan('create(CreateRequest)'))
            assertEquals(firstPass, output.getText('UTF-8'))
        }
    }

    @Test
    void malformedOrDuplicateCaseIdsFailWithoutOverwritingTheFile() {
        File dir = temporary.newFolder('swagger-invalid')
        SwaggerApiCasesMergeService service = new SwaggerApiCasesMergeService()
        service.merge(dir, scan('create(CreateRequest)'))
        File output = new File(dir, 'api-cases.yml')
        Map content = read(dir)
        content.DemoController['create(CreateRequest)'].cases << [
                id: 'case_001', title: 'Duplicate']
        String invalid = new Yaml().dump(content)
        output.setText(invalid, 'UTF-8')

        try {
            service.validate(dir)
            fail('Expected duplicate ID rejection')
        } catch (IllegalStateException expected) {
            assertTrue(expected.message.contains('duplicate'))
        }
        assertEquals(invalid, output.getText('UTF-8'))
    }

    private static Map read(File dir) {
        (Map) new Yaml().load(new File(dir, 'api-cases.yml').getText('UTF-8'))
    }

    private static SwaggerScanResult scan(String... signatures) {
        Map<String, SwaggerApiMetadata> methods = new LinkedHashMap<>()
        signatures.each { String signature ->
            String name = signature.substring(0, signature.indexOf('('))
            methods[signature] = new SwaggerApiMetadata(
                    'DemoController', signature, name, ['request'],
                    'com/example/DemoController.java', '@PostMapping',
                    ['POST'], ['/demo/' + name], [], [])
        }
        new SwaggerScanResult([DemoController: methods], ['request'])
    }
}
