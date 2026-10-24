# Concurrent Programming

Organize tasks that may progress independently or interleave, coordinating shared state or message exchange while preserving safety and progress.

## Learning goals

Understand the mental model, motivation, execution mechanisms, suitable applications and trade-offs. This module is **language-independent**; concrete libraries and frameworks are examples, not the curriculum's owner.

## Prerequisites

State, functions, asynchronous actions and basic execution flow.

## Scope boundaries

Language-independent models: shared memory, actors/message passing and CSP/channels. Java threads, synchronization, virtual threads and the Java Memory Model belong to Java Advance; reactive/backpressure belongs to Reactive.

## Learning path

1. **Concurrency, parallelism and asynchrony:** Distinguish concurrency, parallelism, asynchrony and interleaving.
2. **Shared state and synchronization:** Identify races, critical sections and ownership boundaries.
3. **Visibility, atomicity and ordering:** Explain visibility, atomicity and ordering across independently progressing tasks.
4. **Message passing and actors:** Model message exchange, actor isolation, mailboxes and conceptual supervision.
5. **CSP and channels:** Contrast channels with shared memory, including rendezvous and buffering.
6. **Progress and liveness:** Recognize deadlocks, livelocks, starvation and fairness constraints.
7. **Cancellation, failures and testing:** Plan cancellation, task lifetime, deterministic testing and trade-offs.

> The roadmap is the high-level source of truth. Knowledge/Menu, Quiz, Interview and experiments belong to subsequent workflow steps and are not marked complete here.
