package com.example.learning.task.sheets.service

import groovy.json.JsonSlurper
import org.gradle.api.GradleException

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * STEP 11: publish exactly ONE whole, translated temp file back into its
 * target-language module path. The worker never needs to edit or even inspect
 * the module directly. This is file delivery, not text extraction/merging.
 *
 * Prime alone owns ASSIGN/REPORT/QA and all progress journal mutations.
 */
class ModuleTranslationFileDeliveryService {
    Map execute(File rootDirectory, File moduleDirectory, String runId,
                String targetLanguage, String targetPath, String workerId,
                boolean apply) {
        File root = rootDirectory.canonicalFile
        File module = moduleDirectory.canonicalFile
        Path modules = new File(root, 'module').canonicalFile.toPath()
        if (!module.toPath().startsWith(modules) || module.toPath() == modules ||
                !new File(module, 'gradle.properties').isFile()) {
            throw new GradleException('STEP11 file delivery requires one real module.')
        }
        if (!(runId ==~ /[A-Za-z0-9][A-Za-z0-9_-]{0,63}/) ||
                !(workerId ==~ /[A-Za-z0-9][A-Za-z0-9_.-]{0,99}/)) {
            throw new GradleException('BLOCKER: STEP11_DELIVERY_INVALID_RUN_OR_WORKER')
        }
        String language = targetLanguage?.trim()?.toLowerCase(Locale.ROOT)
        String relative = targetPath?.replace('\\', '/')?.trim()
        if (!(language ==~ /[a-z]{2,8}(?:-[a-z0-9]{2,8})?/) ||
                language == 'vi' || !relative || relative.startsWith('/') ||
                relative.contains('..') || relative.contains(':') ||
                relative.contains('//')) {
            throw new GradleException('BLOCKER: STEP11_DELIVERY_INVALID_TARGET')
        }
        String location = root.toPath().relativize(module.toPath()).toString()
                .replace(File.separatorChar, '/' as char)
        File manifestFile = new File(module, 'build/step11/' + runId + '/manifest.json')
        guard(module.toPath(), manifestFile.toPath())
        if (!manifestFile.isFile()) throw new GradleException('Missing STEP11 manifest.')
        Map manifest = new JsonSlurper().parse(manifestFile, 'UTF-8') as Map
        if (manifest.runId != runId || manifest.module != location ||
                (manifest.targetLanguage ?: 'en') != language ||
                !(manifest.files instanceof List)) {
            throw new GradleException('BLOCKER: STEP11_DELIVERY_MANIFEST_MISMATCH')
        }
        Map entry = (manifest.files as List<Map>).find { it.enPath == relative }
        Map job = (manifest.jobs as List<Map>).find {
            it.files == [relative] && it.id == relative
        }
        if (entry == null || job == null ||
                !relative.contains('/' + language + '/')) {
            throw new GradleException('BLOCKER: STEP11_DELIVERY_NOT_SINGLE_FILE_JOB: ' +
                    relative)
        }
        Map progress = new ModuleTranslationProgressService().execute(
                root, module, runId, 'SHOW')
        Map ownership = (progress.jobs as List<Map>).find { it.id == relative }
        if (ownership?.status != 'ASSIGNED' || ownership.workerId != workerId ||
                !(ownership.assignedHashes instanceof Map)) {
            throw new GradleException('BLOCKER: STEP11_DELIVERY_OWNERSHIP_NOT_ASSIGNED: ' +
                    relative)
        }
        String expectedTargetHash = ownership.assignedHashes[relative] as String
        if (!(expectedTargetHash ==~ /[a-f0-9]{64}/)) {
            throw new GradleException('BLOCKER: STEP11_DELIVERY_ASSIGN_HASH_MISSING')
        }
        File work = ModuleTranslationCatalogService.workFile(
                root, location, runId, relative)
        File target = new File(module, relative)
        File vi = new File(module, entry.viPath as String)
        File baseline = new File(module, 'build/step11/' + runId +
                '/baseline/' + relative)
        [target, vi, baseline].each { File file ->
            guard(module.toPath(), file.toPath())
            if (!Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                throw new GradleException('BLOCKER: STEP11_DELIVERY_MISSING_MODULE_FILE: ' + file)
            }
        }
        if (!Files.isRegularFile(work.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            throw new GradleException('BLOCKER: STEP11_DELIVERY_TEMP_FILE_MISSING: ' + work)
        }
        byte[] original = vi.bytes
        byte[] staged = work.bytes
        byte[] current = target.bytes
        String currentHash = ModuleTranslationCatalogService.sha(current)
        String translatedHash = ModuleTranslationCatalogService.sha(staged)
        if (ModuleTranslationCatalogService.sha(original) != entry.viSha256 ||
                ModuleTranslationCatalogService.sha(baseline.bytes) != entry.enSha256) {
            throw new GradleException('BLOCKER: STEP11_DELIVERY_BASELINE_CHANGED')
        }
        ModuleTranslationValidationService.compare(entry.type as String,
                original, staged)
        if (currentHash != expectedTargetHash && currentHash != translatedHash) {
            throw new GradleException('BLOCKER: STEP11_DELIVERY_TARGET_CHANGED: ' +
                    relative)
        }
        if (!apply) {
            return [status: 'DRY_RUN_VALIDATED', target: target,
                    temp: work, beforeSha256: currentHash,
                    afterSha256: translatedHash, language: language]
        }
        if (currentHash != translatedHash) {
            // Recheck before replacing: a previously assigned worker must not
            // silently overwrite a different edit made after its ASSIGN.
            if (ModuleTranslationCatalogService.sha(target.bytes) != expectedTargetHash) {
                throw new GradleException('BLOCKER: STEP11_DELIVERY_CONCURRENT_TARGET_CHANGE')
            }
            Path stagedFile = Files.createTempFile(target.parentFile.toPath(),
                    '.step11-file-', '.tmp')
            try {
                Files.write(stagedFile, staged, StandardOpenOption.TRUNCATE_EXISTING)
                try {
                    Files.move(stagedFile, target.toPath(),
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING)
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(stagedFile, target.toPath(),
                            StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                Files.deleteIfExists(stagedFile)
            }
        }
        if (ModuleTranslationCatalogService.sha(target.bytes) != translatedHash) {
            throw new GradleException('BLOCKER: STEP11_DELIVERY_READBACK_MISMATCH')
        }
        Files.delete(work.toPath())
        cleanupEmpty(work.parentFile.toPath(),
                new File(root, 'module-generate-agent/temp/step11/files').toPath())
        return [status: currentHash == translatedHash ?
                'ALREADY_PUBLISHED_AND_CLEANED' : 'PUBLISHED_AND_CLEANED',
                target: target, temp: work, beforeSha256: currentHash,
                afterSha256: translatedHash, language: language]
    }

    private static void guard(Path base, Path destination) {
        Path root = base.toAbsolutePath().normalize()
        Path path = destination.toAbsolutePath().normalize()
        if (!path.startsWith(root)) throw new GradleException('STEP11 delivery path escapes root.')
        Path child = root
        for (Path part : root.relativize(path)) {
            child = child.resolve(part)
            if (Files.isSymbolicLink(child)) {
                throw new GradleException('BLOCKER: STEP11_DELIVERY_SYMLINK: ' + child)
            }
        }
    }

    private static void cleanupEmpty(Path at, Path boundary) {
        Path limit = boundary.toAbsolutePath().normalize()
        while (at != null && at.toAbsolutePath().normalize().startsWith(limit) &&
                at.toAbsolutePath().normalize() != limit) {
            try {
                Files.delete(at)
            } catch (IOException ignored) {
                break
            }
            at = at.parent
        }
    }
}
