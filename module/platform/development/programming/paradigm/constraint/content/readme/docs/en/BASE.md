# Constraint Programming

Define variables, domains and validity constraints so a solver can search for solutions instead of hardcoding an exhaustive search strategy.

## Learning goals

Understand the mental model, motivation, execution mechanisms, suitable applications and trade-offs. This module is **language-independent**; concrete libraries and frameworks are examples, not the curriculum's owner.

## Prerequisites

Sets, Boolean expressions, variables and conditional rules; advanced mathematical optimization is not a prerequisite.

## Scope boundaries

Distinguishes constraint satisfaction from logic programming and imperative search. Owns propagation, search, optimization, hard/soft constraints; specific OR-Tools/Timefold APIs and production integrations are elsewhere.

## Learning path

1. **Constraint satisfaction foundations:** Describe variables, domains and constraints in a satisfaction problem.
2. **Constraint modeling:** Separate problem models from solvers and recognize global and redundant constraints.
3. **Propagation and domain filtering:** Understand domain filtering, consistency and pruning before exhaustive exploration.
4. **Search and backtracking:** Reason about variable/value ordering and combinatorial explosion.
5. **Optimization and soft constraints:** Distinguish feasible from optimal solutions and model objectives and trade-offs.
6. **Scheduling and allocation:** Model a small scheduling or allocation problem and diagnose infeasibility.
7. **Complexity and trade-offs:** Compare constraint solving with rule engines, procedural search and specialized algorithms.

> The roadmap is the high-level source of truth. Knowledge/Menu, Quiz, Interview and experiments belong to subsequent workflow steps and are not marked complete here.
