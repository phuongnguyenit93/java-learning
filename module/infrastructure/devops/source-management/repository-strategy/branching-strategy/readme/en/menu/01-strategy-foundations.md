<a id="back-to-top"></a>

# Branching and Integration Strategy Foundations

## Menu
- [Branching Strategy: Purpose and Team Integration Responsibilities](#why-branching-strategy)
- [Git Branch Operations Versus Team Integration Policy](#git-mechanics-vs-team-policy)
- [Parallel Changes, Late Integration, and Conflict Risk](#parallel-work-integration-risk)
- [Mainline, Work, and Release Branches: Roles and Boundaries](#mainline-work-release-vocabulary)
- [Integrated Code vs Releasable Code](#integrated-vs-releasable)
- [Team Strategy Decisions and Git, Platform, and CI Boundaries](#strategy-decisions-and-handoffs)

## <a id="why-branching-strategy">Branching Strategy: Purpose and Team Integration Responsibilities</a>

<details>
<summary>Click for details</summary>

A **branching strategy** is the team's agreement about where changes go, when they are integrated, and who keeps shared source reliable. Git provides branches; it does not choose whether a feature needs its own branch or how long release lines remain supported. Without a shared policy, developers may work for weeks and discover irreconcilable assumptions just before a release.

Follow a six-person payments team through this module: a rounding defect needs an urgent production fix, refunds take several weeks to develop, and customers still run version 1.4. A useful strategy explains the route for all three kinds of work—not just branch names.

**Learning route:** start with branch roles, divergence and integration cadence. Compare Trunk-Based Development, GitHub Flow and classic Git Flow; then choose merge/squash/rebase history policy, release and hotfix propagation, review/mainline governance and evidence-based diagnostics. Git command mechanics and platform-specific PR/CI configuration are covered in their own modules.

**Evidence exercise:** sketch idea → proposed change → review → shared branch → release. Mark each decision owner and the waiting time between steps.

### References
- [Atlassian — Comparing Git workflows](https://www.atlassian.com/git/tutorials/comparing-workflows)

</details>

- [Back to top](#back-to-top)

---

## <a id="git-mechanics-vs-team-policy">Git Branch Operations Versus Team Integration Policy</a>

<details>
<summary>Click for details</summary>

Git stores commits and branch references and supplies merge, rebase, and cherry-pick operations. **Team policy** decides which branch receives work, who reviews it, maximum lifetime, and emergency procedures. A **hosting platform** can enforce parts of that policy through permissions and rules. These layers differ: a technically successful merge is not evidence that updating `main` was authorized.

An might know `git merge` but overlook a two-review policy. Conversely, a mandatory GitHub PR does not by itself select Trunk-Based Development or Git Flow.

**Evidence exercise:** for one proposed change, record the Git integration result, the hosted review decision, and the governing team rule separately. This is a brief cross-module bridge to Git mechanics and collaboration platforms, not a Git command lesson.

</details>

- [Back to top](#back-to-top)

---

## <a id="parallel-work-integration-risk">Parallel Changes, Late Integration, and Conflict Risk</a>

<details>
<summary>Click for details</summary>

Parallel developers can diverge in interface assumptions, data contracts, and deployment order. **Divergence** means their histories evolve separately; a textual **merge conflict** is only its most obvious symptom. Two branches can merge cleanly yet break the system—for example, one changes currency representation while another continues floating-point calculations.

If An and Binh edit the same API independently for twelve days, integration forces them to reconcile both code and stale design assumptions. Integrating daily does not eliminate defects but shortens feedback delay while context is fresh.

**Useful evidence:** branch age, updates from `main`, changed-file size, conflicts, and PR-to-merge lead time. One conflict is not proof a strategy failed; evaluate repeated patterns and business impact.

</details>

- [Back to top](#back-to-top)

---

## <a id="mainline-work-release-vocabulary">Mainline, Work, and Release Branches: Roles and Boundaries</a>

<details>
<summary>Click for details</summary>

The **mainline/trunk** is the central integration stream, often named `main`; a **work/feature branch** carries unaccepted work; a **release branch** supports stabilizing or maintaining a version. Names are conventions: Git Flow's `develop` integrates features while historical `master` tracks production releases. Having a branch named main does not establish which strategy is operating.

The payment team might keep validated changes on `main`, propose refunds on `feature/refunds`, and support previous customers on `release/1.4`. Ask each branch's **purpose and integration timing**, not only its prefix.

**Exercise:** record branch, owner, admissible changes, expected lifetime, and deletion condition. Reuse this table when comparing three workflows.

</details>

- [Back to top](#back-to-top)

---

## <a id="integrated-vs-releasable">Integrated Code vs Releasable Code</a>

<details>
<summary>Click for details</summary>

**Integrated** means a change entered shared history; **releasable** means that revision meets the team's quality criteria for release; **deployed** means a particular artifact reached an environment. They are different states. A refund implementation can be integrated but hidden by a feature flag; `release/1.4` may be stable yet not deployed.

Suppose the rounding fix merged into `main` this morning while acceptance checks are still pending. Report "integrated, validation pending," not "shipped." Distinguish commit/PR evidence, quality results, release tags/builds, and deployment records.

**Boundary:** this module designs the flow of changes. Build/test/CI configuration and artifact delivery belong to delivery modules; here we interpret the relevant signals only.

</details>

- [Back to top](#back-to-top)

---

## <a id="strategy-decisions-and-handoffs">Team Strategy Decisions and Git, Platform, and CI Boundaries</a>

<details>
<summary>Click for details</summary>

A team strategy answers who chooses contribution paths, which branches hold development and releases, how reviews work, which gates precede merging, where hotfixes land, and when work counts as shipped. These are **governance decisions**, not Git syntax or a vendor's settings page.

If main repeatedly breaks, someone must own restoration and prioritize recovery over new features. If customers need 1.4 support, release owners must know which maintenance line receives a backport and who authorizes it.

**Evidence of adoption:** write a one-page policy stating goals, change flow, branch lifespan, review, release/hotfix path, and escalation ownership. Later chapters choose the rules; Git, hosting platforms, and CI implement their corresponding mechanisms.

</details>

- [Back to top](#back-to-top)
