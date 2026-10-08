---
video:
  url: ""
---

# Members, Roles, and Access Permissions

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

## Guest, Planner, Reporter, Developer, Maintainer, and Owner Roles

<!-- VIDEO_SECTION -->

### Scene 1 — Guest, Planner, Reporter, Developer, Maintainer, and Owner Roles

**Time:** `00:00–01:01`

**Visual:**

Build a six-row Guest/Planner/Reporter/Developer/Maintainer/Owner matrix against issue planning, reading source, contributing code, and project administration. Annotate tier and group/project context.

**Script:**

Do not treat these role names as an unlimited ladder where higher always means every action is available. Guest supports limited collaboration, Planner covers planning-related work where offered, Reporter commonly reads repository content, and Developer contributes code within applicable branch rules. Maintainer has broader project management responsibility, while Owner authority has particular group and project contexts. Binh may need review access, An a work-branch contribution path, and Chi configuration responsibility. Select grants for tasks and verify actual entitlements rather than assigning Owner as a shortcut.

**Purpose:**

Establish role intent while preserving the distinctions between features and actual permissions.

## Direct, Inherited, and Shared Project Membership

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:01–01:14`

**Visual:**

Keep the previous scene’s evidence as a smaller left panel, highlight "choose roles for tasks, not prestige", then reveal the next question on the right with a scope-change arrow.

**Script:**

A role label alone does not reveal the path by which the person received that permission.

**Purpose:**

Bridge the earlier observation about choose roles for tasks, not prestige to the next decision about membership can arrive through several independent paths without resetting the case.

### Scene 1 — Direct, Inherited, and Shared Project Membership

**Time:** `01:14–02:12`

**Visual:**

Show Direct project invitation, Inherited parent group, and Shared group feeding one gateway access card. Remove the Direct arrow in the diagram while leaving other paths active.

**Script:**

Binh might have a direct gateway membership and also inherit access from a parent payments group. Removing the direct project invitation would not necessarily remove effective access if the inherited or shared-group route remains. These memberships have different administrators and lifecycles. Instead of immediately concluding that access was revoked, inspect the Members interface and its membership source indicators. Our animation changes only arrows on a diagram; it does not assert that a live person's GitLab permissions were altered.

**Purpose:**

Illustrate why access removal requires tracing all grant sources, not only the direct member list.

## Effective Project Permissions and Group Governance Scopes

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:12–02:25`

**Visual:**

Keep the previous scene’s evidence as a smaller left panel, highlight "membership can arrive through several independent paths", then reveal the next question on the right with a scope-change arrow.

**Script:**

Multiple grants can explain access, but a particular operation also depends on its destination.

**Purpose:**

Bridge the earlier observation about membership can arrive through several independent paths to the next decision about effective permission includes target-specific rules without resetting the case.

### Scene 1 — Effective Project Permissions and Group Governance Scopes

**Time:** `02:25–03:26`

**Visual:**

Show An as a Developer inherited from a parent group. Compare actions push fix-rounding and push protected main with a branch-rule overlay; leave actual results conditional.

**Script:**

An can have the Developer role through a parent group and still be unable to push directly to a protected main branch. The effective action depends on both membership and the rule applying to the target. Similarly, appearing within a group does not prove the person may edit all project Settings. For a denied action, note the exact project, actor, branch, and message before changing membership. This distinction prevents a team from granting broad roles to solve what was actually a branch governance requirement.

**Purpose:**

Connect project membership to branch-specific restrictions rather than implying unrestricted write access.

## Permissions to View, Propose, Review, and Merge Changes

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:26–03:39`

**Visual:**

Keep the previous scene’s evidence as a smaller left panel, highlight "effective permission includes target-specific rules", then reveal the next question on the right with a scope-change arrow.

**Script:**

We now know the actor's role; the MR workflow still contains several separately authorized operations.

**Purpose:**

Bridge the earlier observation about effective permission includes target-specific rules to the next decision about read, propose, approve and merge are different operations without resetting the case.

### Scene 1 — Permissions to View, Propose, Review, and Merge Changes

**Time:** `03:39–04:41`

**Visual:**

Use a four-column View source / Create MR / Approve / Merge main matrix. Route a contractor through a fork, Binh toward review, Chi toward protected-target merge checks.

**Script:**

Someone able to read a public project may propose work from a fork without upstream push access, when policy permits it. A requested reviewer does not automatically produce an eligible approval counted by a required rule. A Maintainer may have merge authority yet still need to satisfy the active approval requirements. Walk across four separate questions: can this person view source, create a proposal, submit a qualifying approval, and merge into this target? Each deserves evidence from permissions and MR rules, not a single role badge.

**Purpose:**

Prevent conflation of contribution route, review eligibility, and merge authority.

## Missing Project Access and Excessive Permissions

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:41–04:54`

**Visual:**

Keep the previous scene’s evidence as a smaller left panel, highlight "read, propose, approve and merge are different operations", then reveal the next question on the right with a scope-change arrow.

**Script:**

When access does not behave as expected, the safe next step is evidence gathering, not broadening roles.

**Purpose:**

Bridge the earlier observation about read, propose, approve and merge are different operations to the next decision about diagnose first; escalate privileges last without resetting the case.

### Scene 1 — Missing Project Access and Excessive Permissions

**Time:** `04:54–05:53`

**Visual:**

Draw a failure tree for invisible project, denied push, and blocked merge. Map each to visibility/membership, branch rules, or review/check requirements; cross out default Admin escalation.

**Script:**

A contractor who only tracks Issues does not need Maintainer rights as a convenience. If the project is invisible, inspect visibility and membership. If a push is denied, check the source branch and protection rule. If Merge is unavailable, examine the target permission, required approvals, and checks before calling it an access defect. Widening privileges without diagnosis can weaken controls and still fail to fix the actual cause. Capture the displayed message and choose a narrowly authorized contribution or review path.

**Purpose:**

Provide a practical least-privilege diagnosis across three distinct failure classes.
