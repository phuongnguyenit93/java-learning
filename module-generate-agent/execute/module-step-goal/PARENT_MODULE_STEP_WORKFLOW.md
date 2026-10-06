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
→ max-two-sub-worker scheduling with Prime optionally acting as a third execution lane
→ wave-oriented three-lane scheduling
→ original-author write ownership for fixes
→ cyclic independent-review routing
→ worktree isolation rules
→ STEP barrier across all children

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
required Curriculum/governance inputs named by those rules
```

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
1. inspect current repository/worktree state;
2. resolve the parent and target child-module inventory;
3. read CURRENT GENERAL_AGENT_RULES.md from disk;
4. resolve what CURRENT canonical STEP 2 is and which rule file owns it;
5. read that CURRENT STEP 2 rule file from disk;
6. verify shared governance compatibility across participating worktrees;
7. execute STEP 2 horizontally across every target child module.
```

For an explicit STEP 3+ partial/resume run:

```text
1. inspect CURRENT repository/worktree state;
2. resolve the parent and target child-module inventory;
3. locate the EXISTING correct branch/worktree for every target child;
4. verify prior STEP state is present there;
5. read CURRENT GENERAL_AGENT_RULES.md from disk;
6. resolve the requested canonical STEP and its CURRENT rule file;
7. read that rule file from disk;
8. verify shared governance compatibility across participating worktrees;
9. execute the requested STEP horizontally across every target child module.
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
refresh required context
        ↓
verify governance compatibility across target worktrees
        ↓
execute NEXT STEP horizontally across all target children
```

Never infer the next STEP from memory, old summaries, numeric guessing, or historical workflow order.

---

## 6. MAXIMUM TWO ACTIVE SUB-WORKERS + PRIME AS AN OPTIONAL THIRD LANE

This Goal is designed for:

```text
MAX ACTIVE SUB-WORKERS = 2

PRIME
→ remains the orchestrator
→ may also act as one independent execution worker when safe

MAX CONCURRENT CHILD-MODULE OWNERS = 3
→ Prime + Worker 1 + Worker 2
```

Do not spawn additional sub-workers merely to increase throughput.

The Prime remains the scheduler and source of orchestration truth even while it owns one child module directly.

Prime is allowed to perform the same authoring/fix/validation role as a sub-worker for one child module. Therefore the scheduler may process up to three child modules concurrently when the worktrees are isolated and review sequencing remains safe.

Preferred execution-lane responsibilities:

```text
Worker 1
→ authors one child module in the current wave
→ writes only that authored child's worktree
→ later reviews another wave child READ-ONLY

Worker 2
→ authors one child module in the current wave
→ writes only that authored child's worktree
→ later reviews another wave child READ-ONLY

Prime, when used as an execution lane
→ authors one child module in the current wave
→ writes only that authored child's worktree
→ later reviews another wave child READ-ONLY
→ must still maintain parent-level scheduling state
```

The **original author remains the write owner** of that child for the current STEP until CLEAN.

Write ownership means:

```text
authoring
validation
receiving review findings
applying valid fixes
revalidation
final CLEAN handoff
```

Review roles may rotate between lanes, but reviewers remain read-only on children they did not author.

Do not permanently assign an author to a later wave while its authored child still has unresolved valid findings or required revalidation unless Prime can preserve that author's fix ownership without unsafe context/worktree switching.

---

## 7. WAVE-ORIENTED CHILD SCHEDULING

At most three child modules are grouped into one active **wave**:

```text
Prime + Worker 1 + Worker 2
```

Prime participation is optional, not mandatory. Use a two-child wave when Prime needs to concentrate on orchestration, integration, recovery, or review routing.

Preferred three-child wave:

```text
AUTHOR PHASE
Prime    → child 1
Worker 1 → child 2
Worker 2 → child 3

REVIEW #1 PHASE
rotate lanes across the three current children

FIX / REVALIDATE PHASE
original authors fix their own children

REVIEW #2 PHASE
rotate again, preferably to a reviewer different from Review #1

FINAL FIX / REVALIDATE
original authors fix their own children

WAVE CLEAN
→ only then schedule the next wave
```

This is **phase-centric / wave-centric scheduling**, not worker-centric scheduling.

The scheduler does not require authoring completion times to be identical, but it should normally keep the current wave together through its review/fix lifecycle rather than immediately sending the first free lane into a later wave.

