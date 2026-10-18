<a id="back-to-top"></a>

# GitHub Flow as a Lightweight Collaboration Strategy

## Menu
- [GitHub Flow: Change Branches and Pull Request Feedback](#github-flow-purpose)
- [Default-Branch Work, Pull Requests, Feedback, and Merge](#default-branch-pr-cycle)
- [Early Feedback, Draft Proposals, and Review Benefits](#github-flow-review-value)
- [GitHub Flow Does Not Guarantee Trunk-Based Branch Lifetimes](#github-flow-vs-tbd)
- [GitHub Flow Strategy vs Platform PR and Ruleset Configuration](#workflow-vs-github-platform)

## <a id="github-flow-purpose">GitHub Flow: Change Branches and Pull Request Feedback</a>

<details>
<summary>Click for details</summary>

**GitHub Flow** is a lightweight collaboration process built around branches, pull requests, and integration into a default branch. It gives parallel work a visible acceptance path with discussion and review. It is a **team workflow**, not a requirement to use a particular GitHub feature or an automatic guarantee that branches remain short-lived.

An creates a branch for the rounding fix, proposes a PR to main, invites review, responds, and merges when requirements are met. Unlike classic Git Flow, this does not prescribe a permanent develop branch and elaborate release/hotfix transitions.

**Observe:** identify target branch, PR purpose, reviewers, and merge evidence. A month-long feature branch can follow a nominal PR sequence but still undermine timely feedback.

### References
- [GitHub Docs — GitHub flow](https://docs.github.com/en/get-started/using-github/github-flow)

</details>

- [Back to top](#back-to-top)

---

## <a id="default-branch-pr-cycle">Default-Branch Work, Pull Requests, Feedback, and Merge</a>

<details>
<summary>Click for details</summary>

The typical cycle branches from an appropriate base, records a focused change, opens a PR describing intent, discusses/reviews, updates the proposal, and merges to the target. Source code on a proposal branch is not yet accepted; Approve does not itself integrate commits. After merging, the completed source branch can be retired instead of lingering indefinitely.

PR #57 proposes the rounding fix from `fix-rounding` against `main`. A reviewer inspects the diff and negative-input evidence; an authorized maintainer merges according to policy. Closing the PR without merging is categorically different from shipping.

**Evidence:** record head/base, linked work item, review decisions, merge status, and proposal-to-integration time. Platform-specific PR configuration is owned by collaboration modules.

</details>

- [Back to top](#back-to-top)

---

## <a id="github-flow-review-value">Early Feedback, Draft Proposals, and Review Benefits</a>

<details>
<summary>Click for details</summary>

PRs expose business context to reviewers before code enters the shared branch. A **Draft PR** can solicit design feedback early, but a proposal not ready for acceptance should not be merged. Teams can split large features so reviewers catch invalid assumptions before implementation spans thousands of lines.

Binh shares a Draft PR for refund schema design. A reviewer spots backward-compatibility risk before full implementation. Binh revises the plan, adds evidence, and marks it ready. If the branch is still long-lived, split compatible preparation into an independent PR.

**Measure:** time to first review, feedback-to-revision time, and review size. Review quality matters more than mechanically requiring long approval chains for every trivial change.

</details>

- [Back to top](#back-to-top)

---

## <a id="github-flow-vs-tbd">GitHub Flow Does Not Guarantee Trunk-Based Branch Lifetimes</a>

<details>
<summary>Click for details</summary>

GitHub Flow defines a **PR-based propose → review → merge path**; TBD emphasizes **very frequent integration and minimal branch lifetimes**. They can coexist: GitHub Flow with one-day branches is consistent with TBD practices. But PR usage does not make a team trunk-based when feature branches remain open for weeks and merge in end-of-month batches.

An merges small fixes daily, while Binh holds refunds for three weeks. The team has a PR workflow but still incurs long-branch integration risk. Improve by using flags, independent increments, and explicit lifetime targets.

**Exercise:** classify the team using separate columns for "PR used?", "branch lifetime?", and "integration cadence?", based on observed work rather than labels.

</details>

- [Back to top](#back-to-top)

---

## <a id="workflow-vs-github-platform">GitHub Flow Strategy vs Platform PR and Ruleset Configuration</a>

<details>
<summary>Click for details</summary>

Despite its name, GitHub Flow is not usable **only on GitHub**. The branch → proposal → review → integration pattern can be implemented with GitLab merge requests or another host's pull requests. Strategy defines expectations; the platform can enforce reviewer eligibility, approvals, checks, protection, and cleanup if configured.

If a team moves from GitHub to GitLab, it can retain short change branches and pre-merge review but must reimplement access and merge policies on the new host. Different UI labels do not automatically imply a different branching strategy.

**Handoff evidence:** write the team rule "changes to main go through reviewed proposals," a role/gate matrix, and a platform-specific configuration mapping. Hosting mechanics belong to the corresponding platform modules.

</details>

- [Back to top](#back-to-top)
