<a id="back-to-top"></a>

# Branch Lifetime and Integration Cadence

## Menu
- [Short-Lived vs Long-Lived Branches: Purpose and Cost](#short-vs-long-lived-branches)
- [Integration Cadence, Change Size, and Early Feedback](#cadence-change-size-feedback)
- [Branch Divergence, Merge Conflicts, and Batched Integration: Evidence](#divergence-and-conflict-evidence)
- [Small Changes and Early Review Without Delayed Integration](#review-small-increments)
- [Feature Flags: Controlled Exposure of Incomplete Work and Incremental Integration](#incomplete-work-policy)

## <a id="short-vs-long-lived-branches">Short-Lived vs Long-Lived Branches: Purpose and Cost</a>

<details>
<summary>Click for details</summary>

A **short-lived branch** has a small purpose, merges quickly, and is retired; a **long-lived branch** carries work or maintenance across many changes. The difference is not naming: time away from mainline accumulates incompatible API assumptions and more review burden. However, `release/1.4` may legitimately persist while customers still run that version; a feature-branch deletion policy should not automatically apply to maintenance lines.

An fixes rounding in one day on `fix-rounding`; Binh builds refunds over three weeks on `feature/refunds`. A long-lived feature branch magnifies end-of-cycle integration risk. Split independent increments and use flags where appropriate.

**Observe:** measure age from divergence to merge, updates from mainline, and unintegrated commits, not merely branch prefixes.

</details>

- [Back to top](#back-to-top)

---

## <a id="cadence-change-size-feedback">Integration Cadence, Change Size, and Early Feedback</a>

<details>
<summary>Click for details</summary>

**Integration cadence** is how often accepted changes enter mainline; **batch size** is the scope reviewed and merged each time. Small, frequent integrations expose incompatible assumptions earlier and help identify failures. They do not guarantee safety: increments still need coherent behavior and evidence.

Two focused PRs of twenty lines each can be easier to review than a two-thousand-line PR mixing multiple payment behaviors after two weeks. Yet a team must not merge half a transaction flow that breaks production merely to hit a frequency target.

**Measure:** PR size, queue time, integration lead time, and time main remains broken. Small PRs waiting five days are not frequent integration in practice.

</details>

- [Back to top](#back-to-top)

---

## <a id="divergence-and-conflict-evidence">Branch Divergence, Merge Conflicts, and Batched Integration: Evidence</a>

<details>
<summary>Click for details</summary>

Branches diverge when each contains commits absent from the other. Risks include textual conflicts, stale tests, and **semantic conflicts** where merging succeeds but the combined behavior violates assumptions. A widening graph shows growing separation; it does not prove a defect until verified.

An changes currency units while Binh fixes rounding against old units. Git may show no textual conflict, yet a domain test exposes incorrect amounts. Early synchronization and context-aware review are therefore important.

**Evidence:** inspect branch graph, ahead/behind counts, merge-base age, repeated conflicts, and integration test outcomes. Differentiate generated-file churn from genuine domain change before deciding whether branch lifetime or review practice is the real cause.

</details>

- [Back to top](#back-to-top)

---

## <a id="review-small-increments">Small Changes and Early Review Without Delayed Integration</a>

<details>
<summary>Click for details</summary>

Break large features into reviewable **vertical slices**: preparatory refactoring, compatible interfaces, a minimum behavioral increment, then controlled exposure. Each increment must keep shared source reliable. Early review surfaces assumptions before the entire refund system is finished. One business story can produce several PRs rather than forcing a single huge ticket-sized change.

Refunds might use PR 1 for a backward-compatible interface, PR 2 for implementation hidden by an off-by-default flag, and PR 3 for monitoring and safe enablement. Reviewers can examine each risk in isolation.

**Verify:** every PR states purpose, limited scope, acceptance evidence, and integration readiness. Splitting work is not permission to merge unbuildable intermediate code.

</details>

- [Back to top](#back-to-top)

---

## <a id="incomplete-work-policy">Feature Flags: Controlled Exposure of Incomplete Work and Incremental Integration</a>

<details>
<summary>Click for details</summary>

A **feature flag** gates behavior at runtime or delivery time, allowing code to integrate before users see the new behavior. Unlike a branch, which separates source histories, a flag controls execution paths in shared source. It supports Trunk-Based Development and decoupled release planning but adds state complexity, tests for both settings, and cleanup work.

The refund implementation merges behind `refunds.enabled=false`; production keeps the old path while the team validates and later enables refunds gradually. A flag cannot hide an incompatible database migration or a security leak simply by being off.

**Checklist:** assign flag owner, default state, on/off tests, rollout and rollback criteria, and deletion date. A flag complements rather than replaces review.

### References
- [Trunk Based Development — Feature flags](https://trunkbaseddevelopment.com/feature-flags/)

</details>

- [Back to top](#back-to-top)
