---
video:
  url: ""
---

# Rebase and the Consequences of History Rewriting

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

## Commit History Resulting from Merge Versus Rebase

<!-- VIDEO_SECTION -->

### Scene 1 — Graph, patches, and safe stopping points

**Time:** `00:00–01:50`

**Visual:**

Use a disposable clone and an unpublished feature branch; compare terminal output and old/new commits while inspecting `git log --graph --oneline --all; git merge feature/rounding`. Show a visible published-history warning; pause whenever state is unclear.

**Script:**

Start from the same split history and draw two possible outcomes. A merge can preserve both lines with a merge commit; rebase replays feature commits on a different base, often producing a more linear graph. Compare the graphs rather than button labels. Neither approach magically makes the code better: the trade-off concerns history shape and how collaborators interpret published commits.

**Purpose:**

Compare history mechanics without prescribing team policy.

## Rebase: Replaying Commits and Changing Commit IDs

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:50–02:03`

**Visual:**

Hold the actual [git log --graph --oneline --all] result and label it 'Commit History Resulting from Merge Versus Rebase'; circle the evidence just established: Compare history mechanics without prescribing team policy.. Split to the next terminal/graph at [git log --format='%h %p %s' -5] labeled 'Rebase: Replaying Commits and Changing Commit IDs'; hide the result for prediction, then reveal the evidence for: Show replayed commits using new parent relationships.

**Script:**

Two histories may express similar changes. Why do the commit IDs differ?

**Purpose:**

Connect evidence from 'Commit History Resulting from Merge Versus Rebase' to 'Rebase: Replaying Commits and Changing Commit IDs', turning the earlier observation into a specific next Git-state check: Show replayed commits using new parent relationships.

### Scene 1 — Graph, patches, and safe stopping points

**Time:** `02:03–03:53`

**Visual:**

Use a disposable clone and an unpublished feature branch; compare terminal output and old/new commits while inspecting `git log --format='%h %p %s' -5; git rebase main`. Show a visible published-history warning; pause whenever state is unclear.

**Script:**

Mark the feature commits before rebasing onto updated main in a throwaway clone. Rebase replays their changes as new commits. Their parents and potentially trees or metadata differ, so IDs normally change. It's not simply dragging the original immutable objects across the graph. Compare logs before and after, keeping in mind that changing published IDs disrupts shared work.

**Purpose:**

Show replayed commits using new parent relationships.

## Rebasing Local Commits onto a New Base

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:53–04:06`

**Visual:**

Hold the actual [git log --format='%h %p %s' -5] result and label it 'Rebase: Replaying Commits and Changing Commit IDs'; circle the evidence just established: Show replayed commits using new parent relationships.. Split to the next terminal/graph at [git switch feature/rounding] labeled 'Rebasing Local Commits onto a New Base'; hide the result for prediction, then reveal the evidence for: Demonstrate safe rebase on a local branch with before/after evidence.

**Script:**

Knowing commits are replayed, let's watch the operation on an unpublished feature branch.

**Purpose:**

Connect evidence from 'Rebase: Replaying Commits and Changing Commit IDs' to 'Rebasing Local Commits onto a New Base', turning the earlier observation into a specific next Git-state check: Demonstrate safe rebase on a local branch with before/after evidence.

### Scene 1 — Graph, patches, and safe stopping points

**Time:** `04:06–05:56`

**Visual:**

Use a disposable clone and an unpublished feature branch; compare terminal output and old/new commits while inspecting `git switch feature/rounding; git status -sb; git rebase main`. Show a visible published-history warning; pause whenever state is unclear.

**Script:**

In the demo, confirm a clean tree and switch to the unpublished feature branch. Rebase that branch onto updated main—not main itself. Watch Git replay the selected commits and then move the feature ref to the new tip. If a change is already present, Git may skip it or report an empty commit. Inspect the graph and code rather than blindly treating every message as an error.

**Purpose:**

Demonstrate safe rebase on a local branch with before/after evidence.

## Resolving Conflicts and Continuing or Aborting Rebase

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:56–06:09`

**Visual:**

Hold the actual [git switch feature/rounding] result and label it 'Rebasing Local Commits onto a New Base'; circle the evidence just established: Demonstrate safe rebase on a local branch with before/after evidence.. Split to the next terminal/graph at [git status] labeled 'Resolving Conflicts and Continuing or Aborting Rebase'; hide the result for prediction, then reveal the evidence for: Show rebase recovery decisions without destructive guesswork.

**Script:**

Rebase replays a sequence, so each conflict must be resolved against the current state.

**Purpose:**

Connect evidence from 'Rebasing Local Commits onto a New Base' to 'Resolving Conflicts and Continuing or Aborting Rebase', turning the earlier observation into a specific next Git-state check: Show rebase recovery decisions without destructive guesswork.

### Scene 1 — Graph, patches, and safe stopping points

**Time:** `06:09–07:59`

**Visual:**

Use TWO independent throwaway feature-branch copies with the same rebase conflict. Copy A resolves the file, stages it, verifies `git status`, then uses `git rebase --continue`; copy B independently inspects `git status` and uses `git rebase --abort`. Highlight before/after commit IDs for both outcomes. Never run continue and abort sequentially in one rebase session or rewrite a published branch.

**Script:**

Replayed changes can collide with updates on main, pausing rebase. Before continuing, inspect status and the conflicted file, resolve the intended behavior, stage the result, then use rebase --continue. In a separate rehearsal use rebase --abort to return to the starting point when supported. Don't reach for reset --hard while the sequencer state is unclear; rebase can replay several commits, not one merge step.

**Purpose:**

Show rebase recovery decisions without destructive guesswork.

## Amending a Local Commit and Its History Consequences

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:59–08:12`

