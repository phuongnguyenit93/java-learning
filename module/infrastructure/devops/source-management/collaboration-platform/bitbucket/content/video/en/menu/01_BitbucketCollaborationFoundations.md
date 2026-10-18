---
video:
  url: ""
---

# Bitbucket Cloud Collaboration Foundations

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

## Bitbucket Cloud: Concept and Source-Collaboration Role

<!-- VIDEO_SECTION -->

### Scene 1 — Bitbucket Cloud: Concept and Source-Collaboration Role

**Time:** `00:00–01:30`

**Visual:**

Use an **illustrative storyboard, not a live repository screenshot**, showing workspace `orchid-team` → project `Billing` → repository `invoice-api` (this hierarchy is not the repository URL). Place a Git commit graph beside it and separate `Pushed` and `Accepted by team` cards; avoid invented approval or status results.

**Script:**

An changes the invoice tax rule. Git records his revision, but teammates still need somewhere to inspect it before accepting it. Bitbucket Cloud is Atlassian's hosted Git collaboration service: repositories, permissions, pull requests, discussions, and merge controls. Notice the PR can remain open even though its source branch has been published. A push does not prove that the target branch changed, let alone that production deployed. Our evidence must come from the current repository, PR, and destination state.

**Purpose:**

Introduce Bitbucket's hosted collaboration role without conflating Git recording or deployment.



## Need for Hosted Source Collaboration in Teams

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:30–01:42`

**Visual:**

Compare An/Binh's overlapping ZIP files with a Bitbucket invoice-api PR diff; keep Git history and reviewer activity as distinct panes.

**Script:**

We have seen what Bitbucket Cloud offers. What goes wrong when a team has only disconnected file copies and no shared review trail?

**Purpose:**

Reveal the traceability benefit of a hosted proposal over manual file exchange.

### Scene 2 — Need for Hosted Source Collaboration in Teams

**Time:** `01:42–03:12`

**Visual:**

Put `invoice-final.zip` and `invoice-final-v2.zip` side by side, then replace the file exchange with a repository and a PR page containing an inspectable diff.

**Script:**

Imagine An and Binh sharing two slightly different project folders over chat. Whoever receives them has to guess which copy includes both the tax change and the rounding fix. A shared repository preserves recorded history; a pull request exposes exactly what is proposed and lets reviewers question it. Hosting matters not just because files have an online home, but because changes and their decisions become traceable. Ask which proposal was reviewed and which target eventually accepted it.

**Purpose:**

Show why controlled collaboration is better than choosing whichever folder looks newest.



## Git and Bitbucket Cloud: Division of Responsibilities

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:12–03:24`

**Visual:**

Keep the Git commit graph below a hosted PR card, then fade the host layer while a local commit remains visible.

**Script:**

We have a shared platform now. Which responsibilities still belong to Git itself?

**Purpose:**

Separate independent Git revision history from hosting-only discussion and approval records.

### Scene 3 — Git and Bitbucket Cloud: Division of Responsibilities

**Time:** `03:24–04:54`

**Visual:**

Two-layer diagram: Git `commit`, `fetch`, `branch` below; Bitbucket `Pull requests`, `Reviewers`, `Repository settings` above.

**Script:**

Git records revisions, branches, and exchanges history. An can create a local Git commit without opening Bitbucket. Bitbucket Cloud surrounds Git repositories with accounts, review conversations and access policies. A commit does not inherently contain a PR approval; that approval is a hosting record. When investigating an incident, decide whether you need to prove which code changed or which governance step occurred. Looking in the wrong layer can produce convincing but irrelevant evidence.

**Purpose:**

Distinguish Git history mechanics from Bitbucket's collaboration and administration.



