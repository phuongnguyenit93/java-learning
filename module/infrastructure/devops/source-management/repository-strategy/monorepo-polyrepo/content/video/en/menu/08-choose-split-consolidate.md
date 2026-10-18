---
video:
  url: ""
---

# Choose, Split, and Consolidate Repositories with Evidence

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

## Repository Topology Selection Criteria

<!-- VIDEO_SECTION -->

### Scene 1 — Repository Topology Selection Criteria

**Time:** `00:00–00:54`

**Visual:**

Draw a decision board with a hard restricted-Fraud read boundary above softer PR coordination, CI cost and tool autonomy tradeoffs. Eliminate infeasible designs first.

**Script:**

Do not start a topology decision with the name of a company that uses a monorepo. First identify cross-component change frequency, interface coupling, owners, read restrictions, release cadence and tooling capability. If Fraud source is restricted to Security, a broadly readable shared repo can fail a mandatory constraint regardless of convenience. If Web and Checkout frequently co-change under the same access rules, a monorepo may help, provided dependent validation remains timely. Each criterion needs actual project evidence.

**Purpose:**

Learners must identify hard constraints distinguished from scored preferences using the displayed evidence, without substituting a repository-count assumption for a strategic decision.

## Cross-Team Change Frequency in Topology Decisions

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:54–01:13`

**Visual:**

Keep the previous evidence "hard constraints distinguished from scored preferences" in the left panel; reveal "requirements spanning multiple repos and linked PR lead time" on the right and animate the question that connects the two observations.

**Script:**

We have just inspected hard constraints distinguished from scored preferences. The next decision is different: we need to verify requirements spanning multiple repos and linked PR lead time. Follow the evidence as the view changes.

**Purpose:**

Bridge the previous result to the specific criterion requirements spanning multiple repos and linked PR lead time rather than presenting unrelated definitions.

### Scene 1 — Cross-Team Change Frequency in Topology Decisions

**Time:** `01:13–02:07`

**Visual:**

Sample TAX-42, SHIP-17 and AUTH-8 tickets, count affected repositories/PRs per requirement, and distinguish formatting commits from business co-changes.

**Script:**

Raw commit count says little about coordination: a mass formatting change is not the same as a business feature requiring three teams. Sample real issues and trace the repositories, PRs and time from first integration to the last required adoption. Repeated Contracts/API/Web co-changes can make multi-repo coordination costly, whereas infrequent interface changes may justify autonomy. Record the sampling window and exclusions rather than inventing a cross-team-change percentage for this video.

**Purpose:**

Learners must identify requirements spanning multiple repos and linked PR lead time using the displayed evidence, without substituting a repository-count assumption for a strategic decision.

## Ownership, Access, Dependencies, and Release Decision Matrix

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:07–02:26`

**Visual:**

Keep the previous evidence "requirements spanning multiple repos and linked PR lead time" in the left panel; reveal "evidence-backed matrix and feasible hybrid topology" on the right and animate the question that connects the two observations.

**Script:**

We have just inspected requirements spanning multiple repos and linked PR lead time. The next decision is different: we need to verify evidence-backed matrix and feasible hybrid topology. Follow the evidence as the view changes.

**Purpose:**

Bridge the previous result to the specific criterion evidence-backed matrix and feasible hybrid topology rather than presenting unrelated definitions.

### Scene 1 — Ownership, Access, Dependencies, and Release Decision Matrix

**Time:** `02:26–03:20`

**Visual:**

Build rows for Checkout, Web and Fraud against owners, read access, contracts, release cadence and validation; highlight Fraud access as a non-negotiable constraint.

**Script:**

A decision matrix is useful only when its cells reference observable facts. Checkout and Web might share ownership, reviewers and frequent interface changes that favor co-location. Fraud may require a separate read boundary despite still depending on a shared contract. Work through ownership, access, dependencies and release cadence first. Among options that satisfy hard restrictions, compare review and tooling costs. A hybrid topology—Checkout/Web together and Fraud separate—can be more defensible than forcing every component into a single extreme.

**Purpose:**

Learners must identify evidence-backed matrix and feasible hybrid topology using the displayed evidence, without substituting a repository-count assumption for a strategic decision.

