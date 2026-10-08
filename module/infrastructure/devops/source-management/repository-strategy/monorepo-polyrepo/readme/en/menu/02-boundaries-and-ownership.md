<a id="back-to-top"></a>

# Repository Boundaries and Code Ownership

## Menu
- [Code Ownership: Definition, Purpose, and Accountability](#what-is-code-ownership)
- [Ownership by Directory or Component in a Monorepo](#ownership-by-path-or-component)
- [Ownership by Individual Repository in a Polyrepo](#ownership-by-repository)
- [Code Discoverability and Reuse Across Repository Topologies](#discoverability-and-reuse)
- [Code Ownership Versus Source Access Permissions](#ownership-versus-access-control)
- [Team Boundaries Need Not Match Repository Boundaries](#team-boundary-versus-repository-boundary)
- [Service Count Does Not Determine Repository Count](#service-count-is-not-repository-count)
- [Evaluating Ownership Areas and Overlap on a Repository Map](#verify-ownership-boundaries)

## <a id="what-is-code-ownership">Code Ownership: Definition, Purpose, and Accountability</a>

<details>
<summary>Click for details</summary>

**Code ownership** assigns accountability for quality, change decisions and incidents in a source area. It is an engineering responsibility supported by owner maps or required review rules, not necessarily exclusive permission to read or modify. It needs backup coverage when people leave or rotate.

If Payments owns `/payments/`, an edit by a Web engineer still needs payment-domain review. Evaluate ownership by whether the responsible reviewer can actually be found, not merely by a team label.

</details>

- [Back to top](#back-to-top)

---

## <a id="ownership-by-path-or-component">Ownership by Directory or Component in a Monorepo</a>

<details>
<summary>Click for details</summary>

In a **monorepo**, ownership can follow paths such as `/apps/web/` for Web and `/libs/auth/` for Security. GitHub **CODEOWNERS** can request owners when a PR touches paths; required approval depends on configured protection and plan availability. Path maps must evolve when directories move.

If `/libs/auth/token` changes a Web consumer, both Security and Web expertise may be needed. Check owner coverage for new paths and for the ownership rule itself; a shared repo does not create accountability automatically.

### References

- [GitHub Docs](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/about-code-owners)

</details>

- [Back to top](#back-to-top)

---

## <a id="ownership-by-repository">Ownership by Individual Repository in a Polyrepo</a>

<details>
<summary>Click for details</summary>

In a **polyrepo**, a repository often has its own maintainers, policies and permissions: `checkout-api.git` belongs to Checkout, `identity.git` to Identity. This creates a clear operational owner, but cross-repo interface responsibility must remain explicit: providers and consumers both have work to do.

Changing Identity's schema may break Checkout even if the Identity PR has an owner. Track ownership of both repositories and the contract they share; a repository boundary does not replace consumer support commitments.

</details>

- [Back to top](#back-to-top)

---

## <a id="discoverability-and-reuse">Code Discoverability and Reuse Across Repository Topologies</a>

<details>
<summary>Click for details</summary>

**Discoverability** means finding existing code, APIs, tests and usage examples before reimplementing them. A monorepo can simplify search and cross-codebase updates; polyrepos can also provide discovery through indexing or a catalog. Reuse still requires intentional contracts rather than indiscriminate copying.

Google's research reports common-repo visibility advantages while also noting multi-repo access-control and toolchain benefits. Measure duplicate implementations and time to find API owners instead of assuming every shared folder creates good reuse.

### References

- [Jaspan et al. (2018) — survey and developer-tool-log evidence](https://research.google/pubs/advantages-and-disadvantages-of-a-monolithic-codebase/)

</details>

- [Back to top](#back-to-top)

---

## <a id="ownership-versus-access-control">Code Ownership Versus Source Access Permissions</a>

<details>
<summary>Click for details</summary>

**Ownership** asks who is accountable for reviewing and maintaining code; **access control** asks who can read, push or administer it. CODEOWNERS and path review rules route or require approvals; they do **not** hide one directory from someone with permission to clone the whole repository. Restricted source may need separate repositories and appropriate access policies.

If a vendor may read web UI but must not see fraud algorithms, placing both under one readable repo with Fraud reviews does not isolate the fraud code. That is a source-access boundary decision, not a reviewer configuration issue.

</details>

- [Back to top](#back-to-top)

---

## <a id="team-boundary-versus-repository-boundary">Team Boundaries Need Not Match Repository Boundaries</a>

<details>
<summary>Click for details</summary>

Org charts can change faster than code dependency relationships. Enforcing one repository per team can create churn during reorganizations; one team may maintain several repos for distinct security or lifecycle needs. Team and repository boundaries should align only when that alignment reduces coordination without damaging shared contracts.

If API and Mobile revise a payload every sprint despite different managers, splitting repositories will not remove schema negotiation. Specify interface reviewers before mapping repositories to team names.

</details>

- [Back to top](#back-to-top)

---

## <a id="service-count-is-not-repository-count">Service Count Does Not Determine Repository Count</a>

<details>
<summary>Click for details</summary>

**Service count** is a runtime architecture property; **repository count** is a source-governance choice. Five services can share a monorepo; a single service may depend on source maintained in two repos. Counting deployment units does not reveal cross-component PR frequency or read-access requirements.

If two services frequently co-evolve a schema, close source coordination may help. If one handles restricted data, separate access boundaries may dominate. Evaluate dependencies and access rather than automatically creating one repository per new service.

</details>

- [Back to top](#back-to-top)

---

## <a id="verify-ownership-boundaries">Evaluating Ownership Areas and Overlap on a Repository Map</a>

<details>
<summary>Click for details</summary>

Build an **ownership map** listing repo/path, primary and backup teams, consumed interfaces, required reviewers, read/write boundaries and escalation ownership. Highlight **unowned areas**, overlapping responsibility where each team expects the other to act, and overloaded reviewers asked about unrelated changes.

If Platform owns `/libs/payments` but Checkout fixes nearly every defect, clarify contract ownership before inventing a new repository. Evidence includes PRs stuck waiting for an unknown reviewer and issues repeatedly routed to the wrong team—not merely folder counts.

</details>

- [Back to top](#back-to-top)
