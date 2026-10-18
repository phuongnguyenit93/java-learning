<a id="back-to-top"></a>

# Branch Restrictions and Merge Checks

## Menu
- [Purpose of Protecting Important Branches](#why-protect-important-branches)
- [Branch Write Access Versus Merge Permissions](#branch-write-versus-merge-permissions)
- [Project and Repository Rules and Branch Patterns](#project-repository-and-branch-pattern-rules)
- [Merge Checks: Conditions Evaluated Before Integration](#merge-check-concepts-and-examples)
- [Advisory Checks Versus Premium Merge Blocking](#advisory-versus-premium-blocking)
- [Scenario: The Same Unresolved Merge Check on Standard and Premium Plans](#merge-check-plan-scenario)
- [Overlapping Restrictions and Merge Diagnostics](#overlapping-restrictions-and-diagnostics)

## <a id="why-protect-important-branches">Purpose of Protecting Important Branches</a>

<details>
<summary>Click for details</summary>

An important team branch should be protected from accidental updates and changes that have not received agreed scrutiny. In Bitbucket Cloud, **branch restrictions** constrain certain actions on selected branches for users or groups, while **merge checks** evaluate conditions associated with PRs. They answer different questions: *who may perform an operation* and *whether a proposal meets specified criteria*.

Orchid designates `invoice-api/main` as accepted shared source. An can work on another branch, but the team wants Binh to inspect rounding consequences before the tax change reaches `main`. Appropriate controls reduce accidental edits and make acceptance decisions visible, although no control guarantees business correctness.

**Exercise:** name one risk if everyone can write straight to `main` and another if an authorized merger ignores unresolved review tasks. Decide which control layer addresses each.

### References
- [Atlassian — Configure a project's branch restrictions](https://support.atlassian.com/bitbucket-cloud/docs/configure-a-projects-branch-restrictions/)

</details>

- [Back to top](#back-to-top)

---

## <a id="branch-write-versus-merge-permissions">Branch Write Access Versus Merge Permissions</a>

<details>
<summary>Click for details</summary>

**Branch write access** concerns direct updates to a branch; **merge access** concerns integrating a proposal into the destination. Branch restrictions can govern those operations differently. Having repository Write does not automatically entitle a contributor to update every protected branch directly or merge every PR.

In Orchid, An can contribute to the repository while a target-branch restriction limits writing or merging to designated people. Mai must inspect both repository/project access and relevant branch restrictions. Giving An broader Write access alone may fail to fix a merge denial and unnecessarily expand permissions.

**Comparison exercise:** distinguish *viewing a diff*, *writing directly to an important branch*, and *merging a PR into that branch*. For each, identify the relevant access and branch-policy evidence, rather than assuming one error message reveals the exact missing right.

</details>

- [Back to top](#back-to-top)

---

## <a id="project-repository-and-branch-pattern-rules">Project and Repository Rules and Branch Patterns</a>

<details>
<summary>Click for details</summary>

Rules can be configured at a **repository** or **project** level so related repositories follow consistent governance. A **branch-name pattern** can apply a restriction to multiple matching branches without defining each branch separately. Patterns such as `release/*` can be helpful, but their actual matching and scope must be verified.

Orchid's Billing project contains `invoice-api` and `billing-docs`. A project-level restriction for `main` can affect both repositories even if the original concern was the API. Repository-level rules can introduce further constraints. Administrators should inspect all applicable rules rather than one convenient settings page.

**Practice:** build a table with *rule, scope, branch pattern, allowed actors*. For a PR targeting `main` and another targeting `release/2026-10`, identify which rules must be examined; do not guess enforcement without checking pattern matching.

### References
- [Atlassian — Configure a project's branch restrictions](https://support.atlassian.com/bitbucket-cloud/docs/configure-a-projects-branch-restrictions/)

</details>

- [Back to top](#back-to-top)

---

## <a id="merge-check-concepts-and-examples">Merge Checks: Conditions Evaluated Before Integration</a>

<details>
<summary>Click for details</summary>

A **merge check** evaluates a condition before a PR is integrated, such as approval counts, **Changes requested** status, unresolved review tasks, or reported automated checks. It signals whether defined criteria are met; it cannot independently determine that the business requirement is correct.

Orchid wants two assurances: Binh has approved the tax change and the fractional-invoice review task is resolved. A PR with one approval but an open task can still have an unresolved check. A build/test-related check consumes a **status reported by another system**; implementing a pipeline to produce that status belongs to the CI/CD module, not this Bitbucket collaboration lesson.

**Observation:** a PR can show which checks passed or remain unresolved. **Practice:** propose two useful checks for a money-calculation change and describe which domain evidence a reviewer still needs beyond their status.

### References
- [Atlassian — Suggest or require checks before a merge](https://support.atlassian.com/bitbucket-cloud/docs/suggest-or-require-checks-before-a-merge/)

</details>

- [Back to top](#back-to-top)

---

## <a id="advisory-versus-premium-blocking">Advisory Checks Versus Premium Merge Blocking</a>

<details>
<summary>Click for details</summary>

A dangerous assumption is that configuring a merge check automatically makes merging impossible whenever it fails. Atlassian distinguishes **advisory warnings** from **enforced blocking**: on Free/Standard plans, ordinary unresolved checks may warn while an otherwise authorized person can still merge. On **Premium**, the option **Prevent a merge with unresolved merge checks** can enforce blocking for applicable outstanding conditions.

Purchasing Premium **does not by itself enable** enforcement if the prevention setting has not been configured. Branch restrictions can separately deny a user the right to merge, so a warning-only check does not mean everyone can merge. Mai must verify the plan, chosen prevention setting, target-branch restrictions, and actual permissions.

**Practice:** for An's PR lacking an approval, answer separately *what warning appears?* and *is the merge action technically prevented?*. Do not infer the second answer from the first.

### References
- [Atlassian — Suggest or require checks before a merge](https://support.atlassian.com/bitbucket-cloud/docs/suggest-or-require-checks-before-a-merge/)

</details>

- [Back to top](#back-to-top)

---

## <a id="merge-check-plan-scenario">Scenario: The Same Unresolved Merge Check on Standard and Premium Plans</a>

<details>
<summary>Click for details</summary>

Consider the same Orchid PR with an open task, “Test a negative-amount invoice.” Assume a merge check is configured to require task completion. **On Standard**, an unresolved task may produce a warning, yet someone otherwise authorized can still merge if no different restriction stops them. Human governance must therefore include a rule not to bypass meaningful warnings.

**On Premium**, if Mai has enabled blocking unresolved checks and the check applies to the target branch, the merge is prevented until the task is resolved. Merely upgrading without the prevention configuration can leave advisory behavior. Exact UI wording can change, so the current settings are the decisive evidence.

**Scenario exercise:** create Standard/Premium columns with rows for *open task, displayed warning, authorized user's merge ability*. State explicitly **Premium with enforcement configured**; otherwise the comparison is false.

</details>

- [Back to top](#back-to-top)

---

## <a id="overlapping-restrictions-and-diagnostics">Overlapping Restrictions and Merge Diagnostics</a>

<details>
<summary>Click for details</summary>

When several rules target the same branch, a PR can appear blocked even if the condition visible on one screen is satisfied. The cause may involve **project/repository permissions**, **branch restrictions**, **merge checks**, or still-open review tasks. Granting Admin or disabling all rules is not an appropriate default troubleshooting step.

Mai should first record the destination branch, acting user, and actual message. Next inspect effective access, matching project and repository rules, reviewer/task states, and plan-specific enforcement settings. This separates **lacking permission to merge**, **an enforceable unmet condition**, and **an advisory warning**.

**Practice:** a PR has the expected approvals but merging is denied. Produce at least three distinct hypotheses and evidence that could rule out each. Preserve the existing governance while diagnosing instead of loosening controls blindly.

</details>

- [Back to top](#back-to-top)
