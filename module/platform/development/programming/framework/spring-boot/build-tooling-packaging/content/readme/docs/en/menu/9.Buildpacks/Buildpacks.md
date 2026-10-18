<a id="back-to-top"></a>

# Cloud Native Buildpacks

## Menu
- [What Are Builders, Buildpacks, and Run Images?](#buildpack-model)
- [What Part of the Buildpack Lifecycle Does a Boot User Need to Understand?](#buildpack-lifecycle-boundary)
- [How Do Buildpack Layers and Caches Support Repeated Builds?](#buildpack-layers-and-caches)
- [Which Buildpack Inputs Can Spring Boot Customize?](#buildpack-customization)
- [Where Does Buildpack Integration End and Native-Image Semantics Begin?](#buildpack-native-boundary)

## <a id="buildpack-model">What Are Builders, Buildpacks, and Run Images?</a>

<details>
<summary>Click for details</summary>

Cloud Native Buildpacks turn application input into an OCI image without requiring the application team to author the complete Dockerfile. Three roles are useful to separate:

```text
builder image
→ build environment + lifecycle + available buildpacks

buildpack
→ detects application needs and contributes runtime/build layers

run image
→ base runtime image used by the produced application image
```

Spring Boot's build plugins integrate this model into Gradle/Maven. Boot chooses sensible defaults and passes the application plus configuration to the buildpack lifecycle; the buildpack ecosystem still owns the generic buildpack specification and implementation.

### References

- [Spring Boot 3.3 — Packaging OCI Images](https://docs.spring.io/spring-boot/3.3/gradle-plugin/packaging-oci-image.html)
- [Cloud Native Buildpacks Documentation](https://buildpacks.io/docs/)

</details>

- [Back to top](#back-to-top)

---

## <a id="buildpack-lifecycle-boundary">What Part of the Buildpack Lifecycle Does a Boot User Need to Understand?</a>

<details>
<summary>Click for details</summary>

A Boot developer does not need to implement the Cloud Native Buildpacks lifecycle, but should understand the decision flow well enough to diagnose image builds:

```text
application input
→ analyze previous-image / registry state
→ detect applicable buildpacks
→ restore reusable layers/cache state
→ build application/runtime layers
→ export OCI image
```

If detection cannot recognize the application, investigate builder/buildpack selection and application input. If compilation or dependency work fails during the build stage, inspect the relevant buildpack output. If image export/publish fails, the problem may instead be daemon, registry, or credentials.

That level of reasoning is enough for Boot integration. Lifecycle internals belong to the Buildpacks technology itself.

The Boot task is an orchestrator for this lifecycle, not an implementation of each phase. Its logs are therefore the first evidence source: they show the builder image, participating buildpacks, detected application type, contributed layers, and export result. When detection fails, changing application runtime configuration is unlikely to help because the application has not been launched.

Keep lifecycle reasoning coarse but ordered. Analyze validates/reads prior-image and registry state; detect decides applicability; restore recovers reusable layers; build contributes application/runtime content; export assembles the OCI result. The exact CNB lifecycle implementation remains outside this module.

</details>

- [Back to top](#back-to-top)

---

## <a id="buildpack-layers-and-caches">How Do Buildpack Layers and Caches Support Repeated Builds?</a>

<details>
<summary>Click for details</summary>

Buildpacks separate runtime components and application content into reusable layers. They also use build caches so expensive inputs that have not changed can be reused on later image builds.

For a Java application, a change to application classes should not necessarily force every runtime component or dependency layer to be reconstructed. Reuse depends on the builder/buildpack implementation and cache configuration, but the core idea matches layered packaging: isolate content by change behavior so stable layers survive frequent source edits.

Do not assume “cache enabled” guarantees a hit. Builder changes, dependency changes, buildpack version changes, environment changes, or explicit cache cleanup can invalidate reusable state.

</details>

- [Back to top](#back-to-top)

---

## <a id="buildpack-customization">Which Buildpack Inputs Can Spring Boot Customize?</a>

<details>
<summary>Click for details</summary>

Boot's Gradle and Maven integrations expose the inputs application teams most often need: image name, builder and run image selection, environment variables understood by buildpacks, additional buildpacks, bindings, cache configuration, network-related build settings, and publication/registry credentials.

The important discipline is to prefer the highest-level supported input that expresses the requirement. For example, when a buildpack documents an environment variable for choosing a JVM setting, pass that buildpack input rather than replacing the entire builder just to change one option.

Customization should remain reproducible in build configuration. Avoid relying on an engineer's local daemon state when the same image must later be built in CI.

Boot 3.3 exposes customization at several levels: choose a builder or run image, provide buildpack environment variables, replace or reorder buildpacks, attach bindings, select the builder network, configure clean-cache behavior, and control build/launch caches. These are integration inputs passed to the builder rather than arbitrary Dockerfile commands.

Prefer immutable builder references or organization-managed upgrade rules in repeatable pipelines. A floating builder tag can change the JDK or buildpack behavior without source changes, which makes failures difficult to reproduce even when the application repository is unchanged.

</details>

- [Back to top](#back-to-top)

---

## <a id="buildpack-native-boundary">Where Does Buildpack Integration End and Native-Image Semantics Begin?</a>

<details>
<summary>Click for details</summary>

Buildpacks can produce both ordinary JVM application images and, when the appropriate native build path is selected, images containing native executables. This module owns **how Boot invokes the image build**. It does not own why AOT processing is required, closed-world constraints, runtime hints, GraalVM compatibility, or native testing.

```text
bootBuildImage / build-image
→ build-tooling-packaging

BP_NATIVE_IMAGE / native-oriented build path
→ invocation may pass through Buildpacks

AOT + hints + closed world + native compatibility
→ native-image module
```

Keeping this boundary prevents a packaging chapter from teaching the same native-image curriculum twice.

When the GraalVM Native Image Gradle plugin is applied, Boot adjusts its image-build integration so the default builder path and environment can request a native image. That automatic bridge is useful evidence that build tooling participates in the native workflow, but it does not transfer semantic ownership.

If the failure concerns whether a reflection hint is missing, why dynamic behavior is incompatible with closed-world analysis, or how AOT-generated code changes runtime behavior, stop debugging Buildpacks and move to the native-image curriculum. The packaging command is only the entry point.

</details>

- [Back to top](#back-to-top)
