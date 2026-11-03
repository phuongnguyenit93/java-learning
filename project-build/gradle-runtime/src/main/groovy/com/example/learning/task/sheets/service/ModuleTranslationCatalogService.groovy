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
 * STEP 11: a worker job owns exactly one file or a defined EN file group. The catalog prepares a
 * read-only baseline (in build/) and never edits an authored VI/EN file.
 * Semantic quality remains a Prime review, not an automated claim.
 */
class ModuleTranslationCatalogService {
    private static final String DOC = 'content/readme/docs/'
    private static final String SWAGGER = 'src/main/resources/swagger/'
    private static final Map<String, String> SINGLE = [
            'content/readme/docs/en/BASE.md': 'INTRODUCTION',
            'content/roadmap/en/roadmap.yml': 'ROADMAP',
            'content/reference/en/references.yml': 'REFERENCE'
    ].asImmutable()
    private static final Map<String, String> API_TYPES = [
            'controller-description.yml': 'SWAGGER_CONTROLLER_DESCRIPTION',
            'api-descriptions.yml': 'SWAGGER_API_DESCRIPTIONS',
            'api-execution.yml': 'SWAGGER_API_EXECUTION',
            'api-cases.yml': 'SWAGGER_API_CASES',
    ].asImmutable()
    private static final Map<String, String> MODULE_ASSESSMENTS = [
            'content/quiz/en/quiz.yml'          : 'MODULE_QUIZ',
            'content/interview/en/interview.yml': 'MODULE_INTERVIEW',
            'content/casestudy/en/casestudy.yml': 'MODULE_CASE_STUDY',
    ].asImmutable()

