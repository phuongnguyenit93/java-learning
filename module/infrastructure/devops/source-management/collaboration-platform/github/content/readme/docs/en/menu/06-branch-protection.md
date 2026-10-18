<a id="back-to-top"></a>

# Branch Protection and Merge Requirements

## Menu
- [Branch Protection Rules and Their Matching Branches](#branch-protection-rules)
- [Overlapping Rulesets Versus a Matching Branch Protection Rule](#rulesets-and-overlap)
- [Required Pull Requests, Review Counts, and Code Owner Approvals](#required-pr-review-and-approval)
- [Required Status Checks and Pull Request Merge Eligibility](#checks-and-merge-eligibility)
- [Push Restrictions, Rule Bypass, and Troubleshooting Blocked Merges](#push-bypass-and-troubleshooting)

## <a id="branch-protection-rules">Branch Protection Rules and Their Matching Branches</a>

<details>
<summary>Click for details</summary>

Branch protection rules apply requirements to named branches or matching patterns such as main and release/*. They can require PRs, reviews, status checks, or restrict pushes and deletion. **Only one branch protection rule applies at a time to a branch**; multiple matching branch-protection patterns do not automatically accumulate all their settings.

If main blocks direct pushes but release/* uses different requirements, administrators must identify the effective matching rule and its precedence. With appropriate rights, inspect Settings → Branches and compare the configured requirements with the PR's merge box. Do not assume Write bypasses the rule, or that every option is available on every plan.

### References
- [GitHub Docs — About protected branches](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches)

</details>

- [Back to top](#back-to-top)

---

## <a id="rulesets-and-overlap">Overlapping Rulesets Versus a Matching Branch Protection Rule</a>

<details>
<summary>Click for details</summary>

A **ruleset** groups rules with its own target scope and enforcement status, potentially at repository or organization level when supported. Unlike a branch protection rule, **multiple rulesets can apply simultaneously** to one branch; their applicable requirements must all be considered. An evaluate-only ruleset may report effects without enforcement, depending on feature availability and configuration.

For example, an organization ruleset requires reviews while a repository ruleset requires checks; a PR into main may need both. Inspect applicable rules and enforcement state rather than relying only on a Branch protection screen. Bypass actors have scoped exceptions, not blanket permission to ignore every active rule.

### References
- [GitHub Docs — About rulesets](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/about-rulesets)

</details>

- [Back to top](#back-to-top)

---

## <a id="required-pr-review-and-approval">Required Pull Requests, Review Counts, and Code Owner Approvals</a>

<details>
<summary>Click for details</summary>

Repositories can require changes to important branches to use PRs, receive enough approvals, and obtain code-owner approval for matching files. These are **configured requirements**, not an intrinsic property of every PR. A green Approve from one reviewer may be insufficient if the reviewer is ineligible or other rules remain unmet.

Suppose pricing has one colleague's approval, but the effective rule requires two approvals and a code owner. The PR stays blocked. Inspect the merge box, base-branch CODEOWNERS, Ready state, and submitted reviews. Depending on settings, new commits may invalidate earlier approvals. Do not loosen policy merely to meet a deadline without assessing the change.

</details>

- [Back to top](#back-to-top)

---

## <a id="checks-and-merge-eligibility">Required Status Checks and Pull Request Merge Eligibility</a>

<details>
<summary>Click for details</summary>

A status check is a result reported to GitHub by a validation source, such as a test runner or analysis service. **Required status checks** block merging only when configured as required for the target branch. Distinguish pending, failed, and missing results from the required source; one green check does not prove that every required check passed.

A PR may pass unit-tests while security-review remains pending, leaving it ineligible. Diagnose the exact required check name, the commit being evaluated, the reporting source, and the applicable rule. This lesson teaches how to interpret GitHub check evidence, not how to build GitHub Actions workflows or CI pipelines.

</details>

- [Back to top](#back-to-top)

---

## <a id="push-bypass-and-troubleshooting">Push Restrictions, Rule Bypass, and Troubleshooting Blocked Merges</a>

<details>
<summary>Click for details</summary>

When push or merge fails, separate **actor permissions**, **branch protection**, **rulesets**, **reviews**, and **checks** before changing configuration. Push restrictions govern direct branch updates; merge requirements govern PR acceptance. Admin or bypass permissions may provide exceptions according to configuration, but active rulesets can impose additional rules with their own bypass actors.

A contributor with Write may be denied a direct push to main; the intended path could be a separate branch and PR. If an approved PR still cannot merge, examine active rulesets and pending required checks. Record the exact error, target branch, effective rule, and actor. Avoid force pushing or granting Admin to hide an intentionally enforced policy.

</details>

- [Back to top](#back-to-top)
