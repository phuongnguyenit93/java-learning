# EXECUTION HEARTBEAT

## 1. Purpose

This file defines the cross-cutting **execution heartbeat contract** for long-running module-generation workflows.

It exists to prevent a healthy Prime / worker / tool workflow from becoming externally indistinguishable from a stalled response merely because there has been no visible activity for a long period.

The heartbeat is an **orchestration concern**, not a learning-content STEP and not a substitute for real progress.

Canonical navigation:

```text
GENERAL_AGENT_RULES.md
→ ../GENERAL_AGENT_RULES.md

FULL_WORKFLOW.md
→ ../execute/execute-goal/FULL_WORKFLOW.md

PARENT_MODULE_STEP_WORKFLOW.md
→ ../execute/module-step-goal/PARENT_MODULE_STEP_WORKFLOW.md

HEARTBEAT.md
→ this cross-cutting liveness / waiting / recovery contract
```

This file is one member of the mandatory `module-generate-agent/rules/` rule set. `GENERAL_AGENT_RULES.md`, `FULL_WORKFLOW.md`, and `PARENT_MODULE_STEP_WORKFLOW.md` must read **every current file in that rules folder** before execution begins, including this file.

---

## 2. Core rule

During a knowingly long-running execution, Prime must not intentionally allow an orchestration-controlled quiet period to exceed the heartbeat budget without either real visible progress or a liveness heartbeat.

Heartbeat cadence is **adaptive and chosen by Prime/GPT at runtime**.

```text
NO fixed heartbeat interval

Prime chooses the next heartbeat/wake-up interval from CURRENT execution state
→ expected duration of the work being awaited
→ whether genuine tool/worker activity is already visible
→ connection/runtime stability
→ cost of checking too frequently
→ any known host-side long-silence / stalled-response threshold
```

Prime must choose a **bounded, reasonable interval** rather than an arbitrary fixed cadence. When a host-side long-silence/stall threshold is known, the planned quiet window should stay comfortably below it. When that threshold is unknown, use a conservative interval appropriate to the current operation and adjust future intervals from observed behavior.

The cadence may therefore change during one execution. For example, Prime may check sooner around a short worker handoff and wait longer for a known long-running build. The goal is not periodic noise; the goal is to avoid an unnecessarily long silent window while minimizing useless polling.

This rule applies particularly while Prime is:

```text
waiting for sub-workers
waiting for a long-running terminal/tool process
waiting between review/implementation phases
waiting for an external operation whose state can be checked safely
using a scheduler/no-op wait solely to remain available for worker results
recovering from an interrupted stream while backend work may still be alive
```

If real visible progress is already occurring before the currently selected heartbeat deadline, no synthetic heartbeat is required. Reset/reconsider the next heartbeat interval from the new CURRENT state instead of blindly repeating the previous delay.

---

## 3. Heartbeat must be independent of a new model request

A heartbeat must **not** be implemented by sending another conversational request such as:

```text
continue
still there?
keep going
status?
```

to the same still-running model turn or to a worker merely to create activity.

That pattern is forbidden because the previous turn may still be alive and a second model request can create overlapping work, duplicated edits, ordering ambiguity, additional worker pressure, or rate-limit failures.

Preferred heartbeat sources are low-cost orchestration/runtime actions such as:

```text
local scheduler wake-up
non-mutating process/status check
non-mutating worker/supervisor status read
bounded terminal wait that returns a short marker
existing tool/session liveness check
```

The heartbeat itself must not require a fresh reasoning worker.

---

## 4. Heartbeat tick algorithm

At each heartbeat deadline, use the least invasive available check.

```text
HEARTBEAT DEADLINE
        ↓
is there newer genuine visible progress?
   ├─ YES → reset heartbeat window; do nothing else
   └─ NO
        ↓
check actual execution state
        ↓
work is confirmed alive and merely quiet?
   ├─ YES → emit one short heartbeat/status marker
   │        → reset heartbeat window
   │        → continue waiting/work
   │
   └─ NO / UNKNOWN
            ↓
         DO NOT fake liveness
            ↓
         enter recovery / state verification
```

A heartbeat is valid only when there is a reasonable liveness basis, for example:

```text
worker status explicitly indicates RUNNING / active execution
worker/runtime explicitly reports waiting on an owned in-flight dependency
terminal process/session still exists
tool call is still active
the scheduler is intentionally waiting for already-dispatched work
backend/session status confirms the turn is still in progress
```

If liveness cannot be confirmed, the workflow must verify/recover actual state instead of emitting heartbeats forever.

`SLEEPING` by itself is **not** proof that an assigned task is still executing. A sleeping/revivable worker may already have finished its prior assignment. When a worker is SLEEPING, first consume/verify its available result/state; do not keep heartbeating as though that worker were still computing.

---

## 5. Safe heartbeat behavior

Heartbeat work must be **non-mutating, cheap, and bounded**.

Allowed examples:

```text
read current worker/tool status
poll an already-owned running terminal session without sending destructive input
perform a local scheduler wake-up
emit a concise orchestration status marker
inspect an already-known process/session state
```

When no native scheduler/status primitive exists, a bounded local no-op wait may be used solely as an orchestration wake-up. Prime/GPT chooses the delay for that specific wait. For example on the current Windows repository host:

