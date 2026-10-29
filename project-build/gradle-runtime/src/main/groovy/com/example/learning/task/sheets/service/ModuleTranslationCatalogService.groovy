package com.example.learning.task.sheets.service

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.GradleException

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/**
 * STEP 14: one EN file is one exclusive worker job. The catalog prepares a
 * read-only baseline (in build/) and never edits an authored VI/EN file.
 * Semantic quality remains a Prime review, not an automated claim.
 */
class ModuleTranslationCatalogService {
    private static final String DOC = 'content/readme/docs/'
    private static final String SWAGGER = 'src/main/resources/swagger/'
    private static final Map<String, String> SINGLE = [
            'content/readme/docs/en/BASE.md': 'INTRODUCTION',
            'content/roadmap/en/roadmap.yml': 'ROADMAP'
    ].asImmutable()
    private static final Map<String, String> API_TYPES = [
            'controller-description.yml': 'SWAGGER_CONTROLLER_DESCRIPTION',
            'api-descriptions.yml': 'SWAGGER_API_DESCRIPTIONS',
            'api-execution.yml': 'SWAGGER_API_EXECUTION',
            'api-cases.yml': 'SWAGGER_API_CASES',
            'api-params.yml': 'SWAGGER_API_PARAMS'
    ].asImmutable()

    Map execute(File repoRoot, File moduleRoot, String runId, String operation,
                boolean apply) {
        File root = repoRoot.canonicalFile
        File module = moduleRoot.canonicalFile
        checkModule(root, module)
        if (!(runId ==~ /[A-Za-z0-9][A-Za-z0-9_-]{0,63}/)) {
            throw new GradleException('translationRunId must be a safe 1..64-char identifier.')
        }
        String selected = operation?.toUpperCase(Locale.ROOT)
        if (!(selected in ['PREPARE', 'VALIDATE'])) {
            throw new GradleException('Unsupported STEP 14 operation: ' + operation)
        }
        String location = root.toPath().relativize(module.toPath()).toString()
                .replace(File.separatorChar, '/' as char)
        File runDirectory = new File(module, 'build/step14/' + runId)
        File manifest = new File(runDirectory, 'manifest.json')
        checkPath(module, manifest)
        List<Map> inventory = inventory(module)
        if (!inventory) throw new GradleException('No translatable EN files in module.')
        verifyProtected(module)
        if (selected == 'PREPARE') {
            if (manifest.exists()) {
                throw new GradleException('STEP 14 manifest already exists: ' + manifest +
                        '. Resume with VALIDATE, do not overwrite baseline.')
            }
            inventory.each { Map entry ->
                // Only the first preparation may require byte-identical VI/EN.
                if (entry.viSha256 != entry.enSha256) {
                    throw new GradleException('STEP 13 VI/EN clone not intact for ' +
                            entry.enPath + '. Refusing a new translation baseline.')
                }
                ModuleTranslationValidationService.compare(entry.type as String,
                        bytes(module, entry.viPath as String),
                        bytes(module, entry.enPath as String))
            }
            if (apply) {
                Files.createDirectories(runDirectory.toPath())
                File baseline = new File(runDirectory, 'baseline')
                inventory.each { Map entry ->
                    File output = new File(baseline, entry.enPath as String)
                    checkPath(module, output)
                    Files.createDirectories(output.parentFile.toPath())
                    Files.write(output.toPath(), bytes(module, entry.enPath as String),
                            StandardOpenOption.CREATE_NEW)
                }
                Map document = [module: location, runId: runId,
                                baselinePhase: 'STEP_13_CLONED',
                                files: inventory]
                // File is created last; interrupted preparation must not appear complete.
                Files.write(manifest.toPath(),
                        JsonOutput.prettyPrint(JsonOutput.toJson(document))
                                .getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE_NEW)
            }
            return [status: apply ? 'PREPARED' : 'DRY_RUN', module: location,
                    total: inventory.size(), runId: runId, manifest: manifest,
                    files: inventory.collect { Map item ->
                        [type: item.type, enPath: item.enPath,
                         result: apply ? 'BASELINED' : 'WOULD_BASELINE']
                    }]
        }

        if (!manifest.isFile()) {
            throw new GradleException('STEP 14 manifest is missing: ' + manifest)
        }
        Map document = new JsonSlurper().parse(manifest) as Map
        if (document.module != location || document.runId != runId ||
                !(document.files instanceof List)) {
            throw new GradleException('Invalid STEP 14 manifest module/runId.')
        }
        List<Map> original = document.files as List<Map>
        if (inventory.collect { it.enPath } != original.collect { it.enPath } ||
                inventory.collect { it.type } != original.collect { it.type }) {
            throw new GradleException('VI/EN translation file inventory changed.')
        }
        int translated = 0
        int pending = 0
        List<Map> results = []
        original.each { Map entry ->
            String path = entry.enPath as String
            byte[] source = bytes(module, entry.viPath as String)
            byte[] actual = bytes(module, path)
            File snapshot = new File(runDirectory, 'baseline/' + path)
            checkPath(module, snapshot)
            if (!snapshot.isFile() || sha(snapshot.bytes) != entry.enSha256 ||
                    sha(source) != entry.viSha256) {
                throw new GradleException('Source or STEP 14 baseline drift: ' + path)
            }
            ModuleTranslationValidationService.compare(entry.type as String, source, actual)
            boolean changed = sha(actual) != entry.enSha256
            if (changed) translated++ else pending++
            results.add([enPath: path, type: entry.type,
                         result: changed ? 'STRUCTURE_PASS_NEEDS_SEMANTIC_QA' : 'PENDING',
                         afterSha256: sha(actual)])
        }
        return [status: 'VALIDATED_STRUCTURE', module: location, runId: runId,
                total: original.size(), translated: translated, pending: pending,
                semanticQaRequired: true, files: results]
    }

