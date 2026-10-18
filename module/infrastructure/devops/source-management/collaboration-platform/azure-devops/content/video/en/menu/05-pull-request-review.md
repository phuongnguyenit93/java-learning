---
video:
  url: ""
---

# Discuss and Review Pull Requests

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

## PR Descriptions and Changed-File Diffs

<!-- VIDEO_SECTION -->

### Scene 1 — PR Descriptions and Changed-File Diffs

**Time:** `00:00–00:53`

**Visual:**

Pull request Description beside Files changed tab, with sample timeout fix diff; highlight unrelated file changes.

Use an authorized test PR or credited Microsoft documentation, never invent real reviewer votes.

**Script:**

Read the PR as a testable proposal: the Description promises a timeout fix, while Files shows which code actually changed. If an unrelated file appears, the reviewer asks why before approving. A statement such as 'fixed' isn't evidence by itself. When the author pushes again or the target moves, the comparison can change. Review the current diff rather than an outdated capture.

**Purpose:**

Compare stated intent against actual PR diff.

## Assigned Reviewers and Review Context

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:53–01:03`

**Visual:**

Leave the timeout diff open while highlighting the named Bob and policy-required Carla reviewer slots.

**Script:**

The description explains intent; now who should inspect the actual timeout diff?

**Purpose:**

Move from what changed to who is expected to inspect the change.

### Scene 1 — Assigned Reviewers and Review Context

**Time:** `01:03–01:53`

**Visual:**

PR reviewers panel showing optional invited Bob and required reviewer Carla with separate indicators.

Use an authorized test PR or credited Microsoft documentation, never invent real reviewer votes.

**Script:**

The Reviewers panel distinguishes Bob, optionally invited by the author, from Carla, required by a target-branch rule. Counting avatars isn't the same as counting eligible approvals. Reviewers need relevant access and technical context, including awareness of the target branch. Use authenticated demo data or clearly attributed documentation; never claim fictional people actually approved a live PR.

**Purpose:**

Separate reviewer invitation from policy-required reviewer.

## Inline Comments and Discussion Threads in Pull Requests

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:53–02:04`

**Visual:**

Pin Bob's review request next to the altered timeout line and open an unresolved discussion thread.

**Script:**

Assigning reviewers starts a conversation. How do they point to a particular changed line?

**Purpose:**

Turn reviewer assignment into a concrete, traceable question about source code.

### Scene 1 — Inline Comments and Discussion Threads in Pull Requests

**Time:** `02:04–02:57`

**Visual:**

Inline comment at changed timeout line; discussion thread state Active versus Resolved; code and thread shown together.

Use an authorized test PR or credited Microsoft documentation, never invent real reviewer votes.

**Script:**

Attach a comment to the exact changed timeout line and name the failing input or missing case. The author replies and updates the source branch; the reviewer checks the new diff. Marking a thread Resolved records discussion state, not proof of correct code. If comment-resolution policy is enabled and blocking, an active thread can prevent completion even when reviewers otherwise approve.

**Purpose:**

Connect inline feedback, author revision and thread resolution evidence.

## Approve Versus Approve with Suggestions Votes

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:57–03:08`

**Visual:**

Keep the thread onscreen while opening the Approve and Approve with suggestions choices.

**Script:**

A discussion thread isn't a vote. What do the two positive review choices mean?

**Purpose:**

Separate a discussion thread from a submitted review vote.

### Scene 1 — Approve Versus Approve with Suggestions Votes

**Time:** `03:08–03:56`

**Visual:**

Review vote menu highlights Approve and Approve with suggestions; completion button remains separate.

Use an authorized test PR or credited Microsoft documentation, never invent real reviewer votes.

**Script:**

Open Azure Repos' reviewer vote choices and distinguish Approve from Approve with suggestions. Both express approval; the latter carries suggestions for improvement. Neither performs a merge, and whether the vote counts depends on the target branch's minimum-reviewer settings. Record which revision was reviewed instead of treating a green approval icon as an integration action.

**Purpose:**

Explain approval with suggestions without equating votes with merge.

## Wait for Author Versus Reject: Review Vote Implications

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:56–04:07`

