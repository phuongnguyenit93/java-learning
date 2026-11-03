package com.example.learning.task.codeexplorer.service

import groovy.json.JsonSlurper
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.junit.Assert.*

/** The shared Java indexing engine remains required by Case Study Explorer.
 * No module-wide material.yml config or general Explorer task is supported. */
class CodeExplorerMaterialServiceTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder()

    @Test
    void buildsCaseScopedSymbolsAndReferencesWithoutGeneralMaterialConfig() {
        Map<String, byte[]> sources = [
                'sample/SampleController.java': '''package sample;
public class SampleController {
    private final BookService service = new BookService();
    public String executeCase1(String name) { return service.search(name); }
}
'''.getBytes('UTF-8'),
                'sample/BookService.java': '''package sample;
public class BookService {
    public String search(String request) { return request.trim(); }
}
'''.getBytes('UTF-8')
        ]
        Map bundle = CodeExplorerMaterialService.buildFromSources('casestudy-g1-c1', sources)
        assertEquals(2, bundle.fileCount)
        Map index = json(bundle.outputs['index.json'])
        assertEquals('CODE_EXPLORER', index.type)
        assertEquals('casestudy-g1-c1', index.id)
        assertFalse(index.containsKey('apiMap'))
        assertEquals(2, index.files.size())
        assertEquals('sample', index.tree[0].name)
        Map symbolData = json(bundle.outputs['symbols.json'])
        Map refData = json(bundle.outputs['references.json'])
        String id = 'sample.BookService#search(String)'
        assertTrue(symbolData.symbols.any { it.id == id && it.file == 'sample/BookService.java' })
        assertEquals([id], refData.references.find {
            it.kind == 'METHOD_CALL' && it.text == 'search'
        }.targets)
        assertFalse(bundle.outputs.containsKey('api-map.json'))
        assertEquals(bundle.outputs['index.json'].toList(),
                CodeExplorerMaterialService.buildFromSources('casestudy-g1-c1', sources)
                        .outputs['index.json'].toList())
    }

    @Test
    void overloadedMethodsAreMarkedAmbiguous() {
        Map<String,byte[]> sources = [
                'sample/Controller.java': '''package sample;
public class Controller {
    private final Service service = new Service();
    public String run() { return service.search("hello"); }
}
'''.getBytes('UTF-8'),
                'sample/Service.java': '''package sample;
public class Service {
    public String search(String value) { return value; }
    public String search(Integer value) { return value.toString(); }
}
'''.getBytes('UTF-8'),
        ]
        Map refs = json(CodeExplorerMaterialService
                .buildFromSources('casestudy-g1-c2', sources).outputs['references.json'])
        Map call = refs.references.find { it.kind == 'METHOD_CALL' && it.text == 'search' }
        assertEquals('MULTIPLE_TARGETS', call.status)
        assertEquals(2, call.targets.size())
    }

    @Test
    void cleanupRetiresOldModuleWideBundlesWithoutDeletingCaseStudy() {
        File root = temp.newFolder('repo')
        File bundle = new File(root, 'build/learning-data/java/module/DEMO/code-explorer')
        File old = new File(bundle, 'files/demo/Old.java')
        old.parentFile.mkdirs()
        old.setText('public class Old {}', 'UTF-8')
        File active = new File(bundle, 'casestudy/index.json')
        active.parentFile.mkdirs()
        active.setText('{"type":"CASE_STUDY_EXPLORER"}', 'UTF-8')
        new File(bundle, 'index.json').setText('{"version":1}', 'UTF-8')
        CodeExplorerMaterialService.cleanUnclaimedLegacyBundles(root, [] as Set<String>)
        assertFalse(old.exists())
        assertFalse(new File(bundle, 'index.json').exists())
        assertTrue(active.isFile())
    }

    private static Map json(byte[] raw) {
        new JsonSlurper().parseText(new String(raw, 'UTF-8')) as Map
    }
}
