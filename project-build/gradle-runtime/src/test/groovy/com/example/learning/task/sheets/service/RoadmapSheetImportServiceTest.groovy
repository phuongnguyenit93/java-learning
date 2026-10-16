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

        String yaml = RoadmapSheetImportService.buildYaml(null, rows)
        assertTrue(yaml.startsWith('# <roadmap-schema>'))
        Map document = new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml)
        assertEquals(rows, document.roadmap)
        assertEquals('UNCHANGED', RoadmapSheetImportService.determineAction(yaml, rows))
    }

    @Test
    void recognizesEmptySkeletonAndUpdatesOldIdsAndRelations() {
        List<Map<String, Object>> candidate = RoadmapSheetImportService.convertRoadmap([
                ['Lambda', 'Purpose', 'One\nTwo']
        ])
        assertEquals('CREATE',
                RoadmapSheetImportService.determineAction(
                        '# <roadmap-schema>\n# preserved\n# </roadmap-schema>\n\nroadmap: []\n',
                        candidate))
        String imported = RoadmapSheetImportService.buildYaml(
                '# <roadmap-schema>\n# preserved\n# </roadmap-schema>\n\nroadmap: []\n',
                candidate
        )
        assertTrue(imported.contains('# preserved'))
        assertEquals('UPDATE',
                RoadmapSheetImportService.determineAction(
                        imported.replace('id: lambda', 'id: original-stable-id')
                                .replace('title: Lambda', 'relatedKnowledge: [Category]\n    title: Lambda'),
                        candidate
                ))
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
        assertEquals('UPDATE', RoadmapSheetImportService.determineAction(
                RoadmapSheetImportService.buildYaml(null, source)
                        .replace('Purpose', 'Changed purpose'),
                source
        ))
    }

    @Test
    void applyReplacesPopulatedRoadmapBacksUpOldValuesAndIsIdempotent() {
        File root = temporary.newFolder('sheet-source-of-truth')
        File destination = RoadmapSheetImportService.resolveDestination(
                root, 'module/example'
        )
        assertTrue(destination.parentFile.mkdirs())
        String oldFile = '''# <roadmap-schema>
# old comments stay
# </roadmap-schema>

roadmap:
  - id: original-stable-id
    title: Lambda
    purpose: old purpose
    objectives: [Old objective]
    relatedKnowledge: [Legacy]
'''
        destination.setText(oldFile, 'UTF-8')
        List<Map<String, Object>> sheet = RoadmapSheetImportService.convertRoadmap([
                ['Lambda', 'New purpose', 'Objective one\nObjective two'],
                ['Stream', 'New milestone', 'Third objective']
        ])

        Map dryRun = RoadmapSheetImportService.syncRoadmap(
                root, destination, oldFile, sheet, false
        )
        assertEquals('DRY_RUN', dryRun.status)
        assertEquals(oldFile, destination.getText('UTF-8'))
        assertFalse(new File(root, '.gradle/sheet-roadmap-backups').exists())

        Map applied = RoadmapSheetImportService.syncRoadmap(
                root, destination, oldFile, sheet, true
        )
        assertEquals('UPDATED', applied.status)
        assertTrue((applied.backup as File).isFile())
        assertEquals(oldFile, (applied.backup as File).getText('UTF-8'))
        String updated = destination.getText('UTF-8')
        assertTrue(updated.contains('# old comments stay'))
        Map yaml = new Yaml(new SafeConstructor(new LoaderOptions())).load(updated)
        assertEquals(sheet, yaml.roadmap)
        assertFalse(updated.contains('original-stable-id'))
        assertFalse(updated.contains('relatedKnowledge'))
        assertEquals(['lambda', 'stream'], yaml.roadmap*.id)

        Map again = RoadmapSheetImportService.syncRoadmap(
                root, destination, updated, sheet, true
        )
        assertEquals('UNCHANGED', again.status)
        assertNull(again.backup)
        assertEquals(updated, destination.getText('UTF-8'))
        assertEquals(1, (applied.backup as File).parentFile.listFiles().size())

        List<Map<String, Object>> revised = RoadmapSheetImportService.convertRoadmap([
                ['Lambda', 'Revised later', 'Objective one'],
                ['Stream', 'New milestone', 'Third objective']
        ])
        Map secondUpdate = RoadmapSheetImportService.syncRoadmap(
                root, destination, updated, revised, true
        )
        assertEquals('UPDATED', secondUpdate.status)
        assertEquals(updated, (secondUpdate.backup as File).getText('UTF-8'))
        Map current = new Yaml(new SafeConstructor(new LoaderOptions()))
                .load(destination.getText('UTF-8'))
        assertEquals(revised, current.roadmap)
        assertEquals(2, (applied.backup as File).parentFile.listFiles().size())
    }

    @Test
    void preservesNonRoadmapYamlByRefusingOverwrite() {
        List<Map<String, Object>> milestones = RoadmapSheetImportService.convertRoadmap([
                ['Lambda', 'Purpose', 'Objective']
        ])
        expectFailure {
            RoadmapSheetImportService.determineAction(
                    'metadata: protected\nroadmap: []\n', milestones
            )
        }
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
