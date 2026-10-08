---
video:
  url: ""
---

# Foundations of Source Collaboration on GitHub

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

## GitHub Repository Hosting and the Need for Team Collaboration

<!-- VIDEO_SECTION -->

### Scene 1 — GitHub Repository Hosting and the Need for Team Collaboration

**Time:** `00:00–00:56`

**Visual:**

Begin with three ZIP archives all labeled final, then replace them with a GitHub repository model. Highlight Code, Pull requests, and Settings in sequence; mark the display as an illustrative storyboard, not live repository evidence.

**Script:**

Picture three teammates sending three files called final. Which one did the team actually accept? GitHub does not make that business decision for us, but it brings Git history and collaboration evidence into one place. Code shows recorded source; Pull requests holds proposals and discussions; Settings is where eligible maintainers manage repository rules. Our running example is an invoice rounding defect. To handle it responsibly, we will follow the source change, its review, and the conditions for accepting it.

Our route is repository ownership and visibility, shared branches versus forks, PRs and reviews, access roles, branch protection, Issue/Release evidence, and an end-to-end contribution. This is a GitHub collaboration lesson, not a replacement for Git internals or CI deployment configuration.

**Purpose:**

Establish the hosting and collaboration problem before introducing the platform’s separate surfaces.

## Unreviewed Changes, Lost Context, and Unclear Acceptance Without Shared Collaboration

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:56–01:07`

**Visual:**

Shrink the Code / Pull requests / Settings triad to one corner. Place an empty 'negative amount tested?' cell at center beside the proposed rounding fix.

**Script:**

A shared place for code is useful, but it cannot by itself prevent a poor change. What evidence is still missing?

**Purpose:**

Carry the opening hosting model into the unresolved acceptance question: a stored patch still needs review and test evidence.

### Scene 1 — Unreviewed Changes, Lost Context, and Unclear Acceptance Without Shared Collaboration

**Time:** `01:07–02:02`

**Visual:**

Reveal a four-column storyboard for a rounding PR: recorded, negative-input tested, reviewed, accepted. Leave unsupported cells blank rather than inventing a green check.

**Script:**

Suppose a rounding fix works for ordinary invoices, but nobody has checked a negative amount. Seeing the edit in a repository does not answer that question. We need a specific defect description, an eligible reviewer, and an example that could reproduce the failure. Notice which cells of our evidence board remain blank. A review process reduces the chance of overlooked problems, but a comment cannot substitute for tests or the domain knowledge needed to judge the result.

**Purpose:**

Show why traceable evidence and explicit acceptance differ from merely storing a change.

## Git Version History Versus GitHub Collaboration Responsibilities

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:02–02:13`

**Visual:**

Fade the incomplete evidence board into two labeled lanes: local Git commits at left, GitHub review and policy at right.

**Script:**

We have two kinds of evidence: recorded changes and governed acceptance. Which system owns each?

**Purpose:**

Assign the missing evidence to the correct layer so viewers do not ask Git history alone to prove approval.

### Scene 1 — Git Version History Versus GitHub Collaboration Responsibilities

**Time:** `02:13–03:09`

**Visual:**

Split the screen: a local Git commit-and-branch graph on the left; a GitHub PR with reviewers and rules on the right. Animate one proposal crossing the boundary, not the underlying history mechanics.

**Script:**

An can record a commit or create a branch on a laptop with no GitHub session. Those are Git operations. GitHub hosts a Git repository and adds review requests, discussions, repository permissions, and merge conditions. Therefore 'I merged this locally' is not proof that a protected GitHub pull request met its review rules. The two layers work together but answer different questions. We will follow the hosted collaboration evidence here and leave command details to the Git learning module.

**Purpose:**

Separate source-history mechanics from hosted approval and policy behavior.

## GitHub Repositories: Source, History, and Collaboration Context

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:09–03:20`

**Visual:**

Slide the local Git graph behind the GitHub repository window; uncover its branch selector, commit history, and Pull requests tab one at a time.

**Script:**

Now that the two responsibilities are distinct, what exactly can we inspect inside a GitHub repository?

**Purpose:**

Move from distinguishing Git and GitHub responsibilities to identifying the exact repository views that show each state.

### Scene 1 — GitHub Repositories: Source, History, and Collaboration Context

**Time:** `03:20–04:16`

**Visual:**

Show three labeled interface sketches: Code, commit history, and Pull requests. Highlight main versus fix-rounding, with proposed changes shown outside main.

**Script:**

The Code view displays a file tree for the branch currently selected. The commit list provides recorded points in history. Pull requests can show a proposed change that the destination branch does not yet contain. In our sketch, fix-rounding carries a correction, while main remains unchanged until that correction is accepted. A default branch is not automatically the production deployment either. When looking at a screen, first ask which branch and which stage of acceptance the evidence actually represents.

**Purpose:**

Keep a proposal, a recorded state, and deployment status from being treated as one thing.

## Personal Accounts, Organizations, and Repository Context

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:16–04:27`