## Workspace, Project, and Repository Hierarchy in Bitbucket Cloud

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:54–05:06`

**Visual:**

Move from Git/hosting layers into the orchid-team → Billing → invoice-api breadcrumb; add billing-docs with a separate commit graph.

**Script:**

Now that the layers are clear, where does Bitbucket organize the repositories?

**Purpose:**

Distinguish organizational containment from the independent history of each repository.

### Scene 4 — Workspace, Project, and Repository Hierarchy in Bitbucket Cloud

**Time:** `05:06–06:36`

**Visual:**

Reveal breadcrumb `orchid-team → Billing → invoice-api`; open sibling `billing-docs` while showing separate commit histories.

**Script:**

The workspace provides an organizational space. A project groups related repositories, and a repository holds its own Git source and history. Orchid uses workspace `orchid-team`, project `Billing`, and the separate `invoice-api` and `billing-docs` repositories. Being in one project does not merge their histories. On a real capture, navigate from workspace to project and then each repository, and read the breadcrumb before changing settings. It tells us which scope a later permission or policy might affect.

**Purpose:**

Establish the workspace/project/repository hierarchy without implying shared Git history.



## Members, Administrators, and Shared Repositories

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:36–06:48`

**Visual:**

Keep both Billing repositories onscreen; assign An, Binh and Mai to separate contributor, reviewer and admin columns without assuming grants.

**Script:**

With that structure in place, who owns contribution, review, and administrative decisions?

**Purpose:**

Link repository scope to accountable people while preventing role labels from implying effective permission.

### Scene 5 — Members, Administrators, and Shared Repositories

**Time:** `06:48–08:18`

**Visual:**

Three people cards An/Binh/Mai alongside `Contribute`, `Review`, `Administer`; highlight distinct responsibilities, not identical permissions.

**Script:**

An contributes the tax change, Binh brings invoice-rounding expertise, and Mai maintains access and team controls. Those responsibilities overlap sometimes, but being requested as a reviewer does not automatically grant administration rights. Equally, an administrator does not necessarily know the correct business formula. In Bitbucket Cloud, inspect actual effective permissions at the relevant workspace, project, and repository scope instead of assuming a job title gives universal access. Separate who can read, contribute, review, and configure the system.

**Purpose:**

Explain why requested reviewers, contributors, and administrators are different concepts.



## Pull Requests: Concept, Authors, Reviewers, and Target Branches

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:18–08:30`

**Visual:**

Expand the BILL-142 PR from the actor matrix; spotlight fix/tax source, main destination and Binh requested but not yet approved.

**Script:**

How does An invite Binh to assess the exact change?

**Purpose:**

Connect participant responsibilities to a concrete reviewable source-to-target proposal.

### Scene 6 — Pull Requests: Concept, Authors, Reviewers, and Target Branches

**Time:** `08:30–10:00`

**Visual:**

Open an illustrative `BILL-142 Update invoice tax` PR; point out source `fix/tax`, destination `main`, diff, and reviewer list.

**Script:**

A pull request proposes changes from a source branch and possibly source repository into a destination. It is a collaboration record, not a special Git commit. An opens a PR to show Binh the tax change and any impact on fractional invoices. Inviting Binh does not mean he approved the code. A displayed approval does not, by itself, prove every merge condition has been met. Before looking at a diff, confirm both endpoints; reviewing the wrong target can lead to the wrong source being updated.

**Purpose:**

Show the basic PR object and identify exactly what author, reviewer, and destination mean.



## Source-Change Journey Through Bitbucket Cloud

<!-- VIDEO_SECTION -->

### Transition

**Time:** `10:00–10:12`

**Visual:**

Keep BILL-142 Open; unfold Edit → Shared → Review task → Merge main, marking only observed stages as completed.

**Script:**

The PR is the proposal; let's follow it until the shared destination actually changes.

**Purpose:**

Turn the PR concept into a conditional integration path rather than an implied successful merge.

### Scene 7 — Source-Change Journey Through Bitbucket Cloud

**Time:** `10:12–11:42`

**Visual:**

Timeline of repository access → feature branch → PR → Binh comment → review task → merge conditions → `main`, each stage with a distinct proof card.

**Script:**

An first needs suitable repository access, then a Git change, a PR targeting `main`, and review feedback. Binh asks for a rounding test, An updates the proposal, and the team evaluates its applicable checks. Only an authorized integration into the destination updates accepted shared source. On Free or Standard, an unresolved ordinary check can be advisory rather than a hard block; Premium blocking also needs explicit configuration. The next chapter asks an essential question: which person has which right at each scope?

**Purpose:**

Summarize the observable end-to-end route while introducing effective access as the next chapter.
