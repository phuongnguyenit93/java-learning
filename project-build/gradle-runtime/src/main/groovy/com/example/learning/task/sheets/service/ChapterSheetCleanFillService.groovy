package com.example.learning.task.sheets.service

import org.gradle.api.GradleException

/**
 * Import a complete VI chapter question/video snapshot. The Sheet owns the
 * entire directory for the selected kind, never the surrounding chapter.
 * Old VI-only legacy directories are retired in the SAME clean/fill operation.
 */
class ChapterSheetCleanFillService {
    private static final String CHAPTER = /[0-9]+\.[A-Za-z0-9][A-Za-z0-9 _.-]*/
    private static final Map<String, String> FILENAMES = [
            interview: 'question.yml', quiz: 'question.yml',
            video: 'video-script.yml'
    ].asImmutable()

    static Map<String, Object> replace(File repositoryRoot, File moduleDirectory,
                                       String kind, Map<String, String> chapters,
                                       boolean apply, Closure afterMutation = null) {
        if (!FILENAMES.containsKey(kind) || chapters == null) {
            throw new GradleException('Unsupported chapter Sheet snapshot kind: ' + kind)
        }
        File root = repositoryRoot.canonicalFile
        File module = moduleDirectory.canonicalFile
        File moduleRoot = new File(root, 'module').canonicalFile
        if (!module.toPath().startsWith(moduleRoot.toPath()) || module == moduleRoot) {
            throw new GradleException('Unsafe chapter Sheet module: ' + module)
        }
        File menu = new File(module, 'content/readme/docs/vi/menu')
        if (menu.exists() && !menu.isDirectory()) {
            throw new GradleException('Chapter menu is not a directory: ' + menu)
        }
        Map<File, Map<String, String>> targets = new LinkedHashMap<>()
        if (menu.isDirectory()) {
            File[] folders = menu.listFiles()
            if (folders == null) throw new GradleException('Cannot list Sheet chapter menu: ' + menu)
            folders.findAll { it.isDirectory() }.each { File chapter ->
                File owned = new File(chapter, kind)
                if (owned.exists()) {
                    if (!(chapter.name ==~ CHAPTER)) {
                        throw new GradleException('Unexpected chapter with ' + kind + ': ' + chapter)
                    }
                    targets[owned] = [:]
                }
            }
        }
        chapters.each { String chapter, String body ->
            if (!(chapter ==~ CHAPTER) || chapter.contains('..') || body == null) {
                throw new GradleException('Invalid ' + kind + ' chapter: ' + chapter)
            }
            File home = new File(menu, chapter)
            if (!new File(home, 'knowledge').isDirectory()) {
                throw new GradleException('Missing Knowledge chapter for ' + kind + ' import: ' + home)
            }
            targets[new File(home, kind)] = [(FILENAMES[kind]): body]
        }
        // Only VI legacy output belongs to this importer; EN is never touched.
        File legacy = new File(module, 'content/' + kind + '/vi')
        if (legacy.exists()) targets[legacy] = [:]
        Map result = SheetSnapshotReplaceService.replaceAll(
                root, targets, apply, afterMutation)
        return result + [chapterCount: chapters.size()]
    }
}
