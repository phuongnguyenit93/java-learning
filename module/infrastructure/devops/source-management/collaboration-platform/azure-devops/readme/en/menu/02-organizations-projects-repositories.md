<a id="back-to-top"></a>

# Organizations, Projects, and Git Repositories

## Menu
- [Azure DevOps Organization, Project, and Membership Hierarchy](#organization-project-hierarchy)
- [Project Administration Boundaries Versus Git Repositories](#project-and-repository-boundary)
- [Multiple Git Repositories Within One Project](#multiple-git-repositories-in-project)
- [Administrative Roles in Creating, Sharing, and Managing Repositories](#repository-owner-admin-roles)
- [Project Visibility Versus Repository Access](#project-visibility-versus-repo-access)
- [Azure DevOps Services Public Projects: New Creation Ends in 2026 and Private Conversion in 2027](#public-project-retirement-2026)
- [Effects of Enabling or Disabling Azure Repos](#enable-disable-repos-service)
- [Evidence of Project, Repository, and Administration Boundaries](#verify-project-repo-structure)

## <a id="organization-project-hierarchy">Azure DevOps Organization, Project, and Membership Hierarchy</a>

<details>
<summary>Click for details</summary>

An **organization** contains projects and controls account-level users, groups and access. Each **project** can expose Repos and other enabled services. Being present in the organization or a project is necessary for many actions, but effective rights still depend on permissions and access level.

Suppose `RetailCo` contains `Checkout` and `Warehouse`; Alice's Checkout membership does not automatically grant edit rights to Warehouse repositories. Verify the organization in `dev.azure.com/<org>`, then Projects and Project settings to locate the boundary.

</details>

- [Back to top](#back-to-top)

---

## <a id="project-and-repository-boundary">Project Administration Boundaries Versus Git Repositories</a>

<details>
<summary>Click for details</summary>

A **project** is the broader governance boundary for members, groups and service settings. A **repository** has its own Read, Contribute, creation/management and policy-related rights, with potentially more specific branch permissions. A project is not just another name for a single Git repository.

For example, the fictional `Checkout` project contains `checkout-api` and `checkout-web`. Backend engineers might contribute to `checkout-api` but only read `checkout-web`. Inspect Project settings → Repositories → the selected repository → Security rather than inferring all rights from a project-level role.

</details>

- [Back to top](#back-to-top)

---

## <a id="multiple-git-repositories-in-project">Multiple Git Repositories Within One Project</a>

<details>
<summary>Click for details</summary>

One Azure DevOps project may host **multiple Git repositories**, each with separate refs, history, settings and effective permissions. This can share project-level coordination while separating codebases, at the cost of more cross-repository change management.

If Checkout has `checkout-api` and `checkout-web`, an API contract change may require separate PRs. Azure Repos does not automatically deploy those PRs atomically. The strategic choice between monorepo and polyrepo belongs to the monorepo-polyrepo module.

</details>

- [Back to top](#back-to-top)

---

## <a id="repository-owner-admin-roles">Administrative Roles in Creating, Sharing, and Managing Repositories</a>

<details>
<summary>Click for details</summary>

Creating repositories requires appropriate project-level rights such as **Create repository**. Changing repo permissions or policies requires relevant administration rights, for example Manage permissions or Edit policies. **Project Administrators** commonly manage project resources, but administrator membership should not be treated as automatic permission to bypass branch policies.

Before provisioning a partner-facing repository, confirm owning project, responsible team and security groups. After creation inspect Project settings → Repositories. Avoid granting broad Manage permissions merely to let a developer contribute code.

</details>

- [Back to top](#back-to-top)

---

## <a id="project-visibility-versus-repo-access">Project Visibility Versus Repository Access</a>

<details>
<summary>Click for details</summary>

**Project visibility** determines whether the project is publicly discoverable or requires authenticated/member access. **Repository permissions** govern which eligible identities may Read, Contribute or administer a particular repository or branch. For private-project code access, Basic-or-higher access and appropriate permissions commonly apply.

A historically public project never meant anonymous contributors could push. For an access failure or missing Repos tab, separate visibility, Stakeholder-versus-Basic access level, membership, service enablement and effective repository rights.

</details>

- [Back to top](#back-to-top)

---

## <a id="public-project-retirement-2026">Azure DevOps Services Public Projects: New Creation Ends in 2026 and Private Conversion in 2027</a>

<details>
<summary>Click for details</summary>

This is an **Azure DevOps Services** lifecycle change, not a Git rule. The **2026 retirement** notice says public-project creation is no longer generally available and remaining public projects will be automatically converted to **private in 2027**, ending anonymous access. **Legacy-policy caveat:** Microsoft's *About projects* page still describes a restricted exception for **organizations that had already enabled Allow public project**, while new organizations cannot enable that policy. As retirement notices and legacy behavior evolve, never promise that a given organization can create a public project without checking current policy. Do not assume the same timetable applies to on-premises Azure DevOps Server.

Teams with public projects should inventory memberships, anonymous integrations and links before private conversion. Microsoft suggests evaluating GitHub for ongoing public-code hosting. Consult the current retirement notice for the precise rollout details rather than assuming visibility can remain public indefinitely.

### References

- [Microsoft Learn — public project retirement 2026](https://learn.microsoft.com/en-us/azure/devops/organizations/projects/public-projects-retirement?view=azure-devops)
- [Microsoft Learn — About projects: legacy public-policy exception](https://learn.microsoft.com/en-us/azure/devops/organizations/projects/about-projects?view=azure-devops)

</details>

- [Back to top](#back-to-top)

---

## <a id="enable-disable-repos-service">Effects of Enabling or Disabling Azure Repos</a>

<details>
<summary>Click for details</summary>

Enabling **Azure Repos** at project level exposes repository and PR workflows. If the service is disabled, Repos pages may disappear even for users who previously had correct Git permissions. That is different from a repository Deny or an insufficient access level.

When the Repos hub is missing, have an authorized administrator inspect Project settings → Overview/Services (UI labels may vary). Confirm service availability before randomly changing repository security entries.

</details>

- [Back to top](#back-to-top)

---

## <a id="verify-project-repo-structure">Evidence of Project, Repository, and Administration Boundaries</a>

<details>
<summary>Click for details</summary>

Evidence exercise: open `RetailCo/Checkout`, record the **organization, project and selected repository**, then inspect Project settings → Repositories and the target branch used by its PRs. Switch to Warehouse: Checkout's repository is not relocated there simply because the same organization owns both projects.

Safe evidence includes project path, repository name, Read-capable group and administrative owner; exclude access tokens and sensitive account details. Visibility in a list alone does not establish permission to push or approve.

</details>

- [Back to top](#back-to-top)
