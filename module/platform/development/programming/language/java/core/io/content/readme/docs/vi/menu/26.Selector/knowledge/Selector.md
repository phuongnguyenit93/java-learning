<a id="back-to-top"></a>

# Selector

## Menu
- [Selector và mô hình nhiều channel trên một luồng](#selector-001)
- [Đăng ký channel ở chế độ non-blocking](#selector-002)
- [SelectionKey, interest set và ready set](#selector-003)
- [OP_ACCEPT, OP_CONNECT, OP_READ và OP_WRITE](#selector-004)
- [select, selectNow và select có timeout](#selector-005)
- [Lặp selectedKeys và loại bỏ key đã xử lý](#selector-006)
- [Wakeup, hủy key và đóng selector](#selector-007)
- [Sẵn sàng I/O không bảo đảm hoàn tất toàn bộ thao tác](#selector-008)
- [Chỉ đăng ký OP_WRITE khi còn dữ liệu cần gửi](#selector-009)
- [Tránh busy loop, nghẽn xử lý và dùng chung buffer sai cách](#selector-010)

## <a id="selector-001">Selector và mô hình nhiều channel trên một luồng</a>

<details>
<summary>Xem chi tiết</summary>

# Selector và mô hình nhiều channel trên một luồng (Java NIO)

Thông thường, khi một ứng dụng mạng sử dụng lời gọi đọc blocking, mỗi kết nối có thể cần một luồng riêng để chờ dữ liệu. Java NIO cung cấp **`Selector`** để một luồng có thể theo dõi **nhiều channel có khả năng lựa chọn (selectable channels)** và chỉ xử lý các thao tác I/O mà hệ điều hành/provider báo là **có khả năng tiến triển** (*readiness*). Kiến trúc này là nền tảng của nhiều **event loop** và máy chủ mạng quản lý nhiều kết nối.

Điều cốt lõi: **Selector không tự đọc/ghi dữ liệu, không tạo luồng cho mỗi channel và không đảm bảo một lần `read` hoặc `write` chuyển hết dữ liệu.** Nó phối hợp với `SelectableChannel`, `SelectionKey`, `ByteBuffer` và logic ứng dụng để đợi rồi xử lý sự kiện.

## 1. Selector giải quyết vấn đề gì?

Hãy hình dung máy chủ có nhiều kết nối TCP, phần lớn thời gian mỗi kết nối chỉ chờ một yêu cầu tiếp theo:

```text
Mô hình đơn giản: một luồng cho một kết nối

Client A ── Socket A ── Thread A ── read() chờ
Client B ── Socket B ── Thread B ── read() chờ
Client C ── Socket C ── Thread C ── read() chờ
...                        ...

Mô hình event loop dùng Selector

Client A ── SocketChannel A ─┐
Client B ── SocketChannel B ─┼── Selector.select() ── Event-loop thread
Client C ── SocketChannel C ─┘                       ├── A: OP_READ?
                                                    ├── B: OP_WRITE?
                                                    └── C: OP_CONNECT?
```

Một luồng đợi readiness của nhiều channel, sau đó xử lý từng `SelectionKey` phù hợp. Điều này có thể giảm chi phí quản lý nhiều luồng đang chờ I/O, nhất là với lượng lớn kết nối phần lớn thời gian rảnh.

Tuy nhiên, **một event-loop thread vẫn chỉ thực hiện code tại một thời điểm**. Nếu handler chạy CPU nặng, gọi database blocking, `Thread.sleep` lâu hoặc chờ một Future blocking, tất cả kết nối do luồng đó quản lý có thể bị chậm. Vì vậy event loop thường làm I/O và logic ngắn gọn, giao tác vụ blocking/CPU nặng sang nhóm worker phù hợp.

## 2. Các thành phần và quan hệ sở hữu

| Thành phần | Vai trò |
| --- | --- |
| `Selector` | Đăng ký nhiều `SelectableChannel`, đợi readiness, cung cấp tập khóa sẵn sàng. |
| `SelectableChannel` | Channel có thể đặt non-blocking và đăng ký với một hoặc nhiều selector; ví dụ `SocketChannel`, `ServerSocketChannel`, các channel của `Pipe`. |
| `SelectionKey` | Quan hệ đăng ký **một channel trên một selector**, chứa interest ops, ready ops và attachment. |
| `SelectionKey.interestOps()` | Những loại sự kiện ứng dụng muốn selector giám sát. |
| `SelectionKey.readyOps()` | Những loại thao tác mà lựa chọn gần nhất cho thấy có thể tiến triển. |
| `SelectionKey.attachment()` | State tùy ý của ứng dụng, ví dụ buffer đọc dở, hàng đợi ghi, parser. |
| `ByteBuffer` | Dữ liệu được đọc/ghi qua channel, với `position`, `limit` và `remaining()`. |

Một channel có thể được đăng ký với nhiều selector, nhưng với **cùng một selector**, mỗi channel có tối đa một đăng ký/`SelectionKey` còn hiệu lực. Thao tác `register(selector, ops, attachment)` trên channel đã đăng ký có thể cập nhật interest/attachment của khóa hiện có, không tạo vô hạn khóa trùng.

`Selector.open()` chọn provider khả dụng; không nên phụ thuộc cứng vào cơ chế native như `epoll`, `kqueue` hay `poll` để suy ra mọi đặc tính nền tảng. JVM và hệ điều hành chọn triển khai thích hợp.

## 3. Interest ops và ready ops: hai tập khác nhau

Các thao tác quan trọng:

| Operation | Channel thường dùng | Khi nào quan tâm? |
| --- | --- | --- |
| `SelectionKey.OP_ACCEPT` | `ServerSocketChannel` | Server có kết nối mới chờ `accept()`. |
| `SelectionKey.OP_CONNECT` | `SocketChannel` | Kết nối non-blocking đang có thể hoàn tất; gọi `finishConnect()`. |
| `SelectionKey.OP_READ` | `SocketChannel`, `Pipe.SourceChannel` | Có khả năng đọc được dữ liệu, EOF hoặc trạng thái cần xử lý. |
| `SelectionKey.OP_WRITE` | `SocketChannel`, `Pipe.SinkChannel` | Có khả năng ghi thêm dữ liệu vào channel. |

Mỗi loại channel chỉ hỗ trợ một tập ops được công bố qua `validOps()`. Không đăng ký `OP_ACCEPT` cho `SocketChannel` hoặc `OP_READ` cho `ServerSocketChannel`: tổ hợp không được hỗ trợ có thể ném `IllegalArgumentException`.

Ví dụ:

```java
// Snippet: socket là SocketChannel non-blocking đã kết nối.
SelectionKey key = socket.register(selector, SelectionKey.OP_READ);
int wanted = key.interestOps(); // Ứng dụng đang quan tâm đến OP_READ.
int ready = key.readyOps();     // Readiness được phát hiện ở lần chọn gần đây.
```

Hai tập cờ dạng bitmask này **không đồng nghĩa**. `interestOps` là ý định theo dõi; `readyOps` là tín hiệu readiness đã chọn. Kiểm tra an toàn bằng `key.isValid()` trước các phương thức phụ thuộc trạng thái khóa. Các tiện ích như `isReadable()`, `isWritable()`, `isAcceptable()`, `isConnectable()` tương ứng với bit trong ready ops.

**Readiness không bảo đảm thao tác sẽ chuyển dữ liệu.** Giữa lúc `select()` trả về và lời gọi I/O, trạng thái có thể thay đổi; một lần `read`/`write` trên non-blocking channel vẫn có thể trả 0. Luôn xử lý kết quả thực tế.

## 4. Bắt buộc cấu hình non-blocking trước khi register

Để đăng ký channel với selector, channel phải ở chế độ **non-blocking**:

```java
// Snippet: server là ServerSocketChannel đang mở.
server.configureBlocking(false);
server.register(selector, SelectionKey.OP_ACCEPT);
```

Nếu channel vẫn blocking, đăng ký có thể ném `IllegalBlockingModeException`. Với `SocketChannel` đã kết nối:

```java
socket.configureBlocking(false);
socket.register(selector, SelectionKey.OP_READ, connectionState);
```

Đây là snippet; `selector` và `connectionState` do chương trình khai báo. Khi channel đã đăng ký với bất kỳ selector nào, không thể tùy tiện chuyển ngược về blocking mode; cố cấu hình blocking thường gây `IllegalBlockingModeException` khi còn đăng ký hợp lệ.

**FileChannel không phải SelectableChannel** và không dùng `register(selector, OP_READ)`. Thao tác đọc file theo `FileChannel` vẫn có thể blocking; khi cần event loop mạng, hãy xử lý file I/O có chiến lược riêng như thread pool, asynchronous API phù hợp hoặc truyền dữ liệu được kiểm soát.

## 5. Chu trình event loop chuẩn

Luồng xử lý thường thấy:

```text
1. Selector.open()
2. Tạo + configureBlocking(false) cho các channel
3. register(selector, interestOps, attachment)
                    |
                    v
          selector.select()
                    |
       +------------+-------------+
       | selectedKeys()           |
       v                          |
4. Duyệt từng SelectionKey        |
5. iterator.remove()              |
6. Kiểm tra key.isValid()         |
7. Xử lý ACCEPT/CONNECT/READ/WRITE|
8. Cập nhật interestOps, buffers  |
9. Đóng/cancel khi cần            |
       |                          |
       +--------------------------+
```

Khung Java điển hình:

```java
// Snippet: selector đã được mở, channel đã được register.
while (running) {
    selector.select(); // Chờ ít nhất một event hoặc wakeup/interrupt.
    Iterator<SelectionKey> it =
            selector.selectedKeys().iterator();

    while (it.hasNext()) {
        SelectionKey key = it.next();
        it.remove(); // Xóa khỏi selected-key set sau khi lấy ra.

        if (!key.isValid()) {
            continue;
        }
        if (key.isAcceptable()) {
            handleAccept(key);
        }
        if (key.isValid() && key.isConnectable()) {
            handleConnect(key);
        }
        if (key.isValid() && key.isReadable()) {
            handleRead(key);
        }
        if (key.isValid() && key.isWritable()) {
            handleWrite(key);
        }
    }
}
```

Đoạn trên là **pseudocode dạng Java**, giả định tồn tại `running` và các hàm `handle...` trong chương trình của bạn. Từng handler có thể hủy khóa, đóng channel hoặc thay đổi interest ops, nên cần kiểm tra tính hợp lệ phù hợp trước lần sử dụng tiếp theo.

**Vì sao `iterator.remove()` là bắt buộc trong mẫu này?** `selectedKeys()` là tập khóa được chọn, không được tự làm sạch mỗi vòng. Nếu không xóa key đã xử lý, nó có thể tiếp tục xuất hiện và gây việc xử lý lặp trên sự kiện cũ. Không xóa bằng `selector.selectedKeys().clear()` giữa lúc đang duyệt cùng iterator.

## 6. Ví dụ Java 17+ chạy độc lập: một Selector giám sát hai Pipe

Để minh họa cơ chế **một luồng – nhiều channel** mà không phải phụ thuộc mạng hoặc port, ví dụ dùng **hai `Pipe.SourceChannel`**. Chúng là selectable channels có thể nhận `OP_READ`. Hai `Pipe.SinkChannel` ghi thông điệp và selector đọc từ các source tương ứng.

Mỗi thông điệp kết thúc bởi ký tự xuống dòng. Buffer đọc cố ý nhỏ (3 byte) để minh họa **một thông điệp có thể được nhận thành nhiều chunk**, nên trạng thái chưa hoàn chỉnh được giữ trong `Session.pending`. Khi đủ thông điệp, chương trình hủy key. Kết quả được lưu trong `TreeMap` để in theo thứ tự ổn định, vì **thứ tự các ready keys không được bảo đảm**.

Lưu đúng tên **`SelectorMultiplexDemo.java`**:

```java
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

public class SelectorMultiplexDemo {
    private static final class Session {
        final String name;
        final ByteArrayOutputStream pending = new ByteArrayOutputStream();
        Session(String name) { this.name = name; }
    }

    private static void send(Pipe.SinkChannel sink, String text)
            throws IOException {
        ByteBuffer data = ByteBuffer.wrap(
                (text + "\n").getBytes(StandardCharsets.US_ASCII));
        while (data.hasRemaining()) {
            sink.write(data);
        }
    }

    public static void main(String[] args) throws IOException {
        Pipe a = Pipe.open();
        Pipe b = Pipe.open();
        try (Selector selector = Selector.open();
             Pipe.SourceChannel readA = a.source();
             Pipe.SinkChannel writeA = a.sink();
             Pipe.SourceChannel readB = b.source();
             Pipe.SinkChannel writeB = b.sink()) {

            readA.configureBlocking(false);
            readB.configureBlocking(false);
            readA.register(selector, SelectionKey.OP_READ, new Session("A"));
            readB.register(selector, SelectionKey.OP_READ, new Session("B"));

            send(writeA, "alpha");
            send(writeB, "beta");

            Map<String, String> received = new TreeMap<>();
            while (received.size() < 2) {
                if (selector.select(2_000) == 0) {
                    throw new IOException("Timeout chờ dữ liệu");
                }
                Iterator<SelectionKey> iterator =
                        selector.selectedKeys().iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();
                    if (!key.isValid() || !key.isReadable()) {
                        continue;
                    }
                    Session session = (Session) key.attachment();
                    Pipe.SourceChannel channel =
                            (Pipe.SourceChannel) key.channel();
                    ByteBuffer chunk = ByteBuffer.allocate(3);
                    while (key.isValid()) {
                        int n = channel.read(chunk);
                        if (n == -1) {
                            throw new IOException("EOF trước dấu kết thúc");
                        }
                        if (n == 0) {
                            break;
                        }
                        chunk.flip();
                        while (chunk.hasRemaining()) {
                            byte ch = chunk.get();
                            if (ch == '\n') {
                                received.put(session.name,
                                        session.pending.toString(
                                                StandardCharsets.US_ASCII));
                                key.cancel();
                                break;
                            }
                            session.pending.write(ch);
                        }
                        chunk.clear();
                    }
                }
            }
            System.out.println("channels=" + received.size());
            received.forEach((name, message) ->
                    System.out.println(name + "=" + message));
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 SelectorMultiplexDemo.java
java SelectorMultiplexDemo
```

Stdout đã đối chiếu khi biên dịch và chạy thực tế trên Java 17+:

```text
channels=2
A=alpha
B=beta
```

Điểm cần quan sát:

1. `Pipe.open()` tạo **hai** cặp source/sink. Chương trình chỉ chạy một `main` thread xử lý selection, không tạo thread per channel.
2. Hai source được `configureBlocking(false)` rồi `register(selector, OP_READ, Session)`. Mỗi khóa gắn tên và vùng chứa dữ liệu chưa hoàn chỉnh.
3. `send` ghi các chuỗi ASCII cùng newline vào hai sink. Phương thức `write` được gọi lặp đến khi buffer hết byte; sink trong demo vẫn ở chế độ blocking và chỉ ghi lượng nhỏ **trước** vòng event loop nên phù hợp với ví dụ khép kín.
4. `selector.select(2_000)` đợi tối đa khoảng hai giây để có ready keys; đoạn demo báo lỗi nếu không có tiến triển, giúp tránh chờ vô hạn khi môi trường không cung cấp dữ liệu như dự kiến.
5. Mỗi `SourceChannel.read` đọc tối đa ba byte một lần. Nếu trả 0, nguồn non-blocking hiện chưa có thêm byte; vòng inner thoát để trở về selector. Khi gặp `\n`, thông điệp hoàn chỉnh được lưu vào map, khóa được hủy.
6. Output được sắp xếp A rồi B bằng `TreeMap`. Không giả định `selectedKeys()` luôn duyệt key đăng ký trước rồi key đăng ký sau.
7. `try-with-resources` đóng selector và bốn channel khi kết thúc. Demo không gọi mạng, không mở port và không dùng nhiều thread.

**Giới hạn có chủ ý của demo:** chỉ nhận một dòng ASCII ngắn trên mỗi pipe và không cho phép thông điệp lớn hoặc nhiều dòng trong cùng chunk. Trong server thật, handler cần xử lý nhiều frame trong một lượt đọc, ràng buộc kích thước buffer, bảo vệ khỏi dữ liệu ác ý và giữ state qua các vòng select.

## 7. Đưa mô hình vào TCP ServerSocketChannel

Một máy chủ TCP thường bắt đầu bằng:

```java
// Snippet: cần imports Selector, SelectionKey,
// ServerSocketChannel, SocketChannel, InetSocketAddress.
Selector selector = Selector.open();
ServerSocketChannel server = ServerSocketChannel.open();
server.configureBlocking(false);
server.bind(new InetSocketAddress("127.0.0.1", 8080));
server.register(selector, SelectionKey.OP_ACCEPT);
```

Khi nhận `OP_ACCEPT`:

```java
// Snippet: key lấy từ vòng selectedKeys().
ServerSocketChannel server =
        (ServerSocketChannel) key.channel();
SocketChannel client = server.accept();
if (client != null) {
    client.configureBlocking(false);
    client.register(selector, SelectionKey.OP_READ,
            new ConnectionState());
}
```

`ConnectionState` là lớp **ứng dụng phải định nghĩa**, ví dụ chứa `ByteBuffer input`, hàng đợi output và parser state. Hai đoạn này là **minh họa**, chưa bao gồm vòng đời đóng server/socket, xử lý error hay import đầy đủ.

Với non-blocking server, `accept()` có thể trả `null` dù trước đó readiness báo accept; điều kiện có thể thay đổi. Không dùng vòng chờ `while (accept() == null) { }`. Khi một sự kiện cho phép, có thể lặp `accept()` đến khi trả null để xử lý nhiều kết nối đang chờ, nhưng phải đặt ngân sách công việc hợp lý để không bỏ đói các channel khác.

Một socket vừa accept cần `configureBlocking(false)` **trước khi** đăng ký `OP_READ`; nếu quên, `register` có thể thất bại. Luồng event loop có thể giữ danh sách state theo attachments mà không cần mỗi kết nối một Java thread.

## 8. OP_CONNECT: kết nối non-blocking chưa hoàn tất

Với client chủ động kết nối:

```java
// Snippet: selector đã mở.
SocketChannel client = SocketChannel.open();
client.configureBlocking(false);
boolean connected = client.connect(remoteAddress);
if (connected) {
    client.register(selector, SelectionKey.OP_READ, state);
} else {
    client.register(selector, SelectionKey.OP_CONNECT, state);
}
```

`remoteAddress` và `state` phải được khai báo ở ngữ cảnh ứng dụng. Khi key báo `isConnectable()`:

```java
SocketChannel client = (SocketChannel) key.channel();
if (client.finishConnect()) {
    key.interestOps(SelectionKey.OP_READ);
}
```

Trước khi `finishConnect()` thành công, không coi socket là đã sẵn sàng để xử lý application protocol. Kết nối có thể thất bại bằng `IOException`. Khi thất bại, cần hủy đăng ký, đóng channel và thông báo lỗi/áp dụng retry policy; không tiếp tục kích hoạt `OP_READ` trên socket hỏng.

Lưu ý: `OP_CONNECT` là sự kiện để **kiểm tra và hoàn tất kết nối**, không phải bằng chứng kết nối đã thành công.

## 9. OP_READ: đọc bao nhiêu byte là đủ?

Một `OP_READ` nghĩa là đọc có khả năng tiến triển. Với `SocketChannel` non-blocking:

- `read(buffer) > 0`: đã đưa số byte đó vào buffer và tăng `position`.
- `read(buffer) == 0`: chưa chuyển byte; thoát khỏi vòng thử đọc hiện tại và chờ readiness mới.
- `read(buffer) == -1`: peer đóng phía ghi/EOF; tùy protocol, có thể chấp nhận half-close, hoặc đóng kết nối sau khi xử lý dữ liệu còn lại.

Mẫu trạng thái nếu parser có thể giữ frame chưa đủ:

```java
// Snippet: socket non-blocking; state.input là ByteBuffer
// đang ở pha ghi, đủ dung lượng xử lý tối thiểu header.
int n = socket.read(state.input);
if (n == -1) {
    closeConnection(key);
} else if (n > 0) {
    state.input.flip();
    decodeAvailableFrames(state.input, state);
    state.input.compact();
}
```

Các hàm `closeConnection`/`decodeAvailableFrames` và kiểu `state` là **khung thiết kế**, không phải API có sẵn trong JDK. `decodeAvailableFrames` chỉ được tiêu thụ các byte thực sự cấu thành frame đầy đủ hoặc tiếp tục giữ state cần thiết. `compact()` giữ các byte chưa được parser tiêu thụ, đưa chúng về đầu buffer để lần `read` tiếp nối sau đó.

**Không đồng nhất một TCP `read` với một request.** TCP là luồng byte: một `read` có thể nhận nửa header, một request đầy đủ hoặc nhiều request ghép. Chọn framing: length-prefix, delimiter, fixed-size hoặc protocol decoder tương ứng. Với dữ liệu không tin cậy, phải có giới hạn độ dài, số frame, tốc độ, bộ nhớ và thời gian.

Nếu parser không thể tiến triển và input buffer đầy, cần có kế hoạch tăng dung lượng có hạn mức, stream payload hoặc từ chối request; gọi `read` vào buffer không còn `remaining()` chỉ tạo tình trạng không tiến triển.

## 10. OP_WRITE: chỉ đăng ký khi có dữ liệu cần gửi

Với kết nối TCP, socket thường sẵn sàng để ghi trong phần lớn thời gian khi send buffer của OS còn chỗ. Nếu một chương trình giữ `OP_WRITE` trong interest ops **liên tục dù không có dữ liệu chờ**, selector có thể liên tục đánh thức event loop, gây **busy loop** và tốn CPU.

Chiến lược phổ biến:

1. Mặc định theo dõi `OP_READ`.
2. Khi có thông điệp muốn gửi, encode thành `ByteBuffer`, enqueue vào outbound queue của connection.
3. Thử `socket.write(buffer)`. Nếu còn byte chưa gửi, giữ nguyên buffer với `position` đã tiến và bật `OP_WRITE`.
4. Khi nhận `OP_WRITE`, ghi các buffer tồn đến khi hết dữ liệu, đạt giới hạn ngân sách hoặc `write` trả 0.
5. Khi outbound queue trống, **gỡ `OP_WRITE` khỏi interest ops** để tránh đánh thức không cần thiết.

Mẫu bật/tắt bitmask:

```java
// Bật quan tâm write, giữ nguyên các flags khác:
key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);

// Khi đã gửi hết:
key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
```

Đây là snippet; key phải còn hợp lệ. **Không** sử dụng `interestOps(SelectionKey.OP_WRITE)` một cách mù quáng nếu vẫn cần đọc, vì thao tác đó thay **toàn bộ** bitmask và có thể vô tình tắt `OP_READ`.

Mẫu xử lý ghi (có hàng đợi `ByteBuffer` do ứng dụng quản lý):

```java
// Snippet: pending là ByteBuffer đầu hàng đợi,
// socket non-blocking, key hợp lệ.
while (pending.hasRemaining()) {
    int written = socket.write(pending);
    if (written == 0) {
        break; // Giữ position/limit để thử tiếp khi OP_WRITE sẵn sàng.
    }
}
```

`write` có thể ghi ít hơn `remaining()`. Đừng `clear()` hoặc tạo lại buffer chưa ghi hết; sẽ mất dữ liệu hoặc gửi trùng. Một outbound queue thực tế cần điều phối nhiều buffer, giới hạn kích thước chờ (backpressure) và đóng kết nối chậm theo chính sách.

## 11. selectedKeys, keys và cancellation

Cần phân biệt các tập:

| API | Ý nghĩa |
| --- | --- |
| `selector.keys()` | Các khóa đang đăng ký với selector (trừ các khóa đã được loại khỏi tập theo tiến trình hủy). |
| `selector.selectedKeys()` | Tập khóa có readiness được chọn, dành cho ứng dụng duyệt và **xóa key đã xử lý**. |
| `key.cancel()` | Đánh dấu hủy đăng ký; khóa trở nên không hợp lệ, deregistration được xử lý trong các bước selection tiếp theo. |
| `key.isValid()` | Kiểm tra đăng ký còn hợp lệ ở thời điểm gọi. |
| `key.channel()` / `key.selector()` | Truy xuất channel/selector liên quan. |
| `key.attach(obj)` / `key.attachment()` | Lưu/đọc state của connection, giảm nhu cầu map toàn cục. |

Điều thực tiễn: **`key.cancel()` không đồng nghĩa đóng channel**. Nếu kết nối đã hỏng hoặc ứng dụng đã xử lý xong, cần đóng `SocketChannel`/`Pipe.SourceChannel` khi bạn sở hữu nó; đóng channel sẽ làm các đăng ký liên quan mất hiệu lực, nhưng không nhất thiết tự đóng selector.

Đừng lặp toàn bộ `selector.keys()` để tìm ready ops sau `select()`; hãy dùng `selectedKeys()`. Không tự sửa những tập khóa không được API cho phép tùy tiện; mẫu phổ biến là `iterator.remove()` trên selected set.

Một key có thể đồng thời ready nhiều ops hợp lệ, nên handler phải cân nhắc thứ tự, kiểm tra `isValid()` sau bước có khả năng hủy key và giới hạn lượng công việc mỗi vòng để các kết nối khác được phục vụ công bằng.

## 12. select(), select(timeout), selectNow() và wakeup()

| Phương thức | Ý nghĩa chính |
| --- | --- |
| `select()` | Chờ đến khi selection có điều kiện hoàn tất; có thể được đánh thức bởi `wakeup()`/interrupt/close. |
| `select(long timeout)` | Chờ tối đa thời gian nhất định tính bằng millisecond nếu timeout dương; `timeout == 0` có nghĩa chờ không giới hạn theo API. |
| `selectNow()` | Thực hiện selection **không chờ**. |
| `wakeup()` | Yêu cầu đánh thức một thao tác `select` đang hoặc sắp bị chặn; có hiệu lực đối với chu kỳ chọn. |
| `close()` | Đóng selector; các đăng ký liên quan bị hủy, không tự mặc nhiên đóng tất cả channel. |

`select()` trả về số key có thông tin readiness được cập nhật trong lần selection, **không** phải số request đã xử lý hay tổng số byte có thể đọc. Một lần trả 0 không nhất thiết là lỗi: có thể do timeout, wakeup hoặc ngữ cảnh chọn khác. Một event loop thực tế phải xử lý trả 0 hợp lý, tránh cứ quay vòng liên tục vì không có tiến triển.

Khi một thread khác cập nhật state ứng dụng để event loop cần xử lý nhanh, mô hình phổ biến:

```java
// Snippet: executed by producer thread.
pendingTasks.add(task); // Hàng đợi thread-safe do ứng dụng cấp.
selector.wakeup();
```

Event loop khi thức dậy sẽ lấy việc từ hàng đợi và cập nhật registration/interest ops. **Không** để producer thread trực tiếp chỉnh state không thread-safe của nhiều handler. Nếu quan tâm cấu hình concurrent selector, tham khảo quy tắc đồng bộ của JDK; đừng dựa vào “thường chạy đúng” để thay hợp đồng.

Waking selector **không gửi dữ liệu mạng** và không bảo đảm socket writable; đó chỉ là tín hiệu điều phối, sau đó event loop vẫn phải kiểm tra trạng thái channel và queue.

## 13. Những việc tuyệt đối không nên làm trong event loop

- **Không gọi blocking I/O** trong handler của một event loop quản lý nhiều kết nối: ví dụ JDBC đồng bộ, đọc file lớn trực tiếp hoặc network call blocking.
- **Không busy-spin khi `read/write == 0`.** Với non-blocking channel, quay về `select` và đợi readiness phù hợp.
- **Không giữ `OP_WRITE` thường trực khi outbound queue trống.** Readiness ghi có thể được thông báo liên tiếp làm nóng CPU.
- **Không giả định `readyOps` bảo đảm chuyển một số byte nhất định.** Lệnh `read/write` vẫn có thể chuyển ít, thậm chí 0.
- **Không dùng một `ByteBuffer`/parser state duy nhất cho mọi connection** nếu dữ liệu có thể xen kẽ; mỗi connection cần state riêng hoặc cơ chế sở hữu được kiểm soát.
- **Không xử lý toàn bộ một connection vô hạn trong một readiness**. Giới hạn số byte, số frame hoặc thời gian mỗi key để đảm bảo fairness.
- **Không giữ hàng đợi outbound không giới hạn**. Kết nối chậm có thể khiến ứng dụng hết bộ nhớ.
- **Không bỏ qua khóa invalid hoặc channel đóng** khi dọn tài nguyên; `CancelledKeyException`/`ClosedChannelException` có thể xảy ra nếu state thay đổi.
- **Không dùng Selector như sự thay thế cho application protocol**: framing, encoding, validation, authorization, timeout và recovery vẫn thuộc tầng ứng dụng.

## 14. Mô hình kiến trúc một thread và cách mở rộng

Với một số lượng lớn connection, một luồng theo dõi readiness của nhiều channel là hiệu quả khi từng handler làm việc nhẹ. Khi hệ thống tăng tải, có thể dùng **nhiều event-loop thread**, mỗi luồng sở hữu selector riêng và tập connection khác nhau, kết hợp worker pool cho CPU/việc blocking. Việc một kênh thuộc selector nào là quyết định kiến trúc; tránh đồng thời xử lý cùng một connection từ nhiều event loop khi chưa có chiến lược ownership.

Ví dụ phân tầng:

```text
                      Acceptor / ServerSocketChannel
                                  |
                       Chấp nhận TCP connection
                                  |
                           Phân chia connection
                          /                 \
                 Event loop A            Event loop B
                   Selector A              Selector B
                   N sockets               M sockets
                      |                       |
               Decode + queue          Decode + queue
                          \                 /
                           Worker pool
                        (CPU/blocking jobs)
                                  |
                         Kết quả qua task queue
                         + selector.wakeup()
```

Đây là mô hình minh họa, không phải yêu cầu bắt buộc mọi hệ thống phải dùng nhiều selector. Với dịch vụ có traffic vừa phải, một event loop đơn giản, rõ ràng và đo được có thể đủ; thêm nhiều thread/selector đòi quản lý thread ownership, hàng đợi, đồng bộ state và shutdown phức tạp hơn.

### Độ phức tạp và giới hạn

Selector giảm nhu cầu giữ một thread blocking trên mỗi connection, nhưng **không làm số socket/file descriptor miễn phí**. Tài nguyên OS, bộ nhớ mỗi connection, số buffer, quota, latency của handler, TLS, timeout và backpressure vẫn giới hạn sức chứa. Hiệu năng thực tế phải được đo với mẫu traffic đại diện.

Không phải mọi channel đều hỗ trợ selector, không phải mọi loại I/O đều là readiness API. Ví dụ các stream/file channel thông thường có thể cần chiến lược xử lý khác. Lựa chọn event loop dựa trên mô hình workload và khả năng kiểm soát state.

## 15. Lỗi thường gặp và cách chẩn đoán

| Triệu chứng | Nguyên nhân có thể | Cách sửa |
| --- | --- | --- |
| `IllegalBlockingModeException` | `register` khi channel đang blocking hoặc đổi sang blocking khi còn đăng ký. | `configureBlocking(false)` trước `register`; quản lý lifecycle rõ. |
| `IllegalArgumentException` tại `register` | Interest ops không thuộc `validOps()` của channel. | Chọn đúng OP_ACCEPT/CONNECT/READ/WRITE theo loại channel. |
| CPU 100% khi ít traffic | Luôn bật `OP_WRITE` hoặc spin khi `read/write == 0`. | Gỡ OP_WRITE khi queue trống; chờ readiness/wakeup. |
| Một request bị xử lý lặp | Không xóa key trong `selectedKeys()`. | Dùng iterator.remove() ngay trong vòng duyệt. |
| `CancelledKeyException` | Key bị cancel/đóng channel rồi vẫn kiểm tra readiness/interest. | Kiểm tra `isValid()` và dừng handler khi đóng. |
| Một connection làm chậm toàn bộ server | Handler blocking, CPU nặng hoặc loop xử lý không giới hạn. | Budget mỗi event, chuyển blocking/CPU sang worker. |
| Lệnh `write` báo thành công nhưng client nhận thiếu | Không lặp theo `ByteBuffer.hasRemaining()`, mất state khi partial write. | Giữ buffer pending, kích OP_WRITE đến khi gửi hết. |
| Server chờ mãi dù client đã gửi một phần | Parser đòi một frame đầy đủ nhưng không giữ state/compact đúng. | Xây framing, lưu input giữa các lượt và giới hạn kích thước. |
| Không thấy client kết nối | Quên đăng ký `OP_ACCEPT` hoặc `finishConnect()` với client non-blocking. | Phân biệt server/client lifecycle và OP_CONNECT. |
| Sau `wakeup()` không có key mới | Wakeup chỉ đánh thức `select`, không tạo I/O readiness. | Xử lý hàng đợi task rồi kiểm tra key như bình thường. |
| Dọn socket nhưng selector vẫn còn key invalid | Cancel được xử lý theo selection lifecycle; selected set chưa được dọn. | Hủy key, đóng channel, bỏ key đã xử lý và đóng selector khi shutdown. |

Trong khi debug, log có cấu trúc cho mỗi connection: ID, interest ops, ready ops, bytes read/written, `buffer.position/limit`, số byte pending, nguyên nhân đóng và thời gian xử lý handler. Cẩn trọng không log payload chứa thông tin nhạy cảm hoặc làm logger blocking trong event loop.

## 16. Bài tập thực hành

**Bài 1 — Thêm channel thứ ba.** Mở rộng chương trình demo với `Pipe c`, đăng ký thêm `SourceChannel` non-blocking và gửi `gamma\n`. Chỉnh điều kiện hoàn thành để đợi 3 thông điệp. Dùng `TreeMap` để giữ kết quả có thứ tự, không phụ thuộc thứ tự readiness.

**Bài 2 — Tách thông điệp thành nhiều chunk.** Đổi đoạn `ByteBuffer.allocate(3)` thành `allocate(1)` rồi `allocate(5)`. Chứng minh output không thay đổi nhờ `Session.pending` và xử lý newline. Giải thích vì sao chỉ gọi `read` một lần rồi lập tức chuyển nội dung thành request là sai.

**Bài 3 — OP_WRITE và backpressure.** Thiết kế outbound queue gồm ba `ByteBuffer` cho mỗi kết nối. Viết pseudocode bật `OP_WRITE` nếu còn pending; tắt khi queue hết; giữ `position` của buffer khi `write == 0`. Thêm quota cho số byte chờ để tránh tăng bộ nhớ vô hạn.

**Bài 4 — TCP echo server.** Dùng `ServerSocketChannel` non-blocking nhận client ở `OP_ACCEPT`, tạo `ConnectionState` với input/output buffers, đọc `OP_READ` và echo dữ liệu ở `OP_WRITE`. Giới hạn dung lượng hàng đợi; đóng socket khi `read == -1` và không còn output cần flush. Kiểm thử hai client gửi từng nửa thông điệp.

**Bài 5 — Thread khác cập nhật event loop.** Tạo một task queue thread-safe. Producer thêm tác vụ rồi gọi `selector.wakeup()`. Event loop nhận task, cập nhật interest ops và xử lý trong thread sở hữu selector. Giải thích tại sao không truyền một `ByteBuffer` mutable cho nhiều luồng mà không quản lý ownership.

**Bài 6 — Graceful shutdown.** Xây cơ chế ngừng nhận kết nối mới, tiếp tục xả outbound queue có deadline, sau đó cancel keys, đóng channels rồi đóng selector. Ghi lại những trường hợp timeout, exception và channel đã đóng để tránh resource leak.

### Tự kiểm tra

1. `Selector.select()` trả về các byte đã đọc hay danh sách channel có readiness?
2. Vì sao `SocketChannel` cần `configureBlocking(false)` trước đăng ký?
3. `interestOps` khác `readyOps` thế nào, và vì sao `OP_WRITE` không nên luôn bật?
4. Khi `socket.read(buffer) == 0`, ta có kết luận EOF hay phải chờ lần readiness tiếp?
5. `key.cancel()` có tự đóng socket không?
6. Vì sao `iterator.remove()` quan trọng khi duyệt `selectedKeys()`?
7. Một event-loop thread có thể xử lý nhiều socket, nhưng tại sao vẫn phải chuyển thao tác blocking/CPU nặng ra ngoài?
8. Vì sao parser cần xử lý partial reads, partial writes và nhiều frame trong cùng một đợt đọc?

## 17. Tham khảo chính thức

- [Java SE 21 — Selector](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/Selector.html): các tập key, selection, wakeup và vòng đời.
- [Java SE 21 — SelectionKey](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/SelectionKey.html): interest/ready ops, attachments, key cancellation và bitmask.
- [Java SE 21 — SelectableChannel](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/SelectableChannel.html): non-blocking, valid ops và registration.
- [Java SE 21 — SocketChannel](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/SocketChannel.html): non-blocking connect, read/write và EOF.

**Ghi nhớ:** Selector là công cụ **multiplex readiness của nhiều selectable channel trên một event-loop thread**. Đăng ký non-blocking channel bằng đúng interest ops, duyệt và xóa selected keys, giữ state riêng mỗi connection, xử lý `read/write` có thể chỉ chuyển một phần, bật `OP_WRITE` khi thật sự có dữ liệu và không cho công việc blocking giữ chân event loop.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="selector-002">Đăng ký channel ở chế độ non-blocking</a>

<details>
<summary>Xem chi tiết</summary>

# Đăng ký channel ở chế độ non-blocking với Java NIO `Selector`

## 1. Mục tiêu và phạm vi

Trong Java NIO, `Selector` cho phép một thread theo dõi **trạng thái sẵn sàng I/O** (*readiness*) của nhiều channel phù hợp, thay vì phải dành riêng một thread chặn ở mỗi socket. Để selector theo dõi một channel, chúng ta cần mở đúng **`SelectableChannel`**, đặt nó ở **non-blocking mode**, rồi đăng ký với `Selector` cùng tập thao tác quan tâm (*interest operations*).

Sau bài này, bạn sẽ:

- Hiểu điều kiện bắt buộc để `register(selector, ops)` thành công: channel phải thuộc `SelectableChannel`, có trạng thái mở, ở non-blocking mode và `ops` hợp lệ với channel.
- Sử dụng đúng thứ tự `configureBlocking(false)`, `Selector.open()`, `register(...)`, `select()` và duyệt `selectedKeys()`.
- Phân biệt `interestOps` (muốn theo dõi) và `readyOps` (selector đã xác định sẵn sàng); hiểu `SelectionKey` và attachment.
- Xử lý lỗi `IllegalBlockingModeException`, `IllegalArgumentException`, `ClosedChannelException`, hủy key và đóng tài nguyên.
- Tránh hai lỗi phổ biến trong vòng lặp event: quên xóa key đã xử lý khỏi tập selected keys và đăng ký `OP_WRITE` liên tục khi chưa có dữ liệu cần gửi.
- Viết ví dụ **Java 17+** chạy được với `Pipe.SourceChannel` và `Selector` trước khi chuyển sang socket thực tế.

Bài tập trung vào **đăng ký non-blocking channel** và hệ quả trực tiếp. Những phần thiết kế protocol TCP lớn, framing, backpressure và kết nối mạng phân tán được nhắc đến đúng mức cần thiết để áp dụng an toàn.

## 2. Ba vai trò: channel, selector và selection key

**`SelectableChannel`** là một lớp channel có thể đăng ký với selector. Các loại tiêu biểu:

| Channel | Thao tác quan tâm hợp lệ thường gặp | Ý nghĩa |
| --- | --- | --- |
| `ServerSocketChannel` | `SelectionKey.OP_ACCEPT` | Server socket có kết nối mới đang đợi `accept()` |
| `SocketChannel` | `OP_CONNECT`, `OP_READ`, `OP_WRITE` | Hoàn tất kết nối, có thể đọc hoặc có thể ghi |
| `DatagramChannel` | `OP_READ`, `OP_WRITE` | Datagram UDP có thể đọc/ghi |
| `Pipe.SourceChannel` | `OP_READ` | Đầu đọc của pipe có byte sẵn sàng |
| `Pipe.SinkChannel` | `OP_WRITE` | Đầu ghi của pipe có thể ghi |
| `FileChannel` | **Không có operation đăng ký selector** | File I/O, không phải `SelectableChannel` |

Các bit operation là **các hằng số của `SelectionKey`**, chẳng hạn `SelectionKey.OP_READ`, chứ không phải của `Selector`. Đừng đoán tập operation từ việc một channel có `read(ByteBuffer)` hay `write(ByteBuffer)`: dùng `channel.validOps()`.

**`Selector`** quản lý các kênh đã đăng ký và thực hiện thao tác chọn kênh có readiness phù hợp. Một selector có thể có nhiều registration.

**`SelectionKey`** biểu diễn một registration giữa **một channel và một selector**. Key nắm:

- `channel()`: channel đã đăng ký.
- `selector()`: selector sở hữu key.
- `interestOps()`: bitmask các thao tác ứng dụng hiện muốn quan sát.
- `readyOps()`: bitmask thao tác được lần chọn báo là sẵn sàng.
- `attachment()`: đối tượng trạng thái tùy chọn của ứng dụng.
- `isValid()`, `cancel()`: trạng thái vòng đời registration.

Một key thuộc về **cặp (channel, selector)**. Nếu channel đã đăng ký với selector đó, gọi `register` lần nữa thường **cập nhật interest ops và attachment của key hiện tại**, không tạo key thứ hai cho cùng cặp.

```text
                  Selector
                     |
       +-------------+-------------+
       |                           |
 SelectionKey A                SelectionKey B
 interestOps=OP_READ           interestOps=OP_ACCEPT
       |                           |
 Pipe.SourceChannel           ServerSocketChannel
       |                           |
  configureBlocking(false)    configureBlocking(false)
```

Sơ đồ minh họa ý nghĩa đăng ký, không phải mệnh đề rằng hai channel này luôn cùng một ứng dụng.

## 3. Vì sao phải gọi `configureBlocking(false)`?

Một selectable channel mới mở thường ở **blocking mode**. Ví dụ `ServerSocketChannel.accept()` có thể chờ một kết nối, còn `SocketChannel.read()` có thể chờ dữ liệu.

Đối với `Selector`, channel được đăng ký **phải ở non-blocking mode**. Quy trình chuẩn:

```java
try (Selector selector = Selector.open();
     ServerSocketChannel server = ServerSocketChannel.open()) {

    server.configureBlocking(false);
    server.bind(new InetSocketAddress("127.0.0.1", 0));
    SelectionKey key = server.register(
            selector, SelectionKey.OP_ACCEPT);

    // Bắt đầu vòng lặp select/accept...
}
```

Đoạn trên là **snippet minh họa**, cần import `java.nio.channels.Selector`, `ServerSocketChannel`, `SelectionKey`, `java.net.InetSocketAddress` và xử lý `IOException` trong phương thức bao quanh. `port=0` cho OS tự chọn cổng; ví dụ không in cổng nên không phụ thuộc giá trị được cấp.

Điều cần nhớ:

1. `configureBlocking(false)` cấu hình channel, **không** tạo selector và cũng không đăng ký ngầm.
2. `register(selector, ops)` gắn channel vào selector với tập thao tác quan tâm ban đầu; bản thân lời gọi này **không chờ** readiness.
3. `selector.select()` mới thực hiện bước đợi/chọn các key có operation đã đăng ký trở nên sẵn sàng.
4. Non-blocking không có nghĩa mọi I/O sẽ thành công đủ byte ngay. `read/write` có thể trả 0; `accept()` có thể trả `null` nếu tại thời điểm gọi không còn kết nối sẵn sàng.

Nếu đang blocking, gọi `register` sẽ ném **`IllegalBlockingModeException`**. Cách sửa là cấu hình `false` trước khi đăng ký, **không** cố bắt ngoại lệ rồi bỏ qua registration.

Sau khi đã đăng ký một channel với selector, không được tùy tiện gọi `configureBlocking(true)`: chuyển lại sang blocking mode khi còn registration hợp lệ sẽ ném `IllegalBlockingModeException`. `cancel()` của key có hiệu lực đánh dấu hủy ngay, nhưng **deregistration được xử lý trong hoạt động chọn tiếp theo của selector**. Vì vậy không nên giả định mọi đăng ký đã được xóa sạch ngay trong mọi thread chỉ vì đã gọi cancel; hãy quản lý trạng thái lifecycle rõ ràng.

## 4. Chọn `interestOps` hợp lệ với `validOps()`

Tham số `ops` của `register(selector, ops)` là một **bitmask**, có thể kết hợp bằng toán tử OR `|`:

```java
int interests = SelectionKey.OP_READ | SelectionKey.OP_WRITE;
```

Đây chỉ là cú pháp kết hợp, **không phải lựa chọn tốt mặc định** cho mọi channel. Mỗi loại channel chỉ hỗ trợ một tập bit nhất định. Kiểm tra:

```java
int requested = SelectionKey.OP_READ;
if ((requested & ~channel.validOps()) != 0) {
    throw new IllegalArgumentException("Operation not supported");
}
```

Snippet giả định `channel` là một `SelectableChannel`. Biểu thức `requested & ~channel.validOps()` tìm bit được yêu cầu nhưng không có trong `validOps`. Nếu khác 0, không nên đăng ký.

Một vài ví dụ:

- `Pipe.SourceChannel` hỗ trợ `OP_READ`. Đăng ký nó với `OP_WRITE` sẽ ném `IllegalArgumentException`.
- `Pipe.SinkChannel` hỗ trợ `OP_WRITE`, không dùng nó như channel đọc.
- `ServerSocketChannel` quan tâm `OP_ACCEPT`; đăng ký `OP_READ` trên server listener là sai kiểu operation.
- `SocketChannel` hỗ trợ `OP_CONNECT` để theo dõi quá trình kết nối chưa hoàn tất, rồi thường chuyển quan tâm sang `OP_READ` và tùy tình huống sang `OP_WRITE`.

Giá trị `ops=0` **hợp lệ**: channel vẫn đăng ký nhưng tạm thời không quan tâm operation nào. Khi cần, ứng dụng có thể cập nhật lại `interestOps`; đây là kỹ thuật hữu ích để điều tiết backpressure.

`validOps()` là thông tin khả năng của loại channel. Nó không nói rằng mọi operation đang được báo ready tại thời điểm kiểm tra.

## 5. `register` tạo/cập nhật `SelectionKey` như thế nào?

Hai overload thường dùng:

```java
SelectionKey key = channel.register(selector, ops);
SelectionKey keyWithAttachment = channel.register(
        selector, ops, attachment);
```

Đây là snippet minh họa, cần khai báo `channel`, `selector`, `ops` và `attachment`.

**Attachment** là object ứng dụng lưu cùng key. Ví dụ:

- Buffer đầu vào/đầu ra của một socket.
- Mã kết nối, trạng thái xử lý protocol.
- Một đối tượng giữ state đọc header và chờ đủ payload.

`attach(object)` của `SelectionKey` có thể thay thế attachment hiện tại. `attachment()` trả object đó hoặc `null` nếu chưa gắn. Khi register lại cùng channel/selector, dùng overload không có attachment sẽ không nhất thiết thay đổi attachment; dùng overload có attachment để đặt attachment mới một cách rõ ràng.

Cần phân biệt:

| Thao tác | Hiệu ứng |
| --- | --- |
| `register(selector, OP_READ)` | Đăng ký mới hoặc cập nhật interest ops của registration hiện có |
| `key.interestOps(OP_READ)` | Đặt lại toàn bộ tập interest operations của key |
| `key.interestOps(key.interestOps() | OP_WRITE)` | Thêm OP_WRITE vào interest ops hiện có |
| `key.interestOps(key.interestOps() & ~OP_WRITE)` | Loại OP_WRITE khỏi tập quan tâm |
| `key.attach(state)` | Gắn trạng thái riêng của ứng dụng |
| `key.cancel()` | Hủy registration; key trở thành không hợp lệ |
| `channel.close()` | Đóng channel; các registration liên quan bị hủy |

**Cập nhật `interestOps`** không đồng nghĩa điều kiện readiness vừa đổi hoặc đã được selector phản ánh ngay trong một lượt `select` đang diễn ra. Nếu một thread khác sửa interest set trong lúc thread selector đang đợi, cần thiết kế cách đánh thức selector phù hợp (`selector.wakeup()`) và đồng bộ state của ứng dụng.

## 6. Ví dụ Java 17+ chạy được: đăng ký `Pipe.SourceChannel` và xử lý `OP_READ`

`Pipe` giúp học selector mà không phụ thuộc cổng TCP hay firewall. Đầu sink ghi một số byte ngắn; đầu source được đăng ký trong non-blocking mode. Đây là chương trình độc lập.

Lưu tên file **`PipeRegistrationDemo.java`**:

```java
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

public class PipeRegistrationDemo {
    public static void main(String[] args) throws IOException {
        Pipe pipe = Pipe.open();

        try (Pipe.SourceChannel source = pipe.source();
             Pipe.SinkChannel sink = pipe.sink();
             Selector selector = Selector.open()) {

            source.configureBlocking(false);

            SelectionKey key = source.register(
                    selector, SelectionKey.OP_READ, "message-source");

            System.out.println("registered=" + key.isValid());
            System.out.println("attachment=" + key.attachment());
            System.out.println("interestedInRead="
                    + ((key.interestOps() & SelectionKey.OP_READ) != 0));

            byte[] payload = "HELLO".getBytes(StandardCharsets.US_ASCII);
            ByteBuffer toSend = ByteBuffer.wrap(payload);

            // Sink blocking mặc định, payload nhỏ; ghi đủ 5 byte.
            while (toSend.hasRemaining()) {
                sink.write(toSend);
            }

            ByteArrayOutputStream received = new ByteArrayOutputStream();

            while (received.size() < payload.length) {
                int selected = selector.select(2000);
                if (selected == 0) {
                    throw new IOException("Timed out waiting for readable pipe");
                }

                Iterator<SelectionKey> iterator =
                        selector.selectedKeys().iterator();

                while (iterator.hasNext()) {
                    SelectionKey selectedKey = iterator.next();
                    iterator.remove(); // Không xử lý lại key cũ ở lượt sau.

                    if (!selectedKey.isValid() || !selectedKey.isReadable()) {
                        continue;
                    }

                    ByteBuffer input = ByteBuffer.allocate(16);
                    int count = source.read(input);

                    if (count == -1) {
                        throw new IOException("Pipe ended before complete data");
                    }
                    if (count > 0) {
                        input.flip();
                        byte[] bytes = new byte[input.remaining()];
                        input.get(bytes);
                        received.write(bytes, 0, bytes.length);
                    }
                }
            }

            System.out.println("receivedBytes=" + received.size());
            System.out.println("text="
                    + received.toString(StandardCharsets.US_ASCII));
        }
    }
}
```

Biên dịch/chạy trên JDK 17+:

```bash
javac PipeRegistrationDemo.java
java PipeRegistrationDemo
```

Stdout nếu các thao tác pipe, selector và I/O đều thành công:

```text
registered=true
attachment=message-source
interestedInRead=true
receivedBytes=5
text=HELLO
```

**Giải thích chi tiết:**

1. `Pipe.open()` tạo hai đầu liên kết. `source` nhận dữ liệu do `sink` ghi.
2. `source.configureBlocking(false)` phải chạy **trước** `source.register(...)`.
3. `register(selector, OP_READ, "message-source")` tạo key và gắn nhãn tùy chọn.
4. `sink.write(toSend)` chuyển lần lượt toàn bộ 5 byte `HELLO`. Sự kiện đọc từ source là sẵn sàng mà không cần socket mạng.
5. `selector.select(2000)` chờ tối đa 2 giây; nếu hệ thống không báo readiness trong khoảng đó, chương trình báo lỗi thay vì giả vờ thành công.
6. `selectedKeys()` là **tập key được chọn**, cần duyệt và `iterator.remove()` khi đã xử lý. Loại bỏ khỏi tập selected không đồng nghĩa hủy đăng ký.
7. `source.read(input)` trả số byte thực đọc. Vì channel non-blocking, lần đọc có thể trả `0`; chương trình chỉ đưa dữ liệu vào kết quả khi `count > 0`.
8. Khi tích đủ 5 byte, chương trình in chính xác `HELLO`. Không giả định một lần `read()` luôn nhận đủ toàn bộ payload.
9. `try-with-resources` đóng các channel và selector. Sau khi source/sink đóng, không tiếp tục dùng chúng.

**Lưu ý:** Bài toán thực tế dùng socket không thể biết trước lúc nào đủ một message chỉ dựa trên một sự kiện `OP_READ`. Thường cần buffer, framing (độ dài hoặc dấu phân cách) và logic giữ lại byte dở. Ví dụ pipe dùng payload 5 byte đã biết để tập trung vào thao tác đăng ký.

## 7. Ví dụ Java 17+ chạy được: các lỗi đăng ký

Một lỗi thường gặp là cố đăng ký channel đang blocking; lỗi khác là chọn operation không nằm trong `validOps()`.

Lưu file **`RegistrationErrorsDemo.java`**:

```java
import java.nio.channels.IllegalBlockingModeException;
import java.nio.channels.Pipe;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

public class RegistrationErrorsDemo {
    public static void main(String[] args) throws Exception {
        Pipe pipe = Pipe.open();

        try (Pipe.SourceChannel source = pipe.source();
             Pipe.SinkChannel sink = pipe.sink();
             Selector selector = Selector.open()) {

            try {
                // SourceChannel mặc định ở blocking mode.
                source.register(selector, SelectionKey.OP_READ);
            } catch (IllegalBlockingModeException expected) {
                System.out.println("blocking registration rejected");
            }

            source.configureBlocking(false);

            try {
                // Source chỉ đọc được, không đăng ký OP_WRITE.
                source.register(selector, SelectionKey.OP_WRITE);
            } catch (IllegalArgumentException expected) {
                System.out.println("invalid operation rejected");
            }

            SelectionKey key =
                    source.register(selector, SelectionKey.OP_READ);
            System.out.println("read registration valid=" + key.isValid());

            try {
                // Đang có registration hợp lệ: không bật blocking lại.
                source.configureBlocking(true);
            } catch (IllegalBlockingModeException expected) {
                System.out.println("switch to blocking rejected");
            }

            key.cancel();
            System.out.println("valid after cancel=" + key.isValid());
        }
    }
}
```

```bash
javac RegistrationErrorsDemo.java
java RegistrationErrorsDemo
```

Stdout khi chạy thành công:

```text
blocking registration rejected
invalid operation rejected
read registration valid=true
switch to blocking rejected
valid after cancel=false
```

Các ngoại lệ là **tình huống được tạo có chủ ý để học API**, không phải cách khuyến nghị điều khiển luồng chính. Trong sản phẩm thật, hãy kiểm tra thiết kế và cấu hình channel trước khi đăng ký, dùng `validOps()` và kiểm soát key lifecycle.

Khi `key.cancel()` được gọi, `key.isValid()` trở thành `false` ngay. Selector sẽ xử lý việc deregister bị trì hoãn trong quá trình chọn tiếp theo. **Không** suy diễn rằng key hết hợp lệ thì mọi tài nguyên của channel đã được đóng: muốn ngừng dùng channel phải đóng đúng đối tượng sở hữu.

## 8. `Selector.select()`, `selectNow()` và `wakeup()`

Nhóm phương thức chọn thường gặp:

| Phương thức | Hành vi |
| --- | --- |
| `selector.select()` | Có thể chặn cho tới khi có sự kiện chọn, wakeup, interrupt hoặc trường hợp khác theo hợp đồng |
| `selector.select(timeoutMillis)` | Chờ tối đa thời hạn đã chỉ định; số `0` có thể do timeout hoặc các nguyên nhân khác |
| `selector.selectNow()` | Thực hiện thao tác chọn không chờ |
| `selector.wakeup()` | Khiến một thao tác chọn đang đợi (hoặc lần chọn tiếp theo trong những trường hợp phù hợp) trả về |
| `selector.selectedKeys()` | Tập key đã được đưa vào selected-key set |
| `selector.keys()` | Tập key đang đăng ký với selector (khác selected-key set) |

Kết quả trả về của `select()` là **số key được cập nhật/đưa vào tập selected trong lượt chọn**, không phải số byte sẵn sàng và không đồng nghĩa số connection mới. Một `SelectionKey` có thể biểu diễn nhiều operation readiness; không cộng số bit rồi coi là số kết nối.

Lệnh `select(0)` không có nghĩa “poll ngay lập tức”; trong Java API, timeout bằng 0 có thể mang ý nghĩa chờ không giới hạn. Nếu mục đích là kiểm tra không chờ, dùng `selectNow()`.

`wakeup()` hữu ích khi thread khác đã bổ sung công việc, cần selector loop sớm cập nhật `interestOps` hoặc xử lý yêu cầu đóng. Khi dùng selector nhiều thread, quản lý an toàn của danh sách công việc, của key và việc wakeup có ý nghĩa không kém lời gọi API.

Một vòng event loop phải xử lý trường hợp không có key được chọn mà vẫn cần chạy việc khác; không cố xem mọi kết quả `0` là EOF.

## 9. `interestOps` khác `readyOps`: điều ứng dụng muốn so với điều selector thấy

Một key có thể quan tâm `OP_READ` nhưng tại thời điểm kiểm tra chưa có byte nào để đọc:

```text
interestOps = OP_READ        // mong muốn theo dõi
readyOps    = 0              // chưa báo sẵn sàng
```

Khi selector cập nhật key, nó có thể báo `readyOps` chứa `OP_READ`:

```text
interestOps = OP_READ
readyOps    = OP_READ
```

Khi đó gọi `key.isReadable()` tiện hơn so với tự kiểm tra bit:

```java
if (key.isValid() && key.isReadable()) {
    // Thử đọc từ channel nonblocking và kiểm tra số byte thực đọc.
}
```

Đây là snippet, yêu cầu `key` là `SelectionKey` do selector cung cấp. `isReadable()` chỉ nên hỏi khi key hợp lệ; một số thao tác đọc state key không hợp lệ có thể ném `CancelledKeyException`.

Không dùng `isReadable()` thay cho kết quả `read()`. Readiness mang tính tức thời, đặc biệt khi nhiều thread hoặc nhiều đường truy cập cùng dùng kênh; sau event, `read()` vẫn có thể trả 0, có thể đọc ít byte, hoặc trả `-1` nếu phía kia đã đóng và không còn dữ liệu.

`interestOps` là **bitmask muốn theo dõi**, `readyOps` là **bitmask của hoạt động báo ready**; chúng không tương đương và không được giải thích như một độ dài buffer.

## 10. Duyệt selected keys đúng cách

Skeleton event loop điển hình:

```java
while (running) {
    selector.select();

    Iterator<SelectionKey> iterator =
            selector.selectedKeys().iterator();

    while (iterator.hasNext()) {
        SelectionKey key = iterator.next();
        iterator.remove();

        if (!key.isValid()) {
            continue;
        }

        if (key.isAcceptable()) {
            // Nhận kết nối (ServerSocketChannel).
        }
        if (key.isConnectable()) {
            // Hoàn tất connect (SocketChannel.finishConnect()).
        }
        if (key.isReadable()) {
            // Đọc dữ liệu từ channel.
        }
        if (key.isWritable()) {
            // Ghi phần dữ liệu đang chờ trong output queue.
        }
    }
}
```

Đây là **skeleton minh họa**, không phải chương trình độc lập: `running`, `selector`, các handler và import cần do ứng dụng cung cấp. Nó cố ý không đóng vai trò server TCP hoàn chỉnh.

### Tại sao phải gọi `iterator.remove()`?

`selectedKeys()` giữ các key đã được chọn. Nếu không loại key sau xử lý, ứng dụng có thể xử lý lại trạng thái cũ một cách không mong muốn trong các lượt sau. `iterator.remove()` chỉ **gỡ key khỏi selected-key set**, không hủy registration và không đóng channel.

Đừng gọi `selector.selectedKeys().clear()` trước khi handler đọc dữ liệu cần xử lý, vì như vậy sẽ làm mất danh sách sự kiện của lượt hiện tại. Cũng đừng gọi `key.cancel()` chỉ vì đã xử lý một sự kiện: cancel sẽ ngừng registration cho những lượt sau.

### Handler phải kiểm tra kết quả I/O

Đối với `SocketChannel.read(buffer)`:

- `n > 0`: nhận `n` byte.
- `n == 0`: chưa có byte để đọc trong lần gọi nonblocking này.
- `n == -1`: đã đến trạng thái cuối input stream của channel (EOF); cần đóng/cập nhật connection theo protocol.

Đối với `SocketChannel.write(buffer)`, kết quả là số byte **thực ghi**, có thể bằng 0 hoặc nhỏ hơn `buffer.remaining()` trước khi gọi. Vì vậy phải giữ byte chưa ghi trong output buffer/queue, thay vì giả định đã gửi xong.

## 11. Chuyển sang TCP: `OP_ACCEPT`, `OP_CONNECT` và `OP_READ`

### 11.1. Server nhận kết nối

Luồng cơ bản:

```java
ServerSocketChannel server = ServerSocketChannel.open();
server.configureBlocking(false);
server.bind(new InetSocketAddress("127.0.0.1", 8080));

SelectionKey serverKey =
        server.register(selector, SelectionKey.OP_ACCEPT);
```

Đây là snippet cần import, một `Selector` mở sẵn, xử lý `IOException` và quản lý đóng server. Cổng 8080 chỉ để minh họa; thực tế có thể bị chiếm hoặc không có quyền bind.

Khi `serverKey.isAcceptable()`:

```java
ServerSocketChannel listener =
        (ServerSocketChannel) serverKey.channel();
SocketChannel client = listener.accept();

if (client != null) {
    client.configureBlocking(false);
    client.register(selector, SelectionKey.OP_READ);
}
```

Cũng là snippet: `accept()` của server nonblocking **có thể trả `null`** nếu không còn kết nối sẵn sàng. Mỗi client được accept là một channel **mới**; phải cấu hình nonblocking cho chính client đó rồi mới register vào selector.

Trong ứng dụng thật, nên attach state của kết nối, chuẩn bị buffer, giới hạn số client, cấu hình timeout và đảm bảo đóng client khi handler thất bại. Không để channel rò rỉ chỉ vì exception giữa `accept` và `register`.

### 11.2. Client kết nối nonblocking

`SocketChannel.connect(remote)` có thể:

- Trả `true` nếu kết nối hoàn tất ngay.
- Trả `false` nếu quá trình kết nối đang diễn ra; khi đó thường đăng ký `OP_CONNECT` để chờ thông báo.

```java
SocketChannel client = SocketChannel.open();
client.configureBlocking(false);

boolean connected = client.connect(remote);
int ops = connected
        ? SelectionKey.OP_READ
        : SelectionKey.OP_CONNECT;

client.register(selector, ops);
```

Snippet giả định `remote` là `SocketAddress` hợp lệ và `selector` đã mở. Khi key connectable, handler phải gọi `client.finishConnect()`, kiểm tra kết quả và xử lý `IOException` như `ConnectException` nếu kết nối thất bại. Khi kết nối hoàn tất, cập nhật interest ops cho những công việc tiếp theo như `OP_READ`.

Không coi sự kiện `OP_CONNECT` là bằng chứng kết nối thành công tuyệt đối mà bỏ qua `finishConnect()`. Cũng không đăng ký `OP_READ` rồi kỳ vọng đọc được data trước khi hoàn tất connect.

### 11.3. Vì sao `OP_WRITE` cần được bật có điều kiện?

Trên TCP thông thường, socket thường **sẵn sàng ghi** phần lớn thời gian. Nếu đăng ký `OP_WRITE` liên tục ngay cả khi output queue trống, vòng selector có thể thức dậy nhiều lần mà không có công việc, dẫn đến CPU cao.

Mẫu xử lý:

1. Khi ứng dụng có byte chờ gửi, ghi thử vào channel.
2. Nếu chưa gửi hết, giữ lại phần còn lại trong queue/buffer và **thêm `OP_WRITE` vào interest ops**.
3. Khi key writable, ghi tiếp; mỗi lần kiểm tra byte thực sự đã ghi.
4. Khi output queue trống, **bỏ `OP_WRITE`** nhưng thường giữ `OP_READ`.
5. Tránh vòng lặp bận khi một lần `write()` trả `0`; chờ readiness tiếp theo.

Ví dụ cập nhật key (snippet):

```java
// Còn byte cần gửi:
key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);

// Khi đã gửi hết:
key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
```

Cần `key.isValid()` khi thao tác trong môi trường có cancellation. Trong ứng dụng đa luồng, dùng cơ chế đồng bộ thích hợp nếu thread khác cũng thay đổi `interestOps`.

## 12. Hủy key, đóng channel và chuyển blocking mode

Ba thao tác có tác dụng khác nhau:

| Thao tác | Điều xảy ra |
| --- | --- |
| `key.cancel()` | Key trở thành invalid ngay; yêu cầu deregister, channel chưa tự đóng |
| `channel.close()` | Đóng kênh; mọi registration liên quan bị hủy |
| `selector.close()` | Đóng selector và hủy các registration; **không tự động đóng mọi channel đã đăng ký** |

Sau cancel, key không còn dùng hợp lệ để điều khiển interest ops. Khi muốn thay đổi registration, phải xem xét trạng thái cancellation/deregistration thay vì gọi `register` liên tục trên key đã hủy rồi mong một key hợp lệ mới xuất hiện ngay.

Muốn đổi channel đang registered trở lại blocking mode cần bảo đảm nó **không còn đăng ký với selector nào**; việc hủy key và xử lý deregistration có thể cần một lượt selection để có hiệu lực. Điều này liên quan tới đồng bộ thread: tránh đổi mode trong lúc selector thread còn đang xử lý key đó.

Nếu bạn muốn ngừng vòng lặp, thường nên:

1. Đặt trạng thái dừng trong ứng dụng theo cách thread khác nhìn thấy được.
2. Gọi `selector.wakeup()` nếu thread selector đang chặn trong `select()`.
3. Để chính event-loop xử lý cleanup ở điểm an toàn: hủy key, đóng channel, sau đó đóng selector.
4. Thực hiện `try-with-resources` hoặc cleanup rõ ràng cho tài nguyên mới mở.

Đừng nhầm **remove khỏi selected-key set** với **cancel registration** hoặc **close channel**.

## 13. Các ngoại lệ chính khi đăng ký

| Tình huống | Ngoại lệ/kết quả |
| --- | --- |
| Channel vẫn ở blocking mode khi gọi `register` | `IllegalBlockingModeException` |
| Yêu cầu operation nằm ngoài `validOps()` | `IllegalArgumentException` |
| Channel đã đóng | `ClosedChannelException` |
| Selector đã đóng | `ClosedSelectorException` |
| Đặt blocking mode `true` khi channel còn registration hợp lệ | `IllegalBlockingModeException` |
| Đọc `interestOps` / `readyOps` từ key đã cancel | Có thể ném `CancelledKeyException` |
| `SelectionKey.cancel()` | Key invalid; cần xử lý deregistration về sau |
| `selector.select(timeout)` trả 0 | Không có key mới trong kết quả, có thể timeout/wakeup; không phải EOF của channel |
| `SocketChannel.read(buffer)` trả `-1` | EOF của dòng byte channel, không phải key bị hủy |

Lưu ý: ngoại lệ có thể xuất hiện trong luồng code khi tài nguyên bị đóng từ thread khác. Kiểm tra `isOpen()` ngay trước một thao tác chỉ là quan sát tại thời điểm đó; không thể loại bỏ hoàn toàn race condition. Vẫn phải bắt lỗi ở biên I/O.

## 14. Lỗi phổ biến và cách sửa

1. **Đăng ký trước khi chuyển nonblocking:** gọi `configureBlocking(false)` trước `register`.
2. **Dùng `FileChannel` với `Selector`:** `FileChannel` không phải `SelectableChannel`; sử dụng API file đồng bộ hoặc `AsynchronousFileChannel` tùy mục tiêu.
3. **Đăng ký sai operation:** `Pipe.SourceChannel` không hỗ trợ `OP_WRITE`, server listener không hỗ trợ `OP_READ`; tra `validOps()`.
4. **Đọc `isReadable()` trên key đã hủy:** kiểm tra `key.isValid()` và thiết kế lifecycle phù hợp.
5. **Quên `iterator.remove()`:** selected-key set giữ key cũ; xóa khỏi tập sau khi lấy ra xử lý.
6. **Hủy key sau mỗi sự kiện:** sẽ ngừng theo dõi channel; chỉ cancel khi thực sự muốn deregister.
7. **Đóng selector rồi tưởng socket/channel cũng đã đóng:** phải sở hữu và đóng các channel rõ ràng.
8. **Giữ `OP_WRITE` mãi:** khi không có dữ liệu chờ ghi, sự kiện writable thường luôn xảy ra, gây bận CPU.
9. **Giả định readable là đọc được cả frame:** `read()` có thể trả 0, đọc một phần, hoặc EOF; cần parser có trạng thái.
10. **Không xử lý kết nối TCP đang pending:** dùng `OP_CONNECT` và `finishConnect()` trước khi bắt đầu đọc/ghi ở trạng thái connected.
11. **Dùng `selector.select(0)` để poll:** xem hợp đồng timeout; khi cần không chờ hãy dùng `selectNow()`.
12. **Thay interest ops từ worker mà không wakeup:** event loop có thể tiếp tục chờ một event cũ; dùng queue công việc và `wakeup()` đúng lúc.
13. **Nhầm attachment là một bản sao trạng thái tự đồng bộ:** attachment chỉ là reference object của ứng dụng; với nhiều thread cần bảo đảm đồng bộ/visibility.
14. **Gọi `configureBlocking(true)` ngay sau `key.cancel()` và suy ra đã deregister xong:** deregistration được selector xử lý theo vòng đời, phải phối hợp an toàn.
15. **Kết luận từ số `select()` trả về là đã đọc được từng ấy byte:** đó là số key được chọn/cập nhật, còn số byte phải đọc từ kết quả I/O.

## 15. Bài tập vận dụng

### Bài A — đăng ký channel đúng thứ tự

Tạo `Pipe`, gọi `source.register(selector, OP_READ)` khi channel vẫn blocking rồi quan sát ngoại lệ. Sau đó `source.configureBlocking(false)`, đăng ký lại, ghi 3 byte vào sink và dùng `selector.select(timeout)` để chờ sự kiện. Khi đọc, giải thích sự khác nhau giữa số key chọn và số byte đọc.

### Bài B — thử `validOps`

In `source.validOps()` và kiểm tra các bit `OP_READ` / `OP_WRITE` bằng toán tử bitwise. Vì sao `source.register(selector, OP_WRITE)` thất bại dù pipe cũng có `SinkChannel` ghi được?

### Bài C — thay đổi `interestOps`

Đăng ký `OP_READ`, gọi `key.interestOps(0)` rồi kiểm tra giá trị `key.interestOps()`; sau đó đăng ký/cập nhật lại `OP_READ`. Việc đặt 0 có đóng channel hay hủy key không? Tại sao?

### Bài D — thiết kế socket ghi một phần

Một socket có output queue 64 KiB, nhưng lần `write(buffer)` đầu chỉ ghi 8 KiB. Bạn cần giữ phần 56 KiB còn lại ra sao? Khi nào bật `OP_WRITE`, khi nào bỏ nó, và tại sao không gọi write trong vòng lặp bận khi kết quả là 0?

### Bài E — cleanup đúng

Từ chương trình `PipeRegistrationDemo`, hãy bổ sung tình huống cancel key trước khi đóng pipe. Quan sát `isValid()`. Giải thích vì sao `selector.selectedKeys().remove(key)` không thay thế `cancel()`, và vì sao đóng selector không phải thao tác đóng channel.

## 16. Câu hỏi tự kiểm tra

1. Có thể gọi `register` trực tiếp trên một `FileChannel` không? Vì sao?
2. Sự khác nhau giữa `Selector` và `SelectionKey` là gì?
3. Vì sao phải `configureBlocking(false)` trước `register`?
4. `SelectionKey.OP_READ | SelectionKey.OP_WRITE` là kiểu dữ liệu gì, có luôn hợp lệ cho mọi selectable channel không?
5. `channel.validOps()` kiểm tra điều gì? Nó có đồng nghĩa `readyOps()` không?
6. Một channel đã đăng ký với selector A; gọi register cùng selector A lần hai có bắt buộc tạo thêm key không?
7. Khi nào `isReadable()` trả true? Có đảm bảo `read()` sẽ trả đầy đủ frame message không?
8. Trong event loop, vì sao nên gọi `iterator.remove()` ngay khi lấy selected key để xử lý?
9. `SelectionKey.cancel()` và `SelectableChannel.close()` khác nhau thế nào?
10. `selector.close()` có tự đóng toàn bộ socket đã đăng ký không?
11. Khi client nonblocking gọi `connect(remote)` trả false, cần theo dõi bit operation nào và phương thức nào hoàn tất kết nối?
12. Tại sao giữ `OP_WRITE` ngay cả khi không có dữ liệu cần gửi có thể gây tải CPU cao?
13. Nếu selector thread đang đợi, thread khác vừa enqueue dữ liệu, `wakeup()` có vai trò gì?
14. `select(0)` và `selectNow()` có cùng ý nghĩa không?
15. Vì sao kết quả `select()` không phải số byte có thể đọc?

## 17. Tài liệu Java 17

- [`SelectableChannel` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/SelectableChannel.html): `configureBlocking`, `register`, `validOps`.
- [`Selector` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/Selector.html): `select`, `selectedKeys`, `wakeup`, đóng selector.
- [`SelectionKey` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/SelectionKey.html): `interestOps`, `readyOps`, `OP_READ/WRITE/ACCEPT/CONNECT`, `cancel`, attachment.
- [`Pipe` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/Pipe.html): cặp `SourceChannel/SinkChannel`.
- [`SocketChannel` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/SocketChannel.html): kết nối nonblocking, `connect` và `finishConnect`.

## 18. Ghi nhớ

Quy trình đăng ký chuẩn là **mở `SelectableChannel` → `configureBlocking(false)` → mở `Selector` → `channel.register(selector, validOps, attachment)` → `selector.select()` → xử lý selected keys → quản lý interest ops và cleanup**.

`SelectionKey` nắm tập operation ứng dụng **muốn quan sát** và tập operation mà selector **báo sẵn sàng**. Sự kiện readiness chỉ là tín hiệu để **thử I/O**, không phải cam kết một lần `read/write` hoàn tất toàn bộ. Hãy kiểm tra kết quả thực, loại key đã xử lý khỏi selected-key set, chỉ bật `OP_WRITE` khi có dữ liệu đang chờ, và phân biệt `cancel`, `close channel` và `close selector`. Đó là nền tảng cần có trước khi xây event loop socket nonblocking đáng tin cậy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="selector-003">SelectionKey, interest set và ready set</a>

<details>
<summary>Xem chi tiết</summary>

# SelectionKey, interest set và ready set trong Java NIO Selector

## 1. Bài toán: một luồng theo dõi nhiều channel

Với Java NIO, `Selector` giúp một luồng theo dõi nhiều `SelectableChannel` thay vì phải tạo một thread chặn ở mỗi `SocketChannel.read()`. Ứng dụng đăng ký các thao tác muốn theo dõi, gọi `select()` để đợi channel sẵn sàng, rồi xử lý các key được chọn.

Ba khái niệm dễ bị lẫn:

- **`SelectionKey`:** đối tượng đại diện cho **một đăng ký** giữa một `SelectableChannel` và một `Selector`.
- **Interest set (tập quan tâm):** các thao tác ứng dụng muốn selector giám sát, biểu diễn bằng bitmask lấy từ `key.interestOps()`.
- **Ready set (tập sẵn sàng):** các thao tác mà lần chọn đã xác định là sẵn sàng đối với key, biểu diễn bằng `key.readyOps()`.

Chúng không phải là ba tên gọi cho cùng một trạng thái. Hãy nhìn luồng điều khiển:

```text
SelectableChannel (đặt non-blocking)
          │
          │ register(selector, OP_READ | OP_WRITE)
          ▼
   SelectionKey
          │
          ├── interestOps() : những gì ứng dụng MUỐN theo dõi
          ├── readyOps()    : những gì selector phát hiện SẴN SÀNG
          ├── channel()     : channel đã đăng ký
          ├── selector()    : selector chứa đăng ký
          └── attachment()  : ngữ cảnh ứng dụng, nếu có
          │
          ▼
  selector.select()
          │
          ▼
  selector.selectedKeys()
          │
          ▼
  kiểm tra key.isReadable(), isWritable(), ...
          │
          ▼
  xử lý non-blocking I/O; điều chỉnh interestOps; xóa key khỏi selectedKeys
```

**Mục tiêu bài học:** hiểu quan hệ giữa key, interest set, ready set và selected-key set; sử dụng bitmask `OP_READ`/`OP_WRITE`/`OP_CONNECT`/`OP_ACCEPT`; cập nhật interest đúng lúc; xử lý selected keys an toàn; tránh busy loop và nhầm “ready” với “I/O chắc chắn hoàn tất”.

## 2. `SelectionKey` là kết quả của việc đăng ký

API cơ bản:

```java
Selector selector = Selector.open();
SelectableChannel channel = /* channel thích hợp */;
channel.configureBlocking(false);
SelectionKey key = channel.register(selector, SelectionKey.OP_READ);
```

Đoạn trên **chỉ minh họa**: `channel` phải là một loại `SelectableChannel` có hỗ trợ `OP_READ`, và caller phải đóng channel/selector khi hoàn tất. `register` trả về `SelectionKey` gắn với cặp channel–selector đó.

Các thuộc tính/phương thức chính:

| Phương thức | Ý nghĩa |
|---|---|
| `key.channel()` | Truy cập channel của đăng ký |
| `key.selector()` | Truy cập selector gắn với đăng ký |
| `key.interestOps()` | Đọc bitmask interest hiện tại |
| `key.interestOps(mask)` | Thay thế interest set bằng bitmask mới |
| `key.readyOps()` | Đọc bitmask readiness của key |
| `key.isReadable()`, `isWritable()`, `isConnectable()`, `isAcceptable()` | Tiện ích kiểm tra các bit trong ready set |
| `key.isValid()` | Key còn là đăng ký hợp lệ hay không |
| `key.cancel()` | Yêu cầu hủy đăng ký; xử lý hủy trong selection tiếp theo |
| `key.attach(obj)` / `key.attachment()` | Gắn và lấy ngữ cảnh nghiệp vụ, không làm thay đổi readiness |

`SelectionKey` là một **registration**, không đại diện cho một event đơn lẻ. Cùng một key có thể được selector chọn nhiều lần trong suốt vòng đời kết nối. Chỉ tạo một key cho một cặp channel–selector tại một thời điểm; đăng ký lại một channel với **cùng selector** khi đã có đăng ký hợp lệ sẽ cập nhật interest/attachment của đăng ký hiện có thay vì tạo key thứ hai độc lập.

### 2.1. Trước khi đăng ký phải cấu hình non-blocking

`SelectableChannel.register` yêu cầu channel ở chế độ non-blocking. Với `SocketChannel`, `ServerSocketChannel` hoặc `Pipe.SourceChannel`, gọi:

```java
channel.configureBlocking(false);
```

Nếu đăng ký một selectable channel vẫn đang blocking, `register` có thể ném `IllegalBlockingModeException`. Nếu cố chuyển một selectable channel có đăng ký còn hợp lệ quay lại blocking, `configureBlocking(true)` cũng không được phép theo contract tương ứng.

**Phân biệt:** `FileChannel` không phải `SelectableChannel`; file disk thông thường không được đăng ký trực tiếp với `Selector` theo mô hình này. `Selector` đặc biệt hữu ích cho socket và những selectable channel tương ứng.

## 3. Các bit `OP_*` và `validOps()`

Java dùng các hằng số bitmask của `SelectionKey`:

| Hằng số | Giá trị int | Thao tác được theo dõi | Channel điển hình |
|---|---:|---|---|
| `SelectionKey.OP_READ` | `1` | Có dữ liệu để đọc, EOF hoặc tình huống đọc không còn chặn theo semantics channel | `SocketChannel`, `Pipe.SourceChannel` |
| `SelectionKey.OP_WRITE` | `4` | Có thể ghi ít nhất một số byte mà không chặn | `SocketChannel`, `Pipe.SinkChannel` |
| `SelectionKey.OP_CONNECT` | `8` | Kết nối không chặn có thể hoàn tất | `SocketChannel` |
| `SelectionKey.OP_ACCEPT` | `16` | Server có thể nhận kết nối mới | `ServerSocketChannel` |

Các bit khác nhau có thể ghép bằng phép OR bit:

```java
int readAndWrite =
        SelectionKey.OP_READ | SelectionKey.OP_WRITE;

boolean wantsRead =
        (readAndWrite & SelectionKey.OP_READ) != 0;

boolean wantsAccept =
        (readAndWrite & SelectionKey.OP_ACCEPT) != 0;
```

Đây là đoạn minh họa có thể đặt trong method. Kết quả `wantsRead == true`, `wantsAccept == false`.

**Không phải mọi channel đều hỗ trợ mọi thao tác.** Kiểm tra:

```java
int supportedMask = channel.validOps();
boolean canRead =
        (supportedMask & SelectionKey.OP_READ) != 0;
```

Đoạn minh họa yêu cầu `channel` là `SelectableChannel` đã được khai báo. Ví dụ:

- `ServerSocketChannel`: thường chỉ hỗ trợ `OP_ACCEPT`.
- `SocketChannel`: hỗ trợ `OP_CONNECT`, `OP_READ`, `OP_WRITE`.
- `Pipe.SourceChannel`: hỗ trợ `OP_READ`.
- `Pipe.SinkChannel`: hỗ trợ `OP_WRITE`.

Nếu `register(selector, ops)` hoặc `key.interestOps(ops)` nhận bit không nằm trong `channel.validOps()`, API ném `IllegalArgumentException`. Đặc biệt, không đăng ký `OP_ACCEPT` trên `SocketChannel` để chờ server nhận kết nối: chỉ `ServerSocketChannel` mới có vai trò accept.

### 3.1. Interest set có thể bằng 0

`key.interestOps(0)` là thao tác hợp lệ: tạm thời không giám sát sự kiện I/O nào trên key. Nó **không xóa đăng ký** và không đóng channel.

Ứng dụng có thể dùng `interestOps(0)` để tạm dừng xử lý khi đang điều phối trạng thái; khi sẵn sàng theo dõi lại, đặt các bit mong muốn. Nếu mục đích là ngừng giám sát mãi mãi, hãy hủy key hoặc đóng channel/selector theo chính sách vòng đời.

## 4. Interest set: ứng dụng muốn selector theo dõi điều gì?

`interestOps()` là **bitmask nguyện vọng** do code ứng dụng quyết định. Ví dụ một socket sau khi kết nối xong có thể muốn đọc nhưng chưa có dữ liệu chờ gửi:

```java
key.interestOps(SelectionKey.OP_READ);
```

Khi hàng đợi output có dữ liệu chưa gửi hết, thêm `OP_WRITE`:

```java
key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
```

Khi đã gửi hết dữ liệu, bỏ `OP_WRITE`:

```java
key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
```

Các dòng trên là **mã minh họa** với một `SelectionKey key` còn hợp lệ và channel hỗ trợ cả `READ` và `WRITE`. Cách kết hợp bit dùng:

- `|` (OR): thêm bit mà vẫn giữ các bit khác.
- `& ~` (AND NOT): xóa bit mà vẫn giữ các bit khác.
- `&` (AND): kiểm tra xem một bit có được bật không.

Kể từ Java 11, `SelectionKey` còn có `interestOpsOr(ops)` và `interestOpsAnd(ops)` để cập nhật interest theo phép OR/AND nguyên tử đối với các lời gọi phương thức interestOps khác:

```java
key.interestOpsOr(SelectionKey.OP_WRITE);     // Thêm WRITE.
key.interestOpsAnd(~SelectionKey.OP_WRITE);   // Bỏ WRITE.
```

Đây là đoạn minh họa yêu cầu key hợp lệ và OP_WRITE được channel hỗ trợ. Các API atomic bit update hữu ích khi nhiều thread thay đổi interest; tuy nhiên chúng **không tự đồng bộ hóa hàng đợi output, trạng thái protocol hoặc vòng đời channel**. Việc cập nhật bitmask và đẩy dữ liệu vào queue vẫn cần một giao thức thread-safe thống nhất.

### 4.1. Thay interest không viết đè ready set ngay lập tức

`key.interestOps(newMask)` thay đổi điều selector sẽ quan tâm **trong các lần selection**. Nó không có nghĩa `key.readyOps()` được xóa hoặc tính lại tức thì. Interest set và ready set là các bitmask có mục đích khác nhau.

Theo contract selector, khi selection đang diễn ra, một thay đổi interest được gọi đồng thời có thể không được tính đến cho tới lần selection kế tiếp. Khi một worker thread chuyển dữ liệu vào queue và bật `OP_WRITE`, thường gọi thêm `selector.wakeup()` để yêu cầu event loop đang chờ trong `select()` quay lại xử lý thay đổi.

## 5. Ready set: selector đã quan sát trạng thái nào?

Sau `selector.select()`, một key có thể có:

```java
int ready = key.readyOps();

boolean canRead  = (ready & SelectionKey.OP_READ) != 0;
boolean canWrite = (ready & SelectionKey.OP_WRITE) != 0;
```

Đây là đoạn minh họa: `readyOps()` cần key hợp lệ và nên được đọc trong chu kỳ xử lý key đã chọn. Thông thường đọc readiness qua:

```java
if (key.isReadable()) {
    // Thử read() non-blocking.
}
if (key.isWritable()) {
    // Thử write() non-blocking.
}
```

Những phương thức `isReadable()`/`isWritable()` kiểm tra **ready set**, không kiểm tra interest set. Một key có thể vẫn lưu bit sẵn sàng được phát hiện từ lần chọn trước trong khi interest đã đổi, nên tránh coi `isReadable()` trên một key tùy ý là phép thăm dò trạng thái socket tức thời.

### 5.1. Sẵn sàng không có nghĩa “thao tác sẽ truyền hết dữ liệu”

Với non-blocking `SocketChannel`:

- `isReadable()==true` có nghĩa selector phát hiện điều kiện đọc. `read(buffer)` vẫn có thể trả `0` trong một số tình huống hoặc `-1` ở EOF; không bảo đảm một lần đọc đủ cả message.
- `isWritable()==true` chỉ gợi ý có khả năng ghi được **một lượng byte nào đó** mà không chặn. `write(buffer)` có thể ghi một phần, và sau đó có thể trả `0`.
- `isAcceptable()==true` là tín hiệu nên thử `server.accept()`; thao tác non-blocking vẫn cần xử lý giá trị trả về `null` theo contract.
- `isConnectable()==true` cho biết nên gọi `socket.finishConnect()`; việc kết nối vẫn có thể thất bại với ngoại lệ I/O.

Readiness là điều kiện để **thử thực hiện I/O đúng API**, không phải lời hứa rằng toàn bộ yêu cầu nghiệp vụ đã hoàn thành. Một message có thể gồm nhiều byte hoặc nhiều frame; phải lưu trạng thái khi dữ liệu chưa đủ.

### 5.2. Ready set được cập nhật ra sao khi một key vẫn được chọn?

Selector duy trì một **selected-key set**. Khi một key mới được chọn do một interest op sẵn sàng, ready set được cập nhật theo thao tác được nhận diện. Nếu key **đã nằm** trong selected-key set, các thao tác ready mới có thể được **OR** thêm vào các bit ready hiện có, thay vì tự động làm mới theo mô hình “snapshot hiện tại”.

Hệ quả: `readyOps()` không phải luôn là kết quả của một phép kiểm tra kernel tức thì tại thời điểm bạn đọc nó. Các bit có thể còn từ chu kỳ selection trước nếu chương trình giữ key trong selected set.

Cách viết event loop thông thường là **lấy từng key được chọn, xử lý, rồi loại khỏi `selectedKeys()` bằng iterator.remove()**. Ở lần selection sau, nếu vẫn có readiness thì selector có thể đưa key trở lại set.

## 6. Ba tập của Selector: registered, selected và cancelled

Ngoài hai **bitset trong từng key**, `Selector` quản lý những **tập key**:

| Tập | Lấy bằng / cơ chế | Ý nghĩa |
|---|---|---|
| Registered-key set | `selector.keys()` | Những key hiện đăng ký với selector |
| Selected-key set | `selector.selectedKeys()` | Những key được chọn và chờ ứng dụng xử lý |
| Cancelled-key set | Được selector duy trì nội bộ | Những key đã được yêu cầu hủy, sẽ được xử lý trong thao tác selection |

**Đừng nhầm** `selectedKeys()` (tập đối tượng key) với `key.readyOps()` (bitmask bên trong một key). Chúng có kiểu dữ liệu và ý nghĩa khác nhau.

Một key có thể:

1. Đã đăng ký và hợp lệ, nhưng **chưa** nằm trong selected set.
2. Nằm trong selected set, có ready bit đã được ghi nhận.
3. Được ứng dụng loại khỏi selected set sau xử lý nhưng **vẫn đăng ký** để theo dõi các đợt readiness tiếp theo.
4. Bị `cancel()`, trở nên invalid và được hủy đăng ký qua quy trình selection; không còn sử dụng như key hợp lệ.

### 6.1. Tại sao phải `iterator.remove()`?

Một vòng lặp mẫu:

```java
Iterator<SelectionKey> iterator =
        selector.selectedKeys().iterator();

while (iterator.hasNext()) {
    SelectionKey key = iterator.next();
    iterator.remove(); // Đã nhận key để xử lý.

    if (!key.isValid()) {
        continue;
    }

    if (key.isReadable()) {
        // Đọc non-blocking tới khi không đọc thêm được, hoặc theo quota.
    }
}
```

Mã trên là snippet minh họa nằm trong vòng đời selector đã được tạo, với `import java.util.Iterator` và `java.nio.channels.SelectionKey`. Dùng iterator để xóa khỏi selected set là cách chính thức và thông thường; `selectedKeys().add(...)` không được hỗ trợ.

**Loại khỏi selected-key set không phải `cancel()`.** Hành vi `iterator.remove()` chỉ báo chương trình đã xử lý một lượt notification. Channel và registration vẫn có thể hoạt động; selector sẽ chọn lại nếu có thao tác mới/tiếp diễn thỏa interest.

Nếu quên remove:

- Event loop có thể xử lý một key cũ lặp lại theo trạng thái ready cũ.
- Số key “đang selected” không phản ánh chính xác các sự kiện mới.
- Các bit ready có thể được giữ/OR qua nhiều lần selection khi key chưa ra khỏi selected set.
- Dễ tạo xử lý lặp hoặc làm kết quả quan sát khó hiểu.

## 7. Ví dụ Java 17+ chạy được: quan sát interest và ready trên Pipe

Để không phụ thuộc cổng mạng hay server ngoài, ví dụ dùng `Pipe`. `Pipe.SourceChannel` hỗ trợ `OP_READ` và có thể đăng ký với selector; `Pipe.SinkChannel` ghi dữ liệu vào đầu còn lại.

**Tệp nguồn:** `SelectionKeyDemo.java`

```java
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

public class SelectionKeyDemo {
    public static void main(String[] args) throws Exception {
        Pipe pipe = Pipe.open();

        try (Pipe.SourceChannel source = pipe.source();
             Pipe.SinkChannel sink = pipe.sink();
             Selector selector = Selector.open()) {

            source.configureBlocking(false);
            SelectionKey key = source.register(
                    selector, SelectionKey.OP_READ, "inbox");

            System.out.println("interestRead: "
                    + ((key.interestOps() & SelectionKey.OP_READ) != 0));

            ByteBuffer outbound = ByteBuffer.wrap(
                    "NIO".getBytes(StandardCharsets.US_ASCII));

            while (outbound.hasRemaining()) {
                sink.write(outbound);
            }

            System.out.println("selectMatched: "
                    + (selector.select(1000) > 0));

            Iterator<SelectionKey> iterator =
                    selector.selectedKeys().iterator();

            while (iterator.hasNext()) {
                SelectionKey selected = iterator.next();
                iterator.remove();

                if (selected.isValid() && selected.isReadable()) {
                    ByteBuffer inbound = ByteBuffer.allocate(16);
                    int count = source.read(inbound);
                    inbound.flip();

                    System.out.println("readyRead: "
                            + selected.isReadable());
                    System.out.println("readCount: " + count);
                    System.out.println("payload: "
                            + StandardCharsets.US_ASCII.decode(inbound));

                    selected.interestOps(0);
                }
            }

            System.out.println("interestAfter: "
                    + key.interestOps());
            System.out.println("selectedEmpty: "
                    + selector.selectedKeys().isEmpty());
        }
    }
}
```

Biên dịch và chạy bằng JDK 17+:

```bash
javac SelectionKeyDemo.java
java SelectionKeyDemo
```

**Stdout dự kiến trong môi trường bình thường, không có gián đoạn/đánh thức giả trước khi dữ liệu được chọn:**

```text
interestRead: true
selectMatched: true
readyRead: true
readCount: 3
payload: NIO
interestAfter: 0
selectedEmpty: true
```

**Giải thích chi tiết:**

1. `Pipe.open()` tạo đầu nguồn và đầu đích. `Pipe` **không** tự triển khai `AutoCloseable`; ví dụ đóng riêng `source`, `sink` và `selector` bằng `try-with-resources`.
2. `source.configureBlocking(false)` là điều kiện để đăng ký. `source.register(selector, OP_READ, "inbox")` trả key có interest READ; `"inbox"` là attachment.
3. `sink.write(...)` đưa ba byte `NIO` vào pipe. Sau đó `select(1000)` đợi tối đa khoảng một giây để phát hiện nguồn sẵn sàng đọc. Với pipe nội bộ và dữ liệu đã có, dự kiến chọn được key.
4. `selectedKeys()` chứa key vừa được chọn. `iterator.remove()` xóa nó khỏi selected set **nhưng không hủy registration**.
5. `selected.isReadable()` kiểm tra ready bit `OP_READ`. `source.read(inbound)` lấy ba byte, `flip()` chuẩn bị giải mã US-ASCII thành `NIO`.
6. `selected.interestOps(0)` tạm tắt giám sát trên key sau khi đã xử lý. Key vẫn hợp lệ cho đến khi cancel/close.
7. `selectedKeys().isEmpty()` trả `true` vì ứng dụng đã remove các key được chọn.

Ví dụ đóng cả ba tài nguyên đúng phạm vi. Không in object identity, địa chỉ file hay số cổng ngẫu nhiên. Nếu selector bị interrupt hoặc môi trường có hành vi không thường gặp, selection có thể trả về 0; đó là lý do event loop production luôn phải xử lý cả trường hợp `select()` trở về **không có key**.

### 7.1. Đọc dữ liệu sau `isReadable` vẫn phải kiểm tra `read`

Trong ví dụ nhỏ, `source.read` dự kiến trả 3. Với chương trình thật, `read` có thể trả 0 khi chưa có byte để lấy thêm hoặc -1 khi đầu vào kết thúc; khi dữ liệu nhiều hơn kích thước buffer, phải tiếp tục đọc và giữ phần thông điệp chưa hoàn chỉnh để xử lý sau.

**Không suy diễn rằng một key READ-ready chứa đúng một message.** TCP là byte stream, có thể chứa nửa message, nhiều message, hoặc dữ liệu chia thành những lần đọc tùy ý. Một parser phải có state để xác định boundary theo protocol.

## 8. Event loop Selector tối thiểu cho server/socket

Dưới đây là **khung minh họa thuật toán**, không phải chương trình độc lập: nó dùng `selector`, `server`, các handler và queue do ứng dụng định nghĩa. Mục đích là xác định nơi đọc ready bit và cập nhật interest.

```java
while (running) {
    selector.select(); // Có thể trở về không có key.

    Iterator<SelectionKey> it =
            selector.selectedKeys().iterator();

    while (it.hasNext()) {
        SelectionKey key = it.next();
        it.remove();

        if (!key.isValid()) {
            continue;
        }

        try {
            if (key.isAcceptable()) {
                acceptConnections(key);
            }
            if (key.isValid() && key.isConnectable()) {
                finishConnection(key);
            }
            if (key.isValid() && key.isReadable()) {
                readAvailableBytes(key);
            }
            if (key.isValid() && key.isWritable()) {
                flushPendingWrites(key);
            }
        } catch (IOException ex) {
            key.cancel();
            key.channel().close();
        }
    }
}
```

**Đây là pseudocode theo cú pháp Java, không tự biên dịch**: `running` và bốn hàm handler là thành phần của ứng dụng. Nó minh họa thứ tự phổ biến:

1. `select()` đợi thao tác từ key mà selector quan tâm.
2. Duyệt selected set, lấy key và remove nó khỏi tập.
3. Kiểm tra valid để tránh xử lý key đã cancel.
4. Xử lý các thao tác được báo ready; việc xử lý có thể thay interestOps, cancel key hoặc đóng channel.
5. Theo dõi lỗi I/O và vòng đời kết nối; không giữ invalid key cho vòng sau.

Việc kiểm tra nhiều `if` thay vì `else if` hợp lý khi một key có thể ready nhiều thao tác cùng lúc. Tuy nhiên handler có thể thay đổi/cancel key, nên ví dụ kiểm tra `isValid()` trước mỗi thao tác kế tiếp.

### 8.1. Server: `OP_ACCEPT` và đăng ký channel vừa được chấp nhận

`ServerSocketChannel` đang non-blocking được đăng ký `OP_ACCEPT`:

```java
server.configureBlocking(false);
SelectionKey acceptKey =
        server.register(selector, SelectionKey.OP_ACCEPT);
```

Đây là snippet với `server` kiểu `ServerSocketChannel` và `selector` đã được tạo; server phải bind đúng địa chỉ/port trước khi nhận kết nối.

Khi `acceptKey.isAcceptable()`:

```java
SocketChannel client = server.accept();
if (client != null) {
    client.configureBlocking(false);
    client.register(selector, SelectionKey.OP_READ);
}
```

Đây là đoạn minh họa cần import `SocketChannel`, xử lý `IOException` và quản lý đóng client khi kết thúc. `accept()` có thể trả `null` khi không có connection chờ tại thời điểm gọi trong non-blocking mode. Với server có nhiều kết nối đến đồng thời, thường accept theo vòng lặp đến khi `null`, có giới hạn số lượng mỗi lượt nếu cần fairness.

**Lỗi cần tránh:** đăng ký `OP_ACCEPT` lên `SocketChannel` của client. Client dùng các op tương ứng như `OP_CONNECT`, `OP_READ` và `OP_WRITE`.

### 8.2. Client: `OP_CONNECT` không thay thế `finishConnect()`

Với `SocketChannel` non-blocking, `connect(remote)` có thể chưa hoàn thành ngay. Nếu còn pending, đăng ký `OP_CONNECT`, rồi khi `isConnectable()`:

```java
SocketChannel client = (SocketChannel) key.channel();

if (client.finishConnect()) {
    key.interestOps(SelectionKey.OP_READ);
}
```

Đoạn minh họa giả định `key` là key của socket đang kết nối, có xử lý `IOException` và `SocketChannel` được import. `finishConnect()` có thể ném lỗi khi kết nối thất bại. Sau khi kết nối thành công, nên bỏ `OP_CONNECT` và chuyển sang các thao tác thật sự cần theo dõi. Nếu còn byte chờ ghi, có thể thêm `OP_WRITE` phù hợp.

## 9. Vì sao `OP_WRITE` dễ gây busy loop?

Trên socket không chặn, hệ thống thường có sẵn một lượng chỗ trống trong send buffer. Vì thế **OP_WRITE có thể luôn ready trong phần lớn thời gian**, ngay cả khi ứng dụng chưa có byte nào cần gửi.

Một pattern nguy hiểm:

```java
client.register(selector,
        SelectionKey.OP_READ | SelectionKey.OP_WRITE);

// Không có dữ liệu để ghi, nhưng vẫn giữ OP_WRITE suốt đời kết nối.
```

Đoạn minh họa với client đã non-blocking, selector hợp lệ và socket hỗ trợ cả READ/WRITE. Selector có thể liên tục thức dậy vì writable; CPU tăng dù ứng dụng không có công việc.

**Cách quản lý hợp lý với hàng đợi ghi:**

- Khi queue output **rỗng**, interest thông thường chỉ cần `OP_READ` (hoặc bộ bit cần thiết khác), **không giữ `OP_WRITE`**.
- Khi enqueue một message để gửi, thêm `OP_WRITE` và đánh thức selector nếu thread khác đang chờ.
- Khi key writable, gọi `SocketChannel.write(ByteBuffer)` để gửi **bao nhiêu byte có thể**, cập nhật position buffer.
- Nếu byte chưa gửi hết do write một phần hoặc bằng 0, giữ `OP_WRITE` để nhận tín hiệu khi socket lại có khả năng ghi.
- Khi queue cạn, **xóa `OP_WRITE`**. Không ghi một lần rồi giả định toàn bộ message đã truyền.

Mã cập nhật bit minh họa:

```java
// Khi có dữ liệu chờ, channel hỗ trợ OP_WRITE:
key.interestOpsOr(SelectionKey.OP_WRITE);

// Khi không còn dữ liệu chờ:
key.interestOpsAnd(~SelectionKey.OP_WRITE);
```

Không gọi hai dòng liên tiếp như một thao tác xử lý thực tế: chúng minh họa **hai thời điểm trạng thái queue khác nhau**. Và đừng để non-I/O thread tự ghi socket đồng thời với event loop mà không có thỏa thuận synchronization rõ ràng.

### 9.1. `isWritable()` không đồng nghĩa đã gửi xong

`key.isWritable()==true` cho biết có readiness ghi; `write(buffer)` có thể chỉ ghi một phần. Kiểm tra `buffer.hasRemaining()` để giữ buffer chưa gửi hết và tiếp tục khi có readiness sau đó. Với giao thức framing, cần giữ nguyên thứ tự các buffer trong queue, tránh tái sử dụng một `ByteBuffer` trong lúc vẫn còn byte chưa gửi.

## 10. Thay đổi interest từ thread khác và `wakeup()`

Một event loop có thể đang chờ trong `selector.select()`. Trong khi đó, thread xử lý nghiệp vụ tạo dữ liệu trả lời và muốn ghi trên socket:

```java
// Worker thread: queue phải an toàn đa luồng.
pendingWrites.add(outgoing);

key.interestOpsOr(SelectionKey.OP_WRITE);
selector.wakeup();
```

Đây là đoạn minh họa không độc lập: `pendingWrites` là queue phù hợp đồng thời, `outgoing` là buffer có byte, `key` hợp lệ và hỗ trợ WRITE. Gọi `wakeup()` báo selector rời trạng thái chờ trong `select()` (hoặc khiến lần select tiếp theo trở về sớm theo contract), cho phép event loop xử lý queue/interest mới.

Các lưu ý:

- `wakeup()` **không** tự đặt `OP_WRITE` hay readiness bit. Nó chỉ tác động cơ chế đánh thức.
- InterestOps thay đổi trong lúc `select()` đang chạy có thể chỉ được ghi nhận ở lần selection tiếp theo, tùy timing/semantics.
- `select()` có thể trả về 0 sau wakeup; đó là tình huống hợp lệ và event loop vẫn phải chạy phần xử lý công việc được enqueue.
- Cần cơ chế điều phối queue và interest để tránh race: giữa lúc event loop thấy queue rỗng và tắt WRITE, thread khác có thể vừa enqueue. Có thể gom mọi thay đổi interest vào event-loop thread bằng task queue + `wakeup()` để giảm rủi ro.
- `SelectionKey.interestOpsOr/And` giúp cập nhật bitmask mà không làm mất cập nhật đồng thời từ API interest, nhưng **không đồng bộ dữ liệu nghiệp vụ** hoặc thứ tự I/O.

### 10.1. Phân biệt `select()`, `select(timeout)` và `selectNow()`

- `select()`: có thể chờ tới khi có key ready, wakeup, interrupt hoặc điều kiện khác khiến nó trở về.
- `select(long timeout)`: đợi tối đa khoảng thời gian yêu cầu; với timeout `0`, hợp đồng là chờ vô hạn tương tự `select()`, **không phải polling ngay lập tức**.
- `selectNow()`: chọn không chặn và trở về ngay.

Giá trị trả về là số key có ready set được cập nhật bởi thao tác selection hiện tại, **không được đồng nhất mù quáng với `selectedKeys().size()`** nếu ứng dụng giữ các key đã chọn qua nhiều lượt. Một lần select cũng có thể trả về 0 khi bị đánh thức, bị interrupt hoặc hết thời gian chờ; code không nên giả định cứ return là có ít nhất một key.

## 11. `cancel()`, đóng channel và kiểm tra `isValid()`

`key.cancel()` đánh dấu key **không còn hợp lệ** ngay khi yêu cầu hủy và đưa nó vào quy trình xử lý đăng ký bị hủy của selector. Thao tác loại registration khỏi tập của selector được xử lý trong selection tiếp theo theo contract, không phải một lời hứa rằng mọi cấu trúc nội bộ được dọn sạch ngay tức thì.

```java
key.cancel();
System.out.println(key.isValid()); // false
```

Đây là đoạn minh họa khi `key` còn là đối tượng SelectionKey. Sau cancel, gọi các API đòi hỏi key hợp lệ như `interestOps()` hoặc `readyOps()` có thể ném `CancelledKeyException`. Phương thức `channel()` và `selector()` giúp truy xuất ngữ cảnh đăng ký, nhưng điều đó không có nghĩa key còn đang hoạt động.

**Phân biệt các thao tác:**

| Thao tác | Đóng channel? | Hủy registration? | Key còn trong selected set? |
|---|---|---|---|
| `selectedKeys().remove(key)` | Không | Không | Đã loại khỏi selected set |
| `key.interestOps(0)` | Không | Không | Có thể còn nếu chưa remove |
| `key.cancel()` | Không tự đóng channel | Yêu cầu hủy, key invalid ngay | Cleanup trong selection tiếp theo |
| `key.channel().close()` | Có | Khi channel đóng, các key liên quan bị hủy | Cleanup theo selector |
| `selector.close()` | Đóng selector, vô hiệu hóa registration | Có | Tập được dọn theo contract; channel tự nó không nhất thiết đóng |

Một hệ thống lỗi I/O trên socket thường vừa `cancel()` vừa `channel.close()`. Chỉ `cancel` không giải phóng socket; chỉ remove khỏi selected set không hủy đăng ký.

### 11.1. Attachment dùng để làm gì?

`key.attach(obj)` lưu một đối tượng ngữ cảnh của ứng dụng trên key. Ví dụ:

```java
final class ConnectionState {
    ByteBuffer input = ByteBuffer.allocate(4096);
    // Có thể bổ sung output queue, parser state, id phiên...
}

ConnectionState state = new ConnectionState();
key.attach(state);
ConnectionState retrieved =
        (ConnectionState) key.attachment();
```

Đây là đoạn code minh họa đặt trong phạm vi có `SelectionKey key` hợp lệ và import `ByteBuffer`; cần thiết kế đồng bộ nếu attachment bị truy cập ở nhiều thread. Attachment không tạo I/O readiness, không chứa “message vừa đến” do selector tự giải mã; nó chỉ là nơi thuận tiện để gắn parser/buffer/queue mà chương trình tự quản lý.

Khi cancel/close, cần giải phóng các tài nguyên được tham chiếu bởi attachment theo chính sách ứng dụng, tránh memory leak từ những object vẫn còn tham chiếu trong data structure.

## 12. Trạng thái SELECTED không phải subscription cấp cao

Selector NIO có thể được xem như công cụ báo **khả năng thực hiện thao tác I/O**. Nó không tự:

- Tách gói theo newline, length-prefix, JSON hoặc giao thức tùy chỉnh.
- Bảo đảm fairness giữa các connection; một connection có dữ liệu quá nhiều có thể chiếm thời gian xử lý.
- Đảm bảo một lần `read()` tạo đúng một message.
- Lập lịch retry hay timeout nghiệp vụ.
- Đảm bảo thứ tự event xử lý bằng nhiều thread nếu bạn tự triển khai concurrent handlers.
- Cung cấp backpressure hoàn chỉnh giữa người gửi, output queue và socket.
- Bảo đảm tính đúng đắn của việc giữ/sửa attachment.

Các thiết kế thực tế cần phân biệt lớp **event loop (readiness)** và lớp **protocol/application state (message, retry, timeout, backpressure)**.

### 12.1. Edge-triggered và level-triggered: không suy luận quá mức từ Java API

Một số OS có cơ chế notification khác nhau. Java `Selector` trừu tượng hóa chi tiết provider. Không dựa vào giả định “một ready event = một byte/packet/message mới”, hoặc mỗi lần select luôn cho một sự kiện duy nhất rồi bit được clear như message queue.

Mô hình lập trình bền vững là: khi được chọn, **thử I/O không chặn**, cập nhật bộ đệm/queue, và điều chỉnh interest cho chu kỳ tiếp theo. Với đọc, thường tiếp tục đọc đến khi không còn byte có thể lấy trong lượt đó (hoặc dừng theo quota để giữ fairness); với ghi, quản lý phần chưa gửi và tắt OP_WRITE khi queue đã hết.

## 13. Ví dụ xử lý tình huống interest và ready khác nhau

### Trường hợp A: chỉ quan tâm đọc, socket có thể ghi

```java
key.interestOps(SelectionKey.OP_READ);
```

Dù socket trên OS lúc đó có khả năng ghi, selector không cần đưa ready bit WRITE vào thông báo do chương trình không quan tâm WRITE. **Khả năng I/O cấp OS** không đồng nhất với **interest mask ứng dụng**.

### Trường hợp B: muốn đọc và ghi, nhưng chỉ đọc ready

```java
key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
```

Nếu selector chỉ nhận diện READ ready trong lượt chọn, `isReadable()` đúng còn `isWritable()` có thể sai. Interest set có hai bit; ready set có thể chỉ có một. Không lấy `interestOps()` để quyết định gọi `read()/write()` mà không xét readiness và trạng thái queue.

### Trường hợp C: xử lý xong rồi thay interest

Ứng dụng nhận được READ-ready, đọc xong và tạm dừng đọc để áp dụng backpressure:

```java
key.interestOpsAnd(~SelectionKey.OP_READ);
```

Interest set hiện không còn READ. Nhưng `key.readyOps()` có thể vẫn lưu READ từ selection trước; chỉ thay interest không làm ready bit biến mất tức thì. Ứng dụng phải dùng trạng thái điều phối riêng và xử lý theo chu kỳ chọn, không tái gọi read chỉ vì bit cũ còn trên key.

### Trường hợp D: key đã cancel

```java
key.cancel();
boolean stillValid = key.isValid(); // false
```

Không gọi `key.isReadable()` rồi coi trạng thái là hợp lệ; key invalid có thể ném `CancelledKeyException` khi đọc ready set. Chỉ xử lý key sau `isValid()` và vẫn chuẩn bị bắt lỗi I/O vì trạng thái có thể đổi đồng thời.

### Trường hợp E: quên loại key khỏi selected set

Một key vẫn có thể nằm trong `selector.selectedKeys()` khi event loop gọi `select()` lần tiếp theo. Các bit ready có thể tích lũy/thay đổi theo quy tắc key đã selected, không cho ra snapshot đơn giản. Dùng `Iterator.remove` sau khi lấy key để xử lý và không biến selected set thành nơi lưu task queue của ứng dụng.

## 14. Những lỗi phổ biến

1. **Đăng ký channel chưa `configureBlocking(false)`.** Có thể ném `IllegalBlockingModeException`.
2. **Nhầm `FileChannel` với `SelectableChannel`.** FileChannel không đăng ký Selector theo cách của SocketChannel.
3. **Dùng `OP_ACCEPT` cho client SocketChannel.** ServerSocketChannel mới hỗ trợ accept; xem `validOps()`.
4. **Cho rằng `interestOps()` và `readyOps()` bằng nhau.** Interest là yêu cầu theo dõi, ready là kết quả selection.
5. **Dùng `interestOps` để biết kênh đã sẵn sàng chưa.** Phải xem readiness qua `readyOps`/`isReadable` sau selection và thử I/O.
6. **Xem `isWritable` là gửi hết buffer được ngay.** Non-blocking write có thể gửi một phần hoặc trả 0.
7. **Xem `isReadable` là chắc chắn có một message hoàn chỉnh.** Cần parser, giữ byte dư và xử lý EOF.
8. **Để `OP_WRITE` bật liên tục dù output queue rỗng.** Dễ gây busy loop và CPU cao.
9. **Quên `iterator.remove()` khi duyệt selectedKeys.** Các key cũ tồn tại qua vòng selection, dễ xử lý lặp.
10. **Nghĩ remove khỏi selectedKeys là cancel key.** Chúng tác động hai tập và hai mục đích khác nhau.
11. **Gọi `selectedKeys().add(key)` để tạo event giả.** API không cho thêm tùy ý; dùng task queue/wakeup riêng.
12. **Nhầm selected-key set là ready set.** Set chứa đối tượng SelectionKey; ready set là bitmask trên từng key.
13. **Tin `readyOps()` luôn phản ánh trạng thái OS ở đúng nano-giây hiện tại.** Ready bits có thể còn của selection trước nếu key chưa được bỏ selected set.
14. **Cập nhật interest từ worker thread rồi mong select đang chờ tự nhận ngay.** Cần hiểu timing và dùng `selector.wakeup()` theo giao thức.
15. **Xem `wakeup()` là “bật OP_WRITE”.** Nó chỉ đánh thức selector; phải thay interest và chuẩn bị queue thật.
16. **Dùng `key.interestOps(key.interestOps() | bit)` đa luồng mà không xét race.** Cân nhắc `interestOpsOr/And` hoặc gom cập nhật về event-loop thread.
17. **Tiếp tục gọi ready/interests sau `cancel()`.** Có thể gặp `CancelledKeyException`.
18. **Chỉ cancel key mà không đóng socket hỏng.** Registration mất nhưng tài nguyên channel vẫn cần được dọn.
19. **Đọc `select(0)` như “poll ngay”.** Với Java Selector, timeout 0 có nghĩa chờ vô hạn; dùng `selectNow()` để poll.
20. **Cho rằng giá trị return của select luôn bằng kích thước selectedKeys.** Nếu còn key từ lượt trước, hai số có thể khác.
21. **Dùng một lần `read`/`write` rồi bỏ qua phần dữ liệu chưa xong.** ByteBuffer position/remaining cần được giữ trong state.
22. **Dùng một handler blocking lâu trong selector thread.** Tất cả kết nối khác có thể bị chậm; tách tác vụ dài khỏi event loop.
23. **Tưởng `attach()` tự quản lý cleanup, timeout hay thread safety.** Attachment chỉ lưu tham chiếu ứng dụng.
24. **Không xử lý `accept()==null` hoặc `finishConnect()` thất bại.** Ready chỉ dẫn hướng thử I/O, không miễn xử lý tình huống cạnh tranh và ngoại lệ.
25. **Đăng ký OP_READ/WRITE không thuộc `channel.validOps()`.** Gây `IllegalArgumentException`; kiểm tra capability đúng loại channel.

## 15. Tự kiểm tra kiến thức

**Câu 1.** `SelectionKey` đại diện cho một lần read event hay một đăng ký channel–selector?  
**Trả lời:** Một đăng ký. Cùng key có thể được chọn nhiều lần.

**Câu 2.** `key.interestOps()==OP_READ` có bảo đảm `key.isReadable()==true` ngay không?  
**Không.** Interest chỉ là thao tác muốn theo dõi; ready phải được selector phát hiện trong selection.

**Câu 3.** Khác nhau giữa `selector.selectedKeys()` và `key.readyOps()`?  
**Trả lời:** selectedKeys là tập `SelectionKey` đã chọn; readyOps là bitmask thao tác sẵn sàng trên *một key*.

**Câu 4.** Làm sao kiểm tra xem bit OP_READ có được bật trong mask?  
**Trả lời:** `(mask & SelectionKey.OP_READ) != 0`. Không dùng `mask == OP_READ` nếu có thể có nhiều bit.

**Câu 5.** Vì sao `OP_WRITE` không nên luôn luôn bật?  
**Vì** socket thường writable khi send buffer còn chỗ, khiến selector đánh thức liên tục dù queue output rỗng.

**Câu 6.** `iterator.remove()` có đóng channel không?  
**Không.** Nó chỉ gỡ key khỏi selected-key set, không cancel registration.

**Câu 7.** Cần làm gì sau `key.cancel()`?  
**Trả lời:** Không dùng interest/ready trên key invalid; đóng channel khi không dùng nữa và để selector xử lý hủy đăng ký.

**Câu 8.** `selector.select(0)` có giống `selectNow()` không?  
**Không.** Timeout 0 của `select(long)` là chờ vô hạn; `selectNow()` mới không chặn.

**Câu 9.** Một socket `isWritable()==true`, `write(ByteBuffer)` có thể trả 0 không?  
**Có.** Readiness không bảo đảm ghi được toàn bộ dữ liệu; giữ byte chưa gửi và chờ lượt khác.

**Câu 10.** Worker thread muốn gửi dữ liệu cho socket do event loop quản lý. Có cần `wakeup()`?  
**Thường cần** nếu selector có thể đang chờ: enqueue dữ liệu theo giao thức thread-safe, bật interest WRITE hoặc gửi task cập nhật interest cho loop, sau đó `wakeup()`.

**Câu 11.** Nếu `SocketChannel` chưa kết nối xong, một lần `isConnectable()` có bảo đảm đã connected?  
**Không.** Phải gọi `finishConnect()` và xử lý ngoại lệ, sau đó mới cập nhật interest.

**Câu 12.** Một pipe SourceChannel hỗ trợ OP_ACCEPT không?  
**Không.** SourceChannel của pipe chỉ hỗ trợ read; xem `validOps()` thay vì áp đặt các bit không liên quan.

## 16. Ghi nhớ và tài liệu chính thức

**Công thức vận hành:** đăng ký `SelectionKey` với **interest set** phù hợp → gọi `select` → duyệt **selected-key set** → đọc **ready set** → thực hiện non-blocking I/O → cập nhật trạng thái và interest → `iterator.remove()` để sẵn sàng cho đợt selection tiếp theo.

Những phân biệt bắt buộc nhớ:

- `interestOps` là **mong muốn theo dõi**, không phải bằng chứng kênh đang ready.
- `readyOps` là **trạng thái đã được selector ghi nhận**, không thay thế việc kiểm tra kết quả thực tế của `read`/`write`.
- `selectedKeys()` là **tập key cần xử lý**, không phải bitmask; key có thể là registered dù không selected.
- `cancel`, `remove khỏi selectedKeys` và `interestOps(0)` là **ba hành động khác nhau**.
- Tắt `OP_WRITE` khi output queue rỗng, dùng `selector.wakeup()` để phối hợp event loop và các thread khác, và quản lý giao thức non-blocking với buffer/attachment riêng.

Tài liệu Java SE 21:

- `SelectionKey` — `interestOps`, `readyOps`, các bit `OP_*`, `cancel`, `attach`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/SelectionKey.html
- `Selector` — các key set, `select`, `selectNow`, `wakeup`, quy tắc cập nhật readiness: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/Selector.html
- `SelectableChannel` — `validOps`, `configureBlocking`, `register`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/SelectableChannel.html
- `SocketChannel` — `connect`, `finishConnect`, `read`, `write`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/SocketChannel.html
- `ServerSocketChannel` — `accept`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/ServerSocketChannel.html
- `Pipe` — SourceChannel và SinkChannel: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/Pipe.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="selector-004">OP_ACCEPT, OP_CONNECT, OP_READ và OP_WRITE</a>

<details>
<summary>Xem chi tiết</summary>

# Selector: OP_ACCEPT, OP_CONNECT, OP_READ và OP_WRITE trong Java NIO

## 1. Bốn cờ biểu diễn điều gì?

Trong Java NIO, `java.nio.channels.Selector` giúp một thread theo dõi trạng thái **sẵn sàng thực hiện I/O** của nhiều `SelectableChannel`. Thay vì liên tục gọi `read()` hoặc `write()` trên từng kết nối và tự đoán kết nối nào có thể tiến triển, chương trình đăng ký các sự kiện quan tâm qua `SelectionKey`.

Bốn hằng số được định nghĩa trong `java.nio.channels.SelectionKey`:

| Operation | Giá trị bit | Channel điển hình | Ý nghĩa |
| --- | ---: | --- | --- |
| `OP_ACCEPT` | 16 | `ServerSocketChannel` | Có kết nối đến đang sẵn sàng để `accept()` |
| `OP_CONNECT` | 8 | `SocketChannel` | Kết nối TCP bất đồng bộ đang sẵn sàng để kiểm tra/hoàn tất bằng `finishConnect()` |
| `OP_READ` | 1 | `SocketChannel`, `DatagramChannel`, `Pipe.SourceChannel` | Thao tác đọc có thể tiến triển hoặc phát hiện EOF/lỗi |
| `OP_WRITE` | 4 | `SocketChannel`, `DatagramChannel`, `Pipe.SinkChannel` | Thao tác ghi có thể tiến triển, thường vì có chỗ trong bộ đệm đầu ra |

Các hằng số là **bit flag**, vì vậy có thể kết hợp bằng phép OR bit `|`:

~~~java
int interests = SelectionKey.OP_READ | SelectionKey.OP_WRITE;
~~~

Biểu thức trên yêu cầu theo dõi **cả đọc và ghi**; nó không thực hiện bất kỳ I/O nào. Một thao tác `OP_ACCEPT` không có nghĩa một client đã được trả về; bạn vẫn cần gọi `ServerSocketChannel.accept()`. `OP_CONNECT` không có nghĩa mọi bước kết nối đã hoàn thành; bạn vẫn cần `SocketChannel.finishConnect()` nếu `connect()` trước đó còn pending.

**Quy tắc cốt lõi:** Selector báo **readiness** (khả năng tiến triển), không báo rằng ứng dụng đã đọc, ghi, chấp nhận hay hoàn tất kết nối thay cho bạn.

## 2. Ba tập thao tác: validOps, interestOps và readyOps

Một `SelectionKey` kết nối **một channel** với **một selector**. Muốn hiểu tại sao một cờ được báo hay không, cần tách ba khái niệm:

1. `channel.validOps()`: tập cờ **channel hỗ trợ về mặt loại**. Ví dụ `ServerSocketChannel` hỗ trợ `OP_ACCEPT`; `SocketChannel` hỗ trợ `OP_CONNECT`, `OP_READ` và `OP_WRITE`.
2. `key.interestOps()`: tập cờ **ứng dụng hiện muốn selector giám sát**. Có thể thay đổi bằng `key.interestOps(mask)` khi key còn hợp lệ.
3. `key.readyOps()`: tập cờ mà selector **đã xác định là sẵn sàng** trên key đã được chọn trong lượt selection. Có thể kiểm tra bằng `isAcceptable()`, `isConnectable()`, `isReadable()` và `isWritable()`.

~~~text
channel.validOps()
    │ tập thao tác mà loại channel hỗ trợ
    ▼
key.interestOps()
    │ ứng dụng chọn bit cần theo dõi
    ▼
selector.select()
    │ phát hiện readiness thực tế
    ▼
key.readyOps() / key.isReadable() / ...
    │
    ▼
Ứng dụng tự gọi accept / finishConnect / read / write
~~~

Không phải cứ có `OP_READ` trong `interestOps` là `isReadable()` lập tức trả `true`. Nó chỉ đăng ký ý định quan sát. Ngược lại, `readyOps` không nên được suy luận như tình trạng I/O bất biến: trạng thái mạng có thể thay đổi ngay sau khi selector đánh dấu ready.

### validOps phải khớp channel

| Loại channel | validOps về mặt loại |
| --- | --- |
| `ServerSocketChannel` | `OP_ACCEPT` |
| `SocketChannel` | `OP_CONNECT \| OP_READ \| OP_WRITE` |
| `DatagramChannel` | `OP_READ \| OP_WRITE` |
| `Pipe.SourceChannel` | `OP_READ` |
| `Pipe.SinkChannel` | `OP_WRITE` |

Không đăng ký `OP_ACCEPT` cho `SocketChannel`, cũng không đăng ký `OP_CONNECT` cho `ServerSocketChannel`. Nếu `interestOps` chứa bit không nằm trong `validOps()`, `register(selector, ops)` hay `interestOps(ops)` có thể ném `IllegalArgumentException`.

Có thể kiểm tra:

~~~java
boolean canAccept =
        (serverChannel.validOps() & SelectionKey.OP_ACCEPT) != 0;
~~~

Đây là mảnh code minh họa có biến `serverChannel` đã mở và import `SelectionKey`, không phải chương trình độc lập.

## 3. Điều kiện trước khi đăng ký với Selector

Một `SelectableChannel` phải được đặt **non-blocking mode** trước khi đăng ký selector. Quy trình nền tảng:

~~~java
Selector selector = Selector.open();
ServerSocketChannel server = ServerSocketChannel.open();
server.configureBlocking(false);
server.bind(new InetSocketAddress("127.0.0.1", 0));
SelectionKey serverKey =
        server.register(selector, SelectionKey.OP_ACCEPT);
~~~

Đây là đoạn minh họa trong phương thức có xử lý `IOException` và đóng tài nguyên bằng `try-with-resources`; các import cần dùng thuộc `java.nio.channels` và `java.net`. Cổng `0` yêu cầu hệ điều hành chọn cổng còn trống.

`register(selector, ops)` trả một `SelectionKey`; `key.channel()` là channel đã đăng ký và `key.selector()` là selector tương ứng. Một channel chỉ có **một key trên mỗi selector**, nhưng có thể đăng ký với nhiều selector nếu đáp ứng điều kiện API. Nếu đã đăng ký trên selector đó, đăng ký lại có thể cập nhật interest set và attachment của key hiện có.

Hãy phân biệt:

- **Blocking mode:** lời gọi I/O có thể đợi dữ liệu/khả năng hoàn thành. Không được đăng ký một channel đang blocking với selector; cố làm vậy ném `IllegalBlockingModeException`.
- **Non-blocking mode:** `accept()` có thể trả `null`, `read()` hoặc `write()` có thể trả `0`; chương trình nên xử lý thông qua readiness thay vì bận chờ vô hạn.
- **Selector:** không thực hiện I/O thay ứng dụng, mà cung cấp tập key đã được chọn để ứng dụng điều phối.

Một chương trình vẫn có thể dùng non-blocking channel không có selector; selector có ý nghĩa khi cần quản lý nhiều channel và tránh thao tác hỏi liên tục từng kết nối.

## 4. OP_ACCEPT: nhận kết nối mới

`OP_ACCEPT` dùng với `ServerSocketChannel` đang mở và đã bind tới địa chỉ lắng nghe. Khi key có `isAcceptable() == true`, hãy gọi `accept()`:

~~~java
if (key.isAcceptable()) {
    ServerSocketChannel server =
            (ServerSocketChannel) key.channel();

    SocketChannel client = server.accept();
    if (client != null) {
        client.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ);
    }
}
~~~

Đây là đoạn nằm **bên trong vòng xử lý selected keys**, giả sử `selector` và `key` đã hợp lệ.

Giải thích:

1. `server` tiếp nhận kết nối; bản thân nó chỉ hỗ trợ `OP_ACCEPT`.
2. `accept()` trả một `SocketChannel` **mới** cho kết nối vừa được chấp nhận, khác với `ServerSocketChannel` đang lắng nghe.
3. Với channel non-blocking, ngay cả sau một thông báo readiness, `accept()` vẫn có thể trả `null` nếu không còn kết nối có thể nhận tại thời điểm gọi.
4. `SocketChannel` vừa nhận phải được cấu hình non-blocking **trước khi register**.
5. Nếu server có thể nhận liên tiếp nhiều kết nối, có thể xử lý nhiều `accept()` trong một lượt với giới hạn hợp lý để không bỏ đói các key khác.

Sau khi accept, dữ liệu của kết nối mới được đọc qua `SocketChannel` và `OP_READ`; không đọc trực tiếp từ `ServerSocketChannel` bằng `OP_READ`.

## 5. OP_CONNECT: hoàn tất kết nối TCP non-blocking

`OP_CONNECT` áp dụng cho **SocketChannel đang thực hiện kết nối chưa hoàn tất**. Quy trình phía client:

~~~java
SocketChannel client = SocketChannel.open();
client.configureBlocking(false);

boolean connected = client.connect(remoteAddress);
if (connected) {
    client.register(selector, SelectionKey.OP_READ);
} else {
    client.register(selector, SelectionKey.OP_CONNECT);
}
~~~

Đây là đoạn minh họa; `remoteAddress` là địa chỉ đích đã xác định, `selector` là Selector đang mở.

Điểm then chốt: `connect(...)` **có thể trả `true` ngay** nếu kết nối hoàn tất tức thì. Khi đó **không cần chờ `OP_CONNECT`**, và có thể đăng ký thẳng `OP_READ`/`OP_WRITE` theo nhu cầu.

Nếu `connect(...)` trả `false`, kết nối còn pending. Khi selector báo `isConnectable()`:

~~~java
if (key.isConnectable()) {
    SocketChannel client = (SocketChannel) key.channel();
    if (client.finishConnect()) {
        key.interestOps(SelectionKey.OP_READ);
    }
}
~~~

`finishConnect()` có thể hoàn tất kết nối, trả `true`, hoặc trả `false` nếu vẫn còn pending trong chế độ non-blocking. Nó cũng có thể ném `IOException` khi kết nối thất bại (ví dụ đích từ chối). Không coi `OP_CONNECT` là bảo đảm kết nối đã thành công.

Sau khi kết nối xong, **thay `OP_CONNECT` bằng những cờ của trạng thái giao thức tiếp theo**. Thường là `OP_READ` nếu cần nhận dữ liệu, hoặc `OP_WRITE` khi có payload cần gửi. Để mãi `OP_CONNECT` trong interest set sau khi đã connected có thể dẫn đến xử lý sự kiện không phù hợp.

## 6. OP_READ: sẵn sàng đọc không đồng nghĩa có đủ một bản tin

`OP_READ` thường xuất hiện khi socket có dữ liệu, đầu bên kia đóng một chiều kết nối hoặc có điều kiện I/O cần ứng dụng xử lý.

~~~java
if (key.isReadable()) {
    SocketChannel channel = (SocketChannel) key.channel();
    ByteBuffer dst = ByteBuffer.allocate(4096);
    int n = channel.read(dst);

    if (n > 0) {
        dst.flip();
        // Tiêu thụ đúng n byte vừa nhận.
    } else if (n == -1) {
        // Peer đã gửi EOF cho chiều đọc này.
        key.cancel();
        channel.close();
    } else {
        // n == 0: chưa có tiến triển; thử lại ở lượt phù hợp.
    }
}
~~~

Đây là mã minh họa, có thể cần thêm cơ chế giữ lại buffer giữa các lần đọc trong ứng dụng thực tế.

Phải phân biệt:

- `n > 0`: có đúng `n` byte mới được đưa vào buffer; buffer `position` đã tăng, nên `flip()` trước khi tiêu thụ.
- `n == 0`: lời gọi chưa chuyển được byte; không được giả định kết nối đã đóng.
- `n == -1`: EOF của channel đọc. Cần quyết định đóng toàn bộ kết nối hay hỗ trợ half-close theo giao thức.
- **Một lần `read` không bảo đảm đủ một HTTP request, một JSON message hay một record ứng dụng.** TCP là luồng byte, không giữ ranh giới bản tin.

Với protocol có header và payload, thường giữ `ByteBuffer` ở attachment của `SelectionKey` hoặc một object connection state. Phân tích các record hoàn chỉnh, sau đó `compact()` để giữ byte còn thiếu và tiếp tục nhận vào lượt kế tiếp. Tránh cấp phát buffer mới rồi vứt dữ liệu chưa phân tích mỗi lần có `OP_READ`.

Readiness chỉ báo cơ hội tiến triển; vì dữ liệu có thể bị một bên khác tiêu thụ hoặc trạng thái thay đổi, lời gọi `read` vẫn cần xử lý giá trị trả về thực tế.

## 7. OP_WRITE: chỉ theo dõi khi thực sự có dữ liệu muốn gửi

`OP_WRITE` báo channel **có khả năng ghi**. Đối với socket TCP thông thường, điều kiện này rất hay đúng do send buffer của hệ điều hành có chỗ. Nếu luôn đăng ký `OP_WRITE` dù không có gì cần gửi, selector có thể liên tục báo writable, khiến vòng lặp tốn CPU.

**Chính sách thường dùng:**

1. Mặc định theo dõi `OP_READ` cho một kết nối đã thiết lập.
2. Khi có dữ liệu cần gửi, đưa dữ liệu vào queue/buffer và thêm `OP_WRITE` vào `interestOps`.
3. Khi selector báo writable, gọi `write` và xử lý số byte ghi thực tế.
4. Nếu còn payload chưa gửi, giữ `OP_WRITE` để tiếp tục ở lượt sau.
5. Khi queue đã rỗng, **bỏ bit `OP_WRITE`** khỏi interest set.

Mảnh code minh họa:

~~~java
if (key.isWritable()) {
    SocketChannel channel = (SocketChannel) key.channel();
    ByteBuffer pending = (ByteBuffer) key.attachment();

    if (pending != null) {
        channel.write(pending);
        if (!pending.hasRemaining()) {
            key.attach(null);
            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
        }
    }
}
~~~

Đây là mảnh ví dụ giả định attachment chỉ lưu một buffer chờ gửi; trong chương trình thực tế, attachment thường là đối tượng chứa cả inbound buffer, outbound queue và trạng thái giao thức.

`write(ByteBuffer)` **không bảo đảm tiêu thụ hết** payload một lần. Nếu trả `0` hoặc còn `pending.hasRemaining()`, chưa được loại buffer. Chỉ hủy `OP_WRITE` khi dữ liệu đã được gửi hết vào channel (hoặc giao thức chủ động hủy việc gửi).

Để thêm bit ghi mà giữ lại bit đọc:

~~~java
key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
~~~

Để bỏ bit ghi:

~~~java
key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
~~~

Dấu `|` thêm bit, `& ~` xóa bit có chọn lọc. Không dùng `key.interestOps(OP_WRITE)` nếu vẫn cần theo dõi `OP_READ`: lời gọi setter này thay thế toàn bộ interest set.

## 8. Chu trình xử lý selectedKeys

Một vòng lặp Selector tối thiểu có dạng:

~~~java
while (running) {
    selector.select();

    Iterator<SelectionKey> it = selector.selectedKeys().iterator();
    while (it.hasNext()) {
        SelectionKey key = it.next();
        it.remove();

        if (!key.isValid()) {
            continue;
        }

        if (key.isAcceptable()) {
            // accept() và đăng ký socket được chấp nhận.
        }
        if (key.isConnectable()) {
            // finishConnect(), đổi interest set khi kết nối hoàn tất.
        }
        if (key.isReadable()) {
            // read(), xử lý n > 0, n == 0, n == -1.
        }
        if (key.isWritable()) {
            // write(), tiếp tục dữ liệu dở, bỏ OP_WRITE khi hết.
        }
    }
}
~~~

Đây là **skeleton minh họa**, chưa có khai báo `running`, `selector` hay xử lý ngoại lệ/đóng channel. Trong hệ thống sản xuất cần các điều kiện dừng và quản lý tài nguyên thích hợp.

Những điểm quan trọng:

- `selector.select()` có thể chặn tới khi có key sẵn sàng, có `wakeup()`, bị interrupt hoặc có điều kiện khác theo API. `select(timeout)` cho phép giới hạn thời gian đợi; phải xử lý cả trường hợp trả 0.
- `selectedKeys()` là tập key đã chọn và **không tự được dọn** chỉ vì đã duyệt qua. Dùng `it.remove()` sau khi lấy key.
- `key.isValid()` kiểm tra key còn hợp lệ. Khi hủy key qua `cancel()` hoặc đóng channel, key sẽ không còn dùng được cho các phép kiểm tra readiness.
- `isReadable()` và `isWritable()` có thể cùng đúng trên **một key SocketChannel**, nên code có thể xử lý nhiều điều kiện trong cùng lượt; nếu một nhánh đóng/cancel channel, phải tránh các nhánh sau sử dụng key không còn hợp lệ.
- Trong code chạy thực tế, nên định tuyến theo **loại channel/trạng thái kết nối** và kiểm tra tính hợp lệ sau hành động có thể đóng channel.
- `interestOps` là tập đang theo dõi, trong khi `readyOps` là kết quả select; việc thay interest set không có nghĩa bit ready cũ tự biến thành một lần I/O đã xảy ra.

Một số hệ thống dùng một thread Selector và các worker khác tạo dữ liệu chờ gửi. Khi worker khác thay đổi state, có thể cần `selector.wakeup()` để thread đang đợi select nhận biết thay đổi kịp thời. Tuy vậy, `wakeup()` không thay thế cơ chế đồng bộ của queue hoặc trạng thái chia sẻ giữa các thread.

## 9. Ví dụ Java 17+ chạy độc lập: TCP loopback sử dụng cả bốn cờ

Ví dụ dưới đây tạo một server và một client trong cùng tiến trình, qua địa chỉ loopback và cổng ngẫu nhiên do OS cấp. Chương trình dùng Selector để:

- `OP_ACCEPT`: server chấp nhận socket mới;
- `OP_CONNECT`: client theo dõi hoàn tất kết nối **nếu connect còn pending**;
- `OP_WRITE`: client gửi chuỗi ASCII `PING`, sau đó bỏ interest ghi;
- `OP_READ`: socket phía server đọc đủ bốn byte.

Ví dụ chỉ in kết quả cuối cùng để stdout không phụ thuộc thứ tự các key được selector trả về. `OP_CONNECT` có thể không phát sinh trong lần chạy cụ thể nếu `connect()` hoàn tất ngay; code xử lý đúng cả hai khả năng.

Lưu thành `SelectorOperationsDemo.java`:

~~~java
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

public class SelectorOperationsDemo {
    public static void main(String[] args) throws IOException {
        try (Selector selector = Selector.open();
             ServerSocketChannel server = ServerSocketChannel.open();
             SocketChannel client = SocketChannel.open()) {

            server.configureBlocking(false);
            server.bind(new InetSocketAddress(
                    InetAddress.getLoopbackAddress(), 0));
            server.register(selector, SelectionKey.OP_ACCEPT);

            client.configureBlocking(false);
            boolean connectedNow = client.connect(server.getLocalAddress());
            client.register(selector, connectedNow
                    ? SelectionKey.OP_WRITE
                    : SelectionKey.OP_CONNECT);

            ByteBuffer outbound = ByteBuffer.wrap(
                    "PING".getBytes(StandardCharsets.US_ASCII));
            ByteBuffer inbound = ByteBuffer.allocate(4);
            SocketChannel accepted = null;
            boolean acceptedConnection = false;

            try {
                long deadline = System.nanoTime()
                        + TimeUnit.SECONDS.toNanos(10);

                while (inbound.hasRemaining()
                        && System.nanoTime() < deadline) {

                    selector.select(250);
                    Iterator<SelectionKey> it =
                            selector.selectedKeys().iterator();

                    while (it.hasNext()) {
                        SelectionKey key = it.next();
                        it.remove();

                        if (!key.isValid()) {
                            continue;
                        }

                        if (key.isAcceptable()) {
                            ServerSocketChannel listener =
                                    (ServerSocketChannel) key.channel();
                            SocketChannel peer = listener.accept();

                            if (peer != null) {
                                peer.configureBlocking(false);
                                peer.register(selector, SelectionKey.OP_READ);
                                accepted = peer;
                                acceptedConnection = true;
                                key.interestOps(0);
                            }
                        } else if (key.isConnectable()) {
                            SocketChannel connecting =
                                    (SocketChannel) key.channel();
                            if (connecting.finishConnect()) {
                                key.interestOps(SelectionKey.OP_WRITE);
                            }
                        } else if (key.isWritable()) {
                            SocketChannel sender =
                                    (SocketChannel) key.channel();
                            sender.write(outbound);

                            if (!outbound.hasRemaining()) {
                                key.interestOps(0);
                            }
                        } else if (key.isReadable()) {
                            SocketChannel receiver =
                                    (SocketChannel) key.channel();
                            int n = receiver.read(inbound);

                            if (n == -1) {
                                throw new EOFException(
                                        "Connection ended before PING");
                            }
                        }
                    }
                }

                if (inbound.hasRemaining()) {
                    throw new IOException("Timed out waiting for PING");
                }

                inbound.flip();
                System.out.println("accepted=" + acceptedConnection);
                System.out.println("connected=" + client.isConnected());
                System.out.println("message="
                        + StandardCharsets.US_ASCII.decode(inbound));
            } finally {
                if (accepted != null) {
                    accepted.close();
                }
            }
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac SelectorOperationsDemo.java
java SelectorOperationsDemo
~~~

Stdout mong đợi khi môi trường cho phép TCP loopback và kết nối/đọc ghi hoàn thành bình thường:

~~~text
accepted=true
connected=true
message=PING
~~~

Lý giải từng giai đoạn:

1. `server` mở ở non-blocking mode, bind loopback, đăng ký `OP_ACCEPT`.
2. `client.connect(...)` có thể trả `true` hoặc `false`. Nếu `true`, client đã nối thành công và đăng ký `OP_WRITE` ngay; nếu `false`, client đăng ký `OP_CONNECT`, sau đó `finishConnect()` chuyển sang `OP_WRITE`.
3. Selector báo `OP_ACCEPT` khi server có kết nối sẵn sàng. `accept()` trả socket `peer` (có thể `null` trong một thời điểm); code đăng ký socket này với `OP_READ`.
4. Khi client có thể ghi, `OP_WRITE` khiến code gửi dần buffer `PING`. Nếu chưa gửi hết, giữ `OP_WRITE`; khi gửi hết, `interestOps(0)` để tránh vòng lặp writable liên tục.
5. `OP_READ` của socket phía server khiến code đọc vào `inbound`. Nếu mới nhận một phần thì buffer vẫn có `remaining()`, vòng lặp tiếp tục chờ.
6. Khi đủ bốn byte, vòng lặp kết thúc và in `PING`. Hai biến boolean mô tả kết nối cuối cùng, không phụ thuộc việc `OP_CONNECT` có xuất hiện hay không.
7. `selectedKeys` được loại từng key bằng `Iterator.remove()`; các socket, server và Selector được đóng khi hoàn tất.

**Phạm vi ví dụ:** cần một môi trường cho phép tạo socket loopback. Timeout 10 giây giúp thất bại rõ ràng khi môi trường bị chặn hoặc quá chậm; khi đó chương trình ném `IOException` thay vì in đủ ba dòng. Đây là mẫu học tập, chưa triển khai các yêu cầu production như hàng đợi dữ liệu nhiều bản tin, backpressure tổng quát, quản lý timeout per-connection và giới hạn tài nguyên.

## 10. Khi dùng interestOps nên đổi như thế nào?

Một lifecycle kết nối TCP thường có dạng:

~~~text
Phía server: ServerSocketChannel
    OP_ACCEPT
       |
       | accept() thành công
       v
SocketChannel được chấp nhận
    OP_READ  <-------------+
       |                   |
       | có dữ liệu cần gửi|
       v                   |
  OP_READ | OP_WRITE       |
       |                   |
       | gửi hết           |
       +-------------------+

Phía client: SocketChannel
    connect(...)
       |
       +-- trả true --------------> OP_READ / OP_WRITE
       |
       +-- trả false --> OP_CONNECT
                           |
                           | finishConnect() thành công
                           v
                      OP_READ / OP_WRITE
~~~

Sơ đồ trình bày trạng thái **interest set** mà chương trình điều chỉnh. Đây không phải bộ máy tự động của Selector; logic ứng dụng chịu trách nhiệm chuyển trạng thái.

Bảng quyết định:

| Tình huống | Interest set hợp lý |
| --- | --- |
| Server chờ kết nối mới | `OP_ACCEPT` |
| Client non-blocking còn pending connect | `OP_CONNECT` |
| Đã kết nối, chờ request/response tới | `OP_READ` |
| Đã kết nối, có dữ liệu outbound còn chờ | `OP_READ \| OP_WRITE` (hoặc OP_WRITE khi chủ động tạm dừng đọc) |
| Đã gửi hết payload | Bỏ `OP_WRITE`; thường giữ `OP_READ` |
| Kết nối đóng hoặc không còn cần theo dõi | `cancel()` key rồi đóng channel nếu đã kết thúc vòng đời |

Không nên luôn đăng ký `OP_READ | OP_WRITE` cho mọi socket khi hoàn toàn không có outbound data. Vì writable thường sẵn sàng, chương trình có thể liên tục xử lý event ghi giả, làm tăng CPU usage đáng kể.

## 11. Các lỗi thường gặp

### Nhầm operation với hành động đã hoàn tất

`isAcceptable()` không tự tạo client; `isConnectable()` không tự hoàn thành kết nối; `isReadable()` không tự đổ byte vào buffer; `isWritable()` không tự chuyển payload. **Cần gọi đúng API I/O** và kiểm tra kết quả.

### Đăng ký operation không hợp lệ

`ServerSocketChannel` không hỗ trợ `OP_READ` hoặc `OP_CONNECT`. `SocketChannel` không hỗ trợ `OP_ACCEPT`. Dùng `validOps()` để phân biệt và tránh `IllegalArgumentException`.

### Quên configureBlocking(false)

Đăng ký channel đang blocking với Selector gây `IllegalBlockingModeException`. Cấu hình non-blocking trước khi `register`.

### Bỏ qua nhánh kết nối hoàn tất ngay

`SocketChannel.connect(address)` có thể trả `true`. Nếu chương trình luôn chờ `OP_CONNECT` sau đó, có thể không tiến triển đúng. Khi trả true, chuyển ngay sang interest set cần thiết của kết nối đã thành lập.

### Không gọi finishConnect

Với connect non-blocking đang pending, phải `finishConnect()` để hoàn tất hoặc nhận thông báo lỗi. Bỏ qua bước này rồi đọc/ghi ngay có thể dẫn tới `NotYetConnectedException`.

### Đăng ký OP_WRITE liên tục

Đây là lỗi hiệu năng kinh điển. Vì socket thường writable, selector có thể liên tục đánh thức thread. Chỉ thêm `OP_WRITE` khi có dữ liệu cần gửi, xóa ngay khi dữ liệu được gửi hết.

### Không xử lý I/O một phần

Một sự kiện `OP_READ` có thể chỉ đọc được vài byte của bản tin; một `OP_WRITE` có thể chỉ gửi được một phần buffer. Với TCP không có ranh giới message tự nhiên, phải giữ state giữa các lượt và xây cơ chế framing.

### Không remove key khỏi selectedKeys

Nếu dùng `selector.selectedKeys()` mà không `iterator.remove()`, key vẫn còn trong tập đã chọn và có thể gây xử lý lặp không mong muốn. Selected set thuộc trách nhiệm quản lý của vòng lặp event.

### Bỏ qua key invalid và thay đổi trạng thái trong vòng lặp

Sau khi một nhánh đóng channel hoặc `cancel()`, các lời gọi kiểm tra readiness sau đó có thể ném `CancelledKeyException`. Kiểm tra `isValid()` và tránh dùng lại key đã hủy.

### Đụng cùng Selector từ nhiều thread mà không phối hợp

Nếu thread khác thêm dữ liệu vào queue hoặc đổi interest set, cần thiết kế thread safety cho queue/state và cân nhắc `selector.wakeup()` để vòng select biết có công việc mới. `wakeup()` không tự đồng bộ mọi biến và không tạo ra một bản tin mạng.

## 12. Câu hỏi tự kiểm tra

1. `ServerSocketChannel` cần theo dõi cờ nào để nhận kết nối? **`OP_ACCEPT`**. Kết nối mới được lấy bằng **`accept()`**.
2. Client gọi `connect(...)` và nhận `false`. Nên quan tâm sự kiện nào? **`OP_CONNECT`**, sau đó gọi `finishConnect()`.
3. Client gọi `connect(...)` và nhận `true`. Có bắt buộc chờ `OP_CONNECT` không? **Không**; kết nối đã hoàn thành ngay, hãy theo dõi read/write theo nhu cầu.
4. Sau `OP_READ`, `read(buffer)` trả `0` nghĩa là EOF? **Không**. EOF là **`-1`**; 0 có nghĩa chưa đọc được byte trong lần gọi.
5. Vì sao không nên để `OP_WRITE` luôn được đăng ký? Vì socket rất hay writable, dễ tạo vòng lặp tốn CPU khi không có payload.
6. `key.interestOps(OP_WRITE)` có giữ lại `OP_READ` đã đăng ký không? **Không**; setter thay cả mask. Dùng `key.interestOps(key.interestOps() | OP_WRITE)` để thêm mà giữ bit cũ.
7. `validOps` và `readyOps` khác nhau thế nào? **validOps** là các thao tác loại channel có thể hỗ trợ; **readyOps** là những thao tác được selector báo ready ở lượt selection.
8. Vì sao cần `it.remove()` trong vòng duyệt `selectedKeys()`? Vì tập selected key không tự dọn sau khi ứng dụng xử lý.
9. `OP_CONNECT` có áp dụng cho socket phía server vừa `accept` không? Thông thường **không cần**; socket nhận qua `accept()` đã có kết nối và thường được đăng ký `OP_READ`.
10. Trong event loop, nhận `OP_WRITE` và `channel.write(buffer)` mới ghi được một phần. Xử lý sao? **Giữ buffer còn byte, giữ `OP_WRITE`, thử tiếp khi có readiness**.

**Ghi nhớ:** `OP_ACCEPT` báo cơ hội nhận kết nối, `OP_CONNECT` báo cơ hội hoàn tất kết nối pending, `OP_READ` báo cơ hội đọc/nhận EOF, `OP_WRITE` báo cơ hội ghi. Chúng là **bit của interest/ready state**, không phải thao tác I/O. Mẫu NIO hiệu quả là đăng ký đúng `validOps`, xử lý `readyOps` dựa trên kết quả gọi channel, bảo toàn dữ liệu chưa hoàn tất, và **chỉ giữ `OP_WRITE` khi có outbound data**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="selector-005">select, selectNow và select có timeout</a>

<details>
<summary>Xem chi tiết</summary>

# Java NIO Selector: `select()`, `selectNow()` và `select(timeout)`

## 1. Selector giải quyết bài toán gì?

Khi server cần theo dõi hàng trăm socket, tạo một luồng luôn chờ đọc trên từng kết nối có thể tốn tài nguyên. Java NIO cung cấp **`java.nio.channels.Selector`** để một luồng kiểm tra những **`SelectableChannel`** nào hiện *sẵn sàng* thực hiện thao tác I/O đã quan tâm, rồi xử lý các channel tương ứng.

Selector không trực tiếp chuyển dữ liệu. Nó chỉ giúp ứng dụng nhận biết các điều kiện sẵn sàng như:

- `SelectionKey.OP_ACCEPT`: server socket có khả năng chấp nhận kết nối.
- `SelectionKey.OP_CONNECT`: socket đang kết nối có thể hoàn tất kết nối.
- `SelectionKey.OP_READ`: channel có thể thực hiện thao tác đọc mà không chờ dữ liệu theo nghĩa readiness của hệ thống.
- `SelectionKey.OP_WRITE`: channel có thể thực hiện thao tác ghi theo điều kiện readiness.

**Sẵn sàng không đồng nghĩa hoàn tất công việc.** Socket sẵn sàng đọc vẫn có thể trả `0`, `-1` hoặc một lượng byte nhỏ hơn kỳ vọng; socket sẵn sàng ghi vẫn có thể chỉ ghi một phần. Selector không thay thế việc kiểm tra kết quả `read/write` và quản lý `ByteBuffer`.

Ba cách chọn key là trọng tâm:

1. `selector.select()`: **chờ** cho đến khi có sự kiện sẵn sàng hoặc được đánh thức/ngắt.
2. `selector.selectNow()`: **kiểm tra ngay**, không chờ.
3. `selector.select(timeoutMillis)`: **chờ có giới hạn thời gian** nếu `timeoutMillis > 0`; riêng **`timeoutMillis == 0` lại chờ không giới hạn**.

Một chương trình non-blocking thường dùng `select()` hoặc `select(timeout)` trong **event loop**. `selectNow()` thích hợp khi chương trình còn các công việc khác và chỉ muốn thăm dò I/O ngay lúc đó.

## 2. Chuẩn bị Selector, channel và SelectionKey

Cú pháp cơ bản:

~~~java
// Đoạn minh họa, không phải toàn bộ chương trình.
try (Selector selector = Selector.open();
     ServerSocketChannel server = ServerSocketChannel.open()) {
    server.configureBlocking(false);
    server.bind(new InetSocketAddress("127.0.0.1", 8080));

    SelectionKey key = server.register(
            selector, SelectionKey.OP_ACCEPT);
    // Chạy vòng chọn và xử lý sự kiện...
}
~~~

Cần các import tương ứng từ `java.net` và `java.nio.channels`. Chỉ đăng ký được channel hỗ trợ selection và được đặt ở **non-blocking mode**:

~~~java
channel.configureBlocking(false);
SelectionKey key = channel.register(selector, SelectionKey.OP_READ);
~~~

Nếu cố đăng ký một channel đang ở blocking mode, có thể gặp `IllegalBlockingModeException`. Nếu interest operation không thuộc `channel.validOps()`, `register` có thể ném `IllegalArgumentException`. Một `FileChannel` thông thường **không phải** `SelectableChannel`; không thể đăng ký FileChannel với Selector như socket.

### Bộ ba tập key

Mỗi selector duy trì những tập key liên quan:

- **Key set — `selector.keys()`:** các `SelectionKey` đã đăng ký với selector, chưa được hủy/loại bỏ hoàn toàn.
- **Selected-key set — `selector.selectedKeys()`:** các key được lựa chọn và chưa bị ứng dụng lấy khỏi tập đã chọn.
- **Cancelled-key set:** tập xử lý nội bộ của selector; `key.cancel()` sẽ lên lịch hủy, và key bị loại khỏi tập trong quá trình chọn tiếp theo hoặc khi selector xử lý việc hủy.

Một `SelectionKey` liên kết **một channel + một selector**, có các thuộc tính:

| Thành phần | Ý nghĩa |
|---|---|
| `channel()` | Channel được đăng ký |
| `selector()` | Selector sở hữu key |
| `interestOps()` | Những thao tác ứng dụng **muốn được thông báo**, như `OP_READ \| OP_WRITE` |
| `readyOps()` | Những thao tác đã được ghi nhận là **sẵn sàng** sau selection |
| `isReadable()/isWritable()/isAcceptable()/isConnectable()` | Kiểm tra bit trong `readyOps` |
| `attachment()/attach(obj)` | Gắn trạng thái kết nối hoặc đối tượng xử lý của ứng dụng |
| `isValid()/cancel()` | Kiểm tra/hủy đăng ký |

Không nhầm `interestOps` (cái muốn theo dõi) với `readyOps` (cái đã quan sát sẵn sàng). Trong nhiều tình huống ứng dụng chỉ cần quan tâm `OP_READ`, không nên bật `OP_WRITE` mọi lúc.

## 3. `select()`: chờ sự kiện

Chữ ký:

~~~java
int readyCount = selector.select();
~~~

`select()` thực hiện một lượt selection **blocking**. Theo đặc tả, lời gọi có thể kết thúc khi:

- Có ít nhất một channel được chọn vì sẵn sàng với thao tác thuộc interest set.
- Luồng khác gọi `selector.wakeup()`.
- Luồng đang chọn bị `interrupt()`.
- Selector bị đóng trong quá trình chờ (trường hợp lỗi/vòng đời cần xử lý).

Nó **không có tham số giới hạn thời gian**. Nếu không có gì sẵn sàng và không có tác nhân đánh thức, luồng có thể chờ lâu. Khác với `Thread.sleep()`, selector chờ các điều kiện I/O và quản lý đăng ký key.

Ví dụ luồng xử lý một listener TCP thường là:

~~~java
// Minh họa trong event loop: selector và server đã mở/đăng ký.
while (running) {
    int updated = selector.select();
    if (updated == 0) {
        continue; // Có thể do wakeup/interrupt, không tự giả định có I/O.
    }

    Iterator<SelectionKey> it = selector.selectedKeys().iterator();
    while (it.hasNext()) {
        SelectionKey key = it.next();
        it.remove(); // Xóa khỏi selected-key set để lần sau chọn lại.
        if (!key.isValid()) {
            continue;
        }
        if (key.isAcceptable()) {
            // server.accept(), cấu hình socket non-blocking và register.
        } else if (key.isReadable()) {
            // channel.read(buffer), xử lý 0 / -1 / partial read.
        }
    }
}
~~~

Đoạn trên **không độc lập chạy được** vì `running`, các channel và nhánh xử lý chưa được khai báo. Mục 7 có ví dụ hoàn chỉnh Java 17+.

**Không dùng số trả về để quyết định xử lý một cách máy móc.** `select()` trả số key có **ready-operation set được cập nhật** trong lượt chọn; không nhất thiết bằng số key hiện diện trong `selectedKeys()`. Khi muốn xử lý các key đã được chọn, hãy duyệt selected-key set theo vòng đời của nó.

## 4. `selectNow()`: kiểm tra ngay mà không chờ

Chữ ký:

~~~java
int updated = selector.selectNow();
~~~

- Thực hiện selection **non-blocking**: không chờ một channel trở nên sẵn sàng.
- Có thể trả về `0` nếu không có thao tác sẵn sàng mới được ghi nhận.
- Có thể trả về số dương nếu ready-operation sets của các key được cập nhật.
- Vẫn cập nhật selected-key set theo quy tắc selection thông thường.
- **Xóa tác dụng của các lời gọi `wakeup()` còn đang chờ** trước đó.

Ví dụ:

~~~java
// Minh họa: selector đã có ít nhất một registered key.
doOtherWork();
int updated = selector.selectNow();
if (updated > 0) {
    processSelectedKeys(selector);
}
~~~

`doOtherWork()` và `processSelectedKeys(...)` là hàm do ứng dụng định nghĩa. Nếu luồng liên tục gọi `selectNow()` trong một vòng không chờ và không có việc khác, nó có thể trở thành **busy polling**, tiêu tốn CPU.

### Trường hợp thực tế nên dùng

- Tích hợp việc kiểm tra I/O vào một vòng lặp đã có scheduler, game loop hoặc tác vụ tính toán.
- Kiểm tra xem có event nào sẵn sàng *ngay lúc này* mà không dừng công việc chính.
- Thực hiện một lượt selection không chờ để đồng bộ hóa trạng thái key sau một thay đổi nhất định — nhưng phải nhớ nó có thể **tiêu thụ tác dụng wakeup đang chờ**.

`selectNow()` **không có nghĩa lấy các byte ngay lập tức** và **không tự chuyển channel về non-blocking**. Việc đọc/ghi vẫn phải được ứng dụng thực hiện và phải xử lý kết quả chính xác.

## 5. `select(timeout)`: chờ có giới hạn, với bẫy `timeout=0`

Chữ ký:

~~~java
int readyCount = selector.select(500); // timeout dương: chờ khoảng tối đa 500 ms.
~~~

Tham số của overload này là **`long`, đơn vị millisecond**, không phải giây hoặc nanosecond.

| Cách gọi | Chế độ chờ | Nhận xét |
|---|---|---|
| `selector.select()` | Chờ không thời hạn mặc định | Trả khi có readiness, wakeup, interrupt hoặc lỗi/vòng đời tương ứng |
| `selector.selectNow()` | Không chờ | Trả về sau lượt thăm dò hiện tại |
| `selector.select(1)` | Chờ tối đa theo yêu cầu khoảng 1 ms | Không đảm bảo precision 1 ms |
| `selector.select(500)` | Chờ tối đa khoảng 500 ms | Có thể trở về trước timeout |
| `selector.select(0)` | **Chờ vô thời hạn** | **Không tương đương `selectNow()`** |
| `selector.select(-1)` | Không hợp lệ | Ném `IllegalArgumentException` |

Chính xác hơn, Java **không hứa thời hạn real-time tuyệt đối**: scheduling, OS và timer resolution có thể khiến thời điểm trở về sớm/trễ so với thời gian đồng hồ dự kiến. `timeout > 0` là giới hạn chờ theo ngữ nghĩa API, không phải một SLA thời gian cứng.

### Ví dụ dùng timeout cho công việc định kỳ

Một ứng dụng cần xử lý I/O nhưng ít nhất khoảng một lần trong mỗi chu kỳ kiểm tra timer:

~~~java
// Minh họa: selector đã được cấu hình, không phải chương trình độc lập.
while (running) {
    selector.select(250);
    handleSelectedKeys(selector);
    runScheduledTasksIfDue();
}
~~~

Timeout này tránh việc **đợi I/O vô thời hạn** khiến timer không bao giờ được kiểm tra. Tuy vậy:

- Không bảo đảm `runScheduledTasksIfDue()` chạy *chính xác* mỗi 250 ms.
- Nếu `handleSelectedKeys(...)` xử lý lâu, timer còn có thể bị trễ.
- Nếu phải đợi deadline chính xác hơn, tính **thời gian còn lại tới deadline gần nhất**, thay vì luôn chọn một timeout cố định.
- Nếu có thread khác đẩy task cần chạy gấp vào queue, dùng `selector.wakeup()` để không cần chờ hết timeout.

### Không tạo timeout bằng cách luôn truyền 0

Một lỗi nguy hiểm:

~~~java
// Sai nếu mong kiểm tra ngay lập tức:
selector.select(0); // 0 = chờ vô thời hạn, KHÔNG phải poll.
~~~

Hãy dùng `selectNow()` để **poll không chờ**. Nếu cần deadline thực, dùng timeout dương và duy trì deadline bằng `System.nanoTime()` (thích hợp đo khoảng thời gian, tránh phụ thuộc đồng hồ wall-clock bị điều chỉnh).

## 6. So sánh ba phương thức ở cùng một tình huống

Giả sử một `Pipe.SourceChannel` hoặc `SocketChannel` đã đăng ký `OP_READ`, nhưng hiện tại chưa có byte nào để đọc:

~~~text
Không có dữ liệu:
  select()       -> chờ đến khi có sự kiện hoặc wakeup/interrupt
  selectNow()    -> thăm dò rồi trở về ngay, thường trả 0
  select(100)    -> chờ tới khi có event hoặc khoảng 100 ms
  select(0)      -> chờ không thời hạn

Khi dữ liệu mới xuất hiện:
  cả ba phương thức đều có thể ghi nhận OP_READ
  selectedKeys() chứa key tương ứng để ứng dụng xử lý
  ứng dụng vẫn phải gọi read(...) và kiểm tra kết quả
~~~

| Tiêu chí | `select()` | `selectNow()` | `select(timeout > 0)` |
|---|---|---|---|
| Có thể block | Có | Không | Có, tới khi sự kiện/wakeup/interrupt/timeout |
| Có tham số thời gian | Không | Không | Có: millisecond |
| Trả `0` được không | Có | Có | Có |
| Cập nhật `selectedKeys()` | Có | Có | Có |
| Được `wakeup()` đánh thức | Có | Không cần chờ; đồng thời xóa wakeup pending | Có |
| Hợp với trường hợp nào | Event loop chủ yếu chờ I/O | Poll lồng vào công việc khác | Event loop có deadline hoặc timer |
| Rủi ro khi dùng sai | Không chạy việc định kỳ nếu thiếu wakeup | Spin/busy polling tốn CPU | Nhầm `0` với non-blocking; kỳ vọng độ chính xác timer tuyệt đối |

Điểm chung: **kết quả trả về không phải số byte** và không phải “bao nhiêu socket đã đọc xong”. Nó liên quan số key có ready set được cập nhật trong lượt chọn.

## 7. Chương trình Java 17+ hoàn chỉnh: kiểm thử cả ba hình thức

Ví dụ dùng `java.nio.channels.Pipe` nên **không cần port mạng** và không bị phụ thuộc vào kết nối Internet. `Pipe.SourceChannel` là `SelectableChannel`, hỗ trợ `OP_READ`; `Pipe.SinkChannel` ghi dữ liệu vào đầu kia.

Chương trình làm theo thứ tự:

1. Đăng ký source non-blocking để theo dõi `OP_READ`.
2. Gọi `selectNow()` khi pipe chưa có dữ liệu.
3. Gọi `select(40)` khi chưa có dữ liệu — kết thúc bởi timeout.
4. Ghi hai byte ASCII `OK` vào sink; sau đó gọi `select()` và nhận key có `OP_READ`.
5. Duyệt `selectedKeys()`, xóa key đã chọn và đọc ra dữ liệu.
6. Gọi `wakeup()` trước một lần `select()` khác để chứng minh có thể đánh thức mà không phải chờ dữ liệu.

Lưu chính xác tệp **`SelectorSelectModesDemo.java`**:

~~~java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

public class SelectorSelectModesDemo {

    private static void writeAll(Pipe.SinkChannel sink, ByteBuffer src)
            throws IOException {
        while (src.hasRemaining()) {
            sink.write(src); // Sink mặc định ở blocking mode.
        }
    }

    public static void main(String[] args) throws IOException {
        Pipe pipe = Pipe.open();

        try (Selector selector = Selector.open();
             Pipe.SourceChannel source = pipe.source();
             Pipe.SinkChannel sink = pipe.sink()) {

            source.configureBlocking(false);
            source.register(selector, SelectionKey.OP_READ);

            System.out.println("selectNow(empty)="
                    + selector.selectNow());

            System.out.println("select(40,empty)="
                    + selector.select(40));

            writeAll(sink, ByteBuffer.wrap(
                    "OK".getBytes(StandardCharsets.US_ASCII)));

            System.out.println("select(ready)="
                    + selector.select());

            Iterator<SelectionKey> iterator =
                    selector.selectedKeys().iterator();

            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove(); // Rất quan trọng.

                if (!key.isValid() || !key.isReadable()) {
                    continue;
                }

                Pipe.SourceChannel readable =
                        (Pipe.SourceChannel) key.channel();
                ByteBuffer data = ByteBuffer.allocate(16);
                int count = readable.read(data);

                data.flip();
                byte[] bytes = new byte[data.remaining()];
                data.get(bytes);

                System.out.println("read(" + count + ")="
                        + new String(bytes, StandardCharsets.US_ASCII));
            }

            selector.wakeup();
            System.out.println("select(wakeup)=" + selector.select());
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac SelectorSelectModesDemo.java
java SelectorSelectModesDemo
~~~

**Stdout trong điều kiện chạy bình thường, không có ngắt luồng hay tác nhân I/O bên ngoài:**

~~~text
selectNow(empty)=0
select(40,empty)=0
select(ready)=1
read(2)=OK
select(wakeup)=0
~~~

Giải thích từng dòng:

- `selectNow(empty)=0` vì chưa có byte và lệnh không chờ.
- `select(40,empty)=0` vì chưa có readiness trước khi timeout khoảng 40 ms (không có yêu cầu đo chính xác số milliseconds).
- Sau `writeAll`, pipe đã có hai byte; source được xem là read-ready nên `select()` ghi nhận một key.
- `selectedKeys()` được duyệt và `iterator.remove()` xóa key khỏi tập đã chọn. `read(...)` lấy được hai byte và chương trình hiển thị `OK`.
- Sau khi byte đã được đọc hết, `selector.wakeup()` làm cho lần `select()` tiếp theo trở về ngay và ở ví dụ này không có key ready mới, nên trả 0.

Ví dụ **không dùng `Thread.sleep()` để giả lập I/O**; timeout được chứng minh qua lượt chọn không có dữ liệu và readiness được kích hoạt bởi ghi pipe. Trong một môi trường có sự cố I/O, đóng kênh sớm hoặc ngắt luồng từ ngoài, kết quả có thể không giống stdout mẫu — ứng dụng thật phải xử lý các trường hợp đó.

### Vì sao source cần non-blocking?

Các channel được `register(selector, ...)` phải cho phép selection. `source.configureBlocking(false)` là bước bắt buộc. Lệnh `sink.write(...)` trong ví dụ không đăng ký selector và có thể hoạt động blocking. Đó là hai đối tượng channel khác nhau nên không mâu thuẫn.

## 8. `selectedKeys()` không tự rỗng sau mỗi lần select

Một điểm dễ dẫn tới lỗi là **tập đã chọn tồn tại giữa các lần gọi selection**.

Quy trình của các phương thức `select()` / `selectNow()` / `select(timeout)`:

1. Kiểm tra các đăng ký đang bị hủy và bỏ key không còn hợp lệ khi cần.
2. Hỏi hệ thống về các thao tác channel đang sẵn sàng.
3. Nếu key chưa nằm trong selected-key set, thêm vào và đặt ready-operation set.
4. Nếu key **đã** nằm trong selected-key set, các readiness bit mới có thể được **hợp** (OR) vào ready-operation set hiện có.
5. Selected-key set không được tự động xóa hết bởi mỗi lần select. **Ứng dụng chịu trách nhiệm lấy key đã xử lý ra khỏi tập**.

Ví dụ cách chuẩn:

~~~java
// Đoạn minh họa thuộc một event loop.
int updated = selector.select();
Iterator<SelectionKey> iter = selector.selectedKeys().iterator();

while (iter.hasNext()) {
    SelectionKey key = iter.next();
    iter.remove(); // Không để key cũ lưu mãi trong selectedKeys().

    if (!key.isValid()) {
        continue;
    }
    if (key.isReadable()) {
        // Đọc tới khi phù hợp với non-blocking I/O và protocol.
    }
}
~~~

Có thể dùng `selector.selectedKeys().clear()` sau khi đã lấy một bản sao các key, nhưng `iterator.remove()` trong lúc duyệt thường rõ ràng và ít tạo lỗi hơn. **Không gọi `selector.keys().clear()`** với ý định chỉ loại những key vừa được chọn; `keys()` là tập các đăng ký, không phải selected-key set để consume.

### Vì sao `selectedKeys().size()` và return value có thể khác?

Một key tồn tại trong selected-key set từ lượt trước có thể không được cập nhật readiness ở lượt hiện tại, song vẫn ở trong tập vì ứng dụng chưa remove. Vì vậy:

- `select()` có thể trả `0` mà `selectedKeys()` **chưa rỗng**.
- Không coi `selectedKeys().size()` là “số sự kiện mới trong lượt này”.
- Không dựa vào return value một cách độc lập với việc quản lý tập key và trạng thái channel.

Nếu để key cũ trong selected set quá lâu, `isReadable()` có thể khiến logic xử lý dữ liệu đã cạn hoặc readiness không còn khớp kỳ vọng. Điều này đặc biệt dễ gây busy loop và khó debug khi nhiều thao tác được OR vào `readyOps()`.

## 9. `wakeup()`, interrupt và close

### `selector.wakeup()`

Khi một thread đang chờ trong `select()` hoặc `select(timeout)`, thread khác có thể yêu cầu nó trở về:

~~~java
// Thread quản lý cập nhật tasks/interests:
taskQueue.add(task);
selector.wakeup();
~~~

Event loop sau khi thức dậy cần kiểm tra task queue, thay đổi registrations/interests an toàn và tiếp tục làm việc.

**Quy tắc ít được chú ý:**

- Nếu selection đang chờ, `wakeup()` khiến nó có thể trở về nhanh.
- Nếu chưa có selection đang chờ, hiệu ứng wakeup được giữ cho **lần selection tiếp theo**; lần đó có thể trở về ngay.
- **`selectNow()` tiêu thụ/xóa một wakeup đang chờ**.
- Gọi `wakeup()` nhiều lần trước khi có selection mới **không tích lũy thành nhiều token wakeup**.
- Lần select bị đánh thức **vẫn có thể trả số dương** nếu có key ready, nên không đồng nghĩa return value chắc chắn 0.

Sơ đồ:

~~~text
Thread A (event loop)                     Thread B (worker)
----------------------                    -----------------
selector.select()  [đang chờ]
                                          queue.add(task)
                                          selector.wakeup()
select() trở về
drain task queue
xử lý selected keys
tiếp tục event loop
~~~

### `Thread.interrupt()`

Ngắt thread đang blocked trong selection cũng là lý do làm lượt chọn trở về; việc ngắt thread có thể làm trạng thái interrupt được đặt và còn cần quy tắc dừng thích hợp.

**Đừng xử lý `select() == 0` bằng cách spin vô hạn**. Kiểm tra cờ `running`, trạng thái interrupt, queue công việc và xem selector có đang bị đóng không.

### `close()` và hủy key

`selector.close()` giải phóng tài nguyên selector; các thao tác tiếp theo trên selector đã đóng có thể ném `ClosedSelectorException`. `SelectionKey.cancel()` đánh dấu đăng ký cần hủy; việc tách khỏi selector có thể được hoàn tất trong selection tiếp theo. Khi đóng socket, cần hủy/giải phóng resource và dọn per-connection state.

Không nên đồng thời duyệt `selectedKeys()` trên nhiều thread không có đồng bộ. Selected-key set **không thread-safe**; một event loop nên có owner rõ ràng, các thread khác gửi yêu cầu qua queue rồi gọi `wakeup()`.

## 10. Những lỗi thường gặp với `OP_WRITE` và busy loop

Một `SocketChannel` thường có thể ghi được ngay trong điều kiện bình thường vì OS socket-send buffer còn chỗ. Nếu luôn đăng ký:

~~~java
// Minh họa trường hợp thường không nên làm:
socket.register(selector, SelectionKey.OP_WRITE);
~~~

selector có thể trả về rất thường xuyên ngay cả khi ứng dụng **không có dữ liệu outbound**. Khi đó event loop cứ thức dậy rồi chẳng làm gì, gây CPU cao.

**Chính sách phổ biến cho ứng dụng thực tế:**

1. Khi outbound queue rỗng, **không** giữ `OP_WRITE` trong interest set.
2. Khi có dữ liệu cần gửi, thử write ngay (nếu thiết kế cho phép).
3. Nếu write chỉ gửi một phần, giữ nguyên `ByteBuffer.position()` và bật `OP_WRITE` để đợi socket ghi được.
4. Sau khi write hết queue, tắt `OP_WRITE` để tránh readiness liên tục.

Ví dụ:

~~~java
// Mã minh họa, key là SelectionKey còn hợp lệ.
int ops = key.interestOps();
if (hasPendingOutput) {
    key.interestOps(ops | SelectionKey.OP_WRITE);
} else {
    key.interestOps(ops & ~SelectionKey.OP_WRITE);
}
~~~

`hasPendingOutput` là biến ứng dụng định nghĩa. Không cập nhật `interestOps()` khi key đã bị hủy; có thể gặp `CancelledKeyException`. Nếu thread khác đổi interest trong lúc selector đang chờ, cần sử dụng cơ chế phối hợp với event loop và `wakeup()` để thay đổi có hiệu lực đúng lúc.

## 11. Đọc/ghi non-blocking: ready không đồng nghĩa đã đủ dữ liệu

Với `SocketChannel` non-blocking:

~~~java
// Mã minh họa: socket đã được đăng ký OP_READ và buffer đã chuẩn bị.
int n = socket.read(buffer);
if (n > 0) {
    // Có n byte mới. Frame có thể vẫn chưa đầy đủ.
} else if (n == 0) {
    // Chưa nhận thêm byte trong lượt này; không spin liên tục.
} else { // n == -1
    // End-of-stream: remote peer đã đóng đầu gửi của luồng byte.
    key.cancel();
    socket.close();
}
~~~

Với `OP_ACCEPT`, `server.accept()` trên `ServerSocketChannel` non-blocking **có thể trả `null`**; cần xử lý và không cố thao tác trên channel `null`.

Với `OP_CONNECT`, khi key connectable cần gọi `socket.finishConnect()` theo đúng contract, sau đó cập nhật interest như `OP_READ` hoặc `OP_WRITE`. Không giả định socket đã hoàn thành kết nối chỉ vì `key.isConnectable()` trả true.

Trong ứng dụng truyền frame có length prefix, parser phải tích lũy đủ byte header/body qua **nhiều lượt selection**. `select()` chỉ báo readiness, không nói “một frame hoàn chỉnh đã có”.

## 12. Timeout của selector khác timeout nghiệp vụ

`select(500)` là thời gian chờ cho **một lượt selection**. Nó không tự động tạo các timeout sau:

- **Timeout kết nối:** giới hạn thời gian hoàn tất TCP connect.
- **Timeout đọc đủ một frame:** giới hạn thời gian nhận cả header và payload.
- **Idle timeout:** đóng kết nối nếu không nhận hoạt động trong một khoảng.
- **Request deadline:** hết hạn toàn bộ request gồm cả parse/compute/write.
- **Shutdown deadline:** kết thúc event loop có trật tự.

Mỗi loại cần lưu **deadline** và kiểm tra theo thời gian monotonic như `System.nanoTime()`; đồng thời lựa chọn khoảng chờ của selector bằng khoảng thời gian tới deadline gần nhất.

Ví dụ tính toán *trong ngữ cảnh đã có deadline*:

~~~java
long now = System.nanoTime();
long remainingNanos = deadlineNanos - now;

if (remainingNanos <= 0) {
    expireRequests();
} else {
    long timeoutMs =
            Math.max(1, java.util.concurrent.TimeUnit.NANOSECONDS
                    .toMillis(remainingNanos));
    selector.select(timeoutMs);
}
~~~

Đây là mã minh họa, `deadlineNanos` và `expireRequests()` là thành phần do ứng dụng cung cấp.

**Vì sao `Math.max(1, ...)`?** Nếu còn dương nhưng dưới 1 millisecond, phép chuyển sang milliseconds có thể làm tròn về `0`, và `select(0)` **sẽ chờ vô hạn** thay vì chờ rất ngắn. Cách ép tối thiểu 1 ms tránh bẫy đó, nhưng có thể trễ deadline đôi chút. Nếu độ chính xác cực cao là cần thiết, phải thiết kế scheduler/clock phù hợp thay vì coi `select(timeout)` là hard real-time timer.

### Tránh quên task khi wakeup sớm

Khi thread khác thêm task vào queue và gọi `wakeup()`, event loop cần **xử lý queue sau select**. Không suy ra `select() == 0` chỉ do timer; nguyên nhân có thể là wakeup hoặc interrupt.

## 13. Lỗi, ngoại lệ và trường hợp biên

| Tình huống | Lỗi/hành vi cần dự kiến |
|---|---|
| `select(-5)` | `IllegalArgumentException` |
| `select(0)` | Chờ vô thời hạn, **không phải** poll |
| `selectNow()` | Không chờ, có thể `0`; tiêu thụ wakeup pending |
| `select()` sau `wakeup()` | Trở về ngay, kết quả có thể 0 hoặc dương |
| `selector.close()` rồi select | `ClosedSelectorException` |
| I/O lỗi khi thực hiện selection | `IOException` |
| `key.interestOps()` trên key không còn hợp lệ | `CancelledKeyException` |
| Đăng ký channel ở blocking mode | `IllegalBlockingModeException` |
| Đăng ký operations không hợp lệ với channel | `IllegalArgumentException` |
| Duyệt và thêm trực tiếp vào `selectedKeys()` | Không cho phép thêm key trực tiếp; có thể `UnsupportedOperationException` |
| Dùng nhiều thread sửa/duyệt selectedKeys không đồng bộ | Race và sai kết quả; selected-key set không thread-safe |
| Không remove key đã xử lý | Ready bits/tập selected có thể giữ trạng thái cũ giữa các lần select |
| Non-blocking `read()` trả 0 | Không có tiến triển trong lượt này; đợi readiness phù hợp |
| Non-blocking `read()` trả -1 | End-of-stream, cần xử lý đóng hoặc half-close theo protocol |

**Không giải quyết lỗi bằng cách gọi lặp `selectNow()` vô hạn.** Một event loop tốt phải xử lý lifecycle, backpressure, task queue và thao tác I/O partial.

## 14. Pattern event loop tương đối an toàn

Phần dưới là **khung minh họa**, không phải một server hoàn chỉnh. `processTasks()`, `acceptConnections()`, `readFromConnection()` và `flushPendingOutput()` phải do ứng dụng định nghĩa, kèm xử lý lỗi/đóng socket/đăng ký key.

~~~java
while (running) {
    // Chọn timeout dương theo deadline gần nhất, hoặc dùng select().
    selector.select(250);

    processTasks(); // Xử lý các yêu cầu đã gửi vào queue từ thread khác.

    Iterator<SelectionKey> it =
            selector.selectedKeys().iterator();

    while (it.hasNext()) {
        SelectionKey key = it.next();
        it.remove();

        if (!key.isValid()) {
            continue;
        }

        try {
            if (key.isAcceptable()) {
                acceptConnections(key);
            }
            if (key.isConnectable()) {
                finishConnecting(key);
            }
            if (key.isReadable()) {
                readFromConnection(key);
            }
            if (key.isWritable()) {
                flushPendingOutput(key);
            }
        } catch (IOException e) {
            closeConnection(key);
        }
    }

    expireDeadlines();
}
~~~

Đặc điểm của khung:

- Duyệt selected keys và remove mỗi key một lần.
- Kiểm tra valid trước khi dùng operations.
- Không giả định chỉ có một readiness bit: cùng key có thể có cả READ và WRITE.
- Không dùng `else if` cho mọi trường hợp nếu cần xử lý nhiều trạng thái readiness trong cùng vòng; tuy nhiên việc một nhánh hủy key có thể đòi hỏi kiểm tra valid lại trước nhánh sau.
- Dùng `selector.wakeup()` khi có tác vụ ngoài luồng cần event loop xử lý ngay.
- Đặt cơ chế đóng channel, cancellation, timeout và backpressure rõ ràng.

Với ứng dụng nhỏ chỉ có một luồng và không có timer, `select()` có thể là lựa chọn đơn giản nhất. Với server có timeout nghiệp vụ, `select(timeout)` thường phù hợp hơn. `selectNow()` chỉ nên dùng khi thật sự cần non-blocking polling.

## 15. Bảng lỗi tư duy và cách sửa

| Nhầm lẫn | Cách hiểu đúng |
|---|---|
| “`select(0)` giống `selectNow()`” | `select(0)` **chờ vô thời hạn**; `selectNow()` không chờ. |
| “`select(100)` luôn chờ đúng 100 ms” | Có thể trả sớm do readiness/wakeup/interrupt; timer không hard real-time. |
| “Giá trị trả về là số byte đọc được” | Đó là số key có ready-operation set được cập nhật, không phải bytes. |
| “`select() == 0` chắc chắn không có việc gì” | Có thể do wakeup/interrupt, queue task hoặc key cũ chưa consume. |
| “`selectedKeys()` tự clear khi gọi `select()` lần mới” | Không; ứng dụng phải remove các selected keys đã xử lý. |
| “`isReadable()` nghĩa là đủ nguyên một message” | Chỉ readiness; phải đọc/parse dữ liệu thực tế, xử lý partial. |
| “FileChannel có thể register với Selector” | FileChannel không phải SelectableChannel. |
| “Chỉ cần `register`, không cần non-blocking” | Channel phải `configureBlocking(false)` trước khi đăng ký. |
| “`selectNow()` luôn tiết kiệm CPU vì không chờ” | Poll dày đặc có thể tạo busy-spin. |
| “Luôn bật OP_WRITE cho mọi socket” | Thường khiến selector wake liên tục; bật khi thực sự có pending write. |
| “`wakeup()` luôn khiến select trả 0” | Nếu có readiness, select vẫn có thể trả số dương. |
| “`wakeup()` là một hàng đợi token tích lũy” | Nhiều lần wakeup trước một selection thường chỉ tương đương một lần. |
| “Timeout selector chính là timeout request” | Cần deadline riêng cho request/protocol, không chỉ chờ I/O. |

## 16. Tự kiểm tra

1. Có `OP_READ` đăng ký nhưng chưa có byte; chọn API nào để trở về ngay? **`selectNow()`**.
2. Gọi `select(0)` thì tối đa chờ bao lâu? **Không giới hạn theo timeout**, tới khi có readiness/wakeup/interrupt hoặc vòng đời khác.
3. Gọi `select(200)` và sau 30 ms có data. Có bắt buộc chờ đủ 200 ms? **Không.** Có thể trở về khi có readiness.
4. `select(200)` trả 0. Có chắc reader không còn dữ liệu có thể xử lý? **Không nên suy luận như vậy**; kiểm tra selected keys, task queue và trạng thái hiện tại.
5. Vì sao phải `iterator.remove()` trong vòng xử lý selected keys? **Tránh giữ key cũ trong selected set cho lượt chọn kế tiếp**.
6. Một socket luôn được đánh dấu writable dù không có byte để gửi; điều chỉnh gì? **Tắt interest `OP_WRITE` khi outbound queue rỗng**.
7. Cần đánh thức một thread đang blocked trong `select()` khi worker thread thêm task. Dùng gì? **`selector.wakeup()`**.
8. `selectNow()` có tác động gì tới `wakeup()` được gọi trước đó? **Nó xóa tác dụng wakeup đang chờ**.
9. Đã `select()` và `key.isReadable()==true`. `read()` có chắc trả đủ nguyên frame không? **Không**.
10. `select(-1)` có hợp lệ không? **Không, `IllegalArgumentException`**.
11. Có thể dùng `FileChannel` với Selector không? **Không**, nó không phải `SelectableChannel`.
12. Có deadline còn 500 microsecond; truyền `select(TimeUnit.NANOSECONDS.toMillis(500_000))` có đúng không? **Không**, nó thành `select(0)` và chờ vô hạn. Phải xử lý thời gian dưới 1 ms cẩn thận.

## 17. Tài liệu chính thức

- [Java SE 17 — Selector](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/Selector.html): `select()`, `selectNow()`, `select(long)`, `selectedKeys()`, `wakeup()`, cách cập nhật ready-operation set và caveat timeout.
- [Java SE 17 — SelectionKey](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/SelectionKey.html): `interestOps`, `readyOps`, `isReadable`, `isWritable`, key cancellation.
- [Java SE 17 — SelectableChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/SelectableChannel.html): `configureBlocking(false)`, `register`, `validOps`.
- [Java SE 17 — Pipe](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/Pipe.html): source/sink channel dùng trong ví dụ có thể chạy không cần server mạng.
- [Java SE 17 — SocketChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/SocketChannel.html): partial read/write, non-blocking, `finishConnect()`.
- [Java SE 17 — ServerSocketChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/ServerSocketChannel.html): đăng ký `OP_ACCEPT`, `accept()` trên non-blocking server.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="selector-006">Lặp selectedKeys và loại bỏ key đã xử lý</a>

<details>
<summary>Xem chi tiết</summary>

# Lặp `selectedKeys` và loại bỏ key đã xử lý trong Java NIO Selector

## 1. Vì sao phải xử lý và loại bỏ từng SelectionKey?

Khi lập trình mạng non-blocking bằng Java NIO, ứng dụng thường dùng **một vòng lặp sự kiện** (*event loop*) để quản lý nhiều socket qua `Selector`. Mỗi lần gọi `selector.select()`, hệ thống xác định các channel đã đăng ký có thao tác I/O nào đang **sẵn sàng** (*ready*). Những đăng ký đó được biểu diễn bằng `SelectionKey`.

Sau một lần chọn, chương trình lấy tập:

```java
Set<SelectionKey> selected = selector.selectedKeys();
```

**Đây không phải một hàng đợi tự tiêu thụ.** Tập `selectedKeys()` giữ những key đã được chọn cho đến khi ứng dụng chủ động loại chúng khỏi tập (hoặc khi key bị hủy, theo quy trình xử lý hủy đăng ký của selector). Nếu bạn lặp xử lý nhưng **không loại bỏ key**, nó có thể vẫn xuất hiện trong tập đã chọn ở các vòng sau. Kết quả dễ gặp là xử lý trùng trạng thái sẵn sàng cũ, log lặp, truy cập lại buffer sai pha và event loop khó kiểm soát.

Mẫu chuẩn:

```java
Iterator<SelectionKey> iterator =
        selector.selectedKeys().iterator();

while (iterator.hasNext()) {
    SelectionKey key = iterator.next();
    iterator.remove(); // Loại khỏi selected-key set.

    if (!key.isValid()) {
        continue;
    }

    // Xử lý sự kiện readiness được báo cho key này.
}
```

Điều quan trọng nhất: **`iterator.remove()` chỉ loại key ra khỏi tập đang được chọn**, chứ không mặc định đóng socket hay hủy đăng ký `SelectionKey`. Key có thể xuất hiện trở lại ở những lần `select()` tiếp theo nếu channel vẫn đăng ký, quan tâm đến sự kiện phù hợp và sẵn sàng.

## 2. Ba tập và ba trạng thái cần phân biệt

Trong một `Selector`, việc hiểu đúng ba khái niệm sau giúp tránh bug vòng lặp:

| Khái niệm | API | Ý nghĩa |
| --- | --- | --- |
| **Registered-key set** | `selector.keys()` | Các key hiện đang đăng ký với selector; không phải tập do ứng dụng tự xóa trực tiếp |
| **Selected-key set** | `selector.selectedKeys()` | Các key mà một lượt chọn đã xác định là sẵn sàng theo những interest phù hợp |
| **Canceled-key set** | Nội bộ của `Selector` | Các key đã được hủy và chờ selector xử lý dọn đăng ký |

Hai thao tác rất khác nhau:

- **`iterator.remove()`** trên iterator của `selectedKeys()`: lấy một key ra khỏi **selected-key set** sau khi đã nhận công việc từ lượt chọn. Đăng ký vẫn sống nếu key chưa bị hủy.
- **`key.cancel()`**: đánh dấu key **không còn hợp lệ**, yêu cầu hủy đăng ký với selector. Việc loại nó khỏi tập đăng ký được selector xử lý tại thời điểm thích hợp, thường trong một thao tác lựa chọn tiếp theo.

`key.cancel()` **không đóng channel**. Muốn kết thúc kết nối, ứng dụng thường phải gọi `channel.close()` và thu dọn state của kết nối. Đóng channel cũng khiến những key liên quan bị hủy, nhưng vẫn không đồng nghĩa bạn có thể bỏ qua quy tắc dọn vòng lặp và quản lý tài nguyên.

### Ví dụ tình huống

Một socket đang đăng ký `OP_READ` và có byte chờ đọc:

1. `selector.select()` nhận biết socket có thể đọc.
2. Key của socket nằm trong `selectedKeys()`.
3. Vòng lặp lấy key và gọi `iterator.remove()`.
4. Chương trình gọi `channel.read(buffer)`.
5. Nếu kết nối vẫn mở, key vẫn thuộc các đăng ký hợp lệ; khi điều kiện đọc xuất hiện ở một lượt chọn mới, key có thể được chọn **một lần nữa**.

**Không nên `cancel()` mọi key sau mỗi lần đọc**, bởi như vậy ứng dụng sẽ mất đăng ký và phải xây lại cơ chế theo dõi nếu muốn tiếp tục nhận dữ liệu.

## 3. Quy trình chuẩn: select → iterate → remove → dispatch

Luồng điều khiển thường dùng:

```text
                    +---------------------+
                    | selector.select()   |
                    +----------+----------+
                               |
                               v
                    +---------------------+
                    | selectedKeys()      |
                    +----------+----------+
                               |
                               v
                    +---------------------+
                    | iterator.hasNext()? |
                    +----------+----------+
                               |
                       yes     |     no
                        +------+------> Quay lại select()
                        |
                        v
               key = iterator.next()
                        |
                        v
                iterator.remove()
                        |
                        v
                 key.isValid()?
                  /           \
                no            yes
                |              |
         bỏ qua key      xử lý readyOps
                |              |
                +-------+------+
                        |
                        v
                  key tiếp theo
```

Các bước đáng ghi nhớ:

1. `select()` có thể trả về 0 (ví dụ do wakeup hoặc timeout). Không phải mỗi lần thức dậy đều có I/O để xử lý.
2. `selectedKeys()` chứa **tập** kết quả; không bảo đảm thứ tự các key.
3. `iterator.next()` chỉ lấy phần tử hiện tại trong tập.
4. `iterator.remove()` xóa **đúng phần tử vừa trả về bởi iterator**, an toàn khi chỉnh sửa tập trong lúc đang duyệt bằng iterator.
5. `key.isValid()` kiểm tra key có còn hợp lệ trước khi truy cập readiness hoặc cập nhật interest.
6. Xử lý các cờ phù hợp `isAcceptable()`, `isConnectable()`, `isReadable()`, `isWritable()`.
7. Thoát vòng iterator và quay lại `select()` khi ứng dụng chưa dừng.

Trong event loop thực, cần thêm xử lý ngoại lệ, đóng kết nối, giới hạn công việc mỗi lượt, hàng đợi ghi còn dở, timeout và cách thức dừng selector.

## 4. Vì sao dùng `Iterator.remove()` thay vì xóa tùy tiện?

Đoạn code sau **không nên dùng**:

```java
for (SelectionKey key : selector.selectedKeys()) {
    selector.selectedKeys().remove(key); // Sai cách trong khi for-each.
    // Xử lý key...
}
```

Vòng `for-each` thực chất duyệt bằng iterator. Thay đổi cấu trúc tập từ một đường khác khi iterator còn hoạt động có thể gây `ConcurrentModificationException` hoặc phụ thuộc cách triển khai. Hãy dùng iterator của **chính tập đó** và gọi `iterator.remove()`.

Cách đúng:

```java
Iterator<SelectionKey> it =
        selector.selectedKeys().iterator();

while (it.hasNext()) {
    SelectionKey key = it.next();
    it.remove();

    if (!key.isValid()) {
        continue;
    }

    // Dispatch theo trạng thái ready của key.
}
```

**`iterator.remove()` phải theo sau một lần `next()` hợp lệ** và không gọi hai lần liên tiếp trên cùng phần tử. Gọi `remove()` trước `next()` hoặc lần thứ hai mà chưa có `next()` mới sẽ ném `IllegalStateException` theo hợp đồng Iterator.

Đặt `remove()` **ngay sau `next()`** giúp bảo đảm key được lấy ra khỏi tập đã chọn kể cả khi handler phía sau cần bỏ qua hoặc báo lỗi. Đây là một lựa chọn quản lý vòng đời event rõ ràng, không có nghĩa bản thân `remove()` đã xử lý xong byte ở socket.

### Có thể dùng `selectedKeys().clear()` không?

`selector.selectedKeys().clear()` là thao tác loại **toàn bộ** key khỏi selected set. Về mặt API, tập này cho phép remove/clear; nhưng xóa toàn bộ quá sớm sẽ bỏ các key **chưa được xử lý** trong lượt đang có. Do đó `clear()` chỉ phù hợp khi ứng dụng **cố ý bỏ qua toàn bộ selected events** hoặc đã có cơ chế snapshot/dispatch bảo đảm không mất công việc cần làm.

Không dùng `clear()` trước khi duyệt một lượt thông báo readiness với kỳ vọng vẫn xử lý đầy đủ các sự kiện vừa nhận. Mẫu `iterator.remove()` từng key rõ ràng và dễ kiểm soát hơn.

## 5. `remove()`, `cancel()`, `close()` và `interestOps(0)`

| Thao tác | Loại key khỏi selected set? | Đăng ký key còn hợp lệ? | Channel còn mở? |
| --- | --- | --- | --- |
| `iterator.remove()` | Có, với key vừa lấy | **Thường vẫn hợp lệ** nếu chưa bị hủy | Không đổi |
| `key.cancel()` | Không phải cơ chế “tiêu thụ selected event”; selector sẽ xử lý việc hủy | **Không**, key trở nên invalid | Không tự đóng |
| `key.channel().close()` | Gây hủy những đăng ký liên quan, selector dọn theo vòng đời | Không còn hợp lệ | **Không** |
| `key.interestOps(0)` | Không tự xóa key đang nằm trong selected set | Vẫn có thể hợp lệ | Không đổi |

**Hai phạm vi khác nhau:**

- **Consumed readiness**: đã lấy thông tin key ra để xử lý cho lượt select hiện tại; dùng `iterator.remove()`.
- **Lifecycle/cancellation**: không muốn selector theo dõi channel nữa; dùng `key.cancel()` và quyết định có cần `channel.close()` hay không.

Khi peer đóng chiều gửi và `read()` trả về `-1`, server thường hủy key và đóng socket (nếu protocol không cần giữ nửa kết nối). Khi chỉ đọc được vài byte của một thông điệp, **không** hủy key; giữ state để đợi các byte tiếp theo.

`interestOps(0)` có thể tạm dừng theo dõi readiness mà không hủy đăng ký. Nhưng nếu key đã được đưa vào selected-key set, bạn vẫn cần tiêu thụ/xóa entry của lượt chọn cũ; đổi interest không phải là một phép dọn selected-key set.

## 6. `interestOps` và `readyOps` không phải một

`SelectionKey` có hai tập bit quan trọng:

- **`interestOps()`**: những loại I/O mà ứng dụng muốn theo dõi, ví dụ `OP_READ` hoặc `OP_READ | OP_WRITE`.
- **`readyOps()`**: những loại I/O mà lượt chọn đã ghi nhận là đang sẵn sàng cho key.

Các phương thức tiện ích `isReadable()`, `isWritable()`, `isAcceptable()`, `isConnectable()` phản ánh những bit ready tương ứng; chúng **không kiểm tra interestOps**.

| Bit | Kênh điển hình | Cách xử lý |
| --- | --- | --- |
| `SelectionKey.OP_ACCEPT` | `ServerSocketChannel` | Gọi `accept()` và kiểm tra kết quả có thể là `null` |
| `SelectionKey.OP_CONNECT` | `SocketChannel` đang connect | Gọi `finishConnect()`, xử lý lỗi/kết nối còn dở |
| `SelectionKey.OP_READ` | `SocketChannel` đã kết nối | Gọi `read()`, kiểm tra `>0`, `0`, `-1` |
| `SelectionKey.OP_WRITE` | `SocketChannel` có dữ liệu chờ gửi | Thử `write()` phần pending, điều chỉnh interest khi gửi hết |

**Một key có thể có nhiều bit ready cùng lúc.** Vì vậy dùng chuỗi `if` độc lập khi các hoạt động phù hợp cùng một channel cần được xử lý trong một lượt, thay vì mặc định `else if` sẽ bao quát mọi trường hợp:

```java
if (key.isConnectable()) {
    // finishConnect() nếu kết nối đang chờ.
}
if (key.isValid() && key.isReadable()) {
    // read, giữ parser state.
}
if (key.isValid() && key.isWritable()) {
    // write phần pending.
}
```

Đây là đoạn minh họa; trong ứng dụng thực phải kiểm tra state kết nối, buffer pending và loại channel cụ thể. Với `ServerSocketChannel`, chỉ `OP_ACCEPT` là lựa chọn hợp lệ; không gọi `isReadable()` để kỳ vọng chấp nhận kết nối.

**Điểm tinh tế:** Theo hợp đồng `Selector`, key đã nằm trong selected set có thể được cập nhật tập ready operations ở các lần selection tiếp theo, thay vì được tự loại bỏ. Do đó **không xóa key** làm bạn tiếp tục quan sát key cũ và có thể pha trộn trạng thái sẵn sàng giữa các lượt. `iterator.remove()` giúp phân định rõ từng lượt xử lý.

## 7. Ví dụ Java 17+ số 1: Pipe, selected key và registered key

Để quan sát trực tiếp `selectedKeys()` mà không cần mạng bên ngoài, ta dùng `java.nio.channels.Pipe`. `Pipe.SourceChannel` có thể đăng ký `Selector` với `OP_READ`. `Pipe.SinkChannel` ghi dữ liệu vào pipe, làm source sẵn sàng đọc.

Ví dụ này ghi đúng hai byte ASCII `OK`, nhận qua selector, loại bỏ key đã chọn, rồi kiểm tra:

- selected set đã rỗng hay chưa;
- key có còn đăng ký với selector hay không.

**Tệp `SelectedKeysPipeDemo.java`:**

```java
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

public class SelectedKeysPipeDemo {
    public static void main(String[] args) throws IOException {
        Pipe pipe = Pipe.open();

        try (Pipe.SourceChannel source = pipe.source();
             Pipe.SinkChannel sink = pipe.sink();
             Selector selector = Selector.open()) {

            source.configureBlocking(false);
            SelectionKey registered =
                    source.register(selector, SelectionKey.OP_READ);

            ByteBuffer outgoing = ByteBuffer.wrap(
                    "OK".getBytes(StandardCharsets.US_ASCII));
            while (outgoing.hasRemaining()) {
                sink.write(outgoing);
            }

            ByteBuffer received = ByteBuffer.allocate(2);
            long deadline = System.nanoTime()
                    + TimeUnit.SECONDS.toNanos(5);

            while (received.hasRemaining()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new IOException("Selection timeout");
                }

                long timeoutMs = Math.max(1L,
                        TimeUnit.NANOSECONDS.toMillis(remaining));
                selector.select(timeoutMs);

                Iterator<SelectionKey> iterator =
                        selector.selectedKeys().iterator();

                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();

                    if (!key.isValid()) {
                        continue;
                    }
                    if (key.isReadable()) {
                        int n = source.read(received);
                        if (n == -1) {
                            throw new EOFException("Pipe closed early");
                        }
                    }
                }
            }

            received.flip();
            String message = StandardCharsets.US_ASCII
                    .decode(received).toString();

            System.out.println("Nhan duoc: " + message);
            System.out.println("Selected set rong: "
                    + selector.selectedKeys().isEmpty());
            System.out.println("Key van dang ky: "
                    + selector.keys().contains(registered));
            System.out.println("Key con hop le: "
                    + registered.isValid());
        }
    }
}
```

Biên dịch/chạy bằng JDK 17+:

```bash
javac SelectedKeysPipeDemo.java
java SelectedKeysPipeDemo
```

**Stdout khi các thao tác I/O và lựa chọn hoàn tất:**

```text
Nhan duoc: OK
Selected set rong: true
Key van dang ky: true
Key con hop le: true
```

### Giải thích kết quả

1. `source.configureBlocking(false)` là điều kiện để đăng ký vào selector.
2. `sink` ghi hai byte, nhờ đó `source` trở nên sẵn sàng đọc.
3. `select(timeoutMs)` chọn key của source khi có readiness.
4. `iterator.remove()` chỉ xóa phần tử đang duyệt khỏi **selected-key set**, không hủy key.
5. `source.read(received)` nhận số byte có sẵn. Vòng ngoài tiếp tục nếu chưa đủ hai byte; vì vậy stdout không phụ thuộc dữ liệu về trong một hay nhiều lần chọn.
6. `selector.selectedKeys().isEmpty() == true` sau khi mọi key đã được lấy ra.
7. `selector.keys().contains(registered) == true` và `registered.isValid() == true` cho thấy đăng ký vẫn còn sống.

Các dòng stdout không in số lượt `select()`, vì số lần event loop thức dậy có thể khác nhau theo lịch của hệ điều hành. Chương trình chỉ in trạng thái cuối trong điều kiện thành công.

**Lưu ý:** khi source/sink/selector rời `try-with-resources`, tài nguyên được đóng. Việc key còn hợp lệ ở thời điểm in **không có nghĩa nó sẽ tiếp tục hợp lệ sau khi channel hoặc selector đóng**.

## 8. Ví dụ Java 17+ số 2: TCP server chọn `OP_ACCEPT` và `OP_READ`

Ví dụ thứ hai thể hiện vòng lặp gần với ứng dụng server:

- Mở `ServerSocketChannel` trên loopback và cổng hệ điều hành tự cấp.
- Đặt server ở non-blocking mode và đăng ký `OP_ACCEPT`.
- Dùng client kết nối gửi bốn byte ASCII `PING`.
- Khi selector báo sẵn sàng chấp nhận, server tạo `SocketChannel` non-blocking và đăng ký `OP_READ`.
- Event loop đọc đến khi nhận đủ bốn byte, luôn dùng `iterator.remove()` cho từng key đã lấy.
- Sau khi hoàn tất thông điệp, server **hủy key và đóng client được chấp nhận** vì phiên làm việc đã hoàn thành.

**Tệp `SelectedKeysTcpDemo.java`:**

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

public class SelectedKeysTcpDemo {
    public static void main(String[] args) throws IOException {
        try (Selector selector = Selector.open();
             ServerSocketChannel server = ServerSocketChannel.open()) {

            server.bind(new InetSocketAddress(
                    InetAddress.getLoopbackAddress(), 0));
            server.configureBlocking(false);
            server.register(selector, SelectionKey.OP_ACCEPT);

            try (SocketChannel sender = SocketChannel.open()) {
                sender.connect(server.getLocalAddress());
                ByteBuffer outgoing = ByteBuffer.wrap(
                        "PING".getBytes(StandardCharsets.US_ASCII));
                while (outgoing.hasRemaining()) {
                    sender.write(outgoing);
                }

                String result = null;
                long deadline = System.nanoTime()
                        + TimeUnit.SECONDS.toNanos(5);

                while (result == null) {
                    long nanos = deadline - System.nanoTime();
                    if (nanos <= 0) {
                        throw new IOException("TCP selection timeout");
                    }

                    long timeoutMs = Math.max(1L,
                            TimeUnit.NANOSECONDS.toMillis(nanos));
                    selector.select(timeoutMs);

                    Iterator<SelectionKey> iterator =
                            selector.selectedKeys().iterator();

                    while (iterator.hasNext()) {
                        SelectionKey key = iterator.next();
                        iterator.remove();

                        if (!key.isValid()) {
                            continue;
                        }

                        if (key.isAcceptable()) {
                            SocketChannel peer = server.accept();
                            if (peer != null) {
                                peer.configureBlocking(false);
                                peer.register(
                                        selector,
                                        SelectionKey.OP_READ,
                                        ByteBuffer.allocate(4));
                            }
                        } else if (key.isReadable()) {
                            SocketChannel peer =
                                    (SocketChannel) key.channel();
                            ByteBuffer buffer =
                                    (ByteBuffer) key.attachment();

                            int n = peer.read(buffer);
                            if (n == -1 && buffer.hasRemaining()) {
                                throw new EOFException(
                                        "Client closed before PING");
                            }

                            if (!buffer.hasRemaining()) {
                                buffer.flip();
                                result = StandardCharsets.US_ASCII
                                        .decode(buffer).toString();

                                key.cancel();
                                peer.close();
                            }
                        }
                    }
                }

                System.out.println("Nhan duoc: " + result);
                System.out.println("Selected set rong: "
                        + selector.selectedKeys().isEmpty());
                System.out.println("Server con mo: " + server.isOpen());
            }
        }
    }
}
```

Chạy:

```bash
javac SelectedKeysTcpDemo.java
java SelectedKeysTcpDemo
```

**Stdout khi kết nối loopback và I/O thành công:**

```text
Nhan duoc: PING
Selected set rong: true
Server con mo: true
```

### Điều gì được chứng minh?

- Trong cùng một vòng đời selector, key của server có thể sẵn sàng **accept**, còn key của peer có thể sẵn sàng **read**.
- Mỗi key đã lấy được **loại khỏi selected set ngay**; điều này độc lập với việc có hủy key hay không.
- Key của server tiếp tục sống để có thể chấp nhận các kết nối sau; key của peer đã xử lý đủ một thông điệp được `cancel()` và socket được đóng.
- `ByteBuffer` được gắn bằng `register(selector, OP_READ, attachment)` và đọc dần đến khi đủ bốn byte. Không mặc định một lần `read()` trả đủ bốn byte.

Trong ví dụ này, `else if` phù hợp vì `OP_ACCEPT` và `OP_READ` được đăng ký cho **hai loại channel khác nhau** và mỗi key chỉ phục vụ một nhánh. Với `SocketChannel` có cả `OP_READ` và `OP_WRITE`, cần kiểm tra độc lập những bit ready cần xử lý, kèm kiểm tra lại `key.isValid()` nếu handler trước có thể đóng socket.

**Phạm vi ví dụ:** server chỉ nhận một thông điệp cố định bốn byte từ một client loopback. Ứng dụng thật cần framing cho TCP stream, giới hạn buffer, hàng đợi gửi, timeout riêng cho từng kết nối, cleanup khi lỗi và cơ chế shutdown event loop.

## 9. Readiness không đồng nghĩa đã xử lý xong dữ liệu

Giả sử `selector.select()` báo một `SocketChannel` sẵn sàng đọc:

```java
int count = socket.read(inputBuffer);
```

Có thể gặp:

| Kết quả | Diễn giải | Cách xử lý |
| --- | --- | --- |
| `count > 0` | Đã nhận đúng `count` byte | Cập nhật parser/buffer state; giữ đăng ký nếu cần thêm |
| `count == 0` | Lần đọc này chưa có tiến độ | Không spin; quay lại selector hoặc điều chỉnh interest |
| `count == -1` | EOF phía nhận | Kết thúc hoặc xử lý half-close theo giao thức |
| Ném `IOException` | Lỗi I/O | Cleanup key/channel/buffer theo chính sách |

Ngay cả khi key được chọn là readable, `read` không cam kết lấp đầy buffer hoặc trả dữ liệu nguyên thông điệp. Ready là trạng thái *có thể thực hiện I/O*, không phải một bản sao dữ liệu đợi sẵn để ứng dụng tiêu thụ tùy thích.

Vì vậy:

- **`iterator.remove()`** nghĩa là “tôi đã lấy thông tin readiness của lượt này ra khỏi tập”, **không có nghĩa** “tôi đã nhận hết dữ liệu của socket”.
- **`key.cancel()`** nghĩa là “tôi không cần đăng ký này nữa”, không dùng thay cho xử lý phần còn dở.
- **`inputBuffer.compact()`** giữ byte chưa được parser xử lý để nhận thêm, khác hoàn toàn với thao tác loại key khỏi selected set.

Tách bạch ba cấp — *event*, *đăng ký*, *nội dung byte* — là nguyên tắc quan trọng khi viết event loop.

## 10. Đặt `remove()` trước hay sau phần xử lý?

Có hai cách có thể thấy trong mã nguồn:

```java
SelectionKey key = iterator.next();
iterator.remove();
process(key);
```

và

```java
SelectionKey key = iterator.next();
process(key);
iterator.remove();
```

Chúng đều dựa trên cùng API iterator nếu `process` không làm thay đổi chính tập đang duyệt. Tuy nhiên **đặt `remove()` ngay sau `next()` là lựa chọn bền vững hơn** khi handler có thể ném lỗi, đóng channel hoặc cần `continue` sang key khác.

Nếu mã đặt `remove` sau handler mà handler ném ngoại lệ trước khi đến lệnh này, key vẫn nằm trong selected-key set. Tùy cách vòng lặp phục hồi, key ấy có thể bị xử lý lại một cách không có chủ ý.

Nếu vì yêu cầu đặc biệt cần hoãn remove, có thể dùng cấu trúc `try/finally` cẩn thận, nhưng phải tránh gọi `remove()` sai trạng thái và phải hiểu hành vi khi iterator bị thay đổi. Với phần lớn event loop một luồng, cách đơn giản nhất vẫn là **lấy key, remove ngay, rồi dispatch**.

## 11. Xử lý ngoại lệ theo từng key để tránh làm hỏng cả vòng lặp

Trong ứng dụng production, một client hỏng không nên mặc định làm toàn bộ selector dừng phục vụ các client còn lại. Một mẫu xử lý:

```java
Iterator<SelectionKey> it =
        selector.selectedKeys().iterator();

while (it.hasNext()) {
    SelectionKey key = it.next();
    it.remove();

    if (!key.isValid()) {
        continue;
    }

    try {
        handleReadyKey(key);
    } catch (IOException ex) {
        key.cancel();

        try {
            key.channel().close();
        } catch (IOException closeError) {
            ex.addSuppressed(closeError);
        }

        // Ghi log/metric có ngữ cảnh, tiếp tục key khác
        // nếu chính sách ứng dụng cho phép.
    }
}
```

Đây là **đoạn mã minh họa**: `selector` và `handleReadyKey` chưa được khai báo nên không phải chương trình biên dịch độc lập. Cần xét những ngoại lệ unchecked như `CancelledKeyException`, `IllegalArgumentException` hoặc lỗi lập trình có thể xuất hiện; không nên vô điều kiện nuốt tất cả `RuntimeException` và tiếp tục như không có gì xảy ra.

Chỉ gọi `key.cancel()` khi thực sự muốn ngừng theo dõi đăng ký; với lỗi I/O của peer dẫn đến đóng socket, đó thường là lựa chọn phù hợp. Bất cứ state nào đi kèm key (attachment) cũng cần được giải phóng hoặc xử lý tồn đọng khi kết thúc kết nối.

**Quy tắc tối thiểu:** tách lỗi của từng kết nối khỏi lỗi cấp selector; sau lỗi nghiêm trọng ở selector, phải có chiến lược tái tạo hoặc dừng event loop.

## 12. `selectedKeys()` khác `keys()`

Hai tập thường bị nhầm vì đều chứa `SelectionKey`:

```java
Set<SelectionKey> allRegistered = selector.keys();
Set<SelectionKey> readySelected = selector.selectedKeys();
```

- `keys()`: tập các đăng ký thuộc selector. Tập trả về không cho phép ứng dụng trực tiếp thêm/xóa key; có thể ném `UnsupportedOperationException` nếu cố sửa.
- `selectedKeys()`: tập các key được chọn và **cho phép remove/clear**; ứng dụng không thể tùy ý thêm key vào đó.

Khi `iterator.remove()` từ `selectedKeys()`, `keys()` vẫn có thể chứa key đó. Đây chính là kết quả của `SelectedKeysPipeDemo`.

Cũng cần phân biệt giữa key **được chọn** và key **còn hợp lệ**: một key đã bị `cancel()` có thể đang chờ selector xử lý dọn đăng ký. Vì thế `key.isValid()` cần được kiểm tra trước khi truy cập ready operations, và không nên tự suy ra trạng thái chỉ từ số lượng phần tử trong một tập.

## 13. Không dùng `cancel()` để “dọn sạch sự kiện đã chọn”

Đây là lỗi thiết kế dễ xảy ra:

```java
Iterator<SelectionKey> it = selector.selectedKeys().iterator();

while (it.hasNext()) {
    SelectionKey key = it.next();
    it.remove();
    key.cancel(); // Sai nếu vẫn muốn tiếp tục theo dõi channel.

    // Lần chọn sau không còn đăng ký này để xử lý I/O tiếp.
}
```

Lệnh `key.cancel()` **không phải** bước bắt buộc sau mỗi `read` hay `write`. Nó dùng khi ngừng theo dõi một channel hoặc muốn đăng ký lại theo chiến lược khác.

Ví dụ một TCP client gửi message 100 byte nhưng lần đầu server chỉ nhận 20 byte:

- Lấy key từ selected set và gọi `iterator.remove()`.
- Đọc 20 byte, cập nhật số byte còn cần.
- **Giữ key hợp lệ và quan tâm `OP_READ`** để nhận 80 byte còn lại khi có readiness.
- Không `cancel()` và đóng socket chỉ vì `read` đầu tiên chưa đủ 100 byte.

Khi kết thúc kết nối hoặc có lỗi không thể phục hồi mới hủy key, đóng channel, dọn attachment/buffer. Lưu ý việc đọc 20 byte không chứng minh message đã hoàn chỉnh: TCP không duy trì ranh giới record ứng dụng; parser phải quản lý framing.

## 14. `OP_WRITE`: lý do loại key không đủ để tránh vòng quay CPU

Một socket TCP thường xuyên có thể ghi vì phía kernel vẫn còn buffer gửi. Nếu chương trình đăng ký `OP_WRITE` ngay cả lúc **không có dữ liệu cần gửi**, selector có thể liên tục báo key writable ở các lần chọn. Kể cả đã gọi `iterator.remove()` đúng cách, vòng lặp vẫn có thể thức dậy liên tục theo một readiness **đang thật sự tồn tại**.

Cách đúng là quản lý interest:

1. Chỉ bật `OP_WRITE` khi có outbound data còn chờ.
2. Khi gửi được một phần, giữ `ByteBuffer.position()` và duy trì `OP_WRITE`.
3. Khi đã gửi hết queue, **tắt `OP_WRITE`** khỏi `interestOps`.
4. Nếu có dữ liệu mới được enqueue, bật `OP_WRITE` trở lại và đánh thức selector nếu cần.

Ví dụ tắt write interest (minh họa):

```java
int interests = key.interestOps();
key.interestOps(interests & ~SelectionKey.OP_WRITE);
```

Mã cần xác thực key hợp lệ, trạng thái gửi và đồng bộ khi nhiều thread tương tác. Đừng xóa luôn `OP_READ` nếu vẫn cần nhận request. Thao tác thay `interestOps` là thay loại sự kiện quan tâm trong tương lai, **không thay thế** việc `iterator.remove()` key đã chọn hiện tại.

## 15. `select()`, `selectNow()`, timeout và vòng lặp chính

Ba kiểu lựa chọn thường gặp:

| API | Hành vi |
| --- | --- |
| `selector.select()` | Có thể chờ cho đến khi có key sẵn sàng, wakeup, ngắt hoặc điều kiện kết thúc tương ứng |
| `selector.select(timeoutMs)` với timeout dương | Chờ tối đa một khoảng thời gian tính bằng ms nếu chưa có readiness; trả số lượng key được cập nhật theo hợp đồng |
| `selector.selectNow()` | Thực hiện thao tác chọn không chặn và trả về ngay |

**Lưu ý:** `select(0)` không mang nghĩa “poll ngay” trong Java Selector; timeout 0 có ý nghĩa chờ vô hạn. Muốn không chặn, dùng `selectNow()`.

Việc `select` trả về một số dương không có nghĩa chỉ cần xử lý đúng bấy nhiêu phần tử bằng cách lấy từ vị trí chỉ số 0: `selectedKeys()` là **Set**, không phải array được sắp thứ tự. Luôn duyệt tập thực tế.

Một mẫu event loop:

```java
while (running) {
    selector.select();

    Iterator<SelectionKey> it =
            selector.selectedKeys().iterator();

    while (it.hasNext()) {
        SelectionKey key = it.next();
        it.remove();

        if (!key.isValid()) {
            continue;
        }

        // Phân phối cho handler phù hợp.
    }
}
```

Đây là minh họa vì `running` và `selector` chưa được khai báo. Trong ứng dụng thực, cần xử lý shutdown, `wakeup()` khi thay trạng thái từ thread khác, timeout, hủy đăng ký và đóng tài nguyên.

### Vì sao không phụ thuộc thứ tự selected keys?

`Set<SelectionKey>` không cam kết ưu tiên accept trước read, hoặc socket A trước socket B. Handler phải thiết kế độc lập với thứ tự, và một số key được xử lý trong lượt sau nếu readiness xuất hiện sau lần chọn hiện tại. Nếu ứng dụng cần công bằng (*fairness*) giữa các kết nối, cần chính sách số byte/số tác vụ tối đa mỗi lượt, chứ không dựa vào thứ tự duyệt tự nhiên của `selectedKeys()`.

## 16. Cấu hình channel và đăng ký key đúng cách

Channel cần ở **non-blocking mode** trước khi đăng ký selector:

```java
ServerSocketChannel server = ServerSocketChannel.open();
server.configureBlocking(false);
server.register(selector, SelectionKey.OP_ACCEPT);
```

Đây là đoạn minh họa chưa hoàn chỉnh vì cần mở/bind server, khai báo selector và đóng tài nguyên.

`SocketChannel` đã được chấp nhận từ server cần được cấu hình theo mô hình event loop:

```java
SocketChannel accepted = server.accept();
if (accepted != null) {
    accepted.configureBlocking(false);
    accepted.register(
        selector, SelectionKey.OP_READ, ByteBuffer.allocate(1024));
}
```

Một channel đang có đăng ký selector hợp lệ không thể bị chuyển thẳng về blocking mode: `configureBlocking(true)` có thể ném `IllegalBlockingModeException`. Nếu định chuyển mode, phải hủy đăng ký và để selector hoàn tất việc loại key phù hợp, không đơn giản chỉ `iterator.remove()`.

**`iterator.remove()` không ảnh hưởng blocking mode**. Sau khi bạn lấy key khỏi selected set, channel vẫn ở non-blocking mode và key vẫn có thể được đăng ký.

## 17. Các lỗi thường gặp và cách sửa

| Sai lầm | Vì sao sai | Cách xử lý |
| --- | --- | --- |
| Duyệt `selectedKeys()` nhưng không xóa phần tử | Key có thể tiếp tục nằm trong selected set qua lượt sau | Dùng `Iterator.remove()` cho từng key đã lấy |
| Gọi `selectedKeys().remove(key)` trong `for-each` | Sửa cấu trúc set ngoài iterator có thể làm hỏng vòng duyệt | Duyệt với iterator và gọi `it.remove()` |
| Gọi `key.cancel()` sau mọi sự kiện đọc | Hủy cả đăng ký vẫn cần thiết | Chỉ cancel khi thực sự dừng theo dõi |
| Nhầm `iterator.remove()` với đóng socket | Registration và channel không bị đóng | Đóng channel riêng khi kết thúc kết nối |
| `selectedKeys().clear()` trước khi xử lý | Bỏ qua sự kiện đã chọn | Remove từng key sau `next()` |
| Không kiểm tra `key.isValid()` | Có thể gặp `CancelledKeyException` khi đọc trạng thái | Kiểm tra validity và xử lý lỗi đúng phạm vi |
| Chỉ dùng `else if` cho mọi bit ready | Có thể bỏ lỡ hoạt động khác cùng sẵn sàng | Kiểm tra các bit phù hợp, recheck trạng thái sau handler |
| Không tắt `OP_WRITE` khi queue đã rỗng | Selector thường xuyên báo writable, CPU cao | Chỉ bật OP_WRITE khi còn outbound pending |
| Quay vòng liên tục khi `read()==0` | Busy spin mà không có dữ liệu | Trả quyền cho event loop và đợi readiness |
| Diễn giải `read()==0` là EOF | Đóng peer quá sớm | `read()==-1` mới là EOF |
| Không xóa key nếu handler ném lỗi | Selected set có thể còn phần tử gây khó phục hồi | Remove ngay sau next; cleanup trong catch |
| Chia sẻ iterator/selected set giữa nhiều thread không đồng bộ | Race condition, xử lý trùng sự kiện | Quy định một thread sở hữu event loop hoặc đồng bộ chặt |
| Nghĩ `selector.keys()` có thể gọi `remove` trực tiếp | Registered set không cho sửa tùy tiện | Dùng `cancel` để hủy đăng ký |
| Nghĩ `select(0)` tương đương `selectNow()` | `select(0)` có thể chờ vô hạn | Dùng `selectNow()` khi muốn poll |
| Không giữ ByteBuffer qua nhiều lần read | TCP message bị cắt mảnh, parser sai | Attachment/state per connection và framing rõ ràng |

## 18. Lưu ý thread-safety và tài nguyên

`Selector` hỗ trợ các thao tác chọn và đăng ký, nhưng **selected-key set không phải hàng đợi xử lý sự kiện đa luồng tự động an toàn**. Nếu một thread duyệt và xóa trong khi thread khác cũng chỉnh selected set, bạn phải kiểm soát đồng bộ và ownership, nếu không có thể gặp race hoặc trạng thái khó dự đoán.

Thiết kế phổ biến: **một event-loop thread sở hữu `selectedKeys()` và thao tác `select`**, còn các worker thread khác chuyển thông điệp hoặc tác vụ qua queue. Khi cần cập nhật interest hoặc yêu cầu event loop xử lý việc mới, dùng cơ chế đánh thức như `selector.wakeup()` và một hàng đợi có đồng bộ phù hợp. Không mặc định chỉ gọi `wakeup()` là đủ để đồng bộ dữ liệu trong các cấu trúc ứng dụng.

Key có thể mang một `attachment` (qua `key.attach(value)` hoặc `register(selector, ops, attachment)`) để lưu parser state, inbound/outbound buffers hoặc metadata kết nối. Khi đóng kết nối, đừng quên giải phóng tham chiếu tới các buffer/tác vụ để tránh giữ bộ nhớ lâu hơn cần thiết.

Các handler cũng không nên chặn lâu trên event-loop thread: xử lý CPU nặng, truy vấn database blocking hoặc logging blocking kéo dài sẽ làm tất cả key khác bị chậm. Một lượt `selectedKeys()` thành công về mặt API chưa bảo đảm ứng dụng đáp ứng tốt dưới tải.

## 19. Bài tập kiểm tra kiến thức

### Tình huống A: key được remove nhưng không cancel

Một `SelectionKey k` đang hợp lệ, đăng ký `OP_READ`, được selector chọn. Handler thực hiện:

```java
SelectionKey k = iterator.next();
iterator.remove();
```

Hỏi `k` còn có thể được chọn ở một lượt `select()` tương lai không?

**Có.** `iterator.remove()` chỉ xóa khỏi selected set, không hủy đăng ký. Khi channel tiếp tục có readiness phù hợp với interest và key vẫn hợp lệ, selector có thể chọn lại key.

### Tình huống B: cancel nhưng không đóng channel

Handler gọi:

```java
key.cancel();
```

Channel có chắc chắn đã bị đóng không?

**Không.** `cancel()` hủy đăng ký, không tự đóng channel. Nếu muốn kết thúc socket, cần chính sách đóng tài nguyên phù hợp. Một key đã cancel không còn hợp lệ, còn việc selector loại nó khỏi registered set được xử lý theo vòng đời lựa chọn.

### Tình huống C: key vừa readable vừa writable

Một key của `SocketChannel` có `readyOps()` chứa cả `OP_READ` và `OP_WRITE`, ứng dụng có dữ liệu chờ gửi. Nếu handler chỉ có:

```java
if (key.isReadable()) {
    readFromPeer();
} else if (key.isWritable()) {
    writeToPeer();
}
```

Điều gì có thể xảy ra?

**Nhánh write bị bỏ qua** trong lượt ấy khi read đúng, dù write cũng sẵn sàng. Đây là minh họa chưa hoàn chỉnh vì cần khai báo hai hàm xử lý. Nếu thiết kế cần xử lý cả hai, dùng hai điều kiện độc lập và đảm bảo key còn hợp lệ sau nhánh thứ nhất.

### Tình huống D: selected set rỗng nhưng registered set không rỗng

Sau xử lý:

```text
selector.selectedKeys().isEmpty() = true
selector.keys().isEmpty() = false
```

Có phải đang sai không?

**Không.** Đây là trạng thái bình thường: lượt sự kiện đã chọn đã được tiêu thụ, nhưng selector vẫn theo dõi những channel còn đăng ký.

### Tình huống E: gửi chậm

Một key writable được xử lý và gọi `socket.write(buffer)` trả về 0. Handler có nên `cancel()` key và đóng socket ngay không?

**Không mặc định.** Kết quả 0 của non-blocking write có thể chỉ là chưa có tiến độ. Giữ outbound buffer và interest `OP_WRITE` phù hợp, quay về selector để đợi lúc có thể gửi tiếp. Chỉ đóng khi có lỗi, timeout hoặc chính sách nghiệp vụ yêu cầu.

## 20. Tóm tắt

**Mẫu lặp selected keys nên ghi nhớ:**

```java
Iterator<SelectionKey> it =
        selector.selectedKeys().iterator();

while (it.hasNext()) {
    SelectionKey key = it.next();
    it.remove(); // Tiêu thụ selected event, không hủy đăng ký.

    if (!key.isValid()) {
        continue;
    }

    // Dispatch sự kiện phù hợp.
}
```

Từ mẫu này, luôn nhớ bốn ranh giới trách nhiệm:

1. **`iterator.remove()`**: xóa key khỏi **selected-key set** ở lượt hiện tại.
2. **`key.cancel()`**: yêu cầu hủy **đăng ký** khi ứng dụng không còn muốn theo dõi channel.
3. **`channel.close()`**: đóng **tài nguyên I/O** khi vòng đời kết nối kết thúc.
4. **`interestOps(...)` và ByteBuffer state**: quản lý những loại readiness cần theo dõi và dữ liệu còn chờ xử lý.

Duyệt key đúng không thay thế việc xử lý partial read/write, EOF, framing, backpressure hoặc error handling. Nhưng đó là nền tảng giúp event loop rõ ràng, tránh sự kiện cũ bị giữ lại ngoài ý muốn và quản lý nhiều kết nối một cách có kiểm soát.

**Tài liệu Java SE 17 để đối chiếu:** `java.nio.channels.Selector` (`selectedKeys()`, `keys()`, `select()`, `selectNow()`), `java.nio.channels.SelectionKey` (`cancel()`, `isValid()`, `interestOps()`, `readyOps()`), `ServerSocketChannel`, `SocketChannel`, `Pipe` và `java.nio.ByteBuffer`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="selector-007">Wakeup, hủy key và đóng selector</a>

<details>
<summary>Xem chi tiết</summary>

# Wakeup, hủy SelectionKey và đóng Selector trong Java NIO

## 1. Vì sao cần ba thao tác khác nhau?

Trong Java NIO, một `Selector` quản lý nhiều `SelectableChannel` để chương trình có thể đợi những channel sẵn sàng đọc, ghi, nhận kết nối hoặc hoàn tất kết nối mà không cần một luồng chờ trên từng socket. Mỗi lần đăng ký một channel với selector sẽ tạo hoặc trả về một `SelectionKey` đại diện cho mối quan hệ đăng ký đó.

Khi event loop vận hành, ứng dụng thường cần giải quyết ba việc rất khác nhau:

1. **`selector.wakeup()`:** đánh thức một lời gọi selection đang chờ, hoặc khiến lần selection kế tiếp trở về ngay. Selector và các channel vẫn có thể tiếp tục sử dụng.
2. **`key.cancel()`:** hủy đăng ký **một channel với một selector**; key mất hiệu lực ngay, nhưng việc xóa khỏi các tập key và deregister thực tế xảy ra trong một lượt selection tiếp theo.
3. **`selector.close()`:** kết thúc vòng đời **cả selector**, vô hiệu tất cả key gắn với nó và giải phóng tài nguyên của selector; **không tự đóng các channel đã đăng ký**.

Không nên coi ba lời gọi là những cách tương đương để "dừng I/O".

| Thao tác | Điều thay đổi ngay | Các channel có đóng không? | Selector còn sử dụng được? |
|---|---|---|---|
| `wakeup()` | Một lượt select bị đánh thức hoặc next select được báo trước | Không | Có |
| `SelectionKey.cancel()` | Key trở thành invalid; được đưa vào cancelled-key set | Không | Có |
| `Selector.close()` | Selector đóng; key associated bị vô hiệu và deregister | **Không tự động** | Không |
| `channel.close()` | Chính channel đóng và những registration tương ứng bị cancel | **Có, channel đó** | Có nếu selector chưa đóng |

Lưu ý: nếu một channel được đăng ký trên **nhiều selector**, `key.cancel()` chỉ hủy đăng ký gắn với key đó. Ngược lại, `channel.close()` tác động tới bản thân channel và làm các key đăng ký channel ấy mất hiệu lực.

## 2. Hiểu Selector, SelectionKey và ba tập key

`Selector.open()` tạo selector. Chỉ một `SelectableChannel` ở **non-blocking mode** mới được đăng ký qua `channel.register(selector, ops)`. Với TCP:

~~~java
SocketChannel channel = SocketChannel.open();
channel.configureBlocking(false);
SelectionKey key = channel.register(selector, SelectionKey.OP_READ);
~~~

Đây là **đoạn minh họa**, cần có biến `Selector selector` còn open, channel ở trạng thái phù hợp và các import. Một channel mới chưa connected thường không nên đăng ký `OP_READ` trước khi kết nối hoàn tất; ví dụ này chỉ minh họa API register, không phải chương trình chạy độc lập.

Một selector theo Java 17 có ba tập logic:

- **Key set — `selector.keys()`:** tập các key đăng ký với selector (không thể tự ý add/remove).
- **Selected-key set — `selector.selectedKeys()`:** các key đã được phát hiện sẵn sàng ở lượt selection trước. Ứng dụng thường phải tự loại key khỏi tập sau khi xử lý.
- **Cancelled-key set:** tập nội bộ chứa key đã bị hủy nhưng chưa hoàn tất deregister. **Không có API public để trực tiếp đọc tập này.**

Đầu vòng đời selector, các tập rỗng. Sau `register`, key xuất hiện trong key set. Sau `select` nhận được readiness, key có thể xuất hiện trong selected-key set. Sau `cancel`, key invalid ngay nhưng vẫn có thể hiện diện trong `keys()` cho đến lượt selection tiếp theo xử lý cancellation.

~~~text
register(channel, OP_READ)
       |
       v
Key hợp lệ trong selector.keys()
       |
       +-- select nhận readiness --> selectedKeys có thể chứa key
       |                                  |
       |                                  +-- application xử lý rồi iterator.remove()
       |
       +-- key.cancel() --------> key invalid ngay
                                         |
                                         v
                              Cancelled-key set nội bộ
                                         |
                                      next select
                                         |
                                         v
                             Deregister, loại khỏi các tập
~~~

**Phân biệt:** `selectedKeys().remove(key)` chỉ loại key khỏi **danh sách sự kiện đang chờ ứng dụng xử lý**, không hủy đăng ký. `key.cancel()` mới yêu cầu ngừng theo dõi registration này.

## 3. \`Selector.wakeup()\`: đánh thức một lượt selection

`Selector.wakeup()` là công cụ điều phối khi một thread đang đợi trong `select()` hoặc khi thread khác cần cập nhật công việc/interestOps của event loop.

Các trường hợp:

1. **Đã có thread đang block trong `select()`:** `wakeup()` khiến thao tác đó sớm trở về. Nó **không bảo đảm** kết quả trả là `0`, vì có thể đồng thời đã có key ready.
2. **Chưa có selection đang chờ:** lần gọi selection kế tiếp trở về ngay, trừ khi một `selectNow()`/`selectNow(Consumer)` tiêu thụ trạng thái wakeup trước đó.
3. **Gọi wakeup nhiều lần giữa hai lượt selection:** hiệu lực giống **một lần**; không có hàng đợi đếm số tín hiệu wakeup.
4. **Sau lượt selection đã bị đánh thức:** lần selection kế tiếp có thể block bình thường nếu không có readiness hay tín hiệu mới.
5. **`wakeup()` không đăng ký, không cancel, không đóng channel**, cũng không tự đưa một task vào queue của ứng dụng.

Chữ ký:

~~~java
public abstract Selector wakeup()
~~~

Phương thức trả chính selector để có thể gọi theo kiểu chaining, nhưng cách viết rõ ràng trong event loop thường là `selector.wakeup();`.

### Khi nào dùng wakeup?

Giả sử một thread quản lý event loop đang:

~~~java
while (running) {
    selector.select();
    // Xử lý các key ready và task được gửi từ thread khác.
}
~~~

Nếu thread khác cần yêu cầu event loop thay đổi interest set, đăng ký channel, gửi dữ liệu hoặc dừng vòng lặp, có thể **đưa yêu cầu vào queue trước rồi gọi wakeup**. Lúc tỉnh dậy, event loop xử lý yêu cầu rồi mới select tiếp.

~~~text
Thread công việc: queue.add(task) ---> selector.wakeup()
                                            |
                                            v
Thread event loop:  select() trở về --> drain queue --> cập nhật state
                                                  --> xử lý readiness
                                                  --> select tiếp
~~~

Để không làm mất yêu cầu do cạnh tranh, queue phải thread-safe (ví dụ `ConcurrentLinkedQueue`) hoặc có đồng bộ thích hợp. `wakeup` là **tín hiệu đánh thức**, không thay thế queue và không chứa payload.

### Wakeup trước select vẫn có ý nghĩa

Không cần cố dùng `Thread.sleep` để "canh" thời điểm thread đang block. Java bảo đảm **wakeup gọi trước lần select tiếp theo** cũng có hiệu lực. Ví dụ:

~~~java
selector.wakeup();
int updated = selector.select(); // trả ngay, updated có thể là 0
~~~

Đây là đoạn minh họa. Nếu có channel ready, kết quả có thể khác 0. `selectNow()` trả ngay trong mọi trường hợp và **xóa hiệu lực wakeup chưa sử dụng**.

### Wakeup khác Thread.interrupt

Thread khác gọi `selector.wakeup()` **không đặt interrupt flag** trên thread của event loop. Gọi `thread.interrupt()` khi thread đó đang block trong selection sẽ đặt interrupt flag và đánh thức selector như gọi `wakeup()`. Không tự suy luận việc đánh thức `Selector.select()` là `ClosedByInterruptException` hoặc channel bị đóng; loại exception ấy gắn với blocking I/O trên `InterruptibleChannel`, còn select có hợp đồng riêng.

Nếu chủ đích chỉ là báo "có task mới", thường `wakeup()` diễn đạt ý định rõ ràng hơn `interrupt()`.

## 4. \`SelectionKey.cancel()\`: hủy một registration

`SelectionKey.cancel()` có hành vi chính xác:

- Khi trả về, `key.isValid()` là **`false`**.
- Key được đưa vào **cancelled-key set** của selector và sẽ bị loại khỏi mọi tập key, đồng thời channel bị deregister, trong lượt selection tiếp theo.
- Hủy lặp lại cùng key là **idempotent**: lời gọi tiếp theo không có tác dụng mới.
- Key đã bị hủy **không trở lại hợp lệ**. Nếu cần theo dõi lại channel, chờ deregistration hoàn tất rồi gọi `register` để nhận key mới.
- **`cancel()` không đóng channel**. Ứng dụng vẫn có thể dùng channel trực tiếp nếu nó còn open và ở trạng thái cho phép.

Ví dụ quan sát trạng thái:

~~~java
key.cancel();
System.out.println(key.isValid()); // false
selector.selectNow();              // xử lý cancelled-key set
~~~

Đoạn này minh họa thứ tự thao tác khi `selector` còn open. Nếu chỉ cancel và không gọi selection thêm, key có thể vẫn xuất hiện trong `selector.keys()`; đó là trạng thái trung gian hợp lệ.

### Phân biệt cancel và remove khỏi selectedKeys

Hai thao tác thường xuất hiện trong cùng event loop nhưng có tác dụng khác nhau:

~~~java
Iterator<SelectionKey> it = selector.selectedKeys().iterator();
while (it.hasNext()) {
    SelectionKey key = it.next();
    it.remove(); // đã lấy một event ra khỏi selected-key set

    if (!key.isValid()) {
        continue; // key có thể đã bị cancel ở thread khác
    }

    if (key.isReadable()) {
        // Đọc cho đến khi không còn dữ liệu hiện sẵn hoặc hết budget.
    }
}
~~~

Đây là **đoạn mã trong một phương thức đã có biến selector**; phần xử lý đọc chỉ là chú thích. `it.remove()` không cancel và không đóng channel; nó chỉ tránh xử lý lặp sự kiện cũ. Việc `selectedKeys()` không tự xóa hết key sau mỗi vòng là nguồn lỗi CPU busy loop phổ biến.

### Điều gì xảy ra với selectedKeys khi cancel?

Sau `cancel()`, key invalid ngay nhưng có thể vẫn nằm trong `selectedKeys()`/`keys()` cho đến khi selection operation thực hiện bước xử lý cancellation. Vì vậy khi duyệt selected keys, cần kiểm tra `key.isValid()` trước khi gọi `readyOps()`, `isReadable()`, `isWritable()` hoặc cập nhật `interestOps()`.

Những lời gọi đó có thể ném `CancelledKeyException` nếu key đã invalid. Kiểm tra và thao tác cũng có thể chịu race với thread khác; tránh để nhiều thread tùy ý xử lý cùng key mà không có quy tắc ownership.

### Nếu muốn hủy và đăng ký lại ngay?

`cancel()` không tức thì deregister underlying registration. Trong code chủ động chạy ở event loop, có thể gọi `selector.selectNow()` để thúc đẩy xử lý cancelled keys, sau đó mới `register` lại channel nếu còn open và non-blocking. Điều này giúp phân biệt **vô hiệu key tức thì** với **deregister theo lượt selection**.

Không nên dùng `selectNow()` tùy tiện từ nhiều thread đồng thời với một selector đã có event loop; chọn rõ một thread sở hữu luồng selection để tránh tranh chấp.

## 5. \`Selector.close()\`: dừng selector nhưng không đóng channel

`Selector` triển khai `Closeable` và `AutoCloseable`. Do vậy nên dùng `try-with-resources` hoặc một phương thức shutdown đảm bảo `close()` được gọi đúng chủ sở hữu.

Theo Java 17, `selector.close()`:

1. Đánh dấu selector đã đóng và giải phóng những tài nguyên nội bộ thuộc selector.
2. Nếu thread khác đang block ở một selection operation, nó được đánh thức như qua `wakeup()`.
3. Các key còn đăng ký bị vô hiệu, các channel bị **deregister khỏi selector này**.
4. Gọi `close()` lần thứ hai không có tác dụng mới.
5. Những lần sử dụng selector sau khi đóng (ngoại trừ `close()`/`wakeup()`) ném `ClosedSelectorException`.
6. **Không tự động đóng `SocketChannel`, `ServerSocketChannel`, `DatagramChannel`, `Pipe.SourceChannel` hay channel khác đã đăng ký**.

Điểm cuối rất quan trọng với leak tài nguyên: đóng selector **không phải** lệnh đóng tất cả kết nối socket. Server phải tự đóng những channel mình sở hữu, bên cạnh selector.

Ví dụ:

~~~java
Selector selector = Selector.open();
SocketChannel channel = SocketChannel.open();
try {
    channel.configureBlocking(false);
    channel.register(selector, SelectionKey.OP_CONNECT);

    selector.close();
    System.out.println(channel.isOpen()); // true
} finally {
    channel.close();
}
~~~

**Đoạn mã minh họa**: có thể ném `IOException`; socket vừa mở chưa connected nhưng vẫn có thể đăng ký quan tâm `OP_CONNECT` theo `validOps`. `selector.close()` không đóng `channel`. Không coi đây là ví dụ kết nối TCP hoàn chỉnh.

### Close trong lúc thread đang select

Một thread khác có thể gọi `selector.close()` để dừng một `select()` đang block. Nhưng event loop phải chuẩn bị để quan sát selector đã đóng, có thể gặp `ClosedSelectorException` ở thao tác sau đó, và dừng sử dụng selector. **Không dựa vào một số lượng ready keys cố định** khi có close/cancel chạy đồng thời.

Nếu chỉ muốn event loop thức dậy, thực hiện task mới rồi tiếp tục, dùng `wakeup()`. Nếu đã xác định vòng đời selector kết thúc, mới dùng `close()` theo quy trình shutdown.

## 6. Demo Java 17+ chạy được: wakeup, cancel, close

Demo sau chỉ dùng `Pipe` và `Selector` nội bộ, không yêu cầu kết nối mạng hoặc port. Không có byte gửi vào pipe, vì vậy khi chưa có readiness, `wakeup()` giúp `select()` trở về với 0 key được chọn. Sau đó hủy key và chứng minh channel vẫn còn open khi selector đã bị đóng.

**Tệp: `SelectorLifecycleDemo.java`**

~~~java
import java.nio.channels.Pipe;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

public class SelectorLifecycleDemo {
    public static void main(String[] args) throws Exception {
        Pipe pipe = Pipe.open();

        try (Selector selector = Selector.open();
             Pipe.SourceChannel source = pipe.source();
             Pipe.SinkChannel sink = pipe.sink()) {

            source.configureBlocking(false);
            SelectionKey key =
                    source.register(selector, SelectionKey.OP_READ);

            System.out.println("registered=" + selector.keys().size());

            selector.wakeup();
            System.out.println("selected=" + selector.select());

            System.out.println("valid before=" + key.isValid());
            key.cancel();
            System.out.println("valid after=" + key.isValid());
            System.out.println(
                    "pending count=" + selector.keys().size());

            selector.selectNow(); // xử lý cancelled-key set
            System.out.println(
                    "removed count=" + selector.keys().size());
            System.out.println(
                    "source open after cancel=" + source.isOpen());

            SelectionKey second =
                    source.register(selector, SelectionKey.OP_READ);
            System.out.println("second valid=" + second.isValid());

            selector.close();
            System.out.println(
                    "second valid after close=" + second.isValid());
            System.out.println(
                    "source open after selector close=" + source.isOpen());
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac SelectorLifecycleDemo.java
java SelectorLifecycleDemo
~~~

**Stdout trên JDK 17+ trong điều kiện demo mô tả:**

~~~text
registered=1
selected=0
valid before=true
valid after=false
pending count=1
removed count=0
source open after cancel=true
second valid=true
second valid after close=false
source open after selector close=true
~~~

Giải thích:

1. `source.configureBlocking(false)` là điều kiện cần để đăng ký với selector.
2. `wakeup()` trước `select()` đảm bảo lượt select trở về ngay; vì pipe không có dữ liệu, `selected=0`.
3. `key.cancel()` làm `key.isValid() == false` ngay, nhưng trước lượt selection kế tiếp key còn trong `selector.keys()`: `pending count=1`.
4. `selector.selectNow()` xử lý cancellation và deregister, nên key set rỗng.
5. Source channel vẫn open sau `cancel()`, nên có thể đăng ký lại; key mới là hợp lệ.
6. `selector.close()` làm key mới mất hiệu lực nhưng **không đóng source channel**.
7. `try-with-resources` vẫn đóng cả source và sink khi scope kết thúc, không để lại tài nguyên pipe.

Không có `Thread.sleep()` trong demo: tính tức thì của `select()` xuất phát trực tiếp từ hợp đồng `wakeup()`, không phụ thuộc timing giữa các thread. Demo mô phỏng một channel không có readiness; trong ứng dụng có key ready thì `select()` có thể trả số khác 0 ngay cả khi đã wakeup.

## 7. Event loop nhận lệnh từ thread khác qua task queue và wakeup

Một kiến trúc đơn giản là để **một thread chuyên trách** thực hiện `select`, duyệt `selectedKeys`, thay đổi `interestOps` và đóng những channel thuộc event loop. Các thread khác không tự ý đụng vào selected-key set; chúng gửi command vào queue rồi báo `wakeup`.

Ví dụ mã khung (chỉ minh họa cấu trúc, không phải chương trình hoàn chỉnh):

~~~java
final class LoopCommands {
    private final Selector selector;
    private final Queue<Runnable> pending =
            new ConcurrentLinkedQueue<>();

    LoopCommands(Selector selector) {
        this.selector = selector;
    }

    void submit(Runnable task) {
        pending.add(task);
        selector.wakeup();
    }

    void drain() {
        for (Runnable task; (task = pending.poll()) != null; ) {
            task.run();
        }
    }
}
~~~

Cần import `java.nio.channels.Selector`, `java.util.Queue`, `java.util.concurrent.ConcurrentLinkedQueue` trong chương trình thật. `drain()` được **thread event loop** gọi. Đặt `pending.add(task)` trước `selector.wakeup()` nhằm bảo đảm công việc đã có trong queue khi loop tỉnh.

Một event loop điển hình:

~~~java
while (running) {
    selector.select();
    commands.drain();

    Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
    while (keys.hasNext()) {
        SelectionKey key = keys.next();
        keys.remove();

        if (!key.isValid()) {
            continue;
        }
        // Xử lý OP_ACCEPT / OP_CONNECT / OP_READ / OP_WRITE.
    }
}
~~~

Đây là mã minh họa: `running`, `commands`, `selector` là biến đã được khởi tạo, phần xử lý I/O cần triển khai. Thread khác gọi `commands.submit(...)` để thêm hoặc hủy interest, đổi trạng thái shutdown. Code production cần handling của `IOException`, `CancelledKeyException`, per-client state, backpressure và lỗi trong Runnable để một command lỗi không làm mất toàn bộ event loop không chủ đích.

**Chú ý:** `wakeup()` **không ép selector đang trong một lần selection phải cập nhật ngay interestOps của lần đó**. Java 17 xác định thay đổi interest set trong khi đang select sẽ có hiệu lực ở **lượt selection tiếp theo**. Chính vì vậy, cơ chế queue + wakeup rồi thay interestOps trên event-loop thread dễ kiểm soát hơn.

## 8. Selected-key set và những cạnh tranh thường gặp

### 8.1. selectedKeys không tự bị clear hoàn toàn

API `selector.selectedKeys()` trả tập key được chọn để ứng dụng xử lý. Nếu chỉ duyệt mà không `iterator.remove()`, một key vẫn có thể nằm trong tập đó sau khi đã xử lý readiness. Ở lượt select kế tiếp, ready operations của key sẵn trong tập có thể được gộp vào trạng thái cũ. Điều này dễ tạo **xử lý readiness lặp** và busy loop.

Cách xử lý thông dụng là xóa từng key khỏi selected-key set trước khi dispatch.

### 8.2. Hai kiểu select khác nhau trong Java 17

- `select()` / `select(timeout)` / `selectNow()` dùng **selected-key set** và ứng dụng tự dọn tập này.
- Các overload Java 11+ nhận `Consumer<SelectionKey>` như `select(action)` hay `selectNow(action)` có thể **gọi callback trực tiếp** trên key ready, **không thêm key vào selected-key set** theo dạng selection này.

Với callback selection, phải rất thận trọng vì callback diễn ra trong quá trình synchronized selection. **Không thực hiện một selection operation reentrant trên chính selector bên trong callback**; hành vi không được xác định thống nhất cho mọi provider. Nếu callback đóng selector, lời gọi selection sẽ báo `ClosedSelectorException` khi callback hoàn thành.

### 8.3. Thread safety của các tập

Theo tài liệu `Selector`:

- **Selector và key set** an toàn cho một số thao tác đồng thời được API mô tả.
- **Selected-key set và cancelled-key set không được coi là thread-safe** cho mọi cách dùng trực tiếp.
- `key.cancel()` được phép gọi từ thread khác, nhưng việc deregister thực tế sẽ xảy ra trong selection operation.
- Trước khi xử lý key lấy từ tập, phải nghĩ tới khả năng key đã bị cancel hoặc channel đã closed ở một thread khác.
- Khi nhiều thread thay đổi selected-key set trực tiếp, cần synchronize theo hợp đồng API; đơn giản hơn là giao việc duyệt và xóa selected keys cho một event-loop thread.

`selector.keys()` cũng **không nên được dùng làm danh sách bảo đảm tất cả key hợp lệ**, bởi cancelled key có thể chưa được loại khỏi tập.

## 9. Shutdown đúng: đánh thức, hủy key, đóng channel và đóng selector

Để dừng một server/event loop mà không rò rỉ socket hay file descriptor, cần tách rõ **signal** và **cleanup**.

Một quy trình thường dùng:

1. Đánh dấu trạng thái `running = false` hoặc enqueue một shutdown command theo cơ chế thread-safe.
2. Gọi `selector.wakeup()` để thread event loop đang đợi thoát khỏi `select()` và nhận lệnh dừng.
3. Ngừng nhận registration mới hoặc mở socket mới; ngừng nhận message mới theo chính sách server.
4. Hủy key và/hoặc đóng từng channel còn do service sở hữu. `key.cancel()` đơn thuần không đóng kết nối.
5. Đóng `selector` trong `finally` / `try-with-resources` để giải phóng tài nguyên selector; không tiếp tục gọi `select` sau khi đóng.
6. Thu hồi các tài nguyên còn lại như worker executor, message buffers có ownership đặc biệt, timer hay các adapter liên kết.

Một khung shutdown minh họa:

~~~java
void requestStop() {
    running = false;       // ví dụ running là volatile
    selector.wakeup();     // signal tới event-loop thread
}

void cleanupOnLoopThread() throws IOException {
    for (SelectionKey key : selector.keys()) {
        key.channel().close(); // đóng tài nguyên channel đang do loop sở hữu
    }
    selector.close();
}
~~~

Đây là **mã minh họa trong lớp quản lý** có các biến `volatile boolean running` và `Selector selector`, chưa trình bày handling và đồng bộ đầy đủ. Chỉ dùng vòng lặp đóng tất cả `key.channel()` nếu event loop **thực sự sở hữu** tất cả channel ấy; với channel mượn từ thành phần khác, cần thỏa thuận ownership.

### Vì sao không chỉ gọi selector.close?

Nếu chỉ đóng selector, socket channels vẫn có thể open. Điều này giữ tài nguyên hệ điều hành và kết nối không cần thiết, dẫn tới lỗi kiểu “too many open files”, cạn file descriptor hoặc tài nguyên mạng. **Close selector không thay cho close channel**.

### Vì sao không chỉ gọi key.cancel?

`cancel()` chỉ loại channel khỏi cơ chế multiplexing, và việc loại thực sự xảy ra ở selection tiếp theo. Channel vẫn open. Nếu ứng dụng không có chủ đích giữ channel để xử lý ngoài selector, phải đóng nó khi kết thúc.

### Vì sao không gọi Thread.interrupt để thay wakeup?

Interrupt là tín hiệu ở cấp thread, ảnh hưởng cách task xử lý cancellation và các blocking operations khác. `wakeup` chuyên biệt cho selection; với task queue/event loop, nó thường có semantics dễ hiểu hơn. Nếu thread đã bị interrupt, code phải tôn trọng interrupt flag và chính sách shutdown của ứng dụng.

## 10. Khi nào dùng selectNow, khi nào dùng wakeup?

| Tình huống | API phù hợp | Lưu ý |
|---|---|---|
| Thread A đang có event loop `select()`, B vừa gửi task | `selector.wakeup()` | Task phải được lưu ngoài selector |
| Một thread muốn poll readiness mà không block | `selector.selectNow()` | Đồng thời tiêu thụ pending wakeup |
| Muốn hủy một registration | `key.cancel()` | Không đóng channel, chờ lượt selection |
| Muốn xử lý cancelled key ngay trong thread sở hữu selector | `selector.selectNow()` | Có thể tiến hành deregistration |
| Muốn dừng hẳn selector | `selector.close()` | Key invalid, channel vẫn cần cleanup |
| Muốn kết thúc kết nối TCP | `socketChannel.close()` | Registration của channel cũng bị cancel |
| Chỉ muốn loại event đã dispatch khỏi selected-key set | `iterator.remove()` | Registration vẫn còn hợp lệ |

Một biến thể thường thấy ở code cũ là gọi `selector.wakeup()` nhiều lần để “gửi nhiều task”. Nhưng wakeup **không đếm sự kiện**; nên dùng queue, xử lý đến khi queue rỗng, và dùng wakeup làm tín hiệu đánh thức.

## 11. Lỗi thường gặp

1. **“`wakeup()` đóng selector.”** Sai. Nó chỉ đánh thức một lần selection hoặc đánh dấu pending wakeup.
2. **“Mỗi `wakeup()` cộng một lượt đánh thức trong queue.”** Sai. Nhiều wakeup giữa hai lượt selection có hiệu lực như một.
3. **“Nếu gọi wakeup trước select thì không tác dụng.”** Sai. Lượt select tiếp theo có thể trở về ngay.
4. **“`wakeup()` luôn làm `select()` trả 0.”** Sai. Ready keys vẫn có thể được phát hiện.
5. **“`key.cancel()` xóa key khỏi `keys()` ngay.”** Sai. Key invalid ngay, nhưng deregister được xử lý trong selection operation sau.
6. **“`key.cancel()` đóng socket.”** Sai. Channel vẫn open nếu không bị đóng riêng.
7. **“`selector.close()` đóng tất cả channel đăng ký.”** Sai. Nó đóng selector, invalid key và deregister; channel do chủ sở hữu đóng riêng.
8. **“`selector.keys()` chỉ chứa valid keys.”** Sai. Cancelled keys có thể còn ở đó tạm thời.
9. **“Xóa key khỏi `selectedKeys` là hủy đăng ký.”** Sai. Chỉ dọn event đã chọn.
10. **“selectedKeys được Java tự clear sau mọi select.”** Sai. Ứng dụng thường phải tự remove đã xử lý.
11. **“Cứ đọc `isReadable()` khi key có trong selectedKeys.”** Sai. Key có thể đã bị cancel, nên kiểm tra valid và tính tới race.
12. **“Selector và mọi tập key đều thread-safe để thao tác tùy ý.”** Sai. Selected-key set cần tuân thủ synchronization hoặc chỉ do event-loop thread xử lý.
13. **“Thay đổi interestOps từ thread khác đang select có tác dụng ngay trong chính lượt đó.”** Không được bảo đảm; hiệu lực ở lượt selection tiếp theo.
14. **“Đóng selector rồi có thể select lại sau khi wakeup.”** Sai. Selector đã closed thì selection tiếp tục ném `ClosedSelectorException`.

## 12. Câu hỏi tự kiểm tra

**Câu 1:** Thread A đang `selector.select()`, thread B enqueue task rồi gọi `selector.wakeup()`. A có cần đóng selector để xử lý task không?

**Đáp án:** Không. `wakeup` khiến select trở về; A xử lý task rồi có thể tiếp tục select trên selector còn open.

**Câu 2:** Vừa gọi `key.cancel()`, `key.isValid()` là gì và key còn có thể nằm trong `selector.keys()` không?

**Đáp án:** `false` ngay. Có; việc loại khỏi key set và deregister diễn ra trong lượt selection tiếp theo.

**Câu 3:** Đóng selector có tự đóng `SocketChannel` đã đăng ký không?

**Đáp án:** Không. Selector invalid key/deregister, còn các channel open phải do chủ sở hữu quản lý và đóng nếu cần.

**Câu 4:** Sự khác biệt giữa `iterator.remove()` trên `selectedKeys` và `key.cancel()`?

**Đáp án:** `iterator.remove()` chỉ bỏ một sự kiện khỏi tập selected; `cancel()` làm registration của channel trên selector mất hiệu lực.

**Câu 5:** Năm lần `wakeup()` giữa hai lần `select()` có bảo đảm năm lần select tiếp theo đều không block không?

**Đáp án:** Không. Các wakeup có hiệu lực gộp như một lần; sau lần selection tiêu thụ wakeup, những lần kế tiếp có thể block.

### Tài liệu Java 17 chính thức

- [Selector: key sets, selection, concurrency, wakeup, close](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/Selector.html)
- [SelectionKey: cancel, isValid, interestOps](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/SelectionKey.html)
- [SelectableChannel: register và blocking mode](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/SelectableChannel.html)
- [CancelledKeyException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/CancelledKeyException.html)
- [ClosedSelectorException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/ClosedSelectorException.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="selector-008">Sẵn sàng I/O không bảo đảm hoàn tất toàn bộ thao tác</a>

<details>
<summary>Xem chi tiết</summary>

# Selector: sẵn sàng I/O không bảo đảm hoàn tất toàn bộ thao tác

Trong Java NIO, `Selector` theo dõi nhiều `SelectableChannel` để phát hiện **readiness** — một thao tác đọc, ghi, nhận kết nối hoặc hoàn tất kết nối **có khả năng tiến triển** mà không cần chờ theo cơ chế blocking. Nhưng **ready không đồng nghĩa complete**: tín hiệu `OP_READ` không hứa đọc được toàn bộ thông điệp, `OP_WRITE` không hứa ghi được hết buffer, `OP_ACCEPT` không hứa chấp nhận tất cả kết nối đang chờ, và `OP_CONNECT` không hứa kết nối đã thành công.

Đó là lý do một event loop đúng phải kết hợp **ready ops + kết quả thật của API + trạng thái còn lại của buffer/protocol**, sau đó quay lại Selector khi chưa hoàn tất. Bài này tập trung phân biệt readiness với completion, xử lý partial I/O, zero progress và giới hạn của từng sự kiện trong Java 17+.

## 1. Readiness khác completion ra sao?

Hai khái niệm:

- **Readiness (sẵn sàng):** OS/provider thông báo rằng một loại thao tác trên channel có khả năng thực hiện ở thời điểm khảo sát; `Selector.select()` cung cấp các `SelectionKey` chứa bit readiness.
- **Completion (hoàn tất):** thao tác **ở cấp ứng dụng** thực sự đạt mục tiêu, ví dụ nhận đủ frame dài 100 byte, gửi hết một response 8 KiB, hoàn tất TCP connect, hoặc xử lý hết danh sách kết nối đang chờ.

```text
                 Selector.select()
                        |
                        v
                  OP_READ ready
                        |
                        v
              socket.read(buffer)
                        |
             +----------+-----------+
             |          |           |
           n > 0       n == 0      n == -1
             |          |           |
       Có byte mới   Chưa byte    EOF/half-close
             |        tiến triển    |
             v                      v
       Đủ frame chưa?        Xử lý kết thúc
          /       \
        Có        Chưa
        |          |
   Xử lý frame  Lưu buffer/state
                rồi chờ đọc tiếp
```

Một thông điệp TCP có thể được chia qua nhiều lần `read`, hoặc nhiều thông điệp nằm trong một lần `read`. Đừng đánh đồng **ranh giới I/O call**, **ranh giới gói TCP** và **ranh giới request ứng dụng**.

### Ví dụ ngắn

Giả sử protocol định nghĩa frame dài **8 byte** nhưng lúc `OP_READ` được báo, lần đọc đầu chỉ trả 3 byte. Buffer đã có `position = 3`, còn thiếu 5 byte: request **chưa hoàn tất**. Nếu handler cố giải mã ngay, có thể ném `BufferUnderflowException` hoặc tạo dữ liệu sai. Handler phải giữ 3 byte, đợi lần đọc tiếp và chỉ xử lý khi đủ 8 byte.

Ngược lại, một sự kiện `OP_READ` cũng có thể dẫn đến việc đọc đủ **hai frame** trong một lần; parser phải xử lý cả hai thay vì luôn giả định chỉ có một.

## 2. Tập interest ops và ready ops không phải kết quả I/O

Một `SelectionKey` mang hai tập bitmask:

| API | Trả lời câu hỏi | Không cam kết |
| --- | --- | --- |
| `key.interestOps()` | Ứng dụng đang **muốn theo dõi** loại thao tác nào? | Không khẳng định channel đã sẵn sàng. |
| `key.readyOps()` | Selector vừa phát hiện loại thao tác nào **có khả năng tiến triển**? | Không khẳng định bytes hay bản ghi đã hoàn tất. |
| `key.isReadable()` | Bit `OP_READ` hiện diện trong ready ops? | Không bảo đảm `read` trả đúng số byte cần. |
| `key.isWritable()` | Bit `OP_WRITE` hiện diện trong ready ops? | Không bảo đảm `write` gửi hết buffer. |
| `key.isAcceptable()` | Có readiness cho `accept`? | Không bảo đảm xử lý hết mọi client đang chờ. |
| `key.isConnectable()` | Có readiness để thử hoàn tất connect? | Không bảo đảm connect thành công. |

Cả `interestOps` và `readyOps` mô tả **loại thao tác**, không phải kích thước transfer. Để biết tiến độ, dùng giá trị `n` mà channel trả về và trạng thái `ByteBuffer`.

Trước khi đọc trạng thái key, kiểm tra `key.isValid()`. Key có thể đã bị `cancel()` hoặc channel bị đóng; nhiều thao tác trên key invalid ném `CancelledKeyException`.

## 3. Tác động của read/write lên ByteBuffer

`ByteBuffer` có ba thông số và một giá trị suy ra:

```text
0 <= position <= limit <= capacity
remaining = limit - position
```

- `position()` là offset của phần tử kế tiếp trong buffer.
- `limit()` là biên kết thúc **exclusive** của vùng đang được phép xử lý.
- `capacity()` là sức chứa của buffer.
- `remaining()` là số byte còn lại trong vùng từ `position` đến trước `limit`.

Khi gọi `socket.read(buffer)` trong **pha ghi vào buffer**, nếu `n > 0` thì channel đặt thêm `n` byte và dịch `buffer.position()` đúng `n`.

Sau khi đọc, gọi `buffer.flip()` để chuyển sang **pha tiêu thụ**: `limit = position` cũ và `position = 0`. Bộ giải mã đọc từng byte hoặc các trường dữ liệu trong vùng `[position, limit)`. Sau khi giải mã:

- Dùng `clear()` **chỉ khi đã xử lý hết** nội dung cần giữ, để tiếp tục đọc đợt mới.
- Dùng `compact()` khi vẫn còn dữ liệu chưa đủ frame; lệnh này giữ phần `remaining()`, dồn về đầu, đặt position bằng số byte giữ lại và limit về capacity.
- Dùng `rewind()` khi muốn đọc lại cùng một vùng, không phải để chuẩn bị nhận thêm dữ liệu mới.

Khi gọi `socket.write(outBuffer)` trong **pha đọc từ buffer**, nếu `n > 0` thì channel tiêu thụ `n` byte và tăng `outBuffer.position()` đúng `n`. Nếu `outBuffer.hasRemaining()` còn đúng, response **chưa gửi hết**.

**Không cộng thủ công** `position` thêm `n`: phương thức channel đã cập nhật. Và không dùng `capacity` làm số byte hợp lệ sau `flip()`; điều kiện đúng là `remaining()`.

## 4. OP_READ: đọc một phần là bình thường

Với `SocketChannel` đã `configureBlocking(false)`, gọi `read(ByteBuffer)` trả:

| Giá trị | Nghĩa | Xử lý điển hình |
| --- | --- | --- |
| `n > 0` | Đã đọc thực tế `n` byte. | Tiến parser; nếu chưa đủ frame, giữ phần dở dang và đọc tiếp khi thích hợp. |
| `n == 0` | Không chuyển byte trong lời gọi này. | Không kết luận EOF; thoát vòng đọc và chờ readiness khác. |
| `n == -1` | Đầu vào tới EOF, ví dụ peer đóng nửa ghi. | Xử lý kết thúc giao thức, frame còn dang dở và chính sách half-close/đóng kết nối. |

Khi thấy ready, có thể đọc lặp cho đến khi `read == 0` (đã lấy hết dữ liệu hiện sẵn) **hoặc đạt hạn mức công việc mỗi sự kiện**. Không nên độc chiếm event loop bằng việc xử lý mãi một kết nối đang liên tục bơm dữ liệu.

Mẫu **minh họa** (các hàm và biến state là của ứng dụng, không phải API có sẵn):

```java
// socket: SocketChannel non-blocking; state.input đang ở pha ghi.
int n = socket.read(state.input);
if (n < 0) {
    handleInputEof(state); // Xét frame dở, half-close, output chờ.
} else if (n > 0) {
    state.input.flip();
    decodeCompleteFrames(state.input, state); // Chỉ tiêu thụ frame đủ.
    state.input.compact(); // Giữ byte thiếu để lần đọc sau nối tiếp.
}
// n == 0: Không xử lý dữ liệu mới; đợi OP_READ lần sau.
```

Nếu input buffer đã đầy mà parser vẫn chưa xử lý được, phải đặt hạn mức dung lượng/khung hoặc thiết kế stream payload. **Không** chờ `OP_READ` vô hạn với `buffer.remaining() == 0`; kể cả có readiness, buffer không thể nhận thêm.

Readiness có thể được báo ngay khi có dấu hiệu EOF; không mặc định `key.isReadable()` đồng nghĩa có ít nhất một byte dữ liệu.

## 5. OP_WRITE: writable không có nghĩa đã gửi hết

Trong TCP, một socket có thể được báo writable khi hệ điều hành có khả năng nhận **ít nhất một phần** dữ liệu vào vùng đệm gửi. Nhưng:

- `socket.write(buffer)` có thể trả `n < buffer.remaining()` trước lệnh.
- Nó có thể trả `0`, kể cả sau khi trước đó key báo ready (điều kiện đã thay đổi hoặc tài nguyên hiện không sẵn sàng).
- Khi còn dữ liệu, ứng dụng phải giữ nguyên buffer (cùng `position` đã cập nhật) rồi ghi tiếp trong lượt này hoặc ở readiness sau.
- Số byte được channel chấp nhận **không bằng** số byte bên nhận đã xử lý, và không bảo đảm thông điệp đã tới ứng dụng đối tác.

Mẫu gửi đúng:

```java
// pending: ByteBuffer đã flip, chứa dữ liệu còn chờ gửi.
while (pending.hasRemaining()) {
    int n = socket.write(pending);
    if (n == 0) {
        break; // Không quay vòng bận: chờ OP_WRITE lần sau.
    }
}
if (!pending.hasRemaining()) {
    // Hoàn tất buffer hiện tại; bỏ OP_WRITE nếu queue trống.
}
```

Đây là snippet, cần `socket` là `SocketChannel` non-blocking. Trong chương trình thật, kiểm tra deadline, đóng socket khi `IOException`, xử lý các buffer tiếp theo trong hàng đợi, và áp dụng backpressure.

**Lưu ý quan trọng:** `OP_WRITE` thường xuyên ready khi socket còn khả năng ghi. Nếu bật `OP_WRITE` vĩnh viễn dù **không có dữ liệu pending**, selector có thể đánh thức rất nhiều lần và CPU tăng cao. Bật chỉ khi hàng đợi outbound không rỗng, tắt khi đã gửi hết:

```java
// Snippet: key còn valid.
key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);

// Sau khi hoàn tất toàn bộ outbound queue:
key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
```

Thao tác bitmask `|`, `& ~` giữ nguyên những interest khác như `OP_READ`. Gọi `interestOps(OP_WRITE)` một cách mù quáng có thể vô tình tắt đọc.

## 6. Ví dụ Java 17+: Selector ready nhiều lần, dữ liệu chuyển từng phần

Ví dụ này thực sự sử dụng **một `Selector`**, một `Pipe.SinkChannel` và một `Pipe.SourceChannel`. Hai channel được đặt **non-blocking** và lần lượt đăng ký `OP_WRITE`, `OP_READ`. Để minh họa partial I/O **một cách xác định**, mỗi lần ghi được chủ động giới hạn tối đa 2 byte bằng cách tạm thu hẹp `ByteBuffer.limit()`; buffer đọc cũng chỉ có capacity 2.

Việc tạm hạ limit là **kỹ thuật kiểm thử** để làm rõ hành vi, không phải đảm bảo `SocketChannel` luôn ghi đúng 2 byte. Channel thật vẫn có thể chuyển **ít hơn**.

Lưu thành **`ReadinessNotCompletionDemo.java`**:

```java
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

public class ReadinessNotCompletionDemo {
    public static void main(String[] args) throws IOException {
        Pipe pipe = Pipe.open();
        try (Pipe.SourceChannel source = pipe.source();
             Pipe.SinkChannel sink = pipe.sink();
             Selector selector = Selector.open()) {

            source.configureBlocking(false);
            sink.configureBlocking(false);
            source.register(selector, SelectionKey.OP_READ);
            SelectionKey outputKey =
                    sink.register(selector, SelectionKey.OP_WRITE);

            ByteBuffer pending = ByteBuffer.wrap(
                    "ABCDE".getBytes(StandardCharsets.US_ASCII));
            ByteArrayOutputStream received = new ByteArrayOutputStream();
            int writeCalls = 0;
            int readCalls = 0;

            while (pending.hasRemaining() || received.size() < 5) {
                if (selector.select(3_000) == 0) {
                    throw new IOException("Timeout khi chờ I/O");
                }
                Iterator<SelectionKey> it =
                        selector.selectedKeys().iterator();
                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    it.remove();
                    if (!key.isValid()) {
                        continue;
                    }

                    if (key.isWritable()) {
                        int oldLimit = pending.limit();
                        pending.limit(Math.min(
                                oldLimit, pending.position() + 2));
                        try {
                            int n = sink.write(pending);
                            if (n > 0) {
                                writeCalls++;
                            }
                        } finally {
                            pending.limit(oldLimit);
                        }
                        if (!pending.hasRemaining()) {
                            outputKey.interestOps(0);
                        }
                    }

                    if (key.isReadable()) {
                        ByteBuffer chunk = ByteBuffer.allocate(2);
                        int n = source.read(chunk);
                        if (n == -1) {
                            throw new IOException("EOF sớm");
                        }
                        if (n > 0) {
                            readCalls++;
                            chunk.flip();
                            while (chunk.hasRemaining()) {
                                received.write(chunk.get());
                            }
                        }
                    }
                }
            }

            System.out.println("writeCalls>=3: " + (writeCalls >= 3));
            System.out.println("readCalls>=3: " + (readCalls >= 3));
            System.out.println("payload=" +
                    received.toString(StandardCharsets.US_ASCII));
            System.out.println("pending=" + pending.remaining());
        }
    }
}
```

Biên dịch và chạy với JDK 17+:

```bash
javac -encoding UTF-8 ReadinessNotCompletionDemo.java
java ReadinessNotCompletionDemo
```

Output đã được biên dịch và chạy kiểm chứng:

```text
writeCalls>=3: true
readCalls>=3: true
payload=ABCDE
pending=0
```

Giải thích chi tiết:

1. `Pipe.open()` tạo cặp sink/source. Source và sink đều được `configureBlocking(false)` và đăng ký vào cùng selector.
2. `ByteBuffer.wrap("ABCDE"...)` có 5 byte pending. Mỗi lần sink ready, chương trình tạm giới hạn vùng ghi tối đa 2 byte; vì thế **cần ít nhất 3 lần ghi thành công**, dù selector đã báo readiness từ trước.
3. `sink.write(pending)` dịch position theo số byte thật. `finally` trả lại limit cũ. Khi buffer được gửi hết, `outputKey.interestOps(0)` tắt `OP_WRITE` để tránh vòng quay vô ích.
4. Source ready dẫn tới `source.read(chunk)` với buffer chỉ 2 byte, nên muốn nhận đủ 5 byte cần **ít nhất 3 lượt đọc thành công**. Các byte được tích lũy vào `ByteArrayOutputStream`.
5. `selectedKeys().iterator()` được duyệt và mỗi key được `iterator.remove()`; thứ tự key được chọn không xác định, nhưng kết quả cuối cùng vẫn như nhau.
6. Event loop kết thúc dựa trên **hai điều kiện hoàn tất thực**: pending hết byte **và** số byte nhận đạt 5, không phải vì đã gặp một readiness event.
7. Dùng timeout trong ví dụ để báo lỗi nếu không có readiness thay vì đứng chờ vô hạn. Đối với hệ thống thật, timeout phải là chính sách của toàn operation, không chỉ mỗi lần select.

Dòng `writeCalls>=3` và `readCalls>=3` diễn tả **cận dưới**, không cố định số lời gọi native. Đây là lý do output được thiết kế ổn định trên những triển khai channel hợp lệ trong hoàn cảnh kiểm thử; số lần lặp thực tế có thể cao hơn.

## 7. OP_ACCEPT không đảm bảo đã nhận mọi kết nối

Với `ServerSocketChannel` non-blocking, `OP_ACCEPT` báo có khả năng có kết nối chờ. Handler cần gọi `server.accept()` và xử lý kết quả thật:

```java
// Snippet: server là ServerSocketChannel non-blocking
// đã được register OP_ACCEPT, selector đang mở.
for (int i = 0; i < 64; i++) { // Giới hạn để không đói channel khác.
    SocketChannel client = server.accept();
    if (client == null) {
        break; // Không có kết nối nào sẵn để nhận lúc này.
    }
    client.configureBlocking(false);
    client.register(selector, SelectionKey.OP_READ, new ClientState());
}
```

`ClientState` là kiểu do ứng dụng định nghĩa; đây là snippet không biên dịch độc lập. `accept()` có thể trả `null` khi không còn kết nối pending, kể cả đã nhận readiness trước đó.

**Không** dùng `while (server.accept() == null) { }`: đây là busy spin. Không mặc định một `OP_ACCEPT` chỉ ứng với một client; có thể accept nhiều kết nối có sẵn trong một lượt, với hạn mức để event loop còn phục vụ channel khác.

## 8. OP_CONNECT không đồng nghĩa TCP đã kết nối thành công

Với `SocketChannel` non-blocking:

```java
// Snippet: client non-blocking; remoteAddress và selector có sẵn.
boolean connected = client.connect(remoteAddress);
if (connected) {
    client.register(selector, SelectionKey.OP_READ);
} else {
    client.register(selector, SelectionKey.OP_CONNECT);
}
```

Khi `key.isConnectable()`:

```java
// Snippet: key hợp lệ của một SocketChannel.
SocketChannel client = (SocketChannel) key.channel();
try {
    if (client.finishConnect()) {
        key.interestOps(SelectionKey.OP_READ);
    }
} catch (IOException failure) {
    key.cancel();
    client.close();
    // Báo lỗi kết nối, áp dụng retry có kiểm soát nếu cần.
}
```

`OP_CONNECT` nghĩa là có thể **thử hoàn tất** trạng thái kết nối, không có nghĩa handshake đã thành công. `finishConnect()` có thể trả `false` nếu chưa hoàn tất hoặc ném exception khi connect thất bại. Nếu chưa kết nối xong, **không** bắt đầu đọc/ghi application protocol.

Cần phân biệt một TCP connection established với **hoàn tất giao thức ứng dụng** (TLS handshake, xác thực, nhận request...). Selector báo readiness ở tầng channel; những công đoạn cấp trên có state riêng.

## 9. Sự kiện ready là gợi ý tại thời điểm chọn, không là lời hứa lâu dài

Giữa `select()` và thao tác thực tế, tình trạng sẵn sàng có thể thay đổi. Các nguyên nhân phổ biến:

- Một thread khác hoặc một consumer khác đã tiêu thụ dữ liệu trên channel.
- Buffer đích đã đầy hoặc input reader cố đọc 0 byte.
- Buffer nguồn đã hết dữ liệu cần ghi, nên việc báo writable không mang ý nghĩa còn một response chờ gửi.
- Peer đóng kết nối, socket báo lỗi hoặc channel bị hủy/đóng.
- Readiness của cùng key được cập nhật ở vòng selection khác, trong khi ứng dụng vẫn nhìn các bit cũ nếu quản lý selected-key set sai.

Vì vậy các handler phải đọc kết quả `n` thay vì đoán từ `isReadable()`/`isWritable()`. Tín hiệu ready cũng **không bảo đảm lệnh I/O bất kỳ trên channel đó sẽ không bao giờ chặn**: dùng đúng non-blocking mode, đúng loại thao tác, và tránh code blocking khác nằm trong handler.

## 10. Vòng lặp ghi và xử lý zero progress

Trên channel non-blocking, `write` có thể trả 0 khi chưa chuyển byte. Mẫu sai:

```java
while (out.hasRemaining()) {
    socket.write(out); // Có thể luôn trả 0; CPU quay không ngừng.
}
```

Mẫu đúng cho một lần xử lý readiness:

```java
while (out.hasRemaining()) {
    int n = socket.write(out);
    if (n == 0) {
        break; // Chờ lần OP_WRITE phù hợp kế tiếp.
    }
}
if (out.hasRemaining()) {
    key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
} else {
    key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
}
```

Hai đoạn là snippet. Với một queue nhiều buffer, logic chỉ bỏ `OP_WRITE` khi **toàn bộ queue** trống, không chỉ buffer hiện tại. Để tránh starvation, event loop thường có giới hạn byte/time mỗi readiness.

Nếu buffer được tạo ra từ thread khác, giữ queue thread-safe và dùng `selector.wakeup()` để báo thread event loop cập nhật interest/state. Không tự cập nhật cùng một `ByteBuffer` từ hai thread mà không có ownership rõ.

## 11. Khi nào hoàn tất một request?

Một ứng dụng tốt định nghĩa rõ điều kiện kết thúc:

| Tác vụ | Readiness có ý nghĩa gì? | Điều kiện completion của ứng dụng |
| --- | --- | --- |
| Nhận header fixed 4 byte | `OP_READ` có thể cho một phần byte. | Tích lũy đủ 4 byte, giải mã header hợp lệ. |
| Nhận frame length-prefix | `OP_READ` có thể cho một phần hoặc nhiều frame. | Đủ header, payload đủ số byte có giới hạn, kiểm tra hợp lệ. |
| Gửi response 10 KiB | `OP_WRITE` có thể ghi một phần. | Toàn bộ outbound buffers `!hasRemaining()` hoặc queue trống. |
| Mở kết nối TCP | `OP_CONNECT` cho phép thử `finishConnect()`. | `finishConnect() == true`; nếu exception, kết nối thất bại. |
| Chấp nhận client | `OP_ACCEPT` báo có khả năng có pending connection. | Đã nhận và khởi tạo đủ tài nguyên cho từng client được accept. |
| TLS handshake | Readiness cho bước socket cần đọc/ghi. | Máy trạng thái TLS cho biết handshake hoàn tất, không chỉ vì socket writable. |

Với TCP, ngay cả khi socket `write` đã tiêu thụ hết dữ liệu ứng dụng, điều đó **chỉ** có nghĩa byte đã được channel chấp nhận để gửi theo hợp đồng; không bảo đảm peer đã nhận, xử lý hoặc commit chúng. Nếu cần xác nhận nghiệp vụ, thiết kế ACK ở tầng ứng dụng, timeout, request id và xử lý retry/duplicate.

**Không lấy `selectedKeys().isEmpty()` hay `select() > 0` làm điều kiện hoàn tất giao dịch.** Chúng chỉ là trạng thái của cơ chế selection.

## 12. Protocol framing và bộ nhớ khi đọc từng phần

Một giao thức thường cần phân tách message. Ba kiểu hay gặp:

1. **Fixed-size:** mỗi bản ghi N byte; đọc đến khi đủ N rồi xử lý.
2. **Length-prefix:** header cho biết chiều dài payload; kiểm tra độ dài hợp lệ, quota và đủ byte trước khi giải mã.
3. **Delimiter:** đọc cho đến ký tự phân cách, cần giới hạn độ dài để tránh peer không gửi delimiter.

Ví dụ parser cho bản ghi 4-byte `int`:

```java
// Snippet: input đã nhận byte vào pha ghi.
input.flip();
while (input.remaining() >= Integer.BYTES) {
    int value = input.getInt(); // Tương đối: tiêu thụ 4 byte.
    handleValue(value);
}
input.compact(); // Giữ lại 0..3 byte cho lượt read tiếp theo.
```

Cách này tránh `BufferUnderflowException` khi chỉ mới nhận một phần số nguyên. EOF với 1–3 byte dư là dấu hiệu bản ghi bị cắt (trừ khi giao thức có quy định khác).

Nếu một frame có length-prefix do bên ngoài cung cấp, kiểm tra độ dài **trước khi cấp phát** hoặc đọc theo stream với hạn mức. Dữ liệu dài bất thường, decompression bomb, client đọc rất chậm hoặc queue output tăng vô hạn đều có thể gây cạn tài nguyên dù event loop vẫn “non-blocking”.

## 13. Fairness, backpressure và không chặn event loop

Một event-loop thread quản lý nhiều channel. Việc liên tục đọc đến khi hết nguồn dữ liệu trên **một** client bận rộn có thể làm những client khác bị trì hoãn. Hạn mức thường đặt theo:

- Tổng số byte tối đa xử lý trong một readiness.
- Tổng số frame/đơn vị công việc tối đa trong một lượt.
- Deadline hoặc ngân sách thời gian cho handler.
- Dung lượng tối đa của input frame / outbound queue.
- Thời gian rảnh, thời gian hoàn thành frame và tốc độ truyền tối thiểu.

Nếu outbound queue phình to, áp dụng **backpressure**: tạm ngừng đọc/nhận message mới cho connection đó, từ chối/tạm dừng producer, hoặc đóng client theo chính sách. Không tiếp tục cấp phát buffer vô hạn vì Selector vẫn đang cho ready read.

CPU nặng, JDBC đồng bộ, file I/O blocking và xử lý lâu nên chuyển sang worker pool hoặc giải pháp bất đồng bộ phù hợp. Mô hình một thread cho nhiều channel chỉ hữu ích khi handler nhanh, không bị khóa bởi tác vụ khác.

## 14. selectedKeys: xóa đúng để tránh xử lý lặp

Mẫu event loop:

```java
// Snippet: selector và running được khai báo ngoài.
while (running) {
    selector.select();
    Iterator<SelectionKey> it =
            selector.selectedKeys().iterator();

    while (it.hasNext()) {
        SelectionKey key = it.next();
        it.remove();

        if (!key.isValid()) {
            continue;
        }
        if (key.isAcceptable()) {
            onAccept(key);
        }
        if (key.isValid() && key.isConnectable()) {
            onConnect(key);
        }
        if (key.isValid() && key.isReadable()) {
            onRead(key);
        }
        if (key.isValid() && key.isWritable()) {
            onWrite(key);
        }
    }
}
```

Các handler là hàm do ứng dụng định nghĩa, không phải chương trình độc lập. `iterator.remove()` cần thực hiện sau khi lấy key ra khỏi selected-key set: tập này không tự xóa toàn bộ key chỉ vì đã gọi handler. Nếu không, key cũ có thể tiếp tục xuất hiện, tạo việc xử lý lặp và hiểu nhầm một readiness là nhiều sự kiện khác nhau.

Một key có thể được báo ready nhiều ops cùng lúc. Handler đầu có thể đóng channel hoặc cancel key, nên cần kiểm tra lại `isValid()` giữa các bước có rủi ro thay đổi vòng đời.

`selector.select()` cũng có thể trả 0 do wakeup/interrupt/đóng hoặc những lý do được API cho phép. Không biến `select() == 0` thành “peer disconnect” hoặc vòng lặp spin vô hạn; xử lý pending tasks, shutdown và timeout theo trạng thái ứng dụng.

## 15. Readiness API không phải completion API

Java cung cấp nhiều nhóm abstraction:

| Nhóm | Mô hình | Điều ứng dụng phải làm |
| --- | --- | --- |
| `Selector` + non-blocking `SocketChannel` | **Readiness**: chọn channel có khả năng thao tác. | Gọi `read/write`, kiểm tra `n`, giữ state, lặp khi cần. |
| Blocking `InputStream`/`Socket` | Lệnh có thể đợi dữ liệu. | Vẫn phải lặp nếu cần đủ N byte; read không hứa đầy buffer. |
| `AsynchronousSocketChannel` | Gửi yêu cầu I/O bất đồng bộ, nhận completion bằng `Future`/`CompletionHandler`. | Xử lý số byte thực hoàn tất và tiếp tục nếu muốn đủ frame. |
| `FileChannel` | File channel hỗ trợ positional I/O và buffer. | Không thể register như `SelectableChannel` với selector; vẫn kiểm tra partial transfer. |

Ngay cả với *completion callback* của API bất đồng bộ, một completion riêng lẻ có thể biểu thị **chỉ một phần** của message lớn hơn. Completion của lời gọi I/O và completion của request ứng dụng không nhất thiết trùng nhau.

Không nên chuyển sang `AsynchronousSocketChannel` chỉ vì muốn “mỗi event luôn gửi trọn buffer”; ứng dụng vẫn cần framing, backpressure, timeout, xử lý kết thúc và trạng thái tiến trình.

## 16. Những sai lầm phổ biến

| Nhận định hoặc lỗi | Vì sao sai | Cách sửa |
| --- | --- | --- |
| “`OP_READ` ⇒ đọc đủ 1 request.” | TCP là byte stream; dữ liệu có thể phân mảnh hoặc gộp. | Parser theo framing, tích lũy bytes theo `remaining()`. |
| “`OP_WRITE` ⇒ gửi hết response.” | Socket có thể nhận một phần hoặc 0 byte. | Lặp theo `hasRemaining()`, giữ pending buffers. |
| “`OP_CONNECT` ⇒ TCP đã kết nối.” | Cần `finishConnect()`; có thể thất bại. | Kiểm tra return/exception rồi mới xử lý protocol. |
| “`OP_ACCEPT` ⇒ accept luôn được client.” | Non-blocking `accept()` có thể trả null. | Kiểm tra null, quay về selector; cho phép nhận nhiều client. |
| “`write() > 0` ⇒ peer đã xác nhận dữ liệu.” | Channel chỉ cho biết số byte đã tiêu thụ từ buffer gửi. | Định nghĩa ACK cấp ứng dụng nếu cần cam kết nghiệp vụ. |
| “Nếu ready thì read/write không thể trả 0.” | Readiness có thể thay đổi; không phải bảo đảm số byte. | Xử lý n==0 và chờ readiness sau. |
| “Cứ tiếp tục write khi trả 0.” | Vòng spin bận làm CPU tăng. | Break, giữ state, đợi `OP_WRITE`. |
| “`clear()` sau mỗi read.” | Có thể mất byte chưa đủ frame. | Dùng `compact()` để giữ dữ liệu dở dang. |
| “Dùng capacity để biết bao nhiêu byte còn lại.” | Sau flip, `limit` mới là ranh giới hợp lệ. | Sử dụng `remaining()` và `hasRemaining()`. |
| “Luôn bật `OP_WRITE` cho mọi client.” | Socket thường writable ngay cả khi không có dữ liệu. | Tắt khi queue output trống. |
| “Một event loop có thể xử lý task blocking lớn mà không ảnh hưởng client khác.” | Một thread bị giữ thì các channel cùng selector phải đợi. | Đặt budget và tách tác vụ blocking ra worker. |
| “FileChannel đăng ký với Selector được.” | `FileChannel` không triển khai `SelectableChannel`. | Dùng API phù hợp cho file I/O. |

## 17. Bài tập thực hành

**Bài 1 — Tích lũy frame 10 byte.** Sử dụng `ByteBuffer.allocate(16)`, mô phỏng bốn đợt đọc: 3, 1, 4, 2 byte. Sau từng đợt, in `position/limit/remaining`; chỉ giải mã frame khi đủ 10 byte. Giải thích tại sao không thể dựa vào `OP_READ` đầu tiên.

**Bài 2 — Mô phỏng partial write.** Tạo outbound buffer chứa 11 byte. Giới hạn mỗi lần write tối đa 3 byte (dùng kỹ thuật giảm `limit` trong demo). Đếm số lượt cần thiết và kiểm tra nội dung cuối. Với 11 byte cần **ít nhất 4 lượt ghi**; nếu channel còn ghi ít hơn hạn mức thì số lượt thực tế có thể cao hơn.

**Bài 3 — Thay Pipe bằng TCP.** Dùng `ServerSocketChannel` và `SocketChannel` non-blocking cho local loopback. Cài đặt OP_ACCEPT, OP_READ, OP_WRITE và state của connection. Cho client cố tình gửi một frame theo hai lần ghi; server phải trả lời chỉ khi đã thu đủ.

**Bài 4 — Không giữ OP_WRITE vô ích.** Tạo queue hai buffer. Bật `OP_WRITE` khi queue bắt đầu có dữ liệu, giữ nguyên buffer nếu write trả 0, gỡ OP_WRITE chỉ khi cả hai buffer đã hết. Kiểm tra CPU lúc server nhàn rỗi.

**Bài 5 — EOF giữa frame.** Với giao thức header 4 byte + payload, cho bên gửi đóng sau 2 byte header. Xác định lỗi truncate, không nhầm EOF với read == 0. Bổ sung giới hạn chiều dài và timeout để phòng client gửi thiếu.

**Bài 6 — Công bằng giữa các client.** Tạo hai channel, A liên tục gửi dữ liệu còn B thỉnh thoảng gửi frame ngắn. So sánh độ trễ của B khi handler A xử lý mọi byte vô hạn và khi chỉ xử lý tối đa N byte hoặc M frame mỗi vòng selection.

**Bài 7 — Completion vs business ACK.** Viết sequence diagram gồm client `write` hoàn tất, server nhận byte, server commit vào database, server gửi ACK. Chỉ rõ thời điểm nào có thể retry và cần idempotency key để tránh xử lý trùng sau mất ACK.

### Câu hỏi tự kiểm tra

1. Vì sao `isReadable() == true` vẫn có thể đi cùng `read() == 0` hoặc `read() == -1`?
2. Vì sao `isWritable()` không cho biết đã gửi bao nhiêu byte và peer đã nhận chưa?
3. Một frame có 100 byte nhưng lần read trả 60 thì phải làm gì với 40 byte còn thiếu?
4. Tại sao nên `compact()` thay `clear()` khi parser còn byte chưa xử lý?
5. Khi nào nên bật/gỡ bit `OP_WRITE` khỏi `interestOps`?
6. Điều kiện nào mới chứng minh `finishConnect()` hoàn tất?
7. Một event loop có nhiều channel có giải quyết tự động CPU-heavy handler không?
8. Completion callback của API bất đồng bộ có đồng nghĩa một request nghiệp vụ đã hoàn thành không?

## 18. Tham khảo

- [Java SE 21 — Selector](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/Selector.html): cơ chế chọn readiness, tập key và wakeup.
- [Java SE 21 — SelectionKey](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/SelectionKey.html): ready/interest ops và trạng thái đăng ký.
- [Java SE 21 — SocketChannel](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/SocketChannel.html): non-blocking connect/read/write, EOF và kết quả truyền byte.
- [Java SE 21 — ByteBuffer](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/ByteBuffer.html): position, limit, remaining, flip, compact.
- [Java SE 21 — AsynchronousSocketChannel](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/AsynchronousSocketChannel.html): completion handler/Future và I/O bất đồng bộ.

**Ghi nhớ:** `Selector` cho biết **có cơ hội thực hiện I/O**, không xác nhận hoàn tất thao tác. Thành công phải được quyết định bằng **giá trị trả về thật, byte còn thiếu, state của protocol và kết quả nghiệp vụ**. Giữ trạng thái qua nhiều lượt selection, tránh busy spin, giới hạn tài nguyên và chỉ gọi một request là hoàn tất khi toàn bộ điều kiện của nó đã thực sự thỏa mãn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="selector-009">Chỉ đăng ký OP_WRITE khi còn dữ liệu cần gửi</a>

<details>
<summary>Xem chi tiết</summary>

# Chỉ đăng ký OP_WRITE khi còn dữ liệu cần gửi trong Java NIO Selector

## 1. Vì sao OP_WRITE thường gây vòng lặp tốn CPU?

Trong Java NIO, `SelectionKey.OP_WRITE` yêu cầu `Selector` theo dõi xem `SocketChannel` có khả năng nhận thêm byte từ ứng dụng hay không. Với socket TCP đã kết nối, bộ đệm gửi của hệ điều hành thường còn chỗ, nên channel có thể được báo **writable** hầu như liên tục.

Nếu mọi socket đều đăng ký `OP_WRITE` từ khi kết nối và giữ nguyên cờ đó cả lúc không có dữ liệu gửi, một thread Selector có thể chạy vòng lặp rất nhanh:

~~~text
Outbound queue rỗng
        |
interestOps = OP_READ | OP_WRITE
        |
select() trả về vì socket writable
        |
isWritable() là true nhưng không có byte nào cần gửi
        |
quay lại select() → lại writable → xử lý trống → lặp mãi
~~~ 

Đây là **write-readiness spin**: vòng lặp sự kiện liên tục thức dậy nhưng không có công việc hữu ích, làm CPU tăng, tiêu tốn năng lượng và giảm khả năng xử lý những connection thực sự có công việc.

**Nguyên tắc:** chỉ thêm `OP_WRITE` vào `interestOps` khi kết nối còn byte chờ gửi; xóa nó khi hàng đợi outbound đã trống. `OP_READ` thường được giữ riêng nếu kết nối vẫn cần nhận dữ liệu.

## 2. Interest, readiness và write thực tế là ba chuyện khác nhau

| Thành phần | Câu hỏi được trả lời |
| --- | --- |
| `key.interestOps()` | Ứng dụng **muốn theo dõi** những hoạt động nào? |
| `key.readyOps()` hoặc `key.isWritable()` | Selector **đã phát hiện** socket có thể tiến hành ghi ở lượt chọn này? |
| `channel.write(buffer)` | Lần gọi này **thực sự chuyển được bao nhiêu byte** từ buffer? |

Một key có `OP_WRITE` trong interest set không có nghĩa byte đã được gửi. `key.isWritable()` không bảo đảm một lần `write` sẽ chuyển hết buffer. Với non-blocking channel, `write` có thể trả 0 hoặc một số byte nhỏ hơn `buffer.remaining()` trước khi gọi.

Thông tin writable chỉ liên quan đến khả năng ghi ở đầu gửi. Nó **không chứng minh** peer đã nhận, đã xử lý hoặc đã lưu bền dữ liệu; muốn biết điều đó cần giao thức phản hồi hoặc ACK ở tầng ứng dụng.

~~~text
Có byte chờ gửi → Thêm OP_WRITE → Selector báo writable
                                      |
                                      v
                                write(buffer)
                                      |
                      +---------------+---------------+
                      |                               |
                 Còn dữ liệu?                    Đã hết dữ liệu?
                      |                               |
                   Giữ OP_WRITE                   Bỏ OP_WRITE
                   Giữ position                   Giữ OP_READ
                   Chờ lượt sau                   Tránh CPU spin
~~~

## 3. Bật và tắt đúng bit OP_WRITE

Sau khi TCP đã kết nối, không có dữ liệu outbound:

~~~java
key.interestOps(SelectionKey.OP_READ);
~~~

Khi dữ liệu mới xuất hiện, thêm `OP_WRITE` mà **không làm mất** các cờ cũ:

~~~java
key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
~~~

Khi queue trống, chỉ xóa bit `OP_WRITE`:

~~~java
key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
~~~

Đây là các đoạn minh họa nằm trong event loop, với `key` hợp lệ và các import `java.nio.channels.*` thích hợp; chúng chưa là chương trình độc lập.

| Interest set trước | Tác động | Interest set sau |
| --- | --- | --- |
| `OP_READ` | Thêm `OP_WRITE` | `OP_READ | OP_WRITE` |
| `OP_READ | OP_WRITE` | Thêm `OP_WRITE` lần nữa | Không đổi |
| `OP_READ | OP_WRITE` | Xóa `OP_WRITE` | `OP_READ` |
| `OP_READ` | Xóa `OP_WRITE` | Không đổi |

**Sai lầm:** viết `key.interestOps(SelectionKey.OP_WRITE)` khi vẫn muốn đọc. Lời gọi đó **thay thế toàn bộ mask**, xóa `OP_READ`. Nếu chỉ thêm cờ, dùng phép OR bit; nếu chỉ bỏ cờ, dùng phép AND với bit đảo.

Java 17 cũng có `SelectionKey.interestOpsOr(mask)` và `interestOpsAnd(mask)` để cập nhật bit nguyên tử. Tuy nhiên, tính nguyên tử của mask không tự bảo vệ hàng đợi dữ liệu: trong ứng dụng nhiều thread vẫn cần quy tắc ownership, đồng bộ hoặc bàn giao tác vụ.

## 4. ByteBuffer phải giữ nguyên phần chưa ghi xong

Thao tác `channel.write(source)` tiêu thụ byte từ `source.position()` đến trước `source.limit()`, làm tăng position theo số byte thực sự được chuyển.

Giả sử buffer chứa 10 byte. Một lần `write` chuyển 4 byte:

~~~text
Trước: position=0, limit=10, remaining=10
write() trả 4
Sau:   position=4, limit=10, remaining=6
~~~

Sáu byte cuối **vẫn chưa được chuyển khỏi ByteBuffer**. Ứng dụng phải giữ buffer đó và tiếp tục viết khi có readiness lần tới. Không được gọi `clear()`, `rewind()` hay `flip()` tùy tiện giữa hai lần gửi vì có thể làm gửi trùng hoặc đánh mất byte.

Ví dụ một connection có outbound queue:

~~~text
head                                                     tail
  |                                                        |
[Buffer A: còn 6 byte] → [Buffer B: còn 12 byte] → [Buffer C]
                           Outbound vẫn chưa rỗng
                           nên cần OP_WRITE
~~~

Dùng queue giữ các ByteBuffer đã sẵn sàng **đọc** để gửi. `ByteBuffer.wrap(byte[])` và `StandardCharsets.UTF_8.encode(text)` trả buffer có trạng thái phù hợp cho việc gửi; với `ByteBuffer.allocate(...)` rồi `put(...)`, phải `flip()` sau khi put.

Mảnh code cơ bản cho sự kiện writable:

~~~java
while (!outbound.isEmpty()) {
    ByteBuffer head = outbound.peek();
    int n = channel.write(head);

    if (head.hasRemaining()) {
        // Đã ghi một phần hoặc n == 0; giữ head để gửi tiếp.
        break;
    }

    outbound.remove();
}

if (outbound.isEmpty()) {
    key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
}
~~~

Đây là đoạn minh họa trong phương thức xử lý một `SocketChannel` với một `Queue<ByteBuffer> outbound` và `SelectionKey key`; nó không phải chương trình chạy độc lập.

Việc kiểm tra `head.hasRemaining()` là quan trọng nhất. Khi buffer đầu tiên đã hết, loại khỏi queue và thử gửi buffer kế tiếp. Khi socket chưa thể tiến triển, không được quay vòng gọi `write` vô hạn: rời hàm, giữ `OP_WRITE` và chờ lượt readiness sau.

## 5. Một connection có các trạng thái nào?

~~~text
                    Connected
                        |
                        v
                 Queue trống
                 interest=OP_READ
                        |
                  enqueue(payload)
                        |
                        v
                  Queue có byte
            interest=OP_READ | OP_WRITE
                        |
                 Selector writable
                        |
                  write(head)
                        |
             +----------+----------+
             |                     |
        Head còn byte        Head đã gửi hết
             |                     |
         Chờ lần sau          Bỏ head khỏi queue
             |                     |
         Giữ OP_WRITE        Còn head khác?
                                   |
                              +----+----+
                              |         |
                             Có        Không
                              |         |
                         Giữ OP_WRITE   Bỏ OP_WRITE
                                        Giữ OP_READ
~~~

Đây là một **state machine do ứng dụng quản lý**, không phải cơ chế tự động của Selector. Nó phải xử lý cả hai trường hợp `write` tiến triển một phần và `write` trả 0.

Không nên tạo một `SelectionKey` mới mỗi lần có payload. Một channel đã đăng ký trên một selector thường giữ key tương ứng và chỉ thay interest set. Nếu key bị cancel/đóng thì cần kết thúc vòng đời kết nối hoặc tái đăng ký theo điều kiện API.

## 6. Ví dụ Java 17+ chạy độc lập: bật/tắt OP_WRITE khi gửi PING và PONG

Chương trình dùng loopback TCP, một `Selector` và một thread. Client gửi `PING`, server trả `PONG`. Chỉ khi có dữ liệu outbound mới bật `OP_WRITE`, và tắt sau khi gửi xong.

Server dùng `OP_ACCEPT`. Client dùng `OP_CONNECT` nếu `connect()` chưa hoàn tất; nếu kết nối hoàn tất ngay thì chuyển thẳng sang `OP_WRITE`. Các output chỉ được in khi từng chặng đã hoàn thành, nên không phụ thuộc thứ tự các selected key.

Lưu thành `WriteInterestDemo.java`:

~~~java
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

public class WriteInterestDemo {
    public static void main(String[] args) throws IOException {
        try (Selector selector = Selector.open();
             ServerSocketChannel listener = ServerSocketChannel.open();
             SocketChannel client = SocketChannel.open()) {

            listener.configureBlocking(false);
            listener.bind(new InetSocketAddress(
                    InetAddress.getLoopbackAddress(), 0));
            listener.register(selector, SelectionKey.OP_ACCEPT);

            client.configureBlocking(false);
            boolean connected = client.connect(listener.getLocalAddress());
            client.register(selector, connected
                    ? SelectionKey.OP_WRITE
                    : SelectionKey.OP_CONNECT);

            ByteBuffer request = ByteBuffer.wrap(
                    "PING".getBytes(StandardCharsets.US_ASCII));
            ByteBuffer serverInput = ByteBuffer.allocate(4);
            ByteBuffer response = ByteBuffer.wrap(
                    "PONG".getBytes(StandardCharsets.US_ASCII));
            ByteBuffer clientInput = ByteBuffer.allocate(4);

            SocketChannel peer = null;
            long deadline = System.nanoTime()
                    + TimeUnit.SECONDS.toNanos(10);

            try {
                while (clientInput.hasRemaining()
                        && System.nanoTime() < deadline) {
                    selector.select(250);
                    Iterator<SelectionKey> it =
                            selector.selectedKeys().iterator();

                    while (it.hasNext()) {
                        SelectionKey key = it.next();
                        it.remove();

                        if (!key.isValid()) {
                            continue;
                        }

                        if (key.isAcceptable()) {
                            SocketChannel accepted = listener.accept();
                            if (accepted != null) {
                                accepted.configureBlocking(false);
                                accepted.register(
                                        selector, SelectionKey.OP_READ);
                                peer = accepted;
                                key.interestOps(0);
                            }
                        } else if (key.isConnectable()) {
                            if (client.finishConnect()) {
                                key.interestOps(SelectionKey.OP_WRITE);
                            }
                        } else if (key.isWritable()) {
                            if (key.channel() == client) {
                                client.write(request);
                                if (!request.hasRemaining()) {
                                    key.interestOps(SelectionKey.OP_READ);
                                    System.out.println(
                                            "client OP_WRITE removed=true");
                                }
                            } else {
                                SocketChannel serverSide =
                                        (SocketChannel) key.channel();
                                serverSide.write(response);
                                if (!response.hasRemaining()) {
                                    key.interestOps(0);
                                    System.out.println(
                                            "server OP_WRITE removed=true");
                                }
                            }
                        } else if (key.isReadable()) {
                            SocketChannel reading =
                                    (SocketChannel) key.channel();

                            if (reading == client) {
                                int n = client.read(clientInput);
                                if (n < 0) {
                                    throw new EOFException(
                                            "Server closed before PONG");
                                }
                            } else {
                                int n = reading.read(serverInput);
                                if (n < 0) {
                                    throw new EOFException(
                                            "Client closed before PING");
                                }
                                if (!serverInput.hasRemaining()) {
                                    serverInput.flip();
                                    String message =
                                            StandardCharsets.US_ASCII
                                                    .decode(serverInput)
                                                    .toString();
                                    System.out.println(
                                            "server received=" + message);
                                    key.interestOps(SelectionKey.OP_WRITE);
                                }
                            }
                        }
                    }
                }

                if (clientInput.hasRemaining()) {
                    throw new IOException("Timed out waiting for PONG");
                }

                clientInput.flip();
                System.out.println("client received="
                        + StandardCharsets.US_ASCII.decode(clientInput));
            } finally {
                if (peer != null) {
                    peer.close();
                }
            }
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac WriteInterestDemo.java
java WriteInterestDemo
~~~

Stdout mong đợi khi môi trường cho phép kết nối loopback và hoàn thành I/O:

~~~text
client OP_WRITE removed=true
server received=PING
server OP_WRITE removed=true
client received=PONG
~~~

Phân tích:

1. Client đăng ký `OP_CONNECT` khi connection còn pending; nếu hoàn tất ngay thì đăng ký `OP_WRITE` vì đã có sẵn `PING`.
2. Client ghi buffer `PING`. Nếu viết được ít hơn 4 byte, buffer còn `remaining()` và `OP_WRITE` vẫn bật. Khi gửi đủ, chuyển về `OP_READ`.
3. Socket bên server theo dõi `OP_READ`, tích lũy đúng 4 byte. Khi đã có `PING`, nó đổi interest set sang `OP_WRITE` vì chuẩn bị gửi `PONG`.
4. Server ghi hết `PONG` và bỏ `OP_WRITE` bằng cách đặt interest về 0, vì ví dụ chỉ xử lý một request/response.
5. Client đọc đủ 4 byte `PONG`, kết thúc vòng lặp và in kết quả.
6. Các selected key được loại khỏi tập bằng `Iterator.remove()`; channel và selector được đóng khi kết thúc.

**Điều kiện:** môi trường phải cho phép mở socket TCP loopback và hoạt động trong thời hạn 10 giây. Ví dụ này chỉ minh họa một message cố định mỗi chiều, chưa xử lý queue nhiều message, TLS, giao thức framing hoặc hàng nghìn connection. Nếu môi trường không cho phép networking, sẽ có ngoại lệ thay vì stdout trên.

## 7. Trong server thật: outbound queue và connection state

Với nhiều message, nên gắn state riêng cho mỗi `SelectionKey` bằng `key.attach(state)`. Một dạng thường dùng:

~~~java
final class ConnectionState {
    final java.util.ArrayDeque<ByteBuffer> outbound =
            new java.util.ArrayDeque<>();
    final ByteBuffer inbound = ByteBuffer.allocate(8192);
    long queuedBytes;
}
~~~

Đây là một class minh họa trong dự án lớn hơn; cần import `java.nio.ByteBuffer`.

Ở **thread Selector** khi có message mới:

~~~java
void enqueue(SelectionKey key, ByteBuffer data) {
    ConnectionState state = (ConnectionState) key.attachment();
    if (!data.hasRemaining()) {
        return;
    }

    state.queuedBytes += data.remaining();
    state.outbound.addLast(data);
    key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
}
~~~

Ở **thread Selector** khi key writable:

~~~java
void onWritable(SelectionKey key) throws IOException {
    SocketChannel ch = (SocketChannel) key.channel();
    ConnectionState state = (ConnectionState) key.attachment();

    while (!state.outbound.isEmpty()) {
        ByteBuffer head = state.outbound.peekFirst();
        int n = ch.write(head);
        state.queuedBytes -= n;

        if (head.hasRemaining()) {
            break;
        }
        state.outbound.removeFirst();
    }

    if (state.outbound.isEmpty()) {
        key.interestOps(
                key.interestOps() & ~SelectionKey.OP_WRITE);
    }
}
~~~

Đây là các **mảnh phương thức minh họa**, chưa có `main` hoặc cơ chế bảo vệ full lifecycle. Chúng giả định key hợp lệ và state chỉ do thread Selector thay đổi.

**Ownership của ByteBuffer:** sau khi enqueue, producer không nên sửa backing array/position của payload cho đến khi consumer đã xử lý hoặc sao chép xong. Nếu không, có thể phát sinh race và gửi dữ liệu sai.

Nếu queue đã rỗng, không cần chờ thêm event writable. Khi dữ liệu mới lại đến, bật bit một lần nữa. `OP_READ` vẫn được giữ nếu ứng dụng còn muốn nhận request.

## 8. Backpressure và giới hạn lượng dữ liệu chờ

Tắt `OP_WRITE` giúp tránh CPU spin **khi không có gì để gửi**, nhưng không giải quyết vấn đề **producer tạo dữ liệu nhanh hơn socket tiêu thụ**. Nếu ứng dụng tạo 10 MiB/s nhưng đường truyền chỉ tiêu thụ 1 MiB/s, queue tăng khoảng 9 MiB mỗi giây; đây là ví dụ minh họa, không phải số đo cho mọi hệ thống.

Để tránh cạn bộ nhớ, nên:

- Giới hạn số byte và số message trong queue theo mỗi connection và trên toàn hệ thống.
- Dùng **high-water mark** để dừng hoặc giảm nguồn tạo thêm dữ liệu khi tồn đọng quá cao.
- Dùng **low-water mark** để tiếp tục nhận/đẩy dữ liệu khi backlog giảm.
- Nếu cần, tạm bỏ `OP_READ` để không nhận quá nhiều yêu cầu rồi lại phải tạo thêm response, nhưng chỉ khi giao thức cho phép.
- Giới hạn lượng byte xử lý trong một vòng event loop để một connection có outbound lớn không làm đói những connection khác.
- Có chính sách timeout, từ chối yêu cầu hoặc ngắt kết nối quá chậm nếu queue không thể phục hồi.

Một queue đã được drain hết qua `write` cũng không có nghĩa remote ứng dụng đã xử lý dữ liệu. Cần ACK/response riêng nếu giao thức đòi hỏi xác nhận.

## 9. Khi thread khác tạo outbound data

Trong ứng dụng thực tế, worker thread có thể tạo xong response trong khi selector thread đang ngủ ở `select()`. Đừng để worker tùy tiện sửa `ArrayDeque` của connection, vì `ArrayDeque` không thread-safe.

Mô hình dễ quản lý:

1. Worker tạo payload và thêm **tác vụ enqueue** vào một command queue thread-safe.
2. Worker gọi `selector.wakeup()` để thread đang ở `select()` xử lý tác vụ sớm.
3. Thread Selector lấy tác vụ, đưa payload vào outbound queue của connection, thêm `OP_WRITE` khi cần.
4. Sau khi ghi hết dữ liệu, chính thread Selector bỏ `OP_WRITE`.

~~~text
Worker thread                       Selector thread
     |                                      |
     | tạo response                         | select()
     |                                      |
     +-- commandQueue.add(task) ----------->|
     +-- selector.wakeup() ----------------> | thức dậy
                                            |
                                            +-- xử lý task
                                            +-- outbound.add(payload)
                                            +-- thêm OP_WRITE
                                            +-- chờ writable và write
~~~

`wakeup()` **không** tự gửi byte, không tự thay `interestOps` và cũng không làm queue bất kỳ trở thành thread-safe. Khi task đến trễ sau lúc key đã cancel hoặc connection đóng, phải có chính sách bỏ task hoặc xử lý lỗi an toàn.

`SelectionKey.interestOpsOr(...)` và `interestOpsAnd(...)` có thể hỗ trợ cập nhật bit nguyên tử, nhưng không thay thế đồng bộ dữ liệu queue. Một thread duy nhất sở hữu interest set/queue giúp tránh sai sót khi có nhiều nguồn tạo response.

## 10. Những lỗi phổ biến

### Giữ OP_WRITE kể cả khi không có payload

~~~java
socket.register(selector,
        SelectionKey.OP_READ | SelectionKey.OP_WRITE);
~~~

Đây là mảnh minh họa có `socket` và `selector` đang mở. Khi không có outbound data, socket vẫn thường writable, khiến selector thức dậy liên tục. **Sửa:** mặc định chỉ đăng ký `OP_READ` và bật `OP_WRITE` khi queue có byte.

### Xóa OP_WRITE quá sớm

`channel.write(payload)` có thể chuyển 4 trong 10 byte. Nếu lập tức xóa `OP_WRITE` thì 6 byte còn lại có thể chờ mãi không được gửi. **Sửa:** chỉ xóa khi mọi outbound buffer đã hết `remaining()`.

### Ghi một phần rồi reset buffer

Gọi `clear()` hoặc `rewind()` với buffer đang gửi dở sẽ làm position quay về đầu, có thể gửi trùng dữ liệu. Buffer phải được giữ nguyên trạng thái từ lượt writable trước.

### Busy loop khi write trả 0

~~~java
while (buffer.hasRemaining()) {
    channel.write(buffer);
}
~~~

Với non-blocking channel, vòng trên có thể xoay CPU khi không tiến triển. **Sửa:** nếu còn dữ liệu mà chưa ghi được, quay về Selector và chờ readiness.

### Thay cả interest mask và bỏ mất OP_READ

`key.interestOps(SelectionKey.OP_WRITE)` xóa những bit cũ, không chỉ thêm `OP_WRITE`. Dùng phép OR để thêm bit hoặc chỉ thay toàn bộ mask nếu thực sự muốn thay state.

### Không giới hạn outbound queue

Quản lý `OP_WRITE` đúng vẫn có thể hết RAM nếu remote peer quá chậm. Áp dụng watermarks, queue caps và backpressure.

### Thay đổi queue ở nhiều thread không có đồng bộ

`ArrayDeque` không thread-safe. Nên để Selector thread sở hữu queue, còn worker thread dùng command queue thread-safe và `wakeup()`.

### Nhầm writable với delivery ACK

Khi `write` trả số byte đã chuyển, chưa có bảo đảm peer đã xử lý message. Nếu cần xác nhận end-to-end thì xây giao thức ACK.

## 11. Bài tập kiểm tra

1. Channel đã connected, outbound queue rỗng. Nên quan tâm bit nào? **Thường chỉ `OP_READ`**, không giữ `OP_WRITE`.
2. Có thêm một buffer 200 byte vào queue. Cần làm gì? **Bật `OP_WRITE`**.
3. `write` chuyển 70/200 byte. Tiếp theo? **Giữ 130 byte và `OP_WRITE`**, thử tiếp khi được báo ready.
4. Queue có hai buffer; buffer đầu đã hết nhưng buffer thứ hai còn byte. Được bỏ `OP_WRITE` chưa? **Chưa**.
5. Queue rỗng hoàn toàn. Việc cần làm? **Bỏ `OP_WRITE`**, thường giữ `OP_READ`.
6. `key.interestOps(OP_WRITE)` có giữ bit `OP_READ` không? **Không**; setter thay nguyên mask.
7. Tại sao không gọi `write` liên tiếp vô hạn nếu trả `0`? **Vì non-blocking channel chưa tiến triển, cần đợi readiness**.
8. Tại sao phải giữ `ByteBuffer.position()` giữa các lần write? **Để tiếp tục từ byte chưa chuyển, không lặp lại dữ liệu cũ**.
9. Worker tạo response khi selector đang chờ. Phối hợp ra sao? **Đưa task vào queue an toàn, gọi `wakeup()`, để selector thread bật `OP_WRITE`**.
10. Bật/tắt `OP_WRITE` đã đủ chống cạn RAM chưa? **Chưa**; phải giới hạn outbound queue và tạo backpressure.

**Ghi nhớ:** `OP_WRITE` biểu diễn khả năng ghi, không phải tín hiệu "có dữ liệu cần gửi". Chỉ bật bit khi outbound queue có byte; giữ bit khi `write` mới chuyển một phần; khi mọi buffer đã được tiêu thụ, bỏ `OP_WRITE` để tránh CPU spin. Kết hợp với ByteBuffer ownership, đồng bộ giữa thread và backpressure để có event loop vừa chính xác vừa hiệu quả.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="selector-010">Tránh busy loop, nghẽn xử lý và dùng chung buffer sai cách</a>

<details>
<summary>Xem chi tiết</summary>

# Tránh busy loop, nghẽn xử lý và dùng chung buffer sai cách với Java NIO Selector

## 1. Mục tiêu: event loop phải chờ đúng, xử lý nhanh và giữ dữ liệu đúng chủ sở hữu

`java.nio.channels.Selector` giúp một thread theo dõi nhiều channel không chặn (*non-blocking channel*) và xử lý khi chúng sẵn sàng đọc, ghi, chấp nhận kết nối hoặc hoàn tất kết nối. Hiệu quả của mô hình này phụ thuộc vào ba quyết định quan trọng:

1. **Không quay vòng vô ích (*busy loop*)**: không liên tục gọi `selectNow()` hoặc `read()`/`write()` khi không có tiến triển; không duy trì `OP_WRITE` khi không còn bytes cần gửi.
2. **Không để một kết nối làm nghẽn cả event loop**: một callback/block xử lý chậm, CPU-heavy hoặc gọi API blocking sẽ trì hoãn các channel còn lại.
3. **Không dùng chung buffer mutable sai cách**: `ByteBuffer` chứa cả dữ liệu lẫn `position`, `limit`, `mark`; dùng chung cùng một buffer giữa các kết nối hoặc các worker mà không có quy tắc ownership có thể tạo dữ liệu sai, mất byte hoặc lộ dữ liệu từ kết nối khác.

Một selector *không tự* điều phối công việc nghiệp vụ, backpressure, phân quyền buffer hay memory visibility. Nó chỉ cung cấp thông báo về các thao tác channel **có khả năng** tiến triển. Chương trình phải kết hợp selector, trạng thái kết nối và giao thức đóng/mở interest ops đúng cách.

## 2. Hiểu đúng Selector.select() và readiness

Một vòng lặp selector thông dụng:

~~~java
try (Selector selector = Selector.open()) {
    while (running) {
        selector.select(); // chờ readiness hoặc được đánh thức
        Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
        while (iterator.hasNext()) {
            SelectionKey key = iterator.next();
            iterator.remove();
            if (!key.isValid()) continue;
            // Kiểm tra key.isAcceptable()/isConnectable()/isReadable()/isWritable()
        }
    }
}
~~~

Đây là **khung minh họa** cần đặt trong chương trình có import, biến `running` và xử lý channel thực tế. Đừng dùng khung này như một server hoàn chỉnh: nó chưa đăng ký bất kỳ channel nào nên chưa làm việc hữu ích.

- `selector.select()` chặn cho tới khi có key sẵn sàng, bị `wakeup()`, thread bị interrupt hoặc điều kiện khác theo API. Nó **có thể trả 0**, vì thế không được giả định mọi lần thoát khỏi `select` đều có I/O để làm.
- `selector.select(timeout)` chờ nhiều nhất khoảng thời gian dương chỉ định; khi timeout có thể trả 0. Theo API, **timeout bằng 0 tương đương chờ vô hạn**, không phải polling tức thời.
- `selector.selectNow()` kiểm tra ngay, không chặn. Nếu gọi liên tục trong `while(true)` sẽ tạo polling tốn CPU khi không có dữ liệu.
- `key.readyOps()`/`key.isReadable()` biểu thị sự sẵn sàng do lần selection ghi nhận, **không** chứng minh một thao tác đơn lẻ chắc chắn chuyển byte. Cần kiểm tra `read`/`write` thực tế.
- `key.interestOps(...)` là tập sự kiện ứng dụng muốn theo dõi. Những bit `OP_READ`/`OP_WRITE` không phải lệnh đọc/ghi ngay; chúng quyết định sự kiện nào đánh thức event loop.
- `selector.selectedKeys()` là tập key đã được chọn. Phải xóa key khi xử lý, thường dùng `iterator.remove()`. Nếu để nguyên, cùng một key có thể bị xử lý đi xử lý lại dù không có sự kiện mới.
- Một key có thể sẵn sàng cho nhiều loại thao tác. Phải kiểm tra tính hợp lệ và trạng thái channel sau mỗi thao tác, vì một handler có thể đóng hoặc `cancel()` key.

Phân biệt rõ **selected-key set** (đã được chọn) với **registered-key set** (đã đăng ký). Xóa khỏi selected-key set không hủy đăng ký; `key.cancel()` mới hủy key và channel cần được đóng khi không dùng nữa.

## 3. Ba kiểu busy loop thường gặp

### 3.1. Polling vô hạn bằng selectNow()

**Sai:**

~~~java
while (running) {
    selector.selectNow();
    processReadyKeys(selector);
}
~~~

Đây là pseudocode Java (`processReadyKeys` là hàm do ứng dụng cung cấp). Khi không có readiness, vòng lặp không ngủ mà quay lại ngay, có thể làm một CPU core bận gần như liên tục.

**Tốt hơn:** dùng `select()` khi không có deadline, hoặc `select(timeout)` với một thời hạn thực sự cần thiết (ví dụ để xử lý scheduled task). Nếu có công việc nội bộ do thread khác gửi tới, phối hợp qua một hàng đợi và `selector.wakeup()` khi thêm công việc mới. Không dùng `Thread.sleep(1)` ngẫu nhiên để chữa một kiến trúc polling sai.

### 3.2. Vòng read()==0 và write()==0

Trong non-blocking mode:

~~~java
int count = channel.read(input);
if (count == 0) {
    // Chưa có byte khả dụng ngay; chờ selector.
}
if (count == -1) {
    // Peer đã kết thúc hướng gửi: chuyển trạng thái EOF hoặc đóng.
}
~~~

Đây là đoạn minh họa; `channel` và `input` phải tồn tại. `read()==0` không phải EOF. Nếu `input` không còn chỗ (`!input.hasRemaining()`), còn phải xử lý/compact buffer thay vì cứ đợi đọc tiếp.

Tương tự, non-blocking `write(output)` có thể tiêu thụ **một phần hoặc 0 byte** khi socket chưa đủ khả năng nhận dữ liệu. Nếu `output.hasRemaining()`, giữ nguyên buffer và đăng ký `OP_WRITE`; khi có sự kiện sẵn sàng ghi, thử viết tiếp. Không busy-spin:

~~~java
while (output.hasRemaining()) {
    channel.write(output); // SAI với non-blocking nếu write liên tục trả 0.
}
~~~

Ở blocking mode, việc chờ ghi tiếp có thể phù hợp với lời gọi chặn, nhưng code trên là lỗi phổ biến nếu `channel.configureBlocking(false)` và không có cơ chế chờ readiness.

### 3.3. OP_WRITE luôn bật dù không có dữ liệu gửi

Một socket thường xuyên có khả năng ghi thêm bytes. Nếu đăng ký `OP_WRITE` vô điều kiện:

~~~java
key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
~~~

và tiếp tục giữ bit `OP_WRITE` ngay cả khi outbound queue trống, `select()` có thể trở về liên tục vì socket vẫn writable. Đây là lý do phổ biến khiến event loop ăn CPU dù hệ thống không có tải ứng dụng.

**Quy tắc:** chỉ đăng ký `OP_WRITE` **khi đã có dữ liệu đang chờ gửi**. Sau khi toàn bộ output được ghi hết, gỡ `OP_WRITE`:

~~~java
// Khi outbound vừa chuyển từ rỗng sang có dữ liệu:
key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);

// Trong handler writable, sau khi ghi hết toàn bộ dữ liệu chờ:
key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
~~~

Đây là mã minh họa trong event loop, giả định `key` còn hợp lệ. Nếu channel chỉ phục vụ một request rồi kết thúc, đóng/cancel key khi đã flush thay cho việc giữ `OP_READ` không còn cần thiết. Nếu có nhiều frame outbound, chỉ gỡ `OP_WRITE` khi **toàn bộ queue outbound** đã rỗng, không chỉ một buffer vừa hết.

## 4. Ví dụ chạy được với Java 17+: một event loop, buffer riêng, OP_WRITE có điều kiện

Ví dụ sau có server ở loopback với `ServerSocketChannel` không chặn và một client độc lập dùng `SocketChannel` blocking. Client gửi đúng một request kết thúc bằng newline; server nhận request, chuyển sang chỉ quan tâm `OP_WRITE` khi có response, và đóng socket sau khi gửi hết. Mỗi socket server có một object `Connection` chứa `ByteBuffer`/state riêng trong `SelectionKey.attachment()`.

Tạo tệp **`SelectorSafeDemo.java`**:

~~~java
import java.io.ByteArrayOutputStream;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class SelectorSafeDemo {
    static final class Connection {
        final ByteBuffer input = ByteBuffer.allocate(128);
        final ByteArrayOutputStream request = new ByteArrayOutputStream();
        ByteBuffer output;
    }

    public static void main(String[] args) throws Exception {
        try (Selector selector = Selector.open();
             ServerSocketChannel server = ServerSocketChannel.open()) {
            server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            server.configureBlocking(false);
            server.register(selector, SelectionKey.OP_ACCEPT);

            ExecutorService clients = Executors.newSingleThreadExecutor();
            try {
                Future<String> result = clients.submit(() -> {
                    try (SocketChannel channel = SocketChannel.open()) {
                        channel.connect(server.getLocalAddress());
                        ByteBuffer request = ByteBuffer.wrap(
                                "ping\n".getBytes(StandardCharsets.UTF_8));
                        while (request.hasRemaining()) channel.write(request);
                        channel.shutdownOutput();
                        ByteBuffer response = ByteBuffer.allocate(32);
                        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                        while (true) {
                            int n = channel.read(response);
                            if (n == -1) break;
                            response.flip();
                            while (response.hasRemaining()) bytes.write(response.get());
                            response.clear();
                        }
                        return bytes.toString(StandardCharsets.UTF_8).trim();
                    }
                });

                int completed = 0;
                while (completed < 1) {
                    if (selector.select(2000) == 0) {
                        throw new IOException("Timed out waiting for ready channels");
                    }
                    Iterator<SelectionKey> selected = selector.selectedKeys().iterator();
                    while (selected.hasNext()) {
                        SelectionKey key = selected.next();
                        selected.remove();
                        if (!key.isValid()) continue;
                        if (key.isAcceptable()) {
                            SocketChannel peer = server.accept();
                            if (peer != null) {
                                peer.configureBlocking(false);
                                peer.register(selector, SelectionKey.OP_READ, new Connection());
                            }
                        } else {
                            SocketChannel peer = (SocketChannel) key.channel();
                            Connection state = (Connection) key.attachment();
                            if (key.isReadable()) {
                                int n = peer.read(state.input);
                                if (n < 0) {
                                    throw new IOException("Request truncated before newline");
                                }
                                if (n > 0) {
                                    state.input.flip();
                                    while (state.input.hasRemaining()) {
                                        byte value = state.input.get();
                                        if (value == '\n') {
                                            System.out.println("Server received: " +
                                                    state.request.toString(StandardCharsets.UTF_8));
                                            state.output = ByteBuffer.wrap(
                                                    "PONG\n".getBytes(StandardCharsets.UTF_8));
                                            key.interestOps(SelectionKey.OP_WRITE);
                                            break;
                                        }
                                        state.request.write(value);
                                        if (state.request.size() > 64) {
                                            throw new IOException("Request too long");
                                        }
                                    }
                                    state.input.compact();
                                }
                            }
                            if (key.isValid() && key.isWritable()) {
                                peer.write(state.output);
                                if (!state.output.hasRemaining()) {
                                    key.cancel();
                                    peer.close();
                                    completed++;
                                }
                            }
                        }
                    }
                }
                System.out.println("Client received: " + result.get());
                System.out.println("Completed: " + completed);
            } finally {
                clients.shutdownNow();
            }
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17+:

~~~bash
javac SelectorSafeDemo.java
java SelectorSafeDemo
~~~

Kết quả **đã kiểm tra bằng cách biên dịch và chạy**:

~~~text
Server received: ping
Client received: PONG
Completed: 1
~~~

### Vì sao ví dụ tránh ba lỗi mục tiêu?

1. **Không busy loop:** server dùng `selector.select(2000)` và chỉ xử lý những key sẵn sàng; không gọi `selectNow()` không ngừng. Thời hạn 2 giây ở đây là *demo timeout*, không phải khuyến nghị cố định cho production.
2. **Không giữ `OP_WRITE` vô ích:** client mới kết nối chỉ có interest `OP_READ`. Khi gặp newline và đã tạo response, server đổi interest thành `OP_WRITE`. Sau khi bytes response được ghi hết, server đóng channel. Ở kết nối dài hạn, thay vì đóng sẽ gỡ bit `OP_WRITE` và giữ các interest thích hợp.
3. **Không chia sẻ buffer giữa các kết nối:** một lần `accept()` tạo một `Connection` riêng. `state.input` và `state.output` được dùng trong một event loop duy nhất, qua `key.attachment()`.
4. **Xử lý partial write:** `peer.write(state.output)` chỉ ghi những byte nó có thể nhận ngay. Nếu vẫn còn bytes, buffer giữ nguyên `position` và `OP_WRITE` vẫn bật để lần sẵn sàng tiếp theo viết nốt. Không gọi `clear()` trước khi gửi xong.
5. **Xử lý framing tối thiểu:** request kết thúc bằng newline, giới hạn nội dung 64 byte. Server không giả định một lần `read` luôn đúng một message. Nếu nhận EOF trước newline, báo truncated request.
6. **Selected key đã xử lý được loại khỏi selected set** bằng `selected.remove()`. Việc này khác với `key.cancel()`: hủy đăng ký chỉ diễn ra khi response đã hoàn tất và server chủ động đóng kết nối.

**Giới hạn có chủ đích của demo:** xử lý đúng **một request ASCII ngắn và một response** trên một kết nối, rồi dừng. Khi đã thấy newline, demo không giữ byte thừa để xử lý request tiếp theo; nó không phải bộ parser nhiều message. Client được chạy riêng và dùng blocking I/O để dễ quan sát; chỉ server mới dùng Selector. Mã production cần xử lý đóng tất cả accepted channel ngay cả khi lỗi/timeout, giới hạn số kết nối, bảo vệ queue, parse nhiều frame nếu cần, quản lý cancel, deadline và shutdown toàn server. Nếu sửa thành protocol lâu dài, cần có cấu trúc outbound queue thay vì chỉ một `state.output`.

## 5. Tại sao một handler chậm làm nghẽn tất cả kết nối?

Một event loop sử dụng **một thread** để xử lý nhiều key. Khi đang xử lý key A, thread đó chưa quay lại kiểm tra key B. Nếu code xử lý A làm một việc chặn, mọi kết nối khác cùng selector sẽ đợi, dù socket của chúng đã sẵn sàng.

Ví dụ hành vi nguy hiểm trong handler:

~~~java
if (key.isReadable()) {
    int n = socket.read(buffer);
    // Sau khi đọc, thực hiện xử lý CPU nặng 10 giây,
    // gọi JDBC đồng bộ hoặc yêu cầu HTTP blocking tại đây.
}
~~~

Đây là mã minh họa. Những việc dễ gây nghẽn bao gồm truy vấn DB đồng bộ, HTTP client blocking, `Thread.sleep()`, khóa mutex lâu, phân tích JSON khổng lồ, mã hóa dữ liệu nặng, ghi log xuống I/O chậm hoặc gọi một API bất đồng bộ rồi `future.get()` ngay trên event loop.

Hệ quả quan sát được:

- Latency tăng đồng loạt trên nhiều kết nối, dù mỗi client gửi rất ít dữ liệu.
- Read buffer không được drain kịp khiến kernel receive buffers tăng áp lực; sender có thể bị chậm do TCP flow control.
- Pending output không được flush khiến hàng đợi response tăng, thời gian sống kết nối và tài nguyên bị giữ lâu.
- Timer và timeout application layer có thể chạy muộn nếu chúng được xử lý trong chính thread bị nghẽn.
- Tăng số channel đăng ký lên cùng selector không làm event loop tự có thêm CPU.

**Định hướng xử lý:** event loop chỉ làm I/O non-blocking, parse frame gọn, cập nhật trạng thái nhỏ và lập lịch tác vụ. Các công việc CPU-heavy hoặc blocking được chuyển sang worker pool có giới hạn. Worker trả kết quả bằng một hàng đợi thread-safe; event loop nhận kết quả, tạo outbound buffers, bật `OP_WRITE` và tiếp tục. Không để worker trực tiếp thao tác đồng thời trên buffer mà event loop đang dùng.

## 6. Offload tác vụ nặng mà vẫn giữ ownership và ordering

Một mô hình an toàn có thể là:

~~~text
SocketChannel (non-blocking)
      │ OP_READ
      ▼
Selector thread ── decode frame / kiểm tra giới hạn
      │
      ├─ copy immutable input / chuyển giao sở hữu
      ▼
Bounded worker queue → Worker xử lý blocking/CPU-heavy
      │
      └─ immutable result / outbound bytes
                   ▼
          Thread-safe completion queue
                   │ selector.wakeup()
                   ▼
Selector thread → queue bytes → enable OP_WRITE → write partial → drain
~~~

Các nguyên tắc quan trọng:

- **Chỉ một chủ sở hữu thao tác cursor của read/write buffer tại mỗi thời điểm.** Event loop có thể chuyển một snapshot byte sang worker; sau khi chuyển ownership thật sự, nó không được `compact/clear/put` vào cùng buffer cho đến lúc worker trả.
- **Không gửi vào worker một tham chiếu `ByteBuffer` mutable rồi tái sử dụng ngay để đọc tiếp.** Worker có thể thấy `position/limit` thay đổi giữa xử lý; kết quả giải mã có thể sai hoặc gây `BufferUnderflowException`.
- **Hàng đợi bounded** giữ số tác vụ chờ có giới hạn. Nếu worker không theo kịp, giảm/tạm tắt `OP_READ` cho những connection cần throttling hoặc phản hồi quá tải theo policy. Không dùng queue vô hạn vì memory có thể tăng tới OOM.
- **Kết quả worker cần gắn với connection generation/id** và phải được bỏ qua nếu socket/key đã đóng hoặc request đã timeout. Tránh gửi response muộn lên channel mới tái sử dụng ID.
- **Giữ thứ tự response** nếu giao thức đòi hỏi: nhiều worker hoàn tất không theo thứ tự request. Dùng sequence number, queue theo connection, hoặc giới hạn một tác vụ đang chạy trên mỗi connection.
- **Giao thức đánh thức Selector:** thread khác có thể thêm kết quả vào completion queue và gọi `selector.wakeup()`. Khi `select()` trở về, event loop drain queue rồi cập nhật interest ops. Đừng dựa vào việc một writer ngoài thread gọi `key.interestOps(...)` mà event loop chắc chắn được đánh thức ngay.
- **Đừng gọi `Future.get()` trong event loop** để đợi worker: điều đó biến offload thành blocking, phá lợi ích của selector.

Ví dụ mô tả cách chuyển thông báo giữa worker và event loop, **không phải chương trình độc lập**:

~~~java
// Ở worker:
completedResults.add(new Result(connectionId, requestId, outputBytes));
selector.wakeup();

// Ở event loop, sau khi select() trở về:
Result done;
while ((done = completedResults.poll()) != null) {
    // Kiểm tra key còn hợp lệ và request còn cần kết quả.
    // Tạo ByteBuffer riêng từ outputBytes hoặc chuyển ownership rõ ràng.
    // Đưa vào outbound queue của chính connection.
    // Đăng ký OP_WRITE nếu queue vừa từ rỗng sang có dữ liệu.
}
~~~

`completedResults` phải là queue thread-safe, `Result` là kiểu do ứng dụng định nghĩa. Việc gọi `selector.wakeup()` **không chuyển dữ liệu thay queue** và không giúp một `ByteBuffer` chia sẻ tùy tiện trở thành an toàn đa luồng.

## 7. Không dùng chung ByteBuffer sai cách

`ByteBuffer` có hai loại trạng thái cần bảo vệ:

1. **Cursor metadata**: `position`, `limit`, `mark`. `get()`, `put()`, `flip()`, `clear()`, `compact()` có thể làm thay đổi chúng.
2. **Backing bytes**: mảng byte hoặc vùng direct/mapped memory. Hai buffer khác object cũng có thể dùng chung storage.

Một lỗi thường gặp:

~~~java
ByteBuffer sharedInput = ByteBuffer.allocate(8192);

// Trong mọi handler OP_READ cho nhiều socket:
int n = channel.read(sharedInput);
sharedInput.flip();
consume(sharedInput);
sharedInput.clear();
~~~

Đây là mã minh họa: `consume` do ứng dụng cung cấp. Khi nhiều connection dùng chung `sharedInput` và dữ liệu đến đan xen, rất dễ đọc byte của client A như byte của client B. Dù các handler chạy tuần tự trên một event loop, nếu parser cần giữ phần frame chưa đủ qua các lần đọc, việc `clear` buffer toàn cục giữa hai connection **làm mất trạng thái dang dở**. Dùng chung buffer còn trở nên nguy hiểm hơn khi offload vào worker.

### 7.1. Một connection, một inbound state và outbound queue

Trong chương trình lớn, có thể lưu trạng thái tại `SelectionKey.attachment()`:

~~~java
final class ConnectionState {
    final ByteBuffer input = ByteBuffer.allocate(8192);
    final Deque<ByteBuffer> outbound = new ArrayDeque<>();
    boolean inputEof;
    long queuedBytes;
}
~~~

Đây là **class minh họa**; cần import `ByteBuffer`, `Deque`, `ArrayDeque`. Nó phù hợp với event loop sở hữu độc quyền; **`ArrayDeque` không phải hàng đợi thread-safe**. Nếu worker có response, hãy post kết quả qua completion queue; event loop mới cập nhật `outbound` và `queuedBytes`.

Mỗi connection có parser state riêng: vùng dữ liệu chưa đủ một frame, chiều dài frame mong đợi, trạng thái đóng, watermark và deadline. Không được chỉ lưu một `ByteBuffer` dùng chung cho mọi connection rồi hy vọng Selector tự tách dữ liệu theo key.

### 7.2. duplicate(), slice() và asReadOnlyBuffer() không tự copy bytes

- `duplicate()` tạo view có con trỏ riêng nhưng **chia sẻ nội dung**.
- `slice()` tạo view cho một vùng còn lại hoặc vùng xác định nhưng **chia sẻ backing storage**.
- `asReadOnlyBuffer()` cấm ghi qua view đó, nhưng alias gốc vẫn có thể ghi vào vùng nhớ.
- `ByteBuffer.wrap(byteArray)` chia sẻ mảng với caller; caller giữ mảng có thể sửa nội dung ngoài buffer.
- `ByteBuffer.allocateDirect(...)` chỉ nói đến kiểu cấp phát, không bảo đảm thread safety.

**Ví dụ dễ nhầm:**

~~~java
ByteBuffer original = ByteBuffer.wrap(new byte[] {1, 2, 3});
ByteBuffer readOnly = original.asReadOnlyBuffer();
original.put(0, (byte) 9);
System.out.println(readOnly.get(0)); // 9
~~~

Đoạn trên có thể đặt trong một method và không cần concurrency để chứng minh rằng read-only view **không sở hữu bản sao immutable**. Nếu thread khác ghi đồng thời vào backing bytes trong khi reader đọc, phải có cơ chế đồng bộ hoặc ownership; nếu cả dữ liệu lẫn cursor hoàn toàn bất biến sau khi publish an toàn, nhiều reader có thể dùng view riêng.

### 7.3. flip()/compact() khi nhận frame chưa đầy

Khi đọc được một phần khung, không tự `clear()` để bỏ byte chưa tiêu thụ:

~~~java
int n = channel.read(input);
if (n > 0) {
    input.flip();
    decodeCompleteFrames(input);
    input.compact(); // giữ phần frame chưa hoàn chỉnh, dồn về đầu vùng ghi
}
~~~

Đây là đoạn minh họa; `decodeCompleteFrames` phải chỉ tăng `position` qua **frame hoàn chỉnh**, để lại byte thiếu trong `remaining()`. `compact()` sao chép byte còn lại về đầu và đặt position để lần đọc mới nối tiếp chúng. Nếu parser đã tiêu thụ toàn bộ dữ liệu, `clear()` là lựa chọn hợp lệ; nếu buffer đầy vì frame lớn hơn capacity, cần chính sách giới hạn, tăng buffer có kiểm soát hoặc streaming parser.

Trong non-blocking write, **không** `clear()`/`compact()` output trước khi `write` trả hết dữ liệu; phần còn lại trong buffer là chính xác những byte chưa được channel tiêu thụ.

## 8. Backpressure: nếu producer nhanh hơn socket

Giả sử worker tạo response 20 MB/s nhưng client chỉ nhận 1 MB/s. Nếu server cứ đưa response mới vào queue mà không giới hạn, pending bytes sẽ tăng dần cho tới khi dùng hết bộ nhớ. Selector chỉ báo khả năng ghi tạm thời; nó không tự giới hạn số bytes chờ trong chương trình.

Cần quy định:

| Tín hiệu | Biện pháp |
| --- | --- |
| Outbound queue vừa có bytes | Bật `OP_WRITE` cho connection |
| `write(...) > 0` nhưng còn bytes | Giữ buffer với position mới và tiếp tục ở sự kiện writable tới |
| `write(...) == 0` | Dừng lượt ghi; đợi `OP_WRITE`, không busy-spin |
| Outbound queue rỗng | Gỡ `OP_WRITE`; chỉ giữ interest còn cần |
| Queued bytes vượt **high watermark** | Tạm dừng nhận thêm công việc, giới hạn producer hoặc phản hồi quá tải |
| Queued bytes giảm xuống dưới **low watermark** | Khôi phục `OP_READ`/tiếp nhận công việc theo chính sách |
| Client không tiến triển vượt deadline | Hủy công việc, giải phóng queue/buffer và đóng connection phù hợp |

Hai watermark tránh bật/tắt liên tục khi queue dao động quanh một ngưỡng duy nhất. Khi tắt `OP_READ`, dữ liệu có thể vẫn được giữ trong kernel receive buffer và peer bị giảm tốc do TCP flow control; điều này không có nghĩa ứng dụng đã từ chối mọi dữ liệu mới trên mạng. Cần thêm giới hạn số connection, kích thước frame và thời gian chờ.

Một handler writable nhiều buffer nên giới hạn **write budget** mỗi vòng, ví dụ tối đa một lượng byte hoặc số buffer, để không chiếm hết event loop khi một client truyền dữ liệu liên tục. Sau khi dùng hết budget mà queue còn dữ liệu, để interest `OP_WRITE` bật cho lượt sau. Điều này hỗ trợ fairness.

## 9. Fairness và tránh “một key độc chiếm”

Ngay cả khi mọi channel không chặn, event loop vẫn có thể bị nghẽn nếu handler của một key:

- Đọc vô hạn cho đến khi `read() == 0` trên một connection luôn có dữ liệu.
- Giải mã hàng trăm nghìn frame trong một lượt selection.
- Gửi toàn bộ outbound queue lớn trong một lần xử lý key.
- Gọi xử lý CPU-heavy hoặc blocking I/O sau mỗi read.
- Giữ một monitor khóa lâu khiến event loop phải đợi thread khác.

Một chiến lược là đặt **budget mỗi key**: tối đa N lượt read, M frame decode hoặc X bytes write trên mỗi vòng. Khi hết budget, lưu state để tiếp tục ở vòng xử lý sau, không làm mất những byte đã đọc. Budget nên phối hợp với mức độ tải thật, fairness, deadlines và backpressure.

Không thể chỉ cắt vòng và quên dữ liệu đã có trong inbound buffer: phần frame đã hoàn chỉnh còn nằm trong bộ nhớ có thể sẽ **không** sinh thêm socket-readiness nếu kernel chưa có byte mới. Nếu đã ngừng xử lý khi còn nhiều frame nội bộ, cần lên lịch một lượt xử lý tiếp theo qua task queue của event loop, hoặc xử lý đến điểm an toàn trước khi kết thúc callback.

## 10. Lỗi EOF, cancel key và vòng đời connection

Trong `OP_READ` handler:

~~~java
int n = channel.read(input);
if (n == -1) {
    // Peer đã kết thúc hướng gửi.
    // Hoàn tất frame cuối hoặc báo truncated frame; quyết định có gửi response không.
}
~~~

`-1` nghĩa là EOF **hướng nhận**, khác với `0` khi chưa có byte. Không giữ `OP_READ` vô hạn và tiếp tục đọc `-1` mà không đổi trạng thái; tùy nền tảng có thể gây readiness loop. Nếu còn response cần gửi sau khi peer half-close, gỡ `OP_READ` và giữ `OP_WRITE` tới khi flush xong. Khi không còn dữ liệu cần trao đổi, đóng channel và hủy key.

Cần xử lý cả:

- `IOException` khi peer reset hoặc network lỗi.
- `ClosedChannelException` nếu channel đóng khi đăng ký hoặc thao tác.
- `CancelledKeyException` nếu key đã cancel rồi vẫn gọi `isReadable/interestOps`.
- `ClosedSelectorException` khi selector bị đóng trong lúc vòng lặp vẫn dùng.
- Connection non-blocking đang connect: `OP_CONNECT` và `finishConnect()` cần được xử lý đúng trước read/write.
- Listener accept: `ServerSocketChannel.accept()` trong non-blocking mode có thể trả `null` ngay cả khi có sự kiện `OP_ACCEPT`; không dereference null.
- EOF/truncated frame: parser phải biết framing của protocol; TCP không giữ ranh giới giữa các lần `write`/`read`.

Với mỗi lỗi, cleanup cần gỡ connection khỏi registry, hủy key, đóng channel và giải phóng các buffer/reference/pending task. Nếu đã offload worker, kết quả hoàn thành muộn phải bị bỏ khi connection không còn hợp lệ.

Không dùng `selector.keys().clear()` hay chỉ `iterator.remove()` để giả định đã đóng socket. Hủy key và đóng channel là thao tác riêng; resource cleanup phải có owner rõ ràng.

## 11. Đọc hiện tượng CPU cao và xử lý sự cố

Nếu process Java dùng nhiều CPU nhưng lưu lượng mạng thấp, kiểm tra trước:

1. Vòng event loop dùng `selectNow()` liên tục hay `select(positiveTimeout)` cực nhỏ không cần thiết?
2. `OP_WRITE` có luôn bật dù outbound queue rỗng?
3. Handler có quên `selectedIterator.remove()` khiến key được xử lý lặp?
4. Sau EOF, có tiếp tục quan tâm `OP_READ`/đọc lại `-1` không?
5. Các lần `read/write` trả 0 có tạo retry trong vòng `while` không?
6. Có exception được bắt rồi bỏ qua, dẫn đến cùng lỗi lặp mãi mà không đóng key/channel không?
7. Có logic `selector.wakeup()` gọi quá thường xuyên từ worker mà không có kết quả mới không?
8. Có lượng dữ liệu lớn ở parser/outbound làm event loop không tới lượt các kết nối còn lại không?

Nếu latency cao nhưng CPU không nhất thiết tăng, kiểm tra tác vụ blocking trong handler, worker pool quá tải, lock contention, outbound queue tích tụ, connection count và thời gian xử lý mỗi vòng selection.

Các chỉ số hữu ích: số channel đăng ký, số selected key theo operation, số lần `select()` trả 0, thời gian mỗi callback, pending bytes theo connection, outbound high-watermark events, worker queue depth, thời gian I/O không tiến triển và số kết nối đóng do timeout.

Những chỉ số này là gợi ý quan sát; chúng không thuộc bản thân API `Selector`. Ứng dụng cần tự ghi metric hoặc dùng profiler/thống kê JVM phù hợp.

## 12. Quy tắc thiết kế tổng hợp

| Nhu cầu | Cách làm phù hợp | Sai lầm cần tránh |
| --- | --- | --- |
| Chờ I/O nhiều socket | `Selector.select()` với channel non-blocking | Polling `selectNow()` vô hạn |
| Không có outbound bytes | Chỉ giữ `OP_READ` (hoặc interest khác cần thiết) | Luôn bật `OP_WRITE` |
| Có bytes chưa ghi hết | Giữ per-connection buffer, để `OP_WRITE` bật | Busy-spin khi write trả 0; clear output quá sớm |
| Nhận frame từng phần | Per-connection input/decoder state, `compact()` đúng lúc | Một buffer toàn cục cho mọi connection |
| Task CPU/DB blocking | Offload vào bounded worker pool | `Future.get()` trong event loop |
| Nhận kết quả worker | Queue thread-safe + `selector.wakeup()` + owner event loop cập nhật interest | Worker mutate cùng ByteBuffer/SelectionKey state thiếu giao thức |
| Tốc độ sinh > tốc độ gửi | Backpressure, watermark, deadline | Outbound queue không giới hạn |
| Connection liên tục có dữ liệu | Budget read/write/parse mỗi lượt | Để một key chiếm event loop vô hạn |
| EOF hoặc lỗi | Cập nhật state, hủy interest, đóng khi đúng lúc | Lặp đọc -1 hoặc bỏ sót cleanup |

Một sơ đồ trách nhiệm dễ kiểm chứng: **event loop sở hữu selector, selection keys và buffer trạng thái I/O; worker xử lý bản dữ liệu đã chuyển giao an toàn; kết quả quay về qua queue thread-safe; event loop tự cập nhật interest ops và thực hiện I/O**. Có thể dùng thiết kế khác, nhưng phải chứng minh được ownership, visibility và giới hạn tài nguyên tương đương.

## 13. Câu hỏi tự kiểm tra

1. `selectNow()` trả 0 liên tục, vòng `while(true)` gọi lại ngay. Hậu quả là gì? **Busy loop**, có thể dùng CPU cao mà không xử lý dữ liệu.
2. `select(0)` có tương đương `selectNow()` không? **Không**; timeout 0 của `select(long)` tương đương chờ không giới hạn, còn `selectNow()` không chặn.
3. Vì sao `OP_WRITE` có thể tạo CPU cao khi output queue rỗng? Vì socket thường xuyên writable, nên selector liên tục chọn key mà handler không có bytes để gửi.
4. `SocketChannel.write(buffer)` trả 0 có nghĩa channel đã đóng không? **Không**; ở non-blocking mode thường là tạm thời không ghi được ngay. Giữ buffer, đợi readiness.
5. Hai kết nối cùng dùng một `ByteBuffer` nhưng chỉ có một event-loop thread, có chắc an toàn không? **Không**: frame chưa hoàn chỉnh và `position/limit` của connection này có thể bị thao tác connection kia ghi đè/làm mất.
6. Dùng `duplicate()` hoặc `asReadOnlyBuffer()` có tạo vùng dữ liệu độc lập không? **Không**; chúng vẫn chia sẻ backing bytes với các alias.
7. Vì sao chuyển JDBC blocking sang worker rồi ngay lập tức gọi `future.get()` trong event loop vẫn chậm? Vì event-loop thread vẫn bị chặn chờ kết quả.
8. Khi worker tạo response, nó có nên trực tiếp `put()` vào outbound buffer mà event loop đang `write()` không? **Không**, trừ khi thiết kế có giao thức ownership/đồng bộ được chứng minh an toàn.
9. Đã gửi hết một buffer nhưng outbound queue còn buffer khác, có gỡ `OP_WRITE` không? **Chưa**; chỉ gỡ khi không còn pending bytes.
10. `read() == -1` rồi vẫn giữ `OP_READ` để đọc lại mãi có ổn không? **Không**; đó là EOF hướng nhận, phải đổi trạng thái/interest hoặc đóng khi giao thức hoàn tất.
11. Tại sao queue worker và output cần bounded? Vì producer nhanh hơn consumer nếu không giới hạn sẽ làm tăng memory không kiểm soát.
12. Có thể giới hạn read budget rồi bỏ qua các frame đã có sẵn trong input buffer không? **Không**; phải lưu parser state và lên lịch tiếp tục xử lý để không bỏ sót frame.

## Tài liệu tham khảo

- [Java SE 17 — Selector](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/Selector.html): `select()`, `selectNow()`, timeout, selected keys, `wakeup()` và yêu cầu đồng bộ.
- [Java SE 17 — SelectionKey](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/SelectionKey.html): interest/ready operations, attachment, cancel.
- [Java SE 17 — SocketChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/SocketChannel.html): non-blocking read/write, connect, EOF.
- [Java SE 17 — ByteBuffer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html): con trỏ, `flip`, `compact`, view chia sẻ backing storage.
- [Java SE 17 — ServerSocketChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/ServerSocketChannel.html): accept không chặn, cấu hình channel.


</details>

- [Quay lại đầu trang](#back-to-top)
