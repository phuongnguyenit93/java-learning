---
video:
  url: ""
---

# Stash, Undo, and Recover Safely

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

## Uncommitted Work and Shared-History Classification Before Undoing

<!-- VIDEO_SECTION -->

### Scene 1 — Choose recovery without guessing

**Time:** `00:00–01:48`

**Visual:**

Use separately duplicated disposable repositories and a working-tree/index/HEAD diagram; inspect `git status --short; git log --oneline -4; git branch -vv`. Highlight state before/after and destructive-operation warnings; resolve placeholder IDs from the actual demo log.

**Script:**

Before undoing anything, classify the state: unstaged edits, staged content, local-only commits, or commits others have already fetched. The word 'undo' hides very different risks. Capture status and a short log in the practice repository and record the current commit ID before changing history. There isn't one Git command that safely reverses every kind of work.

**Purpose:**

Establish a state snapshot before choosing an undo mechanism.

## Stash: Shelving and Restoring Uncommitted Work

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:48–02:02`

**Visual:**

Hold the actual [git status --short] result and label it 'Uncommitted Work and Shared-History Classification Before Undoing'; circle the evidence just established: Establish a state snapshot before choosing an undo mechanism.. Split to the next terminal/graph at [git stash push -m "pause pricing fix"] labeled 'Stash: Shelving and Restoring Uncommitted Work'; hide the result for prediction, then reveal the evidence for: Demonstrate stash creation, apply versus pop, and recovery evidence.

**Script:**

We need to set aside uncommitted work; stash is temporary storage, not a normal shared commit.

**Purpose:**

Connect evidence from 'Uncommitted Work and Shared-History Classification Before Undoing' to 'Stash: Shelving and Restoring Uncommitted Work', turning the earlier observation into a specific next Git-state check: Demonstrate stash creation, apply versus pop, and recovery evidence.

### Scene 1 — Choose recovery without guessing

**Time:** `02:02–03:50`

**Visual:**

Use separately duplicated disposable repositories and a working-tree/index/HEAD diagram; inspect `git stash push -m "pause pricing fix"; git stash list; git stash apply`. Highlight state before/after and destructive-operation warnings; resolve placeholder IDs from the actual demo log.

**Script:**

Imagine you're midway through a pricing fix when an urgent task arrives. Inspect status, then stash the uncommitted tracked-file changes. Check stash list before applying the saved work later. Apply keeps the stash entry for safety; pop attempts to apply and remove it when successful. Restoration can conflict with newer edits, so always review the resulting status rather than assuming the workspace was magically restored.

**Purpose:**

Demonstrate stash creation, apply versus pop, and recovery evidence.

## Default Stash Scope and Options for Untracked or Ignored Files

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:50–04:04`

**Visual:**

Hold the actual [git stash push -m "pause pricing fix"] result and label it 'Stash: Shelving and Restoring Uncommitted Work'; circle the evidence just established: Demonstrate stash creation, apply versus pop, and recovery evidence.. Split to the next terminal/graph at [git stash push -u -m "include new notes"] labeled 'Default Stash Scope and Options for Untracked or Ignored Files'; hide the result for prediction, then reveal the evidence for: Expose stash's default scope using a concrete untracked-file example.

**Script:**

Stash looks comprehensive, but new and ignored files aren't included by default.

**Purpose:**

Connect evidence from 'Stash: Shelving and Restoring Uncommitted Work' to 'Default Stash Scope and Options for Untracked or Ignored Files', turning the earlier observation into a specific next Git-state check: Expose stash's default scope using a concrete untracked-file example.

### Scene 1 — Choose recovery without guessing

**Time:** `04:04–05:52`

**Visual:**

Use separately duplicated disposable repositories and a working-tree/index/HEAD diagram; inspect `git stash push -u -m "include new notes"; git stash list; git status -sb`. Highlight state before/after and destructive-operation warnings; resolve placeholder IDs from the actual demo log.

**Script:**

Create a tracked-file modification and a new untracked notes file. The default stash handles tracked changes, not all untracked content; -u includes untracked files, while ignored files need different, more dangerous handling such as -a. Compare status before and after. For irreplaceable data don't rely on a single stash entry as the only backup.

**Purpose:**

