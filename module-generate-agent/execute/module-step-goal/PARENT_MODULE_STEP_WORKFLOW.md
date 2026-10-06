# GOAL — PARENT MODULE HORIZONTAL STEP WORKFLOW

This file is a **shared execution/orchestration Goal** for processing a parent module that contains multiple target child modules.

It intentionally fixes only one numbered entry point:

```text
START = STEP 2
```

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

The CURRENT repository governance determines the workflow after STEP 2.

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

This Goal owns only **parent-level orchestration, worker scheduling, worktree ownership, review routing, STEP barriers, and compaction checkpoints**.

Authority is split as follows:

```text
PARENT_MODULE_STEP_WORKFLOW.md
→ process sibling child modules horizontally by STEP
→ max-two-sub-worker scheduling with Prime optionally acting as a third execution lane
→ child-module ownership until CLEAN
→ cross-review routing
→ worktree isolation rules
→ STEP barrier across all children
→ safe compaction checkpoints

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

Before STEP 2 authoring begins, the Prime must establish the exact execution inventory.

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

as durable orchestration state and preserve it across worker handoffs and compaction.

If the mapping is ambiguous, resolve it from CURRENT repository/worktree state before any edit.

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

At Goal start:

```text
1. inspect current repository/worktree state;
2. resolve the parent and target child-module inventory;
3. read CURRENT GENERAL_AGENT_RULES.md from disk;
4. resolve what CURRENT canonical STEP 2 is and which rule file owns it;
5. read that CURRENT STEP 2 rule file from disk;
6. verify shared governance compatibility across participating worktrees;
7. execute STEP 2 horizontally across every target child module.
```

Only `STEP 2` is fixed by this Goal.

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

Preferred worker responsibilities:

```text
Worker 1
→ owns one child module for the CURRENT STEP
→ edits only that owned child's worktree
→ may perform read-only independent review of Worker 2's child

Worker 2
→ owns one child module for the CURRENT STEP
→ edits only that owned child's worktree
→ may perform read-only independent review of Worker 1's child

Prime, when used as an execution lane
→ owns one child module for the CURRENT STEP
→ edits only that owned child's worktree
→ may perform read-only independent review of a sub-worker-owned child
→ must still maintain parent-level scheduling/checkpoint state
```

A worker keeps ownership of its child module until that child is CLEAN for the CURRENT STEP.

Ownership means:

```text
authoring
validation
receiving review findings
applying valid fixes
revalidation
final CLEAN handoff
```

Do not move a worker permanently to another child while its currently owned child still has unresolved MUST FIX / SHOULD IMPROVE findings or required validation.

---

## 7. CHILD SCHEDULING

At most three child modules may be actively owned at once:

```text
Prime + Worker 1 + Worker 2
```

Prime participation is optional, not mandatory. Use only two child lanes when Prime needs to concentrate on orchestration, integration, recovery, or review routing.

Example:

```text
Worker 1 → child 1
Worker 2 → child 2
Prime    → child 3

child 1 CLEAN → Worker 1 may take child 4 when safe
child 2 CLEAN → Worker 2 may take child 5 when safe
child 3 CLEAN → Prime may take child 6 when safe
```

The scheduler does not require child completion times to be identical.

However, the Prime must not optimize utilization at the cost of review correctness.

If one execution lane is required to perform a pending independent review for another lane, Prime may delay assigning new authoring work until that review is complete.

Prefer clear ownership and correct review sequencing over keeping all three lanes busy every moment.

---

## 8. WRITE ISOLATION AND CROSS-REVIEW

For two concurrently owned children, the two active lanes may cross-review each other.

For three concurrently owned children, prefer a cyclic independent-review assignment so nobody reviews the child they authored:

```text
AUTHORSHIP
Worker 1 → child 1
Worker 2 → child 2
Prime    → child 3

REVIEW CYCLE
Prime    → reviews child 1 READ-ONLY
Worker 1 → reviews child 2 READ-ONLY
Worker 2 → reviews child 3 READ-ONLY
```

Another cyclic direction is acceptable when scheduling requires it, provided reviewer independence is preserved.

An independent reviewer must not edit the reviewed child's worktree while acting in review role.

Review findings are reported to Prime.

Prime routes accepted findings back to the child owner.

The owner performs the fix in its own worktree.

This prevents two workers from concurrently writing the same child branch/worktree and preserves clear change ownership.

---

## 9. MANDATORY SEQUENTIAL REVIEW LIFECYCLE PER CHILD

For every non-Commit STEP that CURRENT governance requires to pass a learning/content review gate, each child module must satisfy the CURRENT review requirement independently.

Where the common two-review rule applies, use:

```text
OWNER IMPLEMENTS CURRENT STEP
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
OWNER fixes all valid MUST FIX
+ fixes all valid SHOULD IMPROVE
        ↓
