<a id="back-to-top"></a>

# Pull Request Reviews and Feedback Tasks

## Menu
- [Purpose of Code Review and Participant Responsibilities](#review-purpose-and-participants)
- [Reviewing Diffs and File or Line Discussions](#diff-comments-and-discussions)
- [Comments, Change Requests, and Review Tasks](#feedback-versus-review-tasks)
- [Review Status Versus Merge Eligibility](#review-status-versus-merge-eligibility)
- [Updating Changes and Requesting Another Review](#revisions-and-re-review)

## <a id="review-purpose-and-participants">Purpose of Code Review and Participant Responsibilities</a>

<details>
<summary>Click for details</summary>

**Code review** turns a one-way proposal into an accountable discussion: the author explains intent, reviewers evaluate correctness and risk, and authorized participants make acceptance decisions. Its purpose is not only catching syntax mistakes; reviewers must assess whether a change solves its requirement without breaking related behavior.

In Orchid, An understands the new tax rule, Binh understands rounding, and Mai administers access. Binh can request fractional-invoice examples and flag missing cases. Mai is not automatically the strongest business reviewer merely because Mai has Admin rights. Changes spanning different concerns may need more than one reviewer.

**Exercise:** for a tax change affecting accounting and an API, identify two areas of expertise that should be represented and what each reviewer needs to inspect before approving. This chapter teaches Bitbucket review collaboration, not Git merge mechanics.

</details>

- [Back to top](#back-to-top)

---

## <a id="diff-comments-and-discussions">Reviewing Diffs and File or Line Discussions</a>

<details>
<summary>Click for details</summary>

Bitbucket's **Files changed** view lets a reviewer inspect a diff, select relevant files or lines, and leave comments in context. Threaded discussion avoids vague messages such as “this looks wrong” with no location. PR activity retains replies, and review interfaces can expose changes made since an earlier inspection.

Binh comments next to rounding logic: “With a 10% tax rate, at what stage should an invoice of 100.05 be rounded?” That points to an input, a code location, and the behavior needing evidence. An can answer with a test example or revise the code; an ordinary comment is not automatically an actionable unresolved task or a mandatory merge blocker.

**Practice:** write a line-specific review comment containing *observation, potential consequence, and reproducible example*. Decide whether it is a discussion or work that should become a tracked task.

### References
- [Atlassian — Review code in a pull request](https://support.atlassian.com/bitbucket-cloud/docs/review-code-in-a-pull-request/)

</details>

- [Back to top](#back-to-top)

---

## <a id="feedback-versus-review-tasks">Comments, Change Requests, and Review Tasks</a>

<details>
<summary>Click for details</summary>

Bitbucket offers different kinds of feedback. A **comment** expresses a question or suggestion; a **task** tracks work that should be completed; **Changes requested** expresses a reviewer's judgment that the current proposal needs revision. These are not interchangeable, and every comment does not automatically become a task.

Binh may suggest a clearer variable name but create a separate task, “Add a fractional-amount invoice example,” so An can track a specific requirement. After An supplies evidence, the task can be resolved through review. Bitbucket supports creating tasks from comments, with editing/deletion capabilities depending on user role. Closing tasks does not automatically supply approvals or satisfy other merge conditions.

**Evidence:** a PR has one discussion comment and two tasks, one still open. **Practice:** state what An must complete and distinguish task resolution from the review decision.

### References
- [Atlassian — Review code in a pull request (create and manage tasks)](https://support.atlassian.com/bitbucket-cloud/docs/review-code-in-a-pull-request/)

</details>

- [Back to top](#back-to-top)

---

## <a id="review-status-versus-merge-eligibility">Review Status Versus Merge Eligibility</a>

<details>
<summary>Click for details</summary>

**Approval** shows that a reviewer accepted what they inspected, but **merge eligibility** can depend on more: destination-branch permissions, required approval counts, unresolved tasks, requested changes, and applicable merge checks. Therefore, a green approval indicator is not an unconditional license to merge.

On Free/Standard plans, Bitbucket may show unresolved merge checks as **warnings** while still allowing an otherwise authorized user to merge. **Enforced blocking** for unresolved checks requires Premium capability and the corresponding prevention setting. Branch restrictions on write/merge access are a different control. Do not infer all policy conditions from one reviewer status.

**Practice:** An's PR has one approval but the negative-invoice task is unfinished. List the evidence and configured requirements Mai needs to examine before declaring the PR ready.

### References
- [Atlassian — Suggest or require checks before a merge](https://support.atlassian.com/bitbucket-cloud/docs/suggest-or-require-checks-before-a-merge/)

</details>

- [Back to top](#back-to-top)

---

## <a id="revisions-and-re-review">Updating Changes and Requesting Another Review</a>

<details>
<summary>Click for details</summary>

After An responds to comments, the PR may contain material different from what Binh approved earlier. **Re-review** checks that feedback was addressed without introducing fresh defects. Bitbucket helps reviewers inspect changes since an earlier review and follow the PR activity history.

An adds a test for a 100.05 invoice but also edits an unrelated discount function. Binh should inspect the new differences rather than closing tasks on trust. Some Premium checks can require another approval after source changes; that is a configured plan capability, not universal behavior for every PR.

**Practice:** describe four steps—*An updates, identifies the new diff, Binh verifies the evidence, then review/tasks are updated*. Explain why yesterday's approval may not establish that today's source is safe to merge.

</details>

- [Back to top](#back-to-top)
