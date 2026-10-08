<a id="back-to-top"></a>

# Merge Requests as GitLab Change Proposals

## Menu
- [Shared-Project Branch Contributions](#shared-project-contribution)
- [Forks as Separate Projects and Contributions to Upstream](#forked-project-contribution)
- [Source and Target Projects and Branches in Merge Requests](#source-target-project-branches)
- [Change Descriptions, Assignees, and Draft/Ready States](#mr-description-assignee-draft)
- [Merge Request Lifecycle: Open, Update, Review, Merge, or Close](#mr-lifecycle-states)

## <a id="shared-project-contribution">Shared-Project Branch Contributions</a>

<details>
<summary>Click for details</summary>

A contributor with appropriate access to a shared project typically works on a **separate source branch** rather than updating main directly. An MR records the purpose, file differences, and review requests before integration. Branch creation and pushes depend on roles and protection rules; Developer does not mean unrestricted access to every branch.

An fixes Issue #42 on `fix-rounding` and opens MR !57 against `main`. Check that source and target are branches of the same project. If direct pushes to main are blocked, proposing an MR is usually the intended workflow; seeking Maintainer solely to skip review is not.

</details>

- [Back to top](#back-to-top)

---

## <a id="forked-project-contribution">Forks as Separate Projects and Contributions to Upstream</a>

<details>
<summary>Click for details</summary>

A GitLab fork is a **separate project** derived from an upstream project, not merely another branch. A contributor may propose work from a fork without upstream push access, subject to repository visibility and forking policies. Groups and privacy constraints can restrict where forks can be created.

A contractor forks a public gateway project, works on a branch in their fork, then opens an MR into `company/payments/gateway:main`. The reviewer verifies both project namespaces. Owning the fork does not grant merge rights or permission to edit the upstream protected branch.

</details>

- [Back to top](#back-to-top)

---

## <a id="source-target-project-branches">Source and Target Projects and Branches in Merge Requests</a>

<details>
<summary>Click for details</summary>

Every MR compares a **source project and branch** with a **target project and branch**. Selecting the wrong target can send a fix toward another release line or yield a huge unintended diff. Equal branch names do not prove identical history or project ownership.

In the MR creation interface, verify the target project/branch, then the source and the changed-file list. If a fork MR unexpectedly contains hundreds of files, recheck its chosen target and shared history. Record the source/target pair with the review context so the change's route remains auditable.

</details>

- [Back to top](#back-to-top)

---

## <a id="mr-description-assignee-draft">Change Descriptions, Assignees, and Draft/Ready States</a>

<details>
<summary>Click for details</summary>

An MR description should explain the problem, before/after behavior, verification, and risk. The **assignee** coordinates the work; a **reviewer** evaluates it, and the roles need not belong to one person. A **Draft** MR supports early discussion before merge readiness; marking it Ready signals review readiness, not an approval.

An opens Draft MR !57 while negative-input validation is missing and links Issue #42. After adding evidence, An marks it Ready and asks Binh to review. Check the Draft label, reviewer, and merge widget; changing the label does not itself satisfy approval or protected-branch rules.

</details>

- [Back to top](#back-to-top)

---

## <a id="mr-lifecycle-states">Merge Request Lifecycle: Open, Update, Review, Merge, or Close</a>

<details>
<summary>Click for details</summary>

An MR moves from an open proposal through new source commits, discussions, Ready status, review, and a final **Merged** or **Closed without merge** outcome. New commits change its diff and may require further review; approvals can be reset according to project settings. Open does not prove merge eligibility.

**Evidence walkthrough:** (1) read purpose and source/target; (2) inspect Changes and discussions; (3) check reviewers and approvals; (4) inspect merge status and checks; (5) verify the target and recorded integration when Merged. If closed without merging, record whether it was superseded, withdrawn, or rejected.

</details>

- [Back to top](#back-to-top)
