---
video:
  url: ""
---

# Merge Request Reviews, Feedback, and Approvals

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

## Review Context: Diffs, Discussions, and Inline Comments

<!-- VIDEO_SECTION -->

### Scene 1 — Review Context: Diffs, Discussions, and Inline Comments

**Time:** `00:00–01:00`

**Visual:**

Open an illustrative MR !57 Overview, Changes, and Discussions. Place Issue #42's reject-negative requirement beside a diff that clamps negatives to zero; keep check results unspecified.

**Script:**

Binh should not begin by counting modified lines. First read Issue number 42: negative invoice amounts must be rejected. The MR diff shows a clamp to zero, which could compile while violating that requirement. Pause on the mismatch, then open an inline discussion to ask for a reproducible example. A green automation badge would not prove this business rule was handled correctly, and we have not run any real pipeline for this storyboard. The reviewer needs the requirement, source change, and evidence together.

**Purpose:**

Teach contextual diff review and show why test indicators cannot replace business reasoning.

## Reviewers, Assignees, and Eligible Approvers

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:00–01:13`

**Visual:**

Keep the previous scene’s evidence as a smaller left panel, highlight "review the requirement before scanning the diff", then reveal the next question on the right with a scope-change arrow.

**Script:**

The diff review is a task; the system's approval requirement may involve different people and permissions.

**Purpose:**

Bridge the earlier observation about review the requirement before scanning the diff to the next decision about reviewer assignment does not establish eligible approval without resetting the case.

### Scene 1 — Reviewers, Assignees, and Eligible Approvers

**Time:** `01:13–02:12`

**Visual:**

Show Assignee An, Reviewer Binh, and a conditional Approval rules card for Chi. Draw different responsibility arrows and an eligibility check for counted approvals.

**Script:**

The assignee keeps the MR moving, while a reviewer inspects its quality. An eligible approver must additionally meet the membership and rule conditions for a submitted approval to count. An might be assignee, Binh reviewer, and Chi a required compliance approver when that feature and configuration are available. Adding Binh to Reviewers does not create an approval, and a comment is not a qualifying approval vote. Inspect the Approvals widget and actual eligible users before counting a review requirement as satisfied.

**Purpose:**

Clarify separate participation roles and tier-dependent approval eligibility.

## Comments, Suggestions, and Approval Decisions

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:12–02:25`

**Visual:**

Keep the previous scene’s evidence as a smaller left panel, highlight "reviewer assignment does not establish eligible approval", then reveal the next question on the right with a scope-change arrow.

**Script:**

A reviewer can leave several kinds of feedback. Which of them constitutes a positive approval?

**Purpose:**

Bridge the earlier observation about reviewer assignment does not establish eligible approval to the next decision about a comment, suggestion, and approval are not interchangeable without resetting the case.

### Scene 1 — Comments, Suggestions, and Approval Decisions

**Time:** `02:25–03:23`

**Visual:**

Zoom into a pricing line with three overlays: Comment asking about negatives, Suggestion proposing an edit, and Approve as a separate review decision.

**Script:**

Binh asks whether input -1 should be rejected. That is a comment requiring an answer, not necessarily a merge-blocking decision. A suggestion proposes a concrete edit, while Approve signals a positive review. GitLab Free supports feedback and review, but we must not assume all advanced enforcement features are included. Even a submitted approval may leave the MR blocked by another rule or target permission. Read the actual review and updated source before inferring that the code is accepted.

**Purpose:**

Separate conversational feedback from submitted approval and from eligibility to merge.

## Blocking a Merge with Request Changes: Premium/Ultimate Availability

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:23–03:36`

**Visual:**

Keep the previous scene’s evidence as a smaller left panel, highlight "a comment, suggestion, and approval are not interchangeable", then reveal the next question on the right with a scope-change arrow.

**Script:**

A request for revision communicates intent; whether it enforces a block depends on the offering.

**Purpose:**

Bridge the earlier observation about a comment, suggestion, and approval are not interchangeable to the next decision about blocking Request changes has a plan-dependent boundary without resetting the case.

### Scene 1 — Blocking a Merge with Request Changes: Premium/Ultimate Availability

**Time:** `03:36–04:36`

**Visual:**

Place GitLab Free alongside Premium/Ultimate. Show enforcement-capable Request changes only on supported plans; leave exact button availability subject to the actual instance.

**Script:**

When Binh finds an incorrect rounding rule, the useful response names the failing input, evidence, and acceptance condition. GitLab offers Request changes behavior, but using that state to block a merge is a Premium/Ultimate capability in the documented review experience. We should not draw a guaranteed blocking control on every Free project. Verify the offering and current interface instead of assuming identical behavior across plans. After An updates the fix, Binh needs to inspect the new revision and explicitly reconsider the request.

**Purpose:**

Prevent a false claim that every GitLab plan enforces Request changes as a merge gate.

## Approval Rules, Required Approvers, and Tier Constraints

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:36–04:49`

