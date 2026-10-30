package com.example.learning.setup.root.readmeMetadata.service

import com.example.learning.setup.module.readme.service.ReadmeKnowledgeParser
import com.example.learning.utils.GradleBuildUtils
import com.example.learning.utils.ProjectPropertyUtils
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml

import java.nio.file.Files
import java.nio.file.StandardCopyOption


class ReadmeMetadataSyncService {

    static final String METADATA_FILE_NAME =
            'knowledge-metadata.yml'


    static final String DEFAULT_DIFFICULTY =
            'BASIC'



    static final Set<String> ALLOWED_DIFFICULTIES = [
            'BASIC',
            'INTERMEDIATE',
            'ADVANCED'
    ] as Set<String>


    private final Logger logger
    private final ReadmeKnowledgeParser parser
    private final Yaml reader
    private final Yaml writer


    ReadmeMetadataSyncService(
            Logger logger
    ) {

        this.logger =
                logger


        this.parser =
                new ReadmeKnowledgeParser(
                        logger
                )


        DumperOptions options =
                new DumperOptions()


        options.setDefaultFlowStyle(
                DumperOptions.FlowStyle.BLOCK
        )


        options.setPrettyFlow(
                true
        )


        options.setIndent(
                2
        )


        this.reader =
                new Yaml()


        this.writer =
                new Yaml(
                        options
                )
    }


    void sync(
            Project rootProject
    ) {

        File moduleRoot =
                new File(
                        rootProject.projectDir,
                        'module'
                )


        if (!moduleRoot.isDirectory()) {

            throw new GradleException(
                    """
Project module directory was not found:

${moduleRoot.absolutePath}
""".stripIndent()
            )
        }


        List<WritePlan> plans = []


        resolveRealModuleProjects(
                rootProject,
                moduleRoot
        ).each {
            Project moduleProject ->


                resolveLanguageSources(
                        moduleProject
                ).each {
                    LanguageSource languageSource ->


                        plans.addAll(
                                buildChapterWritePlans(
                                        moduleProject,
                                        languageSource
                                )
                        )
                }
        }


        int changedFiles = 0


        plans.each {
            WritePlan plan ->


                if (
                        writeSafelyIfChanged(
                                plan.outputFile,
                                plan.content
                        )
                ) {

                    changedFiles++
                }
        }


        logger.lifecycle(
                '🧩 [README-METADATA] Synced {} chapter metadata files ({} changed).',
                plans.size(),
                changedFiles
        )
    }

    /**
     * Prepare metadata for a Sheet README snapshot before replacing its files.
     * Sheet is the only source for imported metadata. Derive fields from the
     * new Knowledge/Sheet values and deterministic defaults, never from files
     * left in the module (including any legacy metadata).
     */
    String prepareImportedMetadata(File moduleDirectory,
                                   Map<String, String> documents,
                                   Map<String, List<String>> difficulties) {
        Map next = new LinkedHashMap()
        Set<String> allIds = new LinkedHashSet<>()
        File menuDirectory = new File(moduleDirectory, 'content/readme/docs/vi/menu')
        documents.each { String sourcePath, String markdown ->
            String knowledgePath = normalizeKnowledgePath(sourcePath)
            if (next.containsKey(knowledgePath)) {
                throw new GradleException('Duplicate imported Knowledge path: ' + knowledgePath)
            }
            Map nextFile = new LinkedHashMap()
            File virtualFile = new File(menuDirectory, knowledgePath)
            if (sourcePath.startsWith('/') || sourcePath.contains('\\') ||
                    !virtualFile.canonicalFile.toPath().startsWith(menuDirectory.canonicalFile.toPath())) {
                throw new GradleException('Unsafe Knowledge source path: ' + sourcePath)
            }
            List<ReadmeKnowledgeParser.SectionCandidate> sections = parser.parse(virtualFile, markdown)
                    .findAll { it.valid }
            List<String> levels = difficulties[sourcePath] ?: []
            if (levels.size() != sections.size()) {
                throw new GradleException("Knowledge Difficulty/ID count mismatch: ${sourcePath}")
            }
            sections.eachWithIndex { ReadmeKnowledgeParser.SectionCandidate section, int i ->
                if (!allIds.add(section.id)) {
                    throw new GradleException('Duplicate Knowledge ID: ' + section.id)
                }
                Map item = new LinkedHashMap()
                String fromSheet = levels[i]?.trim()?.toUpperCase(Locale.ROOT)
                if (fromSheet) {
                    if (!ALLOWED_DIFFICULTIES.contains(fromSheet)) {
                        throw new GradleException("Invalid Sheet Knowledge Difficulty at ${sourcePath}: ${fromSheet}")
                    }
                    item.difficulty = fromSheet
                }
                // Blank O always uses the defined BASIC default.
                ensureRequiredFields(item, moduleDirectory.absolutePath, 'vi',
                        sourcePath, section.id)
                nextFile[section.id] = item
            }
            if (nextFile) next[knowledgePath] = nextFile
        }
        return next.isEmpty() ? '{}\n' : writer.dump(next)
    }

