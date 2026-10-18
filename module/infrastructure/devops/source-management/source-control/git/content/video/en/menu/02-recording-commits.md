---
video:
  url: ""
---

# Record Changes and Inspect Commit History

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

## Inspecting Change States with git status

<!-- VIDEO_SECTION -->

### Scene 1 — Guided evidence: Inspecting Change States with git status

**Time:** `00:00–01:34`

**Visual:**

In a disposable repository, place the terminal beside the README editor and inspect `git status -sb; git status`. Highlight the exact output difference before and after each relevant action.

**Script:**

Before committing anything, ask what is actually different. Status separates untracked files, staged changes, and unstaged modifications. Create a new file and edit a tracked file so both appear, then read the two entries carefully. Check the repository and branch in the short status line; stage only after you know which work belongs to this change.

**Purpose:**

Build the habit of inspecting state before staging.

## .gitignore Rules and File Tracking Boundaries

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:34–01:48`

**Visual:**

Hold the actual [git status -sb] result and label it 'Inspecting Change States with git status'; circle the evidence just established: Build the habit of inspecting state before staging.. Split to the next terminal/graph at [printf 'dist/\n.env\n' > .gitignore] labeled '.gitignore Rules and File Tracking Boundaries'; hide the result for prediction, then reveal the evidence for: Show that ignore patterns do not erase tracked content or history.

**Script:**

Status can expose files that should never enter a commit. Set that boundary before staging.

**Purpose:**

Connect evidence from 'Inspecting Change States with git status' to '.gitignore Rules and File Tracking Boundaries', turning the earlier observation into a specific next Git-state check: Show that ignore patterns do not erase tracked content or history.

### Scene 1 — Guided evidence: .gitignore Rules and File Tracking Boundaries

**Time:** `01:48–03:22`

**Visual:**

In a disposable repository, place the terminal beside the README editor and inspect `printf 'dist/\n.env\n' > .gitignore; git status --short`. Highlight the exact output difference before and after each relevant action.

**Script:**

Place a generated dist folder and an .env file beside source files. Add ignore patterns and compare status before and after: new matching files are normally hidden from untracked results. But an already tracked secret is not untracked by adding an ignore pattern, and Git history still exists. Review which files are excluded before committing an ignore rule.

**Purpose:**

Show that ignore patterns do not erase tracked content or history.

## Differences Between Working Tree and Staged Content

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:22–03:36`

**Visual:**

Hold the actual [printf 'dist/\n.env\n' > .gitignore] result and label it '.gitignore Rules and File Tracking Boundaries'; circle the evidence just established: Show that ignore patterns do not erase tracked content or history.. Split to the next terminal/graph at [git diff -- README.md] labeled 'Differences Between Working Tree and Staged Content'; hide the result for prediction, then reveal the evidence for: Prove the staged-versus-unstaged distinction using one file.

**Script:**

A tracked file can have two diffs. Which one is about to be recorded?

**Purpose:**

Connect evidence from '.gitignore Rules and File Tracking Boundaries' to 'Differences Between Working Tree and Staged Content', turning the earlier observation into a specific next Git-state check: Prove the staged-versus-unstaged distinction using one file.

### Scene 1 — Guided evidence: Differences Between Working Tree and Staged Content

**Time:** `03:36–05:10`

**Visual:**

In a disposable repository, place the terminal beside the README editor and inspect `git diff -- README.md; git diff --cached -- README.md`. Highlight the exact output difference before and after each relevant action.

**Script:**

Stage the first README edit, then change it again. Place ordinary diff above diff --cached. The top compares your working tree against the index, while the bottom compares the index against HEAD. They are different comparisons, not duplicate reports. Before committing, ask the viewer which version the next snapshot will include and verify the prediction.

**Purpose:**

Prove the staged-versus-unstaged distinction using one file.

## Staging Selected Content for the Next Commit

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:10–05:24`

**Visual:**

Hold the actual [git diff -- README.md] result and label it 'Differences Between Working Tree and Staged Content'; circle the evidence just established: Prove the staged-versus-unstaged distinction using one file.. Split to the next terminal/graph at [git add -p README.md] labeled 'Staging Selected Content for the Next Commit'; hide the result for prediction, then reveal the evidence for: Demonstrate an intentional, reviewable staged selection.

**Script:**

The two diffs show why staging is a choice rather than a bulk upload.

**Purpose:**

Connect evidence from 'Differences Between Working Tree and Staged Content' to 'Staging Selected Content for the Next Commit', turning the earlier observation into a specific next Git-state check: Demonstrate an intentional, reviewable staged selection.

### Scene 1 — Guided evidence: Staging Selected Content for the Next Commit

**Time:** `05:24–06:58`

**Visual:**

In a disposable repository, place the terminal beside the README editor and inspect `git add -p README.md; git diff --cached`. Highlight the exact output difference before and after each relevant action.

**Script:**

Suppose README contains a real correction and an unrelated formatting change. Use add -p to inspect and accept only the relevant hunk, then reread diff --cached. The next commit should express one understandable intent, not everything that happens to be in the directory. If a hunk is awkward, stop and inspect; don't blindly stage every file to make status quiet.

**Purpose:**

Demonstrate an intentional, reviewable staged selection.

## Commits: Intentional Snapshots and Change Messages

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:58–07:12`

**Visual:**

Hold the actual [git add -p README.md] result and label it 'Staging Selected Content for the Next Commit'; circle the evidence just established: Demonstrate an intentional, reviewable staged selection.. Split to the next terminal/graph at [git commit -m "Validate negative totals"] labeled 'Commits: Intentional Snapshots and Change Messages'; hide the result for prediction, then reveal the evidence for: Connect the staged snapshot to a meaningful commit record.

