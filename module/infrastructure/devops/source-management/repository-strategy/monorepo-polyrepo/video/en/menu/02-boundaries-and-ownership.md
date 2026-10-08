---
video:
  url: ""
---

# Repository Boundaries and Code Ownership

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

## Code Ownership: Definition, Purpose, and Accountability

<!-- VIDEO_SECTION -->

### Scene 1 — Code Ownership: Definition, Purpose, and Accountability

**Time:** `00:00–00:54`

**Visual:**

Highlight libs/contracts with primary Platform owner, Checkout backup and Web/API consumers. Use separate icons for reviewing and source reading.

**Script:**

Code ownership is a commitment about who understands a source area and who responds when it changes or fails. For Contracts, Platform might be primary, with a Checkout backup and Web as a consumer. That does not make Platform the only group allowed to read or edit the files. If CODEOWNERS lists a team nobody can reach, accountability remains weak. Verify reviewer contact, actual review permissions and a fallback escalation path rather than treating the file as governance completed.

**Purpose:**

Learners must identify code accountability separate from exclusive source access using the displayed evidence, without substituting a repository-count assumption for a strategic decision.

## Ownership by Directory or Component in a Monorepo

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:54–01:13`

**Visual:**

Keep the previous evidence "code accountability separate from exclusive source access" in the left panel; reveal "path review accountability within one Git root" on the right and animate the question that connects the two observations.

**Script:**

We have just inspected code accountability separate from exclusive source access. The next decision is different: we need to verify path review accountability within one Git root. Follow the evidence as the view changes.

**Purpose:**

Bridge the previous result to the specific criterion path review accountability within one Git root rather than presenting unrelated definitions.

### Scene 1 — Ownership by Directory or Component in a Monorepo

**Time:** `01:13–02:07`

**Visual:**

Show apps/web, libs/auth and services/api with Web, Security and API owners. A cross-path PR highlights two distinct reviewer lanes.

**Script:**

A monorepo can assign responsibility by directory or component even though everyone works under one Git root. A PR touching both apps/web and libs/auth needs relevant expertise from Web and Security. CODEOWNERS can help route review requests on supported hosts, while mandatory approval depends on configured branch rules and eligibility. The path rule does not create read isolation or guarantee anyone reviewed the change. Inspect owner coverage and the actual required-review settings before declaring it governed.

**Purpose:**

Learners must identify path review accountability within one Git root using the displayed evidence, without substituting a repository-count assumption for a strategic decision.

## Ownership by Individual Repository in a Polyrepo

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:07–02:25`

**Visual:**

Keep the previous evidence "path review accountability within one Git root" in the left panel; reveal "repository ownership plus cross-repository contract ownership" on the right and animate the question that connects the two observations.

**Script:**

We have just inspected path review accountability within one Git root. The next decision is different: we need to verify repository ownership plus cross-repository contract ownership. Follow the evidence as the view changes.

**Purpose:**

Bridge the previous result to the specific criterion repository ownership plus cross-repository contract ownership rather than presenting unrelated definitions.

### Scene 1 — Ownership by Individual Repository in a Polyrepo

**Time:** `02:25–03:19`

**Visual:**

Give checkout-api.git and identity.git separate maintainer and reviewer cards; draw an interface contract edge with joint API-provider/consumer accountability.

**Script:**

In a polyrepo, the repository boundary often makes the primary maintenance contact easier to locate. Checkout owns checkout-api and Identity owns its own source. Yet Checkout may consume Identity's authentication interface, so both sides must own compatibility and notification when it changes. An owner label on each repository does not identify who reviews the shared contract. Assign responsibility both at the repo and across the dependency edge, then verify that linked PRs reach those reviewers.

**Purpose:**

Learners must identify repository ownership plus cross-repository contract ownership using the displayed evidence, without substituting a repository-count assumption for a strategic decision.

## Code Discoverability and Reuse Across Repository Topologies

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:19–03:37`

**Visual:**

Keep the previous evidence "repository ownership plus cross-repository contract ownership" in the left panel; reveal "finding APIs and consumers through search evidence" on the right and animate the question that connects the two observations.

**Script:**

We have just inspected repository ownership plus cross-repository contract ownership. The next decision is different: we need to verify finding APIs and consumers through search evidence. Follow the evidence as the view changes.

**Purpose:**

Bridge the previous result to the specific criterion finding APIs and consumers through search evidence rather than presenting unrelated definitions.

### Scene 1 — Code Discoverability and Reuse Across Repository Topologies

**Time:** `03:37–04:31`

**Visual:**

Compare searching AuthClient in a monorepo with searching a multi-repo catalog/index; highlight API definition, Web usage and API usage, then a separate consumer-test field.

**Script:**

A shared library helps only when teams can find it and understand how it is consumed. In a monorepo, searching AuthClient may expose both the implementation and examples in Web and API. Multiple repositories can offer similar discovery through indexing and catalogs. Neither layout guarantees correct reuse by itself. Measure how long it takes to find an owner and trustworthy usage example, and then validate the consumer contract rather than inferring compatibility from a search result.

**Purpose:**

Learners must identify finding APIs and consumers through search evidence using the displayed evidence, without substituting a repository-count assumption for a strategic decision.

## Code Ownership Versus Source Access Permissions

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:31–04:50`

**Visual:**

Keep the previous evidence "finding APIs and consumers through search evidence" in the left panel; reveal "review gates do not grant directory-level read isolation" on the right and animate the question that connects the two observations.

