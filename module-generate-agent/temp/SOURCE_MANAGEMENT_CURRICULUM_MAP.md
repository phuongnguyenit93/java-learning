# Source Management Curriculum Map

Status: **STEP 1 LOCKED**

Target area:

```text
module/infrastructure/devops/source-management
```

Repository baseline:

```text
Primary source-control system     Git
Collaboration platforms          GitHub / GitLab / Bitbucket / Azure DevOps (Azure Repos emphasis)
Target learner                   Java/backend developer learning practical DevOps
Learning-module type             LIBRARY / concept-and-workflow oriented
```

This Curriculum Map owns the **area-level learning architecture** for Source Management. It defines canonical module inventory, primary concept ownership, boundaries, dependencies, recommended learning order, terminology ownership and cross-area handoff. It deliberately does not define Roadmap milestones, H2/menu identities, Knowledge bodies, API experiments, Quiz questions or Interview questions.

---

## 1. Area purpose

The Source Management area teaches how software source moves from an individual developer's working copy into a durable, reviewable and team-governed source history.

The learner should leave the area with this mental model:

```text
source files change locally
        ↓
version control records intentional history
        ↓
remote repositories make that history shareable
        ↓
collaboration platforms add review / permissions / governance around repositories
        ↓
repository strategy defines how teams organize branches and repository boundaries
        ↓
delivery automation consumes the governed source state
```

This area is therefore about **source history, repository collaboration and repository-level team strategy**. It is not the owner of CI/CD pipelines, build systems, artifact repositories, deployment, runtime infrastructure, generic security infrastructure or software-process methodology.

---

## 2. Learner profile and prerequisites

Primary learner:

- a developer who can work with files/directories and a command-line shell;
- understands the idea of source code and project files;
- may be completely new to Git and hosted repository collaboration;
- eventually needs team-level reasoning about branch and repository strategy.

No Java-specific prerequisite is required. The area should remain useful across backend projects even though the repository's primary learner is a Java/Spring developer.

Recommended neighboring context:

```text
DevOps fundamentals
→ explains the broader source-to-production feedback loop

software-process methodology
→ may explain Scrum/Kanban/team process, but is not required before basic Git

delivery-automation
→ comes after governed source management when the learner begins CI/CD
```

---

## 3. Canonical module inventory

The current Source Management curriculum contains exactly these eight real learning modules:

| Projection order | Module | Role | Primary responsibility |
| ---: | --- | --- | --- |
| 10 | `fundamentals` | Foundation | Source-management mental model; version control vs repository hosting vs repository strategy; local/remote/source-of-truth orientation; lifecycle handoff to delivery automation |
| 20 | `source-control/git` | Core mechanism | Git repository/history model, working tree/index/repository, commits, refs/branches, merge/rebase, remotes, tags, undo/recovery and Git-native collaboration mechanics |
| 30 | `collaboration-platform/github` | Peer platform specialization / recommended first | GitHub repository hosting and collaboration: repositories, pull requests, reviews, permissions, branch/ruleset protection, issues/releases and organization/team workflows |
| 40 | `collaboration-platform/gitlab` | Peer platform specialization | GitLab repository hosting and collaboration: projects/groups, merge requests, reviews/approvals, protected branches, permissions, issues/releases; CI/CD only as boundary context |
| 50 | `collaboration-platform/bitbucket` | Peer platform specialization | Bitbucket workspaces/projects/repositories, pull requests, review, branch restrictions, permissions and repository collaboration; Pipelines only as boundary context |
| 60 | `collaboration-platform/azure-devops` | Peer platform specialization | Azure DevOps collaboration with emphasis on Azure Repos: projects/repos, pull requests, reviews, branch policies and permissions; Azure Pipelines remains delivery automation |
| 70 | `repository-strategy/branching-strategy` | Team strategy | Trunk-based development, GitHub Flow, Git Flow and related branch/integration/release strategy, branch lifetime, merge-policy trade-offs and governance intent |
| 80 | `repository-strategy/monorepo-polyrepo` | Architecture / advanced strategy | Repository topology: monorepo vs polyrepo, ownership/boundary, cross-project change, dependency/release coupling, permission/tooling/CI implications and scaling trade-offs |

