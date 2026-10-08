---
video:
  url: ""
---

# Access and Repository Governance

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

## Workspace, Project, and Repository Permission Scopes

<!-- VIDEO_SECTION -->

### Scene 1 — Workspace, Project, and Repository Permission Scopes

**Time:** `00:00–01:30`

**Visual:**

Zoom from workspace `orchid-team` to project Billing and `invoice-api`; display separate panes for workspace access, project permissions, and repository permissions.

**Script:**

Belonging to a workspace does not automatically grant write access to every repository. Bitbucket Cloud uses multiple permission scopes because Orchid may want accounting to read the source, developers to update `invoice-api`, and Mai to govern Billing settings. If An is denied an operation, record exactly what he attempted: reading a repository, pushing a branch, or changing settings. Those are different checks. Do not infer edit rights from the fact that a workspace appears in the user's navigation. Inspect project and repository access at the relevant scope.

**Purpose:**

Make effective permission scope a prerequisite to diagnosing access.



## Users, Groups, and Administrators

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:30–01:42`

**Visual:**

Keep Workspace/Project/Repository scopes visible; place billing-developers and Mai in their actual management lanes, leaving inherited admin blank.

**Script:**

Rights apply at workspace, project, and repository scopes. Who receives those grants, and how do people and groups differ from administrators?

**Purpose:**

Connect permission scope to user/group responsibility without assuming full administrative inheritance.

### Scene 2 — Users, Groups, and Administrators

**Time:** `01:42–03:12`

**Visual:**

Show group `billing-developers` in project access, An and Binh as members, and Mai as a scoped administrator. Animate one member leaving the group.

**Script:**

Direct user grants can handle small exceptions, but become difficult to maintain for a changing team. A developers group gives Orchid a manageable way to apply an intended permission set and revoke it as membership changes. Administrative authority also has boundaries: an administrator of a project is not necessarily an Atlassian organization administrator. For a recording, use demonstration users, conceal real email addresses, and inspect the UI's current role labels rather than relying on someone's job title.

**Purpose:**

Explain permission groups and scoped administration without assuming universal admin roles.



## Workspaces Managed in Bitbucket Versus Atlassian Administration

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:12–03:24`

**Visual:**

Split the admin view into older Bitbucket-managed and Atlassian-administered workspace screens while retaining Mai's membership context.

**Script:**

Now we need to find the right administration screen for this particular workspace.

**Purpose:**

Prepare learners for differing administration surfaces without assuming every workspace uses the legacy UI.

### Scene 3 — Workspaces Managed in Bitbucket Versus Atlassian Administration

**Time:** `03:24–04:54`

**Visual:**

Side-by-side cards `Legacy Bitbucket-managed workspace` and `Atlassian-administered workspace`; highlight organizational application access separately from repository/project access.

**Script:**

Bitbucket Cloud workspaces are not all administered through the same screens. Older workspaces may retain user management inside Bitbucket, while newly provisioned workspaces use Atlassian Administration for organization and product access. The workspace, project, and repository hierarchy still matters. But a current user may not see the access menu shown in an older tutorial. Identify the workspace's administration environment first; distinguish app access from project or repository rights. Never widen an account's permissions simply to force the interface to match a screenshot.

**Purpose:**

Prevent navigation mistakes across two genuine Bitbucket workspace administration models.



## Effects of Project Permissions on Member Repositories

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:54–05:06`

**Visual:**

Apply a Billing project Write card over invoice-api and billing-docs and annotate current and future repository coverage.

**Script:**

What happens to member repositories when access is granted at the project level?

**Purpose:**

Demonstrate the breadth of project-level grants and why a repo-specific need can be overprovisioned.

### Scene 4 — Effects of Project Permissions on Member Repositories

**Time:** `05:06–06:36`

**Visual:**

Show Billing with `invoice-api` and `billing-docs`, assign a `billing-developers` Write project card, and highlight both repositories. Display project levels Read, Write, Create, Admin.

**Script:**

Project permissions can affect existing repositories and future repositories created in that project. For Orchid, giving developers Write on all of Billing could be broader than intended when they only need to edit the invoice API. Atlassian documents project permission levels such as Read, Write, Create, and Admin, but they are not a universal four-item table for every repository setting. In the capture, inspect both repositories after setting up a project role; the observable lesson is how a convenient project-wide grant broadens the effective access surface.

**Purpose:**

Show permission inheritance and why convenient group access can create unintended reach.



## Read, Write, Admin, and Visibility Boundaries

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:36–06:48`

**Visual:**

Retain the two inherited repository grants, then compare Public/Private visibility against Clone, Push and Settings operations.

**Script:**

Project grants explain some actions, but visibility and modification rights are not equivalent.

**Purpose:**

Separate read visibility from write and administration after examining project-level inheritance.

### Scene 5 — Read, Write, Admin, and Visibility Boundaries

**Time:** `06:48–08:18`

**Visual:**

Compare demonstration public and private repository pages with separate `Read/Clone`, `Push`, and `Manage settings` icons; hide credentials.

**Script:**

Read permissions concern source visibility and retrieval; Write concerns contributions; Admin concerns settings within the given scope. Project scope also defines a Create level for creating repositories. However, a public repository being readable does not make it writable by anonymous users. A private repository does not become readable to every workspace member. Test reads and modifications separately with appropriate nonprivileged demo users and record the actual result; a privileged administrator token would not demonstrate ordinary access.

**Purpose:**

Teach public/private visibility as distinct from per-operation authorization.



## Recognizing Excessive or Missing Permissions

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:18–08:30`

**Visual:**

Transform the role matrix into an Actor / Operation / Repo / Branch / Error worksheet for An's blocked main push.

**Script:**

What evidence helps us tell insufficient permission from excessive permission?

**Purpose:**

Teach evidence-based permission troubleshooting instead of immediately escalating roles.

### Scene 6 — Recognizing Excessive or Missing Permissions

**Time:** `08:30–10:00`

**Visual:**

Use a five-column diagnostic sheet `Actor / Action / Repo and branch / Exact message / Effective rule`; fill a denied merge to `main` and an excessive Admin grant.

**Script:**

An may be able to open a PR but still lack permission to merge into a protected branch. That is not necessarily a workspace membership problem. At the other extreme, someone with a read-only responsibility might have enough rights to edit sensitive settings. Mai should capture the actor, operation, target repository and branch, and the exact observed denial or unexpected success. Then inspect project, repository and branch-level conditions before changing anything. Granting Admin or disabling all restrictions first destroys useful evidence and increases risk.

**Purpose:**

Provide a safe access-diagnosis workflow and hand off to pull requests.
