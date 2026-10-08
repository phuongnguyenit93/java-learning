<a id="back-to-top"></a>

# Discuss and Review Pull Requests

## Menu
- [PR Descriptions and Changed-File Diffs](#pr-diff-and-description)
- [Assigned Reviewers and Review Context](#reviewer-assignment-and-context)
- [Inline Comments and Discussion Threads in Pull Requests](#line-comments-discussion-threads)
- [Approve Versus Approve with Suggestions Votes](#approve-versus-approve-suggestions)
- [Wait for Author Versus Reject: Review Vote Implications](#wait-for-author-versus-reject)
- [Review Votes Versus Pull Request Completion](#review-vote-vs-pr-completion)
- [Author Revisions, Feedback Resolution, and Re-Review](#author-revisions-and-re-review)
- [Evidence of Reviewer Assignments, Discussions, and Review Votes](#verify-review-state)

## <a id="pr-diff-and-description">PR Descriptions and Changed-File Diffs</a>

<details>
<summary>Click for details</summary>

Review a PR by comparing its **stated intent** (title/description) with its **Files diff**, including additions, deletions and unrelated edits. The PR comparison can change as source or target evolves, so a screenshot from yesterday is not guaranteed to reflect the current proposed code.

A PR titled “fix timeout” that also alters permission configuration warrants questions. Compare commits, changed files, test evidence and linked work; do not approve based solely on the author's prose.

</details>

- [Back to top](#back-to-top)

---

## <a id="reviewer-assignment-and-context">Assigned Reviewers and Review Context</a>

<details>
<summary>Click for details</summary>

A **reviewer** is asked to inspect correctness and risk; a **required reviewer** can be designated by target-branch policy and differs from an optional person invited by the author. Reviewers need relevant expertise and access to inspect/vote, not automatic permission to administer the branch.

For payment processing changes, choose someone who understands refund flows. Inspect the PR Reviewers list and required labels. An optional approval does not substitute for a separately required reviewer.

</details>

- [Back to top](#back-to-top)

---

## <a id="line-comments-discussion-threads">Inline Comments and Discussion Threads in Pull Requests</a>

<details>
<summary>Click for details</summary>

**Inline comments** attach to paths/lines in a diff. **Discussion threads** track feedback and responses and can remain active or become resolved. A resolved thread records the conversation state, not automatic proof that code became correct.

For a null-handling review comment, the author pushes a fix and cites tests, then reviewers inspect the new diff before resolving. A target policy requiring **comment resolution** may block completion while required threads remain active even when votes look sufficient.

</details>

- [Back to top](#back-to-top)

---

## <a id="approve-versus-approve-suggestions">Approve Versus Approve with Suggestions Votes</a>

<details>
<summary>Click for details</summary>

**Approve** expresses reviewer approval. **Approve with suggestions** also approves while suggesting improvements. Both are **review votes**, not merge operations; the target branch's configured reviewer policy determines whether enough acceptable votes exist.

If Bob approves with a naming suggestion, nothing automatically renames the method. Alice should assess the feedback, and build or comment-resolution requirements can still block completion. Read the attached comments, not just the vote label.

### References

- [Microsoft Learn — approve versus approve suggestions](https://learn.microsoft.com/en-us/azure/devops/repos/git/review-pull-requests?view=azure-devops)

</details>

- [Back to top](#back-to-top)

---

## <a id="wait-for-author-versus-reject">Wait for Author Versus Reject: Review Vote Implications</a>

<details>
<summary>Click for details</summary>

**Wait for author** asks the author to address feedback before renewed review; **Reject** states the proposal is unacceptable as reviewed. Votes by a **required reviewer** can block approval; the effects of optional negative votes depend on the selected branch policy options.

For a PR missing a security test, the reviewer chooses Wait for author with specific evidence. After the author updates code, the reviewer reexamines the changes and changes the vote as appropriate; pushing a commit does not magically convert Reject to Approve.

### References

- [Microsoft Learn — wait for author versus reject](https://learn.microsoft.com/en-us/azure/devops/repos/git/review-pull-requests?view=azure-devops)

</details>

- [Back to top](#back-to-top)

---

## <a id="review-vote-vs-pr-completion">Review Votes Versus Pull Request Completion</a>

<details>
<summary>Click for details</summary>

A review vote records **an individual's decision**; **Complete PR** performs integration into the target subject to effective permissions and policies. A fully approved PR may remain unmerged because validation failed, comments are unresolved, or the actor lacks rights; specifically authorized users may sometimes bypass policy.

On the PR Overview Bob can show Approve while Policies still reports a failed required check and completion is blocked. Do not mark a work item as merged simply because reviewers approved.

</details>

- [Back to top](#back-to-top)

---

## <a id="author-revisions-and-re-review">Author Revisions, Feedback Resolution, and Re-Review</a>

<details>
<summary>Click for details</summary>

An author can push another commit to an **Active PR's source branch**. Azure Repos updates the proposed diff/commit list and may rerun checks; whether earlier votes are reset depends on the **minimum-reviewer policy configuration**. Do not assume every push always preserves or clears approvals.

If a second fix expands from timeout logic into fee calculation, the author should explain the expanded scope and request another review. Inspect current source updates, threads and votes rather than relying on an earlier approval screenshot.

</details>

- [Back to top](#back-to-top)

---

## <a id="verify-review-state">Evidence of Reviewer Assignments, Discussions, and Review Votes</a>

<details>
<summary>Click for details</summary>

A review audit should capture **assigned reviewers**, their current votes, required reviewer indicators, unresolved discussion threads, source updates and policy results. A lone “Approved” badge cannot explain every merge decision.

Open a PR from Repos → Pull requests and compare Files, Overview/Reviewers and Policies. State “two approvals exist, but a required reviewer still selected Wait for author” rather than “almost ready.” Avoid exposing secrets or private identity data in audit screenshots.

</details>

- [Back to top](#back-to-top)