    /** Convert prepared entries into complete new metadata/ directory files. */
    Map<String, String> importedChapterMetadataFiles(String prepared) {
        Object parsed = reader.load(prepared)
        if (!(parsed instanceof Map)) {
            throw new GradleException('Invalid prepared Sheet Metadata.')
        }
        Map<String, Map> perChapter = new LinkedHashMap<>()
        (parsed as Map).each { Object sourcePath, Object entries ->
            String path = normalizeKnowledgePath(sourcePath as String)
            String chapter = path.split('/')[0]
            Map sections = perChapter.computeIfAbsent(chapter) { new LinkedHashMap() }
            Map values = resolveOptionalMap(entries,
                    'Invalid prepared Sheet Metadata: ' + path) ?: [:]
            values.each { Object id, Object fields ->
                if (sections.containsKey(id)) {
                    throw new GradleException('Duplicate Sheet Metadata ID in ' + chapter + ': ' + id)
                }
                sections[id] = fields
            }
        }
        Map<String, String> files = new LinkedHashMap<>()
        perChapter.each { String chapter, Map entries ->
            files[chapter + '/metadata/' + METADATA_FILE_NAME] = writer.dump([sections: entries])
        }
        return files
    }

    /** Call only after the corresponding README snapshot was applied. */
    Map syncImportedMetadata(File moduleDirectory, String prepared, boolean apply) {
        Object parsed = reader.load(prepared)
        if (!(parsed instanceof Map)) {
            throw new GradleException('Invalid prepared README Knowledge metadata.')
        }
        File menuDirectory = new File(moduleDirectory, 'content/readme/docs/vi/menu')
        Map<String, Map> perChapter = new LinkedHashMap<>()
        (parsed as Map).each { Object path, Object entries ->
            String normalized = normalizeKnowledgePath(path as String)
            String chapter = normalized.split('/')[0]
            Map result = perChapter.computeIfAbsent(chapter) { new LinkedHashMap() }
            Map source = resolveOptionalMap(entries,
                    'Invalid imported Knowledge metadata: ' + normalized) ?: [:]
            source.each { Object id, Object metadata ->
                if (result.containsKey(id) && result[id] != metadata) {
                    throw new GradleException(
                            'Conflicting imported Knowledge anchor in chapter ' + chapter + ': ' + id)
                }
                result[id] = metadata
            }
        }
        List<WritePlan> plans = perChapter.collect { String chapter, Map entries ->
            File target = chapterMetadataFile(menuDirectory, chapter)
            return new WritePlan(target, writer.dump([sections: entries]))
        }
        if (plans.isEmpty()) {
            return [status: 'UNCHANGED', file: null, files: []]
        }
        boolean changed = plans.any { WritePlan plan ->
            !plan.outputFile.isFile() || plan.outputFile.getText('UTF-8') != plan.content
        }
        if (!changed) return [status: 'UNCHANGED', file: plans.first().outputFile,
                              files: plans*.outputFile]
        if (!apply) return [status: 'DRY_RUN', file: plans.first().outputFile,
                            files: plans*.outputFile]
        int count = plans.count { WritePlan plan ->
            writeSafelyIfChanged(plan.outputFile, plan.content)
        }
        return [status: count ? 'UPDATED' : 'UNCHANGED',
                file: plans.first().outputFile, files: plans*.outputFile]
    }

