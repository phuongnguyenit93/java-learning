<a id="back-to-top"></a>

# Tools, Collaboration, and Team Strategy

## Menu
- [Version Control Versus Hosting Platforms](#vcs-vs-hosted-platform)
- [Purpose of Change Proposals and Review](#proposing-and-reviewing-changes)
- [Access and Change Acceptance Rules](#access-and-change-acceptance)
- [Branching Policy Versus Git Mechanics](#branching-policy-vs-git-mechanics)
- [Repository Topology Strategy: One or Multiple Repositories](#repository-topology-strategy)

## <a id="vcs-vs-hosted-platform">Version Control Versus Hosting Platforms</a>

<details>
<summary>Click for details</summary>

A repository with history is not sufficient for healthy teamwork. A **VCS** such as Git records versions, history, and exchanges of changes. A **hosting/collaboration platform** such as GitHub, GitLab, Bitbucket, or Azure Repos provides hosted repositories and surfaces for proposals, reviews, and access governance; exact features depend on the product.

Separate **mechanism** from **team process**. Git can record work locally without a GitHub account. Conversely, creating a hosted account does not prove that revisions were recorded carefully or reviewed. Hosting cannot replace the history, accepted-state, and accountability principles already established.

**Practice:** assign *record a change*, *request review*, *manage who may view a repository*, and *recover an older state* to VCS, platform, or team policy. Learn detailed Git operations in the Git module and vendor-specific permissions and review in a collaboration-platform module.

</details>

- [Back to top](#back-to-top)

---

## <a id="proposing-and-reviewing-changes">Purpose of Change Proposals and Review</a>

<details>
<summary>Click for details</summary>

After An finishes a tax fix, sharing it with the team makes a **proposal**, not an accepted source revision. **Review** means other people inspect differences, understand intent, question assumptions, and provide feedback before a change may enter shared source. Many hosted platforms represent this through pull requests or merge requests, but those are platform collaboration objects, not the core Git history mechanism.

A useful proposal states the problem, the affected scope, and verification evidence. A reviewer might notice that Binh's rounding update changes the outcome. A comment is not automatically an approval, and merge eligibility depends on the repository's governance rules.

**Evidence:** 'Change tax from 8% to 10%; compare invoices before and after' is more reviewable than 'update invoice'. **Practice:** write two specific questions a reviewer should ask before accepting a money-calculation change.

</details>

- [Back to top](#back-to-top)

---

## <a id="access-and-change-acceptance">Access and Change Acceptance Rules</a>

<details>
<summary>Click for details</summary>

**Access permissions** determine who can view, propose, modify, or administer repository resources. **Acceptance conditions** determine what evidence must exist before shared source is updated—such as review and appropriate checks. They are related but not equivalent: permission to write does not mean an individual proposal has been approved.

**Least privilege** limits avoidable risk: a person reading documentation need not change the main shared state; a reviewer may need context without administrative control. Specific roles and enforced merge checks differ by provider and plan, so a vendor's button labels should not be presented as universal policy.

**Scenario:** Binh is allowed to submit a proposal but the required review is missing; the proposal must wait. **Practice:** write separate rules for who may *propose*, who may *approve*, and what indicates the shared revision was *accepted*.

</details>

- [Back to top](#back-to-top)

---

## <a id="branching-policy-vs-git-mechanics">Branching Policy Versus Git Mechanics</a>

<details>
<summary>Click for details</summary>

A **branch** in Git allows different lines of recorded development. That is a **tool capability**. Whether a team creates a branch per task, integrates daily, or keeps release stabilization branches is a **branching policy** selected for its needs.

Two teams can both use Git yet choose different strategies: one integrates small work into a shared mainline frequently; another must maintain fixes for previously shipped versions. A branch name or hosting product alone does not determine the right strategy. Branch creation and merge mechanics belong to the Git module; Trunk-Based Development, GitHub Flow, Git Flow, and branch-lifetime decisions belong to Branching Strategy.

**Practice:** classify 'create a branch' as mechanism or policy, then classify 'close work branches within two days'. Explain what goal and evidence are needed to evaluate the latter rule.

</details>

- [Back to top](#back-to-top)

---

## <a id="repository-topology-strategy">Repository Topology Strategy: One or Multiple Repositories</a>

<details>
<summary>Click for details</summary>

**Repository topology** is the decision to organize software components in one or multiple repositories. A **monorepo** keeps multiple components together; a **polyrepo** separates them across repositories. This is a collaboration and ownership choice, not a switch inside Git.

A shared repository can make cross-component edits visible together, but permissions and test scope need management. Separate repositories can clarify ownership and access boundaries, while coordinated changes across them may require extra work. Either topology can support different release cadences with appropriate tooling; repository count alone does not dictate release frequency.

**Example:** an invoice format shared by `billing` and `reporting` requires evaluating both components whether they share a repository or not. **Practice:** list one benefit and cost for each topology. The detailed decision framework belongs to the Monorepo/Polyrepo module.

</details>

- [Back to top](#back-to-top)
