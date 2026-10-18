---
video:
  url: ""
---

# GitHub Issues and Releases

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

## GitHub Issues as Work Items and Discussion Records

<!-- VIDEO_SECTION -->

### Scene 1 — GitHub Issues as Work Items and Discussion Records

**Time:** `00:00–00:54`

**Visual:**

Present illustrative Issue #42, 'Negative-amount rounding bug', with input -1 and expected versus observed behavior. Place a separate commit card outside the Issue boundary.

**Script:**

An Issue records a need or defect, not an implemented correction. Our Issue number 42 describes unexpected rounding on a negative invoice amount and gives a reproducible input. People can discuss the scope and expected behavior there. Creating that record does not update main or run a fix. A work branch and PR will provide different evidence later. Ask whether another maintainer could reproduce the problem from the Issue alone before considering it ready for implementation.

**Purpose:**

Start work tracking with a reproducible problem rather than pretending an Issue is source code.

## Issue Assignees, Labels, and Milestones for Work Organization

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:54–01:05`

**Visual:**

Keep the Issue #42 defect description at left; place empty Assignee, Label, and Milestone chips to the right before filling them.

**Script:**

A well-written Issue must still be organized among other work. Which fields help without proving completion?

**Purpose:**

Move from recording a problem to organizing work without presenting planning fields as completion evidence.

### Scene 1 — Issue Assignees, Labels, and Milestones for Work Organization

**Time:** `01:05–01:58`

**Visual:**

Attach Assignee: An, Label: bug, and Milestone: candidate v1.4.0 to Issue #42. Caption them owner, classification, planning group—not 'fix completed'.

**Script:**

An assignee indicates who is tracking the work. A label helps categorize or filter it, and a milestone groups issues toward a target. None of those fields proves that the defect has been fixed. Assigning An and a candidate v1.4.0 milestone supports planning, but it does not show a merged PR or a published Release. Treat these fields as coordination data, then inspect the change and acceptance records for evidence of actual delivery.

**Purpose:**

Avoid confusing planning metadata with implementation or release outcomes.

## Connecting Issues to Pull Requests and Change Outcomes

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:58–02:09`

**Visual:**

Hold Issue #42 with its planning chips, then bring in unmerged PR #57 connected by a thin, conditional link.

**Script:**

Issue #42 states the need while PR #57 proposes code. How do we connect them without conflating their states?

**Purpose:**

Explain why traceability links a work item to a proposed fix without proving resolution.

### Scene 1 — Connecting Issues to Pull Requests and Change Outcomes

**Time:** `02:09–03:05`

**Visual:**

Connect Issue #42 and PR #57 with a link. Show a sample 'Fixes #42' phrase in the PR description, then annotate the default-branch condition; keep Issue Closed empty until verified.

**Script:**

Linking lets reviewers follow a defect to the proposed correction. GitHub supports manual associations and closing keywords such as Fixes in a PR description. Automatic closure has default-branch conditions: a PR targeting some other branch should not be assumed to close the Issue as soon as it merges. Our illustration displays the link but does not manufacture a Closed outcome. A relationship provides traceability; it does not replace review or demonstrate that the repair has reached a released product.

**Purpose:**

Demonstrate conditional Issue-closing behavior and evidence traceability.

## Git Tags Versus GitHub Releases

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:05–03:16`

**Visual:**

Follow PR #57's possible merge arrow toward a commit; pin a Git tag there and show a separate blank Release card.

**Script:**

After a change enters shared source, what does the project publish for users and maintainers?

**Purpose:**

Separate accepted source changes from the additional step of naming and publishing a version.

### Scene 1 — Git Tags Versus GitHub Releases

**Time:** `03:16–04:11`

**Visual:**

Show Git tag v1.4.0 pointing at a commit alongside a GitHub Release card with title, notes, draft/prerelease state, and assets. Leave deployment on another timeline.

**Script:**

A Git tag is a named reference in Git history and often marks a version point. A GitHub Release is a publication record associated with a tag and may contain a title, notes, and downloadable assets. Creating a tag does not automatically publish a Release, and a Release is not proof that every environment has been deployed. Our hypothetical rounding fix could be announced in v1.4.0, but the release action and actual delivery evidence have their own lifecycles.

**Purpose:**

Show the distinct roles of tag, Release, and downstream deployment.

## Release Notes, Downloadable Assets, and Versioning Boundaries

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:11–04:22`

**Visual:**

Leave the tag and Release side by side; expand the Release card into notes, uploaded assets, and generated source archives.

**Script:**

A Release record exists; what information would make it useful to someone deciding whether to adopt the version?

**Purpose:**

Move from distinguishing release objects to judging which publication details users actually need.

### Scene 1 — Release Notes, Downloadable Assets, and Versioning Boundaries

**Time:** `04:22–05:15`

**Visual:**

Mock a v1.4.0 release note with Fixed negative rounding, Compatibility, and Documentation. Place attached assets separately from GitHub-generated source archives; label it a draft storyboard.

**Script:**

Release notes should tell users which behavior changed, what compatibility concerns exist, and where guidance can be found. Uploaded release assets are files maintainers attach; GitHub-generated source archives are a different artifact. Automated notes may help draft the text, but maintainers still need to verify its accuracy. Our v1.4.0 card is an example structure, not evidence that a Release has actually been published. Release numbering and the build/deployment process remain separate responsibilities.

**Purpose:**

Teach useful publication evidence without inventing artifacts or CI delivery.
