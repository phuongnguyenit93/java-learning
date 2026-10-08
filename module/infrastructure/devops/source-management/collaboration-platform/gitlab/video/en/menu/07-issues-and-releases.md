---
video:
  url: ""
---

# GitLab Issues and Releases: Work Tracking and Release Metadata

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

## GitLab Issues as Records for Team Work and Discussion

<!-- VIDEO_SECTION -->

### Scene 1 — GitLab Issues as Records for Team Work and Discussion

**Time:** `00:00–01:00`

**Visual:**

Show illustrative Issue #42 with input -1, expected rejection, and observed behavior. Place source commits and deployment as separate downstream cards.

**Script:**

An Issue records a defect or work item before the code changes. In our example, a negative invoice amount produces unacceptable behavior, so An captures the input and expected rejection along with enough detail to reproduce it. Teammates can discuss scope and assign responsibility. But neither opening nor closing the Issue is itself a commit or deployment event. When reviewing Issue number 42, first ask whether the documented behavior gives someone enough evidence to evaluate the correction later proposed in MR number 57.

**Purpose:**

Establish requirements and reproducibility as separate evidence from source integration.

## Issue Assignees, Labels, and Milestones

<!-- VIDEO_SECTION -->

### Transition

**Time:** `01:00–01:13`

**Visual:**

Keep the previous scene’s evidence as a smaller left panel, highlight "an Issue records the need, not an implemented fix", then reveal the next question on the right with a scope-change arrow.

**Script:**

Once the issue is understood, the team needs planning metadata that should not be confused with delivery.

**Purpose:**

Bridge the earlier observation about an Issue records the need, not an implemented fix to the next decision about planning metadata does not prove implementation without resetting the case.

### Scene 1 — Issue Assignees, Labels, and Milestones

**Time:** `01:13–02:13`

**Visual:**

Attach Assignee An, Label bug, and Milestone v1.4.0 to Issue #42. Keep Merged and Release outcomes as separate question-mark cards.

**Script:**

Assignees identify who coordinates work, labels help filter issues, and milestones group work toward a planned goal. None of these fields proves an MR was accepted into main. Assigning Issue number 42 to An and placing it in v1.4.0 makes the plan visible, but reviewers still need the MR and merge record. A team-defined ready label is not a GitLab permission to merge. If responsibility changes, update the assignee and the discussion context rather than treating the label or milestone as completion evidence.

**Purpose:**

Teach operational work organization while protecting the acceptance and release evidence boundary.

## Linking Issues to Merge Requests and Default-Branch Closing Conditions

<!-- VIDEO_SECTION -->

### Transition

**Time:** `02:13–02:26`

**Visual:**

Keep the previous scene’s evidence as a smaller left panel, highlight "planning metadata does not prove implementation", then reveal the next question on the right with a scope-change arrow.

**Script:**

Traceability connects Issue and MR; the next question is whether and when the work item changes state.

**Purpose:**

Bridge the earlier observation about planning metadata does not prove implementation to the next decision about linking an MR differs from automatically closing the Issue without resetting the case.

### Scene 1 — Linking Issues to Merge Requests and Default-Branch Closing Conditions

**Time:** `02:26–03:26`

**Visual:**

Draw Issue #42 linked to MR !57 with the illustrative phrase Closes #42. Compare an MR targeting default main versus a release branch, leaving the Issue Closed outcome unfilled.

**Script:**

An Issue-to-MR link lets observers follow a requirement toward its proposed fix. GitLab supports closing patterns such as Closes in an MR description, but automatic closure generally depends on integration into the default branch. An MR targeting a different release branch should not be assumed to have the same effect. Our storyboard can show the link immediately while leaving the Issue's Closed state unknown until the actual timeline supports it. Closing an MR without merging also cannot prove the defect was corrected.

**Purpose:**

Teach conditional closing behavior without presenting a keyword as guaranteed completion.

## Git Tags Versus GitLab Releases

<!-- VIDEO_SECTION -->

### Transition

**Time:** `03:26–03:39`

**Visual:**

Keep the previous scene’s evidence as a smaller left panel, highlight "linking an MR differs from automatically closing the Issue", then reveal the next question on the right with a scope-change arrow.

**Script:**

Accepted source can be identified by a version tag, but publication is another action.

**Purpose:**

Bridge the earlier observation about linking an MR differs from automatically closing the Issue to the next decision about a Git tag, GitLab Release and deployment are distinct without resetting the case.

### Scene 1 — Git Tags Versus GitLab Releases

**Time:** `03:39–04:42`

**Visual:**

Show tag v1.4.0 pointing to a commit, a separate GitLab Release card with date/notes/assets, and an independent production deployment column.

**Script:**

After a change is accepted, a team may place a Git tag on a commit to identify a version point. A GitLab Release is a publication record associated with that tag, including a title, date, notes, and optional assets. Creating a tag does not guarantee a Release was published, and seeing a Release does not prove production was deployed from it. Our v1.4.0 correction is hypothetical; verify the tag, target commit, and actual release page before announcing delivery. The mechanics of creating Git tags belong to the Git module.

**Purpose:**

Keep version references, publication records and running deployments conceptually separate.

## Release Notes, Assets, and the Versioning/Deployment Boundary

<!-- VIDEO_SECTION -->

### Transition

**Time:** `04:42–04:55`

**Visual:**

Keep the previous scene’s evidence as a smaller left panel, highlight "a Git tag, GitLab Release and deployment are distinct", then reveal the next question on the right with a scope-change arrow.

**Script:**

A Release record becomes useful only when its notes and linked assets accurately describe what was accepted.

**Purpose:**

Bridge the earlier observation about a Git tag, GitLab Release and deployment are distinct to the next decision about notes and assets communicate verifiable release information without resetting the case.

### Scene 1 — Release Notes, Assets, and the Versioning/Deployment Boundary

**Time:** `04:55–05:58`

**Visual:**

Mock a GitLab Release v1.4.0 page with Fixed negative amounts, Compatibility, Documentation, and an assets panel. Put build, signing, and deployment on another workflow lane.

**Script:**

Users need more than a version label. Release notes should describe meaningful fixes, compatibility changes, and where to learn more. They should be checked against the accepted MR, relevant Issue, and tagged commit rather than invented from a planned milestone. Assets may be downloadable files or links to distributions, but their presence does not replace building, signing, or deploying software. Our release card is a proposed narrative, not evidence that an artifact has been uploaded. If no actual Release exists, report only the verified source or tag state.

**Purpose:**

Illustrate responsible communication and the separation of release metadata from delivery pipelines.
