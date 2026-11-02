# Transactional Outbox Pattern

Transactional Outbox is a **distributed-systems messaging and integration pattern** for reliably publishing events when a business operation must update a database and notify another service. It addresses the **dual-write problem**: a database commit can succeed while a subsequent publish to Kafka, RabbitMQ, or another destination fails.

## Core idea

1. In **one local database transaction**, persist the business state change (for example, an order) and an outbox record describing the event (`OrderCreated`).
2. Commit both records together. If the transaction rolls back, neither record remains.
3. An independent outbox relay publishes committed events, using polling or change data capture (CDC), and retries failures.
4. Consumers handle duplicates using stable event IDs or business-level idempotency keys.

This avoids losing the **intent to publish** after a successful database commit. It does **not** guarantee exactly-once delivery across the database, broker, and consumers: the relay can crash after publishing but before recording success. Event ordering, retries, backoff, dead-letter handling, retention, and monitoring require explicit design.

## Learning scope

- The dual-write problem and atomic local transactions.
- Outbox record structure: event ID, aggregate ID, event type, payload, creation time, and publication state.
- Relay options: polling publisher versus CDC (for example, Debezium).
- At-least-once delivery, consumer idempotency, ordering, and recovery from crashes.
- Integration testing for rollback, publish failure, duplicate delivery, and eventual delivery.

## Related concepts

- [Saga](../saga/README.md): coordinates a multi-step distributed business process; outbox can reliably publish messages used by a saga.
- [Distributed System](../README.md): the broader architectural context.
- Kafka's advanced materials under `module/integration/messaging/event-streaming/kafka`: broker-specific delivery and transaction semantics.

This README is an introductory module overview; the full Roadmap, Knowledge, and executable examples have not been authored yet.
