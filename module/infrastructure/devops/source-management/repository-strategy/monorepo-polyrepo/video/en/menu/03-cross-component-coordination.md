---
video:
  url: ""
---

# Coordinating Cross-Component and Cross-Repository Changes

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

## Cross-Component Changes: Concept and Basic Scenario

<!-- VIDEO_SECTION -->

### Scene 1 — Cross-Component Changes: Concept and Basic Scenario

**Time:** `00:00–00:55`

**Visual:**

Highlight totalAmount→amount across Contracts, API and Web, with two expected JSON shapes displayed as diagrams rather than executed responses.

**Script:**

If a schema field is renamed while Web still reads the old name, each isolated PR may look reasonable yet the combined system can fail. A cross-component change means one requirement affects multiple code units or the contract between them. In the storyboard, compare the old and proposed JSON shapes and identify all consumers. Repository topology changes where those coordinated edits are recorded, but it cannot remove the underlying compatibility obligation. Ask which pieces must change together or stay backward compatible.

**Purpose:**

Learners must identify a single requirement touching schema API and Web using the displayed evidence, without substituting a repository-count assumption for a strategic decision.

## Sources of Coordination Cost in Cross-Team Changes

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:55–01:15`

**Visual:**

Keep the previous evidence "a single requirement touching schema API and Web" in the left panel; reveal "review waiting and library adoption as coordination cost" on the right and animate the question that connects the two observations.

**Script:**

We have just inspected a single requirement touching schema API and Web. The next decision is different: we need to verify review waiting and library adoption as coordination cost. Follow the evidence as the view changes.

**Purpose:**

Bridge the previous result to the specific criterion review waiting and library adoption as coordination cost rather than presenting unrelated definitions.

### Scene 1 — Sources of Coordination Cost in Cross-Team Changes

**Time:** `01:15–02:09`

**Visual:**

Draw Contracts PR→library 2.1→API PR→Web adoption timeline with waiting lanes; contrast one cross-area PR that still requires several owner reviews.

**Script:**

Coordination cost is not merely how many lines changed. Three repos introduce separate publish and adoption events, while a monorepo may gather the diff yet still wait for reviews or affected tests. Follow the timestamps of the library PR, first consumer integration and last compatible release. Those events can reveal waiting and rework without invented lead-time numbers. If the delay arises from unstable interfaces, changing topology alone is unlikely to cure it.

**Purpose:**

Learners must identify review waiting and library adoption as coordination cost using the displayed evidence, without substituting a repository-count assumption for a strategic decision.

## Shared History for Observing and Reviewing Cross-Component Changes

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:09–02:29`

**Visual:**

Keep the previous evidence "review waiting and library adoption as coordination cost" in the left panel; reveal "one shared diff across contract and dependent consumers" on the right and animate the question that connects the two observations.

**Script:**

We have just inspected review waiting and library adoption as coordination cost. The next decision is different: we need to verify one shared diff across contract and dependent consumers. Follow the evidence as the view changes.

**Purpose:**

Bridge the previous result to the specific criterion one shared diff across contract and dependent consumers rather than presenting unrelated definitions.

### Scene 1 — Shared History for Observing and Reviewing Cross-Component Changes

**Time:** `02:29–03:23`

**Visual:**

Reveal one TAX-42 PR changing contracts/schema.json, api/Dto.java and web/client.ts, with separate owners and unchecked consumer-test criteria.

**Script:**

A monorepo can place a contract change and its consumers in one visible diff and one shared historical reference. Reviewers can trace how Web and API adapt when schema names change. That is a genuine advantage for cross-component migrations, but a multi-file PR is not proof that all dependent tests ran or the correct owners approved. Inspect the changed consumer paths and coverage matrix. The historical grouping improves visibility; quality still depends on explicit review and validation.

**Purpose:**

Learners must identify one shared diff across contract and dependent consumers using the displayed evidence, without substituting a repository-count assumption for a strategic decision.

