---
video:
  url: ""
---

# Remotes, Tracking Branches, and History Synchronization

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

## Remote Repositories: Sharing and Exchanging Git History

<!-- VIDEO_SECTION -->

### Scene 1 — Compare actual repositories

**Time:** `00:00–01:41`

**Visual:**

In a fresh disposable parent, initialize `local-demo` with branch `main` and record at least one seed commit using a local demo identity. From `local-demo`, create a sibling empty bare server using `git init --bare ../remote-demo.git`. Show that the seed commit exists only in `local-demo` so far; `git remote -v` is still empty. Do not describe a second clone or remote commit ID before publishing. Never use a real shared repository.

**Script:**

To see a remote without a hosting account, create a bare repository in a separate demo folder. The local repository has its own history; the remote is another repository that exchanges commit objects and refs. A remote does not automatically provide pull requests, reviewers, or GitHub policies. Draw a local/remote diagram and follow real commit IDs across that boundary.

**Purpose:**

Establish the remote as a Git repository rather than a pull-request API.

## Configuring and Inspecting Remotes Such as origin

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:41–01:53`

**Visual:**

Hold the actual [git init --bare ../remote-demo.git] result and label it 'Remote Repositories: Sharing and Exchanging Git History'; circle the evidence just established: Establish the remote as a Git repository rather than a pull-request API.. Split to the next terminal/graph at [git remote add origin ../remote-demo.git] labeled 'Configuring and Inspecting Remotes Such as origin'; hide the result for prediction, then reveal the evidence for: Inspect remote configuration without confusing it with synchronization.

**Script:**

We have a remote repository; first we need a reliable name and URL for it.

**Purpose:**

Connect evidence from 'Remote Repositories: Sharing and Exchanging Git History' to 'Configuring and Inspecting Remotes Such as origin', turning the earlier observation into a specific next Git-state check: Inspect remote configuration without confusing it with synchronization.

### Scene 1 — Compare actual repositories

**Time:** `01:53–03:34`

**Visual:**

Inside the seeded `local-demo`, run `git remote add origin ../remote-demo.git; git remote -v; git remote get-url origin` and freeze this state: the bare remote still has no `main`. THEN explicitly publish the seed with `git push -u origin main` and refresh tracking using `git fetch origin`. Use `git ls-remote origin main` and `git show-ref refs/remotes/origin/main` to show that `origin/main` now exists. Explain that the push/fetch, not `remote add`, transferred or discovered history.

**Script:**

Origin is a conventional remote name, not a required server name. Add a local-path remote and inspect the configured URL. Changing that URL does not rewrite local commits. At first the bare server contains no branch. Only the subsequent explicit push creates its `main`; fetch then makes the local `origin/main` ref safe to use in upcoming examples. Notice that `remote add` by itself neither publishes nor downloads history.

**Purpose:**

Inspect remote configuration without confusing it with synchronization.

## Local Branches, Server Branches, and Remote-Tracking References

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:34–03:46`

**Visual:**

Hold the actual [git remote add origin ../remote-demo.git] result and label it 'Configuring and Inspecting Remotes Such as origin'; circle the evidence just established: Inspect remote configuration without confusing it with synchronization.. Split to the next terminal/graph at [git branch -avv] labeled 'Local Branches, Server Branches, and Remote-Tracking References'; hide the result for prediction, then reveal the evidence for: Distinguish server-side branches, local branches, and cached tracking references.

**Script:**

With the remote configured, distinguish three types of references before trusting any status.

**Purpose:**

Connect evidence from 'Configuring and Inspecting Remotes Such as origin' to 'Local Branches, Server Branches, and Remote-Tracking References', turning the earlier observation into a specific next Git-state check: Distinguish server-side branches, local branches, and cached tracking references.

### Scene 1 — Compare actual repositories

**Time:** `03:46–05:27`

**Visual:**

Split view of two isolated local clones and a separate local bare remote; run/inspect `git branch -avv; git show-ref; git ls-remote origin`. Compare commit IDs and refs; never force-push to a real shared server.

**Script:**

Show three columns: local main, the actual main branch in the remote repository, and origin/main as a local remote-tracking ref. After fetch, origin/main reflects Git's last local update from that remote; it's not a live pointer that changes instantly with the server. Compare ls-remote origin with local show-ref IDs and notice stale tracking information.