## Conditions and Benefits of Repository Consolidation

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:20–03:39`

**Visual:**

Keep the previous evidence "evidence-backed matrix and feasible hybrid topology" in the left panel; reveal "consolidation benefits conditional on access and tooling readiness" on the right and animate the question that connects the two observations.

**Script:**

We have just inspected evidence-backed matrix and feasible hybrid topology. The next decision is different: we need to verify consolidation benefits conditional on access and tooling readiness. Follow the evidence as the view changes.

**Purpose:**

Bridge the previous result to the specific criterion consolidation benefits conditional on access and tooling readiness rather than presenting unrelated definitions.

### Scene 1 — Conditions and Benefits of Repository Consolidation

**Time:** `03:39–04:33`

**Visual:**

Show recurring paired Web and API Contracts PR chains; then one potential consolidated PR, with path owner and affected-validation requirements beside it.

**Script:**

When most Web changes wait for a related API or Contracts PR, consolidation may improve visibility and reduce repeated cross-repo handoffs. The value is not simply having fewer repository entries. Before moving source, verify compatible read boundaries, clear path owners and adequate affected-test tooling. If restricted material becomes newly readable by a vendor, the consolidation may be unacceptable. Define success through linked-change lead time and review quality, not the repository-count reduction alone.

**Purpose:**

Learners must identify consolidation benefits conditional on access and tooling readiness using the displayed evidence, without substituting a repository-count assumption for a strategic decision.

## Conditions and Constraints for Splitting a Repository

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:33–04:53`

**Visual:**

Keep the previous evidence "consolidation benefits conditional on access and tooling readiness" in the left panel; reveal "splitting source for real isolation while retaining contracts" on the right and animate the question that connects the two observations.

**Script:**

We have just inspected consolidation benefits conditional on access and tooling readiness. The next decision is different: we need to verify splitting source for real isolation while retaining contracts. Follow the evidence as the view changes.

**Purpose:**

Bridge the previous result to the specific criterion splitting source for real isolation while retaining contracts rather than presenting unrelated definitions.

### Scene 1 — Conditions and Constraints for Splitting a Repository

**Time:** `04:53–05:47`

**Visual:**

Split a Web+Fraud repository into web.git and fraud.git, then show a versioned interface contract and additional coordination arrows that remain necessary.

**Script:**

Separating a repository can be justified when a vendor must see Web source but not Fraud, or when lifecycle and ownership requirements genuinely diverge. But if the only symptom is slow cloning, investigate binary history, indexing and affected validation first. Splitting source still leaves interface contracts and consumer upgrades to maintain. The decision trades some coordination convenience for a real boundary; it does not eliminate technical coupling or guarantee faster tests.

**Purpose:**

Learners must identify splitting source for real isolation while retaining contracts using the displayed evidence, without substituting a repository-count assumption for a strategic decision.

## Migration Costs for History, Permissions, and Dependencies

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:47–06:06`

**Visual:**

Keep the previous evidence "splitting source for real isolation while retaining contracts" in the left panel; reveal "history, access and integration migration inventory" on the right and animate the question that connects the two observations.

**Script:**

We have just inspected splitting source for real isolation while retaining contracts. The next decision is different: we need to verify history, access and integration migration inventory. Follow the evidence as the view changes.

**Purpose:**

Bridge the previous result to the specific criterion history, access and integration migration inventory rather than presenting unrelated definitions.

### Scene 1 — Migration Costs for History, Permissions, and Dependencies

**Time:** `06:06–07:00`

**Visual:**

Show migration checklist for commit history, tags, PR links, URLs, permissions, packages, dependencies and build assumptions, with sandbox rehearsal and rollback cards.

**Script:**

Changing topology is more than dragging folders between directories. History references, tags, old PR and Issue links, repository URLs, permissions, package identities and build assumptions may all need migration decisions. A careless split can make earlier commits difficult to discover. Inventory those assets, rehearse on a disposable copy and define a coexistence period and rollback path before switching users to a new source layout. The detailed history-filter commands belong to Git specialists rather than this strategic course.

**Purpose:**

Learners must identify history, access and integration migration inventory using the displayed evidence, without substituting a repository-count assumption for a strategic decision.

## Topology Transition Plans and Observable Outcomes

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:00–07:18`

**Visual:**

Keep the previous evidence "history, access and integration migration inventory" in the left panel; reveal "pilot baseline success criteria and rollback gates" on the right and animate the question that connects the two observations.

**Script:**

We have just inspected history, access and integration migration inventory. The next decision is different: we need to verify pilot baseline success criteria and rollback gates. Follow the evidence as the view changes.

**Purpose:**

Bridge the previous result to the specific criterion pilot baseline success criteria and rollback gates rather than presenting unrelated definitions.

### Scene 1 — Topology Transition Plans and Observable Outcomes

**Time:** `07:18–08:12`

**Visual:**

Animate Baseline→API/Web pilot→Compare→Rollout or Rollback, with measurement cells for PR lead time, contract defects, test feedback and developer experience.

**Script:**

A topology migration without baseline evidence can become an expensive source-reorganization exercise. For an API/Web consolidation pilot, record actual PR lead time, contract failures, feedback latency and developer experience before changing the layout. Pilot only a bounded component set, assign owners and preserve rollback options. Afterward compare equivalent evidence over a similar observation window. If coordination improves while test feedback becomes intolerably slow, address tooling or reconsider the plan rather than expanding migration blindly.

**Purpose:**

Learners must identify pilot baseline success criteria and rollback gates using the displayed evidence, without substituting a repository-count assumption for a strategic decision.

