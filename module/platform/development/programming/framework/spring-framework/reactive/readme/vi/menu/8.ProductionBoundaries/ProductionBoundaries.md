<a id="back-to-top"></a>

# Production boundary, trade-off và thiết kế end-to-end

## Menu
- [Blocking call và thread starvation](#webflux-blocking-thread-starvation)
- [Unbounded buffering và scheduler misuse](#webflux-buffering-scheduler-pitfalls)
- [Backpressure và cancellation tại boundary WebFlux](#webflux-backpressure-cancellation)
- [Connection lifetime và streaming response](#webflux-connection-lifetime)
- [Resource limit và áp lực production](#webflux-resource-limits)
- [Chọn Spring MVC hay WebFlux](#webflux-mvc-decision)
- [Mức phù hợp của dependency stack](#webflux-dependency-stack-fit)
- [Độ phức tạp với team và vận hành](#webflux-team-complexity)
- [Handoff sang các module lân cận](#webflux-neighboring-module-handoffs)
- [Luồng WebFlux end-to-end](#webflux-end-to-end-flow)

## <a id="webflux-blocking-thread-starvation">Blocking call và thread starvation</a>

<details>
<summary>Xem chi tiết</summary>

WebFlux được thiết kế để một nhóm runtime thread tương đối nhỏ có thể điều phối nhiều thao tác I/O cùng lúc. Mô hình này chỉ hiệu quả khi các thread đó không bị giữ bởi blocking work. Một lời gọi blocking chiếm thread trong khi thread không thể tiếp tục xử lý signal hay network event khác.

Vì vậy lỗi không chỉ là “một request chậm”. Nếu event-loop thread bị chặn bởi JDBC, synchronous SDK, filesystem access hoặc lock, nhiều connection được phân cho cùng loop có thể tăng latency. Khi đủ nhiều loop bị giữ, server có thể trông như bị đứng dù CPU chưa cao.

~~~java
@GetMapping("/profile/{id}")
Mono<Profile> profile(@PathVariable String id) {
    Profile value = blockingClient.load(id); // block trước khi Mono tồn tại
    return Mono.just(value);
}
~~~

Bọc kết quả bằng Mono không di chuyển lời gọi blocking sang thread khác. Spring Framework 6.1 có thể invoke một số **annotated controller method** trên executor được cấu hình, nhưng đó là công cụ interoperability cho ranh giới hữu hạn chứ không phải cách hợp thức hóa blocking dependency ở mọi nơi trong pipeline.

Khi vận hành, nên nhìn thread dump, request latency, hành vi event loop, downstream latency và pool saturation cùng nhau. Nếu ứng dụng chủ yếu dựa trên dependency blocking, Spring MVC thường cho execution model đơn giản và dễ dự đoán hơn việc liên tục offload khỏi WebFlux event loop.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-buffering-scheduler-pitfalls">Unbounded buffering và scheduler misuse</a>

<details>
<summary>Xem chi tiết</summary>

Một reactive pipeline có thể không block thread nhưng vẫn sụp dưới tải nếu tích lũy dữ liệu không có giới hạn. Aggregate request/response lớn, collect một Flux không kết thúc vào List, hoặc queue công việc nhanh hơn downstream có thể xử lý đều biến concurrency thành áp lực bộ nhớ.

Các giới hạn codec như maxInMemorySize là hàng rào bảo vệ cho thao tác cần gom dữ liệu. Tăng limit có thể đúng khi payload contract thực sự lớn nhưng vẫn có giới hạn; cứ tăng limit để chấp nhận payload không kiểm soát chỉ dời điểm lỗi sang chỗ khác.

Scheduler/executor switching cũng dễ bị dùng sai. Chuyển execution sang nơi khác chỉ thay đổi **công việc chạy ở đâu**; nó không làm giảm lượng công việc, không biến queue vô hạn thành hữu hạn và không làm blocking dependency trở thành non-blocking. Mỗi ranh giới offload cần lý do, năng lực rõ và cách hệ thống phản ứng khi năng lực đã đầy.

Quy tắc WebFlux cần giữ là: giữ nguyên streaming khi protocol có tính streaming, đặt giới hạn cho aggregation khi cần whole value, và làm blocking interoperability thật rõ. Chi tiết operator/scheduler của Reactor thuộc module reactive programming.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-backpressure-cancellation">Backpressure và cancellation tại boundary WebFlux</a>

<details>
<summary>Xem chi tiết</summary>

Ở ranh giới WebFlux, Reactive Streams demand giúp nối tốc độ application tiêu thụ/tạo dữ liệu với asynchronous HTTP read/write. Thực tế, WebFlux không cần đọc hoặc tạo vô hạn phần tử chỉ vì connection còn mở.

Backpressure không có nghĩa mọi tầng network hoặc downstream đều có demand semantics giống nhau. Buffer vẫn tồn tại trong codec, connector, TCP stack, proxy và application operator. Mục tiêu là giữ các buffer có giới hạn và để demand lan truyền xa nhất có thể qua các component hỗ trợ nó.

Cancellation quan trọng tương tự. Browser có thể đóng connection, timeout có thể cancel request, hoặc caller có thể ngừng consume WebClient response. Cancellation có thể lan ngược publisher chain để upstream work có cơ hội dừng.

Application code không nên tách side effect tốn kém khỏi request rồi mặc định cho nó chạy tiếp sau khi HTTP exchange đã biến mất, trừ khi đó là công việc độc lập có chủ đích. Semantics cancel của database/transaction phụ thuộc integration riêng và thuộc data-access cùng transaction-management.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-connection-lifetime">Connection lifetime và streaming response</a>

<details>
<summary>Xem chi tiết</summary>

Request thông thường chỉ giữ connection cho đến khi một response hữu hạn được ghi xong. Streaming làm lifecycle dài hơn: Server-Sent Events, newline-delimited stream hoặc response dài có thể giữ connection hoạt động nhiều phút hay nhiều giờ.

Long-lived connection cần mô hình vận hành rõ ràng: server/proxy idle timeout, client cancellation, cách keep-alive hoạt động, số stream đồng thời, tốc độ downstream sản xuất dữ liệu và cách hệ thống xử lý deploy/shutdown. Non-blocking giúp giảm nhu cầu thread-per-connection, nhưng mỗi connection vẫn tiêu thụ socket, buffer, bookkeeping và năng lực của ứng dụng.

Response commitment cũng làm error handling khác đi. Khi status/header và một phần body đã gửi, lỗi xảy ra sau đó thường không thể được thay bằng một JSON error response mới. Kết quả quan sát được có thể chỉ là stream bị ngắt hoặc connection đóng.

Client cần coi stream termination là một phần của protocol. Reconnect, replay, resume token hoặc semantics at-most-once là quyết định ở cấp ứng dụng; framework không thể tự tái tạo phần stream đã gửi dở.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-resource-limits">Resource limit và áp lực production</a>

<details>
<summary>Xem chi tiết</summary>

Non-blocking I/O giúp một process điều phối nhiều thao tác đồng thời hiệu quả hơn, nhưng năng lực vẫn hữu hạn. Áp lực production xuất hiện ở nhiều giới hạn riêng:

- số server connection và file descriptor của OS;
- event-loop và tài nguyên connector;
- outbound connection pool và pending acquisition queue;
- codec aggregation limit và DataBuffer memory;
- application queue, concurrency limit và object bị giữ lâu;
- năng lực của downstream service, database và external API.

Dependency bão hòa sớm nhất thường quyết định hành vi của toàn hệ thống. Nếu WebFlux server nhận nhiều công việc đồng thời hơn rất nhiều so với downstream pool có thể xử lý, queue chỉ bị chuyển vào memory và timeout tăng lên.

Nên đặt giới hạn tại ranh giới có năng lực thực, sau đó quan sát rejection, timeout, cancellation, memory và latency dưới tải gần production. Connection limit, body limit và timeout là một phần của runtime contract, không phải cấu hình khẩn cấp chỉ chỉnh khi hệ thống đã quá tải.

Cũng cần phân biệt năng lực khi hệ thống khỏe với năng lực khi có lỗi. Downstream bình thường có thể xử lý concurrency hiện tại, nhưng khi nó chậm đi, request giữ connection/buffer lâu hơn và làm những giới hạn vốn hợp lý bị cạn nhanh.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-mvc-decision">Chọn Spring MVC hay WebFlux</a>

<details>
<summary>Xem chi tiết</summary>

Spring MVC và Spring WebFlux đều là production web stack của Spring. Nên chọn dựa trên đặc điểm tải và dependency stack thay vì xem một stack là phiên bản “mới hơn” hoặc mặc định nhanh hơn.

WebFlux phù hợp khi application có nhiều asynchronous/non-blocking I/O, cần streaming, phải điều phối số lượng lớn thao tác I/O chậm đồng thời, hoặc compose tự nhiên với reactive dependency. Giá trị chính là concurrency efficiency và composition; nó không làm một remote call đơn lẻ tự nhiên có latency thấp hơn.

Spring MVC thường đơn giản hơn khi phần lớn dependency là blocking, request flow mang tính imperative và mức concurrency mục tiêu phù hợp Servlet model. Java virtual thread còn làm thay đổi đặc điểm chi phí của blocking MVC, vì vậy quyết định kiến trúc nên dựa vào yêu cầu và phép đo thực tế.

Không nên chọn WebFlux chỉ vì một library trả Mono hoặc vì application dùng WebClient. Ngược lại, một ranh giới blocking nhỏ có kiểm soát cũng chưa đủ để loại WebFlux. Hãy đánh giá đường dữ liệu chủ đạo, nhu cầu streaming, đặc điểm concurrency, kinh nghiệm vận hành và khả năng tương thích của dependency quan trọng.

Nếu cả hai stack đều đáp ứng yêu cầu, sự nhất quán và khả năng hiểu của team là tiêu chí hợp lệ. Execution model đơn giản hơn mà vẫn đạt mục tiêu năng lực thường dễ vận hành hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-dependency-stack-fit">Mức phù hợp của dependency stack</a>

<details>
<summary>Xem chi tiết</summary>

WebFlux đem lại lợi ích kiến trúc rõ nhất khi đường I/O quan trọng tương thích với asynchronous, non-blocking composition. Một dependency blocking có thể cô lập; một stack mà đa số dependency đều blocking sẽ làm thay đổi toàn bộ trade-off.

Hãy kiểm tra data path end-to-end:

~~~text
HTTP request
  -> WebFlux handler
  -> application service
  -> persistence / cache / remote HTTP / SDK
  -> response
~~~

Outbound HTTP có thể giữ reactive path bằng WebClient. Reactive data-access infrastructure cũng có thể làm vậy với driver phù hợp, nhưng repository/query/transaction semantics thuộc data-access, Spring Data và transaction-management tương ứng. JDBC/JPA về bản chất là blocking API dù kết quả bị bọc trong publisher.

Khi bắt buộc dùng blocking library, hãy quyết định liệu boundary đó có đủ nhỏ và hữu hạn để offload lên executor phù hợp hay không. Queue size, concurrency, timeout và cách hệ thống phản ứng khi bão hòa phải là một phần của quyết định. Nếu blocking call chiếm phần lớn normal request path, MVC thường rõ và rẻ hơn về vận hành.

Compatibility còn gồm hành vi ẩn: DNS resolution, template rendering, serialization extension, callback của thư viện thứ ba hoặc logging appender đều có thể block hay allocate lớn. Cần verify dependency quan trọng thay vì suy luận từ việc API “trông reactive”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-team-complexity">Độ phức tạp với team và vận hành</a>

<details>
<summary>Xem chi tiết</summary>

Ứng dụng reactive web mang thêm các khái niệm vận hành mà team cần debug được: event-loop starvation, publisher cancellation, contextual logging, long-lived connection, asynchronous stack trace, connector pool và thời điểm response committed.

Độ phức tạp đó đáng giá khi nó giải quyết tải thực tế. Nó là chi phí không cần thiết nếu application chỉ là CRUD blocking nhỏ và mục tiêu throughput vốn đã dễ đạt.

Team nên làm execution model quan sát được trong cả code review lẫn monitoring. Bằng chứng hữu ích gồm request/downstream latency, connection-pool saturation, hành vi event loop, memory khi streaming, timeout/cancellation rate và correlation id vẫn đi đúng qua reactive execution.

Load test nên mô phỏng downstream chậm và client cancellation, không chỉ happy path phản hồi nhanh. WebFlux có thể chạy rất đẹp khi mọi dependency trả ngay nhưng degrade mạnh khi partial outage làm connection lifetime tăng.

Cũng cần quy ước chung cho error mapping, context propagation, ranh giới blocking có giới hạn, WebClient configuration và streaming endpoint. Nếu mỗi người tự dựng một reactive style khác nhau, chi phí debug sẽ lớn hơn lợi ích concurrency.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-neighboring-module-handoffs">Handoff sang các module lân cận</a>

<details>
<summary>Xem chi tiết</summary>

Module này sở hữu WebFlux runtime của Spring Framework, server programming model, cross-cutting web pipeline và các cơ chế chuyên sâu của WebClient. Một số chủ đề được dừng lại có chủ đích tại ranh giới của module lân cận:

- **Reactive programming** sở hữu Reactive Streams theory, Reactor operator, scheduler và cơ chế backpressure tổng quát.
- **Validation và data binding** sở hữu Validator, DataBinder, ConversionService và Formatter mà WebFlux dùng ở ranh giới HTTP.
- **Data access** sở hữu cơ chế truy cập JDBC/R2DBC của Spring Framework; reactive repository semantics thuộc các Spring Data module tương ứng.
- **Transaction management** sở hữu Spring transaction policy và reactive transaction semantics.
- **Testing** sở hữu WebTestClient và Framework test infrastructure.
- **Messaging** sở hữu Spring WebSocket client/server cùng handler/session lifecycle, WebSocket/STOMP application messaging và Spring RSocket programming.
- **Spring Security** sở hữu authentication, authorization, SecurityContext, CSRF và security policy dù tích hợp vào WebFlux filter chain.
- **Spring Web** sở hữu Servlet-stack MVC, là mốc so sánh kiến trúc chính.

Các handoff này là ranh giới thiết kế, không phải WebFlux bị thiếu tính năng. Chương này chỉ cần giúp nhận ra từng chủ đề gắn vào đâu trong HTTP flow; mô hình khái niệm đầy đủ của chủ đề đó nên học ở module sở hữu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-end-to-end-flow">Luồng WebFlux end-to-end</a>

<details>
<summary>Xem chi tiết</summary>

Một request đi theo cấu hình DispatcherHandler thông thường có thể được nhìn như một luồng liên tục:

~~~text
reactive HTTP server
  -> HttpHandler adapter
  -> WebFilter chain / WebExceptionHandler boundary
  -> DispatcherHandler
  -> HandlerMapping
  -> HandlerAdapter
  -> annotated controller hoặc functional HandlerFunction
  -> application service
  -> optional WebClient / non-blocking I/O khác
  -> HandlerResult / ServerResponse
  -> HandlerResultHandler + codec hoặc view rendering
  -> ghi response và completion
~~~

ServerWebExchange mang trạng thái request/response qua lớp server-web; Reactor Context có thể mang dữ liệu ngữ cảnh xuyên reactive composition. Lỗi hoặc cancellation có thể đi ngược chain; filter và exception handler phía ngoài quan sát chúng theo ordering và theo việc response đã committed hay chưa.

Functional endpoint có thêm một đường triển khai: RouterFunction có thể được adapt trực tiếp thành HttpHandler cùng HandlerStrategies. Đường mức thấp này không đi qua DispatcherHandler/RouterFunctionMapping để discover route, dù vẫn dùng functional request/response strategy được cấu hình cho adapter.

Nếu Spring 6.1 blocking controller execution được bật, annotated controller method được chọn có thể được gọi trên executor cấu hình. Đây chỉ là một ranh giới thực thi cụ thể trong luồng; downstream reactive work vẫn tuân theo execution semantics của publisher chain.

Mục tiêu production là quyền sở hữu rõ ràng xuyên suốt: mỗi giai đoạn biết mình đang route, decode, invoke business logic, gọi outbound I/O, map error hay ghi response. Khi xuất hiện vấn đề latency hoặc tài nguyên, lần theo luồng này giúp tìm đúng tầng có bằng chứng thay vì xem “WebFlux” như một khối đen.

### Tài liệu tham khảo

- Spring Framework 6.1.14 API — DispatcherHandler
- Spring Framework 6.1.14 API — org.springframework.web.server
- Spring Framework 6.1.14 API — WebClient

</details>

- [Quay lại đầu trang](#back-to-top)