The numeric values above are a **tree/presentation projection**, not a claim that the four collaboration platforms form a learning dependency chain. They belong to the same specialization wave; one chosen platform is sufficient for the primary learning path.

### Grouping contract

The physical grouping remains:

```text
source-management/
├── fundamentals/                         # real module
├── source-control/                       # group
│   └── git/                              # real module
├── collaboration-platform/               # group
│   ├── github/                           # real module
│   ├── gitlab/                           # real module
│   ├── bitbucket/                        # real module
│   └── azure-devops/                     # real module
└── repository-strategy/                  # group
    ├── branching-strategy/               # real module
    └── monorepo-polyrepo/                # real module
```

`source-control`, `collaboration-platform` and `repository-strategy` are taxonomy/grouping directories, not additional learning owners.

### Current non-module decisions

The curriculum intentionally does **not** create separate modules for SVN, Mercurial, Perforce or CVS. For the current Java/backend DevOps target, they are comparative/background material only unless a future learner target creates a real independent need. Their existence and centralized-vs-distributed trade-offs may be introduced in `fundamentals` or referenced from Git without creating duplicate full curricula.

The curriculum also intentionally does **not** create separate modules for:

```text
repository-boundary
code-ownership
CODEOWNERS
merge-strategy
release-branch
hotfix-branch
branch-protection
```

These are natural concepts inside `branching-strategy`, `monorepo-polyrepo` or a platform-specific collaboration module. They do not currently justify additional standalone learning domains.

---

## 4. Module ownership and boundaries

### 4.1 `fundamentals`

Primary ownership:

```text
what source management is and why teams need it
source history / shared source-of-truth mental model
version control at concept level
centralized vs distributed VCS orientation
local repository vs remote repository orientation
source-control tool vs collaboration platform vs repository strategy
high-level source-change lifecycle from local edit to governed shared history
handoff from source management to delivery automation
```

Boundary:

- does **not** own Git commands/data-model depth → `source-control/git`;
- does **not** own PR/MR platform behavior → collaboration-platform modules;
- does **not** own trunk-based/Git Flow/monorepo decisions → repository-strategy modules;
- does **not** own CI/CD behavior → current generic owner `delivery-automation/ci-cd/fundamentals`;
- does **not** own build-tool mechanics → the current repository has planned/taxonomy targets under `platform/development/engineering/build-tool/{gradle,maven}`, but no real generic Gradle/Maven learning module owner yet; Source Management must not absorb that missing external curriculum;
- does **not** own deployment mechanics → `runtime-delivery/deployment/fundamentals`.

The module is an orientation layer, not a second shallow copy of every child module.

### 4.2 `source-control/git`

Primary ownership:

```text
Git repository and object/history mental model at learner-appropriate depth
working tree / index / repository relationship
recording changes and commit history
refs and branches
branch creation/switching
merge mechanics and conflicts
rebase mechanics and history-rewrite implications
remote repositories and remote-tracking refs
fetch / pull / push mental model
tags
undo/recovery mechanics appropriate to Git learning
stash and other common Git-native workflow helpers when useful
distributed Git contribution/collaboration mechanics at tool level
```

Boundary:

- Git owns **mechanics**, not the organization's branch strategy;
- branch naming conventions, branch lifetime, trunk-based/Git Flow decision and policy intent → `repository-strategy/branching-strategy`;
- pull/merge request UI, platform permissions, branch protection configuration, organization/group/workspace concepts → collaboration-platform owner;
- CI pipelines triggered from Git events → `delivery-automation/ci-cd/fundamentals`;
- artifact version/release lifecycle → `delivery-automation/versioning` and `release-management`;
- secret scanning → `devsecops/secret-scanning`;
- dependency scanning → `devsecops/dependency-scanning`;
- software supply-chain security → `devsecops/software-supply-chain`.

Git may introduce collaboration workflows sufficiently to explain why branches/remotes/merge/rebase exist, but it must hand off platform governance rather than duplicating GitHub/GitLab/Bitbucket/Azure curricula.

