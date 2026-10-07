# GOAL — PARENT MODULE HORIZONTAL STEP WORKFLOW

This file is a **shared execution/orchestration Goal** for processing a parent module that contains multiple target child modules.

For a full parent-module run, the default numbered entry point is:

```text
START = STEP 2
```

An invoking request may explicitly start/resume at a later canonical STEP (STEP 3 or later). That is treated as a **partial/resume run**, not as permission to fall back to `main` or repository root.

It does **not** hard-code:

- how many STEPs currently exist;
- which STEP number currently belongs to Roadmap, Menu, Knowledge, API, Video, Quiz, Interview, Validation, Commit, or any future phase;
- the final STEP number;
- the filenames of future STEP rules;
- the parent module path;
- the child-module list;
- the child-module worktree paths;
- a technology-specific Curriculum path.

The parent module, target child modules, and their branch/worktree locations are supplied by the invoking request/session context and CURRENT repository state.

The CURRENT repository governance determines the workflow after the actual requested start STEP.

### Mandatory rules-folder loading gate

Before this Goal performs any parent orchestration, child authoring/review, worker scheduling, validation, write transfer, phase transition, or long-running wait, it must enumerate and read **every current file** under [`../../rules/`](../../rules/) in full.

Do not cherry-pick only a known rule. The complete current `rules/` folder is mandatory cross-cutting execution context.

If the rules folder changes materially during a long-running parent run, re-enumerate and re-read the complete folder before the next parent phase or canonical STEP begins.

---

## 1. PRIMARY OBJECTIVE

Process the parent module **horizontally by STEP**.

The execution unit is:

```text
1 child module × 1 CURRENT STEP
```

The parent-level progression is:

```text
STEP 2
├─ child module 1 → CLEAN
├─ child module 2 → CLEAN
├─ child module 3 → CLEAN
└─ ... every target child module → CLEAN
        ↓
resolve CURRENT next canonical STEP
        ↓
next STEP
├─ child module 1 → CLEAN
├─ child module 2 → CLEAN
└─ ... every target child module → CLEAN
        ↓
continue until CURRENT canonical Commit / final-delivery STEP
```

Do **not** complete STEP 2 through final delivery for one child before beginning STEP 2 for its siblings.

Instead:

```text
finish CURRENT STEP for ALL target children
        ↓
only then advance the parent workflow to the NEXT canonical STEP
```

This horizontal rule exists to keep sibling modules aligned to the same current STEP contract, curriculum boundary, and review standard.

---

## 2. WORKFLOW AUTHORITY

This Goal owns only **parent-level orchestration, worker scheduling, worktree ownership, review routing, and STEP barriers**.

Authority is split as follows:

```text
PARENT_MODULE_STEP_WORKFLOW.md
→ process sibling child modules horizontally by STEP
→ parent-wide phase scheduling across the full target-child set
→ explicit transferable write ownership with no concurrent writers
→ independent-review routing that preserves reviewer-vs-initial-author separation
→ worktree isolation rules
→ STEP barrier across all children

rules/**
→ mandatory cross-cutting execution rules loaded in full before work begins
→ worker concurrency limit is owned outside this Goal
→ heartbeat/liveness behavior is owned outside this Goal

GENERAL_AGENT_RULES.md
→ CURRENT canonical workflow graph/order
→ CURRENT STEP numbering
→ CURRENT STEP-to-rule-file routing
→ global module-generation governance

CURRENT STEP rule file
→ complete authoring / schema / validation / review rules for that STEP
```

Do not duplicate STEP-specific content rules in this Goal.

When anything conflicts:

```text
current repository architecture/governance
        ↓
CURRENT GENERAL_AGENT_RULES.md
        ↓
CURRENT canonical rule file for the STEP being executed
        ↓
this Goal's generic orchestration wording
        ↓
remembered assumptions
```

The higher applicable authority wins.

---

