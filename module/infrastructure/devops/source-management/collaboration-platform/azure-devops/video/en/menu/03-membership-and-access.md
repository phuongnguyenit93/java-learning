---
video:
  url: ""
---

# Membership, Security Groups, and Effective Permissions

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

## Access Levels Versus Security Permissions

<!-- VIDEO_SECTION -->

### Scene 1 — Access Levels Versus Security Permissions

**Time:** `00:00–00:54`

**Visual:**

Azure DevOps Organization users access level dropdown Basic/Stakeholder beside Repos Git Security Read matrix.

Use authorized sandbox identities, redact real users, label documentation illustrations, and never change production permissions.

**Script:**

Put two settings for the same identity side by side: Basic or Stakeholder access level describes feature entitlement, while Read and Contribute are resource security permissions. A repo-level Allow doesn't automatically override an insufficient access tier. Compare Microsoft's private-repository Basic requirement and inspect the visible current values in a permitted test project. Don't change paid access entitlements simply to produce a teaching effect.

**Purpose:**

Separate feature entitlement from resource permission.

## Readers, Contributors, and Project Administrators: Default Permissions

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:54–01:05`

**Visual:**

Turn the Basic/Stakeholder access selector into a Readers/Contributors/Project Administrators group table.

**Script:**

Basic or Stakeholder governs features. Which default groups then govern actions in this project?

**Purpose:**

Move from feature entitlement to default security-group permission, without conflating them.

### Scene 1 — Readers, Contributors, and Project Administrators: Default Permissions

**Time:** `01:05–02:02`

**Visual:**

Security page groups Readers, Contributors, Project Administrators, show sample membership panel and rights table.

Use authorized sandbox identities, redact real users, label documentation illustrations, and never change production permissions.

**Script:**

Compare default Readers, Contributors, and Project Administrators groups. Readers focus on viewing, Contributors can work with permitted branches and PRs, and Project Administrators manage project configuration. These defaults are starting points, not a guarantee that every repository and branch behaves identically. Use a test identity to inspect effective permissions rather than infer rights from a group name; even admin labels should not be treated as automatic blanket bypass.

**Purpose:**

Use default groups as context, not as proof of effective rights.

## Direct Permissions Versus Security Group Membership

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:02–02:14`

**Visual:**

Select Alice in the group table and reveal direct permissions beside two inherited group grants.

**Script:**

An assigned group is only one source of permissions. Alice can also receive direct settings.

**Purpose:**

Expose the multiple origins of an actor's configured permission.

### Scene 1 — Direct Permissions Versus Security Group Membership

**Time:** `02:14–03:05`

**Visual:**

Permission UI: select Alice, expand Groups membership Contributors and Restricted; compare direct Allow, group Deny.

Use authorized sandbox identities, redact real users, label documentation illustrations, and never change production permissions.

**Script:**

Select illustrative Alice and expand her Contributors and Restricted group memberships. A permission may be granted directly or through several groups. At the same scope, a conflicting Deny normally overrides Allow, so a single green checkmark doesn't tell the whole story. Annotate where each permission comes from. When Alice cannot push, inspect group membership before changing repository security.

**Purpose:**

Trace direct/group sources before debugging effective access.

## Organization, Project, Repository, and Branch Permission Scopes and Inheritance

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:05–03:15`

**Visual:**

Stack Alice's permissions at organization, project, repo and main branch depth.

**Script:**

Let's map those grants across organization, project, repository and branch boundaries.

**Purpose:**

Show why a permission's scope matters as much as its source.

### Scene 1 — Organization, Project, Repository, and Branch Permission Scopes and Inheritance

**Time:** `03:15–04:07`

**Visual:**

Layered diagram organization/project/repository/main branch; Security tab for repo then Branches > main > Branch security.

Use authorized sandbox identities, redact real users, label documentation illustrations, and never change production permissions.

**Script:**

Show organization, project, repository, and main-branch scopes as nested layers. Repository rights can be inherited, while a branch may carry more specific settings. Compare repository Security against main Branch security for the same Alice identity. A parent Allow alone doesn't prove the child outcome. Record the exact resource scope first. This is security permission inheritance, not yet PR branch-policy evaluation.

**Purpose:**

Explain inherited repository permissions versus branch-specific scope.

## Allow, Deny, Not Set, and Effective Permissions

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:07–04:19`

**Visual:**

Magnify one Git security row with Allow, Deny and Not set badges against the branch scope stack.

**Script:**

The security page has three labels: Allow, Deny and Not set. What do they mean?

**Purpose:**

Introduce the configuration states before deciding whether an action is authorized.

### Scene 1 — Allow, Deny, Not Set, and Effective Permissions

**Time:** `04:19–05:18`

**Visual:**

Three-column annotated security grid Allow/Deny/Not set; compare effective permission view with one group-deny example.

Use authorized sandbox identities, redact real users, label documentation illustrations, and never change production permissions.

**Script:**

Freeze Allow, Deny, and Not set in the security grid. Allow grants at the configured scope; Deny restricts under the evaluation rules; Not set neither grants nor denies on its own. Show Alice with direct Not set but group Allow, then contrast a same-scope group Deny. Effective access is not determined by one blank cell. Avoid teaching the false universal rule that any Deny wins across every scope and inheritance boundary.

**Purpose:**

Dispel Not set and blanket Deny misconceptions.

