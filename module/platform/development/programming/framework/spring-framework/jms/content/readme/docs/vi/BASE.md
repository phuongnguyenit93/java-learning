# Spring JMS

Module này dùng để học **Spring Framework JMS support** trên nền Jakarta Messaging. Mục tiêu là hiểu đúng mô hình tích hợp của Spring quanh message production, synchronous reception, message-driven listener, conversion, transaction, lifecycle, recovery và vận hành production, thay vì học theo một broker cụ thể.

Mốc phiên bản của repository là **Spring Framework 6.1.14**. Trong module này, `JmsTemplate` là template chính cho truy cập JMS đồng bộ và `JmsMessagingTemplate` nối Spring Messaging operations lên JMS. Các API chỉ xuất hiện ở thế hệ Framework mới hơn, như `JmsClient` trong Spring Framework 7, nằm ngoài mốc này.

## Vì sao module này tồn tại?

Khi dùng Jakarta Messaging API trực tiếp, application code phải xử lý nhiều infrastructure concern như resource management, destination resolution, conversion, listener lifecycle, recovery và transaction participation.

Spring JMS đặt một framework layer quanh các concern đó:

```text
Application code
        ↓
Spring JMS
        ↓
Jakarta Messaging API
        ↓
JMS provider / broker
```

Module luôn giữ rõ các boundary này. Spring JMS đơn giản hóa việc tích hợp với JMS; nó không định nghĩa lại delivery guarantee của broker và không thay thế Jakarta Messaging specification.

## Điều kiện tiên quyết

Người học nên đã hiểu:

- Spring container cơ bản như managed bean và configuration;
- ý tưởng Spring Messaging chung rằng message có payload và headers;
- các khái niệm asynchronous messaging cơ bản như producer, consumer, queue, topic, acknowledgement và redelivery;
- transaction cơ bản trước khi học phối hợp JMS transaction.

Spring Messaging/WebSocket/STOMP chuyên sâu thuộc module `messaging`. Generic Spring transaction policy thuộc `transaction-management`.

## Luồng học

Module đi theo thứ tự:

```text
mục đích và boundary của Spring JMS
        ↓
JmsTemplate access model
        ↓
destination + conversion + message metadata
        ↓
listener container runtime model
        ↓
@JmsListener endpoint infrastructure
        ↓
transaction + delivery semantics
        ↓
concurrency + recovery + resource lifecycle
        ↓
observability + testing + integration boundaries
```

Thứ tự này có chủ đích. Annotation-driven listener chỉ được học sau khi learner thấy rõ listener-container model; transaction/recovery chỉ được học sau khi đã hiểu đường đi của message production và consumption.

## Bản đồ chương

1. **Nền tảng Spring JMS** — giải thích Spring JMS tồn tại để làm gì và tách trách nhiệm giữa Framework, Jakarta Messaging và provider.
2. **Truy cập JMS bằng Template** — xây access model đồng bộ quanh `JmsTemplate`, callback, request-reply, QoS, exception translation và cầu nối `JmsMessagingTemplate` sang Spring Messaging.
3. **Destination, chuyển đổi dữ liệu và metadata của message** — học destination resolution, payload conversion, JMS metadata và mapping giữa Spring Messaging header với JMS metadata.
4. **Mô hình Message Listener Container** — hiểu runtime container sở hữu asynchronous consumer lifecycle, resource, dispatch và recovery.
5. **Listener Endpoint điều khiển bằng Annotation** — nối `@EnableJms` và `@JmsListener` với container factory, endpoint registry, method invocation và reply.
6. **Transaction và ngữ nghĩa giao nhận** — nối acknowledgement, rollback, local JMS transaction, JTA/XA coordination, redelivery và idempotency.
7. **Concurrency, Recovery và vòng đời tài nguyên** — học concurrency choice, subscription configuration, recovery, connection wrapper và listener-container caching.
8. **Sẵn sàng vận hành và ranh giới tích hợp** — kết thúc bằng Spring Framework 6.1 JMS observation, testing strategy và handoff sang các Spring messaging technology lân cận.

## Ranh giới module

Module này sở hữu direct Spring-style JMS access:

```text
JmsTemplate
JmsMessagingTemplate
MessageListenerContainer
@JmsListener
JmsTransactionManager
Spring JMS conversion / destination / connection support
```

Module **không** sở hữu:

- toàn bộ Jakarta Messaging specification semantics;
- broker-specific administration, clustering, persistence hoặc tuning;
- Enterprise Integration Patterns và JMS gateway/adapter của Spring Integration;
- Spring AMQP hoặc Spring Kafka abstractions;
- generic Spring Messaging/WebSocket/STOMP infrastructure;
- generic Spring transaction-management theory.

Các topic trên chỉ được nhắc khi cần để xác định đúng boundary của Spring JMS.
