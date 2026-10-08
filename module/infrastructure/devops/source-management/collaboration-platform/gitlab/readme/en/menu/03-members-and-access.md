<a id="back-to-top"></a>

# Members, Roles, and Access Permissions

## Menu
- [Guest, Planner, Reporter, Developer, Maintainer, and Owner Roles](#access-roles-overview)
- [Direct, Inherited, and Shared Project Membership](#direct-inherited-shared-membership)
- [Effective Project Permissions and Group Governance Scopes](#effective-project-permissions)
- [Permissions to View, Propose, Review, and Merge Changes](#access-for-mr-contribution)
- [Missing Project Access and Excessive Permissions](#permission-troubleshooting)

## <a id="access-roles-overview">Guest, Planner, Reporter, Developer, Maintainer, and Owner Roles</a>

<details>
<summary>Click for details</summary>

GitLab defines **Guest, Planner, Reporter, Developer, Maintainer, and Owner** roles with different capabilities. These are permission sets, not company job titles. A Guest has limited planning-oriented access and generally cannot read private project code; a Reporter can read repository data; Developers contribute according to branch permissions; Maintainers manage much project configuration; Owner is associated with higher-level ownership as supported. Planner addresses planning work where available.

A bug reporter rarely needs Maintainer while a source contributor may need Developer. Use the current feature-specific Roles and permissions matrix rather than assuming a role name implies every action or exists identically in every deployment.

### References
- [GitLab Docs — Roles and permissions](https://docs.gitlab.com/user/permissions/)

</details>

- [Back to top](#back-to-top)

---

## <a id="direct-inherited-shared-membership">Direct, Inherited, and Shared Project Membership</a>

<details>
<summary>Click for details</summary>

Access may be **direct** at the project, **inherited** from a parent group, or provided through a **group shared** with the project. Each path has different administrators and lifecycle. Removing a direct project membership does not necessarily revoke access if an inherited or shared path remains.

Suppose Binh can still read gateway after an invitation is removed. Inspect the Members view and membership source, then check parent groups and shared access. Do not modify broad parent permissions merely to fix a single project's intended boundary.

### References
- [GitLab Docs — Members of a project](https://docs.gitlab.com/user/project/members/)

</details>

- [Back to top](#back-to-top)

---

## <a id="effective-project-permissions">Effective Project Permissions and Group Governance Scopes</a>

<details>
<summary>Click for details</summary>

**Effective access** is what GitLab allows after considering role, membership source, group/project scope, and applicable settings. Appearing in a group does not necessarily permit project administration. Protected branches impose additional restrictions even when a Developer may normally contribute to unprotected branches.

An inherited Developer might be unable to push directly to protected main. That can be intentional branch governance rather than a membership error. Diagnose the user, exact project, membership source, and attempted action before investigating branch rules. Reading an MR is not the same permission as editing project settings.

</details>

- [Back to top](#back-to-top)

---

## <a id="access-for-mr-contribution">Permissions to View, Propose, Review, and Merge Changes</a>

<details>
<summary>Click for details</summary>

Reading repository data, making a source branch, opening an MR, being assigned as reviewer, approving, and merging are separate operations. Someone who can view a public project may contribute through a fork without upstream push rights. A Maintainer may be eligible to merge protected branches while still having to satisfy approval rules.

An auditor might need a review role without editing code, subject to approval eligibility and plan settings. Build a matrix of Read source, Create MR, Approve, and Merge main, then identify the least authority for each actor. Promoting an author to Owner is not a sound fix for missing required approvals.

</details>

- [Back to top](#back-to-top)

---

## <a id="permission-troubleshooting">Missing Project Access and Excessive Permissions</a>

<details>
<summary>Click for details</summary>

Access failures may appear as invisible projects, denied clones, unavailable MR creation, or blocked merging; they have different causes. **Least privilege** grants enough authority for the work without unnecessarily exposing settings or bypass controls. Distinguish membership errors from target-branch protection and required checks.

For a contractor who only handles Issues, Maintainer is usually excessive. Record the denied operation, examine the role and membership source, check visibility, then inspect target rules. A fork contribution route or an eligible reviewer may solve the real need without broader access.

</details>

- [Back to top](#back-to-top)
