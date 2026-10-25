package com.example.learning.task.sheets.service

import org.gradle.api.GradleException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.junit.Assert.*

class SheetContentImportCoordinatorTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder()

    private static final String ROOT = 'module/platform/development/programming/language/java/version/'

    @Test
    void skipsTemplateAndImportsEachDistinctTabInOrder() {
        List<String> processed = []
        SheetContentImportCoordinator coordinator = fakeCoordinator(
                [
                        [title: 'Template', gid: '0'],
                        [title: 'Java 8', gid: '8'],
                        [title: 'Java 11', gid: '11']
                ],
                ['8': ROOT + 'java8', '11': ROOT + 'java11'],
                processed
        )
        Map report = coordinator.importByFiles(temporary.newFolder('repo'), 'https://example', false)
        assertEquals(1, report.skippedTemplate)
        assertEquals(2, report.total)
        assertEquals(0, report.failed)
        assertEquals(['8', '11'], processed)
        assertEquals(['DRY_RUN', 'DRY_RUN'], report.results*.status)
    }

    @Test
    void duplicateI3FailsBothOwnersBeforeAnyWrite() {
        List<String> processed = []
        SheetContentImportCoordinator coordinator = fakeCoordinator(
                [
                        [title: 'A', gid: '1'], [title: 'B', gid: '2'],
                        [title: 'C', gid: '3']
                ],
                ['1': ROOT + 'java8', '2': ROOT + 'java8', '3': ROOT + 'java11'],
                processed
        )
        Map report = coordinator.importByFiles(temporary.newFolder('repo'), 'https://example', true)
        assertEquals(2, report.failed)
        assertEquals(['FAILED', 'FAILED', 'IMPORTED'], report.results*.status)
        assertTrue(report.results[0].error.contains('Duplicate Module Location'))
        assertEquals(['3'], processed)
    }

    @Test
    void brokenTabDoesNotPreventIndependentTabs() {
        List<String> processed = []
        SheetContentImportCoordinator coordinator = fakeCoordinator(
                [[title: 'Broken', gid: '1'], [title: 'Ready', gid: '2']],
                ['1': '../outside', '2': ROOT + 'java8'], processed
        )
        Map report = coordinator.importByFiles(temporary.newFolder('repo'), 'https://example', false)
        assertEquals(1, report.failed)
        assertEquals(['FAILED', 'DRY_RUN'], report.results*.status)
        assertEquals(['2'], processed)
    }

    @Test
    void byLocationMustMatchRealGradleModuleDirectory() {
        List<String> processed = []
        File root = temporary.newFolder('repo')
        File module = new File(root, ROOT + 'java8')
        assertTrue(module.mkdirs())
        SheetContentImportCoordinator wrong = fakeCoordinator(
                [[title: 'Java 8', gid: '8']],
                ['8': ROOT + 'java11'], processed
        )
        try {
            wrong.importByLocation(root, module, 'https://example', '8', true)
            fail('Mismatched I3 must stop before any write')
        }
        catch (GradleException expected) {
            assertTrue(expected.message.contains('Module Location mismatch'))
        }
        assertTrue(processed.isEmpty())

        SheetContentImportCoordinator correct = fakeCoordinator(
                [[title: 'Java 8', gid: '8']],
                ['8': ROOT + 'java8'], processed
        )
        Map result = correct.importByLocation(root, module, 'https://example', '8', false)
        assertEquals('DRY_RUN', result.status)
        assertEquals(['8'], processed)
    }

    @Test
    void moduleLocalPartsRunOnlyTheirOwnImporterAndAllKeepsOriginalOrder() {
        File root = temporary.newFolder('separate-parts')
        File module = new File(root, ROOT + 'java8')
        assertTrue(module.mkdirs())
        List<String> called = []
        SheetContentImportCoordinator coordinator = selectiveCoordinator(
                ROOT + 'java8', called
        )
        ['ROADMAP', 'README', 'VIDEO', 'INTERVIEW', 'QUIZ'].each { String part ->
            called.clear()
            Map result = coordinator.importPartByLocation(
                    root, module, 'https://example', '8', false, part
            )
            assertEquals([part], called)
            assertEquals(part, result.part)
        }

        called.clear()
        Map combined = coordinator.importByLocation(
                root, module, 'https://example', '8', false
        )
        assertEquals(['ROADMAP', 'README', 'VIDEO', 'INTERVIEW', 'QUIZ'], called)
        assertEquals('ROADMAP', combined.part)
        assertEquals('README', combined.readme.part)
        assertEquals('VIDEO', combined.video.part)
        assertEquals('INTERVIEW', combined.interview.part)
        assertEquals('QUIZ', combined.quiz.part)
    }

    @Test
    void singlePartStillEnforcesModuleLocationBeforeAnyImporterIsInvoked() {
        File root = temporary.newFolder('separate-location-guard')
        File module = new File(root, ROOT + 'java8')
        assertTrue(module.mkdirs())
        List<String> called = []
        SheetContentImportCoordinator coordinator = selectiveCoordinator(
                ROOT + 'java11', called
        )
        ['ROADMAP', 'README', 'VIDEO', 'INTERVIEW', 'QUIZ'].each { String part ->
            try {
                coordinator.importPartByLocation(
                        root, module, 'https://example', '8', true, part
                )
                fail(part + ' bypassed the I3 check')
            }
            catch (GradleException expected) {
                assertTrue(expected.message.contains('Module Location mismatch'))
            }
        }
        assertTrue(called.isEmpty())
    }

    private static SheetContentImportCoordinator selectiveCoordinator(
            String location, List<String> called
    ) {
        SheetsMetadataReaderService reader = new SheetsMetadataReaderService() {
            @Override
            Map<String, Object> openTab(String url, String gid) {
                return [tabTitle: 'Java 8', sheetId: gid, moduleLocation: location]
            }
        }
        RoadmapSheetImportService roadmap = new RoadmapSheetImportService() {
            @Override
            Map<String, Object> execute(File root, Map<String, Object> ctx, boolean apply) {
                called.add('ROADMAP')
                return [part: 'ROADMAP', status: 'DRY_RUN']
            }
        }
        ReadmeSheetImportService readme = new ReadmeSheetImportService() {
            @Override
            Map<String, Object> execute(File root, Map<String, Object> ctx, boolean apply) {
                called.add('README')
                return [part: 'README', status: 'DRY_RUN']
            }
        }
        VideoSheetImportService video = new VideoSheetImportService() {
            @Override
            Map<String, Object> execute(File root, Map<String, Object> ctx, boolean apply) {
                called.add('VIDEO')
                return [part: 'VIDEO', status: 'DRY_RUN']
            }
        }
        InterviewSheetImportService interview = new InterviewSheetImportService() {
            @Override
            Map<String, Object> execute(File root, Map<String, Object> ctx, boolean apply) {
                called.add('INTERVIEW')
                return [part: 'INTERVIEW', status: 'DRY_RUN']
            }
        }
        QuizSheetImportService quiz = new QuizSheetImportService() {
            @Override
            Map<String, Object> execute(File root, Map<String, Object> ctx, boolean apply) {
                called.add('QUIZ')
                return [part: 'QUIZ', status: 'DRY_RUN']
            }
        }
        return new SheetContentImportCoordinator(reader, roadmap, readme, video, interview, quiz)
    }

    private static SheetContentImportCoordinator fakeCoordinator(
            List<Map> tabs,
            Map<String, String> locations,
            List<String> processed
    ) {
        SheetsMetadataReaderService reader = new SheetsMetadataReaderService() {
            @Override
            Map<String, Object> openSpreadsheet(String url) {
                return [spreadsheetTitle: 'Test', spreadsheetId: 'spreadsheet', tabs: tabs]
            }

            @Override
            Map<String, Object> readTab(Map<String, Object> document, String gid) {
                Map tab = tabs.find { it.gid == gid }
                return [
                        tabTitle: tab.title, sheetId: gid,
                        moduleLocation: locations[gid]
                ]
            }

            @Override
            Map<String, Object> openTab(String url, String gid) {
                return readTab(openSpreadsheet(url), gid)
            }
        }
        RoadmapSheetImportService roadmap = new RoadmapSheetImportService() {
            @Override
            Map<String, Object> execute(File root, Map<String, Object> context, boolean apply) {
                processed.add(context.sheetId as String)
                return [
                        status: apply ? 'IMPORTED' : 'DRY_RUN',
                        milestones: 3,
                        file: new File(root, context.moduleLocation + '/content/roadmap/vi/roadmap.yml')
                ]
            }
        }
        ReadmeSheetImportService readme = new ReadmeSheetImportService() {
            @Override
            Map<String, Object> execute(File root, Map<String, Object> context, boolean apply) {
                return [status: 'SKIPPED_EMPTY_MENU', groups: 0, lessons: 0]
            }
        }
        VideoSheetImportService video = new VideoSheetImportService() {
            @Override
            Map<String, Object> execute(File root, Map<String, Object> context, boolean apply) {
                return [status: 'SKIPPED_EMPTY_VIDEO', videoCount: 0]
            }
        }
        InterviewSheetImportService interview = new InterviewSheetImportService() {
            @Override
            Map<String, Object> execute(File root, Map<String, Object> context, boolean apply) {
                return [status: 'SKIPPED_EMPTY_INTERVIEW', questionCount: 0]
            }
        }
        QuizSheetImportService quiz = new QuizSheetImportService() {
            @Override
            Map<String, Object> execute(File root, Map<String, Object> context, boolean apply) {
                return [status: 'SKIPPED_EMPTY_QUIZ', questionCount: 0]
            }
        }
        return new SheetContentImportCoordinator(reader, roadmap, readme, video, interview, quiz)
    }
}