### Pipeline the next phase as soon as it is eligible

Do not insert an artificial idle barrier between phases inside the same wave.

When the prerequisites for the **next wave phase** are satisfied, any free execution lane should immediately take eligible work from that next phase.

Example:

```text
AUTHOR PHASE
Prime    → child 1 → done
Worker 1 → child 2 → done
Worker 2 → child 3 → done

All wave authoring is now complete.

If Worker 1 becomes available first:
→ Worker 1 immediately starts an eligible Review #1 assignment
→ do NOT wait for Prime and Worker 2 to become idle merely to announce a formal phase switch
```

The same pipeline rule applies later:

```text
Review #1 prerequisites satisfied
→ free original author may begin an accepted fix/revalidation assignment

Review #1 fixes + required revalidation complete for a child
→ a free independent lane may begin that child's Review #2

Review #2 findings accepted
→ the original author may fix/revalidate immediately
```

This is a **work-conserving pipeline within the current wave**. It exists to minimize unnecessary idle time while preserving all source-state dependencies.

Do not confuse this with advancing to the next canonical STEP or the next child wave. The current canonical STEP barrier and current-wave ownership still apply.

Never pipeline work whose prerequisite source state is not ready. In particular:

```text
NO Review #1 before the reviewed child's authoring/required pre-review validation is complete
NO Review #2 before that child's Review #1 is adjudicated, accepted fixes are applied, and required revalidation completes
NO next canonical STEP before ALL target children are CLEAN for the current STEP
```

However, the Prime must not optimize utilization at the cost of review correctness.

If one execution lane finishes authoring early, it may perform useful validation/orchestration/review work at a safe boundary, but Prime must not force context switching merely to avoid brief idle time.

Prefer clear ownership and correct review sequencing over keeping all three lanes busy every moment, while using phase rotation to keep utilization high naturally.

---

## 7A. FRESH SUB-WORKER SESSION TITLE CONTRACT

Whenever Prime opens/spawns a **new sub-worker session**, the task instruction must explicitly tell that worker that the session title should begin with the CURRENT canonical STEP prefix.

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

When sending the initial task to a fresh sub-worker session, include an explicit instruction such as:

```text
Session title requirement: prefix this new session title with `STEP <N> - `.
```

If the product/session runtime auto-generates titles and does not expose direct title control, still include this instruction in the first worker task so the intended title convention is unambiguous. Also use the same STEP-prefixed wording in the worker label/task headline when practical.

This title rule applies to newly created sub-worker sessions. Reusing/reviving an existing worker session does not require creating a new title merely because the worker receives another message.

---

## 8. WRITE ISOLATION AND CYCLIC CROSS-REVIEW

For two-child waves, use the available third lane as an independent reviewer when possible; otherwise the two active lanes may cross-review while preserving write ownership.

For three concurrently owned children, prefer a cyclic independent-review assignment so nobody reviews the child they authored:

```text
AUTHORSHIP
Prime    → child 1
Worker 1 → child 2
Worker 2 → child 3

REVIEW #1 CYCLE
Worker 2 → reviews child 1 READ-ONLY
Prime    → reviews child 2 READ-ONLY
Worker 1 → reviews child 3 READ-ONLY

REVIEW #2 CYCLE — after Review #1 fixes/revalidation
Worker 1 → reviews child 1 READ-ONLY
Worker 2 → reviews child 2 READ-ONLY
Prime    → reviews child 3 READ-ONLY
```

This preferred rotation gives each child:

```text
1 original author
+ Review #1 by a second lane
+ Review #2 by the third lane
```

Another cyclic direction is acceptable when scheduling requires it, provided reviewer independence is preserved and Review #2 still audits the CURRENT post-fix source.

An independent reviewer must not edit the reviewed child's worktree while acting in review role.

Review findings are reported to Prime.

Prime routes accepted findings back to the original author/write owner.

The original author/write owner performs the fix in its own worktree.

This prevents two workers from concurrently writing the same child branch/worktree and preserves clear change ownership.

---

## 9. MANDATORY SEQUENTIAL REVIEW LIFECYCLE PER CHILD AND WAVE

For every non-Commit STEP that CURRENT governance requires to pass a learning/content review gate, each child module must satisfy the CURRENT review requirement independently.

