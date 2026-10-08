---
video:
  url: ""
---

# Jira, Confluence, and Team Collaboration

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

## Jira Work Items: Concept and Source-Change Context

<!-- VIDEO_SECTION -->

### Scene 1 — Jira Work Items: Concept and Source-Change Context

**Time:** `00:00–01:35`

**Visual:**

Display Jira work item `BILL-142 Tax rule update` alongside the Bitbucket `invoice-api` PR; label `Why` and `How`, use demo data only.

**Script:**

A Bitbucket pull request tells Binh how An proposes to change source, but the diff alone does not establish why the business wants a different tax rule. Jira work item `BILL-142` can capture the requirement, owner, and business workflow. The work item is not a Git commit, and the PR is not a substitute for issue tracking. Binh reads Jira to understand the desired behavior, then returns to the source diff and invoice examples to evaluate implementation. A Jira status of Done does not by itself prove the code merged or production deployed.

**Purpose:**

Distinguish business requirement provenance from the implementation change.



## Connecting Source Changes and Pull Requests to Jira

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:35–01:47`

**Visual:**

Keep Jira BILL-142's requirement visible; draw conditional links from the branch and PR only when integration is configured.

**Script:**

The Jira work item explains why the tax rule should change. How can we connect that requirement to the branch, commits, and pull request?

**Purpose:**

Connect business intent to development references without implying a typed issue key always synchronizes.

### Scene 2 — Connecting Source Changes and Pull Requests to Jira

**Time:** `01:47–03:22`

**Visual:**

Illustrate PR `BILL-142 Update invoice tax`, branch `BILL-142-tax`, and Jira↔Bitbucket links that appear only under a configured connection.

**Script:**

When administrators properly connect Jira and Bitbucket Cloud, a work-item key in branch names, commit messages, or PR titles can help surface related development activity. Binh can navigate from the Jira requirement to the tax PR and back to the expected behavior. But merely typing `BILL-142` somewhere does not guarantee a link if the integration or permissions are not configured. For a recording, verify both navigation directions and state the real setup prerequisites rather than portraying an unconfigured key as automatic cross-product synchronization.

**Purpose:**

Explain source-to-Jira traceability with explicit integration and permission prerequisites.



## Mentions, Notifications, and Review Tasks

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:22–03:34`

**Visual:**

Retain the Jira-to-PR link; magnify Binh's mention, activity notification and outstanding 100.05 test task.

**Script:**

Context is linked; how do smaller team conversations stay actionable inside the PR?

**Purpose:**

Translate linked work context into specific actionable PR communication rather than approval.

### Scene 3 — Mentions, Notifications, and Review Tasks

**Time:** `03:34–05:09`

**Visual:**

Show a rounding question beside code, an @mention of Binh, a demo notification and an open task assigned to An; show distinct icons and states.

**Script:**

Not every feedback item requires a meeting. PR comments keep questions beside changed lines, @mentions invite relevant people to inspect the discussion, notifications surface activity, and tasks track concrete work to completion. Orchid mentions Binh about rounding and assigns An a negative-invoice test task. Tagging Binh is not proof of approval. Receiving a notification does not prove he opened the PR. The evidence we need is the discussion itself, the open or resolved task, and the current reviewer judgment when it is available.

**Purpose:**

Separate mention, notification, actionable task, and approval records.



## Retirement of Built-in Issues and Wiki on August 20, 2026

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:09–05:21`

**Visual:**

Switch from the PR task to an Atlassian 2026 retirement timeline; cross out native Cloud Issues/Wiki at August 20 and point to Jira/Confluence.

**Script:**

Some older guides also mention native Bitbucket Issues and Wiki. Does that workflow still exist?

**Purpose:**

Make the retired cloud features unambiguously unavailable rather than teaching a broken UI path.

### Scene 4 — Retirement of Built-in Issues and Wiki on August 20, 2026

**Time:** `05:21–06:56`

**Visual:**

Show an official Atlassian timeline ending **August 20, 2026**; cross out `Cloud native Issues` and `Cloud native Wiki` from UI and API and point to external work tracking/docs.

**Script:**

Old Bitbucket Cloud tutorials may show an Issues tab and repository Wiki editor. Those are no longer current features: Atlassian removed the native Issues and Wiki capabilities from Bitbucket Cloud UI and API on August twentieth, twenty twenty-six. Do not ask learners to create a new native Bitbucket Cloud issue or edit a native Wiki page as though those workflows remain available. This statement is specifically about Cloud; do not assume the same retirement statement applies to every Bitbucket Server or Data Center edition. Orchid should choose Jira or another work tracker and a separate documentation home.

**Purpose:**

Prevent an obsolete native-Cloud Issues/Wiki demonstration with a dated product-specific boundary.



## Confluence and Separate Team Documentation

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:56–07:08`

**Visual:**

Retain the Issues/Wiki sunset marker; open Confluence's Invoice rounding policy beside the repository README and PR reference without a generated Git commit.

**Script:**

Once work tracking has moved elsewhere, where should longer-lived team documentation reside?

**Purpose:**

Differentiate external collaborative documentation from versioned source files after the native wiki retirement.

### Scene 5 — Confluence and Separate Team Documentation

**Time:** `07:08–08:43`

**Visual:**

Confluence demo page `Invoice rounding policy` beside a versioned repository README; link both conceptually from PR `BILL-142` without suggesting shared Git commits.

**Script:**

Confluence is useful for shared business rules, architecture decisions and collaboration notes. README or versioned files near source are often better for details that must evolve with code. Orchid keeps its rounding policy in team documentation while the PR explains the implementation. Editing a Confluence page does not create a Git commit in `invoice-api`; completing a PR does not automatically guarantee the external document is consistent. The team must identify owners, stable links, and a review step for keeping the descriptions aligned.

**Purpose:**

Choose documentation by ownership and lifecycle rather than assuming automatic cross-product changes.
