---
video:
  url: ""
---

# Protected Branches and Merge Eligibility

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

## Protected Branches and Their Source-Governance Purpose

<!-- VIDEO_SECTION -->

### Scene 1 — Protected Branches and Their Source-Governance Purpose

**Time:** `00:00–01:01`

**Visual:**

Highlight gateway/main and overlay a Protected boundary. Separate arrows for direct push and merge-request integration, with no fabricated access-denied toast.

**Script:**

A protected branch puts tighter controls on important source lines such as main or release branches. It does not repair incorrect application logic; it governs who can update the branch and how proposed changes are accepted. An being a Developer does not by itself prove direct push permission to protected main. Inspect the matching branch rule in the current GitLab interface, often under Settings, Repository, Branch rules, rather than relying only on an older Protected branches screenshot. The effective rule and actor determine the outcome.

**Purpose:**

Show branch protection as an access and governance mechanism, not a test suite.

## Allowed to Merge vs Allowed to Push and Merge

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:01–01:14`

**Visual:**

Keep the previous scene’s evidence as a smaller left panel, highlight "protection controls actors and acceptance paths", then reveal the next question on the right with a scope-change arrow.

**Script:**

The branch is protected, yet authorized merging need not grant the same person direct push access.

**Purpose:**

Bridge the earlier observation about protection controls actors and acceptance paths to the next decision about Allowed to merge and Allowed to push and merge differ without resetting the case.

### Scene 1 — Allowed to Merge vs Allowed to Push and Merge

**Time:** `01:14–02:18`

**Visual:**

Display separate configured controls: Allowed to merge = Maintainers, Allowed to push and merge = No one. Distinguish accepted MR arrows from prohibited direct-push arrows.

**Script:**

Do not infer all protected-branch rights from a single selector. Allowed to merge concerns who may integrate an MR into the target. Allowed to push and merge governs direct updates as well as related merge access under its configuration. In an example where Maintainers can merge but direct pushes are set to No one, an eligible MR may be accepted without opening the direct-push route. This is a hypothetical rule combination; we have not modified a hosted project. Read both controls before concluding that a Maintainer should gain broad push access.

**Purpose:**

Teach the separate authorization paths and why a review-only merge workflow is possible.

## Project vs Top-Level Group Protection: GitLab.com, Self-Managed, Dedicated (Premium/Ultimate, 17.6+)

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:18–02:31`

**Visual:**

Keep the previous scene’s evidence as a smaller left panel, highlight "Allowed to merge and Allowed to push and merge differ", then reveal the next question on the right with a scope-change arrow.

**Script:**

Project-level protection handles one repository; could a parent group enforce rules across several projects?

**Purpose:**

Bridge project action permissions to top-level group Owner authority and Premium/Ultimate availability across all three GitLab offerings.

### Scene 1 — Project vs Top-Level Group Protection: GitLab.com, Self-Managed, Dedicated (Premium/Ultimate, 17.6+)

**Time:** `02:31–03:32`

**Visual:**

Compare `Project → Settings → Repository → Branch rules` against `Top-level Group → Settings → Repository → Protected branches`. Show `Premium/Ultimate · GitLab.com | Self-Managed | Dedicated · GA 17.6`, `Top-level group Owner`, and `No subgroup configuration`. Draw inherited protection arrows to gateway/billing and label the *separate REST API* `Self-Managed only`.

**Script:**

Top-level group protected branches are available in Premium and Ultimate on GitLab.com, Self-Managed and Dedicated, generally available since 17.6. A top-level group Owner creates the rule; subgroups cannot configure group protection. The rule applies to member projects but cannot be edited there. The separate group protected branches REST API docs list Self-Managed only; do not confuse that API limit with UI support. Chi must confirm tier, group ownership and available settings before recording; this diagram is not a live success claim.

**Purpose:**

Teach all three UI offerings and the top-level Owner/no-subgroup boundary while distinguishing the Self-Managed-only REST endpoint.

## Overlapping Rules: Most Permissive Access vs Strictest Code Owner Requirement

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:32–03:45`

**Visual:**

Keep all three offerings visible and show inherited group protection beside a separately configured project rule; highlight the matching branch patterns.

**Script:**

Several protection rules can match one branch. We need the platform's actual precedence, not an assumption.

**Purpose:**

Bridge inherited group scope and separate project rules to the next question: how multiple matching rules combine permission and owner-approval requirements.

### Scene 1 — Overlapping Rules: Most Permissive Access vs Strictest Code Owner Requirement

**Time:** `03:45–04:44`

**Visual:**

Overlay patterns main = No one push and m* = Developer push. Label the access result 'most permissive matching access'; show separate strictest Code Owner requirement.

**Script:**

When several GitLab protection patterns match a branch, the exact-name rule does not automatically override a wildcard. For push, merge, and force-push access, GitLab generally applies the most permissive matching setting. Code Owner approval requirements instead follow the most restrictive matching rule. That asymmetry matters: a broad m* wildcard can weaken what appeared to be a locked main pattern, while an owner-approval requirement remains strict. Compare the two settings carefully, and do not import GitHub's one-matching-branch-protection mental model into GitLab.

**Purpose:**

Explain overlapping-rule precedence and the security pitfall of permissive wildcards.

## Required Code Owner Approval on Protected Target Branches

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:44–04:57`

