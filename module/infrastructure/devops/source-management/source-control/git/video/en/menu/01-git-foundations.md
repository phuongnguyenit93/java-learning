---
video:
  url: ""
---

# Git Foundations: Repository, States, and Snapshots

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

## Git: Definition and Role in Distributed Version Control

<!-- VIDEO_SECTION -->

### Scene 1 — Observe the evidence: Git: Definition and Role in Distributed Version Control

**Time:** `00:00–01:36`

**Visual:**

In an isolated throwaway repository, show the terminal alongside the relevant three-area/graph diagram. Run or inspect `git init; git status -sb`; freeze the relevant output and compare before/after state.

**Script:**

Imagine two folders containing the same source file. One holds only today's contents; in the other, we initialize Git and can record a history of deliberate changes. Git operates on our machine without requiring a GitHub account. That's the practical meaning of distributed version control: a repository can carry history and record commits locally, not merely upload files.

Our learning route starts with working tree/index/repository and commits, then branches, merges, remotes, rebase, safe undo and an end-to-end practice scenario. Pull request approvals belong to later hosting and team-policy lessons.

**Purpose:**

Distinguish distributed Git from file storage or a hosting platform.

## Snapshots and the Rationale for Recording Versioned States

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:36–01:49`

**Visual:**

Hold the actual [git init] result and label it 'Git: Definition and Role in Distributed Version Control'; circle the evidence just established: Distinguish distributed Git from file storage or a hosting platform.. Split to the next terminal/graph at [git log --oneline] labeled 'Snapshots and the Rationale for Recording Versioned States'; hide the result for prediction, then reveal the evidence for: Show how commits represent versioned project states.

**Script:**

Snapshots need storage as well as editable files; let's separate those responsibilities.

**Purpose:**

Connect evidence from 'Git: Definition and Role in Distributed Version Control' to 'Snapshots and the Rationale for Recording Versioned States', turning the earlier observation into a specific next Git-state check: Show how commits represent versioned project states.

### Scene 1 — Observe the evidence: Snapshots and the Rationale for Recording Versioned States

**Time:** `01:49–03:25`

**Visual:**

In an isolated throwaway repository after `git init`, create `app.txt` and an unchanged `stable.txt`, configure local Git identity, and commit both files as the first snapshot. Then edit only `app.txt`, stage and commit it again while keeping `stable.txt` unchanged. Only after HEAD exists, run `git log --oneline` and `git show --stat HEAD`; compare the two snapshots and the reused unchanged content in the graph. Do not perform these writes in a real working repository.

**Script:**

Create two commits while leaving one file unchanged and editing another. Git treats each commit as a snapshot of the project tree, reusing content objects when possible. Read show and the history graph: a commit describes a project version and its ancestry, not a recording of every keystroke.

**Purpose:**

Show how commits represent versioned project states.

## Git Repository and Working Tree: Concepts and Boundaries

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:25–03:38`

**Visual:**

Hold the actual [git log --oneline] result and label it 'Snapshots and the Rationale for Recording Versioned States'; circle the evidence just established: Show how commits represent versioned project states.. Split to the next terminal/graph at [git rev-parse --show-toplevel] labeled 'Git Repository and Working Tree: Concepts and Boundaries'; hide the result for prediction, then reveal the evidence for: Identify the boundary between editable files and Git's repository database.

**Script:**

Editing a file isn't a commit. Where does Git assemble the next proposed snapshot?

**Purpose:**

Connect evidence from 'Snapshots and the Rationale for Recording Versioned States' to 'Git Repository and Working Tree: Concepts and Boundaries', turning the earlier observation into a specific next Git-state check: Identify the boundary between editable files and Git's repository database.

### Scene 1 — Observe the evidence: Git Repository and Working Tree: Concepts and Boundaries

**Time:** `03:38–05:14`

**Visual:**

In an isolated throwaway repository, show the terminal alongside the relevant three-area/graph diagram. Run or inspect `git rev-parse --show-toplevel; git status --short`; freeze the relevant output and compare before/after state.

**Script:**

On the left, we edit a file in the working tree. Inside .git, the repository records history, objects and references. Change README and run status: the file changes immediately, but no new commit appears. Use rev-parse to locate the repository root; that separation lets Git compare what we are editing with what has been recorded.

**Purpose:**

Identify the boundary between editable files and Git's repository database.