## Coordinating Related Changes Across Separate Repositories

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:23–03:43`

**Visual:**

Keep the previous evidence "one shared diff across contract and dependent consumers" in the left panel; reveal "independent linked PRs rather than atomic multi-repository commit" on the right and animate the question that connects the two observations.

**Script:**

We have just inspected one shared diff across contract and dependent consumers. The next decision is different: we need to verify independent linked PRs rather than atomic multi-repository commit. Follow the evidence as the view changes.

**Purpose:**

Bridge the previous result to the specific criterion independent linked PRs rather than atomic multi-repository commit rather than presenting unrelated definitions.

### Scene 1 — Coordinating Related Changes Across Separate Repositories

**Time:** `03:43–04:37`

**Visual:**

Link TAX-42 to contracts#42, api#81 and web#17. Display separate approval and merge lanes without depicting an atomic cross-repo commit.

**Script:**

A polyrepo change may require three independently reviewed pull requests. Each repository owns its own history and integration decision, so a shared issue or change ID must link the work. A table should record each PR, responsible owner, published contract version and which consumer adopted it. There is no default Git operation that atomically commits across three separate repositories. If one PR lags, backward compatibility and staged delivery become important. Verify linked records before marking the requirement completed.

**Purpose:**

Learners must identify independent linked PRs rather than atomic multi-repository commit using the displayed evidence, without substituting a repository-count assumption for a strategic decision.

## Integration Order and Cross-Component Compatibility

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:37–04:56`

**Visual:**

Keep the previous evidence "independent linked PRs rather than atomic multi-repository commit" in the left panel; reveal "expand migrate contract with supported-version evidence" on the right and animate the question that connects the two observations.

**Script:**

We have just inspected independent linked PRs rather than atomic multi-repository commit. The next decision is different: we need to verify expand migrate contract with supported-version evidence. Follow the evidence as the view changes.

**Purpose:**

Bridge the previous result to the specific criterion expand migrate contract with supported-version evidence rather than presenting unrelated definitions.

### Scene 1 — Integration Order and Cross-Component Compatibility

**Time:** `04:56–05:50`

**Visual:**

Animate accept old+new field → upgrade Web clients → remove old field after usage evidence; show a client/server compatibility matrix at each stage.

**Script:**

A breaking interface change can force coordinated releases even when repos are separate; a shared repo cannot rescue an already deployed client. An expand–migrate–contract sequence accepts both field forms temporarily, moves consumers to the new one, and removes the old form after usage evidence supports it. This is a coordination strategy rather than a pipeline recipe. At each stage, inspect which client/server version pairs must work. Two nearby merge timestamps do not establish compatibility.

**Purpose:**

Learners must identify expand migrate contract with supported-version evidence using the displayed evidence, without substituting a repository-count assumption for a strategic decision.

## Review Accountability and Context Preservation Across Teams

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:50–06:09`

**Visual:**

Keep the previous evidence "expand migrate contract with supported-version evidence" in the left panel; reveal "reviewers for each component plus shared interface ownership" on the right and animate the question that connects the two observations.

**Script:**

We have just inspected expand migrate contract with supported-version evidence. The next decision is different: we need to verify reviewers for each component plus shared interface ownership. Follow the evidence as the view changes.

**Purpose:**

Bridge the previous result to the specific criterion reviewers for each component plus shared interface ownership rather than presenting unrelated definitions.

### Scene 1 — Review Accountability and Context Preservation Across Teams

**Time:** `06:09–07:03`

**Visual:**

Place Contracts, API and Web reviewer lanes across one diff and a three-PR alternative; highlight missing contract ownership if only Web approves.

**Script:**

A PR is not safely reviewed merely because it collected approvals. TAX-42 needs Web context, API provider expertise and someone accountable for the shared contract. A monorepo can bring these reviewers into one conversation; polyrepos may spread them across related PRs. Neither arrangement automatically supplies cross-domain judgment. Check the review matrix against the changed contract and impacted consumers. If the interface has no accountable owner, that is a visible blocker rather than a box to wave through.

**Purpose:**

Learners must identify reviewers for each component plus shared interface ownership using the displayed evidence, without substituting a repository-count assumption for a strategic decision.

## Tracing Requirements and Changes Across Components

<!-- VIDEO_SECTION -->

### Transition

**Time:** `07:03–07:23`

**Visual:**

Keep the previous evidence "reviewers for each component plus shared interface ownership" in the left panel; reveal "requirement to PRs commits versions and contract results" on the right and animate the question that connects the two observations.

**Script:**

We have just inspected reviewers for each component plus shared interface ownership. The next decision is different: we need to verify requirement to PRs commits versions and contract results. Follow the evidence as the view changes.

**Purpose:**

Bridge the previous result to the specific criterion requirement to PRs commits versions and contract results rather than presenting unrelated definitions.

### Scene 1 — Tracing Requirements and Changes Across Components

**Time:** `07:23–08:17`

**Visual:**

Show TAX-42→related PRs→commit SHAs→Contracts 2.1→API/Web declared versions→contract-test evidence, leaving deployment state unknown.

**Script:**

Traceability lets a maintainer start at a requirement and locate every source change, release and consumer adoption it depends on. A monorepo might offer one cross-area PR; a polyrepo needs explicit links among separate PRs. In either case, the record must identify which contract version the running consumer expects and what tests evaluated it. For a production problem, distinguish missing integration, outdated consumer deployment and genuine incompatibility. An issue marked Done cannot replace this evidence chain.

**Purpose:**

Learners must identify requirement to PRs commits versions and contract results using the displayed evidence, without substituting a repository-count assumption for a strategic decision.

## Evidence of Team Conflicts and Unresolved Dependencies

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:17–08:36`

