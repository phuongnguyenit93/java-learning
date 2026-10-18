<a id="back-to-top"></a>

# Integration and Repository History Policy

## Menu
- [Team Policy for Integrated History: Purpose and Rationale](#target-history-policy-purpose)
- [Merge Commits: Preserved Branch Context and Intermediate History](#merge-commit-result)
- [Squash Merges: One Logical Change in Target-Branch History](#squash-merge-result)
- [Rebase-and-Merge: Linear Target History, Separate Commits, and New Commit IDs](#rebase-and-merge-result)
- [Target-Branch Rebase-and-Merge Results Versus Work-Branch Rebase](#server-integration-vs-local-rebase)
- [Risks of Rewriting Shared Commits and Effects on Collaborators](#history-rewrite-risk)
- [History Policy Trade-Offs: Traceability, Reverts, Diagnosis, and Readability](#traceability-revert-diagnostics)

## <a id="target-history-policy-purpose">Team Policy for Integrated History: Purpose and Rationale</a>

<details>
<summary>Click for details</summary>

After integrating a branch, a team must decide **how target-branch history represents the proposal**. A merge commit preserves two parent histories, squash records one logical change, and rebase-and-merge adds individual commits linearly. This is a traceability and recovery policy, not merely a preference for a pretty graph.

A refund branch has five commits named "WIP," "fix tests," and "final." Keeping each may be valuable if commits are independently meaningful; squashing can be clearer when they are intermediate edits of one change. A hotfix's distinct audited steps may deserve preservation.

**Policy exercise:** specify merge-mode criteria by PR type, commit-message quality, and diagnostic needs; then map policy to hosting configuration.

</details>

- [Back to top](#back-to-top)

---

## <a id="merge-commit-result">Merge Commits: Preserved Branch Context and Intermediate History</a>

<details>
<summary>Click for details</summary>

A **merge commit** on the target normally has two parents linking target history with the source branch history. It preserves individual commits and the boundary where the branch was integrated, helping auditors understand which group of changes arrived together. The tradeoff is a more branching graph that requires parent relationships for diagnosis.

`feature/refunds` contains a compatible interface commit followed by a test commit. A merge commit preserves both and records when the whole proposal entered main. If the source contains many meaningless "fix typo" commits, that noise also remains.

**Evidence:** view the resulting graph and merge boundary, comparing before/after target content. Git commands create the structure; this module selects the appropriate policy.

</details>

- [Back to top](#back-to-top)

---

## <a id="squash-merge-result">Squash Merges: One Logical Change in Target-Branch History</a>

<details>
<summary>Click for details</summary>

**Squash merge** creates one new target-branch commit representing an entire proposal. It keeps mainline concise and can make a logical feature easy to identify or revert, but it **does not preserve every intermediate source commit as a separate target-history commit**. The source PR may retain discussion and branch history separately.

A refund implementation went through six review revisions; the team squashes into one "Add refunds behind flag" commit. Reverting the feature is straightforward at the logical-change level, but reconstructing intermediate reasoning requires PR evidence.

**Exercise:** compare target graphs after squash versus merge commit, count resulting commits, and choose according to audit needs rather than assuming one mode is universally cleaner.

</details>

- [Back to top](#back-to-top)

---

## <a id="rebase-and-merge-result">Rebase-and-Merge: Linear Target History, Separate Commits, and New Commit IDs</a>

<details>
<summary>Click for details</summary>

**Rebase-and-merge** replays source commits onto the target in a linear history without an aggregate merge commit. GitHub's hosted option **always produces new commit SHAs and updates committer information**. This differs subtly from a local `git rebase` that is already based on an ancestor and might not rewrite the same metadata. The source SHA must not be assumed identical to its accepted counterpart.

The refund PR has an "interface" commit and a "behavior" commit; after Rebase and merge, main gets corresponding commits with new IDs. The PR links the proposal to the result.

**Evidence:** compare linear graph, commit count, messages, and source/target SHAs. Decide whether commit-level bisectability outweighs added history complexity.

### References
- [GitHub Docs — Pull request merges](https://docs.github.com/en/pull-requests/reference/pull-request-merges)

</details>

- [Back to top](#back-to-top)

---

## <a id="server-integration-vs-local-rebase">Target-Branch Rebase-and-Merge Results Versus Work-Branch Rebase</a>

<details>
<summary>Click for details</summary>

Distinguish the **hosting platform's integration result on the target** from an **author rebasing a work branch to update its base**. Both may change SHAs, but they affect different branches and have different owners. Local rebase manages source history; GitHub's rebase-and-merge controls how accepted PR commits are recorded in target history.

Binh rebases `feature/refunds` onto main before review, potentially changing source commits and requiring reviewers to inspect the updated diff. Later, GitHub Rebase and merge creates new target SHAs according to platform behavior. Different IDs alone are not a bug.

**Compare:** actor, affected branch, review implications, and final merge evidence. Rebase flags and command mechanics belong to the Git module.

</details>

- [Back to top](#back-to-top)

---

## <a id="history-rewrite-risk">Risks of Rewriting Shared Commits and Effects on Collaborators</a>

<details>
<summary>Click for details</summary>

**History rewriting** can replace commit IDs colleagues already depend on, creating confusing PR comparisons, duplicated commits, or reconciliation work. Cleaning up a private unshared branch may be reasonable; rewriting published or shared history carries more risk. A host's accepted merge mode is not the same action as an author's force-push.

Binh rebases a shared refund branch while An still holds the old commits. An pushes on that outdated base and the PR suddenly contains an unexpected diff. The team needs explicit ownership of rewrites and communication before changing shared history.

**Policy:** avoid rewriting commits others consume; if an exception is necessary, document affected users, timing, and a recovery plan.

</details>

- [Back to top](#back-to-top)

---

## <a id="traceability-revert-diagnostics">History Policy Trade-Offs: Traceability, Reverts, Diagnosis, and Readability</a>

<details>
<summary>Click for details</summary>

The three modes optimize different properties: merge commit emphasizes branch boundaries, squash emphasizes one logical change, and rebase-and-merge emphasizes linear individual commits. **Traceability** also needs Issue/PR links, approval evidence, accepted commits, and release references; merge mode is only one piece. **Revertability** may be simpler with a squash unit but must still be validated against data and behavior.

Refunds causes a production issue: squash offers one target commit, rebase-and-merge offers several linear commits, and merge commit preserves the branch integration boundary. None guarantees safe rollback after an incompatible database migration.

**Evidence exercise:** build a matrix of merge mode → target commit identities → PR links → rollback scope → diagnostic costs.

</details>

- [Back to top](#back-to-top)
