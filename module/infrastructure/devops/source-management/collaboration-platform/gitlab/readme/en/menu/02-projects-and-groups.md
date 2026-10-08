<a id="back-to-top"></a>

# GitLab Project and Group Organization

## Menu
- [Project Ownership: Personal Namespace vs Group](#project-namespace-ownership)
- [Organizing Groups, Subgroups, and Their Projects](#groups-and-subgroups)
- [Repositories and Project-Level Governance Settings](#project-repository-settings)
- [Public, Private, Internal: Existing GitLab.com Internal Projects Remain, New Ones Disallowed](#project-visibility-offerings)
- [Group and Project Boundaries for Source Sharing and Collaboration](#projects-group-collaboration-boundary)

## <a id="project-namespace-ownership">Project Ownership: Personal Namespace vs Group</a>

<details>
<summary>Click for details</summary>

A personal project suits individual experimentation; a group-owned project better supports source that must outlive one employee and use shared administration. `alice/demo` and `company/payments/gateway` have different owners; a grouped project can inherit parent policies. Moving projects can affect URLs, membership, and integrations.

A payment gateway should not depend on An's personal account. Before creating the project, select an appropriate group namespace, designate maintainers, and plan access. Verify ownership using the project path and breadcrumbs rather than assuming the person who created it remains the owner.

</details>

- [Back to top](#back-to-top)

---

## <a id="groups-and-subgroups">Organizing Groups, Subgroups, and Their Projects</a>

<details>
<summary>Click for details</summary>

Groups govern collections of projects; subgroups subdivide ownership by product or team. `company/payments` could contain gateway and billing projects; parent membership and policy may flow to children. A subgroup is a GitLab governance object, not a source folder or Git branch.

Browse a group's Subgroups and Projects pages and record common members and accountable administrators. Do not equate this hierarchy mechanically with deployment: several services can share a group, while a project need not be exactly one deployed service.

### References
- [GitLab Docs — Groups](https://docs.gitlab.com/user/group/)

</details>

- [Back to top](#back-to-top)

---

## <a id="project-repository-settings">Repositories and Project-Level Governance Settings</a>

<details>
<summary>Click for details</summary>

A GitLab project combines a Git repository with visibility, membership, default branch, MR, and feature settings. The default branch is the normal Code view and common target for proposals; it is not automatically the version running in production.

When switching the default branch from master to main, inspect open MRs, branch protections, and affected integrations. Read access does not guarantee permission to modify Settings. Compare the selected Code branch, project configuration, and actor role before interpreting any state change.

</details>

- [Back to top](#back-to-top)

---

## <a id="project-visibility-offerings">Public, Private, Internal: Existing GitLab.com Internal Projects Remain, New Ones Disallowed</a>

<details>
<summary>Click for details</summary>

**Public** exposes public content to anyone; **private** requires appropriate access; **internal** on GitLab Self-Managed or Dedicated is accessible to authenticated instance users other than external users. On **GitLab.com, new Internal projects cannot be created**, although existing legacy Internal projects retain their setting. GitLab Internal is not the same entitlement model as GitHub Enterprise internal repositories.

For proprietary payment source on GitLab.com, choose private rather than looking for an unavailable Internal option. Before changing visibility, inspect who can clone or browse, member privileges, and higher-level restrictions. Visibility does not replace separate token or secret management.

### References
- [GitLab Docs — Project and group visibility](https://docs.gitlab.com/user/public_access/)

</details>

- [Back to top](#back-to-top)

---

## <a id="projects-group-collaboration-boundary">Group and Project Boundaries for Source Sharing and Collaboration</a>

<details>
<summary>Click for details</summary>

Groups govern common ownership and policy, while projects hold their own repositories, MRs, and Issues. Parent-group members may inherit project access; someone invited to one project does not automatically administer its siblings. This boundary determines who can browse code, invite others, and complete changes.

A contractor who only needs gateway access should not be invited to the entire payments group by default. Map parent groups, direct members, and inherited membership before expanding access. The next chapter turns this structure into effective roles and authorization decisions.

</details>

- [Back to top](#back-to-top)
