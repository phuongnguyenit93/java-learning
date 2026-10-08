---
video:
  url: ""
---

# Centralized and Distributed Models

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

## Centralized Version Control: Server-and-Client History Model

<!-- VIDEO_SECTION -->

### Scene 1 — Centralized Version Control: Server-and-Client History Model

**Time:** `00:00–01:15`

**Visual:**

Draw An, Binh and Lan as clients connected to one `Central history server`; show checkout/check-in arrows and dim the connection during an outage.

**Script:**

In centralized version control, a server holds the authoritative history and clients work against it. Subversion is a familiar example. A developer can often keep editing files already checked out locally, but recording new history and many history operations require the central service. When connectivity fails, the working copy does not suddenly disappear. The limiting factor is access to operations that need the history server. Keep that distinction visible: centralized does not mean no offline file editing, and a client copy of files is not the same thing as a full local history repository.

**Purpose:**

Explain centralized history ownership and its network dependency without exaggerating offline limitations.



## Distributed Version Control: Repository History in Local Copies

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:15–01:27`

**Visual:**

Keep the central A–B history visible, duplicate the history into An's and Binh's Git clones, add An's local C commit and disable the network arrow.

**Script:**

What changes when history itself can live in each developer's local repository?

**Purpose:**

Visualize which history data resides in each copy when shifting from centralized to distributed control.

### Scene 2 — Distributed Version Control: Repository History in Local Copies

**Time:** `01:27–02:42`

**Visual:**

Replace the central-only timeline with An's and Binh's local repositories, each showing shared A–B plus individual successors. Only reveal exchange arrows when connected.

**Script:**

A distributed system such as Git normally gives every full clone its own commit database. An can inspect existing commits, compare recorded versions and create another local commit without contacting a server. When the network returns, the repositories can exchange those changes. Distributed does not mean that every clone is synchronized or equally authorized to publish. Teams still designate shared integration references and decide who may update them. A shallow clone and some specialized repositories have different limits; this picture describes a normal clone containing the history needed for the experiment.

**Purpose:**

Make offline history behavior concrete while keeping the role of accepted shared source distinct.



## Local Work and Collaboration Trade-offs

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:42–02:54`

**Visual:**

Shrink the local A–B/C clone graphs into a comparison board for offline recording, synchronization, history access and accepted-branch rights.

**Script:**

Having local history is useful. What coordination costs remain?

**Purpose:**

Translate the local-history benefit into explicit coordination and governance trade-offs.

### Scene 3 — Local Work and Collaboration Trade-offs

**Time:** `02:54–04:09`

**Visual:**

Compare a two-column table: `CVCS: central history operations` versus `DVCS: local history and later exchange`, with rows for offline, synchronization, access, and review.

**Script:**

A central service can make the authoritative history location obvious, but it also concentrates dependency on server availability when recording revisions. Distributed clones support more local history work, while allowing copies to diverge until changes are exchanged. Neither label tells us that review is stronger or permissions are automatically safer. Ask which operations must work without a network, how the team handles divergence, who maintains the shared integration repository, and what review controls surround it. The trade-off is between workflows and failure modes, not a competition over which name sounds more modern.

**Purpose:**

Present useful selection questions and reject simplistic winner/loser comparisons.



## History Availability and Copy Governance

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:09–04:21`

**Visual:**

Retain the offline/synchronization comparison, reconnect An, Binh and remote histories, and label History available separately from Accepted source.

**Script:**

If several copies retain history, which one does the team actually accept?

**Purpose:**

Show that multiple recoverable histories do not remove the need for governed shared integration.

### Scene 4 — History Availability and Copy Governance

**Time:** `04:21–05:36`

**Visual:**

Three boxes An/Binh/Server; place `History available` under every local copy, but `Team accepted source` only under the agreed integration point.

**Script:**

A Git clone can retain valuable history during an outage without becoming the team's approved integration source. Teams still establish repository and branch ownership, access grants and review conditions. A centralized server also needs backups, credential management and a recovery plan; central storage alone is not governance. Distributed copies can help retain recorded objects, but copied history does not guarantee approval or complete disaster recovery. Think of two different questions: can I inspect the old history here, and has the team agreed this is the source it will build from? They do not have the same answer.

**Purpose:**

Separate availability of copied history from explicit authorization and acceptance.



## Server-Outage Scenario: Availability of Local Version History

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:36–05:48`

**Visual:**

Set the remote to unavailable; keep An's Git history A–B/C beside a centralized working copy and highlight local history inspection versus server check-in.

**Script:**

Let's make that distinction observable during a server outage.

**Purpose:**

Ground the CVCS/DVCS distinction in observable outage behavior under explicit clone and network assumptions.

### Scene 5 — Server-Outage Scenario: Availability of Local Version History

**Time:** `05:48–07:03`

**Visual:**

Show a prepared normal Git clone using `git log` then committing locally with network off; alongside, a centralized working copy remains editable but cannot check in a new central revision. Mark the setup assumptions.

**Script:**

Suppose the connection to our central server is unavailable for an hour. In a normal Git clone with the required history already downloaded, An can inspect past commits and record another local commit, then share it later. In a server-dependent centralized workflow, An may edit checked-out files, but cannot record the next central revision while the server is unreachable. That observation shows where history operations occur. It does not prove that Git automatically resolves later conflicts or grants permission to update the shared branch. When service returns, both workflows still need inspection, coordination and an agreed acceptance process.

**Purpose:**

Demonstrate the outage behavior under explicit assumptions and close the availability-versus-acceptance distinction.
