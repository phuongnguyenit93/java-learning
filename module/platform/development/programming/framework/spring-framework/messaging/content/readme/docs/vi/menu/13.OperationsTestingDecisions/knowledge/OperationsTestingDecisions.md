<a id="back-to-top"></a>

# Vận hành, Testing và quyết định thiết kế

## Menu
- [Capacity của channel executor](#messaging-executor-capacity)
- [Send-time và buffer limit](#messaging-send-limits)
- [Message-size limit](#messaging-message-size-limits)
- [Broker availability và recovery](#broker-availability-recovery)
- [Slow client và ranh giới backpressure](#slow-client-boundary)
- [WebSocketMessageBrokerStats và monitoring](#websocket-broker-stats)
- [Testing WebSocket và STOMP messaging](#stomp-testing)
- [Testing RSocket messaging](#rsocket-testing)
- [Chọn raw WebSocket, STOMP hay RSocket](#choose-messaging-model)
- [Chọn simple broker hay broker relay](#choose-stomp-broker)
- [Handoff sang Spring Integration](#spring-integration-handoff)
- [Production readiness checklist](#messaging-production-checklist)

## <a id="messaging-executor-capacity">Capacity của channel executor</a>

<details>
<summary>Xem chi tiết</summary>

STOMP over WebSocket dùng các Spring channel bất đồng bộ, phía sau thường là executor, đặc biệt với `clientInboundChannel` và `clientOutboundChannel`. Vì vậy capacity planning phải bắt đầu từ loại công việc mỗi channel thực hiện thay vì chọn thread count theo cảm tính.

Inbound thường dẫn tới application handler. Nếu handler chủ yếu CPU-bound, concurrency nên gần capacity CPU; nếu handler block vì database hoặc remote call, thread sẽ bị giữ lâu hơn và có thể cần nhiều concurrency hơn hoặc tốt hơn là tách blocking work ra khỏi đường xử lý chính. Outbound chủ yếu ghi dữ liệu tới client, nên client/network chậm có thể giữ sending capacity lâu hơn nhiều.

Executor phải được cấu hình như một hệ gồm **core size, maximum size và queue capacity**. Một lỗi phổ biến của `ThreadPoolExecutor` là tăng maximum pool nhưng vẫn để queue gần như không giới hạn; task mới sẽ tiếp tục vào queue nên pool không tăng vượt core size.

```java
@Override
public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.taskExecutor()
            .corePoolSize(8)
            .maxPoolSize(16)
            .queueCapacity(2000);
}
```

Cần đo queue growth, active thread, task latency và rejection dưới traffic thực tế. Tăng thread mà không giới hạn downstream work chỉ dời bottleneck và có thể tăng memory pressure hoặc contention.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="messaging-send-limits">Send-time và buffer limit</a>

<details>
<summary>Xem chi tiết</summary>

Với STOMP over WebSocket, trường hợp outbound khó nhất là một client nhận dữ liệu chậm trong khi application tiếp tục phát message. Spring chỉ cho một thread tại một thời điểm gửi trên một WebSocket session; các message khác cho session đó sẽ được buffer.

`WebSocketTransportRegistration` có hai giới hạn bổ sung cho nhau:

```java
@Override
public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
    registration
            .setSendTimeLimit(15_000)
            .setSendBufferSizeLimit(512 * 1024);
}
```

`sendTimeLimit` giới hạn khoảng thời gian Spring cho phép một send chậm tiếp tục trong khi các message sau đang phải xếp hàng, còn `sendBufferSizeLimit` giới hạn lượng outbound data đang chờ cho session. Khi vượt safety limit, session có thể bị đóng để một slow client không chiếm tài nguyên server không giới hạn.

Một chi tiết quan trọng của 6.1 là send-time limit được kiểm tra khi có một message khác tiếp tục cố gửi. Nếu chỉ có một send bị treo và không có send tiếp theo, setting này tự nó không ngắt physical socket write đó. Vì vậy đây là guardrail cho buffering/tài nguyên, không thay thế timeout của WebSocket server hoặc network connection.

Đây là guardrail an toàn, không phải throughput target. Nên chọn giá trị dựa trên message size thực tế, mức lag chấp nhận được, network condition và memory budget. Buffer quá lớn có thể che giấu slow-client problem cho tới khi latency/heap tăng mạnh; buffer quá nhỏ có thể ngắt client khỏe trong burst ngắn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="messaging-message-size-limits">Message-size limit</a>

<details>
<summary>Xem chi tiết</summary>

`messageSizeLimit` kiểm soát kích thước tối đa của một STOMP message inbound sau khi Spring buffer và ráp lại. Nó giải quyết vấn đề khác với outbound send buffer.

WebSocket container có frame/message limit riêng, và STOMP client có thể chia một STOMP message lớn thành nhiều WebSocket message. Spring STOMP support có thể ráp các phần này, vì vậy application cần upper bound cho logical STOMP message:

```java
@Override
public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
    registration.setMessageSizeLimit(128 * 1024);
}
```

Nên đặt giới hạn dựa trên command/event hợp lệ lớn nhất cộng serialization overhead. Payload lớn làm tăng allocation, copy, parse time và bề mặt denial-of-service. Nếu message thường xuyên lên tới nhiều MB, nên xem nội dung đó có phù hợp hơn với object/blob storage rồi chỉ gửi reference qua messaging hay không.

Cần tách ba loại limit: limit của WebSocket container, Spring STOMP message-size limit sau reassembly, và outbound send buffer limit theo session. Tuning một loại không tự động thay đổi hai loại còn lại.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="broker-availability-recovery">Broker availability và recovery</a>

<details>
<summary>Xem chi tiết</summary>

Với simple broker, broker processing nằm ngay trong application process. Với STOMP broker relay, Spring duy trì TCP connection tới external broker, trong đó có “system” connection dùng cho message do application chủ động phát. External broker có thể unavailable độc lập với WebSocket client session.

Spring publish `BrokerAvailabilityEvent` khi trạng thái availability của broker relay thay đổi. Component chủ động gửi message qua broker có thể lắng nghe event này để pause, reject hoặc degrade công việc trong lúc system connection không hoạt động.

```java
@EventListener
void brokerAvailability(BrokerAvailabilityEvent event) {
    brokerAvailable.set(event.isBrokerAvailable());
}
```

Broker relay tự động cố thiết lập lại **system connection** dùng chung khi mất kết nối tới broker. Các broker connection đại diện cho từng WebSocket/STOMP client không được tự động reconnect; client phải có reconnect logic riêng. Dù theo hướng nào, reconnect cũng không tự làm message được tạo trong outage trở nên durable. Application phải quyết định rõ: fail fast, buffer bằng một durable/bounded component, retry idempotent hay drop theo business semantics.

External broker cũng cần được monitor độc lập. WebSocket endpoint vẫn accept connection chỉ chứng minh application còn nhận được client; nó không chứng minh broker relay vẫn route subscription và broadcast thành công.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="slow-client-boundary">Slow client và ranh giới backpressure</a>

<details>
<summary>Xem chi tiết</summary>

STOMP over WebSocket và RSocket có backpressure model khác nhau. Một STOMP/WebSocket client có thể chậm, nhưng protocol stack không mang Reactive Streams demand từ client ngược xuyên qua toàn bộ application producer.

Spring vì vậy bảo vệ STOMP session bằng executor capacity kết hợp send-time và buffer limit. Khi client tụt lại, application phải chọn policy như coalesce update, sample data, giới hạn queue, drop message không quan trọng hoặc disconnect session. Chỉ tăng queue size là trì hoãn quyết định chứ không giải quyết nguyên nhân.

RSocket request-stream và request-channel mang demand qua `REQUEST_N`, nhờ đó remote requester có thể giới hạn lượng payload responder được phép phát trên stream đó. Tuy nhiên backpressure vẫn scoped theo stream; nó không tự giới hạn số lượng independent request mới nếu không có thêm admission control hoặc cơ chế như leasing.

Với cả hai protocol, phải chú ý fan-out multiplication. Một application event broadcast tới hàng nghìn client có thể tạo hàng nghìn slow-consumer path riêng. Cần giới hạn ở từng session và ở aggregate service level, đồng thời định nghĩa rõ loss/replay semantics.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="websocket-broker-stats">WebSocketMessageBrokerStats và monitoring</a>

<details>
<summary>Xem chi tiết</summary>

`WebSocketMessageBrokerStats` cung cấp một operational view gọn về hạ tầng WebSocket/STOMP của Spring. Nó báo thông tin về:

- WebSocket session;
- quá trình xử lý STOMP sub-protocol;
- STOMP broker relay nếu có;
- executor của `clientInboundChannel`;
- executor của `clientOutboundChannel`;
- SockJS task scheduler.

Spring định kỳ log các thống kê này; logging period mặc định là 30 phút và có thể đổi qua `setLoggingPeriod`. Class cũng có các method `get...StatsInfo()` để lấy từng nhóm thông tin.

Các stats này phù hợp để trả lời câu hỏi như “session có đang tích tụ không?”, “outbound executor có saturated không?” hay “broker relay còn connected không?”. Chúng là diagnostic summary, không thay thế observability system hoàn chỉnh. Production vẫn nên có structured metric cho latency, failure, queue depth, disconnect reason, message rate, broker health và business-level success.

Cần đọc metric theo mối quan hệ nguyên nhân. Outbound queue cao có thể do slow client, message quá lớn hoặc bursty publisher; chỉ nhìn thread count sẽ không đủ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="stomp-testing">Testing WebSocket và STOMP messaging</a>

<details>
<summary>Xem chi tiết</summary>

Test WebSocket/STOMP nên bao phủ framework mapping layer chứ không chỉ business logic. Gọi trực tiếp controller method chỉ kiểm tra Java code, không kiểm tra destination mapping, message conversion, header, interceptor hay channel routing.

Spring mô tả hai cách server-side test: load application configuration thật rồi gửi message qua `clientInboundChannel`, hoặc dựng tối thiểu messaging infrastructure như `SimpAnnotationMethodMessageHandler` quanh các controller cần test. Các test này tập trung và nhanh hơn việc mở network connection.

End-to-end test khởi động embedded WebSocket server rồi dùng STOMP client như `WebSocketStompClient`. Khi đó có thể kiểm tra handshake, CONNECT/SUBSCRIBE/SEND flow, broker routing, serialization, user destination và disconnect behavior qua transport thật.

Nên dùng synchronization primitive có timeout thay vì sleep cố định. Ví dụ complete future/latch khi frame mong đợi tới và fail rõ ràng nếu timeout. Test teardown phải unsubscribe/close session để một test không rò state sang test khác.

Chỉ cần một số ít end-to-end test để bảo vệ wiring/protocol behavior; business service phía dưới messaging layer nên được test bằng unit/integration test thông thường.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rsocket-testing">Testing RSocket messaging</a>

<details>
<summary>Xem chi tiết</summary>

Test RSocket nên kiểm tra interaction contract thay vì chỉ gọi trực tiếp responder method. Các trường hợp có giá trị cao gồm route resolution, codec compatibility, metadata extraction, input/output cardinality, cancellation và connection lifecycle.

Với reactive behavior tập trung, có thể test publisher bằng Reactor `StepVerifier`. Với framework integration, dựng `RSocketMessageHandler` làm responder rồi dùng `RSocketRequester` gọi vào test RSocket server, tốt nhất trên ephemeral port. Test nên đi qua đúng API mà peer thật sử dụng:

```java
StepVerifier.create(
        requester.route("inventory.watch")
                .data(request)
                .retrieveFlux(ItemEvent.class)
                .take(2))
    .expectNextCount(2)
    .verifyComplete();
```

Ít nhất nên có một test cho mỗi interaction model mà application expose. Với request-stream/channel, kiểm tra cancellation hoặc bounded demand khi nó có ý nghĩa. Với setup logic, test cả path `@ConnectMapping` được chấp nhận và bị từ chối. Với metadata, kiểm tra custom MIME type được encode, extract và map đúng thành header.

Connection failure trong test nên deterministic: dispose requester/server rõ ràng, đặt timeout và tránh phụ thuộc external broker/port nếu test không chủ đích là system-level.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="choose-messaging-model">Chọn raw WebSocket, STOMP hay RSocket</a>

<details>
<summary>Xem chi tiết</summary>

Nên chọn messaging model từ contract ứng dụng thật sự cần:

| Nhu cầu | Raw WebSocket | STOMP over WebSocket | RSocket |
| --- | --- | --- | --- |
| Custom text/binary duplex protocol | Rất phù hợp | Thêm một protocol layer | Có thể dùng nhưng nhiều structure hơn |
| Destination, subscription, pub/sub convention | Tự xây | Rất phù hợp | Có route model, không phải STOMP broker semantics |
| External STOMP broker relay | Không | Rất phù hợp | Không có tương đương trực tiếp |
| Request-response / request-stream / bidirectional stream | Tự xây convention | Phải tự quy ước | First-class |
| Reactive Streams demand qua network | Không | Không | Có với request-stream/channel |
| Browser ecosystem đơn giản | Native WebSocket API | STOMP JS client trưởng thành | Tùy RSocket client support |

Raw WebSocket cho mức tự do cao nhất nhưng application phải tự định nghĩa framing, routing, error, subscription và compatibility. STOMP thêm messaging vocabulary chuẩn và phù hợp với broker-oriented pub/sub. RSocket phù hợp với multiplexed requester/responder interaction và reactive streaming.

Không nên chọn chỉ từ latency benchmark. Team familiarity, browser/client library, broker requirement, delivery semantics, observability, network infrastructure và failure recovery thường quyết định chi phí dài hạn nhiều hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="choose-stomp-broker">Chọn simple broker hay broker relay</a>

<details>
<summary>Xem chi tiết</summary>

Simple broker tích hợp của Spring phù hợp khi application cần subscription tracking và broadcast cơ bản ngay trong process. Nó hỗ trợ một phần STOMP behavior và giữ deployment đơn giản vì không cần broker process riêng.

Đổi lại, simple broker không được thiết kế cho clustering và không cung cấp đầy đủ broker feature như STOMP acknowledgement/receipt. Khi có nhiều application instance, nó không thể tự phân phối broadcast từ instance này tới client đang connected ở instance khác.

STOMP broker relay chuyển message tới external STOMP-capable broker như RabbitMQ hoặc ActiveMQ. Cách này thêm operational dependency và broker connection management, nhưng đổi lại có broker feature đầy đủ hơn và hỗ trợ cross-instance broadcasting qua shared broker infrastructure.

Chọn simple broker khi:

- một instance hoặc non-clustered delivery là đủ;
- basic destination subscription đáp ứng yêu cầu;
- operational simplicity quan trọng hơn broker feature.

Chọn broker relay khi:

- application phải scale ngang và chia sẻ broadcast giữa nhiều instance;
- acknowledgement/receipt hoặc full STOMP feature có ý nghĩa;
- broker durability/routing/operation đã là một phần của architecture.

Broker relay chỉ là bridge, không phải công tắc “bật reliability”. End-to-end delivery guarantee vẫn phụ thuộc STOMP command, broker configuration, client acknowledgement, retry và application idempotency.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-integration-handoff">Handoff sang Spring Integration</a>

<details>
<summary>Xem chi tiết</summary>

Spring Integration phù hợp khi bài toán chính chuyển sang composition của message flow thay vì endpoint programming model. Nó xây trên Spring Messaging abstraction và cung cấp gateway, adapter, router, transformer, filter, channel cùng integration-flow lifecycle.

Ví dụ một controller có vài `@MessageMapping` method rồi delegate sang service vẫn nằm gọn trong Spring Messaging. Ngược lại, hệ thống cần bridge RSocket, JMS, Kafka, file và HTTP bằng các bước routing/transformation tái sử dụng là bài toán integration và phù hợp hơn với Spring Integration.

Ranh giới nên rõ:

```text
Spring Messaging
    -> message model, channel, WebSocket/STOMP/RSocket endpoint semantics

Spring Integration
    -> enterprise integration pattern và composed adapter/gateway flow
```

Không nên nhét một integration flow lớn thủ công vào WebSocket/RSocket handler. Ngược lại, cũng không cần đưa Spring Integration vào chỉ để thay cho vài dòng service delegation của endpoint đơn giản.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="messaging-production-checklist">Production readiness checklist</a>

<details>
<summary>Xem chi tiết</summary>

Khi review production readiness, messaging contract và resource limit phải được ghi rõ. Tối thiểu cần xác nhận:

- **Protocol choice:** vì sao raw WebSocket, STOMP hay RSocket phù hợp interaction model.
- **Connection lifecycle:** authentication, authorization, setup/handshake failure, heartbeat/keepalive, disconnect cleanup và reconnect behavior.
- **Capacity:** inbound/outbound executor sizing, bounded queue, rejection behavior và cách cô lập blocking work.
- **Slow consumer:** send-time/buffer limit cho STOMP/WebSocket; demand/cancellation behavior cho RSocket stream.
- **Payload:** message-size limit, codec/MIME compatibility, validation và chiến lược với large object.
- **Broker behavior:** giới hạn của simple broker hoặc availability/reconnect của external broker relay, cùng outage semantics.
- **Delivery semantics:** dữ liệu nào có thể mất, retry, replay, duplicate hoặc cần acknowledgement; idempotency khi retry có thể xảy ra.
- **Observability:** connected session, message rate, queue depth, handler latency, error, disconnect reason, broker health và RSocket connection failure.
- **Testing:** route/destination mapping, metadata/header, serialization, mọi interaction model được dùng, cancellation, outage path và một tập nhỏ end-to-end test.
- **Shutdown:** ngừng nhận work mới, đóng session/requester và giải phóng executor/server mà không leak connection.

Checklist phải tạo ra limit và failure behavior cụ thể chứ không chỉ ghi “monitor trong production”. Load test nên dùng message size, fan-out, connection count và slow consumer gần thực tế vì messaging system thường fail ở resource boundary trước khi happy-path handler code có vấn đề.

### Tài liệu tham khảo

- [Spring Framework — STOMP performance](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/configuration-performance.html)
- [Spring Framework — STOMP testing](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/testing.html)
- [Spring Framework — RSocket](https://docs.spring.io/spring-framework/reference/rsocket.html)

</details>

- [Quay lại đầu trang](#back-to-top)