    private static File chapterMetadataFile(File menuDirectory, String chapter) {
        if (!(chapter ==~ /\d+\..+/) || chapter.contains('/') ||
                chapter.contains('\\') || chapter in ['.', '..']) {
            throw new GradleException('Unsafe Knowledge chapter: ' + chapter)
        }
        File target = new File(menuDirectory, chapter + '/metadata/' + METADATA_FILE_NAME)
        if (!target.canonicalFile.toPath().startsWith(menuDirectory.canonicalFile.toPath())) {
            throw new GradleException('Knowledge metadata target escapes menu: ' + target)
        }
        return target
    }


    /**
     * Each chapter owns a sections: {anchor-ID: fields} map under metadata/.
     * Only menu/<chapter>/knowledge/*.md contributes Knowledge sections.
     */
    private List<WritePlan> buildChapterWritePlans(
            Project moduleProject, LanguageSource source
    ) {
        File legacyFile = new File(moduleProject.projectDir,
                'content/readme/metadata/' + source.language + '/' + METADATA_FILE_NAME)
        Map legacy = loadMap(legacyFile)
        Map<String, List<File>> chapters = collectMarkdownFiles(source.menuDirectory)
                .groupBy { File file -> file.parentFile.parentFile.name }
        List<WritePlan> plans = []
        Set<String> globalIds = new LinkedHashSet<>()

        chapters.each { String chapter, List<File> articles ->
            File target = chapterMetadataFile(source.menuDirectory, chapter)
            Map existing = loadChapterSections(target)
            Map next = new LinkedHashMap(existing)
            Set<String> chapterIds = new LinkedHashSet<>()
            articles.each { File article ->
                String sourcePath = relativePath(source.menuDirectory, article)
                Map fromLegacy = existingKnowledgeMetadata(legacy, sourcePath,
                        source.menuDirectory)
                parser.parse(article).findAll { it.valid }.each { section ->
                    String id = section.id
                    if (!chapterIds.add(id) || !globalIds.add(id)) {
                        throw new GradleException('Duplicate Knowledge anchor: ' + id +
                                ' in ' + sourcePath)
                    }
                    Map old = resolveOptionalMap(fromLegacy[id],
                            'Invalid legacy Knowledge metadata: ' + sourcePath + '#' + id) ?: [:]
                    Map current = resolveOptionalMap(existing[id],
                            'Invalid chapter Knowledge metadata: ' + sourcePath + '#' + id) ?: [:]
                    Map merged = new LinkedHashMap(old)
                    merged.putAll(current)
                    ensureRequiredFields(merged, moduleProject, source.language, sourcePath, id)
                    next[id] = merged
                }
            }
            if (!next.isEmpty()) {
                plans.add(new WritePlan(target, writer.dump([sections: next])))
            }
        }
        return plans
    }


    private static void ensureRequiredFields(
            Map<String, Object> section,
            Project moduleProject,
            String language,
            String sourcePath,
            String sectionId
    ) {
        ensureRequiredFields(section, moduleProject.path, language, sourcePath, sectionId)
    }

    private static void ensureRequiredFields(
            Map<String, Object> section,
            String modulePath,
            String language,
            String sourcePath,
            String sectionId
    ) {

        // Drop obsolete metadata when synchronizing older local chapter files.
        section.remove('aiGenerated')
        section.remove('reviewed')

        Object difficultyValue =
                section[
                        'difficulty'
                ]


        if (
                difficultyValue == null ||
                        difficultyValue.toString().trim().isBlank()
        ) {

            section[
                    'difficulty'
            ] =
                    DEFAULT_DIFFICULTY
        }
        else {

            String difficulty =
                    difficultyValue
                            .toString()
                            .trim()
                            .toUpperCase(
                                    Locale.ROOT
                            )


            if (!ALLOWED_DIFFICULTIES.contains(difficulty)) {

                throw new GradleException(
                        """
Invalid README knowledge difficulty.

Module:
${modulePath}

Language:
${language}

README file:
${sourcePath}

Section id:
${sectionId}

Value:
${difficultyValue}

Allowed values:
${ALLOWED_DIFFICULTIES.join(', ')}

Existing human-owned values are not overwritten automatically.
""".stripIndent()
                )
            }
        }


    }

