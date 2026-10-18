<a id="back-to-top"></a>

# Change Coupling and Release Independence

## Menu
- [Change Coupling Versus Release Coupling](#change-coupling-vs-release-coupling)
- [Benefits and Conditions of Independent Releases](#why-independent-release-matters)
- [Independent Component Releases Are Possible in a Monorepo](#monorepo-can-release-independently)
- [Release Coupling Can Persist Across Polyrepos](#polyrepo-can-be-release-coupled)
- [Release Scheduling Risks from Interface or Shared-Library Changes](#shared-interface-upgrade-release-risks)
- [Validation Boundaries and Release Readiness Criteria](#validation-boundaries-and-readiness)
- [Differing Release Cadences and Coordination Costs](#release-cadence-and-team-coordination)
- [Case Study: Coordinated Versus Independent Releases](#analyze-release-independence-scenario)

## <a id="change-coupling-vs-release-coupling">Change Coupling Versus Release Coupling</a>

<details>
<summary>Click for details</summary>

**Change coupling** means a requirement often demands edits across components; **release coupling** means those components must be delivered together or in a strict order. These are related but distinct. Monorepo changes may land at one commit while services release separately; polyrepo changes may be recorded separately yet still require coordinated deployment.

If API and Web both change a schema but API supports old/new forms temporarily, Web can release later. Measure cross-component edits and forced synchronized deployments separately.

</details>

- [Back to top](#back-to-top)

---

## <a id="why-independent-release-matters">Benefits and Conditions of Independent Releases</a>

<details>
<summary>Click for details</summary>

**Independent release** lets one component ship a fix without waiting for every other component. It requires stable contracts, component-level readiness and independent deployment/monitoring/rollback capabilities. Separate repositories alone provide none of these.

A UI hotfix ideally deploys without redeploying Payment. If it requires an unreleased API change, independence is nominal; contract compatibility must be addressed first.

</details>

- [Back to top](#back-to-top)

---

## <a id="monorepo-can-release-independently">Independent Component Releases Are Possible in a Monorepo</a>

<details>
<summary>Click for details</summary>

A monorepo provides **one source history**, not necessarily **one artifact or release button**. With separate build/deploy boundaries, Catalog and Payments can release at different times even when sourced from a shared commit history. Each component needs traceable source revision and validation.

Evidence is component-specific release records, source revision and tests—not the existence of a shared PR. Implementing affected builds or deployment pipelines belongs to CI/build ownership; here we state the strategic requirement.

</details>

- [Back to top](#back-to-top)

---

## <a id="polyrepo-can-be-release-coupled">Release Coupling Can Persist Across Polyrepos</a>

<details>
<summary>Click for details</summary>

Polyrepos provide **separate histories and access**, not independent runtime contracts. If `orders-api.git` introduces a required field and `orders-web.git` cannot tolerate the previous form, teams still coordinate deployment order or introduce compatibility. One repository per microservice does not guarantee **real release autonomy**.

Track coupled releases, contract-related errors and cascade rollbacks. If interface design causes the coupling, adding more repositories merely adds coordination points.

</details>

- [Back to top](#back-to-top)

---

## <a id="shared-interface-upgrade-release-risks">Release Scheduling Risks from Interface or Shared-Library Changes</a>

<details>
<summary>Click for details</summary>

Shared-interface changes risk **release skew**: a provider reaches a new version while consumers still use old assumptions, or consumers deploy before required provider behavior exists. Beyond compilation, consider schemas, feature flags, compatibility windows and rollback combinations.

If payment library v3 removes a method before API consumers adopt its replacement, rollout breaks. Plan provider expansion → consumer migration → contract retirement, supported by adoption evidence.

</details>

- [Back to top](#back-to-top)

---

## <a id="validation-boundaries-and-readiness">Validation Boundaries and Release Readiness Criteria</a>

<details>
<summary>Click for details</summary>

A **validation boundary** identifies which components need verification after a dependency changes; **release readiness** considers tests, contracts, review and residual risks. Monorepos can identify affected components, while polyrepos may validate versions and cross-repository contracts. Specific build commands belong elsewhere.

For a shared DTO change, record which API/Web consumers require tests, whether cross-version contracts pass, and who approves release. A green library-only build is insufficient for a full migration.

</details>

- [Back to top](#back-to-top)

---

## <a id="release-cadence-and-team-coordination">Differing Release Cadences and Coordination Costs</a>

<details>
<summary>Click for details</summary>

Teams have differing **release cadences**: mobile apps may ship slowly, APIs daily, and shared libraries monthly. Topology changes source coordination, while sustainable asynchronous releases require stable contracts and deprecation communication. Forcing one cadence because code shares a repo creates bottlenecks; splitting repos does not shield clients from breaking APIs.

Track supported version combinations and cross-team waiting time. Improve contracts before changing topology solely because schedules differ.

</details>

- [Back to top](#back-to-top)

---

## <a id="analyze-release-independence-scenario">Case Study: Coordinated Versus Independent Releases</a>

<details>
<summary>Click for details</summary>

Compare an API/Web tax-rule change. **Case A:** API adds a field while keeping the old one; Web migrates next week, enabling separate releases even in a monorepo. **Case B:** API immediately removes the old field; Web must deploy with it despite two polyrepos. The key factor is **compatibility**, not `.git` placement.

Compare each component's source revision, deployment time, supported client/server combinations and errors. Repository count alone is a poor measure of release autonomy.

</details>

- [Back to top](#back-to-top)
