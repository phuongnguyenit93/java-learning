# WORKER CONCURRENCY RULE

## 1. Purpose

This file owns the cross-cutting Chat On Steroids **worker-pool limit and worker-reuse rules** used by module-generation workflows.

It is intentionally separated from parent STEP barriers, review sequencing, write ownership, and STEP-specific content rules.

Those orchestration rules remain in their canonical workflow files.

---

## 2. Mandatory worker-pool cap

```text
MAX WORKERS IN POOL = 2

COUNTED STATES
→ ACTIVE
→ SLEEPING
```

Prime remains the orchestrator.

The cap is based on the number of reusable worker sessions that currently occupy the pool, not only on workers that are actively computing.

Therefore:

```text
2 ACTIVE
→ pool full

1 ACTIVE + 1 SLEEPING
→ pool full

2 SLEEPING
→ pool full

1 SLEEPING
→ one numerical slot may appear unused
→ BUT spawning is still forbidden while that sleeping worker exists
```

`FINISHED` / `FAILED` workers that are no longer reusable worker sessions do not count toward this ACTIVE + SLEEPING pool. When runtime state is ambiguous, verify actual worker state before deciding that a slot is free.

Prime may also act as one independent execution lane when safe and when doing so does not compromise scheduling, write isolation, review independence, or orchestration visibility.

Therefore, when Prime also owns one eligible assignment:

```text
MAX CONCURRENT EXECUTION OWNERS = 3
→ Prime + up to 2 workers from the worker pool
```

This does not mean both workers must be ACTIVE. A sleeping worker still occupies one of the two worker-pool slots.

---

## 3. Sleeping-worker reuse is mandatory before spawn

Hard rule:

```text
IF ANY SLEEPING WORKER EXISTS
→ DO NOT CREATE / SPAWN A NEW WORKER
```

This is mandatory, not a preference.

Required spawn decision:

```text
need another worker lane?
        ↓
check worker pool state first
        ↓
any SLEEPING worker exists?
   ├─ YES → spawning is forbidden
   │        → reuse/revive a sleeping worker for a real eligible assignment when possible
   │        → otherwise use Prime / another eligible existing lane / wait or reroute work
   │
   └─ NO
        ↓
count ACTIVE + SLEEPING workers
        ↓
count < 2 ?
   ├─ YES → a new worker may be spawned if there is a real eligible assignment
   └─ NO  → spawning is forbidden
```

Do not create a third worker session because an existing worker is sleeping.

Do not create a replacement worker merely because a sleeping worker is inconvenient for the next assignment.

Do not bypass reviewer eligibility, write ownership, phase barriers, or STEP barriers merely to make use of a sleeping worker. If the sleeping worker is not eligible for the currently open assignment, keep the worker pool unchanged and schedule only work that remains valid under the active orchestration rules.

Prime must inspect current worker states before every spawn decision. Never infer that a worker slot is free solely because no worker is currently producing output.

---

## 4. Execution lanes are reusable

Workers are execution lanes, not permanent child-module owners.

Use:

```text
eligible execution lane
→ take one eligible assignment
→ finish that assignment safely
→ normally become SLEEPING / reusable when the runtime retains the worker session
→ take another eligible assignment in the SAME current phase when useful
```

Do not reserve one worker permanently for one child unless there is a concrete operational reason to do so.

When a retained worker becomes SLEEPING, it remains part of the two-worker pool and should be revived/reused for subsequent eligible work instead of creating a fresh worker session.

The active orchestration workflow still decides:

```text
which phase is open
which child assignment is eligible
whether Prime may safely own an assignment
reviewer eligibility
write ownership
STEP barriers
```

This file owns worker-pool size, mandatory sleeping-worker reuse-before-spawn behavior, and reusable worker-lane behavior.

---

## 5. This rule does not weaken barriers

The worker concurrency cap does **not** alter any parent/global barrier.

In particular, it does not authorize:

```text
starting Review #1 before the active parent workflow's implement barrier opens
starting Review #2 before the active parent workflow's Review #2 eligibility barrier opens
advancing one child alone to the next STEP
bypassing required fixes/revalidation/reviews
allowing multiple writers on one child worktree
```

Barrier ownership remains in the active orchestration file, especially `execute/module-step-goal/PARENT_MODULE_STEP_WORKFLOW.md` for parent-horizontal execution.

---

## 6. Integration contract

This rule must be loaded together with every other current file under `module-generate-agent/rules/` before execution begins.

Relevant orchestration owners:

```text
GENERAL_AGENT_RULES.md
→ ../GENERAL_AGENT_RULES.md

FULL_WORKFLOW.md
→ ../execute/execute-goal/FULL_WORKFLOW.md

PARENT_MODULE_STEP_WORKFLOW.md
→ ../execute/module-step-goal/PARENT_MODULE_STEP_WORKFLOW.md
```

If this file conflicts with a higher canonical repository/governance authority, the higher authority wins.