Expose stash's default scope using a concrete untracked-file example.

## git restore and Its Effects on the Working Tree and Index

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:52–06:06`

**Visual:**

Hold the actual [git stash push -u -m "include new notes"] result and label it 'Default Stash Scope and Options for Untracked or Ignored Files'; circle the evidence just established: Expose stash's default scope using a concrete untracked-file example.. Split to the next terminal/graph at [git restore -- README.md] labeled 'git restore and Its Effects on the Working Tree and Index'; hide the result for prediction, then reveal the evidence for: Distinguish restoring the working tree from unstaging the index.

**Script:**

After restoring work, sometimes we want to unstage content without discarding the edit.

**Purpose:**

Connect evidence from 'Default Stash Scope and Options for Untracked or Ignored Files' to 'git restore and Its Effects on the Working Tree and Index', turning the earlier observation into a specific next Git-state check: Distinguish restoring the working tree from unstaging the index.

### Scene 1 — Choose recovery without guessing

**Time:** `06:06–07:54`

**Visual:**

Use separately duplicated disposable repositories and a working-tree/index/HEAD diagram; inspect `git restore -- README.md; git restore --staged README.md; git diff --cached`. Highlight state before/after and destructive-operation warnings; resolve placeholder IDs from the actual demo log.

**Script:**

Restore can target different areas depending on its options. In the disposable copy, inspect the diff first, then restore only a deliberately selected file. Restore --staged removes the staged selection while usually keeping the working-tree edit. Plain restore can discard unstaged work by replacing it from the index. These operations can lose content, so they are not harmless cleanup commands for your real source.

**Purpose:**

Distinguish restoring the working tree from unstaging the index.

## git reset Modes and Data Loss Risks

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:54–08:08`

**Visual:**

Hold the actual [git restore -- README.md] result and label it 'git restore and Its Effects on the Working Tree and Index'; circle the evidence just established: Distinguish restoring the working tree from unstaging the index.. Split to the next terminal/graph at [git reset --soft HEAD~1] labeled 'git reset Modes and Data Loss Risks'; hide the result for prediction, then reveal the evidence for: Compare soft/mixed and explain hard-reset risk against all three areas.

**Script:**

Restore handles file contents; reset can also move HEAD, changing history position.

**Purpose:**

Connect evidence from 'git restore and Its Effects on the Working Tree and Index' to 'git reset Modes and Data Loss Risks', turning the earlier observation into a specific next Git-state check: Compare soft/mixed and explain hard-reset risk against all three areas.

### Scene 1 — Choose recovery without guessing

**Time:** `08:08–09:56`

**Visual:**

Use TWO separate disposable clones, each with at least two commits and a clean worktree. Copy A runs only `git reset --soft HEAD~1; git status -sb; git diff --cached`; copy B STARTS FROM THE SAME original commit and runs only `git reset --mixed HEAD~1; git status -sb; git diff --cached`. Compare staged and unstaged state side by side. Do not chain soft and mixed on one branch or reset published history.

**Script:**

On two separate disposable copies, show that soft reset moves HEAD while retaining index and working tree, whereas mixed reset moves HEAD and resets the index while leaving working files. Don't stack both commands on one repo and pretend the results are comparable. Hard reset also rewrites tracked working-tree content, so describe its data-loss risk on a diagram rather than making it a default live demo.

**Purpose:**

Compare soft/mixed and explain hard-reset risk against all three areas.

## git revert for Reversing Shared Changes Without Rewriting Commits

<!-- VIDEO_SECTION -->

### Transition

**Time:** `09:56–10:10`

**Visual:**

Hold the actual [git reset --soft HEAD~1] result and label it 'git reset Modes and Data Loss Risks'; circle the evidence just established: Compare soft/mixed and explain hard-reset risk against all three areas.. Split to the next terminal/graph at [git revert <published-commit-id>] labeled 'git revert for Reversing Shared Changes Without Rewriting Commits'; hide the result for prediction, then reveal the evidence for: Show that revert preserves existing history and adds an inverse commit.

**Script:**

Reset can move branch history. Shared history often needs a new, traceable undo commit.

**Purpose:**

Connect evidence from 'git reset Modes and Data Loss Risks' to 'git revert for Reversing Shared Changes Without Rewriting Commits', turning the earlier observation into a specific next Git-state check: Show that revert preserves existing history and adds an inverse commit.

