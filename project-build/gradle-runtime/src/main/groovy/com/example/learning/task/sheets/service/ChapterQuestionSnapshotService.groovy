package com.example.learning.task.sheets.service

/**
 * A module-wide Interview/Quiz import CLEANs every owned VI chapter directory
 * and legacy directory before FILL. Knowledge/Metadata/Video/EN are untouched.
 * No existing question YAML is consulted to prepare the new output.
 */
class ChapterQuestionSnapshotService {
    static Map<String, Object> replace(File root, File module, String kind,
                                       Map<String, String> chapters, boolean apply) {
        return ChapterSheetCleanFillService.replace(root, module, kind, chapters, apply)
    }
}
