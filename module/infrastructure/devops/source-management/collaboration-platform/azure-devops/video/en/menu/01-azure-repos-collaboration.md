---
video:
  url: ""
---

# Azure Repos and Git-Based Collaboration

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

## Azure DevOps and Azure Repos: Concepts and Service Boundaries

<!-- VIDEO_SECTION -->

### Scene 1 — Azure DevOps and Azure Repos: Concepts and Service Boundaries

**Time:** `00:00–00:57`

**Visual:**

Azure DevOps Services org landing page with Repos, Boards, Pipelines tiles; zoom specifically into Repos.

Use an authorized Azure DevOps Services sandbox. If live data is unavailable, label Microsoft Learn imagery as illustration, never claim fabricated live outcomes.

**Script:**

Open a permitted demo organization's Azure DevOps Services home. Several services appear, but our journey stays inside Azure Repos. Azure DevOps is the broader collaboration product; Repos hosts Git repositories, permissions, and pull requests. Git itself records commits on your machine; Azure Repos provides team-facing governance around shared history. Azure DevOps Server is a separately deployed product with version-specific features, so don't assume every Services policy applies to it.

We will move from organizations/projects/repositories to access rights, branches or forks, reviews, policies and PR completion, then combine them in the fictional RetailCo/Checkout/checkout-api case. Boards and Pipelines are related but remain separate learning topics.

**Purpose:**

Distinguish Azure DevOps Services, Azure Repos, and native Git.

## The Role of Hosted Source Collaboration Beyond Git

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:57–01:08`

**Visual:**

Keep the Repos tile lit and open a Git commit graph next to a PR review card.

**Script:**

Git keeps changes; where can a team see its review decisions and shared status?

**Purpose:**

Show the missing collaboration decisions once the learner has seen hosted Git storage.

### Scene 1 — The Role of Hosted Source Collaboration Beyond Git

**Time:** `01:08–02:04`

**Visual:**

Split view: terminal git log on left, Azure Repos PR list with reviewers and status badges on right.

Use an authorized Azure DevOps Services sandbox. If live data is unavailable, label Microsoft Learn imagery as illustration, never claim fabricated live outcomes.

**Script:**

Place git log beside a real Azure Repos pull-request list. Commit IDs prove which snapshots exist, but they don't tell us who is allowed to merge into main or whether two reviewers inspected a change. The PR view captures actors, discussions, and policy results. Use a permitted sandbox repository; if no real PR is available, show a clearly labeled Microsoft Learn illustration rather than fabricate live approvals.

**Purpose:**

Show why hosted collaboration evidence complements version history.

## Git History Versus Azure Repos Collaboration Governance

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:04–02:14`

**Visual:**

Carry the commit graph left and reveal repository Security and PR Policies on the right.

**Script:**

Now separate the history Git records from the governance Azure Repos applies.

**Purpose:**

Locate where Git history stops being sufficient evidence of governed acceptance.

### Scene 1 — Git History Versus Azure Repos Collaboration Governance

**Time:** `02:14–03:08`

**Visual:**

Overlay on terminal git fetch/push/merge graph, then highlight Azure Repos repository Security and PR Policies tabs.

Use an authorized Azure DevOps Services sandbox. If live data is unavailable, label Microsoft Learn imagery as illustration, never claim fabricated live outcomes.

**Script:**

In the terminal, fetch, push, merge, and rebase manipulate Git objects or references. In Azure Repos, the Security view and PR policies answer different questions: who may read or contribute, who votes, and which requirements block completion. We won't invent a local HTTP endpoint to mimic Microsoft policy. We'll inspect authorized hosted views and official guidance; deeper Git mechanics belong to the Git module.

**Purpose:**

Assign mechanics to Git and governance to Azure Repos.

## Organizations, Projects, Repositories, and Contributors: Core Vocabulary

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:08–03:19`

**Visual:**

Turn the Git-versus-Repos split into an org → project → repository → contributor breadcrumb.

**Script:**

Those responsibilities need a home. Let's name the organization, project, repository and people involved.

**Purpose:**

Give names to the Azure DevOps scopes that own the subsequent collaboration decisions.

### Scene 1 — Organizations, Projects, Repositories, and Contributors: Core Vocabulary

**Time:** `03:19–04:15`

**Visual:**

Draw Azure DevOps Services > RetailCo organization > Checkout project > checkout-api repository > Alice contributor hierarchy; project settings and Repos navigation.

Use an authorized Azure DevOps Services sandbox. If live data is unavailable, label Microsoft Learn imagery as illustration, never claim fabricated live outcomes.

**Script:**

Draw RetailCo → Checkout → checkout-api as an explicitly fictional example, not a live account connection. The organization contains projects and users, a project holds one or more repositories, and contributors act within granted rights. Administration and access can differ by scope. On an authorized tenant, check breadcrumb and selected repository before reading any security setting; access in repo A does not prove access in repo B.

**Purpose:**

Map each actor to an organization, project, repository and role.

## Pull Requests: Concept, Purpose, and Source/Target Branches

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:15–04:26`

