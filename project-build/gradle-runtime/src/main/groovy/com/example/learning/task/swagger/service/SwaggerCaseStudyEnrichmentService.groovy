package com.example.learning.task.swagger.service

import org.gradle.api.GradleException
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Deterministic enrichment of Swagger using caseStudy fields; no AI or translation. */
class SwaggerCaseStudyEnrichmentService {
    private static final Set SUMMARIES = ['Chưa có tiêu đề', 'Title not provided', ''] as Set
    private static final Set DESCRIPTIONS = ['Chưa có mô tả chi tiết', 'Detailed description not provided', ''] as Set

    Map execute(File module, List<String> languages, boolean apply) {
        Map<File, byte[]> outputs = new LinkedHashMap<>()
        Map report = new LinkedHashMap()
        List locales = languages.collect { it?.trim()?.toLowerCase(Locale.ROOT) }.findAll { it }.unique()
        if (locales.isEmpty()) throw new GradleException('MODULE_LANGUAGE is empty')
        locales.each { String lang ->
            File source = new File(module, 'content/casestudy/' + lang + '/casestudy.yml')
            if (!source.isFile()) {
                report[lang] = [status: 'SKIPPED_NO_LOCALIZED_CASE_STUDY']
                return
            }
            File dir = new File(module, 'src/main/resources/swagger/' + lang)
            File apiFile = new File(dir, 'api-descriptions.yml')
            File controllerFile = new File(dir, 'controller-description.yml')
            if (!apiFile.isFile() || !controllerFile.isFile()) {
                throw new GradleException('Run generateApiSwaggerDescription first for ' + lang)
            }
            Map sourceDoc = asMap(parse(source), 'Case Study')
            List groups = sourceDoc.caseStudy instanceof List ? sourceDoc.caseStudy as List : null
            if (groups == null || groups.isEmpty()) throw new GradleException('Missing Case Study groups for ' + lang)
            Map apis = asMap(parse(apiFile), 'Swagger API')
            Map controllers = asMap(parse(controllerFile), 'Swagger controllers')
            Set names = new LinkedHashSet()
            int casesTotal = 0
            int changed = 0
            int preserved = 0
            groups.each { Object rawGroup ->
                Map group = asMap(rawGroup, 'Case Study group')
                if (!(group.groupID instanceof Number) || group.groupID.intValue() < 1 ||
                        group.groupID.doubleValue() != group.groupID.intValue()) {
                    throw new GradleException('Invalid Case Study groupID: ' + group.groupID)
                }
                int id = group.groupID.intValue()
                String name = 'Group' + id + 'Controller'
                if (!names.add(name)) throw new GradleException('Duplicate group ' + id)
                String groupName = required(group.groupName, name + '.groupName')
                Map methods = asMap(apis[name], name + ' API methods')
                Map controller = asMap(controllers[name], name + ' controller description')
                String heading = '<h2>' + escapeHtml(groupName) + '</h2>'
                if (fill(controller, 'description', heading, { String s ->
                    s.isBlank() || s.matches('(?s)<h2>Group[0-9]+ Controller</h2>\\s*<p>(Đây là mô tả|This is a description)</p>')
                })) changed++
                else if (controller.description != heading) preserved++
                if (controller.readmeRelated instanceof Map &&
                        (controller.readmeRelated as Map).size() == 1 &&
                        !(controller.readmeRelated as Map).file?.toString()?.trim()) {
                    controller.remove('readmeRelated')
                }
                List cases = group.cases instanceof List ? group.cases as List : null
                if (cases == null || cases.isEmpty()) throw new GradleException('No cases in ' + name)
                Set matched = new LinkedHashSet()
                cases.eachWithIndex { Object rawCase, int offset ->
                    int n = offset + 1
                    Map item = asMap(rawCase, name + ' case ' + n)
                    List signatures = methods.keySet().findAll { Object signature ->
                        signature instanceof String &&
                                (signature as String).matches('executeCase' + n + '\\([^)]*\\)')
                    }.toList()
                    if (signatures.size() != 1) throw new GradleException('Missing/ambiguous method for ' + name + ' case ' + n)
                    String signature = signatures.first()
                    matched.add(signature)
                    Map method = asMap(methods[signature], name + '.' + signature)
                    String route = '/case-studies/group/' + id + '/case/' + n + '/execute'
                    if (method.methodName != 'executeCase' + n || method.usage != true ||
                            !(method.mappingPaths instanceof List) || !(route in method.mappingPaths)) {
                        throw new GradleException('Swagger route mismatch for ' + name + '.' + signature)
                    }
                    String summary = required(item.situation, name + '.situation')
                    String resolve = required(item.resolve, name + '.resolve')
                    String knowledge = required(item.knowledge, name + '.knowledge')
                    Map relation = asMap(item.readmeRelated, name + '.readmeRelated')
                    if (!(relation.anchorList instanceof List)) throw new GradleException('Missing anchorList for ' + name)
                    List anchors = (relation.anchorList as List).collect { required(it, name + '.anchorList') }
                    if (anchors.size() != anchors.toSet().size()) throw new GradleException('Duplicate anchorList for ' + name)
                    String description = '<p><strong>Hướng xử lý:</strong> ' + escapeHtml(resolve) +
                            '</p>\n<p><strong>Kiến thức liên quan:</strong> ' + escapeHtml(knowledge) + '</p>'
                    if (fill(method, 'summary', summary, { String s -> s in SUMMARIES })) changed++
                    else if (method.summary != summary) preserved++
                    if (fill(method, 'description', description, { String s -> s in DESCRIPTIONS })) changed++
                    else if (method.description != description) preserved++
                    Map existing = method.readmeRelated == null ? [:] :
                            asMap(method.readmeRelated, name + '.readmeRelated')
                    if (existing.anchorList != null && (!(existing.anchorList instanceof List) ||
                            !(existing.anchorList as List).every { it instanceof String })) {
                        throw new GradleException('Invalid Swagger anchorList for ' + name + '.' + signature)
                    }
                    if (existing.anchorList == anchors) {
                        if (existing.keySet() != (['anchorList'] as Set)) changed++
                        method.readmeRelated = [anchorList: anchors]
                    } else if ((existing.anchorList == null || (existing.anchorList as List).isEmpty()) &&
                            !existing.file?.toString()?.trim() && !existing.anchor?.toString()?.trim()) {
                        method.readmeRelated = [anchorList: anchors]
                        changed++
                    } else {
                        preserved++
                    }
                    casesTotal++
                }
                if (methods.keySet().findAll { it.toString().startsWith('executeCase') }.toSet() != matched) {
                    throw new GradleException('Unexpected methods in ' + name)
                }
            }
            if (apis.keySet().toSet() != names || controllers.keySet().toSet() != names) {
                throw new GradleException('Swagger controller inventory mismatch for ' + lang)
            }
            stage(outputs, apiFile, dump(apis))
            stage(outputs, controllerFile, dump(controllers))
            report[lang] = [controllers: names.size(), cases: casesTotal,
                            changed: changed, authoredPreserved: preserved]
        }
        if (apply) applyAll(outputs)
        [mode: apply ? 'APPLY' : 'DRY_RUN', filesChanged: outputs.size(), languages: report]
    }

