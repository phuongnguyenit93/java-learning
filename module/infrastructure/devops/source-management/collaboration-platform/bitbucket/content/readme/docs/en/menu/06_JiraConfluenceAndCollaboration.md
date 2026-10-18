<a id="back-to-top"></a>

# Jira, Confluence, and Team Collaboration

## Menu
- [Jira Work Items: Concept and Source-Change Context](#jira-work-item-context)
- [Connecting Source Changes and Pull Requests to Jira](#linking-source-changes-to-jira)
- [Mentions, Notifications, and Review Tasks](#mentions-notifications-and-review-tasks)
- [Retirement of Built-in Issues and Wiki on August 20, 2026](#bitbucket-native-issues-wiki-retirement)
- [Confluence and Separate Team Documentation](#confluence-and-external-documentation)

## <a id="jira-work-item-context">Jira Work Items: Concept and Source-Change Context</a>

<details>
<summary>Click for details</summary>

A pull request answers “how was the source changed?”, while reviewers also need to know **why the change is needed**. A **Jira work item** records a requirement, defect, or task with context, ownership, and workflow status. It exists outside the Git repository and is neither a commit nor a special Bitbucket pull request type.

In Orchid, work item `BILL-142` describes the invoice tax-rule change, while the PR in `invoice-api` implements part of that work. Binh reads Jira for expected examples and the PR diff for technical consequences. The mere presence of a Jira key does not prove the requirement was verified or finished.

**Practice:** write a short `BILL-142` item with *old behavior, required behavior, acceptance examples*; then state which evidence belongs in Jira context and which belongs in the PR.

### References
- [Atlassian — Integrate Bitbucket and Jira](https://support.atlassian.com/bitbucket-cloud/docs/use-bitbucket-cloud-and-jira-together/)

</details>

- [Back to top](#back-to-top)

---

## <a id="linking-source-changes-to-jira">Connecting Source Changes and Pull Requests to Jira</a>

<details>
<summary>Click for details</summary>

With **Bitbucket Cloud and Jira connected** through the appropriate administration setup, the team can associate work context with branches, commits, and PRs. Atlassian documents use of a Jira work-item key in branch names, commit messages, or PR titles to surface related source activity.

A PR titled `BILL-142: Update invoice tax calculation` helps Binh navigate from source changes to the business requirement and back from Jira to the implementation discussion. **Linking** must not be confused with **automatically transitioning a Jira issue**: workflow changes depend on configured integrations and choices, not a universal “merge means Done” rule.

**Practice:** trace *Jira BILL-142 → tax PR → diff → reviewer feedback → accepted evidence*. If the expected link is missing, check the workspace-to-Jira connection and consistency of the work-item key before assuming the PR is defective.

### References
- [Atlassian — Integrate Bitbucket and Jira](https://support.atlassian.com/bitbucket-cloud/docs/use-bitbucket-cloud-and-jira-together/)

</details>

- [Back to top](#back-to-top)

---

## <a id="mentions-notifications-and-review-tasks">Mentions, Notifications, and Review Tasks</a>

<details>
<summary>Click for details</summary>

Not every team discussion requires a meeting. In a Bitbucket PR, **comments** preserve questions beside the code, **@mentions** draw relevant people into the discussion, **notifications** surface activity, and **tasks** track specific outstanding actions. These support coordination but do not independently constitute approval.

Orchid mentions Binh beside rounding logic, assigns An an actionable negative-invoice task, and tracks it through resolution. Merely tagging a person without a clear question can leave responsibilities ambiguous. Reviewers still need to inspect updated changes before their decision.

**Practice:** replace the vague comment “please check this” with a task whose completion can be verified. Name the participant to mention and the evidence required to close it. Distinguish a broader Jira work item from a Bitbucket PR task focused on review feedback.

</details>

- [Back to top](#back-to-top)

---

## <a id="bitbucket-native-issues-wiki-retirement">Retirement of Built-in Issues and Wiki on August 20, 2026</a>

<details>
<summary>Click for details</summary>

Older guides sometimes describe built-in Bitbucket Cloud **Issues** and **Wiki** features in each repository. That is no longer current product behavior: Atlassian confirmed that on **August 20, 2026**, these native features were removed from the **Bitbucket Cloud UI and API**. Do not teach learners to create new native repository Issues or Wiki pages as available workflows.

Teams must separate work tracking and documentation from that legacy assumption. **Jira** or another tracker can manage defects and requirements; **Confluence** or separate documentation can preserve team knowledge. Organizations that used the retired capabilities should verify that historical content was exported or migrated and that old internal links have been updated; the retirement did not guarantee automatic migration.

**Evidence:** Atlassian's August 20 announcement explicitly states that users can no longer interact with native Issues/Wikis through the app or API. **Practice:** identify three parts of an existing team workflow that depended on those features and assign a replacement owner.

### References
- [Atlassian — Announcing sunset of Bitbucket Issues and Wikis](https://community.atlassian.com/forums/Bitbucket-articles/Announcing-sunset-of-Bitbucket-Issues-and-Wikis/ba-p/3193882)

</details>

- [Back to top](#back-to-top)

---

## <a id="confluence-and-external-documentation">Confluence and Separate Team Documentation</a>

<details>
<summary>Click for details</summary>

Code and team documentation have different lifecycles. **Confluence** is a collaborative knowledge space suited to business processes, support procedures, and architecture decisions; a Bitbucket repository can retain README files or versioned documentation close to code. A Confluence page does not require a corresponding Git commit.

Orchid stores rounding rules and tax examples in maintained team documentation, while PR `BILL-142` explains the source implementation. The team should designate **authoritative documentation** and reference it from Jira/PR discussions where useful, rather than maintain two independent guides that later disagree. Confluence permissions are administered separately from Bitbucket repository permissions.

**Practice:** create a table mapping *information, location, maintenance owner* for the tax code, business specification, review outcome, and operations guide. Do not select Bitbucket's retired native Wiki as a current documentation destination.

</details>

- [Back to top](#back-to-top)
