package com.example.learning.setup.root.structure.service

import groovy.json.JsonSlurper
import org.gradle.api.GradleException

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Fingerprint only committed Git inputs, never generated output or a dirty working tree. */
class LocalRunFingerprintService {
    private static final String VERSION = 'local-run-fingerprint-v2\n'
    private static final String INPUTS = 'project-build/gradle-runtime/src/main/resources/local-run/fingerprint-v2-roots.txt'
    private static final String REV = 'HEAD'
    private static final String MISSING = 'MISSING'
    private static final String GRADLE_RUNTIME_TREE = 'project-build/gradle-runtime'
    private static final String GENERATED_STRUCTURE =
            'project-build/gradle-runtime/src/main/resources/structure/module-structure.txt'

    private final File root
    private final List<String> sharedPaths
    private final Map<String, String> gitObjectCache = [:]
    private final Map<String, String> gitContentCache = [:]
    private Map<String, String> servicePaths

    LocalRunFingerprintService(File root) {
        this.root = root
        File file = new File(root, INPUTS)
        if (!file.isFile()) throw new GradleException("Missing Local Run V2 input manifest: ${file}")
        sharedPaths = file.readLines('UTF-8')
                .collect { it.trim() }
                .findAll { it && !it.startsWith('#') }
    }

    String fingerprint(String modulePath) {
        Set<String> paths = new TreeSet<>(sharedPaths)
        Set<String> dependencies = new TreeSet<>()
        addModuleWithDependencies(modulePath, dependencies, new HashSet<String>())
        dependencies.each { String dependency ->
            if (objectAt(dependency) == MISSING) {
                throw new GradleException("Local Run V2 module is not committed at HEAD: ${dependency}")
            }
        }
        paths.addAll(dependencies)
        StringBuilder payload = new StringBuilder(VERSION)
        paths.each { String path ->
            payload.append(path).append('\t').append(sharedObjectAt(path)).append('\n')
        }
        byte[] digest = MessageDigest.getInstance('SHA-256')
                .digest(payload.toString().getBytes(StandardCharsets.UTF_8))
        return HexFormat.of().formatHex(digest)
    }

