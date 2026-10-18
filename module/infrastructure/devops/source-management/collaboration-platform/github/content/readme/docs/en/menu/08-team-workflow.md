<a id="back-to-top"></a>

# GitHub Team Collaboration Workflow

## Menu
- [From Issue to Contribution, Review, and Eligible Merge](#issue-to-pr-to-merge)
- [Access and Review Requirements That Block Pull Requests](#review-and-access-blockers)
- [Ruleset, Branch Protection, and Status Check Merge Blockers](#protection-and-check-blockers)
- [Evidence of Merged Pull Requests, Linked Issues, and GitHub Releases](#verify-accepted-change-and-release)
- [Handoffs to Git Mechanics, Branching Strategy, Versioning, and CI/CD](#handoffs-to-git-policy-ci)

## <a id="issue-to-pr-to-merge">From Issue to Contribution, Review, and Eligible Merge</a>

<details>
<summary>Click for details</summary>

Apply the running case: Issue #42 describes negative-amount rounding with a reproducible input. An author works on fix-rounding in a shared repository or fork, opens PR #57 against main, links the Issue, and includes evidence for the correction. Reviewers inspect the diff, request revisions or approve, and GitHub evaluates applicable rules. An authorized actor merges only when requirements are satisfied.

**Interface walkthrough:** (1) inspect the Issue and assignee; (2) verify head/base; (3) read Files changed and discussions; (4) inspect approvals, checks, and rulesets; (5) verify Merged state and the related Issue. Missing evidence must not be replaced with assumptions. This demonstrates collaboration, not Git command mechanics or CI setup.

</details>

- [Back to top](#back-to-top)

---

## <a id="review-and-access-blockers">Access and Review Requirements That Block Pull Requests</a>

<details>
<summary>Click for details</summary>

When a PR stalls, separate **repository invisibility**, **inability to push a branch**, **missing reviewer assignment**, and **insufficient eligible approvals**. Missing read access points to visibility or membership; read-only contributors may need a fork; someone listed in CODEOWNERS without sufficient access may not qualify as a code owner.

**Scenario:** A pricing PR attempts to request an invisible team or a team without appropriate access. Do not solve it by giving the author Admin. Inspect CODEOWNERS on the base, team roles, Ready state, required approval counts, and the merge box. Record the specific blocker and the responsible actor: author, reviewer, or repository administrator.

</details>

- [Back to top](#back-to-top)

---

## <a id="protection-and-check-blockers">Ruleset, Branch Protection, and Status Check Merge Blockers</a>

<details>
<summary>Click for details</summary>

An approved PR can remain unmergeable because a required check is pending, direct pushes are restricted, rulesets overlap, code-owner approval is missing, or the PR is still Draft. Identify the **actual applicable rule** and **reporting requirement** rather than calling the platform broken. One branch protection rule is not equivalent to several concurrent rulesets.

**Diagnostic order:** verify base and Draft/Ready status; inspect submitted reviews; read required checks for the current commit; inspect branch protection and active rulesets; check actor permissions and bypass scope. A green check for an earlier commit does not settle a pending check for new changes. Record the exact unmet rule or check, not merely a disabled Merge button.

</details>

- [Back to top](#back-to-top)

---

## <a id="verify-accepted-change-and-release">Evidence of Merged Pull Requests, Linked Issues, and GitHub Releases</a>

<details>
<summary>Click for details</summary>

After a PR is Merged, distinguish three facts: its change was integrated into the base branch, its linked Issue may have closed under the applicable mechanism, and a Release may be published later. All are platform evidence; none alone proves deployment to every environment.

**Evidence exercise:** inspect the PR's merge record, compare the base branch's files, and check Issue #42. Then open Releases and inspect the tag and notes for the fix. If no Release exists, report "merged, not yet published as a GitHub Release." If a Release exists but deployment was not checked, report "release published; deployment unverified." Precise statements prevent inflated completion claims.

</details>

- [Back to top](#back-to-top)

---

## <a id="handoffs-to-git-policy-ci">Handoffs to Git Mechanics, Branching Strategy, Versioning, and CI/CD</a>

<details>
<summary>Click for details</summary>

The ownership map is explicit: **Git** manages commits, branches, and history; **GitHub** hosts repositories and provides PRs, access controls, and acceptance rules; **Branching Strategy** establishes branch models and integration cadence; **CI/CD** systems validate and deliver software; **versioning policy** defines release numbering and maintenance lines. These layers cooperate but are not interchangeable.

If a team asks why production is unchanged after a Merged PR, confirm GitHub's merge evidence and hand off build/deployment questions to CI/CD owners instead of altering branch protection. If the question is why to maintain a release branch, that is branching policy rather than a GitHub-only feature. Correct ownership speeds diagnosis without expanding permissions unnecessarily.

</details>

- [Back to top](#back-to-top)
