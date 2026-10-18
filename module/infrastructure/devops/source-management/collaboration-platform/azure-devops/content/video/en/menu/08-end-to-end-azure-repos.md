---
video:
  url: ""
---

# Apply an End-to-End Azure Repos Workflow

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

## Mapping Organizations, Projects, Repositories, and Owners

<!-- VIDEO_SECTION -->

### Scene 1 — Mapping Organizations, Projects, Repositories, and Owners

**Time:** `00:00–00:50`

**Visual:**

Diagram fictional RetailCo > Checkout > checkout-api; actors Alice Contributor, Bob/Carla reviewers, Charlie target completer, admin security group.

Mark RetailCo/Bug 104 as illustrative; use only authorized Azure DevOps Services sandbox evidence or sourced Microsoft documentation, never a production action.

**Script:**

Return to one explicitly fictional RetailCo/Checkout/checkout-api scenario. Project Administrators govern project settings, a restricted repository owner group handles sensitive controls, Contributors propose changes, and Reviewers assess risk. Label every person with the scope of their responsibility. Don't assume titles automatically confer bypass rights. Mark this as a Services scenario so readers don't generalize features across Server versions.

**Purpose:**

Anchor a coherent cross-chapter actor/scope map.

## Selecting Shared Branches or Forks Based on Contribution Rights

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:50–01:02`

**Visual:**

Keep the RetailCo → Checkout → checkout-api hierarchy and branch into shared work or a fork.

**Script:**

The people and repositories are mapped. Does Alice have Write, or should she use a fork?

**Purpose:**

Connect the named participants and scope to their actual contribution rights.

### Scene 1 — Selecting Shared Branches or Forks Based on Contribution Rights

**Time:** `01:02–01:58`

**Visual:**

Flow shared checkout-api feature branch when Alice Contribute allowed, else approved fork with independent permissions and target main policy.

Mark RetailCo/Bug 104 as illustrative; use only authorized Azure DevOps Services sandbox evidence or sourced Microsoft documentation, never a production action.

**Script:**

Alice is in Contributors with permission to create and push feature branches, so this team uses a shared checkout-api branch. An external collaborator may use a fork under organization policy; the original's permissions and policies don't automatically copy into that fork. Either way, the PR targets the intended upstream main and its target rules. Record who controls source updates and who can complete into the target.

**Purpose:**

Choose a path from effective rights rather than ideology.

## Transition from Draft to Review-Ready Pull Requests

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:58–02:09`

**Visual:**

Follow Alice's chosen feature branch into a Draft PR and then an explicit Ready-for-review action.

**Script:**

After choosing the source path, prepare a Draft proposal and explicitly mark it Ready.

**Purpose:**

Show how the selected contribution path reaches a reviewable proposal.

### Scene 1 — Transition from Draft to Review-Ready Pull Requests

**Time:** `02:09–03:03`

**Visual:**

PR creation form draft feature/timeout → main, title timeout edge case, Bug 104, tests plan; later Ready for review status.

Mark RetailCo/Bug 104 as illustrative; use only authorized Azure DevOps Services sandbox evidence or sourced Microsoft documentation, never a production action.

**Script:**

Alice identifies a timeout edge case and opens a Draft PR linked to illustrative Bug 104 for early feedback. The description records the failing input, intended correction, and verification approach. After revising the source and running relevant tests, she marks the PR ready for review. That status change doesn't generate approvals or make policies pass. Keep the same PR ID through the remaining story.

**Purpose:**

Show Draft→Ready as a deliberate author action, not merge readiness.

## Tracking Review Votes and Branch Policy Requirements

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:03–03:13`

**Visual:**

Hold the Ready PR beside Bob's Approve and Carla's Wait for author, then add policy badges.

**Script:**

Bob's Approve and Carla's Wait for author show why required reviewers matter.

**Purpose:**

Introduce the conflict between visible positive votes and required reviewer conditions.

### Scene 1 — Tracking Review Votes and Branch Policy Requirements

**Time:** `03:13–04:10`

**Visual:**

Reviewers Bob Approve and required Carla Wait for author, active comment and target main two reviewer minimum/build validation.

Mark RetailCo/Bug 104 as illustrative; use only authorized Azure DevOps Services sandbox evidence or sourced Microsoft documentation, never a production action.

**Script:**

Bob inspects the diff and approves, but required reviewer Carla chooses Wait for author because a negative-timeout test is missing. Main requires two counted approvals, resolved discussions, and build validation. The PR remains blocked despite Bob's approval. Alice adds a test, updates the source, and asks Carla to review again. Vote reset depends on configured policy, so read the current reviewer panel rather than assuming earlier votes survive.

**Purpose:**

Trace a required negative vote into concrete author revision.

## Completing PRs with Work Item Traceability Evidence

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:10–04:20`

**Visual:**

