<a id="back-to-top"></a>

# Protected Branches and Merge Eligibility

## Menu
- [Protected Branches and Their Source-Governance Purpose](#protected-branches-purpose)
- [Allowed to Merge vs Allowed to Push and Merge](#allowed-to-merge-vs-push)
- [Project vs Top-Level Group Protection: GitLab.com, Self-Managed, Dedicated (Premium/Ultimate, 17.6+)](#project-group-branch-rules)
- [Overlapping Rules: Most Permissive Access vs Strictest Code Owner Requirement](#overlapping-rule-precedence)
- [Required Code Owner Approval on Protected Target Branches](#code-owner-approval-protected-target)
- [Approvals and Check Statuses as Merge Gates, Not Pipeline Implementation](#merge-checks-and-pipeline-boundary)
- [Blocked Merges: Push/Merge Permissions, Approvals, and Check States](#blocked-merge-diagnosis)

## <a id="protected-branches-purpose">Protected Branches and Their Source-Governance Purpose</a>

<details>
<summary>Click for details</summary>

A **protected branch** applies tighter push and merge permissions to important branches such as main, stable, or release. It prevents uncontrolled direct changes and establishes an acceptance path. Protection does not fix bad code; it controls who may act and which requirements apply.

An has Developer access but cannot push directly to main because the team expects an MR. In the relevant GitLab version, inspect Settings → Repository → Protected branches or Branch rules, determine the matching pattern, and read the effective permissions. A denied push may represent intended governance.

</details>

- [Back to top](#back-to-top)

---

## <a id="allowed-to-merge-vs-push">Allowed to Merge vs Allowed to Push and Merge</a>

<details>
<summary>Click for details</summary>

**Allowed to merge** governs who may merge MRs into a protected branch; **Allowed to push and merge** governs direct pushes as well as its configured merge access. Permission to merge an MR is not the same as direct push permission. Overly broad push access can undermine the review path.

Suppose main allows Maintainers to merge but sets Allowed to push and merge to No one. A Maintainer can merge an eligible MR without directly pushing to main. Inspect both controls, predict outcomes for Developer and Maintainer, then compare against GitLab's actual access messages before modifying any rule.

</details>

- [Back to top](#back-to-top)

---

## <a id="project-group-branch-rules">Project vs Top-Level Group Protection: GitLab.com, Self-Managed, Dedicated (Premium/Ultimate, 17.6+)</a>

<details>
<summary>Click for details</summary>

**Project branch rules** govern a particular project. **Group-level protected branches** are available in **Premium/Ultimate** on **GitLab.com, GitLab Self-Managed, and GitLab Dedicated**, and became **Generally Available in GitLab 17.6**. Only an **Owner of a top-level group** can configure this group protection; **subgroups are not supported as configuration locations**. Group rules apply to projects in that group and **cannot be modified from project settings**. A project Maintainer may configure a separate project-level rule for the same branch, but cannot directly edit the inherited group rule; when both rules match, GitLab evaluates their combined effect as explained in the next section.

**UI feature versus REST API:** the official **Protected branches → In a group** documentation explicitly supports all three offerings. The separate **Group-level protected branches REST API** documentation currently lists **Self-Managed only**; that endpoint's offering restriction **does not restrict** the group-protection feature available through the UI.

For example, the Owner of top-level group `company` wants consistent `main` protection for projects `payments/gateway` and `payments/billing`. Before recording, verify the Premium/Ultimate tier, offering, top-level Owner role, and actual **Group → Settings → Repository → Protected branches** screen. Do not imply an inherited project rule can be edited in the project or a new group rule configured directly in subgroup `payments`. If the live system is unavailable, label the example as an illustrated permission model rather than a successful real UI operation.

### References
- [GitLab Docs — Protected branches, In a group (UI; three offerings)](https://docs.gitlab.com/user/project/repository/branches/protected/)
- [GitLab Docs — Group-level protected branches REST API (Self-Managed only)](https://docs.gitlab.com/api/group_protected_branches/)

</details>

- [Back to top](#back-to-top)

---

## <a id="overlapping-rule-precedence">Overlapping Rules: Most Permissive Access vs Strictest Code Owner Requirement</a>

<details>
<summary>Click for details</summary>

When a branch matches several protection rules, GitLab normally uses the **most permissive** settings for push, merge, and force-push access; **Code Owner approval** instead follows the **most restrictive** matching requirement. An exact-name rule does not automatically override a wildcard for all permissions. This differs from GitHub's branch protection selection.

If main matches `main` (push: No one) and `m*` (Developer push allowed), the wildcard may effectively broaden access. If any matching rule requires Code Owner approval, that stricter requirement can remain. Strict administrators must inspect **all matching patterns**, not only the seemingly specific one.

### References
- [GitLab Docs — Protection rules and permissions](https://docs.gitlab.com/user/project/repository/branches/protection_rules/)

</details>

- [Back to top](#back-to-top)

---

## <a id="code-owner-approval-protected-target">Required Code Owner Approval on Protected Target Branches</a>

<details>
<summary>Click for details</summary>

For a protected target branch with the relevant rule enabled, **Code Owner approval** requires an eligible owner of a changed path to approve before merge. This is a GitLab Premium/Ultimate capability; simply adding CODEOWNERS does not create a blocking gate on every tier. Listed owners also need eligible membership and permissions for their approval to count.

MR !57 changes `pricing/`; Binh's general approval may not satisfy the payments team's Code Owner requirement. Examine the matching CODEOWNERS entry, target branch, configured owner-approval rule, and Approval widget. Elevating the author is not a substitute for accountable ownership.

</details>

- [Back to top](#back-to-top)

---

## <a id="merge-checks-and-pipeline-boundary">Approvals and Check Statuses as Merge Gates, Not Pipeline Implementation</a>

<details>
<summary>Click for details</summary>

GitLab can use approvals, unresolved discussions, checks, and pipeline status to decide whether an MR is eligible to merge. A **pipeline** is an automated sequence of jobs such as build/test managed by CI; here we read only its visible result, without authoring `.gitlab-ci.yml` or teaching execution graphs.

An MR may be Approved while the widget reports a failed pipeline. Determine whether the configured merge checks require it, which commit the result evaluates, and who owns the remediation. Passing automation does not replace business review or merge permissions; lack of a pipeline on a project with no CI setup is not automatically a failure.

</details>

- [Back to top](#back-to-top)

---

## <a id="blocked-merge-diagnosis">Blocked Merges: Push/Merge Permissions, Approvals, and Check States</a>

<details>
<summary>Click for details</summary>

A blocked MR may result from actor roles, protected targets, insufficient eligible approvals, active Request changes, unresolved discussions, or failed checks/pipelines. A reliable diagnosis uses the **specific message in the MR** and effective rules, not merely a disabled Merge button. Tier and offering determine which gates are even available.

**Order:** verify source/target and Ready state; check the actor's merge permission; examine approvals and Code Owners; inspect discussions and checks; then examine all matching protection rules. Record MR !57's exact blocker before changing access. Do not force push main or switch off rules merely to meet a deadline.

</details>

- [Back to top](#back-to-top)
