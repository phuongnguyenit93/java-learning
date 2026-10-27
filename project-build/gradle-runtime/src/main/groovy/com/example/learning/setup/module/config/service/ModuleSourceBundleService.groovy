package com.example.learning.setup.module.config.service

import com.example.learning.setup.module.config.model.ModuleType
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.tasks.bundling.Zip

/**
 * Creates an importable, standalone Gradle project for a single runnable module.
 * Module source is the only Java source in the bundle. External dependencies
 * use Maven coordinates obtained from the orchestration-resolved classpaths;
 * only project(...) dependencies ship as binary jars in libs/.
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
        def testRuntime = project.configurations.getByName('testRuntimeClasspath')
        def testCompile = project.configurations.getByName('testCompileClasspath')

        def prepare = project.tasks.register('prepareModuleSourceBundle') {
            group = 'Local Run'
            description = 'Assemble standalone source project for this runnable module.'
            dependsOn(runtime, compile, testRuntime, testCompile)
            // Gradle may expose another project's classes directory in
            // compileClasspath, rather than its JAR. Always build the
            // project-owned JAR explicitly for the standalone archive.
            dependsOn {
                [runtime, compile, testRuntime, testCompile]
                        .collectMany { Configuration config -> resolvedArtifacts(config).projects }
                        .toSet().collect { String path ->
                            project.rootProject.project(path).tasks.named('jar')
                        }
            }
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

                // Read dependency edges after orchestration has configured the
                // module. Traverse only the root and internal project nodes:
                // Maven child dependencies belong to Gradle transitive
                // resolution, not to this standalone build.gradle.
                Map runtimeItems = resolvedArtifacts(runtime)
                Map compileItems = resolvedArtifacts(compile)
                Map testRuntimeItems = resolvedArtifacts(testRuntime)
                Map testCompileItems = resolvedArtifacts(testCompile)
                Map<String, String> runtimeModules = runtimeItems.modules
                Map<String, String> compileOnlyModules =
                        compileItems.modules.findAll { String id, String version ->
                            !runtimeModules.containsKey(id)
                        }
                Map<String, String> testModules =
                        (testRuntimeItems.modules + testCompileItems.modules)
                                .findAll { String id, String version ->
                                    !runtimeModules.containsKey(id) &&
                                            !compileOnlyModules.containsKey(id)
                                }

                // Only jars produced by Gradle project dependencies are
                // bundled: e.g. Swagger and Execution Context owned by this
                // repository. Everything hosted in Maven repositories stays
                // declared by its resolved group/artifact/version.
                List<File> jars = (
                        runtimeItems.projects + compileItems.projects +
                        testRuntimeItems.projects + testCompileItems.projects
                ).toSet().collect { String path ->
                    def jarTask = project.rootProject.project(path).tasks.named('jar').get()
                    File jar = jarTask.archiveFile.get().asFile
                    if (!jar.isFile()) {
                        throw new GradleException('Missing project dependency jar: ' + path +
                                ' -> ' + jar)
                    }
                    jar
                }.sort { it.name }
                Map<String, File> filenames = [:]
                jars.each { File jar ->
                    if (filenames.containsKey(jar.name) &&
                            filenames[jar.name].canonicalPath != jar.canonicalPath) {
                        throw new GradleException('Source bundle jar name collision: ' + jar.name)
                    }
                    filenames[jar.name] = jar
                }
                if (!jars.isEmpty()) {
                    project.copy {
                        from(jars)
                        into(new File(stage, 'libs'))
                        duplicatesStrategy = DuplicatesStrategy.FAIL
                    }
                }

                new File(stage, 'settings.gradle').setText(
                        "rootProject.name = '${slug}'\n", 'UTF-8')
                new File(stage, 'build.gradle').setText(
                        standaloneGradle(runtimeModules, compileOnlyModules, testModules, !jars.isEmpty()), 'UTF-8')
                new File(stage, 'README.md').setText(
                        """# ${serviceName} — standalone source

This archive includes only the selected module's Java source and resources.
Public dependencies are declared using their resolved Maven coordinates in
`build.gradle` and downloaded from Maven Central. The `libs/` directory
contains only compiled internal `java-learning` project dependencies
(such as Swagger and Execution Context adapters).

Requires **JDK 21** and an internet connection for first-time dependency
resolution. Open this folder in IntelliJ IDEA as a Gradle project, or:

```shell
./gradlew bootRun
./gradlew bootJar
```

On Windows, use `gradlew.bat` instead of `./gradlew`.
The Spring Boot app runs on the module's configured port (or use
`--args='--server.port=9000'`). Dependencies are derived from the
orchestration-resolved module classpaths at export time, not from
independently maintained per-module Gradle declarations.
""", 'UTF-8')
                project.logger.lifecycle(
                        '[SOURCE-BUNDLE] Staged {} ({} direct Maven runtime, {} compile-only, {} test dependencies, {} internal jars)',
                        serviceName, runtimeModules.size(), compileOnlyModules.size(), testModules.size(), jars.size())
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

    private static Map resolvedArtifacts(Configuration configuration) {
        Map<String, String> modules = [:]
        Set<String> projects = [] as Set
        Set<Object> visitedProjects = [] as Set
        List<ResolvedComponentResult> stack = [configuration.incoming.resolutionResult.root]
        while (!stack.isEmpty()) {
            ResolvedComponentResult component = stack.remove(stack.size() - 1)
            if (!visitedProjects.add(component.id)) continue
            component.dependencies.each { dependency ->
                if (!(dependency instanceof ResolvedDependencyResult)) {
                    throw new GradleException('Unresolved source bundle dependency in ' +
                            configuration.name + ': ' + dependency)
                }
                ResolvedComponentResult selected = dependency.selected
                def identity = selected.id
                if (identity instanceof ModuleComponentIdentifier) {
                    String key = identity.group + ':' + identity.module
                    String existing = modules.put(key, identity.version)
                    if (existing != null && existing != identity.version) {
                        throw new GradleException('Multiple resolved versions for ' + key +
                                ' in ' + configuration.name)
                    }
                    // Its transitive Maven dependencies are resolved online.
                } else if (identity instanceof ProjectComponentIdentifier) {
                    projects.add(identity.projectPath)
                    // Internal projects are distributed as JARs, without
                    // published POM metadata. Their direct public dependencies
                    // must therefore be represented in our build.gradle.
                    stack.add(selected)
                } else {
                    throw new GradleException('Unsupported source bundle dependency in ' +
                            configuration.name + ': ' + identity)
                }
            }
        }
        return [modules: modules, projects: projects]
    }

    private static String gradleLines(String scope, Map<String, String> modules) {
        return modules.sort { a, b -> a.key <=> b.key }
                .collect { String name, String version ->
                    "    ${scope} '${name}:${version}'"
                }.join('\n')
    }

    private static String standaloneGradle(Map<String, String> runtimeModules,
                                          Map<String, String> compileOnlyModules,
                                          Map<String, String> testModules,
                                          boolean hasProjectJars) {
        String onlineRuntime = gradleLines('implementation', runtimeModules)
        String onlineCompile = gradleLines('compileOnly', compileOnlyModules)
        String onlineTests = gradleLines('testImplementation', testModules)
        String internal = hasProjectJars
                ? "    // Only binaries originating from java-learning project(...) dependencies.\n" +
                        "    implementation fileTree(dir: 'libs', include: ['*.jar'])\n"
                : ''
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
    // Effective external dependencies, resolved by the original orchestration.
${onlineRuntime}
${onlineCompile}
${onlineTests}
${internal}
}

tasks.withType(Test).configureEach {
    useJUnitPlatform()
}
"""
    }
}
