# GOAL — FULL MODULE WORKFLOW FROM STEP 2 TO CANONICAL COMMIT

This file is a **shared execution/orchestration Goal**.

It intentionally knows only one fixed numbered entry point:

```text
START = STEP 2
```

It does **not** hard-code:

- how many STEPs currently exist;
- which number currently belongs to Menu, Knowledge, API, Video, Quiz, Interview, Validation, Commit, or any future phase;
- the final STEP number;
- the filenames of future STEP rules;
- a target module name/path;
- a technology-specific Curriculum path.

The target module is supplied by the invoking request/session context.

The CURRENT repository governance determines the workflow after STEP 2.

### Mandatory rules-folder loading gate

Before this Goal performs any module work, STEP routing, validation, review, worker action, or long-running wait, it must enumerate and read **every current file** under [`../../rules/`](../../rules/) in full.

Do not read only a known rule such as `HEARTBEAT.md`. The complete current `rules/` folder is mandatory execution context.

If the rules folder changes materially during the Goal, re-enumerate and re-read the complete folder before the next canonical STEP begins.

---

## 1. PRIMARY OBJECTIVE

For the supplied target module:

```text
begin at canonical STEP 2
        ↓
execute the CURRENT canonical STEP chain in order
        ↓
continue autonomously through every required STEP
        ↓
stop only after the CURRENT canonical Commit / final-delivery STEP completes
```

Do NOT stop after an intermediate STEP.

Do NOT ask for another prompt merely to continue to the next canonical STEP.

Only stop early when current canonical governance requires stopping, or when a genuine blocker cannot be resolved safely, including an external CLI/auth/check/permission/conflict condition during final delivery.

---

## 2. WORKFLOW AUTHORITY

This Goal owns only **execution orchestration**.

Authority is split as follows:

```text
FULL_WORKFLOW.md
→ start at STEP 2
→ continue until canonical Commit/final-delivery STEP
→ no-skip policy
→ fresh-context transition policy
→ sequential independent-review policy
→ cross-step routing policy

rules/**
→ mandatory cross-cutting execution rules loaded in full before work begins
→ currently includes heartbeat/liveness behavior and worker-concurrency limits

GENERAL_AGENT_RULES.md
→ CURRENT canonical workflow graph/order
→ CURRENT STEP numbering
→ CURRENT STEP-to-rule-file routing
→ global module-generation governance

CURRENT STEP rule file
→ complete rules for the STEP being executed
```

Do not duplicate STEP-specific authoring/schema/generator/content rules in this Goal.

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

## 3. RESOLVE THE WORKFLOW DYNAMICALLY

### Initial entry

At Goal start:

```text
1. read CURRENT GENERAL_AGENT_RULES.md from disk;
2. enumerate CURRENT module-generate-agent/rules/;
3. read EVERY current file in rules/ in full;
4. inspect current repository/worktree state;
5. resolve what CURRENT canonical STEP 2 is and which rule file owns it;
6. read that CURRENT STEP 2 rule file from disk;
7. load the context required by those current rules;
8. execute STEP 2 while applying all loaded cross-cutting rules.
```

Only `STEP 2` is fixed by this Goal.

### Every later transition

After the current STEP becomes CLEAN:

```text
CURRENT STEP becomes CLEAN
        ↓
apply/finish all current STEP changes
        ↓
inspect CURRENT repository state
        ↓
RE-READ CURRENT GENERAL_AGENT_RULES.md FROM DISK
        ↓
resolve the NEXT canonical STEP and its CURRENT delegated rule file
        ↓
READ THAT STEP RULE FILE FROM DISK
        ↓
refresh all context required by that STEP
        ↓
execute that STEP
```

Never infer the next STEP from:

- memory;
- an earlier copy of `GENERAL_AGENT_RULES.md`;
- the STEP sequence that existed when this Goal started;
- filename sorting;
- numeric guessing;
- a previous worker's summary;
- this Goal's historical wording.

If workflow files change while the Goal is running, the newly read CURRENT files govern the next transition.

Reading future STEP files early does **not** satisfy the transition rule. The relevant files must be re-read when that STEP is actually about to begin.

---

## 4. DYNAMIC STOP CONDITION

This Goal does not know or care which STEP number is final.

