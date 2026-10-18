---
video:
  url: ""
---

# Practice a Complete Git Change Lifecycle

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

## Example: Tracing a File from Editing to Commit

<!-- VIDEO_SECTION -->

### Scene 1 — One scenario, traceable evidence

**Time:** `00:00–01:52`

**Visual:**

Use separate disposable clones of the rounding-fix example; present terminal, diffs, graph, and refs. Inspect `git status -sb; git diff; git add README.md; git diff --cached; git commit -m "Fix rounding"`. Pause on decisive output and compare against the previous snapshot, never operating on real source.

**Script:**

Open one continuing scenario: a rounding bug in a disposable Git repository. At first status shows an unstaged change. Read diff to understand the exact edit, stage only the relevant file, inspect diff --cached, and commit with a meaningful fix message. Freeze four before-and-after views and identify which area changes at each step; successfully recording a commit never substitutes for business tests.

**Purpose:**

Reinforce the three-area model through one continuous change.

## Example: Following a Commit Across Branches and Remote-Tracking References

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:52–02:04`

**Visual:**

Hold the actual [git status -sb] result and label it 'Example: Tracing a File from Editing to Commit'; circle the evidence just established: Reinforce the three-area model through one continuous change.. Split to the next terminal/graph at [git init --bare ../remote-demo.git] labeled 'Example: Following a Commit Across Branches and Remote-Tracking References'; hide the result for prediction, then reveal the evidence for: Connect commits and branches to remotes without inventing PR APIs.

**Script:**

We have a commit; collaboration introduces a branch and a repository to exchange it with.

**Purpose:**

Connect evidence from 'Example: Tracing a File from Editing to Commit' to 'Example: Following a Commit Across Branches and Remote-Tracking References', turning the earlier observation into a specific next Git-state check: Connect commits and branches to remotes without inventing PR APIs.

### Scene 1 — One scenario, traceable evidence

**Time:** `02:04–03:56`

**Visual:**

Continue the Chapter 8 demo from a real baseline commit on `main`, all inside a disposable parent. Create a sibling bare server with `git init --bare ../remote-demo.git`, then set `git remote add origin ../remote-demo.git` only if origin does not already exist. BEFORE creating a feature, explicitly run `git push -u origin main; git fetch origin`; verify `git ls-remote origin main` and local `origin/main` both resolve. Now run `git switch -c feature/rounding`, make and commit a genuine rounding edit, then `git push -u origin feature/rounding; git ls-remote origin`. Show both remote branches; do not assume that publishing only the feature automatically creates remote main.

**Script:**

After the baseline commit, connect the separate bare remote and publish main explicitly to establish a real `origin/main`. Only then create a feature branch, record its correction and publish that feature. Compare the local branch, remote feature and main refs after the push. The server now has both named branches, and fetching can show real divergence later. Publishing a branch does not automatically create a pull request: hosted approval remains outside native Git.

**Purpose:**

Connect commits and branches to remotes without inventing PR APIs.

## Comparison of Merge and Rebase Integration Mechanics

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:56–04:08`

**Visual:**

Hold the actual [git init --bare ../remote-demo.git] result and label it 'Example: Following a Commit Across Branches and Remote-Tracking References'; circle the evidence just established: Connect commits and branches to remotes without inventing PR APIs.. Split to the next terminal/graph at [git fetch origin] labeled 'Comparison of Merge and Rebase Integration Mechanics'; hide the result for prediction, then reveal the evidence for: Contrast merge and rebase mechanics in one shared scenario.

**Script:**

The remote may have advanced. We must choose integration mechanics from actual history.

**Purpose:**

Connect evidence from 'Example: Following a Commit Across Branches and Remote-Tracking References' to 'Comparison of Merge and Rebase Integration Mechanics', turning the earlier observation into a specific next Git-state check: Contrast merge and rebase mechanics in one shared scenario.

### Scene 1 — One scenario, traceable evidence

**Time:** `04:08–06:00`

**Visual:**

