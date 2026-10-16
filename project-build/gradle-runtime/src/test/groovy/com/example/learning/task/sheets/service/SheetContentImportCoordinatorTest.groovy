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
                        file: new File(root, context.moduleLocation + '/roadmap/vi/roadmap.yml')
                ]
            }
        }
        ReadmeSheetImportService readme = new ReadmeSheetImportService() {
            @Override
            Map<String, Object> execute(File root, Map<String, Object> context, boolean apply) {
                return [status: 'SKIPPED_EMPTY_MENU', groups: 0, lessons: 0]
            }
        }
        return new SheetContentImportCoordinator(reader, roadmap, readme)
    }
}