**Script:**

After selecting the fix, we need a historical checkpoint another developer can understand.

**Purpose:**

Connect evidence from 'Staging Selected Content for the Next Commit' to 'Commits: Intentional Snapshots and Change Messages', turning the earlier observation into a specific next Git-state check: Connect the staged snapshot to a meaningful commit record.

### Scene 1 — Guided evidence: Commits: Intentional Snapshots and Change Messages

**Time:** `07:12–08:46`

**Visual:**

In a disposable repository, place the terminal beside the README editor and inspect `git commit -m "Validate negative totals"; git show --stat HEAD`. Highlight the exact output difference before and after each relevant action.

**Script:**

Once the staged diff matches your intention, commit with a message about the behavior being corrected rather than 'fix stuff'. Use git show --stat to confirm which files entered the new commit. Git recorded the index snapshot at that moment; later working-tree changes can remain. A successful commit proves history was written, not that your code passed its tests.

**Purpose:**

Connect the staged snapshot to a meaningful commit record.

## git log History, Commit IDs, and Parent Relationships

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:46–09:00`

**Visual:**

Hold the actual [git commit -m "Validate negative totals"] result and label it 'Commits: Intentional Snapshots and Change Messages'; circle the evidence just established: Connect the staged snapshot to a meaningful commit record.. Split to the next terminal/graph at [git log --oneline --graph --decorate -5] labeled 'git log History, Commit IDs, and Parent Relationships'; hide the result for prediction, then reveal the evidence for: Read commit IDs and parents as graph evidence.

**Script:**

A single commit is useful. How do several commits form a readable history?

**Purpose:**

Connect evidence from 'Commits: Intentional Snapshots and Change Messages' to 'git log History, Commit IDs, and Parent Relationships', turning the earlier observation into a specific next Git-state check: Read commit IDs and parents as graph evidence.

### Scene 1 — Guided evidence: git log History, Commit IDs, and Parent Relationships

**Time:** `09:00–10:34`

**Visual:**

In a disposable repository, place the terminal beside the README editor and inspect `git log --oneline --graph --decorate -5; git show --pretty=raw HEAD`. Highlight the exact output difference before and after each relevant action.

**Script:**

Show three commits and their parent edges. The one-line graph helps locate a checkpoint, while show --pretty=raw reveals its full ID and parent. A commit ID identifies its content and metadata; it isn't a sequential version number. Trace HEAD to the current commit and then walk back through parents: these links will explain branches and merges later.

**Purpose:**

Read commit IDs and parents as graph evidence.

## Comparing Commit History Points and File Versions

<!-- VIDEO_SECTION -->

### Transition

**Time:** `10:34–10:48`

**Visual:**

Hold the actual [git log --oneline --graph --decorate -5] result and label it 'git log History, Commit IDs, and Parent Relationships'; circle the evidence just established: Read commit IDs and parents as graph evidence.. Split to the next terminal/graph at [git diff HEAD~1 HEAD -- README.md] labeled 'Comparing Commit History Points and File Versions'; hide the result for prediction, then reveal the evidence for: Separate historical comparison from working-tree inspection.

**Script:**

History has IDs and parents. We can now ask precisely how a file changed between them.

**Purpose:**

Connect evidence from 'git log History, Commit IDs, and Parent Relationships' to 'Comparing Commit History Points and File Versions', turning the earlier observation into a specific next Git-state check: Separate historical comparison from working-tree inspection.

### Scene 1 — Guided evidence: Comparing Commit History Points and File Versions

**Time:** `10:48–12:22`

**Visual:**

In a disposable repository, place the terminal beside the README editor and inspect `git diff HEAD~1 HEAD -- README.md; git show HEAD~1:README.md`. Highlight the exact output difference before and after each relevant action.

**Script:**

To understand what actually changed, don't rely on the commit message. Compare the same file at two valid commits, then inspect the earlier version directly. The diagram shows two recorded snapshots, not today's working tree. If your repository has only a root commit, HEAD~1 won't exist: pick two real history points from log instead of copying the command blindly.

**Purpose:**

Separate historical comparison from working-tree inspection.

## Evidence of Committed Content Versus Remaining Changes

<!-- VIDEO_SECTION -->

### Transition

**Time:** `12:22–12:36`

**Visual:**

Hold the actual [git diff HEAD~1 HEAD -- README.md] result and label it 'Comparing Commit History Points and File Versions'; circle the evidence just established: Separate historical comparison from working-tree inspection.. Split to the next terminal/graph at [git status -sb] labeled 'Evidence of Committed Content Versus Remaining Changes'; hide the result for prediction, then reveal the evidence for: Build an evidence checklist without confusing a clean tree with correct code.

**Script:**

Comparing saved checkpoints isn't enough. We also need to verify the work left out.

**Purpose:**

Connect evidence from 'Comparing Commit History Points and File Versions' to 'Evidence of Committed Content Versus Remaining Changes', turning the earlier observation into a specific next Git-state check: Build an evidence checklist without confusing a clean tree with correct code.

### Scene 1 — Guided evidence: Evidence of Committed Content Versus Remaining Changes

**Time:** `12:36–14:10`

**Visual:**

In a disposable repository, place the terminal beside the README editor and inspect `git status -sb; git diff; git diff --cached; git show --stat HEAD`. Highlight the exact output difference before and after each relevant action.

**Script:**

Pause at four outputs from the same repository. Status reports outstanding work; the two diffs separate staged from unstaged material; show describes what the latest commit actually recorded. A commit can succeed while leaving more changes behind. If status still shows modifications, decide whether they belong to a later task or were accidentally omitted—never stage everything just to make the screen look clean.

**Purpose:**

Build an evidence checklist without confusing a clean tree with correct code.
