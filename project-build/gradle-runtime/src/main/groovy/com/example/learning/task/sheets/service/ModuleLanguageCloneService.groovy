package com.example.learning.task.sheets.service

import org.gradle.api.GradleException

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * STEP 12A. The final VI module tree is authoritative; EN is a translated-later
 * structural clone. All validation happens before any EN subtree is moved.
 * Old EN subtrees are saved under module/build/step12a-backups/ for recovery.
 */
class ModuleLanguageCloneService {
    static final List<String> OWNED = [
            'content/readme/docs', 'content/roadmap',
            'src/main/resources/swagger'
    ].asImmutable()

    Map execute(File repositoryRoot, File moduleDirectory, boolean apply) {
        File root = repositoryRoot.canonicalFile
        File module = moduleDirectory.canonicalFile
        File modulesRoot = new File(root, 'module').canonicalFile
        if (module == modulesRoot || !module.toPath().startsWith(modulesRoot.toPath()) ||
                !module.isDirectory() || !new File(module, 'gradle.properties').isFile()) {
            throw new GradleException('STEP 12A requires a real Gradle module within module/.')
        }
        String location = root.toPath().relativize(module.toPath())
                .toString().replace(File.separatorChar, '/' as char)
        RoadmapSheetImportService.resolveDestination(root, location)
        Map<String, Map<String, byte[]>> expected = new LinkedHashMap<>()
        Map<String, Map<String, byte[]>> before = new LinkedHashMap<>()
        OWNED.each { String area ->
            File vi = new File(module, area + '/vi')
            File en = new File(module, area + '/en')
            ModuleLanguageCloneService.checkPaths(module, vi, en)
            // Video is authored after STEP 15. Preserve existing EN Video even
            // when mandatory STEP 12A is re-run from a newer VI tree.
            expected[area] = ModuleLanguageCloneService.sourceTree(module, area)
            before[area] = ModuleLanguageCloneService.readTree(en)
        }
        requireSource(expected)
        ModuleLanguageCloneValidationService.validate(expected, root)

        int total = expected.values().sum { Map tree -> tree.size() } as int
        List<String> changes = OWNED.findAll { String key ->
            !ModuleLanguageCloneService.equalTrees(before[key], expected[key])
        }
        Map result = [status: changes.isEmpty() ? 'UNCHANGED' : apply ? 'CLONED' : 'DRY_RUN',
                      module: location, groups: OWNED.size(), files: total, changes: changes,
                      backup: null]
        if (!apply || changes.isEmpty()) return result

        // Preserve the original bytes read during preflight and abort on concurrent changes.
        OWNED.each { String key ->
            if (!ModuleLanguageCloneService.equalTrees(before[key], ModuleLanguageCloneService.readTree(new File(module, key + '/en'))) ||
                    !ModuleLanguageCloneService.equalTrees(expected[key], ModuleLanguageCloneService.sourceTree(module, key))) {
                throw new GradleException('VI or EN changed since STEP 12A preflight: ' + key)
            }
        }

        String runId = UUID.randomUUID().toString()
        File backupRoot = new File(module, 'build/step12a-backups/' + runId)
        List<Map> operations = []
        try {
            // Stage *all* new EN data before moving any current target.
            changes.eachWithIndex { String key, int index ->
                File target = new File(module, key + '/en')
                Files.createDirectories(target.parentFile.toPath())
                // Use the Git-ignored module/build/ area: interrupted staging
                // must never accidentally appear as commit-ready content.
                File staged = new File(module, 'build/step12a-staging/' + runId +
                        '/' + String.format('%02d', index) + '/' + key + '/en')
                File backup = new File(backupRoot, String.format('%02d', index) + '/' + key + '/en')
                Map record = [key: key, target: target, stage: staged,
                              backup: backup, hadOld: target.exists(),
                              movedOld: false, movedNew: false]
                operations.add(record)
                if (!expected[key].isEmpty()) {
                    Files.createDirectories(staged.toPath())
                    ModuleLanguageCloneService.writeTree(staged, expected[key])
                    if (!ModuleLanguageCloneService.equalTrees(expected[key], ModuleLanguageCloneService.readTree(staged))) {
                        throw new GradleException('Failed to verify staged EN: ' + key)
                    }
                }
            }
            OWNED.each { String key ->
                if (!ModuleLanguageCloneService.equalTrees(before[key], ModuleLanguageCloneService.readTree(new File(module, key + '/en'))) ||
                        !ModuleLanguageCloneService.equalTrees(expected[key], ModuleLanguageCloneService.sourceTree(module, key))) {
                    throw new GradleException('Concurrent VI/EN change before commit: ' + key)
                }
            }
            int completed = 0
            operations.each { Map op ->
                if (op.hadOld) {
                    Files.createDirectories((op.backup as File).parentFile.toPath())
                    Files.move((op.target as File).toPath(), (op.backup as File).toPath())
                    op.movedOld = true
                }
                if (!expected[op.key].isEmpty()) {
                    Files.move((op.stage as File).toPath(), (op.target as File).toPath())
                    op.movedNew = true
                }
                afterReplacement(++completed)
            }
            OWNED.each { String key ->
                if (!ModuleLanguageCloneService.equalTrees(expected[key], ModuleLanguageCloneService.readTree(new File(module, key + '/en')))) {
                    throw new GradleException('EN readback differs from VI clone: ' + key)
                }
            }
            result.backup = operations.any { it.hadOld }
                    ? backupRoot.absolutePath : null
            return result
        } catch (Exception failure) {
            List<String> problems = []
            operations.reverseEach { Map op ->
                try {
                    if (op.movedNew) ModuleLanguageCloneService.removeTree(op.target as File)
                    if (op.movedOld) {
                        Files.createDirectories((op.target as File).parentFile.toPath())
                        Files.move((op.backup as File).toPath(), (op.target as File).toPath())
                    }
                } catch (Exception rollbackError) {
                    problems.add(op.key + ': ' + rollbackError.message)
                }
            }
            throw new GradleException('STEP 12A aborted: ' + failure.message +
                    (problems ? '. ROLLBACK INCOMPLETE: ' + problems.join('; ') +
                            ', inspect ' + backupRoot.absolutePath : '. Prior EN restored.'), failure)
        } finally {
            operations.each { Map op ->
                // A staged directory can be safely removed only if it was never committed.
                File staged = op.stage as File
                if (staged.exists()) ModuleLanguageCloneService.removeTree(staged)
            }
        }
    }