    private Map<String, Object> loadMap(
            File file
    ) {

        if (
                !file.exists() ||
                        file.length() == 0
        ) {

            return new LinkedHashMap<>()
        }


        if (!file.isFile()) {

            throw new GradleException(
                    """
README metadata path exists but is not a file:

${file.absolutePath}
""".stripIndent()
            )
        }


        Object loaded


        try {

            loaded =
                    reader.load(
                            file.getText(
                                    'UTF-8'
                            )
                    )
        }
        catch (Exception exception) {

            throw new GradleException(
                    """
Unable to parse README metadata YAML.

File:
${file.absolutePath}

The file will NOT be overwritten.

Cause:
${exception.message}
""".stripIndent(),
                    exception
            )
        }


        if (loaded == null) {

            return new LinkedHashMap<>()
        }


        if (!(loaded instanceof Map)) {

            throw new GradleException(
                    """
Invalid README metadata YAML structure.

Expected root object to be a map.

File:
${file.absolutePath}

The file will NOT be overwritten.
""".stripIndent()
            )
        }


        return loaded as Map<String, Object>
    }


    /** Local metadata has exactly one root key: sections. The legacy central
     * file is read separately by loadMap() and retains its per-file keys. */
    private Map<String, Object> loadChapterSections(File file) {
        if (!file.exists()) return new LinkedHashMap<>()
        Map root = loadMap(file)
        if (root.size() != 1 || !root.containsKey('sections') ||
                !(root.sections instanceof Map)) {
            throw new GradleException(
                    'Chapter Knowledge metadata must contain only sections: {anchor: fields}: ' +
                            file.absolutePath)
        }
        Map sections = root.sections as Map
        sections.each { Object anchor, Object metadata ->
            if (!(anchor instanceof String) || !(metadata instanceof Map)) {
                throw new GradleException(
                        'Invalid chapter Knowledge section entry: ' + file.absolutePath +
                                ' #' + anchor)
            }
        }
        return sections as Map<String, Object>
    }

    private static Map<String, Object> resolveOptionalMap(
            Object value,
            String message
    ) {

        if (value == null) {
            return null
        }


        if (!(value instanceof Map)) {

            throw new GradleException(
                    message.trim()
            )
        }


        return value as Map<String, Object>
    }

    /**
     * Knowledge paths are chapter/knowledge/file.md. An imported Sheet
     * snapshot may still use chapter/file.md until its importer is migrated.
     */
    private static String normalizeKnowledgePath(String sourcePath) {
        if (sourcePath == null || sourcePath.contains('\\') ||
                sourcePath.startsWith('/')) {
            throw new GradleException('Unsafe Knowledge source path: ' + sourcePath)
        }
        List<String> segments = sourcePath.split('/') as List<String>
        if (!(segments.size() in [2, 3]) ||
                !(segments[0] ==~ /\d+\..+/) ||
                (segments.size() == 3 && segments[1] != 'knowledge') ||
                (segments.last() in ['', '.', '..']) ||
                !segments.last().toLowerCase(Locale.ROOT).endsWith('.md')) {
            throw new GradleException('Unsafe Knowledge source path: ' + sourcePath)
        }
        return segments.size() == 2
                ? segments[0] + '/knowledge/' + segments[1]
                : sourcePath
    }

