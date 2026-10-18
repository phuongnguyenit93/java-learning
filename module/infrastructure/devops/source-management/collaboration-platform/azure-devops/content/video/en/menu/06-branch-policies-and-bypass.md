---
video:
  url: ""
---

# Branch Policies, Merge Requirements, and Bypass Permissions

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

## Purpose of Protecting Important PR Target Branches

<!-- VIDEO_SECTION -->

### Scene 1 — Purpose of Protecting Important PR Target Branches

**Time:** `00:00–00:53`

**Visual:**

Azure Repos Branches > main > Branch policies screenshot; main annotated as shared baseline, feature branch as source.

Use authorized sandbox views or attributed Microsoft Learn; read settings only, never execute a production bypass or teach pipeline configuration.

**Script:**

Why protect main? It's often a shared stable baseline, so unsafe integration affects many contributors. Open main's Branch policies to inspect requirements applied before PR completion. These are hosted governance controls, not Git commands. Git owns the commit graph and conflicts; Azure Repos evaluates reviews and checks against the target branch. First verify that the displayed policy belongs to the intended target.

**Purpose:**

Explain why target branch governance exists.

## Branch Permissions Versus Branch Policies

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:53–01:05`

**Visual:**

Move main's Branch policies card aside to reveal its Branch security permissions grid.

**Script:**

Protection raises two questions: who can act on main, and which PR conditions must pass?

**Purpose:**

Separate an actor's right to act from requirements imposed on the target PR.

### Scene 1 — Branch Permissions Versus Branch Policies

**Time:** `01:05–01:56`

**Visual:**

Side-by-side main Branch security (Contribute, Edit policies, bypass) and Branch policies (minimum reviewers, validation).

Use authorized sandbox views or attributed Microsoft Learn; read settings only, never execute a production bypass or teach pipeline configuration.

**Script:**

The Security grid answers who may Contribute, Force push, Edit policies, or bypass. Branch policies answers which review or validation conditions a PR must meet before completion. Permission to push a feature branch doesn't imply authority to update protected main or disable checks. Comparing both views explains why granting more access isn't the fix for a failing required policy.

**Purpose:**

Distinguish actor authorization from PR completion requirements.

## Required Merge Policies Versus Optional Checks

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:56–02:06`

**Visual:**

Keep both settings panels visible; highlight Blocking/Required and Optional beside two policy outcomes.

**Script:**

Among configured policies, which checks block completion and which only advise?

**Purpose:**

Make the difference between enforced and advisory checks observable.

### Scene 1 — Required Merge Policies Versus Optional Checks

**Time:** `02:06–02:53`

**Visual:**

Branch policies list annotated Required/Blocking status versus Optional; PR Policies summary green/yellow/red.

Use authorized sandbox views or attributed Microsoft Learn; read settings only, never execute a production bypass or teach pipeline configuration.

**Script:**

Enabled doesn't always mean blocking. Inspect target-branch policy settings for required versus optional behavior, then read PR Policies to identify which failed condition prevents completion. Optional status can inform a reviewer without acting as the same gate. A red icon needs context: the configured policy, its scope, and whether it is required.

**Purpose:**

Read blocking versus advisory status from actual policy configuration.

## Minimum Reviewer Counts and Self-Approval Rules

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:53–03:05`

**Visual:**

Zoom from the blocking label into Minimum reviewers and its self-approval and vote-reset switches.

**Script:**

Even a required review count depends on whose approval is eligible and whether votes reset.

**Purpose:**

Show that approval counts depend on reviewer eligibility and branch configuration.

### Scene 1 — Minimum Reviewer Counts and Self-Approval Rules

**Time:** `03:05–03:58`

**Visual:**

Minimum reviewers policy toggles self-approval, most recent pusher, vote reset; current PR reviewer count panel.

Use authorized sandbox views or attributed Microsoft Learn; read settings only, never execute a production bypass or teach pipeline configuration.

**Script:**

A policy requiring two approvals doesn't mean two reviewer names are enough. Inspect whether requestor self-approval counts, whether the most recent pusher may count, and how votes are reset after source updates. An Approve vote can be present without satisfying the configured minimum. Read the actual current policy evaluation rather than counting profile pictures; don't edit real policy settings for a demo.

**Purpose:**

Demonstrate why two visible approvals may not meet policy.

## Required Reviewers and Comment Resolution Requirements

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:58–04:10`

**Visual:**

Retain the reviewer-count panel and reveal the path-based Required reviewers rule beside an active thread.

**Script:**

A numeric minimum may be met while a required reviewer or unresolved thread still blocks.

**Purpose:**

Connect minimum counts with independent required reviewers and comment-resolution requirements.

### Scene 1 — Required Reviewers and Comment Resolution Requirements

**Time:** `04:10–05:00`

**Visual:**

Required reviewers rule with path filter and Check for comment resolution; PR active discussion thread highlighted.

Use authorized sandbox views or attributed Microsoft Learn; read settings only, never execute a production bypass or teach pipeline configuration.

