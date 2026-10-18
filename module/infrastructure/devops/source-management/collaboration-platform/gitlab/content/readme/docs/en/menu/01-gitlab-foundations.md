<a id="back-to-top"></a>

# Foundations of Source Collaboration on GitLab

## Menu
- [GitLab as a Git Hosting and Team Collaboration Platform](#gitlab-hosting-purpose)
- [Unreviewed Changes, Lost Work Context, and Unclear Merge Decisions Without GitLab Collaboration](#unstructured-collaboration-risks)
- [Git Version History Versus GitLab Collaboration Responsibilities](#git-versus-gitlab)
- [GitLab Projects and Git Repositories: Collaboration Scope and Source History](#gitlab-project-repository)
- [Personal Namespaces, Groups, and Subgroups as Organizational Boundaries](#gitlab-namespace-basics)
- [Responsibilities of Members, Contributors, Assignees, and Reviewers](#gitlab-participants)
- [Merge Requests as Reviewable Change Proposals Before Integration](#gitlab-mr-introduction)
- [Relationships Among Projects, Source/Target Branches, Merge Requests, and Reviewers](#gitlab-workflow-concepts)

## <a id="gitlab-hosting-purpose">GitLab as a Git Hosting and Team Collaboration Platform</a>

<details>
<summary>Click for details</summary>

GitLab hosts Git repositories inside **projects** while providing work tracking, change discussion, and access governance. A team needs more than a remote copy of its source: it must know who proposed a change, who reviewed it, and when it became shared source. Passing ZIP folders in chat offers weak version and decision evidence.

**Learning route:** begin with GitLab projects, repositories, groups/subgroups, contributors, reviewers, and merge requests (MRs), distinguishing Git mechanics from GitLab collaboration. Then cover project organization and access, proposals from shared branches or forks, review/approval, protected branches and merge requirements. Finally trace an Issue through an MR to a Release without mistaking accepted source for deployed software; Git commit internals and CI/CD deployment belong to other modules.

Consider a payment team correcting rounding. One developer proposes an edit, another examines the difference, and an authorized maintainer accepts it. Open a sample project and locate **Code**, **Merge requests**, and **Issues**. Uploading code to GitLab is not proof it was merged or deployed.

### References
- [GitLab Docs — Manage projects](https://docs.gitlab.com/user/project/working_with_projects/)

</details>

- [Back to top](#back-to-top)

---

## <a id="unstructured-collaboration-risks">Unreviewed Changes, Lost Work Context, and Unclear Merge Decisions Without GitLab Collaboration</a>

<details>
<summary>Click for details</summary>

Without an agreed collaboration process, two developers may change the same calculation while assuming different business rules. The problem goes beyond line conflicts: missing descriptions, review decisions, and ownership make acceptance hard to explain. Review lowers risk but does not replace behavioral verification.

Suppose Issue #42 says negative totals must be rejected, while MR !57 silently clamps them to zero. Without an explanation, reviewers might miss the business contradiction. Inspect a sample MR for its purpose, changed files, discussion, reviewer, and final state. For each missing record, identify what a maintainer can no longer verify.

</details>

- [Back to top](#back-to-top)

---

## <a id="git-versus-gitlab">Git Version History Versus GitLab Collaboration Responsibilities</a>

<details>
<summary>Click for details</summary>

Git owns commits, branches, history, and repository synchronization independently of GitLab. GitLab organizes that Git data in projects and adds membership, merge requests, comments, Issues, and Releases. A **local merge** is a version-history operation; an **approved MR** is a collaboration decision. One does not prove the other.

A developer may merge branches locally but remain unable to update a protected main branch. Conversely, an MR approval does not itself put commits on main. This lesson introduces only enough commit/branch vocabulary to read source and target. Git command mechanics belong to the Git module, while long-term branch policy belongs to Branching Strategy.

</details>

- [Back to top](#back-to-top)

---

## <a id="gitlab-project-repository">GitLab Projects and Git Repositories: Collaboration Scope and Source History</a>

<details>
<summary>Click for details</summary>

A GitLab project governs a Git repository plus enabled collaboration features such as MRs, Issues, and membership. The Git repository stores branch versions and commit history; a **project is more than a folder of source files**. An Issue can describe work before any commit exists, and an MR can be pending while its proposed files already exist on a source branch.

If DiscountPolicy is changed on fix-rounding, Code on main may still show the old implementation while the MR presents the new diff. Record project, viewed branch, commit, and MR state together; otherwise screenshots can mistake proposed code for accepted code.

</details>

- [Back to top](#back-to-top)

---

## <a id="gitlab-namespace-basics">Personal Namespaces, Groups, and Subgroups as Organizational Boundaries</a>

<details>
<summary>Click for details</summary>

A **namespace** identifies ownership in a project path. A project in a personal namespace belongs to an account; a project in a **group** belongs to the group; **subgroups** organize child scopes and can inherit membership and policy from parents. `company/payments/gateway` identifies a gateway project under the payments subgroup, not three nested Git repositories.

These scopes help allocate responsibilities as a company grows. Browse a group and subgroup to identify projects and members, then compare the full project path. Do not assume access to a child subgroup grants administrative control over every parent group.

</details>

- [Back to top](#back-to-top)

---

## <a id="gitlab-participants">Responsibilities of Members, Contributors, Assignees, and Reviewers</a>

<details>
<summary>Click for details</summary>

A member receives a role on a project or group; a **contributor** has contributed work but may lack current push access; an **assignee** typically coordinates an Issue or MR; a **reviewer** evaluates proposed changes. Authorship or membership does not automatically permit self-approval when project settings forbid it.

In the running example, An owns Issue #42, Binh reviews MR !57, and Chi maintains the protected target branch. Distinguish who can **read**, **propose**, **review**, and **merge** rather than calling all three "authorized users." The permissions chapter will map these responsibilities to effective roles.

</details>

- [Back to top](#back-to-top)

---

## <a id="gitlab-mr-introduction">Merge Requests as Reviewable Change Proposals Before Integration</a>

<details>
<summary>Click for details</summary>

A **merge request (MR)** proposes integrating changes from a source branch/project into a target branch/project. It holds the description, diff, discussions, reviewers, approvals, and final merge state. It supports inspection **before** changes enter shared source; opening an MR does not accept them.

An author might propose MR !57 from `fix-rounding` into `main`, link Issue #42, and document input -1. A reviewer can request another boundary case. Inspect Draft/Open/Merged/Closed states and remember **Approve is not Merge**. For cross-project MRs, verify both project identities as well as branch names.

</details>

- [Back to top](#back-to-top)

---

## <a id="gitlab-workflow-concepts">Relationships Among Projects, Source/Target Branches, Merge Requests, and Reviewers</a>

<details>
<summary>Click for details</summary>

The model is a chain: groups organize project ownership and membership; the source repository/branch carries the proposal; the target project/branch receives an accepted change; an MR connects both; reviewers evaluate; protected branches and approval rules set acceptance conditions. An Issue may motivate the MR and a Release may later document the outcome.

Draw Issue #42 → fix-rounding branch → MR !57 → feedback → approval → merge → Release v1.4. For each arrow, identify evidence: linked Issue, diff, review decision, merge state, and release tag/notes. Mark the boundaries where Git mechanics or CI/CD takes over without implementing a pipeline here.

</details>

- [Back to top](#back-to-top)
