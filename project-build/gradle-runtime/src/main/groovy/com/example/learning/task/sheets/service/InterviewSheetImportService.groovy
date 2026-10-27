package com.example.learning.task.sheets.service

import com.example.learning.setup.module.interview.service.InterviewQuestionSchemaService
import com.example.learning.utils.schema.validate.ContractSchemaValidateUtils
import com.google.api.services.sheets.v4.Sheets
import org.gradle.api.GradleException
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml

import java.nio.file.Files

/**
 * Sheet Interview YAML (and legacy JSON) -> each chapter's questions/interview.yml
 * and questions/summary.yml when summaryQuestions were authored.
 * Only Groovy/Sheets API/SnakeYAML are used; no AI, Sheet writes or metadata inference.
 */
class InterviewSheetImportService {
    private static final int CELL_LIMIT = 30000

    Map<String, Object> execute(File root, Map<String, Object> context, boolean apply) {
        File directory = resolveDirectory(root, context.moduleLocation as String)
        File module = new File(root.canonicalFile, context.moduleLocation as String).canonicalFile
        Sheets sheets = context.client as Sheets
        String id = context.spreadsheetId as String
        String tab = "'" + (context.tabTitle as String).replace("'", "''") + "'"
        Map base = [tab: context.tabTitle, gid: context.sheetId,
                    location: context.moduleLocation, directory: directory]

        // Tabs without the Interview P:S columns must not delete existing
        // module Interview output. Populated wrong headers are rejected below.
        def metadata = sheets.spreadsheets().get(id)
                .setFields('sheets(properties(sheetId,gridProperties(columnCount)))')
                .execute()
        def selected = (metadata.sheets ?: []).find {
            it.properties?.sheetId?.toString() == context.sheetId?.toString()
        }
        if (!selected) {
            throw new GradleException("Interview tab gid=${context.sheetId} no longer exists.")
        }
        if ((selected.properties?.gridProperties?.columnCount ?: 0) < 19) {
            return base + [status: 'SKIPPED_NO_INTERVIEW_SCHEMA',
                           questionCount: 0, chapterCount: 0, clearedOldFiles: 0]
        }

        List<List<Object>> menuRows = (sheets.spreadsheets().values()
                .get(id, tab + '!A19:B').execute().values ?: []) as List<List<Object>>
        Map parsed = ReadmeSheetImportService.parseMenu(menuRows)
        if (!(parsed.status in ['READY', 'SKIPPED_EMPTY_MENU'])) {
            return base + [status: parsed.status, questionCount: 0,
                           chapterCount: 0, missingRows: parsed.missingRows ?: [],
                           clearedOldFiles: 0]
        }
        List<Map> groups = parsed.groups as List<Map>
        List<List<Object>> rows = (sheets.spreadsheets().values()
                .get(id, tab + '!P18:S').setValueRenderOption('FORMULA')
                .execute().values ?: []) as List<List<Object>>
        if (rows.every { List<Object> cells -> (0..3).every { int c -> !raw(cells, c) } }) {
            return base + [status: 'SKIPPED_NO_INTERVIEW_SCHEMA', questionCount: 0,
                           chapterCount: 0, clearedOldFiles: 0]
        }
        Map assembled = collectQuestions(groups, rows)
        Map<String, Map<String,String>> chapterFiles = [:]
        (assembled.byChapter as Map<String, Map>).each { String chapter, Map document ->
            Map<String,String> files = [ 'interview.yml': renderYaml(document.questions as List<Map>) ]
            if (document.containsKey('summaryQuestions')) {
                files['summary.yml'] = renderSummaryYaml(document.summaryQuestions as List<Map>)
            }
            chapterFiles[chapter] = files
        }
        Map sync = ChapterQuestionSnapshotService.replaceInterview(root, module,
                chapterFiles, apply)
        return base + sync + [
                questionCount: (assembled.questions as List).size(),
                summaryQuestionCount: (assembled.summaryQuestions as List).size(),
                chapterCount: assembled.chapterCount
        ]
    }

    /**
     * Interview range begins at P18, unlike the Menu range beginning at A19.
     * Validating EVERY P:S data row prevents unnoticed orphan Interview cells.
     * Blank chapters are omitted. A valid Interview schema with no authored
     * questions is an authoritative empty snapshot: clean old Interview output.
     */
    static Map collectQuestions(List<Map> groups, List<List<Object>> rows) {
        Set<Integer> parentRows = groups.collect {
            (it.parentRow as Number).intValue()
        } as Set<Integer>
        List<String> labels = (0..3).collect { int col -> raw(row(rows, 1), col).trim() }
        if (raw(row(rows, 0), 0).trim() != 'Interview' ||
                !(labels.unique().size() == 1 && labels[0] in ['Interview YAML', 'Interview JSON'])) {
            throw new GradleException('Invalid Interview headers at P18:S19.')
        }

        rows.drop(2).eachWithIndex { List<Object> cells, int i ->
            int sheetRow = i + 20
            if (!parentRows.contains(sheetRow) &&
                    (0..3).any { int col -> raw(cells, col) }) {
                throw new GradleException(
                        "Interview data on non-parent row P${sheetRow}:S${sheetRow}."
                )
            }
        }

        List<Map> questions = []
        List<Map> summaryQuestions = []
        Map<String, Map> byChapter = new LinkedHashMap<>()
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
                                "Invalid Interview fragments at P${sheetRow}:S${sheetRow}."
                        )
                    }
                    authoredYaml.append(text)
                }
            }
            if (authoredYaml.length() == 0) return
            String filename = group.filename as String
            String file = "${i + 1}.${filename.substring(0, filename.length() - 3)}/${filename}"
            Map normalized = InterviewSheetNormalizeService.normalizeYaml(
                    authoredYaml.toString(), file, sheetRow
            )
            List<Map> authored = normalized.questions as List<Map>
            String chapter = "${i + 1}.${filename.substring(0, filename.length() - 3)}"
            byChapter[chapter] = normalized
            questions.addAll(authored)
            summaryQuestions.addAll((normalized.summaryQuestions ?: []) as List<Map>)
            chapters++
        }
        return [status: 'READY', questions: questions, summaryQuestions: summaryQuestions, chapterCount: chapters,
                byChapter: byChapter]
    }

    static String renderSummaryYaml(List<Map> questions) {
        if (!questions) throw new GradleException('Cannot render an empty Summary Questions snapshot.')
        DumperOptions options = new DumperOptions()
        options.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        options.allowUnicode = true
        options.indent = 2
        options.width = 120
        Map document = [questions: questions]
        ContractSchemaValidateUtils.validateData('summaryQuestions', document)
        return new Yaml(options).dump(document)
    }

    /** Complete schema fields are serialized; content is not rewritten by AI. */
    static String renderYaml(List<Map> questions) {
        if (!questions) {
            throw new GradleException('Cannot render an empty Interview snapshot.')
        }
        DumperOptions options = new DumperOptions()
        options.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        options.allowUnicode = true
        options.indent = 2
        options.width = 120
        String result = new InterviewQuestionSchemaService().renderCommentBlock('vi') +
                '\n\n' + new Yaml(options).dump([questions: questions])

        // Validate the EXACT serialized chapter document with the source
        // Interview schema before allowing even one old file to be deleted.
        File staged = Files.createTempFile('sheet-interview-validate-', '.yml').toFile()
        try {
            staged.setText(result, 'UTF-8')
            new InterviewQuestionSchemaService().validateQuestionFile(staged)
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
            throw new GradleException('Unsafe Interview location: ' + location)
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
