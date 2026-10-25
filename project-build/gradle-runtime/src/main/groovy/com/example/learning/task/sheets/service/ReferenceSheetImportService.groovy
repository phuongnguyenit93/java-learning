package com.example.learning.task.sheets.service

import com.example.learning.utils.schema.validate.ContractSchemaValidateUtils
import com.google.api.services.sheets.v4.Sheets
import org.gradle.api.GradleException
import org.yaml.snakeyaml.Yaml

/** Module-wide language-neutral Reference: Sheet L2/L3 -> content/reference/references.yml. */
class ReferenceSheetImportService {
    static final String HEADER = 'Reference'
    static final int CELL_LIMIT = 30000

    Map<String, Object> execute(File repositoryRoot, Map<String, Object> context, boolean apply) {
        File directory = directory(repositoryRoot, context.moduleLocation as String)
        Sheets client = context.client as Sheets
        String tab = "'${(context.tabTitle as String).replace("'", "''")}'"
        List<List<Object>> rows = (client.spreadsheets().values()
                .get(context.spreadsheetId as String, "${tab}!L2:L3")
                .setValueRenderOption('FORMULA').execute().values ?: []) as List<List<Object>>
        String label = raw(rows, 0)
        String source = raw(rows, 1)
        Map base = [tab: context.tabTitle, gid: context.sheetId,
                    location: context.moduleLocation, directory: directory]
        if (!label && !source) return base + [status: 'SKIPPED_NO_REFERENCE_SCHEMA']
        if (label != HEADER) {
            throw new GradleException('Invalid Reference header at L2; expected Reference.')
        }
        // A blank cell is an unstarted authoring area, not an explicit delete.
        // Use `references: []` to intentionally clear an authored Reference.
        if (!source.trim()) return base + [status: 'SKIPPED_EMPTY_REFERENCE']
        Map document = parseReference(source)
        // Keep an explicit empty YAML when asked to clear. The Reference
        // setup plugin must not resurrect an example skeleton on next sync.
        Map<String, String> files = ['references.yml': source]
        Map result = SheetSnapshotReplaceService.replace(repositoryRoot, directory, files, apply)
        return base + result + [references: (document.references as List).size()]
    }

    static Map parseReference(String source) {
        if (source == null || source.codePointCount(0, source.length()) > CELL_LIMIT ||
                source.length() > 32767) {
            throw new GradleException('Reference L3 exceeds the safe Sheet/Excel cell limit.')
        }
        Object value
        try { value = new Yaml().load(source) }
        catch (Exception ex) { throw new GradleException('Invalid Reference YAML at L3: ' + ex.message, ex) }
        if (!(value instanceof Map) || !(value.references instanceof List) ||
                value.keySet() != ['references'] as Set) {
            throw new GradleException('Reference L3 must contain only references: [items].')
        }
        (value.references as List).eachWithIndex { Object item, int i ->
            if (!(item instanceof Map) || !(item.title instanceof String) ||
                    !item.title.trim() || !(item.url instanceof String) ||
                    !item.url.trim() ||
                    (item.containsKey('description') && !(item.description instanceof String))) {
                throw new GradleException('Invalid Reference entry #' + (i + 1) + ' at L3.')
            }
        }
        ContractSchemaValidateUtils.validateData('reference', value)
        return value as Map
    }

    static File directory(File root, String moduleLocation) {
        // Shares the same safe I3/module contract as the other Sheet importers.
        File module = RoadmapSheetImportService.resolveDestination(root, moduleLocation)
                .parentFile.parentFile.parentFile.parentFile
        return new File(module, 'content/reference')
    }

    private static String raw(List<List<Object>> rows, int index) {
        return index < rows.size() && rows[index] ? (rows[index][0]?.toString() ?: '') : ''
    }
}
