<a id="back-to-top"></a>

# Organizing and Sharing GitHub Repositories

## Menu
- [Personal vs Organization-Owned Repositories](#personal-vs-organization-repos)
- [Public, Private, and Enterprise-Wide Internal Visibility on GitHub Enterprise Cloud](#repository-visibility-and-plans)
- [Repository Details, Sharing Settings, and Default Branch](#repository-settings-and-default-branch)
- [Repository Discovery, Visibility, and Contribution Permissions](#repository-access-and-discovery)

## <a id="personal-vs-organization-repos">Personal vs Organization-Owned Repositories</a>

<details>
<summary>Click for details</summary>

Personal repositories suit experiments under individual ownership; organization repositories support teams, organizational access, and shared governance. Moving source from a personal namespace to an organization changes ownership, permissions, URL context, and potentially integrations. Decide who should own the repository before inviting collaborators.

For example, a company's payment source should not depend on a single employee's personal account. During repository creation, choose the correct owner, visibility, and backup administrators. In Settings, distinguish people who govern the repository from contributors who only need to propose changes.

</details>

- [Back to top](#back-to-top)

---

## <a id="repository-visibility-and-plans">Public, Private, and Enterprise-Wide Internal Visibility on GitHub Enterprise Cloud</a>

<details>
<summary>Click for details</summary>

**Public** permits Internet-wide reading; **private** limits access to authorized people; **internal** on GitHub Enterprise Cloud grants read access to enterprise members, even in other organizations within the enterprise. Internal does not simply mean "organization members only." Availability also depends on the enterprise/account model and policy.

A cross-department shared codebase may suit internal visibility, while source restricted to one sensitive team may warrant private. Before changing visibility, inspect who gains or loses access and what happens to forks; do not treat the dropdown as the entire security model. Visibility complements, rather than replaces, secret management.

### References
- [GitHub Docs — About repositories](https://docs.github.com/en/enterprise-cloud@latest/repositories/creating-and-managing-repositories/about-repositories)

</details>

- [Back to top](#back-to-top)

---

## <a id="repository-settings-and-default-branch">Repository Details, Sharing Settings, and Default Branch</a>

<details>
<summary>Click for details</summary>

Repository name, description, owner, visibility, default branch, and collaboration controls establish repository-wide context. The **default branch** is GitHub's default Code view and typical target for new PRs; it does not establish what is deployed or prohibit all other branches.

If a team changes its default from master to main, newly opened PRs may target main, while existing PRs and integrations deserve separate inspection. Record the before/after settings and confirm the actor has sufficient access. Organization or enterprise policy can limit settings even when a repository-level UI is visible.

</details>

- [Back to top](#back-to-top)

---

## <a id="repository-access-and-discovery">Repository Discovery, Visibility, and Contribution Permissions</a>

<details>
<summary>Click for details</summary>

Repository **discoverability**, **read access**, and **permission to propose changes** are separate concepts. Someone can see a public repository and contribute by fork/PR without being allowed to push directly. Reading a private repository does not imply administrative access. Internal visibility follows enterprise membership rather than an individual team name.

If a collaborator says "I cannot find the repo," first check the URL and visibility. If they can browse it but cannot edit the upstream branch, check their role and contribution path. Differentiate inaccessible repository, missing Settings controls, and a blocked PR: each points to a different authorization or governance layer.

</details>

- [Back to top](#back-to-top)