## 3. RESOLVE THE TARGET CHILD SET AND WORKTREES FIRST

Before any authoring begins, the Prime must establish the exact execution inventory.

Resolve and record:

```text
parent module

target child module 1
→ module path
→ branch
→ worktree path

target child module 2
→ module path
→ branch
→ worktree path

...
```

Never assume a child should be edited in repository root merely because the same module path exists there.

If the invoking context says each child module owns a dedicated branch/worktree, every write for that child must occur in that exact worktree.

The Prime must treat the mapping:

```text
child module ↔ branch ↔ worktree
```

as durable orchestration state and preserve it across worker handoffs.

If the mapping is ambiguous, resolve it from CURRENT repository/worktree state before any edit.

### STEP 2 versus STEP 3+ entry behavior

When a full run begins at STEP 2, dedicated branches/worktrees may need to be created if CURRENT governance requires them and they do not already exist.

When an invoking request begins/resumes at **STEP 3 or later**, assume the module branches/worktrees normally already exist from earlier STEP work. In that case:

```text
DO NOT default to main
DO NOT edit the repository-root copy of the module merely because it exists
DO NOT create a replacement worktree just for convenience

INSTEAD:
→ inspect CURRENT git worktree/branch state
→ locate the existing branch/worktree for each target child
→ verify that it is the branch carrying that child's prior STEP changes
→ continue the requested STEP inside that exact worktree
```

If more than one candidate worktree exists, resolve the correct one from branch identity, target-module state, and CURRENT repository evidence before writing.

Only create a missing branch/worktree during STEP 3+ when CURRENT governance/request explicitly requires creation and no valid existing worktree can be found.

---

## 4. GLOBAL GOVERNANCE CONSISTENCY ACROSS WORKTREES

Separate worktrees isolate child-module changes, but they can also carry different versions of shared governance.

Before starting a CURRENT STEP across the child set, verify that the governing files needed for that STEP are compatible across the participating worktrees.

At minimum, check the CURRENT versions of:

```text
module-generate-agent/GENERAL_AGENT_RULES.md
CURRENT canonical STEP rule file
STEP-range context inputs defined below
```

### Mandatory context-loading contract by STEP range

Before authoring **or reviewing** any child for the CURRENT STEP, Prime and the assigned execution lane must work from fresh CURRENT context, not remembered summaries.

Always load:

```text
CURRENT module-generate-agent/GENERAL_AGENT_RULES.md
        +
CURRENT canonical STEP rule file resolved from that governance
        +
CURRENT target-child module state / required upstream artifacts
```

Then apply this explicit Curriculum boundary:

```text
STEP 2 / STEP 3 / STEP 4 / STEP 5
→ resolve the relevant Curriculum Map for the actual parent/child scope
→ when substantive Curriculum content exists, READ and USE it

STEP 6 AND LATER
→ Curriculum is NOT a required execution input
→ do not reload/search Curriculum merely by habit
→ derive the STEP from CURRENT module content + CURRENT approved/upstream artifacts
  + source/runtime/repository evidence required by the CURRENT STEP rule
```

For STEP 2–5, a Curriculum filename existing is not enough. Treat a missing file, empty file, title-only scaffold, placeholder-only artifact, or a Curriculum that contains no substantive scope/ownership guidance for the target as **no usable Curriculum content**.

When STEP 2–5 has no usable Curriculum content:

```text
inspect the CURRENT child/module content
        +
inspect CURRENT upstream artifacts/repository evidence that already exists
        ↓
substantive grounding exists?
   ├─ YES → continue from that CURRENT content according to GENERAL + CURRENT STEP rules
   └─ NO  → STOP the affected child instead of inventing scope/content
```

If an affected child stops for this reason, report the missing context clearly. Because the parent cannot advance to the next canonical STEP until every target child satisfies the current barrier, the parent workflow remains blocked until the missing context is supplied/resolved or CURRENT governance changes the target set.

