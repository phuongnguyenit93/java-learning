---
video:
  url: ""
---

# Back-off and User Control

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

## Back-off as a Design Contract

<!-- VIDEO_SECTION -->

### Scene 1 — Ask who owns the decision

**Time:** `00:00–00:55`

**Visual:**

Show a decision card with the question “Has the application already chosen an `AcmeClient`?” Branch it to “yes → default disappears” and “no → contribute `DefaultAcmeClient`.” Add smaller examples of property switches and narrower applicability conditions around the same decision card.

**Script:**

Back-off starts with a design question, not an annotation: has the application already made this decision? If the answer is yes, a reusable default serving the same role should step aside. If the answer is no, the integration can provide its safe default. `ConditionalOnMissingBean` is a common tool for that contract, but the larger principle is application control. A good default is useful precisely because it knows when not to participate.

**Purpose:**

Define back-off as an intentional ownership contract that keeps auto-configuration convenient without making it invasive.

## How User-defined Beans Take Control

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:55–01:05`

**Visual:**

Turn the abstract “application already chose” branch into two `ApplicationContextRunner` lanes: one empty user configuration and one containing `CustomClientConfiguration`.

**Script:**

Now make that contract observable. The easiest proof is to build the context twice and change only whether the application supplies the bean.

**Purpose:**

Move from the design rule to deterministic evidence that a user-defined bean changes the resulting context.

### Scene 2 — Two contexts prove the override path

**Time:** `01:05–01:55`

**Visual:**

Show the same auto-configuration under test in two focused-context diagrams. Run A: no user bean → `DefaultAcmeClient` appears. Run B: `CustomClientConfiguration` contributes `AcmeClient` first → the default branch is crossed out. Keep the rest of the integration present in both diagrams.

**Script:**

Boot applies auto-configuration after user-defined bean definitions have been registered, so a missing-bean condition can see the application's choice. In the first context, there is no `AcmeClient`, so the default can be registered. In the second, user configuration contributes an `AcmeClient` first, and the default backs off. Notice that we are replacing one collaborator, not disabling the whole integration.

**Purpose:**

Show the intended consumer experience: a supported bean override removes the default while leaving unrelated auto-configuration behavior intact.

## Bean Type Specificity and Condition Visibility

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:55–02:05`

**Visual:**

Zoom from the two context outcomes into the bean method signature and highlight both the condition target and the declared return type separately.

**Script:**

The override can still fail if the condition is asking about the wrong type or if the bean definition exposes less type information than later conditions expect.

**Purpose:**

Bridge successful back-off into the type-visibility details that make bean conditions deterministic.

### Scene 3 — Condition target and return type are related, not identical

**Time:** `02:05–03:00`

**Visual:**

Show this focused snippet and highlight each line in turn: `@ConditionalOnMissingBean(AcmeClient.class)` above `DefaultAcmeClient acmeClient()`. Beside it, draw two labels: “back-off role = any `AcmeClient`” and “definition type information = `DefaultAcmeClient`.” Contrast briefly with a method that returns only the broad `AcmeClient` interface.

**Script:**

Here the condition explicitly asks whether any `AcmeClient` already exists. That preserves the intended back-off contract: any user-provided implementation prevents the default. The bean method itself returns `DefaultAcmeClient`, which preserves concrete type information for other conditions that may need to reason about that implementation. Those are two authoring choices with different jobs. Making the return type broad does not automatically express the right condition target, and making the condition target concrete can accidentally narrow the override contract.

**Purpose:**

Make the current Knowledge distinction precise: condition target defines the replacement role, while return-type specificity affects bean-definition type visibility.

## Configurable Defaults vs Forced Policy

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:00–03:10`

**Visual:**

Pull back from the method signature to a side-by-side product-design comparison labeled “configurable default” and “forced policy.”

**Script:**

Correct type matching solves only the mechanism. The bigger quality test is whether the integration offers a supported path for the application to choose differently.

**Purpose:**

Move from bean-condition mechanics to the design trade-off between convenience and framework policy.

### Scene 4 — A default should have a deliberate escape hatch

**Time:** `03:10–04:00`

**Visual:**

On the left, show “no `AcmeClient` → create default” and “custom `AcmeClient` → use custom.” On the right, show “always create default” leading to ambiguity or an ignored custom bean, with a red warning. Add a callout: “extension point is intentional.”

**Script:**

A configurable default chooses for the application only until the application chooses for itself. A forced policy keeps creating the framework's choice even when the consumer has a legitimate replacement. Not every infrastructure bean needs unlimited customization, but the supported extension points should be deliberate. Consumers should not have to discover an accidental bean conflict just to understand how to customize a starter.

**Purpose:**

Teach the design standard behind back-off so the learner can judge an integration, not merely reproduce an annotation pattern.

## Back-off Failure Patterns

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:00–04:10`

**Visual:**

Turn the “configurable default” diagram into a diagnostic board with five failure cards: wrong type, early evaluation, overlapping defaults, unconditional infrastructure, name/type mismatch.

**Script:**

When back-off misbehaves, the failure usually falls into a small number of recognizable categories. Diagnose the condition's actual view of the context instead of guessing from the final bean list.

**Purpose:**

Bridge design intent into a concrete failure-classification and debugging workflow.

### Scene 5 — Diagnose from the condition's point of view

**Time:** `04:10–05:10`

**Visual:**

Walk through the five failure cards, then show a Condition Evaluation Report panel beside the bean definitions visible at evaluation time. Highlight the exact target type/name field. End with a focused-context test pair: supported override present → default absent; supported override absent → default present.

**Script:**

Common failures include checking the wrong type, evaluating before the relevant definition is visible, letting multiple missing-bean defaults become order-sensitive, creating infrastructure unconditionally, or confusing name-based and type-based searches. The practical workflow is to inspect the condition report, inspect the definitions available at that point, and verify the exact target. Then prove the supported override in both directions. A correct back-off contract should be repeatable, not something that works only in one accidental startup order.

**Purpose:**

Give the learner a bounded diagnostic method and a two-sided test for validating back-off behavior.
