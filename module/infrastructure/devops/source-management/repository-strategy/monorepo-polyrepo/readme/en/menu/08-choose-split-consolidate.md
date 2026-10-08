<a id="back-to-top"></a>

# Choose, Split, and Consolidate Repositories with Evidence

## Menu
- [Repository Topology Selection Criteria](#topology-decision-criteria)
- [Cross-Team Change Frequency in Topology Decisions](#cross-team-change-frequency)
- [Ownership, Access, Dependencies, and Release Decision Matrix](#ownership-access-dependency-release-matrix)
- [Conditions and Benefits of Repository Consolidation](#when-to-consolidate-repositories)
- [Conditions and Constraints for Splitting a Repository](#when-to-split-repository)
- [Migration Costs for History, Permissions, and Dependencies](#migration-costs-and-risk)
- [Topology Transition Plans and Observable Outcomes](#transition-plan-and-observable-outcomes)
- [Evidence from Large-Scale Monorepo Research](#evaluate-monorepo-case-evidence)
- [Contrasting Cases Supporting Different Repository Topologies](#compare-balanced-case-scenarios)
- [Ownership Handoffs to Build/CI, Git, and Service Architecture](#handoff-to-neighboring-owners)

## <a id="topology-decision-criteria">Repository Topology Selection Criteria</a>

<details>
<summary>Click for details</summary>

Score topology by **cross-component change frequency**, interface coupling, ownership, read-access constraints, release cadence, tooling maturity and migration cost. Separate hard constraints from preferences: mandatory source isolation cannot be offset by convenience points for a monorepo.

Checkout and Web co-evolving an API weekly with shared access and good validation might suit a monorepo. Partner-restricted Payments source needs an explicit isolation boundary even if cross-team work increases.

</details>

- [Back to top](#back-to-top)

---

## <a id="cross-team-change-frequency">Cross-Team Change Frequency in Topology Decisions</a>

<details>
<summary>Click for details</summary>

How often one requirement changes several areas is more informative than raw commit count. If most features touch API, Web and Contract, three repositories may create repeated coordination. If teams change their shared interface only a few times a year, polyrepos may be reasonable.

Sample issues and PRs across several iterations: count repositories per requirement, lead time from first to last integration and rework due to dependencies. Do not treat mass formatting commits as business coupling.

</details>

- [Back to top](#back-to-top)

---

## <a id="ownership-access-dependency-release-matrix">Ownership, Access, Dependencies, and Release Decision Matrix</a>

<details>
<summary>Click for details</summary>

Build a **decision matrix** comparing co-location versus separation across ownership, read permissions, co-change frequency, dependency versions, release cadence, tooling feedback and compliance. Populate each cell with evidence. **Hard constraints** such as source isolation eliminate infeasible options before weighted preferences are scored.

A shared Checkout+Web repo may improve API reviews while Fraud must remain separate. That intentional **hybrid topology** can be better than enforcing one extreme across an organization.

</details>

- [Back to top](#back-to-top)

---

## <a id="when-to-consolidate-repositories">Conditions and Benefits of Repository Consolidation</a>

<details>
<summary>Click for details</summary>

Consolidation makes sense when repositories **frequently co-change**, have compatible read boundaries, hide consumer relationships or duplicate enough governance work to outweigh shared-repo tooling costs. Do not merge solely because teams share a manager; plan path ownership and history/tooling capacity first.

If Web and API Contract PRs almost always travel together with shared reviewers, consolidation may improve migration visibility. Define success as reduced cross-repository lead time—not fewer repository cards.

</details>

- [Back to top](#back-to-top)

---

## <a id="when-to-split-repository">Conditions and Constraints for Splitting a Repository</a>

<details>
<summary>Click for details</summary>

Splitting is justified when **read access/compliance must differ**, ownership and lifecycle genuinely diverge, or shared governance/tooling conflicts cannot be reasonably solved within one repository. Splitting solely because clone/test is slow can fail when binaries or orchestration are the real issue; investigate optimization first.

If Fraud is restricted to Security but Web is shared with vendors, isolation is compelling. If API/Web still heavily consume shared libraries, plan versioned contracts after separation.

</details>

- [Back to top](#back-to-top)

---

## <a id="migration-costs-and-risk">Migration Costs for History, Permissions, and Dependencies</a>

<details>
<summary>Click for details</summary>

Topology changes are **history and dependency migrations**, not mere directory moves. Assess commit/tag/branch references, issue/PR links, permissions/review rules, package identities, build assumptions, owners and coexistence windows. Careless history splitting can break discoverability of earlier commits.

Inventory dependencies and integrations, preserve references/backups, rehearse in a separate environment and define rollback. Actual commands for filtering or moving Git history belong to Git specialists rather than this strategy lesson.

</details>

- [Back to top](#back-to-top)

---

## <a id="transition-plan-and-observable-outcomes">Topology Transition Plans and Observable Outcomes</a>

<details>
<summary>Click for details</summary>

A transition plan needs **baseline measures**, success criteria, accountable owners, migration order, affected consumers, overlap periods and stop/rollback rules. Pilot with a bounded component set before changing the whole organization. An unmeasured topology migration can become an expensive repository-renaming exercise.

For an API+Web consolidation pilot, track PR lead time, contract defects, test feedback and developer experience. If coordination improves but test latency triples, invest in tooling or reconsider the choice.

</details>

- [Back to top](#back-to-top)

---

## <a id="evaluate-monorepo-case-evidence">Evidence from Large-Scale Monorepo Research</a>

<details>
<summary>Click for details</summary>

**Potvin and Levenberg (2016)** describe Google's very large **common source of truth**, made feasible by a **custom-built source-control system and specialized tooling**. This is repository-topology evidence, **not proof that ordinary Git automatically scales to Google's size**. The **Jaspan et al. (2018)** comparison provides **different evidence**: engineers who had experienced both monolithic and multi-repository systems were surveyed, with developer-tool logs used as corroboration. Respondents highlighted monorepo API discoverability, reuse and consumer migrations, alongside multi-repo access-control, stability and toolchain flexibility. These are **context-specific trade-offs**, not a universal winner.

When applying the case, state organizational scale, tooling capacity and assumptions. A twelve-person team should not copy Google's infrastructure merely because Google uses a monorepo.

### References

- [Potvin and Levenberg (2016) — Google's custom monolithic repository](https://research.google/pubs/why-google-stores-billions-of-lines-of-code-in-a-single-repository/)
- [Jaspan et al. (2018) — Comparative monorepo/multi-repo evidence](https://research.google/pubs/advantages-and-disadvantages-of-a-monolithic-codebase/)

</details>

- [Back to top](#back-to-top)

---

## <a id="compare-balanced-case-scenarios">Contrasting Cases Supporting Different Repository Topologies</a>

<details>
<summary>Click for details</summary>

**Case A:** a startup with API, Web and shared contracts, one access group, frequent cross-component changes and fast validation may benefit from a monorepo. **Case B:** a regulated firm with restricted Payment code, vendors and distinct release schedules may need polyrepos or a hybrid despite extra contract coordination. Both choices can be justified by different constraints.

Score access, ownership, coupling, release, tooling and migration cost. State **what evidence or changed assumption would reverse the decision** rather than repeating “monorepo for startups, polyrepo for enterprises.”

</details>

- [Back to top](#back-to-top)

---

## <a id="handoff-to-neighboring-owners">Ownership Handoffs to Build/CI, Git, and Service Architecture</a>

<details>
<summary>Click for details</summary>

This module decides **source boundaries** using ownership, dependencies, access and coordination; it does not implement tools. **Git** owns history/refs/migration mechanics; **GitHub/GitLab/Bitbucket/Azure DevOps** own hosted access/review workflows; **build/CI** owns affected builds, caches and pipelines; **service architecture** owns APIs and deployment boundaries.

Choosing one API+Web repo creates a requirement for fast dependent validation—not a Gradle task or pipeline YAML implementation here. Hand off goals, accountable owners and outcome metrics to the relevant specialists.

</details>

- [Back to top](#back-to-top)
