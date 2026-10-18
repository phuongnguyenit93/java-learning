<a id="back-to-top"></a>

# End-to-End Native Image Readiness

## Menu
- [How Does the Full Boot Native-image Journey Fit Together?](#native-readiness-flow)
- [What Should Be Checked before Committing to Native Deployment?](#native-readiness-checklist)
- [How Do You Locate a Failure across AOT, Closed-world, Hints, Build, and Dependencies?](#native-failure-location)
- [How Do You Choose a Native Executable, Native Container Image, or JVM Deployment?](#native-delivery-choice)
- [Which Generated Assets, Tests, and Compatibility Evidence Should Drive the Decision?](#native-evidence-loop)
- [Which Neighboring Module Owns the Next Layer of Detail?](#native-module-handoffs)

## <a id="native-readiness-flow">How Does the Full Boot Native-image Journey Fit Together?</a>

<details>
<summary>Click for details</summary>

The native journey is one evidence chain rather than a set of unrelated tools:

```text
normal Boot application model
→ choose native goal and build-time environment
→ Spring AOT prepares bean model + generated assets
→ Runtime Hints/reachability metadata preserve dynamic needs
→ GraalVM compiles closed-world executable
→ focused native tests exercise native-sensitive paths
→ executable or OCI image is delivered
```

If one link is unclear, do not jump to the final compiler flag. Return to the earliest boundary whose assumptions are not proven.

</details>

- [Back to top](#back-to-top)

---

## <a id="native-readiness-checklist">What Should Be Checked before Committing to Native Deployment?</a>

<details>
<summary>Click for details</summary>

Before making native the production delivery form, confirm:

```text
runtime goal is measurable (startup/memory/density)
supported JDK/GraalVM/Boot toolchain is reproducible
build-time profiles/properties are understood
dynamic reflection/resources/proxies have supported metadata
critical third-party dependencies are native-ready
native-sensitive integration paths have tests
CI has enough time/CPU/memory for native builds
target OS/architecture strategy is defined
diagnostic/observability needs still have an acceptable path
JVM fallback or rollback strategy is understood where needed
```

The checklist converts “it builds on my machine” into a delivery decision.

</details>

- [Back to top](#back-to-top)

---

## <a id="native-failure-location">How Do You Locate a Failure across AOT, Closed-world, Hints, Build, and Dependencies?</a>

<details>
<summary>Click for details</summary>

Start with comparison evidence and move forward only after each boundary works:

```text
1. regular JVM build/run
2. normal JVM tests
3. AOT processing
4. inspect generated AOT/hint assets
5. native compilation
6. native startup
7. failing native-specific code path
```

Failure at step 3 points to the prepared Spring model; at step 5 to native/toolchain/reachability analysis; at step 7 to a runtime dynamic path, dependency, or environment difference. This sequence avoids turning every native error into “add reflection config.”

</details>

- [Back to top](#back-to-top)

---

## <a id="native-delivery-choice">How Do You Choose a Native Executable, Native Container Image, or JVM Deployment?</a>

<details>
<summary>Click for details</summary>

Separate **runtime form** from **packaging form**:

```text
native executable
→ direct OS/process delivery when the platform accepts binaries

native OCI image
→ same native runtime benefits inside a container delivery contract

JVM JAR / JVM OCI image
→ ordinary JVM runtime, widest compatibility and fastest build feedback
```

Choose native executable versus native image based mainly on deployment platform. Choose native versus JVM based on runtime economics, build cost, compatibility, diagnostics, and operational requirements.

</details>

- [Back to top](#back-to-top)

---

## <a id="native-evidence-loop">Which Generated Assets, Tests, and Compatibility Evidence Should Drive the Decision?</a>

<details>
<summary>Click for details</summary>

Use generated AOT sources/hints, native build logs, focused native tests, dependency support documentation, runtime benchmarks, and production-like memory/startup measurements as one feedback loop.

When an explicit application hint fixes a failure, add a test that proves the required registration. When a dependency upgrade removes a workaround, delete stale metadata. When runtime measurements show native provides no material benefit, keep the JVM path rather than preserving native complexity for its own sake.

Native readiness is therefore maintained evidence, not a one-time “compile succeeded” badge.

</details>

- [Back to top](#back-to-top)

---

## <a id="native-module-handoffs">Which Neighboring Module Owns the Next Layer of Detail?</a>

<details>
<summary>Click for details</summary>

Use the boundary map when a native problem expands:

```text
conditional beans / auto-config decisions
→ auto-configuration

normal lifecycle/events/runtime behavior
→ application-runtime

Gradle/Maven plugins, Buildpacks, OCI delivery
→ build-tooling-packaging

general Boot test slices/full-context strategy
→ testing

reflection/proxy/class-loader language semantics
→ Java / Spring Framework owner

native compiler internals and low-level GraalVM behavior
→ GraalVM owner
```

Native-image keeps the end-to-end integration story coherent while explicitly handing deeper neighboring mechanics to their primary owners.

</details>

- [Back to top](#back-to-top)
