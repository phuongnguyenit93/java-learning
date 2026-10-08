<a id="back-to-top"></a>

# Trunk-Based Development and Frequent Integration

## Menu
- [Trunk-Based Development: Frequent Integration to Prevent Branch Backlogs](#tbd-definition-purpose)
- [One Trunk and the Expectation of a Reliable Mainline](#single-trunk-reliable-mainline)
- [Direct-to-Trunk vs Very Short-Lived Review Branches](#direct-vs-short-branch)
- [Small Changes, Early Validation, and Frequent Integration](#frequent-integration-small-changes)
- [Feature Flags and Optional Release Branches in Trunk-Based Workflows](#feature-flags-release-branches)
- [Evidence of Real Trunk-Based Practices and Common Anti-Patterns](#tbd-evidence-antipatterns)

## <a id="tbd-definition-purpose">Trunk-Based Development: Frequent Integration to Prevent Branch Backlogs</a>

<details>
<summary>Click for details</summary>

**Trunk-Based Development (TBD)** keeps contributors integrating frequently into one central stream, directly or through very short review branches. Its objective is to avoid integration hell caused by long-running feature branches. The defining evidence is **low divergence and high integration frequency**, not whether a branch happens to be named `main`. TBD does not forbid reviews or require every merge to deploy immediately.

The payments team keeps `main` as trunk and integrates refunds through small safe increments while prioritizing the rounding fix. If a team calls itself TBD but keeps a refund PR open for three weeks, its observed behavior contradicts the model.

### References
- [Trunk Based Development — Introduction](https://trunkbaseddevelopment.com/)

</details>

- [Back to top](#back-to-top)

---

## <a id="single-trunk-reliable-mainline">One Trunk and the Expectation of a Reliable Mainline</a>

<details>
<summary>Click for details</summary>

**Trunk** is the convergence point for accepted work, so the team must keep it reliable enough for other contributors. Reliable does not mean every feature is visible; it means critical builds, tests, and shared contracts remain sound. Policy should assign ownership for a broken mainline and prioritize restoration instead of ignoring it.

Suppose a refund change breaks a payment check. The failure becomes visible quickly, and an owner prioritizes a validated revert or fix-forward. Parking the failure on an indefinite "fix main later" branch while everyone continues is not the intended habit.

**Evidence:** time mainline spends broken, named incident responders, and recovery latency. How a build pipeline runs belongs to CI; the branching strategy specifies the quality expectation.

</details>

- [Back to top](#back-to-top)

---

## <a id="direct-vs-short-branch">Direct-to-Trunk vs Very Short-Lived Review Branches</a>

<details>
<summary>Click for details</summary>

TBD can use **direct commits to trunk** in a small team with suitable validation and review practices, or **short-lived branches** for pre-merge review. Those branches should genuinely be short: practical TBD guidance targets roughly a day or two, often one developer (or a pair). A long unfinished branch merged at the end is not TBD merely because it used a PR.

An fixes rounding on a one-day branch, gets review, and merges to main; another team might integrate directly under its own quality policy. Both paths need equivalent reliability expectations.

**Observe:** measure unintegrated branch lifetime, contributors per branch, and lag between approval and merge.

### References
- [Trunk Based Development — Short-lived feature branches](https://trunkbaseddevelopment.com/short-lived-feature-branches/)

</details>

- [Back to top](#back-to-top)

---

## <a id="frequent-integration-small-changes">Small Changes, Early Validation, and Frequent Integration</a>

<details>
<summary>Click for details</summary>

Frequent integration requires **small yet coherent increments**, each understandable and verifiable on its own. A large payment API change might be split into compatible preparation, behind-flag behavior, validation/observability, and controlled enablement. One business outcome can therefore produce multiple trunk integrations.

Waiting until the entire refund feature is finished encourages divergence; merging an unbuildable half-implementation violates trunk reliability. The skill is choosing safe increment boundaries, not maximizing merge count.

**Scenario:** outline three PRs and demonstrate that each preserves the relevant checks and unchanged user behavior with the flag off. Measure review queue time to see whether feedback really arrives early.

</details>

- [Back to top](#back-to-top)

---

## <a id="feature-flags-release-branches">Feature Flags and Optional Release Branches in Trunk-Based Workflows</a>

<details>
<summary>Click for details</summary>

TBD does not prohibit release branches. A continuous-deployment team may release straight from trunk; a scheduled-release team can cut a **release branch just in time**, then restrict it to stabilization and necessary fixes. **Feature flags** permit integrated code to remain inactive, separating integration from release and exposure.

The refund implementation is on main but off in v1.4. Near the release date, the team may cut a short stabilization branch while trunk continues advancing. **Trunk-Based Development guidance favors reproducing, fixing, and verifying a defect on trunk first**, then selectively carrying the needed patch to the release branch and verifying that line separately. This is a **trunk → release** policy, unlike classic Git Flow, whose production hotfixes can originate on the production line and propagate back into develop. Only when a defect cannot be reproduced on trunk should release-first work be considered, with an explicit plan to prevent the fix from going missing on trunk.

**Evidence:** branch point, allowed change types, chosen release tag, and retirement criteria.

### References
- [Trunk Based Development — Branch for release](https://trunkbaseddevelopment.com/branch-for-release/)

</details>

- [Back to top](#back-to-top)

---

## <a id="tbd-evidence-antipatterns">Evidence of Real Trunk-Based Practices and Common Anti-Patterns</a>

<details>
<summary>Click for details</summary>

Do not classify TBD from a README branch diagram alone. **Healthy evidence** includes small PRs, low branch age, little unintegrated work, rapid mainline recovery, and no secondary long-running feature integration branch. **Antipatterns** include an indefinite develop line, five developers sharing a refund branch for weeks, or one giant end-of-sprint merge.

If 80% of PRs merge within a day but one critical PR remains open for thirty days, study the tail of the distribution, not just the median. Also verify that increments are safe; unchecked flags do not make risky code healthy.

**Measure:** branch age, batch size, merge frequency, time main is broken, and exceptions. End each retrospective with an owned improvement action.

</details>

- [Back to top](#back-to-top)