## Relationship Between Explicit, Inherited, and Effective Permissions

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:18–05:28`

**Visual:**

Transform the three badges into explicit, inherited and effective permission columns for Alice.

**Script:**

A configured setting isn't always Alice's effective permission; inheritance also contributes.

**Purpose:**

Make the distinction between a displayed setting and the actual effective result visible.

### Scene 1 — Relationship Between Explicit, Inherited, and Effective Permissions

**Time:** `05:28–06:22`

**Visual:**

Same identity Alice: explicit repo row, inherited branch row, effective permissions diagnostics panel.

Use authorized sandbox identities, redact real users, label documentation illustrations, and never change production permissions.

**Script:**

Label three perspectives on one right: explicit means configured here; inherited means received from a parent or group; effective means the result evaluated for an action. A directly unset value can coexist with effective Allow from another source. Don't reverse-engineer the rules from cell colors alone. Where supported, inspect effective-permission diagnostics or ask the authorized administrator to verify the actual user and branch.

**Purpose:**

Separate configured, inherited, and evaluated permission.

## Git Repository Read, Contribute, and Administration Permissions

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:22–06:33`

**Visual:**

Keep Alice's effective permission column while opening Read, Contribute and Admin action rows.

**Script:**

Once we know the effective result, which concrete Git operations can Alice perform?

**Purpose:**

Translate the abstract permission evaluation into specific Git repository operations.

### Scene 1 — Git Repository Read, Contribute, and Administration Permissions

**Time:** `06:33–07:29`

**Visual:**

Repo security rights matrix: Read, Contribute, Create branch, Force push, Edit policies and Manage permissions highlighted separately.

Use authorized sandbox identities, redact real users, label documentation illustrations, and never change production permissions.

**Script:**

Inspect repo rights individually. Read supports viewing and cloning; Contribute governs updates where permitted; creating branches or tags may require separate rights. Edit policies and Manage permissions are administrative capabilities, while Force push can rewrite history or remove refs and should be restricted. A reader is not a writer, and a contributor who can push a feature branch does not automatically get to weaken main's governance.

**Purpose:**

Match each permission to a concrete action and risk.

## Interactions Among Deny, Inherited Rights, and Scope-Specific Settings

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:29–07:39`

**Visual:**

Overlay repository Deny and branch Allow examples onto the action table at their actual scopes.

**Script:**

A repository-level setting and a branch-specific setting may differ. Let's compare scopes.

**Purpose:**

Surface inheritance and specificity rather than asserting a blanket rule for every Deny.

### Scene 1 — Interactions Among Deny, Inherited Rights, and Scope-Specific Settings

**Time:** `07:39–08:33`

**Visual:**

Side-by-side repo inherited Deny and main explicit Allow; separate same-scope group Deny/Allow table with caution labels.

Use authorized sandbox identities, redact real users, label documentation illustrations, and never change production permissions.

**Script:**

Compare two different cases. First, conflicting group Allow and Deny at the same scope usually resolve in favor of Deny. Second, inherited settings from a parent resource can interact differently with explicit settings on a child branch. Therefore 'Deny always wins everywhere' and 'a user Allow always beats a group' are both oversimplified. Record the source, scope, and actual effective outcome before drawing conclusions.

**Purpose:**

Teach same-scope Deny versus inherited/child specificity.

## Contribute to Pull Requests Versus Branch Contribute Permissions

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:33–08:43`

**Visual:**

Switch from branch Contribute to Contribute to pull requests, beside a reviewer vote.

**Script:**

One right concerns changing branches; another concerns participating in PR discussions.

**Purpose:**

Prevent a review action from being mistaken for Git ref-write permission.

### Scene 1 — Contribute to Pull Requests Versus Branch Contribute Permissions

**Time:** `08:43–09:35`

**Visual:**

Security grid highlights Contribute to pull requests separately from Contribute; PR reviewer vote panel adjacent.

Use authorized sandbox identities, redact real users, label documentation illustrations, and never change production permissions.

**Script:**

An eligible reviewer can participate in PR discussions and voting through Contribute to pull requests without necessarily having branch Contribute rights to push code. These are distinct actions. Show the PR comment/vote surface next to the Git write-permission row. Reviewing a proposal doesn't grant the right to update main, and a visible PR action doesn't prove general repository write access.

**Purpose:**

Separate participation in PR review from writing Git refs.

## Diagnosing Effective Permissions for Repository Access Failures

<!-- VIDEO_SECTION -->

### Transition

**Time:** `09:35–09:45`

**Visual:**

Show a denied Git action next to the access-level, membership and effective-rights diagnostic tree.

**Script:**

When access fails, inspect entitlement, membership, permission and scope evidence.

**Purpose:**

Turn the completed permissions model into a usable investigation sequence.

### Scene 1 — Diagnosing Effective Permissions for Repository Access Failures

**Time:** `09:45–10:39`

**Visual:**

Flowchart missing Repos → service enablement → Basic access → project membership → repo Read/branch Contribute → effective rights; failed action annotations.

Use authorized sandbox identities, redact real users, label documentation illustrations, and never change production permissions.

**Script:**

Suppose Alice cannot see Repos, cannot clone, or gets a push rejection. These are different symptoms. Check service enablement and Services versus Server, feature access level for private source, project membership, repository Read, then branch Contribute and effective group overrides. Diagnose the exact actor and target before changing anything. Granting blanket Project Administrator rights is not an acceptable shortcut for a specific access issue.

**Purpose:**

Produce least-privilege permission diagnostic evidence.
