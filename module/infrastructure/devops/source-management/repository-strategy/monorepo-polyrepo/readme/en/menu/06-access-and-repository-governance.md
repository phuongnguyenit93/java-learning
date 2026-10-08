<a id="back-to-top"></a>

# Repository Access and Governance

## Menu
- [Repository-Level Read and Write Access Boundaries](#repository-access-boundary)
- [Code Owners and Path-Based Review Governance](#path-ownership-and-review-governance)
- [Path-Based Reviews Do Not Replace Read-Access Isolation](#review-rules-vs-access-isolation)
- [Security and Compliance Constraints on Repository Separation](#restricted-source-and-compliance)
- [Consistent Governance and Review Policies Across Polyrepos](#governance-consistency-across-polyrepos)
- [Risks from Missing Code Ownership in Monorepos](#monorepo-ownership-gaps)
- [Policy Drift and Inconsistency Across Polyrepos](#polyrepo-policy-drift)
- [Topology Decisions Against Access and Governance Constraints](#compare-governance-constraints)

## <a id="repository-access-boundary">Repository-Level Read and Write Access Boundaries</a>

<details>
<summary>Click for details</summary>

**Repository-level access** usually determines who may clone/read source, push changes and manage settings. A shared repository containing public-to-team and restricted source can expose both to people with read access unless an additional reliable isolation mechanism exists. Polyrepos can apply different permissions by repository but multiply policy administration.

If a vendor may inspect Web UI code but must not see Fraud code, separate `web.git` and `fraud.git` can establish read boundaries. Check legal obligations and actual hosting capabilities rather than folder naming.

</details>

- [Back to top](#back-to-top)

---

## <a id="path-ownership-and-review-governance">Code Owners and Path-Based Review Governance</a>

<details>
<summary>Click for details</summary>

A monorepo can use **path ownership** and review rules so changes under `/billing/` receive Billing expertise even when a different team authors them. GitHub CODEOWNERS requests owners; mandatory approval depends on configured protection/rulesets and plan availability. Ownership rules themselves should be protected.

If a PR touches `/security/keys` and `/web/`, relevant owners should inspect the change. But CODEOWNERS **does not create directory-level read isolation**; it governs reviews, not source visibility.

### References

- [GitHub Docs — Code owners](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/about-code-owners)

</details>

- [Back to top](#back-to-top)

---

## <a id="review-rules-vs-access-isolation">Path-Based Reviews Do Not Replace Read-Access Isolation</a>

<details>
<summary>Click for details</summary>

A **review gate** controls whether approvals are needed before changes integrate. **Read-access isolation** controls who can download source. Someone allowed to clone a repository can typically view paths they are not authorized to approve or modify. Path ownership in a monorepo therefore cannot replace separate read boundaries for restricted code.

A Security-required crypto review reduces unsafe edits but does **not prevent vendors who can clone the repo from reading crypto source**. Restricted material needs an access boundary and dependency coordination across it.

</details>

- [Back to top](#back-to-top)

---

## <a id="restricted-source-and-compliance">Security and Compliance Constraints on Repository Separation</a>

<details>
<summary>Click for details</summary>

**Security and compliance** requirements can dominate convenience: partner-licensed code, regulated source, export restrictions or separate audit obligations may require explicit read/write boundaries. Polyrepos may help isolation only if hosting, CI credentials and artifact sharing honor the same constraints.

If partner-controlled source must be visible to one group only, copying it into a broadly readable monorepo merely to reduce PRs is unacceptable. Document access obligations and consult security/legal owners before consolidation.

</details>

- [Back to top](#back-to-top)

---

## <a id="governance-consistency-across-polyrepos">Consistent Governance and Review Policies Across Polyrepos</a>

<details>
<summary>Click for details</summary>

Polyrepos enable **repository-specific rights and review rules**, but operations still need minimum baselines: protected branches, required reviews, secret controls, owner contacts and incident process. Local flexibility is valuable; inconsistent baselines can create a weak-link repository.

If eleven of twelve repositories require review while one does not, a security-sensitive change could bypass the intended standard through that repo. Maintain a repository inventory and review policy drift periodically rather than equating repository count with maturity.

</details>

- [Back to top](#back-to-top)

---

## <a id="monorepo-ownership-gaps">Risks from Missing Code Ownership in Monorepos</a>

<details>
<summary>Click for details</summary>

A large monorepo without explicit owners can become **everyone can change it, nobody is accountable**: shared library updates lack compatibility review, new developers cannot locate experts, and reviews focus only on style. Area ownership needs primary/backup owners, coverage of new paths and escalation routes.

If `/libs/contracts` has no owner despite API/Web dependencies, each team may change it for local needs. CODEOWNERS helps request reviews but must be backed by real accountability and contract checks.

</details>

- [Back to top](#back-to-top)

---

## <a id="polyrepo-policy-drift">Policy Drift and Inconsistency Across Polyrepos</a>

<details>
<summary>Click for details</summary>

**Policy drift** occurs when repositories gradually diverge in unintended ways: repo A requires checks, B forgets them, C has a single maintainer, D has no backup owner. Differences can be legitimate for different risk levels, but unplanned drift raises audit and security cost.

Maintain a catalog of owners, required policies, access levels, review intervals and approved exceptions. Fix the specific weak boundary rather than forcing identical settings regardless of product risk.

</details>

- [Back to top](#back-to-top)

---

## <a id="compare-governance-constraints">Topology Decisions Against Access and Governance Constraints</a>

<details>
<summary>Click for details</summary>

A governance decision matrix should capture **read access**, **write/merge rights**, **required reviewers**, source sensitivity, shared-code demand and policy maintenance cost. Monorepo favors source visibility and path review; polyrepo can isolate reading by repository. Non-negotiable read restrictions are hard constraints, not small weighted preferences.

If Fraud must remain restricted while Checkout is broadly internal, two repos may be necessary despite more interface PRs. Measure excess access grants, policy exceptions and coordination cost after the decision.

</details>

- [Back to top](#back-to-top)
