<a id="back-to-top"></a>

# RSocket Interaction Model và ranh giới Reactive

## Menu
- [1. Vì sao RSocket tồn tại](#rsocket-purpose)
- [2. Request-response](#rsocket-request-response)
- [3. Fire-and-forget](#rsocket-fire-and-forget)
- [4. Request-stream](#rsocket-request-stream)
- [5. Request-channel](#rsocket-request-channel)
- [6. Tính đối xứng requester và responder](#rsocket-symmetry)
- [7. Setup và connection model](#rsocket-setup-connection)
- [8. Route và metadata](#rsocket-routes-metadata)
- [9. Ranh giới Reactive Streams và backpressure](#rsocket-reactive-streams-boundary)
- [10. Ranh giới TCP, WebSocket và protocol feature](#rsocket-transports-boundary)

## <a id="rsocket-purpose">1. Vì sao RSocket tồn tại</a>

<details>
<summary>Xem chi tiết</summary>

RSocket là một giao thức ứng dụng dành cho giao tiếp bất đồng bộ, song công và có thể ghép nhiều luồng logic trên cùng một kết nối. Thay vì ép mọi trao đổi vào một kiểu request/response, RSocket định nghĩa bốn mô hình tương tác: request-response, fire-and-forget, request-stream và request-channel. Giao thức mang data cùng metadata trong các frame nhị phân và có thể chạy trên transport dạng byte stream như TCP hoặc WebSocket.

RSocket thuộc Spring Messaging vì Spring cung cấp mô hình lập trình phía trên các tương tác đó. Framework ánh xạ Java object và reactive type sang payload, route, metadata và response stream. Với các tương tác dạng stream, RSocket còn truyền tín hiệu demand của Reactive Streams qua ranh giới mạng, nhờ đó bên nhận có thể làm chậm bên phát gần nguồn thay vì chỉ dựa vào hàng đợi hoặc cơ chế congestion control của transport.

RSocket phù hợp khi ứng dụng cần nhiều tương tác đồng thời trên một kết nối dài hạn, cần server chủ động tạo request, hoặc cần streaming một chiều/hai chiều. Nó không phải durable message broker: flow control của RSocket không cung cấp hàng đợi bền vững, replay sau outage hay delivery guarantee do broker quản lý.

Vì vậy câu hỏi thiết kế nên là “Ứng dụng có cần interaction model và flow-control semantics của RSocket hay không?”. Nếu nhu cầu chỉ là trao đổi frame text/binary trong browser hoặc destination/subscription theo STOMP, các mô hình WebSocket/STOMP ở phần trước thường đơn giản hơn.

### Tài liệu tham khảo

- [Spring Framework — RSocket](https://docs.spring.io/spring-framework/reference/rsocket.html)
- [RSocket protocol](https://rsocket.io/about/protocol/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rsocket-request-response">2. Request-response</a>

<details>
<summary>Xem chi tiết</summary>

Request-response là tương tác một input, một output. Requester gửi một request payload và chờ tối đa một response payload, error hoặc cancellation. Hình dạng này quen thuộc như request/response của HTTP, nhưng việc trao đổi vẫn bất đồng bộ và được ghép cùng nhiều RSocket stream khác trên một kết nối.

Trong Spring:

```java
Mono<Status> status = requester
        .route("device.status")
        .data(new StatusRequest("d-42"))
        .retrieveMono(Status.class);
```

mỗi lời gọi không cần một kết nối riêng. Subscription khởi động reactive work; nếu subscriber hủy vì không còn cần kết quả, cancellation có thể kết thúc RSocket stream tương ứng.

Chọn request-response khi contract thật sự chỉ có một kết quả: lookup, command có kết quả, validation hoặc acknowledgement mang thông tin nghiệp vụ. Nếu kết quả vốn là một tập lớn hay dòng dữ liệu theo thời gian, không nên gom tất cả vào một collection chỉ để giữ API một response; request-stream diễn đạt contract rõ hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rsocket-fire-and-forget">3. Fire-and-forget</a>

<details>
<summary>Xem chi tiết</summary>

Fire-and-forget là tương tác một chiều. Requester gửi payload nhưng không nhận application response cho request stream đó. Mô hình này phù hợp với tín hiệu mà bên gửi không cần kết quả nghiệp vụ, chẳng hạn telemetry ít giá trị, hint hoặc notification idempotent mà ứng dụng chấp nhận khả năng mất.

Với `RSocketRequester`, `send()` trả về `Mono<Void>`. Việc `Mono` hoàn tất chỉ cho biết thao tác gửi đã hoàn thành thành công từ góc nhìn requester; nó không phải acknowledgement rằng responder đã chạy xong application logic.

```java
Mono<Void> sent = requester
        .route("telemetry.sample")
        .data(sample)
        .send();
```

Khác biệt này rất quan trọng khi thiết kế reliability. RSocket xem fire-and-forget là best effort. Nếu bên gửi cần xác nhận rõ ở mức application, hãy dùng request-response contract có trả confirmation tương ứng. Các yêu cầu mạnh hơn như durable redelivery, at-least-once processing hoặc effective exactly-once ở mức nghiệp vụ vẫn cần thêm persistence, retry, deduplication/idempotency hoặc một durable messaging system; request-response tự nó không tạo những guarantee đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rsocket-request-stream">4. Request-stream</a>

<details>
<summary>Xem chi tiết</summary>

Request-stream gửi một request rồi nhận từ 0 đến nhiều response payload. Nó phù hợp với kết quả đến dần theo thời gian hoặc vốn có tính incremental, chẳng hạn search result, status update, event feed, hay tập kết quả lớn không nên materialize thành một response duy nhất.

```java
Flux<Reading> readings = requester
        .route("sensor.readings")
        .data(new SensorQuery("s-9"))
        .retrieveFlux(Reading.class);
```

Đặc điểm cốt lõi ở mức protocol là demand. Requester cấp quyền phát response qua các frame flow-control như `REQUEST_N`; responder không được phát vượt quá demand đã được cấp. Nếu requester không còn cần dữ liệu, cancellation kết thúc stream.

Request-stream có thể biểu diễn stream hữu hạn hoặc kéo dài lâu. Ứng dụng vẫn phải tự định nghĩa timeout, reconnect, có replay event đã bỏ lỡ hay không và cách biểu diễn error. Backpressure kiểm soát stream đang sống; nó không tạo persistent history.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rsocket-request-channel">5. Request-channel</a>

<details>
<summary>Xem chi tiết</summary>

Request-channel là interaction dành cho **request input có nhiều giá trị**. Requester mở channel rồi có thể tiếp tục gửi payload, còn responder có thể trả về 0, 1 hoặc nhiều response value. Trường hợp many-to-many phổ biến có mental model gần với hai stream phối hợp hơn là “một request có response rất dài”.

Với annotated responder, Spring ánh xạ multi-value input thành request-channel khi output cardinality là 0, 1 hoặc nhiều. Một requester many-to-many điển hình có thể gửi `Flux<Command>` và nhận `Flux<Result>`:

```java
Flux<Result> results = requester
        .route("session.commands")
        .data(commands, Command.class)
        .retrieveFlux(Result.class);
```

Request-channel phù hợp khi dữ liệu từ requester tiếp tục thay đổi đồng thời với response, ví dụ trạng thái cộng tác thời gian thực, subscription thay đổi theo thời gian hoặc điều khiển thiết bị hai chiều. Cần định nghĩa rõ cancellation và completion: một phía hoàn tất outbound stream không có nghĩa phía kia đã hoàn tất.

Không nên dùng channel chỉ vì transport có thể giao tiếp hai chiều. Nếu requester gửi đúng một request và chỉ responder phát nhiều giá trị, request-stream diễn đạt contract rõ hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rsocket-symmetry">6. Tính đối xứng requester và responder</a>

<details>
<summary>Xem chi tiết</summary>

“Client” và “server” mô tả vai trò khi thiết lập kết nối. Sau setup, RSocket connection mang tính đối xứng: mỗi peer đều có thể khởi tạo request mới, trở thành requester của stream đó, còn peer kia là responder.

Spring thể hiện tính đối xứng này trực tiếp. Một method `@ConnectMapping` hoặc `@MessageMapping` ở phía server có thể nhận `RSocketRequester` đại diện cho peer đang kết nối và dùng nó để gọi ngược về client. Phía client cũng có thể đăng ký annotated responder bằng `RSocketMessageHandler.responder(...)` thông qua connector của requester.

Vì vậy “requester” là vai trò của một interaction, không đồng nghĩa cố định với browser/client; “responder” cũng không đồng nghĩa cố định với server. Cùng một peer có thể là requester ở stream này và responder ở stream khác trong cùng thời điểm.

Hệ quả thực tế là connection ownership và request ownership là hai vấn đề riêng. Nếu ứng dụng bật server-to-client request, route contract, authorization và lifecycle rule phải được thiết kế cho cả hai chiều.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rsocket-setup-connection">7. Setup và connection model</a>

<details>
<summary>Xem chi tiết</summary>

Một RSocket connection bắt đầu bằng setup. Phía chủ động kết nối gửi frame `SETUP` để thiết lập các tham số áp dụng cho cả connection, chẳng hạn data MIME type và metadata MIME type; responder có thể chấp nhận hoặc từ chối connection. `RSocketRequester.Builder` chuẩn bị `RSocketConnector` và setup payload khi kết nối qua TCP hoặc WebSocket.

Setup là lifecycle event của connection, tách biệt với các request stream phía sau. Trong annotated model của Spring, `@ConnectMapping` xử lý setup ban đầu và các metadata-push notification về sau. Ở server, nếu asynchronous setup handling kết thúc bằng error thì connection mới bị từ chối.

```java
@ConnectMapping
Mono<Void> connected(RSocketRequester peer) {
    return registrationService.register(peer);
}
```

Không nên block setup để chờ một request gọi ngược qua chính connection đó, vì normal request chỉ có thể tiến hành sau khi setup hoàn tất. Nếu server cần khởi động outbound request ngay lúc kết nối, hãy tách request đó khỏi completion path của setup như hướng dẫn của Spring.

Keepalive, resumption, interceptor và các protocol option khác là connection policy ở lớp RSocket connector/server. Chúng không nên bị giấu bên trong từng route handler.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rsocket-routes-metadata">8. Route và metadata</a>

<details>
<summary>Xem chi tiết</summary>

RSocket tách data khỏi metadata. Spring thường dùng routing metadata để chọn annotated handler và dùng composite metadata khi một request cần nhiều metadata entry độc lập như route, tracing information hay authentication token.

Data MIME type và metadata MIME type được thỏa thuận cho connection trong setup. Sau đó metadata theo từng request được mang cùng các request frame. Route là application metadata, không phải HTTP URL. Route matcher của Spring mặc định dùng dấu `.` làm separator và RSocket routing không có bước URL decoding như HTTP.

```java
Flux<Item> items = requester
        .route("catalog.find.{category}", "books")
        .metadata(token, authenticationMimeType)
        .data(query)
        .retrieveFlux(Item.class);
```

Ở responder, routing metadata trở thành destination để `@MessageMapping` hoặc `@RSocketExchange` chọn handler; metadata khác chỉ có thể xuất hiện dưới dạng header khi đã có decoder và extractor registration tương ứng.

Nên giữ route ổn định, mang ý nghĩa nghiệp vụ. Không nên nhét protocol concern vào data payload nếu nó phù hợp hơn với metadata, và cũng không nên giả định metadata tùy ý sẽ tự được Spring hiểu nếu chưa cấu hình codec/extractor.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rsocket-reactive-streams-boundary">9. Ranh giới Reactive Streams và backpressure</a>

<details>
<summary>Xem chi tiết</summary>

Ranh giới reactive của RSocket sâu hơn việc “Java API trả về `Flux`”. Với request-stream và request-channel, demand được biểu diễn trên wire bằng flow control của RSocket. Khi downstream subscriber yêu cầu ít item hơn, số payload peer từ xa được phép phát cũng có thể giảm theo.

Spring giữ mô hình này bằng cách thích nghi reactive type thông qua `ReactiveAdapterRegistry` và sử dụng tự nhiên `Mono`/`Flux` trong requester và annotated responder. Nhờ vậy stream có thể giữ tính demand-aware xuyên qua serialization và network boundary.

Backpressure không biến arbitrary application code thành non-blocking. Nếu handler thực hiện I/O blocking trên event-loop thread, nó vẫn có thể làm nghẽn các công việc khác. Tương tự, một hàng đợi không giới hạn giữa network và consumer sẽ phá lợi ích về memory mà demand control mang lại.

Khi thiết kế:

- giữ pipeline streaming theo đúng demand;
- chuyển blocking work không thể tránh sang scheduler/executor phù hợp;
- đặt giới hạn cho application buffer và định nghĩa overflow policy;
- truyền cancellation ngược lên upstream nếu công việc đắt đỏ nên dừng.

Flow control kiểm soát producer của một stream đang sống. Nó không thay thế rate limit, durable queue hay admission control cho nhiều request độc lập.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rsocket-transports-boundary">10. Ranh giới TCP, WebSocket và protocol feature</a>

<details>
<summary>Xem chi tiết</summary>

RSocket là application protocol; TCP và WebSocket là transport dùng để mang RSocket frame. TCP thường phù hợp cho service-to-service. WebSocket hữu ích khi browser, proxy hoặc network policy ưu tiên đường kết nối qua HTTP Upgrade. Chọn transport nào cũng không làm thay đổi bốn RSocket interaction model.

Protocol còn định nghĩa nhiều cơ chế ngoài bốn model cơ bản, gồm keepalive, fragmentation/reassembly, metadata push, leasing và resumption. Một số là core mechanics, một số là capability tùy chọn. Spring cho phép cấu hình sâu hơn qua `RSocketRequester.Builder.rsocketConnector(...)`, nơi ứng dụng có thể thiết lập keepalive, resumption, interceptor và các tùy chọn của RSocket Java.

Khi chẩn đoán sự cố, nên tách rõ các lớp:

```text
application contract
    -> Spring route / codec / responder
    -> RSocket frame và flow-control semantics
    -> TCP hoặc WebSocket transport
    -> network
```

WebSocket disconnect là failure ở transport/connection; route-not-found hay application error thuộc lớp phía trên. Tương tự, dùng WebSocket transport không tự tạo STOMP semantics, và chạy RSocket over WebSocket cũng không biến RSocket thành STOMP.

### Tài liệu tham khảo

- [Spring Framework — RSocket overview và requester](https://docs.spring.io/spring-framework/reference/rsocket.html)
- [RSocket protocol — frame và flow-control semantics](https://rsocket.io/about/protocol/)

</details>

- [Quay lại đầu trang](#back-to-top)
