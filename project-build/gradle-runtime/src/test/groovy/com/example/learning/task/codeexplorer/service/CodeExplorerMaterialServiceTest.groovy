package com.example.learning.task.codeexplorer.service

import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.junit.Assert.*

class CodeExplorerMaterialServiceTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder()

    @Test
    void buildsPortableBundleWithDirectGoToDefinitionAndSwaggerEndpoint() {
        Map f = fixture()
        CodeExplorerMaterialService service = new CodeExplorerMaterialService()
        Map dry = service.execute(f.root, f.module, false, false)
        assertEquals('DRY_RUN', dry.status)
        assertEquals(2, dry.files)
        assertEquals(1, dry.apis)
        assertFalse(new File(f.module, 'build/code-explorer/index.json').exists())
        Map created = service.execute(f.root, f.module, true, false)
        assertEquals('APPLIED', created.status)
        File bundle = new File(f.module, 'build/code-explorer')
        def index = parse(new File(bundle, 'index.json'))
        def symbols = parse(new File(bundle, 'symbols.json')).symbols
        def refs = parse(new File(bundle, 'references.json')).references
        def api = parse(new File(bundle, 'api-map.json')).endpoints
        assertEquals('CODE_EXPLORER', index.type)
        assertEquals('sample', index.id)
        assertEquals(2, index.files.size())
        assertEquals('DIRECTORY', index.tree[0].type)
        assertEquals('sample', index.tree[0].name)
        assertEquals(2, index.tree[0].children.size())
        assertEquals('RESOLVED', api[0].status)
        assertEquals('/sample/search', api[0].path)
        assertEquals('POST', api[0].httpMethod)
        String target = 'sample.BookCatalogService#search(String)'
        assertEquals([target], refs.find {
            it.kind == 'METHOD_CALL' && it.text == 'search'
        }.targets)
        assertEquals(1, refs.count {
            it.kind == 'METHOD_CALL' && it.text == 'search' && it.status == 'RESOLVED'
        })
        assertTrue(symbols.any { it.id == target && it.file == 'sample/BookCatalogService.java' })
        assertTrue(new File(bundle, 'files/sample/SampleController.java').text
                .contains('catalog.search(request)'))
        Map previous = [index:index, symbols:symbols, refs:refs, api:api]
        service.execute(f.root, f.module, true, false)
        assertEquals(previous.index, parse(new File(bundle, 'index.json')))
        assertEquals(previous.symbols, parse(new File(bundle, 'symbols.json')).symbols)
        assertEquals(previous.refs, parse(new File(bundle, 'references.json')).references)
    }

    @Test
    void publishesOnlySelectedFilesUnderPortalRoute() {
        Map f = fixture()
        def service = new CodeExplorerMaterialService()
        service.execute(f.root, f.module, true, true)
        File portal = new File(f.root,
                'build/learning-data/java/module/SAMPLE_MODULE/code-explorer')
        assertTrue(new File(portal, 'index.json').isFile())
        assertTrue(new File(portal, 'files/sample/BookCatalogService.java').isFile())
        assertFalse(new File(portal, 'files/sample/InternalSecret.java').exists())
        assertEquals(new File(f.module, 'build/code-explorer/index.json').bytes.toList(),
                new File(portal, 'index.json').bytes.toList())
    }

    @Test
    void overloadedSameArityIsNeverGuessed() {
        Map f = fixture()
        File service = new File(f.module, 'src/main/java/sample/BookCatalogService.java')
        service.setText('''package sample;
public class BookCatalogService {
    public String search(String request) { return request; }
    public String search(Integer request) { return request.toString(); }
}
''', 'UTF-8')
        new CodeExplorerMaterialService().execute(f.root, f.module, true, false)
        def refs = parse(new File(f.module,'build/code-explorer/references.json')).references
        Map call = refs.find { it.text == 'search' && it.kind == 'METHOD_CALL' }
        assertEquals('MULTIPLE_TARGETS', call.status)
        assertEquals(2, call.targets.size())
        def api = parse(new File(f.module,'build/code-explorer/api-map.json')).endpoints
        // The Controller method itself remains uniquely addressable. Only
        // the call into the overloaded Service is intentionally ambiguous.
        assertEquals('RESOLVED', api[0].status)
    }

    @Test
    void rejectsPathEscapeAndMissingModuleConfigWithoutWriting() {
        Map f = fixture()
        File settings = new File(f.module,'content/code-explorer/material.yml')
        settings.text = settings.text.replace('sample/*.java','../secrets/*.java')
        try {
            new CodeExplorerMaterialService().execute(f.root,f.module,true,false)
            fail('unsafe include must fail')
        } catch (GradleException expected) {
            assertTrue(expected.message.contains('Unsafe'))
        }
        assertFalse(new File(f.module,'build/code-explorer/index.json').exists())
    }

    private static Map parse(File file) {
        new JsonSlurper().parse(file) as Map
    }

    private Map fixture() {
        File root = temp.newFolder('repo')
        File module = new File(root, 'module/example')
        assertTrue(module.mkdirs())
        put(module, 'gradle.properties', 'IS_MODULE=TRUE')
        put(module, 'master.json',
                '{"SERVICE_NAME":{"VALUE":"SAMPLE_MODULE"}}')
        put(module, 'content/code-explorer/material.yml', '''version: 1
type: CODE_EXPLORER
id: sample
language: java
sourceRoot: src/main/java
include:
  - sample/*.java
apiMapping:
  source: SWAGGER
''')
        put(module,'src/main/java/sample/SampleController.java', '''package sample;
public class SampleController {
    private final BookCatalogService catalog = new BookCatalogService();
    public String search(String request) {
        return catalog.search(request);
    }
}
''')
        put(module,'src/main/java/sample/BookCatalogService.java', '''package sample;
public class BookCatalogService {
    public String search(String request) { return request.trim(); }
}
''')
        put(module,'src/main/resources/swagger/vi/api-descriptions.yml', '''SampleController:
  search(String):
    methodName: search
    params: [request]
    path: sample/SampleController.java
    httpMethods: [POST]
    mappingPaths: [/sample/search]
''')
        return [root:root,module:module]
    }

    private static void put(File module, String name, String text) {
        File output = new File(module,name)
        assertTrue(output.parentFile.isDirectory() || output.parentFile.mkdirs())
        output.setText(text,'UTF-8')
    }
}
