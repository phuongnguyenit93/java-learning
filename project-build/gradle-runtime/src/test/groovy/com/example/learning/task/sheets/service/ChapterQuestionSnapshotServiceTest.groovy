package com.example.learning.task.sheets.service

import org.gradle.api.GradleException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.junit.Assert.*

class ChapterQuestionSnapshotServiceTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder()

    @Test
    void preflightFullModuleThenReplaceOnlyOwnedQuestions() {
        File root = temp.newFolder('repo')
        File module = new File(root, 'module/oop')
        File first = new File(module, 'content/readme/docs/vi/menu/1.Basic')
        File second = new File(module, 'content/readme/docs/vi/menu/2.Class')
        [first, second].each { File chapter ->
            assertTrue(new File(chapter, 'knowledge').mkdirs())
        }
        File meta = new File(first, 'metadata/knowledge-metadata.yml')
        assertTrue(meta.parentFile.mkdirs())
        meta.setText('sections: {}', 'UTF-8')
        File quiz = new File(first, 'quiz/question.yml')
        assertTrue(quiz.parentFile.mkdirs())
        quiz.setText('OLD', 'UTF-8')
        File obsolete = new File(first, 'quiz/archive/old-quiz.yml')
        assertTrue(obsolete.parentFile.mkdirs())
        obsolete.setText('OBSOLETE', 'UTF-8')
        File stale = new File(second, 'quiz/question.yml')
        assertTrue(stale.parentFile.mkdirs())
        stale.setText('STALE', 'UTF-8')
        File interview = new File(first, 'interview/question.yml')
        assertTrue(interview.parentFile.mkdirs())
        interview.setText('INTERVIEW', 'UTF-8')
        File old = new File(module, 'content/quiz/vi/question.yml')
        assertTrue(old.parentFile.mkdirs())
        old.setText('LEGACY QUIZ', 'UTF-8')

        Map<String, String> next = ['1.Basic': 'NEW']
        Map dry = ChapterQuestionSnapshotService.replace(root, module, 'quiz', next, false)
        assertEquals('DRY_RUN', dry.status)
        assertEquals('OLD', quiz.getText('UTF-8'))
        assertTrue(stale.isFile())
        assertTrue(old.isFile())
        Map applied = ChapterQuestionSnapshotService.replace(root, module, 'quiz', next, true)
        assertEquals('UPDATED', applied.status)
        assertEquals('NEW', quiz.getText('UTF-8'))
        assertFalse(obsolete.exists())
        assertFalse(stale.exists())
        assertFalse(old.exists())
        assertEquals('INTERVIEW', interview.getText('UTF-8'))
        assertEquals('sections: {}', meta.getText('UTF-8'))
        assertEquals('UNCHANGED',
                ChapterQuestionSnapshotService.replace(root, module, 'quiz', next, false).status)
        assertEquals('CLEARED_EMPTY',
                ChapterQuestionSnapshotService.replace(root, module, 'quiz', [:], true).status)
        assertFalse(quiz.parentFile.exists())
        assertEquals('INTERVIEW', interview.getText('UTF-8'))
    }

    @Test
    void invalidChapterPreventsAnyDeletion() {
        File root = temp.newFolder('invalid')
        File module = new File(root, 'module/demo')
        File chapter = new File(module, 'content/readme/docs/vi/menu/1.Basic')
        assertTrue(new File(chapter, 'knowledge').mkdirs())
        File existing = new File(chapter, 'interview/question.yml')
        assertTrue(existing.parentFile.mkdirs())
        existing.setText('PRESERVE', 'UTF-8')
        try {
            ChapterQuestionSnapshotService.replace(root, module, 'interview',
                    ['2.Missing': 'NEW'], true)
            fail('Expected missing Knowledge blocker')
        } catch (GradleException expected) {
            assertTrue(expected.message.contains('Missing Knowledge chapter'))
        }
        assertEquals('PRESERVE', existing.getText('UTF-8'))
    }
}
