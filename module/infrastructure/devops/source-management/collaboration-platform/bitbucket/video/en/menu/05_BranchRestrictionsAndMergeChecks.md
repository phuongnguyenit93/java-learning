---
video:
  url: ""
---

# Branch Restrictions and Merge Checks

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

## Purpose of Protecting Important Branches

<!-- VIDEO_SECTION -->

### Scene 1 — Purpose of Protecting Important Branches

**Time:** `00:00–01:35`

**Visual:**

Show `invoice-api/main` with two arrows `Direct push` and `PR merge`; overlay separate boxes `Branch restrictions: who may act` and `Merge checks: condition status`.

**Script:**

Orchid treats main as its shared integration reference. It wants to prevent accidental direct updates and avoid accepting proposals before agreed checks. Bitbucket Cloud provides related but distinct controls. Branch restrictions constrain certain operations on selected branches and users; merge checks evaluate PR conditions before integration. Ask two separate questions: is this actor authorized for this action, and does the proposal meet the conditions currently configured? A check result cannot automatically grant permissions, and a permission grant does not prove a PR is ready.

**Purpose:**

Establish the distinction between authorization restrictions and PR readiness checks.



## Branch Write Access Versus Merge Permissions

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:35–01:47`

**Visual:**

Split main into An's direct-push route and Mai's PR-merge route, each with its own restriction setting.

**Script:**

Protecting `main` is the goal. How do direct write access and merging through a pull request differ?

**Purpose:**

Separate the two action permissions that branch restrictions can control.

### Scene 2 — Branch Write Access Versus Merge Permissions

**Time:** `01:47–03:22`

**Visual:**

An has repository Write; show different results for `Push directly to main` and `Merge PR into main`, alongside scoped branch restriction settings.

**Script:**

Repository Write does not necessarily permit every action on a protected branch. Orchid may allow An to push a work branch while restricting direct updates to main and reserving PR merges for a defined set of users. Mai can inspect the exact branch restriction controlling each operation. If An reports a denied branch update, do not explain the error solely by pointing to his repository membership. Capture the attempted action and target branch; compare them with the actual configured restriction rather than granting blanket administrator privileges.

**Purpose:**

Demonstrate why branch push access and PR merge authorization are separate questions.



## Project and Repository Rules and Branch Patterns

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:22–03:34`

**Visual:**