### 4.3 `collaboration-platform/github`

Primary ownership:

```text
GitHub repository hosting model
repository visibility/settings at platform level
fork/contribution workflow at GitHub level
pull requests
code review / requested changes / approvals
branch protection / rulesets / merge requirements
repository/team/organization permissions and ownership concepts
issues and lightweight source-work collaboration
GitHub release surface as repository collaboration metadata
platform-specific collaboration workflow and governance
```

Boundary:

- Git mechanics remain `source-control/git`;
- branch-strategy decision belongs `repository-strategy/branching-strategy` even when GitHub Flow is the chosen strategy;
- GitHub Actions is not owned here; current generic CI owner is `delivery-automation/ci-cd/fundamentals`, while `delivery-automation/ci-cd/github-actions` remains a planned/taxonomy specialization until it becomes a real module;
- general semantic versioning/release-management belongs delivery automation;
- GitHub-specific security features may be mentioned as integration surfaces, but secret/dependency/supply-chain curricula belong `devsecops/secret-scanning`, `devsecops/dependency-scanning` and `devsecops/software-supply-chain` respectively.

### 4.4 `collaboration-platform/gitlab`

Primary ownership:

```text
GitLab project/group/repository collaboration model
merge requests
review / approval workflow
protected branches and repository permissions
issue/release collaboration surfaces
group/project governance relevant to source collaboration
```

Boundary:

- Git commands/history/remotes remain Git ownership;
- generic branching strategy remains repository-strategy ownership;
- GitLab CI is not owned here; current generic CI owner is `delivery-automation/ci-cd/fundamentals`, while `delivery-automation/ci-cd/gitlab-ci` remains a planned/taxonomy specialization until it becomes a real module;
- DevSecOps domains remain security-delivery owners even when GitLab exposes integrated features.

### 4.5 `collaboration-platform/bitbucket`

Primary ownership:

```text
Bitbucket workspace/project/repository collaboration model
pull requests and code review
branch restrictions and merge checks at Bitbucket implementation level
repository/project permissions
team collaboration workflows around hosted Git repositories
```

Boundary:

- Git mechanics remain Git ownership;
- abstract branching strategy remains repository-strategy ownership;
- Bitbucket Pipelines is a CI/CD concern and does not expand this module into delivery automation.

### 4.6 `collaboration-platform/azure-devops`

Primary ownership:

```text
Azure DevOps project/repository collaboration model with Azure Repos emphasis
Git repositories in Azure Repos
pull requests and code review
branch policies / required reviewers / merge controls
repository/project permissions and source collaboration workflow
```

Boundary:

- the module does not become a broad survey of every Azure DevOps service;
- Azure Pipelines is CI/CD/delivery automation;
- Azure Boards/project-management depth belongs the appropriate software-process/project-management owner if introduced;
- Git mechanics remain Git ownership;
- generic branching/repository topology decisions remain repository-strategy ownership.

### 4.7 `repository-strategy/branching-strategy`

Primary ownership:

```text
why teams need an explicit integration/branch strategy
short-lived vs long-lived branches
trunk-based development
GitHub Flow at strategy level
Git Flow at strategy level
feature/release/hotfix branch roles when relevant
integration frequency and branch lifetime trade-offs
merge vs rebase/squash policy as team-history strategy, without reteaching Git mechanics
branch naming/protection/review requirements as policy intent
strategy selection criteria and anti-patterns
```

Boundary:

- `git` teaches how branch/merge/rebase operations work;
- GitHub/GitLab/Bitbucket/Azure modules teach how their product enforces review/protection/policy;
- CI pipeline implementation based on branches/tags belongs delivery automation;
- release orchestration and artifact promotion belongs `release-management`.

`branching-strategy` owns **team decision and trade-off**, not command syntax or one vendor's settings UI.

### 4.8 `repository-strategy/monorepo-polyrepo`

Primary ownership:

