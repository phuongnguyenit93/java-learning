<a id="back-to-top"></a>

# UDP và giao tiếp datagram

## Menu
- [Mô hình datagram và DatagramSocket](#udp-datagram-model)
- [DatagramPacket và địa chỉ từng gói](#datagram-packet-addressing)
- [Thứ tự, giao hàng và giới hạn độ tin cậy](#udp-delivery-semantics)
- [Ý nghĩa của DatagramSocket.connect](#datagram-connect-semantics)
- [Broadcast và multicast](#udp-broadcast-multicast)
- [DatagramChannel và ranh giới với NIO](#datagram-channel-boundary)

## <a id="udp-datagram-model">Mô hình datagram và DatagramSocket</a>

<details>
<summary>Click for details</summary>

UDP dùng mô hình **datagram**: mỗi lần gửi tạo ra một đơn vị dữ liệu độc lập có payload và endpoint đích. Java biểu diễn socket UDP bằng `DatagramSocket` và đơn vị gửi/nhận bằng `DatagramPacket`.

Khác với TCP, phía nhận không có `listen()`/`accept()` để tạo một kết nối mới cho từng peer. Một `DatagramSocket` thường bind vào endpoint local rồi nhận nhiều datagram trên cùng socket:

```text
bên gửi A ---- datagram ----\
                              > DatagramSocket bind vào cổng local
bên gửi B ---- datagram ----/
```

Ví dụ phía nhận tối giản:

```java
try (DatagramSocket socket = new DatagramSocket(9000)) {
    byte[] buffer = new byte[2048];
    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

    socket.receive(packet);

    String text = new String(
            packet.getData(),
            packet.getOffset(),
            packet.getLength(),
            StandardCharsets.UTF_8);
}
```

`receive()` trên `DatagramSocket` ở chế độ blocking thông thường sẽ chờ cho tới khi một datagram đến hoặc thao tác kết thúc vì timeout/lỗi/đóng socket. Mỗi lần `receive()` xử lý tối đa một datagram; Java không ghép nhiều datagram thành một byte stream như TCP.

UDP phù hợp khi ứng dụng muốn giữ ranh giới message và chấp nhận tự quyết định cách xử lý mất gói, thứ tự, retry hoặc dữ liệu cũ. Những chính sách đó thuộc protocol/tầng ứng dụng; `DatagramSocket` chỉ cung cấp cơ chế gửi và nhận datagram.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="datagram-packet-addressing">DatagramPacket và địa chỉ từng gói</a>

<details>
<summary>Click for details</summary>

`DatagramPacket` mang cả **vùng dữ liệu** và **địa chỉ peer**. Ý nghĩa của địa chỉ phụ thuộc packet đang được dùng để gửi hay vừa được nhận.

Khi gửi, packet chứa địa chỉ đích:

```java
byte[] data = "PING".getBytes(StandardCharsets.UTF_8);
InetSocketAddress target = new InetSocketAddress("127.0.0.1", 9000);

DatagramPacket packet = new DatagramPacket(data, data.length, target);
socket.send(packet);
```

Khi nhận, `receive(packet)` điền buffer và cập nhật packet với địa chỉ/cổng của bên gửi. Vì thế một socket chưa `connect()` có thể trả lời đúng peer của từng datagram:

```java
byte[] buffer = new byte[2048];
DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
socket.receive(packet);

SocketAddress sender = packet.getSocketAddress();
int receivedLength = packet.getLength();

DatagramPacket reply = new DatagramPacket(
        packet.getData(),
        packet.getOffset(),
        receivedLength,
        sender);
socket.send(reply);
```

`getOffset()` và `getLength()` xác định vùng dữ liệu hợp lệ trong mảng nền; không nên xử lý toàn bộ `getData()` như thể mọi byte trong mảng đều thuộc message hiện tại.

Buffer nhận cũng đặt giới hạn cho datagram. Nếu message đến dài hơn length hiện tại của `DatagramPacket`, Java chỉ đặt phần vừa vào packet và **phần vượt quá bị cắt bỏ (truncate)** cho lần nhận đó. UDP không biến phần còn lại thành một `receive()` tiếp theo. Vì vậy kích thước buffer phải phù hợp với contract của protocol.

Sau khi nhận, `getLength()` cho biết độ dài message vừa nhận. Nếu ứng dụng chủ động gọi `setLength(...)`, giá trị đó sẽ đặt giới hạn vùng buffer dùng cho lần nhận tiếp theo; vì vậy chỉ thay đổi length khi protocol hoặc cách quản lý buffer thực sự yêu cầu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="udp-delivery-semantics">Thứ tự, giao hàng và giới hạn độ tin cậy</a>

<details>
<summary>Click for details</summary>

UDP bảo toàn **ranh giới datagram** tại API: một lần gửi tương ứng với một datagram, và một lần receive nhận một datagram (hoặc phần đầu của nó nếu buffer quá nhỏ). Điều đó không đồng nghĩa với bảo đảm giao hàng đáng tin cậy.

`DatagramSocket` không bổ sung các bảo đảm kiểu TCP cho ứng dụng:

- datagram có thể không đến;
- các datagram có thể đến khác thứ tự gửi;
- ứng dụng không nên dựa vào việc mỗi datagram chỉ xuất hiện đúng một lần nếu protocol của nó cần semantics chặt chẽ hơn;
- bên gửi không nhận được một acknowledgment ở cấp stream từ `DatagramSocket` để chứng minh ứng dụng phía peer đã xử lý message.

Do đó, nếu bài toán cần sequence number, acknowledgment, retry, phát hiện trùng lặp, thời hạn hiệu lực hoặc reassembly ở tầng ứng dụng, các quy tắc đó phải được protocol của ứng dụng định nghĩa rõ. JDK UDP API không tự biến UDP thành một phiên đáng tin cậy.

Ví dụ, một mẫu telemetry độc lập có thể chấp nhận mất một vài datagram và ưu tiên độ đơn giản/độ trễ. Ngược lại, một luồng công việc "mỗi command phải được xử lý đúng theo thứ tự" cần thêm semantics ở protocol hoặc một transport phù hợp hơn.

Điểm phân biệt cần giữ với TCP là:

```text
TCP -> byte stream có thứ tự; ứng dụng tự framing message
UDP -> có sẵn ranh giới datagram; ứng dụng tự xử lý reliability/order nếu cần
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="datagram-connect-semantics">Ý nghĩa của DatagramSocket.connect</a>

<details>
<summary>Click for details</summary>

Tên `DatagramSocket.connect(...)` dễ gây nhầm nếu áp mental model của TCP. Với UDP, `connect` **không thực hiện handshake kiểu TCP và không tạo một phiên đáng tin cậy**. Nó gắn socket với một IP/port từ xa cụ thể.

Sau khi connect:

- socket có một peer từ xa mặc định;
- với peer unicast thông thường, datagram nhận qua socket được lọc theo đúng peer đã gắn;
- `send()` phải nhắm tới peer phù hợp; packet mang địa chỉ khác với peer từ xa đã connect sẽ bị từ chối;
- mã có thể dùng thông tin peer từ xa từ `getRemoteSocketAddress()` và một số API trở nên thuận tiện hơn.

Địa chỉ multicast là trường hợp đặc biệt: datagram socket connect tới địa chỉ multicast chỉ dùng để gửi và không có cùng mô hình lọc phía nhận như unicast.

```java
try (DatagramSocket socket = new DatagramSocket()) {
    InetSocketAddress peer = new InetSocketAddress("127.0.0.1", 9000);
    socket.connect(peer);

    byte[] data = "PING".getBytes(StandardCharsets.UTF_8);
    DatagramPacket packet = new DatagramPacket(data, data.length, peer);
    socket.send(packet); // target phải khớp peer đã connect
}
```

`connect()` không làm peer phải tồn tại hay phải "accept" kết nối. Việc một datagram gửi đi có đến hay được ứng dụng phía bên kia xử lý vẫn tuân theo UDP semantics. Với datagram socket đã connect, lỗi đích không thể truy cập **có thể** xuất hiện dưới dạng `PortUnreachableException`, nhưng Java API không bảo đảm exception này luôn xảy ra.

Khi `disconnect()` thành công, nó bỏ liên kết với peer từ xa và đưa socket trở lại cách dùng chưa connect, nơi mỗi packet có thể chỉ định peer riêng. Java 21 còn cho phép `disconnect()` ném `UncheckedIOException` nếu việc ngắt liên kết gặp lỗi; khi đó trạng thái socket có thể không xác định và cách an toàn là đóng socket thay vì tiếp tục tái sử dụng. Vì vậy hãy dùng UDP `connect()` khi một socket chủ yếu giao tiếp với một peer và việc lọc peer/địa chỉ đích mặc định có ích, chứ không phải vì cần vòng đời kết nối kiểu TCP.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="udp-broadcast-multicast">Broadcast và multicast</a>

<details>
<summary>Click for details</summary>

Broadcast và multicast đều gửi datagram tới nhiều phía nhận tiềm năng, nhưng chúng dùng hai mô hình địa chỉ khác nhau.

**Broadcast** dùng một broadcast address của mạng IPv4. `DatagramSocket` có socket option `SO_BROADCAST`; trên các cách triển khai phù hợp, socket mới thường bật option này để cho phép gửi broadcast. Java API khuyến nghị phía nhận muốn nhận broadcast nên bind vào wildcard address vì việc nhận broadcast khi bind vào một địa chỉ cụ thể có thể phụ thuộc cách triển khai.

```java
try (DatagramSocket socket = new DatagramSocket()) {
    socket.setBroadcast(true);

    byte[] data = "DISCOVER".getBytes(StandardCharsets.UTF_8);
    DatagramPacket packet = new DatagramPacket(
            data,
            data.length,
            new InetSocketAddress("255.255.255.255", 9000));
    socket.send(packet);
}
```

Địa chỉ broadcast thực tế thường phụ thuộc mạng/subnet; ví dụ trên chỉ minh họa ranh giới API.

**Multicast** gửi tới một multicast group. Java cung cấp `MulticastSocket`, một lớp con của `DatagramSocket`, để join/leave group và chọn network interface phù hợp. Trên Java 21, overload nên ưu tiên là `joinGroup(SocketAddress, NetworkInterface)` vì nó xác định rõ interface local; overload cũ chỉ nhận `InetAddress` đã deprecated.

```java
NetworkInterface netIf = NetworkInterface.getByName("eth0");
InetSocketAddress group = new InetSocketAddress("239.10.10.10", 9000);

try (MulticastSocket socket = new MulticastSocket(9000)) {
    socket.joinGroup(group, netIf);
    try {
        byte[] buffer = new byte[2048];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);
    } finally {
        socket.leaveGroup(group, netIf);
    }
}
```

Multicast còn liên quan tới interface gửi ra và phạm vi TTL/hop. Đây là cơ chế socket/networking thuộc module này; việc thiết kế discovery protocol, chiến lược membership hoặc topology multicast lớn hơn thuộc phạm vi hệ thống/protocol tương ứng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="datagram-channel-boundary">DatagramChannel và ranh giới với NIO</a>

<details>
<summary>Click for details</summary>

`DatagramChannel` là đối tác NIO của `DatagramSocket`. Nó vẫn gửi và nhận **datagram**, nhưng dùng `ByteBuffer` và mô hình channel thay vì `DatagramPacket` làm abstraction chính.

Channel chưa connect dùng `send`/`receive` với địa chỉ của từng datagram:

```java
try (DatagramChannel channel = DatagramChannel.open()) {
    channel.bind(new InetSocketAddress(9000));

    ByteBuffer buffer = ByteBuffer.allocate(2048);
    SocketAddress sender = channel.receive(buffer);

    if (sender != null) {
        buffer.flip();
        channel.send(buffer, sender);
    }
}
```

Sau `connect(remote)`, `DatagramChannel` gắn với một peer cho tới khi `disconnect()` hoặc `close()`. Khi đó `read`/`write` có thể được dùng với peer đã connect; channel vẫn là transport datagram chứ không trở thành TCP stream.

Ranh giới quan trọng với NIO là mô hình thực thi:

- blocking mode: `receive()` có thể chờ tới khi datagram đến;
- non-blocking mode: nếu chưa có datagram sẵn, `receive()` trả `null` ngay;
- vì là `SelectableChannel`, `DatagramChannel` có thể đăng ký với `Selector` trong luồng xử lý non-blocking;
- multicast membership cũng có API channel riêng thông qua `join(...)` và `MembershipKey`.

Chương này chỉ đặt `DatagramChannel` vào đúng mô hình tư duy UDP. Chương kế tiếp trước hết thiết lập timeout, lỗi, cancellation và vòng đời tài nguyên áp dụng xuyên các kiểu socket; chương NIO Networking sau đó mới phát triển trạng thái `ByteBuffer`, selector readiness, registration/interest set và các mô hình thực thi của channel.

</details>

- [Quay lại đầu trang](#back-to-top)