    private static void verifyProtected(File module) {
        List<String> protectedFiles = ['content/readme/docs/en/LIST.md']
        File docs = new File(module, DOC + 'vi/menu')
        for (String path : walk(docs)) {
            if (path ==~ /[^\/]+\/metadata\/knowledge-metadata\.yml/) {
                protectedFiles.add(DOC + 'en/menu/' + path)
            }
        }
        protectedFiles.each { String path ->
            String vi = toVi(path)
            byte[] expected = bytes(module, vi)
            if (path.endsWith('/LIST.md')) {
                String converted = new String(expected, StandardCharsets.UTF_8)
                        .replace('content/readme/docs/vi/menu/',
                                'content/readme/docs/en/menu/')
                        .replace('README MODULE STRUCTURE (VI)',
                                'README MODULE STRUCTURE (EN)')
                expected = converted.getBytes(StandardCharsets.UTF_8)
            }
            if (!Arrays.equals(expected, bytes(module, path))) {
                throw new GradleException('Protected EN file changed: ' + path)
            }
        }
    }

    static List<Map> inventory(File module) {
        List<Map> entries = []
        Map<String, String> targets = new TreeMap<>()
        SINGLE.each { String key, String type ->
            if (new File(module, toVi(key)).isFile()) targets[key] = type
        }
        File docs = new File(module, DOC + 'vi/menu')
        for (String path : walk(docs)) {
            if (path ==~ /[^\/]+\.md/ ||
                    path ==~ /[^\/]+\/knowledge\/[^\/]+\.md/) {
                targets[DOC + 'en/menu/' + path] = 'README'
            } else if (path ==~ /[^\/]+\/quiz\/question\.yml/) {
                targets[DOC + 'en/menu/' + path] = 'QUIZ'
            } else if (path ==~ /[^\/]+\/questions\/interview\.yml/) {
                targets[DOC + 'en/menu/' + path] = 'INTERVIEW'
            } else if (path ==~ /[^\/]+\/questions\/summary\.yml/) {
                targets[DOC + 'en/menu/' + path] = 'SUMMARY_QUESTIONS'
            } else if (path ==~ /[^\/]+\/summary\/section\.yml/) {
                targets[DOC + 'en/menu/' + path] = 'SUMMARY_SECTION'
            } else if (path ==~ /[^\/]+\/summary\/summaries\.yml/) {
                targets[DOC + 'en/menu/' + path] = 'SUMMARY_LESSONS'
            }
        }
        File swagger = new File(module, SWAGGER + 'vi')
        for (String path : walk(swagger)) {
            if (!API_TYPES.containsKey(path)) {
                throw new GradleException('Unknown Swagger translation file: ' + path)
            }
            targets[SWAGGER + 'en/' + path] = API_TYPES[path]
        }
        targets.each { String en, String type ->
            String vi = toVi(en)
            File viFile = new File(module, vi)
            File enFile = new File(module, en)
            checkPath(module, viFile)
            checkPath(module, enFile)
            if (!viFile.isFile() || !enFile.isFile()) {
                throw new GradleException('Missing VI/EN translation pair: ' + en)
            }
            entries.add([type: type, viPath: vi, enPath: en,
                         viSha256: sha(viFile.bytes), enSha256: sha(enFile.bytes)])
        }
        // STEP 14 owns mandatory translations only. Video is authored later,
        // and a chapter may have a VI script without an EN counterpart.
        for (String area : ['content/readme/docs', 'content/roadmap',
                             SWAGGER[0..-2]]) {
            File vi = new File(module, area + '/vi')
            File en = new File(module, area + '/en')
            Set a = walk(vi) as Set
            Set b = walk(en) as Set
            if (area == 'content/readme/docs') {
                a = a.findAll { it in ['BASE.md', 'LIST.md'] ||
                        it ==~ /menu\/[^\/]+\.md/ ||
                        it ==~ /menu\/[^\/]+\/knowledge\/[^\/]+\.md/ ||
                        it ==~ /menu\/[^\/]+\/metadata\/knowledge-metadata\.yml/ ||
                        it ==~ /menu\/[^\/]+\/(quiz\/question|questions\/(interview|summary))\.yml/ ||
                        it ==~ /menu\/[^\/]+\/summary\/(section\.yml|summaries\.yml)/ } as Set
                b = b.findAll { it in ['BASE.md', 'LIST.md'] ||
                        it ==~ /menu\/[^\/]+\.md/ ||
                        it ==~ /menu\/[^\/]+\/knowledge\/[^\/]+\.md/ ||
                        it ==~ /menu\/[^\/]+\/metadata\/knowledge-metadata\.yml/ ||
                        it ==~ /menu\/[^\/]+\/(quiz\/question|questions\/(interview|summary))\.yml/ ||
                        it ==~ /menu\/[^\/]+\/summary\/(section\.yml|summaries\.yml)/ } as Set
            }
            if (a != b) throw new GradleException('VI/EN tree mismatch: ' + area)
        }
        return entries
    }

