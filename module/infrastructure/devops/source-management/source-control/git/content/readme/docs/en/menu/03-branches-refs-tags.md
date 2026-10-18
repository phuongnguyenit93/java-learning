<a id="back-to-top"></a>

# Branches, HEAD, References, and Tags

## Menu
- [The Commit Graph and Git References](#commit-graph-and-references)
- [Local Branches and HEAD as Git References](#branches-and-head)
- [Creating, Inspecting, and Deleting Local Branches](#create-inspect-delete-branches)
- [Switching Branches with Uncommitted Changes and Safety Constraints](#switch-and-uncommitted-changes)
- [Detached HEAD: State and Risks of an Unattached Reference](#detached-head)
- [Fixed Tags Versus Branch References That Advance](#tags-vs-branches)
- [Evidence of HEAD, Branch, and Tag Positions in History](#verify-branch-tag-positions)

## <a id="commit-graph-and-references">The Commit Graph and Git References</a>

<details>
<summary>Click for details</summary>

Commits form a graph through parent links. A **Git reference (ref)** is a name pointing to an object ID, typically a commit; examples include `refs/heads/main` and `refs/tags/v1.0`. Refs make history navigable without memorizing full hashes.

`git log --graph --all --decorate` shows parent edges and reference labels; `git show-ref` lists available refs. A commit graph captures ancestry, not a team's branching policy.

```bash
git log --graph --all --oneline --decorate
git show-ref
```

</details>

- [Back to top](#back-to-top)

---

## <a id="branches-and-head">Local Branches and HEAD as Git References</a>

<details>
<summary>Click for details</summary>

A **local branch** is a ref under `refs/heads/`; it normally advances when you commit on that branch. **HEAD** usually points symbolically to the current branch name. New commits move that branch forward, leaving other branch refs unchanged.

`git symbolic-ref --short HEAD` gives the attached branch name; `git rev-parse HEAD` identifies its commit. Under detached HEAD, symbolic-ref fails because no branch is attached: that can be a valid temporary inspection state.

```bash
git symbolic-ref --short HEAD
git rev-parse HEAD
git branch -vv
```

### References

- [Git documentation](https://git-scm.com/book/en/v2/Git-Branching-Branches-in-a-Nutshell)

</details>

- [Back to top](#back-to-top)

---

## <a id="create-inspect-delete-branches">Creating, Inspecting, and Deleting Local Branches</a>

<details>
<summary>Click for details</summary>

Create a `topic` branch at HEAD with `git switch -c topic`; Git creates the ref and checks it out. Inspect local branches and upstream information with `git branch -vv`. `git switch main` only works when the repository actually has a `main` branch.

`git branch -d topic` refuses unsafe deletion of an unmerged branch under its merge checks; `-D` force-deletes its ref, potentially hiding unique commits. Objects are not necessarily deleted immediately, but reflog/garbage collection are not a substitute for preserving needed work.

```bash
git switch -c topic
git branch -vv
git switch main
git branch -d topic
```

</details>

- [Back to top](#back-to-top)

---

## <a id="switch-and-uncommitted-changes">Switching Branches with Uncommitted Changes and Safety Constraints</a>

<details>
<summary>Click for details</summary>

Switching branches updates checked-out paths to the target commit's tree. Git usually refuses when switching would overwrite uncommitted work, but do **not** assume every local modification will move cleanly. Inspect `git status` beforehand.

Commit coherent work, stash unfinished tracked work, or use a separate worktree as appropriate. `git switch -` returns to the previous checkout; examine status again, including untracked filename collisions.

```bash
git status --short
git switch -
git status --short
```

</details>

- [Back to top](#back-to-top)

---

## <a id="detached-head">Detached HEAD: State and Risks of an Unattached Reference</a>

<details>
<summary>Click for details</summary>

A **detached HEAD** points directly to a commit rather than a branch. It is useful for examining a past version with `git switch --detach <commit>`. New commits created here exist, but no normal branch advances automatically; they may be hard to find after moving away.

If such work must survive, attach a branch with `git switch -c saved-work` before leaving. `git status -sb` makes detached state visible; it is not an error when you are only inspecting history.

```bash
git switch --detach HEAD~1
git status -sb
git switch -c saved-work
```

</details>

- [Back to top](#back-to-top)

---

## <a id="tags-vs-branches">Fixed Tags Versus Branch References That Advance</a>

<details>
<summary>Click for details</summary>

A **tag** marks an object or release point and does not normally advance with new commits. A **lightweight tag** is a direct ref; an **annotated tag** is its own tag object with tagger/message information (and optionally a signature). Unlike branches, tags stay at the chosen object unless explicitly changed.

After a commit exists, try `git tag v1.0` or `git tag -a v1.0 -m "Release v1.0"`; `git show-ref --tags` verifies the destination. Rewriting a published tag creates ambiguity between clones.

```bash
git tag -a v1.0 -m "Release v1.0"
git show-ref --tags
```

</details>

- [Back to top](#back-to-top)

---

## <a id="verify-branch-tag-positions">Evidence of HEAD, Branch, and Tag Positions in History</a>

<details>
<summary>Click for details</summary>

Compare `git branch --show-current`, `git rev-parse HEAD`, `git show-ref --heads --tags`, and a decorated log to see which names point to the same commit. Committing on the current branch advances its ref, while an earlier tag remains fixed.

Two names showing the same hash means they target the same object; it does not establish server publication or review approval. Local refs remain local until history is exchanged.

```bash
git branch --show-current
git show-ref --heads --tags
git log --oneline --decorate -5
```

</details>

- [Back to top](#back-to-top)