Before the integration demo, clone the already seeded bare remote into a SEPARATE disposable `colleague-copy`, check out `main`, make a nonconflicting change, commit it with a demo identity, and push `main` to the bare remote. Keep the author's `feature/rounding` checked out in the original demo. Now run `git fetch origin; git log --graph --oneline --decorate --all; git merge origin/main` in the author copy. Freeze the graph before the merge: both feature and `origin/main` must have distinct commits. A successful merge now has real ancestry to demonstrate; compare it to a hypothetical rebase graph without rebasing shared feature history.

**Script:**

Our colleague copy really updates remote main while the author works on feature. Fetch and inspect the two distinct tips before integration. Merge preserves an explicit view of both lines, whereas rebase could replay unpublished work under a suitable team policy. Here we run merge and inspect the result rather than rebasing a published branch. Choosing trunk-based or Git Flow is a team-policy question, not a consequence of this command.

**Purpose:**

Contrast merge and rebase mechanics in one shared scenario.

## Diagnosing Divergent History and Conflicts

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:00–06:12`

**Visual:**

Hold the actual [git fetch origin] result and label it 'Comparison of Merge and Rebase Integration Mechanics'; circle the evidence just established: Contrast merge and rebase mechanics in one shared scenario.. Split to the next terminal/graph at [git merge origin/main] labeled 'Diagnosing Divergent History and Conflicts'; hide the result for prediction, then reveal the evidence for: Show conflict resolution through domain intent and index state.

**Script:**

Divergent histories are normal. A conflict is where integration needs a human content decision.

**Purpose:**

Connect evidence from 'Comparison of Merge and Rebase Integration Mechanics' to 'Diagnosing Divergent History and Conflicts', turning the earlier observation into a specific next Git-state check: Show conflict resolution through domain intent and index state.

### Scene 1 — One scenario, traceable evidence

**Time:** `06:12–08:04`

**Visual:**

The PREVIOUS scene completed a nonconflicting merge, so do NOT rerun it and claim a conflict. Instead prepare a NEW pair of disposable working copies from the same pre-merge baseline: one commits a changed rounding line on `main` and publishes it to the bare remote; the other, on `feature/rounding`, commits a DIFFERENT change to that exact line. In the second copy fetch the updated `origin/main` and run `git merge origin/main; git status; git ls-files -u; git diff --check`. Freeze on real unmerged index entries, then manually resolve, stage and verify; never use the working repositories containing previous lessons.

**Script:**

We use a separate copy rather than reusing the branch that was already merged. Two contributors change the same rounding line differently, creating genuine conflicting commits. After fetch, merge pauses and status with ls-files -u exposes unmerged index stages. Consult the business requirement, resolve deliberately, stage and verify before completing the merge. Never commit conflict markers or apply recovery commands from a training video to company source.

**Purpose:**

Show conflict resolution through domain intent and index state.

## Safe Undo Choices Based on Whether Commits Were Shared

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:04–08:16`

**Visual:**

Hold the actual [git merge origin/main] result and label it 'Diagnosing Divergent History and Conflicts'; circle the evidence just established: Show conflict resolution through domain intent and index state.. Split to the next terminal/graph at [git status --short] labeled 'Safe Undo Choices Based on Whether Commits Were Shared'; hide the result for prediction, then reveal the evidence for: Use publishing status to choose revert instead of careless rewriting.

**Script:**

Even an integrated fix can be wrong; recovery depends on whether its commit was shared.

**Purpose:**

Connect evidence from 'Diagnosing Divergent History and Conflicts' to 'Safe Undo Choices Based on Whether Commits Were Shared', turning the earlier observation into a specific next Git-state check: Use publishing status to choose revert instead of careless rewriting.

### Scene 1 — One scenario, traceable evidence

**Time:** `08:16–10:08`

**Visual:**

Use separate disposable clones of the rounding-fix example; present terminal, diffs, graph, and refs. Inspect `git status --short; git log --oneline --all; git revert <selected-commit-id>`. Pause on decisive output and compare against the previous snapshot, never operating on real source.

