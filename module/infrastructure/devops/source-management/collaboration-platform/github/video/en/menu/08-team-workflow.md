---
video:
  url: ""
---

# GitHub Team Collaboration Workflow

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

## From Issue to Contribution, Review, and Eligible Merge

<!-- VIDEO_SECTION -->

### Scene 1 — From Issue to Contribution, Review, and Eligible Merge

**Time:** `00:00–00:57`

**Visual:**

Animate Issue #42 → fix-rounding work branch → PR #57 → main. Mark the sequence hypothetical and reveal the final merge arrow only after review and rules are discussed.

**Script:**

Return to the negative rounding defect. Issue number 42 describes the input and unexpected behavior. An prepares a correction on a suitable work branch or fork, then opens PR number 57 toward main, links the Issue, and explains the before-and-after evidence. Binh reads the diff and may request revisions. Only when access and applicable repository requirements are satisfied could the change merge. Pause at each card and ask what GitHub evidence establishes this stage rather than assuming the whole chain succeeded.

**Purpose:**

Bring the complete collaboration lifecycle together without presenting hypothetical GitHub outcomes as live facts.

## Access and Review Requirements That Block Pull Requests

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:57–01:08`

**Visual:**

Hold the Issue → branch/fork → PR review chain; highlight visibility and Write-access icons before reaching the reviewer.

**Script:**

The proposal may be sound yet collaboration can fail before reviewers reach the business logic. Which access layer is responsible?

**Purpose:**

Surface permissions as the first potential blocker in the running workflow.

### Scene 1 — Access and Review Requirements That Block Pull Requests

**Time:** `01:08–02:03`

**Visual:**

Build a diagnostic tree from can't see repository to can't push head to can't request eligible reviewer. Annotate Read/Write/team membership and fork alternative, not live permission changes.

**Script:**

If Binh cannot open the repository, investigate visibility and access rather than the rounding algorithm. If An can read but cannot push a work branch to the shared repository, the correct remedy may be scoped permission or a contribution through a fork. A person listed in CODEOWNERS also needs eligible repository access for the review process. Capture the actor, denied action, and scope. Granting Admin broadly is not a justified way to move PR number 57 forward.

**Purpose:**

Separate access discovery, branch contribution and eligible review routing problems.

## Ruleset, Branch Protection, and Status Check Merge Blockers

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:03–02:14`

**Visual:**

Keep the actor and repository permission checks visible; add branch protection, enforcing rulesets, and status-check gates before Merge.

**Script:**

Even with an eligible reviewer, a PR can remain blocked by repository governance rather than source changes.

**Purpose:**

Distinguish a valid reviewer from complete merge eligibility under configured governance.

### Scene 1 — Ruleset, Branch Protection, and Status Check Merge Blockers

**Time:** `02:14–03:10`

**Visual:**

Create a PR #57 diagnostic worksheet: Draft, review count, code owner, required check source, branch protection, rulesets. Leave actual-results cells blank pending real inspection.

**Script:**

Suppose Binh approved but Merge is still unavailable. First check whether the PR is Draft, which base branch it targets, which branch protection rule matches, and which enforcing rulesets also apply. Then inspect qualifying approvals, code-owner requirements, and the exact required status checks. A single branch protection rule and multiple simultaneously applicable rulesets are different mechanisms. This is a diagnostic order, not a recommendation to disable them. If a check is missing, identify its expected name and reporting source.

**Purpose:**

Teach a reproducible policy/blocker diagnosis without inventing pass or fail states.

## Evidence of Merged Pull Requests, Linked Issues, and GitHub Releases

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:10–03:21`

**Visual:**

Fade the merge-blocker checklist into four separate evidence columns: merged PR, Issue state, Release, deployment.

**Script:**

If the team really did merge, what can we claim—and what must still be verified separately?

**Purpose:**

Prepare the audience to report exactly which outcome was verified after resolving a blocker.

### Scene 1 — Evidence of Merged Pull Requests, Linked Issues, and GitHub Releases

**Time:** `03:21–04:16`

**Visual:**

Reveal columns PR Merged, Issue Closed?, Release Published?, and Deployed?. Only the hypothetical merge is assumed; the other outcomes remain explicit questions.

**Script:**

A Merged PR establishes integration into the destination history as recorded by GitHub. It does not always mean Issue number 42 closed; that depends on the link and closing conditions. A v1.4.0 Release is a separate publication tied to a tag and notes, not an automatic consequence of this PR. None of those repository records alone proves the fix reached production. Before reporting success, name exactly which of these four events has evidence and which still needs confirmation.

**Purpose:**

Teach responsible post-merge status claims across issue, release and deployment boundaries.

## Handoffs to Git Mechanics, Branching Strategy, Versioning, and CI/CD

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:16–04:27`

**Visual:**

Gather the four evidence columns into labeled lanes for Git, GitHub, branching strategy, and delivery/versioning.

**Script:**

We followed an Issue toward acceptance and publication. Which tool or policy layer owned each piece of evidence?

**Purpose:**

Make the module's concluding handoff precise so acceptance evidence is not confused with deployment or version control mechanics.

### Scene 1 — Handoffs to Git Mechanics, Branching Strategy, Versioning, and CI/CD

**Time:** `04:27–05:24`

**Visual:**

Four lanes: Git history, GitHub collaboration, Branching Strategy, CI/CD and Versioning. Sort Issue #42, PR #57, tag/Release and hypothetical pipeline/deployment records into their owning lanes.

**Script:**

Retell this workflow as separate responsibilities. Git records commits, branches, and distributed history. GitHub hosts the collaboration: repositories, pull requests, reviewers, access, and acceptance rules. Branching Strategy decides branch lifetime and integration cadence. CI/CD and version policy govern validation, packaging, numbering, and deployment. If PR number 57 merges, that is evidence about accepted source, not proof of a shipped and running product. The useful final habit is to identify which layer each claim comes from and what record can verify it.

**Purpose:**

End the learning path with a precise handoff to Git mechanics, branch policy and delivery evidence.
