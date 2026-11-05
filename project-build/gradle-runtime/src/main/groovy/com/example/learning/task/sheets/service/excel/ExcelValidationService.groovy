package com.example.learning.task.sheets.service.excel

import com.example.learning.task.sheets.service.ModuleAssessmentSheetImportService
import com.example.learning.task.sheets.service.ModuleContentExcelSyncService
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.gradle.api.GradleException

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Phase 12: semantic round-trip, POI reopen, guarded offline atomic publish. */
class ExcelValidationService {
    void validate(ModuleExcelContext ctx) {
        if (ctx.workbook.numberOfSheets != 1 || ctx.text(1, 2) != ctx.serviceName ||
                ctx.text(3, 9) != ctx.location || ctx.text(19, 28) ||
                ctx.text(19, 29) != 'Summary YAML' ||
                ctx.text(19, 16) != 'Interview YAML' ||
                ctx.text(19, 19) != 'CaseStudy YAML' ||
                ctx.text(19, 22) != 'Quiz YAML') {
            throw new GradleException('STEP 12 XLSX header or module identity mismatch.')
        }
        Set<String> lessonIds = [] as Set
        ctx.chapters.each { Map group ->
            Map parsed = ModuleContentExcelSyncService.parseMarkdown(
                    ctx.read('content/readme/docs/' + ctx.language + '/menu/' +
                            group.name + '/knowledge/' + group.filename))
            if (ctx.text(group.row as int, 1) == '' ||
                    ctx.text(group.row as int, 1) != parsed.title ||
                    ctx.text(group.row as int, 2) != group.filename ||
                    ctx.text(group.row as int, 3) != group.base) {
                throw new GradleException('STEP 12 chapter identity changed: ' + group.name)
            }
            (group.children as List<Map>).eachWithIndex { Map child, int i ->
                String id = child.id as String
                if (!lessonIds.add(id) || ctx.text(child.row as int, 1) != id ||
                        ctx.text(child.row as int, 2) != child.title ||
                        ctx.joined(child.row as int, 3, 1, 12) !=
                                (parsed.sections as List<Map>)[i].body ||
                        !(ctx.text(child.row as int, 15) in
                                ['BASIC','INTERMEDIATE','ADVANCED'])) {
                    throw new GradleException('STEP 12 Knowledge mismatch: ' + id)
                }
            }
            if (ctx.text(group.row as int, 29) !=
                    ctx.read('content/readme/docs/' + ctx.language + '/menu/' +
                            group.name + '/summary/section.yml')) {
                throw new GradleException('STEP 12 Summary missing for ' + group.name)
            }
        }
        if (ctx.text(17, 2) != ctx.read('content/readme/docs/' +
                ctx.language + '/BASE.md') ||
                ctx.text(3, 12) != ctx.read('content/reference/' +
                        ctx.language + '/references.yml')) {
            throw new GradleException('STEP 12 Introduction/Reference round-trip mismatch.')
        }
        Map<String, String> main = [
                interview: ctx.joined(20, 16, 5, 3),
                casestudy: ctx.joined(20, 19, 5, 3),
                quiz: ctx.joined(20, 22, 5, 2)
        ]
        List<List<Object>> interviewPhase = phase4(ctx, 16,
                (ctx.assessment.interview.interviewQuestion as List).size())
        List<List<Object>> casePhase = phase4(ctx, 19,
                (ctx.assessment.casestudy.caseStudy as List).size())
        Map<String, String> assembled = ModuleAssessmentSheetImportService.assemble(
                main, lessonIds, [interview: interviewPhase, casestudy: casePhase])
        ctx.assessment.each { String kind, Map original ->
            Map recovered = ctx.yaml(assembled[kind], 'reconstructed ' + kind)
            if (recovered != original) {
                throw new GradleException('STEP 12 assessment round-trip lost ' + kind)
            }
        }
    }

    private static List<List<Object>> phase4(ModuleExcelContext ctx, int first, int count) {
        List<List<Object>> result = []
        for (int row = 27; row < 27 + count * 3; row++) {
            result.add((first..(first + 2)).collect { int col -> ctx.text(row, col) })
        }
        return result
    }

