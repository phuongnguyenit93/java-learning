---
video:
  url: ""
---

# Repository Topology Foundations: Monorepo, Polyrepo, and Boundaries

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

## Repository Topology: Concept and Repository Organization Scope

<!-- VIDEO_SECTION -->

### Scene 1 — Repository Topology: Concept and Repository Organization Scope

**Time:** `00:00–00:54`

**Visual:**

Draw Web, Checkout API and Contracts; first enclose them in one Git-root boundary, then three separate Git roots. Keep the deployed-service diagram unchanged.

**Script:**

Look at the same three payment components in two source layouts. One has a shared Git root; the other has three separate histories. Neither changes the services running in production. Repository topology is about source boundaries, not container counts. Our path now examines ownership, cross-component changes, dependencies, release independence, read access, CI/tooling costs and evidence-based choices to split or consolidate repositories.

**Purpose:**

Learners must identify repository topology versus service arrangement using the displayed evidence, without substituting a repository-count assumption for a strategic decision.

## The Influence of Repository Boundaries on Collaboration and Change

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:54–01:12`

**Visual:**

Keep the previous evidence "repository topology versus service arrangement" in the left panel; reveal "why repository boundaries change coordination and access" on the right and animate the question that connects the two observations.

**Script:**

We have just inspected repository topology versus service arrangement. The next decision is different: we need to verify why repository boundaries change coordination and access. Follow the evidence as the view changes.

**Purpose:**

Bridge the previous result to the specific criterion why repository boundaries change coordination and access rather than presenting unrelated definitions.

### Scene 1 — The Influence of Repository Boundaries on Collaboration and Change

**Time:** `01:12–02:06`

**Visual:**

Show TAX-42 replacing totalAmount across Contracts, API and Web. Annotate PR review, history links and read-access edges in one-root and three-root layouts.

**Script:**

A request to rename totalAmount sounds simple until Contracts, API and Web must all change. One repository can reveal those edits in a coordinated diff, but still needs the right reviewers. Separate repositories preserve individual PR and release histories, yet require linked work and compatibility planning. The boundary changes where developers locate, review and authorize source changes; it cannot make the underlying software dependency disappear. Observe the necessary people and records in each layout.

**Purpose:**

Learners must identify why repository boundaries change coordination and access using the displayed evidence, without substituting a repository-count assumption for a strategic decision.

## Monorepo: Multiple Projects or Components in One Repository

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:06–02:24`

**Visual:**

Keep the previous evidence "why repository boundaries change coordination and access" in the left panel; reveal "one Git history hosting independent components" on the right and animate the question that connects the two observations.

**Script:**

We have just inspected why repository boundaries change coordination and access. The next decision is different: we need to verify one Git history hosting independent components. Follow the evidence as the view changes.

**Purpose:**

Bridge the previous result to the specific criterion one Git history hosting independent components rather than presenting unrelated definitions.

### Scene 1 — Monorepo: Multiple Projects or Components in One Repository

**Time:** `02:24–03:18`

**Visual:**

Reveal store/.git containing apps/web, services/api and libs/contracts. Give each component its own build-unit icon inside the shared source boundary.

**Script:**

A monorepo stores several meaningful projects or components under one version-control history. Web, API and Contracts can therefore appear together in one proposed source diff. That does not require the same programming language, build command or release date for every component. Trace the single Git-root border while keeping the component boundaries visible. This arrangement can improve discovery and coordinated changes, but the group still needs owners, scoped validation and adequate tooling.

**Purpose:**

Learners must identify one Git history hosting independent components using the displayed evidence, without substituting a repository-count assumption for a strategic decision.

