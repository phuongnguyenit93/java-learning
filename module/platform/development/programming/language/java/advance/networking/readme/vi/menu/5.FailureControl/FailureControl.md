<a id="back-to-top"></a>

# Timeout, lỗi và vòng đời tài nguyên mạng

## Menu
- [Các điểm có thể chờ trong network I/O](#network-wait-points)
- [Connect, accept và read timeout](#socket-timeouts)
- [Socket option và tác động ở transport/OS boundary](#socket-options)
- [Mô hình lỗi khi resolve, bind, connect và truyền dữ liệu](#network-failure-model)
- [Trách nhiệm sở hữu, close và dọn dẹp khi lỗi](#network-resource-cleanup)
- [Interrupt, cancellation và blocking socket I/O](#network-cancellation)
- [Các sai lầm Networking thường gặp](#networking-pitfalls)

## <a id="network-wait-points">Các điểm có thể chờ trong network I/O</a>

<details>
<summary>Click for details</summary>

Mã networking thường “treo” không phải vì JVM đứng, mà vì chương trình đang **chờ một điều kiện bên ngoài**. Các điểm chờ quan trọng gồm:

- hostname resolution;
- connect tới remote endpoint;
- ServerSocket.accept() chờ kết nối mới;
- InputStream.read() hoặc DatagramSocket.receive() chờ dữ liệu;
- selector hoặc cơ chế hoàn tất bất đồng bộ chờ readiness/completion theo mô hình khác.

Không có một “network timeout” duy nhất bao phủ toàn bộ chuỗi:

~~~text
resolve
  ↓
connect
  ↓
request / write
  ↓
read / receive
  ↓
close
~~~

Mỗi giai đoạn có API và kiểu lỗi riêng. Ví dụ Socket.connect(address, timeout) giới hạn connect, còn Socket.setSoTimeout(...) giới hạn blocking read; ServerSocket.setSoTimeout(...) giới hạn accept.

Khi thiết kế timeout, trước tiên hãy xác định **đang muốn giới hạn thời gian của giai đoạn nào**. Một con số timeout áp vào sai ranh giới vừa không giải quyết được việc chờ kéo dài thật sự, vừa khiến việc gỡ lỗi khó hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="socket-timeouts">Connect, accept và read timeout</a>

<details>
<summary>Click for details</summary>

JDK phân timeout theo thao tác thay vì cung cấp một cấu hình chung.

Connect timeout:

~~~java
Socket socket = new Socket();
socket.connect(
        new InetSocketAddress("example.com", 443),
        3_000
);
~~~

Read timeout trên Socket kiểu truyền thống:

~~~java
socket.setSoTimeout(5_000);
int value = socket.getInputStream().read();
~~~

Nếu read tiếp tục block quá thời gian đó, JDK ném SocketTimeoutException nhưng socket không tự động bị đóng chỉ vì SO_TIMEOUT hết hạn.

ServerSocket và DatagramSocket cũng có SO_TIMEOUT cho accept() và receive():

~~~java
serverSocket.setSoTimeout(5_000);
datagramSocket.setSoTimeout(5_000);
~~~

Điểm dễ nhầm: SO_TIMEOUT không phải write timeout tổng quát và cũng không giới hạn DNS lookup. Với HTTP Client còn có client connect timeout và timeout theo từng request ở abstraction khác. Vì vậy timeout phải luôn được đọc cùng API cụ thể đang sử dụng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="socket-options">Socket option và tác động ở transport/OS boundary</a>

<details>
<summary>Click for details</summary>

Socket options là các điều khiển hoặc gợi ý mà Java chuyển xuống networking stack của hệ điều hành. Chúng ảnh hưởng hành vi của socket nhưng không nên được chỉnh chỉ vì “mã production thường có”.

Một số option quen thuộc:

- TCP_NODELAY: ảnh hưởng việc trì hoãn/coalesce packet nhỏ của TCP;
- SO_KEEPALIVE: yêu cầu TCP keepalive ở mức transport;
- SO_SNDBUF / SO_RCVBUF: gợi ý cho kích thước buffer;
- SO_REUSEADDR: ảnh hưởng khả năng tái sử dụng địa chỉ local trong các tình huống phụ thuộc platform;
- SO_LINGER: thay đổi hành vi của close đối với dữ liệu TCP chưa gửi hết;
- SO_BROADCAST: cho phép UDP broadcast.

NetworkChannel hiện đại còn có API type-safe:

~~~java
channel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
~~~

Một option có thể chỉ là **gợi ý**, hoặc có ngữ nghĩa khác nhau đôi chút giữa các hệ điều hành. Hãy đo và hiểu vấn đề trước khi tuning. Socket option không thay thế timeout, retry hay health-check ở tầng ứng dụng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="network-failure-model">Mô hình lỗi khi resolve, bind, connect và truyền dữ liệu</a>

<details>
<summary>Click for details</summary>

Lỗi mạng nên được phân loại theo vòng đời thay vì catch IOException rồi ghi một thông điệp chung.

Ví dụ:

~~~text
resolve
→ UnknownHostException

bind
→ BindException / address already in use / permission

connect
→ ConnectException / timeout / route failure

read-write
→ SocketTimeoutException / EOF / reset / IOException

closed resource
→ SocketException / ClosedChannelException
~~~

**EOF không giống timeout.** Với TCP stream, read trả -1 thường nghĩa peer đã kết thúc output theo cách mà local side quan sát như end-of-stream. Timeout nghĩa chưa có dữ liệu trong khoảng chờ đã cấu hình. Connection reset lại là một dạng termination khác.

Bối cảnh rất quan trọng. Một ConnectException tới đúng host/port nói điều khác hoàn toàn với UnknownHostException trước khi có IP.

Log hữu ích nên giữ giai đoạn, local/remote endpoint và thao tác, nhưng không log payload bí mật. Phân loại tốt giúp quyết định việc nào có thể retry, việc nào là lỗi cấu hình, và việc nào phải handoff sang resilience policy ở module khác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="network-resource-cleanup">Trách nhiệm sở hữu, close và dọn dẹp khi lỗi</a>

<details>
<summary>Click for details</summary>

Socket và Channel là tài nguyên gắn với OS. GC không phải cơ chế quản lý vòng đời phù hợp cho kết nối mạng. Mã nguồn nên có **bên sở hữu rõ ràng** chịu trách nhiệm close.

Với tài nguyên có phạm vi rõ, ưu tiên try-with-resources:

~~~java
try (Socket socket = new Socket("localhost", 8080);
     InputStream in = socket.getInputStream()) {

    // communicate
}
~~~

Đóng Socket sẽ đóng các stream liên quan tới socket đó. Với channel, sau close thì I/O tiếp theo thất bại vì channel không còn open.

Việc dọn dẹp phải chạy cả khi:

- connect xong nhưng protocol setup thất bại;
- read/write ném exception;
- task bị cancel;
- ứng dụng quyết định bỏ request.

Không nên để nhiều tầng cùng “nghĩ mình là bên sở hữu” và cùng điều khiển vòng đời một cách mơ hồ. Ví dụ nếu một thư viện nhận Socket từ bên gọi, contract phải nói rõ thư viện có close socket hay không.

Trách nhiệm sở hữu tài nguyên là điều kiện nền cho cancellation, shutdown và connection reuse ở các chương sau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="network-cancellation">Interrupt, cancellation và blocking socket I/O</a>

<details>
<summary>Click for details</summary>

Cancellation của network I/O phải trả lời được hai câu hỏi: **thao tác nào cần dừng, và tài nguyên có còn dùng được sau khi dừng không?**

Trên Java 21, cách triển khai mặc định của hệ thống cho Socket, ServerSocket và DatagramSocket có hành vi đặc biệt khi thao tác blocking chạy trong **virtual thread**: nếu virtual thread bị interrupt khi đang block trên socket I/O, thread được đánh thức và socket bị đóng.

~~~java
Thread worker = Thread.ofVirtual().start(() -> {
    try (Socket socket = new Socket("localhost", 8080)) {
        socket.getInputStream().read(); // may block
    } catch (IOException e) {
        // cancellation / close is observed here
    }
});

worker.interrupt();
~~~

Đây là lý do quan trọng khiến cancellation phải đi cùng suy luận về vòng đời tài nguyên. Không nên catch exception rồi tiếp tục giả định socket vẫn còn dùng được.

Với InterruptibleChannel, interrupt/async close cũng có ngữ nghĩa riêng của channel API. Chapter NIO sẽ đi sâu hơn vào mô hình đó.

Ở cấp module, quy tắc cần giữ là: cancellation không chỉ là “dừng thread”; nó phải dẫn hệ thống tới một **trạng thái tài nguyên xác định**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="networking-pitfalls">Các sai lầm Networking thường gặp</a>

<details>
<summary>Click for details</summary>

Một số lỗi lặp lại trong Java networking đến từ mô hình tư duy sai hơn là cú pháp sai:

1. **Không đặt giới hạn thời gian ở thao tác có thể chờ**  
   Luồng xử lý request có thể giữ tài nguyên vô thời hạn khi remote peer không phản hồi.

2. **Coi TCP read = một message**  
   TCP chỉ là byte stream; framing phải do protocol/ứng dụng định nghĩa.

3. **Coi UDP connect = reliable connection**  
   DatagramSocket.connect chỉ gắn/lọc peer, không thêm bảo đảm giao nhận.

4. **Tuning socket option theo công thức**  
   Buffer, linger, keepalive và TCP_NODELAY chỉ có ý nghĩa khi biết bottleneck và hành vi của hệ điều hành.

5. **Nuốt IOException rồi retry vô hạn**  
   Unknown host, refused connection, timeout và response ứng dụng không hợp lệ không phải cùng một loại lỗi.

6. **Quên close hoặc trách nhiệm sở hữu không rõ**  
   Rò rỉ file descriptor/socket cuối cùng trở thành lỗi production.

7. **Mặc định non-blocking luôn tốt hơn**  
   Selector có chi phí quản lý trạng thái; Java 21 virtual threads làm phong cách blocking trở thành lựa chọn thực tế cho nhiều workload I/O-bound.

Chương tiếp theo chuyển từ lỗi/vòng đời sang NIO để hiểu khi nào độ phức tạp của channel/selector thật sự đáng dùng.

</details>

- [Quay lại đầu trang](#back-to-top)
