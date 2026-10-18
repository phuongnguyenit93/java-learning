---
video:
  url: ""
---

# Foundations of Source Collaboration on GitLab

<!--
VIDEO SCRIPT FORMAT

Section rules:
- Each H2 is one video section/chapter.
- The first section requires at least 1 Scene.
- From the second section onward, each section requires at least 1 Transition + 1 Scene.
- Step 6 may add more Scenes/Transitions when the Knowledge content needs them.

TRANSITION FORMAT

### Transition

**Time:** `MM:SS–MM:SS`

**Visual:**

Describe the transition, title card, or screen change.

**Script:**

Write the short bridge from the previous section to the current section.

**Purpose:**

Explain why this transition exists.

SCENE FORMAT

### Scene N — <optional scene title>

**Time:** `MM:SS–MM:SS`

**Visual:**

Describe what the viewer sees: slide, diagram, source code, terminal, API request/response, runtime output, highlight, or callout.

**Script:**

Write the narration/presentation script for this scene.

**Purpose:**

Explain what this scene teaches, demonstrates, or proves.
-->

## GitLab as a Git Hosting and Team Collaboration Platform

<!-- VIDEO_SECTION -->

### Scene 1 — GitLab as a Git Hosting and Team Collaboration Platform

**Time:** `00:00–00:58`

**Visual:**

Start with three ZIP files all named final, then reveal an illustrative GitLab project. Spotlight Code, Merge requests, Issues, and Members in turn, with 'storyboard, not live results' caption.

**Script:**

Three developers send files labeled final, but which change did the team actually accept? GitLab does not judge business correctness for us. It gives the team a place to connect Git history to proposals, reviews, permissions, and acceptance decisions. In our gateway project, Code shows recorded source, Merge requests organizes proposed integration, and Issues records the problem being solved. As we follow a negative-invoice example, distinguish having recorded code from having the group's agreement to use it.

We will follow that example through project/group ownership and permissions, shared branches or forks, MRs and review, protected branch requirements, Issues and Releases, then combine them into one change-acceptance workflow. Git commits and CI/CD deployment remain separate learning topics.

**Purpose:**

Motivate hosted collaboration with a recognizable failure before introducing platform objects.

## Unreviewed Changes, Lost Work Context, and Unclear Merge Decisions Without GitLab Collaboration

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:58–01:11`

**Visual:**

Keep the previous scene’s evidence as a smaller left panel, highlight "one home for proposed changes and decisions", then reveal the next question on the right with a scope-change arrow.

**Script:**

Hosting records the files, but that alone cannot establish that the newly recorded behavior is correct. What evidence is missing?

**Purpose:**

Bridge the earlier observation about one home for proposed changes and decisions to the next decision about review reduces risk but does not replace tests without resetting the case.

### Scene 1 — Unreviewed Changes, Lost Work Context, and Unclear Merge Decisions Without GitLab Collaboration

**Time:** `01:11–02:13`

**Visual:**

Compare Issue #42 requiring rejection of -1 with an illustrative MR !57 that clamps the value to zero. Leave testing and reviewer-result cells blank rather than inventing a successful check.

**Script:**

Suppose An changes the code to clamp -1 to zero, while Issue number 42 requires negative amounts to be rejected. The edit might compile and still violate the business rule. Without a shared description and review, Binh cannot easily tell what was proposed or why it was accepted. Put the requirement beside the diff and ask what test would establish the expected behavior. Review can expose the inconsistency, but discussion alone cannot prove the fix. Our empty check cells mean no validation has been executed here.

**Purpose:**

Expose the risk of missing change context and distinguish review from behavioral proof.

## Git Version History Versus GitLab Collaboration Responsibilities

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:13–02:26`

**Visual:**

Keep the previous scene’s evidence as a smaller left panel, highlight "review reduces risk but does not replace tests", then reveal the next question on the right with a scope-change arrow.

**Script:**

To explain a missing review record, first separate the system that stores history from the system that hosts collaboration.

**Purpose:**

Bridge the earlier observation about review reduces risk but does not replace tests to the next decision about Git records history; GitLab governs collaboration without resetting the case.

### Scene 1 — Git Version History Versus GitLab Collaboration Responsibilities

**Time:** `02:26–03:26`

**Visual:**

Split the frame between a local Git commit graph and a GitLab project with MR, reviewer, and branch settings. Animate a proposal crossing the boundary, not a local merge becoming an approval.

**Script:**

An can record commits on a laptop without opening GitLab. Git owns that local version history and the branches that refer to it. GitLab hosts that repository and adds merge requests, discussions, membership, and merge conditions. Saying 'I merged it locally' does not show that MR number 57 met the target project's review requirements. We will inspect the hosted collaboration records in this module, rather than teach the Git commands themselves. The two layers cooperate, but neither supplies the other layer's evidence automatically.

**Purpose:**

Separate distributed version control mechanics from hosted proposal and authorization records.

## GitLab Projects and Git Repositories: Collaboration Scope and Source History

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:26–03:39`

**Visual:**

Keep the previous scene’s evidence as a smaller left panel, highlight "Git records history; GitLab governs collaboration", then reveal the next question on the right with a scope-change arrow.

**Script:**

GitLab hosts the records that Git itself does not own. Let's inspect the objects inside one project.

**Purpose:**

Bridge the earlier observation about Git records history; GitLab governs collaboration to the next decision about a project contains more than its Git repository without resetting the case.

### Scene 1 — GitLab Projects and Git Repositories: Collaboration Scope and Source History

**Time:** `03:39–04:39`

**Visual:**

Draw a gateway Project boundary containing Repository, Issues, Merge requests, Members, and Settings. Highlight DiscountPolicy on fix-rounding while main remains a separate source view.

**Script:**

A GitLab project is not just a folder of files. Its Git repository contains source and recorded history, while an Issue can describe a defect before any commit exists. A merge request can expose a proposal on a work branch that main has not accepted. Here the fix-rounding version appears in the project, but we do not claim production already contains that change. When looking at the interface, ask which branch, which collaboration record, and which acceptance stage each pane is actually showing.

**Purpose:**

Teach the project boundary and the difference between proposed source and target history.

## Personal Namespaces, Groups, and Subgroups as Organizational Boundaries

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:39–04:52`

