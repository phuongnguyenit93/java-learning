---
video:
  url: ""
---

# Runtime Hints for Dynamic Access

<!--
VIDEO SCRIPT FORMAT

Section rules:
- Each H2 is one video section/chapter.
- The first section requires at least 1 Scene.
- From the second section onward, each section requires at least 1 Transition + 1 Scene.
- Step 6 may add more Scenes/Transitions when the Knowledge content needs them.

TRANSITION FORMAT

### Transition

**Time:** `MM:SS–MM:SS`

**Visual:**

Describe the transition, title card, or screen change.

**Script:**

Write the short bridge from the previous section to the current section.

**Purpose:**

Explain why this transition exists.

SCENE FORMAT

### Scene N — <optional scene title>

**Time:** `MM:SS–MM:SS`

**Visual:**

Describe what the viewer sees: slide, diagram, source code, terminal, API request/response, runtime output, highlight, or callout.

**Script:**

Write the narration/presentation script for this scene.

**Purpose:**

Explain what this scene teaches, demonstrates, or proves.
-->

## Why Does Native Compilation Need Runtime Hints?

<!-- VIDEO_SECTION -->

### Scene 1 — Make invisible runtime access visible to the build

**Time:** `00:00–00:41`

**Visual:**

Show three ordinary direct calls as solid arrows, then three indirect operations as dotted arrows: reflective constructor lookup, `getResource("...")`, and a JDK proxy created from interface names. Let static analysis see only the solid arrows until a `Runtime Hint` card supplies the missing edges.

**Script:**

Static analysis follows ordinary code references well, but frameworks often reach things indirectly. A constructor invoked through reflection, a resource selected by a string path, or an interface combination used for a JDK proxy may not look reachable from normal bytecode calls. Runtime Hints turn those hidden runtime assumptions into build evidence so the native executable retains the capability that the application will need later.

**Purpose:**

Motivate hints from an observable static-analysis blind spot before introducing Spring's API model.

## What Does Spring's `RuntimeHints` Model Describe?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:41–00:52`

**Visual:**

Replace the generic hint card with a `RuntimeHints` object that fans out into typed registries.

**Script:**

Rather than making application code hand-author GraalVM JSON for every case, Spring provides a programmatic model that describes the capability in Spring-level terms.

**Purpose:**

Bridge the need for metadata to the abstraction Spring exposes to application and library code.

### Scene 1 — One model, several native capabilities

**Time:** `00:52–01:28`

**Visual:**

Display a compact `RuntimeHints` API map with `reflection()`, `resources()`, `serialization()`, `proxies()`, and `jni()`; connect it downstream to generated native-image metadata.

**Script:**

`RuntimeHints` groups the native runtime requirements that Spring AOT needs to communicate: reflective type and member access, resource inclusion, Java serialization, JDK proxy interface combinations, and JNI access. Spring AOT translates those registrations into the metadata consumed by native-image tooling. The abstraction matters because libraries can express what their dynamic contract requires without every caller depending on GraalVM metadata file details.

**Purpose:**

Give the learner the shape and role of `RuntimeHints` without turning the section into low-level JSON configuration.

## How Do Reflection, Resource, Serialization, and JDK Proxy Hints Differ?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:28–01:39`

**Visual:**

Expand the typed registries into five rows and place a concrete runtime symptom beside each one.

**Script:**

Choosing the right hint starts by naming the capability that would otherwise be missing. Reflection, resources, serialization, proxies, and JNI solve different problems.

**Purpose:**

Move from the RuntimeHints container to the semantic distinction between hint categories.

### Scene 1 — Match the hint to the runtime operation

**Time:** `01:39–02:25`

**Visual:**

Use a five-row table: reflective constructor → reflection hint; `messages/banner.txt` → resource hint; Java `ObjectOutputStream` type → serialization hint; `ServiceA + Marker` → JDK proxy hint; native callback/member access → JNI hint. Add a red cross between `JSON binding` and `Java serialization hint`.

**Script:**

A reflection hint preserves the type or member metadata and the reflective operation you need. A resource hint includes classpath resource patterns. A serialization hint is specifically for Java serialization. A JDK proxy hint preserves the interface combination required to create that proxy, and JNI has its own hint channel. JSON binding is not the same thing as Java serialization: binding libraries commonly need reflection hints for the types they inspect or construct. Register the narrow capability the runtime path actually uses instead of broad access “just in case.”

**Purpose:**

Correct the previously truncated explanation and prevent the common confusion between Java serialization and object binding.

## Which Hints Can Spring Infer and Which Must Application Code Contribute?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:25–02:36`

**Visual:**

Split the table into `framework understands contract` and `custom/unknown dynamic behavior`.

**Script:**

You should not manually register every dynamic operation. The ownership question is whether Spring or a supported library already understands the contract.

**Purpose:**

Shift from hint taxonomy to the decision of who should provide the metadata.

### Scene 1 — Prefer inferred support, add explicit hints for custom behavior

**Time:** `02:36–03:17`

**Visual:**

On the left show controller binding, configuration properties, and framework proxies receiving green `inferred/contributed` badges. On the right show a custom reflection helper, computed resource name, unusual serializer, and unsupported library receiving `application/library hint needed` badges.

