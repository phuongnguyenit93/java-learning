# 📂 README MODULE STRUCTURE (EN)

* [01-strategy-foundations](readme/en/menu/01-strategy-foundations.md)
* [02-branch-lifetime-and-cadence](readme/en/menu/02-branch-lifetime-and-cadence.md)
* [03-trunk-based-development](readme/en/menu/03-trunk-based-development.md)
* [04-github-flow](readme/en/menu/04-github-flow.md)
* [05-git-flow](readme/en/menu/05-git-flow.md)
* [06-integration-history-policy](readme/en/menu/06-integration-history-policy.md)
* [07-release-hotfix-policy](readme/en/menu/07-release-hotfix-policy.md)
* [08-team-governance](readme/en/menu/08-team-governance.md)
* [09-selection-and-diagnostics](readme/en/menu/09-selection-and-diagnostics.md)

# Overview: Team Branching and Integration Strategy

A branching strategy is a team-level agreement on how parallel work is organized, when changes reach the mainline, and what must be true before accepting them. A Git branch is only a history-management mechanism; without shared policy, teams risk late integration, aging branches, slow review, and missed maintenance fixes.

**Prerequisites:** Understand repositories, commits, branches, merges, and the basic purpose of pull/merge requests. This module does not teach Git commands, vendor settings, or pipeline configuration.

**Learning path:** Start with why teams need a strategy and the vocabulary of mainline, work, and release branches. Understand branch lifetime and integration cadence before comparing Trunk-Based Development, GitHub Flow, and Git Flow in their proper contexts. Continue with integrated-history policy, release/hotfix coordination, and team governance. Finally, choose a model for a sample situation and diagnose evidence that the workflow is failing.

**Learning outcomes:** Explain a strategy choice rather than copying a branded workflow, reason about change size and integration frequency, match release constraints to a policy, and propose review responsibilities, mainline safeguards, and patch propagation. GitHub Flow supports pull-request collaboration but does not itself enforce Trunk-Based Development's short branch lifetimes; Git Flow introduces additional coordination costs for continuously delivered products.

**Boundaries:** Git owns branch/merge/rebase mechanics; GitHub, GitLab, Bitbucket, and Azure DevOps own platform review/permission/protection configuration. CI/CD implements checks; versioning and deployment have separate owners. This module concentrates on team decisions, trade-offs, and integration governance.
