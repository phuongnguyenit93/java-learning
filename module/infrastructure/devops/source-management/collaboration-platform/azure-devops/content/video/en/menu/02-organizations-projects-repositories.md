---
video:
  url: ""
---

# Organizations, Projects, and Git Repositories

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

## Azure DevOps Organization, Project, and Membership Hierarchy

<!-- VIDEO_SECTION -->

### Scene 1 — Azure DevOps Organization, Project, and Membership Hierarchy

**Time:** `00:00–00:50`

**Visual:**

Organization selector shows RetailCo, Project selector Checkout and Warehouse, Users page limited to permitted sandbox.

Use only an authorized sandbox or clearly attributed Microsoft documentation, without modifying a live organization's configuration.

**Script:**

Navigate from the illustrative RetailCo organization to Checkout and Warehouse projects. Organization membership doesn't imply equal rights in every project; access entitlement and security permissions still matter. The organization is the broader account and collaboration boundary, not a Git repository. Record the visible project and actor context in an authorized sandbox, and avoid exposing real membership data.

**Purpose:**

Establish org/project hierarchy and access context.

## Project Administration Boundaries Versus Git Repositories

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:50–01:01`

**Visual:**

Zoom from RetailCo organization selector into Checkout project settings, leaving the repository list below.

**Script:**

The organization contains projects, but project administration isn't identical to controlling one repository.

**Purpose:**

Show why project administration is a broader boundary than one Git repository.

### Scene 1 — Project Administration Boundaries Versus Git Repositories

**Time:** `01:01–01:48`

**Visual:**

Project Settings navigation and Repositories list; compare Checkout project settings and checkout-api repository Security.

Use only an authorized sandbox or clearly attributed Microsoft documentation, without modifying a live organization's configuration.

**Script:**

Place Project settings beside checkout-api's repository Security view. Projects organize members and services; a repository has its own Git history, settings, and permissions, with potentially more specific branch rights. The project administration role does not make every operation identical for every user. Always verify the selected repository before interpreting a disabled action.

**Purpose:**

Contrast project-wide management with repository and branch scope.

## Multiple Git Repositories Within One Project

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:48–01:58`

**Visual:**

Expand checkout-api and checkout-web as independent Git graphs under the same Checkout project.

**Script:**

One project can own several repositories. Do they share one Git history?

**Purpose:**

Separate shared project administration from each repository's own history.

### Scene 1 — Multiple Git Repositories Within One Project

**Time:** `01:58–02:46`

**Visual:**

Checkout project Repos dropdown choosing checkout-api then checkout-web; show two different branch lists.

Use only an authorized sandbox or clearly attributed Microsoft documentation, without modifying a live organization's configuration.

**Script:**

In the Checkout project, switch between checkout-api and checkout-web. Their branch lists, histories, and repository settings are separate despite the shared project context. That separation supports different codebases but makes cross-repo changes a coordination concern. We're not choosing monorepo versus polyrepo here; we're learning to identify the exact repo before opening a PR.

**Purpose:**

Show multiple repositories do not share a single Git history.

## Administrative Roles in Creating, Sharing, and Managing Repositories

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:46–02:57`

**Visual:**

Hold the two repository tiles and reveal Create repository versus Manage permissions controls.

**Script:**

Now distinguish the permission to create repositories from the permission to manage them.

**Purpose:**

Make the learner ask which role governs creation and which role governs ongoing access.

### Scene 1 — Administrative Roles in Creating, Sharing, and Managing Repositories

**Time:** `02:57–03:50`

**Visual:**

Compare Project Settings > Repositories security role Create repository with repo Security > Manage permissions / Edit policies.

Use only an authorized sandbox or clearly attributed Microsoft documentation, without modifying a live organization's configuration.

**Script:**

A contributor may be able to work on allowed branches without being permitted to create repositories or edit policies. Compare the project-level Create repository permission with repository or branch-level Manage permissions and Edit policies. Project Administrators often manage configuration, but always inspect effective permissions for the actual resource. This is a read-only walkthrough; don't mutate production security settings for a video.

**Purpose:**

Distinguish creation, management, and code contribution privileges.

## Project Visibility Versus Repository Access

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:50–04:01`

**Visual:**

Slide the role matrix toward Project visibility and repository Read, with a question mark between them.

**Script:**

Administrative access and project visibility still don't tell us every reader's Git rights.

