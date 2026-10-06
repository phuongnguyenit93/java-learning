---
video:
  url: ""
---

# Testing Auto-configuration Decisions

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

## Why Auto-configuration Needs Focused Context Tests

<!-- VIDEO_SECTION -->

### Scene 1 — Test the decision matrix, not one startup

**Time:** `00:00–00:55`

**Visual:**

Show a matrix with rows for user bean absent/present, property enabled/disabled, optional library present/absent, and non-web/Servlet/Reactive context. Contrast it with a single large `@SpringBootTest` box covering only one combination.

**Script:**

Auto-configuration is a decision matrix. A bean can appear or disappear when a user bean is added, a property flips, an optional class vanishes, or the context type changes. A full `@SpringBootTest` can prove that one application starts, but it is often too broad to explain one condition. Focused context tests let us vary exactly the input that drives the decision and assert the resulting context.

**Purpose:**

Explain why the testing strategy must mirror the condition matrix rather than relying on one full-application happy path.

## ApplicationContextRunner

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:55–01:05`

**Visual:**

Collapse the test matrix into a small `ApplicationContextRunner` code window.

**Script:**

For non-web auto-configuration, `ApplicationContextRunner` is the basic tool for building those small, controlled contexts.

**Purpose:**

Move from testing strategy to the concrete runner used for focused auto-configuration verification.

### Scene 2 — Inputs in, context assertions out

**Time:** `01:05–02:00`

**Visual:**

Show `new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(AcmeClientAutoConfiguration.class))`. Add input blocks for user configuration, property values, and classloader. Then animate `.run(context -> ...)` creating and closing a fresh context.

**Script:**

The runner registers the auto-configuration under test without bootstrapping a complete production application. Each invocation starts a fresh context, applies the inputs you specify, gives the test that context for assertions, and closes it afterward. That makes the evidence local: the setup tells us exactly what state Boot saw, and the assertions tell us exactly which beans should or should not exist.

**Purpose:**

Show the focused test lifecycle and why it produces clearer condition evidence than a broad application test.

## Back-off and Property Variant Tests

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:00–02:10`

**Visual:**

Duplicate the runner into two lanes and label the changed inputs “user bean” and “property value.”

**Script:**

The real value appears when the same runner is reused across deliberate variants instead of assuming the annotations behave as intended.

**Purpose:**

Connect the runner mechanics to the two most important selection variants: user override and property state.

### Scene 3 — Prove both sides of each condition

**Time:** `02:10–03:05`

**Visual:**

First lane: `.withUserConfiguration(CustomClientConfiguration.class)` and an assertion that there is one `AcmeClient`, with `DefaultAcmeClient` crossed out. Second pair: `acme.client.enabled=true` shows the default integration present; `false` shows it absent. Add callouts for `havingValue`, `matchIfMissing`, and condition target type.

**Script:**

Back-off should be tested with and without the user replacement, not inferred from the annotation. Property conditions deserve the same treatment: run the enabled and disabled variants and include the missing-property case when `matchIfMissing` matters. Testing both sides catches inverted `havingValue` logic, wrong assumptions about defaults, and missing-bean conditions that target the wrong type.

**Purpose:**

Turn back-off and property semantics into repeatable two-sided evidence rather than annotation-based assumptions.

## Testing Classpath Absence with FilteredClassLoader

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:05–03:15`

**Visual:**

Move the changed-input highlight from properties to the classloader block.

**Script:**

Properties and user beans are easy to vary. Optional-class behavior needs a different kind of input: a classpath where the dependency is intentionally hidden.

**Purpose:**

Bridge context-state variants to deterministic classpath-absence testing.

### Scene 4 — Hide the class, do not uninstall the dependency

**Time:** `03:15–04:05`

**Visual:**

Show `.withClassLoader(new FilteredClassLoader(AcmeClient.class))`. On the outcome side, display “optional bean absent” and “context starts.” Place `NoClassDefFoundError` in a red crossed-out box. Highlight the isolated optional configuration structure from the previous chapter.

**Script:**

`FilteredClassLoader` lets the test simulate a classpath where a class or package is missing even though the test build still contains that dependency. A correct optional integration should simply stop applying. The stronger assertion is not just that the bean is absent; it is that the context starts cleanly without a linkage failure. That proves both the class condition and the isolation around optional method signatures.

**Purpose:**

Show how classpath absence becomes deterministic test evidence for optional-dependency and class-loading safety.

## Servlet and Reactive Context Runners

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:05–04:15`

**Visual:**

Turn the generic context icon into three icons: non-web, Servlet, Reactive.

**Script:**

The context type itself can be a condition input, so the runner must represent the environment the auto-configuration is supposed to see.

**Purpose:**

Move from classpath input to application-context-type input.

### Scene 5 — Match the runner to the condition being tested

**Time:** `04:15–05:00`

**Visual:**

Show three pairings: core configuration → `ApplicationContextRunner`; Servlet-only configuration → `WebApplicationContextRunner`; Reactive-only configuration → `ReactiveWebApplicationContextRunner`. Cross out a Servlet condition being “proven” in a non-web context.

**Script:**

The plain runner creates a non-web context. If the auto-configuration depends on a Servlet or Reactive environment, use the corresponding web-context runner. Choosing the runner is part of the test input. A web condition cannot be meaningfully verified in the wrong context type. Broader MVC, WebFlux, and test-slice strategy still belongs to the dedicated testing and web modules.

**Purpose:**

Teach that context type is explicit test data while preserving the module boundary around broader web testing.

## Condition Evaluation Evidence in Tests

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:00–05:10`

**Visual:**

Keep a failing assertion on screen and reveal a Condition Evaluation Report panel beside it.

**Script:**

Bean presence is the primary contract, but when an assertion fails, the condition report can explain which predicate produced the result.

**Purpose:**

Bridge outcome assertions into supporting diagnostic evidence without making report text the test contract.

### Scene 6 — Use the report to explain, not to snapshot everything

**Time:** `05:10–06:00`

**Visual:**

Show a failing “expected bean present” assertion. Add a condition-report logging listener or report-inspection box, highlight one negative condition, then return to the test input and correct it. Cross out a giant snapshot assertion over the entire report.

**Script:**

When a focused test surprises you, log or inspect the Condition Evaluation Report and find the negative condition that explains the outcome. Then fix the actual applicability rule or the test input. Do not turn every test into a snapshot of the full report. Bean presence, absence, and behavior remain the durable contract; the report is supporting evidence that tells you why Boot made that decision.

**Purpose:**

Show how condition-report diagnostics complement focused context assertions while avoiding brittle report snapshots.
