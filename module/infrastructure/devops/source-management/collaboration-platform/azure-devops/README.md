# 📂 README MODULE STRUCTURE (EN)

* [01-azure-repos-collaboration](readme/en/menu/01-azure-repos-collaboration.md)
* [02-organizations-projects-repositories](readme/en/menu/02-organizations-projects-repositories.md)
* [03-membership-and-access](readme/en/menu/03-membership-and-access.md)
* [04-branches-forks-contributions](readme/en/menu/04-branches-forks-contributions.md)
* [05-pull-request-review](readme/en/menu/05-pull-request-review.md)
* [06-branch-policies-and-bypass](readme/en/menu/06-branch-policies-and-bypass.md)
* [07-complete-pull-requests](readme/en/menu/07-complete-pull-requests.md)
* [08-end-to-end-azure-repos](readme/en/menu/08-end-to-end-azure-repos.md)

# Azure Repos: Collaborate on and Govern Source Changes

Azure Repos is Azure DevOps' Git repository hosting and source collaboration service. This module helps learners understand **where source is owned, who may contribute, how proposals are reviewed, and why a pull request can or cannot be completed**. Git owns version history; Azure Repos adds repository-level collaboration and governance.

**Prerequisites:** basic familiarity with Git repositories, branches, commits, and remotes. Learn the organization, project, repository, and pull request vocabulary before permissions and branch policies.

**Learning journey:** Git versus Azure Repos → organizations/projects/repositories, visibility, and the Azure DevOps Services public-project lifecycle → security groups and Allow/Deny/Not set → shared branches/forks and source/target PRs → discussions and review votes → branch policies, checks, and bypass rights → complete/auto-complete and work-item links → an end-to-end collaboration scenario with observable evidence.

**Product context:** Azure DevOps Services retired new public-project creation in 2026; remaining public projects are scheduled to become private in 2027. Exact permissions, features, and UI details can depend on the product and version.

**Boundary:** this module owns *Azure Repos collaboration*, not Git internals, branch lifetime strategy, or pipeline implementation. Azure Boards appears only as linked work items, and Azure Pipelines only as a possible source of build validation/status. The eight chapters now provide substantive explanations, collaboration examples, branch-policy reasoning, and observable evidence for troubleshooting review and completion.
