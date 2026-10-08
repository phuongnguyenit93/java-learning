---
video:
  url: ""
---

# Integration and Repository History Policy

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

## Team Policy for Integrated History: Purpose and Rationale

<!-- VIDEO_SECTION -->

### Scene 1 — Compare integrated history

**Time:** `00:00–01:52`

**Visual:**

One source graph with a three-commit feature and three alternative target histories; decision card lists audit, revert, and review criteria.

Generate graphs in a disposable repository; credit host docs for PR UI illustrations and never attribute fabricated live PR results.

**Script:**

Our example PR has three commits: a schema preparation, behavior update, and tests. The question isn't whether Git can merge—it is what the target history should communicate afterward. Preserving three commits can help granular investigation; a single squash can represent one cohesive intention; a linear result may read more cleanly but can replace commit IDs. Record the team's audit, diagnosis, and rollback goals before choosing a shape simply because its graph looks attractive.

**Purpose:**

Prioritize investigation and traceability needs before merge style.

## Merge Commits: Preserved Branch Context and Intermediate History

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:52–02:05`

**Visual:**

Keep W1/W2/W3 on the proposed branch and reveal two-parent merge M on the target without hiding the source commits.

**Script:**

We have three feature commits. First show how a merge commit preserves their ancestry and branch context.

**Purpose:**

Connect the history-policy question to preserved branch ancestry and integration context.

### Scene 1 — Compare integrated history

**Time:** `02:05–03:57`

**Visual:**

Terminal `git log --graph --oneline --all` after a non-fast-forward merge; node M has two parents, retaining feature commits.

Generate graphs in a disposable repository; credit host docs for PR UI illustrations and never attribute fabricated live PR results.

**Script:**

In the sandbox, main and feature have both advanced before a non-fast-forward merge. The graph shows merge node M with two parents and the original feature commits intact. That preserves integration context and intermediate history, useful for tracing a change series. Many such merges also make the graph denser, so clear messages and review conventions matter. A fast-forward merge need not create a two-parent node unless explicitly requested; don't claim every merge operation does.

**Purpose:**

Prove merge-parent topology and retained commit history.

## Squash Merges: One Logical Change in Target-Branch History

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:57–04:10`

**Visual:**

Hold merge M, then show W1/W2/W3 squashed into one target S, with hosted PR discussion in an independent panel.

**Script:**

A merge retains intermediate commits. Now turn those three patches into one logical squash commit on main.

**Purpose:**

Contrast target commit granularity with hosted review traceability.

### Scene 1 — Compare integrated history

**Time:** `04:10–06:02`

**Visual:**

Before/after squash graph: W1/W2/W3 become one S commit on main, with PR review metadata retained separately.

Generate graphs in a disposable repository; credit host docs for PR UI illustrations and never attribute fabricated live PR results.

**Script:**

Put three work commits on the feature branch: WIP, test fix, and naming cleanup. After squash, main receives one commit S representing the aggregate behavior with a useful message. Those intermediate commits no longer appear as distinct steps on the target line under their original IDs; hosting PR records can still preserve the discussion. Squash can make main easier to scan by logical change, but target-history bisecting becomes less granular. If the PR covers unrelated intentions, one squash may hide them.

**Purpose:**

Explain target squashing and intermediate-history tradeoffs.

## Rebase-and-Merge: Linear Target History, Separate Commits, and New Commit IDs

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:02–06:15`

**Visual:**

Keep target squash S beside rebase-and-merge W1'/W2'/W3' on a linear parent chain, labeling old and new IDs.

**Script:**

Squash creates one target commit. Rebase-and-merge retains distinct patches on a linear target with new commit IDs.

**Purpose:**

Show how rebase-and-merge retains patch-level steps while changing commit identity.

### Scene 1 — Compare integrated history

**Time:** `06:15–08:07`

**Visual:**

Rebase-and-merge graph: patches W1/W2/W3 replay as W1'/W2'/W3' on a linear mainline; highlight changed IDs.

Generate graphs in a disposable repository; credit host docs for PR UI illustrations and never attribute fabricated live PR results.

**Script:**

Rebase-and-merge places the branch's changes onto the updated target as a linear series, generally preserving commit boundaries as new target commits. Parentage and potentially metadata change, so IDs need not match the original branch; GitHub's rebase-and-merge specifically creates new SHAs. This can retain meaningful patch-level steps if source commits are well organized, but the source and target no longer share identical commit identities. Inspect parents and messages, not just the straight line.

**Purpose:**

Show that linear history need not retain original commit IDs.

## Target-Branch Rebase-and-Merge Results Versus Work-Branch Rebase

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:07–08:20`

