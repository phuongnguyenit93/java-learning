package com.example.learning.task.sheets.service

import com.example.learning.utils.schema.validate.VideoYamlContentUtils
import org.gradle.api.GradleException

/** Entire authored VI video/ folders are owned by the Sheet Video importer. */
class ChapterVideoSnapshotService {
    static Map<String, Object> replace(File root, File module,
                                       Map<String, String> chapters, boolean apply,
                                       Closure afterMutation = null) {
        if (chapters == null) throw new GradleException('Null Video snapshot.')
        chapters.each { String chapter, String source ->
            if (!(chapter ==~ /[1-9][0-9]*\.[A-Za-z][A-Za-z0-9]*/)) {
                throw new GradleException('Invalid Video chapter: ' + chapter)
            }
            VideoYamlContentUtils.parse(source)
        }
        Closure callback = afterMutation == null ? null : { String phase, File directory ->
            // Keep the diagnostic callback pointing to a recognizable authored
            // path for chapter and legacy clean phases alike.
            File touched = directory.name == 'vi'
                    ? new File(directory, 'menu/' + (chapters.keySet().first() ?: 'unknown') +
                            '/' + VideoYamlContentUtils.FILENAME)
                    : new File(directory, VideoYamlContentUtils.FILENAME)
            afterMutation.call(touched)
        }
        Map result = ChapterSheetCleanFillService.replace(
                root, module, 'video', chapters, apply, callback)
        return result + [pendingFiles: !apply && result.status == 'DRY_RUN' ?
                result.expectedFiles + result.clearedOldFiles : 0,
                unchangedFiles: result.status == 'UNCHANGED' ? chapters.size() : 0]
    }
}
