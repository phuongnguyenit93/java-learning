<a id="back-to-top"></a>

# Remotes, Tracking Branches, and History Synchronization

## Menu
- [Remote Repositories: Sharing and Exchanging Git History](#remote-repository-purpose)
- [Configuring and Inspecting Remotes Such as origin](#configure-and-inspect-remotes)
- [Local Branches, Server Branches, and Remote-Tracking References](#local-remote-and-tracking-refs)
- [Upstream Relationships for Local Branches](#upstream-relationship)
- [git fetch Versus git pull in History Synchronization](#fetch-vs-pull)
- [git push, Remote Updates, and Rejection Conditions](#push-and-rejected-updates)
- [Reconciling Diverged Histories Before Sharing Changes](#reconcile-divergent-history)
- [Reference Evidence Before and After History Synchronization](#verify-local-and-remote-history)

## <a id="remote-repository-purpose">Remote Repositories: Sharing and Exchanging Git History</a>

<details>
<summary>Click for details</summary>

A remote is another Git repository, often hosted on a server, named locally for exchanging commits and references. Git still creates commits **locally**; a remote provides a sharing endpoint, not code review by itself. A repository can have multiple remotes; `origin` is a convention.

`git remote -v` lists fetch/push URLs, which may use HTTPS or SSH. Do not embed secrets in URLs. Hosted permission models belong to the collaboration platform, not Git's ref mechanics.

```bash
git remote -v
git remote show origin
```

</details>

- [Back to top](#back-to-top)

---

## <a id="configure-and-inspect-remotes">Configuring and Inspecting Remotes Such as origin</a>

<details>
<summary>Click for details</summary>

Use `git remote add origin <url>` when that name does not yet exist; `git remote set-url origin <url>` changes an existing name's address. `git remote -v` reads local configuration, not server availability. `git ls-remote --heads origin` queries branch refs if connectivity and permissions allow.

Verify the destination before adding or changing URLs. With forks or mirrors, double-check fetch and push destinations to avoid publishing history to the wrong place.

```bash
git remote -v
git ls-remote --heads origin
```

</details>

- [Back to top](#back-to-top)

---

## <a id="local-remote-and-tracking-refs">Local Branches, Server Branches, and Remote-Tracking References</a>

<details>
<summary>Click for details</summary>

Your local `main` (`refs/heads/main`) differs from the **remote-tracking ref** `origin/main` (`refs/remotes/origin/main`). It stores the most recently fetched view of the remote branch, not a live connection. `git fetch origin` updates these refs according to the fetch refspec.

`git branch -avv` displays local and remote-tracking names. The actual server branch is a separate ref; `origin/main` is your locally cached observation.

```bash
git branch -avv
git show-ref --heads
git show-ref
```

### References

- [Git documentation](https://git-scm.com/book/en/v2/Git-Branching-Remote-Branches)

</details>

- [Back to top](#back-to-top)

---

## <a id="upstream-relationship">Upstream Relationships for Local Branches</a>

<details>
<summary>Click for details</summary>

An upstream relationship tells Git which branch to compare for ahead/behind and supplies defaults for certain pull/push operations (subject to configuration). It is commonly set by `git push -u origin topic` at first publication or `git branch --set-upstream-to=origin/main main` when the tracking ref already exists.

`git branch -vv` shows configured tracking and ahead/behind counts relative to last-fetched information. Counts may be stale; upstream alone does **not** publish your commits.

```bash
git branch -vv
git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'
```

</details>

- [Back to top](#back-to-top)

---

## <a id="fetch-vs-pull">git fetch Versus git pull in History Synchronization</a>

<details>
<summary>Click for details</summary>

`git fetch origin` downloads objects and updates remote-tracking refs **without integrating into the checked-out branch**. `git pull` runs fetch and then integrates according to arguments/configuration: it may merge, rebase, or require a fast-forward. Modern Git can ask you to choose how divergent histories should be reconciled.

For explicit control, fetch first, inspect `git log --left-right --oneline HEAD...origin/main`, then choose merge or rebase intentionally. Pull is not just a download operation.

```bash
git fetch origin
git log --left-right --oneline HEAD...origin/main
```

### References

- [Git documentation](https://git-scm.com/docs/git-pull)

</details>

- [Back to top](#back-to-top)

---

## <a id="push-and-rejected-updates">git push, Remote Updates, and Rejection Conditions</a>

<details>
<summary>Click for details</summary>

`git push origin topic` asks the server to update its `topic` ref after uploading required objects. The server may reject it for permissions/rules or a **non-fast-forward** update where remote history contains commits not included in the proposed new tip.

For divergence, fetch, inspect both sets of commits, integrate deliberately, then push again. `--force` can replace collaborators' history; `--force-with-lease` adds a conditional safety check but still rewrites a remote ref and should not be the default fix.

```bash
git fetch origin
git log --oneline --left-right origin/topic...topic
git push origin topic
```

### References

- [Git documentation](https://git-scm.com/docs/git-push)

</details>

- [Back to top](#back-to-top)

---

## <a id="reconcile-divergent-history">Reconciling Diverged Histories Before Sharing Changes</a>

<details>
<summary>Click for details</summary>

When local `main` and `origin/main` each have unique commits, history has diverged. Inspect `git merge-base main origin/main` and a left/right graph of the symmetric difference. Merging can preserve both sides' commit IDs; rebasing replays and changes local commit IDs, best considered before those commits are shared.

Start with a clean working tree and the intended target branch, resolve conflicts if any, then inspect status, graph, and tests. A rebase is not permission to force-push blindly.

```bash
git merge-base main origin/main
git log --left-right --graph --oneline main...origin/main
```

</details>

- [Back to top](#back-to-top)

---

## <a id="verify-local-and-remote-history">Reference Evidence Before and After History Synchronization</a>

<details>
<summary>Click for details</summary>

Before fetching, `origin/main` reflects an **earlier observation**. After fetch, `git rev-parse origin/main` may change if the server advanced; local `main` does not advance automatically. After explicit integration, compare the local branch and commit graph.

`git status -sb` reports tracking relative to cached upstream refs. To ask the live server, use `git ls-remote origin refs/heads/main` when connected and compare IDs. Local and server observations are different evidence.

```bash
git rev-parse main
git rev-parse origin/main
git ls-remote origin refs/heads/main
```

</details>

- [Back to top](#back-to-top)