**Script:**

For a bad local-only commit, the author may add a correcting commit or deliberately rewrite unpublished history. If the wrong commit is already shared, demonstrate revert in the practice clone, creating an inverse commit while preserving the original record. Resolve the placeholder to a real ID from log. Ask whether the issue is uncommitted work, a local commit, or shared history; the answer determines the recovery action.

**Purpose:**

Use publishing status to choose revert instead of careless rewriting.

## End-to-End Evidence from Status, Diffs, History, and References

<!-- VIDEO_SECTION -->

### Transition

**Time:** `10:08–10:20`

**Visual:**

Hold the actual [git status --short] result and label it 'Safe Undo Choices Based on Whether Commits Were Shared'; circle the evidence just established: Use publishing status to choose revert instead of careless rewriting.. Split to the next terminal/graph at [git status -sb] labeled 'End-to-End Evidence from Status, Diffs, History, and References'; hide the result for prediction, then reveal the evidence for: Conclude with a consistent status/diff/graph/reference evidence checklist.

**Script:**

After integrating and recovering, we need a reliable way to validate the whole change journey.

**Purpose:**

Connect evidence from 'Safe Undo Choices Based on Whether Commits Were Shared' to 'End-to-End Evidence from Status, Diffs, History, and References', turning the earlier observation into a specific next Git-state check: Conclude with a consistent status/diff/graph/reference evidence checklist.

### Scene 1 — One scenario, traceable evidence

**Time:** `10:20–12:12`

**Visual:**

Use separate disposable clones of the rounding-fix example; present terminal, diffs, graph, and refs. Inspect `git status -sb; git diff; git diff --cached; git log --graph --oneline --all; git show-ref`. Pause on decisive output and compare against the previous snapshot, never operating on real source.

**Script:**

End with five evidence sources: working-tree status, staged diff, recorded commit contents, parent graph, and local/remote refs. Ask the learner to predict a clean working tree with stale origin/main, or a new commit alongside remaining unstaged edits. This is how to diagnose Git rather than memorize a command sequence: reconcile several independent views of the same repository.

**Purpose:**

Conclude with a consistent status/diff/graph/reference evidence checklist.

## Boundary Between Git Mechanics, Hosted Review, and Branching Strategy

<!-- VIDEO_SECTION -->

### Transition

**Time:** `12:12–12:24`

**Visual:**

Hold the actual [git status -sb] result and label it 'End-to-End Evidence from Status, Diffs, History, and References'; circle the evidence just established: Conclude with a consistent status/diff/graph/reference evidence checklist.. Split to the next terminal/graph at [git log --oneline --graph --all] labeled 'Boundary Between Git Mechanics, Hosted Review, and Branching Strategy'; hide the result for prediction, then reveal the evidence for: Hand off Git mechanics to hosted reviews and team branch policy.

**Script:**

We can now read Git evidence. Where does the tool hand off to team governance?

**Purpose:**

Connect evidence from 'End-to-End Evidence from Status, Diffs, History, and References' to 'Boundary Between Git Mechanics, Hosted Review, and Branching Strategy', turning the earlier observation into a specific next Git-state check: Hand off Git mechanics to hosted reviews and team branch policy.

### Scene 1 — One scenario, traceable evidence

**Time:** `12:24–14:16`

**Visual:**

Use separate disposable clones of the rounding-fix example; present terminal, diffs, graph, and refs. Inspect `git log --oneline --graph --all; git branch -vv`. Pause on decisive output and compare against the previous snapshot, never operating on real source.

**Script:**

On the final graph, Git gives us commits, branches, diffs, and history exchange. It doesn't itself know who approved a pull request or which merge policy an organization selected. Contrast this with a hosted GitHub, GitLab or Azure Repos review screen; branching strategy is a separate team-governance topic. Keep the handoff clear: Git explains the history evidence, while acceptance and release policy live above it.

**Purpose:**

Hand off Git mechanics to hosted reviews and team branch policy.