```powershell
$heartbeatDelaySeconds = 120 # example only; choose from CURRENT execution state
Start-Sleep -Seconds $heartbeatDelaySeconds
Write-Output '[HEARTBEAT] orchestration wake-up'
```

`120` above is illustrative, not a default or required cadence. This is only a wake-up/checkpoint mechanism. After it returns, Prime must re-evaluate actual work state and choose the next interval again; it must not blindly repeat the same wait if the execution has completed, failed, changed phase, or become unknown.

Forbidden heartbeat behavior:

```text
starting a new model turn only to say "continue"
pinging a worker only to keep it awake
spawning a new worker as a heartbeat
running Gradle/build/test merely to create output
performing web search merely to create output
editing/touching files merely to create output
Git write operations merely to create output
restarting work whose previous state has not been verified
```

---

## 6. Prime waiting for workers

When Prime has dispatched work and currently has no safe authoring/review assignment of its own, Prime remains the scheduler.

Use:

```text
worker work dispatched
        ↓
Prime has no eligible immediate task
        ↓
arm / perform bounded heartbeat wait
        ↓
heartbeat deadline
        ↓
read available worker/supervisor state without sending a new worker prompt
        ↓
worker result available?
   ├─ YES → process result immediately
   └─ NO  → confirmed alive? heartbeat and continue
```

Do not manufacture filler repository work while waiting.

Do not repeatedly message a worker that is already RUNNING solely for liveness. Do not revive a SLEEPING worker merely to create heartbeat activity. A worker message/revival is for a real task, handoff, or correction, not for heartbeat traffic.

---

## 7. Long-running terminal/tool work

If Prime owns a long-running command or tool session, prefer the runtime's existing session/poll mechanism rather than launching duplicate commands.

Required pattern:

```text
start one command/tool
        ↓
retain its session/process identity
        ↓
bounded wait / poll
        ↓
new output?
   ├─ YES → genuine progress; reset heartbeat window
   └─ NO  → process still alive?
             ├─ YES → heartbeat status and continue polling
             └─ NO  → collect terminal state and handle completion/failure
```

Never start a replacement build/process merely because the first one is quiet.

---

## 8. Long model reasoning / no external work

The model must not pretend it can self-interrupt an already-running uninterrupted inference at an exact wall-clock time.

Therefore, when a phase is expected to require unusually long reasoning with no external activity, the execution design should prefer bounded checkpoints:

```text
inspect / reason about one bounded unit
        ↓
record visible progress or perform the next real tool action
        ↓
continue with the next bounded unit
```

If the host runtime provides an independent scheduler/heartbeat facility, use that facility because it can wake independently of model reasoning.

If no independent scheduler exists, do not claim that a prompt instruction alone can guarantee an exact timed heartbeat during hidden reasoning. Instead structure the work into smaller checkpoints before entering a potentially long silent phase.

---

## 9. Interrupted connection / recovery

A local heartbeat does not repair a disconnected response stream by itself.

If the runtime reports an interrupted/disconnected/recovered response, use:

```text
connection/stream interruption detected
        ↓
stop treating heartbeat alone as proof of health
        ↓
recover/reconnect when the host supports it
        ↓
verify actual worker/tool/repository state
        ↓
resume only remaining work
```

After recovery, assume that some requested work **may already have completed** even if the final assistant message was lost.

Therefore always verify actual state before retrying side-effecting work.

Never blindly replay the whole previous assignment after an interrupted turn.

---

## 10. Heartbeat status is not task progress

Keep the distinction explicit:

```text
REAL PROGRESS
→ source/review/validation/tool/worker state materially advanced

HEARTBEAT
→ execution is confirmed alive but no material result is ready yet
```

Heartbeat messages must be short and factual, for example:

```text
[HEARTBEAT] Worker execution is still active; no new result yet.
[HEARTBEAT] Terminal process is still running; continuing to wait.
[HEARTBEAT] Prime scheduler is active; waiting for dispatched work.
```

Do not describe a heartbeat as implementation progress when nothing material changed.

---

## 11. Stop conditions

Stop heartbeating immediately when any of these becomes true:

```text
the awaited work completed
the awaited work failed
the awaited work was cancelled
liveness can no longer be confirmed
a real next action is available
the workflow reached its normal completion/blocker condition
```

Heartbeat is never an excuse to keep an execution alive indefinitely.

The execution remains governed by its normal completion, blocker, review, ownership, worktree, and final-delivery rules.

---

## 12. Integration contract

This file is cross-cutting and must be applied together with the active orchestration document:

```text
single-module/full-chain execution
→ ../execute/execute-goal/FULL_WORKFLOW.md

parent-module/horizontal execution
→ ../execute/module-step-goal/PARENT_MODULE_STEP_WORKFLOW.md

canonical module-generation governance
→ ../GENERAL_AGENT_RULES.md
```

Heartbeat never changes STEP ordering, review requirements, worker ownership, write isolation, Curriculum rules, or Commit/final-delivery authority.

Its only authority is **liveness-safe waiting, visible heartbeat cadence, and interrupted-turn recovery behavior**.