After each STEP becomes CLEAN, re-read `GENERAL_AGENT_RULES.md` and resolve what comes next.

Continue while a next canonical STEP exists.

The normal successful stop condition is semantic:

```text
CURRENT canonical workflow identifies a STEP as the Commit / final-delivery STEP
        ↓
read that STEP's CURRENT canonical rule file
        ↓
execute it completely according to the CURRENT rule
        ↓
its required commit/push/PR-or-MR/merge/verification/cleanup lifecycle completes
        ↓
GOAL COMPLETE
```

Do not stop merely because a historically familiar final STEP number was reached.

If future governance inserts, removes, renumbers, or renames STEPs before Commit, follow the newly resolved chain automatically.

---

## 5. ABSOLUTE NO-SKIP RULE

Every canonical STEP from the fixed entry STEP 2 through the canonical Commit/final-delivery STEP must be freshly executed for this Goal unless CURRENT governance explicitly says otherwise.

Artifact existence does not prove current approval.

Do NOT skip a STEP merely because:

```text
its output already exists
a previous session edited it
a previous review said CLEAN
a generator already ran
the module currently builds
the artifact looks complete
```

Existing artifacts are current-state/migration evidence to be handled according to `GENERAL_AGENT_RULES.md` and the CURRENT STEP rule.

---

## 6. FRESH CONTEXT AT EVERY STEP

Immediately before executing any STEP, refresh the context required by the CURRENT rules.

As applicable this includes:

```text
CURRENT GENERAL_AGENT_RULES.md
CURRENT canonical STEP rule file
repository architecture/governance required by those rules
target-module current state
relevant Curriculum Map when applicable
approved/current upstream artifacts
current technology/version baseline
authoritative official documentation
actual build/runtime/source evidence
```

Never use this Goal as a substitute for those sources.

Never execute a STEP only from remembered context captured during an earlier STEP.

The complete `rules/` set remains active across STEP transitions. It is not a STEP artifact. If any file in that folder changes materially during a long-running Goal, re-enumerate and re-read the entire folder before the next STEP begins.

---

## 7. WORKSPACE / WORKTREE SAFETY

Before the first edit, inspect the repository/worktree/branch state required by CURRENT governance.

Follow the CURRENT canonical isolation/worktree/branch rules resolved when entering STEP 2 and any later updates to those rules.

Never:

- arbitrarily reuse a similarly named worktree;
- reset unrelated user work;
- delete another session's uncommitted changes;
- overwrite concurrent work;
- restore/revert files merely because they look generated without proving those changes belong to this execution.

If a generator creates repository-wide side effects, identify ownership precisely and revert only changes proven to belong to the current execution when such reversion is appropriate.

---

## 8. MANDATORY SEQUENTIAL REVIEW LIFECYCLE

For every non-Commit STEP that CURRENT governance requires to pass the learning/content review gate, normally require at least **two fresh independent full reviews** unless the CURRENT canonical STEP rule explicitly sets a different minimum. In particular, `STEP_5_API.md` allows **one independent full review** when an initial `NOT REQUIRED` API decision is independently confirmed; if that decision is overturned and API is built, **two NEW post-build full reviews** are mandatory (the previous no-API review does not count).

Reviews are **sequential source-state reviews**, never parallel reviews of one snapshot.

Required orchestration:

```text
IMPLEMENT / DECIDE CURRENT STEP
        ↓
run validation required by CURRENT STEP rules
        ↓
SOURCE STATE V1
        ↓
FRESH INDEPENDENT REVIEW #1
        ↓
adjudicate Review #1
        ↓
fix all valid MUST FIX
+ fix all valid SHOULD IMPROVE
        ↓
revalidate
        ↓
SOURCE STATE V2
        ↓
FRESH INDEPENDENT REVIEW #2 OF V2
        ↓
adjudicate Review #2
        ↓
fix all valid MUST FIX
+ fix all valid SHOULD IMPROVE
        ↓
revalidate
        ↓
CLEAN?
   ├─ YES → STEP complete
   └─ NO  → Review #3 / #4 / ... with the same sequential rule
```

Hard prohibition:

