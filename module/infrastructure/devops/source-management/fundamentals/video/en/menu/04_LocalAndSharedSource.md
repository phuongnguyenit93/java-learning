---
video:
  url: ""
---

# Local and Shared Source

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

## Working Files Versus Versioned History

<!-- VIDEO_SECTION -->

### Scene 1 — Working Files Versus Versioned History

**Time:** `00:00–01:15`

**Visual:**

Show three layers: An's editor displays `Invoice.java` at 10%; recorded local revision still shows 8%; shared team state also shows 8%. Highlight each separately.

**Script:**

An types a ten-percent rate into his editor. That does not mean version history contains this change. He may have saved the file to disk but never recorded a revision. On this screen we distinguish three places: files being edited, history already recorded locally, and the source the team shares. If An closes the editor and says 'I saved it', Binh cannot tell which of those places contains the new rule. Before calling a change complete, we need a reliable answer to what changed, where it was recorded, and what the team has actually accepted.

**Purpose:**

Separate working files, recorded revisions and accepted shared source through an observable example.



## Local and Remote Repositories

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:15–01:27`

**Visual:**

Keep the editor/recorded/shared source layers; open An local repo and Team remote history boxes with synchronization arrows initially disabled.

**Script:**

Where do local work and the team's shared history actually live?

**Purpose:**

Reveal separate locations for local and shared revisions without suggesting that saving a file syncs repositories.

### Scene 2 — Local and Remote Repositories

**Time:** `01:27–02:42`

**Visual:**

Two repository boxes labeled `An local` and `Team remote`, each containing commit history; put working files beneath An. Show exchange arrows appearing only on deliberate sync.

**Script:**

A local repository stores revisions An recorded on his own machine. A remote repository is another Git repository used for sharing history, commonly on a server. The two do not synchronize every edit automatically. An may commit locally while the server remains unaware. The team may also advance a remote branch while An has not fetched the newer revision. Even when a remote exists, the group must decide which repository and integration reference are authoritative for its work. Remote is a location and exchange role, not a magical approval switch.

**Purpose:**

Explain local versus remote with explicit sync boundaries and no implicit permissions claim.



## Synchronization and Divergent Histories

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:42–02:54`

**Visual:**

Retain local and remote repositories; mark common commit B and show local advancing to C while remote advances to D with no automatic merge.

**Script:**

What if those two repositories have progressed in different directions?

**Purpose:**

Derive legitimate divergence from two independently advanced histories, not from a presumed corruption.

### Scene 3 — Synchronization and Divergent Histories

**Time:** `02:54–04:09`

**Visual:**

Draw shared commits A–B, with local An→C and remote→D from B; mark two distinct tips and a dotted `diverged` note before showing any integration arrow.

**Script:**

Imagine An records commit C for the tax rate while the remote receives commit D for rounding. Both descend from B, but neither tip contains the other. This is a divergent history, not necessarily corrupted data. Sharing C does not silently remove D, and fetching D does not mean that it has been merged with C. The developer needs to inspect each line of development, deliberately integrate changes, and verify the combined result. The Git module owns commands such as fetch, merge and rebase. Our foundation is the simpler insight that two valid copies can disagree for a while.

**Purpose:**

Visualize a diverged DAG and distinguish observation/fetch from integration.



## Accepted Shared Source: Concept and Criteria

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:09–04:21`

**Visual:**

Preserve diverged tips C and D; introduce integrated M plus explicit review/check criteria before revealing an Accepted label.

**Script:**

Once C and D can be integrated, what makes the combined source the team's agreed baseline?

**Purpose:**

Distinguish a technical combined revision from the team's governed accepted baseline.

### Scene 4 — Accepted Shared Source: Concept and Criteria

**Time:** `04:21–05:36`

**Visual:**

Add combined revision M to the A–B→C,D graph; label C and D `proposals` and M `accepted integration`, with a separate review/check indicator.

**Script:**

A revision published on a server can still be only a proposal. Teams normally define an accepted integration branch or reference and conditions for allowing changes into it. If M combines An's ten-percent tax change with Binh's rounding fix, it becomes accepted shared source only under the team's actual rules. The fact that the commit exists does not prove a reviewer approved it. Likewise, approval does not demonstrate that software has already been built or deployed. The useful habit is to inspect revision history and collaboration status as separate forms of evidence.

**Purpose:**

Define accepted shared source as a governed decision rather than any available remote commit.
