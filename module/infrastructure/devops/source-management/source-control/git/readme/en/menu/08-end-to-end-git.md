<a id="back-to-top"></a>

# Practice a Complete Git Change Lifecycle

## Menu
- [Example: Tracing a File from Editing to Commit](#trace-change-to-commit)
- [Example: Following a Commit Across Branches and Remote-Tracking References](#follow-branch-and-remote)
- [Comparison of Merge and Rebase Integration Mechanics](#select-merge-or-rebase-mechanics)
- [Diagnosing Divergent History and Conflicts](#diagnose-divergence-and-conflict)
- [Safe Undo Choices Based on Whether Commits Were Shared](#choose-safe-undo-in-context)
- [End-to-End Evidence from Status, Diffs, History, and References](#verify-end-to-end-evidence)
- [Boundary Between Git Mechanics, Hosted Review, and Branching Strategy](#handoff-to-team-collaboration)

## <a id="trace-change-to-commit">Example: Tracing a File from Editing to Commit</a>

<details>
<summary>Click for details</summary>

For this **disposable-repository example**, first make and enter a **new empty practice directory**, run `git init` there, and set `git config user.name "Practice User"` and `git config user.email "practice@example.invalid"` **locally in that practice repository**. Create `notes.txt` with a line of text using your editor. Do not perform the exercise in a real work project. Before staging, `git status --short` now shows it as untracked. Stage and inspect `git diff --cached`, then commit. Edit again without staging: `git diff` shows new working-tree changes while `git show HEAD:notes.txt` still reads the committed version.

The evidence chain `status → staged diff → log -1 → show HEAD:path` proves the three-area transitions. If identity is missing, configure local user.name/user.email before committing rather than skipping the step.

```bash
git status --short
git add notes.txt
git diff --cached
git commit -m "Add notes"
git show HEAD:notes.txt
```

</details>

- [Back to top](#back-to-top)

---

## <a id="follow-branch-and-remote">Example: Following a Commit Across Branches and Remote-Tracking References</a>

<details>
<summary>Click for details</summary>

After committing locally on `topic`, decorated log points `topic` at the new commit while any `origin/topic` may still reflect an earlier fetch. `git push -u origin topic` to a verified authorized server updates its branch; another clone sees it when fetching.

Compare `git branch -avv` before and after; `git ls-remote origin refs/heads/topic` asks the live server. Successful push alone provides no code-review approval.

```bash
git branch -avv
git push -u origin topic
git ls-remote origin refs/heads/topic
```

</details>

- [Back to top](#back-to-top)

---

## <a id="select-merge-or-rebase-mechanics">Comparison of Merge and Rebase Integration Mechanics</a>

<details>
<summary>Click for details</summary>

When both `main` and `topic` advance, `git merge topic` from main integrates ancestry and may create a multi-parent merge commit. `git rebase main` from topic replays topic commits with new IDs. File results may be similar, but **history graphs and publication risks** differ.

**These are ALTERNATIVE workflows, not consecutive commands**: for merge, switch to `main` then integrate `topic`; for rebase, remain on an unpublished `topic`, replay it onto `main`, then separately choose how to integrate the result. A shared branch benefits from preserving existing IDs with merge. Compare the graph and tested content after whichever route you choose.

```bash
# Option A (merge): git switch main; git merge topic
# Option B (rebase UNPUBLISHED topic): git switch topic; git rebase main
git log --oneline --graph --all
```

</details>

- [Back to top](#back-to-top)

---

## <a id="diagnose-divergence-and-conflict">Diagnosing Divergent History and Conflicts</a>

<details>
<summary>Click for details</summary>

For a rejected push or stopped merge, do not force immediately. First `git fetch origin`, then inspect `git log --left-right --graph HEAD...origin/main` for both sides before choosing integration. When conflicts occur, `git status` lists unmerged paths; edit intended behavior, stage, and continue the specific operation.

Conflict markers mean semantic choices are needed, not that Git is broken. Test and inspect diffs/status afterward to ensure no change was silently discarded.

```bash
git fetch origin
git log --left-right --graph --oneline HEAD...origin/main
git status
```

</details>

- [Back to top](#back-to-top)

---

## <a id="choose-safe-undo-in-context">Safe Undo Choices Based on Whether Commits Were Shared</a>

<details>
<summary>Click for details</summary>

If a student staged `config.txt` by mistake, `git restore --staged -- config.txt` keeps the working copy. If they want to discard backed-up working edits, `git restore -- config.txt` does so. For a faulty new unpublished commit, consider amend; for a published faulty commit, `git revert <commit>` preserves old history.

All are called “undo,” but they affect different areas. Always examine status, both diffs, and commit history before choosing an action that might erase unrecorded work.

```bash
git status -sb
git diff -- config.txt
git diff --cached -- config.txt
git log -3 --oneline
```

</details>

- [Back to top](#back-to-top)

---

## <a id="verify-end-to-end-evidence">End-to-End Evidence from Status, Diffs, History, and References</a>

<details>
<summary>Click for details</summary>

An end-to-end Git check uses independent observations: `git status -sb` for working/index state and tracking, `git diff --cached` for staged snapshot, `git log --graph --decorate` for ancestry, `git branch -avv` for refs/upstream, and `git ls-remote` for a live server ref. No single command proves every state.

After merge/rebase verify file contents and tests; after push verify server refs. **Committed ≠ pushed ≠ reviewed ≠ deployed**: these represent different systems and decisions.

```bash
git status -sb
git diff --cached
git log --oneline --graph --decorate --all
git branch -avv
```

</details>

- [Back to top](#back-to-top)

---

## <a id="handoff-to-team-collaboration">Boundary Between Git Mechanics, Hosted Review, and Branching Strategy</a>

<details>
<summary>Click for details</summary>

Git owns objects, refs, merge/rebase mechanics, and commit exchange. A **pull request / merge request** is a hosted-platform workflow for discussing, reviewing, and authorizing changes, not a native Git object. A **branching strategy** is a team decision about branch lifetime and integration frequency rather than one Git command.

For further depth, the GitHub, GitLab, Bitbucket, and Azure DevOps modules cover hosted reviews/permissions; branching-strategy covers trunk-based versus Git Flow decisions; monorepo-polyrepo covers repository topology. This is a plain-text handoff, not an expansion of Git into platform governance or CI/CD.

```bash
git help log
git help push
```

</details>

- [Back to top](#back-to-top)
