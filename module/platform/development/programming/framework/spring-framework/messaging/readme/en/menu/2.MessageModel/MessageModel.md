<a id="back-to-top"></a>

# Message Model, Channels, and Handlers

## Menu
- [Message as payload and headers](#message-payload-headers)
- [Building messages and accessing headers](#message-building-access)
- [MessageChannel](#message-channel)
- [SubscribableChannel and subscribers](#subscribable-channel)
- [MessageHandler](#message-handler)
- [Synchronous and executor-backed delivery](#channel-delivery-semantics)
- [Message conversion](#message-conversion)
- [Messaging errors and delivery failures](#messaging-errors)

## <a id="message-payload-headers">Message as payload and headers</a>

<details>
<summary>Click for details</summary>

Spring represents an application message as two parts: a typed payload and metadata. Message<T> exposes getPayload() and getHeaders(). The payload is the business value being communicated; headers carry information needed to route, convert, correlate, or describe that payload without forcing those concerns into the payload type itself.

MessageHeaders implements Map<String, Object>, but in Spring Framework 6.1 it is immutable. Calls such as put, putAll, remove, or clear throw UnsupportedOperationException. Treat a Message as a value that is created and then handed across component boundaries. If a later stage needs different headers, create or rebuild a message instead of mutating an already published header map.

MessageHeaders can contain framework keys such as id, timestamp, contentType, replyChannel, and errorChannel. The presence of a header does not by itself activate routing behavior; the component consuming the message decides what a header means.

</details>

- [Back to top](#back-to-top)

---

## <a id="message-building-access">Building messages and accessing headers</a>

<details>
<summary>Click for details</summary>

MessageBuilder is the usual API when application code needs to create or derive messages while preserving the immutable-header contract:

    Message<OrderCreated> message =
            MessageBuilder.withPayload(event)
                    .setHeader("tenantId", tenantId)
                    .build();

GenericMessage is convenient when the payload and a header map are already available. For protocol-specific metadata, Spring provides MessageHeaderAccessor subclasses such as SimpMessageHeaderAccessor and StompHeaderAccessor. An accessor gives typed operations while a message is being prepared; the resulting MessageHeaders presented by a built message remain immutable to callers.

Prefer semantic header names and avoid putting mutable application state into headers simply because the value fits in Object. Headers are shared infrastructure metadata, so accidental mutation of a mutable header value can still create race conditions even though the MessageHeaders map itself cannot be structurally modified.

</details>

- [Back to top](#back-to-top)

---

## <a id="message-channel">MessageChannel</a>

<details>
<summary>Click for details</summary>

MessageChannel is the sending boundary in the core model. A producer depends on the channel contract instead of invoking a particular consumer. The send methods return whether the message was successfully sent according to that channel's delivery contract; the overload with a timeout lets implementations apply a bounded send when that concept is meaningful.

The abstraction intentionally says little about storage, durability, threading, or fan-out. Those properties come from the concrete channel and surrounding infrastructure. In the STOMP stack, for example, clientInboundChannel, brokerChannel, and clientOutboundChannel are channels with specific roles in an end-to-end message flow.

This separation is useful in application design: a sender can focus on building a valid message and choosing the correct communication boundary. It should not infer broker guarantees or asynchronous execution merely from the fact that the value is sent through MessageChannel.

</details>

- [Back to top](#back-to-top)

---

## <a id="subscribable-channel">SubscribableChannel and subscribers</a>

<details>
<summary>Click for details</summary>

SubscribableChannel extends MessageChannel with subscribe and unsubscribe operations for MessageHandler instances. A subscriber is invoked when a message is delivered through the channel; consumers do not poll the channel for data.

The Spring Framework implementation used heavily by WebSocket/STOMP infrastructure is ExecutorSubscribableChannel. It maintains subscribers and delegates handler invocation either directly or through an Executor, depending on how it was constructed. This makes SubscribableChannel an important bridge between the logical message graph and the actual thread that performs handler work.

Subscription is an infrastructure relationship, not a broker subscription such as a STOMP SUBSCRIBE frame. The same word appears at different layers, so keep the distinction clear: SubscribableChannel manages Java MessageHandler subscribers inside the application, while STOMP subscriptions are protocol-level client registrations handled later by broker infrastructure.

</details>

- [Back to top](#back-to-top)

---

## <a id="message-handler">MessageHandler</a>

<details>
<summary>Click for details</summary>

MessageHandler is the receiving side of the core contract. Its handleMessage(Message<?>) method represents one unit of message-processing work. Framework infrastructure can therefore route a Message without knowing the concrete class that performs the work.

A handler may inspect headers, convert or delegate the payload, call application code, or forward another message, depending on its role. The generic interface does not prescribe those behaviors. It also allows MessagingException to represent failures associated with message handling.

Keep handlers focused on one processing responsibility. Once the design grows into explicit routers, filters, splitters, aggregators, or enterprise integration flows, the ownership moves to Spring Integration. The foundational MessageHandler contract remains relevant, but this module does not turn it into an EIP catalog.

</details>

- [Back to top](#back-to-top)

---

## <a id="channel-delivery-semantics">Synchronous and executor-backed delivery</a>

<details>
<summary>Click for details</summary>

Threading depends on the concrete channel and executor. With no Executor, ExecutorSubscribableChannel invokes handlers in the thread that calls send. With an Executor, handler invocation is delegated through that executor. Whether this actually creates a thread handoff or asynchronous completion depends on the Executor implementation; for example, a SyncTaskExecutor still executes tasks synchronously in the calling thread.

That distinction changes observable behavior. With caller-thread execution, a handler exception can be observed in the sender's call path and thread-local context naturally remains on the same thread. With an executor that schedules work asynchronously, send can return before handler work completes, exceptions occur in executor tasks, ordering depends on the executor, and ordinary ThreadLocal state does not automatically become application context on another thread.

    ExecutorSubscribableChannel sync = new ExecutorSubscribableChannel();
    ExecutorSubscribableChannel executorBacked =
            new ExecutorSubscribableChannel(taskExecutor);

Do not use a channel type name, or merely the presence of an Executor, as proof that processing is asynchronous. Inspect the concrete executor semantics. Replacing caller-thread execution with an executor that actually dispatches work to other threads is a semantic change, not only a performance tweak; code that relied on caller-thread execution can behave differently after that handoff.

</details>

- [Back to top](#back-to-top)

---

## <a id="message-conversion">Message conversion</a>

<details>
<summary>Click for details</summary>

Message conversion bridges Java application types and the representation carried in a message. Spring's MessageConverter contract can convert an object into a Message payload representation and can convert an incoming Message into a requested target type. CompositeMessageConverter can try multiple converters in order.

Conversion is normally invoked by higher-level infrastructure such as messaging templates or annotated handler-method argument and return-value processing. MessageChannel itself does not inspect an arbitrary payload and convert it automatically merely because a message is sent.

Content type is often part of converter selection. In STOMP handling, a client may send JSON bytes plus a content-type header, and Spring can use the configured converter to supply a domain object to a @MessageMapping method. If no converter supports the source/target combination, the failure belongs to the message-processing pipeline and should be diagnosed at the conversion boundary rather than treated as a routing problem.

Prefer explicit, predictable converter configuration. A very broad custom converter can make debugging harder by accepting payloads that should have failed fast.

</details>

- [Back to top](#back-to-top)

---

## <a id="messaging-errors">Messaging errors and delivery failures</a>

<details>
<summary>Click for details</summary>

Messaging failures have to be interpreted together with the delivery model. With caller-thread channel delivery, a MessagingException raised by a handler can propagate through the send call. When the configured executor actually schedules handler work asynchronously, the original sender may already have returned successfully before processing fails.

MessageHeaders defines replyChannel and errorChannel keys, but the core MessageChannel abstraction does not promise a universal error bus or automatic retry. Specific frameworks and components may interpret those headers; Spring Integration, for example, has richer error-channel semantics. In this module, always identify the component that owns error handling instead of assuming the header alone provides it.

At higher layers, WebSocket handlers have transport-error callbacks, annotated messaging supports @MessageExceptionHandler, STOMP clients receive protocol ERROR frames, and broker connections have their own failure signals. Those mechanisms solve different failure classes. A useful debugging sequence is: did sending fail, did conversion fail, did handler code fail, did routing miss, or did the transport/broker fail?

</details>

- [Back to top](#back-to-top)