```text
repository topology as an engineering decision
monorepo vs polyrepo mental models
repository boundary and team/service ownership trade-offs
cross-project / cross-service changes
shared-code and dependency coordination implications
release-coupling implications
permissions and governance implications
repository-size/tooling/scaling implications
CI/build impact at strategy level
when to split or consolidate repositories
```

Boundary:

- does not own build-system implementation for a monorepo;
- does not own CI affected-project detection/cache/pipeline implementation beyond strategic consequences;
- does not own microservice architecture/service decomposition;
- does not own platform-engineering golden paths/templates beyond repository-topology interaction;
- does not own Git internals or platform UI mechanics.

---

## 5. Cross-area ownership guardrails

| Concept/domain | Source Management treatment | Current external owner / repository status |
| --- | --- | --- |
| CI/CD pipelines | Source events/branches/tags may be inputs; no pipeline authoring here | `delivery-automation/ci-cd/fundamentals` |
| GitHub Actions | GitHub module only establishes boundary/integration context | current generic owner: `delivery-automation/ci-cd/fundamentals`; `ci-cd/github-actions` is a planned/taxonomy specialization until it becomes a real module |
| GitLab CI | GitLab module only establishes boundary/integration context | current generic owner: `delivery-automation/ci-cd/fundamentals`; `ci-cd/gitlab-ci` is a planned/taxonomy specialization until it becomes a real module |
| Build-system/tool mechanics | Monorepo strategy may discuss impact but not implementation | no current real generic learning module owner; planned/taxonomy targets exist under `platform/development/engineering/build-tool/{gradle,maven}` and remain outside Source Management |
| Monorepo CI optimization / affected-project pipeline behavior | `monorepo-polyrepo` owns only the strategic consequence | `delivery-automation/ci-cd/fundamentals` currently owns generic CI/CD implementation concepts; future real tool specializations may own vendor mechanics |
| Artifact repositories | Git repositories store source/history, not published binary artifacts | `delivery-automation/artifact-management/fundamentals` |
| Semantic versioning / release orchestration | Tags/releases may be source-management surfaces; lifecycle policy lives elsewhere | `delivery-automation/versioning`, `release-management` |
| Deployment branches/environments | Strategy may discuss coupling/risk; deployment and environment policy live elsewhere | deployment mechanics → `runtime-delivery/deployment/fundamentals`; environment topology/promotion strategy → `infrastructure-management/environment-management/environment-strategy` |
| Secret scanning | Platform may expose the feature, but detection/remediation workflow is security delivery | `devops/devsecops/secret-scanning` |
| Dependency scanning | Platform integration is supporting context only | `devops/devsecops/dependency-scanning` |
| Software supply chain | Source integrity is an input, not the whole curriculum | `devops/devsecops/software-supply-chain` |
| Secret lifecycle / Vault | Source repositories should not become secret-management curriculum | no current real learning module owner; taxonomy/planned area exists under `infrastructure/system/security/secrets-management` and remains outside Source Management |
| Scrum/Kanban/team methodology | PR/branch workflows may interact with team process | `platform/development/software-process/methodology/agile-development` |
| Microservice decomposition | Polyrepo examples may use services, but service boundaries are not decided here | `module/microservice` architecture/pattern owners |
| Internal developer platform / golden paths | Repository strategy may be consumed by standardized templates/workflows | platform abstraction → `platform-engineering/internal-developer-platform`; recommended standardized workflow → `platform-engineering/golden-path` |

The key boundary is:

```text
SOURCE MANAGEMENT
→ what source history/repositories/team source collaboration should look like

DELIVERY AUTOMATION
→ what automated build/test/package/release flow should do after source changes
```

---

## 6. Dependency graph

These are **learning dependencies**, not Gradle dependencies.

```text
fundamentals
    ↓
source-control/git
    ↓
choose at least one collaboration platform
    ├── github
    ├── gitlab
    ├── bitbucket
    └── azure-devops
    ↓
repository-strategy/branching-strategy
    ↓
repository-strategy/monorepo-polyrepo
```

Important nuances:

