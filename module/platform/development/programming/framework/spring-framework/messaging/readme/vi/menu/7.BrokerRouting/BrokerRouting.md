<a id="back-to-top"></a>

# Broker Routing và User Destination

## Menu
- [1. Built-in simple broker](#simple-broker)
- [2. Capability và giới hạn của simple broker](#simple-broker-limits)
- [3. External STOMP broker relay](#broker-relay)
- [4. System và client broker connection](#broker-relay-connections)
- [5. Quy ước đặt tên destination](#destination-conventions)
- [6. User destination](#user-destinations)
- [7. Nhiều session cho một user](#multi-session-users)
- [8. Unresolved user destination trong clustered application](#unresolved-user-destinations)
- [9. Ranh giới với broker-native behavior](#broker-native-boundary)

## <a id="simple-broker">1. Built-in simple broker</a>

<details>
<summary>Xem chi tiết</summary>

Built-in simple broker là lựa chọn broker nhỏ nhất trong STOMP stack của Spring. Nó chạy ngay trong process ứng dụng, giữ subscription trong bộ nhớ và chuyển tiếp message có destination khớp với broker prefix đã cấu hình. Vì vậy nó phù hợp để học message flow, phát triển local và các ứng dụng single-node vừa phải khi external broker chưa cần thiết.

```java
@Override
public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.setApplicationDestinationPrefixes("/app");
    registry.enableSimpleBroker("/topic", "/queue");
}
```

Message dưới `/app` đi về application handler như `@MessageMapping`. Message dưới `/topic` hoặc `/queue` được `SimpleBrokerMessageHandler` xử lý; component này giữ `SubscriptionRegistry` và phát message phù hợp tới các session đã subscribe thông qua `clientOutboundChannel`.

Mental model quan trọng là **subscription routing trong cùng process**, không phải một messaging server bền vững. State thuộc instance hiện tại và mất khi instance dừng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="simple-broker-limits">2. Capability và giới hạn của simple broker</a>

<details>
<summary>Xem chi tiết</summary>

Simple broker cố ý chỉ triển khai một phần hành vi STOMP. Nó hỗ trợ routing theo subscription và có thể hỗ trợ heartbeat khi được cấu hình `TaskScheduler`, nhưng không phải full STOMP broker. Spring Reference chỉ rõ simple broker không hỗ trợ STOMP acknowledgement hoặc receipt và không phù hợp cho clustering.

Boundary này rất quan trọng. Không nên mô tả simple broker như thể nó cung cấp durable queue, delivery guarantee, transaction, dead-lettering hoặc subscription state dùng chung giữa nhiều node. Với simple broker, `/topic` và `/queue` chỉ là convention routing; chúng không tự tạo broker entity bền vững.

Hãy dùng simple broker khi yêu cầu thực tế là "ghi nhớ active subscription trên process này và fan-out message". Chuyển sang full broker khi correctness hoặc scale phụ thuộc broker-owned state hay shared routing giữa nhiều application instance.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="broker-relay">3. External STOMP broker relay</a>

<details>
<summary>Xem chi tiết</summary>

STOMP broker relay cho phép giữ programming model của Spring ở phía ứng dụng nhưng giao trách nhiệm broker cho một STOMP-capable broker thật. Thay vì `enableSimpleBroker(...)`, cấu hình dùng `enableStompBrokerRelay(...)`. Spring chuyển broker-directed STOMP message sang broker ngoài và đưa response từ broker trở lại WebSocket client.

`StompBrokerRelayMessageHandler` vì vậy là cầu nối, không phải embedded broker. Đây thường là lựa chọn khi ứng dụng cần feature hoặc khả năng scale vượt quá simple broker.

```java
@Override
public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.setApplicationDestinationPrefixes("/app");
    registry.enableStompBrokerRelay("/topic", "/queue")
            .setRelayHost("broker.internal")
            .setRelayPort(61613);
}
```

Destination durability, permission, queue lifecycle và vendor-specific routing vẫn do broker được chọn định nghĩa.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="broker-relay-connections">4. System và client broker connection</a>

<details>
<summary>Xem chi tiết</summary>

Broker relay có hai vai trò connection rất dễ nhầm. Với mỗi STOMP client session, Spring mở một TCP connection độc lập tới external broker và dùng connection đó cho frame gắn với client tương ứng. Song song, relay duy trì một **system connection** cho message phát sinh từ server-side application component.

Điều này giải thích nhiều hành vi runtime: server-originated broadcast dùng system connection; client traffic dùng per-client broker connection; số broker connection có thể tăng theo active STOMP session; và capacity planning phải tính cả broker/network chứ không chỉ executor pool của Spring.

Khi broker gửi message ngược về per-client connection, relay gắn message với Spring session tương ứng trước khi đẩy xuống `clientOutboundChannel`. Broker nằm ngoài process ứng dụng nhưng Spring vẫn giữ session context cần để route frame về đúng client.

Recovery của hai loại connection không đối xứng. Khi mất kết nối tới broker, relay tự tiếp tục thử thiết lập lại shared **system connection**. Các per-client broker connection không được tự động reconnect, vì vậy WebSocket/STOMP client bị ảnh hưởng phải tự reconnect và thiết lập session mới. Do đó broker availability cho server-originated messaging không đồng nghĩa mọi client relay connection đã được phục hồi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="destination-conventions">5. Quy ước đặt tên destination</a>

<details>
<summary>Xem chi tiết</summary>

Destination trước hết là routing key. Spring dùng prefix để quyết định component nào xử lý message, nhưng chuỗi destination không tự mang queue/topic semantics phổ quát.

```text
/app/**   -> application handler
/topic/** -> convention cho broker-routed broadcast
/queue/** -> convention cho point-to-point style
/user/**  -> logical per-user destination
```

Với built-in simple broker, `/topic` và `/queue` chỉ là convention. Với external broker relay, cú pháp và semantics destination còn phụ thuộc STOMP implementation của broker.

Nên chọn prefix làm rõ routing intent và tách application destination khỏi broker destination. Không nên nhúng authorization policy vào naming convention; routing name và security policy giải quyết hai bài toán khác nhau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="user-destinations">6. User destination</a>

<details>
<summary>Xem chi tiết</summary>

User destination cho phép application code gửi tới một logical user mà không cần biết concrete session-specific destination của connection. Client có thể subscribe vào logical destination như `/user/queue/updates`, và Spring resolve nó thành destination đủ riêng biệt để không đụng với user hoặc session khác.

Ở phía gửi, `SimpMessagingTemplate.convertAndSendToUser(...)` và `@SendToUser` dùng cùng mô hình resolve. Ứng dụng chỉ diễn đạt "gửi tới user X", còn Spring ánh xạ yêu cầu đó tới một hoặc nhiều active session destination của X.

Đây là routing feature, không phải authentication system. User identity thường đến từ `Principal` của WebSocket session, thường được kế thừa từ HTTP handshake đã xác thực. Cách identity được tạo và quyền truy cập của nó thuộc Spring Security.

Cơ chế user destination không bắt buộc phải có authenticated user. Một WebSocket session chưa xác thực vẫn có thể subscribe user destination; trong trường hợp đó `@SendToUser` hành xử như `broadcast = false` và chỉ nhắm tới chính session đã gửi message.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="multi-session-users">7. Nhiều session cho một user</a>

<details>
<summary>Xem chi tiết</summary>

Một authenticated user có thể đồng thời có nhiều WebSocket session: nhiều tab trình duyệt, nhiều thiết bị hoặc reconnect đều có thể cùng hoạt động. Spring vì vậy xem "user" và "session" là hai identity liên quan nhưng không đồng nhất.

Mặc định, message gửi tới một user có thể được phân phối tới tất cả active session của user đó. Khi handler response chỉ nên quay lại session đã kích hoạt xử lý, `@SendToUser(broadcast = false)` thu hẹp delivery về session đó.

Đây là quyết định thiết kế: "user cần thấy notification ở mọi nơi" khác với "chỉ interaction hiện tại cần reply". Application state không nên giả định mỗi account chỉ có một WebSocket session, và disconnect handling cần chịu được session churn bình thường.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="unresolved-user-destinations">8. Unresolved user destination trong clustered application</a>

<details>
<summary>Xem chi tiết</summary>

Trong deployment nhiều instance, node đang gửi tới user X có thể không giữ bất kỳ session nào của X. Ở node đó user destination trở thành unresolved dù session mục tiêu có thể nằm trên node khác.

Spring có thể broadcast unresolved user-destination message để application server khác có cơ hội resolve. Message broker configuration cung cấp `userDestinationBroadcast` cho mục đích này, còn user-registry broadcast giúp các node trao đổi thông tin user đang kết nối.

Cơ chế này giải quyết bài toán khám phá routing, không thay thế distributed user-state management nói chung. Các broadcast destination phải được chia sẻ qua external broker và cấu hình nhất quán. Cleanup per-user destination là broker-specific và nên theo hướng dẫn của broker.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="broker-native-boundary">9. Ranh giới với broker-native behavior</a>

<details>
<summary>Xem chi tiết</summary>

Spring STOMP support dừng có chủ đích ở ranh giới tích hợp broker. Khi correctness phụ thuộc durable queue, acknowledgement, transaction, dead-letter policy, TTL, broker-side selector, persistence, federation hoặc vendor-specific routing, tài liệu của broker mới là nguồn có thẩm quyền.

```text
Spring Framework
  handler + channel + STOMP adaptation + relay
        |
        v
External broker
  durable routing + broker entity + broker protocol semantics + operations
```

Không nên biến broker-native guarantee thành Spring Framework guarantee chỉ vì ứng dụng dùng `enableStompBrokerRelay`. Spring vận chuyển frame tới và từ broker; broker mới định nghĩa ý nghĩa vận hành của chúng.

</details>

- [Quay lại đầu trang](#back-to-top)
