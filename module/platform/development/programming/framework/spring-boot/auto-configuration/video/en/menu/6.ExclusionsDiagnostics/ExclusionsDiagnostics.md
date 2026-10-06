---
video:
  url: ""
---

# Exclusions and Diagnostics

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

## When Should Auto-configuration Be Excluded?

<!-- VIDEO_SECTION -->

### Scene 1 — Replace one choice or remove the whole candidate?

**Time:** `00:00–00:55`

**Visual:**

Show one `AcmeClientAutoConfiguration` tree. On the left, replace only the `AcmeClient` bean and keep the rest of the tree; label it “back-off.” On the right, remove the entire auto-configuration node; label it “exclusion.” Add examples: alternative integration path, deployment-specific decision, migration.

**Script:**

Back-off and exclusion solve different problems. If the application still wants the integration but needs to replace one supported choice, back-off is the narrow tool. Exclusion says this auto-configuration should not participate at all. That can be appropriate for a different integration path, a deployment-specific decision, or a migration. Starting with exclusion for every customization need usually throws away more configuration than necessary.

**Purpose:**

Give the learner a decision rule for choosing targeted back-off versus whole-candidate exclusion.

## Class, Name, and Property Exclusions

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:55–01:05`

**Visual:**

Keep the whole-candidate removal path and split it into three labeled controls: class, class name, and `spring.autoconfigure.exclude`.

**Script:**

Once exclusion is the right intent, Boot gives the application several ways to express that same decision.

**Purpose:**

Move from exclusion intent to the concrete mechanisms available to a Boot 3.3 application.

### Scene 2 — Three ways to identify the candidate

**Time:** `01:05–01:55`

**Visual:**

Show `exclude = AcmeClientAutoConfiguration.class`, `excludeName = "com.acme...AcmeClientAutoConfiguration"`, and `spring.autoconfigure.exclude=com.acme...AcmeClientAutoConfiguration`. Highlight “compile-time type reference,” “string name,” and “external configuration” beneath them. Beside the list, contrast “excluded” with “discovered but condition false.”

**Script:**

Class-based exclusion uses a direct type reference. Name-based exclusion identifies the candidate without requiring that class as a compile-time reference. The `spring.autoconfigure.exclude` property moves the decision into external configuration. All three remove the candidate from participation. That is different from a candidate being discovered and then naturally failing one of its conditions, and that distinction becomes visible in diagnostics.

**Purpose:**

Explain the practical differences among exclusion forms and preserve the semantic difference between exclusion and a normal negative condition match.

## Condition Evaluation Report

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:55–02:05`

**Visual:**

Slide the exclusion controls into a diagnostic panel titled “Condition Evaluation Report,” with positive and negative match columns.

**Script:**

When the final context surprises you, the condition report lets you stop guessing and inspect the predicates Boot actually evaluated.

**Purpose:**

Bridge configuration controls into the primary diagnostic evidence for auto-configuration decisions.

### Scene 3 — Read the report as a chain of predicates

**Time:** `02:05–03:00`

**Visual:**

Show a simplified report view for `AcmeClientAutoConfiguration`: candidate known, class condition positive, property condition negative, missing-bean condition not reached or not matched, exclusion state false. Use arrows to trace the first decisive negative result rather than scrolling the whole report.

**Script:**

For a missing `AcmeClient`, read the report as a decision chain. Was the auto-configuration a candidate? Did the class condition match? What property value did the property condition see? Did a missing-bean check back off? Was the candidate excluded? The value of the report is that “Boot did not create my bean” becomes a set of inspectable predicates. The report is evidence of the selection process, not a replacement for understanding what each condition means.

**Purpose:**

Teach a targeted way to use the Condition Evaluation Report rather than treating it as an undifferentiated startup dump.

## Boot Debug Diagnostics

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:00–03:10`

**Visual:**

Move the report panel into a terminal window and add a `debug` startup toggle above it.

**Script:**

You do not need custom logging in every auto-configuration just to see this evidence. Boot can surface the same condition report during startup diagnostics.

**Purpose:**

Move from the report as a concept to the practical startup diagnostic workflow.

### Scene 4 — Search for the candidate, then work outward

**Time:** `03:10–03:55`

**Visual:**

Show a terminal containing a long condition report. Use search highlighting to jump directly to `AcmeClientAutoConfiguration`, then highlight one negative condition and its message. Fade unrelated report sections into the background.

**Script:**

When debug diagnostics are enabled, positive and negative matches become visible during startup. Do not read the entire report from the top as if every line matters equally. Start with the auto-configuration you expected. Then work outward to the condition that explains its state. That turns a large diagnostic artifact into a focused answer to one question.

**Purpose:**

Give the learner an efficient report-reading habit that scales to real Boot applications.

## Diagnosing Unexpected Matches and Missing Beans

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:55–04:05`

**Visual:**

Turn the highlighted report entry into a seven-step troubleshooting checklist.

**Script:**

The report is most useful when it sits inside a repeatable diagnostic order instead of being consulted at random.

**Purpose:**

Bridge individual diagnostic tools into an end-to-end troubleshooting playbook.

### Scene 5 — Diagnose from discovery to focused reproduction

**Time:** `04:05–05:05`

**Visual:**

Reveal the checklist one item at a time: dependency/candidate exists → exclusion state → configuration-level conditions → bean-level conditions and visible definitions → properties and application type → ordering assumptions → focused context reproduction. End on an `ApplicationContextRunner` box with four inputs: classpath, properties, user configuration, context type.

**Script:**

Work from the outside in. Confirm the dependency and candidate exist. Check exclusions. Inspect configuration-level conditions, then bean-level conditions and the definitions visible to them. Verify the relevant properties and application type. Challenge any ordering assumption. Finally, reproduce the smallest state with a focused context runner. If that focused test and the real application disagree, compare the inputs that actually drive the decision: classpath, properties, user configuration, and context type.

**Purpose:**

Provide a bounded, evidence-first diagnostic sequence that separates discovery, selection, registration, and environment differences.
