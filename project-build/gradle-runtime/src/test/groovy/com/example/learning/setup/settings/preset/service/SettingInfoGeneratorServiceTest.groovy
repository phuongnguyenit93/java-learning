package com.example.learning.setup.settings.preset.service

import com.example.learning.setup.settings.preset.model.ModuleSetupInfo
import org.gradle.api.GradleException
import org.gradle.api.logging.Logging
import org.junit.Test

import java.lang.reflect.InvocationTargetException

import static org.junit.Assert.*

class SettingInfoGeneratorServiceTest {

    private static SettingInfoGeneratorService service() {
        new SettingInfoGeneratorService(Logging.getLogger(SettingInfoGeneratorServiceTest))
    }

    private static String resolve(SettingInfoGeneratorService target, String value,
                                  String directory = 'spring-boot') {
        def module = new ModuleSetupInfo(
                directory: new File('module/platform/' + directory),
                modulePath: 'module:platform:' + directory,
                relativePath: 'module/platform/' + directory
        )
        def method = SettingInfoGeneratorService.getDeclaredMethod(
                'resolveModuleName', ModuleSetupInfo, Map)
        method.accessible = true
        return method.invoke(target, module,
                [SERVICE_NAME: [TYPE: 'string', VALUE: value]]) as String
    }

    @Test
    void acceptsOnlyUpperSnakeCaseServiceName() {
        def target = service()
        ['OBJECT_ORIENTED', 'OBJECT_ORIENTED_PROGRAMMING', 'SPRING_BOOT',
         'JAVA21', 'JAVA_21', 'A'].each { String name ->
            assertEquals(name, resolve(target, name))
        }

        ['object_oriented', 'ObjectOriented', 'Object Oriented',
         'OBJECT-ORIENTED', '_OBJECT', 'OBJECT_', 'OBJECT__ORIENTED',
         '9OBJECT', 'ĐỐI_TƯỢNG'].each { String name ->
            try {
                resolve(target, name)
                fail('Should reject non-UPPER_SNAKE_CASE: ' + name)
            } catch (InvocationTargetException error) {
                assertTrue(error.cause instanceof GradleException)
                assertTrue(error.cause.message.contains('UPPER_SNAKE_CASE'))
            }
        }
    }

    @Test
    void blankServiceNameGetsSafeUppercaseFallbackWithoutChangingStoredValue() {
        def target = service()
        assertEquals('SPRING_BOOT', resolve(target, '', 'spring-boot'))
        assertEquals('SPRING_SECURITY', resolve(target, null, 'spring-security'))
    }

    @Test
    void duplicateExplicitServiceNameFailsRatherThanOverwritingCatalog() {
        def target = service()
        def catalogField = SettingInfoGeneratorService.getDeclaredField('moduleCatalog')
        catalogField.accessible = true
        Map catalog = catalogField.get(target) as Map
        catalog['SPRING_BOOT'] = [MODULE_PATH: 'module:already:registered']

        try {
            resolve(target, 'SPRING_BOOT')
            fail('Duplicate module identifiers must be rejected')
        } catch (InvocationTargetException error) {
            assertTrue(error.cause instanceof GradleException)
            assertTrue(error.cause.message.contains('Duplicate SERVICE_NAME'))
        }
        assertEquals('module:already:registered', catalog['SPRING_BOOT'].MODULE_PATH)
        assertEquals('PLATFORM_SPRING_BOOT', resolve(target, '', 'spring-boot'))
    }

    @Test
    void otherEnumNamesKeepTheirExistingIdentifierRules() {
        def validator = SettingInfoGeneratorService.getDeclaredMethod(
                'validateEnumConstant', String, String)
        validator.accessible = true
        validator.invoke(null, 'camelCase', 'DATABASE_MODULE_NAME')
    }
}
