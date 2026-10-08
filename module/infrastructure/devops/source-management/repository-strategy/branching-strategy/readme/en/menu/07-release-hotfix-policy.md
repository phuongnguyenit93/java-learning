<a id="back-to-top"></a>

# Release and Hotfix Branch Policy

## Menu
- [Release from Mainline vs a Dedicated Stabilization Branch](#release-from-main-vs-branch)
- [Release Branch Lifecycle: Creation, Maintenance, and Retirement](#release-branch-lifecycle)
- [Hotfix Targets and Propagation Across Development Lines](#hotfix-targets-and-propagation)
- [Backport and Forward-Port: Propagating Fixes Across Required Code Lines](#backport-forwardport-policy)
- [Long-Lived Release Branch Costs and Multi-Version Support](#release-branch-divergence-cost)

## <a id="release-from-main-vs-branch">Release from Mainline vs a Dedicated Stabilization Branch</a>

<details>
<summary>Click for details</summary>

Teams may **release directly from mainline** when validated revisions are routinely deployable, or **cut a stabilization branch** when scheduled releases require a fixed change set. Both can be sensible; selection depends on delivery cadence, qualification needs, customer expectations, and fix-forward capability. Not every release needs a `release/*` branch.

A payment website delivering continuously may tag a revision from main; installed client software may stabilize v1.4 while main advances toward v1.5. The branch separates those timelines but introduces two lines to coordinate.

**Evidence:** record branch cut versus publication date, fixes unique to the release line, and the exact commit behind each version.

### References
- [Trunk Based Development — Release from trunk](https://trunkbaseddevelopment.com/release-from-trunk/)

</details>

- [Back to top](#back-to-top)

---

## <a id="release-branch-lifecycle">Release Branch Lifecycle: Creation, Maintenance, and Retirement</a>

<details>
<summary>Click for details</summary>

A release branch needs a defined lifecycle: select a **qualified base commit**, create it when stabilization becomes necessary, limit changes to fixes and version-related work, validate the release, and retire the branch after support ends. If it keeps accepting unrelated new features, it becomes a parallel development line with integration debt.

For example, `release/1.4` is cut from a qualified revision, receives only two stabilization patches, and is tagged v1.4.0 while main continues refunds work. The release owner retires the maintenance line only when supported customers have moved on.

**Checklist:** base commit, owner, allowed changes, release tag, propagation of fixes, and retirement review date. Do not delete a branch while supported installations still depend on it.

</details>

- [Back to top](#back-to-top)

---

## <a id="hotfix-targets-and-propagation">Hotfix Targets and Propagation Across Development Lines</a>

<details>
<summary>Click for details</summary>

A **hotfix** is an urgent correction to already released software. The team must identify **the history corresponding to the failing version**, the patch-release destination, and every development line that must receive the correction. In classic Git Flow, the hotfix originates on production history and flows back into production and develop; multi-version support needs additional explicit destinations.

Rounding fails on v1.4 while main has changed currency representation for v1.5. Fixing only main cannot repair deployed v1.4. **For Trunk-Based Development**, favor reproducing, fixing, and verifying the bug on trunk first; then selectively carry the necessary patch to the v1.4 release branch and verify it there. If the bug cannot be reproduced on trunk, a release-first fix is an exception that requires an explicit plan to prevent the defect from surviving on main. **In classic Git Flow**, production hotfixes normally start from production history and propagate to the production and develop lines. Either path requires compatibility checks because v1.4 and main may implement the behavior differently.

**Evidence:** patch tag, initiating ticket, each target line, and propagation state. One fixed branch is not grounds to close a multi-version incident.

### References
- [Trunk-Based Development — Branch for release](https://trunkbaseddevelopment.com/branch-for-release/)
- [Vincent Driessen — A successful Git branching model](https://nvie.com/posts/a-successful-git-branching-model/)

</details>

- [Back to top](#back-to-top)

---

## <a id="backport-forwardport-policy">Backport and Forward-Port: Propagating Fixes Across Required Code Lines</a>

<details>
<summary>Click for details</summary>

A **backport** carries a fix from newer code into an older supported line; a **forward-port** ensures a correction made on an older line also exists in newer development. The business intent may match even when the implementations differ because interfaces and schemas evolved. Cherry-pick is **one possible Git mechanism**, not a promise that patches apply or pass review automatically.

A security fix developed on main must be backported to `release/1.4`; that version uses a different authentication API and needs an adapted patch with its own validation. Likewise, a v1.4-only emergency fix needs forward-porting to avoid recurrence in v1.6.

**Evidence matrix:** defect × supported line, with affected status, patch PR, test result, release, and owner.

</details>

- [Back to top](#back-to-top)

---

## <a id="release-branch-divergence-cost">Long-Lived Release Branch Costs and Multi-Version Support</a>

<details>
<summary>Click for details</summary>

Each active release branch is a **maintenance promise**: triage issues, select valid patches, retest, publish, and propagate corrections across lines. Supporting more versions multiplies review, backport, testing, and security effort even though creating a Git branch is cheap. Retaining old release branches indefinitely "just in case" is not free.

The payments team supports 1.2, 1.3, 1.4, and main; every vulnerability requires four impact decisions and possibly several different fixes. A single release owner may become the bottleneck.

**Decision:** define support end dates, retirement criteria, security patch obligations, and alerts for missing patches. Count actively maintained lines rather than merely remote branch names.

</details>

- [Back to top](#back-to-top)