**Visual:**

Keep the previous evidence "requirement to PRs commits versions and contract results" in the left panel; reveal "symptoms distinguished from repository-topology root cause" on the right and animate the question that connects the two observations.

**Script:**

We have just inspected requirement to PRs commits versions and contract results. The next decision is different: we need to verify symptoms distinguished from repository-topology root cause. Follow the evidence as the view changes.

**Purpose:**

Bridge the previous result to the specific criterion symptoms distinguished from repository-topology root cause rather than presenting unrelated definitions.

### Scene 1 — Evidence of Team Conflicts and Unresolved Dependencies

**Time:** `08:36–09:30`

**Visual:**

Investigate long reviewer queues, incompatible consumer versions, rollbacks and failed cross-version checks; tag each with plausible ownership/contract/tooling causes.

**Script:**

A delayed change is evidence of friction, not yet evidence that repository topology caused it. Reviewer queues may reflect ownership bottlenecks, incompatible API clients may reflect poor contracts, and slow validation may be a tooling problem. Any of these can occur in mono- or multi-repository teams. Collect real timestamps, failure reports and linked PR histories, then classify the cause. Splitting or consolidating repos without diagnosing the dominant constraint can reproduce the same problem in a different shape.

**Purpose:**

Learners must identify symptoms distinguished from repository-topology root cause using the displayed evidence, without substituting a repository-count assumption for a strategic decision.

## Case Study: Coordinating a Change Across Three Components

<!-- VIDEO_SECTION -->

### Transition

**Time:** `09:30–09:48`

**Visual:**

Keep the previous evidence "symptoms distinguished from repository-topology root cause" in the left panel; reveal "one requirement compared under both source topologies" on the right and animate the question that connects the two observations.

**Script:**

We have just inspected symptoms distinguished from repository-topology root cause. The next decision is different: we need to verify one requirement compared under both source topologies. Follow the evidence as the view changes.

**Purpose:**

Bridge the previous result to the specific criterion one requirement compared under both source topologies rather than presenting unrelated definitions.

### Scene 1 — Case Study: Coordinating a Change Across Three Components

**Time:** `09:48–10:42`

**Visual:**

Split the screen: a one-PR Schema/API/Web change with several owners versus a schema-release→API PR→Web PR chain and separate version decisions.

**Script:**

An address-contract change affects Schema, Checkout API and Web. A monorepo can show the migration in one diff, yet the resulting large PR may wait for several domain reviewers. A polyrepo can publish a compatible Schema version and let consumers adopt it on separate schedules, at the cost of additional linked records. Compare reviewer coverage, supported-version combinations, consumer contract results and rollback plans. These are observable differences; invented speed or failure percentages would obscure the decision.

**Purpose:**

Learners must identify one requirement compared under both source topologies using the displayed evidence, without substituting a repository-count assumption for a strategic decision.
