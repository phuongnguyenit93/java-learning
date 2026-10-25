<a id="back-to-top"></a>

# NIO Networking, multiplexing và I/O bất đồng bộ

## Menu
- [SocketChannel, ServerSocketChannel và DatagramChannel](#network-channels)
- [Buffer và partial network I/O](#network-buffer-interaction)
- [Selector và mô hình readiness](#selector-readiness-model)
- [Registration, interest set và SelectionKey](#selection-key-lifecycle)
- [AsynchronousSocketChannel và completion model](#asynchronous-channel-model)
- [Unix-domain sockets với network channels](#unix-domain-channels)
- [Virtual threads và blocking network I/O trên Java 21](#virtual-threads-network-io)
- [Chọn blocking, selector hay asynchronous I/O](#network-io-model-choice)

## <a id="network-channels">SocketChannel, ServerSocketChannel và DatagramChannel</a>

<details>
<summary>Click for details</summary>

NIO networking giữ nguyên các khái niệm transport đã học ở TCP/UDP, nhưng thay lớp bọc stream bằng **channel + `ByteBuffer`** và cho phép chọn blocking hoặc non-blocking ở các channel có thể select.

- `SocketChannel` là channel cho socket hướng luồng và hỗ trợ `read`/`write` bằng `ByteBuffer`. Channel có thể đang chưa connect, đang chờ hoàn tất connect hoặc đã connected. `open()` mặc định dùng cho Internet stream socket; khi mở với `StandardProtocolFamily.UNIX`, cùng abstraction này dùng endpoint Unix-domain thay vì địa chỉ Internet.
- `ServerSocketChannel` bind/listen và `accept()` kết nối mới; kết quả là một `SocketChannel` riêng cho kết nối đó.
- `DatagramChannel` gửi/nhận datagram; nó giữ message boundary của UDP thay vì biến dữ liệu thành byte stream.

```java
try (ServerSocketChannel server = ServerSocketChannel.open()) {
    server.bind(new InetSocketAddress(8080));

    try (SocketChannel connection = server.accept()) {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int read = connection.read(buffer);
    }
}
```

Các channel mới mặc định hoạt động theo blocking mode. Giá trị của NIO không nằm ở việc "channel luôn non-blocking"; cùng abstraction có thể dùng theo luồng xử lý blocking đơn giản hoặc chuyển sang non-blocking để đăng ký với `Selector`.

Với Internet stream socket, `SocketChannel` vẫn biểu diễn **byte stream**. Chuyển từ `Socket` sang `SocketChannel` không làm TCP xuất hiện message boundary. Protocol framing ở chương TCP vẫn áp dụng nguyên vẹn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="network-buffer-interaction">Buffer và partial network I/O</a>

<details>
<summary>Click for details</summary>

`ByteBuffer` vừa chứa byte vừa giữ trạng thái `position`, `limit` và `capacity`. I/O mạng cập nhật `position`, nên code phải quản lý trạng thái buffer cùng với trạng thái protocol.

Một lần `read()` hoặc `write()` **không đồng nghĩa toàn bộ frame đã được xử lý**:

- `read(buffer)` có thể đọc ít byte hơn `buffer.remaining()`; trong non-blocking mode nó có thể trả `0`; `-1` báo end-of-stream.
- `write(buffer)` có thể ghi ít hơn số byte còn lại. Nếu `buffer.hasRemaining()` vẫn đúng, phần còn lại phải được giữ để ghi tiếp.

Một thao tác ghi blocking đơn giản có thể lặp tới khi buffer hết dữ liệu:

```java
ByteBuffer out = StandardCharsets.UTF_8.encode("hello\n");
while (out.hasRemaining()) {
    channel.write(out);
}
```

Với non-blocking channel, không nên biến vòng lặp trên thành busy-spin khi `write()` trả `0`. Event loop thường giữ buffer đang gửi trong trạng thái kết nối và chỉ đăng ký `OP_WRITE` khi còn dữ liệu đang chờ gửi.

Phía đọc thường dùng chuỗi trạng thái:

```text
channel.read(buffer)
        ↓
buffer.flip()      // chuyển từ ghi vào buffer sang đọc dữ liệu đã nhận
        ↓
parse/tiêu thụ byte
        ↓
buffer.compact()   // giữ phần chưa parse, tạo chỗ cho lần read tiếp theo
```

`clear()` phù hợp khi toàn bộ dữ liệu cũ đã được tiêu thụ; `compact()` phù hợp khi còn partial frame. Đây là lý do trạng thái buffer và protocol framing phải được thiết kế cùng nhau trong mã mạng non-blocking.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="selector-readiness-model">Selector và mô hình readiness</a>

<details>
<summary>Click for details</summary>

`Selector` là một **multiplexor của `SelectableChannel`**. Một thread có thể chờ readiness của nhiều channel thay vì block riêng trên từng channel. Selector không thực thi logic nghiệp vụ và không phải thread pool.

Channel phải ở non-blocking mode trước khi đăng ký với selector:

```java
try (Selector selector = Selector.open();
     ServerSocketChannel server = ServerSocketChannel.open()) {

    server.bind(new InetSocketAddress(8080));
    server.configureBlocking(false);
    server.register(selector, SelectionKey.OP_ACCEPT);

    while (true) {
        selector.select();

        Iterator<SelectionKey> it = selector.selectedKeys().iterator();
        while (it.hasNext()) {
            SelectionKey key = it.next();
            it.remove();

            if (key.isAcceptable()) {
                // accept kết nối và đăng ký channel mới nếu cần
            }
        }
    }
}
```

Readiness trả lời câu hỏi kiểu "thao tác này **có vẻ có thể tiến triển ngay**", không hứa rằng thao tác kế tiếp sẽ hoàn tất toàn bộ việc đọc/ghi. Tài liệu `SelectionKey` gọi ready set là một gợi ý; trạng thái có thể thay đổi sau khi lần selection hoàn tất hoặc sau một thao tác I/O.

Các thao tác phổ biến là `OP_ACCEPT`, `OP_CONNECT`, `OP_READ` và `OP_WRITE`, tùy loại channel. `select()` có thể block tới khi có readiness, `selectNow()` kiểm tra mà không block, còn `wakeup()` giúp thread khác đánh thức selector đang chờ để event loop xử lý thay đổi đăng ký/trạng thái.

Khi dùng `selectedKeys()`, ứng dụng phải remove/clear các key đã xử lý. Selector thêm key ready vào selected-key set, nhưng không tự xóa từng key chỉ vì ứng dụng đã xử lý nó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="selection-key-lifecycle">Registration, interest set và SelectionKey</a>

<details>
<summary>Click for details</summary>

Mỗi cặp channel/selector có một `SelectionKey`. Lần đăng ký đầu tiên tạo key đó; nếu cùng channel đăng ký lại với cùng selector, API trả lại key hiện có và cập nhật interest set. Nếu dùng overload ba tham số, attachment hiện tại chỉ bị thay khi truyền attachment khác `null`. Cùng channel đăng ký với selector khác sẽ có key riêng. Vì vậy mỗi key nối một **channel**, một **selector** và trạng thái selection của đúng cặp đó.

Hai tập thao tác cần phân biệt:

- **interest set**: những thao tác ứng dụng muốn selector theo dõi ở lần selection tiếp theo, đọc/đổi qua `interestOps(...)`;
- **ready set**: những thao tác selector vừa phát hiện là ready, đọc qua `readyOps()` hoặc helper như `isReadable()`.

Ứng dụng thường attach trạng thái kết nối vào key:

```java
SelectionKey key = channel.register(selector, SelectionKey.OP_READ);
key.attach(new ConnectionState());
```

Khi có dữ liệu gửi ra, event loop có thể thêm `OP_WRITE`; sau khi buffer đang chờ đã gửi hết, bỏ `OP_WRITE` khỏi interest set. Nếu luôn quan tâm `OP_WRITE`, socket thường xuyên writable có thể khiến selector liên tục đánh thức và tạo busy loop.

Kết nối non-blocking cũng dùng state machine. `SocketChannel.connect(remote)` có thể trả `false`; channel sau đó được theo dõi với `OP_CONNECT`, và khi connectable thì gọi `finishConnect()` để hoàn tất hoặc nhận lỗi kết nối.

`cancel()` làm key không còn hợp lệ, nhưng việc hủy đăng ký thực tế được selector xử lý ở lần selection kế tiếp. Đóng channel hoặc selector cũng làm key mất hiệu lực. Vì thế event loop phải kiểm tra tính hợp lệ khi có khả năng thread khác đóng/cancel tài nguyên.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="asynchronous-channel-model">AsynchronousSocketChannel và completion model</a>

<details>
<summary>Click for details</summary>

`AsynchronousSocketChannel` và `AsynchronousServerSocketChannel` dùng **completion model**: ứng dụng khởi động thao tác, sau đó nhận kết quả khi thao tác hoàn tất. Đây là mental model khác với Selector, nơi ứng dụng được báo readiness rồi tự gọi I/O.

Hai kiểu nhận completion chính là `Future` và `CompletionHandler`:

```java
try (AsynchronousSocketChannel channel = AsynchronousSocketChannel.open()) {
    channel.connect(new InetSocketAddress("example.com", 8080)).get();

    ByteBuffer buffer = ByteBuffer.allocate(1024);
    Future<Integer> pendingRead = channel.read(buffer);
    int read = pendingRead.get();
}
```

Thao tác vẫn được khởi động theo mô hình asynchronous channel; tuy nhiên nếu bên gọi gọi `Future.get()` ngay như ví dụ trên thì chính bên gọi sẽ chờ kết quả tại điểm đó. `Future` không tự biến phần mã phía sau thành callback/pipeline composition.

Hoặc callback:

```java
channel.read(buffer, state, new CompletionHandler<Integer, State>() {
    @Override
    public void completed(Integer read, State state) {
        // thao tác đã hoàn tất; xử lý kết quả và khởi động bước tiếp theo
    }

    @Override
    public void failed(Throwable error, State state) {
        // xử lý lỗi và vòng đời tài nguyên
    }
});
```

Asynchronous API không loại bỏ partial I/O: kết quả completion vẫn là số byte thực sự đọc/ghi. Buffer cũng không an toàn để code khác thay đổi khi thao tác bất đồng bộ đang sử dụng nó.

Channel loại này có giới hạn về thao tác đang chờ. Ví dụ, bắt đầu một lần đọc mới khi lần đọc trước chưa hoàn tất có thể gây `ReadPendingException`; tương tự có `WritePendingException`, và asynchronous server chỉ cho phép một `accept` đang chờ tại một thời điểm (`AcceptPendingException`). Thiết kế state machine vẫn cần trách nhiệm sở hữu rõ ràng.

Các channel có thể thuộc một `AsynchronousChannelGroup`, nơi cách triển khai quản lý tài nguyên thực thi cho completion. Đây là cơ chế completion của networking; thiết kế executor/concurrency sâu hơn thuộc module Concurrency.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="unix-domain-channels">Unix-domain sockets với network channels</a>

<details>
<summary>Click for details</summary>

Không phải mọi socket channel đều dùng IP + port. JDK hỗ trợ **Unix-domain sockets** qua `SocketChannel` và `ServerSocketChannel`, với endpoint là `UnixDomainSocketAddress` chứa một đường dẫn hệ thống tệp.

```java
Path socketPath = Path.of("/tmp/java-learning.sock");
Files.deleteIfExists(socketPath);

UnixDomainSocketAddress address = UnixDomainSocketAddress.of(socketPath);

try (ServerSocketChannel server =
         ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
    server.bind(address);
    try (SocketChannel connection = server.accept()) {
        // read/write ByteBuffer như stream-oriented SocketChannel khác
    }
} finally {
    Files.deleteIfExists(socketPath);
}
```

Client có thể mở channel thuộc `StandardProtocolFamily.UNIX` rồi `connect(address)`. Transport này dành cho IPC trên cùng máy; nó không phải một cách viết khác của `InetSocketAddress`.

Khả năng hỗ trợ phụ thuộc nền tảng/provider. Mã phải chấp nhận trường hợp protocol family không được hỗ trợ. Ngoài ra, khi bind Unix-domain server vào một đường dẫn có tên, socket file **vẫn tồn tại sau khi channel đóng** theo Java API; ứng dụng chịu trách nhiệm dọn đường dẫn trước khi bind lại khi phù hợp.

`UnixDomainSocketAddress` dùng `Path` từ hệ thống tệp mặc định của hệ thống, và nền tảng còn có giới hạn độ dài đường dẫn phụ thuộc cách triển khai. Những chi tiết này là lý do abstraction endpoint ở Java rộng hơn IP address + port.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-threads-network-io">Virtual threads và blocking network I/O trên Java 21</a>

<details>
<summary>Click for details</summary>

Trước virtual threads, một lý do phổ biến để chọn Selector là tránh giữ một platform thread đắt cho mỗi kết nối đang block. Java 21 thay đổi trade-off đó.

Theo JEP 444, khi mã trong virtual thread block trên phần lớn I/O mạng của `java.net` và `java.nio.channels`, runtime có thể tạm dừng virtual thread và giải phóng carrier platform thread để chạy việc khác. Vì vậy **nhiều kết nối đồng thời không tự động đồng nghĩa phải chuyển sang non-blocking Selector**.

Trên Java 21, lợi ích này giả định virtual thread có thể unmount khỏi carrier. Nếu mã xung quanh làm virtual thread bị pinned, ví dụ block trong một số vùng `synchronized` hoặc native call, carrier có thể vẫn bị giữ trong thời gian block. Cơ chế pinning chi tiết thuộc module Virtual Threads/Concurrency; Networking chỉ cần giữ giới hạn này khi so sánh các mô hình I/O.

Ví dụ mô hình thread-per-connection vẫn có thể giữ luồng xử lý tuần tự, dễ đọc:

```java
try (var executor = Executors.newVirtualThreadPerTaskExecutor();
     ServerSocket server = new ServerSocket(8080)) {

    while (!server.isClosed()) {
        Socket socket = server.accept();
        executor.submit(() -> {
            try (socket) {
                handle(socket);
            }
            return null;
        });
    }
}
```

JDK 21 còn quy định các thao tác blocking của `Socket`, `ServerSocket` và `DatagramSocket` mặc định hệ thống là interruptible khi chạy trong virtual thread: interrupt sẽ đánh thức virtual thread và đóng socket. Các channel vốn là `InterruptibleChannel` đã có semantics cancellation/interrupt riêng.

Virtual threads không làm I/O nhanh hơn và không xóa các giới hạn khác như bandwidth, độ trễ từ xa, connection pool hoặc năng lực xử lý phía sau. Selector vẫn phù hợp khi cần event-loop state machine và kiểm soát readiness rõ ràng; asynchronous channels vẫn hữu ích khi API/composition cần kiểu completion.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="network-io-model-choice">Chọn blocking, selector hay asynchronous I/O</a>

<details>
<summary>Click for details</summary>

Ba mô hình đều hợp lệ; quyết định nên bắt đầu từ **semantics và mức kiểm soát cần thiết**, không từ giả định "NIO luôn nhanh hơn".

| Mô hình | Khi phù hợp | Chi phí thiết kế cần chấp nhận |
|---|---|---|
| Blocking socket/channel + thread/virtual thread per task | Luồng request/kết nối tuần tự, mã dễ đọc; Java 21 virtual threads cho phép concurrency lớn hơn với kiểu blocking | Vẫn cần giới hạn tài nguyên, timeout/cancellation và không được tạo tải vô hạn cho hệ thống phía sau |
| Non-blocking `SelectableChannel` + `Selector` | Cần một hoặc vài event-loop thread multiplex nhiều channel, kiểm soát readiness và buffer/trạng thái đang chờ trực tiếp | Phải tự quản state machine, partial I/O, interest ops, fairness và vòng đời |
| `Asynchronous*Channel` | Hệ thống tích hợp tự nhiên với completion callback/Future hoặc muốn API theo completion của thao tác | Trạng thái callback/completion, quy tắc thao tác đang chờ và vòng đời channel group phức tạp hơn |

Một quy tắc thực tế:

```text
luồng blocking đáp ứng được yêu cầu
        ↓
giữ blocking model đơn giản, cân nhắc virtual threads trên Java 21

cần explicit readiness multiplexing
        ↓
Selector + non-blocking channels

cần completion-oriented API
        ↓
Asynchronous channels
```

Không mô hình nào tự giải quyết framing cấp ứng dụng, chính sách backpressure, admission control hay tính đúng đắn của concurrency. NIO cung cấp cơ chế I/O; các chính sách cấp ứng dụng vẫn phải được thiết kế ở module sở hữu phù hợp.

Sau khi đã hiểu ba mô hình I/O cấp thấp, chương tiếp theo chuyển lên abstraction cao hơn là JDK HttpClient để thấy cách HTTP che phần lớn socket/channel mechanics nhưng vẫn giữ những concern về completion, timeout và vòng đời tài nguyên.

</details>

- [Quay lại đầu trang](#back-to-top)
