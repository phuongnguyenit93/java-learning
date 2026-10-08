---
video:
  url: ""
---

# Complete Pull Requests and Trace Work

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

## PR Completion Readiness and Unresolved Conflicts

<!-- VIDEO_SECTION -->

### Scene 1 — PR Completion Readiness and Unresolved Conflicts

**Time:** `00:00–00:56`

**Visual:**

PR overview Active, source/target correct, no conflicts, target Policies green, actor rights checklist; Complete action disabled variant.

Use an authorized sandbox or attributed Microsoft materials; don't complete, bypass or abandon production PRs for filming.

**Script:**

For a PR to complete, multiple conditions must align: valid source and target, no unresolved merge conflict, acceptable required votes and checks, and proper actor permissions on the target. Work through the checklist before pressing Complete. An Approve cannot resolve a conflict, and green policies do not grant missing rights to the person completing. If something is missing, record the PR as not ready—not 'basically merged'.

**Purpose:**

Identify independent completion preconditions.

## Complete Pull Requests Versus Set Auto-Complete

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:56–01:06`

**Visual:**

Hold the conflict and policy checklist while opening Complete and Set auto-complete side by side.

**Script:**

Contrast completing an eligible PR immediately with setting auto-complete for later.

**Purpose:**

Contrast an immediate authorized merge with deferred policy-gated completion.

### Scene 1 — Complete Pull Requests Versus Set Auto-Complete

**Time:** `01:06–01:56`

**Visual:**

PR More options Complete vs Set auto-complete; waiting build check then policy passes, auto-complete status; no live submit.

Use an authorized sandbox or attributed Microsoft materials; don't complete, bypass or abandon production PRs for filming.

**Script:**

Complete attempts integration immediately when all prerequisites pass. Set auto-complete records the intention to finish later when required conditions pass. It doesn't fix failing code, turn Failed into Passed, or grant authorization. Read the pending check and auto-complete state on a permitted demo PR; otherwise use an attributed official illustration. Automated completion still respects target-branch requirements.

**Purpose:**

Distinguish immediate integration from deferred policy-gated completion.

## Abandon, Reactivate, and Complete: Pull Request Lifecycle

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:56–02:06`

**Visual:**

Move the auto-complete pending banner into an Active → Abandoned → Reactivated → Completed timeline.

**Script:**

Auto-complete waits for conditions; abandoning or reactivating creates a different lifecycle.

**Purpose:**

Clarify that waiting, abandoning and completing are distinct PR lifecycle outcomes.

### Scene 1 — Abandon, Reactivate, and Complete: Pull Request Lifecycle

**Time:** `02:06–02:56`

**Visual:**

Timeline Active → Abandoned → Reactivated Active → Completed; source branch list remains after abandon screenshot.

Use an authorized sandbox or attributed Microsoft materials; don't complete, bypass or abandon production PRs for filming.

**Script:**

Active means a proposal remains open. Abandoned closes it without integration, Reactivate resumes that review, and Completed records a successful merge. Keep the source commits visible on the timeline: abandonment doesn't automatically delete the branch's history and isn't equivalent to revert. On reactivation, refresh the diff, votes, and policies because time and target history may have changed.

**Purpose:**

Explain abandoned/reactivated lifecycle without conflating Git history.

## PR Completion Options and Merge Traceability in Azure Repos

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:56–03:07`

**Visual:**

Keep Completed on the timeline and expand the target Git graph for merge and squash traces.

**Script:**

A Completed record is useful. Which merge strategy shaped the target branch history?

**Purpose:**

Tie the completion state to the actual history left by the chosen merge option.

### Scene 1 — PR Completion Options and Merge Traceability in Azure Repos

**Time:** `03:07–04:00`

**Visual:**

Completion dialog merge strategy choices, source branch deletion checkbox, completed PR timeline and target Git graph.

Use an authorized sandbox or attributed Microsoft materials; don't complete, bypass or abandon production PRs for filming.

**Script:**

During completion, Azure Repos may offer basic merge, squash, or rebase variants depending on policy. Deleting the source branch is a separate completion option, not proof of correct code. Compare the target graph after basic merge versus squash to see distinct histories while preserving PR and commit references for traceability. Don't present any single merge method as universally correct for every team.

**Purpose:**

Connect merge strategy and completion options to target history.

## Linking Work Items to PRs: Purpose and Work Context

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:00–04:12`

**Visual:**

Pin the completed change to Bug 104 on a separate Azure Boards work-item card.

**Script:**

The merge record explains how, while a linked work item explains why it was proposed.

**Purpose:**

Attach business context to the PR without claiming that the bug was resolved automatically.

### Scene 1 — Linking Work Items to PRs: Purpose and Work Context

**Time:** `04:12–05:04`

**Visual:**

PR Work Items tab showing linked illustrative Bug 104, small diagram Boards tracking record vs Git commit.

