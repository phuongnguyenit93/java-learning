<a id="back-to-top"></a>

# Governed Source Change Lifecycle

## Menu
- [From Individual Change to Shared Source](#individual-to-shared-change-flow)
- [Recorded, Shared, Reviewed, and Accepted](#recorded-shared-reviewed-accepted)
- [Scenario: A Shared Change That the Team Has Not Yet Accepted](#shared-but-not-accepted-scenario)
- [Synchronization and Review Failure Modes](#source-collaboration-failure-modes)
- [Evidence of a Governed Source Change](#evidence-of-a-governed-change)
- [From Accepted Source to Delivery Automation](#source-to-delivery-boundary)

## <a id="individual-to-shared-change-flow">From Individual Change to Shared Source</a>

<details>
<summary>Click for details</summary>

The previous chapters combine into one logical journey: **edit working files → record a revision → share a proposal → review the changes → accept a state into shared source**. These are conceptual stages: a distributed system such as Git can record revisions in a local repository while offline, whereas recording a revision into shared history in a centralized system such as SVN normally requires communication with its server. Organizations may implement the stages with different tools and checks.

In the `Invoice.java` story, An changes the tax rule, records the legal rationale, shares the proposal, asks Binh to examine rounding consequences, revises the change as needed, and only then reaches the team's accepted source. No step automatically proves the next one occurred: saving is not recording, publishing is not review, and review is not acceptance.

**Practice:** draw a five-stage diagram labeled *working file*, *history*, *proposal*, *feedback*, and *shared state*. For each stage, identify evidence a newcomer could use to tell where the change stands.

</details>

- [Back to top](#back-to-top)

---

## <a id="recorded-shared-reviewed-accepted">Recorded, Shared, Reviewed, and Accepted</a>

<details>
<summary>Click for details</summary>

Four labels are often confused but have different conditions. **Recorded:** a revision exists in version history. **Shared:** others can access the proposed change. **Reviewed:** an inspection and feedback or decision process occurred. **Accepted:** the change entered the agreed shared state under team rules.

The common progression is forward, but not every proposal is accepted. Review may request rework or rejection. 'Tests passed' is technical evidence, not automatically the same as approval; many teams require an explicit decision.

**Checklist:** when someone says 'the fix is up', ask whether it is saved locally, published remotely, reviewed, or included in accepted shared history. **Practice:** label each point in the An–Binh example and state one way to prove each label.

</details>

- [Back to top](#back-to-top)

---

## <a id="shared-but-not-accepted-scenario">Scenario: A Shared Change That the Team Has Not Yet Accepted</a>

<details>
<summary>Click for details</summary>

An records the 10% tax correction and shares a proposal. Binh inspects its difference and notices that the updated rounding rule can change some totals by one cent. Binh requests an additional calculation example for an invoice with fractional values. An's work is **shared**, may be **under review**, but is **not accepted**.

Another developer downloading An's proposal for experiments does not turn it into the official state. An must supply evidence or revise the fix; authorized acceptance follows once conditions are met. A rejected proposal may still remain in individual history.

**Scenario practice:** a manager asks 'Is the fix done?' Answer precisely using the recorded/shared/reviewed/accepted distinctions and explain why an uploaded file is not evidence of completed integration.

</details>

- [Back to top](#back-to-top)

---

## <a id="source-collaboration-failure-modes">Synchronization and Review Failure Modes</a>

<details>
<summary>Click for details</summary>

Even a well-equipped workflow can fail when participants confuse states. **Synchronization mistaken for approval:** an author sees work on a remote and assumes acceptance. **Stale starting state:** Binh edits a revision that lacks An's tax change. **Unclear authority:** two repositories are both called 'official'. **Context-free review:** reviewers see changed lines but not the business requirement.

Countermeasures include naming lifecycle stages, checking the starting baseline, designating the accepted source location, and requiring rationale with evidence. These are collaboration principles, not substitutes for vendor-specific permission or check configuration.

**Observation:** if invoice totals regress after integration, identify the accepted revision, relevant difference, reviewer feedback, and verification performed. **Practice:** match each failure mode to one concrete diagnostic question.

</details>

- [Back to top](#back-to-top)

---

## <a id="evidence-of-a-governed-change">Evidence of a Governed Source Change</a>

<details>
<summary>Click for details</summary>

A governed change needs **inspectable evidence**, not just 'done'. Look for a starting revision, the difference introduced, its rationale, the proposer, review outcomes, and the resulting accepted shared state. Depending on risk, tests, work-item context, and approval evidence may also be needed.

For `Invoice.java`, useful evidence includes 'tax 8% to 10% for a regulatory change', before/after invoice examples, Binh's rounding feedback, and the accepted history point containing the final result. This supports regression investigation and accountability but cannot guarantee defect-free behavior.

**Practice:** prepare a six-item acceptance checklist for An's change and mark which items a lone `shop-final.zip` cannot establish. Do not assume every collaboration product uses the same status names or buttons.

</details>

- [Back to top](#back-to-top)

---

## <a id="source-to-delivery-boundary">From Accepted Source to Delivery Automation</a>

<details>
<summary>Click for details</summary>

Source management reaches its boundary here at **the team's accepted shared source state**, not when an application is deployed. That accepted state can become input to later **build**, **test**, and **delivery/deployment** processes. Acceptance of source alone does not prove that downstream delivery checks passed.

For further study, the Git module owns commit, branch, and synchronization mechanics. GitHub, GitLab, Bitbucket, or Azure DevOps modules explain product-specific proposals, reviews, and repository permissions. Branching Strategy and Monorepo/Polyrepo own team-level source organization choices. CI/CD and release management belong to delivery automation. This is an explanatory handoff, not a fabricated navigation link.

**Final practice:** retell the An–Binh story in six steps, from diverging `Invoice.java` copies to a team-accepted revision. Add a separate sentence naming the testing and deployment outcomes that are **not yet established**. That boundary prepares you for the next modules.

</details>

- [Back to top](#back-to-top)
