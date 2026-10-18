<a id="back-to-top"></a>

# Shared Code, Dependencies, and Compatibility

## Menu
- [Shared Libraries and Consumers: Concepts and Usage Relationships](#shared-library-and-consumers)
- [Dependency Boundaries Versus Repository Boundaries](#dependency-boundary-vs-repository-boundary)
- [Coordinated Library and Consumer Updates in a Monorepo](#coordinated-library-update-in-monorepo)
- [Version Management and Consumer Adoption Across Polyrepos](#version-adoption-across-polyrepos)
- [Interface Contracts, Compatibility, and Breaking Changes](#interface-contract-and-compatibility)
- [Dependency Consistency Versus Technology Autonomy](#dependency-consistency-vs-autonomy)
- [Co-Location Does Not Mandate Identical Dependency Versions](#co-location-does-not-force-versions)
- [Evidence from Shared-Library and Consumer Upgrade Scenarios](#compare-dependency-migration-evidence)

## <a id="shared-library-and-consumers">Shared Libraries and Consumers: Concepts and Usage Relationships</a>

<details>
<summary>Click for details</summary>

A **shared library** exposes reusable logic/contracts, such as `auth-client`. A **consumer** is a module/application depending on that API. A library change can affect many consumers regardless of repository boundaries. Topology changes how easily relationships are visible; it does not by itself define version semantics.

Removing a parameter from `AuthClient.verify(token)` may require web and API changes. Inventory consumers, check compilation/contracts and assign migration ownership before publishing the change.

</details>

- [Back to top](#back-to-top)

---

## <a id="dependency-boundary-vs-repository-boundary">Dependency Boundaries Versus Repository Boundaries</a>

<details>
<summary>Click for details</summary>

A **dependency boundary** exists where module A consumes B's API; a **repository boundary** exists where their source history is managed. Two modules in one monorepo still need to avoid cyclic dependencies; separate repositories can be so tightly coupled that every edit requires coordination. File proximity is not contract design.

If `checkout` depends on `pricing` inside one repo, its public interface still needs stability. Splitting repositories does not eliminate coupling; inspect the actual dependency graph first.

</details>

- [Back to top](#back-to-top)

---

## <a id="coordinated-library-update-in-monorepo">Coordinated Library and Consumer Updates in a Monorepo</a>

<details>
<summary>Click for details</summary>

A monorepo allows `libs/auth-client`, `apps/web` and `services/api` to change together in one PR. Reviewers see consumer migration and affected tests before accepting the change; Google's research identifies coordinated API updates as a practical common-repo benefit. Unvalidated consumers still remain a risk.

For a method rename, update call sites and test each affected consumer. Touching all expected paths in one diff is not proof of runtime compatibility.

### References

- [Jaspan et al. (2018) — survey and developer-tool-log evidence](https://research.google/pubs/advantages-and-disadvantages-of-a-monolithic-codebase/)

</details>

- [Back to top](#back-to-top)

---

## <a id="version-adoption-across-polyrepos">Version Management and Consumer Adoption Across Polyrepos</a>

<details>
<summary>Click for details</summary>

In polyrepos, a library typically publishes a **version** that consumers later declare as a dependency. The provider might release `auth-client 2.1` while Web stays on `1.9` and API adopts `2.1`. Consumer adoption can be independent, so compatibility promises and support windows matter.

Track consumer → current version → owner → upgrade plan → validation. A successful library publication does not mean deployed applications automatically consume it.

</details>

- [Back to top](#back-to-top)

---

## <a id="interface-contract-and-compatibility">Interface Contracts, Compatibility, and Breaking Changes</a>

<details>
<summary>Click for details</summary>

An **interface contract** includes externally relied-on behavior and data—not just method signatures: JSON fields, error semantics, latency expectations and version support. A **breaking change** invalidates previously supported consumers. A monorepo may catch it in shared checks but external consumers can still break.

Removing a `currency` field may break old clients; prefer adding the new representation, migrating consumers and retiring the old one only after adoption evidence. Contract tests matter more than the repository name.

</details>

- [Back to top](#back-to-top)

---

## <a id="dependency-consistency-vs-autonomy">Dependency Consistency Versus Technology Autonomy</a>

<details>
<summary>Click for details</summary>

Monorepos can make shared dependency versions easier to **discover and coordinate**; polyrepos can give teams more room for different **languages, build tools and upgrade schedules**. Either topology can enforce or relax dependency governance: code placement does not magically standardize technology.

A Java team on JDK 21 and a Go team can share one repo with different toolchains; separate repos cannot prevent API mismatches. Score tooling autonomy and dependency management based on actual teams, not labels.

### References

- [Jaspan et al. (2018) — survey and developer-tool-log evidence](https://research.google/pubs/advantages-and-disadvantages-of-a-monolithic-codebase/)

</details>

- [Back to top](#back-to-top)

---

## <a id="co-location-does-not-force-versions">Co-Location Does Not Mandate Identical Dependency Versions</a>

<details>
<summary>Click for details</summary>

Two modules in one Git repository can declare **different dependency versions** when their builds and compatibility policies permit. Conversely, separate repositories can be required by policy to use the same framework baseline. Topology does not dictate lockfiles, workspace resolution or version policy.

Web may use UI library v4 while Admin uses v3 within one repo, provided both are validated and the old version has an upgrade plan. “Monorepo always means single-version” is not a universal rule.

</details>

- [Back to top](#back-to-top)

---

## <a id="compare-dependency-migration-evidence">Evidence from Shared-Library and Consumer Upgrade Scenarios</a>

<details>
<summary>Click for details</summary>

Evaluate an `auth-client 1.x → 2.x` migration by listing impacted consumers, their PRs/commits, deployed dependency versions, contract-test results and incidents. A monorepo can coordinate validation in one review; polyrepos often need monitoring across repositories and releases.

Success means **consumers actually adopted supported versions** with acceptable compatibility and lead time—not merely that a library PR merged. For rollback, know which consumers require the newer API and which compatibility windows remain.

</details>

- [Back to top](#back-to-top)
