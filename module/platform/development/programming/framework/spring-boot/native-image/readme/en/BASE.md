# Spring Boot Native Image

This module explains the Spring Boot application-level path from a normal dynamic JVM runtime model to an AOT-prepared native executable. The focus is not GraalVM compiler internals; it is the Boot developer mental model for AOT processing, closed-world consequences, runtime hints, supported native build paths, native-specific testing, ecosystem compatibility, and evidence-based deployment decisions.

## What You Will Learn

You will learn why Spring AOT is required before native compilation, what generated assets the AOT pipeline contributes, how closed-world constraints change assumptions that are normally deferred to runtime, how `RuntimeHints` describe dynamic access that static analysis cannot infer, how Boot integrates Maven/Gradle native build tooling and Buildpacks, how to test native-sensitive behavior deliberately, and how to decide whether native deployment is worth the build and compatibility cost.

## Prerequisites

You should already understand the normal Spring Boot application runtime, auto-configuration, build-tooling/packaging, and Boot testing model. Basic knowledge of reflection, proxies, resources, and JVM deployment is useful. This module does not teach GraalVM compiler/runtime internals, generic Maven/Gradle mechanics, Docker/Buildpacks internals, or the full Java reflection/proxy model.

## Learning Flow

1. Start with the problem native compilation creates for a dynamic Boot application and the benefits that motivate AOT work.
2. Follow Spring AOT from the application model to generated source, bytecode, and hint metadata.
3. Understand the closed-world consequences for classpath, bean graph, profiles, properties, and other dynamic behavior.
4. Learn how runtime hints and third-party reachability metadata describe dynamic access that native-image tooling cannot infer safely.
5. Build and run native executables or native OCI images through Boot-supported build paths.
6. Test AOT/native-sensitive behavior selectively instead of moving the entire feedback loop away from the JVM.
7. Evaluate runtime gains against build cost, metadata quality, dependency support, and compatibility risk.
8. Synthesize readiness evidence and choose among a native executable, native container image, and normal JVM deployment.

## Module Boundary

This module owns Spring Boot's AOT/native application model, runtime hints, native-readiness reasoning, supported native build handoffs, and native-specific validation strategy. General Boot plugin, executable packaging, Buildpacks, and image-delivery mechanics belong to `build-tooling-packaging`; the normal runtime lifecycle belongs to `application-runtime`; broader Boot testing belongs to `testing`; conditional auto-configuration semantics belong to `auto-configuration`; generic Java reflection/proxy/class-loading mechanics and GraalVM compiler/runtime internals remain with their dedicated owners.