- all four collaboration-platform modules depend on basic Git understanding;
- the four collaboration-platform modules do **not** depend on one another;
- a learner does not need to master all four platforms before continuing — one platform is enough for the primary path, while the others are alternative/transferable specializations;
- `branching-strategy` technically depends mainly on Git branch/merge concepts, but is recommended after one hosted platform so PR/MR/review/protection concepts are concrete rather than abstract;
- `monorepo-polyrepo` is last because it asks the learner to reason about team/repository/dependency/release/tooling trade-offs rather than basic source-control operation;
- no dependency cycle is permitted among these modules.

---

## 7. Recommended learning waves

### Wave 1 — Source-management orientation

```text
fundamentals
```

Goal: understand why source management exists and distinguish source control, hosting/collaboration and repository strategy.

### Wave 2 — Git source-control core

```text
source-control/git
```

Goal: gain the mechanics and mental model required to work safely with repository history, branches and remotes.

### Wave 3 — Hosted collaboration

Recommended primary path:

```text
collaboration-platform/github
```

Alternative/transferable specializations:

```text
collaboration-platform/gitlab
collaboration-platform/bitbucket
collaboration-platform/azure-devops
```

Goal: understand how a hosted product turns Git repositories into governed team collaboration through PR/MR review, permissions and protected-branch policy.

The primary path uses GitHub first for presentation consistency, not because the curriculum requires every learner or organization to choose GitHub.

### Wave 4 — Repository/team strategy

```text
repository-strategy/branching-strategy
        ↓
repository-strategy/monorepo-polyrepo
```

Goal: move from operating tools to making engineering decisions about integration flow and repository topology.

### Handoff after Source Management

After the learner has a governed shared source workflow, the natural DevOps continuation is:

```text
delivery-automation/fundamentals
        ↓
delivery-automation/ci-cd/fundamentals
```

---

## 8. Recommended sibling order

### 8.1 `source-management/module-order.yml`

The current direct-child order is the STEP 1 locked order:

```yaml
version: 1
children:
  fundamentals:
    order: 10
  source-control:
    order: 20
  collaboration-platform:
    order: 30
  repository-strategy:
    order: 40
```

Reasoning:

```text
orientation
→ Git mechanics
→ concrete hosted collaboration
→ higher-level strategy/trade-off
```

### 8.2 `collaboration-platform/module-order.yml`

```yaml
version: 1
children:
  github:
    order: 10
  gitlab:
    order: 20
  bitbucket:
    order: 30
  azure-devops:
    order: 40
```

This is presentation order among peer specializations, not a dependency chain.

### 8.3 `repository-strategy/module-order.yml`

```yaml
version: 1
children:
  branching-strategy:
    order: 10
  monorepo-polyrepo:
    order: 20
```

Branch/integration strategy is learned first because repository-topology decisions are easier to reason about after the learner already understands how teams integrate and govern changes.

---

## 9. Area terminology ownership

| Term | First/full owner in this area | Consumed by |
| --- | --- | --- |
| Source management | `fundamentals` | all modules |
| Version control | `fundamentals` concept; Git owns mechanics | all modules |
| Distributed VCS | `fundamentals` orientation; Git owns concrete behavior | collaboration/strategy |
| Repository | `fundamentals` orientation; Git owns Git repository mechanics | all modules |
| Working tree / index / commit | `source-control/git` | platform/strategy modules |
| Branch / ref | `source-control/git` mechanics | branching strategy + platforms |
| Remote / remote-tracking ref | `source-control/git` | collaboration platforms |
| Merge / rebase | `source-control/git` mechanics | branching strategy consumes trade-offs |
| Pull request | platform-specific owner (`github`, `bitbucket`, `azure-devops`) | branching strategy as generic integration-gate concept |
| Merge request | `collaboration-platform/gitlab` | branching strategy as equivalent collaboration concept |
| Branch protection / policy | platform module owns configuration; `branching-strategy` owns policy intent | collaboration + strategy |
| Code review | platform module owns workflow implementation; fundamentals may orient | branching strategy consumes governance intent |
| Trunk-based development | `branching-strategy` | platform modules may show enforcement examples |
| GitHub Flow / Git Flow | `branching-strategy` | Git/platform modules provide supporting mechanics |
| Monorepo / polyrepo | `monorepo-polyrepo` | delivery automation/platform engineering consume implications |
| Repository boundary | `monorepo-polyrepo` | microservice/platform/delivery areas may reference |

