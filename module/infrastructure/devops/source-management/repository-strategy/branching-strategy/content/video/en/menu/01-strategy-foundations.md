---
video:
  url: ""
---

# Branching and Integration Strategy Foundations

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

## Branching Strategy: Purpose and Team Integration Responsibilities

<!-- VIDEO_SECTION -->

### Scene 1 — Case study and evidence

**Time:** `00:00–01:43`

**Visual:**

Split screen: a diverging Git commit graph and a team calendar marking review and release dates.

Use a throwaway Git repository for graphs; label any team policy/PR illustration instead of claiming a live hosted outcome.

**Script:**

Imagine two engineers creating changes in one repository. Git makes both branches cheap, but it doesn't decide when they should integrate, who reviews them, or whether a release waits. A branching strategy is the team's shared agreement about integration points, change size, and acceptance conditions. Look at the two arrows on the graph: without convergence, the shared build cannot include everything the team thinks has finished.

We will inspect branch lifetime and integration cadence, contrast Trunk-Based Development, GitHub Flow and Git Flow, then choose history, release/hotfix, review and mainline policies and measure their results. The payments team's rounding and refund work will remain our common scenario.

**Purpose:**

Define strategy as coordination rules supported by graph and cadence evidence.

## Git Branch Operations Versus Team Integration Policy

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:43–01:57`

**Visual:**

Hold the diverging strategy graph; reveal git branch -vv at left and a reviewer/merge policy card at right, labeling refs separately from permissions.

**Script:**

We have competing lines of work. What does Git reveal about their commits, and which integration conditions must the team add?

**Purpose:**

Convert the strategy definition into a visible comparison between Git mechanics and team rules.

### Scene 1 — Case study and evidence

**Time:** `01:57–03:40`

**Visual:**

Terminal shows `git branch -vv` and `git log --graph --oneline --all` beside a team policy requiring review before merge.

Use a throwaway Git repository for graphs; label any team policy/PR illustration instead of claiming a live hosted outcome.

**Script:**

In the terminal Git knows which commits each branch points to; branch creation and merge are mechanics. The team policy next to it says which checks precede mainline integration, when stale branches need attention, and who responds to failures. Git itself doesn't invent those agreements. Naming a branch trunk while integrating quarterly doesn't make a team trunk-based. Ask separately what Git permits and when the team has agreed to use that capability.

**Purpose:**

Separate Git capability from organizational policy evidence.

## Parallel Changes, Late Integration, and Conflict Risk

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:40–03:54`

**Visual:**

Keep the mechanics-versus-policy board, send An and Binh down competing feature tips, then stretch the timeline from one to twelve days around a shared config file.

**Script:**

Now that Git commands and review rules are separate, watch what happens when two changes drift apart before integration.

**Purpose:**

Connect missing integration discipline to the observable growth in divergent work.

### Scene 1 — Case study and evidence

**Time:** `03:54–05:37`

**Visual:**

Two graphs: feature/auth and feature/billing diverge from main; twelve commits later both touch config, marking merge-base and conflict.

Use a throwaway Git repository for graphs; label any team policy/PR illustration instead of claiming a live hosted outcome.

**Script:**

In the first graph, branches live for a day with a tiny change each. In the second they grow for weeks and both touch config; the merge-base lies far behind, so the conflict is harder to reason about. Compare `git log --graph` and the tip-to-base diffs in a disposable repo. Strategy doesn't eliminate every conflict. It reduces how long incompatible assumptions stay hidden and makes feedback possible while changes are still understandable.

**Purpose:**

Show why aging divergence increases coordination cost.

## Mainline, Work, and Release Branches: Roles and Boundaries

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:37–05:51`

**Visual:**

From the aging feature graph, highlight main, then introduce feature/checkout and release/1.4 cards with distinct owners and retirement criteria.

**Script:**

That conflict graph shows why branches need roles: which line integrates work, and which supports a release?

**Purpose:**

Differentiate neglected feature isolation from deliberately supported release maintenance.

### Scene 1 — Case study and evidence

**Time:** `05:51–07:34`

**Visual:**

Graph labels `main`, `feature/checkout`, `release/1.4`; each line has a responsible owner and a closure condition.

Use a throwaway Git repository for graphs; label any team policy/PR illustration instead of claiming a live hosted outcome.

**Script:**

Those three labels aren't mandatory branches for every team. Mainline is the shared integration point. A work branch, if used, holds changes not yet merged into main and should have an understood lifetime. A release branch is useful when a stable line is needed to prepare or patch a supported version. It is not a deployment environment, and a separate develop branch isn't universal—that belongs to a specific model.

**Purpose:**

Recognize branch roles without imposing one universal topology.

## Integrated Code vs Releasable Code

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:34–07:48`

**Visual:**

Retain main and release history; mark commit X Integrated, then reveal a release-checklist and unverified tag v1.4 beside a blank deployment field.

**Script:**

After identifying branch roles, compare a commit integrated into main with a revision verified for release.

**Purpose:**

Make accepted source, release readiness and actual delivery separate states.

### Scene 1 — Case study and evidence

**Time:** `07:48–09:31`

**Visual:**

Two-column evidence board: commit X on main versus a verified release tag v1.4 after validation.

Use a throwaway Git repository for graphs; label any team policy/PR illustration instead of claiming a live hosted outcome.

**Script:**

A merged PR proves that a change entered shared history; it doesn't prove the change shipped. Keep two separate markers on screen: commit X is integrated into main, while release v1.4 identifies a verified, approved revision. Continuous delivery benefits from a reliable mainline, but checks and feature exposure can remain separate controls. Never use the Merge button as evidence that production users already received the feature.

**Purpose:**

Do not equate integration with deployment or feature enablement.

## Team Strategy Decisions and Git, Platform, and CI Boundaries

<!-- VIDEO_SECTION -->

### Transition

**Time:** `09:31–09:45`

**Visual:**

Keep Integrated/Releasable/Deployed cards and align Git graph, hosted PR, CI report and recovery owner beneath their respective evidence columns.

**Script:**

We have separated integration from release readiness. Assign Git, PR review, CI and team decisions to their respective owners.

**Purpose:**

Synthesize which team and tool supplies proof for each stage of the source-to-release path.

### Scene 1 — Case study and evidence

**Time:** `09:45–11:28`

**Visual:**

Three distinct views: Git CLI graph, hosted PR review, CI check result, plus a decision-to-owner matrix.

Use a throwaway Git repository for graphs; label any team policy/PR illustration instead of claiming a live hosted outcome.

**Script:**

To close, assign each decision to its owner. Git controls commits, refs, and integration mechanics. Hosts such as GitHub or GitLab manage review records and configurable protections. CI reports checks; the team decides required quality, integration cadence, and who restores a failing mainline. These layers cooperate but aren't one invented local API. Next we'll measure what branch lifetime actually costs.

**Purpose:**

Synthesize responsibilities before examining integration cadence.
