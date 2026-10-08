---
video:
  url: ""
---

# Code Review and Pull Request Decisions

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

## Review Context: Descriptions, Diffs, Discussions, and Check Results

<!-- VIDEO_SECTION -->

### Scene 1 — Review Context: Descriptions, Diffs, Discussions, and Check Results

**Time:** `00:00–00:54`

**Visual:**

Open an illustrative PR #57: Description shows a reproducible invoice input, Files changed highlights rounding lines, Checks remains marked 'no live result shown'. Move a magnifier across these surfaces.

**Script:**

Binh should not begin by scrolling diff lines without a question. First establish why PR number 57 exists, which invoice case failed, and what behavior is expected. Then compare that description with Files changed, commits, and discussion. Checks may carry results reported by other validation systems, but passing a check would not by itself establish business correctness. We have not run a test in this storyboard, so the check pane is intentionally not shown as green.

**Purpose:**

Guide purposeful PR review and prevent fake validation evidence.

## Comments, Approvals, and Requests for Changes

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:54–01:05`

**Visual:**

Freeze the highlighted rounding diff beside its description; replace the magnifier with three Review decision cards: Comment, Approve, Request changes.

**Script:**

Inspecting a diff supplies context. A reviewer still needs to communicate the kind of decision being made.

**Purpose:**

Move from collecting PR evidence to recording a review decision with its correct merge implications.

### Scene 1 — Comments, Approvals, and Requests for Changes

**Time:** `01:05–01:58`

**Visual:**

Place Comment, Approve, and Request changes cards beside the same changed line. Mark Comment 'not approval'; show a blocking effect only when matching required-review rules are active.

**Script:**

Binh may ask about a negative invoice amount without declaring the change acceptable. GitHub separates a submitted Comment review, Approve, and Request changes. A line comment is not automatically a blocking request for changes. If the base branch has required-review policy, eligible submitted reviews and their current state may affect whether merging is allowed. Count real review decisions and inspect the effective rules; the number of comment bubbles cannot stand in for approval.

**Purpose:**

Make the distinction among feedback, approval, and configured blocking concrete.

## Responding to Feedback, Updating Changes, and Requesting Re-Review

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:58–02:09`

**Visual:**

Pin Binh's Request changes card on the negative-input comment, then add an updated-commit marker and a second review slot.

**Script:**

A request for changes should produce inspectable new evidence, not just a message saying 'fixed'.

**Purpose:**

Show why submitted feedback creates a revision and renewed inspection loop.

### Scene 1 — Responding to Feedback, Updating Changes, and Requesting Re-Review

**Time:** `02:09–03:01`

**Visual:**

Animate Binh's negative-input question → An's changed example → updated Files changed → renewed inspection. Stamp 'yesterday's review ≠ proof about new commits'.

**Script:**

An should separate a functional defect, missing test case, design concern, or optional suggestion before revising. Updating the head changes the PR's diff. An then explains what was corrected and requests another look rather than assuming every reviewer noticed the new commits. If branch rules dismiss stale approvals after changes, the review state may need to be renewed. Even without that setting, yesterday's approval cannot prove what today's updated source does.

**Purpose:**

Demonstrate an accountable re-review cycle and the effect of configured stale-approval policies.

## Review Requests, Teams, and CODEOWNERS Routing

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:01–03:12`

**Visual:**

Carry the updated Files changed path into a CODEOWNERS lookup; branch the path toward billing and UI reviewer teams.

**Script:**

Who should inspect the edit? Matching expertise to changed paths is a separate collaboration problem.

**Purpose:**

Shift from whether the correction was addressed to who is eligible and appropriate to inspect it.

### Scene 1 — Review Requests, Teams, and CODEOWNERS Routing

**Time:** `03:12–04:06`

**Visual:**

Show CODEOWNERS mapping billing/** to @orchid/billing-reviewers and ui/** to @orchid/ui-reviewers. Highlight billing/Invoice.java and a permission eligibility check before a request arrow.

**Script:**

GitHub can request reviews from individuals or teams. CODEOWNERS lets a repository map changed paths to responsible reviewers, making that routing more repeatable. But naming someone in the file does not grant repository access or eligible review rights. For billing/Invoice.java, inspect which pattern matches and whether that team has the necessary permissions. Automatic requests help reach the right people; they do not guarantee those people have read the diff or approved its business behavior.

**Purpose:**

Explain conditional CODEOWNERS routing and distinguish ownership metadata from access control.

## Base-Branch CODEOWNERS Requests on Ready PRs vs Required Approval

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:06–04:17`

**Visual:**

Zoom in on a billing CODEOWNERS match, then split into 'review requested' and 'review required by rule' indicators sourced from base main.

**Script:**

An automatic review request and a required approval are often confused. Let's put the two mechanisms on different sides.

**Purpose:**

Prevent automatic reviewer routing from being mistaken for mandatory approval.

### Scene 1 — Base-Branch CODEOWNERS Requests on Ready PRs vs Required Approval

**Time:** `04:17–05:14`

**Visual:**

Left: CODEOWNERS read from base main, request triggered when Draft becomes Ready. Right: optional branch protection/ruleset requiring code-owner approval. Add 'request ≠ approval' across the middle.

**Script:**

When a PR becomes ready, applicable CODEOWNERS from the base branch can route a review request for the changed paths. That is a request, not automatically a merge gate. Only when branch protection or a ruleset requires code-owner approval does that eligibility become a required condition. Check the base branch file, matching pattern, owner permission, and applicable rules. A CODEOWNERS edit on the head branch does not define the base's current ownership rules, and a notification is not a submitted approval.

**Purpose:**

Preserve the critical base-branch and rule-dependent distinction for code owner approval.
