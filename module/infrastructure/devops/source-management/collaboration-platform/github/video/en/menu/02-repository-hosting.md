---
video:
  url: ""
---

# Organizing and Sharing GitHub Repositories

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

## Personal vs Organization-Owned Repositories

<!-- VIDEO_SECTION -->

### Scene 1 — Personal vs Organization-Owned Repositories

**Time:** `00:00–00:56`

**Visual:**

Compare alice/demo and orchid/payments. Expand the organization repository into a Billing team and access matrix; mark an ownership transfer with an integrations-review checklist.

**Script:**

A personal repository makes sense for an individual experiment. When an entire team must maintain a payments service, ownership needs to outlive any one developer. An organization can grant access through teams and make ongoing responsibility clearer. Moving a repository to a different owner should also trigger a check of URLs, permissions, and integrations; the change is more than a new label. For our case, keep payments under the organization and ask who may administer it and who merely contributes.

**Purpose:**

Connect personal versus organization ownership to continuity and access governance.

## Public, Private, and Enterprise-Wide Internal Visibility on GitHub Enterprise Cloud

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:56–01:07`

**Visual:**

Leave alice/demo and orchid/payments owner labels visible; overlay three visibility audiences, emphasizing members of other organizations within the enterprise for Internal.

**Script:**

Ownership names a steward, but who can actually read the contents? Visibility answers a different question.

**Purpose:**

Bridge ownership to audience scope and highlight why Internal does not mean only the owning team.

### Scene 1 — Public, Private, and Enterprise-Wide Internal Visibility on GitHub Enterprise Cloud

**Time:** `01:07–02:04`

**Visual:**

Reveal Public, Private, and Internal audience diagrams. Put a member of a second organization inside the same enterprise within the Internal audience; keep outsiders outside. Annotate Enterprise Cloud availability.

**Script:**

Public repositories can be read across the Internet. Private access is limited to authorized viewers. Internal visibility on GitHub Enterprise Cloud is different: members of the enterprise can read an internal repository even when they belong to another organization within that enterprise. Internal therefore does not mean 'my team only'. Plan, enterprise model, and policy still matter. Before using a visibility setting for sensitive work, identify the intended readers and verify the actual access boundaries instead of relying on the label.

**Purpose:**

Clarify the distinctive enterprise scope of Internal visibility without inventing entitlement.

## Repository Details, Sharing Settings, and Default Branch

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:04–02:15`

**Visual:**

Turn the visibility circles into a repository Settings outline; trace a line from default branch to the selected Code tree and suggested PR base.

**Script:**

Once we know the audience, what repository settings guide the default browsing and contribution experience?

**Purpose:**

Move from who can view the repository to the navigation defaults those viewers encounter.

### Scene 1 — Repository Details, Sharing Settings, and Default Branch

**Time:** `02:15–03:10`

**Visual:**

Mock Settings → General with owner, description, visibility, and default branch. Highlight the selected Code tree and suggested PR base when switching main to another name; leave deployment in a separate lane.

**Script:**

The default branch helps determine what appears first in the Code view and which base GitHub commonly proposes for new pull requests. It is not automatic evidence of what runs in production. Changing the default branch can affect navigation and workflows that depend on a branch name. Before making that change, inspect documentation links, PR conventions, and integrations. In our payments repository, the setting is a collaboration default; a release or deployment still needs its own evidence.

**Purpose:**

Show default-branch effects without implying CI/CD or production state.

## Repository Discovery, Visibility, and Contribution Permissions

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:10–03:21`

**Visual:**

Keep the main default-branch label above a three-column Find / Read / Push matrix; leave Write decisions unfilled.

**Script:**

Visibility answers who can see source. It does not tell us how that person may contribute.

**Purpose:**

Expose the remaining permission question after explaining repository visibility and default navigation.

### Scene 1 — Repository Discovery, Visibility, and Contribution Permissions

**Time:** `03:21–04:15`

**Visual:**

Build a Find / Read / Push matrix for a public outsider, an authorized private reader, and an internal enterprise member. Reveal each capability separately, leaving proposal eligibility dependent on settings.

**Script:**

A visitor may read a public repository but still be unable to push into main. Depending on repository policy they may propose a change through a fork and pull request. Someone authorized to read a private repository does not thereby gain Settings access. Enterprise internal visibility likewise addresses reading rather than granting Write. When a contributor reports a problem, diagnose discovery, read access, and contribution permission separately. Broad Admin access is not the appropriate shortcut.

**Purpose:**

Prevent inference of push or administrative rights from visibility alone.