## The Index (Staging Area) in Git's Three-Area Model

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:14–05:27`

**Visual:**

Hold the actual [git rev-parse --show-toplevel] result and label it 'Git Repository and Working Tree: Concepts and Boundaries'; circle the evidence just established: Identify the boundary between editable files and Git's repository database.. Split to the next terminal/graph at [git add README.md] labeled 'The Index (Staging Area) in Git's Three-Area Model'; hide the result for prediction, then reveal the evidence for: Prove the three-area distinction with separate staged and unstaged diffs.

**Script:**

Once the index selects a snapshot, how does a commit connect it to history?

**Purpose:**

Connect evidence from 'Git Repository and Working Tree: Concepts and Boundaries' to 'The Index (Staging Area) in Git's Three-Area Model', turning the earlier observation into a specific next Git-state check: Prove the three-area distinction with separate staged and unstaged diffs.

### Scene 1 — Observe the evidence: The Index (Staging Area) in Git's Three-Area Model

**Time:** `05:27–07:03`

**Visual:**

In an isolated throwaway repository, show the terminal alongside the relevant three-area/graph diagram. Run or inspect `git add README.md; git diff; git diff --cached`; freeze the relevant output and compare before/after state.

**Script:**

Between your working tree and HEAD is the index, or staging area. Edit README, stage it, and then make one more edit. Plain diff shows the later working-tree change, while diff --cached reveals what is currently selected for the next commit. Those three areas can hold different states of the same file, and commit records the staged version.

**Purpose:**

Prove the three-area distinction with separate staged and unstaged diffs.

## Git Objects, Snapshots, and Commit Relationships

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:03–07:16`

**Visual:**

Hold the actual [git add README.md] result and label it 'The Index (Staging Area) in Git's Three-Area Model'; circle the evidence just established: Prove the three-area distinction with separate staged and unstaged diffs.. Split to the next terminal/graph at [git cat-file -p HEAD] labeled 'Git Objects, Snapshots, and Commit Relationships'; hide the result for prediction, then reveal the evidence for: Connect commit trees and parents to inspectable version history.

**Script:**

Objects explain stored history. How does Git describe a file that hasn't been recorded yet?

**Purpose:**

Connect evidence from 'The Index (Staging Area) in Git's Three-Area Model' to 'Git Objects, Snapshots, and Commit Relationships', turning the earlier observation into a specific next Git-state check: Connect commit trees and parents to inspectable version history.

### Scene 1 — Observe the evidence: Git Objects, Snapshots, and Commit Relationships

**Time:** `07:16–08:52`

**Visual:**

In an isolated throwaway repository, show the terminal alongside the relevant three-area/graph diagram. Run or inspect `git cat-file -p HEAD; git ls-tree HEAD`; freeze the relevant output and compare before/after state.

**Script:**

Zoom into cat-file for a sample commit: there's a tree, a parent when applicable, and author information. Then inspect ls-tree to see the referenced snapshot, rather than a list of editing commands. Parent links form the commit graph. We're inspecting objects, not manually changing .git internals.

**Purpose:**

Connect commit trees and parents to inspectable version history.