**Visual:**

Hold the actual [git status] result and label it 'Resolving Conflicts and Continuing or Aborting Rebase'; circle the evidence just established: Show rebase recovery decisions without destructive guesswork.. Split to the next terminal/graph at [git commit --amend -m "Document corrected rounding"] labeled 'Amending a Local Commit and Its History Consequences'; hide the result for prediction, then reveal the evidence for: Show amend replacing a commit and why local-only scope matters.

**Script:**

Rebase can replace a series of commits; amend is the smaller last-commit example.

**Purpose:**

Connect evidence from 'Resolving Conflicts and Continuing or Aborting Rebase' to 'Amending a Local Commit and Its History Consequences', turning the earlier observation into a specific next Git-state check: Show amend replacing a commit and why local-only scope matters.

### Scene 1 — Graph, patches, and safe stopping points

**Time:** `08:12–10:02`

**Visual:**

Use a disposable clone and an unpublished feature branch; compare terminal output and old/new commits while inspecting `git commit --amend -m "Document corrected rounding"; git log -2 --format='%h %s'`. Show a visible published-history warning; pause whenever state is unclear.

**Script:**

A recent local commit may have the wrong message or omit a file. Review the staged diff first, then amend HEAD to produce a replacement commit and compare IDs. Even a small message correction can change the commit ID because Git creates a new immutable commit object. Do not casually amend a published commit that others may have based work upon.

**Purpose:**

Show amend replacing a commit and why local-only scope matters.

## Risks of Rewriting Already Shared Commits

<!-- VIDEO_SECTION -->

### Transition

**Time:** `10:02–10:15`

**Visual:**

Hold the actual [git commit --amend -m "Document corrected rounding"] result and label it 'Amending a Local Commit and Its History Consequences'; circle the evidence just established: Show amend replacing a commit and why local-only scope matters.. Split to the next terminal/graph at [git branch -vv] labeled 'Risks of Rewriting Already Shared Commits'; hide the result for prediction, then reveal the evidence for: Explain collaboration risks from replacing already shared commit IDs.

**Script:**

Amend seems simple locally, but shared history changes the consequences.

**Purpose:**

Connect evidence from 'Amending a Local Commit and Its History Consequences' to 'Risks of Rewriting Already Shared Commits', turning the earlier observation into a specific next Git-state check: Explain collaboration risks from replacing already shared commit IDs.

### Scene 1 — Graph, patches, and safe stopping points

**Time:** `10:15–12:05`

**Visual:**

Use a disposable clone and an unpublished feature branch; compare terminal output and old/new commits while inspecting `git branch -vv; git log --graph --oneline --all; git status -sb`. Show a visible published-history warning; pause whenever state is unclear.

**Script:**

Imagine a colleague already fetched the old feature commits. If the author rebases and force-pushes replacement IDs, the colleague's base no longer matches and their graph can diverge or duplicate equivalent changes. That's why the default is to avoid rewriting published history. If a team coordinates an exceptional rewrite, force-with-lease is a carefully reviewed safeguard, not a casual repair button.

**Purpose:**

Explain collaboration risks from replacing already shared commit IDs.

## Evidence of Commit Graph and Content Changes After Rebase

<!-- VIDEO_SECTION -->

### Transition

**Time:** `12:05–12:18`

**Visual:**

Hold the actual [git branch -vv] result and label it 'Risks of Rewriting Already Shared Commits'; circle the evidence just established: Explain collaboration risks from replacing already shared commit IDs.. Split to the next terminal/graph at [git log --graph --oneline --decorate --all] labeled 'Evidence of Commit Graph and Content Changes After Rebase'; hide the result for prediction, then reveal the evidence for: Finish with graph evidence and patch-level verification.

**Script:**

After replacing IDs, we must verify patch equivalence as well as final content.

**Purpose:**

Connect evidence from 'Risks of Rewriting Already Shared Commits' to 'Evidence of Commit Graph and Content Changes After Rebase', turning the earlier observation into a specific next Git-state check: Finish with graph evidence and patch-level verification.

### Scene 1 — Graph, patches, and safe stopping points

**Time:** `12:18–14:08`

**Visual:**

Use a disposable clone and an unpublished feature branch; compare terminal output and old/new commits while inspecting `git log --graph --oneline --decorate --all; git range-diff main old-feature feature/rounding`. Show a visible published-history warning; pause whenever state is unclear.

**Script:**

Put old and new commit graphs side by side and connect commits that represent similar intended patches despite different IDs. Where retained refs make the ranges available, use range-diff to compare the old and new series, then inspect the final content and tests. A tidy straight history does not guarantee correct behavior. In the next chapter reflog will help us examine earlier local ref positions.

**Purpose:**

Finish with graph evidence and patch-level verification.
