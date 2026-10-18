---
video:
  url: ""
---

# Branch Lifetime and Integration Cadence

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

## Short-Lived vs Long-Lived Branches: Purpose and Cost

<!-- VIDEO_SECTION -->

### Scene 1 — Read the integration cadence

**Time:** `00:00–01:48`

**Visual:**

Compare graph A, a one-day three-commit feature, with B, a two-week feature far behind main, showing days since merge-base.

Use an isolated repository and explicitly illustrative PR timelines, not claims about a user's live GitHub data.

**Script:**

Compare two checkout changes. Branch A lives one day and its merge-base remains close to main. Branch B lives two weeks while several teammates change the same area; integration now requires revisiting older assumptions. Long branches aren't inherently wrong—a maintenance branch has a different purpose—but a long-isolated feature normally carries higher compatibility risk. Measure lifetime and divergence instead of judging by branch names.

**Purpose:**

Distinguish aging work branches from intentional maintenance lines.

## Integration Cadence, Change Size, and Early Feedback

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:48–02:02`

**Visual:**

Turn the one-day/two-week graphs into a Monday–Friday timeline comparing three small PRs with a large Friday batch and reviewer wait lanes.

**Script:**

Compare a one-day branch with a two-week branch. How would smaller changes and earlier integration improve feedback?

**Purpose:**

Relate branch age to change batch size and feedback timing, not just commit count.

### Scene 1 — Read the integration cadence

**Time:** `02:02–03:50`

**Visual:**

Monday-to-Friday timeline comparing a 900-line Friday PR with five small daily PRs, including review turnaround and mainline graph.

Use an isolated repository and explicitly illustrative PR timelines, not claims about a user's live GitHub data.

**Script:**

A 900-line Friday pull request bundles many decisions into one review. Split that work into safe vertical increments and mainline receives feedback throughout the week; a contract mismatch appears while there is still little code to untangle. Measure diff size, review wait time, and intervals between merges. Frequent integration helps only when each increment is safe—it isn't a license to rush broken code into the shared branch.

**Purpose:**

Relate change size and review latency to integration safety.

## Branch Divergence, Merge Conflicts, and Batched Integration: Evidence

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:50–04:04`

**Visual:**

Zoom from PR timing into main/feature diverging at merge-base C; place git log --graph beside the competing config diffs.

**Script:**

Frequent integration helps, but inspect merge-bases and shared-file changes to see where conflict cost actually grows.

**Purpose:**

Translate delayed integration into inspectable ancestry and changed-content evidence.

### Scene 1 — Read the integration cadence

**Time:** `04:04–05:52`

**Visual:**

Terminal evidence: `git log --graph --oneline --all`, `git merge-base main feature/payment`, and competing config-file diffs.

Use an isolated repository and explicitly illustrative PR timelines, not claims about a user's live GitHub data.

**Script:**

Zoom into a graph where main and feature/payment split at commit C. Both sides changed the same config under different assumptions, so integration reports a conflict. Annotate the merge-base, touched files, and days of separation. Age alone doesn't predict an exact conflict probability, but graph and diff reveal where review will be difficult. Merging everything at sprint end also bunches reviewer workload and delays feedback.

**Purpose:**

Use merge-base and diff as actual divergence evidence.

## Small Changes and Early Review Without Delayed Integration

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:52–06:06`

**Visual:**

Split the competing contract diff into three scoped PR cards: compatible addition, consumer migration and cleanup; attach separate review markers.

**Script:**

The conflict is visible. What if the team divides one large requirement into safe, independently reviewable increments?

**Purpose:**

Show how independently safe slices reduce the amount of unintegrated contract work.

### Scene 1 — Read the integration cadence

**Time:** `06:06–07:54`

**Visual:**

Three illustrative PRs under one story: compatible contract change, implementation, then cleanup; each with its diff and review timestamp.

Use an isolated repository and explicitly illustrative PR timelines, not claims about a user's live GitHub data.

**Script:**

A user story doesn't have to map to one enormous branch or pull request. For a checkout API change, PR one establishes a compatible contract, PR two migrates the consumer, and PR three removes the old path after verification. All can link to the same work item while remaining independently reviewable. Show tests on each increment. If a slice cannot safely stand alone, refine the design instead of merging risky partial behavior just to meet a cadence target.

**Purpose:**

Show safe vertical slicing rather than a one-story-one-PR rule.

## Feature Flags: Controlled Exposure of Incomplete Work and Incremental Integration

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:54–08:08`

**Visual:**

Keep three integrated PR slices and overlay an OFF/ON flag gating a refund button; display tests for both exposure states.

**Script:**

Those increments may contain unfinished behavior. Use a disabled feature flag to separate integration from exposure.

**Purpose:**

Explain how integration cadence can remain short without prematurely exposing incomplete behavior.

### Scene 1 — Read the integration cadence

**Time:** `08:08–09:56`

**Visual:**

Conceptual `if (featureEnabled)` toggle with OFF/ON outcomes and contract-test table; main contains commits but UI button stays hidden.

Use an isolated repository and explicitly illustrative PR timelines, not claims about a user's live GitHub data.

**Script:**

A discount feature needs several days of work. Instead of isolating it for a month, the team can integrate compatible increments behind a disabled feature flag. In the same mainline revision, OFF preserves existing user behavior while controlled ON allows testing the new path. A flag doesn't replace tests, access control, or rollout planning; the team also needs an owner and removal criteria. We're discussing the branching decision, not implementing a particular flag service.

**Purpose:**

Explain flags as exposure controls, not automatic correctness.
