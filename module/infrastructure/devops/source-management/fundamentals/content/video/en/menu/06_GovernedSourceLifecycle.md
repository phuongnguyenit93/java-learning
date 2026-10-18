---
video:
  url: ""
---

# Governed Source Change Lifecycle

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

## From Individual Change to Shared Source

<!-- VIDEO_SECTION -->

### Scene 1 — From Individual Change to Shared Source

**Time:** `00:00–01:15`

**Visual:**

Show six stages `An edits Invoice.java → Record revision → Share → Review → Accept → Build`; color source governance and downstream delivery differently.

**Script:**

We can now assemble the source-management lifecycle. An begins with a local edit, records a version, shares it so Binh can inspect it, and the team decides whether that change belongs in the shared baseline. Only then might a delivery workflow build or release the accepted source. Those are not six spellings of Save. Each boundary has a different kind of evidence. In this final chapter we will follow the same tax-and-rounding example across the stages and examine what it means when a change stops before acceptance.

**Purpose:**

Connect the learner's mental model into a complete source change lifecycle with a delivery boundary.



## Recorded, Shared, Reviewed, and Accepted

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:15–01:27`

**Visual:**

Retain Edit→Record→Share→Review→Accept→Build; zoom into four Recorded / Shared / Reviewed / Accepted cards with different evidence beneath each.

**Script:**

Four stages are especially easy to collapse into 'done'. How can we tell them apart?

**Purpose:**

Disambiguate the four commonly collapsed stages through their distinct records.

### Scene 2 — Recorded, Shared, Reviewed, and Accepted

**Time:** `01:27–02:42`

**Visual:**

Reveal four cards `Recorded`, `Shared`, `Reviewed`, `Accepted`, with example evidence `Revision ID`, `Remote ref`, `Review result`, `Integrated target`.

**Script:**

Recorded means a revision exists in version history. Shared means that revision was exchanged to a place where the team can inspect it, not that anyone has inspected it. Reviewed means there has been an assessment and recorded feedback or votes. Accepted means the change reached the team's agreed integration point according to its process. A revised proposal may go through review again, so the real lifecycle is not just four irreversible checkboxes. Match each statement to a piece of evidence: a revision ID, a published reference, a review record and the accepted target revision. One cannot substitute for another.

**Purpose:**

Distinguish the four states using different evidence artifacts instead of treating push as approval.



## Scenario: A Shared Change That the Team Has Not Yet Accepted

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:42–02:54`

**Visual:**

Keep Recorded and Shared highlighted; display illustrative PR 42 Open with Needs rounding test while main still has the previous tax value.

**Script:**

What does a change look like when it has been shared but not accepted?

**Purpose:**

Use an unfinished proposal to prove that sharing commits is not evidence of accepted integration.

### Scene 3 — Scenario: A Shared Change That the Team Has Not Yet Accepted

**Time:** `02:54–04:09`

**Visual:**

Display illustrative `PR 42: tax rate 10%`, state `Open`, Binh comment `Needs rounding test`, and `main` still showing 8%. Highlight Shared, leave Accepted dimmed.

**Script:**

An records commit C changing tax to ten percent and pushes the topic branch to the shared hosting service. Colleagues can now examine it, but main may still contain the earlier version. Binh reviews the proposal and notices that rounding behavior has no regression test, so he asks for another change. Saying 'the fix is already up' would confuse publication with integration. The evidence tells a more precise story: commit C is available for review on the topic branch, but the team's target has not accepted it. That is normal, not a Git failure.

**Purpose:**

Demonstrate with observable proposal and target states why shared does not imply accepted.



## Synchronization and Review Failure Modes

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:09–04:21`

**Visual:**

Retain the Open PR and unchanged main; reveal Overwrite, Stale sync, Old review screenshot and Push≠merge with matching verification artifacts.

**Script:**

Which failures become likely if a team skips these boundaries?

**Purpose:**

Turn the pending-proposal scenario into a diagnostic table of distinct failure modes.

### Scene 4 — Synchronization and Review Failure Modes

**Time:** `04:21–05:36`

**Visual:**

Four warning cards: `Folder overwrite`, `Stale synchronization`, `Old PR screenshot`, `Shared mistaken for accepted`; reveal a matching verification check under each.

**Script:**

Copying folders over one another can silently remove Binh's edits. Failing to synchronize can make An work from an old base. Reviewing only yesterday's screenshot may miss new changes pushed after the review. And treating a successful push as accepted source gives management the wrong progress report. These are different failure modes and need different checks. Inspect the actual version history, compare source and target references, recheck the current diff and votes, and verify the integration state. You do not need every platform command to recognize where the missing evidence belongs.

**Purpose:**

Map common failures to the correct evidence source instead of proposing a blanket fix.



## Evidence of a Governed Source Change

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:36–05:48`

**Visual:**

Collapse the four failure cards into Change / Revision / Proposal / Reviewer decision / Accepted target, preserving Pending where unknown.

**Script:**

Can we summarize a governed change without relying on everyone's memory?

**Purpose:**

Translate failure diagnosis into a compact auditable source-governance handoff.

### Scene 5 — Evidence of a Governed Source Change

**Time:** `05:48–07:03`

**Visual:**

Fill a five-row evidence sheet: `Change`, `Recorded revision`, `Shared proposal`, `Review decision`, `Accepted target`, using clearly marked illustrative IDs.

**Script:**

A good handoff answers five questions. What exactly changed? Which revision captured it? Where was it proposed for team review? Who assessed it, and what issues were raised? Which target revision finally accepted it? These facts let a colleague reconstruct the decision without collecting disconnected chat messages. If review remains pending, the sheet must say pending instead of pretending completion. Governed change does not mean every edit is automatically correct. It means the team can inspect, challenge, and explain the path from proposal to accepted source.

**Purpose:**

Offer a compact evidence checklist that preserves uncertainty and review state honestly.



## From Accepted Source to Delivery Automation

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:03–07:15`

**Visual:**

Keep the Accepted revision M on the evidence sheet; draw a hard boundary to Build → Test → Release → Deploy with delivery evidence still unknown.

**Script:**

Once the source is accepted, is the running application already updated?

**Purpose:**

Close the course by separating accepted source from unverified downstream delivery outcomes.

### Scene 6 — From Accepted Source to Delivery Automation

**Time:** `07:15–08:30`

**Visual:**

Draw a bold boundary from `Accepted source M` to `Build → Test → Release → Deploy`. Place `Accepted code ≠ deployed system` across the separation.

**Script:**

Even if accepted revision M contains both An's tax correction and Binh's rounding fix, that only establishes which source the team agreed to integrate. It does not prove the build passed, tests succeeded, a release artifact was created, or production deployed it. Those observations belong to CI/CD and delivery. When someone says a bug fix is live, ask for deployment evidence rather than only an approved pull request. The lasting habit from these six chapters is to ask what was recorded, what was shared, what was reviewed, what was accepted, and only then what was actually delivered.

**Purpose:**

Close with the critical source-to-delivery boundary and the evidence-based mindset from the whole course.