**Purpose:**

Distinguish server-side branches, local branches, and cached tracking references.

## Upstream Relationships for Local Branches

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:27–05:39`

**Visual:**

Hold the actual [git branch -avv] result and label it 'Local Branches, Server Branches, and Remote-Tracking References'; circle the evidence just established: Distinguish server-side branches, local branches, and cached tracking references.. Split to the next terminal/graph at [git branch --set-upstream-to=origin/main main] labeled 'Upstream Relationships for Local Branches'; hide the result for prediction, then reveal the evidence for: Show upstream tracking as configuration, not remote authorization.

**Script:**

Now that the refs are separate, upstream configuration explains ahead/behind reporting.

**Purpose:**

Connect evidence from 'Local Branches, Server Branches, and Remote-Tracking References' to 'Upstream Relationships for Local Branches', turning the earlier observation into a specific next Git-state check: Show upstream tracking as configuration, not remote authorization.

### Scene 1 — Compare actual repositories

**Time:** `05:39–07:20`

**Visual:**

Split view of two isolated local clones and a separate local bare remote; run/inspect `git branch --set-upstream-to=origin/main main; git branch -vv`. Compare commit IDs and refs; never force-push to a real shared server.

**Script:**

A local branch can track an upstream branch so Git can calculate ahead/behind and choose a default source for pull. Set that relationship only after origin/main exists from an appropriate fetch or push; otherwise inspect refs instead of guessing. Upstream is configuration—it doesn't prove equal content or authorization to push.

**Purpose:**

Show upstream tracking as configuration, not remote authorization.

## git fetch Versus git pull in History Synchronization

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:20–07:32`

**Visual:**

Hold the actual [git branch --set-upstream-to=origin/main main] result and label it 'Upstream Relationships for Local Branches'; circle the evidence just established: Show upstream tracking as configuration, not remote authorization.. Split to the next terminal/graph at [git fetch origin] labeled 'git fetch Versus git pull in History Synchronization'; hide the result for prediction, then reveal the evidence for: Observe that fetch refreshes tracking refs while pull includes integration.

**Script:**

Upstream gives us a comparison point. Fetch and pull affect local history differently.

**Purpose:**

Connect evidence from 'Upstream Relationships for Local Branches' to 'git fetch Versus git pull in History Synchronization', turning the earlier observation into a specific next Git-state check: Observe that fetch refreshes tracking refs while pull includes integration.

### Scene 1 — Compare actual repositories

**Time:** `07:32–09:13`

**Visual:**

Split view of two isolated local clones and a separate local bare remote; run/inspect `git fetch origin; git log --oneline --graph --all; git pull --ff-only`. Compare commit IDs and refs; never force-push to a real shared server.

**Script:**

Fetch first, then compare origin/main and main in the graph. Fetch transfers objects and refreshes remote-tracking refs without automatically modifying your current working tree or integrating into your branch. Pull fetches and then integrates according to configuration; use --ff-only in this controlled demo so divergence causes a safe refusal. Pull isn't a magic clean-sync command.

**Purpose:**

Observe that fetch refreshes tracking refs while pull includes integration.

## git push, Remote Updates, and Rejection Conditions

<!-- VIDEO_SECTION -->

### Transition

**Time:** `09:13–09:25`

**Visual:**

Hold the actual [git fetch origin] result and label it 'git fetch Versus git pull in History Synchronization'; circle the evidence just established: Observe that fetch refreshes tracking refs while pull includes integration.. Split to the next terminal/graph at [git push -u origin feature/rounding] labeled 'git push, Remote Updates, and Rejection Conditions'; hide the result for prediction, then reveal the evidence for: Distinguish successful publishing from a non-fast-forward rejection.

**Script:**

Fetch tells us what exists remotely; push must respect that history.

**Purpose:**

Connect evidence from 'git fetch Versus git pull in History Synchronization' to 'git push, Remote Updates, and Rejection Conditions', turning the earlier observation into a specific next Git-state check: Distinguish successful publishing from a non-fast-forward rejection.

### Scene 1 — Compare actual repositories

**Time:** `09:25–11:06`

**Visual:**