**Visual:**

Keep the previous scene’s evidence as a smaller left panel, highlight "blocking Request changes has a plan-dependent boundary", then reveal the next question on the right with a scope-change arrow.

**Script:**

The ability to request revision is one gate; some projects instead require a number of eligible approvals.

**Purpose:**

Bridge the earlier observation about blocking Request changes has a plan-dependent boundary to the next decision about approval rules count qualified approvals, not avatars without resetting the case.

### Scene 1 — Approval Rules, Required Approvers, and Tier Constraints

**Time:** `04:49–05:52`

**Visual:**

Sketch Settings > Merge requests > Approval rules showing required counts 0 and 2. Mark qualifying direct group/project membership and Premium/Ultimate scope.

**Script:**

An approval rule defines both a required number and eligible approvers. On GitLab Premium or Ultimate, rules can be configured for projects and, subject to controls, individual MRs. A zero count can make a rule optional; a positive count establishes a requirement. Imagine MR number 57 has one approval but requires two eligible votes: it is not ready just because one avatar shows a check. Membership matters, and mere visibility or indirect association with an approver group is not proof of eligibility. Inspect the actual rule and counted users.

**Purpose:**

Explain count, eligibility, overrides, and plan limits without manufacturing MR state.

## Code Owner Scope Versus General Approval Rules

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:52–06:05`

**Visual:**

Keep the previous scene’s evidence as a smaller left panel, highlight "approval rules count qualified approvals, not avatars", then reveal the next question on the right with a scope-change arrow.

**Script:**

Counting eligible reviewers is not enough when the changed files fall into an owner-controlled area.

**Purpose:**

Bridge the earlier observation about approval rules count qualified approvals, not avatars to the next decision about CODEOWNERS is path-specific, unlike general MR rules without resetting the case.

### Scene 1 — Code Owner Scope Versus General Approval Rules

**Time:** `06:05–07:04`

**Visual:**

Put CODEOWNERS mapping /pricing/ to @company/payments-reviewers beside MR !57 changing pricing/Calculator.java. Show a separate, plan-dependent protected-target owner-approval requirement.

**Script:**

CODEOWNERS maps areas of source to responsible people or groups. If an MR modifies pricing, a general approval may not satisfy a required code-owner condition on the protected target branch when the feature is enabled. Naming a person or group in CODEOWNERS does not give them access or make their approval valid. General approval rules can require different reviewers for an entire MR. Compare the changed path, ownership entry, target branch settings, and actual eligible membership before declaring the change acceptable.

**Purpose:**

Distinguish path accountability from general required approval logic.

## Feedback Resolution, Merge Request Updates, and Re-Review

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:04–07:17`

**Visual:**

Keep the previous scene’s evidence as a smaller left panel, highlight "CODEOWNERS is path-specific, unlike general MR rules", then reveal the next question on the right with a scope-change arrow.

**Script:**

After an eligible reviewer raises a defect, what evidence should the author provide before seeking another decision?

**Purpose:**

Bridge the earlier observation about CODEOWNERS is path-specific, unlike general MR rules to the next decision about resolving a discussion does not replace re-review without resetting the case.

### Scene 1 — Feedback Resolution, Merge Request Updates, and Re-Review

**Time:** `07:17–08:19`

**Visual:**

Animate Binh's negative-input concern → An's revised diff/test description → Binh inspects new commits. Show Resolved thread and Approvals as separate indicators.

**Script:**

A reply saying 'fixed' is not enough for Binh to approve the current revision. New source commits alter the MR diff, and project settings may reset previous approvals. Resolving a discussion only records that the conversation was handled; it is not proof that the business behavior was corrected. An should identify the changed lines and new negative-input test, then request a fresh look. Binh verifies the updated source and evidence before submitting a decision. We should not make the merge indicator green merely to complete the demonstration.

**Purpose:**

Teach a traceable feedback and re-review cycle distinct from UI tidying.
