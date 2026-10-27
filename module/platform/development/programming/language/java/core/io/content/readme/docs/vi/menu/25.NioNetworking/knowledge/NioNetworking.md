<a id="back-to-top"></a>

# NIO mạng

## Menu
- [SocketChannel, ServerSocketChannel và DatagramChannel](#nio-networking-001)
- [Chế độ blocking và non-blocking](#nio-networking-002)
- [Connect, finishConnect, accept, read và write](#nio-networking-003)
- [TCP là luồng byte, không bảo toàn ranh giới bản tin](#nio-networking-004)
- [Thiết kế framing bằng độ dài hoặc dấu phân cách](#nio-networking-005)
- [Partial read, partial write và lưu trạng thái giữa các lần sẵn sàng](#nio-networking-006)
- [EOF, đóng kết nối và đóng một chiều](#nio-networking-007)
- [Datagram và khác biệt với luồng TCP](#nio-networking-008)
- [Phạm vi I/O mạng và các kiến thức giao thức cần học riêng](#nio-networking-009)

## <a id="nio-networking-001">SocketChannel, ServerSocketChannel và DatagramChannel</a>

<details>
<summary>Xem chi tiết</summary>

# SocketChannel, ServerSocketChannel và DatagramChannel trong Java NIO

## 1. Ba channel mạng giải quyết ba nhu cầu khác nhau

Java NIO (`java.nio.channels`) cung cấp các channel để giao tiếp mạng thông qua `ByteBuffer`. Ba loại quan trọng:

- **`SocketChannel`:** đầu cuối của kết nối luồng byte, thường là **TCP**. Có thể đóng vai trò client hoặc là kênh kết nối được server chấp nhận.
- **`ServerSocketChannel`:** đầu lắng nghe kết nối, thường là **TCP server**; gọi `accept()` để nhận một `SocketChannel` mới cho client cụ thể.
- **`DatagramChannel`:** gửi/nhận các gói tin có ranh giới thông điệp (datagram), thường là **UDP**; không cần một kết nối TCP.

Tất cả sử dụng `ByteBuffer`, đều là `SelectableChannel` và có thể hoạt động **blocking** hoặc **non-blocking**, phù hợp cả code đơn giản và event loop `Selector`. Nhưng semantics dữ liệu và vòng đời của chúng khác nhau: **TCP là stream có thứ tự, UDP là datagram với ranh giới gói**.

Bảng định hướng:

| Tiêu chí | `SocketChannel` | `ServerSocketChannel` | `DatagramChannel` |
|---|---|---|---|
| Vai trò | Đọc/ghi trên kết nối | Lắng nghe, nhận kết nối | Gửi/nhận datagram |
| Giao thức thông thường | TCP | TCP | UDP |
| Dữ liệu ứng dụng | Luồng byte, không có ranh giới bản tin | Không trực tiếp mang payload | Mỗi datagram giữ ranh giới gói |
| API tiêu biểu | `connect`, `finishConnect`, `read`, `write` | `bind`, `accept` | `bind`, `send`, `receive`, `connect`, `read`, `write` |
| Non-blocking | `connect` có thể pending; `read/write` có thể trả 0 | `accept` có thể trả `null` | `receive` có thể trả `null`; `send` có thể trả 0 |
| Khi server chấp nhận client TCP | Là kênh vừa được trả về bởi `accept()` | Vẫn tiếp tục nghe các client khác | Không có ý niệm TCP `accept()` |

Trong Java 17, các API `SocketChannel` và `ServerSocketChannel` còn có khả năng cho **Unix-domain socket** nếu dùng đúng protocol family/address và nền tảng hỗ trợ. Bài này tập trung Internet socket **TCP/UDP** trên loopback, tránh nhầm lẫn rằng mọi implementation chỉ có một protocol family.

## 2. Mô hình TCP: một listener, nhiều kênh kết nối

Một TCP server có thể được hình dung như sau:

~~~text
Client A: SocketChannel --TCP--> SocketChannel A
                                      ^
                                      |
Client B: SocketChannel --TCP--> SocketChannel B
                                      ^
                                      |
                            ServerSocketChannel
                          (listen trên IP + port)
                          accept() trả channel mới
~~~

`ServerSocketChannel` **chỉ là listener**. Mỗi lần `accept()` thành công, server nhận một **`SocketChannel` riêng** cho kết nối đó. Để đọc/ghi dữ liệu của client A, server gọi `read/write` trên channel A, không trên `ServerSocketChannel`.

### 2.1. Các bước phía server

1. Mở `ServerSocketChannel.open()`.
2. Gọi `bind(localAddress)` để lắng nghe một địa chỉ và cổng.
3. Gọi `accept()` để nhận một kết nối.
4. Với `SocketChannel` được chấp nhận, dùng `read`/`write` với `ByteBuffer`.
5. Đóng channel client khi xong, và đóng listener khi dừng server.

Mã minh họa (trong một phương thức đã xử lý `IOException`):

~~~java
try (ServerSocketChannel server = ServerSocketChannel.open()) {
    server.bind(new InetSocketAddress("127.0.0.1", 8080));
    try (SocketChannel peer = server.accept()) {
        // peer là kết nối riêng của client vừa được nhận.
        ByteBuffer received = ByteBuffer.allocate(1024);
        int count = peer.read(received);
        // Cần kiểm tra count và xử lý framing dữ liệu.
    }
}
~~~

Code trên có thể **block vô thời hạn** tại `accept()` hoặc `read()` nếu không có client hoặc dữ liệu phù hợp. Đây là đoạn mô tả cách dùng, không phải demo độc lập ở mục 6.

### 2.2. Các bước phía client

~~~java
try (SocketChannel client = SocketChannel.open()) {
    client.connect(new InetSocketAddress("127.0.0.1", 8080));
    ByteBuffer bytes = StandardCharsets.UTF_8.encode("hello");
    while (bytes.hasRemaining()) {
        client.write(bytes);
    }
}
~~~

Đây là đoạn minh họa, cần listener TCP tương ứng, import và xử lý ngoại lệ. Mặc định channel **blocking**, nên `connect` chờ kết nối được thiết lập hoặc báo lỗi. Chỉ gọi `read/write` sau khi đã kết nối; nếu chưa kết nối, có thể nhận `NotYetConnectedException`.

## 3. TCP là byte stream, không phải các gói bản tin Java

Một trong những lỗi phổ biến nhất là coi một `write` tương ứng chính xác một `read` ở bên kia. TCP chỉ cam kết luồng byte đúng thứ tự khi kết nối hoạt động bình thường; nó **không giữ ranh giới các lần ghi của ứng dụng**.

Ví dụ client gọi:

~~~text
write("HELLO")
write("WORLD")
~~~

Server có thể đọc dữ liệu theo các lần như:

~~~text
read -> "HEL"
read -> "LOWOR"
read -> "LD"
~~~

Hoặc nhiều cách phân chia khác, miễn thứ tự byte không bị đảo. Ngay cả một lần `read(ByteBuffer)` ở blocking mode cũng không phải cam kết lấp đầy toàn bộ buffer.

### 3.1. Đọc với ByteBuffer

~~~java
ByteBuffer buffer = ByteBuffer.allocate(1024);
int n = channel.read(buffer);

if (n == -1) {
    // EOF: peer đã kết thúc phía gửi/đọc hết luồng.
} else if (n > 0) {
    buffer.flip(); // chuẩn bị đọc đúng các byte mới nhận
    // Parse dữ liệu trong [position, limit).
    buffer.clear(); // chuẩn bị buffer cho lượt nhận mới
}
~~~

Mã minh họa với `SocketChannel channel` đã kết nối. `read` trả về:

- **`n > 0`:** đã đọc n byte và `position` của buffer tăng n.
- **`n == 0`:** có thể xảy ra, đặc biệt trong non-blocking mode khi chưa có byte sẵn để nhận; không được coi là EOF.
- **`n == -1`:** phía vào của stream đã tới end-of-stream (ví dụ peer đã đóng/`shutdownOutput`).

Trong code parsing thật, **không gọi `clear()` làm mất các byte thông điệp chưa hoàn chỉnh**. Nếu frame mới chứa một phần dữ liệu, dùng `compact()` để giữ byte dư rồi đọc tiếp. `clear()` chỉ đặt lại trạng thái buffer, không xóa vật lý nội dung.

### 3.2. Ghi phải kiểm tra remaining

~~~java
ByteBuffer data = StandardCharsets.UTF_8.encode("hello");
while (data.hasRemaining()) {
    channel.write(data);
}
~~~

`write` trả về số byte thực sự nhận từ buffer; có thể ít hơn `remaining` ban đầu. Đặc biệt non-blocking `write` có thể trả **0** nếu socket output buffer chưa sẵn sàng. Không lập vòng lặp bận (busy spin) khi non-blocking trả 0: nên chờ `OP_WRITE` qua `Selector` hoặc tổ chức cơ chế retry có kiểm soát.

Trong blocking mode, vòng lặp trên giúp thể hiện yêu cầu “gửi hết dữ liệu trong buffer”, nhưng **không bảo đảm peer đã đọc/xử lý dữ liệu**, cũng không tự thêm ranh giới message.

### 3.3. Protocol framing

Vì TCP không có message boundary, ứng dụng phải chọn **protocol framing**, ví dụ:

- Mỗi message kết thúc bằng newline (phải xác định charset và xử lý newline trong payload).
- Prefix độ dài 4 byte rồi đến đúng số byte body, với endian và giới hạn kích thước được quy định.
- Fixed-length message (chỉ phù hợp khi schema xác định độ dài).
- Giao thức có framing sẵn như HTTP/2 hoặc WebSocket (xử lý theo đúng đặc tả của chúng).

Ví dụ, một parser cho 4-byte length prefix không thể mặc định một `read` sẽ đọc đủ cả 4 byte. Cần lưu trạng thái giữa các lượt đọc, giới hạn độ dài để tránh cấp phát quá lớn và phân biệt EOF giữa frame.

## 4. ServerSocketChannel: bind, accept và nhiều client

`ServerSocketChannel.open()` tạo listener chưa bind. Gọi `bind`:

~~~java
ServerSocketChannel server = ServerSocketChannel.open();
server.bind(new InetSocketAddress("127.0.0.1", 0));
InetSocketAddress address =
        (InetSocketAddress) server.getLocalAddress();
~~~

Đây là mã minh họa, chưa đóng `server`; code production phải sở hữu và đóng tài nguyên. Port **0** yêu cầu OS cấp một ephemeral port còn trống, giúp test tránh cố định port 8080/9000. Địa chỉ `127.0.0.1` chỉ nhận kết nối trong cùng máy; địa chỉ wildcard có thể chấp nhận kết nối trên nhiều giao diện và cần cân nhắc an toàn mạng.

`accept()` có hai tình huống:

- **Blocking mode (mặc định):** thường chờ tới khi có client và trả về `SocketChannel` mới, trừ khi có lỗi/đóng/interrupt.
- **Non-blocking mode:** nếu chưa có kết nối chờ chấp nhận thì trả `null`; khi có kết nối thì trả channel.

`ServerSocketChannel` bản thân không thực hiện `read`/`write` payload TCP. Các socket đã `accept()` tồn tại riêng biệt. Đóng listener không đồng nghĩa một chương trình đã đóng mọi accepted channel; server phải quản lý vòng đời từng kết nối.

### Kết nối non-blocking của SocketChannel

`SocketChannel` có thể được mở non-blocking:

~~~java
SocketChannel client = SocketChannel.open();
client.configureBlocking(false);
boolean connected = client.connect(remote);

if (!connected) {
    // Kết nối đang pending; đợi OP_CONNECT bằng Selector.
    // Khi có readiness, gọi client.finishConnect().
}
~~~

Đây là mã minh họa: `remote` phải là `SocketAddress` phù hợp, và channel cần được đóng khi xong. `connect()` có thể trả ngay **`true`** nếu kết nối hoàn tất tức thì; nếu trả **`false`**, không được đọc/ghi như channel đã kết nối. Khi có tín hiệu connectable (hoặc vào thời điểm thích hợp), `finishConnect()` hoàn tất kết nối; trong non-blocking mode nó cũng có thể trả `false` khi còn pending. Một kết nối thất bại sẽ báo `IOException` thích hợp.

`SocketChannel.isConnectionPending()` và `isConnected()` giúp quan sát trạng thái hiện tại, nhưng không thay `finishConnect()` khi kết nối còn dở dang.

## 5. DatagramChannel: gửi/nhận UDP và ranh giới gói

Khác TCP, UDP có **datagram boundary**. Một lần gửi là một datagram và một lần nhận trả về **một datagram**. Không có đảm bảo tin cậy: gói có thể mất, trùng, đến sai thứ tự hoặc quá trễ. Ứng dụng cần tự xử lý nếu đòi acknowledgement, retry, sequencing hoặc chống trùng.

### 5.1. UDP server và client không cần accept

~~~text
DatagramChannel sender                       DatagramChannel receiver
    send(buffer, remote) --- UDP datagram ---> receive(buffer)
                                                |
                                                +-- trả SocketAddress nguồn
~~~

`DatagramChannel` có thể dùng `send(ByteBuffer, SocketAddress)` và `receive(ByteBuffer)` khi **chưa connected**. Thông thường phía nhận gọi `bind(localAddress)` để nghe cổng ổn định; phía gửi có thể được OS tự bind khi lần đầu gửi.

~~~java
try (DatagramChannel udp = DatagramChannel.open()) {
    udp.bind(new InetSocketAddress("127.0.0.1", 9999));
    ByteBuffer packet = ByteBuffer.allocate(2048);
    SocketAddress from = udp.receive(packet);
    packet.flip();
    // Xử lý đúng một datagram và địa chỉ người gửi 'from'.
}
~~~

Đây là **đoạn mã minh họa**: cần gửi UDP tới port 9999 thì `receive()` mới trả trong blocking mode. Không giả định gói sẽ có độ dài tối đa 2048; nếu datagram đến **lớn hơn `remaining()` của ByteBuffer**, phần dư bị **loại bỏ âm thầm** theo Javadoc. Vì thế phải chọn kích thước nhận thích hợp với giao thức và giới hạn MTU/payload.

### 5.2. Return values cần phân biệt

- `receive(dst)` trả **`SocketAddress`** nguồn khi có datagram; trong non-blocking mode trả **`null`** khi chưa có gói.
- `send(src, target)` trả **số byte** đã gửi. Trong non-blocking mode, có thể trả **0** khi output buffer chưa có chỗ cho datagram.
- Một lần `send` thành công không có nghĩa peer đã nhận, xử lý hay gửi ACK.
- Dùng `dst.flip()` sau receive trước khi giải mã; `receive` đẩy `position` tương ứng số byte đã chép.

**Không chuyển logic đọc stream TCP với vòng lặp ghép các byte thành một frame rồi áp dụng máy móc cho UDP:** UDP đã có ranh giới datagram, một gói quá lớn bị cắt trong buffer nhận là mất dữ liệu gói, không phải “phần còn lại sẽ đến ở `receive()` kế tiếp”.

### 5.3. “Connect” của DatagramChannel khác TCP

`DatagramChannel.connect(peerAddress)` **không tạo phiên TCP và không bắt tay tin cậy**. Nó liên kết channel UDP với một peer mặc định: chỉ nhận datagram từ peer đó, gửi tới peer đó và cho phép dùng `read`/`write` (vốn không có tham số địa chỉ). Channel UDP không connected vẫn dùng `send/receive` được.

Sau `disconnect()`, channel có thể gửi/nhận tới từ các địa chỉ phù hợp qua `send/receive`. Với UDP, một lần `write` là một datagram theo semantics của channel, không phải byte stream ghép với nhiều lần `write` khác.

## 6. Ví dụ Java 17+ hoàn chỉnh: TCP echo mini + UDP loopback

Demo chạy trong **một process**, dùng port do OS cấp nên không cần khởi động server riêng. Luồng TCP tạo listener, client kết nối, server `accept` rồi trao đổi `ping/pong` (mỗi message có đúng bốn byte trong ví dụ). Luồng UDP gửi một datagram `hi` từ sender tới receiver trên loopback.

**Tên tệp: `NetworkChannelsDemo.java`**

~~~java
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class NetworkChannelsDemo {
    private static void sendAll(SocketChannel channel, String value)
            throws IOException {
        ByteBuffer data = StandardCharsets.UTF_8.encode(value);
        while (data.hasRemaining()) {
            channel.write(data);
        }
    }

    private static String readExactly(SocketChannel channel, int size)
            throws IOException {
        ByteBuffer data = ByteBuffer.allocate(size);
        while (data.hasRemaining()) {
            if (channel.read(data) == -1) {
                throw new EOFException("Truncated TCP message");
            }
        }
        data.flip();
        return StandardCharsets.UTF_8.decode(data).toString();
    }

    public static void main(String[] args) throws IOException {
        // 1. TCP: listener + client + accepted SocketChannel.
        try (ServerSocketChannel listener = ServerSocketChannel.open();
             SocketChannel client = SocketChannel.open()) {
            listener.bind(new InetSocketAddress("127.0.0.1", 0));
            client.connect(listener.getLocalAddress());

            try (SocketChannel accepted = listener.accept()) {
                sendAll(client, "ping");
                System.out.println(
                        "tcp server: " + readExactly(accepted, 4));

                sendAll(accepted, "pong");
                System.out.println(
                        "tcp client: " + readExactly(client, 4));
            }
        }

        // 2. UDP: mỗi send tương ứng một datagram.
        try (DatagramChannel receiver =
                     DatagramChannel.open(StandardProtocolFamily.INET);
             DatagramChannel sender =
                     DatagramChannel.open(StandardProtocolFamily.INET)) {
            receiver.bind(new InetSocketAddress("127.0.0.1", 0));
            sender.send(
                    StandardCharsets.UTF_8.encode("hi"),
                    receiver.getLocalAddress());

            ByteBuffer packet = ByteBuffer.allocate(16);
            SocketAddress source = receiver.receive(packet);
            packet.flip();

            System.out.println("udp source? " + (source != null));
            System.out.println(
                    "udp message: " + StandardCharsets.UTF_8.decode(packet));
        }
    }
}
~~~

Biên dịch và chạy tại thư mục có tệp nguồn:

~~~bash
javac NetworkChannelsDemo.java
java NetworkChannelsDemo
~~~

**Stdout khi TCP/UDP loopback trên máy hoạt động bình thường:**

~~~text
tcp server: ping
tcp client: pong
udp source? true
udp message: hi
~~~

Diễn giải:

1. `listener.bind(..., 0)` mở một port TCP tự chọn, còn `client.connect(listener.getLocalAddress())` kết nối tới listener trong cùng máy.
2. `accept()` trả `SocketChannel` đại diện cho kết nối phía server; hai đối tượng `client` và `accepted` là hai đầu của một kết nối TCP.
3. `sendAll` lặp cho đến khi `ByteBuffer` gửi hết. `readExactly` cũng lặp đến đủ **đúng bốn byte**; việc đó tránh giả định một lần `read` luôn nhận đủ message. Đây chỉ là giao thức fixed-length dùng để minh họa, không phải parser message tùy độ dài.
4. `DatagramChannel` sender gửi đúng một datagram `hi`. Receiver nhận và in địa chỉ nguồn có tồn tại, rồi giải mã hai byte.
5. `try-with-resources` đóng mọi channel, kể cả khi có exception.

**Giới hạn demo:** dùng TCP/UDP trong cùng process và loopback, tránh sự cố mạng bên ngoài. Trên môi trường khóa hoàn toàn quyền socket, TCP/UDP bind/connect có thể thất bại. Chương trình không có timeout vì mục tiêu là ví dụ ngắn, chắc chắn khi loopback hoạt động; code production cần timeout, cancellation và xử lý mạng lỗi. Hình thức TCP fixed-length bốn byte được xác định trước ở cả hai phía, không phải framing tổng quát cho payload tùy ý.

## 7. Blocking, non-blocking và Selector

Các channel trên là `SelectableChannel`. Mặc định chúng ở **blocking mode**. Gọi `configureBlocking(false)` sẽ chuyển sang non-blocking (khi không bị ràng buộc bởi đăng ký `Selector` không tương thích).

| Thao tác | Blocking | Non-blocking |
|---|---|---|
| `ServerSocketChannel.accept()` | Chờ kết nối, rồi trả `SocketChannel` | Chưa có kết nối ⇒ trả `null` |
| `SocketChannel.connect(remote)` | Chờ kết nối thành công hoặc lỗi | Có thể trả `false`; cần `finishConnect()` |
| `SocketChannel.read(dst)` | Thường chờ ít nhất một byte hoặc EOF khi buffer có chỗ | Chưa có dữ liệu ⇒ có thể trả 0 |
| `SocketChannel.write(src)` | Có thể chờ output buffer; vẫn nên kiểm tra remaining | Có thể ghi một phần hoặc trả 0 |
| `DatagramChannel.receive(dst)` | Chờ datagram phù hợp | Chưa có datagram ⇒ trả `null` |
| `DatagramChannel.send(src, remote)` | Gửi datagram hoặc báo lỗi | Có thể trả 0 nếu chưa gửi được gói |

Với nhiều kết nối, một thread có thể quản lý event loop bằng `Selector`:

~~~text
Selector.select() -> selectedKeys
   OP_ACCEPT  -> listener.accept()
   OP_CONNECT -> client.finishConnect()
   OP_READ    -> socket.read(...) hoặc datagram.receive(...)
   OP_WRITE   -> socket.write(...) các byte còn tồn trong hàng đợi
~~~

Đây là sơ đồ ý tưởng. Mỗi `SelectionKey` gắn với channel đã đăng ký; code thật phải kiểm tra key valid, xử lý ngoại lệ, byte framing, backpressure và đóng channel/selector. Trên kênh non-blocking:

- Khi `accept() == null` hay `receive() == null`, không coi đó là lỗi hoặc EOF.
- Khi `SocketChannel.read() == 0`, giữ nguyên state đang đọc dở, chờ thêm dữ liệu.
- Khi `SocketChannel.write() == 0`, lưu `ByteBuffer` còn dữ liệu để ghi tiếp ở một sự kiện phù hợp.
- Nếu đăng ký `OP_WRITE` liên tục kể cả khi không có byte chờ ghi, selector có thể liên tục báo writable và gây CPU busy loop.

Cần hiểu **non-blocking I/O không tự làm TCP thành UDP**, không tự thêm message boundaries và không cam kết tác vụ mạng hoàn thành ngay.

## 8. Đóng kết nối, EOF, timeout và lỗi mạng

### 8.1. Vòng đời của channel

Các channel triển khai `AutoCloseable`, nên dùng `try-with-resources`. Với TCP:

- `SocketChannel.close()` đóng toàn bộ kết nối phía channel.
- `SocketChannel.shutdownOutput()` kết thúc phía ghi nhưng có thể tiếp tục đọc. Peer thường nhận EOF sau khi byte trước đó đã được xử lý theo TCP.
- `SocketChannel.shutdownInput()` kết thúc phía đọc; các lần đọc sau trả `-1` theo hợp đồng.
- `SocketChannel.read(...) == -1` nói tới **EOF của luồng TCP**, không phải một message rỗng.
- Listener và những accepted socket có vòng đời riêng: đóng listener **không thay thế** việc đóng các channel client còn được quản lý.

`DatagramChannel` không có “EOF do peer đóng” như một byte stream TCP. Một thời gian dài không nhận UDP **không chứng minh** peer đã đóng; datagram có thể bị mất, chặn hoặc chưa được gửi.

### 8.2. InterruptibleChannel

`SocketChannel`, `ServerSocketChannel` và `DatagramChannel` đều triển khai `InterruptibleChannel`. Với thread đang bị chặn trong I/O:

- Thread khác gọi `channel.close()` có thể làm blocked operation ném `AsynchronousCloseException`.
- Thread khác gọi `thread.interrupt()` làm channel bị đóng và blocked operation ném `ClosedByInterruptException`, với interrupt flag của thread được đặt.

Nếu channel đã đóng trước khi thao tác I/O bắt đầu, có thể gặp `ClosedChannelException`. Các ngoại lệ `NotYetConnectedException`, `AlreadyConnectedException`, `ConnectionPendingException`, `BindException`, `ConnectException` và những `IOException` khác mang ý nghĩa trạng thái hoặc hỏng kết nối khác nhau, cần xử lý theo tình huống. Không được bắt ngoại lệ interrupt rồi tái sử dụng channel vốn đã đóng.

### 8.3. Timeout và backpressure

Code production phải có chính sách:

- Giới hạn thời gian chờ kết nối/nhận dữ liệu thích hợp, ví dụ non-blocking kết hợp `Selector.select(timeout)`, scheduling deadline hoặc API timeout tương ứng; đừng giả định `configureBlocking(false)` tự cung cấp timeout.
- Với TCP, nếu producer gửi nhanh hơn consumer, phải giữ hàng đợi các `ByteBuffer` còn lại và có giới hạn bộ nhớ/backpressure.
- Với UDP, đặt kích thước datagram hợp lý, xử lý loss/reorder/duplicates ở tầng giao thức nếu cần; gửi nhiều byte không đồng nghĩa chúng tự chia thành các datagram an toàn theo message schema.
- Tránh để nhiều thread cùng sửa một `ByteBuffer` không được đồng bộ. SocketChannel cho phép một read và một write đồng thời nhưng state buffer mà application chia sẻ vẫn do ứng dụng bảo vệ.
- Kiểm soát số kết nối, file descriptor/socket handle và đóng mọi accepted channel khi shutdown.

## 9. Lựa chọn TCP hay UDP theo yêu cầu dữ liệu

**Chọn TCP/SocketChannel + ServerSocketChannel** khi ứng dụng cần truyền **byte stream có thứ tự và kiểm soát độ tin cậy ở tầng transport**: request/response dạng stream, truyền file, giao thức có session. Nhưng cần tự thiết kế framing, timeout, phân luồng và bảo vệ trước peer gửi chậm.

**Chọn UDP/DatagramChannel** khi ứng dụng muốn thao tác trên **từng datagram**, chấp nhận khả năng mất/trùng/sai thứ tự hoặc có cơ chế khắc phục riêng: telemetry, khám phá dịch vụ, dữ liệu thời gian thực tùy protocol. Cần xem MTU, giới hạn payload, chống giả mạo nguồn và rủi ro mạng.

Một số giao thức hiện đại xây dựng độ tin cậy trên UDP bằng logic riêng; vì vậy việc dùng UDP không đồng nghĩa mọi ứng dụng trên nó bắt buộc chấp nhận mất dữ liệu. Khác biệt trọng tâm là **những bảo đảm nào được tầng transport cung cấp sẵn**, những gì phải xử lý ở tầng giao thức ứng dụng.

## 10. Những lỗi thường gặp

1. **Gọi `read/write` trên `ServerSocketChannel` như một kết nối TCP.** Listener chỉ `accept`; đọc/ghi trên accepted `SocketChannel`.
2. **Nghĩ mỗi TCP `write` tương ứng một `read`.** TCP là stream; phải có framing và vòng lặp buffer.
3. **Coi `SocketChannel.read()==0` là EOF.** EOF là `-1`; `0` không có byte được đọc.
4. **Không kiểm tra `hasRemaining()` sau `write`.** Dữ liệu có thể còn trong buffer, nhất là non-blocking.
5. **Dùng vòng lặp busy spin khi non-blocking `write()==0`.** Cần quản lý readiness và backpressure.
6. **Không gọi `finishConnect()` sau non-blocking `connect()==false`.** Kết nối còn pending; không được đọc/ghi như đã connected.
7. **Gọi `accept()` trong non-blocking và mặc định không thể là `null`.** Không có client chờ thì `null` là bình thường.
8. **Nhầm UDP `connect` với TCP handshake.** UDP connect đặt peer mặc định/lọc nguồn; không cung cấp stream hay delivery guarantee.
9. **Buffer UDP quá nhỏ rồi mong nhận phần dư lần sau.** Phần dư của datagram bị cắt **và bỏ**.
10. **Cho rằng UDP `send` thành công là peer đã nhận.** Nó không xác nhận delivery hay xử lý.
11. **Quên `flip` hoặc `compact` khi đọc ByteBuffer.** Parser có thể đọc 0 byte, đọc byte cũ hoặc mất frame dở dang.
12. **Chỉ đóng ServerSocketChannel mà để quên accepted sockets.** Channel listener và client có vòng đời riêng.
13. **Mặc định mọi môi trường đều cho bind port cố định.** Port có thể bận hoặc bị policy chặn; test loopback nên dùng port 0.
14. **Cho rằng non-blocking giải quyết mọi timeout và error handling.** Ứng dụng vẫn cần deadline, queue, xử lý EOF/error và shutdown.

## 11. Câu hỏi tự kiểm tra

**Câu 1.** Vì sao TCP client gọi hai lần `write("A")` và `write("B")` nhưng server chỉ có một lần `read()` nhận `"AB"`?

**Đáp án:** TCP cung cấp byte stream có thứ tự, không giữ ranh giới từng lần `write` của ứng dụng. Cần framing nếu muốn tách message.

**Câu 2.** Với `ServerSocketChannel` ở non-blocking mode, `accept()` trả gì khi chưa có client?

**Đáp án:** `null`. Đây không phải kết nối lỗi; ứng dụng tiếp tục event loop đợi `OP_ACCEPT`.

**Câu 3.** `SocketChannel.connect(remote)` trả `false` trong non-blocking mode. Bước tiếp theo?

**Đáp án:** Kết nối còn pending; chờ connectable và gọi `finishConnect()` đến khi kết nối hoàn tất hoặc ném lỗi. Không đọc/ghi khi chưa connected.

**Câu 4.** Datagram 120 byte tới `DatagramChannel.receive(dst)` khi `dst.remaining()==32`. Có lấy tiếp 88 byte qua `receive` kế tiếp không?

**Đáp án:** Không. Dữ liệu vượt phần buffer nhận của datagram đó bị loại bỏ theo hợp đồng; lần `receive` tiếp theo dành cho datagram khác.

**Câu 5.** Vì sao kết quả `udp.send` không đủ để khẳng định server nhận thành công?

**Đáp án:** UDP không có ACK/delivery guarantee ở tầng transport; `send` chỉ cho biết hành động gửi ở phía local. Nếu cần xác nhận, giao thức ứng dụng phải bổ sung.

### Tài liệu chính thức Java 17

- [SocketChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/SocketChannel.html)
- [ServerSocketChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/ServerSocketChannel.html)
- [DatagramChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/DatagramChannel.html)
- [SelectableChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/SelectableChannel.html)
- [Selector](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/Selector.html)
- [ByteBuffer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nio-networking-002">Chế độ blocking và non-blocking</a>

<details>
<summary>Xem chi tiết</summary>

# Chế độ blocking và non-blocking trong Java NIO mạng

## 1. Blocking và non-blocking trả lời câu hỏi gì?

Trong lập trình mạng, thao tác như chờ kết nối, chấp nhận kết nối, nhận byte hay gửi byte có thể cần thời gian do phụ thuộc vào hệ điều hành, trạng thái socket, peer và tốc độ mạng.

**Blocking** và **non-blocking** mô tả cách một lời gọi I/O ứng xử khi điều kiện để thực hiện thao tác chưa sẵn sàng:

- **Blocking I/O**: lời gọi có thể giữ luồng đang gọi ở trạng thái chờ cho đến khi thao tác có thể tiến triển, kết thúc, gặp EOF hoặc lỗi. Đây **không có nghĩa** lúc nào cũng chờ, hoặc một lời gọi luôn truyền đủ mọi byte.
- **Non-blocking I/O**: lời gọi không chờ vô hạn chỉ để đợi điều kiện I/O trở nên sẵn sàng; nó có thể trả về ngay mà **chưa chuyển byte nào**. Ứng dụng phải quyết định khi nào thử lại, thường phối hợp `Selector` để nhận thông báo mức độ sẵn sàng (*readiness*).

Ví dụ server gọi `ServerSocketChannel.accept()` khi chưa có client:

| Chế độ của server channel | Điều có thể xảy ra |
| --- | --- |
| Blocking (mặc định) | `accept()` chờ tới khi có kết nối hoặc một tình huống kết thúc/lỗi |
| Non-blocking | `accept()` trả về ngay **`null`** nếu hiện chưa có kết nối chờ được chấp nhận |

Khác với `accept()`, một số lệnh `read()` non-blocking dùng **`0`** để báo chưa nhận byte. Không được đánh đồng `null`, `0` và `-1` — mỗi API có hợp đồng riêng.

Java NIO cung cấp cả hai mô hình để phù hợp với số lượng kết nối, mô hình thực thi, khả năng mở rộng và tính đơn giản của ứng dụng.

## 2. Các lớp liên quan trong NIO mạng

| Kiểu Java | Vai trò | Liên hệ blocking/non-blocking |
| --- | --- | --- |
| `ServerSocketChannel` | Lắng nghe và chấp nhận kết nối TCP | `accept()` blocking hoặc non-blocking |
| `SocketChannel` | Kết nối TCP; đọc/ghi qua `ByteBuffer` | `connect()`, `finishConnect()`, `read()`, `write()` |
| `DatagramChannel` | Gửi/nhận datagram, thường dùng UDP | `receive()` có thể trả `null` nếu non-blocking chưa có datagram |
| `SelectableChannel` | Interface chung cho các channel có thể đăng ký selector | `configureBlocking(boolean)` và `isBlocking()` |
| `Selector` | Theo dõi các channel đã đăng ký và các sự kiện readiness | `select()`, `select(timeout)`, `selectNow()` |
| `SelectionKey` | Đại diện một lần đăng ký channel với selector | `OP_ACCEPT`, `OP_CONNECT`, `OP_READ`, `OP_WRITE` |

**`FileChannel` không phải `SelectableChannel`** và không thể chuyển thành non-blocking kiểu socket bằng `configureBlocking(false)` hoặc đăng ký vào `Selector`. Đây là điểm khác biệt giữa **Java NIO dùng buffer/channel** nói chung với **I/O mạng không chặn dùng selector**.

Một channel mới thuộc nhóm `SelectableChannel` được tạo ở **blocking mode** theo hợp đồng API, trừ khi mã của bạn chủ động chuyển sang non-blocking.

## 3. Chuyển chế độ qua `configureBlocking`

`SelectableChannel` có:

```java
SelectableChannel configureBlocking(boolean block) throws IOException;
boolean isBlocking();
```

- `configureBlocking(true)`: yêu cầu chế độ blocking.
- `configureBlocking(false)`: yêu cầu chế độ non-blocking.
- `isBlocking()`: kiểm tra chế độ hiện tại (đối với channel đang mở).

Ví dụ:

```java
ServerSocketChannel server = ServerSocketChannel.open();
System.out.println(server.isBlocking()); // true
server.configureBlocking(false);
System.out.println(server.isBlocking()); // false
```

Đây là đoạn mã minh họa và cần quản lý đóng channel. `configureBlocking` là **cấu hình của đối tượng channel**, không phải thay đổi toàn bộ JVM hay tất cả socket cùng một lúc.

Có ràng buộc quan trọng: **một channel được đăng ký với bất kỳ selector nào không thể chuyển về blocking mode ngay khi nó còn ở trạng thái đăng ký**. Cố `configureBlocking(true)` trong tình huống đó ném `IllegalBlockingModeException`. Khi `SelectionKey.cancel()`, việc hủy đăng ký có thể chỉ được xử lý trong chu kỳ lựa chọn tiếp theo; không nên hủy key rồi lập tức giả định có thể chuyển sang blocking mà không đồng bộ vòng đời.

Ngoài ra, **muốn đăng ký `Selector`, channel phải đang ở non-blocking mode**. Đăng ký khi còn blocking sẽ ném `IllegalBlockingModeException`. Không có nghĩa mọi non-blocking channel bắt buộc phải đăng ký selector: có thể thăm dò bằng lời gọi trực tiếp, nhưng thăm dò lặp vô điều kiện dễ tạo busy loop.

## 4. Hành vi của `accept`, `connect`, `read` và `write`

### 4.1. `ServerSocketChannel.accept()`

- Blocking: có thể chờ một kết nối mới.
- Non-blocking: trả về `SocketChannel` khi có kết nối chờ; nếu chưa có, trả về **`null`**.

Hệ quả: luôn kiểm tra kết quả `accept()` trước khi gọi phương thức của accepted channel.

### 4.2. `SocketChannel.connect(remote)`

- Blocking: thường chỉ trả về sau khi thiết lập kết nối hoàn tất hoặc phát sinh lỗi; `connect` trả `true` nếu đã kết nối thành công.
- Non-blocking: có thể trả về **`false`** vì quá trình kết nối chưa hoàn tất. Khi channel có tín hiệu `OP_CONNECT`, gọi `finishConnect()` để hoàn thành bắt tay; nếu `finishConnect()` vẫn trả false, tiếp tục chờ phù hợp.
- `OP_CONNECT` báo trạng thái sẵn sàng hoàn tất, **không bảo đảm** việc kết nối sẽ thành công: lỗi kết nối có thể xuất hiện qua `finishConnect()`.

Không đọc/ghi ứng dụng như thể socket chắc chắn đã kết nối chỉ vì `connect()` non-blocking trả false. Phải xử lý trạng thái kết nối đang chờ.

### 4.3. `SocketChannel.read(dst)`

Giả sử `dst` có chỗ trống, không chỉ đọc, và socket đã kết nối:

| Kết quả | Ý nghĩa |
| --- | --- |
| `n > 0` | Đã nhận `n` byte; `dst.position()` tăng `n` |
| `n == 0` | Chưa nhận byte nào trong lời gọi hiện tại; thường gặp với non-blocking khi chưa sẵn sàng |
| `n == -1` | Đầu bên kia đã kết thúc chiều gửi hoặc tới EOF |

**`0` không phải EOF**. Cũng không được giả định `read` luôn nhận đủ `dst.remaining()`. TCP là **byte stream** chứ không bảo toàn ranh giới thông điệp: một gói logic có thể đến trong nhiều lần `read`, và một lần `read` có thể chứa dữ liệu của nhiều thông điệp ứng dụng.

Nếu buffer đã đầy (`dst.remaining() == 0`), trước hết phải xử lý dữ liệu rồi `clear()` hoặc `compact()` theo chu kỳ hiện tại, thay vì diễn giải việc không nhận thêm dữ liệu thành “đầu bên kia đã đóng”.

### 4.4. `SocketChannel.write(src)`

- Trả về số byte thực tế đã gửi từ vùng `[src.position(), src.limit())`.
- Có thể chỉ ghi **một phần** dữ liệu, ngay cả khi blocking mode; mã đúng vẫn phải xử lý lượng còn lại theo hợp đồng.
- Ở non-blocking mode, có thể trả `0` nếu hiện chưa thể ghi thêm.
- `write` không dùng `-1` để báo EOF; tình trạng kết nối bị đóng/lỗi sẽ cần xử lý qua ngoại lệ và trạng thái channel.

Một lời gọi `write()` thành công có nghĩa dữ liệu đã được chấp nhận vào đường truyền theo hợp đồng I/O, **không bảo đảm ứng dụng phía peer đã đọc hoặc xử lý dữ liệu**.

## 5. Blocking không đồng nghĩa đồng bộ hóa toàn bộ chương trình

Hình dung một luồng phục vụ một client bằng các bước:

```text
accept() --chờ kết nối--> read() --chờ byte--> xử lý
                                      |
                                      v
                                  write()
```

Nếu các lời gọi I/O blocking, **luồng thực hiện lời gọi** có thể chờ. Các luồng khác trong cùng JVM vẫn có thể chạy. Vì vậy “blocking I/O” không đồng nghĩa “toàn bộ ứng dụng bị đứng”, và “non-blocking I/O” cũng không tự đồng nghĩa “xử lý song song”.

Blocking thường phù hợp với:
- Ứng dụng đơn giản, ít kết nối đồng thời.
- Luồng riêng cho từng kết nối khi tài nguyên và chiến lược thực thi cho phép.
- Mã nghiệp vụ cần luồng điều khiển tuyến tính, dễ đọc và dễ gỡ lỗi.

Những đánh đổi thường gặp:
- Nếu số kết nối lớn và nhiều client không gửi dữ liệu trong thời gian dài, thiết kế một thread nền tảng cho mỗi client có thể tốn tài nguyên.
- Cần kiểm soát timeout, hủy tác vụ, backpressure và đóng socket.
- Một thao tác có thể chờ lâu nếu không có tiến độ và không thiết kế cách kết thúc.

**Non-blocking** giúp một số lượng nhỏ luồng điều phối nhiều kết nối, nhưng chuyển sự phức tạp sang quản lý trạng thái của từng kết nối, selector, buffer và các tình huống read/write một phần. Không mặc định chuyển sang non-blocking là tăng tốc tất cả trường hợp.

## 6. Ví dụ Java 17+ chạy độc lập: TCP blocking

Ví dụ này chạy một server và một client trên loopback trong cùng JVM. Server dùng `ServerSocketChannel.accept()` ở blocking mode, client gửi đúng bốn byte ASCII `PING`; server đọc đủ bốn byte rồi kết thúc. Để không phụ thuộc thứ tự log giữa hai luồng, **chỉ luồng chính in kết quả sau khi tác vụ server hoàn tất**.

**Tệp `BlockingTcpDemo.java`:**

```java
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class BlockingTcpDemo {
    public static void main(String[] args) throws Exception {
        try (ServerSocketChannel server = ServerSocketChannel.open()) {
            server.bind(new InetSocketAddress(
                    InetAddress.getLoopbackAddress(), 0));

            ExecutorService pool = Executors.newSingleThreadExecutor();
            try {
                Future<String> response = pool.submit(() -> {
                    try (SocketChannel accepted = server.accept()) {
                        ByteBuffer bytes = ByteBuffer.allocate(4);
                        while (bytes.hasRemaining()) {
                            int n = accepted.read(bytes);
                            if (n == -1) {
                                throw new EOFException("Client closed early");
                            }
                        }

                        bytes.flip();
                        return StandardCharsets.US_ASCII
                                .decode(bytes).toString();
                    }
                });

                try (SocketChannel client = SocketChannel.open()) {
                    client.connect(server.getLocalAddress());
                    ByteBuffer outgoing = ByteBuffer.wrap(
                            "PING".getBytes(StandardCharsets.US_ASCII));

                    while (outgoing.hasRemaining()) {
                        client.write(outgoing);
                    }
                }

                String received = response.get(5, TimeUnit.SECONDS);
                System.out.println("Blocking server: "
                        + server.isBlocking());
                System.out.println("Noi dung nhan: " + received);
            } finally {
                server.close();
                pool.shutdownNow();
            }
        }
    }
}
```

Biên dịch và chạy bằng JDK 17+:

```bash
javac BlockingTcpDemo.java
java BlockingTcpDemo
```

**Stdout khi kết nối loopback và I/O thành công:**

```text
Blocking server: true
Noi dung nhan: PING
```

**Giải thích:**

1. `bind(..., 0)` để hệ điều hành cấp một cổng TCP trống; không cần cố định số cổng và không in số cổng vì nó thay đổi theo từng lần chạy.
2. Server giữ chế độ blocking mặc định. Tác vụ trong pool chờ ở `accept()` cho đến khi client kết nối.
3. Client dùng `SocketChannel.open()` và `connect(...)` ở blocking mode mặc định, sau đó ghi đủ bốn byte với vòng lặp `hasRemaining()`.
4. Server tiếp tục gọi `read(bytes)` cho đến khi nhận đủ bốn byte hoặc gặp EOF. Không giả định một `read` đủ bốn byte.
5. `Future.get` giúp luồng chính chỉ in kết quả sau khi server đọc xong. Timeout 5 giây là giới hạn chờ của `get`, không phải timeout tự động cho từng thao tác socket; demo được thiết kế cho môi trường loopback hoạt động bình thường.

Nếu muốn xây server thực tế, cần cơ chế dừng, timeout, giới hạn kết nối, quản lý luồng và bảo vệ tài nguyên. Ví dụ chỉ chứng minh hành vi blocking và cách đọc TCP đầy đủ một thông điệp có độ dài đã biết.

## 7. Non-blocking: thử I/O khi có thể tiến triển

Đối với `ServerSocketChannel` hay `SocketChannel`:

```java
channel.configureBlocking(false);
```

Khi chuyển sang non-blocking, mã phải chuẩn bị xử lý:
- `accept() == null` nếu chưa có kết nối chờ.
- `connect() == false` nếu đang kết nối.
- `read() == 0` khi chưa nhận được byte.
- `write() == 0` khi chưa chuyển được byte.
- `read() == -1` khi gặp EOF (khác hoàn toàn với 0).

Một cách sai thường gặp:

```java
while (channel.read(buffer) == 0) {
    // Không làm gì.
}
```

Vòng lặp này có thể liên tục chiếm CPU khi chưa có dữ liệu. Cũng không có cơ sở bảo đảm một lần đọc sau đó sẽ nhận đủ cả thông điệp.

**Thực hành tốt:** Đăng ký channel non-blocking với `Selector` và xử lý khi có sự kiện `OP_ACCEPT`/`OP_CONNECT`/`OP_READ`/`OP_WRITE` phù hợp. Khi một lệnh non-blocking không tiến triển, hãy trả quyền điều khiển cho event loop, giữ trạng thái chưa hoàn tất và đợi thông báo tiếp theo.

## 8. `Selector`: chờ readiness cho nhiều channel

`Selector` là cơ chế multiplexing các channel selectable:

```text
Socket A (non-blocking) ---\
Socket B (non-blocking) ----> Selector ---> một event loop
ServerSocketChannel -------/
                            |
                            +-- accept khi sẵn sàng
                            +-- finishConnect khi kết nối sẵn sàng
                            +-- read khi sẵn sàng đọc
                            +-- write khi sẵn sàng ghi
```

Quy trình tổng quát:

1. Mở `Selector`.
2. Chuyển mỗi channel cần theo dõi sang `configureBlocking(false)`.
3. Đăng ký qua `channel.register(selector, interestOps)` để nhận `SelectionKey`.
4. Gọi `selector.select()` hoặc `select(timeout)` để chờ ít nhất một channel phù hợp có readiness, hoặc trở về theo timeout/wakeup.
5. Duyệt `selector.selectedKeys()`, xử lý các key còn hợp lệ, và **xóa key khỏi tập selectedKeys sau khi xử lý**.
6. Thay đổi `interestOps` theo trạng thái thực, tiếp tục vòng event loop đến khi ứng dụng có điều kiện dừng.

**Readiness không có nghĩa chắc chắn chuyển được một lượng byte định trước.** Giữa sự kiện selector và thao tác I/O, điều kiện có thể thay đổi. Non-blocking `read`/`write` vẫn có thể trả 0 và buộc ứng dụng giữ trạng thái để chờ lần sau.

Các bit sự kiện thông dụng:

| Bit | Thường dùng với | Khi nào quan tâm |
| --- | --- | --- |
| `SelectionKey.OP_ACCEPT` | `ServerSocketChannel` | Có kết nối chờ được chấp nhận |
| `SelectionKey.OP_CONNECT` | `SocketChannel` đang connect non-blocking | Có thể gọi `finishConnect()` |
| `SelectionKey.OP_READ` | `SocketChannel` đã kết nối | Có thể đọc byte, xử lý EOF/lỗi hoặc nhận dữ liệu |
| `SelectionKey.OP_WRITE` | `SocketChannel` có dữ liệu chờ gửi | Có thể thử ghi phần còn lại |

Không phải bit nào cũng hợp lệ cho mọi channel. `ServerSocketChannel.validOps()` hỗ trợ `OP_ACCEPT`; `SocketChannel` hỗ trợ các thao tác kết nối/đọc/ghi. Đăng ký bit không hợp lệ ném `IllegalArgumentException`.

**Đừng để `OP_WRITE` bật mãi chỉ vì socket đang mở.** Socket thường có thể ghi gần như liên tục khi buffer mạng còn chỗ, dẫn tới selector thức dậy lặp đi lặp lại và tiêu tốn CPU. Chỉ quan tâm `OP_WRITE` khi có dữ liệu **đã chuẩn bị nhưng chưa gửi hết**, và bỏ interest này khi không còn byte chờ gửi.

## 9. Ví dụ Java 17+ chạy độc lập: non-blocking accept/read với Selector

Chương trình sau mở server trên loopback ở chế độ non-blocking, đăng ký `OP_ACCEPT`, sau đó dùng một client **blocking** để gửi chuỗi ASCII `HELLO`. Event loop của server dùng `Selector` nhận kết nối và đọc cho đến khi đủ năm byte. Việc client được dùng blocking không làm server trở thành blocking: **chế độ được cấu hình riêng theo channel**.

Ví dụ chỉ in kết quả khi đã nhận đủ dữ liệu, nên không phụ thuộc sự kiện SELECT xảy ra một hay nhiều lần. Thời gian đợi được giới hạn để demo không chờ mãi trong môi trường mạng gặp sự cố.

**Tệp `NonBlockingSelectorDemo.java`:**

```java
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

public class NonBlockingSelectorDemo {
    public static void main(String[] args) throws IOException {
        try (Selector selector = Selector.open();
             ServerSocketChannel server = ServerSocketChannel.open()) {

            server.bind(new InetSocketAddress(
                    InetAddress.getLoopbackAddress(), 0));
            server.configureBlocking(false);
            server.register(selector, SelectionKey.OP_ACCEPT);

            try (SocketChannel client = SocketChannel.open()) {
                client.connect(server.getLocalAddress());
                ByteBuffer message = ByteBuffer.wrap(
                        "HELLO".getBytes(StandardCharsets.US_ASCII));

                while (message.hasRemaining()) {
                    client.write(message);
                }

                String received = null;
                long deadline = System.nanoTime()
                        + TimeUnit.SECONDS.toNanos(5);

                while (received == null) {
                    long remainingNanos = deadline - System.nanoTime();
                    if (remainingNanos <= 0) {
                        throw new IOException("Selector timeout");
                    }

                    long timeoutMillis = Math.max(1L,
                            TimeUnit.NANOSECONDS.toMillis(remainingNanos));
                    selector.select(timeoutMillis);

                    Iterator<SelectionKey> keys =
                            selector.selectedKeys().iterator();

                    while (keys.hasNext()) {
                        SelectionKey key = keys.next();
                        keys.remove();

                        if (!key.isValid()) {
                            continue;
                        }

                        if (key.isAcceptable()) {
                            SocketChannel peer = server.accept();
                            if (peer != null) {
                                peer.configureBlocking(false);
                                peer.register(selector, SelectionKey.OP_READ,
                                        ByteBuffer.allocate(5));
                            }
                        } else if (key.isReadable()) {
                            SocketChannel peer =
                                    (SocketChannel) key.channel();
                            ByteBuffer buffer =
                                    (ByteBuffer) key.attachment();

                            int n = peer.read(buffer);
                            if (n == -1) {
                                throw new EOFException("Peer closed early");
                            }

                            if (!buffer.hasRemaining()) {
                                buffer.flip();
                                received = StandardCharsets.US_ASCII
                                        .decode(buffer).toString();
                                key.cancel();
                                peer.close();
                            }
                        }
                    }
                }

                System.out.println("Server non-blocking: "
                        + !server.isBlocking());
                System.out.println("Noi dung nhan: " + received);
            }
        }
    }
}
```

Biên dịch, chạy:

```bash
javac NonBlockingSelectorDemo.java
java NonBlockingSelectorDemo
```

**Stdout khi loopback và I/O thành công:**

```text
Server non-blocking: true
Noi dung nhan: HELLO
```

**Cần hiểu rõ:**

1. `server.configureBlocking(false)` bắt buộc trước `server.register(selector, OP_ACCEPT)`.
2. Selector chờ sẵn sàng chấp nhận, sau đó `server.accept()` trả channel mới hoặc có thể trả null; chương trình kiểm tra null.
3. Channel mới cũng được chuyển non-blocking trước khi đăng ký `OP_READ`.
4. `read(buffer)` có thể đọc một phần. Vì buffer có capacity 5, event loop giữ buffer làm attachment và tiếp tục đến khi đủ năm byte.
5. `selector.selectedKeys()` là tập các key đã được chọn; code dùng `Iterator.remove()` để tránh xử lý lặp key cũ không đúng cách.
6. Demo không dùng `OP_WRITE` vì client gửi dữ liệu bằng blocking mode. Server production xử lý chiều gửi non-blocking cần quản lý hàng đợi buffer và bật/tắt `OP_WRITE` theo lượng dữ liệu chưa gửi.
7. Đoạn demo giả định thông điệp đúng năm byte. Ứng dụng thực cần **framing** (ví dụ tiền tố độ dài, delimiter hoặc protocol parser), giới hạn kích thước thông điệp, bộ nhớ và số kết nối để tránh đọc dở hoặc xử lý sai dữ liệu.

## 10. Vòng đời kết nối non-blocking và `OP_CONNECT`

Client non-blocking có thêm giai đoạn *kết nối chưa hoàn tất*:

```text
SocketChannel.open()
      |
      v
configureBlocking(false)
      |
      v
connect(address)
      |
      +--- true ---> connected; đăng ký OP_READ khi cần
      |
      +--- false --> đăng ký OP_CONNECT
                         |
                         v
                  selector báo connectable
                         |
                         v
                    finishConnect()
                         |
                         +--- true --> connected
                         |
                         +--- false --> tiếp tục đợi
```

Khung mã minh họa:

```java
SocketChannel socket = SocketChannel.open();
socket.configureBlocking(false);
boolean connected = socket.connect(remoteAddress);

if (connected) {
    socket.register(selector, SelectionKey.OP_READ);
} else {
    socket.register(selector, SelectionKey.OP_CONNECT);
}

// Trong event loop, chỉ khi key.isConnectable():
// if (socket.finishConnect()) {
//     key.interestOps(SelectionKey.OP_READ);
// }
```

Đoạn này **không phải chương trình độc lập**; cần khai báo `remoteAddress`, `selector`, xử lý `IOException`, sự kiện đọc/ghi và đóng tài nguyên. `finishConnect()` có thể ném ngoại lệ khi kết nối thất bại; không dùng `isConnectable()` như bảo đảm kết nối thành công. Nếu còn dữ liệu outbound đang chờ sau khi connect hoàn tất, có thể quan tâm thêm `OP_WRITE`.

## 11. Partial write và backpressure trong non-blocking

Với socket non-blocking, hàng đợi gửi ở kernel hoặc peer có thể không nhận thêm byte ngay. Một lệnh:

```java
int sent = socket.write(outgoing);
```

có thể trả từ 0 tới số byte đang còn trong `outgoing`. Chỉ phần đã gửi làm `outgoing.position()` tăng. Nếu `sent==0` hoặc vẫn còn `outgoing.hasRemaining()`, phải **giữ lại phần dữ liệu chưa gửi** để gửi tiếp khi `OP_WRITE` báo sẵn sàng.

Một mô hình quản lý:

```text
Ứng dụng tạo response -> ByteBuffer ở read mode
                           |
                           v
                      socket.write()
                           |
                    +------+------+
                    |             |
                Đã gửi hết    Còn remaining
                    |             |
                  bỏ OP_WRITE   bật OP_WRITE
                                  |
                                  v
                              sự kiện writable
                                  |
                                  v
                            thử write lần nữa
```

Không dùng vòng `while (outgoing.hasRemaining()) { socket.write(outgoing); }` không điều kiện trên event-loop non-blocking nếu `write()` có thể luôn trả 0: nó gây **busy spin**, chặn các kết nối khác. Thay vào đó, khi chưa gửi hết, giữ lại buffer và quay về selector.

**Backpressure** là cơ chế phản hồi khi nơi tiêu thụ dữ liệu chậm hơn nơi tạo dữ liệu. Event loop cần giới hạn lượng dữ liệu pending trên mỗi kết nối (ví dụ giới hạn byte trong queue), quyết định tạm ngừng đọc, từ chối thêm request hoặc ngắt kết nối theo chính sách. Non-blocking không loại bỏ áp lực bộ nhớ; nó khiến việc quản lý áp lực này trở thành trách nhiệm rõ ràng của ứng dụng.

## 12. `ByteBuffer.flip`, `clear` và `compact` trong network I/O

Cả hai chế độ đều dùng `ByteBuffer`; chuyển blocking mode không tự thay đổi quy tắc buffer.

| Bước | Buffer state |
| --- | --- |
| Chuẩn bị nhận byte | `clear()` → `position=0`, `limit=capacity` |
| Gọi `channel.read(buffer)` | `position` tăng theo byte nhận thực tế |
| Chuẩn bị giải mã/xử lý | `flip()` → `limit=oldPosition`, `position=0` |
| Giải mã/tiêu thụ | `position` tăng theo số byte đã xử lý |
| Đã xử lý hết | `clear()` để nhận lần tiếp theo |
| Chưa xử lý hết | `compact()` giữ phần dư trước khi nhận thêm |

**`clear()` không xóa byte khỏi vùng nhớ**. Nó chỉ thiết lập lại position và limit. Nếu parser còn một phần thông điệp chưa đủ byte, `compact()` đưa phần chưa xử lý về đầu buffer, giúp nhận thêm dữ liệu ở vùng trống phía sau.

Đối với UTF-8 hoặc các protocol có record bị chia qua nhiều lần nhận, mỗi `read` không nhất thiết tương ứng một chuỗi/hàng. Cần parser theo giao thức và decoder có khả năng xử lý dữ liệu đến từng phần. Ví dụ non-blocking ở trên dùng ASCII với độ dài cố định nhằm cô lập chủ đề blocking mode, không áp dụng nguyên xi để xử lý text tùy ý.

## 13. So sánh blocking và non-blocking

| Tiêu chí | Blocking | Non-blocking |
| --- | --- | --- |
| Khi I/O chưa sẵn sàng | Luồng gọi có thể chờ | Lời gọi có thể trở về không tiến độ |
| Điều khiển luồng | Tuyến tính, dễ đọc | Event-driven/state machine thường phức tạp hơn |
| Công cụ NIO phổ biến | `SocketChannel`, `ServerSocketChannel` blocking | `configureBlocking(false)` + `Selector` |
| Xử lý kết quả `read` | Vẫn phải kiểm tra số byte, EOF | Phải kiểm tra số byte, `0`, EOF |
| Xử lý `write` | Vẫn phải kiểm tra partial write | Phải giữ pending bytes và chờ readiness |
| Chi phí nhiều kết nối chờ | Mô hình nhiều luồng có thể tốn tài nguyên | Một event loop có thể quản lý nhiều socket |
| Thử thách thiết kế | Thread, timeout, hủy, giới hạn tài nguyên | Key, trạng thái kết nối, buffer, backpressure, lỗi |
| Hiệu năng | Có thể tốt cho ứng dụng đơn giản | Có thể tốt cho lượng lớn socket chủ yếu chờ |
| Bản chất | Một cách thực hiện I/O | Một cách thực hiện I/O, không tự đồng nghĩa bất đồng bộ |

Chọn mô hình theo yêu cầu cụ thể. Nếu số kết nối ít và công việc chủ yếu tuần tự, blocking có thể dễ bảo trì. Nếu phải quản lý hàng nghìn kết nối chủ yếu đợi sự kiện, non-blocking kết hợp selector có thể phù hợp hơn. Cần **benchmark trong môi trường thực** thay vì khẳng định một chế độ luôn nhanh hơn.

## 14. Non-blocking khác asynchronous ở điểm nào?

Hai thuật ngữ thường bị dùng lẫn:

- **Non-blocking channel**: một lần gọi I/O có thể trở về ngay nếu chưa tiến triển. Ứng dụng thường tiếp tục bằng `Selector`, readiness và thao tác I/O **chủ động ở lần sau**.
- **Asynchronous channel**: API như `AsynchronousSocketChannel` dùng callback hoặc `Future` để biểu diễn một thao tác bất đồng bộ đang diễn ra hoặc đã hoàn tất. Cơ chế hoàn thành thuộc hợp đồng API asynchronous, không phải chính thao tác `SocketChannel.read` non-blocking.

Một event loop non-blocking có thể lập trình theo phong cách “asynchronous” ở cấp ứng dụng, nhưng không có nghĩa `SocketChannel.configureBlocking(false)` biến đối tượng thành `AsynchronousSocketChannel`. Hai loại có API và mô hình hoàn thành khác nhau.

Ngoài ra, `CompletableFuture` hoặc luồng riêng chạy một lời gọi blocking không tự chuyển **socket** sang non-blocking mode; chỉ cách tổ chức tác vụ ở tầng ứng dụng thay đổi.

## 15. Thời gian chờ, đóng kết nối và xử lý lỗi

Ứng dụng cần chính sách rõ cho:
- **Kết nối không hoàn tất**: timeout cho connect; xử lý `finishConnect` thất bại.
- **Peer im lặng**: timeout ở tầng giao thức/ứng dụng; non-blocking + selector timeout giúp event loop đánh giá deadline.
- **Đọc được `-1`**: peer kết thúc chiều gửi; hủy interest và đóng hoặc xử lý half-close theo giao thức.
- **Ghi chưa hết**: giữ buffer pending, không giải phóng/`clear` sớm.
- **Channel bị đóng khi đang được dùng**: bắt và xử lý `IOException`/`ClosedChannelException` hoặc lỗi cụ thể.
- **Selector key hủy**: kiểm tra `key.isValid()`; `interestOps` hoặc readiness trên key không hợp lệ có thể gây `CancelledKeyException`.
- **Đóng tài nguyên**: dùng `try-with-resources` khi ownership rõ, và quản lý vòng đời channel đã được gắn với selector.

`selector.select(timeout)` không đồng nghĩa timeout cho từng socket. Đây là khoảng chờ readiness của **lần lựa chọn**, còn timeout nghiệp vụ cần theo dõi deadline của từng kết nối và kiểm tra sau mỗi lần thức dậy.

Không giả định mỗi lần `select()` trả về đều có I/O hữu ích: selector có thể thức dậy do `wakeup()`, đóng, ngắt hoặc do các điều kiện khác; kiểm tra số key sẵn sàng và trạng thái channel trước khi xử lý.

**Bảo mật:** Khi xử lý dữ liệu từ mạng, giới hạn kích thước request, thời gian giữ kết nối, lượng buffer pending và số kết nối. Không cấp phát buffer vô hạn theo thông tin chiều dài do client gửi mà chưa kiểm tra.

## 16. Những lỗi phổ biến

| Sai lầm | Hậu quả | Cách đúng |
| --- | --- | --- |
| Nghĩ non-blocking `read()==0` là EOF | Kết thúc kết nối sớm | Chỉ `-1` là EOF trong hợp đồng đọc |
| Nghĩ blocking `read` luôn lấp đầy buffer | Parser nhận thiếu byte | Theo dõi số byte đã đọc, dùng framing |
| Nghĩ `write` luôn gửi hết dữ liệu | Mất phần response chưa gửi | Giữ `ByteBuffer` và tiếp tục từ position hiện tại |
| Quên `configureBlocking(false)` trước `register` | `IllegalBlockingModeException` | Cấu hình non-blocking trước khi đăng ký |
| Cố `configureBlocking(true)` khi vẫn đăng ký selector | `IllegalBlockingModeException` | Hoàn thành hủy đăng ký trước khi chuyển mode |
| Gọi `accept()` non-blocking rồi không kiểm tra null | `NullPointerException` | Kiểm tra kết quả trước khi dùng |
| Gọi `read/write` trước khi `finishConnect` xong | Sai trạng thái kết nối | Xử lý `OP_CONNECT` và `finishConnect` |
| Lặp liên tục khi `read/write == 0` | Busy spin, CPU cao | Trả quyền điều khiển cho Selector/event loop |
| Giữ `OP_WRITE` liên tục khi không có dữ liệu chờ | Vòng sự kiện thức dậy nhiều | Chỉ bật `OP_WRITE` khi pending write |
| Không xóa key khỏi `selectedKeys()` | Xử lý lặp sự kiện cũ | Duyệt qua iterator và `remove()` |
| Coi một lần `read` là một TCP message | Sai framing | Thiết kế protocol parser rõ ràng |
| Nghĩ `FileChannel` có thể đăng ký selector mạng | Không đúng kiểu API | Dùng `SelectableChannel` phù hợp |
| Đóng buffer/peer sai vòng đời | Mất dữ liệu hoặc lỗi I/O | Thiết kế ownership, shutdown và backpressure |

## 17. Bài tập kiểm tra

**Câu 1.** Một `SocketChannel` non-blocking có `dst.remaining() == 128` và gọi `read(dst)` trả về `0`. Có thể kết luận peer đã đóng kết nối chưa?

**Không.** `0` chỉ cho biết lần gọi hiện tại không nhận byte; chờ `OP_READ` hoặc cơ chế đợi phù hợp. Nếu `read` trả về `-1` mới là EOF.

**Câu 2.** `write(src)` trả về 3 khi ban đầu `src.remaining()==10`. Còn bao nhiêu byte cần xử lý, và cần làm gì?

**Còn 7 byte.** `src.position` tăng 3; không `clear` sớm. Nếu dùng non-blocking và chưa thể gửi tiếp, giữ buffer, quan tâm `OP_WRITE` và thử ghi phần dư khi sẵn sàng.

**Câu 3.** `ServerSocketChannel` đã được đăng ký với `Selector`. Có thể gọi `server.configureBlocking(true)` ngay không?

**Không.** Nếu còn đăng ký selector, chuyển về blocking ném `IllegalBlockingModeException`; cần quản lý hủy đăng ký, xử lý canceled keys và vòng đời selector đúng cách.

**Câu 4.** Non-blocking `SocketChannel.connect(remote)` trả về `false`. Có phải thất bại kết nối?

**Không.** Nó có thể chỉ nghĩa là kết nối đang được thực hiện. Đăng ký `OP_CONNECT`, gọi `finishConnect()` khi sẵn sàng và xử lý lỗi nếu có.

**Câu 5.** Vì sao một server non-blocking có thể phục vụ nhiều kết nối trên một event loop, nhưng vẫn có thể phản hồi chậm?

**Vì event loop vẫn là mã chạy trên CPU.** Nếu handler làm việc chặn lâu, xử lý quá nặng, hoặc bận quay vòng khi không có I/O, các kết nối khác sẽ bị chậm. Phải tránh tác vụ blocking trên event loop hoặc đưa tác vụ nặng sang nơi thực thi riêng có kiểm soát.

## 18. Ghi nhớ

1. **Blocking**: thao tác I/O có thể giữ luồng chờ. **Non-blocking**: thao tác có thể trả ngay với kết quả chưa tiến triển.
2. **`configureBlocking(false)`** áp dụng trên `SelectableChannel`; NIO socket mới thường bắt đầu ở blocking mode.
3. **`accept()==null`**, **`read()==0`**, **`read()==-1`**, **`connect()==false`** là những trạng thái **khác nhau** phải xử lý đúng hợp đồng.
4. **`Selector`** giúp theo dõi readiness `OP_ACCEPT`, `OP_CONNECT`, `OP_READ`, `OP_WRITE` mà không busy-spin.
5. **Partial read/write và `ByteBuffer.position/limit`** là trách nhiệm của ứng dụng ở cả hai chế độ; với non-blocking cần lưu trạng thái còn lại và quản lý backpressure.
6. Blocking, non-blocking và asynchronous là những khái niệm liên quan nhưng **không đồng nghĩa**. Chọn kiến trúc dựa trên nhu cầu, độ phức tạp và tải thực tế.

**Tài liệu chuẩn:** Java SE 17 API — `java.nio.channels.SelectableChannel`, `ServerSocketChannel`, `SocketChannel`, `Selector`, `SelectionKey`, `java.nio.ByteBuffer`. Đặc biệt tham khảo Javadoc về `configureBlocking`, `register` và các kết quả trả về của `accept/read/write/connect/finishConnect`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nio-networking-003">Connect, finishConnect, accept, read và write</a>

<details>
<summary>Xem chi tiết</summary>

# Connect, finishConnect, accept, read và write trong Java NIO mạng

## 1. Bức tranh tổng thể: từ thiết lập kết nối đến trao đổi byte

Java NIO cung cấp hai channel quan trọng cho mạng TCP:

- `ServerSocketChannel`: **lắng nghe** tại một địa chỉ/cổng và `accept()` một kết nối TCP đến, tạo ra `SocketChannel` cho phía server trao đổi dữ liệu.
- `SocketChannel`: đại diện một kết nối TCP; phía client dùng `connect(...)`/`finishConnect()` để thiết lập kết nối, cả hai phía dùng `read(...)` và `write(...)` để nhận/gửi byte.

Có thể sử dụng các channel này ở hai chế độ:

- **Blocking** (mặc định): những lời gọi có thể chờ đến khi một thao tác có thể hoàn tất theo hợp đồng, nhưng một `read` hoặc `write` vẫn không nhất thiết xử lý hết buffer.
- **Non-blocking** (`configureBlocking(false)`): lời gọi trả về khi chưa thể hoàn tất ngay; chương trình phải lưu trạng thái và thử tiếp vào lúc thích hợp, thường phối hợp `Selector`.

Luồng hoạt động điển hình:

~~~text
             SERVER                                  CLIENT
    ServerSocketChannel.open()                 SocketChannel.open()
               |                                        |
           bind(local)                                 |
               |                                  connect(remote)
               |                                        |
         accept() chờ/yield                    [có thể pending]
               |                                        |
       SocketChannel accepted                   finishConnect()
               |                                        |
               +----------- TCP connected --------------+
               |                                        |
         read() / write()                         write() / read()
               |                                        |
          close() / EOF                    shutdownOutput() / close()
~~~

`accept()` chỉ thuộc `ServerSocketChannel`. `connect()` và `finishConnect()` thuộc `SocketChannel`. Còn `read()/write()` dùng trên các `SocketChannel` của **cả hai đầu kết nối**; giao tiếp TCP là hai chiều.

**Điều cần phân biệt:** hoàn tất kết nối là một trạng thái khác với có dữ liệu để đọc hoặc chỗ trống để ghi. `finishConnect() == true` không có nghĩa bên kia đã gửi dữ liệu; `OP_READ`/`OP_WRITE` không có nghĩa một thông điệp ứng dụng đã đủ.

## 2. Mở, bind, connect và accept

### 2.1. Phía server: bind rồi accept

~~~java
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

try (ServerSocketChannel server = ServerSocketChannel.open()) {
    server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
    System.out.println(server.getLocalAddress());

    try (SocketChannel peer = server.accept()) {
        // Dùng peer.read(...) và peer.write(...).
    }
}
~~~

Đây là **mảnh minh họa**, không phải ví dụ độc lập hoàn toàn: đặt trong `main` hoặc phương thức khai báo/xử lý `IOException`, và cần một client kết nối thì lời gọi blocking `accept()` mới tiếp tục.

- `ServerSocketChannel.open()` tạo channel server chưa bind.
- `bind(local)` chọn địa chỉ IP và port để lắng nghe. `port=0` yêu cầu hệ điều hành cấp một cổng khả dụng; `getLocalAddress()` cho biết địa chỉ thực tế sau bind.
- `accept()` trả về **một `SocketChannel` mới** cho kết nối được chấp nhận. Server socket vẫn dùng để chờ thêm kết nối; không dùng chính nó để đọc/ghi payload.
- Channel được `accept()` tạo ra ban đầu ở **blocking mode**, kể cả khi `ServerSocketChannel` được cấu hình non-blocking. Nếu muốn sử dụng nó với selector, phải chủ động `peer.configureBlocking(false)` trước khi đăng ký.

Khi server dùng blocking mode, `accept()` thường chờ cho đến khi có kết nối hoặc xảy ra lỗi/đóng channel. Khi dùng non-blocking mode, `accept()` **trả `null`** nếu hiện chưa có kết nối nào có thể nhận; `null` không đồng nghĩa server socket đã đóng.

Lưu ý: server được bind trên loopback (`127.0.0.1`/`::1` tương ứng môi trường) chỉ để giao tiếp nội bộ trong máy. Khi muốn nhận kết nối từ máy khác, cần địa chỉ bind, cấu hình mạng, firewall và chính sách bảo mật phù hợp.

### 2.2. Phía client: connect

~~~java
SocketChannel client = SocketChannel.open();
boolean connectedNow = client.connect(remoteAddress);
~~~

`remoteAddress` là một `SocketAddress` hợp lệ như `new InetSocketAddress("localhost", port)`. Sau khi `connect(...)`:

| Chế độ của client | Kết quả `connect(remote)` | Ý nghĩa |
| --- | --- | --- |
| Blocking (mặc định) | Thông thường trả `true` nếu kết nối được thiết lập; có thể chặn hoặc ném `IOException`. | Kết nối đã hoàn tất khi trở về thành công. |
| Non-blocking | Trả `true` nếu hoàn tất ngay, hoặc `false` nếu đang kết nối. | Nếu trả `false`, còn bước `finishConnect()` trước khi truyền dữ liệu. |

`connect` không đồng nghĩa “đã có một kết nối hoàn chỉnh” trong **non-blocking mode** khi nó trả `false`. Ở thời điểm đó, `client.isConnectionPending()` có thể trả `true`. Muốn biết đã thực sự sẵn sàng gửi/nhận, dùng `isConnected()` hoặc kết quả của `finishConnect()` theo chu trình phù hợp.

Có thể gặp ngoại lệ khi địa chỉ không hợp lệ, không có route, từ chối kết nối, cổng bị chặn, channel đóng, hoặc thao tác kết nối không hợp lệ với trạng thái hiện tại. Ví dụ cố connect lại khi đã kết nối có thể gặp `AlreadyConnectedException`; khi đã có kết nối pending, có thể gặp `ConnectionPendingException`.

## 3. finishConnect(): hoàn tất kết nối đang chờ

`SocketChannel.finishConnect()` dùng để hoàn thành quá trình TCP connect đã được khởi tạo nhưng chưa xong.

~~~java
client.configureBlocking(false);
boolean connected = client.connect(remoteAddress);
if (!connected) {
    // Một lúc sau, khi channel sẵn sàng kết nối:
    connected = client.finishConnect();
}
~~~

Đoạn này là minh họa theo trạng thái. Với client **non-blocking**:

- `finishConnect() == true`: kết nối đã hoàn tất; có thể bắt đầu `read`/`write`.
- `finishConnect() == false`: vẫn chưa hoàn tất; lưu trạng thái, đợi sự kiện sẵn sàng rồi thử lại.
- Ném `IOException`: việc thiết lập kết nối thất bại.
- Gọi khi không có thao tác kết nối đang chờ và channel cũng chưa kết nối có thể ném `NoConnectionPendingException`.

Trong **blocking mode**, `finishConnect()` có thể chờ tới khi tiến trình kết nối hoàn tất hoặc phát sinh lỗi. Với mô hình non-blocking, không gọi lặp liên tục trong vòng `while` không có cơ chế chờ vì dễ gây busy-spin.

### 3.1. Selector với OP_CONNECT

`Selector` cho phép đợi một hoặc nhiều channel báo trạng thái sẵn sàng. Mẫu đơn giản cho non-blocking connect:

~~~java
// client và remoteAddress đã được tạo.
// Đây là đoạn minh họa, đặt trong phương thức throws IOException.
client.configureBlocking(false);

boolean connected = client.connect(remoteAddress);
if (!connected) {
    try (Selector selector = Selector.open()) {
        SelectionKey key = client.register(selector, SelectionKey.OP_CONNECT);

        while (!client.finishConnect()) {
            int ready = selector.select(5_000);
            if (ready == 0) {
                throw new java.net.SocketTimeoutException(
                        "Không hoàn tất kết nối trong khoảng chờ");
            }

            selector.selectedKeys().clear();
        }
        key.cancel();
    }
}

// Chỉ tại đây mới thực hiện read/write trên kết nối đã thiết lập.
~~~

Các import thêm cho mảnh minh họa: `java.nio.channels.Selector` và `java.nio.channels.SelectionKey`.

Trong ví dụ, `selector.select(5_000)` có thể trở về sau khi có sự kiện, timeout, wakeup hoặc do tác động khác; cách ứng dụng thực tế xử lý sự kiện sẽ xét từng selected key và trạng thái channel. Đoạn này chỉ thể hiện vòng đời **một kết nối đang pending**, không phải mẫu selector production hoàn chỉnh.

Trong event loop production, thường xử lý `key.isConnectable()` rồi gọi `finishConnect()`. Khi thành công, thay `interestOps` khỏi `OP_CONNECT` và đăng ký các thao tác mong muốn như `OP_READ` hoặc `OP_WRITE`; luôn có quy tắc timeout, hủy và đóng channel nếu kết nối thất bại.

### 3.2. Phân biệt OP_CONNECT và OP_ACCEPT

| Event | Channel đăng ký | Khi sẵn sàng |
| --- | --- | --- |
| `SelectionKey.OP_CONNECT` | `SocketChannel` đang kết nối non-blocking | Gọi `finishConnect()` để xác nhận thiết lập xong; có thể vẫn cần xử lý lỗi. |
| `SelectionKey.OP_ACCEPT` | `ServerSocketChannel` non-blocking | Gọi `accept()` để lấy `SocketChannel`; có thể trả `null` nếu không còn kết nối chờ. |
| `SelectionKey.OP_READ` | `SocketChannel` đã kết nối | Gọi `read(...)`; có thể đọc ít byte, `0` hoặc `-1`. |
| `SelectionKey.OP_WRITE` | `SocketChannel` đã kết nối | Gọi `write(...)` khi có dữ liệu chờ; có thể ghi một phần hoặc `0`. |

Readiness chỉ là lời nhắc **nên thử thao tác**, không phải cam kết toàn bộ yêu cầu I/O sẽ hoàn tất trong một lần gọi.

## 4. accept(): nhận kết nối chờ, không đọc dữ liệu

Một server TCP có thể chấp nhận nhiều client. Mỗi `accept()` thành công trả một `SocketChannel` riêng đại diện kết nối đó. SocketChannel này có hai hướng dữ liệu độc lập theo luồng byte TCP.

Trong non-blocking mode:

~~~java
try (ServerSocketChannel server = ServerSocketChannel.open()) {
    server.bind(new InetSocketAddress("127.0.0.1", 9000));
    server.configureBlocking(false);

    SocketChannel peer = server.accept();
    if (peer != null) {
        peer.configureBlocking(false);
        // Đăng ký OP_READ hoặc quản lý tiếp theo trong event loop.
    }
}
~~~

Đây là **mảnh minh họa**, cần `import java.net.InetSocketAddress;` và API channel tương ứng. Port 9000 chỉ dùng minh họa; có thể đang bị chiếm trong môi trường thực tế.

Nếu `accept() == null`, không nên suy ra server có lỗi. Nó chỉ cho biết **lượt gọi non-blocking này** chưa lấy được kết nối. Event loop cần đợi `OP_ACCEPT` để thử lại.

Với selector, một lượt `OP_ACCEPT` có thể có nhiều kết nối chờ; một mẫu thường dùng là:

~~~java
if (key.isAcceptable()) {
    ServerSocketChannel listener = (ServerSocketChannel) key.channel();
    SocketChannel peer;

    while ((peer = listener.accept()) != null) {
        peer.configureBlocking(false);
        peer.register(selector, SelectionKey.OP_READ,
                new ClientState());
    }
}
~~~

Đây là **pseudocode tích hợp vào event loop**: `key`, `selector` và `ClientState` cần được ứng dụng định nghĩa; `ClientState` có thể chứa các buffer và trạng thái protocol. Vòng `while` dạng này thích hợp khi listener đã được cấu hình **non-blocking**; nếu listener blocking thì gọi `accept()` thêm lần nữa có thể chờ vô hạn.

Một server thực tế còn phải áp dụng giới hạn số kết nối, timeout không hoạt động, backpressure, xác thực ứng dụng và quy trình đóng socket theo trạng thái.

## 5. read(): lấy byte từ TCP vào ByteBuffer

Trên `SocketChannel`, cú pháp:

~~~java
ByteBuffer buffer = ByteBuffer.allocate(1024);
int count = socket.read(buffer);
~~~

`socket` ở đây là `SocketChannel` đã được kết nối. Các kết quả đọc:

| Giá trị trả về | Diễn giải |
| --- | --- |
| `count > 0` | Chính xác `count` byte được chép vào buffer; `position` tăng tương ứng. |
| `count == 0` | Không byte nào được chuyển trong lần gọi; thường gặp khi non-blocking và chưa có dữ liệu. |
| `count == -1` | Peer đã kết thúc phía gửi (end-of-stream) và không còn byte tiếp theo để đọc. |

Trong blocking mode, một lần đọc với buffer còn chỗ thường đợi để có ít nhất một byte, gặp EOF hoặc lỗi; **không đợi để lấp đầy buffer**. Trong non-blocking mode, `read()` có thể trả `0` rất nhanh. Không suy ra `0` là kết nối đã bị đóng. `-1` thể hiện EOF của hướng đọc.

Sau khi `read(buffer)` ghi một lượng byte vào buffer, chương trình chuyển sang đọc chính buffer bằng `flip()`:

~~~java
int n = socket.read(buffer);
if (n > 0) {
    buffer.flip();

    while (buffer.hasRemaining()) {
        byte b = buffer.get();
        // Phân tích byte b.
    }

    buffer.clear();
} else if (n == -1) {
    // Peer đã đóng hướng gửi.
} else {
    // n == 0: chưa tiến triển; chờ lần sẵn sàng tiếp theo.
}
~~~

Đây là **mảnh minh họa** dành cho trường hợp ứng dụng xử lý **toàn bộ byte vừa đọc**. Nếu parser mới tiêu thụ được một phần thông điệp, phải giữ các byte chưa xử lý bằng `compact()` hoặc cấu trúc tích lũy phù hợp, không gọi `clear()` để chúng có thể bị ghi đè.

### 5.1. Một lần read không phải một thông điệp

TCP là **byte stream**: không giữ nguyên ranh giới giữa các `write(...)` phía gửi. Nếu người gửi gửi:

~~~text
[HEL][LO]
~~~

người nhận có thể thấy:

~~~text
read #1: HELLO
~~~

hoặc:

~~~text
read #1: H
read #2: EL
read #3: LO
~~~

Cả hai đều phù hợp với TCP. Ứng dụng phải có **framing** như độ dài gói, delimiter hoặc khuôn dạng record. Nếu header dài 4 byte, cần đọc đủ 4 byte theo trạng thái chứ không kết luận chỉ từ một lần `read`.

### 5.2. Khi EOF đến lúc một frame còn dang dở

Nếu đã nhận 2/4 byte header rồi `read() == -1`, đó là **thông điệp bị thiếu/truncated** theo giao thức độ dài cố định. Ngược lại, khi ứng dụng chỉ đọc toàn bộ luồng đến EOF, `-1` là điều kiện hoàn tất bình thường.

`SocketChannel.shutdownOutput()` cho phép một phía ngừng gửi nhưng có thể tiếp tục đọc từ peer; đây là dạng **half-close** TCP. `-1` trên hướng đọc không nhất thiết có nghĩa ứng dụng phải lập tức hủy dữ liệu gửi còn chờ theo chiều ngược lại; quyết định thuộc protocol.

## 6. write(): gửi byte từ ByteBuffer sang kết nối

`write(buffer)` lấy các byte từ **`position` đến trước `limit`** của buffer và tăng position theo số byte thực gửi:

~~~java
ByteBuffer buffer = ByteBuffer.wrap(
        "PING".getBytes(StandardCharsets.US_ASCII));

int written = socket.write(buffer);
System.out.println("remaining=" + buffer.remaining());
~~~

Đây là **mảnh minh họa**, có `socket` đã kết nối và import `java.nio.ByteBuffer`, `java.nio.charset.StandardCharsets`. Kết quả `remaining` phụ thuộc số byte được socket tiêu thụ ở lần write.

- `written > 0`: số byte đã được channel tiếp nhận; có thể vẫn còn byte phải gửi.
- `written == 0`: chưa byte nào được tiêu thụ; ở non-blocking mode thường cần đợi `OP_WRITE`.
- Các lỗi I/O được thể hiện bằng exception. `write()` không dùng `-1` để báo EOF như `read()`.

Một mẫu blocking phù hợp cho gửi hết buffer:

~~~java
static void writeFully(SocketChannel channel, ByteBuffer src)
        throws IOException {
    while (src.hasRemaining()) {
        int n = channel.write(src);
        if (n == 0) {
            throw new IOException("Blocking write không tiến triển");
        }
    }
}
~~~

Mẫu trên giả định channel **blocking và đã kết nối**. Sau partial write, `src.position()` tự tiến đến byte tiếp theo, vì vậy chỉ gọi lại `write(src)`. **Không `flip()` lần nữa**, không `clear()` khi vẫn còn `src.hasRemaining()`.

Với non-blocking, thay vì ném lỗi ngay ở `n == 0`, ứng dụng nên giữ buffer trong hàng đợi chờ gửi và đăng ký `OP_WRITE`. Chỉ tái sử dụng hoặc giải phóng buffer khi mọi byte của nó đã được tiêu thụ.

### 6.1. OP_WRITE và backpressure

Nếu ứng dụng luôn quan tâm `OP_WRITE` dù không có dữ liệu chờ, selector có thể báo socket sẵn sàng ghi rất thường xuyên, gây bận CPU. Quy tắc thường dùng:

1. Khi có dữ liệu gửi, xếp buffer vào hàng đợi và bật `OP_WRITE` nếu chưa thể gửi hết.
2. Khi có `OP_WRITE`, thử ghi buffer ở đầu hàng đợi, giữ phần còn lại nếu partial write.
3. Khi tất cả buffer đã hết `remaining` và hàng đợi trống, **bỏ `OP_WRITE` khỏi interest set**, chỉ giữ những event cần thiết như `OP_READ`.

`write()` thành công chỉ xác nhận byte được channel tiếp nhận theo hợp đồng, **không xác nhận** ứng dụng peer đã xử lý thông điệp. Nếu cần xác nhận nghiệp vụ, hãy định nghĩa ACK/response ở tầng ứng dụng.

## 7. Blocking và non-blocking: bảng hành vi cần nhớ

| Thao tác | Blocking mode | Non-blocking mode |
| --- | --- | --- |
| `SocketChannel.connect(remote)` | Chờ hoàn tất, thường trả `true` hoặc lỗi. | Có thể trả `false` khi kết nối đang pending. |
| `SocketChannel.finishConnect()` | Nếu pending, có thể chờ đến khi hoàn tất/lỗi. | Trả `false` khi vẫn pending; dùng `OP_CONNECT` để đợi. |
| `ServerSocketChannel.accept()` | Có thể chờ client đến. | Trả `null` nếu chưa có kết nối sẵn. |
| `SocketChannel.read(dst)` | Có thể chờ ít nhất một byte/EOF/lỗi, không bảo đảm đầy buffer. | Có thể trả `0` nếu chưa có byte sẵn. |
| `SocketChannel.write(src)` | Có thể chờ để ghi tiến triển, không bảo đảm hoàn tất buffer trong một lượt. | Có thể trả `0` nếu tạm thời không ghi được. |

`configureBlocking(false)` cần được gọi **trước** khi đăng ký channel với selector. Nếu channel đã đăng ký với một selector và registration còn hiệu lực, việc chuyển lại blocking có thể gây `IllegalBlockingModeException`. Khi muốn đổi về blocking, cần hủy registration, bảo đảm key không còn đăng ký hợp lệ, rồi mới thay đổi mode. Nhiều thiết kế giữ nguyên non-blocking trong suốt vòng đời kết nối để tránh phức tạp này.

Không xây dựng vòng `while` gọi ngay lại `finishConnect/accept/read/write` mỗi khi nhận `false/null/0` mà không có điều kiện chờ hoặc giới hạn lượt xử lý. Nó có thể trở thành busy-spin, làm CPU tăng và giảm khả năng phục vụ nhiều kết nối.

## 8. Ví dụ Java 17+ chạy được: TCP loopback với đủ connect, accept, read, write

Ví dụ này dùng **blocking mode** để trình bày luồng kết nối trọn vẹn, dễ biên dịch và kiểm chứng. Một luồng server nhận kết nối từ `ServerSocketChannel.accept()`, đọc đúng 4 byte `PING` rồi trả 2 byte `OK`. Luồng main đóng vai client, kết nối loopback, gửi PING và đọc phản hồi.

`finishConnect()` không cần gọi trong ví dụ blocking vì `connect()` hoàn tất trước khi trả thành công. Phần sau có riêng ví dụ non-blocking `finishConnect`.

Lưu file `NioTcpLifecycleDemo.java`:

~~~java
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class NioTcpLifecycleDemo {

    static void readFully(SocketChannel channel, ByteBuffer dst)
            throws IOException {
        while (dst.hasRemaining()) {
            int n = channel.read(dst);
            if (n == -1) {
                throw new EOFException("Peer đóng khi dữ liệu chưa đủ");
            }
            if (n == 0) {
                throw new IOException("Blocking read không tiến triển");
            }
        }
    }

    static void writeFully(SocketChannel channel, ByteBuffer src)
            throws IOException {
        while (src.hasRemaining()) {
            int n = channel.write(src);
            if (n == 0) {
                throw new IOException("Blocking write không tiến triển");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        try (ServerSocketChannel server = ServerSocketChannel.open()) {
            server.bind(new InetSocketAddress(
                    InetAddress.getLoopbackAddress(), 0));

            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<String> received = executor.submit(() -> {
                    try (SocketChannel peer = server.accept()) {
                        ByteBuffer incoming = ByteBuffer.allocate(4);
                        readFully(peer, incoming);
                        incoming.flip();

                        String request = StandardCharsets.US_ASCII
                                .decode(incoming).toString();

                        ByteBuffer reply = ByteBuffer.wrap(
                                "OK".getBytes(StandardCharsets.US_ASCII));
                        writeFully(peer, reply);
                        return request;
                    }
                });

                try (SocketChannel client = SocketChannel.open()) {
                    boolean connected = client.connect(
                            (InetSocketAddress) server.getLocalAddress());

                    ByteBuffer request = ByteBuffer.wrap(
                            "PING".getBytes(StandardCharsets.US_ASCII));
                    writeFully(client, request);

                    ByteBuffer answer = ByteBuffer.allocate(2);
                    readFully(client, answer);
                    answer.flip();

                    String acknowledgment = StandardCharsets.US_ASCII
                            .decode(answer).toString();

                    System.out.println("connected=" + connected);
                    System.out.println("response=" + acknowledgment);
                }

                System.out.println("received=" +
                        received.get(5, TimeUnit.SECONDS));
            } finally {
                executor.shutdownNow();
            }
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17+:

~~~bash
javac NioTcpLifecycleDemo.java
java NioTcpLifecycleDemo
~~~

**Stdout trong môi trường cho phép kết nối TCP loopback:**

~~~text
connected=true
response=OK
received=PING
~~~

Đối chiếu với các API:

1. `server.bind(...)` lắng nghe ở một port loopback do hệ điều hành chọn. Client dùng `server.getLocalAddress()` nên không cần hard-code cổng.
2. `server.accept()` ở luồng phụ chờ một kết nối và trả về `SocketChannel peer`. Bởi vì accepted socket mặc định blocking, vòng `readFully` phù hợp.
3. `client.connect(...)` ở blocking mode trả `true` khi kết nối thiết lập thành công.
4. `client.write(...)` gửi 4 byte PING; hàm `writeFully` lặp nếu cần cho tới khi buffer hết dữ liệu.
5. `peer.read(...)` nhận đủ 4 byte bằng `readFully` rồi `flip()` để giải mã ASCII.
6. Server ghi lại 2 byte OK; client đọc đủ, `flip()` và decode.
7. Chỉ luồng main in ra stdout, nên thứ tự các dòng được cố định. `Future.get` lấy kết quả server sau khi xử lý xong.

Ví dụ này hoạt động qua TCP thật trên máy cục bộ, nên cần môi trường cho phép mở socket loopback. Nó không dùng selector và không cố tình kích hoạt partial read/write; các hàm `readFully`/`writeFully` được viết đúng hợp đồng ngay cả nếu một lần truyền ít byte hơn yêu cầu. Trong ứng dụng production, nên thêm deadline/timeout cho toàn bộ vòng đời đọc/ghi, giới hạn kích thước và cơ chế dừng luồng nếu peer không phản hồi.

## 9. Ví dụ non-blocking connect/finishConnect đơn giản

Sau khi nắm chu trình blocking, có thể đổi riêng phía client sang non-blocking. Đoạn này minh họa **thiết lập kết nối**, không phải toàn bộ server production:

~~~java
try (SocketChannel client = SocketChannel.open();
     Selector selector = Selector.open()) {

    client.configureBlocking(false);
    boolean connected = client.connect(remote);

    if (!connected) {
        SelectionKey key = client.register(
                selector, SelectionKey.OP_CONNECT);

        while (!client.finishConnect()) {
            int ready = selector.select(5_000);
            if (ready == 0) {
                throw new java.net.SocketTimeoutException(
                        "Kết nối không sẵn sàng");
            }
            selector.selectedKeys().clear();
        }
        key.cancel();
    }

    // client.isConnected() == true:
    // Có thể đăng ký đọc/ghi hoặc gửi/nhận non-blocking.
}
~~~

Đây là **mảnh minh họa**, biến `remote` là `SocketAddress` được cung cấp trước và import `java.nio.channels.SocketChannel`, `Selector`, `SelectionKey`, `java.net.SocketAddress`. Đối với một event loop đa kết nối, nên dùng từng `SelectionKey` và attachment quản lý trạng thái thay vì vòng connect chuyên dụng như trên.

Nếu gọi `client.read(...)` hoặc `client.write(...)` ngay khi `connect(remote)` trả `false` mà chưa hoàn tất bằng `finishConnect()`, có thể gặp `NotYetConnectedException`. Do đó sự kiện `OP_CONNECT` và bước `finishConnect` cần được xử lý trước `OP_READ/OP_WRITE`.

### 9.1. Skeleton xử lý SelectionKey

~~~java
Iterator<SelectionKey> it = selector.selectedKeys().iterator();
while (it.hasNext()) {
    SelectionKey key = it.next();
    it.remove();

    if (!key.isValid()) {
        continue;
    }
    try {
        if (key.isAcceptable()) {
            // ServerSocketChannel.accept() trong non-blocking mode.
        }
        if (key.isConnectable()) {
            // SocketChannel.finishConnect() cho connect pending.
        }
        if (key.isReadable()) {
            // SocketChannel.read(...); xử lý >0, 0, -1.
        }
        if (key.isWritable()) {
            // SocketChannel.write(...); xử lý partial/0.
        }
    } catch (IOException ex) {
        key.cancel();
        key.channel().close();
    }
}
~~~

Đây là **pseudocode khung event loop**: `selector` và các channel cần được khởi tạo, đăng ký đúng mode, cũng như phải quản lý các `ByteBuffer` và trạng thái kết nối. Các nhánh chưa phải code production. Không thể dùng mỗi nhánh `isReadable` rồi đọc một lần và coi đã nhận đủ thông điệp.

## 10. Các tình huống biên dễ gây lỗi

### 10.1. connect trả false nhưng bỏ qua finishConnect

~~~java
client.configureBlocking(false);
boolean done = client.connect(remote);
// Sai nếu done == false mà lập tức client.write(...).
~~~

Cách đúng: khi pending, đăng ký `OP_CONNECT` và gọi `finishConnect()` cho tới khi thành công hoặc lỗi/timeout. Trạng thái `isConnected()` giúp theo dõi nhưng không thay thế hợp đồng hoàn tất của `finishConnect()`.

### 10.2. accept trả null rồi cho rằng server hỏng

Trong non-blocking mode, `accept() == null` là kết quả thường gặp khi chưa có kết nối chờ. Đợi `OP_ACCEPT` thay vì tự spin vô hạn.

### 10.3. read trả 0 bị hiểu là EOF

`0` có nghĩa chưa byte nào được chuyển trong lần đó; `-1` mới biểu thị hướng đọc đã hết dữ liệu. Với non-blocking, chờ `OP_READ`. Với blocking channel và buffer có chỗ, sự kiện `0` là dấu hiệu cần xem lại giả định về provider/trạng thái ứng dụng.

### 10.4. read() một lần rồi parse cả frame

Chưa chắc buffer đã có đủ frame. Cần kiểm tra `n` và `remaining()`, duy trì trạng thái parser qua nhiều lần đọc, xử lý trường hợp một read chứa nhiều frame. Dùng `compact()` khi có byte cuối đang dang dở.

### 10.5. write() xong liền clear buffer

Nếu write chỉ gửi một phần, `clear()` có thể khiến byte chưa gửi bị ghi đè. Giữ `position/limit` như hiện tại; gửi tiếp khi channel sẵn sàng.

### 10.6. Đăng ký OP_WRITE liên tục

Socket thường rất hay “sẵn sàng ghi”, ngay cả khi ứng dụng không có gì để gửi. Quan tâm `OP_WRITE` suốt thời gian kết nối có thể làm selector thức dậy quá nhiều; chỉ bật khi thực sự có byte chờ ghi, tắt khi hàng đợi trống.

### 10.7. Gọi configureBlocking(true) khi channel vẫn đăng ký selector

Nếu channel đang có registration hợp lệ, việc đổi sang blocking mode có thể ném `IllegalBlockingModeException`. Tổ chức vòng đời channel sao cho mode và selector registration nhất quán.

### 10.8. Không xử lý peer đóng nửa kết nối

Khi `read() == -1`, peer đã đóng hướng gửi; ứng dụng có thể còn byte chờ ghi theo chiều còn lại tùy giao thức. Chọn quy trình kết thúc an toàn, hoàn tất dữ liệu cần gửi hoặc hủy theo quy tắc rõ ràng. Đừng coi EOF là “socket đã hủy cả hai chiều” một cách mặc định.

### 10.9. Tin rằng write hoàn tất đồng nghĩa peer đã xử lý

Ghi hết buffer chỉ nói rằng dữ liệu đã được chuyển cho cơ chế channel ở phía gửi theo hợp đồng. TCP và logic ứng dụng phía nhận là các tầng khác. Nếu muốn xác nhận xử lý nghiệp vụ, giao thức cần thông điệp trả lời/ACK riêng.

### 10.10. Không giới hạn tài nguyên kết nối

Mỗi client có thể giữ socket, buffer và trạng thái parser. Server production cần giới hạn connections, kích thước frame, thời gian chờ header/payload, hàng đợi write, tốc độ nhận dữ liệu và các cơ chế đóng/hủy để tránh cạn bộ nhớ và file descriptor.

## 11. Tóm tắt bằng bảng quyết định

| Bạn đang làm gì? | API phù hợp | Dấu hiệu phải tiếp tục/xử lý |
| --- | --- | --- |
| Client bắt đầu kết nối | `SocketChannel.connect(remote)` | `false` trong non-blocking → đang pending. |
| Client có kết nối pending | `SocketChannel.finishConnect()` | `false` → đợi OP_CONNECT; `true` → kết nối xong. |
| Server nhận client | `ServerSocketChannel.accept()` | `null` trong non-blocking → đợi OP_ACCEPT. |
| Đọc payload vào buffer | `SocketChannel.read(dst)` | `>0` xử lý byte; `0` đợi tiếp; `-1` EOF. |
| Ghi buffer ra TCP | `SocketChannel.write(src)` | `>0` kiểm tra remaining; `0` đợi OP_WRITE. |
| Đọc thông điệp nhiều đợt | `ByteBuffer.flip/compact` + parser | Giữ byte chưa đủ frame; không clear nhầm. |
| Đợi sẵn sàng trên nhiều channel | `Selector.select` + `SelectionKey` | Xử lý đúng kind, mode, timeout và lifecycle. |

## 12. Bài tập tự kiểm tra

1. **Phương thức nào nhận TCP connection ở server?** `ServerSocketChannel.accept()`, trả về `SocketChannel` của client mới.
2. **Non-blocking `connect(remote)` trả `false` có phải thất bại?** Không. Kết nối đang chờ; cần `finishConnect()`.
3. **`finishConnect()` trả `false` nghĩa là gì?** Chưa hoàn tất, nên đợi readiness hoặc cơ chế retry thích hợp.
4. **Sự kiện nào thường gắn với kết nối client đang pending?** `SelectionKey.OP_CONNECT`.
5. **Non-blocking `accept()` chưa có kết nối thì trả gì?** `null`.
6. **SocketChannel do server accept tạo có tự non-blocking theo listener không?** Không. Channel accepted ban đầu ở blocking mode; cần chủ động cấu hình nếu dùng selector.
7. **`read(buffer)` trả `0` có phải EOF?** Không; `-1` mới báo EOF hướng đọc.
8. **`write(buffer)` đã gửi 3 trong 8 byte thì gửi tiếp từ đâu?** Từ `buffer.position()` hiện tại, không flip/clear lần nữa; còn 5 byte.
9. **Hai lần client.write("HE") và client.write("LLO") có bảo đảm server.read hai lần tương ứng không?** Không, TCP là byte stream.
10. **Tại sao phải tắt `OP_WRITE` khi không còn dữ liệu chờ?** Để event loop tránh các thông báo writable lặp liên tục.
11. **Đã connect thành công có nghĩa phía server gửi đủ một frame chưa?** Không. Connection lifecycle và message framing độc lập.
12. **Một socket nhận `-1` từ read có thể vẫn có dữ liệu cần gửi?** Có thể, theo protocol half-close; cần quyết định xử lý theo trạng thái ứng dụng.

**Ghi nhớ:** `connect` khởi đầu thiết lập TCP, `finishConnect` xác nhận hoàn tất kết nối pending, `accept` tạo socket của client ở phía server, và `read/write` truyền từng phần của **dòng byte**. Với NIO non-blocking, hãy kiểm tra **giá trị trả về, trạng thái buffer và readiness event**, bảo toàn các byte chưa xử lý, và có cơ chế đóng/timeout rõ ràng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nio-networking-004">TCP là luồng byte, không bảo toàn ranh giới bản tin</a>

<details>
<summary>Xem chi tiết</summary>

# TCP là luồng byte, không bảo toàn ranh giới bản tin — Java NIO mạng

## 1. Mục tiêu bài học

Khi hai ứng dụng trao đổi dữ liệu bằng TCP, phía gửi có thể gọi `write()` nhiều lần, nhưng phía nhận **không có bảo đảm rằng mỗi lần `read()` ứng với một lần `write()`**. TCP cung cấp **một luồng byte có thứ tự**, không cung cấp sẵn khái niệm “một bản tin” ở cấp ứng dụng.

Đây là điểm nền tảng khi lập trình `SocketChannel`, `ServerSocketChannel` và `ByteBuffer` trong Java NIO. Nếu bỏ qua, ứng dụng dễ gặp các lỗi: JSON nhận thiếu, hai lệnh dính liền, giải mã UTF-8 sai hoặc giao thức bị treo vì không biết một bản tin đã kết thúc hay chưa.

Sau bài này, bạn có thể:

- Giải thích chính xác vì sao một lần ghi không tương đương một lần đọc TCP.
- Phân biệt **TCP segment**, **byte stream**, **application message** và **frame**.
- Thiết kế giao thức xác định ranh giới bản tin bằng độ dài, delimiter hoặc kích thước cố định.
- Đọc/ghi đủ byte với `SocketChannel`, xử lý giá trị trả về nhỏ hơn dự kiến, `0` và `-1`.
- Dùng `ByteBuffer` đúng cách, đặc biệt với `flip()`, `clear()`, `compact()`.
- Phòng các lỗi về kích thước frame, UTF-8, blocking/non-blocking và dữ liệu đến từng phần.

## 2. TCP cung cấp gì và không cung cấp gì?

TCP là giao thức **hướng kết nối**, truyền một dãy byte có thứ tự. Khi kết nối còn hoạt động và dữ liệu được nhận thành công, TCP sắp xếp lại dữ liệu theo thứ tự byte của luồng; các cơ chế sequence number, retransmission, kiểm soát luồng và tắc nghẽn hoạt động bên dưới ứng dụng.

Tuy nhiên, **TCP không định nghĩa ranh giới bản tin của ứng dụng**. Ví dụ phía gửi thực hiện:

~~~text
write("ABC")
write("DEF")
write("GHI")
~~~

Phía nhận có thể quan sát các kết quả đọc như:

~~~text
Lần đọc 1: "ABCDEFGHI"
~~~

hoặc:

~~~text
Lần đọc 1: "A"
Lần đọc 2: "BCDE"
Lần đọc 3: "FGHI"
~~~

hoặc những cách chia khác, miễn thứ tự các byte được giữ nguyên. Đây là **những khả năng minh họa**, không phải quy luật cố định của TCP hay của một JVM.

Điều này cũng đúng khi người gửi gom tất cả byte vào **một** lần `write()`: một lần `read()` có thể trả về ít byte hơn. Ngược lại, nếu gửi bằng nhiều lần `write()`, một lần `read()` có thể trả về byte từ nhiều lời gọi ghi.

### Phân biệt các lớp

| Khái niệm | Ai xác định? | Có phải ranh giới bản tin ứng dụng không? |
| --- | --- | --- |
| TCP segment | TCP stack / đường truyền | **Không** |
| Các lần gọi `SocketChannel.write(...)` | Mã phía gửi | **Không** |
| Các lần gọi `SocketChannel.read(...)` | Mã phía nhận, dữ liệu sẵn có và trạng thái channel | **Không** |
| Frame của giao thức ứng dụng | Thiết kế giao thức của bạn (hoặc HTTP, WebSocket, v.v.) | **Có**, nếu giao thức định nghĩa đúng |
| Message nghiệp vụ | Ứng dụng | Có thể nằm trong một frame hoặc được ghép từ nhiều frame theo giao thức |

Không nên suy luận “mỗi TCP packet là một message”. Ứng dụng Java thông thường nhận **byte trên socket**, không nhận trực tiếp một danh sách các TCP segment để ghép theo packet.

> **Quy tắc:** Bên nhận phải tự xác định bản tin kết thúc ở đâu. Hãy áp dụng **message framing**, đừng đoán từ số lần `read()`.

## 3. Tại sao dữ liệu có thể bị chia nhỏ hoặc gộp lại?

Giả sử client gửi hai lệnh:

~~~text
LOGIN alice
PING
~~~

Nếu không có dấu kết thúc lệnh hay trường độ dài, bên nhận chỉ thấy chuỗi byte `LOGIN alicePING` và **không có cách tổng quát nào biết phải tách ở đâu**.

Ngay cả khi client gọi `write()` tách biệt hai lần, TCP và hệ điều hành vẫn có thể chia/gộp dữ liệu theo nhiều yếu tố:

1. **Kích thước buffer của ứng dụng:** nếu buffer nhận chỉ còn 4 byte, `read()` không thể đặt 10 byte vào đó.
2. **Thời điểm đọc:** dữ liệu từ nhiều lần ghi có thể đã đến trước một lần đọc.
3. **Phân đoạn/truyền lại TCP:** cách dữ liệu được đưa qua mạng không phải giao diện bản tin cho ứng dụng.
4. **Socket không chặn (non-blocking):** lần đọc hiện tại có thể chỉ nhận một phần, hoặc chưa có byte nào.
5. **Áp lực luồng (backpressure):** phía nhận/đích chậm khiến số byte chấp nhận trong một lần ghi nhỏ hơn yêu cầu.

Tên gọi hay gặp là “sticky packets” hoặc “packet fragmentation” ở tầng ứng dụng; cách hiểu chính xác hơn là **ứng dụng chưa thiết kế hoặc chưa xử lý framing trên một byte stream**.

TCP không làm thất lạc **ranh giới** mà nó từng hứa giữ: ngay từ đầu TCP **không hứa giữ ranh giới thông điệp**.

## 4. Đọc và ghi với `SocketChannel`: phải kiểm tra số byte thực tế

Trong Java NIO:

~~~java
int readBytes = socketChannel.read(readBuffer);
int writtenBytes = socketChannel.write(writeBuffer);
~~~

Hai lời gọi trả về số byte **thực tế** được đọc/ghi trong lần thực hiện, không nhất thiết bằng `ByteBuffer.remaining()` trước đó.

### Với `read(ByteBuffer)`

- Giá trị **dương**: đã đọc được số byte tương ứng, buffer tiến `position` đúng số byte.
- Giá trị **0**: lần này không đọc byte nào. Với channel **non-blocking**, thông thường là chưa có dữ liệu đọc được ngay; cũng có thể xảy ra khi buffer không có chỗ trống.
- Giá trị **-1**: đã gặp **end-of-stream (EOF)**, tức phía bên kia đã kết thúc chiều gửi dữ liệu và các byte còn được xếp hàng đã được tiêu thụ.
- Phát sinh `IOException`: lỗi I/O, đóng channel, reset kết nối hoặc điều kiện khác; không đồng nhất mọi lỗi với EOF bình thường.

Trong chế độ **blocking**, nếu buffer còn chỗ, lệnh đọc thường đợi đến khi có ít nhất một byte, EOF hoặc lỗi; **nó không đợi cho đủ cả bản tin ứng dụng**. Một lời gọi đọc thành công có thể chỉ trả về 1 byte.

### Với `write(ByteBuffer)`

- Giá trị **dương**: một phần hoặc toàn bộ số byte còn lại trong buffer đã được chấp nhận vào channel; `position` buffer tiến tương ứng.
- Giá trị **0**: lần gọi không ghi được byte nào, đặc biệt có thể gặp trong non-blocking. Không suy ra rằng peer đã nhận xong dữ liệu.
- Lần gọi `write()` trả về thành công **không bảo đảm phía đối tác đã đọc/xử lý** byte đó ở tầng ứng dụng.

Ví dụ vòng lặp cơ bản trong chế độ blocking:

~~~java
while (buffer.hasRemaining()) {
    int n = channel.write(buffer);
    if (n == 0) {
        throw new IOException("Socket ghi không tiến triển");
    }
}
~~~

Đoạn này chỉ minh họa, cần `channel` là `SocketChannel` **blocking**, `buffer` đã `flip()` để sẵn sàng đọc ra và import `java.io.IOException`. Trong non-blocking, nếu `write()` trả 0 thì **đăng ký chờ khả năng ghi** bằng `Selector` hoặc thiết kế sự kiện tương ứng, không quay vòng liên tục trên CPU.

## 5. Ba cách thiết kế ranh giới bản tin

### 5.1. Length-prefix framing: đặt độ dài ở đầu

Mỗi frame chứa độ dài payload trước, sau đó là đúng số byte payload. Ví dụ dùng một số nguyên 32-bit (4 byte) **big-endian**, không dấu ở cấp ý nghĩa giao thức nhưng khi đọc bằng Java `int` phải xác thực miền hợp lệ:

~~~text
[ 00 00 00 05 ][ H E L L O ][ 00 00 00 04 ][ J A V A ]
      4 byte        5 byte          4 byte       4 byte
      length=5                      length=4
~~~

Bên nhận làm theo các bước:

1. Đọc **đủ 4 byte** header, dù cần nhiều lần `read()`.
2. Giải mã độ dài theo endian đã thỏa thuận; từ chối số âm, quá lớn hoặc vượt giới hạn giao thức.
3. Đọc **đủ đúng số byte payload** đã công bố.
4. Chỉ khi đủ byte mới giải mã payload và xử lý một message.
5. Lặp lại bước 1 cho frame tiếp theo, kể cả khi các byte frame tiếp theo đã nằm sẵn trong buffer nhận.

**Ưu điểm:** payload có thể chứa byte bất kỳ, kể cả newline hoặc byte `0`; dễ kiểm tra tính đầy đủ. **Hạn chế:** cần quản lý trạng thái header/payload khi dữ liệu đến từng phần, phải giới hạn độ dài trước khi cấp phát bộ nhớ.

Một giao thức production nên chỉ rõ độ rộng header, endian, kích thước tối đa, encoding payload, phiên bản, xử lý frame lỗi và chính sách timeout.

### 5.2. Delimiter framing: dấu phân cách

Ví dụ lệnh văn bản kết thúc bằng LF (`\n`):

~~~text
PING\n
LOGIN alice\n
~~~

Phía nhận tích lũy byte đến dấu LF rồi tách message. Đây là lựa chọn đơn giản cho các giao thức line-oriented.

**Cẩn trọng:**

- Dấu phân cách có thể nằm trong payload; phải định nghĩa escaping/quoting hoặc cấm nó trong nội dung.
- Khi dùng UTF-8, phải bảo đảm giải mã đúng cả khi một ký tự nhiều byte bị chia qua các lần đọc; thường nên tách/ghép **byte** trước, rồi giải mã trọn message hoặc dùng `CharsetDecoder` có trạng thái.
- Phải giới hạn chiều dài dòng. Nếu peer gửi vô hạn mà không gửi `\n`, server không được tích lũy buffer vô hạn.
- Quy định rõ `\n` hay `\r\n`, cũng như cách xử lý dòng cuối kết thúc bằng EOF.

### 5.3. Fixed-size framing: mỗi frame có kích thước cố định

Nếu mọi bản ghi đều dài đúng 32 byte, bên nhận cứ tích đủ 32 byte rồi xử lý. Cách này phù hợp với giao thức nhị phân có schema cố định.

Nhược điểm: dữ liệu biến độ dài cần padding hoặc trường xác định chiều dài; không thể tự suy đoán bản ghi ngắn hơn. **Vẫn phải lặp đọc** đến khi đủ 32 byte, vì mỗi `read()` có thể trả về ít hơn.

### Phương án nào nên chọn?

| Giao thức | Ưu điểm | Điểm cần chú ý |
| --- | --- | --- |
| Length-prefix | Rõ kích thước; hỗ trợ payload nhị phân tùy ý | Kiểm tra giới hạn, endian, trạng thái header/body |
| Delimiter | Dễ đọc/debug với văn bản | Escaping, giới hạn chiều dài, encoding |
| Fixed-size | Đơn giản với record đồng nhất | Lãng phí padding, khó biểu diễn payload dài biến đổi |
| Kết thúc bởi EOF | Có thể phù hợp cho **một payload cuối cùng** trên kết nối | Không dùng để phân biệt nhiều message trên kết nối tiếp tục mở |

Tránh dùng **thời gian chờ**, `available()`, kích thước buffer hoặc số lần `read()` để đoán message đã hoàn tất. Những dữ kiện đó không xác định ranh giới giao thức.

## 6. Ví dụ Java 17+ hoàn chỉnh: hai bản tin trong một luồng TCP

Ví dụ sau tạo một TCP server trên loopback với cổng do hệ điều hành cấp, một client gửi liên tiếp hai frame, rồi server đọc đúng hai message theo length-prefix.

Để người học thấy rõ một message có thể cần **nhiều lần đọc**, hàm `readExactly` cố ý giới hạn mỗi lần đọc tối đa **3 byte** bằng `ByteBuffer.slice()`. Đây là giới hạn **do ví dụ chủ động đặt ra**, không phải hành vi phân đoạn mạng được TCP bảo đảm.

Lưu tệp **`TcpMessageFramingDemo.java`**:

~~~java
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.FutureTask;

public class TcpMessageFramingDemo {
    private static final int MAX_FRAME_BYTES = 1024;
    private static final int READ_CHUNK = 3;

    public static void main(String[] args) throws Exception {
        try (ServerSocketChannel listener = ServerSocketChannel.open()) {
            listener.bind(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));

            FutureTask<List<String>> received = new FutureTask<>(() -> {
                try (SocketChannel peer = listener.accept()) {
                    return List.of(readFrame(peer), readFrame(peer));
                }
            });
            Thread serverThread = new Thread(received, "tcp-frame-reader");
            serverThread.start();

            try (SocketChannel client = SocketChannel.open()) {
                client.connect((InetSocketAddress) listener.getLocalAddress());

                byte[] first = "HELLO".getBytes(StandardCharsets.UTF_8);
                byte[] second = "JAVA".getBytes(StandardCharsets.UTF_8);

                // Hai frame liên tiếp trong MỘT ByteBuffer của ứng dụng.
                ByteBuffer outgoing = ByteBuffer.allocate(
                        Integer.BYTES + first.length
                                + Integer.BYTES + second.length);
                outgoing.putInt(first.length).put(first);
                outgoing.putInt(second.length).put(second);
                outgoing.flip();

                // Một lời gọi write không được giả định sẽ ghi hết buffer.
                while (outgoing.hasRemaining()) {
                    int written = client.write(outgoing);
                    if (written == 0) {
                        throw new IOException("Blocking write không tiến triển");
                    }
                }
            }

            List<String> messages = received.get();
            System.out.println("Message 1: " + messages.get(0));
            System.out.println("Message 2: " + messages.get(1));
            System.out.println("Frame count: " + messages.size());
        }
    }

    private static String readFrame(SocketChannel channel)
            throws IOException {
        ByteBuffer header = ByteBuffer.allocate(Integer.BYTES);
        readExactly(channel, header);
        header.flip();

        int byteLength = header.getInt();
        if (byteLength < 0 || byteLength > MAX_FRAME_BYTES) {
            throw new IOException("Invalid frame length: " + byteLength);
        }

        ByteBuffer payload = ByteBuffer.allocate(byteLength);
        readExactly(channel, payload);
        payload.flip();
        return StandardCharsets.UTF_8.decode(payload).toString();
    }

    private static void readExactly(SocketChannel channel, ByteBuffer dst)
            throws IOException {
        while (dst.hasRemaining()) {
            ByteBuffer step = dst.slice();
            step.limit(Math.min(step.remaining(), READ_CHUNK));

            int n = channel.read(step);
            if (n == -1) {
                throw new EOFException(
                        "EOF giữa frame, còn thiếu "
                                + dst.remaining() + " byte");
            }
            if (n == 0) {
                // Demo dùng SocketChannel blocking và buffer còn chỗ.
                // Non-blocking cần Selector hoặc cơ chế chờ tương ứng.
                throw new IOException("Blocking read không tiến triển");
            }

            // Vì đọc qua slice, tự cập nhật vị trí của buffer gốc.
            dst.position(dst.position() + n);
        }
    }
}
~~~

Biên dịch/chạy từ thư mục chứa file bằng **JDK 17+**:

~~~bash
javac -encoding UTF-8 TcpMessageFramingDemo.java
java TcpMessageFramingDemo
~~~

**Stdout** khi kết nối loopback hoạt động bình thường:

~~~text
Message 1: HELLO
Message 2: JAVA
Frame count: 2
~~~

### Giải thích từng điểm quan trọng

1. `listener.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))` chỉ lắng nghe loopback, cổng `0` yêu cầu hệ điều hành chọn cổng sẵn có. Chương trình lấy chính địa chỉ vừa bind, nên stdout không phụ thuộc số cổng.
2. Server và client dùng `SocketChannel` **blocking mặc định**. Thread server đợi `accept()` rồi đọc hai frame. Thread chính gửi đủ hai frame và đợi kết quả bằng `FutureTask.get()`.
3. `outgoing.putInt(first.length)` đặt header 4 byte với byte order mặc định **big-endian**, sau đó đặt payload. Tương tự cho frame thứ hai. Hai frame nằm sát nhau trong 17 byte dữ liệu của ứng dụng.
4. `outgoing.flip()` chuyển buffer từ chế độ **ghi byte vào buffer** sang chế độ **đọc byte từ buffer để gửi ra socket**. Vòng `while (outgoing.hasRemaining())` xử lý ghi từng phần.
5. `readFrame` phải thu đủ 4 byte trước khi gọi `getInt()`, rồi thu đúng `byteLength` byte payload. Không suy đoán rằng vì client đã gửi hai frame liền nhau thì server sẽ nhận cả hai trong một lần `read()`.
6. `readExactly` đặt `step.limit(...)` tối đa 3 byte. Vì vậy header 4 byte và payload 5 byte chắc chắn cần **nhiều lần gọi `read()`** ngay cả nếu mạng đã cung cấp sẵn tất cả dữ liệu. `step` là một `slice` dùng chung nội dung bộ nhớ với `dst` nhưng có vị trí độc lập, nên phải cập nhật `dst.position()` sau mỗi lần đọc.
7. Nếu peer đóng kết nối trước khi gửi đủ header hoặc payload, `readExactly` nhận `-1` và ném `EOFException`; phần message bị thiếu **không** được xử lý như message hoàn chỉnh.
8. Giới hạn `MAX_FRAME_BYTES` được kiểm tra **trước khi cấp phát** buffer payload; điều này ngăn header giả mạo yêu cầu cấp phát tùy ý lớn.
9. `UTF_8.decode(...)` chỉ chạy sau khi đã có đủ payload. Ví dụ dùng dữ liệu ASCII trong UTF-8 nên không gặp ký tự bị chia giữa các lần đọc.

**Ý nghĩa của ví dụ:** Hai message được gửi liền nhau trên **một luồng TCP**, trong khi việc phân chia thành hai message ở bên nhận chỉ dựa trên **header độ dài**. Kích thước các lần đọc không quyết định số message.

## 7. Mẫu tích lũy dữ liệu với `ByteBuffer` trong ứng dụng non-blocking

Ở chế độ non-blocking (`channel.configureBlocking(false)`), `read()` có thể trả về 0 do hiện không có dữ liệu. Không được áp dụng nguyên xi `readExactly` dạng blocking phía trên để **ngồi chờ** một frame; thay vào đó cần giữ trạng thái parse giữa các sự kiện đọc.

Vòng xử lý thường có ý tưởng:

1. Khi `Selector` báo kênh có khả năng đọc, gọi `channel.read(buffer)`; kết quả có thể dương, 0 hoặc -1.
2. Chuyển buffer sang trạng thái đọc bằng `flip()`.
3. Trích xuất **những frame hoàn chỉnh** hiện đã tích đủ. Nếu chưa đủ 4 byte header hoặc chưa đủ payload, giữ lại phần chưa xử lý.
4. Gọi `compact()` để dồn các byte chưa đọc về đầu buffer, tạo chỗ nhận thêm dữ liệu. Khi đã tiêu thụ toàn bộ, `clear()` cũng có thể phù hợp.
5. Chờ sự kiện đọc tiếp theo. Nếu `read() == -1` khi còn frame dang dở, báo lỗi giao thức kết thúc sớm.

Ví dụ vòng đời ByteBuffer:

~~~text
Mới tạo/clear:     position=0, limit=capacity   (chuẩn bị ghi dữ liệu vào)
Đọc socket:        channel.read(buffer)          (position tăng theo byte nhận)
flip:              limit=position, position=0    (chuẩn bị parse)
Parse một phần:    position tăng theo byte đã tiêu thụ
compact:           giữ lại byte chưa parse       (chuẩn bị đọc socket tiếp)
~~~

**Không nhầm `clear()` với xóa dữ liệu bộ nhớ:** `clear()` chỉ đặt lại chỉ số vị trí và giới hạn; nếu còn byte chưa xử lý thì gọi `clear()` có thể khiến chúng bị ghi đè. `compact()` mới phù hợp khi cần giữ lại dữ liệu chưa parse.

### Một lần đọc có thể tạo 0, 1 hoặc nhiều frame

Parser đúng phải xử lý cả ba tình huống:

- **0 frame đầy đủ:** chỉ nhận một phần header hoặc một phần payload; chờ thêm byte.
- **1 frame đầy đủ:** phát ra đúng một message, giữ phần dư nếu có.
- **Nhiều frame đầy đủ:** tiếp tục parse trong buffer hiện có đến khi thiếu dữ liệu cho frame tiếp theo.

Ví dụ, buffer hiện có thể chứa:

~~~text
[length=3][CAT][length=3][DOG][length=5][HE...
~~~

Parser phát ra `CAT` và `DOG`, đồng thời **giữ lại header của frame thứ ba và hai byte `HE`**. Lần đọc tiếp theo bổ sung `LLO` mới cho phép xử lý message `HELLO`.

Khi frame có thể lớn hơn buffer đọc socket, parser phải có trạng thái độc lập: đọc header, xác thực chiều dài, cấp phát/thu thập payload có giới hạn, rồi xử lý. Không giả định một buffer socket cố định phải đủ chứa mọi frame.

### Gửi non-blocking cũng có trạng thái dở dang

Nếu `channel.write(outgoing)` chỉ ghi 7 trong 20 byte, **giữ nguyên buffer với 13 byte còn lại**. Khi khả năng ghi sẵn sàng, tiếp tục từ `position` hiện tại. Nếu trả 0, đăng ký quan tâm `SelectionKey.OP_WRITE` khi còn dữ liệu chờ, và vô hiệu hóa quan tâm ghi khi hàng đợi đã hết để tránh vòng lặp sự kiện ghi không cần thiết.

Phải định nghĩa chính sách **backpressure**: giới hạn hàng đợi gửi, tạm ngừng đọc thêm đầu vào hoặc từ chối yêu cầu khi peer đọc quá chậm. TCP không tự quyết định giới hạn bộ nhớ cho queue thông điệp của ứng dụng.

## 8. UTF-8 và ranh giới bản tin không phải ranh giới ký tự

Một ký tự UTF-8 có thể chiếm 1–4 byte. Giả sử ký tự `Đ` được mã hóa thành nhiều byte; nếu một lần `read()` nhận byte đầu, lần sau nhận byte còn lại, giải mã từng chunk độc lập có thể tạo ký tự lỗi hoặc ký tự thay thế.

**Quy trình an toàn cho giao thức length-prefix:**

1. Đếm độ dài payload theo **byte UTF-8** (`text.getBytes(StandardCharsets.UTF_8).length`), không theo `String.length()`.
2. Gửi header là số **byte**, sau đó gửi đúng byte của payload.
3. Bên nhận thu đủ byte trước khi giải mã toàn bộ payload, hoặc dùng `CharsetDecoder` giữ trạng thái qua nhiều chunk.
4. Chỉ xử lý message sau khi nội dung hợp lệ theo schema.

Ví dụ chuỗi `"Xin chào"` có số byte UTF-8 **lớn hơn** số ký tự mà người đọc đếm. Nếu header lấy `text.length()` thay vì `encoded.length`, phía nhận sẽ cắt ngang payload, làm hỏng framing cho cả các message tiếp theo.

`ByteBuffer.order(ByteOrder.BIG_ENDIAN)` hoặc mặc định big-endian ảnh hưởng cách đọc số nguyên header. Endian của header **khác** với encoding UTF-8 của body; cần định nghĩa rõ cả hai.

Khi dữ liệu đến từ bên không tin cậy, `StandardCharsets.UTF_8.decode` có thể thay thế chuỗi byte UTF-8 sai định dạng theo chính sách mặc định. Nếu yêu cầu từ chối dữ liệu sai, dùng `CharsetDecoder` với `CodingErrorAction.REPORT` và bắt lỗi giải mã, không âm thầm nhận thông điệp bị lỗi.

## 9. EOF, half-close, đóng kết nối và timeout

### `read() == -1` nghĩa là gì?

`-1` báo rằng **đầu vào từ phía peer đã kết thúc** và không còn byte dữ liệu để nhận tiếp trên chiều đó. Đối với giao thức length-prefix:

- Nếu đã đọc đủ một frame rồi sau đó nhận `-1` đúng ở vị trí bắt đầu frame mới, có thể coi đây là kết thúc luồng hợp lệ **nếu giao thức cho phép đóng tại ranh giới frame**.
- Nếu mới đọc 2/4 byte header hoặc 7/10 byte payload, `-1` là **frame bị cắt cụt**, phải báo lỗi.
- Nếu giao thức yêu cầu các frame bắt buộc hoặc số lượng frame cụ thể, đóng ở ranh giới vẫn có thể là lỗi nghiệp vụ.

TCP còn hỗ trợ **half-close**: phía gửi có thể kết thúc chiều gửi bằng `SocketChannel.shutdownOutput()` nhưng vẫn đọc phản hồi theo chiều ngược lại. Vì vậy, EOF của chiều nhận không nhất thiết có nghĩa cả hai phía đã đóng hoàn toàn cùng lúc.

### Không dùng khoảng im lặng để kết luận hết message

Nếu server chưa nhận thêm byte trong 50 ms, điều đó **không chứng minh** frame đã kết thúc. Độ trễ mạng, scheduling, congestion hoặc tải của peer đều có thể khiến dữ liệu đến sau.

**Timeout** là công cụ xử lý kết nối chậm/bị treo theo chính sách vận hành; nó **không phải message delimiter**. Nếu hết timeout giữa frame, quyết định hủy frame/kết nối hoặc retry theo giao thức cụ thể.

### Một lần `write()` thành công không phải xác nhận xử lý

Việc Java trả về đã ghi N byte thường chỉ có nghĩa channel đã chấp nhận N byte ở phía gửi. Nếu cần biết peer đã **xử lý nghiệp vụ thành công**, giao thức phải có ACK/response với định danh request. TCP cũng không tự bảo đảm “exactly once” cho thông điệp qua các lần ngắt và kết nối lại; nếu client retry, server cần logic idempotency/deduplication khi nghiệp vụ yêu cầu.

## 10. Các kiểm tra bảo mật và độ bền của frame

Giao thức framing triển khai không đúng dễ thành điểm yếu của server:

| Nguy cơ | Ví dụ | Cách kiểm soát |
| --- | --- | --- |
| Header độ dài giả mạo | Peer gửi length `2_000_000_000` | Giới hạn `MAX_FRAME_BYTES` trước khi cấp phát |
| Header số âm | `getInt()` trả về `-1` | Từ chối giá trị âm và trường hợp vượt giới hạn |
| Gửi dở frame rồi giữ kết nối | Mới có 1/4 byte header | Timeout, quota kết nối, giới hạn thời gian nhận |
| Dòng delimiter dài vô tận | Peer không gửi `\n` | Giới hạn độ dài message và cắt kết nối vi phạm |
| Giải mã sớm | Chunk chứa nửa ký tự UTF-8 | Ghép đủ byte hoặc dùng decoder có trạng thái |
| Tích lũy không giới hạn | Nhiều frame đến nhanh hơn tốc độ xử lý | Backpressure, queue bounded, giới hạn frame đang chờ |
| Nhiều request trên một kết nối | Payload nối liên tiếp | Parser xử lý nhiều frame và lưu phần dư |
| Peer ngắt giữa frame | `read() == -1` khi còn thiếu payload | Phát hiện truncated frame, không xử lý dữ liệu thiếu |
| Client gửi mãi mà không nhận | `write()` trả 0 liên tục trong non-blocking | Chờ `OP_WRITE`, quản lý timeout và queue |
| Tin rằng đã ghi là peer hoàn tất | Mất kết nối sau `write` | Xác nhận tầng ứng dụng, request ID/idempotency khi cần |

### Giới hạn kích thước trước khi tạo đối tượng

Nếu header là `int`, kiểm tra ngay:

~~~java
int length = header.getInt();
if (length < 0 || length > MAX_FRAME_BYTES) {
    throw new IOException("Invalid length: " + length);
}
ByteBuffer payload = ByteBuffer.allocate(length);
~~~

Đây là **đoạn minh họa**, cần `header` đã đủ 4 byte và đang ở chế độ đọc, `MAX_FRAME_BYTES` là giới hạn cấu hình hợp lệ, import `ByteBuffer` và `IOException`. Trên đường đọc nhiều frame, cũng cần giới hạn **tổng** bộ nhớ và số lượng kết nối, không chỉ từng frame riêng lẻ.

## 11. Các lỗi phổ biến khi lập trình Java NIO mạng

**Lỗi 1 — Một lần đọc là một message.**

~~~java
int n = channel.read(buffer);
// Sai: xử lý ngay n byte như một message hoàn chỉnh.
~~~

`n` là số byte nhận trong lần đọc, không phải độ dài bản tin. Hãy parser theo giao thức framing.

**Lỗi 2 — Một lần ghi chắc chắn gửi hết.**

~~~java
channel.write(buffer);
// Sai: bỏ qua buffer.hasRemaining() và phần chưa ghi.
~~~

Phải giữ buffer và ghi tiếp; với non-blocking, đợi khi writable.

**Lỗi 3 — Không giữ header/payload dở dang.** Chỉ đọc được 2 byte trong header 4 byte rồi bỏ đi sẽ khiến 2 byte tiếp theo bị diễn giải thành đầu một header mới sai lệch.

**Lỗi 4 — Chỉ parse một frame khi buffer chứa nhiều frame.** Dẫn đến độ trễ không cần thiết hoặc mất byte thừa nếu buffer bị `clear()` trước khi xử lý phần dư.

**Lỗi 5 — Dùng `read() == 0` làm EOF.** `0` là không có byte trong lần đọc; `-1` mới là EOF. Đối với non-blocking, cách xử lý `0` là chờ sự kiện thích hợp.

**Lỗi 6 — Dùng `String.length()` làm số byte UTF-8.** Dễ cắt ngang ký tự nhiều byte và làm lệch cả luồng frame kế tiếp.

**Lỗi 7 — Bỏ qua giới hạn frame.** Peer có thể gửi header yêu cầu cấp phát khổng lồ, gây quá tải hoặc hết bộ nhớ.

**Lỗi 8 — Gọi `clear()` khi vẫn còn byte chưa parse.** Buffer mất thông tin về phần dư; `compact()` phù hợp hơn nếu còn dữ liệu phải giữ lại.

**Lỗi 9 — Dựa vào TCP_NODELAY hoặc quy mô packet để tạo ranh giới.** `TCP_NODELAY` ảnh hưởng một số hành vi gom byte/latenсy, **không biến TCP thành message-oriented transport**. Framing vẫn bắt buộc.

**Lỗi 10 — Cho rằng EOF luôn là kết thúc hợp lệ của frame.** Nếu header đã hứa 100 byte mà mới nhận 60, đây là truncated message và cần báo lỗi.

## 12. Câu hỏi tự kiểm tra

**Câu 1.** Client gọi `write("HELLO")` rồi `write("JAVA")`. Server gọi `read()` một lần nhận được `"HELLOJAVA"` có đúng không?

**Đáp án:** Có. TCP là byte stream; hai lần ghi có thể xuất hiện chung trong một lần đọc.

**Câu 2.** Một `read()` trả về 3 byte của header dài 4 byte. Bên nhận nên làm gì?

**Đáp án:** Giữ 3 byte đã nhận, đọc thêm 1 byte để đủ header, rồi mới giải mã length. Không xử lý 3 byte đó thành một message.

**Câu 3.** Header yêu cầu payload 30 byte, nhưng lần đọc đầu chỉ nhận 8 byte. Có phải dữ liệu bị lỗi?

**Đáp án:** Chưa. Đây là tình huống đọc từng phần bình thường; tiếp tục tích lũy 22 byte còn lại. Nếu peer đóng trước khi đủ thì mới kết luận frame bị cắt cụt.

**Câu 4.** `read()` trả về 0 trên `SocketChannel` non-blocking. Có được đóng kết nối vì EOF không?

**Đáp án:** Không. `0` chỉ có nghĩa lần gọi chưa đọc được byte; thường cần đợi `Selector` báo sẵn sàng đọc.

**Câu 5.** `write()` của buffer 1.024 byte trả về 600. Cần gửi bao nhiêu byte tiếp theo?

**Đáp án:** Còn 424 byte chưa ghi. `ByteBuffer.position()` đã tăng theo lượng ghi, nên tiếp tục `write` với **chính buffer đó** khi channel cho phép.

**Câu 6.** Có thể gửi UTF-8 `"Xin chào"` với header bằng `String.length()` không?

**Đáp án:** Không nếu header là số byte payload; phải mã hóa UTF-8 rồi dùng `encoded.length`.

**Câu 7.** Khi đã nhận đủ 2 frame và còn 2 byte đầu header frame thứ ba trong buffer, có thể gọi `clear()` không?

**Đáp án:** Không nên vì sẽ bỏ mất dữ liệu header còn dở. Giữ phần dư bằng `compact()` hoặc cơ chế buffer tương đương.

**Câu 8.** Nếu ứng dụng cần phân biệt các lệnh ngay trong một kết nối TCP lâu dài, có thể dùng EOF để ngăn cách chúng không?

**Đáp án:** Không. EOF chỉ báo kết thúc chiều gửi; dùng length-prefix, delimiter có quy tắc, hoặc fixed-size framing để phân biệt nhiều lệnh.

## 13. Ghi nhớ

- **TCP = byte stream có thứ tự**, **không có ranh giới message do TCP bảo toàn**.
- **`write()` không tương ứng 1:1 với `read()`**. Một message có thể đi qua nhiều lần đọc; một lần đọc có thể chứa nhiều message.
- **Framing là trách nhiệm của giao thức ứng dụng**: length-prefix, delimiter hoặc fixed-size; không đoán bằng packet, buffer hay thời gian.
- Đọc/ghi Java NIO phải xét **số byte thực tế**; `read() == -1` là EOF, `0` không phải EOF; non-blocking phải giữ trạng thái và đợi readiness.
- Dùng `flip()`/`compact()` đúng, giữ phần dữ liệu dở dang, kiểm tra độ dài trước khi cấp phát, và chỉ giải mã UTF-8 sau khi đã xử lý đúng ranh giới byte.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nio-networking-005">Thiết kế framing bằng độ dài hoặc dấu phân cách</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế framing bằng độ dài hoặc dấu phân cách trong Java NIO mạng

## 1. Vì sao TCP cần framing?

`SocketChannel` trong Java NIO truyền và nhận **luồng byte** TCP. TCP không bảo toàn ranh giới thông điệp của ứng dụng: một lần `write()` có thể được đọc thành nhiều lần `read()`, và một lần `read()` có thể chứa byte của nhiều lần `write()`.

Giả sử ứng dụng gửi hai thông điệp `"HELLO"` và `"WORLD"`. Receiver có thể nhận các chunk `"HEL"`, `"LOWOR"`, `"LD"`, hoặc nhận toàn bộ `"HELLOWORLD"` trong một lần. Không cách phân chia nào sai theo hợp đồng TCP. Vì thế chương trình **không thể** dựa vào mỗi lần `read()` để xác định một thông điệp.

**Message framing** là một định dạng ở tầng ứng dụng giúp phân định từng thông điệp (frame) trên luồng byte. Hai cách phổ biến là:

- **Length-prefix**: đặt thông tin độ dài ở header, sau đó gửi đúng số byte payload.
- **Delimiter-based**: kết thúc từng payload bằng một chuỗi byte đặc biệt, ví dụ LF (`0x0A`) hoặc CRLF (`0x0D 0x0A`).

Bài học thuộc Java Core I/O → **NIO mạng**, giải thích thiết kế wire format, state machine xử lý `ByteBuffer`, partial read/write, EOF, giới hạn bộ nhớ, kiểm thử và cách chọn kỹ thuật.

## 2. Phân biệt các tầng khi truyền dữ liệu

| Thuật ngữ | Vai trò |
| --- | --- |
| Payload | Nội dung ứng dụng như chuỗi UTF-8, JSON, byte nhị phân |
| Frame | Payload và thông tin xác định ranh giới (header độ dài hoặc delimiter) |
| TCP stream | Chuỗi byte được chuyển theo thứ tự, không có ranh giới message |
| `SocketChannel.read(ByteBuffer)` | Chuyển một lượng byte từ socket vào buffer tại lần gọi này |
| `ByteBuffer` | Bộ nhớ và con trỏ `position`, `limit`, `capacity` |
| TCP segment | Đơn vị đóng gói của tầng mạng, không đồng nhất với frame ứng dụng |

Ngay cả khi thử nghiệm trên localhost cho cảm giác “một write bằng một read”, ứng dụng phải xử lý chia nhỏ, ghép lại và thay đổi thời điểm nhận dữ liệu. Giải pháp là **decoder giữ trạng thái giữa các lần nhận byte**.

## 3. Framing bằng header độ dài

Một giao thức đơn giản dùng **4 byte đầu là số nguyên có dấu, big-endian** chứa độ dài tính bằng **byte của payload**, theo sau là đúng lượng byte đó:

```text
+------------------------------+-----------------------+
| length: 4 bytes (big-endian) | payload: length bytes |
+------------------------------+-----------------------+

"CAT" (3 byte UTF-8):
  00 00 00 03 | 43 41 54

"HI" (2 byte UTF-8):
  00 00 00 02 | 48 49
```

Các đặc điểm cần ghi rõ trong tài liệu giao thức:

1. Header có kích thước bao nhiêu byte; ví dụ này là 4.
2. Header đếm **chỉ payload** hay cả header; ví dụ này chỉ đếm payload.
3. Endianness; ví dụ dùng `ByteOrder.BIG_ENDIAN`.
4. Số âm hoặc length vượt giới hạn bị từ chối; `length=0` có thể đại diện frame rỗng.
5. Payload mã hóa UTF-8, byte nhị phân hay một schema khác.
6. Chính sách EOF giữa header hoặc giữa payload, timeout, compression và giới hạn tổng tài nguyên.

Với UTF-8, **độ dài ký tự Java khác số byte trên wire**: `String.length()` không phải giá trị để điền vào header. Luôn mã hóa trước rồi dùng `payload.getBytes(UTF_8).length`.

### 3.1. Encoder (đoạn mã trong một lớp Java)

```java
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

static ByteBuffer encode(String message) {
    byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
    if (bytes.length > 1024) {
        throw new IllegalArgumentException("Payload too large");
    }
    ByteBuffer frame = ByteBuffer.allocate(4 + bytes.length);
    frame.order(ByteOrder.BIG_ENDIAN);
    frame.putInt(bytes.length);
    frame.put(bytes);
    frame.flip();
    return frame;
}
```

Code này là **phương thức minh họa**, cần đặt trong một class. Sau `flip()`, `position=0`, `limit=4+bytes.length` để `SocketChannel.write(frame)` bắt đầu chuyển byte từ header. Nếu quên `flip()`, channel có thể không gửi byte mới nào.

Độ dài 4 byte là một `int` có dấu ở Java. Nếu muốn hỗ trợ unsigned length hoặc varint, phải đặc tả rõ cách giải mã và kiểm tra tràn. Không ngầm coi mọi tổ hợp 4 byte đều là độ dài được cấp phát hợp lệ.

## 4. Decoder độ dài là máy trạng thái

Với length-prefix, decoder có hai pha:

```text
WAIT_HEADER (tích lũy đủ 4 byte)
             |
             v
     kiểm tra 0 <= N <= MAX
             |
             +-- N = 0 ------> emit frame rỗng ----+
             |                                     |
             v                                     |
WAIT_BODY (tích lũy đúng N byte)                   |
             |                                     |
             +---- emit payload --------------------+
             |
             v
         WAIT_HEADER tiếp
```

Không được giả định header 4 byte đến trong một `read()`. Ví dụ kết quả một lần `read` có thể mới chứa 1 byte header, lần kế có 3 byte, rồi lần tiếp theo mới chứa payload. Nếu đọc `getInt()` khi chưa đủ 4 byte, có thể ném `BufferUnderflowException`. Tương tự, một lần `read` không bảo đảm nhận được toàn bộ `N` byte payload.

Sau khi emit một frame, decoder phải **tiếp tục xử lý các byte còn lại trong cùng chunk**: một `read()` có thể chứa nhiều frame hoàn chỉnh và một phần frame kế tiếp.

Ví dụ hai frame nối tiếp:

```text
Frame "Hi":  00 00 00 02  48 69
Frame "NIO": 00 00 00 03  4E 49 4F

Luồng: [00 00 00 02 48 69 00 00 00 03 4E 49 4F]

Chunk A [00 00 00]            -> thiếu header 1 byte
Chunk B [02 48 69 00 00]      -> emit "Hi", giữ 2 byte đầu header thứ hai
Chunk C [00 03 4E 49 4F]      -> emit "NIO"
```

Decoder phải có trạng thái bền theo **từng kết nối TCP**: số byte header đã nhận, độ dài đã validate, số byte payload còn thiếu. Nếu dùng chung một state giữa hai socket độc lập, byte của hai kết nối có thể bị nối sai thành một thông điệp.

### 4.1. Giới hạn kích thước trước khi cấp phát

```java
int length = header.getInt(); // header đã có đủ 4 byte
if (length < 0 || length > MAX_PAYLOAD_BYTES) {
    throw new IllegalArgumentException("Invalid length: " + length);
}
```

Đây là code minh họa, cần một `header` và `MAX_PAYLOAD_BYTES` phù hợp. Đầu vào từ mạng **không đáng tin để cấp phát trực tiếp**. Không được `new byte[length]` trước khi xác minh length; một peer có thể gửi số rất lớn để gây cạn RAM. Với dữ liệu nén, phải giới hạn cả dung lượng sau giải nén. Ngoài per-frame limit, ứng dụng cần giới hạn tổng số buffer, số kết nối, request đang chờ và timeout.

## 5. Framing bằng dấu phân cách

Với giao thức văn bản, có thể chọn **byte LF = `0x0A`** làm dấu kết thúc:

```text
Dữ liệu gửi: red LF blue LF

Byte trên wire:
  72 65 64 0A 62 6C 75 65 0A
  [ red   ]LF [  blue  ] LF
```

Decoder tích lũy byte cho đến LF, emit phần trước LF thành một frame, bỏ delimiter rồi tiếp tục. Một chunk có thể chứa nhiều LF (nhiều frame); LF có thể đến ở cuối hoặc đầu chunk bất kỳ.

**Ưu điểm:** dễ xem bằng công cụ dòng lệnh, phù hợp protocol theo dòng, log và lệnh text; không cần đo trước độ dài payload.

**Giới hạn:** delimiter **không được xuất hiện không escape trong payload**. Nếu payload chứa LF, phải có escaping, quoting hoặc encoding được đặc tả (ví dụ JSON Lines với newline trong JSON string biểu diễn bằng escape), hoặc dùng length-prefix. Không được tự động coi mọi CRLF và LF là tương đương nếu protocol chưa quy định.

### 5.1. CRLF và delimiter nhiều byte

Một delimiter `CRLF` có hai byte: `0x0D 0x0A`. Nếu một chunk kết thúc ở CR và chunk kế bắt đầu ở LF, thuật toán tìm CRLF trong **từng chunk riêng** sẽ bỏ sót biên thông điệp. Parser cần giữ byte CR đang chờ hoặc dùng matcher streaming theo delimiter. Với delimiter dài hơn, có thể dùng một state machine/KMP giữ số byte khớp tiền tố.

### 5.2. Giới hạn độ dài dòng

Nếu bên gửi không bao giờ gửi LF, decoder không thể phát frame và dữ liệu tích lũy tăng vô hạn. Cần `MAX_LINE_BYTES`, kiểm tra trước khi thêm byte mới, timeout và chính sách từ chối kết nối khi vượt giới hạn. Hai LF liên tiếp có thể là frame rỗng; chấp nhận hoặc từ chối tùy hợp đồng giao thức.

## 6. So sánh length-prefix và delimiter

| Tiêu chí | Length-prefix | Delimiter |
| --- | --- | --- |
| Xác định ranh giới | Header + đúng N byte payload | Tìm chuỗi delimiter |
| Payload nhị phân bất kỳ | Dễ hỗ trợ | Phải escape khi chứa delimiter |
| Văn bản đọc trực tiếp | Header nhị phân không tiện nhìn | Dễ theo dõi bằng terminal |
| Payload dài bao nhiêu | Phải biết hoặc dùng chiến lược streaming/chunked | Không cần biết trước |
| Decoder | Tích lũy header rồi body | Matcher delimiter qua các chunk |
| Rủi ro lớn | Header giả, length âm/quá lớn, sai endian | Quên escaping, không có delimiter, line quá dài |
| Frame qua nhiều lần read | Cần giữ trạng thái | Cần giữ trạng thái |
| Nhiều frame cùng một lần read | Lặp parse để lấy hết | Lặp scan để lấy hết |
| EOF giữa frame | Header/body thiếu là lỗi theo strict protocol | Dòng chưa có delimiter: tùy policy |
| Ứng dụng điển hình | RPC, Protobuf, binary protocol | Line protocol, command text, JSON Lines |

Có thể dùng kết hợp: header length/type rồi payload chứa text có newline. Lựa chọn phải bám vào dữ liệu và định dạng hợp đồng; không có một kỹ thuật mặc định tốt nhất cho mọi hệ thống.

## 7. Ví dụ Java 17+ chạy độc lập: giải mã được dữ liệu bị chia hoặc gộp

Chương trình sau **mô phỏng kết quả socket read** bằng cách chia một luồng byte thành những chunk tùy ý. Decoder length-prefix quản lý 4 byte header và payload, decoder delimiter dùng LF; cả hai giữ trạng thái qua các lần gọi. Đây là bài test xác định, không phụ thuộc thời điểm mạng.

Tạo tệp `FramingDemo.java`:

```java
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FramingDemo {
    static final int MAX_BYTES = 1024;

    static byte[] encodeLengthFrame(String text) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        if (data.length > MAX_BYTES) {
            throw new IllegalArgumentException("Too long");
        }
        ByteBuffer frame = ByteBuffer.allocate(4 + data.length);
        frame.order(ByteOrder.BIG_ENDIAN);
        frame.putInt(data.length);
        frame.put(data);
        return frame.array();
    }

    static class LengthDecoder {
        private final ByteBuffer header =
                ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
        private byte[] body;
        private int used;

        List<String> accept(byte[] chunk) {
            List<String> messages = new ArrayList<>();

            for (byte b : chunk) {
                if (body == null) {
                    header.put(b);
                    if (!header.hasRemaining()) {
                        header.flip();
                        int length = header.getInt();
                        header.clear();

                        if (length < 0 || length > MAX_BYTES) {
                            throw new IllegalArgumentException("Bad length");
                        }
                        if (length == 0) {
                            messages.add("");
                        } else {
                            body = new byte[length];
                            used = 0;
                        }
                    }
                } else {
                    body[used++] = b;
                    if (used == body.length) {
                        messages.add(new String(
                                body, StandardCharsets.UTF_8));
                        body = null;
                        used = 0;
                    }
                }
            }
            return messages;
        }

        void endOfInput() throws EOFException {
            if (header.position() != 0 || body != null) {
                throw new EOFException("Incomplete length frame");
            }
        }
    }

    static class LineDecoder {
        private final ByteArrayOutputStream pending =
                new ByteArrayOutputStream();

        List<String> accept(byte[] chunk) {
            List<String> messages = new ArrayList<>();
            for (byte b : chunk) {
                if (b == 10) { // LF
                    messages.add(new String(
                            pending.toByteArray(), StandardCharsets.UTF_8));
                    pending.reset();
                } else {
                    if (pending.size() >= MAX_BYTES) {
                        throw new IllegalArgumentException("Line too long");
                    }
                    pending.write(b);
                }
            }
            return messages;
        }

        void endOfInput() throws EOFException {
            if (pending.size() != 0) {
                throw new EOFException("Missing final LF");
            }
        }
    }

    public static void main(String[] args) throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        stream.writeBytes(encodeLengthFrame("Hi"));
        stream.writeBytes(encodeLengthFrame("NIO"));
        byte[] wire = stream.toByteArray();

        LengthDecoder length = new LengthDecoder();
        System.out.println("length 1: " +
                length.accept(Arrays.copyOfRange(wire, 0, 3)));
        System.out.println("length 2: " +
                length.accept(Arrays.copyOfRange(wire, 3, 8)));
        System.out.println("length 3: " +
                length.accept(Arrays.copyOfRange(wire, 8, wire.length)));
        length.endOfInput();

        byte[] lines = ("red" + (char) 10 + "blue" + (char) 10)
                .getBytes(StandardCharsets.UTF_8);
        LineDecoder line = new LineDecoder();
        System.out.println("line 1: " +
                line.accept(Arrays.copyOfRange(lines, 0, 2)));
        System.out.println("line 2: " +
                line.accept(Arrays.copyOfRange(lines, 2, 6)));
        System.out.println("line 3: " +
                line.accept(Arrays.copyOfRange(lines, 6, lines.length)));
        line.endOfInput();
    }
}
```

Biên dịch/chạy bằng JDK 17 trở lên:

```bash
javac FramingDemo.java
java FramingDemo
```

Kết quả:

```text
length 1: []
length 2: [Hi]
length 3: [NIO]
line 1: []
line 2: [red]
line 3: [blue]
```

**Giải thích:**

1. Chunk đầu của length-prefix mới có 3 byte header, chưa thể emit.
2. Chunk hai chứa byte header thứ tư, payload `Hi` và 2 byte đầu của header kế tiếp. Decoder emit `Hi`, giữ trạng thái header dang dở.
3. Chunk cuối hoàn tất header thứ hai và ba byte `NIO`, emit `NIO`.
4. Với delimiter, hai byte đầu là `re`. Chunk kế là `d`, LF, `bl`; decoder emit `red`, giữ `bl`. Chunk cuối là `ue`, LF, nên emit `blue`.
5. `endOfInput()` bảo đảm EOF ở ranh giới frame. Nếu header/body hoặc line còn dở, ví dụ này ném `EOFException`.

Decoder demo dùng `new String(bytes, UTF_8)`, có thể thay thế byte UTF-8 không hợp lệ. Nếu protocol yêu cầu *bắt buộc* UTF-8 hợp lệ, hãy dùng `CharsetDecoder` với `CodingErrorAction.REPORT`. Với performance cao, có thể thay vòng lặp từng byte bằng parser đọc trực tiếp `ByteBuffer`, nhưng phải giữ các bất biến về framing như trên.

## 8. Tích hợp với `SocketChannel` và `ByteBuffer`

Phần code Java NIO thực tế phải phân biệt ba giá trị từ `read()`:

- `n > 0`: có `n` byte mới; đưa **đúng đoạn này** vào decoder.
- `n == 0`: chưa có byte mới tại lần gọi, thường gặp khi socket non-blocking chưa sẵn sàng; không emit và không coi là EOF.
- `n == -1`: input stream kết thúc; gọi `decoder.endOfInput()` để kiểm tra frame dở dang rồi kết thúc phần đọc theo chính sách.
- `IOException`: lỗi I/O cần xử lý riêng, không đổi thành frame rỗng.

Ví dụ khung xử lý **trong một handler ứng dụng**; các biến `socket`, `buffer`, `decoder` và `handleMessage` phải được định nghĩa ngoài đoạn:

```java
int n = socket.read(buffer);

if (n > 0) {
    buffer.flip();
    byte[] chunk = new byte[buffer.remaining()];
    buffer.get(chunk);

    for (String message : decoder.accept(chunk)) {
        handleMessage(message);
    }
    buffer.clear();
} else if (n == 0) {
    // Non-blocking: trở về Selector/event loop để chờ tiếp.
} else {
    decoder.endOfInput();
    // Kết thúc nhận, đóng hoặc half-close theo giao thức.
}
```

Ví dụ này giả định `buffer` được chuẩn bị nhận dữ liệu từ lần đọc trước. Ở một ứng dụng thực tế cần xử lý lỗi callback, giới hạn số byte và trạng thái đóng tài nguyên.

**Hai mẫu quản lý buffer khác nhau:**

- **Decoder tự sở hữu pending bytes**, như `LengthDecoder` trong ví dụ: sau khi chuyển toàn bộ byte mới vào decoder có thể `buffer.clear()`. `clear()` chỉ reset `position/limit` và không có tác dụng xóa byte.
- **Decoder giữ phần chưa đủ trong chính read buffer**: khi chuyển sang chế độ đọc bằng `flip()`, parser tiêu thụ frame hoàn chỉnh, sau đó dùng `compact()` để giữ byte còn thiếu; không `clear()` nếu còn partial frame.

Không trộn lẫn hai cách để tránh mất dữ liệu hoặc đọc lại byte cũ. Mỗi kết nối giữ decoder và read buffer riêng.

### 8.1. Selector không quyết định ranh giới message

`SelectionKey.isReadable()` chỉ cho biết có khả năng đọc; nó **không hứa** rằng `read()` nhận đủ một frame, hoặc mỗi thông báo ready chứa một message. Trong event loop:

```text
Selector chọn key readable
           |
       channel.read(buffer)
         /      |       \
       n>0     n=0     n=-1
        |       |        |
    feed      chờ       EOF -> finalize decoder
   decoder   sự kiện    và đóng theo chính sách
        |
   emit 0..N frame
```

Nếu `read` trả `0`, đừng gọi liên tục trong vòng `while` không có cơ chế chờ: gây busy loop và tốn CPU. Trả điều khiển về selector/event loop và giữ state pending đến lượt sau.

## 9. Chiều ghi cần xử lý partial write

Cũng như `read()`, `SocketChannel.write(ByteBuffer)` có thể xử lý **ít byte hơn số byte còn lại**, đặc biệt trong non-blocking mode. Điều này không được phép làm thay đổi thứ tự frame:

```java
ByteBuffer frame = encode("Hello");
int written = socket.write(frame);
// Nếu frame.hasRemaining(), frame chưa được gửi xong.
```

Đây là snippet trong một phương thức có `socket` và `encode`. Khi còn byte trong frame, phải giữ chính buffer đó với `position` hiện tại để ghi tiếp. Nếu gọi `clear()` hoặc gửi frame tiếp theo ngay, có thể gây xen kẽ header/payload hoặc làm mất byte.

Một thiết kế phổ biến là **outbound queue** chứa `Deque<ByteBuffer>`; mỗi phần tử là một frame đã `flip()`. Khi có `OP_WRITE`, ghi phần tử đầu tiên cho đến khi hết byte hoặc socket tạm không nhận thêm:

```java
while (!outbound.isEmpty()) {
    ByteBuffer head = outbound.peek();
    int written = socket.write(head);

    if (head.hasRemaining()) {
        // Còn byte; giữ nguyên head ở đầu queue và chờ OP_WRITE.
        break;
    }
    outbound.remove();

    if (written == 0) {
        break;
    }
}
```

Đây là đoạn minh họa trong handler có sẵn `SocketChannel socket` và `Deque<ByteBuffer> outbound`. Trong event loop, chỉ đăng ký `OP_WRITE` khi có byte còn chờ và hủy interest này khi queue rỗng để tránh selector báo writable liên tục. Cần giới hạn tổng outbound bytes để xử lý client nhận chậm (backpressure).

**`write()==0` không phải EOF.** Nó nói lượt ghi này không chuyển byte; với non-blocking socket cần chờ một sự kiện phù hợp.

## 10. EOF, timeout và thông điệp bị cắt

Khi `read()` trả `-1`, protocol phải quyết định liệu buffer hiện có là frame hợp lệ hay còn thiếu dữ liệu.

| Trạng thái khi EOF | Cách diễn giải với protocol strict |
| --- | --- |
| Length decoder đang chờ header mới, không có byte dư | Kết thúc bình thường |
| Header mới nhận 1–3/4 byte | Truncated header |
| Đã nhận header length N, payload mới có K byte với K nhỏ hơn N | Truncated payload |
| Delimiter decoder không có pending bytes | Kết thúc bình thường |
| Delimiter decoder có pending bytes nhưng không có delimiter | Thiếu delimiter, nếu protocol đòi LF kết thúc |
| Delimiter protocol cho phép final line không có LF | Có thể emit dòng cuối, nhưng phải ghi rõ ngoại lệ này |

Không phải protocol nào cũng cho phép EOF thay thế delimiter. Nếu sử dụng mô hình line theo Unix, có thể chấp nhận dòng cuối không có LF; nếu framing yêu cầu LF bắt buộc, phải báo lỗi. **Hai bên phải thống nhất**.

Ngoài EOF còn có các chính sách timeout: client có thể gửi header rồi giữ kết nối mở mãi mà không gửi payload; decoder cần deadline cho frame, giới hạn số kết nối và cơ chế hủy thao tác chậm. TCP half-close có nghĩa phía gửi kết thúc output không nhất thiết đồng nghĩa phía nhận cũng đã đóng; ứng dụng nên phân biệt chiều dữ liệu.

## 11. Dữ liệu UTF-8 và delimiter

Nếu gửi `"Tiếng Việt"` theo length-prefix, header chứa số **byte UTF-8**, không chứa số ký tự Java. Một ký tự nhiều byte có thể bị chia giữa hai lần socket read; decoder nên đợi đủ payload rồi decode, hoặc dùng `CharsetDecoder` ở chế độ streaming.

Với delimiter LF `0x0A`, việc quét byte trước rồi decode cả frame là cách đơn giản nếu protocol chọn UTF-8 và không cho phép LF bên trong payload. Đối với JSON Lines, newline trong trường string phải được escape thành nội dung văn bản hợp lệ (ví dụ ký tự backslash+n) thay vì chèn LF thô.

Nếu delimiter là chuỗi dài như CRLF, một phần delimiter có thể nằm cuối read buffer và phần còn lại nằm đầu buffer sau. Khi scan trong từng chunk riêng, cần matcher có state hoặc giữ đoạn cuối đủ dài để nối với chunk sau.

Nếu dữ liệu nhị phân có thể chứa bất kỳ giá trị byte nào, **length-prefix thường đơn giản hơn** giải pháp delimiter phải escape tất cả lần xuất hiện của dấu phân cách.

## 12. Xử lý an toàn và giới hạn tài nguyên

**Với length-prefix:**

- Kiểm tra length âm/vượt `MAX_FRAME_BYTES` trước khi allocate.
- Giới hạn tổng số frame chờ xử lý, thời gian nhận frame và tổng byte trong queue.
- Nếu có compression, giới hạn kích thước sau giải nén.
- Nếu có schema có cấu trúc, validate version, type, payload format trước khi sử dụng.
- Đảm bảo không tính nhầm số char hoặc count ký tự Java thành byte count.

**Với delimiter:**

- Chỉ tích lũy tối đa `MAX_LINE_BYTES` trước delimiter, kể cả peer không bao giờ gửi delimiter.
- Nếu delimiter có thể xuất hiện trong payload, phải escape/quote theo định dạng hoặc chọn phương thức khác.
- Xác định CRLF và LF có được phép cùng tồn tại không.
- Xác định empty frame, trailing delimiter và EOF không có delimiter.

**Với cả hai:**

- Decoder độc lập cho mỗi kết nối.
- Có timeout, idle timeout, chính sách close, exception logging phù hợp.
- Không busy-loop khi read hoặc write trả 0.
- Quản lý inbound/outbound backpressure.
- Không tin dữ liệu đầu vào mạng chỉ vì đã xác định được ranh giới frame: framing **không** xác thực danh tính, không mã hóa và không bảo đảm dữ liệu an toàn.

## 13. Kiểm thử ở mọi điểm chia chunk

Vì ranh giới của TCP read tùy ý, nên tạo test đưa **cùng một luồng byte hợp lệ** vào decoder theo nhiều cách chia:

1. Từng byte một.
2. Toàn bộ byte trong một chunk.
3. Cắt giữa từng byte của header length.
4. Cắt giữa header và payload.
5. Cắt giữa byte UTF-8 nhiều byte.
6. Chunk có nhiều frame hoàn chỉnh và một phần frame sau.
7. Cắt giữa CR và LF của delimiter CRLF.
8. EOF khi header hoặc payload chưa hoàn chỉnh.
9. Header âm, header quá lớn, delimiter thiếu, dòng quá dài.

Bất biến mong muốn:

```text
decode(allBytes as one chunk)
  == decode(same bytes split into arbitrary chunks)
```

Ở đây `decode` so sánh danh sách frame **cùng nội dung và thứ tự**, giả định luồng byte hợp lệ và cùng trạng thái ban đầu. Đối với luồng không hợp lệ, cần kiểm tra cả loại lỗi và không emit frame thiếu dữ liệu.

Property-based testing/fuzzing rất hữu ích: sinh các chuỗi frame ngẫu nhiên theo encoder, nối thành stream, chia ngẫu nhiên thành các chunk, rồi kiểm tra decoder tạo đúng tập frame ban đầu. Những test này phát hiện bug mà các bài thử “một write, một read” thường bỏ sót.

## 14. Các lỗi phổ biến

| Lỗi | Hậu quả | Cách sửa |
| --- | --- | --- |
| Cho rằng một `write` bằng một `read` | Frame bị chia/gộp sai | Parser streaming với state |
| Đọc một lần rồi `getInt()` ngay | Thiếu byte header | Đợi đủ 4 byte |
| Header dùng `String.length()` | Sai độ dài UTF-8 | Đếm byte sau encoding |
| Encoder/decoder khác endian | Độ dài bị hiểu sai | Chọn BIG_ENDIAN và test wire bytes |
| Dùng length mạng để allocate không giới hạn | OutOfMemory/DoS | Validate trước allocation |
| Chỉ emit một frame mỗi chunk | Bỏ frame tiếp | Lặp xử lý hết chunk |
| Không giữ state frame dở | Mất header/payload giữa các lần read | `compact()` hoặc decoder độc lập |
| Delimiter xuất hiện trong payload | Tách sớm | Escape/encoding/length-prefix |
| Không giới hạn byte khi chờ delimiter | Bộ nhớ phình to | MAX_LINE_BYTES và deadline |
| Chỉ tìm CRLF bên trong từng chunk | Bỏ delimiter bị chia | Streaming delimiter matcher |
| `read()==0` xem là EOF | Ngắt kết nối sai | Chỉ `-1` là EOF |
| `write()==0` thì lặp liên tục | Busy CPU | `OP_WRITE`, queue và backpressure |
| `clear()` buffer trước khi giữ byte dư | Mất frame dở | `compact()` hoặc state machine |
| EOF khi frame dở vẫn emit | Dữ liệu thiếu | Báo truncated frame theo strict protocol |

## 15. Tự kiểm tra

**1.** Client gọi `write` hai lần cho hai message, server nhận một `read` chứa cả hai. TCP có lỗi không?  
**Đáp án:** Không. Framing tầng ứng dụng phải tách hai message.

**2.** Length header là 4 byte, một lượt read mới nhận 2 byte. Decoder có thể đọc `getInt()` không?  
**Đáp án:** Không. Phải đợi đủ 4 byte rồi đọc length.

**3.** Payload dài 5 byte, lần đầu chỉ nhận 3. Có nên emit luôn?  
**Đáp án:** Không. Giữ 3 byte và đợi 2 byte tiếp, hoặc báo truncated nếu EOF.

**4.** Một chunk chứa frame A, frame B và hai byte đầu của frame C. Decoder làm gì?  
**Đáp án:** Emit A và B, giữ hai byte đầu của C.

**5.** Line protocol dùng LF, nhưng dữ liệu người dùng chứa newline. Có thể gửi thẳng không?  
**Đáp án:** Không, nếu không có escape/quote; có thể chuyển sang length-prefix.

**6.** CRLF chia thành hai chunk ở CR và LF. Parser cần gì?  
**Đáp án:** Giữ trạng thái delimiter chưa hoàn chỉnh qua các chunk.

**7.** Peer gửi header length 2.000.000.000 byte. Có nên allocate ngay?  
**Đáp án:** Không. Validate giới hạn và reject nếu vượt.

**8.** `SocketChannel.write` trả 0 nhưng frame còn byte. Có thể gửi frame tiếp theo trước không?  
**Đáp án:** Không. Giữ frame đầu trong queue và chờ có khả năng ghi.

**9.** EOF đến khi length decoder đang giữ header mới chỉ có 3 byte. Có thể coi là message rỗng không?  
**Đáp án:** Không. Trong strict framing đây là truncated header.

## 16. Checklist triển khai

- Chốt wire format: byte order, header length, length semantics, charset/delimiter, maximum, frame rỗng, version.
- Decoder duy trì state giữa các `read` và xử lý 0..N frame cho mỗi chunk.
- Producer xử lý partial `write` bằng `ByteBuffer.position()` và queue.
- NIO event loop phân biệt `read>0`, `read==0`, `read==-1`.
- Quy định EOF/timeout khi frame chưa hoàn chỉnh.
- Giới hạn kích thước frame/dòng, tổng queue, số kết nối và thời gian chờ.
- Test chia/gộp frame ở mọi ranh giới và thử dữ liệu không hợp lệ.
- Chỉ giải mã UTF-8 khi đủ byte, hoặc dùng decoder streaming; dùng strict validation khi cần.
- Đo hiệu năng và backpressure dưới tải, không dựa vào hành vi localhost.

## Tham khảo API chính thức

- Java SE 21 — `SocketChannel`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/SocketChannel.html
- Java SE 21 — `ReadableByteChannel`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/ReadableByteChannel.html
- Java SE 21 — `WritableByteChannel`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/WritableByteChannel.html
- Java SE 21 — `ByteBuffer`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/ByteBuffer.html
- Java SE 21 — `Selector`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/Selector.html
- Java SE 21 — `CharsetDecoder`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/charset/CharsetDecoder.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nio-networking-006">Partial read, partial write và lưu trạng thái giữa các lần sẵn sàng</a>

<details>
<summary>Xem chi tiết</summary>

# Partial read, partial write và lưu trạng thái giữa các lần sẵn sàng trong Java NIO

Một kết nối TCP truyền **dòng byte** liên tục. Một lời gọi `SocketChannel.read(ByteBuffer)` không tương ứng với đúng một thông điệp nghiệp vụ; một lần `write(ByteBuffer)` cũng không đảm bảo toàn bộ thông điệp đã được gửi. Đặc biệt với `SocketChannel` ở chế độ non-blocking và `Selector`, `read()` hoặc `write()` có thể xử lý **một phần dữ liệu**, hoặc trả về `0` mà không phải lỗi.

Bài toán quan trọng là: **sau mỗi lần có sự kiện sẵn sàng, phải giữ nguyên số byte đã nhận, số byte còn cần gửi và trạng thái phân tích thông điệp để lần sẵn sàng tiếp theo tiếp tục đúng chỗ**. Nếu vứt bộ đệm giữa các lần `select()`, chương trình có thể làm mất byte, gửi thiếu thông điệp, ghép sai frame hoặc chạy vòng lặp tốn CPU khi socket chưa thể tiến triển.

## 1. TCP stream không có ranh giới thông điệp

Giả sử client gửi chuỗi UTF-8 `HELLO\n` dài 6 byte. Tại phía server, các lần đọc hợp lệ có thể trông như:

```text
Client write:  [ H E L L O \n ]

Server read #1: [ H E ]
Server read #2: [ L ]
Server read #3: [ L O \n ]
```

Hoặc server có thể đọc cả 6 byte trong một lần. Ngược lại, nếu client gửi hai thông điệp `HELLO\n` và `WORLD\n`, server có thể nhận nhiều byte của cả hai trong cùng một lần đọc.

Các nguyên nhân gồm trạng thái bộ đệm TCP, điều kiện mạng, kích thước `ByteBuffer`, tốc độ xử lý và hệ điều hành. Vì thế **mỗi lần socket báo readable không đại diện cho một thông điệp hoàn chỉnh**.

Ứng dụng phải tự định nghĩa **framing**, chẳng hạn:

- Kết thúc bằng dấu phân cách (`\n` cho dòng văn bản).
- Tiền tố độ dài: bốn byte đầu biểu diễn số byte payload, sau đó đọc đúng lượng byte tương ứng.
- Khung có cấu trúc cố định, nếu giao thức thật sự quy định kích thước không đổi.
- Trình phân tích giao thức riêng có thể nhận nhiều frame hoặc một frame chia thành nhiều phần.

Framing phải được xử lý trên **chuỗi byte liên tục**. Với UTF-8, một ký tự có thể trải qua nhiều lần đọc; không nên `new String(chunk)` rồi giả định từng chunk là văn bản hoàn chỉnh.

## 2. Ý nghĩa chính xác của kết quả `SocketChannel.read`

Đối với một `SocketChannel` có đăng ký `OP_READ`, lời gọi:

```java
int n = socket.read(inputBuffer);
```

có ba loại kết quả cần tách bạch:

| Giá trị | Ý nghĩa | Ứng dụng cần làm gì |
| --- | --- | --- |
| `n > 0` | Có `n` byte được đưa vào vùng còn trống của `inputBuffer` | Giữ dữ liệu đã nhận và thử phân tích frame hoàn chỉnh |
| `n == 0` | Chưa đọc được byte nào trong lời gọi này; điều này bình thường với non-blocking socket hoặc buffer không có chỗ | Không coi là EOF, không spin vô hạn; giữ trạng thái cho lần readiness sau |
| `n == -1` | Phía remote đã kết thúc luồng đọc của kênh (EOF phía nhận) | Quyết định đóng kết nối, xử lý half-close hoặc kiểm tra frame dở dang theo giao thức |

Đọc vào buffer sẽ tăng `inputBuffer.position()` đúng bằng số byte đọc được, **không tự thay đổi `limit()`**. Nếu trước lần gọi buffer đã chứa ba byte chưa xử lý, lần đọc tiếp theo phải tiếp tục ở vị trí sau ba byte đó.

Đoạn minh họa đúng ý tưởng:

```java
// socket: SocketChannel non-blocking; input: ByteBuffer được giữ lâu dài.
int n = socket.read(input);
if (n > 0) {
    input.flip();       // Đọc các byte đã nhận.
    parseFrames(input); // Hàm phân tích frame có thể để lại dữ liệu dở.
    input.compact();    // Dồn byte chưa tiêu thụ về đầu, chuẩn bị đọc thêm.
} else if (n == -1) {
    closeOrHalfClose(socket); // Hàm nghiệp vụ minh họa.
}
```

Hai hàm `parseFrames` và `closeOrHalfClose` là **hàm minh họa chưa định nghĩa**, đoạn này không phải chương trình Java độc lập. Quan trọng nhất: `parseFrames` phải để `position` sau byte cuối đã tiêu thụ; `compact()` giữ nguyên các byte còn lại. Nếu ứng dụng dùng parser lưu trạng thái khác, vẫn phải bảo toàn byte chưa xử lý.

## 3. Partial write và trạng thái `ByteBuffer` còn lại

Ghi non-blocking có quy tắc tương tự:

```java
int n = socket.write(outputBuffer);
```

- Nếu `n > 0`, channel nhận `n` byte từ buffer; `outputBuffer.position()` tăng lên `n`. Các byte đã được kernel nhận vào đường gửi **chưa chắc phía remote đã nhận hoặc xử lý**.
- Nếu `n == 0`, tại thời điểm đó channel không ghi thêm được byte nào (hoặc buffer không còn byte). Với buffer còn dữ liệu, giữ nguyên buffer và chờ sự kiện writable tiếp theo.
- `write(ByteBuffer)` không có hợp đồng "ghi hết `remaining()` trong một lần". Cần xử lý partial write dựa trên giá trị trả về và `buffer.hasRemaining()`.

Ví dụ buffer chứa 5 byte `ABCDE`, các lần ghi có thể là:

```text
Trước ghi:  position=0, limit=5, remaining=5
write #1:  trả 2  -> position=2, remaining=3
write #2:  trả 0  -> position=2, remaining=3
write #3:  trả 3  -> position=5, remaining=0
```

Khi quay lại từ event loop, **không gọi `rewind()` hoặc `flip()` trên output buffer chưa gửi hết**, vì như vậy có thể gửi lại byte đã gửi hoặc đổi sai miền dữ liệu hợp lệ. Giữ buffer với `position` hiện tại cho tới khi `hasRemaining()` là `false`.

Nếu có nhiều frame đang chờ, mỗi kết nối thường giữ **hàng đợi các buffer đầu ra**. Chỉ lấy phần tử đầu ra sau khi buffer đó đã hết byte; sau đó mới chuyển sang frame tiếp theo để giữ đúng thứ tự TCP.

## 4. Vì sao phải lưu trạng thái giữa những lần `select()`?

`Selector` giúp một luồng theo dõi nhiều `SelectableChannel` bằng các `SelectionKey`. Với socket không chặn:

1. Đăng ký `OP_READ` khi muốn nhận dữ liệu.
2. Khi `select()` báo khóa có `isReadable()`, thử đọc và cập nhật input buffer.
3. Nếu thông điệp đã hoàn chỉnh, chuẩn bị response thành output buffer và đăng ký `OP_WRITE` khi còn byte cần gửi.
4. Khi khóa có `isWritable()`, thử ghi phần còn lại; nếu chưa hết, giữ `OP_WRITE`.
5. Khi output buffer đã hết byte, **bỏ `OP_WRITE`** để tránh selected liên tục vì socket thường xuyên writable, dù không còn dữ liệu cần gửi.

Tính chất readiness là **gợi ý về khả năng thao tác**, không phải bảo đảm read/write sẽ xử lý tất cả dữ liệu mong muốn hoặc luôn tiến triển. Ngay cả khi selected key báo ready, `read` hoặc `write` vẫn có thể trả `0`; phải cập nhật trạng thái dựa vào giá trị thật.

### Một cấu trúc trạng thái trên mỗi kết nối

Một mô hình thường dùng là `Session` hoặc `ConnectionState`, được gắn vào `SelectionKey`:

```java
// Đoạn định nghĩa minh họa.
final class ConnectionState {
    final ByteBuffer inbound = ByteBuffer.allocate(8192);
    final java.util.ArrayDeque<ByteBuffer> outgoing =
            new java.util.ArrayDeque<>();
    boolean peerInputClosed;
}
```

Sau khi `channel.register(selector, SelectionKey.OP_READ, state)`, có thể lấy lại state qua `(ConnectionState) key.attachment()`. **Không tạo lại `ByteBuffer` ở mỗi vòng selector**: buffer mới sẽ không còn các byte chưa đủ tạo frame từ lượt trước.

State có thể phải chứa thêm số byte payload đang đợi, trạng thái parser, charset decoder, deadline, bộ đếm giới hạn, trạng thái half-close và các giao thức request/response đang chạy.

## 5. `flip()`, `compact()` và dữ liệu nhận dở

Giả sử ứng dụng nhận một frame kết thúc bằng `\n`, nhưng lần đọc thứ nhất chỉ nhận `HEL`:

```text
Buffer sau read #1:
[ H E L _ _ _ _ _ ]  position=3, limit=8
flip():
[ H E L _ _ _ _ _ ]  position=0, limit=3
parser: chưa có '\n', không tiêu thụ byte
compact():
[ H E L _ _ _ _ _ ]  position=3, limit=8

read #2 thêm "LO\n":
[ H E L L O \n _ _ ] position=6, limit=8
flip() -> parser nhận đủ "HELLO\n".
compact() -> position=0, limit=8 nếu đã tiêu thụ hết.
```

Nếu buffer có `ONE\nTWO`, parser nên lấy frame `ONE\n`, giữ lại `TWO` bằng `compact()` và chờ `\n` còn thiếu. Nếu buffer có `ONE\nTWO\n`, parser phải có thể trả ra **hai frame trong cùng lần phân tích**.

Cần phân biệt:

- `flip()`: chuyển từ chế độ ghi dữ liệu vào buffer sang chế độ đọc dữ liệu đã ghi.
- `clear()`: đặt position=0, limit=capacity, **không giữ riêng vùng byte chưa xử lý** và không xóa byte thật.
- `compact()`: giữ byte còn lại trong vùng `position..limit`, dồn về đầu buffer và đặt lại trạng thái để tiếp tục nhận.

Nếu parser đã tiêu thụ toàn bộ byte, `compact()` có kết quả position=0 giống trạng thái sẵn sàng ghi mới. Nếu còn phần frame dở, `compact()` mới bảo vệ nó.

### Khi buffer đầu vào đầy mà vẫn chưa có frame

Nếu `inbound.remaining()==0` trong chế độ ghi nhưng parser chưa hoàn thành frame, ứng dụng không thể tiếp tục nhận thêm byte vào buffer cũ. Cần chính sách rõ ràng: giới hạn kích thước frame, mở rộng buffer tới hạn mức hợp lệ hoặc từ chối thông điệp quá lớn. Đừng cứ gọi `read()` vào buffer đầy rồi spin với kết quả 0.

## 6. Chương trình Java 17+ hoàn chỉnh: một kết nối TCP, nhiều lượt readiness

Ví dụ sau dùng TCP loopback để kết quả có thể chạy độc lập. Client gửi một frame ASCII `PING\n`, server xử lý bằng `SocketChannel` non-blocking và `Selector`. Để **minh họa chắc chắn** việc lưu trạng thái, mỗi lần xử lý readable chỉ cho phép đọc tối đa **2 byte**, mỗi lần writable chỉ cho phép ghi tối đa **2 byte**, bằng cách tạm hạ `ByteBuffer.limit()`. Đây là giới hạn do ví dụ đặt ra để buộc có nhiều bước xử lý; trong ứng dụng thật các lần read/write có thể ngắn hơn do mạng hoặc thiết bị.

Tạo file **`PartialIoSelectorDemo.java`**:

```java
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

public final class PartialIoSelectorDemo {
    private static final byte[] REQUEST =
            "PING\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] RESPONSE =
            "ACK:PING\n".getBytes(StandardCharsets.US_ASCII);

    private PartialIoSelectorDemo() {}

    private static final class Session {
        final ByteBuffer inbound = ByteBuffer.allocate(32);
        ByteBuffer outbound;
        int readFragments;
        int writeFragments;
        boolean done;
    }

    public static void main(String[] args) throws IOException {
        try (ServerSocketChannel listener = ServerSocketChannel.open();
             SocketChannel client = SocketChannel.open();
             Selector selector = Selector.open()) {

            listener.bind(new InetSocketAddress(
                    InetAddress.getLoopbackAddress(), 0));
            client.connect(listener.getLocalAddress());

            try (SocketChannel accepted = listener.accept()) {
                accepted.configureBlocking(false);
                Session session = new Session();
                accepted.register(selector, SelectionKey.OP_READ, session);

                // Client sử dụng blocking mode để gửi một request nhỏ.
                writeAll(client, ByteBuffer.wrap(REQUEST));

                long deadline = System.nanoTime()
                        + TimeUnit.SECONDS.toNanos(5);

                while (!session.done) {
                    if (System.nanoTime() >= deadline) {
                        throw new IOException("Timed out waiting for I/O");
                    }

                    if (selector.select(100) == 0) {
                        continue;
                    }

                    Iterator<SelectionKey> it =
                            selector.selectedKeys().iterator();
                    while (it.hasNext()) {
                        SelectionKey key = it.next();
                        it.remove();
                        if (!key.isValid()) {
                            continue;
                        }

                        Session state = (Session) key.attachment();
                        if (key.isReadable()) {
                            readOneFragment(key, state);
                        }
                        if (key.isValid() && key.isWritable()) {
                            writeOneFragment(key, state);
                        }
                    }
                }

                ByteBuffer reply = ByteBuffer.allocate(RESPONSE.length);
                while (reply.hasRemaining()) {
                    int n = client.read(reply);
                    if (n < 0) {
                        throw new EOFException("Reply truncated");
                    }
                    if (n == 0) {
                        throw new IOException("No client read progress");
                    }
                }
                reply.flip();

                System.out.println("request=PING");
                System.out.println("reply="
                        + StandardCharsets.US_ASCII.decode(reply)
                                .toString().strip());
                System.out.println("read fragments >=3: "
                        + (session.readFragments >= 3));
                System.out.println("write fragments >=5: "
                        + (session.writeFragments >= 5));
            }
        }
    }

    private static void readOneFragment(SelectionKey key, Session state)
            throws IOException {
        SocketChannel socket = (SocketChannel) key.channel();

        if (!state.inbound.hasRemaining()) {
            throw new IOException("Request exceeds input capacity");
        }

        int oldLimit = state.inbound.limit();
        int cap = Math.min(2, state.inbound.remaining());
        state.inbound.limit(state.inbound.position() + cap);

        int n;
        try {
            n = socket.read(state.inbound);
        } finally {
            state.inbound.limit(oldLimit);
        }

        if (n < 0) {
            throw new EOFException("Peer closed before request completed");
        }
        if (n == 0) {
            return; // Đợi readability tiếp theo, giữ nguyên position.
        }

        state.readFragments++;
        int written = state.inbound.position();
        if (state.inbound.get(written - 1) != (byte) '\n') {
            return; // Frame chưa đủ, giữ byte đã nhận trong inbound.
        }

        state.inbound.flip();
        byte[] frame = new byte[state.inbound.remaining()];
        state.inbound.get(frame);
        if (!Arrays.equals(frame, REQUEST)) {
            throw new IOException("Unexpected request");
        }

        state.outbound = ByteBuffer.wrap(RESPONSE);
        key.interestOps(SelectionKey.OP_WRITE);
    }

    private static void writeOneFragment(SelectionKey key, Session state)
            throws IOException {
        SocketChannel socket = (SocketChannel) key.channel();
        ByteBuffer out = state.outbound;
        if (out == null) {
            throw new IOException("No outbound frame");
        }

        int oldLimit = out.limit();
        int cap = Math.min(2, out.remaining());
        out.limit(out.position() + cap);

        int n;
        try {
            n = socket.write(out);
        } finally {
            out.limit(oldLimit);
        }

        if (n > 0) {
            state.writeFragments++;
        }
        if (!out.hasRemaining()) {
            key.interestOps(0); // Không đăng ký OP_WRITE khi hết dữ liệu.
            state.done = true;
        }
        // Nếu n==0 hoặc còn byte: giữ nguyên out.position().
    }

    private static void writeAll(SocketChannel channel, ByteBuffer data)
            throws IOException {
        while (data.hasRemaining()) {
            int n = channel.write(data);
            if (n == 0) {
                throw new IOException("No blocking write progress");
            }
        }
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac PartialIoSelectorDemo.java
java PartialIoSelectorDemo
```

Kết quả khi TCP loopback và selector hoạt động bình thường:

```text
request=PING
reply=ACK:PING
read fragments >=3: true
write fragments >=5: true
```

Các dòng cuối **không giả định số lần đọc/ghi chính xác**. Vì chương trình giới hạn mỗi lần đọc/ghi thành tối đa 2 byte, frame 5 byte cần ít nhất 3 lần nhận byte thành công, frame 9 byte cần ít nhất 5 lần gửi byte thành công. Hệ điều hành có thể khiến số lần nhiều hơn; kết quả so sánh `>=3` và `>=5` vẫn là `true`. Timeout 5 giây chỉ là bảo vệ ví dụ khỏi vòng chờ vô hạn khi môi trường mạng bất thường.

### Trạng thái được giữ ở đâu?

- `Session.inbound` **không được cấp phát lại giữa các readiness**. Sau lượt đọc đầu tiên, position có thể là 2; lần kế tiếp đọc tiếp ở đó cho tới khi gặp byte xuống dòng.
- `Session.outbound` được tạo một lần khi frame request hoàn chỉnh. Sau mỗi lượt writable, `outbound.position()` tăng đúng bằng số byte đã ghi.
- `Session.readFragments`, `writeFragments` và `done` được giữ trong `Session` gắn với `SelectionKey` qua `register(..., session)` và `attachment()`.
- Khi đã nhận request, server đổi interest từ `OP_READ` sang `OP_WRITE`. Khi gửi xong, xóa interest, rồi kết thúc ví dụ.
- Hai lần tạm giảm `limit()` được khôi phục trong `finally`, bảo toàn capacity và giới hạn ban đầu của buffer.
- Ví dụ cố ý chỉ nhận **một frame ASCII xác định** và không hỗ trợ pipeline nhiều request; đây là giới hạn của giao thức demo. Server thực tế phải phân tích nhiều frame trong một buffer, giữ frame dở bằng `compact()` hoặc state parser và xử lý EOF/half-close.

Chương trình không cần chạy thêm server bên ngoài; hai đầu kết nối được tạo trong một process trên loopback. Với socket non-blocking, việc `select()` báo ready không được hiểu là một frame đầy đủ. Ví dụ chứng minh phần byte đang chờ sống qua nhiều lần readiness.

## 7. Thiết kế parser thông điệp có tiền tố độ dài

Giao thức dùng newline phù hợp minh họa nhưng không thích hợp cho mọi loại binary payload. Với frame gồm **4 byte độ dài** (big-endian) và tiếp theo là `length` byte payload, parser cần giữ hai giai đoạn:

1. `READ_LENGTH`: đọc đủ 4 byte; nếu mới có 1–3 byte thì giữ lại, chưa giải mã độ dài.
2. `READ_PAYLOAD`: sau khi giải mã length và kiểm tra hạn mức, tiếp tục gom đủ payload; sau khi hoàn thành có thể chuyển lại `READ_LENGTH` để nhận frame kế tiếp.

Có thể dùng hai buffer riêng:

```java
// Phác thảo state machine, các biến được lưu trên mỗi connection.
ByteBuffer lengthBytes = ByteBuffer.allocate(4);
ByteBuffer payload = null;

// Trong READ_LENGTH: nạp lengthBytes cho đến khi !hasRemaining().
if (!lengthBytes.hasRemaining()) {
    lengthBytes.flip();
    int length = lengthBytes.getInt(); // BIG_ENDIAN theo mặc định.
    if (length < 0 || length > MAX_FRAME_BYTES) {
        throw new IOException("Invalid frame length");
    }
    payload = ByteBuffer.allocate(length);
    lengthBytes.clear();
    // Chuyển state sang READ_PAYLOAD.
}
```

Đây là **đoạn phác thảo**, chưa định nghĩa `MAX_FRAME_BYTES`, trạng thái, hành vi gom byte hay thao tác xử lý payload; không phải chương trình biên dịch độc lập. Cần bảo đảm không đánh mất byte khi trong một `read` vừa đủ header vừa xuất hiện một phần payload. Không tạo buffer theo length khi chưa xác minh giới hạn, và nên kiểm tra cả tổng bộ nhớ đang dùng của nhiều kết nối.

Nếu frame có length 0, chính sách giao thức phải quyết định đó là frame rỗng hợp lệ hay lỗi; nếu hợp lệ, parser cần hoàn tất ngay mà không chờ `read` thêm byte vào buffer có remaining=0.

## 8. Điều khiển `OP_WRITE` và backpressure

Một sai lầm thường gặp là ngay khi tạo connection đã đăng ký:

```java
channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
```

Nếu không có dữ liệu chờ gửi, nhiều socket vẫn thường writable và selector có thể trả readiness liên tục. Event loop sẽ thức dậy vô ích, làm CPU tăng cao.

Chính sách phổ biến:

- **Không có dữ liệu chờ gửi:** chỉ quan tâm `OP_READ` (cùng các bit interest khác nếu giao thức cần).
- **Có dữ liệu gửi còn lại:** thêm `OP_WRITE` để có cơ hội hoàn thành khi socket nhận thêm byte.
- **Hàng đợi gửi đã rỗng:** bỏ `OP_WRITE` ngay.
- **Hàng đợi output vượt ngưỡng:** áp dụng backpressure: tạm giảm/loại `OP_READ` cho kết nối, từ chối thêm request, giới hạn bộ nhớ hoặc đóng kết nối theo chính sách dịch vụ.

Thay đổi bit interest phải **giữ những bit cần thiết khác**. Ví dụ nếu đang quan tâm read:

```java
// Phác thảo: key còn valid và thao tác do event-loop owner thực hiện.
key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
// Khi queue đã rỗng:
key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
```

Nếu thread khác thêm dữ liệu vào outbound queue, cần phối hợp việc thay đổi `interestOps` và có thể gọi `selector.wakeup()` để thread event loop không đợi hết timeout trước khi xử lý. Không cho nhiều thread cùng sửa `ByteBuffer.position`, queue và key mà không có nguyên tắc sở hữu/đồng bộ nhất quán.

### Khi một kết nối rất bận

Không nên một callback đọc/ghi không giới hạn byte đến khi cạn socket, vì có thể khiến các kết nối khác bị đói tài nguyên (starvation). Đặt hạn mức công việc theo lượt, ví dụ tối đa một số byte, frame hoặc thời gian, rồi quay lại xử lý những key khác. Giới hạn 2 byte trong ví dụ là để quan sát partial I/O; trong hệ thống thật chọn ngưỡng dựa trên benchmark và mục tiêu latency/throughput.

## 9. EOF, half-close, timeout và đóng tài nguyên

`read() == -1` báo remote đã đóng hướng gửi (hoặc socket đã tới EOF phía đọc). Với TCP, có tình huống **half-close**: peer đã gọi `shutdownOutput()` nhưng vẫn có thể nhận dữ liệu theo hướng ngược lại. Ứng dụng không nên mặc định phải vứt ngay hàng đợi output trong mọi giao thức; chính sách phụ thuộc request đã nhận, response cần gửi và cách phát hiện kết thúc kết nối.

Khi EOF đến giữa frame:

- Nếu giao thức đòi frame hoàn chỉnh, báo lỗi frame không đầy đủ.
- Nếu giao thức dùng EOF làm dấu kết thúc thông điệp, xử lý theo quy ước đó, tránh lẫn với framing length-prefix/newline.
- Nếu còn output cần gửi, có thể giữ connection cho tới khi đã gửi hoặc hết deadline, nếu giao thức và trạng thái socket cho phép.

Khi đóng một channel có đăng ký selector, selection key liên quan sẽ bị hủy; nên loại bỏ key khỏi các cấu trúc dữ liệu và giải phóng những reference tới state/buffer. Đối với lỗi I/O, timeout hoặc protocol violation, đóng có kiểm soát, tránh giữ một queue buffer vĩnh viễn.

Một connection state hoàn chỉnh thường cần cả read timeout, write timeout, maximum frame size, maximum pending bytes và idle timeout. Readiness không tự đưa ra giới hạn nào trong số đó.

## 10. Ghi nhận trạng thái để debug partial I/O

Khi chẩn đoán, ghi log theo **số byte thực xử lý** thay vì theo giả định về một lần readiness:

| Trường | Mục đích |
| --- | --- |
| `readResult` | Số byte thực đọc, 0 hoặc -1 |
| `input.position/limit/capacity` | Lượng byte đã nhận và còn chỗ |
| `parserState` | Đang đợi header, payload, delimiter hay xử lý request |
| `writeResult` | Số byte channel thực chấp nhận |
| `output.remaining` | Tổng byte của buffer hiện tại còn phải gửi |
| `pendingQueueBytes` | Áp lực bộ nhớ đầu ra của kết nối |
| `key.interestOps/readyOps` | Đang quan tâm điều gì, selector vừa báo điều gì |
| Thời điểm nhận/gửi cuối | Phục vụ timeout và giám sát tiến độ |

Không log toàn bộ payload nếu chứa bí mật hoặc dữ liệu cá nhân; tập trung vào metadata, số byte và định danh kết nối phù hợp.

## 11. Lỗi thường gặp

| Lỗi | Hệ quả | Cách xử lý |
| --- | --- | --- |
| Mỗi `read()` được xem là một thông điệp hoàn chỉnh | Tách sai frame hoặc ghép nhầm hai frame | Có framing và parser duy trì state |
| Nhận `0` rồi coi là EOF | Đóng kết nối hợp lệ quá sớm | Chỉ `-1` biểu thị EOF với `read` |
| Đọc được vài byte rồi tạo input buffer mới tại readiness tiếp theo | Mất header/payload còn dở | Giữ buffer trong connection state |
| Sau `write` chưa hết buffer lại `flip/rewind` | Gửi trùng byte hoặc sai ranh giới dữ liệu | Giữ `position` tới khi `hasRemaining()==false` |
| Gọi `clear()` khi parser còn byte chưa dùng | Vứt dữ liệu frame dở | `compact()` hoặc dùng parser state riêng |
| Không bỏ `OP_WRITE` khi queue rỗng | Vòng `select()` thức dậy liên tục, tốn CPU | Chỉ quan tâm write khi còn pending output |
| Loop khi `read/write` trả 0 | Busy-loop, không nhường thời gian cho các kết nối khác | Chờ readiness tiếp theo, dùng selector |
| Xử lý một key rất lâu hoặc không giới hạn output queue | Starvation, tăng memory/latency | Byte budget mỗi lượt và backpressure |
| Giải mã UTF-8 riêng từng chunk | Lỗi ký tự nhiều byte bị cắt ngang | Decode trên byte đã đủ frame hoặc dùng `CharsetDecoder` lưu state |
| `read()==-1` nhưng không kiểm tra frame dở | Chấp nhận thông điệp thiếu byte | Quy tắc xử lý EOF và frame chưa hoàn thành |
| Thay đổi `interestOps` từ thread khác nhưng không wakeup/phối hợp | Chậm hoặc bỏ lỡ tiến độ cho đến lần selector thức dậy tiếp | Một event-loop owner, queue an toàn và `selector.wakeup()` |

**Ghi nhớ:** Với Java NIO non-blocking, mỗi lần `OP_READ` hoặc `OP_WRITE` sẵn sàng chỉ là một *cơ hội* xử lý dữ liệu. **Luôn dựa vào số byte thật sự đọc/ghi, giữ buffer và parser state qua nhiều lần `select()`, đăng ký `OP_WRITE` khi còn dữ liệu gửi, và loại bỏ nó khi hoàn tất**. Đó là nền tảng để một server dùng selector xử lý TCP stream chính xác và hiệu quả.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nio-networking-007">EOF, đóng kết nối và đóng một chiều</a>

<details>
<summary>Xem chi tiết</summary>

# EOF, đóng kết nối và đóng một chiều trong Java NIO mạng

## 1. Vấn đề: hết dữ liệu hiện tại khác với kết thúc luồng

Trong mạng TCP, một kết nối truyền **hai luồng byte độc lập theo hướng**: client → server và server → client. Khi xử lý với `java.nio.channels.SocketChannel`, cần phân biệt ba trạng thái:

- **Chưa có byte nào để đọc ngay**: ở chế độ non-blocking, `read(ByteBuffer)` có thể trả `0`; kết nối vẫn có thể nhận dữ liệu trong tương lai.
- **Đầu bên kia đã kết thúc hướng gửi bình thường**: sau khi dữ liệu đang chờ được nhận hết, `read(ByteBuffer)` trả `-1`, nghĩa là *end of stream* (EOF) trên hướng nhận hiện tại.
- **Kết nối hoặc channel gặp lỗi/bị đóng**: lời gọi có thể ném `IOException` hoặc một ngoại lệ cụ thể; không được đánh đồng mọi lỗi này với EOF bình thường.

Java NIO cho phép đóng toàn bộ channel bằng `close()` hoặc đóng **một hướng** (*half-close*) bằng `shutdownOutput()` / `shutdownInput()`. Chọn sai API có thể làm server chờ mãi, làm client không nhận được phản hồi, hoặc cắt mất dữ liệu.

Các mục tiêu của bài: đọc đúng kết quả `-1/0/>0`, hiểu ý nghĩa của TCP FIN, phân biệt full close với half-close, viết giao thức request/response kết thúc bằng EOF an toàn, và xử lý vòng đời trong blocking/non-blocking I/O.

## 2. SocketChannel.read() trả về gì?

Với một `ByteBuffer` còn chỗ nhận dữ liệu:

~~~java
int n = channel.read(buffer);
~~~

Đây là một lời gọi minh họa; `channel` phải là `SocketChannel` đã kết nối, `buffer` là vùng đệm hợp lệ. Ý nghĩa kết quả:

| Kết quả | Ý nghĩa | Hành động thường phù hợp |
| --- | --- | --- |
| `n > 0` | Đã nhận `n` byte và `buffer.position()` tăng tương ứng | Xử lý bytes và tiếp tục theo giao thức |
| `n == 0` | Không chuyển byte trong lần gọi này | Ở non-blocking: đợi readiness tiếp theo; đừng coi là EOF |
| `n == -1` | Hướng nhận đã gặp **EOF**; không còn byte nào tiếp theo trong luồng đó | Xử lý khung cuối, hoàn tất đọc hoặc chuyển trạng thái giao thức |
| Ném `IOException` | Lỗi I/O, ngắt kết nối bất thường hoặc đóng đồng thời... | Xử lý lỗi theo loại và cleanup, không tự biến thành EOF |

**Buffer trống chỗ đọc cũng có thể khiến kết quả bằng 0:** nếu `buffer.remaining() == 0`, thao tác `read` không có nơi chứa byte. Vì thế phải bảo đảm buffer ở trạng thái nhận dữ liệu (thường sau `clear()` hoặc `compact()`). Một `read` trả 0 khi buffer đầy không nói điều gì về trạng thái TCP.

Ở chế độ **blocking**, với buffer có chỗ, `read` thông thường chờ có dữ liệu, EOF hoặc lỗi. Ở chế độ **non-blocking**, khi chưa có dữ liệu khả dụng, `read` có thể trả `0` ngay lập tức. Không viết vòng lặp `while (channel.read(buf) == 0) { }` để quay CPU liên tục; sử dụng `Selector` hoặc bộ điều phối phù hợp.

EOF là tín hiệu **theo hướng**. Khi server nhận `-1` từ client, điều đó nói rằng client không còn gửi thêm byte trên hướng client → server, **không** có nghĩa server đã mất quyền gửi dữ liệu phản hồi trên hướng server → client.

### EOF không phải một byte đặc biệt trong ByteBuffer

`-1` là **giá trị trả về của phương thức đọc**, không phải byte 0xFF nằm trong dữ liệu. TCP mang byte nhị phân tùy ý, kể cả `0xFF`, `0x00`, và chuỗi rỗng. Không tạo một byte “EOF” để chèn vào payload rồi mong `SocketChannel.read` tự nhận ra. Trong ứng dụng, EOF xảy ra do trạng thái kết thúc hướng gửi của kết nối.

## 3. TCP half-close: shutdownOutput() khác close()

Hãy hình dung kết nối hai chiều:

~~~text
Client                                   Server
   │ ---------- request bytes ----------> │
   │ ---------- FIN (write EOF) ---------> │
   │ <--------- response bytes ----------- │
   │ <--------- FIN (write EOF) ---------- │
   │                                       │
   └── close()                    close() ─┘
~~~

Sau khi client đã gửi đủ request, `client.shutdownOutput()` kết thúc **hướng ghi của client**. Với TCP bình thường, phía server sẽ đọc hết request rồi nhận `-1`; nhưng client vẫn có thể đọc response trên hướng ngược lại. Server có thể thực hiện tương tự sau khi gửi response.

So sánh:

| Phương thức | Hướng bị ảnh hưởng tại phía gọi | Sau đó còn làm được gì? |
| --- | --- | --- |
| `channel.shutdownOutput()` | Kết thúc **ghi** của chính channel | Vẫn có thể `read()` từ phía đối tác; `write()` tiếp sẽ ném `ClosedChannelException` |
| `channel.shutdownInput()` | Kết thúc **đọc** của chính channel | Các lần đọc tiếp trả `-1`; vẫn có thể `write()` nếu output chưa shutdown |
| `channel.close()` | Đóng toàn bộ channel và tài nguyên gắn với nó | Không tiếp tục đọc/ghi channel; `isOpen()` thành `false` |
| Đối tác gọi `shutdownOutput()` | Đối tác ngừng gửi vào hướng mình đang đọc | Sau khi bytes đang chờ hết, `read()` ở mình trả `-1`; có thể tiếp tục gửi ngược |
| Đối tác đóng kết nối bình thường | Có thể thấy EOF khi dữ liệu cũ đã đọc hết | Không giả định mình luôn gửi tiếp được; vòng đời đã kết thúc theo phía đối tác |

**Đặc điểm cốt lõi:** `shutdownOutput()` không đóng toàn bộ `SocketChannel`. Sau lời gọi thành công, `channel.isOpen()` vẫn có thể là `true`. Trạng thái “channel còn mở” không đồng nghĩa “cả hai chiều vẫn cho phép đọc/ghi”.

Gọi `shutdownOutput()` thêm lần nữa khi output đã shutdown không có tác dụng. Nhưng nếu channel đã đóng toàn bộ thì `shutdownOutput()` ném `ClosedChannelException`; nếu chưa kết nối, có thể ném `NotYetConnectedException`.

Cần phân biệt **thông báo kết thúc luồng** với **xác nhận nghiệp vụ**. TCP FIN theo sau những byte đã được bên gửi chuyển xuống ngăn xếp TCP theo thứ tự; nó không phải sự xác nhận rằng server đã xử lý, ghi database hoặc cam kết giao dịch thành công. Muốn xác nhận hoàn tất, giao thức ứng dụng cần phản hồi riêng.

## 4. Ví dụ Java 17+ hoàn chỉnh: gửi request, half-close, đọc response

Ví dụ mở `ServerSocketChannel` tại địa chỉ loopback với cổng động (port 0), nên không cần máy chủ bên ngoài và giảm khả năng đụng cổng. Client gửi `ping`, gọi `shutdownOutput()`, chờ server đọc tới EOF và phản hồi `pong`. Server cũng gọi `shutdownOutput()` để client nhận EOF của response.

Tạo file `HalfCloseDemo.java`:

~~~java
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class HalfCloseDemo {
    public static void main(String[] args) throws Exception {
        try (ServerSocketChannel listener = ServerSocketChannel.open()) {
            listener.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<?> task = executor.submit(() -> {
                    try (SocketChannel peer = listener.accept()) {
                        String request = readUntilEof(peer, "Server");
                        System.out.println("Server request: " + request);
                        writeAll(peer, "pong");
                        peer.shutdownOutput();
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                });

                try (SocketChannel client = SocketChannel.open()) {
                    client.connect(listener.getLocalAddress());
                    writeAll(client, "ping");
                    client.shutdownOutput();
                    String response = readUntilEof(client, "Client");
                    System.out.println("Client response: " + response);
                }
                task.get();
            } finally {
                executor.shutdownNow();
            }
        }
    }

    private static void writeAll(SocketChannel channel, String text)
            throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    private static String readUntilEof(SocketChannel channel, String side)
            throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        while (true) {
            int n = channel.read(buffer);
            if (n == -1) {
                System.out.println(side + " EOF: -1");
                break;
            }
            if (n == 0) {
                continue;
            }
            buffer.flip();
            byte[] part = new byte[buffer.remaining()];
            buffer.get(part);
            bytes.write(part);
            buffer.clear();
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac HalfCloseDemo.java
java HalfCloseDemo
~~~

Đầu ra chính xác đã được kiểm tra bằng cách biên dịch và chạy:

~~~text
Server EOF: -1
Server request: ping
Client EOF: -1
Client response: pong
~~~

**Giải thích vòng đời:**

1. Listener được bind vào loopback; `listener.getLocalAddress()` trả địa chỉ/cổng thực tế để client kết nối. Các `SocketChannel` mặc định ở chế độ blocking.
2. Một tác vụ trên thread riêng gọi `listener.accept()` để nhận socket server. `Future.get()` ở cuối giúp thread chính chờ server hoàn tất và truyền lỗi tác vụ server về thread chính.
3. Client gọi `writeAll` để bảo đảm `ByteBuffer` đã được channel tiêu thụ hết; `write` **có thể ghi ít hơn số byte còn lại** nên phải lặp cho đến khi `hasRemaining()` là false.
4. `client.shutdownOutput()` báo kết thúc hướng gửi. Server `readUntilEof` đọc tất cả byte; chỉ dừng khi `read` trả `-1`, sau đó in request `ping`.
5. Server vẫn có thể gọi `writeAll(peer, "pong")` dù hướng nhận của nó đã EOF. Đây chính là lợi ích của half-close. Sau khi gửi response, server `shutdownOutput()`.
6. Client nhận `pong` và tiếp tục đọc tới EOF ở chiều ngược. Cuối cùng `try-with-resources` đóng hai `SocketChannel` và listener.
7. Ví dụ chỉ minh họa giao thức **một request và một response, kết thúc bằng EOF**; không phải protocol dùng lại cùng kết nối cho nhiều lượt gọi.

`readUntilEof` gom byte rồi mới giải mã UTF-8. Làm vậy tránh tình huống một ký tự đa byte bị tách qua hai lần `read`. Với payload lớn, tránh gom tất cả vào bộ nhớ: giải mã UTF-8 theo luồng hoặc ghi trực tiếp ra tệp/consumer có giới hạn kích thước.

Vòng `n == 0` trong ví dụ không busy-spin trong tình huống bình thường vì channel đang ở chế độ blocking với buffer còn chỗ. **Không chuyển nguyên xi vòng lặp này sang non-blocking mode**; hãy dùng `Selector` và quản lý readiness.

## 5. shutdownInput(): dừng nhận tại phía cục bộ

`channel.shutdownInput()` dừng **hướng đọc của chính channel** mà không nhất thiết đóng hướng gửi. Theo API `SocketChannel`, sau khi shutdown input, các lần `read` tiếp theo trả `-1`. Nếu thread khác đang block trong `read` trên cùng channel, thao tác bị đánh thức và hoàn thành với kết quả `-1` theo hợp đồng của `SocketChannel`.

Ví dụ minh họa, khi `channel` đã kết nối:

~~~java
channel.shutdownInput();
int n = channel.read(ByteBuffer.allocate(8));
System.out.println(n); // -1
~~~

Đây chỉ là đoạn code minh họa cần đặt trong method và xử lý `IOException`.

Điều **không được hiểu nhầm**: gọi `shutdownInput()` ở client không phải lời nhắn “request của tôi đã gửi xong” tới server. Để bên server quan sát EOF của **luồng client gửi đi**, client dùng `shutdownOutput()`. `shutdownInput()` là thay đổi trạng thái đọc cục bộ; dữ liệu chưa đọc có thể bị bỏ đi theo cách của hệ thống, và ứng dụng không nên dùng nó để cố gắng “đọc hết rồi khôi phục” cùng hướng sau đó.

Nếu client cố tình từ chối đọc thêm response, việc đóng input khi server vẫn đang ghi có thể có hậu quả mạng khác tùy nền tảng. Đừng tự giả định server sẽ luôn được báo ngay hoặc việc ghi từ server được bảo đảm thành công.

## 6. Đóng toàn bộ channel: close() không phải half-close

`channel.close()` đóng tài nguyên channel. Trong code sử dụng `try-with-resources`, `close()` được gọi khi thoát khối dù có exception. Đóng hoàn toàn là lựa chọn đúng khi ứng dụng **không còn nhu cầu đọc hoặc ghi** trên kết nối.

~~~java
try (SocketChannel channel = SocketChannel.open(address)) {
    // Trao đổi request/response hoàn chỉnh theo giao thức.
} // close() tại đây
~~~

Đây là đoạn minh họa trong method có `address` hợp lệ và xử lý ngoại lệ. Thứ tự kết thúc thông thường:

1. Hoàn tất phần dữ liệu ứng dụng cần gửi; nếu protocol cần một marker kết thúc request theo hướng gửi, ghi marker/length hoặc gọi `shutdownOutput()` theo hợp đồng protocol.
2. Nếu còn cần response, tiếp tục đọc cho tới khi nhận đủ response theo protocol, có thể là số byte được khai báo hoặc EOF do server.
3. Chỉ khi không cần thêm dữ liệu, đóng toàn bộ channel bằng `close()`.

Nếu client `close()` **ngay sau ghi request** trong một protocol cần chờ response, client có thể mất cơ hội đọc response. Tùy trạng thái socket, hệ điều hành và dữ liệu còn tồn đọng, phía kia có thể thấy EOF hoặc lỗi reset; không nên cam kết “close sẽ luôn gửi FIN và mọi byte sẽ đến nơi”.

Một số ngoại lệ/hiện tượng khi đóng:

- Khi một thread đang chặn ở thao tác I/O và một thread khác đóng channel, thao tác có thể kết thúc bằng `AsynchronousCloseException`.
- Nếu thread đang block trong I/O bị interrupt, channel có thể đóng và báo `ClosedByInterruptException` theo hợp đồng `InterruptibleChannel`.
- Sau full close, `isOpen()` là `false`; các thao tác I/O không thể tiếp tục và thường báo `ClosedChannelException`.
- `shutdownOutput()` chỉ đóng hướng ghi: viết tiếp sau đó ném `ClosedChannelException` **ngay cả khi `isOpen()` còn true**.
- TCP reset từ peer có thể hiển thị như `SocketException`/`IOException` thay vì EOF; hãy log loại lỗi và trạng thái giao thức.

Khác với `SocketChannel` đã kết nối, `ServerSocketChannel` là **listener nhận kết nối**. Nó không có “hướng gửi request và hướng nhận response” trên một kết nối cụ thể; dùng `close()` để ngừng accept và giải phóng listener.

## 7. EOF trong blocking và non-blocking I/O

### 7.1. Vòng đọc blocking chuẩn

Với channel blocking và buffer được chuẩn bị đúng:

~~~java
ByteBuffer buf = ByteBuffer.allocate(4096);
while (true) {
    int n = channel.read(buf);
    if (n == -1) {
        break; // EOF: peer kết thúc hướng gửi.
    }
    if (n == 0) {
        continue; // Không coi là EOF.
    }
    buf.flip();
    // Tiêu thụ các byte trong [position, limit) của buf.
    buf.clear();
}
~~~

Đây là **đoạn minh họa**, không phải chương trình độc lập. Phải thay phần “tiêu thụ” bằng xử lý thực sự trước `clear()`. `clear()` chỉ đặt lại cursor, **không xóa dữ liệu**. Nếu decoder nhận chưa đủ một frame, cần `compact()` để giữ byte còn dở thay vì bỏ hết.

### 7.2. State machine với Selector

Trong non-blocking mode, một event `SelectionKey.OP_READ` có thể có nghĩa **có dữ liệu để đọc hoặc đã đến trạng thái EOF/lỗi**. Handler cần gọi `channel.read(...)` và phân nhánh theo giá trị, không xem readiness là bằng chứng luôn có `n > 0`.

Mẫu điều khiển khái niệm:

~~~java
if (key.isReadable()) {
    SocketChannel ch = (SocketChannel) key.channel();
    int n = ch.read(receiveBuffer);
    if (n > 0) {
        // Decode / tích lũy frame; chờ event kế tiếp nếu chưa đủ.
    } else if (n == 0) {
        // Chưa có byte: trở về selector, không busy-spin.
    } else { // n == -1
        key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
        // Đánh dấu input EOF; xử lý request cuối/response đang chờ.
        // Chỉ đóng channel nếu cả hai chiều đã hoàn tất.
    }
}
~~~

Đây là **mã minh họa trong event loop**, giả định `key` hợp lệ, `receiveBuffer` đã được chuẩn bị và các exception được xử lý bên ngoài. `SelectionKey` và `SocketChannel` có thể được import từ `java.nio.channels`.

Nếu sau EOF vẫn luôn để `OP_READ` và lập tức đọc lại mà không chuyển trạng thái, một số hệ thống sẽ tiếp tục báo channel ready liên tục, dẫn tới CPU cao. Cần **ngừng quan tâm đọc**, kết thúc xử lý hướng nhận hoặc đóng kết nối sau khi hướng gửi còn lại hoàn tất.

Với non-blocking `write`, `n == 0` cũng không phải lỗi hay “đã gửi hết”; thường phải giữ buffer còn `hasRemaining()` và đợi `OP_WRITE` khi có dữ liệu chờ. Không đăng ký `OP_WRITE` liên tục nếu không có bytes cần gửi vì socket có thể liên tục writable.

### 7.3. Parser phải phân biệt EOF với frame cuối

TCP là **byte stream**, không bảo toàn ranh giới từng lần `write` thành từng lần `read`. Một request `"hello"` có thể đến qua nhiều lần đọc; nhiều request có thể cùng nằm trong một lần đọc.

Khi EOF tới:

- Nếu protocol dựa trên “đọc đến khi bên gửi đóng hướng output”, EOF đánh dấu kết thúc payload hợp lệ.
- Nếu protocol dùng độ dài cố định/length-prefix và EOF tới khi còn thiếu byte, phải báo **truncated frame**, không nhận phần thiếu làm request hợp lệ.
- Nếu protocol có nhiều frame trên một kết nối lâu dài, EOF thường nói rằng phía kia đã kết thúc hướng gửi, **không** phân chia các frame trước đó; parser vẫn phải xử lý ranh giới theo protocol.
- Khi không có EOF hoặc delimiter/length-prefix, server không thể biết chắc request đã kết thúc chỉ vì một lần `read` trả `0` hoặc client tạm dừng gửi.

Đừng dùng `buffer.hasRemaining()`, `channel.isConnected()` hay `channel.isOpen()` như phép thử thay thế `read() == -1` cho việc peer đã gửi EOF.

## 8. Vì sao server hoặc client bị treo khi chờ EOF?

Ví dụ protocol kiểu “client gửi toàn bộ request, server đợi EOF rồi gửi response”. Nếu client làm như sau:

~~~text
Client: write(request) → read(response)
Server: readUntilEOF(request) → write(response)
~~~

cả hai có thể **chờ nhau vô hạn**:

- Client đang chờ response nên không kết thúc hướng gửi.
- Server đang chờ EOF của request nên chưa tạo response.

Sửa theo giao thức half-close:

~~~text
Client: write(request) → shutdownOutput() → read(response)
Server: readUntilEOF(request) → write(response) → shutdownOutput()
~~~

Một cách khác là thiết kế **framing không cần half-close**:

~~~text
[4 bytes length][N bytes request] → server biết đọc đúng N byte
[4 bytes length][M bytes response] → client biết đọc đúng M byte
~~~

Framing theo length giúp dùng lại kết nối cho nhiều request/response, pipelining hoặc multiplexing. Half-close phù hợp với các protocol có **một luồng dữ liệu kết thúc thật sự**, nơi không cần gửi thêm gì theo hướng đó. Nếu client cần gửi request thứ hai sau khi nhận response thứ nhất, **không** gọi `shutdownOutput()` sau request đầu trong cùng socket.

### Timeout và cancellation

Một client/server trong production không nên chờ vô hạn khi peer không gửi đủ frame hoặc không bao giờ half-close. Với `Selector` có thể quản lý deadline và hủy registration/đóng channel khi hết hạn; với blocking channel cần chính sách timeout/hủy phù hợp (thread interrupt, đóng channel được điều phối, hoặc cơ chế cấp cao hơn). Không tự giả định `Socket.setSoTimeout(...)` sẽ áp dụng đúng như một timeout cho mọi lời gọi `SocketChannel.read(...)`; chọn API timeout phù hợp với mode và cấu trúc ứng dụng thực tế.

## 9. Shutdown và race giữa các thread

Theo Java API, `SocketChannel` có thể được dùng bởi nhiều thread cho đọc và ghi đồng thời, nhưng **tối đa một thread đọc và một thread ghi tại một thời điểm**. Nếu thread A đang `read()` và thread B gọi `shutdownInput()`, lời gọi read đang bị chặn được hoàn thành với `-1`. Nếu thread A đang `write()` và B gọi `shutdownOutput()`, thao tác write đang bị chặn có thể kết thúc bằng `AsynchronousCloseException` theo hợp đồng.

Các API này quản lý vòng đời hướng truyền, **không** làm cho một `ByteBuffer` chia sẻ trở thành thread-safe. Nếu reader và writer cùng thao tác một buffer hoặc nhiều worker sửa `position/limit` của cùng buffer, vẫn phải phân quyền hoặc đồng bộ.

Khuyến nghị về ownership:

- Mỗi kết nối có một bộ điều phối vòng đời xác định trạng thái `OPEN`, `OUTPUT_SHUTDOWN`, `INPUT_EOF`, `FULLY_CLOSED` phù hợp; các trạng thái hướng gửi/nhận có thể kết hợp và xảy ra theo nhiều thứ tự.
- Chỉ gọi `shutdownOutput()` **sau khi tất cả byte cần gửi đã được chấp nhận bởi channel**; với non-blocking I/O phải giữ phần ghi còn lại, không lấy “đã gọi write một lần” làm hoàn tất.
- Các tác vụ đọc phải được thông báo khi output hay toàn connection sắp đóng nếu kết quả công việc phụ thuộc response.
- Chỉ `close()` khi không còn pending read/write quan trọng, hoặc cố ý hủy chúng và chấp nhận lỗi hủy.
- Sau khi nhận EOF, hướng nhận đã kết thúc; nếu ứng dụng vẫn cần gửi response, không tự động full-close ở event handler nhận EOF.

Hãy bảo đảm cleanup dùng `try-with-resources`, `finally` hoặc quản lý lifecycle tương đương. Khi hủy nhiều tác vụ async, tránh các lời gọi đóng kép và lối đi có thể bỏ sót giải phóng selector/key/channel.

## 10. EOF bình thường, reset, timeout và các ngoại lệ

| Quan sát | Không nên hiểu là | Cách xử lý gợi ý |
| --- | --- | --- |
| `read() == -1` | Không phải “chưa có dữ liệu” | Đánh dấu peer ngừng gửi ở hướng đó; xử lý kết thúc thông điệp |
| `read() == 0` | Không phải EOF | Xem buffer còn chỗ, mode blocking/non-blocking và chờ readiness |
| `ClosedChannelException` | Không phải bằng chứng chắc peer gửi FIN | Kiểm tra chính mình đã close/shutdownOutput hoặc code đóng đồng thời |
| `AsynchronousCloseException` | Không phải luôn là lỗi mạng | Xem thread khác đã đóng/shutdown trong lúc I/O bị chặn |
| `ClosedByInterruptException` | Không phải FIN từ phía đối tác | Kiểm tra chính sách interrupt/cancellation |
| `SocketException: Connection reset` (thông điệp tùy nền tảng) | Không phải EOF bình thường | Xử lý lỗi peer/mạng; không giả định payload đã hoàn tất |
| Đã `write()` hết buffer | Không chứng minh peer đã nhận và xử lý | Cần acknowledgment tầng ứng dụng nếu nghiệp vụ yêu cầu |
| `channel.isOpen() == true` | Không chứng minh cả hai chiều còn hoạt động | Theo dõi trạng thái input/output và protocol riêng |

Một connection reset (RST), mạng đứt, máy đối tác crash hoặc lỗi TLS có thể không cho EOF bình thường. Phân biệt graceful EOF và exceptional termination rất quan trọng khi cập nhật trạng thái nghiệp vụ: không đánh dấu “đã hoàn tất thành công” nếu payload chưa thỏa framing và checksum/application acknowledgment.

## 11. Ghi chú khi dùng TLS và giao thức tầng trên

Ví dụ trong bài là **TCP thuần**. Với TLS, ý nghĩa đóng ở tầng ứng dụng còn liên quan đến TLS `close_notify` và cơ chế của `SSLEngine`/thư viện TLS. Không tự coi TCP FIN là bằng chứng đã nhận một thông điệp TLS đóng hợp lệ. Việc đóng thiếu `close_notify` có thể là *truncation* đối với một số giao thức; mỗi ứng dụng phải tuân thủ contract của tầng mã hóa và protocol.

Tương tự, giao thức HTTP có length/chunked framing hoặc những quy tắc riêng về connection persistence; không thể quyết định “đã nhận đủ HTTP response” chỉ bằng việc chờ TCP EOF trong mọi trường hợp. Dùng parser/framing của giao thức thay vì giả định đóng kết nối sau mỗi message.

## 12. Lỗi phổ biến và cách tránh

1. **Coi `read() == 0` là đã đóng:** non-blocking 0 chỉ biểu thị chưa có byte trong lần đọc này, hoặc không có chỗ trong buffer.
2. **Bỏ qua `-1` rồi tiếp tục đăng ký `OP_READ`:** dễ tạo vòng lặp readiness/EOF và đốt CPU.
3. **Chờ EOF mà không `shutdownOutput()` khi protocol yêu cầu:** tạo deadlock client chờ response/server chờ request kết thúc.
4. **Gọi `close()` ngay sau khi gửi request cần response:** mất khả năng đọc chiều ngược.
5. **Gọi `shutdownInput()` để báo server đã gửi xong request:** sai chiều; phải dùng `shutdownOutput()`.
6. **Dùng `shutdownOutput()` rồi cố gửi thêm dữ liệu:** output đã shutdown, write tiếp ném `ClosedChannelException`.
7. **Giả định một `write` gửi đủ hoặc một `read` nhận đủ message:** TCP không giữ ranh giới thông điệp; cần vòng đọc/ghi và framing.
8. **Dùng half-close cho giao thức nhiều request trên cùng chiều:** một khi đã shutdown output, không thể mở lại chiều gửi đó trên cùng channel.
9. **Coi TCP EOF là thành công nghiệp vụ:** EOF chỉ nói rằng hướng gửi kết thúc theo quan sát mạng; cần xác thực payload và phản hồi ứng dụng.
10. **Không quản lý giới hạn kích thước/deadline:** peer có thể gửi quá lớn hoặc không bao giờ kết thúc; production cần giới hạn tài nguyên và timeout.

## 13. Câu hỏi tự kiểm tra

1. Trong non-blocking mode, `read(buffer) == 0` có chứng minh server đã đóng kết nối? **Không**, đây là lần gọi chưa nhận byte (hoặc buffer không còn chỗ), không phải EOF.
2. Client gửi request rồi gọi `shutdownOutput()`. Client có đọc response tiếp được không? **Có**, vì chỉ hướng ghi client bị đóng.
3. Server đọc được `-1`, nhưng vẫn phải trả một kết quả tính toán. Có được gọi `write()` không? **Có**, nếu hướng gửi của server vẫn mở và kết nối không bị đóng/lỗi.
4. Vì sao `shutdownInput()` không thay thế `shutdownOutput()` để thông báo EOF request cho server? Vì `shutdownInput()` dừng **hướng nhận cục bộ**, còn server đang chờ kết thúc **hướng gửi từ client**.
5. Sau `shutdownOutput()`, `isOpen()` có thể vẫn true nhưng `write()` hoạt động không? **Không**, viết tiếp ném `ClosedChannelException`.
6. Client và server cùng chờ `readUntilEOF()` mà không bên nào đóng hướng output: điều gì xảy ra? **Có thể chờ nhau vô hạn**, trừ khi giao thức hoặc một sự kiện hủy/timeout kết thúc tình huống.
7. EOF có luôn xảy ra khi cáp mạng đứt hoặc peer bị kill không? **Không**. Có thể timeout, reset hoặc `IOException`; cũng có thể không có tín hiệu tức thì.
8. Một lời gọi `SocketChannel.read()` trả 5 byte có nghĩa peer chỉ gửi một message dài 5 byte? **Không**. TCP là byte stream; 5 byte có thể là một phần message hay một phần của nhiều message.
9. Nếu server gửi response theo length-prefix, client nhất thiết phải chờ `-1` mới hoàn tất response không? **Không**. Có thể hoàn tất khi đã nhận đủ frame theo độ dài khai báo.
10. Khi dùng `Selector`, nhận `-1` có nên cứ giữ `OP_READ` và đọc lại vô hạn? **Không**. Cần chuyển trạng thái và hủy quan tâm đọc hoặc đóng khi thích hợp.

## Tài liệu API tham khảo

- [Java SE 17 — SocketChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/SocketChannel.html): `read`, `write`, `shutdownInput`, `shutdownOutput`, đồng bộ khi dùng nhiều thread và kết nối.
- [Java SE 17 — ReadableByteChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/ReadableByteChannel.html): ý nghĩa kết quả đọc `n > 0`, `0`, `-1`.
- [Java SE 17 — ServerSocketChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/ServerSocketChannel.html): bind, accept và đóng listener.
- [Java SE 17 — SelectionKey](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/SelectionKey.html): `OP_READ`, `OP_WRITE`, trạng thái selection.
- [Java SE 17 — ByteBuffer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html): `flip`, `clear`, `compact` và thao tác buffer.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nio-networking-008">Datagram và khác biệt với luồng TCP</a>

<details>
<summary>Xem chi tiết</summary>

# Datagram và khác biệt với luồng TCP trong Java NIO

## 1. Vấn đề cần hiểu

Khi truyền dữ liệu qua mạng, ứng dụng có thể cần gửi **từng thông điệp độc lập** hoặc duy trì **một luồng byte liên tục**. Hai mô hình thường gặp:

- **Datagram:** mỗi lần gửi tạo một gói/thông điệp có ranh giới riêng. Trên Internet, mô hình quen thuộc là **UDP** (User Datagram Protocol); Java NIO cung cấp `DatagramChannel`.
- **TCP stream:** TCP truyền một **luồng byte có thứ tự** giữa hai đầu đã kết nối, không gắn ý nghĩa ranh giới thông điệp vào từng lời gọi ghi. Java NIO thường dùng `SocketChannel` và `ServerSocketChannel`.

Sự khác biệt quan trọng không chỉ là “UDP nhanh, TCP chậm”. Cần hiểu **ranh giới thông điệp, kết nối, độ tin cậy, thứ tự, hành vi đọc/ghi, kích thước dữ liệu, điều khiển luồng, bảo mật và cách thiết kế giao thức ứng dụng**.

Mục tiêu bài này:

1. Hiểu datagram là gì, mỗi datagram mang địa chỉ đích/nguồn như thế nào.
2. Dùng `DatagramChannel.send`, `receive`, `bind`, `connect` trong Java NIO.
3. Phân biệt datagram với byte stream mà `SocketChannel` đọc/ghi.
4. Xử lý `ByteBuffer`, chế độ blocking/non-blocking và `Selector`.
5. Chọn UDP/datagram hoặc TCP phù hợp và nhận biết các lỗi thiết kế phổ biến.

> **Phạm vi:** đây là bài về Java core I/O/NIO, tập trung vào semantics của datagram UDP và TCP stream. UDP không đồng nghĩa với mọi giao thức dựa trên UDP; chẳng hạn một giao thức tầng trên có thể tự bổ sung xác thực, truyền lại hoặc kiểm soát tắc nghẽn.

## 2. Hai mô hình nhìn từ ứng dụng

### UDP datagram: từng thông điệp riêng

Một ứng dụng gửi ba datagram:

```text
Sender                         Network                      Receiver

Datagram #1: ["ABC"] ---------\
Datagram #2: ["DEF"] ----------> [có thể mất/đảo thứ tự] ---> receive(): một datagram
Datagram #3: ["GHI"] ---------/

Ranh giới ["ABC"], ["DEF"], ["GHI"] thuộc từng gói.
```

Một lần `DatagramChannel.send(buffer, address)` gửi nội dung remaining như **một datagram**. Một lần `receive(buffer)` nhận **một datagram** (nếu có) và trả địa chỉ của bên gửi. Không ghép hai datagram thành một lời gọi `receive` và không chia một datagram thành các phần để các lần `receive` sau tự nhận tiếp.

Tuy nhiên, datagram có thể **bị mất, đến trễ, bị nhân bản hoặc đến không theo thứ tự**. Sender không nhận được cam kết delivery chỉ từ việc `send` trả về số byte đã gửi. Chương trình muốn reliability phải tự xây cơ chế hoặc chọn giao thức tầng trên thích hợp.

### TCP: một luồng byte có thứ tự

Ứng dụng TCP gọi ghi ba lần:

```text
Sender SocketChannel                      Receiver SocketChannel

write("ABC")
write("DEF")  ----->  TCP byte stream -----> read() có thể trả:
write("GHI")                                "ABCDEF", hoặc
                                            "AB" rồi "CDEFGHI", hoặc
                                            các phân đoạn khác
                        Nội dung logic: "ABCDEFGHI"
```

TCP đảm bảo thứ tự byte và cơ chế truyền lại/kiểm soát lỗi khi kết nối hoạt động bình thường, nhưng **không giữ ranh giới giữa các lần `write`**. Một lần `read` có thể trả một phần thông điệp, nhiều thông điệp hoặc không có byte ở chế độ non-blocking. Ứng dụng phải tự định nghĩa framing, chẳng hạn tiền tố độ dài, delimiter, hoặc định dạng tự mô tả.

**Điểm cốt lõi:** UDP giữ ranh giới gói ở API nhưng không bảo đảm giao hàng; TCP hỗ trợ luồng byte tin cậy có thứ tự trong kết nối, nhưng không giữ ranh giới thông điệp ứng dụng.

## 3. Bảng so sánh datagram UDP và TCP stream

| Tiêu chí | UDP / DatagramChannel | TCP / SocketChannel |
|---|---|---|
| Mô hình dữ liệu | Datagram độc lập, mỗi gói có ranh giới | Luồng byte liên tục |
| Thiết lập kết nối | Không bắt buộc handshake để gửi UDP | TCP cần kết nối và bắt tay trước khi trao đổi thông thường |
| API gửi NIO | `send(ByteBuffer, SocketAddress)`; khi connected có `write` | `write(ByteBuffer)` trên kết nối TCP |
| API nhận NIO | `receive(ByteBuffer)` trả `SocketAddress` của gói | `read(ByteBuffer)` trả số byte, `-1` khi EOF của stream |
| Ranh giới thông điệp | Giữ theo datagram; gói quá lớn so với receive buffer bị cắt mất phần dư | Không có ranh giới theo từng lần write; ứng dụng tự framing |
| Bảo đảm dữ liệu tới đích | Không có bảo đảm ở tầng UDP | Có truyền lại, thứ tự và cơ chế kiểm soát lỗi trong kết nối còn hoạt động |
| Thứ tự/nhân bản | Có thể đảo thứ tự, trùng hoặc mất gói | Ứng dụng thấy byte đúng thứ tự; không tự hiển thị dữ liệu lặp do retransmission |
| Kích thước | Mỗi datagram có giới hạn; MTU và fragmentation quan trọng | Byte stream không có khái niệm “một message kích thước MTU” ở API |
| Flow/congestion control | UDP không cung cấp sẵn bảo đảm tương đương TCP | TCP có flow control và congestion control |
| Phát hiện peer không khả dụng | Khó và không đồng nhất; `send` thành công không chứng minh peer nhận | Kết nối có trạng thái, có thể thấy EOF/lỗi, nhưng phát hiện sự cố vẫn có thể bị trễ |
| Broadcast/multicast | UDP phù hợp với các cơ chế này nếu OS/network hỗ trợ | TCP unicast kết nối giữa hai endpoint |
| Selector | `DatagramChannel` hỗ trợ `OP_READ`, `OP_WRITE` | `SocketChannel` hỗ trợ `OP_READ`, `OP_WRITE`, `OP_CONNECT` |
| Khi nào thường chọn | Telemetry, discovery, game state, real-time với chính sách mất gói rõ ràng | HTTP thông thường qua TCP, RPC theo stream, chuyển file, dữ liệu cần truyền đủ có thứ tự |

Bảng này mô tả **UDP/TCP ở mức transport**. Một giao thức ứng dụng trên UDP có thể xây thêm reliability, ví dụ truyền lại và kiểm soát congestion. Khi đó ứng dụng **không còn dựa vào UDP thuần** để đạt các tính năng đó.

## 4. `DatagramChannel` trong Java NIO

`java.nio.channels.DatagramChannel` là selectable channel để gửi/nhận các datagram. Một số API cốt lõi:

| API | Ý nghĩa |
|---|---|
| `DatagramChannel.open()` | Tạo channel datagram với protocol family mặc định do hệ thống lựa chọn |
| `DatagramChannel.open(StandardProtocolFamily.INET)` | Tạo channel cho IPv4 |
| `bind(SocketAddress local)` | Gắn socket vào địa chỉ/cổng cục bộ; truyền `null` để hệ thống chọn |
| `getLocalAddress()` | Đọc địa chỉ/cổng cục bộ khi đã bind |
| `send(ByteBuffer src, SocketAddress target)` | Gửi một datagram tới target |
| `receive(ByteBuffer dst)` | Nhận một datagram, trả địa chỉ sender; có thể trả `null` nếu channel non-blocking và chưa có gói |
| `connect(SocketAddress remote)` | Gắn một địa chỉ peer vào UDP channel; không phải TCP handshake |
| `read(ByteBuffer dst)` / `write(ByteBuffer src)` | I/O trên DatagramChannel **đã connect**, theo peer đã định |
| `disconnect()` | Bỏ peer gắn trước đó, không có TCP close handshake |
| `configureBlocking(false)` | Chuyển sang non-blocking, có thể đăng ký với `Selector` |
| `setOption(...)` | Cấu hình socket options có hỗ trợ, ví dụ receive/send buffer |

Thông thường làm việc với `Path` là FileChannel, còn làm việc với địa chỉ mạng là `SocketAddress` / `InetSocketAddress`. Hai khái niệm đều là “channel” nhưng không có cùng API dữ liệu.

### Bind và địa chỉ

Server UDP thường bind vào cổng xác định:

```java
try (DatagramChannel channel =
             DatagramChannel.open(StandardProtocolFamily.INET)) {
    channel.bind(new InetSocketAddress("127.0.0.1", 9000));
    // Nhận datagram gửi tới địa chỉ/cổng này.
}
```

Đây là **đoạn minh họa**, cổng 9000 có thể đang được sử dụng. `127.0.0.1` là loopback IPv4, chỉ để thử giữa các tiến trình trên cùng máy. Muốn lắng nghe trên một địa chỉ mạng thật, cần chọn bind address, firewall và quyền tương ứng.

Có thể dùng cổng `0` để hệ điều hành chọn cổng còn trống, ví dụ `new InetSocketAddress(InetAddress.getLoopbackAddress(), 0)` rồi lấy cổng được gán từ `getLocalAddress()`. Cách này thuận tiện trong bài thử tự động và tránh tranh chấp một số cổng cố định.

### Gửi datagram

```java
ByteBuffer outbound = StandardCharsets.UTF_8.encode("PING");
int bytes = channel.send(
        outbound,
        new InetSocketAddress("127.0.0.1", 9000)
);
```

Đây là đoạn minh họa, cần các import `ByteBuffer`, `StandardCharsets` và `InetSocketAddress` cùng channel đã mở. `send` lấy các byte từ vùng `[position, limit)` của buffer, và khi gửi thành công sẽ tiến buffer position theo số byte gửi.

Với UDP, dữ liệu của một lần send được xử lý như **một datagram**, chứ không phải “một phần stream đang tiếp diễn”. Nếu channel **non-blocking** và không thể gửi ngay, `send` có thể trả `0`. Nếu lỗi mạng/tài nguyên hoặc kích thước gói không hợp lệ, có thể ném `IOException`. Đừng tự nối một lệnh send trả `0` với giả định “phần gói đầu đã được gửi”; phải quản lý retry của **cùng datagram** theo kết quả và trạng thái buffer.

### Nhận datagram

```java
ByteBuffer incoming = ByteBuffer.allocate(2048);
SocketAddress source = channel.receive(incoming);

if (source != null) {
    incoming.flip();
    String text = StandardCharsets.UTF_8.decode(incoming).toString();
    System.out.println(text);
}
```

Đây cũng là đoạn minh họa. `receive` ghi nội dung gói vào buffer từ position hiện tại, cập nhật position, rồi trả địa chỉ người gửi. Khi channel non-blocking, nếu chưa có datagram, `source == null`; khi blocking, lời gọi có thể chờ gói đến.

**Ranh giới quan trọng:** nếu datagram dài hơn `incoming.remaining()`, phần không vừa bị **bỏ** theo semantics nhận datagram; lần receive sau không nhận tiếp “phần đuôi” của gói đó. Vì vậy hãy chọn dung lượng buffer thích hợp với **kích thước message tối đa của giao thức**, kèm chính sách từ chối/kiểm tra message quá dài. `flip()` chuyển buffer sang chế độ đọc phần byte mới nhận, nhưng không làm sống lại phần byte đã bị cắt.

## 5. Ví dụ Java 17+: gửi và nhận qua loopback bằng DatagramChannel + Selector

Ví dụ hoàn chỉnh dùng hai `DatagramChannel` trên cùng máy:

1. Receiver bind vào loopback IPv4 cổng do hệ điều hành chọn.
2. Receiver chuyển sang non-blocking và đăng ký `SelectionKey.OP_READ` với `Selector`.
3. Sender gửi một datagram chứa đúng bốn byte ASCII `PING`.
4. Selector chờ tối đa 3 giây để tránh ví dụ treo vô hạn nếu datagram không đến.
5. Receiver nhận datagram và in nội dung cùng một xác nhận rằng địa chỉ nguồn đã được trả về.

Tạo tệp **`DatagramVsTcpDemo.java`**:

```java
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;

public class DatagramVsTcpDemo {
    public static void main(String[] args) throws IOException {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");

        try (DatagramChannel receiver =
                     DatagramChannel.open(StandardProtocolFamily.INET);
             DatagramChannel sender =
                     DatagramChannel.open(StandardProtocolFamily.INET);
             Selector selector = Selector.open()) {

            receiver.bind(new InetSocketAddress(loopback, 0));
            receiver.configureBlocking(false);
            receiver.register(selector, SelectionKey.OP_READ);

            sender.bind(new InetSocketAddress(loopback, 0));

            SocketAddress destination = receiver.getLocalAddress();
            ByteBuffer outbound = StandardCharsets.US_ASCII.encode("PING");

            int sent = sender.send(outbound, destination);
            if (sent != 4 || outbound.hasRemaining()) {
                throw new IOException("Could not send complete PING datagram");
            }

            if (selector.select(3000) == 0) {
                throw new IOException("Timed out waiting for UDP datagram");
            }

            ByteBuffer incoming = ByteBuffer.allocate(128);
            SocketAddress source = receiver.receive(incoming);
            if (source == null) {
                throw new IOException("No datagram available after select");
            }

            incoming.flip();
            String message =
                    StandardCharsets.US_ASCII.decode(incoming).toString();

            System.out.println("Sent bytes: " + sent);
            System.out.println("Received: " + message);
            System.out.println("Sender identified: " + (source != null));
        }
    }
}
```

Biên dịch và chạy trong thư mục chứa file:

```bash
javac DatagramVsTcpDemo.java
java DatagramVsTcpDemo
```

Trên máy cho phép UDP loopback và datagram được chuyển tới receiver trong thời gian chờ, stdout:

```text
Sent bytes: 4
Received: PING
Sender identified: true
```

Giải thích:

- `StandardProtocolFamily.INET` chọn IPv4; hai channel dùng địa chỉ `127.0.0.1`.
- Cổng `0` nhờ OS cấp một cổng có thể sử dụng. `receiver.getLocalAddress()` trả cổng thật nên ví dụ không phải hardcode một cổng.
- `StandardCharsets.US_ASCII.encode("PING")` tạo buffer có bốn byte cần gửi. `send` tiêu thụ bốn byte, làm `outbound.hasRemaining()` thành `false`.
- Receiver đăng ký `OP_READ`, còn `selector.select(3000)` chờ tối đa ba giây. `DatagramChannel` phải ở non-blocking mode khi đăng ký với selector.
- `receive(incoming)` trả `SocketAddress` của sender nếu lấy được datagram; `incoming.flip()` chuẩn bị đọc đúng số byte vừa nhận.
- Chương trình không in địa chỉ/cổng thật, vì cổng do OS cấp thay đổi theo mỗi lần chạy.
- Không in/chạy một ví dụ TCP riêng; phần khác biệt với TCP được phân tích bằng sơ đồ và quy tắc framing ở dưới.

**Giới hạn:** UDP vốn không bảo đảm delivery. Dù gửi tới loopback thường thành công, không thể khẳng định mọi môi trường đều trả đúng stdout; nếu firewall, OS hoặc tài nguyên mạng chặn, chương trình ném `IOException`, kể cả timeout. Điều này cố ý làm lỗi rõ ràng thay vì treo ở blocking `receive` mãi.

`Selector` báo readiness, không bảo đảm tuyệt đối rằng trong mọi trạng thái hệ thống khi gọi `receive` sẽ chắc chắn có gói; vì vậy ví dụ vẫn kiểm tra `source == null`. Trong ứng dụng thực, sự kiện và tập `selectedKeys()` thường được xử lý trong event loop để phục vụ nhiều channel; với một receiver duy nhất, ví dụ có thể kiểm tra trực tiếp sau khi select trả số dương.

## 6. Đọc/ghi không blocking và Selector

`DatagramChannel` kế thừa `SelectableChannel`. Với `configureBlocking(false)`:

- `receive(dst)` có thể trả `null` nếu chưa có gói.
- `send(src, target)` có thể trả `0` nếu không thể gửi ngay mà không block.
- Có thể đăng ký channel với `Selector` dùng `SelectionKey.OP_READ` và `OP_WRITE`.
- `OP_READ` chỉ báo sẵn sàng nhận theo thông tin tại lúc selector kiểm tra; xử lý phải chấp nhận tình trạng không còn dữ liệu khi thật sự receive.
- Đăng ký `OP_WRITE` thường cần quản lý interest ops cẩn thận vì socket có thể gần như luôn được coi là writable, gây vòng lặp CPU nếu kích hoạt nó liên tục mà không có gói chờ gửi.
- Không dùng `OP_ACCEPT` cho `DatagramChannel` (không có khái niệm accept TCP) hay `OP_CONNECT` như với `SocketChannel` (UDP `connect` không phải kết nối TCP đang chờ hoàn tất).

Cấu trúc event loop thường gặp:

```java
// Mã minh họa: channel đã configureBlocking(false) và đăng ký OP_READ.
while (running) {
    selector.select();

    var iterator = selector.selectedKeys().iterator();
    while (iterator.hasNext()) {
        SelectionKey key = iterator.next();
        iterator.remove();

        if (key.isValid() && key.isReadable()) {
            ByteBuffer packet = ByteBuffer.allocate(2048);
            SocketAddress from = ((DatagramChannel) key.channel())
                    .receive(packet);
            if (from != null) {
                packet.flip();
                // Giải mã đúng một datagram và xử lý from.
            }
        }
    }
}
```

Đây **không phải chương trình chạy độc lập**; `running`, `selector`, cấu hình channel, chính sách quản lý buffer và đóng tài nguyên cần được định nghĩa bên ngoài. Vòng lặp chọn khóa chỉ là ví dụ tổ chức event loop. Trong hệ thống thực phải xử lý exceptions, giới hạn tốc độ, timeouts, malformed packets và shutdown.

Một datagram đến không có nghĩa sender đã thực hiện handshake. UDP server không cần một `ServerSocketChannel.accept()`; chỉ cần channel bind đúng cổng để nhận message.

## 7. Connected DatagramChannel không biến UDP thành TCP

`DatagramChannel.connect(remote)` có thể khiến tên API dễ gây nhầm. Với UDP, `connect` **gắn một địa chỉ peer vào socket**, cho phép I/O kiểu `read(ByteBuffer)` và `write(ByteBuffer)`, và thường chỉ nhận datagram từ peer đó theo ngữ nghĩa channel. Nó **không thực hiện handshake TCP**.

Ví dụ minh họa:

```java
SocketAddress remote = new InetSocketAddress("127.0.0.1", 9000);

try (DatagramChannel channel =
             DatagramChannel.open(StandardProtocolFamily.INET)) {
    channel.connect(remote);

    ByteBuffer request = StandardCharsets.US_ASCII.encode("HELLO");
    channel.write(request);          // Gửi datagram tới peer đã kết nối.

    // channel.read(response) sẽ đọc từ peer này; nếu đang blocking
    // có thể chờ, vì UDP không bảo đảm có response.
}
```

Đoạn này cần các import tương ứng; không tự chạy như một client hoàn chỉnh vì không có server trên cổng 9000.

Sau `connect`:

- `channel.isConnected()` có thể trả `true`, nhưng không chứng minh peer thực sự đang hoạt động.
- `read` và `write` có thể dùng địa chỉ peer đã lưu. Khi unconnected, `read/write` không hợp lệ và có thể ném `NotYetConnectedException`.
- Việc nhận thông điệp được gắn với peer đã kết nối; không hiểu đây là kết nối có session state, sequence number và cơ chế truyền lại kiểu TCP.
- Gọi `disconnect()` bỏ peer đã gắn; channel tiếp tục là datagram channel.
- Trong một số tình huống, thông báo ICMP (chẳng hạn port unreachable) có thể làm `PortUnreachableException` xuất hiện; không dựa vào nó như cơ chế phát hiện peer chết đáng tin cậy.

**UDP connect giúp đơn giản hóa địa chỉ peer**, chứ không tự cung cấp reliable stream.

## 8. Ranh giới datagram và nguy cơ truncate

UDP là giao thức theo message. Giả sử peer gửi datagram chứa `ABCDEFGHIJ` (10 byte), trong khi receiver cấp buffer chỉ còn 4 byte:

```text
Datagram đến: [A B C D E F G H I J]  (10 byte)
Receive buffer remaining: 4 byte

Sau receive:
  Buffer có thể chứa [A B C D]
  [E F G H I J] của datagram này bị bỏ.
```

Phần đuôi **không được gửi vào lần `receive` kế tiếp** như với byte stream. Nó đã mất ở cấp API nhận datagram. Đó là lý do một server UDP phải quy định tối đa message và chọn receive buffer phù hợp.

Nếu giao thức cần payload 1.000 byte, ngoài việc cấp buffer đủ lớn, có thể đặt một header gồm `version`, `messageType`, `messageLength` và checksum/sequence. Sau nhận, kiểm tra tổng byte đã nhận, header và `messageLength` trước khi parse. Tránh tin tưởng độ dài do client tự báo.

### Giới hạn kích thước và MTU

UDP có giới hạn độ dài datagram ở mức giao thức/địa chỉ IP; không thể gửi một buffer tùy ý lớn bao nhiêu cũng được. Dữ liệu gửi qua IP còn chịu **MTU** (Maximum Transmission Unit):

- Datagrams lớn có thể cần IP fragmentation (tùy IPv4/IPv6, cấu hình hệ thống và chính sách đường truyền).
- Mất một fragment có thể khiến cả datagram không tái lắp được, nên datagram lớn dễ thất bại hơn trong một số môi trường.
- Gửi vượt kích thước hỗ trợ có thể gây lỗi như `IOException`/lỗi socket.
- Phần kích thước phù hợp phụ thuộc thực tế network, IPv4/IPv6 và path MTU; không nên lấy một con số lý thuyết làm “message size an toàn trên mọi mạng”.

Đối với hệ thống thực, giữ message ở mức nhỏ phù hợp với đường truyền, hoặc tự thiết kế phân mảnh/reassembly tầng ứng dụng kèm giới hạn bộ nhớ, timeout và xác thực. Đừng mặc định UDP tự đảm bảo việc tách một thông điệp ứng dụng khổng lồ thành các `receive` nhỏ có thể ghép lại.

## 9. Vì sao TCP cần framing?

TCP stream **không giữ ranh giới giữa các lần `write`**. Chẳng hạn server gọi:

```java
socketChannel.write(ByteBuffer.wrap(new byte[]{'A', 'B', 'C'}));
socketChannel.write(ByteBuffer.wrap(new byte[]{'D', 'E', 'F'}));
```

Đây chỉ là đoạn minh họa; cần xử lý short write, quyền socket và vòng đời channel. Client có thể đọc `ABCDEF` trong một lần, hoặc `A` rồi `BCDE` rồi `F`, tùy trạng thái buffer/network. Không được suy ra “mỗi lần client.read là một message”.

Hai cách framing cơ bản:

### Length-prefixed framing

```text
TCP bytes:
[4-byte length N][N-byte payload][4-byte length M][M-byte payload]...

Receiver:
1. Đọc đủ 4 byte length.
2. Giải mã length theo endian cố định.
3. Kiểm tra 0 <= length <= maxMessageSize.
4. Đọc đủ length byte payload.
5. Lặp lại cho message tiếp theo.
```

Ví dụ muốn dùng `ByteBuffer.getInt()` cho length, phải đảm bảo đã nhận đủ bốn byte. Nếu chỉ nhận một hoặc hai byte, lưu chúng trong buffer và tiếp tục đọc; không coi đó là một “message thiếu” rồi bỏ tùy ý.

### Delimiter framing

```text
HELLO\nWORLD\n
```

Ứng dụng tìm `\n` để phân biệt các thông điệp. Khi payload có thể chứa delimiter, cần escape/encode; đồng thời quy định giới hạn độ dài dòng để tránh client gửi dữ liệu vô hạn mà không có delimiter.

TCP cung cấp **thứ tự byte**, nhưng không cung cấp cấu trúc message. UDP cung cấp **cấu trúc datagram** ở transport, nhưng nếu ứng dụng cần một message logic trải qua nhiều datagram, phải tự định nghĩa framing/chunking/reassembly ở tầng ứng dụng.

## 10. Reliability, ordering và duplicate trên UDP

UDP không tự gửi ACK, retransmit và sắp thứ tự giúp ứng dụng. Nếu ứng dụng cần các tính năng này, một giao thức tầng trên có thể đặt metadata vào payload:

```text
+---------+-------------+-------------+----------------+
| version | sequence No | timestamp   | payload        |
+---------+-------------+-------------+----------------+
```

Ví dụ chiến lược:

1. **Sequence number:** phân biệt thông điệp mới/cũ, phát hiện mất gói hoặc duplicate.
2. **ACK/timeout:** thông báo gói đã nhận; sender quyết định retransmit sau thời gian thích hợp.
3. **Idempotency:** khi retransmit khiến cùng tác vụ xuất hiện hai lần, receiver không thực thi thao tác nghiệp vụ hai lần.
4. **Reordering buffer:** giữ các gói đến sai thứ tự khi cần phát lại theo trình tự.
5. **Rate/congestion control:** hạn chế tốc độ gửi khi network congested, tránh tự tạo bão retransmission.
6. **Expiration:** với trạng thái game/telemetry, dữ liệu cũ có thể nên bị bỏ thay vì gửi lại quá trễ.

Nhưng khi tự bổ sung đầy đủ retransmission, congestion control, ordering, flow control và bảo mật, hệ thống sẽ phức tạp đáng kể. Với dữ liệu nghiệp vụ yêu cầu đầy đủ byte theo thứ tự, TCP thường tiết kiệm công sức hơn. Với thời gian thực, đôi khi **bỏ frame cũ để nhận frame mới** là đúng mục tiêu, nên UDP/datagram lại phù hợp.

### TCP cũng có giới hạn cần nhận thức

- TCP không cam kết latency cố định hay rằng thông điệp sẽ đến trước deadline.
- TCP retransmit có thể gây tăng trễ khi packet loss, khiến dữ liệu mới phía sau không được ứng dụng đọc trước byte cũ bị thiếu theo thứ tự stream (head-of-line blocking).
- Một lần `write` trả thành công không đảm bảo ứng dụng peer đã **xử lý** dữ liệu. TCP xác nhận ở tầng truyền tải, không phải ACK nghiệp vụ.
- Kết nối TCP có thể đứt, timeout hoặc bị đóng nửa chừng; ứng dụng vẫn cần timeout, retry thích hợp, kiểm soát idempotency và xử lý dữ liệu chưa commit.

## 11. Lựa chọn API Java NIO theo nhu cầu

| Bài toán | Hướng tiếp cận |
|---|---|
| Discovery trong mạng cục bộ | `DatagramChannel` + UDP broadcast/multicast nếu mạng hỗ trợ, kèm giới hạn và xác thực |
| Telemetry định kỳ, chấp nhận mất mẫu cũ | Datagram + sequence/timestamp, rate limit |
| Trạng thái game cập nhật liên tục, dữ liệu cũ ít giá trị | Datagram + xử lý packet loss/out-of-order |
| Gửi file chính xác toàn vẹn | TCP stream hoặc giao thức tin cậy khác, kiểm tra checksum |
| RPC cần request/response hoàn chỉnh | Thường TCP hoặc giao thức ứng dụng đã được xây dựng phù hợp |
| Nhiều kết nối TCP đồng thời trên một luồng | `ServerSocketChannel` + `SocketChannel` + `Selector` |
| Nhiều sender gửi một gói tới một cổng | `DatagramChannel.bind` + `receive` hoặc `Selector` |
| UDP chỉ trao đổi với một peer cố định | Có thể `DatagramChannel.connect(peer)` và `read/write`, nhưng vẫn là UDP |
| Cần TLS theo dòng byte | TCP + TLS/`SSLEngine` (hoặc stack mạng thích hợp); UDP thuần không tự có TLS |
| Cần bảo mật UDP | Thiết kế DTLS hoặc giao thức đã được kiểm chứng; không tự coi datagram là đã xác thực |

Chọn protocol ở **mức yêu cầu ứng dụng**: chấp nhận mất gói hay không, có deadline không, cần ordering không, dữ liệu nhạy cảm không, môi trường mạng nào và liệu phải xử lý NAT/firewall hay multicast.

## 12. Lỗi phổ biến khi lập trình DatagramChannel

1. **Cho rằng `send` thành công đồng nghĩa receiver đã nhận.** Sai: UDP không có ACK nghiệp vụ, đường truyền vẫn có thể mất gói.
2. **Cho rằng UDP `connect` có TCP handshake.** Sai: nó chỉ gắn peer vào datagram socket theo semantics UDP.
3. **Dùng receive buffer nhỏ rồi tưởng lần receive kế tiếp lấy phần dư.** Sai: dữ liệu vượt remaining của một datagram bị bỏ.
4. **Quên `ByteBuffer.flip()` sau `receive`.** Buffer position đang ở sau byte mới ghi; decode trực tiếp trước flip có thể nhận sai vùng.
5. **Quên giữ ranh giới message khi xử lý nhiều datagram.** Mỗi lần nhận một gói cần parse độc lập hoặc theo protocol reassembly riêng.
6. **Dùng `read/write` với DatagramChannel chưa `connect`.** Cần `send/receive` hoặc kết nối peer trước.
7. **Gọi `receive` trên blocking channel rồi mong trả `null` ngay khi chưa có gói.** Blocking có thể chờ; muốn poll, dùng non-blocking hoặc selector/timeout theo thiết kế.
8. **Không kiểm tra `source == null` ở non-blocking.** Khi không có datagram, `receive` có thể trả null.
9. **Quên `configureBlocking(false)` trước khi đăng ký selector.** Đăng ký selectable channel đang blocking có thể ném `IllegalBlockingModeException`.
10. **Đăng ký `OP_ACCEPT` cho DatagramChannel.** UDP không có accept; hãy dùng `OP_READ`.
11. **Giả định mỗi TCP read trả đúng một message.** TCP là stream; phải có framing và vòng lặp đủ byte.
12. **Tự gửi datagram quá lớn.** MTU, fragmentation và độ ổn định mạng khiến gói lớn kém phù hợp trong nhiều trường hợp.
13. **Không giới hạn message size/rate.** Packet từ nguồn không tin cậy có thể khiến server tốn CPU hoặc bộ nhớ.
14. **Dùng địa chỉ sender làm danh tính bảo mật.** Địa chỉ UDP có thể bị giả mạo hoặc thay đổi; cần xác thực ứng dụng khi thao tác quan trọng.
15. **Cho rằng UDP nhanh hơn TCP trong mọi bài toán.** Tổng chi phí reliability tự xây, retry, fragmentation hoặc packet loss có thể khiến thực tế phức tạp và kém hiệu quả hơn.
16. **Không đóng channel/selector.** Dùng try-with-resources, xử lý exceptions và shutdown để giải phóng socket và hệ thống.

## 13. Các tình huống tư duy

### Tình huống A: ứng dụng nhận nhiệt độ mỗi giây

Nếu mỗi mẫu mới thay thế giá trị cũ và việc mất một vài mẫu chấp nhận được, UDP datagram thường hợp lý. Gắn `timestamp`/`sequence` để loại dữ liệu đến muộn hoặc trùng. Đừng suy ra datagram đáng tin cậy chỉ vì chương trình chạy ổn trên loopback.

### Tình huống B: gửi lệnh thanh toán một lần

UDP thuần không phù hợp nếu chỉ send rồi kết luận giao dịch thành công. Thao tác thanh toán cần cơ chế xác thực, idempotency, xác nhận nghiệp vụ, lưu trạng thái và retry kiểm soát. Dùng protocol tin cậy và giao thức ứng dụng được thiết kế để tránh thực thi trùng, không dựa vào API `send`.

### Tình huống C: game gửi vị trí 20 lần mỗi giây

Thông tin vị trí cũ có thể trở nên vô nghĩa khi frame mới đã tới. Datagram có thể giảm tình trạng phải chờ truyền lại dữ liệu cũ, nhưng vẫn cần sequence number, xử lý packet loss, tốc độ gửi hợp lý và đồng bộ trạng thái quan trọng theo kênh đáng tin hơn.

### Tình huống D: gửi hai thông điệp trên TCP

Sender thực hiện `write("HELLO")` và `write("WORLD")`. Receiver đọc `"HELLOWORLD"` một lần. Đó là hành vi hoàn toàn bình thường với TCP. Nếu cần hai message, phải dùng framing, ví dụ `[length=5][HELLO][length=5][WORLD]`.

### Tình huống E: nhận gói 1.500 byte vào buffer 512 byte

Receiver không thể lấy nốt 988 byte trong lần `receive` kế tiếp. Nó phải thiết kế kích thước receive buffer và giới hạn protocol từ đầu, hoặc reject packet có kích thước không đúng. Phần đã bị cắt không thể khôi phục chỉ bằng `flip` hay đọc lại.

### Tình huống F: client UDP muốn tránh xử lý gói từ peer khác

`DatagramChannel.connect(remote)` có thể giúp lọc địa chỉ peer và cung cấp API `read/write` đơn giản. Nó vẫn không xác thực người gửi theo chuẩn bảo mật, không bảo đảm remote sẽ trả lời và không thay thế mã hóa/chống giả mạo.

## 14. Câu hỏi tự kiểm tra

**1.** Gửi UDP hai lần: `"AB"` và `"CD"`. Receiver có được quyền giả định một `receive` sẽ trả `"ABCD"` không?  
**Không.** Mỗi lần receive xử lý một datagram; các gói có thể mất/đảo thứ tự, nhưng UDP không tự ghép hai datagram thành một message.

**2.** Ghi TCP `"AB"` rồi `"CD"`. Receiver có thể nhận cả `"ABCD"` trong một `read` không?  
**Có.** TCP là byte stream, không lưu ranh giới hai lần write.

**3.** Vì sao `channel.receive(buffer)` trả `SocketAddress` thay vì chỉ trả số byte?  
**Vì** DatagramChannel khi nhận gói unconnected cần cung cấp địa chỉ nguồn cho ứng dụng. Số byte có thể xác định từ mức tăng `ByteBuffer.position()` (cần ghi nhận trạng thái trước khi receive).

**4.** Channel non-blocking và chưa có datagram thì `receive` trả gì?  
**Có thể trả `null`.** Đừng dereference địa chỉ nguồn khi chưa kiểm tra kết quả.

**5.** Sau `DatagramChannel.connect(peer)`, sender có tự được bảo đảm truyền lại khi mất gói không?  
**Không.** Vẫn là UDP.

**6.** Nếu datagram có 1.000 byte nhưng `dst.remaining() == 100`, liệu mười lần receive sẽ đọc đủ 1.000 byte đó?  
**Không.** Phần không vừa buffer bị loại bỏ ở lần nhận datagram đó.

**7.** Để chờ gói trên một receiver non-blocking có thể dùng công cụ nào?  
**`Selector`** và đăng ký `OP_READ`; hoặc polling có chiến lược phù hợp. Không dùng `OP_ACCEPT`.

**8.** TCP có ngăn ứng dụng gửi trùng một lệnh sau khi client retry không?  
**Không tự ngăn ở tầng nghiệp vụ.** TCP đảm bảo stream trong một kết nối nhưng retry request qua ứng dụng/kết nối mới vẫn có thể khiến thao tác được gửi hai lần. Cần idempotency khi quan trọng.

## 15. Ghi nhớ

- **UDP datagram = ranh giới thông điệp ở cấp gói, không có bảo đảm delivery/thứ tự.**
- **TCP = luồng byte có thứ tự và cơ chế truyền tin cậy, không có ranh giới message ở API.**
- **`DatagramChannel.send`/`receive` xử lý từng datagram**, `receive` trả địa chỉ người gửi; buffer thiếu chỗ sẽ làm mất phần dư của gói.
- **`DatagramChannel.connect` không phải TCP handshake**; UDP không tự thành reliable stream.
- **`SocketChannel.read/write` cần framing và xử lý partial I/O** nếu ứng dụng làm việc theo thông điệp.
- **`Selector` phù hợp với DatagramChannel non-blocking**, đặc biệt cho event loop nhiều socket.
- Khi chọn UDP hay TCP, ưu tiên yêu cầu về latency, loss, ordering, message size, bảo mật và độ phức tạp giao thức.

### Tài liệu API

- [DatagramChannel — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/DatagramChannel.html)
- [SocketChannel — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/SocketChannel.html)
- [ServerSocketChannel — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/ServerSocketChannel.html)
- [Selector — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/Selector.html)
- [ByteBuffer — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nio-networking-009">Phạm vi I/O mạng và các kiến thức giao thức cần học riêng</a>

<details>
<summary>Xem chi tiết</summary>

# Phạm vi I/O mạng và các kiến thức giao thức cần học riêng trong Java NIO

**Java NIO mạng** cung cấp cách lập trình *đọc, ghi, kết nối, chấp nhận kết nối và nhận biết trạng thái sẵn sàng của socket* thông qua các channel và buffer. Những công cụ như `SocketChannel`, `ServerSocketChannel`, `DatagramChannel`, `ByteBuffer`, `Selector` và `SelectionKey` giúp xây dựng tầng vận chuyển dữ liệu, đặc biệt khi muốn một số ít thread xử lý nhiều kết nối.

Tuy nhiên, **gửi/nhận được byte không đồng nghĩa đã xây dựng được một giao thức ứng dụng hoàn chỉnh**. Phía trên tầng I/O còn có cách định ranh giới thông điệp, mã hóa, xác thực, bảo mật TLS, timeout, retry, quản lý phiên, kiểm soát lưu lượng, khôi phục kết nối và các quy tắc nghiệp vụ. Phía dưới Java NIO, các cơ chế TCP/IP, UDP, DNS, kernel networking, firewall và hạ tầng mạng cũng có hợp đồng riêng.

Mục tiêu bài học là **xác định ranh giới kiến thức**: điều gì thuộc API Java NIO mạng cần nắm trong module Java Core I/O, điều gì chỉ cần biết như điều kiện để dùng API đúng, và những chủ đề giao thức nào phải học thành bài chuyên sâu riêng khi xây dựng hệ thống thực tế.

## 1. Phân lớp để tránh nhầm I/O với giao thức

Một ứng dụng gửi yêu cầu qua mạng thường đi qua các tầng:

```text
Quy tắc nghiệp vụ: gọi dịch vụ, phản hồi, lỗi, idempotency, authorization
                            |
Giao thức ứng dụng: HTTP/gRPC/WebSocket hoặc framing tự định nghĩa
                            |
Bảo mật phiên truyền (nếu sử dụng): TLS / mTLS
                            |
Tầng vận chuyển: TCP stream hoặc UDP datagram
                            |
Java I/O API: SocketChannel, DatagramChannel, Selector, ByteBuffer
                            |
Hệ điều hành: socket, network stack, network interface, firewall
                            |
Network / thiết bị / peer ở xa
```

Đây là **sơ đồ tư duy** để phân định trách nhiệm; TLS thực tế thường được áp dụng trên transport thích hợp và các giao thức như QUIC có kiến trúc riêng (tích hợp bảo mật, truyền qua UDP), nên không lấy sơ đồ làm mô hình tuyệt đối cho mọi giao thức.

Với Java NIO:

- **API cung cấp** cách mở/kết nối socket, nhận kết nối mới, đọc/ghi bytes, nhận/gửi datagram, thay đổi blocking mode, đăng ký interest và lấy các sự kiện readiness.
- **API không tự định nghĩa** một gói bytes nghĩa là request gì, có bao nhiêu bytes trong một thông điệp ứng dụng, dùng JSON hay Protobuf, có được phép thực hiện thao tác hay không, hay cần retry thế nào.
- **API cũng không thay OS thực hiện TCP/IP**. Java gọi tới networking stack; việc packet bị chia nhỏ, gửi lại, định tuyến hoặc bị drop là chuyện của transport/OS/network. Chương trình phải hiểu những hệ quả quan sát được qua API.

Điểm dễ nhầm là từ “packet”: ở UDP, mỗi lệnh `send` hướng đến **một datagram**; ở TCP, mỗi `write` chỉ đưa thêm bytes vào **một byte stream**, không tạo ra một “message TCP” có thể nhận nguyên khối từ `read` tương ứng.

## 2. Những kiến thức nằm trong phạm vi Java NIO mạng

### 2.1. Họ channel và trách nhiệm

| API | Dùng để làm gì? | Không tự cung cấp |
| --- | --- | --- |
| `ServerSocketChannel` | Bind vào địa chỉ, lắng nghe TCP và `accept` kết nối mới thành `SocketChannel`. | HTTP server, routing, xác thực, protocol framing. |
| `SocketChannel` | Kết nối TCP, `read`/`write` bytes qua `ByteBuffer`, `shutdownInput/Output`, chọn blocking hoặc non-blocking. | Ranh giới thông điệp, retry an toàn, mã hóa TLS mặc định. |
| `DatagramChannel` | `bind`, `connect` (tùy trường hợp), `send`/`receive` datagram UDP với `ByteBuffer`. | Bảo đảm delivery, ordering, chống duplicate, application ACK. |
| `ByteBuffer` | Quản lý vùng byte có `position`, `limit`, `capacity`, `flip`, `compact`. | Charset nếu không gắn decoder, protocol parser, thread-safety tự động. |
| `Selector` | Đợi các channel đã đăng ký có các sự kiện sẵn sàng phù hợp. | Xử lý request, tự thực hiện read/write, timer nghiệp vụ, fairness tự động. |
| `SelectionKey` | Mang `interestOps`, `readyOps`, attachment và trạng thái đăng ký. | Một bảo đảm “sẽ đọc/ghi hết payload” mỗi lần readiness xuất hiện. |
| `SocketOption` và `StandardSocketOptions` | Điều chỉnh một số socket options, theo loại channel và OS. | Tự tối ưu network, bỏ qua giới hạn kernel hoặc tạo SLA. |

Các lớp kể trên thuộc `java.nio`, `java.nio.channels` và `java.net`. Hãy phân biệt chúng với `FileChannel`: channel cho file có seek, offset và mapping; channel cho socket có network endpoint và có thể đăng ký `Selector` khi là `SelectableChannel`.

### 2.2. Chu kỳ dùng SocketChannel/ServerSocketChannel

Với một kết nối TCP ở chế độ blocking:

1. Server tạo `ServerSocketChannel.open()` rồi `bind(localAddress)`.
2. Server gọi `accept()` để nhận kết nối từ client.
3. Client tạo `SocketChannel.open()` rồi `connect(remoteAddress)`.
4. Hai bên dùng `read(ByteBuffer)` / `write(ByteBuffer)` để trao đổi **byte stream**.
5. Hai bên xác định protocol framing, đóng nửa kết nối khi thích hợp (`shutdownOutput()`) hoặc đóng hẳn channel (`close()`).

Trong chế độ non-blocking, `connect(...)` có thể trả `false` (kết nối chưa hoàn tất); cần kiểm tra `finishConnect()` khi nhận sự kiện phù hợp. `accept()` có thể trả `null` nếu chưa có kết nối mới; `read/write` có thể trả `0` khi chưa thể tiến triển.

**Kiểu channel và blocking mode quyết định hành vi**, không chỉ tên `read`/`write`. Tránh dùng một vòng `while (read() == 0)` chạy liên tục khiến CPU tăng cao; với non-blocking I/O, dùng readiness, queue và backpressure theo kiến trúc đã chọn.

### 2.3. Selector và event loop chỉ giải quyết bài toán readiness

Một event loop đơn giản thường:

```text
configureBlocking(false)
register(selector, OP_ACCEPT / OP_CONNECT / OP_READ ...)
              |
selector.select() đợi kênh sẵn sàng
              |
duyệt selectedKeys và xử lý từng loại sự kiện
              |
read/write bytes, cập nhật per-connection state
              |
đổi interestOps phù hợp; quay lại select
```

Đây là **sơ đồ quy trình**, không phải Java source độc lập. Trong code thật cần:

- Bỏ key đã xử lý khỏi tập `selectedKeys()`, thông thường qua iterator.remove().
- Khi `OP_ACCEPT`: gọi `accept`, xử lý kết quả `null` nếu thích hợp, cấu hình client channel non-blocking và đăng ký `OP_READ`.
- Khi `OP_CONNECT`: gọi `finishConnect`, xử lý lỗi và thay interest ops.
- Khi `OP_READ`: đọc vào buffer thích hợp, phân tích từng phần dữ liệu, xử lý `-1` là phía remote đã kết thúc chiều gửi.
- Khi `OP_WRITE`: ghi tiếp các buffer còn lại; **chỉ quan tâm `OP_WRITE` khi có dữ liệu cần đẩy** để tránh event loop thức liên tục vì socket thường xuyên writable.
- Lưu trạng thái parser và buffer cho **từng connection**, theo dõi queue outbound và áp dụng giới hạn bộ nhớ.
- Xử lý `IOException` theo từng kết nối, thay vì khiến một kết nối lỗi làm sập cả server.

`Selector` cho biết kênh *có thể* thực hiện một thao tác mà không block ở thời điểm kiểm tra; nó không định nghĩa số byte chắc chắn chuyển được, và không làm toàn bộ logic của một request trở thành bất đồng bộ. Nếu handler chạy truy vấn DB chậm hoặc CPU nặng ngay trên event-loop thread, nó vẫn có thể trì hoãn các connection khác.

## 3. TCP là byte stream, không phải chuỗi message

Đây là kiến thức transport **bắt buộc ở mức nền tảng** để dùng `SocketChannel` đúng. TCP đem lại một **byte stream có thứ tự**, với cơ chế truyền tin cậy theo mô hình TCP khi kết nối còn hoạt động. Nhưng không có ranh giới giữa các lần `write` của ứng dụng:

```text
Client gọi:
  write("HELLO")
  write("WORLD")

Server có thể read được:
  "HELLOWORLD"
hoặc:
  "HE" + "LLOW" + "ORLD"
hoặc nhiều cách chia chunk khác nhau
```

Đây là minh họa các cách phân đoạn *quan sát qua read*; không phải lời hứa hệ điều hành phải chọn một cách cụ thể. Mỗi `read` được quyền trả ít bytes hơn phần còn trống trong buffer. Khi stream kết thúc có thể trả `-1`; việc một phía đóng kết nối đột ngột có thể gây exception và không thể suy ra toàn bộ dữ liệu chưa xử lý đã được peer application chấp nhận.

### Framing là phần của protocol ứng dụng

Muốn tách message, hai bên phải thống nhất một quy tắc, ví dụ:

| Kiểu framing | Nguyên lý | Lỗi và giới hạn cần xét |
| --- | --- | --- |
| **Length-prefix** | Header chứa độ dài; tiếp theo là đúng số byte payload. | Thống nhất byte order, kích thước header, giới hạn tối đa, xử lý EOF giữa frame. |
| **Delimiter** | Ký tự/chuỗi đặc biệt kết thúc frame, ví dụ `\n`. | Escaping, delimiter bên trong nội dung, giới hạn chiều dài dòng, encoding. |
| **Fixed-length** | Mỗi record có cùng số byte. | Cấu trúc cứng, padding, phiên bản dữ liệu. |
| **Self-describing format** | Parser của định dạng tự tìm ranh giới theo luật giao thức. | Parser có thể phải giữ state qua nhiều lần read, cần giới hạn độ phức tạp. |

**Không lấy `ByteBuffer.position()` hoặc số byte của một lần `SocketChannel.read` làm số lượng message.** Một lần read có thể chứa nửa header, một message hoàn chỉnh cộng một phần message tiếp, hoặc nhiều message liền nhau. Parser phải dùng trạng thái tích lũy qua nhiều lần đọc. Với non-blocking code, buffer có thể còn dữ liệu chưa parse và phải được `compact()` hoặc quản lý bằng queue/ring buffer cho lần đọc tiếp.

### Ví dụ Java 17+: một frame TCP có header độ dài

Ví dụ dùng blocking `SocketChannel` qua địa chỉ loopback để tập trung vào **ranh giới message**; không cần thiết lập máy chủ mạng bên ngoài. Một frame gồm 4 byte số nguyên big-endian là độ dài UTF-8, tiếp theo là số byte payload tương ứng. Đây là giao thức ví dụ tự đặt ra để học, **không phải HTTP hoặc một chuẩn sản xuất**.

Tạo file `TcpFramingDemo.java`:

```java
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public class TcpFramingDemo {
    static void readFully(SocketChannel socket, ByteBuffer dst) throws IOException {
        while (dst.hasRemaining()) {
            int n = socket.read(dst);
            if (n < 0) throw new EOFException("Truncated TCP frame");
            if (n == 0) throw new IOException("Blocking read made no progress");
        }
    }

    static void writeFully(SocketChannel socket, ByteBuffer src) throws IOException {
        while (src.hasRemaining()) {
            int n = socket.write(src);
            if (n == 0) throw new IOException("Blocking write made no progress");
        }
    }

    public static void main(String[] args) throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        try (ServerSocketChannel server = ServerSocketChannel.open()) {
            server.bind(new InetSocketAddress(loopback, 0));
            ExecutorService pool = Executors.newSingleThreadExecutor();
            try {
                Future<String> received = pool.submit(() -> {
                    try (SocketChannel peer = server.accept()) {
                        ByteBuffer header = ByteBuffer.allocate(Integer.BYTES);
                        readFully(peer, header);
                        header.flip();
                        int size = header.getInt();
                        if (size < 0 || size > 1024)
                            throw new IOException("Invalid frame size");
                        ByteBuffer body = ByteBuffer.allocate(size);
                        readFully(peer, body);
                        body.flip();
                        return StandardCharsets.UTF_8.decode(body).toString();
                    }
                });

                try (SocketChannel client = SocketChannel.open()) {
                    client.connect(server.getLocalAddress());
                    byte[] message = "Xin chào".getBytes(StandardCharsets.UTF_8);
                    ByteBuffer packet = ByteBuffer.allocate(Integer.BYTES + message.length);
                    packet.putInt(message.length).put(message).flip();
                    writeFully(client, packet);
                    client.shutdownOutput();
                }
                System.out.println("tcp-message=" + received.get(5, TimeUnit.SECONDS));
            } finally {
                pool.shutdownNow();
            }
        }
    }
}
```

Biên dịch/chạy Java 17+ ở môi trường cho phép tạo TCP loopback socket:

```bash
javac --release 17 TcpFramingDemo.java
java TcpFramingDemo
```

Output đã kiểm chứng:

```text
tcp-message=Xin chào
```

Giải thích:

1. `server.bind(..., 0)` yêu cầu OS chọn một cổng tạm, rồi client dùng địa chỉ trả về từ `server.getLocalAddress()`.
2. Server chạy tác vụ nhận kết nối riêng, đọc **đủ 4 byte** header rồi phân tích length với `getInt()`. `ByteBuffer` mặc định dùng big-endian, nên hai phía phù hợp nhau.
3. Kiểm tra `0 <= size <= 1024` trước khi cấp phát payload: chỉ đọc 4 byte từ network chưa đủ để tin bên gửi sẽ không khai báo dung lượng quá lớn.
4. Server tiếp tục `readFully` tới khi đủ payload hoặc ném `EOFException` khi peer đóng giữa frame. Client dùng `writeFully`, không giả định một `write` gửi đủ.
5. `UTF_8.decode` giải mã **toàn bộ payload được frame**. Nếu hai bên dùng charset khác nhau hoặc payload hỏng thì cần quy tắc báo lỗi decoder/validation của giao thức.
6. `Future.get(5, TimeUnit.SECONDS)` giới hạn thời gian đợi kết quả trong chương trình demo, không phải một hệ thống quản lý timeout cho toàn bộ hoạt động kết nối sản xuất.

**Phạm vi bài:** code chủ yếu minh họa primitive NIO và framing. Chưa xử lý giao thức có nhiều frame liên tiếp, backpressure trên non-blocking socket, TLS, xác thực, replay, quota, kiểm soát request hoặc recovery. Khi xây dựng dịch vụ thật, cần tách parser/protocol state khỏi transport và ràng buộc kích thước/tài nguyên cho mỗi kết nối.

## 4. UDP có ranh giới datagram nhưng không có bảo đảm như TCP

`DatagramChannel` nhận và gửi **datagram** bằng `send(ByteBuffer, SocketAddress)` / `receive(ByteBuffer)`. Khác TCP byte stream:

- Mỗi datagram có ranh giới thông điệp ở tầng transport; một lần nhận thành công trả thông tin sender của một datagram.
- Datagram có thể **bị mất, đến không đúng thứ tự, trùng lặp** hoặc quá lớn so với mạng, tùy điều kiện. Không suy ra gửi thành công ở client thì remote app đã xử lý.
- Khi buffer nhận nhỏ hơn datagram, **phần vượt quá có thể bị loại bỏ**; không có “đọc tiếp nửa còn lại” như TCP stream. Vì vậy cần định nghĩa maximum payload và kích thước receive buffer đúng.
- `connect` trên `DatagramChannel` xác định peer mặc định/lọc giao tiếp theo peer, nhưng không thực hiện TCP handshake và **không** biến UDP thành transport tin cậy.
- UDP không có stream EOF tương tự TCP; cơ chế timeout, thông báo mất kết nối hoặc kết thúc phiên thuộc thiết kế ứng dụng/protocol.

Muốn xây giao thức tin cậy trên UDP, phải học thêm sequence number, ACK, retransmission, duplicate suppression, ordering, congestion/flow control và cách xác định peer/phiên. Đây là phần **giao thức chuyên sâu**, vượt quá việc học API `DatagramChannel` đơn thuần. Một số công nghệ như QUIC đã triển khai các cơ chế riêng, không nên tự xây lại nếu không có nhu cầu kỹ thuật cụ thể.

### Ví dụ Java 17+: gửi và nhận một UDP datagram qua loopback

Tạo file `UdpDatagramDemo.java`:

```java
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;

public class UdpDatagramDemo {
    public static void main(String[] args) throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        try (DatagramChannel receiver = DatagramChannel.open();
             DatagramChannel sender = DatagramChannel.open()) {
            receiver.bind(new InetSocketAddress(loopback, 0));
            sender.bind(new InetSocketAddress(loopback, 0));
            byte[] text = "PING".getBytes(StandardCharsets.UTF_8);
            int sent = sender.send(ByteBuffer.wrap(text), receiver.getLocalAddress());
            ByteBuffer buffer = ByteBuffer.allocate(64);
            SocketAddress from = receiver.receive(buffer);
            buffer.flip();
            String received = StandardCharsets.UTF_8.decode(buffer).toString();
            System.out.println("udp-sent-bytes=" + sent);
            System.out.println("udp-received=" + received);
            System.out.println("udp-has-sender=" + (from != null));
        }
    }
}
```

```bash
javac --release 17 UdpDatagramDemo.java
java UdpDatagramDemo
```

Kết quả đã kiểm chứng khi loopback cho phép UDP và datagram đến nơi:

```text
udp-sent-bytes=4
udp-received=PING
udp-has-sender=true
```

`sender.send` gửi 4 byte từ buffer. `receiver.receive` lấy một datagram và địa chỉ người gửi; `flip()` chuẩn bị vùng byte vừa nhận cho decoder. Demo dùng datagram nhỏ, cùng host nên dễ quan sát. **Một lần chạy thành công không chứng minh UDP giao hàng tin cậy trên mạng thực.** Trong môi trường chặn socket hoặc drop datagram, blocking `receive` có thể đợi; ứng dụng thật nên quy định deadline, xử lý lỗi và đóng channel khi hủy tác vụ.

## 5. Kiến thức TCP/IP cần biết nhưng phải học riêng khi đào sâu

Java NIO **phơi bày** hệ quả của tầng vận chuyển qua I/O result và option; kiến thức sâu về TCP/IP thuộc chủ đề giao thức hoặc mạng máy tính.

| Khái niệm | Cần hiểu ở mức dùng NIO | Học chuyên sâu riêng khi nào? |
| --- | --- | --- |
| IP, port, endpoint | `InetSocketAddress` chỉ định địa chỉ + cổng; server bind, client connect. | Định tuyến, IPv4/v6, NAT, subnet, load balancer, multi-homing. |
| DNS | Hostname cần phân giải thành địa chỉ; có thể lỗi hoặc thay đổi. | TTL, caching, split-horizon DNS, failover, DNSSEC, service discovery. |
| TCP connection | Có handshake, thứ tự byte, half-close, EOF, reset; `read` không giữ message boundary. | Congestion control, retransmission, window scaling, keepalive tuning, queueing và packet capture. |
| TCP flow control | Writer có thể phải chờ hoặc `write` chuyển ít byte; cần outbound queue/backpressure. | Receive window, congestion window, thuật toán điều khiển nghẽn, bufferbloat. |
| UDP datagram | Có ranh giới gói; có thể mất/trùng/sai thứ tự; buffer nhỏ gây cắt dữ liệu. | MTU, fragmentation, application retransmission, jitter, streaming thời gian thực. |
| Firewall/NAT | Bind/listen không có nghĩa thiết bị ở xa truy cập được; kết nối có thể bị chặn. | Stateful firewall, NAT traversal, reverse proxy, ingress policy. |
| Timeout | Một socket có thể treo rất lâu nếu không quy định thời hạn và xử lý hủy. | Deadline propagation, retry budgets, circuit breaker, failure detectors. |

Ví dụ thấy `SocketChannel.write` trả `0` ở chế độ non-blocking không nhất thiết có nghĩa network đã hỏng; có thể send buffer chưa sẵn sàng. Ngược lại, một write trả số byte dương chỉ chứng minh bytes đã được chuyển theo hợp đồng local API, **không xác nhận remote ứng dụng đã nhận, parse hoặc commit yêu cầu**. Muốn biết remote đã xử lý đúng cần cơ chế acknowledgement **ở giao thức ứng dụng**.

## 6. Giao thức ứng dụng phải định nghĩa rõ ràng, không phát sinh tự động từ Channel

Một giao thức giữa client và server cần trả lời các câu hỏi:

1. **Message bắt đầu/kết thúc ở đâu?** Length-prefix, delimiter, header định dạng hay cấu trúc đã chuẩn hóa?
2. **Dữ liệu là gì?** Text UTF-8, JSON, Protobuf, CBOR hay binary tùy chỉnh? Byte order, charset, null, field bắt buộc?
3. **Phiên bản được tương thích thế nào?** Field cũ/mới, version negotiation, schema validation, giới hạn kích thước?
4. **Request/response ghép cặp bằng gì?** Request id, correlation id, multiplexing, thứ tự phản hồi?
5. **Lỗi trả ra thế nào?** Error code, transient/permanent, malformed input, unknown request, server busy?
6. **Thời hạn là bao lâu?** Timeout kết nối, đọc, xử lý, deadline toàn giao dịch và cancellation?
7. **Retry có an toàn không?** Nếu kết nối đứt sau khi gửi yêu cầu, bên gửi có biết server đã xử lý chưa? Idempotency key và deduplication thuộc giao thức/nghiệp vụ.
8. **Phân quyền ra sao?** Authentication, authorization, mTLS/token, auditing, rate limit và quota.

Một protocol parser tốt phải xử lý **dữ liệu đến từng phần** và input không tin cậy. Đừng cấp phát `ByteBuffer.allocate(untrustedLength)` trước khi kiểm tra giới hạn, đừng tin một lần read đủ header, và đừng xử lý request chưa xác thực bằng cách chạy logic nghiệp vụ với quyền hệ thống.

### So sánh với HTTP

Khi nghiệp vụ chỉ cần gọi REST API, **`java.net.http.HttpClient`** hoặc client framework phù hợp thường là lựa chọn đúng hơn tự dùng `SocketChannel` rồi tự viết parser HTTP. Một HTTP implementation đã mang theo các quy tắc URL, method, header, status, streaming body, phiên bản HTTP, HTTP/2 multiplexing và tích hợp TLS theo cấu hình.

`SocketChannel` chỉ cung cấp **byte transport**, không tự hiểu `GET /... HTTP/1.1`, `Content-Length`, chunked transfer, HTTP/2 frames, cookies hoặc xác thực. `HttpClient` trong module `java.net.http` hỗ trợ HTTP và WebSocket client; một ứng dụng HTTP server có thể dùng framework chuyên dụng. Xây protocol qua raw socket là bài tập hữu ích để hiểu framing, nhưng không nên triển khai giao thức phổ biến từ đầu nếu thư viện chuẩn đã đáp ứng nhu cầu.

## 7. TLS, mTLS và bảo mật nằm ở tầng nào?

`SocketChannel` **không tự mã hóa** bytes. Một kết nối TCP không TLS truyền dữ liệu dưới dạng plaintext ở mức ứng dụng (dù có thể có bảo vệ riêng ở tầng mạng/hạ tầng, điều đó không thay TLS end-to-end theo yêu cầu). Để bảo vệ dữ liệu truyền:

- **TLS** đòi hỏi handshake, xác nhận danh tính peer theo chứng chỉ/hostname, trao đổi khóa, mã hóa và kiểm tra tính toàn vẹn của record.
- **mTLS** cho phép xác thực lẫn nhau bằng chứng chỉ client/server; còn mapping từ danh tính đã xác thực tới quyền truy cập nghiệp vụ là **authorization**, không do TLS tự quyết định.
- Java có `SSLSocket` / `SSLServerSocket` cho mô hình stream và **`SSLEngine`** cho trường hợp muốn điều khiển mã hóa qua buffer, có thể tích hợp với NIO non-blocking. `SSLEngine.wrap/unwrap`, handshake status, delegated tasks, close-notify và trạng thái buffer đòi hỏi một state machine riêng.
- Không truyền dữ liệu nhạy cảm hoặc xác thực qua raw `SocketChannel` rồi mặc định nó an toàn vì ở trong mạng nội bộ.

Phần cần biết trong **Java Core I/O** là ranh giới trách nhiệm và API nào tích hợp. Thiết kế ciphersuite, certificate validation, CA trust, revocation, key rotation, ALPN, TLS handshake state machine và phòng chống tấn công thuộc **bài bảo mật/giao thức chuyên sâu**. Nếu nghiệp vụ chỉ cần HTTPS client, dùng thư viện HTTP/TLS chuẩn với kiểm tra chứng chỉ và hostname được bật; đừng tắt verification để chữa lỗi kết nối trong production.

## 8. Timeout, cancel, backpressure và đóng kết nối

Đây là phần thiết kế I/O cần hiểu ở mức API, còn chính sách cụ thể thường nằm ở hệ thống mạng/application.

**Timeout không có một công tắc dùng cho mọi thao tác NIO:**

- Blocking `SocketChannel.connect` và `read` có hành vi chờ khác với non-blocking connect/read qua `Selector`. Cần xác định deadline ở cấp tác vụ, có thể dùng event loop timer và cancel/close đúng cách.
- `Selector.select(timeout)` giới hạn lần chờ readiness, **không giới hạn tổng thời gian xử lý request**. Cần tự lưu thời điểm bắt đầu/deadline theo connection/request.
- Kết nối thành công không bảo đảm peer còn đáp ứng; TCP keepalive ở OS khác với application heartbeat hoặc read deadline.
- Khi dùng `Future`/asynchronous framework, timeout của việc **đợi kết quả** có thể không tự dừng socket I/O; phải kết nối cơ chế hủy với vòng đời channel.

**Backpressure:** nếu phía nhận chậm hơn phía gửi, outbound buffer có thể tăng không giới hạn. Một server NIO đúng cần giới hạn số bytes pending mỗi connection, tạm ngừng đọc hoặc trả lỗi/làm chậm producer, và bật `OP_WRITE` khi có bytes chưa gửi. Đây là chính sách tài nguyên ứng dụng, không phải công việc tự động của `Selector`.

**Close:** với TCP, `shutdownOutput()` báo kết thúc chiều gửi, trong khi vẫn có thể đọc phản hồi; `close()` đóng channel và có thể khiến I/O đang chờ bị ngắt. Khi peer đọc được `-1`, đó là trạng thái end-of-stream cho chiều đọc; **không có nghĩa message trước đó hợp lệ nếu parser vẫn thiếu byte**. Vùng buffer còn bytes hoặc request chưa ACK cần có chính sách kết thúc rõ ràng. Với UDP không có khái niệm stream EOF/half-close giống TCP.

## 9. Phân biệt lỗi transport, protocol và nghiệp vụ

| Tình huống | Thuộc lớp nào? | Xử lý chính |
| --- | --- | --- |
| `ConnectException` khi không kết nối được | Transport/network | Log địa chỉ/cổng, đánh giá timeout/retry theo deadline; không tự động retry vô hạn. |
| `read` trả `-1` giữa frame TCP | Transport kết hợp framing | Đánh dấu message chưa đủ, từ chối dữ liệu dở và đóng/khôi phục theo protocol. |
| `write` ghi ngắn hoặc trả `0` khi non-blocking | NIO I/O state | Giữ buffer phần còn lại, cập nhật readiness/backpressure. |
| Frame khai báo độ dài vượt quota | Application protocol/parser | Từ chối sớm, giới hạn cấp phát bộ nhớ và đóng kết nối nếu vi phạm giao thức. |
| JSON sai schema, phiên bản không tương thích | Application protocol/schema | Trả protocol error với mã lỗi phù hợp. |
| TLS certificate không tin cậy | Security/identity | Từ chối handshake; kiểm tra truststore/hostname, không bỏ validation. |
| HTTP status 401/403 | HTTP + authorization | Xử lý credential/quyền, không nhầm thành TCP connection failure. |
| Request thanh toán đã được server xử lý nhưng ACK bị mất | Application consistency | Idempotency key, query transaction status; tránh retry mù gây double charge. |
| Datagram UDP mất | UDP/network | Chấp nhận mất nếu là telemetry best-effort hoặc thiết kế reliability riêng. |
| Nhiều request ngốn CPU và làm nghẽn event loop | Application scheduling | Giới hạn công việc, tách xử lý nặng khỏi thread vận hành selector. |

Nhận diện đúng lớp lỗi giúp tránh một anti-pattern: thấy `IOException` rồi “gửi lại toàn bộ request” một cách tự động. Với request **không idempotent**, lỗi kết nối xảy ra sau khi server đã cập nhật dữ liệu có thể khiến retry thực hiện thêm lần nữa. Độ tin cậy nghiệp vụ cần một protocol/transaction identity mà bản thân I/O API không biết.

## 10. Những phần cần học riêng khi phát triển hệ thống mạng

Các chủ đề dưới đây liên quan sát sao tới NIO nhưng cần chương trình học chuyên sâu riêng vì có bộ quy tắc vượt xa việc đọc/ghi buffer:

| Chủ đề học riêng | Trọng tâm tối thiểu |
| --- | --- |
| **TCP/IP và mạng máy tính** | IP/port, DNS, handshake, connection states, congestion/flow control, routing, NAT/firewall, socket diagnostics, packet capture. |
| **Giao thức ứng dụng** | Framing, request/response, encoding, schema, versioning, parser incremental, multiplexing, heartbeat và error contract. |
| **HTTP/HTTPS và WebSocket** | HTTP methods, headers, body framing, caching, status codes, HTTP/2, WebSocket handshake/frames, reverse proxy. |
| **TLS/mTLS và bảo mật** | Certificate/PKI, truststore, hostname verification, TLS handshake, SSLEngine state, secrets, authentication/authorization. |
| **Resilience trong distributed systems** | Timeout budgets, retry/backoff/jitter, idempotency, duplicate suppression, circuit breaker, partial failure. |
| **Event-loop architecture** | Selector lifecycle, per-connection state, write queue, fairness, task offloading, buffer pooling, leak detection. |
| **UDP / realtime / QUIC** | Datagram loss/reorder, MTU, reliable delivery khi cần, jitter buffers, congestion control, QUIC/HTTP3. |
| **Quan sát và vận hành** | Metrics latency/throughput, connection counts, TLS errors, tracing, packet logging an toàn, load testing. |

Không bắt buộc học toàn bộ chi tiết này trước khi viết NIO network hello-world. Nhưng khi chuyển từ demo sang production, chủ đề nào ảnh hưởng trực tiếp tới hợp đồng dịch vụ phải được chọn và đào sâu có hệ thống.

### Một lộ trình hợp lý

1. Bắt đầu với `ByteBuffer` và hành vi short read/write, EOF, `flip/compact`.
2. Hiểu `SocketChannel` / `ServerSocketChannel` ở **blocking mode**; tự viết một length-prefixed message nhỏ, có giới hạn payload và xử lý EOF dở.
3. Thử `DatagramChannel` để nhận ra ranh giới và failure model khác TCP.
4. Sau khi thông thạo byte transport, chuyển sang `configureBlocking(false)`, `Selector`, `SelectionKey`, trạng thái kết nối và outbound queue/backpressure.
5. Học TLS/HTTP/application protocol riêng khi mục đích thực tế yêu cầu; ưu tiên API/framework chuẩn thay vì tự viết HTTP/TLS.
6. Cuối cùng kiểm tra failure mode, timeout, retry safety, security, load test và khả năng quan sát trên mạng thật.

## 11. Những ngộ nhận thường gặp

**Ngộ nhận 1: “`SocketChannel` là một HTTP client.”**

Sai. Channel chỉ trao đổi bytes với TCP peer. Không có xử lý HTTP method/status/header mặc định. HTTP client là `java.net.http.HttpClient` hoặc framework phù hợp.

**Ngộ nhận 2: “Một lần `write` tương ứng một lần `read`.”**

Sai với TCP. Bytes có thể được gom/chia thành nhiều lần read. Cần framing và parser tích lũy.

**Ngộ nhận 3: “Có Selector thì server tự động xử lý được mọi request bất đồng bộ.”**

Sai. Selector chỉ báo readiness. Handler blocking hoặc CPU nặng vẫn làm nghẽn event loop; state machine, queue và scheduling do ứng dụng quản lý.

**Ngộ nhận 4: “UDP connect thành công có nghĩa peer đã nhận kết nối.”**

Sai. UDP không sử dụng TCP handshake. `DatagramChannel.connect` cấu hình peer liên quan; không chứng minh bên kia tồn tại hoặc datagram được giao.

**Ngộ nhận 5: “Đã gửi đủ bytes tức là giao dịch đã thành công.”**

Sai. Thành công của local I/O không phải ACK của remote ứng dụng. Cần application-level response và chính sách idempotency khi retry.

**Ngộ nhận 6: “Non-blocking read trả 0 là EOF.”**

Sai. `0` nghĩa chưa chuyển byte trong lời gọi hiện tại (có thể do buffer rỗng hoặc trạng thái chưa sẵn sàng); `-1` mới là EOF của stream đối với đọc TCP.

**Ngộ nhận 7: “TCP bảo đảm không mất dữ liệu nghiệp vụ trong mọi trường hợp.”**

Sai. TCP cho mô hình byte stream đáng tin cậy khi kết nối hoạt động; reset, timeout hoặc crash vẫn tạo trạng thái chưa rõ bên kia đã xử lý tới đâu. Cần giao thức xác nhận/khôi phục cấp ứng dụng.

**Ngộ nhận 8: “TLS là một option tự có của `SocketChannel`.”**

Sai. Dùng SSL/TLS APIs hoặc framework thích hợp, quản lý handshake, validation và lifecycle. Raw SocketChannel không mã hóa payload tự động.

**Ngộ nhận 9: “Trong loopback chạy đúng thì kết nối qua Internet chắc chắn đúng.”**

Sai. Loopback không mô phỏng đầy đủ packet loss, latency, NAT, firewall, proxies, MTU, congestion, certificates, mạng đứt quãng và pressure của server.

## 12. Checklist trước khi chọn Java NIO cho network service

- **Transport:** chọn TCP hay UDP vì yêu cầu delivery/ordering, không chỉ vì muốn tốc độ.
- **Protocol:** xác định framing, encoding, maximum message size, version, parser state.
- **Blocking model:** cần blocking/virtual thread hoặc non-blocking selector? Chọn theo số kết nối, latency, workload và năng lực vận hành.
- **ByteBuffer:** ownership từng connection, `flip/compact`, partial read/write, charset decode across chunks.
- **Backpressure:** giới hạn inbound/outbound queue, per-client quota, chính sách từ chối khi quá tải.
- **Timeout:** kết nối, read, write progress, processing deadline, cancellation, heartbeat.
- **Security:** TLS/hostname verification, authentication, authorization, chống dữ liệu đầu vào độc hại.
- **Resilience:** retry chỉ khi an toàn, idempotency và duplicate handling, phân biệt lỗi transport/protocol/kinh doanh.
- **Resource lifecycle:** đóng channel/selector, bỏ key, tránh leak socket và buffer direct.
- **Testing:** loopback unit demo, integration qua network thật, kiểm thử payload bị chia chunk, lỗi EOF giữa frame, payload quá lớn, mạng chậm/ngắt và sender nhanh hơn receiver.

### Câu hỏi tự kiểm tra

**1. Hai lần `client.write("A")` và `client.write("B")` có bảo đảm server đọc được lần lượt “A” và “B” trong đúng hai lệnh read?**  
Không. TCP giữ thứ tự bytes nhưng không giữ ranh giới các lời gọi write. Server có thể thấy “AB” trong một read hoặc từng phần.

**2. `Selector` có xử lý HTTP request hay chỉ báo readiness?**  
Chỉ báo readiness của các channel được đăng ký. Parser HTTP, việc định tuyến và nghiệp vụ thuộc tầng khác.

**3. `DatagramChannel.receive` nhận một phần datagram rồi có thể đọc tiếp phần còn lại bằng `receive` lần sau?**  
Không. Nếu buffer nhận không đủ, phần vượt quá của datagram có thể bị loại bỏ; lần receive sau là cho datagram khác.

**4. Chọn `SocketChannel` thì traffic đã được mã hóa tự động chưa?**  
Chưa. Cần TLS stack như `SSLEngine` hoặc API/framework HTTPS/TLS thích hợp.

**5. Một request đã `write` xong rồi socket reset, có nên retry mọi request ngay không?**  
Không. Remote application có thể đã xử lý. Cần xác định idempotency và trạng thái xử lý ở tầng ứng dụng.

**6. Khi nào nên dùng `HttpClient` thay vì raw `SocketChannel`?**  
Khi nghiệp vụ cần HTTP/HTTPS client thông thường; thư viện HTTP xử lý các quy tắc giao thức mà raw channel không tự cung cấp.

**7. Vì sao học Java NIO vẫn cần hiểu cơ bản TCP/UDP?**  
Vì kết quả `read/write` và lựa chọn cơ chế framing, timeout, reliability phụ thuộc loại transport. Nhưng nghiên cứu chi tiết congestion control, DNS, TLS hay HTTP là các mảng kiến thức riêng.

## Tài liệu chính thức

- Java SE 21: [`SocketChannel`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/SocketChannel.html), [`ServerSocketChannel`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/ServerSocketChannel.html) — TCP connect, accept, read/write.
- Java SE 21: [`DatagramChannel`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/DatagramChannel.html) — UDP datagram send/receive.
- Java SE 21: [`Selector`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/Selector.html), [`SelectionKey`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/SelectionKey.html) — non-blocking readiness.
- Java SE 21: [`ByteBuffer`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/ByteBuffer.html) — buffer state và byte handling.
- Java SE 21: [`SSLEngine`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/net/ssl/SSLEngine.html) — tích hợp TLS với buffers/transport.
- Java SE 21: [`java.net.http`](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/module-summary.html) — HTTP client và WebSocket APIs.

**Điều cần ghi nhớ:** Java NIO mạng giúp xây *đường đi của bytes và cơ chế readiness*, còn giao thức truyền tin quyết định *bytes biểu thị thông điệp nào, xử lý ra sao, an toàn và tin cậy đến mức nào*. Hãy học cả hai lớp nhưng giữ đúng ranh giới trách nhiệm để không vô tình biến một demo socket thành một protocol production thiếu framing, TLS, timeout hoặc quy tắc khôi phục.

</details>

- [Quay lại đầu trang](#back-to-top)