**Visual:**

Follow Alice from the contributor box into the PR creation form; pin source feature/timeout and target main.

**Script:**

With the actors identified, watch Alice propose a change into main through a PR.

**Purpose:**

Link the actor hierarchy to a directional proposal without treating it as a merge.

### Scene 1 — Pull Requests: Concept, Purpose, and Source/Target Branches

**Time:** `04:26–05:22`

**Visual:**

Azure Repos PR creation form: source feature/timeout → target main, Files tab, title and description highlighted.

Use an authorized Azure DevOps Services sandbox. If live data is unavailable, label Microsoft Learn imagery as illustration, never claim fabricated live outcomes.

**Script:**

Open the PR creation form for a permitted demo repo: source feature/timeout and target main. The pull request is not a new Git commit type. It's a hosted proposal that compares two lines of work before integration. Read both source and target repository names, not only branch labels; forked contributions can cross repository boundaries. A draft signals work in progress and shouldn't be treated as completion-ready.

**Purpose:**

Describe PR source/target and purpose without conflating proposal and merge.

## Reviewers, Branch Policies, and the Model of PR Merge Requirements

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:22–05:34`

**Visual:**

Keep the source/target PR arrow visible as reviewer votes, branch policies and Complete status appear.

**Script:**

A PR is a request. Reviewers and target rules determine whether it becomes an accepted change.

**Purpose:**

Introduce the separate review, policy and completion requirements acting on one proposal.

### Scene 1 — Reviewers, Branch Policies, and the Model of PR Merge Requirements

**Time:** `05:34–06:25`

**Visual:**

Three column PR example: reviewer votes, target main Branch policies page, Complete button state; use non-sensitive real sandbox or official screenshot.

Use an authorized Azure DevOps Services sandbox. If live data is unavailable, label Microsoft Learn imagery as illustration, never claim fabricated live outcomes.

**Script:**

Freeze three areas: reviewer votes, main's policy evaluation, and the Complete control. An Approve is a reviewer's vote; minimum reviewers and required checks are evaluated under the target branch's settings. Completion is a separate authorized action. Read blocking versus optional labels—two reviewer avatars are not automatically two counted approvals. An individual vote may be excluded under configured rules.

**Purpose:**

Relate reviewer vote, target policy, and authorized completion.

## Azure Repos Boundaries with Azure Boards and Azure Pipelines

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:25–06:37`

**Visual:**

Pull the PR work-item link and build check into neighboring Boards and Pipelines lanes.

**Script:**

The PR also points to work tracking and validation. Which Azure DevOps service owns those records?

**Purpose:**

Identify where collaboration evidence depends on another Azure DevOps service.

### Scene 1 — Azure Repos Boundaries with Azure Boards and Azure Pipelines

**Time:** `06:37–07:36`

**Visual:**

Show Repos PR-linked work item badge and generic Boards/Pipelines navigation separated with a dotted boundary.

Use an authorized Azure DevOps Services sandbox. If live data is unavailable, label Microsoft Learn imagery as illustration, never claim fabricated live outcomes.

**Script:**

A PR may link Bug 104 to explain why a timeout change exists. That work item belongs to Azure Boards, not to a commit. Build validation can appear in PR policies, but defining jobs and pipeline YAML belongs to Azure Pipelines. Here we read the status consumed by Azure Repos rather than teach CI setup. When a check fails, record which one failed and hand the underlying build investigation to its owner.

**Purpose:**

Explain Boards/Pipelines handoffs without teaching their implementation.

## Collaboration States and Evidence for an Azure Repos Change

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:36–07:47`

**Visual:**

Fold the service lanes into a PR evidence checklist showing owner, branches, votes, policy and state.

**Script:**

Let's collect the actor, branches, votes, checks and state into one trustworthy evidence trail.

**Purpose:**

Turn conceptual service boundaries into a concrete verification habit.

### Scene 1 — Collaboration States and Evidence for an Azure Repos Change

**Time:** `07:47–08:38`

**Visual:**

Annotated single PR overview with highlighted author, source/target, reviewer vote, active thread, Policy status, Active/Completed label.

Use an authorized Azure DevOps Services sandbox. If live data is unavailable, label Microsoft Learn imagery as illustration, never claim fabricated live outcomes.

**Script:**

Close with an evidence checklist beside the PR: which repository, who authored it, exact source and target, effective reviewer votes, unresolved threads, blocking policy results, and current Active or Completed state. Don't infer today's status from a stale screenshot; refresh after source commits change. These are the observations we'll inspect in more depth before calling a contribution accepted.

**Purpose:**

Create a reliable cross-screen collaboration evidence checklist.
