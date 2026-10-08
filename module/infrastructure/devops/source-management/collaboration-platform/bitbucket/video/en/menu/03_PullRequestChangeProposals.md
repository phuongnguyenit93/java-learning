---
video:
  url: ""
---

# Proposing Changes with Pull Requests

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

## Pull Requests as Reviewable Change Proposals

<!-- VIDEO_SECTION -->

### Scene 1 — Pull Requests as Reviewable Change Proposals

**Time:** `00:00–01:30`

**Visual:**

Open illustrative `BILL-142 Update invoice tax` PR, with source `fix/BILL-142-tax`, target `main`, and a visible `Open` state.

**Script:**

An can create a Git commit for the tax change without asking anybody to review it. A Bitbucket Cloud pull request makes it a reviewable proposal: we can see where the change comes from, where it may go, what the diff contains and who has been invited. A PR is not a special kind of commit, and opening one is not integration. On our demonstration screen the proposal remains Open. That is evidence the team can discuss the change, not evidence that `main` or a deployed application already includes it.

**Purpose:**

Define the PR as a hosted proposal with verifiable scope and status.



## Source and Destination Repositories, Branches, and Diffs

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:30–01:42`

**Visual:**

Keep BILL-142 Open with source and destination; briefly target an old release branch to expose a surprising diff, then restore main.

**Script:**

We have a reviewable change proposal. Which source and destination does it actually compare, and what can the diff prove?

**Purpose:**

Make target selection a verifiable review prerequisite, not a formality.

### Scene 2 — Source and Destination Repositories, Branches, and Diffs

**Time:** `01:42–03:12`

**Visual:**

Show Source/Destination repository and branch fields with a red warning for a reversed target; move to `Files changed` and highlight 8%-to-10% tax diff.

**Script:**

Before Binh reviews a line, An must verify both the source and destination repository and branch. They can be in the same repository or different repositories when a fork contributes. Orchid proposes `invoice-api/fix/BILL-142-tax` into `invoice-api/main`. An accidental destination such as an old maintenance branch changes the meaning of the proposal, even if the code itself is correct. The Files changed view shows the patch in the context of those selected endpoints. Check that context before demonstrating the diff; use safe demo repositories and conceal sensitive paths.

**Purpose:**

Make correct source/target selection observable before drawing conclusions from the diff.



## Change Descriptions, Context, and Reviewers

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:12–03:24`

**Visual:**

Retain the 8%-to-10% tax diff; reveal Before/After, Invoice 100.05, Verification and Binh's reviewer card.

**Script:**

Now that the target is correct, what information does the reviewer need beyond the changed lines?

**Purpose:**

Move from syntactic diff inspection to business-context evidence that a reviewer can evaluate.

### Scene 3 — Change Descriptions, Context, and Reviewers

**Time:** `03:24–04:54`

**Visual:**

PR description panel with `Previous behavior`, `Intended behavior`, `Invoice 100.05`, `Verification`, and Jira `BILL-142`, next to a reviewer card for Binh.

**Script:**

A title like 'fix bug' makes Binh reconstruct the entire requirement. An should describe the old and desired tax behavior, the expected impact, an invoice example with fractional values, and how the change was checked. If the Jira integration is configured, the `BILL-142` key helps connect the business request to the PR. Binh is invited for his rounding expertise, not because a reviewer must be the repository administrator. Default reviewers may appear automatically under configured settings; being listed is only a request to inspect, not approval.

**Purpose:**

Show a useful PR narrative and the difference between invited and approving reviewers.



## Shared Changes Versus Team-Accepted Changes

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:54–05:06`

**Visual:**

Keep the readable PR description while comparing its Open status with unchanged main and a pending negative-invoice task.

**Script:**

The change is now visible and understandable. Has it actually reached accepted source?

**Purpose:**

Expose why a shared, well-described proposal remains separate from accepted source.

### Scene 4 — Shared Changes Versus Team-Accepted Changes

**Time:** `05:06–06:36`

**Visual:**

Side-by-side screens show PR still `Open`, unresolved task `Test negative invoice`, and destination `main` unchanged. Timeline `pushed → proposed → accepted`.

**Script:**

Pushing a topic branch makes the change available to colleagues. Opening a PR makes it reviewable. Neither operation means the destination now includes it. Binh might request a negative-invoice test or leave an unresolved task; applicable checks can still report issues. Only an authorized merge into the intended destination changes the accepted shared source. Do not confuse a successful push, a reviewer comment, an approval and a completed merge. In Free or Standard, a warning for an unresolved ordinary check is not necessarily an enforced block, so a clickable merge button is not proof of readiness.

**Purpose:**

Separate proposal evidence from review status and actual target integration.