Where the common two-review rule applies, use:

```text
ORIGINAL AUTHOR IMPLEMENTS CURRENT STEP
        ↓
owner runs required validation
        ↓
CHILD SOURCE STATE V1
        ↓
ANOTHER INDEPENDENT LANE performs FRESH READ-ONLY REVIEW #1
        ↓
review findings → Prime
        ↓
Prime adjudicates / routes findings
        ↓
ORIGINAL AUTHOR fixes all valid MUST FIX
+ fixes all valid SHOULD IMPROVE
        ↓
ORIGINAL AUTHOR revalidates
        ↓
CHILD SOURCE STATE V2
        ↓
ANOTHER INDEPENDENT LANE performs FRESH FULL REVIEW #2 OF CURRENT V2
        ↓
findings → Prime → original author
        ↓
ORIGINAL AUTHOR fixes valid findings
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

At wave level, preserve the same **logical dependency order**. Physical execution may pipeline into the next eligible phase as soon as prerequisites are satisfied; it does not need a full-lane idle barrier between phases:

```text
AUTHOR all children in the wave
        ↓
REVIEW #1 all children in the wave
        ↓
ORIGINAL AUTHORS apply accepted fixes
        ↓
REVALIDATE all materially changed children
        ↓
REVIEW #2 all children against CURRENT post-fix source
        ↓
ORIGINAL AUTHORS apply accepted fixes
        ↓
REVALIDATE
        ↓
WAVE CLEAN
```

For example, once all authoring in the wave is complete, the first free eligible lane should begin Review #1 immediately. Likewise, once one child's Review #1 fixes and revalidation are complete, an eligible independent lane may begin that child's Review #2 without waiting for unrelated children to finish their own fix work.

Do not start Review #2 for one child from its old pre-fix snapshot while another process is still applying that child's Review #1 fixes.

---

## 10. WHEN ONLY ONE CHILD REMAINS

If the CURRENT STEP ends with a partial wave, every remaining child still requires the same review standard.

Use the otherwise free lanes as read-only reviewers even if they are not authoring a child in that final wave.

Example:

```text
Prime    → authors final child 7
Worker 1 → Review #1 for child 7
Worker 2 → Review #2 for child 7 after fixes/revalidation
```

Do not downgrade review independence merely because only one target child remains.

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

Reviewers must know explicitly that their access to the other child's worktree is READ-ONLY.

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
respect MAX 2 active sub-workers; Prime may also own one child when safe
        ↓
complete final delivery for ALL target children
```

Do not apply the generic two-learning-review requirement to the Commit STEP unless the CURRENT Commit rule explicitly requires it.

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
→ PROCESS CHILDREN WITH MAX 2 ACTIVE SUB-WORKERS
→ PRIME MAY ALSO OWN ONE CHILD, ALLOWING UP TO 3 CONCURRENT CHILD LANES
→ GROUP UP TO 3 CHILDREN INTO A WAVE
→ AUTHOR IN PARALLEL
→ WHEN A NEXT WAVE PHASE BECOMES ELIGIBLE, ASSIGN FREE LANES IMMEDIATELY; DO NOT ADD ARTIFICIAL IDLE BARRIERS
→ ROTATE REVIEW #1 READ-ONLY
→ ORIGINAL AUTHORS FIX + REVALIDATE
→ ROTATE REVIEW #2 ON CURRENT POST-FIX SOURCE, PREFERABLY WITH THE THIRD LANE
→ ORIGINAL AUTHORS REMAIN WRITE OWNERS UNTIL CLEAN
→ EVERY FRESH SUB-WORKER SESSION MUST BE INSTRUCTED TO USE TITLE PREFIX `STEP <N> - `
→ REVIEW #1 → FIX → REVALIDATE → REVIEW #2 OF CURRENT SOURCE
→ DO NOT ADVANCE ANY CHILD ALONE TO NEXT STEP
→ ADVANCE ONLY WHEN ALL TARGET CHILDREN ARE CLEAN

STOP ONLY AFTER:
→ THE DYNAMICALLY DISCOVERED CANONICAL COMMIT / FINAL-DELIVERY STEP
→ COMPLETES FOR ALL TARGET CHILDREN
```

Proceed according to the parent module, child-module inventory, and worktree mapping supplied by the invoking request and CURRENT repository state.
