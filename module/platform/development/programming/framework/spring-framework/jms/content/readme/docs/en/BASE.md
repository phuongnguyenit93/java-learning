# Spring JMS

This module teaches **Spring Framework JMS support** on top of Jakarta Messaging. The goal is to understand the Spring integration model around message production, synchronous reception, message-driven listeners, conversion, transactions, lifecycle, recovery, and production operation rather than learning a specific broker.

The repository baseline is **Spring Framework 6.1.14**. In this module, `JmsTemplate` is the primary synchronous JMS access template and `JmsMessagingTemplate` bridges Spring Messaging operations onto JMS. APIs introduced only in later Framework generations, such as `JmsClient` in Spring Framework 7, are outside the baseline.

## Why this module exists

Using the Jakarta Messaging API directly requires application code to deal with infrastructure concerns such as resource handling, destination resolution, conversion, listener lifecycle, recovery, and transaction participation.

Spring JMS adds a framework layer around those concerns:

```text
Application code
        ↓
Spring JMS
        ↓
Jakarta Messaging API
        ↓
JMS provider / broker
```

The module keeps those boundaries explicit. Spring JMS simplifies integration with JMS; it does not redefine broker delivery guarantees or replace the Jakarta Messaging specification.

## Prerequisites

Learners should already understand:

- basic Spring container concepts such as managed beans and configuration;
- the generic Spring Messaging idea that a message can carry a payload plus headers;
- basic asynchronous messaging concepts such as producer, consumer, queue, topic, acknowledgement, and redelivery;
- basic transaction concepts before studying coordinated JMS transactions.

Deep Spring Messaging/WebSocket/STOMP concepts belong to the neighboring `messaging` module. Generic Spring transaction policy belongs to `transaction-management`.

## Learning flow

The module follows this sequence:

```text
Spring JMS purpose and boundaries
        ↓
JmsTemplate access model
        ↓
destinations + conversion + message metadata
        ↓
listener container runtime model
        ↓
@JmsListener endpoint infrastructure
        ↓
transactions + delivery semantics
        ↓
concurrency + recovery + resource lifecycle
        ↓
observability + testing + integration boundaries
```

The order is deliberate. Annotation-driven listeners are learned only after the listener-container model is visible, and transaction/recovery behavior is learned only after message production and consumption paths are understood.

## Chapter map

1. **Spring JMS Foundations** — establishes why Spring JMS exists and separates Framework, Jakarta Messaging, and provider responsibilities.
2. **Template-based JMS Access** — builds the synchronous access model around `JmsTemplate`, callbacks, request-reply, QoS, exception translation, and the `JmsMessagingTemplate` bridge to Spring Messaging.
3. **Destinations, Conversion, and Message Metadata** — covers destination resolution, payload conversion, JMS metadata, and mapping between Spring Messaging headers and JMS metadata.
4. **Message Listener Container Model** — explains the runtime container that owns asynchronous consumer lifecycle, resources, dispatch, and recovery.
5. **Annotation-driven Listener Endpoints** — connects `@EnableJms` and `@JmsListener` to container factories, endpoint registries, method invocation, and replies.
6. **Transactions and Delivery Semantics** — connects acknowledgement, rollback, local JMS transactions, JTA/XA coordination, redelivery, and idempotency.
7. **Concurrency, Recovery, and Resource Lifecycle** — covers concurrency choices, subscription configuration, recovery, connection wrappers, and listener-container caching.
8. **Production Readiness and Integration Boundaries** — closes the module with Spring Framework 6.1 JMS observations, testing strategy, and handoff to neighboring Spring messaging technologies.

## Module boundaries

This module owns direct Spring-style JMS access:

```text
JmsTemplate
JmsMessagingTemplate
MessageListenerContainer
@JmsListener
JmsTransactionManager
Spring JMS conversion / destination / connection support
```

It does **not** own:

- Jakarta Messaging specification semantics in full;
- broker-specific administration, clustering, persistence, or tuning;
- Enterprise Integration Patterns and JMS gateways/adapters from Spring Integration;
- Spring AMQP or Spring Kafka abstractions;
- generic Spring Messaging/WebSocket/STOMP infrastructure;
- generic Spring transaction-management theory.

Those topics are referenced only where necessary to define the Spring JMS boundary.