OWNER revalidates
        ↓
CHILD SOURCE STATE V2
        ↓
ANOTHER INDEPENDENT LANE performs FRESH FULL REVIEW #2 OF CURRENT V2
        ↓
findings → Prime → owner
        ↓
OWNER fixes valid findings
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

---

## 10. WHEN ONLY ONE CHILD REMAINS

If the CURRENT STEP has an odd number of target children, the final child still requires the same review standard.

Use the other worker as read-only reviewer even if that worker no longer owns an active authoring child.

Example:

```text
Worker 1 → owns final child 7
Worker 2 → independent reviewer for child 7
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

## 13. COMPACTION CHECKPOINT AFTER EACH TWO CLEAN CHILDREN

Use semantic checkpoints rather than relying only on automatic context-pressure compaction.

After each additional **two child modules become CLEAN for the CURRENT STEP**, prepare a compaction checkpoint when it can be done safely.

Safe checkpoint conditions:

```text
the two counted child modules are CLEAN
no accepted fix for those children is pending
no review of those children is still running/pending
their current worktree state is on disk
Prime has recorded current orchestration state
```

The checkpoint must record at least:

```text
CURRENT STEP
parent module
completed/CLEAN children for this STEP
remaining children for this STEP
child → branch → worktree mapping
current worker ownership
any review/fix still pending on other active children
important cross-module decisions needed for continuation
the rule that filesystem/CURRENT source is the source of truth
```

Then report clearly:

```text
COMPACTION CHECKPOINT READY
```

and wait for the user/app to perform `Compact & resume now` when manual compaction is required.

Do not claim that typing the phrase itself programmatically compacts the session.

If reaching exactly two CLEAN children would require interrupting another child in the middle of an unsafe author/review/fix transition, delay the checkpoint until the next safe boundary.

After resume:

```text
1. inspect CURRENT worktree/filesystem state;
2. restore child/worktree ownership mapping from the checkpoint;
3. re-read CURRENT GENERAL_AGENT_RULES.md;
4. re-read the CURRENT STEP rule file;
5. re-read CURRENT source needed for pending work;
6. continue from actual filesystem state, not conversational memory.
```

Auto-compaction may still occur earlier as a safety mechanism. If it does, recover using the same source-of-truth procedure.

---

## 14. WORKTREE SAFETY

Separate child worktrees are a feature of this workflow, not an exception.

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

## 15. ROOT / SHARED SCANNER SIDE EFFECTS

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

## 16. UPSTREAM DEFECT ROUTING

If a child STEP discovers a real defect owned by an earlier STEP, shared generator, parent curriculum, or repository infrastructure, route it according to CURRENT governance.

Do not silently mutate another ownership area while pretending the current child STEP alone changed.

After an upstream correction, reopen and revalidate all materially affected child/current-STEP gates required by CURRENT rules.

---

## 17. COMMIT / FINAL-DELIVERY STEP

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

## 18. DEFINITION OF DONE

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

FOR EACH CURRENT STEP:
→ RE-READ CURRENT GOVERNANCE
→ PROCESS CHILDREN WITH MAX 2 ACTIVE SUB-WORKERS
→ PRIME MAY ALSO OWN ONE CHILD, ALLOWING UP TO 3 CONCURRENT CHILD LANES
→ ONE OWNER PER CHILD UNTIL CLEAN
→ ANOTHER INDEPENDENT LANE REVIEWS READ-ONLY
→ REVIEW #1 → FIX → REVALIDATE → REVIEW #2 OF CURRENT SOURCE
→ CHECKPOINT AFTER EACH TWO CLEAN CHILDREN WHEN SAFE
→ DO NOT ADVANCE ANY CHILD ALONE TO NEXT STEP
→ ADVANCE ONLY WHEN ALL TARGET CHILDREN ARE CLEAN

STOP ONLY AFTER:
→ THE DYNAMICALLY DISCOVERED CANONICAL COMMIT / FINAL-DELIVERY STEP
→ COMPLETES FOR ALL TARGET CHILDREN
```

Proceed according to the parent module, child-module inventory, and worktree mapping supplied by the invoking request and CURRENT repository state.
