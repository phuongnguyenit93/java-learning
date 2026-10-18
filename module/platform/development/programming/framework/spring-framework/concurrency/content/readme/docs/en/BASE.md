# Spring Framework Concurrency

Spring Framework Concurrency explains how Spring manages task execution, asynchronous method invocation, and time-based scheduling on top of Java concurrency primitives. The module focuses on framework abstractions, container lifecycle, proxy-driven async dispatch, context propagation, scheduler behavior, and the Spring Framework 6.1 integration points for JDK 21 virtual threads.

The goal is not to relearn threads, executors, futures, synchronization, or the Java Memory Model. Those concepts remain prerequisites from Java Concurrency. Here, the learner builds the mental model needed to choose and operate Spring-managed executors and schedulers correctly.

## Prerequisites

- Java Concurrency fundamentals: threads, executors, futures, synchronization, thread pools, and virtual-thread basics.
- Spring Core Container: beans, dependency injection, configuration, and managed lifecycle.
- Spring AOP basics when reasoning about proxy-based `@Async` interception and self-invocation.
- Reactive Programming fundamentals only when studying reactive `@Scheduled` methods.

## Learning flow

1. **Spring Concurrency Mental Model** — separate task execution, asynchronous invocation, and scheduling while keeping the Spring/JDK boundary clear.
2. **Task Execution and `@Async`** — learn `TaskExecutor`, `ThreadPoolTaskExecutor`, async proxy dispatch, executor selection, return types, and failure observation.
3. **Context Propagation** — understand why thread-bound context is lost and how `TaskDecorator` and Spring 6.1 context-propagation support address the boundary safely.
4. **TaskScheduler and Programmatic Scheduling** — build the scheduling model around `TaskScheduler`, `Trigger`, implementation choices, cancellation, rescheduling, and lifecycle.
5. **Declarative Scheduling with `@Scheduled`** — learn trigger modes, cron/time-zone semantics, scheduler qualification, overlap, failures, and reactive scheduled methods.
6. **Virtual-Thread Integration** — compare Spring's virtual-thread executors/scheduler with pooled platform-thread infrastructure and understand the fixed-delay caveat.
7. **Lifecycle, Failures, and Production Reasoning** — combine shutdown, saturation, failure models, diagnostics, Boot handoff, and end-to-end executor/scheduler decisions.

## Module boundary

This module owns Spring Framework task execution and scheduling abstractions. It does not own the underlying Java concurrency model, generic proxy/AOP theory, Spring Boot task auto-configuration, Reactive Streams theory, or Quartz internals.

Spring Boot may provide convenient executor and scheduler defaults, but those defaults belong to the Spring Boot curriculum. Quartz appears here only as a decision boundary for workloads that need capabilities beyond Spring's in-process `TaskScheduler` model.

The repository baseline for this learning journey is Spring Framework **6.1.14** on Java **21**.
