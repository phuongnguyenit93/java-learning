---
video:
  url: ""
---

# Merge Branches and Resolve Conflicts

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

## History Divergence During Parallel Branch Development

<!-- VIDEO_SECTION -->

### Scene 1 — Merge mechanics and evidence

**Time:** `00:00–01:42`

**Visual:**

Two branches in an isolated practice repo, terminal beside the before/after graph; inspect `git log --graph --oneline --all --decorate`. Freeze relevant output and do not run this against production branches.

**Script:**

Two developers commit from the same common ancestor and the graph forks. This divergence isn't a failure; it means integration must account for two lines of history. Highlight the common ancestor and both tips, then predict which commits belong to each branch. Merging is more than pasting two file contents together: ancestry determines the relationship being reconciled.

**Purpose:**

Recognize divergence before choosing merge mechanics.

## Fast-Forward Merge and Branch Reference Movement

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:42–01:55`

**Visual:**

Hold the actual [git log --graph --oneline --all --decorate] result and label it 'History Divergence During Parallel Branch Development'; circle the evidence just established: Recognize divergence before choosing merge mechanics.. Split to the next terminal/graph at [git switch main] labeled 'Fast-Forward Merge and Branch Reference Movement'; hide the result for prediction, then reveal the evidence for: Prove that fast-forward need not create a merge commit.

**Script:**

When one tip is still an ancestor, a pointer move works. What if both sides have added commits?

**Purpose:**

Connect evidence from 'History Divergence During Parallel Branch Development' to 'Fast-Forward Merge and Branch Reference Movement', turning the earlier observation into a specific next Git-state check: Prove that fast-forward need not create a merge commit.

### Scene 1 — Merge mechanics and evidence

**Time:** `01:55–03:37`

**Visual:**

Switch to a separately seeded disposable example (not the diverged history from Scene 1): `feature/rounding` has one new commit, while `main` is still its ancestor. Beside the before/after graph, inspect `git switch main; git merge feature/rounding; git log --graph --oneline --all`. Freeze relevant output and do not run this against production branches.

**Script:**

Unlike our earlier diverged graph, this independent demonstration starts with main still an ancestor of the feature tip. A merge can therefore simply advance main to the feature commit. Pause before running the command and ask whether a new merge node is needed. Inspect the graph afterward: a fast-forward adds no separate merge commit. This is determined by ancestry, not by the names of branches.

**Purpose:**

Prove that fast-forward need not create a merge commit.

## Merge Base and Three-Way Merge Mechanics

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:37–03:50`

**Visual:**

Hold the actual [git switch main] result and label it 'Fast-Forward Merge and Branch Reference Movement'; circle the evidence just established: Prove that fast-forward need not create a merge commit.. Split to the next terminal/graph at [git merge-base main feature/rounding] labeled 'Merge Base and Three-Way Merge Mechanics'; hide the result for prediction, then reveal the evidence for: Explain three-way integration using the base and both tips.

**Script:**

Fast-forward is the simple case. Diverged branches require a common-ancestor snapshot.

**Purpose:**

Connect evidence from 'Fast-Forward Merge and Branch Reference Movement' to 'Merge Base and Three-Way Merge Mechanics', turning the earlier observation into a specific next Git-state check: Explain three-way integration using the base and both tips.

### Scene 1 — Merge mechanics and evidence

**Time:** `03:50–05:32`

**Visual:**

Two branches in an isolated practice repo, terminal beside the before/after graph; inspect `git merge-base main feature/rounding; git log --graph --oneline --all`. Freeze relevant output and do not run this against production branches.

**Script:**

Let main and feature each receive an independent commit, then locate merge-base. Git compares the common ancestor snapshot with each tip so it can combine compatible edits. If both sides alter the same area incompatibly, a human decision is needed. Draw two arrows from merge-base to the tips: a single tip-to-tip diff doesn't explain the whole integration context.

**Purpose:**

Explain three-way integration using the base and both tips.