### Scene 1 — Choose recovery without guessing

**Time:** `10:10–11:58`

**Visual:**

Use separately duplicated disposable repositories and a working-tree/index/HEAD diagram; inspect `git revert <published-commit-id>; git log --oneline -4`. Highlight state before/after and destructive-operation warnings; resolve placeholder IDs from the actual demo log.

**Script:**

For a commit that colleagues may already have fetched, a safer default is to create a new reversing commit with revert. Choose the actual demo commit from log, ensure a clean tree, and inspect the result. The original commit stays in history and the graph gains an explicit undo record. Revert can still conflict, and it won't magically reverse external effects such as production data.

**Purpose:**

Show that revert preserves existing history and adds an inverse commit.

## Local Reflogs, Expiration, and Recovery Limitations

<!-- VIDEO_SECTION -->

### Transition

**Time:** `11:58–12:12`

**Visual:**

Hold the actual [git revert <published-commit-id>] result and label it 'git revert for Reversing Shared Changes Without Rewriting Commits'; circle the evidence just established: Show that revert preserves existing history and adds an inverse commit.. Split to the next terminal/graph at [git reflog -8] labeled 'Local Reflogs, Expiration, and Recovery Limitations'; hide the result for prediction, then reveal the evidence for: Show reflog recovery clues and their local, expiring nature.

**Script:**

Revert leaves a shared record. For accidental local ref moves, we need a way to inspect old positions.

**Purpose:**

Connect evidence from 'git revert for Reversing Shared Changes Without Rewriting Commits' to 'Local Reflogs, Expiration, and Recovery Limitations', turning the earlier observation into a specific next Git-state check: Show reflog recovery clues and their local, expiring nature.

### Scene 1 — Choose recovery without guessing

**Time:** `12:12–14:00`

**Visual:**

Use separately duplicated disposable repositories and a working-tree/index/HEAD diagram; inspect `git reflog -8; git log --oneline --all`. Highlight state before/after and destructive-operation warnings; resolve placeholder IDs from the actual demo log.

**Script:**

Reset and rebase can move references. The local reflog records recent moves of HEAD or branch tips and can reveal an old commit no longer easy to find in regular log. Identify an earlier ID and inspect it before taking any recovery action. Reflog is local and can expire or be pruned, so it's neither a backup nor proof that a remote server still retains the object.

**Purpose:**

Show reflog recovery clues and their local, expiring nature.

## Criteria for Safe Git Recovery Based on Current State

<!-- VIDEO_SECTION -->

### Transition

**Time:** `14:00–14:14`

**Visual:**

Hold the actual [git reflog -8] result and label it 'Local Reflogs, Expiration, and Recovery Limitations'; circle the evidence just established: Show reflog recovery clues and their local, expiring nature.. Split to the next terminal/graph at [git status --short] labeled 'Criteria for Safe Git Recovery Based on Current State'; hide the result for prediction, then reveal the evidence for: Conclude with an evidence-driven safe recovery decision table.

**Script:**

Reflog helps find earlier positions, but the real skill is choosing tools for the right state.

**Purpose:**

Connect evidence from 'Local Reflogs, Expiration, and Recovery Limitations' to 'Criteria for Safe Git Recovery Based on Current State', turning the earlier observation into a specific next Git-state check: Conclude with an evidence-driven safe recovery decision table.

### Scene 1 — Choose recovery without guessing

**Time:** `14:14–16:02`

**Visual:**

Use separately duplicated disposable repositories and a working-tree/index/HEAD diagram; inspect `git status --short; git diff; git diff --cached; git reflog -5`. Highlight state before/after and destructive-operation warnings; resolve placeholder IDs from the actual demo log.

**Script:**

Close with a decision table. For uncommitted edits, inspect diffs before stash or restore; for accidental staging, consider restore --staged. For local-only commits, amend or reset may be appropriate after reviewing their effects; for shared commits, revert usually preserves collaboration history. Flag hard reset, clean, and force push as dangerous, and rehearse in copies. Good recovery has before-and-after evidence, not just an exit code.

**Purpose:**

Conclude with an evidence-driven safe recovery decision table.
