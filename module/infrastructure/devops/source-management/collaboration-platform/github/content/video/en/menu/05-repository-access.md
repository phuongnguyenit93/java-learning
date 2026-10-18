---
video:
  url: ""
---

# Repository Access and Organization Membership

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

## Organizations, Teams, and Outside Collaborators on GitHub

<!-- VIDEO_SECTION -->

### Scene 1 — Organizations, Teams, and Outside Collaborators on GitHub

**Time:** `00:00–00:55`

**Visual:**

Diagram the Orchid organization, Billing team, and payments repository. Put an outside collaborator beyond the org border with an access arrow only to the selected repository.

**Script:**

An organization can manage many people and repositories. Teams help maintain shared permissions without individually inviting everyone to every project. An outside collaborator may have access to selected organization repositories without becoming a normal organization member. In our payments example, Mai needs Binh to inspect the invoice change, so she checks team membership, effective repository access, and any relevant organization policy. A name in a team or a requested review does not imply blanket access across the organization.

**Purpose:**

Illustrate scoped organization/team access and the special outside-collaborator boundary.

## Read, Triage, Write, Maintain, and Admin Roles

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:55–01:06`

**Visual:**

Hold the organization/team/outside-collaborator map, then populate a Read / Triage / Write / Maintain / Admin role matrix beside it.

**Script:**

Permission is now scoped; which repository role should match each task?

**Purpose:**

Move from where access comes from to which actions each repository role supports.

### Scene 1 — Read, Triage, Write, Maintain, and Admin Roles

**Time:** `01:06–02:01`

**Visual:**

Reveal a Read/Triage/Write/Maintain/Admin role matrix against viewing source, managing issues, pushing code, operating the repo, and changing access. Label it a decision aid subject to actual policy.

**Script:**

Start with the work someone must do. Read is suited to viewing source; Triage can manage certain issue and pull-request work without code write access; Write supports contributing code. Maintain grants broader operational management but not all sensitive administrative actions. Admin covers more sensitive repository control and should be granted carefully. Exact capabilities depend on the repository context and organization policy. Our matrix is a least-privilege reasoning aid, not a claim about one person's current effective grants.

**Purpose:**

Connect the five role names to responsibilities and careful permission selection.

## Permissions for Contributing, Requesting Reviews, and Maintaining Repositories

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:01–02:12`

**Visual:**

Fade the role matrix into concrete actions: inspect PR, push head, request review, change Settings and merge a protected base.

**Script:**

Having access is not the same as being allowed to perform every PR or Settings action.

**Purpose:**

Show why a user's effective permissions must be checked against the specific intended action and target rules.

### Scene 1 — Permissions for Contributing, Requesting Reviews, and Maintaining Repositories

**Time:** `02:12–03:06`

**Visual:**

Build an action checklist: view PR, request reviewer, push head, edit Settings, merge protected base. Attach both effective actor permission and target-rule checks to the merge row.

**Script:**

Ask three separate questions: who may see this repository, who may contribute changes, and who may administer settings? Receiving a review request does not make Binh the owner. An may be able to push a work branch while still failing the requirements for merging into main. Mai's maintenance role also has limits. When an action is denied, record the actor, target branch, and actual message. Diagnose role permissions alongside applicable branch rules before granting wider access.

**Purpose:**

Prevent confusion between contribution, review invitation, administration and merge eligibility.

## Missing Access, Excessive Permissions, and Unclear Ownership

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:06–03:17`

**Visual:**

Highlight a denied push action and contrast a broad Admin grant with a targeted permission-and-branch diagnostic.

**Script:**

An access failure can tempt a team to grant broad Admin rights. What new risk does that create?

**Purpose:**

Turn role and action boundaries into an evidence-based response to access failures.

### Scene 1 — Missing Access, Excessive Permissions, and Unclear Ownership

**Time:** `03:17–04:13`

**Visual:**

Split into 'Grant Admin to make it work' with risk callouts and 'Inspect actor/branch/rule first' with a diagnostic checklist. No fake successful permission result is shown.

**Script:**

An reports a rejected push. Giving Admin access might hide the immediate symptom, but it also expands the ability to modify sensitive settings or permissions. Instead, find the specific target, effective role, branch restriction, and required review condition. Sometimes the problem is acceptance policy, not permission to write source. Orchid should identify owners for access changes, review approvals, and temporary grants, and periodically remove unnecessary access. Least privilege is an operating discipline, not merely a set of role labels.

**Purpose:**

Conclude with a safe diagnostic and governance habit rather than permission escalation.
