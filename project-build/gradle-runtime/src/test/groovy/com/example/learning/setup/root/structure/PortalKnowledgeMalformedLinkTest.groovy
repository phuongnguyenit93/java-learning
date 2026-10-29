package com.example.learning.setup.root.structure

import com.example.learning.setup.root.structure.service.PortalKnowledgeProjectionService
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.junit.Assert.assertNotNull

class PortalKnowledgeMalformedLinkTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder()

    @Test
    void preservesMalformedRelativeReferencesInsteadOfAbortingPortalExport() {
        File markdown = temporary.newFile('Knowledge.md')
        markdown.setText('# Knowledge\n', 'UTF-8')
        def service = new PortalKnowledgeProjectionService(ProjectBuilder.builder().build().logger)
        def validate = PortalKnowledgeProjectionService.getDeclaredMethod(
                'validateRawLink', File, String)
        validate.accessible = true
        assertNotNull(validate)
        // A quote in a path is invalid on Windows but may be legal on POSIX.
        // Either way this advisory validation must not throw.
        validate.invoke(service, markdown, 'B".')
        validate.invoke(service, markdown, 'missing-article.md')
    }
}
