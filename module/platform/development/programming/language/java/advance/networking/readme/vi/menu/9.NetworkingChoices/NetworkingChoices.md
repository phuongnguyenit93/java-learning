<a id="back-to-top"></a>

# Chọn abstraction Networking và giữ đúng boundary

## Menu
- [Mô hình quyết định khi chọn API Networking](#networking-choice-model)
- [Chọn TCP, UDP hay API mức cao](#transport-choice)
- [Blocking I/O, virtual threads và event-driven I/O](#blocking-concurrency-choice)
- [Ranh giới TLS/JSSE với Security & Cryptography](#networking-security-boundary)
- [Ranh giới retry, resilience và policy tầng ứng dụng](#networking-resilience-boundary)
- [Handoff sang HTTP/WebSocket integration](#networking-integration-handoff)
- [Mental model Networking end-to-end](#networking-end-to-end-model)

## <a id="networking-choice-model">Mô hình quyết định khi chọn API Networking</a>

<details>
<summary>Click for details</summary>

Đến cuối module, câu hỏi quan trọng không còn là “class nào tồn tại?” mà là **abstraction nào khớp với bài toán giao tiếp hiện tại?**

Một mô hình quyết định hữu ích gồm năm chiều:

1. **Data semantics** — byte stream, datagram, request/response hay message-oriented connection?
2. **Peer relationship** — một connection lâu sống hay nhiều interaction độc lập?
3. **Concurrency model** — blocking thread-per-task, selector readiness hay asynchronous completion?
4. **Control level** — cần tự quản lý framing/socket option hay muốn API mức cao xử lý protocol?
5. **Vòng đời/lỗi** — ai sở hữu timeout, cancellation, close và retry policy?

~~~text
bài toán
 ↓
ngữ nghĩa giao tiếp
 ↓
mức kiểm soát cần thiết
 ↓
ràng buộc concurrency/vòng đời
 ↓
abstraction JDK nhỏ nhất vẫn đáp ứng yêu cầu
~~~

“API thấp hơn” không tự động nhanh hơn, và “API cao hơn” không tự động đơn giản trong mọi trường hợp. Mục tiêu là chọn **mức abstraction thấp nhất cần thiết, nhưng không thấp hơn**.

Ví dụ, nếu bài toán chỉ là gọi một HTTP endpoint, tự mở Socket rồi viết HTTP framing tạo ra độ phức tạp không cần thiết. Ngược lại, nếu đang xây custom binary protocol trên TCP, HttpClient không phải abstraction phù hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transport-choice">Chọn TCP, UDP hay API mức cao</a>

<details>
<summary>Click for details</summary>

Chọn transport/API nên bắt đầu từ semantic của dữ liệu.

**TCP / Socket**

- cần ordered byte stream;
- cần connection-oriented communication;
- ứng dụng tự định nghĩa framing/protocol;
- chấp nhận quản lý vòng đời kết nối trực tiếp.

**UDP / Datagram**

- mỗi datagram có ranh giới riêng;
- chấp nhận loss/reordering/duplication theo transport semantics;
- cần latency/control mà connection-oriented stream không phù hợp;
- ứng dụng chịu trách nhiệm nếu muốn reliability bổ sung.

**HttpClient**

- bài toán tự nhiên là HTTP request/response;
- muốn reusable client configuration, redirects, proxy, authentication, body handling, HTTP/1.1 hoặc HTTP/2;
- không muốn tự viết HTTP framing.

**WebSocket client**

- cần connection lâu sống và giao tiếp message-oriented hai chiều;
- server nói WebSocket;
- ứng dụng chấp nhận vòng đời event/listener.

Một cách nhớ:

~~~text
custom stream protocol      → TCP
independent datagrams       → UDP
giao tiếp tài nguyên HTTP   → HttpClient
bidirectional WS messaging  → WebSocket client
~~~

Nếu yêu cầu chỉ nói “real-time” hoặc “nhanh”, chưa đủ để chọn transport. Cần biết ngữ nghĩa giao nhận, trạng thái/vòng đời và protocol ứng dụng trước.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="blocking-concurrency-choice">Blocking I/O, virtual threads và event-driven I/O</a>

<details>
<summary>Click for details</summary>

Concurrency model và network API liên quan chặt chẽ nhưng không phải cùng một quyết định.

Phong cách blocking truyền thống có luồng code dễ đọc:

~~~text
accept/connect
→ read
→ process
→ write
~~~

Trước virtual threads, thread-per-connection với rất nhiều blocking platform thread có thể tạo chi phí tài nguyên đáng kể. Selector-based non-blocking I/O giải quyết bằng cách multiplex nhiều channel trên ít thread hơn, đổi lại ứng dụng phải quản lý readiness và trạng thái theo từng kết nối rõ ràng.

Java 21 thay đổi trade-off: virtual thread có thể block trên network I/O mà không giữ carrier thread trong lúc chờ ở các thao tác được hỗ trợ. Vì vậy:

~~~text
many concurrent connections
≠ automatically require Selector
~~~

Chọn **blocking + virtual threads** khi:

- luồng xử lý tự nhiên là tuần tự;
- thread-per-task giúp code rõ hơn;
- không cần readiness-level control đặc biệt.

Chọn **Selector/event-driven** khi:

- architecture đã dựa trên event loop;
- cần explicit readiness multiplexing/control;
- muốn quản lý rất nhiều channel với state machine riêng.

Chọn **asynchronous channels** khi completion-style API thật sự phù hợp integration/concurrency model.

Đừng benchmark abstraction bằng microbenchmark không có hành vi mạng thật. Throughput, latency, memory, scheduling và độ phức tạp đều phải được đánh giá trong workload thực tế.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="networking-security-boundary">Ranh giới TLS/JSSE với Security & Cryptography</a>

<details>
<summary>Click for details</summary>

Networking cần biết **TLS nằm ở đâu trong stack**, nhưng không sở hữu toàn bộ security curriculum.

Mental model:

~~~text
application protocol
        ↓
TLS secure transport
        ↓
TCP/network transport
~~~

Trong JDK, JSSE cung cấp các abstraction như SSLContext, SSLSocket, SSLServerSocket và SSLEngine. HttpClient/WebSocket qua HTTPS/WSS có thể sử dụng TLS phía dưới mà ứng dụng không trực tiếp thao tác từng TLS record.

Networking cần hiểu các hệ quả vận hành:

- handshake có thể thất bại trước khi dữ liệu ứng dụng được trao đổi;
- endpoint identity/certificate validation ảnh hưởng việc connect thành công;
- TLS có vòng đời và cấu hình riêng.

Nhưng các topic sau thuộc **Security & Cryptography**:

- trust store / key store;
- certificate chain và X.509;
- cipher suite và protocol security;
- key material;
- custom TrustManager/KeyManager;
- cryptographic correctness.

Vì vậy khi gặp SSLHandshakeException, Networking giúp xác định lỗi xảy ra ở bước thiết lập security sau transport connection; nguyên nhân certificate/trust cụ thể cần handoff sang module Security.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="networking-resilience-boundary">Ranh giới retry, resilience và policy tầng ứng dụng</a>

<details>
<summary>Click for details</summary>

Networking phát hiện lỗi; **resilience policy quyết định phải làm gì sau lỗi**.

Không nên viết:

~~~text
catch IOException
→ retry forever
~~~

Retry an toàn phụ thuộc nhiều thông tin ngoài transport:

- thao tác có idempotent không?
- request đã tới server nhưng response bị mất hay chưa?
- timeout là connect timeout hay read timeout?
- có backoff/jitter không?
- có retry budget/circuit breaker không?
- lỗi là transient hay lỗi cấu hình/lỗi vĩnh viễn?

Ví dụ HTTP POST có thể đã tạo dữ liệu phía server trước khi client mất connection. Tự động retry chỉ vì thấy IOException có thể tạo duplicate side effect.

Networking có trách nhiệm:

- cung cấp phân loại lỗi đủ tốt;
- giữ timeout/cancellation/trạng thái tài nguyên rõ ràng;
- không che mất original cause.

Module Resilience/tầng integration mới quyết định retry, circuit breaker, fallback, deadline propagation hay chiến lược idempotency.

Boundary này giúp code tránh “transport layer tự đoán business safety”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="networking-integration-handoff">Handoff sang HTTP/WebSocket integration</a>

<details>
<summary>Click for details</summary>

Java Networking dừng ở **cơ chế client API và hành vi transport**. Khi ứng dụng bắt đầu quan tâm ý nghĩa của request/message, trách nhiệm đã chuyển lên tầng integration.

Với HTTP, Networking sở hữu:

- HttpClient configuration/reuse;
- cơ chế HttpRequest/HttpResponse;
- body publisher/subscriber;
- cơ chế timeout/proxy/authenticator.

Integration sở hữu:

- REST/resource design;
- API contract và status-code policy;
- serialization format/domain schema;
- idempotency/retry semantics;
- service-to-service communication pattern.

Với WebSocket, Networking sở hữu listener, demand, fragmentation và vòng đời send/close. Realtime Integration sở hữu reconnect, subscription/topic model, STOMP/broker semantics và protocol thông điệp của ứng dụng.

Mental handoff:

~~~text
Networking
→ "Java gửi/nhận bằng cơ chế nào?"

Integration
→ "Ứng dụng đang giao tiếp theo contract nào?"
~~~

Giữ ranh giới này giúp module Java Advanced đủ sâu về runtime/API nhưng không lặp lại curriculum Microservice/Integration.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="networking-end-to-end-model">Mental model Networking end-to-end</a>

<details>
<summary>Click for details</summary>

Một network interaction hoàn chỉnh có thể được suy luận theo chuỗi:

~~~text
1. ứng dụng có nhu cầu giao tiếp
        ↓
2. chọn abstraction protocol/API
        ↓
3. hostname / định danh tài nguyên
        ↓
4. phân giải tên
        ↓
5. local + remote endpoint cụ thể
        ↓
6. bind / connect / opening handshake
        ↓
7. truyền dữ liệu hoặc hoàn tất bất đồng bộ
        ↓
8. xử lý timeout / lỗi / cancellation
        ↓
9. close / tái sử dụng / giải phóng tài nguyên
~~~

Khi một sự cố production xuất hiện, đi ngược luồng này sẽ hiệu quả hơn việc đổi option ngẫu nhiên:

~~~text
Tên có resolve đúng không?
→ endpoint có đúng không?
→ connect/handshake có thành công không?
→ đang block ở giai đoạn nào?
→ data model/framing có đúng không?
→ peer đã close/reset chưa?
→ timeout/cancellation thuộc layer nào?
→ tài nguyên có được close/reuse đúng không?
~~~

Sau module này, người học không cần nhớ mọi method của java.net. Điều quan trọng là có thể đặt API vào đúng mô hình tư duy và trả lời được:

- abstraction này giải quyết tầng nào?
- thao tác này có thể chờ ở đâu?
- lỗi này thuộc giai đoạn nào?
- ai sở hữu vòng đời?
- khi nào cần handoff sang Concurrency, Security hoặc Integration?

Đó là mô hình tư duy dùng được cả khi API cụ thể thay đổi ở các Java release sau.

</details>

- [Quay lại đầu trang](#back-to-top)
