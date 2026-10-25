<a id="back-to-top"></a>

# Streaming WebFlux

## Menu
- [SSE với Flux và ServerSentEvent](#webflux-streaming-001)
- [JSON theo luồng, NDJSON và lựa chọn media type](#webflux-streaming-002)
- [DataBuffer, bộ nhớ đệm dùng pool và trách nhiệm giải phóng](#webflux-streaming-003)
- [Giới hạn kích thước dữ liệu, tốc độ tiêu thụ và tránh gom toàn bộ stream](#webflux-streaming-004)
- [Cô lập tác vụ chặn với scheduler phù hợp và giới hạn đồng thời](#webflux-streaming-005)
- [Hủy đăng ký, client ngắt kết nối và dọn dẹp tài nguyên](#webflux-streaming-006)
- [Truyền ngữ cảnh qua Reactor Context thay cho giả định về ThreadLocal](#webflux-streaming-007)
- [WebSocketHandler reactive và vòng đời luồng nhận/gửi](#webflux-streaming-008)

## <a id="webflux-streaming-001">SSE với Flux và ServerSentEvent</a>

<details>
<summary>Xem chi tiết</summary>

# SSE với Flux và ServerSentEvent trong Spring WebFlux

## 1. Tình huống và mục tiêu

Một dashboard muốn hiện thông báo tức thời khi đơn hàng thay đổi; một trang quản trị muốn hiển thị log, tiến độ job, giá chứng khoán hoặc trạng thái tác vụ mà không phải gửi `GET` lặp lại mỗi giây. **Server-Sent Events (SSE)** là cơ chế HTTP cho phép server **đẩy một dòng sự kiện một chiều** đến client thông qua kết nối response mở lâu. Trong Spring WebFlux, bạn có thể trả **`Flux<T>`** với `produces = text/event-stream`, hoặc trả **`Flux<ServerSentEvent<T>>`** để kiểm soát `id`, `event`, `retry`, `comment` và `data`.

Bài học này đi từ giao thức SSE đến vận hành thực tế với **Java 17+, Spring Framework 6.x và Spring Boot 3.x**. Học xong bạn sẽ:

1. Đọc đúng `text/event-stream` gồm `data:`, `id:`, `event:`, `retry:`, dòng chú thích `:` và dòng trống kết thúc event.
2. Giải thích `Flux<ServerSentEvent<T>>` và cơ chế **non-blocking HTTP response streaming** trong Spring WebFlux; phân biệt với `Mono<T>`, JSON response thông thường và MVC `SseEmitter`.
3. Tạo controller SSE hữu hạn/dài hạn, đặt event ID, named events, event data, retry delay và heartbeat.
4. Kết nối từ browser bằng **`EventSource`**, xử lý named events, `onerror`, `readyState`, tự động reconnect và `close()`.
5. Hiểu **`Last-Event-ID`**, replay checkpoint, ordering, at-least-once/duplicate/out-of-order caveats; biết SSE **không tự có event log bền vững**.
6. Phân biệt `Flux.interval` per-client cold publisher với hot shared source (`Sinks.Many`), và quản lý backpressure, slow consumers, memory, cancellation.
7. Cấu hình reverse proxy, timeout, keep-alive, CORS/cookies, authorization, content security và giới hạn kết nối.
8. Chạy một ứng dụng Spring Boot WebFlux có endpoint `/api/events/history` và `/api/events/ticks`, trang web EventSource và kiểm thử HTTP với `WebTestClient`.
9. Biết khi nào nên chọn SSE, WebSocket, polling hoặc message broker.

**Phạm vi:** WebFlux reactive stack (không cần `spring-boot-starter-web`). Ví dụ dùng EventSource của browser và endpoint GET; không hứa delivery chính xác một lần hoặc durable replay nếu không có backing event store. Các đoạn gắn nhãn “minh họa” cần phụ thuộc bên ngoài trước khi dùng, còn nhóm file ở mục 11 là ví dụ thực hành độc lập khi đặt vào dự án Maven Spring Boot 3.x có dependencies đã liệt kê.

## 2. SSE là gì? Vì sao vẫn là HTTP?

SSE sử dụng một **HTTP GET** thông thường, với response:

~~~http
GET /api/events/history HTTP/1.1
Host: localhost:8080
Accept: text/event-stream
~~~

Server trả:

~~~http
HTTP/1.1 200 OK
Content-Type: text/event-stream;charset=UTF-8
Cache-Control: no-cache

id: 1
event: news
retry: 2000
data: news-1

id: 2
event: news
retry: 2000
data: news-2

~~~

Sau headers là các dòng UTF-8. **Một dòng trống** phân cách hai events. Server có thể tiếp tục ghi event tiếp theo mà không kết thúc response. Khác với polling, client không phải tạo request mới cho từng update; khác với WebSocket, client và server không trao đổi các message hai chiều trên cùng một kênh SSE.

SSE không có một “TCP protocol mới”: nó chạy trên HTTP, dùng những proxy/firewall/observability HTTP thông thường nhưng vẫn chịu ảnh hưởng của HTTP response buffering, idle timeout và connection limits.

### SSE một chiều

| Nhu cầu | SSE | WebSocket | Polling |
| --- | --- | --- | --- |
| Server liên tục đẩy cập nhật | Rất phù hợp | Phù hợp | Có thể nhưng tốn requests |
| Client gửi thông điệp realtime cùng kết nối | Không | Có | Tạo HTTP request riêng |
| Browser API đơn giản | `EventSource` | `WebSocket` | `fetch`/XHR |
| Reconnect mặc định trên browser | Có, với quy tắc EventSource | App tự thiết kế | App tự lập lịch |
| Chuẩn hóa event ID/retry | Có các field SSE | App tự định nghĩa | App tự định nghĩa |
| Mỗi luồng tương ứng HTTP GET | Có | Handshake/connection riêng | Có nhiều GET |
| Đảm bảo durable replay | Không tự có | Không tự có | Phụ thuộc server |
| Truyền binary trực tiếp theo protocol | Không (UTF-8 text) | Có binary frames | HTTP hỗ trợ binary |

Nếu ứng dụng cần gửi cursor/input/actions lên server, vẫn có thể **POST qua HTTP endpoint khác** trong khi EventSource nhận notifications qua GET. Nếu client phải gửi nhiều messages realtime liên tục và server phản hồi tương ứng, hãy cân nhắc WebSocket.

## 3. Định dạng wire-level `text/event-stream`

Theo tiêu chuẩn SSE và tài liệu MDN, stream phải được encode UTF-8. Mỗi event gồm những **dòng có cấu trúc**, kết thúc bởi **một dòng trống**. Các trường quan trọng:

| Field | Vai trò | Lưu ý |
| --- | --- | --- |
| `data: ...` | Nội dung event | Nhiều dòng data được nối bằng ký tự newline |
| `event: news` | Loại/named event | Browser dùng `addEventListener("news", handler)` |
| `id: 42` | Event ID | Browser ghi nhớ để có thể gửi `Last-Event-ID` khi reconnect |
| `retry: 3000` | Gợi ý thời gian reconnect **mili giây** | Chỉ số nguyên hợp lệ; không phải thời gian timeout của server |
| `: comment` | Chú thích | Không dispatch message, phù hợp heartbeat |
| Dòng trống | Kết thúc event | Nếu thiếu, client có thể chưa dispatch event |

Một event không có `event:` được xử lý thành **`message` event**; browser gán handler qua `onmessage` hoặc `addEventListener("message", ...)`. Nếu có `event: news`, nó trở thành named event **`news`**, và **không** tự gọi `onmessage` cho cùng event đó.

### 3.1. Một event có nhiều dòng data

~~~text
event: notice
id: 17
data: line one
data: line two

~~~

Browser nhận `event.data` tương đương:

~~~text
line one
line two
~~~

Dòng cuối cùng trống chỉ để kết thúc event; không nằm trong nội dung. Việc chứa các dấu xuống dòng trong data do encoder/server phải serialize đúng, không tự nối raw user input vào wire format.

### 3.2. `id` và `retry`

~~~text
id: 18
retry: 5000
data: pending

~~~

Browser có thể nhớ ID **18** và dùng reconnect time **5000 ms**. Tuy nhiên `retry` không phải heartbeat interval, cũng không phải guarantee server sẽ phát đúng ID 19. Event IDs phải được thiết kế cùng storage/replay semantics.

`id:` rỗng có ngữ nghĩa **xóa last-event-id** trong chuẩn EventSource. Không chủ ý phát ID rỗng khi bạn cần cơ chế khôi phục cursor.

### 3.3. Comment-only heartbeat

~~~text
: keep-alive

~~~

Dòng bắt đầu bằng `:` là comment và không tạo message thông thường, nhưng giúp **gửi bytes định kỳ** để phát hiện disconnect hoặc tránh một số idle timeouts. Heartbeat phải thực sự được flush qua server/proxy; nếu nginx/gateway đệm toàn response rồi mới gửi thì client vẫn không nhận.

### 3.4. Đừng tự nối dữ liệu không tin cậy thành event frame

Sai:

~~~java
String raw = "data: " + untrustedInput + "\n\n";
~~~

Nếu input chứa newline và `event:`, `id:` giả, bạn có thể làm thay đổi SSE framing. Dùng `ServerSentEvent.builder().data(...)` hoặc codec được cấu hình đúng để serialize. Khi nội dung là JSON, dùng DTO/JSON encoder và đảm bảo client render an toàn.

## 4. Spring WebFlux phát SSE bằng Flux

Một controller minh họa:

~~~java
@RestController
class DemoSseController {

    @GetMapping(path="/updates",
            produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<String> updates() {
        return Flux.interval(Duration.ofSeconds(1))
                .map(index -> "update-" + index);
    }
}
~~~

Đây là class **minh họa** cần imports và Boot WebFlux project. WebFlux subscribe vào Publisher, encode mỗi string thành SSE `data:` và gửi dần thay vì chờ Flux complete. Dùng `produces=text/event-stream` rất quan trọng khi không sử dụng wrapper `ServerSentEvent`.

Với wrapper chuyên dụng:

~~~java
@GetMapping(path="/events",
        produces=MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> events() {
    return Flux.interval(Duration.ofSeconds(1))
            .map(i -> ServerSentEvent.<String>builder()
                    .id(Long.toString(i))
                    .event("tick")
                    .retry(Duration.ofSeconds(3))
                    .data("tick-" + i)
                    .build());
}
~~~

Snippet trên là **method minh họa trong WebFlux controller**, không phải project độc lập. `ServerSentEvent` nằm ở **`org.springframework.http.codec.ServerSentEvent`**; sử dụng fluent builder: `id`, `event`, `retry(Duration)`, `comment`, `data`, `build`.

Nếu client browser dùng `source.onmessage = ...` mà server phát `event("tick")`, handler `onmessage` sẽ **không** bắt named event; phải `source.addEventListener("tick", ...)`.

### 4.1. `Flux` hữu hạn và vô hạn

- `Flux.range(1, 5)` có 5 items và hoàn tất; phù hợp demo/history/test.
- `Flux.interval(Duration.ofSeconds(1))` là chuỗi tick không có end theo mặc định, tiếp tục cho đến khi cancel/disconnect hoặc server stop.
- `Flux.interval(...).take(3)` phát đúng ba tick, rồi complete; phù hợp test deterministic về số items.
- Khi server kết thúc stream hữu hạn, browser `EventSource` thường **tự reconnect**, vì kết thúc kết nối có thể được hiểu là sự cố; với stream hữu hạn, client nên **`close()`** khi đã nhận đủ phần tử.
- Một Flux vô hạn không tự đồng nghĩa server có durable event log; khi client mất mạng, các events giữa hai lần kết nối có thể bị mất.

### 4.2. Không gọi `block()` hay `subscribe()` trong handler

Sai:

~~~java
@GetMapping("/wrong")
String wrong() {
    return remoteService.load().block();
}
~~~

Trên Reactor Netty event-loop, `.block()` có thể ném `IllegalStateException` hoặc gây blocking làm giảm throughput. `subscribe()` riêng trong controller khiến framework không quản lý được lifecycle/cancellation của task. Đúng là **return `Flux`/ `Mono` đã compose** để WebFlux subscribe.

Nếu source là thư viện blocking, có thể cần `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())`; đó là cơ chế **offload**, không biến JDBC/file SDK blocking thành API non-blocking. Giới hạn tasks/queue/downstream connection pool vẫn bắt buộc.

## 5. EventSource của browser

Trong browser:

~~~javascript
const source = new EventSource("/api/events/history");

source.addEventListener("news", (event) => {
  console.log("ID:", event.lastEventId);
  console.log("Data:", event.data);
});

source.onerror = (event) => {
  console.warn("SSE disconnected or failed", source.readyState);
};

// Gọi khi không còn cần theo dõi:
source.close();
~~~

Điểm cần hiểu:

- `EventSource(url)` mở HTTP **GET**. JavaScript **không** tự chọn POST, `Content-Type` request body hoặc binary frames.
- Browser có thể tự reconnect khi kết nối hỏng hoặc server đóng, theo thuật toán EventSource.
- `event.lastEventId` cho biết last event ID liên quan event được dispatch.
- `source.readyState`: `0=CONNECTING`, `1=OPEN`, `2=CLOSED`. `onerror` không luôn có nghĩa stream chấm dứt vĩnh viễn.
- `close()` đóng kết nối và chặn reconnect. Nên dùng trong component unmount, tab/page navigation hoặc khi nhận event terminal.
- EventSource tiêu chuẩn **không cho tùy ý thêm custom HTTP headers** vào request từ constructor, ví dụ `Authorization: Bearer ...`. Với bearer-token auth, có thể cần một phương án khác (same-origin cookie với CSRF/CORS policy, `fetch` streaming parser, hoặc ticket ngắn hạn) và thiết kế bảo mật phù hợp.
- Có thể dùng `new EventSource(url, { withCredentials: true })` trong những trường hợp CORS credentialed requests được phép và cookies đáp ứng SameSite/Secure. Không dùng `Access-Control-Allow-Origin: *` cùng credentials.

### 5.1. Named events khác `onmessage`

Server:

~~~text
event: progress
data: 25

event: progress
data: 50

event: done
data: finished

~~~

Browser:

~~~javascript
source.addEventListener("progress", (e) => {
  console.log("Progress:", e.data);
});
source.addEventListener("done", () => source.close());
~~~

Không đợi `onmessage` nhận event `progress`. Nếu muốn một handler chung, bạn phải subscribe đến những names phù hợp hoặc phát mặc định không có trường `event:`.

### 5.2. Render event data an toàn

Không làm:

~~~javascript
result.innerHTML = event.data;
~~~

Nếu server phản ánh user input trong event.data mà không sanitize, HTML injection có thể thành XSS. Với text messages:

~~~javascript
result.textContent = event.data;
~~~

Với JSON, parse bằng `JSON.parse(e.data)`, validate schema/type/limits rồi render từng giá trị qua `textContent` hoặc framework escaping. EventSource không thay chính sách CSP, authentication, authorization hoặc output encoding theo ngữ cảnh.

## 6. Last-Event-ID, resume và ngữ nghĩa delivery

Khi server gửi:

~~~text
id: 102
event: news
data: updated

~~~

Browser theo thuật toán EventSource có thể nhớ `102`. Nếu kết nối bị ngắt, request mới thường có:

~~~http
GET /api/events/history HTTP/1.1
Accept: text/event-stream
Last-Event-ID: 102
~~~

**Nhưng giao thức chỉ cung cấp cursor**, không tự lưu events 103, 104, 105 tại server. Muốn resume đúng, server phải có **nguồn replay đáng tin** và hiểu ID:
- Append-only database/event log (có retention).
- Kafka partition/topic theo key và offset tương ứng với consumer contract.
- Redis Streams có stream IDs và retention chính sách.
- Event store với global/per-user sequence numbers và khả năng truy vấn `WHERE id > lastId`.

Nếu server chỉ dùng `Flux.interval` tạo dữ liệu mới theo từng subscriber, ID `0,1,2` của connection A **không có nghĩa** connection B có thể lấy lại phần thiếu từ A. Không dùng timer indexes làm global durable event IDs.

### 6.1. Replay phải xử lý duplicates và gaps

Giả sử client nhận và hiển thị event #42 nhưng chưa kịp persist việc xử lý trước khi ngắt. Khi reconnect, server có thể gửi lại #42 hoặc #43 tùy thời điểm ID được browser cập nhật/đồng bộ. Điều này đòi hỏi:
- Event IDs ổn định và có ý nghĩa trong domain/stream scope.
- Client xử lý **idempotent**, hoặc bỏ qua ID đã thấy theo chính sách.
- Server xác định rõ **exclusive resume** (thường `id > Last-Event-ID`) hay inclusive semantics.
- Xử lý **cursor quá cũ** (đã vượt retention): yêu cầu full resync/snapshot hoặc báo lỗi API có chủ đích.
- **Phân quyền khi replay**: ID của tenant A không được khiến tenant B đọc event A.
- Không hứa “exactly-once” nếu chưa có transaction/idempotency/state acknowledgement thực.
- Khi nhiều partitions/users, ordering chỉ có thể bảo đảm trong một key/partition/stream mà app định nghĩa, không mặc định global.

### 6.2. Ví dụ logic DB, không phải controller runnable

~~~java
Flux<Notification> replayAfter(String principalId, long lastSeenId) {
    return notificationRepository
            .findAuthorizedAfter(principalId, lastSeenId);
}
~~~

Đây là đoạn mô hình nghiệp vụ, phụ thuộc reactive repository thật. Query phải lọc authorization/tenant, order theo sequence đáng tin và giới hạn batch/read timeout. Nếu cần **kết hợp replay với live stream**, phải tránh race “sau khi query xong nhưng trước khi subscribe live” bằng cursor + atomic subscribe/snapshot hoặc thiết kế event log như nguồn duy nhất. `Flux.concat(replay, live)` đơn giản có thể bỏ lỡ sự kiện trong khe nối nếu không có cơ chế đồng bộ.

## 7. Heartbeat, flush và phát hiện disconnect

SSE là kết nối thường mở lâu. Các tầng có thể đóng connection nếu lâu không có dữ liệu:
- Load balancer idle timeout.
- CDN/reverse proxy response timeout.
- NAT hoặc intermediaries.
- Browser/mobile network mất kết nối.
- Container/server write error khi client đã rời.

Theo Spring WebFlux reference, khi streaming nên phát dữ liệu định kỳ để **phát hiện client đã disconnect sớm hơn**. Heartbeat SSE dưới dạng comment:

~~~java
Flux<ServerSentEvent<String>> heartbeats =
        Flux.interval(Duration.ofSeconds(15))
                .map(i -> ServerSentEvent.<String>builder()
                        .comment("keep-alive")
                        .build());
~~~

Đoạn trên là snippet dùng Reactor, không phải controller độc lập. Khi merge với notifications, nên dùng một publisher có heartbeat và một nguồn notifications:

~~~java
Flux<ServerSentEvent<String>> stream =
        Flux.merge(notifications, heartbeats);
~~~

Giả sử `notifications` là publisher thực được khai báo. Không dùng `heartbeats` như một cơ chế lặp event nghiệp vụ: comment-only event không được dispatch như `message` trong browser. Heartbeat là giao thức kiểm tra kết nối; khi producer lâu không có event, gửi comment nhỏ sẽ hữu ích hơn giữ im lặng hàng phút.

**Lưu ý cấu hình thực tế:** đặt heartbeat 15–30 giây chỉ là ví dụ; cần đảm bảo nhỏ hơn idle timeout thực tế ở toàn đường HTTP, đủ phù hợp bandwidth/connection costs. Nếu proxy buffer dữ liệu, heartbeat phải được forward/flush, không chỉ được tạo trong Reactor pipeline.

## 8. Cold Flux và hot publisher: một kết nối hay nhiều subscribers?

### 8.1. `Flux.interval` thường có chuỗi riêng mỗi subscription

~~~java
Flux<Long> ticks = Flux.interval(Duration.ofSeconds(1));
~~~

Khi mỗi client subscribe, `Flux.interval` tạo timer sequence theo subscription; nhiều clients có thể thấy `0,1,2,...` riêng. Điều này tốt cho demo/timers per-client nhưng không thể biểu diễn shared business updates với một global monotonic ID.

Nếu có 10.000 EventSource clients, 10.000 per-client intervals/subscriptions vẫn có chi phí scheduling và connection management, dù không giữ 10.000 blocking Servlet threads. Kiểm soát global connection limits và broadcast architecture.

### 8.2. `Sinks.Many` cho hot source

Một mẫu minh họa trong Spring service:

~~~java
@Service
class NewsHub {

    private final Sinks.Many<String> sink =
            Sinks.many().multicast().directBestEffort();

    public Flux<String> events() {
        return sink.asFlux();
    }

    public boolean publish(String message) {
        Sinks.EmitResult result = sink.tryEmitNext(message);
        return result.isSuccess();
    }
}
~~~

Đây là **class minh họa**, cần imports `@Service`, `Sinks`, `Flux` và producer thực. `directBestEffort` không cache history và có thể **bỏ qua subscriber chậm** để subscribers khác còn nhận. `tryEmitNext` có thể trả `FAIL_ZERO_SUBSCRIBER`, `FAIL_OVERFLOW`, `FAIL_NON_SERIALIZED` hoặc trạng thái khác; không gán `publish=true` là đã ghi bền vững.

Nếu nhiều producers phát vào cùng sink đồng thời, cơ chế `Sinks` kiểm tra concurrent emission và có thể từ chối non-serialized signals. Hãy serialize producers hoặc xử lý `EmitResult` có metric/retry bounded theo policy, không spin vô hạn trên event loop.

### 8.3. Những loại sink khác nhau

| Sinks.Many | Ý nghĩa | Trade-off |
| --- | --- | --- |
| `multicast().directBestEffort()` | Best effort, subscriber chậm có thể mất item | Không dành cho durable/reliable delivery |
| `multicast().directAllOrNothing()` | Nếu một subscriber không có demand, event có thể bị bỏ cho tất cả | Một slow consumer ảnh hưởng cả nhóm |
| `multicast().onBackpressureBuffer()` | Có buffer và demand coordination | Cần chọn bounded size, overflow policy, tránh slow subscriber làm nghẽn |
| `replay().limit(n)` | Replay được một số gần nhất cho late subscribers | Chỉ cache in-memory, reset khi restart, chưa durable |
| Event log DB/Kafka/Redis Streams | Truy vấn durable history theo cursor | Thêm storage/retention/authorization/ordering complexity |

Một **sink là source phát events**, không phải cơ sở dữ liệu. Hệ thống nhiều pods không tự thấy cùng events chỉ vì mỗi pod tạo sink singleton; cần broker/stream distribution hoặc sticky routing hợp lệ.

## 9. Backpressure và slow clients trong SSE

Reactive Streams có backpressure: downstream yêu cầu `request(n)`, upstream theo khả năng sẽ sản xuất có kiểm soát. Nhưng **HTTP SSE/EventSource không có per-event business acknowledgement**. Khi browser đọc chậm hoặc TCP buffers đầy, backpressure có thể xuất hiện tại HTTP output/writer, còn event log upstream hoặc producer bên ngoài vẫn có thể phát liên tục.

Các vấn đề cần đánh giá:

- Mỗi SSE client là một connection lâu dài, có network/file-descriptor/memory overhead.
- Client dùng mobile network hoặc tab bị background throttle có thể đọc chậm.
- Nếu queue per-client không giới hạn, memory sẽ tăng theo số client × lag.
- Nếu drop events, downstream có thể bị **gaps theo id**; client phải detect/resync.
- Nếu terminate slow client, browser tự reconnect tạo **reconnect storm** nếu không có backoff/rate control.
- Nếu một shared sink buffer chịu áp lực bởi subscriber chậm, **tất cả** subscribers có thể bị ảnh hưởng.
- Nếu producer publish các events chưa persist nhưng SSE connection đứt, các events có thể mất khi không có durable store.

### Operator patterns

~~~java
Flux<ServerSentEvent<String>> result =
        source.onBackpressureLatest()
              .map(v -> ServerSentEvent.<String>builder(v).build());
~~~

Đây là snippet có `source` là publisher thực. `onBackpressureLatest` có thể bỏ intermediate values, chỉ phù hợp khi **chỉ cần state mới nhất** như nhiệt độ/giá hiển thị, **không** phù hợp với giao dịch thanh toán hoặc các events không được mất. Không chọn drop/overflow policy chỉ vì nó giúp dashboard “mượt”.

Nếu cần mọi event có thể replay: dùng durable log và cursor, phát có giới hạn khi client có demand. Nếu không thể đáp ứng, chủ động đóng stream hoặc gửi signal cần resync, không giữ queue vô hạn.

## 10. Security, cookies, CORS và reverse proxy

### 10.1. Authorization cho HTTP GET đang mở lâu

SSE thường là GET, nhưng URL có thể lộ dữ liệu nhạy cảm. Quy tắc:
1. Xác thực và phân quyền **trước** subscribe vào nguồn events.
2. Với replay, kiểm tra quyền **theo từng event/tenant** hoặc enforce query đúng scope, không chỉ check `Last-Event-ID`.
3. Không để event IDs chứa secret/token hay PII nếu không cần; `id` có thể xuất hiện trong logs/headers.
4. Nếu quyền bị thu hồi trong khi kết nối vẫn mở, thiết kế re-evaluation/forced disconnect và revoke subscription.
5. Không dùng một singleton `Sinks.Many<UserMessage>` rồi broadcast mọi tin của mọi user cho toàn bộ listeners mà không filter quyền.
6. Tránh đưa bearer token vào URL query string vì URLs dễ bị log/lộ qua analytics/referrer/history. EventSource không hỗ trợ custom auth headers chuẩn; chọn cookie/session hoặc design thay thế cẩn trọng.

### 10.2. CORS và credentialed EventSource

~~~javascript
const source = new EventSource(
  "https://api.example.com/api/events/ticks",
  { withCredentials: true }
);
~~~

Đây là **ví dụ URL minh họa**, không hoạt động với local demo nếu chưa cấu hình CORS/HTTPS. Backend phải cho phép cụ thể `Origin`, credentials và cookie policy hợp lệ. Không thiết lập `Access-Control-Allow-Origin: *` khi request dùng credentials; giải quyết `SameSite`, `Secure`, HTTPS và CSRF policy theo threat model.

SSE chỉ là server → browser channel. Nếu trang dùng POST riêng để tạo công việc, POST có cookie auth cần CSRF protection như mọi unsafe request thông thường.

### 10.3. Proxy buffering và idle timeout

Một vài reverse proxies/CDNs có thể đệm HTTP response. Dù server đã emit event, browser không nhận gì cho đến khi buffer đầy hoặc connection đóng. Cần xác minh và cấu hình riêng từng tầng:
- Content-Type đúng **`text/event-stream; charset=UTF-8`**.
- Disable response buffering cho SSE route ở proxy nếu cần (ví dụ một số cấu hình nginx có `proxy_buffering off`; header `X-Accel-Buffering: no` chỉ có tác dụng ở các tầng thực sự hỗ trợ).
- Idle timeout **lớn hơn heartbeat interval**; kiểm tra ingress, LB, CDN, app server và mobile carriers.
- Streaming flush thực sự tới client; không áp compression/buffering theo cách gộp nhiều events gây delay.
- `Cache-Control: no-cache` hoặc policy phù hợp để tránh cache/intermediary dùng lại một response stream không đúng. Đây không phải `no-store` và không giải quyết auth/caching riêng.
- Connection limits/HTTP/2 multiplexing: HTTP/1.1 browser thường giới hạn số kết nối song song trên cùng origin, nhiều tab EventSource có thể cạnh tranh connection slots; HTTP/2 có concurrent streams negotiated khác. Không coi HTTP/2 là vô hạn streams.
- Observability: đo bytes/second, concurrent SSE connections, drop/reconnect rate, response status, disconnect cause.

Không giả định `Flux<ServerSentEvent>` tự sửa cấu hình gateway hoặc giúp proxy stream events nếu tầng proxy bật buffering.

## 11. Ví dụ Spring Boot 3.x + Java 17 chạy được

Để học protocol/replay mà không phụ thuộc DB/message broker, ứng dụng thực hành dùng **danh sách sự kiện 1..5 cố định**. Controller cung cấp:

- `GET /api/events/history`: `Flux<ServerSentEvent<String>>` hữu hạn. Với `Last-Event-ID`, trả các events có ID lớn hơn ID đã nhận. Đây **chỉ là in-memory lesson dataset**; nếu deployment restart nhưng data thay đổi, code này không còn chứng minh durable replay.
- `GET /api/events/ticks`: `Flux<ServerSentEvent<String>>` chạy 1 giây/lần, phát event tên `tick`. Đây là timer **per subscription** và không hứa global sequence/replay.
- `GET /` phục vụ file HTML tĩnh với `EventSource` kết nối tới history/ticks.
- `WebTestClient` integration tests trên server HTTP thật, assert named event ID/data, Last-Event-ID và validation.
- Không dùng security, Redis, Kafka, reverse proxy hay DB, vì mục đích là hiểu SSE wire/lifecycle một cách xác định được.

### 11.1. Tạo project

Spring Initializr: Maven, Java 17+, Spring Boot **3.x**, dependency `Spring Reactive Web` (artifact `spring-boot-starter-webflux`), và `Spring Boot Starter Test`.

Snippet trong `pom.xml` được Spring Initializr sinh ra:

~~~xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
~~~

Đây là **đoạn dependencies**, chưa phải file Maven đầy đủ (parent/BOM/compiler/spring-boot-maven-plugin đã nằm trong skeleton do Initializr tạo). Đừng thêm `spring-boot-starter-web` vì Boot 3.x thường chọn Spring MVC nếu MVC và WebFlux đều có trên classpath.

Cấu trúc files:

~~~text
src/main/java/com/example/demo/
  DemoApplication.java
  NewsEventsController.java
src/main/resources/static/
  index.html
src/test/java/com/example/demo/
  NewsEventsControllerTest.java
~~~

### 11.2. DemoApplication.java

~~~java
package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
~~~

### 11.3. NewsEventsController.java

~~~java
package com.example.demo;

import java.time.Duration;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/events")
public class NewsEventsController {

    private static final int LAST_DEMO_ID = 5;

    @GetMapping(path = "/history",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> history(
            @RequestHeader(name = "Last-Event-ID",
                    required = false) String lastEventId) {

        int afterId = parseCursor(lastEventId);

        return Flux.range(afterId + 1, LAST_DEMO_ID - afterId)
                .delayElements(Duration.ofMillis(60))
                .map(id -> ServerSentEvent.<String>builder()
                        .id(Integer.toString(id))
                        .event("news")
                        .retry(Duration.ofSeconds(2))
                        .data("news-" + id)
                        .build());
    }

    @GetMapping(path = "/ticks",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> ticks() {

        return Flux.interval(Duration.ofSeconds(1))
                .map(index -> ServerSentEvent.<String>builder()
                        .event("tick")
                        .comment("per-subscriber demo timer")
                        .data("tick-" + index)
                        .build());
    }

    private static int parseCursor(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            int id = Integer.parseInt(value);
            if (id >= 0 && id <= LAST_DEMO_ID) {
                return id;
            }
        } catch (NumberFormatException ignored) {
            // Return 400 below for malformed or non-numeric IDs.
        }
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Invalid Last-Event-ID");
    }
}
~~~

**Phân tích từng bước:**

1. `history` nhận `Last-Event-ID` là **HTTP request header** tùy chọn. Nếu vắng, coi như `0` trong dataset này; giá trị không phải integer 0..5 bị từ chối **400**.
2. `Flux.range(afterId+1, 5-afterId)` phát các số nguyên từ ID tiếp theo tới 5. Với `Last-Event-ID: 3`, nó phát **4,5**; với 5 thì phát Flux rỗng.
3. `delayElements(60ms)` chỉ để cho curl/browser thấy streaming qua thời gian; nó không mô phỏng durable queue. Test có thể chờ đến 5 seconds.
4. `ServerSentEvent.<String>builder().id(...).event("news").retry(...).data(...).build()` tạo đúng frame có ID, named event và data.
5. `ticks` là infinite cold timer per subscription, không cung cấp IDs bởi những index mỗi subscriber không phải global log cursor.
6. Controller **không** tự subscribe; WebFlux quản lý subscribe, write và cancel khi connection kết thúc.
7. `parseCursor` giới hạn dữ liệu đầu vào, không cho một header tùy ý làm thay đổi scope của demo. Production không thể chỉ validate numeric range nếu event log có namespace/authorization/retention phức tạp.
8. Không dùng `Thread.sleep` trong Netty event-loop; Reactor timer scheduling không cần giữ blocked thread cho mỗi tick.
9. Các event data là `String` ngắn và cố định. Với DTO/JSON, `ServerSentEvent<SomeRecord>` hoạt động tương tự nếu codec được cấu hình, nhưng client cần `JSON.parse`.
10. Không xây `Cache-Control`/CORS/security/proxy deployment trong demo này. Phần production cần cấu hình các lớp đó và kiểm thử qua actual ingress.

### 11.4. index.html: browser client

`src/main/resources/static/index.html`

~~~html
<!DOCTYPE html>
<html lang="vi">
<head>
  <meta charset="UTF-8">
  <title>Spring WebFlux SSE Demo</title>
</head>
<body>
  <h1>Thông báo SSE</h1>
  <p id="state">Chưa kết nối</p>
  <ul id="news"></ul>
  <button id="startTicks">Mở live ticks</button>
  <button id="stopTicks">Đóng live ticks</button>
  <ul id="ticks"></ul>

  <script>
    const state = document.getElementById("state");
    const news = document.getElementById("news");
    const ticks = document.getElementById("ticks");

    // Stream hữu hạn; tự đóng ở event ID 5 để ngăn reconnect loop.
    const history = new EventSource("/api/events/history");

    history.onopen = () => {
      state.textContent = "Đang nhận lịch sử";
    };
    history.addEventListener("news", event => {
      const li = document.createElement("li");
      li.textContent = event.lastEventId + ": " + event.data;
      news.appendChild(li);

      if (event.lastEventId === "5") {
        history.close();
        state.textContent = "Đã nhận đủ năm sự kiện";
      }
    });
    history.onerror = () => {
      if (history.readyState !== EventSource.CLOSED) {
        state.textContent = "Mất kết nối, browser có thể thử lại";
      }
    };

    let live = null;
    document.getElementById("startTicks").onclick = () => {
      if (live !== null) return;

      live = new EventSource("/api/events/ticks");
      live.addEventListener("tick", event => {
        const li = document.createElement("li");
        li.textContent = event.data; // không dùng innerHTML
        ticks.appendChild(li);
      });
      live.onerror = () => {
        // Browser xử lý reconnect; log/observe nếu cần.
        console.warn("tick stream disconnected");
      };
    };
    document.getElementById("stopTicks").onclick = () => {
      if (live !== null) {
        live.close();
        live = null;
      }
    };
    window.addEventListener("beforeunload", () => {
      history.close();
      if (live !== null) live.close();
    });
  </script>
</body>
</html>
~~~

**Điểm thiết kế:**
- Khi đã nhận ID cuối `5`, code **`history.close()`** vì endpoint history hữu hạn. Nếu không, EventSource có thể reconnect sau khi server hoàn thành.
- `tick` là named event nên phải dùng `addEventListener("tick", ...)`.
- `textContent` tránh đưa dữ liệu SSE trực tiếp vào HTML parser; nếu hiển thị rich HTML phải sanitize và tuân output encoding/CSP.
- `stopTicks` đóng subscription/kết nối, giúp giải phóng tài nguyên browser/server sớm hơn.
- Code `live` không chống rendering hàng triệu `li` nếu để mở nhiều giờ; production cần **giới hạn số items hiển thị** hoặc virtualized list. UI memory cũng là thành phần cần giám sát.
- Không xem một EventSource nối lại là bảo đảm server đã phát lại mọi dữ liệu thiếu; demo history chỉ replay năm IDs cố định.

### 11.5. NewsEventsControllerTest.java

Các test dùng **WebTestClient** qua embedded HTTP server để đọc stream hữu hạn thành typed `ServerSentEvent<String>`. Không kết nối `ticks` vô hạn và cố đợi nó `complete`.

~~~java
package com.example.demo;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NewsEventsControllerTest {

    @LocalServerPort
    int port;

    WebTestClient client;

    @BeforeEach
    void setup() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Test
    void emitsFiveNamedEventsWithIdsAndRetry() {
        List<ServerSentEvent<String>> received =
                client.get()
                        .uri("/api/events/history")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .exchange()
                        .expectStatus().isOk()
                        .expectHeader().contentTypeCompatibleWith(
                                MediaType.TEXT_EVENT_STREAM)
                        .returnResult(
                            new ParameterizedTypeReference<
                                ServerSentEvent<String>>() {})
                        .getResponseBody()
                        .collectList()
                        .block(Duration.ofSeconds(5));

        assertNotNull(received);
        assertEquals(5, received.size());

        ServerSentEvent<String> first = received.get(0);
        assertEquals("1", first.id());
        assertEquals("news", first.event());
        assertEquals("news-1", first.data());
        assertEquals(Duration.ofSeconds(2), first.retry());

        ServerSentEvent<String> last = received.get(4);
        assertEquals("5", last.id());
        assertEquals("news-5", last.data());
    }

    @Test
    void lastEventIdRequestsOnlyEventsAfterCursor() {
        List<ServerSentEvent<String>> received =
                client.get()
                        .uri("/api/events/history")
                        .header("Last-Event-ID", "3")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .exchange()
                        .expectStatus().isOk()
                        .returnResult(
                            new ParameterizedTypeReference<
                                ServerSentEvent<String>>() {})
                        .getResponseBody()
                        .collectList()
                        .block(Duration.ofSeconds(5));

        assertNotNull(received);
        assertEquals(2, received.size());
        assertEquals("4", received.get(0).id());
        assertEquals("5", received.get(1).id());
    }

    @Test
    void malformedCursorIsBadRequest() {
        client.get()
                .uri("/api/events/history")
                .header("Last-Event-ID", "abc")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void liveTimerProducesEventsWithoutWaitingForCompletion() {
        List<ServerSentEvent<String>> received =
                client.get()
                        .uri("/api/events/ticks")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .exchange()
                        .expectStatus().isOk()
                        .returnResult(
                            new ParameterizedTypeReference<
                                ServerSentEvent<String>>() {})
                        .getResponseBody()
                        .take(2)
                        .collectList()
                        .block(Duration.ofSeconds(5));

        assertNotNull(received);
        assertEquals(2, received.size());
        assertEquals("tick", received.get(0).event());
        assertEquals("tick-0", received.get(0).data());
        assertEquals("tick-1", received.get(1).data());
    }
}
~~~

Các test nhấn mạnh:
- Khi stream hữu hạn, `collectList()` chỉ complete sau event cuối; cần timeout để tránh test treo nếu code thay đổi thành infinite stream.
- Khi stream vô hạn, **`take(2)` giới hạn dữ liệu và cancel subscription** sau hai items; không gọi `collectList()` trên Flux vô hạn.
- Assert `id`, `event`, `data`, `retry` thay vì dựa vào các đoạn output byte có thể thay đổi whitespace/codec.
- `Last-Event-ID: 3` tạo events 4,5 theo contract demo. Đây là logic replay có dataset cố định chứ chưa là message durability.
- HTTP server dùng random port và client `bindToServer`, khác với test controller trực tiếp; **test này không mô phỏng reverse proxy/CDN**. Test streaming qua ingress thật vẫn cần riêng.
- `.block(Duration)` được dùng **chỉ trong JUnit test thread** để chờ kết quả; không dùng `.block()` trong WebFlux event-loop controller.

Để chạy:
~~~bash
./mvnw test
~~~

Trên Windows:
~~~powershell
.\mvnw.cmd test
~~~

Nếu không có Maven wrapper, dùng `mvn test` với Maven đã cài. Test timeout ở đây là bảo vệ JUnit run, không chứng minh SLA/liveness trên production.

## 12. Thử bằng curl và quan sát wire format

Khởi động ứng dụng:

~~~bash
./mvnw spring-boot:run
~~~

Đọc lịch sử:

~~~bash
curl -N -i   -H "Accept: text/event-stream"   http://localhost:8080/api/events/history
~~~

Đầu ra minh họa (bỏ qua những headers khác):

~~~text
id:1
event:news
retry:2000
data:news-1

id:2
event:news
retry:2000
data:news-2

~~~

Codec thực tế có thể có dấu cách sau dấu `:` hoặc thứ tự các fields khác nhưng semantics phải giống nhau. `-N` của curl tắt buffering stdout, giúp nhìn events tới từng đợt. Nếu dùng `curl` không có `-N`, có thể thấy hiển thị bị giữ lại bởi output buffering dù server thực sự stream.

Thử resume:

~~~bash
curl -N -i   -H "Last-Event-ID: 3"   -H "Accept: text/event-stream"   http://localhost:8080/api/events/history
~~~

Kết quả stream có **ID 4 và 5**, không phát lại 1–3. Điều này chỉ đúng do contract của demo.

Xem live ticks:

~~~bash
curl -N -H "Accept: text/event-stream"   http://localhost:8080/api/events/ticks
~~~

Trong terminal, nhấn `Ctrl+C` để chủ động ngắt; phía server nên nhận signal cancellation/connection closed để ngừng subscription. Đừng để test/CLI mở vô hạn rồi kết luận server leak khi connection chưa đóng.

Sai cursor:

~~~bash
curl -i   -H "Last-Event-ID: abc"   http://localhost:8080/api/events/history
~~~

Endpoint trả **400 Bad Request** theo `parseCursor` đã định nghĩa.

**Lưu ý về shell:** ví dụ nhiều dòng dùng dấu `\` dành cho Linux/macOS/Git Bash. Trên Windows PowerShell, dùng `curl.exe` trong một dòng hoặc cú pháp line continuation phù hợp.

## 13. Error handling: trước và sau khi response đã commit

Khi controller trả Publisher, **errors có thể xảy ra**:
1. Trước HTTP response headers: parse `Last-Event-ID` lỗi → có thể trả **400** như API thông thường.
2. Sau khi gửi event thứ 100: response headers đã commit, server không thể tùy ý đổi sang HTTP 500 JSON body mà vẫn giữ cùng response. Kết nối có thể kết thúc/lỗi và browser sẽ reconnect.
3. Trong source `WebClient`/database/message broker: cần phân biệt recoverable vs fatal. Nếu muốn gửi một **application-level error event** và đóng stream, có thể `onErrorResume` sang event `error` theo protocol riêng nhưng không được rò exception stack/credentials ra client.
4. Client disconnect: Reactor subscription thường cancel; source cần release DB cursor, broker subscription và resources qua `doFinally`/`usingWhen` khi cần.

Ví dụ minh họa:

~~~java
return source
        .map(message -> ServerSentEvent.<String>builder()
                .event("news")
                .data(message)
                .build())
        .onErrorResume(ex ->
                Flux.just(ServerSentEvent.<String>builder()
                        .event("stream-error")
                        .data("Temporary stream error")
                        .build()));
~~~

Đây là snippet chưa runnable độc lập vì `source` chưa được khai báo. Sau khi phát `stream-error`, stream thường complete; client phải quyết định resubscribe, UI error hay full resync. **Không** tự gửi `ex.getMessage()` nếu exception có thể chứa nội bộ service/token/PII.

### 13.1. Retry storm

Nếu 5.000 browser clients cùng reconnect sau một outage, tất cả có thể gửi GET lại gần như đồng thời. `retry: 1000` không giải quyết server saturation. Cần:
- Rate limit/admission control ở ingress.
- Server/client policy retry/backoff/jitter khi có quyền điều chỉnh.
- Hạn chế subscription setup nặng.
- Shared event storage và incremental catch-up phù hợp.
- Capacity/load testing với reconnect bursts.
- Không tạo connection pool/tracing objects mới vô hạn cho từng reconnect.

Browser EventSource built-in `retry` không thể biểu diễn tất cả jitter policies phức tạp chỉ qua một field. Nếu yêu cầu chuyên biệt, application-level connection management có thể cần một transport/client khác.

## 14. SSE và transaction/event outbox

Ví dụ: một REST POST tạo đơn hàng trong database, rồi notification được đẩy qua SSE. Nếu code phát event **trước khi DB commit**, browser có thể nhận “order-created” trong khi transaction rollback. Nếu phát **sau commit** nhưng server crash trước publish, event bị mất.

Một kiến trúc đáng tin cậy hơn:

~~~text
HTTP POST /orders
     |
     v
DB transaction: orders row + outbox_event row
     |
     +---- COMMIT
               |
               v
Outbox publisher/broker
               |
               v
Durable event stream
               |
               +---- replay after Last-Event-ID
               |
               v
Authorized SSE subscriptions (per user / tenant)
               |
               v
Browser EventSource
~~~

Outbox xử lý consistency giữa DB changes và notification workflow tốt hơn “save DB rồi fire-and-forget `sink.tryEmitNext`”. Nhưng vẫn phải xử lý duplicate delivery, retention, authorization, crash/restart, message ordering và poison messages.

### Last-Event-ID và DB cursor

Ví dụ SQL giả định có `notification_events(id BIGINT, owner_id, payload)`:

~~~sql
SELECT id, payload
FROM notification_events
WHERE owner_id = :ownerId
  AND id > :lastEventId
ORDER BY id ASC
LIMIT :batchSize;
~~~

Các variables là bind parameters của DB driver, không nối string từ header. Nếu IDs tăng toàn cục và có gaps, truy vấn vẫn trả những events có ID lớn hơn theo thứ tự. `LIMIT` kiểm soát batch memory nhưng server cần lặp tới khi bắt kịp log rồi chuyển live tail **mà không bỏ lỡ khoảng giao nhau**.

Một vài stream IDs từ Kafka/Redis không phải số nguyên tuyến tính một chiều toàn hệ thống. `Last-Event-ID` là chuỗi opaque đối với SSE protocol; ứng dụng có thể đặt cursor dạng composite/versioned token (không chứa secret) và parse theo format riêng.

## 15. Quan sát, tối ưu và giới hạn tài nguyên

Mỗi SSE connection có một thời gian sống dài; HTTP server latency metric cho request có thể bằng **thời gian mở kết nối** chứ không phải latency của từng event. Hãy đo:

| Metric | Dùng để biết |
| --- | --- |
| `sse_connections_active` | Đang có bao nhiêu subscribers/kết nối |
| `sse_subscribe_rate` | Tốc độ kết nối mới/reconnect |
| `sse_disconnections_total` | Client rời, network lỗi, proxy timeout |
| `sse_heartbeat_gap_seconds` | Có gửi/flush heartbeat đúng hạn không |
| `sse_event_lag_ms` | Từ event source timestamp đến lúc client nhận (nếu đo được) |
| `sse_events_dropped_total` | Sink/operator policy làm mất event |
| `sse_replay_requests_total` | Khối lượng reconnect/replay |
| `sse_replay_gap_total` | Cursor quá cũ hoặc missing data |
| JVM heap/direct memory/GC/threads | Sức khỏe server và buffer pressure |
| Gateway/LB concurrent streams/timeouts | Bottleneck tầng ngoài |
| DB/broker consumer lag | Source đã chậm trước khi phát SSE |

Các tên metrics là **gợi ý thiết kế**, không tuyên bố Spring Boot tự xuất đúng chúng mà không instrumentation. `Micrometer`/Actuator có thể thêm để ghi gauges/counters theo ứng dụng; tránh gán label `userId` có cardinality rất cao nếu không cần.

### 15.1. Không giữ toàn bộ events trong memory

~~~java
// ANTI-PATTERN: không có bound rõ cho history.
List<Notification> history = new ArrayList<>();
~~~

Nếu lưu mỗi event vĩnh viễn cho mọi user, heap tăng không giới hạn. Hãy dùng durable store có TTL/retention, pagination/checkpoints; nếu replay in-memory chỉ dùng `replay().limit(n)` với n nhỏ, biết rõ sẽ mất khi restart.

### 15.2. Slow subscribers

Đặt policy trước: events có được phép mất không? Nếu là telemetry mới nhất có thể drop intermediate states. Nếu là audit/payment events, không được silently drop, nên lưu durable log và cho client catch up bằng cursor. Đừng chọn `onBackpressureBuffer` không giới hạn chỉ để tránh lỗi immediate overflow.

### 15.3. Timeouts và heartbeat

Chọn nhịp heartbeat sao cho nhỏ hơn toàn bộ idle timeouts, đồng thời không làm network bùng nổ khi số client cực lớn. Nếu 100.000 clients và heartbeat 15 s, trung bình sẽ có khoảng 6.667 heartbeat writes/second chưa tính event thật; phải đo năng lực server/gateway, không coi comment-only frame là miễn phí.

### 15.4. Graceful shutdown

Khi deploy rolling restart, connections có thể bị ngắt/reconnect. Thiết kế:
- Health/readiness chặn subscriptions mới khi chuẩn bị shutdown.
- Có thời gian drain/close stream phù hợp.
- Browser reconnect sang instance khác mà cursor vẫn hợp lệ trong durable store.
- Logs/metrics nhận diện deliberate shutdown khác outage.
- Không để job/thread/connection từ source tiếp tục hoạt động khi SSE subscription đã cancel.

## 16. Khi nào chọn SSE thay vì WebSocket?

Chọn **SSE** khi:
- Dữ liệu truyền chủ yếu **server → browser**.
- Nội dung là text/JSON notifications; client không cần binary/multiplex custom protocol.
- Muốn browser tự reconnect theo SSE semantics.
- Có thể dùng GET trên HTTP infrastructure sẵn có.
- Không cần client gửi realtime nhiều messages trên cùng connection.

Chọn **WebSocket** khi:
- Cần **full-duplex**: game, chat với input realtime, collaboration, low-latency interactive controls.
- Truyền binary messages theo framing riêng.
- Cần hai chiều nhiều messages trong cùng một persistent channel và sẵn sàng quản lý reconnect/ack/protocol.

Chọn **polling** khi:
- Tải thấp, dữ liệu cập nhật thưa, simplicity/infra compatibility quan trọng hơn realtime.
- Gateways/proxies không hỗ trợ long-lived SSE connections như yêu cầu.
- Client/công nghệ hạn chế EventSource.

Chọn **message broker** như Kafka/Redis Streams khi cần fan-out/durable replay/scale pods; broker là **nguồn/transport nội bộ**, và SSE có thể là **giao diện HTTP ra browser**. Đây không phải hai lựa chọn loại trừ nhau.

## 17. Lỗi phổ biến và cách khắc phục

| Triệu chứng | Nguyên nhân thường gặp | Cách sửa |
| --- | --- | --- |
| Client chỉ thấy output sau khi stream đóng | Proxy/HTTP client buffering | `curl -N`, proxy buffering off, heartbeat/flush |
| `onmessage` không chạy | Server phát named `event: news` | `addEventListener("news", ...)` |
| EventSource mở lại nhiều lần khi đã nhận đủ | Flux hữu hạn complete; browser reconnect | Client `close()` ở terminal event |
| Duplicate messages sau reconnect | Replay có thể trùng/cursor update timing | Idempotent client, sequence IDs |
| Mất events khi chuyển pod | In-memory sink/timer không durable | Shared event log + Last-Event-ID |
| Cùng ID nhưng payload khác sau restart | ID không ổn định/global | Stable event IDs thuộc domain/stream |
| Client bị 400 khi reconnect | Header cursor format không hợp lệ/đã đổi | Version cursor parser, resync policy |
| Server không nhận Last-Event-ID như mong đợi | Client chưa từng thấy ID hợp lệ hoặc không dùng EventSource | Inspect request/reconnect headers |
| SSE stream trả Content-Type application/json | Thiếu `produces=text/event-stream` | Khai báo đúng mapping hoặc ServerSentEvent wrapper |
| Client không hiển thị dữ liệu | Named event vs JSON parsing/display logic | Check frames, event names, textContent |
| Kết nối đóng mỗi phút | Gateway idle timeout | Heartbeat nhỏ hơn timeout, config gateway |
| Browser thiếu cookie cross-site | SameSite/CORS credentials | Origin allowlist, Secure cookie, withCredentials |
| Dùng Bearer header được với fetch nhưng EventSource không | EventSource không hỗ trợ custom headers | Cookie hoặc streaming fetch client |
| Tất cả users nhận thông báo user khác | Shared broadcast sink không filter | Per-tenant/user subscriptions + authorization |
| RAM tăng cùng số clients | Unbounded buffer/history/UI list | Bounded queues, retention, cleanup |
| Một client chậm làm nghẽn mọi người | Sink policy có coupling/buffer | directBestEffort hoặc isolated durable fan-out |
| Data events mất khi drop policy | `onBackpressureLatest` bỏ intermediate updates | Durable log cho dữ liệu cần chính xác |
| EventSource gửi lại request sau lỗi | Auto reconnect là behavior chuẩn | Xử lý backoff/rate/close khi terminal |
| `Flux.interval` có ID 0 lại sau reconnect | Per-subscriber timer | Không dùng timer ID làm durable cursor |
| Test `collectList` treo | Flux vô hạn không complete | `take(n)` trước `collectList` |
| SSE bị XSS ở UI | Dùng `innerHTML = event.data` | `textContent`/escaping |
| Server lỗi sau khi đã stream vài events | HTTP headers đã commit | App-level error event + close/reconnect |
| Thống kê latency HTTP lớn | SSE connection kéo dài | Đo per-event lag, connection metrics riêng |

## 18. Câu hỏi ôn tập

**1. SSE có hoạt động theo cả hai chiều không?**  
Không. SSE là luồng response từ server tới client qua HTTP GET; cần request khác để client gửi dữ liệu.

**2. `ServerSentEvent` cho phép đặt những field nào?**  
Các trường chính `id`, `event`, `data`, `retry`, `comment`. `Flux<ServerSentEvent<T>>` phù hợp khi cần điều khiển chúng.

**3. Khi nào dùng `onmessage`, khi nào dùng `addEventListener`?**  
`onmessage` nhận event mặc định không có named `event:`; named events như `event: news` cần `addEventListener("news", ...)`.

**4. Last-Event-ID có tự phát lại các events bị mất không?**  
Không. Nó cung cấp cursor, server phải có durable replay source và logic truy vấn/authorization.

**5. Vì sao browser reconnect sau khi Flux hữu hạn complete?**  
EventSource thường retry khi connection đóng; client nên `close()` khi nhận sự kiện kết thúc có chủ đích.

**6. Heartbeat `: keep-alive` có dispatch message không?**  
Không. Comment frame giúp gửi bytes định kỳ, kiểm tra connection/idle proxy, nhưng không gọi `onmessage`.

**7. `Flux.interval` có phải hot topic chung cho mọi browser không?**  
Không mặc định; mỗi subscription có timer/index riêng. Muốn fan-out dùng shared source/Sinks/broker và thiết kế event IDs.

**8. `directBestEffort` có bảo đảm mọi subscriber nhận đủ dữ liệu không?**  
Không. Subscriber chậm có thể bị bỏ sự kiện, và sink không persist history.

**9. SSE có đảm bảo exactly-once delivery không?**  
Không. Cần idempotency, checkpoint và hệ thống lưu sự kiện nếu muốn delivery semantics mạnh.

**10. Tại sao `collectList()` trên `/ticks` dễ gây test treo?**  
Vì `Flux.interval` không kết thúc. Phải `take(n)`/timeout hoặc chủ động cancel.

**11. Vì sao không nên đưa bearer token lên query param SSE URL?**  
URL dễ xuất hiện trong log, analytics, referrer, browser history; nên dùng cookie có security policy hoặc giải pháp authentication được thiết kế.

**12. HTTP/2 có loại bỏ tất cả connection limits cho SSE?**  
Không. Có limits concurrent streams và hạ tầng/proxy/server budgets; cần load test thực.

## 19. Checklist triển khai production

1. Khai báo response `text/event-stream`, UTF-8; trả `Flux<ServerSentEvent<T>>` khi cần fields đầy đủ.
2. Định nghĩa event names, schema JSON/version, IDs và retry semantics rõ ràng cho clients.
3. Dùng `EventSource` đúng named event listeners; `close()` khi không còn cần stream.
4. Đặt heartbeat comment-only nếu đường truyền có thể idle; test flush thật qua CDN/proxy.
5. Khi muốn resume, lưu event log đáng tin + retention và xử lý `Last-Event-ID`, duplicate/gap/resync.
6. Authorization cho connection, replay và từng tenant/user; không broadcast dữ liệu nhạy cảm qua sink chung.
7. Bảo vệ cookie/CORS/CSRF cho những endpoints liên quan; không gửi token trong URL.
8. Chọn rõ hot/cold Publisher; giới hạn connection count, per-client buffer, subscriber lag và cancellation.
9. Không bật replay/all hoặc unbounded buffers mà thiếu retention/memory budgets; chịu trách nhiệm overflow policy.
10. Đặt timeout/ingress/proxy buffering/HTTP2 settings thích hợp với SSE, đo reconnect storms.
11. Với events thật từ DB, cân nhắc outbox/event log để tránh publish-before-commit và mất thông báo sau crash.
12. Test browser, `curl -N`, WebTestClient, replay từ cursor, named events, slow client, proxy buffering và rolling restart.
13. Đo số connections, retry/drop/replay, per-event delivery lag, JVM memory, broker lag và Gateway limits.
14. Không hứa SSE luôn nhanh hơn polling hoặc WebSocket; đo theo traffic profile và operational constraints.

## 20. Nguồn tham khảo

- [Spring Framework 6.2 — WebFlux controller return values, Flux ServerSentEvent](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/controller/ann-methods/return-types.html)
- [Spring Framework 6.2 — WebFlux Streaming and heartbeat](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/reactive-spring.html)
- [Spring Framework 6.2 — ServerSentEvent API](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/http/codec/ServerSentEvent.html)
- [Spring Framework 6.2 — WebTestClient](https://docs.spring.io/spring-framework/reference/6.2/testing/webtestclient.html)
- [Project Reactor — Sinks and multicast/replay/backpressure](https://projectreactor.io/docs/core/release/reference/coreFeatures/sinks.html)
- [Project Reactor — Flux.interval and Reactor operators](https://projectreactor.io/docs/core/release/reference/)
- [MDN — Using server-sent events / EventSource](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events)
- [MDN — EventSource](https://developer.mozilla.org/en-US/docs/Web/API/EventSource)
- [WHATWG HTML Living Standard — Server-sent events](https://html.spec.whatwg.org/multipage/server-sent-events.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-streaming-002">JSON theo luồng, NDJSON và lựa chọn media type</a>

<details>
<summary>Xem chi tiết</summary>

# JSON theo luồng, NDJSON và lựa chọn media type trong Spring WebFlux

## 1. Bối cảnh: một Flux không tự quyết định định dạng HTTP

Một controller Spring WebFlux có thể trả `Flux<Event>` để biểu diễn nhiều dữ liệu xuất hiện qua thời gian. Nhưng **`Flux<T>` là kiểu Publisher của ứng dụng, không phải một định dạng trên dây (wire format)**. Cùng một `Flux<Event>` có thể được HTTP encoder viết thành:

- **Một JSON array hoàn chỉnh** với `Content-Type: application/json`, ví dụ `[{"id":1},{"id":2}]`.
- **Các JSON values tách nhau bằng newline** với `Content-Type: application/x-ndjson`, ví dụ một object trên một dòng.
- **Server-Sent Events (SSE)** với `Content-Type: text/event-stream`, mỗi event có framing `id:`, `event:`, `data:`.
- Một dạng binary/streaming khác khi dùng codec, media type và contract tương ứng.

Sự khác biệt tác động trực tiếp tới thời điểm client thấy dữ liệu, memory, backpressure, caching, browser support, status/error handling và khả năng resume sau khi kết nối ngắt.

Ví dụ, một report trả dữ liệu sau 10 giây và gồm 10.000 records:

- Nếu client chỉ muốn tải một document JSON chuẩn và cần parse toàn bộ kết quả: `application/json` thường phù hợp.
- Nếu client muốn xử lý từng bản ghi ngay khi server phát ra, và cả hai bên hỗ trợ line-oriented parsing: **NDJSON** phù hợp hơn.
- Nếu browser muốn theo dõi các cập nhật dạng thông báo một chiều qua kết nối mở lâu: **SSE** thường dễ dùng hơn NDJSON với `EventSource`.
- Nếu yêu cầu là byte-stream/file, hoặc một protocol có framing khác, không nên đặt `application/json` chỉ vì dữ liệu ở source ban đầu là Java object.

**Phạm vi:** Spring **WebFlux** (không phải Servlet MVC), Java **17**, Spring Boot **3.3.5**, Spring Framework **6.1.x**, Reactor `Flux`/`Mono`, Jackson JSON codec, annotated controllers, WebTestClient. Chủ đề chính là **JSON streaming, NDJSON và lựa chọn media type**; những phần về bảo mật, cancel và backpressure chỉ giải thích các ranh giới cần thiết để triển khai đúng.

### Kết quả học tập

Sau bài này, bạn có thể:

1. Phân biệt JSON document, JSON array, NDJSON, JSON Text Sequences và SSE.
2. Biết vì sao `Flux<T>` với `application/json` và `application/x-ndjson` được encode khác nhau.
3. Thiết kế `produces`/`consumes`, `Accept`/`Content-Type` và test 406/415 đúng.
4. Viết endpoint trả `ResponseEntity<Flux<DTO>>` theo JSON array hoặc NDJSON.
5. Nhận `@RequestBody Flux<DTO>` dạng NDJSON, giới hạn kích thước và validate trước side effects.
6. Dùng `WebTestClient` để kiểm tra nội dung JSON array, line framing và reactive item decoding.
7. Hiểu sự khác biệt giữa flush của encoder và việc một client nhận từng network chunk.
8. Thiết kế status/headers và error policy khi HTTP response có thể đã commit một phần.
9. Lựa chọn SSE, NDJSON hoặc regular JSON theo consumer, kết nối và yêu cầu nghiệp vụ.
10. Phát hiện lỗi blocking event-loop, buffer vô hạn, request-body double-read, stream cancellation và bảo mật dữ liệu.

## 2. So sánh ba định dạng cần nhớ trước tiên

Giả sử dữ liệu Java gồm hai events:

```java
new Event(1L, "order", "created");
new Event(2L, "order", "paid");
```

Đây là snippet dùng record `Event` sẽ được khai báo trong project đầy đủ.

### 2.1. Một JSON array: application/json

HTTP body:

```json
[
  {"id":1,"type":"order","message":"created"},
  {"id":2,"type":"order","message":"paid"}
]
```

Đây là **một JSON document hợp lệ** có root là array. Dù Jackson có thể xuất tối giản không có khoảng trắng, array vẫn có opening bracket, separators giữa elements, closing bracket.

**Điểm mạnh:** hầu hết client JSON parsers hiểu được; thuận tiện cho list nhỏ/response hoàn tất, cache, schema và API tooling. **Giới hạn:** một JSON document chưa hoàn chỉnh cho tới khi có đủ closing delimiters; encoder WebFlux thường gom các phần tử của Flux để serialize thành một JSON array khi dùng media type không streaming. Không nên giả định client nhận được bản ghi đầu ngay khi upstream Flux phát bản ghi đầu.

### 2.2. NDJSON: application/x-ndjson

HTTP body:

```text
{"id":1,"type":"order","message":"created"}
{"id":2,"type":"order","message":"paid"}
```

Mỗi dòng là **một JSON text độc lập**. Các record cách nhau bằng `\n` (newline); thường có newline cuối. Đây **không phải** một JSON array và toàn bộ chuỗi nối các objects không phải một single JSON document mà `JSON.parse(wholeBody)` trực tiếp chấp nhận.

**Điểm mạnh:** line-oriented parser có thể xử lý record ngay sau khi nhận đủ newline; phù hợp export lớn, append-only logs/events, ETL, pipeline streaming. **Giới hạn:** schema/ordering/recovery do ứng dụng quyết định; parser cần xử lý dữ liệu đến từng phần, không được mặc định mỗi TCP read hoặc HTTP chunk tương ứng một dòng.

NDJSON là dạng thường được gọi là *Newline Delimited JSON* hoặc *JSON Lines*. MIME type phổ biến trong Spring WebFlux hiện đại là **`application/x-ndjson`**. Với hệ thống cũ có thể gặp `application/stream+json`; không coi chúng tự động interchangeable nếu chưa xác minh codec/version/client support.

### 2.3. SSE: text/event-stream

HTTP body:

```text
id: 1
event: order
data: {"id":1,"type":"order","message":"created"}

id: 2
event: order
data: {"id":2,"type":"order","message":"paid"}

```

SSE là **protocol framing khác**: blank line kết thúc event, `data:` chứa payload, `id:` hỗ trợ reconnection thông qua `Last-Event-ID` và `event:` đặt tên event. Nó có thể mang JSON trong data, nhưng **không phải NDJSON**. Trên browser, `EventSource` được thiết kế để tiêu thụ `text/event-stream`, không phải `application/x-ndjson`.

Một EventSource browser là **một chiều server → client**. Nếu cần client gửi liên tục hai chiều, thường cân nhắc WebSocket hoặc một protocol phù hợp khác.

### Bảng tóm tắt

| Thuộc tính | application/json | application/x-ndjson | text/event-stream |
| --- | --- | --- | --- |
| Framing | JSON document, thường array | Một JSON text mỗi dòng | SSE fields + blank line |
| Mức độ dễ parse | Rất phổ biến | Cần NDJSON parser | Browser EventSource hoặc SSE parser |
| Nhiều item theo thời gian | Có thể từ Flux nhưng encoder thường gom trước khi output | Có, flush từng item theo streaming codec | Có, flush theo event |
| Parse khi chưa có toàn bộ body | Không với JSON.parse toàn body | Có khi đủ newline của từng record | Có khi đủ event boundary |
| Client browser built-in | fetch + response.json() | fetch streaming + TextDecoder/parser | EventSource |
| ID/reconnect semantics | Tự thiết kế | Tự thiết kế | `id`, `Last-Event-ID`, auto reconnect của EventSource |
| Response có thể vô hạn | Không lý tưởng với JSON array | Có thể, khi đặt limits/protocol | Có thể, cần heartbeat/cancel |
| Typical use | CRUD/list nhỏ | Logs, exports, batch records | Live notifications, progress |

## 3. Media type là một phần của hợp đồng, không phải nhãn trang trí

WebFlux dùng `MediaType` để lựa chọn codec và hành vi ghi response. Hai quyết định thường độc lập:

- `Content-Type`: media type của **body HTTP đang được gửi**.
- `Accept`: media types mà **client muốn nhận** từ response.

Ví dụ client yêu cầu NDJSON:

```http
GET /api/streams/ndjson HTTP/1.1
Host: localhost:8080
Accept: application/x-ndjson
```

Response:

```http
HTTP/1.1 200 OK
Content-Type: application/x-ndjson

{"id":1,"type":"order","message":"created"}
{"id":2,"type":"order","message":"paid"}
```

Với POST NDJSON, client gửi:

```http
POST /api/streams/ingest HTTP/1.1
Host: localhost:8080
Content-Type: application/x-ndjson
Accept: application/json

{"message":"first"}
{"message":"second"}
```

Ở đây **request body** là NDJSON còn **response body** có thể là một JSON object tóm tắt. Đừng yêu cầu Accept NDJSON chỉ vì Content-Type NDJSON; hai chiều là hai contract khác nhau.

### Các constants nên dùng

```java
MediaType.APPLICATION_JSON
MediaType.APPLICATION_JSON_VALUE
MediaType.APPLICATION_NDJSON
MediaType.APPLICATION_NDJSON_VALUE
MediaType.TEXT_EVENT_STREAM
MediaType.TEXT_EVENT_STREAM_VALUE
```

Đây là Spring `org.springframework.http.MediaType` constants, không phải strings tự định nghĩa trong project. Dùng chúng trong `@GetMapping(produces=...)`, `@PostMapping(consumes=...)`, `ResponseEntity.contentType(...)` và WebTestClient.

### Các media type dễ nhầm

| Media type | Ý nghĩa | Ghi nhớ |
| --- | --- | --- |
| `application/json` | Một JSON representation/document | Không đồng nghĩa line-oriented stream |
| `application/x-ndjson` | NDJSON, một JSON text trên mỗi dòng | Dùng khi consumer hiểu NDJSON |
| `text/event-stream` | Server-Sent Events | Có fields/event boundaries, không phải NDJSON |
| `application/json-seq` | JSON Text Sequences theo RFC 7464 | Dùng RS (ASCII 0x1E) framing; **không phải NDJSON** |
| `application/stream+json` | Tên cũ thường thấy trong một số Spring integrations | Tùy version/codec; ưu tiên NDJSON khi hợp đồng cho phép |
| `application/octet-stream` | Bytes tùy ý | Không mang semantics JSON/object stream |
| `text/plain` | Văn bản thuần | Có thể chứa chuỗi JSON nhìn giống JSON nhưng không tự có JSON codec contract |

**Không chọn `application/json-seq` thay NDJSON chỉ vì đều "nhiều JSON".** RFC 7464 sử dụng record separator, còn NDJSON dựa trên newline. Parsers phải thống nhất framing.

## 4. Spring WebFlux encode Flux ra HTTP như thế nào?

Request đi qua `DispatcherHandler`/result handling và các `HttpMessageWriter`. Với JSON, `Jackson2JsonEncoder` và HTTP codecs của Spring làm việc dựa trên type, media type và reactive publisher.

Về cơ chế:

```text
Flux<Event> của controller
      |
      v
WebFlux HandlerResultHandler
      |
      v
HttpMessageWriter chọn encoder theo element type + media type
      |
      +--> application/json
      |      thường gom các elements trước khi encode
      |      tạo một JSON array document
      |
      +--> application/x-ndjson
             encode từng Event thành JSON text
             thêm newline separator
             flush theo từng value với streaming media type
      |
      v
ServerHttpResponse.writeWith / writeAndFlushWith
      |
      v
Reactive HTTP adapter + network buffers + proxy
```

### Vì sao chọn NDJSON lại có thể giảm latency "time-to-first-item"?

Với `application/json`, encoder thường cần biết collection để tạo một JSON array hoàn chỉnh. Với streaming media type, encoder có thể encode, write và **flush từng value**, nên không cần đợi source hoàn tất để đưa record đầu xuống output pipeline.

**Tuy nhiên flush không có nghĩa "đã tới browser ngay lập tức"**:
- Reactor Netty có event loops và TCP buffers.
- Kernel có packetization và network latency.
- Reverse proxy/API gateway có thể bật buffering/compression.
- HTTP/2 flow control hoặc client đọc chậm ảnh hưởng nhịp.
- Client decoder có thể đợi đủ newline/frame.
- TLS transport và network chunk boundaries không trùng record boundaries.

Vì vậy đo cả **server emission time**, **first byte time** và **client observed first record**, thay vì đo chỉ tổng latency.

### Flux<T> không bắt buộc là streaming response

```java
@GetMapping(path = "/json",
            produces = MediaType.APPLICATION_JSON_VALUE)
public Flux<Event> json() {
    return source.events();
}
```

Không có gì trong chữ ký `Flux<Event>` đảm bảo mỗi Event được flush riêng. Nếu cần contract NDJSON, khai báo `produces=APPLICATION_NDJSON_VALUE` và test response Content-Type/wire format.

### Không dùng Flux<String> để "tự động tạo JSON array"

Với Jackson codecs, `String` có handling riêng, thường được coi là nội dung văn bản để CharSequence encoder ghi, không phải DTO JSON cần serialize thành JSON string elements. Vì vậy:

```java
// Để trả JSON array strings chính xác, dùng một value List:
@GetMapping(path = "/labels",
            produces = MediaType.APPLICATION_JSON_VALUE)
public Mono<List<String>> labels() {
    return Mono.just(List.of("first", "second"));
}
```

Snippet này phụ thuộc imports `GetMapping`, `Mono`, `List`, `MediaType`. Nó rõ ràng về JSON array `["first","second"]`. Đừng giả định `Flux.just("first","second")` được serialize thành cùng JSON array dưới mọi codec configuration.

## 5. Ba kiểu endpoint phổ biến

### 5.1. JSON list hữu hạn

```java
@GetMapping(path = "/list",
            produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<Flux<Event>> list() {
    return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(source.events());
}
```

Status và Content-Type biết ngay. Body là Flux, nhưng vì media type là JSON thông thường nên HTTP result dự kiến là **một array JSON** sau quá trình encoding. Phù hợp khi danh sách hữu hạn và không quá lớn.

### 5.2. NDJSON hữu hạn

```java
@GetMapping(path = "/ndjson",
            produces = MediaType.APPLICATION_NDJSON_VALUE)
public ResponseEntity<Flux<Event>> ndjson() {
    return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_NDJSON)
            .body(source.eventsWithDelay());
}
```

Nguồn phát theo thời gian; encoder NDJSON ghi từng object và newline. Client có thể xử lý từng record độc lập. Status 200 và headers được chọn trước khi body phát. **Nếu event thứ ba fail sau hai dòng đã flush, status khó có thể đổi thành 500 nữa.**

### 5.3. NDJSON dài hạn hoặc vô hạn

```java
@GetMapping(path = "/live",
            produces = MediaType.APPLICATION_NDJSON_VALUE)
public Flux<Event> live() {
    return Flux.interval(Duration.ofSeconds(1))
            .map(n -> new Event(
                    n + 100, "heartbeat", "tick-" + n));
}
```

Đây là snippet với record `Event` có ba fields được định nghĩa bên dưới. `Flux.interval` dùng Reactor scheduler; không tạo một Java thread ngủ riêng trên mỗi request.

**Không** gọi `collectList()` trên Flux vô hạn, vì Publisher không complete nên list không bao giờ phát và có thể dùng memory ngày càng lớn. Với live stream cần:
- Periodic heartbeat/đặc tả idle behavior.
- Idle timeouts phù hợp ở server/proxy/client.
- Client cancellation cleanup.
- Event retention/replay policy nếu cần resume.
- Backpressure/sampling/drop policy nếu producer nhanh hơn consumer.
- Hạn chế số concurrent connections.

## 6. SSE khác NDJSON ngay cả khi data là JSON

WebFlux hỗ trợ `ServerSentEvent<T>` thuộc `org.springframework.http.codec`:

```java
@GetMapping(path = "/sse",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<Event>> sse() {
    return source.eventsWithDelay()
            .map(value -> ServerSentEvent.<Event>builder()
                    .id(Long.toString(value.id()))
                    .event("change")
                    .data(value)
                    .build());
}
```

Đây là snippet với `source` đã có. Server-Sent Events cho browser `EventSource` metadata hữu ích như `id` và `event`, hỗ trợ reconnect. NDJSON thuần **không** có protocol chuẩn tương đương `Last-Event-ID`; muốn resume cần contract dựa trên offset/sequence/checkpoint riêng.

### SSE heartbeat

Trong stream dài hạn, nếu không phát gì quá lâu, broken connections có thể chỉ bị phát hiện chậm; đồng thời reverse proxy có thể idle-timeout. SSE có thể phát comment-only heartbeat:

```text
: keep-alive

```

Khi viết WebFlux SSE phức tạp, có thể kết hợp event publisher với một ticker phát comment-only `ServerSentEvent`, nhưng cần tránh gửi heartbeat vào cùng stream khi business contract không mong đợi. Client parser phải xử lý comments như SSE protocol định nghĩa.

### Đừng chọn SSE cho mọi streaming API

SSE tiện cho browser notification nhưng thường không phù hợp khi:
- Client cần POST một stream dữ liệu lên server.
- Cần binary data.
- Cần giao thức hai chiều với command/data flows.
- Client cần một NDJSON export được xử lý bằng Unix line tools.

Ngược lại, NDJSON thường phù hợp cho server-to-server ETL/log exports mà không cần EventSource behavior.

## 7. Chọn media type dựa trên nhu cầu của client

| Nhu cầu | Nên bắt đầu bằng | Vì sao |
| --- | --- | --- |
| Một DTO | `application/json` + `Mono<DTO>` | Một JSON object |
| Một list nhỏ (tối đa vài trăm phần tử theo policy) | `application/json` + list/Flux hữu hạn | Client parsing đơn giản |
| Export lớn cần xử lý từng record | `application/x-ndjson` + `Flux<DTO>` | Streaming per-line |
| Browser dashboard nhận thông báo | `text/event-stream` + SSE events | EventSource, id/reconnect |
| Client POST nhiều records đến dần | NDJSON request + `@RequestBody Flux<DTO>` | Producer/consumer stream |
| Một batch transactional cần validate trước | `application/json` array + `Mono<List<DTO>>`/wrapper | Dễ atomic validation/response |
| File raw bytes | `application/octet-stream` hoặc file-specific type | Không sai framing |
| Protocol JSON sequence theo RFC 7464 | `application/json-seq` | RS framing theo spec, không phải NDJSON |
| GraphQL/gRPC/WebSocket | Protocol được thiết kế riêng | Không trá hình thành NDJSON |
| Client chỉ gọi `response.json()` | `application/json` | NDJSON toàn body không phải JSON document |

**Câu hỏi trước khi thiết kế:** client có đọc incremental không; nếu server trả hàng nghìn items, client có đủ bộ nhớ không; có cần per-record ack/retry không; có cần thứ tự; có cần resume và event IDs; kết nối có proxy buffering không; status phải biết khi nào; error xảy ra nửa chừng sẽ biểu diễn ra sao?

## 8. Content negotiation: Accept, Content-Type, produces, consumes

`@GetMapping(produces=...)` ràng buộc response representation theo Accept; `@PostMapping(consumes=...)` ràng buộc media type request body theo Content-Type.

```java
@GetMapping(path = "/ndjson",
        produces = MediaType.APPLICATION_NDJSON_VALUE)
public Flux<Event> ndjson() {
    return source.events();
}

@PostMapping(path = "/ingest",
        consumes = MediaType.APPLICATION_NDJSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
public Mono<IngestReceipt> ingest(
        @RequestBody Flux<NewEvent> body) {
    return service.accept(body);
}
```

Đây là snippets với classes/service của ví dụ. Đừng tráo:
- `Accept: application/x-ndjson` nghĩa client muốn **nhận** NDJSON.
- `Content-Type: application/x-ndjson` nghĩa client **đang gửi** NDJSON.
- `produces` mô tả response format.
- `consumes` mô tả request-body format.

Ví dụ:
- GET NDJSON endpoint với `Accept: text/plain` có thể nhận **406 Not Acceptable**.
- POST tới NDJSON-only endpoint với `Content-Type: application/json` có thể nhận **415 Unsupported Media Type**.
- JSON malformed hoặc NDJSON line sai cú pháp thường thành **400 Bad Request**, tùy error handling.
- Với browser fetch, nếu có credentials/cross-origin, còn có CORS preflight policy.

HTTP clients có thể gửi `Accept: */*`. Khi có nhiều mapping cùng path phân biệt bằng media type, phải kiểm tra content negotiation/ordering để tránh một client tình cờ nhận format không mong đợi. Với API contract lâu dài, explicit paths hoặc headers có thể dễ quản lý hơn implicit negotiation khi streaming vs non-streaming khác semantics mạnh.

## 9. Đọc request body NDJSON bằng @RequestBody Flux<T>

Với NDJSON input:

```http
POST /api/streams/ingest HTTP/1.1
Content-Type: application/x-ndjson

{"message":"first"}
{"message":"second"}
```

Controller có thể khai báo:

```java
@PostMapping(path = "/ingest",
        consumes = MediaType.APPLICATION_NDJSON_VALUE)
public Mono<Receipt> ingest(
        @RequestBody Flux<NewEvent> incoming) {

    return incoming
            .map(this::validate)
            .collectList()
            .map(events -> new Receipt(events.size()));
}
```

Đây là **snippet** có `Receipt` và `validate` giả định. `HttpMessageReader`/Jackson decoder tạo ra `NewEvent` theo framing NDJSON. Nhưng `@RequestBody Flux<T>` **không tự bảo đảm**:
- Dữ liệu được giới hạn records/bytes.
- Tất cả field constraints được validate như mong muốn.
- Tất cả events sẽ được persisted atomically.
- Client disconnect sẽ rollback side effects đã hoàn thành.
- Mỗi raw network packet chứa đúng một JSON object.

### Lựa chọn: xử lý từng record hay gom batch?

**Xử lý từng record:**

```java
return incoming
        .concatMap(eventStore::save)
        .count();
```

Snippet với `eventStore.save` trả `Mono<?>`. `concatMap` bảo toàn processing order theo thứ tự đầu vào và giới hạn concurrency theo cách tuần tự. Nhưng nếu record thứ 5 lỗi, 4 record đầu có thể đã lưu: **không atomic**.

**Gom batch có giới hạn:**

```java
return incoming.take(6)
        .collectList()
        .flatMap(list -> {
            if (list.size() > 5) {
                return Mono.error(new ResponseStatusException(
                        HttpStatus.PAYLOAD_TOO_LARGE,
                        "Too many records"));
            }
            return validateThenSave(list);
        });
```

Snippet với `validateThenSave` giả định. Nếu chấp nhận tối đa 5 records, cần đọc tối đa **6** để phát hiện vượt hạn; `take(5)` rồi tự ý chấp nhận 5 đầu sẽ âm thầm làm mất phần dư. Sau đó validate *toàn bộ* trước khi side effect nếu contract đòi tính toàn vẹn.

Giới hạn số records **không đủ** bảo vệ server trước một JSON object khổng lồ. Cần thêm byte limits, JSON nesting limits, timeouts và business quotas.

## 10. Lỗi giữa stream: HTTP status đã commit thì sao?

Giả sử server đã gửi:

```text
HTTP/1.1 200 OK
Content-Type: application/x-ndjson

{"id":1,"type":"order","message":"created"}
{"id":2,"type":"order","message":"paid"}
```

Sau đó source phát `Mono.error(new IOException(...))` trước record thứ 3. Trong HTTP, status và headers thường **đã được commit**. Framework không thể đảm bảo biến stream trước đó thành một response mới `500 application/problem+json`; client có thể chỉ thấy kết nối đóng đột ngột, body thiếu hoặc protocol-level error.

Vì vậy với NDJSON export:
- Document rõ rằng stream có thể incomplete.
- Dùng stable event IDs/sequence để client phát hiện missing records.
- Nếu cần resume, thiết kế checkpoint/`?afterId=...` và retention policy.
- Nếu cần toàn-or-nothing, đừng bắt đầu response trước khi validate/compute xong; chọn JSON array hoặc trả job ID để tải file đã chuẩn bị.
- Logging/metrics ghi nhận `onError` và `CANCEL` khác với HTTP 5xx.
- Không retry một stream nhập dữ liệu có side effects nếu thiếu idempotency.

### Những thứ không nên làm

```java
// PHẢN VÍ DỤ: mọi lỗi đều được che bằng Flux.empty()
return source.eventsWithDelay()
        .onErrorResume(error -> Flux.empty());
```

Nếu server đã gửi 2/3 records mà operator chuyển lỗi thành complete bình thường, client có thể lầm tưởng export đã hoàn tất. Chỉ dùng fallback nếu protocol/business contract cho phép, phải có dấu hiệu completeness/checksum/end marker thích hợp.

### Thiết kế sentinel kết thúc khi bắt buộc

Một số protocol xây event wrapper:

```json
{"type":"row","data":{"id":1}}
{"type":"row","data":{"id":2}}
{"type":"end","count":2}
```

Đây **không phải NDJSON mặc định của Spring**, mà là một application-specific contract. Client phải chỉ coi stream đầy đủ khi thấy `type=end` và kiểm tra count/checksum; nếu socket đóng trước marker thì retry/resume. Không tự gửi marker giả khi upstream fail.

## 11. Reactive backpressure và quan hệ với HTTP transport

Reactive Streams có `request(n)`/demand và cancellation. Khi HTTP response consumer chậm, pipeline có thể giảm demand từ upstream nếu codecs/server/runtime hỗ trợ đúng. Nhưng:
- `Flux.create` đẩy events từ callback không giới hạn có thể tích tụ memory.
- `onBackpressureBuffer()` không giới hạn có thể OOM.
- `collectList()` trên stream vô hạn không complete và tăng memory.
- `flatMap` với concurrency cao có thể làm đổi ordering hoặc quá tải downstream.
- `publishOn`/`subscribeOn` chỉ chuyển scheduler; không làm đồng bộ JDBC trở thành non-blocking.
- File reads/DB drivers có blocking APIs nếu application sử dụng những APIs đó.

### Blocking example

```java
// Không nên làm trong WebFlux event-loop path:
return source.events()
        .map(event -> blockingJdbcRepository.save(event));
```

Đây là snippet với repository giả định; JDBC call vẫn chiếm thread đang thực thi. Nếu phải dùng blocking driver trong thời gian chuyển đổi:

```java
return source.events()
        .concatMap(event ->
                Mono.fromCallable(
                        () -> blockingJdbcRepository.save(event))
                    .subscribeOn(Schedulers.boundedElastic()));
```

Cách này offload sang bounded workers, **không biến JDBC non-blocking**. Đặt connection pool, timeouts, concurrency và cancellation strategy. Với pipeline cần non-blocking end-to-end, dùng client/driver reactive phù hợp.

### Order và consistency

Với events theo sequence, `concatMap` bảo toàn processing order; `flatMap` mặc định có thể hoàn thành out-of-order. Nếu stream phản ánh một aggregate transaction sequence, contract phải nêu rõ ordering. HTTP/TCP giữ thứ tự bytes trên một connection, nhưng upstream merge/flatMap có thể làm dữ liệu **được tạo** sai thứ tự trước khi encode.

## 12. Bảo mật và vận hành stream

### Authentication/authorization

Một endpoint export NDJSON có thể trả hàng nghìn records nhạy cảm. Cần:
- Spring Security reactive authentication/authorization trước khi bắt đầu stream.
- Per-resource/tenant access checks, tránh IDOR.
- Không lấy `X-User` hoặc `X-Role` do client tự gửi làm identity.
- Đảm bảo quyền không bị bỏ qua bởi một custom WebFilter tạo response stream.
- Nếu stream dài và token hết hạn/ACL thay đổi, có business policy rõ cho việc refresh/terminate stream.

### Privacy

Một record NDJSON vẫn có thể chứa PII/secrets; newline delimiter không tự mask. Đặt `Cache-Control: no-store` hoặc cache policy thích hợp nếu dữ liệu cá nhân, tránh shared CDN cache. Không log nguyên response body hoặc `Authorization` header. Nếu gửi NDJSON qua file export, kiểm soát thời gian tồn tại, signed URL TTL và audit access.

### Injection và parser safety

NDJSON là JSON syntax, không tự chống XSS hoặc injection ở consumer. Nếu consumer chèn `message` vào DOM bằng `innerHTML` thì vẫn có XSS risk; dùng textContent/escaping. Nếu dữ liệu được đưa vào SQL/command downstream, dùng parameterized statements/escaping đúng nơi. Parser cần validate schema, max line length, max JSON depth, Unicode handling và duplicate keys policy.

### DoS/resilience

- Bounded concurrent streams/users.
- Request/input limits theo byte và record count.
- Idle/total duration limits theo endpoint.
- Client disconnect/cancellation cleanup; không giữ publisher/subscription vô hạn.
- Retry/backoff/resume limits để tránh thundering herd.
- Reverse proxy buffering/timeouts phù hợp; không tắt mọi protections để "stream nhanh".
- Metrics: bytes/second, emitted records, dropped items, active connections, p95 first-record latency, error/cancel count.

## 13. Dự án Spring Boot 3.3.5 hoàn chỉnh

Demo gồm các endpoints:

| Endpoint | Method | Request Content-Type | Response Content-Type | Ý nghĩa |
| --- | --- | --- | --- | --- |
| `/api/streams/json` | GET | Không có body | `application/json` | Flux 3 items → JSON array |
| `/api/streams/ndjson` | GET | Không có body | `application/x-ndjson` | Flux 3 items, phát cách nhau ngắn |
| `/api/streams/sse` | GET | Không có body | `text/event-stream` | SSE có ID và event name |
| `/api/streams/live` | GET | Không có body | `application/x-ndjson` | Stream vô hạn, client chủ động cancel |
| `/api/streams/single` | GET | Không có body | `application/json` | Một JSON object |
| `/api/streams/strings` | GET | Không có body | `application/json` | JSON array strings chính xác |
| `/api/streams/ingest` | POST | `application/x-ndjson` | `application/json` | Đọc 1–5 records rồi trả summary, validate explicit |

Không có database. Dữ liệu dùng `List.of` bất biến; `Flux.delayElements`/`Flux.interval` minh họa publisher phát theo thời gian. Không có blocking I/O hay code tự gọi `subscribe()`. Request ingest **không lưu dữ liệu**; chỉ validate và trả summary để tập trung vào codec/framing.

### 13.1. Cây project

```text
webflux-ndjson-demo/
├── pom.xml
└── src/
    ├── main/java/example/streaming/
    │   ├── StreamingApplication.java
    │   ├── StreamEvent.java
    │   ├── IncomingEvent.java
    │   ├── IngestReceipt.java
    │   ├── EventSource.java
    │   └── StreamController.java
    └── test/java/example/streaming/
        └── StreamControllerTest.java
```

### 13.2. pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>
    <groupId>example</groupId>
    <artifactId>webflux-ndjson-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <java.version>17</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

Chỉ thêm `spring-boot-starter-webflux`, **không thêm `spring-boot-starter-web`**, để Spring Boot mặc định chọn reactive web application type. Jackson JSON support đến từ WebFlux starter. Validation starter cung cấp Jakarta Bean Validation `Validator`.

### 13.3. Application class

**`src/main/java/example/streaming/StreamingApplication.java`**

```java
package example.streaming;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class StreamingApplication {
    public static void main(String[] args) {
        SpringApplication.run(StreamingApplication.class, args);
    }
}
```

### 13.4. DTO records

**`src/main/java/example/streaming/StreamEvent.java`**

```java
package example.streaming;

public record StreamEvent(
        long id,
        String type,
        String message) {}
```

**`src/main/java/example/streaming/IncomingEvent.java`**

```java
package example.streaming;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IncomingEvent(
        @NotBlank @Size(max = 80) String message) {}
```

**`src/main/java/example/streaming/IngestReceipt.java`**

```java
package example.streaming;

import java.util.List;

public record IngestReceipt(
        int accepted,
        List<String> messages) {}
```

Java 17 records là DTO bất biến và Jackson có hỗ trợ JSON record serialization/deserialization trong Boot 3.3.5. `@NotBlank` và `@Size` chỉ là validation metadata; demo sẽ **gọi `Validator.validate(...)` chủ động**, thay vì giả định body Flux nhất định đã được kiểm tra theo mọi custom validation policy.

### 13.5. EventSource: nguồn dữ liệu và nhịp phát

**`src/main/java/example/streaming/EventSource.java`**

```java
package example.streaming;

import java.time.Duration;
import java.util.List;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class EventSource {

    private final List<StreamEvent> seed = List.of(
            new StreamEvent(1, "order", "created"),
            new StreamEvent(2, "order", "paid"),
            new StreamEvent(3, "order", "shipped"));

    public Flux<StreamEvent> snapshot() {
        return Flux.fromIterable(seed);
    }

    public Mono<StreamEvent> first() {
        return snapshot().next();
    }

    public Flux<StreamEvent> delayed() {
        return snapshot().delayElements(Duration.ofMillis(50));
    }

    public Flux<StreamEvent> live() {
        return Flux.interval(Duration.ofMillis(150))
                .map(n -> new StreamEvent(
                        n + 100, "heartbeat", "tick-" + n));
    }
}
```

`snapshot()` tạo Flux từ List hữu hạn; `delayed()` phát 3 items cách nhau một khoảng ngắn bằng Reactor scheduler, không `Thread.sleep`; `live()` tạo stream vô hạn để test cancellation. Không giả định thời gian 50 ms/150 ms là deadline cứng: scheduler và OS có jitter. Trong production, nguồn có thể là change stream, message broker hoặc R2DBC; backpressure/cancellation phải được xử lý tại nguồn thật.

### 13.6. Controller: JSON, NDJSON, SSE và ingest

**`src/main/java/example/streaming/StreamController.java`**

```java
package example.streaming;

import java.util.List;

import jakarta.validation.Validator;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/streams")
public class StreamController {

    private final EventSource source;
    private final Validator validator;

    public StreamController(
            EventSource source,
            Validator validator) {
        this.source = source;
        this.validator = validator;
    }

    @GetMapping(
            path = "/json",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Flux<StreamEvent>> json() {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(source.snapshot());
    }

    @GetMapping(
            path = "/ndjson",
            produces = MediaType.APPLICATION_NDJSON_VALUE)
    public ResponseEntity<Flux<StreamEvent>> ndjson() {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_NDJSON)
                .cacheControl(CacheControl.noStore())
                .body(source.delayed());
    }

    @GetMapping(
            path = "/sse",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<StreamEvent>> sse() {
        return source.delayed()
                .map(event -> ServerSentEvent
                        .<StreamEvent>builder()
                        .id(Long.toString(event.id()))
                        .event("change")
                        .data(event)
                        .build());
    }

    @GetMapping(
            path = "/live",
            produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<StreamEvent> live() {
        return source.live();
    }

    @GetMapping(
            path = "/single",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<StreamEvent> single() {
        return source.first();
    }

    @GetMapping(
            path = "/strings",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<List<String>> strings() {
        return Mono.just(List.of("first", "second"));
    }

    @PostMapping(
            path = "/ingest",
            consumes = MediaType.APPLICATION_NDJSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<IngestReceipt>> ingest(
            @RequestBody Flux<IncomingEvent> incoming) {

        return incoming.take(6)
                .collectList()
                .map(records -> {
                    if (records.size() > 5) {
                        throw new ResponseStatusException(
                                HttpStatus.PAYLOAD_TOO_LARGE,
                                "At most five records");
                    }
                    if (records.isEmpty()) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "At least one record required");
                    }
                    for (IncomingEvent record : records) {
                        if (!validator.validate(record).isEmpty()) {
                            throw new ResponseStatusException(
                                    HttpStatus.BAD_REQUEST,
                                    "Invalid event");
                        }
                    }

                    List<String> messages = records.stream()
                            .map(IncomingEvent::message)
                            .toList();

                    return ResponseEntity.ok(
                            new IngestReceipt(
                                    records.size(), messages));
                });
    }
}
```

### Vì sao các endpoints này thể hiện đúng media types?

- `json()` và `ndjson()` đều trả **`ResponseEntity<Flux<StreamEvent>>`**, nên chính `Content-Type`/`produces` làm rõ encoder nên tạo JSON array hay từng NDJSON line.
- `sse()` trả `Flux<ServerSentEvent<...>>` và `text/event-stream`; trên wire sẽ có `id`/`event`/`data` và blank lines thay vì NDJSON thuần.
- `live()` phát liên tục và client phải cancel; không có `collectList()`.
- `single()` dùng Mono cho đúng cardinality một object.
- `strings()` tránh nhầm `Flux<String>` với JSON array string values bằng cách trả một `Mono<List<String>>`.
- `ingest()` nhận NDJSON bằng `@RequestBody Flux<IncomingEvent>`, nhưng **trả JSON object thông thường**; giới hạn 5 records bằng `take(6)`, validate tất cả trước khi tạo receipt.
- `cacheControl(CacheControl.noStore())` gắn header để minh họa dữ liệu streaming/private không nên cache tùy tiện; ở app thật hãy chọn cache policy dựa vào dữ liệu.
- `ResponseStatusException` dùng khi body hợp lệ về cú pháp nhưng vi phạm quy tắc batch; lỗi decoder/malformed NDJSON xử lý qua WebFlux default error handling và không tự trở thành receipt.

**Lưu ý:** Demo ingest chỉ đếm/kiểm tra, không persist dữ liệu. Nếu thêm DB, phải thiết kế transactional/idempotent semantics; `collectList()` không phải một DB transaction. Nếu server bị client gửi một record khổng lồ, giới hạn 5 records không thay giới hạn request bytes.

## 14. Chạy project và quan sát wire format

Cài JDK 17+ và Maven. Từ thư mục `webflux-ndjson-demo`:

```bash
mvn test
mvn spring-boot:run
```

Hai lệnh chạy **tuần tự**. Sau khi Boot khởi động, mặc định listen tại `http://localhost:8080` nếu chưa đổi `server.port`.

### 14.1. Regular JSON array

```bash
curl -i \
  -H 'Accept: application/json' \
  'http://localhost:8080/api/streams/json'
```

Các phần chính của response:

```http
HTTP/1.1 200 OK
Content-Type: application/json

[{"id":1,"type":"order","message":"created"},{"id":2,"type":"order","message":"paid"},{"id":3,"type":"order","message":"shipped"}]
```

JSON serializer có thể chọn spacing và header bổ sung khác. Điều cần kiểm tra là **một root array có 3 items**, không phải 3 JSON documents nối bởi newlines.

### 14.2. NDJSON output

```bash
curl -i -N \
  -H 'Accept: application/x-ndjson' \
  'http://localhost:8080/api/streams/ndjson'
```

Dự kiến body:

```text
{"id":1,"type":"order","message":"created"}
{"id":2,"type":"order","message":"paid"}
{"id":3,"type":"order","message":"shipped"}
```

`curl -N` giảm buffering ở phía curl để dễ quan sát dữ liệu đến theo thời gian; nó **không** tắt buffering tại proxy/server. Với decoder của Spring 6.1, NDJSON output được tạo theo từng value và newline separator. Không đồng nhất số HTTP chunks với số JSON lines.

### 14.3. SSE output

```bash
curl -i -N \
  -H 'Accept: text/event-stream' \
  'http://localhost:8080/api/streams/sse'
```

SSE body có dạng:

```text
id:1
event:change
data:{"id":1,"type":"order","message":"created"}

id:2
event:change
data:{"id":2,"type":"order","message":"paid"}

id:3
event:change
data:{"id":3,"type":"order","message":"shipped"}

```

Một số emitter/formatters có thể sử dụng dấu cách sau colon; parser SSE không nên phụ thuộc spacing đó. Điểm phân biệt là **trường `data:` và blank line kết thúc event**, không phải chỉ có newline giữa JSON objects.

### 14.4. Live NDJSON và hủy kết nối

```bash
curl -N \
  -H 'Accept: application/x-ndjson' \
  'http://localhost:8080/api/streams/live'
```

Đây là **stream vô hạn**. Dùng `Ctrl+C` để ngắt client khi quan sát đủ vài dòng. Không dùng một JSON parser đợi `]` kết thúc; stream không có một outer array. Với API thật, application cần dispose subscriptions và cleanup upstream resources khi client cancel.

### 14.5. POST NDJSON input, nhận một JSON receipt

Trên Bash:

```bash
curl -i -X POST \
  'http://localhost:8080/api/streams/ingest' \
  -H 'Content-Type: application/x-ndjson' \
  -H 'Accept: application/json' \
  --data-binary $'{"message":"alpha"}\n{"message":"beta"}\n'
```

Dự kiến:

```http
HTTP/1.1 200 OK
Content-Type: application/json

{"accepted":2,"messages":["alpha","beta"]}
```

Trong PowerShell, cú pháp `$'...'` của Bash không hoạt động; hãy tạo file `input.ndjson` chứa đúng hai dòng rồi gọi:

```bash
curl -i -X POST \
  'http://localhost:8080/api/streams/ingest' \
  -H 'Content-Type: application/x-ndjson' \
  --data-binary '@input.ndjson'
```

Đây là ý tưởng portable qua shell có `curl` thật, nhưng trên Windows PowerShell `curl` có thể là alias khác; dùng `curl.exe` khi cần.

### 14.6. Test lỗi Content-Type và Accept

```bash
curl -i -X POST \
  'http://localhost:8080/api/streams/ingest' \
  -H 'Content-Type: application/json' \
  -d '[{"message":"wrong-format"}]'

curl -i \
  -H 'Accept: text/plain' \
  'http://localhost:8080/api/streams/ndjson'
```

Endpoint ingest khai báo `consumes=application/x-ndjson` nên request `application/json` không khớp, thông thường nhận **415 Unsupported Media Type**. NDJSON GET khai báo `produces=application/x-ndjson`, client chỉ Accept `text/plain` thường nhận **406 Not Acceptable**.

### 14.7. Test validation trên một NDJSON line

```bash
curl -i -X POST \
  'http://localhost:8080/api/streams/ingest' \
  -H 'Content-Type: application/x-ndjson' \
  --data-binary $'{"message":"   "}\n'
```

Dự kiến **400 Bad Request** vì `@NotBlank` được kiểm tra thủ công qua `Validator` trong handler. Error body mặc định của Spring Boot có thể khác theo configuration; demo không tuyên bố một JSON error schema cụ thể cho nó.

## 15. WebTestClient: kiểm chứng JSON array, NDJSON framing và streaming

Tập hợp tests dưới đây chạy `RANDOM_PORT`, kiểm thử toàn bộ reactive HTTP server pipeline, codecs và content negotiation chứ không chỉ gọi Java method. Các tests dùng dữ liệu bất biến nên không phụ thuộc thứ tự thực thi.

**`src/test/java/example/streaming/StreamControllerTest.java`**

```java
package example.streaming;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment =
        SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class StreamControllerTest {

    @Autowired
    WebTestClient client;

    @Test
    void regularJsonReturnsOneArrayWithThreeEvents() {
        client.get().uri("/api/streams/json")
              .accept(MediaType.APPLICATION_JSON)
              .exchange()
              .expectStatus().isOk()
              .expectHeader().contentTypeCompatibleWith(
                      MediaType.APPLICATION_JSON)
              .expectBodyList(StreamEvent.class)
              .hasSize(3);
    }

    @Test
    void ndjsonHasThreeNonemptyLines() {
        client.get().uri("/api/streams/ndjson")
              .accept(MediaType.APPLICATION_NDJSON)
              .exchange()
              .expectStatus().isOk()
              .expectHeader().contentTypeCompatibleWith(
                      MediaType.APPLICATION_NDJSON)
              .expectBody()
              .consumeWith(result -> {
                  byte[] bytes = result.getResponseBody();
                  assertNotNull(bytes);

                  String body = new String(
                          bytes, StandardCharsets.UTF_8);
                  List<String> lines = body.lines()
                          .filter(line -> !line.isBlank())
                          .toList();

                  assertEquals(3, lines.size());
                  assertTrue(lines.get(0).contains("\"id\":1"));
                  assertTrue(lines.get(1).contains("\"id\":2"));
                  assertTrue(lines.get(2).contains("\"id\":3"));
              });
    }

    @Test
    void reactiveDecoderReadsNdjsonItems() {
        Flux<StreamEvent> events = client.get()
                .uri("/api/streams/ndjson")
                .accept(MediaType.APPLICATION_NDJSON)
                .exchange()
                .expectStatus().isOk()
                .returnResult(StreamEvent.class)
                .getResponseBody();

        StepVerifier.create(events)
                .expectNextMatches(event -> event.id() == 1)
                .expectNextMatches(event -> event.id() == 2)
                .expectNextMatches(event -> event.id() == 3)
                .verifyComplete();
    }

    @Test
    void sseResponseUsesEventFraming() {
        client.get().uri("/api/streams/sse")
              .accept(MediaType.TEXT_EVENT_STREAM)
              .exchange()
              .expectStatus().isOk()
              .expectHeader().contentTypeCompatibleWith(
                      MediaType.TEXT_EVENT_STREAM)
              .expectBody()
              .consumeWith(result -> {
                  byte[] bytes = result.getResponseBody();
                  assertNotNull(bytes);
                  String text = new String(
                          bytes, StandardCharsets.UTF_8);
                  assertTrue(text.contains("id:"));
                  assertTrue(text.contains("event:"));
                  assertTrue(text.contains("data:"));
              });
    }

    @Test
    void ingestFluxOfTwoNdjsonObjects() {
        client.post().uri("/api/streams/ingest")
              .contentType(MediaType.APPLICATION_NDJSON)
              .accept(MediaType.APPLICATION_JSON)
              .body(Flux.just(
                      new IncomingEvent("alpha"),
                      new IncomingEvent("beta")),
                      IncomingEvent.class)
              .exchange()
              .expectStatus().isOk()
              .expectBody()
              .jsonPath("$.accepted").isEqualTo(2)
              .jsonPath("$.messages[0]").isEqualTo("alpha")
              .jsonPath("$.messages[1]").isEqualTo("beta");
    }

    @Test
    void ingestRejectsInvalidRecord() {
        client.post().uri("/api/streams/ingest")
              .contentType(MediaType.APPLICATION_NDJSON)
              .body(Flux.just(new IncomingEvent("   ")),
                      IncomingEvent.class)
              .exchange()
              .expectStatus().isBadRequest();
    }

    @Test
    void ingestRejectsMoreThanFiveRecords() {
        client.post().uri("/api/streams/ingest")
              .contentType(MediaType.APPLICATION_NDJSON)
              .body(Flux.range(0, 6)
                      .map(n -> new IncomingEvent("m" + n)),
                      IncomingEvent.class)
              .exchange()
              .expectStatus().isEqualTo(413);
    }

    @Test
    void rejectsWrongRequestContentType() {
        client.post().uri("/api/streams/ingest")
              .contentType(MediaType.APPLICATION_JSON)
              .bodyValue("{\"message\":\"one\"}")
              .exchange()
              .expectStatus().isEqualTo(415);
    }

    @Test
    void rejectsUnacceptableResponseType() {
        client.get().uri("/api/streams/ndjson")
              .accept(MediaType.TEXT_PLAIN)
              .exchange()
              .expectStatus().isEqualTo(406);
    }

    @Test
    void infiniteStreamCanBeCancelled() {
        Flux<StreamEvent> stream = client.get()
                .uri("/api/streams/live")
                .accept(MediaType.APPLICATION_NDJSON)
                .exchange()
                .expectStatus().isOk()
                .returnResult(StreamEvent.class)
                .getResponseBody();

        StepVerifier.create(stream)
                .expectNextCount(2)
                .thenCancel()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void stringArrayIsValidJsonArray() {
        client.get().uri("/api/streams/strings")
              .accept(MediaType.APPLICATION_JSON)
              .exchange()
              .expectStatus().isOk()
              .expectBody()
              .jsonPath("$[0]").isEqualTo("first")
              .jsonPath("$[1]").isEqualTo("second");
    }
}
```

Bộ test có **11 test methods**. Khi source files được tạo đúng và các tests pass, Maven thường báo tổng `Tests run: 11, Failures: 0, Errors: 0` (thông tin có thể thay đổi theo test runner). **Mã được rà soát tĩnh; trong quá trình viết Knowledge lên Google Sheets, không chạy Maven runtime.**

### Test nào xác minh điều gì?

- `regularJsonReturnsOneArrayWithThreeEvents`: list kết quả là một JSON array có 3 DTOs.
- `ndjsonHasThreeNonemptyLines`: HTTP raw bytes chứa ba dòng riêng, đúng thứ tự events.
- `reactiveDecoderReadsNdjsonItems`: NDJSON response được WebTestClient decode thành ba `StreamEvent` theo Reactive Streams.
- `sseResponseUsesEventFraming`: có các fields `id:`, `event:`, `data:`; không đồng nhất với NDJSON.
- `ingestFluxOfTwoNdjsonObjects`: WebTestClient tự encode Flux đầu vào thành NDJSON và server decode thành Flux DTO.
- `ingestRejectsInvalidRecord`: Jakarta Validator từ chối message chỉ có whitespace.
- `ingestRejectsMoreThanFiveRecords`: giới hạn record count; 6 items trả 413.
- `rejectsWrongRequestContentType`: `consumes` của POST không chấp nhận application/json.
- `rejectsUnacceptableResponseType`: `produces` của GET không chấp nhận text/plain.
- `infiniteStreamCanBeCancelled`: client nhận hai items rồi hủy; không block đợi stream vô hạn complete.
- `stringArrayIsValidJsonArray`: Mono<List<String>> có JSON array semantics.

### Vì sao không assert mỗi HTTP chunk là một record?

Một NDJSON line có thể bị chia giữa nhiều TCP packets hoặc nhiều lines có thể đi trong một packet. Kiểm thử phải parse bằng **newline delimiter** hoặc codec, không dựa vào HTTP chunk count.

### Vì sao StepVerifier quan trọng cho stream vô hạn?

Nếu dùng `expectBody()` đối với stream không bao giờ complete, test có thể chờ mãi hoặc timeout. `StepVerifier` với `thenCancel()` cho phép thực hiện một kịch bản client thật: nhận vài phần tử rồi đóng subscription.

### Kiểm thử bổ sung khi dùng production

- Slow downstream consumer, proxy buffering, gzip/HTTP2 interactions.
- Client disconnect trước record cuối và cleanup upstream.
- Stream error sau record đầu, incomplete export detection.
- 1000+ lines, max line/JSON nesting/byte size.
- UTF-8 split giữa các network reads.
- Invalid JSON ở dòng thứ 2.
- Sensitive data/cache directives.
- Phân quyền theo tenant/owner.
- Timeout/keepalive ở proxy và server.
- Resume từ event sequence hoặc offset.
- Backpressure trên broker/DB thật, không chỉ in-memory Flux.

## 16. Client parser: cách đọc NDJSON an toàn

### 16.1. Python client đọc từng dòng

Ví dụ client Python dựa trên requests `stream=True`:

```python
import json
import requests

url = "http://localhost:8080/api/streams/ndjson"

with requests.get(
    url,
    headers={"Accept": "application/x-ndjson"},
    stream=True,
    timeout=(5, 30),
) as response:
    response.raise_for_status()
    for line in response.iter_lines(decode_unicode=True):
        if not line:
            continue
        record = json.loads(line)
        print(record["id"], record["message"])
```

Đây là ví dụ runnable khi cài package `requests`; output ID/message dự kiến là:

```text
1 created
2 paid
3 shipped
```

Không dùng `response.json()` cho toàn NDJSON body: nó không phải một JSON document duy nhất.

### 16.2. JavaScript fetch stream parser

Browser `fetch` có thể đọc `ReadableStream` theo chunks. Cần ghép partial chunks rồi tách theo newline:

```javascript
async function readNdjson(url, onRecord) {
  const response = await fetch(url, {
    headers: { Accept: "application/x-ndjson" }
  });
  if (!response.ok) {
    throw new Error("HTTP " + response.status);
  }
  if (!response.body) {
    throw new Error("Streaming body unavailable");
  }

  const reader = response.body
    .pipeThrough(new TextDecoderStream())
    .getReader();

  let pending = "";
  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    pending += value;

    let newline;
    while ((newline = pending.indexOf("\n")) >= 0) {
      const line = pending.slice(0, newline).trim();
      pending = pending.slice(newline + 1);
      if (line.length > 0) {
        onRecord(JSON.parse(line));
      }
    }
  }

  // Chấp nhận dòng cuối không có newline khi protocol cho phép.
  if (pending.trim() !== "") {
    onRecord(JSON.parse(pending.trim()));
  }
}

readNdjson("/api/streams/ndjson", event => {
  console.log(event.id, event.message);
});
```

Đây là snippet thực tế cho browser hỗ trợ `fetch` streams và `TextDecoderStream`. Đối với production:
- Giới hạn `pending.length` để tránh một dòng độc hại vô hạn gây OOM.
- Nếu client yêu cầu newline cuối để xác nhận integrity, **không** tự coi pending cuối là record hoàn chỉnh.
- Xử lý AbortController/cancellation, timeouts, retries và CORS credentials.
- Với DOM, dùng `textContent` thay `innerHTML` để tránh XSS từ message.
- Dùng parser biết xử lý CRLF (`\r\n`), whitespace và invalid lines theo contract.
- Trong môi trường reverse proxy, lần read() không đồng nghĩa một record.

### 16.3. Unix command-line tools

`curl -N` + line-oriented processors thuận tiện cho NDJSON. Ví dụ với `jq`:

```bash
curl -sS -N \
  -H 'Accept: application/x-ndjson' \
  'http://localhost:8080/api/streams/ndjson' \
  | jq -r '.id | tostring'
```

Nếu server trả 3 records như seed, stdout:

```text
1
2
3
```

Lưu ý pipeline phụ thuộc `jq` được cài; đây là ví dụ tiện ích CLI, không phải bước bắt buộc chạy Spring app.

## 17. Lựa chọn ResponseEntity khi stream có điều kiện

Nếu status/headers đã biết trước khi phát stream:

```java
public ResponseEntity<Flux<StreamEvent>> export() {
    return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_NDJSON)
            .body(source.delayed());
}
```

Nếu phải kiểm tra quyền/quota hoặc resource existence **bất đồng bộ trước khi trả status**, chọn `Mono<ResponseEntity<Flux<StreamEvent>>>`:

```java
public Mono<ResponseEntity<Flux<StreamEvent>>> export() {
    return permissions.canExport()
            .map(allowed -> {
                if (!allowed) {
                    return ResponseEntity.status(
                            HttpStatus.FORBIDDEN)
                            .<Flux<StreamEvent>>build();
                }
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_NDJSON)
                        .body(source.delayed());
            });
}
```

Hai đoạn là snippets với `permissions`, `source` giả định. Điểm cần nhớ:

- `ResponseEntity<Flux<T>>`: HTTP envelope (status/headers) được chọn ngay khi controller return.
- `Mono<ResponseEntity<Flux<T>>>`: HTTP envelope được chọn sau Mono check async; body Flux có thể stream sau.
- `Mono<ResponseEntity<T>>`: phù hợp request đơn cần chọn 200/404/201 theo result.
- Nếu stream đã bắt đầu, error sau commit không tự biến 200 thành 500.

Trong production, check quyền không chỉ là một boolean; cần Spring Security reactive authentication và kiểm tra resource/tenant tại service boundary. Không lấy `X-Role` do client gửi làm "admin".

## 18. Khi nào nên trả job ID thay vì giữ stream?

Không phải mọi export dài đều cần HTTP live stream. Nếu tạo báo cáo 30 phút, phải truy cập một DB rất đắt hoặc cần tệp có checksum ổn định, phương án phù hợp hơn có thể là:

1. `POST /exports` trả **202 Accepted** cùng job ID.
2. Worker riêng xây export file vào storage có lifecycle.
3. `GET /exports/{id}` trả trạng thái, checksum và metadata.
4. Khi hoàn tất, client tải một file `application/x-ndjson` hoặc `application/json` có content length/file integrity rõ.
5. Có TTL, access controls, audit, retry, idempotency.

Streaming hữu ích khi cần dữ liệu incremental thật sự, nhưng kết nối HTTP mở lâu tiêu thụ sockets, buffers và tracking state; proxy/CDN có thể timeout. "Reactive" không đồng nghĩa việc giữ vô hạn một HTTP connection luôn tốt hơn thiết kế job-based.

## 19. Sai lầm thường gặp và cách sửa

| Hiện tượng | Nguyên nhân phổ biến | Cách sửa |
| --- | --- | --- |
| Flux tạo 1 JSON array thay vì từng dòng | Response `application/json` | `produces=application/x-ndjson` và compatible codec |
| Client gọi JSON.parse(wholeBody) thất bại | Body là NDJSON nhiều JSON texts | Parse từng newline-delimited line |
| Client cứ chờ, không thấy record đầu | JSON codec gom Flux; proxy/client buffering | NDJSON/SSE, flush + đo client-side |
| Một chunk chứa nửa JSON object | Transport chunks không phải record framing | Giữ buffer đến newline |
| Chuỗi "first" không thành JSON array | `Flux<String>` được writer xử lý như text | `Mono<List<String>>` cho array |
| SSE client không nhận NDJSON | EventSource yêu cầu text/event-stream | Dùng `Flux<ServerSentEvent<T>>` |
| NDJSON parser hiểu sai json-seq | JSON-seq dùng RS delimiter | Chọn đúng spec/framing |
| 406 Not Acceptable | `Accept` không khớp `produces` | Gửi header phù hợp hoặc cung cấp representation khác |
| 415 Unsupported Media Type | `Content-Type` không khớp `consumes` | Đổi đúng request media type |
| 400 khi POST NDJSON | JSON line hỏng/DTO fail validation | Validate line và input schema, logs an toàn |
| Client gửi 6 records nhưng API chỉ nhận 5 | Thiếu record count limit policy | `take(6)` + reject >5 |
| API chấp nhận 5, bỏ dòng thứ 6 | Dùng `take(5)` truncate im lặng | Đọc thêm 1 để phát hiện overflow |
| Stream trả 200 dù lỗi ở giữa | Response đã commit | Resume/end marker/error protocol |
| App memory tăng với stream | `collectList()`/unbounded buffer | Bounded stream và limits |
| Thread pool nghẽn dù dùng Flux | Có JDBC/file/network blocking ở operator | Driver async hoặc bounded offload |
| Mất ordering | `flatMap` concurrency | `concatMap` hoặc sequence-aware merge |
| Proxy giữ toàn bộ response | Proxy buffering/compression | Cấu hình/tracing end-to-end cho stream |
| NDJSON lộ dữ liệu user | Không auth/tenant check/cache policy | Security + privacy + no-store |
| Client mất dữ liệu sau reconnect | Không có sequence/checkpoint | Event IDs, offsets, resume semantics |
| SSE id không hồi phục đúng | Server không xử lý Last-Event-ID | Retention/replay policy |
| Charset lỗi với Unicode split packets | Decode từng binary chunk độc lập | Streaming TextDecoder |
| 413 không xuất hiện như mong muốn | Giới hạn tại codec/proxy khác | Test 413/400 và own policy |
| Request body không đọc được | WebFilter subscribe raw DataBuffer trước | Không double-read, decorator đúng |
| NDJSON record gửi JSON array một dòng | Định dạng client sai | Một JSON object trên mỗi dòng |
| Streaming tests đứng mãi | Dùng expectBody() cho Flux vô hạn | StepVerifier + thenCancel |
| Throughput thấp | Downstream chậm, nhiều buffered items | Đo source/encoder/network/client separately |

## 20. Checklist triển khai thật

- [ ] Biết rõ client cần JSON document, NDJSON hay SSE.
- [ ] Chọn `produces`/`consumes` và status/headers nhất quán.
- [ ] Test `Accept`/`Content-Type` cùng 406/415 behavior.
- [ ] Đừng đồng nhất `Flux<T>` với "streaming on wire".
- [ ] Không giả định HTTP chunks/frames bằng JSON lines.
- [ ] Có NDJSON decoder phía client với buffer, UTF-8 và newline handling.
- [ ] Đặt giới hạn số items, bytes/line, JSON depth và request timeout.
- [ ] Không dùng `collectList()` cho stream không bound/vô hạn.
- [ ] Check validation/auth/tenant trước side effects và response commit.
- [ ] Dùng `ResponseEntity<Flux<T>>` hoặc `Mono<ResponseEntity<Flux<T>>>` đúng thời điểm chọn status.
- [ ] Không kỳ vọng đổi status sau partial writes.
- [ ] Có schema, ordering, idempotency, retry/resume contract.
- [ ] Streaming errors/timeout/cancel đều được logs/metrics phân loại.
- [ ] Không block event loop, không tự subscribe và không read body hai lần.
- [ ] Backpressure/buffer/cancellation ở upstream thật.
- [ ] Nếu dùng SSE, có reconnect/Last-Event-ID/heartbeat policy.
- [ ] Nếu sau reverse proxy, kiểm tra buffering, HTTP/2, TLS, compression.
- [ ] Không log PII/tokens/raw event payload, không cache private data công khai.
- [ ] Test list JSON, NDJSON raw lines, typed decoder, live cancellation và SSE framing.
- [ ] Benchmark qua production-like deployment; chỉ số server emission không đủ.
- [ ] Đánh giá job-based export khi công việc dài và không cần live interaction.

## 21. Bài tập thực hành

### Bài tập 1 — Thay media type

Đổi annotation của `/ndjson` từ `APPLICATION_NDJSON_VALUE` sang `APPLICATION_JSON_VALUE`, rồi quan sát:
- Content-Type trả về.
- Raw body sau khi 3 items complete.
- Bao lâu client thấy first complete JSON document.
- Điều gì xảy ra nếu thử parse từng dòng NDJSON.

Sau đó trả lại cấu hình NDJSON và viết lại một WebTestClient test chụp raw bytes, không đoán số network chunks.

### Bài tập 2 — Lỗi sau partial stream

Cho `source.delayed()` phát hai items rồi phát một `Flux.error`. Kiểm tra:
- Hai dòng đầu có đến client không?
- HTTP status còn 200 không?
- WebTestClient/curl phản ánh disconnect/error ra sao?
- Có cần end marker để phân biệt complete thành công với truncated stream không?

Không viết test cứng "luôn trả 500" vì response có thể đã commit.

### Bài tập 3 — Batch giới hạn và validation

Thay mức tối đa từ 5 thành 100:
- Dùng `take(101)`.
- Reject khi số phần tử lớn hơn 100, không lặng lẽ truncate.
- Validate mọi object trước khi ghi.
- Thêm max line bytes/request bytes ở runtime/proxy.
- Kiểm tra 100 records hợp lệ, 101 records, dòng số 2 invalid, empty stream và client cancel giữa chừng.

### Bài tập 4 — Browser consumer

Viết `fetch()` + streaming NDJSON reader. Dùng `AbortController` để hủy sau 2 records. Quan sát server logs/`doFinally` signal; đừng lưu entire payload trong browser memory.

### Bài tập 5 — SSE cho dashboard

Tạo một stream sự kiện tên `inventory-change` có `id` và JSON `data`. Dùng EventSource đọc và xử lý reconnect với `Last-Event-ID`. Cần có durable event store hoặc checkpoint mới bảo đảm không mất events, không phải chỉ thêm field `id` là đủ.

## 22. Câu hỏi tự kiểm tra

1. Vì sao `Flux<Event>` chưa đủ để quyết định body trên dây?
2. `application/json` khác `application/x-ndjson` thế nào khi upstream là Flux 3 items?
3. Vì sao `JSON.parse(wholeNdjsonBody)` thường lỗi?
4. NDJSON dùng delimiter nào? JSON Text Sequences (RFC 7464) dùng delimiter nào?
5. `text/event-stream` có phải NDJSON không?
6. `ServerSentEvent` có các fields nào quan trọng?
7. `Accept` và `Content-Type` khác chiều nào?
8. Annotated `produces` và `consumes` dùng để làm gì?
9. Khi nào HTTP status 406 và 415 thường xuất hiện?
10. Vì sao một `Flux<String>` không chắc thành JSON array strings?
11. `MediaType.APPLICATION_NDJSON` giúp điều gì trong codec?
12. `curl -N` có tắt proxy buffering không?
13. Có nên parse NDJSON theo ranh giới TCP packet/HTTP chunk không?
14. NDJSON có built-in Last-Event-ID như SSE không?
15. Status 200 có thể đổi thành 500 JSON sau khi đã gửi 2/3 lines không?
16. Tại sao `collectList()` trên stream vô hạn là lỗi nghiêm trọng?
17. Vì sao phải đọc phần tử thứ 101 để phát hiện batch >100?
18. `@Valid` có thay thế authorization/tenant checks không?
19. Gọi blocking JDBC trong `flatMap` có trở thành non-blocking nhờ Flux không?
20. `Schedulers.boundedElastic` làm thay đổi nơi blocking xảy ra như thế nào?
21. Vì sao `concatMap` hữu ích khi thứ tự sự kiện quan trọng?
22. HTTP status 404 có nhất thiết là một Reactor error signal không?
23. Vì sao cần heartbeat hoặc idle policy cho stream kết nối lâu?
24. Nếu client cancel, ai chịu trách nhiệm cleanup upstream?
25. Vì sao MIME type `application/json-seq` không nên bị gắn nhầm cho NDJSON?
26. WebTestClient cần test gì ngoài status để xác nhận framing?
27. Nếu client cần toàn bộ dữ liệu atomically, có nên stream partial NDJSON không?
28. Dữ liệu user-specific NDJSON nên được cache public ở CDN không?
29. Vì sao client cần buffer partial UTF-8 chunks và line fragments?
30. Khi nào nên dùng `202 Accepted` + job ID thay vì giữ HTTP stream mở?

**Gợi ý:** (1) Publisher type khác wire media type. (2) JSON array vs line-delimited objects/flush. (3) Không phải một JSON document. (4) LF so với ASCII RS. (5) Không, SSE có protocol fields. (6) id/event/data/retry/comment. (7) Nhận vs gửi. (8) Response vs request media predicates. (9) Accept không phù hợp vs request Content-Type không phù hợp. (10) String encoder đặc biệt. (11) Streaming JSON encoder/decoder. (12) Không. (13) Không. (14) Không, phải tự thiết kế resume. (15) Không bảo đảm vì committed. (16) Chờ complete và tăng RAM. (17) Reject chính xác mà không truncate. (18) Không. (19) Không. (20) Offload blocking, không chuyển driver thành non-blocking. (21) Bảo toàn order. (22) Không. (23) Phát hiện disconnect/proxy timeouts. (24) Application/source/client và reactive cancellation pipeline đúng trách nhiệm. (25) Framing khác. (26) Content-Type, raw lines, decoded DTOs, cancel. (27) Cân nhắc bounded JSON/job-based. (28) Không nếu dữ liệu nhạy cảm. (29) Chunks không trùng boundaries. (30) Công việc quá lâu, cần job lifecycle/checksum/retries.

## 23. Tài liệu tham khảo chính thức

- [Spring Framework — WebFlux Reactive Core, HTTP Codecs, Streaming](https://docs.spring.io/spring-framework/reference/web/webflux/reactive-spring.html)
- [Spring Framework — WebFlux Annotated Controllers](https://docs.spring.io/spring-framework/reference/web/webflux/controller.html)
- [Spring Framework — WebFlux @ResponseBody](https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-methods/responsebody.html)
- [Spring Framework — WebFlux @RequestBody](https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-methods/requestbody.html)
- [Spring Framework — WebFlux ResponseEntity return types](https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-methods/responseentity.html)
- [Spring Framework — WebFlux Functional Endpoints](https://docs.spring.io/spring-framework/reference/web/webflux-functional.html)
- [Spring Framework — Server-Sent Events](https://docs.spring.io/spring-framework/reference/web/webflux/reactive-spring.html#webflux-codecs)
- [Spring Framework — WebTestClient](https://docs.spring.io/spring-framework/reference/testing/webtestclient.html)
- [Spring Framework 6.1 — AbstractJackson2Encoder Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/http/codec/json/AbstractJackson2Encoder.html)
- [Spring Framework 6.1 — ServerSentEvent Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/http/codec/ServerSentEvent.html)
- [Project Reactor — Backpressure and Reactive Streams](https://projectreactor.io/docs/core/release/reference/)
- [RFC 9110 — HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110.html)
- [RFC 7464 — JavaScript Object Notation (JSON) Text Sequences](https://www.rfc-editor.org/rfc/rfc7464.html)
- [NDJSON specification repository](https://github.com/ndjson/ndjson-spec)

**Tổng kết:** **Flux là mô hình nhiều giá trị, không phải một định dạng HTTP.** Với WebFlux/Jackson, `application/json` thường đưa nhiều values thành **một JSON array**; `application/x-ndjson` cho phép **từng JSON text + newline**, encode/write/flush theo từng element. `text/event-stream` là SSE với framing và reconnect semantics khác. Hãy chọn media type theo **consumer protocol**, test đúng Content-Type/Accept và byte framing, thiết kế backpressure/bounds, giải quyết error sau commit và cancellation, và không dùng reactive types làm lý do bỏ qua validation, security hay hiệu năng downstream.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-streaming-003">DataBuffer, bộ nhớ đệm dùng pool và trách nhiệm giải phóng</a>

<details>
<summary>Xem chi tiết</summary>

# DataBuffer, bộ nhớ đệm dùng pool và trách nhiệm giải phóng trong Spring WebFlux

## 1. Mục tiêu và vấn đề thực tế

Trong Spring WebFlux, HTTP body không nhất thiết xuất hiện một lần dưới dạng `String` hay `byte[]`. Khi tải tệp, phát video, proxy một luồng dữ liệu hoặc đọc payload lớn, hạ tầng thường xử lý **`Flux<DataBuffer>`**, tức một chuỗi những mảnh byte. Một số mảnh dùng **pooled/direct memory** được quản lý bằng reference count; nếu nhận buffer mà không trả lại đúng lúc sẽ gây rò bộ nhớ, còn trả lại quá sớm sẽ gây lỗi truy cập vùng nhớ đã được tái sử dụng.

Bài thuộc nhóm **Streaming WebFlux**, dành cho **Java 17+, Spring Framework 6.x, Spring Boot 3.x, Project Reactor**. Trọng tâm không chỉ là biết gọi `DataBufferUtils.release`, mà là trả lời đúng câu hỏi **ai sở hữu buffer tại từng bước**.

Sau bài học, bạn có thể:

- Giải thích `DataBuffer`, `DataBufferFactory`, `DefaultDataBuffer`, `NettyDataBuffer`, `PooledDataBuffer` và `DataBufferUtils`.
- Hiểu direct vs heap memory, pooling và reference counting (`retain`/`release`).
- Phân biệt **đọc xong rồi bỏ** với **chuyển ownership sang người tiêu thụ tiếp theo**.
- Biết khi nào **cần tự release** và khi nào **tuyệt đối không release thủ công** vì WebFlux/codec/HTTP writer đã chịu trách nhiệm.
- Viết pipeline `Flux<DataBuffer>` xử lý đúng `onNext`, `onError`, `cancel` và discarded buffers.
- Dùng `doOnDiscard(DataBuffer.class, DataBufferUtils::release)` đúng chỗ; không lạm dụng `doFinally` để release các buffer đã bị chuyển quyền.
- Hiểu `DataBufferUtils.join(...)`, `maxByteCount`, `DataBufferLimitException`, zero-copy composite và giới hạn memory.
- Phân biệt các biến thể `DataBufferUtils.write(...)`, file channel và response writing.
- Xây ví dụ Spring Boot WebFlux có endpoint nhận bytes giới hạn và endpoint trả streaming bytes; kiểm thử bằng `WebTestClient` và Reactor.
- Chẩn đoán leak, double-release, use-after-release, native memory growth và giới hạn tài nguyên.

**Phạm vi:** WebFlux reactive streaming, không phải Java `ByteBuffer` đơn lẻ. Code dùng Spring Boot 3.x/WebFlux, nơi ứng dụng thường dùng Reactor Netty. Các hành vi cụ thể của native buffer allocator có thể khác theo HTTP server/framework version; hãy dựa trên API contract và kiểm thử, không tự hardcode reference count của mọi triển khai.

## 2. DataBuffer nằm ở đâu trong HTTP pipeline?

```text
HTTP network -> Reactor Netty ByteBuf
                         |
                         v
                  NettyDataBuffer
                         |
                   Flux<DataBuffer>
                         |
           +-------------+--------------+
           |             |              |
           v             v              v
       JSON decoder  custom transform  raw streaming/file
           |             |              |
           v             v              v
       Java DTO        bytes/frames   DataBuffer stream
           |             |              |
           +-------------+--------------+
                         |
                         v
               HTTP response writer
                         |
                         v
                 release/cleanup
```

`DataBuffer` trừu tượng hóa một mảnh byte bất kể nền tảng là Netty, Jetty, array hay `ByteBuffer`. Framework có `Encoder`, `Decoder`, message readers/writers và transport handlers xử lý chúng.

Một endpoint thông thường:

```java
@PostMapping("/users")
public Mono<UserDto> create(@RequestBody Mono<UserInput> body) {
    return body.flatMap(service::create);
}
```

Đây là **snippet**, không phải file độc lập. Ứng dụng không trực tiếp thấy `DataBuffer` vì message reader/Jackson decoder đang sở hữu các raw buffers và giải phóng theo contract của codec. Việc tự thêm `DataBufferUtils.release(...)` vào controller DTO là vô nghĩa.

Chỉ cần xử lý raw `DataBuffer` khi bạn chủ động tham gia đường đi byte-level như upload/proxy/filter/custom decoder/binary transforms. Khi đó bạn phải xác định ownership rõ ràng.

## 3. Các interface/lớp quan trọng

| Thành phần | Trách nhiệm |
| --- | --- |
| `DataBuffer` | Byte buffer abstraction: đọc/ghi, positions, số byte có thể đọc/ghi |
| `DataBufferFactory` | Allocate hoặc wrap byte array/ByteBuffer thành DataBuffer |
| `DefaultDataBufferFactory` | Factory thường dùng heap/NIO buffers không reference-counted kiểu Netty |
| `NettyDataBufferFactory` | Factory dựa trên Netty `ByteBufAllocator` |
| `PooledDataBuffer` | DataBuffer mở rộng với `retain()`, `release()`, `isAllocated()` |
| `CloseableDataBuffer` | API close khi buffer hỗ trợ lifecycle kiểu AutoCloseable |
| `DataBufferUtils` | Helper read/write/join/release/retain/touch và byte-count operations |
| `DataBufferLimitException` | Lỗi khi tổng số bytes cần buffer vượt giới hạn đặt trước |
| `DataBufferUtils.releaseConsumer()` | Consumer gọi release cho mỗi DataBuffer |
| `DataBufferUtils.join(...)` | Gộp Flux buffers thành một DataBuffer (có thể composite) |
| `DataBufferUtils.read(...)` | Chuyển file/resource/channel thành Flux buffers |
| `ServerHttpResponse.writeWith(...)` | Viết Publisher<DataBuffer> ra response body |

Các lớp ở package `org.springframework.core.io.buffer`, trừ các HTTP request/response types thuộc Spring Web reactive HTTP APIs.

Không nhầm `org.springframework.core.io.buffer.DataBuffer` với `java.nio.ByteBuffer`: DataBuffer có các semantics quản lý memory/pooled ownership riêng, nhất là khi được bọc Netty `ByteBuf`.

## 4. Heap memory, direct memory và pooled memory

### Heap buffer

Một buffer dựa trên Java `byte[]` thường nằm trong JVM heap; garbage collector quản lý tuổi thọ array. `DefaultDataBufferFactory` có thể sử dụng kiểu này. `DataBufferUtils.release` với buffer không được pooled/closeable có thể trả `false` vì không có native reference cần decrement.

**Điều này không khiến code leak-safe trên mọi transport.** Nếu test chỉ chạy với DefaultDataBufferFactory, một bug thiếu release có thể không lộ rõ; triển khai Netty sử dụng pooled direct memory vẫn rò.

### Direct buffer

`ByteBuffer.allocateDirect`/Netty direct buffers có thể dùng memory ngoài heap để giảm copy khi I/O. Phần memory đó **không được nhìn thấy đầy đủ** qua chỉ số `Runtime.totalMemory()-freeMemory()`; leak có thể gây OOM/direct-memory pressure dù Java heap còn đủ.

### Pool

Tạo/hủy hàng nghìn buffers thường xuyên có chi phí. Pool cho phép trả vùng memory về allocator để tái sử dụng. Tuy nhiên **buffer được trả về pool có thể được một request khác dùng ngay**; nếu code tiếp tục đọc sau release, dữ liệu có thể sai, phát exception hoặc xảy ra lỗi nguy hiểm ở tầng native.

Không kết luận “GC sẽ tự release buffer đúng lúc” trong trường hợp `PooledDataBuffer` reference-counted. Pool ownership cần được giải quyết bằng release có chủ đích ở boundary.

## 5. Reference counting: retain và release

`PooledDataBuffer` theo semantics của Spring:

```text
allocate()      refCount ≈ 1 (chủ sở hữu ban đầu)
retain()        refCount ≈ 2 (thêm một reference có trách nhiệm release)
release()       refCount ≈ 1
release()       refCount ≈ 0 -> allocator có thể tái dùng memory
```

Dấu `≈` nhấn mạnh đây là **mô hình đơn giản hóa**; allocator/shared slices/composite buffers có chi tiết riêng.

- `retain()` giữ buffer sống lâu hơn bằng cách tăng reference count nếu là pooled.
- `release()` giảm count của reference đã sở hữu; khi về 0, vùng nhớ được coi đã trả lại.
- `isAllocated()` phản ánh trạng thái theo PooledDataBuffer contract.
- `DataBufferUtils.retain(dataBuffer)` chỉ retain khi thích hợp; code portable hơn tự cast.
- `DataBufferUtils.release(dataBuffer)` release khi buffer pooled hoặc closeable theo API; có thể trả true/false tùy việc giải phóng.
- **Mỗi ownership reference thêm bởi retain phải có một release tương ứng**.
- Không gọi release hai lần trên **cùng một ownership**.
- Không retain “cho chắc” mà không thiết kế đường release trong cả success/error/cancel.

Một bộ nhớ được phát lại từ pool không phải một object business bất biến mà bạn có thể lưu vào cache hoặc chia sẻ cho nhiều threads tùy tiện. Nếu cần giữ payload sau HTTP request, hãy **copy dữ liệu vào `byte[]`/storage có giới hạn**, hoặc quản lý retain/release với ownership rõ ràng.

## 6. Quy tắc vàng: ai là consumer cuối?

| Tình huống | Ai chịu trách nhiệm cuối? | Hành động |
| --- | --- | --- |
| Controller nhận `Mono<DTO>` từ `@RequestBody` | Decoder/WebFlux | Không release thủ công DTO |
| Code đọc trực tiếp một `DataBuffer`, biến thành String/DTO rồi bỏ buffer | Code đó | Release trong `finally` |
| Code gộp buffers bằng `DataBufferUtils.join` | Join quản lý các buffers thành phần; consumer của kết quả quản lý joined buffer | Release **joined result** một lần |
| Code tạo buffer rồi truyền tới `ServerHttpResponse.writeWith` | HTTP writer sau khi nhận ownership | Không release sớm trong controller |
| Code tạo buffer, nhưng encode thất bại **trước khi bàn giao** | Code tạo buffer | Release trong catch/finally |
| Code filter bỏ bớt DataBuffer trên đường đi | Pipeline/filter đã bỏ | Release discarded buffers, ví dụ `doOnDiscard` |
| Code cần giữ một pooled buffer ngoài vòng đời hiện tại | Người giữ thêm reference | `retain` khi giữ; `release` khi xong |
| Code dùng `DataBufferUtils.write(..., OutputStream)` | Caller quản lý channel, xử lý returned buffers | Theo contract, output stream không đóng và buffers không tự release |
| Code chỉ gọi `Flux<DataBuffer>.map(...)` để quan sát rồi forward cùng buffer | Downstream cuối nếu ownership được chuyển rõ | Không release khi còn forward |

**Không có quy tắc chung “cứ thấy DataBuffer là gọi release ngay”.** Đúng hơn: **mỗi lần một bên tiêu thụ xong ownership thì phải release; nếu chuyển ownership cho bên khác thì không tự release phần đó**.

## 7. Dùng DataBufferFactory đúng cách

Trong WebFlux HTTP handler, factory có sẵn trên response:

```java
DataBufferFactory factory = response.bufferFactory();
byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);
DataBuffer buffer = factory.wrap(bytes);
```

Đây là snippet trong method có `ServerHttpResponse response` và imports Spring/Java. `wrap(byte[])` bọc dữ liệu sẵn có; `allocateBuffer(initialCapacity)` tạo buffer mới.

```java
DataBuffer buffer = factory.allocateBuffer(64);
buffer.write("hello".getBytes(StandardCharsets.UTF_8));
```

Không dùng `allocateBuffer()` no-arg khi đã biết capacity hợp lý (method no-arg đã deprecated trong Spring Framework 6.0+). Đặt `64` là initial capacity, **không phải** giới hạn payload: DataBuffer implementation có thể mở rộng nếu cứ write nhiều bytes.

**Nếu wrapper dùng underlying byte[] có thể thay đổi, không mutate mảng đó sau khi đã bàn giao buffer**; đây là nguy cơ data race/corruption. Cách đơn giản cho response nhỏ là trả `Mono<String>`/DTO để encoder tự quản lý thay vì raw DataBuffer.

### Wrap và allocate khác pool thế nào?

Factory cụ thể quyết định wrap có copy/pooled hay không. `DefaultDataBufferFactory` và `NettyDataBufferFactory` có implementation khác nhau. Không dựa vào câu “wrap không copy” để kết luận reference counting luôn tăng hoặc không cần release. Nếu bạn chỉ cần text JSON API thông thường, hãy để framework encoder quyết định.

## 8. Đọc một buffer và release trong finally

Ví dụ chuyển raw `DataBuffer` thành `byte[]`:

```java
static byte[] consumeBytes(DataBuffer buffer) {
    try {
        byte[] out = new byte[buffer.readableByteCount()];
        buffer.read(out);
        return out;
    } finally {
        DataBufferUtils.release(buffer);
    }
}
```

**Đây là method hoàn chỉnh để đặt trong class có imports** `org.springframework.core.io.buffer.DataBuffer` và `DataBufferUtils`, nhưng **không** tự biên dịch khi thiếu Spring libraries/class wrapper.

Ownership: method **nhận và tiêu thụ toàn bộ ownership của buffer đầu vào**. Nó trả `byte[]` độc lập và release buffer ngay cả khi `read(...)` ném exception. Đừng truyền vào method này một buffer mà HTTP writer hoặc downstream khác đang cần dùng nếu bạn chưa có reference/ownership riêng.

**Cảnh báo memory:** Tạo `byte[]` bằng `readableByteCount()` có thể allocate rất lớn nếu input không bị giới hạn. Chỉ dùng sau khi đã có giới hạn kích thước request và tổng số bytes. Nếu cần streaming file nhiều GB, xử lý từng buffer và giới hạn memory; không gom toàn bộ vào heap.

## 9. Anti-pattern: release trước response.writeWith

**Sai:**

```java
DataBuffer db = response.bufferFactory()
    .wrap("hello".getBytes(StandardCharsets.UTF_8));

DataBufferUtils.release(db);       // Ownership đã bị trả quá sớm!
return response.writeWith(Mono.just(db));
```

Đây là **phản ví dụ**, không được đưa vào project. Nếu db là pooled và refCount đã về 0, HTTP writer có thể đọc vùng nhớ đã được allocator thu hồi.

**Đúng khi chuyển ownership cho HTTP writer:**

```java
DataBuffer db = response.bufferFactory()
    .wrap("hello".getBytes(StandardCharsets.UTF_8));
return response.writeWith(Mono.just(db));
```

Đây cũng là snippet trong method trả `Mono<Void>`, có `response`. Khi giao buffer cho `writeWith`, code không tự release buffer đó sau khi phát. Writer/transport layer có trách nhiệm theo contract. Nếu method tự allocate nhưng thất bại **trước khi Publisher được trả/bàn giao**, code tạo buffer phải dọn reference đó.

**Không tự viết `doOnNext(DataBufferUtils::release)` trên source đang gửi vào HTTP response.** Nó có thể làm buffer hết hạn trước khi response writer sử dụng.

## 10. Reactive operators có thể bỏ hoặc giữ buffer

Reactor operators như `filter`, `skip`, `take`, `flatMap`, `buffer`, `window` có thể bỏ phần tử hoặc giữ chúng trong internal queues. Nếu phần tử là `DataBuffer` pooled, cần xác định cả **path được forward** lẫn **path bị discard**.

Ví dụ loại bỏ buffer rỗng:

```java
Flux<DataBuffer> nonEmpty = incoming
    .filter(db -> db.readableByteCount() > 0)
    .doOnDiscard(DataBuffer.class, DataBufferUtils::release);
```

Đây là snippet với `Flux<DataBuffer> incoming` có sẵn. Với dropped buffers, `doOnDiscard` giúp release khi operator hỗ trợ discard. **Những buffers được giữ lại trong `nonEmpty` vẫn cần được consumer cuối release hoặc nhận ownership** — `doOnDiscard` không tự release phần tử được gửi tới downstream.

Nếu pipeline chuyển đổi thành DTO:

```java
Flux<byte[]> payloads = incoming
    .filter(db -> db.readableByteCount() > 0)
    .map(db -> {
        try {
            byte[] out = new byte[db.readableByteCount()];
            db.read(out);
            return out;
        } finally {
            DataBufferUtils.release(db);
        }
    })
    .doOnDiscard(DataBuffer.class, DataBufferUtils::release);
```

Đây là snippet minh họa trong một pipeline đã có `incoming`. `map` xử lý buffers đi qua; `filter` có thể discard. Với operators nội bộ phức tạp, đặt `doOnDiscard` ở một vị trí mà hook thấy các discard upstream/downstream phù hợp, theo Reactor semantics. Không kỳ vọng `doOnDiscard` tự xử lý mọi ownership giữ trong biến local bên ngoài Reactor, hoặc mọi operator tùy chỉnh.

### Cancellation và discarded buffers

Khi client disconnect/cancel, operator với prefetch/queue có thể bỏ các phần tử đang giữ; một số operators hỗ trợ discard hooks để cleanup. Nhưng nếu ứng dụng tự cất buffer vào `Map`, `List` hoặc shared queue thì `doOnDiscard` không quản lý references bên ngoài: bạn vẫn phải release explicit cho những references đó trên complete/error/cancel.

## 11. Vì sao doFinally không thể thay release cho từng DataBuffer?

Một anti-pattern:

```java
Flux<DataBuffer> source = incoming
    .doFinally(signal -> System.out.println("done"));
```

`doFinally` chạy **một lần cho một subscription terminal/cancel ở vị trí operator**, không phải một lần cho từng buffer. Chỉ in log ở đây không giải phóng các phần tử đã được phát và bỏ. Nếu bạn giữ `List<DataBuffer>` trong một scope, dùng `doFinally` để dọn list có thể hợp lý **khi bảo đảm ownership, exactly-once cleanup và không có buffer nào đã chuyển downstream**, nhưng cách đó dễ sai với cạnh tranh giữa cancellation và async writes.

Tốt hơn:

- Release trực tiếp mỗi buffer khi tiêu thụ xong (`try/finally`, `doOnNext` khi đúng boundary).
- Dùng `doOnDiscard` cho elements operator bỏ.
- Dùng `DataBufferUtils.join` cho bounded aggregation.
- Dùng `using/usingWhen` để quản lý resources như file/channel/connection (không thay ownership của từng DataBuffer).
- Để response writer quản lý buffers đã chuyển cho `writeWith`.

**Không release cùng buffer ở cả `map finally` và `doOnNext` downstream**, vì có thể double-release.

## 12. DataBufferUtils.release, retain, touch

Các helpers chuẩn:

```java
DataBufferUtils.retain(buffer);   // Tăng ref nếu buffer pooled.
DataBufferUtils.release(buffer);  // Giảm ref/close nếu implementation hỗ trợ.
DataBufferUtils.touch(buffer, "request-123");
```

Đây là snippets. `touch` gắn hint nếu buffer hỗ trợ leak tracking; hint **không** thay release và không dùng string chứa token/PII.

### Khi nào retain?

Giả sử bạn đã có một buffer thuộc **một chủ sở hữu** mà phải bàn giao thêm cho một consumer độc lập. Khi và chỉ khi cần hai ownership references:

```text
Owner A             Owner B
  |                   |
  | -- retain(buf) -->|  (thêm 1 reference)
  |                   |
  | release(buf)      |  Owner A xong
  |                   |
  |                release(buf)  Owner B xong
```

Nếu A chỉ **chuyển** ownership duy nhất cho B, **không cần retain**; A không release nữa. Retain vô cớ rồi chỉ release một lần sẽ leak một reference; release của A trước khi retain cho B sẽ gây use-after-release.

### Kiểm tra trạng thái

`PooledDataBuffer.isAllocated()` là API kiểm buffer vẫn được phân bổ. Nó không phải tín hiệu “đã đủ an toàn để bất kỳ thread nào đọc” hay bằng chứng uniqueness; multiple owners có thể cùng giữ reference. Không sử dụng một lần check-then-use làm synchronization giữa concurrent threads. Ownership và thread safety phải được thiết kế chặt.

## 13. DataBufferUtils.join: gộp, giới hạn và composite

`DataBufferUtils.join(Publisher<? extends DataBuffer>)` gộp một luồng thành một `Mono<DataBuffer>`. Kết quả có thể:

- Copy bytes vào một buffer mới; hoặc
- **Composite/zero-copy** nắm references tới các buffers thành phần.

Hành vi tùy implementation; không giả định `join` luôn copy hoặc luôn zero-copy.

**Ownership đặc biệt:**

1. Buffers **đã đưa vào join thành công** được quản lý bởi aggregate/composite — **không release lại từng buffer thành phần**.
2. Khi join gặp error hoặc cancel trước khi hoàn tất, Spring giải phóng những buffers đã tích lũy theo API contract.
3. **Người tiêu thụ kết quả joined DataBuffer** phải release **joined result** sau khi đọc xong.
4. Không vừa release joined result vừa release các thành phần vì đó có thể là double release.

### Giới hạn maxByteCount

```java
Mono<DataBuffer> bounded =
    DataBufferUtils.join(request.getBody(), 64 * 1024);
```

Đây là snippet trong method có `ServerHttpRequest request`. `64 * 1024` = 65.536 bytes, **tổng dung lượng buffer** ở nhánh join. Nếu vượt, API phát `DataBufferLimitException`; không tạo một byte array vài GB rồi mới kiểm độ dài.

Lưu ý: Việc giới hạn `join` bảo vệ **aggregation cụ thể**, không thay các hạn mức HTTP server, decoder, gateway, upload quota, CPU hoặc tổng in-flight requests. Nếu 2.000 clients cùng tạo 65 KiB buffers, tổng memory vẫn đáng kể.

### Release joined result đúng chỗ

```java
return DataBufferUtils.join(request.getBody(), 65536)
    .map(joined -> {
        try {
            byte[] bytes = new byte[joined.readableByteCount()];
            joined.read(bytes);
            return calculate(bytes);
        } finally {
            DataBufferUtils.release(joined);
        }
    });
```

Snippet giả định `calculate(byte[])` trả DTO; có thể thêm `onErrorMap(DataBufferLimitException.class,...)` để map thành HTTP 413. `map` nhận duy nhất joined buffer đã hoàn tất; nếu join bị cancel/error trước khi phát result, Spring quản lý các accumulated buffers. Mỗi joined result được giải phóng một lần trong `finally`.

## 14. DataBufferUtils.write: các overload không giống nhau

`DataBufferUtils.write` có nhiều chữ ký; không được gộp tất cả thành câu “write sẽ tự release”.

| Dạng | Kết quả | Điểm cần nhớ |
| --- | --- | --- |
| `write(source, OutputStream)` | `Flux<DataBuffer>` | Không đóng OutputStream và **không tự release buffers** |
| `write(source, WritableByteChannel)` | `Flux<DataBuffer>` | Không đóng channel, không tự release buffers |
| `write(source, AsynchronousFileChannel)` | `Flux<DataBuffer>` | Không đóng channel, không tự release buffers |
| `write(source, AsynchronousFileChannel, position)` | `Flux<DataBuffer>` | Cũng cần ownership/close policy |
| `write(source, Path, OpenOption...)` | `Mono<Void>` | File write API tự quản lý file channel theo implementation; xem Javadoc khi dùng, không đồng nhất với overload Flux |
| `read(Path, factory, size)` | `Flux<DataBuffer>` | Stream file, API bảo đảm đóng file khi Flux kết thúc |
| `join(source, max)` | `Mono<DataBuffer>` | Aggregation bounded; release joined result sau khi dùng |

Ví dụ với `OutputStream` (minh họa **blocking API**, không đặt trực tiếp trên Netty event loop):

```java
Flux<DataBuffer> written =
    DataBufferUtils.write(source, outputStream)
        .doOnNext(DataBufferUtils.releaseConsumer())
        .doOnDiscard(DataBuffer.class, DataBufferUtils::release);
```

Snippet này cần `source` và `outputStream` được tạo/đóng bởi owner đúng. `write(source, outputStream)` thực thi khi có subscription, không tự close stream; `doOnNext` release những buffers đã được writer phát ra **sau khi ghi**. Với prefetch/cancel, discarded hook giúp release ở operators hỗ trợ discard. Trong triển khai hoàn chỉnh, còn phải đóng stream trên complete/error/cancel, hạn chế blocking bằng scheduler phù hợp và xử lý đường lỗi trước khi buffer được phát tới `doOnNext`.

Để lưu file upload thực tế, ưu tiên `FilePart.transferTo(Path)` khi phù hợp và áp dụng size limit, kiểm filename/path, storage authorization và cleanup. Chỉ dùng low-level write khi thật sự cần kiểm soát bytes.

## 15. Custom decoder và encoder: điểm bàn giao ownership

Spring mô tả quy tắc đối với codecs:

### Decoder

Decoder là bên cuối trực tiếp đọc một buffer trước khi tạo object cấp cao (`String`/DTO/JSON). Nếu đã hoàn tất đọc và không chuyển `DataBuffer` cho ai khác, **decoder release buffer**.

```java
.map(buffer -> {
    try {
        return deserialize(buffer);
    } finally {
        DataBufferUtils.release(buffer);
    }
})
.doOnDiscard(DataBuffer.class, DataBufferUtils::release);
```

Đây là snippet cần implementation `deserialize` và upstream. Mỗi input buffer được đọc xong phải được release; discarded hook cho các đường rẽ không đưa buffer vào map. Nếu decoder gộp nhiều buffers, dùng `join`/bounded aggregation và release **aggregate** đúng; đừng giữ object pooled trong field sống lâu.

### Encoder

Encoder tạo một `DataBuffer` và chuyển downstream cho HTTP response writer. Nếu encode hoàn tất, encoder **bàn giao** ownership, không release sớm. Nếu encode/serialization throw **trước khi bàn giao**, encoder phải cleanup.

```java
DataBuffer buffer = factory.allocateBuffer(256);
boolean transferred = false;
try {
    writeSerializedValue(buffer, value);
    transferred = true;
    return buffer;
} finally {
    if (!transferred) {
        DataBufferUtils.release(buffer);
    }
}
```

Đây là snippet minh họa trong method có `factory`, `value` và `writeSerializedValue`. Nó bao phủ exception **trước return/bàn giao**; khi downstream filter hoặc HTTP writer từ chối/hủy, còn cần thiết kế release/discard theo đúng pipeline. Không lấy đoạn này làm custom encoder production mà thiếu test error/cancel.

**Quan trọng:** “Bên tạo tự release” đúng chỉ khi tạo thất bại trước khi giao; khi encoder đã giao cho writer thì **writer** là consumer chịu trách nhiệm theo contract.

## 16. WebClient: consume hoặc release response body

`WebClient` thường dùng các high-level methods:

```java
Mono<UserDto> user = webClient.get()
    .uri("/users/{id}", id)
    .retrieve()
    .bodyToMono(UserDto.class);
```

Đây là snippet trong service có `webClient`/`UserDto`. Framework codecs giải mã và quản lý DataBuffers; code của bạn chỉ nhận DTO, không tự release raw buffers.

Khi cần kiểm status/body tùy condition:

```java
Mono<String> result = webClient.get()
    .uri("/status")
    .exchangeToMono(response -> {
        if (response.statusCode().is2xxSuccessful()) {
            return response.bodyToMono(String.class);
        }
        return response.createError();
    });
```

`exchangeToMono`/`exchangeToFlux` kiểm response body chưa được consume khi returned publisher hoàn tất và giải phóng để tránh leak. **Không dùng API raw response exchange rồi quên xử lý/release body**. Không trả `ClientResponse` ra ngoài callback để đọc body sau này khi callback contract không cho phép.

### Khi chủ động nhận DataBuffer từ WebClient

Nếu bạn dùng `response.bodyToFlux(DataBuffer.class)`, consumer raw có responsibility tương ứng:

- Nếu chỉ đọc/sao chép buffer vào DTO, release sau khi đọc.
- Nếu relay sang `ServerHttpResponse.writeWith`, transfer ownership theo contract, **không release trên nhánh forward**.
- Nếu filter bỏ một buffer hoặc cancel, giải phóng những buffers còn thuộc ownership của mình.
- Đừng đưa cùng một `Flux<DataBuffer>` cold tới hai subscriptions không chủ ý; một response network body không phải collection repeatable.

Trong một HTTP proxy, ngoài reference count còn cần propagate backpressure, status/headers, cancel, timeout, kiểm hop-by-hop headers và request size limits. Không biến nó thành `collectList` unbounded.

## 17. HTTP response streaming: transfer ownership đúng cách

Một controller có thể trả `Mono<Void>` và viết raw buffers:

```java
@GetMapping("/download")
public Mono<Void> download(ServerHttpResponse response) {
    response.getHeaders()
        .setContentType(MediaType.APPLICATION_OCTET_STREAM);

    Flux<DataBuffer> buffers = Flux.defer(() ->
        Flux.just("alpha\n", "beta\n")
            .map(text -> response.bufferFactory().wrap(
                text.getBytes(StandardCharsets.UTF_8))));

    return response.writeWith(buffers);
}
```

Đây là snippet, file runnable hoàn chỉnh có ở phần thực hành. Ở đây `response.writeWith` là owner cuối theo HTTP server; **không gắn `.doOnNext(DataBufferUtils::release)`** lên buffers. `Flux.defer` bảo đảm mỗi subscription tạo pipeline/source mới, nhưng controller handler thường đã được gọi riêng cho mỗi HTTP request; không coi defer là cách sửa mọi vấn đề ownership.

### Khi nào có thể return Flux<DataBuffer> trực tiếp?

Trong annotated WebFlux controller có thể trả kiểu reactive body khi handler/codec hỗ trợ; song `ServerHttpResponse.writeWith` minh họa rõ ownership. Functional handler cũng có thể dùng `ServerResponse.ok().body(bufferFlux, DataBuffer.class)`. Dù chọn cách nào, cần phân biệt producer tạo buffers, HTTP writer consume và operator nào có thể discard.

Không dùng `Flux<DataBuffer>.cache()` theo thói quen: cache giữ references tới pooled buffers mà HTTP writer có thể đã release, dẫn đến dữ liệu không hợp lệ ở subscriptions sau. Nếu cần cache nội dung, cache `byte[]`/immutable domain data với size+TTL policy và tạo DataBuffers **mới** cho mỗi response.

## 18. Ứng dụng Spring Boot 3 Java 17+ runnable

Tạo Maven project từ Spring Initializr với:

- **Java 17+**.
- **Spring Boot 3.x**.
- `spring-boot-starter-webflux` (Reactor, Netty, Jackson).
- `spring-boot-starter-test` (test scope).
- `reactor-test` (test scope nếu starter-test của phiên bản đang dùng không có).

Không thêm `spring-boot-starter-web` nếu muốn Boot tự chọn reactive application type khi chỉ có WebFlux starter. Ứng dụng này không cần database hoặc file upload thực tế. Nó có:

- `POST /api/buffers/crc32` nhận binary body `application/octet-stream`, gộp **tối đa 65.536 bytes**, tính CRC32 và trả JSON; **giải phóng joined result trong finally**.
- `GET /api/buffers/download` tạo hai buffers text và chuyển trực tiếp cho HTTP writer.
- `DataBufferLimitException` được ánh xạ thành **413 Payload Too Large** ở endpoint nhận dữ liệu.

Cấu trúc:

```text
src/main/java/com/example/demo/
  DemoApplication.java
  BufferController.java
src/test/java/com/example/demo/
  BufferControllerTest.java
  BufferOwnershipTest.java
```

### 18.1. DemoApplication.java

```java
package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

### 18.2. BufferController.java

```java
package com.example.demo;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/buffers")
public class BufferController {

    private static final int MAX_BYTES = 64 * 1024;

    @PostMapping(path = "/crc32",
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Digest> crc32(ServerHttpRequest request) {

        return DataBufferUtils.join(request.getBody(), MAX_BYTES)
            .map(joined -> {
                try {
                    // MAX_BYTES được áp dụng trước allocation byte[].
                    byte[] bytes = new byte[joined.readableByteCount()];
                    joined.read(bytes);

                    CRC32 crc = new CRC32();
                    crc.update(bytes);
                    return new Digest(bytes.length,
                        String.format("%08x", crc.getValue()));
                } finally {
                    // Chúng ta tiêu thụ joined result, nên tự release.
                    DataBufferUtils.release(joined);
                }
            })
            .switchIfEmpty(Mono.just(new Digest(0, "00000000")))
            .onErrorMap(DataBufferLimitException.class, ex ->
                new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "body too large", ex));
    }

    @GetMapping(path = "/download",
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Mono<Void> download(ServerHttpResponse response) {
        response.getHeaders()
            .setContentType(MediaType.APPLICATION_OCTET_STREAM);

        // Tạo buffers mới mỗi subscription.
        Flux<DataBuffer> payload = Flux.defer(() ->
            Flux.just("alpha\n", "beta\n")
                .map(text -> response.bufferFactory().wrap(
                    text.getBytes(StandardCharsets.UTF_8))));

        // Transfer ownership cho HTTP response writer.
        return response.writeWith(payload);
    }

    public record Digest(int length, String crc32) {}
}
```

**Phân tích ownership endpoint crc32:**

1. `ServerHttpRequest.getBody()` cung cấp `Flux<DataBuffer>`.
2. `DataBufferUtils.join(source, 65536)` tiếp nhận các components và quản lý accumulated buffers khi thành công hoặc cancel/error.
3. `map(joined -> ...)` là consumer của **joined result**, copy bytes sang heap có giới hạn để tính checksum.
4. `finally` release joined result đúng một lần, cả khi `CRC32`/read có lỗi.
5. Nếu tổng bytes vượt 65536, join báo `DataBufferLimitException`; accumulated buffers do join quản lý, **không có joined result để tự release thêm**.
6. Nếu request body rỗng và join complete empty, trả `Digest(0,"00000000")`.
7. `onErrorMap` chỉ đổi error thành response exception; không thay reference-count semantics.
8. Không gọi `block()` hoặc tự `subscribe()` trong controller.

**Phân tích download:** `response.writeWith(payload)` là consumer/owner cuối. Code không giữ buffer sau khi đã đưa vào publisher, không làm double release và không cache pooled memory. Payload rất nhỏ, nhưng pattern có thể mở rộng sang bounded streaming từ file/channel bằng `DataBufferUtils.read` với lifecycle rõ.

**Production caveat:** CRC32 chỉ là checksum phát hiện lỗi ngẫu nhiên, **không phải chữ ký mật mã, không chống giả mạo**. Nếu API cần integrity chống tấn công, dùng HMAC/sha-256 phù hợp và đừng tính checksum nhầm là authentication. Endpoint demo không bảo vệ/authenticate người gọi; production cần limits ở gateway/security nữa.

### 18.3. Khởi động

macOS/Linux:

```bash
./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

Nếu không đặt `server.port`, app thường nghe port 8080. Không biên dịch các file Spring này bằng `javac BufferController.java` trần khi thiếu Maven/Gradle dependencies.

## 19. curl: kiểm tra CRC32, giới hạn và streaming

### Gửi 5 bytes "hello"

```bash
curl -i -X POST \
  'http://localhost:8080/api/buffers/crc32' \
  -H 'Content-Type: application/octet-stream' \
  -H 'Accept: application/json' \
  --data-binary 'hello'
```

Kỳ vọng HTTP **200**:

```json
{"length":5,"crc32":"3610a686"}
```

Đây là CRC32 của 5 byte UTF-8 ASCII `hello` (không có newline). Nếu dùng `echo hello | curl --data-binary @-`, stdin có thể thêm newline và CRC32/length khác. Dùng `printf 'hello'` hoặc literal `--data-binary 'hello'` để có output trên.

### Gửi body rỗng

```bash
curl -i -X POST \
  'http://localhost:8080/api/buffers/crc32' \
  -H 'Content-Type: application/octet-stream' \
  --data-binary ''
```

Kỳ vọng JSON `{"length":0,"crc32":"00000000"}` nếu HTTP body được nhận rỗng đúng như demo. Không gửi Content-Type có thể không match endpoint trước khi code kiểm body.

### Body vượt 64 KiB

Không cần gửi hàng MB/GB. Trên máy local, dùng script tạo đúng 65.537 bytes:

```bash
python -c "import sys; sys.stdout.buffer.write(b'x' * 65537)" \
  | curl -sS -i -X POST \
      'http://localhost:8080/api/buffers/crc32' \
      -H 'Content-Type: application/octet-stream' \
      --data-binary @-
```

Kỳ vọng HTTP **413 Payload Too Large** từ `DataBufferLimitException` được map tại controller nếu request được xử lý đến join. Gateway/server có thể giới hạn request sớm hơn. Không gửi payload lớn vào server ngoài môi trường test có quyền.

### Download raw binary stream

```bash
curl -i 'http://localhost:8080/api/buffers/download'
```

HTTP body chính xác (UTF-8, hai newline):

```text
alpha
beta
```

Server có thể chunk hoặc thêm transfer headers; **không** kết luận hai `DataBuffer` luôn là hai TCP packets. Headers tự động có thể thay đổi theo web server. Không dùng `Content-Length` hardcode nếu response streaming chưa tính độ dài.

## 20. WebTestClient kiểm HTTP endpoint

Tệp `src/test/java/com/example/demo/BufferControllerTest.java`:

```java
package com.example.demo;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

@SpringBootTest(webEnvironment =
        SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class BufferControllerTest {

    @Autowired
    WebTestClient client;

    @Test
    void crc32OfHelloHasExpectedLengthAndHex() {
        byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);

        client.post().uri("/api/buffers/crc32")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue(bytes)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.length").isEqualTo(5)
            .jsonPath("$.crc32").isEqualTo("3610a686");
    }

    @Test
    void tooLargeBodyBecomes413() {
        byte[] bytes = new byte[65537];

        client.post().uri("/api/buffers/crc32")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .bodyValue(bytes)
            .exchange()
            .expectStatus().isEqualTo(413);
    }

    @Test
    void downloadContainsExpectedBytes() {
        byte[] expected =
            "alpha\nbeta\n".getBytes(StandardCharsets.UTF_8);

        client.get().uri("/api/buffers/download")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentTypeCompatibleWith(
                MediaType.APPLICATION_OCTET_STREAM)
            .expectBody()
            .consumeWith(result ->
                assertArrayEquals(expected, result.getResponseBody()));
    }
}
```

Chạy:

```bash
./mvnw test
```

PowerShell:

```powershell
.\mvnw.cmd test
```

Các bài test là **source mẫu** với Boot 3/WebFlux test dependencies, không phải tuyên bố đã chạy trên repository người dùng. `WebTestClient` đi qua WebFlux HTTP server khi dùng RANDOM_PORT; tuy nhiên không tự động mô phỏng reverse proxy, client disconnect hay mọi chế độ leak detection Netty.

**Điểm đáng chú ý:** Việc assert HTTP 200/413 và bytes đúng **không chứng minh** native reference count trong mọi path đã về 0. Cần thêm unit test pooled buffers, tests cancellation và soak/load test có leak detection.

## 21. Test pooled ownership bằng NettyDataBufferFactory

Spring Boot WebFlux mặc định thường dùng Reactor Netty và có Netty `ByteBufAllocator`. Tệp `src/test/java/com/example/demo/BufferOwnershipTest.java`:

```java
package com.example.demo;

import java.nio.charset.StandardCharsets;

import io.netty.buffer.PooledByteBufAllocator;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.NettyDataBuffer;
import org.springframework.core.io.buffer.NettyDataBufferFactory;

import static org.junit.jupiter.api.Assertions.*;

class BufferOwnershipTest {

    @Test
    void retainedBufferNeedsMatchingReleases() {
        NettyDataBufferFactory factory =
            new NettyDataBufferFactory(
                PooledByteBufAllocator.DEFAULT);

        NettyDataBuffer buffer = factory.allocateBuffer(32);
        buffer.write("ok".getBytes(StandardCharsets.UTF_8));

        assertTrue(buffer.isAllocated());

        DataBufferUtils.retain(buffer);   // +1 reference
        DataBufferUtils.release(buffer);  // Release đầu.
        assertTrue(buffer.isAllocated()); // Vẫn còn ref thứ hai.

        DataBufferUtils.release(buffer);  // Release cuối.
        assertFalse(buffer.isAllocated());
    }

    @Test
    void joinedBufferCanBeReleasedAsOneUnit() {
        NettyDataBufferFactory factory =
            new NettyDataBufferFactory(
                PooledByteBufAllocator.DEFAULT);

        NettyDataBuffer a = factory.allocateBuffer(8);
        NettyDataBuffer b = factory.allocateBuffer(8);
        a.write(new byte[]{1, 2});
        b.write(new byte[]{3, 4});

        // join thuộc quyền quản lý các buffers thành phần.
        var joined = DataBufferUtils.join(
            reactor.core.publisher.Flux.just(a, b),
            32).block();

        assertNotNull(joined);
        try {
            byte[] bytes = new byte[joined.readableByteCount()];
            joined.read(bytes);
            assertArrayEquals(
                new byte[]{1, 2, 3, 4}, bytes);
        } finally {
            DataBufferUtils.release(joined);
        }
        // Không gọi release(a) và release(b) thêm.
    }
}
```

**Lưu ý bản chất của test:**

- Netty `PooledByteBufAllocator.DEFAULT` cần Netty trên classpath. `spring-boot-starter-webflux` với Reactor Netty cung cấp Netty 4 dependency trong Boot 3 thông thường, nhưng nếu ứng dụng đổi server stack thì hãy khai báo đúng test dependency.
- Test thứ nhất minh họa retain/release hai ownership references và `isAllocated`. Tuy nhiên code production không nên gọi release hai lần vô điều kiện trên buffer không biết đã retain hay chưa.
- Test thứ hai dùng join, release **joined result duy nhất**, không release a/b nữa. Nếu join fail trước khi tạo result, contract join quản lý accumulated buffers; trong test nâng cao cần kiểm các nhánh error/cancel.
- `block()` chỉ ở JUnit test thread nhằm lấy joined result, **không** trong HTTP event loop.
- Không dùng `isAllocated` như một phép đồng bộ trong shared mutable/concurrent code. Nó chỉ kiểm trạng thái tại thời điểm đọc.

Nếu cần chứng minh leak-free thật, bật Netty leak detector trong test/staging và chạy tải/cancel; pass hai unit tests không đủ bao quát mọi race.

## 22. JDK 17 độc lập: mô phỏng ref-counting ownership

Không cần Spring dependencies cho mô hình khái niệm nhỏ này. Tệp `ReferenceOwnershipDemo.java`:

```java
public class ReferenceOwnershipDemo {

    static final class Lease {
        private int references = 1;

        synchronized void retain() {
            if (references == 0) {
                throw new IllegalStateException("already released");
            }
            references++;
        }

        synchronized void release() {
            if (references == 0) {
                throw new IllegalStateException("double release");
            }
            references--;
        }

        synchronized int count() {
            return references;
        }
    }

    public static void main(String[] args) {
        Lease lease = new Lease();

        lease.retain(); // Chủ B giữ thêm.
        System.out.println("after retain=" + lease.count());

        lease.release(); // Chủ A đã xong.
        System.out.println("after A=" + lease.count());

        lease.release(); // Chủ B đã xong.
        System.out.println("after B=" + lease.count());
    }
}
```

Biên dịch và chạy:

```bash
javac ReferenceOwnershipDemo.java
java ReferenceOwnershipDemo
```

Stdout **chính xác**:

```text
after retain=2
after A=1
after B=0
```

Đây **không phải** implementation Netty, PooledDataBuffer hay thread-safe memory allocator thực tế. Nó chỉ diễn tả luật ownership balance dễ nhớ; code Spring thực phải dùng `DataBufferUtils.retain/release` và tuân theo bên đã bàn giao/consume buffers.

## 23. DataBufferLimitException và giới hạn tài nguyên

Vì sao giới hạn cực quan trọng:

```text
1 request upload      10 MB
100 requests in-flight
------------------------
~1 GB payload nếu gom toàn bộ cùng lúc
(chưa tính objects, codec state, network queues, buffers copy)
```

`DataBufferUtils.join(source, maxByteCount)` đặt giới hạn tại **aggregation cụ thể**. Với default codecs, property `spring.codec.max-in-memory-size` của Spring Boot có thể kiểm giới hạn in-memory decoding/aggregation tùy codec/framework version. Không suy rằng một property duy nhất bao phủ mọi low-level custom buffer handling, multipart streaming, HTTP server connection quota và gateway request size.

Thực hành:

- Đặt giới hạn request body **trước khi allocation toàn bộ payload**.
- Dùng `maxByteCount` ở mọi aggregation raw.
- Khi parse JSON, dùng decoder/validation với limit phù hợp; không tự collect unbounded.
- Khi xử lý multipart, có hạn mức total size, mỗi file, mỗi field, số parts, disk spill/quarantine.
- Giới hạn số đồng thời uploads/downloads/connections và tổng native memory budget.
- Chọn chunk size hợp lý, không dùng một buffer 1 GB rồi gọi đó là streaming.
- Streaming nhiều chunk không có nghĩa ứng dụng không buffer; `collectList`/`join`/`cache` có thể biến nó thành aggregation lớn.
- Nếu cần lưu file rất lớn, stream vào storage thay vì giữ cả file trong heap.
- Tách long streams khỏi request/worker capacity khác khi cần.

**413 Payload Too Large** là hợp đồng HTTP thường phù hợp với body vượt quota; nếu error đã xảy ra sau khi response commit, không thể luôn gửi 413 mới.

## 24. Cancellation, error và race trong stream

Reactive Streams có ba kiểu kết thúc thường gặp:

```text
onComplete()   -> nguồn kết thúc thành công
onError(e)     -> nguồn kết thúc do lỗi
cancel()       -> subscriber không còn muốn nhận thêm
```

Các trách nhiệm:

| Trường hợp | Điều cần giải phóng |
| --- | --- |
| Error khi đang đọc một DataBuffer | Buffer local đã nhận quyền sở hữu |
| Cancel khi join đang tích lũy | Join tự release accumulated buffers theo contract |
| Cancel khi operator có internal queue | Discard hook và operator semantics |
| HTTP client disconnect sau một số chunks | Những buffers pending có ownership ở writer/pipeline; cleanup producer |
| File write hỏng sau khi mở channel | Channel/file resources và buffers không chuyển quyền |
| Encoder serialization lỗi trước khi publish | Buffer mới tạo, chưa bàn giao |
| Retry tạo subscription mới | Không tái dùng pooled buffers đã được release từ lần trước |

Không release một buffer trong cả `doOnError` lẫn `doFinally` nếu hai nhánh cùng nhắm một reference. `doFinally` có thể chạy sau downstream terminal; nếu dùng nó cho cleanup cần tránh race/double-free và bảo đảm không ai đang đọc buffer nữa.

Nếu client disconnect, application không được giả định phát tiếp `onNext` vô hạn vào queue. Dùng bounded queues/backpressure, hủy file read/remote subscriptions theo cancellation và không lưu pooled buffers trong static caches.

## 25. Leak detection với Netty và troubleshooting

Các dấu hiệu có thể gặp:

- Native/direct memory tăng liên tục dù số active requests ổn định.
- `OutOfDirectMemoryError` hoặc lỗi liên quan Netty pooled allocator.
- Netty leak detector ghi cảnh báo kiểu `LEAK: ByteBuf.release() was not called...`.
- HTTP streaming trả bytes sai hoặc `IllegalReferenceCountException` khi buffer đã release quá sớm.
- Heap dump không giải thích hết native memory consumption.
- Tăng `-Xmx` không giải quyết được leak native/direct buffers và còn có thể làm memory limit của container chật hơn.

Trong môi trường **test/staging có kiểm soát**, Netty có property `-Dio.netty.leakDetection.level=advanced` hoặc `paranoid` để thu thập bằng chứng, nhưng mức cao có overhead lớn; không bật `paranoid` trên production bận rộn mà thiếu đánh giá tác động. Cú pháp/env var cụ thể phụ thuộc cách JVM/server được khởi động.

Checklist điều tra:

1. Xác nhận backend dùng pooled buffer (Reactor Netty, Jetty hay custom implementation).
2. Tìm những nơi nhận trực tiếp `Flux<DataBuffer>`.
3. Vẽ ownership mỗi buffer: ai allocate, ai consume, ai release, ai transfer.
4. Kiểm branches `filter`/`skip`/`take`/`timeout`/`onErrorResume`/`retry`.
5. Tìm `collectList`/`cache`/shared state giữ buffer.
6. Kiểm encoder lỗi trước bàn giao.
7. Kiểm raw `DataBufferUtils.write` overload có tự release không, channel có đóng không.
8. Kiểm `WebClient` raw response body có được consume/relay/release không.
9. Tạo test cancellation/disconnect, malformed body, quota breach và streaming failure.
10. Quan sát native memory, Netty allocator metrics, request concurrency và P99 latency.
11. So sánh performance trước/sau thay đổi, không gọi `release` bừa bãi để làm im leak detector.
12. Nếu dùng `touch` hints, không ghi dữ liệu bí mật vào hint.

**Không dùng `System.gc()` hoặc tăng heap làm giải pháp cho leak reference-counted.** Phải sửa đường ownership.

## 26. Tình huống thực tế và cách chọn pattern

### 26.1. JSON REST CRUD bình thường

Controller nhận `@RequestBody Mono<Dto>`, trả `Mono<ResponseEntity<Dto>>`. **Không cần** trực tiếp quản lý `DataBuffer`. Spring codecs/HTTP writers nắm responsibility.

### 26.2. Tính hash/checksum request binary nhỏ

Dùng bounded `DataBufferUtils.join`, copy bytes có giới hạn, release joined result trong `finally`; trả DTO. Đây là pattern của sample app.

### 26.3. Stream file lớn về client

Ưu tiên `Resource` hoặc `DataBufferUtils.read`/`ServerResponse.body` phù hợp, không join cả file. Khi chuyển `Flux<DataBuffer>` thẳng cho response writer, owner downstream cuối release; code không vừa forward vừa release.

### 26.4. Proxy/relay upstream WebClient body

Đọc raw `Flux<DataBuffer>` chỉ nếu thực sự cần; forward ownership tới response writer và xử lý header/status/backpressure/cancel đúng. Không cache raw buffers, không dual-subscribe và không leak khi target/client đóng.

### 26.5. Lọc một vài mảnh

Nếu filter bỏ mảnh, dùng discard hook. Nếu mảnh giữ lại được chuyển xuống downstream, **không release mảnh giữ lại trước khi downstream dùng**.

### 26.6. Chuyển thành DTO/String

Nếu code là consumer cuối của raw DataBuffer, release sau khi tạo DTO trong `try/finally`. Nếu dùng standard codecs, không chèn release thêm.

### 26.7. Gộp nhiều mảnh rồi chia nhỏ lại

`join` có thể tạo composite giữ references. Nếu split thành retained views cần hiểu `retainedSlice`/Netty reference semantics: ai giữ view, ai release composite, có share underlying memory không. Tránh thiết kế này trừ khi cần thiết; dùng copy vào bounded immutable bytes cho dữ liệu sống lâu.

### 26.8. Logging/filter request body

Đây là nơi leak rất phổ biến. Một `WebFilter` đọc raw body để log rồi không tái tạo/decorate body đúng sẽ khiến controller downstream không đọc được hoặc bị đọc hai lần. Nếu bắt buộc inspect payload, phải có size cap, redact secrets, replay/decorator semantics và ownership/cancel tests; không copy ví dụ debug body logging lên production vô điều kiện.

## 27. Những lỗi phổ biến

1. **Nhìn DataBuffer như byte[] GC-managed:** pooled reference cần release.
2. **Không phân biệt heap/direct memory:** leak native có thể không xuất hiện trong heap metric.
3. **Đọc buffer rồi không release:** local consumer cuối tạo leak.
4. **Release rồi chuyển buffer cho `writeWith`:** use-after-release.
5. **Vừa release buffer trong controller vừa để writer release:** double-release.
6. **Retain nhưng không có release tương ứng:** leak reference.
7. **Release mọi buffer ở doFinally bất kể đã chuyển downstream:** ownership sai/race.
8. **Tin doOnDiscard release mọi onNext:** nó chỉ xử lý discarded buffers theo operator support.
9. **Dùng filter/drop trên pooled source mà quên doOnDiscard:** leak dropped pieces.
10. **Dùng `join` rồi release từng component và joined result:** double-release.
11. **Dùng `join` mà không release joined result:** leak aggregate.
12. **Gộp body không maxByteCount:** nguy cơ memory exhaustion.
13. **Tin `maxByteCount` ở một join là tổng quota hệ thống:** vẫn cần concurrency/server/gateway limits.
14. **Tin `DataBufferUtils.write(OutputStream)` tự đóng stream/release buffers:** sai overload contract.
15. **Dùng blocking OutputStream trên Netty event loop:** thread starvation.
16. **Cache `Flux<DataBuffer>` pooled:** resubscription có thể thấy memory đã release.
17. **Subscribe raw WebClient body rồi quên consume/release:** leak response resources.
18. **Đọc request body hai lần trong filter/controller:** body thường không repeatable.
19. **Mutate `byte[]` sau khi wrap/bàn giao:** data corruption.
20. **Assume mọi DataBuffer đều pooled:** implementation quyết định; dùng helper portable.
21. **Assume mọi pooled buffer đều dùng same refCount numbers:** slices/composite có nuances.
22. **Lấy `isAllocated` rồi đọc trên thread khác không đồng bộ:** TOCTOU/race.
23. **Quên error path khi custom encoder allocate:** leak trên serialization failure.
24. **Quên cancel path trong long-lived streaming:** leak buffers/listeners.
25. **Dùng unbounded `collectList` cho file lớn:** đẩy byte buffers vào RAM.
26. **Dựa vào một happy-path HTTP test để tuyên bố leak-free:** cần pooled/cancel/error stress.
27. **Bật Netty leakDetection paranoid trên production bận:** overhead đáng kể.
28. **Tăng -Xmx để “sửa” off-heap leak:** chưa xử lý root cause.
29. **Dùng buffer shared giữa hai consumers mà không retain/copy:** double ownership sai.
30. **Gọi release hai lần vì tưởng release đầu trả false nghĩa chưa release:** return value không phải license release tùy tiện.
31. **Gọi retainedSlice mà quên lifetime của parent/shared memory:** unsafe refs.
32. **Không giới hạn file upload/multipart:** memory/disk exhaustion.
33. **Cho raw binary endpoint public không kiểm quota/auth:** dễ thành DoS.
34. **Coi CRC32 là chữ ký bảo mật:** không chống giả mạo.
35. **Coi một DataBuffer = một packet TCP:** transport có thể chia/gộp tùy flow control.
36. **Không phân biệt lỗi trước/sau response commit:** status không luôn sửa được.
37. **Bỏ Spring codecs để tự parse JSON từ raw ByteBuf không cần thiết:** phức tạp và dễ leak.

## 28. Checklist code review và production

### Ownership

- [ ] Có sơ đồ allocate → read/write → transfer/release cho mỗi raw DataBuffer?
- [ ] Consumer cuối của buffer có release trong success/error/cancel?
- [ ] Nếu chuyển buffer sang HTTP writer, code không release sớm?
- [ ] Mỗi `retain` có đúng một release ownership tương ứng?
- [ ] `join` chỉ release joined result, không double release components?
- [ ] Custom decoder/encoder có cleanup khi exception trước bàn giao?
- [ ] `doOnDiscard` đặt đúng operator/path khi có dropped buffers?
- [ ] Không dùng `cache`/share raw pooled buffers sống ngoài request?
- [ ] Không mutate underlying byte[] sau khi chuyển ownership?

### Giới hạn tài nguyên

- [ ] Mọi aggregation có maxByteCount/codec memory limit phù hợp?
- [ ] Có giới hạn số concurrent requests, uploads, stream connections?
- [ ] Có file size/part count/storage quota cho multipart?
- [ ] Blocking file/OutputStream work không chạy trên Netty event-loop?
- [ ] Body raw không bị consume lặp giữa WebFilter và controller?
- [ ] Xử lý disconnect/cancel và cleanup resource subscriptions?
- [ ] Response status và header được đặt trước commit?

### Kiểm thử và vận hành

- [ ] Unit test pooled retain/release và join composite?
- [ ] HTTP test kiểm bytes đúng, body rỗng, vượt limit/413?
- [ ] Error path: malformed body, decoder lỗi, stream writer lỗi?
- [ ] Cancel/disconnect path: không còn pooled buffers/queued references?
- [ ] Quan sát native memory, allocator metrics, leak detector trong staging?
- [ ] Không log raw body/token/file data vì mục đích debug không kiểm soát?
- [ ] Tắt leak-debug mức overhead cao khi vận hành bình thường trừ khi có kế hoạch?

## 29. Câu hỏi tự kiểm tra

**1.** `DataBuffer` có phải luôn ở Java heap không?

**Đáp án:** Không. Nó có thể bọc direct/native pooled buffers, nhất là với Netty.

**2.** Vì sao `DataBufferUtils.release(buffer)` cần được gọi trong custom decoder?

**Đáp án:** Decoder là consumer cuối của raw buffer khi đã tạo DTO/String và không forward nó nữa; pooled memory cần được trả.

**3.** Khi nào không nên release trong controller?

**Đáp án:** Khi đã chuyển ownership của buffer cho `response.writeWith`/HTTP writer hoặc một consumer được giao trách nhiệm cuối.

**4.** Retain làm gì?

**Đáp án:** Thêm một reference nếu buffer pooled, cho phép chủ sở hữu mới giữ buffer sống đến khi release riêng.

**5.** Gọi retain một lần rồi chỉ release một lần có sao không?

**Đáp án:** Nếu đã có một initial ownership và không ai release reference còn lại, refCount > 0 và leak.

**6.** `doOnDiscard` giải phóng những buffer nào?

**Đáp án:** Những phần tử DataBuffer bị các operators có hỗ trợ discard bỏ đi tại pipeline boundary, không tự release mọi buffer đã forward.

**7.** `DataBufferUtils.join` có luôn copy dữ liệu không?

**Đáp án:** Không. Nó có thể tạo zero-copy composite; ownership của thành phần nằm trong aggregate theo contract.

**8.** Sau khi join hoàn tất, release gì?

**Đáp án:** Release joined result khi tiêu thụ xong; không release lại từng component đã đưa vào join.

**9.** Vì sao cần `join(source, maxByteCount)`?

**Đáp án:** Giới hạn tổng bytes khi aggregation, tránh gom body vô hạn; vượt limit phát DataBufferLimitException.

**10.** `DataBufferUtils.write(source, OutputStream)` có tự release input DataBuffers không?

**Đáp án:** Không. Returned Flux phát các buffers sau write; caller cần giải phóng theo contract và quản lý đóng OutputStream.

**11.** `response.writeWith(flux)` có cần `doOnNext(DataBufferUtils::release)` không?

**Đáp án:** Không nên. Writer là consumer cuối; release trước writer có thể làm dữ liệu bị thu hồi quá sớm.

**12.** Heap ít dùng nhưng native memory tăng đều có thể do đâu?

**Đáp án:** Pooled/direct buffer leak, shared allocator pressure, outstanding connections hoặc các native resources khác; cần metrics/leak detector và phân tích ownership.

**13.** Vì sao `doFinally` không thay release mỗi buffer?

**Đáp án:** doFinally quan sát một subscription kết thúc/cancel, không tự được gọi trên từng buffer; các buffers có thể đã chuyển ownership cho downstream.

**14.** CRC32 có chứng minh dữ liệu do một bên đáng tin gửi không?

**Đáp án:** Không. Nó chỉ checksum không kháng giả mạo; nếu cần authentication/integrity dùng HMAC/signature.

**15.** Test HTTP CRC32 PASS có đủ kết luận không có leak không?

**Đáp án:** Không. Cần test pooled reference counts, error, cancellation và stress/leak detector.

## 30. Nguồn tham khảo

- Spring Framework — Data Buffers and Codecs: https://docs.spring.io/spring-framework/reference/core/databuffer-codec.html
- Spring Framework 6.2.4 — `DataBufferUtils` Javadoc, `join`/`release`/`write`: https://docs.spring.io/spring-framework/docs/6.2.4/javadoc-api/org/springframework/core/io/buffer/DataBufferUtils.html
- Spring Framework 6.2.4 — `PooledDataBuffer` Javadoc: https://docs.spring.io/spring-framework/docs/6.2.4/javadoc-api/org/springframework/core/io/buffer/PooledDataBuffer.html
- Spring Framework 6.2.4 — `DataBufferFactory` Javadoc: https://docs.spring.io/spring-framework/docs/6.2.4/javadoc-api/org/springframework/core/io/buffer/DataBufferFactory.html
- Spring Framework 6.2.4 — `DataBuffer` Javadoc: https://docs.spring.io/spring-framework/docs/6.2.4/javadoc-api/org/springframework/core/io/buffer/DataBuffer.html
- Spring Framework 6.2.4 — `DataBufferLimitException`: https://docs.spring.io/spring-framework/docs/6.2.4/javadoc-api/org/springframework/core/io/buffer/DataBufferLimitException.html
- Spring Framework — WebFlux annotated controllers and request body: https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-methods/requestbody.html
- Spring Framework — WebClient response exchange/cleanup: https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-exchange.html
- Spring Framework — WebFlux WebTestClient: https://docs.spring.io/spring-framework/reference/testing/webtestclient.html
- Project Reactor — Handling discarded elements: https://projectreactor.io/docs/core/release/reference/advancedFeatures/cleanup.html
- Project Reactor — Error handling and cancellation: https://projectreactor.io/docs/core/release/reference/coreFeatures/error-handling.html
- Netty — ByteBuf reference counting: https://netty.io/wiki/reference-counted-objects.html
- Netty — Resource leak detector: https://netty.io/wiki/reference-counted-objects.html#troubleshooting-buffer-leaks


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-streaming-004">Giới hạn kích thước dữ liệu, tốc độ tiêu thụ và tránh gom toàn bộ stream</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn kích thước dữ liệu, tốc độ tiêu thụ và tránh gom toàn bộ stream trong Spring WebFlux

## 1. Bài toán và mục tiêu

Spring WebFlux và Project Reactor giúp xây dựng HTTP pipelines xử lý dữ liệu **theo luồng (streaming)**, trong đó nguồn phát, toán tử xử lý và phía tiêu thụ có thể tiến hành dần thay vì đợi toàn bộ body đến đủ. Tuy nhiên, “ứng dụng trả `Flux`” **không đồng nghĩa bộ nhớ luôn bị giới hạn**, “non-blocking” **không đồng nghĩa vô hạn throughput**, và “có backpressure” **không đồng nghĩa tất cả các downstream, proxy hay cơ sở dữ liệu tự biết giảm tốc**.

Một request có thể vượt giới hạn vì:
- Client gửi một body lớn hoặc upload chậm, và server chờ/đệm quá nhiều bytes.
- JSON decoder buộc phải gom một object lớn, dù endpoint được viết theo reactive API.
- Code gọi `collectList()`, `collectMap()`, `reduce` với accumulator lớn hoặc `DataBufferUtils.join(...)` không giới hạn.
- Một nguồn phát nhanh hơn người đọc, dẫn tới buffers/queues trong `flatMap`, hot source, WebClient hoặc gateway phình ra.
- Một file download rất lớn được đọc toàn bộ thành `byte[]` trước khi response được gửi.
- Retry, timeout, connection pool và số request đồng thời khiến tổng memory tăng dù từng request có giới hạn riêng.

Sau bài này, bạn sẽ biết cách:

1. Phân biệt **limit kích thước** (bytes/items), **rate limiting** (bytes/giây, requests/giây), **concurrency limiting**, **backpressure/demand**, **timeout** và **quota**.
2. Nêu chính xác phạm vi của `maxInMemorySize` trong Spring WebFlux codecs, `DataBufferLimitException`, `DataBufferUtils.join(source, maxByteCount)`.
3. Xử lý `Flux<DataBuffer>` mà không giữ toàn body; đếm bytes bằng một state nhỏ và release buffers sau khi đã tiêu thụ.
4. Tạo response NDJSON/SSE theo luồng, tránh materialize vô hạn bằng `collectList()` hay `block()` trong event loop.
5. Hiểu `limitRate`, `limitRequest`, `take`, `flatMap(..., concurrency)`, `concatMap`, `onBackpressureBuffer`, `onBackpressureLatest` và `sample`, cùng hậu quả khi drop events.
6. Đặt limits tại nhiều lớp: ingress, WebFlux codec, application-level cumulative bytes, WebClient, connection pools, slow-client controls và storage.
7. Chạy ví dụ **Java 17+, Spring Boot 3.x, Spring Framework 6.x** với REST endpoint đếm bytes upload có hard limit và endpoint NDJSON streaming; dùng `WebTestClient` kiểm tra HTTP 200/413 và các chunks.
8. Thiết kế cleanup/cancellation/error, quan sát metrics và tránh hiểu sai kết quả benchmark.

**Phạm vi:** WebFlux với Reactor/Reactive Streams và Reactor Netty phổ biến trong Spring Boot 3.x. Dù dùng các phần tử của Spring Framework 6.2 để giải thích, hãy đối chiếu API theo phiên bản project thực tế. Bài không tuyên bố WebFlux tự làm toàn bộ I/O trong ứng dụng thành non-blocking nếu bên dưới còn JDBC/SDK/file API blocking.

## 2. Sơ đồ luồng dữ liệu và nơi có thể tích tụ bộ nhớ

Một endpoint có thể đi qua nhiều vùng đệm:

~~~text
HTTP client / upstream
         |
         | TCP incoming bytes / headers / chunks
         v
Ingress / CDN / reverse proxy
         |  - max request body
         |  - read timeout, slow client protection
         |  - buffering có thể bật/tắt theo route
         v
Netty HTTP receive / request body Flux<DataBuffer>
         |
         |  decoder & message reader (JSON/NDJSON/multipart)
         |  maxInMemorySize áp dụng theo ngữ cảnh
         v
Controller -> Reactor operators
         |  - map/filter, flatMap queues, buffers
         |  - collectList / join (aggregate!)
         |  - concurrency/prefetch/rate policy
         v
Service / WebClient / R2DBC / file storage
         |  - pool / DB connection / upstream timeout
         v
WebFlux response encoder / HTTP send
         |  - codec, TCP send buffers, slow reader
         v
Proxy / Browser
~~~

Với từng ranh giới phải trả lời:
- **Ai cấp phát dữ liệu?** Java heap, direct/native buffers, temp disk hay remote queue?
- **Ai có quyền giải phóng?** Framework decoder, owner của DataBuffer, writer, store, cancellation handler?
- **Có hard limit rõ ràng không?** Per element, per HTTP request, per user/tenant, per instance và hệ thống?
- **Có backpressure thật sự không?** Có từ `Publisher` tới nguồn đọc được không, hay upstream service/message broker vẫn push?
- **Có điểm nào giữ toàn bộ body trước khi xử lý không?** `bodyToMono(byte[].class)`, `collectList`, `join`, proxy buffering?
- **Nếu client disconnect thì sao?** Downstream có cancel không, buffers đã cấp phát có được dọn không, filesystem/object storage có file dở không?

Cần nhìn **cả chuỗi**, không chỉ đặt một operator Reactor rồi mặc định mọi tầng đều an toàn.

## 3. Phân biệt năm loại giới hạn dễ nhầm

| Cơ chế | Kiểm soát gì? | Ví dụ | Không thay thế được |
| --- | --- | --- | --- |
| **Kích thước** | Tổng byte/part/item sau khi đọc | Upload tối đa 10 MiB | Tốc độ gửi bytes và request flood |
| **Tốc độ** | Số bytes/items được xử lý trong một đơn vị thời gian | 1 MiB/s mỗi tenant | Tổng bytes/1 request |
| **Demand/backpressure** | Số phần tử downstream đang yêu cầu upstream | Reactive Streams `request(n)` | Global rate limit |
| **Concurrency** | Số operation/connection đang active | Chỉ 8 remote calls đồng thời | Bytes/connection và timeout |
| **Thời gian/quota** | Deadline, idle timeout, dung lượng theo user/ngày | 30 s/request, 1 GiB/ngày | Buffer leaks và malformed payload |

Ví dụ: giới hạn `flatMap(..., 8)` cho tối đa khoảng 8 inner subscriptions hoạt động trong toán tử đó, **nhưng** nếu mỗi subscription tải 1 GiB bằng `collectList`, ứng dụng vẫn có thể OOM. Giới hạn **tổng bytes** 10 MiB nhưng không có minimum upload rate, client có thể giữ kết nối hàng giờ bằng cách gửi 1 byte mỗi phút. Còn `limitRate(16)` yêu cầu upstream theo từng đợt tối đa 16 phần tử, **không** thể hứa tốc độ 16 event/giây.

### 3.1. Tính tổng tài nguyên theo concurrency

Giả sử ứng dụng cho phép 200 uploads đồng thời, và mỗi upload có thể giữ tạm 1 MiB trong memory. Chỉ riêng phần đó có thể cần khoảng **200 MiB**, chưa tính JVM, Netty direct buffers, TLS buffers, encoder, databases và các request khác.

~~~text
Tổng ngân sách bộ nhớ cho buffers
  ≈ số requests đồng thời × buffered bytes/request
  + các queue/cache/shared pools
  + overhead của framework và runtime.
~~~

Đây là **ước lượng**, không phải một đẳng thức heap chính xác: dữ liệu có thể ở native/direct memory, temp disk hoặc object storage. Chỉ giới hạn per request mà không đặt concurrency budget vẫn chưa đủ.

## 4. Backpressure: nhu cầu tiêu thụ phần tử chứ không phải tốc độ thời gian

Trong Reactive Streams, subscriber gọi `request(n)` thông báo muốn nhận tối đa n phần tử tiếp theo. Publisher có thể emit tới số lượng đã yêu cầu theo hợp đồng. Reactor operators phối hợp demand qua chuỗi khi nguồn hỗ trợ.

Ví dụ:

~~~java
Flux<Integer> source = Flux.range(1, 100);

Flux<Integer> boundedDemand = source.limitRate(8);
~~~

`limitRate(8)` chia demand lớn downstream thành các yêu cầu nhỏ hơn upstream, có cơ chế replenishment. Nó **không** đặt `sleep(1/8s)`; một nguồn dữ liệu có sẵn có thể phát cả 100 phần tử rất nhanh.

### 4.1. `limitRate` không phải bandwidth throttle

~~~java
flux.limitRate(32);
~~~

Điều trên là `request(n)` batching cho Reactor; nó không giới hạn mỗi phần tử là bao nhiêu bytes. Nếu mỗi DataBuffer 64 KiB thì 32 phần tử tương đương tới khoảng 2 MiB dữ liệu; nếu mỗi phần tử là vài bytes thì chỉ rất nhỏ. Hơn nữa, upstream/bridges không hợp tác có thể đã buffer sẵn.

`limitRate(highTide, lowTide)` cho phép cấu hình nhu cầu/replenishment. `lowTide=0` giúp request theo lô nghiêm ngặt hơn, nhưng vẫn không phải token bucket theo clock.

### 4.2. `take(n)`/limitRequest không thay byte limit

~~~java
Flux<String> firstTen = source.take(10);
~~~

Câu lệnh này chỉ lấy **10 phần tử**, bất kể mỗi phần tử dài 10 bytes hay 10 MiB. Nếu một NDJSON document hoặc DataBuffer khổng lồ được phát thành một phần tử, `take(10)` không bảo vệ memory/bytes.

Reactor còn có `limitRequest(n)` để giới hạn tổng demand qua operator theo tài liệu phiên bản; ý nghĩa là giới hạn **số phần tử**, không phải số bytes hoặc thời gian.

### 4.3. `delayElements` không tương đương rate limit production

~~~java
Flux.range(1, 5)
    .delayElements(Duration.ofMillis(200));
~~~

Đây là minh họa phát các phần tử với độ trễ. Dùng timer làm demo đơn giản, nhưng `delayElements` **không** tự quản lý global quota theo tenant hoặc bandwidth thật khi có hàng nghìn clients. Nếu source là hot, dữ liệu có thể buffer/drop/overflow trước khi operator nhận; nếu downstream slow, memory/queue vẫn cần chính sách.

Muốn điều tiết bytes/giây thực tế, thiết kế token bucket/rate limiter trên **bytes đã đọc hoặc gửi**, và đảm bảo mechanism giới hạn được đường dữ liệu thật, không chỉ thêm `Thread.sleep` trên event loop.

## 5. Codec `maxInMemorySize`: phạm vi thực sự là gì?

Spring WebFlux dùng HTTP message readers và decoders để chuyển bytes thành DTO, JSON, form fields, multipart và text.

**`maxInMemorySize` không phải giới hạn tổng byte của mọi HTTP request hoặc của toàn bộ stream.** Theo Spring Framework reference:
- Với `@RequestBody byte[]` hoặc object cần aggregate, decoder có thể gom dữ liệu và limit áp vào phần được gom.
- Với NDJSON/JSON stream, reader có thể gom **từng JSON object** trước khi emit; limit thường áp vào kích thước của **một object**, không phải tổng vô hạn objects.
- Với form fields, các phần có thể được gom theo contract của reader.
- Với multipart parsed reader, limit thường áp non-file parts; đối với file parts, limit là **ngưỡng spool ra disk**, còn max disk usage và số parts có giới hạn khác.
- Với `Flux<DataBuffer>` raw body, bạn vẫn cần **hard byte count** ở ứng dụng/ingress nếu muốn giới hạn toàn request.

### 5.1. Cấu hình codecs server-side

~~~java
@Configuration
public class CodecBudgetConfig implements WebFluxConfigurer {
    @Override
    public void configureHttpMessageCodecs(
            ServerCodecConfigurer configurer) {
        configurer.defaultCodecs()
                .maxInMemorySize(256 * 1024);
    }
}
~~~

Đây là class minh họa; phải import `Configuration`, `WebFluxConfigurer`, `ServerCodecConfigurer`. Trong Spring Boot 3.x, cũng có thể cấu hình property:

~~~properties
spring.codec.max-in-memory-size=256KB
~~~

Chọn **một chính sách được kiểm thử**; đừng thêm nhiều custom codec configurer mâu thuẫn nhau. Giá trị 256 KiB là ví dụ và đồng thời là default thường được Spring docs nêu ở WebClient codecs; kiểm tra theo Boot version/codec. Kích thước phù hợp phụ thuộc kích thước **một DTO/object** dự kiến chứ không phải tổng file upload.

### 5.2. DataBufferLimitException

Khi decoder/join cố buffer vượt ngưỡng, bạn có thể gặp:

~~~text
org.springframework.core.io.buffer.DataBufferLimitException:
Exceeded limit on max bytes to buffer
~~~

Đừng “sửa” bằng cách tăng lên vô hạn hoặc `-1` mà chưa biết **vì sao cần aggregate**. Các bước hợp lý:
1. Xác định điểm gom: `byte[]`, `String`, JSON object rất lớn, `collectList`, `join`, multipart fields.
2. Nếu business chỉ cần parse theo dòng/record, dùng `Flux<Record>` với format streaming phù hợp (NDJSON, SSE).
3. Nếu cần file lớn, dùng `Flux<DataBuffer>`/FilePart streaming thay vì `byte[]`.
4. Nếu object cố hữu lớn và đã có hard business limit, tăng riêng budget vừa đủ và test.
5. Đặt ingress/body size limit và application cumulative count độc lập.

### 5.3. WebClient có codec budget riêng

~~~java
WebClient client = WebClient.builder()
        .codecs(c -> c.defaultCodecs()
                .maxInMemorySize(256 * 1024))
        .build();
~~~

Đây là snippet trong code có imports. Nếu dùng `bodyToMono(String.class)` với response nhiều MiB, nó có thể phải aggregate thành một String và bị giới hạn. Nếu remote API hỗ trợ NDJSON, bạn có thể dùng `bodyToFlux(Record.class)`, với object bound và HTTP connection limits; không tự động bảo vệ tổng số records vô hạn nếu code sau đó gọi `collectList`.

## 6. Tránh các operator vô tình gom toàn bộ stream

### 6.1. Các anti-pattern thường gặp

~~~java
Mono<List<Item>> all = itemFlux.collectList();
Mono<Map<String, Item>> map = itemFlux.collectMap(Item::id);
Mono<byte[]> bytes = webClient.get()
        .uri("/large-download")
        .retrieve()
        .bodyToMono(byte[].class);
Mono<DataBuffer> combined =
        DataBufferUtils.join(buffers);
~~~

Đây là **đoạn minh họa** với `Item`, `itemFlux`, `webClient`, `buffers` và URI giả định. Các API có use case hợp lý khi dữ liệu **nhỏ, đã giới hạn, hữu hạn**; nhưng chúng tạo vùng tích lũy:
- `collectList()` giữ các object cho đến khi source complete; source vô hạn không bao giờ complete.
- `collectMap()` giữ entries theo keys, có thể tăng theo data cardinality.
- `bodyToMono(byte[].class)` cần biểu diễn response bằng một byte array.
- `DataBufferUtils.join(buffers)` gom/compose mọi DataBuffer từ source, có thể giữ references/nội dung tăng không giới hạn.
- `sort()`, `reduce` với accumulator growing list/string cũng có thể aggregate nhiều dữ liệu.
- `cache()`, `replay()` không có giới hạn hoặc sink replay-all lưu history lâu dài.

**Ưu tiên:** xử lý từng phần tử, ghi vào sink có giới hạn, sử dụng transactional batches có kích thước kiểm soát hoặc phân trang với cursor. Với request vô hạn, không dùng `collectList` trừ khi đã `take(n)` hữu hạn và giới hạn kích thước từng phần tử.

### 6.2. Khi nào `collectList` vẫn được phép?

~~~java
return itemFlux
        .take(20)
        .collectList();
~~~

Được khi:
- Từng Item cũng có kích thước tối đa hợp lệ.
- 20 Items là một giới hạn nghiệp vụ phù hợp.
- Không có operator upstream nào đã aggregate dữ liệu lớn.
- Đã kiểm soát remote call / timeout / overall response size.
- Có thể chấp nhận materialize 20 Items trước khi trả response.

`take(20)` không thay validation max size of one item. Với pagination, hãy trả `Page/Slice` hữu hạn có `limit` ở database thay vì tải hàng triệu rows về rồi `take(20)` ở application.

### 6.3. `DataBufferUtils.join(..., maxByteCount)`

Khi bắt buộc hợp nhất **một payload nhỏ đã có hard maximum**, sử dụng overload giới hạn:

~~~java
Mono<DataBuffer> joined =
        DataBufferUtils.join(buffers, 64 * 1024);
~~~

Javadoc xác nhận khi vượt `maxByteCount`, `DataBufferLimitException` được phát và buffers đã tích lũy được giải phóng theo contract. Đây **không** phải cách stream vô hạn: nó vẫn cần gom đến khi source hoàn tất trong limit.

Nếu bạn nhận buffer kết quả, chính consumer phải release **buffer đã join** sau khi xử lý:

~~~java
return DataBufferUtils.join(buffers, 64 * 1024)
        .map(joinedBuffer -> {
            try {
                return decodeBoundedPayload(joinedBuffer);
            } finally {
                DataBufferUtils.release(joinedBuffer);
            }
        });
~~~

Đây là snippet minh họa có `decodeBoundedPayload` bên ngoài. Không tự release từng source buffers một lần nữa sau khi join đã nhận ownership theo Javadoc; làm vậy dễ double-release. Đừng suy rằng tăng `maxByteCount` giúp một stream hàng GiB “non-blocking”.

## 7. DataBuffer: giữ đúng ownership khi đếm kích thước

Bài về **DataBuffer, bộ nhớ pool và trách nhiệm giải phóng** đã phân tích đầy đủ `PooledDataBuffer`, `retain/release`, `join`, `write` và leak detection. Ở bài này, điều cần áp dụng là: **nếu chính mã của bạn tiêu thụ một `DataBuffer` chỉ để đếm bytes rồi bỏ, hãy release sau khi đọc; nếu chuyển buffer cho HTTP writer thì không release sớm**.

| Trường hợp trong pipeline giới hạn bytes | Ai chịu trách nhiệm |
| --- | --- |
| Đọc `readableByteCount()` rồi không chuyển buffer đi | Mã consumer gọi `DataBufferUtils.release(buffer)` trong `finally` |
| Chuyển `Flux<DataBuffer>` vào response writer | Writer nhận ownership; không release trước `writeWith` |
| `DataBufferUtils.join(source, maxByteCount)` | Utility quản lý các buffer nguồn; consumer release **joined result** khi đã dùng |
| Operator bỏ buffer khi filter/cancel | Dùng `doOnDiscard(DataBuffer.class, DataBufferUtils::release)` ở vị trí được operator hỗ trợ |
| Ghi stream sang file | Theo đúng contract của overload `DataBufferUtils.write(...)` hoặc `FilePart.transferTo(...)`; cleanup file dở khi hủy |

Ví dụ khi bộ đếm **tiêu thụ** một buffer, không truyền nó đi tiếp:

~~~java
int size;
try {
    size = dataBuffer.readableByteCount();
} finally {
    DataBufferUtils.release(dataBuffer);
}
~~~

Đây là snippet trong method có biến `DataBuffer dataBuffer` và import `DataBufferUtils`. Với nhánh xuất response, đảo chiều ownership: bàn giao cho writer thay vì release ở đây. Không gọi release **hai lần**, và nhớ giới hạn số buffers đang chờ xử lý, không chỉ kích thước một buffer.

**Tránh stream giả:** `Files.readAllBytes(path)` rồi `Mono.just(byteArray)` vừa dùng filesystem API blocking vừa materialize toàn bộ file. Khi dữ liệu lớn, dùng `Resource`/`Flux<DataBuffer>` streaming với byte cap, timeout và cancellation cleanup. `doOnDiscard` chỉ bổ sung cho những operators hỗ trợ discard, không tự xử lý mọi reference bị ứng dụng giữ trong collection riêng.

## 8. Đếm bytes trên stream thay vì đọc toàn bộ: một pattern an toàn

Cần bảo vệ endpoint upload binary với hard limit **64 KiB** trong demo. Body vẫn được nhận qua `Flux<DataBuffer>`, nhưng chỉ lưu một **biến đếm `long`** và release từng buffer:

~~~java
return Mono.defer(() -> {
    AtomicLong bytes = new AtomicLong();
    return body.concatMap(buffer -> {
        try {
            int n = buffer.readableByteCount();
            if (n > MAX_BYTES - bytes.get()) {
                return Mono.<Void>error(
                    new ResponseStatusException(
                        HttpStatus.PAYLOAD_TOO_LARGE,
                        "body exceeds limit"));
            }
            bytes.addAndGet(n);
            return Mono.<Void>empty();
        } finally {
            DataBufferUtils.release(buffer);
        }
    }, 1)
    .doOnDiscard(DataBuffer.class, DataBufferUtils::release)
    .then(Mono.fromSupplier(() -> new Receipt(bytes.get())));
});
~~~

Đây là **method body minh họa**; full controller ở mục 13 có imports và route hoàn chỉnh. Lý do quan trọng:

1. `Mono.defer` tạo state đếm **cho từng subscription**, không để counter mutable trong controller singleton.
2. `concatMap(..., 1)` xử lý lần lượt và cấu hình prefetch nhỏ, tránh mất thứ tự/đua updates trong code demo.
3. Kiểm tra `n > MAX_BYTES - bytes.get()` thay vì `bytes.addAndGet(n) > MAX_BYTES` cho phép tránh overflow nếu counters lớn hơn trong ứng dụng thực.
4. `finally` luôn gọi `DataBufferUtils.release(buffer)` sau khi đọc số byte, kể cả khi trả error.
5. Khi đã vượt ngưỡng, `Mono.error` dẫn tới HTTP 413 khi response chưa commit và upstream subscription bị hủy. **Không cần** đọc phần body còn lại vào RAM chỉ để chứng minh vượt hạn mức.
6. `.doOnDiscard(...)` là bảo vệ **bổ sung** cho discard-aware operators, không thay release chính.
7. Bởi vì response là Receipt nhỏ trả **sau khi body được tiêu thụ hoặc error**, không cần giữ toàn body để tạo JSON.
8. Demo không đo tốc độ gửi bytes/giây hoặc ngăn slowloris; cần giới hạn ở ingress/transport.

### 8.1. `Content-Length` có đáng tin không?

Có thể kiểm tra sớm `Content-Length` trước khi đọc, ví dụ để từ chối request có header công bố 1 GiB trong khi quota là 10 MiB. Nhưng:
- Request có thể không có Content-Length (chunked/streaming).
- Header có thể bị proxy chuyển đổi.
- Client không nên được tin để quyết định quota; phải kiểm tra **số bytes thực nhận**.
- Body đã được decode có thể khác kích thước **compressed/encoded bytes**: cần xác định limit đang áp trên wire bytes, decompressed bytes hay business payload.
- Compression/decompression bombs cần hard cap sau giải nén nếu app hỗ trợ compressed uploads.

Do đó, kiểm tra Content-Length là fast-fail optimization, **không thay** streaming cumulative bytes guard.

## 9. Kiểm soát tốc độ tiêu thụ và sự chênh lệch upstream/downstream

### 9.1. Backpressure khi upstream hợp tác

Trong Reactor pipeline:

~~~java
Flux<Item> stream = repository.findMany()
        .limitRate(16)
        .flatMap(this::sendToUpstream, 4);
~~~

Đây là snippet với `repository`, `Item`, `sendToUpstream` giả định. `limitRate(16)` điều chỉnh request batching; `flatMap(..., 4)` giới hạn concurrent inner subscriptions. Nếu downstream HTTP API chỉ cho 10 QPS, **4 concurrent** chưa bảo đảm <=10 QPS; cần rate limiter/capability ở downstream.

Một phiên bản tuần tự:

~~~java
Flux<Result> stream = items
        .concatMap(item -> process(item));
~~~

`concatMap` xử lý lần lượt inner streams, bảo toàn thứ tự; throughput có thể thấp hơn `flatMap`. Nếu processing từng item là blocking, `concatMap` không tự đưa nó khỏi event loop; cần client non-blocking hoặc bounded offload.

### 9.2. Tốc độ items khác tốc độ bytes

Nếu phải giới hạn **1 MiB/giây**, chỉ gọi `limitRate(100)` trên `Flux<DataBuffer>` là sai nếu buffer size không cố định. Bạn cần một **rate limiter xét từng số byte thực**, ví dụ token bucket đo elapsed time có concurrency-safe state và admission budget, hoặc giới hạn egress ở reverse proxy/gateway/object storage.

Một cơ chế làm chậm reactive source có thể là `delayElements`, nhưng:
- Chỉ phù hợp dữ liệu nhỏ, cold source với số item kiểm soát, không phải một hệ thống bandwidth throttling chuẩn.
- Không tự giới hạn nhiều requests cùng tenant.
- Nếu source liên tục phát nhanh hơn, phải biết dữ liệu đang nằm ở queue nào.
- **Không gọi `Thread.sleep` trên Reactor Netty event loop**; nó block cả những kết nối khác cùng event-loop thread.

### 9.3. FlatMap prefetch và buffers

`flatMap`, `publishOn`, `buffer`, `window`, `groupBy` có prefetch/queue semantics riêng. Khi một pipeline gom/chạy quá nhiều inner operations, có thể giữ nhiều objects trước khi downstream xử lý. Hãy:
1. Chọn explicit concurrency budget ở `flatMap(fn, concurrency)`.
2. Cân nhắc overload prefetch phù hợp version nếu xử lý nhiều items lớn.
3. Cân nhắc `concatMap` cho những sources cần thứ tự và bounded memory.
4. Tránh `groupBy` với cardinality không có giới hạn mà không có tiêu thụ group đầy đủ.
5. Không buffer toàn bộ streaming source vào `collectList` trong terminal.
6. Đo active memory và queue length dưới tải thật.

### 9.4. Hot sources và policies khi vượt tốc

Với `Sinks.Many` hoặc message broker, producer có thể phát khi downstream chưa sẵn sàng. Reactor có các toán tử:

~~~java
source.onBackpressureLatest();
source.onBackpressureDrop();
source.onBackpressureBuffer(128);
~~~

Đây là những snippet minh họa, mỗi biểu thức trả `Flux` mới; trong ứng dụng phải compose vào return chain. Semantics:
- **Latest**: có thể bỏ dữ liệu cũ, giữ cập nhật gần nhất khi không đủ demand.
- **Drop**: có thể bỏ events khi thiếu demand.
- **Buffer(128)**: giữ bounded số elements, rồi phát overflow error theo overload/policy.
- Buffer size là **số elements**, không phải bytes. Một object 10 MiB vẫn gây áp lực lớn.
- Chính source đó có thể có buffer riêng trước operator; giới hạn ở một operator không bảo vệ mọi boundary.

Dùng drop/latest với telemetry/dashboard nơi chỉ cần trạng thái mới nhất; **không được drop** giao dịch/đơn hàng/audit events phải giao đủ. Khi cần replay đáng tin, phải có durable event log + cursor/idempotency, không chỉ một sink giữ in-memory.

## 10. Streaming response: NDJSON, SSE và binary bytes

### 10.1. JSON array thường được xem như một document

Response `application/json` với body `[{"id":1}, ...]` có thể buộc consumer cần chờ đủ array hoặc có cơ chế streaming parser đặc biệt. Nếu muốn processing record-by-record, sử dụng media type hỗ trợ framing, ví dụ:
- `application/x-ndjson` cho **một JSON object mỗi dòng**.
- `text/event-stream` cho SSE events (`data:`, optional IDs).
- `application/octet-stream` khi streaming bytes/file nhị phân.
- Protocol/framing riêng nếu hai bên thống nhất.

Chọn format dựa client decoding và business contract, không chỉ đổi `produces` mà vẫn `collectList()` trước return.

### 10.2. NDJSON example

~~~java
@GetMapping(path = "/numbers",
        produces = MediaType.APPLICATION_NDJSON_VALUE)
public Flux<StreamItem> numbers() {
    return Flux.range(1, 5)
            .delayElements(Duration.ofMillis(70))
            .map(i -> new StreamItem(i, "item-" + i));
}
~~~

Đây là method sẽ có class/imports hoàn chỉnh ở mục 13. Trả Flux để codec encode từng item, tránh gom năm items trước khi ghi HTTP. `delayElements` dùng cho quan sát demo; **không** phải business throughput guarantee.

### 10.3. SSE khi số updates không xác định

~~~java
@GetMapping(path="/events",
        produces=MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> events() {
    return Flux.interval(Duration.ofSeconds(1))
            .map(i -> ServerSentEvent.<String>builder()
                    .event("tick")
                    .data("tick-" + i)
                    .build());
}
~~~

Đây là snippet trong WebFlux controller có imports. Browser dùng `EventSource`/named event listener. Đây là **Flux vô hạn**, do đó **đừng gọi `collectList()`** trên stream, và trong integration test phải `take(n)` rồi cancel. SSE connection còn cần heartbeat, proxy flushing, active connection cap và replay policy khi disconnect.

### 10.4. Binary download theo chunks

Một HTTP file/download response có thể trả `Flux<DataBuffer>` tạo từ `DataBufferUtils.read(Path, DataBufferFactory, bufferSize)` hoặc `Resource` phù hợp:

~~~java
Flux<DataBuffer> chunks = DataBufferUtils.read(
        filePath, new DefaultDataBufferFactory(), 8192);
~~~

Snippet có `Path filePath`. Nó không đòi bạn tự đọc `Files.readAllBytes(filePath)` trước, nhưng cần:
- Kiểm tra authorization trước khi mở file.
- Đường dẫn/size được kiểm soát và chống path traversal.
- Để HTTP writer tiêu thụ/release buffers theo contract. Không release từng buffer **trước** khi HTTP writer nhận nó.
- Xử lý source error/cancellation và cleanup nguồn.
- Không mở hàng nghìn file handles vô hạn qua một endpoint public.
- Theo dõi HTTP range/ETag/cache policy khi hỗ trợ resume download.

Nếu sử dụng Filesystem blocking APIs, hãy chọn `DataBufferUtils.read` hoặc `Resource`/HTTP resource writer theo đúng context thay vì tự đọc synchronous file trong event loop.

## 11. WebClient: không tự aggregate response lớn

Một microservice làm gateway gọi upstream `WebClient` có thể vô tình chuyển từ streaming thành aggregate.

### Anti-pattern

~~~java
Mono<byte[]> all = webClient.get()
        .uri("/api/huge-file")
        .retrieve()
        .bodyToMono(byte[].class);
~~~

Dữ liệu phải biểu diễn thành một byte[] trước khi consumer nhận. Nếu muốn relay stream:

~~~java
Flux<DataBuffer> body = webClient.get()
        .uri("/api/huge-file")
        .retrieve()
        .bodyToFlux(DataBuffer.class);
~~~

Đây là snippet: `body` trả raw `DataBuffer` từ WebClient; nếu trực tiếp nối vào HTTP server response, **phải hiểu ownership** và đảm bảo downstream writer nhận/release đúng. Nếu subscribe để tự đọc hoặc bỏ stream, cần cleanup buffers. Không tự `.subscribe()` rồi return 200 trước khi upstream complete.

Với JSON nhỏ đã giới hạn, `bodyToMono(Dto.class)` hoàn toàn hợp lý; mục tiêu là **biết phần nào cần aggregate**.

### Các hạn mức outbound cần đặt

- WebClient codec `maxInMemorySize` cho buffered decoding.
- Remote response body total bytes limit nếu consumer không cho phép file lớn.
- HTTP client connection pool max connections, pending acquire timeout.
- Connect/response/read timeouts và tổng business deadline.
- Rate limit và concurrency budget per upstream/tenant.
- Retry bounded + backoff khi idempotent, không retry vô hạn.
- Max redirects/trusted hosts để tránh SSRF trong proxy download feature.
- Metrics về buffered bytes và downstream slow consumers.

Không coi `timeout(Duration.ofSeconds(5))` là giới hạn bytes: một upstream có thể gửi hàng GiB rất nhanh trước 5 s. Ngược lại, chỉ có size limit mà không có timeouts cho phép client cực chậm giữ connection lâu.

## 12. Một ví dụ số học minh họa budget

Giả sử hệ thống có:
- 4 instances.
- Mỗi instance nhận tối đa 100 streaming requests đồng thời.
- Mỗi request cho phép 64 KiB application buffer theo pipeline.
- HTTP writer còn có network buffers và event-loop queues.
- Downstream storage pool tối đa 10 concurrent writes/instance.

Một budget phần application buffer lý tưởng:

~~~text
100 requests × 64 KiB = 6,400 KiB ≈ 6.25 MiB / instance.
4 instances × 6.25 MiB = 25 MiB (chỉ một loại buffer).
~~~

**Đây là phép tính ví dụ**, không phải số liệu đo; tổng RSS/heap/direct memory thực có thể lớn hơn đáng kể vì decoder, TCP, TLS, gói request đang bay, object headers, temporary files và GC. Số threads ít không chứng minh low memory nếu buffer per request và total connections vẫn tăng.

Nếu queue dùng `onBackpressureBuffer(128)` với mỗi item là 1 MiB, bản thân queue đó có thể giữ tới khoảng **128 MiB cho một subscriber**, chưa kể overhead. Cần cap **byte size** ngoài element count khi item size biến thiên.

### Đo ở đâu?

| Metric | Nên theo dõi |
| --- | --- |
| JVM heap/live objects | Bộ nhớ được giữ và tần suất GC |
| Direct/native buffer allocation | Leak Netty pooled buffers, off-heap pressure |
| Active connections/in-flight streams | Số streams ở server/client |
| Request/response bytes | P50/p95/p99 size và user/tenant quota |
| Processing latency & p99 | Kể cả timeout/error |
| Rate-limited/rejected requests | 413/429/503 và nguyên nhân |
| Reactor scheduler queue | BoundedElastic blocking work saturation |
| WebClient connection pending | Pool saturation, upstream slow |
| DB/Redis pool/queries | Thực sự xử lý dữ liệu tới đâu |
| Downstream consumer lag | Có backpressure, buffers hay dropped events |
| Temp disk/object storage staging | Upload có spool hoặc chưa cleanup |

Một biểu đồ “RPS tăng” không đủ nếu p99 latency, 413, 503, direct memory hoặc GC pauses cũng tăng.

## 13. Ví dụ thực hành Spring Boot 3.x / Java 17

Dự án minh họa gồm:
- `POST /api/stream/measure`: nhận **`application/octet-stream`**, đếm bytes qua `Flux<DataBuffer>` rồi release từng buffer, giới hạn **64 KiB/request**, trả JSON `{"bytes":N}`.
- `GET /api/stream/numbers`: trả **NDJSON streaming** gồm 5 record, không gom thành một List trong controller.
- `GET /api/stream/ticks`: trả **SSE** từ `Flux<ServerSentEvent<String>>`, không hoàn tất cho tới khi cancel.
- `WebTestClient`: kiểm tra 4 bytes được chấp nhận, 65,537 bytes trả 413, NDJSON nhận từng record và SSE được cancel sau `take(2)`.
- `WebFluxConfigurer` áp decoder budget 256 KiB. **Giới hạn upload 64 KiB là logic khác**, không lệ thuộc codec `maxInMemorySize`.

Demo **không** bao gồm auth, admission control, gateway max-body config, slowloris defense, virus scanning hay multi-tenant quotas; bổ sung các lớp đó trước khi triển khai public.

### 13.1. Project dependencies

Tạo Spring Initializr Maven project với Java 17+, Spring Boot 3.x, `Spring Reactive Web` và `Spring Boot Starter Test`. Đảm bảo có:

~~~xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
~~~

Đây là **fragment** đặt trong `pom.xml` do Initializr sinh sẵn, không phải toàn bộ file Maven độc lập. Nếu vừa thêm `spring-boot-starter-web` vừa thêm WebFlux, Boot 3.x thường ưu tiên MVC application type; chỉ dùng WebFlux starter cho bài demo.

Cấu trúc:

~~~text
src/main/java/com/example/demo/
    DemoApplication.java
    StreamCodecConfig.java
    StreamController.java
src/test/java/com/example/demo/
    StreamControllerTest.java
~~~

### 13.2. DemoApplication.java

~~~java
package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
~~~

### 13.3. StreamCodecConfig.java

~~~java
package com.example.demo;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
public class StreamCodecConfig implements WebFluxConfigurer {

    @Override
    public void configureHttpMessageCodecs(
            ServerCodecConfigurer configurer) {
        configurer.defaultCodecs()
                .maxInMemorySize(256 * 1024);
    }
}
~~~

Nhắc lại: đoạn codec này giới hạn phần **decoder buffering**, không bảo vệ tổng upload size. Nếu muốn giới hạn 64 KiB total body, route sẽ **đếm bytes thật**.

### 13.4. StreamController.java

~~~java
package com.example.demo;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/stream")
public class StreamController {

    private static final long MAX_BYTES = 64L * 1024;

    @PostMapping(path = "/measure",
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Mono<ByteReceipt> measure(
            @RequestBody Flux<DataBuffer> body) {

        return Mono.defer(() -> {
            AtomicLong bytes = new AtomicLong();

            return body.concatMap(buffer -> {
                        try {
                            int n = buffer.readableByteCount();

                            // Check remaining budget before addition:
                            if (n > MAX_BYTES - bytes.get()) {
                                return Mono.<Void>error(
                                    new ResponseStatusException(
                                        HttpStatus.PAYLOAD_TOO_LARGE,
                                        "raw body exceeds 64 KiB"));
                            }

                            bytes.addAndGet(n);
                            return Mono.<Void>empty();
                        } finally {
                            DataBufferUtils.release(buffer);
                        }
                    }, 1)
                    .doOnDiscard(
                        DataBuffer.class, DataBufferUtils::release)
                    .then(Mono.fromSupplier(() ->
                            new ByteReceipt(bytes.get())));
        });
    }

    @GetMapping(path = "/numbers",
            produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<StreamItem> numbers() {
        return Flux.range(1, 5)
                .delayElements(Duration.ofMillis(70))
                .map(i -> new StreamItem(i, "item-" + i));
    }

    @GetMapping(path = "/ticks",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> ticks() {
        return Flux.interval(Duration.ofMillis(100))
                .map(n -> ServerSentEvent.<String>builder()
                        .event("tick")
                        .data("tick-" + n)
                        .build());
    }

    public record ByteReceipt(long bytes) {}

    public record StreamItem(int sequence, String label) {}
}
~~~

**Phân tích tính an toàn và giới hạn:**
1. Controller là singleton bean nhưng state `AtomicLong` được tạo trong `Mono.defer` **theo từng subscription**, không dùng field shared.
2. `concatMap(..., 1)` xử lý buffers theo thứ tự với prefetch nhỏ; mỗi buffer được release trong finally.
3. Nếu vượt MAX_BYTES, Mono phát `ResponseStatusException(413)` **trước khi trả body Receipt**, không cần giữ toàn body trong RAM. Upstream có thể được cancel thay vì đọc hết request.
4. `MAX_BYTES - bytes.get()` tránh cộng vượt ngưỡng; `long` giúp đếm tổng bytes với headroom lớn hơn `int`.
5. `doOnDiscard` bổ sung cho các operators có cơ chế discard; không chứng minh mọi buffer đã được framework cấp phát nhưng chưa deliver đều sẽ được dọn trong mọi network error case. Cần integration/stress tests dưới Netty pooled buffer leak detector khi dùng direct buffers.
6. `numbers` trả `Flux<StreamItem>` NDJSON 5 items hữu hạn; codec có thể encode/flush từng item. Không gọi `collectList` trong controller.
7. `ticks` phát SSE vô hạn; phải hủy subscription khi client rời. Một ứng dụng thật cần connection cap, heartbeat/proxy config và source lifecycle cleanup.
8. `delayElements`/timer ở đây **chỉ phục vụ quan sát streaming**; không phải hệ thống limit toàn bộ bandwidth hay QPS.
9. Demo không tự tạo file hay publish upload, nên bài toán dọn staging file không xuất hiện. Khi chuyển sang upload file thật, cần staging/atomic publish/cancel cleanup.
10. Cấu hình codec 256 KiB không làm mất hiệu lực giới hạn total bytes 64 KiB: hai chính sách ở hai tầng khác nhau.

### 13.5. StreamControllerTest.java — HTTP integration tests

~~~java
package com.example.demo;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StreamControllerTest {

    @LocalServerPort
    int port;

    WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Test
    void acceptsSmallBodyAndCountsExactBytes() {
        client.post()
                .uri("/api/stream/measure")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .bodyValue(new byte[] {1, 2, 3, 4})
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.bytes").isEqualTo(4);
    }

    @Test
    void rejectsActualBodyLargerThan64KiB() {
        client.post()
                .uri("/api/stream/measure")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .bodyValue(new byte[65_537])
                .exchange()
                .expectStatus().isEqualTo(413);
    }

    @Test
    void ndjsonCanBeReadIncrementally() {
        List<StreamController.StreamItem> data =
                client.get()
                        .uri("/api/stream/numbers")
                        .accept(MediaType.APPLICATION_NDJSON)
                        .exchange()
                        .expectStatus().isOk()
                        .expectHeader().contentTypeCompatibleWith(
                            MediaType.APPLICATION_NDJSON)
                        .returnResult(StreamController.StreamItem.class)
                        .getResponseBody()
                        .take(3)
                        .collectList()
                        .block(Duration.ofSeconds(5));

        assertNotNull(data);
        assertEquals(3, data.size());
        assertEquals(1, data.get(0).sequence());
        assertEquals("item-3", data.get(2).label());
    }

    @Test
    void infiniteSseIsBoundedInTestWithTakeTwo() {
        List<ServerSentEvent<String>> data =
                client.get()
                        .uri("/api/stream/ticks")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .exchange()
                        .expectStatus().isOk()
                        .returnResult(
                            new ParameterizedTypeReference<
                                    ServerSentEvent<String>>() {})
                        .getResponseBody()
                        .take(2)
                        .collectList()
                        .block(Duration.ofSeconds(5));

        assertNotNull(data);
        assertEquals(2, data.size());
        assertEquals("tick", data.get(0).event());
        assertEquals("tick-0", data.get(0).data());
        assertEquals("tick-1", data.get(1).data());
    }
}
~~~

**Điểm cần hiểu trong test:**
- `@SpringBootTest(RANDOM_PORT)` và `WebTestClient.bindToServer` dùng embedded HTTP server thực; không chỉ gọi method Java.
- Test size 65,537 bytes vượt 65,536 bytes. Nếu gateway hay client connector xử lý error sớm làm socket close, đôi khi network layer kết thúc trước khi có body lỗi; cần cấu hình và integration testing tương ứng khi production route rất lớn. Với demo và request nhỏ vừa quá hạn, dự kiến HTTP 413.
- `take(3)` đặt **trong test subscriber** để không gom dữ liệu vô hạn; server `numbers` vốn chỉ có 5 items. Test này không chứng minh production throughput hoặc streaming flush qua nginx.
- `take(2)` khiến subscriber hủy SSE sau 2 events, tránh `collectList()` chờ một Flux vô hạn.
- `.block(Duration.ofSeconds(5))` nằm **chỉ ở JUnit test thread**; không dùng `.block()` bên trong Spring WebFlux handler/event loop.
- Nếu muốn chứng minh thực sự stream chứ không buffer toàn response, nên thêm test quan sát **thời điểm nhận từng element** (ví dụ StepVerifier trong client-side test), vì `collectList()` hữu hạn ở test cuối cùng vẫn giữ danh sách nhỏ để assert.

Chạy:
~~~bash
./mvnw test
~~~

Windows:
~~~powershell
.\mvnw.cmd test
~~~

Nếu không có wrapper, chạy `mvn test` khi Maven đã cài. Không suy diễn test demo này đã đo memory dưới 100.000 concurrent uploads; cần stress test riêng.

## 14. Dùng curl để nhìn streaming và size limit

Chạy server:

~~~bash
./mvnw spring-boot:run
~~~

### 14.1. Request binary nhỏ

~~~bash
printf 'abcd' > input.bin
curl -i --data-binary @input.bin   -H "Content-Type: application/octet-stream"   http://localhost:8080/api/stream/measure
~~~

Kết quả dự kiến:

~~~json
{"bytes":4}
~~~

Đếm **bytes**, không phải số ký tự Unicode. File UTF-8 có dấu tiếng Việt thường nhiều bytes hơn số ký tự được nhìn thấy.

### 14.2. NDJSON streaming

~~~bash
curl -i -N   -H "Accept: application/x-ndjson"   http://localhost:8080/api/stream/numbers
~~~

Response body về mặt logic là các dòng:

~~~json
{"sequence":1,"label":"item-1"}
{"sequence":2,"label":"item-2"}
{"sequence":3,"label":"item-3"}
{"sequence":4,"label":"item-4"}
{"sequence":5,"label":"item-5"}
~~~

Trong code fence `json`, nội dung trên là **NDJSON gồm nhiều JSON objects độc lập**, không phải một JSON document hợp lệ duy nhất. `curl -N` giúp tắt buffering stdout, nhưng reverse proxy vẫn có thể buffer response nếu cấu hình sai.

### 14.3. SSE vô hạn

~~~bash
curl -N -H "Accept: text/event-stream"   http://localhost:8080/api/stream/ticks
~~~

Quan sát `event:tick`, `data:tick-0`, `data:tick-1`, ... theo wire format của codec. Nhấn `Ctrl+C` để ngắt kết nối. Không để terminal/automated test giữ stream vô hạn khi không cần.

### 14.4. Giới hạn upload

Tạo file >64 KiB rồi POST để quan sát 413:

~~~bash
python -c "open('big.bin','wb').write(b'x' * 65537)"
curl -i --data-binary @big.bin   -H "Content-Type: application/octet-stream"   http://localhost:8080/api/stream/measure
~~~

Đoạn lệnh Python chỉ tạo **file test** có 65,537 bytes; không liên quan runtime Java. Response mong đợi là HTTP 413. Một số connectors đóng request body sớm khi server trả 413; thông điệp curl về broken pipe hoặc connection reset trong tình huống này cần phân biệt với bug business logic.

Các lệnh nhiều dòng dùng `\` cho Linux/macOS/Git Bash. Windows PowerShell dùng `curl.exe` một dòng hoặc line continuation `backtick` theo PowerShell.

## 15. Streaming file upload lớn: khác demo đếm bytes

Nếu lưu file thật, thông thường:
1. Xác thực và áp quota trước khi mở storage.
2. Nhận FilePart hoặc `Flux<DataBuffer>` và đọc/ghi theo streaming.
3. Kiểm soát per-file bytes tổng, tốc độ upload tối thiểu/timeout và số uploads đồng thời.
4. Giữ staging file/object có storage key do server tạo, không dùng raw client filename.
5. Ghi theo một pass; không `collectList` rồi `Files.write`.
6. Hash/scan MIME/virus theo policy, **không dựa vào Content-Type/extension do client gửi**.
7. Nếu quota/virus/IO/error/cancellation xảy ra, dọn staging và chỉ publish khi complete.
8. Xử lý backpressure giữa inbound HTTP và disk/object-storage writer; disk write có thể blocking nếu dùng API/SDK synchronous.
9. Đảm bảo max total bytes ở ingress, app và storage; codec maxInMemorySize **không đủ**.
10. Có idempotency/retry/resumable strategy nếu file lớn và mạng không ổn định.

### `DataBufferUtils.write` cần đọc đúng overload

`DataBufferUtils.write(source, Path, OpenOption...)` trả `Mono<Void>` trong API Spring Framework 6.x; các overload nhận `OutputStream`/`WritableByteChannel` có thể trả `Flux<DataBuffer>` và **không tự release** buffers. Không copy-paste `doOnNext(DataBufferUtils.releaseConsumer())` tùy tiện vào overload có ownership khác.

Nếu dùng `FilePart.transferTo(Path)`, không đọc `file.content()` một lượt rồi transfer lại với giả định nó luôn repeatable. Chọn contract và tests cho một-pass streaming. Việc ghi thành công không đồng nghĩa scan thành công hoặc dữ liệu đã được durable replicated.

## 16. Proxy, networking và “server đang stream nhưng client không thấy”

Một pipeline WebFlux có thể emit từng item ngay, nhưng nginx/gateway/compression layer **buffer toàn bộ response**. Với NDJSON/SSE route:
- Khai báo `Content-Type` phù hợp (`application/x-ndjson`, `text/event-stream`, `application/octet-stream`).
- Kiểm tra proxy response buffering, HTTP/2 stream settings, compression buffering và flush.
- Đặt idle timeout phù hợp; với SSE có thể phát heartbeat comment-only.
- Dùng `curl -N`/browser Network Timing/packet captures để quan sát item arrival, không chỉ bytes cuối.
- Không cho response cache sai user/tenant.
- Đặt max header/body/request limits ở ingress.
- Đo slow-reader conditions; tốc độ client tải xuống cũng là bottleneck.

SSE có thể dùng comment lines `: keep-alive` để tránh im lặng quá lâu. Đây không phải giới hạn kích thước dữ liệu, và heartbeat tạo overhead khi có nhiều clients.

## 17. Timeout, cancellation và cleanup

### 17.1. Hai loại timeout cần phân biệt

- **Total deadline**: request/operation không vượt thời gian kinh doanh, ví dụ 30 giây.
- **Idle/read timeout**: client/upstream không được im lặng quá lâu, kể cả tổng chưa hết 30 giây.

Một live SSE stream có chủ đích kéo dài hàng giờ có thể **không phù hợp với total response timeout 30 giây**, nhưng vẫn cần heartbeat/idle detection và connection cap. Ngược lại, một upload phải hoàn tất trong 60 giây có thể cần absolute deadline bên cạnh minimum data rate.

### 17.2. Cancellation không phải phép màu

Khi client disconnect, Reactor có thể phát cancellation ngược upstream; các operators và I/O implementations hợp tác sẽ dừng. Tuy nhiên:
- Công việc blocking đang chạy trong worker có thể không dừng ngay.
- Một DB query/client SDK không hỗ trợ cancel có thể tiếp tục giữ connection.
- Một file đã ghi nửa chừng vẫn cần cleanup staging.
- `doFinally` gọi khi complete/error/cancel theo Reactor lifecycle, nhưng cleanup bất đồng bộ cần thiết kế riêng; không gọi `subscribe()` vô chủ trong hook.
- Pooled buffers bị discard/cancel cần được release ở đúng ownership boundaries.

### 17.3. Quá tải nên được xử lý rõ ràng

Khi queue/connections/quota đầy, có thể trả:
- **413 Payload Too Large** nếu kích thước vượt policy.
- **429 Too Many Requests** nếu user/tenant quá tốc hoặc quota.
- **503 Service Unavailable** nếu service/storage quá tải tạm thời.
- **408/504** theo failure mode HTTP client/proxy timeout, sau khi kiểm tra tầng phát lỗi.

Không giữ request trong queue vô hạn chỉ để “không làm mất việc”. Giới hạn vào (admission control) và retry có backoff thường an toàn hơn khi nguồn lực đã bão hòa.

## 18. Kiểm thử và hiệu năng đúng cách

### 18.1. Các case chức năng

| Case | Điều cần xác nhận |
| --- | --- |
| Raw body 0 bytes | Receipt 0 hoặc contract lỗi rõ |
| Raw body đúng 64 KiB | Được chấp nhận theo ngưỡng |
| Raw body 64 KiB + 1 | 413 và không ghi toàn bộ vào RAM |
| Nhiều chunks kích thước khác nhau | Tổng bytes đúng, không overflow |
| Client ngắt giữa upload | Server cancel và cleanup đúng |
| NDJSON nhiều items | Nhận từng object, không cần toàn array |
| JSON object vượt codec budget | DataBufferLimitException hoặc lỗi theo contract |
| SSE vô hạn | `take(n)`/cancel khi test, không collectList toàn stream |
| Hot producer nhanh, client chậm | Buffer/drop/overflow đúng policy |
| Proxy buffering enabled | Xác nhận hiện tượng chậm flush |
| Concurrency 1/100/1000 | Heap/direct memory/DB pools trong budgets |
| Downstream lỗi giữa stream | Cleanup và response error semantics |
| Retry nhiều lần | Không nhân tải hoặc tạo event/record trùng |
| Quota theo tenant | Không vượt limit ngay cả với nhiều concurrent requests |

### 18.2. Benchmark phải đo đúng memory

Không lấy một response 1 KiB rồi kết luận service có thể truyền 100 GiB. Nên:
1. So sánh `bodyToMono(byte[].class)` với raw chunk streaming ở **cùng throughput và payload profile**.
2. Chạy tải vừa đủ để đo RSS/direct memory/heap allocation, GC và Netty pooled buffer usage.
3. Ghi số requests thành công/timeout/413/429/503, p95/p99 latency.
4. Thử upstream fast, downstream slow, client disconnect và proxy buffering.
5. Đo queue của boundedElastic, `flatMap` concurrency, WebClient pool acquires và DB lock contention.
6. Kiểm thử overload theo thiết kế, không tổ chức tấn công tải lên hệ thống đang phục vụ production.
7. Kiểm tra data correctness (đủ bytes, checksum, không duplicate/missing chunks), vì throughput cao nhưng mất dữ liệu không phải thành công.

### 18.3. BlockHound, Java Flight Recorder và observability

Trong test phù hợp, BlockHound có thể giúp phát hiện một số blocking calls trên Reactor non-blocking threads, nhưng không chứng minh toàn pipeline không có nguồn lực bị đệm vô hạn. Java Flight Recorder/threaddumps/Netty leak detector/direct-memory metrics giúp tìm queue, allocations và chặn event loop. Kiểm tra tương thích của công cụ với Java/Reactor versions trước khi kích hoạt.

## 19. Lỗi thường gặp

| Sai lầm | Hậu quả | Cách sửa |
| --- | --- | --- |
| Nghĩ `Flux` tự tiết kiệm memory | Một operator sau nó vẫn có thể aggregate | Audit `collect*`, `join`, caches, buffers |
| Dùng `collectList` trên stream vô hạn | Không bao giờ complete, memory tăng | `take(n)`, streaming format, bounded storage |
| Đặt `maxInMemorySize` để cap tổng file upload | File/stream vẫn có thể lớn hơn limit | Total byte counter + ingress cap |
| Tăng `maxInMemorySize=-1` khi lỗi | OOM/heap spikes | Thiết kế streaming hoặc bounded DTO |
| Chỉ dùng `limitRate(20)` làm 20 items/sec | Demand batching không kiểm soát clock | Rate limiter theo thời gian |
| Chỉ dùng `take(100)` để cap bytes | Một item có thể khổng lồ | Đếm byte kích thước thực |
| `onBackpressureBuffer` không giới hạn | Slow consumers giữ queue vô hạn | Bounded buffer, overflow policy |
| Drop events giao dịch bằng latest/drop | Mất dữ liệu không phát hiện | Durable log + retry/checkpoint |
| Dùng `flatMap` concurrency cao vô tội vạ | Pool/DB/upstream saturation | Explicit concurrency budgets |
| Quên release `DataBuffer` trực tiếp consume | Netty direct memory leak | try/finally, ownership, discard tests |
| Release trước khi HTTP writer dùng buffer | Use-after-release/corrupt bytes | Writer sở hữu buffer tới khi hoàn thành |
| `DataBufferUtils.join` toàn file lớn | Gom toàn body | Stream/write hoặc join với cap nhỏ |
| `bodyToMono(byte[].class)` ở gateway | Buffer response lớn | `bodyToFlux(DataBuffer.class)` + streaming relay |
| `Thread.sleep` để giảm tốc upload | Block event loop | Non-blocking limiter/transport shaping |
| Chỉ kiểm tra Content-Length | Chunked/body thực khác header | Đếm bytes thực + ingress cap |
| Không có timeout | Slowloris/hanging streams | Deadline + read/idle timeout |
| Codec budget có nhưng số requests vô hạn | Tổng memory tăng theo concurrency | Admission/connection limits |
| Server stream nhưng client chờ đến cuối | Proxy/HTTP client buffers | Flush, correct content type, curl -N |
| Test `collectList` lên SSE vô hạn | Test treo | `take(n)` và timeout |
| Dùng MVC blocking SDK trong WebFlux event loop | Ngừng xử lý nhiều connections | Reactive driver hoặc bounded offload |
| Không cleanup file dở khi cancel | Rác disk và quota sai | Staging + cleanup, integration test |
| Retry vô hạn khi 503 | Thêm tải và queue | Bounded backoff/jitter |
| Chỉ đo RPS, bỏ p99/errors | Đánh giá sai tải khỏe | Successful RPS + SLO + resource metrics |

## 20. Câu hỏi ôn tập

**1. `limitRate(10)` có nghĩa giới hạn 10 items mỗi giây?**  
Không. Nó chia demand lên upstream theo batches, không tự áp đồng hồ hay bandwidth policy.

**2. `maxInMemorySize=256 KiB` có tự chặn mọi request trên 256 KiB?**  
Không. Nó liên quan việc reader/decoder buffer dữ liệu. NDJSON có thể chứa nhiều objects nhỏ trong một stream dài, còn raw `Flux<DataBuffer>` cần cumulative byte limit riêng.

**3. Vì sao `collectList()` trên SSE `Flux.interval` là lỗi?**  
Flux không có terminal complete trong hoạt động bình thường, nên List không thể được emit và có thể tích lũy vô hạn.

**4. Nếu chỉ cho 10 DataBuffers mà mỗi buffer 8 MiB, bộ nhớ có an toàn không?**  
Không thể suy ra: có thể giữ đến khoảng 80 MiB buffers chưa tính overhead. Cần tính byte sizes.

**5. `DataBufferUtils.join(buffers, 65536)` làm gì?**  
Nó gom/compose buffers trong budget 65,536 bytes, phát lỗi khi vượt budget. Đây vẫn là aggregation hữu hạn, không phải raw streaming.

**6. Vì sao release buffer trong `finally`?**  
Nếu code throw khi đang kiểm tra size, buffer cần được release. `finally` chạy cả success lẫn exception.

**7. Trong demo, tại sao dùng `Mono.defer`?**  
Mỗi subscription nhận một counter riêng; không để state mutable shared trong controller singleton.

**8. `Content-Length` có đủ để enforce quota không?**  
Không. Chunked requests có thể không có header, và header không phải nguồn tin cậy duy nhất. Đếm bytes đã nhận thật.

**9. Nếu WebFlux dùng WebClient mà response gọi `bodyToMono(byte[].class)`, có còn streaming không?**  
Không theo nghĩa consumer nhận từng chunks; nó phải materialize một byte array trước khi emit. Có thể dùng raw DataBuffer Flux hoặc NDJSON Flux khi phù hợp.

**10. Vì sao 413 và 429 khác nhau?**  
413 là payload vượt limit; 429 là rate/quota exceeded. Cần policy định nghĩa rõ và kiểm thử.

**11. Làm thế nào kiểm tra SSE vô hạn bằng WebTestClient?**  
Dùng `take(n)`/timeout ở test subscription, không chờ `collectList` của toàn infinite stream.

**12. Giới hạn concurrency và limit bytes có thay thế nhau không?**  
Không. Một request có thể rất lớn; ngược lại hàng nghìn requests nhỏ có thể cộng dồn thành quá tải.

## 21. Checklist triển khai

1. Vẽ toàn bộ chuỗi ingest/process/egress và xác định mọi nơi aggregate/buffer.
2. Đặt ingress max-body/timeout/rate controls và concurrent connections budget.
3. Cấu hình WebFlux `maxInMemorySize` cho decoder, hiểu giới hạn per object/part, không nhầm với total request size.
4. Dùng raw `Flux<DataBuffer>` hoặc `FilePart` phù hợp để stream body lớn; đếm cumulative bytes và stop khi vượt.
5. Release mọi DataBuffer mà ứng dụng trực tiếp consume; bảo vệ error/cancel/discard, không double-release buffer HTTP writer đang sở hữu.
6. Tránh `collectList`, `collectMap`, unbounded `join`, `cache`, `replay` trên stream không giới hạn; dùng bounded batches/records.
7. Với NDJSON/SSE, trả Flux trực tiếp, test streaming/cancellation/slow readers thay vì chờ toàn bộ data.
8. Dùng `limitRate` cho demand batching, explicit concurrency cho `flatMap`, rate limiter cho requests/bytes per second; không coi chúng là một.
9. Chọn overflow/drop/backpressure policy theo nghiệp vụ: có được mất events hay không?
10. Đặt WebClient codec/connection pool/timeout/response-byte policies và downstream storage quotas.
11. Nếu upload file, staging/scan/atomic publish/cleanup; không dùng raw filename/path hoặc Content-Length làm tin cậy tuyệt đối.
12. Kiểm tra reverse proxy buffering/flush, ingress limits, load balancer idle timeout, HTTP/2 behavior.
13. Test 0 bytes, exactly limit, limit+1, chunked uploads, client disconnect, concurrency, large objects, streaming correctness.
14. Quan sát heap + direct memory + temp disk + GC + queues + connection pools + p99/errors; thay đổi giới hạn dựa trên SLO thực tế.

## Tài liệu tham khảo chính thức

- [Spring Framework 6.2 — WebFlux Reactive Core, Limits và Multipart](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/reactive-spring.html)
- [Spring Framework 6.2 — Configuring HTTP Codecs](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/config.html)
- [Spring Framework 6.2 — WebClient Configuration: maxInMemorySize](https://docs.spring.io/spring-framework/reference/6.2/web/webflux-webclient/client-builder.html)
- [Spring Framework 6.2 — DataBufferUtils API, join và release](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/io/buffer/DataBufferUtils.html)
- [Spring Framework 6.2 — WebFlux Return Types](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/controller/ann-methods/return-types.html)
- [Spring Framework 6.2 — WebTestClient](https://docs.spring.io/spring-framework/reference/6.2/testing/webtestclient.html)
- [Project Reactor — Backpressure, limitRate and Prefetch](https://projectreactor.io/docs/core/release/reference/advancedFeatures/advanced-broadcast-multiple-subscribers-connectableflux.html)
- [Project Reactor — Reactor Sinks and Backpressure](https://projectreactor.io/docs/core/release/reference/coreFeatures/sinks.html)
- [Project Reactor — Schedulers and blocking offloading](https://projectreactor.io/docs/core/release/reference/coreFeatures/schedulers.html)
- [Reactive Streams specification](https://www.reactive-streams.org/)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-streaming-005">Cô lập tác vụ chặn với scheduler phù hợp và giới hạn đồng thời</a>

<details>
<summary>Xem chi tiết</summary>

# Cô lập tác vụ chặn với scheduler phù hợp và giới hạn đồng thời trong Streaming WebFlux

## 1. Bài toán: một lời gọi chặn có thể làm nghẽn nhiều HTTP streams

Spring WebFlux sử dụng mô hình xử lý HTTP không chặn trên một số lượng tương đối nhỏ các **event-loop threads**. Reactor Netty tiếp nhận request, giải mã và ghi response trong các luồng xử lý I/O. Vì vậy, nếu một handler chạy JDBC đồng bộ, `Thread.sleep`, `InputStream.read` trên nguồn chậm hoặc một SDK chỉ có blocking API ngay trên event loop, toàn bộ những request khác cùng chia sẻ thread đó có thể bị trì hoãn.

```text
             HTTP requests / SSE / NDJSON
                       |
                       v
           Reactor Netty event loops
             |       |       |
             |  +----+-------+-----+
             |  |  Blocking SDK   |   <- SAI: giữ event loop
             |  |  chờ 2 giây     |
             |  +-----------------+
             |
         các HTTP requests khác phải chờ

Cách tốt hơn:
 event loop -> Mono.fromCallable(...) -> boundedElastic worker
      |                                      |
      | request thread không bị giữ          | Blocking SDK/read/write
      |                                      |
      +<-------- asynchronous result --------+
                       |
             encode/write HTTP response
```

Khi chuyển blocking task khỏi event loop, bạn chỉ giải quyết **thread isolation**. Nếu 10.000 người dùng đồng thời gọi một dịch vụ legacy mà nó chỉ chịu được 20 requests, scheduler có thể có hàng nghìn task xếp hàng, độ trễ tăng, connection pool cạn và overload lan sang các hệ thống khác. Cần thêm **giới hạn đồng thời, hàng đợi có giới hạn, timeout, kiểm soát lỗi, cancellation và metrics**.

Bài học thuộc **Streaming WebFlux** vì áp lực càng rõ trên endpoint trả NDJSON/SSE/file stream: mỗi connection có thể tồn tại lâu, producer và consumer có tốc độ khác nhau, việc phát dữ liệu ở tốc độ không kiểm soát có thể gây thêm queue và direct-memory pressure.

**Baseline:** Java 17+, Spring Boot 3.5.x, Spring Framework 6.2.x, Project Reactor 3.7.x, Reactor Netty. Với Java 21+ có thêm tùy chọn virtual threads cho Reactor boundedElastic được trình bày ở phần sau.

## 2. Mục tiêu kiến thức và tiêu chí hoàn thành

Sau bài này, có thể:

1. Nhận diện thao tác **blocking** trong controller, service, codec, publisher và callback.
2. Hiểu `Schedulers.parallel()`, `single()`, `boundedElastic()` và `newBoundedElastic(...)` khác nhau thế nào.
3. Biết vì sao công thức được Reactor khuyến nghị là **`Mono.fromCallable(blockingCall).subscribeOn(Schedulers.boundedElastic())`**.
4. Phân biệt `subscribeOn` với `publishOn` và vị trí chuyển thread.
5. Dùng scheduler **riêng, có giới hạn** để tránh một dependency legacy chiếm hết global pool.
6. Điều chỉnh `flatMap(mapper, concurrency)` hoặc `flatMapSequential` để giới hạn số task **trong một stream**.
7. Phân biệt concurrency per-stream với **global concurrency** qua toàn bộ HTTP requests/nodes.
8. Dùng non-blocking admission như `Semaphore.tryAcquire` hoặc bulkhead khi cần, không gọi `acquire` trên event loop.
9. Biết timeout/cancel không tự bảo đảm dừng ngay blocking I/O hoặc rollback side effects.
10. Viết Java 17 WebFlux.fn demo và WebTestClient/StepVerifier tests mà không chặn Netty event loop.

Không có một scheduler nào làm biến mất bản chất blocking của JDBC, file I/O hoặc HTTP client đồng bộ. Nó chỉ chọn nơi hoạt động blocking được thực thi; mục tiêu dài hạn có thể là driver/reactive client đúng nghĩa hoặc chuyển công việc sang background job.

## 3. Blocking là gì? Phân loại theo nguồn

| Công việc | Có thể block? | Cách tiếp cận |
| --- | --- | --- |
| `Thread.sleep` | Có | Không gọi trên event loop; demo dùng để mô phỏng |
| JDBC `ResultSet.next` / `Connection.execute` | Có | Driver reactive như R2DBC nếu phù hợp; nếu chưa có, tách sang boundedElastic |
| `RestTemplate` / blocking HTTP SDK | Có | Ưu tiên WebClient; khi bắt buộc dùng, tách và giới hạn |
| `Files.readAllBytes` | Có và dùng RAM lớn | Stream file phù hợp; nếu API blocking, tách executor và giới hạn bytes |
| `InputStream.read` / `OutputStream.write` | Có | Cô lập nếu API đồng bộ; chú ý client chậm và resource cleanup |
| `Synchronized`/locks chờ dài | Có thể | Loại bỏ contention, không chỉ đổi scheduler |
| `Mono.block()` / `Flux.blockLast()` | Có | Không dùng trong reactive request pipeline |
| CPU-heavy JSON transformation | Không I/O, nhưng chiếm CPU | `parallel` hoặc dedicated compute pool, giới hạn công việc |
| Jackson HTTP codec thường | Có thể dùng CPU/allocations đáng kể | Giới hạn payload; codec không nên tự thêm blocking I/O |
| WebClient non-blocking | Thông thường không block thread gọi | Dùng reactive composition, connection pool/timeout/limit |
| Reactive database driver | Thiết kế non-blocking | Vẫn cần pool/concurrency/backpressure |
| `Mono.just(expensiveCall())` | **Có**, chạy expensiveCall trước | Dùng `fromCallable` để hoãn side effect |
| `Mono.fromCallable(...)` | Bản thân lazy, không tự chuyển thread | Thêm `subscribeOn(boundedElastic)` khi callable block |

Nếu thư viện được quảng cáo là “async” nhưng thực ra dùng sync I/O và giữ thread, vẫn cần đo/kiểm tra. Nếu chỉ đổi tên method thành `Mono<...>` mà bên trong gọi blocking code trên event loop, hệ thống vẫn bị nghẽn.

### 3.1. Khả năng event loop chịu tải không vô hạn

Netty event loops phù hợp các callback ngắn, không blocking. Ví dụ một event loop có 100 connections; chỉ một handler giữ thread 500 ms cũng có thể làm nhiều connections khác trễ dù CPU hầu như rảnh. Khi streaming, điều này biểu hiện thành event SSE đến thành cụm, NDJSON response chậm hoặc health endpoint có latency spikes.

Đừng sửa bằng cách tăng mù quáng số Netty event-loop threads. Nếu thread đang bị block 2 giây, thêm vài threads không giải quyết được điểm nghẽn gốc hoặc capacity của downstream.

## 4. Các Scheduler quan trọng trong Reactor

| Scheduler | Đặc điểm | Phù hợp | Không phù hợp |
| --- | --- | --- | --- |
| `Schedulers.immediate()` | Chạy task tại thread hiện có | Đoạn xử lý nhẹ | Không cô lập blocking |
| `Schedulers.single()` | Chia sẻ một thread chuyên dụng | Công việc tuần tự nhẹ | Một task blocking sẽ giữ mọi task chung |
| `Schedulers.parallel()` | Fixed pool dựa theo CPU | CPU-bound calculations ngắn | JDBC, blocking socket, file I/O chờ |
| `Schedulers.boundedElastic()` | Shared pool chuyên dành cho blocking có **giới hạn threads và pending tasks** | Legacy blocking I/O, migration | Không tự bảo vệ toàn hệ thống khỏi request flood |
| `Schedulers.newBoundedElastic(...)` | Pool riêng với cap/queue có thể định cấu hình | Isolate một dependency theo SLA/quotas | Không thay admission control của toàn app |
| `Schedulers.fromExecutorService(...)` | Bọc Java ExecutorService | Tích hợp pool hiện có có lifecycle rõ | Không nên mặc định dùng common unbounded executor |

Một số chú ý:

- `Schedulers.elastic()` cũ không được khuyến nghị để cô lập lượng blocking lớn, vì có thể tạo quá nhiều threads và che vấn đề backpressure.
- `Schedulers.parallel()` và `single()` đánh dấu threads thuộc diện **non-blocking**; gọi `block()` trên chúng có thể ném `IllegalStateException`.
- `boundedElastic` là nơi hợp lý để chờ một thư viện blocking, **không** làm cho external library có HTTP/TCP backpressure.
- Scheduler không phải Reactive Streams Subscriber. `request(n)` là tín hiệu demand; scheduler là nơi task chạy. Hai cơ chế bổ sung nhưng không thay nhau.

### 4.1. Giới hạn mặc định của boundedElastic trong Reactor 3.7

Theo Javadoc/Reactor 3.7:

- Shared `boundedElastic()` có giới hạn **tối đa mặc định 10 × số CPU processors** ở runtime cho backing platform threads.
- Mức queue mặc định là **100.000 task đang đợi trên mỗi backing thread**, không phải một hàng đợi 100.000 toàn hệ thống.
- Sau khi queue/capacity tương ứng đầy, việc schedule có thể ném **`RejectedExecutionException`**.
- Idle pools có thể được thu hồi sau một khoảng thời gian (mặc định khoảng 60 giây với platform-thread implementation).
- Các defaults có thể được điều chỉnh bằng system properties `reactor.schedulers.defaultBoundedElasticSize` và `reactor.schedulers.defaultBoundedElasticQueueSize` **trước khi global scheduler khởi tạo**.

Các số mặc định này **không phải capacity recommendation cho mọi server**. Thí dụ 8 CPUs có thể cho phép khoảng 80 backing threads và rất nhiều tasks được queue; downstream DB pool chỉ có 10 connections vẫn có thể overload hoặc gây latency lớn. 100.000 queued tasks **mỗi backing thread** là giới hạn kỹ thuật, không phải hàng đợi ngắn tốt cho interactive HTTP API.

Đừng đặt toàn bộ endpoint legacy, export lớn và log blocking vào cùng shared scheduler mà không có ranh giới, nếu từng workload có SLO và tải khác nhau.

## 5. Mẫu cơ bản đúng: fromCallable + subscribeOn

### 5.1. Sai: gọi blocking trước khi tạo Mono

```java
// Anti-pattern: legacyClient.fetch(id) chạy ngay trên
// thread hiện tại, có thể là Netty event loop.
return Mono.just(legacyClient.fetch(id));
```

`Mono.just(...)` chỉ bọc **giá trị đã được tính xong**. Java đánh giá `fetch(id)` trước khi `Mono.just` được gọi. Dù thêm `.subscribeOn(boundedElastic())` sau đó, thao tác `fetch` vẫn đã chạy **trước** khi scheduling.

### 5.2. Đúng: trì hoãn và chuyển subscription

```java
Mono<Customer> find(String id) {
    return Mono.fromCallable(() -> legacyClient.fetch(id))
            .subscribeOn(Schedulers.boundedElastic());
}
```

Đây là **fragment**, giả định `legacyClient.fetch` có blocking contract và trả một `Customer`. Nó là `Mono` lazy; callable được gọi khi subscription diễn ra trên một boundedElastic worker. Reactor khuyến nghị đặt `subscribeOn` gần nguồn blocking.

`Mono.fromCallable` có thể complete empty nếu callable trả null; nếu null là lỗi nghiệp vụ, chọn `switchIfEmpty(Mono.error(...))` để tạo 404/502 phù hợp.

### 5.3. Với thao tác blocking không trả kết quả

```java
Mono<Void> writeAudit(AuditLine input) {
    return Mono.fromRunnable(() -> blockingAuditSink.write(input))
            .subscribeOn(Schedulers.boundedElastic())
            .then();
}
```

Đây là fragment. `Mono.fromRunnable` phù hợp với tác vụ chỉ side effect không trả result. Đừng dùng `subscribe()` trong service để “fire-and-forget” mà không có owner/lifecycle/error tracking; HTTP request kết thúc có thể khiến lỗi audit bị mất. Nếu audit phải durable, cân nhắc outbox/message broker/job và transaction boundary.

### 5.4. Kiểm tra thread bằng log

```java
Mono.fromCallable(() -> {
    String name = Thread.currentThread().getName();
    return "read-on=" + name;
}).subscribeOn(Schedulers.boundedElastic());
```

Đây là fragment; muốn thấy kết quả phải subscribe qua test/HTTP pipeline. **Không assert chính xác tên thread ở production**: implementation có thể khác theo Reactor version, scheduler custom hoặc Java 21 virtual-thread mode.

### 5.5. Đừng gọi block() để “hoàn tất” pipeline

```java
// Sai trong handler WebFlux:
Customer c = find(id).block();
return ServerResponse.ok().bodyValue(c);
```

Thay bằng:

```java
return find(id)
        .flatMap(customer ->
                ServerResponse.ok().bodyValue(customer));
```

Functional WebFlux handler trả `Mono<ServerResponse>` và framework subscribe. `block()` trên event loop có thể ném lỗi hoặc cản trở các HTTP requests khác.

## 6. subscribeOn khác publishOn như thế nào?

### 6.1. subscribeOn: ảnh hưởng upstream subscription/source

```java
Mono<String> pipeline =
        Mono.fromCallable(() -> blockingGateway.read())
            .subscribeOn(Schedulers.boundedElastic())
            .map(String::trim);
```

`subscribeOn` tác động nơi source/subscription được thực thi. Với blocking source, vị trí gần nguồn giúp người đọc biết nguyên nhân scheduling.

### 6.2. publishOn: chuyển execution context downstream

```java
Flux<Result> pipeline =
        incoming
            .publishOn(Schedulers.parallel())
            .map(this::expensiveCpuTransform);
```

`publishOn` tạo ranh giới scheduler cho các operators phía sau cho tới ranh giới khác; nó có **prefetch/queue** riêng. Phù hợp tách CPU-heavy transform có giới hạn; không nên dùng `parallel` cho blocking JDBC hoặc `Thread.sleep`.

Một cách khác để cô lập blocking stage:

```java
return source
        .flatMap(item ->
            Mono.fromCallable(() -> blockingGateway.fetch(item))
                .subscribeOn(Schedulers.boundedElastic()),
            4);
```

Đây là fragment: tạo **một Mono lazy cho mỗi item**, chạy blocking call trên boundedElastic, tối đa bốn inner publishers active trong subscription đó.

### 6.3. Thứ tự/cấu trúc mới là điều quyết định

Chỉ thêm `publishOn(boundedElastic)` ở cuối chain **không quay ngược thời gian** để di chuyển blocking call đã chạy trước đó. Ví dụ sau vẫn có nguy cơ block event loop:

```java
return source
        .map(id -> blockingGateway.fetch(id))  // chạy trước
        .publishOn(Schedulers.boundedElastic());
```

Nếu bạn thực sự cần di chuyển toàn bộ đoạn `map` xuống pool, phải đặt `publishOn` **trước** `map` — nhưng dùng `flatMap(id -> Mono.fromCallable(...).subscribeOn(...), n)` thường làm ranh giới blocking và concurrency rõ hơn.

## 7. Tự tạo scheduler riêng cho hệ thống legacy

Dùng `newBoundedElastic` để phân vùng riêng:

```java
@Configuration
class LegacySchedulerConfig {
    @Bean(destroyMethod = "dispose")
    Scheduler legacyIoScheduler() {
        return Schedulers.newBoundedElastic(
                4,              // tối đa 4 backing threads
                32,             // queue cap mỗi backing thread
                "legacy-io");   // tên thread prefix
    }
}
```

Đây là **class fragment**, cần imports `@Configuration`, `@Bean`, `reactor.core.scheduler.Scheduler`/`Schedulers`. `dispose()` khi Spring context đóng giải phóng scheduler riêng. Với Spring Boot 3.5, bean type Scheduler có thể được injected theo qualifier.

**4 threads, 32 pending tasks/thread** là con số **để học**, không phải thông số production. Tham số thứ hai của `Schedulers.newBoundedElastic(threadCap, queuedTaskCap, name)` là **queue capacity PER BACKING THREAD**, không phải 32 task cho toàn pool; đừng trình bày nó như global request quota.

Lợi ích của scheduler riêng:

- Một dependency chậm không chiếm toàn bộ shared boundedElastic.
- Dễ thu thập metrics theo tên scheduler.
- Có thể áp timeout/rejection policy riêng.
- Thread cap phản ánh giới hạn sơ bộ của downstream.
- Có lifecycle rõ; không tạo một scheduler mới **trong mỗi HTTP request**.

Không nên:

```java
// Anti-pattern: mỗi request tạo pool mới, dễ leak resources:
Mono.fromCallable(this::blockingWork)
        .subscribeOn(
            Schedulers.newBoundedElastic(4, 32, "per-request"));
```

Scheduler tạo theo request mà không dispose gây resource leak và chi phí quản lý. Dùng singleton Spring bean hoặc shared scheduler khi phù hợp.

## 8. Giới hạn đồng thời bằng flatMap và flatMapSequential

Một `Flux<ID>` có thể nhận hàng trăm ID. Nếu viết:

```java
return ids.flatMap(id -> legacyService.find(id));
```

`flatMap` có default concurrency/prefetch nhất định, có thể tạo nhiều inner subscriptions và queue. Với blocking downstream, **đặt concurrency rõ**:

```java
Flux<Result> responses = ids
        .flatMap(
            id -> legacyService.find(id),
            4);
```

Trong **một subscription** của pipeline này, `flatMap(..., 4)` giới hạn số inner publishers được subscribe cùng lúc, giúp chỉ có tối đa bốn calls đang được điều phối đồng thời trong stream đó.

Lưu ý:

- `flatMap` có thể phát kết quả **khác thứ tự ID đầu vào** vì task hoàn thành khác nhau.
- `flatMapSequential(mapper, 4)` cho phép inner tasks concurrent nhưng **giữ thứ tự output**; để giữ thứ tự có thể phải buffer các kết quả hoàn thành sớm.
- `concatMap` thực hiện inner publishers **tuần tự** (thường concurrency hiệu dụng 1) và giữ thứ tự.
- `flatMap(mapper, concurrency, prefetch)` có tham số prefetch bổ sung, ảnh hưởng demand/queues; không phải hard byte memory cap.
- Nếu một item là object 10 MB, concurrency 4 vẫn có thể giữ hàng chục MB + codec overhead.
- Với nguồn hot, concurrency control không tự điều khiển broker hoặc sensor đang push bất chấp demand.

### 8.1. Khác biệt giữa per-stream và global concurrency

Giả sử mỗi HTTP request gọi:

```java
Flux.range(1, 100)
    .flatMap(this::blockingFetch, 4);
```

Nếu có **200 HTTP requests** đồng thời, tổng số inner subscriptions được phép tới mức lý thuyết `200 × 4 = 800` (trước khi scheduler/downstream giới hạn). Vì vậy **`flatMap(...,4)` không phải giới hạn toàn ứng dụng 4 calls**.

Ngoài per-stream cap, cần cân nhắc:

1. Global concurrency/bulkhead theo dependency.
2. Scheduler thread cap và queue cap.
3. HTTP client connection pool / JDBC pool.
4. Gateway limits, tenant quotas, fairness.
5. Timeouts và circuit breaker.
6. Memory dùng cho buffered outputs và in-flight items.

Nếu deploy ba instances, mỗi instance cap 4 vẫn có thể có 12 calls toàn cụm. Global/cross-node quota cần coordinator/broker/gateway hoặc service-side policy, không chỉ `Semaphore` trong JVM.

## 9. Non-blocking bulkhead với Semaphore.tryAcquire

Một `Semaphore` có thể giới hạn số blocking operations **đang thực thi**, nhưng phải dùng cẩn thận để không chặn event loop.

**Sai:**

```java
// Anti-pattern nếu chạy trong reactive event-loop thread:
semaphore.acquire(); // BLOCK đến khi có permit!
try {
    blockingGateway.fetch();
} finally {
    semaphore.release();
}
```

**Tốt hơn:** nếu cần cap, dùng `tryAcquire()` không blocking bên trong callable đã được schedule:

```java
Mono<Result> fetchBounded(String id) {
    return Mono.fromCallable(() -> {
        if (!slots.tryAcquire()) {
            throw new LegacyBusyException();
        }
        try {
            return blockingGateway.fetch(id);
        } finally {
            slots.release();
        }
    }).subscribeOn(legacyScheduler);
}
```

Đây là **fragment**, giả định singleton `Semaphore slots`, dedicated `Scheduler legacyScheduler` và exceptions/types có thật. Khi hết permits, task chạy trên worker sẽ fail-fast thay vì chờ permit. Nhưng task vẫn có thể **chờ trong scheduler queue trước khi chạy đến tryAcquire**. Vì vậy:

- Scheduler queue vẫn cần giới hạn và theo dõi.
- Nếu yêu cầu reject *ngay khi vào HTTP endpoint*, cần admission control ở request boundary/gateway hoặc cơ chế acquire non-blocking **trước** scheduling với cancellation-safe ownership.
- Không dùng `tryAcquire(timeout, unit)` trên event loop; dù có timeout nó vẫn **blocking**.
- Permit phải release trong `finally` đúng lúc blocking call thực sự kết thúc, không chỉ khi downstream cancel.
- Nếu release permit trong `doFinally` mà blocking thread vẫn đang chạy sau cancel/timeout, bạn có thể đếm thiếu active calls và cho phép quá nhiều work thực. Hãy gắn permit lifetime vào **actual blocking task** hoặc dùng bulkhead library có contract cancellation rõ.
- Nếu task bị queue rồi bị cancel **trước khi bắt đầu**, phương án acquire bên trong callable không leak permit.

Với tính năng phức tạp (fairness, metrics, global distribution, slow queue, circuit breaker), dùng thư viện bulkhead/rate-limiter được duy trì hoặc ingress admission controls; `Semaphore` demo chỉ minh họa nguyên tắc.

## 10. RejectedExecutionException, overload và HTTP status

Khi `newBoundedElastic` hết backing threads và hàng đợi cho task, scheduling có thể ném `RejectedExecutionException`. Nếu dependency đang quá bận và custom bulkhead `tryAcquire` trả false, chúng ta có thể trả **503 Service Unavailable** với `Retry-After` có kiểm soát.

```java
return legacyService.find(id)
        .flatMap(result ->
            ServerResponse.ok().bodyValue(result))
        .onErrorResume(
            RejectedExecutionException.class,
            ex -> ServerResponse.status(503)
                    .header("Retry-After", "1")
                    .bodyValue(Map.of(
                        "code", "LEGACY_CAPACITY_EXCEEDED")));
```

Đây là **fragment**. Không coi `503 Retry-After: 1` là một công thức chung. Chọn retry hint dựa trên service semantics, SLO, load và jitter/backoff. Trên request đã trả một phần stream, có thể **không đổi** status 200 sang 503; xem phần streaming phía dưới.

### 10.1. Phân biệt 429 và 503

- **429 Too Many Requests:** client/tenant đã vượt request rate/quota theo chính sách.
- **503 Service Unavailable:** phụ thuộc/instance tạm thời không có capacity.
- **504 Gateway Timeout:** gateway không nhận response từ upstream đúng hạn trong mô hình tương ứng.
- **500 Internal Server Error:** lỗi nội bộ không phân loại hoặc bug.
- **408 Request Timeout:** client không gửi request đủ đúng hạn theo HTTP semantics.

Không map toàn bộ `TimeoutException` thành 429, hay toàn bộ `RejectedExecutionException` thành lỗi 400. Cần nhất quán error code, metrics và policy retries.

## 11. Timeout và cancellation không phải phép màu

Một cách giới hạn thời gian:

```java
return legacyService.find(id)
        .timeout(Duration.ofMillis(500));
```

`timeout` phát `TimeoutException` cho downstream nếu quá hạn và thường cancel subscription upstream. Tuy nhiên:

- Thread đang chạy trong blocking JDBC/SDK có thể **không dừng ngay** hoặc không hỗ trợ interrupt.
- Task trong queue có thể chưa bắt đầu; cancel có thể ngăn nó chạy trong những trường hợp scheduler hỗ trợ.
- `Thread.interrupt` không đồng nghĩa network socket/DB operation chắc hủy được.
- Side effect đã commit (ví dụ thanh toán hoặc gửi email) không được rollback chỉ vì downstream timeout.
- Nếu client retry sau timeout mà server vẫn xử lý, request có thể chạy **hai lần**.
- `doOnCancel` và `doFinally(CANCEL)` thông báo vòng đời publisher, không bảo đảm hoàn tất cleanup của external work.

### 11.1. Deadline và timeout nhiều tầng

Một lời gọi legacy nên có:

1. HTTP client/request deadline.
2. Connection acquisition timeout.
3. Socket/connect/read timeout của **SDK hoặc database driver** thực tế.
4. Bulkhead/queue-wait deadline.
5. Reactor `timeout` cho end-to-end promise.
6. Gateway/ingress timeout và retry policy.
7. Idempotency key/transaction semantics cho operations có side effects.

Nếu Reactor timeout 200 ms nhưng JDBC driver chỉ timeout 30 s, client có thể được trả lỗi sớm trong khi thread/connection DB vẫn bị giữ lâu. Tốt hơn là thiết kế **timeout tại nguồn blocking** và hạn chế inflight operations.

### 11.2. Không retry vô hạn khi đang overload

```java
// Không nên:
return legacyService.find(id).retry();
```

Với dependency đang cạn capacity, retry vô hạn tạo **retry storm**, giữ scheduler queue và connection pool đầy. Dùng retry có hạn mức, exponential backoff/jitter, chỉ với lỗi retryable, và chỉ khi operation idempotent hoặc có dedupe key. Request đã streaming một phần thường không retry lại mà không có framing/checkpoint.

## 12. Streaming WebFlux: producer chậm, consumer chậm và buffering

### 12.1. Dùng NDJSON

HTTP response kiểu NDJSON:

```http
HTTP/1.1 200 OK
Content-Type: application/x-ndjson

{"id":"A","value":"value-A"}
{"id":"B","value":"value-B"}
{"id":"C","value":"value-C"}
```

Một `Flux<Record>` trả về `ServerResponse.body(flux, Record.class)` có thể được encode theo từng record nếu content type là `application/x-ndjson`. Nhưng:

- `flatMap` mặc định có thể reorder; `flatMapSequential` hoặc `concatMap` nếu phải giữ input ordering.
- Codec có thể có buffer per record, network có outbound queues.
- Proxy/CDN có thể gom nhiều events và chỉ flush cuối.
- `subscribeOn(boundedElastic)` cô lập nguồn blocking, không trực tiếp điều khiển TCP flush.
- Nếu client ngắt, downstream có thể cancel stream nhưng task blocking đã bắt đầu có thể còn chạy.
- Nếu lỗi tại record 20 và HTTP 200 đã committed, response có thể chỉ chứa 19 records mà không có JSON error hoàn chỉnh.

**Dữ liệu mỗi record vẫn cần validation và total stream quotas.** Trong demo, nhập vào là danh sách IDs tối đa tám để giới hạn số task; bản production cần validate IDs/authorization/tenant boundary.

### 12.2. Với SSE

Một live dashboard có thể gửi:

```text
id: 7
event: progress
data: {"percent":70}

id: 8
event: complete
data: {"percent":100}

```

SSE `ServerSentEvent<T>` được WebFlux encode thành `text/event-stream`. Nếu dữ liệu được tạo bởi blocking SDK, wrap từng call bằng bounded scheduler hoặc tốt hơn dùng asynchronous source. **Không tạo một blocking thread cho mỗi SSE subscriber rồi `while(true) Thread.sleep(...)`**: nó giữ worker rất lâu, cạn pool.

Cân nhắc:

- Dùng scheduler timer/reactive `Flux.interval` cho heartbeat thay vì sleeping workers.
- Shared pub/sub nguồn dữ liệu và per-client bounded queue/drop/latest strategy (nếu cho phép mất update).
- Khi cần mọi event đầy đủ, dùng durable broker + IDs + replay.
- Connection limits/heartbeat/reconnect/backoff.
- Không coi Reactor demand là một HTTP wire command `request(n)` từ browser EventSource.

### 12.3. Slow consumers và response encoding

Khi client nhận chậm, Reactor Netty có thể điều tiết demand dựa trên write readiness, nhưng operator queues, JSON encoding và proxy buffers vẫn tồn tại. Nếu mỗi response item chứa vài MB dữ liệu, concurrency 2 cũng có thể dùng rất nhiều heap/direct memory.

Không dùng `onBackpressureBuffer()` unbounded để “chữa” slow network; nó có thể gây OOM. Nếu dữ liệu cho phép mất mẫu, chọn `onBackpressureLatest`/`onBackpressureDrop` với policy; nếu dữ liệu quan trọng, backpressure tới nguồn hoặc dùng broker/replay.

## 13. Ứng dụng runnable: Spring Boot 3.5 + WebFlux.fn + bounded legacy calls

Demo gồm hai endpoints:

- `GET /api/legacy/{id}`: gọi một service **blocking mô phỏng**, chạy trên dedicated boundedElastic, trả JSON khi thành công.
- `GET /api/legacy/stream?ids=A,B,C`: trả **NDJSON stream** giữ thứ tự đầu vào nhờ `flatMapSequential(..., 2)`; tối đa tám IDs mỗi request.
- Mỗi blocking call sử dụng `Semaphore.tryAcquire` trong **worker** để giới hạn **ba calls đang thực thi trong một JVM**.
- Scheduler riêng có tối đa bốn backing platform threads và queue cap 32 trên mỗi backing thread. Khi không còn capacity, custom exception hoặc scheduler rejection được map thành 503 cho endpoint single; với stream đã bắt đầu, phải xem giới hạn HTTP committed response.
- Tests dùng `WebTestClient` thật qua HTTP và `StepVerifier`/metrics để xác nhận output và tính chất concurrency.

**Phạm vi:** demo chỉ có read-only simulated service. `Thread.sleep(70)` minh họa blocking SDK; đây **không** phải hành vi nên để trong production. Chưa có auth/tenant quota, DB/HTTP client thật, distributed concurrency limiter hoặc guaranteed flow control qua proxy.

### 13.1. Tạo project Gradle

Chọn Java 17, Spring Boot 3.5.x, Gradle và **Spring Reactive Web** trong Initializr. Trong project có Boot plugin, dependency management, Maven Central và toolchain, dependencies:

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-webflux'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'io.projectreactor:reactor-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

Đây là **fragment của build.gradle**, không phải file Gradle độc lập. Không thêm `spring-boot-starter-web` vào demo nếu muốn WebFlux/Netty: Boot 3.x thường chọn Servlet MVC khi cả MVC và WebFlux starters có mặt.

### 13.2. Java source: một file hoàn chỉnh

Tạo **`src/main/java/demo/isolation/IsolationApplication.java`**:

```java
package demo.isolation;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@SpringBootApplication
public class IsolationApplication {
    public static void main(String[] args) {
        SpringApplication.run(IsolationApplication.class, args);
    }
}

record LegacyRecord(String id, String value) {}

record ApiError(String code) {}

class LegacyBusyException extends RuntimeException {}

@Configuration
class IsolationConfig {

    @Bean(destroyMethod = "dispose")
    Scheduler legacyIoScheduler() {
        // Demo: 4 threads, 32 queued tasks per backing thread.
        return Schedulers.newBoundedElastic(
                4, 32, "legacy-io");
    }

    @Bean
    RouterFunction<ServerResponse> legacyRoutes(
            LegacyHandler handler) {
        return RouterFunctions.route()
                // Static route trước variable route.
                .GET("/api/legacy/stream", handler::stream)
                .GET("/api/legacy/{id}", handler::single)
                .build();
    }
}

@Component
class LegacyBlockingGateway {
    private final Semaphore slots = new Semaphore(3);
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger peak = new AtomicInteger();

    LegacyRecord fetch(String id) throws InterruptedException {
        // Chỉ chạy bên trong scheduled callable.
        if (!slots.tryAcquire()) {
            throw new LegacyBusyException();
        }

        int inFlight = active.incrementAndGet();
        peak.accumulateAndGet(inFlight, Math::max);

        try {
            // Mô phỏng SDK đồng bộ/legacy API.
            Thread.sleep(70L);
            return new LegacyRecord(id, "value-" + id);
        } finally {
            active.decrementAndGet();
            slots.release();
        }
    }

    int peakActive() {
        return peak.get();
    }

    int activeNow() {
        return active.get();
    }
}

@Service
class LegacyService {
    private final LegacyBlockingGateway gateway;
    private final Scheduler legacyScheduler;

    LegacyService(
            LegacyBlockingGateway gateway,
            @Qualifier("legacyIoScheduler")
            Scheduler legacyScheduler) {
        this.gateway = gateway;
        this.legacyScheduler = legacyScheduler;
    }

    Mono<LegacyRecord> fetch(String id) {
        return Mono.fromCallable(() -> gateway.fetch(id))
                .subscribeOn(legacyScheduler);
    }
}

@Component
class LegacyHandler {
    private final LegacyService service;

    LegacyHandler(LegacyService service) {
        this.service = service;
    }

    private Mono<ServerResponse> invalidIds() {
        return ServerResponse.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ApiError("INVALID_IDS"));
    }

    private Mono<ServerResponse> overloaded() {
        return ServerResponse
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ApiError("LEGACY_OVERLOADED"));
    }

    Mono<ServerResponse> single(ServerRequest request) {
        String id = request.pathVariable("id");
        if (!id.matches("[A-Za-z0-9-]{1,16}")) {
            return invalidIds();
        }

        return service.fetch(id)
                .flatMap(record ->
                    ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(record))
                .onErrorResume(
                    LegacyBusyException.class,
                    error -> overloaded())
                .onErrorResume(
                    RejectedExecutionException.class,
                    error -> overloaded());
    }

    Mono<ServerResponse> stream(ServerRequest request) {
        String raw = request.queryParam("ids")
                .orElse("");

        List<String> ids = Arrays.stream(
                raw.split(",", -1))
                .map(String::trim)
                .toList();

        if (ids.isEmpty() || ids.size() > 8
                || ids.stream().anyMatch(id ->
                    !id.matches("[A-Za-z0-9-]{1,16}"))) {
            return invalidIds();
        }

        Flux<LegacyRecord> records =
                Flux.fromIterable(ids)
                    // Concurrency per HTTP response = 2.
                    // Giữ thứ tự dù blocking call hoàn tất khác nhau.
                    .flatMapSequential(service::fetch, 2);

        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_NDJSON)
                .body(records, LegacyRecord.class);
    }
}
```

**Cấu trúc Java:** `IsolationApplication` là public class duy nhất trong một file Java 17; các records/classes package-private cùng package nên Spring component scan phát hiện `IsolationConfig`, `LegacyBlockingGateway`, `LegacyService` và `LegacyHandler`.

**Chú thích an toàn/semantics:**

- `LegacyService.fetch` tạo Mono **lazy**, callable chạy trên custom scheduler khi subscribe; Netty event loop không chạy `Thread.sleep(70L)`.
- `LegacyBlockingGateway.fetch` dùng `Semaphore.tryAcquire` bên trong scheduled work nên **không block khi chờ permit**. `finally` trả permit ngay khi actual fetch kết thúc, kể cả exception/interrupt. Nếu task bị cancel trước khi bắt đầu, chưa acquire permit.
- `peakActive`/`activeNow` là local JVM metrics **cho test/giải thích**, không phải toàn cluster. Không trả thông tin này ra public API production nếu không cần.
- `flatMapSequential(...,2)` chỉ giới hạn concurrency **mỗi HTTP stream**; các request khác dùng cùng scheduler/bulkhead.
- `ids.size() ≤ 8` là quota per-request cho demo. ID regex giới hạn cú pháp/độ dài, nhưng không phải authorization hoặc defense chống toàn bộ request floods.
- `ServerResponse.body(flux, LegacyRecord.class)` là body writer của WebFlux.fn; `application/x-ndjson` giúp writer phát mỗi record theo dòng.
- `onErrorResume(Rejection...)` xử lý **single** trước khi `ServerResponse` được tạo. Đối với `stream`, response có thể commit trong lúc ghi những records đầu; không bảo đảm có thể trả 503 JSON khi task thứ N bị reject.
- ``32 queued tasks per backing thread`` không phải cap 32 cho toàn endpoint; nếu 200 requests đồng thời, một số có thể bị queue trước khi gateway `tryAcquire`.
- Nếu một external SDK bỏ qua interruption, Reactor cancellation không bảo đảm release permit sớm: permit thuộc vòng đời thực của `fetch`.
- Nếu muốn một bulkhead **reject ngay tại lúc HTTP admission** chứ không phải lúc worker bắt đầu, cần thiết kế cancellation-safe acquisition ở request boundary và bounded queues độc lập.

### 13.3. Chạy và thử HTTP

Khởi chạy:

```bash
./gradlew bootRun
```

**Gọi một ID:**

```bash
curl -i \
  'http://localhost:8080/api/legacy/A'
```

Khi không bị overload, body dự kiến:

```json
{"id":"A","value":"value-A"}
```

**NDJSON stream nhiều ID:**

```bash
curl -N -i \
  -H 'Accept: application/x-ndjson' \
  'http://localhost:8080/api/legacy/stream?ids=A,B,C'
```

Body dự kiến, theo thứ tự input nhờ `flatMapSequential`:

```text
{"id":"A","value":"value-A"}
{"id":"B","value":"value-B"}
{"id":"C","value":"value-C"}
```

**ID không hợp lệ hoặc quá nhiều IDs:**

```bash
curl -i \
  'http://localhost:8080/api/legacy/stream?ids=A,B,not%20allowed'
```

Response dự kiến 400:

```json
{"code":"INVALID_IDS"}
```

**Để thử overload thật**, cần load test tạo nhiều simultaneous HTTP clients và đo `activeNow`/`peakActive`, scheduler queue và rejection counts. Một lệnh curl đơn lẻ không chứng minh bulkhead đã hoạt động trong trạng thái quá tải.

Các output minh họa ở đây là **dự kiến từ thiết kế**, **không phải kết quả chạy Gradle hoặc load test thực tế**.

## 14. Kiểm thử WebTestClient và StepVerifier

### 14.1. Vì sao cần hai loại test?

- **`WebTestClient` qua RANDOM_PORT:** kiểm tra HTTP codec, route, status, NDJSON framing và response contents trên embedded Reactor Netty.
- **`StepVerifier` với `Mono/Flux`:** kiểm tra publisher signals, error, cancel, terminal state mà không cần HTTP.
- **Load test slow consumers:** đo actual inflight work, queue depth, latency, CPU, direct memory và behavior khi cùng lúc có nhiều clients.
- **Thread assertion:** chỉ là hỗ trợ, không nên hardcode tên exact thread; Java21 virtual threads/scheduler implementations có thể đổi naming.

### 14.2. Bài kiểm thử cho Java 17 demo

Tạo **`src/test/java/demo/isolation/IsolationApplicationTests.java`**:

```java
package demo.isolation;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IsolationApplicationTests {

    @Autowired
    WebTestClient client;

    @Autowired
    LegacyService service;

    @Autowired
    LegacyBlockingGateway gateway;

    @Test
    void singleReadRespondsWithJson() {
        client.get()
                .uri("/api/legacy/A")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("A")
                .jsonPath("$.value").isEqualTo("value-A");
    }

    @Test
    void invalidIdIsRejectedBeforeSchedulingWork() {
        client.get()
                .uri("/api/legacy/invalid_id")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                    .isEqualTo("INVALID_IDS");
    }

    @Test
    void streamingEndpointPreservesRecordOrder() {
        Flux<LegacyRecord> body = client.get()
                .uri("/api/legacy/stream?ids=A,B,C,D")
                .accept(MediaType.APPLICATION_NDJSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(
                        MediaType.APPLICATION_NDJSON)
                .returnResult(LegacyRecord.class)
                .getResponseBody();

        StepVerifier.create(body)
                .expectNext(new LegacyRecord("A", "value-A"))
                .expectNext(new LegacyRecord("B", "value-B"))
                .expectNext(new LegacyRecord("C", "value-C"))
                .expectNext(new LegacyRecord("D", "value-D"))
                .verifyComplete();
    }

    @Test
    void streamRejectsTooManyIds() {
        client.get()
                .uri("/api/legacy/stream?ids=A,B,C,D,E,F,G,H,I")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                    .isEqualTo("INVALID_IDS");
    }

    @Test
    void serviceIsLazyUntilSubscriberConsumes() {
        // Constructor creates a Mono; it does not execute Gateway.
        var publisher = service.fetch("LAZY");
        StepVerifier.create(publisher)
                .expectNext(new LegacyRecord(
                        "LAZY", "value-LAZY"))
                .verifyComplete();
    }

    @Test
    void perStreamConcurrencyIsAtMostTwo() {
        StepVerifier.create(
                Flux.fromIterable(List.of(
                    "1", "2", "3", "4", "5", "6"))
                    .flatMapSequential(service::fetch, 2))
                .expectNextCount(6)
                .verifyComplete();

        // Without concurrent unrelated tests, peak is bounded by 2.
        assertTrue(gateway.peakActive() <= 2);
        assertTrue(gateway.activeNow() == 0);
    }
}
```

Lệnh chạy:

```bash
./gradlew test
```

**Sáu tests được thiết kế để pass** khi chạy đúng baseline và mặc định JUnit chạy các test methods tuần tự. Đây **chỉ là expected behavior từ review API/mã**, **không có bằng chứng Gradle đã thực thi**. Test `peakActive() <=2` phụ thuộc trạng thái bean dùng chung và không cho phép test khác đồng thời chạy background blocking calls; nếu CI chạy tests parallel hoặc cùng app nhận traffic ngoài, hãy cô lập test context/gateway hoặc dùng test instrumentation riêng.

**Điểm yếu cần tránh:** `serviceIsLazyUntilSubscriberConsumes` ở trên **chỉ xác minh output sau subscribe**, chưa trực tiếp chứng minh “không có side effect trước subscribe” vì nó không assert counter trước đó. Một test nghiêm ngặt hơn nên inject fake gateway hoặc spy, gọi `service.fetch(id)`, assert invocation count = 0, rồi subscribe và assert count = 1. Mục đích đoạn hiện tại là test successful publisher integration.

### 14.3. Test deterministic về concurrency bằng StepVerifier

Nếu cần chứng minh concurrency cap mà không phụ thuộc thời gian Thread.sleep/CPU load, có thể tạo một source test với `AtomicInteger` và async fake tasks dùng `Mono.defer`/`Sinks.One`, chủ động điều khiển khi từng inner hoàn tất. Sau đó verify max active subscriptions theo `flatMap(...,2)`. Tránh assert wall-clock kiểu “phải nhanh hơn 140 ms” vì CI tải không ổn định.

Một test đơn giản về error propagation:

```java
StepVerifier.create(
        Mono.<String>error(
                new LegacyBusyException()))
        .expectError(LegacyBusyException.class)
        .verify();
```

Đây là **fragment** cho unit test; chỉ chứng minh Reactor truyền typed error. Test HTTP 503 cần tạo trạng thái bulkhead đầy có kiểm soát rồi assert error mapping tại handler hoặc WebTestClient.

## 15. Vì sao virtual threads không thay thế concurrency limits?

Từ Reactor 3.6, `boundedElastic` có thể hỗ trợ execution trên virtual threads khi chạy **Java 21+** và bật system property:

```properties
reactor.schedulers.defaultBoundedElasticOnVirtualThreads=true
```

Đây là **Reactor system property**, cần đặt đủ sớm (trước khi scheduler shared init), không tự suy ra nó là Spring Boot `application.properties` binding mặc định. Trong process Java, thường truyền dưới dạng:

```bash
java -Dreactor.schedulers.defaultBoundedElasticOnVirtualThreads=true \
  -jar app.jar
```

Với Java 17 baseline demo, **tùy chọn này không bật virtual threads**. Trong Java 21+, shared `Schedulers.boundedElastic()` có thể dùng virtual-thread-per-task implementation theo cấu hình Reactor; custom `Schedulers.newBoundedElastic(4,32,...)` có overload/semantics khác, đừng mặc định nghĩ mọi custom pool tự chuyển sang virtual threads.

Virtual threads giúp giảm chi phí thread bị park ở nhiều blocking calls, nhưng:

- Số connections DB vẫn bị giới hạn.
- Remote service vẫn chỉ chịu được số QPS/concurrency nhất định.
- CPU, heap, network và thread pinning vẫn có thể là bottleneck.
- Queued tasks vẫn cần capacity/timeout.
- Gọi blocking native library có thể có semantics khác pinning/interruption.
- Không bỏ bulkhead, admission controls và observability.

**Một triệu virtual threads không biến database 10 connections thành database có một triệu connections**. Chọn virtual threads dựa trên profiling và phiên bản JDK/Reactor; ưu tiên những giải pháp giảm blocking gốc khi có thể.

## 16. Scheduler lifecycle, metrics và quyết định sizing

### 16.1. Những thông số cần đo

| Metric | Ý nghĩa |
| --- | --- |
| Active running legacy tasks | Inflight blocking operations thật |
| Peak inflight / permitted slots | Bulkhead có sát capacity không |
| Scheduler backing thread count | Có thường xuyên chạm thread cap không |
| Scheduler pending queue depth | Có backlog do blocking calls quá lâu không |
| Oldest queued wait time | Client đang chờ quá lâu dù CPU rảnh? |
| Scheduled task rejections | Quá tải scheduler hoặc queue cap |
| HTTP 503 / 429 / timeout rates | Dấu hiệu policy bảo vệ đang hoạt động |
| Dependency connection pool active/pending | Nút thắt DB/HTTP client thực |
| Latency P50/P95/P99 per route | Kinh nghiệm người dùng và SLO |
| SSE time-to-first-event/event lag | Streaming client nhận chậm vì producer hay proxy |
| JVM heap + Netty direct memory | Backpressure/buffer pressure |
| Cancellation vs task completion | Task có tiếp tục chạy sau HTTP timeout không |
| Retry count and downstream QPS | Phát hiện retry storm |

Reactor có khả năng dùng Micrometer scheduler metrics khi tích hợp `reactor-core-micrometer` và đăng ký scheduler với `Micrometer.timedScheduler(...)` theo API phiên bản tương ứng. Nếu project không dùng Micrometer module, hãy đo qua runtime/JVM metrics và metrics từ service. Không bật logging mọi item/payload khi traffic cao hoặc có dữ liệu nhạy cảm.

### 16.2. Quy tắc chọn pool size (không có một con số chung)

Khi downstream legacy chỉ chịu tối đa `C` concurrent requests, scheduler thread cap và bulkhead per-instance nên được chọn với:

- Tổng traffic từ tất cả instances/clients.
- Số DB/HTTP connections thực.
- Request latency và timeout của dependency.
- Memory mỗi task/response item.
- Workload khác chia sẻ scheduler.
- Mức queuing được chấp nhận và HTTP deadline.
- Fairness giữa tenant/request class.
- Cold start/peak bursts, autoscaling và headroom.

Ví dụ hướng nghĩ:

```text
DB pool = 10 connections
Web app = 2 instances
Cần dành 4 connections cho jobs/admin/migrations

Không thể chọn mỗi app 10 blocking calls
rồi giả định tổng 20 lúc cao điểm đều an toàn.
Phải phân bổ quota theo service budget + các consumers.
```

Con số trên chỉ minh họa nguyên tắc capacity planning, không phải công thức tối ưu chung.

### 16.3. Backpressure không tự giải quyết scheduler queue

Reactive Streams `request(n)` giúp điều tiết số phần tử trong pipeline nhưng một callable blocking đã được schedule có thể giữ thread rất lâu. Nếu downstream request 1000 items từ `flatMap` với concurrency cao, rất nhiều tasks có thể vào scheduler queue. Kết hợp:

- `flatMap` explicit concurrency.
- Bounded scheduling queue.
- Bulkhead/admission control.
- Demand-aware source/records limits.
- Response writer backpressure, không tạo unbounded buffers.
- Retry/timeout policy với worker cleanup.

## 17. Bảo mật, resource ownership và yêu cầu production

1. **Không chạy blocking crypto/file scan/server-side request (SSRF) trên Netty event loop**. Nếu SDK blocking, isolate nhưng vẫn phải kiểm soát destination URLs/allowlists.
2. **Không cho user tự quyết định path/file/DB key** khi export qua blocking file API; validate/authorize trước operation.
3. Đặt **max request body, upload bytes, response record size, number of IDs, concurrency** theo tenant và endpoint.
4. Không trả raw `RejectedExecutionException`, JDBC error hoặc stack trace vào API response.
5. Không log password, Authorization, Cookie, signed URL hoặc PII trong scheduler task MDC.
6. ThreadLocal/MDC **không tự truyền xuyên scheduling boundary**. Dùng Reactor Context và context-propagation/tracing integration đúng phiên bản, explicit immutable context cho legacy library khi cần.
7. Cancellation phải giải phóng DB connections, streams và buffers đúng lifecycle, nhưng không giả định một interrupt luôn có tác dụng.
8. Nếu multiple tenants dùng shared pool, một tenant có thể chiếm queue; cần per-tenant fairness/admission.
9. Bulkhead rejection không thay rate limiting/authorization; HTTP client có thể spam nhỏ khiến queue đầy.
10. Với side effects (payment, email, job), phải có idempotency/reconciliation dù đã dùng bounded scheduler.
11. Circuit breaker đặt dựa trên failure/latency của dependency, không đóng/mở chỉ vì một lỗi input 400.
12. Sử dụng health checks và alerts có tín hiệu để không che overload bằng auto-retry vô hạn.

## 18. Troubleshooting: hiện tượng và nguyên nhân

| Triệu chứng | Nguyên nhân khả dĩ | Hướng xử lý |
| --- | --- | --- |
| WebFlux throughput thấp dù CPU nhàn | JDBC/SDK/Thread.sleep trên event loop | Profile thread dumps, cô lập blocking source |
| `Mono.just(blockingCall())` vẫn block | Tính toán diễn ra trước Mono | `Mono.fromCallable(...).subscribeOn(...)` |
| `subscribeOn` đặt sau `Mono.just` không giúp | Blocking call đã chạy | Đưa blocking work vào callable |
| `publishOn` cuối chain không giúp | Blocking `map` chạy trước boundary | Chuyển boundary trước map hoặc wrap inner Mono |
| `block()` ném IllegalStateException | Reactor non-blocking thread | Không block, dùng reactive composition |
| CPU-bound code trên boundedElastic gây queue | Dùng sai pool cho compute | Đưa compute ngắn sang `parallel`/dedicated compute |
| Pool có nhiều pending tasks | Downstream chậm, cap/queue quá rộng | Bulkhead, timeout, fail-fast, metrics |
| Global pool cạn khi export chạy | Workloads dùng chung boundedElastic | Dedicated scheduler / job queue |
| `flatMap(...,4)` nhưng có 100 calls | Nhiều HTTP requests độc lập | Global semaphore/admission + pool limits |
| Stream NDJSON kết quả không đúng thứ tự | Dùng flatMap merge kết quả | flatMapSequential/concatMap |
| NDJSON chỉ hiển thị sau nhiều giây | Proxy/codec/client buffering | `curl -N`, inspect proxy, actual first-event timings |
| Timeout HTTP nhưng DB query vẫn chạy | Driver không cancel/interrupt | Configure driver timeouts và DB statement cancellation |
| `doFinally` release permit nhưng overload vượt cap | Task vẫn chạy sau client cancel | Release permit trong actual blocking task |
| 503 tăng khi nhiều clients | Scheduler/bulkhead reject | Kiểm tra queue/dependency latency/QPS |
| Retry storm | Clients retry ngay vô hạn | Backoff, jitter, Retry-After, idempotency |
| CPU/heap tăng do `collectList()` | Tích lũy full stream | Stream response / bounded batch |
| Client đóng tab, task vẫn hoạt động | Cancellation không ngay hoặc SDK bỏ qua interrupt | Track task lifecycle, SDK cancel API |
| Streaming lỗi giữa chừng vẫn status 200 | Response committed | Protocol error, partial-stream contract |
| Số queued tasks lớn nhưng memory không đoán được | Item/task metadata và buffers khác nhau | Theo dõi queue bytes/memory và heap profile |
| Khi bật virtual threads vẫn nghẽn DB | DB/HTTP connection cap không đổi | Bulkhead/pool sizing theo dependency |

### Quy trình chẩn đoán

1. Dùng metrics/thread dump xác định **thread nào block và ở method nào**.
2. Xác nhận runtime đang dùng WebFlux/Netty, không vô tình MVC.
3. Tìm `block()`, `Thread.sleep`, JDBC, synchronous HTTP SDK trong handlers/operators.
4. Kiểm tra blocking code có nằm **bên trong lazy `fromCallable`** hay không.
5. Xem `subscribeOn`/`publishOn` xuất hiện trước/sau stage nào.
6. Kiểm tra `flatMap` concurrency mỗi stream và số simultaneous HTTP requests.
7. Kiểm tra scheduler cap/queue, DB/HTTP client pool, bulkhead permits.
8. Kiểm tra timeout thực tế ở client/driver, cancellation propagation.
9. Nếu streaming chậm, phân biệt producer, codec, proxy và browser.
10. Load test nhiều concurrent clients rồi kiểm tra heap/direct memory, QPS, p99, rejection và resource cleanup.

## 19. So sánh những lựa chọn kiến trúc

| Nhu cầu | Lựa chọn nên cân nhắc | Điều cần bảo vệ |
| --- | --- | --- |
| Một legacy SDK blocking ngắn | `Mono.fromCallable` + boundedElastic | Timeout, pool cap |
| Legacy SDK shared bởi nhiều routes | Dedicated `newBoundedElastic` bean | Queue/rejection, cross-route fairness |
| Batch IDs từ request | `flatMap(mapper,n)` | Per-stream cap, total count/bytes |
| Kết quả phải giữ thứ tự | `flatMapSequential` hoặc `concatMap` | Out-of-order buffering |
| Global concurrency tại JVM | Non-blocking bulkhead/semaphore | Permit lifetime, cancel |
| Across multiple nodes | Gateway/distributed quotas, downstream pool policies | Không dựa local semaphore |
| Blocking DB nhưng cần reactive API | Temporary boundedElastic isolation | Migration sang R2DBC nếu phù hợp |
| Số lượng tác vụ kéo dài hàng phút | Background job/queue + 202/GET status | Durable state, retry, DLQ |
| Streaming live updates không mất events | Broker + SSE/WebSocket | Replay, ordered IDs, slow consumers |
| Streaming chỉ cần trạng thái mới nhất | BackpressureLatest/coalescing | Cho phép mất intermediate updates |
| Native async client tồn tại | WebClient / reactive DB driver | Connection pooling, cancellation |

Một API không cần realtime và mất vài phút để tạo báo cáo có thể tốt hơn với `POST /exports` trả `202 Accepted` + job ID, sau đó download file đã hoàn tất; giữ hàng nghìn HTTP streaming requests trên blocking workers không phải lựa chọn tối ưu.

## 20. Các khác biệt phiên bản quan trọng

| Version | Ghi chú |
| --- | --- |
| Reactor 3.3+ | BoundedElastic được giới thiệu để thay cách dùng elastic không có thread cap |
| Reactor 3.6/3.7 | `boundedElastic` có thêm lựa chọn virtual-thread implementation trên Java 21+ khi bật system property |
| Spring Framework 6.2 / Boot 3.5 | Java17 baseline; WebFlux.fn trả `Mono<ServerResponse>` và hỗ trợ NDJSON/SSE |
| Java 17 | Không có Java virtual threads production như Java 21 |
| Java 21+ | Có thể dùng Reactor boundedElastic virtual threads theo điều kiện/configuration |
| Spring Framework 7 / Boot 4 | Kiểm tra dependency/API/test module/config migration; concepts vẫn hữu ích |

Không giả định toàn bộ Boot `spring.threads.virtual.enabled=true` đồng nghĩa shared Reactor `boundedElastic()` luôn chạy virtual threads trong mọi version. Đọc chính xác Reactor Javadoc/system property và test thread/capacity thực tế.

## 21. Checklist production

- [ ] Không có blocking JDBC/HTTP/file operation trực tiếp trên Netty event loop.
- [ ] Blocking source nằm trong `Mono.fromCallable`/`fromRunnable` lazy và có `subscribeOn` phù hợp.
- [ ] Không gọi `block()`/`blockLast()` trong WebFlux handler.
- [ ] Không tạo scheduler mới mỗi request; có bean lifecycle và dispose.
- [ ] Dedicated scheduler theo downstream khi cần workload isolation.
- [ ] Pool thread cap/queue cap đo theo service capacity, không lấy defaults làm SLO.
- [ ] `flatMap` concurrency được khai báo rõ cho các batch/streams.
- [ ] Global HTTP/dependency concurrency có bulkhead/admission riêng.
- [ ] Không `Semaphore.acquire` trên event loop; permit release theo actual task completion.
- [ ] Driver/socket/connection timeouts cùng deadline/Retry-After hợp lý.
- [ ] Cancellation/timeout không tạo duplicate side effects hoặc leaked resources.
- [ ] NDJSON/SSE phù hợp framing; không commit error status muộn.
- [ ] Streaming producer và consumer có bounded queue/backpressure policy.
- [ ] Rate limit/tenant quota, authn/authz không phụ thuộc scheduler.
- [ ] Logs/metrics không lộ credentials/PII hoặc raw rejected payload.
- [ ] Metrics có scheduler pending, active, rejection, p95/p99, dependency pool usage.
- [ ] Unit StepVerifier và WebTestClient integration tests; thêm load test concurrent clients.
- [ ] Test qua proxy thực với slow-client/reconnect/timeout/disconnect.
- [ ] Có kế hoạch migration sang non-blocking driver hoặc durable job khi tải tăng.

## 22. Bài tập tự đánh giá

1. Vì sao `Mono.just(legacy.read())` vẫn chặn event loop dù viết `subscribeOn(boundedElastic())` phía sau?
2. Hãy sửa code trên bằng `Mono.fromCallable` và giải thích lúc nào callable được gọi.
3. `Schedulers.parallel()` khác `boundedElastic()` ở mục tiêu nào? Chạy JDBC trên parallel có gì nguy hiểm?
4. So sánh tác động của `subscribeOn` và `publishOn` đối với operator `map` có blocking call.
5. Một `flatMap(...,4)` ở endpoint và 100 clients đồng thời sẽ cho phép tối đa bao nhiêu inner subscriptions nếu không có global limit?
6. `Schedulers.newBoundedElastic(4,32,"io")` có nghĩa toàn ứng dụng chỉ queue 32 tasks không? Tra Javadoc.
7. Vì sao không tạo `newBoundedElastic` trong từng HTTP request?
8. Nếu `Semaphore.tryAcquire` thực thi **bên trong scheduled callable**, requests bị queue trước đó đã được reject ngay tại HTTP admission chưa?
9. Vì sao `Semaphore.acquire` trên Netty event loop là anti-pattern?
10. Nếu HTTP timeout và `doFinally` release permit nhưng blocking query còn chạy, tác hại gì?
11. `flatMapSequential` và `concatMap` khác nhau về concurrency, order, latency và buffering thế nào?
12. Một NDJSON stream trả 100 items, item 40 lỗi sau khi 39 items đã ghi. Có thể đổi HTTP status sang 500/503 không?
13. Khi client EventSource đóng tab, blocking SDK có lập tức dừng không? Giải thích cancellation limitations.
14. Một ứng dụng có 4 instances, mỗi instance bulkhead 3. Tổng calls tới DB có thể là bao nhiêu? Muốn global 6 cần giải pháp gì?
15. Nếu DB pool chỉ có 10 connections, để `boundedElastic` mặc định nhiều threads có chắc an toàn không?
16. `Retry-After` nên được trả lúc nào? Phân biệt 429 (quota) và 503 (capacity).
17. Kết hợp timeout, circuit breaker và retry thế nào để không tạo retry storm?
18. Khi chạy Java 21, bật virtual threads cho Reactor boundedElastic bằng property nào? Nó có giải quyết DB connection limits không?
19. Nếu one response record là 5MB, concurrency 2 bảo vệ memory thế nào và chưa bảo vệ ở đâu?
20. Thêm test tạo 20 requests đồng thời, xác minh `activeNow() ≤ 3` và overload response phù hợp; tránh phụ thuộc wall clock.
21. Thiết kế streaming endpoint qua proxy NGINX có buffering; vì sao `curl -N` trên origin chưa chắc mô tả browser UX thật?
22. Chọn giữa WebClient reactive, boundedElastic-wrapped RestTemplate, R2DBC và background job cho ba kiểu workload: lấy dữ liệu nhanh, báo cáo dài, import nhiều GB.
23. Nếu downstream chậm 5 giây nhưng WebFlux timeout 500ms, workers/DB connections có thể còn bị giữ bao lâu?
24. Nếu log chứa `ThreadLocal` traceId ở handler nhưng mất ở boundedElastic worker, cần Reactor Context/tracing integration như thế nào?
25. Làm sao tạo custom dedicated scheduler metrics không làm lộ user IDs vào tags?
26. Vì sao `onBackpressureBuffer()` unbounded không giải quyết blocking pool saturation?

## 23. Tài liệu chính thức và hướng đọc

- [Project Reactor 3.7 — Threading and Schedulers](https://docs.spring.io/projectreactor/reactor-core/docs/3.7.x/reference/html/coreFeatures/schedulers.html): khác biệt scheduler, `publishOn`/`subscribeOn`, boundedElastic threads/queues và virtual threads.
- [Project Reactor 3.7 — FAQ: Wrap a synchronous blocking call](https://docs.spring.io/projectreactor/reactor-core/docs/3.7.x/reference/html/faq.html): `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())`.
- [Project Reactor 3.7 — Schedulers Javadoc](https://projectreactor.io/docs/core/3.7.8/api/reactor/core/scheduler/Schedulers.html): `newBoundedElastic`, thread/queue caps, global defaults, virtual threads property.
- [Project Reactor 3.7 — Flux Javadoc](https://projectreactor.io/docs/core/3.7.8/api/reactor/core/publisher/Flux.html): `flatMap`, `flatMapSequential`, `concatMap`, concurrency/prefetch.
- [Project Reactor 3.7 — Mono Javadoc](https://projectreactor.io/docs/core/3.7.8/api/reactor/core/publisher/Mono.html): `fromCallable`, `fromRunnable`, `subscribeOn`, `timeout` và cancellation.
- [Spring Framework 6.2 — WebFlux Reactive Core](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/reactive-spring.html): non-blocking I/O, event loops, backpressure, reactive streams.
- [Spring Framework 6.2 — WebFlux Functional Endpoints](https://docs.spring.io/spring-framework/reference/6.2/web/webflux-functional.html): RouterFunction, HandlerFunction, ServerResponse.body/NDJSON.
- [Spring Framework 6.2 — WebFlux HTTP Streaming](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/controller/ann-methods/responsebody.html): reactive streaming media types, codecs và flush.
- [Spring Framework — WebTestClient](https://docs.spring.io/spring-framework/reference/testing/webtestclient.html): HTTP tests cho streaming và reactive response bodies.
- [Spring Boot 3.5 — Reactive Web Applications](https://docs.spring.io/spring-boot/3.5/reference/web/reactive.html): WebFlux/embedded Netty setup.
- [Reactive Streams JVM Specification](https://www.reactive-streams.org/): request(n), cancellation và publisher/subscriber contract.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-streaming-006">Hủy đăng ký, client ngắt kết nối và dọn dẹp tài nguyên</a>

<details>
<summary>Xem chi tiết</summary>

# Hủy đăng ký, client ngắt kết nối và dọn dẹp tài nguyên trong Streaming WebFlux

## 1. Bài toán: client ngừng đọc nhưng server vẫn đang phát stream

Một endpoint Spring WebFlux có thể trả về `Flux<ServerSentEvent<T>>` để đẩy sự kiện liên tục tới trình duyệt, hoặc trả `Flux<DataBuffer>` để truyền tệp lớn. Khác response JSON một lần, một stream có thể sống lâu, chứa nhiều sự kiện/bytes và có **tài nguyên gắn với từng subscription**: timer, đăng ký broker, database cursor, upstream HTTP connection, file channel hay pooled buffers.

Ví dụ:

~~~java
@GetMapping(
        path = "/api/ticks",
        produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> ticks() {
    return Flux.interval(Duration.ofSeconds(1))
            .map(i -> ServerSentEvent.<String>builder()
                    .event("tick")
                    .id(Long.toString(i))
                    .data("tick-" + i)
                    .build());
}
~~~

Đây là **fragment** cần class Spring `@RestController` cùng imports. Browser có thể dùng `EventSource`, sau vài sự kiện đóng tab hoặc gọi `eventSource.close()`. Nếu client đã không quan tâm, server nên ngừng gửi dữ liệu, huỷ upstream subscription và dọn tài nguyên. Nhưng phải phân biệt:

- **`cancel()`**: tín hiệu trong Reactive Streams/Project Reactor, do downstream hủy subscription.
- **Client disconnect**: sự kiện ở socket HTTP/transport, có thể dẫn đến `cancel()` trên response publisher **khi adapter phát hiện được**.
- **Resource cleanup**: giải phóng đối tượng thực (listener/file/DB connection/pooled buffer), không phải chỉ tăng một counter “cancelled”.
- **Business rollback**: hoàn tác tác dụng đã xảy ra; không tự xảy ra chỉ vì một subscription bị hủy.

**Không được khẳng định rằng client đóng tab thì server tức thì nhận `SignalType.CANCEL`, hoặc rằng `cancel` chắc chắn dừng JDBC query đang block.** Thời điểm và cách transport phát hiện disconnect phụ thuộc TCP/HTTP, read/write activity, proxy, server adapter và timing.

### Mục tiêu

Sau bài này bạn có thể:
1. Phân biệt ba sự kiện `onComplete`, `onError`, `cancel` và callback tương ứng của Reactor.
2. Hiểu luồng tín hiệu từ browser `EventSource.close()` hoặc hủy HTTP stream đến Netty/WebFlux/Publisher.
3. Dùng đúng `doOnCancel`, `doFinally(SignalType)`, `Flux.using` và `Flux.usingWhen`.
4. Đảm bảo cleanup ở **mọi** kết cục: thành công, lỗi, timeout, hủy; tránh double-close và resource leak.
5. Làm rõ cleanup **đồng bộ** so với cleanup **bất đồng bộ**, cũng như vị trí của `doOnDiscard`/`DataBufferUtils.release`.
6. Kiểm tra cold/hot publishers, subscriptions độc lập, shared upstream và multiplex nhiều clients.
7. Thiết kế SSE heartbeat/reconnect, timeout, proxy buffering và shutdown graceful.
8. Viết Java 17 + Spring Boot 3.3.5 WebFlux demo, test `StepVerifier.thenCancel()` và HTTP `WebTestClient`.
9. Nhận diện khi client disconnect **không** nên rollback job/transaction hoặc huỷ một workflow bền vững.

Bài giới hạn ở **Spring WebFlux/Project Reactor và streaming HTTP**, không nhầm cơ chế này với Servlet MVC `SseEmitter`/`DeferredResult`. Mã demo tương thích Spring Framework 6.1.x và Reactor 3.6.x được Spring Boot 3.3.5 quản lý.

## 2. Reactive Streams: subscription là một hợp đồng có vòng đời

Một `Publisher<T>` phát tín hiệu cho `Subscriber<T>` qua một `Subscription`. Trong Reactor, `Flux<T>` và `Mono<T>` thực thi hợp đồng này.

~~~text
Publisher<T>  ---onSubscribe(subscription)--->  Subscriber<T>
                 <------ request(n) ----------
                 ------ onNext(item) -------->
                 ------ onNext(item) -------->
                 ------ onComplete() -------->  [terminal]

hoặc           ------ onError(error) -------->  [terminal]

hoặc           <---------- cancel() ----------  [downstream hủy]
~~~

Ba tình huống cuối khác nhau:
- **Complete**: nguồn thông báo đã phát hết thành công; Subscriber nhận `onComplete`.
- **Error**: nguồn kết thúc bằng một lỗi; Subscriber nhận `onError`.
- **Cancel**: phía downstream gọi `Subscription.cancel()`; nó **không phải onComplete/onError gửi xuống subscriber**, và có thể xảy ra trước khi nguồn kết thúc.

Hợp đồng Reactive Streams nói về **ngừng yêu cầu/nhận thêm signals** sau cancel, trong phạm vi bất đồng bộ và race conditions. Nó không tự xác định HTTP status, transaction semantics, file descriptor cleanup hoặc lý do client đóng TCP.

### 2.1. Một request có thể tạo một subscription, nhưng đừng coi đó là định luật cho mọi publisher

Với annotated WebFlux controller trả `Flux`, framework thường là bên subscribe để viết HTTP response. Nhưng một logical request có thể kích hoạt nhiều publisher/inner subscriptions qua `flatMap`, `retryWhen`, `switchIfEmpty`, `zip` hoặc HTTP codec. Mỗi subscription có lifecycle riêng; ví dụ `retryWhen` có thể **subscribe lại** một cold source.

Vì vậy:
- Resource **per-subscription** phải được mở/đóng theo mỗi lượt subscribe.
- `doOnSubscribe` không mặc định chỉ chạy một lần trên mọi HTTP request trong mọi cấu hình.
- Một `Flux` được cache/share/hot multicast có thể không bị hủy upstream ngay khi **một** client rời đi.

### 2.2. Demand không đồng nghĩa giữ kết nối

`request(n)` mô tả số items downstream có thể nhận thêm (backpressure). `cancel()` chấm dứt subscription. Giảm demand không nhất thiết là cancel, và một subscriber tạm chậm không có nghĩa browser đã disconnect.

### 2.3. Terminal signals thường chỉ có một

Một nguồn chuẩn phát `onComplete` **hoặc** `onError` một lần, không cả hai. Cancellation là yêu cầu bất đồng bộ và có thể đua với terminal signal đang trên đường xử lý.

Điều này làm cho cleanup phải **idempotent** và không được phụ thuộc vào assumption một callback cụ thể sẽ luôn thắng mọi race.

## 3. Dòng sự kiện: từ HTTP client đến Reactor cancellation

### 3.1. SSE client chủ động đóng

~~~javascript
const source = new EventSource("/api/ticks");

source.addEventListener("tick", event => {
  console.log(event.data);
});

document.querySelector("#stop").addEventListener("click", () => {
  source.close();
});
~~~

Đây là đoạn browser JavaScript cho một trang đã có nút `#stop`, không phải project frontend đầy đủ. `EventSource.close()` yêu cầu ngừng EventSource connection và tự động reconnect; nó không gửi một HTTP endpoint "cancel" riêng.

Ở server:

~~~text
Browser EventSource
      |
      | HTTP GET /api/ticks
      v
Reactor Netty HTTP connection
      |
      v
WebFlux HandlerResultHandler / SSE writer
      |
      v
Flux<ServerSentEvent> subscription
      |
      +--- onNext(event 0) --> encoded bytes --> network
      +--- onNext(event 1) --> encoded bytes --> network
      |
      ... browser closes / network breaks ...
      |
      +--- adapter detects disconnect/write error
      |
      +--- may cancel response subscription
      |           |
      |           +--> doOnCancel
      |           +--> doFinally(CANCEL)
      |           +--> using/usingWhen cleanup
      |
      +--- or error/termination path according to transport/timing
~~~

**Sơ đồ thể hiện khả năng thường gặp**, không hứa một ánh xạ 1:1 rằng HTTP disconnect luôn báo chính xác một `CANCEL` tức thì. Trong thực tế có thể thấy `ON_ERROR` do write failure hoặc kết thúc request khác; một client mất mạng đột ngột đôi khi chỉ được phát hiện sau lần write tiếp theo/timeout.

### 3.2. Không có bảo đảm "client đã đọc xong sự kiện"

Server đã emit `onNext(event)` không chứng minh browser đã:
1. Nhận đủ TCP bytes.
2. Parse SSE event.
3. Chạy listener JavaScript.
4. Ghi nhận nghiệp vụ.
5. Lưu event bền vững.

Nếu sự kiện quan trọng cần **at-least-once delivery** hoặc acknowledgement, cần event IDs, replay store, consumer offset/ack protocol và idempotency. `onNext`/write completion không thay application-level acknowledgement.

### 3.3. Graceful close, abrupt disconnect và proxy

Client có thể dừng vì `EventSource.close()`, rời trang, network mất, refresh, request timeout hoặc reverse proxy ngắt idle connection. Proxy có thể giữ socket với upstream trong khi downstream browser đã đi, hoặc buffering/timeout làm delay phát hiện disconnect.

Không xây logic cleanup chỉ dựa vào "browser luôn gửi FIN tức thì". Cần:
- Bounded timeouts và server-side liveness policy.
- Periodic heartbeat nếu streaming protocol phù hợp.
- Upstream cancellation-aware clients.
- Cleanup per subscription và cleanup khi server shutdown.

## 4. Reactor operators: complete, error, cancel và finally

### 4.1. `doOnComplete` chỉ dành cho hoàn thành bình thường

~~~java
Flux<Long> stream = Flux.range(1, 3)
        .map(Integer::longValue)
        .doOnComplete(() -> recordCompleted());
~~~

`recordCompleted()` là fragment chưa định nghĩa. Callback chỉ chạy khi source hoàn thành thành công; nó **không đủ** để dọn resource với một stream vô hạn hoặc bị cancel.

### 4.2. `doOnError` chỉ nhận lỗi

~~~java
Flux<String> stream = fetchEvents()
        .doOnError(ex -> logSafeFailure(ex));
~~~

`fetchEvents`/`logSafeFailure` là fragments. DoOnError dành cho observability trên lỗi, không thực hiện retry/recovery và không tự chạy khi chỉ cancel. Tránh log raw headers/tokens hoặc message chứa dữ liệu cá nhân.

### 4.3. `doOnCancel` chạy khi subscription bị cancel

~~~java
Flux<Long> stream = Flux.interval(Duration.ofSeconds(1))
        .doOnCancel(() -> incrementCancelledSubscriptions());
~~~

Đối với Reactor, callback được thực thi trước khi cancel được chuyển upstream ở điểm operator đó. Nếu cancellation xảy ra vì `take(2)` hoặc `timeout`, callback có thể chạy dù không có browser đóng kết nối.

Không đánh đồng metric `cancelledSubscriptions` với số "người đóng tab": downstream operators, tests, retries, server shutdown và other cancellation paths đều có thể gây cancel.

### 4.4. `doFinally(SignalType)` bao phủ cả ba tình huống

~~~java
Flux<Long> stream = Flux.interval(Duration.ofSeconds(1))
        .doFinally(signal -> {
            switch (signal) {
                case ON_COMPLETE -> record("complete");
                case ON_ERROR -> record("error");
                case CANCEL -> record("cancel");
                default -> record("other");
            }
        });
~~~

`record` là một fragment chưa định nghĩa. Reactor `SignalType` có các hằng số khác, nhưng ba loại trên là quan trọng cho termination. Callback `doFinally` được gọi khi sequence kết thúc hoặc bị cancel; có thể chạy **sau khi terminal signal đã đi qua downstream**, và các `doFinally` nối tiếp có thể chạy thứ tự ngược.

**Cảnh báo trong test:** một `StepVerifier.verifyComplete()` đã return không phải lúc nào cũng bảo đảm các side effects ở `doFinally` bất đồng bộ đã hiển thị ngay; cần synchronization/latch/await có timeout khi assertions phụ thuộc callback đó.

### 4.5. Bảng so sánh callback

| Operator | onComplete | onError | cancel | Dùng cho |
| --- | --- | --- | --- | --- |
| `doOnComplete` (Flux) | Có | Không | Không | Theo dõi thành công |
| `doOnError` | Không | Có | Không | Log/metric lỗi |
| `doOnCancel` | Không | Không | Có | Theo dõi hủy |
| `doOnTerminate` | Có | Có | Không | Side effect khi có terminal signal |
| `doAfterTerminate` | Có | Có | Không | Side effect sau complete/error |
| `doFinally` | Có | Có | Có | Ghi metric kết thúc/latency |
| `using` | Có | Có | Có | Sở hữu/đóng tài nguyên đồng bộ |
| `usingWhen` | Có | Có | Có | Lifecycle tài nguyên với cleanup publisher async |

**Điểm mấu chốt:** `doFinally` là callback cho side effects; **không phải phép màu biến việc đóng một connection async thành một giao dịch có thể await**. Khi cleanup là async, `usingWhen` là abstraction mạnh hơn.

## 5. Tài nguyên phải thuộc subscription, không thuộc method controller

### 5.1. Anti-pattern: mở tài nguyên quá sớm

~~~java
@GetMapping("/events")
public Flux<Event> events() {
    ExternalSubscription subscription = registerListener();
    return subscription.events();
}
~~~

Đây là **fragment anti-pattern** với `Event` và `ExternalSubscription` chưa khai báo. Listener được mở ngay lúc controller method chạy, trước khi upstream reactive publisher được subscribe, và không có đường đóng khi request cancel. Nếu response thất bại trước subscription hoặc retry tạo nhiều subscribers, có thể leak.

Cần thiết kế producer dạng lazy:

~~~java
return Flux.defer(() -> {
    ExternalSubscription listener = registerListener();
    return listener.events()
            .doFinally(signal -> listener.close());
});
~~~

Đây vẫn là fragment minh họa, chỉ phù hợp nếu `close()` **đồng bộ, ngắn, idempotent** và listener chỉ thuộc đúng subscription này. Với resource ownership thực sự, ưu tiên `Flux.using`; với cleanup cần publisher async, dùng `Flux.usingWhen`.

### 5.2. `Flux.defer`

`Flux.defer(Supplier<Publisher<T>>)` xây một publisher mới cho **mỗi subscription**. Nó hữu ích để tránh mở listener khi chưa có subscription, nhưng không tự bảo đảm close nếu bạn quên gắn cleanup operator.

### 5.3. Một resource dùng chung cho nhiều clients

Nếu bạn đặt một WebSocket/Kafka consumer hoặc `Sinks.Many` global rồi nhiều HTTP SSE clients cùng subscribe, **một** client hủy không được phép `close()` shared resource cho tất cả.

Hai thiết kế khác nhau:
- **Per-client**: mỗi subscription có listener/resource riêng; `using`/`usingWhen` đóng đúng resource của client đó.
- **Shared hot source**: một resource phục vụ nhiều subscribers; lifetime thuộc application/shared service, dùng reference counting có kiểm soát hoặc application shutdown; per-client cleanup chỉ hủy **nhánh subscription của client**.

Không gắn `doFinally(sharedConnection::close)` trên từng client response, vì client đầu tiên rời đi có thể phá stream của mọi người.

## 6. `Flux.using`: mở/đóng tài nguyên đồng bộ

`Flux.using` giống tinh thần Java `try-with-resources` cho một publisher có lifecycle dài:

~~~java
Flux<String> lines = Flux.using(
        () -> openResource(),
        resource -> resource.events(),
        resource -> resource.close());
~~~

Fragment này cần `openResource()` trả resource có `events()` và `close()`. Reactor gọi resource supplier **khi subscribe**, sử dụng source publisher, rồi đóng resource khi stream complete, error hoặc cancel.

### 6.1. Quy tắc "một resource, một owner"

Mỗi subscription mới cần một resource riêng nếu resource không share-safe:
- Mở tài nguyên trong supplier của `using`, không từ một biến global eager.
- Chỉ resource creator/owner chính thức được close, không nhiều operators cùng release.
- Cleanup nên idempotent nếu môi trường bên ngoài có thể phát tín hiệu dừng thêm một lần.
- Không cất resource trong `ThreadLocal`: Reactor operators có thể đổi thread qua scheduler.
- Khi close có thể block, đừng thực hiện trong Netty event-loop callback; dùng async API hoặc scheduler thích hợp.

### 6.2. Cleanup khi `take(n)`

~~~java
Flux<Long> finiteView =
        Flux.using(
                () -> openCounterResource(),
                resource -> Flux.interval(Duration.ofMillis(50)),
                resource -> resource.close())
            .take(2);
~~~

`openCounterResource` là fragment. `take(2)` thường gửi cancellation upstream sau hai items; resource vẫn được cleanup. **`take` downstream có thể báo onComplete cho subscriber, trong khi upstream nhận cancel**. Do đó không dùng `doFinally(CANCEL)` làm bằng chứng duy nhất cho "client đứt kết nối": test `take` cũng tạo CANCEL.

### 6.3. Cleanup phải nhanh nếu đồng bộ

Nếu `close()` thực hiện:
- blocking network call lâu,
- `Future.get()`,
- chờ database lock,
- flush file hàng giây,

thì nó có thể chiếm event-loop thread khi cancellation đi qua operator. Khi tài nguyên hỗ trợ close bất đồng bộ, dùng `usingWhen`. Nếu bắt buộc close blocking legacy, isolate qua bounded worker và tôn trọng giới hạn queue/timeout; không gắn `Thread.sleep` vào `doFinally` trên Netty loop.

## 7. `Flux.usingWhen`: tài nguyên có cleanup bất đồng bộ

Khi resource mở và đóng đều là publishers, dùng `usingWhen`. Ví dụ nguyên tắc:

~~~java
Flux<Event> events = Flux.usingWhen(
        openAsyncConnection(),                      // Mono<Connection>
        connection -> connection.events(),           // Flux<Event>
        connection -> connection.closeAsync(),       // Mono<Void> on complete
        (connection, error) -> connection.closeAsync(), // Mono<Void> on error
        connection -> connection.closeAsync());      // Mono<Void> on cancel
~~~

Đây là **fragment** cho một API external giả định, không phải code runnable độc lập. `openAsyncConnection()`, `Connection.events()` và `closeAsync()` cần thực sự non-blocking, trả publishers và có resource ownership rõ.

### 7.1. Vì sao đủ ba cleanup paths?

Overload đầy đủ nhận:
1. `asyncComplete` khi publisher resource sử dụng hoàn tất bình thường.
2. `asyncError` khi lỗi; có thể điều chỉnh rollback/cleanup tùy error.
3. `asyncCancel` khi downstream cancel.

Nếu cleanup completion/error thất bại, `usingWhen` có quy tắc ưu tiên/suppression errors cần kiểm theo Reactor Javadoc, không tự cho rằng lỗi body gốc luôn còn nguyên như message trả cho client. **On cancel không có downstream consumer để nhận một error response mới**; lỗi cleanup phải được log/metric đúng layer và có retry/bù trừ nếu policy yêu cầu.

### 7.2. Resource đơn giản vs transaction

Ví dụ database reactive có thể dùng một transaction:
- **Complete** ⇒ commit.
- **Error** ⇒ rollback.
- **Cancel** ⇒ xử lý cancellation theo transaction policy (thường rollback nếu transaction còn mở).

Nhưng không tự kết luận dùng `usingWhen` là đủ thay `ReactiveTransactionManager` hoặc `TransactionalOperator` của Spring. Transaction management có nhiều edge cases (savepoints, context, driver, connection reuse, isolation). Với R2DBC, nên dùng Spring reactive transaction facilities, không tự chế lệnh commit/rollback trên mọi SSE subscription.

### 7.3. Không dùng `doFinally` thay thế indiscriminately

Sai với async close:

~~~java
return resource.events()
        .doFinally(signal ->
                resource.closeAsync().subscribe());
~~~

Fragment này “fire-and-forget”: cleanup subscribe tách rời, error khó giám sát và không được compose vào resource lifecycle. `usingWhen` cho bạn một operator quản lý completion/error/cancel cleanup có contract rõ hơn.

## 8. `Disposable`, Scheduler và cancellation của timer

`Flux.interval(...)` dùng một scheduler để phát các tick, và upstream subscription thường ngừng scheduling khi cancelled. Khi trả `Flux.interval` từ WebFlux handler, **không tự gọi `subscribe()` trong controller** để lưu một `Disposable` nếu chỉ muốn stream kết quả cho client.

Sai:

~~~java
@GetMapping("/wrong")
public Flux<Long> wrong() {
    Disposable orphan = Flux.interval(Duration.ofSeconds(1))
            .subscribe(i -> audit(i));
    return Flux.interval(Duration.ofSeconds(1));
}
~~~

Đây là fragment minh họa với `audit` chưa định nghĩa. `orphan` là subscription độc lập, client disconnect của returned Flux **không hủy** nó; nếu không dispose, timer/audit vẫn chạy. Sửa bằng cách return một publisher được compose:

~~~java
@GetMapping("/right")
public Flux<Long> right() {
    return Flux.interval(Duration.ofSeconds(1))
            .doOnNext(i -> recordSafeMetric(i));
}
~~~

`recordSafeMetric` cũng là fragment, nhưng side effect gắn với actual response subscription. Nếu cần một background broadcast dùng chung, quản lý lifecycle trong service application (khởi động/đóng theo bean lifecycle) chứ không khởi động `subscribe()` mới trên mỗi HTTP request.

### 8.1. `Disposable.dispose()` và `Subscription.cancel()`

Reactor `Disposable` được trả khi tự `subscribe(...)` thường cho phép `dispose()`, qua đó yêu cầu hủy subscription. Ở WebFlux controller, framework sở hữu subscriber của HTTP response; bạn **không cần** gọi dispose thủ công. Với custom scheduler hoặc shared consumer do application tự tạo, application phải nắm quyền sở hữu lifecycle và dispose ở shutdown.

## 9. SSE: heartbeat, reconnect và event IDs

### 9.1. Vì sao SSE cần heartbeat?

Một SSE stream có thể không có business event trong vài phút. Một số reverse proxies, load balancers hoặc browser/network middleboxes có idle timeouts; nếu không có bytes được truyền, connection có thể bị đóng. Heartbeat có thể:
- Phát SSE **comment** như `: keepalive`, được EventSource bỏ qua như event business.
- Phát một event `heartbeat` riêng, frontend có thể nhận/ignore.
- Phát ping theo infrastructure-specific protocol khi phù hợp.

Heartbeat giúp **phát hiện/giữ connection** ở một số môi trường, không bảo đảm mọi proxy cho phép stream sống vô hạn. Thêm intervals phù hợp giới hạn idle timeout, và kiểm proxy response buffering.

### 9.2. SSE event format

~~~text
id: 42
event: book-updated
data: {"id":7,"version":3}

: keepalive

~~~

Dòng rỗng kết thúc một SSE event. `id` có thể giúp client gửi `Last-Event-ID` khi reconnect. **EventSource tự reconnect theo browser policy** nhưng server chỉ có thể replay mất event nếu ứng dụng giữ history/offset; số ID được phát không tự bảo đảm delivery exactly-once.

### 9.3. EventSource reconnect có thể mở subscription mới

Nếu client mất kết nối và browser reconnect:
- Server có thể thấy **subscription cũ cancel/close**.
- Request mới tạo **subscription mới** và resource mới.
- Nếu có replay dựa trên Last-Event-ID, việc đọc offset/authorizing stream phải tính đến user/tenant và retention.
- Duplicate events là khả năng thực tế; frontend phải idempotent nếu apply side effects.

Không thiết kế cleanup "cũ xong mới được mở mới": network race có thể khiến stream mới mở trước khi stream cũ được server phát hiện đã chết.

### 9.4. Ứng dụng nên giới hạn stream lifetime

Một endpoint SSE có thể stream indefinitely, nhưng phải có chính sách:
- Giới hạn subscription concurrency per user/tenant nếu cần.
- Giới hạn backend listeners/connections và số lần reconnect.
- Heartbeat / idle timeout / maximum session lifetime.
- Backpressure/slow consumer policy.
- Graceful shutdown/rolling deploy strategy.
- Auth token expiry/re-auth behavior.

Giữ SSE vô hạn không đồng nghĩa giữ transaction DB mở vô hạn; thường không làm vậy.

## 10. Client disconnect không phải lúc nào cũng là `CANCEL` ngay

### 10.1. Không thể luôn biết ngay người dùng đã đóng tab

Với TCP, server có thể chỉ phát hiện một disconnect khi:
- Đọc/ghi socket nhận lỗi hoặc EOF.
- Transport/HTTP server callback báo channel inactive.
- Keepalive/heartbeat hay idle timeout phát hiện.
- Proxy upstream connection bị đóng.

Nếu server không gửi dữ liệu trong thời gian dài, một network break có thể chưa được thấy ngay. Một pipeline `Flux.never()` không phát gì còn khiến application không dễ nhận ra client đã biến mất, tùy adapter/proxy. Vì vậy streaming nên cân nhắc heartbeat/liveness và cleanup on timeout.

### 10.2. `doFinally` không đo "số tab đã đóng"

Thống kê lý tưởng cần phân loại:
- `ON_COMPLETE`: stream kết thúc theo hợp đồng.
- `ON_ERROR`: stream lỗi từ upstream, encoding hoặc transport.
- `CANCEL`: downstream không cần dữ liệu từ một subscription này nữa.
- Network diagnostics: riêng logs/metrics của socket/HTTP, proxy disconnect.
- Business session changes: login/logout/revocation, không phải raw transport.

Cùng một client ngừng stream có thể không phát đúng cùng một terminal signal trong mọi stack/version.

### 10.3. `doOnError` trên source có thấy lỗi ghi socket không?

Không phải luôn. Một lỗi socket có thể xảy ra **downstream của source Flux** trong HTTP writer, rồi writer hủy source; `doOnError` trên upstream source có thể không nhận onError mà nhận CANCEL. Do đó muốn quan sát transport errors phải dùng instrumentation ở WebFlux/Netty layer hoặc server metrics, ngoài doOnError trên business publisher.

## 11. Cleanup khi response đã commit

Streaming HTTP thường ghi headers/status trước khi body hoàn tất. Khi SSE đã phát vài events, status 200 có thể đã **commit**. Sau đó upstream exception:
- Không chắc có thể đổi thành status 500 cùng `ProblemDetail`.
- Không chắc có thể gửi event "error" hợp lệ cho client đã disconnect.
- Có thể cần đóng stream và để client reconnect.
- Nếu protocol cho phép in-band error event, phải thiết kế event schema, nhưng không thay HTTP status đã gửi.

Với `ServerHttpResponse.isCommitted()`, bạn biết response đã được đánh dấu committed trong abstraction, nhưng không nên tự diễn giải nó như rollback switch. Error handling trước commit và sau commit là hai bài toán khác nhau.

### 11.1. Đừng đăng ký cleanup bằng `then` sau một stream vô hạn

~~~java
return response.writeWith(dataBuffers)
        .then(Mono.fromRunnable(resource::close));
~~~

Đây là fragment; nếu publisher streaming vô hạn, `then` có thể **không chạy** cho đến complete, và khi bị cancel, `then` không phải cơ chế cleanup tin cậy. Dùng resource operator `using`/`usingWhen` hoặc `doFinally` phù hợp lifecycle.

## 12. Streaming DataBuffer: pooled memory và ownership

Khi stream bytes trực tiếp:

~~~java
Flux<DataBuffer> body = request.getBody();
return response.writeWith(body);
~~~

Đây là fragment, không phải một proxy HTTP hoàn chỉnh. `DataBuffer` có thể backed by pooled Netty `ByteBuf`; release/retain đúng nơi đặc biệt quan trọng khi request/response bị cancel.

### 12.1. Quy tắc cơ bản

- **Producer sở hữu** buffer trong thời gian chưa bàn giao cho downstream.
- Khi đã gửi `DataBuffer` tới `ServerHttpResponse.writeWith(...)`, response writer có trách nhiệm tiêu thụ/phối hợp release theo contract; không release cùng buffer **trước khi writer đọc**.
- Nếu ứng dụng tự đọc buffer rồi **không** chuyển nó đi, cần `DataBufferUtils.release(buffer)` khi xử lý xong, kể cả error paths.
- Nếu một operator bỏ/discard buffers trước khi downstream nhận chúng, `doOnDiscard(DataBuffer.class, DataBufferUtils::release)` có thể hữu ích khi operator hỗ trợ discard hooks.
- Cancellation có thể khiến buffers đã buffered/prefetched bị discard; phải kiểm actual operator semantics và test Netty leak-detection trong môi trường phù hợp.
- Nếu giữ buffer sang asynchronous task/subscriber khác, `DataBufferUtils.retain`/`release` cần cân bằng theo ownership; không dùng buffer đã release.

**Đừng blindly gọi release ở tất cả operators**: double release hoặc use-after-release có thể phá dữ liệu/native memory. Dựa trên contract của Spring/Reactor codec, khi không cần thao tác raw bytes thì **để codecs/Resource abstractions** quản lý là tốt nhất.

### 12.2. Anti-pattern: release trước khi ghi

~~~java
Flux<DataBuffer> broken = source
        .doOnNext(DataBufferUtils::release);

return response.writeWith(broken);
~~~

Sai vì response writer nhận một buffer có thể đã release.

### 12.3. Anti-pattern: consume mà không release

~~~java
return DataBufferUtils.join(request.getBody())
        .map(buffer -> {
            byte[] bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            return bytes; // nếu pooled, chưa release!
        });
~~~

Ngoài thiếu release, `join` không giới hạn có thể gây memory exhaustion. Đối với body nhỏ, dùng overload `join(publisher, maxByteCount)` và:

~~~java
return DataBufferUtils.join(request.getBody(), 1024)
        .map(buffer -> {
            try {
                byte[] bytes = new byte[buffer.readableByteCount()];
                buffer.read(bytes);
                return bytes;
            } finally {
                DataBufferUtils.release(buffer);
            }
        });
~~~

Đây là fragment cần `request` và các imports; quá max bytes sẽ gây `DataBufferLimitException`. Với payload lớn, **không** aggregate; stream sang file/object store có giới hạn rõ và xử lý partial upload/cancel.

### 12.4. Cancellation khi ghi file

Nếu dùng `DataBufferUtils.write(...)`, hiểu contract của overload đang dùng:
- Một số overload **không tự release source DataBuffers**; docs yêu cầu bạn subscribe `DataBufferUtils.releaseConsumer()` cho source buffers khi cần.
- Các overload ghi tới Path hoặc AsynchronousFileChannel có ownership và completion semantics riêng.
- Khi upload bị cancel, file có thể là **partial file**. Cần chính sách xóa temp file hoặc sử dụng staged upload rồi atomic rename khi complete.
- Đừng chuyển một file upload chưa hoàn tất thành "thành công" trong database chỉ vì đã mở file và ghi vài chunks.

## 13. Cancellation có dừng upstream thật không?

### 13.1. Reactive HTTP client như WebClient

Nếu handler stream dữ liệu từ `WebClient`:

~~~java
return webClient.get()
        .uri("/upstream/events")
        .retrieve()
        .bodyToFlux(Event.class);
~~~

Đây là fragment có bean `WebClient` và type `Event`. Khi subscriber cancel, Reactor Netty client có thể cancel/đóng exchange hoặc ngừng đọc theo implementation/timing, giúp tránh dùng tài nguyên thừa. Nhưng không đảm bảo remote server đã rollback tác dụng hoặc không phát thêm events trước khi nhận disconnect.

### 13.2. JDBC hoặc legacy blocking code

~~~java
return Flux.fromIterable(ids)
        .map(id -> blockingRepository.findById(id));
~~~

Nếu `blockingRepository` là JDBC/JPA sync, `map` có thể block event loop. Cancel downstream khi JDBC call đang chạy **không chắc chắn interrupt được driver** hoặc rollback query. Cần:
- Driver statement/query/socket timeouts.
- Bounded executor/scheduler cho blocking legacy calls.
- Connection pool limits, circuit breaker/overload policy.
- Idempotency và transaction boundaries.
- Explicit cancel API nếu driver hỗ trợ, có race handling.

Với I/O reactive hoàn toàn (R2DBC/WebClient), compose publisher không gọi `block()`. Không hứa "return Flux" tự làm database non-blocking.

### 13.3. CPU-bound work

Một phép tính CPU-bound lớn có thể không chú ý cancellation cho đến khi hoàn thành một khối tính toán. Nếu muốn interruptible, chia thành chunks nhỏ, kiểm cancellation/cooperative flags đúng API và phân bổ compute pool phù hợp. Hủy subscriber không tự dừng một JVM native CPU routine ngay lập tức.

## 14. Timeout, retry và cancel khác nhau

### 14.1. `timeout` thường tạo error rồi cancel upstream

~~~java
Flux<Long> guarded = Flux.interval(Duration.ofSeconds(2))
        .timeout(Duration.ofMillis(500));
~~~

Ở downstream, `timeout` phát `TimeoutException`. Ở upstream `Flux.interval`, operator có thể nhận `CANCEL` vì timeout đã hủy subscription cũ. Vì vậy cùng một request có thể có:
- downstream `doFinally(ON_ERROR)` sau timeout,
- upstream `doFinally(CANCEL)` do timeout cancel.

Kết luận: **SignalType phụ thuộc vị trí đặt operator**, không phải nhãn tuyệt đối của toàn HTTP request.

### 14.2. Retry tạo lại resource và subscription

~~~java
Flux<Event> stream = openEvents()
        .retryWhen(Retry.backoff(3, Duration.ofMillis(200)));
~~~

Đây là fragment. Khi lỗi, `retryWhen` có thể subscribe lại, mở một resource mới. Với `using`/`usingWhen`, lần subscribe cũ phải được cleanup trước/tương ứng lifecycle trước khi resource mới được dùng. Tránh retry vô hạn trên SSE: có thể gây reconnect storm, duplicated side effects và hết subscription quotas.

### 14.3. Một request bị cancel không đồng nghĩa timeout response 504

HTTP 504 là gateway timeout theo semantics; 503 là service unavailable; 408 là request timeout theo server policy. Client disconnect thường **không còn response receiver** để gửi error body/status mới, nên đừng tự chuyển mọi `CANCEL` thành một HTTP 504.

## 15. Shared streams: `publish().refCount()`, `share()` và lifecycle

Nếu service giữ một nguồn sự kiện "hot" dùng chung:

~~~java
Flux<Event> shared = source.publish().refCount(1);
~~~

Đây là fragment có `source` phát event objects. `refCount(1)` subscribe upstream khi có ít nhất một subscriber và có thể cancel upstream khi số subscribers về 0. Tuy nhiên behavior chi tiết còn phụ thuộc `refCount(n,gracePeriod)`, sources và operators khác.

**Quan trọng:** nếu resource dùng chung thuộc toàn ứng dụng, không close nó ngay khi một SSE subscriber cancel. Có thể dùng:
- Sinks.Many với application-level owner.
- Shared broker consumer có ref counting.
- Per-client subscription disposable được quản lý, không đóng broker consumer.
- Replay/history store có giới hạn khi cần Last-Event-ID.
- Backpressure/slow consumer policy cho từng client.

### 15.1. `cache()` có thể giữ dữ liệu và upstream

Một `Flux.cache()` hoặc `replay()` có thể giữ items trong memory theo chính sách và cho subscriber mới xem lại. Với infinite streams, cache không giới hạn có nguy cơ memory leak và upstream không dừng khi người cuối đi. Đừng thêm `cache()` vào SSE chỉ để "tránh duplicate DB calls" mà chưa xác định lifecycle/retention.

### 15.2. `Sinks.Many`: per-subscriber cleanup vs shared sink

Khi nhiều SSE clients đọc từ một shared sink, hủy một subscriber cần dừng nhánh nhận của subscriber đó; nó không tự hoàn thành shared sink cho mọi clients. Nếu bạn tự đăng ký listener trên sink/hot data structure, dọn listener reference/metadata để tránh growing lists, và theo dõi `currentSubscriberCount` hoặc metrics phù hợp.

## 16. Ví dụ runnable: Spring Boot 3.3.5 / Java 17 / WebFlux SSE

Ví dụ có ba endpoint:
- `GET /api/stream`: SSE vô hạn, mỗi subscription mở một resource `StreamLease` và đóng khi complete/error/cancel qua `Flux.using`; ghi counters.
- `GET /api/finite`: SSE hữu hạn gồm hai sự kiện để test HTTP encoding/response completion.
- `GET /api/metrics`: snapshot counters `active`, `closed`, `cancelled` mang tính chẩn đoán.

Demo không dùng DB, Kafka, credentials, proxy hay external HTTP dependencies. Mỗi `StreamLease` chỉ chứa một counter, vì vậy `close` **nhanh, đồng bộ** và an toàn cho `Flux.using`. Khi thay bằng broker listener/DB connection có close async, áp dụng `usingWhen` thay vì ngồi chờ trong `close`.

~~~text
webflux-stream-lifecycle/
├── settings.gradle
├── build.gradle
└── src/
    ├── main/java/com/example/streamdemo/
    │   ├── StreamDemoApplication.java
    │   ├── stream/StreamLease.java
    │   ├── stream/StreamService.java
    │   └── web/StreamController.java
    └── test/java/com/example/streamdemo/
        ├── StreamServiceTest.java
        └── StreamHttpTest.java
~~~

### 16.1. Gradle config

`settings.gradle`:

~~~groovy
rootProject.name = 'webflux-stream-lifecycle'
~~~

`build.gradle`:

~~~groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.5'
    id 'io.spring.dependency-management' version '1.1.6'
}

group = 'com.example'
version = '0.0.1-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'io.projectreactor:reactor-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
~~~

Chỉ dùng `spring-boot-starter-webflux` để Boot chạy Reactor Netty theo setup tiêu chuẩn. Java 17+ và Gradle wrapper tương thích cần có trong project khi chạy lệnh. `spring-boot-starter-web` có thể khiến Boot chọn MVC type nếu dùng cùng WebFlux starter.

### 16.2. Main application

`src/main/java/com/example/streamdemo/StreamDemoApplication.java`:

~~~java
package com.example.streamdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class StreamDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(StreamDemoApplication.class, args);
    }
}
~~~

### 16.3. `StreamLease`: chủ sở hữu resource per subscription

`src/main/java/com/example/streamdemo/stream/StreamLease.java`:

~~~java
package com.example.streamdemo.stream;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

final class StreamLease implements AutoCloseable {

    private final AtomicInteger active;
    private final AtomicInteger closed;
    private final AtomicBoolean disposed = new AtomicBoolean();

    StreamLease(AtomicInteger active, AtomicInteger closed) {
        this.active = active;
        this.closed = closed;
        active.incrementAndGet();
    }

    @Override
    public void close() {
        if (disposed.compareAndSet(false, true)) {
            active.decrementAndGet();
            closed.incrementAndGet();
        }
    }
}
~~~

`AtomicBoolean.compareAndSet(false, true)` bảo đảm close idempotent, tránh decrement active hai lần nếu một thành phần khác vô tình cố đóng thêm. Việc dùng AtomicIntegers không đại diện cho distributed metrics across pods; production cần monitoring ngoài JVM hoặc labeled counters/gauges thích hợp.

### 16.4. `StreamService`: tạo resource đúng lúc subscribe

`src/main/java/com/example/streamdemo/stream/StreamService.java`:

~~~java
package com.example.streamdemo.stream;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

@Service
public class StreamService {

    private final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger closed = new AtomicInteger();
    private final AtomicInteger cancelled = new AtomicInteger();

    public Flux<ServerSentEvent<String>> events() {
        return Flux.using(
                () -> new StreamLease(active, closed),
                lease -> Flux.interval(
                        Duration.ZERO, Duration.ofMillis(100))
                        .map(index -> ServerSentEvent.<String>builder()
                                .event("tick")
                                .id(Long.toString(index))
                                .data("tick-" + index)
                                .build()),
                StreamLease::close)
                .doFinally(signal -> {
                    if (signal == SignalType.CANCEL) {
                        cancelled.incrementAndGet();
                    }
                });
    }

    public Flux<ServerSentEvent<String>> finite() {
        return Flux.just("alpha", "beta")
                .map(word -> ServerSentEvent.<String>builder()
                        .event("word")
                        .data(word)
                        .build());
    }

    public int activeCount() {
        return active.get();
    }

    public int closedCount() {
        return closed.get();
    }

    public int cancelledCount() {
        return cancelled.get();
    }
}
~~~

Các quyết định có chủ đích:
1. Không tạo `StreamLease` ở controller hoặc singleton constructor: `Flux.using` gọi supplier khi **mỗi subscriber** subscribe.
2. `Flux.interval(Duration.ZERO, 100 ms)` phát tick không dùng `Thread.sleep`. Event loop không bị giữ trong lúc chờ timer.
3. Tạo `ServerSentEvent` có event name `tick`, event id số thứ tự và text payload.
4. `StreamLease::close` do `using` gọi trên cả complete/error/cancel; nó idempotent.
5. `doFinally(CANCEL)` chỉ ghi **subscription cancellation**, không được quảng cáo là bằng chứng 100% client rời trang.
6. `finite()` tách ra để test HTTP body SSE hữu hạn.
7. `active` có thể lên/xuống khi clients subscribe/cancel; metrics endpoint chỉ là snapshot bất đồng bộ, không phải transactional invariant cùng thời điểm.
8. Nếu client mất mạng âm thầm, `active` có thể vẫn dương đến khi transport phát hiện/timeout. Tăng heartbeat/monitoring, không tự force-close khi một metric thay đổi.

### 16.5. Controller SSE + metrics

`src/main/java/com/example/streamdemo/web/StreamController.java`:

~~~java
package com.example.streamdemo.web;

import com.example.streamdemo.stream.StreamService;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
public class StreamController {

    private final StreamService streams;

    public StreamController(StreamService streams) {
        this.streams = streams;
    }

    @GetMapping(
            path = "/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream() {
        return streams.events();
    }

    @GetMapping(
            path = "/finite",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> finite() {
        return streams.finite();
    }

    @GetMapping(
            path = "/metrics",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Integer>> metrics() {
        return Mono.fromSupplier(() -> Map.of(
                "active", streams.activeCount(),
                "closed", streams.closedCount(),
                "cancelled", streams.cancelledCount()));
    }
}
~~~

Không có `subscribe()` hoặc `block()` trong controller. WebFlux quản lý subscription và SSE HTTP writer. Vì là demo, endpoint metrics công khai; production metrics phải tuân deployment security policy và không lộ dữ liệu client/user chi tiết.

### 16.6. Vì sao demo không dùng `doFinally(close)` để đóng resource?

`doFinally(lease::close)` vẫn có thể đúng với một resource **độc lập per subscriber**, close nhanh và được tạo đúng lúc; nhưng `using` biểu đạt **quyền sở hữu** rõ hơn và tránh lỗi mở resource eagerly ngoài reactive chain. Khi close cần non-blocking publisher, chuyển sang `usingWhen`.

## 17. Unit test: cancellation chắc chắn do test chủ động tạo

`StepVerifier` có thể kiểm subscription lifecycle **deterministically ở Reactor level**, không cần giả lập mạng hỏng.

`src/test/java/com/example/streamdemo/StreamServiceTest.java`:

~~~java
package com.example.streamdemo;

import com.example.streamdemo.stream.StreamService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;
import reactor.test.StepVerifier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamServiceTest {

    @Test
    void cancelStopsSourceAndReleasesOwnedResource() {
        StreamService service = new StreamService();

        StepVerifier.create(service.events())
                .expectNextCount(2)
                .thenCancel()
                .verify(Duration.ofSeconds(3));

        assertEquals(0, service.activeCount());
        assertEquals(1, service.closedCount());
    }

    @Test
    void usingClosesWhenSourceCompletesNormally() {
        AtomicBoolean closed = new AtomicBoolean();

        Flux<String> flux = Flux.using(
                () -> "resource",
                resource -> Flux.just("a", "b"),
                resource -> closed.set(true));

        StepVerifier.create(flux)
                .expectNext("a", "b")
                .verifyComplete();

        assertTrue(closed.get());
    }

    @Test
    void usingClosesWhenSourceErrors() {
        AtomicBoolean closed = new AtomicBoolean();

        Flux<String> flux = Flux.using(
                () -> "resource",
                resource -> Flux.concat(
                        Flux.just("a"),
                        Flux.error(new IllegalStateException("broken"))),
                resource -> closed.set(true));

        StepVerifier.create(flux)
                .expectNext("a")
                .expectErrorMessage("broken")
                .verify();

        assertTrue(closed.get());
    }

    @Test
    void doFinallySeesCancelButThatDoesNotMeanNetworkDisconnect() {
        AtomicReference<SignalType> reason = new AtomicReference<>();

        Flux<Long> flux = Flux.interval(Duration.ofMillis(10))
                .doFinally(reason::set);

        StepVerifier.create(flux)
                .expectNextCount(2)
                .thenCancel()
                .verify(Duration.ofSeconds(3));

        // doFinally callback có thể trễ so với downstream;
        // không assert reason.get() ngay tức thì ở đây.
    }
}
~~~

**Giải thích assertions:**
- Test đầu dùng **test subscriber tự thenCancel**, không có TCP client. Nó chứng minh `using` cleanup theo cancellation, **không chứng minh Netty phát hiện browser close trong 0 ms**.
- Test complete/error chứng minh close ở hai nhánh khác.
- Test cuối cố ý không assert `doFinally` ngay sau verify vì callbacks downstream/upstream có ordering subtleties; nếu cần chắc chắn reason, hãy dùng `CountDownLatch` trong callback và `await` với timeout thay vì `Thread.sleep` vô hạn.
- Dữ liệu chính xác của hai ticks không được assert vì timestamp/scheduler có thể thay đổi; chỉ đếm số items rồi cancel.
- Với `Flux.using` sync cleanup đơn giản, trạng thái resource đóng có thể kiểm ngay sau StepVerifier termination/cancellation trong demo.
- Tất cả tests nên hoàn thành trong vài giây khi scheduler hoạt động bình thường; CI chậm vẫn có thể gây timeout test nếu máy quá tải, cần cấu hình phù hợp.

### 17.1. Test `doFinally(CANCEL)` bằng latch khi cần

Nếu muốn assert chính xác callback:

~~~java
CountDownLatch finalized = new CountDownLatch(1);
AtomicReference<SignalType> reason = new AtomicReference<>();

Flux<Long> flux = Flux.interval(Duration.ofMillis(10))
        .doFinally(signal -> {
            reason.set(signal);
            finalized.countDown();
        });

StepVerifier.create(flux)
        .expectNextCount(2)
        .thenCancel()
        .verify(Duration.ofSeconds(3));

assertTrue(finalized.await(2, TimeUnit.SECONDS));
assertEquals(SignalType.CANCEL, reason.get());
~~~

Đây là **fragment cho một test JUnit** có imports `CountDownLatch`, `TimeUnit`, `Flux`, `StepVerifier`, `AtomicReference` và assertions. Dùng bounded `await` trong test, **không** dùng `CountDownLatch.await()` trong event-loop handler ở production.

## 18. WebTestClient: kiểm SSE thật mà không giả định disconnect tức thì

Test qua HTTP/Netty có thể xác nhận stream hữu hạn được encode thành SSE, còn cancellation service-level đã được test ở mục trước.

`src/test/java/com/example/streamdemo/StreamHttpTest.java`:

~~~java
package com.example.streamdemo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class StreamHttpTest {

    @Autowired
    WebTestClient client;

    @Test
    void finiteSseHasEventFramingAndCompletes() {
        client.get()
                .uri("/api/finite")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(
                        MediaType.TEXT_EVENT_STREAM)
                .expectBody(String.class)
                .value(body -> {
                    assertTrue(body.contains("event:word"));
                    assertTrue(body.contains("data:alpha"));
                    assertTrue(body.contains("data:beta"));
                });
    }

    @Test
    void metricsEndpointIsReadable() {
        client.get()
                .uri("/api/metrics")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.active").isNumber()
                .jsonPath("$.closed").isNumber()
                .jsonPath("$.cancelled").isNumber();
    }
}
~~~

Không cần sleep hoặc mở vô hạn connection trong test HTTP này. Khi muốn test real TCP disconnect:
1. Dùng một client có API hủy response-body subscription/đóng connection có kiểm soát.
2. Nhận ít nhất một SSE event để biết stream thật sự đã được subscribe và có hoạt động I/O.
3. Hủy body subscription hoặc đóng client connection.
4. Chờ **bounded deadline** cho metric `active` về giá trị phù hợp, không assert tức thì.
5. Test cả proxy/load balancer thật nếu production có các tầng đó.

Không bịa assertion deterministic “Netty luôn phát CANCEL trong 1 ms” ở mọi OS/container/CI. HTTP tests và Reactor unit tests **bổ sung cho nhau**.

### 18.1. Ví dụ WebTestClient chủ động cancel response body

~~~java
FluxExchangeResult<ServerSentEvent<String>> result =
        client.get()
                .uri("/api/stream")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(
                        new ParameterizedTypeReference<ServerSentEvent<String>>() {});

StepVerifier.create(result.getResponseBody())
        .expectNextCount(2)
        .thenCancel()
        .verify(Duration.ofSeconds(5));
~~~

Fragment này cần imports `FluxExchangeResult`, `ServerSentEvent`, `ParameterizedTypeReference`, `StepVerifier`, `Duration` và field `WebTestClient client`. Nó chủ động cancel downstream body publisher; không khẳng định HTTP connection/socket được server quan sát ra sao ở từng transport/runtime. Muốn assert server cleanup, dùng bounded polling/await trên server metrics và giữ test isolation.

## 19. Chạy demo bằng curl

Ở project có Gradle wrapper hợp lệ:

~~~bash
./gradlew test
./gradlew bootRun
~~~

Trên Windows PowerShell:

~~~powershell
.\gradlew.bat test
.\gradlew.bat bootRun
~~~

Chạy các lệnh theo thứ tự ở shell, không khởi động `bootRun` nếu tests chưa hoàn tất. Các file `build.gradle`/`settings.gradle` không tự tạo Gradle wrapper; nếu thiếu, cần tạo wrapper bằng Gradle tương thích.

### 19.1. Stream không kết thúc

~~~bash
curl -N -i 'http://localhost:8080/api/stream' \
  -H 'Accept: text/event-stream'
~~~

Ví dụ payload xuất hiện lặp lại:

~~~text
id:0
event:tick
data:tick-0

id:1
event:tick
data:tick-1

id:2
event:tick
data:tick-2

~~~

Trong terminal, nhấn Ctrl+C để hủy phía client. `curl -N` tắt output buffering của curl, giúp quan sát events ngay hơn. **Không** diễn giải ví dụ này là raw HTTP bytes luôn có đúng thứ tự/line endings trên mọi server; Spring SSE encoder có thể sắp xếp fields khác nhau theo implementation.

### 19.2. Quan sát counters

~~~bash
curl -i 'http://localhost:8080/api/metrics'
~~~

Ví dụ khi đang có một subscriber:

~~~json
{"active":1,"closed":0,"cancelled":0}
~~~

Sau khi cancel đã được server quan sát và cleanup xong, có thể thấy:

~~~json
{"active":0,"closed":1,"cancelled":1}
~~~

**Chỉ là output ví dụ cho một JVM mới chạy với một subscriber**. Nếu có nhiều clients, reconnect, HTTP tests hoặc timing khác nhau, counters khác. Browser/client disconnect không luôn làm chúng đổi ngay lập tức.

### 19.3. SSE hữu hạn

~~~bash
curl -N -i 'http://localhost:8080/api/finite'
~~~

Dự kiến HTTP 200, `Content-Type: text/event-stream` và hai events, rồi response kết thúc. Cách này giúp phân biệt một stream `ON_COMPLETE` có thật với một stream bị cancel.

### 19.4. Test trình duyệt

~~~javascript
const events = new EventSource("http://localhost:8080/api/stream");
events.addEventListener("tick", e => console.log(e.lastEventId, e.data));

// Sau đó:
events.close();
~~~

Nếu frontend nằm ở **origin khác**, phải cấu hình **CORS cho SSE GET** tương ứng, và xem chính sách cookies/auth nếu dùng credentials. Demo không tự bật CORS, nên ví dụ browser này dễ kiểm nhất từ trang cùng origin hoặc môi trường development có CORS được phép. Không thêm wildcard origin + credentials một cách tùy tiện.

## 20. Cleanup tài nguyên thực: file, subscription broker, connection, cursor

| Tài nguyên | Khi mở | Cleanup đúng | Cẩn trọng |
| --- | --- | --- | --- |
| Timer `Flux.interval` | Khi subscribe | Cancel upstream schedule | Không tự subscribe tạo timer orphan |
| Per-client message listener | Khi subscriber đăng ký | Unregister listener đúng ID/token | Không unregister shared listener toàn app |
| Broker topic subscription | Khi tạo streaming subscription | Cancel/close tương ứng | Shared consumer có owner riêng |
| Database R2DBC cursor/connection | Khi reactive query/transaction bắt đầu | Driver reactive close/rollback theo framework | Không giữ DB transaction vô hạn cho SSE |
| JDBC cursor (blocking) | Trong bounded worker | Close connection/result set + driver timeout | Cancel signal không chắc interrupt query |
| InputStream/FileChannel | Khi bắt đầu đọc file | Close channel on complete/error/cancel | Blocking close không chạy trên Netty loop |
| File upload temp file | Khi tạo staged file | Delete temp on error/cancel, atomic finalize on success | Tránh partial upload được công nhận thành công |
| DataBuffer/Netty ByteBuf | Khi cấp phát/nhận buffer | Release đúng owner, discard hook khi cần | Double release/use-after-release |
| WebClient upstream HTTP | Khi downstream subscribe | Cancel body publisher/network exchange | Remote side effects không rollback |
| Thread pool/Scheduler shared | Lúc bean start | Dispose ở application shutdown | Không shutdown pool global theo mỗi client |
| Hot source/Sink dùng chung | Lúc application setup | Close theo shared owner/ref count | Một client cancel không dừng mọi clients |

Không mở resource như singleton rồi đóng nó bằng `doFinally` trên **mọi** user stream: đó là nguồn của nhiều lỗi “người dùng A ngắt kết nối làm B mất dữ liệu”.

## 21. Shutdown và rolling deployment

### 21.1. Graceful shutdown không giống mọi stream đều complete

Khi deployment rolling restart:
- Server có thể ngừng nhận connections mới nhưng long-lived SSE còn đang hoạt động.
- Grace period của HTTP server, load balancer và orchestrator có thể giới hạn thời gian chờ.
- Nếu grace period hết, connection vẫn có thể bị cắt và Reactor stream nhận cancellation/error tùy stack.
- Browser EventSource có thể tự reconnect đến pod mới.
- Nếu cần replay, event offsets phải lưu bên ngoài pod.

Với Spring Boot, cấu hình graceful shutdown và phase timeout theo phiên bản, rồi test thực tế với long-lived streams; đừng giả định server sẽ chờ vô hạn.

### 21.2. Không giữ mutable state per-client trong static map mãi mãi

Nếu quản lý subscription registry, phải unregister khi complete/error/cancel, giới hạn số entries, tránh giữ `ServerWebExchange`/credentials/body buffers lâu dài. Nếu process chết đột ngột, không có `doFinally` nào chạy được — phải dựa vào TTL/leases/recovery ở external resources.

### 21.3. Distributed cancellation

Trong hệ nhiều replicas:
- Mỗi HTTP connection thuộc một server instance.
- Redis/Kafka/DB cursor có thể nằm ngoài instance và cần ownership/lease semantics.
- Reconnect có thể đến replica khác.
- Cleanup của replica cũ có thể chậm hoặc không chạy khi node crash.
- Dùng TTL, durable offsets, idempotency và external lease expiration khi trạng thái sống lâu quan trọng.

## 22. Khả năng quan sát và metric đúng nghĩa

Nên đo:
- Active SSE subscriptions theo endpoint/feature (không gắn raw user ID làm high-cardinality label).
- Subscriptions completed/errored/cancelled theo signal, **không** gọi cancelled là "user closed tab".
- Duration và bytes/events delivered (định nghĩa rõ "sent" vs client acknowledged).
- Resource leases opened/closed, pending cleanup, cleanup error count.
- Upstream client/DB pool active/idle, cancellations, driver timeout rates.
- Netty HTTP connections, network write failures, proxy idle timeouts.
- Reconnect rate, Last-Event-ID replay lag và duplicate event handling.
- Scheduler queue/threads cho any blocking legacy cleanup.
- Unreleased DataBuffer/direct memory/leak detector logs trong staging.

Khi log, chỉ dùng safe trace/request IDs; không log Authorization, cookies, raw SSE personal payload hoặc entire client IP history trái policy. Reactor Context hữu ích để truyền trace metadata qua callbacks đổi thread; không assume ThreadLocal/MDC tự lan khắp reactive chain.

### 22.1. Phân biệt logical stream, HTTP connection và upstream source

Một logical user session có thể mở nhiều HTTP connections do reconnect. Một connection có thể liên quan tới một response publisher, trong khi nhiều publishers có thể dùng chung broker consumer. Metrics phải thể hiện đúng tầng thay vì đếm tất cả là "active users".

### 22.2. Cleanup metric không đủ để phát hiện memory leak

Counter `opened == closed` sau load test là chỉ báo tốt nhưng không bảo đảm pooled ByteBuf đã release, buffers trong queues đã giải phóng hoặc listener references không bị giữ. Kết hợp heap/native memory profiling, Netty leak detection, resource metrics, test cancellation và review code ownership.

## 23. Những lỗi phổ biến và cách sửa

| Triệu chứng | Nguyên nhân | Cách khắc phục |
| --- | --- | --- |
| Đóng tab nhưng broker listener vẫn tồn tại | Mở ngoài subscription và không unregister | `using`/`usingWhen` đúng per-subscriber owner |
| `doOnComplete` không chạy với SSE vô hạn | Stream chưa complete hoặc bị cancel | `doFinally`/resource operator |
| `doOnError` không chạy khi client disconnect | Write error nằm downstream, upstream nhận cancel | Theo dõi Netty/HTTP error và cleanup cancellation |
| Counter CANCEL tăng khi dùng `take(1)` | `take` cancel upstream sau đủ items | Đừng gọi đó là tab close |
| Stream mới mở khi reconnect, stream cũ chưa close | Network/proxy phát hiện trễ | Bounded idle timeout/heartbeat, TTL/leases |
| Một client disconnect làm mọi clients mất dữ liệu | Per-client cleanup đóng shared resource | Phân tách ownership/ref counting |
| Timeout rồi query JDBC vẫn chạy | Blocking driver không ngừng theo cancel | Driver timeout, bounded worker, explicit cancel if supported |
| `.block()` trong cleanup làm Netty lag | Blocking close trên event loop | `usingWhen`/non-blocking close hoặc scheduler phù hợp |
| Cleanup tạo publisher rồi bỏ qua | Gọi `closeAsync()` nhưng không compose | `usingWhen` hoặc return publisher quản lý đúng |
| `subscribe()` trong controller chạy mãi | Orphan subscription không thuộc response | Return publisher thay vì tự subscribe |
| Buffered upload sau cancel còn file rác | Không cleanup partial files | Staging + delete on error/cancel |
| Native memory tiếp tục tăng | DataBuffer retain/release không cân bằng | Ownership review, doOnDiscard, leak detector |
| 500 JSON không trả được giữa SSE | Response đã commit 200 | In-band error/reconnect/close policy |
| Unit test cleanup flaky | Assert quá sớm sau doFinally | Latch/await bounded, đừng Thread.sleep tùy ý |
| Client không phát hiện stream chết | Proxy giữ idle connection | SSE heartbeat và timeout phù hợp |
| Khi rolling restart stream bị cắt | Grace period/long-lived connections | Reconnect+replay, graceful shutdown policy |
| Duplicate event sau reconnect | Không dùng event ID/idempotency | Last-Event-ID/history và idempotent consumer |
| Event bị mất khi client đóng | onNext không là ACK | Durable event log/offset/ack protocol |
| Cache Flux vô hạn gây memory leak | `cache()` không bound | Bounded replay/refCount/retention |
| Spring MVC `SseEmitter` được dùng trong Netty WebFlux | Nhầm hai web stacks | `Flux<ServerSentEvent<T>>` của WebFlux |

### Quy trình debug

1. Xác minh `spring-boot-starter-webflux`, reactive runtime và đúng package imports.
2. Vẽ đường data: controller → publishers/operators → HTTP writer → Netty/socket.
3. Xác định resource được **mở ở lúc nào** và **ai là owner**.
4. Tìm `subscribe()` thủ công, `block()`, `Thread.sleep` hoặc resource eager construction.
5. Kiểm `doFinally` và `using/usingWhen` gắn vào đúng upstream boundary.
6. Xác định source là cold/per-client hay hot/shared, tránh close nhầm shared source.
7. Với disconnect thật, xem Netty logs/HTTP connection metrics/proxy idle timeout.
8. Kiểm buffer ownership, release/discard/cancel và partial file cleanup.
9. Test complete/error/cancel bằng StepVerifier; test HTTP SSE bằng WebTestClient.
10. Đặt bound cho test async cleanup; không ép assertions real disconnect ở mốc thời gian tùy tiện.
11. Load-test nhiều clients, reconnect storm và slow consumers.
12. Kiểm app shutdown/pod kill để thấy trường hợp callback cleanup không chạy và cần TTL.

## 24. Checklist production

- [ ] Mỗi per-client stream subscription có resource owner/lifetime rõ.
- [ ] Resource mở lazy khi subscribe, không mở eager rồi bỏ mặc.
- [ ] Dọn được complete/error/cancel qua `using/usingWhen`.
- [ ] Cleanup đồng bộ ngắn; close async không bị `subscribe()` rời rạc trong finally.
- [ ] Close idempotent để đối phó race/double-notification.
- [ ] Không gọi `block()` hoặc blocking JDBC trong Netty event loop.
- [ ] Driver/client timeout và cancellation semantics được hiểu đúng.
- [ ] Client disconnect được xem là best-effort signal, không hứa phát hiện tức thì.
- [ ] Có SSE heartbeat, reconnect policy và event IDs/replay khi cần.
- [ ] Side effects có idempotency; cancel không ngầm rollback committed transaction.
- [ ] Shared hot sources có owner/refCount riêng, không bị một client close toàn bộ.
- [ ] Raw DataBuffers release theo ownership; xử lý discard/cancel/partial file.
- [ ] Timeout/slow consumer/overload/backpressure có giới hạn.
- [ ] Status/headers sau commit không bị sửa như response chưa gửi.
- [ ] Test StepVerifier đủ complete/error/cancel, có timeout bounded.
- [ ] Test HTTP SSE và cancellation qua WebTestClient/client thực ở staging.
- [ ] Metrics tách ON_COMPLETE/ON_ERROR/CANCEL/HTTP disconnect và cleanup result.
- [ ] Graceful shutdown không thay durable replay/lease expiry.
- [ ] Logs/tracing không chứa tokens, cookies và raw personal SSE events.

### Câu hỏi tự kiểm tra

**1.** `cancel()` khác `onComplete()` như thế nào?

**Trả lời:** cancel là downstream yêu cầu ngừng một subscription; onComplete là tín hiệu nguồn phát kết thúc thành công. Cancel không phải một onComplete truyền đến subscriber.

**2.** `doOnError` có bắt mọi client disconnect không?

**Trả lời:** Không. Lỗi ghi socket có thể nằm downstream, upstream chỉ nhận cancel, hoặc disconnect chưa được phát hiện ngay.

**3.** Muốn cleanup cả complete, error, cancel nên dùng gì?

**Trả lời:** `doFinally` khi cần callback/metric; `Flux.using` cho tài nguyên đóng đồng bộ; `Flux.usingWhen` nếu cleanup là publisher async.

**4.** Vì sao `take(2)` có thể khiến upstream `doFinally(CANCEL)` chạy?

**Trả lời:** Sau khi nhận đủ hai items, take có thể cancel upstream dù downstream subscriber nhận complete bình thường.

**5.** Client ngắt kết nối có chắc rollback DB update vừa commit không?

**Trả lời:** Không. Transport cancel không phải business transaction rollback; cần transaction boundaries/idempotency/compensation cụ thể.

**6.** Nếu một shared sink có 50 SSE clients, client đầu tiên cancel có nên close sink không?

**Trả lời:** Không, trừ khi shared owner/refCount policy quyết định resource không còn ai sử dụng. Per-client cleanup chỉ dọn subscription riêng.

**7.** Vì sao `Flux.usingWhen` phù hợp hơn `doFinally(() -> closeAsync().subscribe())`?

**Trả lời:** usingWhen gắn publisher cleanup vào lifecycle của resource, xử lý complete/error/cancel một cách có cấu trúc; subscribe trong callback tách khỏi flow và khó theo dõi lỗi.

**8.** Raw `DataBuffer` bị release trong `doOnNext` rồi truyền vào `writeWith` có an toàn không?

**Trả lời:** Không. Writer có thể đọc buffer đã release; phải giữ ownership rõ, chỉ release đúng nơi kết thúc sử dụng.

**9.** SSE `id` có bảo đảm exactly-once delivery không?

**Trả lời:** Không. Cần replay store, offsets/ack và idempotent consumer tùy yêu cầu reliability.

**10.** StepVerifier cancel thành công có chứng minh Netty báo client close ngay lập tức không?

**Trả lời:** Không. Nó chỉ test Reactor cancellation semantics; transport/proxy disconnect detection phải test riêng bằng HTTP client và staging network.

## 25. Tài liệu tham khảo chính thức

- [Spring Framework — WebFlux Annotated Return Values](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/controller/ann-methods/return-types.html): `Flux<ServerSentEvent>` và HTTP streaming.
- [Spring Framework — WebFlux Functional Endpoints](https://docs.spring.io/spring-framework/reference/6.2/web/webflux-functional.html): functional handlers/router và reactive response streaming.
- [Spring Framework — WebFlux Overview](https://docs.spring.io/spring-framework/reference/web/webflux.html): Reactive Streams server infrastructure và non-blocking I/O.
- [Spring Framework — Data Buffers and Codecs](https://docs.spring.io/spring-framework/reference/core/databuffer-codec.html): pooled buffers, ownership/release, codecs.
- [Spring Framework — DataBufferUtils Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/core/io/buffer/DataBufferUtils.html): `retain`, `release`, `join`, `write` utilities.
- [Project Reactor 3.6 — Handling Errors / Using Resources and Finally](https://projectreactor.io/docs/core/3.6.11/reference/index.html#_using_resources_and_the_finally_block): `using`/`doFinally`.
- [Project Reactor — Reactor Core API: Flux](https://projectreactor.io/docs/core/3.6.11/api/reactor/core/publisher/Flux.html): `using`, `usingWhen`, `doOnCancel`, `doFinally`.
- [Project Reactor — Testing with StepVerifier](https://projectreactor.io/docs/core/3.6.11/reference/index.html#testing): `thenCancel` và timeout.
- [Project Reactor — Hot Versus Cold](https://projectreactor.io/docs/core/3.6.11/reference/index.html#reactor.hotCold): `share`, `refCount`, per-subscription behavior.
- [Spring Framework — WebTestClient](https://docs.spring.io/spring-framework/reference/testing/webtestclient.html): reactive HTTP tests.
- [Spring Framework — ServerSentEvent Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/http/codec/ServerSentEvent.html): `event`, `data`, `id`, `retry`.
- [Reactive Streams Specification](https://www.reactive-streams.org/): `Subscription.request(n)` và `Subscription.cancel()`.
- [MDN — Using Server-Sent Events](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events): `EventSource`, event stream format, reconnect và browser lifecycle.

**Ghi nhớ:** Khi HTTP streaming client dừng quan tâm, downstream **có thể** hủy reactive subscription và yêu cầu upstream ngừng phát. Nhưng `cancel` không đồng nhất với disconnect ngay lập tức, không phải transaction rollback và không tự bảo đảm close mọi tài nguyên. Hãy mở resource theo subscription, dùng `using`/`usingWhen` để cleanup đủ complete/error/cancel, hiểu Buffer ownership, quản lý heartbeat/reconnect và kiểm transport bằng tests riêng. Với shared sources, tách lifetime per-client khỏi lifetime application.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-streaming-007">Truyền ngữ cảnh qua Reactor Context thay cho giả định về ThreadLocal</a>

<details>
<summary>Xem chi tiết</summary>

# Truyền ngữ cảnh qua Reactor Context thay cho giả định về ThreadLocal trong Streaming WebFlux

## 1. Bài toán: một request đi qua nhiều thread nhưng phải giữ đúng ngữ cảnh

Trong Spring MVC đồng bộ, một request có thể được xử lý liên tục trên một Servlet worker thread. Vì vậy mã cũ thường lưu correlation ID, người dùng hiện tại, locale hoặc MDC vào `ThreadLocal` rồi đọc lại ở mọi phương thức trên cùng thread. **Spring WebFlux không cam kết một request chỉ chạy trên một Java thread**. HTTP callback của Reactor Netty, toán tử `publishOn`, `subscribeOn`, `flatMap` với nguồn bất đồng bộ, `Mono.delay`, WebClient và reactive data driver có thể chạy tiếp ở các execution contexts khác nhau.

Tình huống thực tế:

```text
Client GET /api/events X-Request-Id: req-A
    │
    ▼
reactor-http-nio-2           tiếp nhận HTTP, tạo chuỗi Mono/Flux
    │
    ├── WebClient gửi remote request không block
    └── Mono.delay(...)      đợi timer mà không chiếm worker thread
                 │
                 ▼
parallel-1                  dữ liệu đến, pipeline tiếp tục
                 │
                 ├── đọc ThreadLocal: không bảo đảm thấy req-A
                 └── đọc Reactor Context: có thể thấy req-A nếu
                      toàn chuỗi có contextWrite đúng vị trí
                 │
                 ▼
reactor-http-nio-3          ghi thêm HTTP/SSE body
```

Tên thread chỉ minh họa. Không dựa vào tên/số lượng event-loop threads để thiết kế chức năng. Với **Reactor Context**, request metadata đi cùng chuỗi tín hiệu thông qua **subscription**, không bị phụ thuộc một thread nhất định. Điều này đặc biệt quan trọng trong bài **Streaming WebFlux** khi một Flux có thể sống lâu, trải qua các scheduler, nhiều callback và bị client hủy.

### 1.1. Mục tiêu học tập

Sau bài này bạn có thể:

1. Phân biệt `ThreadLocal` gắn với Java thread và `Context` gắn với từng **Reactor Subscriber/subscription**.
2. Dùng `contextWrite`, `Mono.deferContextual`, `Flux.deferContextual`, `Context` và `ContextView` theo đúng hướng propagation.
3. Giải thích vì sao `contextWrite` đặt cuối source code thường được thực thi **trước khi upstream phát onNext**, và tại sao đổi thứ tự operators làm thay đổi giá trị đọc được.
4. Kiểm soát phạm vi ghi đè context trong `flatMap`, `concatMap` và nested publishers, tránh rò rỉ context giữa các requests/tenants.
5. Đưa correlation ID từ WebFlux `WebFilter` vào Reactor Context và đọc trong service, kể cả sau `Mono.delay` và `subscribeOn`.
6. Hiểu việc đọc Reactor Context trong SSE/Flux: context cần cho từng event, cancellation và cả error path.
7. Phân biệt native Reactor Context với Micrometer Context Propagation/MDC/ThreadLocal bridge ở chế độ default và automatic.
8. Không coi một header do client tự khai là authentication, không đưa secrets vào logs hoặc context dùng chung.
9. Tạo dự án Spring Boot 3/Java 17 chạy được, có WebFilter, service, controller, WebTestClient và StepVerifier tests.
10. Debug các trường hợp “context missing”, request ID lẫn nhau, callback chạy ngoài chuỗi và reactive security principal không tồn tại.

**Phạm vi:** Spring Framework 6.x, Project Reactor 3.6.x / Spring Boot 3.3.x, Java 17+. Bài học không khẳng định mọi ThreadLocal đều biến mất trong WebFlux: có các cơ chế tự động khôi phục được cấu hình, nhưng chúng **không thay đổi nguyên tắc lõi** rằng một reactive pipeline không sở hữu một thread cố định.

## 2. Tư duy đúng về ThreadLocal và Context

### 2.1. ThreadLocal: giá trị nằm trong ngữ cảnh của thread đang chạy

Ví dụ imperative:

```java
private static final ThreadLocal<String> REQUEST_ID =
        new ThreadLocal<>();

static void handleRequest() {
    REQUEST_ID.set("req-123");
    try {
        callBusinessFunction(); // nếu cùng thread, get() thấy req-123
    } finally {
        REQUEST_ID.remove(); // cực kỳ quan trọng với thread pool
    }
}
```

`ThreadLocal.get()` truy cập trạng thái của **current Java Thread**. Nó không đi cùng một logical request khi công việc được chuyển sang executor/scheduler khác. Với thread pool reused, quên `remove()` còn có thể làm dữ liệu cũ rò rỉ sang một request khác.

Trong MVC đồng bộ, pattern set→get→remove có thể hợp lý nếu code và framework quản lý scope rõ. Trong WebFlux, dù một callback được gọi lại “trên cùng thread” tình cờ, đó **không phải contract** mà business code được dựa vào.

### 2.2. Reactor Context: giá trị nằm theo Subscriber

`reactor.util.context.Context` là một key-value container **immutable (mỗi mutation tạo Context mới)** liên kết với một subscription. Operators upstream có thể truy cập ngữ cảnh được downstream `Subscriber` cung cấp.

Các đặc điểm:

- Key/value không được là `null`; context không phải Map mutable chung.
- Mỗi thao tác `put`/`delete` tạo một bản ngữ cảnh logic mới.
- Có thể dùng String key, class/object key hoặc key chuyên biệt; nên namespace để tránh collisions.
- `ContextView` chỉ cung cấp đọc: `get`, `getOrDefault`, `hasKey`, `stream`.
- `Context` cung cấp thao tác tạo bản mới: `put`, `delete`, `putAll`.
- Dữ liệu truy cập được tại **subscription time / trong reactive chain**, không phải ở mọi đoạn synchronous code chạy ngoài reactive chain.
- Không bảo đảm truyền vào bất kỳ `ExecutorService`, `CompletableFuture`, external callbacks hoặc library khác chỉ vì bạn return Mono ở tầng ngoài.

**Khác nhau về nơi gắn state:**

| Chủ đề | ThreadLocal | Reactor Context |
| --- | --- | --- |
| Thuộc về | Java Thread đang chạy | Subscriber/subscription |
| Cross-thread | Không tự chuyển | Có thể truy cập qua Reactor operators khi context propagate |
| Lifecycle | Thread và explicit cleanup | Subscription/reactive chain |
| Mutability | ThreadLocal.set thay giá trị | Context bất biến, trả bản mới |
| Isolation giữa requests | Phụ thuộc set/remove và thread reuse | Theo subscription/context boundary |
| Cách đọc | `THREAD_LOCAL.get()` | `Mono.deferContextual(ctx -> ...)` |
| Cách ghi | `THREAD_LOCAL.set(x)` | `publisher.contextWrite(ctx -> ctx.put(key,x))` |
| Khả năng dùng với non-Reactor callback | Trực tiếp nếu cùng thread | Cần bridge/contextSnapshot/explicit arguments |
| Rủi ro | Leak, stale value, lost propagation | Sai thứ tự operator, context bị mất ngoài chain, key collision |

Không coi Reactor Context như một “global map” để lấy ở bất kỳ function nào. Nó được định hướng **subscriber context**, nên đọc đúng thông qua Reactor APIs.

### 2.3. Phân biệt ba đối tượng

- `Context`: interface đọc/ghi *bất biến về mặt semantic*; các methods `put` tạo instance mới.
- `ContextView`: interface **read-only**; loại được `deferContextual` truyền cho lambda.
- `ContextSnapshot` của Micrometer: **khác** Reactor Context, dùng để capture/restore các context mechanisms như ThreadLocal từ/đến registered accessors.

Ví dụ:

```java
Context original = Context.of("traceId", "A");
Context updated = original.put("tenantHint", "tenant-1");

System.out.println(original.hasKey("tenantHint")); // false
System.out.println(updated.hasKey("tenantHint"));  // true
```

Đây là snippet với `reactor.util.context.Context` import; trong dự án Java có Reactor dependency thì stdout của hai dòng là `false` và `true`. Lưu ý `tenantHint` chỉ là dữ liệu minh họa, **không** dùng một tenant ID do client tự khai làm bằng chứng authorization.

## 3. Cách ghi context: contextWrite

`contextWrite` là API thường dùng để làm phong phú ngữ cảnh của các operators **upstream**.

```java
Mono<String> pipeline =
    Mono.deferContextual(ctx ->
        Mono.just("trace=" + ctx.get("traceId")))
        .contextWrite(Context.of("traceId", "req-A"));
```

Khi `pipeline` được subscribe, giá trị kết quả là:

```text
trace=req-A
```

`contextWrite(ContextView)` gộp context mới với downstream context; `contextWrite(Function<Context,Context>)` cho phép tạo context bằng logic merge/replace.

```java
Mono<String> result =
    Mono.deferContextual(ctx ->
        Mono.just(ctx.getOrDefault("traceId", "absent")))
      .contextWrite(ctx -> ctx.put("traceId", "req-A"));
```

Nên giữ context value nhỏ, immutable hoặc thread-safe. Không nhét nguyên HTTP request body, large lists, EntityManager, mutable Servlet request, unbounded buffers hoặc token/secret thừa vào Context.

### 3.1. Vì sao contextWrite ở cuối mã nguồn lại có hiệu lực bên trên?

Một pipeline thường được đọc theo **assembly order** từ trên xuống, nhưng subscription đi ngược chiều:

```text
Source / Mono.deferContextual          ── tạo giá trị
       ↑ context nhìn thấy từ downstream
.map(...)                               ── xử lý item
       ↑
.contextWrite(key=value)                ── ghi context cho upstream
       ↑ subscription tiến từ dưới lên
Subscriber / WebFlux HTTP writer
```

Vì vậy code:

```java
Mono.deferContextual(ctx -> Mono.just(ctx.get("k")))
    .contextWrite(Context.of("k", "v"));
```

có thể đọc `v`. Nhưng chuyển `contextWrite` **lên trước** một `flatMap` đọc context phía dưới nó có thể làm flatMap đó không nhìn thấy giá trị.

### 3.2. Ví dụ sai vị trí operator

```java
Mono<String> wrong = Mono.just("value")
    .contextWrite(ctx -> ctx.put("k", "inside"))
    .flatMap(x -> Mono.deferContextual(ctx ->
        Mono.just(ctx.getOrDefault("k", "missing"))));
```

Kết quả mong đợi: **missing**. Vì `flatMap` nằm downstream của `contextWrite`; context write đó chỉ tác động upstream.

Sửa:

```java
Mono<String> right = Mono.just("value")
    .flatMap(x -> Mono.deferContextual(ctx ->
        Mono.just(ctx.getOrDefault("k", "missing"))))
    .contextWrite(ctx -> ctx.put("k", "inside"));
```

Kết quả: **inside**. Hai đoạn trên là mã có thể đặt trong method Java có Reactor imports, kết quả xác minh bằng StepVerifier (mục 14).

### 3.3. Ghi đè cùng key: vị trí quyết định giá trị

```java
Mono<String> result =
    Mono.deferContextual(ctx ->
        Mono.just(ctx.get("language")))
       .contextWrite(ctx -> ctx.put("language", "vi"))
       .contextWrite(ctx -> ctx.put("language", "en"));
```

Kết quả: **vi**. Hai operators ghi cùng key; đối với source, `contextWrite(vi)` gần source hơn và override `en`. Giá trị `en` có thể vẫn được thấy bởi operators ở giữa/downstream, tùy phạm vi.

Không thiết kế security bằng việc “ghi đè role/tenant” ở một inner publisher chưa được kiểm tra. Context không phải auth barrier; authoritative user/tenant phải đến từ Spring Security/context đáng tin.

### 3.4. Chỉ một subscriber có một context view cụ thể

Một publisher cold có thể được subscribe nhiều lần với contexts riêng:

```java
Mono<String> source =
    Mono.deferContextual(ctx ->
        Mono.just(ctx.getOrDefault("requestId", "missing")));

Mono<String> a =
    source.contextWrite(Context.of("requestId", "req-A"));
Mono<String> b =
    source.contextWrite(Context.of("requestId", "req-B"));
```

`a` cho req-A, `b` cho req-B. Không cần lưu requestId trong static field hay mutate một shared Context.

## 4. Đọc context đúng cách: deferContextual và ContextView

`Mono.deferContextual` tạo inner Mono dựa trên `ContextView` của subscription hiện tại. `Flux.deferContextual` cũng có cơ chế tương tự cho Flux.

```java
Mono<String> currentRequestId() {
    return Mono.deferContextual(ctx ->
        Mono.just(ctx.getOrDefault("requestId", "anonymous")));
}
```

Không gọi `Mono.just(Context.of(...).get(...))` để lấy context hiện tại; bạn vừa tự tạo map mới không liên quan subscriber. Cũng không có hàm static phổ quát `ReactorContext.current()` hoạt động tùy nơi như `ThreadLocal.get()`.

### 4.1. Đọc trong một operator asynchronous

```java
Mono<String> result =
    Mono.delay(Duration.ofMillis(10))
        .flatMap(tick ->
            Mono.deferContextual(ctx ->
                Mono.just("trace=" +
                    ctx.getOrDefault("traceId", "none"))))
        .contextWrite(Context.of("traceId", "req-42"));
```

Sau timer chạy, inner `Mono.deferContextual` vẫn nhận context của subscription. Kết quả là `trace=req-42`. Không test bằng tên thread như “parallel-1” vì scheduler pool size/name có thể thay đổi.

### 4.2. Khi nào get, getOrDefault, hasKey?

- `ctx.get(key)`: yêu cầu key phải tồn tại; nếu không, thường ném `NoSuchElementException`.
- `ctx.getOrDefault(key, fallback)`: phù hợp với metadata tùy chọn như correlation ID.
- `ctx.hasKey(key)`: kiểm tra sự hiện diện.
- `ctx.getOrEmpty(key)`: cách lấy optional value khi phù hợp API.
- `ctx.stream()`: chỉ dành cho diagnostics cực kỳ kiểm soát; không dump toàn context ra logs vì có thể chứa auth/tracing values.

Với bắt buộc security principal, **không dùng fallback “admin”** khi thiếu key. Context missing có thể là lỗi authorization hoặc bug propagation; xử lý fail-closed.

### 4.3. Key String hay Object?

```java
public final class Keys {
    public static final String TRACE_ID = "example.trace-id";
    private Keys() {}
}
```

Chuỗi có namespace tránh va chạm khi dùng chung libraries. Nếu muốn type-safety và chống collisions tốt hơn, có thể dùng `private static final Object TRACE_KEY = new Object()` và dùng object đó làm key; nhưng phải chia sẻ chính **cùng object key** qua các class đọc/ghi.

**Không** tạo `new Object()` mỗi lần đọc/ghi, vì key identity không khớp. Dự án demo bên dưới dùng string constant để dễ quan sát/test.

## 5. Lợi ích trong Streaming WebFlux: nhiều onNext, một context

Một `Flux` SSE có thể phát ba sự kiện hoặc hoạt động nhiều phút. Context gắn với subscription của client; mỗi event có thể đọc cùng correlation ID.

```java
Flux<String> events =
    Flux.interval(Duration.ofMillis(100))
        .take(3)
        .concatMap(n ->
            Mono.deferContextual(ctx ->
                Mono.just(ctx.get("traceId") + ":" + n)))
        .contextWrite(Context.of("traceId", "stream-A"));
```

Khi complete bình thường, các items là:

```text
stream-A:0
stream-A:1
stream-A:2
```

Vì `Flux.interval` phát nhịp scheduler khác, `ThreadLocal.get()` không bảo đảm cùng giá trị như lúc HTTP request bắt đầu. Nhưng `deferContextual` phía inner publisher được compose trong chain thấy context của subscriber.

### 5.1. Một client không được đọc nhầm request ID của client khác

Giả sử A gửi `X-Request-Id: A` và B gửi `X-Request-Id: B`. Nếu dùng static mutable field:

```java
// ANTI-PATTERN — request A/B có thể ghi đè nhau.
static String currentRequestId;
```

thì events của A có thể hiển thị B. Với Reactor Context, filter thiết lập key **cho từng subscriber**, khi các signals xen kẽ nhau context vẫn tách biệt theo subscription.

**Tuy nhiên:** một publisher hot/shared có thể cần thiết kế scope riêng. Nếu tạo một `Flux.share()` ở singleton service với context từ request đầu tiên, không suy rằng context của các subscribers sau sẽ tự được áp chính xác cho upstream chung. Khi mỗi người cần own trace/tenant, đọc context ở **downstream branch** riêng của từng subscriber hoặc explicitly pass immutable metadata sau authentication; không share một upstream chứa user-specific context mà chưa hiểu multicast/replay semantics.

### 5.2. SSE và cancellation

Client ngắt SSE có thể khiến subscription bị **cancel**. `doFinally` nhận `SignalType.CANCEL` và phù hợp cho cleanup/metrics; `doOnComplete` không chạy khi cancel.

```java
Flux<String> stream = service.events()
    .doFinally(signalType -> {
        // COMPLETE, ERROR hoặc CANCEL.
        // Không block event-loop; tránh log context nhạy cảm.
    });
```

Đây là snippet với `service` giả định, không phải một file chạy độc lập. Nếu cần log request ID ở `doFinally`, có thể capture **immutable request ID đã được đọc từ `deferContextual`** rồi tạo inner chain chứa cleanup. Không kỳ vọng callback bất kỳ luôn có access trực tiếp tới context hiện tại.

### 5.3. Response đã bắt đầu và lỗi tiếp theo

Một SSE response đã commit status 200 và phát events thì không thể đảm bảo chuyển thành HTTP 500 nếu upstream lỗi giữa chừng. Cần protocol-level error event hoặc kết thúc stream/retry policy. Context propagation hỗ trợ tracing/diagnostics, **không thay** HTTP semantics.

### 5.4. Backpressure không phụ thuộc ThreadLocal

Reactive Streams demand/cancellation thuộc subscription. Reactor Context truyền metadata theo subscription nhưng **không làm downstream nhanh hơn**, không tự giới hạn bộ nhớ của `onBackpressureBuffer` và không bảo đảm remote producer tuân thủ demand. Thiết kế buffering/batching/rate limits riêng.

## 6. Context trong flatMap, concatMap, publishOn và subscribeOn

### 6.1. Scheduler boundary

```java
Mono<String> pipeline = Mono.just("start")
    .publishOn(Schedulers.parallel())
    .flatMap(value ->
        Mono.deferContextual(ctx ->
            Mono.just(value + "-" + ctx.get("traceId"))))
    .contextWrite(Context.of("traceId", "req-123"));
```

Kết quả: `start-req-123`. `publishOn` chuyển nơi chạy một số downstream signals, còn Reactor Context gắn subscriber nên vẫn đi theo chain.

### 6.2. Inner publisher scoped context

```java
Mono<String> result = Mono.just("hello")
    .flatMap(word ->
        Mono.deferContextual(ctx ->
            Mono.just(word + "-" + ctx.get("k")))
        .contextWrite(Context.of("k", "inner")))
    .flatMap(value ->
        Mono.deferContextual(ctx ->
            Mono.just(value + "-" + ctx.get("k"))))
    .contextWrite(Context.of("k", "outer"));
```

Kết quả: `hello-inner-outer`. Context `inner` áp vào inner publisher của flatMap thứ nhất, không ghi đè toàn main sequence downstream. Những chain phức tạp nên kiểm tra bằng StepVerifier trước khi dựa vào scope cho audit/security.

### 6.3. Không viết Context rồi kỳ vọng đọc từ một sibling branch

Hai inner publishers sinh độc lập dưới `flatMap` có thể có context overrides cục bộ khác nhau. Một `contextWrite` trên branch A không tự sửa context của branch B. Muốn chia sẻ metadata đúng, đặt context ở downstream của cả hai hoặc pass một immutable value qua arguments.

### 6.4. concatMap vs flatMap

`flatMap` có thể interleave signals khi inner publishers async; `concatMap` tuần tự hóa inner streams theo order. **Cả hai có thể giữ Reactor Context** khi được compose đúng. Chọn operator dựa trên ordering/concurrency, không dùng `concatMap` chỉ để “fix” ThreadLocal propagation.

### 6.5. publishOn không biến source blocking thành non-blocking

```java
Mono<String> bad =
    Mono.just(legacyJdbc.read()) // blocking chạy ngay!
        .publishOn(Schedulers.boundedElastic());
```

Câu lệnh `legacyJdbc.read()` chạy **trước khi Mono được tạo**. Nếu bắt buộc bridge legacy:

```java
Mono<String> bridged =
    Mono.fromCallable(() -> legacyJdbc.read())
        .subscribeOn(Schedulers.boundedElastic());
```

Đây là snippets với dependency giả định. BoundedElastic không biến JDBC thành reactive DB driver; nguồn blocking cần timeout, pool/concurrency limits. Context có thể tiếp tục được đọc bằng `deferContextual` khi hoàn tất, nhưng `ThreadLocal` của JDBC/custom logger không tự có request metadata trừ khi bridge đúng.

## 7. Những điều Reactor Context KHÔNG tự làm

1. **Không tự tạo authentication**: request ID/tenant header nằm trong context không biến nó thành trusted principal.
2. **Không bảo đảm ThreadLocal của mọi framework**: cần context propagation integration/registered accessors nếu vẫn muốn dùng ThreadLocal.
3. **Không tự truyền sang arbitrary ExecutorService**: nếu tách khỏi Publisher chain, phải truyền immutable value hoặc ContextSnapshot/context-aware wrapper.
4. **Không tự tạo distributed tracing**: trace ID nội bộ không tự propagates qua HTTP headers sang remote service; sử dụng tracing instrumentation theo W3C Trace Context khi cần.
5. **Không tự quản lý HttpSession**: Context thường theo subscription, không phải persistent session store.
6. **Không phải cache**: `Context.put` không làm data source được cache hoặc share giữa requests.
7. **Không tự cleanup các side effects**: cần cancellation-aware lifecycle, reactor resource management.
8. **Không mang lại backpressure** cho callback source không hỗ trợ demand.
9. **Không thay được security context**: Spring Security reactive APIs đã có conventions.
10. **Không bảo đảm data visibility bên ngoài Reactor operators**: plain callback/thread pool có riêng lifecycle.

### 7.1. Bẫy “tự subscribe()” trong handler

```java
Mono<Void> writeAudit() {
    return Mono.deferContextual(ctx -> auditService.save(
        ctx.get("requestId")));
}

Mono<Response> badHandler() {
    writeAudit().subscribe(); // tách subscription khỏi HTTP chain
    return service.load().map(this::toResponse);
}
```

Đây là pseudocode minh họa; không phải class standalone. Subscription mới từ `subscribe()` có thể có **Context rỗng**, xử lý errors ngoài HTTP và gây side effect không được hủy theo request. Hướng đúng là compose audit vào return publisher:

```java
Mono<Response> goodHandler() {
    return writeAudit()
        .then(service.load())
        .map(this::toResponse);
}
```

Trong real app, audit đảm bảo exactly-once/durable cần cơ chế transaction/outbox/event log, không phải chỉ `then`. Điểm của ví dụ là **giữ chuỗi subscription**, không manual subscribe.

### 7.2. Completeness: error và empty

Nếu `Mono.deferContextual(ctx -> Mono.just(ctx.get("key")))` thiếu key, chain phát error. Nếu metadata optional, dùng getOrDefault rõ ràng. Đừng dùng `onErrorResume(any -> Mono.empty())` để nuốt errors do DB hoặc thiếu security context; nó có thể biến failure thành 200 body rỗng/404 tùy web layer.

## 8. ThreadLocal với logging MDC: không được giả định và cũng không phải lúc nào phải viết lại hết

Logback/SLF4J MDC phổ biến dùng ThreadLocal. Code synchronous:

```java
MDC.put("requestId", "req-1");
log.info("processing...");
MDC.remove("requestId");
```

đúng trong cùng thread và scope được dọn sạch. Nhưng nếu Mono chạy một phần trên reactor-http-nio, phần khác trên parallel, MDC không tự đi theo request trong chế độ Reactor chưa có bridge. Mặc dù log message có thể vẫn hiện correlation ID ở một số callbacks, **đừng suy ra nó luôn đúng**.

### 8.1. Một cách thủ công có kiểm soát

Đối với một đoạn logging synchronous ngắn, có thể đọc ContextView rồi set/restore MDC trong phạm vi callback:

```java
Mono<Void> logWithContext() {
    return Mono.deferContextual(ctx -> {
        String requestId =
            ctx.getOrDefault("requestId", "missing");

        String previous = MDC.get("requestId");
        try {
            MDC.put("requestId", requestId);
            log.info("Processing with trace context");
        } finally {
            if (previous == null) MDC.remove("requestId");
            else MDC.put("requestId", previous);
        }
        return Mono.empty();
    });
}
```

Đây là snippet minh họa, cần logger và MDC imports; không runnable riêng. Phạm vi set/restore **chỉ bao quanh log call đồng bộ**; tuyệt đối không set MDC rồi trả một Mono asynchronous và mong ThreadLocal tồn tại cho toàn subscription. Với nhiều logging sites, dùng framework-integrated Micrometer tracing/observability thay vì tự set hàng loạt.

### 8.2. Context propagation bridge: hai chế độ

Từ Reactor 3.5, Reactor Core hỗ trợ tích hợp SPI `io.micrometer:context-propagation`:

- **Default/limited mode**: chỉ một số operators như `handle`/`tap` phục hồi registered ThreadLocal state trong phạm vi nhất định. Không cam kết mọi `map`/`flatMap` callback nhìn được MDC.
- **Automatic mode**: khi bật `Hooks.enableAutomaticContextPropagation()`, Reactor cố gắng khôi phục những ThreadLocal đã được đăng ký thông qua `ThreadLocalAccessor` trên nhiều operator callbacks. Bật từ lúc startup vì chế độ này áp dụng cho **subscriptions mới**.
- `contextCapture()`: snapshot giá trị của **registered** ThreadLocal(s) ở subscription time và đưa chúng vào Reactor Context để upstream có thể nhìn thấy.
- `contextWrite(...)`: trực tiếp đặt key/value trong Reactor Context; không đồng nghĩa đã tạo ThreadLocal restoration.
- Automatic mode có overhead và cleanup semantics; key vắng mặt có thể là tín hiệu clear ThreadLocal tránh leak.

**Điều kiện rất quan trọng:** chỉ bật hook **không đủ** để một arbitrary `new ThreadLocal<>()` được propagates. Cần dependency context-propagation và `ThreadLocalAccessor` được đăng ký theo library hoặc code ứng dụng.

### 8.3. Spring Boot property trên các phiên bản hỗ trợ

Trong Spring Boot 3.3, có thể cấu hình:

```properties
spring.reactor.context-propagation=auto
```

Property này dùng cơ chế automatic context propagation của Reactor; không làm mất yêu cầu registered accessors hoặc biến Reactor Context thành implicit user identity. Tránh vừa bật hook thủ công ở nhiều nơi vừa cấu hình property mà không có lý do. Production cần benchmark và kiểm tra context lifecycle.

Nếu ứng dụng chỉ cần request correlation ID trong business/service code, **đọc Context trực tiếp** bằng `deferContextual` thường rõ ràng hơn dependency vào ThreadLocal restoration. Khi cần tương thích OpenTelemetry/Micrometer/SLF4J, dùng integration chuẩn và đối chiếu log traces trong test.

### 8.4. Bảng so sánh kết quả kỳ vọng

| Điều kiện | Reactor Context `deferContextual` | Arbitrary ThreadLocal.get() |
| --- | --- | --- |
| Một Reactor pipeline bình thường có contextWrite | Đọc được key hợp lệ | Không tự đảm bảo |
| Chuyển thread bằng publishOn | Vẫn đọc context qua operators | Không tự đảm bảo |
| Default Micrometer context propagation | Vẫn đọc context | Khôi phục ở các operators hỗ trợ cho registered accessor |
| Automatic propagation với registered accessor | Vẫn đọc context | Có thể restore theo contract/library |
| Automatic mode nhưng ThreadLocal chưa đăng ký | Vẫn đọc context | Không tự đảm bảo |
| Tách công việc bằng manual subscribe | Có thể mất parent context | Không tự đảm bảo |
| Tách sang raw ExecutorService | Cần explicit propagation | Cần explicit propagation |
| Một hot source share nhiều subscribers | Phải thiết kế per-subscriber scope | Có nguy cơ lẫn nếu đặt sai |

## 9. Spring Security: SecurityContext nằm ở đâu trong WebFlux?

Spring Security reactive sử dụng các reactive contexts và `SecurityWebFilterChain`, không dựa vào một Servlet `SecurityContextHolder` ThreadLocal luôn hiện diện trên mọi scheduler. Spring Security cung cấp `ReactiveSecurityContextHolder`:

```java
Mono<String> currentUser() {
    return ReactiveSecurityContextHolder.getContext()
        .map(security -> security.getAuthentication())
        .filter(auth -> auth != null && auth.isAuthenticated())
        .map(auth -> auth.getName());
}
```

Đây là snippet yêu cầu `spring-boot-starter-security`, imports và authentication config thích hợp. **Không** tự tạo SecurityContext từ request header `X-User-Role` rồi đặt vào Reactor Context như một cách “đăng nhập”.

### 9.1. Ngữ cảnh Request ID khác ngữ cảnh phân quyền

- `X-Request-Id` từ client: có thể dùng để correlate logs, **không** xác thực identity.
- Tracing context: có thể đến từ W3C `traceparent` được kiểm soát theo ingress/tracing policy.
- Principal: được Spring Security tạo từ trusted authentication method.
- Tenant ID: phải được xác định từ principal/session/authorization, có thể so với route parameter hoặc header đã validate theo policy.
- Locale/feature flag: có thể là metadata nhưng cần nguồn đáng tin nếu ảnh hưởng kết quả nhạy cảm.

Không log tokens/credentials và không lưu cả principal object mutable trong singleton Map rồi suy đó là safe context for all requests.

## 10. WebClient, R2DBC và ranh giới giữa các subscriptions

### 10.1. WebClient compose trong cùng chain

```java
Mono<String> result = Mono.deferContextual(ctx -> {
    String correlation = ctx.get("requestId");

    return webClient.get()
        .uri("/remote/items")
        .header("X-Request-Id", correlation)
        .retrieve()
        .bodyToMono(String.class);
});
```

Đây là snippet có WebClient đã inject và URL remote giả định, không phải class hoàn chỉnh. Ở đây Reactor Context **được đọc tường minh**, sau đó ID được đặt thành outbound HTTP header. Reactor Context **không tự gửi qua mạng** nếu client không có instrumentation hoặc application logic phù hợp.

**Không truyền security credential** lấy từ context thành arbitrary outbound header; cần OAuth/OIDC, token exchange hoặc trust delegation đúng policy.

### 10.2. R2DBC reactive repository

```java
Mono<Note> selected =
    repository.findById(id)
       .flatMap(note ->
           Mono.deferContextual(ctx -> {
               audit.record(ctx.get("requestId"), note.id());
               return Mono.just(note);
           }));
```

Snippet minh họa, `audit.record` nếu blocking phải được thay bằng reactive publisher/bridge phù hợp; nếu chỉ gọi sync rồi return Mono, bạn vẫn block caller thread. Chính việc chuỗi vẫn nằm trong Reactor composition giữ context; dùng `block()` hoặc manual subscribe cắt đứt ngữ nghĩa.

### 10.3. Transaction context khác request ID

Reactive transaction management sử dụng subscriber Context để nối transaction resources qua operators, thay vì chỉ ThreadLocal của imperative `@Transactional`. Nhưng tự đặt key `transactionId` vào Reactor Context **không tạo transaction thực**. Dùng `TransactionalOperator`/reactive Spring transaction infrastructure và driver hỗ trợ. Tránh chia sẻ một transaction-scoped publisher ra các subscribers độc lập.

### 10.4. Quay về legacy ExecutorService

Nếu bắt buộc dùng một asynchronous API khác Reactor, hãy truyền một immutable subset (ví dụ request ID) như method parameter, hoặc dùng Micrometer `ContextSnapshot`/context-aware Executor wrapper được cấu hình với accessors phù hợp. Không ném cả `ContextView` vào global static variable và không duy trì nó sau request lâu hơn cần thiết.

## 11. Demo runnable: Spring Boot 3.3.13, Java 17, WebFlux Context

Đây là ứng dụng đầy đủ, không cần database hoặc remote service. Tất cả mã dùng `spring-boot-starter-webflux` và `reactor-core` từ Spring Boot quản lý. Demo dùng Reactor Context trực tiếp, **không bật automatic ThreadLocal propagation** vì mục tiêu là chứng minh lý do không phụ thuộc ThreadLocal.

API:

- `GET /api/context`: trả requestId và stage = `direct`.
- `GET /api/context/delayed`: đi qua `Mono.delay`, vẫn đọc requestId.
- `GET /api/context/bounded`: chuyển bước nhỏ qua `Schedulers.boundedElastic()`, vẫn giữ requestId.
- `GET /api/context/scoped`: ghi đè context trong một nhánh cục bộ thành `local-override` để hiểu scope; response header do filter vẫn là ID bên ngoài.
- `GET /api/context/events`: 3 SSE events, mỗi event đọc context trên inner Mono.
- `GET /api/context/error`: reactive error 400; WebFilter đã set correlation response header.
- `GET /api/context/missing`: route không tồn tại, xác nhận 404 của WebFlux.

Request ID header mẫu `X-Request-Id` chỉ dùng **diagnostics**, không dùng cho auth/tenant. Filter chỉ nhận characters allowlist và độ dài ≤64 để hạn chế log injection; nếu input không đạt rule, nó tạo UUID.

### 11.1. Cấu trúc file

```text
reactor-context-demo/
├── pom.xml
└── src/
    ├── main/java/example/
    │   ├── ReactorContextApplication.java
    │   ├── ContextKeys.java
    │   ├── TraceReply.java
    │   ├── RequestIdFilter.java
    │   ├── TraceService.java
    │   └── TraceController.java
    └── test/java/example/
        ├── ContextOperatorsTest.java
        └── TraceHttpTest.java
```

### 11.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>reactor-context-demo</artifactId>
    <version>1.0.0</version>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.13</version>
        <relativePath/>
    </parent>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

**Lưu ý:** Không thêm `spring-boot-starter-web` vào demo, vì khi MVC và WebFlux starters cùng có mặt Boot thường chọn Servlet MVC. Mã `WebFilter` ở đây thuộc `org.springframework.web.server` và phù hợp reactive web application type.

### 11.3. ReactorContextApplication.java

```java
package example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ReactorContextApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReactorContextApplication.class, args);
    }
}
```

### 11.4. ContextKeys.java

```java
package example;

public final class ContextKeys {

    public static final String REQUEST_ID = "example.request-id";

    private ContextKeys() {
    }
}
```

Dùng một constant key chung cho filter và service. Không copy-paste String literals khác nhau ở nhiều nơi.

### 11.5. TraceReply.java

```java
package example;

public record TraceReply(
        String requestId,
        String stage,
        String threadName) {
}
```

`threadName` chỉ để quan sát diagnostics; không xây assertions “phải là thread X” vì Reactor schedulers và Netty thread names có thể đổi theo môi trường/versions.

### 11.6. RequestIdFilter.java

```java
package example;

import java.util.UUID;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

@Component
@Order(0)
public class RequestIdFilter implements WebFilter {

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            WebFilterChain chain) {

        String requested = exchange.getRequest()
                .getHeaders().getFirst("X-Request-Id");

        final String requestId = requested != null &&
                requested.matches("[A-Za-z0-9._-]{1,64}")
            ? requested
            : UUID.randomUUID().toString();

        exchange.getResponse().getHeaders()
                .set("X-Request-Id", requestId);

        return chain.filter(exchange)
            .contextWrite(context ->
                context.put(ContextKeys.REQUEST_ID, requestId));
    }
}
```

Giải thích:

1. `WebFilter` chạy trong reactive WebHandler chain.
2. Đọc ID tùy chọn từ HTTP request, kiểm tra ký tự/độ dài.
3. Đặt một header response để client correlate lỗi với request.
4. Trả `chain.filter(exchange).contextWrite(...)`, không gọi `block()` hay `subscribe()`.
5. Phần chain **upstream** của contextWrite có thể đọc ID qua `deferContextual`, kể cả sau scheduler hops.

ID từ client vẫn **không đáng tin cho authorization**, và không bao giờ dùng để quyết định principal/tenant. Trong production tracing instrumentation có thể tạo/validate trace IDs riêng.

### 11.7. TraceService.java

```java
package example;

import java.time.Duration;

import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class TraceService {

    public Mono<TraceReply> snapshot(String stage) {
        return Mono.deferContextual(context -> {
            String requestId = context.getOrDefault(
                    ContextKeys.REQUEST_ID, "missing");

            return Mono.just(new TraceReply(
                requestId,
                stage,
                Thread.currentThread().getName()));
        });
    }

    public Mono<TraceReply> delayed() {
        return Mono.delay(Duration.ofMillis(10))
                .flatMap(value -> snapshot("after-delay"));
    }

    public Mono<TraceReply> bounded() {
        return Mono.fromCallable(() -> "legacy-stage")
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(this::snapshot);
    }

    public Mono<TraceReply> scoped() {
        return snapshot("local-scope")
            .contextWrite(context ->
                context.put(
                    ContextKeys.REQUEST_ID, "local-override"));
    }
}
```

- `snapshot` lấy requestId khi **subscription** đi đến `deferContextual`. Không có static state.
- `delayed` dùng timer scheduler rồi `flatMap` tới `snapshot`; context vẫn có.
- `bounded` cho thấy context vẫn truy cập được sau `subscribeOn(boundedElastic)`. Callable trả một String nhỏ, **không thực hiện blocking I/O thật**.
- `scoped` cố ý ghi đè cùng key gần source; response body thấy `local-override`, nhưng WebFilter response header phản ánh ID gốc vì nó được đặt từ request trước khi body subscribe.

Không gán `threadName` của hai requests như một bằng chứng context isolation, vì scheduler names có thể trùng. Test dựa vào *giá trị requestId*.

### 11.8. TraceController.java

```java
package example;

import java.time.Duration;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
public class TraceController {

    private final TraceService service;

    public TraceController(TraceService service) {
        this.service = service;
    }

    @GetMapping("/api/context")
    public Mono<TraceReply> direct() {
        return service.snapshot("direct");
    }

    @GetMapping("/api/context/delayed")
    public Mono<TraceReply> delayed() {
        return service.delayed();
    }

    @GetMapping("/api/context/bounded")
    public Mono<TraceReply> bounded() {
        return service.bounded();
    }

    @GetMapping("/api/context/scoped")
    public Mono<TraceReply> scoped() {
        return service.scoped();
    }

    @GetMapping(
        value = "/api/context/events",
        produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<TraceReply>> events() {
        return Flux.interval(Duration.ofMillis(10))
            .take(3)
            .concatMap(index ->
                service.snapshot("event-" + index)
                    .map(reply ->
                        ServerSentEvent.<TraceReply>builder()
                            .id(Long.toString(index))
                            .event("trace")
                            .data(reply)
                            .build()));
    }

    @GetMapping("/api/context/error")
    public Mono<TraceReply> error() {
        return Mono.error(new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "demonstration error"));
    }
}
```

**Chuỗi streaming:** mỗi `index` từ Flux timer được chuyển sang một `service.snapshot(...)`. Vì inner Mono thuộc same composed subscription, context của WebFilter vẫn xuất hiện trong từng SSE payload. `take(3)` dừng stream để test không treo. Production SSE vô hạn cần heartbeat, request cancellation, resource controls và proxies timeouts.

**Error path:** `ResponseStatusException(HttpStatus.BAD_REQUEST)` được WebFlux exception infrastructure xử lý thành 400 khi chưa commit. Filter đã đặt `X-Request-Id` header, giúp client correlate lỗi ngay cả khi body error do framework sinh.

### 11.9. Chạy Maven/cURL

Tại thư mục chứa `pom.xml` (JDK 17+, Maven):

```bash
mvn test
mvn spring-boot:run
```

**Lưu ý:** Các câu lệnh này là hướng dẫn dành cho người học. Tài liệu tự chứa code; chưa tuyên bố tests được chạy trong môi trường tạo bài.

Gọi trực tiếp:

```bash
curl -i -H "X-Request-Id: demo-A" \
  http://localhost:8080/api/context
```

HTTP 200, header `X-Request-Id: demo-A` và body JSON dạng:

```json
{
  "requestId": "demo-A",
  "stage": "direct",
  "threadName": "reactor-http-nio-1"
}
```

**Không** dựa vào giá trị `threadName` ở trên vì chỉ là ví dụ; tên thực thay đổi. Chỉ các fields `requestId` và `stage` là contract ổn định.

Bước delay:

```bash
curl -i -H "X-Request-Id: demo-B" \
  http://localhost:8080/api/context/delayed
```

Kỳ vọng body chứa:

```json
{"requestId":"demo-B","stage":"after-delay"}
```

Phần threadName khác tùy runtime.

Bước boundedElastic:

```bash
curl -i -H "X-Request-Id: demo-C" \
  http://localhost:8080/api/context/bounded
```

Kỳ vọng requestId `demo-C` và stage `legacy-stage`.

Scope:

```bash
curl -i -H "X-Request-Id: outer-123" \
  http://localhost:8080/api/context/scoped
```

Kỳ vọng **header** `X-Request-Id: outer-123`, nhưng **body** có `"requestId":"local-override"`. Đây là một demo cố tình ghi đè để minh họa scope; không nên ghi đè correlation ID trong business code production trừ khi có lý do và audit.

Invalid header:

```bash
curl -i -H "X-Request-Id: invalid id" \
  http://localhost:8080/api/context
```

Kỳ vọng header/body requestId là một UUID mới theo logic validation; đừng assert một UUID cố định.

SSE:

```bash
curl -N -i -H "X-Request-Id: stream-9" \
  -H "Accept: text/event-stream" \
  http://localhost:8080/api/context/events
```

Body SSE sẽ chứa ba events (format/whitespace và thread names không cố định):

```text
id:0
event:trace
data:{"requestId":"stream-9","stage":"event-0","threadName":"..."}

id:1
event:trace
data:{"requestId":"stream-9","stage":"event-1","threadName":"..."}

id:2
event:trace
data:{"requestId":"stream-9","stage":"event-2","threadName":"..."}

```

Không kỳ vọng SSE chunk boundaries giống line boundaries của TCP. `curl -N` chỉ tắt buffering của curl.

## 12. Tests: WebTestClient kiểm tra context qua HTTP thật

File `src/test/java/example/TraceHttpTest.java`:

```java
package example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.web.reactive.server.FluxExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class TraceHttpTest {

    @Autowired
    WebTestClient client;

    @Test
    void requestIdAppearsInBodyAndResponseHeader() {
        client.get().uri("/api/context")
            .header("X-Request-Id", "req-one")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().valueEquals("X-Request-Id", "req-one")
            .expectBody()
                .jsonPath("$.requestId").isEqualTo("req-one")
                .jsonPath("$.stage").isEqualTo("direct");
    }

    @Test
    void contextSurvivesDelayBoundary() {
        client.get().uri("/api/context/delayed")
            .header("X-Request-Id", "req-delay")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
                .jsonPath("$.requestId").isEqualTo("req-delay")
                .jsonPath("$.stage").isEqualTo("after-delay");
    }

    @Test
    void contextSurvivesBoundedElasticBoundary() {
        client.get().uri("/api/context/bounded")
            .header("X-Request-Id", "req-bounded")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
                .jsonPath("$.requestId").isEqualTo("req-bounded")
                .jsonPath("$.stage").isEqualTo("legacy-stage");
    }

    @Test
    void innerContextOverrideHasLocalScope() {
        client.get().uri("/api/context/scoped")
            .header("X-Request-Id", "external-id")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().valueEquals(
                "X-Request-Id", "external-id")
            .expectBody()
                .jsonPath("$.requestId")
                    .isEqualTo("local-override")
                .jsonPath("$.stage")
                    .isEqualTo("local-scope");
    }

    @Test
    void invalidInboundIdGetsNewId() {
        client.get().uri("/api/context")
            .header("X-Request-Id", "invalid id with spaces")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
                .jsonPath("$.requestId")
                    .value(value -> {
                        String id = String.valueOf(value);
                        assertNotEquals(
                            "invalid id with spaces", id);
                        assertTrue(id.length() >= 30);
                    });
    }

    @Test
    void errorKeepsCorrelationHeader() {
        client.get().uri("/api/context/error")
            .header("X-Request-Id", "req-error")
            .exchange()
            .expectStatus().isBadRequest()
            .expectHeader().valueEquals(
                "X-Request-Id", "req-error");
    }

    @Test
    void sseEventsKeepSameSubscriptionContext() {
        FluxExchangeResult<ServerSentEvent<TraceReply>> result =
            client.get().uri("/api/context/events")
                .header("X-Request-Id", "req-stream")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectHeader()
                    .contentTypeCompatibleWith(
                        MediaType.TEXT_EVENT_STREAM)
                .returnResult(new ParameterizedTypeReference<
                    ServerSentEvent<TraceReply>>() {});

        StepVerifier.create(result.getResponseBody())
            .expectNextMatches(event ->
                event.data() != null &&
                "req-stream".equals(event.data().requestId()) &&
                "event-0".equals(event.data().stage()))
            .expectNextMatches(event ->
                event.data() != null &&
                "req-stream".equals(event.data().requestId()) &&
                "event-1".equals(event.data().stage()))
            .expectNextMatches(event ->
                event.data() != null &&
                "req-stream".equals(event.data().requestId()) &&
                "event-2".equals(event.data().stage()))
            .verifyComplete();
    }

    @Test
    void twoHttpRequestsHaveDifferentContext() {
        client.get().uri("/api/context")
            .header("X-Request-Id", "client-A")
            .exchange()
            .expectStatus().isOk()
            .expectBody().jsonPath("$.requestId")
                .isEqualTo("client-A");

        client.get().uri("/api/context")
            .header("X-Request-Id", "client-B")
            .exchange()
            .expectStatus().isOk()
            .expectBody().jsonPath("$.requestId")
                .isEqualTo("client-B");
    }

    @Test
    void missingRouteIs404() {
        client.get().uri("/api/context/not-mapped")
            .exchange()
            .expectStatus().isNotFound();
    }
}
```

Có **9 WebTestClient test methods**. Test SSE thực sự consume 3 events, không chỉ kiểm tra Content-Type. Test request A/B ở trên gọi tuần tự — xác nhận context không bị giữ thành singleton từ request trước; để test **concurrency thật**, xem StepVerifier merge ở phần sau.

**Không assert threadName cố định**, vì project cần chạy ổn định trên OS/CPU/scheduler khác nhau. Nếu test ngữ cảnh trên một actual network/proxy runtime, có thể bổ sung parallel HTTP load và trace IDs theo log instrumentation.

## 13. Tests: StepVerifier xác minh semantics Context không phụ thuộc WebFlux HTTP

File `src/test/java/example/ContextOperatorsTest.java`:

```java
package example;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

class ContextOperatorsTest {

    private static final String KEY = "test.trace";

    @Test
    void contextWriteIsVisibleUpstream() {
        Mono<String> source = Mono.deferContextual(ctx ->
            Mono.just(ctx.get(KEY)));

        StepVerifier.create(
                source.contextWrite(Context.of(KEY, "A")))
            .expectNext("A")
            .verifyComplete();
    }

    @Test
    void withoutContextDefaultIsUsed() {
        Mono<String> source = Mono.deferContextual(ctx ->
            Mono.just(ctx.getOrDefault(KEY, "missing")));

        StepVerifier.create(source)
            .expectNext("missing")
            .verifyComplete();
    }

    @Test
    void wrongOperatorPositionDoesNotAffectDownstream() {
        Mono<String> source = Mono.just("x")
            .contextWrite(ctx -> ctx.put(KEY, "inner"))
            .flatMap(value ->
                Mono.deferContextual(ctx ->
                    Mono.just(ctx.getOrDefault(KEY, "missing"))));

        StepVerifier.create(source)
            .expectNext("missing")
            .verifyComplete();
    }

    @Test
    void rightOperatorPositionIsVisibleToFlatMap() {
        Mono<String> source = Mono.just("x")
            .flatMap(value ->
                Mono.deferContextual(ctx ->
                    Mono.just(ctx.getOrDefault(KEY, "missing"))))
            .contextWrite(ctx -> ctx.put(KEY, "A"));

        StepVerifier.create(source)
            .expectNext("A")
            .verifyComplete();
    }

    @Test
    void nearestUpstreamContextWriteWinsForSameKey() {
        Mono<String> source =
            Mono.deferContextual(ctx -> Mono.just(ctx.get(KEY)))
                .contextWrite(ctx -> ctx.put(KEY, "inner"))
                .contextWrite(ctx -> ctx.put(KEY, "outer"));

        StepVerifier.create(source)
            .expectNext("inner")
            .verifyComplete();
    }

    @Test
    void contextSurvivesPublishOn() {
        Mono<String> source =
            Mono.just("start")
                .publishOn(Schedulers.parallel())
                .flatMap(value ->
                    Mono.deferContextual(ctx ->
                        Mono.just(value + "-" + ctx.get(KEY))))
                .contextWrite(Context.of(KEY, "X"));

        StepVerifier.create(source)
            .expectNext("start-X")
            .verifyComplete();
    }

    @Test
    void contextSurvivesDelay() {
        Mono<String> source = Mono.delay(Duration.ofMillis(5))
            .flatMap(tick ->
                Mono.deferContextual(ctx ->
                    Mono.just(ctx.get(KEY))))
            .contextWrite(Context.of(KEY, "delayed"));

        StepVerifier.create(source)
            .expectNext("delayed")
            .verifyComplete();
    }

    @Test
    void separateSubscriptionsAreIsolated() {
        Mono<String> source =
            Mono.deferContextual(ctx -> Mono.just(ctx.get(KEY)));

        Flux<String> merged = Flux.merge(
            source.contextWrite(Context.of(KEY, "A"))
                .subscribeOn(Schedulers.parallel()),
            source.contextWrite(Context.of(KEY, "B"))
                .subscribeOn(Schedulers.parallel()));

        StepVerifier.create(merged.collectList())
            .expectNextMatches(list ->
                list.size() == 2 &&
                list.contains("A") &&
                list.contains("B"))
            .verifyComplete();
    }

    @Test
    void innerFlatMapContextDoesNotRewriteSibling() {
        Mono<String> source = Mono.just("hello")
            .flatMap(word ->
                Mono.deferContextual(ctx ->
                    Mono.just(word + "-" + ctx.get(KEY)))
                    .contextWrite(Context.of(KEY, "inner")))
            .flatMap(value ->
                Mono.deferContextual(ctx ->
                    Mono.just(value + "-" + ctx.get(KEY))))
            .contextWrite(Context.of(KEY, "outer"));

        StepVerifier.create(source)
            .expectNext("hello-inner-outer")
            .verifyComplete();
    }

    @Test
    void manyFluxEventsSeeContext() {
        Flux<String> source = Flux.range(0, 3)
            .concatMap(index -> Mono.deferContextual(ctx ->
                Mono.just(ctx.get(KEY) + ":" + index)))
            .contextWrite(Context.of(KEY, "stream-A"));

        StepVerifier.create(source)
            .expectNext("stream-A:0", "stream-A:1",
                "stream-A:2")
            .verifyComplete();
    }

    @Test
    void innerOverrideAffectsOnlyInnerPublisher() {
        Mono<String> base =
            Mono.deferContextual(ctx -> Mono.just(ctx.get(KEY)));

        Mono<String> source = base
            .contextWrite(Context.of(KEY, "inner"));

        StepVerifier.create(
            source.contextWrite(Context.of(KEY, "outer")))
            .expectNext("inner")
            .verifyComplete();

        StepVerifier.create(
            base.contextWrite(Context.of(KEY, "outer")))
            .expectNext("outer")
            .verifyComplete();
    }

    @Test
    void missingRequiredKeyIsAnError() {
        Mono<String> source =
            Mono.deferContextual(ctx -> Mono.just(ctx.get(KEY)));

        StepVerifier.create(source)
            .expectError(java.util.NoSuchElementException.class)
            .verify();
    }
}
```

Có **12 Reactor unit tests**. Kết quả không lệ thuộc Netty thread names; StepVerifier xác nhận exact values, completion và missing context. Test `separateSubscriptionsAreIsolated` merge hai pipelines chạy cùng scheduler, kiểm tra A/B cùng xuất hiện dù thứ tự completion có thể thay đổi. Đó là minh họa concurrency tốt hơn test sequential HTTP đơn thuần.

### 13.1. Khi nào dùng StepVerifier.withVirtualTime?

Khi test stream nhiều phút có `delayElements`/`interval`, dùng virtual scheduler sẽ nhanh và ổn định hơn `Thread.sleep`. Tạo publisher **bên trong Supplier** của `withVirtualTime`:

```java
@Test
void virtualTimeStillPreservesContext() {
    StepVerifier.withVirtualTime(() ->
        Flux.interval(Duration.ofHours(1))
            .take(2)
            .concatMap(n ->
                Mono.deferContextual(ctx ->
                    Mono.just(ctx.get("trace") + ":" + n)))
            .contextWrite(Context.of("trace", "virtual")))
        .expectSubscription()
        .thenAwait(Duration.ofHours(2))
        .expectNext("virtual:0", "virtual:1")
        .verifyComplete();
}
```

Snippet bổ sung đặt trong `ContextOperatorsTest` với imports đã có. Khi dùng `withVirtualTime`, không tạo Flux.interval ở ngoài Supplier rồi mới verify vì scheduler có thể đã được capture trước khi virtual scheduler được cài đặt.

## 14. Truyền context sang callbacks khác Reactor

### 14.1. CompletableFuture

```java
CompletableFuture<String> future =
    CompletableFuture.supplyAsync(
        () -> legacyService.readData());
```

Đoạn này sử dụng executor khác và không nhất thiết có Reactor subscriber context. Nếu bọc:

```java
Mono<String> wrapped = Mono.fromFuture(future);
```

bạn có reactive result nhưng không tự có context bên trong `supplyAsync` đã chạy trước đó. Nếu việc đọc ngữ cảnh quan trọng, đọc từ `deferContextual` rồi pass **immutable request ID** vào supplier hoặc dùng context-propagating executor chuẩn.

### 14.2. Scheduler wrappers, Micrometer ContextSnapshot

Với non-Reactor executors và libraries phụ thuộc ThreadLocal, dùng `io.micrometer:context-propagation` để đăng ký accessors/capture snapshot khi cần, rồi bọc công việc thực thi theo đúng API version. Không triển khai “set ThreadLocal mà quên restore” vì thread pool sẽ reuse. Chỉ restore đúng một **scope** và close/clear trong finally/try-with-resources.

### 14.3. Coroutines

Spring WebFlux với Kotlin coroutines có khả năng bridge Reactor Context sang coroutine context qua integrations phù hợp (ví dụ `ReactorContext`), nhưng không mặc định every Java ThreadLocal survives suspension. Trước khi port Java Reactor code sang Kotlin, kiểm tra coroutine context propagation/withContext boundaries và framework dependencies.

### 14.4. Reactive transaction boundaries

Một Spring reactive transaction giữ connection/transaction state theo reactive context. Nếu tự `subscribe()` một repository.save() riêng, operation đó có thể thoát khỏi transaction/subscription và commit ngoài thời điểm kỳ vọng. Compose publisher trong transaction chain, dùng `TransactionalOperator` hoặc reactive @Transactional đúng config.

## 15. Security, privacy và logging hygiene

Dù Reactor Context hữu ích cho audit/tracing, không nên biến nó thành nơi giữ tất cả state của user.

### 15.1. Phân loại metadata

| Loại | Có thể dùng context? | Ghi chú |
| --- | --- | --- |
| Correlation/request ID đã validate | Có | Chỉ cho logs/tracing, không xác thực |
| Span/observation context | Có qua instrumentation | Để Micrometer/OpenTelemetry quản lý |
| Authenticated principal | Spring Security reactive context | Không tự tin X-User headers |
| Tenant ID | Chỉ khi nguồn đáng tin | Phải authorize từng resource |
| Locale/timezone preference | Có thể | Chọn nguồn và validation rõ |
| Raw JWT/password/session cookie | Tránh | Có thể lộ qua context dumps/logs |
| Large request body/FilePart | Tránh | Buffer/memory/resources |
| Mutable JPA EntityManager | Không phù hợp | Blocking/thread-bound semantics |
| Mutable global Map state | Không | Rủi ro data races/leaks |

### 15.2. IDs từ client: xác thực hay chỉ correlation?

Một requestId header có thể do client giả, tái sử dụng hoặc chứa dữ liệu không phù hợp. Trong demo chỉ cho allowed pattern và echo. Trong production cần quy định độ dài, uniqueness, log forging, ingress trusted trace correlation và sampling. Không dùng requestId làm access control hay idempotency key nếu chưa xác thực scope.

### 15.3. Tracing qua mạng

Reactor Context nằm **trong process/JVM**. Khi WebClient gọi remote service, remote JVM không tự đọc được Context này. Distributed tracing dùng HTTP propagation conventions như W3C `traceparent` và instrumentation. Không tự log toàn ContextView để debug vì có thể chứa keys nội bộ từ security/tracing libraries.

### 15.4. Correlation logs và lỗi HTTP

Khi xảy ra lỗi giữa một SSE stream, correlation ID giúp tìm logs đúng request. Nhưng response có thể đã commit 200 và không còn cách sửa status; correlation không thay business failure protocol. Error logs cần severity, structured error codes và không leak tokens/PII.

## 16. Lỗi phổ biến cần nhận diện

| Nhầm lẫn / triệu chứng | Nguyên nhân | Hướng đúng |
| --- | --- | --- |
| “ThreadLocal luôn còn nguyên trong WebFlux” | Publisher chuyển scheduler/thread | Dùng Reactor Context hoặc bridge |
| “Một request có một thread Netty riêng” | Event loops phục vụ nhiều channels | Không dựa vào thread identity |
| “Context chỉ là một global map” | Gắn subscriber | Đọc bằng deferContextual |
| “contextWrite sửa Context object tại chỗ” | Context immutable | Nhận Context mới theo chain |
| “contextWrite luôn ảnh hưởng mọi operator bên dưới nó” | Hướng propagation ngược | Đặt write downstream của readers |
| “Context đặt ở source được mọi flatMap downstream đọc” | Scope của contextWrite chỉ upstream | Test order bằng StepVerifier |
| “Mono.just(ctx.get(...)) ở controller lấy context hiện tại” | Không có ctx per-subscription trong imperative code | deferContextual |
| “Một static String requestId đủ cho tất cả” | Race/leak cross-request | Context per-subscriber |
| “Context inner branch ảnh hưởng mọi siblings” | Ghi đè scoped | Đặt write ở common downstream nếu cần |
| “subscribe() thủ công giữ HTTP context” | Tạo subscription khác | Compose publisher/return chain |
| “publishOn làm mất Reactor Context” | Thường không khi pipeline đúng | deferContextual qua operator |
| “publishOn tự truyền mọi ThreadLocal” | ThreadLocal khác Context | Registered accessor/bridge |
| “Bật auto hook là mọi ThreadLocal đều có” | Thiếu SPI/accessor | Cấu hình Micrometer đúng |
| “default mode khôi phục MDC trong mọi map” | Limited restoration | Kiểm tra handle/tap/automatic mode |
| “contextCapture đọc Context rồi ghi ThreadLocal” | Thường capture registered ThreadLocal vào Context | Hiểu chiều capture/restore |
| “WebFilter set ThreadLocal là đủ cho stream SSE dài” | Lối sống thread thay đổi | WebFilter contextWrite |
| “SSE Flux share global có per-client Context tự động” | Upstream hot sharing phức tạp | Đọc context theo per-client downstream |
| “Reactive Context tự gửi sang remote microservice” | Chỉ local JVM/subscription | Tracing/outbound headers rõ |
| “Mất Context thì fallback admin” | Auth bypass | Fail-closed |
| “X-Request-Id là user identity” | Header client tự sửa | Auth principal đáng tin |
| “MDC.put rồi không cần remove” | Thread reused | try/finally hoặc integration |
| “Dump toàn Context ra logs an toàn” | Có thể chứa sensitive metadata | Chỉ log allowlisted keys |
| “Mọi blocking call sẽ được boundedElastic tự nhận” | Reactor không tự chuyển library code | Explicit bridge/non-blocking driver |
| “Context giúp Flux nhanh hơn” | Chỉ mang metadata | Tối ưu nguồn/backpressure riêng |
| “HTTP 200 stream lỗi vẫn set lại 500” | Headers đã commit | Protocol error/cancel |
| “WebTestClient assert threadName đủ chứng minh” | Runtime detail unstable | Assert requestId và test scheduler hops |
| “Context key string không có collision” | Libraries/app có thể cùng key | Namespace/object keys |
| “Context cache() luôn riêng cho từng subscriber” | Shared source có lifecycle riêng | Hiểu hot/cold/cache semantics |
| “Request context là transaction” | Business transaction API khác | Reactive transaction infrastructure |
| “Context là HttpSession” | Subscription-scoped không durable | Dùng session store đúng |

## 17. Checklist trước khi dùng trong production

1. [ ] Định nghĩa rõ metadata cần truyền (correlation ID, trusted principal reference, trace).
2. [ ] Không dùng static mutable request state hay ThreadLocal mà thiếu bridge/cleanup.
3. [ ] Context keys ổn định, namespaced hoặc typed identity.
4. [ ] Đặt `contextWrite` downstream của readers mong muốn.
5. [ ] Dùng `deferContextual` đúng tại subscription time.
6. [ ] Không gọi `subscribe()` để làm side effects ngoài request chain.
7. [ ] Nếu chuyển scheduler bằng publishOn/subscribeOn, test context còn nguyên.
8. [ ] Test context trong inner publishers và sibling branches.
9. [ ] Với SSE/streaming, test nhiều events có cùng context.
10. [ ] Dọn tài nguyên khi complete/error/cancel; không phụ thuộc chỉ doOnComplete.
11. [ ] Nếu dùng MDC/ThreadLocal, cấu hình Micrometer accessors và automatic/default mode rõ.
12. [ ] Không bật auto context propagation chỉ vì “bật cho chắc”; đo overhead.
13. [ ] Spring Security principal/tenant nhận từ trusted auth, không từ request ID header.
14. [ ] Outbound WebClient tracing/correlation được propagates qua headers có kiểm soát.
15. [ ] DB transaction vẫn dùng reactive transaction APIs, không tự giả bằng context key.
16. [ ] Không log/copy secrets và không dump toàn context.
17. [ ] Backpressure, buffering, timeouts và long-lived connections có giới hạn.
18. [ ] Có tests StepVerifier cho missing/override/parallel context isolation.
19. [ ] WebTestClient xác minh header/body/error/SSE sau scheduler boundaries.
20. [ ] Quan sát trace/log correlation theo request dưới concurrent load.

## 18. Câu hỏi ôn tập và đáp án

**1.** Reactor Context gắn với thread hay subscriber?  
**Đáp án:** Subscriber/subscription, không phải một Java Thread.

**2.** `contextWrite` truyền giá trị về phía nào?  
**Đáp án:** Về upstream operators khi subscription thiết lập.

**3.** Vì sao dùng `deferContextual`?  
**Đáp án:** Đọc ngữ cảnh khi Publisher được subscribe thay vì tại assembly time.

**4.** `ContextView` có mutable set() không?  
**Đáp án:** Không; nó là read-only view.

**5.** `Context.put` có mutate instance gốc không?  
**Đáp án:** Không, trả một Context mới.

**6.** Có thể dùng ThreadLocal trong MVC sync không?  
**Đáp án:** Có khi scope được quản lý và cleanup đúng; nhưng không suy sang WebFlux.

**7.** `publishOn` có tự xóa Reactor Context không?  
**Đáp án:** Không, nếu chuỗi Reactor được compose đúng, context vẫn theo subscription.

**8.** `ThreadLocal.get()` sau publishOn có chắc đọc đúng request?  
**Đáp án:** Không, trừ khi có context propagation bridge được cấu hình phù hợp.

**9.** Automatic mode có propagates một arbitrary ThreadLocal chưa đăng ký không?  
**Đáp án:** Không; cần ThreadLocalAccessor/context-propagation integration.

**10.** `contextCapture()` chủ yếu làm gì?  
**Đáp án:** Capture registered ThreadLocal values tại subscription time vào Reactor Context.

**11.** Tại sao không tự `subscribe()` trong WebFlux handler?  
**Đáp án:** Tạo subscription mới có thể làm mất context, cancellation, error handling và transaction scope.

**12.** `X-Request-Id` có nên dùng làm role hoặc tenant authority?  
**Đáp án:** Không; nó chỉ là metadata correlation không đáng tin để phân quyền.

**13.** Một Flux SSE có ba events thì có ba Context riêng không?  
**Đáp án:** Thường cùng một subscription context cho client, trừ khi inner branches override có chủ ý.

**14.** `flatMap` inner publisher viết context có thay context toàn app không?  
**Đáp án:** Không, scope của inner context write là cục bộ.

**15.** Dùng một global shared Flux cho dữ liệu riêng tư của từng user cần cẩn thận gì?  
**Đáp án:** Upstream sharing có thể không bảo đảm per-subscriber context/tenant boundaries; phải tách scope và authorization.

**16.** Làm sao đưa correlation ID ra một remote HTTP service?  
**Đáp án:** Đọc Context và đặt outbound header có policy hoặc dùng tracing instrumentation.

**17.** ReactiveSecurityContextHolder dùng cho gì?  
**Đáp án:** Lấy Spring Security authentication context trong reactive chain.

**18.** Context có tự tạo DB transaction không?  
**Đáp án:** Không, cần reactive transaction infrastructure.

**19.** Vì sao MDC cần cleanup trên thread pool?  
**Đáp án:** Thread reused, state cũ có thể lộ sang request khác.

**20.** Cách kiểm thử context không phụ thuộc threadName là gì?  
**Đáp án:** StepVerifier expectNext values và WebTestClient HTTP IDs, thêm scheduler hops.

**21.** Context có ảnh hưởng HTTP status sau response commit không?  
**Đáp án:** Không, nó chỉ truyền metadata.

**22.** `contextWrite` gần source hơn ghi giá trị nào nếu downstream cũng ghi cùng key?  
**Đáp án:** Giá trị gần source hơn có thể override giá trị downstream đối với source.

**23.** Nếu context key bắt buộc không tồn tại, `ctx.get(key)` làm gì?  
**Đáp án:** Thường ném NoSuchElementException; nên xử lý fail-closed hoặc policy rõ.

**24.** `spring.reactor.context-propagation=auto` có tự tạo principal không?  
**Đáp án:** Không, nó chỉ cấu hình propagation cho registered ThreadLocal integration.

**25.** Khi người dùng hủy SSE, cleanup nên xem tín hiệu nào?  
**Đáp án:** `SignalType.CANCEL` qua `doFinally`/lifecycle phù hợp, không chờ complete.

## 19. Nguồn tham khảo chính thức

- [Project Reactor 3.6 Reference — Adding a Context to a Reactive Sequence](https://projectreactor.io/docs/core/3.6.12/reference/index.html#context): cơ chế per-subscriber Context, contextWrite, deferContextual, thứ tự operators và inner scopes.
- [Project Reactor — Context-Propagation Support](https://projectreactor.io/docs/core/release/reference/advanced-contextPropagation.html): Micrometer context-propagation, default/automatic modes, ThreadLocalAccessor, contextCapture.
- [Project Reactor — Mono Javadoc](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html): `deferContextual`, `contextWrite`, subscription and operator behavior.
- [Project Reactor — Flux Javadoc](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html): `Flux.deferContextual`, scheduler and error/cancellation operators.
- [Spring Framework 6.2 — WebFlux WebHandler API](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/web-handler-api.html): WebFilter, ServerWebExchange, reactive request lifecycle.
- [Spring Boot — Observability and Context Propagation](https://docs.spring.io/spring-boot/reference/actuator/observability.html): Reactor context-propagation auto setting và Micrometer integration.
- [Spring Security — Reactive Method Security](https://docs.spring.io/spring-security/reference/reactive/authorization/method.html): reactive security context/authorization.
- [Spring Framework — WebTestClient](https://docs.spring.io/spring-framework/reference/6.2/testing/webtestclient.html): HTTP reactive tests.
- [Reactor Test StepVerifier Javadoc](https://projectreactor.io/docs/test/release/api/reactor/test/StepVerifier.html): signal/value/time tests.

**Điều cần nhớ:** ***ThreadLocal thuộc về thread, Reactor Context thuộc về subscriber.*** Với WebFlux stream, muốn truyền request ID/trace/locale qua asynchronous boundaries thì ghi vào Context bằng `contextWrite` và đọc bằng `deferContextual` theo đúng scope. Nếu cần MDC hoặc thư viện dựa ThreadLocal, dùng Micrometer context-propagation/registered accessors rõ ràng; không suy rằng Reactor Context tự biến mọi ThreadLocal thành an toàn. Và luôn tách metadata correlation với authentication/authorization.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-streaming-008">WebSocketHandler reactive và vòng đời luồng nhận/gửi</a>

<details>
<summary>Xem chi tiết</summary>

# WebSocketHandler reactive và vòng đời luồng nhận/gửi trong Spring WebFlux

## 1. Mục tiêu: một kết nối, hai luồng bất đồng bộ

Trong HTTP REST thông thường, client gửi một request và nhận một response. Với **WebSocket**, client thực hiện một **HTTP handshake** trước; sau khi server chấp nhận nâng cấp giao thức, client và server có thể **gửi thông điệp theo cả hai hướng** trong một kết nối lâu dài. Điều này rất phù hợp với chat, collaboration, live dashboards, games và event notification hai chiều.

Spring WebFlux có API WebSocket ở package `org.springframework.web.reactive.socket`. Hai kiểu quan trọng nhất là:

- `WebSocketHandler`: xử lý **toàn bộ phiên WebSocket** bằng method `Mono<Void> handle(WebSocketSession session)`.
- `WebSocketSession`: cung cấp **luồng nhận** `Flux<WebSocketMessage> receive()` và **luồng gửi** `Mono<Void> send(Publisher<WebSocketMessage>)`; ngoài ra có handshake info, session ID, tạo text/binary/ping/pong messages và API đóng kết nối.

Đây không phải controller `@GetMapping`, cũng không phải WebFlux.fn `HandlerFunction<ServerResponse>`. WebSocket thường chỉ route theo một endpoint URL **khi handshake**, sau đó ứng dụng phải tự xác định **giao thức thông điệp bên trong phiên**.

Bài sử dụng **Spring Boot 3.3.5, Spring Framework 6.1.x, Java 17 và Reactor**. Phần ví dụ chạy trên `spring-boot-starter-webflux` với Reactor Netty, không cần Servlet MVC hoặc thư viện STOMP.

### Kết quả học tập

Sau bài, bạn có thể:

1. Trình bày WebSocket handshake HTTP 101 và luồng text/binary frames sau upgrade.
2. Phân biệt `WebSocketHandler`, `WebSocketSession`, `WebSocketMessage`, `WebSocketClient`, `HandlerMapping` và `WebSocketHandlerAdapter`.
3. Hiểu `receive()` như inbound Flux và `send(...)` như outbound Mono completion.
4. Ghép inbound và outbound thành **một Publisher** thể hiện chính xác lifecycle của session.
5. Tự xây handler echo và handler phát các thông báo có giới hạn.
6. Hiểu close, error, cancel, half-open connection, ping/pong, graceful shutdown và session scope.
7. Xử lý input/output có giới hạn, không block event loop, không subscribe thủ công.
8. Phân biệt luồng WebSocket với SSE, NDJSON và HTTP response streaming.
9. Hiểu authentication lúc handshake, authorization từng message, Origin/subprotocol và WebSocket security.
10. Viết bài kiểm thử integration qua WebSocket client, xác nhận messages và closure.
11. Biết khi nào cần broker/pub-sub/Sinks để multiplex nhiều producers vào một đường send an toàn.
12. Nhận ra pooled buffer ownership, memory/backpressure, per-connection resource leaks và các rủi ro production.

## 2. Từ HTTP handshake đến WebSocket frames

Một kết nối WebSocket bắt đầu bằng HTTP GET có Upgrade headers (ví dụ HTTP/1.1):

```http
GET /ws/echo HTTP/1.1
Host: localhost:8080
Connection: Upgrade
Upgrade: websocket
Sec-WebSocket-Version: 13
Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
Origin: http://localhost:3000
```

`Sec-WebSocket-Key` bên trên là ví dụ tài liệu; client WebSocket tạo key ngẫu nhiên theo giao thức. Server chấp nhận handshake trả:

```http
HTTP/1.1 101 Switching Protocols
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
```

`Sec-WebSocket-Accept` được tính theo RFC 6455 từ key; không nên hardcode trong ứng dụng. Sau status **101**, HTTP request/response theo kiểu REST thông thường đã được nâng cấp thành phiên trao đổi WebSocket frames.

Luồng logic:

```text
Browser / WebSocketClient
       |
       | HTTP GET + Upgrade headers
       v
Reactive HTTP server (ví dụ Reactor Netty)
       |
       v
WebFlux DispatcherHandler
       |
       +--> SimpleUrlHandlerMapping: /ws/echo -> WebSocketHandler
       |
       +--> WebSocketHandlerAdapter
              |
              +--> WebSocketService / HandshakeWebSocketService
                       |
                       +--> kiểm tra handshake
                       +--> chọn server-specific upgrade strategy
                       +--> trả 101 khi hợp lệ
                       +--> tạo WebSocketSession
                                  |
                                  v
                             handle(session)
                                  |
                  +---------------+---------------+
                  |                               |
            receive() Flux                  send(Publisher)
                  |                               |
             inbound frames                 outbound frames
                  |                               |
                  +---------- Mono<Void> ----------+
                                completion/error/cancel
```

**Không nhầm:** Một lần handshake có thể tạo **rất nhiều** tin nhắn hai chiều, không có một HTTP status cho mỗi tin nhắn. Nếu ứng dụng cần trả "lỗi nghiệp vụ" khi client gửi sai JSON, nó phải quy định message error schema hoặc close code — không thể sau upgrade trả một HTTP 400 mới cho từng message.

### Khi nào dùng WebSocket?

- Chat, collaborative editing, multiplayer/game state và command/reply với latency thấp.
- Client vừa cần gửi lệnh, vừa cần nhận events mà không tạo HTTP request mới mỗi lần.
- Tương tác hai chiều hoặc push liên tục với nhiều phản hồi.

Nếu chỉ server đẩy cập nhật tới browser, **SSE** có thể đơn giản hơn. Nếu chỉ export danh sách dữ liệu một chiều, **NDJSON** cũng có thể phù hợp. Cần chọn theo **communication pattern**, không chỉ vì muốn dùng Reactor.

## 3. WebSocketHandler: hợp đồng cốt lõi

Interface ở `org.springframework.web.reactive.socket.WebSocketHandler` có method:

```java
public interface WebSocketHandler {
    Mono<Void> handle(WebSocketSession session);

    // API thực có thêm getSubProtocols() mặc định.
}
```

Đây là đoạn **minh họa chữ ký API**, không phải file cần định nghĩa lại.

`handle(session)` được gọi sau khi handshake thành công. Nó phải **return một `Mono<Void>` phản ánh toàn bộ việc xử lý session**. Publisher đó complete khi phiên được xử lý xong hoặc đóng theo policy; phát error signal khi xảy ra lỗi chưa được xử lý; có thể bị cancel khi kết nối bị hủy/disconnect.

### Mono<Void> không phải "không làm gì"

- Không chứa một DTO Java để serialize thành HTTP JSON response.
- Có các signals **onSubscribe, onComplete, onError, cancel**.
- Server/framework subscribe vào returned Mono; application **không được** tự subscribe một Mono detached rồi lập tức return `Mono.empty()`.
- Khi handler trả `Mono.empty()` ngay, session có thể kết thúc sớm mà bạn chưa đọc/gửi gì.
- Khi handler trả một Mono không bao giờ complete và không xử lý cancellation, connection/resource có thể tồn tại rất lâu không cần thiết.

### Không dùng Servlet WebSocket interface trong WebFlux

| Stack | Kiểu thường dùng | Đầu vào và vòng đời |
| --- | --- | --- |
| WebFlux reactive | `org.springframework.web.reactive.socket.WebSocketHandler` | `handle(WebSocketSession): Mono<Void>` |
| Spring Servlet WebSocket | `org.springframework.web.socket.WebSocketHandler` | Callback như `afterConnectionEstablished`, `handleMessage` |
| Spring messaging/STOMP | `@MessageMapping`, broker abstractions | Giao thức messaging mức cao |
| Browser client | `WebSocket` JavaScript | `onopen`, `onmessage`, `onclose`, `onerror` |

Các interface WebSocketHandler của Servlet và WebFlux **có tên giống nhau nhưng package và execution model khác nhau**. Đừng import `org.springframework.web.socket.WebSocketHandler` vào example reactive này.

## 4. WebSocketSession là ngữ cảnh của một kết nối

API tiêu biểu từ `org.springframework.web.reactive.socket.WebSocketSession`:

| API | Ý nghĩa |
| --- | --- |
| `getId()` | ID phiên do implementation cung cấp, hữu ích để correlation |
| `getHandshakeInfo()` | Metadata handshake (URI, headers, principal, v.v.) |
| `receive()` | `Flux<WebSocketMessage>` của thông điệp đi vào |
| `send(Publisher<WebSocketMessage>)` | Ghi outbound messages, trả `Mono<Void>` khi gửi hoàn tất |
| `textMessage(String)` | Tạo outbound text message |
| `binaryMessage(Function<DataBufferFactory,DataBuffer>)` | Tạo binary message |
| `pingMessage(...)` / `pongMessage(...)` | Tạo control messages nếu cần |
| `close(CloseStatus)` | Yêu cầu đóng phiên với close status |
| `closeStatus()` | `Mono<CloseStatus>` phản ánh close outcome |
| `isOpen()` | Trạng thái tại thời điểm kiểm tra, không nên dựa vào để đồng bộ lifecycle |
| `bufferFactory()` | Factory tạo DataBuffer cho outbound messages |

`WebSocketSession` **chỉ sống cho một kết nối**. Không cất object này vào field mutable của singleton handler rồi dùng chung giữa nhiều clients. Với multi-user notification, registry/cache session state phải có lifecycle cleanup, bounded queues và concurrency policy, không chỉ dùng `HashMap<String,Session>` vô hạn.

### HandshakeInfo không phải HTTP request thông thường

Handshake metadata có thể cung cấp `URI`, headers và principal. Sau khi upgrade, handler không có một HTTP request mới mỗi khi client gửi text. Muốn truyền metadata trên từng message, cần **định nghĩa message envelope riêng** hoặc subprotocol. Không dùng raw query parameter `?user=alice` làm bằng chứng authenticated identity.

## 5. Luồng nhận: Flux<WebSocketMessage> receive()

`session.receive()` trả `Flux<WebSocketMessage>`. Một message có loại TEXT, BINARY, PING hoặc PONG (việc expose/handling control frames tùy server implementation/protocol); payload nằm ở `DataBuffer`.

Ví dụ log chỉ **loại message**:

```java
Mono<Void> inbound = session.receive()
        .doOnNext(message ->
                logger.info("session {} type={}",
                        session.getId(), message.getType()))
        .then();
```

Đây là **snippet** (cần `session` và logger bean). `then()` biến stream nhận nhiều messages thành `Mono<Void>`, complete khi inbound complete. Không log raw payload nếu có credentials, PII hoặc secrets.

### Đọc text ngay trong operator synchronous

```java
Flux<String> textInput = session.receive()
        .filter(message ->
                message.getType() == WebSocketMessage.Type.TEXT)
        .map(WebSocketMessage::getPayloadAsText);
```

Đây là snippet có `session`. `getPayloadAsText()` chuyển bytes từ frame text sang String trong lúc message buffer còn hợp lệ. Nếu muốn xử lý bất đồng bộ **sau** khi giữ lại nguyên `WebSocketMessage`/pooled `DataBuffer`, phải lưu ý `retain`/`release` (mục 16). Ví dụ echo đầy đủ chọn **tách thành String sớm**, không giữ buffer qua async boundary.

### Flux nhận thường kết thúc khi kết nối đóng

Không nên coi mỗi `onComplete` là business "đã nhận đủ N messages" nếu protocol của client là live channel. Nó có thể xảy ra vì close bình thường. `onError` có thể báo network/decoder failure; `cancel` có thể đến khi application trả về/handler bị hủy. Nếu muốn dừng sau một command `stop`, hãy thiết kế close semantics, không dựa vào "client sẽ ngắt socket sau khi gửi" như một hợp đồng ngầm.

## 6. Luồng gửi: Mono<Void> send(Publisher<WebSocketMessage>)

`session.send(outboundPublisher)` nhận một publisher chứa các outbound WebSocketMessage. Khi nguồn hoàn tất và các messages được ghi, returned Mono hoàn tất.

```java
Flux<WebSocketMessage> greeting = Flux.just(
        session.textMessage("hello"),
        session.textMessage("welcome"));

Mono<Void> result = session.send(greeting);
return result;
```

Đây là **snippet** dùng trong `handle(session)`. Nếu handler chỉ cần gửi hai thông báo rồi kết thúc phiên, hoàn tất `send` cũng có thể là điểm kết thúc application handling; framework/runtime quản lý closure theo lifecycle.

### Không gọi send() nhiều lần độc lập

Phản ví dụ:

```java
// PHẢN VÍ DỤ: hai outbound writers cạnh tranh cùng một session.
session.send(streamA).subscribe();
session.send(streamB).subscribe();
return Mono.empty();
```

Điều này tạo subscriptions detached, không gắn với lifecycle/error/cancellation của handler. Hai writers có thể gây race hoặc hỏng thứ tự messages. Khi cần dữ liệu từ nhiều nguồn, **merge/multiplex về một Publisher<WebSocketMessage> rồi gọi session.send(...) một lần**, với giới hạn queue và ordering policy.

### Gửi text

```java
return session.send(
        Flux.just("first", "second")
                .map(session::textMessage));
```

Đây là snippet. `WebSocketSession.textMessage` tạo WebSocket text frame theo encoding chuẩn (UTF-8). Đừng tự thêm delimiter `\n` nếu protocol không quy định; **WebSocket đã có message boundaries**, khác với NDJSON trên HTTP stream.

## 7. Echo: ghép inbound và outbound bằng một flow

Một echo server nhận text và gửi lại:

```java
@Override
public Mono<Void> handle(WebSocketSession session) {
    Flux<WebSocketMessage> outgoing = session.receive()
            .filter(message ->
                    message.getType() == WebSocketMessage.Type.TEXT)
            .map(WebSocketMessage::getPayloadAsText)
            .map(text -> "echo:" + text)
            .map(session::textMessage);

    return session.send(outgoing);
}
```

Đây là một **implementation snippet**; mã class runnable ở mục 14. Cấu trúc quan trọng:

```text
session.receive()
    -> WebSocketMessage
    -> String
    -> transform
    -> session.textMessage(...)
    -> session.send(outgoing)
    -> Mono<Void>
```

Không có `block()`/`subscribe()` thủ công. `session.send(outgoing)` subscribe vào outgoing; outgoing lại subscribe vào inbound. Khi client đóng, inbound complete/error; outgoing tương ứng dừng, send hoàn tất/hủy và framework dọn phiên.

### Vì sao đây là cách đơn giản, an toàn?

- Một inbound subscription.
- Một outbound writer.
- Mỗi text message được phản hồi một lần.
- Không giữ mutable per-session fields.
- Dùng reactive operators và tôn trọng cancellation.
- Không cần create `Sinks.Many`/shared queue khi chưa có broadcast.

### Echo không phải production chat

Echo không có auth, rooms, fan-out, durable messages, replay, unread counts, delivery acknowledgements hoặc multi-node coordination. Nếu chỉ copy echo handler rồi lưu mọi session trong một global map, bạn chưa giải quyết authorization, memory limits, cleanup hay horizontal scaling.

## 8. Khi inbound và outbound độc lập nhau

Một hệ thống thông báo có thể vừa **nhận commands** vừa **gửi events từ nguồn ngoài**. Hai streams không nhất thiết có quan hệ "một input → một output".

Ví dụ khái niệm:

```java
Mono<Void> inbound = session.receive()
        .filter(message ->
                message.getType() == WebSocketMessage.Type.TEXT)
        .map(WebSocketMessage::getPayloadAsText)
        .concatMap(commandService::handle)
        .then();

Flux<WebSocketMessage> outboundMessages =
        eventService.streamForUser(userId)
                .map(session::textMessage);

Mono<Void> outbound = session.send(outboundMessages);

return inbound.and(outbound);
```

Đây là **snippet phụ thuộc** các biến `session`, `commandService`, `eventService`, `userId` giả định. `inbound.and(outbound)` phối hợp completion hai phía theo Reactor, giống cách Spring docs minh họa. **Không đánh đồng điều này với mọi nhu cầu closure**: nếu outbound vô hạn nhưng inbound complete vì client ngắt, cần xác nhận composition chọn có cancel outbound đúng lúc; với một số application, `takeUntilOther(inbound)` hoặc một termination signal rõ hơn có thể phù hợp.

### Các chiến lược kết thúc khác nhau

1. **Echo dependent flow:** outbound phụ thuộc trực tiếp inbound, dừng khi inbound kết thúc; `return session.send(session.receive().map(...))`.
2. **Server finite push:** chỉ gửi N messages rồi dừng, `return session.send(Flux.take(N)...)`.
3. **Long-lived push:** outbound có thể vô hạn, cần gắn completion/cancel từ connection và business shutdown policy.
4. **Command 'stop':** nhận một command ứng dụng, phát acknowledgement, rồi close với status phù hợp; client/server thống nhất semantics.
5. **Server shutdown:** graceful close active sessions, đợi thời hạn nhất định, hủy remaining subscriptions.
6. **Fatal protocol error:** đóng với close code phù hợp, không cố gửi một HTTP 500 sau upgrade.

**Một WebSocket connection là full duplex**, nhưng làm sao *orchestrate* hai flows là quyết định của ứng dụng, không phải `send` tự đọc inbound thay bạn.

## 9. Reactive completion, error và cancellation

`Mono<Void>` từ `handle()` quyết định điểm kết thúc lifecycle. Bảng:

| Signal | Ý nghĩa | Hành động thiết kế |
| --- | --- | --- |
| `onComplete` | Luồng handling hoàn tất bình thường | Dọn state, counters, subscriptions |
| `onError` | Lỗi chưa được xử lý | Log an toàn; đóng phù hợp; không giả JSON HTTP response mới |
| `CANCEL` | Subscription bị hủy bởi disconnect/shutdown/logic khác | Cleanup upstream resources và timers |
| Close frame | WebSocket protocol báo đóng | Ghi nhận close code/reason khi có thể |
| Socket reset không close frame | Network failure bất thường | Không giả định luôn có clean close event |
| Timeout/idle expiry | Không có hoạt động hoặc policy hết hạn | Chọn ping/heartbeat và server/proxy idle settings |

Đừng chỉ viết:

```java
return session.receive()
        .doOnComplete(() -> metrics.connectedMinusOne())
        .then();
```

Nếu connection bị cancel hoặc error, `doOnComplete` có thể không chạy và active connection count bị leak. Dùng `doFinally` trên **một publisher sở hữu lifecycle**, đếm chính xác một lần và tránh double-decrement:

```java
return session.send(outgoing)
        .doOnSubscribe(sub -> active.increment())
        .doFinally(signal -> active.decrement());
```

Đây là **snippet** với `outgoing` và `active` do ứng dụng cấp. Nếu metrics increment chưa chạy hoặc cùng session tái subscribe, phải bảo vệ logic đếm; `doFinally` là nơi phù hợp để xử lý COMPLETE/ERROR/CANCEL, không nhất thiết là nơi đóng mọi resources một cách đồng bộ.

### Không dựa vào isOpen() để quyết định lifecycle

`session.isOpen()` chỉ là snapshot tại một thời điểm, có race với network close. Trong reactive code, hãy dựa vào completion/error/cancellation và `closeStatus()` khi thích hợp. Đừng loop `while(session.isOpen())` rồi `Thread.sleep` để poll WebSocket.

## 10. CloseStatus và quy tắc đóng

WebSocket RFC 6455 có **close frame** với status code và (tùy chọn) reason. Spring WebFlux cung cấp `CloseStatus` và API `session.close(...)`.

Ví dụ:

```java
return session.close(CloseStatus.NORMAL);
```

Đây là snippet bên trong handler. `CloseStatus.NORMAL` biểu thị đóng bình thường (1000). Một số code thường gặp:

| Code | Ý nghĩa tiêu chuẩn | Ghi nhớ |
| --- | --- | --- |
| 1000 | Normal Closure | Kết thúc phiên bình thường |
| 1001 | Going Away | Endpoint rời đi/server shutdown |
| 1002 | Protocol Error | Vi phạm giao thức WebSocket |
| 1003 | Unsupported Data | Loại dữ liệu không được hỗ trợ |
| 1008 | Policy Violation | Policy vi phạm, chẳng hạn authorization policy |
| 1009 | Message Too Big | Frame/message vượt giới hạn server |
| 1011 | Unexpected Condition | Server gặp lỗi ngoài dự kiến |
| 1006 | Abnormal Closure (reserved, không gửi trên wire) | Client nhận khi không có close frame hợp lệ |
| 1015 | TLS failure (reserved, không gửi trên wire) | Không gửi như close code của ứng dụng |

Đừng gửi 1006/1015 trong close frame. Các names/constants của `CloseStatus` hỗ trợ common cases, nhưng cần kiểm tra Javadoc version trước khi dùng constant cho từng code. Custom status không nên thay quy tắc RFC 6455.

### Đóng ở đâu cho hợp lý?

- Khi xử lý command rời room, có thể gửi acknowledgement cuối rồi `then(session.close(...))`.
- Khi input không hợp lệ nghiêm trọng hoặc payload quá lớn, có thể đóng với 1008/1009, tùy layer đã phát hiện lỗi.
- Khi server deploy/shutdown, gửi 1001 khi có thể rồi hủy sau timeout.
- Nếu client đã biến mất, không bảo đảm close frame được giao tận nơi.
- Reason có độ dài giới hạn UTF-8 và không được chứa secrets/stack trace/PII.

**WebSocket close code không giống HTTP status.** Sau HTTP 101, mã 1000/1008/1011 nằm trong WebSocket close frame; không phải một HTTP response `200/403/500` mới.

## 11. Backpressure trong WebSocket: cái gì được bảo đảm?

`session.receive()` là Flux, `session.send(Publisher)` nhận nguồn Reactive Streams. Điều này cho phép composition có demand/cancellation, nhưng không biến TCP/browser/WebSocket protocol thành một hàng đợi thông minh vô hạn.

- Server-side reactive writes có thể chậm theo I/O demand và network.
- Browser `WebSocket.send(...)` không có Reactive Streams `request(n)` API; gửi liên tục có thể tăng `bufferedAmount` phía client.
- Một message broker có thể đẩy nhanh hơn socket write; nếu queue không giới hạn, RAM tăng.
- `Sinks.Many` có thể báo `FAIL_OVERFLOW` khi giới hạn hàng đợi; phải xử lý thay vì bỏ qua.
- `Flux.onBackpressureBuffer()` không giới hạn có thể OOM.
- Một slow client không nên khiến toàn bộ cluster hoặc room lưu events vô hạn.
- `flatMap` có thể thay đổi thứ tự kết quả; dùng `concatMap` nếu thứ tự command/reply phải giữ nguyên.

### Một pipeline luôn có bounds

Đối với chat room:
- Mỗi kết nối có quota message/second và max payload bytes.
- Per-session outbound queue **bounded**.
- Policy khi đầy: drop old presence updates, coalesce status, disconnect slow client hoặc báo backpressure.
- Đừng drop durable business messages một cách im lặng.
- Với pub/sub, tách durable delivery/ACK/replay khỏi best-effort live notifications.
- Dùng transport-level ping/pong và idle eviction, không lưu session "ma" mãi.

**Reactive API không đồng nghĩa mọi downstream non-blocking**: nếu trong `map` gọi JDBC, filesystem hoặc HTTP client đồng bộ, event loop vẫn bị chặn.

## 12. Đừng block WebFlux event loop

Phản ví dụ:

```java
// PHẢN VÍ DỤ: blocking database call trên event-loop.
return session.send(session.receive()
        .map(message -> {
            String text = message.getPayloadAsText();
            return session.textMessage(jdbcService.lookup(text));
        }));
```

`jdbcService.lookup` là giả định và có thể block. Khi hàng nghìn kết nối cùng nhận messages, blocking call ở event loop gây latency toàn server.

Nếu phải dùng API blocking tạm thời:

```java
Flux<WebSocketMessage> outbound = session.receive()
        .filter(m -> m.getType() == WebSocketMessage.Type.TEXT)
        .map(WebSocketMessage::getPayloadAsText)
        .concatMap(text ->
                Mono.fromCallable(() -> jdbcService.lookup(text))
                    .subscribeOn(Schedulers.boundedElastic()))
        .map(session::textMessage);

return session.send(outbound);
```

Đây là snippet cần `jdbcService` và import `Schedulers`. Nó **chuyển blocking work sang bounded workers**, không biến JDBC thành non-blocking. Dùng timeout, giới hạn concurrency, DB pool phù hợp; ưu tiên driver/client reactive nếu có lợi ích thực tế. Với cancellation sau khi blocking task đã chạy, không bảo đảm side effect JDBC tự rollback.

## 13. DataBuffer ownership, binary messages và ping/pong

### 13.1. WebSocketMessage mang payload DataBuffer

`WebSocketMessage` có type và payload. Trên Reactor Netty, một số buffers được **pool và reference-counted**. Nếu chỉ đọc text ngay trong synchronous `map`, chuyển sớm thành String giúp tránh giữ buffer lâu.

Phản ví dụ:

```java
// NGUY HIỂM nếu message/buffer bị giải phóng trước callback.
return session.receive()
        .delayElements(Duration.ofSeconds(1))
        .map(WebSocketMessage::getPayloadAsText)
        .then();
```

Nếu dùng async boundary giữ message gốc, buffer có thể không còn hợp lệ tại thời điểm đọc. Khi thực sự cần giữ buffer, dùng `retain`/`release` đúng ownership theo hướng dẫn Spring, ưu tiên API abstractions hỗ trợ. Đừng tự retain rồi quên release — pooled direct memory có thể leak.

### 13.2. Binary frames khác text frames

`session.binaryMessage(factory -> factory.wrap(byteArray))` tạo binary WebSocketMessage. Với binary payload lớn, tránh gọi `byte[]` khổng lồ không có max size. Khi nhận binary:
- Xác định compression/frame fragmentation policy.
- Validate content length, magic bytes/schema/CRC nếu cần.
- Không hiểu binary data như UTF-8 text.
- Giới hạn max frame/message bytes tại server WebSocket upgrade strategy, proxy và application.

### 13.3. Ping/Pong và heartbeats

WebSocket chuẩn có control frames PING/PONG và CLOSE. Heartbeat có thể giúp:
- Phát hiện half-open sockets.
- Giữ kết nối qua một số intermediary idle timeouts.
- Đo round-trip latency nếu protocol/implementation cho phép.

Nhưng **không giả định mọi Ping frame xuất hiện trong `session.receive()` ở dạng message application**: server runtime có thể xử lý Ping/Pong ở tầng transport và tự pong. Kiểm tra Netty/Jetty/Tomcat strategy tương ứng. Nếu client browser không expose Ping/Pong API, có thể dùng **application-level heartbeat** JSON/text message với type `ping`/`pong` (nhưng đó là protocol riêng, không phải RFC control frame).

## 14. Project Maven runnable: Echo + Ticks WebSocket endpoints

Project minh họa ba đặc điểm:

1. `/ws/echo`: client gửi hai text messages, server echo lại; inbound điều khiển outbound.
2. `/ws/ticks`: server phát đúng ba thông báo định kỳ, không cần message đầu vào.
3. `/api/status`: REST JSON thông thường, giúp thấy WebSocket và REST có thể cùng tồn tại trong một WebFlux application.

Hai WebSocket handlers được đăng ký qua `SimpleUrlHandlerMapping`. Trong Boot WebFlux bình thường, framework đã cấu hình `WebSocketHandlerAdapter`, không cần tự tạo adapter bean thứ hai.

### 14.1. Cấu trúc project

```text
webflux-websocket-demo/
├── pom.xml
└── src/
    ├── main/java/example/ws/
    │   ├── WebSocketDemoApplication.java
    │   ├── EchoSocketHandler.java
    │   ├── TickSocketHandler.java
    │   ├── WebSocketRouting.java
    │   └── StatusController.java
    └── test/java/example/ws/
        └── WebSocketIntegrationTest.java
```

### 14.2. pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>
    <groupId>example</groupId>
    <artifactId>webflux-websocket-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <java.version>17</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

Không cần `spring-boot-starter-websocket` dành cho Servlet MVC stack trong ví dụ WebFlux này, và không thêm `spring-boot-starter-web` để tránh Boot ưu tiên MVC application type khi cả hai có mặt.

### 14.3. Main class

**`src/main/java/example/ws/WebSocketDemoApplication.java`**

```java
package example.ws;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WebSocketDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebSocketDemoApplication.class, args);
    }
}
```

### 14.4. Reactive echo handler

**`src/main/java/example/ws/EchoSocketHandler.java`**

```java
package example.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class EchoSocketHandler implements WebSocketHandler {

    @Override
    public Mono<Void> handle(WebSocketSession session) {

        Flux<WebSocketMessage> outbound = session.receive()
                .filter(message ->
                        message.getType()
                                == WebSocketMessage.Type.TEXT)
                // Đọc ngay khi DataBuffer còn hợp lệ.
                .map(WebSocketMessage::getPayloadAsText)
                .map(text -> text.length() > 256
                        ? "error:too-long"
                        : "echo:" + text)
                .map(session::textMessage);

        return session.send(outbound);
    }
}
```

**Điểm then chốt:** Chỉ **một** `receive()` và **một** `send(outbound)`, được nối trong cùng reactive pipeline. Không tự subscribe, không giữ WebSocketMessage vào cache, không gọi blocking API. Server giữ connection hoạt động trong lúc client gửi text và đóng khi lifecycle kết thúc.

Demo giới hạn **256 Java UTF-16 code units** để đơn giản, **không phải** giới hạn bytes theo RFC 6455. Trong production, cần max message bytes ở server, kiểm tra encoding/length và policy đóng khi dữ liệu vi phạm. Trả `error:too-long` như một text reply chỉ là ứng dụng demo, không phải chuẩn WebSocket protocol error.

Non-text messages bị filter bỏ qua, không làm echo; production protocol nên định nghĩa rõ binary/control handling và close status khi không hỗ trợ, không âm thầm bỏ mọi loại message mà client cần biết.

### 14.5. Periodic outbound handler

**`src/main/java/example/ws/TickSocketHandler.java`**

```java
package example.ws;

import java.time.Duration;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class TickSocketHandler implements WebSocketHandler {

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        Flux<WebSocketMessage> ticks =
                Flux.interval(Duration.ofMillis(60))
                    .take(3)
                    .map(i -> session.textMessage(
                            "tick-" + (i + 1)));

        return session.send(ticks);
    }
}
```

`Flux.interval` tạo các ticks theo Reactor scheduler, **không dùng `Thread.sleep` hoặc tạo một thread ngủ riêng cho mỗi socket**. `take(3)` bảo đảm finite output; sau ba messages, source complete và `send` hoàn tất. Lifecycle response/connection sẽ được framework xử lý; không kỳ vọng ticks tới client tại chính xác 60/120/180 ms vì scheduling/network jitter.

### 14.6. WebSocket route mapping

**`src/main/java/example/ws/WebSocketRouting.java`**

```java
package example.ws;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;

@Configuration
public class WebSocketRouting {

    @Bean
    HandlerMapping webSocketMapping(
            EchoSocketHandler echo,
            TickSocketHandler ticks) {

        return new SimpleUrlHandlerMapping(
                Map.of("/ws/echo", echo,
                       "/ws/ticks", ticks),
                -1);
    }
}
```

**Đọc cấu hình:** `SimpleUrlHandlerMapping` ánh xạ URL tới các instances `WebSocketHandler`. Order -1 thường cho mapping này ưu tiên trước annotated controller mappings ở cùng DispatcherHandler. Trong app Boot WebFlux có Java config mặc định, `WebSocketHandlerAdapter` đã được đăng ký; **không tạo thêm `WebSocketHandlerAdapter` bean chỉ vì chưa thấy class đó trong source**.

Ở ứng dụng có nhiều custom mappings, tránh giao nhau/overlap `/ws/**` với annotated controllers hoặc static fallback. Kiểm tra mapping order, HTTP handshake đúng và startup logs khi có collision.

### 14.7. REST status endpoint dùng chung application

**`src/main/java/example/ws/StatusController.java`**

```java
package example.ws;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatusController {

    @GetMapping("/api/status")
    public Map<String, String> status() {
        return Map.of("state", "ready");
    }
}
```

Nó không liên quan trực tiếp WebSocket session nhưng giúp minh họa **REST và WebSocket chung WebFlux DispatcherHandler**. Đối với `/ws/echo`, HTTP request thường thiếu `Upgrade` không biến thành một REST request trả JSON: handshake có thể bị từ chối.

## 15. Chạy project và thử bằng WebSocket client

Từ thư mục `webflux-websocket-demo` với Java 17+ và Maven:

```bash
mvn test
mvn spring-boot:run
```

Hai lệnh chạy theo thứ tự. Khi Boot mở cổng mặc định 8080:

### Test REST endpoint

```bash
curl -i 'http://localhost:8080/api/status'
```

Dự kiến body:

```json
{"state":"ready"}
```

### Vì sao curl GET thường không test được WebSocket?

`curl -i http://localhost:8080/ws/echo` không gửi đầy đủ WebSocket handshake/Upgrade headers và không duy trì WebSocket frame protocol như một WebSocket client. Nó có thể nhận HTTP 4xx thay vì 101. Hãy dùng browser WebSocket API, `websocat`, `wscat` hoặc Spring `WebSocketClient`.

### CLI với websocat

Nếu đã cài `websocat`:

```bash
websocat ws://localhost:8080/ws/echo
```

Gõ:

```text
xin-chao
reactor
```

Client sẽ quan sát các thông điệp echo tương ứng:

```text
echo:xin-chao
echo:reactor
```

Đây là nội dung message expected, không khẳng định terminal client sẽ hiển thị chính xác prompt/local echo như nhau trên mọi OS. Nhấn Ctrl+C hoặc đóng client để kết thúc kết nối.

### CLI periodic ticks

```bash
websocat ws://localhost:8080/ws/ticks
```

Dự kiến server phát ba message:

```text
tick-1
tick-2
tick-3
```

Sau outbound source complete, phiên có thể được framework đóng; client cần xử lý close event. Không có thêm tick-4 vì `take(3)` giới hạn source.

## 16. Browser client: lắng nghe message và close

Ví dụ trên trang HTML chạy tại origin được server cho phép, browser hỗ trợ `WebSocket`:

```javascript
const socket = new WebSocket("ws://localhost:8080/ws/echo");

socket.addEventListener("open", () => {
  console.log("socket open");
  socket.send("hello");
  socket.send("Spring WebFlux");
});

socket.addEventListener("message", event => {
  console.log("received:", event.data);
});

socket.addEventListener("close", event => {
  console.log("closed:", event.code, event.reason);
});

socket.addEventListener("error", () => {
  console.error("WebSocket transport error");
});
```

Khi thành công, browser console có thể hiển thị:

```text
socket open
received: echo:hello
received: echo:Spring WebFlux
```

Client chưa chủ động close, nên server echo tiếp tục giữ session cho đến khi browser/tab/network đóng. Trong production nên đóng socket khi component unmount/page close và có connection reconnect/heartbeat policy.

**HTTPS page:** từ một trang `https://`, dùng WebSocket bảo mật `wss://` để tránh mixed-content failure và gửi traffic qua TLS. Browser sẽ tự tạo Upgrade/handshake headers; không tự viết `Sec-WebSocket-Key`.

### Cẩn thận với bufferedAmount

Browser `socket.send(...)` có thể xếp outbound bytes vào buffer; `socket.bufferedAmount` cho biết số bytes đã queue ở thời điểm đọc. Nếu producer phát quá nhanh, không gửi vô hạn không kiểm soát:

```javascript
if (socket.readyState === WebSocket.OPEN &&
    socket.bufferedAmount < 64 * 1024) {
  socket.send("presence:update");
}
```

Đây là ví dụ **policy best-effort** cho một loại message có thể drop/coalesce, không dùng để làm mất durable business commands. Browser API này không cho full Reactive Streams backpressure; cần ứng dụng tự thiết kế ACKs/rate limits khi messages quan trọng.

## 17. Integration tests: handshake, echo, ticks và REST

`WebTestClient` hữu ích cho REST, nhưng WebSocket frames cần **WebSocket client**. Test dưới đây khởi động Spring Boot trên random port rồi dùng `ReactorNettyWebSocketClient` kết nối thật.

**`src/test/java/example/ws/WebSocketIntegrationTest.java`**

```java
package example.ws;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment =
        SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketIntegrationTest {

    @LocalServerPort
    int port;

    private URI ws(String path) {
        return URI.create(
                "ws://localhost:" + port + path);
    }

    @Test
    void echoRepliesForTwoTextMessages() {
        ReactorNettyWebSocketClient client =
                new ReactorNettyWebSocketClient();
        AtomicReference<List<String>> replies =
                new AtomicReference<>();

        client.execute(ws("/ws/echo"), session -> {
            Mono<Void> sending = session.send(
                    Flux.just("hello", "reactor")
                            .map(session::textMessage));

            Mono<Void> receiving = session.receive()
                    .map(WebSocketMessage::getPayloadAsText)
                    .take(2)
                    .collectList()
                    .doOnNext(replies::set)
                    .then();

            return Mono.when(sending, receiving)
                    .then(session.close());
        }).block(Duration.ofSeconds(5));

        assertEquals(
                List.of("echo:hello", "echo:reactor"),
                replies.get());
    }

    @Test
    void periodicHandlerEmitsExactlyThreeTicks() {
        ReactorNettyWebSocketClient client =
                new ReactorNettyWebSocketClient();
        AtomicReference<List<String>> ticks =
                new AtomicReference<>();

        client.execute(ws("/ws/ticks"), session ->
                session.receive()
                        .map(WebSocketMessage::getPayloadAsText)
                        .take(3)
                        .collectList()
                        .doOnNext(ticks::set)
                        .then())
              .block(Duration.ofSeconds(5));

        assertEquals(
                List.of("tick-1", "tick-2", "tick-3"),
                ticks.get());
    }

    @Test
    void ordinaryRestRequestStillWorks() {
        WebTestClient http = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();

        http.get().uri("/api/status")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.state").isEqualTo("ready");
    }

    @Test
    void plainHttpGetCannotReplaceWebSocketHandshake() {
        WebTestClient http = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();

        http.get().uri("/ws/echo")
                .exchange()
                .expectStatus().is4xxClientError();
    }
}
```

Bộ test có **4 methods**. Hai methods kiểm tra WebSocket frames thực tế; một method xác nhận REST/JSON cùng server; một method xác nhận GET thường không tương đương handshake. `block(Duration.ofSeconds(5))` **chỉ chạy trên JUnit test thread**, không nằm trong WebFlux event-loop server code. Trong app production, không dùng `block()` trong handlers.

**Giải thích `Mono.when(sending, receiving)`:** nó subscribe đồng thời vào luồng gửi của client và luồng nhận replies, rồi complete khi cả hai hoàn tất. Sau đó client chủ động gửi close. Nếu chỉ `sending.then(receiving)`, việc subscribe receive sau khi send xong đôi khi làm test phụ thuộc buffering/timing; composition đồng thời rõ lifecycle hơn.

**Không hardcode timing chính xác:** tick có thể trễ do scheduler/network; test khẳng định thứ tự/nội dung chứ không khẳng định mỗi tick xuất hiện đúng 60 ms.

**Không phải một benchmark:** bốn tests không đo thousands concurrent sockets, proxy upgrade, TLS, idle timeouts, memory leaks, backpressure hoặc message delivery guarantee. Đây là runnable project được rà soát API tĩnh; **chưa chạy Maven trong phiên biên soạn Knowledge trên Google Sheets**.

### Nếu WebSocket test timeout

Kiểm tra:
- App thật chạy WebFlux, không phải Spring MVC do dependency selection.
- `/ws/echo` và `/ws/ticks` được `SimpleUrlHandlerMapping` đăng ký.
- `WebSocketHandlerAdapter` do WebFlux configuration cung cấp; custom WebFlux config không thay hỏng.
- Reverse proxy/ingress nếu có đang forward `Upgrade` và `Connection` headers.
- Server đã bind `@LocalServerPort` trước khi Reactor Netty client kết nối.
- Test client có thực sự subscribe vào `receive()` và send publisher hay không.
- Handler có return publisher đúng lifecycle thay vì `Mono.empty()` quá sớm.

## 18. Nhiều publishers outbound: thiết kế multiplex an toàn

Một chat connection có thể phải gửi:
- Direct reply của handler.
- Messages từ room.
- Presence notifications.
- Heartbeat/periodic status.

**Đừng mở bốn `session.send(...)` độc lập.** Một cách tổng quát là hợp nhất các sources thành một `Flux<WebSocketMessage>`:

```java
Flux<String> replies = ...;
Flux<String> roomEvents = ...;
Flux<String> presence = ...;

Flux<WebSocketMessage> output = Flux.merge(
        replies, roomEvents, presence)
        .map(session::textMessage);

return session.send(output);
```

Đây là **snippet kiến trúc**, với các publishers đã có từ application services. `Flux.merge` có thể interleave events; không bảo đảm toàn cục thứ tự giữa nhiều sources. Nếu cần ordering, thiết kế sequence numbers, partition keys hoặc serialize các commands qua một queue.

### Tránh nhận raw receive() hai lần

Sai:

```java
Mono<Void> a = session.receive().doOnNext(this::audit).then();
Mono<Void> b = session.send(session.receive().map(this::reply));
return Mono.when(a, b);
```

Đây là phản ví dụ; nếu nguồn inbound là one-shot/live stream, hai subscriptions có thể cạnh tranh/nhân đôi consumption. Cần **một inbound flow** rồi share/multicast có chủ đích hoặc route commands sang sink/processor đã được kiểm soát.

### Sinks.Many: phù hợp khi nào?

`Sinks.Many<T>` của Reactor có thể kết nối imperative events từ broker/callback tới reactive outbound publisher. Nhưng cần chọn mode:
- `multicast()` cho subscribers đang online.
- `replay()` khi cần giữ history, **có giới hạn retention**.
- `unicast()` với một subscriber khi phù hợp từng session.
- Overflow strategy/quota và xử lý `tryEmitNext` failures.

Không viết `sink.tryEmitNext(event)` rồi luôn giả định success. Nếu một consumer quá chậm, phải có policy do business chấp nhận. Đóng sink khi session kết thúc, cleanup subscriptions/room membership và không giữ references làm memory leak.

## 19. Auth, Origin, CSRF và quyền trên từng message

### Authentication ở handshake

Handshake vẫn là một HTTP request. Spring Security WebFlux có thể áp authentication ở tầng WebFilter/SecurityWebFilterChain và cấp Principal/metadata cho WebSocket handshake/session. Tuy nhiên **không bảo đảm** mọi header/token có cùng điều kiện như HTTP fetch:

- Browser WebSocket constructor **không cho tùy ý đặt Authorization header** như Java HTTP client thông thường.
- Cookies có thể được gửi tự động theo browser policy; phải cân nhắc SameSite, secure flag và session fixation.
- Query string token có nguy cơ lọt vào server/proxy logs/history, nên không đặt secrets dài hạn vào `?token=...`.
- Nếu dùng subprotocol để mang auth metadata, phải có protocol/design rõ và không lạm dụng header cơ chế thương lượng cho secrets.

### Origin và Cross-Site WebSocket Hijacking

WebSocket handshake có `Origin` trong browser. **CORS của WebSocket không phải hoàn toàn giống chính sách `fetch()`**: browser không tự áp cùng cơ chế preflight/response đọc như REST. Server phải kiểm tra Origin theo allowlist phù hợp để giảm rủi ro cross-site WebSocket hijacking (đặc biệt với cookie/session auth).

Spring WebFlux có thể cấu hình CORS/allowed origins cho WebSocket handler mapping, ví dụ thông qua `CorsConfigurationSource` trên handler hoặc `SimpleUrlHandlerMapping` URL CORS configuration theo Spring docs. **Không** cho mọi origins trong production chỉ để browser demo hoạt động. Kiểm tra browser thực tế, reverse proxy và WebSocket handshake response khi thêm origin policy.

### Authorization từng thông điệp

Một user được phép **mở kết nối** chưa chắc có quyền:
- Join một room cụ thể.
- Subscribe private event stream của người khác.
- Thực hiện `deleteDocument`/`transferFunds` message.
- Gửi với tốc độ cao hoặc payload lớn.

Thiết kế message envelope:

```json
{"type":"join","roomId":"room-17","requestId":"r-42"}
```

Đây chỉ là JSON protocol của **ứng dụng**, không phải payload chuẩn bắt buộc bởi WebSocket. Server decode schema, validate input, xác thực principal và **check ACL mỗi action**; tuyệt đối không dùng `userId` client gửi để giả danh. Với multi-tenant, route messages bằng tenant identity tin cậy từ authentication, không chỉ URL/body.

### CSRF và WebSocket

Nếu browser có session cookies, rủi ro cross-site handshake/commands phải được quản lý bằng Origin validation, authentication/session policy và message-level authorization. Không suy luận HTTP CSRF filter luôn kiểm tra every WebSocket message sau upgrade. Với STOMP/Spring Security messaging có những chính sách khác; bài này sử dụng low-level WebSocketHandler và **phải tự xác định protocol security**.

## 20. WebSocket vs HTTP streaming, SSE và NDJSON

| Đặc điểm | WebSocket | SSE | NDJSON qua HTTP |
| --- | --- | --- | --- |
| Giao thức | Upgrade sang WS (thường HTTP 101 với HTTP/1.1) | HTTP response streaming | HTTP response/request streaming |
| Hướng chính | **Hai chiều** | Server → client | Thường một chiều mỗi HTTP exchange |
| Framing | Message frames (text/binary/control) | `data:`/`event:`/blank line | Mỗi JSON record một dòng |
| Browser API | `WebSocket` | `EventSource` | `fetch` + ReadableStream/parser |
| Reconnect | Application quản lý | EventSource có auto-reconnect | Application quản lý |
| Event IDs | Application protocol | `id`/`Last-Event-ID` | Application protocol |
| Status lỗi sau khi kết nối | Message/close code | Streaming HTTP error constraints | Streaming HTTP error constraints |
| Client → server nhiều lệnh | Có trên cùng kết nối | Cần HTTP riêng | POST streaming có thể phù hợp, nhưng không full duplex messaging như WS |
| Binary payload | Có | Thường text/data framing | Có thể raw HTTP stream với media type phù hợp |
| Proxy support | Cần hỗ trợ Upgrade/WS | HTTP streaming, có thể buffer | HTTP streaming, có thể buffer |
| Phức tạp vận hành | Connections/rooms/security/retry | Heartbeat/replay/idle | Parsing/partial errors/resume |

**Không chọn WebSocket chỉ vì trả một Flux.** Với report 100.000 records chỉ download một chiều, NDJSON/HTTP file có thể đơn giản hơn. Với dashboard updates từ server, SSE đôi khi ít phức tạp hơn. Khi cần collaboration với commands hai chiều, WebSocket có lợi hơn.

## 21. Quy tắc xử lý error sau handshake

Sau khi HTTP status 101 thành công:

1. **Không còn một HTTP response 400/500 riêng cho mỗi WebSocket message**.
2. Muốn báo lỗi nghiệp vụ, gửi một message protocol, ví dụ:
   `{"type":"error","code":"invalid_command","requestId":"r-42"}`.
3. Nếu client vi phạm policy/protocol nghiêm trọng, dùng close status thích hợp.
4. Nếu server gặp lỗi bất ngờ, hạn chế leak exception messages/stack traces.
5. Nếu đã gửi một phần sequence rồi connection đứt, client cần biết **đã nhận đến message sequence nào**.
6. Các durable commands cần requestId/idempotency/ACK và retry policy.
7. Bảo đảm server không vô tình broadcast error chứa private room data cho user không được phép.
8. Logs/metrics nên gắn session ID, trace ID, auth/tenant theo chính sách riêng, không dump raw payload.

Ví dụ envelope cho reply:

```json
{"type":"ack","requestId":"r-42","sequence":182,"status":"accepted"}
```

Một ACK protocol cần quy định `accepted` nghĩa server đã queue hay đã commit durable storage. Không hứa exactly-once chỉ vì chạy trên cùng TCP connection; một ACK có thể mất khi network đóng ngay sau side effect, client retry có thể tạo duplicate.

## 22. Per-session state, rooms và horizontal scaling

Mỗi connection có một session riêng. Trong một application process, bạn có thể có hàng nghìn sessions, nhưng đừng chỉ giữ một `List<WebSocketSession>` static rồi broadcast bất kể user/tenant.

Vấn đề production:

- **Identity:** session → authenticated user/tenant/principal.
- **Rooms:** ai được join, subscribe, leave; validate ACL.
- **Presence:** join/leave có thể lặp khi reconnect; dùng TTL/dedup.
- **Memory:** bounded outbound queues, remove disconnected sessions.
- **Ordering:** sequence per room/user, order across producers/instances.
- **Horizontal scale:** message broker/pub-sub để fan-out giữa nodes; một process-local Map chỉ thấy sessions của node đó.
- **Sticky sessions:** WebSocket TCP connection ở node trong suốt phiên, nhưng reconnect có thể vào node khác.
- **Replay:** broker/event log/DB nếu cần không mất messages.
- **Security:** không gửi message riêng cho người nhận chỉ vì roomId client tự khai.
- **Observability:** active connections, handshakes, close code distribution, messages/sec, queue depth, late cancellations.

Nếu mở rộng qua nhiều node, việc dùng `Sinks.Many` local không tự đồng bộ subscriptions và history trên cluster. Cần shared broker hoặc architecture khác. Với durable chat, persistence và ACK protocol quan trọng hơn việc chỉ "đã đẩy WebSocket frame".

## 23. Health, idle timeouts, shutdown và reconnect

### Idle connections

Nếu client/server lâu không gửi:
- Load balancer/proxy có thể đóng idle connection.
- Socket half-open không nhất thiết được phát hiện ngay.
- TCP keepalive thường không đủ để đáp ứng một application heartbeat SLA.

Chọn ping/pong transport hoặc application heartbeat, timeout (ví dụ nếu không có pong/heartbeat sau N chu kỳ thì close). Đừng tạo heartbeat cực dày cho hàng chục nghìn clients khiến tài nguyên tăng không cần thiết.

### Graceful shutdown

Khi rolling deployment:
1. Ngừng chấp nhận handshakes mới.
2. Thông báo going-away/drain nếu ứng dụng cho phép.
3. Đóng các sessions hiện có trong một deadline.
4. Cleanup subscriptions/timers/queues/room memberships.
5. Client retry/reconnect có backoff + jitter.
6. Khôi phục subscriptions/replay theo cursor nếu cần.

### Browser reconnect

Không reconnect ngay lập tức trong một vòng lặp vô hạn khi server lỗi. Dùng exponential backoff + jitter, tối đa số attempts và phân biệt close code do policy từ chối với network failure. Khi user logout, **ngừng reconnect** và xóa state nhạy cảm. Nếu server yêu cầu reauthentication, không reuse expired token vô hạn.

## 24. Các lỗi thường gặp

| Triệu chứng | Nguyên nhân phổ biến | Cách khắc phục |
| --- | --- | --- |
| WebSocket 404 | Chưa đăng ký `SimpleUrlHandlerMapping` hoặc sai path | Đăng ký bean với URL chính xác, test handshake |
| Handshake không được upgrade | Thiếu Upgrade headers, proxy không pass WebSocket | Dùng WebSocket client và cấu hình proxy |
| Import nhầm WebSocketHandler | Dùng Servlet `org.springframework.web.socket` trong WebFlux | Dùng `org.springframework.web.reactive.socket` |
| Handle() return ngay, socket đóng sớm | Trả `Mono.empty()` nhưng work detached | Trả Mono<Void> đại diện lifecycle |
| Client send nhưng không thấy reply | Không return/subscribe đúng outbound `session.send` | Ghép receive→send thành một chain |
| Hai replies bị đảo | `flatMap` xử lý async out-of-order | `concatMap`/sequence IDs |
| Socket double write | Gọi nhiều `session.send(...).subscribe()` | Một outbound publisher/one send |
| Kết nối đứng mãi | Outbound vô hạn không gắn cancellation | Compose inbound close/termination |
| Memory leak khi client disconnect | Không remove registry/sink/timer | `doFinally` cleanup trên owner publisher |
| Duplicate handling | Subscribe receive() nhiều nơi | Một receive flow, multicast đúng |
| DataBuffer lỗi sau async delay | Pooled buffer bị giải phóng | Copy String/bytes sớm hoặc retain/release đúng |
| Direct memory leak | Retain buffer nhưng không release | DataBufferUtils và ownership policy |
| Text message bị bỏ | Filter chỉ chấp nhận TEXT nhưng client gửi binary | Định nghĩa protocol/type và close/error rõ |
| 1006 gửi từ server | Dùng reserved close code | Chỉ gửi hợp lệ RFC 6455 codes |
| HTTP error sau upgrade không tới client | WebSocket khác HTTP response | Message error hoặc close status |
| Browser handshake bị chặn | Origin allowlist/proxy/Security policy | Cấu hình và test đúng origins |
| Browser không đặt được Authorization | Native WebSocket constructor hạn chế headers | Dùng auth flow phù hợp, không query token tùy tiện |
| User truy cập room khác | Chỉ check auth lúc handshake | Authorization mỗi room/action |
| Slow client làm queue tăng | Unbounded Sinks/buffer | Bounded queues và overload policy |
| Event loop lag | Blocking JDBC/API trong handler | Reactive driver hoặc bounded offload |
| Ping không xuất hiện trong receive() | Runtime auto-handles control frames | Kiểm tra implementation; app heartbeat nếu cần |
| Client reconnect tạo duplicate commands | Thiếu requestId/idempotency | Dedup/ACK/persistence |
| WebSocket qua HTTPS lỗi | Dùng `ws://` trên secure page | `wss://` và TLS |
| Test WebTestClient GET không upgrade | HTTP client không làm WS frames | ReactorNettyWebSocketClient/WebSocketClient |
| Multi-node chat mất notifications | Process-local sessions không chia sẻ | Broker/event bus + durable offsets |
| Origin '*' với cookie auth | Cross-site WS hijacking | Strict allowlist và auth/ACL |
| Connection leak ở deploy | Không graceful drain | Close with deadline, cleanup và client reconnect |

## 25. Checklist review production

- [ ] Dùng `spring-boot-starter-webflux`, đúng reactive socket imports.
- [ ] `WebSocketHandlerAdapter` và WebSocket routing được WebFlux config phát hiện.
- [ ] Mỗi handler trả **một** `Mono<Void>` sở hữu lifecycle.
- [ ] `receive()` không bị subscribe nhiều lần ngoài ý định.
- [ ] Một outbound send Publisher/connection với multiplexing có kiểm soát.
- [ ] Không `block()` hoặc `subscribe()` tự phát trên Netty event loop.
- [ ] Không giữ pooled `WebSocketMessage` qua async boundary khi chưa retain/release.
- [ ] Có message type/schema validation và max bytes/frame/message.
- [ ] Authentication handshake và Origin allowlist đúng.
- [ ] Authorization từng command/room/tenant.
- [ ] Không truyền secrets lâu dài trong URL query/logs.
- [ ] Session-scoped resources và room memberships cleanup khi complete/error/cancel.
- [ ] Quy tắc close codes/heartbeats/idle detection rõ ràng.
- [ ] Per-client outbound queues bounded, có backpressure/overload policy.
- [ ] Không gửi HTTP response mới sau 101 upgrade.
- [ ] Retry/reconnect có backoff, idempotency và replay policy nếu cần.
- [ ] App có graceful shutdown/drain với nhiều kết nối lâu dài.
- [ ] Metrics active sockets, close codes, dropped messages, queue sizes và latency.
- [ ] Integration tests thực sự dùng WebSocketClient, không chỉ MockMvc/WebTestClient HTTP.
- [ ] Kiểm thử qua reverse proxy, TLS, load balancer và idle timeouts như production.

## 26. Bài tập thực hành

### Bài 1 — Ping-pong ở tầng application

Thêm lệnh text `"ping"` vào echo protocol, response text `"pong"`. Giải thích tại sao đây **khác** với WebSocket control frame PING/PONG. Test bằng WebSocketClient, gửi `ping` và assert `pong`.

### Bài 2 — Close với thông báo

Khi client gửi `"bye"`:
1. Gửi text acknowledgement `"goodbye"`.
2. Sau khi acknowledgement được ghi, close với `CloseStatus.NORMAL`.
3. Không gửi lại messages nếu session đã đóng.
4. Test close không gây double sends và nguồn inbound được hủy/cleanup.

### Bài 3 — Broadcast room

Thiết kế một room có 3 sessions:
- Authenticated user mới được join.
- Gắn sequence cho mỗi room event.
- Một inbound receive flow mỗi session.
- Một outbound send flow mỗi session.
- Bounded queue và overflow policy khi một user đọc chậm.
- Cleanup đúng khi cancel/error/normal close.
- Nếu scale 2 nodes, mô tả vì sao cần pub/sub hoặc broker.

### Bài 4 — Slow consumer

Giả lập một client không đọc outbound nhanh:
- Đo queue depth, active sessions, send completion latency.
- Thử overload policy (drop presence updates vs disconnect).
- Không làm mất durable payment/chat commands không có ACK.
- Xác minh sự khác biệt giữa backpressure Reactor và browser `bufferedAmount`.

### Bài 5 — Reverse proxy / TLS

Kết nối tới `wss://example.test/ws/echo` qua một trusted proxy có cấu hình Upgrade/Connection, TLS và idle timeout:
- Test HTTP 101 vs các handshake errors.
- Test browser Origin allowlist.
- Quan sát close 1000 vs abnormal disconnect.
- Test reconnect backoff khi server rolling deploy.

## 27. Câu hỏi tự kiểm tra

1. `WebSocketHandler.handle` nhận kiểu gì và trả kiểu gì?
2. `WebSocketSession.receive()` và `send(...)` trả những reactive types nào?
3. Vì sao WebSocket cần handshake 101 trước khi gửi frames?
4. WebSocket text messages có dùng newline delimiter như NDJSON không?
5. `WebSocketHandler` reactive khác Servlet WebSocketHandler ra sao?
6. Vì sao `return Mono.empty()` ngay có thể đóng session quá sớm?
7. Có nên gọi `session.send(...).subscribe()` trong handler không?
8. Nếu outbound là `session.receive().map(...)`, completion phụ thuộc điều gì?
9. Khi inbound/outbound độc lập, phải ghép termination ra sao?
10. Làm sao nhận biết client cancel, network error và normal close?
11. `doFinally` có gì hơn `doOnComplete` khi cleanup?
12. Tại sao `session.isOpen()` snapshot không đủ làm vòng lặp life-cycle?
13. Vì sao chỉ có một outbound writer cho một connection?
14. `flatMap` và `concatMap` khác nhau gì khi xử lý replies có thứ tự?
15. `WebSocketMessage.Type.TEXT` khác BINARY như thế nào?
16. Khi nào cần retain/release WebSocketMessage DataBuffer?
17. HTTP 400 có thể được gửi cho lỗi nghiệp vụ sau khi đã upgrade thành WebSocket không?
18. Code 1006 có được truyền trong close frame không?
19. Có thể dùng WebTestClient HTTP GET thay WebSocket client để kiểm chứng messages không?
20. Vì sao browser WebSocket API không hỗ trợ custom Authorization headers như fetch?
21. Origin validation có tác dụng gì với cookie-auth WebSocket?
22. Kiểm tra quyền ở handshake đã đủ cho room-specific commands chưa?
23. WebSocket có tự chống message floods, slow consumers và memory leak không?
24. `Flux.interval` có nghĩa mỗi session tạo một sleeping Java Thread riêng không?
25. Vì sao `boundedElastic` không biến JDBC thành non-blocking?
26. Với nhiều producer, nên gọi nhiều `send` hay merge về một outbound Flux?
27. Nếu room có nhiều server nodes, local `Map<sessionId,session>` có đủ broadcast không?
28. SSE/NDJSON có lợi thế nào khi client chỉ cần nhận dữ liệu một chiều?
29. Khi client reconnect sau disconnect, làm sao chống duplicate business commands?
30. Khi shutdown, server và client cần những quy tắc nào để không leak state?

**Gợi ý:** (1) WebSocketSession → Mono<Void>. (2) Flux<WebSocketMessage> và Mono<Void>. (3) Đổi từ HTTP sang WS protocol. (4) Không; WS tự có framing. (5) Reactive Mono/Flux vs Servlet callbacks. (6) Lifecycle Mono kết thúc ngay. (7) Không. (8) Inbound close/error/cancel. (9) Một Mono<Void> kết hợp đúng signals. (10) Reactive signals/closeStatus/metrics. (11) Hỗ trợ ERROR/CANCEL. (12) Race/CPU polling. (13) Tránh competing writers và ordering lỗi. (14) concatMap giữ thứ tự. (15) Nội dung/framing/payload handling khác. (16) Giữ pooled buffer qua async boundary. (17) Không, cần WS message/close code. (18) Không. (19) Không. (20) Native browser constructor không cho tùy biến request headers. (21) Chống cross-site WS hijacking. (22) Chưa. (23) Không, cần bounds/policies. (24) Không. (25) Chỉ offload blocking. (26) Một merged Flux. (27) Không, cần bus/broker. (28) Ít phức tạp hơn cho one-way. (29) requestId + ACK + idempotency. (30) Drain/close/cleanup/backoff/replay.

## 28. Tài liệu tham khảo

- [Spring Framework — WebFlux WebSocket API](https://docs.spring.io/spring-framework/reference/6.1/web/webflux-websocket.html)
- [Spring Framework 6.1 — WebSocketHandler Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/reactive/socket/WebSocketHandler.html)
- [Spring Framework 6.1 — WebSocketSession Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/reactive/socket/WebSocketSession.html)
- [Spring Framework 6.1 — WebSocketMessage Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/reactive/socket/WebSocketMessage.html)
- [Spring Framework 6.1 — CloseStatus Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/reactive/socket/CloseStatus.html)
- [Spring Framework 6.1 — WebSocketHandlerAdapter Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/reactive/socket/server/support/WebSocketHandlerAdapter.html)
- [Spring Framework — WebFlux Configuration](https://docs.spring.io/spring-framework/reference/web/webflux/config.html)
- [Spring Framework — WebFlux Security](https://docs.spring.io/spring-security/reference/reactive/configuration/webflux.html)
- [Spring Framework — Reactive Data Buffers](https://docs.spring.io/spring-framework/reference/core/databuffer-codec.html)
- [Project Reactor Reference](https://projectreactor.io/docs/core/release/reference/)
- [RFC 6455 — The WebSocket Protocol](https://www.rfc-editor.org/rfc/rfc6455.html)
- [MDN — WebSocket Web API](https://developer.mozilla.org/en-US/docs/Web/API/WebSocket)
- [MDN — Writing WebSocket client applications](https://developer.mozilla.org/en-US/docs/Web/API/WebSockets_API/Writing_WebSocket_client_applications)

**Tổng kết:** Spring WebFlux `WebSocketHandler` xử lý **một kết nối WebSocket đã handshake**, nhận `WebSocketSession` và trả **`Mono<Void>` mô tả toàn bộ vòng đời**. `receive()` là `Flux<WebSocketMessage>` inbound; `send(Publisher<WebSocketMessage>)` là outbound writer. Một echo handler dùng `session.send(session.receive().map(...))` để ghép hai hướng thành một flow; các ứng dụng nhiều producer hoặc stream dài hạn phải thiết kế termination, cancellation, close codes, message ordering, backpressure và resource cleanup. WebSocket khác SSE/NDJSON vì là **hai chiều và có message framing riêng**, nhưng không tự cung cấp authentication, authorization, durable delivery hoặc cluster broadcast. Hãy kiểm thử qua WebSocket client thật và bảo vệ mỗi kết nối bằng giới hạn, security và lifecycle policy.


</details>

- [Quay lại đầu trang](#back-to-top)
