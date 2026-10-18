---
video:
  url: ""
---

# GitLab Project and Group Organization

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

## Project Ownership: Personal Namespace vs Group

<!-- VIDEO_SECTION -->

### Scene 1 — Project Ownership: Personal Namespace vs Group

**Time:** `00:00–00:57`

**Visual:**

Contrast alice/demo and company/payments/gateway. Attach a review checklist for URL references, effective members, and integrations to a possible transfer, without claiming a transfer was executed.

**Script:**

Two projects can have similar source but very different ownership responsibilities. Alice's demo may be personal, while the payments gateway should remain manageable after a developer leaves the team. Group ownership makes it possible to organize continuing membership and governance. Moving a project to another namespace requires reviewing URLs, access, and integrations; it is not merely cosmetic. Before inviting reviewers, confirm which group actually owns the target project and who has authority to maintain it.

**Purpose:**

Connect namespace selection to continuity, not just prettier repository URLs.

## Organizing Groups, Subgroups, and Their Projects

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:57–01:10`

**Visual:**

Keep the previous scene’s evidence as a smaller left panel, highlight "the namespace identifies the stewardship boundary", then reveal the next question on the right with a scope-change arrow.

**Script:**

A group owner can govern several projects; what changes when that ownership is arranged into subgroups?

**Purpose:**

Bridge the earlier observation about the namespace identifies the stewardship boundary to the next decision about groups organize governance rather than source directories without resetting the case.

### Scene 1 — Organizing Groups, Subgroups, and Their Projects

**Time:** `01:10–02:10`

**Visual:**

Build a company → payments → gateway and billing organizational tree next to a separate Git source tree. Annotate inherited membership arrows as configuration-dependent.

**Script:**

A group can contain projects, and subgroups organize them under teams or product domains. Payments may contain both gateway and billing, yet this is an organizational hierarchy rather than nested Git repositories. Membership from a parent group can contribute access to child projects, while a direct project invitation has a narrower source. Open the Members view to distinguish these grants. The group tree helps reason about administration; it does not determine where each service is deployed or which repository holds another's code.

**Purpose:**

Explain the hierarchy and the source of inherited grants without conflating deployment topology.

## Repositories and Project-Level Governance Settings

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:10–02:23`

**Visual:**

Keep the previous scene’s evidence as a smaller left panel, highlight "groups organize governance rather than source directories", then reveal the next question on the right with a scope-change arrow.

**Script:**

The group sets shared context, but project-specific settings still govern how this repository is used.

**Purpose:**

Bridge the earlier observation about groups organize governance rather than source directories to the next decision about default branch guides browsing, not production state without resetting the case.

### Scene 1 — Repositories and Project-Level Governance Settings

**Time:** `02:23–03:23`

**Visual:**

Show a schematic project Settings panel with visibility, default branch, MRs, and Members. Change master to main in the diagram and highlight suggested MR targets, not deployment.

**Script:**

Project-level settings shape the repository's default source view, visibility, and collaboration behavior. A default branch change can affect what people browse and the target they commonly select for MRs. It is not evidence that production deployed from the newly selected branch. Before making such a change, inspect open MRs, protected-branch rules, and integrations that reference branch names. Someone able to read the source may still lack permission to edit these settings. The relevant context is the project and the acting user's actual authority.

**Purpose:**

Demonstrate default-branch consequences without inventing CI or deployment effects.

## Public, Private, Internal: Existing GitLab.com Internal Projects Remain, New Ones Disallowed

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:23–03:36`

**Visual:**

Keep the previous scene’s evidence as a smaller left panel, highlight "default branch guides browsing, not production state", then reveal the next question on the right with a scope-change arrow.

**Script:**

A project has a visibility setting; does GitLab mean the same thing by Internal across all offerings?

**Purpose:**

Bridge the earlier observation about default branch guides browsing, not production state to the next decision about GitLab Internal differs from GitHub Enterprise Internal without resetting the case.

### Scene 1 — Public, Private, Internal: Existing GitLab.com Internal Projects Remain, New Ones Disallowed

**Time:** `03:36–04:33`

**Visual:**

Compare Public/Private/Internal across new GitLab.com projects, grandfathered GitLab.com projects, and Self-Managed/Dedicated offerings. Mark 'new Internal on GitLab.com' unavailable.

**Script:**

Public project content is visible to outsiders; Private requires appropriate authorization. On GitLab Self-Managed or Dedicated, Internal visibility generally exposes a project to authenticated instance users, except external users. GitLab.com no longer permits creating new Internal projects, though existing Internal projects can retain legacy visibility. This is not GitHub Enterprise Cloud's enterprise-internal audience model. Before publishing proprietary gateway code, inspect the actual offering and readership, not just a label that sounds familiar from another platform.

**Purpose:**

Prevent a serious visibility mistake by distinguishing offering constraints and legacy status.

## Group and Project Boundaries for Source Sharing and Collaboration

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:33–04:46`

**Visual:**

Keep the previous scene’s evidence as a smaller left panel, highlight "GitLab Internal differs from GitHub Enterprise Internal", then reveal the next question on the right with a scope-change arrow.

**Script:**

Visibility and ownership define boundaries, but a person's actual actions depend on how access is granted.

**Purpose:**

Bridge the earlier observation about GitLab Internal differs from GitHub Enterprise Internal to the next decision about a project invitation differs from group-derived access without resetting the case.

### Scene 1 — Group and Project Boundaries for Source Sharing and Collaboration

**Time:** `04:46–05:46`

**Visual:**

Draw two grant paths: directly invited to gateway versus inherited from payments group. Outline gateway and billing separately, with access only where each grant can apply.

**Script:**

Inviting a contractor directly into gateway does not make that person the administrator of every payments project. Membership granted at a parent group can have a wider inherited scope, depending on configuration. Therefore an organization should not invite someone into the whole group simply because they need to read one repository. In the Members screen, trace whether access is direct, inherited, or supplied by another shared group before deciding what the user can do. The namespace alone is insufficient evidence of effective permission.

**Purpose:**

Close the ownership chapter by making grant scope an observable question.
