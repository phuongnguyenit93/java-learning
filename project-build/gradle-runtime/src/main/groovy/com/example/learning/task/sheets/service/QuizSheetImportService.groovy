package com.example.learning.task.sheets.service

import com.example.learning.setup.module.quiz.service.QuizQuestionSchemaService
import com.google.api.services.sheets.v4.Sheets
import org.gradle.api.GradleException
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml

import java.nio.file.Files

/**
 * Sheet Quiz YAML -> each chapter's quiz/question.yml.
 * Only Groovy/Sheets API/SnakeYAML are used; no AI, Sheet writes or metadata inference.
 */
class QuizSheetImportService {
    private static final int CELL_LIMIT = 30000

    Map<String, Object> execute(File root, Map<String, Object> context, boolean apply) {
        File directory = resolveDirectory(root, context.moduleLocation as String)
        File module = new File(root.canonicalFile, context.moduleLocation as String).canonicalFile
        Sheets sheets = context.client as Sheets
        String id = context.spreadsheetId as String
        String tab = "'" + (context.tabTitle as String).replace("'", "''") + "'"
        Map base = [tab: context.tabTitle, gid: context.sheetId,
                    location: context.moduleLocation, directory: directory]

        // Old tabs without Quiz columns must not delete existing module Quiz.
        def metadata = sheets.spreadsheets().get(id)
                .setFields('sheets(properties(sheetId,gridProperties(columnCount)))')
                .execute()
        def selected = (metadata.sheets ?: []).find {
            it.properties?.sheetId?.toString() == context.sheetId?.toString()
        }
        if (!selected) {
            throw new GradleException("Quiz tab gid=${context.sheetId} no longer exists.")
        }
        if ((selected.properties?.gridProperties?.columnCount ?: 0) < 23) {
            return base + [status: 'SKIPPED_NO_QUIZ_SCHEMA',
                           questionCount: 0, chapterCount: 0, clearedOldFiles: 0]
        }

        List<List<Object>> menuRows = (sheets.spreadsheets().values()
                .get(id, tab + '!A19:B').execute().values ?: []) as List<List<Object>>
        Map parsed = ReadmeSheetImportService.parseMenu(menuRows)
        if (parsed.status == 'SKIPPED_EMPTY_MENU' ||
                parsed.status == 'SKIPPED_MISSING_FILENAME') {
            return base + [status: parsed.status, questionCount: 0,
                           chapterCount: 0, missingRows: parsed.missingRows ?: [],
                           clearedOldFiles: 0]
        }
        List<Map> groups = parsed.groups as List<Map>
        List<List<Object>> rows = (sheets.spreadsheets().values()
                .get(id, tab + '!T18:W').setValueRenderOption('FORMULA')
                .execute().values ?: []) as List<List<Object>>
        Map assembled = collectQuestions(groups, rows)
        if (assembled.status == 'SKIPPED_EMPTY_QUIZ') {
            return base + [status: assembled.status, questionCount: 0,
                           chapterCount: 0, clearedOldFiles: 0]
        }
        Map<String, String> chapterFiles = [:]
        (assembled.byChapter as Map<String, List<Map>>).each { String chapter, List<Map> items ->
            chapterFiles[chapter] = renderYaml(items)
        }
        Map sync = ChapterQuestionSnapshotService.replace(root, module, 'quiz',
                chapterFiles, apply)
        return base + sync + [
                questionCount: (assembled.questions as List).size(),
                chapterCount: assembled.chapterCount
        ]
    }