    private void addModuleWithDependencies(String path, Set<String> paths, Set<String> visited) {
        if (!visited.add(path)) return
        paths.add(path)
        if (objectAt(path) == MISSING) {
            throw new GradleException("Local Run dependency missing from HEAD: ${path}")
        }

        String propertiesContent = contentAt("${path}/properties.json")
        if (propertiesContent != null) {
            Map value = new JsonSlurper().parseText(propertiesContent) as Map
            ['MODULE_DEPEND.MODULE_DEPEND_LIST', 'DATABASE.DATABASE_LIST', 'YML.BUILD_YML_MODULE_DEPEND'].each { String key ->
                List<String> parts = key.split('\\.') as List<String>
                Object entry = (value[parts[0]] instanceof Map) ? value[parts[0]][parts[1]] : null
                Object raw = entry instanceof Map ? entry.VALUE : null
                String csv = raw instanceof List ? raw.join(',') : (raw ?: '').toString()
                csv.split(',').collect { it.trim().toUpperCase(Locale.ROOT) }.findAll { it }.each { String dependency ->
                    String dependencyPath = serviceIndex()[dependency]
                    if (!dependencyPath) throw new GradleException("Unresolved Local Run V2 dependency ${dependency} in ${path}")
                    addModuleWithDependencies(dependencyPath, paths, visited)
                }
            }
        }

        String buildContent = contentAt("${path}/build.gradle")
        if (buildContent != null) {
            def matcher = (buildContent =~ /project\s*\(\s*['"](:[A-Za-z0-9_:-]+)['"]\s*\)/)
            matcher.each { match ->
                String dependencyPath = match[1].substring(1).replace(':', '/')
                addModuleWithDependencies(dependencyPath, paths, visited)
            }
        }
    }

    private Map<String, String> serviceIndex() {
        if (servicePaths != null) return servicePaths
        Map<String, String> resolved = [:]
        // Enumerate HEAD, not the live filesystem. A dirty/deleted local master.json
        // must never silently change the dependency closure of a committed build.
        Process process = new ProcessBuilder('git', '-C', root.absolutePath,
                'ls-tree', '-r', '--name-only', REV,
                'module', 'internal', 'project-build/springboot-runtime').start()
        String listing = process.inputStream.getText('UTF-8')
        String errors = process.errorStream.getText('UTF-8')
        if (process.waitFor() != 0) {
            throw new GradleException("Unable to enumerate Local Run V2 Git snapshot: ${errors}")
        }
        Set<String> tracked = listing.readLines().toSet()
        tracked.findAll { String path ->
            path.endsWith('/master.json') &&
                    tracked.contains(path.substring(0, path.length() - '/master.json'.length()) + '/gradle.properties')
        }.sort().each { String masterPath ->
            String relative = masterPath.substring(0, masterPath.length() - '/master.json'.length())
            String masterContent = contentAt(masterPath)
            if (masterContent == null) {
                throw new GradleException("Missing committed Local Run V2 metadata: ${masterPath}")
            }
            Map master = new JsonSlurper().parseText(masterContent) as Map
            Object item = master.SERVICE_NAME
            String name = (item instanceof Map ? item.VALUE : item)?.toString()?.trim()?.toUpperCase(Locale.ROOT)
            if (name) {
                if (resolved.containsKey(name) && resolved[name] != relative) {
                    throw new GradleException("Ambiguous Local Run V2 SERVICE_NAME: ${name}")
                }
                resolved[name] = relative
            }
        }
        servicePaths = resolved
        return resolved
    }

    private String contentAt(String path) {
        if (!gitContentCache.containsKey(path)) {
            Process process = new ProcessBuilder('git', '-C', root.absolutePath, 'show', "${REV}:${path}")
                    .redirectErrorStream(false).start()
            String content = process.inputStream.getText('UTF-8')
            process.errorStream.getText('UTF-8')
            gitContentCache[path] = process.waitFor() == 0 ? content : null
        }
        return gitContentCache[path]
    }

    private String objectAt(String path) {
        if (!gitObjectCache.containsKey(path)) {
            Process process = new ProcessBuilder('git', '-C', root.absolutePath, 'rev-parse', "${REV}:${path}")
                    .redirectErrorStream(true).start()
            String result = process.inputStream.getText('UTF-8').trim()
            int code = process.waitFor()
            gitObjectCache[path] = code == 0 && result ==~ /[0-9a-fA-F]{40,64}/ ? result.toLowerCase(Locale.ROOT) : MISSING
        }
        return gitObjectCache[path]
    }

    private String sharedObjectAt(String path) {
        if (path != GRADLE_RUNTIME_TREE || objectAt(path) == MISSING) {
            return objectAt(path)
        }
        // Gradle's generated navigation resource is not an authored build
        // input. Keep every other tracked file (including future additions).
        Process process = new ProcessBuilder('git', '-C', root.absolutePath,
                'ls-tree', '-r', '--full-tree', REV, '--', path).start()
        String listing = process.inputStream.getText('UTF-8')
        String errors = process.errorStream.getText('UTF-8')
        if (process.waitFor() != 0) {
            throw new GradleException("Unable to read committed shared inputs: ${errors}")
        }
        List<String> files = []
        listing.readLines().each { String entry ->
            String[] parts = entry.split('\\t', 2)
            if (parts.length != 2) {
                throw new GradleException("Malformed Git shared input entry: ${entry}")
            }
            String name = parts[1]
            if (name == GENERATED_STRUCTURE) return
            String[] metadata = parts[0].split(' ')
            if (metadata.length != 3 || metadata[1] != 'blob' || !name.startsWith(path + '/')) {
                throw new GradleException("Unexpected Git shared input entry: ${entry}")
            }
            files.add("${name}\t${metadata[2]}\n".toString())
        }
        byte[] digest = MessageDigest.getInstance('SHA-256')
                .digest(files.sort().join('').getBytes(StandardCharsets.UTF_8))
        return HexFormat.of().formatHex(digest)
    }
}
