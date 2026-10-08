<a id="back-to-top"></a>

# Git Flow and Versioned Release Coordination

## Menu
- [Git Flow Origins and Criteria for Adoption](#git-flow-context)
- [Git Flow Production Branch (Original Master or Modern Main) and Develop Roles](#main-develop-roles)
- [Feature, Release, and Hotfix Branch Roles in Git Flow](#feature-release-hotfix-roles)
- [Stabilizing an Upcoming Release While Development Continues](#release-stabilization-handoffs)
- [Multiple Supported Versions Require Additional Policies Beyond Basic Git Flow](#multiple-maintained-versions-caveat)
- [Synchronization Costs and the Author's Continuous-Delivery Caveat](#git-flow-costs-continuous-delivery)

## <a id="git-flow-context">Git Flow Origins and Criteria for Adoption</a>

<details>
<summary>Click for details</summary>

**Git Flow**, described by Vincent Driessen in 2010, coordinates explicitly versioned releases, stabilization windows, and urgent production fixes while future development continues. It is a **historically influential workflow with named branch responsibilities**, not a default Git mode. The original uses `master` and `develop`; a modern team may call the production branch `main` without changing its role.

Suppose payment software runs at customer sites, versions 1.4 and 1.5 need maintenance, and the next version remains under development. Git Flow supplies a coordination map, but it does not automatically solve indefinite multi-version support.

**Selection rule:** inspect release cadence and maintenance obligations before adding `develop` simply because the model is familiar.

### References
- [Vincent Driessen — A successful Git branching model](https://nvie.com/posts/a-successful-git-branching-model/)

</details>

- [Back to top](#back-to-top)

---

## <a id="main-develop-roles">Git Flow Production Branch (Original Master or Modern Main) and Develop Roles</a>

<details>
<summary>Click for details</summary>

In original Git Flow, **`master`** (which a team may rename `main`) records production release history, often with version tags; **`develop`** integrates work for the next release. Both are long-lived but promise different things. Code on develop can be accepted for future development without being release-ready; the production line records selected stable versions.

Refunds merge into develop for version 1.5 while main remains tagged 1.4.0. Sending features directly to the production branch outside the release process breaks the agreed model.

**Observe:** draw the two histories and mark where features merge, releases are tagged, and hotfixes propagate. Team members should agree on their roles regardless of naming differences.

</details>

- [Back to top](#back-to-top)

---

## <a id="feature-release-hotfix-roles">Feature, Release, and Hotfix Branch Roles in Git Flow</a>

<details>
<summary>Click for details</summary>

Git Flow distinguishes three supporting branch roles: **feature/** branches leave and return to develop; **release/** branches leave develop to stabilize a forthcoming version; **hotfix/** branches start from production history to repair an urgent released defect. Their different sources and destinations prevent lost fixes and accidental feature releases.

Refunds belong on `feature/refunds` toward develop; `release/1.5` accepts stabilization work; `hotfix/1.4.1` branches from production to repair rounding. Completing the hotfix requires updating both the released line and relevant development line.

**Original Git Flow exception when a release branch is already active:** the urgent fix still reaches the production line, but merge the hotfix into the active `release/1.5` **instead of automatically merging it straight into `develop`**. Finishing the release carries that fix back into develop; if develop needs it immediately, the original model also permits an earlier verified merge there. Track exactly which lines received the patch.

**Exercise:** for each branch, record origin, admissible changes, destination(s), and retirement event. Git command syntax is taught elsewhere.

### References
- [Atlassian — Gitflow Workflow](https://www.atlassian.com/git/tutorials/comparing-workflows/gitflow-workflow)

</details>

- [Back to top](#back-to-top)

---

## <a id="release-stabilization-handoffs">Stabilizing an Upcoming Release While Development Continues</a>

<details>
<summary>Click for details</summary>

Once develop contains enough features for the next version, the team creates a **release branch**. Stabilization changes there should focus on fixes, version preparation, and relevant documentation while new features continue on develop. When ready, the release reaches production history, and **stabilization fixes must also return to develop** so future versions retain them.

QA spots a configuration defect in `release/1.5`. The team fixes it, qualifies version 1.5, and propagates the change into develop. Otherwise 1.6 may reintroduce a bug already fixed in 1.5.

**Evidence:** release merge/tag, the corresponding fix in develop, and a named release-manager checklist. Parallel lines have real synchronization cost.

</details>

- [Back to top](#back-to-top)

---

## <a id="multiple-maintained-versions-caveat">Multiple Supported Versions Require Additional Policies Beyond Basic Git Flow</a>

<details>
<summary>Click for details</summary>

The basic Git Flow diagram usually shows one production line and one develop line; it **does not define complete policy for several older versions maintained simultaneously**. If customers still use 1.4 and 1.5, the team must define support branches, support deadlines, hotfix destinations, backports, and retirement conditions. Merging one hotfix into main does not automatically repair every old release.

A library vulnerability affects versions 1.4, 1.5, and 1.6. Identify affected maintenance lines, validate each patch, and publish a corresponding supported fix.

**Evidence:** maintain a matrix of supported version → branch/tag → patch state → accountable release owner. This extends governance beyond the simple original branch diagram.

</details>

- [Back to top](#back-to-top)

---

## <a id="git-flow-costs-continuous-delivery">Synchronization Costs and the Author's Continuous-Delivery Caveat</a>

<details>
<summary>Click for details</summary>

Multiple permanent branches support release stabilization but create more merge directions, bidirectional fix propagation, and opportunities for long-lived features. In his **March 5, 2020 reflection**, Vincent Driessen warned against treating Git Flow as a universal remedy and suggested a simpler workflow such as GitHub Flow for **continuous delivery** web applications.

A payment website deploying many times each day may incur needless develop → release → main transitions for tiny changes. Offline client software supporting both 1.4 and 1.5 may benefit from explicit version lines.

**Decision evidence:** compare lead time, required synchronization paths, concurrent supported versions, and missed-hotfix incidents. Git Flow is a context-dependent choice, not a mandatory best practice.

### References
- [Driessen — Git Flow and 2020 reflection](https://nvie.com/posts/a-successful-git-branching-model/)

</details>

- [Back to top](#back-to-top)