    Map execute(File repoRoot, File moduleRoot, String runId, String operation,
                boolean apply, String workerMode = 'NORMAL',
                Integer requestedWorkers = null) {
        File root = repoRoot.canonicalFile
        File module = moduleRoot.canonicalFile
        checkModule(root, module)
        if (!(runId ==~ /[A-Za-z0-9][A-Za-z0-9_-]{0,63}/)) {
            throw new GradleException('translationRunId must be a safe 1..64-char identifier.')
        }
        String selected = operation?.toUpperCase(Locale.ROOT)
        if (!(selected in ['PREPARE', 'VALIDATE'])) {
            throw new GradleException('Unsupported STEP 11 operation: ' + operation)
        }
        String dispatchMode = (workerMode ?: 'NORMAL').toUpperCase(Locale.ROOT)
        if (!(dispatchMode in ['NORMAL', 'SPECIAL'])) {
            throw new GradleException('BLOCKER: UNKNOWN_WORKER_MODE: ' + dispatchMode)
        }
        if (dispatchMode == 'SPECIAL' && (requestedWorkers == null || requestedWorkers < 1)) {
            throw new GradleException('BLOCKER: SPECIAL_MODE_WORKER_COUNT_REQUIRED')
        }
        if (dispatchMode == 'NORMAL' && requestedWorkers != null && requestedWorkers > 3) {
            throw new GradleException('BLOCKER: NORMAL_MODE_MAX_3_WORKERS')
        }
        int maxWorkers = dispatchMode == 'NORMAL' ?
                (requestedWorkers == null ? 3 : requestedWorkers) : requestedWorkers
        if (maxWorkers < 1) {
            throw new GradleException('BLOCKER: WORKER_COUNT_MUST_BE_POSITIVE')
        }
        String location = root.toPath().relativize(module.toPath()).toString()
                .replace(File.separatorChar, '/' as char)
        File runDirectory = new File(module, 'build/step11/' + runId)
        File manifest = new File(runDirectory, 'manifest.json')
        checkPath(module, manifest)
        List<Map> inventory = inventory(module)
        if (!inventory) throw new GradleException('No translatable EN files in module.')
        List<Map> jobs = workerJobs(inventory)
        File agentDirectory = new File(root, 'module-generate-agent')
        if (agentDirectory.isDirectory()) {
            jobs.each { Map job ->
                File rule = new File(agentDirectory, 'worker/' + (job.rule as String))
                checkPath(root, rule)
                if (!rule.isFile()) {
                    throw new GradleException('BLOCKER: STEP11_WORKER_RULE_MISSING: ' + rule)
                }
            }
        }
        verifyProtected(module)
        if (selected == 'PREPARE') {
            if (manifest.exists()) {
                throw new GradleException('STEP 11 manifest already exists: ' + manifest +
                        '. Resume with VALIDATE, do not overwrite baseline.')
            }
            inventory.each { Map entry ->
                // Only the first preparation may require byte-identical VI/EN.
                if (entry.viSha256 != entry.enSha256) {
                    throw new GradleException('STEP 10 VI/EN clone not intact for ' +
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
                                baselinePhase: 'STEP_10_CLONED',
                                workerMode: dispatchMode, maxWorkers: maxWorkers,
                                files: inventory, jobs: jobs]
                // File is created last; interrupted preparation must not appear complete.
                Files.write(manifest.toPath(),
                        JsonOutput.prettyPrint(JsonOutput.toJson(document))
                                .getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE_NEW)
            }
            return [status: apply ? 'PREPARED' : 'DRY_RUN', module: location,
                    total: inventory.size(), runId: runId, manifest: manifest,
                    jobs: jobs, jobCount: jobs.size(),
                    workerMode: dispatchMode, maxWorkers: maxWorkers,
                    files: inventory.collect { Map item ->
                        [type: item.type, enPath: item.enPath,
                         result: apply ? 'BASELINED' : 'WOULD_BASELINE']
                    }]
        }

        if (!manifest.isFile()) {
            throw new GradleException('STEP 11 manifest is missing: ' + manifest)
        }
        Map document = new JsonSlurper().parse(manifest) as Map
        if (document.module != location || document.runId != runId ||
                !(document.files instanceof List)) {
            throw new GradleException('Invalid STEP 11 manifest module/runId.')
        }
        List<Map> original = document.files as List<Map>
        if (inventory.collect { it.enPath } != original.collect { it.enPath } ||
                inventory.collect { it.type } != original.collect { it.type }) {
            throw new GradleException('VI/EN translation file inventory changed.')
        }
        if (document.baselinePhase != 'STEP_10_CLONED' ||
                document.jobs != jobs ||
                !(document.workerMode in ['NORMAL', 'SPECIAL']) ||
                !(document.maxWorkers instanceof Number) || document.maxWorkers < 1) {
            throw new GradleException('STEP 11 worker assignment or baseline drift.')
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
                throw new GradleException('Source or STEP 11 baseline drift: ' + path)
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
                semanticQaRequired: true, workerMode: document.workerMode,
                maxWorkers: document.maxWorkers, jobs: jobs,
                jobCount: jobs.size(), files: results]
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

    /** Deterministic queue. Only the files assigned here may be written by a job owner. */
    static List<Map> workerJobs(List<Map> inventory) {
        Map<String, Map> queued = new LinkedHashMap<>()
        Map<String, String> workerRule = [
                'README'                         : 'STEP_11_WORKER_KNOWLEDGE.md',
                'INTRODUCTION'                   : 'STEP_11_WORKER_COMMON.md',
                'ROADMAP'                        : 'STEP_11_WORKER_COMMON.md',
                'REFERENCE'                      : 'STEP_11_WORKER_COMMON.md',
                'SUMMARY_SECTION'                : 'STEP_11_WORKER_SUMMARY.md',
                'SUMMARY_LESSONS'                : 'STEP_11_WORKER_SUMMARY.md',
                'MODULE_QUIZ'                    : 'STEP_11_WORKER_QUIZ.md',
                'MODULE_INTERVIEW'               : 'STEP_11_WORKER_INTERVIEW.md',
                'MODULE_CASE_STUDY'              : 'STEP_11_WORKER_CASE_STUDY.md',
                'SWAGGER_CONTROLLER_DESCRIPTION' : 'STEP_11_WORKER_SWAGGER_CONTROLLER.md',
                'SWAGGER_API_DESCRIPTIONS'       : 'STEP_11_WORKER_SWAGGER_DESCRIPTIONS.md',
                'SWAGGER_API_EXECUTION'          : 'STEP_11_WORKER_SWAGGER_EXECUTION.md',
                'SWAGGER_API_CASES'              : 'STEP_11_WORKER_SWAGGER_CASES.md'
        ]
        inventory.each { Map entry ->
            String type = entry.type as String
            String path = entry.enPath as String
            String rule = workerRule[type]
            if (!rule) {
                throw new GradleException('No STEP 11 worker rule for translation type: ' + type)
            }
            String key
            if (type in ['INTRODUCTION', 'ROADMAP', 'REFERENCE']) {
                key = 'COMMON'
            } else if (type in ['SUMMARY_SECTION', 'SUMMARY_LESSONS']) {
                def match = (path =~ /\/menu\/([^\/]+)\/summary\/(section|summaries)\.yml$/)
                if (!match.find()) throw new GradleException('Unscoped Summary job: ' + path)
                key = 'SUMMARY/' + match.group(1)
            } else {
                key = path
            }
            Map job = queued[key]
            if (job == null) {
                job = [id: key, rule: rule, files: []]
                queued[key] = job
            }
            if (job.rule != rule) {
                throw new GradleException('Conflicting translation worker rules: ' + key)
            }
            (job.files as List).add(path)
        }
        queued.each { String key, Map job ->
            (job.files as List).sort()
            if (key.startsWith('SUMMARY/')) {
                Set names = (job.files as List<String>).collect {
                    it.substring(it.lastIndexOf('/') + 1)
                } as Set
                if (names != (['section.yml', 'summaries.yml'] as Set)) {
                    throw new GradleException('BLOCKER: INCOMPLETE_SUMMARY_PAIR ' + key)
                }
            }
        }
        List<Map> jobs = queued.values().toList().sort { Map a, Map b ->
            a.id.toString() <=> b.id.toString()
        }
        Set<String> assigned = jobs.collectMany { it.files as List<String> } as Set
        if (assigned.size() != inventory.size() ||
                assigned != (inventory.collect { it.enPath } as Set)) {
            throw new GradleException('BLOCKER: STEP11_JOB_OWNERSHIP_OVERLAP_OR_GAP')
        }
        return jobs
    }

    static List<Map> inventory(File module) {
        List<Map> entries = []
        Map<String, String> targets = new TreeMap<>()
        SINGLE.each { String key, String type ->
            if (new File(module, toVi(key)).isFile()) targets[key] = type
        }
        MODULE_ASSESSMENTS.each { String key, String type ->
            if (new File(module, toVi(key)).isFile()) targets[key] = type
        }
        File docs = new File(module, DOC + 'vi/menu')
        for (String path : walk(docs)) {
            if (path ==~ /[^\/]+\.md/ ||
                    path ==~ /[^\/]+\/knowledge\/[^\/]+\.md/) {
                targets[DOC + 'en/menu/' + path] = 'README'
            } else if (path ==~ /[^\/]+\/quiz\/question\.yml/ &&
                    !new File(module, 'content/quiz/vi/quiz.yml').isFile()) {
                targets[DOC + 'en/menu/' + path] = 'QUIZ'
            } else if (path ==~ /[^\/]+\/questions\/interview\.yml/ &&
                    !new File(module, 'content/interview/vi/interview.yml').isFile()) {
                targets[DOC + 'en/menu/' + path] = 'INTERVIEW'
            } else if (path ==~ /[^\/]+\/questions\/summary\.yml/) {
                // Old per-chapter data stays untouched after canonical module-wide
                // assessments have replaced the old STEP 6/7 shape.
                if (!new File(module, 'content/interview/vi/interview.yml').isFile()) {
                    throw new GradleException('BLOCKER: LEGACY_SUMMARY_QUESTIONS_NOT_SUPPORTED: ' + path)
                }
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
        // STEP 11 owns mandatory translations only. Video is authored later,
        // and a chapter may have a VI script without an EN counterpart.
        for (String area : ['content/readme/docs', 'content/roadmap', 'content/reference',
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
        MODULE_ASSESSMENTS.each { String key, String type ->
            File vi = new File(module, toVi(key))
            File en = new File(module, key)
            if (vi.exists() != en.exists()) {
                throw new GradleException('Missing module assessment VI/EN pair: ' + key)
            }
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
