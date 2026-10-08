<a id="back-to-top"></a>

# Bitbucket Cloud Collaboration Foundations

## Menu
- [Bitbucket Cloud: Concept and Source-Collaboration Role](#bitbucket-cloud-purpose)
- [Need for Hosted Source Collaboration in Teams](#why-hosted-source-collaboration)
- [Git and Bitbucket Cloud: Division of Responsibilities](#git-versus-bitbucket-cloud)
- [Workspace, Project, and Repository Hierarchy in Bitbucket Cloud](#workspace-project-repository-model)
- [Members, Administrators, and Shared Repositories](#shared-source-members-and-administrators)
- [Pull Requests: Concept, Authors, Reviewers, and Target Branches](#pull-request-foundation)
- [Source-Change Journey Through Bitbucket Cloud](#bitbucket-contribution-overview)

## <a id="bitbucket-cloud-purpose">Bitbucket Cloud: Concept and Source-Collaboration Role</a>

<details>
<summary>Click for details</summary>

**Bitbucket Cloud** is Atlassian's hosted service for Git repositories and team collaboration: contributors can propose changes, discuss their impact, review them, and accept selected changes into shared source. When two engineers change invoice calculations, Git preserves history; the team additionally needs a common location, scoped access, and a decision record.

This course moves from workspace organization through permissions, pull requests, review, and merge governance. Bitbucket Cloud **does not replace Git**, and uploading work does not mean anyone reviewed it. Our running example uses the *Orchid* team, the `invoice-api` repository, An's tax-rule change, and Binh's rounding review.

**Practice:** distinguish what Git can record on An's computer from what a team agreement or Bitbucket collaboration surface must provide. A hosting service coordinates work; people remain accountable for accepting it.

### References
- [Atlassian — What is a workspace?](https://support.atlassian.com/bitbucket-cloud/docs/what-is-a-workspace/)
- [Atlassian — Use pull requests for code review](https://support.atlassian.com/bitbucket-cloud/docs/use-pull-requests-for-code-review/)

</details>

- [Back to top](#back-to-top)

---

## <a id="why-hosted-source-collaboration">Need for Hosted Source Collaboration in Teams</a>

<details>
<summary>Click for details</summary>

Without a shared collaboration point, An can send `invoice-final.zip` in chat while Binh maintains `invoice-final-2.zip`. Neither file name reveals who reviewed the change, which state was accepted, or who may access the code. **Governed hosting** addresses coordination rather than merely providing disk capacity.

Bitbucket supplies repositories for shared history, pull requests for proposing differences, and comments or tasks for discussing fixes. Permissions and branch governance restrict who may affect important source states. These layers are complementary: version history without review may preserve a business defect, while review alongside excessively broad access still leaves risk.

**Observable evidence:** for an accepted correction, the team can locate the proposer, reviewer, changed lines, and merge conditions. **Practice:** explain what fails when the team removes one layer—history, review, or access control.

</details>

- [Back to top](#back-to-top)

---

## <a id="git-versus-bitbucket-cloud">Git and Bitbucket Cloud: Division of Responsibilities</a>

<details>
<summary>Click for details</summary>

**Git** is a distributed version-control system; commits, branches, and history exchange are Git mechanisms. **Bitbucket Cloud** hosts Git repositories and adds collaboration structures such as pull requests, reviewers, member permissions, and checks before merging.

An can record the `taxRate` edit in a local Git repository without signing in to Bitbucket. When An wants the team to accept it, Bitbucket offers a proposal and review context. Its *Merge* action concerns acceptance into the destination; how Git represents a merge in history belongs to the Git module, not to this platform lesson.

**Try classifying:** creating a commit, assigning a reviewer, reading a pull-request diff, and configuring repository permissions. The operations touch different layers of the same project. Use the Git module for implementation mechanics; this lesson establishes the handoff.

</details>

- [Back to top](#back-to-top)

---

## <a id="workspace-project-repository-model">Workspace, Project, and Repository Hierarchy in Bitbucket Cloud</a>

<details>
<summary>Click for details</summary>

Bitbucket Cloud organizes source through **workspace → project → repository**. A workspace is a team's or organization's collaboration space; a project groups related repositories for organization and governance; each repository hosts its own Git content and history. Placing two repositories in one project does not combine their commit histories.

Orchid has workspace `orchid-team`, project `Billing`, and repositories `invoice-api` and `billing-docs`. A Bitbucket repository URL identifies its workspace and repository slug; the project provides organizational and permission context. Project permissions can affect repositories in that project, which the next chapter explains.

**Sketch the hierarchy:** draw a workspace containing a project and two repositories. Do not place Git branches or pull requests at the same hierarchy level as projects; they concern change activity within or between repositories.

### References
- [Atlassian — What is a workspace?](https://support.atlassian.com/bitbucket-cloud/docs/what-is-a-workspace/)
- [Atlassian — Create a project](https://support.atlassian.com/bitbucket-cloud/docs/create-a-project/)

</details>

- [Back to top](#back-to-top)

---

## <a id="shared-source-members-and-administrators">Members, Administrators, and Shared Repositories</a>

<details>
<summary>Click for details</summary>

A shared repository still has distinct **contributors**, **reviewers**, and **administrators**. Authorized members may read or update content; reviewers evaluate proposals; administrators govern permissions and configuration. One person can hold several roles, but administrative access is not proof of business approval.

In Orchid, An proposes the tax fix, Binh reviews it, and Mai administers the Billing project. Mai can change certain access settings that An cannot, yet Mai need not be the tax-domain reviewer. Membership in a workspace does not by itself guarantee access to every private repository unless appropriate permissions apply.

**Exercise:** make a table for An, Binh, and Mai covering *read source*, *propose edits*, *review*, and *change permissions*. Apply least privilege. The next chapter explains the actual permission scopes.

</details>

- [Back to top](#back-to-top)

---

## <a id="pull-request-foundation">Pull Requests: Concept, Authors, Reviewers, and Target Branches</a>

<details>
<summary>Click for details</summary>

A **pull request (PR)** is Bitbucket's collaboration object for proposing changes from a **source** branch/repository into a **destination** branch/repository. The author explains intent; reviewers inspect and comment; the destination represents the shared state that may change after requirements are met.

An updates `invoice-api` on a work branch and opens a PR targeting the team's branch. Binh is a reviewer because invoice rounding needs domain knowledge. A PR includes a title, description, changed files, and discussion, but **the existence of a PR** is not evidence of acceptance.

**Practice:** complete “An proposes change ___ from ___ into ___ because ___; Binh needs to check ___.” Chapters 3–5 separately deepen change proposals, reviews, and merge governance.

</details>

- [Back to top](#back-to-top)

---

## <a id="bitbucket-contribution-overview">Source-Change Journey Through Bitbucket Cloud</a>

<details>
<summary>Click for details</summary>

The contribution journey connects the concepts introduced so far: an authorized member accesses a repository, prepares Git changes, shares a PR, receives feedback, resolves tasks, satisfies applicable merge conditions, and ultimately updates the destination. Each stage has different owners and inspectable evidence.

In Orchid, An should not call the tax fix “in production” just because the PR exists. Binh may require additional rounding evidence. If checks only warn and no blocking policy is enforced, human governance remains necessary; a warning is not approval.

**Learning map:** draw six columns—*access, proposal, review, tasks, checks, merge*—and identify where subsequent chapters deepen each. Chapter one provides the mental model rather than a platform-configuration tutorial.

</details>

- [Back to top](#back-to-top)