**Script:**

We have just inspected finding APIs and consumers through search evidence. The next decision is different: we need to verify review gates do not grant directory-level read isolation. Follow the evidence as the view changes.

**Purpose:**

Bridge the previous result to the specific criterion review gates do not grant directory-level read isolation rather than presenting unrelated definitions.

### Scene 1 — Code Ownership Versus Source Access Permissions

**Time:** `04:50–05:44`

**Visual:**

Draw one Web+Fraud repository readable by a vendor; highlight a Fraud CODEOWNERS review gate without hiding the directory, then show separated Fraud read permissions.

**Script:**

Suppose a vendor needs the Web source but Fraud contains restricted algorithms. If both live in a repository the vendor may clone, a Fraud CODEOWNERS rule does not conceal those files. It routes or requires review of modifications under suitable policies; reading and approval are different controls. If confidentiality demands isolation, verify a real hosting access boundary instead of relying on folder names or reviewer patterns. Separate repositories may be warranted even though they increase integration coordination.

**Purpose:**

Learners must identify review gates do not grant directory-level read isolation using the displayed evidence, without substituting a repository-count assumption for a strategic decision.

## Team Boundaries Need Not Match Repository Boundaries

<!-- VIDEO_SECTION -->

### Transition

**Time:** `05:44–06:03`

**Visual:**

Keep the previous evidence "review gates do not grant directory-level read isolation" in the left panel; reveal "organizational chart versus stable source dependency edges" on the right and animate the question that connects the two observations.

**Script:**

We have just inspected review gates do not grant directory-level read isolation. The next decision is different: we need to verify organizational chart versus stable source dependency edges. Follow the evidence as the view changes.

**Purpose:**

Bridge the previous result to the specific criterion organizational chart versus stable source dependency edges rather than presenting unrelated definitions.

### Scene 1 — Team Boundaries Need Not Match Repository Boundaries

**Time:** `06:03–06:57`

**Visual:**

Rearrange Web and Payments team boxes in an org chart while preserving Web→Contracts→Payments dependencies and existing repository boundaries.

**Script:**

Teams can be renamed, combined or split without changing the underlying API dependencies. If every organizational adjustment forces a repository migration, the topology may be coupled to a volatile org chart rather than the source relationships. A single team can also own multiple repositories for good security or lifecycle reasons. Keep the dependency edges visible while moving the team cards. Then ask which boundaries are stable enough to justify a Git history and access decision.

**Purpose:**

Learners must identify organizational chart versus stable source dependency edges using the displayed evidence, without substituting a repository-count assumption for a strategic decision.

## Service Count Does Not Determine Repository Count

<!-- VIDEO_SECTION -->

### Transition

**Time:** `06:57–07:17`

**Visual:**

Keep the previous evidence "organizational chart versus stable source dependency edges" in the left panel; reveal "the number of services and repositories are independent counts" on the right and animate the question that connects the two observations.

**Script:**

We have just inspected organizational chart versus stable source dependency edges. The next decision is different: we need to verify the number of services and repositories are independent counts. Follow the evidence as the view changes.

**Purpose:**

Bridge the previous result to the specific criterion the number of services and repositories are independent counts rather than presenting unrelated definitions.

### Scene 1 — Service Count Does Not Determine Repository Count

**Time:** `07:17–08:11`

**Visual:**

Place five independently deployed services inside one Git root and one service consuming schemas across two repositories. Keep deployment arrows separate.

**Script:**

Five running services can live in one source repository while releasing on different schedules. A single running service can also depend on source or schemas maintained in two separate repositories. Counting deployable services therefore does not reveal the right source topology. Add repository roots, owner information, read permissions and which contracts or PRs commonly change together. These relationships—not a count of containers—show the actual source-coordination burden.

**Purpose:**

Learners must identify the number of services and repositories are independent counts using the displayed evidence, without substituting a repository-count assumption for a strategic decision.

## Evaluating Ownership Areas and Overlap on a Repository Map

<!-- VIDEO_SECTION -->

### Transition

**Time:** `08:11–08:32`

**Visual:**

Keep the previous evidence "the number of services and repositories are independent counts" in the left panel; reveal "a verified owner map exposes orphaned or overlapping areas" on the right and animate the question that connects the two observations.

**Script:**

We have just inspected the number of services and repositories are independent counts. The next decision is different: we need to verify a verified owner map exposes orphaned or overlapping areas. Follow the evidence as the view changes.

**Purpose:**

Bridge the previous result to the specific criterion a verified owner map exposes orphaned or overlapping areas rather than presenting unrelated definitions.

### Scene 1 — Evaluating Ownership Areas and Overlap on a Repository Map

**Time:** `08:32–09:26`

**Visual:**

Build an ownership matrix with repo/path, primary, backup, consumers, reviewers, read/write access and escalation. Mark libs/contracts with no backup or unclear schema review.

**Script:**

Make ownership reviewable rather than a slogan. Every meaningful source area should identify a primary contact, backup, affected consumers and the escalation route when someone is unavailable. For libs/contracts, Platform might maintain source while API and Web validate compatibility. If the ownership matrix has no backup or no schema reviewer, that gap can matter more than whether the code shares a root. Look for misrouted PR requests and waiting time before proposing a topology change.

**Purpose:**

Learners must identify a verified owner map exposes orphaned or overlapping areas using the displayed evidence, without substituting a repository-count assumption for a strategic decision.