## Polyrepo: Distributing Projects or Components Across Repositories

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:18–03:36`

**Visual:**

Keep the previous evidence "one Git history hosting independent components" in the left panel; reveal "separate histories and explicit consumer version adoption" on the right and animate the question that connects the two observations.

**Script:**

We have just inspected one Git history hosting independent components. The next decision is different: we need to verify separate histories and explicit consumer version adoption. Follow the evidence as the view changes.

**Purpose:**

Bridge the previous result to the specific criterion separate histories and explicit consumer version adoption rather than presenting unrelated definitions.

### Scene 1 — Polyrepo: Distributing Projects or Components Across Repositories

**Time:** `03:36–04:30`

**Visual:**

Split the tree into store-web.git, store-api.git and store-contracts.git. Label a Contracts 2.1 release while Web still declares 1.9.

**Script:**

In a polyrepo, Web, API and Contracts have distinct repositories, histories and permissions. A library maintainer might publish version 2.1 while Web still consumes 1.9. The interface needs a documented compatibility period rather than an assumption that every consumer has upgraded. This separation can support access isolation and different toolchains, but it does not guarantee runtime autonomy. Follow the separate source histories and actual dependency declarations to see the trade-off.

**Purpose:**

Learners must identify separate histories and explicit consumer version adoption using the displayed evidence, without substituting a repository-count assumption for a strategic decision.

## Repositories, Projects, Modules, and Packages: Distinct Boundaries

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:30–04:48`

**Visual:**

Keep the previous evidence "separate histories and explicit consumer version adoption" in the left panel; reveal "repository root versus product/build/language boundaries" on the right and animate the question that connects the two observations.

**Script:**

We have just inspected separate histories and explicit consumer version adoption. The next decision is different: we need to verify repository root versus product/build/language boundaries. Follow the evidence as the view changes.

**Purpose:**

Bridge the previous result to the specific criterion repository root versus product/build/language boundaries rather than presenting unrelated definitions.

### Scene 1 — Repositories, Projects, Modules, and Packages: Distinct Boundaries

**Time:** `04:48–05:42`

**Visual:**

Place four cards: Git repository root, Payments product, Gradle :billing module and Java com.shop.billing package. Draw non-one-to-one containment arrows.

**Script:**

Payments can be a product project, while :billing is a Gradle build module and com.shop.billing is a Java package namespace. None of those terms defines a version-control boundary. A single repository may contain several build modules, and one product may use multiple repositories. When someone proposes creating a new repo because a package grew, ask which source-history or access boundary is actually needed. Point to the Git root rather than treating each organization term as interchangeable.

**Purpose:**

Learners must identify repository root versus product/build/language boundaries using the displayed evidence, without substituting a repository-count assumption for a strategic decision.

## Repository Boundaries Versus Service and Deployment Boundaries

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:42–06:00`

**Visual:**

Keep the previous evidence "repository root versus product/build/language boundaries" in the left panel; reveal "source boundaries distinct from service and deployment units" on the right and animate the question that connects the two observations.

**Script:**

We have just inspected repository root versus product/build/language boundaries. The next decision is different: we need to verify source boundaries distinct from service and deployment units. Follow the evidence as the view changes.

**Purpose:**

Bridge the previous result to the specific criterion source boundaries distinct from service and deployment units rather than presenting unrelated definitions.

### Scene 1 — Repository Boundaries Versus Service and Deployment Boundaries

**Time:** `06:00–06:54`

**Visual:**

Keep one Git root around API/Web while showing two separate build artifacts and deployment clocks; reverse the example for one deployed product fed by several repositories.

**Script:**

The same source repository can hold Web and API while each produces a different artifact and deploys on a different schedule. That makes it a monorepo with potentially independent deployment units. Conversely, one delivered product can depend on source from several repositories. To evaluate operational independence, inspect runtime contracts, release records and deployment ownership, not the number of Git roots. This distinction prevents the misleading statement that every monorepo must be a monolith.

**Purpose:**

Learners must identify source boundaries distinct from service and deployment units using the displayed evidence, without substituting a repository-count assumption for a strategic decision.

## Relationship Among Ownership, Change Coordination, and Independence

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:54–07:14`

**Visual:**

Keep the previous evidence "source boundaries distinct from service and deployment units" in the left panel; reveal "ownership, cross-component coordination and release autonomy as separate tests" on the right and animate the question that connects the two observations.

**Script:**

