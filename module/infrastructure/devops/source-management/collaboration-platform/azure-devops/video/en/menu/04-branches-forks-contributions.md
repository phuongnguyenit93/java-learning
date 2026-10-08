---
video:
  url: ""
---

# Contribution Paths Through Shared Branches and Forks

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

## Two Contribution Paths: Shared-Repository Branches and Forks

<!-- VIDEO_SECTION -->

### Scene 1 — Two Contribution Paths: Shared-Repository Branches and Forks

**Time:** `00:00–00:50`

**Visual:**

Two swimlanes: Alice branches inside checkout-api; external contributor forks checkout-api; PR arrows both target main.

Use an authorized test tenant or attributed official illustrations; RetailCo/Bug 104 is explicitly fictional.

**Script:**

Compare two contribution lanes. Alice can create feature branches directly inside checkout-api under her effective permissions; an isolated contributor may use a separate fork if policies and rights allow it. Both propose changes through PRs targeting the intended main branch. Neither path is universally safer: the trade-off concerns source write scope, identity, review requirements, and organizational control.

**Purpose:**

Select shared branch or fork based on actual write scope.

## Forks as Separate Repositories and Their Upstream Source

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:50–01:02`

**Visual:**

Move Alice's shared feature branch aside and animate an independently owned checkout-api fork.

**Script:**

A contributor without shared-repository Write may use a fork. What is separate in that model?

**Purpose:**

Explain how an alternative contribution path changes repository ownership and write scope.

### Scene 1 — Forks as Separate Repositories and Their Upstream Source

**Time:** `01:02–01:50`

**Visual:**

Diagram checkout-api upstream → checkout-api-alice fork → source feature/timeout; branch list and remote names origin/upstream labeled.

Use an authorized test tenant or attributed official illustrations; RetailCo/Bug 104 is explicitly fictional.

**Script:**

A fork is a separate repository with its own Git history and branches governed by the contributor's permitted rights. Pushing to the fork does not update upstream/main. To propose integration, Alice creates a PR from fork/feature/timeout into the original repository's main. Git remote names origin and upstream are local conventions—not authoritative service ownership labels.

**Purpose:**

Prove fork source updates do not silently update original repo.

## Fork Permissions and PR Policies of the Target Repository

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:50–02:01`

**Visual:**

Keep origin and upstream in separate boxes, then pin the target main branch's policies to upstream.

**Script:**

The fork has its own permissions; completion still targets upstream main and its policies.

**Purpose:**

Demonstrate why a fork's own permissions do not cancel target-branch policy.

### Scene 1 — Fork Permissions and PR Policies of the Target Repository

**Time:** `02:01–02:52`

**Visual:**

Two Security / Branch policies screenshots under different repository breadcrumbs, original target main policy highlighted.

Use an authorized test tenant or attributed official illustrations; RetailCo/Bug 104 is explicitly fictional.

**Script:**

Show repository settings under two distinct breadcrumbs. Azure Repos forks do not automatically copy the original's permissions, policies, or build pipelines. Being an administrator of a fork doesn't authorize writing into upstream/main. When the PR targets upstream/main, that target branch's governance controls completion. We read existing policy results rather than build a pipeline as part of this video.

**Purpose:**

Explain why upstream target policies govern fork PR completion.

## Source and Target Repositories and Branches of a PR

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:52–03:03`

**Visual:**

Attach source and target repository/branch labels to the fork-to-upstream PR arrow.

**Script:**

Next check both PR endpoints so the proposed edit cannot reach the wrong branch.

**Purpose:**

Eliminate direction ambiguity before inspecting the proposal's content.

### Scene 1 — Source and Target Repositories and Branches of a PR

**Time:** `03:03–03:57`

**Visual:**

PR header shows Source repository + feature branch and Target repository + main; arrow overlays and two separate owner names.

Use an authorized test tenant or attributed official illustrations; RetailCo/Bug 104 is explicitly fictional.

**Script:**

A PR identifies four things: source repository, source branch, target repository, and target branch. The name main may exist in many repositories, so branch labels alone aren't sufficient. This four-part mapping determines what is proposed and where target policies apply. Ask the learner to point to the arrow and explain whether the source is a fork or a branch in the shared repository.

**Purpose:**

Trace a PR across repository and branch boundaries.

## Focused PR Proposals with Descriptions and Change Context

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:57–04:07`

**Visual:**

Open the PR description next to the correctly targeted arrow and expose Summary, Risks and Verification.

**Script:**

Correct direction isn't a complete proposal. Reviewers also need purpose and verification.

**Purpose:**

Connect correct PR direction to the evidence reviewers need to judge the change.

### Scene 1 — Focused PR Proposals with Descriptions and Change Context

**Time:** `04:07–05:01`

**Visual:**

PR creation form fills meaningful title, Summary, Risks, Verification, related Bug 104 and invited reviewer.

Use an authorized test tenant or attributed official illustrations; RetailCo/Bug 104 is explicitly fictional.

**Script:**

For a timeout fix tied to illustrative Bug 104, use a title describing the behavior, then write the previous failure, intended change, test evidence, and compatibility risk. Reviewers compare this description with the Files diff. Linking a work item adds context, not proof of correctness. Opening a PR does not create a Git commit, and the author remains responsible for maintaining the source branch.

**Purpose:**

Build a scoped, verifiable change proposal.

## Draft Pull Requests Versus Ready-for-Review PRs

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:01–05:13`

**Visual:**

Leave the proposed fix visible, then place Draft and Ready status cards side by side.

**Script:**

An informative PR can still be Draft. When should it be marked ready for review?

**Purpose:**

Separate early feedback from formal readiness without assuming approval.

### Scene 1 — Draft Pull Requests Versus Ready-for-Review PRs

**Time:** `05:13–06:05`

**Visual:**

Compare a Draft PR status with Ready for review/Active UI; highlight changed indicator not simulated vote.

Use an authorized test tenant or attributed official illustrations; RetailCo/Bug 104 is explicitly fictional.

**Script:**

A Draft PR signals unfinished work or early feedback. Marking it ready for review invites formal review; it doesn't grant merge permission or satisfy policies. Compare the header state and current diff, not a fabricated vote. Reviewers should know whether they are offering early guidance or making a completion-relevant decision. Draft status is a workflow signal, not a policy bypass.

**Purpose:**

Distinguish proposal readiness from authorization and mergeability.

## Contribution Path Evidence: Author, Permissions, and Target Branch

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:05–06:15`

**Visual:**

Gather PR author, source rights, target policies and Draft/Active status into one evidence board.

**Script:**

Finally, record the author, source rights, target rules and readiness as evidence.

**Purpose:**

Summarize the three permission and state checks required for a contribution path.

### Scene 1 — Contribution Path Evidence: Author, Permissions, and Target Branch

**Time:** `06:15–07:06`

**Visual:**

One-page PR evidence board: author, source repo/branch, target repo/branch, Draft/Active, source rights, target policy indicator.

Use an authorized test tenant or attributed official illustrations; RetailCo/Bug 104 is explicitly fictional.

**Script:**

Finish by separating three questions: can the actor create a PR, push new source commits, and complete integration into the target? Each has different rights and state requirements. Capture actor, both repositories and branches, draft/active status, and target-policy evidence. Seeing a Create PR button doesn't prove permission to update protected main. This inventory prepares us to evaluate reviewers.

**Purpose:**

Build three-part create/push/complete permission evidence.