    /** Test seam for rollback failure injection; does not change normal behavior. */
    protected void afterReplacement(int completed) { }

    private static Map<String, byte[]> sourceTree(File module, String key) {
        boolean docs = key == 'content/readme/docs'
        Map<String, byte[]> actual = readTree(new File(module, key + '/vi'), docs)
        if (!docs || actual.isEmpty() || !actual.containsKey('LIST.md')) return actual
        String original = new String(actual['LIST.md'], StandardCharsets.UTF_8)
                .replace('content/readme/docs/vi/menu/', 'content/readme/docs/en/menu/')
                .replace('README MODULE STRUCTURE (VI)', 'README MODULE STRUCTURE (EN)')
        actual['LIST.md'] = original.getBytes(StandardCharsets.UTF_8)
        readTree(new File(module, key + '/en')).each { String path, byte[] bytes ->
            if (videoPath(path)) actual[path] = bytes
        }
        return actual
    }

    private static boolean videoPath(String relative) {
        relative ==~ /menu\/[^\/]+\/video(?:\/.*)?/
    }

    private static void requireSource(Map<String, Map<String, byte[]>> trees) {
        Map docs = trees['content/readme/docs']
        if (!docs.containsKey('BASE.md') || !docs.containsKey('LIST.md') ||
                !docs.keySet().any { it ==~ /menu\/[^\/]+\.md/ ||
                        it ==~ /menu\/[^\/]+\/knowledge\/[^\/]+\.md/ }) {
            throw new GradleException('Incomplete VI README/metadata; refusing to replace EN.')
        }
    }

    private static void checkPaths(File module, File vi, File en) {
        [vi, en].each { File candidate ->
            Path modulePath = module.toPath()
            Path selected = candidate.toPath().toAbsolutePath().normalize()
            if (!selected.startsWith(modulePath)) {
                throw new GradleException('Unsafe language subtree: ' + candidate)
            }
            Path current = modulePath
            for (Path segment : modulePath.relativize(selected)) {
                current = current.resolve(segment)
                if (Files.isSymbolicLink(current)) {
                    throw new GradleException('Refusing linked language path: ' + current)
                }
            }
        }
    }

    static Map<String, byte[]> readTree(File folder, boolean excludeVideo = false) {
        if (!folder.exists()) return new LinkedHashMap<>()
        if (!folder.isDirectory() || Files.isSymbolicLink(folder.toPath())) {
            throw new GradleException('Unsafe language directory: ' + folder)
        }
        Path root = folder.canonicalFile.toPath()
        Map<String, byte[]> result = new LinkedHashMap<>()
        def walker = Files.walk(folder.toPath())
        try {
            walker.forEach { Path file ->
                String relative = folder.toPath().relativize(file).toString()
                        .replace(File.separatorChar, '/' as char)
                if (excludeVideo && videoPath(relative)) return
                if (Files.isSymbolicLink(file)) {
                    throw new GradleException('Symlink inside language tree: ' + file)
                }
                if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    if (!file.toFile().canonicalFile.toPath().startsWith(root)) {
                        throw new GradleException('Language file escaped module: ' + file)
                    }
                    result[relative] = Files.readAllBytes(file)
                }
            }
        } finally { walker.close() }
        return result
    }

    static boolean equalTrees(Map<String, byte[]> left, Map<String, byte[]> right) {
        if (left.keySet() != right.keySet()) return false
        return left.every { String path, byte[] bytes -> Arrays.equals(bytes, right[path]) }
    }

    private static void writeTree(File root, Map<String, byte[]> content) {
        content.each { String relative, byte[] bytes ->
            if (!relative || relative.contains('\\') || relative.startsWith('/') ||
                    relative.split('/').any { it in ['', '.', '..'] }) {
                throw new GradleException('Unsafe relative language filename: ' + relative)
            }
            File output = new File(root, relative)
            if (!output.canonicalFile.toPath().startsWith(root.canonicalFile.toPath())) {
                throw new GradleException('Staging escape: ' + relative)
            }
            Files.createDirectories(output.parentFile.toPath())
            Files.write(output.toPath(), bytes)
        }
    }

    private static void removeTree(File folder) {
        if (!folder.exists()) return
        if (Files.isSymbolicLink(folder.toPath())) {
            throw new GradleException('Refusing symlink delete: ' + folder)
        }
        def walker = Files.walk(folder.toPath())
        try {
            walker.sorted(Comparator.reverseOrder()).forEach { Path p ->
                if (Files.isSymbolicLink(p)) {
                    throw new GradleException('Refusing symlink delete: ' + p)
                }
                Files.delete(p)
            }
        } finally { walker.close() }
    }
}
