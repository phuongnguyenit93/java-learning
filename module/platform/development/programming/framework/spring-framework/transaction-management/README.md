# 📂 README MODULE STRUCTURE (EN)

* **1.Purpose**
    * [Purpose](readme/en/menu/1.Purpose/Purpose.md)
* **2.Abstraction**
    * [Abstraction](readme/en/menu/2.Abstraction/Abstraction.md)
* **3.ResourceSynchronization**
    * [ResourceSynchronization](readme/en/menu/3.ResourceSynchronization/ResourceSynchronization.md)
* **4.Declarative**
    * [Declarative](readme/en/menu/4.Declarative/Declarative.md)
* **5.TransactionPolicy**
    * [TransactionPolicy](readme/en/menu/5.TransactionPolicy/TransactionPolicy.md)
* **6.Rollback**
    * [Rollback](readme/en/menu/6.Rollback/Rollback.md)
* **7.Propagation**
    * [Propagation](readme/en/menu/7.Propagation/Propagation.md)
* **8.Programmatic**
    * [Programmatic](readme/en/menu/8.Programmatic/Programmatic.md)
* **9.Reactive**
    * [Reactive](readme/en/menu/9.Reactive/Reactive.md)
* **10.EventsLifecycle**
    * [EventsLifecycle](readme/en/menu/10.EventsLifecycle/EventsLifecycle.md)
* **11.BoundarySynthesis**
    * [BoundarySynthesis](readme/en/menu/11.BoundarySynthesis/BoundarySynthesis.md)

# Spring Transaction Management

This module teaches **Spring Framework transaction management as a framework abstraction**, not as a list of `@Transactional` options and not as a replacement for database transaction fundamentals.

The main goal is to understand how Spring turns transaction policy into a reliable application boundary across imperative and reactive execution models.

The learning journey is:

```text
why transaction management exists
        ↓
Spring transaction abstraction
        ↓
resource synchronization
        ↓
declarative transaction boundaries
        ↓
transaction policy and rollback
        ↓
propagation
        ↓
programmatic control
        ↓
reactive transactions
        ↓
transaction-bound events and lifecycle hooks
        ↓
production boundary synthesis
```

## What this module owns

This module is the primary owner of:

- Spring's transaction abstraction and transaction-manager strategies;
- `@Transactional` semantics and declarative demarcation;
- transaction policy such as isolation, timeout, read-only mode, labels, and manager selection;
- rollback rules and rollback-only state;
- propagation and transaction participation;
- transaction-bound resource synchronization at the framework level;
- programmatic transaction management;
- imperative vs reactive transaction context;
- transaction-bound events and transaction execution listeners;
- transaction-boundary design for real applications.

## Prerequisites

Learners should already understand:

- Java exceptions and call stacks;
- basic database transaction concepts such as commit, rollback, isolation, and savepoints;
- basic Spring container and bean concepts;
- the proxy/interceptor mental model at a high level;
- Reactor Context before going deeply into reactive transaction behavior.

Deep proxy mechanics belong to the **Spring AOP** module. JDBC, ORM, and R2DBC data-access mechanics belong to the **Spring Data Access / persistence** modules. Reactive programming fundamentals belong to the **Reactive** module.

## Chapter map

The module is organized into eleven chapters:

1. **Why Transaction Management Exists** — establishes the unit-of-work and boundary mental model.
2. **Spring Transaction Abstraction and Manager Strategies** — introduces the core manager contracts and execution model.
3. **Resource Synchronization and Data-Access Participation** — explains how Spring associates resources so multiple data-access operations participate in one transaction.
4. **Declarative Transaction Demarcation** — connects metadata, interception, and proxy boundaries.
5. **Transaction Attributes and Boundary Design** — turns annotation attributes into transaction policy.
6. **Rollback and Failure Semantics** — explains commit/rollback decisions and failure handling.
7. **Propagation and Transaction Participation** — models logical and physical transaction scopes.
8. **Programmatic Transaction Management** — covers explicit imperative and reactive control.
9. **Reactive Transaction Model** — replaces the thread-bound mental model with Reactor Context.
10. **Transaction-Bound Events and Lifecycle Hooks** — coordinates callbacks and observation with transaction phases.
11. **Transaction Boundary Synthesis for Production Systems** — combines the concepts into production design decisions.

## Important boundaries

This module deliberately does not become:

- a JDBC or ORM tutorial;
- a catalog of database isolation anomalies;
- a full Spring AOP course;
- a Reactor fundamentals course;
- a Spring Boot transaction auto-configuration module;
- a transactional testing module;
- a distributed transaction or distributed-systems course.

Those topics are referenced only far enough to make Spring transaction behavior understandable.

## How to study the module

Do not start by memorizing propagation constants or annotation attributes.

Use this sequence instead:

```text
identify the business unit of work
        ↓
choose the transaction boundary
        ↓
understand which transaction manager and resources participate
        ↓
understand commit / rollback semantics
        ↓
reason about nested calls and propagation
        ↓
verify thread/reactive context boundaries
        ↓
coordinate post-transaction side effects explicitly
```

The expected outcome is that you can explain **why a transaction starts, which work participates, what causes it to commit or roll back, where its guarantees stop, and which Spring mechanism is responsible at each point**.
