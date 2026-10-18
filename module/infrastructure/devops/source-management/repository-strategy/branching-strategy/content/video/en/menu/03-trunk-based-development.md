---
video:
  url: ""
---

# Trunk-Based Development and Frequent Integration

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

## Trunk-Based Development: Frequent Integration to Prevent Branch Backlogs

<!-- VIDEO_SECTION -->

### Scene 1 — Inspect an integrated trunk

**Time:** `00:00–01:54`

**Visual:**

Mainline graph with daily commits; `feature/tax` appears and merges within a day, compared with a three-week work branch.

Use a disposable Git repository and clearly labeled PR/check illustrations; never claim fabricated live CI results.

**Script:**

Trunk-Based Development starts with one shared integration point. Contributors bring changes back frequently rather than maintaining parallel development lines for weeks. That branch can be named main—renaming a branch isn't enough to adopt the model. Track a week in `git log --graph`: tiny review branches return promptly, with no backlog of long-lived feature lines. This is a collaboration discipline, not a special Git command or an approach restricted to monorepos.

**Purpose:**

Explain TBD using observed cadence, not branch labels.

## One Trunk and the Expectation of a Reliable Mainline

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:54–02:07`

**Visual:**

Keep the single-trunk daily graph and mark one failing post-merge check; attach an owner-driven recovery timeline before the next contribution.

**Script:**

Frequent commits converge on one trunk. Watch the checks and recovery duties that keep the shared line reliable.

**Purpose:**

Connect frequent convergence to the obligation to restore a reliable mainline quickly.

### Scene 1 — Inspect an integrated trunk

**Time:** `02:07–04:01`

**Visual:**

Single trunk graph with frequent test results; red/yellow/green verification and a named team owner for recovery.

Use a disposable Git repository and clearly labeled PR/check illustrations; never claim fabricated live CI results.

**Script:**

Many contributors rely on the same trunk, so frequent integration requires prompt validation and recovery. Here a commit turns the example build red; the integrating developer and agreed owner act quickly rather than waiting for release day. Hosted branch protection and checks can help enforce this, but maintaining mainline health is a team commitment. A green build isn't absolute proof of business correctness, yet rapid feedback makes regression investigation smaller.

**Purpose:**

Relate mainline reliability to actionable failure ownership.

## Direct-to-Trunk vs Very Short-Lived Review Branches

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:01–04:14`

**Visual:**

Split the recovery view into a tested direct commit lane and a one-day PR lane; add a two-week review branch as a cautionary contrast.

**Script:**

With a reliable trunk defined, compare direct commits against short-lived review branches returning to that same line.

**Purpose:**

Distinguish two legitimate trunk integration paths from long-lived feature isolation.

### Scene 1 — Inspect an integrated trunk

**Time:** `04:14–06:08`

**Visual:**

Two timelines: validated direct commits versus one-day review PRs returning to the same trunk; two-week PR marked as anti-pattern.

Use a disposable Git repository and clearly labeled PR/check illustrations; never claim fabricated live CI results.

**Script:**

A small team may validate and commit directly, while another briefly uses pull-request branches for review. Both can be trunk-based if changes converge quickly and review branches don't become shared long-running development streams. Annotate actual open and merge dates instead of assuming that the presence of PRs defines the strategy. Check branch lifetime and how far each change diverges from trunk.

**Purpose:**

Avoid equating TBD with no PRs or a particular hosting workflow.

## Small Changes, Early Validation, and Frequent Integration

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:08–06:21`

**Visual:**

Retain direct/PR paths, then sequence a refund story through compatible contract, small behavior and cleanup increments with evidence at each merge.

**Script:**

Both routes converge quickly. Now split one refund feature into independently validated increments.

**Purpose:**

Turn a short-branch rule into concrete, independently safe increments of a larger feature.

### Scene 1 — Inspect an integrated trunk

**Time:** `06:21–08:15`

**Visual:**

A user story split into three tested increments, mainline log showing each independently integrated change.

Use a disposable Git repository and clearly labeled PR/check illustrations; never claim fabricated live CI results.

**Script:**

Split a refund feature into three independently safe steps: introduce compatible state handling, implement the new behavior, then remove the old path after validation. Each slice gets checks and reaches trunk; we needn't wait until the entire user journey is complete. Frequent integration is about small reviewable, recoverable work, not simply triggering a pipeline more often. If a slice cannot be integrated safely on its own, redesign it before merging.

**Purpose:**

Show how verified increments enable frequent integration.

## Feature Flags and Optional Release Branches in Trunk-Based Workflows

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:15–08:28`

**Visual:**

Keep the three accepted refund increments, add a visible OFF/ON flag, then split into a trunk tag and a late-cut release branch.

**Script:**

Some safe increments are not ready for users. Contrast controlled feature flags with optional release preparation.

**Purpose:**

Connect unfinished-feature exposure to the optional release paths used by trunk-first teams.

### Scene 1 — Inspect an integrated trunk

**Time:** `08:28–10:22`

**Visual:**

Feature flag OFF/ON diagram, trunk release tag, optional release branch cut near release; highlight cherry-pick trunk → release.

Use a disposable Git repository and clearly labeled PR/check illustrations; never claim fabricated live CI results.

**Script:**

A feature flag keeps incomplete behavior hidden even when code is integrated into trunk. A continuously delivering team may release from a mainline tag and roll forward after failures. If a stable version needs maintenance, cut a release branch late and apply selected fixes. Under trunk-first practice, reproduce and test a bug on trunk before cherry-picking its fix onto the release line, then validate that line as well. Avoid turning release branches into additional feature-development trunks.

**Purpose:**

Distinguish feature exposure, release tags, and optional maintenance branches.

## Evidence of Real Trunk-Based Practices and Common Anti-Patterns

<!-- VIDEO_SECTION -->

### Transition

**Time:** `10:22–10:35`

**Visual:**

Retain the feature flag and both release paths; animate a fix starting on trunk and cherry-picking to release, then reveal a branch-age/merge-frequency audit.

**Script:**

With flags and release paths visible, inspect branch age, integration rate and hotfix direction for evidence of real TBD.

**Purpose:**

Turn practice examples into observable tests of whether the team actually follows trunk-based development.

### Scene 1 — Inspect an integrated trunk

**Time:** `10:35–12:29`

**Visual:**

Diagnosis board: branch-age histogram, main merge frequency, active feature branches, release branch state, and hotfix direction arrows.

Use a disposable Git repository and clearly labeled PR/check illustrations; never claim fabricated live CI results.

**Script:**

Close with observable questions: is there one shared trunk; how old are feature branches; how often do changes integrate; how quickly do checks report; can unfinished behavior remain dark; and where are production fixes made first? Renaming develop to main while merging in batches is not genuine TBD. Fixing only a release branch risks reintroducing the bug in the next version. Let graphs, timestamps, and outcomes drive process improvements rather than slogans.

**Purpose:**

Provide measurable TBD practices and anti-patterns.