**Visual:**

Replace approval cards with Wait for author and Reject against Carla's required-review badge.

**Script:**

Approval and suggestions are not the same as Wait for author or Reject.

**Purpose:**

Contrast positive review votes with signals that still require a response or block policy.

### Scene 1 — Wait for Author Versus Reject: Review Vote Implications

**Time:** `04:07–04:57`

**Visual:**

Vote menu Wait for author and Reject; required reviewer badge and target branch policy result panel side by side.

Use an authorized test PR or credited Microsoft documentation, never invent real reviewer votes.

**Script:**

Carla selects Wait for author because a timeout edge case remains unresolved; the author should revise and request another look. Reject indicates the proposal is unacceptable as reviewed. A required reviewer's negative vote can block completion under policy; optional reviewer behavior depends on the configured rules. Don't invent a simplified vote engine—read the real policy evaluation.

**Purpose:**

Read blocking implications of required negative votes.

## Review Votes Versus Pull Request Completion

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:57–05:08`

**Visual:**

Hold the vote count beside a failed build policy and an unavailable Complete action.

**Script:**

Even an approved PR may fail branch policy or lack permission to Complete.

**Purpose:**

Show why submitted reviews and authorized completion are independent conditions.

### Scene 1 — Review Votes Versus Pull Request Completion

**Time:** `05:08–05:58`

**Visual:**

PR Overview shows approved votes but failed build validation and disabled Complete action.

Use an authorized test PR or credited Microsoft documentation, never invent real reviewer votes.

**Script:**

Consider a PR with enough approving votes but a failing build-validation requirement. Review votes reflect reviewer decisions; completion still requires effective permissions and successful blocking policies on the target. An Approved badge alone isn't merge evidence. Show both statuses simultaneously and identify which missing condition must be addressed. We don't branch into implementing Azure Pipelines here.

**Purpose:**

Prove review vote isn't the same as a successful completion.

## Author Revisions, Feedback Resolution, and Re-Review

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:58–06:09`

**Visual:**

Move from the old vote to a new source commit and refresh the diff and vote-reset indicator.

**Script:**

If the author changes code, an old vote may not describe the current diff.

**Purpose:**

Explain why revised source demands renewed scrutiny under configured rules.

### Scene 1 — Author Revisions, Feedback Resolution, and Re-Review

**Time:** `06:09–07:03`

**Visual:**

Author pushes a second commit in demo, Files diff refreshes; minimum-reviewer vote reset option screenshot alongside votes before/after.

Use an authorized test PR or credited Microsoft documentation, never invent real reviewer votes.

**Script:**

The author pushes another commit to an Active PR's source branch. Files and checks update, while earlier reviewer votes may be retained or reset depending on minimum-reviewer policy options. Neither 'every push clears votes' nor 'votes always stay valid' is a safe general rule. Reopen the current reviewer list and policy results, and ask the reviewer to validate the new source revision before completion.

**Purpose:**

Demonstrate policy-configurable vote reset and re-review.

## Evidence of Reviewer Assignments, Discussions, and Review Votes

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:03–07:14`

**Visual:**

Compress the updated PR into a checklist of reviewers, votes, threads, commits and active policy results.

**Script:**

Before moving to branch policy, capture exactly which votes and threads are current.

**Purpose:**

Create a reproducible record of what the current review actually covers.

### Scene 1 — Evidence of Reviewer Assignments, Discussions, and Review Votes

**Time:** `07:14–08:03`

**Visual:**

Checklist screenshot current reviewers/required labels/votes, unresolved threads, source commit count/date, Policy outcome and PR status.

Use an authorized test PR or credited Microsoft documentation, never invent real reviewer votes.

**Script:**

Perform a review audit: identify required reviewers, each current vote, unresolved threads, most recent source update, and policy results. A green Approved label tells only part of the story. Record the observation time and refresh after further commits rather than relying on old screenshots. The next chapter explains why those target-branch policy conditions control completion.

**Purpose:**

Build an evidence checklist for reviewer and discussion state.
