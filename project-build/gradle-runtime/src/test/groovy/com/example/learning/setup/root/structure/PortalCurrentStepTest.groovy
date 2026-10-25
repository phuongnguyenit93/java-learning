package com.example.learning.setup.root.structure

import com.example.learning.setup.root.structure.service.ProjectStructureService
import org.gradle.api.GradleException
import org.junit.Test

import static org.junit.Assert.*

class PortalCurrentStepTest {
    @Test
    void parsesAllSupportedProgressStatesWithoutChangingLegacyDefaults() {
        def method = ProjectStructureService.getDeclaredMethod('nodeCurrentStep', Map, File)
        method.accessible = true
        File directory = new File('demo')
        assertEquals('0', method.invoke(null, [:], directory))
        ['0', '5', '12A', '15', 'FINAL'].each { String stage ->
            assertEquals(stage, method.invoke(null,
                    [CURRENT_STEP: [TYPE:'string', VALUE: stage]], directory))
        }
        ['', '-1', '05', '12a', 'done', 12, null].each { Object invalid ->
            try {
                method.invoke(null, [CURRENT_STEP: [VALUE: invalid]], directory)
                fail('Accepted invalid stage: ' + invalid)
            } catch (java.lang.reflect.InvocationTargetException ex) {
                assertTrue(ex.cause instanceof GradleException)
            }
        }
    }
}
