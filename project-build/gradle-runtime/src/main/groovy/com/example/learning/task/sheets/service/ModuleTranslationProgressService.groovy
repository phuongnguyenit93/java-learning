package com.example.learning.task.sheets.service

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.GradleException

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Instant

/**
 * PRIME-only STEP 11 recovery journal, separate from the immutable module manifest.
 *
 * Files live in module-generate-agent/temp/step11/<module-relative-path>/<runId>.json.
 * Only the Prime/task writes this file; workers never own it. The journal is *not*
 * a substitute for validation of authored EN files or Prime's semantic review.
 */
class ModuleTranslationProgressService {
    static final int VERSION = 1
    private static final Set<String> FINISHED =
            ['QA_PASS', 'SKIP_NO_TRANSLATABLE_TEXT'] as Set<String>
    private static final Set<String> ACTIONS =
            ['SHOW', 'ASSIGN', 'REPORT', 'QA_PASS', 'SKIP_NO_TRANSLATABLE_TEXT',
             'REVIEW', 'BLOCKER', 'REQUEUE', 'COMPLETE'] as Set<String>

    /**
     * Called BEFORE PREPARE APPLY creates a new manifest. An unfinished
     * journal for the same module must be resumed, never bypassed with a new RUN_ID.
     */
    void preflightPrepare(File rootDirectory, File moduleDirectory, String runId) {
        File root = rootDirectory.canonicalFile
        File module = moduleDirectory.canonicalFile
        if (!(runId ==~ /[A-Za-z0-9][A-Za-z0-9_-]{0,63}/)) {
            throw new GradleException('Invalid STEP11 progress RUN_ID.')
        }
        Path moduleBase = new File(root, 'module').canonicalFile.toPath()
        if (module.toPath() == moduleBase || !module.toPath().startsWith(moduleBase) ||
                !new File(module, 'gradle.properties').isFile()) {
            throw new GradleException('STEP11 progress requires a real module.')
        }
        File agent = new File(root, 'module-generate-agent')
        if (!agent.isDirectory()) {
            throw new GradleException('STEP11 progress needs module-generate-agent folder.')
        }
        String location = root.toPath().relativize(module.toPath()).toString()
                .replace(File.separatorChar, '/' as char)
        File directory = new File(agent, 'temp/step11/' + location)
        guard(agent.toPath(), directory.toPath())
        if (directory.exists() && !directory.isDirectory()) {
            throw new GradleException('BLOCKER: STEP11_PROGRESS_DIRECTORY_NOT_REGULAR')
        }
        if (directory.isDirectory()) {
            def stream = Files.newDirectoryStream(directory.toPath(), '*.json')
            try {
                stream.each { Path path ->
                    if (path.fileName.toString() != runId + '.json') {
                        throw new GradleException('BLOCKER: STEP11_PROGRESS_OTHER_RUN_ACTIVE: ' +
                                path + '. Resume the existing RUN_ID instead.')
                    }
                }
            } finally {
                stream.close()
            }
        }
    }

    /** Idempotent; never reset an existing journal (including its job states). */
    Map initialize(File rootDirectory, File moduleDirectory, String runId) {
        preflightPrepare(rootDirectory, moduleDirectory, runId)
        Map context = context(rootDirectory, moduleDirectory, runId)
        File ledgerFile = context.ledger as File
        Map manifest = context.manifest as Map
        byte[] manifestBytes = context.manifestBytes as byte[]
        String manifestHash = ModuleTranslationCatalogService.sha(manifestBytes)
        if (ledgerFile.isFile()) {
            Map existing = read(context)
            return response(existing, ledgerFile, 'EXISTING')
        }
        if (ledgerFile.exists()) {
            throw new GradleException('BLOCKER: STEP11_PROGRESS_NOT_REGULAR_FILE: ' + ledgerFile)
        }
        // A missing journal after workers have started is ambiguous.
        // Compare targets to THIS run's immutable baseline, not to VI: a
        // retranslation run may start with already-translated target files.
        File module = context.module as File
        (manifest.files as List<Map>).each { Map entry ->
            String targetPath = entry.enPath as String
            File source = new File(module, entry.viPath as String)
            File target = new File(module, targetPath)
            File baseline = new File(module, 'build/step11/' + runId +
                    '/baseline/' + targetPath)
            [source, target, baseline].each { File file ->
                guard(module.toPath(), file.toPath())
                if (!Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                    throw new GradleException('BLOCKER: STEP11_PROGRESS_BASELINE_MISSING: ' +
                            file)
                }
            }
            if (ModuleTranslationCatalogService.sha(source.bytes) != entry.viSha256 ||
                    ModuleTranslationCatalogService.sha(baseline.bytes) != entry.enSha256) {
                throw new GradleException('BLOCKER: STEP11_PROGRESS_BASELINE_DRIFT: ' +
                        targetPath)
            }
            if (ModuleTranslationCatalogService.sha(target.bytes) != entry.enSha256) {
                throw new GradleException('BLOCKER: STEP11_PROGRESS_RECOVERY_REQUIRED: ' +
                        'Target differs from this run baseline: ' + targetPath +
                        '. Recover the prior worker state before creating a journal.')
            }
        }
        String now = Instant.now().toString()
        List<Map> jobs = (manifest.jobs as List<Map>).collect { Map job ->
            [id: job.id, rule: job.rule, files: job.files,
             status: 'PENDING', workerId: null, attempts: 0,
             assignedHashes: [:], reportedHashes: [:],
             note: '', updatedAt: now]
        }
        Map ledger = [schemaVersion: VERSION, step: 11,
                      module: context.location, runId: runId,
                      manifestSha256: manifestHash,
                      workerMode: manifest.workerMode, maxWorkers: manifest.maxWorkers,
                      revision: 0, createdAt: now, updatedAt: now,
                      jobs: jobs, history: []]
        save(context, ledger, null)
        return response(ledger, ledgerFile, 'CREATED')
    }

