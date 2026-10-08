<a id="back-to-top"></a>

# Git Foundations: Repository, States, and Snapshots

## Menu
- [Git: Definition and Role in Distributed Version Control](#what-is-git)
- [Snapshots and the Rationale for Recording Versioned States](#why-git-snapshots)
- [Git Repository and Working Tree: Concepts and Boundaries](#repository-and-working-tree)
- [The Index (Staging Area) in Git's Three-Area Model](#index-and-three-areas)
- [Git Objects, Snapshots, and Commit Relationships](#git-objects-and-commits)
- [Tracked, Untracked, Modified, Staged, and Committed States](#file-tracking-and-states)
- [Initializing a Repository and Cloning Existing History](#initialize-and-clone)
- [Example: Tracing a File Through Git's Three Areas](#trace-file-across-areas)
- [Git Command Quick Reference and Official Documentation](#git-command-reference-and-docs)

## <a id="what-is-git">Git: Definition and Role in Distributed Version Control</a>

<details>
<summary>Click for details</summary>

Git is a **distributed version control system (DVCS)**. A normal clone contains its own commit database, allowing history inspection and new commits without a server connection. This solves the problem of manually passed source folders with no trustworthy answer to what changed, which state was recorded, or where histories diverged. Git manages **versioned source history**; it does not itself approve pull requests or deploy an application.

You only need basic files, directories, and terminal skills at entry. We will first model Git's three areas, then record snapshots and exchange commit history between repositories.

**Learning route:** this chapter introduces the repository, working tree, index, commits and file states. Next, record and inspect history, learn branches/HEAD/tags, merge and resolve conflicts, then exchange history through remotes. Once these foundations are established, compare rebase, safe undo and recovery; the final chapter combines the mechanics into one Git exercise. PR approval and release decisions belong to hosting/team policy, not to Git commands.

```bash
git --version
git help -a
```

### References

- [Git official documentation](https://git-scm.com/book/en/v2/Getting-Started-What-is-Git%3F)

</details>

- [Back to top](#back-to-top)

---

## <a id="why-git-snapshots">Snapshots and the Rationale for Recording Versioned States</a>

<details>
<summary>Click for details</summary>

Git primarily records each commit as a **snapshot of the tracked file tree**, not merely a replayable list of line edits. Unchanged content can reuse Git objects, while parent links connect snapshots into history. This is why you can reconstruct a version, compare versions, and recover a recorded state.

For example, after editing `app.txt` twice and committing twice, each commit names a complete recorded state. `git show HEAD:app.txt` reads the current commit's version, which may differ from the working file after further edits.

```bash
git log --oneline -3
git show HEAD:app.txt
```

</details>

- [Back to top](#back-to-top)

---

## <a id="repository-and-working-tree">Git Repository and Working Tree: Concepts and Boundaries</a>

<details>
<summary>Click for details</summary>

A **Git repository** stores objects, references, and configuration (normally under `.git/` for a non-bare checkout). Its **working tree** is the checked-out set of files you read and edit. Editing a file on disk does not alter a stored commit: the checked-out copy and Git's history are separate states.

Use `git rev-parse --show-toplevel` from a nested directory to locate the working tree root; `git status` compares current work with the index and HEAD. A **bare repository** lacks a normal working tree and commonly serves as a shared remote.

```bash
git rev-parse --show-toplevel
git status --short
```

</details>

- [Back to top](#back-to-top)

---

## <a id="index-and-three-areas">The Index (Staging Area) in Git's Three-Area Model</a>

<details>
<summary>Click for details</summary>

The practical model has **working tree → index (staging area) → repository**. You edit in the working tree, select a snapshot in the index, and `git commit` stores that indexed snapshot. `git add` updates the index with the path's current working contents; a later edit does not automatically restage it.

If you stage `notes.txt` and edit it again, the staged version remains the one from the earlier `add`. `git diff` shows the further unstaged edits, while `git diff --cached` shows what will enter the commit.

```bash
git add notes.txt
git diff -- notes.txt
git diff --cached -- notes.txt
```

### References

- [Git official documentation](https://git-scm.com/book/en/v2/Git-Tools-Reset-Demystified)

</details>

- [Back to top](#back-to-top)

---

## <a id="git-objects-and-commits">Git Objects, Snapshots, and Commit Relationships</a>

<details>
<summary>Click for details</summary>

Git's object model includes **blobs** for file contents, **trees** mapping filenames to blobs and subtrees, and **commits** pointing to a tree, parent commits, and author/message metadata. A usual commit has one parent; a merge often has multiple parents; the first/root commit has none. These links form the commit graph.

`git cat-file -t HEAD` identifies the object type; `git cat-file -p HEAD` reveals its tree and parent headers. An object ID depends on the stored content, so changing parent or message produces another commit ID. Inspect rather than manually editing `.git/objects`.

```bash
git cat-file -t HEAD
git cat-file -p HEAD
```

### References

- [Git official documentation](https://git-scm.com/book/en/v2/Git-Internals-Git-Objects)

</details>

- [Back to top](#back-to-top)

---

## <a id="file-tracking-and-states">Tracked, Untracked, Modified, Staged, and Committed States</a>

<details>
<summary>Click for details</summary>

An **untracked** file is not in the index; a **tracked** path already participates in Git's recorded/index state. Tracked files can be **modified** in the working tree, **staged** in the index, or represented in a **committed** snapshot. One path may be staged *and* modified again afterward.

In `git status --short`, the first status column compares index to HEAD and the second compares working tree to index; `??` indicates untracked paths. Read both columns rather than treating every changed file as the same state.

```bash
git status --short
git status
```

</details>

- [Back to top](#back-to-top)

---

## <a id="initialize-and-clone">Initializing a Repository and Cloning Existing History</a>

<details>
<summary>Click for details</summary>

`git init` creates Git repository metadata in an existing directory, but **does not create an initial commit**. `git clone <url> <directory>` downloads an existing repository's history and references and normally checks out a working tree; its default remote is typically called `origin`. Use init for new history and clone to join an existing history.

In a disposable directory, run `git init`, create a file, then stage and commit after configuring identity. Avoid initializing a real project blindly when you have not checked whether it is already under version control.

```bash
mkdir demo
cd demo
git init
git status
```

</details>

- [Back to top](#back-to-top)

---

## <a id="trace-file-across-areas">Example: Tracing a File Through Git's Three Areas</a>

<details>
<summary>Click for details</summary>

Consider a new `notes.txt`: `git status --short` initially shows `?? notes.txt`. After `git add notes.txt`, the index marks a new staged file; after `git commit`, the initial snapshot enters history. Editing the file again without staging leaves the index and working tree different.

Reproduce this in a disposable repository: inspect status at every boundary, use `git diff --cached` before the commit, and `git show HEAD:notes.txt` afterward. The key observation is that **working tree, index, and HEAD can hold different versions of one path**.

```bash
git status --short
git add notes.txt
git diff --cached -- notes.txt
git commit -m "Record notes"
git show HEAD:notes.txt
```

</details>

- [Back to top](#back-to-top)

---

## <a id="git-command-reference-and-docs">Git Command Quick Reference and Official Documentation</a>

<details>
<summary>Click for details</summary>

This is a **quick reference**, not a replacement for the three-area model and risk assessment. **Rows are alternatives, not a top-to-bottom script.** First inspect `status`, `diff`, and `log`, and determine whether commits have been shared; run state-changing commands only after understanding their scope.

| Goal | Command | Caution |
| --- | --- | --- |
| State | `git status -sb` | Read only |
| Unstaged / staged changes | `git diff` / `git diff --cached` | Different comparisons |
| Record snapshot | `git add <path>`, `git commit -m "message"` | Commit reads index |
| History / refs | `git log --oneline --graph --decorate` | Read only |
| Create/switch branch | `git switch -c topic`, `git switch main` | Check unfinished edits |
| Exchange history | `git fetch origin`, `git push -u origin topic` | Push affects collaborators |
| Unstage without discarding working edits | `git restore --staged -- <path>` | Updates index, preserves working copy |
| Discard unstaged tracked edits | `git restore -- <path>` | **Overwrites** working content; back it up first |
| Move HEAD/branch with index control | `git reset --soft <commit>` / `git reset --mixed <commit>` | For unpublished local history; soft retains index, mixed resets it |
| Reset HEAD, index, and working tree | `git reset --hard <commit>` | **Data loss risk** for uncommitted edits; obstructing untracked paths can be removed |
| Reverse a shared change without rewriting prior commits | `git revert <commit>` | Creates a **new inverse commit**; may conflict; merge reverts require parent selection |
| Find former ref positions for committed work | `git reflog` | Local and expiring; **not** recovery of never-recorded editor changes |
| Replay commits onto another base | `git rebase main` while on topic | May change commit IDs; avoid rewriting shared work |

**Choose by affected state:** `restore --staged` targets the index; `restore` targets the working tree; `reset` moves refs (and may reset index/tree); `revert` creates a new inverse commit suitable for shared history; `reflog` finds old ref positions; `rebase` replays commits, potentially changing SHAs. Consult `git help <command>` and make a safe copy before destructive changes.

```bash
git help status
git help restore
```

### References

- [Git official documentation](https://git-scm.com/docs)
- [git-reset: modes and data effects](https://git-scm.com/docs/git-reset)
- [git-revert: inverse commits](https://git-scm.com/docs/git-revert)
- [git-reflog: local ref movements](https://git-scm.com/docs/git-reflog)
- [git-rebase: replaying commits](https://git-scm.com/docs/git-rebase)

</details>

- [Back to top](#back-to-top)
