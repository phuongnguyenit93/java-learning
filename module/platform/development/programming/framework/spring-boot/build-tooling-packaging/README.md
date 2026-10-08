# 📂 README MODULE STRUCTURE (EN)

* **1.Purpose**
    * [Purpose](readme/en/menu/1.Purpose/Purpose.md)
* **2.BuildPlugins**
    * [BuildPlugins](readme/en/menu/2.BuildPlugins/BuildPlugins.md)
* **3.DependencyManagement**
    * [DependencyManagement](readme/en/menu/3.DependencyManagement/DependencyManagement.md)
* **4.BootRun**
    * [BootRun](readme/en/menu/4.BootRun/BootRun.md)
* **5.BootJarBootWar**
    * [BootJarBootWar](readme/en/menu/5.BootJarBootWar/BootJarBootWar.md)
* **6.ExecutableArchives**
    * [ExecutableArchives](readme/en/menu/6.ExecutableArchives/ExecutableArchives.md)
* **7.LayeredArchives**
    * [LayeredArchives](readme/en/menu/7.LayeredArchives/LayeredArchives.md)
* **8.ReproducibleBuilds**
    * [ReproducibleBuilds](readme/en/menu/8.ReproducibleBuilds/ReproducibleBuilds.md)
* **9.Buildpacks**
    * [Buildpacks](readme/en/menu/9.Buildpacks/Buildpacks.md)
* **10.OciImages**
    * [OciImages](readme/en/menu/10.OciImages/OciImages.md)
* **11.DockerBuildStrategy**
    * [DockerBuildStrategy](readme/en/menu/11.DockerBuildStrategy/DockerBuildStrategy.md)
* **12.DeploymentHandoff**
    * [DeploymentHandoff](readme/en/menu/12.DeploymentHandoff/DeploymentHandoff.md)

# Spring Boot Build Tooling and Packaging

This module explains how Spring Boot connects an ordinary Gradle or Maven build to a runnable development process, an executable application artifact, and optionally an OCI image. The focus is Boot-specific integration: plugin responsibilities, dependency alignment, executable archive structure, layering, reproducibility, Buildpacks, and the point where the produced artifact is handed to deployment infrastructure.

## What You Will Learn

You will learn how the Spring Boot Gradle and Maven plugins participate in the build, how `spring-boot-dependencies` keeps common dependencies aligned, how development-time run tasks differ from executing a packaged artifact, how `bootJar`/`bootWar` and Maven `repackage` create executable archives, why Spring Boot Loader and nested archives exist, how layered/reproducible packaging supports downstream delivery, and how Boot integrates Cloud Native Buildpacks through image-building tasks.

## Prerequisites

You should already understand the basic Spring Boot application model, starters and the classpath, and ordinary dependency/build concepts in Gradle or Maven. This module does not teach general Gradle/Maven syntax, Docker internals, registry operation, CI/CD pipelines, or deployment orchestration.

## Learning Flow

1. Establish why Boot needs build and packaging integration and where that ownership ends.
2. Understand the Gradle/Maven plugin responsibilities and Boot-managed dependency baseline.
3. Run the application through Boot-aware development tasks and compare that path with `java -jar`.
4. Produce executable JAR/WAR artifacts and understand the nested-archive and Boot Loader model.
5. Study layered and reproducible packaging as properties of the delivery artifact.
6. Learn the Cloud Native Buildpacks model that Boot's image-building integration relies on.
7. Build OCI images through Boot's Buildpacks integration and compare that path with Dockerfile-based packaging without taking ownership of generic container mechanics.
8. Choose an artifact/image handoff strategy and classify where build/package/image/deployment failures belong.

## Module Boundary

This module owns Spring Boot's build plugins, dependency-alignment integration, executable packaging, Spring Boot Loader orientation, archive layering/reproducibility, and Boot-managed Buildpacks/OCI-image invocation. Application lifecycle after process launch belongs to `application-runtime`; generic Gradle/Maven knowledge belongs to build-tool curricula; Dockerfile/container/registry/runtime mechanics and deployment orchestration belong to infrastructure owners; AOT, GraalVM, runtime hints, closed-world behavior, and native-image semantics belong to `native-image`.