```text
DO NOT:

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

Review #2 cannot start until Review #1 is complete, its findings are adjudicated, valid fixes are applied, and the resulting source is revalidated.

The same sequencing applies to every later review.

This remains mandatory even when many workers are available.

---

## 9. INDEPENDENT REVIEW CONTRACT

Each mandatory review is a fresh full audit of the CURRENT artifact state using the CURRENT canonical rule for that STEP.

Build/generator/schema/parity/runtime success does not count as an independent review.

The implementation agent's own self-check does not count as one of the required independent reviews.

For Review #2 and later:

```text
previous review result
        +
CURRENT post-fix source/artifacts
        +
CURRENT canonical STEP rule file
        +
all other context required by that STEP
```

are inputs.

The previous review result is context, never a replacement for inspecting the current source.

The later reviewer must independently audit the whole current artifact and may confirm, reject, refine, or extend earlier findings, including incomplete fixes, regressions, severity mistakes, false assumptions, and newly visible issues.

Use the finding classification and CLEAN criteria defined by CURRENT rules. Where the common classification applies:

```text
MUST FIX
SHOULD IMPROVE
OPTIONAL
```

If a review causes a material fix, revalidate before any later review or CLEAN decision.

---

## 10. STEP COMPLETION AND NEXT-STEP RESOLUTION

A STEP is complete only when it satisfies:

```text
CURRENT canonical STEP completion gate
+
the review lifecycle applicable to that STEP
+
all required post-fix validation
+
CLEAN current source state
```

Once CLEAN:

```text
do not guess STEP N+1
        ↓
re-read CURRENT GENERAL_AGENT_RULES.md
        ↓
resolve next canonical STEP dynamically
        ↓
read its CURRENT canonical file
        ↓
refresh context
        ↓
continue
```

---

## 11. UPSTREAM DEFECT ROUTING

If a downstream STEP discovers a real defect owned upstream, route it according to CURRENT `GENERAL_AGENT_RULES.md` and the CURRENT STEP rules.

Do not silently mutate another STEP while pretending only the current STEP changed.

After an upstream correction:

```text
re-open the owning STEP completion/review gate
        ↓
fix + sequentially review as required
        ↓
revalidate materially affected downstream artifacts
        ↓
re-run affected downstream review gates when required
```

Area-level ownership/inventory/boundary/dependency defects must be routed as Curriculum problems according to CURRENT governance.

---

## 12. COMMIT / FINAL-DELIVERY STEP

The Commit/final-delivery STEP is discovered dynamically from CURRENT `GENERAL_AGENT_RULES.md`.

It is not hard-coded to any number or filename here.

When that STEP becomes current:

```text
re-read CURRENT GENERAL_AGENT_RULES.md
        ↓
resolve the CURRENT canonical Commit/final-delivery STEP rule file
        ↓
read that file from disk
        ↓
execute it exactly
```

Do not apply the generic two-learning-review requirement to the Commit STEP unless its CURRENT canonical rule explicitly requires it.

This Goal authorizes the canonical final-delivery lifecycle when CURRENT rules permit it.

If an external condition blocks final delivery, stop safely and preserve/report state exactly as the CURRENT Commit rule requires.

---

## 13. DEFINITION OF DONE

The Goal is complete only when:

```text
canonical STEP 2
→ freshly executed under CURRENT rules
→ CLEAN

every canonical STEP discovered after STEP 2
→ executed in CURRENT canonical order
→ no required STEP skipped
→ CURRENT completion/review gates satisfied
→ CLEAN before transition

canonical Commit / final-delivery STEP
→ dynamically discovered from CURRENT governance
→ CURRENT canonical rule file read at actual transition time
→ lifecycle completed successfully
```

No previous CLEAN result substitutes for fresh execution required by this Goal.

No validation result substitutes for mandatory independent reviews.

No two mandatory reviews for one STEP may review the same pre-fix source snapshot in parallel.

Most importantly:

```text
START = STEP 2

AFTER EVERY CLEAN STEP:
→ RE-READ CURRENT GENERAL_AGENT_RULES.md
→ RESOLVE THE NEXT CANONICAL STEP
→ READ THAT STEP'S CURRENT RULE FILE FROM DISK
→ REFRESH CONTEXT
→ EXECUTE

STOP ONLY AFTER:
→ THE DYNAMICALLY DISCOVERED CANONICAL COMMIT / FINAL-DELIVERY STEP COMPLETES
```

Proceed autonomously for the target module supplied by the invoking request.