We have just inspected source boundaries distinct from service and deployment units. The next decision is different: we need to verify ownership, cross-component coordination and release autonomy as separate tests. Follow the evidence as the view changes.

**Purpose:**

Bridge the previous result to the specific criterion ownership, cross-component coordination and release autonomy as separate tests rather than presenting unrelated definitions.

### Scene 1 — Relationship Among Ownership, Change Coordination, and Independence

**Time:** `07:14–08:08`

**Visual:**

Create a three-row evidence board: Contracts owner, reviewers for TAX-42, and whether Web/API deployments can differ. Attach an owner map, linked PRs and compatibility matrix respectively.

**Script:**

Before selecting a topology, ask who owns Contracts, which teams must coordinate an API change, and whether Web can release without waiting for API. One team may own several repos and still face interface dependencies; one repo may contain independently released services. Put an owner matrix, connected PR history and supported-version table beside these questions. The real decision comes from how those constraints interact, not a simplistic one-repository-per-team diagram.

**Purpose:**

Learners must identify ownership, cross-component coordination and release autonomy as separate tests using the displayed evidence, without substituting a repository-count assumption for a strategic decision.

## A Monorepo Does Not Imply a Monolithic Application

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:08–08:28`

**Visual:**

Keep the previous evidence "ownership, cross-component coordination and release autonomy as separate tests" in the left panel; reveal "two independent axes: Git topology and runtime packaging" on the right and animate the question that connects the two observations.

**Script:**

We have just inspected ownership, cross-component coordination and release autonomy as separate tests. The next decision is different: we need to verify two independent axes: Git topology and runtime packaging. Follow the evidence as the view changes.

**Purpose:**

Bridge the previous result to the specific criterion two independent axes: Git topology and runtime packaging rather than presenting unrelated definitions.

### Scene 1 — A Monorepo Does Not Imply a Monolithic Application

**Time:** `08:28–09:22`

**Visual:**

Draw a two-by-two matrix with mono/poly repository on one axis and monolithic/multiple services on the other. Populate all four cells with plausible API/Web examples.

**Script:**

Monolith describes packaging and runtime architecture. Monorepo describes where source history is managed. Multiple separately deployed services can share one repository; a single delivered application can be assembled from several source repositories. All four combinations in the matrix are possible. The label microservices therefore does not justify splitting the repo by itself. Ask whether a proposed change targets source governance, deployment coupling or the application design, and seek evidence for that layer.

**Purpose:**

Learners must identify two independent axes: Git topology and runtime packaging using the displayed evidence, without substituting a repository-count assumption for a strategic decision.

## Examples of Monorepo and Polyrepo Source Layouts

<!-- VIDEO_SECTION -->

### Transition

**Time:** `09:22–09:42`

**Visual:**

Keep the previous evidence "two independent axes: Git topology and runtime packaging" in the left panel; reveal "classification by actual Git roots and independent history" on the right and animate the question that connects the two observations.

**Script:**

We have just inspected two independent axes: Git topology and runtime packaging. The next decision is different: we need to verify classification by actual Git roots and independent history. Follow the evidence as the view changes.

**Purpose:**

Bridge the previous result to the specific criterion classification by actual Git roots and independent history rather than presenting unrelated definitions.

### Scene 1 — Examples of Monorepo and Polyrepo Source Layouts

**Time:** `09:42–10:36`

**Visual:**

Show A: store.git/{web,api,shared}, B: web.git/api.git/shared.git, and C: a submodule or package-registry dependency diagram. Count Git roots, not directories.

**Script:**

Diagram A contains three folders but only one Git root, so it is a monorepo. Diagram B has independent repositories, making it a polyrepo even if the IDE displays them under one workspace. Diagram C links repositories through a submodule or published package; that link does not merge their histories. To classify an unfamiliar system, verify each actual repository root and its separate commit history, then investigate the related read-access and review boundaries.

**Purpose:**

Learners must identify classification by actual Git roots and independent history using the displayed evidence, without substituting a repository-count assumption for a strategic decision.
