---
video:
  url: ""
---

# Contribution Paths and Pull Request Creation

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

## Contributing Through a Branch in a Shared Repository

<!-- VIDEO_SECTION -->

### Scene 1 — Contributing Through a Branch in a Shared Repository

**Time:** `00:00–00:55`

**Visual:**

Animate orchid/payments with main and fix-rounding work branch. Highlight the proposed edit only on the work branch; draw a PR arrow to main but no completed merge.

**Script:**

An has Write access to the shared repository and wants to fix the invoice defect. Instead of updating main directly, An prepares work on fix-rounding and proposes it through a pull request. Reviewers can inspect the difference before the shared branch changes, and configured protection rules can be evaluated. This is a common path for authorized collaborators, not a universal guarantee of merge permission. We are following the proposal through GitHub rather than teaching Git branch-creation syntax.

**Purpose:**

Show why a work branch and PR protect shared source during collaboration.

## Forks as Separate Repositories and Their Upstream Relationship

<!-- VIDEO_SECTION -->

### Transition

**Time:** `00:55–01:06`

**Visual:**

Slide An's authorized fix-rounding branch aside and introduce a separate contributor-owned repository connected to orchid/payments by a fork arrow.

**Script:**

Shared-repository contribution depends on access. What path exists when the upstream repository does not grant Write?

**Purpose:**

Show why lack of upstream Write changes the contribution route from a shared branch to a separately owned fork.

### Scene 1 — Forks as Separate Repositories and Their Upstream Relationship

**Time:** `01:06–02:00`

**Visual:**

Draw orchid/payments and contributor/payments as separate repositories. Animate a fork arrow outward and a PR arrow back to upstream/main; outline separate permission scopes.

**Script:**

A fork is another repository, with its own namespace and permissions. It is not simply a second branch inside the upstream repository. A contributor may work in that fork and propose the change back through a pull request when the repository's policies allow it. The fork does not grant push permission to upstream. In our example, the corrected invoice code lives in the contributor's repository until upstream maintainers inspect and choose whether to accept it.

**Purpose:**

Make fork ownership and proposed upstream integration visually unambiguous.

## Pull Request Head and Base Repositories and Branches

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:00–02:11`

**Visual:**

Keep the two repository boxes but attach head and base badges; momentarily point the comparison arrow toward the wrong target.

**Script:**

A fork and upstream repository can both appear in one pull request. How do we prevent an inverted or misdirected proposal?

**Purpose:**

Translate the fork/upstream distinction into a PR direction check before review begins.

### Scene 1 — Pull Request Head and Base Repositories and Branches

**Time:** `02:11–03:07`

**Visual:**

Label head contributor/payments:fix-rounding and base orchid/payments:main. Briefly invert the endpoints to show a wrong-destination warning, then restore the intended comparison.

**Script:**

Always inspect both endpoints. The head is the source repository and branch carrying the proposed edit. The base is the destination repository and branch that might receive it. A fork can have a differently named branch, and matching names do not make the direction correct. In our PR number 57 storyboard, the correction travels from fix-rounding toward main. Choosing a maintenance branch unintentionally could produce a misleading comparison, so the reviewer should check the target before looking at the diff.

**Purpose:**

Teach correct PR direction through a concrete head/base comparison.

## Draft Pull Requests vs Ready-for-Review Proposals

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:07–03:18`

**Visual:**

Hold the correctly directed head/base arrow; overlay a Draft badge and a missing-test checklist on PR #57.

**Script:**

A correctly targeted PR can still be unfinished. How can the author ask for early feedback without implying merge readiness?

**Purpose:**

Separate a well-targeted comparison from the decision to invite formal review.

### Scene 1 — Draft Pull Requests vs Ready-for-Review Proposals

**Time:** `03:18–04:13`

**Visual:**

Show Draft PR on a timeline with comments available and Merge disabled; only on Ready for review reveal eligible automatic CODEOWNERS review requests.

**Script:**

An can open a Draft pull request while the idea still needs tests or design feedback. Teammates can inspect and comment on it, but a draft cannot be merged. When An selects Ready for review, GitHub can automatically request matching code owners if CODEOWNERS and access conditions apply. The request is a request, not an approval. This timeline distinguishes early collaboration from a claim that the proposal is ready to be evaluated under the target branch's rules.

**Purpose:**

Show draft gating and the timing of automatic code-owner requests without equating requests with approvals.

## The Pull Request Lifecycle: Open, Update, Discuss, Merge, or Close

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:13–04:24`

**Visual:**

Switch the Draft badge to Ready and open a forked state diagram for updated, merged, and closed-without-merge outcomes.

**Script:**

Ready for review starts evaluation; it does not guarantee a favorable outcome. What states can follow?

**Purpose:**

Carry readiness into the PR lifecycle without implying that a ready proposal will necessarily merge.

### Scene 1 — The Pull Request Lifecycle: Open, Update, Discuss, Merge, or Close

**Time:** `04:24–05:20`

**Visual:**

Show Open → commits/review → Merged or Closed without merge. Highlight Files changed updating after another head commit and distinguish the two terminal paths.

**Script:**

A PR can receive more commits, comments, and review decisions before any final action. The author may revise the head branch, and reviewers should inspect the updated difference. Some proposals merge; others are closed without merging. The Closed label alone is not enough to prove main contains the fix. We need the Merged outcome and evidence at the destination. Nor should we assume a linked Issue or Release changed state automatically; those are separate records we will revisit later.

**Purpose:**

Give a reliable state-transition model for PR completion without inventing hosted results.