## Merge Commits and Multiple Parent Relationships

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:32–05:45`

**Visual:**

Hold the actual [git merge-base main feature/rounding] result and label it 'Merge Base and Three-Way Merge Mechanics'; circle the evidence just established: Explain three-way integration using the base and both tips.. Split to the next terminal/graph at [git merge --no-ff feature/rounding] labeled 'Merge Commits and Multiple Parent Relationships'; hide the result for prediction, then reveal the evidence for: Verify merge commits by parent relationships, not just a command name.

**Script:**

Three-way integration can create a recorded meeting point between histories.

**Purpose:**

Connect evidence from 'Merge Base and Three-Way Merge Mechanics' to 'Merge Commits and Multiple Parent Relationships', turning the earlier observation into a specific next Git-state check: Verify merge commits by parent relationships, not just a command name.

### Scene 1 — Merge mechanics and evidence

**Time:** `05:45–07:27`

**Visual:**

Two branches in an isolated practice repo, terminal beside the before/after graph; inspect `git merge --no-ff feature/rounding; git show --pretty=raw HEAD`. Freeze relevant output and do not run this against production branches.

**Script:**

In our diverged demo, a successful merge may produce a new commit with two parents. Inspect show --pretty=raw and mark both parent IDs on the graph. The merge commit records an integration point; its tree records the resulting content. Not every merge creates this node because fast-forward may apply. Confirm the actual topology after the operation.

**Purpose:**

Verify merge commits by parent relationships, not just a command name.

## File Conflict Indicators and In-Progress Merge States

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:27–07:40`

**Visual:**

Hold the actual [git merge --no-ff feature/rounding] result and label it 'Merge Commits and Multiple Parent Relationships'; circle the evidence just established: Verify merge commits by parent relationships, not just a command name.. Split to the next terminal/graph at [git status] labeled 'File Conflict Indicators and In-Progress Merge States'; hide the result for prediction, then reveal the evidence for: Identify an unresolved merge using status, the index, and conflict markers.

**Script:**

Content doesn't always combine cleanly. Let's inspect Git's conflict evidence rather than pick a winner blindly.

**Purpose:**

Connect evidence from 'Merge Commits and Multiple Parent Relationships' to 'File Conflict Indicators and In-Progress Merge States', turning the earlier observation into a specific next Git-state check: Identify an unresolved merge using status, the index, and conflict markers.

### Scene 1 — Merge mechanics and evidence

**Time:** `07:40–09:22`

**Visual:**

Two branches in an isolated practice repo, terminal beside the before/after graph; inspect `git status; git ls-files -u; git diff -- README.md`. Freeze relevant output and do not run this against production branches.

**Script:**

Now make both branches change the same line incompatibly. Git stops rather than pretending it knows the correct business rule. Status reports the in-progress merge; ls-files -u exposes unresolved index stages, and the file shows conflict markers. Those markers are guides for human resolution, not valid final source that should be committed unchanged.

**Purpose:**

Identify an unresolved merge using status, the index, and conflict markers.

## Resolving, Completing, or Aborting a Merge

<!-- VIDEO_SECTION -->

### Transition

**Time:** `09:22–09:35`

**Visual:**

Hold the actual [git status] result and label it 'File Conflict Indicators and In-Progress Merge States'; circle the evidence just established: Identify an unresolved merge using status, the index, and conflict markers.. Split to the next terminal/graph at [README.md] labeled 'Resolving, Completing, or Aborting a Merge'; hide the result for prediction, then reveal the evidence for: Demonstrate completion versus abort with explicit safeguards.

**Script:**

Detecting a conflict is only the start; resolution requires correct content and proof.

**Purpose:**

Connect evidence from 'File Conflict Indicators and In-Progress Merge States' to 'Resolving, Completing, or Aborting a Merge', turning the earlier observation into a specific next Git-state check: Demonstrate completion versus abort with explicit safeguards.

### Scene 1 — Merge mechanics and evidence

**Time:** `09:35–11:17`

**Visual:**

