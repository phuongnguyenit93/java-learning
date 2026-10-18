package com.example.learning.setup.root.structure.service

import com.example.learning.utils.schema.validate.VideoYamlContentUtils
import com.example.learning.utils.ProjectPropertyUtils
import com.example.learning.utils.GradleBuildUtils
import groovy.json.JsonOutput
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger

import java.nio.file.Path

/**
 * Video YAML -> published Video Script / Video Index JSON.
 * Video's source category identity comes from its Knowledge chapter folder
 * (e.g. 1.Introduction/video-script.yml -> Introduction), NOT its common
 * filename video-script.yml. No Markdown parsing and no source writes.
 */
class PortalVideoProjectionService {
    private static final String OUTPUT_DIRECTORY = LearningDataPaths.MODULE_DIRECTORY
    private final Logger logger

    PortalVideoProjectionService(Logger logger) {
        this.logger = logger
    }

    void generate(Project rootProject) {
        File moduleRoot = new File(rootProject.projectDir, 'module')
        if (!moduleRoot.isDirectory()) {
            throw new GradleException("Project module directory was not found: ${moduleRoot}")
        }
        File outputDirectory = new File(rootProject.projectDir, OUTPUT_DIRECTORY)
        Map<String, String> outputs = new LinkedHashMap<>()
        Set<String> routes = new LinkedHashSet<>()

        rootProject.allprojects.findAll { Project module ->
            Path path = module.projectDir.canonicalFile.toPath()
            path.startsWith(moduleRoot.canonicalFile.toPath()) &&
                    new File(module.projectDir, 'gradle.properties').isFile()
        }.sort { Project a, Project b ->
            a.projectDir.absolutePath <=> b.projectDir.absolutePath
        }.each { Project module ->
            if (!ProjectPropertyUtils.isEnabled(module, 'BUILD_VIDEO')) return
            List<String> languages = ProjectPropertyUtils.getStringList(
                    module, 'MODULE_LANGUAGE').collect { String value ->
                value?.trim()?.toLowerCase(Locale.ROOT)
            }.findAll { String value -> value != null && !value.isBlank() }.unique()

            String routeId = (module.findProperty('SERVICE_NAME') ?: module.projectDir.name)
                    .toString().trim()
            boolean anyVideo = false
            languages.each { String language ->
                File menu = new File(module.projectDir, "content/video/${language}/menu")
                if (!menu.isDirectory()) return
                List<File> scriptFiles = []
                menu.eachFileRecurse { File file ->
                    if (file.isFile() && file.name == VideoYamlContentUtils.FILENAME) {
                        scriptFiles.add(file)
                    }
                }
                scriptFiles.each { File file ->
                    VideoYamlContentUtils.categoryId(file)
                }
                scriptFiles.sort { File a, File b ->
                    int leftOrder = Integer.parseInt(a.parentFile.name.split('\\.')[0])
                    int rightOrder = Integer.parseInt(b.parentFile.name.split('\\.')[0])
                    (leftOrder <=> rightOrder) ?:
                            (relativePath(menu, a) <=> relativePath(menu, b))
                }
                if (scriptFiles.isEmpty()) return
                anyVideo = true
                List<Map> indexItems = []
                Set<String> categoryIds = new LinkedHashSet<>()
                scriptFiles.each { File scriptFile ->
                    String relative = relativePath(menu, scriptFile)
                    if (relative.count('/') != 1) {
                        throw new GradleException("Video Script must be inside a single chapter folder: ${scriptFile}")
                    }
                    String categoryId = VideoYamlContentUtils.categoryId(scriptFile)
                    if (!categoryIds.add(categoryId.toLowerCase(Locale.ROOT))) {
                        throw new GradleException(
                                "Duplicate Portal Video category id '${categoryId}' in ${module.path}/${language}.")
                    }
                    Map document
                    try {
                        document = VideoYamlContentUtils.parse(scriptFile.getText('UTF-8'))
                    }
                    catch (Exception error) {
                        throw new GradleException(
                                "Invalid Portal Video YAML ${scriptFile.absolutePath}: ${error.message}", error)
                    }
                    String contentPublicPath =
                            "/module/${encode(routeId)}/video/${encode(language)}/content/${encode(categoryId)}/script.json"
                    String contentOutputPath =
                            "${routeId}/video/${language}/content/${categoryId}/script.json"
                    Map scriptProjection = [
                            version   : 1,
                            moduleId  : routeId,
                            language  : language,
                            categoryId: categoryId,
                            title     : document.title,
                            sourcePath: relative,
                            video     : document.video,
                            sections  : document.sections
                    ]
                    putJsonOutput(outputs, contentOutputPath, scriptProjection)
                    indexItems.add([
                            categoryId     : categoryId,
                            title          : document.title,
                            sourcePath     : relative,
                            content        : contentPublicPath,
                            url            : document.video.url,
                            sectionCount   : document.sections.size(),
                            sceneCount     : document.scenes,
                            transitionCount: document.transitions
                    ])
                }
                putJsonOutput(outputs, "${routeId}/video/${language}/index.json", [
                        version: 1, moduleId: routeId, language: language,
                        videoCount: indexItems.size(), items: indexItems
                ])
            }
            if (anyVideo && !routes.add(routeId)) {
                throw new GradleException("Duplicate Portal Video route identifier: ${routeId}")
            }
        }
        writeOutputs(outputDirectory, outputs)
        removeStaleFiles(outputDirectory, outputs.keySet())
        logger.lifecycle('🎬 [PORTAL-VIDEO] Generated {} Video projection files.', outputs.size())
    }

    private static String relativePath(File root, File file) {
        return GradleBuildUtils.normalizePath(
                root.toPath().toAbsolutePath().normalize()
                        .relativize(file.toPath().toAbsolutePath().normalize()).toString())
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, 'UTF-8').replace('+', '%20')
    }

    private static void putJsonOutput(Map<String, String> outputs, String path, Map payload) {
        if (outputs.containsKey(path)) {
            throw new GradleException("Duplicate Portal Video output: ${path}")
        }
        outputs[path] = JsonOutput.prettyPrint(JsonOutput.toJson(payload)) + '\n'
    }

    private static void writeOutputs(File root, Map<String, String> outputs) {
        outputs.each { String relative, String content ->
            File file = new File(root, relative)
            if (!file.parentFile.isDirectory() && !file.parentFile.mkdirs() &&
                    !file.parentFile.isDirectory()) {
                throw new GradleException("Cannot create Portal Video directory: ${file.parentFile}")
            }
            if (!file.isFile() || file.getText('UTF-8') != content) {
                file.setText(content, 'UTF-8')
            }
        }
    }

    /** Remove only Video JSON outputs owned by this projector. */
    private static void removeStaleFiles(File root, Set<String> expected) {
        if (!root.exists()) return
        if (!root.isDirectory()) {
            throw new GradleException("Portal Video output is not a directory: ${root}")
        }
        List<File> stale = []
        root.eachFileRecurse { File file ->
            if (!file.isFile()) return
            String relative = relativePath(root, file)
            List<String> parts = relative.split('/').toList()
            if (parts.size() >= 3 && parts[1] == 'video' && !expected.contains(relative)) {
                stale.add(file)
            }
        }
        stale.each { File file ->
            if (!file.delete() && file.exists()) {
                throw new GradleException("Cannot remove stale Portal Video file: ${file}")
            }
        }
    }
}
