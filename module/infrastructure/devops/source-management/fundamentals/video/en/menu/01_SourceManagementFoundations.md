---
video:
  url: ""
---

# Source Management Foundations

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

## Source Management: Concept and Scope

<!-- VIDEO_SECTION -->

### Scene 1 — Source Management: Concept and Scope

**Time:** `00:00–01:15`

**Visual:**

Split screen: two folders containing `Invoice.java` on the left; a three-stage timeline `Editing → Recorded → Team accepted` on the right. Highlight one stage at a time.

**Script:**

Imagine An changes the tax rate in `Invoice.java` while Binh edits the same file. The problem is not simply deciding which file has the later timestamp. We need to know what was recorded, where the changes came from, and which version the team agreed to use. Source management organizes those versions, their history, and the collaboration around them. Recording a change does not prove that someone reviewed it, and accepting source does not mean it is deployed. Keep these three stages in view as our map for this chapter.

Our route is to learn why history matters, compare centralized and distributed systems, distinguish working files from shared repositories, and then connect the tool, hosting, team policy and acceptance stages. We will reuse the tax-and-rounding example along the way.

**Purpose:**

Define the actual boundary of source management without confusing recording, acceptance or deployment.



## Purpose of Source Management in Team Collaboration

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:15–01:27`

**Visual:**

Retain Recorded / Shared / Accepted from Scene 1; split An and Binh into concurrent Invoice.java edit lanes that collide at the shared handoff.

**Script:**

We now have three stages. Why do two developers need to distinguish them?

**Purpose:**

Connect three source states to the actual reason two simultaneous editors need an accepted baseline.

### Scene 2 — Purpose of Source Management in Team Collaboration

**Time:** `01:27–02:42`

**Visual:**

Show An and Binh editing one `Invoice.java`. Overlay three questions: `Who changed it? What changed? Which version did we accept?`

**Script:**

When you work alone, saving a file can feel like the whole workflow. On a team, An can change the tax calculation while Binh improves rounding, both for valid reasons and both in the same file. Without a clear history and acceptance point, whoever assembles the next release must guess how to combine them. Shared source management does not require everyone to edit one physical copy. It gives the team enough evidence to preserve independent work and make deliberate integration decisions instead of relying on memory or file names.

**Purpose:**

Establish the teamwork motivation using two simultaneous but legitimate edits.



## Risks of Unmanaged Manual Source Sharing

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:42–02:54`

**Visual:**

Collapse the An/Binh coordination lanes into the Who/What/Accepted grid, then reveal three nearly identical final ZIP archives and a possible overwrite arrow.

**Script:**

What specifically goes wrong if a team treats folders as its version history?

**Purpose:**

Move from collaboration goals to the concrete silent-overwrite failure in unmanaged file handoffs.

### Scene 3 — Risks of Unmanaged Manual Source Sharing

**Time:** `02:54–04:09`

**Visual:**

Place `project-final.zip`, `project-final-v2.zip`, and `project-fixed.zip` on screen; drag one over another and highlight Binh's rounding line disappearing.

**Script:**

Three folders may all claim to be final. Their names still cannot tell us which fix each one contains. If An sends an older copy of the rounding logic after Binh has updated it, copying An's whole folder over the shared one can silently remove Binh's work. A folder backup may preserve an earlier state, but it does not by itself explain who changed a line or why. Notice the dangerous detail: the file remains readable and the project might still compile, while the intended behavior has been lost.

**Purpose:**

Demonstrate silent overwrite and why naming folders is not traceable history.



## Example: Two Developers Exchanging Edited Files and Versioned Folders

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:09–04:21`

**Visual:**

Freeze the ZIP overwrite result, rewind to 09:00, and place An's 10:00 tax edit beside Binh's 10:30 rounding edit.

**Script:**

Let's replay those edits as a timeline instead of trusting the ZIP filenames.

**Purpose:**

Turn a generic overwrite failure into a causal sequence the learner can inspect step by step.

### Scene 4 — Example: Two Developers Exchanging Edited Files and Versioned Folders

**Time:** `04:21–05:36`

**Visual:**

Show 09:00 rate 8%; 10:00 An sets 10%; 10:30 Binh adds rounding; 11:00 An shares an old full folder. Compare tax and rounding columns before/after.

**Script:**

We start with an eight-percent tax rule. An changes it to ten percent; Binh independently adds rounding. They exchange folders by message. When An sends his complete project, that copy does not yet contain Binh's change. Declaring An's folder the new shared version preserves the new rate but loses rounding. Picking the newest modification time will not reconstruct Binh's intention. We must compare both proposed changes, decide how they fit together, and record which combined state was accepted.

**Purpose:**

Show the exact missing change in a consistent running scenario, not just an abstract warning.



## Source, Versions, Repositories, and History

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:36–05:48`

**Visual:**

Keep the 09:00–11:00 replay visible; label Invoice.java, a recorded snapshot, repository storage and individual history points in place.

**Script:**

To explain this timeline precisely, we need four distinct terms.

**Purpose:**

Attach foundational terminology to objects already observed in the manual-handoff scenario.

### Scene 5 — Source, Versions, Repositories, and History

**Time:** `05:48–07:03`

**Visual:**

Reveal four labeled cards: `Source: Invoice.java`, `Version: recorded state`, `Repository: stored versions`, `History: links between states`.

**Script:**

Source code is the material we change, such as `Invoice.java`. A version is a defined state of that source; a folder name alone is not a reliable version identifier. A repository stores version-control data and references. History links recorded states so we can inspect how we arrived somewhere. Those terms are related but they are not interchangeable. When someone says the code was saved, ask a more useful question: was it merely saved in an editor, recorded in version history, or already shared with the team?

**Purpose:**

Give learners a stable vocabulary for source, version, repository, and recorded history.



## Shared Source, Traceability, and Accountability

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:03–07:15`

**Visual:**

Hold the A→B revision line with its Version/History labels; unfold Author, Changed lines, Reason and Accepted? while leaving approval explicitly unknown.

**Script:**

Even with recorded versions, what makes one change accountable to the team?

**Purpose:**

Show that availability of historical metadata differs from accountable group acceptance.

### Scene 6 — Shared Source, Traceability, and Accountability

**Time:** `07:15–08:30`

**Visual:**

Display a change record with columns `Author`, `Patch`, `Reason`, `Acceptance`; fill An's tax edit and Binh's rounding proposal, leaving the latter pending.

**Script:**

Suppose somebody asks why the tax rate changed. A trustworthy record lets us inspect the content, author and stated rationale. But the team's accepted source also depends on collaboration: who reviewed the proposal, which conditions were met, and whether it was actually accepted. In this foundation chapter we draw that boundary rather than inventing a review mechanism. Git will teach the recording machinery, while hosting platforms will teach permissions and approvals. Next we will see exactly what version history can prove before any team-level acceptance happens.

**Purpose:**

Connect provenance to accountability and hand off to the version-control history chapter.