**Purpose:**

Distinguish project audience from effective permission to inspect a particular repo.

### Scene 1 — Project Visibility Versus Repository Access

**Time:** `04:01–04:51`

**Visual:**

Read Project settings Overview visibility and selected private repo effective Read panel; display two-row truth table.

Use only an authorized sandbox or clearly attributed Microsoft documentation, without modifying a live organization's configuration.

**Script:**

For a private project, outsiders generally need the right authenticated access before viewing the code. Yet project membership doesn't automatically authorize every repository action. Draw a two-column distinction: project visibility governs who can enter the project boundary, while repository security governs reading or contributing to code. A visibility badge alone is never proof of push permission.

**Purpose:**

Separate project visibility from repository permissions.

## Azure DevOps Services Public Projects: New Creation Ends in 2026 and Private Conversion in 2027

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:51–05:02`

**Visual:**

Keep the visibility indicator on screen and introduce Microsoft's 2026–2027 public-project retirement notice.

**Script:**

Visibility itself is changing for Azure DevOps Services public projects in 2026 and 2027.

**Purpose:**

Connect the visibility choice to its dated Azure DevOps Services lifecycle constraint.

### Scene 1 — Azure DevOps Services Public Projects: New Creation Ends in 2026 and Private Conversion in 2027

**Time:** `05:02–05:55`

**Visual:**

Microsoft Learn public-projects-retirement page showing 2026 retirement and 2027 private conversion; badge Services only; source URL visible.

Use only an authorized sandbox or clearly attributed Microsoft documentation, without modifying a live organization's configuration.

**Script:**

Show Microsoft's Public projects retirement notice with the 2026 retirement and scheduled conversion of remaining public projects to private in 2027. This is an Azure DevOps Services policy, not a Git behavior and not automatically a Server feature. Read the legacy Allow public projects organization-policy caveat rather than assuming every organization presents the same UI. Preserve the dated source link on screen.

**Purpose:**

Teach dated Services-only retirement and legacy policy caveat accurately.

## Effects of Enabling or Disabling Azure Repos

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:55–06:07`

**Visual:**

Fold the retirement notice into a Services panel; highlight Azure Repos enabled and the navigation sidebar.

**Script:**

Even a permitted project can show no Repos tab. Could the Repos service be disabled?

**Purpose:**

Explain that a missing Repos tab can reflect a disabled service rather than a permission denial.

### Scene 1 — Effects of Enabling or Disabling Azure Repos

**Time:** `06:07–07:03`

**Visual:**

Project Settings > Overview > Services showing Repos enabled toggle, then navigation sidebar with Repos absent in disabled example; no actual toggling.

Use only an authorized sandbox or clearly attributed Microsoft documentation, without modifying a live organization's configuration.

**Script:**

A missing Repos navigation item doesn't automatically prove Read permission was denied. The project may have disabled the Repos service, hiding its workflows. Inspect the enabled/disabled state with authorized documentation or a controlled sandbox capture; don't toggle a real team's service merely for a demonstration. Only after confirming Repos is enabled should we diagnose user-level repository rights. Missing features and denied permissions are different failure modes.

**Purpose:**

Differentiate service enablement from access denial.

## Evidence of Project, Repository, and Administration Boundaries

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:03–07:13`

**Visual:**

Collapse RetailCo → Checkout → checkout-api into one labeled breadcrumb evidence worksheet.

**Script:**

Bring together the hierarchy, service state and repository breadcrumb before diagnosing access.

**Purpose:**

Prepare the scope-specific facts needed for a later permissions diagnosis.

### Scene 1 — Evidence of Project, Repository, and Administration Boundaries

**Time:** `07:13–08:06`

**Visual:**

Walk through RetailCo > Checkout > checkout-api with breadcrumb, Repositories list and target main; then switch to Warehouse and highlight changed breadcrumb.

Use only an authorized sandbox or clearly attributed Microsoft documentation, without modifying a live organization's configuration.

**Script:**

Record organization, project, repository, and intended main target branch from a permitted sandbox. Switch to Warehouse and watch the breadcrumb change even though the signed-in identity stays the same. Every screenshot of a policy must carry its scope; otherwise two different repository settings can look deceptively comparable. That scoped evidence is what we'll need before investigating access levels, security groups, and inheritance.

**Purpose:**

Build a scope-labeled evidence inventory for later permission diagnosis.