Do not use external knowledge alone to silently bootstrap a completely empty STEP 2–5 child when both usable Curriculum content and substantive CURRENT module/upstream content are absent in this parent workflow.

For STEP 6+, the grounding source is the **CURRENT module**, not the Curriculum Map. If the CURRENT module/upstream artifacts required by that STEP are missing or insufficient, route/stop according to the CURRENT STEP and upstream-defect rules rather than falling back to Curriculum.

Do not silently assume two worktrees contain equivalent rules because they came from the same repository historically.

If meaningful governance drift exists:

```text
STOP authoring for the affected worktree(s)
        ↓
identify the canonical current rule source
        ↓
resolve/synchronize according to repository governance
        ↓
only then continue the STEP
```

Do not cherry-pick, rebase, overwrite, or synchronize branches merely to make them match unless the current request/governance authorizes that operation.

---

## 5. DYNAMIC STEP ENTRY AND TRANSITION

### Initial entry

At Goal start, resolve the actual requested start STEP first.

For the normal full run:

```text
1. read CURRENT GENERAL_AGENT_RULES.md from disk;
2. enumerate CURRENT module-generate-agent/rules/;
3. read EVERY current file in rules/ in full;
4. inspect current repository/worktree state;
5. resolve the parent and target child-module inventory;
6. resolve what CURRENT canonical STEP 2 is and which rule file owns it;
7. read that CURRENT STEP 2 rule file from disk;
8. apply the STEP-range context-loading contract above, including relevant Curriculum handling for STEP 2;
9. verify shared governance compatibility across participating worktrees;
10. execute STEP 2 horizontally across every target child module while applying all loaded cross-cutting rules.
```

For an explicit STEP 3+ partial/resume run:

```text
1. read CURRENT GENERAL_AGENT_RULES.md from disk;
2. enumerate CURRENT module-generate-agent/rules/;
3. read EVERY current file in rules/ in full;
4. inspect CURRENT repository/worktree state;
5. resolve the parent and target child-module inventory;
6. locate the EXISTING correct branch/worktree for every target child;
7. verify prior STEP state is present there;
8. resolve the requested canonical STEP and its CURRENT rule file;
9. read that rule file from disk;
10. apply the STEP-range context-loading contract above;
11. verify shared governance compatibility across participating worktrees;
12. execute the requested STEP horizontally across every target child module while applying all loaded cross-cutting rules.
```

Do not replay STEP 2 merely because this Goal's normal full-run entry is STEP 2 when the invoking request explicitly authorizes a later-step partial/resume run.

### Every later transition

The next canonical STEP may begin only after every target child module is CLEAN for the current STEP.

Required transition:

```text
ALL TARGET CHILDREN CLEAN FOR CURRENT STEP
        ↓
finish required parent-level/current-STEP validation, if any
        ↓
inspect CURRENT repository/worktree state
        ↓
RE-READ CURRENT GENERAL_AGENT_RULES.md FROM DISK
        ↓
resolve the NEXT canonical STEP and its CURRENT delegated rule file
        ↓
READ THAT STEP RULE FILE FROM DISK
        ↓
APPLY THE STEP-RANGE CONTEXT-LOADING CONTRACT
→ STEP 2–5: Curriculum when substantive, otherwise CURRENT content fallback; stop if neither exists
→ STEP 6+: CURRENT module/upstream content; Curriculum not required
        ↓
verify governance compatibility across target worktrees
        ↓
execute NEXT STEP horizontally across all target children
```

Never infer the next STEP from memory, old summaries, numeric guessing, or historical workflow order.

---

## 6. CROSS-CUTTING WORKER RULES + WRITE OWNERSHIP

The worker-pool limit, ACTIVE + SLEEPING counting semantics, mandatory sleeping-worker reuse-before-spawn rule, and reusable-lane behavior are intentionally **not owned by this Goal**.

They are loaded from the mandatory `../../rules/` set, currently including `WORKER_CONCURRENCY.md`.

