<a id="back-to-top"></a>

# Foundations of Source Collaboration on GitHub

## Menu
- [GitHub Repository Hosting and the Need for Team Collaboration](#github-as-hosting-platform)
- [Unreviewed Changes, Lost Context, and Unclear Acceptance Without Shared Collaboration](#unmanaged-change-collaboration-risks)
- [Git Version History Versus GitHub Collaboration Responsibilities](#git-versus-github)
- [GitHub Repositories: Source, History, and Collaboration Context](#repository-and-history)
- [Personal Accounts, Organizations, and Repository Context](#accounts-organizations-and-repository-context)
- [Repository Owners, Collaborators, and Contributors](#participants-and-contribution)
- [Pull Requests as Proposals Before Changes Enter Shared Source](#pull-request-basics)
- [Relationships Among Repositories, Source/Target Branches, Pull Requests, and Reviewers](#github-collaboration-map)

## <a id="github-as-hosting-platform">GitHub Repository Hosting and the Need for Team Collaboration</a>

<details>
<summary>Click for details</summary>

GitHub hosts Git repositories while providing pull requests, discussions, access control, and rules for accepting changes. A team passing ZIP folders around struggles to establish which proposal was approved and why. GitHub puts source history and collaboration decisions into an inspectable shared context.

**Learning route:** start with repositories, source/target branches, PRs, contributors/reviewers and the Git-versus-GitHub boundary. Then explore personal/organization repository hosting and visibility, contributing through a shared branch or fork, PR review and access roles, branch protection/rulesets, Issues and Releases; finally trace one issue through an accepted change. Git command internals and CI/CD implementation remain separate subjects.

Consider a booking team where one developer changes prices and another changes email notifications. Each can propose work without independently replacing the shared branch. Open a sample repository and identify its owner plus Code, Pull requests, and Issues. Source appearing on GitHub does not prove that it has been reviewed or deployed.

### References
- [GitHub Docs — About repositories](https://docs.github.com/en/repositories/creating-and-managing-repositories/about-repositories)

</details>

- [Back to top](#back-to-top)

---

## <a id="unmanaged-change-collaboration-risks">Unreviewed Changes, Lost Context, and Unclear Acceptance Without Shared Collaboration</a>

<details>
<summary>Click for details</summary>

A syntactically valid change can still violate business requirements. Without review and recorded decisions, a team cannot reliably tell who examined it, what was checked, or why it was merged. Review reduces risk but does not replace testing or domain understanding.

Imagine a PR changing monetary rounding. Its description should provide sample inputs, expected outputs, and an edge case. A reviewer may question the tax rule behind a one-line edit; the answer becomes part of the discussion record. Inspect a PR's description, Files changed, comments, and merge status. Identify which decision becomes difficult to defend if one of those records is absent.

</details>

- [Back to top](#back-to-top)

---

## <a id="git-versus-github">Git Version History Versus GitHub Collaboration Responsibilities</a>

<details>
<summary>Click for details</summary>

Git is a distributed version-control system: commits, branches, and history exist locally without GitHub. GitHub hosts Git data and adds PRs, reviews, Issues, Releases, and governance. Consequently, performing a Git merge is distinct from satisfying a hosted PR's review and eligibility rules.

A developer merging local branches does not produce the team's required GitHub review evidence. Conversely, an Approve review does not automatically integrate code into the base branch. This module introduces only enough Git vocabulary to read the GitHub interface. Commands and history internals belong to the Git module (source-control/git); team branch-model policy belongs to Branching Strategy.

</details>

- [Back to top](#back-to-top)

---

## <a id="repository-and-history">GitHub Repositories: Source, History, and Collaboration Context</a>

<details>
<summary>Click for details</summary>

A GitHub repository exposes source files and commits per branch alongside collaboration context. Code shows the selected branch's tree, commit history records version points, and PRs display proposals that might not yet exist on the destination branch. The default branch is not automatically the deployed release.

A new DiscountPolicy file can appear in a PR's Files changed tab while remaining absent from Code on main. That is expected before merging. Switch branches in the interface, inspect a file's history, and compare the PR's head and base. Record repository, branch, commit, and PR state together whenever using screenshots as evidence.

</details>

- [Back to top](#back-to-top)

---

## <a id="accounts-organizations-and-repository-context">Personal Accounts, Organizations, and Repository Context</a>

<details>
<summary>Click for details</summary>

A repository belongs to a personal account or an organization. An organization manages members, teams, and repositories; an enterprise may govern several organizations. The owner/repository label identifies ownership, not the viewer's effective permissions.

For example, alice/demo is a personal repository whereas company/payments belongs to the company organization. A maintenance team should grant appropriate individual or team access rather than share a personal account. Inspect repository ownership and, if authorized, Settings and access management. Distinguish needing to read, review, contribute, or administer before requesting elevated access.

</details>

- [Back to top](#back-to-top)

---

## <a id="participants-and-contribution">Repository Owners, Collaborators, and Contributors</a>

<details>
<summary>Click for details</summary>

An owner governs the account or organization; a collaborator has been granted repository access; a contributor has contributed work but may have no present push permission. A reviewer evaluates a specific PR. One person can hold multiple roles, but those labels are not interchangeable.

An external developer opening a PR from a fork can be a contributor without write access to upstream. A reviewer is not automatically permitted to change branch protection. For an author, reviewer, and administrator, list each required action and the narrowest permission that supports it. This prepares the later repository-role chapter.

</details>

- [Back to top](#back-to-top)

---

## <a id="pull-request-basics">Pull Requests as Proposals Before Changes Enter Shared Source</a>

<details>
<summary>Click for details</summary>

A pull request (PR) proposes integrating changes from the source head branch into the target base branch. It gathers the change description, file differences, discussion, review decisions, and merge status. Opening a PR does not accept code; Approve is not the same as Merge.

For example, a pricing fix uses feature/discount-fix as head and main as base. Reviewers may Comment, Approve, or Request changes; the author can update the proposal before a final decision. Locate head/base, Files changed, Reviews, and Draft/Open/Merged/Closed status. Closing a PR without merging does not integrate its proposal into the target.

</details>

- [Back to top](#back-to-top)

---

## <a id="github-collaboration-map">Relationships Among Repositories, Source/Target Branches, Pull Requests, and Reviewers</a>

<details>
<summary>Click for details</summary>

A repository stores source; the head branch carries a proposal; the base is its destination; a PR connects both; reviewers evaluate; permissions and protection rules govern merge eligibility. An Issue can explain the original need, while a Release communicates an accepted result. These objects connect but do not replace one another.

Use one scenario throughout the module: Issue #42 reports a rounding bug, an author opens PR #57 against main, a reviewer asks for an edge case, and an authorized maintainer merges after requirements are met. The team checks the Issue and later publishes release information. Draw Issue → head/base → PR → review → merge → Release, noting visible evidence at each step.

</details>

- [Back to top](#back-to-top)