Starting from the seeded `local-demo/main`, create `feature/rounding`, make a small genuine commit on a disposable file, then run `git push -u origin feature/rounding; git status -sb; git ls-remote origin feature/rounding`. Highlight the before/after remote ref. For the non-fast-forward explanation, draw a separate rejected-update case after another clone advances the same remote branch; do not claim this first push was rejected. Never force-push to a real shared server.

**Script:**

Push a disposable feature branch to our bare remote, then check both its local and remote positions. Push requests a remote ref update; if the server has commits not contained in the proposed history, a non-fast-forward push is normally rejected. Do not reach for --force as a fix: inspect the other work first. -u sets tracking for later use; it doesn't bypass server-side controls.

**Purpose:**

Distinguish successful publishing from a non-fast-forward rejection.

## Reconciling Diverged Histories Before Sharing Changes

<!-- VIDEO_SECTION -->

### Transition

**Time:** `11:06–11:18`

**Visual:**

Hold the actual [git push -u origin feature/rounding] result and label it 'git push, Remote Updates, and Rejection Conditions'; circle the evidence just established: Distinguish successful publishing from a non-fast-forward rejection.. Split to the next terminal/graph at [git fetch origin] labeled 'Reconciling Diverged Histories Before Sharing Changes'; hide the result for prediction, then reveal the evidence for: Teach divergence diagnosis before retrying a push.

**Script:**

Push can be rejected because histories diverged. The right response begins with inspection and integration.

**Purpose:**

Connect evidence from 'git push, Remote Updates, and Rejection Conditions' to 'Reconciling Diverged Histories Before Sharing Changes', turning the earlier observation into a specific next Git-state check: Teach divergence diagnosis before retrying a push.

### Scene 1 — Compare actual repositories

**Time:** `11:18–12:59`

**Visual:**

Create `clone-B` from the now seeded bare remote, switch to its `main`, record a different-line commit with a demo identity and push it to the bare `origin/main`. Meanwhile `local-demo` remains on its committed `feature/rounding`. In `local-demo`, run `git fetch origin; git log --graph --oneline --decorate --all; git merge origin/main`. Freeze the different tips before merging; if an intentional same-line conflict is staged instead, resolve it before claiming a completed merge. No force push or edits outside the throwaway parent.

**Script:**

Imagine two independent clones each create a commit and clone A pushes first. In clone B, fetch and inspect both tips before choosing integration. Merge or rebase depends on collaboration policy and which commits have been published; this demonstration uses merge to preserve visible history. Resolve any conflicts and verify before pushing. Never erase someone else's remote commits with a casual force push.

**Purpose:**

Teach divergence diagnosis before retrying a push.

## Reference Evidence Before and After History Synchronization

<!-- VIDEO_SECTION -->

### Transition

**Time:** `12:59–13:11`

**Visual:**

Hold the actual [git fetch origin] result and label it 'Reconciling Diverged Histories Before Sharing Changes'; circle the evidence just established: Teach divergence diagnosis before retrying a push.. Split to the next terminal/graph at [git branch -avv] labeled 'Reference Evidence Before and After History Synchronization'; hide the result for prediction, then reveal the evidence for: Close with explicit server-versus-local tracking evidence.

**Script:**

After reconciliation, verify both repositories rather than trusting one machine's status.

**Purpose:**

Connect evidence from 'Reconciling Diverged Histories Before Sharing Changes' to 'Reference Evidence Before and After History Synchronization', turning the earlier observation into a specific next Git-state check: Close with explicit server-versus-local tracking evidence.

### Scene 1 — Compare actual repositories

**Time:** `13:11–14:52`

**Visual:**

Split view of two isolated local clones and a separate local bare remote; run/inspect `git branch -avv; git ls-remote origin; git log --graph --oneline --all`. Compare commit IDs and refs; never force-push to a real shared server.

**Script:**

Finish with two views: local refs after fetching and the actual remote refs. ls-remote proves the server's current pointers, branch -avv shows local tracking state, and log --graph explains the ancestry. If IDs disagree, ask whether fetch ran, whether push succeeded, and whether you're inspecting the right remote. A single 'success' message is not a substitute for evidence from both sides.

**Purpose:**

Close with explicit server-versus-local tracking evidence.
