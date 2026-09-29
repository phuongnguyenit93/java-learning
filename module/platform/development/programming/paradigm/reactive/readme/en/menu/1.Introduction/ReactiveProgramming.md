# Reactive Programming

## <a id="reactive-what">1. What is Reactive Programming?</a>

Reactive Programming is a paradigm that organizes logic around **data or event streams that change over time** and the propagation of those changes through processing stages.

Instead of always asking for a value immediately, reactive code often describes what should happen when new data or events arrive.

## <a id="reactive-why">2. Why does it exist?</a>

Systems with continuous events, asynchronous I/O, or producers and consumers operating at different speeds need a model that represents data flow and timing explicitly.

Manual callbacks can solve individual cases, but long flows can make composition, error propagation, and cancellation difficult to reason about.

## <a id="reactive-solution">3. How does Reactive Programming help?</a>

```text
source
  ↓
stream of signals
  ↓
transform / filter / combine
  ↓
consumer
```

Important concerns include push/pull models, asynchronous boundaries, backpressure, cancellation, and error signaling.

## <a id="reactive-streams-boundary">4. Where do Reactive Streams fit?</a>

Reactive Streams is not the whole Reactive Programming paradigm. It is a specification and contract focused on asynchronous stream processing with non-blocking backpressure.

Publisher, Subscriber, Subscription, and Processor belong to that contract layer.

## <a id="reactive-framework-boundary">5. Boundary with frameworks</a>

This module does not own Spring WebFlux or framework-specific APIs.

Spring-specific reactive implementation belongs to `framework/spring-framework/reactive`.
