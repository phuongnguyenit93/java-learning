package com.example.learning.task.sheets.service.excel

import groovy.json.JsonSlurper
import org.gradle.api.GradleException

/** STEP 12: one module-local task, 12 deterministic services per language. */
class ModuleExcelGenerationService {
    Map execute(File rootDirectory, File moduleDirectory, List<String> requested,
                boolean apply) {
        File root = rootDirectory.canonicalFile
        File module = moduleDirectory.canonicalFile
        File moduleRoot = new File(root, 'module').canonicalFile
        if (!module.isDirectory() || !module.toPath().startsWith(moduleRoot.toPath()) ||
                module == moduleRoot || !new File(module, 'gradle.properties').isFile()) {
            throw new GradleException('STEP 12 must run at a real module Gradle project.')
        }
        String location = root.toPath().relativize(module.toPath())
                .toString().replace(File.separatorChar, '/' as char)
        Map master = new JsonSlurper().parse(new File(module, 'master.json')) as Map
        String service = master.SERVICE_NAME?.VALUE as String
        List<String> declared = master.MODULE_LANGUAGE?.VALUE as List<String>
        if (!service?.trim() || !(service ==~ /[A-Z][A-Z0-9_]*/) ||
                !(declared instanceof List) || !declared ||
                declared.any { !(it instanceof String) || !(it ==~ /[a-z]{2}/) }) {
            throw new GradleException('Invalid STEP 12 SERVICE_NAME/MODULE_LANGUAGE in master.json.')
        }
        List<String> languages = requested ? requested : declared
        if (!languages || languages.toSet().size() != languages.size() ||
                languages.any { !declared.contains(it) }) {
            throw new GradleException('STEP 12 languageList must select unique declared languages.')
        }
        List<ModuleExcelContext> contexts = []
        try {
            languages.each { String lang ->
                ModuleExcelContext ctx = new ModuleExcelContext(module, location, lang, service)
                contexts.add(ctx)
                new ExcelWorkbookService().fill(ctx)      // 1
                new ExcelHeaderService().fill(ctx)        // 2
                new ExcelRoadmapService().fill(ctx)       // 3
                new ExcelReferenceService().fill(ctx)     // 4
                new ExcelIntroductionService().fill(ctx)  // 5
                new ExcelKnowledgeService().fill(ctx)     // 6
                new ExcelSummaryService().fill(ctx)       // 7
                new ExcelInterviewService().fill(ctx)     // 8
                new ExcelCaseStudyService().fill(ctx)     // 9
                new ExcelQuizService().fill(ctx)          // 10
                new ExcelVideoService().fill(ctx)         // 11
            }
            List<Map> files = new ExcelValidationService().finish(contexts, apply) // 12
            return [status: apply ? 'EXPORTED' : 'DRY_RUN',
                    module: location, files: files]
        } finally {
            contexts.each { it.close() }
        }
    }
}