---

## 10. Coverage matrix

| Major concept/domain | Primary owner | Supporting/related module | External owner/boundary |
| --- | --- | --- | --- |
| Source-management purpose | `fundamentals` | all | DevOps fundamentals provides wider lifecycle |
| Centralized vs distributed orientation | `fundamentals` | Git | no separate SVN/Mercurial curriculum currently |
| Git history/repository model | `source-control/git` | fundamentals | none |
| Git branch/merge/rebase/remotes/tags | `source-control/git` | branching strategy | team policy belongs strategy |
| Hosted repository collaboration | platform-specific module | Git | CI/security capabilities handed off |
| Pull-request review | GitHub/Bitbucket/Azure owner as applicable | branching strategy | GitLab uses merge-request terminology |
| Merge-request review | GitLab | branching strategy | Git mechanics remain Git |
| Permissions / protected branches | platform-specific module | branching strategy | strategy owns intent, platform owns implementation |
| Branching/integration strategy | `branching-strategy` | Git + one chosen platform | CI pipeline implementation elsewhere |
| Trunk-based / GitHub Flow / Git Flow | `branching-strategy` | Git/platform examples | no duplicate platform owner |
| Repository topology | `monorepo-polyrepo` | branching strategy | microservice boundaries/build implementation elsewhere |
| Monorepo CI/build implications | `monorepo-polyrepo` strategic view | delivery automation | actual affected-build/pipeline tooling elsewhere |
| Releases/tags | Git owns tag mechanics; platform owns release surface | strategy may consume | versioning/release management owns lifecycle policy |
| Source-triggered CI | source area provides event/context only | platform modules | `delivery-automation/ci-cd/fundamentals` owns generic pipeline behavior |
| Secret scanning from source history/content | platform may expose integration | Git/repository context | `devsecops/secret-scanning` owns detection/remediation curriculum |
| Dependency scanning from source manifests | platform may expose integration | Git/repository context | `devsecops/dependency-scanning` owns dependency-vulnerability workflow |
| Source/release provenance and supply-chain integrity | Git/platform history is an input | repository context | `devsecops/software-supply-chain` owns supply-chain security curriculum |

No major Source Management concept is intentionally left without an owner in the current scope.

---

## 11. Module-creation and granularity decisions

### 11.1 Git remains the only current source-control implementation module

This is deliberate for the present learner target. The curriculum should explain that other VCS families exist, but it should not create low-value modules solely for taxonomy completeness.

A future VCS module should be added only if the learner target materially requires its own mental model, mechanics, workflow and practical depth — for example a future game-development track with a genuine Perforce requirement.

### 11.2 Four collaboration platforms remain separate modules

Although they overlap conceptually, each platform has enough independent repository hierarchy, review/governance terminology, permissions and policy mechanics to justify a specialization module.

They must still share a strict boundary:

```text
Git concepts → Git owner
generic strategy → repository-strategy owner
vendor-specific collaboration mechanics → platform owner
CI/CD → delivery-automation owner
```

### 11.3 Repository strategy stays at two modules

`branching-strategy` and `monorepo-polyrepo` are sufficient for the current curriculum.

Do not split smaller concepts such as CODEOWNERS, merge strategy, repository boundary or release branches into standalone modules unless future content proves that one of them has become a genuinely independent learning domain.

---

## 12. Major gap and duplication review

### Foundational gaps

No blocking foundational module gap is identified.

Two neighboring curricula currently exist only as taxonomy/planned destinations rather than real generic learning modules: generic Gradle/Maven build-tool mechanics and infrastructure secrets-management/Vault. They are recorded as **external repository gaps/boundaries**, not as Source Management ownership gaps. This Curriculum must not create duplicate Source Management modules merely to compensate for those neighboring areas.

The area has:

```text
orientation owner
→ fundamentals

source-control mechanics owner
→ git

hosted collaboration owners
→ github / gitlab / bitbucket / azure-devops

team strategy owners
→ branching-strategy / monorepo-polyrepo
```