    /**
     * @param expectedRevision mandatory for mutations; read from SHOW.
     * @param note human QA / reconciliation evidence, never free-form secrets.
     */
    Map execute(File rootDirectory, File moduleDirectory, String runId,
                String action, String jobId = '', String workerId = '',
                String note = '', Integer expectedRevision = null) {
        String selected = (action ?: '').trim().toUpperCase(Locale.ROOT)
        if (!(selected in ACTIONS)) {
            throw new GradleException('Unknown STEP11 progress action: ' + selected)
        }
        Map ctx = context(rootDirectory, moduleDirectory, runId)
        Map journal = read(ctx)
        File ledgerFile = ctx.ledger as File
        if (selected == 'SHOW') return response(journal, ledgerFile, 'RESUMED')

        if (expectedRevision == null || expectedRevision != journal.revision) {
            throw new GradleException('BLOCKER: STEP11_PROGRESS_REVISION_MISMATCH: ' +
                    'read SHOW and provide its current revision before writing.')
        }
        String safeNote = (note ?: '').trim()
        if (safeNote.length() > 500 || safeNote.indexOf(0) >= 0) {
            throw new GradleException('STEP11 progress note must be <=500 characters.')
        }
        if (selected == 'COMPLETE') {
            ensureComplete(ctx, journal)
            byte[] before = ledgerFile.bytes
            if (ModuleTranslationCatalogService.sha(before) !=
                    ModuleTranslationCatalogService.sha(ctx.ledgerBytes as byte[])) {
                throw new GradleException('BLOCKER: STEP11_PROGRESS_CONCURRENT_CHANGE')
            }
            // A terminated Gradle/Prime during atomic save may leave a staging
            // file. At COMPLETE no worker or other writer should be running.
            Path parent = ledgerFile.parentFile.toPath()
            def leftovers = Files.newDirectoryStream(parent, '.step11-progress-*.tmp')
            try {
                leftovers.each { Path child ->
                    guard(new File(ctx.root as File, 'module-generate-agent').toPath(), child)
                    if (!Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS)) {
                        throw new GradleException('BLOCKER: STEP11_PROGRESS_UNSAFE_STAGING: ' +
                                child)
                    }
                    Files.delete(child)
                }
            } finally {
                leftovers.close()
            }
            Files.delete(ledgerFile.toPath())
            cleanupEmptyDirectories(ledgerFile.parentFile.toPath(),
                    new File(ctx.root as File, 'module-generate-agent/temp').toPath())
            return response(journal, ledgerFile, 'REMOVED_AFTER_STEP11_PASS')
        }
        String id = (jobId ?: '').trim()
        Map job = (journal.jobs as List<Map>).find { it.id == id } as Map
        if (job == null) {
            throw new GradleException('Unknown STEP11 progress jobId: ' + id)
        }
        String prior = job.status as String
        String actor = (workerId ?: '').trim()
        if (selected == 'ASSIGN') {
            if (prior != 'PENDING' ||
                    !(actor ==~ /[A-Za-z0-9][A-Za-z0-9_.-]{0,99}/)) {
                throw new GradleException('BLOCKER: STEP11_PROGRESS_ASSIGN_REQUIRES_PENDING_AND_WORKER')
            }
            List<Map> active = (journal.jobs as List<Map>).findAll {
                it.status == 'ASSIGNED'
            }
            if (active.size() >= (journal.maxWorkers as int) ||
                    active.any { it.workerId == actor }) {
                throw new GradleException('BLOCKER: STEP11_PROGRESS_WORKER_POOL_OR_OWNER_BUSY')
            }
            job.workerId = actor
            job.attempts = (job.attempts as int) + 1
            job.assignedHashes = hashes(ctx.module as File, job.files as List<String>)
            job.reportedHashes = [:]
            job.note = ''
            job.status = 'ASSIGNED'
        } else if (selected == 'REPORT') {
            if (prior != 'ASSIGNED' || job.workerId != actor) {
                throw new GradleException('BLOCKER: STEP11_PROGRESS_REPORT_OWNER_MISMATCH')
            }
            verifyJobFiles(ctx, job)
            job.reportedHashes = hashes(ctx.module as File, job.files as List<String>)
            job.status = 'REPORTED'
            job.note = safeNote
        } else if (selected in FINISHED) {
            if (prior != 'REPORTED' || !safeNote ||
                    !(job.reportedHashes instanceof Map)) {
                throw new GradleException('BLOCKER: STEP11_PROGRESS_QA_REQUIRES_REPORT_AND_NOTE')
            }
            verifyJobFiles(ctx, job)
            Map nowHashes = hashes(ctx.module as File, job.files as List<String>)
            if (nowHashes != job.reportedHashes) {
                throw new GradleException('BLOCKER: STEP11_PROGRESS_FILES_CHANGED_AFTER_REPORT')
            }
            if (selected == 'SKIP_NO_TRANSLATABLE_TEXT' &&
                    nowHashes != initialHashes(ctx, job.files as List<String>)) {
                throw new GradleException('BLOCKER: STEP11_PROGRESS_SKIP_HAS_CONTENT_CHANGES')
            }
            job.status = selected
            job.note = safeNote
        } else if (selected in ['REVIEW', 'BLOCKER']) {
            if (prior in FINISHED || !safeNote) {
                throw new GradleException('BLOCKER: STEP11_PROGRESS_REVIEW_REQUIRES_REASON')
            }
            job.status = selected
            job.note = safeNote
        } else if (selected == 'REQUEUE') {
            if (!(prior in ['ASSIGNED', 'REPORTED', 'REVIEW', 'BLOCKER']) || !safeNote) {
                throw new GradleException('BLOCKER: STEP11_PROGRESS_REQUEUE_REQUIRES_RECONCILIATION')
            }
            // Prime must verify the previous worker has terminated. Requeue
            // does not alter any EN file or resurrect a stale worker.
            job.status = 'PENDING'
            job.workerId = null
            job.assignedHashes = [:]
            job.reportedHashes = [:]
            job.note = safeNote
        }
        String now = Instant.now().toString()
        job.updatedAt = now
        journal.updatedAt = now
        journal.revision = (journal.revision as int) + 1
        (journal.history as List).add([revision: journal.revision,
                                       jobId: job.id, from: prior, action: selected,
                                       workerId: actor ?: job.workerId, note: safeNote,
                                       at: now])
        save(ctx, journal, ctx.ledgerBytes as byte[])
        return response(journal, ledgerFile, selected)
    }

    private static Map context(File rootDirectory, File moduleDirectory, String runId) {
        File root = rootDirectory.canonicalFile
        File module = moduleDirectory.canonicalFile
        if (!(runId ==~ /[A-Za-z0-9][A-Za-z0-9_-]{0,63}/)) {
            throw new GradleException('Invalid STEP11 progress RUN_ID.')
        }
        Path moduleBase = new File(root, 'module').canonicalFile.toPath()
        if (module.toPath() == moduleBase || !module.toPath().startsWith(moduleBase) ||
                !new File(module, 'gradle.properties').isFile()) {
            throw new GradleException('STEP11 progress requires a real module.')
        }
        String location = root.toPath().relativize(module.toPath()).toString()
                .replace(File.separatorChar, '/' as char)
        File manifestFile = new File(module, 'build/step11/' + runId + '/manifest.json')
        guard(module.toPath(), manifestFile.toPath())
        if (!manifestFile.isFile()) {
            throw new GradleException('Missing STEP11 manifest; PREPARE APPLY first.')
        }
        byte[] manifestBytes = manifestFile.bytes
        Map manifest
        try { manifest = new JsonSlurper().parseText(
                new String(manifestBytes, StandardCharsets.UTF_8)) as Map }
        catch (Exception ex) {
            throw new GradleException('Invalid STEP11 manifest JSON: ' + ex.message, ex)
        }
        if (manifest.module != location || manifest.runId != runId ||
                !(manifest.jobs instanceof List) || !(manifest.files instanceof List) ||
                !(manifest.maxWorkers instanceof Number) ||
                !(manifest.workerMode in ['NORMAL', 'SPECIAL'])) {
            throw new GradleException('STEP11 progress manifest identity/shape mismatch.')
        }
        if (!ModuleTranslationCatalogService.validJobs(
                manifest.jobs, manifest.files as List<Map>)) {
            throw new GradleException('STEP11 progress manifest job ownership mismatch.')
        }
        File agent = new File(root, 'module-generate-agent')
        if (!agent.isDirectory()) {
            throw new GradleException('STEP11 progress needs module-generate-agent folder.')
        }
        File ledgerFile = new File(agent, 'temp/step11/' + location + '/' + runId + '.json')
        guard(agent.toPath(), ledgerFile.toPath())
        return [root: root, module: module, location: location,
                ledger: ledgerFile, manifest: manifest, manifestBytes: manifestBytes]
    }

    private static Map read(Map ctx) {
        File file = ctx.ledger as File
        guard(new File(ctx.root as File, 'module-generate-agent').toPath(), file.toPath())
        if (!file.isFile()) {
            throw new GradleException('STEP11 progress journal missing; ' +
                    'use INIT only before any worker has started: ' + file)
        }
        byte[] raw = file.bytes
        Map journal
        try { journal = new JsonSlurper().parseText(
                new String(raw, StandardCharsets.UTF_8)) as Map }
        catch (Exception ex) {
            throw new GradleException('Invalid STEP11 progress JSON: ' + ex.message, ex)
        }
        Map manifest = ctx.manifest as Map
        if (journal.schemaVersion != VERSION || journal.step != 11 ||
                journal.runId != manifest.runId || journal.module != ctx.location ||
                journal.manifestSha256 != ModuleTranslationCatalogService.sha(
                        ctx.manifestBytes as byte[]) ||
                journal.workerMode != manifest.workerMode ||
                journal.maxWorkers != manifest.maxWorkers ||
                !(journal.revision instanceof Number) ||
                !(journal.jobs instanceof List) ||
                (journal.jobs as List).size() != (manifest.jobs as List).size()) {
            throw new GradleException('BLOCKER: STEP11_PROGRESS_MANIFEST_OR_SCHEMA_DRIFT')
        }
        (journal.jobs as List<Map>).eachWithIndex { Map job, int index ->
            Map original = (manifest.jobs as List<Map>)[index]
            if (job.id != original.id || job.rule != original.rule ||
                    job.files != original.files ||
                    !(job.status in (['PENDING', 'ASSIGNED', 'REPORTED',
                                     'REVIEW', 'BLOCKER'] + FINISHED.toList()))) {
                throw new GradleException('BLOCKER: STEP11_PROGRESS_JOB_DRIFT')
            }
        }
        ctx.ledgerBytes = raw
        return journal
    }

    private static void verifyJobFiles(Map ctx, Map job) {
        Map manifest = ctx.manifest as Map
        (job.files as List<String>).each { String en ->
            Map original = (manifest.files as List<Map>).find { it.enPath == en }
            File module = ctx.module as File
            File vi = new File(module, original.viPath as String)
            File target = new File(module, en)
            File baseline = new File(module, 'build/step11/' + manifest.runId +
                    '/baseline/' + en)
            [vi, target, baseline].each { File file ->
                guard(module.toPath(), file.toPath())
                if (!file.isFile()) throw new GradleException('Missing STEP11 source: ' + file)
            }
            if (ModuleTranslationCatalogService.sha(vi.bytes) != original.viSha256 ||
                    ModuleTranslationCatalogService.sha(baseline.bytes) != original.enSha256) {
                throw new GradleException('BLOCKER: STEP11_PROGRESS_BASELINE_DRIFT: ' + en)
            }
            ModuleTranslationValidationService.compare(original.type as String,
                    vi.bytes, target.bytes)
        }
    }

    private static Map<String, String> hashes(File module, List<String> files) {
        Map<String, String> result = new LinkedHashMap<>()
        files.each { String file ->
            File target = new File(module, file)
            guard(module.toPath(), target.toPath())
            if (!target.isFile()) throw new GradleException('Missing STEP11 job file: ' + target)
            result[file] = ModuleTranslationCatalogService.sha(target.bytes)
        }
        return result
    }

    private static Map<String, String> initialHashes(Map ctx, List<String> files) {
        Map<String, String> result = new LinkedHashMap<>()
        files.each { String path ->
            Map entry = (ctx.manifest.files as List<Map>).find { it.enPath == path }
            result[path] = entry.enSha256 as String
        }
        return result
    }

    private static void ensureComplete(Map ctx, Map journal) {
        List<Map> jobs = journal.jobs as List<Map>
        if (jobs.any { !(it.status in FINISHED) }) {
            throw new GradleException('BLOCKER: STEP11_PROGRESS_INCOMPLETE_JOBS')
        }
        jobs.each { Map job ->
            verifyJobFiles(ctx, job)
            if (hashes(ctx.module as File, job.files as List<String>) != job.reportedHashes) {
                throw new GradleException('BLOCKER: STEP11_PROGRESS_QA_HASH_DRIFT: ' + job.id)
            }
        }
        Map manifest = ctx.manifest as Map
        new ModuleTranslationCatalogService().execute(ctx.root as File, ctx.module as File,
                manifest.runId as String, 'VALIDATE', false,
                manifest.workerMode as String, (manifest.maxWorkers as Number).intValue(),
                (manifest.targetLanguage ?: 'en') as String)
        File master = new File(ctx.module as File, 'master.json')
        if (!master.isFile()) throw new GradleException('STEP11 master.json missing.')
        Map document = new JsonSlurper().parse(master) as Map
        if (document.CURRENT_STEP?.TYPE != 'string' ||
                document.CURRENT_STEP?.VALUE != '11') {
            throw new GradleException('BLOCKER: STEP11_PROGRESS_CHECKPOINT_NOT_READBACK_11')
        }
    }

    private static Map response(Map journal, File path, String result) {
        List<Map> jobs = journal.jobs as List<Map>
        Map<String, Integer> counts = [:].withDefault { 0 }
        jobs.each { Map job -> counts[job.status as String]++ }
        return [result: result, path: path, revision: journal.revision,
                module: journal.module, runId: journal.runId, workerMode: journal.workerMode,
                maxWorkers: journal.maxWorkers, total: jobs.size(),
                qaPass: jobs.count { it.status in FINISHED },
                active: counts.ASSIGNED, pending: counts.PENDING,
                reported: counts.REPORTED, review: counts.REVIEW, blocker: counts.BLOCKER,
                jobs: jobs, recoveryRequired: jobs.findAll {
                    it.status in ['ASSIGNED', 'REPORTED', 'REVIEW', 'BLOCKER']
                }*.id]
    }

    /** Create a complete JSON replacement in the same directory; never truncate in place. */
    private static void save(Map ctx, Map ledger, byte[] expected) {
        File file = ctx.ledger as File
        File agent = new File(ctx.root as File, 'module-generate-agent')
        guard(agent.toPath(), file.toPath())
        Files.createDirectories(file.parentFile.toPath())
        byte[] current = file.isFile() ? file.bytes : null
        boolean unchanged = expected == null ? current == null :
                (current != null && Arrays.equals(expected, current))
        if (!unchanged) {
            throw new GradleException('BLOCKER: STEP11_PROGRESS_CONCURRENT_CHANGE')
        }
        byte[] contents = (JsonOutput.prettyPrint(JsonOutput.toJson(ledger)) + '\n')
                .getBytes(StandardCharsets.UTF_8)
        Path staging = Files.createTempFile(file.parentFile.toPath(),
                '.step11-progress-', '.tmp')
        try {
            Files.write(staging, contents, StandardOpenOption.TRUNCATE_EXISTING)
            Files.move(staging, file.toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(staging)
        }
    }

    private static void cleanupEmptyDirectories(Path start, Path boundary) {
        Path at = start
        while (at != null && at.startsWith(boundary) && at != boundary) {
            try {
                Files.delete(at)
            } catch (java.io.IOException ignored) {
                break
            }
            at = at.parent
        }
    }

    private static void guard(Path base, Path destination) {
        Path root = base.toAbsolutePath().normalize()
        Path target = destination.toAbsolutePath().normalize()
        if (!target.startsWith(root)) throw new GradleException('STEP11 progress path escapes root.')
        Path check = root
        for (Path name : root.relativize(target)) {
            check = check.resolve(name)
            if (Files.isSymbolicLink(check)) {
                throw new GradleException('BLOCKER: STEP11_PROGRESS_SYMLINK: ' + check)
            }
        }
    }
}