    private static boolean fill(Map entry, String key, String wanted, Closure<Boolean> isPlaceholder) {
        Object old = entry[key]
        if (old == wanted) return false
        if (old == null || (old instanceof String && isPlaceholder(old))) {
            entry[key] = wanted
            return true
        }
        false
    }

    private static String required(Object input, String label) {
        if (!(input instanceof String) || !input.trim()) throw new GradleException('Missing ' + label)
        input.trim()
    }

    private static Map asMap(Object input, String label) {
        if (!(input instanceof Map)) throw new GradleException('Invalid ' + label)
        input as Map
    }

    private static String escapeHtml(String v) {
        v.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
                .replace('"', '&quot;').replace("'", '&#39;')
    }

    private static Object parse(File file) {
        LoaderOptions opts = new LoaderOptions()
        opts.allowDuplicateKeys = false
        new Yaml(new SafeConstructor(opts)).load(file.getText('UTF-8'))
    }

    private static byte[] dump(Map data) {
        DumperOptions opts = new DumperOptions()
        opts.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        opts.prettyFlow = true
        opts.indent = 2
        new Yaml(opts).dump(data).getBytes('UTF-8')
    }

    private static void stage(Map<File, byte[]> changes, File file, byte[] bytes) {
        if (!Arrays.equals(Files.readAllBytes(file.toPath()), bytes)) changes[file] = bytes
    }

    private static void applyAll(Map<File, byte[]> outputs) {
        Map<File, byte[]> before = outputs.collectEntries { File f, byte[] bytes ->
            [(f): Files.readAllBytes(f.toPath())]
        }
        try {
            outputs.each { File file, byte[] content ->
                File tmp = File.createTempFile('swagger-enrich-', '.tmp', file.parentFile)
                try {
                    Files.write(tmp.toPath(), content)
                    Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                } finally { tmp.delete() }
                if (!Arrays.equals(Files.readAllBytes(file.toPath()), content)) {
                    throw new GradleException('Readback mismatch: ' + file)
                }
            }
        } catch (Exception failure) {
            before.each { File file, byte[] bytes -> Files.write(file.toPath(), bytes) }
            throw failure
        }
    }
}
