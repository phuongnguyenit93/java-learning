<a id="back-to-top"></a>

# Merge Branches and Resolve Conflicts

## Menu
- [History Divergence During Parallel Branch Development](#why-branches-diverge)
- [Fast-Forward Merge and Branch Reference Movement](#fast-forward-merge)
- [Merge Base and Three-Way Merge Mechanics](#merge-base-and-three-way)
- [Merge Commits and Multiple Parent Relationships](#merge-commit-and-parents)
- [File Conflict Indicators and In-Progress Merge States](#recognize-merge-conflicts)
- [Resolving, Completing, or Aborting a Merge](#resolve-or-abort-merge)
- [Cherry-Picking Individual Commits Versus Merging a Branch](#cherry-pick-vs-merge)
- [Evidence of Content and Commit History After a Merge](#verify-merged-outcome)

## <a id="why-branches-diverge">History Divergence During Parallel Branch Development</a>

<details>
<summary>Click for details</summary>

Two branches may start at one base commit and acquire different descendants. This **divergence** means neither tip is necessarily an ancestor of the other. It is normal parallel development; integration becomes necessary when work must converge.

`git log --oneline --graph --all` reveals the fork point. Diverged history does not itself imply a content conflict: unrelated edits can merge automatically.

```bash
git log --oneline --graph --all --decorate
```

</details>

- [Back to top](#back-to-top)

---

## <a id="fast-forward-merge">Fast-Forward Merge and Branch Reference Movement</a>

<details>
<summary>Click for details</summary>

If the target tip is an **ancestor** of the source tip, a **fast-forward** moves the target branch ref forward without creating a merge commit. On the target branch, `git merge --ff-only topic` explicitly requires this ancestry and fails if branches have diverged.

Compare `git rev-parse HEAD` before and after and inspect the log graph: there is no new two-parent merge node. Fast-forward is not a conflict-resolution method for divergent histories.

```bash
git merge --ff-only topic
git log --oneline --graph -6
```

</details>

- [Back to top](#back-to-top)

---

## <a id="merge-base-and-three-way">Merge Base and Three-Way Merge Mechanics</a>

<details>
<summary>Click for details</summary>

For diverged branches Git identifies a suitable **merge base** (common ancestor), computes changes from that base to each tip, and combines them in a three-way merge. Incompatible edits to the same region trigger a conflict rather than Git guessing intent; independent edits often merge automatically.

`git merge-base main topic` helps locate a common ancestor; complicated graphs may have multiple candidates. Git defines the mechanics, while team branching policy belongs elsewhere.

```bash
git merge-base main topic
git diff main...topic --stat
```

### References

- [Git documentation](https://git-scm.com/docs/git-merge-base)

</details>

- [Back to top](#back-to-top)

---

## <a id="merge-commit-and-parents">Merge Commits and Multiple Parent Relationships</a>

<details>
<summary>Click for details</summary>

A non-fast-forward merge commonly creates a **merge commit** with multiple parents: first parent is the branch tip before merging, another is the integrated tip. The resulting tree is a new combined snapshot. Its message records integration intent but does not guarantee semantic correctness.

`git rev-list --parents -n 1 HEAD` prints the merge commit and parent IDs; `git show --no-patch --pretty=raw HEAD` inspects metadata. Default merge-diff presentation can differ from showing a simple single-parent commit.

```bash
git rev-list --parents -n 1 HEAD
git show --no-patch --pretty=raw HEAD
```

</details>

- [Back to top](#back-to-top)

---

## <a id="recognize-merge-conflicts">File Conflict Indicators and In-Progress Merge States</a>

<details>
<summary>Click for details</summary>

When Git cannot combine content, `git status` reports **unmerged paths**. Text files may contain `<<<<<<<`, `=======`, and `>>>>>>>` markers for competing content. Resolve the intended behavior, not merely the marker syntax. `git diff --name-only --diff-filter=U` lists unresolved paths.

`git ls-files -u` shows index stages for base/ours/theirs. Binary or rename conflicts may not show inline markers; inspect Git state rather than relying only on marker search.

```bash
git status
git diff --name-only --diff-filter=U
git ls-files -u
```

</details>

- [Back to top](#back-to-top)

---

## <a id="resolve-or-abort-merge">Resolving, Completing, or Aborting a Merge</a>

<details>
<summary>Click for details</summary>

During a conflicted merge, inspect state, edit each file to the intended combined behavior, run relevant tests, and `git add -- path` to mark it resolved. When no unmerged paths remain, complete with `git commit` (or `git merge --continue` on supported versions). `git merge --abort` attempts to return to the pre-merge state.

**Caution:** aborting can struggle to reconstruct intertwined pre-existing local changes; start merges with a clean working tree. A syntactically successful merge still requires semantic tests.

```bash
git status
git add -- src.txt
git diff --cached --check
git merge --continue
```

### References

- [Git documentation](https://git-scm.com/book/en/v2/Git-Branching-Basic-Branching-and-Merging)

</details>

- [Back to top](#back-to-top)

---

## <a id="cherry-pick-vs-merge">Cherry-Picking Individual Commits Versus Merging a Branch</a>

<details>
<summary>Click for details</summary>

`git merge topic` integrates branch ancestry. `git cherry-pick <commit>` applies the change represented by one commit and **creates a new commit on the current branch**; it does not import all topic history. This is useful for a targeted fix, but the new commit normally has a different ID.

If a cherry-pick conflicts, resolve and stage, then `git cherry-pick --continue` or `--abort`. Cherry-picking a merge commit needs an explicit mainline parent (`-m`); do not guess which parent is appropriate.

```bash
git cherry-pick <commit-id>
git cherry-pick --continue
git cherry-pick --abort
```

### References

- [Git documentation](https://git-scm.com/docs/git-cherry-pick)

</details>

- [Back to top](#back-to-top)

---

## <a id="verify-merged-outcome">Evidence of Content and Commit History After a Merge</a>

<details>
<summary>Click for details</summary>

After integration, verify both **content** and **ancestry**: status for an incomplete operation, decorated graph for parent links, `git diff <base> HEAD --stat` for changed files, and project tests for behavior. `git merge-base --is-ancestor topic HEAD` returns success when topic's tip is an ancestor of HEAD.

Cherry-pick can reproduce a patch **without** preserving source ancestry, so ancestry checks are not a valid test of whether the cherry-pick's contents were applied.

```bash
git status
git log --graph --oneline --decorate -12
git merge-base --is-ancestor topic HEAD
```

</details>

- [Back to top](#back-to-top)
