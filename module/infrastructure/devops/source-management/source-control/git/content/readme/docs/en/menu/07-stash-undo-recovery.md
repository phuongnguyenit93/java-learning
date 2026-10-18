<a id="back-to-top"></a>

# Stash, Undo, and Recover Safely

## Menu
- [Uncommitted Work and Shared-History Classification Before Undoing](#classify-uncommitted-and-shared-work)
- [Stash: Shelving and Restoring Uncommitted Work](#stash-work-in-progress)
- [Default Stash Scope and Options for Untracked or Ignored Files](#stash-tracked-and-untracked)
- [git restore and Its Effects on the Working Tree and Index](#restore-worktree-and-index)
- [git reset Modes and Data Loss Risks](#reset-modes-and-risk)
- [git revert for Reversing Shared Changes Without Rewriting Commits](#revert-shared-commit)
- [Local Reflogs, Expiration, and Recovery Limitations](#reflog-and-expiration)
- [Criteria for Safe Git Recovery Based on Current State](#safe-recovery-decision)

## <a id="classify-uncommitted-and-shared-work">Uncommitted Work and Shared-History Classification Before Undoing</a>

<details>
<summary>Click for details</summary>

Before undoing, classify where a change exists: **working tree**, **index**, **unpublished local commit**, or **shared commit**. The mechanisms differ: restore changes selected file states, reset moves refs and possibly index/worktree, while revert records an inverse change for published history. Choosing the wrong tool can destroy edits never committed.

Use status, unstaged/staged diffs, and outgoing commit ranges (when upstream exists) as evidence. If publication is uncertain, fetch and compare refs; **back up important uncommitted work before destructive actions**.

```bash
git status -sb
git diff
git diff --cached
git branch -vv
```

</details>

- [Back to top](#back-to-top)

---

## <a id="stash-work-in-progress">Stash: Shelving and Restoring Uncommitted Work</a>

<details>
<summary>Click for details</summary>

`git stash push -m "WIP: parsing"` shelves unfinished tracked changes so you can switch context. `git stash list` lists entries; `git stash show -p 'stash@{0}'` shows stored changes. `git stash apply` applies without deleting the entry; `git stash pop` attempts to apply and drop it after success.

Applying a stash can conflict with the current worktree. Stash is temporary convenience, not durable backup; inspect its patch before dropping an entry.

```bash
git stash push -m "WIP: parsing"
git stash list
git stash show -p 'stash@{0}'
git stash apply 'stash@{0}'
```

</details>

- [Back to top](#back-to-top)

---

## <a id="stash-tracked-and-untracked">Default Stash Scope and Options for Untracked or Ignored Files</a>

<details>
<summary>Click for details</summary>

By default `git stash push` saves tracked working and indexed changes; it does **not include untracked paths**. `-u` includes non-ignored untracked paths, while `-a` also includes ignored paths. Including caches or ignored secrets can create unexpected stored data.

In a test repo, create an untracked file, stash normally, and run `git status --short`: it generally remains. Choose `git stash push -u` only after reviewing which extra files will be saved.

```bash
git status --short
git stash push -u -m "include untracked"
git stash list
```

### References

- [Git documentation](https://git-scm.com/book/en/v2/Git-Tools-Stashing-and-Cleaning)

</details>

- [Back to top](#back-to-top)

---

## <a id="restore-worktree-and-index">git restore and Its Effects on the Working Tree and Index</a>

<details>
<summary>Click for details</summary>

`git restore -- path` normally restores the tracked path's **working tree from the index**, discarding unstaged edits while leaving the index unchanged. `git restore --staged -- path` resets that path's index entry to HEAD and **keeps working edits**, making it useful for mistaken staging. `git restore --source=<commit> -- path` can explicitly read an older stored version into the working tree.

Overwriting an uncommitted path can lose data. Inspect `git diff -- path` first; restore is not an all-purpose undo.

```bash
git diff -- src.txt
git restore --staged -- src.txt
git restore -- src.txt
```

### References

- [Git documentation](https://git-scm.com/docs/git-restore)

</details>

- [Back to top](#back-to-top)

---

## <a id="reset-modes-and-risk">git reset Modes and Data Loss Risks</a>

<details>
<summary>Click for details</summary>

`git reset --soft <commit>` moves HEAD/branch while retaining index and working tree; `--mixed` (default) moves the ref and resets index but keeps working files; `--hard` resets **ref, index, and tracked working files** to the target, potentially deleting uncommitted edits. Reset rewrites local branch position; check whether commits were shared.

For a new unpublished commit, `git reset --soft HEAD~1` keeps changes staged; `--mixed` would unstage them. **These modes are alternatives: never run all three sequentially in the same working repository.** Avoid `--hard` with unbacked work. Untracked files may remain, except paths obstructing reset can be removed.

```bash
git reset --soft HEAD~1
# Alternative to --soft: git reset --mixed HEAD~1
# DANGEROUS, do not run without backup: git reset --hard <commit-id>
```

### References

- [Git documentation](https://git-scm.com/book/en/v2/Git-Tools-Reset-Demystified)

</details>

- [Back to top](#back-to-top)

---

## <a id="revert-shared-commit">git revert for Reversing Shared Changes Without Rewriting Commits</a>

<details>
<summary>Click for details</summary>

If a shared commit must be undone, `git revert <commit>` normally creates a **new commit** that applies the inverse change, preserving older IDs and avoiding forced history rewriting. In a clean repository inspect the target commit first; then test and examine log/diff to confirm the effect.

Revert can conflict when later commits depend on the original. Resolve and `git revert --continue`, or cancel with `git revert --abort`. Reverting a merge requires a selected mainline parent and an understanding of later merge behavior.

```bash
git show --stat <commit-id>
git revert <commit-id>
git log -3 --oneline
```

### References

- [Git documentation](https://git-scm.com/docs/git-revert)

</details>

- [Back to top](#back-to-top)

---

## <a id="reflog-and-expiration">Local Reflogs, Expiration, and Recovery Limitations</a>

<details>
<summary>Click for details</summary>

A **reflog** records local ref movements (such as switches, resets, and rebases) and can reveal older commit IDs. After finding an ID with `git reflog`, anchor it with `git branch rescue <hash>`. Local reflogs are **not synchronized** by push/fetch, and entries expire; unreachable objects may eventually be garbage-collected.

`git reflog show HEAD` lists HEAD movements. Create a ref while the target commit still exists. Reflog is neither a permanent backup nor a way to recover never-recorded editor changes reliably.

```bash
git reflog -10
git reflog show HEAD
git branch rescue <old-commit-id>
```

### References

- [Git documentation](https://git-scm.com/docs/git-reflog)

</details>

- [Back to top](#back-to-top)

---

## <a id="safe-recovery-decision">Criteria for Safe Git Recovery Based on Current State</a>

<details>
<summary>Click for details</summary>

A recovery decision should begin with **read-only** status/diff/log, classification of staged/unstaged/committed state, and publication status. Unstage mistakes with `git restore --staged`; discard working edits only after backup; amend or soft-reset a new unpublished commit; generally revert shared commits.

After a mistaken reset/rebase, inspect reflog before old objects expire. Do not reflexively use `git clean -fd` or `reset --hard`: unrecorded edits and untracked files may be unrecoverable from Git.

```bash
git status -sb
git diff
git diff --cached
git reflog -8
```

</details>

- [Back to top](#back-to-top)