## Evidence from Large-Scale Monorepo Research

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:12–08:32`

**Visual:**

Keep the previous evidence "pilot baseline success criteria and rollback gates" in the left panel; reveal "2016 bespoke source control case versus 2018 comparative mixed methods" on the right and animate the question that connects the two observations.

**Script:**

We have just inspected pilot baseline success criteria and rollback gates. The next decision is different: we need to verify 2016 bespoke source control case versus 2018 comparative mixed methods. Follow the evidence as the view changes.

**Purpose:**

Bridge the previous result to the specific criterion 2016 bespoke source control case versus 2018 comparative mixed methods rather than presenting unrelated definitions.

### Scene 1 — Evidence from Large-Scale Monorepo Research

**Time:** `08:32–09:26`

**Visual:**

Contrast Potvin and Levenberg's 2016 custom-built source repository with Jaspan et al.'s 2018 comparative engineer survey and developer-tool log analysis; attach claims to the correct study.

**Script:**

The shortcut 'Google uses a monorepo, so ordinary Git scales to billions of lines' is not supported. Potvin and Levenberg in 2016 described Google's very large common source supported by custom-built source-control infrastructure and specialized tooling. Jaspan and colleagues in 2018 conducted a separate mixed-method comparison, surveying engineers experienced with both layouts and examining developer-tool logs. They report discoverability/reuse benefits and multi-repo access-control and toolchain advantages. Those observations are context-specific trade-offs, not a guarantee for a twelve-person team.

**Purpose:**

Learners must identify 2016 bespoke source control case versus 2018 comparative mixed methods using the displayed evidence, without substituting a repository-count assumption for a strategic decision.

## Contrasting Cases Supporting Different Repository Topologies

<!-- VIDEO_SECTION -->

### Transition

**Time:** `09:26–09:46`

**Visual:**

Keep the previous evidence "2016 bespoke source control case versus 2018 comparative mixed methods" in the left panel; reveal "contrasting cases with explicit decision-reversal criteria" on the right and animate the question that connects the two observations.

**Script:**

We have just inspected 2016 bespoke source control case versus 2018 comparative mixed methods. The next decision is different: we need to verify contrasting cases with explicit decision-reversal criteria. Follow the evidence as the view changes.

**Purpose:**

Bridge the previous result to the specific criterion contrasting cases with explicit decision-reversal criteria rather than presenting unrelated definitions.

### Scene 1 — Contrasting Cases Supporting Different Repository Topologies

**Time:** `09:46–10:40`

**Visual:**

Compare startup API/Web/Contracts frequent co-changes and shared access against regulated Fraud restricted source and external Web vendors; include what new evidence would reverse each choice.

**Script:**

Consider two hypothetical organizations. A startup regularly changes API, Web and Contracts together, with shared read access and fast validation, so one repo may simplify coordinated review. A regulated company must isolate Fraud source while external vendors work on Web, so separate or hybrid repositories may be more appropriate. There is no rule saying startups always use monorepos or enterprises always use polyrepos. New confidentiality requirements, tooling costs or different dependency patterns could reverse either recommendation.

**Purpose:**

Learners must identify contrasting cases with explicit decision-reversal criteria using the displayed evidence, without substituting a repository-count assumption for a strategic decision.

## Ownership Handoffs to Build/CI, Git, and Service Architecture

<!-- VIDEO_SECTION -->

### Transition

**Time:** `10:40–11:00`

**Visual:**

Keep the previous evidence "contrasting cases with explicit decision-reversal criteria" in the left panel; reveal "strategy handoffs to Git host governance CI and service architecture" on the right and animate the question that connects the two observations.

**Script:**

We have just inspected contrasting cases with explicit decision-reversal criteria. The next decision is different: we need to verify strategy handoffs to Git host governance CI and service architecture. Follow the evidence as the view changes.

**Purpose:**

Bridge the previous result to the specific criterion strategy handoffs to Git host governance CI and service architecture rather than presenting unrelated definitions.

### Scene 1 — Ownership Handoffs to Build/CI, Git, and Service Architecture

**Time:** `11:00–11:55`

**Visual:**

Place Repository Strategy in the center with handoffs to Git history migration, host permissions/review, build/CI affected validation and service architecture contracts/deployment, each with an observable acceptance criterion.

**Script:**

After selecting a source topology, the strategy owner should not silently become the owner of every Git command, pipeline or service architecture decision. Document source boundaries, access constraints, reviewer accountability, expected coordination gains and measurements. Git specialists preserve history, host administrators configure access and branch review, CI owners implement affected-work feedback, and architects maintain compatible interfaces and deployment boundaries. A combined API/Web repository still needs fast dependent validation; that requirement is a handoff, not a pipeline tutorial in this module.

**Purpose:**

Learners must identify strategy handoffs to Git host governance CI and service architecture using the displayed evidence, without substituting a repository-count assumption for a strategic decision.
