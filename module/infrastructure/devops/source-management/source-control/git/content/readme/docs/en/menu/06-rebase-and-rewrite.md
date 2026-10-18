<a id="back-to-top"></a>

# Rebase and the Consequences of History Rewriting

## Menu
- [Commit History Resulting from Merge Versus Rebase](#merge-versus-rebase-outcomes)
- [Rebase: Replaying Commits and Changing Commit IDs](#rebase-replay-and-commit-ids)
- [Rebasing Local Commits onto a New Base](#rebase-local-work)
- [Resolving Conflicts and Continuing or Aborting Rebase](#resolve-continue-or-abort-rebase)
- [Amending a Local Commit and Its History Consequences](#amend-a-local-commit)
- [Risks of Rewriting Already Shared Commits](#shared-history-rewrite-risk)
- [Evidence of Commit Graph and Content Changes After Rebase](#verify-history-after-rebase)

## <a id="merge-versus-rebase-outcomes">Commit History Resulting from Merge Versus Rebase</a>

<details>
<summary>Click for details</summary>

The same source changes can be integrated by **merge** or **rebase** with different ancestry graphs. A merge preserves divergent original commits and records a convergence point; rebase replays a series of commits on a newer base, creating new IDs and often a linear-looking history.

Linear appearance is not universally better: merge retains original history, while rebase can simplify a local topic's ancestry but complicate shared work. Team-level integration policy belongs to branching-strategy; here we study Git mechanics.

```bash
git log --oneline --graph --all
```

</details>

- [Back to top](#back-to-top)

---

## <a id="rebase-replay-and-commit-ids">Rebase: Replaying Commits and Changing Commit IDs</a>

<details>
<summary>Click for details</summary>

On a topic branch, `git rebase main` selects topic-specific commits and replays their changes onto the tip of `main` (subject to options/history). New commits have different parents and thus **different IDs** even if patches are similar. Old positions may appear in reflog temporarily, not as permanent backups.

Record a decorated graph before and after, and compare content/diff as well as IDs. Git may skip already-applied changes; counts are not guaranteed to stay identical.

```bash
git switch topic
git log --oneline --graph --all
git rebase main
```

### References

- [Git documentation](https://git-scm.com/book/en/v2/Git-Branching-Rebasing)

</details>

- [Back to top](#back-to-top)

---

## <a id="rebase-local-work">Rebasing Local Commits onto a New Base</a>

<details>
<summary>Click for details</summary>

Suppose an unpublished `topic` branch was based on an older `main`. When `main` advances, switch to clean `topic` and use `git rebase main` to replay topic work onto the new base. This is suited to **local unpublished commits**, not a shared branch actively used by others.

Beforehand check clean status and `git log main..topic` for the commits you expect to replay. Afterward run tests and inspect the topic-vs-main diff, rather than trusting linear history alone.

```bash
git status
git log --oneline main..topic
git rebase main
git diff --stat main...topic
```

</details>

- [Back to top](#back-to-top)

---

## <a id="resolve-continue-or-abort-rebase">Resolving Conflicts and Continuing or Aborting Rebase</a>

<details>
<summary>Click for details</summary>

A rebase may pause on a commit whose changes cannot apply cleanly. `git status` identifies the in-progress rebase; resolve files, `git add -- path`, then `git rebase --continue` to replay remaining commits. `git rebase --abort` attempts to restore the pre-rebase position.

`git rebase --skip` **drops the commit being replayed**, potentially losing work. Use it only when you have verified the patch is already present or the commit is no longer wanted. **Continue and abort are alternative paths**, not consecutive steps.

```bash
git status
git add -- src.txt
git rebase --continue
# If ABORTING instead of continuing: git rebase --abort
```

### References

- [Git documentation](https://git-scm.com/docs/git-rebase)

</details>

- [Back to top](#back-to-top)

---

## <a id="amend-a-local-commit">Amending a Local Commit and Its History Consequences</a>

<details>
<summary>Click for details</summary>

`git commit --amend` creates a replacement for HEAD using the current staged snapshot and edited metadata/message; it does **not mutate** an immutable commit. The branch advances to a new commit ID. It is suitable for polishing a **local unpublished** commit after inspecting the index.

Even a message-only amendment changes the commit ID. Do not amend commits collaborators have already fetched without coordinating the consequences of rewriting history.

```bash
git diff --cached
git commit --amend -m "Clarify validation"
```

</details>

- [Back to top](#back-to-top)

---

## <a id="shared-history-rewrite-risk">Risks of Rewriting Already Shared Commits</a>

<details>
<summary>Click for details</summary>

Rebasing or amending published commits creates new IDs while collaborators' clones still reference old IDs. Force-pushing can invalidate review context, force others to rebase, or overwrite remote work. Default to **not rewriting shared history**. Inspect upstream and outgoing commit ranges when tracking information exists.

If a private branch absolutely requires rewrite, coordinate first and understand `--force-with-lease`: it prevents some unexpected remote overwrites but does not guarantee every collaboration workflow is safe.

```bash
git branch -vv
git log --oneline '@{upstream}'..HEAD
```

</details>

- [Back to top](#back-to-top)

---

## <a id="verify-history-after-rebase">Evidence of Commit Graph and Content Changes After Rebase</a>

<details>
<summary>Click for details</summary>

Record the graph or disposable ref names before rebasing, then compare new IDs and ancestry with a decorated log. Inspect `git diff main...topic` and run tests to verify **the intended content** survived.

For a deeper comparison, `git range-diff <old-base>..<old-tip> <new-base>..<new-tip>` compares commit-series patches when you have retained both ranges. A straight-looking graph alone is not evidence of correctness.

```bash
git log --graph --oneline --all
git diff main...topic
git range-diff <old-base>..<old-tip> <new-base>..<new-tip>
```

</details>

- [Back to top](#back-to-top)
