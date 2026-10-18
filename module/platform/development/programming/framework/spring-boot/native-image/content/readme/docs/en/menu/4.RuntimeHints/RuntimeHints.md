<a id="back-to-top"></a>

# Runtime Hints for Dynamic Access

## Menu
- [Why Does Native Compilation Need Runtime Hints?](#runtime-hints-purpose)
- [What Does Spring's `RuntimeHints` Model Describe?](#runtime-hints-model)
- [How Do Reflection, Resource, Serialization, and JDK Proxy Hints Differ?](#reflection-resource-proxy-hints)
- [Which Hints Can Spring Infer and Which Must Application Code Contribute?](#inferred-vs-explicit-hints)
- [When Do `RuntimeHintsRegistrar` and Hint Annotations Apply?](#runtime-hints-registrar)
- [How Does Third-party Reachability Metadata Fit the Model?](#reachability-metadata)
- [How Do Missing Reflection, Resource, or Proxy Behaviors Point to Hint Problems?](#missing-hint-diagnosis)

## <a id="runtime-hints-purpose">Why Does Native Compilation Need Runtime Hints?</a>

<details>
<summary>Click for details</summary>

Static analysis follows code references well, but frameworks sometimes name or access things indirectly. A private method invoked reflectively, a resource opened by string path, or an interface used for a JDK proxy may not look reachable from ordinary bytecode calls.

Runtime Hints tell Spring/GraalVM that these capabilities must remain available in the native executable. They convert an otherwise invisible runtime assumption into explicit build metadata.

### References

- [Spring Boot 3.3 — Advanced Native Images Topics](https://docs.spring.io/spring-boot/3.3/reference/packaging/native-image/advanced-topics.html)
- [Spring Framework 6.1.14 — RuntimeHints API](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/aot/hint/RuntimeHints.html)

</details>

- [Back to top](#back-to-top)

---

## <a id="runtime-hints-model">What Does Spring's `RuntimeHints` Model Describe?</a>

<details>
<summary>Click for details</summary>

`RuntimeHints` is Spring's programmatic model for registering runtime requirements that need native reachability support. It groups different kinds of requirements instead of exposing application code directly to GraalVM JSON details.

Typical registrations describe:

```text
reflection
resources
Java serialization
JDK proxies
JNI
```

Spring AOT later translates these requirements into the metadata consumed by native-image tooling. This abstraction lets Spring libraries contribute native support using Spring-level semantics.

</details>

- [Back to top](#back-to-top)

---

## <a id="reflection-resource-proxy-hints">How Do Reflection, Resource, Serialization, and JDK Proxy Hints Differ?</a>

<details>
<summary>Click for details</summary>

Each hint category preserves a different capability:

```text
reflection hint
→ preserve type/member metadata and permitted reflective operations

resource hint
→ include classpath resource patterns that code will open later

serialization hint
→ retain types that require Java serialization at runtime

JDK proxy hint
→ make the required interface combination available for proxy creation

JNI hint
→ preserve reflective member access required through JNI
```

`SerializationHints` is specifically about Java serialization. JSON/object binding is a different case: Spring commonly contributes reflection hints for the types that a binding library must inspect or construct. Do not register a Java-serialization hint merely because an object is converted to or from JSON.

`RuntimeHints` also exposes a separate `jni()` category in Spring Framework 6.1.14. JNI remains a native-readiness boundary rather than a topic this module teaches in depth; the important point here is that JNI access has its own hint channel instead of being treated as ordinary reflection or serialization.

Use the narrowest capability that matches actual behavior. Registering broad reflective access to large package trees can hide design problems and increase native-image footprint/analysis without explaining what the application really needs.

</details>

- [Back to top](#back-to-top)

---

## <a id="inferred-vs-explicit-hints">Which Hints Can Spring Infer and Which Must Application Code Contribute?</a>

<details>
<summary>Click for details</summary>

Spring AOT understands many Spring contracts and can infer hints automatically. For example, common controller/binding patterns, configuration properties, framework-generated proxies, and supported libraries may contribute the necessary metadata without application code doing anything special.

Explicit hints become necessary when the application or a library performs dynamic access through a pattern Spring cannot recognize. That often occurs in custom reflection utilities, resources whose names are computed indirectly, unusual serialization, or third-party components without native support.

Do not start native migration by registering everything. Build/test first, inspect the failing dynamic path, and add the smallest missing contract.

</details>

- [Back to top](#back-to-top)

---

## <a id="runtime-hints-registrar">When Do `RuntimeHintsRegistrar` and Hint Annotations Apply?</a>

<details>
<summary>Click for details</summary>

Implement `RuntimeHintsRegistrar` when application/library code needs to programmatically register several native runtime requirements. The registrar receives a `RuntimeHints` instance and can register reflection, resources, proxies, Java serialization, or JNI requirements. It can be imported with supported Spring mechanisms such as `@ImportRuntimeHints`.

For common binding/reflection cases, supported annotations such as `@RegisterReflectionForBinding` can express a narrower intent directly. Prefer declarative support where it clearly matches the use case; use a registrar when the hint logic needs code or groups multiple registrations.

Hints themselves can be unit-tested with `RuntimeHintsPredicates`, giving fast feedback before a full native build.

</details>

- [Back to top](#back-to-top)

---

## <a id="reachability-metadata">How Does Third-party Reachability Metadata Fit the Model?</a>

<details>
<summary>Click for details</summary>

Not every dynamic dependency is owned by Spring. Third-party libraries can ship native-image configuration or participate in the broader GraalVM reachability-metadata ecosystem. Spring Boot applications benefit from that metadata because the native compiler can preserve required behavior without every application duplicating hints.

Think in ownership order:

```text
Spring/framework can infer it
→ framework-provided hints

library owns dynamic behavior
→ library/reachability metadata should ideally describe it

application creates custom dynamic behavior
→ application RuntimeHints
```

Application-level workarounds are sometimes necessary, but upstream library support is usually more reusable.

</details>

- [Back to top](#back-to-top)

---

## <a id="missing-hint-diagnosis">How Do Missing Reflection, Resource, or Proxy Behaviors Point to Hint Problems?</a>

<details>
<summary>Click for details</summary>

A strong hint signal is: **the same code path works on the JVM but fails in the native executable when it reaches a dynamic operation**. A missing resource, absent reflective constructor/member, or proxy-generation failure should trigger inspection of reachability metadata.

Diagnose narrowly:

```text
identify exact failing dynamic operation
→ check whether Spring/library already has a supported hint path
→ inspect generated metadata
→ add/test explicit hint if application owns the behavior
→ rebuild and re-run the focused path
```

The GraalVM tracing agent can help discover missing accesses, but its output still needs review; exercising broad test infrastructure can capture metadata unrelated to the production application.

</details>

- [Back to top](#back-to-top)