**Script:**

Spring AOT can infer or contribute hints for many supported Spring contracts, including common controller and binding patterns, configuration properties, and framework-generated proxies. Explicit hints become important when application or library code uses a dynamic pattern Spring cannot recognize: custom reflection utilities, indirectly computed resource names, unusual serialization, or third-party behavior with no native support. Start from the failing contract and add the smallest missing registration rather than registering entire packages broadly.

**Purpose:**

Teach a minimal, ownership-aware strategy for explicit hints instead of defensive over-registration.

## When Do `RuntimeHintsRegistrar` and Hint Annotations Apply?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:17–03:28`

**Visual:**

Show two authoring lanes: `declarative annotation` and `programmatic registrar`.

**Script:**

When application code does own the missing dynamic contract, Spring gives us both declarative and programmatic ways to express it—and a fast way to test it.

**Purpose:**

Introduce the concrete authoring choices required by the Knowledge section.

### Scene 1 — Use annotations for narrow intent, registrars for code-driven registration

**Time:** `03:28–04:13`

**Visual:**

Open a small code split-screen. Left: `@RegisterReflectionForBinding(MyDto.class)`. Right: a `RuntimeHintsRegistrar` registering one reflection type and one resource pattern, imported with `@ImportRuntimeHints`.

**Script:**

For a common reflection or binding case, an annotation such as `@RegisterReflectionForBinding` can express the intent directly. When the hint logic needs code or several registrations, implement `RuntimeHintsRegistrar`; it receives `RuntimeHints` and can register reflection, resources, proxies, Java serialization, or JNI requirements. `@ImportRuntimeHints` can attach that registrar to application configuration. Choose the narrow declarative form when it fits; use a registrar when the requirement is genuinely programmatic.

**Purpose:**

Restore both annotation and registrar paths that the previous Video omitted.

### Scene 2 — Test the registration without compiling a native image

**Time:** `04:13–04:39`

**Visual:**

Show a tiny unit test using `RuntimeHintsPredicates` against a populated `RuntimeHints` instance; green-check the expected reflection/resource registration.

**Script:**

You do not need a full native build to check every hint declaration. `RuntimeHintsPredicates` lets a unit test assert that the expected type, member, or resource registration exists. That gives fast feedback for an application-owned hint, while the later native test still proves the end-to-end runtime behavior.

**Purpose:**

Add the fast verification technique present in Knowledge and connect it to the broader testing strategy.

## How Does Third-party Reachability Metadata Fit the Model?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:39–04:50`

**Visual:**

Move the registrar code into a three-level ownership stack: framework → library/repository → application.

**Script:**

Application hints are only one ownership layer. If a library owns the dynamic behavior, the most reusable fix should usually live with that library or its reachability metadata.

**Purpose:**

Carry the hint-authoring discussion into dependency ownership instead of defaulting every workaround to application code.

### Scene 1 — Put metadata near the owner of the dynamic behavior

**Time:** `04:50–05:30`

**Visual:**

Animate three cases down the stack: Spring contract → framework-provided hints; third-party library → library metadata or GraalVM reachability-metadata repository; custom application reflection → application `RuntimeHints`. Show the same application consuming all three.

**Script:**

Think in ownership order. When Spring understands the contract, framework-provided hints should cover it. When a third-party library owns the dynamic behavior, that library can ship native-image configuration or be covered by the GraalVM reachability-metadata ecosystem. When your application invents the dynamic behavior, application `RuntimeHints` are appropriate. Local workarounds are sometimes necessary, but upstream library metadata is usually easier for every consumer to maintain.

**Purpose:**

Restore the Knowledge ownership hierarchy and reduce application-specific metadata duplication.

## How Do Missing Reflection, Resource, or Proxy Behaviors Point to Hint Problems?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:30–05:41`

**Visual:**

Turn the ownership stack into a troubleshooting flow that starts with `same path works on JVM?`.

**Script:**

Hints become a diagnosis only when the failure pattern points there. The strongest signal is a dynamic path that works on the JVM and disappears or fails only in native execution.

**Purpose:**

End by tying the hint model to evidence-based failure diagnosis.

### Scene 1 — Diagnose the exact dynamic operation

**Time:** `05:41–06:26`

**Visual:**

Show three native failures in a terminal: missing resource, reflective constructor unavailable, proxy creation failure. For each, follow arrows through `identify operation` → `check framework/library support` → `inspect generated metadata` → `add/test app hint if owned here` → `rebuild focused path`.

**Script:**

A missing resource, absent reflective constructor or member, or proxy-generation failure that appears only in the native executable should trigger a narrow reachability investigation. Identify the dynamic operation first. Check whether Spring or the library already has a supported hint path. Inspect the generated metadata. If the application owns the behavior, add and unit-test the smallest explicit registration, then rebuild and rerun that path. A tracing agent can help discover dynamic access, but its output still needs review because broad test activity can record metadata the production path never needs.

**Purpose:**

Give the learner a repeatable hint-diagnosis workflow instead of “add reflection config until it works.”
