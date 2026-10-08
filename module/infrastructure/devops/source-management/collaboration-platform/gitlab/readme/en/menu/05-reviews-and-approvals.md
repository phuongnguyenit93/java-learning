<a id="back-to-top"></a>

# Merge Request Reviews, Feedback, and Approvals

## Menu
- [Review Context: Diffs, Discussions, and Inline Comments](#review-context-diff-discussions)
- [Reviewers, Assignees, and Eligible Approvers](#mr-reviewers-vs-assignees)
- [Comments, Suggestions, and Approval Decisions](#review-comments-vs-approvals)
- [Blocking a Merge with Request Changes: Premium/Ultimate Availability](#request-changes-tier-gate)
- [Approval Rules, Required Approvers, and Tier Constraints](#approval-rules-required-approvers)
- [Code Owner Scope Versus General Approval Rules](#code-owners-vs-approval-rules)
- [Feedback Resolution, Merge Request Updates, and Re-Review](#follow-up-reviews)

## <a id="review-context-diff-discussions">Review Context: Diffs, Discussions, and Inline Comments</a>

<details>
<summary>Click for details</summary>

Reviewing an MR should begin with the **business intent**, before/after behavior, diff, commits, and discussions rather than line count. Inline comments attach observations to specific edits; the overall proposal still needs a clear review outcome. A green check can demonstrate that a configured validation ran, not that the pricing rule is correct.

For MR !57, the diff clamps negative amounts although Issue #42 requires rejection. The reviewer compares descriptions, examples, and changed files to identify the mismatch. Inspect Changes and Discussions, then verify feedback against the latest source revision.

</details>

- [Back to top](#back-to-top)

---

## <a id="mr-reviewers-vs-assignees">Reviewers, Assignees, and Eligible Approvers</a>

<details>
<summary>Click for details</summary>

A **reviewer** evaluates proposed changes; an **assignee** coordinates the MR. An **eligible approver** has the membership and other conditions for an approval to count toward a rule. A commenter is not automatically an eligible approver, and assigning a reviewer does not create an approval.

An may be assignee, Binh reviewer, and Chi a required security approver if policy demands it. Inspect the Approvals widget, reviewer states, and eligible users rather than counting avatars. Eligibility and advanced reviewer-selection tools can vary by tier.

</details>

- [Back to top](#back-to-top)

---

## <a id="review-comments-vs-approvals">Comments, Suggestions, and Approval Decisions</a>

<details>
<summary>Click for details</summary>

A review can contain Comments, suggested changes, or **Approve**. A Comment clarifies a concern but does not inherently grant approval; a suggestion proposes an editable change. Approve records a review decision, while protected branch or other gates can still block merging. GitLab Free supports review and feedback without necessarily offering every enforcement feature of Premium.

Binh notes that negative amounts lack validation; An updates the MR and Binh reviews the new revision before approving. Distinguish comments, the Approve action, satisfaction of any required rule, and the final merge state.

</details>

- [Back to top](#back-to-top)

---

## <a id="request-changes-tier-gate">Blocking a Merge with Request Changes: Premium/Ultimate Availability</a>

<details>
<summary>Click for details</summary>

**Request changes** signals that a reviewer wants revisions rather than acceptance. Using it **to block merging** is a GitLab **Premium/Ultimate** capability under the current review documentation; it must not be presented as an always-enforced gate on Free. A reviewer should explain the defect, evidence, and acceptance criteria rather than using the state alone.

Binh selects Request changes for incorrect rounding; An updates the MR and asks for another review. Compare two deployments: where enforcement is available and configured, the merge widget reports a blocking review; elsewhere the team uses its agreed process and whatever gates its tier supports.

### References
- [GitLab Docs — Merge request reviews](https://docs.gitlab.com/user/project/merge_requests/reviews/)

</details>

- [Back to top](#back-to-top)

---

## <a id="approval-rules-required-approvers">Approval Rules, Required Approvers, and Tier Constraints</a>

<details>
<summary>Click for details</summary>

An **approval rule** defines how many approvals are required and which approvers qualify. GitLab's current approval-rule feature is **Premium/Ultimate** across GitLab.com, Self-Managed, and Dedicated. Rules can serve as project defaults or apply to an MR; a required count above zero creates a gate while zero can describe an optional rule. Overrides and self-approval limits depend on settings.

**Membership and visibility are not approval eligibility:** only **direct members** of a group selected as approvers count, not users who merely inherit membership from another group. Planner/Reporter approval under ordinary rules requires specifically enabled permissions and qualifying shared-group membership; **Code Owner approval** instead requires Developer, Maintainer, or Owner. Check the actual *eligible approvers* list rather than assuming that anyone who can read an MR can satisfy a rule.

If the payments project requires two eligible reviewers for main, one approval leaves the requirement unmet. Inspect the Approval widget, eligible members, required count, target branch, and applicable rule rather than assuming any commenter satisfies it.

### References
- [GitLab Docs — Approval rules](https://docs.gitlab.com/user/project/merge_requests/approvals/rules/)

</details>

- [Back to top](#back-to-top)

---

## <a id="code-owners-vs-approval-rules">Code Owner Scope Versus General Approval Rules</a>

<details>
<summary>Click for details</summary>

A **CODEOWNERS** file maps paths such as `/pricing/` to accountable people or groups; it expresses path-level ownership, distinct from broader MR/project approval rules. When protected target branches require Code Owner approval (subject to tier), changes under covered paths need an eligible owner. Merely listing a name does not grant membership or valid approval rights.

MR !57 touches `/pricing/`, so Chi in the payments ownership group may need to approve under the configured rule. Another person's Approve might not satisfy Code Owner requirements. Inspect the matching pattern, target branch, eligible owner, and rule status.

</details>

- [Back to top](#back-to-top)

---

## <a id="follow-up-reviews">Feedback Resolution, Merge Request Updates, and Re-Review</a>

<details>
<summary>Click for details</summary>

After feedback, the author should explain what changed, add evidence, and request review of the **latest revision**. New commits can reset previous approvals when project settings require it; resolving a discussion thread does not substitute for confirming behavior. Mark a thread resolved only after addressing its concern.

An adds negative-amount tests and replies to Binh's thread with example inputs and outputs. Binh inspects the updated diff and submits a new review. Compare commit revisions, unresolved discussions, the Approval widget, and merge eligibility. If blocked, identify the specific remaining condition before changing settings.

</details>

- [Back to top](#back-to-top)
