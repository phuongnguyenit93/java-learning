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


    static final boolean DEFAULT_AI_GENERATED =
            true


    static final boolean DEFAULT_REVIEWED =
            false


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


                        plans.add(
                                buildWritePlan(
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
                '🧩 [README-METADATA] Synced {} language metadata files ({} changed).',
                plans.size(),
                changedFiles
        )
    }


    private WritePlan buildWritePlan(
            Project moduleProject,
            LanguageSource languageSource
    ) {

        File outputFile =
                new File(
                        moduleProject.projectDir,
                        "src/main/resources/readme/${languageSource.language}/${METADATA_FILE_NAME}"
                )


        Map<String, Object> existingRoot =
                loadMap(
                        outputFile
                )


        Map<String, Object> synchronizedRoot =
                new LinkedHashMap<>()


        Set<String> sectionIds =
                new LinkedHashSet<>()


        Map<String, String> sectionSources =
                new LinkedHashMap<>()


        collectMarkdownFiles(
                languageSource.menuDirectory
        ).each {
            File markdownFile ->


                String sourcePath =
                        relativePath(
                                languageSource.menuDirectory,
                                markdownFile
                        )


                Map<String, Object> existingFileEntry =
                        resolveOptionalMap(
                                existingRoot[
                                        sourcePath
                                ],
                                """
Invalid README metadata file entry.

Module:
${moduleProject.path}

Language:
${languageSource.language}

README file:
${sourcePath}
""".stripIndent()
                        )


                Map<String, Object> synchronizedFileEntry =
                        new LinkedHashMap<>()


                parser.parse(
                        markdownFile
                )
                        .findAll {
                            ReadmeKnowledgeParser.SectionCandidate candidate ->


                                candidate.valid
                        }
                        .each {
                            ReadmeKnowledgeParser.SectionCandidate candidate ->


                                if (!sectionIds.add(candidate.id)) {

                                    throw new GradleException(
                                            """
Duplicate README knowledge section id detected.

Module:
${moduleProject.path}

Language:
${languageSource.language}

Section id:
${candidate.id}

First source:
${sectionSources[candidate.id]}

Duplicate source:
${sourcePath}

Section ids must be unique within one module/language.
""".stripIndent()
                                    )
                                }


                                sectionSources[
                                        candidate.id
                                ] =
                                        sourcePath


                                Map<String, Object> existingSection =
                                        resolveOptionalMap(
                                                existingFileEntry == null
                                                        ? null
                                                        : existingFileEntry[
                                                                candidate.id
                                                        ],
                                                """
Invalid README metadata section entry.

Module:
${moduleProject.path}

Language:
${languageSource.language}

README file:
${sourcePath}

Section id:
${candidate.id}
""".stripIndent()
                                        )


                                Map<String, Object> synchronizedSection =
                                        existingSection == null
                                                ? new LinkedHashMap<>()
                                                : new LinkedHashMap<>(
                                                        existingSection
                                                )


                                ensureRequiredFields(
                                        synchronizedSection,
                                        moduleProject,
                                        languageSource.language,
                                        sourcePath,
                                        candidate.id
                                )


                                synchronizedFileEntry[
                                        candidate.id
                                ] =
                                        synchronizedSection
                        }


                if (!synchronizedFileEntry.isEmpty()) {

                    synchronizedRoot[
                            sourcePath
                    ] =
                            synchronizedFileEntry
                }
        }


        String content =
                synchronizedRoot.isEmpty()
                        ? '{}\n'
                        : writer.dump(
                                synchronizedRoot
                        )


        return new WritePlan(
                outputFile,
                content
        )
    }


    private static void ensureRequiredFields(
            Map<String, Object> section,
            Project moduleProject,
            String language,
            String sourcePath,
            String sectionId
    ) {

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
${moduleProject.path}

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


        if (
                !section.containsKey(
                        'aiGenerated'
                ) ||
                        section[
                                'aiGenerated'
                        ] == null
        ) {

            section[
                    'aiGenerated'
            ] =
                    DEFAULT_AI_GENERATED
        }


        if (
                !section.containsKey(
                        'reviewed'
                ) ||
                        section[
                                'reviewed'
                        ] == null
        ) {

            section[
                    'reviewed'
            ] =
                    DEFAULT_REVIEWED
        }


        validateBooleanField(
                section,
                'aiGenerated',
                moduleProject,
                language,
                sourcePath,
                sectionId
        )


        validateBooleanField(
                section,
                'reviewed',
                moduleProject,
                language,
                sourcePath,
                sectionId
        )
    }


    private static void validateBooleanField(
            Map<String, Object> section,
            String field,
            Project moduleProject,
            String language,
            String sourcePath,
            String sectionId
    ) {

        Object value =
                section[
                        field
                ]


        if (value instanceof Boolean) {
            return
        }


        throw new GradleException(
                """
Invalid README knowledge boolean metadata.

Module:
${moduleProject.path}

Language:
${language}

README file:
${sourcePath}

Section id:
${sectionId}

Field:
${field}

Value:
${value}

Expected YAML boolean true or false.
""".stripIndent()
        )
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
                        'readme'
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

        File[] children =
                directory.listFiles()


        if (children == null) {

            throw new GradleException(
                    """
Unable to read README knowledge directory:

${directory.absolutePath}
""".stripIndent()
            )
        }


        children
                .toList()
                .sort {
                    File left,
                    File right ->


                        left.name <=> right.name
                }
                .each {
                    File child ->


                        if (child.name.startsWith('.')) {
                            return
                        }


                        if (child.isDirectory()) {

                            collectMarkdownFiles(
                                    child,
                                    result
                            )


                            return
                        }


                        if (
                                child.isFile() &&
                                        child.name.toLowerCase(
                                                Locale.ROOT
                                        ).endsWith(
                                                '.md'
                                        )
                        ) {

                            result.add(
                                    child
                            )
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