**Visual:**

Keep the previous scene’s evidence as a smaller left panel, highlight "a project contains more than its Git repository", then reveal the next question on the right with a scope-change arrow.

**Script:**

Now that the project boundary is clear, how does GitLab express ownership above individual projects?

**Purpose:**

Bridge the earlier observation about a project contains more than its Git repository to the next decision about a namespace expresses ownership, not nested Git histories without resetting the case.

### Scene 1 — Personal Namespaces, Groups, and Subgroups as Organizational Boundaries

**Time:** `04:52–05:51`

**Visual:**

Build company/payments/gateway as group, subgroup, and project cards. Draw conditional policy/member inheritance arrows, but only one repository within gateway.

**Script:**

The path company/payments/gateway may resemble folders, but it does not mean three Git repositories are nested. Company is a group, payments a subgroup, and gateway the project containing source and collaboration features. This hierarchy helps organize ownership and can influence inherited membership and policy. Open the Groups and Projects views to verify which object you are inspecting. The repository tree and the organizational namespace answer different questions, so do not derive Git history or push permission from the URL alone.

**Purpose:**

Distinguish organizational hierarchy from repository topology and prepare for membership inheritance.

## Responsibilities of Members, Contributors, Assignees, and Reviewers

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:51–06:04`

**Visual:**

Keep the previous scene’s evidence as a smaller left panel, highlight "a namespace expresses ownership, not nested Git histories", then reveal the next question on the right with a scope-change arrow.

**Script:**

The namespace identifies an owner. Individual participants still have distinct jobs and permission sources.

**Purpose:**

Bridge the earlier observation about a namespace expresses ownership, not nested Git histories to the next decision about roles, contribution, assignment, and review are different labels without resetting the case.

### Scene 1 — Responsibilities of Members, Contributors, Assignees, and Reviewers

**Time:** `06:04–07:03`

**Visual:**

Show An as Issue assignee, Binh as MR !57 reviewer, and Chi as project Maintainer; keep a separate effective-membership card for each person.

**Script:**

An being assigned Issue number 42 does not automatically grant permission to merge main. Binh being requested as an MR reviewer does not make Binh a Maintainer. Chi can manage protection rules while still needing a domain reviewer for the invoice logic. Compare three questions for each person: what have they contributed, what are they assigned to do, and what role do they actually hold? Self-approval restrictions may matter depending on tier and configuration. Treat responsibility and permission as separate evidence.

**Purpose:**

Prevent workflow labels from being mistaken for effective authority or valid approval.

## Merge Requests as Reviewable Change Proposals Before Integration

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:03–07:16`

**Visual:**

Keep the previous scene’s evidence as a smaller left panel, highlight "roles, contribution, assignment, and review are different labels", then reveal the next question on the right with a scope-change arrow.

**Script:**

Once participants are identified, they need a shared record of one proposed code change.

**Purpose:**

Bridge the earlier observation about roles, contribution, assignment, and review are different labels to the next decision about an MR proposes integration rather than proving it without resetting the case.

### Scene 1 — Merge Requests as Reviewable Change Proposals Before Integration

**Time:** `07:16–08:14`

**Visual:**

Create a schematic MR !57 with source fix-rounding, target main, Description, Changes, Discussions, and Approvals. Leave Merged and pipeline result unfilled.

**Script:**

A merge request connects the source branch containing a proposal to the target branch that might accept it. An opens MR number 57 to explain the negative-amount defect and give Binh a focused diff to review. The MR collects description, discussions, reviewer state, and approval information when applicable. Its existence does not prove anyone authorized the change or that a check passed. Keep the source, target, and review evidence visible before we discuss the conditions for completing the merge.

**Purpose:**

Establish MR as a reviewable proposal and preserve the boundary between open and merged.

## Relationships Among Projects, Source/Target Branches, Merge Requests, and Reviewers

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:14–08:27`

**Visual:**

Keep the previous scene’s evidence as a smaller left panel, highlight "an MR proposes integration rather than proving it", then reveal the next question on the right with a scope-change arrow.

**Script:**

We have the project, participants, and MR; now connect them into a traceable change journey.

**Purpose:**

Bridge the earlier observation about an MR proposes integration rather than proving it to the next decision about each workflow stage requires its own evidence without resetting the case.

### Scene 1 — Relationships Among Projects, Source/Target Branches, Merge Requests, and Reviewers

**Time:** `08:27–09:28`

**Visual:**

Connect Issue #42 → fix-rounding → MR !57 → eligible review/rules → main → possible Release v1.4. Use a dashed line toward release and deployment.

**Script:**

We can follow the defect without pretending every stage is the same event. Issue number 42 explains the need, fix-rounding carries An's proposal, and MR number 57 organizes review and the requested target. Branch protection and any applicable approval requirements govern acceptance. A Release may be published later, but merging source does not automatically create release metadata or deploy to production. Each arrow needs its own observable GitLab record. The next chapter asks who owns the project and who can see these collaboration records.

**Purpose:**

Synthesize the end-to-end evidence chain and hand off to group/project governance.