This Goal continues to own parent-specific assignment eligibility, phase scheduling, write ownership, review routing, and barriers.

Prime remains the scheduler and source of parent-orchestration truth even when a cross-cutting rule permits Prime to execute one eligible assignment directly. Before every worker spawn decision, Prime must apply the CURRENT worker-pool state rules from `WORKER_CONCURRENCY.md`.

Whenever Prime has dispatched work but temporarily has no eligible immediate assignment, apply the heartbeat/liveness behavior from the mandatory `rules/` set rather than manufacturing worker traffic or filler work.

### Write ownership is exclusive but transferable

Every child branch/worktree has at most **one active writer at a time**.

The initial author is the preferred fixer when convenient because that preserves context, but fix ownership may be transferred to Prime or another worker when that is simpler or more efficient.

Transfer rules:

```text
before writing:
→ identify the child module
→ identify its exact branch
→ identify its exact worktree
→ identify the CURRENT write owner

when ownership transfers:
→ previous writer stops writing that child
→ Prime records/knows the new write owner
→ new writer uses the SAME child branch/worktree
→ never allow concurrent writers
```

A reviewer may become the fixer after the review has finished. Review independence is preserved by requiring the reviewer to be different from the **initial author** of that child for the CURRENT STEP; the reviewer is not required to remain permanently read-only afterward.

The initial author must still be tracked durably because reviewer eligibility depends on it even if later write ownership changes.

---

## 7. PARENT-WIDE PHASE SCHEDULING ACROSS ALL TARGET CHILDREN

Do not group children into lifecycle waves.

The CURRENT STEP is scheduled across the **entire target-child set** using parent-wide phases.

The high-level model is:

```text
PHASE A — IMPLEMENT / AUTHOR ALL N CHILDREN
        ↓
ALL N IMPLEMENTED + REQUIRED PRE-REVIEW VALIDATION COMPLETE
        ↓
PHASE B — REVIEW #1 ACROSS ALL N CHILDREN
        ↓
ALL N HAVE COMPLETED REVIEW #1
        ↓
PHASE C — REVIEW #2 BECOMES GLOBALLY ELIGIBLE
        ↓
EVERY CHILD EVENTUALLY COMPLETES REVIEW #2
        ↓
REVIEW #3+ ONLY FOR AFFECTED CHILDREN WHEN NEEDED
        ↓
ALL N CLEAN
        ↓
PARENT-LEVEL CONSISTENCY CHECK
        ↓
NEXT CANONICAL STEP
```

The execution lanes allowed by the mandatory cross-cutting worker rule are a **worker-pool limit**, not a child grouping boundary. A SLEEPING worker remains in that pool and must be reused according to the cross-cutting rule rather than replaced by a newly spawned worker.

Example with many children:

```text
IMPLEMENT PHASE
available lane → child A → then another eligible child ...
available lane → child B → then another eligible child ...
available lane → child C → then another eligible child ...

When a lane becomes free:
→ take the next eligible child assignment in the same CURRENT parent phase
```

### Implement barrier before Review #1

Review #1 does not open until **all target children** have completed CURRENT-STEP implementation plus whatever pre-review validation the CURRENT STEP rule requires.

Therefore this is forbidden:

```text
child A implementation done
→ start Review #1 A
while child B/C/... have not yet finished implementation
```

Required:

```text
ALL N children implementation complete
        ↓
open Review #1 phase
```

### Review #1 fixes may happen immediately per child

Once Review #1 is open, review assignments may be processed in any efficient order.

When one child's Review #1 finishes:

```text
Review #1 child A complete
→ adjudicate findings
→ transfer/assign write ownership if needed
→ fix accepted findings immediately
→ revalidate child A immediately
```

Do **not** force child A to wait for Review #1 of every sibling before applying its own Review #1 fixes.

