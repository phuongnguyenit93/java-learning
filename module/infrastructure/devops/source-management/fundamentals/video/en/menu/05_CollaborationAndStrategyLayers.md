---
video:
  url: ""
---

# Tools, Collaboration, and Team Strategy

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

## Version Control Versus Hosting Platforms

<!-- VIDEO_SECTION -->

### Scene 1 — Version Control Versus Hosting Platforms

**Time:** `00:00–01:15`

**Visual:**

Two layers: below, a Git A–B–C commit graph with fetch/push arrows; above, hosting PR/reviewer/policy cards. Use a dotted link between commit and PR.

**Script:**

Git records and exchanges commits. Hosting platforms such as GitHub, GitLab, Bitbucket and Azure DevOps add accounts, repository access, proposed-change pages and collaboration policies. Look at the two layers. An's commit C is a piece of technical Git history. An's pull request and Binh's review comment are collaboration records managed by a hosting service. `git log` cannot tell us who approved a PR, and a hosting interface does not replace Git's object database. Separating the layers makes it easier to troubleshoot a problem without asking the wrong system for evidence.

**Purpose:**

Distinguish Git mechanics from hosted collaboration records and their respective evidence.



## Purpose of Change Proposals and Review

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:15–01:27`

**Visual:**

Keep the underlying A–B–C Git history and unfold a hosted Tax 8%→10% PR with a diff and Binh's rounding question.

**Script:**

If a commit is not proof of review, what does a change proposal contribute?

**Purpose:**

Link recorded commits to the separate hosted proposal and review evidence.

### Scene 2 — Purpose of Change Proposals and Review

**Time:** `01:27–02:42`

**Visual:**

PR panel `Tax 8%→10%`, with intent, diff, reviewer question and state. Binh asks about rounding; An responds with a revised patch and test note.

**Script:**

A proposal explains why a change matters, displays the diff, and gives colleagues a place to challenge its assumptions before acceptance. In our invoice case, Binh notices that the new rate has not been checked against rounding behavior. He asks for an edge-case test, and An updates the proposal before another review. The value is not the presence of an Approve button. It is a traceable discussion connected to the actual code. Meanwhile, the underlying Git commit still exists even if the proposal has not yet been approved or integrated.

**Purpose:**

Use a concrete refund/invoice review question to demonstrate the distinct value of a PR.



## Access and Change Acceptance Rules

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:42–02:54`

**Visual:**

Retain the PR diff and Binh's comment; attach Read / Contribute / Approve / Merge permissions to protected main.

**Script:**

The review can be technically sound. Who is authorized to accept it?

**Purpose:**

Demonstrate why positive review context does not itself authorize an integration.

### Scene 3 — Access and Change Acceptance Rules

**Time:** `02:54–04:09`

**Visual:**

Show a permission matrix with `Read`, `Contribute`, `Approve`, and `Merge`; highlight an author who may push a topic branch but not merge protected main.

**Script:**

Teams distinguish permission to read code, contribute to branches, participate in reviews, and integrate into protected shared references. Someone can propose a change without having authority to update main directly. Depending on the hosting service and current configuration, required reviewers, checks or permissions may block completion. Do not assume the same rule or plan tier applies on every platform. The useful investigation is to ask who may perform which operation, on which repository or branch, and under what checks. These answers are verified in the actual hosted system, not invented from the Git commit graph.

**Purpose:**

Make accepted changes contingent on real permissions/policies rather than mere technical correctness.



## Branching Policy Versus Git Mechanics

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:09–04:21`

**Visual:**

From merge rights on main, reveal a branch-lifetime clock for short-lived topic and release branches while keeping the same Git merge mechanism.

**Script:**

Permissions determine who can integrate. What determines how long a branch should exist?

**Purpose:**

Separate the team's branch-duration and integration policy from the low-level Git operation.

### Scene 4 — Branching Policy Versus Git Mechanics

**Time:** `04:21–05:36`

**Visual:**

Keep graph A–B→topic, add a clock and three labels `Trunk-based`, `GitHub Flow`, `Git Flow`, then fade out command syntax to focus on cadence.

**Script:**

Creating a branch, merging, and rebasing are Git mechanisms. A team also decides when branches start, how quickly they should integrate, whether release branches are needed, and who can update the shared baseline. That is branching strategy. The very same `git merge` command can be part of quite different team workflows. This foundation video names the distinction rather than teaching every strategy. The branching-strategy module will compare trunk-based development, GitHub Flow and Git Flow using integration frequency, release needs, and coordination costs as evidence.

**Purpose:**

Separate team branch-lifetime policy from low-level Git commands.



## Repository Topology Strategy: One or Multiple Repositories

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:36–05:48`

**Visual:**

Shrink the branch-lifetime clock; compare one Git root for invoice-api/checkout-web with two roots and the same contract dependency.

**Script:**

Branch lifetime is one strategic choice. What about the number of repositories?

**Purpose:**

Bridge branch workflow policy to the independent choice of how many repositories hold related source.

### Scene 5 — Repository Topology Strategy: One or Multiple Repositories

**Time:** `05:48–07:03`

**Visual:**

Compare one repository containing `invoice-api` and `checkout-web` with two independent repositories; animate one cross-component contract edit requiring one coordinated change versus two proposals.

**Script:**

A team may keep several components in one repository or give them separate repositories. A payment-contract update in one repository can be coordinated in one version history. Split repositories may require two proposals and explicit compatibility checks. That does not mean a monorepo automatically builds faster, or a polyrepo always deploys independently. The real choice depends on ownership, access boundaries, tooling, and cross-component change patterns. The monorepo-polyrepo module develops those trade-offs. For now, remember that source collaboration has tool, review, branch-policy, and repository-topology layers.

**Purpose:**

Introduce topology as a separate governance/coordination decision and preserve module boundaries.