## Tracked, Untracked, Modified, Staged, and Committed States

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:52–09:05`

**Visual:**

Hold the actual [git cat-file -p HEAD] result and label it 'Git Objects, Snapshots, and Commit Relationships'; circle the evidence just established: Connect commit trees and parents to inspectable version history.. Split to the next terminal/graph at [git status --short] labeled 'Tracked, Untracked, Modified, Staged, and Committed States'; hide the result for prediction, then reveal the evidence for: Read tracked, untracked, and staged states from Git output.

**Script:**

We've seen file states. Now let's distinguish starting fresh from copying an existing history.

**Purpose:**

Connect evidence from 'Git Objects, Snapshots, and Commit Relationships' to 'Tracked, Untracked, Modified, Staged, and Committed States', turning the earlier observation into a specific next Git-state check: Read tracked, untracked, and staged states from Git output.

### Scene 1 — Observe the evidence: Tracked, Untracked, Modified, Staged, and Committed States

**Time:** `09:05–10:41`

**Visual:**

In an isolated throwaway repository, show the terminal alongside the relevant three-area/graph diagram. Run or inspect `git status --short; git add notes.txt; git status -sb`; freeze the relevant output and compare before/after state.

**Script:**

Create notes.txt and observe the question marks for an untracked file. After add, Git stages its contents; edit it again and status can show both staged and unstaged changes. A committed, tracked file can still be modified in the working tree. Rather than calling everything 'saved', read the status columns and ask which version lives in which area.

**Purpose:**

Read tracked, untracked, and staged states from Git output.

## Initializing a Repository and Cloning Existing History

<!-- VIDEO_SECTION -->

### Transition

**Time:** `10:41–10:54`

**Visual:**

Hold the actual [git status --short] result and label it 'Tracked, Untracked, Modified, Staged, and Committed States'; circle the evidence just established: Read tracked, untracked, and staged states from Git output.. Split to the next terminal/graph at [git init scratch-demo] labeled 'Initializing a Repository and Cloning Existing History'; hide the result for prediction, then reveal the evidence for: Distinguish a new repository from a copy of existing history.

**Script:**

We've assembled the pieces. Let's follow one file all the way from editing to a commit.

**Purpose:**

Connect evidence from 'Tracked, Untracked, Modified, Staged, and Committed States' to 'Initializing a Repository and Cloning Existing History', turning the earlier observation into a specific next Git-state check: Distinguish a new repository from a copy of existing history.

### Scene 1 — Observe the evidence: Initializing a Repository and Cloning Existing History

**Time:** `10:54–12:30`

**Visual:**

In a new disposable parent directory, run `git init scratch-demo`, seed a real revision with `git -C scratch-demo -c user.name=Demo -c user.email=demo@example.invalid commit --allow-empty -m "Seed history"`, then `git clone ./scratch-demo scratch-copy` and `git -C scratch-copy log --oneline`. Split the two terminals, highlight the matching source/clone commit ID; never touch the learner's working repository.

**Script:**

Side by side, init starts a new repository without an initial commit; clone copies an existing repository and its history from a source. Here we use a local path, not a network service, and create a source commit before cloning. Clone doesn't mean 'create a new branch'. After each operation inspect status and log in the correct directory.

**Purpose:**

Distinguish a new repository from a copy of existing history.

## Example: Tracing a File Through Git's Three Areas

<!-- VIDEO_SECTION -->

### Transition

**Time:** `12:30–12:43`

**Visual:**

Hold the actual [git init scratch-demo] result and label it 'Initializing a Repository and Cloning Existing History'; circle the evidence just established: Distinguish a new repository from a copy of existing history.. Split to the next terminal/graph at [git status -sb] labeled 'Example: Tracing a File Through Git's Three Areas'; hide the result for prediction, then reveal the evidence for: Validate the lifecycle with before-and-after state evidence.

**Script:**

That compact sequence tells a full story. Where do we look up syntax and safety options reliably?

**Purpose:**

Connect evidence from 'Initializing a Repository and Cloning Existing History' to 'Example: Tracing a File Through Git's Three Areas', turning the earlier observation into a specific next Git-state check: Validate the lifecycle with before-and-after state evidence.

### Scene 1 — Observe the evidence: Example: Tracing a File Through Git's Three Areas

**Time:** `12:43–14:19`

**Visual:**

In an isolated throwaway repository, show the terminal alongside the relevant three-area/graph diagram. Run or inspect `git status -sb; git diff; git add README.md; git diff --cached; git commit -m "Record baseline"`; freeze the relevant output and compare before/after state.

**Script:**

Keep the same file on screen. After editing, diff compares working tree with index; after add, diff --cached compares index with HEAD. Commit in this disposable repository and check status: it should be clean if no further edits remain. Each command answers a different question, and edits made after staging are not silently included in the commit.

**Purpose:**

Validate the lifecycle with before-and-after state evidence.

## Git Command Quick Reference and Official Documentation

<!-- VIDEO_SECTION -->

### Transition

**Time:** `14:19–14:32`

**Visual:**

Hold the actual [git status -sb] result and label it 'Example: Tracing a File Through Git's Three Areas'; circle the evidence just established: Validate the lifecycle with before-and-after state evidence.. Split to the next terminal/graph at [git help status] labeled 'Git Command Quick Reference and Official Documentation'; hide the result for prediction, then reveal the evidence for: Leave an official command lookup method and a safe foundation for the next chapter.

**Script:**
We have now followed one file through the working tree, index, and commit. To repeat these checks without the recording, we need to find authoritative command references and read warnings before experimenting.



**Purpose:**

Connect evidence from 'Example: Tracing a File Through Git's Three Areas' to 'Git Command Quick Reference and Official Documentation', turning the earlier observation into a specific next Git-state check: Leave an official command lookup method and a safe foundation for the next chapter.

### Scene 1 — Observe the evidence: Git Command Quick Reference and Official Documentation

**Time:** `14:32–16:08`

**Visual:**

In a disposable repository, run `git help status`, `git status -h`, and `git help restore` separately; open https://git-scm.com/docs in a browser beside the terminal. Highlight Usage, Options, and warnings about discarding changes.

**Script:**

End with a three-column reference: command, question, and observable evidence. Status asks what's changed; diff asks how; add selects content; commit records a snapshot; log reads its history. Open git help status or git-scm.com/docs for authoritative flags. Never treat reset --hard or clean -fd as harmless exploratory commands—read the help first, use a disposable clone, and check status before and after.

**Purpose:**

Leave an official command lookup method and a safe foundation for the next chapter.