Replace Carla's pending feedback with a revised diff, resolved thread, green check and Complete choice.

**Script:**

After revision and valid checks, inspect evidence for an authorized completion.

**Purpose:**

Show the evidence that must change before integration can succeed.

### Scene 1 — Completing PRs with Work Item Traceability Evidence

**Time:** `04:20–05:16`

**Visual:**

After revised PR, Carla Approve, resolved thread, build Passed, authorized Complete, Completed status, target history and linked Bug 104.

Mark RetailCo/Bug 104 as illustrative; use only authorized Azure DevOps Services sandbox evidence or sourced Microsoft documentation, never a production action.

**Script:**

After Carla approves, discussions are resolved and required validation passes, an authorized actor can complete the PR or rely on configured auto-complete while pending conditions settle. Completed status and target Git history record integration under the selected merge type, while Bug 104 retains the change rationale. Its state may change under workflow options, but completion doesn't universally close the bug. Inspect PR, Git, and work-item evidence separately.

**Purpose:**

Validate linked PR, target history and work item separately.

## Blocked PR Completion: Diagnostic Scenario

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:16–05:26`

**Visual:**

Keep the hypothetical Completed PR on screen and overlay alternative Failed, Pending and Denied blockers.

**Script:**

The example merged; what different blocker could prevent the same outcome?

**Purpose:**

Contrast a successful path with the distinct failure modes that require diagnosis.

### Scene 1 — Blocked PR Completion: Diagnostic Scenario

**Time:** `05:26–06:18`

**Visual:**

Diagnostic variants: required reviewer waiting, build Failed, conflict, permission denied, missing link; arrows to Carla, CI owner, Git contributor, admin.

Mark RetailCo/Bug 104 as illustrative; use only authorized Azure DevOps Services sandbox evidence or sourced Microsoft documentation, never a production action.

**Script:**

Show multiple controlled variants of the same PR: Carla still waiting means reviewer/author action; failed validation goes to the CI owner; conflict returns to Git mechanics; denied completion requires scoped effective-permission review; missing work item requires genuine context. Don't use another approval or bypass as a universal fix. Every diagnostic branch should identify both the evidence and the accountable owner.

**Purpose:**

Route each PR blocker to its actual owning domain.

## End-to-End Evidence: Permissions, Reviewers, Checks, and PR Status

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:18–06:28`

**Visual:**

Resolve the blockers into a structured ledger of actors, rights, votes, checks and PR state.

**Script:**

Record source, votes, policies and actor permissions before claiming the workflow passed.

**Purpose:**

Turn the scenario into an auditable collaboration record.

### Scene 1 — End-to-End Evidence: Permissions, Reviewers, Checks, and PR Status

**Time:** `06:28–07:18`

**Visual:**

End-to-end evidence table: org/project/repo, access level/effective rights, source/target/PR status, votes/threads, blocking policy results, Completed time.

Mark RetailCo/Bug 104 as illustrative; use only authorized Azure DevOps Services sandbox evidence or sourced Microsoft documentation, never a production action.

**Script:**

Build the final evidence board: organization/project/repository, access entitlement and effective rights, source/target and PR ID, lifecycle transitions, current reviewer votes/threads, required policy results, authorized completer, and completion timestamp. Every row should point to a real permitted screen or record, not an invented green checkbox. This lets the team explain acceptance decisions beyond the commit graph alone.

**Purpose:**

Build a complete collaboration governance evidence record.

## Handoffs to Git Mechanics, Branching Strategy, Boards, and Pipelines

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:18–07:29`

**Visual:**

Fold the ledger into four lanes: Git, branching strategy, Azure Boards and Pipelines.

**Script:**

Finish by assigning Git, branch strategy, Boards and Pipelines to their separate evidence.

**Purpose:**

Point to the next topic owners without attributing delivery evidence to PR completion.

### Scene 1 — Handoffs to Git Mechanics, Branching Strategy, Boards, and Pipelines

**Time:** `07:29–08:25`

**Visual:**

Final split architecture: Git CLI object graph, Branching Strategy lifetime chart, Monorepo/Polyrepo topology, Azure Boards work item, Azure Pipelines check owner, Azure Repos PR in center.

Mark RetailCo/Bug 104 as illustrative; use only authorized Azure DevOps Services sandbox evidence or sourced Microsoft documentation, never a production action.

**Script:**

Finish with clear responsibility boundaries. Git owns object history, merges, and fetch/push. Branching Strategy determines branch lifetime and release practice; Monorepo/Polyrepo selects repository topology; Boards owns work-item workflows; Pipelines owns build jobs. Azure Repos hosts source, permissions, PR discussions, and target policy evaluation of reported checks. The takeaway isn't memorizing every Azure DevOps button; it's knowing which evidence supports a decision and who owns the next action.

**Purpose:**

Close with accurate handoffs, not newly invented technical curricula.