Keep push/merge permissions visible while expanding Billing over invoice-api and billing-docs; overlay main and release/* patterns.

**Script:**

One operation has a restriction. How widely might the same rule apply?

**Purpose:**

Extend a branch rule from one operation to its broader project and branch-pattern coverage.

### Scene 3 — Project and Repository Rules and Branch Patterns

**Time:** `03:34–05:09`

**Visual:**

Project Billing shows `invoice-api` and `billing-docs`; highlight project-level `main` pattern and `release/*` matched/unmatched example branches.

**Script:**

Branch restrictions can live at repository or project scope and may target branch-name patterns. A project-level rule for main can affect both Orchid repositories, even if Mai thought only about the invoice API. A pattern such as `release/*` matches a set of branch names under its defined semantics, not every string containing the word release. Before stating that a policy governs a given PR, identify the actual destination branch and every applicable project/repository rule. Do not invent a universal winner when rules overlap; inspect the current settings.

**Purpose:**

Show project-wide impact and branch-pattern matching without asserting unsupported precedence.



## Merge Checks: Conditions Evaluated Before Integration

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:09–05:21`

**Visual:**

Collapse the permission matrix into a BILL-142 checklist for approvals, tasks, Changes requested and build status.

**Script:**

Now we know who can act. What evaluates the PR itself before merge?

**Purpose:**

Move from actor eligibility to the separate conditions evaluated for the proposed change.

### Scene 4 — Merge Checks: Conditions Evaluated Before Integration

**Time:** `05:21–06:56`

**Visual:**

PR `BILL-142` check list with `Binh approval`, `No unresolved task`, `No Changes requested`, and `Build status`; highlight the open negative-invoice task.

**Script:**

Merge checks assess conditions associated with a PR: a configured approval count, outstanding tasks, review requests for changes, or reported automated checks. Orchid wants Binh's approval and a completed negative-invoice test. With that task still open, its configured task-related condition may remain unresolved. A green check is not proof the tax formula is correct; it tells us a specified criterion was met. Capture the exact PR check and its configured scope instead of inferring policy from a comment or repository role.

**Purpose:**

Describe merge checks as evidence of configured conditions, not automatic domain correctness.



## Advisory Checks Versus Premium Merge Blocking

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:56–07:08`

**Visual:**

Keep the unresolved negative-invoice task in both Standard advisory and Premium enforced columns, with the enforce switch explicit.

**Script:**

Does an unresolved check always lock the merge action?

**Purpose:**

Distinguish an advisory check from Premium blocking only when enforcement is enabled.

### Scene 5 — Advisory Checks Versus Premium Merge Blocking

**Time:** `07:08–08:43`

**Visual:**

Two compared PR states: `Free/Standard: advisory warning; an otherwise authorized merge may proceed` and `Premium with Prevent a merge with unresolved merge checks enabled: blocked`.

**Script:**

This difference matters in Bitbucket Cloud. On Free and Standard, ordinary unresolved merge checks can appear as warnings even when an otherwise authorized contributor can still merge, provided no other control blocks the action. Premium supports enforced blocking through `Prevent a merge with unresolved merge checks` when the option is enabled and relevant checks apply. Premium purchase alone is not enough, and an advisory icon is not necessarily a prohibition. A careful recording names the plan, the setting and the actual outcome rather than promising that every check is enforced by default.

**Purpose:**

Prove the tier-and-configuration boundary between warning and enforced block.



## Scenario: The Same Unresolved Merge Check on Standard and Premium Plans

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:43–08:55`

**Visual:**

Hold the same PR actor, task and settings while comparing a permitted Standard attempt with blocked Premium enforcement without inventing a live outcome.

**Script:**

Let's keep the same unresolved task and compare what changes with the enforcement setting.

**Purpose:**

Isolate merge-check enforcement as the variable in a controlled conceptual comparison.

### Scene 6 — Scenario: The Same Unresolved Merge Check on Standard and Premium Plans

**Time:** `08:55–10:30`

**Visual:**

Use a controlled comparison table for one open `BILL-142` task: Standard authorized actor, advisory warning and possible merge; Premium enforcement enabled, blocked merge.

**Script:**

Assume the same PR, the same open task, and the same configured task-completion check. Under Standard, an authorized actor may be warned while still able to merge if other restrictions do not prevent it. That requires human governance to avoid bypassing important warnings. Under Premium with unresolved-check blocking enabled and applicable to the destination, the system prevents integration until the outstanding requirement is resolved. If the recording account does not actually have both tiers, show a clearly labeled documentation-based comparison. Do not manufacture live blocking evidence.

**Purpose:**

Contrast equivalent PR conditions across two enforcement models with precise assumptions.



## Overlapping Restrictions and Merge Diagnostics

<!-- VIDEO_SECTION -->

### Transition

**Time:** `10:30–10:42`

**Visual:**

Turn the advisory/blocking comparison into an Actor → branch pattern → project/repo grant → check → task diagnostic tree.

**Script:**

If an actual PR is blocked unexpectedly, which overlapping rule should we inspect first?

**Purpose:**

Teach which evidence distinguishes overlapping permissions and merge checks when the actual result is surprising.

### Scene 7 — Overlapping Restrictions and Merge Diagnostics

**Time:** `10:42–12:17`

**Visual:**

PR merge denial with four labeled causes: effective project/repo rights, target branch restriction, merge check, open task. Zoom into actor, target and exact error.

**Script:**

A stalled PR is not enough evidence to declare the platform broken. An might lack merge rights, a branch rule might govern main, a Premium check might be unresolved, or a review task may still be open. Mai should capture who acted, which destination was targeted, when the result appeared, and the exact error or warning. Then inspect matching project-level and repository-level settings and the PR's current review status. Granting Admin to everyone or switching off all rules would erase the very safeguard the team needs to understand. Diagnose before changing authority.

**Purpose:**

Teach a safe ordered investigation of overlapping restrictions and merge outcomes.
