<a id="back-to-top"></a>

# A Governed Bitbucket Team Workflow

## Menu
- [From Repository Access to an Accepted Change](#end-to-end-contribution-flow)
- [Pull Request Merge Readiness: Required Conditions](#review-tasks-and-policy-readiness)
- [Diagnosing a Stalled Pull Request](#diagnosing-a-stalled-pull-request)
- [Evidence of a Reviewed and Accepted Source Change](#evidence-of-accepted-source-change)
- [Handoffs to Git, Branch Strategy, and Bitbucket Pipelines](#handoff-to-git-strategy-and-pipelines)

## <a id="end-to-end-contribution-flow">From Repository Access to an Accepted Change</a>

<details>
<summary>Click for details</summary>

A reliable Bitbucket workflow connects the earlier decisions into an inspectable journey: **appropriate repository access → a Git source change → a PR targeting the right destination → reviewers → tasks and checks → authorized merge → updated shared source**. This is a collaboration sequence; Git commit and merge mechanics are taught in the Git module.

In Orchid, An addresses Jira item `BILL-142`, Binh reviews, and Mai maintains the rules for `invoice-api/main`. An describes the new 10% rule with before/after invoice examples. Binh examines rounding consequences and requests a negative-amount case. Only when current permissions and policy allow it does the team merge the PR. A closed PR does not by itself prove software deployment.

**Synthesis exercise:** draw six milestones for `BILL-142` and assign an observable piece of evidence to each. If no evidence exists for a step, do not claim that step is complete.

</details>

- [Back to top](#back-to-top)

---

## <a id="review-tasks-and-policy-readiness">Pull Request Merge Readiness: Required Conditions</a>

<details>
<summary>Click for details</summary>

A PR is **ready to merge** when the project's actual conditions are met, not merely when the author clicks a button or one approval icon appears. Mai should confirm the correct destination, the acting user's permission, required reviewer approvals, task resolution, Changes requested state, and which checks are blocking or advising under the current configuration.

Keep two modes distinct: **Free/Standard** can report unresolved checks as warnings while still permitting an authorized merge; **Premium with prevention configured** can block merges with unresolved applicable checks. Branch restrictions separately govern who may act. Orchid can maintain a human policy not to bypass warnings even if the platform would allow it.

**Checklist practice:** for An's PR, make columns for *condition, evidence, owner, advisory-or-blocking*. Fill in the open negative-invoice task, Binh's approval, and Mai's merge permission. Do not call the PR ready before inspecting all relevant conditions.

### References
- [Atlassian — Suggest or require checks before a merge](https://support.atlassian.com/bitbucket-cloud/docs/suggest-or-require-checks-before-a-merge/)

</details>

- [Back to top](#back-to-top)

---

## <a id="diagnosing-a-stalled-pull-request">Diagnosing a Stalled Pull Request</a>

<details>
<summary>Click for details</summary>

When a PR stalls, avoid assuming “Bitbucket is broken.” First capture **the actor, source/destination, PR status, exact message, and time**. Then classify possibilities: insufficient repository/project access, a branch restriction denying the operation, pending reviewer feedback, open tasks, unresolved merge checks, or confusion between warnings and blocking.

An can read the PR but cannot merge `main`, even though Binh approved. Mai checks whether a branch restriction allows An to merge before expanding general access. If permissions are sufficient, she investigates tasks and Premium enforcement settings. Disabling every rule at once would destroy useful diagnostic evidence.

**Diagnostic exercise:** draw a decision tree beginning “Was the action denied, or was it only warned about?” At each branch name one observed fact and one relevant permission/check setting. Finish with the **smallest justified correction**, not blanket Admin access.

</details>

- [Back to top](#back-to-top)

---

## <a id="evidence-of-accepted-source-change">Evidence of a Reviewed and Accepted Source Change</a>

<details>
<summary>Click for details</summary>

Proving that a correction was reviewed and accepted takes more than a “done” message. Orchid's evidence includes **Jira `BILL-142`** for the business rationale, the **PR** for source/destination and changed lines, **comments and tasks** for rounding feedback, **approvals/check statuses** under team policy, and the **destination state** after the merge.

Every artifact has limits: Jira Done does not independently prove the source merged; one approval does not prove all tasks closed; a merged PR does not establish that a pipeline ran or a deployment succeeded. Together, inspectable records help the team determine which change entered shared source and on what basis.

**Exercise:** write a six-item acceptance checklist—*requirement, proposed diff, reviewer, task resolution, conditions, accepted state*. Label whether evidence comes from Jira, the Bitbucket PR, or the repository; avoid relying on an uncontextualized screenshot.

</details>

- [Back to top](#back-to-top)

---

## <a id="handoff-to-git-strategy-and-pipelines">Handoffs to Git, Branch Strategy, and Bitbucket Pipelines</a>

<details>
<summary>Click for details</summary>

The Bitbucket Cloud module ends at **collaboration and governance of source acceptance**. For `commit`, `fetch`, `merge`, and distributed history internals, study **Git**. To decide whether the team should use short-lived branches, GitHub Flow/Trunk-Based Development, or a monorepo/polyrepo topology, study **Branching Strategy** and **Monorepo/Polyrepo**. Bitbucket can apply controls, but it does not choose the team's optimal strategy.

**Bitbucket Pipelines** provides build/check automation integrated with Bitbucket. A reviewer may see **reported build/check results** in a PR, but authoring pipelines, running jobs, and delivering software belong to **CI/CD/delivery automation**, not this lesson. Jira tracks work and Confluence documents knowledge; neither replaces Git history.

**Final practice:** retell An's tax-change journey through PR merge, then answer separately *what did Git record, what did Bitbucket govern, and what verification or delivery work remains?* Those answers demonstrate the module's proper boundary.

</details>

- [Back to top](#back-to-top)
