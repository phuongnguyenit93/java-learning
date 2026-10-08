<a id="back-to-top"></a>

# Native Image Trade-offs, Compatibility, and Metadata

## Menu
- [How Should Faster Startup and Lower Memory Be Evaluated?](#native-runtime-gains)
- [What Build-time and Resource Costs Increase?](#native-build-cost)
- [How Does Third-party Dependency Native Readiness Affect the Application?](#dependency-native-readiness)
- [Why Does Reachability Metadata Quality Matter?](#reachability-metadata-quality)
- [How Do You Separate Hint, Metadata, Build-integration, and Dependency Failures?](#native-failure-classification)
- [When Is a Regular JVM Deployment the Better Choice?](#when-not-native)

## <a id="native-runtime-gains">How Should Faster Startup and Lower Memory Be Evaluated?</a>

<details>
<summary>Click for details</summary>

Measure native benefits against the application's actual operational problem. Fast cold startup is valuable for scale-to-zero, bursty workers, command applications, and platforms that frequently create instances. Lower memory can increase service density or reduce resource reservations.

But “starts faster” is not the same as “serves every workload faster.” Compare representative request throughput, tail latency, memory under load, CPU consumption, and operational behavior after warm-up. A long-running JVM may recover part of its startup cost through JIT optimization, while a native executable trades that dynamic optimization model for ahead-of-time compilation.

Use measurements from equivalent configuration and load rather than headline benchmarks from unrelated applications.

</details>

- [Back to top](#back-to-top)

---

## <a id="native-build-cost">What Build-time and Resource Costs Increase?</a>

<details>
<summary>Click for details</summary>

Native compilation is normally much slower and more memory/CPU intensive than producing a JVM JAR. CI also needs a suitable GraalVM/native toolchain or a builder that provides one, and platform-specific output may require separate build jobs.

The feedback-cost difference affects engineering practice:

```text
ordinary source change
→ JVM compile/test feedback first

native-sensitive change
→ focused AOT/hint tests
→ native build/test when signal is needed

release
→ reproducible native build for each supported target
```

Native build cost should be budgeted as part of the delivery pipeline, not discovered after the application has already committed to a native runtime model.

</details>

- [Back to top](#back-to-top)

---

## <a id="dependency-native-readiness">How Does Third-party Dependency Native Readiness Affect the Application?</a>

<details>
<summary>Click for details</summary>

An application can be AOT-friendly while one dependency still uses unsupported or undescribed dynamic behavior. Libraries that use deep reflection, runtime classpath scanning, bytecode generation, native libraries, or unusual resource discovery may need dedicated native support.

Before adopting a dependency in a native-first service, check whether the library/version is known to work with GraalVM, ships reachability metadata, or is covered by the broader reachability-metadata repository. A newer/older version can materially change readiness.

If application code must maintain a large private hint workaround for a dependency, record that maintenance cost and prefer an upstream fix where possible.

</details>

- [Back to top](#back-to-top)

---

## <a id="reachability-metadata-quality">Why Does Reachability Metadata Quality Matter?</a>

<details>
<summary>Click for details</summary>

Reachability metadata is executable-build input. Missing metadata can remove required behavior; metadata that is too broad can retain unnecessary types/resources and hide which dynamic contract is actually required.

Good metadata is:

```text
owned close to the dynamic behavior
specific enough to explain the need
version-aware when library behavior changes
covered by tests that exercise the dynamic path
```

Treat metadata changes like code changes. Review them, keep them in source control when application-owned, and validate them against the dependency version they describe.

### References

- [Spring Boot 3.3 — Advanced Native Images Topics](https://docs.spring.io/spring-boot/3.3/reference/packaging/native-image/advanced-topics.html)
- [GraalVM Reachability Metadata Repository](https://github.com/oracle/graalvm-reachability-metadata)

</details>

- [Back to top](#back-to-top)

---

## <a id="native-failure-classification">How Do You Separate Hint, Metadata, Build-integration, and Dependency Failures?</a>

<details>
<summary>Click for details</summary>

Classify by the boundary that last worked:

```text
JVM application fails too
→ ordinary application/configuration problem first

JVM works, AOT processing fails
→ AOT application-model / unsupported bean-registration problem

AOT succeeds, native compile fails
→ native build configuration, unsupported construct, metadata/toolchain

native executable starts but one dynamic path fails
→ hints/resources/proxy/reflection/dependency metadata

only one third-party feature fails
→ inspect that dependency's native support before broad application workarounds
```

Preserve logs and generated AOT/native metadata as evidence. Changing multiple hint/build settings at once makes the root cause harder to prove.

</details>

- [Back to top](#back-to-top)

---

## <a id="when-not-native">When Is a Regular JVM Deployment the Better Choice?</a>

<details>
<summary>Click for details</summary>

Prefer the JVM when native-specific runtime benefits are small compared with build and compatibility cost. Examples include long-running services with infrequent scaling, workloads that benefit strongly from JIT optimization, dependency stacks with weak native support, teams that depend on JVM-only diagnostics/agents, or pipelines where native build latency dominates delivery time.

The JVM is not a fallback for a failed modernization. It is a first-class Spring Boot deployment model with the widest dynamic/runtime compatibility. A good architecture can keep both options open and choose based on measured constraints.

</details>

- [Back to top](#back-to-top)
