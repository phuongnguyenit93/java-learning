---
video:
  url: ""
---

# Pull Request Reviews and Feedback Tasks

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

## Purpose of Code Review and Participant Responsibilities

<!-- VIDEO_SECTION -->

### Scene 1 — Purpose of Code Review and Participant Responsibilities

**Time:** `00:00–01:30`

**Visual:**

PR `BILL-142` with An as author, Binh as reviewer, and Mai as policy administrator; overlay `Intent?`, `Behavior?`, `Merge authority?`.

**Script:**

Code review is a technical conversation with accountable roles. An explains the new tax calculation, Binh checks whether fractional invoices still behave correctly, and Mai manages the collaboration rules. Being an administrator does not make Mai the domain expert for invoice rounding. Binh can identify a missing scenario in a two-line patch and request evidence before accepting it. An should respond with an updated change and a test, not merely seek another green icon. The meaningful output is an inspectable discussion and a decision on the current proposal.

**Purpose:**

Separate author, reviewer, and administrator responsibilities while focusing on observed business correctness.



## Reviewing Diffs and File or Line Discussions

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:30–01:42`

**Visual:**

Split the Invoice.java 100.05 inline question into a comment, an unresolved test task and a separate Changes requested review status.

**Script:**

We know why review needs distinct participants. Where does Binh inspect the actual changed lines and leave focused feedback?

**Purpose:**

Differentiate discussion, tracked work and reviewer decision without treating a comment as a merge gate.

### Scene 2 — Reviewing Diffs and File or Line Discussions

**Time:** `01:42–03:12`

**Visual:**

Zoom into `Files changed`, highlight the rounding line in `Invoice.java`, open a line comment about input `100.05`, then show its reply in PR activity.

**Script:**

Bitbucket's Files changed view lets Binh focus on the specific code An proposes to alter. Binh highlights the rounding operation and asks which step rounds an invoice worth one hundred point zero five. That gives An an input, a location, and a behavior to explain. An can reply in the discussion, leaving context for later reviewers. A vague chat message saying 'this looks wrong' would not do the same. Remember that a line comment records feedback; it does not automatically create a task or count as an approval.

**Purpose:**

Show an actionable, contextual diff comment and its limits as evidence.



## Comments, Change Requests, and Review Tasks

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:12–03:24`

**Visual:**

Keep Binh's approval next to the outstanding review task and merge-check warning; do not mark merge eligibility from one vote.

**Script:**

Some comments are questions while other requests must be tracked to completion. How does Bitbucket distinguish them?

**Purpose:**

Bridge a positive review to the independent task, permission and check conditions.

### Scene 3 — Comments, Change Requests, and Review Tasks

**Time:** `03:24–04:54`

**Visual:**

Show three independent PR indicators: `Comment: rename variable`, `Task: add fractional test`, `Changes requested`, with task unresolved/resolved lifecycle.

**Script:**

A comment asks or suggests something; a task is a tracked action; Changes requested expresses a reviewer's judgment that the current proposal needs revision. Binh may recommend a clearer variable name in a comment while creating a task for a missing fractional-invoice test. An can implement the test and update the task state, but Binh should still verify the evidence. Resolving a task is not identical to obtaining reviewer approval. Nor does every conversation thread automatically become a task. Show the separate indicators so learners can recognize what each one proves.

**Purpose:**

Distinguish discussion, actionable work and reviewer status instead of treating all feedback alike.



## Review Status Versus Merge Eligibility

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:54–05:06`

**Visual:**

Compare the previously approved diff with An's revision adding a test and unrelated discount edit; highlight uncovered new lines.

**Script:**

If Binh approves, are all merge conditions now satisfied?

**Purpose:**

Show why earlier feedback does not automatically cover subsequent source changes.

### Scene 4 — Review Status Versus Merge Eligibility

**Time:** `05:06–06:36`

**Visual:**

PR summary lists `Approved by Binh`, `Open review task`, `Unresolved merge checks`, and `Destination merge permission`; separate advisory warning from enforced blocker.

**Script:**

An approval says a reviewer accepted what they examined. It does not prove that all tasks are resolved, a Changes requested state is cleared, the acting user has merge permission, or every applicable check passed. On Free and Standard, ordinary unresolved merge checks can be advisory warnings while another authorized person still merges. On Premium, enforcement of unresolved checks depends on enabling and configuring the blocking option. We must inspect the target branch and actual message, not infer eligibility from one green approval icon.

**Purpose:**

Explain the distinct evidence for approval, task readiness, effective rights, and tier-specific enforcement.



## Updating Changes and Requesting Another Review

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:36–06:48`

**Visual:**

Preserve the updated discount diff and re-review marker, then reveal the protected main branch and merge actor permissions.

**Script:**

An has updated the PR after feedback. Does Binh's earlier review necessarily cover the new content?

**Purpose:**

Link completed review to branch-level authority without assuming the author can integrate.

### Scene 5 — Updating Changes and Requesting Another Review

**Time:** `06:48–08:18`

**Visual:**

Compare two PR revisions: tax change before; after adds rounding test and an unrelated discount edit; highlight the new diff and `Re-review`.

**Script:**

An pushes a new revision that adds the requested invoice test, but also changes an unrelated discount function. Binh's prior assessment did not cover that new code. Re-review means inspecting the current diff, confirming the original task is genuinely resolved, and questioning any new behavior outside scope. Depending on a team's configuration, Premium checks can also affect whether approvals must be reconsidered after source updates; do not assume a universal automatic reset or retention rule. What matters is that any acceptance claim refers to the version actually proposed now.

**Purpose:**

Make current-diff evidence the basis of review rather than trusting stale approvals.
