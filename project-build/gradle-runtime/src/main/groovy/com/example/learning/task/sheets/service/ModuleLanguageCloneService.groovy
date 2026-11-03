package com.example.learning.task.sheets.service

import org.gradle.api.GradleException

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * STEP 10. Clone all module-owned VI subtrees to EN with CLEAN -> FILL.
 * Temporary backup/staging is deleted after successful verification.
 */
class ModuleLanguageCloneService {
    static final List<String> OWNED = [
            'content/readme/docs', 'content/roadmap', 'content/reference',
            'src/main/resources/swagger'
    ].asImmutable()

    Map execute(File repositoryRoot, File moduleDirectory, boolean apply) {
        File root = repositoryRoot.canonicalFile
        File module = moduleDirectory.canonicalFile
        File modulesRoot = new File(root, 'module').canonicalFile
        if (module == modulesRoot || !module.toPath().startsWith(modulesRoot.toPath()) ||
                !module.isDirectory() || !new File(module, 'gradle.properties').isFile()) {
            throw new GradleException('STEP 10 requires a real Gradle module within module/.')
        }
        String location = root.toPath().relativize(module.toPath())
                .toString().replace(File.separatorChar, '/' as char)
        RoadmapSheetImportService.resolveDestination(root, location)
        List<String> areas = discoverAreas(module)
        Map<String, Map<String, byte[]>> expected = new LinkedHashMap<>()
        Map<String, Map<String, byte[]>> before = new LinkedHashMap<>()
        areas.each { String area ->
            File vi = new File(module, area + '/vi')
            File en = new File(module, area + '/en')
            ModuleLanguageCloneService.checkPaths(module, vi, en)
            expected[area] = ModuleLanguageCloneService.sourceTree(module, area)
            before[area] = ModuleLanguageCloneService.readTree(en)
        }
        requireSource(expected)
        ModuleLanguageCloneValidationService.validate(expected, root)

        int total = expected.values().sum { Map tree -> tree.size() } as int
        List<String> changes = areas.findAll { String key ->
            !new File(module, key + '/en').isDirectory() ||
                    !ModuleLanguageCloneService.equalTrees(before[key], expected[key])
        }
        Map result = [status: changes.isEmpty() ? 'UNCHANGED' : apply ? 'CLONED' : 'DRY_RUN',
                      module: location, groups: areas.size(), files: total,
                      changes: changes, backup: null,
                      filesByScope: expected.collectEntries { String key, Map tree ->
                          [(key): tree.size()]
                      },
                      enOnlyRemoved: areas.sum { String key ->
                          before[key].keySet().count { !expected[key].containsKey(it) }
                      } ?: 0]
        if (!apply || changes.isEmpty()) return result

        // Preserve the original bytes read during preflight and abort on concurrent changes.
        areas.each { String key ->
            if (!ModuleLanguageCloneService.equalTrees(before[key], ModuleLanguageCloneService.readTree(new File(module, key + '/en'))) ||
                    !ModuleLanguageCloneService.equalTrees(expected[key], ModuleLanguageCloneService.sourceTree(module, key))) {
                throw new GradleException('VI or EN changed since STEP 10 preflight: ' + key)
            }
        }

        String runId = UUID.randomUUID().toString()
        File backupRoot = new File(module, 'build/step10-backups/' + runId)
        File stagingRoot = new File(module, 'build/step10-staging/' + runId)
        List<Map> operations = []
        boolean committed = false
        boolean restored = false
        try {
            // Stage *all* new EN data before moving any current target.
            changes.eachWithIndex { String key, int index ->
                File target = new File(module, key + '/en')
                Files.createDirectories(target.parentFile.toPath())
                // Use the Git-ignored module/build/ area: interrupted staging
                // must never accidentally appear as commit-ready content.
                File staged = new File(stagingRoot,
                        String.format('%02d', index) + '/' + key + '/en')
                File backup = new File(backupRoot, String.format('%02d', index) + '/' + key + '/en')
                Map record = [key: key, target: target, stage: staged,
                              backup: backup, hadOld: target.exists(),
                              movedOld: false, movedNew: false]
                operations.add(record)
                Files.createDirectories(staged.toPath())
                ModuleLanguageCloneService.writeTree(staged, expected[key])
                if (!ModuleLanguageCloneService.equalTrees(expected[key], ModuleLanguageCloneService.readTree(staged))) {
                    throw new GradleException('Failed to verify staged EN: ' + key)
                }
            }
            areas.each { String key ->
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
                Files.move((op.stage as File).toPath(), (op.target as File).toPath())
                op.movedNew = true
                afterReplacement(++completed)
            }
            areas.each { String key ->
                File dest = new File(module, key + '/en')
                if (!dest.isDirectory() ||
                        !ModuleLanguageCloneService.equalTrees(expected[key], ModuleLanguageCloneService.readTree(dest))) {
                    throw new GradleException('EN readback differs from VI clone: ' + key)
                }
            }
            // The backup is transaction-local, not an artifact of a successful clone.
            committed = true
            result.temporaryCleanup = 'PASS'
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
            restored = problems.isEmpty()
            throw new GradleException('STEP 10 aborted: ' + failure.message +
                    (problems ? '. ROLLBACK INCOMPLETE: ' + problems.join('; ') +
                            ', inspect ' + backupRoot.absolutePath : '. Prior EN restored.'), failure)
        } finally {
            // On success or a complete rollback, no backup/staging may remain.
            // A failed rollback must retain its recovery files.
            if (committed || restored) {
                try {
                    if (stagingRoot.exists()) ModuleLanguageCloneService.removeTree(stagingRoot)
                    if (backupRoot.exists()) ModuleLanguageCloneService.removeTree(backupRoot)
                    // Remove empty container directories; preserve other run IDs.
                    [stagingRoot.parentFile, backupRoot.parentFile].each { File parent ->
                        if (parent.isDirectory() && !(parent.list()?.length)) {
                            Files.delete(parent.toPath())
                        }
                    }
                    if (stagingRoot.exists() || backupRoot.exists()) {
                        throw new GradleException('Temporary clone files still exist.')
                    }
                } catch (Exception cleanupFailure) {
                    throw new GradleException('STEP 10 temporary cleanup failed: ' +
                            cleanupFailure.message, cleanupFailure)
                }
            }
        }
    }

