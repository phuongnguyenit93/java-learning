---
video:
  url: ""
---

# Merge Requests as GitLab Change Proposals

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

## Shared-Project Branch Contributions

<!-- VIDEO_SECTION -->

### Scene 1 — Shared-Project Branch Contributions

**Time:** `00:00–01:04`

**Visual:**

Show gateway/main alongside gateway/fix-rounding. Highlight only the work-branch edit, with MR !57 pointing toward main but no completed merge.

**Script:**

An has appropriate access to the shared project and wants to correct Issue number 42. Instead of directly updating main, An prepares the change on fix-rounding and proposes it through an MR. Binh can inspect the difference and ask for the missing negative-input case before integration. Branch creation and push rights still depend on the role and protection rules, so Developer is not a promise of unrestricted write access. The MR arrow shows the intended destination, not a successful merge. We are showing the hosted workflow, not Git command syntax.

**Purpose:**

Present the controlled shared-project contribution path without assuming merge eligibility.

## Forks as Separate Projects and Contributions to Upstream

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:04–01:17`

**Visual:**

Keep the previous scene’s evidence as a smaller left panel, highlight "a work branch inside the shared project", then reveal the next question on the right with a scope-change arrow.

**Script:**

The shared-project route requires access; a fork changes where the contributor owns the source branch.

**Purpose:**

Bridge the earlier observation about a work branch inside the shared project to the next decision about a fork is a separate project with its own permissions without resetting the case.

### Scene 1 — Forks as Separate Projects and Contributions to Upstream

**Time:** `01:17–02:18`

**Visual:**

Draw company/payments/gateway and contractor/gateway as separate project cards with separate Members lists. Use a source-to-upstream MR arrow instead of a direct push.

**Script:**

A contractor without upstream push rights may be able to contribute through a fork, depending on project visibility and forking policy. The fork is another GitLab project with its own namespace, not a branch inside the original project. Being able to change source in the fork does not grant merge authority on the upstream main branch. In our illustration, An proposes a change from contractor/gateway to company/payments/gateway. Reviewers must inspect both project names because group or privacy restrictions can affect the allowed contribution route.

**Purpose:**

Demonstrate project ownership and permission isolation across a fork contribution.

## Source and Target Projects and Branches in Merge Requests

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:18–02:31`

**Visual:**

Keep the previous scene’s evidence as a smaller left panel, highlight "a fork is a separate project with its own permissions", then reveal the next question on the right with a scope-change arrow.

**Script:**

Once two project namespaces participate, an MR can be aimed at the wrong project or branch.

**Purpose:**

Bridge the earlier observation about a fork is a separate project with its own permissions to the next decision about verify source and target project-branch pairs without resetting the case.

### Scene 1 — Source and Target Projects and Branches in Merge Requests

**Time:** `02:31–03:33`

**Visual:**

Zoom into an MR creation form with Source contractor/gateway:fix-rounding and Target company/payments/gateway:main. Briefly switch the target to release/v1 to flag an unintended comparison.

**Script:**

The branch name alone is not enough to understand a merge request. Identify both the source project and branch and the target project and branch. Our contributor's fix lives in a fork, while the intended destination is gateway main. Choosing a maintenance target by mistake could produce a surprising diff or send the correction into the wrong release line. Similar branch names do not imply identical history. Before assigning reviewers, inspect the selected target and changed-file count; a wildly unexpected diff deserves investigation before further edits.

**Purpose:**

Make MR direction and wrong-target detection a visual procedure.

## Change Descriptions, Assignees, and Draft/Ready States

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:33–03:46`

**Visual:**

Keep the previous scene’s evidence as a smaller left panel, highlight "verify source and target project-branch pairs", then reveal the next question on the right with a scope-change arrow.

**Script:**

An MR has correct endpoints, but its description and readiness determine how a reviewer can evaluate it.

**Purpose:**

Bridge the earlier observation about verify source and target project-branch pairs to the next decision about Draft solicits early feedback; Ready is not approval without resetting the case.

### Scene 1 — Change Descriptions, Assignees, and Draft/Ready States

**Time:** `03:46–04:43`

**Visual:**

Illustrate MR !57 with description input -1, expected rejection, and observed behavior. Put Assignee An and Reviewer Binh in separate fields; animate Draft to Ready with Approvals still empty.

**Script:**

A useful MR describes the defect, before-and-after behavior, verification approach, and risk. An opens Draft MR number 57 while the negative-input check is missing and invites early comments. The assignee coordinates progress while reviewers evaluate quality; the fields need not contain the same person. When An supplies evidence and selects Ready, that signals readiness for review, not a newly submitted approval. Read the actual merge widget and reviewer state before claiming the proposal meets requirements.

**Purpose:**

Explain document quality and lifecycle readiness without treating a Draft change as an acceptance event.

## Merge Request Lifecycle: Open, Update, Review, Merge, or Close

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:43–04:56`

**Visual:**

Keep the previous scene’s evidence as a smaller left panel, highlight "Draft solicits early feedback; Ready is not approval", then reveal the next question on the right with a scope-change arrow.

**Script:**

Ready starts evaluation, but an MR can still change or close along more than one route.

**Purpose:**

Bridge the earlier observation about Draft solicits early feedback; Ready is not approval to the next decision about Open, Merged and Closed without merge carry different evidence without resetting the case.

### Scene 1 — Merge Request Lifecycle: Open, Update, Review, Merge, or Close

**Time:** `04:56–05:57`

**Visual:**

Build an Open → updated commit → review/discussion → Merged or Closed flow. Annotate that previous approvals may reset when project settings require it.

**Script:**

After an MR opens, the author may push more source commits, which changes the diff reviewers are examining. Binh should inspect the newest revision, and earlier approvals may be reset depending on project configuration. An Open label is not proof that merge eligibility has been met. A Closed without merge outcome likewise does not show the target contains the correction. Our two final branches are possible outcomes, not claims about live MR number 57. A real completion claim needs GitLab's Merged state and target-source evidence.

**Purpose:**

Train accurate lifecycle reading without confusing closure with integration.
