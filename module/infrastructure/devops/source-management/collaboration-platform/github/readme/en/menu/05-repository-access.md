<a id="back-to-top"></a>

# Repository Access and Organization Membership

## Menu
- [Organizations, Teams, and Outside Collaborators on GitHub](#orgs-teams-and-outside-collaborators)
- [Read, Triage, Write, Maintain, and Admin Roles](#repo-roles)
- [Permissions for Contributing, Requesting Reviews, and Maintaining Repositories](#review-and-maintenance-permissions)
- [Missing Access, Excessive Permissions, and Unclear Ownership](#least-privilege-pitfalls)

## <a id="orgs-teams-and-outside-collaborators">Organizations, Teams, and Outside Collaborators on GitHub</a>

<details>
<summary>Click for details</summary>

An organization groups members and teams to govern access to multiple repositories. Granting a team access is easier to maintain than inviting each contributor separately. An **outside collaborator** can access selected repositories without necessarily becoming an organization member. Effective access must be checked per person; a team name is not proof of every permission.

A payments team may have Write on the payment repository, auditors Read, and an outside specialist access only one permitted project. Review organization membership, team membership, repository roles, and higher-level restrictions before changing access.

### References
- [GitHub Docs — About teams](https://docs.github.com/en/organizations/organizing-members-into-teams/about-teams)

</details>

- [Back to top](#back-to-top)

---

## <a id="repo-roles">Read, Triage, Write, Maintain, and Admin Roles</a>

<details>
<summary>Click for details</summary>

Organization repository roles generally increase in scope: **Read** for viewing source and discussion; **Triage** for managing issues and PRs without code write access; **Write** for contributors; **Maintain** for broad operational management without full sensitive actions; and **Admin** for access management and sensitive operations. Features and custom roles may differ by plan; consult the official permissions matrix.

A bug triager usually needs Triage rather than Write, while a developer with Write should not automatically receive Admin. Build a task matrix: view source, assign Issues, push a branch, change permissions, delete the repository. Assign the narrowest role that supports actual work.

### References
- [GitHub Docs — Repository roles](https://docs.github.com/en/organizations/managing-user-access-to-your-organizations-repositories/managing-repository-roles/repository-roles-for-an-organization)

</details>

- [Back to top](#back-to-top)

---

## <a id="review-and-maintenance-permissions">Permissions for Contributing, Requesting Reviews, and Maintaining Repositories</a>

<details>
<summary>Click for details</summary>

Permissions govern who can view, contribute, or manage Settings; a **review request** invites someone to evaluate a PR, while a **required approval** is an acceptance condition. Neither concept should be confused with owning the repository. Organization policy and specialized permissions can restrict actions even when interface controls appear.

A developer with Write may be unable to change branch rules by design. If a PR lacks an eligible reviewer, request the appropriate team instead of promoting its author to Admin. Verify the intended action, current role, error or unavailable control, and active protection settings before recommending a change.

</details>

- [Back to top](#back-to-top)

---

## <a id="least-privilege-pitfalls">Missing Access, Excessive Permissions, and Unclear Ownership</a>

<details>
<summary>Click for details</summary>

**Least privilege** means giving enough access for current responsibilities, not granting broad access to avoid friction. Missing access appears as repository invisibility, denied pushes, or unavailable Settings. Excessive access increases the chance of accidental deletion, unauthorized policy changes, or bypass. Unclear accountability may leave PRs waiting for review despite many collaborators.

A support engineer who only triages bugs rarely needs Admin. Use a sequence: identify the required task, inspect current effective access, assign the narrowest role, record scope and expected duration, and review it when responsibilities change. If a ruleset blocks a PR, promoting its author to Admin is not the default solution.

</details>

- [Back to top](#back-to-top)