    /**
     * Move metadata by article identity without resetting review or human
     * fields when chapter/file.md becomes chapter/knowledge/file.md.
     * If both keys exist, current-path values take precedence; missing
     * fields are retained from the legacy entry.
     */
    private static Map<String, Object> existingKnowledgeMetadata(
            Map root, String knowledgePath, File menuDirectory = null
    ) {
        String canonical = normalizeKnowledgePath(knowledgePath)
        String legacy = canonical.replace('/knowledge/', '/')
        String legacyRootFile = canonical.substring(canonical.lastIndexOf('/') + 1)
        Map merged = new LinkedHashMap()
        boolean uniqueFlatOwner = true
        if (menuDirectory?.isDirectory()) {
            List<File> matches = (menuDirectory.listFiles() ?: [])
                    .findAll { File chapter ->
                        chapter.isDirectory() &&
                                new File(chapter, 'knowledge/' + legacyRootFile).isFile()
                    }
            uniqueFlatOwner = matches.isEmpty() ||
                    (matches.size() == 1 && matches.first().name == canonical.split('/')[0])
        }
        List<String> keys = uniqueFlatOwner ?
                [legacyRootFile, legacy, canonical] : [legacy, canonical]
        keys.unique().each { String key ->
            Map oldEntries = resolveOptionalMap(root[key],
                    'Invalid Knowledge metadata file: ' + key) ?: [:]
            oldEntries.each { Object sectionId, Object entry ->
                if (merged[sectionId] instanceof Map && entry instanceof Map) {
                    Map combined = new LinkedHashMap(merged[sectionId] as Map)
                    (entry as Map).each { Object field, Object value ->
                        if (value != null) combined[field] = value
                    }
                    merged[sectionId] = combined
                }
                else {
                    merged[sectionId] = entry
                }
            }
        }
        return merged
    }


    private static List<Project> resolveRealModuleProjects(
            Project rootProject,
            File moduleRoot
    ) {

        def moduleRootPath =
                moduleRoot
                        .toPath()
                        .toAbsolutePath()
                        .normalize()


        return rootProject
                .allprojects
                .findAll {
                    Project candidate ->


                        def projectPath =
                                candidate
                                        .projectDir
                                        .toPath()
                                        .toAbsolutePath()
                                        .normalize()


                        projectPath.startsWith(
                                moduleRootPath
                        ) &&
                                new File(
                                        candidate.projectDir,
                                        'gradle.properties'
                                ).isFile()
                }
                .sort {
                    Project left,
                    Project right ->


                        left.projectDir.absolutePath <=>
                                right.projectDir.absolutePath
                }
    }


    private static List<LanguageSource> resolveLanguageSources(
            Project moduleProject
    ) {

        File readmeDirectory =
                new File(
                        moduleProject.projectDir,
                        'content/readme/docs'
                )


        if (!readmeDirectory.isDirectory()) {
            return []
        }


        return ProjectPropertyUtils
                .getStringList(
                        moduleProject,
                        'MODULE_LANGUAGE'
                )
                .collect {
                    String language ->


                        language.toLowerCase(
                                Locale.ROOT
                        )
                }
                .unique()
                .collect {
                    String language ->


                        new LanguageSource(
                                language,
                                new File(
                                        readmeDirectory,
                                        "${language}/menu"
                                )
                        )
                }
                .findAll {
                    LanguageSource source ->


                        source.menuDirectory.isDirectory() &&
                                !collectMarkdownFiles(
                                        source.menuDirectory
                                ).isEmpty()
                }
    }


    private static List<File> collectMarkdownFiles(
            File rootDirectory
    ) {

        List<File> result = []


        collectMarkdownFiles(
                rootDirectory,
                result
        )


        return result.sort {
            File left,
            File right ->


                compareSourcePaths(
                        relativePath(
                                rootDirectory,
                                left
                        ),
                        relativePath(
                                rootDirectory,
                                right
                        )
                )
        }
    }


