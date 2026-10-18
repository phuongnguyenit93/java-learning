<a id="back-to-top"></a>

# Azure Repos and Git-Based Collaboration

## Menu
- [Azure DevOps and Azure Repos: Concepts and Service Boundaries](#azure-devops-vs-azure-repos)
- [The Role of Hosted Source Collaboration Beyond Git](#why-hosted-collaboration)
- [Git History Versus Azure Repos Collaboration Governance](#git-versus-azure-repos-ownership)
- [Organizations, Projects, Repositories, and Contributors: Core Vocabulary](#organization-project-repo-vocabulary)
- [Pull Requests: Concept, Purpose, and Source/Target Branches](#what-is-a-pull-request)
- [Reviewers, Branch Policies, and the Model of PR Merge Requirements](#reviewer-policy-and-merge-relation)
- [Azure Repos Boundaries with Azure Boards and Azure Pipelines](#boards-and-pipelines-boundary)
- [Collaboration States and Evidence for an Azure Repos Change](#trace-collaboration-surface)

## <a id="azure-devops-vs-azure-repos">Azure DevOps and Azure Repos: Concepts and Service Boundaries</a>

<details>
<summary>Click for details</summary>

**Azure DevOps** is a suite of development collaboration services. **Azure Repos** is its source-control service, supporting Git repositories (and TFVC in legacy settings). This module owns hosted Git repositories, permissions, pull requests (PRs), review decisions and branch policies. An Azure DevOps organization is not the same thing as an Azure cloud subscription; permissions do not automatically transfer between them.

A developer can commit locally using Git alone, but a team needs an additional review and access-control surface when proposing changes to a shared `main` branch.

**Learning route:** begin by distinguishing Azure DevOps, Azure Repos and Git and mapping organization → project → repository, contributors and pull requests (PRs). Then learn repository organization, access levels and security permissions, contribution through shared branches or forks, PR review, branch policies/bypass permissions and PR completion. The final chapter traces one issue through accepted shared source. Azure Boards and Pipelines provide adjacent work-tracking and build/deployment capabilities taught separately.

### References

- [Microsoft Learn — Azure Repos, Git, and TFVC](https://learn.microsoft.com/en-us/azure/devops/repos/get-started/what-is-repos?view=azure-devops)

</details>

- [Back to top](#back-to-top)

---

## <a id="why-hosted-collaboration">The Role of Hosted Source Collaboration Beyond Git</a>

<details>
<summary>Click for details</summary>

Git records commits and exchanges objects, but it does **not itself determine** who may read a private repository, which person must review a proposed change, or what checks are required before integration. Azure Repos adds hosting, security groups, PR discussions, reviewer votes and branch policies.

For example, two engineers edit payment calculation logic. Two Git commits cannot prove that someone reviewed refund behavior. A PR connects the intent, diff and reviewer feedback into an inspectable decision trail; having a PR does not substitute for tests or responsible review.

</details>

- [Back to top](#back-to-top)

---

## <a id="git-versus-azure-repos-ownership">Git History Versus Azure Repos Collaboration Governance</a>

<details>
<summary>Click for details</summary>

**Git owns mechanics:** commit IDs, refs, fetch/push, merges, conflicts and rebases. **Azure Repos owns hosted collaboration:** Read/Contribute permissions, PR presentation, reviewer decisions, target branch checks and PR lifecycle records. Git commands still work against an Azure Repos-hosted repository, but the hosting service can reject an update for permission or policy reasons.

A locally valid commit can fail to push without being invalid Git data. First verify the local commit and remote URL, then inspect Azure Repos permissions or target branch policies. Detailed Git internals belong to the Git module.

</details>

- [Back to top](#back-to-top)

---

## <a id="organization-project-repo-vocabulary">Organizations, Projects, Repositories, and Contributors: Core Vocabulary</a>

<details>
<summary>Click for details</summary>

An **organization** governs users and Azure DevOps services. A **project** groups a product/team boundary and its permissions. A **Git repository** stores one code history inside a project, while a **contributor** proposes or updates changes subject to rights. One project can hold several Git repositories.

Use the same fictional example as later chapters, `RetailCo → Checkout → checkout-api`: Alice edits a branch in `checkout-api` and Bob reviews the PR. These are illustrative names, not a live organization; repositories and projects are not interchangeable, and project membership does not grant every operation on every repository or branch.

</details>

- [Back to top](#back-to-top)

---

## <a id="what-is-a-pull-request">Pull Requests: Concept, Purpose, and Source/Target Branches</a>

<details>
<summary>Click for details</summary>

A **pull request (PR)** proposes reviewing and integrating a **source branch** into a **target branch**, within one repository or across a supported fork relationship. A PR is not a native Git object: Azure Repos records its description, changed-file diff, reviewers, discussions and validation state.

For example, source `feature/tax-fix` targets `main`. Accidentally targeting `release/1.x` changes both the history destination and applicable policies. Verify source/target repository and branch before requesting review.

</details>

- [Back to top](#back-to-top)

---

## <a id="reviewer-policy-and-merge-relation">Reviewers, Branch Policies, and the Model of PR Merge Requirements</a>

<details>
<summary>Click for details</summary>

A **reviewer** inspects a change and casts a vote. A **branch policy** places requirements on the **target branch**—reviewer counts, comment resolution or validation. **Completion** integrates approved changes once the actor has rights and requirements are satisfied. An approval vote does not automatically complete a PR.

A PR with one approval still waits if policy requires two; a failed required validation may also block it after two approvals. Read the PR's review/policy status and separate permission problems from unmet checks.

</details>

- [Back to top](#back-to-top)

---

## <a id="boards-and-pipelines-boundary">Azure Repos Boundaries with Azure Boards and Azure Pipelines</a>

<details>
<summary>Click for details</summary>

Adjacent services have different owners: **Azure Boards** tracks work items such as bugs and tasks, while **Azure Pipelines** can run build/test validation and publish results to a PR. Azure Repos links work items and consumes validation state for merge decisions; it does not teach pipeline YAML, build agents or sprint management.

A tax-fix PR may link `Bug 104` to explain the request. A “build validation failed” message identifies a check result; configuring the underlying build is a separate CI/CD concern.

</details>

- [Back to top](#back-to-top)

---

## <a id="trace-collaboration-surface">Collaboration States and Evidence for an Azure Repos Change</a>

<details>
<summary>Click for details</summary>

A source-change trail typically includes repository and source/target branches, PR ID/author, proposed commits, reviewers/votes, resolved or active discussion threads, check/policy results and the PR's **Active, Abandoned or Completed** state. These fields answer different questions about intent, permission, validation and delivery.

Observe an example: Alice opens a PR, Bob approves, but required validation is still failing. Azure Repos keeps the PR incomplete. A local `git log` proves commits exist; it cannot prove that the PR was reviewed or merged.

</details>

- [Back to top](#back-to-top)
