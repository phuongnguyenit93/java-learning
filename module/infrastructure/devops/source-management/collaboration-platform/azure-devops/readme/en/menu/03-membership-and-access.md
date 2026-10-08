<a id="back-to-top"></a>

# Membership, Security Groups, and Effective Permissions

## Menu
- [Access Levels Versus Security Permissions](#access-level-vs-security-permission)
- [Readers, Contributors, and Project Administrators: Default Permissions](#readers-contributors-project-admins)
- [Direct Permissions Versus Security Group Membership](#direct-and-group-membership)
- [Organization, Project, Repository, and Branch Permission Scopes and Inheritance](#permission-scopes-inheritance)
- [Allow, Deny, Not Set, and Effective Permissions](#allow-deny-not-set)
- [Relationship Between Explicit, Inherited, and Effective Permissions](#explicit-inherited-effective-rights)
- [Git Repository Read, Contribute, and Administration Permissions](#access-to-read-contribute-admin)
- [Interactions Among Deny, Inherited Rights, and Scope-Specific Settings](#deny-and-specificity-exceptions)
- [Contribute to Pull Requests Versus Branch Contribute Permissions](#contribute-to-pr-vs-code)
- [Diagnosing Effective Permissions for Repository Access Failures](#troubleshoot-effective-permissions)

## <a id="access-level-vs-security-permission">Access Levels Versus Security Permissions</a>

<details>
<summary>Click for details</summary>

An **access level** such as Basic, Basic + Test Plans or Stakeholder controls feature entitlement. A **security permission** controls whether a user/group can Read, Contribute, Edit policies or Manage permissions on a particular resource. Both matter: allowing repository Read does not necessarily grant Azure Repos in a private project to a Stakeholder-only user.

If a colleague can view work items but not code, inspect their access level in Organization settings → Users and project membership before widening repository rights. Licensing and service editions may differ between Azure DevOps Services and Server.

### References

- [Microsoft Learn — access level vs security permission](https://learn.microsoft.com/en-us/azure/devops/organizations/security/access-levels?view=azure-devops)

</details>

- [Back to top](#back-to-top)

---

## <a id="readers-contributors-project-admins">Readers, Contributors, and Project Administrators: Default Permissions</a>

<details>
<summary>Click for details</summary>

Default groups often include **Readers** (read code/reviews given suitable access), **Contributors** (read and contribute to branches and PR workflows), and **Project Administrators** (manage project settings). These are starting groups, not a guarantee of identical effective rights once Deny assignments, object-level overrides and policies apply.

For a partner who only needs to inspect a PR, choose appropriately scoped read access instead of adding them to Project Administrators. Check the target user's effective permissions; project administration does not automatically mean policy-bypass rights.

### References

- [Microsoft Learn — readers contributors project admins](https://learn.microsoft.com/en-us/azure/devops/organizations/security/permissions-access?view=azure-devops)

</details>

- [Back to top](#back-to-top)

---

## <a id="direct-and-group-membership">Direct Permissions Versus Security Group Membership</a>

<details>
<summary>Click for details</summary>

Permissions may be assigned **directly to a user** or obtained through one or more **security groups**. Group management simplifies team changes but creates interactions: Alice may belong to Contributors and to a separate restricted group, where a Deny for the same permission can defeat an Allow.

When a directly allowed contributor cannot act, inspect all memberships and effective permissions—not merely their individual entry. Before editing a broad group, consider every affected member and prefer least-privilege groups to widespread Deny rules.

</details>

- [Back to top](#back-to-top)

---

## <a id="permission-scopes-inheritance">Organization, Project, Repository, and Branch Permission Scopes and Inheritance</a>

<details>
<summary>Click for details</summary>

Azure DevOps permissions range from **organization/project**, down to **Git repository** and **branch** scopes. Parent settings can be inherited; a repository's Contribute allowance may be the branch default, while a protected branch can receive a more specific assignment.

To diagnose repo-versus-main differences, inspect Project settings → Repositories → Security, then Repos → Branches → Branch security. Do not assume all parent Deny entries always win: an explicit child-object assignment can replace an inherited setting for that identity.

### References

- [Microsoft Learn — permission scopes inheritance](https://learn.microsoft.com/en-us/azure/devops/organizations/security/about-permissions?view=azure-devops)

</details>

- [Back to top](#back-to-top)

---

## <a id="allow-deny-not-set">Allow, Deny, Not Set, and Effective Permissions</a>

<details>
<summary>Click for details</summary>

The principal configured states are **Allow**, **Deny**, and **Not set**. Allow grants at that scope, Deny restricts, and Not set neither grants nor blocks another applicable grant. Effective outcomes also depend on groups, object inheritance, scope and certain system-managed permissions.

If Readers grants Read and Restricted denies Read at the same scope, Deny generally prevails for someone in both groups. If Restricted is merely Not set, it does not cancel Readers' Allow. Inspect effective permissions rather than infer rights from one checkbox.

### References

- [Microsoft Learn — allow deny not set](https://learn.microsoft.com/en-us/azure/devops/organizations/security/about-permissions?view=azure-devops)

</details>

- [Back to top](#back-to-top)

---

## <a id="explicit-inherited-effective-rights">Relationship Between Explicit, Inherited, and Effective Permissions</a>

<details>
<summary>Click for details</summary>

**Explicit** means a setting directly assigned at a scope/identity; **inherited** is received from a parent object or group; **effective** is the result evaluated for an operation. These are not synonyms: a visible “Not set” can coexist with effective Allow through another group.

If Alice can read `checkout-api` but not `checkout-web`, inspect the selected repositories and available “Why?”/effective-permission explanation. Record which assignment caused the outcome rather than changing every project-level permission.

</details>

- [Back to top](#back-to-top)

---

## <a id="access-to-read-contribute-admin">Git Repository Read, Contribute, and Administration Permissions</a>

<details>
<summary>Click for details</summary>

**Read** permits viewing/cloning/fetching code, **Contribute** covers updates to an eligible branch, while **Create branches** and **Create tags** may be separate privileges. **Edit policies** and **Manage permissions** are administrative; **Force push** can rewrite or remove history. Reading code is not permission to push, and Contribute does not grant policy bypass.

For a bug-fix branch, verify Basic access, Read, Create branches and relevant Contribute rights. The engineer typically does not need Manage permissions. Test with a safe feature branch instead of attempting a force push on main.

### References

- [Microsoft Learn — access to read contribute admin](https://learn.microsoft.com/en-us/azure/devops/repos/git/set-git-repository-permissions?view=azure-devops)

</details>

- [Back to top](#back-to-top)

---

## <a id="deny-and-specificity-exceptions">Interactions Among Deny, Inherited Rights, and Scope-Specific Settings</a>

<details>
<summary>Click for details</summary>

Avoid both blanket claims: “Deny always wins every hierarchy” and “a direct Allow always beats a group.” When **combining identities/groups at a scope**, Deny normally wins; for **object inheritance**, a child-level explicit assignment can override the inherited parent value for that identity. System/admin privileges can behave specially depending on operation and version.

If the `release` branch behaves differently from the repository default, inspect its Branch security effective outcome. Prefer deliberately scoped groups instead of spreading Deny exceptions across large hierarchies.

</details>

- [Back to top](#back-to-top)

---

## <a id="contribute-to-pr-vs-code">Contribute to Pull Requests Versus Branch Contribute Permissions</a>

<details>
<summary>Click for details</summary>

**Contribute to pull requests** governs permitted PR participation (including reading, commenting and voting where eligible). It is **different** from repository/branch **Contribute** permission for pushing commits. The default permissions matrix permits **Readers to participate in PRs**, while Microsoft's *About pull requests* calls for **Contributors or equivalent permissions to create and complete a PR**. Completion also depends on effective permissions on the **target branch** and its policies: commenting, creating, completing, and pushing are not interchangeable rights.

Bob may review a PR while a direct `git push` to main is rejected. For creation/completion failures, check project access level, PR rights, Contributor-equivalent permissions, the target branch, and its policies. For push rejection, inspect branch Contribute separately; broad Manage permissions are not a remedy.

### References

- [Microsoft Learn — contribute to pr vs code](https://learn.microsoft.com/en-us/azure/devops/repos/git/set-git-repository-permissions?view=azure-devops)
- [Microsoft Learn — About pull requests and prerequisites](https://learn.microsoft.com/en-us/azure/devops/repos/git/about-pull-requests?view=azure-devops)

</details>

- [Back to top](#back-to-top)

---

## <a id="troubleshoot-effective-permissions">Diagnosing Effective Permissions for Repository Access Failures</a>

<details>
<summary>Click for details</summary>

For “Repos missing / clone denied / push rejected,” check in order: **Services versus Server**, whether Repos is enabled, sufficient access level for a private repo, project membership, effective Read/Contribute on the selected repository and branch, Deny/Not set interactions, and finally target policies. A missing hub is not the same failure as a rejected push.

Capture the operation, UI/Git message and relevant identity/scope without recording secrets. Use security/effective-rights explanations to change the **specific assignment**, then retry a low-risk operation rather than disabling every policy.

</details>

- [Back to top](#back-to-top)
