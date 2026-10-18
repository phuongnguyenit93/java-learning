---
video:
  url: ""
---

# GitHub Flow as a Lightweight Collaboration Strategy

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

## GitHub Flow: Change Branches and Pull Request Feedback

<!-- VIDEO_SECTION -->

### Scene 1 — Trace the pull request cycle

**Time:** `00:00–01:45`

**Visual:**

Flowchart from default `main` through feature branch, pull request, review, merge and branch deletion with one tracked issue.

Use an attributed documentation view or demo PR; illustrative branch ages must be explicitly labeled.

**Script:**

GitHub Flow centers on a reliable default branch and a purpose-specific branch for a proposed change. The author publishes work and uses a pull request to collect feedback before merging when requirements are met. Follow the arrow back to the default branch rather than a long-lived develop line. It's a straightforward collaboration workflow that can be mapped to other hosts, but its steps alone don't impose a fixed two-day branch lifetime.

**Purpose:**

Describe the PR-driven workflow without inventing lifespan guarantees.

## Default-Branch Work, Pull Requests, Feedback, and Merge

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:45–02:00`

**Visual:**

Hold the open branch-to-main PR; magnify the tax diff and Binh's pending review request, keeping the main base unmerged.

**Script:**

Follow one change from the default branch through a work branch, hosted pull request, review and merge.

**Purpose:**

Turn the workflow overview into explicit source/target and reviewer evidence.

### Scene 1 — Trace the pull request cycle

**Time:** `02:00–03:45`

**Visual:**

Terminal demo `git switch -c feature/tax` and `git log --graph --oneline --all` beside GitHub Docs' Create/Files/Reviews PR surfaces.

Use an attributed documentation view or demo PR; illustrative branch ages must be explicitly labeled.

**Script:**

In a disposable repository, branch from main for feature/tax, make one small commit, and inspect the graph. A labeled PR illustration shows explicit source and target, author rationale, diff review, and a merge decision. After successful integration, deleting the work branch prevents confusion over its status. Git creates commits and refs; the hosted PR carries review records. There's no need for a localhost endpoint pretending to be a reviewer.

**Purpose:**

Demonstrate source/target boundaries between Git and hosted reviews.

## Early Feedback, Draft Proposals, and Review Benefits

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:45–04:00`

**Visual:**

Keep the tax diff and Binh's comment; move Draft to Ready after a test update and align review timestamps with the new commit.

**Script:**

A pull request enables review. Show how a draft can collect feedback before the patch becomes difficult to change.

**Purpose:**

Demonstrate timely feedback without treating the author's readiness as an approval.

### Scene 1 — Trace the pull request cycle

**Time:** `04:00–05:45`

**Visual:**

One example PR in Draft and Ready states with inline feedback and a follow-up commit, timestamps explicitly labeled.

Use an attributed documentation view or demo PR; illustrative branch ages must be explicitly labeled.

**Script:**

Open a draft pull request while the team discusses negative-value handling. Leave a precise inline question and get feedback before the branch grows. After updating the patch and tests, mark it ready for final review. The timeline shows feedback arriving early. Yet Draft or Approved labels are not merge authorization by themselves; a hosting platform's configured protections and checks still control the final action.

**Purpose:**

Show early-feedback value without confusing PR labels with merge-readiness.

## GitHub Flow Does Not Guarantee Trunk-Based Branch Lifetimes

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:45–06:00`

**Visual:**

Split the Draft/Ready PR lane into A merged in one day versus B still open after three weeks; compare distance from merge-base.

**Script:**

Drafts encourage collaboration, but how long can the branch remain open? Compare one-day and three-week PR histories.

**Purpose:**

Show that identical hosted review steps can conceal very different trunk integration cadence.

### Scene 1 — Trace the pull request cycle

**Time:** `06:00–07:45`

**Visual:**

Two PRs follow GitHub Flow steps but A merges after a day while B stays open three weeks, with age and divergence labels.

Use an attributed documentation view or demo PR; illustrative branch ages must be explicitly labeled.

**Script:**

Here is the important comparison: both PR A and PR B use a branch, discussion, and eventual merge into main. A lives a day, B three weeks and far from merge-base. GitHub Flow names a collaboration cycle; it doesn't, by itself, enforce TBD's very short branch lifetime or frequent integration. A team can combine the practices with explicit limits and slicing, but the labels are not synonyms.

**Purpose:**

Distinguish the two strategies using actual cadence evidence.

## GitHub Flow Strategy vs Platform PR and Ruleset Configuration

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:45–08:00`

**Visual:**

Preserve the contrasting PR age evidence; shift to team response targets versus host rulesets and status-check permissions with separate owners.

**Script:**

Branch age is a team expectation. See which approvals, checks and permissions the host can actually enforce.

**Purpose:**

Connect measured review cadence to vendor enforcement without implying a universal platform toggle.

### Scene 1 — Trace the pull request cycle

**Time:** `08:00–09:45`

**Visual:**

Two-column diagram: team decision 'review within two days' versus GitHub rulesets, required checks, and permissions with separate owners.

Use an attributed documentation view or demo PR; illustrative branch ages must be explicitly labeled.

**Script:**

The left column states team choices: when to branch, who should review, expected turnaround, and ownership of main. The right column lists platform mechanisms such as PRs, review requirements, and rulesets or branch protection. Not every cadence expectation has a corresponding toggle; branch age still needs monitoring and team action. Platform configuration belongs to the GitHub collaboration module. Here we establish the intent and observable evidence.

**Purpose:**

Separate policy intent from hosted enforcement details.
