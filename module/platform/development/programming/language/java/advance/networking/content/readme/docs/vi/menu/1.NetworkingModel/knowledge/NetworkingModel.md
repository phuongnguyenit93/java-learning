<a id="back-to-top"></a>

# Mô hình Networking trong Java

## Menu
- [Networking trong Java là gì và vì sao cần?](#networking-purpose)
- [Host, địa chỉ, cổng và đầu mút mạng](#network-endpoint-model)
- [Tầng giao vận và mô hình dữ liệu truyền](#transport-and-data-model)
- [Blocking, non-blocking và hoàn tất bất đồng bộ](#io-completion-models)
- [Phạm vi của Java Networking](#networking-module-boundary)

## <a id="networking-purpose">Networking trong Java là gì và vì sao cần?</a>

<details>
<summary>Click for details</summary>

Networking cho phép một chương trình Java trao đổi dữ liệu với **một tiến trình khác thông qua một endpoint mạng**. Tiến trình kia có thể nằm cùng máy, trong cùng mạng nội bộ hoặc ở một hệ thống từ xa. Khác với đọc bộ nhớ hoặc file cục bộ, network I/O luôn đi qua ranh giới bên ngoài JVM: có độ trễ, phụ thuộc hệ điều hành, có thể thất bại giữa chừng và cần quản lý tài nguyên thật.

Nếu không có networking, ứng dụng chỉ có thể xử lý dữ liệu cục bộ hoặc phải dựa vào một cơ chế IPC khác. Networking giải quyết bài toán:

~~~text
hai phía độc lập
→ tìm thấy nhau
→ thiết lập cách giao tiếp
→ truyền dữ liệu
→ xử lý lỗi
→ giải phóng tài nguyên
~~~

JDK cung cấp nhiều mức trừu tượng (abstraction). Ở mức thấp có Socket, ServerSocket, DatagramSocket và các network channel. Ở mức cao hơn có HttpClient và WebSocket client. Mục tiêu của module không phải học thuộc class, mà hiểu **mỗi mức trừu tượng đang che giấu phần nào của bài toán mạng**.

Mô hình tư duy xuyên suốt module:

~~~text
nhu cầu giao tiếp của ứng dụng
    ↓
protocol / API abstraction
    ↓
endpoint + transport
    ↓
I/O mạng
    ↓
tiến trình từ xa
~~~

Khi gỡ lỗi mã mạng, bốn câu hỏi đầu tiên nên là: đang nói chuyện với endpoint nào, bằng transport nào, thao tác nào có thể đang chờ, và tài nguyên nào cần được đóng?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="network-endpoint-model">Host, địa chỉ, cổng và đầu mút mạng</a>

<details>
<summary>Click for details</summary>

Một endpoint mạng cần đủ thông tin để định vị **một đầu giao tiếp**. Với Internet socket, mô hình tư duy phổ biến là:

~~~text
host/IP + port
~~~

Host name như <code>example.com</code> là tên logic dễ dùng. Tên này thường phải được phân giải thành một hoặc nhiều địa chỉ IP. Port xác định endpoint ở tầng transport trên máy đích; cùng một IP có thể phục vụ nhiều tiến trình hoặc dịch vụ qua các port khác nhau.

Java tách các lớp thông tin này thành những abstraction riêng:

- InetAddress: địa chỉ IP, có thể kèm hostname;
- InetSocketAddress: địa chỉ socket kiểu IP/host + port;
- SocketAddress: abstraction chung cho địa chỉ socket;
- UnixDomainSocketAddress: endpoint local theo pathname cho Unix-domain socket.

Không nên đồng nhất URL với endpoint. URL còn có scheme, path, query và ngữ nghĩa của tài nguyên. Ví dụ <code>https://example.com/orders?id=42</code> chứa nhiều thông tin hơn địa chỉ socket mà kết nối cuối cùng sử dụng.

Điểm phân biệt này là nền cho các chương sau: name resolution biến tên thành địa chỉ; TCP/UDP dùng endpoint để truyền dữ liệu; HTTP/WebSocket lại xây abstraction mức cao hơn trên kết nối đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transport-and-data-model">Tầng giao vận và mô hình dữ liệu truyền</a>

<details>
<summary>Click for details</summary>

Transport quyết định **dạng giao tiếp cơ bản mà ứng dụng nhìn thấy**.

Với TCP, ứng dụng nhận một **luồng byte có thứ tự**. TCP không giữ ranh giới thông điệp của ứng dụng. Nếu client gọi write hai lần, server không được giả định sẽ đọc đúng hai lần tương ứng. Ứng dụng phải tự có cơ chế đóng khung (framing) như newline, length-prefix hoặc protocol có cấu trúc.

Với UDP, ứng dụng làm việc với **datagram**. Mỗi datagram giữ ranh giới riêng, nhưng UDP không cung cấp cùng mức bảo đảm như TCP về giao nhận (delivery) hay thứ tự (ordering). Nếu cần độ tin cậy cao hơn, ứng dụng/protocol phải tự thiết kế phần đó.

Các API mức cao thêm ngữ nghĩa mới:

~~~text
TCP byte stream
    ↑
HTTP request / response

TCP connection
    ↑
WebSocket messages
~~~

Vì vậy “gửi dữ liệu qua mạng” không phải lúc nào cũng là cùng một thao tác. Trước khi chọn API, hãy xác định ứng dụng cần **byte stream, datagram, request/response hay hội thoại hướng thông điệp**. Chọn sai mô hình dữ liệu thường khiến framing, retry và vòng đời phức tạp hơn cần thiết.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="io-completion-models">Blocking, non-blocking và hoàn tất bất đồng bộ</a>

<details>
<summary>Click for details</summary>

Ba thuật ngữ thường bị trộn lẫn là **blocking**, **non-blocking** và **asynchronous completion**.

**Blocking I/O** nghĩa là lời gọi có thể giữ luồng thực thi ở đó cho tới khi thao tác có thể tiến triển hoặc kết thúc. Ví dụ đọc từ Socket InputStream có thể chờ dữ liệu.

**Non-blocking I/O** nghĩa là lời gọi trở về khi hiện tại chưa thể làm việc. Với SocketChannel ở non-blocking mode, chương trình thường kết hợp Selector để biết channel nào đã ready.

**Asynchronous I/O** nghĩa là chương trình khởi động thao tác rồi nhận kết quả sau qua Future hoặc CompletionHandler, ví dụ AsynchronousSocketChannel.

~~~text
blocking       → chờ ngay trong lời gọi
non-blocking   → lời gọi return; quan sát readiness
asynchronous   → thao tác hoàn tất sau
~~~

Đây là mô hình I/O, không phải toàn bộ kiến trúc đồng thời. Java 21 còn thay đổi lựa chọn này: blocking network I/O chạy trong virtual thread có thể tạm dừng virtual thread thay vì giữ carrier thread. Vì thế “blocking” không đồng nghĩa “không thể mở rộng”.

Networking chỉ dùng các khái niệm concurrency để hiểu hành vi I/O; scheduling, synchronization và Java Memory Model vẫn thuộc module Concurrency.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="networking-module-boundary">Phạm vi của Java Networking</a>

<details>
<summary>Click for details</summary>

Module này sở hữu **JDK networking APIs và cơ chế vận hành runtime ở phía ứng dụng Java**:

- biểu diễn địa chỉ và phân giải tên;
- TCP/UDP sockets;
- socket options, timeout, lỗi và vòng đời tài nguyên;
- NIO network channels, selector và asynchronous channels;
- JDK HttpClient và WebSocket client;
- cách chọn abstraction mạng phù hợp.

Một số chủ đề xuất hiện để làm rõ ranh giới nhưng không được đào sâu ở đây:

- TLS/JSSE, certificate, key material → Security & Cryptography;
- thread scheduling, executors, virtual-thread model → Concurrency;
- HTTP API design, REST convention, retry/resilience policy → Integration / Resilience;
- WebSocket protocol architecture, reconnect policy, STOMP/messaging semantics → Realtime Integration;
- gateway, proxy architecture, service mesh, routing hạ tầng → Infrastructure / Network.

Ranh giới này tránh một lỗi học phổ biến: thấy một HTTPS request có TLS, retry và JSON rồi gom tất cả thành “Java Networking”. Networking giải thích cơ chế kết nối/API; các mối quan tâm còn lại có module sở hữu riêng.

Sau chương mở đầu này, người học nên hiểu mình sẽ đi theo luồng: **endpoint → transport → lỗi/vòng đời → mô hình NIO → HTTP/WebSocket → lựa chọn và đánh đổi**.

</details>

- [Quay lại đầu trang](#back-to-top)