The worker that becomes free after finishing/fixing one child should continue taking other eligible **Review #1 assignments** until Review #1 has covered the full target-child set.

### Review #2 global eligibility barrier

Review #2 for any child may begin only after:

```text
1. Review #1 has been completed for ALL target children; and
2. that specific child's Review #1 findings have been adjudicated;
3. all accepted fixes for that child have been applied; and
4. that child has completed required revalidation.
```

Once condition (1) is true, Review #2 is globally open. It does **not** need to wait for every sibling to finish Review #1 fixes.

Example:

```text
ALL children have Review #1 result

child A → Review #1 fixed + revalidated → Review #2 eligible now
child B → still applying Review #1 fixes → not yet Review #2 eligible
child C → Review #1 fixed + revalidated → Review #2 eligible now
```

Free lanes may immediately start Review #2 for A/C while B continues its Review #1 fix/revalidation work.

Never start Review #2 from a pre-fix source state.

### Severe governance blocker may interrupt the current review phase

The normal expectation is to finish the parent-wide Review #1 coverage before opening Review #2. However, a **genuine severe governance / Curriculum / ownership / shared-infrastructure blocker** does not need to wait for the rest of Review #1 to finish.

When such a blocker is discovered:

```text
STOP unsafe/affected review or authoring work
        ↓
route the blocker to its canonical owner immediately
        ↓
resolve/synchronize the governing state according to CURRENT rules
        ↓
determine which already-reviewed/implemented children are materially affected
        ↓
reopen/revalidate those affected child states as required
        ↓
resume the parent-wide phase only when it is safe
```

Do not use this exception for ordinary child-local findings. It is for blockers whose unresolved state would make continued sibling review unreliable or invalid.

### No next canonical STEP early

Regardless of intra-STEP scheduling flexibility:

```text
NO next canonical STEP
until ALL target children are CLEAN for the CURRENT STEP
and required parent-level checks pass.
```

---

## 7A. FRESH SUB-WORKER EXACT CHATGPT TITLE CONTRACT

Whenever Prime opens/spawns a **new sub-worker session**, Prime must construct one **exact intended ChatGPT conversation title** before sending the first task.

Required title pattern:

```text
STEP <N> - <short task description>
```

Examples:

```text
STEP 2 - Author Roadmap + Reference for Auto Configuration
STEP 2 - Kiểm tra hoàn tất Roadmap Fundamentals
STEP 4 - Review Knowledge Externalized Configuration
STEP 7 - Fix Quiz Testing
```

The STEP prefix must reflect the actual CURRENT canonical STEP being executed, not a historical hard-coded number.

The first worker task should begin with the exact title requirement, before the normal task body. Preferred structure:

```text
CHATGPT CONVERSATION TITLE:
STEP <N> - <short task description>

Use exactly the title above for this ChatGPT conversation if conversation-title control is available.
Do not translate, shorten, paraphrase, or replace it.

TASK:
<normal worker task starts here>
```

Prime should also use the same exact `STEP <N> - ...` text as the Chat On Steroids worker label/title or task headline when that field is available.

The intended result is therefore:

```text
Chat On Steroids worker title
→ STEP <N> - <short task description>

Underlying ChatGPT conversation title
→ STEP <N> - <short task description>
```

If the product/session runtime auto-generates the ChatGPT sidebar title and does not expose direct title control, the prompt cannot guarantee the UI title. Still include the exact-title block above in the first worker task so the intended title is explicit and the title generator has the strongest possible signal.

Do not claim that task text alone guarantees a ChatGPT sidebar rename when the runtime exposes no rename action.

This title rule applies to newly created sub-worker sessions. Reusing/reviving an existing worker session does not require creating a new title merely because the worker receives another message.

---

## 8. REVIEWER ELIGIBILITY + WRITE ISOLATION

For each child and each mandatory review round:

```text
reviewer
≠ initial author of that child for the CURRENT STEP
```

That is the required independence boundary for this parent workflow.

