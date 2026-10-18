# Advanced Java Networking

This module builds a practical mental model for the JDK networking APIs: addressing and name resolution, TCP/UDP sockets, NIO network I/O models, java.net.http.HttpClient, and the JDK WebSocket client.

The goal is not to memorize the classes in java.net. The goal is to understand **how a Java program communicates over a network, where operations can wait, what data model each transport exposes, who owns network resources, and which abstraction fits a concrete requirement**.

## Prerequisites

Learners should already understand Java Core I/O, especially byte streams, Buffer, Channel, and resource lifetime, plus enough concurrency fundamentals to distinguish threads, executors, blocking, and asynchronous completion. Virtual threads appear here only to evaluate Java 21 network-I/O trade-offs; the full concurrency model belongs to the Concurrency modules.

## Learning flow

The module progresses from foundations to practical decisions:

1. establish the endpoint, transport, and I/O-completion mental model;
2. understand IP addresses, name resolution, network interfaces, URI/URL, and socket addresses;
3. learn TCP as a byte stream and UDP as datagrams;
4. control timeouts, socket options, failures, cancellation, and resource lifetime;
5. move into NIO channels, selectors, and asynchronous channels while placing virtual threads in the correct trade-off;
6. use the JDK's higher-level HTTP Client and WebSocket client;
7. synthesize an API-selection model and hand off security, concurrency, and integration concerns to their owning modules.

## Boundary

Networking owns **JDK networking APIs and their runtime mechanics**. It does not reteach Java I/O foundations, the Java Memory Model, deep TLS/JSSE and cryptography, retry/resilience architecture, gateways/service meshes, or full HTTP/WebSocket protocol architecture.

By the end of the module, a learner should be able to explain the path from hostname → resolved endpoint → transport/API model → data transfer/completion → failure/cancellation → resource release, and choose an appropriate API without assuming that non-blocking I/O is always the better option.
