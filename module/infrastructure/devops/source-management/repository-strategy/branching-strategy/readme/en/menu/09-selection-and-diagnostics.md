<a id="back-to-top"></a>

# Branching Strategy Selection and Failure Indicators

## Menu
- [Trunk-Based Development, GitHub Flow, and Git Flow: Strategy Comparison](#compare-three-models)
- [Release Cadence, Version Support, Team Size, and Check Maturity](#selection-context)
- [Long-Lived Branches and Delayed Integration: Diagnostic Signals](#long-branch-delayed-integration)
- [Recurring Conflicts and Opaque History: Diagnostic Signals](#repeat-conflicts-opacity)
- [Missing Backports Across Required Maintenance Lines](#missing-hotfix-backports)
- [Sample Team Branch Policies and Evidence for Evaluating Outcomes](#sample-team-policy-evidence)
- [Final Handoffs to Git Mechanics, Collaboration Platforms, and Delivery Automation](#strategy-handoffs)

## <a id="compare-three-models">Trunk-Based Development, GitHub Flow, and Git Flow: Strategy Comparison</a>

<details>
<summary>Click for details</summary>

The three models optimize different **primary concerns**. Trunk-Based Development prioritizes frequent integration and minimal divergence; GitHub Flow emphasizes change branches and PR review; Git Flow coordinates develop, release, and hotfix branches for versioned stabilization. They are not entirely mutually exclusive switches: GitHub Flow using one-day branches can align with TBD, and carefully scoped release branches can coexist with trunk-based practice.

A continuously deployed payment website likely favors small trunk integrations and PR review; installed software supporting 1.4 and 1.5 may need maintenance lines. Do not select Git Flow merely because the team is large or call every `main` repository TBD.

**Comparison matrix:** goals, permanent branches, branch lifespan, integration cadence, release/hotfix handling, and synchronization overhead.

</details>

- [Back to top](#back-to-top)

---

## <a id="selection-context">Release Cadence, Version Support, Team Size, and Check Maturity</a>

<details>
<summary>Click for details</summary>

No workflow is universally best. Ask about **release frequency**, **number of supported versions**, **team size and risk**, **automated validation confidence**, and audit requirements. A SaaS product with fast roll-forward differs from desktop software that must maintain old versions or pass formal release qualification.

A six-person team deploying daily with contract checks and reliable rollback may use TBD plus reviewed PRs. A vendor supporting offline 1.4 while building 2.0 may require explicit maintenance lines, without copying every classic Git Flow branch.

**Decision evidence:** actual delivery cadence, validation feedback time, concurrent live releases, hotfix demand, and team skills. Without measurement, a branch diagram can become an imaginary policy.

</details>

- [Back to top](#back-to-top)

---

## <a id="long-branch-delayed-integration">Long-Lived Branches and Delayed Integration: Diagnostic Signals</a>

<details>
<summary>Click for details</summary>

**Long-lived feature branches** accumulate unintegrated assumptions, review burden, and late reconciliation. Warning signs include multiple developers sharing one work branch, enormous PRs, repeated conflicts, and little updating from a fast-moving mainline. A long-lived customer maintenance branch is a different category and should not be judged by feature-branch rules.

A refund branch stays open twenty-four days, changes seventy-three files, and receives first review on the last day. Break it into compatible interface preparation, flagged behavior, and completion instead of attempting a giant overnight merge.

**Diagnose:** measure p50/p90 PR age, diff size, and lag between first commit and merge, separating feature and release branches.

</details>

- [Back to top](#back-to-top)

---

## <a id="repeat-conflicts-opacity">Recurring Conflicts and Opaque History: Diagnostic Signals</a>

<details>
<summary>Click for details</summary>

**Repeated conflicts** can indicate prolonged divergence, overlapping ownership, or uncoordinated architecture changes. **Opaque history** can arise from noisy merge commits, meaningless messages, or squashing unrelated changes into one giant record. Changing to rebase or squash does not automatically solve a semantic coordination failure.

Two teams edit a payment API contract on different branches. Their merge succeeds textually but integration tests fail. Assign contract ownership and split compatible changes instead of simply switching the merge button.

**Evidence:** conflicts by directory, review lead time, escaped integration failures, and ability to trace a released commit back to its PR and Issue.

</details>

- [Back to top](#back-to-top)

---

## <a id="missing-hotfix-backports">Missing Backports Across Required Maintenance Lines</a>

<details>
<summary>Click for details</summary>

A fix applied only to one line can create **version-specific regressions**: 1.4 customers remain exposed despite a mainline fix, or 1.6 reintroduces a defect previously patched in 1.4. This is a missing **fix propagation policy**, not Git failing to merge magically. Concurrent support requires a clear affected-version list and backport/forward-port owners.

The rounding defect affects both 1.4 and main. The incident is fully resolved only when every supported affected line has a validated patch and appropriate release. If a line is unaffected, record evidence rather than blindly copying commits.

**Verify:** maintain defect × release line with affected status, patch PR, test evidence, published version, and sign-off owner.

</details>

- [Back to top](#back-to-top)

---

## <a id="sample-team-policy-evidence">Sample Team Branch Policies and Evidence for Evaluating Outcomes</a>

<details>
<summary>Click for details</summary>

The six-person payments team deploys its SaaS daily but supports 1.4 for three months: use **one mainline for new features**, short reviewed PRs targeted at a day or two, feature flags for refunds, and **release/1.4 only for validated backports**. Define risk-based reviewers, merge gates, mainline recovery owner, and patch-release owner. This is a debatable concrete policy, not a universal recipe.

**Four-week trial:** measure p50/p90 PR age, code-to-integration time, broken-main incidents and recovery duration, and missing 1.4 patches. If stale branches persist, split work and fix review queues; if hotfixes go missing, improve the supported-line matrix.

**Evidence:** a decision record explaining the chosen model plus before/after outcomes.

</details>

- [Back to top](#back-to-top)

---

## <a id="strategy-handoffs">Final Handoffs to Git Mechanics, Collaboration Platforms, and Delivery Automation</a>

<details>
<summary>Click for details</summary>

Keep ownership boundaries clear instead of turning strategy into Git command documentation. The **Git module** covers commit graphs and merge/rebase/cherry-pick/revert mechanics; **GitHub/GitLab** implement PRs/MRs, roles, and branch restrictions; **CI/CD** runs checks, builds, and delivery; **Release management** defines version and support lifecycle. Branching Strategy owns the **when and why** behind those mechanisms.

If main is merged but production is unchanged, verify hosted integration then hand delivery failures to deployment owners rather than redesigning branches. If a patch exists only on v1.4, inspect propagation policy before blaming CI.

**Takeaway:** retain a one-page policy, role/gate map, and supported-version matrix; avoid inventing extra knowledge routes that duplicate neighboring module ownership.

</details>

- [Back to top](#back-to-top)