### Duplication risks to guard downstream

1. **Git vs branching-strategy** — do not reteach merge/rebase mechanics in the strategy module.
2. **branching-strategy vs platform modules** — policy intent belongs strategy; UI/config implementation belongs the vendor module.
3. **platform modules vs CI/CD** — GitHub Actions/GitLab CI/Azure Pipelines/Bitbucket Pipelines must not turn collaboration modules into pipeline curricula.
4. **monorepo-polyrepo vs microservice architecture** — repository topology is not service decomposition.
5. **monorepo-polyrepo vs build/CI tooling** — strategic consequences may be explained, implementation belongs downstream tooling owners.
6. **platform security features vs DevSecOps** — platform integration may be mentioned, but scanning/security process is not duplicated.

### Optional future extensions — not current gaps

The following may become modules only if future scope justifies them:

```text
Perforce / other VCS specialization for a different learner target
enterprise repository governance as a distinct domain if current strategy modules become overloaded
additional collaboration platform if it has real learner demand
```

They are not blockers for the present Curriculum lock.

---

## 13. STEP 1 acceptance check

| Check | Result |
| --- | --- |
| Area scope clear? | PASS |
| Learner/prerequisites clear? | PASS |
| Canonical real-module inventory explicit? | PASS — 8 modules |
| Every major in-scope Source Management concept has one primary owner? | PASS |
| Major cross-module boundaries explicit? | PASS |
| Dependency graph acyclic? | PASS |
| Collaboration platforms treated as peer alternatives rather than false prerequisites? | PASS |
| Repository-strategy granularity reviewed? | PASS — 2 modules retained |
| Non-Git VCS scope decision explicit? | PASS — comparison only for current target |
| Cross-area handoff/boundary clear, including taxonomy-only external areas with no current real owner? | PASS |
| Major foundational gap identified? | NONE |
| `module-order.yml` reflects recommended direct-child learning order? | PASS — official generator validated 4 source-management children, 4 collaboration-platform children and 2 repository-strategy children |
| Detailed Roadmap/Menu/Knowledge design avoided? | PASS |

This Curriculum Map is **STEP 1 LOCKED** after sequential independent review and official module-order generator validation. Future changes to module inventory, primary ownership, boundaries, dependency graph or recommended area learning order are Curriculum migrations and must reopen STEP 1.

---

## 14. STEP 2 handoff constraints

After STEP 1 is locked, Step 2 Roadmap/Reference work for each real module must consume this map rather than redefining area ownership.

Expected handoff:

```text
fundamentals
→ teach the area mental model; stop before Git/platform/strategy depth

git
→ own Git mechanics; cross-link strategy/platform concepts instead of absorbing them

github / gitlab / bitbucket / azure-devops
→ own vendor-specific collaboration; do not own CI/CD or generic Git mechanics

branching-strategy
→ own team integration policy/trade-offs; consume Git mechanics and platform enforcement examples

monorepo-polyrepo
→ own repository-topology decisions; hand implementation consequences to build/CI/microservice/platform owners
```

If a downstream Step discovers that one of these ownership/boundary decisions is wrong, report a **CURRICULUM GAP** and return to STEP 1 rather than silently changing the area architecture inside a module Roadmap or Knowledge file.

---

## 15. Plain-language learning story

This area is about **how a developer's source changes become shared, reviewable and governable team history**.

It exists because files alone do not provide durable history, safe collaboration, review gates or a clear team source of truth.

The learner first understands the source-management landscape, then learns Git as the core source-control mechanism, then sees how a hosted collaboration platform adds review/permissions/governance, and finally learns how teams choose branching and repository-topology strategies based on scale, coupling and delivery needs.

The intended progression is therefore:

```text
What is source management?
        ↓
How does Git record and exchange history?
        ↓
How does a hosted platform govern collaboration around Git?
        ↓
How should a team choose its branching/integration strategy?
        ↓
How should a team choose repository topology as the system and organization scale?
        ↓
hand off governed source to delivery automation
```
