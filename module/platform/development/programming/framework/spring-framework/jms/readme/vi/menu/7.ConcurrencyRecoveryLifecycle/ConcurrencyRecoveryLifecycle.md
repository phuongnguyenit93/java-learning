<a id="back-to-top"></a>

# Concurrency, Recovery và vòng đời tài nguyên

## Menu
- [Khoảng concurrency của Listener](#jms-listener-concurrency)
- [Trade-off giữa Parallelism và Message Ordering](#jms-ordering-tradeoffs)
- [Selector, Durable và Shared Subscription trong cấu hình Listener](#jms-selectors-subscriptions)
- [Vòng đời Listener, Startup/Shutdown, Recovery, BackOff và xử lý ngoại lệ](#jms-recovery-backoff)
- [SingleConnectionFactory và CachingConnectionFactory](#jms-connection-factory-wrappers)
- [Mức cache của DefaultMessageListenerContainer và transaction bên ngoài](#jms-dmlc-cache-levels)

## <a id="jms-listener-concurrency">Khoảng concurrency của Listener</a>

<details>
<summary>Xem chi tiết</summary>

Concurrency quyết định có bao nhiêu consumer task của listener container được phép chạy song song. Với `DefaultMessageListenerContainer`, `concurrentConsumers` là số consumer tối thiểu luôn được giữ hoạt động, còn `maxConcurrentConsumers` là giới hạn trên mà container có thể scale tới khi tải tăng.

Spring hỗ trợ dạng cấu hình ngắn gọn dùng ở factory và `@JmsListener`:

```text
"5-10" → tối thiểu 5, tối đa 10
"10"   → tối thiểu 1, tối đa 10
```

```java
@Bean
DefaultJmsListenerContainerFactory orderFactory(ConnectionFactory cf) {
    var factory = new DefaultJmsListenerContainerFactory();
    factory.setConnectionFactory(cf);
    factory.setSessionTransacted(true);
    factory.setConcurrency("3-12");
    return factory;
}

@JmsListener(
        destination = "orders.in",
        containerFactory = "orderFactory",
        concurrency = "5-20")
void handle(OrderPlaced order) {
}
```

Giá trị ở annotation override concurrency từ factory cho endpoint đó. Container bên dưới vẫn quyết định những capability concurrency nào nó hỗ trợ; DMLC hỗ trợ mô hình min/max với dynamic scaling.

Tăng consumer có thể tăng throughput cho queue khi processing độc lập và provider còn message để phân phối. Nó không bảo đảm scale tuyến tính: database contention, provider prefetch, downstream rate limit, chi phí transaction, CPU hoặc giới hạn connection/session có thể trở thành bottleneck thực sự.

Nên xem minimum là lượng resource ổn định cần duy trì, còn maximum là mức burst parallelism cho phép. Một maximum quá lớn có thể chỉ đẩy overload xuống hệ thống phía sau. Trước khi tăng khoảng concurrency, cần quan sát active consumer, processing latency, queue depth, error/redelivery rate và downstream saturation.

Với queue có lưu lượng thấp, hướng dẫn DMLC của Spring khuyến nghị giữ một consumer nếu chưa có lý do throughput rõ ràng để tăng thêm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-ordering-tradeoffs">Trade-off giữa Parallelism và Message Ordering</a>

<details>
<summary>Xem chi tiết</summary>

Xử lý song song làm thay đổi bảo đảm về thứ tự. Một JMS consumer đơn lẻ tuân theo quy tắc thứ tự mà provider áp dụng cho consumer đó. Khi nhiều consumer xử lý song song, thứ tự hoàn tất công việc nghiệp vụ có thể khác thứ tự broker phân phối message.

```text
message A → consumer 1 → xử lý chậm ─────────┐
message B → consumer 2 → xử lý nhanh → commit│
message C → consumer 3 → xử lý nhanh → commit│
                                             ↓
thứ tự hoàn tất nghiệp vụ: B, C, A
```

Vì vậy chỉ tăng DMLC concurrency sau khi xác định rõ ordering của nghiệp vụ là global, theo key, hay không quan trọng. Nếu mọi update của cùng một aggregate phải tuần tự, kiến trúc thường cần route aggregate đó vào cùng một ordered lane ở broker/application level, thay vì kỳ vọng listener concurrency cao vẫn giữ completion order.

Queue là trường hợp tự nhiên cho competing consumer: một message được provider giao cho một consumer theo JMS/provider semantics. Topic cần cẩn trọng hơn. Các consumer/subscription độc lập có thể mỗi bên nhận một bản publication, nên chỉ tăng số topic consumer có thể tạo duplicate processing trong cùng application node. JMS shared subscription tồn tại để nhiều consumer cùng chia sẻ một subscription; semantics và provider constraint của nó phải được cấu hình có chủ đích.

Vì vậy dynamic scaling của DMLC dễ áp dụng nhất cho queue. Với topic, nên bắt đầu từ một consumer cho mỗi subscription trừ khi thiết kế shared subscription hoặc topology khác thực sự yêu cầu parallel consumers.

**Đánh đổi:** concurrency tăng throughput và có thể giảm backlog, nhưng tăng đồng thời resource usage và làm race condition, duplicate effect, out-of-order completion lộ rõ hơn. Phần triển khai listener phải an toàn khi được gọi song song; tránh mutable shared field nếu không có cơ chế đồng bộ có chủ đích.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-selectors-subscriptions">Selector, Durable và Shared Subscription trong cấu hình Listener</a>

<details>
<summary>Xem chi tiết</summary>

Selector và subscription quyết định *message nào* consumer đủ điều kiện nhận và *subscription identity nào* một topic consumer đang đại diện. Spring đưa các thiết lập này lên listener endpoint/container, còn protocol semantics thuộc Jakarta Messaging và provider.

JMS selector được provider đánh giá trước khi delivery. Nó phù hợp cho filtering dựa trên metadata mà broker hỗ trợ, không phải để thay arbitrary application logic.

```java
@JmsListener(
        destination = "orders.events",
        selector = "region = 'EU' AND priority >= 5")
void handlePriorityEuOrder(OrderPlaced event) {
}
```

Với topic, durable subscription duy trì subscription qua những giai đoạn consumer mất kết nối theo quy tắc JMS/provider. Spring listener factory cung cấp `subscriptionDurable`, còn `@JmsListener.subscription` cung cấp subscription name. Trong Spring Framework 6.1, nếu annotated durable listener không khai báo explicit subscription name, framework có thể tạo default name từ fully qualified listener method identity.

Shared subscription cho phép nhiều consumer tham gia cùng một subscription thay vì mỗi consumer tạo một stream độc lập. Vì vậy `subscriptionShared` khác về bản chất với việc chỉ tăng số lượng topic consumer độc lập.

```java
factory.setPubSubDomain(true);
factory.setSubscriptionDurable(true);
factory.setSubscriptionShared(true);
factory.setConcurrency("3");
```

Subscription identity phải đủ ổn định cho durable/shared semantics mong muốn. Thay đổi client identity, subscription name, selector hoặc destination có thể tạo hậu quả nhìn thấy ở provider; nên xem các thay đổi đó như messaging-contract change và test với provider thật.

**Điểm cần tránh:** selector làm giảm số message tới application nhưng có thể tăng broker-side filtering work. Một hệ thống có rất nhiều selector quá chuyên biệt đôi khi khó vận hành hơn việc route tới các destination rõ ràng. Đây là quyết định kiến trúc chứ không phải chi tiết Spring JMS có thể che giấu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-recovery-backoff">Vòng đời Listener, Startup/Shutdown, Recovery, BackOff và xử lý ngoại lệ</a>

<details>
<summary>Xem chi tiết</summary>

Một listener trong production có hai nhóm lỗi: xử lý message của ứng dụng có thể thất bại, và chính hạ tầng listener có thể mất hoặc không tạo được JMS resource. DMLC xử lý cả hai nhưng qua các hook khác nhau.

Lifecycle được tích hợp với container model của Spring. DMLC tham gia `SmartLifecycle`, nên có thể auto-start cùng application context, stop khi shutdown và tuân theo phase ordering. `JmsListenerEndpointRegistry` truyền lifecycle operation xuống các container được tạo từ annotation endpoint.

Việc thiết lập/phục hồi hạ tầng tuân theo retry policy. Mặc định DMLC dùng recovery interval 5000 ms. Nếu cấu hình một `BackOff`, policy này thay thế khoảng thời gian cố định; khi `BackOffExecution` trả `STOP`, container ngừng các lần recovery tiếp theo.

```java
var factory = new DefaultJmsListenerContainerFactory();
factory.setConnectionFactory(connectionFactory);
factory.setBackOff(new ExponentialBackOff(1_000L, 2.0));
factory.setErrorHandler(t ->
        log.error("JMS listener invocation failed", t));
```

`ErrorHandler` là hook tổng quát cho lỗi chưa được bắt khi container xử lý message; nó không chỉ dành cho exception nghiệp vụ. Khi lỗi là JMS `JMSException`, container còn có thể thông báo cho JMS `ExceptionListener` đã cấu hình, và lỗi đó vẫn có thể đi qua `ErrorHandler`/đường logging của container. Dù các hook có thể chồng lấp, vẫn phải tách nhóm lỗi: poison message ném exception nghiệp vụ khác với connection hỏng hoặc thiết lập consumer thất bại, và recovery hạ tầng do chính sách recovery của container điều khiển chứ không chỉ bởi listener error hook.

`start()`/`stop()` kiểm soát container có đang chủ động consume hay không; chúng không phải retry API cho từng message bị lỗi. Redelivery sau listener rollback phụ thuộc transaction/acknowledgement và provider policy. Container recovery dùng để khôi phục hạ tầng sau lỗi thiết lập/connection.

Khi shutdown, nên để lifecycle contract cho container thời gian dừng có kiểm soát thay vì kết thúc JVM đột ngột. Việc dừng bất ngờ có thể làm tăng redelivery vì provider chưa quan sát được acknowledgement/commit cuối cùng.

Về vận hành, tránh retry nóng vô hạn khi broker đang không khả dụng. Dùng `BackOff` có ý nghĩa, đưa các lỗi recovery lặp lại vào monitoring và quyết định trường hợp nào `STOP` cần người vận hành can thiệp.

### Tài liệu tham khảo

- Spring Framework 6.1 API — `DefaultMessageListenerContainer`
- Spring Framework 6.1 API — `AbstractMessageListenerContainer`

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-connection-factory-wrappers">SingleConnectionFactory và CachingConnectionFactory</a>

<details>
<summary>Xem chi tiết</summary>

JMS connection và session có thể đắt đỏ, nên Spring cung cấp các connection-factory wrapper để reuse resource. Chúng giải quyết bài toán khác với caching nội bộ của DMLC cho listener resource.

`SingleConnectionFactory` cung cấp một JMS `Connection` dùng chung và trả application handle mà lệnh `close()` thông thường không đóng ngay target connection đó. Nó hữu ích trong môi trường standalone khi tạo connection tốn chi phí và một shared connection phù hợp. Thuộc tính `reconnectOnException` cho phép tạo lại target connection sau lỗi provider.

`CachingConnectionFactory` mở rộng mô hình đó. Nó bật reconnect-on-exception mặc định và thêm cache cho JMS `Session`, đồng thời mặc định cache producer và consumer gắn với các session này.

```java
@Bean
CachingConnectionFactory cachingConnectionFactory(ConnectionFactory providerFactory) {
    var caching = new CachingConnectionFactory(providerFactory);
    caching.setSessionCacheSize(10);
    caching.setCacheProducers(true);
    caching.setCacheConsumers(false);
    return caching;
}
```

`sessionCacheSize` là giới hạn **cho từng acknowledgement/session type**, không phải một global count duy nhất. Giá trị mặc định là 1. Bên gọi vẫn phải logical-close session để wrapper có thể đưa session về cache.

Consumer caching có hệ quả về ngữ nghĩa: cached `MessageConsumer` có thể sống lâu hơn phạm vi method nhìn thấy từ code, và durable subscription có quy tắc close/re-registration riêng. Producer/consumer cho temporary queue/topic không được cache.

Với `JmsTemplate` và local `JmsTransactionManager`, reuse connection/session có thể giảm đáng kể chi phí thiết lập. Tuy nhiên với DMLC, Spring thường ưu tiên caching do chính listener container quản lý vì DMLC phối hợp resource đó với lifecycle, concurrency và transaction model. Chồng thêm `CachingConnectionFactory` dưới DMLC khi không có lý do rõ ràng có thể làm stop/restart và dynamic scaling khó hiểu hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-dmlc-cache-levels">Mức cache của DefaultMessageListenerContainer và transaction bên ngoài</a>

<details>
<summary>Xem chi tiết</summary>

DMLC có thể cache resource ở bốn mức khái niệm: không cache, connection, session hoặc consumer. Cache sâu hơn giảm chi phí thiết lập lặp lại, nhưng transactional resource được cache vẫn phải hợp lệ với transaction model đang dùng.

Giá trị mặc định quan trọng trong Spring Framework là:

```text
không có external transaction manager
    → CACHE_CONSUMER

có external transaction manager
    → CACHE_NONE
```

Khi không có external transaction manager, DMLC có thể giữ local JMS resource và mặc định cache tới consumer level. Khi có external transaction manager, Spring mặc định lấy resource mới để môi trường Jakarta EE/JTA có cơ hội enlist đúng connection/session trong từng transaction.

```java
var container = new DefaultMessageListenerContainer();
container.setConnectionFactory(xaAwareConnectionFactory);
container.setTransactionManager(jtaTransactionManager);
// Khi có external TM, mức cache mặc định trở thành CACHE_NONE.
```

Một số server/provider có thể enlist chính xác JMS resource đã cache. Trong môi trường như vậy, tăng rõ ràng cache level lên `CACHE_CONNECTION` hoặc `CACHE_SESSION` có thể giảm overhead. Đây là tối ưu hóa tích hợp provider/container và phải được kiểm chứng với môi trường XA thật; không nên sao chép setting từ provider khác.

Không nhầm DMLC cache level với `CachingConnectionFactory`. DMLC caching nằm trong receive lifecycle của listener container. `CachingConnectionFactory` là wrapper bên ngoài cache resource cho bên gọi nói chung. Kết hợp cả hai lớp có thể giữ consumer/resource theo cách cản trở DMLC stop/restart hoặc dynamic scaling.

Trình tự quyết định nên là:

```text
chọn transaction model
    ↓
hiểu quy tắc enlistment của provider/server
    ↓
chọn DMLC cache level
    ↓
đo chi phí thiết lập và hành vi recovery
```

Chỉ tối ưu caching sau khi tính đúng đắn của transaction đã được bảo đảm. Consumer nhanh hơn nhưng resource không được enlist vào transaction mong muốn vẫn là lỗi correctness, không phải cải thiện hiệu năng.

</details>

- [Quay lại đầu trang](#back-to-top)
