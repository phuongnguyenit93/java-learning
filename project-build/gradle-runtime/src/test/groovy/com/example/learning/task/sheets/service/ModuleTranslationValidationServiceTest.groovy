package com.example.learning.task.sheets.service

import org.gradle.api.GradleException
import org.junit.Test

import static org.junit.Assert.assertTrue
import static org.junit.Assert.fail

class ModuleTranslationValidationServiceTest {
    @Test
    void javaComparisonOperatorsDoNotConsumeTranslatedProseAsHtml() {
        String source = '''<details>
```java
if (count <= 0) {
    throw new IllegalArgumentException("bad count");
}
```
Giải thích bằng tiếng Việt.
</details>
'''
        String target = source.replace('Giải thích bằng tiếng Việt.',
                'Explanation in English.')

        ModuleTranslationValidationService.compare('README',
                source.getBytes('UTF-8'), target.getBytes('UTF-8'))
    }

    @Test
    void actualHtmlAttributeChangesRemainProtected() {
        String source = '<details data-kind="reference">Nội dung</details>'
        String target = '<details data-kind="other">Content</details>'

        try {
            ModuleTranslationValidationService.compare('README',
                    source.getBytes('UTF-8'), target.getBytes('UTF-8'))
            fail('An HTML attribute change must not pass translation validation')
        } catch (GradleException expected) {
            assertTrue(expected.message.contains('Markdown HTML tags/attributes'))
        }
    }
}
