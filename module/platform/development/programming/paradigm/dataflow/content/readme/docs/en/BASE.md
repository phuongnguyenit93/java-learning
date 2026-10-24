# Dataflow Programming

Represent computation as nodes and data-dependency edges where work becomes eligible when its inputs are available.

## Learning goals

Understand the mental model, motivation, execution mechanisms, suitable applications and trade-offs. This module is **language-independent**; concrete libraries and frameworks are examples, not the curriculum's owner.

## Prerequisites

Functions, data structures and simple dependency graphs; functional and imperative concepts help comparison.

## Scope boundaries

Distinct from Data-Oriented Programming (data representation), Reactive Programming (signals over time/backpressure) and general workflow orchestration. Owns dataflow graphs, readiness, scheduling and determinism, not a specific pipeline framework.

## Learning path

1. **Dataflow model and dependency graphs:** Distinguish data dependencies from imperative statement ordering.
2. **Execution readiness:** Explain when nodes are eligible for execution and how outputs enable downstream work.
3. **Graphs, cycles and feedback:** Differentiate DAG computation, feedback loops and iterative execution.
4. **Dependency-driven parallelism:** Identify independent branches, scheduling opportunities and critical paths.
5. **Determinism and state:** Reason about mutable state, effects, retries and nondeterminism.
6. **Batch and streaming dataflow:** Contrast batch and streaming computation with reactive signal models.
7. **Applications and trade-offs:** Compare build, ETL and computation graphs and their abstraction costs.

> The roadmap is the high-level source of truth. Knowledge/Menu, Quiz, Interview and experiments belong to subsequent workflow steps and are not marked complete here.