Review #1 and Review #2:

- may use the same reviewer;
- do not need to rotate through different reviewers;
- may be performed by Prime or either sub-worker as long as the reviewer is not the child's initial author;
- must still satisfy source-state sequencing rules.

A reviewer may write fixes **after its review has ended** if Prime explicitly establishes/transfers write ownership first.

For example, all of these are valid:

```text
initial author = Worker 1
Review #1      = Worker 2
Fix #1         = Worker 2
Review #2      = Worker 2
```

or:

```text
initial author = Worker 1
Review #1      = Worker 2
Fix #1         = Prime
Review #2      = Worker 2
```

provided Review #2 occurs only after Review #1 findings are fully adjudicated/applied as required and the current child source has been revalidated.

Write isolation remains strict:

```text
one child branch/worktree
→ at most one active writer
→ every writer uses that exact child branch/worktree
→ ownership transfer must be clear before writing starts
```

Do not confuse reviewer eligibility with write ownership. Reviewer independence is measured against the **initial author**; write ownership may change later.

---

## 9. MANDATORY SEQUENTIAL REVIEW LIFECYCLE PER CHILD

For every non-Commit STEP that CURRENT governance requires to pass a learning/content review gate, each child module must satisfy the CURRENT review requirement independently.

Where the common two-review rule applies, use:

```text
ORIGINAL AUTHOR IMPLEMENTS CURRENT STEP
        ↓
required validation
        ↓
CHILD SOURCE STATE V1
        ↓
ELIGIBLE INDEPENDENT REVIEWER performs FRESH REVIEW #1
        ↓
review findings → Prime
        ↓
Prime adjudicates / routes findings
        ↓
CURRENT WRITE OWNER fixes all valid MUST FIX
+ fixes all valid SHOULD IMPROVE
        ↓
revalidate
        ↓
CHILD SOURCE STATE V2
        ↓
ELIGIBLE INDEPENDENT REVIEWER performs FRESH FULL REVIEW #2 OF CURRENT V2
        ↓
findings → Prime → assigned/current write owner
        ↓
CURRENT WRITE OWNER fixes valid findings
        ↓
revalidate
        ↓
CLEAN?
   ├─ YES → child complete for CURRENT STEP
   └─ NO  → Review #3 / #4 / ... using the same sequential rule
```

Review #2 must not be a diff-only verification of Review #1.

It must re-read CURRENT post-fix source and audit the full CURRENT artifact according to the CURRENT STEP rule.

Hard prohibition:

```text
SOURCE V1
├─→ Review #1
└─→ Review #2
```

Required:

```text
SOURCE V1
→ Review #1
→ fix
→ revalidate
→ SOURCE V2
→ Review #2
```

The implementation owner's own self-check never substitutes for a mandatory independent review.

Minimum review count is **two for every target child** even when Review #1 reports no findings.

Review #3 / #4 / later rounds are required only for affected children that still have unresolved findings, substantial post-review changes, or another reason under CURRENT rules to reopen review.

Do not rerun Review #3 across unaffected siblings merely to preserve symmetric counts.

The parent-wide scheduling/barrier behavior around these per-child chains is defined in Section 7.

---

## 10. WHEN ONLY ONE CHILD ASSIGNMENT REMAINS IN A PHASE

If only one child assignment remains in the CURRENT phase, that child still requires the same validation/review standard.

Use any otherwise free eligible lane when helpful, while preserving:

```text
reviewer ≠ initial author
correct child branch/worktree
single active writer
Review #1 → fix/revalidate → Review #2 source ordering
```

Do not downgrade review independence merely because only one child assignment remains.

---

## 11. CURRENT-STEP BARRIER

A child becoming CLEAN does **not** authorize advancing that child alone into the next STEP.

For example, this is forbidden:

```text
child 1 → STEP 2 CLEAN → start STEP 3
child 2 → still STEP 2
child 3 → still STEP 2
```

