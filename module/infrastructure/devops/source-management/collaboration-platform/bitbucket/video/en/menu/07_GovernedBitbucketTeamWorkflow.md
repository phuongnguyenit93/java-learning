---
video:
  url: ""
---

# A Governed Bitbucket Team Workflow

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

## From Repository Access to an Accepted Change

<!-- VIDEO_SECTION -->

### Scene 1 — From Repository Access to an Accepted Change

**Time:** `00:00–01:35`

**Visual:**

Full-screen timeline `Access → Git change → PR → Review/tasks → Checks/rights → Merge → Accepted main`, each stage tagged with a specific `BILL-142` evidence card.

**Script:**

Orchid grants appropriate repository access, An records a tax change in Git, opens a PR into `invoice-api/main`, and Binh reviews fractional-invoice behavior. An revises the source, the task is verified, applicable checks are evaluated, and someone with permission integrates into the destination. A commit is not approval. An Open PR is not accepted shared source. Even a completed merge is not proof of deployment. Pause at each stage and ask which actual Bitbucket page or recorded status would establish that it happened.

**Purpose:**

Tie access, proposal, review, checks and accepted source to distinct observable records.



## Pull Request Merge Readiness: Required Conditions

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:35–01:47`

**Visual:**

Keep the full Access→PR→Review→Merge timeline and magnify the readiness sheet: target, actor, approvals, tasks and enforcement.

**Script:**

We have followed a contribution toward accepted source. What must be true before its pull request is ready to merge?

**Purpose:**

Focus the end-to-end workflow on the independently verifiable merge conditions.

### Scene 2 — Pull Request Merge Readiness: Required Conditions

**Time:** `01:47–03:22`

**Visual:**

Show a PR readiness sheet with correct destination, actor merge rights, approvals, open tasks, Changes requested, and effective checks; label Standard warning and Premium enforcement separately.

**Script:**

A PR is not ready merely because An says the work is done or Binh clicked Approve. Mai should inspect the destination, actual merge permission, unresolved tasks, requests for changes, and applicable check status. On Free and Standard, an unresolved ordinary merge check may warn while an authorized user remains able to merge. On Premium, enforced blocking requires that unresolved-check prevention has been enabled and applies. Human governance therefore matters even when the interface does not block a click. Every box on our checklist should lead to inspectable evidence rather than an assumed green screen.

**Purpose:**

Provide an accurate merge-readiness checklist that respects tier-specific enforcement.



## Diagnosing a Stalled Pull Request

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:22–03:34`

**Visual:**

Turn an unresolved readiness indicator into an Actor / Target / Exact error / Matching restrictions diagnostic beside BILL-142.

**Script:**

If the PR remains stalled, which observable evidence should guide the diagnosis?

**Purpose:**

Teach diagnosis from the observed denial or warning instead of guessing about platform behavior.

### Scene 3 — Diagnosing a Stalled Pull Request

**Time:** `03:34–05:09`

**Visual:**

Show An denied on a PR merge, then a classification sheet `Actor`, `Target main`, `Exact error`, `Effective access`, `Matching branch restriction`, `Unresolved check/task`.

**Script:**

Imagine Binh has approved the tax PR but An cannot merge it. Instead of calling Bitbucket broken, Mai records the actual actor, destination and error message. She inspects project and repository permissions, matching restrictions on main, and the current state of checks and review tasks. If a check is only advisory but a merge is denied, some other restriction may be responsible. Giving everyone administrator rights or disabling all protections would hide the reason and increase risk. A successful investigation identifies the specific rule and operation that produced the result.

**Purpose:**

Teach a safe diagnostic sequence that distinguishes missing authority from unresolved PR conditions.



## Evidence of a Reviewed and Accepted Source Change

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:09–05:21`

**Visual:**

Retain the diagnostic evidence and reveal Jira key, PR review, resolved tasks and destination merged revision; preserve Pending for absent records.

**Script:**

Once the merge succeeds, how do we demonstrate that the shared source was actually accepted?

**Purpose:**

Connect resolving merge blockers to defensible evidence of accepted source.

### Scene 4 — Evidence of a Reviewed and Accepted Source Change

**Time:** `05:21–06:56`

**Visual:**

Evidence sheet lists Jira `BILL-142`, PR source/target/diff, Binh review and tasks, check enforcement mode, and the destination merged revision; leave missing items marked Pending.

**Script:**

A Jira work item explains the requirement but its Done status is not proof of a Git merge. The PR identifies the proposed source and destination. Binh's comments and tasks show what was challenged and how it was addressed. Check states tell us about configured conditions at the time, while the destination revision after merge proves which source was integrated. An Open PR with one approval is not enough. When the evidence sheet is complete, another teammate can reconstruct the change decision without relying on an outdated screenshot or an informal message.

**Purpose:**

Offer a compact, auditable acceptance record with explicit evidence limits.



## Handoffs to Git, Branch Strategy, and Bitbucket Pipelines

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:56–07:08`

**Visual:**

Keep the accepted destination revision visible; reveal Git mechanics, Branching Strategy, Bitbucket collaboration and Pipelines lanes with deployment outside source acceptance.

**Script:**

Accepted source is not the end of software delivery. Which module owns the next questions?

**Purpose:**

Close with a clear handoff from hosted merge evidence to Git mechanics and independent delivery verification.

### Scene 5 — Handoffs to Git, Branch Strategy, and Bitbucket Pipelines

**Time:** `07:08–08:43`

**Visual:**

Show lanes `Git mechanics`, `Branching strategy`, `Bitbucket collaboration`, and `Pipelines/Delivery`; move BILL-142 across boundaries; finish with `Accepted source ≠ deployed software`.

**Script:**

Bitbucket Cloud hosts source collaboration, access rules, PRs, review and acceptance. Git owns the underlying commit, branch and merge mechanics. Branching strategy asks how long branches should live and which integration approach suits the team; monorepo versus polyrepo asks how repositories should be divided. Bitbucket Pipelines and other CI/CD tools handle builds and delivery, which do not automatically follow from a successful PR merge. For Orchid, ask which revision exists, which proposal was reviewed, which rights and checks applied, and whether the destination changed. A separate deployment record is needed before anyone claims the tax fix is live.

**Purpose:**

Close with accurate ownership boundaries and a source-to-delivery handoff, without inventing a local learning API.