    /**
     * Quiz range begins at T18. Every populated parent must have complete
     * Quiz before a full snapshot replaces existing module Quiz.
     */
    static Map collectQuestions(List<Map> groups, List<List<Object>> rows) {
        Set<Integer> parentRows = groups.collect {
            (it.parentRow as Number).intValue()
        } as Set<Integer>
        boolean anyContent = (2..<Math.max(2, rows.size())).any { int i ->
            (0..3).any { int col -> raw(row(rows, i), col) }
        }
        if (!anyContent) {
            return [status: 'SKIPPED_EMPTY_QUIZ', questions: [],
                    chapterCount: 0]
        }
        List<String> labels = (0..3).collect { int col -> raw(row(rows, 1), col).trim() }
        if (raw(row(rows, 0), 0).trim() != 'Quiz' ||
                !(labels.unique().size() == 1 && labels[0] == 'Quiz YAML')) {
            throw new GradleException('Invalid Quiz headers at T18:W19.')
        }

        rows.drop(2).eachWithIndex { List<Object> cells, int i ->
            int sheetRow = i + 20
            if (!parentRows.contains(sheetRow) &&
                    (0..3).any { int col -> raw(cells, col) }) {
                throw new GradleException(
                        "Quiz data on non-parent row T${sheetRow}:W${sheetRow}."
                )
            }
        }

        List<Map> questions = []
        Map<String, List<Map>> byChapter = new LinkedHashMap<>()
        int chapters = 0
        groups.eachWithIndex { Map group, int i ->
            int sheetRow = (group.parentRow as Number).intValue()
            StringBuilder authoredYaml = new StringBuilder()
            boolean gap = false
            (0..3).each { int col ->
                String text = raw(row(rows, sheetRow - 18), col)
                if (!text) gap = true
                else {
                    if (gap || text.codePointCount(0, text.length()) > CELL_LIMIT) {
                        throw new GradleException(
                                "Invalid Quiz fragments at T${sheetRow}:W${sheetRow}."
                        )
                    }
                    authoredYaml.append(text)
                }
            }
            if (authoredYaml.length() == 0) {
                throw new GradleException('Incomplete Quiz at chapter parent row ' + sheetRow)
            }
            String filename = group.filename as String
            String file = "${i + 1}.${filename.substring(0, filename.length() - 3)}/${filename}"
            Map normalized = QuizSheetNormalizeService.normalizeYaml(
                    authoredYaml.toString(), file,
                    filename.substring(0, filename.length() - 3), sheetRow
            )
            List<Map> authored = normalized.questions as List<Map>
            String chapter = "${i + 1}.${filename.substring(0, filename.length() - 3)}"
            byChapter[chapter] = authored
            questions.addAll(authored)
            chapters++
        }
        // The parent basename determines the ID namespace. Guard even legacy
        // manually edited input against collisions across separate chapters.
        Set<String> uniqueIds = questions.collect { it.id as String } as Set
        if (uniqueIds.size() != questions.size()) {
            throw new GradleException('Duplicate Quiz question IDs across chapters.')
        }
        return [status: 'READY', questions: questions, chapterCount: chapters,
                byChapter: byChapter]
    }

    /** Complete schema fields are serialized; content is not rewritten by AI. */
    static String renderYaml(List<Map> questions) {
        if (!questions) {
            throw new GradleException('Cannot render an empty Quiz snapshot.')
        }
        DumperOptions options = new DumperOptions()
        options.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        options.allowUnicode = true
        options.indent = 2
        options.width = 120
        String result = new QuizQuestionSchemaService().renderCommentBlock('vi') +
                '\n\n' + new Yaml(options).dump([questions: questions])

        // Validate the EXACT serialized document with the existing module
        // Quiz schema before allowing even one old file to be deleted.
        File staged = Files.createTempFile('sheet-quiz-validate-', '.yml').toFile()
        try {
            staged.setText(result, 'UTF-8')
            new QuizQuestionSchemaService().validateQuestionFile(staged)
        }
        finally {
            Files.deleteIfExists(staged.toPath())
        }
        return result
    }

    static File resolveDirectory(File root, String location) {
        // Reuse the existing strict I3/module path validation.
        RoadmapSheetImportService.resolveDestination(root, location)
        File base = new File(root.canonicalFile, 'module').canonicalFile
        File directory = new File(
                root.canonicalFile,
                "${location}/content/readme/docs/vi/menu"
        ).canonicalFile
        if (!directory.toPath().startsWith(base.toPath()) ||
                directory.toPath() == base.toPath()) {
            throw new GradleException('Unsafe Quiz location: ' + location)
        }
        return directory
    }

    private static List<Object> row(List<List<Object>> rows, int i) {
        return i < rows.size() ? (rows[i] ?: []) : []
    }

    private static String raw(List<Object> values, int i) {
        return i < values.size() && values[i] != null ? values[i].toString() : ''
    }
}
