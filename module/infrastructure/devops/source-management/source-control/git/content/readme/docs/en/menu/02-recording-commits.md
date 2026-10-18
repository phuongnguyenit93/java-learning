<a id="back-to-top"></a>

# Record Changes and Inspect Commit History

## Menu
- [Inspecting Change States with git status](#inspect-status-before-staging)
- [.gitignore Rules and File Tracking Boundaries](#ignore-and-track-files)
- [Differences Between Working Tree and Staged Content](#working-tree-vs-staged-diff)
- [Staging Selected Content for the Next Commit](#stage-intentional-changes)
- [Commits: Intentional Snapshots and Change Messages](#commit-snapshot-and-message)
- [git log History, Commit IDs, and Parent Relationships](#read-log-and-parents)
- [Comparing Commit History Points and File Versions](#compare-history-points)
- [Evidence of Committed Content Versus Remaining Changes](#verify-a-recorded-change)

## <a id="inspect-status-before-staging">Inspecting Change States with git status</a>

<details>
<summary>Click for details</summary>

Before committing, use `git status` to distinguish **untracked, modified, and staged** paths and locate the current branch. `--short` uses two status columns; `-sb` adds branch/upstream context when available. Status is **read-only**, making it a safe first diagnostic.

In a test repository, edit a tracked file and add an untracked one; `git status -sb` distinguishes them. Repeat after staging; commit only after the staged content is intentional.

```bash
git status -sb
git status --short
```

</details>

- [Back to top](#back-to-top)

---

## <a id="ignore-and-track-files">.gitignore Rules and File Tracking Boundaries</a>

<details>
<summary>Click for details</summary>

Build outputs, machine-local configuration and secrets generally should not become repository history. `.gitignore` filters matching **untracked** paths; it does not untrack a file already in the index. `git rm --cached -- path` stops tracking while keeping the working file, after committing that change. Crucially, this **does not remove sensitive content from earlier commits**.

`git check-ignore -v -- file.log` reveals the rule matching an untracked file; `git status --ignored` shows ignored paths. Verify that no secret has already entered history.

```bash
git check-ignore -v -- build.log
git status --ignored --short
```

### References

- [Git official documentation](https://git-scm.com/docs/gitignore)

</details>

- [Back to top](#back-to-top)

---

## <a id="working-tree-vs-staged-diff">Differences Between Working Tree and Staged Content</a>

<details>
<summary>Click for details</summary>

By default, `git diff` compares **working tree against index**, showing unstaged work. `git diff --cached` (also `--staged`) compares **index against HEAD**, showing the next commit's content. In an unborn repository with no HEAD commit, avoid assuming all HEAD-based comparisons already work.

If you stage `src.txt` and edit it again, both diffs can be nonempty but show different changes. That observation catches the mistake of committing an older staged state while viewing newer editor contents.

```bash
git diff -- src.txt
git diff --cached -- src.txt
```

</details>

- [Back to top](#back-to-top)

---

## <a id="stage-intentional-changes">Staging Selected Content for the Next Commit</a>

<details>
<summary>Click for details</summary>

`git add path` stages the path's current state; `git add -p` interactively selects hunks of tracked work (read each prompt). `git add -A` collects many changes in scope and can accidentally include unrelated files. Staging exists to form **one coherent, reviewable, revertible intent per commit**.

Before committing, inspect `git diff --cached --stat` and the full staged diff. To unstage one path without throwing away working edits, use `git restore --staged -- path`.

```bash
git add -p -- src.txt
git diff --cached --stat
git restore --staged -- src.txt
```

### References

- [Git official documentation](https://git-scm.com/book/en/v2/Git-Basics-Recording-Changes-to-the-Repository)

</details>

- [Back to top](#back-to-top)

---

## <a id="commit-snapshot-and-message">Commits: Intentional Snapshots and Change Messages</a>

<details>
<summary>Click for details</summary>

A commit records the **index snapshot** plus author/committer metadata and a message. Configure `user.name` and `user.email` first (locally for a learning repository if desired). Prefer messages explaining intent, such as “Validate empty email,” not “updates.” Different metadata or parent links produce distinct commit IDs even with similar trees.

`git commit -m "Validate empty email"` requires appropriate staged changes; afterward `git status` may still show edits made beyond the stage. `git commit -a` is not a universal staging replacement: it does not include brand-new untracked files.

```bash
git config --local user.name "Student"
git config --local user.email "student@example.invalid"
git commit -m "Validate empty email"
```

</details>

- [Back to top](#back-to-top)

---

## <a id="read-log-and-parents">git log History, Commit IDs, and Parent Relationships</a>

<details>
<summary>Click for details</summary>

`git log --oneline --graph --decorate --all` draws commits and branch/tag labels. A **commit ID** identifies a commit object; shortened hashes work only when unambiguous. `HEAD~1` follows the first parent; a merge can have multiple parents addressable as `HEAD^1` and `HEAD^2`.

Use `git show --no-patch --pretty=raw HEAD` to inspect parent headers and metadata. The graph is not a single chronological list when branches diverge.

```bash
git log --oneline --graph --decorate --all
git show --no-patch --pretty=raw HEAD
```

</details>

- [Back to top](#back-to-top)

---

## <a id="compare-history-points">Comparing Commit History Points and File Versions</a>

<details>
<summary>Click for details</summary>

Compare two recorded snapshots with `git diff HEAD~1 HEAD -- path` when HEAD has a parent. `git show --stat <commit>` summarizes a recorded change, while `git show <commit>:path` prints the file stored at that commit without switching branches.

For `git log A..B`, the range selects commits reachable from B but not A; `git diff A B` compares two endpoint trees. A **history range** and a **snapshot diff** answer different questions.

```bash
git diff HEAD~1 HEAD -- src.txt
git log --oneline HEAD~3..HEAD
```

</details>

- [Back to top](#back-to-top)

---

## <a id="verify-a-recorded-change">Evidence of Committed Content Versus Remaining Changes</a>

<details>
<summary>Click for details</summary>

Evidence of a recorded change is a **readable commit object**, not an editor's “saved” indicator. After committing, `git log -1 --oneline` identifies it, `git show --stat HEAD` lists recorded paths, and `git status` reveals any remaining work.

Compare staged and unstaged diffs beforehand, then inspect `git show HEAD:file`. A clean status means index and working tree currently match HEAD; it **does not prove** that the commit was pushed to a remote or approved by reviewers.

```bash
git log -1 --oneline
git show --stat HEAD
git status --short
```

</details>

- [Back to top](#back-to-top)
