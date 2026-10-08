---
video:
  url: ""
---

# Branch Protection and Merge Requirements

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

## Branch Protection Rules and Their Matching Branches

<!-- VIDEO_SECTION -->

### Scene 1 — Branch Protection Rules and Their Matching Branches

**Time:** `00:00–00:54`

**Visual:**

Illustrate branch protection patterns main and release/* with configurable PR, review, check, and push requirements. Overlay two matching patterns but highlight only the single rule selected for a branch.

**Script:**

Branch protection can require pull requests, reviews, reported checks, or restrict updates to branches matching its scope. These behaviors are configured and may depend on repository plan or policy. A key detail: GitHub applies only one branch protection rule at a time to a branch, even if several rule patterns appear to match. Do not mentally add every branch-protection pattern together. When main behaves unexpectedly, inspect which matching rule actually controls it before changing settings.

**Purpose:**

Teach the single-applicable-rule model and discourage incorrect stacking assumptions.

## Overlapping Rulesets Versus a Matching Branch Protection Rule

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:54–01:05`

**Visual:**

Leave main under one highlighted branch protection card, then fan out two separately enforced ruleset cards and one dashed Evaluate card.

**Script:**

A branch protection rule selects one match. Rulesets have a different model, so what changes when several apply?

**Purpose:**

Contrast the single selected branch-protection rule with cumulative enforced rulesets before asking what they require.

### Scene 1 — Overlapping Rulesets Versus a Matching Branch Protection Rule

**Time:** `01:05–02:00`

**Visual:**

Stack two enforcing rulesets over main while keeping one separate branch-protection card. Show an evaluate-only ruleset as a dashed observation layer, not a blocking barrier.

**Script:**

Rulesets bundle branch or repository rules with scope and enforcement status. Unlike the one-matching-rule behavior of branch protection, multiple rulesets can apply to the same branch at once. Enforced requirements may therefore accumulate. A ruleset in evaluation mode can report how a rule would behave without actually blocking the action as an enforced rule would. Plan, account, and organization policy still limit which features are available. Diagnose the active set instead of assuming every listed rule is enforcing.

**Purpose:**

Explain overlapping effective rulesets and the importance of enforcement state.

## Required Pull Requests, Review Counts, and Code Owner Approvals

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:00–02:11`

**Visual:**

Keep the enforcing ruleset stack above PR #57; reveal separate review-count and CODEOWNERS approval slots below.

**Script:**

Once rules apply, what exactly has to be true about the reviews on the pull request?

**Purpose:**

Translate active policy layers into concrete qualifying-review obligations.

### Scene 1 — Required Pull Requests, Review Counts, and Code Owner Approvals

**Time:** `02:11–03:05`

**Visual:**

Present PR #57 with configurable approval slots and a code-owner slot. One Approve card does not fill all slots. Show CODEOWNERS sourced from base main.

**Script:**

A protected target branch may require changes to arrive through PRs, receive enough eligible approvals, and obtain code-owner approval for matching files. These conditions exist only when configured. A single positive review may not meet the required count or owner requirement. CODEOWNERS on the base branch identifies eligible ownership, but the file alone is not a required-approval setting. Inspect the actual branch rule and the people who submitted qualifying reviews before calling PR number 57 merge-ready.

**Purpose:**

Prevent confusion of visible approval with meeting all configured review requirements.

## Required Status Checks and Pull Request Merge Eligibility

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:05–03:16`

**Visual:**

Preserve the approval slots but uncover a second lane for checks, with an empty required-check result.

**Script:**

Approvals might be sufficient, yet a protected branch can still require technical check evidence.

**Purpose:**

Explain why review readiness and technical check readiness must be evaluated independently.

### Scene 1 — Required Status Checks and Pull Request Merge Eligibility

**Time:** `03:16–04:11`

**Visual:**

Show illustrative Pending, Failed, and Success check cards. Mark one as Required only by configuration, with no fabricated test execution or green live result.

**Script:**

A status check is a result reported to GitHub by a validation source. If the target branch requires a particular check, that reported condition matters to merge eligibility. Pending, failure, and success mean different things; a missing result also needs diagnosis against the required name and source. Our screen is a diagram of possible states, not an executed test. Even a successful report only speaks for the checks that ran, not for every business rule in the application.

**Purpose:**

Clarify required versus optional status evidence and avoid overclaiming what passing checks prove.

## Push Restrictions, Rule Bypass, and Troubleshooting Blocked Merges

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:11–04:22`

**Visual:**

Mark the unresolved required check beside the actor/branch path; open a diagnostic fork for push restriction, merge condition, and bypass.

**Script:**

If merging is still blocked, removing every rule is not a diagnosis. Which layer actually denied the action?

**Purpose:**

Turn an apparent merge failure into a search for the exact controlling permission or requirement.

### Scene 1 — Push Restrictions, Rule Bypass, and Troubleshooting Blocked Merges

**Time:** `04:22–05:16`

**Visual:**

Construct an actor → branch permission → protection → rulesets → review/checks → bypass checklist. Highlight capturing the real error before proposing a change.

**Script:**

When An cannot push or merge, first identify the exact operation being denied. Direct-push restrictions and PR merge requirements are different layers. An Admin or eligible bypass actor may have an exception under certain configuration, yet another ruleset can still apply. Record the actor, target, displayed reason, and effective rules. Then choose the smallest justified correction. Neither granting Admin access nor turning off protection is a safe default response to a stalled pull request.

**Purpose:**

Teach evidence-based diagnosis of push/merge restrictions and conditional bypass.
