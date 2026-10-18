<a id="back-to-top"></a>

# Branch Policies, Merge Requirements, and Bypass Permissions

## Menu
- [Purpose of Protecting Important PR Target Branches](#why-protect-target-branches)
- [Branch Permissions Versus Branch Policies](#branch-permissions-vs-policies)
- [Required Merge Policies Versus Optional Checks](#required-vs-optional-policies)
- [Minimum Reviewer Counts and Self-Approval Rules](#minimum-reviewers-and-self-approval)
- [Required Reviewers and Comment Resolution Requirements](#required-reviewers-and-comment-resolution)
- [Build Validation, Status Checks, and the CI/CD Boundary](#build-validation-status-checks)
- [Permitted Merge Types During PR Completion](#permitted-merge-types)
- [Bypass Policies When Completing PRs Versus When Pushing](#bypass-on-pr-versus-push)
- [Diagnosing Unmet Policies, Checks, and Bypass Permissions](#diagnose-policy-failures)

## <a id="why-protect-target-branches">Purpose of Protecting Important PR Target Branches</a>

<details>
<summary>Click for details</summary>

Branches such as `main` are often shared baselines; an unsafe direct update can affect everyone. **Branch policies** protect a **target branch** through PR review and checks before integration. These are enforced governance controls, not merely a suggested PR description template.

For example, a payment team's main may require two reviewers and a passing build validation. Enforcing relevant policies requires updates through PR unless separately authorized bypass rights apply.

### References

- [Microsoft Learn — why protect target branches](https://learn.microsoft.com/en-us/azure/devops/repos/git/branch-policies?view=azure-devops)

</details>

- [Back to top](#back-to-top)

---

## <a id="branch-permissions-vs-policies">Branch Permissions Versus Branch Policies</a>

<details>
<summary>Click for details</summary>

A **branch permission** answers *who may* Read, Contribute, Force push, Edit policies or bypass. A **branch policy** answers *what must be satisfied* before a PR targeting the branch may complete. Repository Contribute does not imply policy-free merging; Edit policies is not the same as a bypass grant.

If Alice cannot participate in the PR, inspect Repository/Branch Security. If the PR exists but reports “minimum reviewers failed,” inspect policies on its **target branch**. Separating these surfaces prevents unnecessary admin grants.

### References

- [Microsoft Learn — branch permissions vs policies](https://learn.microsoft.com/en-us/azure/devops/repos/git/branch-permissions?view=azure-devops)

</details>

- [Back to top](#back-to-top)

---

## <a id="required-vs-optional-policies">Required Merge Policies Versus Optional Checks</a>

<details>
<summary>Click for details</summary>

An unmet **blocking/required policy** prevents normal PR completion; an **optional/advisory check** reports information without necessarily acting as the same gate. “Enabled” and “Blocking/required” are not always equivalent; inspect each configured policy and product-version behavior.

A security scan might be informational while build validation is required. Passing the optional warning depends on governance judgment, but the required build must pass. Do not disable a blocking check merely to make a PR appear green.

</details>

- [Back to top](#back-to-top)

---

## <a id="minimum-reviewers-and-self-approval">Minimum Reviewer Counts and Self-Approval Rules</a>

<details>
<summary>Click for details</summary>

The **minimum reviewers** policy counts acceptable approvals according to target-branch configuration, including options for author votes, negative votes and resetting prior votes when source changes. Two names on a reviewer list do not necessarily mean two counted approvals.

If two non-author approvals are required, Alice's self-approval plus Bob's approval may still be insufficient when author votes do not count. Inspect policy evaluation and reviewer votes, not merely the number of assigned people.

### References

- [Microsoft Learn — minimum reviewers and self approval](https://learn.microsoft.com/en-us/azure/devops/repos/git/branch-policies?view=azure-devops)

</details>

- [Back to top](#back-to-top)

---

## <a id="required-reviewers-and-comment-resolution">Required Reviewers and Comment Resolution Requirements</a>

<details>
<summary>Click for details</summary>

**Required reviewers** can be automatically added based on repository, branch or path rules and cannot simply be replaced by optional reviewers. A **comment-resolution** policy can require active threads to be resolved before completion when configured as blocking. These checks are distinct from the minimum vote count.

A PR changing `/payments/` may invoke the required payment reviewers. If one chooses Wait for author and a thread remains active, the author should address and re-request review, not delete discussion to hide concerns.

</details>

- [Back to top](#back-to-top)

---

## <a id="build-validation-status-checks">Build Validation, Status Checks, and the CI/CD Boundary</a>

<details>
<summary>Click for details</summary>

**Build validation** requires an acceptable build/test result for proposed integration; **status checks** may consume results from an external service such as security scanning. Azure Repos evaluates the status according to target-branch policy, while YAML pipelines, agents and service configuration belong to Azure Pipelines/CI/CD.

If a required build fails unit tests, approvals alone cannot complete the PR. Inspect the check identity, run and result; configuring the pipeline is outside this Azure Repos course.

</details>

- [Back to top](#back-to-top)

---

## <a id="permitted-merge-types">Permitted Merge Types During PR Completion</a>

<details>
<summary>Click for details</summary>

Azure Repos can restrict **completion merge strategies**: **basic merge** creates a merge commit, **squash** produces one consolidated target commit, and **rebase** variants replay changes with or without an additional merge commit. All still require appropriate PR rights and target policies.

A team preferring one commit per PR might allow squash while restricting other methods; this neither resolves conflicts automatically nor proves test success. Git owns commit graph mechanics, while team-wide strategy selection belongs to branching-strategy.

</details>

- [Back to top](#back-to-top)

---

## <a id="bypass-on-pr-versus-push">Bypass Policies When Completing PRs Versus When Pushing</a>

<details>
<summary>Click for details</summary>

Azure Repos separates **Bypass policies when completing pull requests** (an authorized actor deliberately selects **Override branch policies** when completing) from **Bypass policies when pushing** (for an otherwise permitted push, branch-policy bypass occurs **automatically**, without the PR's opt-in step). These are **different permissions**, not one toggle. Push bypass **does not itself grant a missing Contribute/branch write right**, and ordinary Contribute or an admin job title does not automatically confer both bypass grants.

During an emergency, a narrowly authorized release owner may complete a PR with recorded override rationale without permission for direct pushes to main. Grant at narrow scope, audit the exception and revoke it afterward; bypass does not make code safe.

### References

- [Microsoft Learn — bypass on pr versus push](https://learn.microsoft.com/en-us/azure/devops/repos/git/branch-policies?view=azure-devops)
- [Microsoft Learn — Branch permissions and separate bypass behavior](https://learn.microsoft.com/en-us/azure/devops/repos/git/branch-permissions?view=azure-devops)

</details>

- [Back to top](#back-to-top)

---

## <a id="diagnose-policy-failures">Diagnosing Unmet Policies, Checks, and Bypass Permissions</a>

<details>
<summary>Click for details</summary>

When completion is disabled, check the **target branch**, Draft/Active state, minimum reviewers, required reviewers, comment resolution, build/status checks, conflicts and the actor's **effective permissions**. More approvals or blanket bypass permissions are not universal fixes.

If two approvals exist but a required build fails, follow Policies → build result and fix the failure. If checks pass but completion is unauthorized, inspect repo/branch security. For a fork PR, evaluate policies on the **upstream target**, not the fork source.

</details>

- [Back to top](#back-to-top)
