package com.example.learning.task.sheets.service

import org.gradle.api.GradleException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

import static org.junit.Assert.*

class RoadmapSheetImportServiceTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder()

    @Test
    void skipsWhenFirstMilestoneRowIsEntirelyBlank() {
        List<Object> header = ['Tiêu đề', 'Mục đích', 'Mục tiêu học tập']

        // Google Sheets omits missing/trailing blank cells and rows.
        assertTrue(RoadmapSheetImportService.isFirstMilestoneEmpty([header]))
        assertTrue(RoadmapSheetImportService.isFirstMilestoneEmpty([header, []]))
        assertTrue(RoadmapSheetImportService.isFirstMilestoneEmpty(
                [header, ['  ', '\t', '\n']]
        ))

        // Any actual content in F3, G3 or H3 must still be validated.
        assertFalse(RoadmapSheetImportService.isFirstMilestoneEmpty(
                [header, ['Lambda', '', '']]
        ))
        assertFalse(RoadmapSheetImportService.isFirstMilestoneEmpty(
                [header, ['', 'Purpose']]
        ))
        assertFalse(RoadmapSheetImportService.isFirstMilestoneEmpty(
                [header, ['', '', 'Objective']]
        ))
        expectFailure {
            RoadmapSheetImportService.convertRoadmap([['', 'Purpose', '']])
        }
    }

    @Test
    void convertsVietnameseMilestonesAndRoundTripsYaml() {
        List<Map<String, Object>> rows = RoadmapSheetImportService.convertRoadmap([
                ['Toàn cảnh Java 8', 'Học API hiện đại', 'Lambda và Stream\nOptional và thời gian'],
                ['Đóng gói và đa hình', 'Phân biệt hành vi', 'Kiểm tra hợp đồng\r\nThực hành']
        ])
        assertEquals(['toan-canh-java-8', 'dong-goi-va-da-hinh'], rows*.id)
        assertEquals(['Lambda và Stream', 'Optional và thời gian'], rows[0].objectives)

        String yaml = RoadmapSheetImportService.buildYaml(rows)
        assertTrue(yaml.startsWith('# <roadmap-schema>'))
        Map document = new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml)
        assertEquals(rows, document.roadmap)
        assertEquals(yaml, RoadmapSheetImportService.buildYaml(rows))
    }

    @Test
    void buildsRoadmapFromSheetWithoutCarryingOldIdsOrRelations() {
        List<Map<String, Object>> candidate = RoadmapSheetImportService.convertRoadmap([
                ['Lambda', 'Purpose', 'One\nTwo']
        ])
        String imported = RoadmapSheetImportService.buildYaml(candidate)
        assertFalse(imported.contains('original-stable-id'))
        assertFalse(imported.contains('relatedKnowledge:'))
        assertTrue(imported.contains('id: lambda'))
    }

    @Test
    void acceptsUpdatedSheetContentButRejectsInvalidSource() {
        List<Map<String, Object>> source = RoadmapSheetImportService.convertRoadmap([
                ['Lambda', 'Purpose', 'One\nTwo']
        ])
        expectFailure {
            RoadmapSheetImportService.convertRoadmap([
                    ['Lambda', 'Purpose', 'One'],
                    [],
                    ['Stream', 'Purpose', 'Two']
            ])
        }
        expectFailure {
            RoadmapSheetImportService.convertRoadmap([
                    ['Lambda', 'Purpose', 'One'],
                    ['Lambda!', 'Purpose', 'Two']
            ])
        }
        expectFailure {
            RoadmapSheetImportService.convertRoadmap([['Lambda', '', 'One']])
        }
        assertTrue(RoadmapSheetImportService.buildYaml(source).contains('Purpose'))
    }

    @Test
    void applyReplacesWholeViRoadmapDirectoryWithoutBackup() {
        File root = temporary.newFolder('sheet-source-of-truth')
        File destination = RoadmapSheetImportService.resolveDestination(root, 'module/example')
        assertTrue(destination.parentFile.mkdirs())
        String oldFile = 'metadata: legacy\nroadmap: []\n'
        destination.setText(oldFile, 'UTF-8')
        File stale = new File(destination.parentFile, 'legacy.yml')
        stale.setText('obsolete', 'UTF-8')
        List<Map<String, Object>> sheet = RoadmapSheetImportService.convertRoadmap([
                ['Lambda', 'New purpose', 'Objective one\nObjective two'],
                ['Stream', 'New milestone', 'Third objective']
        ])
        assertEquals('DRY_RUN', RoadmapSheetImportService.syncRoadmap(
                root, destination, oldFile, sheet, false).status)
        assertTrue(stale.exists())
        Map result = RoadmapSheetImportService.syncRoadmap(
                root, destination, oldFile, sheet, true)
        assertEquals('UPDATED', result.status)
        assertEquals(2, result.clearedOldFiles)
        assertFalse(stale.exists())
        assertFalse(new File(root, '.gradle/sheet-roadmap-backups').exists())
        String newContent = destination.getText('UTF-8')
        assertFalse(newContent.contains('metadata: legacy'))
        Map yaml = new Yaml(new SafeConstructor(new LoaderOptions())).load(newContent)
        assertEquals(sheet, yaml.roadmap)
        assertEquals('UNCHANGED', RoadmapSheetImportService.syncRoadmap(
                root, destination, newContent, sheet, true).status)
        assertEquals(newContent, destination.getText('UTF-8'))

        Map empty = RoadmapSheetImportService.syncRoadmap(
                root, destination, newContent, [], true)
        assertEquals('CLEARED_EMPTY', empty.status)
        assertFalse(destination.exists())
        assertEquals([:], SheetSnapshotReplaceService.readTree(destination.parentFile))
        assertFalse(new File(root, '.gradle/sheet-roadmap-backups').exists())
    }
    @Test
    void validatesDestinationWithoutRequiringGradleModule() {
        File root = temporary.newFolder('repo')
        File module = new File(root, 'module')
        assertTrue(module.mkdirs())
        File result = RoadmapSheetImportService.resolveDestination(
                root, 'module/platform/development/programming/language/java/version/java8'
        )
        assertTrue(result.path.replace('\\', '/').endsWith(
                'java8/roadmap/vi/roadmap.yml'
        ))
        assertFalse(result.exists())
        for (String invalid : [
                '../outside', 'module/../outside', 'module//java8',
                'module/../../outside', 'module/drive:C', '/module/java8'
        ]) {
            expectFailure {
                RoadmapSheetImportService.resolveDestination(root, invalid)
            }
        }
    }

    private static void expectFailure(Closure action) {
        try {
            action.call()
            fail('Expected GradleException')
        }
        catch (GradleException expected) {
            // Correct: no partial import on invalid input.
        }
    }
}
