# Reactive Programming

Reactive Programming focuses on **describing and propagating values or events that arrive over time** through a coherent processing flow. Instead of stitching together disconnected callbacks, learners can reason from a source through processing stages to a consumer, including emission timing, failures, and stopping conditions.

**Starting point:** familiarity with values, function transformations, and intent-versus-step descriptions is helpful. Functional and declarative thinking are recommended foundations, but *streams*, *subscriptions*, *demand*, and *backpressure* are introduced here from scratch. Asynchrony means operations need not finish immediately within the same call; reactive does not automatically mean multithreaded, parallel, or non-blocking execution.

**Learning sequence:** begin with values/events changing over time, then sources, producers, consumers, push/pull, composition, and subscription. Understand rate mismatch and possible demand/backpressure responses before learning the Reactive Streams roles and signal contract. Finally, contrast normal completion, failure, and cancellation, examine resource implications, and decide where reactive is more helpful than simpler alternatives.

**Scope:** Reactive Streams is a specification for asynchronous exchange with non-blocking backpressure, not a universal definition of all reactive APIs. Project Reactor and RxJava are libraries, Spring WebFlux is a framework, and Java Flow is a Java API. Thread/scheduling mechanics and specific framework APIs belong to their separate technology or concurrency modules.
