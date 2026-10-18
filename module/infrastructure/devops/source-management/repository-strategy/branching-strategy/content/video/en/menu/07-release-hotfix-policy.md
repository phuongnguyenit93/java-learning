---
video:
  url: ""
---

# Release and Hotfix Branch Policy

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

## Release from Mainline vs a Dedicated Stabilization Branch

<!-- VIDEO_SECTION -->

### Scene 1 — Trace fixes across release lines

**Time:** `00:00–01:51`

**Visual:**

Two release paths: a `v2.0` tag on main versus a separate `release/2.0` branch cut from a verified commit.

All SHAs and versions are from a disposable repository or labeled diagram; use `git log --graph --oneline --all`, never fabricated deployment results.

**Script:**

A continuously deploying team may release a verified mainline commit, tag it, and roll forward on the next fix. Another team needing a stable 2.0 line while main keeps evolving may cut release/2.0 from a known-good commit. The choice follows release cadence and support obligations, not a universally correct branch label. Mark the tag, current HEAD, and branch point; a release line needn't always originate from the latest mainline tip.

**Purpose:**

Contrast tagged mainline release with a time-bounded stabilization line.

## Release Branch Lifecycle: Creation, Maintenance, and Retirement

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:51–02:05`

**Visual:**

Retain tag v2.0 from main beside a release/2.0 branch from a verified commit; reveal its two selected patches and retirement timeline.

**Script:**

We have contrasted release tags with stabilization branches. Follow one release branch from creation through retirement.

**Purpose:**

Bridge the release-point decision to clear maintenance and retirement criteria.

### Scene 1 — Trace fixes across release lines

**Time:** `02:05–03:56`

**Visual:**

Mainline continues feature commits while release/2.0 is cut late and accepts only two fixes, then retires after the last supported tag.

All SHAs and versions are from a disposable repository or labeled diagram; use `git log --graph --oneline --all`, never fabricated deployment results.

**Script:**

Cut release/2.0 when the version scope is understood, rather than maintaining an extra development line indefinitely. During stabilization it takes selected fixes, not an open stream of new features. Name who approves patches and when the branch may be retired after the supported deployment ends. Under Trunk-Based Development, this branch can be created late or even retroactively from an older release tag when a fix is needed. Indefinite release lines carry accumulating cost.

**Purpose:**

Show the creation point, controlled scope, and retirement of release branches.

## Hotfix Targets and Propagation Across Development Lines

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:56–04:10`

**Visual:**

Keep release/2.0 with selected fixes; contrast TBD trunk-first cherry-pick with Git Flow production-line hotfix propagation.

**Script:**

A stable release line now exists. Compare where urgent fixes begin in trunk-first practice and in classic Git Flow.

**Purpose:**

Separate distinct hotfix origins and propagation directions in the two models.

### Scene 1 — Trace fixes across release lines

**Time:** `04:10–06:01`

**Visual:**

Compare two distinct diagrams: TBD fix F on main then cherry-pick into release/2.0; classic Git Flow hotfix from production then merge to production and develop.

All SHAs and versions are from a disposable repository or labeled diagram; use `git log --graph --oneline --all`, never fabricated deployment results.

**Script:**

This is where the strategies must stay distinct. In trunk-first maintenance, reproduce and test the bug on trunk, then selectively cherry-pick the fix to the old release line and validate it there. In classic Git Flow, a hotfix normally branches from production history and is reintegrated into production and develop. Both require tracking which versions received the correction, but their preferred direction differs. Teaching that all hotfixes begin on release would misrepresent TBD.

**Purpose:**

Contrast trunk-first fixes with classic GitFlow using explicit graph arrows.

## Backport and Forward-Port: Propagating Fixes Across Required Code Lines

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:01–06:15`

**Visual:**

Retain both hotfix routes and open a main/release2.0/maintenance1.9 patch matrix with commit IDs, tests and one missing fix.

**Script:**

Both hotfix directions need evidence. Track which supported versions received each backport or forward-port.

**Purpose:**

Turn fix-propagation arrows into verifiable supported-version coverage.

### Scene 1 — Trace fixes across release lines

**Time:** `06:15–08:06`

**Visual:**

Matrix for main, release/2.0 and maintenance/1.9: fix SHA, new cherry-picked commit IDs, validation and approver.

All SHAs and versions are from a disposable repository or labeled diagram; use `git log --graph --oneline --all`, never fabricated deployment results.

**Script:**

Suppose the vulnerability affects 1.9 and 2.0. Commit F on main doesn't prove either older line is fixed. Track each required target, responsible backporter, resulting commit ID and version-specific test outcome. Cherry-pick can create a different SHA or conflict because APIs evolved; it won't automatically bring all dependencies. A classic maintenance-first workflow also needs an explicit forward-port owner. Completion requires both correct source lines and appropriately verified releases.

**Purpose:**

Use a version-by-fix matrix to prove propagation.

## Long-Lived Release Branch Costs and Multi-Version Support

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:06–08:20`

**Visual:**

Expand the patch matrix into four concurrent release histories, accumulating review/cherry-pick decisions beside each version's end-of-life date.

**Script:**

The fix matrix now spans several versions. What ongoing effort is required to maintain those branches?

**Purpose:**

Connect patch coverage to the long-term support and coordination cost of maintained lines.

### Scene 1 — Trace fixes across release lines

**Time:** `08:20–10:11`

**Visual:**

Graph with four concurrent release lines and rising cherry-picks/conflicts, beside a proposed reduced support window.

All SHAs and versions are from a disposable repository or labeled diagram; use `git log --graph --oneline --all`, never fabricated deployment results.

**Script:**

Imagine maintaining versions 1.7 through 2.0 simultaneously. A security fix may need four compatibility reviews, four validation runs, and four release decisions. The cost comes from divergent code and coordination—not simply from Git being slow with branch names. Strategy must now connect to product support windows, maintenance ownership, and end-of-life criteria. The conclusion isn't that release branches are always bad; it's that multi-version commitments need a budget and an explicit operating policy.

**Purpose:**

Measure support-line overhead rather than labeling release branches inherently wrong.