    private static List<String> walk(File directory) {
        if (!directory.exists()) return []
        if (!directory.isDirectory()) throw new GradleException('Expected directory: ' + directory)
        Path root = directory.toPath()
        def paths = Files.walk(root)
        try {
            List<String> values = []
            paths.forEach { Path child ->
                String relative = root.relativize(child).toString()
                        .replace(File.separatorChar, '/' as char)
                // Video lives below README chapters but belongs to a later,
                // independent workflow. Ignore its entire optional subtree.
                boolean docs = directory.toString().replace(File.separatorChar, '/' as char)
                        .contains('/content/readme/docs/')
                if (docs && relative ==~ /(?:menu\/)?[^\/]+\/video(?:\/.*)?/) return
                if (Files.isSymbolicLink(child)) {
                    throw new GradleException('Symlink not permitted: ' + child)
                }
                if (Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS)) {
                    values.add(relative)
                }
            }
            return values.sort()
        } finally { paths.close() }
    }

    private static String toVi(String path) {
        return path.replace('/en/', '/vi/')
    }

    private static byte[] bytes(File module, String relative) {
        File file = new File(module, relative)
        checkPath(module, file)
        if (!file.isFile()) throw new GradleException('Missing translation file: ' + file)
        return Files.readAllBytes(file.toPath())
    }

    private static void checkModule(File root, File module) {
        Path base = new File(root, 'module').canonicalFile.toPath()
        if (module.toPath() == base || !module.toPath().startsWith(base) ||
                !new File(module, 'gradle.properties').isFile()) {
            throw new GradleException('Translation catalog requires one Gradle module.')
        }
    }

    private static void checkPath(File module, File path) {
        Path base = module.toPath()
        Path target = path.toPath().toAbsolutePath().normalize()
        if (!target.startsWith(base)) throw new GradleException('Path escapes module: ' + path)
        Path current = base
        for (Path segment : base.relativize(target)) {
            current = current.resolve(segment)
            if (Files.isSymbolicLink(current)) {
                throw new GradleException('Symlinked translation path: ' + current)
            }
        }
    }

    static String sha(byte[] bytes) {
        return MessageDigest.getInstance('SHA-256').digest(bytes).encodeHex().toString()
    }
}
