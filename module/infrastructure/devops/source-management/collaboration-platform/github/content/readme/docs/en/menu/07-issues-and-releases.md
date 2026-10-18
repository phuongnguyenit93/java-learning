<a id="back-to-top"></a>

# GitHub Issues and Releases

## Menu
- [GitHub Issues as Work Items and Discussion Records](#issues-as-work-items)
- [Issue Assignees, Labels, and Milestones for Work Organization](#issue-assignees-labels-milestones)
- [Connecting Issues to Pull Requests and Change Outcomes](#linking-issues-prs)
- [Git Tags Versus GitHub Releases](#git-tags-vs-github-releases)
- [Release Notes, Downloadable Assets, and Versioning Boundaries](#release-notes-and-assets)

## <a id="issues-as-work-items">GitHub Issues as Work Items and Discussion Records</a>

<details>
<summary>Click for details</summary>

A GitHub Issue records a bug, idea, or work item in repository context. It has a title, description, conversation, and status, and may link to PRs. **An Issue is not a commit**: it explains a need, while a PR records a proposed source change addressing that need.

A useful Issue #42 about negative discounts includes reproduction steps, current behavior, and expected behavior. The team can discuss assumptions before touching source; changes in requirements remain visible in the conversation. Inspect an Issue's Open/Closed status and distinguish it from proof that code was merged or shipped.

### References
- [GitHub Docs — About issues](https://docs.github.com/en/issues/tracking-your-work-with-issues/learning-about-issues/about-issues)

</details>

- [Back to top](#back-to-top)

---

## <a id="issue-assignees-labels-milestones">Issue Assignees, Labels, and Milestones for Work Organization</a>

<details>
<summary>Click for details</summary>

An **assignee** indicates who is responsible for tracking an Issue; **labels** classify topics such as bug or priority; **milestones** group work toward a goal. These fields improve organization but do not prove technical completion. A "ready" label does not establish acceptance, and milestone progress is not a deployment record.

For example, assign Issue #42 to An with a bug label and milestone 1.4. A maintainer can filter open Issues in that milestone to identify remaining work. When ownership changes, update the assignee rather than altering code history. Explain which team decision each field helps support.

</details>

- [Back to top](#back-to-top)

---

## <a id="linking-issues-prs">Connecting Issues to Pull Requests and Change Outcomes</a>

<details>
<summary>Click for details</summary>

Linking a PR to an Issue lets observers see which proposed change addresses an existing work item. GitHub supports manual linking and supported closing keywords in the PR description. **Keyword behavior depends on the default branch**: when the PR targets a different branch, keywords may be ignored for linking and auto-closing.

For a PR targeting main, "Fixes #42" can close the linked Issue when the PR merges into the repository's default branch. Do not assume the same outcome for a PR targeting release/1.3. Verify the Development links and the Issue's state after merging. Neither a link nor a closed Issue proves the fix has been released to users.

### References
- [GitHub Docs — Linking a pull request to an issue](https://docs.github.com/en/issues/tracking-your-work-with-issues/using-issues/linking-a-pull-request-to-an-issue)

</details>

- [Back to top](#back-to-top)

---

## <a id="git-tags-vs-github-releases">Git Tags Versus GitHub Releases</a>

<details>
<summary>Click for details</summary>

A **Git tag** is a named reference to an object in Git history, often marking a version. A **GitHub Release** is a publication record tied to a tag, with a title, notes, draft/prerelease status, and optional assets. A tag alone does not create a Release, and publishing a Release does not prove software was deployed.

For example, tag v1.4.0 identifies chosen source while its GitHub Release explains that Issue #42 was fixed and may provide downloads. Verify the selected tag, target commit, release notes, and publication status separately. Tag mechanics and detailed versioning strategy belong outside this GitHub collaboration module.

</details>

- [Back to top](#back-to-top)

---

## <a id="release-notes-and-assets">Release Notes, Downloadable Assets, and Versioning Boundaries</a>

<details>
<summary>Click for details</summary>

Release notes communicate user-relevant changes: features, fixes, compatibility implications, and documentation. Assets are attached files such as distributions or documentation, distinct from source archives GitHub derives from tags. Automatic notes can help, but maintainers still need to verify accuracy.

Release v1.4.0 might describe the rounding fix, link the PR, and include a package ZIP. Compare the notes to the Merged PR and release tag; the presence of a download does not prove that users are running that version. With immutable releases enabled, editing assets after publication can be restricted, so prepare a draft and verify files before publishing.

### References
- [GitHub Docs — Managing releases](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository)

</details>

- [Back to top](#back-to-top)