Split the screen into TWO independent disposable copies starting from the same conflict. Left: resolve `README.md`, run `git diff --check; git add README.md; git status`, validate behavior and `git merge --continue` only while a merge is in progress. Right: WITHOUT staging or continuing the left copy, separately inspect `git status` and use `git merge --abort`; compare pre/post graphs and state the uncommitted-work recovery limit. Never run both paths as one command sequence.

**Script:**

In the conflicted demo, inspect the intended behavior before editing the file; merely deleting conflict markers does not resolve the business decision. Run diff --check, stage the resolved file, and use status to see whether Git awaits the merge commit. Test the result before committing. In a separate duplicate experiment, merge --abort can abandon the in-progress operation, but never treat it as a universal backup for preexisting uncommitted work.

**Purpose:**

Demonstrate completion versus abort with explicit safeguards.

## Cherry-Picking Individual Commits Versus Merging a Branch

<!-- VIDEO_SECTION -->

### Transition

**Time:** `11:17–11:30`

**Visual:**

Hold the actual [README.md] result and label it 'Resolving, Completing, or Aborting a Merge'; circle the evidence just established: Demonstrate completion versus abort with explicit safeguards.. Split to the next terminal/graph at [git log --oneline feature/rounding] labeled 'Cherry-Picking Individual Commits Versus Merging a Branch'; hide the result for prediction, then reveal the evidence for: Distinguish selected commit application from whole-branch integration.

**Script:**

Sometimes we need one change rather than an entire branch's history.

**Purpose:**

Connect evidence from 'Resolving, Completing, or Aborting a Merge' to 'Cherry-Picking Individual Commits Versus Merging a Branch', turning the earlier observation into a specific next Git-state check: Distinguish selected commit application from whole-branch integration.

### Scene 1 — Merge mechanics and evidence

**Time:** `11:30–13:12`

**Visual:**

Two branches in an isolated practice repo, terminal beside the before/after graph; inspect `git log --oneline feature/rounding; git cherry-pick <safe-commit-id>`. Freeze relevant output and do not run this against production branches.

**Script:**

Place two graphs side by side: merge normally integrates a line of history, while cherry-pick applies changes from a selected commit onto the current line. The picked result generally gets a new ID because its parent differs, and conflicts can still arise. Select a real commit ID from the demo log, inspect status afterward, and verify dependencies—picking one change doesn't magically include everything it needs.

**Purpose:**

Distinguish selected commit application from whole-branch integration.

## Evidence of Content and Commit History After a Merge

<!-- VIDEO_SECTION -->

### Transition

**Time:** `13:12–13:25`

**Visual:**

Hold the actual [git log --oneline feature/rounding] result and label it 'Cherry-Picking Individual Commits Versus Merging a Branch'; circle the evidence just established: Distinguish selected commit application from whole-branch integration.. Split to the next terminal/graph at [git status -sb] labeled 'Evidence of Content and Commit History After a Merge'; hide the result for prediction, then reveal the evidence for: Combine graph, content, and behavior checks after integration.

**Script:**

Both merge and cherry-pick need final evidence; a clean status does not replace behavioral verification.

**Purpose:**

Connect evidence from 'Cherry-Picking Individual Commits Versus Merging a Branch' to 'Evidence of Content and Commit History After a Merge', turning the earlier observation into a specific next Git-state check: Combine graph, content, and behavior checks after integration.

### Scene 1 — Merge mechanics and evidence

**Time:** `13:25–15:07`

**Visual:**

Two branches in an isolated practice repo, terminal beside the before/after graph; inspect `git status -sb; git log --graph --oneline --decorate -8; git show --stat HEAD`. Freeze relevant output and do not run this against production branches.

**Script:**

Use three independent checks after integration: status reveals unfinished operations, log --graph confirms the intended topology, and show identifies recorded content. Run the relevant rounding tests including edge cases. A merge command returning success does not prove the business result is correct; don't report integration complete while conflicts or uncommitted resolution remain.

**Purpose:**

Combine graph, content, and behavior checks after integration.