Use an authorized sandbox or attributed Microsoft materials; don't complete, bypass or abandon production PRs for filming.

**Script:**

Linking the timeout PR to illustrative Bug 104 provides business context for a change in Azure Boards. A work item tracks work; commits and pull requests capture code and review evidence. The association alone doesn't prove the bug was fixed—reviewers still need diffs and test results. We won't teach Boards workflows or issue-state management as part of this Repos module.

**Purpose:**

Distinguish traceability metadata from technical proof.

## Required Work-Item Linking Policy for PR Completion

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:04–05:15`

**Visual:**

Hold the Bug 104 link and reveal a required linked-work-item gate in target Branch policies.

**Script:**

Linking work items can also be a required branch policy, not just optional context.

**Purpose:**

Distinguish an optional traceability link from a configured completion requirement.

### Scene 1 — Required Work-Item Linking Policy for PR Completion

**Time:** `05:15–06:06`

**Visual:**

Target Branch policies Check for linked work items as blocking; compare PR with and without linked work item.

Use an authorized sandbox or attributed Microsoft materials; don't complete, bypass or abandon production PRs for filming.

**Script:**

The target branch can use Check for linked work items as a blocking policy. Without the required link, a PR can remain uncompletable despite approvals and passing builds. Don't attach an irrelevant work item merely to silence a gate; traceability should connect the change to meaningful tracked work. Read the policy result and identify who owns the missing context.

**Purpose:**

Show work-item-linking requirement as an independent merge gate.

## Completed PR Evidence and Related Work Item States

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:06–06:16`

**Visual:**

Retain Completed status but put the linked work item's state under a question mark.

**Script:**

A completed PR doesn't automatically prove its linked work item has closed.

**Purpose:**

Prevent a completed PR from being mistaken for an automatically closed work item.

### Scene 1 — Completed PR Evidence and Related Work Item States

**Time:** `06:16–07:06`

**Visual:**

Completed PR details with completion timestamp, reviewer history, target commit and Work Item linked state; avoid asserting automatic transition.

Use an authorized sandbox or attributed Microsoft materials; don't complete, bypass or abandon production PRs for filming.

**Script:**

A completed PR records its status, reviewers, completion time, and merge choice, while the target Git history reflects integration. A related work item may change state when configured completion options and workflow support it, but a Completed PR doesn't universally close the bug. Read the PR status and work-item state separately before reporting the business task finished.

**Purpose:**

Separate completed PR record from optional work-item state transition.

## Scenario: Verifying Permission to Complete a PR into Its Target Branch

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:06–07:16`

**Visual:**

Freeze a fully approved PR and replace the actor with Charlie; show his effective Complete permission.

**Script:**

Even with green policies, the person clicking Complete needs effective permission.

**Purpose:**

Make target-specific authorization the remaining question after policy checks appear ready.

### Scene 1 — Scenario: Verifying Permission to Complete a PR into Its Target Branch

**Time:** `07:16–08:10`

**Visual:**

Charlie identity effective target branch permissions view beside Bob Approved votes and all green policies; Complete unavailable.

Use an authorized sandbox or attributed Microsoft materials; don't complete, bypass or abandon production PRs for filming.

**Script:**

Imagine Alice opens the PR, Bob approves, and all checks pass, yet Charlie cannot complete it. That isn't necessarily a reviewer failure: inspect Charlie's effective permissions on the actual target branch and completion operation. Contribute to pull requests doesn't automatically grant branch Contribute or policy bypass. Use a clearly labeled hypothetical permission board; don't grant Charlie blanket admin rights to light up the button.

**Purpose:**

Prove completion authorization is actor/target specific.

## Diagnosing Permission, Review Vote, Policy, Check, and Conflict Blockers

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:10–08:21`

**Visual:**

Leave Charlie's denial visible and expand the blocker checklist: conflicts, votes, checks, link, rights.

**Script:**

To diagnose a failed Complete action, inspect conflicts, votes, checks, links and actor rights.

**Purpose:**

Turn a blocked Complete action into separate evidence checks and owners.

### Scene 1 — Diagnosing Permission, Review Vote, Policy, Check, and Conflict Blockers

**Time:** `08:21–09:17`

**Visual:**

Decision checklist Draft/Abandoned → source-target → conflicts → reviewer votes/threads → build/work-item policies → actor permission, arrows to owner.

Use an authorized sandbox or attributed Microsoft materials; don't complete, bypass or abandon production PRs for filming.

**Script:**

Assemble the diagnosis tree. A Draft or Abandoned PR needs a lifecycle action; conflicts return to Git mechanics; missing required votes go to reviewers; failed build validation belongs to the CI owner; missing work-item context requires a real link; insufficient rights require scoped admin review. Each failure has a different responsible actor. Bypass isn't a default shortcut and no single green badge proves all requirements are met.

**Purpose:**

Diagnose distinct blockers and route to responsible owner.
