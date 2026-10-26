package com.example.learning.setup.root.structure.service

import com.example.learning.utils.ProjectPropertyUtils
import com.example.learning.task.sheets.service.SummarySectionYamlService
import groovy.json.JsonOutput
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

import java.nio.file.Files
import java.nio.file.Path

/** Read-only, language-specific Summary JSON projection from authored chapter files. */
class PortalSummaryProjectionService {
    private final Logger logger

    PortalSummaryProjectionService(Logger logger) { this.logger = logger }

    void generate(Project rootProject) {
        File modules = new File(rootProject.projectDir, 'module').canonicalFile
        File out = new File(rootProject.projectDir, LearningDataPaths.MODULE_DIRECTORY)
        Map<String, String> result = new LinkedHashMap<>()
        Set<String> routes = new LinkedHashSet<>()
        rootProject.allprojects.findAll { Project module ->
            module.projectDir.canonicalFile.toPath().startsWith(modules.toPath()) &&
                    new File(module.projectDir, 'gradle.properties').isFile()
        }.sort { Project a, Project b -> a.projectDir.path <=> b.projectDir.path }
                .each { Project module ->
            String route = (module.findProperty('SERVICE_NAME') ?: module.projectDir.name)
                    .toString().trim()
            boolean authored = false
            List<String> langs = ProjectPropertyUtils.getStringList(module,'MODULE_LANGUAGE')
                    .collect { it?.trim()?.toLowerCase(Locale.ROOT) }
                    .findAll { it } .unique()
            langs.each { String lang ->
                File menu = new File(module.projectDir, 'content/readme/docs/' + lang + '/menu')
                if (!menu.isDirectory()) return
                File[] listed = menu.listFiles()
                if (listed == null) throw new GradleException('Cannot list Summary menu: ' + menu)
                List<Map> items = []
                listed.findAll { File f -> f.isDirectory() &&
                        f.name ==~ /[1-9][0-9]*\.[A-Za-z0-9][A-Za-z0-9 _.-]*/ &&
                        !f.name.contains('..') }
                        .sort { File a, File b ->
                            (Integer.parseInt(a.name.split('\\.')[0]) <=>
                             Integer.parseInt(b.name.split('\\.')[0])) ?: (a.name <=> b.name)
                        }.each { File chapter ->
                    File section = new File(chapter, 'summary/section.yml')
                    File yaml = new File(chapter, 'summary/summaries.yml')
                    if (!section.exists() && !yaml.exists()) return
                    if (!section.isFile() || !yaml.isFile() ||
                            Files.isSymbolicLink(section.toPath()) ||
                            Files.isSymbolicLink(yaml.toPath())) {
                        throw new GradleException('Incomplete/linked Portal Summary: ' + chapter)
                    }
                    Map doc = readYaml(yaml)
                    if (doc.keySet() != (['summaries'] as Set) ||
                            !(doc.summaries instanceof List) || (doc.summaries as List).isEmpty()) {
                        throw new GradleException('Invalid Portal Summary YAML: ' + yaml)
                    }
                    Set<String> ids = [] as Set
                    (doc.summaries as List).each { Object value ->
                        if (!(value instanceof Map) ||
                                value.keySet() != (['id','title','summary'] as Set) ||
                                !(value.id instanceof String) || !value.id.trim() ||
                                !(value.title instanceof String) || !value.title.trim() ||
                                !(value.summary instanceof String) || !value.summary.trim() ||
                                !ids.add(value.id as String)) {
                            throw new GradleException('Invalid Summary lesson in ' + yaml)
                        }
                    }
                    Map roadmap = SummarySectionYamlService.read(section.getText('UTF-8'),
                            (doc.summaries as List).collect { it.id as String })
                    String categoryId = chapter.name.substring(chapter.name.indexOf('.') + 1)
                    String contentPath = route + '/summary/' + lang + '/content/' + categoryId + '/summary.json'
                    put(result, contentPath, [version:1, moduleId:route, language:lang,
                            categoryId:categoryId, section:roadmap, summaries:doc.summaries])
                    items.add([categoryId:categoryId, title:categoryId,
                            content:'/module/' + encode(route) + '/summary/' + encode(lang) +
                                    '/content/' + encode(categoryId) + '/summary.json',
                            lessonCount:(doc.summaries as List).size()])
                }
                if (!items.isEmpty()) {
                    authored = true
                    put(result, route + '/summary/' + lang + '/index.json', [
                            version:1,moduleId:route,language:lang,
                            sectionCount:items.size(),items:items])
                }
            }
            if (authored && !routes.add(route)) {
                throw new GradleException('Duplicate Portal Summary module route: ' + route)
            }
        }
        result.each { String path, String body ->
            File file = new File(out, path)
            if (!file.parentFile.isDirectory() && !file.parentFile.mkdirs()) {
                throw new GradleException('Cannot create Portal Summary directory: ' + file.parentFile)
            }
            if (!file.isFile() || file.getText('UTF-8') != body) file.setText(body,'UTF-8')
        }
        if (out.isDirectory()) {
            out.eachFileRecurse { File file ->
                if (!file.isFile()) return
                String relative = out.toPath().relativize(file.toPath()).toString()
                        .replace(File.separatorChar, '/' as char)
                List<String> parts = relative.split('/').toList()
                if (parts.size() >= 3 && parts[1] == 'summary' &&
                        !result.containsKey(relative) && !file.delete()) {
                    throw new GradleException('Cannot remove stale Portal Summary: ' + file)
                }
            }
        }
        logger.lifecycle('[PORTAL-SUMMARY] Generated {} Summary projection files.',result.size())
    }

    private static Map readYaml(File file) {
        try {
            LoaderOptions options = new LoaderOptions()
            options.allowDuplicateKeys = false
            Object value = new Yaml(new SafeConstructor(options)).load(file.getText('UTF-8'))
            if (value instanceof Map) return value as Map
        } catch (Exception e) {
            throw new GradleException('Cannot parse Summary YAML: ' + file, e)
        }
        throw new GradleException('Summary YAML must be a mapping: ' + file)
    }

    private static void put(Map<String,String> outputs, String path, Map data) {
        if (outputs.containsKey(path)) throw new GradleException('Duplicate Summary output: ' + path)
        outputs[path] = JsonOutput.prettyPrint(JsonOutput.toJson(data)) + '\n'
    }

    private static String encode(String text) {
        java.net.URLEncoder.encode(text, 'UTF-8').replace('+','%20')
    }
}