**Visual:**

Keep the previous scene’s evidence as a smaller left panel, highlight "access can be permissive while owner review is strict", then reveal the next question on the right with a scope-change arrow.

**Script:**

The strictest owner requirement matters only when an active rule actually requires an eligible owner for these changed paths.

**Purpose:**

Bridge the earlier observation about access can be permissive while owner review is strict to the next decision about CODEOWNERS alone does not create a mandatory gate without resetting the case.

### Scene 1 — Required Code Owner Approval on Protected Target Branches

**Time:** `04:57–05:56`

**Visual:**

Display MR !57 changing pricing/Calculator.java, a /pricing/ CODEOWNERS pattern, and an explicit owner-approval requirement on protected main. Show Chi's eligibility separately.

**Script:**

When supported protected-branch settings require Code Owner approval, changing a covered pricing file can require a qualifying owner approval. Merely having CODEOWNERS does not impose that blocking requirement on every tier or branch. Listed people must also satisfy the relevant membership and approval eligibility conditions. Binh may approve the general business change while the configured pricing-owner gate remains unmet. Inspect the matching path, protected target setting, and actual approval status instead of counting any positive review as an owner approval.

**Purpose:**

Connect path-level accountability, tier support and protected target approval semantics.

## Approvals and Check Statuses as Merge Gates, Not Pipeline Implementation

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:56–06:09`

**Visual:**

Keep the previous scene’s evidence as a smaller left panel, highlight "CODEOWNERS alone does not create a mandatory gate", then reveal the next question on the right with a scope-change arrow.

**Script:**

A valid code-owner review does not always settle the merge decision; technical checks may be separate gates.

**Purpose:**

Bridge the earlier observation about CODEOWNERS alone does not create a mandatory gate to the next decision about pipeline status is evidence, not the CI implementation lesson without resetting the case.

### Scene 1 — Approvals and Check Statuses as Merge Gates, Not Pipeline Implementation

**Time:** `06:09–07:11`

**Visual:**

Show an MR merge widget with Approvals, Discussions, and Pipeline states Pending/Failed/Success. Label each as hypothetical, with no invented runner output.

**Script:**

GitLab can evaluate approvals, unresolved discussions, and pipeline or check status when deciding merge eligibility, subject to configuration. In this collaboration module, we inspect the reported state rather than authoring CI job definitions or pipeline graphs. An approved MR may still be blocked by a required failed pipeline. A green pipeline, meanwhile, proves only the reported checks completed successfully, not that every pricing rule is correct. Ask which commit was evaluated and which requirement actually applies. The screen is a state model, not an executed CI run.

**Purpose:**

Teach merge-gate evidence while keeping pipeline design in its own curriculum.

## Blocked Merges: Push/Merge Permissions, Approvals, and Check States

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:11–07:24`

**Visual:**

Keep the previous scene’s evidence as a smaller left panel, highlight "pipeline status is evidence, not the CI implementation lesson", then reveal the next question on the right with a scope-change arrow.

**Script:**

Multiple policies may apply; we need a reliable investigation order when the MR merge widget remains blocked.

**Purpose:**

Bridge the earlier observation about pipeline status is evidence, not the CI implementation lesson to the next decision about read the blocker before changing policy without resetting the case.

### Scene 1 — Blocked Merges: Push/Merge Permissions, Approvals, and Check States

**Time:** `07:24–08:22`

**Visual:**

Build a Ready → target/actor → merge permission → approvals/owners → discussions/checks → matching branch rules decision tree. Leave all live-result fields unknown until inspected.

**Script:**

If Merge is unavailable, disabling protection is not diagnosis. First confirm that MR number 57 is Ready and targets the intended branch. Next inspect the actor's merge permission, counted approvals and Code Owners, unresolved discussions, and check results for the current commit. Finally examine every matching protected-branch pattern, including permissive wildcards. Whether Request changes or particular approval rules enforce a gate depends on tier and offering. Record GitLab's actual blocker before requesting the smallest authorized policy correction.

**Purpose:**

Provide an actionable troubleshooting sequence that respects real hosted state and tier limits.
