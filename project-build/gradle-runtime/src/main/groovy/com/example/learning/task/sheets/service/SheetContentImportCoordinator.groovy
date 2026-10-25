package com.example.learning.task.sheets.service

import org.gradle.api.GradleException

/**
 * One import pipeline for both task entrypoints.
 * A content service owns each output format, while this coordinator owns
 * tab selection, Module Location checks, sequencing and batch reporting.
 */
class SheetContentImportCoordinator {

    private final SheetsMetadataReaderService sheetsReader
    private final RoadmapSheetImportService roadmapImporter
    private final ReadmeSheetImportService readmeImporter
    private final VideoSheetImportService videoImporter
    private final InterviewSheetImportService interviewImporter
    private final QuizSheetImportService quizImporter

    SheetContentImportCoordinator(
            SheetsMetadataReaderService sheetsReader = new SheetsMetadataReaderService(),
            RoadmapSheetImportService roadmapImporter = new RoadmapSheetImportService(),
            ReadmeSheetImportService readmeImporter = new ReadmeSheetImportService(),
            VideoSheetImportService videoImporter = new VideoSheetImportService(),
            InterviewSheetImportService interviewImporter = new InterviewSheetImportService(),
            QuizSheetImportService quizImporter = new QuizSheetImportService()
    ) {
        this.sheetsReader = sheetsReader
        this.roadmapImporter = roadmapImporter
        this.readmeImporter = readmeImporter
        this.videoImporter = videoImporter
        this.interviewImporter = interviewImporter
        this.quizImporter = quizImporter
    }

    Map<String, Object> importByLocation(
            File rootDirectory,
            File moduleDirectory,
            String fileUrl,
            String gid,
            boolean apply
    ) {
        return importPartByLocation(
                rootDirectory, moduleDirectory, fileUrl, gid, apply, 'ALL'
        )
    }

    /**
     * Module-local import, limited to one output type when requested.
     * The complete ALL pipeline and root importByFiles remain unchanged.
     */
    Map<String, Object> importPartByLocation(
            File rootDirectory,
            File moduleDirectory,
            String fileUrl,
            String gid,
            boolean apply,
            String part
    ) {
        if (!(part in ['ALL', 'ROADMAP', 'README', 'VIDEO', 'INTERVIEW', 'QUIZ'])) {
            throw new GradleException(
                    "Invalid Sheet import part '${part}'. Use ALL, ROADMAP, README, VIDEO, INTERVIEW or QUIZ."
            )
        }
        File root = rootDirectory.canonicalFile
        File module = moduleDirectory.canonicalFile
        if (module == root || !module.toPath().startsWith(root.toPath())) {
            throw new GradleException(
                    "ByLocation task must run inside the repository: ${module}."
            )
        }
        String expected = root.toPath().relativize(module.toPath())
                .toString().replace(File.separatorChar, '/' as char)
        Map<String, Object> context = sheetsReader.openTab(fileUrl, gid)
        if (context.moduleLocation != expected) {
            throw new GradleException(
                    "Module Location mismatch at tab '${context.tabTitle}' (gid=${gid}), I3: " +
                            "task module='${expected}', Sheet='${context.moduleLocation}'."
            )
        }
        RoadmapSheetImportService.resolveDestination(root, expected)
        switch (part) {
            case 'ROADMAP':
                return roadmapImporter.execute(root, context, apply)
            case 'README':
                return readmeImporter.execute(root, context, apply)
            case 'VIDEO':
                return videoImporter.execute(root, context, apply)
            case 'INTERVIEW':
                return interviewImporter.execute(root, context, apply)
            case 'QUIZ':
                return quizImporter.execute(root, context, apply)
            default:
                return importTab(root, context, apply)
        }
    }

    /**
     * Resolve ALL I3 values before invoking an importer. This prevents
     * duplicate targets from being written even in APPLY mode.
     * Other unrelated tabs continue on failure; caller reports a final
     * failure status after all tabs have been attempted.
     */
    Map<String, Object> importByFiles(
            File rootDirectory,
            String fileUrl,
            boolean apply
    ) {
        Map<String, Object> spreadsheet = sheetsReader.openSpreadsheet(fileUrl)
        List<Map<String, Object>> tabs =
                ((spreadsheet.tabs ?: []) as List<Map<String, Object>>)
                        .findAll { Map tab -> tab.title != 'Template' }
        List<Map<String, Object>> entries = []
        Map<String, List<Map<String, Object>>> byTarget = [:].withDefault { [] }

        tabs.each { Map tab ->
            Map<String, Object> item = [
                    tab   : tab.title,
                    gid   : tab.gid,
                    status: 'PENDING'
            ]
            entries.add(item)
            try {
                Map<String, Object> context =
                        sheetsReader.readTab(spreadsheet, tab.gid as String)
                File target = RoadmapSheetImportService.resolveDestination(
                        rootDirectory, context.moduleLocation as String
                )
                item.context = context
                item.location = context.moduleLocation
                byTarget[target.canonicalPath.toLowerCase(Locale.ROOT)].add(item)
            }
            catch (Exception error) {
                item.status = 'FAILED'
                item.error = error.message
            }
        }

        byTarget.values().each { List<Map<String, Object>> owners ->
            if (owners.size() > 1) {
                String labels = owners.collect { Map item ->
                    "${item.tab} (gid=${item.gid})"
                }.join(', ')
                owners.each { Map item ->
                    item.status = 'FAILED'
                    item.error = "Duplicate Module Location I3 across tabs: ${labels}."
                }
            }
        }

        entries.findAll { Map item -> item.status == 'PENDING' }.each { Map item ->
            try {
                Map<String, Object> result = importTab(
                        rootDirectory, item.context as Map<String, Object>, apply
                )
                item.status = result.status
                item.milestones = result.milestones
                item.file = result.file
                item.clearedOldFiles = result.clearedOldFiles
                item.readme = result.readme
                item.video = result.video
                item.interview = result.interview
                item.quiz = result.quiz
            }
            catch (Exception error) {
                item.status = 'FAILED'
                item.error = error.message
            }
            finally {
                item.remove('context')
            }
        }
        entries.each { Map item -> item.remove('context') }

        return [
                spreadsheet: spreadsheet.spreadsheetTitle,
                skippedTemplate: ((spreadsheet.tabs ?: []) as List).count { Map tab ->
                    tab.title == 'Template'
                },
                results: entries,
                total  : entries.size(),
                failed : entries.count { Map item -> item.status == 'FAILED' }
        ]
    }

    private Map<String, Object> importTab(
            File rootDirectory, Map<String, Object> context, boolean apply
    ) {
        Map<String, Object> roadmap = roadmapImporter.execute(
                rootDirectory, context, apply
        )
        Map<String, Object> readme = readmeImporter.execute(
                rootDirectory, context, apply
        )
        Map<String, Object> video = videoImporter.execute(
                rootDirectory, context, apply
        )
        Map<String, Object> interview = interviewImporter.execute(
                rootDirectory, context, apply
        )
        Map<String, Object> quiz = quizImporter.execute(
                rootDirectory, context, apply
        )
        return roadmap + [readme: readme, video: video, interview: interview,
                          quiz: quiz]
    }
}