**Script:**

A sensitive path can trigger required reviewers under policy, so substituting optional reviewers isn't an equivalent fix. A blocking Check for comment resolution may prevent completion while an active thread remains. Compare configured rules with the PR's thread state. Resolving a conversation doesn't prove the code passed tests, and reviewer count alone doesn't close technical feedback.

**Purpose:**

Show path-based required reviewers and comment-resolution gating.

## Build Validation, Status Checks, and the CI/CD Boundary

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:00–05:10`

**Visual:**

Hold the unresolved-thread badge next to Failed build validation and Pending status check cards.

**Script:**

Resolved comments do not substitute for required build or status-check results.

**Purpose:**

Show that finished discussion does not replace required technical validation.

### Scene 1 — Build Validation, Status Checks, and the CI/CD Boundary

**Time:** `05:10–06:04`

**Visual:**

PR Policies tab shows Build validation Failed and external Status check Pending; handoff banner Azure Pipelines owner.

Use authorized sandbox views or attributed Microsoft Learn; read settings only, never execute a production bypass or teach pipeline configuration.

**Script:**

Build validation uses build/test results required by the target policy, while status checks may come from external services. If a check is Failed or Pending, inspect its name and message on the PR. This lesson interprets the gate; it doesn't teach pipeline YAML or CI job setup. Hand a build failure to the pipeline owner with the PR ID and failing check name.

**Purpose:**

Read validation/check evidence without implementing pipelines.

## Permitted Merge Types During PR Completion

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:04–06:14`

**Visual:**

Clear the check cards and reveal Basic, Squash and Rebase options beside Limit merge types.

**Script:**

Once requirements pass, the branch may still restrict the permitted merge strategy.

**Purpose:**

Move from meeting readiness conditions to choosing a permitted Git integration shape.

### Scene 1 — Permitted Merge Types During PR Completion

**Time:** `06:14–07:04`

**Visual:**

PR completion options shows basic merge, squash, rebase variants; Branch policies Limit merge types screenshot.

Use authorized sandbox views or attributed Microsoft Learn; read settings only, never execute a production bypass or teach pipeline configuration.

**Script:**

A target policy may restrict integration methods. Basic merge can preserve a merge commit, squash records one consolidated target commit, and rebase variants replay commits, sometimes with an additional merge commit. These produce different Git histories. Draw simplified before/after graphs, then inspect which choices the actual main policy allows rather than assuming every button is always visible.

**Purpose:**

Relate configured merge strategies to observable Git history.

## Bypass Policies When Completing PRs Versus When Pushing

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:04–07:15`

**Visual:**

Place the allowed merge strategy beside two different bypass permissions: Complete PR and Push.

**Script:**

Choosing a merge strategy isn't permission to bypass policies during PR completion or push.

**Purpose:**

Prevent one exceptional authority from being treated as permission to bypass all paths.

### Scene 1 — Bypass Policies When Completing PRs Versus When Pushing

**Time:** `07:15–08:11`

**Visual:**

Two permission rows: Bypass policies when completing pull requests vs Bypass policies when pushing; PR Override option contrasted with direct push.

Use authorized sandbox views or attributed Microsoft Learn; read settings only, never execute a production bypass or teach pipeline configuration.

**Script:**

Show two distinct permissions: Bypass policies when completing pull requests allows an authorized person to deliberately override requirements during PR completion. Bypass policies when pushing affects otherwise permitted direct pushes to protected branches. Push bypass isn't a reviewer vote and doesn't make checks pass. Project Administrator membership isn't a blanket assumption of bypass rights. Inspect the configuration; never bypass a live organization's safeguards for a demonstration.

**Purpose:**

Separate PR-completion bypass from push bypass, restrict scope.

## Diagnosing Unmet Policies, Checks, and Bypass Permissions

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:11–08:21`

**Visual:**

Dim the bypass controls and open an ordered diagnostic tree with actor, votes, policies and conflicts.

**Script:**

If Complete remains unavailable, inspect the denied action and effective rule first.

**Purpose:**

Close the policy chapter with evidence-driven troubleshooting rather than an override.

### Scene 1 — Diagnosing Unmet Policies, Checks, and Bypass Permissions

**Time:** `08:21–09:17`

**Visual:**

Decision tree: target branch, PR Active/Draft, votes, unresolved threads, checks, conflicts, effective complete rights; evidence row for each.

Use authorized sandbox views or attributed Microsoft Learn; read settings only, never execute a production bypass or teach pipeline configuration.

**Script:**

Finish with a diagnosis tree: is the PR Draft or Active, is main the intended target, are counted and required votes satisfied, are threads resolved, do build/status or work-item policies pass, are there conflicts, and does the actor have effective completion rights? Record the evidence for each branch before acting. Another approval doesn't fix a Git conflict, and bypass isn't the normal solution to failing tests.

**Purpose:**

Provide an ordered, non-bypass troubleshooting workflow.
