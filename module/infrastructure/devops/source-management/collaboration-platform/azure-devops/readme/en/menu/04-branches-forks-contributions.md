<a id="back-to-top"></a>

# Contribution Paths Through Shared Branches and Forks

## Menu
- [Two Contribution Paths: Shared-Repository Branches and Forks](#shared-branch-versus-fork)
- [Forks as Separate Repositories and Their Upstream Source](#fork-origin-upstream)
- [Fork Permissions and PR Policies of the Target Repository](#fork-permissions-and-policies)
- [Source and Target Repositories and Branches of a PR](#pull-request-source-target)
- [Focused PR Proposals with Descriptions and Change Context](#create-contribution-request)
- [Draft Pull Requests Versus Ready-for-Review PRs](#draft-vs-ready-pull-request)
- [Contribution Path Evidence: Author, Permissions, and Target Branch](#verify-contribution-path)

## <a id="shared-branch-versus-fork">Two Contribution Paths: Shared-Repository Branches and Forks</a>

<details>
<summary>Click for details</summary>

Two contribution routes exist: a **branch inside a shared repository** when contributors may create and push there, or a **fork**—a separate repository used to isolate ownership and permissions. Both can feed a PR into a target branch when product permissions permit, but a fork is not merely another branch name.

For an external partner, teams may prefer a separate fork and reviewing PRs into the upstream repository rather than granting broad Contribute on protected internal code. This is a governance decision, not a Git syntax difference.

</details>

- [Back to top](#back-to-top)

---

## <a id="fork-origin-upstream">Forks as Separate Repositories and Their Upstream Source</a>

<details>
<summary>Click for details</summary>

A **fork** is its own repository with history and an upstream-source relationship. Commits made in the fork do not automatically update upstream; the author pushes to the fork and proposes a PR to the original repository. Local Git remote names `origin` and `upstream` are conventions, not separate Azure Repos features.

The PR source `CheckoutFork/feature` and target `Checkout/main` are distinct repository/branch combinations. Confirm the source repository as well as the branch name, which could be reused elsewhere.

### References

- [Microsoft Learn — fork origin upstream](https://learn.microsoft.com/en-us/azure/devops/repos/git/forks?view=azure-devops)

</details>

- [Back to top](#back-to-top)

---

## <a id="fork-permissions-and-policies">Fork Permissions and PR Policies of the Target Repository</a>

<details>
<summary>Click for details</summary>

When Azure Repos forks a repository, the original repository's **permissions, policies and build pipelines are not automatically copied to the fork**. A contributor may manage their fork under its own rights, but a PR targeting `upstream/main` is subject to policies on the **upstream target branch**. Microsoft's fork guide specifies **Project Valid Users membership plus Read on the upstream repository** to open a PR into it. Completing the PR still requires eligible permissions and satisfied required reviewers/target policies; fork ownership alone supplies none of those upstream rights.

A fork that allows direct pushes does not make an upstream PR mergeable without review. Inspect the fork's source permissions and the upstream target branch's policies separately; a successful fork is not evidence of merge authority.

### References

- [Microsoft Learn — fork permissions and policies](https://learn.microsoft.com/en-us/azure/devops/repos/git/forks?view=azure-devops)

</details>

- [Back to top](#back-to-top)

---

## <a id="pull-request-source-target">Source and Target Repositories and Branches of a PR</a>

<details>
<summary>Click for details</summary>

A PR maps a **source repository and branch → target repository and branch**. Both branches may be in one repository; fork-based PRs usually cross repositories. Reviewer/check policies are primarily determined by the **target branch**, not by how the source branch was named.

Selecting target `develop` instead of `main` can apply a different policy set. Before creating the PR inspect the repository/branch selectors and changed-file comparison. Do not merge first and verify the target afterward.

</details>

- [Back to top](#back-to-top)

---

## <a id="create-contribution-request">Focused PR Proposals with Descriptions and Change Context</a>

<details>
<summary>Click for details</summary>

A useful PR describes **the problem**, **change scope**, verification approach, compatibility impact and linked work item where appropriate. Azure Repos lets authors select source/target, title, description, reviewers and work items; creating a PR does not create a new Git commit by itself. Reviewers need an argument for accepting the change, not just a title like “fix bug.”

For a refund-rule change, describe before/after behavior, edge cases, API implications, tests and the issue context. Choose reviewers familiar with payment logic, and never expose tokens or personal data in PR descriptions.

### References

- [Microsoft Learn — create contribution request](https://learn.microsoft.com/en-us/azure/devops/repos/git/pull-requests?view=azure-devops)

</details>

- [Back to top](#back-to-top)

---

## <a id="draft-vs-ready-pull-request">Draft Pull Requests Versus Ready-for-Review PRs</a>

<details>
<summary>Click for details</summary>

A **draft PR** signals that the author is still gathering early feedback or finishing work; it is not a claim that the change can merge. Marking a PR **ready for review** invites formal review, but required checks and permissions remain independent.

Alice might draft a PR to share a design direction, then mark it ready after tests and description are complete. Inspect the visible Draft/Active state; changing it does not bypass rights or turn failing validations into passing results.

</details>

- [Back to top](#back-to-top)

---

## <a id="verify-contribution-path">Contribution Path Evidence: Author, Permissions, and Target Branch</a>

<details>
<summary>Click for details</summary>

Contribution evidence includes author, source repository/branch, target repository/branch, Draft/Active state, relevant source rights and target policies. Separate whether someone could **create the PR**, **push its source**, and **complete it**—these are different capabilities.

For a fork-based PR, inspect the PR's two repository selectors and security for fork/upstream, then the target policy summary. An open PR that cannot complete is not proof that the fork is invalid; review approvals, checks and completion rights first.

</details>

- [Back to top](#back-to-top)
