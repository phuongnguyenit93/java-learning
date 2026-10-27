package com.example.learning.setup.module.config.service

import com.example.learning.setup.module.config.model.ModuleType
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.bundling.Zip

/**
 * Creates an importable, standalone Gradle project for a single runnable module.
 * Module source is the only Java source in the bundle. Shared runtime modules
 * and external dependencies are distributed as compiled jars in libs/.
 */
class ModuleSourceBundleService {
    static void register(Project project, ModuleType type) {
        if (!(type in [ModuleType.SERVLET, ModuleType.REACTIVE])) return

        String relativePath = project.rootProject.projectDir.toPath()
                .relativize(project.projectDir.toPath()).toString().replace('\\', '/')
        String serviceName = project.findProperty('SERVICE_NAME')?.toString()?.trim()
        if (!serviceName || !(serviceName ==~ /[A-Za-z0-9_]+/)) {
            throw new GradleException('Invalid SERVICE_NAME for source bundle: ' + serviceName)
        }
        String slug = serviceName.toLowerCase(Locale.ROOT)
        String folder = slug + '-source'
        File stage = new File(project.rootProject.buildDir, 'source-staging/' + relativePath + '/' + folder)

        def runtime = project.configurations.getByName('runtimeClasspath')
        def compile = project.configurations.getByName('compileClasspath')

        def prepare = project.tasks.register('prepareModuleSourceBundle') {
            group = 'Local Run'
            description = 'Assemble standalone source project for this runnable module.'
            dependsOn(runtime, compile)
            doLast {
                // This generated folder is exclusively owned by this task.
                project.delete(stage)
                stage.mkdirs()
                project.copy {
                    from(new File(project.projectDir, 'src'))
                    into(new File(stage, 'src'))
                    exclude('**/.env', '**/.env.*', '**/*.pem', '**/*.key', '**/id_rsa*')
                }

                File rootDir = project.rootProject.projectDir
                project.copy {
                    from(new File(rootDir, 'gradlew'))
                    from(new File(rootDir, 'gradlew.bat'))
                    into(stage)
                }
                project.copy {
                    from(new File(rootDir, 'gradle/wrapper'))
                    into(new File(stage, 'gradle/wrapper'))
                }

                // The resolved jar files represent the effective runtime
                // configuration, including the project-owned Swagger/Execution
                // Context adapters. Include compileOnly dependencies as well.
                List<File> jars = (runtime.files + compile.files)
                        .findAll { File f -> f.isFile() && f.name.endsWith('.jar') }
                        .toSet().toList().sort { it.name }
                Map<String, File> filenames = [:]
                jars.each { File jar ->
                    if (filenames.containsKey(jar.name) &&
                            filenames[jar.name].canonicalPath != jar.canonicalPath) {
                        throw new GradleException('Source bundle jar name collision: ' + jar.name)
                    }
                    filenames[jar.name] = jar
                }
                project.copy {
                    from(jars)
                    into(new File(stage, 'libs'))
                    duplicatesStrategy = DuplicatesStrategy.FAIL
                }

                new File(stage, 'settings.gradle').setText(
                        "rootProject.name = '${slug}'\n", 'UTF-8')
                new File(stage, 'build.gradle').setText(standaloneGradle(type), 'UTF-8')
                new File(stage, 'README.md').setText(
                        """# ${serviceName} — standalone source

This archive includes only the selected module's Java source and resources.
Its `libs/` directory contains the resolved binary dependencies needed to
build the project outside the original java-learning repository.

Requires **JDK 21**. Open this folder in IntelliJ IDEA as a Gradle project, or:

```shell
./gradlew bootRun
./gradlew bootJar
```

On Windows, use `gradlew.bat` instead of `./gradlew`.
The Spring Boot app runs on the module's configured port (or use
`--args='--server.port=9000'`). Dependencies in `libs/` correspond to
the revision used to package this archive.
""", 'UTF-8')
                project.logger.lifecycle('[SOURCE-BUNDLE] Staged {} ({} dependency jars)', serviceName, jars.size())
            }
        }

        project.tasks.register('generateModuleSourceBundle', Zip) { Zip zip ->
            group = 'Local Run'
            description = 'Package only this module as a standalone Gradle source ZIP.'
            dependsOn(prepare)
            archiveFileName.set(slug + '_source.zip')
            destinationDirectory.set(project.rootProject.layout.buildDirectory.dir('source/' + relativePath))
            from(stage) { into(folder) }
            // ZIP metadata generated on Windows otherwise marks gradlew 0644.
            // 0755 permits Linux/macOS users to invoke ./gradlew immediately.
            fileMode = 0755
            dirMode = 0755
            duplicatesStrategy = DuplicatesStrategy.FAIL
        }
    }

    private static String standaloneGradle(ModuleType type) {
        String starter = type == ModuleType.REACTIVE ? 'webflux' : 'web'
        return """plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.5'
    id 'io.spring.dependency-management' version '1.1.6'
    id 'io.freefair.lombok' version '8.6'
}

group = 'com.example.learning'
version = '1.0.0'

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

repositories {
    mavenCentral()
}

dependencies {
    // Binary adapters and all resolved dependencies from the original
    // module build are pinned inside this standalone archive.
    implementation fileTree(dir: 'libs', include: ['*.jar'])
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.withType(Test).configureEach {
    useJUnitPlatform()
}
"""
    }
}