    private static int compareSourcePaths(
            String left,
            String right
    ) {

        List<String> leftSegments =
                left.split('/') as List<String>


        List<String> rightSegments =
                right.split('/') as List<String>


        int commonLength =
                Math.min(
                        leftSegments.size(),
                        rightSegments.size()
                )


        for (int index = 0; index < commonLength; index++) {

            String leftSegment =
                    leftSegments[index]


            String rightSegment =
                    rightSegments[index]


            Integer leftNumber =
                    extractLeadingNumber(
                            leftSegment
                    )


            Integer rightNumber =
                    extractLeadingNumber(
                            rightSegment
                    )


            if (
                    leftNumber != null &&
                            rightNumber != null
            ) {

                int numberCompare =
                        leftNumber <=> rightNumber


                if (numberCompare != 0) {

                    return numberCompare
                }
            }


            int segmentCompare =
                    leftSegment <=> rightSegment


            if (segmentCompare != 0) {

                return segmentCompare
            }
        }


        return leftSegments.size() <=> rightSegments.size()
    }


    private static Integer extractLeadingNumber(
            String value
    ) {

        def matcher =
                value =~ /^(\d+)/


        if (!matcher.find()) {

            return null
        }


        try {

            return Integer.parseInt(
                    matcher.group(1)
            )
        }
        catch (NumberFormatException ignored) {

            return null
        }
    }


    private static void collectMarkdownFiles(
            File directory,
            List<File> result
    ) {
        File[] chapters = directory.listFiles()
        if (chapters == null) {
            throw new GradleException(
                    'Unable to read README Knowledge menu directory: ' + directory.absolutePath)
        }
        chapters.each { File chapter ->
            if (!chapter.isDirectory() || !(chapter.name ==~ /\d+\..+/)) {
                return
            }
            File knowledge = new File(chapter, 'knowledge')
            if (!knowledge.isDirectory()) return
            if (!knowledge.canonicalFile.toPath().startsWith(
                    directory.canonicalFile.toPath())) {
                throw new GradleException(
                        'Knowledge folder escapes README menu: ' + knowledge.absolutePath)
            }
            File[] files = knowledge.listFiles()
            if (files == null) {
                throw new GradleException(
                        'Unable to read README Knowledge directory: ' + knowledge.absolutePath)
            }
            files.each { File file ->
                if (file.isFile() && !file.name.startsWith('.') &&
                        file.name.toLowerCase(Locale.ROOT).endsWith('.md') &&
                        file.canonicalFile.toPath().startsWith(
                                knowledge.canonicalFile.toPath())) {
                    result.add(file)
                }
            }
        }
    }


    private static String relativePath(
            File root,
            File target
    ) {

        return GradleBuildUtils.normalizePath(
                root
                        .toPath()
                        .toAbsolutePath()
                        .normalize()
                        .relativize(
                                target
                                        .toPath()
                                        .toAbsolutePath()
                                        .normalize()
                        )
                        .toString()
        )
    }


    private static boolean writeSafelyIfChanged(
            File outputFile,
            String content
    ) {

        if (
                outputFile.isFile() &&
                        outputFile.getText(
                                'UTF-8'
                        ) == content
        ) {

            return false
        }


        if (
                !outputFile.parentFile.isDirectory() &&
                        !outputFile.parentFile.mkdirs() &&
                        !outputFile.parentFile.isDirectory()
        ) {

            throw new GradleException(
                    """
Unable to create README metadata directory:

${outputFile.parentFile.absolutePath}
""".stripIndent()
            )
        }


        File temporaryFile =
                new File(
                        outputFile.parentFile,
                        "${outputFile.name}.tmp"
                )


        temporaryFile.setText(
                content,
                'UTF-8'
        )


        try {

            Files.move(
                    temporaryFile.toPath(),
                    outputFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            )
        }
        finally {

            if (temporaryFile.exists()) {

                temporaryFile.delete()
            }
        }


        return true
    }


    private static class LanguageSource {

        final String language
        final File menuDirectory


        LanguageSource(
                String language,
                File menuDirectory
        ) {

            this.language = language
            this.menuDirectory = menuDirectory
        }
    }


    private static class WritePlan {

        final File outputFile
        final String content


        WritePlan(
                File outputFile,
                String content
        ) {

            this.outputFile = outputFile
            this.content = content
        }
    }
}
