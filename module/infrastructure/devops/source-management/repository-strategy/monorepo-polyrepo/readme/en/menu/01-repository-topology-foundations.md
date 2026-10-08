<a id="back-to-top"></a>

# Repository Topology Foundations: Monorepo, Polyrepo, and Boundaries

## Menu
- [Repository Topology: Concept and Repository Organization Scope](#what-is-repository-topology)
- [The Influence of Repository Boundaries on Collaboration and Change](#why-repository-boundaries-matter)
- [Monorepo: Multiple Projects or Components in One Repository](#monorepo-definition)
- [Polyrepo: Distributing Projects or Components Across Repositories](#polyrepo-definition)
- [Repositories, Projects, Modules, and Packages: Distinct Boundaries](#repository-versus-project-module)
- [Repository Boundaries Versus Service and Deployment Boundaries](#repository-versus-service-deployment)
- [Relationship Among Ownership, Change Coordination, and Independence](#ownership-coordination-independence)
- [A Monorepo Does Not Imply a Monolithic Application](#monorepo-is-not-monolith)
- [Examples of Monorepo and Polyrepo Source Layouts](#recognize-topology-in-examples)

## <a id="what-is-repository-topology">Repository Topology: Concept and Repository Organization Scope</a>

<details>
<summary>Click for details</summary>

**Repository topology** describes how projects, modules and related components are grouped into source repositories. It defines boundaries for shared history, access and review; it does not decide how many applications run in production. The same checkout API, website and library could live in one repository or three without changing the runtime architecture.

Begin by drawing the components, their dependencies and who changes them together. Repository count is an outcome; the real question is which boundaries make coordination and governance sustainable.

**Learning route:** after separating monorepo/polyrepo from service and deployment architecture, inspect ownership, cross-component changes, shared dependencies and compatibility; then consider release independence, source-read access and governance, tooling/CI costs, and finally an evidence-backed decision to retain, split or consolidate repositories. Git commands, pipeline implementation and service architecture remain owned by their specialized modules.

</details>

- [Back to top](#back-to-top)

---

## <a id="why-repository-boundaries-matter">The Influence of Repository Boundaries on Collaboration and Change</a>

<details>
<summary>Click for details</summary>

A **repository boundary** separates histories, repository settings, access and review processes. If an API and web client often change together, splitting them can create linked changes to coordinate. A shared repository improves visibility but may expose unrelated source to a wider audience.

For a `customerId` rename affecting backend and frontend, measure linked PRs, cross-team waiting time and compatibility failures before claiming consolidation or separation would help. Neither topology replaces communication.

</details>

- [Back to top](#back-to-top)

---

## <a id="monorepo-definition">Monorepo: Multiple Projects or Components in One Repository</a>

<details>
<summary>Click for details</summary>

A **monorepo** stores multiple meaningful projects/components in **one repository and shared history**. For instance, `/apps/web`, `/services/api`, and `/libs/contracts` may build independently while sharing a Git root. A library edit and its consumers can appear in one reviewed change, and API usage examples are easier to find.

It still needs clear area ownership, affected-change validation and navigable paths. A common history **does not require** synchronized releases, identical tools or one runtime.

### References

- [Potvin and Levenberg (2016) — Google's monolithic repository case](https://research.google/pubs/why-google-stores-billions-of-lines-of-code-in-a-single-repository/)

</details>

- [Back to top](#back-to-top)

---

## <a id="polyrepo-definition">Polyrepo: Distributing Projects or Components Across Repositories</a>

<details>
<summary>Click for details</summary>

A **polyrepo** organizes projects/components into **separate repositories**, each with its own history, permissions and workflows. For example `web-ui.git`, `payments-api.git` and `contracts.git`; upgrading a shared library usually involves publishing a version and adopting it in consumers. It supports separate read-access boundaries and tool choices.

A single business request may then require multiple PRs arriving at different times. Treat repository separation as an intentional governance choice—not a mandatory one-repo-per-folder or per-service rule.

### References

- [Jaspan et al. (2018) — survey and developer-tool-log evidence](https://research.google/pubs/advantages-and-disadvantages-of-a-monolithic-codebase/)

</details>

- [Back to top](#back-to-top)

---

## <a id="repository-versus-project-module">Repositories, Projects, Modules, and Packages: Distinct Boundaries</a>

<details>
<summary>Click for details</summary>

A **project** is a development/product unit, a **module** is a build or software unit, and a **package** groups code within a language. A **repository** is the version-control boundary around any of these. One Git repository may contain several Gradle modules; one product may span repositories; a Java package is not automatically a repository.

If `billing-core` and `billing-web` are modules inside `billing.git`, their code dependency is separate from co-location. Label source, build and application boundaries explicitly when mapping the system.

</details>

- [Back to top](#back-to-top)

---

## <a id="repository-versus-service-deployment">Repository Boundaries Versus Service and Deployment Boundaries</a>

<details>
<summary>Click for details</summary>

A **service** exposes behavior at runtime; a **deployment unit** is packaged and released into an environment. Both are distinct from the **repository** boundary: one repo may hold several independently deployed services, while one deployable product can draw from multiple repos. Git records source; build and delivery systems determine release units.

For a repo holding web and worker code, web can release today and the worker next week if validation and contracts allow. Monorepo does not mean monolith, and polyrepo does not imply microservices.

</details>

- [Back to top](#back-to-top)

---

## <a id="ownership-coordination-independence">Relationship Among Ownership, Change Coordination, and Independence</a>

<details>
<summary>Click for details</summary>

Three linked but distinct questions drive topology: **who is accountable** for each area; **who coordinates** multi-area changes; and **which components must operate or release independently**. One team may own two repos yet coordinate interfaces weekly; one monorepo may assign paths to two teams with separate releases.

For a Platform-owned library used by API and Web, reviews need library and consumer context. An upgrade may be one shared change or multiple linked PRs. Base boundaries on actual interactions, not just the org chart.

</details>

- [Back to top](#back-to-top)

---

## <a id="monorepo-is-not-monolith">A Monorepo Does Not Imply a Monolithic Application</a>

<details>
<summary>Click for details</summary>

A **monolithic application** describes runtime packaging/coupling; a **monorepo** describes source placement. All combinations can occur: one application in one repo, one application assembled from several repos, independently deployed services in one repo, or services across many repos.

For example `/services/catalog` and `/services/payments` can share Git history but have independent endpoints and releases. Assess service architecture from runtime boundaries, contracts and delivery—not from the number of `.git/` directories.

</details>

- [Back to top](#back-to-top)

---

## <a id="recognize-topology-in-examples">Examples of Monorepo and Polyrepo Source Layouts</a>

<details>
<summary>Click for details</summary>

In layout A, `store.git/{web,api,shared}` has three directories and possibly three builds but one Git history: a **monorepo**. Layout B has `store-web.git`, `store-api.git` and `store-shared.git`: three independent histories, a **polyrepo**. Git submodules or package registries can link projects without erasing repository boundaries.

Verify where commits/PRs actually live and who can read those repositories. Folder names in an IDE and numbers of Docker containers do not determine topology.

</details>

- [Back to top](#back-to-top)
