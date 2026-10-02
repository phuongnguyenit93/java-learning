<a id="back-to-top"></a>

# TCP và socket dạng luồng

## Menu
- [Mô hình kết nối TCP trong Java](#tcp-connection-model)
- [Bind, connect và accept](#tcp-bind-connect-accept)
- [Vòng đời Socket và ServerSocket](#tcp-client-server-lifecycle)
- [Đọc ghi qua InputStream và OutputStream](#tcp-stream-io)
- [Luồng byte và ranh giới thông điệp](#tcp-message-framing)
- [Half-close, shutdownInput và shutdownOutput](#tcp-half-close)
- [Đóng kết nối và trách nhiệm sở hữu tài nguyên](#tcp-resource-lifecycle)

## <a id="tcp-connection-model">Mô hình kết nối TCP trong Java</a>

<details>
<summary>Click for details</summary>

TCP cung cấp một **kết nối hai chiều theo luồng byte** giữa hai endpoint. Trong Java, `Socket` biểu diễn một đầu của kết nối đó; phía server dùng `ServerSocket` để chờ kết nối mới. Khi một client kết nối thành công, server không đọc/ghi trực tiếp trên `ServerSocket`: `accept()` trả về một `Socket` mới dành riêng cho kết nối với client đó.

Có thể hình dung quan hệ như sau:

```text
Socket phía client  <====== kết nối TCP ======>  Socket đã được accept
                                                      ^
                                                      |
                                                ServerSocket
                                             bind + chờ trong accept()
```

Điểm quan trọng là TCP nhìn dữ liệu như một chuỗi byte có thứ tự. API `Socket` đưa chuỗi byte này ra dưới dạng `InputStream` và `OutputStream`, nhưng TCP không biết đâu là "request", "record" hay "message" của ứng dụng. Ranh giới dữ liệu phải do protocol ở tầng ứng dụng quy định.

Một kết nối đã được thiết lập có hai endpoint đầy đủ: địa chỉ/cổng local và địa chỉ/cổng remote. Vì vậy một server có thể dùng cùng một cổng lắng nghe để phục vụ nhiều client, trong khi mỗi `Socket` được chấp nhận vẫn đại diện cho một kết nối riêng biệt.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tcp-bind-connect-accept">Bind, connect và accept</a>

<details>
<summary>Click for details</summary>

Ba thao tác `bind`, `connect` và `accept` mô tả ba vai trò khác nhau trong quá trình hình thành kết nối TCP.

- `bind` gắn socket với một endpoint local. `ServerSocket` thường phải bind trước khi nhận kết nối. Client cũng có thể bind `Socket` vào một địa chỉ/cổng local cụ thể khi có yêu cầu; nếu không, hệ điều hành thường chọn địa chỉ và ephemeral port phù hợp khi kết nối.
- `connect` thuộc phía chủ động: `Socket` client yêu cầu thiết lập kết nối tới endpoint của server.
- `accept` thuộc phía thụ động: `ServerSocket` chờ một kết nối đến và trả về một `Socket` mới khi kết nối được chấp nhận.

Ví dụ tạo server socket chưa bind rồi bind rõ ràng:

```java
try (ServerSocket server = new ServerSocket()) {
    server.bind(new InetSocketAddress("0.0.0.0", 8080));

    try (Socket client = server.accept()) {
        // client là kết nối riêng vừa được accept.
    }
}
```

`0.0.0.0` là wildcard address cho IPv4 trong ví dụ này: server nhận kết nối tới các địa chỉ local phù hợp trên máy. Nếu bind với port `0`, hệ thống chọn một ephemeral port; có thể đọc port thực tế bằng `getLocalPort()` sau khi bind.

`ServerSocket.bind(endpoint, backlog)` còn nhận `backlog`, tức độ dài tối đa **được yêu cầu** cho hàng đợi kết nối đến đang chờ xử lý. Giá trị thực tế và cách áp dụng backlog phụ thuộc cách triển khai/hệ điều hành, vì vậy không nên coi nó là một giới hạn ứng dụng chính xác tuyệt đối.

`accept()` là lời gọi blocking trong kiểu `ServerSocket` thông thường: nếu chưa có kết nối phù hợp, thread chờ tại đó. Timeout/cancellation và kiểm soát lỗi được xử lý sâu hơn ở chương riêng; ở đây điều cần giữ là `accept()` tạo ra một socket kết nối mới, không biến chính `ServerSocket` thành kết nối với client.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tcp-client-server-lifecycle">Vòng đời Socket và ServerSocket</a>

<details>
<summary>Click for details</summary>

Vòng đời cơ bản của client và server có thể đọc theo hai luồng song song:

```text
Server                                  Client
------                                  ------
tạo ServerSocket                        tạo Socket
bind endpoint local                     connect endpoint remote
accept() chờ kết nối đến                kết nối TCP được thiết lập
                                        với endpoint của server
   |
Socket đã được accept
   |                                    |
đọc/ghi byte <------------------------> đọc/ghi byte
   |                                    |
đóng Socket đã accept                  đóng Socket

ServerSocket có thể tiếp tục accept kết nối khác cho tới khi chính nó bị close.
```

Một server tuần tự tối giản có thể minh họa trách nhiệm sở hữu của từng socket:

```java
try (ServerSocket server = new ServerSocket(8080)) {
    while (!server.isClosed()) {
        try (Socket connection = server.accept()) {
            handle(connection);
        }
    }
}
```

Trong server thực tế, việc xử lý nhiều kết nối đồng thời cần một mô hình concurrency phù hợp. Chương Networking này chỉ xác định ranh giới: mỗi kết nối có `Socket` và vòng đời riêng; thiết kế thread/executor/virtual thread thuộc module concurrency tương ứng.

Phía client thường gói toàn bộ phiên giao tiếp trong một `try-with-resources`:

```java
try (Socket socket = new Socket()) {
    socket.connect(new InetSocketAddress("example.com", 8080));

    // sử dụng socket.getInputStream() và socket.getOutputStream()
}
```

Sau khi `close()`, socket không thể tái sử dụng cho một kết nối mới. Nếu cần kết nối khác, tạo socket khác. Tương tự, một `Socket` được `accept()` kết thúc không làm `ServerSocket` tự đóng; listener và kết nối là hai tài nguyên khác nhau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tcp-stream-io">Đọc ghi qua InputStream và OutputStream</a>

<details>
<summary>Click for details</summary>

Sau khi `Socket` đã kết nối, `getInputStream()` cung cấp luồng byte đi **vào** endpoint local và `getOutputStream()` cung cấp luồng byte đi **ra** remote endpoint.

```java
InputStream in = socket.getInputStream();
OutputStream out = socket.getOutputStream();

out.write("PING\n".getBytes(StandardCharsets.UTF_8));
out.flush();

byte[] buffer = new byte[1024];
int read = in.read(buffer);
if (read == -1) {
    // peer đã kết thúc chiều gửi và mọi byte đã nhận được đã được đọc hết.
}
```

`read(byte[])` không hứa sẽ lấp đầy buffer. Nó có thể trả về ít byte hơn kích thước buffer ngay cả khi bên gửi đã gọi `write()` với một mảng lớn. Ngược lại, một lần `read()` cũng có thể nhận byte xuất phát từ nhiều lần `write()` trước đó. Đây là hệ quả trực tiếp của mô hình **byte stream**.

`write()` đưa byte vào cơ chế truyền của socket nhưng không biến mỗi lần gọi thành một message trên đường truyền. Nếu bọc socket bằng `BufferedOutputStream`, `BufferedWriter`, `DataOutputStream` hoặc writer khác, phải hiểu buffer của lớp bọc. `flush()` đặc biệt quan trọng khi lớp bọc đang giữ dữ liệu mà peer cần nhận trước khi có thể tiếp tục protocol.

Khi peer đóng bình thường chiều gửi và toàn bộ byte đã nhận đã được tiêu thụ, `InputStream.read()` trả `-1`. Giá trị `-1` là EOF của luồng, không phải một byte dữ liệu và không đồng nghĩa với "hiện tại chưa có dữ liệu".

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tcp-message-framing">Luồng byte và ranh giới thông điệp</a>

<details>
<summary>Click for details</summary>

TCP bảo toàn thứ tự byte, nhưng **không bảo toàn ranh giới giữa các lần `write()`**. Vì vậy mã kiểu "bên gửi gọi `write()` một lần thì bên nhận gọi `read()` một lần" không phải một protocol hợp lệ.

Giả sử bên gửi gửi hai payload:

```text
write("HELLO")
write("JAVA")
```

Bên nhận có thể quan sát các lần đọc như `"HEL"`, `"LOJA"`, `"VA"`, hoặc một cách chia khác. Chuỗi byte cuối cùng vẫn theo thứ tự `HELLOJAVA`; chỉ có kích thước từng lần đọc thay đổi.

Ứng dụng cần một quy tắc **framing**. Một số cách phổ biến:

- fixed length: mọi record có kích thước cố định;
- delimiter: ví dụ protocol text kết thúc từng dòng bằng `\n`, kèm quy tắc escape/encoding nếu payload có thể chứa delimiter;
- length prefix: gửi độ dài trước, sau đó đọc đúng số byte của payload;
- self-describing format: parser xác định điểm kết thúc theo format, nhưng vẫn cần xử lý dữ liệu đến theo từng phần.

Ví dụ length-prefix đơn giản:

```java
// sender
byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
DataOutputStream out = new DataOutputStream(socket.getOutputStream());
out.writeInt(payload.length);
out.write(payload);
out.flush();

// receiver
DataInputStream in = new DataInputStream(socket.getInputStream());
int length = in.readInt();
if (length < 0 || length > 1_048_576) {
    throw new IOException("Invalid frame length: " + length);
}
byte[] message = in.readNBytes(length);
if (message.length != length) {
    throw new EOFException("Connection ended in the middle of a frame");
}
```

Giới hạn `length` trước khi cấp phát bộ nhớ là một phần của framing an toàn. `readNBytes(length)` giúp mã yêu cầu đủ số byte, nhưng EOF vẫn có thể xuất hiện giữa frame và phải được xử lý như lỗi protocol hoặc việc kết thúc kết nối phù hợp với thiết kế ứng dụng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tcp-half-close">Half-close, shutdownInput và shutdownOutput</a>

<details>
<summary>Click for details</summary>

TCP là full-duplex: hai chiều truyền có thể kết thúc độc lập. Java thể hiện khả năng này bằng `shutdownOutput()` và `shutdownInput()` trên `Socket`.

`shutdownOutput()` nói rằng endpoint local **không gửi thêm byte nữa**. Với TCP, các byte đã ghi trước đó được gửi, sau đó TCP thực hiện trình tự kết thúc bình thường cho chiều output. Peer vẫn có thể gửi dữ liệu ngược lại, và local socket vẫn có thể đọc dữ liệu đó.

Một cách dùng hữu ích là request có độ dài kết thúc bằng EOF của một chiều:

```java
try (Socket socket = new Socket("example.com", 8080)) {
    OutputStream out = socket.getOutputStream();
    out.write(requestBytes);
    out.flush();

    socket.shutdownOutput(); // báo: request đã gửi xong

    byte[] response = socket.getInputStream().readAllBytes();
    // Peer có thể gửi response rồi kết thúc chiều gửi của nó.
}
```

Điểm khác với `close()` là half-close vẫn giữ chiều còn lại hoạt động. Cũng vì vậy, **không đóng `OutputStream` để mô phỏng half-close**: theo contract của `Socket`, đóng stream trả về từ `getInputStream()` hoặc `getOutputStream()` sẽ đóng socket liên kết.

`shutdownInput()` đặt phía đọc local vào trạng thái end-of-stream; các lần đọc tiếp theo trả EOF theo contract của `Socket`. Đây là thao tác local cho chiều input, không phải cách gửi một message ứng dụng cho peer rằng "tôi không muốn nhận nữa". Thông thường protocol dùng `shutdownOutput()` để tạo tín hiệu EOF có ý nghĩa cho phía bên kia, còn `shutdownInput()` ít cần hơn trong mã ứng dụng.

Có thể kiểm tra trạng thái hai chiều bằng `isInputShutdown()` và `isOutputShutdown()`, nhưng các cờ này không thay thế việc xử lý EOF/`IOException` trong luồng I/O thực tế.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tcp-resource-lifecycle">Đóng kết nối và trách nhiệm sở hữu tài nguyên</a>

<details>
<summary>Click for details</summary>

Kết nối mạng giữ tài nguyên hệ điều hành, vì vậy mã cần xác định rõ **ai sở hữu socket và ai chịu trách nhiệm đóng nó**. Quy tắc đơn giản nhất là thành phần nhận trách nhiệm sở hữu kết nối sẽ đóng kết nối trong `try-with-resources` khi phiên làm việc kết thúc.

```java
void handleClient(ServerSocket server) throws IOException {
    try (Socket socket = server.accept()) {
        process(socket);
    } // socket của client được đóng ở đây
}
```

`ServerSocket` và các `Socket` đã được `accept()` là các tài nguyên độc lập:

- đóng một `Socket` đã được `accept()` sẽ kết thúc kết nối đó nhưng server vẫn có thể `accept()` kết nối khác;
- đóng `ServerSocket` dừng listener và đánh thức thread đang block trong `accept()` bằng lỗi đóng socket;
- đóng `ServerSocket` không phải là cơ chế tự động quản lý mọi kết nối đã được `accept()` và chuyển cho phần xử lý khác.

`Socket` cũng sở hữu hai stream gắn với cùng kết nối. Theo Java API, đóng `InputStream` hoặc `OutputStream` lấy từ socket sẽ đóng socket liên kết. Vì vậy tránh thiết kế trách nhiệm sở hữu trong đó nhiều lớp độc lập cùng "sở hữu" từng stream mà không biết rằng một lớp đóng stream sẽ ảnh hưởng cả kết nối.

`try-with-resources` giúp dọn tài nguyên khi xử lý thành công lẫn khi có exception:

```java
try (Socket socket = new Socket("example.com", 8080)) {
    InputStream in = socket.getInputStream();
    OutputStream out = socket.getOutputStream();
    exchange(in, out);
}
```

Ở đây `Socket` là tài nguyên có chủ sở hữu rõ ràng; việc đóng socket cũng kết thúc các stream gắn với kết nối đó. Cách này tránh tạo cảm giác rằng ba tài nguyên độc lập có ba vòng đời mạng khác nhau.

Khi cần half-close, quản lý vòng đời ở cấp `Socket` và dùng `shutdownOutput()`/`shutdownInput()` đúng mục đích thay vì đóng stream sớm. Timeout, reset, cancellation và các dạng lỗi khác được tách sang chương Failure Control; nguyên tắc xuyên suốt ở đây là mọi đường thoát đều phải đưa tài nguyên về trạng thái đóng xác định.

Trước khi đi vào các failure control dùng chung đó, chương kế tiếp sẽ đối chiếu byte stream có kết nối của TCP với mô hình datagram độc lập của UDP để tách rõ transport/data semantics khỏi lifecycle policy.

</details>

- [Quay lại đầu trang](#back-to-top)