**Visual:**

Split the W1'–W3' graph into author-side F→F' rebase and host rebase-and-merge target history, retaining the original source branch ref.

**Script:**

That linear graph can arise through different actions. Compare host rebase-and-merge with rebasing a work branch.

**Purpose:**

Differentiate rewriting a work branch from hosted replay into the target.

### Scene 1 — Compare integrated history

**Time:** `08:20–10:12`

**Visual:**

Two-row comparison: local feature rebased to F' then PR updated; hosted rebase-and-merge creates new target commits without rewriting the source branch.

Generate graphs in a disposable repository; credit host docs for PR UI illustrations and never attribute fabricated live PR results.

**Script:**

Separate the actions. If the author rebases their local feature branch and publishes replacement commits, the work branch history changes and colleagues tracking old IDs may need reconciliation. If the host completes a PR with rebase-and-merge, target commits are replayed while the source branch can retain its earlier history. Both target graphs can be linear, but the operations and collaboration risks differ. A hosting menu choice does not prove the author ran `git rebase` locally.

**Purpose:**

Separate local branch rewriting from hosted target integration.

## Risks of Rewriting Shared Commits and Effects on Collaborators

<!-- VIDEO_SECTION -->

### Transition

**Time:** `10:12–10:25`

**Visual:**

Keep collaborator clone B at H while author A holds rewritten H'; annotate force-with-lease as a conditional check, not authorization.

**Script:**

Those actions affect collaborators differently. Watch what happens when a shared feature branch receives rewritten commits.

**Purpose:**

Make the shared-history rewrite risk observable in divergent collaborator refs.

### Scene 1 — Compare integrated history

**Time:** `10:25–12:17`

**Visual:**

Clones A and B both at H; after A rebases feature to H', B still tracks H; display force-push warning.

Generate graphs in a disposable repository; credit host docs for PR UI illustrations and never attribute fabricated live PR results.

**Script:**

In disposable clones A and B, both fetched feature commit H. A rebases the work and now has replacement H', while B still depends on H. A forced publish can make B's next update diverge or expose equivalent changes under different identities. Avoid rewriting shared history without explicit coordination. `--force-with-lease` provides a conditional safeguard, not permission or a guarantee of harmlessness. Keep the scenario entirely in throwaway clones; never force a real shared branch for filming.

**Purpose:**

Explain shared-history rewrite costs using two isolated clones.

## History Policy Trade-Offs: Traceability, Reverts, Diagnosis, and Readability

<!-- VIDEO_SECTION -->

### Transition

**Time:** `12:17–12:30`

**Visual:**

Carry the diverged clones into a merge/squash/rebase matrix comparing bisect, revert, audit and commit context beside one consistent business diff.

**Script:**

After observing rewriting risks, compare merge, squash and rebase against audit, revert and diagnosis needs.

**Purpose:**

Convert history-identity trade-offs into criteria for choosing an integration policy.

### Scene 1 — Compare integrated history

**Time:** `12:30–14:22`

**Visual:**

Decision matrix for merge, squash, and rebase outcomes across feature traceability, bisect, revert, and PR audit, beside sample graphs.

Generate graphs in a disposable repository; credit host docs for PR UI illustrations and never attribute fabricated live PR results.

**Script:**

Finish with a decision matrix, not a universal winner. Merge commits retain branch context and intermediate steps but add graph complexity. Squash represents a PR as one logical target commit, which may be easier to scan or revert as a unit while losing target-level intermediate granularity. Rebase-and-merge offers linear per-commit history with rewritten IDs. Regardless of policy, reviewers still need trustworthy diffs and tests. Commit messages, PR links, and backport processes ultimately determine audit usefulness.

**Purpose:**

Choose history policy for investigation and recovery needs.
