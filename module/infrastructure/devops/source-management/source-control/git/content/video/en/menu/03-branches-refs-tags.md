---
video:
  url: ""
---

# Branches, HEAD, References, and Tags

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

## The Commit Graph and Git References

<!-- VIDEO_SECTION -->

### Scene 1 — Read the graph and experiment

**Time:** `00:00–01:33`

**Visual:**

In a disposable repository, show the terminal next to a graph labeling main, feature, HEAD, and tags; inspect safely `git log --oneline --graph --all --decorate`. Freeze matching reference/commit IDs before and after; stop if the working tree is not ready.

**Script:**

Open a small history graph. Dots represent commits, edges point to parents, and branch labels indicate the commits they reference. As we commit on a branch, the graph grows and that branch label advances. A branch is not a separate copied directory of the project: the new line of work shares its earlier history with the original.

**Purpose:**

Show branches as refs to commits rather than copied folders.

## Local Branches and HEAD as Git References

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:33–01:47`

**Visual:**

Hold the actual [git log --oneline --graph --all --decorate] result and label it 'The Commit Graph and Git References'; circle the evidence just established: Show branches as refs to commits rather than copied folders.. Split to the next terminal/graph at [git symbolic-ref --short HEAD] labeled 'Local Branches and HEAD as Git References'; hide the result for prediction, then reveal the evidence for: Distinguish HEAD's current position from the moving branch reference.

**Script:**

A branch is a reference. How do we create and inspect a new line of work?

**Purpose:**

Connect evidence from 'The Commit Graph and Git References' to 'Local Branches and HEAD as Git References', turning the earlier observation into a specific next Git-state check: Distinguish HEAD's current position from the moving branch reference.

### Scene 1 — Read the graph and experiment

**Time:** `01:47–03:20`

**Visual:**

In a disposable repository, show the terminal next to a graph labeling main, feature, HEAD, and tags; inspect safely `git symbolic-ref --short HEAD; git branch -vv; git rev-parse HEAD`. Freeze matching reference/commit IDs before and after; stop if the working tree is not ready.

**Script:**

On our diagram, HEAD names the checked-out branch, which points to a commit. Make a new commit and only the active branch advances. Use symbolic-ref when HEAD is attached and rev-parse to inspect its commit ID. If symbolic-ref fails after detaching HEAD, that result is meaningful evidence of a different state, not a random malfunction.

**Purpose:**

Distinguish HEAD's current position from the moving branch reference.

## Creating, Inspecting, and Deleting Local Branches

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:20–03:34`

**Visual:**

Hold the actual [git symbolic-ref --short HEAD] result and label it 'Local Branches and HEAD as Git References'; circle the evidence just established: Distinguish HEAD's current position from the moving branch reference.. Split to the next terminal/graph at [main] labeled 'Creating, Inspecting, and Deleting Local Branches'; hide the result for prediction, then reveal the evidence for: Demonstrate the branch lifecycle and why safe deletion has a guard.

**Script:**

With several branches available, switching must account for unfinished edits.

**Purpose:**

Connect evidence from 'Local Branches and HEAD as Git References' to 'Creating, Inspecting, and Deleting Local Branches', turning the earlier observation into a specific next Git-state check: Demonstrate the branch lifecycle and why safe deletion has a guard.

### Scene 1 — Read the graph and experiment

**Time:** `03:34–05:07`

**Visual:**

In a disposable repository seeded with a `main` baseline, run `git switch -c feature/rounding`, record a small local demo commit with repository-local identity, and inspect `git branch -vv`. To demonstrate safe deletion, FIRST `git switch main; git merge --ff-only feature/rounding`, verify main contains the work, THEN `git branch -d feature/rounding`. Show the graph and branch list before/after; never use `-D` to bypass the safety check.

**Script:**

Create a feature branch in a throwaway repo, add a tiny commit, then observe main still pointing at the earlier commit. To delete a branch, switch away first; use branch -d only after the work you want is integrated. Git can refuse to delete an unmerged branch. Don't reflexively use -D to suppress that warning: deleting a name is different from deciding that its commits are no longer needed.

**Purpose:**

Demonstrate the branch lifecycle and why safe deletion has a guard.

## Switching Branches with Uncommitted Changes and Safety Constraints

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:07–05:21`

**Visual:**

Hold the actual [main] result and label it 'Creating, Inspecting, and Deleting Local Branches'; circle the evidence just established: Demonstrate the branch lifecycle and why safe deletion has a guard.. Split to the next terminal/graph at [git status -sb] labeled 'Switching Branches with Uncommitted Changes and Safety Constraints'; hide the result for prediction, then reveal the evidence for: Show how Git safeguards uncommitted work during branch switches.

**Script:**

Safe branch switching matters. What changes when HEAD points at a commit directly?

**Purpose:**

Connect evidence from 'Creating, Inspecting, and Deleting Local Branches' to 'Switching Branches with Uncommitted Changes and Safety Constraints', turning the earlier observation into a specific next Git-state check: Show how Git safeguards uncommitted work during branch switches.

### Scene 1 — Read the graph and experiment

**Time:** `05:21–06:54`

**Visual:**

In a disposable repository, show the terminal next to a graph labeling main, feature, HEAD, and tags; inspect safely `git status -sb; git switch main; git diff`. Freeze matching reference/commit IDs before and after; stop if the working tree is not ready.

**Script:**

Edit README without committing and try switching to a branch whose version of that file differs. Git may refuse if checkout would overwrite your work; sometimes it can carry the modification across. Switch neither always saves nor always discards edits. Inspect status first, then choose to commit, stash, or settle the work rather than forcing a destructive checkout.

**Purpose:**

Show how Git safeguards uncommitted work during branch switches.

## Detached HEAD: State and Risks of an Unattached Reference

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:54–07:08`

