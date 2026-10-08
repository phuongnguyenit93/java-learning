<a id="back-to-top"></a>

# Complete Pull Requests and Trace Work

## Menu
- [PR Completion Readiness and Unresolved Conflicts](#merge-readiness-and-conflicts)
- [Complete Pull Requests Versus Set Auto-Complete](#complete-pr-versus-auto-complete)
- [Abandon, Reactivate, and Complete: Pull Request Lifecycle](#abandon-reactivate-versus-complete)
- [PR Completion Options and Merge Traceability in Azure Repos](#pr-merge-options-and-traceability)
- [Linking Work Items to PRs: Purpose and Work Context](#work-item-and-pr-link)
- [Required Work-Item Linking Policy for PR Completion](#work-item-linking-policy)
- [Completed PR Evidence and Related Work Item States](#completion-record-and-work-item-state)
- [Scenario: Verifying Permission to Complete a PR into Its Target Branch](#target-permission-completion-scenario)
- [Diagnosing Permission, Review Vote, Policy, Check, and Conflict Blockers](#diagnose-uncompleted-pr)

## <a id="merge-readiness-and-conflicts">PR Completion Readiness and Unresolved Conflicts</a>

<details>
<summary>Click for details</summary>

A PR is **ready to complete** when source/target are valid, changes can integrate without unresolved conflicts, blocking target **policies/checks** pass and the actor has appropriate rights. Reviewer approval is not sufficient when the target moved and code cannot merge. A merge conflict is a content/integration issue, not merely a missing UI privilege.

If feature and main edit the same tax-calculation lines, Azure Repos can report a conflict. The author must reconcile source with the target using Git, retest, and wait for the new checks—not treat bypass as a way to hide a technical conflict.

</details>

- [Back to top](#back-to-top)

---

## <a id="complete-pr-versus-auto-complete">Complete Pull Requests Versus Set Auto-Complete</a>

<details>
<summary>Click for details</summary>

**Complete** attempts to integrate immediately when prerequisites pass. **Set auto-complete** records the intent to finish later when required checks/policies pass. Auto-complete **does not turn a failing build green**, resolve conflicts or grant missing rights. Examine source deletion, squash and work-item transition options before enabling it.

If reviewers approve while validation still runs, auto-complete can wait; only after successful validation and no remaining blockers does the PR complete. Record when auto-complete was configured separately from the actual Completed event.

### References

- [Microsoft Learn — complete pr versus auto complete](https://learn.microsoft.com/en-us/azure/devops/repos/git/complete-pull-requests?view=azure-devops)

</details>

- [Back to top](#back-to-top)

---

## <a id="abandon-reactivate-versus-complete">Abandon, Reactivate, and Complete: Pull Request Lifecycle</a>

<details>
<summary>Click for details</summary>

An **Active** PR remains under proposal/review; **Abandoned** stops the proposal without merging; **Reactivate** resumes an abandoned review; **Completed** records successful integration. Abandon does not itself delete source commits/branches, and an abandoned bug-fix proposal is not proof that a bug was fixed.

Alice might abandon a PR with the wrong target and create a corrected proposal. The old record explains the decision. If reactivating, reevaluate current source/target, votes and policies rather than assuming previous approvals remain appropriate.

</details>

- [Back to top](#back-to-top)

---

## <a id="pr-merge-options-and-traceability">PR Completion Options and Merge Traceability in Azure Repos</a>

<details>
<summary>Click for details</summary>

PR completion can offer **basic merge**, **squash**, **rebase variants**, source-branch deletion, a merge message and work-item options. The merge strategy affects the **target commit graph**: squash records one consolidated commit, while basic merge retains source ancestry with a merge commit. The PR record still connects the review to its outcome.

Before completing, record source/target and the selected method. Afterward inspect the Completed PR, target branch and associated commits. Commit count is not a proxy for the quality or number of approvals.

</details>

- [Back to top](#back-to-top)

---

## <a id="work-item-and-pr-link">Linking Work Items to PRs: Purpose and Work Context</a>

<details>
<summary>Click for details</summary>

A **work item** is a tracking record—bug, task or user story—in Azure Boards. Linking a PR to a work item makes the **reason for a change traceable** from source review. The link is collaboration metadata; it does not prove the issue was resolved in production.

For example, `Bug 104` tracks a rounding defect. The PR links the bug and describes regression tests, allowing reviewers to compare implementation to intent. Work-item state machines, process templates and boards are handled by Azure Boards.

</details>

- [Back to top](#back-to-top)

---

## <a id="work-item-linking-policy">Required Work-Item Linking Policy for PR Completion</a>

<details>
<summary>Click for details</summary>

Azure Repos offers a **Check for linked work items** target branch policy. When configured as blocking, a PR without the required work-item association may remain uncompletable even with passing build and votes. A legitimate work item provides traceability; do not invent fake tasks merely to satisfy a gate.

If main requires a work-item link and the PR omits `Bug 104`, Policies reports the unmet requirement. The author links the real bug and checks policy reevaluation; Git history need not change just to repair PR metadata.

### References

- [Microsoft Learn — work item linking policy](https://learn.microsoft.com/en-us/azure/devops/repos/git/branch-policies?view=azure-devops)

</details>

- [Back to top](#back-to-top)

---

## <a id="completion-record-and-work-item-state">Completed PR Evidence and Related Work Item States</a>

<details>
<summary>Click for details</summary>

After completion, the PR record shows **Completed** status, reviewers/discussions, completion time and merge choice; the target Git history reflects integration. A linked work item **may** transition when a completion option is selected and the configured process allows it, but “Completed PR ⇒ Closed bug” is not a universal rule.

For an audit, inspect both records: the PR can be merged while the bug remains Active pending deployment verification. Source integration and business-resolution state are separate events.

</details>

- [Back to top](#back-to-top)

---

## <a id="target-permission-completion-scenario">Scenario: Verifying Permission to Complete a PR into Its Target Branch</a>

<details>
<summary>Click for details</summary>

Scenario: Alice opens a PR into main, Bob approves and validation passes, yet Charlie cannot complete it. Check **Charlie's effective completion/contribution rights at the target**, Active PR state and any unresolved policies. **Contribute to pull requests** enables participation but does not replace all rights for target integration; **Bypass policies when completing pull requests** is a separate explicit override permission.

Do not grant broad Manage permissions or Bypass just to make the button clickable. Have an administrator inspect effective repository/branch rights, identify the missing operation and apply least privilege.

</details>

- [Back to top](#back-to-top)

---

## <a id="diagnose-uncompleted-pr">Diagnosing Permission, Review Vote, Policy, Check, and Conflict Blockers</a>

<details>
<summary>Click for details</summary>

Diagnose an incomplete PR in order: (1) **Draft or Abandoned** state? (2) correct **source/target** and mergeable code? (3) unmet **required votes** or active threads? (4) failed **build/status/work-item policies**? (5) does the completer have **effective rights**? (6) should reviewers, authors or administrators act next?

“Waiting for required reviewer” is a vote/policy issue, “Merge conflicts” is code integration, and “not authorized” points toward permissions. Record the concrete blocking status and its responsible owner instead of disabling all protections.

</details>

- [Back to top](#back-to-top)
