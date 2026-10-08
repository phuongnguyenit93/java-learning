<a id="back-to-top"></a>

# Coordinating Cross-Component and Cross-Repository Changes

## Menu
- [Cross-Component Changes: Concept and Basic Scenario](#what-is-cross-component-change)
- [Sources of Coordination Cost in Cross-Team Changes](#why-coordination-gets-expensive)
- [Shared History for Observing and Reviewing Cross-Component Changes](#single-history-coordinated-change)
- [Coordinating Related Changes Across Separate Repositories](#multiple-repositories-linked-changes)
- [Integration Order and Cross-Component Compatibility](#integration-order-and-compatibility)
- [Review Accountability and Context Preservation Across Teams](#review-responsibility-and-context)
- [Tracing Requirements and Changes Across Components](#traceability-across-components)
- [Evidence of Team Conflicts and Unresolved Dependencies](#coordination-failure-evidence)
- [Case Study: Coordinating a Change Across Three Components](#compare-cross-repo-change-scenario)

## <a id="what-is-cross-component-change">Cross-Component Changes: Concept and Basic Scenario</a>

<details>
<summary>Click for details</summary>

A **cross-component change** is a requirement whose correct outcome needs updates to several code units or their shared contract. If an API renames `totalAmount` to `amount`, its web client and DTO library may also need changes; updating only one can break users even when its repository tests pass.

Start by naming the provider, consumers, contract and release environments. Repository boundaries affect where changes are recorded and reviewed, not whether the underlying dependency exists.

</details>

- [Back to top](#back-to-top)

---

## <a id="why-coordination-gets-expensive">Sources of Coordination Cost in Cross-Team Changes</a>

<details>
<summary>Click for details</summary>

Coordination costs come from **waiting across owners**, sequencing changes, managing differing interface versions and reviewing related work. Polyrepos can expose separate commit/release events; monorepos may show one diff while still waiting on owners or tests. Dependency coordination matters more than a raw PR count.

For three linked PRs changing an API field, the web PR may wait until the library is published. Measure waiting and rollback frequency before deciding topology is the problem.

</details>

- [Back to top](#back-to-top)

---

## <a id="single-history-coordinated-change">Shared History for Observing and Reviewing Cross-Component Changes</a>

<details>
<summary>Click for details</summary>

In a monorepo, one commit/PR can modify `/api`, `/web` and `/contracts` together. Reviewers see the three changes at **one history point**, reducing forgotten consumers. Google research notes visibility and coordinated API migration as common-repository strengths.

A large PR can still overwhelm reviewers or fail tests; teams remain separately accountable. Keep change scope reviewable, request relevant path owners, and verify consumers actually compile or pass contract tests rather than trusting one green status.

### References

- [Jaspan et al. (2018) — survey and developer-tool-log evidence](https://research.google/pubs/advantages-and-disadvantages-of-a-monolithic-codebase/)

</details>

- [Back to top](#back-to-top)

---

## <a id="multiple-repositories-linked-changes">Coordinating Related Changes Across Separate Repositories</a>

<details>
<summary>Click for details</summary>

In a polyrepo, a requirement may create PRs such as `contracts#42`, `api#81`, and `web#17`. Each has independent history and reviews, so **linked tickets/change identifiers**, owners and intended merge order are essential. Git does not make separate repository commits atomically together by default.

If the new contract is incompatible, consumers must wait for a suitable provider version—or a staged backward-compatible rollout can avoid timing hazards. Inspect all linked PRs and actual dependency versions, not just PR titles.

</details>

- [Back to top](#back-to-top)

---

## <a id="integration-order-and-compatibility">Integration Order and Cross-Component Compatibility</a>

<details>
<summary>Click for details</summary>

**Integration order** matters when providers and consumers cannot understand both old and new interfaces. A safer coordination pattern is **expand–migrate–contract**: temporarily accept both representations, upgrade consumers, then remove the old interface once adoption is complete. This is a compatibility plan, not a CI implementation recipe.

An API accepting both `totalAmount` and `amount` allows clients to migrate on different dates. Validate supported API/client version combinations and contract failures, not merely merge timestamps.

</details>

- [Back to top](#back-to-top)

---

## <a id="review-responsibility-and-context">Review Accountability and Context Preservation Across Teams</a>

<details>
<summary>Click for details</summary>

Cross-team changes need reviewers for **each component's logic** and someone accountable for **the contract connecting them**. Repository or path-based reviews alone do not guarantee whole-feature understanding. Link the original requirement, contract decision, migration plan and owners.

A backend reviewer checks JSON behavior, a frontend reviewer checks displayed values, and a contract owner checks backward compatibility. An unowned concern is a blocker even when two reviewers from one team approved.

</details>

- [Back to top](#back-to-top)

---

## <a id="traceability-across-components">Tracing Requirements and Changes Across Components</a>

<details>
<summary>Click for details</summary>

**Traceability** means moving from one requirement to every relevant change, review decision, version and test result. A monorepo may provide one common PR/commit if the change truly traveled together; polyrepos need a shared ticket linking PRs and release outcomes. Do not mark work complete while a consumer remains unupgraded.

A `REQ-17` record can list contract/API/web PRs, approvals and released versions. During an incident it separates missing source changes from undeployed consumers and incompatible interfaces.

</details>

- [Back to top](#back-to-top)

---

## <a id="coordination-failure-evidence">Evidence of Team Conflicts and Unresolved Dependencies</a>

<details>
<summary>Click for details</summary>

Coordination failure shows up as **PRs waiting for unknown owners**, library releases consumers cannot adopt, repeated integration rollbacks, or production errors when incompatible interface versions coexist. These occur under both topologies; repository shape alone is not a root-cause diagnosis.

Collect the timeline from request through linked PRs, approvals, contract checks, release and incident. If the dominant delay is an unstable interface contract, switching to a monorepo without ownership and compatibility discipline will not fix it.

</details>

- [Back to top](#back-to-top)

---

## <a id="compare-cross-repo-change-scenario">Case Study: Coordinating a Change Across Three Components</a>

<details>
<summary>Click for details</summary>

Consider a shipping-address contract affecting **Schema**, **Checkout API**, and **Web**. In a monorepo, one PR can update all three with affected-component validation, but reviewers from several areas must inspect a larger diff. In a polyrepo, Schema publishes a compatible version, Checkout adopts it, then Web uses the new API; staggered merge/release timing is the risk.

Compare end-to-end lead time, consumer breakages, reviewer ownership and rollback feasibility. Choose topology only after distinguishing repository coordination from contract design problems.

</details>

- [Back to top](#back-to-top)
