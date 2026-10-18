---
video:
  url: ""
---

# Closed-World Consequences for a Boot Application

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

## What Does the Closed-world Assumption Mean for a Boot Application?

<!-- VIDEO_SECTION -->

### Scene 1 — Build from a closed set of evidence

**Time:** `00:00–00:39`

**Visual:**

Draw a graph of reachable application classes. Fade nodes with no reachable path, then add dotted edges labeled `reflection`, `resource`, and `proxy metadata` to show how otherwise invisible behavior can be preserved.

**Script:**

GraalVM Native Image reasons from a closed set of code and metadata available during the build. If code appears unreachable, it may not be present in the executable; if behavior is reached indirectly, the build needs enough metadata to understand that path. Spring AOT adapts the application to this model, but closed-world does not mean “nothing dynamic can happen.” It means dynamic behavior has to fit inside the code and capabilities that the build made reachable.

**Purpose:**

Establish the closed-world mental model that explains later classpath, bean-graph, and hint constraints.

## Why Does the Build-time Classpath Become Part of the Executable Model?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:39–00:50`

**Visual:**

Freeze the reachable graph and place a snapshot frame around the build-time classpath.

**Script:**

The compiler can only reason about what the build can see. In Spring Boot, that classpath also changes which configuration and beans exist before GraalVM even starts its analysis.

**Purpose:**

Connect closed-world reachability to Boot's classpath-driven configuration model.

### Scene 1 — Classpath presence becomes a build input

**Time:** `00:50–01:29`

**Visual:**

Show an `optional-library.jar` being present during one build and absent during another. In parallel, reveal an auto-configuration card and proxy class that appear only in the first generated model.

**Script:**

Classpath presence is a major Boot decision input. During AOT and native processing, the build-time classpath influences auto-configuration, bean definitions, generated proxies, and reachability. Adding a JAR after the native executable has already been built cannot make those classes appear inside that executable. Conversely, changing a dependency version can alter the prepared application model without changing your source. Treat the complete classpath as a reproducible native build input.

**Purpose:**

Show why dependency composition is part of native correctness rather than a late deployment detail.

## Why Can the Bean Graph Not Change Freely after AOT Processing?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:29–01:40`

**Visual:**

Move from the classpath snapshot into a bean graph, then stamp the graph `prepared by AOT`.

**Script:**

Classpath decisions feed the Spring container. Once AOT encodes that prepared container, arbitrary late structural changes no longer have the same freedom as a normal JVM startup.

**Purpose:**

Move from static build inputs to the Spring-specific structural consequence of those inputs.

### Scene 1 — AOT fixes the prepared BeanFactory structure

**Time:** `01:40–02:20`

**Visual:**

Show three beans and one generated proxy in a graph. Try to add a new infrastructure bean at runtime and block the arrow at the `not in prepared model` boundary; keep a property-value arrow flowing into an existing bean.

**Script:**

Spring AOT prepares a concrete `BeanFactory` model and generates initialization for it. A library that relies on arbitrary late bean registration, runtime scanning, or bootstrap code invisible to AOT cannot assume the native process will reconstruct a completely new graph. That does not freeze every value. Existing beans can still read runtime configuration and change business state. The restriction is structural: registrations and infrastructure that define the container need an AOT-compatible path so Spring can understand them during processing.

**Purpose:**

Separate bean-graph structure from ordinary runtime state so the learner does not overgeneralize the closed-world restriction.

## How Can Profiles and Properties Affect Build-time Bean Decisions?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:20–02:32`

**Visual:**

Place two switches beside the bean graph: `profile/property changes bean existence` and `property changes bean value`.

**Script:**

Profiles and properties are where structural and ordinary runtime configuration are easy to confuse. The distinction is whether the input changes which beans exist.

**Purpose:**

Prepare the learner to apply Boot's AOT condition restrictions without claiming all properties are fixed at build time.

### Scene 1 — Structural conditions are build-time decisions

**Time:** `02:32–03:17`

**Visual:**