    List<Map> finish(List<ModuleExcelContext> sources, boolean apply) {
        sources.each { validate(it) }
        List<Map> jobs = sources.collect { ModuleExcelContext ctx ->
            File output = ctx.file('content/sheet/' + ctx.language + '/module.xlsx')
            if (output.exists() && !output.isFile()) {
                throw new GradleException('Non-regular STEP 12 Excel destination: ' + output)
            }
            [ctx: ctx, output: output,
             before: output.isFile() ? ModuleExcelContext.sha(output.bytes) : null]
        }
        if (!apply) {
            return jobs.collect { Map job ->
                ModuleExcelContext ctx = job.ctx as ModuleExcelContext
                ByteArrayOutputStream stream = new ByteArrayOutputStream()
                ctx.workbook.write(stream)
                [language: ctx.language, output: job.output,
                 sha256: ModuleExcelContext.sha(stream.toByteArray()),
                 chapters: ctx.chapters.size(),
                 lessons: ctx.chapters.sum(0) { Map group ->
                     (group.children as List).size()
                 },
                 interview: (ctx.assessment.interview.interviewQuestion as List)
                         .sum(0) { it.questions.size() },
                 cases: (ctx.assessment.casestudy.caseStudy as List)
                         .sum(0) { it.cases.size() },
                 quiz: (ctx.assessment.quiz.quiz as List)
                         .sum(0) { it.questions.size() }]
            }
        }
        List<Map> committed = []
        boolean safeToCleanup = true
        try {
            jobs.each { Map job ->
                File target = job.output as File
                Files.createDirectories(target.parentFile.toPath())
                job.temp = Files.createTempFile(target.parentFile.toPath(),
                        '.module-xlsx-stage-', '.xlsx')
                Files.newOutputStream(job.temp as Path).withCloseable { stream ->
                    (job.ctx as ModuleExcelContext).workbook.write(stream)
                }
                Files.newInputStream(job.temp as Path).withCloseable { stream ->
                    new XSSFWorkbook(stream).withCloseable { XSSFWorkbook check ->
                        ModuleExcelContext ctx = job.ctx as ModuleExcelContext
                        XSSFWorkbook previous = ctx.workbook
                        def sheet = ctx.sheet
                        try {
                            ctx.workbook = check
                            ctx.sheet = check.getSheetAt(0)
                            validate(ctx)
                        } finally {
                            ctx.workbook = previous
                            ctx.sheet = sheet
                        }
                    }
                }
                if (job.before != null) {
                    job.backup = Files.createTempFile(target.parentFile.toPath(),
                            '.module-xlsx-backup-', '.xlsx')
                    Files.copy(target.toPath(), job.backup as Path,
                            StandardCopyOption.REPLACE_EXISTING)
                }
            }
            jobs.each { Map job ->
                File target = job.output as File
                if (job.before != (target.isFile() ?
                        ModuleExcelContext.sha(target.bytes) : null)) {
                    throw new GradleException('STEP 12 Excel destination changed concurrently: ' +
                            target)
                }
                Files.move(job.temp as Path, target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                committed.add(job)
            }
            return jobs.collect { Map job ->
                ModuleExcelContext ctx = job.ctx as ModuleExcelContext
                [language: ctx.language, output: job.output,
                 sha256: ModuleExcelContext.sha((job.output as File).bytes),
                 chapters: ctx.chapters.size(),
                 lessons: ctx.chapters.sum(0) { Map group ->
                     (group.children as List).size()
                 },
                 interview: (ctx.assessment.interview.interviewQuestion as List)
                         .sum(0) { it.questions.size() },
                 cases: (ctx.assessment.casestudy.caseStudy as List)
                         .sum(0) { it.cases.size() },
                 quiz: (ctx.assessment.quiz.quiz as List)
                         .sum(0) { it.questions.size() }]
            }
        } catch (Exception failure) {
            List<String> restoreErrors = []
            committed.reverseEach { Map job ->
                try {
                    File target = job.output as File
                    if (job.backup) {
                        Files.move(job.backup as Path, target.toPath(),
                                StandardCopyOption.REPLACE_EXISTING,
                                StandardCopyOption.ATOMIC_MOVE)
                    } else {
                        Files.deleteIfExists(target.toPath())
                    }
                } catch (Exception e) {
                    restoreErrors.add((job.output as File).path + ': ' + e.message)
                }
            }
            if (restoreErrors) {
                safeToCleanup = false
                throw new GradleException('STEP 12 rollback incomplete: ' +
                        restoreErrors.join('; ') + '; original error: ' + failure.message, failure)
            }
            throw failure
        } finally {
            jobs.each { Map job ->
                if (job.temp) Files.deleteIfExists(job.temp as Path)
                if (safeToCleanup && job.backup) {
                    Files.deleteIfExists(job.backup as Path)
                }
            }
        }
    }
}
