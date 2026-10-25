<a id="back-to-top"></a>

# WebSocket và STOMP

## Menu
- [So sánh polling, SSE, WebSocket và chọn cơ chế cập nhật](#websocket-stomp-001)
- [WebSocket handshake, subprotocol và vòng đời kết nối](#websocket-stomp-002)
- [WebSocketHandler, TextWebSocketHandler và đăng ký endpoint Servlet](#websocket-stomp-003)
- [HandshakeInterceptor, kiểm tra origin và thông tin người dùng](#websocket-stomp-004)
- [Giới hạn message, gửi đồng thời, timeout và ngắt kết nối](#websocket-stomp-005)
- [SockJS fallback và các ràng buộc khi đi qua proxy](#websocket-stomp-006)
- [STOMP, destination và luồng thông điệp qua các channel của Spring](#websocket-stomp-007)
- [@MessageMapping, @SendTo, @SendToUser và SimpMessagingTemplate](#websocket-stomp-008)
- [Simple broker, broker relay và lựa chọn khi mở rộng hệ thống](#websocket-stomp-009)
- [Xác thực, phân quyền destination, heartbeat và phục hồi kết nối](#websocket-stomp-010)
- [Xử lý ngoại lệ, sự kiện kết nối, giám sát và kiểm thử WebSocket](#websocket-stomp-011)

## <a id="websocket-stomp-001">So sánh polling, SSE, WebSocket và chọn cơ chế cập nhật</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh Polling, Server-Sent Events (SSE) và WebSocket: chọn cơ chế cập nhật trong Spring

## 1. Bài toán cần giải quyết

Một trang web thường phải biết **dữ liệu trên server vừa thay đổi**: đơn hàng chuyển trạng thái, dashboard có số liệu mới, hệ thống phát thông báo, tiến độ xử lý công việc, chat xuất hiện tin nhắn. Có ba cơ chế phổ biến:

- **Polling:** client chủ động gửi HTTP request lặp lại để hỏi xem có thông tin mới không.
- **Server-Sent Events (SSE):** client mở một HTTP connection dài hạn; server gửi liên tiếp các sự kiện văn bản từ server đến client khi có thay đổi.
- **WebSocket:** client và server duy trì kết nối hai chiều; cả hai có thể gửi message bất cứ lúc nào. Trong Spring, **STOMP over WebSocket** thường được dùng để thêm khái niệm destination, subscription và message routing.

Không có cơ chế nào tối ưu cho mọi tình huống. Quyết định đúng phụ thuộc vào **hướng dữ liệu, độ trễ chấp nhận được, số kết nối, tải request, đặc tính mạng/proxy, bảo mật, khả năng phục hồi và yêu cầu scale**.

Ví dụ: ứng dụng hiển thị trạng thái xử lý một tác vụ sau mỗi 10 giây không nhất thiết cần WebSocket. Ngược lại, một phòng chat có nhiều luồng trao đổi hai chiều không nên mặc định dùng polling 1 giây chỉ vì dễ triển khai.

## 2. Ba cơ chế hoạt động như thế nào?

### 2.1. Polling (short polling)

Client thực hiện HTTP GET theo chu kỳ, ví dụ 5 giây/lần:

~~~text
Browser                         Server
   | --- GET /api/status ------> |
   | <------ 200 current -------- |
   |       (đợi 5 giây)          |
   | --- GET /api/status ------> |
   | <------ 200 current -------- |
   |       (đợi 5 giây)          |
   | --- GET /api/status ------> |
   | <------ 200 changed -------- |
~~~

Ưu điểm: tương thích HTTP truyền thống, đi qua các hệ thống auth/proxy/load balancer quen thuộc, dễ cache và monitor, không phải giữ một connection cho mỗi client trong suốt thời gian phiên. Nhược điểm: request có thể trả về dữ liệu không đổi; tần suất càng cao thì HTTP traffic càng lớn, còn tần suất thấp thì người dùng thấy dữ liệu cập nhật chậm.

Nếu khoảng polling là `T` giây, độ trễ từ khi server đổi dữ liệu đến khi client biết có thể gần `T` giây; với thời điểm thay đổi phân bố đều so với lịch hỏi, trung bình xấp xỉ `T/2`, **chưa tính network/server latency**. Có `N` client cùng polling mỗi `T` giây, số request/giây xấp xỉ `N/T`. Ví dụ 10.000 client, 10 giây/lần ≈ 1.000 request/giây, ngay cả khi dữ liệu không đổi.

**Long polling** là biến thể: request được server giữ chờ đến khi có thay đổi hoặc hết timeout; client sau đó gọi lại. Nó giảm lượng response rỗng khi ít sự kiện nhưng phức tạp hơn short polling ở timeout, cancellation, giữ request đang chờ và xử lý burst. Long polling **không phải SSE**: mỗi lần có phản hồi, request HTTP kết thúc; SSE có thể đẩy nhiều event trên một response đang mở.

Khi dùng polling, có thể tối ưu bằng `ETag` + `If-None-Match`, `Last-Modified` + `If-Modified-Since` hoặc trả version/timestamp; nếu không đổi, trả `304 Not Modified` để tiết kiệm body. `304` vẫn tiêu tốn một lượt HTTP request và xử lý request.

### 2.2. SSE (Server-Sent Events)

Browser dùng `EventSource` mở HTTP GET với `Accept: text/event-stream`. Server giữ response đang mở, gửi các dòng văn bản đúng định dạng SSE, phân tách các event bằng dòng trống:

~~~text
Browser                         Server
   | --- GET /api/events -----> |
   | <---- HTTP 200 ------------ |
   |      Content-Type: text/event-stream
   | <---- id: 41 -------------- |
   |       event: update        |
   |       data: {"count":41}   |
   |                            |
   | <---- id: 42 -------------- |
   |       event: update        |
   |       data: {"count":42}   |
   |     (cùng HTTP response)   |
~~~

Mẫu SSE hợp lệ:

~~~text
id: 42
event: update
data: {"count":42}

: heartbeat

retry: 3000

~~~

Trong đó `data:` chứa nội dung (có thể nhiều dòng), `event:` là tên sự kiện, `id:` là ID mà trình duyệt ghi nhớ, `retry:` gợi ý thời gian reconnect tính bằng mili giây và dòng bắt đầu bằng dấu `:` là comment (thường dùng làm heartbeat). Kết thúc một event bằng **dòng trống**.

SSE theo chuẩn browser là **server → client**; khi client muốn gửi command vẫn dùng HTTP POST/fetch riêng. `EventSource` tự kết nối lại sau khi kết nối bị ngắt. Nếu trước đó server gửi `id:`, browser có thể gửi `Last-Event-ID` khi reconnect, **nhưng server phải tự có event log/cursor và logic replay thì mới khôi phục được event bỏ lỡ**. Chỉ thêm `id` vào response không tạo bảo đảm phát lại.

SSE sử dụng HTTP bình thường, đơn giản để tích hợp ứng dụng web hiện có. Tuy nhiên response dài hạn cần xử lý timeout, proxy buffering, heartbeat, giới hạn connection/stream và cleanup khi client rời đi. Với HTTP/1.1, trình duyệt thường giới hạn số connection đồng thời theo origin (hay gặp khoảng 6); HTTP/2 có giới hạn streams khác và cần cấu hình thực tế.

### 2.3. WebSocket và STOMP

WebSocket mở phiên giao tiếp full-duplex. Sau handshake HTTP thích hợp, cùng một kết nối cho phép gửi message **hai chiều** mà không cần mỗi message là một HTTP request/response.

~~~text
Browser                      WebSocket server
   | --- handshake HTTP -----> |
   | <--- switched protocols - |
   | ------ "join room" -----> |
   | <----- "member joined" -- |
   | <----- "new message" ----- |
   | ------ "send text" ------> |
   | <----- "new message" ----- |
   |       (kết nối dài hạn)    |
~~~

**Raw WebSocket** là giao thức truyền frame; nó **không tự cung cấp** model topic/subscription, persistence, queue, replay hay authorization cho từng loại tin. `STOMP` là giao thức messaging cấp ứng dụng có frame như `CONNECT`, `SUBSCRIBE`, `SEND`, `MESSAGE`, `DISCONNECT`; nó có thể chạy **trên WebSocket** và được Spring hỗ trợ bằng `@EnableWebSocketMessageBroker`, `@MessageMapping`, `SimpMessagingTemplate`.

Một ví dụ đường dẫn STOMP trong Spring:

~~~text
Client connects ws://host/ws
  --> CONNECT (STOMP)
  --> SUBSCRIBE destination:/topic/orders
Client sends SEND destination:/app/command
  --> Spring @MessageMapping("/command")
  --> server sends to /topic/orders
  --> broker forwards MESSAGE to subscribers
~~~

**Chú ý:** `new WebSocket("/ws")` kết nối bằng WebSocket thuần không tự tạo hoặc xử lý các STOMP frames. Để nói chuyện với endpoint STOMP, browser phải dùng STOMP client (ví dụ `@stomp/stompjs`) hoặc tự thực hiện giao thức STOMP. Ngược lại, endpoint raw `TextWebSocketHandler` không tự hiểu STOMP.

WebSocket không tự động reconnect/replay ở cấp chuẩn browser WebSocket; cần client library hoặc code tự kết nối lại, heartbeat, resume state và xử lý duplicate/missing event.

## 3. Bảng so sánh thực tế

| Tiêu chí | Polling | SSE | WebSocket / STOMP |
| --- | --- | --- | --- |
| Chiều thông tin cập nhật | Client hỏi, server trả lời | Server → client trên một HTTP response; client gửi lệnh bằng HTTP riêng | Full-duplex hai chiều |
| Kết nối | HTTP request lặp lại | HTTP streaming dài hạn | Kết nối WebSocket dài hạn |
| Độ trễ khi có dữ liệu mới | Phụ thuộc chu kỳ polling | Thường gửi ngay khi có sự kiện | Thường gửi ngay khi có message |
| Traffic khi dữ liệu ít thay đổi | Nhiều request rỗng | Nhỏ nếu heartbeat vừa đủ | Nhỏ nếu heartbeat vừa đủ |
| Tích hợp trình duyệt | `fetch`, thư viện HTTP | `EventSource` | `WebSocket`; STOMP cần thêm client |
| Gửi từ browser lên server | HTTP GET/POST bình thường | HTTP request riêng | Qua WebSocket trực tiếp hoặc STOMP `SEND` |
| Reconnect tự động | Lịch polling do ứng dụng quản lý | `EventSource` tự reconnect | Ứng dụng/thư viện phải quản lý |
| Replay thông tin bỏ lỡ | GET lại current snapshot hoặc version | Dùng `id`/`Last-Event-ID` **nếu backend có replay** | Cần cơ chế replay riêng |
| Proxy/load balancer | Thường đơn giản nhất | Cần hỗ trợ streaming, không buffer, timeout phù hợp | Cần hỗ trợ WebSocket upgrade/tunnel và idle timeout |
| Dữ liệu binary | HTTP hỗ trợ | SSE là văn bản UTF-8 | WebSocket có text và binary frames; STOMP thường dùng payload messaging |
| Scale | Scale HTTP stateless dễ hơn, nhưng tốn request | Scale các stream dài hạn và fan-out | Scale connection, subscription, broker và fan-out |
| Phù hợp | Dữ liệu ít thay đổi, chấp nhận trễ | Thông báo/live feed một chiều | Chat, tương tác hai chiều, cộng tác thời gian thực |

Bảng chỉ mô tả đặc tính nền tảng. **Không nên khẳng định WebSocket luôn nhanh hơn SSE**: khi hai bên đều duy trì connection ổn định và chỉ server gửi update, khác biệt latency có thể nhỏ hơn chi phí thiết kế, vận hành và recovery. Hiệu năng thực tế phải đo cùng payload, số người dùng, mạng và server.

## 4. Quy tắc quyết định theo tình huống

1. **Báo cáo, trạng thái tác vụ, dashboard cập nhật mỗi 10–60 giây:** bắt đầu từ polling. Nếu thay đổi rất thưa, tăng khoảng hỏi, dùng version/ETag và dừng khi tab ẩn nếu phù hợp.
2. **Thống kê trực tiếp, feed tin, log tiến độ, thông báo server gửi tới browser:** cân nhắc SSE trước; browser nhận sự kiện một chiều và vẫn dùng HTTP POST để gửi tác vụ.
3. **Chat, multiplayer, collaborative editing, tương tác thời gian thực hai chiều liên tục:** cân nhắc WebSocket. Với các kênh publish/subscribe và routing, STOMP giúp cấu trúc message theo destination.
4. **Nhiều instance server, yêu cầu broadcast toàn hệ thống:** SSE/WebSocket đều cần kiến trúc pub/sub hoặc event bus chung; STOMP simple broker chỉ sống trong bộ nhớ từng instance, **không đủ cho clustering**. Chọn broker relay hoặc kiến trúc fan-out phù hợp.
5. **Cần chắc chắn không mất message, thứ tự và khả năng resume:** xác định delivery semantics riêng (event ID, log bền vững, cursor, ACK, deduplication). Không cơ chế transport nào mặc định thay thế hàng đợi bền vững/database.
6. **Client có thể offline lâu:** khi quay lại nên đồng bộ snapshot và delta từ nguồn bền vững; duy trì WebSocket không thay thế push notification khi trình duyệt/app không hoạt động.

### Một sơ đồ quyết định

~~~text
Cần cập nhật dữ liệu trên browser?
          |
          v
Client có cần gửi message tương tác liên tục, độ trễ thấp hai chiều?
       /                              \
     Có                              Không
     |                                 |
  WebSocket                       Chấp nhận trễ vài giây/phút?
     |                              /              \
  Cần topic/room?                 Có              Không
   /       \                      |                |
  Có      Không                Polling             SSE
   |        |                  (ETag/version)      (+replay nếu cần)
 STOMP   Raw WebSocket
~~~

Đừng chọn theo tên công nghệ “real-time”. Hãy chốt SLO cụ thể như `p95 update latency < 2s`, số client đồng thời, tỉ lệ event/client, payload mỗi event, mức độ chịu mất dữ liệu, retry và chi phí infra rồi mới thử nghiệm.

## 5. Spring Framework: dùng API nào?

### Polling trong Spring MVC

Một endpoint REST trả current snapshot:

~~~java
@RestController
@RequestMapping("/api")
class StatusController {
    @GetMapping("/status")
    Status status() {
        return statusService.current();
    }
}
~~~

`Status` và `statusService` là thành phần ứng dụng cần có; đoạn trên minh họa thiết kế endpoint. Client gọi `GET /api/status` theo chu kỳ. Controller không cần giữ connection hoặc subscription dài hạn giữa các lần gọi.

### SSE trong Spring MVC và WebFlux

Trong **Spring MVC**, dùng `SseEmitter`:

~~~java
@GetMapping(path = "/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter events() {
    SseEmitter emitter = new SseEmitter(60_000L);
    registerAndSendUpdates(emitter);
    return emitter;
}
~~~

`registerAndSendUpdates` là logic tùy chỉnh; khi client disconnect/error/timeout phải gỡ emitter ra khỏi registry và giải phóng tài nguyên. Spring MVC sử dụng Servlet async để giữ response mở; ghi response SSE trong Servlet stack **vẫn có thể dùng blocking thread**. Không gửi message từ event loop reactive sang một API blocking mà không có chiến lược xử lý phù hợp.

Trong **Spring WebFlux**, có thể trả `Flux<ServerSentEvent<T>>`:

~~~java
@GetMapping(path = "/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<Update>> events() {
    return updatesFlux.map(update ->
            ServerSentEvent.<Update>builder()
                    .id(Long.toString(update.sequence()))
                    .event("update")
                    .data(update)
                    .build());
}
~~~

Đoạn WebFlux trên là minh họa; `updatesFlux` phải là publisher thật do hệ thống quản lý. Cần thiết kế phát sự kiện tới nhiều subscriber, backpressure và xử lý subscriber chậm. Đừng lấy một `Flux` chỉ có một subscriber rồi giả định nó tự broadcast.

### WebSocket STOMP trong Spring

Tạo cấu hình message broker:

~~~java
@Configuration
@EnableWebSocketMessageBroker
class SocketConfig implements WebSocketMessageBrokerConfigurer {
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic");
    }
}
~~~

`/ws` là **đường dẫn handshake WebSocket**, `/app` là tiền tố route message từ client vào các `@MessageMapping` trong Spring, còn `/topic` là tiền tố broker chuyển message tới các subscribers. Tên `/topic` là quy ước ở simple broker, không phải bảo đảm durability.

Message đến `/app/command` có thể được xử lý bằng:

~~~java
@Controller
class CommandHandler {
    private final SimpMessagingTemplate template;

    CommandHandler(SimpMessagingTemplate template) {
        this.template = template;
    }

    @MessageMapping("/command")
    void command(Command cmd) {
        template.convertAndSend("/topic/updates", cmd);
    }
}
~~~

Đây là snippet minh họa; cần model `Command`. Với production, **xác thực/ủy quyền theo user, destination và hành động** trước khi chuyển bất kỳ nội dung client nào sang topic. Không tin rằng một topic có tên khó đoán là một hàng rào bảo mật.

## 6. Demo hoàn chỉnh Java 17+: một server cho cả ba mô hình

Ví dụ dưới dùng **Spring Boot 3.x chạy trên Spring MVC**. Một tác vụ thay đổi trạng thái mỗi 5 giây. Client có thể:

- Poll `GET /api/status` để lấy snapshot mới nhất.
- Subscribe SSE `GET /api/events` để nhận event tự động.
- Dùng STOMP qua WebSocket `/ws`, subscribe `/topic/updates` và gửi command tới `/app/command`.

Các ví dụ chung một state in-memory và phục vụ **học tập trên một instance**, chưa triển khai persistence, authorization, backplane, replay hoặc load balancing.

### 6.1. `pom.xml`

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.0</version>
        <relativePath/>
    </parent>
    <groupId>demo</groupId>
    <artifactId>realtime-demo</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <properties>
        <java.version>17</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-websocket</artifactId>
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
~~~

WebSocket STOMP starter tích hợp với MVC; ví dụ này **không yêu cầu** `spring-boot-starter-webflux`. `SseEmitter` thuộc Spring MVC. Đường dẫn source đặt tại `src/main/java/demo/RealtimeDemoApplication.java`.

### 6.2. `src/main/java/demo/RealtimeDemoApplication.java`

~~~java
package demo;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;

@SpringBootApplication
@EnableScheduling
public class RealtimeDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(RealtimeDemoApplication.class, args);
    }
}

record Update(long sequence, String state) {}
record Command(String text) {}

@Component
class UpdatePublisher {
    private final AtomicLong counter = new AtomicLong();
    private final AtomicReference<Update> current =
            new AtomicReference<>(new Update(0, "READY"));
    private final CopyOnWriteArrayList<SseEmitter> emitters =
            new CopyOnWriteArrayList<>();
    private final SimpMessagingTemplate broker;

    UpdatePublisher(SimpMessagingTemplate broker) {
        this.broker = broker;
    }

    Update current() {
        return current.get();
    }

    SseEmitter openStream() {
        // Kết nối SSE tự timeout sau 60 giây;
        // EventSource trên browser có thể mở lại.
        SseEmitter emitter = new SseEmitter(60_000L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));

        send(emitter, current());
        return emitter;
    }

    @Scheduled(initialDelay = 5_000, fixedRate = 5_000)
    public void tick() {
        publish("tick");
    }

    void publish(String state) {
        Update update = new Update(counter.incrementAndGet(), state);
        current.set(update);

        for (SseEmitter emitter : emitters) {
            send(emitter, update);
        }

        // Phát tới client STOMP subscribe /topic/updates.
        broker.convertAndSend("/topic/updates", update);
    }

    private void send(SseEmitter emitter, Update update) {
        try {
            emitter.send(SseEmitter.event()
                    .id(Long.toString(update.sequence()))
                    .name("update")
                    .data(update, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException failure) {
            emitters.remove(emitter);
            emitter.completeWithError(failure);
        }
    }
}

@RestController
class UpdatesController {
    private final UpdatePublisher publisher;

    UpdatesController(UpdatePublisher publisher) {
        this.publisher = publisher;
    }

    @GetMapping("/api/status")
    Update status() {
        return publisher.current();
    }

    @GetMapping(path = "/api/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream() {
        return publisher.openStream();
    }

    @MessageMapping("/command")
    void command(Command command) {
        publisher.publish("client:" + command.text());
    }
}

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic");
    }
}
~~~

Giải thích các phần quan trọng:

- `AtomicReference<Update>` lưu snapshot mới nhất cho polling; `AtomicLong` tạo sequence ID trong một tiến trình.
- `CopyOnWriteArrayList` chỉ hợp lý trong demo/số lượng listener nhỏ. Với lượng kết nối lớn, việc copy mảng khi thêm/xóa, broadcast tuần tự và write blocking có thể gây nghẽn; cân nhắc hệ thống fan-out chuyên dụng và kiểm soát slow clients.
- `@Scheduled` phát một update mỗi 5 giây. Trình duyệt SSE kết nối lại sau timeout và nhận snapshot hiện tại.
- Cả SSE và STOMP cùng nhận bản ghi từ `publish(...)`; request HTTP thường nhận snapshot từ `current()`.
- `SseEmitter.send(...)` có thể thất bại khi client rời đi; callback completion/error/timeout gỡ emitter. Với hệ thống thực, cần tránh gửi đồng thời và có chính sách write timeout/heartbeat/backpressure.
- `sequence` tăng để thuận tiện phát hiện khoảng trống. Demo **không lưu lịch sử** nên chưa thể dùng `Last-Event-ID` để phát lại các sequence bị bỏ lỡ.
- `@MessageMapping("/command")` minh họa đường **client → server** chỉ có ở kênh STOMP. Trong production phải validate kích thước/nội dung và kiểm tra quyền phát command.

### 6.3. Chạy và thử polling

Dùng JDK 17+ và Maven trong thư mục dự án:

~~~bash
mvn spring-boot:run
~~~

Trong terminal khác:

~~~bash
curl -s http://localhost:8080/api/status
~~~

Ngay khi server mới khởi động, **trước lần tick đầu tiên**, response dự kiến:

~~~json
{"sequence":0,"state":"READY"}
~~~

Sau một số lần tick, `sequence` và `state` thay đổi; không thể cố định một output sau khi server chạy lâu. Để gọi liên tục từ JavaScript browser:

~~~javascript
let active = true;

async function poll() {
    if (!active) return;
    try {
        const response = await fetch("/api/status");
        if (!response.ok) throw new Error("HTTP " + response.status);
        const update = await response.json();
        console.log("poll", update);
    } catch (error) {
        console.warn("poll failed", error);
    } finally {
        // setTimeout sau khi request kết thúc tránh chồng request
        // nếu server đáp chậm hơn khoảng polling.
        if (active) setTimeout(poll, 5000);
    }
}
poll();
// Khi rời trang/không cần nữa: active = false;
~~~

Với trang browser phục vụ từ **cùng origin** với ứng dụng, relative URL `/api/status` hoạt động trực tiếp. Nếu chạy JavaScript ở origin khác, dùng absolute URL và cấu hình CORS/auth tương ứng; không mở CORS tùy tiện cho hệ thống thật.

### 6.4. Thử SSE

Với command line:

~~~bash
curl -N http://localhost:8080/api/events
~~~

`-N`/`--no-buffer` giúp quan sát nội dung streaming. Một snapshot ban đầu và các update tiếp theo có dạng:

~~~text
id:0
event:update
data:{"sequence":0,"state":"READY"}

id:1
event:update
data:{"sequence":1,"state":"tick"}

~~~

Khoảng trắng/cách serialize của framework có thể khác một chút; trường quan trọng là `id`, `event`, `data` và dòng trống kết thúc event. Nếu kết nối sau lần tick, event đầu có thể không phải sequence 0.

Browser sử dụng:

~~~javascript
const stream = new EventSource("/api/events");
stream.addEventListener("update", event => {
    const update = JSON.parse(event.data);
    console.log("SSE", update.sequence, update.state);
});
stream.onerror = () => console.warn("SSE disconnected; browser may retry");

// Khi không còn cần cập nhật:
// stream.close();
~~~

Browser tự reconnect khi stream lỗi/đóng theo cách cần reconnect. Đừng giả định SSE `onerror` luôn có nghĩa ngừng kết nối vĩnh viễn; hãy xem trạng thái `readyState`. Để đảm bảo không mất sự kiện cần lưu event log và xử lý header `Last-Event-ID` khi client kết nối lại. Demo chỉ gửi **snapshot mới nhất** và không dùng header đó.

### 6.5. Thử STOMP over WebSocket

Cài thư viện frontend `@stomp/stompjs` (ví dụ `npm install @stomp/stompjs` trong ứng dụng JavaScript có bundler), sau đó dùng:

~~~javascript
import { Client } from "@stomp/stompjs";

const scheme = location.protocol === "https:" ? "wss" : "ws";
const client = new Client({
    brokerURL: scheme + "://" + location.host + "/ws",
    reconnectDelay: 3000,
    onConnect: () => {
        client.subscribe("/topic/updates", message => {
            const update = JSON.parse(message.body);
            console.log("STOMP", update.sequence, update.state);
        });

        client.publish({
            destination: "/app/command",
            body: JSON.stringify({ text: "refresh" })
        });
    },
    onStompError: frame => {
        console.error("Broker error", frame.headers["message"]);
    }
});
client.activate();

// Khi rời màn hình: await client.deactivate();
~~~

Khi kết nối và subscribe thành công, client bắt đầu nhận những lần `tick`. Lệnh `SEND` tới `/app/command` sẽ khiến server phát update `state="client:refresh"` lên cả SSE stream và STOMP topic. Nếu client gửi `SEND` trước khi subscribe, có thể bỏ lỡ message đầu tiên; ví dụ subscribe trước rồi mới publish.

**Không gửi STOMP SEND qua `new WebSocket(...)` rồi chỉ viết JSON thuần:** STOMP yêu cầu frame đúng protocol. Nếu cần raw WebSocket cho payload JSON, tạo handler raw khác bằng Spring WebSocket API; đó là thiết kế riêng.

## 7. Reconnect, nhất quán dữ liệu và sự kiện bị bỏ lỡ

Hệ thống cập nhật thật phải định nghĩa điều gì xảy ra khi browser ngủ, mất mạng hoặc server restart.

### Polling: đồng bộ theo phiên bản

Polling thường lấy **current snapshot**. Khi GET mới thành công, client biết trạng thái hiện tại nhưng không biết tất cả chuyển trạng thái trung gian. Nếu cần lịch sử, endpoint nên hỗ trợ `?since=<cursor>` hoặc gọi event history riêng. Cùng một dữ liệu có thể được nhận lặp lại; client có thể so version/ETag để không render thừa.

### SSE: `Last-Event-ID` và replay

Browser ghi nhận `id` và thường gửi `Last-Event-ID` khi tự reconnect. Server cần:

1. Lưu event có ID/offset nhất quán vào storage hoặc event log phù hợp.
2. Khi reconnect, đọc cursor client gửi và phát lại event sau cursor đó.
3. Chống trùng ở client (idempotent update) vì ranh giới disconnect/ack có thể gây gửi lại.
4. Phát hiện cursor đã quá cũ do retention, rồi yêu cầu tải lại snapshot.
5. Đảm bảo trình tự giữa **replay** và **live** để không bỏ mất sự kiện trong lúc chuyển đổi.

Trong Spring MVC, có thể đọc `@RequestHeader(value = "Last-Event-ID", required = false) String lastId` khi mở emitter; ví dụ demo chưa có event store nên không tự tuyên bố chính xác kiểu delivery "exactly once".

### WebSocket/STOMP: reconnect không bằng replay

WebSocket native không tự kết nối lại; `@stomp/stompjs` cho phép `reconnectDelay` và subscribe lại khi `onConnect` được gọi. Nhưng message gửi khi offline không tự xuất hiện sau reconnect nếu không có broker durable queue/replay hoặc API lấy delta. Đừng gán khả năng persistence, acknowledgement, transaction của **một broker STOMP đầy đủ** cho simple broker tích hợp.

Để đạt trải nghiệm ổn định có thể dùng mô hình chung: sau reconnect, client gọi HTTP để lấy snapshot và version hiện tại, rồi subscribe stream hoặc áp dụng delta; server phát sequence để phát hiện gap. Thiết kế thứ tự subscribe/snapshot phải xử lý race, không đơn giản “GET xong mới subscribe” khi event có thể xảy ra giữa hai bước.

## 8. Scale, tài nguyên và proxy

### Polling

Điểm nghẽn là **request rate** chứ không nhất thiết số socket mở. Tính `N / interval`, thêm jitter để tránh hàng vạn browser cùng thức dậy đúng một giây, và có backoff khi server lỗi. Tối ưu cache, ETag và chỉ poll khi tab đang hiện nếu nghiệp vụ cho phép. Polling cùng chu kỳ 1 giây cho hàng trăm nghìn client có thể đắt hơn các stream dài hạn.

### SSE

Mỗi stream giữ tài nguyên kết nối và state đăng ký; reverse proxy có thể buffer response nếu không cấu hình cho streaming. Cần timeout, heartbeat comment, event size giới hạn và cleanup khi disconnect. Spring MVC `SseEmitter` có thể sử dụng thread ghi blocking; thiết kế broadcast lớn hoặc client quá chậm cần cơ chế fan-out tách biệt. Nếu scale nhiều instance, event cần phân phối tới đúng instance đang giữ emitter (thường dùng event bus chung) hoặc một tầng stream gateway.

### WebSocket/STOMP

Mỗi kết nối giữ phiên lâu, có idle timeout, heartbeat, giới hạn message và số subscriber. Các vấn đề phải đo gồm active sessions, outbound channel queue, slow consumers, memory per connection, broker health và thời gian reconnect. Spring **simple broker** là in-memory, hỗ trợ một phần STOMP, không phù hợp clustering. Nếu nhiều node cùng phục vụ, dùng STOMP broker relay tới broker ngoài (ví dụ RabbitMQ/ActiveMQ có hỗ trợ STOMP), hoặc kiến trúc pub/sub khác được thiết kế cho nhiệm vụ.

### Proxy, load balancer và TLS

- Dùng HTTPS/WSS trên Internet, cấu hình certificate, origin, load balancer và idle timeout phù hợp.
- Với SSE, tắt hoặc điều chỉnh proxy buffering theo tuyến streaming; gửi heartbeat để phát hiện connection đã rớt.
- Với WebSocket, proxy phải cho phép handshake/tunnel thích hợp và timeout đủ dài; có thể cần session affinity hoặc broker chia sẻ, phụ thuộc thiết kế.
- Với polling, giữ API stateless nếu có thể; thêm rate limit và conditional requests.
- Với cả ba, **TLS, xác thực, ủy quyền và bảo vệ dữ liệu** là yêu cầu riêng, không do việc chọn transport tự đáp ứng.

## 9. Bảo mật và xử lý input

- **Polling/REST:** bảo vệ endpoint bằng Spring Security như HTTP API thông thường; áp dụng authorization theo user/tenant/resource, rate limit, cache headers để tránh lộ dữ liệu.
- **SSE:** HTTP request dùng session/cookie cùng origin theo chính sách browser; `EventSource` chuẩn không cho set tùy ý custom Authorization header như `fetch`. Nếu dùng cookie, cần kiểm soát CSRF cho thao tác thay đổi trạng thái (các lệnh POST riêng), SameSite, CORS/credentials, và không nhúng token nhạy cảm vào query string/log.
- **WebSocket:** kiểm tra Origin và xác thực ở handshake/messaging phù hợp; origin check **không thay thế** xác thực/ủy quyền. Với STOMP, kiểm soát CONNECT, SUBSCRIBE, SEND và quyền theo destination, user/tenant. Spring Security có cơ chế message authorization; chính sách cho CONNECT/CSRF cần được thiết kế đúng cho ứng dụng trình duyệt.
- **Payload:** giới hạn kích thước, validate JSON/commands, dùng `textContent` thay vì gắn trực tiếp dữ liệu không tin cậy bằng `innerHTML`. Không xem mọi subscriber trên một topic là có quyền đọc dữ liệu nhạy cảm.
- **Replay:** cursor và event history phải được lọc theo quyền người dùng; tránh cho phép client đặt cursor để đọc các event của tenant khác.

Đặc biệt trong hệ thống nhiều khách hàng, một topic toàn cục như `/topic/orders` có thể vô tình phát sự kiện tới sai đối tượng nếu ứng dụng thiếu message authorization hoặc phân tuyến theo người dùng. Quyền xem dữ liệu cần kiểm tra tại **nguồn phát, destination và đường đọc lịch sử**.

## 10. Các lỗi tư duy và lỗi triển khai thường gặp

1. **“Muốn real-time thì phải dùng WebSocket.”** Nếu chỉ server thông báo cho browser, SSE đơn giản hơn và có reconnect tích hợp; nếu update hiếm và chấp nhận trễ, polling càng dễ.
2. **“SSE có `id` là không mất message.”** Chỉ đúng nếu backend cài replay bền vững và xử lý race/cursor.
3. **“WebSocket tự quản lý topic và replay.”** Raw WebSocket không cung cấp broker; STOMP simple broker cũng không cho durable messaging/clustering.
4. **“Một server chạy thì nhiều replica cũng sẽ broadcast đúng.”** Listener SSE và simple STOMP broker là state cục bộ; muốn nhiều instance cần backplane hoặc broker.
5. **“Polling càng nhanh càng tốt.”** Interval nhỏ làm tăng request rate và peak load; cần chọn theo độ trễ nghiệp vụ và dùng jitter/backoff.
6. **“SSE không cần heartbeat.”** Kết nối im lặng có thể bị proxy hoặc mạng đóng; keepalive phù hợp giúp phát hiện ngắt.
7. **“Socket còn mở là client còn hoạt động tốt.”** Client có thể chậm, buffer outbound có thể lớn; phải giới hạn và thu thập metrics.
8. **“Cần song công thì dùng hai SSE connections.”** SSE một chiều; gửi ngược qua POST được nhưng nếu tương tác hai chiều liên tục, WebSocket/STOMP thường tự nhiên hơn.
9. **“Cứ thêm `withSockJS()` là WebSocket nhanh hơn.”** SockJS là lớp fallback transport/compatibility, không phải STOMP hay một tối ưu tốc độ mặc định. Dùng khi môi trường client/proxy cần fallback.
10. **“Trả `Flux` từ controller MVC là non-blocking toàn bộ.”** Servlet MVC vẫn có cơ chế ghi HTTP và executor riêng; hiểu threading model của stack đang dùng.

## 11. Checklist triển khai và đo kiểm

Trước khi release, hãy ghi rõ:

- **Luồng cập nhật:** server → client hay hai chiều? Một người, nhiều người, toàn hệ thống?
- **Độ trễ:** mục tiêu p95/p99 và tỉ lệ update/cấp số client tối đa?
- **Data semantics:** snapshot mới nhất đủ chưa, hay phải phát lại mọi event? Có cần thứ tự, dedup, idempotency?
- **Transport:** HTTP polling, SSE stream hay WebSocket/STOMP; proxy/load balancer hỗ trợ cụ thể ra sao?
- **Recovery:** reconnect, backoff, heartbeat, resubscribe, snapshot/delta, giới hạn retention?
- **Capacity:** request/s; active SSE/WebSocket connections; memory, thread, executor, pending queue và slow consumer?
- **Security:** auth, origin/CORS/CSRF, topic subscription permissions, tenant isolation, input validation?
- **Observability:** p95 update lag, server→client fan-out lag, reconnect count, disconnect errors, dropped/duplicated events, broker queue size?
- **Load test:** mô phỏng client thật, network chậm, proxy, restart, nhiều instance và mất kết nối; đừng chỉ đo localhost một client.

**Cách chọn thực dụng:** bắt đầu bằng polling nếu chấp nhận trễ và tần suất thấp; chọn SSE khi chủ yếu server đẩy thông báo; dùng WebSocket/STOMP khi giao tiếp hai chiều hoặc mô hình subscription phức tạp thực sự cần. Tách riêng bài toán **transport** khỏi bài toán **phát lại dữ liệu đáng tin cậy, bảo mật và scale**.

## 12. Tài liệu tham khảo

- Spring Framework — Asynchronous Requests, SSE/`SseEmitter` (MVC): https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html
- Spring Framework — Enable STOMP: https://docs.spring.io/spring-framework/reference/web/websocket/stomp/enable.html
- Spring Framework — Message Flow: https://docs.spring.io/spring-framework/reference/web/websocket/stomp/message-flow.html
- Spring Framework — Simple Broker: https://docs.spring.io/spring-framework/reference/web/websocket/stomp/handle-simple-broker.html
- Spring Framework — External Broker: https://docs.spring.io/spring-framework/reference/web/websocket/stomp/handle-broker-relay.html
- Spring Framework — WebSocket transport configuration: https://docs.spring.io/spring-framework/reference/web/websocket/stomp/server-config.html
- MDN — Using server-sent events: https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events
- MDN — WebSocket API: https://developer.mozilla.org/en-US/docs/Web/API/WebSocket


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="websocket-stomp-002">WebSocket handshake, subprotocol và vòng đời kết nối</a>

<details>
<summary>Xem chi tiết</summary>

# WebSocket handshake, subprotocol và vòng đời kết nối trong Spring Framework

## 1. Mục tiêu bài học và phạm vi

WebSocket tạo một kênh giao tiếp **hai chiều (full-duplex), duy trì lâu dài** giữa client và server. Khác với một HTTP request thông thường kết thúc sau khi trả response, kết nối WebSocket bắt đầu bằng **HTTP handshake**, sau đó chuyển sang trao đổi **WebSocket frames** qua cùng kết nối. Nếu hai phía dùng giao thức ứng dụng như **STOMP**, họ còn thương lượng **subprotocol** trong handshake và trao đổi STOMP frames trên WebSocket.

Sau bài này, bạn có thể:

- Diễn giải từng bước HTTP/1.1 Upgrade handshake và ý nghĩa các header `Upgrade`, `Connection`, `Sec-WebSocket-Key`, `Sec-WebSocket-Accept`, `Sec-WebSocket-Version`, `Origin`, `Sec-WebSocket-Protocol`.
- Phân biệt **handshake HTTP**, **WebSocket connection**, **WebSocket subprotocol**, **STOMP CONNECT**, **STOMP subscription** và **SockJS fallback**.
- Cấu hình endpoint bằng `@EnableWebSocket` / `WebSocketConfigurer` hoặc `@EnableWebSocketMessageBroker` / `WebSocketMessageBrokerConfigurer`, kiểm tra handshake bằng `HandshakeInterceptor` và đàm phán subprotocol thông qua `SubProtocolCapable`/`HandshakeHandler`.
- Theo dõi vòng đời `CONNECTING → OPEN → CLOSING → CLOSED`, callback của Spring, trạng thái close, lỗi vận chuyển, heartbeat và cleanup tài nguyên.
- Chạy ví dụ Java 17+ dùng Spring Boot, server raw WebSocket và Java JDK WebSocket client; đối chiếu handshake, subprotocol được chọn, nhận/gửi message và close.

**Phiên bản tham chiếu:** Java 17+, Spring Framework 6.1/Spring Boot 3.3. Cú pháp ví dụ áp dụng cho **Servlet stack (Spring MVC)** với `spring-websocket`. Spring WebFlux có API reactive khác, nêu riêng ở mục 10. WebSocket tuân theo RFC 6455; nâng cấp HTTP/1.1 là đường phổ biến được minh họa ở đây. HTTP/2 có cơ chế extended CONNECT theo RFC 8441, không dùng chính xác chuỗi header HTTP/1.1 ở các ví dụ bên dưới.

## 2. Tại sao cần handshake trước khi gửi dữ liệu?

Một WebSocket client chưa thể gửi text message tùy ý tới URL `ws://...`. Nó phải thiết lập kết nối mạng/TLS nếu cần, gửi yêu cầu mở kết nối đúng protocol, được server chấp nhận, rồi mới dùng WebSocket frames.

Ví dụ HTTP/1.1 request mở kết nối (giá trị key minh họa):

```http
GET /ws/echo HTTP/1.1
Host: localhost:8080
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Version: 13
Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
Sec-WebSocket-Protocol: demo.v1, chat.v2
Origin: http://localhost:3000
```

Nếu chấp nhận, server có thể trả:

```http
HTTP/1.1 101 Switching Protocols
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
Sec-WebSocket-Protocol: demo.v1
```

Đây là **ví dụ handshake với key mẫu trong RFC 6455**; những key thật phải được tạo ngẫu nhiên cho từng lần bắt tay. Không sao chép nguyên key mẫu để xây client production. Header `Origin` được browser thêm khi phù hợp; Java native client và các client không phải browser có thể không gửi.

Luồng tổng quát:

```text
Browser/JDK/JS client                 Spring + HTTP server
       |                                        |
       |  TCP connect / TLS nếu dùng wss       |
       |--------------------------------------->|
       |  GET /ws/echo + Upgrade: websocket    |
       |  Sec-WebSocket-Key + protocol offers  |
       |--------------------------------------->|
       |                         kiểm tra method,
       |                         version, key, Origin,
       |                         security/interceptor
       |                        chọn subprotocol
       |  101 + Sec-WebSocket-Accept + protocol |
       |<---------------------------------------|
       | ======= WebSocket OPEN ============== |
       |  frame TEXT / BINARY / PING / PONG    |
       |<======================================>|
       |  CLOSE frame (code + reason)          |
       |<-------------------------------------->|
       |  TCP/TLS connection closed            |
       |---------------------------------------X
```

### Ý nghĩa từng header

| Header/status | Ai gửi? | Ý nghĩa thực tế |
| --- | --- | --- |
| `GET ... HTTP/1.1` | Client | Mở handshake trên endpoint HTTP đang hỗ trợ WebSocket. |
| `Upgrade: websocket` | Client và server | Đề nghị/xác nhận chuyển sang WebSocket. |
| `Connection: Upgrade` | Client và server | Cho biết token `Upgrade` là tùy chọn dành cho hop HTTP hiện tại. |
| `Sec-WebSocket-Version: 13` | Client | Phiên bản wire protocol tiêu chuẩn RFC 6455. |
| `Sec-WebSocket-Key` | Client | Nonce ngẫu nhiên được Base64 encode; **không phải password hay token đăng nhập**. |
| `Sec-WebSocket-Accept` | Server | Chứng minh response gắn với request key, tính theo thuật toán RFC 6455. |
| `Origin` | Thường là browser | Giúp server thực thi chính sách nguồn; **không thay thế xác thực**. |
| `Sec-WebSocket-Protocol` | Client đề xuất; server chọn | Chọn **một** subprotocol ứng dụng mà hai bên cùng hỗ trợ. |
| `Sec-WebSocket-Extensions` | Hai phía thương lượng | Extension ở mức wire protocol, ví dụ compression `permessage-deflate`; không phải subprotocol. |
| `101 Switching Protocols` | Server | Handshake Upgrade thành công; về sau là WebSocket frames, không còn HTTP request/response tương ứng từng message. |

Với key ví dụ `dGhlIHNhbXBsZSBub25jZQ==`, server tính:

```text
Sec-WebSocket-Accept =
  Base64(SHA-1(
    Sec-WebSocket-Key
    + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"))
```

Kết quả là `s3pPLMBiTxaQ9kYGzzhZRbK+xOo=`. GUID là hằng số của RFC; SHA-1 ở đây dùng **để xác nhận handshake**, không phải mật mã để xác thực danh tính hoặc bảo vệ payload. An toàn truyền tải đến từ `wss://` (TLS), xác thực và authorization của ứng dụng.

### Khi nào handshake thất bại?

Một số nhóm lỗi cần tách biệt:

1. **Trước HTTP:** DNS, TCP connect, TLS certificate/handshake, proxy/firewall. Không có response HTTP 101.
2. **HTTP handshake bị từ chối:** endpoint không tồn tại (404), user chưa xác thực (401/403 theo security), Origin không hợp lệ (403), sai method/header/version, hoặc server/proxy không hỗ trợ Upgrade.
3. **101 được trả nhưng header không hợp lệ:** client chuẩn RFC phải xác minh `Sec-WebSocket-Accept` và subprotocol được chọn. Nếu không hợp lệ, client hủy kết nối.
4. **Handshake đã thành công nhưng WebSocket/ứng dụng lỗi:** đây là lỗi sau khi OPEN; quan sát qua WebSocket close/error, không thể “trả HTTP 500” cho một message như request HTTP thông thường.

Không coi mọi lỗi “WebSocket connection failed” là lỗi message handler; nhiều trường hợp request chưa hề đến `afterConnectionEstablished`.

## 3. Subprotocol: lớp giao ước nằm trên WebSocket

RFC 6455 định nghĩa **frame TEXT/BINARY và control frames**, còn ý nghĩa business của text/binary là việc của ứng dụng. **Subprotocol** là một quy ước cụ thể để mã hóa và hiểu message, chẳng hạn `chat.v1`, `graphql-transport-ws` hoặc các phiên bản của STOMP (`v10.stomp`, `v11.stomp`, `v12.stomp`).

Client có thể gửi:

```http
Sec-WebSocket-Protocol: chat.v2, chat.v1
```

Server chỉ được chọn **một giá trị đã được client đề nghị**, ví dụ:

```http
Sec-WebSocket-Protocol: chat.v1
```

Nếu không có giao thức chung, server **không được trả tên subprotocol chưa được đề nghị**. Server có thể không chọn subprotocol và vẫn cho phép WebSocket nếu ứng dụng chấp nhận chế độ không có subprotocol; nếu nghiệp vụ **bắt buộc** dùng STOMP/chat phiên bản xác định, cần thực thi điều kiện từ chối hoặc đóng kết nối ở lớp thích hợp. Đừng mặc định rằng có danh sách đề nghị nhưng không khớp sẽ luôn bị từ chối tự động.

**Subprotocol ≠ WebSocket URL:** `/ws/chat` là endpoint; `chat.v1` là cú pháp và semantics của messages chạy trên kết nối đó. Cùng endpoint có thể hỗ trợ nhiều phiên bản giao thức nếu handler định nghĩa rõ.

**Subprotocol ≠ HTTP Content-Type:** Sau `101`, payload đi trong frame WebSocket, không phải body HTTP của từng lần GET/POST. Subprotocol chọn định dạng ứng dụng; có thể kết hợp text JSON hay binary protobuf theo thỏa thuận riêng.

**Subprotocol ≠ extension:** `permessage-deflate` điều chỉnh nén frame, không xác định cấu trúc message hay routing.

### STOMP có phải subprotocol không?

Có. Spring hỗ trợ **STOMP over WebSocket**: WebSocket đảm nhận transport; STOMP định nghĩa lệnh `CONNECT`, `CONNECTED`, `SEND`, `SUBSCRIBE`, `MESSAGE`, `DISCONNECT` và headers `destination`, `subscription`, `receipt`, `heart-beat`. Browser chọn STOMP trong `Sec-WebSocket-Protocol` nếu client gửi danh sách phù hợp và server hỗ trợ.

Có **hai bước khác nhau**:

1. **HTTP/WebSocket handshake** hoàn tất: server trả 101; WebSocket OPEN.
2. **STOMP handshake ở tầng ứng dụng**: client gửi frame `CONNECT` hoặc `STOMP`; server/broker trả `CONNECTED` rồi mới xử lý các subscription/message tương ứng.

Ví dụ sau khi WebSocket OPEN:

```text
CONNECT
accept-version:1.2
heart-beat:10000,10000

^@
```

`^@` đại diện byte NUL (0x00) kết thúc STOMP frame, không phải hai ký tự literal caret/at. Server phản hồi frame `CONNECTED` nếu STOMP CONNECT được chấp nhận. **HTTP 101 không có nghĩa STOMP CONNECT đã thành công**, và cũng chưa có bất kỳ `SUBSCRIBE` nào.

Raw WebSocket handler tự chọn subprotocol `demo.v1` là cách đơn giản để học đàm phán. STOMP handler của Spring phức tạp hơn vì còn channel, controller, broker và STOMP frame handling.

## 4. Spring MVC: cấu hình endpoint raw WebSocket và handshake

Trong Servlet stack, dùng `spring-websocket`, `@EnableWebSocket` và `WebSocketConfigurer`. Bạn đăng ký:

- **URL endpoint:** ví dụ `/ws/echo`.
- **`WebSocketHandler`:** callback khi kết nối mở/đóng, nhận message, xảy ra transport error.
- **`HandshakeInterceptor`:** logic trước/sau handshake; có thể từ chối trước 101 hoặc lưu attribute.
- **`HandshakeHandler`:** xử lý kiểm tra protocol/Origin/extensions và nhờ server container upgrade; mặc định Spring dùng `DefaultHandshakeHandler` khi phù hợp.
- **Origin policy:** `setAllowedOrigins(...)`/`setAllowedOriginPatterns(...)`, nhất là browser khác origin.

Mẫu cấu hình tối thiểu:

```java
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
class RawWebSocketConfiguration implements WebSocketConfigurer {
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new EchoHandler(), "/ws/echo")
                .addInterceptors(new ConnectionMetadataInterceptor())
                .setAllowedOriginPatterns("http://localhost:*");
    }
}
```

Đây là **fragment**; `EchoHandler` và `ConnectionMetadataInterceptor` là các class cần định nghĩa. Ví dụ chạy đầy đủ ở mục 8. Trong production, đưa origin được phép vào cấu hình theo môi trường và kết hợp Spring Security phù hợp; dấu `*` chỉ là glob matching trong ví dụ local dev, không tự cấp quyền đăng nhập.

### HandshakeInterceptor: tác động trước Upgrade

Interface `HandshakeInterceptor` định nghĩa:

```java
boolean beforeHandshake(
    ServerHttpRequest request,
    ServerHttpResponse response,
    WebSocketHandler wsHandler,
    Map<String, Object> attributes);

void afterHandshake(
    ServerHttpRequest request,
    ServerHttpResponse response,
    WebSocketHandler wsHandler,
    Exception exception);
```

Đây là chữ ký API đã đơn giản hóa. `beforeHandshake` có thể đọc method, URI, headers, user principal, HTTP session; nếu không được phép, set HTTP status thích hợp và trả `false`. Các phần tử đặt vào `attributes` được chuyển sang attributes của `WebSocketSession` khi handshake thành công, ví dụ correlation ID hoặc user ID an toàn. Đừng đưa raw password/access token vào `attributes` hoặc log.

**Không nhầm:** `afterHandshake` là callback sau quá trình xử lý handshake; không phải bằng chứng rằng STOMP đã CONNECTED, cũng không thay `afterConnectionEstablished`. Để quan sát session mở thật, dùng lifecycle callback của `WebSocketHandler` hoặc event STOMP thích hợp.

### HandshakeHandler và subprotocol negotiation

`DefaultHandshakeHandler` sẽ kiểm tra yêu cầu WebSocket, origin và subprotocol rồi ủy quyền upgrade tới container. Với raw `WebSocketHandler`, Spring có thể lấy supported subprotocols từ handler nếu handler implements `SubProtocolCapable`:

```java
import java.util.List;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.handler.TextWebSocketHandler;

class EchoHandler extends TextWebSocketHandler
        implements SubProtocolCapable {

    @Override
    public List<String> getSubProtocols() {
        return List.of("demo.v1");
    }
}
```

Nếu client đề nghị `demo.v1` và server chọn nó, Spring `WebSocketSession.getAcceptedProtocol()` cho biết tên được thương lượng (ở thời điểm session đã mở). Nếu handler chỉ hỗ trợ raw messages mà không cần subprotocol, không bắt buộc phải implement `SubProtocolCapable`; hai bên phải có định dạng message chung qua quy ước ứng dụng khác.

**Khi nào tùy biến `DefaultHandshakeHandler`?** Khi cần luật chọn subprotocol hoặc kiểm soát handshake mà hook mặc định không đáp ứng; lưu ý mọi tùy biến cần tôn trọng RFC và chính sách security. Các kiểm tra business đơn giản nên đặt vào interceptor thay vì viết lại toàn bộ cơ chế upgrade.

## 5. Vòng đời WebSocket: từ kết nối TCP đến cleanup

### 5.1 Các trạng thái

```text
             TCP/TLS kết nối + HTTP handshake
    ┌─────────────────────────────────────────┐
    │                                         ▼
CONNECTING -------------------------------> OPEN
    │       handshake failed                   │
    ▼                                          │ send/receive TEXT/BINARY
  CLOSED                                       │ PING/PONG
                                               ▼
                                            CLOSING
                                         close frame exchange
                                               ▼
                                             CLOSED

Ngoại lệ: mất mạng đột ngột có thể dẫn đến CLOSED/abnormal
mà không có đủ close frames đúng chuẩn.
```

`CONNECTING`, `OPEN`, `CLOSING`, `CLOSED` là cách mô tả trạng thái phổ biến trên WebSocket browser/client API. Spring server thể hiện vòng đời qua callbacks và `WebSocketSession.isOpen()`; không phải cùng một enum public bắt buộc trong mọi API server.

Tại **OPEN**, hai bên có thể gửi frame theo giao thức WebSocket. Có hai loại data frame: TEXT và BINARY; message có thể bị chia thành nhiều frames, và handler có thể hỗ trợ partial messages tùy cấu hình/server. Ping/Pong và Close là **control frames** riêng, không phải text message bình thường.

### 5.2 Callback ở WebSocketHandler của Spring

| Thời điểm | Callback API | Việc phù hợp |
| --- | --- | --- |
| Sau handshake thành công, WebSocket sẵn sàng | `afterConnectionEstablished(WebSocketSession)` | Ghi metric OPEN, khởi tạo state theo session, kiểm tra protocol/attribute |
| Khi nhận message | `handleMessage(...)`; thường override `handleTextMessage(...)` ở `TextWebSocketHandler` | Parse và xử lý payload theo hợp đồng; trả lời có kiểm soát |
| Khi lỗi vận chuyển | `handleTransportError(WebSocketSession, Throwable)` | Log lỗi phù hợp, cleanup, close nếu session còn mở |
| Sau đóng kết nối | `afterConnectionClosed(WebSocketSession, CloseStatus)` | Xóa state/registries, decrement metric, tránh gửi thêm message |
| Khi nhận Pong | `handlePongMessage(...)` (handler hỗ trợ) | Theo dõi heartbeat/round-trip của transport nếu cần |

Ví dụ sự kiện hợp lý:

```text
HTTP handshake accepted
afterConnectionEstablished(session)
handleTextMessage(session, "hello")
sendTextMessage(session, "echo:hello")
close frame exchanged / underlying transport closed
afterConnectionClosed(session, CloseStatus.NORMAL)
```

**Không bảo đảm thứ tự mọi callback trong mọi lỗi mạng.** Ví dụ kết nối rớt bất ngờ có thể gọi `handleTransportError` và `afterConnectionClosed` với thông tin khác nhau; logic giải phóng phải **idempotent** (xóa nhiều lần không làm sai state). Nếu handshake chưa thành công, chưa tạo WebSocketSession hợp lệ để dùng như OPEN; đừng đợi `afterConnectionClosed` mới dọn resource đã tạo ở interceptor trước khi Upgrade.

### 5.3 Close handshake và status code

Đóng chuẩn WebSocket là **gửi control frame CLOSE**, đầu kia thường phản hồi CLOSE (nếu chưa gửi) rồi transport đóng. Ở Spring:

```java
session.close(CloseStatus.NORMAL);     // code 1000
```

Một số mã quan trọng:

| Code | Nghĩa | Khi nào quan sát |
| --- | --- | --- |
| `1000` | Normal closure | Client/user hoàn tất phiên, server chủ động kết thúc bình thường |
| `1001` | Going away | Server tắt, endpoint điều hướng/đóng ứng dụng |
| `1002` | Protocol error | WebSocket framing/handshake protocol vi phạm |
| `1003` | Unsupported data | Gửi loại dữ liệu không được handler hỗ trợ |
| `1006` | Abnormal closure (**không gửi trên wire**) | API quan sát connection chết mà không nhận close frame chuẩn |
| `1008` | Policy violation | Vi phạm chính sách ứng dụng |
| `1009` | Message too big | Message vượt giới hạn |
| `1011` | Internal server error | Lỗi server khi đang xử lý message |
| `1012` / `1013` | Service restart / Try again later | Các ngữ cảnh vận hành tương ứng nếu endpoint hỗ trợ |

**1005 và 1006 là các giá trị dành cho biểu diễn trạng thái, không được đặt vào Close frame trên wire.** Không cố ép gửi `1006` bằng `session.close(...)`; dùng code hợp lệ như 1000/1008/1011, và log nguyên nhân khi kết nối mất bất thường. Reason string không nên chứa thông tin nhạy cảm, phải tuân giới hạn byte UTF-8 của close control frame. Kết nối TCP bị reset không đồng nghĩa bên kia đã nhận close frame của ứng dụng.

### 5.4 Ping/Pong và heartbeat

- **WebSocket Ping/Pong:** control frames mức RFC 6455; giúp phát hiện tính sống của transport theo chính sách runtime. Endpoint nhận Ping phải xử lý Pong theo RFC; browser JavaScript WebSocket API không expose hàm ping/pong thô cho ứng dụng.
- **STOMP heart-beat:** nhịp heartbeat ở tầng STOMP, thương lượng trong `CONNECT`/`CONNECTED` qua header `heart-beat`; **không phải** WebSocket Ping/Pong. Có thể phát hiện client/broker bất hoạt ngay cả khi TCP connection nhìn bề ngoài còn sống.
- **TCP keepalive:** tùy chọn socket/OS ở tầng mạng, không thay thế timeout ứng dụng. Proxy/firewall/load balancer có idle timeout riêng.
- **SockJS heartbeat:** khi dùng fallback, cơ chế keepalive có thể khác với raw WebSocket và chịu tác động của STOMP heartbeat configuration.

Đặt heartbeat hợp lý: quá ngắn gây traffic không cần thiết, quá dài làm lỗi mất mạng được phát hiện muộn. Với mỗi phương thức, cần đo timeout ở proxy, engine, broker và ứng dụng. Đừng tự viết một vòng ping blocking/sleep trên thread I/O.

## 6. STOMP over WebSocket: endpoint và các mốc lifecycle riêng

Để dùng STOMP messaging thay vì tự parse raw text, trong Spring MVC có thể cấu hình:

```java
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
class StompConfiguration implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/stomp")
                .setAllowedOriginPatterns("https://app.example.org");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic", "/queue");
    }
}
```

Đây là **đoạn cấu hình minh họa**, độc lập với raw WebSocket demo ở mục 8. `/ws/stomp` là HTTP endpoint mở WebSocket; `/app/...` và `/topic/...` là **STOMP destination prefixes**, không phải URL của HTTP handshake. `/app` thường chuyển `SEND` tới `@MessageMapping` trong controller, còn `/topic`/`/queue` được simple broker xử lý theo cấu hình trên. Với simple broker, hai prefix này chỉ là quy ước routing, không tự đảm bảo message persistence hay semantics của queue doanh nghiệp.

Ví dụ luồng:

```text
Client                             Spring STOMP messaging
  GET /ws/stomp + WebSocket Upgrade   → HTTP 101
  STOMP CONNECT                       → STOMP CONNECTED
  STOMP SUBSCRIBE /topic/updates      → broker lưu subscription
  STOMP SEND /app/notify              → @MessageMapping xử lý
                                      → broker gửi MESSAGE /topic/updates
  STOMP DISCONNECT                    → cleanup STOMP session
  WebSocket CLOSE                     → đóng transport
```

Tại sao cần phân biệt? Một WebSocket có thể **OPEN** nhưng STOMP chưa `CONNECTED` (client chưa gửi frame CONNECT, bị reject hoặc đang đợi broker). Kiểm tra ứng dụng chỉ theo callback WebSocket mà coi người dùng đã “subscribe” là không chính xác.

### Event lifecycle phía STOMP

Spring publish các ApplicationEvent hữu ích:

- `SessionConnectEvent`: server nhận STOMP CONNECT từ client; chưa chắc broker đã xác nhận kết nối.
- `SessionConnectedEvent`: phiên STOMP được báo đã connected sau khi xử lý CONNECT thành công.
- `SessionSubscribeEvent` / `SessionUnsubscribeEvent`: đăng ký/hủy subscription STOMP.
- `SessionDisconnectEvent`: thông báo kết thúc phiên STOMP; có thể xảy ra nhiều lần với cùng session trong một số luồng, do đó cleanup cần idempotent.

Ví dụ listener quan sát:

```java
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
class StompSessionEvents {
    @EventListener
    void onConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.wrap(event.getMessage());
        System.out.println("STOMP CONNECTED: " + accessor.getSessionId());
    }

    @EventListener
    void onDisconnected(SessionDisconnectEvent event) {
        System.out.println("STOMP DISCONNECTED: " + event.getSessionId());
        // Dọn map state theo sessionId bằng thao tác idempotent.
    }
}
```

Đây là **Spring bean fragment**, không phải Java file độc lập. Tùy loại event, native/STOMP headers có thể khác nhau; tránh dựa vào event listener để lấy credential chưa được bảo đảm tồn tại. Khi bật broker relay, các giai đoạn CONNECT có thể còn phụ thuộc kết nối broker bên ngoài. **Simple broker** thích hợp học tập/ứng dụng nhỏ, không có tất cả tính năng của broker đầy đủ (ACK/receipt đầy đủ, clustering, persistence...).

### STOMP authentication so với HTTP handshake authentication

Spring thường liên kết `Principal` của HTTP handshake với session WebSocket/STOMP. Với cookie-based authentication, trình duyệt gửi cookie trên handshake phù hợp chính sách; Spring Security có thể xác thực trước upgrade. STOMP `login`/`passcode` mặc định **không tự thay thế** cơ chế HTTP authentication trong Spring WebSocket; nếu ứng dụng dùng token trong STOMP `CONNECT`, phải thiết kế interceptor trên inbound channel và xác thực/ràng buộc Principal rõ ràng, cùng chính sách authorization cho destination.

Một kết nối đã handshake thành công **không tự có quyền gửi tới mọi `/app/...` hoặc subscribe mọi `/topic/...`**. Authorization phải kiểm tra cả connection và message destinations. Với trình duyệt và cookie, nhớ xét origin policy và CSRF/security trên STOMP CONNECT theo stack đang dùng.

## 7. SockJS: tại sao luồng handshake có thể khác?

`SockJS` cung cấp fallback transport khi môi trường không hỗ trợ WebSocket chuẩn hoặc proxy chặn Upgrade. Có thể bật bằng:

```java
registry.addEndpoint("/ws/stomp")
        .setAllowedOriginPatterns("https://app.example.org")
        .withSockJS();
```

Client SockJS sẽ trao đổi thêm request HTTP (ví dụ `/info` và đường dẫn transport), rồi chọn WebSocket hoặc XHR streaming/polling tùy khả năng. **Không thể lấy một request SockJS fallback và mặc định nó tuân theo handshake `101 Switching Protocols` của raw WebSocket**. STOMP frame có thể chạy trên transport fallback nên STOMP session lifecycle và transport lifecycle cần quan sát riêng.

Không gửi browser native `new WebSocket("ws://host/ws/stomp")` tới SockJS URL biến thể rồi kỳ vọng client ấy tự hiểu các đường dẫn/frames của SockJS. Nếu phía server hỗ trợ WebSocket native trên endpoint STOMP, dùng STOMP library với `brokerURL` thích hợp; nếu muốn SockJS fallback, dùng SockJS client + StompJS cấu hình `webSocketFactory`. Chọn thống nhất với server.

## 8. Ví dụ chạy được: raw WebSocket handshake, subprotocol và close

Ví dụ này cố ý dùng **raw WebSocket** để quan sát handshake và lifecycle rõ ràng. Sau đó có thể mở rộng sang STOMP qua cấu hình riêng ở mục 6. Runtime: Java 17+, Gradle, Spring Boot 3.3.x. JDK có sẵn `java.net.http.WebSocket` nên client không cần npm hay browser.

### 8.1 Tạo dự án

Cấu trúc:

```text
websocket-lifecycle-demo/
├── build.gradle
└── src/main/java/demo/
    ├── WsLifecycleApplication.java
    └── WsClientApp.java
```

`build.gradle`:

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.13'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'demo'
version = '1.0.0'

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-websocket'
}

springBoot {
    mainClass = 'demo.WsLifecycleApplication'
}

tasks.register('runClient', JavaExec) {
    group = 'application'
    description = 'Connect the JDK WebSocket client to the local server'
    classpath = sourceSets.main.runtimeClasspath
    mainClass = 'demo.WsClientApp'
}
```

`spring-boot-starter-websocket` kéo các dependency WebSocket cần thiết cùng Servlet server mặc định trong Spring Boot. Plugin Boot quản lý dependency versions, tránh trộn thủ công Spring Framework và Tomcat không tương thích.

### 8.2 Server: WsLifecycleApplication.java

```java
package demo;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@SpringBootApplication
public class WsLifecycleApplication {
    public static void main(String[] args) {
        SpringApplication.run(WsLifecycleApplication.class, args);
    }
}

@Configuration
@EnableWebSocket
class WebSocketConfiguration implements WebSocketConfigurer {

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new EchoHandler(), "/ws/echo")
                .addInterceptors(new DemoHandshakeInterceptor())
                .setAllowedOriginPatterns("http://localhost:*");
    }
}

class DemoHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler handler,
            Map<String, Object> attributes) {

        // Điều kiện chỉ để demo; không dùng query làm credential.
        if (!"client=cli".equals(request.getURI().getRawQuery())) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }

        attributes.put("client", "cli");
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler handler,
            Exception exception) {
        // Có thể ghi metrics/trace không nhạy cảm sau handshake.
    }
}

class EchoHandler extends TextWebSocketHandler implements SubProtocolCapable {

    @Override
    public List<String> getSubProtocols() {
        return List.of("demo.v1");
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        System.out.println("OPEN protocol=" + session.getAcceptedProtocol()
                + " client=" + session.getAttributes().get("client"));
    }

    @Override
    protected void handleTextMessage(
            WebSocketSession session,
            TextMessage message) throws IOException {
        session.sendMessage(new TextMessage("echo:" + message.getPayload()));
    }

    @Override
    public void handleTransportError(
            WebSocketSession session,
            Throwable exception) throws Exception {
        System.err.println("Transport error: "
                + exception.getClass().getSimpleName());
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    public void afterConnectionClosed(
            WebSocketSession session,
            CloseStatus status) {
        System.out.println("CLOSED code=" + status.getCode());
    }
}
```

Điều quan sát được:

- `@EnableWebSocket` mở infrastructure đăng ký handler tại `/ws/echo`.
- `beforeHandshake` kiểm tra query `?client=cli` và đưa một attribute đã kiểm tra vào session. Trả `false` nếu thiếu, response `403` **trước** WebSocket OPEN. Đây chỉ là **bài tập phân biệt lifecycle**, không phải cách xác thực production.
- `EchoHandler.getSubProtocols()` cung cấp `demo.v1` cho negotiation; `session.getAcceptedProtocol()` cho biết protocol đã chọn.
- `afterConnectionEstablished` chạy khi handshake thành công, và `handleTextMessage` gửi phản hồi.
- `afterConnectionClosed` ghi status. `handleTransportError` xử lý lỗi transport bằng cách đóng session nếu vẫn open.

Trong ứng dụng nhiều client, đừng duy trì một biến mutable không đồng bộ ở handler singleton; dùng state theo `session.getId()` trong concurrent map, kiểm soát tài nguyên, xóa idempotent. Gửi đồng thời từ nhiều thread đến một session cần cơ chế đồng bộ hoặc `ConcurrentWebSocketSessionDecorator`, cùng giới hạn thời gian/đệm phù hợp; một WebSocketSession không phải hàng đợi vô hạn.

### 8.3 Client: WsClientApp.java (Java 17)

```java
package demo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

public class WsClientApp {

    public static void main(String[] args) throws Exception {
        CompletableFuture<String> reply = new CompletableFuture<>();
        StringBuilder partial = new StringBuilder();

        WebSocket.Listener listener = new WebSocket.Listener() {
            @Override
            public void onOpen(WebSocket socket) {
                socket.request(1);
            }

            @Override
            public CompletionStage<?> onText(
                    WebSocket socket, CharSequence data, boolean last) {
                partial.append(data);
                if (last) {
                    reply.complete(partial.toString());
                }
                socket.request(1);
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public void onError(WebSocket socket, Throwable error) {
                reply.completeExceptionally(error);
            }
        };

        HttpClient client = HttpClient.newHttpClient();
        WebSocket socket = client.newWebSocketBuilder()
                .subprotocols("demo.v1")
                .buildAsync(
                        URI.create("ws://localhost:8080/ws/echo?client=cli"),
                        listener)
                .get(5, TimeUnit.SECONDS);

        System.out.println("selected=" + socket.getSubprotocol());

        socket.sendText("hello", true).join();
        System.out.println("received=" + reply.get(5, TimeUnit.SECONDS));

        socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
    }
}
```

Client gửi đề xuất `Sec-WebSocket-Protocol: demo.v1`; kết nối tới endpoint qua WebSocket handshake do `HttpClient` thực hiện. Listener yêu cầu cấp tiếp thông báo với `request(1)` để đáp ứng cơ chế demand của Java WebSocket client, ghép các phần text tới `last=true`, rồi trả result cho main thread. `sendClose(...)` bắt đầu close handshake bình thường.

### 8.4 Cách chạy, kết quả và thí nghiệm

Trong thư mục dự án, terminal thứ nhất chạy server:

```bash
gradle bootRun
```

Khi Spring Boot báo server đã mở port 8080, dùng terminal thứ hai:

```bash
gradle runClient
```

Nếu có Gradle Wrapper: `./gradlew bootRun` và `./gradlew runClient` trên macOS/Linux, hoặc `.\gradlew.bat bootRun` và `.\gradlew.bat runClient` trên PowerShell Windows. **Không mặc định có sẵn `gradlew`** trong dự án tự tạo; tạo Gradle Wrapper hoặc dùng Gradle đã cài. Các lệnh trên được viết cho hai terminal riêng: server phải tiếp tục chạy khi client kết nối.

**Hai dòng do client tự in**, nếu server chạy ở port 8080 và handshake thành công:

```text
selected=demo.v1
received=echo:hello
```

Gradle và HTTP/Tomcat/Spring có thể in thêm log, không phải một phần hai dòng từ `System.out.println` trên. Server cũng sẽ in dòng dạng `OPEN protocol=demo.v1 client=cli` và `CLOSED code=1000` khi phiên kết thúc bình thường; thứ tự log giữa hai terminal không cố định. Nếu server còn giữ port 8080 cho ứng dụng khác, cần cấu hình `server.port` và sửa URL trong client cho khớp.

**Thử nghiệm A – từ chối trước handshake:** đổi URL trong client thành `ws://localhost:8080/ws/echo` (bỏ `?client=cli`). `beforeHandshake` trả 403; `buildAsync(...)` thất bại, không có `afterConnectionEstablished`. Điều này khác với mở kết nối thành công rồi bị close code 1008.

**Thử nghiệm B – subprotocol khác:** đổi `.subprotocols("demo.v1")` thành `.subprotocols("unknown.v1")`. Server ở mẫu chỉ hỗ trợ `demo.v1`, nên không thể chọn `unknown.v1`. Tùy container/client, upgrade có thể diễn ra **không có subprotocol được chọn**, hoặc client từ chối do yêu cầu cụ thể của ứng dụng; hãy kiểm tra `getSubprotocol()` và handshake thực tế. Nếu ứng dụng muốn *bắt buộc* một protocol, thêm luật kiểm tra/thực thi rõ ràng.

**Thử nghiệm C – đóng hợp lệ và bất thường:** chạy client như bình thường để quan sát 1000. Sau đó tắt đột ngột mạng/process trong phiên để thấy transport error/abnormal termination có thể xuất hiện mà không có close frame bình thường. Không kết luận có lỗi business chỉ từ code 1006.

### 8.5 HTTP handshake có thể quan sát bằng cách nào?

Browser DevTools → **Network** → **WS** (hoặc WebSocket) → chọn `/ws/echo`; kiểm tra request/response handshake headers, negotiated protocol và Frames/Messages. Với Chrome/Firefox, giao diện hiển thị khác nhau; quan sát header `Sec-WebSocket-Protocol` và response status 101 khi dùng HTTP/1.1 Upgrade.

Trên JDK client, `WebSocket.getSubprotocol()` phản ánh server lựa chọn (chuỗi rỗng nếu không có subprotocol). Để đo transport-level handshake, bật debug logging cho client/server hoặc dùng packet capture **chỉ trong môi trường kiểm thử có quyền**, tránh ghi cookies/token. Một WebSocket client thông thường không tự expose mọi response header từ handshake sau khi kết nối thành công.

## 9. Xác thực, Origin, proxy, tài nguyên và vận hành

### 9.1 WebSocket không tự bỏ qua Spring Security

- HTTP handshake là request tới endpoint và có thể đi qua security filter chain; cần quyết định user nào được mở `/ws/...`.
- Xác thực qua cookie/session hay bearer token phải phù hợp môi trường. Browser WebSocket API không cho tùy ý đặt mọi HTTP header giống `fetch()`; nhiều ứng dụng dùng cookie, hoặc thiết kế authentication ở STOMP CONNECT qua channel interceptor. Không chuyển token bí mật sang query URL chỉ để “dễ debug”.
- `Origin` khác CORS: WebSocket không dùng preflight CORS thông thường giống `fetch`, nhưng Spring có origin checks riêng. Non-browser client có thể tự gửi Origin giả; **Origin chỉ là defense bổ sung chống cross-site WebSocket hijacking**, không phải chứng thực danh tính.
- Với STOMP, xác thực phiên và authorization từng destination `SEND`/`SUBSCRIBE` cũng cần được kiểm tra, kể cả khi HTTP 101 đã hợp lệ.
- Giới hạn số kết nối theo user/IP, kích thước message, thời gian rảnh, fan-out và tốc độ gửi; không cấp bộ đệm không giới hạn.

### 9.2 Reverse proxy và load balancer

Nếu sử dụng nginx hoặc một reverse proxy khác, cần chuyển tiếp HTTP Upgrade và Connection headers, cho phép timeout dài hơn HTTP request thông thường, đồng thời cấu hình TLS ở `wss://`. Ví dụ nginx cơ bản có thể gồm:

```nginx
location /ws/ {
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
    proxy_read_timeout 3600s;
}
```

Đây là fragment triển khai, không phải cấu hình reverse proxy hoàn chỉnh. Cấu hình `Connection` linh hoạt thường dùng map để tránh set Upgrade khi request không có header; trên hệ thống production, cần TLS/security headers/health checks theo hạ tầng. `proxy_read_timeout` và timeout của load balancer phải phù hợp heartbeat và chính sách tài nguyên.

Ứng dụng scale nhiều instance cần chú ý: WebSocket connection **gắn vào một node** trong đời sống kết nối; load balancer không chia một connection đang mở giữa nhiều server. Nếu gửi message đến user trên node khác, cần shared broker/pub-sub, STOMP broker relay hoặc hạ tầng phân phối phù hợp; sticky sessions một mình không tạo message bus phân tán.

### 9.3 Quản lý lifecycle đáng tin cậy

| Sự kiện | Nên làm | Tránh |
| --- | --- | --- |
| Trước handshake | Validate Origin/auth/path/limits; đặt metadata tối thiểu vào attributes | Coi nonce `Sec-WebSocket-Key` là authentication |
| Vừa OPEN | Ghi session ID/Principal, selected subprotocol, thời gian bắt đầu | Mặc định STOMP cũng CONNECTED |
| Nhận TEXT/BINARY | Parse, giới hạn payload, kiểm tra authorization, bắt lỗi phù hợp | Chạy blocking I/O lâu trên thread I/O |
| Gửi message | Kiểm tra session open, buffer/send time limits, backpressure | Nhiều thread send đồng thời không đồng bộ |
| Transport error | Log nguyên nhân an toàn, close khi có thể, cleanup idempotent | Giả sử luôn có close frame và code 1000 |
| Đóng bình thường | Gửi close code hợp lệ, xóa session map, metric decrement một lần | Gửi code reserved `1006` |
| Redeploy/shutdown | Drain/close sessions có kế hoạch, cho client reconnect có jitter | Để mọi client reconnect tức thì và gây thundering herd |

### 9.4 Checklist debug theo giai đoạn

1. **Không có request tới server:** kiểm tra URL `ws://`/`wss://`, DNS, TLS, port, proxy, path.
2. **Có request nhưng không được 101:** kiểm tra response status 400/401/403/404, HTTP Upgrade headers, Origin, security filters, `HandshakeInterceptor`, phiên bản WebSocket.
3. **101 rồi client từ chối:** soát `Sec-WebSocket-Accept`, subprotocol được chọn, extensions và thư viện client.
4. **WebSocket OPEN nhưng không có ứng dụng message:** kiểm tra client subscribe/demand, handler binding, message format TEXT/BINARY, STOMP CONNECTED và destination đúng.
5. **Kết nối tự rớt sau một khoảng idle:** kiểm tra heartbeat, idle timeout proxy/load balancer/container, shutdown và lỗi mạng; không đoán lỗi từ 1006 duy nhất.
6. **Memory/CPU tăng khi nhiều client:** theo dõi active connections, sessions, send buffers, event-loop/thread pool, số lượng subscription, tốc độ message và broker.
7. **Client reconnect liên tục:** backoff + jitter, kiểm tra auth hết hạn, protocol mismatch, rolling deployment, duplicate subscription/cleanup state.

## 10. WebFlux khác gì với Spring MVC WebSocket?

Bài dùng Servlet stack với `org.springframework.web.socket.WebSocketHandler` và `WebSocketSession`. WebFlux có interface ở namespace khác: `org.springframework.web.reactive.socket.WebSocketHandler` với method `Mono<Void> handle(WebSocketSession session)` và các publisher `session.receive()`, `session.send(...)`. Handshake trong WebFlux được điều phối bởi `HandshakeWebSocketService`/`WebSocketHandlerAdapter`, không đăng ký thông qua `@EnableWebSocket` + `WebSocketConfigurer` của MVC.

Ví dụ **fragment WebFlux**:

```java
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import reactor.core.publisher.Mono;

import java.util.Map;

@Bean
HandlerMapping webSocketMapping() {
    WebSocketHandler echo = session ->
            session.send(session.receive()
                    .map(message -> session.textMessage(
                            "echo:" + message.getPayloadAsText())));

    SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
    mapping.setOrder(-1);
    mapping.setUrlMap(Map.of("/ws/reactive", echo));
    return mapping;
}

@Bean
WebSocketHandlerAdapter webSocketHandlerAdapter() {
    return new WebSocketHandlerAdapter();
}
```

Đoạn này đặt trong `@Configuration` class; không phải file độc lập. WebFlux không có cùng callback `afterConnectionEstablished`/`afterConnectionClosed` như MVC; lifecycle được mô hình hóa bằng reactive stream termination/completion và session methods. Tuy nhiên các quy tắc RFC 6455 về handshake, subprotocol, close và transport vẫn là cùng một giao thức nền. Không dùng lẫn import hai `WebSocketHandler` hoặc `WebSocketSession` thuộc MVC/WebFlux.

## 11. Câu hỏi kiểm tra và tình huống thực hành

**Câu 1.** Nhìn thấy HTTP response `101 Switching Protocols` đã đủ kết luận client STOMP subscribe thành công chưa?

**Trả lời:** Chưa. 101 chỉ xác nhận WebSocket Upgrade; client còn phải gửi STOMP CONNECT, nhận CONNECTED và gửi SUBSCRIBE tương ứng.

**Câu 2.** Server chỉ hỗ trợ `chat.v1` nhưng trả header `Sec-WebSocket-Protocol: chat.v2` trong khi client chỉ đề nghị `chat.v1`. Đúng RFC không?

**Trả lời:** Sai. Server chỉ được chọn subprotocol nằm trong danh sách client đề nghị; trong tình huống này `chat.v2` không được đề nghị.

**Câu 3.** Vì sao không nên dùng `Sec-WebSocket-Key` làm chứng cứ client đã đăng nhập?

**Trả lời:** Key là nonce dùng trong kiểm tra handshake, không phải credential, không ràng buộc người dùng hay quyền truy cập.

**Câu 4.** Cần kiểm tra cookie/token/Origin ở thời điểm nào nếu muốn từ chối trước 101?

**Trả lời:** Trong HTTP security chain và/hoặc `HandshakeInterceptor.beforeHandshake` / handshake handler, trước khi upgrade. Message authorization vẫn cần tiếp tục kiểm tra sau OPEN.

**Câu 5.** Thấy close code 1006 trong client logs có nghĩa server đã gửi `Close(1006)` không?

**Trả lời:** Không. 1006 là trạng thái abnormal closure được API báo, không được gửi trong close frame WebSocket.

**Câu 6.** STOMP heartbeat có giống WebSocket Ping/Pong không?

**Trả lời:** Không. STOMP heartbeat thuộc framing STOMP, còn WebSocket Ping/Pong là control frames RFC 6455. Chúng có thể cùng hỗ trợ phát hiện lỗi nhưng không thay thế trực tiếp cho nhau.

**Câu 7.** `HandshakeInterceptor.afterHandshake` có thay thế `afterConnectionEstablished` trong raw Spring WebSocket handler không?

**Trả lời:** Không. Interceptor quan sát quá trình handshake; handler callback chỉ chạy khi kết nối WebSocket đã được thiết lập.

**Bài thực hành mở rộng:** Từ ví dụ ở mục 8, thử ghi correlation ID không nhạy cảm vào session attributes, thêm timeout phát hiện idle, từ chối client có subprotocol không hỗ trợ và theo dõi OPEN/CLOSED active sessions bằng `ConcurrentHashMap` với cleanup idempotent. Chứng minh bằng log và request thực tế rằng kết nối bị từ chối trước 101 khác với kết nối đã OPEN rồi nhận Close frame.

## Tài liệu tham khảo chính thức

- [RFC 6455 — The WebSocket Protocol](https://www.rfc-editor.org/rfc/rfc6455)
- [RFC 8441 — Bootstrapping WebSockets with HTTP/2](https://www.rfc-editor.org/rfc/rfc8441)
- [Spring Framework — WebSocket Overview](https://docs.spring.io/spring-framework/reference/web/websocket.html)
- [Spring Framework — WebSocket Server API and Handshake](https://docs.spring.io/spring-framework/reference/web/websocket/server.html)
- [Spring Framework — STOMP over WebSocket](https://docs.spring.io/spring-framework/reference/web/websocket/stomp.html)
- [Spring Framework — Enable STOMP](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/enable.html)
- [Spring Framework — WebFlux WebSocket](https://docs.spring.io/spring-framework/reference/web/webflux-websocket.html)
- [Spring Framework 6.1 — SubProtocolCapable Javadoc](https://docs.spring.io/spring-framework/docs/6.1.21/javadoc-api/org/springframework/web/socket/SubProtocolCapable.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="websocket-stomp-003">WebSocketHandler, TextWebSocketHandler và đăng ký endpoint Servlet</a>

<details>
<summary>Xem chi tiết</summary>

# WebSocketHandler, TextWebSocketHandler và đăng ký endpoint Servlet trong Spring Framework

## Mục tiêu học tập và phạm vi

Bài này trình bày cách tạo **WebSocket endpoint mức thấp (raw WebSocket)** trên **Spring MVC / Servlet stack** bằng giao diện `WebSocketHandler`, lớp tiện ích `TextWebSocketHandler` và cấu hình `@EnableWebSocket` + `WebSocketConfigurer`. Sau khi học, bạn có thể:

- Hiểu luồng **HTTP Upgrade handshake → WebSocketSession → xử lý message → đóng kết nối**.
- Phân biệt `WebSocketHandler` (hợp đồng callback) và `TextWebSocketHandler` (lớp cơ sở chuyên message dạng text).
- Đăng ký endpoint `/ws/echo` trên Servlet stack, hiểu vai trò của `WebSocketHandlerRegistry`, `ServletWebSocketHandlerRegistry` và Servlet container.
- Tạo ứng dụng Spring Boot/Java 17+ với server echo, trang HTML dùng trình duyệt để kết nối và gửi/nhận message.
- Biết nơi đặt xác thực, kiểm tra origin, quản lý session, giới hạn message và đồng bộ việc gửi khi nhiều luồng cùng ghi lên một WebSocket session.

**Phạm vi chính là raw WebSocket trên Servlet stack.** STOMP là giao thức nhắn tin nằm trên WebSocket và thường dùng `@EnableWebSocketMessageBroker`, broker, `@MessageMapping`. Những thành phần STOMP đó có vai trò khác và không cần thiết để xây dựng endpoint raw WebSocket trong bài.

## 1. WebSocket không giống REST HTTP request/response

HTTP REST thông thường là mô hình từng request nhận từng response. WebSocket bắt đầu bằng **một HTTP handshake** yêu cầu nâng cấp giao thức, rồi chuyển sang kết nối hai chiều sống lâu: server và client đều có thể gửi **frame** mà không cần mở HTTP request mới cho mỗi message.

Ví dụ đường đi:

```text
Browser hoặc WebSocket client
        |
        | HTTP GET /ws/echo
        | Upgrade: websocket
        | Connection: Upgrade
        | Sec-WebSocket-Key: ...
        | Sec-WebSocket-Version: 13
        v
Spring MVC HandlerMapping / WebSocketHttpRequestHandler
        |
        | handshake hợp lệ
        v
Servlet container nâng cấp kết nối
        |
        | HTTP 101 Switching Protocols
        v
WebSocketSession được thiết lập
        |
        +--- client -> text frame ---> handleTextMessage(...)
        |
        +--- server -> text frame ---> WebSocketSession.sendMessage(...)
        |
        +--- close / transport error -> callback đóng/lỗi
```

Các bước quan trọng:

1. **Đăng ký URL:** Spring biết `/ws/echo` được xử lý bởi WebSocket handler nào.
2. **Handshake:** HTTP Upgrade được xác nhận, kiểm tra origin/interceptor, thương lượng subprotocol hoặc extensions khi có.
3. **Session:** Sau khi upgrade thành công, hai phía dùng giao thức WebSocket; Spring cung cấp một `WebSocketSession` đại diện cho kết nối hiện tại.
4. **Message:** Callback nhận text/binary/pong; handler tự định nghĩa ngữ nghĩa payload.
5. **Close:** Hai phía trao đổi close frame khi có thể; server nhận callback đóng hoặc lỗi truyền tải và dọn tài nguyên.

Mỗi kết nối có một session riêng. **Một instance handler có thể xử lý nhiều sessions**, nên không lưu trạng thái người dùng của một kết nối trong biến instance thông thường nếu handler là bean singleton dùng chung.

### 1.1. URL HTTP và WebSocket

Trong ví dụ local, trang web là `http://localhost:8080/` còn endpoint WebSocket là `ws://localhost:8080/ws/echo`. Khi chạy qua HTTPS, client nên kết nối bằng `wss://`. Khác với HTTP polling, sau khi nâng cấp thành công không có một mã trạng thái HTTP mới cho từng WebSocket message.

**HTTP 101** báo nâng cấp thành công; tiếp đó dữ liệu đi qua text/binary frames. Nếu một message sai định dạng, ứng dụng có thể gửi thông báo lỗi dưới dạng frame, hoặc đóng session bằng mã close phù hợp; không “trả HTTP 400 cho frame tiếp theo”.

## 2. Giao diện WebSocketHandler

Spring Servlet dùng `org.springframework.web.socket.WebSocketHandler`. Đây là hợp đồng các callback quản lý lifecycle và message:

| Callback | Khi Spring gọi | Việc thường làm |
| --- | --- | --- |
| `afterConnectionEstablished(WebSocketSession)` | Kết nối được thiết lập | Khởi tạo context, ghi nhận session, gửi lời chào |
| `handleMessage(WebSocketSession, WebSocketMessage<?>)` | Có một message đến | Phân phối text/binary/pong hoặc xử lý chung |
| `handleTransportError(WebSocketSession, Throwable)` | Lỗi truyền tải xảy ra | Logging an toàn, đóng kết nối nếu còn mở, dọn tài nguyên |
| `afterConnectionClosed(WebSocketSession, CloseStatus)` | Kết nối đã đóng | Xóa session khỏi bộ nhớ, giải phóng subscription/resource |
| `supportsPartialMessages()` | Framework hỏi khả năng xử lý message phân mảnh | Trả về có hỗ trợ các partial messages không |

Chữ ký các callback chính có thể phát sinh `Exception`. Nếu tự implement trực tiếp, bạn phải xử lý đủ tất cả các phương thức cần thiết. Trong các trường hợp chỉ cần text, kế thừa `TextWebSocketHandler` thường gọn và rõ hơn.

**Không nhầm hai lớp khác package:**

- Servlet: `org.springframework.web.socket.WebSocketHandler`, `WebSocketSession`, `TextMessage`.
- Reactive WebFlux: `org.springframework.web.reactive.socket.WebSocketHandler`, `WebSocketSession` và `WebSocketMessage`; API dùng Reactor `Mono<Void> handle(WebSocketSession)`.

Hai hệ này cùng giải quyết WebSocket nhưng **không thay thế trực tiếp interface cho nhau**. Bài này sử dụng hoàn toàn Servlet stack và các import từ `org.springframework.web.socket.*`.

### 2.1. WebSocketMessage và WebSocketSession

Trong raw Servlet WebSocket:

- `TextMessage` mang chuỗi UTF-8/text payload, lấy bằng `getPayload()`.
- `BinaryMessage` mang dữ liệu nhị phân (tùy API có `ByteBuffer`).
- `PongMessage` biểu diễn pong control message. Ping/pong có thể được container quản lý ở mức thấp; khi cần heartbeat, thiết kế theo container và giao thức cụ thể.
- `WebSocketSession` có `getId()`, `getPrincipal()`, `getAttributes()`, `getHandshakeHeaders()`, `isOpen()`, `sendMessage(...)` và `close(...)`.

`session.getAttributes()` là map thuộc session WebSocket, có thể chứa dữ liệu được truyền vào từ handshake interceptor. Nó không tự động là một Spring HTTP session đầy đủ. `getPrincipal()` phụ thuộc việc HTTP handshake đã được xác thực/thiết lập principal trong hệ thống hay chưa.

## 3. TextWebSocketHandler: lựa chọn tự nhiên cho text frame

`TextWebSocketHandler` thuộc package `org.springframework.web.socket.handler`, kế thừa `AbstractWebSocketHandler` và xử lý callback cho message text thông qua:

```java
@Override
protected void handleTextMessage(WebSocketSession session,
                                 TextMessage message) throws Exception {
    String text = message.getPayload();
    session.sendMessage(new TextMessage("ECHO:" + text));
}
```

Đây là **đoạn nằm trong subclass**, chưa phải file Java chạy độc lập. Client gửi `hello` thì server gửi `ECHO:hello` trở lại **cùng session**. Client khác không nhận frame này vì không có cơ chế broadcast.

`TextWebSocketHandler` nhận những callback lifecycle mặc định từ `AbstractWebSocketHandler`. Nếu đến binary message, handler cơ sở này từ chối với `CloseStatus.NOT_ACCEPTABLE`; do đó không dùng `TextWebSocketHandler` khi endpoint cần upload/broadcast nội dung nhị phân, trừ khi bạn chủ động định nghĩa lại cách xử lý. Trường hợp cần binary, xem `BinaryWebSocketHandler` hoặc implement handler phù hợp.

### 3.1. Vòng đời, dọn dẹp tài nguyên

Một handler thường override thêm:

```java
@Override
public void afterConnectionEstablished(WebSocketSession session)
        throws Exception {
    // Session có thể bắt đầu nhận/gửi WebSocket message.
}

@Override
public void afterConnectionClosed(WebSocketSession session,
                                  CloseStatus status)
        throws Exception {
    // Dọn state, subscriptions hoặc session registry.
}

@Override
public void handleTransportError(WebSocketSession session,
                                 Throwable exception)
        throws Exception {
    // Log lỗi đã loại thông tin nhạy cảm; cân nhắc đóng session.
}
```

**CloseStatus** dùng mã theo giao thức WebSocket. Ví dụ: 1000 = đóng bình thường, 1008 = vi phạm chính sách, 1009 = message quá lớn, 1011 = lỗi server. Không áp dụng HTTP status code vào API `session.close(...)`.

### 3.2. Có cần lưu tất cả WebSocketSession?

- **Echo một người:** không cần map sessions. Callback đã nhận `WebSocketSession`; phản hồi cho chính session đó.
- **Chat/broadcast:** cần quản lý registry các session đang mở (ví dụ `ConcurrentHashMap`), dọn vào callback close/error, kiểm tra `isOpen()` và quyền nhận message.
- **Nhiều instance server:** registry trong RAM chỉ nhìn thấy session của instance hiện tại. Broadcast toàn hệ thống cần cơ chế fan-out qua broker/pub-sub hoặc kiến trúc tương đương; không thể coi một `ConcurrentHashMap` cục bộ là kết nối toàn cluster.

## 4. Đăng ký endpoint trên Servlet stack

Cách chuẩn trên Spring MVC dùng cấu hình sau:

```java
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final EchoTextWebSocketHandler echoHandler;

    public WebSocketConfig(EchoTextWebSocketHandler echoHandler) {
        this.echoHandler = echoHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(echoHandler, "/ws/echo");
    }
}
```

Imports cho đoạn này:

```java
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
```

Ý nghĩa:

- `@Configuration` khai báo cấu hình Spring.
- `@EnableWebSocket` bật cơ chế cấu hình raw WebSocket trên Servlet stack.
- `WebSocketConfigurer` là callback cấu hình mapping WebSocket handler.
- `WebSocketHandlerRegistry.addHandler(handler, paths...)` gắn instance handler với URL handshake.
- Spring MVC dùng cơ chế registry cho Servlet để tạo các handler mappings và phần xử lý handshake; ứng dụng thường không cần khởi tạo `ServletWebSocketHandlerRegistry` bằng tay.

`ServletWebSocketHandlerRegistry` là implementation của `WebSocketHandlerRegistry` dùng trong môi trường Servlet, nơi các đường dẫn được gắn vào Spring MVC handler mappings. Đây là lớp hạ tầng nội bộ/phần cấu hình, **không phải business handler** và cũng không phải controller REST.

### 4.1. Phân biệt các cách đăng ký

| Cơ chế | Mục đích | Khi dùng |
| --- | --- | --- |
| `@EnableWebSocket` + `WebSocketConfigurer` + `addHandler` | Raw WebSocket qua Spring Servlet | Cần trực tiếp xử lý text/binary frame |
| `@EnableWebSocketMessageBroker` + `registerStompEndpoints` | STOMP over WebSocket | Có destination, subscription, broker, `@MessageMapping` |
| `@ServerEndpoint("/...")` của Jakarta WebSocket | API WebSocket chuẩn Jakarta | Ứng dụng cố ý chọn Jakarta endpoint model |
| Reactive `HandlerMapping` + `WebSocketHandlerAdapter` | Raw WebSocket WebFlux | Ứng dụng chạy reactive HTTP stack |

Không đặt `@GetMapping("/ws/echo")` rồi trả string để “đăng ký WebSocket”. Một controller GET thông thường tạo HTTP response, không tự nâng cấp protocol. `@ServerEndpoint` là cơ chế khác; nếu dùng embedded Servlet container theo Spring Boot thì có yêu cầu registration riêng, không cần cho đường đi `@EnableWebSocket` của bài này.

### 4.2. Vị trí trong request flow

Trong Spring MVC, request handshake `GET /ws/echo` đi qua Servlet container và được định tuyến đến handler mapping cho WebSocket; từ đó Spring dùng đối tượng xử lý handshake như `WebSocketHttpRequestHandler` và tầng `HandshakeHandler`/`RequestUpgradeStrategy` để nâng cấp giao thức. Container thực hiện công việc WebSocket I/O sau upgrade.

Sơ đồ trách nhiệm:

```text
DispatcherServlet / HandlerMappings (HTTP Upgrade GET)
         |
         v
WebSocket registration ("/ws/echo")
         |
         v
WebSocketHttpRequestHandler -> HandshakeHandler
         |
         v
Servlet container adapter nâng cấp protocol
         |
         v
WebSocketSession + WebSocketHandler callbacks
```

`DefaultHandshakeHandler` có thể được tùy biến nếu cần thương lượng subprotocol, origin hoặc chiến lược upgrade riêng. Với ứng dụng Servlet thông thường, cấu hình mặc định qua registry là đủ.

## 5. Ví dụ hoàn chỉnh có thể chạy: Spring Boot WebSocket echo

Ví dụ dùng **Spring Boot 3.3.6, Java 17 và embedded Tomcat**, không cần Redis, STOMP hay broker. Trang HTML được Spring Boot phục vụ cùng origin với endpoint, giúp quan sát chính xác sự khác nhau giữa kết nối HTTP ban đầu và các frame WebSocket tiếp theo.

**Cấu trúc dự án:**

```text
servlet-websocket-demo/
├── build.gradle
└── src/
    └── main/
        ├── java/demo/websocket/
        │   ├── ServletWebSocketDemoApplication.java
        │   ├── EchoTextWebSocketHandler.java
        │   └── WebSocketConfig.java
        └── resources/static/index.html
```

### 5.1. build.gradle

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.6'
    id 'io.spring.dependency-management' version '1.1.6'
}

group = 'demo'
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
    implementation 'org.springframework.boot:spring-boot-starter-websocket'
}
```

Starter này cung cấp hỗ trợ WebSocket cho ứng dụng Spring MVC và môi trường Servlet container tương ứng. Gradle/Spring Boot quản lý các phiên bản Spring Framework, Jakarta Servlet, Tomcat và websocket modules tương thích. Không cần trộn thủ công các bản `javax.websocket.*` cũ vào dự án Spring Boot 3 (sử dụng hệ `jakarta.*`).

### 5.2. ServletWebSocketDemoApplication.java

```java
package demo.websocket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ServletWebSocketDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(ServletWebSocketDemoApplication.class, args);
    }
}
```

Main class bật Spring Boot và component scan trong package `demo.websocket`. Lớp handler `@Component` và cấu hình `@Configuration` phía dưới sẽ được Spring phát hiện.

### 5.3. EchoTextWebSocketHandler.java

```java
package demo.websocket;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class EchoTextWebSocketHandler extends TextWebSocketHandler {

    @Override
    public void afterConnectionEstablished(WebSocketSession session)
            throws IOException {
        session.sendMessage(new TextMessage("READY"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session,
                                     TextMessage message)
            throws IOException {
        String payload = message.getPayload();

        // Giới hạn minh họa theo Java String length; production còn
        // cần giới hạn bytes/frame và buffer ở WebSocket container.
        if (payload.length() > 1024) {
            session.close(CloseStatus.TOO_BIG_TO_PROCESS);
            return;
        }

        session.sendMessage(new TextMessage("ECHO:" + payload));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session,
                                      CloseStatus status) {
        System.out.println("CLOSED " + status.getCode());
    }

    @Override
    public void handleTransportError(WebSocketSession session,
                                     Throwable exception)
            throws IOException {
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }
}
```

Giải thích từng callback:

1. Kết nối thành công → `afterConnectionEstablished` gửi `READY`.
2. Client gửi text payload → `handleTextMessage` đọc `message.getPayload()`.
3. Nếu chuỗi vượt quá 1.024 UTF-16 code units trong ví dụ → đóng session bằng 1009. Đây **không phải giới hạn byte mạng hoàn chỉnh**, vì chữ Unicode có độ dài UTF-8 khác nhau; bảo vệ thực tế cần cấu hình giới hạn ở container.
4. Nếu hợp lệ → gửi `ECHO:` cộng nội dung về **chính client đó**.
5. Đóng kết nối → Spring gọi `afterConnectionClosed`. In mã đóng để quan sát; trong ứng dụng thực tế, nếu có registry/subscription hãy dọn ở đây.
6. Lỗi transport → thử đóng session còn mở bằng 1011. Nếu có nhiều công việc background, handler phải dọn dẹp tài nguyên liên quan, không chỉ đóng socket.

**Tại sao handler không giữ session trong field?** Vì ví dụ chỉ phản hồi ngay session nhận được qua callback. Một bean handler có thể dùng cho rất nhiều kết nối; thêm field như `private WebSocketSession currentSession;` sẽ làm các client ghi đè session của nhau.

### 5.4. WebSocketConfig.java

```java
package demo.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final EchoTextWebSocketHandler echoHandler;

    public WebSocketConfig(EchoTextWebSocketHandler echoHandler) {
        this.echoHandler = echoHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(echoHandler, "/ws/echo");
        // Mặc định chính sách origin cùng nguồn phù hợp demo này.
    }
}
```

Đây chính là bước đăng ký **endpoint Servlet**: `/ws/echo` được mapping đến bean handler. Cần đảm bảo configuration được Spring scan; ở đây package trùng với package của `@SpringBootApplication`.

### 5.5. src/main/resources/static/index.html

Trang HTML tĩnh này được Spring Boot phục vụ tại `http://localhost:8080/`. Người dùng nhấn “Connect”, gửi một đoạn text và quan sát khung nhận. JS tự chọn `ws:` hoặc `wss:` phù hợp protocol của trang.

```html
<!doctype html>
<html lang="vi">
<head>
  <meta charset="utf-8">
  <title>Spring Servlet WebSocket Echo</title>
  <style>
    body { font-family: system-ui, sans-serif; max-width: 760px;
           margin: 32px auto; padding: 0 16px; }
    button, input { padding: 8px; margin: 4px 0; }
    #events { white-space: pre-wrap; padding: 12px;
              background: #f3f4f6; min-height: 180px; }
  </style>
</head>
<body>
  <h1>Raw WebSocket Echo</h1>
  <button id="connect">Connect</button>
  <button id="close" disabled>Close</button>
  <p>
    <input id="message" value="hello" aria-label="Message">
    <button id="send" disabled>Send</button>
  </p>
  <pre id="events" aria-live="polite"></pre>

  <script>
    const connectButton = document.querySelector("#connect");
    const closeButton = document.querySelector("#close");
    const sendButton = document.querySelector("#send");
    const input = document.querySelector("#message");
    const output = document.querySelector("#events");

    let socket = null;

    function log(message) {
      output.textContent += message + "\n";
    }

    connectButton.addEventListener("click", () => {
      const scheme = location.protocol === "https:" ? "wss:" : "ws:";
      const url = scheme + "//" + location.host + "/ws/echo";
      socket = new WebSocket(url);

      connectButton.disabled = true;

      socket.onopen = () => {
        log("OPEN");
        sendButton.disabled = false;
        closeButton.disabled = false;
      };

      socket.onmessage = event => {
        log("RECV " + event.data);
      };

      socket.onerror = () => {
        log("ERROR");
      };

      socket.onclose = event => {
        log("CLOSE " + event.code);
        connectButton.disabled = false;
        sendButton.disabled = true;
        closeButton.disabled = true;
        socket = null;
      };
    });

    sendButton.addEventListener("click", () => {
      if (socket && socket.readyState === WebSocket.OPEN) {
        socket.send(input.value);
        log("SEND " + input.value);
      }
    });

    closeButton.addEventListener("click", () => {
      if (socket && socket.readyState === WebSocket.OPEN) {
        socket.close(1000, "done");
      }
    });
  </script>
</body>
</html>
```

Sử dụng `textContent` để hiển thị phản hồi giúp message như `<script>` không bị diễn giải thành HTML. Nếu hiển thị thông điệp của người dùng khác, tránh ghép chúng vào `innerHTML` khi chưa xử lý an toàn.

### 5.6. Cách chạy và quan sát

Từ thư mục chứa `build.gradle`, cài JDK 17+ và Gradle phù hợp, chạy:

```bash
gradle bootRun
```

Nếu đã tạo Gradle Wrapper, dùng `./gradlew bootRun` (Unix/macOS) hoặc `gradlew.bat bootRun` (Windows).

1. Mở `http://localhost:8080/` trên trình duyệt.
2. Nhấn **Connect**, quan sát `OPEN` và message `READY` được server gửi về.
3. Giữ input `hello` rồi nhấn **Send**.
4. Quan sát browser hiển thị `SEND hello` và `RECV ECHO:hello`.
5. Nhấn **Close**, quan sát `CLOSE 1000` trên browser và `CLOSED 1000` trong stdout của ứng dụng server khi callback close được xử lý.

**Các dòng ứng dụng dự kiến tạo ra tại UI, theo thứ tự thao tác trên** (log khởi động, timing mạng và event close có thể tạo thêm thông tin riêng):

```text
OPEN
RECV READY
SEND hello
RECV ECHO:hello
CLOSE 1000
```

Không phải các dòng trên đều là stdout của Java; đó là output từ vùng `<pre id="events">` của trang demo. Java chỉ in `CLOSED 1000` khi đóng bình thường. Nếu handshake lỗi hoặc khác origin, bạn có thể thấy `ERROR`/`CLOSE 1006` ở trình duyệt; 1006 là trạng thái trình duyệt báo mất kết nối, không phải close frame được gửi bởi server.

### 5.7. Request thật đi qua các thành phần nào?

Trong lần nhấn **Connect**:

```text
new WebSocket("ws://localhost:8080/ws/echo")
  -> browser gửi HTTP Upgrade
  -> Tomcat nhận kết nối
  -> DispatcherServlet tìm mapping /ws/echo
  -> Spring handshake + Servlet WebSocket upgrade
  -> HTTP 101
  -> EchoTextWebSocketHandler.afterConnectionEstablished
  -> session.sendMessage("READY")
  -> browser.onmessage nhận READY
```

Trong lần nhấn **Send**:

```text
browser.socket.send("hello")
  -> WebSocket text frame trên kết nối hiện hữu
  -> EchoTextWebSocketHandler.handleTextMessage
  -> message.getPayload() == "hello"
  -> session.sendMessage(TextMessage("ECHO:hello"))
  -> browser.onmessage nhận "ECHO:hello"
```

Sau khi upgrade, **không có thêm Servlet HTTP GET cho mỗi frame**. Controller REST hoặc filter HTTP thông thường không được gọi lại mỗi khi WebSocket gửi một message. Nếu cần kiểm tra quyền cho từng lệnh trong raw WebSocket, đặt kiểm tra đó trong logic xử lý message hoặc tầng dịch vụ tương ứng.

## 6. HandshakeInterceptor, Origin và xác thực

Handshake bắt đầu bằng HTTP request nên có thể mang header, cookie, thông tin xác thực, query và origin. Spring cung cấp `HandshakeInterceptor` với hai callback `beforeHandshake(...)` và `afterHandshake(...)`.

Ví dụ cấu hình mở rộng (đây là **biến thể minh họa**, thay thế method đăng ký trong file `WebSocketConfig.java` ở trên nếu áp dụng):

```java
@Override
public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(echoHandler, "/ws/echo")
            .addInterceptors(new HttpSessionHandshakeInterceptor())
            .setAllowedOrigins("https://app.example.com");
}
```

Import thêm `org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor`.

- `HttpSessionHandshakeInterceptor` có thể đưa các thuộc tính HTTP session vào map attributes của WebSocket session. Nó **không thay thế xác thực**.
- `setAllowedOrigins(...)` chỉ cho phép browser origin đã khai báo; khi vận hành thật phải đặt origin chính xác theo frontend được phép.
- `setAllowedOriginPatterns(...)` hỗ trợ pattern linh hoạt, nhưng phải giới hạn miền rõ ràng.
- Nếu application phục vụ cả trang HTML và WebSocket trên cùng host/port, chính sách same-origin mặc định phù hợp.
- Không coi `Origin` như một cơ chế authentication: non-browser client có thể tự đặt header này. Vẫn phải xác thực người gọi và kiểm tra quyền truy cập.

**Cẩn thận với ví dụ:** nếu thay config bằng `setAllowedOrigins("https://app.example.com")` trong lúc vẫn mở demo từ `http://localhost:8080/`, browser sẽ bị từ chối handshake vì origin không nằm trong danh sách. Muốn chạy demo local hãy giữ cấu hình ban đầu hoặc thêm rõ origin local phù hợp.

### 6.1. Khi cần HandshakeInterceptor tùy chỉnh

Có thể tạo interceptor kiểm tra trạng thái trước khi chấp nhận nâng cấp:

```java
public final class AttributeHandshakeInterceptor
        implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {

        attributes.put("connectedAt", java.time.Instant.now());
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // Giải phóng tài nguyên hoặc ghi metric nếu cần.
    }
}
```

Đây là **đoạn minh họa cần đặt trong file cùng package và import**:
`org.springframework.web.socket.server.HandshakeInterceptor`,
`org.springframework.http.server.ServerHttpRequest`,
`org.springframework.http.server.ServerHttpResponse`,
`org.springframework.web.socket.WebSocketHandler`,
`java.util.Map`.

Đăng ký bằng `.addInterceptors(new AttributeHandshakeInterceptor())`. Sau handshake, lấy `session.getAttributes().get("connectedAt")` trong handler. Nếu `beforeHandshake` trả `false`, Spring không tiếp tục nâng cấp; response cần được cấu hình phù hợp khi từ chối.

**Bảo mật:** Nếu dùng cookie phiên để tự động xác thực WebSocket từ trình duyệt, cần kiểm tra Origin và authorization cho user/room/resource cụ thể, tránh Cross-Site WebSocket Hijacking. Trong môi trường reverse proxy, phải chuyển tiếp chính xác header Upgrade/Connection và WSS, đồng thời bảo đảm session/routing phù hợp.

## 7. Gửi đồng thời và quản lý session đúng cách

Thông thường session thuộc về một kết nối TCP/WebSocket. Nhiều thread cùng gọi `session.sendMessage(...)` có thể gây race hoặc lỗi với WebSocket container theo chuẩn Jakarta WebSocket. Spring khuyến nghị **đồng bộ việc gửi** nếu có nhiều nguồn phát.

Một cách là bọc session bằng `ConcurrentWebSocketSessionDecorator`:

```java
WebSocketSession safeSession =
        new ConcurrentWebSocketSessionDecorator(
                rawSession,
                10_000,       // giới hạn thời gian send, ms
                64 * 1024);   // giới hạn bộ đệm send, bytes
```

Import `org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator`. Đây là **snippet**, giả định `rawSession` đang có trong callback; không copy snippet vào file demo mà chưa thiết kế vòng đời registry phù hợp.

Decorator bảo đảm tại một thời điểm chỉ một thread thực hiện send trực tiếp; những send kế tiếp có thể vào buffer. Vượt giới hạn thời gian/kích thước buffer sẽ dẫn đến hành vi overflow/đóng session theo cấu hình. Nó **không phải message broker** và không tự đồng bộ session giữa các server node.

### 7.1. Broadcast registry

Nếu xây chat room, cần một cấu trúc dạng:

```java
private final Map<String, WebSocketSession> sessions =
        new ConcurrentHashMap<>();
```

Trong `afterConnectionEstablished`, thêm session; trong `afterConnectionClosed` và đường lỗi, xóa session; khi broadcast, kiểm tra session còn mở, quyền subscribe và thực hiện send tuần tự/qua decorator. Nếu dùng decorator, **đặt session đã bọc vào registry một lần** và dùng chính instance đó để gửi; không tạo decorator mới cho mỗi frame rồi tưởng rằng các lần send được đồng bộ.

**Đây là thiết kế bổ sung**, không cần cho chương trình echo runnable ở trên. Tránh broadcast về mọi session khi chưa xác định room/user/authorization, vì có thể rò dữ liệu giữa người dùng.

### 7.2. Backpressure và giới hạn tài nguyên

Raw Servlet WebSocket API là callback-based, không tự cung cấp reactive backpressure kiểu Reactor `Flux`. Để bảo vệ server cần thiết kế:

- Giới hạn kích thước inbound text/binary message và số kết nối.
- Giới hạn hàng đợi outbound, thời gian gửi, mức độ broadcast và tốc độ message của từng user.
- Đóng session chậm/không còn hợp lệ và dọn session registry.
- Đặt idle timeout/heartbeat theo container và load balancer.
- Hạn chế log payload nhạy cảm, validate message schema và kiểm tra phân quyền ở **mỗi lệnh quan trọng**.

Giới hạn `payload.length() > 1024` trong demo chỉ minh họa kiểm tra ứng dụng; message lớn có thể bị container từ chối **trước khi tới callback**. Với Servlet container dùng Jakarta WebSocket, Spring có `ServletServerContainerFactoryBean` để chỉnh buffer và timeout:

```java
@Bean
ServletServerContainerFactoryBean webSocketContainer() {
    ServletServerContainerFactoryBean container =
            new ServletServerContainerFactoryBean();
    container.setMaxTextMessageBufferSize(8192);
    container.setMaxBinaryMessageBufferSize(8192);
    return container;
}
```

Import `org.springframework.context.annotation.Bean` và `org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean`. Đây là cấu hình tùy chọn đặt trong `@Configuration`; lựa chọn kích cỡ/timeout cần cân đối với payload thật và loại Servlet container đang chạy.

## 8. Raw WebSocket so với STOMP trên WebSocket

Bài đang học trực tiếp xử lý `TextMessage`. Khi client gửi chuỗi `hello`, handler hiểu theo quy ước của ứng dụng và tự gửi `ECHO:hello`. Spring **không tự thêm destination, subscription hay broker** trong cách đăng ký `registry.addHandler(echoHandler, "/ws/echo")`.

STOMP là giao thức tầng ứng dụng có lệnh như `CONNECT`, `SUBSCRIBE`, `SEND` và destination. Khi xây STOMP over WebSocket bằng Spring, thường cấu hình `@EnableWebSocketMessageBroker`, `StompEndpointRegistry` và message broker. Client STOMP gửi frame có headers/body theo chuẩn STOMP, không chỉ raw text tùy ý.

| Đặc điểm | Raw WebSocketHandler | STOMP over WebSocket |
| --- | --- | --- |
| Đăng ký endpoint | `WebSocketConfigurer.registerWebSocketHandlers` | `WebSocketMessageBrokerConfigurer.registerStompEndpoints` |
| Nhận dữ liệu | Callback `handleTextMessage` hoặc `handleMessage` | Broker/messaging routing và `@MessageMapping` |
| Định dạng message | Ứng dụng tự định nghĩa | STOMP frame có command và headers |
| Broadcast | Tự quản lý session/room/queue | Có cơ chế broker destination |
| Phù hợp | Echo, protocol riêng, data stream tùy biến | Chat/pub-sub có destination và subscription |

**Đừng ghép nhầm:** Một client cấu hình STOMP sẽ không tự giao tiếp đúng với echo handler raw nếu server không có logic hiểu frame STOMP. Ngược lại, browser WebSocket raw gửi chuỗi `hello` không thay thế lệnh `SEND` STOMP hợp lệ.

## 9. Lỗi thực tế và cách chẩn đoán

### 9.1. Handshake thất bại, không vào handleTextMessage

Kiểm tra lần lượt:

1. URL có đúng `/ws/echo` không, có sai port/context-path/reverse proxy không?
2. Client đang dùng `ws://` với HTTP và `wss://` với HTTPS đúng không?
3. `@EnableWebSocket` và `WebSocketConfigurer` có nằm trong component scan không?
4. Trên Servlet stack đã có websocket support từ container và dependency đúng chưa?
5. Browser `Origin` có được policy cho phép không?
6. HTTP auth/filter/proxy có chặn GET Upgrade hoặc bỏ `Connection/Upgrade` header không?
7. Có controller route, servlet mapping hoặc endpoint khác chiếm cùng đường dẫn không?

Trong DevTools → Network → WS, handshake thành công thường hiển thị **101 Switching Protocols**. Nếu nhận 400/401/403/404/500, phân biệt rõ **lỗi trước upgrade** với **close code sau upgrade**.

### 9.2. Kết nối thành công nhưng không nhận dữ liệu

- Client chỉ tạo `new WebSocket(...)` mà chưa gọi `send(...)`.
- Handler không override đúng `handleTextMessage(WebSocketSession, TextMessage)`.
- Server gửi phản hồi nhầm session hoặc đã đóng session.
- Client hiển thị message trước khi đăng ký `onmessage` (nên đặt callback ngay sau khi tạo đối tượng).
- Message là binary nhưng handler chỉ nhận text.
- Việc gửi từ nhiều threads làm xung đột. Dùng cơ chế serialize send.
- Client đóng tab hay proxy ngắt kết nối vì idle timeout.

### 9.3. Hiểu sai scope và lifecycle

Không bao giờ giữ một `WebSocketSession` trong field mutable chung như `currentSession` của singleton handler để đại diện toàn bộ người dùng. Một người reconnect sẽ nhận session mới, và session cũ có thể đóng bất kỳ lúc nào. Với broadcast registry phải dọn lifecycle, không giữ session dead vô hạn.

### 9.4. Nhầm lỗi HTTP với lỗi WebSocket

- HTTP 101 = handshake đã upgrade.
- WebSocket close 1000 = đóng bình thường.
- Close 1009 = message quá lớn.
- Close 1011 = server error.
- Browser có thể báo 1006 khi mất kết nối bất thường; 1006 không phải mã close frame gửi ra trên dây.

Không trả JSON HTTP 404 từ `handleTextMessage` sau khi đã nâng cấp kết nối; muốn biểu diễn lỗi nghiệp vụ dùng message schema có mã lỗi của ứng dụng hoặc đóng WebSocket theo hợp đồng.

### 9.5. Quên bảo mật hoặc coi Origin là xác thực

Allowed origins là một lớp bảo vệ cho browser handshake, không thay cho authentication. Kết nối WebSocket có thể sống dài sau khi quyền người dùng thay đổi, nên cân nhắc xác minh session, token expiry, quyền vào room, giới hạn tần suất và việc thu hồi kết nối. Không đưa token vào query nếu có thể tránh vì URL hay bị log; thiết kế phương thức xác thực hợp lý cho browser (cookie/session qua HTTPS với CSRF/origin protections hoặc cơ chế token được xem xét kỹ).

## 10. Bài tập tự luyện

**Bài tập 1 — Theo dõi lifecycle:** Chạy demo, kết nối từ hai tab trình duyệt. Mỗi tab nhận `READY`. Gửi `hello A` ở tab A. Giải thích vì sao tab B không nhận `ECHO:hello A`. Đóng tab A và xem callback close.

**Bài tập 2 — Thêm command:** Trong `handleTextMessage`, nếu payload đúng `PING` thì trả `PONG` theo protocol của ứng dụng; với các chuỗi khác tiếp tục `ECHO:...`. Lưu ý đây là **text message ứng dụng**, không phải WebSocket protocol-level Ping/Pong control frame.

**Bài tập 3 — Kiểm tra giới hạn:** Gửi đoạn text dài hơn 1.024 ký tự Java nhưng dưới giới hạn frame của container. Dự đoán browser nhận close code nào. Thay đổi cấu hình buffer của container và phân tích tại sao lỗi có thể diễn ra trước callback.

**Bài tập 4 — Origin và handshake:** Thêm `setAllowedOrigins("https://app.example.com")` rồi thử lại trang `http://localhost:8080/`. Dự đoán nguyên nhân handshake bị từ chối. Khôi phục cấu hình same-origin cho demo trước khi tiếp tục.

**Bài tập 5 — Thiết kế broadcast:** Không cần viết toàn bộ code, hãy mô tả nơi thêm/xóa session trong lifecycle callbacks, nơi kiểm tra quyền nhận message và lý do phải đồng bộ `sendMessage` khi nhiều producer cùng phát tin.

## Ghi nhớ trọng tâm

- `WebSocketHandler` là hợp đồng các callback lifecycle/message.
- `TextWebSocketHandler` là lớp cơ sở thích hợp để xử lý raw WebSocket **text frames** trên Servlet stack.
- `@EnableWebSocket` + `WebSocketConfigurer` + `WebSocketHandlerRegistry.addHandler(...)` đăng ký URL handshake; hạ tầng Servlet và Spring đảm nhận phần nâng cấp kết nối.
- Sau HTTP 101, xử lý dữ liệu bằng WebSocket frames, không đi qua REST controller cho từng message.
- `WebSocketSession` gắn với một kết nối, handler bean có thể phục vụ nhiều người dùng.
- Khi cần broadcast, xác thực, bảo vệ tài nguyên, đa luồng hoặc nhiều instance server, phải bổ sung quản lý lifecycle, quyền truy cập và đồng bộ send.
- Raw WebSocket và STOMP sử dụng cách cấu hình/lớp xử lý khác nhau; chúng không thể thay thế lẫn nhau nếu chưa thống nhất protocol.

## Tài liệu tham khảo

- Spring Framework — WebSocket API (Servlet): https://docs.spring.io/spring-framework/reference/web/websocket/server.html
- Spring Framework — WebSockets trên Servlet stack: https://docs.spring.io/spring-framework/reference/web/websocket.html
- Spring Framework — `WebSocketHandler` Javadoc: https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/web/socket/WebSocketHandler.html
- Spring Framework — `TextWebSocketHandler` Javadoc: https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/web/socket/handler/TextWebSocketHandler.html
- Spring Framework — `ConcurrentWebSocketSessionDecorator`: https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/web/socket/handler/ConcurrentWebSocketSessionDecorator.html
- Spring Boot — WebSockets: https://docs.spring.io/spring-boot/3.3/reference/messaging/websockets.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="websocket-stomp-004">HandshakeInterceptor, kiểm tra origin và thông tin người dùng</a>

<details>
<summary>Xem chi tiết</summary>

# HandshakeInterceptor, kiểm tra Origin và thông tin người dùng trong WebSocket/STOMP

## 1. Bối cảnh: WebSocket cần kiểm tra gì trước khi nâng cấp kết nối?

Trong ứng dụng Spring, trình duyệt có thể mở kết nối WebSocket để trao đổi dữ liệu thời gian thực: trò chuyện, thông báo hoặc cập nhật trạng thái. Khác với HTTP request thông thường, một kết nối WebSocket có thể tồn tại lâu và truyền nhiều thông điệp theo hai chiều. Nếu chấp nhận nhầm kết nối của một website không tin cậy hoặc gắn sai người dùng, hậu quả không chỉ là một response đơn lẻ: kẻ tấn công có thể gửi/nhận thông điệp trên một phiên kéo dài.

Giai đoạn bắt đầu bằng HTTP request *handshake*. Máy chủ xét các điều kiện kết nối, sau đó mới đồng ý nâng cấp lên WebSocket (khi thành công, HTTP status điển hình là **101 Switching Protocols**). Nếu ứng dụng dùng STOMP, client tiếp tục gửi frame STOMP `CONNECT` trên kết nối đã được thiết lập; từ đó mới có các frame `SUBSCRIBE`, `SEND` và những thông điệp nghiệp vụ khác.

```text
Trình duyệt                         Spring WebSocket/STOMP server
     |                                            |
     |-- HTTP GET /ws, Upgrade: websocket ------->|
     |   Origin: https://app.example.com          |
     |   Cookie: phiên đăng nhập (nếu có)         |
     |                                            v
     |                             HTTP security filter chain
     |                                            |
     |                             HandshakeInterceptor.beforeHandshake
     |                                  | kiểm tra request/Principal
     |                                  | gắn session attributes
     |                                            |
     |                             kiểm tra Origin và handshake handler
     |                                  |
     |<-------------- HTTP 101 (nếu được chấp nhận)
     |                                            |
     |--------- STOMP CONNECT frame ------------->|
     |                             clientInboundChannel:
     |                             ChannelInterceptor / bảo mật STOMP
     |<------------- STOMP CONNECTED -------------|
     |--------- STOMP SUBSCRIBE / SEND ---------->|
     |                             kiểm tra quyền từng message/destination
```

Ba câu hỏi khác nhau:

1. **Origin có được chấp nhận không?** Origin thể hiện nguồn gốc trang web đã khởi tạo kết nối trong ngữ cảnh trình duyệt; cơ chế này góp phần chống hành vi mở WebSocket trái phép từ website khác.
2. **Người dùng là ai?** Trên đường HTTP handshake, ứng dụng thường đã xác thực bằng Spring Security/session hoặc cơ chế HTTP tương đương; `Principal` đã được xác thực có thể được liên kết với WebSocket session.
3. **Người dùng được phép gửi hoặc đăng ký thông điệp nào?** Đây là kiểm soát quyền theo frame/destination trong STOMP, **không được bỏ qua chỉ vì handshake thành công**.

`HandshakeInterceptor` phù hợp với việc quan sát và chuẩn bị handshake, cho phép từ chối sớm hoặc chuyển metadata sang WebSocket session. Nó không thay thế HTTP authentication, chính sách Origin hay kiểm tra quyền gửi message.

## 2. Phân biệt các thành phần của Spring

| Thành phần | Chạy lúc nào? | Vai trò |
| --- | --- | --- |
| `SecurityFilterChain` (Servlet) | Khi HTTP request đi vào, trước khi upgrade | Xác thực HTTP, phân quyền truy cập endpoint, chính sách CSRF/headers phù hợp |
| `HandshakeInterceptor` | Trước và sau xử lý handshake | Kiểm tra request, từ chối handshake, thêm thuộc tính cho WebSocket session |
| `HandshakeHandler` / `DefaultHandshakeHandler` | Trong bước thương lượng HTTP upgrade | Kiểm tra khả năng upgrade, Origin theo cấu hình, sub-protocol và thiết lập Principal |
| `WebSocketSession` | Sau khi WebSocket được mở | Thể hiện một kết nối, ID, headers và attributes, Principal |
| `ChannelInterceptor` | Khi xử lý message trên channel như `clientInboundChannel` | Đọc/kiểm soát STOMP `CONNECT`, `SEND`, `SUBSCRIBE` và những frame tương ứng |
| Spring Security message authorization | Trong chuỗi xử lý STOMP message inbound | Quyết định ai có thể gửi/đăng ký destination nào |
| `@MessageMapping` / `@SendTo` | Khi message STOMP được điều phối đến controller | Xử lý nghiệp vụ, gửi response đến broker/destination |

Không nhầm `HandshakeInterceptor` với `HandlerInterceptor` dành cho HTTP MVC request thông thường. Cũng không nhầm WebSocket raw handler (`WebSocketConfigurer` và `WebSocketHandler`) với STOMP endpoint (`WebSocketMessageBrokerConfigurer` và `StompEndpointRegistry`).

### 2.1 Hợp đồng HandshakeInterceptor

Interface có hai callback:

```java
boolean beforeHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Map<String, Object> attributes) throws Exception;

void afterHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Exception exception);
```

- `beforeHandshake` nhận HTTP request/response, handler đích và map attributes của WebSocket session. **Trả `true`** để tiếp tục handshake; **trả `false`** để chặn. Nếu chặn, có thể đặt status (ví dụ 401 hoặc 403) lên response trước khi trả về `false`.
- Map `attributes` được Spring **sao chép** sang session, không dùng nguyên map reference mà interceptor đã nhận. Đặt các giá trị nhỏ, ổn định; không dùng nó như kênh truyền credential bí mật hoặc object có tài nguyên chưa được giải phóng.
- `afterHandshake` chạy sau bước handshake để quan sát kết quả/ngoại lệ nếu có. Nó không có quyền “trả lại true” để phục hồi handshake bị từ chối và **không phải callback sau mỗi STOMP message**.
- Tham số `WebSocketHandler` là handler được cấu hình cho endpoint; trong ứng dụng STOMP, Spring sử dụng hạ tầng xử lý sub-protocol đằng sau endpoint này.

Không dùng `attributes.put("user", "alice")` để tự tạo `Principal` hoặc nâng quyền cho một kết nối. Thuộc tính chỉ là metadata của session; nhận dạng và xác thực cần đến từ thành phần bảo mật đã được tin cậy.

## 3. Kiểm tra Origin: nguyên nhân, cấu hình và giới hạn

### 3.1 Origin không đồng nghĩa Host, Referer hoặc CORS thông thường

Ví dụ trang ở `https://app.example.com` mở `wss://api.example.com/ws`. HTTP WebSocket handshake từ trình duyệt có thể mang:

```http
GET /ws HTTP/1.1
Host: api.example.com
Upgrade: websocket
Connection: Upgrade
Origin: https://app.example.com
Sec-WebSocket-Version: 13
Sec-WebSocket-Key: <giá trị dùng cho handshake>
```

`Origin` phản ánh nguồn gốc trang gọi (scheme + host + port), không phải URL của server đích, cũng không phải `Referer`. Với các công cụ máy chủ/CLI, header `Origin` có thể bị tự đặt hoặc giả mạo. Vì vậy kiểm tra Origin nhằm phòng chống những tình huống liên quan đến **browser**, không đủ để xác thực người dùng hoặc bảo vệ API khỏi client tùy biến.

Trình duyệt không áp dụng nguyên cơ chế same-origin của XHR/fetch để cấm mở WebSocket cross-origin. Máy chủ phải quyết định Origin nào được phép, nhất là khi credential được gửi qua cookie (rủi ro **Cross-Site WebSocket Hijacking**, hay CSWSH).

### 3.2 Cách khuyến nghị: setAllowedOrigins trên endpoint

Trong Spring STOMP, cấu hình danh sách Origin được phép ngay trên endpoint:

```java
@Override
public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws")
            .setAllowedOrigins("https://app.example.com");
}
```

Đây là đoạn phương thức trong class triển khai `WebSocketMessageBrokerConfigurer`, chưa phải tệp độc lập. Spring hỗ trợ kiểm tra Origin tại phần handshake; mặc định WebSocket/SockJS chỉ chấp nhận same-origin. Có thể chỉ định danh sách các origin cụ thể bằng `setAllowedOrigins(...)`, hoặc dùng `setAllowedOriginPatterns(...)` khi thực sự cần pattern và đã đánh giá phạm vi.

Nguyên tắc:

- Ghi **origin đầy đủ** gồm scheme, host và port khi cần, ví dụ `http://localhost:5173` lúc phát triển; `https://app.example.com` khi triển khai. Không gửi path `/dashboard` trong danh sách origin.
- `https://app.example.com` và `http://app.example.com` là hai origin khác nhau; port khác mặc định cũng tạo origin khác.
- Tránh `setAllowedOrigins("*")` trên endpoint được xác thực bằng cookie hoặc truyền dữ liệu nhạy cảm. Cho phép mọi Origin làm mất một lớp bảo vệ trình duyệt.
- Với reverse proxy, bảo đảm xử lý đúng scheme/host theo forwarded headers **chỉ từ proxy tin cậy**. Nếu không, phép so Origin với endpoint nhìn từ ngoài có thể thất bại hoặc bị diễn giải sai.
- Nếu bật `withSockJS()`, thay đổi danh sách Origin còn ảnh hưởng các fallback transport; một số cơ chế như iframe có thể bị vô hiệu khi bật giới hạn origin.

Không cần viết lại bộ so sánh Origin trong `HandshakeInterceptor` khi quy tắc mặc định và `setAllowedOrigins` đã đáp ứng. Interceptor vẫn có thể thực hiện chính sách bổ sung, chẳng hạn kiểm tra thuê bao/tenant từ server hoặc từ chối kết nối bảo mật chưa xác thực.

### 3.3 Vì sao Origin và CSRF có liên quan nhưng khác nhau?

Origin giúp hạn chế website nào có thể thiết lập WebSocket từ browser. CSRF bảo vệ khỏi các hành động tận dụng credential tự động gửi (ví dụ session cookie) trong bối cảnh người dùng đã đăng nhập.

Khi bật Spring Security cho STOMP, cấu hình an toàn còn có thể yêu cầu **CSRF token trong frame STOMP `CONNECT`**, cung cấp lớp bảo vệ tại cấp messaging. Tùy phiên bản/cách kích hoạt security, việc gửi CONNECT mà không có token có thể bị từ chối dù HTTP handshake đã thành công. Đặc biệt với SockJS fallback, một số transport dùng HTTP POST; không giải quyết bằng cách tắt CSRF trên toàn ứng dụng. Nếu cần ngoại lệ HTTP để SockJS hoạt động, giới hạn chặt vào các URL SockJS và vẫn bảo vệ cấp STOMP theo cấu hình security.

**Lỗi phổ biến:** “Đã setAllowedOrigins thì không cần phân quyền message”. Origin không quyết định user nào được `SUBSCRIBE /topic/private` hoặc `SEND /app/admin`. Cần cấu hình chính sách message-level dựa trên Principal/Authentication đáng tin.

## 4. Xác thực và thông tin người dùng trong handshake

### 4.1 Dùng Principal đã xác thực trên HTTP request

Trường hợp thông dụng: người dùng đã đăng nhập qua HTTP/Spring Security; khi trình duyệt mở WebSocket, request handshake gửi cookie phiên của trang phù hợp. Spring lấy `Principal` đã xác thực từ HTTP request và chuyển sang WebSocket/STOMP session. Ở message controller có thể nhận `Principal` như một tham số.

```text
HTTP login / trusted session authentication
    |
    v
SecurityFilterChain xác thực request GET /ws
    |
    v
request.getPrincipal()  ---> DefaultHandshakeHandler
    |                                 |
    |                                 v
    |                         WebSocket Principal
    |                                 |
    v                                 v
HandshakeInterceptor              STOMP message "user"
    |
    +--> session attributes (metadata bổ sung)
```

Không tự tin vào `userId` lấy từ query `?user=alice`, bất kỳ header client tự đặt hoặc native STOMP header `login` để quyết định Principal của một request đã được xác thực bằng HTTP.

Trong cấu hình STOMP mặc định của Spring, header `login` và `passcode` ở frame CONNECT không được dùng để xác thực lại người dùng WebSocket: Spring lấy danh tính từ HTTP handshake. Nếu ứng dụng dùng bearer token riêng trong STOMP CONNECT (thường gặp khi browser không gửi được custom HTTP Authorization header từ WebSocket API), cần một `ChannelInterceptor` xử lý token trên `clientInboundChannel`, xác thực token ở server rồi đặt `accessor.setUser(authentication)` theo quy tắc Spring; việc đó diễn ra **sau handshake**, không phải trong `HandshakeInterceptor`.

### 4.2 Tạo HandshakeInterceptor để từ chối anonymous và gắn metadata

Ví dụ sau cố ý không tự thực hiện username/password validation trong interceptor. Nó giả định HTTP security chain đã xác thực request tới `/ws`. Nếu không có principal hợp lệ, yêu cầu bị từ chối. Thuộc tính `connectedUserId` chỉ phục vụ đối chiếu session; quyền thực tế vẫn dựa vào `Principal`.

**Tệp `VerifiedUserHandshakeInterceptor.java`:**

```java
package com.example.websocket;

import java.security.Principal;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

public final class VerifiedUserHandshakeInterceptor
        implements HandshakeInterceptor {

    public static final String USER_ID = "connectedUserId";

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {

        Principal principal = request.getPrincipal();

        if (principal == null
                || principal.getName() == null
                || principal.getName().isBlank()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        attributes.put(USER_ID, principal.getName());
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // Để trống: không cần xử lý sau handshake cho ví dụ này.
    }
}
```

Đây là mã Java 17+ đầy đủ cho một class dùng trong dự án Spring WebSocket. Nếu dự án có Spring Security, phải bảo đảm **endpoint handshake được bảo vệ bằng HTTP security**: ở nhiều cấu hình, `AnonymousAuthenticationToken` có thể được biểu diễn trong SecurityContext dù người dùng chưa đăng nhập; không dựa duy nhất vào việc một object `Principal` có tên hay không để định nghĩa xác thực ở mọi hạ tầng. Chính sách authentication/authorization chính nên thuộc `SecurityFilterChain`, còn ví dụ interceptor đóng vai trò kiểm tra phụ và sao chép metadata sau khi đã xác thực.

**Điểm cần nhớ:** `attributes.put(USER_ID, principal.getName())` không tự thay đổi Principal của WebSocket session. `DefaultHandshakeHandler`/hạ tầng Spring quản lý việc chọn Principal. Nếu cần ánh xạ danh tính tùy biến ngoài cơ chế HTTP mặc định, phải dùng chiến lược đã xác thực và tích hợp đúng `HandshakeHandler.determineUser(...)` hoặc STOMP `ChannelInterceptor`, tránh tự gán tên người dùng từ dữ liệu không tin cậy.

### 4.3 Các loại dữ liệu được và không nên đưa vào attributes

Phù hợp: ID điều tra sự cố, mã tenant đã xác minh, thời điểm kết nối, marker phiên bản giao thức hoặc cờ kỹ thuật nhỏ. Không phù hợp: mật khẩu, JWT nguyên văn, refresh token, toàn bộ `SecurityContext` mutable, raw cookie, body nhạy cảm, connection/database handle.

Map attributes có vòng đời gắn với WebSocket session; kết nối có thể sống lâu hơn HTTP request. Vì vậy số lượng và kích thước dữ liệu cần nhỏ, có kế hoạch dọn dẹp và chính sách truy cập đúng mức.

## 5. Ví dụ cấu hình STOMP endpoint hoàn chỉnh

**Tệp `WebSocketStompConfig.java`**:

```java
package com.example.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketStompConfig
        implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .addInterceptors(new VerifiedUserHandshakeInterceptor())
                .setAllowedOrigins("https://app.example.com");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic", "/queue");
    }
}
```

Giải thích từng thành phần:

- `@EnableWebSocketMessageBroker` bật hạ tầng STOMP over WebSocket.
- `/ws` là đường dẫn **HTTP handshake**; nó không phải destination mà client `SUBSCRIBE` hoặc `SEND` tới.
- `addInterceptors(...)` đăng ký `HandshakeInterceptor` ở bước HTTP handshake.
- `setAllowedOrigins(...)` yêu cầu Origin của trang thuộc danh sách cho phép.
- `/app` là prefix cho thông điệp đến `@MessageMapping`; `/topic` và `/queue` được simple broker quản lý trong ví dụ.

Với ứng dụng Servlet đã dùng Spring Security, nên bảo vệ endpoint bằng chuỗi HTTP security phù hợp. Đoạn sau giả định có `spring-boot-starter-security` và xác thực web đã được cấu hình (login/session hoặc cơ chế khác):

**Tệp `WebHttpSecurityConfig.java`:**

```java
package com.example.websocket;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
public class WebHttpSecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/ws", "/ws/**").authenticated()
                .anyRequest().permitAll());
        http.formLogin(withDefaults());
        return http.build();
    }
}
```

**Lưu ý về phạm vi:** ví dụ này dành cho Spring MVC/Servlet + Spring Security 6, nơi HTTP handshake được xử lý qua Servlet security chain; nó không phải cấu hình Spring WebFlux SecurityFilterChain reactive. Phần `anyRequest().permitAll()` chỉ là giả định để minh họa endpoint riêng; trong sản phẩm thật, thay bằng chính sách HTTP hiện hữu của hệ thống, tuyệt đối không mở toàn bộ route nhạy cảm. Cấu hình ví dụ không tắt CSRF, không cấp quyền STOMP tùy tiện; khi kích hoạt WebSocket message security, cần cấu hình authorization và CONNECT CSRF tương ứng.

Với Spring Boot 3.x/Java 17+, ví dụ Gradle trong dự án đã áp dụng Spring Boot dependency management:

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-websocket'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```

Spring Boot 3.3.x trong module học thường dùng Java 21 nhưng mã trên tương thích Java 17+. Đặt ba class trong package `com.example.websocket` nằm dưới package gốc của ứng dụng Spring Boot để component scanning nhận được `@Configuration`. Ví dụ này có tên miền tượng trưng; kết nối cross-origin từ trang khác ngoài `https://app.example.com` sẽ bị bộ kiểm tra Origin của endpoint từ chối. Tạo tài khoản/xác thực thực tế theo chính sách của ứng dụng; không hardcode username/password sản phẩm trong tài liệu.

## 6. Đọc người dùng và session attributes trong STOMP message controller

Sau khi handshake thành công và STOMP client gửi message, ứng dụng có thể đọc Principal và session attributes ở một `@MessageMapping`. Hai nguồn này khác nhau: Principal là danh tính đã được Spring gắn với session; attributes là dữ liệu bổ sung được tạo trong handshake.

**Tệp `IdentityMessageController.java`:**

```java
package com.example.websocket;

import java.security.Principal;
import java.util.Map;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

@Controller
public class IdentityMessageController {

    @MessageMapping("/whoami")
    @SendToUser("/queue/identity")
    public String whoAmI(
            Principal principal,
            SimpMessageHeaderAccessor headers) {

        Map<String, Object> attributes = headers.getSessionAttributes();
        Object copiedUser = attributes == null
                ? null
                : attributes.get(VerifiedUserHandshakeInterceptor.USER_ID);

        // Demo: chỉ trả về username đã xác thực.
        // Metadata phiên chỉ được dùng để quan sát/đối chiếu.
        if (principal == null) {
            throw new IllegalStateException("Authenticated user required");
        }

        String verifiedName = principal.getName();
        if (copiedUser != null
                && !verifiedName.equals(copiedUser.toString())) {
            throw new IllegalStateException("Session identity mismatch");
        }
        return verifiedName;
    }
}
```

Một STOMP client đã được xác thực có thể gửi message đến `/app/whoami` và lắng nghe destination người dùng tương ứng như `/user/queue/identity`, tùy cấu hình broker và messaging security. Dữ liệu trả về là tên người dùng đã xác thực; không trả thông tin bí mật.

Ở đây việc so khớp hai trường chỉ để **phát hiện tình huống metadata không nhất quán** trong ví dụ học tập. Quyết định quyền truy cập vẫn cần dựa vào `Authentication`/Principal và chính sách server-side, không dựa riêng vào một giá trị trong session attributes. Khi identity có thể thay đổi trong vòng đời kết nối, thiết kế cần xử lý đổi phiên, token hết hạn, khóa tài khoản và chính sách đóng kết nối riêng.

### 6.1 Phân biệt kết nối, người dùng và phiên đăng ký

- Một tài khoản có thể mở **nhiều WebSocket session** (nhiều tab/thiết bị), nên không coi username là ID kết nối duy nhất.
- `sessionId` được Spring gắn với một WebSocket/STOMP session; user principal name nhận diện người dùng theo cơ chế xác thực.
- `attributes` có thể chứa thông tin bổ trợ theo từng kết nối.
- Một STOMP session có thể chứa nhiều subscription; quyền `SUBSCRIBE` phải kiểm tra dựa trên destination và user chứ không thể chỉ dựa vào “đã handshake”.
- `SimpUserRegistry` và hạ tầng `/user` có thể giúp Spring chuyển thông điệp theo người dùng/phiên; không tự suy luận rằng mỗi user có đúng một socket.

## 7. Nếu token nằm trong STOMP CONNECT thì HandshakeInterceptor có đọc được không?

**Không.** HTTP handshake xảy ra **trước** khi có frame STOMP `CONNECT`. `HandshakeInterceptor` chỉ nhìn thấy HTTP request (cookies, Origin, headers ở lớp HTTP), không thể đọc native headers của một frame STOMP chưa được gửi.

Ứng dụng browser thường có các lựa chọn:

1. **Session/cookie authentication ở HTTP handshake:** Spring Security xác thực request; Principal được Spring nối sang WebSocket/STOMP. Trường hợp phù hợp khi site và WebSocket endpoint dùng chung hệ thống đăng nhập.
2. **Token trong STOMP CONNECT:** sau handshake, `ChannelInterceptor` trên `clientInboundChannel` đọc header token, gọi thành phần xác thực server-side, tạo `Authentication` hợp lệ và dùng `StompHeaderAccessor.setUser(...)` cho CONNECT. Phải xử lý khi token thiếu/sai/hết hạn và áp dụng message authorization.
3. **Token/cookie ở HTTP handshake:** với browser API `new WebSocket(...)` không thể tự do đặt bất kỳ custom HTTP header nào như với `fetch`. Không nên đưa bearer token dài hạn vào URL query vì dễ lộ qua log, browser history hay hạ tầng; chọn cơ chế đúng kiến trúc và các biện pháp bảo vệ thích hợp.

Đoạn sau minh họa **cấu trúc** của interceptor STOMP; phần xác thực token cần một dịch vụ tin cậy của hệ thống nên không viết giả lập có thể gây hiểu nhầm là xác thực thật:

```java
@Override
public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(new ChannelInterceptor() {
        @Override
        public Message<?> preSend(Message<?> message,
                                   MessageChannel channel) {
            StompHeaderAccessor accessor =
                    MessageHeaderAccessor.getAccessor(
                            message, StompHeaderAccessor.class);

            if (accessor != null
                    && StompCommand.CONNECT.equals(accessor.getCommand())) {
                // Lấy token từ native STOMP headers.
                // Xác thực token bằng dịch vụ bảo mật thực tế.
                // Chỉ sau khi xác thực thành công:
                // accessor.setUser(validatedAuthentication);
            }
            return message;
        }
    });
}
```

Đây là *mảnh minh họa cấu trúc* trong class `WebSocketMessageBrokerConfigurer`, **không phải logic xác thực chạy độc lập**. Cần import `ChannelRegistration`, `ChannelInterceptor`, `Message`, `MessageChannel`, `MessageHeaderAccessor`, `StompHeaderAccessor` và `StompCommand`. Không dùng mã này như security production nếu chưa hiện thực hóa kiểm tra chữ ký, hạn sử dụng, người dùng, quyền và từ chối kết nối. Với Spring Security messaging authorization, interceptor xác thực CONNECT phải được sắp trước interceptor phân quyền của security.

Nếu dùng xác thực HTTP mặc định, Spring có thể bỏ qua `login` và `passcode` của STOMP CONNECT. Không được nhận một chuỗi tùy ý trong `login` rồi coi đó là tên tài khoản đã xác thực.

## 8. Kiểm thử HandshakeInterceptor không cần server mạng thật

Kiểm thử đơn vị giúp xác nhận quy tắc nhận/từ chối theo Principal và metadata, không yêu cầu mở WebSocket thật. Dùng Mockito để mô phỏng `ServerHttpRequest`, `ServerHttpResponse` và `WebSocketHandler`. Class dưới đây chạy với JUnit 5 + spring-boot-starter-test.

**Tệp `VerifiedUserHandshakeInterceptorTest.java`** trong `src/test/java/com/example/websocket/`:

```java
package com.example.websocket;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VerifiedUserHandshakeInterceptorTest {

    private final VerifiedUserHandshakeInterceptor interceptor =
            new VerifiedUserHandshakeInterceptor();

    @Test
    void rejectsHandshakeWithoutPrincipal() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        WebSocketHandler handler = mock(WebSocketHandler.class);
        Map<String, Object> attributes = new HashMap<>();

        boolean proceed = interceptor.beforeHandshake(
                request, response, handler, attributes);

        assertFalse(proceed);
        assertTrue(attributes.isEmpty());
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void acceptsTrustedPrincipalAndCopiesItsName() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        WebSocketHandler handler = mock(WebSocketHandler.class);
        Map<String, Object> attributes = new HashMap<>();
        Principal trustedUser = () -> "alice";

        when(request.getPrincipal()).thenReturn(trustedUser);

        boolean proceed = interceptor.beforeHandshake(
                request, response, handler, attributes);

        assertTrue(proceed);
        assertEquals("alice", attributes.get(
                VerifiedUserHandshakeInterceptor.USER_ID));
        verify(response, never()).setStatusCode(HttpStatus.UNAUTHORIZED);
    }
}
```

Chạy trong dự án Gradle có wrapper và dependencies đã nêu:

```bash
./gradlew test --tests '*VerifiedUserHandshakeInterceptorTest'
```

Với Windows PowerShell dùng `.\gradlew.bat test --tests '*VerifiedUserHandshakeInterceptorTest'`. Khi cả hai ca thành công, JUnit thông báo **2 tests passed** trong báo cáo test; không có stdout cố định do test này không gọi `System.out.println`.

Bài test này kiểm tra hành vi **của interceptor** trước handshake. Nó không kiểm tra toàn bộ HTTP security, Origin policy của endpoint, websocket upgrade, cookie hay STOMP authorization. Những điều đó cần integration tests ở lớp tương ứng.

### 8.1 Kiểm thử tích hợp Origin và security

Tạo một test server với endpoint `/ws` đã cấu hình và một danh tính được xác thực. Thử các trường hợp:

| Trường hợp | Kỳ vọng |
| --- | --- |
| Origin đúng danh sách và HTTP Principal hợp lệ | Có thể tiếp tục handshake (nếu các điều kiện protocol/security khác đều đạt) |
| Origin không thuộc danh sách | Handshake bị từ chối theo chính sách Origin |
| Không có HTTP Principal hợp lệ | HTTP security hoặc interceptor từ chối; ví dụ interceptor trả 401 nếu được gọi |
| Origin đúng nhưng STOMP CONNECT thiếu CSRF token khi security yêu cầu | Có thể upgrade WebSocket, nhưng CONNECT bị chặn ở lớp messaging |
| User đã xác thực gửi SEND đến destination không được phép | Message bị từ chối ở lớp authorization |
| Browser đổi tab / tạo thêm kết nối | Các WebSocket session riêng có metadata riêng |
| STOMP CONNECT mang username giả mạo | Không được ghi đè Principal xác thực trên HTTP |

Khi kiểm thử handshake bằng công cụ CLI, header `Origin` có thể do công cụ tự tạo. Kết quả Origin hợp lệ **không chứng minh người dùng đã được xác thực**. Kiểm thử browser thật (cookie và CORS/Origin) và HTTP tool nên được xem là hai loại bằng chứng bổ sung.

Không hardcode mã status cụ thể của tất cả trường hợp bị từ chối Origin: status cuối cùng có thể phụ thuộc Spring/Security/container/handler nào chặn trước. Test nên xác minh request bị từ chối ở đúng lớp và lý do, trừ khi ứng dụng đã quy định status thành hợp đồng riêng.

## 9. Lỗi thường gặp

| Sai lầm | Hậu quả | Cách khắc phục |
| --- | --- | --- |
| Tin rằng HTTP 101 nghĩa là được phép mọi STOMP destination | Bỏ qua quyền gửi hoặc subscribe | Cấu hình Spring Security message authorization hoặc ChannelInterceptor thích hợp |
| Cho phép Origin `*` để “chữa lỗi CORS” | Cho website bất kỳ thử thiết lập WebSocket, rủi ro với cookie/session | Khai báo allowlist Origin chính xác, hiểu scheme/host/port |
| Coi Origin là danh tính client | Client ngoài browser có thể giả mạo Origin | Kết hợp authentication, authorization, rate limit và chính sách bảo mật |
| Lấy `userId` từ query string rồi gán vào attributes | User có thể tự xưng danh tính bất kỳ | Lấy Principal đáng tin từ HTTP Security hoặc token đã kiểm chứng |
| Dùng `attributes.put(...)` để mong thay Principal | Metadata không tự tạo authentication | Dùng cơ chế chọn Principal/ChannelInterceptor đúng tầng |
| Đọc token STOMP CONNECT trong HandshakeInterceptor | Frame CONNECT đến sau handshake | Dùng `ChannelInterceptor` của `clientInboundChannel` |
| Tự tin vào STOMP `login`/`passcode` mặc định | Spring thường bỏ qua chúng khi xác thực WebSocket dựa trên HTTP | Thiết kế authentication tường minh |
| Không kiểm tra quyền từng `SEND`/`SUBSCRIBE` | User hợp lệ vẫn có thể truy cập dữ liệu của người khác | Phân quyền theo user + destination |
| Đưa JWT hay cookie nguyên văn vào session attributes/log | Lộ bí mật và giữ trong session lâu | Lưu metadata tối thiểu, không ghi secret |
| Dùng `setAllowedOrigins("http://localhost")` nhưng frontend chạy port khác | Handshake cross-origin lỗi | Cấu hình đầy đủ scheme/host/port của frontend |
| Tắt CSRF toàn cục chỉ để SockJS chạy | Mở lỗ hổng ở các endpoint HTTP khác | Cấu hình ngoại lệ hẹp và bảo vệ STOMP CONNECT phù hợp |
| Đổi Origin check theo forwarded headers không tin cậy | Lách hoặc từ chối sai chính sách | Chỉ tin forwarding từ reverse proxy đã kiểm soát |

## 10. Tóm tắt và câu hỏi tự kiểm tra

**Chuỗi quyết định cần nhớ:**

```text
HTTP Security xác thực → Origin policy → HandshakeInterceptor
                  ↓ nếu chấp nhận
        WebSocket session / Principal / attributes
                  ↓
        STOMP CONNECT (có thể có kiểm tra CSRF/token)
                  ↓
        STOMP SEND/SUBSCRIBE → message authorization → nghiệp vụ
```

Câu hỏi tự ôn tập:

1. `HandshakeInterceptor.beforeHandshake` trả `false` nghĩa là gì? **Dừng handshake trước khi kết nối WebSocket được mở.**
2. Tại sao `attributes.put("userId", "...")` không tương đương xác thực? **Nó chỉ đặt metadata, không chứng minh danh tính hoặc tạo Principal.**
3. `Origin` đến từ đâu và có thể tin tuyệt đối không? **Trình duyệt gửi khi bắt đầu WebSocket; client ngoài browser có thể tự giả mạo.**
4. Làm sao giới hạn frontend được kết nối? **Dùng `setAllowedOrigins`/`setAllowedOriginPatterns` với phạm vi cần thiết và bảo vệ authentication tương ứng.**
5. Vì sao STOMP CONNECT token không thể lấy trong HTTP HandshakeInterceptor? **Handshake đi trước frame STOMP CONNECT.**
6. Có phải chỉ cần chặn user lạ ở handshake là đủ? **Không; người dùng hợp lệ vẫn cần được phân quyền theo destination và loại message.**
7. User có thể mở nhiều WebSocket không? **Có; mỗi kết nối có session ID và thuộc tính riêng, dù dùng chung Principal.**
8. Trạng thái HTTP handshake có phải là trạng thái mọi message sau đó? **Không; handshake chỉ tạo phiên, xử lý STOMP là một giai đoạn khác.**

Khi áp dụng trong ứng dụng thật, ưu tiên xây chính sách phân tầng: HTTP authentication hợp lệ, danh sách origin tin cậy, interceptor chỉ lưu thông tin phiên tối thiểu, bảo vệ CONNECT khi cần và kiểm soát quyền ở cấp message.

## 11. Tài liệu chính thức

- [Spring Framework — WebSocket server, Handshake và Allowed Origins](https://docs.spring.io/spring-framework/reference/web/websocket/server.html).
- [Spring Framework — STOMP enable endpoints](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/enable.html).
- [Spring Framework — STOMP authentication](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/authentication.html).
- [Spring Framework — Token authentication ở STOMP CONNECT](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/authentication-token-based.html).
- [Spring Framework — STOMP interception/authorization](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/interceptors.html).
- [Spring Security — WebSocket security, same-origin, CSRF](https://docs.spring.io/spring-security/reference/servlet/integrations/websocket.html).
- [Javadoc — HandshakeInterceptor](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/socket/server/HandshakeInterceptor.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="websocket-stomp-005">Giới hạn message, gửi đồng thời, timeout và ngắt kết nối</a>

<details>
<summary>Xem chi tiết</summary>

# WebSocket và STOMP: Giới hạn message, gửi đồng thời, timeout và ngắt kết nối

## 1. Đặt đúng phạm vi: frame, STOMP message và session

Trong Spring Framework, WebSocket tạo một kết nối hai chiều tồn tại lâu giữa client và server; STOMP là giao thức nhắn tin dạng frame chạy bên trên kết nối đó. Ứng dụng Spring dùng `@EnableWebSocketMessageBroker` thường có các chặng:

```text
Trình duyệt/ứng dụng STOMP
    │ HTTP Upgrade (handshake) → WebSocket session
    │ STOMP CONNECT / SUBSCRIBE / SEND / DISCONNECT
    ▼
WebSocket engine (Tomcat/Jetty/...)
    │ xử lý frame WebSocket, giới hạn frame/buffer, idle timeout
    ▼
STOMP sub-protocol handler
    │ ghép/giải mã STOMP message, kiểm tra messageSizeLimit
    ▼
clientInboundChannel → @MessageMapping / broker
    │
    ▼
clientOutboundChannel → phiên WebSocket
    │    tuần tự hóa send; buffer dữ liệu chờ khi client chậm
    ▼
Trình duyệt nhận MESSAGE hoặc socket đóng
```

Phân biệt ba khái niệm:

- **WebSocket frame/message:** đơn vị của giao thức WebSocket, do HTTP upgrade endpoint và thư viện WebSocket bên dưới xử lý. Giới hạn kích thước tùy container và cấu hình engine.
- **STOMP frame/message:** lệnh `CONNECT`, `SEND`, `SUBSCRIBE`, `MESSAGE`, `ERROR`, `DISCONNECT` kèm headers và body. Một STOMP message lớn có thể đến qua nhiều WebSocket messages/chunks, được bộ giải mã Spring ghép lại trước khi chuyển vào ứng dụng.
- **WebSocket/STOMP session:** kết nối có trạng thái đăng ký subscription, heartbeat, thông tin người dùng và hàng đệm outbound. Giới hạn dành cho một session khác với giới hạn tổng số phiên toàn hệ thống.

Bài áp dụng cho **Spring Framework 6.1.x / Spring Boot 3.3.x với Java 17+** dùng Spring WebSocket + STOMP trên Servlet stack. Các ví dụ cấu hình giới hạn là số **dùng để học**; cấu hình thật phải đo tải và cân nhắc giới hạn client, proxy và broker ngoài (nếu có).

## 2. Giới hạn kích thước message: inbound STOMP khác buffer container

Trong `WebSocketMessageBrokerConfigurer`, dùng:

```java
@Override
public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
    registration.setMessageSizeLimit(64 * 1024); // bytes
}
```

`setMessageSizeLimit` giới hạn **STOMP message nhận từ client**, kể cả khi nó được gom từ nhiều WebSocket messages. Mặc định của Spring STOMP transport là **64 KiB** theo Javadoc Spring 6.1. Con số tính theo **byte**, không phải số ký tự Unicode. Trên wire, STOMP headers và body cũng tiêu thụ không gian; một JSON payload có kích thước sát trần vẫn có thể vượt giới hạn sau khi tính thêm overhead.

Luồng với một message quá lớn:

```text
Client gửi STOMP SEND (kèm body lớn)
 → WebSocket engine tiếp nhận các phần
 → STOMP decoder ghép nội dung
 → vượt messageSizeLimit
 → lỗi tại tầng protocol/transport, có thể phát ERROR/đóng session
 → @MessageMapping không được gọi thành công
```

Không giả định mọi container và mọi loại lỗi đều trả cùng **mã đóng** hay cùng chuỗi `ERROR`. Cách phản ánh ở browser/log phụ thuộc phần bị từ chối (WebSocket engine hay STOMP decoder), phiên bản và trạng thái kết nối.

**Giới hạn khác ở cấp WebSocket engine** (ví dụ cấu hình Jakarta WebSocket/Tomcat):

```java
@Bean
ServletServerContainerFactoryBean webSocketContainer() {
    ServletServerContainerFactoryBean factory =
        new ServletServerContainerFactoryBean();
    factory.setMaxTextMessageBufferSize(32 * 1024);
    factory.setMaxBinaryMessageBufferSize(32 * 1024);
    factory.setMaxSessionIdleTimeout(120_000L);
    return factory;
}
```

Cần imports `org.springframework.context.annotation.Bean` và `org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean`. Đây là **mã cấu hình minh họa**, không phải lựa chọn bắt buộc cho mọi ứng dụng. Nó cấu hình khả năng nhận WebSocket text/binary ở server container và idle timeout (ms), trong khi `setMessageSizeLimit` giới hạn message STOMP sau khi được ghép. Với STOMP, Spring có thể điều chỉnh cỡ đệm WebSocket bên dưới để đủ nhận các chunk mà STOMP client gửi; đừng coi hai con số là cùng một giới hạn.

Có thể đặt limit nhỏ để bảo vệ tài nguyên, nhưng phải xét trường hợp legitimate: JSON có trường Unicode dài, message có headers lớn, snapshot gửi ban đầu, v.v. Với file hoặc đối tượng vài MB, tốt hơn là **upload qua HTTP/object storage**, rồi thông báo sự kiện nhỏ qua STOMP kèm URL/id có kiểm soát. Không dùng tăng `setMessageSizeLimit` vô hạn làm cơ chế thay thế quy trình upload.

**Inbound khác outbound:** `setMessageSizeLimit` không phải trần cho mọi `MESSAGE` server đẩy ra. Khi muốn hạn chế dữ liệu outbound, phải chủ động kiểm soát payload tại nghiệp vụ, broker và dung lượng buffer gửi.

## 3. Gửi đồng thời: mỗi WebSocket session cần tuần tự hóa việc send

Một WebSocket session không nên có nhiều thread đồng thời gọi `sendMessage` trên cùng kết nối. Trong mô hình STOMP broker của Spring, gửi tới client đi qua `clientOutboundChannel` (có thể có nhiều worker threads), nhưng Spring đảm bảo ở thời điểm ghi tới **một WebSocket session** chỉ có một luồng gửi.

Vấn đề thực tế xảy ra khi một client nhận chậm hơn tốc độ server phát:

```text
Server phát các MESSAGE #1, #2, #3, #4...
                   │
                   ▼
clientOutboundChannel (đa luồng)
                   │
                   ▼
Session A: send #1 đang chậm
           #2, #3, #4 phải đợi trong buffer
                   │
                   ├─ buffer tiếp tục tăng
                   └─ kiểm tra sendTimeLimit / sendBufferSizeLimit
                       nếu vượt: phiên có thể bị kết thúc
```

Spring dùng cơ chế tương tự `ConcurrentWebSocketSessionDecorator`: wrapper serialize việc gửi trên một session; các send phát sinh khi thread khác đang ghi sẽ được xếp hàng. Vì vậy, *thread-safe send* **không đồng nghĩa với miễn phí về RAM hoặc bảo đảm người nhận theo kịp**.

Cấu hình giới hạn outbound:

```java
@Override
public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
    registration
        .setMessageSizeLimit(64 * 1024)       // inbound STOMP, bytes
        .setSendBufferSizeLimit(512 * 1024)   // outbound buffer / session, bytes
        .setSendTimeLimit(15_000);            // thời gian gửi, milliseconds
}
```

Cơ chế:

- `setSendBufferSizeLimit(512 * 1024)` đặt giới hạn dữ liệu **đang chờ gửi** cho mỗi session khi send bị nghẽn. Đây không phải queue vô hạn và không phải tổng buffer của mọi session.
- `setSendTimeLimit(15_000)` đặt ngưỡng thời gian cho một hoạt động send kéo dài. Nó **không phải** deadline tổng từ lúc publish cho đến khi client ứng dụng xử lý xong.
- Khi một send còn giữ quyền ghi và các send khác đến, wrapper kiểm tra thời gian đang gửi và dung lượng chờ; nếu vượt, có thể ném `SessionLimitExceededException` và đóng phiên với trạng thái không đáng tin cậy.

**Cảnh báo kỹ thuật quan trọng:** send-time limit của cơ chế này không phải timer cứng cắt ngang một lệnh blocking send đúng từng millisecond. Trong triển khai decorator, giới hạn thường được kiểm tra khi có thêm lần gửi hoặc lúc close; một send duy nhất bị chặn lâu có thể chưa được phát hiện ngay tại mốc giới hạn. Cần kết hợp timeout của container/network, giám sát và chính sách backpressure phù hợp. Đừng coi tăng `sendTimeLimit` là giải pháp cho một browser ngừng đọc.

### Khi tự viết WebSocketHandler thuần

Nếu **không** dùng STOMP broker config mà tự gửi trực tiếp từ nhiều thread, bọc `WebSocketSession` bằng `ConcurrentWebSocketSessionDecorator` hoặc thực hiện cơ chế hàng đợi tuần tự tương đương:

```java
WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(
    originalSession, 15_000, 512 * 1024
);

// Các luồng trong ứng dụng chỉ gọi safe.sendMessage(message);
// không gọi lẫn lộn originalSession.sendMessage(message).
```

Đây là đoạn minh họa, cần import `WebSocketSession` và `org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator`. **Không cần bọc thủ công lần nữa** các session đã được Spring STOMP stack quản lý; tăng tầng wrapper sai chỗ có thể làm rối lifecycle và metrics.

### Gửi đồng thời khác với thứ tự nhận

Sự kiện có thể được publish qua `clientOutboundChannel` bằng nhiều thread nên thứ tự **client thấy** có thể khác thứ tự ứng dụng publish. Nếu thứ tự cho từng session là yêu cầu, bật:

```java
@Override
public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/topic", "/queue");
    registry.setApplicationDestinationPrefixes("/app");
    registry.setPreservePublishOrder(true);
}
```

Từ Spring Framework 6.1 còn có thể bảo toàn **thứ tự xử lý inbound trong một client session**:

```java
@Override
public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws");
    registry.setPreserveReceiveOrder(true);
}
```

Đây là bảo đảm thứ tự **trong session**, không phải global ordering giữa mọi client, và có overhead. Nó không biến STOMP thành giao thức exactly-once hay giải quyết thứ tự do nhiều publisher ngoài Spring tự ghi vào broker.

## 4. Timeout và heartbeat: có nhiều đồng hồ riêng

“WebSocket timeout” có thể nói về hoàn toàn khác nhau:

| Timeout/giám sát | Mục đích | Nơi thường cấu hình |
| --- | --- | --- |
| HTTP handshake/upgrade | Thiết lập kết nối HTTP → WebSocket | HTTP server/proxy/client; không dùng `sendTimeLimit` |
| WebSocket idle timeout | Socket không có traffic trong một thời gian | WebSocket container, ví dụ `setMaxSessionIdleTimeout` |
| STOMP heartbeats | Phát hiện một phía không gửi/nhận traffic như đã thương lượng | STOMP `heart-beat`, TaskScheduler, broker |
| Outbound send time | Một lần gửi về client mất quá lâu | `setSendTimeLimit` |
| Client reconnect delay | Thời gian chờ trước khi thử kết nối lại | Client STOMP/browser hoặc thư viện reconnect |
| Business acknowledgement/receipt | Bên kia đã xử lý/persist nghiệp vụ chưa? | `receipt` của STOMP hoặc application-level ACK/event, tùy contract |

**STOMP heartbeats** dùng cặp số milisecond `heart-beat:cx,cy` trong `CONNECT` và `heart-beat:sx,sy` trong `CONNECTED` để thương lượng nhịp tối thiểu: một bên cho biết tần suất có thể gửi và kỳ vọng nhận. STOMP heartbeat frame có thể là chỉ một byte xuống dòng; khi có các STOMP frame khác, traffic đó cũng có thể đáp ứng nhịp liveness. Chúng không bảo đảm endpoint đã xử lý thành công message nghiệp vụ.

Với simple broker, heartbeat cần `TaskScheduler` được cấu hình:

```java
@Override
public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/topic")
        .setTaskScheduler(heartbeatTaskScheduler)
        .setHeartbeatValue(new long[] {10_000, 10_000});
    registry.setApplicationDestinationPrefixes("/app");
}
```

`heartbeatTaskScheduler` là `TaskScheduler` đã tạo/inject vào cấu hình (ví dụ hoàn chỉnh ở phần 7). Hai số là chu kỳ đề xuất gửi/nhận theo cơ chế Spring broker, **không phải một timeout HTTP request**. Với hệ thống nhiều proxy/load balancer, idle timeout trung gian phải dài hơn nhịp heartbeat có đủ khoảng dư cho jitter/network. Nếu đường truyền proxy cắt socket dù vẫn có heartbeat, kiểm tra chính sách WebSocket Upgrade, TCP idle và cân bằng tải.

**SockJS:** nếu bật SockJS fallback, nó còn có cơ chế heartbeat riêng; khi đã thương lượng STOMP heartbeat, heartbeat SockJS thường được tắt để tránh trùng. Các transport SockJS HTTP streaming/polling có timeout và tài nguyên khác native WebSocket, không thể bê nguyên mọi giả định WebSocket sang.

**Broker relay:** khi Spring dùng external broker (RabbitMQ/ActiveMQ...), heartbeat của kết nối broker relay và heartbeat WebSocket client là hai luồng riêng. Relay có thể tái kết nối broker ở mức hệ thống, **không tự kết nối lại WebSocket client** bị rớt.

## 5. Ngắt kết nối: chủ động, bất thường, đóng do giới hạn

Một session có thể kết thúc vì:

1. **Client chủ động:** gửi STOMP `DISCONNECT` rồi đóng WebSocket (để graceful hơn có thể đợi STOMP `RECEIPT` nếu broker hỗ trợ và client yêu cầu). Tab bị đóng có thể không kịp gửi `DISCONNECT`.
2. **Network/proxy:** mất Wi-Fi, NAT/LB đóng idle connection, client ngủ, mất điện. Server không nhất thiết biết ngay; heartbeat/timeout giúp phát hiện sau một khoảng.
3. **Vượt message size / buffer / send time:** Spring hoặc container từ chối dữ liệu hoặc đóng session.
4. **Protocol/auth/server:** frame STOMP hỏng, subscription không hợp lệ, handshake bị từ chối, lỗi server hoặc restart.
5. **Broker bên ngoài không sẵn sàng:** có thể ảnh hưởng publish/subscription; tùy cấu hình phải xử lý broker availability và reconnection riêng.

Các sự kiện Spring đáng biết:

- `SessionConnectEvent`: server nhận frame STOMP CONNECT, **chưa đủ để khẳng định đã CONNECTED**.
- `SessionConnectedEvent`: broker đã gửi frame CONNECTED; có thể coi STOMP session đã được thiết lập.
- `SessionSubscribeEvent` / `SessionUnsubscribeEvent`: theo dõi thay đổi subscription.
- `SessionDisconnectEvent`: phiên STOMP kết thúc, dù do client hoặc WebSocket transport. **Một session có thể phát sự kiện này hơn một lần**, vì vậy cleanup phải idempotent.

Cách cleanup an toàn thường là thao tác có tính idempotent:

```java
@EventListener
public void onDisconnect(SessionDisconnectEvent event) {
    String sessionId = event.getSessionId();
    SessionState removed = activeSessions.remove(sessionId);
    if (removed != null) {
        releaseResources(removed);
    }
}
```

Đây là **pseudocode Java**: `SessionState`, `activeSessions` và `releaseResources` là thành phần ứng dụng phải định nghĩa. `ConcurrentHashMap.remove` trả `null` ở lần cleanup thứ hai, nên không phát side effect trùng lặp. Nếu cleanup còn liên quan database/ngoại hệ thống, dùng transaction/idempotency key phù hợp. Không giữ một `Set` chứa mọi session ID đã từng disconnect mãi mãi trong heap.

**Khôi phục sau reconnect:** WebSocket/STOMP connection mới thường có **session ID mới**. Client phải tự reconnect, gửi CONNECT và SUBSCRIBE lại; trạng thái subscription in-memory của session cũ không tự được tái tạo. Với chat/notification quan trọng, sử dụng offset/event ID để đồng bộ các message có thể bỏ lỡ, hoặc persistent broker và ACK/transaction theo thiết kế. STOMP heartbeat hay WebSocket TCP connection chỉ xác nhận liveness, không cung cấp exactly-once delivery.

## 6. Chọn số giới hạn theo tải và quan sát hiện tượng

Giả sử một notification trung bình 2 KiB và outbound buffer mỗi session là 64 KiB. Nếu một client bị chậm và có nhiều tin đến, khoảng vài chục message chưa gửi đã có thể áp sát giới hạn (con số cụ thể tùy kích thước frame, serialization, overhead). Với 10.000 client cùng chậm, chỉ riêng buffer ở mức tối đa giả định 64 KiB/session đã tương ứng **khoảng 625 MiB** dữ liệu queue, chưa tính session object, broker, network buffer, TLS và heap overhead. Vì vậy, không tăng buffer để “hết disconnect” trước khi xác định vì sao client chậm.

Khi tải tăng, cân nhắc:

- **Payload:** giảm kích thước message, chỉ gửi các trường cần thiết, tránh gửi snapshot lớn nhiều lần.
- **Outbound:** đồng nhất tài nguyên `clientOutboundChannel` với số slow clients, giảm publish rate, coalesce/throttle các cập nhật chỉ cần giá trị mới nhất.
- **Inbound:** giới hạn số message mỗi user/connection ở gateway hoặc ứng dụng, xác thực quyền destination, kiểm tra schema, giữ `setMessageSizeLimit` vừa đủ hợp đồng.
- **Memory:** số session × buffer mỗi session + inbound decode + broker queue + object/heap trực tiếp; monitor heap và direct memory.
- **Liveness:** heartbeat và idle timeout phối hợp giữa trình duyệt, broker, proxy; đo false disconnect khi network chập chờn.
- **Order:** nếu cần đúng thứ tự trong một session, bật preserve order và kiểm thử hiệu năng; với các message từ nhiều producer, dùng sequence ID/ordering ở domain.
- **Scale:** Spring simple broker giữ subscription trong bộ nhớ của một instance; khi nhiều instance cần routing liên node, cân nhắc broker relay hoặc kiến trúc message broker phù hợp, và load balancer/session routing đúng.

Không dựa vào một “giá trị chuẩn” cho mọi ứng dụng. SLO thực tế bao gồm số kết nối, peak fan-out, message/s, p95/p99 send latency, client di động, tỉ lệ reconnect và điều kiện mạng.

## 7. Ví dụ chạy được: Spring Boot 3.3 + STOMP và WebSocket browser client

Ví dụ có WebSocket endpoint `/ws`, subscription `/topic/echo`, destination gửi `/app/echo`. Tùy chỉnh **1 KiB inbound STOMP**, **64 KiB outbound buffer**, **5 giây gửi**, bật bảo toàn thứ tự và heartbeat. Trang HTML sử dụng native `WebSocket` và **STOMP frame dạng chuỗi** để dễ thấy giao thức, không cần cài thư viện JavaScript STOMP.

Cấu trúc:

```text
stomp-limit-demo/
├── settings.gradle
├── build.gradle
└── src/main/
    ├── java/demo/StompLimitDemo.java
    └── resources/static/index.html
```

**`settings.gradle`**

```groovy
rootProject.name = 'stomp-limit-demo'
```

**`build.gradle`** — JDK 17+, Gradle 8.x:

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.13'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'demo'
version = '1.0.0'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-websocket'
}
```

**`src/main/java/demo/StompLimitDemo.java`**

```java
package demo;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@SpringBootApplication
public class StompLimitDemo {
    public static void main(String[] args) {
        SpringApplication.run(StompLimitDemo.class, args);
    }
}

@Configuration
@EnableWebSocketMessageBroker
class SocketConfig implements WebSocketMessageBrokerConfigurer {
    @Autowired
    @Lazy
    @Qualifier("stompHeartbeatScheduler")
    private TaskScheduler heartbeatScheduler;

    @Bean("stompHeartbeatScheduler")
    ThreadPoolTaskScheduler stompHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("stomp-heartbeat-");
        return scheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws");
        registry.setPreserveReceiveOrder(true);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic")
            .setTaskScheduler(heartbeatScheduler)
            .setHeartbeatValue(new long[] {10_000, 10_000});
        registry.setPreservePublishOrder(true);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration reg) {
        reg.setMessageSizeLimit(1024)           // 1 KiB inbound STOMP
            .setSendBufferSizeLimit(64 * 1024)  // outbound / session
            .setSendTimeLimit(5_000);           // ms
    }
}

record EchoPayload(String text) {}

@Controller
class EchoController {
    @MessageMapping("/echo")
    @SendTo("/topic/echo")
    public EchoPayload echo(EchoPayload payload) {
        return new EchoPayload("echo: " + payload.text());
    }
}

@Component
class SessionEvents {
    private final ConcurrentMap<String, Boolean> live =
        new ConcurrentHashMap<>();

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        String sessionId = SimpMessageHeaderAccessor
            .wrap(event.getMessage()).getSessionId();
        if (sessionId != null) {
            live.put(sessionId, Boolean.TRUE);
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        if (live.remove(event.getSessionId()) != null) {
            System.out.println("Closed STOMP session: " + event.getSessionId());
        }
    }
}
```

**`src/main/resources/static/index.html`**

```html
<!doctype html>
<html lang="vi">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>STOMP connection limits</title>
</head>
<body>
  <h1>STOMP over WebSocket</h1>
  <button id="connect">Connect</button>
  <button id="small">Gửi message nhỏ</button>
  <button id="large">Gửi message vượt giới hạn</button>
  <button id="disconnect">Disconnect</button>
  <pre id="log"></pre>
  <script>
    let socket = null;
    let ready = false;

    const output = document.getElementById("log");
    function log(value) {
      output.textContent += value + "\n";
    }
    function sendFrame(command, headers, body = "") {
      if (!socket || socket.readyState !== WebSocket.OPEN) {
        log("Socket chưa mở");
        return;
      }
      const lines = Object.entries(headers)
        .map(([name, value]) => name + ":" + value)
        .join("\n");
      socket.send(command + "\n" + lines + "\n\n" + body + "\0");
    }

    document.getElementById("connect").onclick = () => {
      ready = false;
      socket = new WebSocket(
        "ws://" + location.host + "/ws", "v12.stomp"
      );
      socket.onopen = () => {
        log("WebSocket OPEN");
        sendFrame("CONNECT", {
          "accept-version": "1.2",
          "host": location.hostname,
          "heart-beat": "0,0"
        });
      };
      socket.onmessage = (event) => {
        const frame = event.data;
        if (frame.startsWith("CONNECTED")) {
          ready = true;
          log("STOMP CONNECTED");
          sendFrame("SUBSCRIBE", {
            id: "sub-1", destination: "/topic/echo"
          });
        } else if (frame.startsWith("MESSAGE")) {
          log("MESSAGE: " + frame.split("\n\n")[1].replace(/\0$/, ""));
        } else if (frame.startsWith("ERROR")) {
          log("STOMP ERROR: " + frame);
        }
      };
      socket.onclose = (e) => {
        ready = false;
        log("WebSocket CLOSED: code=" + e.code + ", reason=" + e.reason);
      };
      socket.onerror = () => log("WebSocket transport error");
    };

    function sendText(text) {
      if (!ready) {
        log("Chưa STOMP CONNECTED");
        return;
      }
      sendFrame("SEND", {
        destination: "/app/echo",
        "content-type": "application/json"
      }, JSON.stringify({text}));
    }
    document.getElementById("small").onclick = () => sendText("Xin chào");
    document.getElementById("large").onclick = () => sendText("x".repeat(2048));
    document.getElementById("disconnect").onclick = () => {
      if (socket && socket.readyState === WebSocket.OPEN) {
        sendFrame("DISCONNECT", {});
        socket.close(1000, "done");
      }
    };
  </script>
</body>
</html>
```

Trong dự án:

```bash
gradle bootRun
```

Sau khi Spring Boot khởi động, mở `http://localhost:8080/index.html` trong trình duyệt:

1. Bấm **Connect**: log sẽ có `WebSocket OPEN` và, khi broker xác nhận, `STOMP CONNECTED`. Client gửi `SUBSCRIBE` đến `/topic/echo`.
2. Bấm **Gửi message nhỏ**: nếu SUBSCRIBE đã được broker xử lý, trình duyệt nhận MESSAGE có body gần như `{"text":"echo: Xin chào"}` (đúng thứ tự JSON theo Jackson record). Trong trường hợp gửi ngay sau CONNECTED, chờ rất ngắn cho subscription được xử lý trước.
3. Bấm **Gửi message vượt giới hạn**: payload 2 KiB + STOMP headers vượt `setMessageSizeLimit(1024)`. Server phải từ chối message ở tầng STOMP; browser có thể thấy `ERROR`, đóng socket hoặc thông báo transport tùy handler/container. **Không khẳng định mã CLOSE hay chuỗi lỗi cố định.**
4. Bấm **Disconnect** khi kết nối đang mở: gửi `DISCONNECT` rồi đóng WebSocket; server phát event ngắt kết nối và cleanup. Muốn xác minh lặp sự kiện, có thể thử reconnect/đóng tab đột ngột; listener chỉ xử lý thành công một lần cho mỗi phiên đã kết nối.

**Kết quả có thể quan sát ổn định nhất:** request nhỏ tạo payload Echo; request quá lớn bị chặn; đóng kết nối giải phóng session. Log có ID và thứ tự bất đồng bộ, vì vậy không đưa một transcript stdout cố định giả tạo.

Ví dụ HTML cố ý dùng `heart-beat:0,0` để thuận tiện cho việc quan sát frame thủ công; vì vậy browser demo này **không thực hiện client-side liveness check**. Trong ứng dụng thật, dùng thư viện STOMP client hỗ trợ heartbeat, reconnect/backoff, auth token, subscription restore và handling receipt đầy đủ. Nếu trang triển khai trên HTTPS, WebSocket phải dùng `wss://`, không dùng `ws://` như localhost.

## 8. Chẩn đoán từng lỗi theo lớp

| Triệu chứng | Khả năng cao | Cần xem |
| --- | --- | --- |
| Client gửi nội dung lớn rồi socket đóng | Vượt STOMP `messageSizeLimit` hoặc giới hạn container | Kích thước bytes gồm headers, log decoder/close reason, text buffer container |
| Client chậm bị ngắt khi server broadcast nhiều | Outbound buffer tăng hoặc send mất quá lâu | `sendBufferSizeLimit`, `sendTimeLimit`, outbound channel queue, network/client throughput |
| Message của cùng user đến không đúng thứ tự | Xử lý ở executor nhiều thread | `setPreservePublishOrder`, `setPreserveReceiveOrder`, thứ tự publish từ nhiều producer |
| Ngắt kết nối sau một khoảng không hoạt động | STOMP heartbeat, container idle hoặc proxy/LB idle | Heartbeat negotiation, TaskScheduler, close code, timeout proxy |
| Session vẫn hiển thị online khi mất mạng | Chưa phát hiện disconnection | Heartbeat/idle timeout, cleanup event, TTL trạng thái hiện diện |
| Cleanup/presence notification bị lặp | `SessionDisconnectEvent` có thể xuất hiện nhiều lần | Dùng remove-if-present, idempotency, dedupe |
| Session reconnect nhưng không còn subscription | Kết nối mới mang session mới | Client cần CONNECT + SUBSCRIBE lại, phục hồi last-event-id |
| Broker relay khôi phục nhưng browser vẫn offline | System broker connection khác client WebSocket connection | `BrokerAvailabilityEvent` và client reconnect riêng |
| RAM tăng khi fan-out lớn | Nhiều session × buffer, subscriptions, broker queue | Memory/GC, active/pending messages, payload size, slow consumers |

Một số chỉ số thiết yếu: số lượng WebSocket sessions hiện tại/đã đóng, close code theo nguyên nhân, STOMP CONNECTED/DISCONNECT, tỉ lệ oversize message, queue của `clientInboundChannel`/`clientOutboundChannel`, send latency, số client chậm, scheduler heartbeat, lượng memory và số lần reconnect. Spring cung cấp `WebSocketMessageBrokerStats` và các thống kê STOMP/WebSocket; kết hợp monitoring/metrics hạ tầng để nhìn cả proxy và external broker. Không log body hoặc credentials nhạy cảm khi debug.

## 9. Những lỗi thiết kế phổ biến

1. **Nhầm STOMP message limit với một WebSocket frame limit.** Một STOMP message có thể qua nhiều WebSocket chunks; giới hạn khác nhau ở từng tầng.
2. **Coi `sendTimeLimit` là request deadline cứng.** Nó kiểm tra khả năng gửi của session khi có cạnh tranh/buffer, không đo “client đã xử lý xong”.
3. **Bật nhiều luồng ghi vào raw `WebSocketSession` mà không serialize.** Với raw handler, cần wrapper hoặc hàng đợi; STOMP broker Spring đã có cơ chế riêng.
4. **Tăng send buffer để chống disconnect mà không xử lý backpressure.** Nhiều session chậm có thể giữ rất nhiều RAM.
5. **Đồng nhất heartbeat với ACK nghiệp vụ.** Ping/heartbeat chỉ nói về liveness; message loss vẫn có thể xảy ra nếu disconnect/restart.
6. **Dựa vào `SessionConnectEvent` để hiển thị online thành công.** CONNECT mới được tiếp nhận; `SessionConnectedEvent` mới biểu thị STOMP handshake đã hoàn tất.
7. **Không làm cleanup idempotent.** `SessionDisconnectEvent` có thể lặp, làm double-release hay double-notification.
8. **Tưởng broker relay tự reconnect trình duyệt.** Client phải reconnect và subscribe lại.
9. **Đặt heartbeat dài hơn hoặc gần bằng idle timeout của proxy.** Dễ tạo disconnect theo chu kỳ dù app không lỗi; cần đo jitter và các timeout trung gian.
10. **Dùng simple broker một node nhưng kỳ vọng subscriptions chia sẻ qua nhiều node.** Khi cần scale đa instance, thiết kế external broker và routing phù hợp.
11. **Không kiểm soát user destinations và permissions.** Message limit giúp tài nguyên, không thay authentication/authorization tại handshake, inbound channel và destination.
12. **Mặc định mã close/ERROR giống nhau mọi browser.** Giới hạn có thể bị container chặn trước Spring; ghi log theo từng lớp và xây client chịu lỗi.

## 10. Câu hỏi tự kiểm tra

1. **Một STOMP SEND có body 100 KiB chia thành nhiều WebSocket messages 8 KiB, nhưng `setMessageSizeLimit(64 KiB)` thì sao?** → Spring kiểm tra kích thước STOMP message được ghép; chia nhỏ frame không “vượt rào” giới hạn.
2. **Trong cùng một session, thread A đang gửi chậm; thread B phát thêm message?** → B không nên ghi cạnh tranh vào socket; thông điệp được buffer/serialize và có thể khiến session chạm send buffer/time limit.
3. **`sendTimeLimit(5_000)` có bảo đảm một send blocking tự bị cắt chính xác sau 5 giây?** → Không; thời điểm kiểm tra giới hạn phụ thuộc sự kiện gửi/đóng và transport. Dùng monitoring/timeout ở tầng khác nếu cần.
4. **Tại sao mất mạng không luôn sinh DISCONNECT ngay?** → Client không kịp gửi frame; server phải dựa vào lỗi I/O, idle timeout hoặc heartbeat để phát hiện.
5. **Vì sao `SessionDisconnectEvent` cần idempotency?** → Event có thể phát hơn một lần; cleanup cần an toàn khi gọi lặp.
6. **Khi bật `setPreservePublishOrder(true)` có đảm bảo thứ tự global không?** → Không; chủ yếu áp dụng thứ tự publish trong một client session.
7. **Nếu một client mobile reconnect, subscription cũ có tự xuất hiện?** → Không; client cần đăng ký lại trên session mới và đồng bộ message đã bỏ lỡ nếu nghiệp vụ yêu cầu.
8. **Giới hạn 1 KiB inbound có làm client không bao giờ nhận MESSAGE lớn hơn 1 KiB?** → Không; inbound STOMP message limit không phải outbound maximum. Phải áp dụng chính sách payload khác nếu cần.

## 11. Tài liệu chính thức

- Spring Framework — STOMP performance: https://docs.spring.io/spring-framework/reference/web/websocket/stomp/configuration-performance.html
- Spring Framework — Message ordering: https://docs.spring.io/spring-framework/reference/web/websocket/stomp/ordered-messages.html
- Spring Framework — STOMP Simple Broker và heartbeat: https://docs.spring.io/spring-framework/reference/web/websocket/stomp/handle-simple-broker.html
- Spring Framework — STOMP application-context events: https://docs.spring.io/spring-framework/reference/web/websocket/stomp/application-context-events.html
- Spring Framework — STOMP monitoring/statistics: https://docs.spring.io/spring-framework/reference/web/websocket/stomp/stats.html
- Spring Framework — WebSocket server container config: https://docs.spring.io/spring-framework/reference/web/websocket/server.html
- Spring Framework 6.1 Javadoc — `WebSocketTransportRegistration`: https://docs.spring.io/spring-framework/docs/6.1.11/javadoc-api/org/springframework/web/socket/config/annotation/WebSocketTransportRegistration.html
- Spring Framework 6.1 Javadoc — `ConcurrentWebSocketSessionDecorator`: https://docs.spring.io/spring-framework/docs/6.1.11/javadoc-api/org/springframework/web/socket/handler/ConcurrentWebSocketSessionDecorator.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="websocket-stomp-006">SockJS fallback và các ràng buộc khi đi qua proxy</a>

<details>
<summary>Xem chi tiết</summary>

# SockJS fallback và các ràng buộc khi đi qua proxy

## 1. Mục tiêu và phạm vi

WebSocket tạo kênh giao tiếp hai chiều, tồn tại lâu giữa trình duyệt và máy chủ. Tuy nhiên đường đi thực tế thường gồm trình duyệt → proxy của doanh nghiệp/CDN → load balancer hoặc reverse proxy → Spring application. Một proxy có thể không chuyển tiếp WebSocket Upgrade, đóng kết nối tưởng như rảnh, hoặc buffer phản hồi HTTP khiến dữ liệu streaming không đến được trình duyệt đúng lúc.

**SockJS** là giao thức mô phỏng trải nghiệm WebSocket, ưu tiên dùng WebSocket khi có thể, rồi chuyển sang các **HTTP transports** phù hợp (HTTP streaming hoặc long polling) khi cần. Spring Framework cung cấp server SockJS trên **Servlet stack** cùng khả năng tích hợp STOMP messaging. SockJS không sửa được mọi lỗi mạng, không tự cung cấp message broker và cũng không thay thế cấu hình reverse proxy đúng.

Sau bài này, bạn sẽ:
- Hiểu trình tự chọn transport: `/info` → WebSocket → streaming/polling, và vì sao fallback vẫn có thể thất bại.
- Phân biệt WebSocket Upgrade với các HTTP GET/POST của SockJS; biết những path và header proxy không được chặn.
- Cấu hình Spring MVC + STOMP + SockJS và client trình duyệt với ví dụ Java 17/Spring Boot 3.3.x có thể chạy.
- Đánh giá timeout, buffering, heartbeat, CORS, Origin, cookie, CSRF và security khi reverse proxy/LB đứng giữa.
- Hiểu sticky session và giới hạn của simple STOMP broker khi scale nhiều instance.
- Tái hiện lỗi và kiểm thử **đúng fallback transport**, không kết luận từ duy nhất một WebSocket handshake thành công.

### Có thật sự cần SockJS?

Trình duyệt hiện đại hầu hết hỗ trợ WebSocket. Nếu mạng, proxy và thiết bị client của hệ thống đều hỗ trợ WebSocket ổn định, **STOMP over native WebSocket thường đơn giản hơn**: ít đường truyền, ít request phụ, ít chi phí tương thích. Chỉ bật SockJS nếu cần hỗ trợ đường mạng không cho WebSocket hoạt động hoặc một nhóm client cũ được xác định. Đừng kích hoạt SockJS theo phản xạ chỉ vì ứng dụng sử dụng STOMP.

## 2. Các lớp giao thức cần phân biệt

| Lớp | Vai trò | Ví dụ |
| --- | --- | --- |
| TCP/TLS | Kết nối vận chuyển; HTTPS/WSS mã hóa đường truyền | Trình duyệt ↔ proxy |
| HTTP / WebSocket handshake | Upgrade từ HTTP sang WebSocket; hoặc request HTTP bình thường | GET + Upgrade, trạng thái 101 |
| SockJS | Chọn WebSocket hoặc mô phỏng qua HTTP, quản lý session/frames ở tầng transport | `/ws/info`, `/ws/{server}/{session}/xhr` |
| STOMP | Giao thức SEND/SUBSCRIBE/MESSAGE trên kênh đã mở | SUBSCRIBE `/topic/greetings` |
| Spring application | Xử lý message ở `@MessageMapping` và publish qua broker | `/app/hello` |

**SockJS không đồng nghĩa STOMP.** Có thể dùng SockJS cùng WebSocketHandler không có STOMP. Ngược lại có thể dùng STOMP trên WebSocket native không có SockJS. Khi ứng dụng cấu hình `registry.addEndpoint("/ws").withSockJS()`, `/ws` là **HTTP base URL của SockJS endpoint**, không phải địa chỉ STOMP destination. Client STOMP thường dùng `webSocketFactory` tạo SockJS rồi gửi STOMP frames qua connection đó.

### 2.1. Đường kết nối SockJS

Giả sử ứng dụng triển khai endpoint `/ws`:

1. Trình duyệt gọi `GET https://example.org/ws/info`. Response JSON cho biết một số khả năng của server, chẳng hạn `websocket`, `cookie_needed`, entropy. URL `/info` phải đến được server; status 404/403/502 tại đây có thể làm kết nối chết **trước khi** thử WebSocket.
2. Nếu điều kiện trình duyệt/mạng cho phép, SockJS thử WebSocket tại path có định danh session. Phía proxy phải chuyển handshake và sau đó duy trì kết nối hai chiều.
3. Nếu không thể dùng WebSocket, SockJS có thể dùng HTTP streaming: một request giữ mở để server đẩy dữ liệu xuống; HTTP POST khác gửi dữ liệu lên.
4. Nếu streaming không khả dụng, client có thể chuyển sang HTTP long polling: duy trì một request nhận dữ liệu, trả response khi có dữ liệu/timeout rồi mở request kế tiếp; request gửi dữ liệu vẫn riêng.
5. Khi kênh mở, STOMP client gửi CONNECT rồi SUBSCRIBE/SEND như bình thường. Đổi transport không đồng nghĩa đổi destination hoặc server-side `@MessageMapping`.

**Không đảm bảo** client nào cũng luôn “WebSocket hỏng thì thành công bằng polling”. CORS, cookies, streaming proxy, authentication, session affinity, policy mạng và giới hạn kết nối đều có thể làm fallback hỏng. Chính client SockJS quyết định danh sách transports được hỗ trợ; giới hạn transports trong test để xác định từng đường truyền.

### 2.2. Cấu trúc URL và session

Các request transport thường nằm dưới:

~~~text
https://example.org/ws/{server-id}/{session-id}/{transport}
~~~

Ví dụ URL ở cấp HTTP, minh họa chứ không phải session thật:

~~~text
GET  /ws/info
GET  /ws/123/sessionABC/websocket       # WebSocket Upgrade
POST /ws/123/sessionABC/xhr             # Nhận dữ liệu qua long polling
POST /ws/123/sessionABC/xhr_send        # Gửi dữ liệu qua HTTP
POST /ws/123/sessionABC/xhr_streaming   # Nhận dữ liệu streaming
~~~

Trong tên **transport của client SockJS** có thể thấy `xhr-polling` hoặc `xhr-streaming`, còn **đoạn path HTTP thực tế** của protocol có dạng `xhr`, `xhr_send`, `xhr_streaming`. Dựa vào tab Network để kiểm tra tên thực tế; đừng xây proxy chỉ cho phép duy nhất `/ws/websocket`.

- `server-id`: một thành phần URL có thể hỗ trợ quy tắc định tuyến ở load balancer; tự nó **không** đảm bảo cùng session sẽ luôn đến đúng backend.
- `session-id`: liên kết những request HTTP của cùng phiên SockJS. Một kết nối long polling cần gửi nhiều request với cùng định danh này.
- `transport`: cơ chế vận chuyển, ảnh hưởng method, thời gian mở kết nối và response framing.
- STOMP destinations như `/topic/greetings` **không phải URL HTTP để proxy rewrite**; chúng nằm trong nội dung frame STOMP.

SockJS frame có thể bắt đầu bằng `o` (mở), `h` (heartbeat), `a["..."]` (array messages), hoặc `c[...]` (đóng). Đây là tầng framing SockJS; bên trong payload có thể chứa STOMP frames. Nếu nhìn thấy `o` ở Network, mới chỉ biết SockJS session được mở, **chưa chứng minh STOMP CONNECT và SUBSCRIBE thành công**.

## 3. Spring server: bật SockJS đúng cách

Để dùng STOMP trên Spring MVC, cấu hình `@EnableWebSocketMessageBroker`, chọn endpoint HTTP và destinations. Mẫu tối thiểu:

~~~java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic");
    }
}
~~~

Đây là đoạn cấu hình tích hợp, import thuộc `org.springframework.context.annotation` và `org.springframework.web.socket.config.annotation`. `withSockJS()` bật transport fallback; nếu bỏ nó, dùng native WebSocket endpoint và tạo client bằng WebSocket URL `ws://`/`wss://` thay cho SockJS HTTP base URL.

Một controller:

~~~java
@Controller
public class GreetingController {
    @MessageMapping("/hello")
    @SendTo("/topic/greetings")
    public Greeting greet(HelloMessage message) {
        return new Greeting("Xin chào, " + message.name());
    }
}
~~~

`HelloMessage`/`Greeting` là DTO của ứng dụng. Client SEND `/app/hello` được route tới method `@MessageMapping("/hello")`; người đã SUBSCRIBE `/topic/greetings` nhận kết quả. Proxy không cần biết các destination STOMP đó.

**CORS/Origin:** mặc định Spring WebSocket/SockJS chỉ chấp nhận cùng origin. Nếu web UI ở `https://app.example.org` và backend ở `https://api.example.org`, phải chủ động thiết kế cross-origin, xác thực và cookies. Ví dụ endpoint:

~~~java
registry.addEndpoint("/ws")
    .setAllowedOrigins("https://app.example.org")
    .withSockJS();
~~~

Snippet trên chỉ minh họa **một origin được tin cậy**; không dùng `"*"` tùy tiện khi có credentials. Khi origin bị giới hạn cụ thể, một số SockJS iframe transports không thể kiểm tra Origin có thể bị vô hiệu hóa. Trình duyệt có thể hỗ trợ fallback XHR bình thường mà không cần iframe; cần test các client thật của sản phẩm.

### Vì sao SockJS server khác WebFlux reactive endpoint?

Tích hợp `withSockJS()` và STOMP broker trong ví dụ thuộc cơ chế **Servlet / Spring MVC** của Spring Framework. Đừng áp nguyên cấu hình này vào một WebFlux-only handler và kỳ vọng tự có SockJS fallback tương đương. Hãy chọn stack, starter và annotation tương ứng. Trong môi trường tải cao, HTTP long polling/streaming tiêu thụ tài nguyên khác WebSocket native; đo thread/connection và memory theo stack triển khai.

## 4. Reverse proxy: hai dạng traffic phải cùng hoạt động

### 4.1. WebSocket Upgrade

Handshake WebSocket trên HTTP/1.1 thường có:

~~~http
GET /ws/123/sessionABC/websocket HTTP/1.1
Host: example.org
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Key: ...
Sec-WebSocket-Version: 13
~~~

`101 Switching Protocols` là dấu hiệu server chấp nhận Upgrade. Các header `Upgrade` và `Connection` thuộc nhóm hop-by-hop; proxy cần cấu hình chuyển đúng sang upstream. Sau 101, proxy phải duy trì kênh hai chiều, không tiếp tục xử lý từng message như request HTTP độc lập.

**Không copy nguyên `Connection: Upgrade` cho mọi HTTP request.** Với GET `/info` hoặc POST polling bình thường, không có handshake Upgrade; một số proxy config nên dùng map để chọn giá trị theo `$http_upgrade`.

### 4.2. HTTP streaming và polling

Khi WebSocket không được phép, proxy vẫn phải:
- Cho phép `GET /ws/info`, `OPTIONS` CORS khi cần, và HTTP `POST` cho polling/streaming/send. Không định tuyến SockJS chỉ theo GET WebSocket.
- Chuyển đúng **toàn bộ sub-path của `/ws/`**, không rewrite mất `{server-id}/{session-id}`. Đặc biệt lưu ý `proxy_pass` có hoặc không có trailing slash sẽ ảnh hưởng URI forwarding trong NGINX.
- Không cache `/info`, session frames hoặc những response polling/streaming nhạy cảm theo trạng thái phiên.
- Tránh response buffering cho streaming vì buffer có thể giữ dữ liệu đến khi đủ lớn mới gửi khiến trình duyệt tưởng kết nối “im lặng”.
- Cho phép request/response có thời gian tồn tại dài hơn HTTP REST thông thường. Kiểm tra timeout của *mọi tầng*: CDN/LB, reverse proxy, backend, và client.
- Giữ nguyên query, method, cookies, Content-Type, Origin và các header cần cho CORS/authentication.
- Cho phép nhiều HTTP request trong cùng session đi qua cùng backend hoặc có chiến lược session routing phù hợp.

**Streaming** đặc biệt nhạy với proxy buffering; **polling** đặc biệt nhạy với số lượng request, sticky session, CORS và giới hạn concurrent connections. Cấu hình tốt cho 101 chưa chắc tốt cho `xhr_streaming`/`xhr`.

### 4.3. Ví dụ NGINX minh họa

Đây là fragment để đưa vào cấu hình NGINX có sẵn; giả sử đã có `server` phục vụ `example.org` và ứng dụng Spring lắng nghe tại `127.0.0.1:8080`. `map` đặt trong `http` context, `location` đặt trong `server` context:

~~~nginx
# Đặt trong http { ... }
map $http_upgrade $connection_upgrade {
    default upgrade;
    ''      close;
}

# Đặt trong server { ... } của example.org
location /ws/ {
    # Không đặt dấu / cuối proxy_pass: giữ nguyên /ws/... cho Spring.
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;

    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection $connection_upgrade;

    proxy_buffering off;       # Quan trọng với SockJS HTTP streaming
    proxy_cache off;
    proxy_read_timeout 75s;    # Ví dụ: phải kiểm tra thực tế với heartbeat
    proxy_send_timeout 75s;
}
~~~

Fragment này **không phải file NGINX standalone**; còn phải cấu hình listener, TLS/certificates, routes khác, security và load balancer theo hệ thống thật. Nếu route đặt dưới prefix khác (ví dụ public `/api/ws` còn Spring là `/ws`), phải thiết kế rewrite và các forwarded headers tương ứng; không thử ngẫu nhiên nhiều dấu `/` khi deploy.

Tại môi trường production HTTPS, trình duyệt sẽ truy cập SockJS qua `https://example.org/ws`; transport WebSocket nội bộ có thể dùng `wss://` ở browser và được TLS-terminate tại edge. Không được trộn trang HTTPS với endpoint `http://`/`ws://` không an toàn vì mixed-content policy. Đảm bảo upstream nhận thông tin scheme/host chuẩn nếu ứng dụng cần sinh absolute URLs; chỉ tin `X-Forwarded-*` từ reverse proxy được kiểm soát.

**Giá trị 75s** ở ví dụ không phải chuẩn bắt buộc. Một số môi trường cắt ở 30/60/120 giây; đặt timeout cao hơn nhịp dữ liệu/heartbeat có biên an toàn, nhưng vẫn có giới hạn tài nguyên và timeout ứng dụng. Kiểm tra cả idle timeout của LB trước NGINX, bởi NGINX không thể vượt qua giới hạn ở hop phía ngoài.

## 5. Heartbeat, session lifecycle và timeout

SockJS có heartbeat frame `h` để tránh một số proxy coi kết nối yên lặng là chết. Theo cấu hình mặc định phổ biến của Spring, heartbeat SockJS được phát khoảng **25 giây** nếu không có message. Có thể thay đổi bằng:

~~~java
registry.addEndpoint("/ws")
    .withSockJS()
    .setHeartbeatTime(25_000);
~~~

Đây là snippet để tích hợp vào `registerStompEndpoints`; thời gian tính bằng **milliseconds**. Khi STOMP client và server đàm phán heartbeat STOMP, Spring không tiếp tục phát SockJS heartbeats độc lập theo cách thường thấy. Vì thế **đừng giả định lúc nào cũng có một frame `h` mỗi 25 giây**. Khi phân tích timeout, phải xem **traffic thực tế** được gửi trên kết nối (STOMP heartbeat, SockJS heartbeat hoặc messages khác).

Các giới hạn liên quan:
- **Proxy read/idle timeout:** quá ngắn có thể cắt WebSocket hoặc streaming dù app đang hoạt động.
- **Heartbeat scheduler:** thread pool thiếu công suất có thể làm heartbeat trễ dưới tải lớn; cần metric và sizing.
- **Disconnect delay:** với HTTP polling, khoảng ngắt giữa các receiving requests không nên khiến server kết luận session mất quá sớm khi mạng chậm.
- **Stream bytes limit/message cache:** streaming có thể kết thúc và nối lại để quản lý buffer; polling có thể cần cache messages giữa các request.
- **Client reconnect delay:** reconnect tạo session mới, cần SUBSCRIBE lại và xét liệu thông điệp lúc mất mạng có cần replay/ack hay không.

SockJS không cung cấp exactly-once delivery hay bảo đảm history replay. Nếu ứng dụng chat/trading cần không mất message, tầng nghiệp vụ phải có sequence ID, ACK, snapshot/replay hoặc durable broker theo yêu cầu.

## 6. Load balancer, sticky session và cluster

SockJS HTTP fallback thường gồm nhiều request cho cùng `session-id`: request nhận message, request gửi message, request nhận tiếp. Các thành phần state của SockJS session nằm tại server xử lý nó. Nếu LB phân tán ngẫu nhiên request đến các Spring instances, instance B có thể không tìm thấy session do instance A tạo. Dấu hiệu hay gặp: kết nối thành công rồi `404` cho `xhr_send`, đóng bất thường hoặc reconnect lặp.

~~~mermaid
flowchart LR
    B[Browser SockJS] --> L[Load balancer]
    L -->|info / first request| A[Spring instance A]
    L -.->|xhr_send same session, sai route| C[Spring instance B]
    A --- M[Session tại A]
    C --- E[Không có session tại B]
~~~

**Sticky session/session affinity** là giải pháp thường dùng: các request cùng một SockJS session đi tới một instance. LB có thể dùng cookie phù hợp, hoặc route có cơ chế ánh xạ rõ ràng; thành phần `server-id` trong URL chỉ có tác dụng khi LB **thực sự cấu hình luật để dùng nó**. Đừng suy ra rằng SockJS tự giải quyết sticky session.

Hai vấn đề khác nhau cần giải quyết riêng:
1. **Routing transport/session:** cùng SockJS session tới đúng node. Sticky routing giúp giữ trạng thái session.
2. **Phân phối message giữa nhiều nodes:** `enableSimpleBroker` là broker trong bộ nhớ của một ứng dụng và **không là broker cluster dùng chung**. Khi cần cross-instance subscriptions và routing phức tạp, xem xét external STOMP broker/relay và kiến trúc phân phối message tương ứng. Thêm broker relay **không tự thay thế** yêu cầu xử lý các HTTP request thuộc cùng SockJS session tại một node.

Khi node chết, sticky session không cứu được socket/session đang kết nối với node đó. Client cần reconnect, xác thực lại, subscribe lại; ứng dụng phải tự thiết kế phục hồi dữ liệu chưa nhận.

## 7. Origin, CORS, cookie, CSRF và authentication khi qua proxy

### 7.1. Origin và CORS

WebSocket handshake có header `Origin`, nhưng WebSocket trong browser không tuân thủ toàn bộ chính sách same-origin giống XMLHttpRequest. Spring có kiểm soát allowed origins ở endpoint; Spring Security có thể bổ sung kiểm soát khi xử lý STOMP CONNECT.

SockJS fallback HTTP qua XHR/polling tuân theo CORS của trình duyệt. Khi cross-origin, **OPTIONS preflight** có thể chạy trước POST; proxy/WAF không được tùy tiện chặn. Spring SockJS có thể tự thêm CORS headers nếu ứng dụng chưa gắn headers. Nếu filter/gateway đã thêm headers, cần tránh trả hai giá trị `Access-Control-Allow-Origin` mâu thuẫn. Khi dùng cookie, `Access-Control-Allow-Origin: *` không thể đi với credentialed requests theo CORS.

Một số việc cần kiểm:
- `Origin` do trình duyệt gửi là origin của trang UI, không phải luôn giống `Host` của backend.
- `Access-Control-Allow-Credentials`, cookie `SameSite`/`Secure` và chính sách third-party cookies có thể khiến auth hoạt động với WebSocket nhưng hỏng trên polling.
- CORS là chính sách browser. Client ngoài browser có thể giả `Origin`; authorization phía server vẫn bắt buộc.
- Giữ danh sách origins ngắn và cụ thể, dùng HTTPS; hạn chế pattern quá rộng.

### 7.2. Cookie và thông tin phiên

Request `/info` có thể trả `cookie_needed`, liên quan quyết định transport của SockJS. Trong Java Servlet, `JSESSIONID` thường được sử dụng; điều này không đồng nghĩa mọi ứng dụng đều bắt buộc dùng session cookie. Nếu JWT được dùng, phải hiểu rõ token đi ở đâu: WebSocket browser API hạn chế tự đặt arbitrary HTTP headers; SockJS XHR và STOMP CONNECT cũng có giới hạn khác nhau. Không truyền token trong query string một cách tùy tiện vì URL dễ xuất hiện trong log, analytics và proxy.

Proxy phải chuyển tiếp cookie khi session/auth cần nó. Với load balancer cookie-based stickiness, kiểm tra cookie được browser gửi tới các HTTP request `/ws/.../xhr` và `/xhr_send` hay không. Header `JSESSIONID` có thể hỗ trợ affinity, nhưng không chứng minh LB đã bật sticky mode.

### 7.3. CSRF và iframe

Với Spring Security STOMP, CSRF protection cho `CONNECT` giúp chống kết nối cross-site dùng session cookie. Với SockJS HTTP fallback, `CONNECT` nằm trong dữ liệu transport; không thể đơn giản gắn CSRF token bằng tùy chọn header HTTP của SockJS như một REST call. Theo tài liệu Spring Security, cung cấp CSRF token trong **STOMP connect headers**, đồng thời nếu cần thì ngoại lệ CSRF phía HTTP nên **chỉ áp dụng cho các SockJS endpoint liên quan**, không tắt toàn site.

Các iframe transports cũ còn vướng `X-Frame-Options: DENY`/CSP `frame-ancestors`. Nếu thật sự cần iframe cùng origin, cần policy phù hợp như SAMEORIGIN và cân nhắc nguồn tải SockJS client library. Với cấu hình allowed origins hạn chế, Spring có thể vô hiệu hóa các iframe transports có nguy cơ không kiểm origin. **Không hạ security mặc định chỉ để giữ mọi browser quá cũ**; chọn danh sách browser được hỗ trợ và test.

## 8. Ví dụ hoàn chỉnh Java 17 + Spring Boot 3.3: WebSocket và ép xhr-polling

Ví dụ cung cấp một Spring Boot app, STOMP echo endpoint, HTML client dùng SockJS + STOMP và test `/ws/info`. Trên trang web có tham số URL để **ép XHR long polling**, nhờ đó ta chứng minh fallback HTTP thực sự hoạt động thay vì chỉ kiểm WebSocket native.

Yêu cầu: **JDK 17+**, Maven, browser có thể tải hai thư viện JS từ CDN. Phần backend có thể chạy khi dependencies Maven đã có sẵn; để chạy browser hoàn toàn offline, tải/cấp phát hai thư viện JS từ static assets của ứng dụng thay cho CDN theo chính sách bản quyền/vận hành của dự án. Mẫu ghim Spring Boot `3.3.13` để tương thích API Spring Framework 6.1 và Java 17; ứng dụng mới nên cân nhắc một dòng Spring Boot hiện còn hỗ trợ.

### 8.1. Thư mục

~~~text
sockjs-proxy-demo/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/demo/SockJsDemoApplication.java
    │   └── resources/static/index.html
    └── test/
        └── java/demo/SockJsInfoTest.java
~~~

### 8.2. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.13</version>
        <relativePath/>
    </parent>
    <groupId>demo</groupId>
    <artifactId>sockjs-proxy-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <java.version>17</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-websocket</artifactId>
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
~~~

`spring-boot-starter-websocket` cung cấp Spring MVC WebSocket/STOMP support và Jackson phù hợp. Không cần cài WebFlux hoặc broker server bên ngoài cho demo một instance.

### 8.3. src/main/java/demo/SockJsDemoApplication.java

~~~java
package demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@SpringBootApplication
public class SockJsDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SockJsDemoApplication.class, args);
    }

    public record HelloMessage(String name) {}
    public record Greeting(String content) {}

    @Configuration
    @EnableWebSocketMessageBroker
    public static class WsConfig implements WebSocketMessageBrokerConfigurer {

        @Override
        public void registerStompEndpoints(StompEndpointRegistry registry) {
            // Cho cùng origin; cross-origin phải được cấu hình có chủ đích.
            registry.addEndpoint("/ws")
                .withSockJS()
                .setHeartbeatTime(25_000);
        }

        @Override
        public void configureMessageBroker(MessageBrokerRegistry registry) {
            registry.setApplicationDestinationPrefixes("/app");
            registry.enableSimpleBroker("/topic");
        }
    }

    @Controller
    public static class GreetingController {
        @MessageMapping("/hello")
        @SendTo("/topic/greetings")
        public Greeting greet(HelloMessage input) {
            return new Greeting("Xin chào, " + input.name());
        }
    }
}
~~~

Giải thích: `/ws` nhận SockJS/transport; `/app/hello` là STOMP SEND destination chuyển vào GreetingController; `/topic/greetings` là subscription qua simple broker. `Greeting` được Jackson serialize JSON từ Java record. Ví dụ cố ý không có authentication để tập trung vào vận chuyển trong môi trường localhost. **Không công khai endpoint demo như một production chat service** mà chưa bổ sung authentication, authorization và message validation.

### 8.4. src/main/resources/static/index.html

~~~html
<!doctype html>
<html lang="vi">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>SockJS transport demo</title>
</head>
<body>
  <h1>SockJS + STOMP</h1>
  <p id="state">Đang khởi tạo...</p>
  <button id="send" disabled>Gửi lời chào</button>
  <pre id="log"></pre>

  <script src="https://cdn.jsdelivr.net/npm/sockjs-client@1.6.1/dist/sockjs.min.js"></script>
  <script src="https://cdn.jsdelivr.net/npm/@stomp/stompjs@7.2.0/bundles/stomp.umd.min.js"></script>
  <script>
    const status = document.getElementById("state");
    const button = document.getElementById("send");
    const log = document.getElementById("log");
    const forcePolling =
      new URLSearchParams(window.location.search).get("transport") === "polling";

    const client = new StompJs.Client({
      // SockJS nhận URL HTTP(S), không truyền ws:// như native WebSocket.
      webSocketFactory: () => new SockJS(
        "/ws",
        null,
        forcePolling ? { transports: ["xhr-polling"] } : {}
      ),
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        status.textContent = forcePolling
          ? "Đã kết nối STOMP bằng xhr-polling"
          : "Đã kết nối STOMP bằng SockJS";
        button.disabled = false;
        // Đặt SUBSCRIBE trong onConnect vì reconnect tạo session mới.
        client.subscribe("/topic/greetings", frame => {
          const body = JSON.parse(frame.body);
          log.textContent += body.content + "\n";
        });
      },
      onWebSocketClose: () => {
        button.disabled = true;
        status.textContent = "Kết nối đóng; thử reconnect...";
      },
      onStompError: frame => {
        status.textContent = "Broker báo lỗi: " + frame.headers.message;
      }
    });

    button.addEventListener("click", () => {
      client.publish({
        destination: "/app/hello",
        body: JSON.stringify({ name: "Spring" }),
        headers: { "content-type": "application/json" }
      });
    });

    client.activate();
  </script>
</body>
</html>
~~~

Hai JavaScript file từ CDN cần truy cập Internet ở lần tải đầu (hoặc tự host tương ứng). Chế độ mặc định SockJS tự chọn transport; chế độ query `?transport=polling` ép `xhr-polling`, hữu ích trong kiểm thử proxy. `reconnectDelay` và STOMP heartbeat chỉ là cấu hình demo; khi triển khai thật cần thiết kế reconnection, auth/CSRF và backoff theo tải.

### 8.5. Test kiểm tra info endpoint

**src/test/java/demo/SockJsInfoTest.java**:

~~~java
package demo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SockJsInfoTest {

    @LocalServerPort
    int port;

    @Test
    void sockJsInfoEndpointResponds() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/ws/info"))
            .GET()
            .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(
            request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"websocket\""));
    }
}
~~~

Test này khởi chạy Spring Boot trên cổng ngẫu nhiên, gửi HTTP request thật và xác minh có response `/ws/info` với trường `websocket`. Nó **không tự kiểm tra tất cả fallback transports**, STOMP subscription, proxy hoặc load balancer; các bước test riêng ở phần tiếp theo giải quyết những tầng đó.

### 8.6. Chạy ứng dụng và kết quả quan sát

Tại thư mục `sockjs-proxy-demo`:

~~~bash
mvn test
mvn spring-boot:run
~~~

Lệnh `mvn test` được kỳ vọng thành công (exit code 0) khi test `SockJsInfoTest` chạy qua. Lệnh sau khởi động server; terminal giữ tiến trình Spring Boot. Nếu không cấu hình port riêng, ứng dụng nghe tại `localhost:8080`. Mở trong browser:

~~~text
http://localhost:8080/
http://localhost:8080/?transport=polling
~~~

Ở mỗi trang, sau khi kết nối thành công bấm **Gửi lời chào**. Phần log hiển thị một dòng do ứng dụng tạo:

~~~text
Xin chào, Spring
~~~

Kết quả này xảy ra sau khi client CONNECT → SUBSCRIBE `/topic/greetings` → SEND `/app/hello` và nhận MESSAGE. Trang có thể hiển thị dòng trạng thái khác nhau tùy chế độ transport. Bật DevTools → Network:
- Trang mặc định: thường thấy `GET /ws/info` và request WebSocket `.../websocket` được Upgrade.
- Trang `?transport=polling`: thấy `GET /ws/info`, request `POST .../xhr` và `POST .../xhr_send`; **không cần 101 Upgrade** để trao đổi STOMP.
- Nếu phản hồi từ CDN bị chặn bởi chính sách browser/firewall, trang chưa thể thực hiện test; kiểm tra tải JS trong Console trước khi chẩn đoán backend.

Kiểm tra backend mà không cần browser:

~~~bash
curl -i http://localhost:8080/ws/info
~~~

Kỳ vọng status **HTTP 200** và JSON chứa thông tin SockJS như `"websocket": true`. Các field khác và giá trị `entropy` có thể khác mỗi lần; **không so khớp cả body JSON theo chuỗi cố định**.

## 9. Kiểm thử qua reverse proxy và tái hiện lỗi

Không kết luận proxy chạy tốt nếu test chỉ gọi trực tiếp `localhost:8080`: đường đó đi vòng qua NGINX/CDN/LB. Hãy chạy cùng hai URL **qua hostname công khai** (HTTPS) sau khi cấu hình proxy.

### Ma trận kiểm thử bắt buộc

| Tình huống | Cách thực hiện | Tín hiệu thành công |
| --- | --- | --- |
| SockJS info | GET `https://host/ws/info` | 200, JSON được phục vụ qua proxy |
| WebSocket preferred | Browser mặc định; xem WS tab | 101, STOMP CONNECT/SUBSCRIBE/SEND và nhận MESSAGE |
| Polling forced | `?transport=polling` qua proxy | POST xhr/xhr_send thành công, nhận MESSAGE |
| Streaming | Giới hạn client transports thành xhr-streaming ở test riêng | Data frame tới browser sớm, không đợi buffer đầy |
| Chặn WebSocket có chủ đích | Test trên môi trường có proxy không hỗ trợ Upgrade | Fallback thành công nếu mạng vẫn cho HTTP vận chuyển |
| Chờ idle | Giữ connection qua nhiều heartbeat intervals | Không bị proxy cắt vô cớ |
| Hai Spring instances | Qua LB có affinity được cấu hình | Các request của session nhất quán tới đúng node |
| Origin khác | UI ở origin được phép và một origin không được phép | Allowed origin hoạt động; origin lạ bị từ chối |
| Cookies/CSRF | Môi trường có Spring Security | STOMP CONNECT và HTTP fallback tuân thủ chính sách |
| Reconnect | Tạm dừng mạng rồi bật lại | Session mới được mở và SUBSCRIBE lại, dữ liệu có chiến lược phục hồi |

**Force `xhr-streaming`:** đổi dòng cấu hình client thành `{ transports: ["xhr-streaming"] }` trong bản test riêng; không cần làm điều này ở code production. Tab Network giúp phát hiện `xhr_streaming` chỉ trả dữ liệu sau thời gian dài do proxy buffering. Một transport bị chặn có thể khiến test fail; đó là bằng chứng về giới hạn của môi trường.

### Chẩn đoán theo hiện tượng

1. **`/ws/info` trả 404:** kiểm tra context path Spring, location/rewrite và đường proxy_pass; request chưa tới SockJS endpoint.
2. **`/ws/info` trả 403:** kiểm tra Origin, CORS filter, Spring Security, CSRF cho các HTTP methods; đừng vội mở `"*"` toàn site.
3. **101 Upgrade không xảy ra, chỉ WebSocket fail:** kiểm tra `Upgrade`/`Connection` và HTTP/1.1 ở upstream, CDN/LB có cho WebSocket và TLS scheme đúng không.
4. **WebSocket chạy nhưng polling fail:** kiểm tra HTTP POST/OPTIONS, CORS, cookie, `/ws/{server}/{session}/xhr*`, WAF rules, giới hạn body và affinity.
5. **Streaming nhận một loạt message trễ:** proxy buffering/caching có thể giữ bytes; thử tắt buffering chỉ trên route cần thiết và đo thời gian frame.
6. **Kết nối ngắt đều đặn mỗi ~30s hoặc ~60s:** so sánh proxy/LB idle timeout với heartbeat được gửi thực tế, không chỉ heartbeat cấu hình.
7. **Một node chạy, hai node lỗi lúc gửi message:** xem routing session-id, cookie affinity và broker state; không đổ lỗi cho SockJS client ngay.
8. **CONNECT thất bại sau HTTP 200:** SOCKJS open frame không đồng nghĩa STOMP auth/CSRF qua broker thành công; xem STOMP ERROR và Spring message security.
9. **Trang HTTPS tới endpoint HTTP:** mixed-content; phải dùng HTTPS SockJS URL/WSS native WebSocket.
10. **Reconnect lặp vô hạn và tải tăng:** giới hạn reconnect backoff, request rate và session timeout; tránh tạo reconnect storm khi upstream chết.

### Các chỉ số nên thu thập

- Count sessions mở/đóng, transport phân bố WebSocket/xhr_streaming/xhr, tỷ lệ fallback thành công.
- Status/error trên `/ws/info`, `.../websocket`, `.../xhr`, `.../xhr_send`; proxy 499/502/504 và thời gian chờ.
- Number of currently open WebSocket and long polling/streaming connections; queue/message buffer, memory và thread usage.
- STOMP CONNECT success/error, SUBSCRIBE count, message latency, reconnect attempts.
- Theo node trong cluster: session routes, sticky affinity hits/misses, lỗi session unknown.
- Tách metric **transport** (HTTP/SockJS) khỏi metric **application message** (STOMP); HTTP 200 cho `/info` không đảm bảo broadcast thành công.

## 10. Các hiểu nhầm và lỗi phổ biến

- **“Có SockJS thì khỏi cấu hình proxy WebSocket”:** SockJS có thể cứu một số đường mạng qua HTTP, nhưng đường ưu tiên vẫn có thể là WebSocket và phải hỗ trợ Upgrade nếu muốn tối ưu.
- **“WebSocket đã lên 101 nên SockJS polling cũng chạy”:** polling phụ thuộc POST, buffering, cookie, CORS, timeout và sticky session riêng.
- **“SockJS fallback chắc chắn giải quyết corporate proxy”:** proxy có thể chặn cả long-lived HTTP request hoặc các POST đặc biệt; không có đảm bảo chung.
- **“`/topic/x` là route NGINX”:** đó là STOMP destination, không phải endpoint HTTP.
- **“`server-id` của SockJS tự pin vào backend”:** chỉ là thành phần URL; LB phải được cấu hình routing cụ thể.
- **“Broker simple chạy cluster”:** broker nội bộ không phân phối subscription qua các instance; cần kiến trúc external broker nếu có cluster.
- **“Mọi 25 giây luôn có SockJS heartbeat `h`”:** STOMP heartbeat đã đàm phán có thể thay thế heartbeat SockJS.
- **“Bật CORS `*` giải quyết auth”:** credentialed cross-origin có ràng buộc nghiêm ngặt; wildcard dễ gây lỗi browser và mở rộng tấn công.
- **“Tắt toàn bộ CSRF cho SockJS”:** chỉ ngoại lệ HTTP endpoint cụ thể khi thật sự cần, còn STOMP CONNECT cần được bảo vệ phù hợp.
- **“Mặc định mọi browser cũ sẽ dùng iframe”:** allowed origins và X-Frame-Options/CSP có thể loại bỏ iframe transports.
- **“Không cần test client thật”:** một unit test Java không phát hiện cấu hình browser mixed-content, CSP, cookie, CDN, iframe hay proxy buffering.
- **“Reconnect tự động phát lại message”:** chỉ tạo kết nối mới; phải subscribe lại và xử lý missing messages theo contract nghiệp vụ.

## 11. Checklist triển khai thực tế

Trước khi bật SockJS trên production, xác định rõ *vì sao* cần fallback và những browser/network được hỗ trợ; thử WebSocket native trong môi trường đích trước khi thêm độ phức tạp.

Kiểm tra toàn chuỗi: `/ws/info` và mọi SockJS transport path không bị rewrite/chặn; NGINX chuyển WebSocket Upgrade đúng, tắt buffering cho streaming, định nghĩa timeouts dựa trên heartbeat thực tế, và có affinity khi nhiều application nodes. Đảm bảo scheme HTTPS/WSS, Origin/CORS/CSRF/cookies/auth, frame-options và CSP theo security policy.

Kiểm thử hai đường transport qua **hostname công khai**, đo reconnect/fallback, thử lỗi node và refresh phiên. Chỉ tuyên bố triển khai thành công khi client nhận được **STOMP MESSAGE thực**, cả với WebSocket và HTTP polling; status 200 từ `/info` là cần nhưng chưa đủ.

## 12. Tài liệu tham khảo

- Spring Framework Reference — SockJS Fallback: https://docs.spring.io/spring-framework/reference/6.2/web/websocket/fallback.html
- Spring Framework Reference — Enable STOMP: https://docs.spring.io/spring-framework/reference/6.2/web/websocket/stomp/enable.html
- Spring Framework 6.1 Javadoc — SockJsServiceRegistration: https://docs.spring.io/spring-framework/docs/6.1.15/javadoc-api/org/springframework/web/socket/config/annotation/SockJsServiceRegistration.html
- Spring Security Reference — WebSocket Security, SockJS & CSRF: https://docs.spring.io/spring-security/reference/servlet/integrations/websocket.html
- NGINX official docs — ngx_http_proxy_module: https://nginx.org/en/docs/http/ngx_http_proxy_module.html
- StompJS guide — Using STOMP with SockJS: https://stomp-js.github.io/guide/stompjs/rx-stomp/ng2-stompjs/using-stomp-with-sockjs
- SockJS protocol definitions — HTTP paths and frame semantics: https://github.com/sockjs/sockjs-protocol


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="websocket-stomp-007">STOMP, destination và luồng thông điệp qua các channel của Spring</a>

<details>
<summary>Xem chi tiết</summary>

# STOMP, destination và luồng thông điệp qua các channel của Spring

## 1. Mục tiêu và bức tranh tổng quan

**WebSocket** cung cấp kết nối song công lâu dài giữa client và server, nhưng chỉ định nghĩa cách vận chuyển dữ liệu; nó không tự quy định message phải gửi đến ai, client đăng ký nhận như thế nào, hay nội dung mang cấu trúc gì. **STOMP (Simple Text Oriented Messaging Protocol)** bổ sung một giao thức nhắn tin dựa trên *frame*, gồm command, headers và body. Spring Framework hỗ trợ **STOMP over WebSocket** bằng cách ánh xạ các frame thành **Spring Message**, định tuyến qua các **MessageChannel**, gọi phương thức **@MessageMapping**, rồi gửi kết quả tới broker và các client đăng ký.

Trong bài này cần phân biệt **ba loại địa chỉ** thường bị gọi chung là endpoint:

1. **WebSocket handshake endpoint**, ví dụ **/ws**: URL mà browser dùng để mở kết nối WebSocket.
2. **Application destination**, ví dụ **/app/chat.send**: STOMP destination được Spring chuyển tới **@MessageMapping("/chat.send")**.
3. **Broker destination**, ví dụ **/topic/chat**: tên nơi client **SUBSCRIBE** để broker phân phối tin tới subscriber.

Ba tên trên đều do ứng dụng cấu hình. Chúng phục vụ ba giai đoạn khác nhau; **/ws không phải nơi client SEND tin nhắn STOMP**, và **/topic/chat không tự gọi controller** khi tiền tố đã tách đúng.

### Những câu hỏi cần tự trả lời sau bài

- Vì sao hai frame **SEND** và **SUBSCRIBE** có thể đi qua cùng WebSocket nhưng được xử lý bởi các thành phần khác nhau?
- Ý nghĩa của **/app**, **/topic**, **/queue**, **/user** là gì, và phần nào do Spring quy định qua cấu hình?
- Một tin từ browser đến **@MessageMapping** đi qua channel nào, và phản hồi đến subscriber đi qua channel nào?
- **clientInboundChannel**, **brokerChannel**, **clientOutboundChannel** có nhiệm vụ gì? Tại sao tên channel không nên nhầm với STOMP destination?
- Khác biệt của **simple broker** và **STOMP broker relay**, cách theo dõi luồng, thứ tự và lỗi.

## 2. STOMP frame: message ở cấp giao thức

Một STOMP frame có dạng:

~~~text
COMMAND
header-1:value
header-2:value

BODY<NUL>
~~~

**<NUL>** trong sơ đồ chỉ ký tự kết thúc frame có mã byte **0x00**, không phải chuỗi chữ NUL. Cấu trúc thực gồm dòng command, các dòng header, một dòng trống, body (nếu có) và ký tự NUL kết thúc. Body có thể là text hoặc binary; với JSON nên có content-type thích hợp.

### Ví dụ tiến trình kết nối và gửi tin

Client kết nối WebSocket tới **ws://localhost:8080/ws**, sau đó gửi STOMP **CONNECT**:

~~~text
CONNECT
accept-version:1.2
heart-beat:10000,10000

<NUL>
~~~

Server trả **CONNECTED** nếu phiên STOMP được chấp nhận:

~~~text
CONNECTED
version:1.2
heart-beat:0,0

<NUL>
~~~

Client muốn nhận dữ liệu chat gửi **SUBSCRIBE**:

~~~text
SUBSCRIBE
id:sub-chat-1
destination:/topic/chat

<NUL>
~~~

Cùng client, hoặc một client khác, muốn phát tin cho server xử lý gửi **SEND**:

~~~text
SEND
destination:/app/chat.send
content-type:application/json

{"from":"An","text":"Chào mọi người"}<NUL>
~~~

Nếu controller trả kết quả tới **/topic/chat** thì broker gửi STOMP **MESSAGE** đến từng subscription phù hợp:

~~~text
MESSAGE
subscription:sub-chat-1
destination:/topic/chat
message-id:msg-1
content-type:application/json

{"from":"An","text":"Chào mọi người"}<NUL>
~~~

Các frame minh họa có thể khác headers, message-id, heartbeat thực tế tùy broker/thư viện; ví dụ **msg-1** chỉ để dễ đọc, không phải giá trị Spring luôn tạo. STOMP còn có **UNSUBSCRIBE**, **DISCONNECT**, **ACK**, **NACK**, **RECEIPT**, **ERROR**; không phải broker nào cũng hỗ trợ trọn bộ tính năng.

| Command | Hướng điển hình | Ý nghĩa |
| --- | --- | --- |
| CONNECT | Client → server | Bắt đầu phiên STOMP trên kết nối đã mở |
| CONNECTED | Server → client | Thông báo phiên STOMP đã thiết lập |
| SUBSCRIBE | Client → server | Đăng ký một destination; cần header id |
| SEND | Client → server | Gửi payload đến destination |
| MESSAGE | Server/broker → client | Chuyển thông điệp đến subscription |
| UNSUBSCRIBE | Client → server | Bỏ đăng ký theo subscription id |
| DISCONNECT | Client → server | Kết thúc phiên theo giao thức |
| ACK/NACK | Client → broker | Xác nhận/từ chối giao nhận nếu broker/mode hỗ trợ |
| ERROR | Server/broker → client | Báo lỗi cấp giao thức |
| RECEIPT | Server/broker → client | Phản hồi yêu cầu receipt khi được hỗ trợ |

**Quan trọng:** WebSocket message, STOMP frame và Spring **Message<?>** không đồng nhất. Ở server, tầng WebSocket/STOMP giải mã frame, chuẩn hóa headers/payload thành Spring Message và đưa lên hệ thống channel nội bộ. Message converter có thể chuyển byte JSON thành một Java DTO khi handler chạy.

## 3. Cấu hình STOMP trong Spring

Một cấu hình tối thiểu Spring Boot/Spring Framework dùng **@EnableWebSocketMessageBroker**:

~~~java
package demo;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setUserDestinationPrefix("/user");
    }
}
~~~

Giải thích từng cấu hình:

- **registry.addEndpoint("/ws")** mở đường dẫn HTTP handshake nâng cấp lên WebSocket; browser kết nối qua **ws://host/ws** hoặc **wss://host/ws** khi dùng TLS. Chưa nói gì về **destination** của frame STOMP.
- **setApplicationDestinationPrefixes("/app")** xác định nhóm destination chuyển tới **SimpAnnotationMethodMessageHandler**, tức các phương thức **@MessageMapping** hoặc **@SubscribeMapping**. Spring thường loại bỏ tiền tố **/app** trước khi so khớp mapping.
- **enableSimpleBroker("/topic", "/queue")** bật broker bộ nhớ của Spring để quản lý subscription và gửi message ở các destination có tiền tố này. Broker không tự hiểu nghiệp vụ: nó không gọi **@MessageMapping** cho **/topic/chat**.
- **setUserDestinationPrefix("/user")** cấu hình tiền tố cho **UserDestinationMessageHandler**, hỗ trợ ánh xạ subscription theo từng phiên/người dùng. Đây không phải broker destination vật lý.
- **@EnableWebSocketMessageBroker** đăng ký các handler, message channels, converters và thành phần broker cần thiết.

Các tên **/app**, **/topic**, **/queue**, **/user** là **quy ước thông dụng do cấu hình quyết định**, không phải toàn bộ STOMP protocol bắt buộc phải dùng đúng tên này. Có thể chọn tiền tố khác, nhưng client và server phải thống nhất. Nên chọn các tiền tố không chồng lấn để một frame không bị nhiều handler xử lý ngoài ý muốn.

### Phân biệt endpoint HTTP và destination STOMP

| Địa chỉ | Nằm ở đâu | Ai xử lý | Client thực hiện |
| --- | --- | --- | --- |
| **/ws** | URL handshake HTTP/WebSocket | WebSocket handshake handler | new WebSocket("ws://.../ws") hoặc STOMP client |
| **/app/chat.send** | Header destination của SEND frame | Spring annotated controller | STOMP publish/SEND |
| **/topic/chat** | Header destination của SUBSCRIBE và MESSAGE | Broker | STOMP subscribe |
| **/queue/notifications** | Broker destination theo quy ước | Broker | Thường subscribe cho luồng thông báo |
| **/user/queue/notifications** | Logical user destination | UserDestinationMessageHandler → broker destination phiên | Subscribe riêng cho phiên/người dùng |

**/queue** theo tên gọi thường gợi ý point-to-point trong broker bên ngoài, nhưng **simple broker của Spring không tự cung cấp hàng đợi bền vững hay semantics competing consumers**; nó quản lý subscription trong bộ nhớ và phát cho các subscription khớp. Không dựa vào tên "/queue" để suy ra message delivery có persistence, ACK hay chỉ một consumer.

## 4. Ba message channel chính của Spring

Spring Messaging cung cấp các abstraction:

- **Message<T>**: payload kiểu T kèm headers, ví dụ destination, content-type, sessionId, simpMessageType.
- **MessageHandler**: thành phần xử lý message.
- **MessageChannel**: điểm gửi message, tách producer khỏi consumer.
- **SubscribableChannel**: message channel cho các handler đăng ký nhận.
- **ExecutorSubscribableChannel**: dạng channel chuyển việc tới executor/thread pool.

Cấu hình STOMP của Spring dùng ba tên channel quan trọng:

| Channel | Tín hiệu đi qua | Nhiệm vụ |
| --- | --- | --- |
| **clientInboundChannel** | Từ client WebSocket vào server | Nhận Spring Message đã giải mã STOMP frame và đưa đến controller, broker hoặc các handler phù hợp |
| **brokerChannel** | Từ mã ứng dụng trong server đến broker | Truyền message do controller, SimpMessagingTemplate hoặc thành phần server tạo để broker phân phối |
| **clientOutboundChannel** | Từ broker/handler server ra client | Đưa message đến WebSocket session rồi mã hóa thành STOMP MESSAGE/frame để gửi đi |

Hình luồng với simple broker:

~~~text
                 WebSocket clients
                 (STOMP frames)
                        │
            CONNECT / SUBSCRIBE / SEND
                        │
                        ▼
              WebSocket transport
              + STOMP frame decoder
                        │
                        ▼
               clientInboundChannel
                    /         \
                   /           \
       /app/chat.send        /topic/chat
             │               SUBSCRIBE
             ▼                  │
  SimpAnnotationMethod        Simple
     MessageHandler           Broker
             │                  ▲
     @MessageMapping            │
       @Controller              │
             │                  │
       @SendTo result ──► brokerChannel
                                │
                                ▼
                          Simple Broker
                                │
                                ▼
                       clientOutboundChannel
                                │
                       STOMP frame encoder
                                │
                                ▼
                     WebSocket subscribers
~~~

**Chỉnh cách đọc sơ đồ:** broker nhận subscription trực tiếp từ **clientInboundChannel**; message **@SendTo** từ controller đi qua **brokerChannel**. Ở bên ra, broker gửi các message đến **clientOutboundChannel**. Đây là mô hình luồng logic: việc chạy thread và dispatch thực tế phụ thuộc executor/handler của Spring. Không phải mọi message inbound đều luôn đi qua brokerChannel.

### Dòng thời gian cụ thể: một câu chat

Giả sử hai browser A và B đã kết nối tới **/ws**:

1. **A SUBSCRIBE /topic/chat** với subscription id **a1**. WebSocket transport giải mã và gửi Spring Message vào **clientInboundChannel**; simple broker nhận và lưu subscription của A.
2. **B SUBSCRIBE /topic/chat** với id **b1**. Broker lưu thêm subscription của B.
3. **A SEND /app/chat.send** với JSON **{"from":"An","text":"Xin chào"}**. Frame được giải mã, chuyển vào **clientInboundChannel**.
4. **SimpAnnotationMethodMessageHandler** nhận vì tiền tố **/app** thuộc ứng dụng; nó loại tiền tố để tìm method **@MessageMapping("/chat.send")** trong controller.
5. Spring **MessageConverter** biến JSON payload thành DTO Java. Controller kiểm tra/biến đổi theo nghiệp vụ và trả một object.
6. **@SendTo("/topic/chat")** biến kết quả thành message outbound với destination **/topic/chat**, chuyển message đó qua **brokerChannel** tới simple broker.
7. Broker đối chiếu destination với các subscription và gửi bản sao cho cả A, B qua **clientOutboundChannel**.
8. WebSocket transport chuyển từng Spring Message thành STOMP **MESSAGE** frame phù hợp với subscription id/session và đẩy qua WebSocket.

**Chú ý:** A cũng nhận tin của chính mình nếu A đã subscribe /topic/chat. Nếu không có subscription nào, broker không tự lưu tin để gửi lại sau này. Nếu client SEND đến destination **/topic/chat** thay vì **/app/chat.send**, simple broker có thể xử lý trực tiếp; controller **@MessageMapping("/chat.send")** sẽ không được gọi. Đây là nguyên nhân phổ biến khiến người dùng thấy tin được broadcast nhưng validation/nghiệp vụ bị bỏ qua.

## 5. @MessageMapping, @SendTo và SimpMessagingTemplate

Controller nhận message **/app/chat.send**:

~~~java
package demo;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class ChatController {

    public record ChatInput(String from, String text) {}
    public record ChatEvent(String from, String text) {}

    @MessageMapping("/chat.send")
    @SendTo("/topic/chat")
    public ChatEvent send(ChatInput input) {
        return new ChatEvent(input.from(), input.text());
    }
}
~~~

Tính chất:
- **@Controller** dùng với Spring Messaging annotation handler; không phải **@RestController** HTTP mapping.
- **@MessageMapping("/chat.send")** map destination sau khi bỏ prefix **/app**. Nó không cần **@PostMapping**.
- **ChatInput** được chuyển từ JSON bằng message converter thích hợp (Jackson khi được cấu hình).
- **@SendTo("/topic/chat")** chỉ rõ nơi publish **giá trị trả về**. Không có **@SendTo**, Spring có quy tắc destination phản hồi mặc định (theo cấu hình broker prefix); tuy nhiên khai báo tường minh giúp người đọc chắc chắn client phải subscribe ở đâu.
- Khi method trả **void**, không có outbound return value tự động. Muốn gửi message hãy inject **SimpMessagingTemplate** hoặc thực hiện side effect khác.
- **@DestinationVariable** ánh xạ biến trên đường destination như **@MessageMapping("/rooms/{roomId}")**; tương tự **@Header**, **@Payload**, **Principal** có thể dùng trong method parameters.

### Server chủ động publish mà không chờ STOMP SEND

Ứng dụng có thể phát thông điệp từ HTTP endpoint, scheduled job hoặc message queue consumer bằng **SimpMessagingTemplate**:

~~~java
@RestController
public class AnnouncementController {

    public record Announcement(String text) {}

    private final SimpMessagingTemplate messaging;

    public AnnouncementController(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @PostMapping("/api/announcements")
    public ResponseEntity<Void> publish(@RequestParam("text") String text) {
        messaging.convertAndSend(
            "/topic/announcements", new Announcement(text));
        return ResponseEntity.accepted().build();
    }
}
~~~

Các import cần là **org.springframework.messaging.simp.SimpMessagingTemplate**, **org.springframework.web.bind.annotation.RestController**, **PostMapping**, **RequestParam**, và **org.springframework.http.ResponseEntity**. Đây là fragment ứng dụng HTTP + WebSocket; phần mã runnable đầy đủ sẽ được trình bày ở mục 10.

**convertAndSend** chuyển object sang Spring Message và đưa qua **brokerChannel**; broker gửi tới subscriber của **/topic/announcements**. Không cần client nào SEND vào **/app** trước. Return HTTP **202 Accepted** ở ví dụ xác nhận endpoint đã xử lý yêu cầu publish ở tầng ứng dụng; nó không bảo đảm mọi browser subscriber đã nhận và xử lý thành công thông báo.

### @SubscribeMapping: đường rẽ khác với @MessageMapping

**@SubscribeMapping("/snapshot")** xử lý một **SUBSCRIBE /app/snapshot** ở annotated controller. Giá trị trả về mặc định đi **trực tiếp qua clientOutboundChannel** đến client vừa subscribe, **không đi brokerChannel** để broadcast hay đăng ký một subscription kéo dài. Điều này hữu ích để gửi snapshot ban đầu khi trang vừa mở:

~~~java
@SubscribeMapping("/snapshot")
public Snapshot initialState() {
    return service.currentSnapshot();
}
~~~

Nếu áp dụng **@SendTo** hoặc **@SendToUser**, hành vi trả kết quả có thể đi qua broker thay vì đường trả trực tiếp mặc định. Đừng dùng SUBSCRIBE **/app/snapshot** như cơ chế thay thế cho đăng ký dài hạn **/topic/updates** nếu cần stream thay đổi liên tục.

## 6. User destination: /user khác /queue thế nào?

Đôi khi server cần gửi thông báo riêng cho một tài khoản hoặc đúng một WebSocket session. Spring hỗ trợ **UserDestinationMessageHandler**, ánh xạ destination logic có tiền tố **/user** thành destination cụ thể cho từng session.

Client subscribe:

~~~text
SUBSCRIBE
id:me-1
destination:/user/queue/notifications

<NUL>
~~~

Ứng dụng gửi tới người dùng đã xác thực:

~~~java
messaging.convertAndSendToUser(
    username,
    "/queue/notifications",
    new Notification("Đã xử lý xong"));
~~~

Với **@MessageMapping**, có thể trả kết quả bằng **@SendToUser("/queue/notifications")**. Server sẽ ánh xạ destination tới một hoặc nhiều phiên của user theo các quy tắc riêng, ví dụ destination vật lý mang session suffix.

**Phân biệt rõ**:
- Client thường **SUBSCRIBE /user/queue/notifications** mà **không điền username** trong subscription destination.
- Mã server dùng **convertAndSendToUser(username, "/queue/notifications", payload)** hoặc **@SendToUser**.
- **/user** là tiền tố logic để Spring giải quyết người nhận, không phải bảo đảm phân quyền.
- Theo mặc định, khi một người dùng có nhiều phiên, thông điệp tới user có thể được phát tới nhiều phiên; **@SendToUser(broadcast=false)** có thể giới hạn trả lời cho phiên phát message khi phù hợp.
- Với một WebSocket session chưa gắn tài khoản, **@SendToUser** vẫn có thể gửi tới session đó khi broadcast=false; hành vi này không thay thế yêu cầu authentication thực sự.
- Khi triển khai nhiều instance và external broker, các user session phân tán cần được cấu hình/định tuyến phù hợp; không mặc định user đích ở instance khác luôn resolve được.

Chỉ định **/queue** trong user destination giúp biểu đạt ý định nhận thông báo cá nhân; không có nghĩa simple broker ghi thông báo bền vững vào ổ đĩa.

## 7. Simple broker và broker relay: đường đi giống và khác

### Simple broker tích hợp

Cấu hình:

~~~java
registry.setApplicationDestinationPrefixes("/app");
registry.enableSimpleBroker("/topic", "/queue");
~~~

Simple broker giữ thông tin subscription trong **bộ nhớ của ứng dụng Spring** và broadcast tin tới session đang kết nối. Nó phù hợp với demo, ứng dụng ít node hoặc hệ thống không đòi hỏi broker mạnh. Nó **không phải message broker đầy đủ**: không cung cấp toàn bộ chức năng STOMP như ACK, receipts, persistence, clustering hay phân phối durable theo kiểu enterprise broker.

Luồng nhận SEND /app → controller → brokerChannel → simple broker → clientOutboundChannel như đã phân tích. Với một SUBSCRIBE /topic, broker giữ subscription từ clientInboundChannel. Với nhiều instance Spring đứng sau load balancer, subscription đơn giản trên node A không được node B biết một cách tự động.

### STOMP broker relay

Khi cần broker thực thụ (ví dụ RabbitMQ/ActiveMQ có bật STOMP), Spring có thể dùng **relay**:

~~~java
@Override
public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.setApplicationDestinationPrefixes("/app");
    registry.enableStompBrokerRelay("/topic", "/queue")
        .setRelayHost("127.0.0.1")
        .setRelayPort(61613)
        .setClientLogin("guest")
        .setClientPasscode("guest")
        .setSystemLogin("guest")
        .setSystemPasscode("guest");
}
~~~

Thông số host, port, account ở đoạn này **chỉ minh họa**, phải lấy từ hệ thống broker được triển khai và bảo vệ secrets trong cấu hình môi trường. **Spring không tự khởi động RabbitMQ/ActiveMQ** khi bật relay.

Luồng khác đi như sau:

~~~text
Client SEND /app/action
       |
       v
clientInboundChannel
       |
 @MessageMapping
       |
 brokerChannel
       |
  STOMP broker relay ──TCP──> External broker
                                 |
                          subscribers matched
                                 |
                         TCP reply to relay
                                 |
                       clientOutboundChannel
                                 |
                            WebSocket clients
~~~

Đối với SUBSCRIBE /topic, relay chuyển subscription đến broker bên ngoài qua TCP. Với SEND vào destination do broker quản lý, relay cũng chuyển lên broker. **@MessageMapping** vẫn chạy trong Spring đối với application destinations như /app.

Relay duy trì TCP connections đến broker, có connection hệ thống và các connection đại diện cho WebSocket clients theo cơ chế relay. Khi broker mất kết nối, cần quản lý reconnect/availability và theo dõi **BrokerAvailabilityEvent**; không coi relay như một queue vô hạn trong bộ nhớ. External broker có thể hỗ trợ ACK, receipts, heartbeat, durable subscriptions và routing nâng cao, nhưng **chỉ khi broker và cách cấu hình thực sự hỗ trợ**.

## 8. Ordering, executor và các vấn đề vận hành

Thông thường **clientInboundChannel** và **clientOutboundChannel** sử dụng executor. Điều này tăng throughput nhưng có hệ quả: message từ một phiên có thể được xử lý/gửi trong thread khác nhau; thứ tự thực tế có thể không trùng thứ tự frame được nhận hoặc thứ tự message được publish.

Nếu thứ tự là yêu cầu nghiệp vụ (ví dụ các bản cập nhật tuần tự trong một phiên), Spring cung cấp lựa chọn:

~~~java
@Override
public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws");
    registry.setPreserveReceiveOrder(true);
}

@Override
public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.setApplicationDestinationPrefixes("/app");
    registry.enableSimpleBroker("/topic", "/queue");
    registry.setPreservePublishOrder(true);
}
~~~

- **setPreserveReceiveOrder(true)** giúp Spring xử lý các message inbound trong cùng WebSocket session theo thứ tự nhận trên **clientInboundChannel**.
- **setPreservePublishOrder(true)** giúp các message outbound trong cùng session được publish tới **clientOutboundChannel** theo thứ tự.
- Hai tùy chọn có chi phí hiệu năng và **không hứa bảo đảm thứ tự toàn cục giữa mọi session, instance hay external broker**. Cần sequence number ở payload nếu ứng dụng cần so sánh/thứ tự nghiệp vụ cross-node.

### Interceptor và observability

Để quan sát/kiểm tra frame STOMP inbound, dùng **ChannelInterceptor** gắn qua **configureClientInboundChannel**:

~~~java
@Override
public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(new ChannelInterceptor() {
        @Override
        public Message<?> preSend(
                Message<?> message, MessageChannel channel) {
            StompHeaderAccessor stomp =
                MessageHeaderAccessor.getAccessor(
                    message, StompHeaderAccessor.class);
            if (stomp != null) {
                System.out.println("STOMP " + stomp.getCommand()
                    + " -> " + stomp.getDestination());
            }
            return message;
        }
    });
}
~~~

Để fragment trên biên dịch, cần import **ChannelRegistration**, **ChannelInterceptor**, **Message**, **MessageChannel**, **MessageHeaderAccessor**, **StompHeaderAccessor**. Đây là ví dụ chẩn đoán **có thể in frame metadata**, không phải cơ chế bảo mật hoàn chỉnh. Chỉ log command/destination cần thiết, không in payload/authorization token. Với kiểm thử production, dùng structured logging/metrics và theo dõi thread pool, size, queue, queue saturation, hủy kết nối và thời gian xử lý.

### Authentication và authorization

Kết nối WebSocket bắt đầu bằng HTTP handshake; principal có thể được kế thừa từ phiên HTTP/authentication ở handshake. STOMP CONNECT, SUBSCRIBE, SEND là các thao tác messaging cần được **phân quyền** riêng. Ví dụ người dùng có quyền đọc **/topic/public** chưa chắc có quyền SEND thẳng vào broker hoặc subscribe **/user/** của người khác.

Trong Spring Security nên có quy tắc ủy quyền message **theo STOMP type và destination**, áp dụng bảo vệ CSRF/origin phù hợp với ứng dụng browser, xác định danh tính trước khi gửi user destination. Không chỉ dựa vào tiền tố **/app** hoặc một trường username trong JSON do client nhập. Một pattern an toàn là giới hạn client SEND đến **/app/** để controller kiểm tra nghiệp vụ; không cho client tự do gửi trực tiếp vào những destination chỉ server được publish.

### Lifecycle và độ tin cậy

- **STOMP heartbeat** phát hiện kết nối im lặng nhưng không thay thế cơ chế phục hồi trạng thái sau reconnect.
- **Connection lost** có thể làm mất subscription của phiên cũ; STOMP client thường phải kết nối và subscribe lại.
- **WebSocket reconnect** không tự replay toàn bộ tin đã phát khi client offline với simple broker.
- Với dữ liệu nghiệp vụ cần không mất, lưu sự kiện/trạng thái vào DB hoặc broker durable, dùng định danh message, acknowledgement và đồng bộ sau reconnect. STOMP over WebSocket tự thân không bảo đảm exactly-once delivery.
- **@MessageExceptionHandler** hỗ trợ ánh xạ lỗi trong controller thành thông điệp; lựa chọn destination lỗi cần rõ ràng. Đừng trả stack trace/bí mật cho client.
- Khi kênh outbound chậm, kiểm soát giới hạn buffer/send timeout, payload size, số subscription và tốc độ phát. Một subscriber chậm có thể gây áp lực tài nguyên.

## 9. Một luồng khác: thông báo từ REST endpoint tới WebSocket

Dòng chat ở mục 4 là **client SEND** → **@MessageMapping**. Nhưng sự kiện trong hệ thống cũng có thể bắt đầu từ HTTP request hoặc scheduled task:

~~~text
HTTP POST /api/announcements
        |
        v
@RestController / service
        |
SimpMessagingTemplate.convertAndSend(
    "/topic/announcements", object)
        |
        v
    brokerChannel
        |
     broker
        |
  clientOutboundChannel
        |
STOMP MESSAGE tới mọi client đã SUBSCRIBE /topic/announcements
~~~

Trong nhánh này **không có STOMP SEND từ client**, nên message do service tạo **không cần đi qua clientInboundChannel**. Đây là ranh giới rất quan trọng khi truy dấu luồng: nhìn vào subscriber nhận được MESSAGE chưa đủ để biết dữ liệu có nguồn từ STOMP controller, REST API, database change event hay scheduler.

## 10. Demo chạy được: chat + announcement với Spring Boot 3, Java 17

Demo chứa **một Spring Boot server** với WebSocket/STOMP endpoint **/ws**, controller nhận **/app/chat.send**, và browser subscribe hai broker destinations **/topic/chat**, **/topic/announcements**. Một HTTP endpoint kích hoạt publish qua **SimpMessagingTemplate**. Với hai tab browser, quan sát được subscription và broadcast trong một JVM.

Cần **JDK 17+**, Maven và Internet để tải dependencies/CDN của STOMP browser client. Demo dùng Spring Boot **3.5.0** để các API tương thích Spring Framework 6.2.x.

Cấu trúc dự án:

~~~text
stomp-channels-demo/
├── pom.xml
└── src/main/
    ├── java/demo/
    │   ├── MessagingDemoApplication.java
    │   ├── WebSocketConfig.java
    │   ├── ChatController.java
    │   └── AnnouncementController.java
    └── resources/static/index.html
~~~

**pom.xml**:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.0</version>
        <relativePath/>
    </parent>
    <groupId>demo</groupId>
    <artifactId>stomp-channels-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <java.version>17</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-websocket</artifactId>
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
~~~

**src/main/java/demo/MessagingDemoApplication.java**:

~~~java
package demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MessagingDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(MessagingDemoApplication.class, args);
    }
}
~~~

**src/main/java/demo/WebSocketConfig.java**:

~~~java
package demo;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic", "/queue");
    }
}
~~~

**src/main/java/demo/ChatController.java**:

~~~java
package demo;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class ChatController {
    public record ChatInput(String from, String text) {}
    public record ChatEvent(String from, String text) {}

    @MessageMapping("/chat.send")
    @SendTo("/topic/chat")
    public ChatEvent send(ChatInput input) {
        return new ChatEvent(input.from(), input.text());
    }
}
~~~

**src/main/java/demo/AnnouncementController.java**:

~~~java
package demo;

import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnnouncementController {
    public record Announcement(String text) {}

    private final SimpMessagingTemplate messaging;

    public AnnouncementController(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @PostMapping("/api/announcements")
    public ResponseEntity<Void> publish(@RequestParam("text") String text) {
        messaging.convertAndSend(
            "/topic/announcements",
            new Announcement(text));
        return ResponseEntity.accepted().build();
    }
}
~~~

**src/main/resources/static/index.html** (vì cùng origin nên WebSocket handshake không đụng lỗi CORS do chạy từ file://):

~~~html
<!doctype html>
<html lang="vi">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>STOMP channels demo</title>
</head>
<body>
  <h1>STOMP channels demo</h1>
  <p id="status">Đang kết nối...</p>
  <button id="send" disabled>Gửi tin chat</button>
  <ol id="feed"></ol>

  <script src="https://cdn.jsdelivr.net/npm/@stomp/stompjs@7/bundles/stomp.umd.min.js"></script>
  <script>
    const status = document.querySelector("#status");
    const feed = document.querySelector("#feed");
    const send = document.querySelector("#send");

    const scheme = location.protocol === "https:" ? "wss" : "ws";
    const client = new StompJs.Client({
      brokerURL: scheme + "://" + location.host + "/ws",
      reconnectDelay: 5000,
      onConnect: () => {
        status.textContent = "STOMP CONNECTED";
        send.disabled = false;

        client.subscribe("/topic/chat", frame => {
          const chat = JSON.parse(frame.body);
          const li = document.createElement("li");
          li.textContent = "Chat " + chat.from + ": " + chat.text;
          feed.append(li);
        });

        client.subscribe("/topic/announcements", frame => {
          const event = JSON.parse(frame.body);
          const li = document.createElement("li");
          li.textContent = "Announcement: " + event.text;
          feed.append(li);
        });
      },
      onWebSocketClose: () => {
        status.textContent = "Mất kết nối, thử nối lại...";
        send.disabled = true;
      },
      onStompError: frame => {
        status.textContent = "STOMP ERROR: " + frame.headers.message;
      }
    });

    send.addEventListener("click", () => {
      client.publish({
        destination: "/app/chat.send",
        body: JSON.stringify({
          from: "An",
          text: "Xin chào qua STOMP"
        }),
        headers: { "content-type": "application/json" }
      });
    });

    client.activate();
  </script>
</body>
</html>
~~~

Ở đây dùng UMD bundle của **@stomp/stompjs** từ CDN để browser có global **StompJs**. Dự án production nên cài npm và bundle client của mình, cấu hình authentication/origin/CSP thích hợp; demo chưa triển khai kiểm tra quyền hoặc validate nội dung chat.

### Chạy và kiểm tra

Tại thư mục **stomp-channels-demo**:

~~~bash
mvn spring-boot:run
~~~

Đợi application khởi động và mở **http://localhost:8080/** trong **hai tab**. Hai tab nên hiển thị **STOMP CONNECTED** khi kết nối thành công.

1. Nhấn **Gửi tin chat** ở tab thứ nhất. Vì cả hai tab đã **SUBSCRIBE /topic/chat**, mỗi tab xuất hiện dòng **Chat An: Xin chào qua STOMP**.
2. Dùng terminal gửi HTTP POST đến REST endpoint:

~~~bash
curl -i -X POST --data-urlencode "text=Hệ thống sẵn sàng" http://localhost:8080/api/announcements
~~~

Response của endpoint là **HTTP 202 Accepted** và không có body. Cả hai tab đang subscribe sẽ thấy **Announcement: Hệ thống sẵn sàng**.

Đây là **kết quả có điều kiện**: nếu browser chưa kết nối/chưa subscribe, server chưa chạy, cổng 8080 đã bị chiếm hoặc CDN không tải được thư viện STOMP, kết quả sẽ khác. Spring Boot log tự sinh phụ thuộc môi trường nên không có stdout cố định cho server. Để chạy nhiều tab an toàn, phải đợi trạng thái CONNECTED trước khi gửi.

### Theo dấu chính xác trong demo

Khi browser gửi chat:

~~~text
Browser A
  publish("/app/chat.send", JSON)
    -> WebSocket /ws
    -> clientInboundChannel
    -> @MessageMapping("/chat.send")
    -> @SendTo("/topic/chat")
    -> brokerChannel
    -> simple broker
    -> clientOutboundChannel
    -> Browser A và B đã subscribe
~~~

Khi gọi HTTP POST announcement:

~~~text
curl -> HTTP @PostMapping("/api/announcements")
     -> SimpMessagingTemplate.convertAndSend("/topic/announcements")
     -> brokerChannel
     -> simple broker
     -> clientOutboundChannel
     -> Browser A và B đã subscribe
~~~

Quan sát rằng nhánh REST **không đi qua clientInboundChannel** và **không gọi @MessageMapping**. Đây chính là khác biệt cần hiểu khi debug event trong ứng dụng dùng cả HTTP và WebSocket.

## 11. Các lỗi thường gặp và cách chẩn đoán

| Triệu chứng | Nguyên nhân khả dĩ | Cách kiểm tra và sửa |
| --- | --- | --- |
| WebSocket kết nối được nhưng controller không chạy | Client SEND thẳng /topic thay vì /app, hoặc mapping sai | Kiểm tra destination của SEND, application prefix, @MessageMapping |
| Client SUBSCRIBE /app/chat.send nhưng không thấy broadcast | /app dành cho handler; subscription broadcast nằm ở broker destination | Subscribe /topic/chat theo @SendTo |
| Client gửi tới /ws như một STOMP destination | Nhầm WebSocket handshake endpoint với destination | Connect tới ws://host/ws, sau đó publish vào /app/... |
| Tin tới controller nhưng không ai nhận | Không có subscription, @SendTo sai đích, không kết nối broker | Xác minh SUBSCRIBE /topic/... và broker prefix |
| Một user nhận nhiều bản sao | Mở nhiều session, subscription trùng hoặc gửi nhiều lần | Kiểm tra session/subscription id, user destination và reconnect |
| Dùng /queue mà không bảo đảm chỉ một consumer | Simple broker không phải durable queue | Sử dụng external broker phù hợp và đúng ack/routing |
| Thứ tự event bị đảo | Executor xử lý đồng thời | Chỉ bật preserveReceiveOrder/preservePublishOrder khi cần; thêm sequence |
| Sau reconnect bị thiếu tin | Simple broker không lưu offline messages | Đồng bộ từ DB, dùng replay/id, durable broker khi yêu cầu |
| WebSocket handshake trả 403 | Origin/CORS/CSRF/auth configuration | Kiểm tra origin cùng host, policy Spring Security, handshake |
| Có CONNECT nhưng SUBSCRIBE/SEND bị cấm | Messaging authorization riêng | Kiểm tra rule theo type/destination |
| JSON không được convert thành DTO | Payload/schema, content-type hoặc message converter | Gửi JSON hợp lệ và content-type thích hợp |
| Broker relay báo mất kết nối | Broker TCP offline, credentials hoặc mạng | Kiểm tra relay host/port, connection, BrokerAvailabilityEvent |
| Browser không thấy gì ở ví dụ | CDN script lỗi, chưa CONNECTED, không subscribe | Xem browser console/Network và trạng thái STOMP client |

### Debug theo thứ tự hợp lý

1. **Handshake**: WebSocket **/ws** có mở và browser nhận trạng thái CONNECTED không?
2. **Frame**: client thực sự gửi STOMP **SUBSCRIBE** hoặc **SEND** với destination gì?
3. **Routing**: prefix **/app** và **/topic** có đúng với cấu hình server không?
4. **Inbound**: message vào **clientInboundChannel** đã được annotated handler/broker nhận chưa?
5. **Business**: phương thức **@MessageMapping** có chạy, converter có decode DTO đúng không?
6. **Broker**: message có đi vào **brokerChannel** và broker có subscription khớp không?
7. **Outbound**: message có tới **clientOutboundChannel**, WebSocket còn mở, subscriber callback có xử lý JSON không?
8. **Vận hành**: có saturation thread pool, outbound buffer, relay offline, timeout, reconnect hay lỗi authorization không?

Nếu cần kiểm tra channel bằng interceptor, bắt đầu bằng **clientInboundChannel** để xem các frame CONNECT/SUBSCRIBE/SEND. Sau đó kiểm tra brokerChannel hoặc clientOutboundChannel theo nhánh đã xác định. Không kết luận chỉ từ console.log bên browser rằng controller đã được gọi.

## 12. Tóm tắt kiến thức trọng tâm và bài tập

- **STOMP** cung cấp command và headers như SEND, SUBSCRIBE, destination trên WebSocket.
- **Destination** là địa chỉ logic trong STOMP frame; **/ws** là HTTP/WebSocket handshake endpoint, thuộc lớp khác.
- **/app** thường định tuyến tới **@MessageMapping**; **/topic** và **/queue** thường dành cho broker; **/user** dùng ánh xạ destination theo người dùng/phiên.
- **clientInboundChannel** nhận message từ WebSocket clients; **brokerChannel** nhận message do ứng dụng server tạo gửi đến broker; **clientOutboundChannel** chuyển message tới WebSocket clients.
- **@SendTo** và **SimpMessagingTemplate** là hai cách gửi từ ứng dụng tới broker. **@SubscribeMapping** mặc định có thể trả trực tiếp tới client qua outbound channel.
- Simple broker quản lý subscription trong bộ nhớ; external STOMP broker relay chuyển việc broadcast đến broker chuyên dụng qua TCP.
- Executor, ordering, authentication, authorization, reconnect và chất lượng giao nhận đều ảnh hưởng hệ thống ngoài luồng happy path.

### Bài tập tự kiểm tra

1. Đổi **@MessageMapping("/chat.send")** thành **@MessageMapping("/rooms/{roomId}")** và dùng **@DestinationVariable String roomId**; gửi phản hồi đúng **/topic/rooms/{roomId}**. Tại sao có thể cần **SimpMessagingTemplate** thay vì @SendTo tĩnh?
2. Bổ sung **@SubscribeMapping("/snapshot")** trả trạng thái hiện tại, cho browser subscribe **/app/snapshot** rồi so với **/topic/chat**: message nào được broadcast?
3. Dùng **@SendToUser("/queue/errors")** để phản hồi validation errors tới đúng phiên. Thảo luận vì sao **username** do client đặt trong JSON không đủ để xác thực người nhận.
4. Thử client gửi vào **/topic/chat** mà không qua **/app/chat.send**; điều gì xảy ra với controller? Trong ứng dụng thật, rule authorization nào nên ngăn việc này?
5. Giải thích vì sao message phát từ REST **/api/announcements** bỏ qua clientInboundChannel nhưng vẫn tới subscriber.
6. Nếu nhiều instance Spring phục vụ người dùng qua load balancer, chọn simple broker hay broker relay? Muốn bảo đảm tin nhắn sau reconnect, cần thêm tầng lưu trữ/đồng bộ gì?

## Tài liệu tham khảo

- [Spring Framework – STOMP Overview](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/overview.html)
- [Spring Framework – Enable STOMP](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/enable.html)
- [Spring Framework – Flow of Messages](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/message-flow.html)
- [Spring Framework – Annotated Controllers](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/handle-annotations.html)
- [Spring Framework – Sending Messages](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/handle-send.html)
- [Spring Framework – Simple Broker](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/handle-simple-broker.html)
- [Spring Framework – External Broker](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/handle-broker-relay.html)
- [Spring Framework – User Destinations](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/user-destination.html)
- [Spring Framework – Order of Messages](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/ordered-messages.html)
- [Spring Framework – Interception](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/interceptors.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="websocket-stomp-008">@MessageMapping, @SendTo, @SendToUser và SimpMessagingTemplate</a>

<details>
<summary>Xem chi tiết</summary>

# @MessageMapping, @SendTo, @SendToUser và SimpMessagingTemplate trong Spring WebSocket/STOMP

## 1. Vai trò của từng thành phần

STOMP over WebSocket cho phép trình duyệt và server trao đổi message qua một kết nối WebSocket. Trong Spring Framework, bốn thành phần thường làm việc cùng nhau:

- `@MessageMapping`: ánh xạ STOMP message đến method của một Spring `@Controller` dựa trên destination; tương tự ý tưởng route HTTP nhưng dành cho messaging.
- `@SendTo`: lấy **giá trị trả về** từ message handler, convert thành message và gửi tới destination do broker quản lý; điển hình là topic cho nhiều subscriber.
- `@SendToUser`: lấy **giá trị trả về** và gửi tới logical user destination của người dùng/session đã gửi request; có tùy chọn chỉ gửi cho session nguồn.
- `SimpMessagingTemplate`: chủ động gửi message từ service, scheduler, listener, HTTP controller hoặc bất kỳ Spring bean nào, không phụ thuộc việc method đang xử lý STOMP message.

Chúng giải quyết các vấn đề khác nhau: **nhận command**, **phát kết quả chung**, **trả riêng**, và **chủ động publish**.

Ví dụ nghiệp vụ:

- Chat: client gửi message, tất cả người trong room nhận cập nhật.
- Form tính giá: tab đang gửi request chỉ nhận kết quả của riêng mình.
- Đơn hàng: sau khi admin thay đổi trạng thái bằng HTTP, server phát thông báo tới client đang subscribe.
- Notification cá nhân: service gửi tới các session WebSocket của một authenticated user.

Đừng nhầm STOMP destination với URL HTTP. `SEND /app/chat` là một STOMP frame; `POST /app/chat` là HTTP request và không tự kích hoạt `@MessageMapping("/chat")`.

## 2. Cấu hình STOMP và đường đi của message

Một cấu hình cơ bản:

~~~java
@Configuration
@EnableWebSocketMessageBroker
class BrokerConfig implements WebSocketMessageBrokerConfigurer {
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setUserDestinationPrefix("/user");
    }
}
~~~

Ý nghĩa của các prefix:

| Destination | Ai xử lý? | Mục đích |
| --- | --- | --- |
| `/ws` | HTTP/WebSocket handshake handler | Client mở kết nối WebSocket và thương lượng STOMP. |
| `/app/chat` | Application controller | STOMP `SEND` được route tới `@MessageMapping("/chat")`. |
| `/topic/chat` | Message broker | Client `SUBSCRIBE`; nhận các message broadcast tới topic. |
| `/queue/updates` | Message broker | Broker destination; tên `queue` không tự bảo đảm durability. |
| `/user/queue/replies` | `UserDestinationMessageHandler` | Client subscribe destination riêng theo user/session. |

`/app` thường được bỏ trước khi so khớp với `@MessageMapping`; trong khi `/topic` và `/queue` chuyển message tới broker. `/user` là prefix để Spring **biến đổi user destination** thành destination nội bộ có gắn session; không thêm `/user` vào `enableSimpleBroker` cùng `/topic`/`/queue`.

Luồng phát broadcast:

~~~text
Browser A                         Spring application                  Browser B
    |                                     |                               |
    | -- SUBSCRIBE /topic/chat ---------> | broker lưu subscription       |
    |                                     | <--- SUBSCRIBE /topic/chat -- |
    | -- SEND /app/chat ----------------> |                               |
    |                          clientInboundChannel                      |
    |                             @MessageMapping("/chat")                |
    |                                @SendTo("/topic/chat")                |
    |                          brokerChannel -> simple broker             |
    | <--- MESSAGE /topic/chat ---------- | -------- MESSAGE -----------> |
~~~

Các kênh quan trọng: `clientInboundChannel` nhận STOMP frame, `brokerChannel` chuyển message từ ứng dụng tới broker, `clientOutboundChannel` phân phối ra các WebSocket sessions. Một message được gửi vào broker chưa đồng nghĩa mọi người dùng đã đọc hoặc xác nhận message.

**Simple broker** phù hợp demo và triển khai nhỏ trên một JVM. Khi cần liên thông nhiều server instances, durable delivery hoặc broker ngoài, cần kiến trúc bổ sung, xem phần cuối.

## 3. @MessageMapping: ánh xạ destination và đọc payload

Ví dụ:

~~~java
@Controller
class ChatController {
    @MessageMapping("/chat")
    public void accept(ChatInput input) {
        // Nhận STOMP SEND /app/chat và xử lý input.
    }
}
~~~

Client STOMP gửi:

~~~text
SEND
destination:/app/chat
content-type:application/json

{"text":"hello"}^@
~~~

`^@` trong đoạn minh họa đại diện byte NUL kết thúc STOMP frame. Thư viện STOMP client sẽ xử lý protocol framing; không cần tự nối ký tự đó vào JSON.

`@Controller` messaging hỗ trợ một số tham số hữu ích:

- `ChatInput input` hoặc `@Payload ChatInput input`: deserialize body bằng `MessageConverter` phù hợp, thường JSON; `@Payload` có thể được suy ra.
- `@DestinationVariable String roomId`: lấy biến từ destination template, ví dụ `@MessageMapping("/rooms/{roomId}/post")`.
- `@Header("simpSessionId") String id`, `MessageHeaders`, `StompHeaderAccessor`: truy cập headers/session metadata nếu có.
- `Principal principal`: danh tính được gắn với handshake WebSocket đã xác thực; có thể `null` nếu kết nối anonymous.
- `@Valid Request request`: kiểm tra Bean Validation nếu dependency/cấu hình hỗ trợ, ví dụ `spring-boot-starter-validation` khi dùng Spring Boot.

Ví dụ room động:

~~~java
@MessageMapping("/rooms/{roomId}/post")
public void post(@DestinationVariable String roomId,
                 ChatInput message,
                 Principal principal) {
    // Kiểm tra principal được quyền tham gia roomId.
    // Sau đó mới gọi service và publish tới room tương ứng.
}
~~~

Client gửi `SEND /app/rooms/42/post`. Destination template được ánh xạ theo path; **roomId do client đưa vào không chứng minh quyền truy cập**. Server vẫn phải kiểm tra membership, tenant và authorization.

`@MessageMapping` cũng có thể đặt ở class level để chia sẻ prefix. Các annotation xử lý STOMP không thay thế `@GetMapping`/`@PostMapping` cho REST.

## 4. @SendTo: phát kết quả cho subscriber của destination

Nếu `@MessageMapping` trả về đối tượng, `@SendTo` xác định nơi Spring chuyển đối tượng đó thành message:

~~~java
@Controller
class ChatHandler {
    @MessageMapping("/chat")
    @SendTo("/topic/chat")
    public ChatEvent handle(ChatInput input) {
        return new ChatEvent("message", input.text());
    }
}
~~~

Giả sử cả hai browser A và B subscribe `/topic/chat`. A gửi `SEND /app/chat` với body `{"text":"hi"}`. Server gọi method, convert return value `ChatEvent`, gửi tới `/topic/chat`; broker phát cho các subscriber, bao gồm A nếu A đã subscribe. **@SendTo không mặc định chỉ gửi cho request sender.**

Có thể gửi đến nhiều destination:

~~~java
@MessageMapping("/metrics")
@SendTo({"/topic/metrics", "/topic/dashboard"})
public MetricUpdated publish(MetricRequest request) {
    return new MetricUpdated(request.name(), request.value());
}
~~~

Làm vậy chỉ khi hai nhóm subscriber thực sự cần cùng dữ liệu; với dữ liệu nhạy cảm, broadcast nhầm destination sẽ làm lộ thông tin.

### Nếu bỏ @SendTo thì sao?

Trong cấu hình phổ biến, Spring có thể suy ra default destination cho giá trị trả về của `@MessageMapping("/hello")` là `/topic/hello`. Tuy vậy nên khai báo `@SendTo` khi muốn contract outbound tường minh, nhất là khi inbound `/app/order/submit` nhưng outbound là `/topic/orders`.

Annotation này phát **return value**. Với method trả `void`, không có object để Spring tự động phát. Muốn chủ động gửi trong method `void`, dùng `SimpMessagingTemplate`.

## 5. @SendToUser: reply theo user hoặc theo đúng session

`@SendToUser` dùng khi response không nên được broadcast tới tất cả subscriber của một public topic:

~~~java
@MessageMapping("/quote")
@SendToUser("/queue/quotes")
public QuoteResult quote(QuoteRequest input, Principal principal) {
    return quotationService.calculate(input, principal);
}
~~~

Client subscribe **`/user/queue/quotes`**, còn annotation khai báo `/queue/quotes`. Spring tự resolve prefix `/user` theo user/session; không viết `@SendToUser("/user/queue/quotes")` khi dùng quy ước cấu hình ở phần 2.

### broadcast=true và broadcast=false

Nếu một authenticated user mở nhiều tab:

| Cách dùng | Phạm vi |
| --- | --- |
| `@SendToUser("/queue/quotes")` | Mặc định `broadcast=true`: gửi tới các session của cùng user đã subscribe. |
| `@SendToUser(value="/queue/replies", broadcast=false)` | Chỉ session gửi STOMP message đang được xử lý. |
| `@SendToUser(value="/queue/errors", broadcast=false)` trên error handler | Gửi lỗi riêng cho tab gây lỗi, không báo lỗi sang các tab khác. |

Ví dụ request–reply tại tab nguồn:

~~~java
@MessageMapping("/calculate")
@SendToUser(value = "/queue/replies", broadcast = false)
public CalculationResult calculate(CalculationCommand command) {
    return calculator.execute(command);
}
~~~

Client gửi `SEND /app/calculate` sau khi subscribe `/user/queue/replies`. Nếu user alice có tab A và B cùng kết nối, A gửi command thì chỉ A nhận reply của method này.

**Anonymous sessions:** Spring vẫn có thể hỗ trợ user destination trong session chưa có authenticated principal. Khi đó `@SendToUser` vận hành như `broadcast=false`, chỉ hướng đến session đã gửi message; không có khả năng tự tìm “những tab khác của cùng user” khi hệ thống chưa xác định được user.

### Spring làm gì với /user?

~~~text
Alice, session S1: SUBSCRIBE /user/queue/inbox
    -> Spring resolve thành destination nội bộ duy nhất cho S1

Alice, session S2: SUBSCRIBE /user/queue/inbox
    -> destination nội bộ riêng cho S2

Bob, session S3: SUBSCRIBE /user/queue/inbox
    -> destination nội bộ riêng cho S3

Server chủ động gửi cho username "alice":
    -> Spring có thể resolve tới S1 và S2 nếu đang kết nối/subscribe.
~~~

Định dạng destination nội bộ được Spring quản lý; không phụ thuộc vào hậu tố session cụ thể khi viết code nghiệp vụ. User destination là **routing convenience**, không tự cung cấp xác thực, quyền truy cập, offline message storage hoặc encryption ngoài transport.

### @MessageExceptionHandler phối hợp @SendToUser

~~~java
@MessageExceptionHandler(IllegalArgumentException.class)
@SendToUser(value = "/queue/errors", broadcast = false)
public ErrorEvent invalidCommand(IllegalArgumentException ex) {
    return new ErrorEvent("INVALID_COMMAND", ex.getMessage());
}
~~~

Spring Messaging gọi `@MessageExceptionHandler` khi controller STOMP phát sinh lỗi phù hợp. Client subscribe `/user/queue/errors` để nhận response. Đây là cơ chế của messaging controller, khác `@ExceptionHandler` cho HTTP request. Tránh trả stack trace hoặc thông tin hệ thống nhạy cảm trong error payload.

## 6. SimpMessagingTemplate: phát chủ động từ service và HTTP

`SimpMessagingTemplate` cho phép bất kỳ Spring bean nào gửi message vào hệ thống broker mà không cần một inbound STOMP frame.

### Public broadcast

~~~java
@Service
class StatusPublisher {
    private final SimpMessagingTemplate template;

    StatusPublisher(SimpMessagingTemplate template) {
        this.template = template;
    }

    public void publish(StatusChanged event) {
        template.convertAndSend("/topic/status", event);
    }
}
~~~

`convertAndSend` dùng configured message converter để tạo payload, rồi gửi vào messaging channel. Nó không bảo đảm message đã hiển thị trên UI hoặc đã được lưu bền vững sau khi lời gọi trả về.

Một HTTP endpoint cũng có thể phát sự kiện:

~~~java
@RestController
class UpdateApi {
    private final SimpMessagingTemplate template;

    UpdateApi(SimpMessagingTemplate template) {
        this.template = template;
    }

    @PostMapping("/api/announce")
    ResponseEntity<Void> announce(@RequestBody Announcement request) {
        template.convertAndSend("/topic/announcements", request);
        return ResponseEntity.accepted().build();
    }
}
~~~

`HTTP 202` là kết quả xử lý phía HTTP, không phải xác nhận mọi WebSocket client đã nhận message. Khi nghiệp vụ cần audit/ack/retry đáng tin cậy, thiết kế thêm cơ chế khác.

### Gửi một user từ backend

~~~java
@Service
class InboxService {
    private final SimpMessagingTemplate template;

    InboxService(SimpMessagingTemplate template) {
        this.template = template;
    }

    public void notifyAuthenticatedUser(
            String authenticatedUsername, InboxEvent event) {
        template.convertAndSendToUser(
                authenticatedUsername, "/queue/inbox", event);
    }
}
~~~

Client của authenticated user subscribe `/user/queue/inbox`. `convertAndSendToUser("alice","/queue/inbox", event)` biểu đạt logic tương đương phát đến user destination `/user/alice/queue/inbox` theo prefix mặc định, nhưng API rõ ràng và đỡ nối chuỗi sai.

**Không lấy authenticatedUsername từ một STOMP payload do client tự khai báo rồi gửi dữ liệu riêng tư tới user đó mà chưa xác minh quyền.** Danh tính phải từ `Principal` hoặc nguồn server-side đáng tin, và vẫn phải kiểm tra quyền nghiệp vụ của sender với recipient/resource.

Có thể có nhiều session của cùng user; gửi theo username thông thường nhắm đến các session của user đang subscribe, không nhất thiết đúng một tab. Nếu phải nhắm một session cụ thể ngoài message handler, cần định tuyến dựa trên session ID/header đúng cách và kiểm tra kỹ cơ chế user destination.

### Sau database commit

Nếu phát notification trước khi transaction lưu dữ liệu commit mà transaction bị rollback, client nhận một sự kiện “ảo”. Có thể tách sự kiện sau commit:

~~~java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
void onOrderChanged(OrderChanged event) {
    template.convertAndSend("/topic/orders", event);
}
~~~

Đoạn trên chỉ minh họa cách gắn listener với transaction đang tồn tại; cần listener bean, event publisher và transaction infrastructure. Phát sau commit **không bảo đảm durable delivery** trong trường hợp process sập sau commit nhưng trước lúc gửi; hệ thống đòi hỏi độ tin cậy cao nên xét transactional outbox/event log.

## 7. So sánh API và chọn đúng cách

| Mục tiêu | Lựa chọn | Lưu ý |
| --- | --- | --- |
| Nhận `SEND /app/...` | `@MessageMapping` | Handler nhận và xử lý input; chưa tự broadcast nếu không trả giá trị hoặc gửi message. |
| Return value đến mọi người subscribe một topic | `@SendTo("/topic/...")` | Ai subscribe hợp lệ cũng có thể nhận. |
| Return value đến các session của người gửi | `@SendToUser("/queue/...")` | Theo authenticated user; mặc định broadcast nhiều session. |
| Return value đến đúng session nguồn | `@SendToUser(value="/queue/...",broadcast=false)` | Hữu ích cho reply và error riêng của một tab. |
| Chủ động phát từ business service/HTTP/scheduler | `template.convertAndSend(destination,payload)` | Không cần inbound STOMP message. |
| Gửi thông báo cho user có username đã xác thực | `template.convertAndSendToUser(username,"/queue/...",payload)` | Client subscribe `/user/queue/...`; cần auth và user registry phù hợp. |

Chọn theo **ai khởi tạo sự kiện** và **người nào phải nhận**, không theo kích thước JSON hay tên annotation trông ngắn hơn.

## 8. Demo hoàn chỉnh với Java 17 và Spring Boot 3.x

Demo chạy Spring MVC + STOMP simple broker trên một instance. Ba chức năng:

1. `SEND /app/hello` → `@MessageMapping` → `@SendTo("/topic/news")`: broadcast mọi tab đã subscribe.
2. `SEND /app/echo` → `@SendToUser(..., broadcast=false)`: chỉ tab nguồn nhận.
3. `POST /api/announcements` → `SimpMessagingTemplate.convertAndSend(...)`: broadcast từ HTTP controller.

Để demo tự chạy dễ nhất, chưa thêm Spring Security; `@SendToUser(broadcast=false)` vẫn dùng được theo **session anonymous**. Ví dụ `convertAndSendToUser(username,...)` cho authenticated user đã có riêng ở phần 6.

### 8.1. pom.xml

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.0</version>
        <relativePath/>
    </parent>
    <groupId>demo</groupId>
    <artifactId>stomp-routing</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <properties>
        <java.version>17</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-websocket</artifactId>
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
~~~

### 8.2. src/main/java/demo/StompRoutingApplication.java

Toàn bộ code Java sau lưu trong **một file**. Chỉ class main là `public`; các class/record khác package-private là hợp lệ trong Java 17.

~~~java
package demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@SpringBootApplication
public class StompRoutingApplication {
    public static void main(String[] args) {
        SpringApplication.run(StompRoutingApplication.class, args);
    }
}

record Input(String text) {}
record Event(String type, String text) {}

@Configuration
@EnableWebSocketMessageBroker
class BrokerConfig implements WebSocketMessageBrokerConfigurer {
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setUserDestinationPrefix("/user");
    }
}

@Controller
class CommandController {
    @MessageMapping("/hello")
    @SendTo("/topic/news")
    Event hello(Input input) {
        return new Event("hello", checked(input));
    }

    @MessageMapping("/echo")
    @SendToUser(value = "/queue/replies", broadcast = false)
    Event echo(Input input) {
        return new Event("reply", "Echo: " + checked(input));
    }

    @MessageExceptionHandler(IllegalArgumentException.class)
    @SendToUser(value = "/queue/errors", broadcast = false)
    Event invalid(IllegalArgumentException error) {
        return new Event("error", error.getMessage());
    }

    static String checked(Input input) {
        if (input == null || input.text() == null
                || input.text().isBlank() || input.text().length() > 500) {
            throw new IllegalArgumentException(
                    "text must contain 1..500 characters");
        }
        return input.text().trim();
    }
}

@RestController
class AnnouncementController {
    private final SimpMessagingTemplate template;

    AnnouncementController(SimpMessagingTemplate template) {
        this.template = template;
    }

    @PostMapping("/api/announcements")
    ResponseEntity<Void> announce(@RequestBody Input input) {
        if (input == null || input.text() == null
                || input.text().isBlank() || input.text().length() > 500) {
            return ResponseEntity.badRequest().build();
        }

        template.convertAndSend("/topic/news",
                new Event("announcement", input.text().trim()));
        return ResponseEntity.accepted().build();
    }
}
~~~

Trong demo:

- `@SendTo("/topic/news")` phát return value, ngay cả khi method không trực tiếp thao tác template.
- `@SendToUser(... broadcast=false)` chỉ trả lời session đã gửi lệnh `/app/echo`.
- `@MessageExceptionHandler` có thể gửi lỗi riêng vào `/user/queue/errors` khi handler ném `IllegalArgumentException`.
- `@PostMapping` là HTTP endpoint, gọi template để phát một message mà không cần inbound STOMP command.
- `Event` là record Java, Jackson sẽ serialize thành JSON thông qua converters do Spring Boot cung cấp.
- `ResponseEntity.accepted()` ở HTTP chỉ xác nhận server xử lý yêu cầu phát tin; không xác nhận các browser đã nhận.

### 8.3. src/main/resources/static/index.html

HTML sau dùng UMD của `@stomp/stompjs`. Trình duyệt cần Internet để tải script CDN; hoặc tải thư viện bằng npm và phục vụ bản build nội bộ nếu muốn chạy offline.

~~~html
<!doctype html>
<html lang="vi">
<head>
    <meta charset="utf-8">
    <title>Spring STOMP demo</title>
</head>
<body>
    <h1>STOMP demo</h1>
    <input id="text" value="Xin chào">
    <button id="hello">Broadcast</button>
    <button id="echo">Reply to this tab</button>
    <button id="announce">Publish from HTTP</button>
    <pre id="log"></pre>

    <script src="https://cdn.jsdelivr.net/npm/@stomp/stompjs@7.2.0/bundles/stomp.umd.min.js"></script>
    <script>
        const input = document.getElementById("text");
        const logPanel = document.getElementById("log");
        const log = value => {
            logPanel.textContent += value + "\n";
        };

        const protocol = location.protocol === "https:" ? "wss://" : "ws://";
        const client = new StompJs.Client({
            brokerURL: protocol + location.host + "/ws",
            reconnectDelay: 3000,
            onConnect: () => {
                log("CONNECTED");
                client.subscribe("/topic/news", message => {
                    log("TOPIC: " + message.body);
                });
                client.subscribe("/user/queue/replies", message => {
                    log("REPLY: " + message.body);
                });
                client.subscribe("/user/queue/errors", message => {
                    log("ERROR: " + message.body);
                });
            },
            onStompError: frame => {
                log("STOMP ERROR: " + (frame.headers.message || "unknown"));
            }
        });
        client.activate();

        function send(destination) {
            if (!client.connected) {
                log("STOMP not connected yet");
                return;
            }
            client.publish({
                destination: destination,
                body: JSON.stringify({ text: input.value })
            });
        }

        document.getElementById("hello").onclick = () => {
            send("/app/hello");
        };
        document.getElementById("echo").onclick = () => {
            send("/app/echo");
        };
        document.getElementById("announce").onclick = async () => {
            try {
                const response = await fetch("/api/announcements", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ text: input.value })
                });
                log("HTTP status: " + response.status);
            } catch (error) {
                log("HTTP request failed: " + error);
            }
        };

        // Khi rời màn hình: await client.deactivate();
    </script>
</body>
</html>
~~~

Code thêm mọi subscription trong `onConnect`: khi STOMP client reconnect, callback lại chạy và subscribe trên connection mới. Dùng `textContent` giúp không render nội dung không tin cậy dưới dạng HTML.

### 8.4. Khởi chạy và kết quả

Dùng JDK 17+ và Maven:

~~~bash
mvn spring-boot:run
~~~

Mở `http://localhost:8080/`, chờ trạng thái `CONNECTED` rồi thử nút:

1. **Broadcast:** gửi `/app/hello`, log `TOPIC: {"type":"hello","text":"Xin chào"}`. Mở thêm tab đã subscribe và gửi lại, cả hai tab nhận broadcast.
2. **Reply to this tab:** gửi `/app/echo`, log chỉ trên tab gửi là `REPLY: {"type":"reply","text":"Echo: Xin chào"}`. Tab kia không nhận reply này.
3. **Publish from HTTP:** browser POST `/api/announcements`, nhận `HTTP status: 202`, đồng thời các tab subscribe topic nhận message với `"type":"announcement"`.
4. **Lỗi:** xóa input rồi nhấn **Reply to this tab**. Handler từ chối text rỗng, session nguồn nhận message ở `/user/queue/errors` khi error handler được gọi.

JSON ví dụ thể hiện các trường chính; việc thứ tự field khi serialize không phải contract của transport.

Có thể gọi HTTP trực tiếp bằng một lệnh (dành cho terminal hỗ trợ curl CLI):

~~~bash
curl -i -X POST http://localhost:8080/api/announcements -H "Content-Type: application/json" -d '{"text":"Update from API"}'
~~~

HTTP response có status 202, còn message broadcast được đẩy tới các subscription WebSocket. Với Windows PowerShell, `curl` có thể là alias; dùng `curl.exe` và cách quote JSON phù hợp với shell.

**Chú ý thứ tự:** simple broker không lưu lại event gửi trước khi subscription bắt đầu. Hãy chờ STOMP connect và subscribe hoạt động trước khi test. Ví dụ này minh họa mapping và routing, không phải đảm bảo delivery/ack hoặc benchmark.

## 9. Các lỗi thường gặp

### 9.1. Gửi sai destination

- Client `SEND /topic/chat` rồi chờ method `@MessageMapping("/chat")` chạy: sai nếu app prefix là `/app`.
- Client phải `SEND /app/chat`; server `@SendTo("/topic/chat")`; subscriber nghe `/topic/chat`.
- Với user reply, client nghe `/user/queue/replies`, server `@SendToUser("/queue/replies")`.

### 9.2. Dùng @SendTo để gửi dữ liệu riêng tư

`@SendTo` phát đến broker destination; nếu người không đủ quyền có thể subscribe topic, họ có thể nhận dữ liệu. Chọn user destination và áp dụng Spring Security message authorization/kiểm tra quyền nghiệp vụ trước khi gửi.

### 9.3. Nhầm username với session

`@SendToUser(broadcast=false)` nhắm session đã gọi handler; `convertAndSendToUser(username, ...)` nhắm user theo registry và có thể fan-out nhiều session. Nếu đòi hỏi đúng một tab, phải xác định session rõ ràng.

### 9.4. Nghĩ dùng tên /queue là có durable queue

Simple broker không cung cấp persistence, clustering, offline queue, replay hay đảm bảo exactly once. Với độ tin cậy cao cần broker ngoài và/hoặc lưu nghiệp vụ bằng database/outbox.

### 9.5. Tưởng template gửi thành công nghĩa client đã đọc

`convertAndSend` đi vào hệ thống message channel; không phải browser acknowledgement. Với nghiệp vụ quan trọng cần ứng dụng tự gắn message ID, theo dõi trạng thái, thiết kế retry/deduplication và xử lý offline.

### 9.6. Handler xử lý blocking quá lâu

STOMP message handlers chạy thông qua inbound channels/executors. Gọi I/O hoặc DB nặng trên chúng mà không giới hạn có thể gây queue và tăng latency. Đo độ trễ handler, inbound/outbound queues và memory; điều chỉnh threading và backpressure theo tải thực tế.

### 9.7. Không subscribe lại khi reconnect

WebSocket/STOMP session mới không kế thừa mọi subscription từ connection cũ. StompJS thường cần subscribe trong `onConnect` và thực hiện đồng bộ snapshot/missed events sau reconnect.

## 10. Bảo mật, scale và production

**Authentication/authorization:** Mô hình browser thường kế thừa authenticated user từ HTTP/WebSocket handshake. Không tự tin header `login`/`passcode` trong STOMP `CONNECT` để xác minh danh tính nếu chưa cấu hình cơ chế nhận token riêng. Phải kiểm tra quyền `SEND`/`SUBSCRIBE` theo destination, giới hạn topic, kiểm tra từng room/order/tenant trong business layer, rate limit và payload validation. Nếu dùng Spring Security, xem cấu hình message authorization và CSRF/origin cho WebSocket.

**SimpMessagingTemplate và transaction:** Đối với sự kiện DB, nên phát sau commit hoặc thông qua outbox để tránh event sai nếu transaction rollback. Sự kiện quan trọng cần mô hình persisted state và retry, không chỉ dựa trên socket đang mở.

**Nhiều instance:** `enableSimpleBroker` lưu subscription nội bộ một JVM. Nếu có replica A và B, việc gửi ở A không mặc nhiên đến user đang kết nối ở B. Dùng STOMP broker relay với broker phù hợp hoặc message backplane và user-registry phối hợp; thiết kế scale phụ thuộc tải và durability mong muốn.

**Định tuyến user:** Trong hệ thống nhiều node, việc tìm user/session có thể cần hỗ trợ user destination broadcast hoặc shared user registry; xác định rõ behavior khi user chưa online và khi cùng một user có nhiều sessions.

**Giới hạn tài nguyên:** Theo dõi active WebSocket sessions, message sizes, send queue, latency, reconnect count, lỗi gửi và slow clients. Heartbeat, timeouts, giới hạn outbound buffer, backoff và dọn session là yêu cầu vận hành quan trọng.

## 11. Checklist trước khi tích hợp

- [ ] `/ws` là endpoint handshake, `/app` là prefix controller, `/topic`/`/queue` dành cho broker, `/user` được Spring resolve riêng.
- [ ] Client gửi STOMP `SEND` bằng thư viện tương thích STOMP; không gửi JSON raw tới STOMP handler.
- [ ] `@MessageMapping` có đúng destination và converter/DTO/validation.
- [ ] `@SendTo` chỉ phát dữ liệu phù hợp với tất cả subscribers của topic.
- [ ] `@SendToUser` chọn đúng `broadcast`: cùng user nhiều session hay đúng session đã gửi.
- [ ] `SimpMessagingTemplate` được inject ở service cho thông báo phát sinh từ HTTP, scheduler hoặc nghiệp vụ khác.
- [ ] Auth, authorization theo user/room, input validation, rate limiting và logging an toàn được triển khai.
- [ ] Có chiến lược reconnect, resubscribe, snapshot/replay nếu cần, offline notification và delivery semantics.
- [ ] Đã load test với số lượng session/message thật và topology nhiều replica nếu dùng.

**Quy tắc ghi nhớ:** `@MessageMapping` **nhận**; `@SendTo` **phát cho subscriber**; `@SendToUser` **trả về user/session**; `SimpMessagingTemplate` **chủ động gửi từ ứng dụng**. Những lựa chọn này xác định đường đi của message; độ tin cậy, quyền và bảo mật là các lớp cần thiết kế tiếp.

## 12. Nguồn tham khảo chính thức

- Spring Framework — Annotated Controllers: https://docs.spring.io/spring-framework/reference/web/websocket/stomp/handle-annotations.html
- Spring Framework — User Destinations: https://docs.spring.io/spring-framework/reference/web/websocket/stomp/user-destination.html
- Spring Framework — Sending Messages: https://docs.spring.io/spring-framework/reference/web/websocket/stomp/handle-send.html
- Spring Framework — Enable STOMP: https://docs.spring.io/spring-framework/reference/web/websocket/stomp/enable.html
- Spring Framework — Message Flow: https://docs.spring.io/spring-framework/reference/web/websocket/stomp/message-flow.html
- Spring Framework — Authorization: https://docs.spring.io/spring-framework/reference/web/websocket/stomp/authorization.html
- StompJS — Browser Client & UMD Bundle: https://stomp-js.github.io/guide/stompjs/using-stompjs-v5.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="websocket-stomp-009">Simple broker, broker relay và lựa chọn khi mở rộng hệ thống</a>

<details>
<summary>Xem chi tiết</summary>

# Simple broker, broker relay và lựa chọn khi mở rộng hệ thống

## 1. Vấn đề mà STOMP broker giải quyết

WebSocket mở kênh giao tiếp hai chiều, duy trì lâu dài giữa trình duyệt/client và Spring server. STOMP bổ sung giao thức messaging trên kênh đó: client gửi `CONNECT`, `SUBSCRIBE`, `SEND`, `DISCONNECT`; server hoặc broker phát `MESSAGE` đến các phiên đã subscribe. Khi một client subscribe `/topic/news`, hệ thống cần biết **ai đang subscribe, message phải đi đâu và khi nào được gửi**.

Spring Framework hỗ trợ hai cách triển khai broker:

- **Simple broker**: bộ xử lý subscription nhẹ, chạy **trong JVM Spring**, lưu subscription trong RAM, gửi message đến các WebSocket sessions phù hợp trên instance đó. Bật bằng `enableSimpleBroker(...)`.
- **STOMP broker relay**: Spring chuyển tiếp STOMP traffic đến **message broker bên ngoài hỗ trợ STOMP/TCP** như RabbitMQ (bật STOMP plugin) hoặc ActiveMQ. Bật bằng `enableStompBrokerRelay(...)`. Broker bên ngoài xử lý subscriptions, routing và tính năng messaging của chính nó.

Sự lựa chọn quan trọng khi ứng dụng mở rộng từ một Spring process lên nhiều instances/pods. **Thêm pod với simple broker không tự tạo mạng phân phối messages chung**. Relay giúp nhiều Spring nodes truy cập chung một broker, nhưng không tự mang lại durable delivery, exactly-once hay authorization đúng cho từng người dùng.

Bài học đi từ sơ đồ luồng Spring messaging, cấu hình Java, semantics routing/ACK, mô hình scale, user destinations, connection/heartbeat, fault recovery, security đến ví dụ **Java 17+ + Spring Boot 3.3.x/Spring Framework 6.1.x**. Các code fragment đều được phân biệt với chương trình chạy đầy đủ.

## 2. Sơ đồ luồng: endpoint, application destination và broker destination

Đầu tiên cần phân biệt **URL mở WebSocket** với **STOMP destination**:

| Giá trị | Vai trò | Ai xử lý? |
| --- | --- | --- |
| `/ws` | HTTP handshake / WebSocket endpoint | Servlet container và Spring WebSocket |
| `/app/chat` | Client gửi STOMP `SEND` cho nghiệp vụ | `@MessageMapping("/chat")` sau khi Spring bỏ prefix `/app` |
| `/topic/chat` | Destination dùng để subscribe/broadcast | Broker (simple hoặc external) |
| `/queue/notices` | Destination có tiền tố `/queue` | Simple broker coi là chuỗi routing; external broker tùy policy |
| `/user/queue/notices` | Destination logic dành cho user/session | Spring `UserDestinationMessageHandler` dịch thành destination cụ thể |

Luồng thường gặp:

```text
Client A                        Spring                            Client B
   │                               │                                  │
   ├─ SEND /app/chat ─────────────►│ clientInboundChannel             │
   │                               │ @MessageMapping("/chat")         │
   │                               │ @SendTo("/topic/chat")            │
   │                               ▼                                  │
   │                         brokerChannel                            │
   │                               │                                  │
   │                ┌──────────────┴──────────────┐                   │
   │                ▼                             ▼                   │
   │          SimpleBroker                STOMP Broker Relay           │
   │          (local RAM)                 → External Broker            │
   │                └──────────────┬──────────────┘                   │
   │                               │                                  │
   │                         clientOutboundChannel                    │
   │                               │                                  │
   │◄──── MESSAGE /topic/chat ─────┼───── MESSAGE /topic/chat ───────►│
```

Sơ đồ chứa **hai lựa chọn broker thay thế nhau**. Một cấu hình Spring cho cùng broker destinations thường chỉ bật **một** broker handler. `clientInboundChannel` nhận messages từ WebSocket; `brokerChannel` nhận messages do application code publish qua `SimpMessagingTemplate` hoặc kết quả của controller; `clientOutboundChannel` đưa broker messages ra WebSocket sessions.

**Không nhầm:** `SimpMessagingTemplate.convertAndSend("/topic/chat", value)` publish tới destination. Lời gọi đó **không đồng nghĩa** client đã nhận/đã xử lý, và nó không phải thao tác gọi HTTP endpoint. Tương tự, HTTP status 101 chỉ xác nhận WebSocket Upgrade; client còn cần STOMP CONNECT và SUBSCRIBE mới có subscription.

## 3. Simple broker: cách hoạt động, giới hạn và heartbeat

### 3.1 Cơ chế trong một instance

Với `registry.enableSimpleBroker("/topic", "/queue")`, Spring tạo bộ nhớ quản lý subscriptions từ các STOMP sessions. Khi A và B subscribe `/topic/news`, rồi server publish message vào destination đó, simple broker phát tới hai sessions. Client C không subscribe sẽ không nhận. Khi client offline, bộ nhớ simple broker **không lưu backlog** để giao sau này.

```text
Spring instance A
 ├─ WebSocket session user1 → SUB /topic/news
 ├─ WebSocket session user2 → SUB /topic/news
 ├─ WebSocket session user3 → SUB /topic/alerts
 └─ SimpleBroker (subscription registry trong RAM)
       │
       ├─ publish /topic/news   → user1, user2
       └─ publish /topic/alerts → user3
```

Các thuộc tính cốt lõi:

1. **Nhanh triển khai**: phù hợp bài học, demo, ứng dụng một instance và thông báo realtime có thể refresh/resync.
2. **Không có persistent message store**: restart JVM mất WebSocket sessions/subscriptions; client reconnect và subscribe lại.
3. **Không phải message broker đầy đủ**: hỗ trợ tập con của STOMP, không cung cấp ACK/RECEIPT semantics và các tính năng cao cấp như full broker. Không dựa vào `ack:client-individual` với simple broker để bảo đảm redelivery.
4. **Subscription match trong instance hiện tại**: không tự chia sẻ subscription registry với các instance Spring khác.
5. **Tiền tố chỉ là quy ước**: `/topic` và `/queue` không tự áp đặt durable topic hay competing-consumer queue. Nếu hai client cùng subscribe `/queue/jobs` dưới simple broker, không nên mặc định chỉ một client nhận job.
6. **Heartbeat có thể cấu hình** với một `TaskScheduler`. STOMP heartbeats khác WebSocket Ping/Pong và TCP keepalive.

Đoạn cấu hình nền:

```java
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
class SocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("http://localhost:*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        registry.enableSimpleBroker("/topic", "/queue");
    }
}
```

Đây là **Spring configuration fragment**, không phải chương trình Java tự chạy. `/ws` là URL handshake, còn `/app` và `/topic` là destination prefixes. Origin localhost chỉ phục vụ ví dụ local; production cần HTTPS/WSS, allowlist origins và authorization phù hợp.

### 3.2 Thiết lập heartbeat cho simple broker

Simple broker cần scheduler nếu bật STOMP heartbeat. Trong một `WebSocketMessageBrokerConfigurer` có thể thêm:

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;

// Các thành viên trong class cấu hình (fragment):
private TaskScheduler messageBrokerTaskScheduler;

@Autowired
void setMessageBrokerTaskScheduler(@Lazy TaskScheduler scheduler) {
    this.messageBrokerTaskScheduler = scheduler;
}

@Override
public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.setApplicationDestinationPrefixes("/app");
    registry.enableSimpleBroker("/topic", "/queue")
            .setTaskScheduler(messageBrokerTaskScheduler)
            .setHeartbeatValue(new long[] {10_000, 10_000});
}
```

Phải **thay thế** phương thức `configureMessageBroker` cũ, không dán thêm phương thức override trùng tên. Spring WebSocket configuration có thể tạo scheduler phù hợp; `@Lazy` giúp tránh dependency cycle khi configurer inject scheduler. Mảng heartbeat tính bằng mili-giây; client và broker vẫn phải thương lượng header `heart-beat` khi STOMP CONNECT/CONNECTED. Heartbeat chỉ phát hiện sự sống của kết nối, không thêm khả năng clustering/persistence.

## 4. Điều gì xảy ra khi scale simple broker lên nhiều pods?

Giả sử có hai Spring instances A và B phía sau load balancer:

```text
                     Load balancer
                    /             \
Client X ──WebSocket─► Spring A   Spring B ◄─WebSocket── Client Y
                         │           │
                   SimpleBroker A  SimpleBroker B
                   SUB /topic/live SUB /topic/live
                         │           │
REST request → Spring A   │           │
         publish /topic/live           │
                         ▼           (không nhận)
                      Client X
```

Hai simple brokers **không chia sẻ RAM**. REST call đi đến A có thể publish đến những sessions đang subscribe trên A; Y nằm ở B sẽ không tự nhận cùng message. Một WebSocket connection được gắn với một instance trong suốt vòng đời; **sticky sessions không biến hai simple brokers thành một broker cluster**.

Hậu quả vận hành:

- Scale từ một lên nhiều replicas có thể làm notification đến không đầy đủ nếu nghiệp vụ cần fan-out across nodes.
- Một user mở hai thiết bị có thể được load balancing đến hai nodes; gửi tới user đó từ một node cần giải quyết user destination giữa nodes.
- Rolling restart làm WebSocket connections của node cũ đóng; client phải tự reconnect và subscribe lại, kể cả khi có external broker.
- Nếu thêm một event bus tự viết để synchronize nodes, phải xét duplicate, ordering, retries, security và loop suppression. Đó là kiến trúc riêng, không phải hành vi mặc định của simple broker.

**Khi nào simple broker vẫn hợp lý?** Khi chỉ một Spring node hoặc các node được partition có chủ đích và không cần gửi qua nhau, khi business tolerates best-effort notifications và client có đường tải lại trạng thái nguồn từ REST/database.

## 5. STOMP broker relay: các kết nối và vai trò của external broker

`registry.enableStompBrokerRelay("/topic", "/queue")` cấu hình Spring chuyển tiếp message đến một broker STOMP/TCP bên ngoài. Browser/client vẫn kết nối tới WebSocket endpoint của Spring; Spring tiếp tục quản lý handshake, security, STOMP frame decoding và `@MessageMapping`. Broker ngoài chịu trách nhiệm phân phối messages giữa các publishers/subscribers.

```text
WebSocket client 1 ─► Spring A ── STOMP/TCP relay client connection ─┐
                                                                   │
WebSocket client 2 ─► Spring B ── STOMP/TCP relay client connection ─┤
                                                                   ▼
                                                       External STOMP Broker
                                               RabbitMQ STOMP / ActiveMQ / ...
                                                                   ▲
REST publisher ───────► Spring A ── STOMP/TCP system connection ────┘
                                                                   │
             Broker gửi MESSAGE về relay TCP session phù hợp ───────┘
                        → Spring A / Spring B → WebSocket clients
```

### 5.1 Hai kiểu TCP connection

| Kết nối | Số lượng điển hình | Công dụng |
| --- | --- | --- |
| **Client relay connection** | Một TCP connection **cho mỗi** WebSocket/STOMP client session | Truyền `CONNECT`, `SUBSCRIBE`, `SEND` và nhận broker response cho session đó |
| **System connection** | Một connection dùng chung **mỗi Spring relay instance** | App gửi những messages không gắn với client cụ thể, ví dụ phát từ REST/cron/service code |

Theo tài liệu Spring, **system connection chỉ dùng để gửi message bắt nguồn từ ứng dụng**, không phải một channel nhận broadcast cho mọi user. Do đó, 10.000 WebSocket sessions có thể tạo số lượng TCP connections lớn tới broker. Bạn phải đánh giá giới hạn kết nối, file descriptors, memory, heartbeats và network trên broker.

### 5.2 Cấu hình relay, credentials và virtual host

```java
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
class RelayConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins("https://app.example.org");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");

        registry.enableStompBrokerRelay("/topic", "/queue")
                .setRelayHost("broker.internal")
                .setRelayPort(61613)
                .setVirtualHost("realtime")
                .setSystemLogin("spring-system")
                .setSystemPasscode("set-through-secret-manager")
                .setClientLogin("spring-clients")
                .setClientPasscode("set-through-secret-manager");
    }
}
```

Đây là **fragment cấu hình tham khảo**, không dùng credentials literal trong production. Chúng phải đến từ secret manager, environment/config và phải thuộc accounts có quyền hạn tối thiểu. Broker cần mở đúng **STOMP TCP listener**; ví dụ cổng `61613` là port phổ biến cho STOMP, không phải mặc định của tất cả broker. Port AMQP RabbitMQ `5672` không có nghĩa là port STOMP.

Nếu broker đặt sau TLS, phải cấu hình transport từ **Spring tới broker** cho TLS; `wss://` chỉ mã hóa **browser ↔ Spring**, không tự mã hóa relay TCP.

**Credentials khác nhau ở hai tầng:**

- End user được xác thực ở Spring khi WebSocket handshake/STOMP CONNECT và bị kiểm soát quyền `SEND`/`SUBSCRIBE`.
- STOMP relay thay `login`/`passcode` gửi tới broker bằng các giá trị `clientLogin`/`clientPasscode`, hoặc `systemLogin`/`systemPasscode` với system connection. Broker username **không tự phản ánh user principal thật**.
- Broker xác thực service accounts và thực thi policy broker resources. Cần tiếp tục authorization theo STOMP destinations phía Spring.

Cách cấu hình tính năng có thể thay đổi giữa phiên bản Spring; hãy dùng javadoc đúng BOM đang chạy. Khi chuyển từ simple sang relay, **thay** `enableSimpleBroker` bằng `enableStompBrokerRelay` cho cùng các destination prefixes, tránh chạy hai broker handlers trùng vùng định tuyến.

### 5.3 Kết nối broker bị ngắt

Relay theo dõi system connection và có cơ chế reconnect. Spring phát `BrokerAvailabilityEvent` khi broker có thể/không thể nhận message từ góc nhìn hệ thống:

```java
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.broker.BrokerAvailabilityEvent;
import org.springframework.stereotype.Component;

@Component
class BrokerStatusListener {
    @EventListener
    void onBrokerChanged(BrokerAvailabilityEvent event) {
        System.out.println("broker available=" + event.isBrokerAvailable());
    }
}
```

Đây là **fragment bean Spring**, không phải chương trình CLI. Event này thích hợp cho monitoring và producer có thể hoãn gửi khi broker xuống. Nó **không xác nhận** rằng từng client TCP session đang hoạt động hoặc message đã được user nhận.

Khi broker restart/network rớt, các relay client connections có thể mất; clients cần **WebSocket/STOMP reconnect, CONNECT, SUBSCRIBE lại**. Không giả định reconnect của system connection tự khôi phục các client subscriptions. Nếu app phải không mất bất kỳ business event nào, hãy dùng durable storage/outbox/queue và xác nhận giao nhận phù hợp thay vì chỉ gọi `convertAndSend(...)`.

## 6. Broker ngoài có nghĩa vụ gì và không hứa điều gì?

Một broker STOMP đầy đủ có thể hỗ trợ ACK/NACK, receipts, topic/queue, persistence, failover, durable subscription, TTL, dead-lettering và clustering. Tuy nhiên khả năng thực tế phụ thuộc **broker, plugin STOMP, cấu hình topology, destination mapping và client headers**.

| Tính chất | Simple broker | Broker relay + external broker |
| --- | --- | --- |
| Subscription location | RAM trong Spring instance | External broker routing/state |
| Cross-node broadcast | Không tự có | Có thể khi tất cả nodes truy cập cùng broker topology |
| Message khi client offline | Không tự lưu | Chỉ khi thiết kế durable queue/topic/replay đúng |
| STOMP ACK/RECEIPT | Không hỗ trợ đầy đủ | Phụ thuộc broker, mode và plugin STOMP |
| Queue delivery cạnh tranh | Prefix `/queue` không bảo đảm | Phụ thuộc broker destination semantics |
| Broker clustering | Không | Có thể nếu broker hỗ trợ và cấu hình |
| Quản trị | Ít phụ thuộc | Cần vận hành broker, credentials, network, HA |
| Chi phí TCP connections | Chủ yếu WebSocket client ↔ Spring | Cộng thêm relay TCP connections tới broker |
| Restart Spring | Mất WebSocket sessions/subscriptions | WebSocket sessions vẫn cần reconnect |
| Exactly-once delivery | Không | Không tự có; cần protocol và idempotent consumer |

**Ví dụ nguy hiểm khi migration:** ứng dụng cũ dùng `/queue/jobs` trên simple broker như tên của một kênh broadcast. Sau khi chuyển sang RabbitMQ/ActiveMQ, external broker có thể định tuyến `/queue/jobs` như hàng đợi với competing consumers. Đây là thay đổi semantics lớn. Phải kiểm tra routing/bindings/ack policy qua integration tests thực tế.

**Một lệnh publish không phải delivery receipt.** `SimpMessagingTemplate.convertAndSend(...)` đưa message tới Spring broker channel. Broker nhận có thể chưa persist, client có thể vừa disconnect, hoặc thiết bị có thể chưa ACK. Nếu yêu cầu delivery guarantees, cần xác định “giao thành công” ở tầng nào và đưa vào thiết kế broker confirmations, ACK, idempotency key, outbox và retry.

## 7. User destination khi chạy nhiều Spring nodes

Spring cho phép client đăng ký `/user/queue/notifications`; Spring `UserDestinationMessageHandler` dịch destination logic thành destination riêng theo session. Server có thể gửi message với:

```java
// Fragment bên trong service đã inject SimpMessagingTemplate messagingTemplate.
messagingTemplate.convertAndSendToUser(
        "alice", "/queue/notifications", "You've got mail");
```

Nếu user `alice` có session trên node B nhưng event được xử lý ở node A, node A có thể không resolve được user destination nếu chỉ thấy registry cục bộ. Spring hỗ trợ settings để phối hợp nhiều nodes:

- `setUserDestinationBroadcast(...)`: broadcast những user destinations không resolve được, để nodes khác thử tìm session phù hợp.
- `setUserRegistryBroadcast(...)`: chia sẻ thông tin user sessions giữa app instances qua broker destination.

Ví dụ **chỉ khi có relay/external broker cùng topology phù hợp**:

```java
@Override
public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.setApplicationDestinationPrefixes("/app");
    registry.setUserDestinationPrefix("/user");

    registry.enableStompBrokerRelay("/topic", "/queue")
            .setRelayHost("broker.internal")
            .setRelayPort(61613);

    registry.setUserDestinationBroadcast("/topic/unresolved-user");
    registry.setUserRegistryBroadcast("/topic/user-registry");
}
```

Đây là **fragment cấu hình**: host/credentials/SSL không được thể hiện đầy đủ. Các destination dùng để broadcast metadata/registry là đường phối hợp **nội bộ**, không cho client bất kỳ `SEND` vào hoặc `SUBSCRIBE` đọc; enforce authorization.

Khi các user destinations giải thành queues riêng trên broker ngoài, chú ý cleanup inactive queues, TTL hoặc auto-delete theo sản phẩm. Một số broker tạo queue tạm cho mỗi WebSocket session; reconnect hàng loạt không được để tích lũy hàng triệu tài nguyên cũ.

## 8. Ra quyết định và lộ trình mở rộng

### 8.1 Bảng quyết định

| Nhu cầu | Lựa chọn gợi ý | Vì sao? |
| --- | --- | --- |
| Học Spring STOMP, một JVM | Simple broker | Gọn, không phải vận hành broker |
| Dashboard đơn node, notification best-effort | Simple broker + REST snapshot/resync | Không cần durable fan-out |
| Nhiều Spring pods cùng broadcast tới một topic | External STOMP broker/relay hoặc message bus chung | Tránh subscriptions bị chia theo node |
| Cần gửi notification cho offline user | Database/outbox và replay; có thể dùng external broker | WebSocket subscription không phải durable inbox |
| Cần ACK, redelivery, dead-letter, retry | Messaging broker đủ tính năng + thiết kế ACK | Simple broker không phải reliable work queue |
| Hàng chục nghìn kết nối và fan-out lớn | Benchmark websocket gateway + relay + broker resources | Relay tạo TCP connections và broker load |
| Cần per-user authorization và privacy | Spring Security trên handshake và STOMP destinations | Loại broker không thay được phân quyền |
| Cần global order | Thiết kế partition/sequence/dedup | Spring outbound order flag chỉ per-session |

Đừng chọn broker chỉ theo số lượng nodes. Hãy định lượng **concurrent WebSocket sessions**, messages/sec, fan-out, payload size, acceptable latency, uptime, RPO/RTO, độ tin cậy khi client offline, thứ tự, chi phí vận hành và security boundaries.

### 8.2 Các bước chuyển simple → relay

1. Liệt kê toàn bộ broker prefixes, topics, queues, publishers/subscribers, user destinations; xác minh semantics hiện tại.
2. Chọn broker có STOMP listener và routing đúng nhu cầu, tạo accounts/permissions, cấu hình TLS/HA, metrics.
3. Bảo toàn WebSocket endpoint và application prefix `/app` khi có thể. Chuyển **một** broker handler từ simple sang relay; tránh trùng prefixes.
4. Test với ít nhất **hai Spring nodes** và các WebSocket clients nối các nodes khác nhau; publish từ từng node và kiểm tra recipients.
5. Kiểm tra user destinations khi user đăng nhập nhiều sessions/nodes, registry broadcasts và cleanup queues.
6. Thử các lỗi: broker restart, DNS/TLS error, mất một node, client reconnect, duplicate/out-of-order messages, slow consumer.
7. Theo dõi backlog, message latency, broker TCP connections, authorization failures, Spring outbound buffer và chuẩn bị rollback.

Bản thân Spring app vẫn xử lý business logic trong `@MessageMapping`, và bạn nên đảm bảo HTTP/WebSocket entrypoints không đổi ngoài các semantics được phê duyệt. External broker **không tự thay thế** database business, REST query hay durable outbox.

## 9. Ví dụ Java 17+ chạy được với simple broker

Ví dụ gồm Spring Boot server phát thông báo lên `/topic/events` và Java JDK WebSocket client gửi trực tiếp STOMP CONNECT/SUBSCRIBE. Không cần Internet khi **chạy HTTP requests nội bộ**; lần build đầu có thể cần tải dependency từ Maven. Đây là một demo subscription đơn node, để hiểu trước khi dùng relay.

### 9.1 Cấu trúc và Gradle

```text
broker-demo/
├── build.gradle
└── src/main/java/demo/
    ├── BrokerDemoApplication.java
    └── StompDemoClient.java
```

`build.gradle`:

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.13'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'demo'
version = '1.0'

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-websocket'
}

springBoot {
    mainClass = 'demo.BrokerDemoApplication'
}

tasks.register('runStompClient', JavaExec) {
    group = 'application'
    classpath = sourceSets.main.runtimeClasspath
    mainClass = 'demo.StompDemoClient'
}
```

Dùng Gradle đã cài hoặc Gradle Wrapper phù hợp với Spring Boot. Source file thứ nhất chứa server, configuration và REST publisher; file thứ hai chứa client. Có thể sử dụng Spring Boot Gradle plugin theo BOM của project thay vì hard-code version khi tích hợp vào hệ thống sẵn có.

### 9.2 Spring Boot server

`src/main/java/demo/BrokerDemoApplication.java`:

```java
package demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@SpringBootApplication
public class BrokerDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(BrokerDemoApplication.class, args);
    }
}

@Configuration
@EnableWebSocketMessageBroker
class BrokerConfig implements WebSocketMessageBrokerConfigurer {
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("http://localhost:*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic", "/queue");
    }
}

@RestController
class EventPublisher {
    private final SimpMessagingTemplate messagingTemplate;

    EventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @PostMapping(
            path = "/api/events",
            consumes = MediaType.TEXT_PLAIN_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    String publish(@RequestBody String body) {
        messagingTemplate.convertAndSend("/topic/events", body);
        return "sent: " + body;
    }
}
```

`@EnableWebSocketMessageBroker` tạo messaging infrastructure. REST controller không trực tiếp giữ danh sách WebSocket sessions mà dùng `SimpMessagingTemplate`. Response `sent: hello` có nghĩa HTTP method được xử lý tới dòng return, **không bảo đảm** tất cả subscribers đã nhận. API chưa xác thực và chỉ phù hợp local demo.

### 9.3 JDK WebSocket client gửi STOMP frames

`src/main/java/demo/StompDemoClient.java`:

```java
package demo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class StompDemoClient {
    private static final class Receiver implements WebSocket.Listener {
        final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        private final StringBuilder pending = new StringBuilder();

        @Override
        public void onOpen(WebSocket socket) {
            socket.request(1);
        }

        @Override
        public CompletionStage<?> onText(
                WebSocket socket, CharSequence chunk, boolean last) {
            synchronized (pending) {
                pending.append(chunk);
                int end;
                while ((end = pending.indexOf("\u0000")) >= 0) {
                    String frame = pending.substring(0, end);
                    pending.delete(0, end + 1);
                    frame = frame.replaceFirst("^[\\r\\n]+", "");
                    if (!frame.isEmpty()) {
                        frames.offer(frame);
                    }
                }
            }
            socket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket socket, Throwable error) {
            frames.offer("ERROR\n\n" + error.getMessage());
        }
    }

    public static void main(String[] args) throws Exception {
        Receiver receiver = new Receiver();
        WebSocket socket = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .subprotocols("v12.stomp")
                .buildAsync(URI.create("ws://localhost:8080/ws"), receiver)
                .get(10, TimeUnit.SECONDS);

        try {
            socket.sendText(
                    "CONNECT\naccept-version:1.2\nhost:localhost"
                    + "\nheart-beat:0,0\n\n\u0000", true).join();

            String connected = receiver.frames.poll(10, TimeUnit.SECONDS);
            if (connected == null || !connected.startsWith("CONNECTED")) {
                throw new IllegalStateException(
                        "Expected CONNECTED: " + connected);
            }
            System.out.println("CONNECTED");

            socket.sendText(
                    "SUBSCRIBE\nid:subscription-1"
                    + "\ndestination:/topic/events"
                    + "\nack:auto\n\n\u0000", true).join();

            System.out.println("SUBSCRIBED /topic/events");
            System.out.println("WAITING FOR MESSAGE");

            String message = receiver.frames.poll(60, TimeUnit.SECONDS);
            if (message == null || !message.startsWith("MESSAGE")) {
                throw new IllegalStateException(
                        "Expected MESSAGE: " + message);
            }

            int separator = message.indexOf("\n\n");
            if (separator < 0) {
                throw new IllegalStateException("Missing frame separator");
            }
            System.out.println("MESSAGE: " + message.substring(separator + 2));

            socket.sendText("DISCONNECT\n\n\u0000", true).join();
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
        } finally {
            socket.abort();
        }
    }
}
```

Ở đây JDK `WebSocket` chỉ triển khai WebSocket frames, **không có STOMP client library**; chúng ta tự gửi STOMP CONNECT/SUBSCRIBE và tìm byte NUL để tách frames. Việc ghép fragments dùng `StringBuilder` cho trường hợp một STOMP frame bị tách ở WebSocket transport. Demo tắt STOMP heartbeat, giới hạn một message nhận và không xử lý toàn bộ tính năng STOMP như `content-length` chứa byte NUL; production nên dùng một STOMP client library phù hợp.

**Lưu ý:** `.subprotocols("v12.stomp")` ở HTTP/WebSocket handshake khác hoàn toàn frame STOMP `CONNECT` gửi sau khi socket OPEN. Server Spring tự thực hiện negotiation nếu có hỗ trợ STOMP 1.2.

### 9.4 Chạy với ba terminal

Terminal 1 — chạy server, đợi ứng dụng sẵn sàng port 8080:

```bash
gradle bootRun
```

Terminal 2 — client kết nối và subscribe:

```bash
gradle runStompClient
```

Kết quả do chương trình client tự in **trước khi message đến**:

```text
CONNECTED
SUBSCRIBED /topic/events
WAITING FOR MESSAGE
```

Terminal 3 — sau khi client đã in `WAITING FOR MESSAGE`, gửi HTTP POST:

```bash
curl -X POST http://localhost:8080/api/events -H "Content-Type: text/plain" --data-binary "hello"
```

Nếu dùng Windows PowerShell:

```powershell
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/events' -ContentType 'text/plain' -Body 'hello'
```

REST response body:

```text
sent: hello
```

Client terminal 2 tiếp tục in:

```text
MESSAGE: hello
```

Spring, Gradle, Tomcat có thể in log khác; các dòng trên là **chính xác phần mà ứng dụng demo tự in** nếu chạy theo thứ tự, không bị timeout và server chạy port 8080. Nếu port đổi, phải đổi URI trong Java client. Chương trình demo không dùng `@MessageMapping` vì muốn tập trung vào đường **application publisher → broker → subscriber**. Bạn có thể mở hai client trước khi gửi POST để thấy fan-out trên một Spring instance.

### 9.5 Thử đổi broker mà giữ endpoint

Khi triển khai broker ngoài đã bật STOMP/TCP listener, trong `BrokerConfig.configureMessageBroker` thay:

```java
registry.enableSimpleBroker("/topic", "/queue");
```

bằng cấu hình tương ứng:

```java
registry.enableStompBrokerRelay("/topic", "/queue")
        .setRelayHost("127.0.0.1")
        .setRelayPort(61613)
        .setSystemLogin("configured-system-user")
        .setSystemPasscode("configured-secret")
        .setClientLogin("configured-client-user")
        .setClientPasscode("configured-secret");
```

Đây là **cấu hình minh họa**, không tự chạy nếu chưa có broker hoặc chưa cấp credentials đúng; production đọc secrets từ environment/secret manager, cấu hình TLS. Khi external broker hiểu `/topic/events` và Spring relay hoạt động, client vẫn subscribe cùng endpoint `/ws`. Để **kiểm chứng scale**, khởi tạo **hai Spring instances** ở các ports khác nhau, hai clients trỏ vào từng instance, rồi publish từ chỉ một node; message phải tới các clients theo topology broker đã cấu hình. Một demo một node chưa chứng minh cross-node fan-out.

## 10. Thứ tự, backpressure và vận hành

### 10.1 Thứ tự không tự được bảo đảm toàn hệ thống

Spring inbound/outbound channels sử dụng executor. Khi có nhiều threads, messages có thể được xử lý/đẩy ra không theo thứ tự nhận vào trên **cùng một session** nếu không cấu hình. Spring có tùy chọn:

```java
// Các fragment đặt vào một WebSocketMessageBrokerConfigurer:
@Override
public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws");
    registry.setPreserveReceiveOrder(true);
}

@Override
public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.setApplicationDestinationPrefixes("/app");
    registry.enableSimpleBroker("/topic");
    registry.setPreservePublishOrder(true);
}
```

`setPreserveReceiveOrder(true)` giúp xử lý inbound messages cho từng session theo thứ tự nhận. `setPreservePublishOrder(true)` bảo đảm thứ tự publish ra outbound channel cho từng session. Cả hai có overhead và **không bảo đảm global ordering** qua mọi nodes, topics, broker queues hay reconnects. Với dữ liệu bắt buộc đúng thứ tự, cần sequence number, entity version, stable partitioning/ordering key và xử lý duplicate/out-of-order trên client.

### 10.2 Slow consumers, send buffer và message size

Client đọc chậm làm WebSocket outbound buffers tăng, khiến server chạm giới hạn memory/latency. Một cấu hình phù hợp có thể là:

```java
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

// Fragment method của WebSocketMessageBrokerConfigurer:
@Override
public void configureWebSocketTransport(
        WebSocketTransportRegistration registry) {
    registry.setMessageSizeLimit(128 * 1024);
    registry.setSendBufferSizeLimit(512 * 1024);
    registry.setSendTimeLimit(15_000);
}
```

Thông số message/buffer tính theo byte và send time theo mili-giây. Những giới hạn này điều chỉnh **transport giữa Spring và client**, không điều chỉnh capacity external broker. Khi cần hỗ trợ large payloads, phải cân nhắc phương án REST/object storage cho dữ liệu lớn, gửi WebSocket thông báo nhỏ.

### 10.3 Metrics và lỗi có thể gặp

| Dấu hiệu | Khả năng | Cách kiểm tra |
| --- | --- | --- |
| Một số clients ở node khác không nhận | Simple brokers phân tán theo pods | Gắn logs node/session ID, kiểm tra broker topology |
| STOMP connected nhưng subscribe không có message | Sai prefix/destination hoặc gửi trước khi subscribe | Kiểm tra frames, routing, authorization |
| Relay báo broker unavailable | STOMP listener, DNS/TLS/firewall hoặc auth lỗi | BrokerAvailabilityEvent, TCP/broker logs |
| Broker quá tải với nhiều clients | Relay TCP per session, broker limits | Active connections, memory, FD, CPU |
| Không tìm thấy user trên node hiện tại | User registry/resolve phân tán | Cấu hình broadcasts, test multi-node |
| Client nhận chậm/đứt | Send buffer, proxy timeout, heartbeat | Outbound queue, close codes, network |
| Duplicate/lost messages sau retry/reconnect | Không có end-to-end idempotency | Event IDs, outbox/replay, ack policy |
| Trật thứ tự UI updates | Concurrent channels, broker/order semantics | Sequence numbers, per-session order options |

**Giám sát tối thiểu:** active WebSocket sessions, subscriptions, inbound/outbound executor queues, message latency, errors/close codes, outbound send buffer, broker TCP connections, broker memory/disk/backlog, reconnect rate, heartbeat misses, publication/delivery metrics theo yêu cầu SLA. Giám sát `SimpUserRegistry` không tự chứng minh client đã xử lý event.

### 10.4 Giải pháp bền vững cho business events

Với notification chỉ để UI biết “dữ liệu mới xuất hiện”, pattern phổ biến là:

```text
Business transaction
     │
     ├─ persist DB + event/outbox (durable)
     │
     ▼
Reliable delivery/event processing
     │
     ▼
Realtime notification via WebSocket/STOMP
     │
     ▼
Client gọi REST/query để tải snapshot mới nhất
```

Khi client rớt mạng, nó reconnect/re-subscribe rồi tải snapshot hoặc các events theo cursor; không trông chờ WebSocket giữ messages offline. Điều này đặc biệt quan trọng với trạng thái tiền, đơn hàng, công việc có hậu quả lâu dài.

## 11. Bảo mật và các lỗi thường gặp khi migration

- **Không coi broker credential là user authentication:** relay dùng credentials đã cấu hình để nối broker. Phải xác thực user tại HTTP/WebSocket hoặc STOMP CONNECT và authorize SEND/SUBSCRIBE theo từng destination.
- **Không mở public internal user-registry topics:** destinations dùng bởi `setUserDestinationBroadcast`/`setUserRegistryBroadcast` phải được bảo vệ.
- **Không tin rằng sticky sessions giải quyết cross-node fan-out:** subscriptions vẫn trong simple broker RAM riêng.
- **Không giả định `/queue` giữ nghĩa giống nhau:** full broker thường có queue semantics khác simple broker; kiểm tra destination mappings.
- **Không gửi secret dưới dạng STOMP header rồi ghi full frame log:** bảo vệ token, cookies, user details; TLS cho cả client→Spring và Spring→broker.
- **Không dùng simple broker cho ACK/durable workflows:** retry/ack/transaction cần broker và contract được thiết kế đúng.
- **Không coi một lần `convertAndSend` là exactly-once:** có thể mất/dặp do retry/reconnect hoặc client offline; đưa message ID/idempotency vào protocol.
- **Không quên reconnect/resubscribe khi node/broker restart:** WebSocket OPEN sau reconnect chưa có nghĩa subscriptions đã khôi phục.
- **Không đánh đồng STOMP heartbeat với TCP keepalive/WebSocket Ping/Pong:** đây là các tầng khác nhau và timeout phải phù hợp proxy.
- **Không để send buffer/broker connections không giới hạn:** định cỡ theo tải, đặt limits, slow-consumer policy và giám sát broker/node.
- **Không trộn relay và simple cho cùng destination prefixes:** sẽ khó dự đoán handler nào nhận message, hoặc gây phát trùng.
- **Không triển khai broker ngoài trước khi kiểm tra listener STOMP:** RabbitMQ plugin/tài nguyên, broker account, destination permissions và network/TLS đều phải sẵn sàng.

## 12. Câu hỏi kiểm tra hiểu bài

**1. Hai Spring pods đều bật simple broker `/topic/news`. POST publish vào pod A. User ở pod B có tự nhận không?**

Không. Simple broker trong pod A chỉ biết subscribers hiện kết nối với pod A. Nếu business cần toàn hệ thống, phải có broker/bus chung.

**2. `/queue/jobs` trên simple broker có chắc chắn chỉ một trong hai subscribers nhận mỗi job không?**

Không. Với simple broker, `/queue` chỉ là prefix chuỗi; competing-consumer queue semantics không được bảo đảm.

**3. Relay kết nối tới RabbitMQ có tự làm mọi message durable không?**

Không. Phụ thuộc queue/exchange/destination mapping, broker settings, headers, publisher/consumer confirmation và idempotency của ứng dụng.

**4. Relay dùng một connection TCP cho mọi WebSocket clients đúng không?**

Sai. Nó dùng riêng client relay TCP connection cho mỗi STOMP client session và system connection cho message của ứng dụng.

**5. `BrokerAvailabilityEvent` bảo đảm mọi client nhận message gần nhất chưa?**

Chưa. Nó phản ánh broker system availability; không có end-to-end client receipt tự động.

**6. Bật `setPreservePublishOrder(true)` có tạo global exactly-once delivery không?**

Không. Nó xử lý outbound publish order trong phạm vi một client session, có overhead; vẫn cần sequence/dedup và broker contract.

**7. Có một user kết nối hai Spring instances khác nhau; vì sao `/user/queue/notifications` cần thiết kế thêm?**

User destination phải được resolve đúng session. Dùng user registry/broadcast qua broker và test multi-node nếu event có thể đến một node khác node giữ session.

**8. Khi client offline 10 phút, cách khôi phục notification quan trọng là gì?**

Lưu dữ liệu nghiệp vụ bền vững, reconnect/resubscribe, và tải snapshot/replay theo cursor. Simple broker không lưu durable offline messages.

## Tài liệu tham khảo chính thức

- [Spring Framework — STOMP Enable](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/enable.html)
- [Spring Framework — Simple Broker](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/handle-simple-broker.html)
- [Spring Framework — External Broker](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/handle-broker-relay.html)
- [Spring Framework — Connecting to Broker](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/handle-broker-relay-configure.html)
- [Spring Framework — Message Flow](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/message-flow.html)
- [Spring Framework — User Destinations](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/user-destination.html)
- [Spring Framework — Message Order](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/ordered-messages.html)
- [Spring Framework — Monitoring](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/stats.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="websocket-stomp-010">Xác thực, phân quyền destination, heartbeat và phục hồi kết nối</a>

<details>
<summary>Xem chi tiết</summary>

# Xác thực, phân quyền destination, heartbeat và phục hồi kết nối với WebSocket/STOMP

## 1. Bối cảnh và bốn lớp trách nhiệm

Khi xây dựng ứng dụng trò chuyện, thông báo hay bảng số liệu thời gian thực bằng Spring WebSocket và STOMP, mở được kết nối chỉ là bước đầu. Hệ thống cần trả lời bốn câu hỏi xuyên suốt vòng đời kết nối:

1. **Xác thực (authentication):** Ai đang kết nối? Danh tính nào có thể tin cậy?
2. **Phân quyền (authorization):** Danh tính đó được gửi (`SEND`) và đăng ký nhận (`SUBSCRIBE`) những destination nào?
3. **Heartbeat:** Làm sao phát hiện kết nối không còn trao đổi dữ liệu, đặc biệt khi TCP/WebSocket chưa báo đóng ngay?
4. **Phục hồi kết nối (reconnection):** Sau mất mạng, client có thể kết nối, xác thực, đăng ký nhận lại như thế nào, và làm sao tránh mất/trùng dữ liệu?

Trong STOMP over WebSocket, mỗi lớp diễn ra ở một giai đoạn khác nhau:

```text
Browser / ứng dụng client
       |
       | HTTP GET /ws, Upgrade: websocket, Cookie/Origin
       v
[HTTP Security + Origin + WebSocket handshake]
       |
       | WebSocket mở, có thể đã gắn Principal từ HTTP
       v
[STOMP CONNECT: phiên STOMP, heartbeat negotiation, CSRF/token nếu cấu hình]
       |
       | CONNECTED
       v
[STOMP SEND, SUBSCRIBE, UNSUBSCRIBE]
       |                 |
       v                 v
/app/** -> Controller    /topic/**, /queue/** -> Message broker
       |
       v
[Message authorization: kiểm tra loại frame + destination + Principal]
       |
       v
[Heartbeats & giám sát phiên]
       |
       | mất kết nối / broker lỗi / auth hết hạn
       v
[Client reconnect -> CONNECT mới -> xác thực lại -> SUBSCRIBE lại]
```

Cần giữ các ranh giới rõ ràng:

- **HTTP handshake được phép** không có nghĩa được gửi/nhận mọi message.
- **Principal đã xác thực** không có nghĩa được truy cập dữ liệu thuộc tài khoản khác.
- **Heartbeat phát hiện sự im lặng/kết nối chết**, không thay thế xác thực, xác nhận giao hàng hay transaction.
- **Kết nối lại thành công** không có nghĩa mọi message trong lúc mất mạng đã được khôi phục.

Bài này dùng Spring Framework với STOMP message broker trên stack Servlet (Spring MVC/Spring Security 6, Java 17+) làm ví dụ. Raw WebSocket hoặc WebFlux không dùng STOMP sẽ có API và quy trình bảo mật khác. Với môi trường phát triển mới, áp dụng phiên bản Spring/Spring Security phù hợp bộ dependency của dự án.

## 2. Xác thực ở HTTP handshake và STOMP CONNECT

### 2.1 Trường hợp thông dụng: HTTP authentication được mang sang STOMP session

STOMP over WebSocket bắt đầu bằng HTTP handshake. Nếu ứng dụng xác thực người dùng qua Spring Security bằng cookie/session, Spring có thể lấy `Principal` từ request HTTP, liên kết nó với WebSocket session, rồi đưa Principal vào các message STOMP của phiên đó.

```text
HTTP login (Spring Security) -> HTTP session/cookie
          |
          v
GET /ws (WebSocket upgrade)
          |
          v
Spring Security xác thực request, lấy Principal
          |
          v
Spring WebSocket lưu người dùng cho WebSocket/STOMP session
          |
          v
STOMP SEND / SUBSCRIBE -> user header -> authorization
```

Ví dụ: Alice đã đăng nhập, kết nối `wss://api.example.com/ws` từ trang được phép. Các frame của phiên có thể được Spring liên kết với `Principal` Alice. Ở `@MessageMapping`, controller có thể nhận tham số `java.security.Principal` hoặc `Authentication` theo tích hợp Security.

Chú ý: STOMP định nghĩa native headers `login` và `passcode` cho CONNECT, nhưng Spring mặc định **không dùng** chúng để xác thực lại phiên STOMP over WebSocket khi hệ thống dựa vào HTTP authentication. Không được tin giá trị client tự đặt ở `login` hoặc `X-User-Id` nếu server chưa kiểm chứng.

### 2.2 Ví dụ bảo vệ endpoint WebSocket ở HTTP layer

**Tệp `HttpSecurityConfig.java`:**

```java
package com.example.chat;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
public class HttpSecurityConfig {

    @Bean
    SecurityFilterChain httpSecurityFilterChain(HttpSecurity http)
            throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/ws", "/ws/**").authenticated()
                .anyRequest().authenticated());
        http.formLogin(withDefaults());
        return http.build();
    }
}
```

Đây là ví dụ cấu hình Spring Security cho Servlet/MVC, không phải chương trình Java độc lập và không phải `SecurityWebFilterChain` của WebFlux. Nó yêu cầu người dùng đã xác thực đối với `/ws` và toàn bộ route khác (đây là chính sách mặc định **minh họa**, cần điều chỉnh route công khai trong ứng dụng thực). `formLogin` thể hiện trường hợp đăng nhập dựa trên session; một sản phẩm API dùng OAuth2/JWT hoặc SSO cần cơ chế xác thực tương ứng.

Không vô hiệu hóa CSRF trên toàn hệ thống chỉ vì có WebSocket. Tùy cách bật Spring Security WebSocket, CONNECT có thể phải mang CSRF token để chống tấn công từ trang khác. Đối với SockJS fallback, nếu cần ngoại lệ HTTP CSRF cho các transport POST, giới hạn ngoại lệ vào endpoint cần thiết và duy trì bảo vệ STOMP CONNECT.

### 2.3 Vai trò của Origin và giới hạn của nó

Origin là nguồn gốc trang web trong browser, ví dụ `https://app.example.com`. Browser không đơn giản áp dụng quy tắc same-origin cho WebSocket như cho XMLHttpRequest; server phải giới hạn Origin được phép, đặc biệt khi cookie/session được gửi tự động.

```java
@Override
public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws")
            .setAllowedOrigins("https://app.example.com");
}
```

Đoạn này thuộc `WebSocketMessageBrokerConfigurer` (cấu hình đầy đủ ở phần sau). `setAllowedOrigins` kiểm tra Origin ở handshake; không phân quyền `/topic/private` hay xác thực danh tính. Client ngoài browser có thể tự đặt Origin, vì vậy **Origin không phải cơ chế xác thực**. Với site nhiều origin, khai báo allowlist cụ thể; tránh `*` cho endpoint cần session/cookie. Nếu dùng proxy, cần thiết lập forwarded headers đáng tin cậy để không sai scheme/host.

### 2.4 Trường hợp token nằm trong STOMP CONNECT

Browser `WebSocket` API thường không cho ứng dụng JavaScript tùy ý gắn HTTP Authorization header cho handshake. Một số hệ thống xác thực ở frame STOMP CONNECT bằng header `Authorization: Bearer ...` do thư viện STOMP gửi **sau khi WebSocket đã mở**.

Quy trình đúng:

1. Client lấy token từ nguồn đáng tin cậy và đưa token vào native STOMP CONNECT header, không đặt user ID tùy ý rồi tin nó.
2. `ChannelInterceptor` được đăng ký trên `clientInboundChannel` đọc token của frame CONNECT.
3. Server kiểm chứng chữ ký/issuer/audience/thời hạn/trạng thái tài khoản bằng cơ chế authentication thực.
4. Chỉ khi token hợp lệ, tạo `Authentication` phù hợp và gọi `StompHeaderAccessor.setUser(authentication)` trên CONNECT.
5. Spring ghi nhận Principal đó cho những STOMP message tiếp theo cùng phiên; message authorization dùng Principal đã xác thực.
6. Cần bảo đảm interceptor xác thực chạy **trước** interceptor Spring Security dùng để phân quyền.

**Mẫu code minh họa tích hợp một bộ kiểm chứng có thật (không tự chứa logic kiểm chứng JWT):**

```java
package com.example.chat;

import java.util.List;
import java.util.function.Function;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;

public final class BearerConnectInterceptor implements ChannelInterceptor {

    private final Function<String, Authentication> tokenVerifier;

    public BearerConnectInterceptor(
            Function<String, Authentication> tokenVerifier) {
        this.tokenVerifier = tokenVerifier;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(
                        message, StompHeaderAccessor.class);

        if (accessor == null
                || accessor.getCommand() != StompCommand.CONNECT) {
            return message;
        }

        List<String> values =
                accessor.getNativeHeader("Authorization");

        if (values == null || values.size() != 1
                || !values.get(0).startsWith("Bearer ")) {
            throw new BadCredentialsException("Missing bearer token");
        }

        String token = values.get(0).substring("Bearer ".length());
        if (token.isBlank()) {
            throw new BadCredentialsException("Empty bearer token");
        }

        Authentication authentication = tokenVerifier.apply(token);
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BadCredentialsException("Invalid bearer token");
        }

        accessor.setUser(authentication);
        return message;
    }
}
```

Code trên hoàn chỉnh về cấu trúc Java, nhưng **không tự xác thực một token**: tham số `tokenVerifier` phải là hàm đã được cấp bởi dịch vụ bảo mật có thật, kiểm tra signature, expiry, issuer/audience và trạng thái người dùng. Không dùng `token -> new UsernamePasswordAuthenticationToken("alice", ... )` như một hàm xác thực sản phẩm. Khi token sai, ví dụ ném `BadCredentialsException` để server từ chối CONNECT theo pipeline.

Đăng ký interceptor trong một class cấu hình riêng có thứ tự cao nếu tích hợp message authorization của Spring Security:

```java
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
class StompAuthChannelConfig
        implements WebSocketMessageBrokerConfigurer {

    private final BearerConnectInterceptor interceptor;

    StompAuthChannelConfig(BearerConnectInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void configureClientInboundChannel(
            ChannelRegistration registration) {
        registration.interceptors(interceptor);
    }
}
```

Đây là đoạn class **bổ sung**; cần import `Configuration`, `Order`, `Ordered`, `ChannelRegistration` và `WebSocketMessageBrokerConfigurer`. Phải có Spring bean `BearerConnectInterceptor` do ứng dụng tạo cùng verifier. Cách đăng ký theo `@Order` thể hiện nhu cầu chạy trước message security, không tự đảm bảo trình tự nếu ứng dụng có nhiều cấu hình không kiểm soát. Một thiết kế cookie/session thường không cần interceptor bearer này.

**Lưu ý quan trọng về CSRF:** `@EnableWebSocketSecurity` của Spring Security có chính sách kiểm tra CSRF trong CONNECT. Kể cả token xác thực hợp lệ, CONNECT có thể bị từ chối nếu thiếu CSRF token theo cấu hình. Tích hợp cookie/session và cấu hình token-based phải thiết kế cùng chính sách an ninh; không tự tắt bảo vệ mà chưa phân tích mô hình đe dọa.

## 3. Phân quyền theo message type và destination

### 3.1 Destination của STOMP là gì?

Với broker được cấu hình:

- `/ws` là **HTTP URL bắt đầu WebSocket/STOMP session**.
- `/app/**` là **application destination**, thường chuyển message `SEND` đến `@MessageMapping` trong ứng dụng.
- `/topic/**` là convention cho nơi phát tin đến nhiều subscriber khi dùng simple broker.
- `/queue/**` là convention thường dùng cho kênh hàng đợi/thông điệp riêng; với Spring simple broker, tiền tố không tự mang bảo đảm point-to-point như một message broker chuyên dụng.
- `/user/**` là **cú pháp user destination của Spring**, thường chuyển các subscription thành destination riêng của từng WebSocket session hoặc user sau khi qua resolver.

Client gửi `SEND /app/chat` và `SUBSCRIBE /topic/announcements` là hai hành động khác nhau, dù đều có `destination`. Không được phân quyền chỉ bằng tiền tố đường dẫn mà quên **loại message**.

Ví dụ:

```text
SEND       /app/chat                   -> controller message
SEND       /topic/announcements        -> phải bị chặn với client thường
SUBSCRIBE  /topic/announcements        -> có thể cho phép
SUBSCRIBE  /user/queue/notifications   -> yêu cầu user xác thực
SUBSCRIBE  /topic/admin                -> chỉ dành role ADMIN
```

Nếu cho client `SEND` trực tiếp đến `/topic/admin`, client có thể phát thông điệp giả như đến từ server, tùy cấu hình broker. Nếu cho user thường `SUBSCRIBE /topic/admin`, họ có thể thấy thông tin không thuộc quyền. Vì vậy phải viết chính sách riêng cho SEND và SUBSCRIBE, mặc định từ chối trường hợp chưa liệt kê.

### 3.2 Spring Security: AuthorizationManager cho STOMP messages

Spring Security hỗ trợ `@EnableWebSocketSecurity` và `MessageMatcherDelegatingAuthorizationManager` để phân quyền các message inbound. Một cấu hình minh họa:

**Tệp `StompMessageSecurityConfig.java`:**

```java
package com.example.chat;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;

@Configuration
@EnableWebSocketSecurity
public class StompMessageSecurityConfig {

    @Bean
    AuthorizationManager<Message<?>> messageAuthorizationManager(
            MessageMatcherDelegatingAuthorizationManager.Builder messages) {

        messages
            .nullDestMatcher().authenticated()
            .simpSubscribeDestMatchers("/topic/admin/**")
                .hasRole("ADMIN")
            .simpSubscribeDestMatchers(
                    "/topic/announcements",
                    "/user/queue/notifications")
                .hasRole("USER")
            .simpDestMatchers("/app/**").hasRole("USER")
            .simpTypeMatchers(
                    SimpMessageType.MESSAGE,
                    SimpMessageType.SUBSCRIBE)
                .denyAll()
            .anyMessage().denyAll();

        return messages.build();
    }
}
```

**Giải thích thứ tự:**

1. `nullDestMatcher().authenticated()` đòi hỏi người dùng đã xác thực đối với message không có destination. Những message mang ý nghĩa phiên như CONNECT/DISCONNECT thường thuộc nhóm này.
2. `simpSubscribeDestMatchers("/topic/admin/**").hasRole("ADMIN")` đặt trước rule rộng hơn để tránh cấp nhầm quyền nhận tin quản trị.
3. `simpSubscribeDestMatchers("/topic/announcements", "/user/queue/notifications").hasRole("USER")` cho ROLE_USER đăng ký hai vùng đã phê duyệt.
4. `simpDestMatchers("/app/**").hasRole("USER")` áp dụng cho destination ứng dụng; nếu cần chỉ cho phép `SEND`, hãy bổ sung matcher/rule cụ thể tương ứng và kiểm thử loại message.
5. Các `MESSAGE` hoặc `SUBSCRIBE` còn lại bị từ chối; cuối cùng từ chối mọi loại message không được liệt kê.

Chính sách minh họa yêu cầu **ROLE_USER** cho `/user/queue/notifications` và `/topic/announcements`; tài khoản có đúng ROLE_ADMIN nhưng không có ROLE_USER sẽ chỉ đạt những rule dành ADMIN, trừ khi hệ thống quy định role hierarchy. Vì vậy cần thiết kế role rõ ràng; không tự giả định ADMIN đương nhiên bao gồm USER.

**Điểm kỹ thuật:** `@EnableWebSocketSecurity` bảo vệ STOMP inbound, không phải thay cho cấu hình Spring Security trên HTTP handshake. Các interceptor có thứ tự xử lý; nếu xác thực bằng STOMP CONNECT token, đặt authentication interceptor trước authorization interceptor. Khi Spring Security kiểm tra CSRF trên CONNECT, client phải gửi token theo hợp đồng bảo mật tương ứng.

### 3.3 Những điều matcher destination chưa làm thay bạn

Một rule `.simpSubscribeDestMatchers("/topic/rooms/**").hasRole("USER")` chỉ chứng minh người dùng có role USER; nó **không tự chứng minh** người dùng thuộc từng phòng cụ thể. Nếu dữ liệu có phạm vi `tenantId`, `roomId` hoặc `documentId`, authorization phải kiểm tra quyền đối với đối tượng đó.

Ví dụ `/topic/rooms/123`: người dùng Alice có ROLE_USER nhưng không nằm trong phòng 123 phải bị từ chối. Có thể xây `AuthorizationManager<Message<?>>` tùy biến để lấy thông tin Principal, message type, destination, kiểm tra membership qua dịch vụ tin cậy rồi trả quyết định. Không chấp nhận `roomId` hoặc `userId` do client đưa lên như chứng cứ quyền. Có thể thiết kế để client `SUBSCRIBE` một user destination, còn server chỉ phát những sự kiện mà user được phép xem.

Khi dùng user destinations, `/user/queue/notifications` là địa chỉ client subscribe; `SimpMessagingTemplate.convertAndSendToUser(username, "/queue/notifications", payload)` là một cách server gửi đến người dùng. Resolver có thể chuyển thành destination cụ thể của session. **Đừng coi chuỗi tên user trong địa chỉ là bằng chứng quyền truy cập.**

### 3.4 Kiểm tra quyền SEND còn quan trọng trong controller

Trong controller, kiểm tra user hoặc `Authentication` theo chính sách nghiệp vụ. Ví dụ mã minh họa đã có `Principal` từ HTTP/STOMP security:

**Tệp `ChatMessageController.java`:**

```java
package com.example.chat;

import java.security.Principal;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

@Controller
public class ChatMessageController {

    @MessageMapping("/echo")
    @SendToUser("/queue/echo")
    public String echo(String body, Principal principal) {
        if (principal == null) {
            throw new IllegalStateException("Authentication required");
        }
        return principal.getName() + ": " + body;
    }
}
```

Khi `setApplicationDestinationPrefixes("/app")`, client `SEND /app/echo` sẽ đến phương thức có `@MessageMapping("/echo")` nếu message authorization cho phép. Với `@SendToUser("/queue/echo")`, client phù hợp thường subscribe `/user/queue/echo`. Nếu áp dụng đúng cấu hình authorization ở mục 3.2, cần **bổ sung rule** cho `/user/queue/echo` trước khi test, vì rule minh họa chỉ cho `/user/queue/notifications`; mặc định sẽ từ chối địa chỉ khác. Đây là tính năng bảo vệ có chủ đích của chiến lược deny-by-default, không phải lỗi broker.

Ví dụ echo không thể thay thế kiểm tra membership đối với nghiệp vụ chat thực: nếu body chứa room ID, controller/service vẫn phải xác minh user là thành viên trước khi gửi tin vào phòng đó.

## 4. Cấu hình broker và endpoint đồng bộ với bảo mật

**Tệp `ChatWebSocketConfig.java`:**

```java
package com.example.chat;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class ChatWebSocketConfig
        implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins("https://app.example.com");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        registry.enableSimpleBroker("/topic", "/queue");
    }
}
```

Đây là cấu hình cơ sở cho ứng dụng Spring MVC + WebSocket message broker. Khai báo `enableSimpleBroker` tạo broker đơn giản trong bộ nhớ, phù hợp đào tạo và các hệ thống nhỏ; nó **không tương đương** RabbitMQ/ActiveMQ về lưu trữ, phân phối nhiều node, xác nhận giao hàng, độ bền message hay quản lý offline.

Các class bảo mật ở phần 2–3 bổ sung theo nhu cầu. Cần quyết định **một** chiến lược authentication chính ở HTTP hay CONNECT token, rồi kiểm soát các channel và CSRF nhất quán. Khi tích hợp nhiều `WebSocketMessageBrokerConfigurer`, hãy hiểu thứ tự đăng ký interceptor và cài đặt cấu hình theo ứng dụng.

Dùng Spring Boot 3.x, Java 17+ và Gradle với Spring dependency management, dependencies điển hình:

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-websocket'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.security:spring-security-messaging'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```

Đặt các tệp Java vào đúng package và source set. Chúng là những thành phần cấu hình cho một ứng dụng Spring Boot có entry point; không thể chạy riêng bằng `javac ChatWebSocketConfig.java` nếu thiếu classpath Spring. Với dự án có Spring Boot dependency management, phiên bản của thư viện Spring nên được quản lý đồng bộ thay vì trộn các JAR không tương thích.

## 5. STOMP heartbeat: thương lượng thời gian và phát hiện kết nối chết

### 5.1 Heartbeat của STOMP là gì?

Kể từ STOMP 1.1, frame `CONNECT`/`CONNECTED` có thể khai báo header `heart-beat: x,y` theo mili giây. Thông thường:

- Phía client `CONNECT heart-beat: cx,cy`: `cx` là thời gian tối thiểu mong muốn giữa các lần client **có thể gửi** heartbeat; `cy` là thời gian tối thiểu client **muốn nhận** dữ liệu/heartbeat từ server.
- Phía server `CONNECTED heart-beat: sx,sy`: `sx` là thời gian tối thiểu server **có thể gửi**; `sy` là thời gian tối thiểu server **muốn nhận** từ client.
- Nếu một phía khai báo `0` cho một chiều gửi hoặc nhận tương ứng, chiều đó có thể không được bật heartbeat.
- Khi cả hai phía đồng ý, khoảng dự kiến **client → server** là `max(cx, sy)`; **server → client** là `max(sx, cy)`.

Ví dụ:

```text
Client: CONNECT   heart-beat:10000,10000
Server: CONNECTED heart-beat:10000,20000

client -> server: max(10000, 20000) = 20000 ms
server -> client: max(10000, 10000) = 10000 ms
```

Đây là **khoảng hoạt động trao đổi dự kiến**, không phải cam kết hệ điều hành sẽ gửi đúng từng mili giây. Khi có frame dữ liệu được gửi thường xuyên, không nhất thiết phát thêm heartbeat riêng. Khi luồng im lặng, heartbeat (một byte xuống dòng theo giao thức) giúp phía nhận nhận thấy kênh còn hoạt động.

**Không phải giao thức xác nhận giao hàng:** heartbeat không xác nhận rằng một `SEND` đã được xử lý, ghi database hoặc tới subscriber. Dùng receipt/ACK và cơ chế nghiệp vụ phù hợp nếu cần biết kết quả.

### 5.2 Ví dụ Java chạy độc lập: tính khoảng heartbeat

Mã sau không dùng Spring, chỉ trình bày cách thương lượng thời gian. Chạy được bằng Java 17+:

**Tệp `HeartbeatNegotiationDemo.java`:**

```java
public class HeartbeatNegotiationDemo {

    static long agreedInterval(long senderCanSend,
                               long receiverWantsReceive) {
        if (senderCanSend == 0 || receiverWantsReceive == 0) {
            return 0;
        }
        return Math.max(senderCanSend, receiverWantsReceive);
    }

    public static void main(String[] args) {
        long clientSend = 10_000;
        long clientReceive = 10_000;
        long serverSend = 10_000;
        long serverReceive = 20_000;

        long clientToServer = agreedInterval(
                clientSend, serverReceive);
        long serverToClient = agreedInterval(
                serverSend, clientReceive);

        System.out.println("client -> server: " + clientToServer + " ms");
        System.out.println("server -> client: " + serverToClient + " ms");
        System.out.println("disabled direction: "
                + agreedInterval(0, 10_000) + " ms");
    }
}
```

Lệnh:

```bash
javac HeartbeatNegotiationDemo.java
java HeartbeatNegotiationDemo
```

**Stdout chính xác:**

```text
client -> server: 20000 ms
server -> client: 10000 ms
disabled direction: 0 ms
```

Chương trình giả định các khoảng không âm và không xác minh wire protocol; nó làm rõ quy tắc STOMP heartbeat. Không nên nhầm hàm tính interval này với scheduler thực của client/server.

### 5.3 Bật heartbeat cho Spring simple broker

Simple broker của Spring có thể gửi STOMP heartbeats khi được gắn `TaskScheduler`. Nếu không có scheduler thích hợp, chỉ đặt kỳ vọng heartbeat mà không cấu hình bộ phát có thể khiến client hiểu nhầm phiên bị chết.

Cấu hình mẫu bên dưới là **biến thể** của phương thức `configureMessageBroker`, thay vì cài đồng thời với đoạn `configureMessageBroker` ở phần 4:

```java
package com.example.chat;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class HeartbeatWebSocketConfig
        implements WebSocketMessageBrokerConfigurer {

    private final TaskScheduler scheduler;

    public HeartbeatWebSocketConfig(
            @org.springframework.beans.factory.annotation.Lazy
            TaskScheduler brokerHeartbeatScheduler) {
        this.scheduler = brokerHeartbeatScheduler;
    }

    @Bean
    TaskScheduler brokerHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("stomp-heartbeat-");
        return scheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins("https://app.example.com");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[] {10_000, 20_000})
                .setTaskScheduler(scheduler);
    }
}
```

Khi đưa vào dự án, chọn cấu hình broker **duy nhất**: dùng `HeartbeatWebSocketConfig` **thay cho** `ChatWebSocketConfig` phần 4, không đăng ký hai lớp song song cùng endpoint. Dùng `@Lazy` trong ví dụ vì việc inject `TaskScheduler` vào `WebSocketMessageBrokerConfigurer` có thể gây vòng phụ thuộc ở một số cách tự cấu hình của Spring. Spring cũng có thể cung cấp scheduler riêng; cần kiểm tra các bean hiện hữu và chỉnh qualifier/naming nếu ứng dụng có nhiều scheduler để bảo đảm đúng scheduler được dùng. Bộ nhớ/tài nguyên scheduler phải được Spring quản lý theo vòng đời ứng dụng.

`setHeartbeatValue(new long[]{10000, 20000})` tương ứng khả năng gửi/nhận mong muốn của phía broker. Chu kỳ thật còn phụ thuộc thương lượng với client, scheduler, traffic và thời gian ngưỡng timeout của phía nhận.

### 5.4 Simple broker và broker relay không giống nhau

Với simple broker, message subscriptions nằm trong bộ nhớ ứng dụng; heartbeat do Spring phối hợp scheduler và các phiên WebSocket. Nếu nhiều instance server độc lập, simple broker trên instance A không tự phân phối message đến subscriber trên instance B.

Với external broker relay (RabbitMQ/ActiveMQ tương thích STOMP), ứng dụng chuyển message tới broker bên ngoài. Relay quản lý kết nối **system** tới broker và các kết nối đại diện client; các heartbeat trên kết nối giữa Spring và broker là một tầng khác với việc client nhận STOMP heartbeat qua WebSocket.

Spring broker relay có thể tự thử nối lại **system connection** khi broker mất mạng (tài liệu mô tả khoảng retry mặc định 5 giây), nhưng việc đó **không tự nối lại WebSocket session của client**. Client vẫn cần logic reconnect, xác thực và đăng ký nhận lại. Khi mất kết nối system, server có thể phát `BrokerAvailabilityEvent` để thành phần phát tin biết broker tạm không sẵn sàng.

## 6. Phục hồi kết nối: trạng thái và quy trình chuẩn

### 6.1 Vì sao cần reconnect logic tại client?

Mất Wi-Fi, điện thoại chuyển mạng, proxy idle timeout, server khởi động lại hoặc broker gặp sự cố đều có thể làm WebSocket/STOMP session đóng. WebSocket API không bảo đảm mọi mạng lỗi đều phát một tín hiệu ngay lập tức; heartbeat và transport timeout giúp phát hiện tình huống im lặng.

Một chu trình client điển hình:

```text
DISCONNECTED
     |
     | connect WebSocket
     v
SOCKET_OPEN
     |
     | STOMP CONNECT (+ credential/CSRF + heart-beat)
     v
STOMP_CONNECTED
     |
     | SUBSCRIBE lại các destination được phép
     | đồng bộ lại dữ liệu từ HTTP snapshot + cursor/sequence
     v
READY
     |
     | mất mạng / server close / heartbeat lost
     v
DISCONNECTED
     |
     | trì hoãn retry có giới hạn + jitter/backoff
     +-----> connect lại
```

Khi reconnect thành công, **phiên STOMP mới** thường có session ID mới; các subscription của phiên cũ không còn. Client phải tạo lại subscription sau `CONNECTED`, và server phải kiểm tra lại quyền của user trên phiên mới.

Không nên gửi dữ liệu nghiệp vụ khi ứng dụng chưa biết phiên có khả năng nhận được. Với message có thể gây tác dụng phụ (thanh toán, thao tác quan trọng), cần idempotency key, correlation ID và API xác nhận kết quả, thay vì phát lại mọi `SEND` mù quáng mỗi lần mạng chập chờn.

### 6.2 Browser client với @stomp/stompjs

Thư viện JavaScript `@stomp/stompjs` có cấu hình heartbeat, reconnect delay và callback trạng thái. Ví dụ sau dùng xác thực qua HTTP cookie/session (do trình duyệt gửi theo chính sách cookie thích hợp) và danh sách destination đã được server cho phép.

**Tệp `stomp-client.js`:**

```javascript
import { Client, ReconnectionTimeMode } from "@stomp/stompjs";

const client = new Client({
  brokerURL: "wss://api.example.com/ws",
  reconnectDelay: 1000,
  reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
  maxReconnectDelay: 15000,
  heartbeatOutgoing: 10000,
  heartbeatIncoming: 10000,

  onConnect: () => {
    console.log("STOMP connected");

    // Đăng ký lại mỗi khi có CONNECTED mới.
    client.subscribe("/user/queue/notifications", (message) => {
      const event = JSON.parse(message.body);
      handleNotification(event);
    });
  },

  onStompError: (frame) => {
    console.error("STOMP error:", frame.headers["message"]);
  },

  onWebSocketClose: () => {
    console.info("WebSocket closed; client may reconnect");
  }
});

function handleNotification(event) {
  // Đối chiếu event.id / sequence trước khi cập nhật UI hoặc state.
  console.log("Event received:", event);
}

client.activate();

// Khi người dùng đăng xuất hoặc trang thực sự không cần kết nối:
// await client.deactivate();
```

Đây là mã dùng trong môi trường JavaScript/browser có bundler và đã cài `@stomp/stompjs`. URL phải đổi sang endpoint thực, `wss` cho môi trường HTTPS. Hàm `handleNotification` chỉ demo log sự kiện; ứng dụng thực phải kiểm tra cấu trúc JSON, độ tin cậy và thứ tự nhận tin.

Giải thích các lựa chọn:

- `heartbeatOutgoing`: client đề xuất khoảng gửi heartbeat; `heartbeatIncoming`: mong đợi khoảng nhận.
- `reconnectDelay` kết hợp `ReconnectionTimeMode.EXPONENTIAL`: chờ ban đầu, tăng thời gian khi lỗi lặp, giới hạn bởi `maxReconnectDelay`; giảm nguy cơ nhiều client cùng thử lại liên tục.
- `onConnect` chạy sau mỗi CONNECTED mới; đặt `subscribe` trong callback để subscription được đăng ký lại.
- `onStompError` xử lý STOMP ERROR frame; không thể coi mọi lỗi là lỗi mạng tạm thời. Nếu xác thực thất bại hoặc tài khoản bị vô hiệu hóa, client nên ngừng thử hoặc yêu cầu đăng nhập mới theo chính sách UI.
- `onWebSocketClose` cho biết transport đóng; reconnect phụ thuộc trạng thái client và cấu hình. Không đặt một `setInterval` retry riêng song song với cơ chế thư viện.

Khi component bị hủy hoặc user logout, gọi `await client.deactivate()` để đóng và dừng vòng reconnect. Tránh tạo nhiều `Client` và giữ chúng hoạt động sau khi rời trang gây subscription trùng và tài nguyên bị giữ.

### 6.3 Dùng token trong CONNECT khi reconnect

Với chiến lược token-based, client cần token hợp lệ cho **mỗi** STOMP CONNECT mới. Nếu token ngắn hạn đã hết, phải làm mới từ luồng xác thực an toàn trước khi reconnect. Thư viện cho phép cập nhật `connectHeaders` hoặc `beforeConnect` tùy API.

Mảnh minh họa:

```javascript
client.beforeConnect = async () => {
  const token = await trustedTokenProvider(); // Hàm ứng dụng cung cấp.
  client.connectHeaders = {
    Authorization: "Bearer " + token
  };
};
```

`trustedTokenProvider` ở đây là **giao diện ứng dụng phải cung cấp**, không phải hàm có sẵn hoặc ví dụ tự chạy độc lập. Nó phải quản lý bí mật an toàn, không log token. Nếu Spring Security đang bật CSRF kiểm tra CONNECT, phải bổ sung CSRF token đúng quy trình xác thực; chỉ đặt Bearer không bảo đảm CONNECT thành công.

Không đưa access token dài hạn vào `brokerURL` query string nếu có cách an toàn hơn: URL có thể bị ghi log ở proxy hoặc hệ thống giám sát. Không lấy token từ input người dùng rồi mặc định tin nội dung chưa xác minh.

### 6.4 Khi nào cần ngừng tự động thử lại?

Các lỗi tạm thời như mất mạng hoặc server bảo trì có thể retry với exponential backoff và jitter. Các lỗi khó có thể phục hồi tự động như thông tin đăng nhập sai, người dùng bị khóa, phiên đã logout hoặc destination không được phép cần biểu diễn lỗi trên UI và dừng retry đến khi trạng thái thay đổi.

Giới hạn thử lại phù hợp UX: ví dụ sau nhiều lần thất bại hiển thị “Mất kết nối, thử lại” và vẫn có hành vi backoff nền khi được phép. Không giả định người dùng luôn online hoặc tab luôn ở foreground; trình duyệt có thể hạn chế timer trong tab nền, vì vậy đo thời gian thực cần chấp nhận sai số.

## 7. Đồng bộ dữ liệu sau reconnect: bảo đảm nào thuộc STOMP?

### 7.1 Heartbeat, receipt, ACK và độ bền message là các khái niệm khác

| Cơ chế | Trả lời câu hỏi | Không bảo đảm |
| --- | --- | --- |
| Heartbeat | Hai phía còn có hoạt động truyền dữ liệu không? | Message đã xử lý thành công hoặc lưu bền |
| STOMP RECEIPT | Broker/đầu nhận đã xử lý frame theo ngữ nghĩa receipt được hỗ trợ chưa? | Nghiệp vụ đã commit database hoặc người dùng đã đọc |
| ACK/NACK | Subscriber có xác nhận xử lý message theo chế độ ack tương ứng không? | “Đúng một lần” cho tác dụng phụ bên ngoài nếu thiếu idempotency |
| Correlation ID | Log/sự kiện thuộc yêu cầu nào? | Tin được bảo mật, đã gửi thành công hay được phân quyền |
| Sequence/cursor | Có sự kiện nào bị bỏ lỡ/trùng thứ tự không? | Tự động đồng bộ lại nếu server không lưu lịch sử |
| Idempotency key | Yêu cầu lặp có thể được xử lý an toàn theo chính sách server không? | Tính nhất quán nếu key bị dùng sai/không được lưu đúng cách |

Với simple broker trong bộ nhớ, client đang mất kết nối thường **không nhận lại** các broadcast trong khoảng offline chỉ vì reconnect. Nếu ứng dụng cần không bỏ sót, phải thiết kế bộ lưu event/history hoặc một message broker hỗ trợ bền vững phù hợp và cơ chế replay.

### 7.2 Mẫu đồng bộ lại có sequence ID

Giả sử server gắn `eventId` tăng dần trong phạm vi từng stream. Client giữ `lastSeenEventId` và sau reconnect:

1. Thiết lập socket và hoàn thành STOMP CONNECT.
2. Đăng ký lại những destination cần thiết.
3. Gọi HTTP API đồng bộ hoặc request replay từ `lastSeenEventId` bằng cơ chế server hỗ trợ.
4. Hợp nhất snapshot/replay với những event nhận trong lúc đồng bộ, loại bỏ trùng theo `eventId` và sắp thứ tự theo contract.
5. Chỉ cập nhật mốc `lastSeenEventId` khi event đã được xử lý/lưu theo tiêu chí client đặt ra.

Cần xử lý **khoảng đua** giữa lúc đăng ký và snapshot; có thể subscribe trước rồi queue sự kiện tạm thời trong khi nạp snapshot theo cursor, hoặc dùng protocol đặc thù của server để lấy snapshot + stream với ranh giới xác định. Không chỉ “refetch một lần” rồi giả định không có message mới trong quá trình đồng bộ.

Với chat gửi tin, dùng `clientMessageId` độc nhất và server-side deduplication để khi client không biết `SEND` trước đó thành công hay chưa, có thể retry mà không tạo hai tin trùng. Nếu message nhạy cảm, xác minh quyền đọc lại trong API replay, không chỉ dựa vào subscription đã qua kiểm tra.

### 7.3 Thận trọng với tính “exactly once”

STOMP chỉ là protocol truyền frame; không tự mang lại exactly-once end-to-end qua nhiều dịch vụ/database/UI. Ngay cả khi broker hỗ trợ ACK và lưu bền, ứng dụng có thể nhận lại sau lỗi giữa xử lý và ACK. Mục tiêu thực tế là **at-least-once + idempotency/deduplication** hoặc cơ chế nghiệp vụ tương đương, tùy yêu cầu.

## 8. Quan sát kết nối ở server: session event và broker availability

Spring phát những sự kiện giúp ứng dụng theo dõi kết nối:

| Event | Khi nào xảy ra? |
| --- | --- |
| `SessionConnectEvent` | Server nhận STOMP CONNECT |
| `SessionConnectedEvent` | Server đã gửi CONNECTED; phiên STOMP được xem là kết nối hoàn tất |
| `SessionSubscribeEvent` | Nhận SUBSCRIBE |
| `SessionUnsubscribeEvent` | Nhận UNSUBSCRIBE |
| `SessionDisconnectEvent` | STOMP session kết thúc; có thể từ DISCONNECT hoặc WebSocket close |
| `BrokerAvailabilityEvent` | Trạng thái sẵn sàng của broker relay thay đổi, thường liên quan system connection |

Một sự kiện `SessionDisconnectEvent` có thể được phát **nhiều hơn một lần cho cùng phiên**, nên mã dọn dẹp phải idempotent.

Ví dụ nhận sự kiện đóng kết nối và ghi log metadata:

**Tệp `StompSessionEvents.java`:**

```java
package com.example.chat;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class StompSessionEvents {

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        SimpMessageHeaderAccessor headers =
                SimpMessageHeaderAccessor.wrap(event.getMessage());
        System.out.println("STOMP connected session="
                + headers.getSessionId());
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        System.out.println("STOMP disconnected session="
                + event.getSessionId());
    }
}
```

Đây là thành phần Spring dùng để minh họa sự kiện; trong sản phẩm thật thay `System.out` bằng logger có giới hạn và dùng operation idempotent khi dọn trạng thái. Một message CONNECTED có thể kèm hoặc không kèm metadata khác; đừng phụ thuộc vào việc đọc cookie/token từ event và không ghi credentials vào log.

Nếu lưu trạng thái presence (“online/offline”), một user có thể có **nhiều session** (nhiều tab/thiết bị). Disconnect của một session không đồng nghĩa toàn bộ user offline. Với hệ thống đa node, cần bộ lưu presence phân tán và TTL/heartbeat ở phạm vi ứng dụng nếu muốn trạng thái toàn hệ thống chính xác.

## 9. Kiểm thử xác thực, phân quyền, heartbeat và reconnect

### 9.1 Ma trận kiểm thử bảo mật

Nên tách kiểm thử theo lớp để thấy lỗi bắt nguồn ở đâu:

| Trường hợp | Kỳ vọng |
| --- | --- |
| HTTP handshake không có user trong chế độ cookie auth | Bị chặn ở HTTP security/handshake |
| Browser Origin ngoài allowlist | Không thể hoàn thành handshake theo policy |
| User hợp lệ, STOMP CONNECT thiếu CSRF khi security yêu cầu | CONNECT bị từ chối; HTTP 101 không đồng nghĩa STOMP session thành công |
| STOMP token sai/đã hết hạn | Authentication interceptor từ chối CONNECT |
| ROLE_USER SEND `/app/echo` | Được chuyển đến controller nếu các điều kiện khác hợp lệ |
| ROLE_USER SEND trực tiếp `/topic/admin/alerts` | Bị chặn, không thể giả danh server phát broadcast |
| ROLE_USER SUBSCRIBE `/topic/admin/**` | Bị chặn |
| ROLE_ADMIN SUBSCRIBE `/topic/admin/**` | Được cho phép nếu đã xác thực và phù hợp role |
| ROLE_USER SUBSCRIBE `/topic/rooms/secret` mà không là thành viên | Bị chặn bởi kiểm tra quyền theo room/user (không chỉ role) |
| User logout rồi reconnect bằng token cũ | Không được quay lại với quyền đã thu hồi |
| Frame CONNECT gửi một user ID tự đặt | Không được ghi đè Principal được xác thực |

Test HTTP handshake và STOMP CONNECT là hai bước riêng: test HTTP có thể nhận status 101 nhưng STOMP CONNECT vẫn gặp ERROR. Một integration test thường dùng một STOMP client thật với server embedded và tài khoản thử nghiệm; không “mock mọi thứ” khi muốn kiểm chứng đường đi giữa HTTP security, channel interceptor và broker.

### 9.2 Test heartbeat khi ít traffic

Dùng hai phía đều hỗ trợ heartbeat, cấu hình khoảng nhỏ **chỉ trong môi trường test** rồi theo dõi:

- CONNECT/CONNECTED thương lượng interval đúng hướng.
- Khi không có frame ứng dụng, heartbeat vẫn được gửi theo interval.
- Khi server/broker ngừng phát dữ liệu, phía client phát hiện sau **ngưỡng dung sai**, không ngay chính xác mili giây đầu tiên.
- Sau phát hiện, ứng dụng đánh dấu disconnected, dừng gửi tác vụ tạm thời và kích hoạt quy trình reconnect.
- Khi reconnect, listener/handler/subscription không được nhân đôi.

Tránh kiểm thử heartbeat chỉ bằng `Thread.sleep(10000)` rồi khẳng định “sau đúng 10 giây sẽ đóng”; scheduler, mạng, hệ điều hành và browser throttling gây sai lệch. Dùng thời hạn tối đa có độ dung sai hợp lý và thu thập signal/connection state.

### 9.3 Test reconnect khi đang có message

Thiết lập một client đã subscribe, gửi vài event có `eventId`, tạm ngắt kết nối trước event tiếp theo, cho client nối lại, rồi phát lại dữ liệu qua API snapshot/replay của ứng dụng. Kiểm tra:

- Client không tồn tại subscription cũ trên session mới nhưng đã **subscribe lại**.
- Event trong khoảng offline được khôi phục bởi cơ chế replay của ứng dụng (nếu có).
- Event nhận trùng được loại bỏ; không có tác dụng phụ hai lần.
- Phân quyền được kiểm tra lại; user bị rút quyền trong lúc offline không thể subscribe hoặc replay dữ liệu cũ.
- Nếu server liên tục từ chối auth, reconnect dừng hoặc yêu cầu đăng nhập theo chính sách.

Với broker relay, test mất broker riêng với test mất WebSocket browser, vì relay system connection và client session có vòng đời khác.

### 9.4 Kiểm thử unit Java cho bộ quyết định retry

Ví dụ một chiến lược tính delay đơn giản, **không thay thế** thư viện @stomp/stompjs nhưng giúp kiểm tra quy tắc backoff:

**Tệp `ReconnectBackoffDemo.java` (Java 17+, không phụ thuộc Spring):**

```java
public class ReconnectBackoffDemo {

    static long retryDelayMillis(int failureCount) {
        long delay = 1000;
        for (int attempt = 1; attempt < failureCount; attempt++) {
            delay = Math.min(delay * 2, 16_000);
        }
        return delay;
    }

    public static void main(String[] args) {
        System.out.println("attempt 1: "
                + retryDelayMillis(1) + " ms");
        System.out.println("attempt 2: "
                + retryDelayMillis(2) + " ms");
        System.out.println("attempt 3: "
                + retryDelayMillis(3) + " ms");
        System.out.println("attempt 6: "
                + retryDelayMillis(6) + " ms");
    }
}
```

Chạy:

```bash
javac ReconnectBackoffDemo.java
java ReconnectBackoffDemo
```

**Stdout chính xác:**

```text
attempt 1: 1000 ms
attempt 2: 2000 ms
attempt 3: 4000 ms
attempt 6: 16000 ms
```

Bản demo bỏ qua jitter, giới hạn số lần thử và độ trễ do browser; những yếu tố đó cần có trong chiến lược reconnect thực khi rất nhiều client cùng bị ngắt, để tránh tạo “thundering herd” lên server.

## 10. Sai lầm phổ biến và cách khắc phục

| Sai lầm | Điều có thể xảy ra | Cách xử lý |
| --- | --- | --- |
| HTTP handshake thành công thì cho mọi frame | User hợp lệ truy cập/tự gửi message trái quyền | Message authorization theo frame/destination, deny-by-default |
| Coi STOMP `login`/`passcode` là xác thực trong Spring | Client có thể giả tên bất kỳ | Dùng HTTP Principal hoặc xác thực CONNECT token thật |
| Chỉ kiểm tra Role mà không kiểm tra room/tenant | User đọc room khác cùng ROLE_USER | Kiểm tra quyền theo đối tượng tại server |
| Cho phép SEND trực tiếp tới `/topic/**` | Client giả danh server broadcast | Deny MESSAGE đến broker destination, chỉ mở `/app/**` cần thiết |
| Cho phép SUBSCRIBE `/topic/private/**` không giới hạn | Lộ dữ liệu nội bộ | Giới hạn destination và kiểm tra membership |
| Dùng wildcard Origin với cookie/session | Mất lớp bảo vệ nguồn trang trình duyệt | Allowlist Origin chặt, xác thực/CSRF tương ứng |
| Tắt CSRF toàn site cho SockJS | Mở lỗ hổng CSRF ở các route khác | Ngoại lệ HTTP rất hẹp và bảo vệ CONNECT |
| Bật heartbeat trên client nhưng không cấu hình server | Client hiểu sai server chết hoặc không detect đúng | Bật heartbeat theo broker, scheduler và thương lượng STOMP |
| Cho rằng heartbeat xác nhận message đã lưu | Thất lạc dữ liệu khi reconnect | Thiết kế ACK/receipt/snapshot/replay phù hợp |
| Broker relay tự reconnect nên browser cũng sẽ reconnect | UI mắc kẹt offline | Thiết lập reconnect trong từng STOMP client |
| Chỉ tạo `subscribe` lần đầu | Sau reconnect không nhận message | Subscribe lại sau mỗi CONNECTED |
| Reconnect liên tục không backoff | Server quá tải khi mất mạng diện rộng | Backoff + jitter + giới hạn, phân biệt lỗi auth |
| Tự động replay mọi SEND sau đứt kết nối | Gây thao tác lặp, trùng tiền/đơn | Idempotency key, tra cứu trạng thái kết quả |
| Xem mỗi user chỉ có một socket | Disconnect một tab làm user bị đánh offline | Theo dõi nhiều session trên mỗi user |
| Dọn phiên theo mỗi `SessionDisconnectEvent` không idempotent | Chạy cleanup lặp, dữ liệu sai | Cleanup an toàn khi nhận nhiều event |
| Dựa vào SimpleBroker để lưu message offline | Mất thông báo giữa lúc không online | Dùng lịch sử/replay hoặc broker có durability phù hợp |
| Ghi token/raw STOMP frame vào log | Rò thông tin xác thực và dữ liệu riêng | Ghi metadata đã lọc, tuyệt đối không log secret |

## 11. Checklist triển khai

Trước khi đưa tính năng WebSocket/STOMP vào môi trường thực:

1. **Xác thực:** xác định HTTP session hay CONNECT token làm nguồn danh tính; kiểm thử danh tính sau reconnect và khi token hết hạn.
2. **Origin/CSRF:** allowlist Origin, hiểu cookie/SameSite/proxy và chính sách CSRF của STOMP CONNECT/SockJS.
3. **Destination:** liệt kê SEND và SUBSCRIBE được phép, từ chối destination không khai báo, kiểm tra quyền tài nguyên riêng.
4. **Heartbeat:** bật scheduler cho simple broker hoặc cấu hình broker relay, thương lượng khoảng thực tế với client, áp dụng dung sai timeout.
5. **Reconnection:** dùng một client instance đúng vòng đời, exponential backoff (có jitter khi cần), resubscribe trong `onConnect` và ngừng retry khi user logout.
6. **Đồng bộ:** lưu cursor/sequence và cung cấp API replay/snapshot nếu không được mất dữ liệu; deduplicate event và dùng idempotency key cho thao tác có tác dụng phụ.
7. **Quan sát:** theo dõi session, connected/disconnected, broker availability, auth failure, retry count, heartbeat-lost, và lý do không nhận message; bảo vệ thông tin cá nhân trong log.
8. **Kiểm thử:** kết hợp test unit matcher/chính sách với integration test HTTP handshake + CONNECT + SUBSCRIBE/SEND + ngắt mạng thật.

Một câu hỏi chẩn đoán hữu ích khi user báo “WebSocket đang connected nhưng không nhận thông báo”: **HTTP socket đã mở hay STOMP CONNECTED rồi? User có đúng Principal? SUBSCRIBE có được phép, đến đúng destination và trên phiên hiện tại? Broker có sẵn sàng? Có event mới hay cần replay sự kiện đã bỏ lỡ?** Trả lời theo từng tầng giúp xác định lỗi chính xác.

## 12. Tài liệu tham khảo

- [Spring Framework — STOMP Authentication](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/authentication.html): HTTP Principal và `login/passcode` của STOMP.
- [Spring Framework — STOMP Token Authentication](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/authentication-token-based.html): xác thực token trong CONNECT bằng `ChannelInterceptor`, thứ tự trước authorization.
- [Spring Security — WebSocket Security](https://docs.spring.io/spring-security/reference/servlet/integrations/websocket.html): `@EnableWebSocketSecurity`, destination matchers, CSRF/same-origin.
- [Spring Framework — STOMP Authorization](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/authorization.html): kiểm tra quyền ở client inbound channel.
- [Spring Framework — Simple Broker](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/handle-simple-broker.html): `TaskScheduler` và heartbeat của broker đơn giản.
- [Spring Framework — Broker Relay](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/handle-broker-relay-configure.html): heartbeat và system connection của external broker.
- [Spring Framework — STOMP Client](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/client.html): `WebSocketStompClient`, heartbeat và receipts.
- [Spring Framework — STOMP Events](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/application-context-events.html): sự kiện connect/disconnect, reconnect trách nhiệm client.
- [@stomp/stompjs — Client API](https://stomp-js.github.io/api-docs/latest/classes/Client.html): reconnect/backoff, heartbeat và vòng đời client.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="websocket-stomp-011">Xử lý ngoại lệ, sự kiện kết nối, giám sát và kiểm thử WebSocket</a>

<details>
<summary>Xem chi tiết</summary>

# Xử lý ngoại lệ, sự kiện kết nối, giám sát và kiểm thử WebSocket trong Spring

## 1. Mục tiêu và phạm vi

Một WebSocket endpoint không chỉ cần nhận và gửi message. Để vận hành đáng tin cậy, ứng dụng phải xác định **lỗi xảy ra ở giai đoạn nào**, biết **khi nào kết nối/STOMP session thực sự tồn tại**, **dọn trạng thái khi kết nối đóng**, có chỉ số quan sát được và có bài kiểm thử cả đường truyền.

Trong Spring Framework 6.x với Java 17+, bài học phân biệt hai mô hình:

1. **Raw WebSocket trên Servlet stack**: `WebSocketHandler`, `TextWebSocketHandler`, `WebSocketSession` và các callback như `handleTransportError`, `afterConnectionClosed`.
2. **STOMP over WebSocket**: `@EnableWebSocketMessageBroker`, `@MessageMapping`, `@MessageExceptionHandler`, sự kiện `SessionConnectEvent`, `SessionConnectedEvent`, `SessionDisconnectEvent`, `BrokerAvailabilityEvent`; kèm `WebSocketMessageBrokerStats` và kiểm thử STOMP.

Đến cuối bài, bạn có thể phân biệt lỗi handshake, lỗi transport, lỗi xử lý message và lỗi broker; viết handler/lắng nghe sự kiện theo vòng đời; đo kết nối đang mở, lỗi và lưu lượng; kiểm thử raw WebSocket với WebSocket client thật; và xây chiến lược kiểm thử STOMP có giới hạn thời gian rõ ràng.

Các ví dụ chính dùng **Spring Boot 3.3.x + Java 17**, thuộc **Servlet / Spring MVC stack**. API reactive WebFlux WebSocket nằm ở package khác và có callback khác; không copy ví dụ Servlet `WebSocketHandler` trực tiếp sang WebFlux.

## 2. Bốn nhóm lỗi thường bị nhầm với nhau

Một kết nối có thể lỗi trước hoặc sau khi protocol được nâng cấp. Chọn đúng tầng xử lý quan trọng hơn việc đặt một khối `try/catch` thật rộng.

| Giai đoạn | Ví dụ | Nơi xử lý/quan sát | Tín hiệu tới client |
| --- | --- | --- | --- |
| HTTP handshake, trước upgrade | Sai URL, thiếu xác thực, origin bị chặn | Servlet filter, Spring Security, `HandshakeInterceptor`, `HandshakeHandler` | HTTP 400/401/403/404 hoặc lỗi handshake |
| Transport WebSocket, sau upgrade | Socket đứt, lỗi đọc/ghi, client đóng tab | `handleTransportError`, `afterConnectionClosed` | Close frame nếu còn gửi được, hoặc mất kết nối |
| Raw message / nghiệp vụ | Lệnh không hợp lệ, payload vượt giới hạn, lỗi service | `handleTextMessage`, validation, logic service | Message lỗi do ứng dụng định nghĩa hoặc close code |
| STOMP message / broker | Payload không hợp lệ, destination không được phép, broker mất kết nối | `@MessageExceptionHandler`, `ChannelInterceptor`, broker events/error handler | STOMP message đến user, STOMP `ERROR` frame hoặc disconnect, tùy loại lỗi |

**Điểm mốc:** HTTP status được trả **khi chưa nâng cấp**. Sau HTTP `101 Switching Protocols`, không còn một HTTP response riêng cho từng WebSocket frame. Vì thế, không “trả HTTP 400” từ `handleTextMessage` hay `@MessageMapping`. Nếu lỗi nghiệp vụ có thể phục hồi, nên chuẩn hóa message lỗi ở tầng ứng dụng; nếu lỗi protocol/chính sách nghiêm trọng, đóng session đúng ngữ nghĩa.

### 2.1. Close code WebSocket

| Code | Ý nghĩa thực tế |
| --- | --- |
| 1000 | Đóng bình thường |
| 1001 | Đầu bên kia rời đi/shutdown |
| 1002 | Lỗi protocol |
| 1003 | Không chấp nhận loại dữ liệu |
| 1008 | Vi phạm chính sách |
| 1009 | Message quá lớn |
| 1011 | Server gặp lỗi bất ngờ |

Code **1006 là trạng thái quan sát kết nối đóng bất thường**, không được gửi trong WebSocket close frame. Tương tự, các code dự trữ không được tự ý dùng làm mã ứng dụng. Một transport error có thể không gửi được close frame, nên client chỉ quan sát mất kết nối chứ không nhất thiết nhận 1011.

## 3. Raw WebSocket: callback xử lý lỗi và vòng đời

Spring Servlet định nghĩa `org.springframework.web.socket.WebSocketHandler`:

```java
void afterConnectionEstablished(WebSocketSession session) throws Exception;
void handleMessage(WebSocketSession session, WebSocketMessage<?> message)
        throws Exception;
void handleTransportError(WebSocketSession session, Throwable exception)
        throws Exception;
void afterConnectionClosed(WebSocketSession session, CloseStatus status)
        throws Exception;
boolean supportsPartialMessages();
```

Đây là **chữ ký API để giải thích**, không phải class Java tự chạy. Khi xử lý text, kế thừa `org.springframework.web.socket.handler.TextWebSocketHandler` và override `handleTextMessage(...)`. Lớp cơ sở hỗ trợ callback lifecycle mặc định.

### 3.1. Các callback cần hiểu chính xác

- `afterConnectionEstablished`: Spring gọi khi handshake thành công và session sẵn sàng. Dùng để đăng ký session, tăng gauge số kết nối, gửi lời chào khi cần.
- `handleTextMessage`: xử lý payload text của **một session cụ thể**. Validate dữ liệu, gọi service, rồi gửi response đúng schema. Không nên để một exception dự kiến (như input sai) lan truyền thành lỗi server.
- `handleTransportError`: nhận lỗi từ tầng truyền tải; có thể log loại lỗi, tăng counter, thử đóng session nếu còn mở. Nó **không phải** callback tổng quát chắc chắn bắt mọi exception nghiệp vụ hay mọi thất bại mạng.
- `afterConnectionClosed`: xóa session khỏi registry và hủy tài nguyên liên quan. Không nên gửi message mới tại đây.
- `supportsPartialMessages`: liên quan cách handler hỗ trợ partial WebSocket messages; không đồng nghĩa STOMP message fragmentation.

Một ứng dụng có nhiều client có thể chia sẻ **một handler bean singleton**. Không lưu `private WebSocketSession currentUserSession;` trong singleton để đại diện tất cả client. Một số callback đóng/lỗi có thể đến trong các tình huống chồng chéo; các thao tác dọn dẹp phải **idempotent**, chẳng hạn dùng `Set.remove(sessionId)` rồi chỉ giảm bộ đếm nếu phần tử thực sự tồn tại.

### 3.2. Lỗi dự kiến và lỗi bất ngờ

Ví dụ nguyên tắc trong subclass:

```java
@Override
protected void handleTextMessage(WebSocketSession session,
                                 TextMessage message) throws Exception {
    String text = message.getPayload();

    if (text.length() > 1024) {
        session.close(CloseStatus.TOO_BIG_TO_PROCESS);
        return;
    }

    if (!"PING".equals(text)) {
        session.sendMessage(new TextMessage("ERROR:BAD_COMMAND"));
        return;
    }

    session.sendMessage(new TextMessage("PONG"));
}
```

Ở đây `ERROR:BAD_COMMAND` là **quy ước ứng dụng**, không phải WebSocket ERROR frame chuẩn. Payload quá lớn là chính sách khác, đóng với 1009. Nếu backend đòi hỏi JSON, nên thiết kế response dạng mã lỗi ổn định (ví dụ `{"type":"error","code":"INVALID_COMMAND"}`) và không gửi stack trace ra client.

Nếu một exception không được xử lý thoát khỏi handler, Spring có lớp hạ tầng như `ExceptionWebSocketHandlerDecorator` để hỗ trợ đóng session trong trường hợp lỗi. Không dựa vào hành vi đóng lỗi mặc định để truyền lỗi nghiệp vụ cho client; tự định nghĩa cách trả lỗi có kiểm soát và thu thập log/metrics ở server.

### 3.3. Không gửi đồng thời từ nhiều thread một cách tùy ý

Với Servlet WebSocket dựa trên Jakarta WebSocket, phần gửi qua một session thường cần serialization; nhiều producer cùng `session.sendMessage(...)` có thể xung đột. `ConcurrentWebSocketSessionDecorator` hỗ trợ giới hạn thời gian gửi và buffer:

```java
WebSocketSession safe =
        new ConcurrentWebSocketSessionDecorator(
                session, 10_000, 64 * 1024);
```

Đây là snippet: import `org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator`. Trong hệ thống broadcast, tạo và quản lý **một wrapper nhất quán cho mỗi session** rồi gửi qua wrapper đó. Không bọc mới cho từng message, vì như vậy không bảo đảm tất cả producer chia sẻ cùng cơ chế gửi.

## 4. STOMP: lỗi ở @MessageMapping và lỗi giao thức là hai chuyện khác

STOMP là giao thức nhắn tin trên WebSocket. Phân luồng thường là:

```text
Client WebSocket handshake /stomp (HTTP 101)
  -> STOMP CONNECT -> CONNECTED
  -> STOMP SUBSCRIBE /topic/...
  -> STOMP SEND /app/...
  -> clientInboundChannel
  -> @MessageMapping controller
  -> /topic/... hoặc /user/queue/...
  -> brokerChannel -> clientOutboundChannel
  -> STOMP MESSAGE tới subscriber
```

### 4.1. @MessageExceptionHandler

Nếu một method `@MessageMapping` ném exception nghiệp vụ, Spring Messaging hỗ trợ `@MessageExceptionHandler`, cùng ý tưởng với `@ExceptionHandler` trên HTTP controller nhưng vận hành ở **message handling**, không tự tạo HTTP response:

```java
@Controller
class PingController {
    record PingRequest(String text) {}
    record ErrorReply(String code) {}

    @MessageMapping("/ping")
    @SendTo("/topic/pongs")
    String ping(PingRequest body) {
        if (body == null || body.text() == null ||
                body.text().isBlank()) {
            throw new IllegalArgumentException("text required");
        }
        return "PONG:" + body.text();
    }

    @MessageExceptionHandler(IllegalArgumentException.class)
    @SendToUser(value = "/queue/errors", broadcast = false)
    ErrorReply badInput(IllegalArgumentException exception) {
        return new ErrorReply("INVALID_TEXT");
    }
}
```

Đoạn trên minh họa **đặt trong controller và có imports thích hợp**. Client SEND đến `/app/ping`. Người đăng ký `/topic/pongs` có thể nhận message thành công. Khi body sai, client liên quan đăng ký `/user/queue/errors` để nhận mã lỗi. `broadcast=false` tránh phát cùng lỗi đến mọi session khác của một user có nhiều kết nối; với anonymous session, nó cũng hữu ích để định tuyến về session liên quan.

`@MessageExceptionHandler` có thể được khai báo cùng controller hoặc đặt trong class `@ControllerAdvice` để áp dụng rộng hơn. Không gửi raw exception message/stack trace cho browser vì có thể lộ thông tin cấu trúc dịch vụ hay dữ liệu nhạy cảm. Việc exception được xử lý không có nghĩa phải tự đóng WebSocket.

### 4.2. STOMP ERROR frame và StompSubProtocolErrorHandler

Lỗi parse STOMP frame, lỗi protocol, lỗi ở kênh xử lý hoặc lỗi hạ tầng khác có thể đi theo đường **STOMP ERROR** riêng, không đi qua một `@MessageExceptionHandler` của controller. Spring cung cấp `StompSubProtocolErrorHandler` để tùy biến thông báo lỗi ở mức subprotocol. Có thể cấu hình thông qua `StompEndpointRegistry.setErrorHandler(...)` trong cấu hình broker, ví dụ với implementation đã được thiết kế để không lộ chi tiết nội bộ.

STOMP `ERROR` frame có thể dẫn tới đóng kết nối. Client nên phân biệt:

- **Message lỗi nghiệp vụ** ở `/user/queue/errors`: ứng dụng có thể tiếp tục giữ kết nối và xử lý request khác.
- **STOMP ERROR frame**: lỗi subprotocol/broker, có thể kèm disconnect; cần kiểm tra kết nối và chính sách reconnect.
- **WebSocket transport close/error**: có thể không hề có STOMP ERROR frame.

### 4.3. BrokerAvailabilityEvent và gửi chủ động

Nếu dùng broker relay (ví dụ broker ngoài ứng dụng), một `BrokerAvailabilityEvent` cho biết trạng thái broker có sẵn/không có sẵn để gửi thông điệp. Broker đơn giản nội bộ thường available ngay sau khởi động; broker relay có thể mất kết nối và khôi phục.

Thành phần gọi `SimpMessagingTemplate.convertAndSend(...)` nên lưu trạng thái availability khi thích hợp, nhưng vẫn cần xử lý `MessageDeliveryException`/lỗi gửi: một event availability chỉ là trạng thái quan sát tại thời điểm nhất định, không bảo đảm lần gửi sau sẽ luôn thành công.

## 5. Sự kiện kết nối STOMP: mốc nào mới được coi là online?

Các sự kiện sau đến từ Spring ApplicationContext khi dùng STOMP-over-WebSocket:

| Event | Thời điểm | Lưu ý |
| --- | --- | --- |
| `SessionConnectEvent` | Server nhận STOMP `CONNECT` | Đây **chưa** phải xác nhận session STOMP đã hoàn tất |
| `SessionConnectedEvent` | Broker phản hồi STOMP `CONNECTED` | Có thể tính là STOMP session đã kết nối |
| `SessionSubscribeEvent` | Client gửi `SUBSCRIBE` | Không đồng nghĩa hệ thống đã xác nhận mọi bước của subscription |
| `SessionUnsubscribeEvent` | Client gửi `UNSUBSCRIBE` | Dùng cập nhật subscription tracking nếu cần |
| `SessionDisconnectEvent` | STOMP session chấm dứt | Có thể được phát **hơn một lần** với cùng session; phải idempotent |
| `BrokerAvailabilityEvent` | Broker chuyển trạng thái available/unavailable | Hữu ích khi có broker relay hoặc gửi chủ động |

**Hai tầng kết nối:** WebSocket HTTP handshake thành công chỉ nói kết nối WebSocket đã mở. Client còn cần gửi STOMP `CONNECT` và nhận `CONNECTED` trước khi nói một STOMP session đã sẵn sàng. Khi broker gặp vấn đề, có thể có socket sống nhưng STOMP messaging chưa sử dụng được.

### 5.1. Lắng nghe bằng @EventListener

```java
@Component
class StompSessions {
    private final Set<String> online = ConcurrentHashMap.newKeySet();

    @EventListener
    public void connected(SessionConnectedEvent event) {
        String id = SimpMessageHeaderAccessor
                .wrap(event.getMessage())
                .getSessionId();
        if (id != null) {
            online.add(id);
        }
    }

    @EventListener
    public void disconnected(SessionDisconnectEvent event) {
        online.remove(event.getSessionId());
    }
}
```

Đây là **snippet**, cần imports từ `java.util.Set`, `java.util.concurrent.ConcurrentHashMap`, `org.springframework.context.event.EventListener`, `org.springframework.stereotype.Component`, `org.springframework.messaging.simp.SimpMessageHeaderAccessor`, `org.springframework.web.socket.messaging.SessionConnectedEvent` và `SessionDisconnectEvent`.

Tập hợp session ID cho phép xử lý disconnect lặp một cách an toàn. Nếu muốn cập nhật một counter `disconnect_total` đúng “số session đã online rồi đóng”, chỉ tăng sau khi `online.remove(id)` trả về `true`. Với session thất bại trước STOMP CONNECTED, loại này chưa được thêm vào tập; nên đo thất bại handshake/connect riêng.

**Không dùng sự kiện kết nối để tự suy ra quyền truy cập:** việc session tồn tại không có nghĩa user được phép đọc tất cả destinations. Spring Security, interceptor và kiểm tra nghiệp vụ cần áp dụng riêng.

### 5.2. Mức độ đảm bảo và đa instance

Danh sách `online` trong RAM chỉ phản ánh session của **instance đang chạy**. Khi ứng dụng triển khai nhiều node, tổng số kết nối và presence toàn hệ thống cần cơ chế phân tán hoặc tổng hợp metrics qua nhiều instance. SessionDisconnectEvent có thể phát lặp; event listener cũng không phải nguồn chân lý tuyệt đối cho việc user đang online nếu mạng đứt và timeout chưa phát hiện.

Spring simple broker thích hợp bắt đầu phát triển nhưng không có đầy đủ chức năng broker ngoài, không phù hợp để mở rộng cụm server theo cách relay multi-node. Với yêu cầu routing xuyên node, cân nhắc full broker relay và thiết kế reconnect/authorization rõ ràng.

## 6. Giám sát WebSocket: đo gì và ở tầng nào?

Không có một metric duy nhất chứng minh dịch vụ WebSocket đang khỏe. Ít nhất cần theo dõi:

| Tín hiệu | Điều nó trả lời | Cách diễn giải |
| --- | --- | --- |
| Số raw WebSocket sessions đang mở | Có bao nhiêu kết nối transport hiện tại? | Gauge tăng/giảm theo lifecycle |
| Số STOMP sessions connected | Có bao nhiêu session STOMP đã nhận CONNECTED? | Có thể khác số socket đang mở |
| Kết nối/đóng bất thường theo thời gian | Mạng/proxy/broker có mất ổn định? | Counter/rate; phân loại close status |
| Lỗi transport và lỗi ứng dụng | Lỗi hạ tầng hay dữ liệu? | Tách lỗi transport/validation/server |
| Inbound/outbound messages | Lưu lượng theo giây và theo loại | Counter; không gắn payload làm metric label |
| Kích thước message, send buffer và send time | Client chậm hoặc quá tải? | Xem phân phối, giới hạn configured |
| STOMP CONNECT/CONNECTED/DISCONNECT và subscriptions | Client hoạt động ở tầng STOMP ra sao? | DISCONNECT count có thể ít hơn do rớt mạng |
| Broker relay availability và pending tasks | Broker hoặc executor có nghẽn? | Quan sát với queue/executor stats |

### 6.1. WebSocketMessageBrokerStats cho STOMP

Khi dùng `@EnableWebSocketMessageBroker`, Spring tạo bean `org.springframework.web.socket.config.WebSocketMessageBrokerStats`. Theo cấu hình mặc định, bean này tổng hợp và **log thông tin thống kê ở mức INFO khoảng mỗi 30 phút**: client WebSocket/SockJS sessions, STOMP frames, broker relay khi có, inbound/outbound executor và các giới hạn/lỗi kết nối.

Đây là thống kê dành cho **STOMP message broker infrastructure**, không tự động thay thế metrics raw `WebSocketHandler` do ứng dụng tự xây. Có thể xem theo kiểu:

```java
@Component
class BrokerStatsReader {
    private final WebSocketMessageBrokerStats stats;

    BrokerStatsReader(WebSocketMessageBrokerStats stats) {
        this.stats = stats;
    }

    void inspect() {
        String summary = stats.toString();
        // Chuyển summary sang log nội bộ hoặc hệ thống giám sát phù hợp.
    }
}
```

Snippet này giải thích cách lấy bean; không tự gọi `inspect()` nếu không có scheduler/caller. Trong Spring 6.2, nhiều structured getters thay thế các API thống kê chuỗi đã deprecated; nên kiểm tra Javadoc đúng phiên bản nếu tích hợp dashboard tự động. BrokerStats không phải API phân tán: nếu có nhiều application instances, mỗi instance có số liệu riêng.

### 6.2. Spring Boot Actuator và Micrometer

Nếu bổ sung `spring-boot-starter-actuator`, bạn có thể đăng ký counters và gauges cho logic ứng dụng, rồi xem bằng endpoint metrics hoặc hệ thống thu thập được cấu hình. Ví dụ thiết kế metric:

```text
demo.ws.raw.active              gauge  - raw sessions hiện đang mở
demo.ws.raw.messages            counter - text messages đã nhận
demo.ws.raw.transport.errors    counter - lỗi transport quan sát được
demo.ws.stomp.active            gauge  - STOMP CONNECTED chưa disconnect
demo.ws.stomp.connected         counter - số session CONNECTED
demo.ws.stomp.disconnected      counter - số session CONNECTED đã đóng
demo.ws.stomp.subscriptions     counter - SUBSCRIBE frame đã nhận
```

**Quy tắc cardinality:** không đưa `sessionId`, `userId`, room ID tự do, token, message text hoặc path có biến vô hạn vào metric tag. Những giá trị này tạo quá nhiều time series, tốn bộ nhớ và có thể làm rò dữ liệu. Metric nên có tag hữu hạn như `protocol=raw|stomp`, `result=success|error`, `reason=normal|transport|timeout`.

**Phân biệt:** metric counter thường tăng kể cả khi người dùng reconnect; gauge thể hiện trạng thái ở thời điểm đọc. Khi application restart, số counter của instance bắt đầu lại từ 0, nên dashboard cần rate/aggregation đúng.

### 6.3. Structured logging và correlation

Một log hữu ích thường chứa **thời gian, loại event, session correlation ID nội bộ, loại close code, exception class, thời lượng xử lý, broker status**. Không log cookie, STOMP authorization headers, personal data hoặc toàn bộ nội dung private chat.

Các thread khác nhau có thể xử lý message của cùng WebSocket session. Không giả định `ThreadLocal` hay MDC của HTTP handshake tự truyền qua mọi callback/executor. Nếu cần correlation, truyền context rõ ràng qua message headers/attributes hoặc một interceptor có chính sách xóa MDC để tránh lây sang request khác.

## 7. Ví dụ chạy được: raw + STOMP + sự kiện + Micrometer metrics

Demo sau gồm hai endpoint trên cùng ứng dụng Servlet:

- `ws://localhost:8080/ws/raw`: raw text WebSocket, nhận `PING` trả `PONG`, input khác trả `ERROR:BAD_COMMAND`.
- `ws://localhost:8080/stomp`: STOMP endpoint. Client subscribe `/topic/pongs`, gửi JSON `{"text":"hello"}` tới `/app/ping` và nhận JSON reply. Nếu thiếu text, `@MessageExceptionHandler` gửi error về `/user/queue/errors`.

Các phần đều dùng Java 17, Spring Boot 3.3.x. Demo **mở cho local để học**, chưa có authentication/authorization nên không nên public trực tiếp ra Internet.

### 7.1. Cấu trúc

```text
websocket-observation-demo/
├── build.gradle
└── src/
    ├── main/
    │   ├── java/demo/ws/
    │   │   ├── DemoApplication.java
    │   │   ├── RawHandler.java
    │   │   ├── RawConfig.java
    │   │   ├── StompConfig.java
    │   │   ├── PingController.java
    │   │   └── StompMonitor.java
    │   └── resources/
    │       ├── application.properties
    │       └── static/index.html
    └── test/
        └── java/demo/ws/RawWebSocketIntegrationTest.java
```

### 7.2. build.gradle

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.6'
    id 'io.spring.dependency-management' version '1.1.6'
}

group = 'demo'
version = '1.0.0'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-websocket'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

`spring-boot-starter-websocket` cung cấp Spring MVC WebSocket/STOMP và embedded Servlet container. `spring-boot-starter-actuator` cung cấp Micrometer và điểm truy vấn metrics; `spring-boot-starter-test` cung cấp JUnit 5 / Spring Boot test. Mọi dependency đều được Spring Boot quản lý phiên bản để tránh lẫn Spring 5/6 hoặc `javax.websocket` với `jakarta.websocket`.

### 7.3. DemoApplication.java

```java
package demo.ws;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

Tất cả class còn lại cùng package `demo.ws` để được component scan.

### 7.4. RawHandler.java: phân biệt lỗi nghiệp vụ và transport

```java
package demo.ws;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class RawHandler extends TextWebSocketHandler {
    private final Set<String> live = ConcurrentHashMap.newKeySet();
    private final Counter messages;
    private final Counter transportErrors;

    public RawHandler(MeterRegistry metrics) {
        Gauge.builder("demo.ws.raw.active", live, Set::size)
                .register(metrics);
        this.messages = Counter.builder("demo.ws.raw.messages")
                .register(metrics);
        this.transportErrors =
                Counter.builder("demo.ws.raw.transport.errors")
                        .register(metrics);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        live.add(session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session,
                                     TextMessage message) throws Exception {
        messages.increment();
        String text = message.getPayload();

        if (text.length() > 1024) {
            session.close(CloseStatus.TOO_BIG_TO_PROCESS);
            return;
        }

        if (!"PING".equals(text)) {
            session.sendMessage(new TextMessage("ERROR:BAD_COMMAND"));
            return;
        }

        session.sendMessage(new TextMessage("PONG"));
    }

    @Override
    public void handleTransportError(WebSocketSession session,
                                     Throwable cause) throws Exception {
        transportErrors.increment();
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session,
                                      CloseStatus status) {
        live.remove(session.getId());
    }
}
```

Handler không giữ session của một user trong field chung; `live` là set các ID đang mở. `remove` idempotent khi callback close đến sau lỗi. Đoạn raw demo chỉ có một nguồn gửi ngay trong callback; khi chuyển thành hệ thống broadcast đa luồng, cần thêm bảo vệ concurrent send và buffer.

Giới hạn `text.length() > 1024` dùng số UTF-16 code units để minh họa. Trong hệ thống thật cần cấu hình giới hạn **bytes, frame và buffer** ở container, kiểm tra kích thước payload trước khi xử lý và đặt các giới hạn tài nguyên theo nhu cầu. Server có thể từ chối message trước khi callback này được gọi.

### 7.5. RawConfig.java: đăng ký raw WebSocket

```java
package demo.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class RawConfig implements WebSocketConfigurer {
    private final RawHandler handler;

    public RawConfig(RawHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/raw");
    }
}
```

`@EnableWebSocket` + `WebSocketConfigurer` dành cho raw Servlet WebSocket. Đây không phải `@MessageMapping` và cũng không phát các `SessionConnectedEvent` ở tầng STOMP.

### 7.6. StompConfig.java: endpoint STOMP và giới hạn transport

```java
package demo.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker
public class StompConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/stomp");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.setApplicationDestinationPrefixes("/app");
        config.enableSimpleBroker("/topic", "/queue");
    }

    @Override
    public void configureWebSocketTransport(
            WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(64 * 1024);
        registration.setSendBufferSizeLimit(512 * 1024);
        registration.setSendTimeLimit(15_000);
    }
}
```

`setMessageSizeLimit` điều chỉnh giới hạn inbound **STOMP subprotocol message** sau reassembly. `setSendBufferSizeLimit`/`setSendTimeLimit` giúp hạn chế client nhận chậm. Những cấu hình này thuộc STOMP transport registration; không tự áp dụng mọi giới hạn cho raw `RawHandler`.

`enableSimpleBroker` đủ cho ví dụ một instance; không hỗ trợ đầy đủ receipt/ack hay mô hình broker cluster. Dùng external broker relay khi cần chức năng và quy mô tương ứng.

### 7.7. PingController.java: gửi thành công và lỗi nghiệp vụ

```java
package demo.ws;

import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

@Controller
public class PingController {
    public record PingRequest(String text) {}
    public record PingReply(String text) {}
    public record ErrorReply(String code) {}

    @MessageMapping("/ping")
    @SendTo("/topic/pongs")
    public PingReply onPing(PingRequest request) {
        String text = request == null ? null : request.text();
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text is required");
        }
        return new PingReply("PONG:" + text);
    }

    @MessageExceptionHandler(IllegalArgumentException.class)
    @SendToUser(value = "/queue/errors", broadcast = false)
    public ErrorReply onValidationError(IllegalArgumentException error) {
        return new ErrorReply("INVALID_TEXT");
    }
}
```

Với `@MessageMapping("/ping")` và prefix `/app`, client SEND tới `/app/ping`. Một request hợp lệ tạo `PingReply` tại `/topic/pongs`. Request rỗng ném exception được `@MessageExceptionHandler` chuyển thành response có schema ổn định để gửi đến session liên quan qua user destination `/user/queue/errors`. Đây là **lỗi nghiệp vụ**, không tự động đồng nghĩa đóng WebSocket.

Spring dùng message converter phù hợp (mặc định với Jackson trên classpath) để chuyển JSON payload sang record `PingRequest` và serialize record response. Nếu có lỗi decode trước khi gọi method, loại exception/tầng xử lý có thể khác: phải kiểm thử thực tế với bad JSON.

### 7.8. StompMonitor.java: đếm kết nối đúng mốc và an toàn với disconnect lặp

```java
package demo.ws;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

@Component
public class StompMonitor {
    private final Set<String> connected = ConcurrentHashMap.newKeySet();
    private final Counter connectedTotal;
    private final Counter disconnectedTotal;
    private final Counter subscribeTotal;
    private final Counter connectAttempts;

    public StompMonitor(MeterRegistry metrics) {
        Gauge.builder("demo.ws.stomp.active", connected, Set::size)
                .register(metrics);
        this.connectedTotal = Counter.builder("demo.ws.stomp.connected")
                .register(metrics);
        this.disconnectedTotal =
                Counter.builder("demo.ws.stomp.disconnected")
                        .register(metrics);
        this.subscribeTotal =
                Counter.builder("demo.ws.stomp.subscriptions")
                        .register(metrics);
        this.connectAttempts =
                Counter.builder("demo.ws.stomp.connect.attempts")
                        .register(metrics);
    }

    @EventListener
    public void onConnect(SessionConnectEvent event) {
        connectAttempts.increment();
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        String sessionId = SimpMessageHeaderAccessor
                .wrap(event.getMessage()).getSessionId();
        if (sessionId != null && connected.add(sessionId)) {
            connectedTotal.increment();
        }
    }

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        subscribeTotal.increment();
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        if (connected.remove(event.getSessionId())) {
            disconnectedTotal.increment();
        }
    }
}
```

Chỉ tăng `connectedTotal` khi session ID mới được thêm, và chỉ tăng `disconnectedTotal` khi ID thực sự được xóa. Nhờ đó, hai `SessionDisconnectEvent` cho cùng session không làm bộ đếm âm hoặc tăng số đóng hai lần.

Nếu muốn đo handshake failures hoặc STOMP CONNECT failures, bổ sung metric ở các tầng liên quan; `connectedTotal - disconnectedTotal` không phải bộ đếm chính xác của mọi socket, đặc biệt khi session chưa bao giờ đạt CONNECTED hoặc ứng dụng vừa restart.

### 7.9. application.properties

```properties
server.port=8080
management.endpoints.web.exposure.include=health,info,metrics
management.endpoint.health.show-details=never
```

Bài demo mở `/actuator/metrics` để xem metric; production phải giới hạn mạng, xác thực/phân quyền hoặc đặt actuator lên management port và kiểm soát endpoint. Đừng public toàn bộ actuator chỉ để thuận tiện debug.

### 7.10. static/index.html: thử cả raw và STOMP từ browser

Trang sau dùng WebSocket API của trình duyệt cho **raw** và minh họa **STOMP frames cơ bản** mà không phụ thuộc CDN. Phần STOMP manual chỉ dành cho việc học trên local; client production nên dùng thư viện STOMP có parser, heartbeat và reconnect đầy đủ.

```html
<!doctype html>
<html lang="vi">
<head>
  <meta charset="utf-8">
  <title>WebSocket Observability Demo</title>
</head>
<body>
  <h1>Raw WebSocket + STOMP</h1>
  <button id="rawConnect">Connect raw</button>
  <button id="rawPing">Send raw PING</button>
  <button id="stompConnect">Connect STOMP</button>
  <button id="stompPing">Send STOMP ping</button>
  <button id="stompBad">Send invalid STOMP ping</button>
  <pre id="log"></pre>

  <script>
    const logBox = document.querySelector("#log");
    const protocol = location.protocol === "https:" ? "wss:" : "ws:";
    const base = protocol + "//" + location.host;
    const log = value => { logBox.textContent += value + "\n"; };
    let raw = null;
    let stomp = null;
    let stompReady = false;

    document.querySelector("#rawConnect").onclick = () => {
      raw = new WebSocket(base + "/ws/raw");
      raw.onopen = () => log("RAW OPEN");
      raw.onmessage = event => log("RAW " + event.data);
      raw.onerror = () => log("RAW ERROR");
      raw.onclose = event => log("RAW CLOSE " + event.code);
    };

    document.querySelector("#rawPing").onclick = () => {
      if (raw && raw.readyState === WebSocket.OPEN) {
        raw.send("PING");
      }
    };

    function frame(command, headers, body = "") {
      const lines = [command];
      for (const [key, value] of Object.entries(headers)) {
        lines.push(key + ":" + value);
      }
      return lines.join("\n") + "\n\n" + body + "\0";
    }

    document.querySelector("#stompConnect").onclick = () => {
      stompReady = false;
      stomp = new WebSocket(base + "/stomp", ["v12.stomp", "v11.stomp"]);
      stomp.onopen = () => {
        stomp.send(frame("CONNECT",
          {"accept-version": "1.2,1.1", host: location.hostname}));
      };
      stomp.onmessage = event => {
        const text = String(event.data);
        log("STOMP " + text.replaceAll("\0", "\\0"));
        if (text.startsWith("CONNECTED\n")) {
          stomp.send(frame("SUBSCRIBE",
            {id: "pongs", destination: "/topic/pongs"}));
          stomp.send(frame("SUBSCRIBE",
            {id: "errors", destination: "/user/queue/errors"}));
          stompReady = true;
          log("STOMP SUBSCRIBE sent");
        }
      };
      stomp.onerror = () => log("STOMP ERROR");
      stomp.onclose = event => {
        stompReady = false;
        log("STOMP CLOSE " + event.code);
      };
    };

    function sendPing(text) {
      if (!stompReady || stomp.readyState !== WebSocket.OPEN) {
        log("STOMP chưa CONNECTED");
        return;
      }
      stomp.send(frame("SEND",
        {destination: "/app/ping", "content-type": "application/json"},
        JSON.stringify({text})));
    }

    document.querySelector("#stompPing").onclick =
      () => sendPing("hello");
    document.querySelector("#stompBad").onclick =
      () => sendPing("");
  </script>
</body>
</html>
```

Lưu ý: script manual trên giả định mỗi `onmessage` có thể được đọc như một frame đơn giản để demo. STOMP thực tế cần parser xử lý phân mảnh, nhiều frames và các quy tắc escaping/heartbeat. Simple broker không hỗ trợ receipts đầy đủ, nên `SUBSCRIBE sent` **không phải xác nhận subscriber đã sẵn sàng**; khi kiểm tra thủ công, nhấn gửi sau khi lệnh subscribe đã được hệ thống xử lý. Muốn test chắc chắn, dùng client STOMP chuyên dụng hoặc thiết kế cơ chế ứng dụng xác nhận subscription.

### 7.11. Chạy và quan sát kết quả

Từ thư mục chứa `build.gradle`, với JDK 17+ và Gradle tương thích:

```bash
gradle bootRun
```

Nếu dự án có Gradle Wrapper, dùng `./gradlew bootRun` (Unix/macOS) hoặc `gradlew.bat bootRun` (Windows).

Mở `http://localhost:8080/`:

1. Nhấn **Connect raw**, rồi **Send raw PING**. Phần log có `RAW OPEN` và `RAW PONG`.
2. Nhấn **Connect STOMP**; sau frame `CONNECTED` và subscribe, nhấn **Send STOMP ping**. Trong STOMP MESSAGE tại `/topic/pongs`, body JSON dự kiến `{"text":"PONG:hello"}`.
3. Nhấn **Send invalid STOMP ping**; khi subscription user destination đã sẵn sàng, body lỗi dự kiến `{"code":"INVALID_TEXT"}` gửi tới `/user/queue/errors`.
4. Kiểm tra `/actuator/metrics/demo.ws.raw.active`, `/actuator/metrics/demo.ws.raw.messages`, `/actuator/metrics/demo.ws.stomp.active` và `/actuator/metrics/demo.ws.stomp.subscriptions` bằng browser hoặc curl.

Đây là nội dung message/metric dự kiến sau thao tác, **không phải chuỗi stdout cố định**: event timing, metrics format, live session counts và các log bootstrap của Spring Boot thay đổi theo thứ tự client kết nối/đóng. Không suy đoán mọi run sẽ có cùng số liệu.

## 8. Kiểm thử tự động: unit, integration và end-to-end

WebSocket là giao thức sống lâu nên một test tốt phải kiểm tra **thời điểm mở, gửi/nhận và đóng**, không chỉ gọi Java method trực tiếp.

| Tầng test | Những gì được xác minh | Những gì chưa được chứng minh |
| --- | --- | --- |
| Unit test handler/service | Validation, error mapping, state changes | Không xác minh HTTP Upgrade, WebSocket framing, container |
| Spring context / messaging integration | @MessageMapping, channel, converter, handler, event wiring | Chưa chắc kết nối qua network/proxy |
| End-to-end raw WebSocket | Server thật, handshake, send/receive, close | Không tự chứng minh STOMP destination routing |
| End-to-end STOMP | CONNECTED, SUBSCRIBE, SEND, MESSAGE, ERROR và broker | Chưa chắc load/proxy/TLS/đa node |
| Load/soak/chaos | Số kết nối lớn, reconnect, client chậm, broker outage, timeout | Cần môi trường giống production mới có ý nghĩa |

### 8.1. RawWebSocketIntegrationTest.java: bài test chạy với server thật

Test sau dùng JUnit 5 và `@SpringBootTest(webEnvironment = RANDOM_PORT)` để chạy embedded Servlet server trên cổng ngẫu nhiên. `StandardWebSocketClient` thực hiện HTTP Upgrade thực tế, không phải direct handler invocation.

```java
package demo.ws;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RawWebSocketIntegrationTest {

    @LocalServerPort
    int port;

    @Test
    void shouldEchoPingAndReportBadCommand() throws Exception {
        BlockingQueue<String> replies = new LinkedBlockingQueue<>();

        TextWebSocketHandler clientHandler = new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session,
                                             TextMessage message) {
                replies.offer(message.getPayload());
            }
        };

        WebSocketSession session = new StandardWebSocketClient()
                .execute(clientHandler,
                        "ws://127.0.0.1:" + port + "/ws/raw")
                .get(5, TimeUnit.SECONDS);

        try {
            session.sendMessage(new TextMessage("PING"));
            assertEquals("PONG",
                    replies.poll(5, TimeUnit.SECONDS));

            session.sendMessage(new TextMessage("BAD"));
            assertEquals("ERROR:BAD_COMMAND",
                    replies.poll(5, TimeUnit.SECONDS));
        } finally {
            session.close();
        }
    }
}
```

Cách chạy:

```bash
gradle test
```

Test dùng timeout hữu hạn để tránh treo CI. Nếu không nhận được message trong 5 giây, `poll` trả null và assertion thất bại có ý nghĩa. `finally` đóng session. Không dùng sleep tùy ý để giả vờ đồng bộ. Có thể dùng `Awaitility` nếu cần đợi gauge trở về 0 một cách ổn định, nhưng metric thường cập nhật bất đồng bộ và không nên assert tức thì sau khi gọi `close()`.

### 8.2. Test STOMP bằng WebSocketStompClient

Spring có `WebSocketStompClient` (package `org.springframework.web.socket.messaging`), phối hợp `StandardWebSocketClient` và `StompSessionHandler`/`StompFrameHandler`. Quy trình e2e hợp lý:

1. Khởi chạy Spring Boot trên random port.
2. Tạo `WebSocketStompClient`, cấu hình `MappingJackson2MessageConverter` nếu muốn truyền/nhận DTO JSON.
3. Gọi `connectAsync(...)` và chờ CONNECTED bằng future/handler.
4. Subscribe `/topic/pongs` và `/user/queue/errors` bằng `StompFrameHandler` trả đúng Java payload type.
5. SEND dữ liệu hợp lệ đến `/app/ping`, đợi message và assert DTO trả về.
6. SEND dữ liệu không hợp lệ, assert error DTO chỉ về user/session liên quan.
7. Disconnect và stop client trong `finally`.

Snippet phía client **minh họa, chưa phải một lớp test đầy đủ**:

```java
WebSocketStompClient stompClient =
        new WebSocketStompClient(new StandardWebSocketClient());
stompClient.setMessageConverter(
        new MappingJackson2MessageConverter());

StompSession session = stompClient
        .connectAsync("ws://127.0.0.1:" + port + "/stomp",
                new StompSessionHandlerAdapter() {})
        .get(5, TimeUnit.SECONDS);
```

Các import bổ sung: `org.springframework.web.socket.messaging.WebSocketStompClient`; `org.springframework.web.socket.messaging.WebSocketStompClient` ở phần code đã nêu; `org.springframework.messaging.converter.MappingJackson2MessageConverter`; `org.springframework.messaging.simp.stomp.StompSession`; `org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter`; `org.springframework.web.socket.client.standard.StandardWebSocketClient`; `java.util.concurrent.TimeUnit`.

**Lưu ý quan trọng để chống flaky test:** Với simple broker, gửi ngay sau `SUBSCRIBE` không bảo đảm subscription đã được lưu xong do messaging channels có thể xử lý bất đồng bộ; simple broker cũng không hỗ trợ đầy đủ STOMP receipt. Nếu test cần một mốc xác nhận đáng tin, thiết kế protocol ứng dụng xác nhận subscription qua một bước khác, hoặc dùng broker hỗ trợ STOMP receipts và đợi receipt. Không dùng `Thread.sleep(500)` làm “bằng chứng” subscriber đã sẵn sàng.

### 8.3. Test sự kiện kết nối và đóng lặp

Cần kiểm chứng hai ca:

- Hai lần thông báo disconnect cho cùng `sessionId` chỉ làm giảm active gauge một lần.
- CONNECT event không đồng nghĩa session đã CONNECTED; chỉ khi connected event đến mới được tính là active.

Với event listener, test unit có thể tạo event tương ứng với mock `Message<?>`/session ID và gọi listener trực tiếp. Đây là test logic idempotency; để xác minh Spring thực sự phát event trong phiên làm việc, cần integration test. Nếu session không kết nối hoàn tất, disconnect cleanup cũng không được làm số active âm.

### 8.4. Test handshake lỗi và abnormal close

Các kịch bản bổ sung:

- Đường dẫn sai: kết nối `/ws/not-found` → handshake không thành công, thường HTTP 404.
- Origin khác bị chặn: có cấu hình allowed-origins phù hợp → handshake từ browser không được phép.
- Token hoặc session thiếu: xác nhận security layer trả 401/403 **trước** upgrade.
- Message quá lớn: kiểm tra chính sách raw handler và giới hạn container. Với payload vượt buffer, handler có thể không thấy message.
- Transport lỗi: giả lập client rớt mạng/proxy đóng socket; đo callback/counters/close status thực tế.
- Nhiều client đồng thời: active gauge tăng, đóng từng client gauge giảm về 0 (đợi eventual consistency).
- STOMP broker relay mất kết nối: quan sát `BrokerAvailabilityEvent`, phát hiện delivery failure, client reconnect.

Không assert một HTTP status hay close code khi trường hợp đó phụ thuộc proxy/container mà chưa cố định môi trường; test cần ghi rõ loại thất bại mong đợi và cách quan sát nó.

## 9. Kịch bản giám sát và xử lý sự cố khi triển khai

### 9.1. Kết nối tăng nhưng không có STOMP message

Kiểm tra HTTP Upgrade có thành công chưa, client đã nhận `CONNECTED` chưa, destination `/app/...`/`/topic/...` đúng chưa và broker có available không. Nếu socket count tăng nhưng STOMP connected count không tăng, ưu tiên kiểm tra STOMP CONNECT, auth/interceptor và broker routing; chưa cần tăng pool size ngay.

### 9.2. Abnormal close tăng đột biến

So khớp thời điểm xảy ra với reverse proxy idle timeout, network drop, restart và giới hạn gửi. Kiểm tra STOMP heartbeat được cấu hình/đồng ý bởi client và server hay chưa; heartbeat không thay thế hoàn toàn TCP/WebSocket transport checks. Quan sát số lần reconnect trên client, hạn chế reconnect storm bằng exponential backoff và jitter.

### 9.3. Latency tăng, send buffer vượt ngưỡng

Xem `clientInboundChannel`/`clientOutboundChannel` executor stats, payload sizes, client chậm, số lượng subscribers và tần suất broadcast. Với STOMP, `setSendTimeLimit` và `setSendBufferSizeLimit` hạn chế tích lũy data cho client chậm. Nếu nghiệp vụ blocking trong message handler, tách việc blocking phù hợp thay vì mở rộng vô hạn executor.

### 9.4. Số sessions không giảm sau disconnect

Rà soát cleanup idempotency; timeout/network half-open có thể khiến callback tới trễ. Đảm bảo không giữ references đến `WebSocketSession` trong map không được dọn. Nếu có subscriber/event listener khác, kiểm tra chúng có giữ state vô hạn không. Không tự giảm counter khi gặp mọi loại lỗi mà chưa biết session đã đăng ký trước đó hay chưa.

### 9.5. Lỗi STOMP không đến đúng client

Xác nhận client đã subscribe `/user/queue/errors`, `@SendToUser(broadcast=false)` có được dùng đúng ngữ cảnh, authenticated principal/user destinations có được cấu hình đúng và lỗi có phát sinh từ `@MessageMapping` hay từ protocol layer. `@MessageExceptionHandler` không thay thế `StompSubProtocolErrorHandler` cho lỗi định dạng frame.

## 10. Bảo mật và giới hạn vận hành

Kết nối dài không có nghĩa quyền của client không thay đổi. Với ứng dụng thực tế cần:

- Xác thực ở HTTP handshake hoặc STOMP CONNECT theo kiến trúc xác thực; kiểm tra quyền truy cập từng destination/room/user.
- Kiểm tra origin cho browser để giảm nguy cơ Cross-Site WebSocket Hijacking. Origin **không phải cơ chế xác thực** với non-browser clients.
- Không đẩy access token, cookie, Authorization hay payload nhạy cảm vào log và metric labels.
- Kiểm soát kích thước và tần suất message, số kết nối mỗi user/IP, send buffers, idle timeout và heartbeat.
- Dùng `wss://` khi triển khai qua HTTPS; bảo đảm reverse proxy cho phép Upgrade/Connection và timeout phù hợp.
- Thiết kế policy cho lỗi có thể phục hồi (message nghiệp vụ), lỗi quyền truy cập nghiêm trọng (đóng kết nối/chặn), lỗi hệ thống (log nội bộ + close) rõ ràng.
- Không mặc định một session STOMP đang CONNECTED nghĩa là user vẫn còn quyền sau khi token/session bị thu hồi; cần chính sách revalidation và disconnect chủ động khi có thể.

## 11. Bài tập tự kiểm tra

**Bài tập 1 — Phân loại lỗi:** Browser kết nối `ws://localhost:8080/ws/raw` nhưng bị HTTP 403; một browser khác kết nối thành công rồi nhận close 1009 sau khi gửi message lớn. Hai trường hợp thuộc tầng xử lý nào? Vì sao không có HTTP 403 ở trường hợp thứ hai?

**Bài tập 2 — Lifecycle:** Client gửi STOMP CONNECT nhưng chưa nhận CONNECTED; server đã nên cộng vào metric `demo.ws.stomp.active` chưa? Lý giải dựa vào SessionConnectEvent và SessionConnectedEvent.

**Bài tập 3 — Lặp disconnect:** Tự gọi listener `onDisconnect` hai lần cho cùng ID đã từng connected. Kỳ vọng gauge và counter thay đổi thế nào? Vì sao phải kiểm tra `Set.remove` trả về `true`?

**Bài tập 4 — Định tuyến lỗi:** SEND JSON `{"text":""}` tới `/app/ping`. Theo demo, lỗi đi qua `@MessageExceptionHandler` về destination nào? Có tự trở thành STOMP ERROR frame hay không?

**Bài tập 5 — Monitoring:** Có 50 raw WebSocket sessions nhưng 45 STOMP sessions, 10 lỗi transport trong một phút và outbound send buffer tăng. Nêu ba giả thuyết cần kiểm tra và metrics/log bổ sung để xác định nguyên nhân, tránh tự kết luận chỉ từ một gauge.

**Bài tập 6 — E2E test:** Bổ sung test kết nối hai raw clients cùng lúc, gửi PING trên một client và chứng minh client kia không nhận message vì endpoint chỉ gửi về session gọi. Đóng cả hai và dùng cơ chế chờ có timeout để xác nhận active gauge trở về 0.

**Bài tập 7 — Reliability:** Nếu ứng dụng chuyển từ simple broker sang broker relay, bổ sung hành vi nào khi BrokerAvailabilityEvent báo false và client mất CONNECTED? Vì sao cần reconnect có backoff và xử lý message delivery failure ngay cả khi event vừa báo true?

## 12. Ghi nhớ các quy tắc quan trọng

1. **Handshake error, transport error, raw message error và STOMP error là bốn tình huống khác nhau.** Chọn handler đúng tầng.
2. `handleTransportError` không phải lưới bắt mọi exception; `afterConnectionClosed` là nơi dọn lifecycle raw WebSocket.
3. `@MessageExceptionHandler` xử lý lỗi method `@MessageMapping`; `StompSubProtocolErrorHandler` dành cho lỗi STOMP ở mức subprotocol.
4. `SessionConnectEvent` khác `SessionConnectedEvent`; tính online của STOMP ở mốc CONNECTED.
5. `SessionDisconnectEvent` có thể phát nhiều lần; cleanup phải idempotent.
6. `WebSocketMessageBrokerStats` phản ánh hạ tầng STOMP, còn raw handler nên đo thêm metric riêng.
7. Một test chỉ gọi handler Java trực tiếp **không chứng minh** WebSocket handshake/frames hoạt động; kết hợp unit, integration và end-to-end.
8. Không dùng sleep tùy ý để bảo đảm STOMP SUBSCRIBE đã sẵn sàng trên simple broker; cần xác nhận đúng tầng.
9. Theo dõi active sessions, errors, closes, message rates, send buffer, broker state và client reconnect; tránh metric labels chứa ID không giới hạn.
10. Hệ thống cần chính sách phân quyền, giới hạn payload, timeout, backpressure/reconnect và logging an toàn trước khi triển khai public.

## Tài liệu chính thức để tra cứu

- Spring WebSocket Servlet API: https://docs.spring.io/spring-framework/reference/6.2/web/websocket/server.html
- Spring STOMP application context events: https://docs.spring.io/spring-framework/reference/6.2/web/websocket/stomp/application-context-events.html
- Spring STOMP annotated controllers và MessageExceptionHandler: https://docs.spring.io/spring-framework/reference/6.2/web/websocket/stomp/handle-annotations.html
- Spring STOMP interception: https://docs.spring.io/spring-framework/reference/6.2/web/websocket/stomp/interceptors.html
- Spring STOMP monitoring: https://docs.spring.io/spring-framework/reference/6.2/web/websocket/stomp/stats.html
- Spring STOMP performance: https://docs.spring.io/spring-framework/reference/6.2/web/websocket/stomp/configuration-performance.html
- Spring STOMP testing: https://docs.spring.io/spring-framework/reference/6.2/web/websocket/stomp/testing.html
- Spring WebSocketClient JavaDoc (6.2): https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/web/socket/client/WebSocketClient.html


</details>

- [Quay lại đầu trang](#back-to-top)
