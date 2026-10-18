<a id="back-to-top"></a>

# Code Review and Pull Request Decisions

## Menu
- [Review Context: Descriptions, Diffs, Discussions, and Check Results](#pr-context-diffs-and-checks)
- [Comments, Approvals, and Requests for Changes](#review-comments-decisions)
- [Responding to Feedback, Updating Changes, and Requesting Re-Review](#change-request-rework)
- [Review Requests, Teams, and CODEOWNERS Routing](#reviewer-requests-codeowners)
- [Base-Branch CODEOWNERS Requests on Ready PRs vs Required Approval](#code-owner-approval-vs-request)

## <a id="pr-context-diffs-and-checks">Review Context: Descriptions, Diffs, Discussions, and Check Results</a>

<details>
<summary>Click for details</summary>

Good review starts with the **purpose of the PR**, the before/after behavior, scope, and user impact rather than isolated diff lines. Compare Files changed with the description, commit context, and discussions. **Checks** may report results produced by external systems; a passing check shows that a particular configured condition passed, not that every business rule is correct.

For a discount fix, trace the related Issue, inspect the changed formula, and ask for sample outputs at boundary values. A reviewer may mark files Viewed to track progress. A final submitted review carries a decision; scattered discussion comments by themselves are not an approval.

### References
- [GitHub Docs — Reviewing proposed changes](https://docs.github.com/en/pull-requests/how-tos/review-pull-requests/reviewing-proposed-changes-in-a-pull-request)

</details>

- [Back to top](#back-to-top)

---

## <a id="review-comments-decisions">Comments, Approvals, and Requests for Changes</a>

<details>
<summary>Click for details</summary>

GitHub distinguishes **Comment** (feedback without approval), **Approve** (positive review of the proposal), and **Request changes** (a request for revisions). A line comment alone is not necessarily a blocking review. When required-review policy is enabled, submitted reviews and reviewer eligibility matter to merge eligibility.

If someone comments that a variable name is unclear, the author can improve it, but the policy may not block the PR. A submitted Request changes may block merging until addressed under the repository's protection settings. Conversely, Approve does not bypass missing status checks or other required approvals. Inspect the Reviews area and merge box, not merely the total number of comments.

</details>

- [Back to top](#back-to-top)

---

## <a id="change-request-rework">Responding to Feedback, Updating Changes, and Requesting Re-Review</a>

<details>
<summary>Click for details</summary>

Authors should classify feedback as functional defects, test gaps, design concerns, or optional suggestions. Updating the head branch changes a PR's Files changed view. Explain what was revised and **request another look**; do not assume reviewers noticed all new commits. Protection settings can dismiss stale approvals after changes, depending on configuration.

Suppose a reviewer asks for negative-amount handling. The author updates the proposal, responds in the thread with a concrete result, and re-requests review. The reviewer checks the updated diff before Approve. Evidence includes the response, final review, and satisfied merge requirements—not simply a thread marked resolved for appearance.

</details>

- [Back to top](#back-to-top)

---

## <a id="reviewer-requests-codeowners">Review Requests, Teams, and CODEOWNERS Routing</a>

<details>
<summary>Click for details</summary>

Review requests can target eligible individuals or teams. **CODEOWNERS** is a repository file mapping source paths to responsible users or teams, enabling GitHub to route review requests for changed paths. Merely listing someone as an owner does not grant them access; owners need appropriate write permission and teams must meet visibility and permission requirements.

A rule such as '/pricing/ @acme/payments' associates changes under pricing with the payments team. GitHub consults CODEOWNERS from the PR's **base branch**, not unmerged edits to the head branch. If requests do not appear, check the supported file location, matching pattern, and effective access.

### References
- [GitHub Docs — About code owners](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/about-code-owners)

</details>

- [Back to top](#back-to-top)

---

## <a id="code-owner-approval-vs-request">Base-Branch CODEOWNERS Requests on Ready PRs vs Required Approval</a>

<details>
<summary>Click for details</summary>

Separate two mechanisms: **CODEOWNERS automatically routes review requests**; **branch protection or rulesets** can require an eligible code-owner approval before merging. A CODEOWNERS file without the relevant required-review setting does not itself create a merge block. One eligible listed owner may suffice for a matching path; approval is not necessarily needed from every named owner.

**Important exception:** Draft PRs do not automatically request code owners. GitHub requests them when a draft becomes Ready for review. For a pricing change, check three independent facts: Does the CODEOWNERS definition on the base match? Is the PR ready? Do active protection rules require owner approval? The merge box helps reveal unmet conditions.

</details>

- [Back to top](#back-to-top)
