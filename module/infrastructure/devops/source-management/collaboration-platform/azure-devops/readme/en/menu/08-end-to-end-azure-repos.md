<a id="back-to-top"></a>

# Apply an End-to-End Azure Repos Workflow

## Menu
- [Mapping Organizations, Projects, Repositories, and Owners](#map-org-project-repo)
- [Selecting Shared Branches or Forks Based on Contribution Rights](#choose-contribution-path)
- [Transition from Draft to Review-Ready Pull Requests](#open-draft-and-ready-pr)
- [Tracking Review Votes and Branch Policy Requirements](#review-and-apply-policies)
- [Completing PRs with Work Item Traceability Evidence](#complete-with-work-item-trace)
- [Blocked PR Completion: Diagnostic Scenario](#investigate-blocked-workflow)
- [End-to-End Evidence: Permissions, Reviewers, Checks, and PR Status](#verify-end-to-end-evidence)
- [Handoffs to Git Mechanics, Branching Strategy, Boards, and Pipelines](#handoff-to-neighboring-modules)

## <a id="map-org-project-repo">Mapping Organizations, Projects, Repositories, and Owners</a>

<details>
<summary>Click for details</summary>

Use a simulated `RetailCo → Checkout → checkout-api` workflow. **Project Administrators** manage project/service settings, a least-privilege repository owner group manages repository controls, Contributors propose changes and Reviewers inspect them. Custom team names do not automatically confer product permissions.

Before the change, record organization/project/repository, target main, relevant Read/Contribute rights and branch policies. This ownership map prevents proposing code in the wrong repo or broadening permissions unnecessarily.

</details>

- [Back to top](#back-to-top)

---

## <a id="choose-contribution-path">Selecting Shared Branches or Forks Based on Contribution Rights</a>

<details>
<summary>Click for details</summary>

Suppose Alice belongs to checkout-api Contributors. If she has permission to create and push a feature branch, use the shared repository. For an external contributor needing isolation, consider a **fork** under organizational policy. The fork's own permissions/policies must be verified separately from the **upstream target branch**.

Record the source repository and source branch, rights supporting that choice and target `checkout-api/main`; branch name alone is not proof of the right contribution path.

</details>

- [Back to top](#back-to-top)

---

## <a id="open-draft-and-ready-pr">Transition from Draft to Review-Ready Pull Requests</a>

<details>
<summary>Click for details</summary>

Alice starts a timeout change and opens a **Draft PR** from `feature/timeout` to `main`, explaining the defect, planned tests and related `Bug 104`. Draft allows early feedback without claiming merge readiness. After implementation and tests, Alice updates the source and marks the PR **Ready for review**.

Track PR state, source/target, current diff and update time. Before moving to ready, inspect the changed-file list for unintended secrets or generated artifacts.

</details>

- [Back to top](#back-to-top)

---

## <a id="review-and-apply-policies">Tracking Review Votes and Branch Policy Requirements</a>

<details>
<summary>Click for details</summary>

Bob reads the files and test evidence and **Approves**. Required reviewer Carla chooses **Wait for author** because a failure case is unresolved. Main requires two acceptable approvals, resolved comments and build validation. The PR remains blocked despite one positive vote; after Alice pushes a correction, verify vote-reset rules and request re-review.

Evidence includes reviewer roles/votes, active threads, counted approvals and check outcomes. Do not report “review passed” while a required reviewer is still waiting.

</details>

- [Back to top](#back-to-top)

---

## <a id="complete-with-work-item-trace">Completing PRs with Work Item Traceability Evidence</a>

<details>
<summary>Click for details</summary>

After Carla approves, threads are resolved and required build passes, an authorized actor **Completes** the PR (or auto-complete triggers) after verifying target and merge type. The PR becomes **Completed**, and the target contains the integrated changes. Linked `Bug 104` remains traceable and may stay Active pending deployment verification.

Capture PR ID/status, target, reviewers/checks, merge method, linked work item and resulting target commit. Completed code review is not evidence of a successful production deployment.

</details>

- [Back to top](#back-to-top)

---

## <a id="investigate-blocked-workflow">Blocked PR Completion: Diagnostic Scenario</a>

<details>
<summary>Click for details</summary>

Investigation variant: Bob approves but **Complete** remains unavailable. “Required reviewer waiting” goes to Carla; “Build validation failed” requires code/check investigation; “Merge conflict” requires reconciling source via Git; “permission denied” requires effective-rights diagnosis for the completing actor.

When the source is a fork, adjusting fork policies does not unlock upstream main. Document **cause → observed UI evidence → responsible owner → safe corrective action** instead of reflexively granting bypass.

</details>

- [Back to top](#back-to-top)

---

## <a id="verify-end-to-end-evidence">End-to-End Evidence: Permissions, Reviewers, Checks, and PR Status</a>

<details>
<summary>Click for details</summary>

End-to-end evidence should cover **identity** (organization/project/repository), **access** (access level/effective rights), **proposal** (source/target/Draft/Active), **review** (required reviewers/votes/threads), **policies** (blocking checks), **completion** (Completed, merge method, target commit) and **traceability** (work item). Correlate these at the same current PR revision.

A production bug remaining open despite a completed PR is a separate deployment/verification issue. Local Git history cannot replace evidence of review; an Approved UI badge cannot prove the target integrated the change.

</details>

- [Back to top](#back-to-top)

---

## <a id="handoff-to-neighboring-modules">Handoffs to Git Mechanics, Branching Strategy, Boards, and Pipelines</a>

<details>
<summary>Click for details</summary>

From this point, **Git** owns commit graphs, merges, fetch/push and conflict resolution; **branching-strategy** owns trunk-based versus Git Flow and branch lifetime; **monorepo-polyrepo** owns repository topology decisions. **Azure Boards** owns work-item processes, while **Azure Pipelines** owns CI/CD and producing validation checks.

Azure Repos connects those systems at the PR, permission, policy and evidence layer. When a PR cannot complete, diagnose the responsible surface—rights, votes, policy, code conflict or check—without turning this Git collaboration course into a pipeline or Boards implementation guide.

</details>

- [Back to top](#back-to-top)