Show AOT evaluating a `@Profile("prod")` and an `@ConditionalOnProperty` style condition against the build environment. Freeze the resulting bean graph, then attempt to flip those inputs only at runtime and show `graph unchanged`.

**Script:**

AOT fully prepares the `BeanFactory`, so conditions are evaluated against the build-time environment. Profiles are implemented through conditions, which means a profile that changes the bean structure has to be supplied to the AOT build. The beans selected then are the ones encoded in the generated model. `@Profile`, profile-specific configuration, and properties that decide whether a bean is created—such as `@ConditionalOnProperty` or an `.enabled` switch—cannot be treated as runtime switches that rebuild a different native bean graph.

**Purpose:**

Correctly teach the Boot 3.3 AOT restriction on profiles and bean-creation properties.

### Scene 2 — Runtime values still have a place

**Time:** `03:17–03:45`

**Visual:**

Keep the same bean graph but change a database URL, timeout, and credential value flowing into an existing bean. Add a green label: `value changes, structure does not`.

**Script:**

The boundary is not “all properties are build-time.” A database URL, timeout, credential, or a profile that only changes values without affecting conditions can still vary when the application runs. What AOT cannot freely revisit is the structural decision about which bean or configuration exists. When diagnosing a native mismatch, ask whether the input changes a value or changes the graph.

**Purpose:**

Preserve the important runtime-configuration nuance required by the updated Knowledge.

## Which Dynamic Behaviors Deserve Native-readiness Review?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:45–03:56`

**Visual:**

Unfreeze the bean graph and overlay dotted runtime edges that static analysis cannot see directly.

**Script:**

Even with the bean structure prepared, individual code paths can still depend on dynamic targets. Those are the next places to inspect for reachability evidence.

**Purpose:**

Move from structural AOT decisions to runtime capabilities that may require hints or dependency support.

### Scene 1 — Review dynamic targets, not keywords

**Time:** `03:56–04:42`

**Visual:**

Build a checklist around a sample service: reflection over a DTO, resource lookup by name, JDK proxy interfaces, serialization, generated bytecode/dynamic class loading, JNI, and configuration that names a class indirectly. Put a small `not automatically unsupported` badge above the list.

**Script:**

Review behavior whose target is chosen dynamically and may not appear as a direct code reference: reflection, classpath resources, JDK proxies, serialization that needs reflective construction, runtime-generated code or class loading, JNI, and configuration that names classes or resources indirectly. This is a review checklist, not a ban list. Spring and many libraries contribute native support automatically. The question is whether the build has enough evidence to retain the exact target and operation your application will use.

**Purpose:**

Teach a practical native-readiness checklist without implying that every dynamic Java feature is unsupported.

## How Do Closed-world Restrictions Differ from Ordinary Runtime Configuration?

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:42–04:53`

**Visual:**

Divide the screen into `structure/reachability fixed by build` and `runtime state still changes`.

**Script:**

The final boundary is operational: a native application is still a live application. Closed-world constrains its prepared executable model, not every decision the service makes after startup.

**Purpose:**

End with a diagnostic distinction the learner can apply to real production failures.

### Scene 1 — Diagnose structure, reachability, and ordinary configuration separately

**Time:** `04:53–05:36`

**Visual:**

Place three examples in columns: `wrong DB URL → runtime config`, `runtime flag expects excluded bean → AOT structural mismatch`, `reflective constructor missing only natively → reachability/hint issue`. Then show requests and business state changing normally underneath all three.

**Script:**

A native service still accepts requests, opens database connections, schedules work, reads external configuration, and changes business state. A wrong database URL is an ordinary runtime configuration problem. A runtime setting that expects a bean graph different from the one AOT generated is a structural mismatch. A reflective call that works on the JVM but fails only in native points toward reachability metadata. Keeping those categories separate prevents every native failure from being blamed on “closed-world” in the abstract.

**Purpose:**

Give the learner a three-way diagnostic model for runtime configuration, AOT structure, and dynamic reachability.
