package com.example.learning.task.sheets.service

import org.gradle.api.GradleException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.junit.Assert.*

class ModuleTranslationFileDeliveryServiceTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder()

    @Test
    void preparesWholeFileThenWorkerPublishesWithOwnershipAndValidation() {
        Map fixture = fixture('en')
        File root = fixture.root as File
        File module = fixture.module as File
        String target = 'content/readme/docs/en/BASE.md'
        String run = 'whole-file-en'
        ModuleTranslationCatalogService catalog = new ModuleTranslationCatalogService()
        Map dry = catalog.execute(root, module, run, 'PREPARE', false)
        assertEquals('DRY_RUN', dry.status)
        File work = ModuleTranslationCatalogService.workFile(root, 'module/demo', run, target)
        assertFalse(work.exists())
        assertFalse((dry.manifest as File).exists())

        Map prepared = catalog.execute(root, module, run, 'PREPARE', true)
        assertEquals(1, prepared.jobCount)
        assertEquals(target, prepared.jobs[0].id)
        assertEquals([target], prepared.jobs[0].files)
        assertEquals('en', prepared.targetLanguage)
        assertEquals(new File(module, target).bytes.toList(), work.bytes.toList())
        assertTrue(new File(module, 'build/step11/' + run +
                '/baseline/' + target).isFile())

        ModuleTranslationProgressService tracker = new ModuleTranslationProgressService()
        Map journal = tracker.initialize(root, module, run)
        String translated = '''# Introduction

This object is encapsulated with §private§.
'''.replace('§' as char, (char) 96)
        work.setText(translated, 'UTF-8')
        ModuleTranslationFileDeliveryService delivery = new ModuleTranslationFileDeliveryService()
        GradleException early = assertThrows(GradleException) {
            delivery.execute(root, module, run, 'en', target, 'worker-01', true)
        }
        assertTrue(early.message.contains('OWNERSHIP_NOT_ASSIGNED'))
        journal = tracker.execute(root, module, run, 'ASSIGN',
                target, 'worker-01', '', journal.revision as Integer)
        assertThrows(GradleException) {
            delivery.execute(root, module, run, 'en', target, 'worker-other', true)
        }
        assertEquals('DRY_RUN_VALIDATED',
                delivery.execute(root, module, run, 'en', target,
                        'worker-01', false).status)
        assertTrue(work.isFile())
        assertTrue(new File(module, target).getText('UTF-8').contains('Giới thiệu'))
        Map published = delivery.execute(root, module, run, 'en', target,
                'worker-01', true)
        assertEquals('PUBLISHED_AND_CLEANED', published.status)
        assertFalse(work.exists())
        assertEquals(translated, new File(module, target).getText('UTF-8'))
        assertEquals('ASSIGNED', tracker.execute(root, module, run, 'SHOW')
                .jobs[0].status) // worker cannot mutate Prime's journal
        journal = tracker.execute(root, module, run, 'REPORT',
                target, 'worker-01', '', journal.revision as Integer)
        journal = tracker.execute(root, module, run, 'QA_PASS', target, '',
                'Prime reviewed translated meaning.', journal.revision as Integer)
        assertEquals(1, journal.qaPass)
        assertEquals('VALIDATED_STRUCTURE', catalog.execute(root, module, run,
                'VALIDATE', false).status)
        assertTrue(new File(module, 'content/readme/docs/vi/BASE.md')
                .getText('UTF-8').contains('Giới thiệu'))
    }

    @Test
    void refusesDriftAndProtectedContentWithNoModuleWriteAndKeepsTemp() {
        Map f = fixture('en')
        File root = f.root as File
        File module = f.module as File
        String target = 'content/readme/docs/en/BASE.md'
        String run = 'file-guard'
        new ModuleTranslationCatalogService().execute(root, module, run,
                'PREPARE', true)
        ModuleTranslationProgressService tracker = new ModuleTranslationProgressService()
        Map at = tracker.initialize(root, module, run)
        at = tracker.execute(root, module, run, 'ASSIGN',
                target, 'worker-01', '', at.revision as Integer)
        File work = ModuleTranslationCatalogService.workFile(root, 'module/demo', run, target)
        String original = new File(module, target).getText('UTF-8')
        String invalid = original.replace('private', 'public')
        // Java inline code is protected even if the prose is otherwise valid.
        work.setText(invalid, 'UTF-8')
        ModuleTranslationFileDeliveryService delivery = new ModuleTranslationFileDeliveryService()
        assertThrows(GradleException) {
            delivery.execute(root, module, run, 'en', target, 'worker-01', true)
        }
        assertEquals(original, new File(module, target).getText('UTF-8'))
        assertTrue(work.isFile())
        work.setText(original.replace('Giới thiệu', 'Introduction')
                .replace('đối tượng', 'object').replace('đóng gói', 'encapsulated'), 'UTF-8')
        new File(module, target).setText(original + '\nExternal edit', 'UTF-8')
        GradleException changed = assertThrows(GradleException) {
            delivery.execute(root, module, run, 'en', target, 'worker-01', true)
        }
        assertTrue(changed.message.contains('TARGET_CHANGED'))
        assertTrue(work.isFile())
    }

    @Test
    void targetLanguageIsConfigurableAndStagingKeepsOriginalFileBytes() {
        Map f = fixture('ja')
        File root = f.root as File
        File module = f.module as File
        String target = 'content/readme/docs/ja/BASE.md'
        String run = 'whole-file-ja'
        ModuleTranslationCatalogService catalog = new ModuleTranslationCatalogService()
        Map prepared = catalog.execute(root, module, run,
                'PREPARE', true, 'NORMAL', null, 'ja')
        assertEquals('ja', prepared.targetLanguage)
        assertEquals(target, prepared.jobs[0].id)
        File work = ModuleTranslationCatalogService.workFile(root, 'module/demo', run, target)
        assertEquals(new File(module, target).bytes.toList(), work.bytes.toList())
        Map progress = new ModuleTranslationProgressService().initialize(root, module, run)
        progress = new ModuleTranslationProgressService().execute(root, module, run,
                'ASSIGN', target, 'worker-ja', '', progress.revision as Integer)
        work.setText('''# オブジェクト指向プログラミング

このオブジェクトは §private§ でカプセル化されます。
'''.replace('§' as char, (char) 96), 'UTF-8')
        Map delivered = new ModuleTranslationFileDeliveryService().execute(
                root, module, run, 'ja', target, 'worker-ja', true)
        assertEquals('PUBLISHED_AND_CLEANED', delivered.status)
        assertTrue(new File(module, target).getText('UTF-8')
                .contains('オブジェクト指向プログラミング'))
        assertEquals('VALIDATED_STRUCTURE', catalog.execute(root, module, run,
                'VALIDATE', false, 'NORMAL', null, 'ja').status)
        assertThrows(GradleException) {
            new ModuleTranslationFileDeliveryService().execute(
                    root, module, run, 'en', target, 'worker-ja', true)
        }
    }

    @Test
    void retranslationUsesExistingEnglishAsBaselineWithoutRecloningVi() {
        Map f = fixture('en')
        File root = f.root as File
        File module = f.module as File
        String targetPath = 'content/readme/docs/en/BASE.md'
        String run = 'retranslate-existing-en'
        File target = new File(module, targetPath)
        String existingEnglish = '''# Introduction

The object uses §private§ for encapsulation.
'''.replace('§' as char, (char) 96)
        String revisedEnglish = '''# Introduction

An object encapsulates its state using §private§.
'''.replace('§' as char, (char) 96)
        target.setText(existingEnglish, 'UTF-8')
        write(module, 'master.json',
                '{"CURRENT_STEP":{"TYPE":"string","VALUE":"FINAL"}}')
        ModuleTranslationCatalogService catalog = new ModuleTranslationCatalogService()
        Map prepared = catalog.execute(root, module, run, 'PREPARE', true)
        assertEquals('PREPARED', prepared.status)
        File work = ModuleTranslationCatalogService.workFile(root, 'module/demo',
                run, targetPath)
        File baseline = new File(module, 'build/step11/' + run +
                '/baseline/' + targetPath)
        assertEquals(existingEnglish, work.getText('UTF-8'))
        assertEquals(existingEnglish, baseline.getText('UTF-8'))
        assertEquals(existingEnglish, target.getText('UTF-8'))

        ModuleTranslationProgressService journal = new ModuleTranslationProgressService()
        Map progress = journal.initialize(root, module, run)
        assertEquals(1, progress.pending)
        progress = journal.execute(root, module, run, 'ASSIGN',
                targetPath, 'worker-01', '', progress.revision as Integer)
        work.setText(revisedEnglish, 'UTF-8')
        Map delivered = new ModuleTranslationFileDeliveryService().execute(
                root, module, run, 'en', targetPath, 'worker-01', true)
        assertEquals('PUBLISHED_AND_CLEANED', delivered.status)
        assertEquals(revisedEnglish, target.getText('UTF-8'))
        assertFalse(work.exists())
        assertEquals('FINAL', new groovy.json.JsonSlurper().parse(
                new File(module, 'master.json')).CURRENT_STEP.VALUE)
        assertEquals('VALIDATED_STRUCTURE', catalog.execute(root, module, run,
                'VALIDATE', false).status)
    }

    private Map fixture(String language) {
        File root = temp.newFolder('repo')
        File module = new File(root, 'module/demo')
        assertTrue(module.mkdirs())
        write(module, 'gradle.properties', '# demo')
        write(module, 'master.json', '{"CURRENT_STEP":{"TYPE":"string","VALUE":"10"}}')
        String source = '''# Giới thiệu

Đối tượng được đóng gói bằng §private§.
'''.replace('§' as char, (char) 96)
        write(module, 'content/readme/docs/vi/BASE.md', source)
        write(module, 'content/readme/docs/' + language + '/BASE.md', source)
        write(module, 'content/readme/docs/vi/LIST.md',
                '# README MODULE STRUCTURE (VI)\n')
        write(module, 'content/readme/docs/' + language + '/LIST.md',
                '# README MODULE STRUCTURE (' + language.toUpperCase(Locale.ROOT) + ')\n')
        write(root, 'module-generate-agent/worker/STEP_11_WORKER/STEP_11_WORKER_COMMON.md',
                '# Assigned rule')
        return [root: root, module: module]
    }

    private static void write(File base, String path, String content) {
        File file = new File(base, path)
        assertTrue(file.parentFile.isDirectory() || file.parentFile.mkdirs())
        file.setText(content, 'UTF-8')
    }
}
