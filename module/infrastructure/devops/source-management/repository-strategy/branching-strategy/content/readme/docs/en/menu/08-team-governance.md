<a id="back-to-top"></a>

# Team Conventions and Integration Governance

## Menu
- [Branch Purpose, Naming, and Ownership Conventions](#branch-purpose-naming)
- [Branch Lifetime, Staleness, and Cleanup Policies](#branch-lifetime-cleanup)
- [Review Expectations, Responsibility, and Feedback Timing](#review-expectations)
- [Policy-Level Pre-Merge Quality Gates](#premerge-gates-policy)
- [Ownership of Mainline Health and Failed Integration](#mainline-health-ownership)
- [Team Policy Intent vs Vendor Branch Protection, Permissions, and CI Configuration](#vendor-enforcement-boundary)

## <a id="branch-purpose-naming">Branch Purpose, Naming, and Ownership Conventions</a>

<details>
<summary>Click for details</summary>

Branch conventions should reveal **purpose and accountable owner**, helping contributors distinguish temporary change branches, release preparation, and maintenance lines. Prefixes such as `feature/`, `fix/`, and `release/` help discovery but do not grant permissions or impose branch lifetimes. Policy identifies who may cut releases, change targets, and declare work finished.

`fix/42-rounding` links Issue #42 and `release/1.4` has a release owner; `temp-final-final` communicates little and tends to linger. Names are only evidence hints; PR descriptions and ownership still matter.

**Checklist:** naming scheme, work-item link, owner, intended target, and deletion event. Hosting permissions are implemented in the GitHub/GitLab modules.

</details>

- [Back to top](#back-to-top)

---

## <a id="branch-lifetime-cleanup">Branch Lifetime, Staleness, and Cleanup Policies</a>

<details>
<summary>Click for details</summary>

Stale feature branches delay feedback, accumulate conflicts, and clutter the branch list. Teams need **branch-age expectations** aligned with their model: very short in TBD, context-dependent in GitHub Flow, and support-driven for release branches. Auto-delete after merging can help but does not resolve abandoned open PRs.

A refund branch has seen no commit for eighteen days. Its owner should split the work, close it, set a realistic next integration date, or retire the branch after confirming it is no longer needed. Do not delete a 1.4 release line that still serves customers.

**Observe:** branch/PR age, inactivity, closed-but-undeleted branches, and exception owners; review anomalies regularly rather than deleting everything automatically.

</details>

- [Back to top](#back-to-top)

---

## <a id="review-expectations">Review Expectations, Responsibility, and Feedback Timing</a>

<details>
<summary>Click for details</summary>

Effective review policy defines its **purpose** (correctness, compatibility, maintainability), **accountable reviewers**, and **response expectations**. Different change risks may justify different reviewer requirements; sensitive payment behavior needs domain expertise, while minor documentation changes may use lighter review. Multi-day review queues defeat rapid integration even with small branches.

A rounding PR needs someone who understands negative amounts and relevant tests. A README typo need not wait for the same specialist team. A requested reviewer is not proof of approval, and team policy must distinguish discussion from merge gates.

**Evidence:** median and tail time to first review, comment resolution time, eligible decision owners, and escaped defects.

</details>

- [Back to top](#back-to-top)

---

## <a id="premerge-gates-policy">Policy-Level Pre-Merge Quality Gates</a>

<details>
<summary>Click for details</summary>

**Pre-merge gates** are the evidence a team requires before integration: core tests, appropriate review, compatibility checks, and perhaps security evaluation for high-risk changes. Strategy defines **which evidence matters**; hosting permissions and CI systems enforce parts of it. Do not claim every pipeline is mandatory when only specific checks are configured.

A payment-schema change merits contract tests and domain approval; a documentation typo may warrant lighter validation. A gate should prevent meaningful risk rather than create an endless queue for low-impact PRs.

**Policy sample:** specify checks by change class, owners of failed/pending results, emergency bypass criteria, and decision logging. CI workflow YAML belongs to another module, not this lesson.

</details>

- [Back to top](#back-to-top)

---

## <a id="mainline-health-ownership">Ownership of Mainline Health and Failed Integration</a>

<details>
<summary>Click for details</summary>

A reliable mainline needs clear ownership when integration breaks build, tests, or shared contracts. **Shared responsibility** must not become nobody's responsibility: teams can designate an on-call owner or require the change author to prioritize recovery. Pausing further merges while main is broken may prevent compounding failures.

A refund PR breaks compilation on main while An is preparing a rounding fix. The responsible team member restores main through a verified fix or revert before continuing routine merges. Treat the incident as an integration responsibility, not automatically a broken CI system.

**Evidence:** detection-to-recovery time, frequency of broken mainline, documented cause, and named responders.

</details>

- [Back to top](#back-to-top)

---

## <a id="vendor-enforcement-boundary">Team Policy Intent vs Vendor Branch Protection, Permissions, and CI Configuration</a>

<details>
<summary>Click for details</summary>

A team rule such as "main requires review and tests" is vendor-independent. GitHub provides pull requests and branch protection/rulesets; GitLab provides merge requests, protected branches, and approval rules. Each platform has **different tier, permission, and rule-combination semantics**. CI reports validation results; it does not decide how many permanent branches a team needs.

If a team migrates from GitHub to GitLab while retaining short branches and two reviewers, it can keep the workflow but must confirm available enforcement features and plan limits. Copying a GitHub ruleset assumption directly into GitLab protection can be incorrect.

**Handoff evidence:** vendor-neutral policy, platform gate mapping, permitted/denied behavior tests, and configuration owners.

</details>

- [Back to top](#back-to-top)