**Visual:**

Hold the actual [git status -sb] result and label it 'Switching Branches with Uncommitted Changes and Safety Constraints'; circle the evidence just established: Show how Git safeguards uncommitted work during branch switches.. Split to the next terminal/graph at [git switch --detach HEAD~1] labeled 'Detached HEAD: State and Risks of an Unattached Reference'; hide the result for prediction, then reveal the evidence for: Recognize detached HEAD and how to preserve useful work.

**Script:**

When HEAD is not attached, the distinction between moving refs and fixed markers becomes clearer.

**Purpose:**

Connect evidence from 'Switching Branches with Uncommitted Changes and Safety Constraints' to 'Detached HEAD: State and Risks of an Unattached Reference', turning the earlier observation into a specific next Git-state check: Recognize detached HEAD and how to preserve useful work.

### Scene 1 — Read the graph and experiment

**Time:** `07:08–08:41`

**Visual:**

In a disposable repository, show the terminal next to a graph labeling main, feature, HEAD, and tags; inspect safely `git switch --detach HEAD~1; git status -sb; git switch main`. Freeze matching reference/commit IDs before and after; stop if the working tree is not ready.

**Script:**

Switch to an older commit in detached HEAD mode in our demo. You can inspect and even commit here, but no named branch automatically preserves new commits. Before leaving work you want to keep, create a branch at that commit. The detached status is a state of your history position, not a broken repository or a branch literally named detached.

**Purpose:**

Recognize detached HEAD and how to preserve useful work.

## Fixed Tags Versus Branch References That Advance

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:41–08:55`

**Visual:**

Hold the actual [git switch --detach HEAD~1] result and label it 'Detached HEAD: State and Risks of an Unattached Reference'; circle the evidence just established: Recognize detached HEAD and how to preserve useful work.. Split to the next terminal/graph at [git tag -a v1.0 -m "First baseline"] labeled 'Fixed Tags Versus Branch References That Advance'; hide the result for prediction, then reveal the evidence for: Contrast a release marker with a moving development branch.

**Script:**

We have branches and tags. Let's place them alongside HEAD on a single graph.

**Purpose:**

Connect evidence from 'Detached HEAD: State and Risks of an Unattached Reference' to 'Fixed Tags Versus Branch References That Advance', turning the earlier observation into a specific next Git-state check: Contrast a release marker with a moving development branch.

### Scene 1 — Read the graph and experiment

**Time:** `08:55–10:28`

**Visual:**

In a disposable repository, show the terminal next to a graph labeling main, feature, HEAD, and tags; inspect safely `git tag -a v1.0 -m "First baseline"; git show v1.0 --no-patch; git branch -vv`. Freeze matching reference/commit IDs before and after; stop if the working tree is not ready.

**Script:**

A branch usually advances with new commits, while a tag marks a particular historical checkpoint. Create an annotated tag in the demo and inspect its message. A tag isn't your everyday working branch, and it isn't the same thing as a GitHub Release with release notes and assets. Changing a published tag can confuse collaborators who retain the old reference, so treat published markers deliberately.

**Purpose:**

Contrast a release marker with a moving development branch.

## Evidence of HEAD, Branch, and Tag Positions in History

<!-- VIDEO_SECTION -->

### Transition

**Time:** `10:28–10:42`

**Visual:**

Hold the actual [git tag -a v1.0 -m "First baseline"] result and label it 'Fixed Tags Versus Branch References That Advance'; circle the evidence just established: Contrast a release marker with a moving development branch.. Split to the next terminal/graph at [git log --graph --oneline --decorate --all] labeled 'Evidence of HEAD, Branch, and Tag Positions in History'; hide the result for prediction, then reveal the evidence for: Verify several references against the same commit graph.

**Script:**

Before merging, we need reliable evidence of where each branch actually points.

**Purpose:**

Connect evidence from 'Fixed Tags Versus Branch References That Advance' to 'Evidence of HEAD, Branch, and Tag Positions in History', turning the earlier observation into a specific next Git-state check: Verify several references against the same commit graph.

### Scene 1 — Read the graph and experiment

**Time:** `10:42–12:15`

**Visual:**

In a disposable repository, show the terminal next to a graph labeling main, feature, HEAD, and tags; inspect safely `git log --graph --oneline --decorate --all; git show-ref --heads --tags`. Freeze matching reference/commit IDs before and after; stop if the working tree is not ready.

**Script:**

Finish with a graph carrying two branches and an annotated tag. Use show-ref to inspect reference IDs, then align them with log --decorate. Attached HEAD follows the checked-out branch; the tag stays at its marked checkpoint as feature advances. Predict the positions before running commands. If your prediction is wrong, redraw the pointer model rather than memorizing a particular output.

**Purpose:**

Verify several references against the same commit graph.