    /** Test seam for rollback failure injection; does not change normal behavior. */
    protected void afterReplacement(int completed) { }

    /** Discover every canonical VI subtree; never traverse content/sheet or build. */
    static List<String> discoverAreas(File module) {
        File content = new File(module, 'content')
        if (!content.isDirectory() || Files.isSymbolicLink(content.toPath())) {
            throw new GradleException('Missing or unsafe module content/: ' + content)
        }
        Set<String> areas = new TreeSet<>()
        def walker = Files.walk(content.toPath())
        try {
            walker.forEach { Path path ->
                Path relative = content.toPath().relativize(path)
                if (relative.nameCount == 0) return
                String first = relative.getName(0).toString()
                if (first == 'sheet') return
                if (Files.isSymbolicLink(path)) {
                    throw new GradleException('Symlink inside content/: ' + path)
                }
                if (path.fileName.toString() == 'vi' &&
                        Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    String parent = module.toPath().relativize(path.parent)
                            .toString().replace(File.separatorChar, '/' as char)
                    areas.add(parent)
                }
            }
        } finally { walker.close() }
        File swagger = new File(module, 'src/main/resources/swagger/vi')
        if (swagger.exists()) {
            if (!swagger.isDirectory() || Files.isSymbolicLink(swagger.toPath())) {
                throw new GradleException('Unsafe VI Swagger directory: ' + swagger)
            }
            areas.add('src/main/resources/swagger')
        }
        if (!areas.contains('content/readme/docs')) {
            throw new GradleException('Missing canonical VI README docs subtree.')
        }
        return new ArrayList<String>(areas)
    }

    private static Map<String, byte[]> sourceTree(File module, String key) {
        boolean docs = key == 'content/readme/docs'
        Map<String, byte[]> actual = readTree(new File(module, key + '/vi'))
        if (!docs || actual.isEmpty() || !actual.containsKey('LIST.md')) return actual
        String original = new String(actual['LIST.md'], StandardCharsets.UTF_8)
                .replace('content/readme/docs/vi/menu/', 'content/readme/docs/en/menu/')
                .replace('README MODULE STRUCTURE (VI)', 'README MODULE STRUCTURE (EN)')
        actual['LIST.md'] = original.getBytes(StandardCharsets.UTF_8)
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
