<a id="back-to-top"></a>

# Branching and Repetition in Control Flow

## Menu
- [The Role of Control Flow in Imperative Programs](#imperative-control-purpose)
- [Condition-Based Branch Selection](#imperative-branch-selection)
- [Repeating Actions by Condition or Collection](#imperative-repetition)
- [Tracing State and Iteration Counts](#imperative-loop-progress)
- [Termination Conditions and Nonterminating Loops](#imperative-termination)
- [Tracing an Execution with Branches and Loops](#imperative-control-trace)

## <a id="imperative-control-purpose">The Role of Control Flow in Imperative Programs</a>

<details>
<summary>Click for details</summary>

Control flow determines **which command executes next** rather than executing every statement exactly once. Branches select a route based on conditions; loops repeat actions. Both extend the state-and-order model learned earlier.

An invalid withdrawal should stop before any update, while a list of transactions may require repeated processing. Without the intended control path, individually correct arithmetic can still produce an incorrect overall outcome.

</details>

- [Back to top](#back-to-top)

---

## <a id="imperative-branch-selection">Condition-Based Branch Selection</a>

<details>
<summary>Click for details</summary>

A branch condition is evaluated when execution reaches it. For `balance >= withdrawal`, the balance at that moment determines approval or rejection; the original starting balance may no longer be relevant after earlier updates.

Each branch should have a distinct responsibility: the valid branch updates state, while rejection preserves it and explains why. If the condition checks only sufficient funds but omits `withdrawal > 0`, invalid negative inputs may enter the wrong path.

</details>

- [Back to top](#back-to-top)

---

## <a id="imperative-repetition">Repeating Actions by Condition or Collection</a>

<details>
<summary>Click for details</summary>

A loop applies an action to multiple items or repeats it until a condition changes. Summing receipts `10, 20, 5` updates a running total 0 → 10 → 30 → 35. Each iteration reads an item, computes the next total, and advances the traversal.

Iteration may have a known count or a condition that determines its eventual length. In both cases, identify the repeated action and the state that progresses; the syntax of language-specific loop constructs is taught elsewhere.

</details>

- [Back to top](#back-to-top)

---

## <a id="imperative-loop-progress">Tracing State and Iteration Counts</a>

<details>
<summary>Click for details</summary>

Read a loop through **one representative iteration**: check its condition, execute its body, advance its tracking state, then check again. After processing two of three receipts, explain why the running total is 30 and one item remains.

Advancing an index too early or too late may skip the first item or process one twice. A table of iteration number, index, current item, and accumulator reveals these off-by-one errors more reliably than checking only the final sum.

</details>

- [Back to top](#back-to-top)

---

## <a id="imperative-termination">Termination Conditions and Nonterminating Loops</a>

<details>
<summary>Click for details</summary>

A loop terminates when its continuation condition becomes false or an explicit exit is taken. A loop with `remaining > 0` needs progress that reduces `remaining`, or some external change that eventually alters the condition.

If the deciding state never changes, the loop may run forever. Conversely, decrementing too far can violate an invariant such as `remaining >= 0`. Analyze termination together with state progress, not by looking at the body alone.

</details>

- [Back to top](#back-to-top)

---

## <a id="imperative-control-trace">Tracing an Execution with Branches and Loops</a>

<details>
<summary>Click for details</summary>

Trace transactions `[+20, -30, -200]` from balance 100 with a no-negative rule. Deposit 20 → 120; withdraw 30 → 90; reject withdrawal 200 → 90. All three iterations are examined, but only two update state.

This trace combines sequencing, branching, and repetition. Reordering transactions could alter which withdrawals succeed even when the requested amounts are unchanged. Testing an imperative solution therefore requires checking execution paths, not just formulas.

</details>

- [Back to top](#back-to-top)
