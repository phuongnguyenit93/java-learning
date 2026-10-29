package com.example.learning.task.sheets.service

import org.gradle.api.GradleException

/**
 * STEP 5 menu identity contract: C on the parent is the chapter base ID;
 * A on each child is the stable Knowledge anchor. C:N on children is Knowledge.
 *
 * Never rewrite an existing ID. Initial fill uses row order; on a partially
 * assigned chapter new IDs grow beyond the greatest existing suffix, so
 * reordering and reinserting lessons do not reuse gaps in existing anchors.
 */
class MenuLessonIdService {
    static boolean isLessonId(String id) {
        return id != null && (id ==~ /[a-z][a-z0-9]*(?:-[a-z0-9]+)*-[0-9]{3,}/)
    }

    static String expectedBase(Map group) {
        String filename = group.filename as String
        if (!(filename ==~ /(?i)[a-z][a-z0-9]*\.md/)) {
            throw new GradleException('Invalid chapter filename in B' + group.parentRow)
        }
        return ReadmeSheetImportService.slugFromStem(filename.substring(0, filename.length() - 3))
    }

    /** C20:C, aligned to row 20. Existing child IDs are validated, not generated. */
    static void validate(List<Map> groups, List<List<Object>> cRows) {
        Set<String> allBases = [] as Set
        Set<String> allIds = [] as Set
        groups.each { Map group ->
            String base = cell(cRows, (group.parentRow as int) - 20)
            String expected = expectedBase(group)
            if (base != expected || !allBases.add(base)) {
                throw new GradleException("Invalid/duplicate chapter base ID at C${group.parentRow}: " +
                        "'${base}', expected '${expected}'.")
            }
            (group.children as List<Map>).each { Map child ->
                String id = child.id as String
                if (!isLessonId(id) || !(id ==~ /\Q${base}\E-[0-9]{3,}/) ||
                        !allIds.add(id)) {
                    throw new GradleException("Invalid/missing/duplicate lesson ID at A${child.row}: '${id}'.")
                }
            }
        }
    }

    /** Pure preflight; return ONLY missing A child cells, never touch A parents. */
    static Map<Integer, String> plan(List<Map> groups, List<List<Object>> cRows) {
        Map<Integer, String> writes = new LinkedHashMap<>()
        Set<String> bases = [] as Set
        Set<String> globalIds = [] as Set
        groups.each { Map group ->
            String base = cell(cRows, (group.parentRow as int) - 20)
            String expected = expectedBase(group)
            if (base != expected || !bases.add(base)) {
                throw new GradleException("Invalid/duplicate chapter base ID at C${group.parentRow}: " +
                        "'${base}', expected '${expected}'.")
            }
            List<Map> children = group.children as List<Map>
            Set<String> existing = [] as Set
            children.each { Map child ->
                String id = child.id as String
                if (id) {
                    if (!isLessonId(id) || !(id ==~ /\Q${base}\E-[0-9]{3,}/) ||
                            !existing.add(id) || !globalIds.add(id)) {
                        throw new GradleException("Invalid/duplicate lesson ID at A${child.row}: '${id}'.")
                    }
                }
            }
            int next = existing.isEmpty() ? 1 :
                    existing.collect { String value ->
                        new BigInteger(value.substring(value.lastIndexOf('-') + 1))
                    }.max().intValueExact() + 1
            children.each { Map child ->
                if (child.id) return
                String id = "${base}-${String.format('%03d', next++)}"
                if (!existing.add(id) || !globalIds.add(id)) {
                    throw new GradleException('Duplicate generated lesson ID: ' + id)
                }
                writes[child.row as int] = id
            }
        }
        return writes
    }

    private static String cell(List<List<Object>> rows, int offset) {
        List<Object> row = offset >= 0 && offset < rows.size() ? (rows[offset] ?: []) : []
        return row ? (row[0]?.toString()?.trim() ?: '') : ''
    }
}
