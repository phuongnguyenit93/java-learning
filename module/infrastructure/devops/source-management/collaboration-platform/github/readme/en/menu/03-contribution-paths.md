<a id="back-to-top"></a>

# Contribution Paths and Pull Request Creation

## Menu
- [Contributing Through a Branch in a Shared Repository](#shared-repository-contribution)
- [Forks as Separate Repositories and Their Upstream Relationship](#fork-and-upstream)
- [Pull Request Head and Base Repositories and Branches](#pr-head-base)
- [Draft Pull Requests vs Ready-for-Review Proposals](#draft-vs-ready)
- [The Pull Request Lifecycle: Open, Update, Discuss, Merge, or Close](#pr-lifecycle)

## <a id="shared-repository-contribution">Contributing Through a Branch in a Shared Repository</a>

<details>
<summary>Click for details</summary>

In a shared repository, a developer with sufficient write access commonly creates a **separate work branch**, keeping unfinished changes away from the target branch. A PR from that branch lets reviewers inspect the proposal and lets protection requirements apply before acceptance. It works well for trusted teammates, but permission to push is not permission to skip review.

For example, a payments-team member with Write access opens a fix-rounding branch and proposes it into main. If a rule prevents pushing, inspect the rule and effective permissions instead of granting Admin casually. Verify that the PR's head and base belong to the same repository but reference different branches.

</details>

- [Back to top](#back-to-top)

---

## <a id="fork-and-upstream">Forks as Separate Repositories and Their Upstream Relationship</a>

<details>
<summary>Click for details</summary>

A **fork** is a separate repository derived from another repository. In a contribution workflow, **upstream** is the original repository to which a change is proposed. Unlike a branch inside a shared repository, the fork has its own namespace and permissions. Forks suit contributors who cannot push to upstream, subject to repository visibility and organization or enterprise forking policies.

An external contributor can fork a public project, modify a branch in their fork, and open a PR against upstream main. Owning the fork does not grant administration of upstream; the target repository's rules still matter. Read the owner names on the head and base to distinguish a fork PR from a same-repository PR.

### References
- [GitHub Docs — Forks](https://docs.github.com/en/pull-requests/reference/forks)

</details>

- [Back to top](#back-to-top)

---

## <a id="pr-head-base">Pull Request Head and Base Repositories and Branches</a>

<details>
<summary>Click for details</summary>

A PR has a **head repository/branch** containing proposed changes and a **base repository/branch** that would receive them. Reversing the direction may produce the wrong diff or target a release branch unintentionally. The head and base can belong to different repositories and have unrelated branch names.

During PR creation, inspect the comparison selector before submitting: choose the intended base repository and branch, then the head repository and comparison branch. Check the visible diff and changed-file list. An unexpectedly large diff is a signal to verify branch selection and the shared history, not automatically evidence that GitHub miscalculated the PR.

</details>

- [Back to top](#back-to-top)

---

## <a id="draft-vs-ready">Draft Pull Requests vs Ready-for-Review Proposals</a>

<details>
<summary>Click for details</summary>

A **Draft** PR exposes work in progress for discussion before it is ready for approval. Others can inspect it and comment, but a draft cannot be merged. GitHub automatically requests applicable code owners when a draft is marked **Ready for review**, not merely when the draft is first opened.

An author might open a draft to discuss an API choice, add examples, then mark it Ready. Observe the state label, requested reviewers, and merge box. If the PR remains blocked, inspect the required reviews and protection settings; marking it Ready does not create an approval by itself.

### References
- [GitHub Docs — Pull requests](https://docs.github.com/en/pull-requests/reference/pull-requests)

</details>

- [Back to top](#back-to-top)

---

## <a id="pr-lifecycle">The Pull Request Lifecycle: Open, Update, Discuss, Merge, or Close</a>

<details>
<summary>Click for details</summary>

A PR typically moves from Open through updates and discussion to **Merged** or **Closed without merging**. New commits on the head may update the proposal, and reviewers make decisions against the current change. An Open label does not prove eligibility; a Closed label alone does not establish acceptance without a Merged state.

Observe the lifecycle systematically: (1) read the purpose, (2) verify head/base, (3) inspect Files changed and reviews, (4) follow new commits, and (5) inspect the final merge box and timeline. For a PR closed without merging, record whether it was duplicate, superseded, or rejected. This is more reliable evidence than inferring success from a button color.

</details>

- [Back to top](#back-to-top)
