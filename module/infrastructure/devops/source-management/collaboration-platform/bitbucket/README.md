# 📂 README MODULE STRUCTURE (EN)

* [01_BitbucketCollaborationFoundations](readme/en/menu/01_BitbucketCollaborationFoundations.md)
* [02_AccessAndRepositoryGovernance](readme/en/menu/02_AccessAndRepositoryGovernance.md)
* [03_PullRequestChangeProposals](readme/en/menu/03_PullRequestChangeProposals.md)
* [04_PullRequestReviewsAndTasks](readme/en/menu/04_PullRequestReviewsAndTasks.md)
* [05_BranchRestrictionsAndMergeChecks](readme/en/menu/05_BranchRestrictionsAndMergeChecks.md)
* [06_JiraConfluenceAndCollaboration](readme/en/menu/06_JiraConfluenceAndCollaboration.md)
* [07_GovernedBitbucketTeamWorkflow](readme/en/menu/07_GovernedBitbucketTeamWorkflow.md)

# Overview — Source Collaboration with Bitbucket Cloud

**Bitbucket Cloud** hosts Git repositories and helps teams organize, propose, review, and govern changes to shared source code. This module develops the mental model for turning individual Git history into accountable team collaboration with clear access, reviewers, and evidence of accepted changes.

**Prerequisites:** conceptual familiarity with Git repositories, commits, branches, and remote repositories. No prior Bitbucket UI experience, CI/CD configuration, or deployment knowledge is required.

**Learning path:** start by separating Git responsibilities from Bitbucket Cloud's collaboration capabilities and learning the workspace–project–repository hierarchy and team roles. Then consider access and inherited permissions, pull requests as proposals, and review feedback and tasks. With those concepts established, examine branch restrictions and merge checks, distinguishing advisory warnings from merge blocking configured on Premium plans. Finally, connect work context through Jira, documentation through Confluence, and assemble a governed end-to-end contribution workflow.

**Current product note:** Atlassian confirmed that on August 20, 2026, Bitbucket Cloud's built-in **Issues** and **Wiki** were removed from its UI and API. Jira can track work items, while Confluence or other documentation services can hold team knowledge. The retired native features are not taught as current Bitbucket capabilities.

**Boundaries and next steps:** this module owns Bitbucket Cloud repository organization, permissions, pull requests, review, merge checks, and linked collaboration. Commit/merge/rebase mechanics belong to **Git**; branch-policy and monorepo/polyrepo decisions belong to **repository strategy**; implementing Bitbucket Pipelines belongs to **delivery automation/CI/CD**, not to this module.
