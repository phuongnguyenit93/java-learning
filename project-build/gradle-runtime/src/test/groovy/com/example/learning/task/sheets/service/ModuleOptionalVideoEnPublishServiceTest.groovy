package com.example.learning.task.sheets.service

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.gradle.api.GradleException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.junit.Assert.*

class ModuleOptionalVideoEnPublishServiceTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder()

    @Test
    void plansOnlyFourEnglishVideoCellsOnActualChapterParent() {
        File module = fixture()
        List<Map> plan = ModuleOptionalVideoEnPublishService.plan(module)
        assertEquals(4, plan.size())
        assertEquals([19,19,19,19], plan.collect { it.r })
        assertEquals([23,24,25,26], plan.collect { it.c })
        assertTrue(plan[0].value.contains('English overview'))
        assertEquals('', plan[1].value)
    }

    @Test
    void refusesMissingEnglishVideoChapter() {
        File module = fixture()
        File en = new File(module, 'content/video/en/menu/1.Intro/video-script.yml')
        assertTrue(en.delete())
        try {
            ModuleOptionalVideoEnPublishService.plan(module)
            fail('Expected EN chapter coverage check')
        } catch (GradleException ex) {
            assertTrue(ex.message.contains('coverage'))
        }
    }

    @Test
    void refusesOrphanVideoThatDoesNotMapToMenu() {
        File module = fixture()
        write(module, 'content/video/en/menu/2.Orphan/video-script.yml', script('Orphan'))
        try {
            ModuleOptionalVideoEnPublishService.plan(module)
            fail('Expected orphan detection')
        } catch (GradleException ex) {
            assertTrue(ex.message.contains('Orphan'))
        }
    }

    private File fixture() {
        File module = tmp.newFolder('module')
        File excel = new File(module, 'content/sheet/vi/module.xlsx')
        assertTrue(excel.parentFile.mkdirs())
        new XSSFWorkbook().withCloseable { book ->
            def sheet = book.createSheet('Module VI')
            sheet.createRow(18).with { row ->
                row.createCell(0).setCellValue('Menu Cấp 1')
                row.createCell(1).setCellValue('Menu Cấp 2')
            }
            sheet.createRow(19).with { row ->
                row.createCell(0).setCellValue('Giới thiệu')
                row.createCell(1).setCellValue('Intro.md')
                row.createCell(23).setCellValue(script('Giới thiệu', 'Tổng quan'))
            }
            sheet.createRow(20).createCell(1).setCellValue('Tổng quan')
            excel.withOutputStream { book.write(it) }
        }
        write(module, 'content/video/vi/menu/1.Intro/video-script.yml',
                script('Giới thiệu', 'Tổng quan'))
        write(module, 'content/video/en/menu/1.Intro/video-script.yml',
                script('English overview'))
        return module
    }

    private static String script(String title, String section = 'Overview') {
        return """title: "${title}"
video:
  url: ""
sections:
  - title: "${section}"
    items:
      - type: SCENE
        time: "00:00-00:10"
        visual: "Scene illustration"
        script: "Spoken explanation"
        purpose: "Learning goal"
"""
    }

    private static void write(File module, String path, String content) {
        File file = new File(module, path)
        assertTrue(file.parentFile.isDirectory() || file.parentFile.mkdirs())
        file.setText(content, 'UTF-8')
    }
}