**Visual:**

Freeze main versus fix-rounding, then zoom out to the owner/repository path above the file tree; draw a question mark over who controls it.

**Script:**

The repository has a history; we still need to know who owns its collaboration context.

**Purpose:**

Shift the audience from inspecting changes to identifying repository stewardship before discussing access.

### Scene 1 — Personal Accounts, Organizations, and Repository Context

**Time:** `04:27–05:20`

**Visual:**

Compare alice/demo with orchid/payments. Expand orchid into organization, teams, and repositories, and show enterprise governance as an optional outer layer, not an automatic permission.

**Script:**

The owner-and-repository path identifies where a repository belongs. Alice might control a personal demo, while the payments project may need organization ownership so teams can maintain access when people change jobs. Some enterprise arrangements govern several organizations. None of these names alone proves what this viewer may do. Ownership, membership, and effective repository permission are separate questions. We'll keep the payments repository under a team namespace for the rest of the storyboard.

**Purpose:**

Differentiate account ownership, organization governance, and individual effective permission.

## Repository Owners, Collaborators, and Contributors

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:20–05:31`

**Visual:**

Collapse the alice/demo and orchid/payments namespaces into person cards for owner, collaborator, contributor and reviewer; keep permission indicators blank.

**Script:**

An organization has people with different responsibilities. Does having contributed once grant permission now?

**Purpose:**

Move from organization ownership to the different roles people can play without assuming their effective rights.

### Scene 1 — Repository Owners, Collaborators, and Contributors

**Time:** `05:31–06:26`

**Visual:**

Arrange person cards in owner, collaborator, contributor, reviewer lanes. Let one person occupy more than one lane, but leave push permission unassigned until access is inspected.

**Script:**

An may have submitted a fix through a fork before. That makes An a contributor, not automatically someone who can push to the upstream repository. Binh can be requested to review PR number 57 without becoming the owner. Mai may administer access, yet the rounding rule still needs someone with the right business knowledge. We should ask separately: what did this person do, what can this person do now, and what responsibility do they have for this proposal?

**Purpose:**

Prevent role labels from being mistaken for push rights or business approval.

## Pull Requests as Proposals Before Changes Enter Shared Source

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:26–06:37`

**Visual:**

Pin An and Binh's contributor/reviewer cards beside fix-rounding and main, then draw an unapproved PR #57 arrow between those branches.

**Script:**

We know the participants. How do they all evaluate the same proposed change rather than exchange files?

**Purpose:**

Convert the participant map into one concrete proposal that several people can inspect and discuss.

### Scene 1 — Pull Requests as Proposals Before Changes Enter Shared Source

**Time:** `06:37–07:34`

**Visual:**

Connect head fix-rounding to base main with a PR #57 card. Unfold Description, Files changed, Conversation, and Reviews as separate tabs.

**Script:**

A pull request proposes moving changes from a head branch into a base branch. Our storyboard labels it PR number 57, from fix-rounding toward main. Reviewers can read the stated reason, inspect the changed files, and record a decision. An Open PR, a Draft PR, and a Merged PR are not interchangeable states. Even one approval is not identical to merging, particularly when repository rules require additional evidence. The PR is the shared proposal, not proof that the target has already changed.

**Purpose:**

Anchor head/base direction, review context, and acceptance state in one visual.

## Relationships Among Repositories, Source/Target Branches, Pull Requests, and Reviewers

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:34–07:45`

**Visual:**

Keep PR #57 in focus; add Issue #42 before its head branch and a dashed Release after a possible merge to main.

**Script:**

We have looked at the pieces individually. Let's assemble a single traceable change without collapsing their different states.

**Purpose:**

Place the PR within a traceable lifecycle while signaling that Issue resolution, merge, and publication are separate events.

### Scene 1 — Relationships Among Repositories, Source/Target Branches, Pull Requests, and Reviewers

**Time:** `07:45–08:43`

**Visual:**

Progressively reveal Issue #42 → fix-rounding head → PR #57 → reviewers/checks → main; add a dashed arrow toward a possible later Release. Label as an illustrative sequence, not a live result.

**Script:**

The Issue describes the need; the work branch carries a candidate correction; PR number 57 asks reviewers to evaluate it against main. Applicable permissions and checks determine whether the proposal can be accepted. A GitHub Release could follow later, but merging the PR does not automatically publish one and does not prove deployment. Notice that each arrow represents a separate event with its own evidence. In the next chapter we ask who owns this repository and who can even see or contribute to it.

**Purpose:**

Give the learner an end-to-end map and explicitly preserve the merge versus release boundary.
