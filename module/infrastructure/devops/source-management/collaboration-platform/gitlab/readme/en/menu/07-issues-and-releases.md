<a id="back-to-top"></a>

# GitLab Issues and Releases: Work Tracking and Release Metadata

## Menu
- [GitLab Issues as Records for Team Work and Discussion](#gitlab-issue-purpose)
- [Issue Assignees, Labels, and Milestones](#issue-assignees-labels-milestones)
- [Linking Issues to Merge Requests and Default-Branch Closing Conditions](#issues-linked-to-merge-requests)
- [Git Tags Versus GitLab Releases](#git-tag-vs-gitlab-release)
- [Release Notes, Assets, and the Versioning/Deployment Boundary](#release-notes-assets-boundary)

## <a id="gitlab-issue-purpose">GitLab Issues as Records for Team Work and Discussion</a>

<details>
<summary>Click for details</summary>

A GitLab Issue tracks a defect, request, or work item inside a project. It has a description, discussion, and status, and may be addressed by an MR; **an Issue is not a commit or deployment record**. Recording the desired behavior before coding lets reviewers compare proposed source with business requirements.

Issue #42 states that negative amounts must be rejected, including reproduction steps, input -1, and the expected result. The team discusses and assigns it before MR !57 begins. Inspect the Issue's requirement, workflow state, and linked MR separately; Closed does not prove the fix reached users.

### References
- [GitLab Docs — Issues](https://docs.gitlab.com/user/project/issues/)

</details>

- [Back to top](#back-to-top)

---

## <a id="issue-assignees-labels-milestones">Issue Assignees, Labels, and Milestones</a>

<details>
<summary>Click for details</summary>

An assignee identifies who coordinates an Issue; labels classify work such as bug, enhancement, or priority; milestones collect Issues and MRs toward a goal or date. These help planning but do not prove source was merged or released. A team-defined "ready" label is not GitLab permission to merge automatically.

Assign Issue #42 to An with bug label and v1.4 milestone. Filter outstanding work within the milestone to assess release readiness. When ownership changes, update the assignee and its context rather than rewriting commit history.

</details>

- [Back to top](#back-to-top)

---

## <a id="issues-linked-to-merge-requests">Linking Issues to Merge Requests and Default-Branch Closing Conditions</a>

<details>
<summary>Click for details</summary>

An Issue can be linked to an MR directly or referenced through **closing patterns** such as `Closes #42` in the MR description. Automatic closure generally depends on integration into the **default branch**; do not assume an MR targeting a release branch triggers identical behavior. A tracked link and an auto-closing event are distinct evidence.

MR !57 targets gateway main with `Closes #42`. After it merges, inspect Issue #42's state and timeline for the closing event. If the MR closes without merging or targets another branch, do not claim the Issue was completed without verifying actual behavior.

</details>

- [Back to top](#back-to-top)

---

## <a id="git-tag-vs-gitlab-release">Git Tags Versus GitLab Releases</a>

<details>
<summary>Click for details</summary>

A **Git tag** is a named reference into Git history, often identifying a version commit. A **GitLab Release** is a publication record associated with a tag, including title, date, notes, and optional assets. A tag does not guarantee that a Release was published, and publication does not itself prove production deployment.

A maintainer tags the accepted fix v1.4.0 and publishes a GitLab Release describing Issue #42. Verify the tag and selected commit alongside release notes. Creating tags with Git commands belongs to the Git module, while choosing version numbers is release/versioning policy.

</details>

- [Back to top](#back-to-top)

---

## <a id="release-notes-assets-boundary">Release Notes, Assets, and the Versioning/Deployment Boundary</a>

<details>
<summary>Click for details</summary>

Release notes should describe user-relevant changes: features, fixes, breaking changes, and documentation. Assets may be uploaded files or links to distributions, distinct from the source changes reviewed in an MR. GitLab records Releases, while building, signing, publishing artifacts, and deployment belong to other workflows.

Release v1.4.0 may list the negative-price fix, link MR !57, and include a downloadable package. Verify notes against the merged source and tag; a visible file does not prove every user runs v1.4.0. If no Release exists, report "merged, not published" rather than claiming delivery.

### References
- [GitLab Docs — Releases](https://docs.gitlab.com/user/project/releases/)

</details>

- [Back to top](#back-to-top)
