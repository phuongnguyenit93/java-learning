<a id="back-to-top"></a>

# Access and Repository Governance

## Menu
- [Workspace, Project, and Repository Permission Scopes](#bitbucket-permission-scopes)
- [Users, Groups, and Administrators](#users-groups-and-administrators)
- [Workspaces Managed in Bitbucket Versus Atlassian Administration](#workspace-admin-environments)
- [Effects of Project Permissions on Member Repositories](#project-permission-inheritance)
- [Read, Write, Admin, and Visibility Boundaries](#read-write-admin-and-visibility)
- [Recognizing Excessive or Missing Permissions](#access-governance-failure-modes)

## <a id="bitbucket-permission-scopes">Workspace, Project, and Repository Permission Scopes</a>

<details>
<summary>Click for details</summary>

Bitbucket Cloud has several permission scopes because a team should not have to grant access independently for every repository in a larger organization. A **workspace** organizes team membership, a **project** can grant access across member repositories, and a **repository** has its own resource access and administration. These scopes connect, but there is no universal “workspace member may do everything” permission.

In Orchid, accounting may need to read `invoice-api`, engineers need to contribute, and Mai administers the Billing project. The effective permissions must be inspected at the applicable scope, including inherited project grants; seeing a workspace does not prove permission to modify a private repository.

**Practice:** draw the `orchid-team` workspace containing project Billing and two repositories. For each level, note which access questions to check before inviting an outside collaborator.

### References
- [Atlassian — Configure project permissions](https://support.atlassian.com/bitbucket-cloud/docs/configure-project-permissions-for-users-and-groups/)

</details>

- [Back to top](#back-to-top)

---

## <a id="users-groups-and-administrators">Users, Groups, and Administrators</a>

<details>
<summary>Click for details</summary>

Permissions for **individual users** handle exceptions; **groups** reduce administrative work when people join or leave teams. **Administrators** manage access and settings at their assigned scope. Administrative roles are not interchangeable: being a project administrator is not automatically being an Atlassian organization administrator.

In Orchid, group `billing-developers` can receive Write access to project Billing instead of creating ten individual grants across two repositories. Removing An from that group should remove access provided through it, but any separate direct grant must still be investigated.

**Evidence:** reviewing effective grants explains why An can modify source. **Exercise:** compare granting five users independently with managing one group, then consider a developer transferring to another department.

</details>

- [Back to top](#back-to-top)

---

## <a id="workspace-admin-environments">Workspaces Managed in Bitbucket Versus Atlassian Administration</a>

<details>
<summary>Click for details</summary>

Bitbucket Cloud currently has **two workspace administration environments**. Older workspaces can still be administered within Bitbucket, while newly provisioned workspaces are managed through **Atlassian Administration** in an Atlassian organization. The workspace→project→repository organization remains useful, but the location of **user and application access administration** changes.

For Atlassian-administered workspaces, organization/user-access administrators grant workspace app access; **project and repository content permissions continue to be managed in Bitbucket**. Instructions for older Bitbucket-managed groups and invitations must not be applied blindly. Atlassian's current documentation says there is no straightforward supported conversion of an existing Bitbucket-administered workspace into a newly organization-managed workspace.

**Scenario:** Mai cannot find the old invitation control in a newly provisioned workspace. Rather than assuming a broken account, identify the workspace administration environment and the appropriate administrator.

### References
- [Atlassian — What is a workspace?](https://support.atlassian.com/bitbucket-cloud/docs/what-is-a-workspace/)

</details>

- [Back to top](#back-to-top)

---

## <a id="project-permission-inheritance">Effects of Project Permissions on Member Repositories</a>

<details>
<summary>Click for details</summary>

A permission granted at the **project** level can apply to current and future repositories in that project. Atlassian documents a hierarchy of project rights—**Read, Write, Create, Admin**—where higher levels encompass lower permissions. This provides consistency but can also spread access more broadly than intended.

If `billing-developers` has project Write in Billing, those members may modify both `invoice-api` and `billing-docs`, even when their responsibilities cover only the API. Repository grants must be inspected **alongside inherited project grants**; removing a direct repository grant does not necessarily remove a project-derived right.

**Exercise:** Mai adds a sensitive `payroll-secrets` repository to Billing. Identify groups that would inherit access and consider whether a different project or narrower project policy is more suitable. Verify both group and direct grants after changes.

### References
- [Atlassian — Configure project permissions](https://support.atlassian.com/bitbucket-cloud/docs/configure-project-permissions-for-users-and-groups/)

</details>

- [Back to top](#back-to-top)

---

## <a id="read-write-admin-and-visibility">Read, Write, Admin, and Visibility Boundaries</a>

<details>
<summary>Click for details</summary>

**Read** generally permits viewing source; **Write** enables contributions; **Admin** governs settings and permissions within the relevant scope. Projects also define a **Create** level for repository creation. Do not blindly apply one four-level hierarchy to every resource type: repository and project rights have distinct descriptions.

**Visibility** is separate from modification rights. A *public* repository may be readable by outsiders who still cannot write to it; a *private* repository requires appropriate access. A public Bitbucket project can contain private repositories, while private projects restrict discovery and should not be treated as automatically exposing their repositories.

**Observation:** Binh can read PR differences but cannot manage Billing project settings—an intended access boundary, not an error. **Check:** select the least privileged role for an auditor who only needs to inspect the code and explain why Admin is inappropriate.

</details>

- [Back to top](#back-to-top)

---

## <a id="access-governance-failure-modes">Recognizing Excessive or Missing Permissions</a>

<details>
<summary>Click for details</summary>

Access problems have two broad classes. **Missing access:** An cannot view a repository or perform a permitted contribution. **Excessive access:** someone who only needs to read can change settings or modify critical source. Unclear administrator ownership makes teams respond slowly and encourages unsafe escalation.

A safe diagnosis records *which person, which operation, which project/repository, and what observed denial or warning*. Then inspect workspace/application access, project grants, repository grants, and branch restrictions as relevant. Do not “just make everyone Admin”; it hides the underlying problem. In Atlassian-administered workspaces, app access may be managed separately from repository permissions.

**Practice:** An can read `invoice-api` but cannot complete a PR action. Mai should inspect the actual permission required and branch state instead of assuming every operation is covered by Read. Capture before/after evidence of the least-privilege correction.

</details>

- [Back to top](#back-to-top)
