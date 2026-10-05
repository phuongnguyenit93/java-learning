# 📂 README MODULE STRUCTURE (VI)

* **1.Introduction**
    * [SpringMessaging](readme/vi/menu/1.Introduction/SpringMessaging.md)
* **2.MessageModel**
    * [MessageModel](readme/vi/menu/2.MessageModel/MessageModel.md)
* **3.WebSocketFoundation**
    * [WebSocketFoundation](readme/vi/menu/3.WebSocketFoundation/WebSocketFoundation.md)
* **4.StompModel**
    * [StompModel](readme/vi/menu/4.StompModel/StompModel.md)
* **5.StompMessageFlow**
    * [StompMessageFlow](readme/vi/menu/5.StompMessageFlow/StompMessageFlow.md)
* **6.StompApplicationHandling**
    * [StompApplicationHandling](readme/vi/menu/6.StompApplicationHandling/StompApplicationHandling.md)
* **7.BrokerRouting**
    * [BrokerRouting](readme/vi/menu/7.BrokerRouting/BrokerRouting.md)
* **8.StompClientLifecycle**
    * [StompClientLifecycle](readme/vi/menu/8.StompClientLifecycle/StompClientLifecycle.md)
* **9.StompLifecycleInterception**
    * [StompLifecycleInterception](readme/vi/menu/9.StompLifecycleInterception/StompLifecycleInterception.md)
* **10.RSocketInteraction**
    * [RSocketInteraction](readme/vi/menu/10.RSocketInteraction/RSocketInteraction.md)
* **11.RSocketRequester**
    * [RSocketRequester](readme/vi/menu/11.RSocketRequester/RSocketRequester.md)
* **12.RSocketResponders**
    * [RSocketResponders](readme/vi/menu/12.RSocketResponders/RSocketResponders.md)
* **13.OperationsTestingDecisions**
    * [OperationsTestingDecisions](readme/vi/menu/13.OperationsTestingDecisions/OperationsTestingDecisions.md)

# Spring Messaging

Spring Messaging là nền tảng của Spring Framework cho giao tiếp ứng dụng theo mô hình message. Module này tập trung vào ba programming model có liên hệ với nhau và cùng sử dụng Spring messaging infrastructure:

```text
Spring Messaging core abstractions
→ WebSocket + STOMP application messaging
→ Spring RSocket requester/responder programming model
```

Mục tiêu không phải là ghi nhớ annotation hay protocol command. Mục tiêu là hiểu message đi qua Spring như thế nào, application handler được chọn ra sao, broker hoặc transport infrastructure tham gia ở đâu, và khi nào ownership phải được handoff sang Spring project khác.

## Prerequisites

Trước khi học module này, learner nên nắm:

- Spring container và dependency-injection fundamentals;
- Java concurrency cơ bản như executor và asynchronous execution;
- HTTP request/response và ý tưởng HTTP Upgrade;
- reactive-programming và backpressure fundamentals trước phần RSocket.

Module này không dạy lại sâu Reactor, Spring Security policy, Spring Session internals, broker-native administration hay Enterprise Integration Patterns.

## Learning flow

Menu được sắp xếp thành một learning journey liên tục:

1. xác định vì sao messaging tồn tại và boundary của module;
2. học mô hình transport-neutral gồm `Message`, channel, handler, conversion và error;
3. kết nối mô hình đó với Spring WebSocket trên Servlet stack;
4. học STOMP semantics và cách STOMP frame trở thành Spring message;
5. theo dõi end-to-end Spring STOMP message flow;
6. học annotated application handling và programmatic sending;
7. hiểu broker routing và user destination;
8. học Spring STOMP client và connection lifecycle;
9. học STOMP/WebSocket lifecycle event, interception, ordering cùng ranh giới security/session;
10. xây dựng RSocket interaction model tối thiểu cần cho Spring;
11. học `RSocketRequester` và `RSocketStrategies`;
12. học annotated RSocket responder và declarative RSocket service interface;
13. kết thúc bằng operations, testing, scaling và architecture decision.

Thứ tự chapter là thứ tự sư phạm. WebSocket/STOMP không technically phụ thuộc RSocket và RSocket cũng không phụ thuộc STOMP. Chúng là các application-messaging model riêng nhưng cùng tái sử dụng một phần Spring messaging foundation.

## Module boundaries

Module này sở hữu Spring Framework messaging mechanics:

- `Message`, headers, channels, handlers, conversion và interception;
- Spring WebSocket abstraction dùng trên Servlet stack;
- STOMP-over-WebSocket application messaging;
- simple broker và STOMP broker-relay integration ở boundary của Spring Framework;
- user destination, STOMP client support, ordering và messaging lifecycle;
- Spring RSocket requester/responder APIs, routing/metadata, annotated handling và RSocket service interface.

Module này không sở hữu:

- Enterprise Integration Patterns, gateway, router, transformer, splitter, aggregator hoặc integration adapter — thuộc Spring Integration;
- Kafka hoặc AMQP-specific producer/consumer semantics — thuộc Spring Kafka và Spring AMQP;
- authentication và authorization policy — thuộc Spring Security;
- distributed HTTP-session management — thuộc Spring Session;
- Reactor và Reactive Streams fundamentals — thuộc reactive-learning owner;
- raw WebSocket hoặc deep RSocket protocol internals ngoài phần tối thiểu cần để hiểu Spring behavior.

Kết thúc module, learner phải có khả năng trace một message end to end, chủ động chọn raw WebSocket, STOMP hay RSocket, hiểu khi nào simple broker hoặc broker relay phù hợp, và nhận biết lúc nào bài toán phải handoff sang Spring Integration hoặc neighboring module khác.
