# Event-Driven Programming

Organize behavior around events and handlers rather than assuming a single direct call sequence.

## Learning goals

Understand the mental model, motivation, execution mechanisms, suitable applications and trade-offs. This module is **language-independent**; concrete libraries and frameworks are examples, not the curriculum's owner.

## Prerequisites

Control flow, callbacks and basic application state.

## Scope boundaries

Owns event loops, dispatchers, handlers, ordering, reentrancy and lifecycle; distinct from Event-Driven Architecture between services and from Reactive Streams subscription/backpressure.

## Learning path

1. **Events, commands and state:** Distinguish events, commands, notifications and state transitions.
2. **Publishers, dispatchers and handlers:** Understand handler registration, dispatch and callback ownership.
3. **Event loops and queues:** Model queues, scheduling and the danger of long-running handlers.
4. **Ordering and reentrancy:** Analyze ordering, nested dispatch, reentrancy and state consistency.
5. **Failures and handler lifecycle:** Handle errors, timeouts, unsubscription and cleanup.
6. **Boundaries with Reactive and EDA:** Differentiate in-process event handling from reactive streams and service-level event-driven architecture.
7. **Design, testing and trade-offs:** Build and test a simple dispatcher, ordering and hidden complexity.

> The roadmap is the high-level source of truth. Knowledge/Menu, Quiz, Interview and experiments belong to subsequent workflow steps and are not marked complete here.