Required:

```text
child 1 STEP 2 CLEAN
child 2 STEP 2 CLEAN
child 3 STEP 2 CLEAN
...
ALL target children STEP 2 CLEAN
        ↓
parent advances to STEP 3
```

This barrier is the core rule of this Goal.

---

## 12. CROSS-MODULE / PARENT-LEVEL CHECKS

Per-child CLEAN is necessary but may not be sufficient when the CURRENT STEP or CURRENT governance defines parent-level consistency requirements.

After all target children are individually CLEAN for a STEP, perform any required parent-level checks such as:

```text
ownership boundaries
sibling curriculum overlap
dependency direction
terminology consistency
ordering constraints
shared route/module identity consistency
generated aggregate/projection integrity
```

Only perform checks that are required or materially relevant under CURRENT governance.

Do not invent a new giant parent-level authoring pass that rewrites already CLEAN child content merely for stylistic uniformity.

If a parent-level check exposes a real defect in one child, reopen that child's CURRENT STEP gate, fix it through its owner, and re-review/revalidate as required before advancing.

---

## 13. WORKTREE SAFETY

Separate child worktrees are a feature of this workflow, not an exception.

For STEP 3+ partial/resume runs, locating and using the already-existing correct child worktree is mandatory before any edit. `main` / repository root is not a fallback workspace.

Never:

- edit child A through child B's worktree;
- assume repository root is the active child worktree;
- reset unrelated user work;
- delete another session's uncommitted changes;
- switch a worktree to another branch just for convenience;
- merge/rebase/cherry-pick across child branches unless CURRENT governance/request authorizes it;
- treat generated changes outside the owned scope as disposable without proving ownership.

Before a write, the worker should know explicitly:

```text
CURRENT STEP
owned child module
owned branch
owned worktree root
allowed edit scope
```

While actively performing a review, a reviewer must treat the reviewed child's worktree as **READ-ONLY** so the review result is produced before any fix mutation. After that review is complete, the same person may become the child's writer/fixer only after write ownership is explicitly transferred/established and no other writer is active.

---

## 14. ROOT / SHARED SCANNER SIDE EFFECTS

Repository-wide scanners/generators may see worktree-management directories such as `.worktrees` or `.wt`.

When a root-level build/generator unexpectedly discovers worktree copies as modules, do not misclassify that as a child-content defect.

Instead:

```text
identify whether the failure is owned by repository/global scanner infrastructure
        ↓
route it to the correct upstream/global owner
        ↓
preserve child worktree changes
        ↓
re-run only the validation required after the infrastructure issue is resolved
```

Do not modify a child module merely to satisfy an accidental scan of the worktree directory itself.

---

## 15. UPSTREAM DEFECT ROUTING

If a child STEP discovers a real defect owned by an earlier STEP, shared generator, parent curriculum, or repository infrastructure, route it according to CURRENT governance.

Do not silently mutate another ownership area while pretending the current child STEP alone changed.

After an upstream correction, reopen and revalidate all materially affected child/current-STEP gates required by CURRENT rules.

---

## 16. COMMIT / FINAL-DELIVERY STEP

The Commit/final-delivery STEP is discovered dynamically from CURRENT `GENERAL_AGENT_RULES.md`.

It is not hard-coded to any number or filename here.

When the parent workflow reaches that STEP:

```text
re-read CURRENT GENERAL_AGENT_RULES.md
        ↓
resolve CURRENT canonical Commit/final-delivery rule
        ↓
read it from disk
        ↓
apply it independently to each target child's branch/worktree as required
        ↓
select one target child branch for final delivery
        ↓
complete that child's branch-specific commit → push → PR/MR → merge → verify lifecycle
        ↓
cleanup that child's worktree/branch only after its merge + remote verification succeeds
        ↓
then select the next target child branch
        ↓
continue until ALL target children have completed final delivery
```

