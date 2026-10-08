---
video:
  url: ""
---

# Team Conventions and Integration Governance

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

## Branch Purpose, Naming, and Ownership Conventions

<!-- VIDEO_SECTION -->

### Scene 1 — Evaluate the team's operating rules

**Time:** `00:00–01:49`

**Visual:**

Team policy table with Branch role, Naming example, Owner, and Merge target for main, feature, release, and hotfix.

Any dashboard or PR values are clearly illustrative unless viewed with authorized host access; never fake a live reviewer status.

**Script:**

A strategy that lives only on a slide soon gets interpreted differently by each developer. Write a short convention: which branch receives integration, what naming identifies purpose, who owns review, and which line may receive maintenance fixes. A name like feature/tax doesn't secure code by itself; it helps people locate work and responsibility. A trunk-only team doesn't need to invent develop merely to fill a policy table. Keep conventions few and purposeful.

**Purpose:**

Create a clear role/name/owner convention without forcing one model.

## Branch Lifetime, Staleness, and Cleanup Policies

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:49–02:03`

**Visual:**

Keep the named main/feature/release/hotfix owner table and reveal a 24-day-old feature branch with responsible reviewer and closure criteria.

**Script:**

With branch roles and owners defined, measure work-branch age, staleness and cleanup against branch purpose.

**Purpose:**

Translate branch naming into actionable lifetime and cleanup responsibilities.

### Scene 1 — Evaluate the team's operating rules

**Time:** `02:03–03:52`

**Visual:**

Illustrative dashboard of PR ages 1, 6, and 24 days with owners, expected closure dates, and graph distance to merge-base.

Any dashboard or PR values are clearly illustrative unless viewed with authorized host access; never fake a live reviewer status.

**Script:**

A PR list includes a branch opened yesterday and another untouched for 24 days. Team policy needs an owner for aging work, a review of whether it can be sliced smaller, and a clear cleanup step after merge. Don't delete unmerged work just to improve a dashboard. A release branch supporting an active version has a different lifetime from a feature branch. Consider age, activity, diff size, and purpose rather than applying one rigid number to every situation.

**Purpose:**

Define stale-branch ownership instead of arbitrary deletion.

## Review Expectations, Responsibility, and Feedback Timing

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:52–04:06`

**Visual:**

Zoom from PR ages 1/6/24 days to a rounding PR with Binh's pending review request and the revision after feedback.

**Script:**

An aging work branch may be waiting for review. Follow a concrete feedback and re-review timeline.

**Purpose:**

Connect stale work to reviewer ownership and measurable turnaround.

### Scene 1 — Evaluate the team's operating rules

**Time:** `04:06–05:55`

**Visual:**

Illustrative pull request with author, reviewer, changed code and test note; request changes → update → re-review timeline.

Any dashboard or PR values are clearly illustrative unless viewed with authorized host access; never fake a live reviewer status.

**Script:**

A review agreement answers three questions: what problem the change addresses, who can assess it, and when feedback should arrive. In a timeout PR, the reviewer identifies a missing edge-case test; the author revises and requests another look. Track review wait time so a short feature branch doesn't become an idle long-lived one. Approvals count according to the host's permissions and policies; configuring those controls belongs to the collaboration-platform module, not to the strategy rule itself.

**Purpose:**

Relate reviewer ownership to timely, verifiable feedback.

## Policy-Level Pre-Merge Quality Gates

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:55–06:09`

**Visual:**

Hold Binh comment→An revision→re-review and reveal required tests, domain approval and compatibility criteria before merge.

**Script:**

Review has an owner. What tests, compatibility checks and approvals must be complete before a change merges?

**Purpose:**

Translate responsible review into explicit quality evidence that must precede acceptance.

### Scene 1 — Evaluate the team's operating rules

**Time:** `06:09–07:58`

**Visual:**

Team requirement checklist: passing tests, compatibility, review completion, release impact, beside a labeled PR check illustration.

Any dashboard or PR values are clearly illustrative unless viewed with authorized host access; never fake a live reviewer status.

**Script:**

State team-level requirements before clicking any platform switch: relevant tests, informed review, compatible contracts, and agreed merge conditions. Decide which gates must block and which merely inform. A green build icon doesn't prove every behavior correct; it supplies one piece of evidence. CI implements checks and the host configures enforcement. The branching-strategy decision is which conditions are necessary and who owns the response when they fail.

**Purpose:**

Separate required quality criteria from CI implementation details.

## Ownership of Mainline Health and Failed Integration

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:58–08:12`

**Visual:**

Carry the merge-gate checklist into a mainline test failure and show detection, owner assignment, tested revert/fix and recovery evidence.

**Script:**

Passing the gates cannot prevent every regression. Trace mainline recovery and responsibility for a revert or fix.

**Purpose:**

Show that mainline health needs post-integration recovery ownership, not just pre-merge checks.

### Scene 1 — Evaluate the team's operating rules

**Time:** `08:12–10:01`

**Visual:**

Mainline graph before and after a failing commit, recovery ownership timeline with tested fix-forward or revert options.

Any dashboard or PR values are clearly illustrative unless viewed with authorized host access; never fake a live reviewer status.

**Script:**

Suppose a PR merged successfully but post-integration checks reveal a regression. Name an owner for mainline health, inspect the suspected commit using log and diff, decide between a tested forward fix and a deliberate revert, and verify recovery. Don't casually force-push or reset shared history to erase evidence. A healthy frequent-integration process is not only about merging quickly; it's also about preventing others from working for hours atop a broken mainline.

**Purpose:**

Establish mainline health ownership and verifiable recovery.

## Team Policy Intent vs Vendor Branch Protection, Permissions, and CI Configuration

<!-- VIDEO_SECTION -->

### Transition

**Time:** `10:01–10:15`

**Visual:**

Retain the red-to-green recovery timeline and split team intent, Git history and host/CI enforcement into explicitly illustrative evidence lanes.

**Script:**

Once recovery ownership is clear, separate team policy intent from host permissions and CI implementation.

**Purpose:**

Clarify which owner specifies behavior and which system can enforce or report it.

### Scene 1 — Evaluate the team's operating rules

**Time:** `10:15–12:04`

**Visual:**

Three-layer diagram: Team intent → Git command/history → Host rulesets/permissions and CI check status; no local Servlet API.

Any dashboard or PR values are clearly illustrative unless viewed with authorized host access; never fake a live reviewer status.

**Script:**

Close with three layers. The team chooses branch lifetime, integration paths, reviewers, and required conditions. Git records branch operations and the commit graph. Hosts enforce permissions, policies and PR workflows, while CI runs checks and reports status. A two-reviewer team rule doesn't automatically block merges until implemented correctly; an enabled rule doesn't guarantee thoughtful review either. Hand off configuration to the appropriate platform module and validate its effect using authentic evidence.

**Purpose:**

Allocate governance, Git, hosting, and CI responsibilities accurately.
