<a id="back-to-top"></a>

# Proposing Changes with Pull Requests

## Menu
- [Pull Requests as Reviewable Change Proposals](#what-is-bitbucket-pull-request)
- [Source and Destination Repositories, Branches, and Diffs](#source-destination-and-diff)
- [Change Descriptions, Context, and Reviewers](#review-context-description-and-reviewers)
- [Shared Changes Versus Team-Accepted Changes](#shared-versus-accepted-changes)

## <a id="what-is-bitbucket-pull-request">Pull Requests as Reviewable Change Proposals</a>

<details>
<summary>Click for details</summary>

A **pull request (PR)** turns individual changes into a **reviewable proposal** rather than asking teammates to trust an attached file. It provides a discussion tied to source and destination branches, displays differences, invites reviewers, and retains a decision trail. A PR is not a special Git commit and does not automatically mean a change was accepted.

In Orchid, An changes the tax calculation on a work branch and opens a PR toward the shared branch of `invoice-api`. Binh must evaluate both the business rule and the rounding impact. A good PR is small enough to review but includes enough context to explain risk; bundling unrelated changes obscures the decision.

**Evidence:** the PR page shows author, rationale, source and destination, and current state. **Practice:** write a clear title and two-sentence description for the tax fix so another engineer understands what to verify before reading code.

### References
- [Atlassian — Create a pull request for review](https://support.atlassian.com/bitbucket-cloud/docs/create-a-pull-request-for-review/)

</details>

- [Back to top](#back-to-top)

---

## <a id="source-destination-and-diff">Source and Destination Repositories, Branches, and Diffs</a>

<details>
<summary>Click for details</summary>

Every PR identifies a **source repository/branch** carrying the proposed edits and a **destination repository/branch** that could receive them. Depending on contribution style, the branches may be in one repository or separate repositories. A **diff** exposes the relevant differences for inspection.

An proposes `orchid-team/invoice-api` branch `fix/tax` into destination `main`. Before creating the PR, verify source and target to avoid sending a fix to an unintended maintenance branch or repository. The difference view helps Binh detect whether the tax edit accidentally touched rounding behavior.

**Practice:** inspect a hypothetical PR changing two files when only one concerns tax. The unrelated file calls for a scope question, not automatic rejection. Git diff and merge internals are taught in the Git module.

</details>

- [Back to top](#back-to-top)

---

## <a id="review-context-description-and-reviewers">Change Descriptions, Context, and Reviewers</a>

<details>
<summary>Click for details</summary>

A PR titled only `fix bug` forces reviewers to guess its intent. A useful description identifies **the previous behavior**, **the intended behavior**, **expected impact**, and **how to verify it**. For the tax adjustment, An can attach a Jira work item if the integration is available, include before/after invoice examples, and assign Binh because Binh understands rounding behavior.

A **reviewer** is someone requested to inspect the change, not necessarily a repository administrator. Bitbucket supports **default reviewers**, but being listed does not prove a review took place or that required approvals are satisfied. Distinguish notification recipients, people giving feedback, and people authorized to merge.

**Practice:** draft a PR template covering *purpose, changes, risks, evidence, and reviewers*. Explain why a user-interface specialist alone might not be enough to review a monetary calculation.

### References
- [Atlassian — Use pull requests for code review](https://support.atlassian.com/bitbucket-cloud/docs/use-pull-requests-for-code-review/)

</details>

- [Back to top](#back-to-top)

---

## <a id="shared-versus-accepted-changes">Shared Changes Versus Team-Accepted Changes</a>

<details>
<summary>Click for details</summary>

When An publishes a work branch and creates a PR, the change is **shared** but not necessarily **accepted**. Binh may request tests for negative totals; tasks may remain open; a merge check may not be satisfied. Only after the team follows applicable access and acceptance conditions does the destination represent the newly agreed source.

Do not treat “pushed”, “PR opened”, “commented on”, and “merged” as synonyms. A review can reject a proposal. In non-Premium configurations, the interface may warn about an unresolved merge check without technically blocking a user who is otherwise allowed to merge. Teams need both platform-state and governance understanding.

**Evidence:** An's PR is visible and has reviewer activity, but destination `main` does not contain the fix. **Practice:** create columns for *recorded, shared, reviewed, accepted*, then identify which event proves entry into shared source.

</details>

- [Back to top](#back-to-top)