Do not apply the generic two-learning-review requirement to the Commit STEP unless the CURRENT Commit rule explicitly requires it.

Each child branch is delivered independently according to the CURRENT Commit rule. Do not combine sibling changes into one synthetic parent branch merely to simplify final delivery unless CURRENT governance explicitly changes that model.

By default, final delivery is **serialized one child branch at a time**. Do not concurrently merge multiple child branches into `main`, because each child's CURRENT Commit lifecycle may verify/synchronize `main` and clean its own worktree/branch before the next branch is delivered. If a future CURRENT Commit rule explicitly defines a safe concurrent parent-delivery strategy, that newer canonical rule may override this serialization.

The Goal is not complete because one child was committed/pushed/merged successfully.

It completes only when every target child's required final-delivery lifecycle has reached the success state defined by CURRENT governance, or when a genuine external blocker is reported exactly as required by the Commit rule.

---

## 17. DEFINITION OF DONE

The parent-module Goal is complete only when:

```text
STEP 2
→ every target child freshly executed under CURRENT rules
→ every target child CLEAN
→ required parent/current-STEP checks pass

every later canonical STEP
→ dynamically resolved from CURRENT governance
→ every target child executed for that STEP
→ every target child CLEAN before parent advances
→ required review/validation gates pass

canonical Commit / final-delivery STEP
→ dynamically discovered
→ CURRENT rule read at actual transition time
→ completed for every target child branch/worktree
```

Most importantly:

```text
START = STEP 2

OR, WHEN EXPLICITLY REQUESTED:
→ RESUME/START AT A LATER CURRENT STEP
→ FIND THE EXISTING CORRECT CHILD BRANCH/WORKTREE FIRST
→ NEVER FALL BACK TO MAIN FOR STEP 3+

FOR EACH CURRENT STEP:
→ RE-READ CURRENT GOVERNANCE
→ APPLY THE CURRENT WORKER-CONCURRENCY RULE FROM module-generate-agent/rules/
→ IMPLEMENT ALL TARGET CHILDREN BEFORE OPENING REVIEW #1
→ REUSE FREE LANES ACROSS CHILDREN WITHIN THE CURRENT PHASE
→ REVIEW #1 MUST COVER ALL TARGET CHILDREN
→ EACH CHILD MAY APPLY REVIEW #1 FIXES + REVALIDATE IMMEDIATELY AFTER ITS REVIEW #1
→ WRITE OWNERSHIP MAY TRANSFER, BUT NEVER ALLOW CONCURRENT WRITERS
→ REVIEWER MUST DIFFER FROM THE CHILD'S INITIAL AUTHOR
→ AFTER ALL CHILDREN HAVE COMPLETED REVIEW #1, OPEN REVIEW #2 GLOBALLY
→ REVIEW #2 FOR A CHILD REQUIRES THAT CHILD'S REVIEW #1 FIXES + REVALIDATION TO BE COMPLETE
→ REVIEW #1 AND REVIEW #2 MAY USE THE SAME ELIGIBLE REVIEWER
→ REVIEW #3+ ONLY FOR AFFECTED CHILDREN WHEN REQUIRED
→ EVERY FRESH SUB-WORKER SESSION GETS AN EXACT `CHATGPT CONVERSATION TITLE` BLOCK USING `STEP <N> - ...`
→ REVIEW #1 → FIX → REVALIDATE → REVIEW #2 OF CURRENT SOURCE
→ DO NOT ADVANCE ANY CHILD ALONE TO NEXT STEP
→ ADVANCE ONLY WHEN ALL TARGET CHILDREN ARE CLEAN

STOP ONLY AFTER:
→ THE DYNAMICALLY DISCOVERED CANONICAL COMMIT / FINAL-DELIVERY STEP
→ COMPLETES FOR ALL TARGET CHILDREN
```

Proceed according to the parent module, child-module inventory, and worktree mapping supplied by the invoking request and CURRENT repository state.
