<a id="back-to-top"></a>

# I/O bất đồng bộ

## Menu
- [Phân biệt asynchronous I/O với non-blocking I/O](#asynchronous-io-001)
- [AsynchronousFileChannel và vị trí thao tác tường minh](#asynchronous-io-002)
- [Future và CompletionHandler](#asynchronous-io-003)
- [AsynchronousSocketChannel và AsynchronousServerSocketChannel](#asynchronous-io-004)
- [AsynchronousChannelGroup và nhóm luồng thực thi](#asynchronous-io-005)
- [Không sửa hoặc tái sử dụng buffer khi thao tác chưa hoàn tất](#asynchronous-io-006)
- [Tiếp tục đọc ghi khi kết quả chỉ hoàn tất một phần](#asynchronous-io-007)
- [Xử lý lỗi, ngắt, timeout và đóng channel](#asynchronous-io-008)
- [Giới hạn số thao tác đồng thời và áp lực lên tài nguyên](#asynchronous-io-009)
- [Lựa chọn blocking, selector hoặc API bất đồng bộ theo bài toán](#asynchronous-io-010)

## <a id="asynchronous-io-001">Phân biệt asynchronous I/O với non-blocking I/O</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt asynchronous I/O với non-blocking I/O trong Java

## 1. Mục tiêu và bối cảnh

Khi thiết kế chương trình I/O hiệu quả, hai thuật ngữ **asynchronous I/O** (*I/O bất đồng bộ*) và **non-blocking I/O** (*I/O không chặn*) thường bị dùng thay cho nhau. Chúng có liên quan nhưng **không đồng nghĩa**.

- **Non-blocking** mô tả hành vi của **lời gọi I/O cụ thể**: khi chưa thể đọc/ghi ngay, thao tác có thể trả về tức thời với tín hiệu “chưa tiến triển”, thay vì chặn thread chờ tài nguyên sẵn sàng. Trong Java NIO, ví dụ là `SocketChannel` hoặc `Pipe.SourceChannel` được cấu hình `configureBlocking(false)`.
- **Asynchronous I/O** mô tả mô hình **khởi phát thao tác rồi nhận thông báo/kết quả khi hoàn tất**: phương thức nộp yêu cầu I/O có thể trả về trước khi thao tác hoàn tất; kết quả đến qua `Future` hoặc `CompletionHandler`. Trong Java NIO.2, ví dụ là `AsynchronousFileChannel` và `AsynchronousSocketChannel`.

Hai mô hình khác nhau ở **thời điểm chương trình biết I/O sẵn sàng** và **thời điểm biết I/O đã hoàn tất**. Cả hai có thể giúp tránh dành một thread chỉ để chờ từng kết nối, nhưng hành vi thực tế và cách tổ chức code khác nhau.

Sau bài, bạn có thể:

1. Phân biệt blocking, non-blocking, synchronous, asynchronous theo **hợp đồng API**.
2. Hiểu `Selector` cùng `SelectionKey.OP_READ/OP_WRITE` trong non-blocking I/O là cơ chế **readiness**, còn `Future/CompletionHandler` trong asynchronous I/O là cơ chế **completion**.
3. Viết ví dụ Java 17+ minh họa non-blocking `Pipe.SourceChannel` và asynchronous `AsynchronousFileChannel`.
4. Hiểu vì sao `Future.get()` có thể chặn, vì sao một callback không bảo đảm toàn bộ ứng dụng không dùng thread chặn I/O, và vì sao readiness chưa chứng minh đã đọc đủ message.
5. Chọn mô hình phù hợp cho server socket, đọc/ghi file, tác vụ batch và thư viện có giao diện blocking.

## 2. Bốn khái niệm thường bị trộn lẫn

**Blocking** là khi thread gọi bị giữ lại cho tới khi điều kiện thao tác xảy ra (hoặc lỗi/timeout/hủy). Ví dụ, `InputStream.read()` thông thường có thể chờ dữ liệu.

**Non-blocking** là khi một lời gọi I/O trả về mà không chờ tài nguyên sẵn sàng. Nếu chưa thể đọc/ghi ở thời điểm đó, kết quả có thể là `0`; ứng dụng phải biết **khi nào thử lại**, thường nhờ `Selector`.

**Synchronous** là khi bên gọi nhận trực tiếp kết quả của thao tác đang thực hiện trước khi tiếp tục phần xử lý cần kết quả đó. Ví dụ `FileChannel.read(buffer)` trả số byte đã đọc khi lời gọi hoàn tất.

**Asynchronous** là khi bên gọi nộp công việc và kết quả được cung cấp qua cơ chế hoàn tất sau đó. Ví dụ `AsynchronousFileChannel.read(buffer, position)` trả `Future<Integer>`, hoặc phiên bản có `CompletionHandler`.

Bốn từ không tạo thành hai cặp đối nghĩa tuyệt đối. Một ứng dụng có thể chạy **I/O blocking trên worker thread** để giao diện gọi ở tầng trên có vẻ bất đồng bộ; thao tác trong worker vẫn là blocking. Một API async trên Java cũng có thể được triển khai bằng cơ chế đồng bộ bên dưới cộng thread pool; hợp đồng async nói về **cách bên gọi giao tiếp với kết quả**, không bảo đảm kernel không bao giờ block bất kỳ thread nào.

| Khía cạnh | Non-blocking I/O | Asynchronous I/O |
| --- | --- | --- |
| Câu hỏi chính | “Có thể đọc/ghi **ngay bây giờ** không?” | “Thao tác tôi đã yêu cầu **hoàn tất** chưa?” |
| Tín hiệu | Readiness của channel; `read/write` có thể trả `0` | Completion: số byte/kết quả hoặc lỗi qua `Future`/handler |
| API Java tiêu biểu | `SocketChannel.configureBlocking(false)`, `Selector` | `AsynchronousSocketChannel`, `AsynchronousFileChannel` |
| Chủ thể tiếp tục thao tác | Ứng dụng gọi `read()/write()` khi được báo ready | Provider thực hiện yêu cầu; ứng dụng nhận kết quả completion |
| Cần event loop? | Thường có selector loop khi nhiều channel | Có thể callback/Future; không bắt buộc một `Selector` |
| Kết quả đọc | Lời gọi `read(ByteBuffer)` trực tiếp trả `int` | `Future<Integer>` hoặc `completed(Integer, attachment)` |
| Dữ liệu chưa đủ | Giữ trạng thái và thử đọc tiếp sau readiness | Nộp thao tác đọc mới sau khi thao tác trước hoàn tất |
| Cách tránh chặn caller | Không gọi thao tác I/O blocking trên event loop | Không gọi `Future.get()` ngay trên event loop |

**Không có API nào hứa luôn đọc đủ một record ứng dụng chỉ trong một lần gọi.** Kết quả byte thực tế, EOF, kích thước buffer, framing và lifecycle vẫn phải được kiểm tra.

## 3. Readiness và completion: sơ đồ trình tự

### 3.1. Non-blocking với `Selector`: readiness trước, thực hiện I/O sau

```text
Thread/event loop             Selector              Socket/Pipe
      |                           |                      |
      | register(OP_READ)         |                      |
      |-------------------------->|                      |
      | select()                  |                      |
      |-------------------------->|                      |
      |      (đợi readiness)      |                      |
      |<------ readable key ------|<--- dữ liệu đến -----|
      | read(buffer)              |                      |
      |------------------------------------------------->|
      |<------------------ int n (n>0, 0 hoặc -1) --------|
      | xử lý n byte; giữ phần record chưa đủ              |
```

`Selector.select()` **có thể chặn thread selector** khi chờ sự kiện; còn bản thân thao tác `read()/write()` của **non-blocking channel** không đợi tài nguyên sẵn sàng theo cách của blocking mode. Vì vậy nói “non-blocking nghĩa là chẳng thread nào bao giờ chờ” cũng sai.

Nếu chương trình không muốn chặn ở `select()`, có `selectNow()` để kiểm tra readiness mà không đợi. Tuy nhiên gọi `selectNow()` liên tục trong vòng lặp bận có thể tiêu hao CPU.

### 3.2. Asynchronous: nộp yêu cầu trước, nhận completion sau

```text
Thread ứng dụng          AsynchronousFileChannel / provider
      |                                 |
      | read(dst, offset)               |
      |-------------------------------->|
      |<---------- Future --------------|
      | tiếp tục công việc khác          |
      |                                 | thực hiện I/O
      |             (khi hoàn tất)      |
      |<----------- completion ---------|
      | get() hoặc completed()/failed()  |
      | xử lý số byte thực sự đọc         |
```

Với overload trả `Future`, `future.get()` sẽ **chờ** nếu kết quả chưa sẵn sàng; gọi nó ngay sau `read` biến luồng xử lý ở caller thành “submit rồi đợi”, dù API I/O vẫn là asynchronous.

Với overload `CompletionHandler`, provider gọi `completed(result, attachment)` hoặc `failed(error, attachment)` khi thao tác hoàn tất. Callback là điểm nhận completion; code callback nên ngắn gọn và không chạy tác vụ CPU nặng chặn thread xử lý completion trong thời gian dài.

### 3.3. Reactor và Proactor là cách gọi mô hình, không phải kiểu Java bắt buộc

**Reactor** là cách tổ chức ứng dụng quanh event readiness (selector) rồi handler tự thực hiện `read/write`. **Proactor** là cách mô tả mô hình nhận completion sau khi hệ thống thực hiện I/O được yêu cầu. Hai thuật ngữ hữu ích để thảo luận kiến trúc, nhưng không nên suy ra một-to-một với mọi chi tiết triển khai JDK/OS hoặc coi tên API là bằng chứng về cơ chế kernel.

## 4. Non-blocking I/O trong Java NIO hoạt động thế nào?

Những kênh như `SocketChannel`, `ServerSocketChannel`, `DatagramChannel` và hai đầu của `Pipe` là `SelectableChannel`. Muốn đăng ký channel với selector:

```java
channel.configureBlocking(false);
SelectionKey key = channel.register(
        selector, SelectionKey.OP_READ);
```

Đây là snippet **không độc lập**, giả định `channel` thuộc `SelectableChannel` phù hợp và `selector` đang mở; cần import cùng xử lý ngoại lệ.

Nếu đăng ký khi channel còn blocking mode, `register` ném `IllegalBlockingModeException`. Nếu dùng operation không được `channel.validOps()` hỗ trợ (ví dụ đăng ký `OP_WRITE` cho `Pipe.SourceChannel`), có thể ném `IllegalArgumentException`.

Các trạng thái điển hình:

- `ReadableByteChannel.read(ByteBuffer)` khi đọc được dữ liệu: trả số byte `n > 0`.
- Đối với non-blocking channel, nếu chưa có byte nào sẵn sàng: **có thể trả `0`**.
- Nếu input đã đến EOF: trả `-1`; điều này **không phải** chỉ là chưa có dữ liệu.
- `WritableByteChannel.write(ByteBuffer)` có thể ghi ít hơn số byte còn trong buffer hoặc trả `0`. Byte chưa ghi vẫn phải được giữ lại để retry khi được báo writable.

`SelectionKey.isReadable()` không bảo đảm `read` sẽ trả đủ byte cho toàn bộ message. Readiness có thể thay đổi giữa thời điểm selector chọn key và thời điểm xử lý I/O, nhất là khi nhiều thread cùng sử dụng channel.

### Một số quy tắc của selector event loop

1. Gọi `configureBlocking(false)` **trước** khi đăng ký.
2. Chỉ đăng ký các bit có trong `validOps()`.
3. Sau `selector.select()`, duyệt `selectedKeys()` và **remove mỗi key đã xử lý khỏi tập selected**. `remove` khác với `key.cancel()`; cancel hủy registration.
4. Kiểm tra `key.isValid()` trước các thao tác trên key có thể đã bị hủy.
5. Với socket, chỉ giữ `OP_WRITE` khi có dữ liệu cần gửi; socket thường writable phần lớn thời gian, đăng ký mãi dễ khiến event loop bận.
6. Nếu thread khác bổ sung công việc trong lúc selector đang đợi, dùng queue và `selector.wakeup()` đúng cách.
7. Đừng xem kết quả `select(timeout) == 0` là EOF; đó là trạng thái chọn, không phải kết quả đọc channel.

## 5. Ví dụ Java 17+ hoàn chỉnh: non-blocking `Pipe` với `Selector`

Để tránh phụ thuộc vào cổng mạng, ví dụ dùng `Pipe` trong cùng process. `Pipe.SourceChannel` được đặt non-blocking và đăng ký `OP_READ`; phía sink ghi thông điệp ASCII `HELLO`. Chương trình gom đủ 5 byte dù mỗi lần `read` có thể nhận một phần.

Lưu tên **`NonBlockingSelectorDemo.java`**:

```java
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

public class NonBlockingSelectorDemo {
    public static void main(String[] args) throws IOException {
        Pipe pipe = Pipe.open();

        try (Pipe.SourceChannel source = pipe.source();
             Pipe.SinkChannel sink = pipe.sink();
             Selector selector = Selector.open()) {

            source.configureBlocking(false);
            source.register(selector, SelectionKey.OP_READ);

            byte[] expected = "HELLO".getBytes(StandardCharsets.US_ASCII);
            ByteBuffer outbound = ByteBuffer.wrap(expected);

            // Sink vẫn blocking; lượng byte rất nhỏ.
            while (outbound.hasRemaining()) {
                sink.write(outbound);
            }

            ByteArrayOutputStream received = new ByteArrayOutputStream();
            ByteBuffer inbound = ByteBuffer.allocate(8);

            while (received.size() < expected.length) {
                int keys = selector.select(2000);
                if (keys == 0) {
                    throw new IOException("Timeout while waiting for read");
                }

                Iterator<SelectionKey> it =
                        selector.selectedKeys().iterator();

                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    it.remove();

                    if (!key.isValid() || !key.isReadable()) {
                        continue;
                    }

                    inbound.clear();
                    int n = source.read(inbound);
                    if (n == -1) {
                        throw new IOException("Unexpected EOF");
                    }
                    if (n > 0) {
                        inbound.flip();
                        while (inbound.hasRemaining()) {
                            received.write(inbound.get());
                        }
                    }
                }
            }

            System.out.println("bytes=" + received.size());
            System.out.println("text="
                    + received.toString(StandardCharsets.US_ASCII));
        }
    }
}
```

Chạy với JDK 17 trở lên:

```bash
javac NonBlockingSelectorDemo.java
java NonBlockingSelectorDemo
```

Stdout khi pipe/selector hoạt động bình thường:

```text
bytes=5
text=HELLO
```

**Điểm quan trọng:**

- `source.configureBlocking(false)` ảnh hưởng lệnh đọc của **source channel**; sink vẫn blocking. Không phải cứ có `Selector` là tất cả channel trong chương trình trở thành non-blocking.
- `selector.select(2000)` có thể **chặn event-loop thread tối đa 2 giây** để chờ readiness. Đây là hành vi dự kiến của selector.
- Khi có `OP_READ`, chương trình **tự gọi** `source.read(inbound)`. Nếu chưa có byte tại chính thời điểm đọc, non-blocking source có thể trả 0.
- Chỉ đưa các byte thực đọc vào `received`. `ByteBuffer.flip()` giới hạn vùng vừa nhận; `clear()` chuẩn bị vòng đọc mới.
- `try-with-resources` đóng hai đầu pipe và selector.
- Ví dụ có hạn timeout để tránh chờ vô hạn; với hệ thống quá tải, `IOException` timeout có thể xuất hiện thay vì stdout thành công.

Đây là ví dụ của **readiness**, không có `Future<Integer>` hay callback completion nào thực hiện thay việc `read` cho ứng dụng.

## 6. Asynchronous I/O trong Java NIO.2

Các API đáng chú ý:

| API | Cách nhận kết quả | Dùng cho |
| --- | --- | --- |
| `AsynchronousFileChannel.read(dst, position)` | `Future<Integer>` | Đọc file theo offset |
| `AsynchronousFileChannel.read(dst, position, attachment, handler)` | `CompletionHandler` | Đọc file theo offset, nhận callback |
| `AsynchronousFileChannel.write(src, position)` | `Future<Integer>` | Ghi file theo offset |
| `AsynchronousFileChannel.write(src, position, attachment, handler)` | `CompletionHandler` | Ghi file và nhận callback |
| `AsynchronousSocketChannel.connect(remote)` | `Future<Void>` hoặc handler | Kết nối TCP |
| `AsynchronousSocketChannel.read(dst)` | `Future<Integer>` hoặc handler | Đọc socket |
| `AsynchronousSocketChannel.write(src)` | `Future<Integer>` hoặc handler | Ghi socket |

`AsynchronousFileChannel` sử dụng **offset rõ ràng**, không phải con trỏ `position()` chia sẻ theo kiểu `FileChannel`. Điều này thuận lợi cho thao tác ở nhiều vị trí nhưng khiến bạn phải quản lý offset chính xác, tránh vùng ghi chồng nhau không được phối hợp.

Theo hợp đồng:

- Nộp yêu cầu I/O bằng `read/write` không có nghĩa dữ liệu đã đọc/ghi hoàn tất.
- Kết quả thành công thường là **số byte thực sự xử lý**; có thể nhỏ hơn kích thước buffer yêu cầu, nên vẫn cần lặp hoặc nộp yêu cầu tiếp.
- Lỗi cần được xử lý qua `ExecutionException` của `Future.get()` hoặc `CompletionHandler.failed(...)`.
- Trong lúc thao tác bất đồng bộ chưa hoàn tất, **không tự ý thay đổi nội dung hay `position/limit` của buffer đang tham gia thao tác**. Tái sử dụng một buffer cho nhiều thao tác pending có thể khiến nội dung/trạng thái không xác định.
- Với một số asynchronous socket channel, nộp thêm một read/write cùng hướng khi operation trước còn pending có thể gây `ReadPendingException` / `WritePendingException`; thiết kế phải theo dõi trạng thái pending.
- Đóng channel trong lúc operation đang pending cần được xử lý bằng cancellation/exception và chính sách cleanup rõ ràng.

`Future.get()` có thể chặn thread gọi; `Future.isDone()` chỉ báo trạng thái hoàn tất, gọi nó liên tục trong một vòng busy-wait cũng có thể tiêu tốn CPU.

## 7. Ví dụ Java 17+ hoàn chỉnh: asynchronous file read với `Future`

Chương trình tạo một file nhỏ, mở `AsynchronousFileChannel`, gửi yêu cầu đọc trước, nhận `Future<Integer>`, sau đó cố ý gọi `get()` để kiểm tra kết quả cho mục đích học API.

Lưu **`AsyncFutureFileDemo.java`**:

```java
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Future;

public class AsyncFutureFileDemo {
    public static void main(String[] args) throws Exception {
        Path file = Files.createTempFile("nio-async-", ".txt");

        try {
            Files.writeString(file, "HELLO", StandardCharsets.US_ASCII);

            try (AsynchronousFileChannel channel =
                    AsynchronousFileChannel.open(
                            file, StandardOpenOption.READ)) {

                ByteBuffer buffer = ByteBuffer.allocate(8);

                Future<Integer> operation = channel.read(buffer, 0);
                System.out.println("request submitted");

                // Cố tình chờ để minh họa Future; caller có thể block.
                int n = operation.get();

                buffer.flip();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);

                System.out.println("bytes=" + n);
                System.out.println("text="
                        + new String(bytes, StandardCharsets.US_ASCII));
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

Lệnh:

```bash
javac AsyncFutureFileDemo.java
java AsyncFutureFileDemo
```

Stdout khi file I/O hoàn tất thành công:

```text
request submitted
bytes=5
text=HELLO
```

Giải thích:

1. `channel.read(buffer, 0)` nộp yêu cầu đọc tối đa 8 byte từ offset 0 và **trả về Future**.
2. In `request submitted` chỉ nói rằng lời gọi submit đã trả về; không chứng minh thao tác đã hoàn tất ngay lúc đó.
3. `operation.get()` có thể chặn cho đến khi thao tác hoàn tất; vì vậy đoạn ví dụ này **không phải event-loop không chặn từ đầu tới cuối**.
4. File chỉ chứa 5 byte, `n == 5` khi thành công. `buffer.flip()` chuyển sang đọc những byte đã được channel đưa vào.
5. `try-with-resources` đóng channel, sau đó file tạm được xóa.

Nếu chỉ muốn minh họa tính đúng đắn với file nhỏ, `Future.get()` thuận tiện. Trong GUI hoặc một event loop xử lý hàng nghìn kết nối, gọi `get()` trực tiếp trên thread quan trọng sẽ làm mất lợi thế mà kiến trúc đang tìm kiếm.

## 8. Ví dụ Java 17+ hoàn chỉnh: asynchronous với `CompletionHandler`

Khi không muốn viết code chờ `Future` ngay sau mỗi lời gọi, dùng overload có `CompletionHandler`. Bài học dùng `CountDownLatch` **chỉ để chương trình demo đợi callback xong trước khi in kết quả và đóng file**. Trong ứng dụng server thật, bạn thường nối tiếp tác vụ bằng callback, event queue hoặc thành phần điều phối thay vì chặn một thread để đợi latch.

Lưu **`AsyncCallbackFileDemo.java`**:

```java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class AsyncCallbackFileDemo {
    public static void main(String[] args) throws Exception {
        Path file = Files.createTempFile("nio-callback-", ".txt");

        try {
            Files.writeString(file, "JAVA", StandardCharsets.US_ASCII);

            try (AsynchronousFileChannel channel =
                    AsynchronousFileChannel.open(
                            file, StandardOpenOption.READ)) {

                ByteBuffer dst = ByteBuffer.allocate(8);
                CountDownLatch done = new CountDownLatch(1);
                AtomicInteger readCount = new AtomicInteger(-2);
                AtomicReference<Throwable> error = new AtomicReference<>();

                channel.read(dst, 0L, "file-read",
                        new CompletionHandler<Integer, String>() {
                    @Override
                    public void completed(Integer n, String attachment) {
                        readCount.set(n);
                        done.countDown();
                    }

                    @Override
                    public void failed(Throwable exc, String attachment) {
                        error.set(exc);
                        done.countDown();
                    }
                });

                System.out.println("submitted=file-read");

                if (!done.await(10, TimeUnit.SECONDS)) {
                    throw new IOException("Timeout waiting for callback");
                }
                if (error.get() != null) {
                    throw new IOException("Asynchronous read failed",
                            error.get());
                }

                dst.flip();
                byte[] bytes = new byte[dst.remaining()];
                dst.get(bytes);

                System.out.println("bytes=" + readCount.get());
                System.out.println("text="
                        + new String(bytes, StandardCharsets.US_ASCII));
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

```bash
javac AsyncCallbackFileDemo.java
java AsyncCallbackFileDemo
```

Stdout khi completion thành công:

```text
submitted=file-read
bytes=4
text=JAVA
```

Trong handler, `completed(Integer, String)` nhận số byte thực đọc và attachment `"file-read"` đã nộp, còn `failed(Throwable, String)` nhận lỗi. `CountDownLatch` bảo đảm thread in kết quả chỉ tiếp tục sau khi handler xử lý trạng thái. `AtomicInteger` và `AtomicReference` làm nơi chuyển kết quả đơn giản trong demo; khi triển khai thật, thay bằng state và cơ chế điều phối phù hợp.

**Không dùng callback để chứng minh không có thread nào chờ trong hệ thống.** Provider có thể sử dụng pool bên dưới để thực hiện công việc I/O, và code demo có `await` chặn thread chính. Lợi ích là API cho phép người gọi **không buộc phải chờ ở chính lời gọi `read`** và có một con đường xử lý hoàn tất tách biệt.

Một timeout ở `await` không tự đảm bảo I/O đã bị hủy. Trong chương trình thực tế cần chính sách hủy/đóng và điều phối những completion đến muộn; không tự giải phóng hoặc tái sử dụng buffer khi còn thao tác pending.

## 9. Vì sao “async” không đồng nghĩa “không có blocking ở mọi nơi”?

Có ít nhất ba tầng cần xét:

1. **API phía người gọi:** phương thức trả `Future` hoặc nhận handler, nên caller có thể tiếp tục việc khác thay vì đợi toàn bộ I/O.
2. **Cách provider thực thi:** có thể dựa trên I/O bất đồng bộ của hệ điều hành, hoặc có thể dùng thread pool để thực hiện những lời gọi chặn ở tầng dưới. Hợp đồng Java không hứa một chiến lược triển khai duy nhất cho mọi hệ điều hành/filesystem/JVM.
3. **Cách ứng dụng tiêu thụ kết quả:** gọi `Future.get()` ngay, `CountDownLatch.await()`, hoặc thực hiện code chậm trong callback có thể làm thread tương ứng bị giữ lại.

Ví dụ kiến trúc:

```text
Luồng HTTP/event loop
    |
    | submit async file read
    v
Provider / executor: xử lý I/O
    |
    | kết quả/exception
    v
CompletionHandler hoặc Future
    |
    v
Hệ thống phản hồi / scheduler
```

Mô hình trên có thể giữ luồng HTTP rảnh trong lúc I/O đang tiến hành, nhưng không có nghĩa implementation không dùng thread pool. Muốn biết công nghệ thực tế đang dùng, cần kiểm tra tài liệu runtime, profiling và môi trường hoạt động.

Tương tự, đưa `Files.readAllBytes(path)` vào `ExecutorService.submit(...)` khiến **caller không đợi ngay** khi submit, nhưng worker vẫn thực hiện blocking I/O. Đây là **asynchronous orchestration** ở tầng ứng dụng, khác với bảo đảm của một **non-blocking channel**. Hai cách đều có thể hợp lý nếu mục tiêu và hạn mức thread được quản lý tốt.

## 10. File I/O: vì sao `FileChannel` không dùng `Selector`?

`FileChannel` hỗ trợ `ByteBuffer`, `position`, `size`, `map`, `transferTo` và `force`. Nhưng **`FileChannel` không phải `SelectableChannel`**: nó không có `configureBlocking(false)` hay `register(selector, ops)`.

```java
FileChannel file = FileChannel.open(path, StandardOpenOption.READ);

// Hai dòng sau KHÔNG biên dịch:
// file.configureBlocking(false);
// file.register(selector, SelectionKey.OP_READ);
```

Đây là **đoạn minh họa sai có chủ đích**, không phải ví dụ runnable; nó chỉ rõ vì sao không thể dùng `Selector` cho `FileChannel`.

Nếu cần xử lý file mà không buộc thread gọi chờ:

- `AsynchronousFileChannel` cung cấp `Future`/`CompletionHandler`.
- Hoặc chạy file I/O đồng bộ trong worker thread/executor và chuyển kết quả trở lại event loop.
- Nếu mục đích là **nhận thông báo file được sửa**, cân nhắc `WatchService` để quan sát sự kiện thay đổi thư mục; nó không phải selector cho từng byte của file và không thay thế việc đọc file thực tế.

**Không được kết luận** “API mang tên NIO” thì mọi channel đều non-blocking hoặc đều đăng ký selector được. NIO chứa cả giao diện đồng bộ, không chặn theo readiness, và bất đồng bộ theo completion.

## 11. TCP server: chọn mô hình nào?

### 11.1. Non-blocking `SocketChannel` + `Selector`

Server thường có một hoặc vài event-loop thread:

1. Mở `ServerSocketChannel` và gọi `configureBlocking(false)`.
2. `register(selector, OP_ACCEPT)` để quan sát kết nối mới.
3. Khi key acceptable, gọi `accept()`; đối tượng `SocketChannel` mới cũng phải `configureBlocking(false)` rồi đăng ký.
4. Khi readable, gọi `read(ByteBuffer)`, kiểm tra `n > 0 / n == 0 / n == -1`, ghép các chunk thành message.
5. Khi có dữ liệu muốn gửi, thực hiện `write(ByteBuffer)`. Nếu ghi dở, giữ phần còn lại và bật `OP_WRITE`; tắt `OP_WRITE` khi output queue rỗng.
6. Khi key bị cancel hoặc channel đóng, giải phóng tài nguyên đúng vòng đời.

`OP_READ` chỉ thông báo có thể thử đọc. Không coi một key ready là hoàn tất request HTTP hay một frame binary protocol.

### 11.2. `AsynchronousServerSocketChannel` / `AsynchronousSocketChannel`

Các asynchronous socket API dùng `Future` hoặc `CompletionHandler` cho `accept`, `connect`, `read`, `write`. Chương trình không dùng `Selector` trực tiếp để chọn key readiness; nó nhận **completion** của từng thao tác đã nộp.

Ví dụ ý tưởng (snippet):

```java
socket.read(buffer, buffer, new CompletionHandler<Integer, ByteBuffer>() {
    @Override
    public void completed(Integer n, ByteBuffer dst) {
        // n: số byte nhận được, hoặc -1 cho EOF.
        // Sau khi xử lý phần đọc được, nộp tiếp một read nếu cần.
    }

    @Override
    public void failed(Throwable error, ByteBuffer dst) {
        // Ghi nhận lỗi và kết thúc/đóng kết nối theo chính sách.
    }
});
```

Đây là **snippet không độc lập**: `socket` là `AsynchronousSocketChannel` đã kết nối, `buffer` là `ByteBuffer` hợp lệ, cần import và chính sách quản lý vòng đời. Nó minh họa điểm completion, không phải một TCP server hoàn chỉnh.

Cả hai hướng đều cần framing, backpressure, giới hạn bộ nhớ, timeout, cancellation, quan sát lỗi và cleanup. Chọn dựa trên API/framework đã dùng, khả năng vận hành, hiệu năng đo được và độ phức tạp dễ kiểm soát; không có kết luận mọi trường hợp asynchronous luôn nhanh hơn non-blocking.

## 12. Khác biệt quan trọng giữa readiness và completion khi xử lý buffer

| Tình huống | Readiness / non-blocking | Completion / async |
| --- | --- | --- |
| Ai tiến hành `read`? | Handler của ứng dụng gọi `channel.read(buffer)` khi ready | Ứng dụng submit `channel.read(...)`; provider xử lý yêu cầu |
| Lúc nào `position` của buffer thay đổi? | Trong lời gọi `read()` khi có byte | Trong quá trình thao tác async; chỉ nên truy cập/flip khi hoàn tất |
| Không có byte ngay lúc này | `read` có thể trả `0` | Yêu cầu có thể còn pending; kết quả nhận sau |
| Kết thúc input | `read` có thể trả `-1` | Completion mang kết quả `-1` khi EOF |
| Ghi một phần | `write` có thể ghi một phần/0; bật OP_WRITE và retry | Completion mang số byte thực ghi; nộp write tiếp cho phần còn lại |
| Lỗi | Thường ném ngay từ `read/write` | Future thất bại hoặc callback `failed` |
| Buffer có thể tái sử dụng? | Sau lời gọi đồng bộ đã hoàn tất, với trạng thái đúng | Sau **completion** và sau khi ứng dụng tiêu thụ dữ liệu đúng cách |

Một lỗi nghiêm trọng ở async là:

```java
Future<Integer> result = asyncFile.read(buffer, 0);
// Sai nếu I/O còn pending:
buffer.clear();
buffer.put(newData);
```

Đây là **mã sai có chủ đích**, vì buffer đang thuộc thao tác đọc còn pending. Nếu đổi nội dung, position hoặc limit quá sớm, kết quả có thể sai hoặc trạng thái buffer không được xác định. Đợi completion trước khi `flip()`, parse hoặc tái sử dụng buffer.

Ngược lại, trong non-blocking channel, lệnh `read(buffer)` đã trả thì lần gọi ấy hoàn tất; nhưng có thể chỉ mới đọc được một phần message. Chương trình vẫn phải giữ đủ state để nhận những byte còn thiếu trong lần readiness sau.

## 13. Timeout, cancellation và lỗi

### Non-blocking

- `selector.select(timeout)` đợi readiness tối đa trong khoảng chỉ định; kết quả 0 không tự nói channel EOF.
- `selectNow()` không chờ; dùng khi event loop có công việc khác, không lặp busy-wait.
- `selector.wakeup()` làm một lần select đang đợi hoặc lần chọn tiếp theo trở về theo hợp đồng.
- `SelectionKey.cancel()` hủy registration, không tự đóng channel.
- `channel.close()` đóng I/O resource và hủy các registration tương ứng.
- `read()==-1` biểu thị EOF của channel, khác `read()==0` là không có tiến triển trong lần đọc non-blocking.

### Asynchronous

- `Future.get(timeout, unit)` đợi tối đa khoảng được chỉ định và có thể ném `TimeoutException`; timeout **không chứng minh thao tác I/O bên dưới đã bị hủy**.
- `Future.cancel(mayInterruptIfRunning)` yêu cầu hủy theo hợp đồng Future; kết quả/hành vi đối với operation đang chạy phụ thuộc API và trạng thái. Không mặc định tài nguyên được rollback, channel vẫn usable hoặc buffer không còn bị truy cập ngay sau cancel.
- `ExecutionException` đóng gói lỗi nguyên nhân khi đợi `get()`; `InterruptedException` báo thread chờ bị interrupt.
- `CompletionHandler.failed(Throwable, attachment)` nhận lỗi ở dạng callback. Nếu callback tự ném exception, cần hiểu cách provider xử lý log/lỗi; không dùng callback để che giấu thất bại.
- `channel.close()` khi còn operation pending có thể dẫn đến completion thất bại/cancellation theo API; ứng dụng cần quy trình shutdown và bảo vệ buffer/state.

Khi yêu cầu ghi dữ liệu bền vững, cả readiness lẫn completion đều **không tự thay thế `FileChannel.force()`, `FileDescriptor.sync()` hay giao thức commit**. “I/O completed” có nghĩa thao tác I/O trong hợp đồng được hoàn tất, không phải luôn bảo đảm chống mất điện.

## 14. Thread pool, backpressure và giới hạn tài nguyên

Không có mô hình nào miễn phí:

**Với non-blocking selector:**
- Một event loop có thể theo dõi nhiều kết nối, nhưng handler xử lý CPU nặng hoặc gọi I/O blocking trên event loop sẽ làm chậm tất cả kết nối liên quan.
- Việc bỏ qua partial write khiến mất byte; giữ `OP_WRITE` liên tục khi queue trống có thể làm CPU tăng.
- Readiness có thể đến nhanh hơn tốc độ xử lý message, cần giới hạn input buffers, tạm giảm `OP_READ` và thiết kế backpressure.

**Với asynchronous:**
- Dù API trả Future/callback, implementation có thể sử dụng executor; pool quá nhỏ hay hàng đợi không giới hạn đều có thể gây độ trễ.
- Callback không nên thực hiện công việc blocking kéo dài trên thread xử lý completion. Nếu cần, chuyển sang executor riêng với giới hạn công việc.
- Không nộp số lượng vô hạn operation đồng thời trên cùng channel/buffer. Theo dõi in-flight operations, buffer ownership và giới hạn dung lượng.
- Cần cơ chế tín hiệu kết quả về thread điều phối, xử lý ngoại lệ và shutdown có thứ tự.

**Với tác vụ blocking trong worker pool:** event loop có thể tiếp tục làm việc khác, nhưng worker pool vẫn bị giới hạn bởi số thread và tốc độ thiết bị. Mô hình này là một công cụ hữu dụng khi hệ thống có sẵn thư viện blocking và tải vừa phải; nó không biến một `InputStream` thành `SelectableChannel`.

Không có con số tuyệt đối rằng một mô hình luôn nhanh hơn. Đo throughput, latency (đặc biệt p95/p99), số thread, CPU, queue size, số kết nối và thời gian thao tác trên môi trường chạy thật.

## 15. Tình huống lựa chọn trong thực tế

| Yêu cầu | Lựa chọn thường phù hợp | Lý do |
| --- | --- | --- |
| Một CLI đọc file nhỏ | `Files.readAllBytes` / `FileChannel` đồng bộ | Code đơn giản, số tác vụ ít |
| HTTP server có nhiều TCP socket và event loop | `SocketChannel` non-blocking + `Selector` hoặc framework tương ứng | Điều phối readiness nhiều channel |
| Muốn đọc/ghi file ở offset mà không buộc caller chờ | `AsynchronousFileChannel` hoặc task trên executor | Có Future/handler hay offload blocking task |
| Ứng dụng xử lý callback completion socket | `AsynchronousSocketChannel` / `AsynchronousServerSocketChannel` | Mô hình callback/Future cho từng operation |
| Dùng thư viện `InputStream` blocking trên UI thread | Chuyển công việc sang worker thread | Giữ UI thread rảnh; I/O ở worker vẫn blocking |
| Theo dõi file log bị cập nhật | `WatchService` kết hợp đọc file thực tế | Sự kiện filesystem khác readiness của socket |
| Nhiều file ghi cần durability | I/O phù hợp + `force/sync` và giao thức dữ liệu | Completion/readiness không phải durability |
| Một request cần nhiều dữ liệu chưa đến đủ | Framing + state/buffer phù hợp với mô hình chọn | Cả hai đều có thể nhận dữ liệu từng phần |

Đừng chuyển toàn bộ code sang async chỉ vì mọi I/O đều có thể chậm. Chọn mô hình dựa trên cách hệ thống xử lý đồng thời, framework đang dùng, yêu cầu về số kết nối, khả năng quản lý state và giới hạn môi trường.

## 16. Lỗi thường gặp

1. **“Async và non-blocking là một.”** Sai: non-blocking trả lời readiness/khả năng thử I/O ngay; async có cơ chế nhận completion sau submit.
2. **“`Selector.select()` không bao giờ block.”** Sai: `select()` có thể chặn thread đợi sự kiện; non-blocking mô tả các lời gọi I/O của channel được cấu hình đúng.
3. **“`isReadable()` nghĩa là đã đọc xong.”** Sai: selector mới chỉ báo readiness, ứng dụng phải gọi `read()` và kiểm tra kết quả.
4. **“AsynchronousFileChannel dùng `Selector`.”** Sai: đây là API completion, không phải `SelectableChannel`.
5. **“`FileChannel` cũng có `configureBlocking(false)`.”** Sai: `FileChannel` không selectable, không có API đó.
6. **“`Future.get()` không block vì Future đến từ async.”** Sai: `get()` có thể đợi cho đến khi operation hoàn tất.
7. **“Callback chứng minh không có OS thread nào bị chặn.”** Sai: implementation có thể dùng thread pool bên dưới.
8. **“Một lần read luôn đủ message.”** Sai trong cả hai mô hình; kết quả có thể ít hơn dự kiến, cần framing.
9. **“Có thể `flip()/clear()` buffer ngay sau submit async.”** Sai khi operation còn pending; phải đợi completion rồi mới tái sử dụng.
10. **“Non-blocking write trả 0 thì cứ retry ngay trong while vô hạn.”** Không tốt: dùng readiness/`OP_WRITE` và backpressure để tránh CPU spin.
11. **“Giữ OP_WRITE mãi để chắc chắn gửi hết.”** Không cần thiết và dễ tạo event liên tục khi socket writable, làm tăng CPU.
12. **“Future timeout là cancel operation.”** Không phải; timeout chỉ giới hạn thời gian chờ của caller.
13. **“Callback hoàn tất là file đã sống sót qua mất điện.”** Không mặc định; durability đòi hỏi hợp đồng đồng bộ storage riêng.
14. **“Tạo task async vô hạn là mở rộng vô hạn.”** Sai: executor, descriptor, buffer và queue đều có giới hạn.
15. **“Read/write trả `0` và `-1` tương đương.”** Sai: 0 là chưa tiến triển trong lời gọi đó, -1 là EOF ở đường đọc.

## 17. Bài tập và câu hỏi tự kiểm tra

1. Giải thích khác nhau giữa “channel đã ready để đọc” và “yêu cầu đọc đã hoàn tất”. Kết quả mỗi trạng thái được nhận qua API Java nào?
2. Vì sao `selector.select()` có thể block nhưng các lần `SocketChannel.read()` trên channel non-blocking không phải chờ dữ liệu sẵn sàng theo blocking mode?
3. Bạn đã đăng ký `OP_READ` cho socket. `isReadable() == true` có đảm bảo `read(buffer)` trả đúng 100 byte đang mong đợi không?
4. Nếu `AsynchronousFileChannel.read(buffer, 0)` trả `Future<Integer>`, điều gì xảy ra nếu gọi `future.get()` ngay trên UI thread?
5. Tại sao có thể có thread pool chạy I/O dưới một API có tên `AsynchronousFileChannel`?
6. Một socket nonblocking chưa thể ghi nên `write(buffer) == 0`. Cần dùng `OP_WRITE` thế nào để retry mà không bận CPU?
7. FileChannel có thể đăng ký với selector không? Nếu muốn đọc file trên một background task, chọn hai phương án nào?
8. Trong `AsyncCallbackFileDemo`, tại sao phải đợi completion trước `dst.flip()` và trước khi đóng channel?
9. Nếu callback trả `completed(20, attachment)` nhưng giao thức yêu cầu 80 byte, chương trình nên làm gì?
10. `Future.get(timeout)` hết thời gian có đồng nghĩa nội dung buffer an toàn để dùng lại ngay không? Cần quản lý lifecycle ra sao?
11. Làm sao phân biệt `read()==0` và `read()==-1` trong nonblocking socket?
12. Thử sửa `NonBlockingSelectorDemo` để sink gửi nhiều phần, quan sát vòng `select`, và giải thích vì sao lượng byte nhận trong mỗi lượt có thể khác.
13. Thử đổi `AsyncFutureFileDemo` sang callback; điều gì khác về cách ứng dụng nhận kết quả và xử lý lỗi?
14. Nếu cần bảo đảm ghi bền vững tới file, asynchronous completion đã là đủ chưa? Vì sao?
15. Bạn chọn kiểu I/O nào cho một server có 50.000 socket và một tác vụ đọc file cấu hình nhỏ không thường xuyên? Giải thích lý do tách công việc file khỏi event loop.

## 18. Tài liệu API Java SE 17

- [`SelectableChannel`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/SelectableChannel.html) — `configureBlocking`, `register` và tính hợp lệ của operation.
- [`Selector`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/Selector.html) — `select`, `selectedKeys`, `wakeup`.
- [`SelectionKey`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/SelectionKey.html) — `interestOps`, `readyOps` và operation bitmask.
- [`AsynchronousFileChannel`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/AsynchronousFileChannel.html) — file I/O với `Future` và `CompletionHandler`.
- [`AsynchronousSocketChannel`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/AsynchronousSocketChannel.html) — TCP connect/read/write theo completion.
- [`CompletionHandler`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/CompletionHandler.html) — callback thành công/thất bại.

## 19. Ghi nhớ

**Non-blocking I/O** giúp lời gọi I/O không phải chờ dữ liệu/tài nguyên sẵn sàng; ứng dụng dùng `Selector` để được báo **readiness** rồi **tự thử `read()/write()`**. **Asynchronous I/O** cho phép nộp yêu cầu rồi nhận **completion** dưới dạng `Future` hoặc `CompletionHandler`; provider thực hiện yêu cầu I/O theo cơ chế tương ứng.

Hai mô hình vẫn phải giải quyết partial I/O, EOF, buffer ownership, timeout, cancellation, backpressure và tài nguyên. `Future.get()` có thể chặn thread gọi; `Selector.select()` cũng có thể đợi readiness. “Async” không hứa provider không dùng blocking threads, và “non-blocking” không có nghĩa mọi thao tác trong ứng dụng đều không thể chặn. Hãy chọn API theo **tín hiệu cần nhận (ready hay completed)**, loại tài nguyên (file/socket), và cách ứng dụng điều phối công việc.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="asynchronous-io-002">AsynchronousFileChannel và vị trí thao tác tường minh</a>

<details>
<summary>Xem chi tiết</summary>

# AsynchronousFileChannel và vị trí thao tác tường minh trong Java NIO.2

## 1. Mục tiêu và ý tưởng cốt lõi

`java.nio.channels.AsynchronousFileChannel` là API đọc/ghi tệp theo mô hình **bất đồng bộ** trong Java NIO.2. Thay vì bắt buộc luồng gọi đứng chờ cho đến khi đọc/ghi xong, ứng dụng có thể **khởi tạo một thao tác I/O**, nhận lại đối tượng `Future` hoặc cung cấp `CompletionHandler` để xử lý khi tác vụ hoàn tất.

Đặc điểm quyết định của API này: **mỗi lần đọc/ghi phải truyền vị trí tệp tường minh** (`long position`).

```java
Future<Integer> pendingRead = channel.read(destination, 128L);
Future<Integer> pendingWrite = channel.write(source, 512L);
```

Hai lệnh trên là ví dụ minh họa với channel đã mở; chúng trả ngay một `Future<Integer>` đại diện cho kết quả sẽ có. Số nguyên kết quả là **số byte thực sự được xử lý**, không phải offset mới. Đọc có thể trả `-1` khi gặp EOF.

Không có API `channel.position()` hay `channel.read(buffer)` không đối số offset như `FileChannel`. Vì vậy, mỗi tác vụ phải tự biết **mình đang làm việc ở vị trí nào trong tệp**. Khi một lời gọi chỉ đọc/ghi được một phần dữ liệu, ứng dụng phải tự tăng offset và gửi thao tác tiếp theo.

Bài này giúp bạn:

1. Phân biệt I/O bất đồng bộ với I/O đồng bộ và non-blocking channel.
2. Mở `AsynchronousFileChannel` đúng quyền và quản lý tài nguyên.
3. Dùng hai cách nhận kết quả: **`Future`** và **`CompletionHandler`**.
4. Tính offset chính xác, xử lý partial read/write, EOF và `ByteBuffer`.
5. Hiểu rủi ro khi chia sẻ buffer, ghi đồng thời, lỗi, đóng channel và hủy Future.
6. Áp dụng một chương trình Java 17+ độc lập có thể biên dịch/chạy.

## 2. Bất đồng bộ không đồng nghĩa tệp tự có con trỏ tiến lên

Hãy xem tệp có mười byte ASCII:

```text
File offset:  0 1 2 3 4 5 6 7 8 9 10
Content:      A B C D E F G H I J |EOF|
```

Nếu muốn đọc bốn byte từ offset 3:

```java
ByteBuffer part = ByteBuffer.allocate(4);
Future<Integer> request = channel.read(part, 3L);
int count = request.get();
```

Khi yêu cầu đã hoàn tất và đọc được đủ bốn byte, `part` chứa `DEFG`. Nhưng **channel không có một current position chung đã được chuyển từ 3 lên 7**. Nếu muốn đọc tiếp, hãy chỉ định **offset 7** ở lời gọi tiếp theo:

```java
ByteBuffer next = ByteBuffer.allocate(3);
Future<Integer> second = channel.read(next, 7L);
```

Điểm khác biệt với `FileChannel.read(buffer)` là thao tác đồng bộ tương đối có thể tự tiến `FileChannel.position()`. `AsynchronousFileChannel` luôn nhận offset cho mỗi `read/write`. Đây là thiết kế thuận lợi khi các tác vụ độc lập muốn truy cập những vùng tệp khác nhau mà không tranh chấp một con trỏ file chung.

### Mô hình tư duy

```text
Thread ứng dụng                    Hệ thống/thực thi I/O

read(dst, offset=3) ----------->  xử lý đọc bắt đầu từ byte 3
   |
   +--> Future<Integer>           [có thể chưa hoàn tất]
   |
   +--> làm công việc khác
   |
future.get() ----------------->  chờ nếu chưa xong
   |
   +--> nhận số byte đã đọc
   +--> cập nhật offset của ứng dụng nếu cần đọc tiếp
```

API bất đồng bộ **không cam kết rằng mọi hệ điều hành đều cung cấp native async disk I/O**: provider có thể sử dụng thread pool và các thao tác file đồng bộ ở phía dưới. Tính bất đồng bộ ở đây nói về **giao diện khởi tạo và nhận completion**, không phải lời bảo đảm tăng hiệu năng trong mọi tình huống.

## 3. So sánh với FileChannel và SocketChannel

| Tiêu chí | `FileChannel` | `AsynchronousFileChannel` | `SocketChannel` non-blocking |
|---|---|---|---|
| Nguồn I/O | Tệp | Tệp | TCP socket |
| Mô hình API | Đọc/ghi đồng bộ theo cách dùng thông thường | Khởi tạo thao tác, nhận `Future` hoặc callback | Gọi `read/write`, sẵn sàng xử lý theo Selector |
| Current file position | Có `position()`/`position(long)` | **Không có** current file position kiểu này | Không phải file offset |
| Đọc/ghi theo offset | Có `read/write(ByteBuffer,long)` | **Bắt buộc** cung cấp `position` trong các lệnh read/write | Không áp dụng offset tệp |
| Thời điểm nhận kết quả | Return khi lời gọi đọc/ghi hoàn thành | Future/callback báo completion | Lời gọi trả ngay lượng byte xử lý nếu non-blocking |
| `Selector` | Không phải selectable channel | **Không đăng ký Selector** | Có thể đăng ký Selector |
| Buffer | `ByteBuffer` | `ByteBuffer`, phải giữ ổn định trong khi pending | `ByteBuffer` |
| Đặc điểm cần lưu ý | Có current position dùng chung ở relative I/O | Offset tường minh; completion order có thể khác thứ tự khởi tạo | Readiness không phải completion của một tác vụ riêng |

**Không gọi `configureBlocking(false)` trên `AsynchronousFileChannel`**: nó không hoạt động theo mô hình `SelectableChannel`. Muốn nhận completion, dùng `Future` hoặc `CompletionHandler`. `Selector` là công cụ readiness phù hợp cho một số network channels, không phải mọi API có chữ “channel”.

## 4. Cách mở AsynchronousFileChannel

### Mở đơn giản

```java
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

Path path = Path.of("data.bin");

try (AsynchronousFileChannel channel = AsynchronousFileChannel.open(
        path,
        StandardOpenOption.READ,
        StandardOpenOption.WRITE,
        StandardOpenOption.CREATE)) {
    // Khởi tạo I/O và chờ/nhận kết quả trước khi đóng.
}
```

Đây là **đoạn code minh họa**; cần đặt trong class/method để chạy. Các option phổ biến:

- `READ`: cho phép đọc.
- `WRITE`: cho phép ghi.
- `CREATE`: tạo tệp nếu chưa tồn tại khi mở để ghi.
- `CREATE_NEW`: chỉ tạo mới; lỗi nếu tệp đã tồn tại.
- `TRUNCATE_EXISTING`: khi mở cho ghi, cắt tệp cũ về rỗng.
- `DELETE_ON_CLOSE`: cơ chế xóa theo vòng đời mở/đóng, phải hiểu quyền và tương tác với hệ thống tệp trước khi dùng.

**`APPEND` không hợp lệ đối với `AsynchronousFileChannel`** theo hợp đồng mở channel. API này nhận offset rõ ràng trong từng thao tác; nếu cần append an toàn nhiều writer, đừng dùng `size() + write` như một bảo đảm append nguyên tử.

### Mở với ExecutorService

`AsynchronousFileChannel.open(Path, Set<? extends OpenOption>, ExecutorService, FileAttribute<?>...)` cho phép cấp thread pool tùy chọn. Mẫu code (minh họa):

```java
ExecutorService executor = Executors.newFixedThreadPool(4);
try {
    try (AsynchronousFileChannel channel =
            AsynchronousFileChannel.open(
                    path,
                    Set.of(StandardOpenOption.READ, StandardOpenOption.WRITE),
                    executor)) {
        // Khởi tạo I/O và đảm bảo mọi pending operation đã kết thúc.
    }
} finally {
    executor.shutdown();
}
```

Cần import `ExecutorService`, `Executors`, `Set`, `AsynchronousFileChannel` và `StandardOpenOption`. Ở overload này, thread pool do caller cung cấp; **caller chịu trách nhiệm vòng đời ExecutorService** và phải phối hợp shutdown với tác vụ/callback chưa hoàn tất. Không shutdown quá sớm khi vẫn đang gửi thao tác mới hoặc chờ callback xử lý kết quả.

Thread pool có thể ảnh hưởng throughput/latency. Không suy ra 4 thread luôn là tối ưu; cần đo trên filesystem, loại I/O và mức concurrency thực tế. `AsynchronousFileChannel.open` không tự biến mọi ổ đĩa thành nền tảng I/O không block thật sự.

## 5. Cách 1 — Nhận kết quả bằng Future<Integer>

API đơn giản:

```java
Future<Integer> future = channel.read(dst, fileOffset);
int n = future.get();
```

`future.get()`:

- **Chờ** nếu thao tác chưa hoàn tất.
- Trả số byte đã đọc khi thành công, `-1` nếu EOF.
- Có thể ném `InterruptedException` khi thread đang chờ bị interrupt.
- Có thể ném `ExecutionException`, với nguyên nhân thật nằm trong `getCause()` khi thao tác I/O thất bại.
- Một Future đã được hoàn tất có thể lấy kết quả qua `get` nhiều lần mà không khởi tạo lại I/O.

Ghi tương tự:

```java
ByteBuffer src = StandardCharsets.UTF_8.encode("hello");
Future<Integer> pending = channel.write(src, 0L);
int written = pending.get();
```

Lời gọi `write` chỉ đưa byte có trong **vùng remaining của buffer** tại thời điểm thực thi thao tác. Không được tùy tiện `clear`, `flip`, `put` hoặc dùng cùng buffer cho một I/O khác trong lúc thao tác còn pending.

**Điểm thiết kế:** nếu gọi `future.get()` ngay sau mỗi lần `read/write`, thread thực tế vẫn đứng chờ. Code vẫn đúng và hữu ích để hiểu API hoặc viết helper đọc/ghi đủ byte, nhưng không tận dụng hết khả năng chồng lấp I/O với công việc khác. Muốn có concurrency, có thể phát động các thao tác độc lập, xử lý việc khác, rồi kiểm tra các Future/callback khi hoàn tất.

### Timeout khi chờ Future

```java
int n = future.get(3, TimeUnit.SECONDS);
```

Đây là ví dụ cần `TimeUnit` và xử lý `TimeoutException`, `InterruptedException`, `ExecutionException`. Timeout của `get` chỉ giới hạn **thời gian thread chờ**; nó **không mặc định chứng minh I/O trong hệ thống đã bị hủy**. Ngay cả `future.cancel(...)` cũng không nên được hiểu là một cam kết portable rằng mọi I/O đã ngừng và buffer có thể bị thay đổi tùy ý ngay tức thì. Hãy quản lý vòng đời pending operation theo hợp đồng implementation và bảo vệ buffer cho tới trạng thái an toàn.

## 6. Đọc/ghi đúng N byte: offset phải tự tăng

Một lần `read(dst, offset)` hoặc `write(src, offset)` **có thể trả ít byte hơn `remaining()`**. Vì không có current file position để tự tiến, helper phải quản lý `long fileOffset`.

### Đọc đủ một vùng

```java
static void readFully(
        AsynchronousFileChannel channel,
        ByteBuffer target,
        long startOffset
) throws Exception {
    long offset = startOffset;

    while (target.hasRemaining()) {
        Future<Integer> pending = channel.read(target, offset);
        int n = pending.get();

        if (n < 0) {
            throw new EOFException("Unexpected EOF at offset " + offset);
        }
        if (n == 0) {
            throw new IOException("Read made no progress");
        }
        offset += n;
    }
}
```

**Đây là phương thức minh họa**, với import `EOFException`, `IOException`, `AsynchronousFileChannel`, `ByteBuffer`, `Future`. Nó chờ kết thúc từng request trước khi tái sử dụng buffer. Việc ném lỗi ở `n == 0` tránh vòng lặp vô hạn; hệ thống cần chính sách retry/backoff riêng có thể dùng chiến lược khác.

### Ghi đủ một vùng

```java
static void writeFully(
        AsynchronousFileChannel channel,
        ByteBuffer source,
        long startOffset
) throws Exception {
    long offset = startOffset;

    while (source.hasRemaining()) {
        Future<Integer> pending = channel.write(source, offset);
        int n = pending.get();

        if (n <= 0) {
            throw new IOException("Write made no progress");
        }
        offset += n;
    }
}
```

Mỗi lần `write` hoàn tất, vị trí **buffer** tăng theo số byte thật sự xử lý, và **biến offset của ứng dụng** phải tăng tương ứng. Nếu viết vòng lặp nhưng luôn gọi `channel.write(source, startOffset)` mà không cộng `n`, lần ghi sau có thể **ghi đè chính các byte của lần ghi trước**.

### Tại sao cần offset kiểu long?

Tệp có thể lớn hơn 2 GiB, vượt giới hạn `int`. Khi tính vị trí bản ghi cố định:

```java
long offset = Math.addExact(
        headerSize,
        Math.multiplyExact((long) recordIndex, recordSize)
);
```

Đây là đoạn minh họa, biến cần khai báo với kiểu thích hợp. `Math.multiplyExact`/`addExact` giúp phát hiện tràn số khi offset tính từ input, thay vì biến thành offset sai. Kiểm tra `offset >= 0` và offset + kích thước thao tác không vượt giới hạn format cho phép.

## 7. Ví dụ Java 17+ hoàn chỉnh: các vị trí tường minh

Ví dụ sau:

1. Tạo tệp tạm.
2. Ghi mười byte ASCII `ABCDEFGHIJ` bắt đầu từ offset 0.
3. Đọc ba byte từ offset 0 thành `ABC`.
4. Đọc bốn byte từ offset 3 thành `DEFG`.
5. Ghi đè byte tại offset 4 bằng `XY`, làm `EF` thành `XY`.
6. Đọc lại toàn bộ, nhận `ABCDXYGHIJ`.
7. Mọi helper dùng `Future`, chờ completion và tự tăng offset.

Lưu đúng tên tệp **`AsyncFilePositionDemo.java`**:

```java
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Future;

public class AsyncFilePositionDemo {
    public static void main(String[] args) throws Exception {
        Path file = Files.createTempFile("async-position-", ".bin");

        try {
            try (AsynchronousFileChannel channel = AsynchronousFileChannel.open(
                    file,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {

                ByteBuffer initial = StandardCharsets.US_ASCII.encode(
                        "ABCDEFGHIJ");
                writeFully(channel, initial, 0L);
                System.out.println("Size: " + channel.size());

                ByteBuffer first = ByteBuffer.allocate(3);
                readFully(channel, first, 0L);
                first.flip();
                System.out.println("First: "
                        + StandardCharsets.US_ASCII.decode(first));

                ByteBuffer middle = ByteBuffer.allocate(4);
                readFully(channel, middle, 3L);
                middle.flip();
                System.out.println("Middle: "
                        + StandardCharsets.US_ASCII.decode(middle));

                ByteBuffer replacement = StandardCharsets.US_ASCII.encode("XY");
                writeFully(channel, replacement, 4L);

                ByteBuffer whole = ByteBuffer.allocate(10);
                readFully(channel, whole, 0L);
                whole.flip();
                System.out.println("Final: "
                        + StandardCharsets.US_ASCII.decode(whole));
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static void readFully(
            AsynchronousFileChannel channel,
            ByteBuffer target,
            long startOffset
    ) throws Exception {
        long offset = startOffset;

        while (target.hasRemaining()) {
            Future<Integer> future = channel.read(target, offset);
            int n = future.get();
            if (n == -1) {
                throw new EOFException("Unexpected EOF at " + offset);
            }
            if (n == 0) {
                throw new IOException("Read made no progress");
            }
            offset += n;
        }
    }

    private static void writeFully(
            AsynchronousFileChannel channel,
            ByteBuffer source,
            long startOffset
    ) throws Exception {
        long offset = startOffset;

        while (source.hasRemaining()) {
            Future<Integer> future = channel.write(source, offset);
            int n = future.get();
            if (n <= 0) {
                throw new IOException("Write made no progress");
            }
            offset += n;
        }
    }
}
```

Biên dịch và chạy trong thư mục chứa mã:

```bash
javac AsyncFilePositionDemo.java
java AsyncFilePositionDemo
```

**Stdout khi filesystem cho phép tạo, đọc, ghi và xóa tệp tạm:**

```text
Size: 10
First: ABC
Middle: DEFG
Final: ABCDXYGHIJ
```

Đây là kết quả dự kiến chính xác khi mọi thao tác hoàn tất thành công. Thứ tự dòng in là xác định vì mỗi helper `get()` chờ thao tác của mình kết thúc trước khi chuyển sang bước tiếp.

**Giải thích từng bước:**

- Ghi `ABCDEFGHIJ` tại offset 0 làm kích thước tệp bằng 10.
- `readFully(...,0L)` đọc `ABC`; không có current position bị dịch sang 3 trên channel.
- `readFully(...,3L)` đọc `DEFG` độc lập với lần đọc trước.
- `writeFully(...,4L)` ghi `XY` tại hai vị trí 4, 5, thay `EF` nhưng không chèn byte; độ dài vẫn 10.
- `readFully(...,0L)` đọc lại từ đầu mà không cần “seek về 0” hay reopen.
- `ByteBuffer.flip()` đặt buffer vào trạng thái đọc phần byte vừa nhận trước khi giải mã ASCII.
- Dù API dùng Future, helper này **đọc/ghi tuần tự theo từng bước vì chờ `get`**. Mục tiêu là làm rõ offset và tính đúng đắn; phần chạy song song là chủ đề kiến trúc bổ sung.

## 8. Cách 2 — CompletionHandler: callback khi I/O hoàn tất

Thay vì trả `Future` và chờ `get()`, `AsynchronousFileChannel` có overload nhận `attachment` và `CompletionHandler`:

```java
channel.read(buffer, offset, attachment, handler);
channel.write(buffer, offset, attachment, handler);
```

Với read:

```java
channel.read(
        destination,
        100L,
        "record-25",
        new CompletionHandler<Integer, String>() {
            @Override
            public void completed(Integer bytesRead, String recordId) {
                System.out.println(
                        "Completed " + recordId + ": " + bytesRead);
            }

            @Override
            public void failed(Throwable error, String recordId) {
                System.err.println(
                        "Failed " + recordId + ": " + error);
            }
        });
```

Đây là **đoạn minh họa trong một method**, cần import `CompletionHandler`, khai báo `channel` và `destination`. `attachment` là dữ liệu ứng dụng tự truyền (ở đây là String `"record-25"`), giúp callback biết request nào hoàn tất. Attachment có thể là object chứa offset, buffer, request ID, deadline hoặc state; không tự động gắn với dữ liệu lưu trong tệp.

Khác biệt quan trọng:

- Callback `completed(result, attachment)` nhận số byte đã xử lý, không nhận luôn `ByteBuffer` trừ khi ứng dụng giữ reference (ví dụ qua attachment).
- Callback `failed(exception, attachment)` nhận lỗi I/O bất đồng bộ.
- Callback có thể chạy trên thread khác với thread gọi `read/write`.
- Thứ tự callback của nhiều yêu cầu độc lập **không được giả định trùng với thứ tự khởi tạo**.
- Nếu lần read chỉ xử lý một phần, callback phải gửi read mới với **offset + bytesRead** và buffer còn remaining, hoặc chuyển giao phần còn thiếu cho một bộ điều phối.
- Callback phải xử lý ngoại lệ một cách có kiểm soát. Một exception không được bắt ngay trong callback không nên được coi là tự động được gửi tới thread khởi tạo như `Future.get()`.

### Callback đọc đủ vùng: bản minh họa có state

```java
record ReadState(ByteBuffer buffer, long nextOffset) {}

static void readNext(
        AsynchronousFileChannel channel,
        ByteBuffer target,
        long offset,
        CompletionHandler<Integer, Long> handler
) {
    channel.read(target, offset, offset, handler);
}
```

Đoạn này minh họa cách dùng **offset làm attachment**; cần khai báo trong class, import các kiểu và sử dụng `record` chỉ như một ví dụ mô hình hóa state. Khi callback thấy `n > 0`, lần đọc tiếp có thể gọi `channel.read(target, oldOffset + n, oldOffset + n, this)`. Nếu `n == -1` trước khi đủ byte thì lỗi EOF. Nếu `n == 0`, nên có chính sách retry/backoff có giới hạn. Một implementation thực tế cần promise/future hoặc handler cuối để báo “toàn bộ record đã đọc đủ”; không được đánh dấu hoàn tất chỉ vì một lần callback thành công.

**Không trộn code Future và callback cho cùng một lần I/O.** Hai overload tạo hai cách nhận kết quả; chọn một mô hình quản lý completion rõ ràng cho mỗi request.

## 9. ByteBuffer khi thao tác còn pending

Đây là điểm rất dễ phát sinh lỗi trong I/O bất đồng bộ.

`AsynchronousFileChannel.read` ghi dữ liệu vào `ByteBuffer`; `write` đọc dữ liệu từ buffer. Trong thời gian I/O chưa hoàn tất:

- Không nên thay `position`, `limit` hay nội dung của buffer.
- Không khởi tạo thao tác read/write khác sử dụng **cùng một buffer** khi chưa đảm bảo thao tác trước đã xong.
- Không decode/đọc dữ liệu vừa nhận trước completion.
- Không tái sử dụng hoặc trả buffer về pool trước khi đã xử lý kết quả cuối cùng.
- Khi callback đã báo hoàn tất hoặc `Future.get()` thành công, chuẩn bị `flip`/`clear` mới an toàn theo luồng công việc thích hợp.

Ví dụ **sai**:

```java
ByteBuffer buf = ByteBuffer.allocate(1024);
Future<Integer> first = channel.read(buf, 0L);
buf.clear();                        // Sai: I/O first vẫn có thể pending.
Future<Integer> second = channel.read(buf, 1024L);
```

Hai thao tác cùng buffer có thể khiến vị trí/nội dung bị truy cập đồng thời ngoài hợp đồng an toàn. `ByteBuffer` không tự biến thành một vùng nhớ chia sẻ có đồng bộ chỉ vì API gọi bất đồng bộ.

Ví dụ **đúng về quyền sở hữu buffer**:

```java
ByteBuffer firstBuffer = ByteBuffer.allocate(1024);
ByteBuffer secondBuffer = ByteBuffer.allocate(1024);

Future<Integer> first = channel.read(firstBuffer, 0L);
Future<Integer> second = channel.read(secondBuffer, 1024L);

int n1 = first.get();
int n2 = second.get();
// Sau get thành công, mới xử lý hai buffer tương ứng.
```

Đây vẫn là đoạn minh họa. Trước khi giải mã, chỉ những byte tương ứng với kết quả hoàn tất là dữ liệu mới đọc hợp lệ. Nếu đọc partial, thiết kế parser phải xử lý phần còn thiếu. Không giả định `n1 == 1024` hoặc `n2 == 1024`.

**Lưu ý concurrency:** ngay cả khi hai buffer riêng, hai thao tác đọc cùng tệp trong khi thread khác ghi/truncate tệp vẫn có thể nhận dữ liệu không nhất quán. Buffer riêng giải quyết tranh chấp vùng nhớ của ứng dụng, không tạo snapshot nhất quán của tệp.

## 10. Tại sao không có current position chung?

Trong asynchronous I/O, thời điểm **được khởi tạo** và thời điểm **hoàn tất** của các request có thể khác nhau:

```text
T0: read(A, position=0)   ────────┐
T1: read(B, position=4096)    ──┐ │
T2: write(C, position=8192) ─┐ │ │
T3: write(C) completed       │ │ │
T4: read(B) completed          │ │
T5: read(A) completed            │
```

Nếu mọi request tự động chia sẻ một con trỏ và tăng khi completion xảy ra, vị trí thao tác sẽ khó xác định và phụ thuộc thứ tự hoàn tất. API dùng **offset tường minh ngay khi gửi request**, giúp mỗi thao tác xác định vùng dữ liệu của mình.

Tuy nhiên, **offset tường minh không chứng minh các I/O đồng thời tại cùng một vùng là nguyên tử hoặc có thứ tự xác định**. Khi hai write chồng lấn:

```text
write A: offset=100, length=10
write B: offset=105, length=10
              [cùng ghi byte 105..109]
```

Kết quả ở phần giao nhau có thể phụ thuộc lịch thực thi và provider; ứng dụng phải tránh ghi chồng lấn hoặc đồng bộ theo thứ tự logic. Với nhiều tác vụ cần đọc-sửa-ghi cùng record, một thao tác `read` rồi `write` không tạo transaction nguyên tử.

### Mô hình xử lý song song an toàn hơn

- Mỗi tác vụ có một **offset riêng được tính toán và kiểm tra**.
- Mỗi tác vụ sở hữu một **ByteBuffer riêng** trong suốt thời gian pending.
- Không ghi chồng lấn nếu không có cơ chế điều phối.
- Có giới hạn số lượng tác vụ concurrent để tránh quá tải thread pool, bộ nhớ, file descriptors hoặc I/O queue.
- Có chính sách timeout, retry, cancel và cleanup, nhất là khi lỗi xảy ra lúc nhiều Future khác vẫn pending.
- Nếu cần snapshot nhất quán, dùng thiết kế lưu trữ thích hợp (file bất biến theo version, khóa/chốt đồng bộ hoặc transaction ở tầng khác).

## 11. Completion, cancellation và lỗi

### Future exceptions

`Future.get()` có thể:

- Trả `Integer >= 0` khi thành công.
- Trả `-1` cho read ở EOF.
- Ném `InterruptedException` khi luồng đợi bị interrupt.
- Ném `ExecutionException` khi thao tác thất bại (kiểm tra `getCause()`).
- Ném `CancellationException` khi Future đã bị cancel theo hợp đồng Future.

Xử lý tổng quát:

```java
try {
    int n = pending.get();
    // Dựa vào n để quyết định parse, retry hoặc EOF.
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    // Hủy/điều phối cleanup theo policy; không tái sử dụng buffer mù quáng.
} catch (ExecutionException ex) {
    Throwable cause = ex.getCause();
    // Log/truyền lỗi gốc phù hợp với tầng ứng dụng.
}
```

Đoạn này giả định `pending` có kiểu `Future<Integer>` và các exception được import. Nếu dữ liệu bắt buộc cập nhật đúng một lần, retry còn phải xét operation trước có thể đã ghi một phần.

### Đóng channel

`AsynchronousFileChannel` là `AutoCloseable`. Try-with-resources đóng channel khi thoát scope, nhưng ứng dụng nên **đảm bảo các request đã được chờ/hoàn tất trước khi scope kết thúc**. Nếu đóng channel khi còn thao tác pending, thao tác có thể thất bại với lỗi đóng channel như `AsynchronousCloseException` hoặc `ClosedChannelException` tùy thời điểm và cơ chế.

Không coi `close()` là một lệnh “chờ mọi callback xong, gửi toàn bộ kết quả và flush file” ở tầng nghiệp vụ. Thiết kế lifecycle rõ: dừng nhận request mới → đợi/điều phối các request đang chạy → xử lý callback/kết quả → thực hiện đồng bộ lưu trữ nếu cần → đóng channel/executor.

### FileLock

`AsynchronousFileChannel` có các thao tác khóa tệp bất đồng bộ và `tryLock`. File lock giúp phối hợp truy cập ở mức filesystem/provider nhưng không biến nhiều `read/write` thành transaction; tính bắt buộc/tư vấn của lock tùy nền tảng. Cũng cần đóng/release đúng vòng đời.

Nếu mục tiêu chỉ là truy cập các vùng file không chồng lấn, đừng thêm locking một cách tùy tiện: nó có thể tăng độ phức tạp và contention. Chỉ dùng khi có hợp đồng đồng thời cần bảo vệ rõ ràng.

## 12. `size()`, `truncate()`, `force()` và độ bền dữ liệu

Bên cạnh `read/write`, `AsynchronousFileChannel` còn có các phương thức hỗ trợ quản lý file:

| Phương thức | Ý nghĩa |
|---|---|
| `size()` | Trả kích thước file ở thời điểm gọi |
| `truncate(long size)` | Cắt đuôi file khi kích thước hiện tại lớn hơn tham số |
| `force(boolean metaData)` | Yêu cầu đồng bộ nội dung (và metadata nếu true) xuống thiết bị lưu trữ theo hợp đồng API |
| `lock(...)`, `tryLock(...)` | Hỗ trợ khóa file hoặc vùng file với semantics/provider tương ứng |
| `isOpen()`, `close()` | Quản lý trạng thái/vòng đời channel |

**`truncate` không tự mở rộng file** nếu size truyền vào lớn hơn độ dài hiện tại. Muốn mở rộng, phải dùng cơ chế ghi hoặc thiết kế dữ liệu phù hợp; không có `channel.position(newPosition)` để thay current position vì API này không có con trỏ chung đó.

`force(true)` giúp yêu cầu đồng bộ cập nhật đã thực hiện qua channel, nhưng không tự tạo transaction giữa nhiều lần write; durability cuối cùng phụ thuộc filesystem, thiết bị và provider. Muốn đảm bảo file hoàn chỉnh khi crash giữa nhiều thao tác write, cân nhắc ghi file tạm, kiểm tra, đồng bộ và thay thế đích phù hợp với semantics filesystem.

Khi đang có I/O pending, không nên `truncate` ngay mà kỳ vọng các lần read/write khác tự thực hiện theo thứ tự bạn hình dung. Điều phối các thao tác liên quan trước khi sửa kích thước file.

## 13. Hiệu năng và giới hạn tài nguyên

**Bất đồng bộ không tự động nhanh hơn đồng bộ.** Lợi ích chủ yếu xuất hiện khi ứng dụng có công việc khác để làm trong lúc chờ I/O, hoặc cần quản lý nhiều tác vụ độc lập mà không muốn mỗi request chiếm một luồng ứng dụng riêng đang chờ.

Khi đánh giá:

1. **Số lượng tác vụ pending:** quá nhiều Future/ByteBuffer có thể gây áp lực bộ nhớ và thread pool.
2. **Kích thước I/O:** đọc từng vài byte bất đồng bộ có thể có overhead lớn so với đọc/gửi theo block phù hợp.
3. **Executor/provider:** một số implementation dùng thread pool thực hiện file I/O; tối ưu bằng đo hiệu năng chứ không chỉ đổi API.
4. **Ổ đĩa và filesystem:** đặc tính NVMe/HDD/network filesystem, cache, flush và số I/O song song có thể thay đổi kết quả.
5. **Đồng thời và consistency:** chạy song song những write chồng lấn có thể làm dữ liệu sai dù throughput tăng.
6. **Độ trễ completion:** callback nhanh nên giao việc CPU nặng cho một executor khác hoặc luồng xử lý phù hợp; không chặn lâu thread của completion pool.
7. **Độ bền:** `force`, metadata update, sync và locking có thể thay đổi đáng kể latency; đừng bỏ chúng chỉ để benchmark đẹp nếu nghiệp vụ đòi hỏi.

Nếu nhu cầu chỉ đọc một file nhỏ một lần rồi đợi ngay, `Files.readAllBytes` hay `FileChannel` đồng bộ có thể đơn giản hơn và đủ tốt. Chọn `AsynchronousFileChannel` khi **mô hình completion/parallel requests** thực sự giúp kiến trúc.

## 14. Các lỗi thường gặp

1. **Tưởng có current position của AsynchronousFileChannel.** Không có; mọi `read/write` nhận `long position`.
2. **Không tăng offset sau partial I/O.** Lần gọi tiếp có thể đọc lặp/ghi đè byte cũ.
3. **Tin rằng một lần read/write xử lý hết buffer.** Kiểm tra kết quả, remaining và EOF.
4. **Gọi `future.get()` ngay rồi vẫn quảng cáo ứng dụng hoàn toàn không block.** `get` có thể chờ; async API không làm luồng chờ tự biến mất.
5. **Nhầm `Future.get()` với `ByteBuffer.get()`.** Cái đầu trả số byte I/O sau completion; cái sau đọc byte từ buffer theo position.
6. **Tái dùng ByteBuffer trước completion.** Có thể gây lỗi dữ liệu hoặc vi phạm hợp đồng truy cập buffer trong lúc pending.
7. **Dùng cùng buffer cho nhiều request cùng lúc.** Dù offset tệp khác nhau, position/nội dung buffer vẫn tranh chấp.
8. **Nhầm EOF `-1` với read `0`.** `0` có thể nghĩa là chưa đọc byte nào; `-1` là hết dữ liệu ở vị trí đó.
9. **Quên `flip()` trước khi decode buffer đã được đọc vào.** Decoder có thể thấy remaining sai hoặc không thấy dữ liệu mới.
10. **Không quản lý `InterruptedException` đúng.** Khôi phục interrupt flag khi không tiêu thụ interrupt có chủ đích và thực hiện cleanup phù hợp.
11. **Cho rằng callback chạy trên cùng thread hoặc theo thứ tự submit.** Thứ tự completion không phải thứ tự khởi tạo.
12. **Đóng channel/executor khi còn I/O pending.** Thao tác có thể thất bại; cần chính sách shutdown/lifecycle rõ.
13. **Dùng `Selector` với AsynchronousFileChannel.** API này không phải selectable channel.
14. **Gửi APPEND khi mở channel.** `AsynchronousFileChannel` không hỗ trợ kiểu append mở tệp như vậy.
15. **Dùng `size()` rồi ghi tại size như append atomic giữa nhiều writer.** Hai writer có thể chọn cùng offset; cần điều phối.
16. **Coi offset độc lập là dữ liệu độc lập.** Các write cùng vùng vẫn có race; truncate có thể ảnh hưởng request khác.
17. **Cho rằng cancel Future luôn đảo ngược phần write đã thực hiện.** Không có transaction/rollback; retry cần thiết kế idempotency.
18. **Chỉ tối ưu số thread mà bỏ qua storage đặc thù.** Benchmark trên loại thiết bị, quy mô và pattern I/O thực tế.

## 15. Khi nào nên chọn AsynchronousFileChannel?

### Trường hợp A — Xử lý nhiều record theo offset độc lập

Tệp record fixed-size, có một số đoạn đã biết vị trí. Bạn có thể submit các `read(buffer, offset)` song song với **buffer riêng cho mỗi request**, rồi xử lý kết quả khi tất cả hoàn tất. Mẫu này có ích nếu tầng ứng dụng muốn concurrency và không cần con trỏ chung.

### Trường hợp B — Server cần chồng lấp công việc với file I/O

Một request cần nạp một đoạn file lớn, nhưng thread xử lý còn công việc khác. Có thể submit file read và tiếp tục xử lý các phần độc lập, sau đó callback/Future báo sẵn sàng. Lưu ý backpressure và số buffer đang tồn tại.

### Trường hợp C — Chỉnh sửa file tuần tự ít lần

Nếu chương trình đọc 100 byte, xử lý rồi ghi 20 byte và luôn chờ ngay, `FileChannel` hoặc `RandomAccessFile` có thể đơn giản hơn. `AsynchronousFileChannel` không bắt buộc chỉ vì tệp lớn.

### Trường hợp D — Nhiều writer vào cùng cuối tệp

Không chỉ gọi `channel.write(buffer, channel.size())` đồng thời và kỳ vọng append đúng thứ tự. `size()` là một snapshot tại thời điểm gọi; hai writer có thể chọn cùng offset. Cần một allocator offset đồng bộ, một writer tuần tự, record log có kiến trúc riêng hoặc cơ chế lưu trữ có append/transaction phù hợp.

### Trường hợp E — Muốn stream mạng event-driven

Nếu tác vụ là network TCP/UDP chứ không phải file I/O, hãy chọn API network tương ứng như `SocketChannel` + `Selector` hoặc nhóm `AsynchronousSocketChannel`, không cố dùng `AsynchronousFileChannel` cho socket.

## 16. Câu hỏi tự kiểm tra

**Câu 1.** `AsynchronousFileChannel` có `channel.position(100L)` như `FileChannel` không?  
**Không.** Mỗi `read/write` có `long position` tường minh.

**Câu 2.** Gọi `read(buffer, 100L)` nhận `Future<Integer>`, sau khi `get()` trả 40. Muốn đọc phần tiếp theo ở offset nào?  
**Offset 140**, với điều kiện buffer còn remaining và format yêu cầu đọc tiếp.

**Câu 3.** Gọi `write(source, 500L)`, `get()` trả 10 nhưng source vẫn còn 8 byte. Lời ghi tiếp nên bắt đầu ở đâu?  
**Offset 510.** Chờ lần ghi trước hoàn tất rồi `write(source, 510L)`.

**Câu 4.** Có thể gọi `buffer.clear()` khi Future read còn pending không?  
**Không nên;** buffer thuộc thao tác đang chạy. Chờ completion/thiết kế quyền sở hữu trước khi đổi trạng thái buffer.

**Câu 5.** Nếu dùng `Future.get()` ngay sau khi submit thì code có nhất thiết không block không?  
**Không.** `get()` có thể chặn luồng gọi cho tới completion.

**Câu 6.** Hai read ở offset 0 và 4096 với hai buffer riêng có phải luôn trả về theo thứ tự submit không?  
**Không.** Thứ tự completion có thể khác. Nếu cần xử lý theo thứ tự, ứng dụng phải điều phối kết quả.

**Câu 7.** `AsynchronousFileChannel` có thể đăng ký `Selector.OP_READ` không?  
**Không.** Nó không phải `SelectableChannel`.

**Câu 8.** `channel.size()` là 1000; hai writer cùng submit `write(buffer,1000L)`. Có được bảo đảm nối tiếp mà không chồng byte không?  
**Không.** Các thao tác dùng cùng offset và có thể ghi chồng lấn. Cần cấp phát offset/serialization trước khi submit.

**Câu 9.** Một write trả thành công nghĩa là dữ liệu đã bền vững qua mất điện chưa?  
**Chưa chắc.** Cần chính sách durability, ví dụ `force` khi phù hợp, và hiểu giới hạn provider/filesystem.

## 17. Tổng kết

- `AsynchronousFileChannel` là API file I/O bất đồng bộ với **offset tường minh cho từng lần đọc/ghi**; nó không có current file position kiểu `FileChannel`.
- `read/write(ByteBuffer, long)` trả `Future<Integer>`; overload có attachment và `CompletionHandler` cho phép nhận completion qua callback.
- **Số byte thành công có thể nhỏ hơn remaining**; cần tăng offset bằng lượng byte thực tế và xử lý EOF.
- **ByteBuffer phải được giữ ổn định khi thao tác còn pending**; không tái sử dụng một buffer cho các request chồng nhau.
- **Offset riêng giúp tổ chức concurrency**, nhưng không tự làm dữ liệu đồng thời nhất quán và không thay thế lock/transaction.
- Bất đồng bộ là một **mô hình lập trình**, không phải cam kết tốc độ cao hơn trong mọi điều kiện; hãy đo hiệu năng và quản lý tài nguyên có giới hạn.

### Tài liệu API Java SE 17

- [AsynchronousFileChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/AsynchronousFileChannel.html)
- [CompletionHandler](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/CompletionHandler.html)
- [FileChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileChannel.html)
- [ByteBuffer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="asynchronous-io-003">Future và CompletionHandler</a>

<details>
<summary>Xem chi tiết</summary>

# Future và CompletionHandler trong Java NIO.2: Hai cách nhận kết quả I/O bất đồng bộ

## 1. Bài toán và mục tiêu

Với I/O đồng bộ quen thuộc, lệnh `channel.read(buffer)` thường chỉ trả về khi thao tác đọc đã có kết quả. Java NIO.2 cung cấp các kênh bất đồng bộ trong gói `java.nio.channels`, cho phép **khởi phát thao tác I/O rồi nhận kết quả về sau**.

Các lớp tiêu biểu gồm:

- `AsynchronousFileChannel`: đọc/ghi vùng tệp tại offset cụ thể.
- `AsynchronousSocketChannel`: kết nối, đọc và ghi qua TCP theo mô hình bất đồng bộ.
- `AsynchronousServerSocketChannel`: nhận kết nối TCP theo mô hình bất đồng bộ.

Hai kiểu API chủ đạo là **`Future`** và **`CompletionHandler`**:

1. **`Future`:** phương thức I/O trả về một đối tượng đại diện kết quả chưa hoàn tất; ứng dụng kiểm tra trạng thái hoặc gọi `get()` để đợi và lấy kết quả.
2. **`CompletionHandler`:** khi gọi I/O, ứng dụng truyền callback; khi thao tác kết thúc, hệ thống gọi `completed(...)` hoặc `failed(...)`.

**Bất đồng bộ không có nghĩa dữ liệu chắc chắn đã sẵn ngay**, thao tác luôn chạy trên một luồng mới, hay mỗi lần đọc/ghi sẽ chuyển đủ số byte yêu cầu. Phần quan trọng là biết khi nào công việc hoàn thành, kết quả thực tế là bao nhiêu và ai quản lý vòng đời buffer/channel.

Sau bài này, bạn sẽ hiểu cách chọn `Future` hoặc `CompletionHandler`, xử lý đọc/ghi một phần, lỗi, timeout, hủy thao tác và viết ví dụ Java 17+ có thể chạy độc lập.

## 2. So sánh chữ ký API

### 2.1. `AsynchronousFileChannel`

Hai phương thức cùng thực hiện đọc ở một offset rõ ràng nhưng sử dụng mô hình trả kết quả khác nhau:

~~~java
// Future: kết quả được lấy từ đối tượng Future<Integer>.
Future<Integer> read(ByteBuffer dst, long position);

// Callback: hệ thống chuyển kết quả tới CompletionHandler.
<A> void read(
        ByteBuffer dst,
        long position,
        A attachment,
        CompletionHandler<Integer, ? super A> handler);
~~~

Tương tự cho thao tác ghi:

~~~java
Future<Integer> write(ByteBuffer src, long position);

<A> void write(
        ByteBuffer src,
        long position,
        A attachment,
        CompletionHandler<Integer, ? super A> handler);
~~~

Đây là **chữ ký phương thức được giản lược để học**, không phải chương trình Java độc lập. `position` là offset tính theo **byte trong tệp**, không phải `ByteBuffer.position()` và không phải vị trí con trỏ tệp chia sẻ.

Với `AsynchronousFileChannel`, không có ý tưởng “file position hiện tại” để dựa vào như `FileChannel.position()`: mỗi lệnh đọc/ghi dùng offset tường minh. Khi thao tác trả về `n` byte, lần tiếp theo xử lý phần còn lại tại `position + n`.

### 2.2. `AsynchronousSocketChannel`

~~~java
Future<Void> connect(SocketAddress remote);
<A> void connect(
        SocketAddress remote,
        A attachment,
        CompletionHandler<Void, ? super A> handler);

Future<Integer> read(ByteBuffer dst);
<A> void read(
        ByteBuffer dst, A attachment,
        CompletionHandler<Integer, ? super A> handler);

Future<Integer> write(ByteBuffer src);
<A> void write(
        ByteBuffer src, A attachment,
        CompletionHandler<Integer, ? super A> handler);
~~~

Đây cũng là **chữ ký minh họa**; một số API có thêm các overload cho timeout hoặc nhiều buffer. Khác với file channel, socket không có offset tệp; byte được nhận/gửi nối tiếp trên luồng TCP.

| Đặc điểm | `Future` | `CompletionHandler` |
| --- | --- | --- |
| Nhận kết quả | Gọi `future.get()` hoặc kiểm tra `isDone()` | Callback `completed(result, attachment)` |
| Lỗi I/O trong quá trình thực thi | Thường được gói trong `ExecutionException` khi gọi `get()` | Callback `failed(exc, attachment)` |
| Chờ hoàn tất | `get()` **chặn luồng gọi**; có biến thể timeout | Không cần chặn luồng khởi phát chỉ để đợi kết quả |
| Phối hợp nhiều bước | Dễ viết tuần tự, nhưng phải quản lý việc chờ | Có thể nối bước từ callback, hoặc chuyển sang `CompletableFuture` |
| Nguy cơ thường gặp | Gọi `get()` ngay rồi vô tình chặn luồng quan trọng | Callback dài, lỗi không được chuyển ra nơi quan sát, hoặc rối luồng điều khiển |
| Hủy | `Future.cancel(...)` cung cấp giao diện yêu cầu hủy | Không có đối tượng `Future` do overload callback trả về |
| Gắn ngữ cảnh | Tự giữ trạng thái bên ngoài `Future` | Dùng `attachment` để chuyển trạng thái tới callback |

Một phương thức chọn overload `Future` **không đồng thời** gọi `CompletionHandler` của overload khác. Hai kiểu thể hiện cùng ý tưởng kết quả bất đồng bộ, nhưng cách tiếp nhận và phối hợp hoàn thành khác nhau.

## 3. Dòng thời gian của một thao tác bất đồng bộ

Với `Future`:

~~~text
Luồng gọi:
  tạo ByteBuffer
       |
  Future<Integer> f = channel.read(buffer, offset)
       |
  xử lý tác vụ độc lập khác (nếu có)
       |
  f.get()  -- có thể phải đợi -->
       |
  đọc số byte thực tế; xử lý buffer/EOF/lỗi
~~~

Với `CompletionHandler`:

~~~text
Luồng khởi phát:
  channel.read(buffer, offset, state, handler)
       |
  tiếp tục chạy công việc khác

Khi I/O kết thúc:
  handler.completed(n, state)
       hoặc
  handler.failed(error, state)
~~~

**Điểm rất dễ nhầm:** `channel.read(...)` trả về `Future` nhanh không có nghĩa lệnh đọc đã hoàn thành. Ngược lại, việc callback chạy sau đó không có nghĩa nó luôn chạy trên một thread dành riêng cho yêu cầu; chính sách thực thi phụ thuộc provider và nhóm luồng/channel.

## 4. Cách dùng `Future` đúng

### 4.1. Khởi phát rồi nhận kết quả

~~~java
ByteBuffer buffer = ByteBuffer.allocate(1024);
Future<Integer> pending = file.read(buffer, 0L);

// Có thể làm một công việc khác tại đây.
int n = pending.get(); // Chặn luồng gọi nếu I/O chưa xong.
~~~

Đoạn mã dùng một `AsynchronousFileChannel file` đã mở và cần import `ByteBuffer`, `Future`. `get()` có thể ném `InterruptedException` hoặc `ExecutionException`; ví dụ này chỉ minh họa luồng thao tác.

Các phương thức quan trọng:

- `future.isDone()`: cho biết thao tác đã đi đến trạng thái kết thúc (thành công, thất bại hoặc đã hủy), **không có nghĩa chắc chắn thành công**.
- `future.get()`: đợi rồi nhận kết quả; lỗi thực thi được bọc trong `ExecutionException`.
- `future.get(timeout, unit)`: giới hạn **thời gian chờ của luồng gọi**; có thể ném `TimeoutException`.
- `future.cancel(mayInterruptIfRunning)`: yêu cầu hủy; có thể trả `false` nếu không hủy được. Sau khi hủy, `get()` có thể ném `CancellationException`.
- `future.isCancelled()`: kiểm tra trạng thái hủy của Future.

**Timeout khi đợi không nhất thiết dừng I/O.** Sau `get(2, SECONDS)` bị `TimeoutException`, thao tác đọc/ghi có thể vẫn đang chạy. Ứng dụng phải quyết định tiếp tục chờ, yêu cầu hủy hoặc đóng tài nguyên; không nên tái sử dụng `ByteBuffer` như thể I/O đã kết thúc.

### 4.2. Phân biệt lỗi lúc khởi phát và lỗi khi thực thi

Một số lỗi có thể xảy ra **ngay ở lời gọi `read/write`**, chẳng hạn tham số không hợp lệ hoặc channel đã đóng. Những lỗi khác phát sinh sau khi thao tác bắt đầu:

~~~java
try {
    Future<Integer> f = file.read(buffer, offset);
    int n = f.get();
    // Xử lý n: n>0, n==0 hoặc n==-1 (đối với đọc).
} catch (ExecutionException ex) {
    Throwable actualCause = ex.getCause();
    // Ghi log/ánh xạ lỗi từ tác vụ I/O.
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    // Tôn trọng chính sách hủy tác vụ của luồng gọi.
}
~~~

Đây là đoạn minh họa với `file`, `buffer`, `offset` đã khai báo và import `Future`, `ExecutionException`. Phải xử lý thêm các lỗi hợp lệ của phương thức khi dùng trong chương trình thật.

**Đừng nuốt `InterruptedException`:** nên phục hồi cờ interrupt bằng `Thread.currentThread().interrupt()` hoặc truyền lỗi theo chính sách của ứng dụng. `get()` bị ngắt không tự chứng minh I/O đã bị hủy.

## 5. Cách dùng `CompletionHandler` đúng

Một `CompletionHandler<V,A>` có hai kiểu tham số:

- `V` là **loại kết quả** của một lần I/O, thường là `Integer` cho số byte đọc/ghi hoặc `Void` cho connect.
- `A` là **loại attachment** do người gọi tự chọn, để mang ngữ cảnh như request id, offset, buffer, trạng thái hoặc đối tượng theo dõi.

~~~java
channel.read(buffer, 0L, requestId,
    new CompletionHandler<Integer, String>() {
        @Override
        public void completed(Integer bytesRead, String id) {
            System.out.println(id + ": " + bytesRead);
        }

        @Override
        public void failed(Throwable error, String id) {
            System.err.println(id + ": " + error);
        }
    });
~~~

Đây là đoạn minh họa; `channel` là `AsynchronousFileChannel`, `buffer` là `ByteBuffer`, `requestId` là `String` và cần import `CompletionHandler`. Callback `completed` chỉ cho biết **một thao tác** đã hoàn tất; nếu `bytesRead` còn thiếu, bạn cần tự khởi phát lần đọc tiếp theo.

### 5.1. Callback nên làm gì?

Trong phương thức `completed`:

1. Kiểm tra số byte thực tế được đọc/ghi.
2. Cập nhật buffer và offset hoặc trạng thái giao thức.
3. Nếu còn dữ liệu cần xử lý, khởi phát thao tác tiếp theo.
4. Nếu hoàn tất toàn bộ yêu cầu, thông báo cho thành phần chờ hoặc phát kết quả.

Trong phương thức `failed`:

1. Ghi nhận nguyên nhân thực tế.
2. Đánh dấu tác vụ thất bại **một lần**.
3. Giải phóng/đóng tài nguyên theo chính sách ownership.

Đừng làm việc CPU nặng, sleep hoặc chờ khóa kéo dài trong callback nếu việc đó chiếm luồng dùng chung để hoàn tất các I/O khác. Nên chuyển công việc nặng sang executor thích hợp.

### 5.2. Attachment giúp tránh trộn trạng thái

Khi nhiều I/O cùng diễn ra, attachment cho phép mỗi callback biết mình thuộc thao tác nào:

~~~java
record RequestState(String id, long fileOffset) {}

RequestState state = new RequestState("req-42", 4096L);
channel.read(buffer, state.fileOffset(), state,
    new CompletionHandler<Integer, RequestState>() {
        @Override
        public void completed(Integer result, RequestState request) {
            System.out.println(request.id() + " -> " + result);
        }

        @Override
        public void failed(Throwable exc, RequestState request) {
            System.err.println(request.id() + ": " + exc);
        }
    });
~~~

Đây là đoạn minh họa cho Java 17+, với `channel`/`buffer` đã mở và import `CompletionHandler`. Khi xử lý tiếp byte còn lại, cần cập nhật offset dựa trên **số byte đã thực sự chuyển**, không dựa trên kích thước yêu cầu ban đầu.

Attachment không tự giải quyết tranh chấp thread hay chia sẻ buffer; trạng thái mutable vẫn cần ownership hoặc đồng bộ thích hợp.

## 6. Kết quả thực tế và vòng lặp đọc/ghi từng phần

Với `AsynchronousFileChannel.read(dst, position)`:

- `n > 0`: có `n` byte được đưa vào buffer; `ByteBuffer.position()` tăng theo `n`.
- `n == -1`: EOF, không còn byte ở vị trí đọc yêu cầu.
- `n == 0`: lần này không chuyển byte; có thể là buffer không còn chỗ hoặc tình huống không tiến triển. **Không coi `0` là đã đủ dữ liệu**.

Với `write(src, position)`:

- `n > 0`: đã ghi `n` byte, `src.position()` tăng tương ứng.
- `n == 0`: chưa chuyển byte nào; cần có chính sách retry/backoff hoặc báo lỗi cho workload phù hợp.
- Ghi không có giá trị `-1` để biểu thị EOF như thao tác đọc.

### 6.1. Mẫu `Future` viết đủ toàn bộ buffer

~~~java
long fileOffset = 0L;

while (src.hasRemaining()) {
    Future<Integer> f = file.write(src, fileOffset);
    int n = f.get();

    if (n <= 0) {
        throw new IOException("Không có tiến triển khi ghi");
    }

    fileOffset += n;
}
~~~

Đây là đoạn minh họa với `file` là `AsynchronousFileChannel` mở quyền ghi, `src` là `ByteBuffer` có dữ liệu sẵn; cần xử lý các checked exception `get()` theo ngữ cảnh. Chương trình mẫu đầy đủ ở phần sau thể hiện cách dùng này.

**Sai lầm nguy hiểm:** khởi phát nhiều `write` trên **cùng một ByteBuffer** trước khi thao tác trước hoàn tất rồi tự tăng offset từ số byte yêu cầu. Bộ đệm bị dùng đồng thời có thể làm dữ liệu/vị trí không xác định theo mong đợi. Với ví dụ tuần tự, chỉ thực hiện lần tiếp theo sau khi đã nhận kết quả của lần hiện tại.

### 6.2. Mẫu callback đọc đủ số byte mục tiêu

Ý tưởng:

~~~text
Đọc tại offset p vào buffer
  |
  +-- n > 0, buffer còn chỗ: đọc tiếp tại p + n
  |
  +-- n > 0, buffer đã đủ: báo thành công
  |
  +-- n == -1: báo EOF sớm nếu còn thiếu byte
  |
  +-- n == 0: xử lý chính sách không tiến triển
  |
  +-- failed(error): báo lỗi
~~~

Phải nhớ rằng `CompletionHandler.completed(n, attachment)` tương ứng **một lần đọc**, không tương ứng luôn cả message hay toàn bộ file.

## 7. Ví dụ Java 17+ hoàn chỉnh: Future để ghi, CompletionHandler để đọc

Chương trình chạy độc lập dưới đây tạo tệp tạm, dùng `Future<Integer>` để ghi đủ `HELLO` vào tệp, sau đó dùng `CompletionHandler` để đọc đủ 5 byte. Kết quả callback được nối với `CompletableFuture` để thread chính có thể đợi hoàn tất và in kết quả có thứ tự.

**Lưu mã thành `AsyncIoFutureHandlerDemo.java`:**

~~~java
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class AsyncIoFutureHandlerDemo {

    public static void main(String[] args) throws Exception {
        Path file = Files.createTempFile("async-io-", ".txt");

        try {
            try (AsynchronousFileChannel channel =
                         AsynchronousFileChannel.open(
                                 file,
                                 StandardOpenOption.READ,
                                 StandardOpenOption.WRITE)) {

                ByteBuffer src = ByteBuffer.wrap(
                        "HELLO".getBytes(StandardCharsets.UTF_8));

                long offset = 0L;
                while (src.hasRemaining()) {
                    Future<Integer> pending = channel.write(src, offset);
                    int n = pending.get();

                    if (n <= 0) {
                        throw new IOException("Future write không tiến triển");
                    }
                    offset += n;
                }

                System.out.println("Future bytes written: " + offset);

                ByteBuffer dst = ByteBuffer.allocate(5);
                CompletableFuture<Integer> done = readFully(
                        channel, dst, 0L);

                int readBytes = done.get(5, TimeUnit.SECONDS);
                dst.flip();

                System.out.println("Handler bytes read: " + readBytes);
                System.out.println("Content: "
                        + StandardCharsets.UTF_8.decode(dst));
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static CompletableFuture<Integer> readFully(
            AsynchronousFileChannel channel,
            ByteBuffer dst,
            long fileOffset) {

        CompletableFuture<Integer> done = new CompletableFuture<>();

        channel.read(dst, fileOffset, fileOffset,
                new CompletionHandler<Integer, Long>() {
                    private int total;

                    @Override
                    public void completed(Integer n, Long currentOffset) {
                        if (n == -1) {
                            done.completeExceptionally(
                                    new EOFException("Tệp kết thúc sớm"));
                            return;
                        }

                        if (n == 0) {
                            done.completeExceptionally(
                                    new IOException("Đọc không tiến triển"));
                            return;
                        }

                        total += n;
                        if (!dst.hasRemaining()) {
                            done.complete(total);
                            return;
                        }

                        long nextOffset = currentOffset + n;
                        try {
                            channel.read(
                                    dst, nextOffset, nextOffset, this);
                        } catch (RuntimeException ex) {
                            done.completeExceptionally(ex);
                        }
                    }

                    @Override
                    public void failed(Throwable error, Long ignoredOffset) {
                        done.completeExceptionally(error);
                    }
                });

        return done;
    }
}
~~~

Chạy với **JDK 17 trở lên**:

~~~bash
javac -encoding UTF-8 AsyncIoFutureHandlerDemo.java
java AsyncIoFutureHandlerDemo
~~~

**Stdout kỳ vọng khi I/O hoàn tất bình thường:**

~~~text
Future bytes written: 5
Handler bytes read: 5
Content: HELLO
~~~

### Giải thích mã và tính đúng đắn

1. `Files.createTempFile` tạo tệp trống và sinh tên khác nhau mỗi lần. Vì stdout không in tên tệp nên kết quả hiển thị ổn định.
2. `AsynchronousFileChannel.open` mở channel với quyền đọc và ghi. Không có `position()` của file channel; mỗi lần gọi cung cấp offset tệp.
3. `Future<Integer> pending = channel.write(src, offset)` khởi phát một lần ghi. `pending.get()` đợi đến khi có số byte thực tế, sau đó `offset += n` để tiếp tục phần còn lại. `src.position()` cũng đã tăng theo số byte ghi.
4. `ByteBuffer.wrap(...)` cung cấp chính xác 5 byte UTF-8 của `HELLO`. Nếu mỗi lần chỉ ghi một phần, vòng lặp vẫn ghi hết trước khi bắt đầu đọc.
5. `readFully(...)` khởi phát lần đọc đầu tiên và trả về `CompletableFuture` biểu diễn **toàn bộ quá trình đọc**, gồm những lần đọc tiếp theo nếu một callback trả về ít byte hơn cần thiết.
6. Mỗi khi `completed(n, currentOffset)` nhận `n > 0`, buffer đã được cập nhật `position`, nên lần đọc tiếp theo dùng **chính buffer** nhưng với offset tệp `currentOffset + n`. Không tự tăng `dst.position()` lần thứ hai.
7. Khi đã đủ 5 byte, callback gọi `done.complete(total)`. Khi xảy ra EOF sớm, không có tiến triển hoặc lỗi I/O, callback dùng `completeExceptionally(...)`.
8. `done.get(5, SECONDS)` chỉ giúp thread chính đợi và áp dụng giới hạn thời gian chờ; callback làm việc trên hạ tầng xử lý I/O của channel. Timeout không tự chứng minh rằng thao tác đã bị hủy.
9. Sau khi hoàn tất, `dst.flip()` chuyển buffer sang trạng thái đọc để giải mã chính xác 5 byte. Chương trình dùng ASCII trong UTF-8 nên không có byte ký tự nhiều byte nằm dở.
10. `try-with-resources` đóng channel rồi `finally` xóa tệp tạm. Nếu có exception, chương trình không giả vờ thành công.

**Giới hạn có chủ đích của ví dụ:** nếu file ổn định và buffer còn chỗ nhưng thao tác đọc trả `0`, ví dụ báo lỗi để tránh vòng lặp không tiến triển. Một ứng dụng mạng hoặc nguồn thay đổi cần chính sách retry/readiness/timeout riêng. Khi timeout xảy ra, chương trình rời `try-with-resources` và đóng channel; cách dừng/cancel thực tế cần được thiết kế rõ trong hệ thống sản xuất.

## 8. `Future` không đồng nghĩa “ứng dụng hoàn toàn không chặn”

Khai báo:

~~~java
Future<Integer> f = channel.read(dst, 0);
int n = f.get();
~~~

là I/O được **khởi phát bất đồng bộ**, nhưng **luồng gọi trở thành chờ đồng bộ tại `get()`**. Nếu cứ gọi `get()` ngay sau mỗi thao tác, hiệu quả về tổ chức luồng có thể rất giống với cách đọc tuần tự.

`Future` vẫn hữu ích trong các trường hợp:

- Tạo I/O rồi xử lý công việc độc lập trước khi cần kết quả.
- Gửi nhiều thao tác **trên các buffer độc lập**, lưu các Future và đợi chúng sau đó.
- Chuyển Future sang một bộ phối hợp khác và để worker chuyên trách đợi kết quả.
- Dùng timeout ở ranh giới yêu cầu khi mô hình thread cho phép chờ.

**`isDone()` + polling liên tục** thường không tốt: tạo busy-spin và khó đặt timeout. Thay bằng `get(timeout, unit)` ở một thread có thể chờ, hoặc dùng callback để nhận tín hiệu khi hoàn thành.

## 9. `CompletionHandler` không tự tạo chuỗi Future và không tự giới hạn tải

Một callback có thể “nối tiếp” thao tác bằng cách gọi lại `read/write` trong `completed`. Tuy nhiên, nếu có hàng chục nghìn kết nối hoặc request, ứng dụng vẫn phải quản lý:

- **Số thao tác đang bay (in-flight):** giới hạn số request và tổng byte/buffer tồn tại đồng thời.
- **Ownership:** ai được đọc/sửa buffer khi thao tác chưa hoàn tất.
- **Thời hạn:** nếu peer không phản hồi, callback có thể chưa đến trong thời gian ứng dụng mong muốn; phải có timeout/hủy/đóng có kiểm soát.
- **Lỗi:** callback `failed` phải được đưa đến log, trạng thái request hoặc Future hoàn tất lỗi; ném exception ra khỏi callback không tương đương với ném từ dòng mã đã khởi phát.
- **Đóng tài nguyên:** channel có thể bị đóng khi còn thao tác chờ; callback/future phải xử lý kết thúc bất thường.
- **Thứ tự nghiệp vụ:** những callback của các thao tác độc lập có thể hoàn thành **không theo thứ tự bắt đầu**, ngay cả khi dữ liệu cần được công bố theo thứ tự.

**Không dùng một `ByteBuffer` mutable cho nhiều thao tác I/O đồng thời** nếu chưa có thiết kế đồng bộ đặc biệt. Thực tế, dùng buffer riêng cho từng request hoặc điều phối một chuỗi thao tác sao cho chỉ có một I/O thao tác trên buffer trong một thời điểm sẽ dễ kiểm tra hơn.

## 10. Khác biệt giữa file và socket bất đồng bộ

### 10.1. File: offset độc lập

`AsynchronousFileChannel.read(dst, position)` đọc theo offset byte được cung cấp. Hai lần đọc tại offset 0 và 4096 là hai yêu cầu khác nhau; chúng có thể hoàn tất theo thứ tự khác nhau và phải có buffer độc lập nếu chạy đồng thời.

Lưu ý rằng Java có thể triển khai I/O file bất đồng bộ bằng thread pool và thao tác hệ điều hành thông thường tùy nền tảng. **Không bảo đảm mỗi thao tác là kernel-native async/zero-copy**, cũng không bảo đảm tăng tốc trên mọi workload. Nếu mục tiêu là hiệu năng, phải đo throughput, CPU, context switches, concurrency và độ trễ.

### 10.2. Socket: byte stream và backpressure

`AsynchronousSocketChannel` dùng TCP byte stream. Một lần `read` có thể trả về ít hơn độ dài message; một lần `write` có thể gửi ít hơn số byte còn trong buffer. Cần **message framing** như length-prefix hoặc delimiter nếu giao thức yêu cầu bản tin.

Một số lỗi đặc trưng cần ghi nhớ:

- `ReadPendingException`: cố khởi phát thao tác đọc khác khi socket đang có một thao tác đọc chưa hoàn tất.
- `WritePendingException`: tương tự cho chiều ghi.
- `NotYetConnectedException`: đọc/ghi trước khi kết nối đã được thiết lập.
- `AsynchronousCloseException`/`ClosedChannelException`: đóng channel có thể ảnh hưởng thao tác đang chờ.

Vì vậy, với socket thường xây dựng một **read loop** tiếp nối sau mỗi kết quả nhận và một **write queue** gửi lần lượt các buffer; không bắn nhiều lần `read` song song trên cùng `AsynchronousSocketChannel` rồi hy vọng chúng tự xếp thứ tự.

`Future<Void> connect(...)` cho kết quả kiểu `Void`; khi kết nối thành công thì `get()` trả `null`. Với callback connect, `completed(null, attachment)` chỉ báo thao tác kết nối hoàn thành, không tự bảo đảm server đã xử lý request.

## 11. Executor, thread pool và sự an toàn của callback

Java NIO.2 dùng nhóm luồng (asynchronous channel group) hoặc executor theo API và loại channel. Chẳng hạn:

- `AsynchronousFileChannel.open(path, options, executor, attrs...)` có overload cho `ExecutorService`.
- `AsynchronousChannelGroup.withFixedThreadPool(...)` có thể cung cấp group cho các socket/server channel bất đồng bộ.

**Không gắn một executor riêng cho từng thao tác nhỏ**: dễ gây bùng nổ số thread, tốn bộ nhớ và chi phí chuyển ngữ cảnh. Hãy thiết kế executor/group theo vòng đời ứng dụng hoặc service.

Nếu callback chia sẻ biến đếm/trạng thái giữa các thread, cần bảo đảm an toàn bộ nhớ bằng ownership, `AtomicInteger`, đồng bộ, immutable data hoặc cơ chế điều phối phù hợp. `CompletableFuture.complete(...)` cung cấp điểm công bố kết quả thuận tiện; nhưng các đối tượng mutable như `ByteBuffer` vẫn cần quy tắc chỉ sử dụng sau khi I/O hoàn tất.

### Một lưu ý về deadlock và starvation

Đừng gọi `future.get()` bên trong callback nếu Future đó chỉ có thể hoàn thành khi một tác vụ khác chạy trên cùng một executor đã bị bạn chặn hết worker. Điều này có thể dẫn đến starvation/deadlock.

Cũng đừng chạy phân tích dữ liệu nặng nhiều giây trong callback của executor I/O dùng chung. Hãy chuyển phần xử lý CPU lớn sang executor tính toán nếu cần.

## 12. Timeout, cancellation và lifecycle

### 12.1. Timeout của người đợi

~~~java
try {
    int n = future.get(3, TimeUnit.SECONDS);
} catch (TimeoutException ex) {
    // Quyết định retry, cancel hoặc đóng channel theo hợp đồng.
}
~~~

Đoạn minh họa dùng `Future<Integer> future` đã có và import `TimeoutException`, `TimeUnit`. **Timeout ở `get` là timeout chờ kết quả**, không bảo đảm I/O đã dừng ngay.

Một số overload của API socket còn cho phép cấu hình timeout trực tiếp khi thực hiện `read/write` bất đồng bộ; cần xem hợp đồng API riêng của overload đó, thay vì áp dụng logic timeout của `Future.get` cho mọi channel.

### 12.2. Cancellation không đồng nghĩa rollback

Nếu gọi:

~~~java
boolean cancelled = future.cancel(true);
~~~

việc hủy thành công Future **không có nghĩa các byte đã ghi trước đó được hoàn tác**. Thao tác I/O có thể đã chuyển một phần trước khi hủy hoặc channel có thể phải đóng tùy trường hợp/triển khai.

Tránh đọc hoặc tái sử dụng buffer ngay sau yêu cầu cancel nếu chưa chắc tác vụ trước đã chấm dứt quyền truy cập. Các lựa chọn an toàn thường là đợi xác nhận hoàn tất, tổ chức ownership nghiêm ngặt hoặc đóng và bỏ channel/buffer theo chính sách quản lý tài nguyên.

### 12.3. Đóng channel

Một channel có thể đóng khi các I/O còn chờ; những yêu cầu đó có thể thất bại. Với chương trình xử lý nhiều request, phải bảo đảm **mọi request có trạng thái kết thúc**: thành công, lỗi, hủy hoặc timeout. Đừng để callback lỗi mất tín hiệu và caller đợi mãi.

`try-with-resources` hợp lý khi vòng đời I/O được quản lý trong một block; tuy nhiên với callback, **không đóng channel ngay sau khi đăng ký callback** nếu còn cần nó cho việc hoàn tất. Phải giữ channel sống đến khi các thao tác cần thiết hoàn tất hoặc bị hủy có kiểm soát.

## 13. Các lỗi phổ biến và cách sửa

| Sai lầm | Hậu quả | Cách đúng |
| --- | --- | --- |
| Gọi `Future.get()` ngay trên event-loop/UI thread | Chặn luồng quan trọng | Đưa việc đợi sang worker thích hợp hoặc dùng callback |
| Nghĩ `future.isDone() == true` tức thành công | Bỏ sót lỗi/hủy | Dùng `get()` hoặc kiểm tra trạng thái và xử lý exception |
| Bỏ qua `ExecutionException.getCause()` | Không thấy nguyên nhân I/O | Ghi và phân loại nguyên nhân gốc |
| Nuốt `InterruptedException` | Mất tín hiệu dừng | Phục hồi interrupt hoặc truyền theo chính sách |
| Giả định một lần `read/write` chuyển đủ byte | Mất dữ liệu hoặc ghi thiếu | Dùng kết quả thực tế để tiếp tục |
| Đọc buffer trước khi I/O hoàn tất | Dữ liệu/chỉ số chưa ổn định | Giữ ownership cho thao tác đang chạy |
| Một buffer cho nhiều I/O song song | Tranh chấp `position/limit` | Dùng buffer riêng hoặc xếp hàng |
| Quên `failed(...)` | Request chờ mãi | Kết thúc request bằng trạng thái lỗi |
| Đóng channel ngay sau `read(..., handler)` | I/O đang chạy thất bại | Giữ channel sống qua vòng đời thao tác |
| Làm CPU-bound/blocking việc dài trong callback | Giảm khả năng xử lý I/O khác | Chuyển việc sang executor phù hợp |
| Retry liên tục khi `n == 0` | Busy-spin, phí CPU | Chờ điều kiện thích hợp hoặc fail fast có chủ đích |
| Nhầm `get(timeout)` với hủy I/O | Tác vụ vẫn đang dùng buffer | Hủy/đóng/quản lý vòng đời riêng |
| Gọi 2 lần `read` đồng thời trên cùng async socket | `ReadPendingException` | Xây read loop tuần tự |
| Phụ thuộc callback hoàn thành đúng thứ tự gửi | Lỗi sắp xếp nghiệp vụ | Gắn request id và điều phối thứ tự |

## 14. Chọn `Future` hay `CompletionHandler`?

**Chọn `Future`** khi bạn muốn viết luồng điều khiển dễ đọc, có worker được phép đợi hoặc cần áp dụng timeout cho việc chờ. Ví dụ batch job xử lý một số tệp độc lập: khởi phát nhiều thao tác trên các buffer riêng, làm việc khác, rồi lấy kết quả khi cần.

**Chọn `CompletionHandler`** khi cần nhiều I/O đồng thời và muốn hệ thống thông báo ngay khi một thao tác hoàn thành, phù hợp với pipeline, socket server hoặc chuỗi tác vụ theo sự kiện. Phải quản lý trạng thái callback và giới hạn tải.

**Kết hợp `CompletionHandler` với `CompletableFuture`** là cách hữu ích để chuyển callback thành một biểu diễn kết quả có thể phối hợp. Ví dụ ở trên cho thấy một `CompletableFuture` đại diện **cả quá trình đọc đầy đủ**, dù các thao tác nền diễn ra qua nhiều callback.

Lựa chọn còn phụ thuộc thư viện, cấu trúc executor, số kết nối/tệp đồng thời, yêu cầu độ trễ, xử lý lỗi và mức độ dễ kiểm thử. **Hai API không tự làm cho I/O nhanh hơn chỉ bởi kiểu thông báo kết quả**.

## 15. Câu hỏi tự kiểm tra

**Câu 1.** `Future<Integer> f = channel.read(buffer, 0)` vừa trả về. Dữ liệu trong `buffer` đã sẵn để xử lý chưa?

**Đáp án:** Không thể kết luận. Phải đợi/kiểm tra thao tác hoàn tất, xử lý kết quả rồi mới dùng buffer.

**Câu 2.** `f.get()` ném `ExecutionException`. I/O thành công hay thất bại?

**Đáp án:** Thao tác đã thất bại; xem `getCause()` để biết nguyên nhân. `Future.isDone()` vẫn có thể trả `true`.

**Câu 3.** Một lời gọi bất đồng bộ ghi 10 byte nhưng callback nhận `completed(4, attachment)`. Có thể coi đã ghi đủ chưa?

**Đáp án:** Chưa. Đã chuyển 4 byte; phải giữ 6 byte còn lại và tiếp tục theo vị trí/buffer thực tế.

**Câu 4.** Đọc qua `AsynchronousFileChannel` trả `-1` khi buffer đang cần 5 byte nữa. Ý nghĩa gì?

**Đáp án:** EOF trước khi đủ dữ liệu dự kiến. Nếu giao thức/yêu cầu đòi đúng 5 byte còn thiếu, đây là lỗi dữ liệu không đầy đủ.

**Câu 5.** Gọi `get(1, SECONDS)` bị `TimeoutException`. Có được sửa ngay `ByteBuffer` đang cấp cho I/O đó?

**Đáp án:** Không mặc định. Timeout chỉ kết thúc việc chờ; I/O có thể còn chạy và đang sử dụng buffer.

**Câu 6.** Có thể gọi thêm `read(...)` trên một `AsynchronousSocketChannel` đang có một lần đọc chờ không?

**Đáp án:** Không; có thể gặp `ReadPendingException`. Cần đọc nối tiếp sau mỗi lần hoàn tất.

**Câu 7.** Vì sao handler có tham số `attachment`?

**Đáp án:** Để callback nhận ngữ cảnh gắn với thao tác, ví dụ offset, request id hoặc trạng thái, mà không phụ thuộc biến toàn cục dùng chung.

**Câu 8.** Nếu callback `failed(...)` chỉ ghi log mà không hoàn tất Promise/Future điều phối, điều gì xảy ra?

**Đáp án:** Thành phần đang chờ có thể treo vô hạn. Đường lỗi cũng phải công bố trạng thái kết thúc.

## 16. Ghi nhớ

- **`Future`:** nhận đối tượng đại diện kết quả; `get()` có thể chặn người đợi, lỗi thực thi nằm trong `ExecutionException`; timeout chờ không tự dừng I/O.
- **`CompletionHandler`:** đưa kết quả/lỗi qua `completed(...)` và `failed(...)`; attachment mang ngữ cảnh; callback phải ngắn, có kiểm soát trạng thái và báo lỗi.
- **Bất đồng bộ vẫn có partial I/O**: tiếp tục theo số byte trả về, không giả định một lần là đủ.
- **Buffer có ownership trong thời gian I/O đang chạy**; không tái sử dụng hoặc sửa buffer trước khi kết thúc.
- **Socket bất đồng bộ** cần điều phối số thao tác đang chờ, framing, backpressure và vòng đời kết nối.
- **Giải pháp đúng** là giải pháp có trạng thái kết thúc rõ ràng cho mọi thao tác: thành công, thất bại, timeout hoặc hủy có kiểm soát.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="asynchronous-io-004">AsynchronousSocketChannel và AsynchronousServerSocketChannel</a>

<details>
<summary>Xem chi tiết</summary>

# AsynchronousSocketChannel và AsynchronousServerSocketChannel trong Java NIO.2

Java NIO.2 cung cấp hai API kết nối TCP bất đồng bộ quan trọng: `AsynchronousServerSocketChannel` để lắng nghe và chấp nhận kết nối, và `AsynchronousSocketChannel` để kết nối, đọc, ghi trên một socket. Thay vì buộc luồng gọi chờ I/O hoàn tất, các phương thức có thể **khởi phát thao tác rồi trả về ngay**; kết quả được nhận bằng `Future` hoặc `CompletionHandler`.

Chữ “asynchronous” nói về **mô hình hoàn thành thao tác**, không phải bảo đảm mọi hệ điều hành đều dùng cùng cơ chế I/O ở nhân hệ điều hành, cũng không có nghĩa callback của ứng dụng không bao giờ chạy trên một luồng có thể bị chặn bởi công việc khác. Khi dùng API này phải quản lý **trạng thái kết nối, buffer, thao tác đang chờ, thứ tự callback, lỗi và đóng tài nguyên**.

Bài này hướng dẫn hai channel, cách mở và sử dụng, hai kiểu nhận kết quả, ví dụ Java 17 chạy độc lập, các giới hạn liên quan đến concurrent read/write và những lỗi dễ gặp.

## 1. Phân biệt hai loại channel và luồng hoạt động

| Lớp | Trách nhiệm | Phương thức chính |
| --- | --- | --- |
| `AsynchronousServerSocketChannel` | Bind vào địa chỉ TCP, chờ và chấp nhận kết nối mới | `open()`, `bind(...)`, `accept()`, `accept(attachment, handler)`, `close()` |
| `AsynchronousSocketChannel` | Kết nối tới server hoặc thao tác trên kết nối được server nhận | `open()`, `connect(...)`, `read(...)`, `write(...)`, `shutdownInput()`, `shutdownOutput()`, `close()` |

Một phiên trao đổi thường có các bước:

```text
Server                                       Client
  |                                           |
  | AsynchronousServerSocketChannel.open()    |
  | bind(localAddress)                        |
  | accept()   [đang chờ hoàn thành]          | AsynchronousSocketChannel.open()
  |                                           | connect(serverAddress)
  |<----------- TCP connection -------------->|
  | accept hoàn thành, trả về                  | connect hoàn thành
  | AsynchronousSocketChannel của server      |
  | read()    [đang chờ] <--------------------| write()
  | write() ------------------------------->  | read()
  | đóng các channel theo vòng đời            | đóng channel
```

**Quan trọng:** `accept()` không trả về một `SocketChannel` đồng bộ mà là một `AsynchronousSocketChannel` mới đại diện cho kết nối đã được chấp nhận. `AsynchronousServerSocketChannel` chỉ dùng cho **lắng nghe và accept**, không dùng để gọi `read()` / `write()` dữ liệu trên từng client.

Các lớp nằm trong package `java.nio.channels`. Chúng là API khác với `ServerSocketChannel` và `SocketChannel` được dùng cùng `Selector`. Không đăng ký `AsynchronousSocketChannel` với `Selector`, không gọi `configureBlocking(false)` và không áp dụng `SelectionKey.OP_READ/OP_WRITE` cho loại channel bất đồng bộ này.

## 2. Mở server và client

Server thường mở channel, `bind` địa chỉ cục bộ rồi bắt đầu một thao tác `accept`:

```java
// Minh họa trong một phương thức cho phép ném IOException.
AsynchronousServerSocketChannel server =
        AsynchronousServerSocketChannel.open();
server.bind(new InetSocketAddress("127.0.0.1", 9000));

Future<AsynchronousSocketChannel> pending = server.accept();
```

Đoạn mã là **minh họa**, cần import các lớp `java.nio.channels`, `java.net`, `java.util.concurrent` và đóng `server` trong `finally` / `try-with-resources`. Nếu port 9000 đang được dùng hoặc địa chỉ không thể bind, sẽ có `IOException`. Lệnh `accept()` đặt một yêu cầu đang chờ; `pending.get()` mới chặn luồng gọi cho tới khi có kết quả.

Client tạo channel và khởi động kết nối:

```java
// Minh họa, bỏ qua quản lý close để tập trung vào API.
AsynchronousSocketChannel client = AsynchronousSocketChannel.open();
Future<Void> connecting =
        client.connect(new InetSocketAddress("127.0.0.1", 9000));
// Sau khi connecting hoàn thành thành công mới tiến hành I/O.
```

Với dạng `Future`, có thể gọi `connecting.get()` để chờ kết nối, nhưng chính việc chờ này **làm luồng gọi bị chặn**. API vẫn bất đồng bộ ở bước khởi tạo operation; việc gọi `get()` ngay lập tức là một quyết định của ứng dụng, không biến channel trở lại `SocketChannel` đồng bộ.

Các cách mở `open()` sử dụng **asynchronous channel group mặc định** của provider. Ngoài ra có overload nhận `AsynchronousChannelGroup` để nhiều channel dùng chung nhóm luồng và tài nguyên do ứng dụng cấu hình.

## 3. Hai mô hình nhận kết quả hoàn thành

### 3.1. Dùng Future: dễ viết logic theo từng bước

Các phương thức như `connect(remote)`, `read(buffer)`, `write(buffer)` và `accept()` trả `Future`. Giá trị hoàn thành phụ thuộc thao tác:

| Lời gọi | Kiểu Future | Dữ liệu nhận qua `get()` |
| --- | --- | --- |
| `client.connect(remote)` | `Future<Void>` | `null` khi kết nối thành công |
| `server.accept()` | `Future<AsynchronousSocketChannel>` | Kết nối client vừa được chấp nhận |
| `client.read(dst)` | `Future<Integer>` | Số byte thực đọc; `-1` khi EOF |
| `client.write(src)` | `Future<Integer>` | Số byte thực ghi |

Ví dụ:

```java
// Đoạn minh họa trong phương thức xử lý checked exceptions.
Future<Integer> operation = client.read(buffer);

// Có thể làm công việc khác tại đây.
Integer n = operation.get(5, TimeUnit.SECONDS);
```

`Future.get(timeout, unit)` đặt **thời hạn chờ cho chính luồng gọi**; khi `get` báo `TimeoutException`, không được mặc định rằng I/O nền đã bị hủy. Muốn có chính sách timeout thực sự phải kiểm soát operation và channel; việc `cancel()` trên một thao tác I/O có những hệ quả provider/API cần xem xét (có trường hợp khiến channel không còn dùng được cho thao tác cùng loại), nên đừng tùy tiện tiếp tục sử dụng như thể không có yêu cầu nào đang chạy.

`Future.get()` có thể ném `InterruptedException` nếu luồng chờ bị ngắt, `ExecutionException` nếu thao tác nền thất bại, `TimeoutException` nếu quá hạn ở overload có timeout. Nên xử lý đúng từng trường hợp và giải phóng tài nguyên.

### 3.2. Dùng CompletionHandler: kết quả đến qua callback

Overload bất đồng bộ nhận **attachment** và **handler** để gửi kết quả khi thao tác kết thúc. Giao diện chính:

```java
public interface CompletionHandler<V, A> {
    void completed(V result, A attachment);
    void failed(Throwable exc, A attachment);
}
```

Đây là chữ ký để minh họa, không phải file code độc lập. `V` là kiểu kết quả của operation (`Integer`, `Void`, hoặc `AsynchronousSocketChannel`); `A` là kiểu state đính kèm do ứng dụng chọn.

Ví dụ bắt đầu accept bằng callback:

```java
// Minh họa: server đã bind, cần import CompletionHandler.
server.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
    @Override
    public void completed(AsynchronousSocketChannel peer, Void unused) {
        // peer là kết nối đã accept; phải quản lý vòng đời peer.
        handleNewClient(peer); // Hàm nghiệp vụ minh họa.
    }

    @Override
    public void failed(Throwable error, Void unused) {
        reportAcceptFailure(error); // Hàm nghiệp vụ minh họa.
    }
});
```

`handleNewClient` và `reportAcceptFailure` là **hàm ứng dụng chưa định nghĩa**, nên đoạn này không phải chương trình chạy độc lập. Không gọi `Future.get()` trong callback để chờ chính operation; callback đã là điểm hoàn thành của operation đó.

Callback có thể được chạy bởi một thread của asynchronous channel group. **Không giả định callback luôn chạy trên cùng thread đã bắt đầu I/O, hoặc các callback của một ứng dụng luôn có một thứ tự dễ dự đoán**. Công việc nặng, I/O chặn và lời gọi khóa lâu trong callback có thể làm giảm khả năng phục vụ các kết nối khác. Hãy chuyển công việc nặng ra executor thích hợp nếu cần.

## 4. Accept bất đồng bộ: không chỉ accept một lần

Một lời gọi `server.accept()` thường ứng với **một kết nối mới**. Sau khi yêu cầu hoàn tất, muốn nhận client tiếp theo thì phải gọi `accept` mới.

Với kiểu callback, mẫu ý tưởng:

```java
// Phác thảo, ở trong một thành phần đang quản lý server.
void acceptNext(AsynchronousServerSocketChannel server) {
    server.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
        @Override
        public void completed(AsynchronousSocketChannel peer, Void ignored) {
            if (server.isOpen()) {
                acceptNext(server); // Lên lịch accept tiếp theo.
            }
            servePeer(peer); // Hàm ứng dụng, không làm nghẽn callback.
        }

        @Override
        public void failed(Throwable error, Void ignored) {
            if (server.isOpen()) {
                onAcceptFailure(error); // Ghi nhận lỗi, điều phối retry.
            }
        }
    });
}
```

Đây là **phác thảo kiến trúc**, chưa cung cấp các hàm `servePeer` / `onAcceptFailure`, retry/backoff hay quản lý đóng socket; không phải chương trình độc lập. Với server chạy thực tế cần bảo đảm luôn có quy ước đăng ký accept tiếp theo sau những lỗi có thể phục hồi, tránh lặp lỗi tức thì không giới hạn.

**Giới hạn bắt buộc:** Một `AsynchronousServerSocketChannel` chỉ cho phép **một yêu cầu accept đang chờ**. Gọi accept khác khi accept trước chưa hoàn tất có thể ném `AcceptPendingException`. Không dùng nhiều thread gọi `accept` không phối hợp trên cùng server channel rồi trông chờ mỗi lời gọi sẽ trở thành một hàng đợi accept.

Trong `completed`, channel `peer` trả về đã được cấp cho ứng dụng; phải có chủ sở hữu và chính sách `close` cho nó, độc lập với chuyện server vẫn tiếp tục chấp nhận client mới.

## 5. Read/write bất đồng bộ và partial I/O

Khi `AsynchronousSocketChannel` hoàn thành một lời gọi đọc hoặc ghi, số byte hoàn thành **không bắt buộc bằng toàn bộ số byte còn lại trong ByteBuffer**:

```java
Future<Integer> r = client.read(input);
Future<Integer> w = client.write(output);
```

Đây là hai thao tác minh họa; chúng cần các buffer hợp lệ, channel ở trạng thái kết nối đúng và chương trình điều phối tránh thao tác trùng loại. Với TCP, bên gửi và bên nhận luôn xử lý **byte stream**, không phải thông điệp có ranh giới tự nhiên.

| Kết quả | `read(dst)` | `write(src)` |
| --- | --- | --- |
| Số dương `n` | Nhận `n` byte, `dst.position()` tăng `n` | Gửi `n` byte, `src.position()` tăng `n` |
| `0` | Chưa đọc byte nào ở operation này, không phải EOF | Chưa ghi byte nào ở operation này, không phải đã gửi xong |
| `-1` | EOF đầu vào (peer đóng hướng gửi) | Không phải kết quả thành công thông thường của write |

Ví dụ ứng dụng cần gửi 10 byte, operation ghi đầu tiên hoàn thành với kết quả 3. Khi callback đến, `output.position()` đã tăng lên 3; **phải tiếp tục gửi 7 byte còn lại từ chính buffer đó**, không `rewind()` rồi gửi lại từ đầu và cũng không thả buffer khi chưa hoàn tất.

Tương tự, một lần đọc vào buffer 8 byte có thể trả 2; chưa thể giải mã một frame dài 8 byte. Ứng dụng phải bảo toàn 2 byte và bắt đầu yêu cầu read tiếp theo để lấy thêm dữ liệu.

### Một thao tác đọc và một thao tác ghi đang chờ

`AsynchronousSocketChannel` cho phép một yêu cầu đọc và một yêu cầu ghi diễn ra đồng thời trên cùng kết nối, nhưng:

- Khi **đang có một read chưa hoàn tất**, khởi phát read thứ hai trên cùng channel có thể ném `ReadPendingException`.
- Khi **đang có một write chưa hoàn tất**, khởi phát write thứ hai trên cùng channel có thể ném `WritePendingException`.
- Chỉ bắt đầu lần read/write **cùng loại** tiếp theo sau khi lần trước hoàn thành thành công hoặc thất bại và trạng thái được điều phối.
- Không thay đổi nội dung, `position` hoặc `limit` của buffer đang được dùng cho một operation khi operation chưa hoàn thành. Tránh chuyển buffer này cho luồng khác sửa đồng thời.
- Sau khi đã hoàn thành read/write, callback hoặc code xử lý `Future` có thể dùng số byte thật và buffer state để quyết định xử lý tiếp.

Đây là sự khác biệt quan trọng so với `Selector`: AIO báo **operation completed**, không phải chỉ có `OP_READ/OP_WRITE` readiness. Dù vậy, completion vẫn có thể chỉ xử lý một phần frame.

### Dùng vòng lặp với Future để xử lý đầy đủ buffer

Đoạn sau là **mẫu minh họa** khi caller có thể chờ kết quả, xử lý `InterruptedException`, `ExecutionException`, `TimeoutException` và có `AsynchronousSocketChannel client` đã kết nối:

```java
ByteBuffer data = ByteBuffer.wrap(new byte[] {1, 2, 3, 4});
while (data.hasRemaining()) {
    int n = client.write(data).get(5, TimeUnit.SECONDS);
    if (n < 0) {
        throw new IOException("Unexpected write result");
    }
    // n==0: chưa gửi thêm; thử operation kế tiếp theo chính sách timeout.
}
```

Chỉ khi `data.hasRemaining()` bằng `false` mới coi byte của buffer đã được channel chấp nhận đủ. Việc đó **không xác nhận ứng dụng bên nhận đã đọc hoặc xử lý dữ liệu**; muốn xác nhận ở tầng nghiệp vụ cần giao thức phản hồi/ACK của ứng dụng.

Với `CompletionHandler`, mẫu xử lý partial write là giữ buffer dưới dạng attachment (hoặc state của kết nối), và gọi `write()` tiếp khi `completed` cho biết còn byte:

```java
// Phác thảo trong thành phần sở hữu channel và buffer.
CompletionHandler<Integer, ByteBuffer> handler =
        new CompletionHandler<>() {
            @Override
            public void completed(Integer n, ByteBuffer pending) {
                if (n < 0) {
                    reportWriteFailure(new IOException("Negative write"));
                } else if (pending.hasRemaining()) {
                    channel.write(pending, pending, this);
                } else {
                    onFrameSent();
                }
            }

            @Override
            public void failed(Throwable error, ByteBuffer pending) {
                reportWriteFailure(error);
            }
        };

channel.write(outbound, outbound, handler);
```

Đây là **phác thảo**, cần `channel`, `outbound`, `onFrameSent`, `reportWriteFailure` và cơ chế ownership/đóng channel. Với lỗi hoặc timeout không được khởi phát lại tùy tiện một operation khi trạng thái channel không còn tin cậy. Ứng dụng sản xuất còn cần hạn mức số byte chờ, số lần thử, deadline và chính sách backpressure.

## 6. Chương trình Java 17 hoàn chỉnh: server accept bằng handler, client kết nối và trao đổi bằng Future

Ví dụ dưới đây tạo server TCP trên địa chỉ loopback với **cổng động** để tránh phụ thuộc vào dịch vụ bên ngoài. Server đăng ký `accept` bằng `CompletionHandler`, dùng `CompletableFuture` chuyển kết quả callback sang luồng điều khiển ví dụ. Client sử dụng `Future` để kết nối và thực hiện I/O. Hai đầu trao đổi các byte ASCII `PING` và `PONG`; hàm đọc/ghi lặp theo byte thực hoàn thành, không dựa vào giả định một operation xử lý hết.

Tạo tệp **`AsyncSocketDemo.java`**:

```java
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class AsyncSocketDemo {
    private AsyncSocketDemo() {}

    public static void main(String[] args) throws Exception {
        try (AsynchronousServerSocketChannel server =
                    AsynchronousServerSocketChannel.open();
             AsynchronousSocketChannel client =
                    AsynchronousSocketChannel.open()) {

            server.bind(new InetSocketAddress(
                    InetAddress.getLoopbackAddress(), 0));

            CompletableFuture<AsynchronousSocketChannel> accepted =
                    new CompletableFuture<>();

            server.accept(null,
                    new CompletionHandler<AsynchronousSocketChannel, Void>() {
                        @Override
                        public void completed(
                                AsynchronousSocketChannel peer,
                                Void attachment) {
                            accepted.complete(peer);
                        }

                        @Override
                        public void failed(Throwable error, Void attachment) {
                            accepted.completeExceptionally(error);
                        }
                    });

            client.connect(server.getLocalAddress())
                    .get(5, TimeUnit.SECONDS);

            try (AsynchronousSocketChannel peer =
                    accepted.get(5, TimeUnit.SECONDS)) {

                writeFully(client, ByteBuffer.wrap(
                        "PING".getBytes(StandardCharsets.US_ASCII)));

                ByteBuffer request = ByteBuffer.allocate(4);
                readFully(peer, request);
                request.flip();
                System.out.println("server received="
                        + StandardCharsets.US_ASCII.decode(request));

                writeFully(peer, ByteBuffer.wrap(
                        "PONG".getBytes(StandardCharsets.US_ASCII)));

                ByteBuffer response = ByteBuffer.allocate(4);
                readFully(client, response);
                response.flip();
                System.out.println("client received="
                        + StandardCharsets.US_ASCII.decode(response));
            }
        }
    }

    private static void writeFully(AsynchronousSocketChannel channel,
                                   ByteBuffer data) throws Exception {
        while (data.hasRemaining()) {
            int n = channel.write(data).get(5, TimeUnit.SECONDS);
            if (n < 0) {
                throw new IOException("Unexpected write result");
            }
        }
    }

    private static void readFully(AsynchronousSocketChannel channel,
                                  ByteBuffer data) throws Exception {
        while (data.hasRemaining()) {
            int n = channel.read(data).get(5, TimeUnit.SECONDS);
            if (n < 0) {
                throw new EOFException("Stream ended before frame was complete");
            }
        }
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac AsyncSocketDemo.java
java AsyncSocketDemo
```

Kết quả khi loopback hoạt động bình thường và I/O hoàn tất trong thời hạn của ví dụ:

```text
server received=PING
client received=PONG
```

**Giải thích luồng thực thi:**

1. `server.bind(..., 0)` yêu cầu hệ điều hành chọn một cổng còn trống trên địa chỉ loopback.
2. `server.accept(null, handler)` đăng ký yêu cầu nhận kết nối và **trả về ngay**; khi hoàn thành, callback đặt channel nhận được vào `accepted`.
3. `client.connect(...)` khởi động kết nối TCP; `get(5, TimeUnit.SECONDS)` chờ kết quả trong demo.
4. `accepted.get(...)` lấy channel phía server. Nó là một `AsynchronousSocketChannel` riêng với `client`.
5. `writeFully(client, ...)` gửi các byte của `PING`. `readFully(peer, ...)` nhận đủ 4 byte và `flip()` trước khi giải mã.
6. Server ghi `PONG`, client đọc đủ 4 byte và in kết quả. `try-with-resources` đóng peer, client và server.

Hàm `readFully` và `writeFully` gọi các **operation bất đồng bộ** nhưng sau đó chờ `Future.get()`, nên ví dụ điều khiển tuần tự để dễ học. Một server AIO có tính mở rộng cao thường tổ chức state/callback để không chặn luồng điều phối cho mỗi connection.

Ví dụ cố ý dùng giao thức **khung cố định 4 byte**, không phải framing cho mọi TCP message. Trong giao thức thực tế, cần đặt giới hạn kích thước frame và giữ state cho frame chưa hoàn tất. Khi `Future.get` timeout, code demo thoát khối `try` và đóng channel thay vì giả định operation tự ngừng. Các hàm minh họa không có backoff cho trường hợp operation liên tục hoàn thành với 0 byte; ví dụ loopback nhỏ không nhằm triển khai server chịu tải.

## 7. Xử lý state theo kết nối và callback mà không chặn

Khi server có nhiều client, không nên giữ một biến `ByteBuffer` chung cho tất cả kết nối. Mỗi `AsynchronousSocketChannel` có thể có một đối tượng `Session` chứa:

```java
// Định nghĩa minh họa cho server nhiều kết nối.
final class Session {
    final AsynchronousSocketChannel channel;
    final ByteBuffer inbound = ByteBuffer.allocate(8192);
    final java.util.ArrayDeque<ByteBuffer> outgoing =
            new java.util.ArrayDeque<>();
    boolean inputEnded;
    int expectedFrameLength = -1;

    Session(AsynchronousSocketChannel channel) {
        this.channel = channel;
    }
}
```

Đây là phần minh họa, không có server đầy đủ hay hàng đợi thread-safe. Một thiết kế thực tế phải làm rõ ai sở hữu `Session` và liệu callback có thể chạy đồng thời hay không.

Chuỗi xử lý điển hình:

1. Khi accept xong, tạo `Session` cho client.
2. Bắt đầu một `read` với `session.inbound` và đính kèm `session` vào handler.
3. Khi read hoàn thành: nếu `-1`, xử lý EOF; nếu nhận dữ liệu, `flip()` để phân tích các frame đã đủ, `compact()` giữ byte chưa hoàn tất; sau đó xin read mới nếu kết nối còn mở và có chỗ trong buffer.
4. Khi một response sẵn sàng, đưa `ByteBuffer` vào hàng đợi output và **chỉ khởi động một write đang chờ** cho từng channel. Sau khi write hoàn thành, nếu buffer còn byte thì tiếp tục; nếu hết thì xử lý buffer kế tiếp.
5. Nếu output queue quá lớn, áp dụng **backpressure**: giới hạn số request, số byte đang chờ, tạm dừng nhận input theo giao thức hoặc đóng kết nối chậm. Không tạo vô hạn buffer khi client không đọc.

`ByteBuffer.flip()` chuyển từ ghi vào buffer sang đọc dữ liệu đã có; `compact()` giữ phần byte chưa được parser tiêu thụ. Gọi `clear()` khi chưa xử lý hết sẽ làm mất ranh giới dữ liệu dở; gọi `rewind()` trên buffer gửi còn byte có thể gây gửi trùng.

Khi hai callback read và write chạy gần nhau, việc cùng sửa `Session` cần sự điều phối cụ thể (ví dụ một state machine và single-owner executor, hoặc khóa thích hợp). **Không suy ra callback được gọi tuần tự chỉ vì cùng channel**.

## 8. AsynchronousChannelGroup và tài nguyên thread

`AsynchronousChannelGroup` là nhóm channel bất đồng bộ liên quan một provider, cho phép kiểm soát tài nguyên dùng để xử lý những completion task. Thay vì dùng group mặc định, ứng dụng có thể tạo group với executor:

```java
// Đoạn minh họa, cần IOException/try-finally và quản lý executor.
ExecutorService executor = Executors.newFixedThreadPool(4);
AsynchronousChannelGroup group =
        AsynchronousChannelGroup.withThreadPool(executor);

AsynchronousServerSocketChannel server =
        AsynchronousServerSocketChannel.open(group);

AsynchronousSocketChannel client =
        AsynchronousSocketChannel.open(group);
```

Đoạn này cần import `java.util.concurrent.ExecutorService`, `Executors` và `java.nio.channels.AsynchronousChannelGroup`; không phải chương trình độc lập. Trong thực tế **phải đóng các channel, shutdown group và quản lý executor/thread lifecycle** theo API cùng provider. Một executor phục vụ callback cũng không nên bị chiếm bởi quá nhiều tác vụ chặn lâu.

Các điểm cần biết:

- Không giả định số lượng thread callback đúng bằng số lượng kết nối TCP; một group có thể phục vụ nhiều channel.
- Implementation có thể khác nhau giữa hệ điều hành/JDK: `AsynchronousSocketChannel` là API bất đồng bộ, không bắt buộc native kernel AIO cho mọi platform.
- `CompletionHandler.failed` phải xử lý lỗi theo trạng thái kết nối; lỗi một client không nên khiến tất cả callback bị bỏ không xử lý.
- Group shutdown và channel close là hai trách nhiệm khác nhau. Không ngừng group giữa lúc còn operation cần chạy hoặc chưa xử lý hết kết quả mà không có chính sách phục hồi.

## 9. Timeout, đóng channel và hủy thao tác

Các overload callback của `AsynchronousSocketChannel` cho phép timeout I/O:

```java
// Minh họa: channel và dst đã khai báo.
channel.read(dst, 5, TimeUnit.SECONDS, state,
        new CompletionHandler<Integer, Session>() {
            @Override
            public void completed(Integer n, Session s) {
                onReadComplete(n, s);
            }

            @Override
            public void failed(Throwable error, Session s) {
                onReadFailure(error, s);
            }
        });
```

Đây là mẫu không độc lập: `channel`, `dst`, `state`, `Session`, `onReadComplete` và `onReadFailure` là các phần của ứng dụng. Khác với `Future.get(5,SECONDS)` (chỉ giới hạn **lúc chờ kết quả**), timeout trên overload `read/write` là một tham số **của chính operation**.

Khi operation timeout hoặc bị hủy, hợp đồng của `AsynchronousSocketChannel` có thể khiến khả năng tiếp tục đọc/ghi ở channel trở nên không được bảo đảm trong một số trường hợp. Với kết nối gặp lỗi nghiêm trọng, cách an toàn thường là đánh dấu session thất bại, hủy tác vụ liên quan, đóng channel và dọn state thay vì cố tái sử dụng buffer/channel một cách mù quáng.

Một số trường hợp cần xử lý riêng:

| Tình huống | Điều cần làm |
| --- | --- |
| `UnresolvedAddressException` hoặc `UnsupportedAddressTypeException` khi connect | Kiểm tra địa chỉ và loại `SocketAddress` |
| Connect thất bại | Operation báo thất bại; không coi channel là kết nối hợp lệ |
| `ReadPendingException` hoặc `WritePendingException` | Có operation cùng loại đang chờ; sửa state machine |
| `AcceptPendingException` | Không phát lệnh accept thứ hai trước khi lệnh đầu hoàn thành |
| `AsynchronousCloseException` hoặc `ClosedChannelException` | Channel đã bị đóng trong lúc thao tác/đã đóng trước thao tác |
| `InterruptedException` khi chờ Future | Giữ đúng chính sách interrupt và cleanup, không mất dấu lỗi |
| `ExecutionException` khi chờ Future | Xem nguyên nhân gốc trong `getCause()` |
| `read` hoàn thành với `-1` | Xử lý EOF, half-close hoặc frame còn thiếu theo giao thức |
| `write` hoàn thành với `0` | Giữ buffer và tiến hành operation tiếp theo có kiểm soát |

`AsynchronousSocketChannel.shutdownOutput()` dùng khi muốn đóng hướng gửi nhưng có thể vẫn đọc phản hồi ở hướng nhận, phù hợp một số giao thức TCP half-close. Tuy nhiên gọi `shutdownOutput()` trước khi tất cả byte chờ gửi hoàn thành có thể làm mất dữ liệu theo ý định ứng dụng.

## 10. So sánh với blocking I/O và Selector

| Tiêu chí | `Socket` / blocking channel | `SocketChannel` + `Selector` | `AsynchronousSocketChannel` |
| --- | --- | --- | --- |
| Cơ chế chính | Luồng đợi khi đọc/ghi | Theo dõi readiness với `select()` | Khởi phát operation, nhận completion |
| Cách biết có dữ liệu | `read` trả về sau khi thao tác hoàn tất hoặc EOF | `OP_READ` báo có thể thử đọc | `Future` hoặc callback cho biết read đã hoàn thành |
| Cách biết ghi xong | Lời gọi blocking kết thúc sau khi ghi phần dữ liệu được chấp nhận | `OP_WRITE` báo khả năng thử ghi | Completion của `write` có số byte đã ghi |
| Partial read/write | Vẫn có thể xảy ra | Cần giữ buffer qua nhiều lượt readiness | Cần giữ buffer qua nhiều completion |
| Điều phối | Có thể đơn giản cho ít kết nối | Event loop, interest operations, state theo key | Callback/Future, state theo connection/attachment |
| Điểm dễ nhầm | Một read chưa chắc bằng một frame | Readiness chưa chắc có một frame | Completion chưa chắc hoàn thành cả frame |

AIO không phải lựa chọn tối ưu mặc định. `Selector` có thể thuận tiện nếu ứng dụng đã có event loop rõ ràng; blocking I/O hoặc virtual threads (trên phiên bản Java hỗ trợ) có thể làm code nghiệp vụ dễ đọc hơn trong một số hệ thống. Hãy chọn kiến trúc dựa trên yêu cầu throughput, latency, số lượng kết nối, tính dễ bảo trì và kiểm thử trên nền tảng triển khai thực tế.

## 11. Lỗi thường gặp và cách khắc phục

| Lỗi | Hậu quả | Hướng xử lý |
| --- | --- | --- |
| Nghĩ `accept()` tự động nhận tất cả kết nối tiếp theo | Server chỉ nhận một client rồi dừng accept | Khởi phát accept mới sau completion phù hợp |
| Gọi hai `accept` đang chờ trên cùng server channel | `AcceptPendingException` | Chỉ một accept outstanding mỗi server channel |
| Gọi `read` lần hai khi read cũ đang chờ | `ReadPendingException` | State machine chỉ lập lịch read mới sau completion |
| Gọi `write` chồng lên write cũ | `WritePendingException` | Hàng đợi output và một write đang chờ |
| Nghĩ một completion đọc bằng một TCP message | Ghép/tách sai frame | Framing, parser và buffer state |
| Không kiểm tra `ByteBuffer.hasRemaining()` sau write | Gửi thiếu dữ liệu | Tiếp tục write từ position hiện tại |
| Gọi `Future.get()` trực tiếp trên thread callback chính rồi chờ lâu | Mất khả năng xử lý completion khác | Dùng callback/state machine hoặc executor riêng |
| Dùng chung một buffer cho nhiều operation, sửa trước completion | Dữ liệu sai và race | Rõ ownership, không đụng buffer đang pending |
| Coi timeout của `get` là đã hủy I/O | Operation vẫn có thể chạy | Đóng/hủy/cleanup theo chính sách rõ ràng |
| Callback ném lỗi hoặc bỏ qua `failed` | Rò rỉ kết nối, mất thông tin lỗi | Xử lý mọi nhánh, logging và close |
| Không quản lý group/executor/socket lifecycle | Giữ thread, kết nối hoặc bộ nhớ không cần thiết | `try-with-resources` cho channel, shutdown group đúng cách |

**Ghi nhớ:** `AsynchronousServerSocketChannel` chờ kết nối TCP và tạo `AsynchronousSocketChannel` cho mỗi client; `AsynchronousSocketChannel` thực hiện connect/read/write bất đồng bộ. Nhận kết quả bằng `Future` hoặc `CompletionHandler`, không phát operation cùng loại trùng khi chưa hoàn thành, không giả định một lần I/O xử lý đủ một frame, và luôn quản lý buffer/state cùng vòng đời kết nối một cách rõ ràng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="asynchronous-io-005">AsynchronousChannelGroup và nhóm luồng thực thi</a>

<details>
<summary>Xem chi tiết</summary>

# AsynchronousChannelGroup và nhóm luồng thực thi trong Java NIO.2

**`AsynchronousChannelGroup`** là cơ chế gom các **asynchronous socket channels** trong Java NIO.2 để dùng chung tài nguyên xử lý I/O và một **nhóm luồng thực thi (thread pool)**. Nhóm quản lý những công việc cần thiết để phát hiện, hoàn tất và chuyển kết quả của thao tác bất đồng bộ đến `CompletionHandler`. Nhờ đó, chương trình có thể khởi tạo thao tác I/O rồi tiếp tục làm việc khác, thay vì bắt buộc giữ một thread chờ mỗi kết nối.

Tuy nhiên, ba khái niệm sau **không đồng nghĩa**:

- *Asynchronous I/O*: thao tác được khởi tạo, kết quả đến về sau qua `Future` hoặc `CompletionHandler`.
- *Thread pool*: nơi các tác vụ phục vụ xử lý I/O và callback được thực thi; **không nhất thiết mỗi kết nối có riêng một thread**.
- *Non-blocking Selector*: mô hình `Selector`/`SocketChannel` khác, nơi ứng dụng chủ động đọc/ghi các channel dựa trên sự kiện sẵn sàng.

Hiểu đúng `AsynchronousChannelGroup` giúp quản lý tài nguyên server, tránh làm nghẽn nhóm luồng khi callback chạy công việc nặng, tránh đóng group khi còn request đang hoạt động, và không nhầm nó với `ExecutorService` của `AsynchronousFileChannel`.

## 1. Vai trò chính của AsynchronousChannelGroup

`AsynchronousChannelGroup` thuộc package `java.nio.channels` (Java 7 trở lên). Đây là lớp trừu tượng đại diện cho **một nhóm các asynchronous channels cùng chia sẻ cơ chế xử lý**. Một group có associated thread pool để:

1. Phục vụ xử lý những sự kiện/tác vụ cần thiết cho các I/O bất đồng bộ trên những channel gắn với group.
2. Dispatch kết quả hoàn thành hoặc thất bại tới `CompletionHandler`.
3. Quản lý vòng đời các tài nguyên I/O, channel và thread pool của group.

**Group không phải một kết nối, không truyền byte, không tự định nghĩa giao thức và không phải một thread riêng.** Mỗi `AsynchronousSocketChannel`/`AsynchronousServerSocketChannel` vẫn đại diện cho endpoint cụ thể và có trạng thái riêng; group là hạ tầng phục vụ nhiều endpoint.

### Hình dung quan hệ

```text
                          AsynchronousChannelGroup
                        +--------------------------+
                        | Thread pool / I/O tasks  |
                        | completion dispatch      |
                        +-------------+------------+
                                      |
            +-------------------------+-------------------------+
            |                         |                         |
   AsynchronousServerSocketChannel   AsynchronousSocketChannel  AsynchronousSocketChannel
          accept(...)                 read/write/connect       read/write/connect
            |                         |                         |
            +------------+------------+------------+------------+
                         |
                 Future / CompletionHandler
                         |
              Kết quả, lỗi, xử lý nghiệp vụ
```

Sơ đồ mô tả **quan hệ logic**, không cam kết OS luôn dùng một thread cố định để xử lý một thao tác cụ thể. Implementation có thể sử dụng cơ chế native asynchronous I/O hoặc mô phỏng bằng các tác vụ nền; Java đưa ra hợp đồng API về completion, không hứa một kiểu kernel I/O đồng nhất trên mọi OS.

## 2. Những channel nào thực sự gắn với group?

| Loại channel | Có thể gắn `AsynchronousChannelGroup`? | Cách liên kết |
| --- | --- | --- |
| `AsynchronousSocketChannel` | **Có** | `AsynchronousSocketChannel.open(group)` hoặc `open()` dùng default group. |
| `AsynchronousServerSocketChannel` | **Có** | `AsynchronousServerSocketChannel.open(group)` hoặc `open()` dùng default group. |
| Socket nhận từ server async | **Có, qua group của server** | `server.accept(...)` trả channel đã kết nối, sử dụng hạ tầng group của server. |
| `AsynchronousFileChannel` | **Không dùng trực tiếp AsynchronousChannelGroup làm tham số** | `open(Path, Set<OpenOption>, ExecutorService, ...)` chỉ định executor; hoặc overload dùng default thread pool. |
| `SocketChannel` / `ServerSocketChannel` đồng bộ | **Không** | Dùng blocking I/O hoặc `Selector`; không phải asynchronous channel group. |
| `FileChannel` | **Không** | Thao tác file channel đồng bộ thông thường, không có liên kết group. |

Đây là khác biệt cần ghi nhớ khi thấy cùng tiền tố `Asynchronous*`. `AsynchronousFileChannel` là asynchronous API nhưng **không có** overload `open(group)`; nó liên kết với `ExecutorService`, không có phương thức `group()`. Java có thể dùng cùng cấu hình default thread pool trong implementation, nhưng không biến file channel thành thành viên trực tiếp của một `AsynchronousChannelGroup` cụ thể do bạn tạo.

Ví dụ gắn hai socket channels với một nhóm:

```java
AsynchronousChannelGroup group =
    AsynchronousChannelGroup.withFixedThreadPool(4,
        runnable -> new Thread(runnable, "nio-aio"));

AsynchronousServerSocketChannel server =
    AsynchronousServerSocketChannel.open(group);
AsynchronousSocketChannel client =
    AsynchronousSocketChannel.open(group);
```

Đây là **đoạn minh họa**, chưa có `bind`/`connect` và chưa giải phóng resource; ứng dụng thật phải đóng cả hai channel rồi shutdown group. Không thể dùng `SocketChannel.open(group)` vì đó là loại channel khác.

## 3. Group được tạo thế nào?

Ba factory method chính:

| Phương thức | Ý nghĩa | Lưu ý |
| --- | --- | --- |
| `withFixedThreadPool(int nThreads, ThreadFactory factory)` | Tạo group với số luồng xử lý tối đa `nThreads` cho các nhiệm vụ của group. | `nThreads` phải dương. Thread factory quyết định tên/daemon/priority; đừng đặt quá thấp nếu callback có việc nặng. |
| `withCachedThreadPool(ExecutorService executor, int initialSize)` | Dùng executor có thể tạo thêm thread khi cần; `initialSize` là **gợi ý** cho implementation. | Executor nên tạo thread khi cần, có thể tái sử dụng thread. Giá trị gợi ý không phải cam kết số luồng đang chạy. |
| `withThreadPool(ExecutorService executor)` | Dùng executor do ứng dụng cung cấp cho công việc phục vụ group. | Executor nên hỗ trợ **direct handoff hoặc hàng đợi không giới hạn**; thread gọi `execute` không nên thực thi task trực tiếp. Implementation có thể có ràng buộc bổ sung. |

Cả ba factory có thể ném `IOException` khi provider gặp lỗi tạo group. `withFixedThreadPool` còn ném `IllegalArgumentException` nếu số luồng không hợp lệ.

### Cách dùng fixed group

```java
// Đoạn minh họa: imports phù hợp, gọi trong method throws IOException.
ThreadFactory factory = task -> {
    Thread thread = new Thread(task, "async-io-worker");
    thread.setDaemon(false);
    return thread;
};
AsynchronousChannelGroup group =
        AsynchronousChannelGroup.withFixedThreadPool(4, factory);
```

`ThreadFactory` cho phép gắn tên hữu ích khi theo dõi thread dump. Dùng daemon hay non-daemon phải đi cùng chính sách shutdown rõ ràng; **không dựa vào JVM tự kết thúc để nhả tài nguyên**. Số 4 là số minh họa, không phải công thức chuẩn cho production; cần đo tải và xem callback chiếm thời gian bao lâu.

**Lưu ý sở hữu executor:** Với `withThreadPool` hoặc `withCachedThreadPool`, executor **được dành riêng** cho group. Javadoc nói việc tự `executor.shutdown()` từ nơi khác khi group vẫn hoạt động dẫn đến hành vi **không xác định**; khi group terminate, nó thực hiện orderly shutdown executor. Không nên chia cùng executor với HTTP handler, business scheduler hoặc nhiều group mà không khảo sát hợp đồng. Nếu dùng executor tự tạo, hãy để group quản lý vòng đời của nó.

## 4. Default group và cấu hình trong JVM

Khi không truyền group:

```java
AsynchronousSocketChannel client = AsynchronousSocketChannel.open();
AsynchronousServerSocketChannel server = AsynchronousServerSocketChannel.open();
```

Các channel này gắn với **default asynchronous channel group** cấp JVM. Java tự tạo default group; theo Java SE 21, thread pool của nó tạo thread khi cần. Nếu không cấu hình thread factory cho default group, pooled threads của default group là **daemon threads**.

Hai system properties được Java định nghĩa cho default group:

- `java.nio.channels.DefaultThreadPool.threadFactory`: tên đầy đủ của class `ThreadFactory` cụ thể có thể được nạp và khởi tạo.
- `java.nio.channels.DefaultThreadPool.initialSize`: giá trị nguyên làm tham số `initialSize`, như trong `withCachedThreadPool`; không phải số thread cố định.

Các property này là cấu hình JVM, không phải lệnh đặt số thread cho mọi group do người dùng tạo. Khi phát triển ứng dụng thư viện và chia sẻ default group, khó kiểm soát tác động của callback từ các thành phần khác; khi cần resource isolation cho một subsystem, group riêng có thể hợp lý.

Dùng default group giúp code nhỏ gọn nhưng **không có nghĩa** toàn bộ lifecycle của hệ thống được quản lý giúp: chương trình vẫn phải đóng socket channels và kiểm soát các tác vụ còn chờ. Nếu luồng giữ JVM chạy chỉ là daemon thread, JVM có thể kết thúc khi không còn non-daemon thread; không nên xem sự tồn tại của callback chờ xử lý là một cơ chế giữ process sống.

## 5. CompletionHandler được chạy trên thread nào?

Với channel gắn với `AsynchronousChannelGroup`, Javadoc bảo đảm **completion handler được gọi bởi một trong các pooled threads của group**. Điều này cung cấp danh tính thực thi có thể dự đoán ở mức group, không phải cam kết callback luôn chạy trên “thread A” hay theo thứ tự các request.

Đối với một operation hoàn thành ngay, nếu thread đã khởi tạo operation **đang chính là pooled thread của group**, implementation có thể gọi handler **trực tiếp trên initiating thread đó**, thay vì đi qua hàng đợi. Có thể có giới hạn số lần callback lồng nhau để tránh tràn stack.

Ví dụ:

```java
socket.read(buffer, requestId,
    new CompletionHandler<Integer, String>() {
        @Override
        public void completed(Integer n, String id) {
            // Callback xử lý kết quả; có thể chạy trên thread của group.
        }

        @Override
        public void failed(Throwable error, String id) {
            // Callback được gọi khi operation hoàn tất do lỗi.
        }
    });
```

Đoạn này là **minh họa API**, giả định `socket` đã kết nối, `buffer` hợp lệ và `requestId` được khai báo. `Integer n` là số byte đọc được (`-1` có thể biểu thị EOF), không phải số message ứng dụng. `attachment` mang theo ngữ cảnh do caller cung cấp; nó không được JDK tự sinh ra.

### Thread thực thi callback và thread thực thi business logic

Nên coi callback chạy trên resource pool **cần được trả lại nhanh**:

- **Phù hợp:** kiểm tra kết quả, cập nhật trạng thái buffer, hoàn tất `CompletableFuture`, đặt một công việc vào queue khác, đăng ký lần đọc kế tiếp khi cần.
- **Không phù hợp khi mất nhiều thời gian:** chạy truy vấn DB blocking, gọi HTTP blocking, parse payload khổng lồ, sleep, chờ người dùng, `Future.get()` của công việc cũng cần chính pool đó.
- **Nguy cơ:** một pool chỉ có 2 threads, cả hai callback chặn trong lúc chờ task khác của group → tiến độ xử lý I/O/callback khác có thể bị trì hoãn nặng hoặc deadlock do starvation.

Một kiến trúc tốt tách **I/O completion dispatch** và **business executor**:

```text
AsynchronousChannelGroup threads
     |
     | đọc/ghi hoàn tất
     v
CompletionHandler (rất ngắn)
     |
     | kiểm tra + enqueue
     v
Business Executor / bounded queue
     |
     | validate, parse, truy vấn DB, business rule
     v
Kết quả trả về → khởi tạo async write
```

Tuy vậy, business executor cũng cần giới hạn queue và backpressure. Không chuyển việc nặng khỏi group bằng cách tạo số task vô hạn gây hết heap. Khi tính toán network protocol, phải giữ thứ tự message/response theo quy tắc ứng dụng, không mặc định thread pool thực thi theo thứ tự submit.

## 6. Future và CompletionHandler: hai cách nhận kết quả

Cùng là asynchronous socket I/O nhưng kiểu lấy kết quả ảnh hưởng khả năng mở rộng:

| Cách dùng | Mô hình | Điểm cần chú ý |
| --- | --- | --- |
| `Future<V>` | Gọi `connect` / `accept` / `read` / `write` nhận Future; caller có thể làm việc khác rồi `get` để lấy kết quả. | `Future.get()` **chặn thread gọi** nếu chưa xong. Không dùng trong callback của chính pool nếu phụ thuộc task khác của pool. |
| `CompletionHandler<V,A>` | Truyền handler/attachment; JDK gọi `completed` hoặc `failed` khi thao tác hoàn tất. | Không block chờ kết quả trong callback; cần quản lý state, lỗi, buffer, concurrency và lifecycle. |

`Future` **không đồng nghĩa non-blocking từ đầu đến cuối** nếu bạn gọi `get()` ngay lập tức. Đó vẫn là API khởi tạo bất đồng bộ, nhưng luồng gọi `get` sẽ chờ. Trong demo ngắn, `get(timeout, unit)` dễ giúp kiểm chứng kết quả; trong server có nhiều connection, completion callback hoặc event-driven abstraction thường phù hợp hơn.

Một thao tác có thể hoàn tất lỗi. Với `Future`, exception từ I/O thường được bọc trong `ExecutionException` khi `get`. Với callback, `failed(Throwable, attachment)` là nhánh xử lý lỗi. **Không nuốt cả hai** rồi giả định dữ liệu đã truyền đủ.

## 7. Ví dụ Java 17+ chạy được: group, Future và callback

Ví dụ mở TCP loopback server và client **cùng gắn vào một group có 2 threads**. Client ghi `PING` bằng asynchronous write/Future; server đọc đến khi đủ 4 byte qua `CompletionHandler`. Program chờ hoàn thành có timeout để không treo vô hạn trong demo.

Lưu thành file `AsyncGroupDemo.java`:

```java
import java.io.EOFException;
import java.io.IOException;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public class AsyncGroupDemo {
    public static void main(String[] args) throws Exception {
        AsynchronousChannelGroup group =
                AsynchronousChannelGroup.withFixedThreadPool(2,
                    task -> new Thread(task, "aio-worker"));
        try {
            InetAddress loopback = InetAddress.getLoopbackAddress();
            try (AsynchronousServerSocketChannel server =
                         AsynchronousServerSocketChannel.open(group)
                             .bind(new InetSocketAddress(loopback, 0));
                 AsynchronousSocketChannel client =
                         AsynchronousSocketChannel.open(group)) {
                Future<AsynchronousSocketChannel> accepted = server.accept();
                client.connect(server.getLocalAddress()).get(5, TimeUnit.SECONDS);
                try (AsynchronousSocketChannel peer =
                             accepted.get(5, TimeUnit.SECONDS)) {
                    ByteBuffer outbound = ByteBuffer.wrap(
                            "PING".getBytes(StandardCharsets.US_ASCII));
                    int totalSent = 0;
                    while (outbound.hasRemaining()) {
                        int n = client.write(outbound).get(5, TimeUnit.SECONDS);
                        if (n <= 0) throw new IOException("Write made no progress");
                        totalSent += n;
                    }
                    ByteBuffer inbound = ByteBuffer.allocate(4);
                    CompletableFuture<Integer> done = new CompletableFuture<>();
                    CompletionHandler<Integer, Void> handler = new CompletionHandler<>() {
                        @Override public void completed(Integer n, Void attachment) {
                            if (n < 0) {
                                done.completeExceptionally(
                                        new EOFException("Truncated message"));
                            } else if (inbound.hasRemaining()) {
                                peer.read(inbound, null, this);
                            } else {
                                done.complete(inbound.position());
                            }
                        }
                        @Override public void failed(Throwable error, Void attachment) {
                            done.completeExceptionally(error);
                        }
                    };
                    peer.read(inbound, null, handler);
                    int totalRead = done.get(5, TimeUnit.SECONDS);
                    inbound.flip();
                    System.out.println("sent=" + totalSent);
                    System.out.println("received=" + totalRead);
                    System.out.println("body=" +
                            StandardCharsets.US_ASCII.decode(inbound));
                }
            }
        } finally {
            group.shutdown();
            if (!group.awaitTermination(5, TimeUnit.SECONDS)) {
                group.shutdownNow();
                group.awaitTermination(5, TimeUnit.SECONDS);
            }
        }
    }
}
```

Lệnh biên dịch và chạy:

```bash
javac --release 17 AsyncGroupDemo.java
java AsyncGroupDemo
```

Kết quả đã kiểm chứng trong môi trường cho phép TCP loopback:

```text
sent=4
received=4
body=PING
```

**Giải thích đầy đủ:**

1. `withFixedThreadPool(2, ...)` tạo group gắn hạ tầng hoàn tất I/O với hai pooled threads; `ThreadFactory` đặt tên. **Tên thread không được in** vì thứ tự chạy callback không phải hợp đồng bảo đảm.
2. `AsynchronousServerSocketChannel.open(group)` và `AsynchronousSocketChannel.open(group)` dùng cùng group. `bind(..., 0)` yêu cầu OS chọn một port tự do.
3. `server.accept()` trả `Future<AsynchronousSocketChannel>`. Client `connect(...).get(5, ...)` chờ kết nối được thiết lập, rồi Future accept trả channel phía server. `get` chặn **main thread**, không biến I/O thành blocking API của channel.
4. Client viết buffer `PING`. Vòng `write` xử lý khả năng ghi **ít byte** mỗi lần; sau khi Future hoàn tất, mới được truy cập/ghi tiếp buffer.
5. Server `read` đăng ký callback. Khi hoàn tất một lần đọc, handler kiểm tra EOF rồi **khởi tạo lần đọc tiếp theo** nếu buffer chưa đủ 4 byte. Vì TCP là stream, không giả định một lần read tương ứng một message.
6. `CompletableFuture done` ở đây chỉ là cầu nối để **main thread** đợi cả khối demo hoàn tất; bản thân completion handler không gọi `get`. Sau khi `done.get` trả về, buffer đã được viết xong; `flip` chuẩn bị để decode.
7. `try-with-resources` đóng server/client/accepted channel. Cuối cùng `group.shutdown()` và `awaitTermination` đợi group kết thúc, dùng `shutdownNow` khi vượt thời hạn mẫu.

Code đơn giản hóa nội dung mạng thành 4 ASCII byte và một kết nối. Dịch vụ thật cần framing, giới hạn kích thước, queue ghi, backpressure, timeout toàn request, xử lý lỗi callback và shutdown phối hợp. Nếu môi trường hạn chế tạo socket, chương trình có thể ném exception; không phải bằng chứng API group không hoạt động.

### Hạn chế của ví dụ cần hiểu

- Một `AsynchronousSocketChannel` cho phép nhiều thread sử dụng, nhưng **tối đa một read đang chờ và một write đang chờ** trên cùng channel. Khởi tạo read thứ hai trước khi read cũ hoàn tất sẽ ném `ReadPendingException`; tương tự `WritePendingException` cho write. Code đặt lần read kế tiếp sau callback nên tránh overlap.
- `read` có thể trả `-1` nếu peer đóng chiều ghi; handler coi đó là dữ liệu dở nếu chưa đủ 4 bytes. Ở production phải có quy tắc parser riêng cho EOF hợp lệ.
- `CompletableFuture` làm tầng phối hợp trong ví dụ, không thay group; hai loại executor/thread vẫn có trách nhiệm khác nhau.
- `awaitTermination(5,...)` là thời gian demo. Hệ thống thật cần grace period, cancellation policy và quan sát request còn đang hoạt động.

## 8. Ràng buộc về số operation đang chờ và buffer

Async không cho phép sử dụng bừa các buffer ở nhiều thread.

### Một read và một write pending trên một async socket

`AsynchronousSocketChannel` có thể đồng thời có **một read và một write** đang chờ. Nhưng hai lần read đang chờ đồng thời hoặc hai lần write đang chờ đồng thời trên cùng socket sẽ bị từ chối qua exception tương ứng. Điều này đòi hỏi per-connection state:

```text
READ_IDLE --start read--> READ_PENDING --completed/failed--> READ_IDLE
WRITE_IDLE --start write--> WRITE_PENDING --completed/failed--> WRITE_IDLE
```

Nếu có nhiều message cần gửi, xếp chúng trong outbound queue, chỉ tạo lần write tiếp theo sau khi operation trước hoàn tất; dữ liệu gửi dở phải tiếp tục từ `ByteBuffer.position` hiện tại. Khi có hàng chờ lớn, dùng backpressure hoặc giới hạn queue, không tạo vô hạn Future rồi hy vọng group tự giữ thứ tự.

### Buffer không nên được sửa khi operation còn pending

Ví dụ **sai**:

```java
ByteBuffer buffer = ByteBuffer.wrap(payload);
socket.write(buffer); // async write chưa chắc đã hoàn tất
buffer.clear();        // SAI: làm đổi trạng thái buffer khi I/O còn pending
```

Đây chỉ là snippet minh họa, `payload` và `socket` giả định đã có. Theo hợp đồng, sau khi khởi tạo operation bất đồng bộ, không truy cập/biến đổi buffer liên quan cho tới khi operation hoàn tất. Nếu socket API timeout và implementation không bảo đảm đã xử lý bao nhiêu byte, trạng thái buffer sau timeout có thể **không được xác định**; phải áp dụng chính sách hủy/đóng và không tùy tiện retry trên buffer đó.

Đối với protocol có frame dài, giữ buffer đúng ownership qua các lần callback; không đưa cùng buffer cho hai pending operations. **Group thread pool không tự làm ByteBuffer thread-safe.**

## 9. AsynchronousFileChannel sử dụng ExecutorService, không dùng group trực tiếp

`AsynchronousFileChannel` cũng hỗ trợ `Future` và `CompletionHandler`, nhưng không có **current file position**; mỗi `read/write` nhận offset tường minh. Nó cho phép **nhiều read/write operations outstanding** trên cùng file channel, nhưng thứ tự thao tác hoặc callback **không được bảo đảm** theo thứ tự khởi tạo. Buffer dùng cho I/O pending không được chia sẻ bất cẩn.

Factory đặc trưng:

```java
AsynchronousFileChannel.open(
    path,
    Set.of(StandardOpenOption.READ, StandardOpenOption.WRITE),
    executor);
```

Đoạn trên chỉ minh họa; `path` và `executor` đã có. `executor` là một `ExecutorService` phục vụ thao tác/dispatch của file channel này. **Không truyền `AsynchronousChannelGroup` vào overload** vì Java không định nghĩa signature đó. Overload `AsynchronousFileChannel.open(path, options...)` dùng thread pool mặc định theo môi trường.

### Ví dụ Java 17+: file async với executor riêng

Lưu thành `AsyncFilePoolDemo.java`:

```java
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class AsyncFilePoolDemo {
    public static void main(String[] args) throws Exception {
        Path file = Files.createTempFile("async-file-", ".dat");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            try (AsynchronousFileChannel channel = AsynchronousFileChannel.open(
                    file, Set.of(StandardOpenOption.READ, StandardOpenOption.WRITE),
                    executor)) {
                ByteBuffer payload = ByteBuffer.wrap(
                        "DATA".getBytes(StandardCharsets.US_ASCII));
                int written = channel.write(payload, 0).get(5, TimeUnit.SECONDS);
                ByteBuffer target = ByteBuffer.allocate(4);
                int read = channel.read(target, 0).get(5, TimeUnit.SECONDS);
                target.flip();
                System.out.println("file-written=" + written);
                System.out.println("file-read=" + read);
                System.out.println("file-data=" +
                        StandardCharsets.US_ASCII.decode(target));
            }
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
            Files.deleteIfExists(file);
        }
    }
}
```

```bash
javac --release 17 AsyncFilePoolDemo.java
java AsyncFilePoolDemo
```

Kết quả đã kiểm chứng trên local filesystem:

```text
file-written=4
file-read=4
file-data=DATA
```

Ở đây sử dụng `ExecutorService` trực tiếp chứ **không tạo `AsynchronousChannelGroup`**. Cả hai Future được `get` xác nhận hoàn tất trước khi dùng lại buffer hoặc đọc kết quả. Nội dung file nhỏ 4 byte khiến output ổn định trong môi trường thử nghiệm, nhưng **API vẫn cho phép read/write ngắn**; khi triển khai với file lớn, cần lặp theo số byte thực xử lý và duy trì offset chính xác.

Khác với việc group **sở hữu executor** được truyền vào `withThreadPool`, executor ở ví dụ file được ứng dụng tạo và ứng dụng chủ động shutdown **sau khi channel được đóng**. Không trộn lẫn lifecycle giữa các API. Với file lớn, điều phối nhiều operation cùng offset có thể làm hỏng record logic nếu không có giao thức tránh overlap, dù mỗi lời gọi riêng hợp lệ.

## 10. Vòng đời AsynchronousChannelGroup: shutdown, shutdownNow, termination

`AsynchronousChannelGroup` **không triển khai AutoCloseable**; không dùng `try (AsynchronousChannelGroup group = ...)` như với socket channel. Quản lý group qua bốn trạng thái/phương thức:

| API | Ý nghĩa chính | Không nên suy diễn |
| --- | --- | --- |
| `isShutdown()` | Group đã được đánh dấu shutdown chưa. | Không có nghĩa tất cả channel đã đóng. |
| `shutdown()` | Khởi tạo **orderly shutdown**: ngăn tạo channel mới gắn vào group. | **Không** tự đóng các channel đang mở, không tự hủy callback đang chạy. |
| `shutdownNow()` | Khởi tạo shutdown và gọi đóng tất cả channel đang mở trong group. | Không cố ngắt/interrrupt các callback đang thực thi; tác vụ có thể thất bại do channel bị đóng. |
| `awaitTermination(timeout, unit)` | Chờ group hoàn tất hoặc hết thời hạn. | Không phải phương thức bắt đầu shutdown; thời gian chờ có thể hết mà group chưa terminate. |
| `isTerminated()` | Xác nhận group đã terminate. | Không phải tín hiệu “mọi request đã commit” nếu ứng dụng không tự quản lý state. |

Theo Javadoc, khi `shutdown` đã được gọi:

- Tạo thêm channel gắn vào group sẽ ném `ShutdownChannelGroupException`.
- **Group chỉ terminate khi** toàn bộ asynchronous channels thuộc group đã đóng, các completion handlers đang thực thi đã chạy xong, và tài nguyên group được giải phóng.
- `shutdown` **không** cố ngừng hoặc interrupt thread đang chạy callback.
- `shutdownNow` sẽ đóng các channel đang mở, nhưng **cũng không** ngắt callback đang thực thi; vì thế không lấy đây làm một hard kill/timeout bảo đảm.

### Trình tự shutdown an toàn

```text
Đánh dấu server không nhận request mới
       |
Dừng đăng ký thao tác mới / đóng server accept endpoint
       |
Chờ hoặc hủy theo chính sách các request đang hoạt động
       |
Đóng các client sockets / server socket thuộc group
       |
group.shutdown()
       |
group.awaitTermination(gracefulTimeout, unit)
       |
Nếu chưa dừng: group.shutdownNow(); theo dõi kết quả/timeout
       |
Chỉ báo kết thúc khi state nghiệp vụ và resources đã được xử lý
```

`shutdownNow` có thể ném `IOException` nếu đóng channel gặp lỗi. `awaitTermination` có thể ném `InterruptedException`; phải giữ đúng chính sách interruption, không bỏ qua rồi tuyên bố đã terminate. Những callback được gọi khi channel đang đóng có thể nhận failure; cần bảo đảm completion signal và cleanup được thực hiện cả ở nhánh lỗi.

**Deadlock shutdown thường gặp:** callback đợi một `CountDownLatch` sẽ chỉ được giải phóng bởi task khác cũng cần group thread, hoặc thread điều phối đóng group rồi chờ một callback chưa bao giờ được phép chạy. Hạn chế việc chờ blocking trên group threads và có cơ chế cancellation/outstanding-work tracking từ thiết kế.

### Group ownership

Một hệ thống có nhiều module có thể cùng dùng một group để giảm overhead, nhưng nếu module A gọi `shutdownNow` trong khi module B vẫn giữ socket thuộc group, module B cũng bị ảnh hưởng. Chỉ **chủ sở hữu group** được phép shutdown; thư viện nhận group từ caller chỉ nên đóng các channel do chính nó sở hữu, trừ khi giao kèo API nói khác.

Nếu muốn cô lập fault/latency giữa các subsystems, dùng group riêng cho từng loại I/O hoặc hạn chế việc nặng trong callbacks. Nhưng tạo group mới cho **mỗi kết nối** sẽ gây thêm thread/tài nguyên và làm mất lợi ích chia sẻ — thường không phù hợp.

## 11. Đặt kích thước pool sao cho phù hợp

**Không có công thức Java API bảo đảm số thread tối ưu.** Khi chọn fixed vs cached, cần phân tích:

- Số concurrent connections và số operations pending; chúng **không** tương đương số threads cần giữ.
- Cách JDK/provider xử lý I/O events và dispatch, số lượng callback hoàn thành mỗi giây.
- Callback chạy trong bao lâu và có blocking không.
- Có business thread pool riêng không? Queue có bounded/backpressure không?
- Network throughput, độ trễ yêu cầu, số CPU core, GC, socket memory và áp lực tài nguyên khác.
- OS, JDK version/provider, số lượng file descriptors và behavior under load.

### Fixed thread pool

Ưu điểm: dễ đặt giới hạn số worker phục vụ group, dễ đo saturation, tạo rõ thread factory. Nhược điểm: nếu callbacks lâu hoặc blocking, toàn nhóm có thể thiếu worker. Tăng `nThreads` không sửa logic deadlock hoặc thiếu backpressure; tăng vô hạn còn tạo overhead.

### Cached thread pool

Ưu điểm: có thể tạo thêm threads khi cần, phù hợp với profile workload/provider nhất định. Nhược điểm: cần giới hạn tải ở tầng ứng dụng vì tăng threads/queue có thể tiêu thụ nhiều memory và làm latency biến động. `initialSize` là hint, **không** phải cam kết tạo đúng số thread ban đầu.

### Custom ExecutorService

Ưu điểm: kiểm soát ThreadFactory, instrumentation, policy cấu hình. Nhược điểm: phải tuân thủ các ràng buộc `withThreadPool`. Javadoc yêu cầu hỗ trợ **direct handoff hoặc hàng đợi không giới hạn**, và thread gọi `execute` không chạy task trực tiếp. Vì vậy **không** tự ý dùng executor với bounded queue + `CallerRunsPolicy` cho `withThreadPool`; khi quá tải, `CallerRunsPolicy` có thể khiến thread dispatch/initiating call bị kéo vào chạy công việc một cách không phù hợp với hợp đồng. Dùng bounded business queue riêng cho backpressure, không phá giả định dispatch của group.

**Quan sát hữu ích:** số socket đang mở, active/pending callbacks, thời gian callback, độ dài outbound queues, số timeout/exception, thời gian graceful shutdown, thread dumps và tỷ lệ CPU. Đo throughput và tail latency trên đúng JDK/OS triển khai.

## 12. Phân biệt AsynchronousChannelGroup và Selector / CompletableFuture / virtual threads

| Công cụ | Trách nhiệm | Điểm khác |
| --- | --- | --- |
| `AsynchronousChannelGroup` | Resource sharing, I/O completion processing, completion dispatch cho các async socket channels. | Không phải API tự đọc/ghi; nhóm không đại diện một kết nối. |
| `AsynchronousSocketChannel` | Khởi tạo connect/read/write async, nhận `Future` hoặc `CompletionHandler`. | Socket có thể thuộc group do người dùng tạo hoặc default group. |
| `SocketChannel` + `Selector` | Readiness-based non-blocking I/O do event loop chủ động điều phối. | Thao tác đọc/ghi vẫn được **ứng dụng chủ động gọi** sau readiness. |
| `ExecutorService` | Chạy task nói chung; có thể làm resource pool của group hoặc phục vụ file async. | Executor một mình không tạo semantics I/O bất đồng bộ cho network socket. |
| `CompletableFuture` | Compose kết quả bất đồng bộ của tác vụ. | Không tự tạo group; có thể nhận kết quả từ callback. |
| Virtual threads (Java 21+) | Cho phép viết luồng điều khiển kiểu blocking với chi phí thread nhỏ hơn trong nhiều tác vụ I/O. | Đây là mô hình lập trình khác; không đồng nghĩa AsynchronousChannelGroup. |

**Không chọn group chỉ vì muốn “nhiều kết nối”.** Cân nhắc ba kiến trúc phổ biến:

1. Blocking network I/O với nền tảng hiện có, hoặc virtual threads khi dùng JDK hỗ trợ và bài toán phù hợp.
2. Non-blocking `SocketChannel` + `Selector` nếu cần kiểm soát event loop và readiness.
3. `AsynchronousSocketChannel` + `AsynchronousChannelGroup` khi muốn khởi tạo I/O và nhận completion qua callbacks/Future.

Mọi lựa chọn vẫn phải giải quyết framing TCP, short read/write, timeout, TLS, authentication, error handling và backpressure ở tầng thích hợp. Group không xóa trách nhiệm này.

## 13. Những lỗi thường gặp và cách sửa

| Sai lầm | Hậu quả | Thiết kế đúng |
| --- | --- | --- |
| Tạo `AsynchronousChannelGroup` cho mỗi connection. | Tốn thêm thread và tài nguyên; khó shutdown. | Một group dùng chung cho subsystem có ownership rõ ràng. |
| Cho callback gọi `Thread.sleep` hoặc truy vấn DB chậm. | Pool xử lý completion bị nghẽn/starvation, latency tăng. | Callback ngắn, chuyển việc nặng sang executor khác có bounded queue. |
| Trong callback gọi `Future.get()` để chờ tác vụ cũng cần group. | Có thể deadlock hoặc pool starvation. | Compose async, callback tiếp nối hoặc chuyển việc chờ ra nơi khác. |
| Chạy hai read đồng thời trên cùng socket. | `ReadPendingException`. | Mỗi kết nối quản lý trạng thái: tối đa một read đang chờ. |
| Chạy hai write đồng thời trên cùng socket. | `WritePendingException`. | Hàng đợi ghi; write tiếp sau khi lần trước hoàn tất. |
| Thay đổi `ByteBuffer` khi operation chưa xong. | Race dữ liệu/position, kết quả không đáng tin. | Quy tắc buffer ownership; chỉ truy cập sau completion. |
| Nghĩ `read` nhận đủ một frame TCP. | Parser hỏng khi dữ liệu đến theo nhiều chunk. | Tích lũy bytes và framing protocol. |
| Gọi `group.shutdown()` nhưng để socket mở. | `awaitTermination` có thể đợi mãi hoặc timeout. | Đóng mọi channel thuộc group và chờ callbacks xong. |
| `shutdownNow` rồi cho rằng callback chắc chắn đã bị interrupt. | Callback tiếp tục chạy, có thể vẫn dùng tài nguyên nghiệp vụ. | Thiết kế cancellation/cooperative stop, đóng channel và await. |
| Tự gọi `executor.shutdown()` trên executor đang được group sử dụng. | Hành vi không xác định theo Javadoc. | Để group sở hữu và shutdown executor của nó. |
| Dùng `AsynchronousFileChannel.open(path, group)`. | Không có overload đó; lỗi biên dịch. | Dùng `open(path, options, ExecutorService)` cho async file. |
| Đọc một callback xảy ra trên thread nào đó rồi khẳng định mọi callback luôn ở thread ấy. | Phụ thuộc lịch thực thi, dễ sai về concurrency. | Chỉ dựa trên bảo đảm pooled threads của group, không dựa trên thread ID cụ thể. |
| `get(timeout)` hết hạn rồi vẫn coi buffer an toàn để tái sử dụng ngay. | Operation có thể còn chạy; buffer đang được sử dụng. | Xác nhận completion/cancel semantics, đóng channel khi cần, xử lý timeout từng API. |

### Exception đáng nhận biết

- `ShutdownChannelGroupException`: cố tạo channel mới ràng buộc group đã shutdown.
- `ReadPendingException` / `WritePendingException`: operation cùng hướng trước chưa hoàn tất trên asynchronous socket.
- `AcceptPendingException`: cố khởi tạo accept mới trong lúc accept trước vẫn pending trên cùng server channel.
- `ExecutionException`: `Future.get` bọc nguyên nhân I/O thất bại.
- `TimeoutException`: luồng đang đợi Future chưa nhận kết quả trong thời gian yêu cầu; **không đồng nghĩa underlying I/O đã bị hủy**.
- `AsynchronousCloseException` / `ClosedChannelException`: channel bị đóng hoặc không còn hợp lệ cho thao tác.
- `InterruptedByTimeoutException`: quá thời gian trong overload async socket read/write có timeout; Javadoc cảnh báo trạng thái buffer/connection có thể không xác định trong một số trường hợp.

Đừng chuyển tất cả exception thành “thử lại ngay”. Khi mạng bị gián đoạn, chưa chắc peer application đã xử lý bao nhiêu bytes. Với protocol có tác động nghiệp vụ, cần framing và idempotency/acknowledgement riêng.

## 14. Checklist triển khai và tự kiểm tra

### Checklist

1. **Làm rõ loại channel:** async socket hay async file? Chỉ socket channels gắn trực tiếp vào `AsynchronousChannelGroup`.
2. **Chọn group:** default group khi đơn giản; explicit group khi cần giới hạn/cô lập và quản lý lifecycle.
3. **Chọn factory:** fixed, cached hay custom executor theo workload và ràng buộc Javadoc.
4. **Sở hữu pool:** với group tự tạo, group chịu trách nhiệm shutdown pooled executor; không tự shutdown từ module khác.
5. **Giữ callback ngắn:** không chờ khóa/DB/network blocking trong pooled threads nếu có thể tránh.
6. **Buffer ownership:** không tái sử dụng/modify buffer khi I/O pending; quản lý per-connection state.
7. **Giới hạn pending operations:** mỗi async socket chỉ một read và một write đồng thời; quản lý write queue.
8. **Protocol:** TCP stream không có message boundaries; xử lý partial read/write, EOF, kích thước frame.
9. **Timeout/cancel:** đặt deadline đúng cấp request; `Future.get` timeout không tự hủy I/O.
10. **Shutdown đúng thứ tự:** ngừng nhận công việc, hoàn tất/hủy, đóng channels, group.shutdown, awaitTermination, fallback phù hợp.
11. **Load test:** theo dõi callback latency, pending tasks, memory, active sockets, error rate và tail latency.
12. **Platform test:** xác minh behavior trên đúng OS/JDK/network provider, không dự đoán implementation từ tên NIO.

### Câu hỏi tự kiểm tra

**1. `AsynchronousChannelGroup` có được dùng để truyền bytes giữa hai socket không?**

Không. Group chia sẻ hạ tầng xử lý completion; chính `AsynchronousSocketChannel` mới thực hiện `read/write`.

**2. Callback của một channel được gắn group có chạy ngẫu nhiên trên thread không thuộc group không?**

Theo hợp đồng, callback được gọi bởi một pooled thread của group. Nếu operation hoàn tất ngay và caller đang ở pooled thread, có thể được gọi trực tiếp trên chính initiating pooled thread đó.

**3. `group.shutdown()` đã đóng tất cả socket chưa?**

Chưa. Orderly shutdown đánh dấu group shutdown và ngăn tạo channel mới; group chỉ terminate khi các channel trong nhóm đã đóng, callbacks đang thực thi hoàn tất và tài nguyên được giải phóng.

**4. Có thể gọi `group.shutdownNow()` để chắc chắn hủy tức thì mọi callback không?**

Không. Nó đóng các channel mở, nhưng không cố interrupt những thread đang chạy completion handler.

**5. Nếu `Future.get(5, SECONDS)` ném `TimeoutException`, thao tác I/O đã tự hủy chưa?**

Không thể suy ra. Timeout của `Future.get` chỉ là timeout chờ kết quả. Phải dùng cơ chế cancel/close/timeout riêng với semantics của channel.

**6. Vì sao không nên gọi `Future.get()` lâu ngay bên trong `CompletionHandler` của pool cố định?**

Vì callback chiếm một pooled thread. Nếu tất cả pooled threads đều chờ task/callback khác cũng cần cùng pool, ứng dụng có thể starvation hoặc deadlock.

**7. `AsynchronousFileChannel` có thể tạo bằng `AsynchronousChannelGroup.withFixedThreadPool` rồi `open(path, group)` không?**

Không. API file async nhận `ExecutorService` trong overload chuyên biệt, không nhận group.

**8. Tại sao example phải lặp đọc và ghi dù đã dùng API asynchronous?**

Vì một operation chỉ hứa trả số bytes thực đọc/ghi, không hứa hoàn tất trọn message TCP. “Asynchronous” mô tả cách nhận completion, **không** làm stream trở thành message-based.

## Nguồn đọc chính thức

- Oracle Java SE 21 — [`AsynchronousChannelGroup`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/AsynchronousChannelGroup.html): group, thread pool, default group, callback dispatch, shutdown và termination.
- Oracle Java SE 21 — [`AsynchronousSocketChannel`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/AsynchronousSocketChannel.html): Future/CompletionHandler, giới hạn một read và một write pending, timeout.
- Oracle Java SE 21 — [`AsynchronousServerSocketChannel`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/AsynchronousServerSocketChannel.html): accept kết nối, accept pending và group.
- Oracle Java SE 21 — [`AsynchronousFileChannel`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/AsynchronousFileChannel.html): async file I/O với ExecutorService và offset riêng.
- Oracle Java SE 21 — [`CompletionHandler`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/CompletionHandler.html): completion callback, attachment và error branch.

**Tóm lại:** `AsynchronousChannelGroup` là **tầng chia sẻ tài nguyên và thread pool** cho asynchronous socket channels, không phải một protocol handler hay event loop tự động xử lý nghiệp vụ. Thành công của thiết kế phụ thuộc ownership rõ ràng, callback ngắn, quản lý buffer/pending operations, cấp phát số threads có đo đạc và shutdown cả channels lẫn group đúng vòng đời.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="asynchronous-io-006">Không sửa hoặc tái sử dụng buffer khi thao tác chưa hoàn tất</a>

<details>
<summary>Xem chi tiết</summary>

# Không sửa hoặc tái sử dụng buffer khi thao tác chưa hoàn tất trong Java NIO bất đồng bộ

## 1. Nguyên tắc quan trọng nhất

Với I/O đồng bộ, lệnh như `channel.write(buffer)` thường trả về sau khi **lượt ghi đó** kết thúc, nên chương trình có thể xem số byte đã ghi và tiếp tục điều chỉnh buffer. Với I/O bất đồng bộ (asynchronous I/O), các API như `AsynchronousFileChannel.write(...)` hoặc `AsynchronousSocketChannel.read(...)` **khởi tạo** một thao tác rồi trả về `Future` hoặc nhận `CompletionHandler`. Việc sử dụng buffer có thể tiếp diễn **sau khi lời gọi khởi tạo đã trả về**.

Vì vậy phải có quy tắc sở hữu:

> **Từ lúc bắt đầu một thao tác đọc/ghi bất đồng bộ đến khi chính thao tác đó hoàn tất, buffer được truyền vào phải được dành riêng cho thao tác: không đọc, sửa, thay đổi position/limit hay tái sử dụng nó và không để một thao tác I/O bất đồng bộ khác truy cập cùng bộ nhớ backing.**

Quy tắc này áp dụng cho cả:

- **Ghi (`write`):** byte nguồn phải được giữ ổn định; một thread không được `put`, `clear`, `flip` hoặc thay `position/limit` trên buffer nguồn trong lúc thao tác ghi còn pending.
- **Đọc (`read`):** vùng đích đang được hệ thống cập nhật; không đọc dữ liệu trước khi hoàn tất, không viết vào cùng vùng, không `flip` hoặc dùng lại buffer đích khi thao tác đọc còn pending.
- **Heap hay direct buffer:** lựa chọn `ByteBuffer.allocate` / `allocateDirect` không thay đổi quy tắc đồng thời.
- **Buffer view:** `duplicate()`, `slice()` và `asReadOnlyBuffer()` vẫn có thể chia sẻ **cùng backing storage**; cursor độc lập **không** làm vùng byte trở nên độc lập.

Đây không phải chỉ là quy tắc “code đẹp”. Tài liệu Java SE nêu rõ ByteBuffer không an toàn cho truy cập đồng thời trong khi I/O bất đồng bộ còn đang thực hiện. Nếu vi phạm, có thể gửi sai payload, nhận dữ liệu bị ghi đè, tính sai số byte, phát sinh exception, hoặc có lỗi rất khó tái hiện.

## 2. Buffer được ứng dụng giao cho một thao tác I/O

Một `ByteBuffer` có hai thành phần cần quan tâm:

1. **Trạng thái điều khiển:** `position`, `limit`, `capacity`, `mark`, và một số thuộc tính khác.
2. **Vùng dữ liệu:** byte trên heap hoặc off-heap mà buffer nhìn thấy; có thể dùng chung giữa nhiều view.

Ví dụ:

```java
ByteBuffer source = ByteBuffer.wrap(new byte[]{10, 20, 30, 40});
source.position(1);
source.limit(3); // remaining = 2 byte: 20 và 30
```

Đây là đoạn minh họa có `import java.nio.ByteBuffer;`.

Giả sử `write(source)` được khởi tạo tại thời điểm này. Thao tác I/O có thể lấy byte từ vùng `[position, limit)` theo contract của channel; khi hoàn tất, `position` được cập nhật tương ứng số byte đã truyền. Nếu một thread khác tự thay `source.position(0)`, `source.limit(4)` hoặc `source.put(1,(byte)99)` khi thao tác chưa kết thúc, chương trình phá vỡ giả định về **dữ liệu nguồn và vùng còn lại** mà thao tác I/O dùng. Không có cam kết là channel đã copy toàn bộ byte tại đúng thời điểm gọi.

### 2.1. “Lời gọi đã trả về” khác “thao tác đã hoàn tất”

```text
Thread ứng dụng                         Hệ thống I/O / thread hoàn tất
----------------                       ----------------------------
tạo ByteBuffer B
khởi tạo async write(B)  ────────────► thao tác đang pending
nhận Future F
  │                                         │
  ├── KHÔNG sửa hoặc tái sử dụng B           ├── đọc bytes của B
  │                                         └── báo hoàn tất
  ▼
F.get() hoàn tất thành công ◄───────── result: số byte đã ghi
  │
  └── có thể kiểm tra remaining, tiếp tục ghi, hoặc tái sử dụng B
```

Trong thời gian pending, chỉ việc đọc `buffer.position()` để “xem tiến độ” từ thread khác cũng không phải cơ chế an toàn: byte buffer không được thiết kế như công cụ progress concurrent. `Future.isDone()` có thể hữu ích để biết trạng thái Future, nhưng cần xét trường hợp cancelled/failed; **`Future.get()` thành công của chính thao tác** hoặc callback `completed` là ranh giới thông thường để bàn giao quyền sử dụng buffer trở lại.

### 2.2. Kết quả `read` và `write` có thể là từng phần

Với `AsynchronousFileChannel` và `AsynchronousSocketChannel`:

- Đọc vào `ByteBuffer dst` có thể chỉ đọc **một phần** `dst.remaining()`. `result` dương cho biết số byte thực tế; `-1` biểu thị EOF trong điều kiện contract.
- Ghi từ `ByteBuffer src` có thể chỉ ghi **một phần** `src.remaining()`; kết quả trả số byte thực tế.
- Khi có `n > 0`, position của buffer được cập nhật để phản ánh n byte đã xử lý; limit thường không đổi theo mô tả API.
- Nếu cần hoàn tất một message, caller phải thiết kế **chuỗi thao tác kế tiếp**, nhưng chỉ bắt đầu thao tác tiếp theo với cùng buffer **sau khi thao tác trước đã hoàn tất**.

Sơ đồ:

```text
Buffer gửi:          [A B C D E F]
                     ^           ^
                  position      limit

async write #1 ── hoàn tất 2 byte ──► remaining = [C D E F]
async write #2 ── hoàn tất 4 byte ──► remaining = []
                                                    ↑
                                               có thể tái dùng
```

Không đặt `position` thủ công dựa trên một kết quả đang còn pending hoặc suy ra “gọi `write` một lần sẽ ghi hết”.

## 3. Các API bất đồng bộ liên quan

### 3.1. `AsynchronousFileChannel`

`AsynchronousFileChannel` thao tác trên file bằng offset tường minh:

```java
Future<Integer> writeFuture = file.write(src, fileOffset);
Future<Integer> readFuture  = file.read(dst, fileOffset);
```

Đây là hai lời gọi **minh họa độc lập**; `file` phải là `AsynchronousFileChannel` mở đúng quyền và `src`/`dst` là các ByteBuffer thích hợp. Phương thức này **không có file position chung**: mỗi lượt `read/write` truyền `long fileOffset` riêng. Điều đó không làm `ByteBuffer.position()` tự mất ý nghĩa; file offset và vị trí trong buffer là **hai tọa độ khác nhau**.

Overload callback:

```java
file.write(src, fileOffset, attachment, handler);
file.read(dst, fileOffset, attachment, handler);
```

Hai dòng là ví dụ về chữ ký (không phải chương trình biên dịch độc lập): `attachment` là đối tượng do ứng dụng cung cấp, và `handler` là `CompletionHandler<Integer,A>` phù hợp. Tài liệu Java cho phép nhiều lượt read/write outstanding trên cùng `AsynchronousFileChannel`, **nhưng không được dùng cùng một ByteBuffer cho nhiều thao tác I/O concurrent**. Ngoài ra thứ tự hoàn tất các operation và callback không được bảo đảm theo thứ tự bắt đầu. Nếu các thao tác cùng ghi đè vùng file, cần một giao thức riêng.

### 3.2. `AsynchronousSocketChannel`

Socket bất đồng bộ cung cấp:

```java
Future<Integer> fRead  = socket.read(dst);
Future<Integer> fWrite = socket.write(src);
```

Đoạn minh họa với `socket` là `AsynchronousSocketChannel` đã kết nối. Với socket này, **tối đa một read outstanding và một write outstanding tại cùng thời điểm**. Nếu khởi tạo read thứ hai trong khi read trước chưa hoàn tất, có thể ném `ReadPendingException`; tương tự `WritePendingException` cho write thứ hai.

Một read và một write có thể diễn ra đồng thời **khi chúng dùng buffer riêng và ứng dụng phối hợp trạng thái đúng**. Tính thread-safe ở mức channel **không đồng nghĩa** ByteBuffer truyền vào là thread-safe.

### 3.3. `ByteBuffer` không sở hữu kiến thức về lifecycle của channel

`ByteBuffer` không có trạng thái `isInFlight()` hoặc `awaitIo()` được tích hợp. Nếu bạn gọi `src.clear()` hoặc `src.put(...)`, ByteBuffer không tự biết có Future đang sử dụng nó để ngăn chặn. Vì thế trách nhiệm quản lý **ownership** thuộc về ứng dụng.

## 4. Ví dụ sai: sửa buffer ngay sau khi bắt đầu async write

Mã sau cố ý mô tả **lỗi**, không phải chương trình nên chạy trong production:

```java
ByteBuffer payload = ByteBuffer.wrap("FIRST".getBytes(StandardCharsets.US_ASCII));

Future<Integer> pending = channel.write(payload, 0L);

// SAI: thao tác trước vẫn có thể đang dùng payload.
payload.clear();
payload.put("LATER".getBytes(StandardCharsets.US_ASCII));
payload.flip();

int written = pending.get(); // Đợi ở đây là quá muộn.
```

Đoạn minh họa giả định `channel` là `AsynchronousFileChannel` và đã import `ByteBuffer`, `Future`, `StandardCharsets`. Có thể trên một máy nhanh, operation kết thúc trước khi `clear()` chạy nên lỗi **không xuất hiện** trong lần thử đó; nhưng mã vẫn sai vì không có quy tắc đồng bộ và thời điểm I/O hoàn tất không được bảo đảm tại lúc chỉnh buffer.

Những thứ có thể bị ảnh hưởng:

- Content được ghi có thể không phải `FIRST`.
- Position/limit của cùng buffer bị hai phía can thiệp.
- `pending.get()` trả số byte đã ghi nhưng không cung cấp ảnh chụp byte gốc khi buffer đã bị sửa.
- Một test chạy 10.000 lần thành công không chứng minh code an toàn trên mọi OS, executor và tải hệ thống.

**Sửa đúng:** đợi `pending.get()` thành công rồi mới `clear()/put()/flip()` cho lần ghi kế tiếp.

### 4.1. Tại sao `duplicate()` cũng không phải giải pháp nếu vẫn sửa byte gốc?

```java
byte[] data = "FIRST".getBytes(StandardCharsets.US_ASCII);
ByteBuffer original = ByteBuffer.wrap(data);
ByteBuffer view = original.duplicate();

Future<Integer> f = channel.write(view, 0L);

// SAI: view và original chia sẻ cùng bytes.
original.put(0, (byte) 'L');
```

`duplicate()` có position/limit riêng, nhưng **chia sẻ storage**. Tương tự:

- `slice()`: cửa sổ dữ liệu chung.
- `asReadOnlyBuffer()`: view chỉ đọc **qua alias đó**, không ngăn original hoặc array gốc thay bytes.
- `ByteBuffer.wrap(data)`: lưu tham chiếu tới mảng gốc, không sao chép.
- `buffer.array()`: nếu accessible, trả mảng backing, không phải bản copy.

Muốn producer được sửa dữ liệu nguồn trong lúc consumer ghi bất đồng bộ, phải tạo một **snapshot dữ liệu riêng**:

```java
byte[] snapshot = originalBytes.clone();
ByteBuffer owned = ByteBuffer.wrap(snapshot);
Future<Integer> f = channel.write(owned, position);
// Giữ owned và snapshot không bị thay đổi tới khi f hoàn tất.
```

Đoạn minh họa với `originalBytes` và `channel` đã được khai báo; clone với `byte[]` tạo bản sao byte độc lập, nhưng caller vẫn phải bảo đảm không có alias khác sửa `snapshot`. Với một `ByteBuffer` bất kỳ (có thể direct/read-only), sao chép `remaining()` bằng `duplicate().get(...)` **trước khi bắt đầu operation**, khi nguồn ổn định và được sở hữu hợp lệ.

## 5. Ví dụ Java 17+ chạy được: `Future.get()` trước khi tái sử dụng

Ví dụ hoàn chỉnh sau ghi một chuỗi byte vào file tạm bằng `AsynchronousFileChannel`; mỗi lần ghi đợi hoàn tất và xem số byte thực tế rồi mới tiếp tục. Sau đó đọc lại, chờ từng thao tác đọc hoàn tất, và **chỉ cuối cùng mới tái sử dụng buffer nguồn**.

**Tệp:** `AsyncBufferOwnershipDemo.java`

```java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Future;

public class AsyncBufferOwnershipDemo {
    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempFile("async-buffer-", ".bin");

        try {
            try (AsynchronousFileChannel channel =
                    AsynchronousFileChannel.open(
                            temp,
                            StandardOpenOption.READ,
                            StandardOpenOption.WRITE)) {

                ByteBuffer outgoing = ByteBuffer.wrap(
                        "ASYNC".getBytes(StandardCharsets.US_ASCII));

                long writePos = 0L;
                while (outgoing.hasRemaining()) {
                    Future<Integer> operation = channel.write(
                            outgoing, writePos);

                    // Từ đây đến get() thành công: KHÔNG truy cập outgoing.
                    int count = operation.get();

                    if (count <= 0) {
                        throw new IOException("No write progress");
                    }
                    writePos += count; // Chỉ cập nhật sau completion.
                }

                System.out.println("written bytes: " + writePos);
                System.out.println("write remaining: "
                        + outgoing.remaining());

                ByteBuffer incoming = ByteBuffer.allocate(16);
                long readPos = 0L;

                while (readPos < writePos) {
                    Future<Integer> operation = channel.read(
                            incoming, readPos);

                    // Tương tự: không đọc/flip incoming khi I/O pending.
                    int count = operation.get();

                    if (count <= 0) {
                        throw new IOException(
                                "Unexpected EOF/no progress");
                    }
                    readPos += count;
                }

                incoming.flip(); // Sau khi lượt read cuối đã xong.
                System.out.println("read bytes: " + readPos);
                System.out.println("content: "
                        + StandardCharsets.US_ASCII.decode(incoming));

                // Bây giờ lượt ghi đã hoàn tất nên tái dùng outgoing an toàn.
                outgoing.clear();
                outgoing.put(
                        "LATER".getBytes(StandardCharsets.US_ASCII));
                outgoing.flip();
                System.out.println("reused: "
                        + StandardCharsets.US_ASCII.decode(outgoing));
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
```

Biên dịch/chạy trên JDK 17+:

```bash
javac AsyncBufferOwnershipDemo.java
java AsyncBufferOwnershipDemo
```

**Stdout (với hệ thống file tạm hoạt động bình thường):**

```text
written bytes: 5
write remaining: 0
read bytes: 5
content: ASYNC
reused: LATER
```

### 5.1. Phân tích chính xác

1. `Files.createTempFile` tạo file riêng cho demo, không cần mạng hoặc process ngoài.
2. `channel.write(outgoing, writePos)` trả `Future<Integer>`. **Không** chỉnh outgoing trong khoảng đang chờ. `get()` trả số byte đã ghi, đồng thời bảo đảm operation hoàn tất thành công tại thời điểm này.
3. Nếu lượt ghi chỉ xử lý một phần, `outgoing.position()` đã tiến lên theo số byte ghi. Vòng sau dùng `writePos` được cộng đúng số byte đó, tránh ghi đè vùng cũ.
4. Vòng đọc tương tự, sử dụng `incoming` làm destination và từng offset file. Chỉ sau khi đã đọc đủ byte dự kiến và lượt cuối xong mới `flip()` để giải mã.
5. Tái dùng `outgoing` bằng `clear()/put()/flip()` ở cuối là **đúng** vì tất cả các write Future đã hoàn tất trước đó. Việc in `LATER` chỉ thể hiện nội dung buffer mới; demo **không ghi `LATER` vào file**.
6. `try-with-resources` đóng channel sau khi tất cả Future đã hoàn tất. `finally` dọn file tạm. Nếu `get()` ném `InterruptedException` hoặc `ExecutionException`, ví dụ báo lỗi thay vì chạy tiếp như thể thành công; trong ứng dụng production cần giao thức dọn dẹp/điều phối cancellation trước khi tái sử dụng hoặc trả buffer về pool.

**Điểm quan trọng:** vòng lặp gọi `get()` ngay nên ở ví dụ này thread gọi thực tế phải **đợi** completion. API vẫn là asynchronous; cách viết tuần tự này phục vụ dạy về ownership, không phải minh họa tối ưu throughput. Trong chương trình thật, có thể dùng handler hoặc pipeline nhiều **buffer độc lập** để tiếp tục xử lý nghiệp vụ khác trong lúc I/O đang pending.

## 6. Cách đúng với `CompletionHandler`: bàn giao ownership cho callback

Với API callback:

```java
channel.write(payload, 0L, context,
        new CompletionHandler<Integer, Context>() {
            @Override
            public void completed(Integer written, Context ctx) {
                // Thao tác này đã hoàn tất.
                // Xem written, cập nhật offset và ownership.
            }

            @Override
            public void failed(Throwable error, Context ctx) {
                // Báo lỗi và dọn operation/context.
            }
        });
```

Đây là **mã minh họa API**, không phải chương trình biên dịch độc lập: `Context`, `channel`, `payload`, `context` cần được khai báo, với `import java.nio.channels.CompletionHandler;`. Ý tưởng:

- **Trước khi gọi:** chuẩn bị byte hợp lệ, chọn position/limit, có chính sách giữ tham chiếu đến buffer.
- **Khi vừa khởi tạo:** trạng thái buffer thuộc operation. Producer không được sửa, `flip`, `clear`, hoặc trả về pool.
- **Trong `completed`:** xác định số byte thành công, có thể tiếp tục ghi phần còn lại bằng **operation mới trên cùng buffer**, hoặc trả lại quyền cho producer nếu đã xong.
- **Trong `failed`:** báo lỗi và giải quyết trạng thái operation. Với trường hợp timeout/cancellation đặc biệt, phải đọc contract và không tái chế buffer bừa bãi.
- **Ở application layer:** chỉ báo cho caller rằng buffer đã rảnh khi việc dùng nó thực sự kết thúc, không chỉ vì lời gọi `channel.write(...)` đã trả.

### 6.1. Ví dụ helper biên dịch được: chuỗi ghi một `ByteBuffer` đến hết

Để xử lý partial write không chặn caller, helper dưới đây dùng callback để tiếp tục ghi phần `remaining`, và hoàn tất một `CompletableFuture<Void>` chỉ khi toàn bộ bytes đã được ghi (hoặc phát hiện lỗi).

**Tệp:** `AsyncWriteAll.java`

```java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.CompletableFuture;

public final class AsyncWriteAll {
    private AsyncWriteAll() {}

    public static CompletableFuture<Void> writeAll(
            AsynchronousFileChannel channel,
            ByteBuffer owned,
            long startOffset) {

        CompletableFuture<Void> done = new CompletableFuture<>();

        if (startOffset < 0) {
            done.completeExceptionally(
                    new IllegalArgumentException("negative offset"));
            return done;
        }

        if (!owned.hasRemaining()) {
            done.complete(null);
            return done;
        }

        CompletionHandler<Integer, Long> handler =
                new CompletionHandler<>() {
            @Override
            public void completed(Integer count, Long pos) {
                if (count == null || count < 0) {
                    done.completeExceptionally(
                            new IOException("Invalid write result"));
                    return;
                }
                if (count == 0) {
                    // Tránh đệ quy gọi lại lập tức vô hạn khi không tiến độ.
                    done.completeExceptionally(
                            new IOException("No write progress"));
                    return;
                }

                if (!owned.hasRemaining()) {
                    done.complete(null);
                    return;
                }

                try {
                    channel.write(owned, pos + count,
                                  pos + count, this);
                } catch (Throwable failure) {
                    done.completeExceptionally(failure);
                }
            }

            @Override
            public void failed(Throwable error, Long pos) {
                done.completeExceptionally(error);
            }
        };

        try {
            channel.write(owned, startOffset, startOffset, handler);
        } catch (Throwable failure) {
            done.completeExceptionally(failure);
        }

        return done;
    }
}
```

Đây là **helper class có thể biên dịch** bằng `javac AsyncWriteAll.java`, không chứa `main` và không có stdout. Để dùng:

```java
ByteBuffer owned = ByteBuffer.wrap(payload.clone());
CompletableFuture<Void> finished =
        AsyncWriteAll.writeAll(channel, owned, 0L);

// Không đụng tới owned trong lúc finished chưa hoàn tất.
finished.join(); // Chỉ minh họa chờ ở caller.
owned.clear();   // Sau hoàn tất thành công: có thể tái sử dụng.
```

Đoạn caller là ví dụ, yêu cầu `payload` là `byte[]` có dữ liệu, `channel` là AsynchronousFileChannel phù hợp, các import và xử lý trạng thái thất bại. Helper **giả định chỉ một chủ sở hữu** đối với `owned`: không ai khác truy cập bytes hoặc cursor khi `writeAll` đang sử dụng.

**Cẩn thận thực tế:** helper này minh họa cách chain callback, không phải một retry engine đầy đủ. Nó trả lỗi nếu write báo 0 để tránh retry loop vô hạn; production cần timeout, backoff/giám sát và chính sách lỗi thích hợp. Nếu caller dùng `finished.cancel(...)`, việc cancel chính `CompletableFuture` bên ngoài **không tự động hủy các write đã khởi tạo**; không được dùng cancelled state để kết luận vùng buffer đã rảnh. Với lỗi xảy ra trong lúc lên lịch thao tác kế tiếp hoặc đóng channel giữa chừng, phải thiết kế bàn giao lại buffer dựa trên trạng thái I/O thực tế chứ không chỉ là cờ Future của ứng dụng.

### 6.2. Callback chạy trên thread nào?

Đối với `AsynchronousFileChannel`, completion handler được thực thi bằng các thread trong thread pool gắn với channel; trong một số điều kiện khi operation hoàn tất ngay và caller thuộc pool, handler có thể chạy trực tiếp trên thread đó. Không dựa vào một callback luôn chạy trên UI thread, main thread hoặc một thread riêng duy nhất.

Nếu callback phải cập nhật UI, queue ứng dụng, hoặc trạng thái dùng chung, cần bàn giao sang executor/loop phù hợp và bảo đảm synchronization. Lỗi thường gặp là callback hoàn tất I/O đúng rồi lại sửa các object ứng dụng không thread-safe.

## 7. Buffer pool: không được trả buffer về quá sớm

Buffer pool giúp tái sử dụng các vùng nhớ được cấp phát, đặc biệt direct buffer. Nhưng **đưa buffer về pool khi operation còn pending** sẽ cho phép task khác lấy ra và ghi đè vùng bytes mà I/O đang dùng.

**Ví dụ sai:**

```java
ByteBuffer leased = pool.acquire();
prepare(leased);
channel.write(leased, fileOffset); // trả Future chưa hoàn tất
pool.release(leased);              // SAI: release quá sớm
```

Đây là pseudo-code minh họa có `pool`/`prepare` do ứng dụng tự định nghĩa; không phải một Java class độc lập. Nếu buffer được đưa cho task khác, cả vị trí lẫn bytes đều có thể bị thay đổi trong lúc write còn pending.

**Luồng đúng:**

```text
FREE (trong pool)
  └── acquire ──► OWNED_BY_PRODUCER
                      │ prepare
                      └── submit I/O ──► IN_FLIGHT
                                            │
                                            ├── completion + xử lý kết quả
                                            ▼
                                    OWNED_BY_CONSUMER
                                            │ chỉ khi không còn tham chiếu pending
                                            └── release ──► FREE
```

Nguyên tắc:

- Một buffer vật lý chỉ có **một chủ sở hữu độc quyền** trong thời gian một operation đang dùng nó.
- Trả buffer về pool ở **điểm hoàn tất thực sự** (và khi không còn thao tác khác cùng dùng storage), không phải trong `finally` ngay sau khi khởi tạo async I/O.
- Nếu một bản ghi cần nhiều lần write do partial result, buffer vẫn thuộc **cùng một chuỗi operation logic** cho tới khi chuỗi đó thực sự xong.
- Nếu pool chứa các view `slice()/duplicate()` của cùng backing storage, chúng **không** trở thành các buffer độc lập để giao đồng thời. Phải tránh overlap vùng nhớ và có quy tắc ownership theo backing.
- Thất bại, timeout và cancellation cần đường hoàn trả riêng, không được lấy “operation trả exception” làm bằng chứng mọi tác nhân I/O đã ngừng chạm vào buffer.

### 7.1. Khi nào nên copy payload?

Giả sử producer đang dùng một `byte[]` có thể bị thay liên tục, còn I/O cần ghi snapshot của phiên bản hiện tại. Hãy copy tại ranh giới handoff:

```java
byte[] snapshot = producerBytes.clone();
ByteBuffer pendingWrite = ByteBuffer.wrap(snapshot);

Future<Integer> f = asyncFile.write(pendingWrite, 0L);

// Producer tiếp tục sửa producerBytes; pendingWrite vẫn giữ snapshot riêng.
```

Đoạn minh họa yêu cầu producer **không sửa source trong lúc clone đang chạy** nếu snapshot cần nhất quán; clone và handoff vẫn cần phối hợp đúng. Snapshot là byte[] riêng nhưng không cho phép task khác sửa `snapshot` trong lúc I/O sử dụng. Copy có chi phí và không phải lúc nào cần; nếu producer có thể nhường ownership của buffer gốc thì có thể tránh copy.

## 8. Không dùng `slice`/`duplicate`/`asReadOnlyBuffer` để né ràng buộc pending

So sánh các cách tạo buffer:

| Cách tạo | Cursor độc lập? | Bytes chia sẻ? | Có tự giải quyết in-flight ownership? |
|---|---|---|---|
| `ByteBuffer.allocate(n)` hai lần riêng | Có | Không | Có thể giao hai vùng riêng cho hai operation |
| `ByteBuffer.wrap(byte[])` | Cursor riêng của view | Có với mảng gốc | Không |
| `buffer.duplicate()` | Có | Có với buffer gốc | Không |
| `buffer.slice()` | Có | Có với buffer gốc (phần vùng liên quan) | Không |
| `buffer.asReadOnlyBuffer()` | Có | Có với alias writable khác | Không |
| `ByteBuffer.allocateDirect(n)` hai lần riêng | Có | Không giữa hai allocation độc lập | Không tự xử lý vòng đời từng operation |
| Copy bytes vào `byte[]` mới/`ByteBuffer` riêng | Có | Không với nguồn (nếu copy đúng) | Hữu ích nhưng vẫn phải giữ bản sao không bị sửa khi pending |

Ví dụ:

```java
ByteBuffer original =
        ByteBuffer.wrap("HELLO".getBytes(StandardCharsets.US_ASCII));
ByteBuffer view = original.asReadOnlyBuffer();

Future<Integer> f = channel.write(view, 0L);

// SAI: read-only view vẫn nhìn chung byte với original.
original.put(0, (byte) 'X');
```

Read-only chỉ ngăn `view.put(...)`, không ngăn owner sửa bytes qua `original`. Đối với những buffer có `array()`, người giữ tham chiếu mảng cũng có thể sửa bytes bất chấp wrapper read-only; do đó phải kiểm soát nguồn bên dưới.

### 8.1. Không chuyển ByteBuffer sang `byte[]` bằng cách dùng ngay `array()`

`buffer.array()` chỉ dùng được khi backing array truy cập được; direct buffer hoặc read-only heap buffer có thể không cho phép. Hơn nữa array có thể dài hơn vùng `remaining()` cần gửi, và nó chính là storage chia sẻ.

Nếu cần một bản sao độc lập chính xác vùng còn lại trước I/O:

```java
static ByteBuffer copiedRemaining(ByteBuffer source) {
    ByteBuffer reader = source.duplicate();
    byte[] snapshot = new byte[reader.remaining()];
    reader.get(snapshot);
    return ByteBuffer.wrap(snapshot);
}
```

Đoạn trên là **method helper**, dùng được trong class đã import `ByteBuffer`. Nó không thay cursor của `source` và hoạt động cả với direct/read-only buffer; tuy nhiên **caller phải bảo đảm source không bị sửa đồng thời trong chính thời gian copy**. Bản sao độc lập chỉ được giao cho operation khi đã copy xong.

## 9. Timeout, cancellation và failure: góc cạnh nguy hiểm

### 9.1. `Future.get(timeout, unit)` hết thời gian đợi không phải là I/O kết thúc

```java
Future<Integer> f = channel.write(buf, 0L);
try {
    f.get(100, TimeUnit.MILLISECONDS);
} catch (TimeoutException ex) {
    // Chỉ có nghĩa thread đợi chưa có kết quả trong 100 ms.
    // Không được buf.clear() hoặc trả buf về pool ở đây.
}
```

Đoạn minh họa với `channel` là AsynchronousFileChannel, `buf` thuộc riêng operation và import `Future`, `TimeUnit`, `TimeoutException`. `TimeoutException` ở đây thuộc **lượt chờ Future**, không có nghĩa system I/O đã bị hủy. Thao tác có thể vẫn đang đọc/ghi buffer.

Các bước hợp lý: tiếp tục theo dõi Future hoặc thực hiện policy cancellation/đóng channel có điều phối; chỉ trả vùng bộ nhớ khi biết chắc không còn hoạt động dùng nó. Không lấy điều kiện `f.isDone()==false` hoặc thời gian đã trôi qua làm cơ chế giải phóng.

### 9.2. `Future.cancel(...)` có phải hủy I/O tức thì?

Không được giả định. `Future.cancel(...)` yêu cầu hủy theo hợp đồng Future; khả năng hủy một thao tác I/O đã được chuyển xuống OS phụ thuộc implementation và trạng thái thực tế. Kể cả khi Future chuyển sang trạng thái cancelled, ứng dụng nên **bảo thủ** với tính an toàn của backing memory nếu không có bảo đảm cụ thể rằng I/O nền đã thôi truy cập buffer.

Đặc biệt với `AsynchronousSocketChannel`, tài liệu Java mô tả các trường hợp timeout có thể khiến channel hoặc kết nối nằm trong trạng thái lỗi phụ thuộc implementation; khi timeout, trạng thái của `ByteBuffer` có thể **không xác định**. Cách an toàn trong tình huống như vậy thường là đóng channel theo chính sách, loại buffer khỏi pool để không bị tái sử dụng ngay, và thiết lập kết nối mới khi cần.

**Không** lập luận rằng “đã timeout/cancel” = “chưa byte nào được đọc/ghi” hoặc “buffer còn nguyên”.

### 9.3. `ExecutionException`, `InterruptedException` và trạng thái completion

- `future.get()` thành công: operation hoàn tất và trả kết quả; có thể xử lý buffer ở phía ứng dụng.
- `ExecutionException`: operation kết thúc có lỗi; kiểm tra nguyên nhân, số byte có thể đã thay đổi phụ thuộc API, và policy recovery. Không coi luôn là 0 byte đã truyền.
- `InterruptedException` khi thread đang `get()`: **lượt đợi bị ngắt**; bản thân I/O có thể tiếp tục. Đừng quay lại thao tác lên buffer chỉ vì thread không còn chờ. Thường khôi phục interrupt flag theo policy và giữ quản lý lifecycle.
- `CancellationException`: Future báo trạng thái hủy; đừng tự suy luận an toàn tái sử dụng buffer nếu có rủi ro I/O nền chưa chấm dứt hoàn toàn.
- Đóng channel khi operation đang pending có thể khiến operation kết thúc với `AsynchronousCloseException` hoặc lỗi liên quan theo contract; cần đảm bảo cleanup được điều phối.

Một cấu trúc API tốt thường trả một **handle operation** chứa cả Future/callback và quyền sở hữu buffer, thay vì đưa buffer lên pool ngay trong nhánh catch.

## 10. “Dùng buffer khác” vẫn phải xét vùng nhớ dùng chung

Giả sử có hai view khác object:

```java
byte[] bytes = new byte[1024];
ByteBuffer a = ByteBuffer.wrap(bytes);
ByteBuffer b = ByteBuffer.wrap(bytes);
```

Hai đối tượng `a` và `b` có vị trí riêng nhưng nhìn **cùng byte[]**. Nếu giao chúng cho hai I/O async đồng thời, có thể gây dữ liệu cạnh tranh, đặc biệt một thao tác read và một thao tác write hoặc hai lần write mà producer vẫn can thiệp mảng.

Tương tự, một `ByteBuffer` slice của vùng lớn hơn có thể là hợp lệ để giao nhiều operation **chỉ khi** các phần vùng vật lý hoàn toàn không chồng nhau và code quản lý đúng bounds, owner, trạng thái và lifecycle của từng phần. Không sử dụng identity `a != b` làm bằng chứng memory regions không overlap.

### 10.1. Nhiều operation trên cùng AsynchronousFileChannel

Java cho phép các read/write file operation tồn tại đồng thời và không định nghĩa thứ tự bắt đầu/hoàn tất. Nếu muốn I/O song song:

- Cấp buffer **độc lập** cho từng operation hoặc chia vùng không overlap với ownership rõ ràng.
- Cấp file offset **riêng**, tránh ghi chồng cùng vùng file nếu nghiệp vụ không cho phép.
- Không mặc định callback A xảy ra trước callback B theo thứ tự `write(A)` rồi `write(B)`.
- Khi ghép dữ liệu, dựa vào file offsets và results, không chỉ thứ tự hoàn tất.
- Nếu tạo stream/log tuần tự, có thể cần một writer queue thay vì khởi tạo một loạt ghi async cùng offset rồi hy vọng hệ thống tự sắp thứ tự.

### 10.2. Một read và một write trên socket cùng lúc

`AsynchronousSocketChannel` hỗ trợ đồng thời tối đa một read và một write ở cùng thời điểm, nhưng **hai buffer phải độc lập về ownership**. Dùng một buffer duy nhất cho cả input và output đồng thời là lỗi ngay cả khi socket cho phép hai operation khác hướng diễn ra cùng lúc.

Sử dụng input/output state riêng:

```text
ConnectionState:
   inboundBuffer  → pending read → completed → decode → chuẩn bị lần read mới
   outboundBuffer → pending write → completed → cập nhật remaining → write tiếp
```

Nếu muốn echo byte vừa đọc lại cho client, phải chờ read hoàn tất, `flip` vùng input đúng lúc, rồi **chuyển ownership** của nó sang write hoặc copy vào buffer output. Không khởi tạo read mới trên cùng backing bytes khi write echo vẫn pending.

## 11. Đối chiếu vòng đời buffer giữa đọc và ghi

| Giai đoạn | Buffer đọc (đích) | Buffer ghi (nguồn) |
|---|---|---|
| Chuẩn bị | `clear()` hoặc đặt position/limit tới vùng có thể nhận byte | `flip()` sau bước ghi payload, hoặc `wrap(bytes)` |
| Khởi tạo | `read(dst, ...)` | `write(src, ...)` |
| Đang pending | **Không đọc, sửa, flip, clear, tái sử dụng** | **Không đọc, sửa, flip, clear, tái sử dụng** |
| Hoàn tất thành công | Xem số byte `n`, position đã tiến; `flip()` khi sẵn sàng tiêu thụ | Xem số byte `n`, position đã tiến; nếu `hasRemaining()`, tiếp tục write |
| Kết thúc logical operation | Consume dữ liệu và giải phóng quyền sở hữu khi parser xong | Trả pool hoặc dùng lại khi không còn byte chờ ghi |
| Timeout/cancel/error | Xử lý theo contract, không giả định buffer còn nguyên hoặc nền I/O đã dừng | Cũng vậy; phải giữ chính sách tránh reuse sớm |

**Ví dụ sai phía read:**

```java
ByteBuffer receive = ByteBuffer.allocate(4096);
Future<Integer> f = socket.read(receive);

receive.flip();  // SAI: read có thể chưa ghi byte vào receive.
parse(receive);  // SAI: parse dữ liệu chưa hoàn tất.
```

Snippet có `socket` thuộc `AsynchronousSocketChannel` và `parse` là hàm ứng dụng, không độc lập. Hành vi sai không nhất thiết ném exception; có thể parse được dữ liệu rỗng/cũ, thay limit khiến kết quả read không đúng contract sử dụng.

**Sửa:**

```java
Future<Integer> f = socket.read(receive);
int n = f.get(); // Đợi completion thành công.
if (n > 0) {
    receive.flip();
    parse(receive);
}
```

Đoạn này minh họa caller đồng bộ đợi kết quả; cần import và xử lý checked exceptions. Nếu n = -1, xử lý EOF/đóng kết nối, không parse dữ liệu mới. Nếu n = 0, xử lý retry/event state, không tự giả định đã có thông điệp.

## 12. Asynchronous I/O không đồng nghĩa mọi đoạn code phải đa luồng

`Future.get()` tạo một điểm đợi có kiểm soát; giúp dạy quy tắc ownership nhưng có thể khiến event loop bị chặn nếu dùng liên tục. `CompletionHandler` cho phép caller làm việc khác; tuy nhiên callback phải xử lý thread safety và vòng đời tài nguyên.

Có thể thiết kế một queue `PendingOperation`:

```text
Producer:
    tạo payload/buffer mới
    enqueue request có ownership
    signal I/O worker

I/O worker / async API:
    lấy request
    khởi tạo read/write
    giữ tham chiếu và đánh dấu IN_FLIGHT

Completion callback:
    xử lý result
    nếu còn bytes → khởi tạo operation tiếp theo, vẫn giữ buffer
    nếu kết thúc → signal completion + chuyển ownership/return pool

Error/cancel:
    đi qua cleanup state machine riêng, tránh premature release
```

Khi chuyển ownership qua nhiều thread, cần công cụ Java Memory Model thích hợp: `BlockingQueue`, `CompletableFuture` với callback đã điều phối, hoặc executor/lock. Chỉ dùng biến boolean thường (không volatile/synchronization) để một thread “tự biết” buffer đã rảnh có thể gây race và thiếu memory visibility.

### 12.1. Backpressure tránh tăng số buffer pending vô hạn

Nếu producer tạo dữ liệu nhanh hơn channel ghi, hệ thống có thể tích lũy hàng nghìn buffer và Future:

- Đặt giới hạn số operation outstanding và tổng bytes pending.
- Chặn, giảm tốc hoặc từ chối producer khi vượt quota.
- Dùng pool cố định nhưng **chỉ phát lại buffer đã giải phóng an toàn**.
- Phân biệt “đã enqueue”, “I/O đang pending”, “đã hoàn tất”, và “đã được application xác nhận”.
- Với TCP, việc write hoàn tất nghĩa byte đã được chấp nhận theo semantics channel, **không** có nghĩa peer đã đọc và xử lý toàn bộ message; muốn ACK ở tầng nghiệp vụ cần giao thức riêng.

## 13. Các lỗi thường gặp

1. **Gọi async `write` rồi lập tức `clear()`.** I/O vẫn có thể sử dụng bytes và cursor cũ.
2. **Gọi async `read` rồi `flip()` trước completion.** Có thể thay limit trong khi hệ thống đang ghi vào buffer.
3. **Sửa `byte[]` gốc sau `wrap(array)`** dù ByteBuffer đang được write async sử dụng. Wrap không copy.
4. **Tưởng `duplicate()` tạo storage độc lập.** Nó chỉ tách cursor, vẫn chia sẻ backing bytes.
5. **Tưởng `asReadOnlyBuffer()` đóng băng payload.** Alias writable khác vẫn sửa bytes được.
6. **Dùng direct buffer để bỏ qua quy tắc concurrent access.** Heap/direct đều yêu cầu ownership.
7. **Cho rằng Java channel thread-safe thì ByteBuffer cũng thread-safe.** Hai contract khác nhau.
8. **Gọi hai read hoặc hai write socket khi thao tác trước pending.** `ReadPendingException`/`WritePendingException`.
9. **Ghi socket một lần rồi kết luận message đã gửi hết.** Có thể partial write, cần giữ remaining.
10. **Tái sử dụng file buffer trong nhiều write outstanding.** `AsynchronousFileChannel` cho phép nhiều operation, không cho phép dùng một buffer concurrent an toàn.
11. **Dựa vào callback thứ tự bắt đầu.** Completion handlers file có thể chạy không theo thứ tự submit.
12. **Trả buffer pool trong `finally` bao quanh lời gọi khởi tạo.** Finally chạy ngay khi phương thức khởi tạo trả về, không đợi async completion.
13. **Dùng `get(timeout)` hết hạn như dấu hiệu thao tác đã dừng.** Nó chỉ báo lượt chờ chưa xong.
14. **Hủy Future rồi tái chế buffer ngay.** Không mặc định cancellation bảo đảm backend đã ngừng truy cập memory.
15. **Dùng `Future.isDone()` như dấu hiệu mọi kiểu kết thúc đều an toàn như success.** Cancelled/failed cần policy khác.
16. **Nuốt `InterruptedException` và sửa buffer.** Thread chờ bị ngắt không có nghĩa I/O đã hoàn tất.
17. **Đóng channel rồi lập tức gửi buffer cho I/O khác mà không xét lifecycle/callback.** Operation còn outstanding phải được dọn và xác nhận trạng thái theo contract.
18. **Không quản lý file offsets trong partial file writes.** Dễ ghi lặp/ghi đè hoặc tạo lỗ.
19. **Dùng cùng backing array với hai view khác nhau đồng thời.** Identity object khác không bảo đảm storage độc lập.
20. **Gọi `read` trên read-only ByteBuffer.** Destination phải ghi được; nếu không có thể ném `IllegalArgumentException`.
21. **Tưởng ghi hoàn tất nghĩa dữ liệu file đã durable qua mất điện.** Completion I/O khác `force(true)` và chính sách durability.
22. **Tưởng write TCP hoàn tất nghĩa peer đã nhận/ACK.** Đây là quy tắc tầng truyền thông, không phải application acknowledgment.
23. **Không kiểm tra số byte hoàn tất.** Nếu kết quả khác số bytes mong đợi, phải tiếp tục/ghi nhận EOF/lỗi.
24. **Không phân biệt application queue và in-flight operation.** Buffer có thể vẫn được callback dùng trong một chuỗi writeAll.
25. **Dùng timeout socket rồi vẫn cố đoán buffer còn nguyên.** Contract timeout có thể để buffer/channel trong trạng thái không xác định.

## 14. Tự kiểm tra

**Câu 1.** `AsynchronousFileChannel.write(buffer,pos)` đã trả `Future`. Có thể gọi `buffer.clear()` ngay không?  
**Không.** Future đại diện một operation pending; chỉ an toàn sau khi operation hoàn tất và ownership được bàn giao lại.

**Câu 2.** Dùng `ByteBuffer duplicate = buffer.duplicate()` có an toàn cho writer nếu producer vẫn sửa bytes gốc?  
**Không.** Duplicate có cursor riêng nhưng share backing storage.

**Câu 3.** Có thể bắt đầu một read và một write đồng thời trên `AsynchronousSocketChannel` không?  
**Có**, theo giới hạn một read và một write outstanding, nhưng phải dùng buffer độc lập và điều phối kết nối đúng.

**Câu 4.** `Future.get(100, MILLISECONDS)` ném `TimeoutException`. Buffer có thể được đưa ngay vào pool không?  
**Không.** Thao tác I/O vẫn có thể đang chạy; timeout của lượt chờ không phải completion.

**Câu 5.** `Future.get()` trả 2 cho buffer ban đầu còn 5 byte. Có thể dùng lại buffer cho payload khác ngay không?  
**Chưa nên** nếu logical write phải gửi đủ 5 byte. Sau completion, buffer còn 3 byte; hãy tiếp tục operation mới với đúng offset, chờ xong toàn bộ rồi mới tái dùng.

**Câu 6.** Vì sao `channel.isOpen() == false` không phải là tín hiệu giải phóng mọi buffer pool mà không cần kiểm tra?  
**Vì** close, cancellation và callback/lỗi của các operation outstanding có lifecycle riêng. Cần xử lý trạng thái I/O/callback và bảo đảm không còn truy cập concurrent.

**Câu 7.** `ByteBuffer.wrap(byte[])` có tự sao chép mảng không?  
**Không.** Nó chia sẻ mảng. Nếu caller cần snapshot byte riêng, copy trước khi giao cho I/O.

**Câu 8.** Gọi `read(dst)` trả Future xong; lúc nào nên `dst.flip()` để parse?  
**Sau** khi operation read hoàn tất thành công (và đã xét result/EOF), không phải ngay sau lời gọi khởi tạo.

**Câu 9.** Hai lần write file có buffer riêng, file offset riêng; callback có chắc theo thứ tự gửi không?  
**Không.** `AsynchronousFileChannel` không cam kết thứ tự completion của các operation outstanding.

**Câu 10.** Muốn cấp phát nhiều buffer từ pool mà không tăng memory vô hạn, cần gì?  
**Cần** quota/backpressure và quy tắc chỉ trả buffer khi không còn operation hay alias đang sử dụng nó.

## 15. Tóm tắt áp dụng và tài liệu chính thức

**Quy tắc kiểm tra trước khi tái sử dụng ByteBuffer:**

1. Operation bất đồng bộ nào đang giữ buffer (hoặc bất kỳ view/backing array dùng chung) đã **thực sự hoàn tất** chưa?
2. Đã xử lý kết quả `n`, partial read/write, EOF, lỗi, timeout/cancellation đúng contract chưa?
3. Có một operation kế tiếp trong chuỗi logical read/write vẫn cần giữ buffer không?
4. Có thread, callback, attachment hoặc alias khác còn quyền ghi/đọc concurrent lên cùng bytes không?
5. Chỉ khi quyền sở hữu đã về phía ứng dụng/pool, mới `clear()`, `flip()`, sửa nội dung hoặc đưa buffer cho nhiệm vụ mới.

Một câu cần nhớ: **“Asynchronous I/O mượn buffer cho đến khi hoàn tất; lời gọi khởi tạo trả về không kết thúc lượt mượn.”**

Tài liệu Java SE 21:

- `AsynchronousFileChannel` — nhiều operation, thứ tự completion, cảnh báo không truy cập ByteBuffer trước khi xong: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/AsynchronousFileChannel.html
- `AsynchronousByteChannel` — partial read/write, buffer ownership và pending exceptions: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/AsynchronousByteChannel.html
- `AsynchronousSocketChannel` — một read/one write outstanding, timeout và tình trạng buffer: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/AsynchronousSocketChannel.html
- `CompletionHandler` — `completed`/`failed`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/CompletionHandler.html
- `ByteBuffer` — `position`, `limit`, `duplicate`, `slice`, backing array: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/ByteBuffer.html
- `Future` — `get`, cancellation và timeout: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Future.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="asynchronous-io-007">Tiếp tục đọc ghi khi kết quả chỉ hoàn tất một phần</a>

<details>
<summary>Xem chi tiết</summary>

# Tiếp tục đọc ghi khi kết quả I/O bất đồng bộ chỉ hoàn tất một phần

## 1. Vấn đề cốt lõi: một operation hoàn thành không đồng nghĩa cả message hoàn tất

Trong Java NIO.2, **I/O bất đồng bộ** cho phép ứng dụng khởi động thao tác `read` hoặc `write` rồi nhận kết quả qua `Future` hoặc `CompletionHandler`. Một thao tác I/O đã **completed successfully** chỉ có nghĩa là *lượt thao tác đó* đã kết thúc theo hợp đồng của API. Nó **không hứa** đã chuyển hết những byte ứng dụng mong muốn.

Ví dụ bạn muốn gửi 100 byte:

~~~text
ByteBuffer cần ghi: 100 byte
     |
     +--> async write lần 1: completed(37)
     |         buffer còn 63 byte
     |
     +--> async write lần 2: completed(20)
     |         buffer còn 43 byte
     |
     +--> async write lần 3: completed(43)
               buffer hết byte --> message gửi hết từ phía local
~~~

Số byte `37, 20, 43` chỉ mang tính minh họa; kết quả thực tế phụ thuộc channel, OS, tải và thời điểm. Ngay cả khi mọi byte được **local write** chấp nhận, điều đó **không bảo đảm** peer đã nhận hay xử lý thành công.

Đối với đọc cũng tương tự: một yêu cầu nhận frame 100 byte có thể hoàn tất lần đầu với 12 byte. Muốn có đúng 100 byte, cần **tái gửi read** với buffer còn chỗ cho đến khi đủ hoặc gặp EOF/lỗi/deadline.

Quy tắc trung tâm:

> **Lấy số byte thực sự hoàn thành làm nguồn sự thật, giữ trạng thái của phần còn lại, chỉ khởi động operation kế tiếp khi operation trước đã kết thúc, và có điểm dừng rõ ràng.**

## 2. Phân biệt ba loại hoàn thành

| Khái niệm | Ý nghĩa | Ví dụ |
|---|---|---|
| **I/O operation completed** | Một lời gọi `read`/`write` đã có result hoặc failure | `completed(12, attachment)` |
| **Buffer completed** | Buffer đích đã đầy để đọc, hoặc buffer nguồn đã hết để ghi | `!dst.hasRemaining()` hoặc `!src.hasRemaining()` |
| **Message/protocol completed** | Toàn bộ frame/request/response đúng giao thức đã được xử lý | Đã nhận đủ length-prefixed body và xác thực |

Trong TCP, ứng dụng tự xây **message framing**: length-prefix, delimiter hoặc fixed-length. Một lời gọi `AsynchronousSocketChannel.read(dst, handler)` không tương ứng một message. Trong file, việc đọc buffer chỉ xong một chunk; còn tệp lớn cần nhiều chunk và các file offset kế tiếp.

Đặc biệt: **async không có nghĩa `Future.get()` không block**. Lời gọi khởi động operation có thể trả sớm, nhưng `future.get()` đợi kết quả và chặn thread gọi. `CompletionHandler` phù hợp khi muốn tiếp tục hoàn toàn dựa trên callback; `Future` phù hợp với cách phối hợp việc đợi kết quả ở một thread cho phép chờ.

## 3. Kết quả read và write cần được hiểu thế nào?

### 3.1. Đọc vào ByteBuffer

Với các asynchronous read API trả `Integer`:

- **`n > 0`:** đã đọc đúng `n` byte. ByteBuffer đích được tăng `position` tương ứng; `limit` không tự thay đổi.
- **`n == 0`:** lượt đọc không chuyển byte nào (ví dụ khi buffer không có `remaining`; trường hợp khác tùy API). Không coi đây là EOF và không tạo vòng lặp retry nóng vô hạn.
- **`n == -1`:** end-of-stream khi không còn byte để đọc. Với socket TCP, đó là phía vào đã kết thúc; với file, đã đến EOF theo điều kiện đọc.
- **Failure:** nhận qua `CompletionHandler.failed(Throwable, A)` hoặc `Future.get()` ném `ExecutionException` (hoặc cancellation/interruption khác).

Ví dụ buffer 8 byte, `position=0`, `limit=8`; nếu `completed(3)`, giờ `position=3`, `remaining=5`. Gọi lại read trên **chính buffer** này (sau khi operation trước đã xong) có thể ghi thêm byte vào từ vị trí 3, không được `clear()` làm mất trạng thái đã nhận.

### 3.2. Ghi từ ByteBuffer

Với các asynchronous write API trả `Integer`:

- **`n > 0`:** `n` byte đã được lấy khỏi buffer nguồn cho lượt ghi; `position` tăng `n`.
- **`n == 0`:** chưa ghi được byte nào. Cần chính sách retry có kiểm soát/deadline, tránh callback tự lặp ngay liên tục.
- **`src.hasRemaining() == true` sau completion:** chưa ghi hết nội dung cần gửi; tiếp tục dùng buffer còn phần chưa ghi.
- **`src.hasRemaining() == false`:** tất cả byte thuộc buffer đã được chuyển qua các lời gọi write thành công. Với TCP, đây **không** phải xác nhận bên nhận đã đọc.
- **Failure:** dừng chuỗi thao tác, thông báo lỗi và xử lý trạng thái có thể đã ghi được một phần.

Trước khi gửi, buffer phải ở **read mode** (`flip()` nếu vừa `put(...)`). Nếu dùng `StandardCharsets.UTF_8.encode(text)`, buffer được trả sẵn để đọc. Không `clear()` hay `rewind()` giữa các lần partial write; làm vậy có thể gửi lại byte cũ.

### 3.3. Không dùng phép kiểm tra sai

~~~java
// Sai: completed() được gọi không chứng minh đã gửi hết.
channel.write(src, null, new CompletionHandler<Integer, Void>() {
    public void completed(Integer n, Void unused) {
        System.out.println("Đã xong toàn message"); // có thể sai
    }
    public void failed(Throwable ex, Void unused) {
        ex.printStackTrace();
    }
});
~~~

Đây là **đoạn phản ví dụ**, yêu cầu `channel` đã connected và các import thích hợp. Chính xác phải kiểm tra `n` và `src.hasRemaining()` rồi quyết định tiếp tục hay hoàn thành.

## 4. ByteBuffer: position, limit, flip, compact là phần của thuật toán

ByteBuffer là nơi giữ **trạng thái đang đọc/ghi dở**. Một số chuyển trạng thái cần nắm:

| Thao tác | Tác dụng | Khi nào nên dùng |
|---|---|---|
| `clear()` | `position=0`, `limit=capacity`; **không xóa dữ liệu vật lý** | Bắt đầu fill một chunk mới khi dữ liệu cũ đã xử lý xong |
| `flip()` | `limit=position`, `position=0` | Chuyển buffer đã nhận/đã ghi bằng `put` sang chế độ đọc |
| `compact()` | Đưa byte còn lại từ `position..limit` về đầu rồi chuyển sang chế độ ghi | Parser đọc được một phần frame, còn dư byte chưa xử lý |
| `hasRemaining()` | `position < limit` | Kiểm tra còn byte cần ghi hoặc còn khoảng trống nhận |
| `remaining()` | `limit - position` | Tính byte còn lại, kiểm tra độ dài |
| `get()`/`put()` | Đọc/ghi tương đối và tăng position | Parser/serializer |

Với **socket read**, nếu application đang lắp frame 12 byte trong một buffer capacity 12:

~~~java
ByteBuffer dst = ByteBuffer.allocate(12);
// read lần 1 nhận 5 byte -> position = 5
// read lần 2 nhận 4 byte -> position = 9
// read lần 3 nhận 3 byte -> position = 12
dst.flip(); // bây giờ parse đúng 12 byte
~~~

Đây là **mã giải thích trạng thái**, không phải chương trình I/O chạy độc lập. Các read giả định hoàn tất tuần tự và buffer chưa bị thao tác trong lúc I/O pending.

**Không gọi `flip()` sau mỗi partial read khi đang cố fill một buffer cố định.** Chỉ flip khi đã nhận đủ vùng cần đọc hoặc khi chủ đích chuyển sang pha xử lý dữ liệu hiện có. Nếu parser tiêu thụ một số byte nhưng còn fragment của frame, dùng `compact()` để giữ fragment.

## 5. Future và CompletionHandler: hai cách tiếp tục

### 5.1. Future<Integer>

`AsynchronousFileChannel.read(dst, filePosition)`, `write(src, filePosition)` và `AsynchronousSocketChannel.read(dst)`/`write(src)` có overload trả `Future<Integer>`.

Một quy trình đơn giản:

~~~java
Future<Integer> pending = asyncFile.read(dst, fileOffset);
int n = pending.get(); // chờ completion; thread gọi bị block
// Sau khi get thành công, dst.position() đã phản ánh byte nhận được.
// Nếu vẫn chưa đủ dữ liệu, khởi tạo một read mới ở fileOffset + n.
~~~

Đây là mã minh họa với `AsynchronousFileChannel asyncFile`, `ByteBuffer dst`, `long fileOffset` có sẵn. `Future.get()` có thể ném `InterruptedException` hoặc `ExecutionException`; code cần chính sách xử lý/cancellation.

Điểm lợi là dễ viết vòng lặp tuần tự, dễ giải thích tính đúng đắn. Điểm hạn chế là thread `get()` phải chờ; nó không biến chương trình thành callback-driven nonblocking processing.

### 5.2. CompletionHandler<V,A>

Callback style trả kết quả qua:

~~~java
void completed(V result, A attachment);
void failed(Throwable exc, A attachment);
~~~

`V` thường là `Integer` cho một buffer, `Long` cho scattering/gathering nhiều buffer, hoặc `Void` cho connect. `A` là **attachment** do ứng dụng chọn để giữ trạng thái của operation, chẳng hạn file offset, ByteBuffer, request ID hoặc đối tượng phiên.

Trong `completed`:

1. Nếu `n > 0`, cập nhật offset theo **số byte thực tế** (khi API có offset).
2. Kiểm tra `ByteBuffer.hasRemaining()`.
3. Nếu còn dữ liệu, gọi tiếp asynchronous read/write với buffer và offset đúng.
4. Nếu đã đủ, hoàn thành operation ở cấp **message**, báo cho phần tiếp theo và giải phóng ownership.
5. Nếu gặp `n == 0` hoặc failure, dùng retry policy/deadline thích hợp; không tự tạo vòng lặp bất tận hoặc nuốt exception.

Trong `failed`, phải kết thúc/chuyển trạng thái request, không để `CompletableFuture`/latch chờ vô thời hạn. Completion handler nên ngắn; xử lý CPU nặng nên chuyển sang executor ứng dụng nếu cần.

## 6. AsynchronousFileChannel: offset là bắt buộc và độc lập

Khác với `FileChannel` có **current file position**, `AsynchronousFileChannel` **không có một current position chung**. Mỗi lời gọi `read`/`write` truyền **file position** dạng `long`. Ví dụ:

~~~java
Future<Integer> readFuture = input.read(dst, 100L);
int bytesRead = readFuture.get();
// Nếu cần đọc tiếp liên tiếp trong file: vị trí kế tiếp là 100L + bytesRead.
~~~

Mã minh họa: đây là offset **trong file**, không phải `ByteBuffer.position()`. Phải quản lý hai con số:

- `fileOffset`: byte index trong file.
- `buffer.position()`: vị trí trong vùng buffer cho lần I/O kế tiếp.

Hai chỉ số này không tự đồng bộ với nhau. Sau một write chỉ hoàn thành `n` byte, **write lại tại `fileOffset + n`** với buffer đã có position tăng n. Nếu giữ offset cũ cho partial write kế tiếp, dữ liệu mới có thể đè lên vùng vừa ghi. Nếu tự cộng thêm cả `buffer.position()` một lần nữa, có thể tạo khoảng trống hoặc bỏ byte.

`AsynchronousFileChannel` cho phép **nhiều I/O cùng outstanding**, nhưng thứ tự hoàn thành và thứ tự callback không được bảo đảm. Không khởi chạy hàng loạt write có vùng file chồng lấn rồi giả định chúng sẽ tuần tự đúng thứ tự được submit. Một `ByteBuffer` cũng không an toàn khi đồng thời dùng cho nhiều I/O đang pending.

### Ví dụ minh họa diễn tiến

~~~text
File nguồn:      A B C D E F G H I J
Buffer capacity: 6

read(dst, offset=0)  -> n=4, dst.position=4
read(dst, offset=4)  -> n=2, dst.position=6
flip()              -> chuẩn bị ghi 6 byte A..F

write(src, offset=0) -> n=3, src.position=3
write(src, offset=3) -> n=3, src.position=6 (hết chunk)

Đọc chunk tiếp:
read(dst, offset=6) -> ... cho đến EOF
~~~

Các số `4,2,3,3` là ví dụ, không có cam kết đó là số byte kết quả trên máy cụ thể.

## 7. Chương trình Java 17+ chạy được: copy bằng asynchronous file channels

Chương trình thực sự copy nội dung tệp bằng `AsynchronousFileChannel`, không giả định mỗi read/write trả đủ toàn buffer.

- **Cố ý dùng buffer capacity 4 byte** cho file 21 byte, nên chắc chắn cần nhiều lượt đọc/ghi theo từng chunk.
- Vòng ngoài đọc từ file nguồn với offset tăng theo `read` thực tế.
- Vòng trong ghi hết byte của chunk từ buffer với offset tăng theo `written` thực tế.
- `Future.get()` khiến ví dụ dễ quan sát và **đồng bộ tại điểm đợi**. Các I/O vẫn được khởi tạo qua asynchronous channel.
- Vì ví dụ không cần mô phỏng backpressure, nếu một I/O trả 0 bất ngờ trên buffer còn dữ liệu, chương trình **dừng có báo lỗi** thay vì vòng lặp nóng vô hạn; sản phẩm thật có thể triển khai retry có deadline và scheduling.

**Tệp `AsyncPartialDemo.java`:**

~~~java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

public class AsyncPartialDemo {
    public static void main(String[] args) throws Exception {
        Path source = Files.createTempFile("async-source-", ".txt");
        Path target = Files.createTempFile("async-target-", ".txt");
        byte[] expected =
                "async-data-0123456789".getBytes(StandardCharsets.UTF_8);

        try {
            Files.write(source, expected);
            long copied = 0L;

            try (AsynchronousFileChannel input =
                         AsynchronousFileChannel.open(
                                 source, StandardOpenOption.READ);
                 AsynchronousFileChannel output =
                         AsynchronousFileChannel.open(
                                 target, StandardOpenOption.WRITE,
                                 StandardOpenOption.TRUNCATE_EXISTING)) {

                ByteBuffer buffer = ByteBuffer.allocate(4);
                long readOffset = 0L;
                long writeOffset = 0L;

                for (;;) {
                    buffer.clear();
                    int read = input.read(buffer, readOffset).get();

                    if (read == -1) {
                        break; // Hết file nguồn.
                    }
                    if (read == 0) {
                        throw new IOException("No read progress");
                    }

                    readOffset += read;
                    buffer.flip();

                    while (buffer.hasRemaining()) {
                        int written =
                                output.write(buffer, writeOffset).get();

                        if (written <= 0) {
                            throw new IOException("No write progress");
                        }
                        writeOffset += written;
                    }
                }
                copied = writeOffset;
            }

            System.out.println("bytes=" + copied);
            System.out.println("equal=" +
                    Arrays.equals(expected, Files.readAllBytes(target)));
            System.out.println("text=" + Files.readString(target));
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(target);
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac AsyncPartialDemo.java
java AsyncPartialDemo
~~~

**Stdout đã kiểm chứng trên JDK:**

~~~text
bytes=21
equal=true
text=async-data-0123456789
~~~

Ý nghĩa:

1. 21 byte không thể nằm hết trong buffer 4 byte; chương trình phải xử lý nhiều chunk.
2. `input.read(buffer, readOffset).get()` chỉ chờ **một read operation**. Số byte thành công `read` được cộng vào offset nguồn.
3. `buffer.flip()` chuẩn bị đúng các byte đã đọc để ghi, không bao gồm byte cũ của buffer.
4. `while(buffer.hasRemaining())` là nơi xử lý **partial write** nếu channel ghi ít byte hơn mong muốn; `writeOffset` tăng bằng `written` thực tế.
5. Khi `read == -1`, vòng lặp dừng vì EOF. Sau khi đóng channel, chương trình xác nhận file đích có đúng nội dung.

Do chương trình dùng `Future.get()`, mỗi iteration chờ completion trước khi dùng lại buffer. Trong pipeline callback thật, phải giữ **ownership** của buffer cho tới callback mới xử lý/tái sử dụng nó.

## 8. Resubmit bằng CompletionHandler: mẫu partial write

Muốn tránh chặn thread bằng `get()`, handler có thể tự khởi động một write tiếp theo sau khi callback trước hoàn thành. Trong ví dụ sau, `AsynchronousFileChannel` cho phép truyền file offset một cách tường minh.

**Đoạn mã minh họa đặt trong một lớp đã có các import liên quan:**

~~~java
static CompletableFuture<Void> writeAll(
        AsynchronousFileChannel channel,
        ByteBuffer src,
        long startOffset) {

    CompletableFuture<Void> done = new CompletableFuture<>();

    if (!src.hasRemaining()) {
        done.complete(null);
        return done;
    }

    CompletionHandler<Integer, Long> handler =
            new CompletionHandler<>() {
        @Override
        public void completed(Integer n, Long offset) {
            if (n <= 0) {
                done.completeExceptionally(
                        new IOException("Write did not progress"));
                return;
            }

            long nextOffset = offset + n;
            if (!src.hasRemaining()) {
                done.complete(null);
                return;
            }

            try {
                channel.write(src, nextOffset, nextOffset, this);
            } catch (RuntimeException ex) {
                done.completeExceptionally(ex);
            }
        }

        @Override
        public void failed(Throwable exc, Long offset) {
            done.completeExceptionally(exc);
        }
    };

    channel.write(src, startOffset, startOffset, handler);
    return done;
}
~~~

Đây là **phương thức minh họa, không phải chương trình Java độc lập**; lớp chứa nó cần import `java.nio.ByteBuffer`, `java.nio.channels.AsynchronousFileChannel`, `java.nio.channels.CompletionHandler`, `java.io.IOException` và `java.util.concurrent.CompletableFuture`. Đầu vào `channel` đang open để ghi, `startOffset >= 0` và `src` phải ở read mode.

Cách nó tiếp tục partial write:

- Lượt đầu `channel.write(src, startOffset, startOffset, handler)` gắn attachment `Long` chính là file offset của lượt vừa khởi tạo.
- Callback `completed(n, offset)` chạy khi operation đó kết thúc. `src.position()` đã được cập nhật theo n byte đã viết.
- Nếu còn dữ liệu, handler gửi tiếp `write(src, offset+n, offset+n, this)`.
- Nếu không còn dữ liệu, `done.complete(null)` đánh dấu **toàn buffer** đã được chuyển hết, không chỉ một lượt.
- Nếu failure hoặc không tiến triển trong ví dụ, `done.completeExceptionally(...)` khiến caller có thể quan sát lỗi.

**Về `n == 0`:** đoạn minh họa cố ý kết thúc với lỗi nếu không tiến triển thay vì tạo vòng callback bận. Trong ứng dụng production, có thể **lập lịch retry sau**, dùng retry budget/deadline/backoff hoặc xác định provider/backend không tiến triển. Không coi việc resubmit lập tức vô hạn khi `n==0` là giải pháp tổng quát.

**Về recursion/ordering:** callback có thể được dispatch rất nhanh và ở một số implementation có thể chạy trực tiếp trên thread khởi tạo theo hợp đồng. Nếu chuỗi callback rất dài, cần cân nhắc chuyển bước tiếp sang executor/trampoline để tránh recursion sâu hoặc chiếm thread completion. Cũng không được sửa hoặc dùng lại `src` ở thread khác trong lúc mỗi operation còn pending.

## 9. Tiếp tục partial read trong CompletionHandler

Muốn đọc **đúng N byte** cho một TCP frame fixed-length, buffer cần đặt `limit` để `remaining() == N` lúc khởi động. Sau mỗi completion:

- `n > 0` và buffer còn chỗ: gửi tiếp một `read` mới trên cùng buffer.
- `n > 0`, buffer hết chỗ: frame đủ N byte; `flip()` rồi xử lý.
- `n == -1` trước khi nhận đủ: remote đóng output sớm; frame bị truncate. Đây là **lỗi framing** dù EOF là kết quả I/O hợp lệ.
- `n == 0` khi vẫn còn chỗ: không tuyên bố EOF và tránh retry nóng; áp dụng chính sách chờ/retry với deadline.
- `failed(exc)`: kết thúc frame có lỗi và dọn tài nguyên theo ownership.

Pseudocode Java theo mô hình callback (không phải chương trình độc lập):

~~~java
channel.read(dst, state, new CompletionHandler<Integer, State>() {
    @Override
    public void completed(Integer n, State s) {
        if (n == -1) {
            s.fail(new EOFException("Incomplete frame"));
            return;
        }

        if (!dst.hasRemaining()) {
            dst.flip();
            s.completeFrame(dst);
            return;
        }

        if (n == 0) {
            s.scheduleRetry(); // deadline/retry policy của ứng dụng
            return;
        }

        channel.read(dst, s, this); // chỉ sau completion trước
    }

    @Override
    public void failed(Throwable exc, State s) {
        s.fail(exc);
    }
});
~~~

Trong pseudocode này, `State` và `scheduleRetry()` là abstraction ứng dụng **chưa được định nghĩa**, nên **không thể compile độc lập**. `scheduleRetry()` phải biết cách tái gửi một read duy nhất vào đúng channel/buffer khi phù hợp, tránh nhiều operation outstanding; không phải phương thức tích hợp của Java. `s.completeFrame(dst)` phải chuyển ownership buffer an toàn.

Một lựa chọn khác với protocol **length-prefixed** là trước tiên đọc đủ header (ví dụ 4 byte), giải mã độ dài theo endian quy định, validate size, sau đó cấp buffer body hoặc duy trì state machine để đọc đủ body. Không được tin length từ peer khi chưa giới hạn kích thước tối đa.

## 10. Khác nhau giữa AsynchronousSocketChannel và AsynchronousFileChannel

| Đặc điểm | AsynchronousSocketChannel | AsynchronousFileChannel |
|---|---|---|
| Mô hình dữ liệu | TCP stream không có message boundary | Tệp byte có file offset |
| Offset trong lời gọi | `read(dst)`/`write(src)` không nhận explicit file offset | `read(dst, position)`/`write(src, position)` |
| Số I/O đồng thời | Tối đa một read pending và một write pending trên socket channel | Nhiều read/write có thể pending; thứ tự không bảo đảm |
| Overlap | Read mới trước khi read cũ xong: `ReadPendingException`; write tương tự `WritePendingException` | Không dùng chung một ByteBuffer cho I/O đồng thời; tránh ghi vùng file chồng lấn không có thứ tự |
| EOF | `read == -1` khi stream đầu vào kết thúc | `read == -1` khi đã tới EOF file |
| Deadline | Overload read/write có timeout (CompletionHandler) | Thường quản lý deadline/cancellation ở tầng ứng dụng |
| Failure đặc biệt | `InterruptedByTimeoutException`; trạng thái channel/buffer có thể không xác định sau timeout | `ExecutionException` bao bọc lỗi Future; close với pending I/O có thể kết thúc `AsynchronousCloseException` |

Với socket, tại một thời điểm có thể có **một read và một write outstanding đồng thời**, nhưng không được khởi tạo read thứ hai khi read đầu còn pending. Nếu dùng callback để re-read, chính callback completion là vị trí tự nhiên khởi tạo read tiếp; tương tự cho write. Nếu luồng khác cũng đọc/ghi cùng socket, cần một bộ điều phối đảm bảo không gửi trùng operation.

**Đặc biệt với timeout:** Java 17 cảnh báo nếu timeout xảy ra khi không thể xác định chính xác bao nhiêu byte đã được đọc/ghi, buffer và trạng thái một chiều I/O của socket có thể ở trạng thái implementation-specific, các operation tiếp có thể ném runtime exception không xác định. Không tự động retry trên chính buffer/channel như thể không byte nào đã truyền; thường cần đóng kết nối hoặc tái đồng bộ giao thức có chủ đích. 

## 11. Buffer ownership và an toàn đồng thời

Một `ByteBuffer` đang được dùng cho asynchronous read/write **không an toàn nếu ứng dụng đồng thời thay đổi** `position`, `limit` hoặc nội dung trước khi operation hoàn tất. Đây là nguồn lỗi rất khó tái hiện.

Các quy tắc quan trọng:

1. **Một owner tại một thời điểm.** Khi submit I/O, trao ownership buffer cho operation/handler tới khi completion hoặc failure được xử lý.
2. **Không `clear`/`flip`/`put` vào buffer đang pending** từ thread khác. Buffer position có thể đang bị thay đổi bởi operation.
3. **Không dựa vào thứ tự callback của các AsynchronousFileChannel operation khác nhau**. Ghi không chồng lấn và quản lý offset rõ ràng.
4. **AsynchronousSocketChannel chỉ cho một read và một write pending**. Điều này không đồng nghĩa thread-safe cho business state hay ByteBuffer chung.
5. **Truyền buffer và offset trong một state/attachment** để tránh đụng biến dùng chung giữa các kết nối.
6. **Khi yêu cầu bị hủy**, xác định operation còn có thể hoàn thành/tác động buffer hay không theo hợp đồng API; tránh tái dùng buffer quá sớm. Với `Future.cancel`, hậu quả có thể phụ thuộc việc I/O đã hoàn thành hoặc cancellation có đóng channel.

Mô hình state machine cho một buffer ghi:

~~~text
NEW
 |
 +-- submit write ---> PENDING
                          |
                  completed(n > 0)
                          |
                 +--------+---------+
                 |                  |
           hasRemaining         no remaining
                 |                  |
              PENDING            COMPLETE
                 |
         failed / timeout
                 |
               FAILED
~~~

Khi nhiều request chia sẻ một TCP connection, cần queue các thông điệp đầu ra để một writer điều phối duy nhất giữ **thứ tự byte giao thức**. Không nên mở hai asynchronous writes trên cùng socket rồi mặc định chúng tự được nối theo thứ tự mong muốn.

## 12. Xử lý lỗi, zero progress, EOF và cancellation

### EOF không phải zero

- **`-1`:** luồng/tệp đã kết thúc theo hợp đồng read; nếu đang nhận frame cố định mà chưa đủ byte, báo frame bị thiếu.
- **`0`:** lượt này không chuyển byte. Không kết luận remote đã đóng; chọn retry policy hợp lý. Với buffer hết remaining, việc trả 0 là hiển nhiên.
- **`n > 0`:** có tiến triển; tiếp tục tới khi đạt điều kiện đủ dữ liệu.

### Tránh retry vô hạn khi trả 0

Trong thực tế, nếu hệ thống/backend có thể trả 0 nhiều lần, cần một trong các chiến lược: lịch retry bất đồng bộ theo thời điểm; deadline timeout của operation/request; giới hạn số lần không tiến triển; hoặc chờ readiness theo API phù hợp. **Không `while(true)` bận** gọi lập tức cùng operation từ completion handler mỗi khi `n==0`.

### Tách lỗi I/O với lỗi logic giao thức

Ví dụ `read == -1` là EOF hợp lệ ở cấp stream nhưng là **lỗi application** nếu body mới nhận 10/100 byte. `write` hoàn thành không đồng nghĩa peer đã phản hồi. `ExecutionException` từ `Future.get` chứa root cause I/O; nên kiểm tra `getCause()`. Với `InterruptedException`, cân nhắc phục hồi interrupt flag và dừng tác vụ theo chính sách. Với timeout/cancellation, không được kết luận buffer chắc chắn chưa bị thay đổi nếu Javadoc không hứa.

### Close và tài nguyên

`AsynchronousFileChannel`/`AsynchronousSocketChannel` triển khai `AutoCloseable`. Dùng `try-with-resources` cho phạm vi sở hữu. Không đóng channel trong lúc callback còn cần nó hoặc khi operation đang pending, trừ khi đó chính là chính sách hủy: close có thể khiến outstanding operations kết thúc bằng `AsynchronousCloseException`. Đối với hệ thống server, quản lý vòng đời channel group, worker executor và queue độc lập với buffer/channels.

## 13. Cách áp dụng vào giao thức thực tế

**TCP length-prefixed message:**

1. Đọc đủ 4 byte header bằng vòng read/callback có trạng thái.
2. `flip` header, đọc độ dài theo `ByteOrder` đã thống nhất và validate tối đa.
3. Cấp buffer body đúng dung lượng (hoặc stream theo chunk nếu body lớn).
4. Tiếp tục đọc body tới khi buffer đầy; EOF trước khi đủ là frame lỗi.
5. Xử lý body sau khi I/O completed và chuyển ownership; nếu có nhiều message trong stream, giữ buffer dư và state parser.

**Gửi response:**

1. Serialize header + body ra các ByteBuffer sẵn để đọc.
2. Submit write đầu tiên.
3. Sau completion, kiểm tra số byte và `hasRemaining`; tiếp tục phần chưa ghi.
4. Chỉ chuyển sang message tiếp theo khi các buffer hiện tại hết byte hoặc quy tắc framing cho phép.
5. Nếu write bị fail hoặc timeout, cập nhật trạng thái request; đừng âm thầm loại các byte chưa gửi.

**Copy file bằng async file channels:**

1. Đặt file offset nguồn/đích riêng.
2. Đọc một chunk; cộng source offset theo bytesRead.
3. `flip` buffer; ghi tới hết chunk; cộng target offset theo bytesWritten.
4. `clear` buffer sau khi cả chunk đã ghi xong; đọc chunk tiếp.
5. Khi EOF, chỉ báo hoàn thành sau khi mọi write đã được xác nhận.

## 14. Các lỗi phổ biến

1. **“CompletionHandler.completed nghĩa là message hoàn tất.”** Sai: result có thể nhỏ hơn expected.
2. **“`Future.get()` trả xong là đã đọc hết buffer.”** Sai: mới hoàn thành một operation.
3. **“Một TCP read là một message.”** Sai: TCP stream cần framing.
4. **“`read()==0` tức EOF.”** Sai: EOF có mã `-1`.
5. **“`write()` luôn chuyển hết `remaining`.”** Sai: kiểm tra số byte và gửi tiếp.
6. **“Partial write xong cứ `rewind()` rồi ghi lại.”** Sai: gửi lặp byte cũ.
7. **“Sau mỗi partial read, gọi `clear()` cho lần kế tiếp.”** Sai: có thể mất fragment mới nhận.
8. **“AsynchronousFileChannel tự tăng current file position.”** Sai: không có current position chung; offset do caller quản lý.
9. **“Sau write n byte, chỉ cần tăng file offset theo capacity buffer.”** Sai: cộng đúng `n`.
10. **“Có thể bắt đầu hai async read trên một AsynchronousSocketChannel.”** Sai: `ReadPendingException`; tương tự write pending.
11. **“AsynchronousFileChannel callback luôn gọi theo thứ tự submit.”** Sai: thứ tự không được bảo đảm.
12. **“ByteBuffer đang pending dùng lại ở thread khác vẫn an toàn.”** Sai: phải bảo vệ ownership tới khi operation hoàn thành.
13. **“Callback `n == 0` thì retry liên tục càng nhanh.”** Sai: dễ busy-spin; cần chính sách chờ.
14. **“Timeout nghĩa là không byte nào đã được chuyển.”** Sai: một số asynchronous socket timeout có trạng thái buffer/I/O không xác định.
15. **“Local write hết byte chứng minh peer đã xử lý.”** Sai: cần response/ACK theo giao thức ứng dụng.

## 15. Câu hỏi tự kiểm tra

**Câu 1.** Một `ByteBuffer` có `remaining=40`; callback write trả `12`. Bước tiếp theo?

**Đáp án:** Buffer tự có position tăng 12; còn 28 byte. Chờ completion trước đó đã xong, khởi tạo write tiếp theo cho phần còn lại, không `rewind` buffer.

**Câu 2.** Một TCP frame cần 100 byte; read callback trả `30` rồi `-1`. Có được coi đã nhận thành công?

**Đáp án:** Không. Stream đã EOF trước khi frame đủ 100 byte; đó là truncated frame ở cấp giao thức.

**Câu 3.** `AsynchronousFileChannel.write(src, 50)` hoàn thành `n=7` nhưng buffer vẫn còn byte. File position lần tiếp theo là bao nhiêu?

**Đáp án:** `57`; buffer position đã tăng 7 để bắt đầu từ phần chưa ghi.

**Câu 4.** Với `AsynchronousSocketChannel`, có thể submit read thứ hai ngay khi read trước còn pending?

**Đáp án:** Không; `ReadPendingException`. Phải nối tiếp sau completion hoặc điều phối state để chỉ có một read pending.

**Câu 5.** Vì sao ví dụ `Future.get()` vẫn là I/O bất đồng bộ ở cấp channel nhưng code nhìn giống blocking?

**Đáp án:** Operation khởi tạo trên asynchronous channel và trả Future. Tuy vậy caller gọi `get()` đã chủ động block để chờ completion; callback có thể giúp tránh việc chờ theo kiểu này.

### Tài liệu chính thức

- [Java 17 — AsynchronousFileChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/AsynchronousFileChannel.html)
- [Java 17 — AsynchronousSocketChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/AsynchronousSocketChannel.html)
- [Java 17 — AsynchronousByteChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/AsynchronousByteChannel.html)
- [Java 17 — CompletionHandler](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/CompletionHandler.html)
- [Java 17 — ByteBuffer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="asynchronous-io-008">Xử lý lỗi, ngắt, timeout và đóng channel</a>

<details>
<summary>Xem chi tiết</summary>

# Xử lý lỗi, ngắt, timeout và đóng channel trong I/O bất đồng bộ Java NIO

## 1. Tại sao I/O bất đồng bộ cần quản lý vòng đời chặt chẽ?

Các API Java NIO.2 như `AsynchronousFileChannel`, `AsynchronousSocketChannel` và `AsynchronousServerSocketChannel` cho phép bắt đầu read/write/connect/accept rồi nhận kết quả **sau đó**, thay vì bắt luồng gọi đợi thao tác hoàn tất ngay tại lời gọi.

Thao tác có thể hoàn tất thành công, thất bại, bị cancel, bị timeout, hoặc bị ảnh hưởng bởi việc đóng channel. Cần phân biệt **trạng thái Future/callback**, **tình trạng byte đã chuyển**, **quyền sử dụng ByteBuffer**, và **tình trạng kết nối/tệp**. Đây không phải cùng một khái niệm.

~~~text
Request -> submit async I/O -> PENDING
                                |   |    |
                                |   |    +-- cancel/timeout/close
                                |   +------- FAILED
                                +----------- COMPLETED
                                             |
                                       trả kết quả số byte
Sau mỗi nhánh: phân loại, kết thúc request đúng một lần,
dọn channel/buffer/attachment theo quyền sở hữu.
~~~

Không đồng nhất câu “đã gửi yêu cầu ghi” với “đã ghi đủ”, và cũng không đồng nhất “timeout chờ kết quả” với “I/O chắc chắn đã dừng”.

## 2. Future và CompletionHandler: hai mô hình nhận kết quả

### Future

~~~java
// Đoạn minh họa: file đã là AsynchronousFileChannel mở để đọc.
ByteBuffer buffer = ByteBuffer.allocate(256);
Future<Integer> pending = file.read(buffer, 0L);

int n = pending.get(); // Có thể đợi đến khi I/O kết thúc.
~~~

- `get()` chờ và trả số byte đọc/ghi; có thể ném `InterruptedException` hoặc `ExecutionException`.
- `get(timeout, unit)` có thể ném thêm `TimeoutException` khi **caller** chờ quá hạn.
- `cancel(false/true)` cố hủy computation; `isCancelled()` và `isDone()` cho biết trạng thái Future.
- Không dùng lại ByteBuffer cho operation khác trước khi có quy trình xử lý completion/cancel an toàn.

Lời gọi I/O là bất đồng bộ, nhưng `future.get()` vẫn có thể **block thread đang đợi**. Mô hình này đơn giản, nhưng gọi `get()` tuần tự cho từng request có thể làm giảm concurrency.

### CompletionHandler

~~~java
// Đoạn minh họa, chưa phải chương trình độc lập.
file.read(buffer, 0L, "req-42",
        new CompletionHandler<Integer, String>() {
            @Override
            public void completed(Integer n, String requestId) {
                System.out.println(requestId + ": " + n);
            }

            @Override
            public void failed(Throwable error, String requestId) {
                System.err.println(requestId + ": " + error);
            }
        });
~~~

`CompletionHandler<V,A>` có hai callback:

- `completed(V result, A attachment)`: hoàn tất thành công.
- `failed(Throwable exc, A attachment)`: hoàn tất thất bại.

Attachment có thể chứa request ID, trạng thái, context hoặc đối tượng điều phối. Handler được dispatch qua thread pool của async channel; trong một số trường hợp hoàn tất tức thì có thể được gọi trực tiếp từ thread khởi tạo đang thuộc pool.

**Handler nên ngắn.** Không block lâu hoặc xử lý CPU nặng trong callback pool. Chuyển công việc dài sang executor riêng nếu cần.

### Lỗi submit khác lỗi hoàn tất

Một lời gọi khởi tạo operation có thể ném lỗi ngay: tham số sai, channel chưa kết nối, read/write cùng chiều đang pending, v.v. Nếu đã khởi tạo thành công mà I/O lỗi sau đó, kết quả đi qua `failed(...)` hoặc `Future.get()`.

Chỉ bọc try/catch quanh `read(..., handler)` **không thay thế** việc triển khai `failed(...)`.

## 3. Những lỗi thường gặp và cách phân loại

| Trường hợp | Ngoại lệ/kết quả | Hành động thường phù hợp |
|---|---|---|
| Future gặp lỗi I/O | `ExecutionException` từ `get()` | Xem `getCause()`, kết thúc request lỗi |
| Handler gặp lỗi | `failed(Throwable, attachment)` | Hoàn tất promise/trạng thái lỗi |
| Caller đợi Future bị ngắt | `InterruptedException` | Khôi phục interrupt flag, xem xét cancel |
| Caller đợi quá hạn | `TimeoutException` | Đừng suy ra I/O nền đã dừng |
| Future bị cancel | `CancellationException` khi `get()` | Không báo thành công, tránh retry mù |
| File I/O đang chờ khi channel đóng | `AsynchronousCloseException` | Dọn pending request và resource |
| Channel đã đóng trước lúc gọi | `ClosedChannelException` hoặc lỗi phù hợp API | Không submit I/O mới |
| Socket chưa kết nối | `NotYetConnectedException` | Kết nối trước |
| Socket đã có một read pending | `ReadPendingException` | Không submit read chồng lấn |
| Socket đã có một write pending | `WritePendingException` | Xếp hàng write |
| Socket I/O timeout trực tiếp | `InterruptedByTimeoutException` | Cân nhắc loại bỏ buffer/channel |
| Read tới EOF | Giá trị `-1` | Phân biệt EOF hợp lệ và frame thiếu |
| Một lần read/write chỉ xử lý một phần | Số byte nhỏ hơn mong muốn | Lưu ByteBuffer position, tiếp tục đúng cách |

**Đừng bắt tất cả exception rồi retry vô hạn.** Timeout, EOF, channel close và lỗi lập trình không có cùng chính sách khôi phục.

## 4. Gỡ lỗi `ExecutionException` và xử lý lỗi từ callback

Khi dùng Future:

~~~java
// Minh họa trong phương thức có các biến và checked exceptions.
try {
    int n = pending.get();
    consume(n);
} catch (ExecutionException e) {
    Throwable root = e.getCause();
    if (root instanceof AsynchronousCloseException) {
        // Channel bị đóng khi đang xử lý.
    } else if (root instanceof IOException) {
        // Lỗi I/O file hoặc mạng.
    } else {
        // Lỗi khác: không xem là thành công.
    }
}
~~~

`consume` là hàm do ứng dụng định nghĩa. Nên ghi log root cause, request ID, file offset hoặc peer, loại operation, số byte đã biết, trạng thái channel.

Khi dùng callback, xử lý ngay `failed(Throwable, attachment)`, không dựa vào `ExecutionException` vì callback-overload không trả Future cho operation đó. Nếu code trong handler tự ném exception, lỗi đó là lỗi của logic xử lý callback; hãy bắt và chuyển về promise/trạng thái request phù hợp, tránh làm hỏng dispatcher.

### Thành công cũng cần kiểm tra số byte

Một lần `read()` có thể đọc ít hơn lượng mong đợi; `-1` nghĩa EOF. Một lần `write()` có thể chỉ ghi một phần của buffer. Không coi `completed(3)` là “đã nhận toàn bộ frame 10 byte”.

## 5. `InterruptedException`: ngắt người đợi, không tự hủy I/O

Ví dụ:

~~~java
try {
    int n = pending.get();
    use(n);
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    pending.cancel(false);
    // Chuyển request sang trạng thái hủy theo chính sách.
}
~~~

Đây là đoạn minh họa, `pending` và `use` đã được định nghĩa bên ngoài.

Các điểm không được nhầm:

1. `InterruptedException` ở đây báo **thread đang chờ** Future bị interrupt.
2. Nó **không chứng minh** I/O đang thực thi ở background đã dừng hoặc chưa có byte nào được chuyển.
3. Thường khôi phục trạng thái ngắt bằng `Thread.currentThread().interrupt()` hoặc truyền exception cho tầng trên xử lý.
4. Nếu quyết định hủy, cần có bước `cancel(...)`/`close()` và kế hoạch buffer ownership.
5. Không `clear()` hoặc tái sử dụng buffer của operation đang pending vì caller đã bỏ chờ.

| Lệnh | Tác dụng chủ yếu | Không bảo đảm |
|---|---|---|
| `thread.interrupt()` | Đánh dấu ngắt thread; `get()` đang chờ có thể ném `InterruptedException` | I/O đã rollback |
| `future.cancel(false)` | Cố hủy Future, không yêu cầu interrupt task đang chạy | Không còn side effects |
| `future.cancel(true)` | Cố hủy và có thể yêu cầu interrupt task | Dừng kernel I/O ngay |
| `channel.close()` | Đóng channel, ảnh hưởng pending operations | Rollback các byte đã chuyển |

## 6. Timeout của `Future.get(timeout)` chỉ giới hạn thời gian đợi

~~~java
int n = pending.get(250, TimeUnit.MILLISECONDS);
~~~

Nếu quá hạn, thread chờ nhận `TimeoutException`, nhưng operation nền có thể vẫn đang chạy. Tình huống:

~~~text
t0: file.read(...) bắt đầu
t1: Future.get(250ms) bắt đầu chờ
t2: caller nhận TimeoutException
t3: I/O nền vẫn có thể tiếp tục và sửa ByteBuffer
t4: I/O cuối cùng hoàn tất/thất bại theo lịch riêng
~~~

Vì vậy không được trả buffer vào pool hoặc dùng cùng buffer cho thao tác mới ngay ở t2.

Một mẫu xử lý:

~~~java
// Minh họa, file và pending đã được khai báo.
try {
    int n = pending.get(250, TimeUnit.MILLISECONDS);
    consume(n);
} catch (TimeoutException e) {
    boolean cancelled = pending.cancel(false); // Best effort.
    // Không coi cancelled == true nghĩa là chưa ghi byte nào.
    // Đánh dấu request timeout; quản lý buffer/channel riêng.
}
~~~

Đây là giới hạn cho **waiter**, không phải timeout trực tiếp ở bên trong `AsynchronousFileChannel.read/write`. Các overload file read/write có offset nhưng không có tham số timeout I/O như socket.

## 7. Timeout của `AsynchronousSocketChannel`: ngữ nghĩa mạnh hơn nhưng rủi ro cao

Asynchronous socket có overload read/write callback với tham số timeout:

~~~java
// Minh họa: socket đã kết nối, dst writable.
socket.read(dst,
        500, TimeUnit.MILLISECONDS,
        requestId,
        new CompletionHandler<Integer, String>() {
            @Override
            public void completed(Integer n, String id) {
                // I/O hoàn tất.
            }

            @Override
            public void failed(Throwable error, String id) {
                if (error instanceof InterruptedByTimeoutException) {
                    // Timeout ngay trong thao tác read.
                }
                // Kết thúc request theo chính sách.
            }
        });
~~~

Theo tài liệu Java 17:

- Nếu timeout xảy ra trước khi I/O hoàn tất, handler được báo lỗi **`InterruptedByTimeoutException`**.
- Nếu triển khai không thể bảo đảm byte chưa/không còn được đọc hoặc ghi, channel có thể bị đặt vào **trạng thái lỗi đặc thù triển khai**. Read/write tiếp theo cùng chiều có thể ném runtime exception không chỉ rõ.
- **Trạng thái ByteBuffer hoặc mảng buffer sau I/O timeout là không xác định.**
- Các giá trị timeout **nhỏ hơn hoặc bằng 0** nghĩa là thao tác không timeout (không phải poll ngay).

**Cách an toàn thường dùng:** coi request/connection đã không còn tin cậy khi timeout socket mà kết quả byte không rõ, đóng channel và không tái sử dụng buffer liên quan. Nếu cần thử lại, mở kết nối mới và dùng request ID/idempotency key để tránh xử lý trùng.

### So sánh trực tiếp

| Cơ chế | Ngoại lệ | Điều đã biết khi timeout |
|---|---|---|
| `Future.get(500, MILLISECONDS)` | `TimeoutException` | Caller hết thời gian chờ; I/O có thể vẫn chạy |
| `socket.read(dst,500,MILLISECONDS,att,handler)` | `InterruptedByTimeoutException` trong `failed` | I/O báo timeout, ByteBuffer không xác định, channel có thể bị lỗi |
| Deadline nghiệp vụ tổng thể | Tùy tầng ứng dụng | Vẫn cần quyết định cancel, close, retry |

`AsynchronousSocketChannel.connect(...)` có Future và callback nhưng không cùng overload timeout như read/write; connect deadline thường do tầng điều phối quản lý.

## 8. Cancel Future không đồng nghĩa rollback

`future.cancel(mayInterruptIfRunning)` cố chuyển Future sang trạng thái canceled. Nếu hủy được chấp nhận, `isCancelled()` là true và `get()` có thể ném `CancellationException`.

Nhưng `cancel(true)` hoặc `cancel(false)` **không phải giao dịch rollback**:

- Một số byte có thể đã được OS đọc/ghi trước khi hủy.
- Peer có thể đã xử lý request dù caller không nhận kết quả.
- `ByteBuffer.position()` không phải căn cứ đủ để kết luận trạng thái giao thức sau cancel.
- Không retry một lệnh thay đổi trạng thái từ xa nếu thiếu idempotency hoặc version check.
- Nếu không thể xác định I/O đã kết thúc và buffer đã an toàn, nên cô lập hoặc loại bỏ buffer thay vì tái sử dụng ngay.

Điểm mấu chốt: **cancel là quản lý lifecycle của operation, không phải undo dữ liệu**.

## 9. Đóng channel khi còn pending operation

`AsynchronousFileChannel.close()` có thể được gọi bất cứ lúc nào. Theo hợp đồng Java 17, các operation đang outstanding trên channel sẽ hoàn tất với **`AsynchronousCloseException`**.

~~~java
// Minh họa: file là AsynchronousFileChannel.
Future<Integer> op = file.read(dst, 0L);

// Thread khác có thể gọi:
file.close();

// Thread đang get() cần chuẩn bị ExecutionException
// có cause là AsynchronousCloseException.
~~~

Đóng channel không đảm bảo những byte đã được chuyển bị hoàn tác. Nếu `isOpen()` trả true tại một thời điểm, thread khác vẫn có thể đóng ngay sau đó: kiểm tra rồi gọi I/O **không loại race**.

### Quy trình shutdown có kiểm soát

~~~text
OPEN -> CLOSING -> CLOSED
          |
          +-- không nhận thêm request mới
          +-- đánh dấu pending operations
          +-- chờ có deadline cho những operation cần thiết
          +-- cancel/close những operation còn lại
          +-- dọn callback, buffer, attachment
          +-- shutdown pool/group sở hữu
~~~

Cần thiết kế request cũng có trạng thái CREATED → PENDING → COMPLETED/FAILED/CANCELLED → CLEANED để mỗi request nhận kết quả **một lần duy nhất**. Nếu callback nhận `AsynchronousCloseException` do shutdown có chủ ý, đó có thể là một lỗi **được dự kiến**; đừng báo thành công nhưng cũng không nhất thiết xem là sự cố hệ thống.

### Ai sở hữu tài nguyên nào?

| Tài nguyên | Đóng đúng cách |
|---|---|
| `AsynchronousFileChannel` | `close()` hoặc try-with-resources |
| `AsynchronousSocketChannel` | `close()` khi request/connection hoàn tất hay lỗi |
| `AsynchronousServerSocketChannel` | `close()` để ngừng accept |
| `ExecutorService` riêng của async file | `shutdown()`, `awaitTermination(...)`; `shutdownNow()` khi cần |
| `AsynchronousChannelGroup` riêng cho async socket | `shutdown()` / `shutdownNow()` / `awaitTermination(...)` |

Trong **Java 17**, `ExecutorService` không phải `AutoCloseable`; hãy shutdown tường minh thay vì đưa executor vào try-with-resources. Không để một request shutdown pool hoặc group đang được nhiều thành phần chia sẻ.

`AsynchronousSocketChannel.shutdownInput()` / `shutdownOutput()` là half-close theo protocol, **không thay thế** đóng toàn bộ channel khi connection gặp lỗi trạng thái nghiêm trọng.

## 10. ByteBuffer ownership, I/O partial và concurrency

Các Buffer được chuyển vào asynchronous operation **không an toàn để truy cập khi operation còn pending**. Không làm:

~~~java
// Sai: cùng ByteBuffer có thể bị background read sử dụng.
ByteBuffer dst = ByteBuffer.allocate(1024);
Future<Integer> first = file.read(dst, 0L);
dst.clear();
Future<Integer> second = file.read(dst, 1024L);
~~~

Mặc dù `AsynchronousFileChannel` cho phép nhiều I/O outstanding, thứ tự I/O và thứ tự callback không được bảo đảm khi chúng đồng thời. Dùng cùng buffer cho nhiều pending operations là không an toàn. Nếu nhiều writes cùng offset, kết quả cuối không tự theo thứ tự submit.

### Async socket chỉ cho một pending read và một pending write cùng lúc

Trên một `AsynchronousSocketChannel`, có thể có một read và một write đang chờ đồng thời, nhưng:

- Read thứ hai khi read trước còn pending → `ReadPendingException`.
- Write thứ hai khi write trước còn pending → `WritePendingException`.

Duy trì queue gửi/nhận, chỉ submit I/O tiếp theo cùng chiều sau khi operation trước hoàn tất. Đừng bắt exception rồi spin retry.

### Một completion chưa chắc chuyển đủ ByteBuffer

Nếu buffer còn remaining sau read/write, có thể cần thao tác tiếp theo. Ví dụ ghi file:

~~~java
// Minh họa: file AsynchronousFileChannel, src và offset đã định nghĩa.
while (src.hasRemaining()) {
    Future<Integer> op = file.write(src, offset);
    int n = op.get();
    if (n <= 0) {
        throw new IOException("No write progress");
    }
    offset += n;
}
~~~

Không submit write tiếp lên cùng buffer trước khi Future trước kết thúc. Khi read trả `-1` nghĩa EOF; nếu phải đọc đúng N byte, EOF trước N là frame/record bị thiếu.

## 11. Chương trình Java 17+ hoàn chỉnh: Future, callback và close

Ví dụ sau không cần server mạng. Chương trình tạo file tạm, dùng `AsynchronousFileChannel`, ghi chuỗi ASCII `ABC` qua Future, đọc lại bằng CompletionHandler, đóng channel, shutdown executor và dọn file.

Lưu chính xác **`AsyncIoLifecycleDemo.java`**:

~~~java
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class AsyncIoLifecycleDemo {

    private static int await(Future<Integer> future)
            throws InterruptedException, ExecutionException,
                   TimeoutException {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(false);
            throw e;
        } catch (TimeoutException e) {
            future.cancel(false);
            throw e;
        }
    }

    private static int writeAll(
            AsynchronousFileChannel channel, ByteBuffer src)
            throws Exception {
        long offset = 0;
        int total = 0;

        while (src.hasRemaining()) {
            Future<Integer> op = channel.write(src, offset);
            int n = await(op);
            if (n <= 0) {
                throw new IOException("No write progress");
            }
            total += n;
            offset += n;
        }
        return total;
    }

    private static CompletableFuture<String> readAll(
            AsynchronousFileChannel channel, int expectedBytes) {
        ByteBuffer dst = ByteBuffer.allocate(expectedBytes);
        CompletableFuture<String> result = new CompletableFuture<>();

        class Reader implements CompletionHandler<Integer, Void> {
            private long offset = 0;

            void start() {
                channel.read(dst, offset, null, this);
            }

            @Override
            public void completed(Integer n, Void attachment) {
                if (n < 0) {
                    result.completeExceptionally(
                            new EOFException("Truncated file"));
                    return;
                }
                if (n == 0) {
                    result.completeExceptionally(
                            new IOException("No read progress"));
                    return;
                }

                offset += n;
                if (dst.hasRemaining()) {
                    try {
                        channel.read(dst, offset, null, this);
                    } catch (RuntimeException e) {
                        result.completeExceptionally(e);
                    }
                    return;
                }

                dst.flip();
                byte[] bytes = new byte[dst.remaining()];
                dst.get(bytes);
                result.complete(new String(
                        bytes, StandardCharsets.US_ASCII));
            }

            @Override
            public void failed(Throwable error, Void attachment) {
                result.completeExceptionally(error);
            }
        }

        try {
            new Reader().start();
        } catch (RuntimeException e) {
            result.completeExceptionally(e);
        }
        return result;
    }

    public static void main(String[] args) throws Exception {
        Path file = Files.createTempFile("async-io-demo-", ".bin");
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            AsynchronousFileChannel channel =
                    AsynchronousFileChannel.open(
                            file,
                            Set.of(StandardOpenOption.READ,
                                   StandardOpenOption.WRITE),
                            pool);

            try (channel) {
                ByteBuffer out = ByteBuffer.wrap(
                        "ABC".getBytes(StandardCharsets.US_ASCII));
                System.out.println(
                        "future-write=" + writeAll(channel, out));

                CompletableFuture<String> read =
                        readAll(channel, 3);
                System.out.println(
                        "callback-read=" + read.get(5, TimeUnit.SECONDS));

                System.out.println("open=" + channel.isOpen());
            }

            System.out.println("closed=" + !channel.isOpen());
        } finally {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            } finally {
                Files.deleteIfExists(file);
            }
        }
    }
}
~~~

Biên dịch và chạy từ thư mục chứa file nguồn:

~~~bash
javac AsyncIoLifecycleDemo.java
java AsyncIoLifecycleDemo
~~~

**Stdout mong đợi khi I/O thành công, không bị interrupt/timeout:**

~~~text
future-write=3
callback-read=ABC
open=true
closed=true
~~~

Giải thích:

1. `writeAll` ghi tới khi source buffer hết remaining. Nó không giả định một write luôn ghi đủ ba byte và cập nhật offset sau mỗi completion.
2. `readAll` khởi động read callback; nếu chỉ nhận một phần thì tiếp tục ở offset kế tiếp. EOF sớm hoặc lỗi làm CompletableFuture kết thúc bằng exception.
3. `read.get(5,SECONDS)` giúp thread chính đợi kết quả callback thực tế. Dùng `Thread.sleep(...)` để đoán callback đã chạy hay chưa sẽ không đáng tin.
4. `isOpen()` là true trước khi rời try-with-resources. Sau khi `close()` được gọi tự động, `isOpen()` là false.
5. Executor do chương trình sở hữu được shutdown sau khi channel đóng, rồi file tạm được xóa.

Nếu có timeout ở bước đợi callback, try-with-resources sẽ đóng channel trước cleanup; điều này **không đồng nghĩa rollback** một operation đã chuyển byte. Ví dụ có đường thành công ổn định thay vì cố tạo race bằng sleep.

## 12. Mẫu Future timeout và interrupt có trách nhiệm

~~~java
// Minh họa: file là AsynchronousFileChannel,
// dst thuộc riêng operation này.
Future<Integer> op = file.read(dst, 0L);

try {
    int n = op.get(200, TimeUnit.MILLISECONDS);
    consume(n);
} catch (TimeoutException e) {
    op.cancel(false);
    // Không dùng lại dst ngay.
    // Owner quyết định đóng/cô lập channel hoặc cleanup.
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    op.cancel(false);
} catch (ExecutionException e) {
    logFailure(e.getCause());
}
~~~

`consume` và `logFailure` là hàm ứng dụng định nghĩa. Nếu `file` là channel **dùng chung cho nhiều request**, không đóng toàn bộ channel chỉ vì một request timeout trừ khi có chính sách tác động tới mọi request liên quan. Cần quản lý lifecycle và ownership rõ ràng từ đầu.

## 13. Mẫu timeout socket với CompletionHandler

~~~java
// Minh họa: socket đã kết nối; RequestContext do ứng dụng tạo.
socket.read(dst, 500, TimeUnit.MILLISECONDS,
        context,
        new CompletionHandler<Integer, RequestContext>() {
            @Override
            public void completed(Integer n, RequestContext ctx) {
                if (n < 0) {
                    ctx.peerClosed();
                } else {
                    ctx.readCompleted(n);
                }
            }

            @Override
            public void failed(Throwable error, RequestContext ctx) {
                ctx.failed(error);
                // Với InterruptedByTimeoutException:
                // không dựa vào trạng thái cũ của buffer/channel.
            }
        });
~~~

Đoạn code này không độc lập; `RequestContext` và `ctx.*` là cấu trúc do ứng dụng định nghĩa. Callback cần đảm bảo request chỉ được báo hoàn tất/thất bại **một lần**, và shutdown không chạy đua với callback để dọn cùng buffer hai lần.

## 14. Thread pool, AsynchronousChannelGroup và shutdown

`AsynchronousFileChannel.open(...)` có thể nhận một `ExecutorService`. Các asynchronous socket channels có thể dùng `AsynchronousChannelGroup` để xử lý callback và tài nguyên I/O liên quan.

| API | Ý nghĩa |
|---|---|
| `ExecutorService.shutdown()` | Ngừng nhận task mới; task đã nhận có thể tiếp tục |
| `ExecutorService.shutdownNow()` | Cố ngắt các task đang thực thi, không đảm bảo dừng ngay |
| `ExecutorService.awaitTermination(...)` | Đợi có timeout cho pool kết thúc |
| `AsynchronousChannelGroup.shutdown()` | Bắt đầu graceful shutdown theo hợp đồng |
| `AsynchronousChannelGroup.shutdownNow()` | Cố shutdown ngay, đóng các channel thuộc group |
| `AsynchronousChannelGroup.awaitTermination(...)` | Đợi group chấm dứt |

Không block callback quá lâu bằng `Future.get()` cho I/O khác cũng phụ thuộc cùng pool nhỏ: dễ gây **thread starvation**. Chỉ thành phần **sở hữu** group/pool mới được shutdown, tránh phá các channel dùng chung.

## 15. Checklist ứng dụng production

1. Mỗi request có state rõ ràng: CREATED → PENDING → COMPLETED/FAILED/CANCELLED → CLEANED.
2. Chỉ dùng ByteBuffer sau khi operation hoàn tất và ownership được trả lại một cách hợp lệ.
3. Luôn xử lý partial read/write, EOF và số byte thực tế.
4. Với Future, lấy lỗi gốc từ `ExecutionException.getCause()`; với handler, cài `failed(...)`.
5. Phân biệt timeout người đợi, timeout I/O socket và deadline nghiệp vụ tổng thể.
6. Khi bắt `InterruptedException`, truyền lên hoặc khôi phục interrupt flag.
7. Cancel không phải rollback; retry có version/idempotency khi có side effect.
8. Với socket, giữ tối đa một read và một write cùng chiều pending theo hợp đồng.
9. Ngừng nhận request mới trước shutdown; đóng channel và xử lý mọi pending callback/future.
10. Không shutdown executor hoặc group dùng chung bởi một request riêng lẻ.
11. Log request ID, operation, offset/peer, timeout, lỗi gốc và trạng thái cleanup.
12. Test EOF, partial I/O, timeout, interrupt, cancel, close khi pending và race lúc shutdown.

## 16. Những lỗi tư duy thường gặp

| Ngộ nhận | Sự thật |
|---|---|
| “Async method trả về tức là I/O xong” | Đợi Future hoặc callback. |
| “`Future.get(timeout)` timeout là I/O dừng” | Chỉ waiter hết hạn; nền có thể tiếp tục. |
| “Ngắt thread đợi là rollback operation” | Không rollback. |
| “`cancel(true)` chắc chắn chưa ghi byte nào” | Không có bảo đảm đó. |
| “Socket timeout giữ buffer nguyên vẹn” | Trạng thái buffer không xác định. |
| “Sau socket timeout luôn retry cùng channel” | Channel có thể ở trạng thái lỗi. |
| “Callback luôn ở thread caller” | Callback liên quan pool; có điều kiện chạy trực tiếp. |
| “Chỉ bắt exception lúc submit là đủ” | Phải xử lý lỗi hoàn tất qua Future/handler. |
| “Close channel undo dữ liệu” | Close không phải transaction rollback. |
| “File async write hoàn tất theo thứ tự submit” | Thứ tự khi concurrent không được hứa. |
| “Async socket cho nhiều read pending” | Có thể ném `ReadPendingException`. |
| “Buffer có thể clear trước khi callback” | Không an toàn khi pending. |
| “Java 17 ExecutorService tự close bằng try-with-resources” | Không; shutdown tường minh. |

## 17. Tự kiểm tra

1. `get(100, MILLISECONDS)` ném `TimeoutException`, I/O còn chạy không? **Có thể.**
2. `InterruptedException` có xác nhận zero bytes transferred không? **Không.**
3. Future thất bại có thể tìm nguyên nhân bằng gì? **`ExecutionException.getCause()`.**
4. Callback báo thất bại qua đâu? **`CompletionHandler.failed(...)`.**
5. Socket I/O timeout trực tiếp báo exception gì? **`InterruptedByTimeoutException`.**
6. Trạng thái buffer sau socket I/O timeout có chắc ổn không? **Không xác định.**
7. Cancel Future có transaction rollback không? **Không.**
8. `AsynchronousFileChannel` có file pointer chung không? **Không; offset cho từng operation.**
9. Đóng async file lúc pending read/write dẫn tới lỗi nào? **`AsynchronousCloseException`.**
10. Submit hai pending reads trên một socket async? **Có thể `ReadPendingException`.**
11. Completion báo 2 byte nhưng cần 8 byte có xong chưa? **Chưa.**
12. Có nên một request tự shutdown shared executor không? **Không.**
13. Timeout một request đã gửi từ xa có thể retry mù không? **Không; cần kiểm tra/idempotency.**

## 18. Tài liệu chính thức Java SE 17

- [AsynchronousFileChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/AsynchronousFileChannel.html): Future, CompletionHandler, close, offsets và ordering.
- [AsynchronousSocketChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/AsynchronousSocketChannel.html): timeout I/O, pending limits và trạng thái buffer.
- [CompletionHandler](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/CompletionHandler.html): completed/failed.
- [Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html): get, get(timeout), cancel.
- [AsynchronousChannelGroup](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/AsynchronousChannelGroup.html): lifecycle và shutdown.
- [AsynchronousCloseException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/AsynchronousCloseException.html): pending operation và close.
- [InterruptedByTimeoutException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/InterruptedByTimeoutException.html): socket I/O timeout.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="asynchronous-io-009">Giới hạn số thao tác đồng thời và áp lực lên tài nguyên</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn số thao tác đồng thời và áp lực lên tài nguyên trong Java NIO I/O bất đồng bộ

## 1. Vì sao async I/O vẫn cần giới hạn?

I/O bất đồng bộ (asynchronous I/O) trong `java.nio.channels` cho phép khởi tạo thao tác đọc/ghi rồi nhận kết quả sau thông qua `Future` hoặc `CompletionHandler`. Luồng gửi yêu cầu không nhất thiết phải đợi thao tác I/O hoàn thành.

Tuy nhiên, **không chặn luồng gọi không có nghĩa thao tác miễn phí tài nguyên**. Nếu ứng dụng liên tục phát động yêu cầu mới mà không kiểm soát tốc độ, hệ thống có thể phải giữ rất nhiều buffer, callback, socket, file descriptor, dữ liệu đang chờ xử lý và công việc trong queue. Khi quá tải, độ trễ tăng, bộ nhớ cạn, lỗi timeout xuất hiện và cả những request bình thường cũng bị ảnh hưởng.

Ví dụ: 10.000 thao tác đọc, mỗi thao tác sở hữu `ByteBuffer` 1 MiB, tạo ra **xấp xỉ 10.000 MiB (gần 9,8 GiB)** dung lượng vùng byte ở mức lý thuyết, chưa tính object, queue, native memory, cache và các tài nguyên khác. Việc dùng `AsynchronousFileChannel` không tự giải quyết bài toán này.

Bài học thuộc **Java Core I/O → I/O bất đồng bộ**. Trọng tâm là xác định chính xác “đồng thời” đang đo cái gì, giới hạn in-flight operations, chọn executor/channel group, backpressure, quyền sở hữu `ByteBuffer`, xử lý hoàn thành/thất bại, đóng tài nguyên và vận hành an toàn.

## 2. Ba con số không được đánh đồng

Các hệ thống async thường có nhiều mức giới hạn riêng:

| Chỉ số | Định nghĩa | Hậu quả nếu quá lớn |
| --- | --- | --- |
| **In-flight operations** | Số I/O đã phát động nhưng chưa hoàn tất | Giữ buffer, kernel resources, callbacks; có thể cạn tài nguyên |
| **Worker/completion threads** | Số luồng dùng để thực thi hoặc phát callback | Context switching/CPU và nguy cơ callback chiếm hết luồng |
| **Queued work / pending bytes** | Số request hoặc số byte đang xếp hàng, chưa được phát I/O hoặc chưa gửi hết | Tích lũy bộ nhớ, tăng latency, mất kiểm soát áp lực |

Ví dụ `ExecutorService` có 4 thread **không tự chứng minh** chỉ có 4 I/O đang chờ. Một thread có thể khởi tạo nhiều thao tác async và trả về ngay; khi đó số thao tác đang chờ có thể cao hơn nhiều so với số worker. Ngược lại, giới hạn `in-flight=4` không có nghĩa chương trình luôn có đúng 4 thread vật lý hoặc chạy nhanh gấp 4 lần.

Nên đo riêng `activeIo`, `pendingRequests`, `pendingBytes`, `callbackDuration`, `requestLatency`, số open channels, lỗi/timeout và tỷ lệ từ chối.

## 3. Những API Java NIO liên quan

### `AsynchronousFileChannel`

`AsynchronousFileChannel.open(path, options, executor, attrs...)` mở file channel có thể đọc/ghi theo vị trí tuyệt đối `long`. `read(dst, position, attachment, handler)` khởi tạo một thao tác rồi callback được gọi với số byte đã đọc hoặc ngoại lệ. Một channel có thể có **nhiều thao tác đọc/ghi đồng thời**, nhưng thứ tự hoàn thành **không được bảo đảm giống thứ tự phát động**.

Tùy provider, async file I/O có thể được triển khai thông qua luồng trong executor chứ không nhất thiết bằng một giao diện kernel asynchronous thuần túy. Số luồng executor và số thao tác đã gửi vẫn là hai giới hạn khác nhau.

### `AsynchronousSocketChannel`

`AsynchronousSocketChannel` cho phép **tối đa một thao tác read đang chờ và một thao tác write đang chờ trên cùng channel tại một thời điểm**. Một read và một write có thể đồng thời tồn tại, nhưng gọi read thứ hai trước khi read trước hoàn tất sẽ ném `ReadPendingException`; tương tự write gây `WritePendingException`.

Điều này chỉ là giới hạn **trên mỗi socket**, không phải giới hạn toàn dịch vụ: 20.000 socket có thể đồng thời có tới 20.000 read pending, và còn các write cùng tồn tại. Cần cả chính sách quota trên connection và toàn ứng dụng.

### `AsynchronousChannelGroup`

Các asynchronous socket/server channels có thể gắn với một `AsynchronousChannelGroup`, liên kết với thread pool dùng để điều phối completion. Quản lý group giúp ứng dụng điều khiển lifecycle và tài nguyên phục vụ nhiều channel.

Đừng đồng nhất `AsynchronousChannelGroup` với `AsynchronousFileChannel.open(..., ExecutorService)`: file channel có thể nhận executor trực tiếp; socket channels dùng channel group. Cũng không mặc định rằng cấu hình `fixedThreadPool(8)` sẽ tự tạo một **bounded application request queue**.

### `Future` và `CompletionHandler`

Với `Future<Integer>`, lời gọi `future.get()` trong luồng phát động sẽ **chặn chính luồng đó** cho đến khi hoàn thành. Với `CompletionHandler`, luồng khởi tạo thường không phải chờ `get`, nhưng callback vẫn phải chạy trên một luồng và vẫn tiêu tốn CPU. Nếu callback chứa logic chặn lâu, đọc database đồng bộ hoặc xử lý CPU nặng, throughput có thể giảm mạnh.

## 4. Vòng đời một thao tác async và lúc giữ tài nguyên

```text
Yêu cầu đến
     |
     v
Kiểm tra quota + lấy permit + cấp buffer
     |
     v
Phát động I/O async (IN-FLIGHT)
     |
     +---- hoàn thành thành công --> xử lý kết quả / chuyển sang phase tiếp
     |
     +---- thất bại / timeout --> xử lý lỗi / đóng hoặc retry theo policy
     |
     v
Giải phóng permit và buffer khi thao tác thực sự kết thúc
     |
     v
Nhận yêu cầu tiếp theo từ hàng đợi có giới hạn
```

**Permit** là “quyền” cho phép giữ một slot I/O đang chạy. Nếu giới hạn 32, số thao tác được theo dõi bởi limiter không vượt 32. Cần giữ permit **suốt vòng đời** của một yêu cầu nếu yêu cầu ấy còn phát sinh nhiều lượt đọc lại để hoàn tất payload; chỉ trả permit khi hoạt động logic được coi là xong hoặc đã xử lý hủy theo hợp đồng của provider.

Đặt `release()` quá sớm ngay sau lời gọi `channel.read(...)` là sai: lời gọi chỉ **khởi tạo** async read, còn buffer và channel có thể đang được sử dụng. Cấp lại permit lúc ấy cho phép các yêu cầu mới tràn vào dù yêu cầu trước chưa hoàn tất.

## 5. Tính ngân sách bộ nhớ trước khi chọn giới hạn

Nếu một request giữ một buffer tối đa `B` byte và tối đa `N` thao tác in-flight, phần bộ nhớ cho các buffer vào khoảng:

```text
memoryForBuffers ≈ N × B

Tổng ngân sách ≈ memoryForBuffers
               + pending application payloads
               + outbound queues
               + connection state
               + executor queue / task objects
               + runtime, TLS, native, file-system resources
```

Ví dụ:

| Max in-flight | Buffer/request | Tổng dung lượng vùng byte, chưa tính overhead |
| ---: | ---: | ---: |
| 16 | 64 KiB | 1 MiB |
| 128 | 64 KiB | 8 MiB |
| 512 | 256 KiB | 128 MiB |
| 10.000 | 1 MiB | ≈ 9,77 GiB |

`ByteBuffer.allocate()` thông thường sử dụng heap; `ByteBuffer.allocateDirect()` sử dụng direct/native memory với các giới hạn và đặc điểm GC khác. **Direct buffer không đồng nghĩa không chiếm RAM.** Số lượng direct buffer lớn có thể gây `OutOfMemoryError: Direct buffer memory` hoặc áp lực native memory ngay cả khi heap còn chỗ.

Nếu ứng dụng giữ buffer trong một object pool, bộ nhớ có thể vẫn được giữ sau khi I/O kết thúc. Pool giảm chi phí cấp phát lặp lại, nhưng pool **không giới hạn** hoặc buffer quá cỡ có thể làm tình hình tệ hơn.

Ngân sách còn phụ thuộc loại buffer, độ dài thông điệp, số kết nối, số byte outbound chưa gửi và tốc độ người dùng xử lý kết quả. Quota theo **số tác vụ** thường cần phối hợp với quota theo **số byte**.

## 6. Dùng `Semaphore` để giới hạn I/O đang bay

`java.util.concurrent.Semaphore` là cách trực tiếp để biểu diễn số slot còn lại:

```java
Semaphore permits = new Semaphore(32);

// Ở vị trí nhận request có thể chờ:
permits.acquire();
try {
    // Cấp tài nguyên và bắt đầu async operation.
    // Sau khi phát động, KHÔNG release ngay tại đây.
} catch (RuntimeException e) {
    // Nếu chưa có completion callback nào sẽ nhận trách nhiệm cleanup,
    // giải phóng permit đã lấy.
    permits.release();
    throw e;
}

// CompletionHandler ở nhánh kết thúc:
try {
    // xử lý success hoặc failed
} finally {
    permits.release();
}
```

Đây là **các đoạn minh họa đặt trong nhiều vị trí**, không phải một hàm Java chạy độc lập. Ở production phải tổ chức trạng thái để bảo đảm **mỗi permit trả đúng một lần**, kể cả callback thất bại, khởi tạo thất bại, cancellation và shutdown. Nếu một thao tác có thể callback thành công/thất bại ngay tại luồng khởi tạo, tránh các race giữa nhánh submit và callback.

Dùng `tryAcquire()` để không chặn thread điều phối khi hết slot:

```java
if (!permits.tryAcquire()) {
    // Từ chối, trả tín hiệu "busy" hoặc xếp vào bounded queue.
    // Không âm thầm tích lũy hàng triệu request.
    return;
}
```

Trong một event loop, tránh `Semaphore.acquire()` vì **chặn thread xử lý sự kiện**; dùng `tryAcquire()`, hàng đợi giới hạn hoặc cơ chế cấp quota bất đồng bộ. Trong batch job có luồng producer riêng, `acquire()` có thể là backpressure đơn giản và hợp lệ.

### 6.1. Chọn chính sách khi hết permit

- **Wait**: producer chờ, thích hợp với batch job hoặc luồng ngoài event loop.
- **Bounded queue**: chờ có thứ tự tới một giới hạn rõ ràng; queue đầy thì từ chối hoặc chờ ở tầng ngoài.
- **Fail fast**: trả lỗi bận, ví dụ HTTP 429/503 (nếu ứng dụng là server phù hợp).
- **Load shedding**: bỏ yêu cầu ưu tiên thấp hoặc kết nối đang quá chậm theo policy rõ ràng.
- **Adaptive limit**: thay đổi số slot dựa trên latency, CPU, bộ nhớ và sai số đo, cần đo kiểm để tránh dao động.

Không dùng `Executors.newFixedThreadPool(k)` như một “giải pháp quota tổng quát” mà quên rằng `newFixedThreadPool` mặc định đi kèm **hàng đợi không giới hạn** cho các tác vụ đã nộp vào pool.

## 7. Ví dụ Java 17+ chạy được: đọc file async với tối đa hai job đồng thời

Chương trình dùng `AsynchronousFileChannel`, `CompletionHandler`, `Semaphore(2)`, `CountDownLatch` và một executor hai luồng. File tạm chứa `ABCDEFGHIJKL` được chia thành ba vùng 4 byte; mọi vùng được đọc bất đồng bộ, nhưng **tối đa hai job logic in-flight**.

Chương trình **không giả định thứ tự callback**: mỗi job lưu kết quả vào vị trí riêng, chỉ in kết quả sau khi tất cả hoàn tất. Nếu một lần đọc trả ít hơn 4 byte, cùng job tiếp tục đọc phần còn thiếu ở offset tiếp theo trong khi giữ permit.

Tạo file `AsyncIoLimitDemo.java`:

```java
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncIoLimitDemo {
    private static final int BLOCK_SIZE = 4;
    private static final int MAX_IN_FLIGHT = 2;

    private static class ReadJob implements CompletionHandler<Integer, Void> {
        private final AsynchronousFileChannel channel;
        private final ByteBuffer buffer = ByteBuffer.allocate(BLOCK_SIZE);
        private final long fileOffset;
        private final int index;
        private final String[] results;
        private final Throwable[] errors;
        private final Semaphore permits;
        private final CountDownLatch done;
        private final AtomicBoolean settled = new AtomicBoolean();

        ReadJob(AsynchronousFileChannel channel, long fileOffset, int index,
                String[] results, Throwable[] errors,
                Semaphore permits, CountDownLatch done) {
            this.channel = channel;
            this.fileOffset = fileOffset;
            this.index = index;
            this.results = results;
            this.errors = errors;
            this.permits = permits;
            this.done = done;
        }

        void submit() {
            try {
                long nextPosition = fileOffset + buffer.position();
                channel.read(buffer, nextPosition, null, this);
            } catch (RuntimeException ex) {
                finish(null, ex);
            }
        }

        @Override
        public void completed(Integer n, Void ignored) {
            if (n == null || n < 0) {
                finish(null, new EOFException("Unexpected EOF"));
                return;
            }
            if (n == 0) {
                // Đoạn demo chọn báo lỗi để tránh vòng lặp retry liên tục.
                finish(null, new IOException("Read made no progress"));
                return;
            }
            if (buffer.hasRemaining()) {
                submit(); // Vẫn giữ cùng permit cho tới khi đủ BLOCK_SIZE.
                return;
            }

            buffer.flip();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            finish(new String(bytes, StandardCharsets.US_ASCII), null);
        }

        @Override
        public void failed(Throwable error, Void ignored) {
            finish(null, error);
        }

        private void finish(String value, Throwable error) {
            if (!settled.compareAndSet(false, true)) {
                return; // bảo vệ cleanup nếu ứng dụng gọi finish lặp lại
            }
            results[index] = value;
            errors[index] = error;
            permits.release(); // Một permit cho mỗi job logic.
            done.countDown();
        }
    }

    public static void main(String[] args) throws Exception {
        Path file = Files.createTempFile("async-io-limits-", ".dat");
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Files.writeString(
                    file, "ABCDEFGHIJKL", StandardCharsets.US_ASCII);

            try (AsynchronousFileChannel channel =
                    AsynchronousFileChannel.open(
                            file, Set.of(StandardOpenOption.READ), pool)) {

                Semaphore permits = new Semaphore(MAX_IN_FLIGHT);
                CountDownLatch done = new CountDownLatch(3);
                String[] results = new String[3];
                Throwable[] errors = new Throwable[3];

                for (int i = 0; i < 3; i++) {
                    // Đây là batch producer ở main, không phải event loop.
                    permits.acquire();
                    new ReadJob(channel, i * (long) BLOCK_SIZE, i,
                            results, errors, permits, done).submit();
                }

                done.await();
                for (int i = 0; i < results.length; i++) {
                    if (errors[i] != null) {
                        throw new IOException("Job " + i + " failed", errors[i]);
                    }
                    System.out.println("chunk " + i + ": " + results[i]);
                }
                System.out.println("in-flight limit: " + MAX_IN_FLIGHT);
            }
        } finally {
            pool.shutdown();
            Files.deleteIfExists(file);
        }
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac AsyncIoLimitDemo.java
java AsyncIoLimitDemo
```

Stdout khi file tạm tạo thành công và hệ thống tệp hoạt động bình thường:

```text
chunk 0: ABCD
chunk 1: EFGH
chunk 2: IJKL
in-flight limit: 2
```

**Tại sao stdout ổn định?** Các completion callback có thể đến theo bất kỳ thứ tự nào, nhưng chúng ghi vào `results[index]`; luồng main đợi `done.await()` rồi mới in theo index. `CountDownLatch` cũng thiết lập quan hệ happens-before cho cập nhật trước `countDown()`. Chương trình không in thứ tự callback hoặc một “số thao tác đỉnh” có thể thay đổi tùy lịch trình.

**Giải thích giới hạn:**

1. `MAX_IN_FLIGHT=2` tạo hai permit. Job 0 và 1 có thể bắt đầu; producer chỉ phát động job 2 khi một completion đã trả permit.
2. Mỗi job có `ByteBuffer` riêng, giữ tới khi job hoàn thành. Nếu `read` chỉ chuyển một phần, `submit()` lại với offset đã tăng từ `buffer.position()` nhưng **không lấy permit mới**.
3. Nhánh `completed`, `failed` và lỗi khởi tạo đồng bộ đều đi qua `finish`; `AtomicBoolean` tránh trả permit nhiều lần.
4. `done.await()` đảm bảo các job đã báo hoàn tất trước khi chương trình đọc kết quả và đóng channel.
5. `pool.shutdown()` báo dừng executor, còn `try-with-resources` đóng channel; file tạm được dọn trong `finally`.

Với file thông thường và ba vùng 4 byte tồn tại, kết quả trên là đầu ra dự kiến. Nếu provider gặp lỗi I/O hoặc trả `0` khi buffer còn chỗ, bản demo chủ động coi là lỗi “no progress” thay vì tự retry vô hạn; kết quả là ngoại lệ thay vì stdout hoàn chỉnh.

**Giới hạn demo:** `Semaphore.acquire()` chặn **main batch producer**, điều này chủ ý để minh họa backpressure; không đưa nguyên mẫu vào thread xử lý sự kiện. Nếu producer bị interrupt hoặc phát sinh lỗi toàn cục trong khi vẫn còn I/O, application thực tế cần phối hợp dừng nhận công việc, hoàn thành/hủy những job còn lại, sau đó mới đóng tài nguyên. Demo dùng file cố định, không bao gồm queue đợi, retry policy, deadline và metrics.

## 8. Một socket không được có nhiều read pending

Với `AsynchronousSocketChannel`, mã sau sai nếu read đầu chưa hoàn tất:

```java
ByteBuffer first = ByteBuffer.allocate(1024);
ByteBuffer second = ByteBuffer.allocate(1024);

socket.read(first);  // Trả Future<Integer>
socket.read(second); // Có thể ném ReadPendingException
```

Đây là snippet có biến `socket` trong một phương thức, không phải chương trình độc lập. Tương tự, khi một write đang pending mà khởi tạo write thứ hai, có thể ném `WritePendingException`.

Mẫu đúng là **chain read tuần tự cho một socket**:

```text
start read #1
     |
     +-- completed(n > 0): parse dữ liệu, kiểm tra quota/buffer
     |                        |
     |                        +-- cần nhận tiếp: start read #2
     |
     +-- completed(-1): EOF, kết thúc input
     +-- failed(error): log/close/cleanup
```

Có thể đồng thời có **một read và một write** trên một socket. Đọc và ghi độc lập nhưng chia sẻ connection state nên phải kiểm soát việc đóng, timeout và đồng bộ trạng thái.

Đừng tạo một luồng mới cho mỗi socket chỉ để gọi `future.get()`. Với hàng nghìn socket, điều đó đánh mất nhiều lợi ích của async I/O. Callback nhanh và lập lịch hợp lý giúp phục vụ nhiều kết nối bằng một nhóm luồng có giới hạn.

## 9. ByteBuffer có quyền sở hữu trong suốt thao tác

Khi `channel.read(buffer, ...)` hoặc `channel.write(buffer, ...)` đang diễn ra, **không được tùy tiện đọc/ghi hoặc thay đổi `position`/`limit` của buffer ấy từ thread khác**. Tài liệu API nhấn mạnh `ByteBuffer` không thread-safe và phải tránh truy cập trong khi thao tác async còn pending.

Sai:

```java
Future<Integer> pending = channel.read(buffer);
buffer.clear(); // Sai: I/O có thể vẫn đang ghi vào buffer
```

Đây là snippet minh họa `AsynchronousByteChannel`, không phải `AsynchronousFileChannel` vì chữ ký đọc của file channel phải có file position. Khi chưa có completion, `clear()` có thể làm thay đổi trạng thái mà I/O đang phụ thuộc.

Cách thiết kế:

- Mỗi thao tác pending có một buffer mà nó sở hữu, hoặc một buffer pool cấp **độc quyền** tới khi completion.
- Chỉ tái sử dụng buffer sau khi I/O đã thực sự kết thúc và consumer không còn dùng dữ liệu.
- Với `slice()`/`duplicate()`, các view có thể chia sẻ storage dù có `position` riêng: không cấp đồng thời cho hai thao tác sửa cùng vùng nhớ.
- Khi partial write, buffer phải tiếp tục từ `position` hiện tại; `clear()` sớm sẽ làm mất trạng thái dữ liệu chưa gửi.
- Khi partial read của một frame, giữ state qua các completion; `read()==0` không phải EOF, `read()==-1` mới báo hết input.
- Nếu timeout khiến buffer có trạng thái chưa xác định theo hợp đồng của `AsynchronousSocketChannel`, không tái sử dụng buffer dựa trên giả định “không byte nào đã được truyền”.

Chỉ dùng `ByteBuffer.allocateDirect` khi profile thực tế cho thấy có lợi; direct buffers cũng cần quota riêng và chính sách cleanup không dựa vào `clear()` (vì `clear()` không xóa dữ liệu và không giải phóng memory).

## 10. CompletionHandler phải ngắn và luôn kết thúc tài nguyên

Callback có thể được gọi bởi thread thuộc pool xử lý async. Nếu callback thực hiện parsing rất nặng, gọi database blocking hoặc chờ một permit khác, callback có thể làm nghẽn completion của các I/O khác.

Mẫu khuyến nghị:

```text
I/O completion callback
  1) Xác nhận kết quả và trạng thái
  2) Chuyển dữ liệu/công việc tới bounded processing queue nếu cần
  3) Kết thúc quyền sở hữu I/O và giải phóng permit đúng thời điểm
  4) Lập lịch I/O tiếp theo khi có quota; tránh blocking lâu
```

Tuy nhiên, nếu queue downstream không có chỗ, **không release permit rồi tiếp tục nhận vô hạn** khi dữ liệu ứng dụng chưa được tiêu thụ. Backpressure phải xuyên suốt pipeline: khi consumer chậm, giới hạn byte đang giữ trong queue hoặc tạm dừng phát read mới trên socket.

### 10.1. Tránh “callback starvation”

Giả sử group có 4 thread và cả 4 callback đều gọi `semaphore.acquire()` để đợi slot do một callback khác sẽ `release()`. Nếu callback cần release đang chờ thread rảnh của cùng pool, ứng dụng có thể rơi vào starvation/deadlock ở mức thiết kế.

Giải pháp: **không blocking trong completion callback**. Nếu không có permit, ghi nhận trạng thái paused và nhờ scheduler hoặc queue có giới hạn tiếp tục sau khi slot được trả; nếu queue đã đầy, áp dụng policy từ chối/backpressure thay vì chờ vô hạn ngay trong pool.

### 10.2. Đảm bảo permit được trả khi failed

Mọi nhánh kết thúc logic phải xử lý:

- `completed(n)` thành công hoàn toàn.
- `completed(n)` một phần, có thể cần retry cùng job.
- `completed(-1)` EOF.
- `failed(exc)` lỗi I/O, timeout hoặc close.
- Lỗi đồng bộ khi bắt đầu I/O trước khi callback được đăng ký thành công.
- Shutdown/cancel theo quy tắc của provider.

Thiết kế một đối tượng quản lý job với trạng thái `AtomicBoolean settled` là cách giảm lỗi double-release. Với job nhiều bước, release khi **job logic** kết thúc; nếu chỉ quan tâm mỗi thao tác vật lý, cần định nghĩa rõ đơn vị quota để tránh sai số đếm.

## 11. Backpressure ở cả inbound và outbound

### Inbound: consumer chậm

Client gửi message nhanh hơn tầng parser/database xử lý. Nếu mỗi completion lập tức đọc tiếp và giữ message trong queue không giới hạn, heap tăng dù số read pending trên mỗi socket không quá một.

Các biện pháp:

1. Giới hạn số request in-flight.
2. Giới hạn **tổng byte** trong inbound queue, không chỉ số lượng frame.
3. Khi quá tải, tạm không phát read tiếp trên connection có backlog lớn, hoặc từ chối theo protocol.
4. Tiếp tục đọc khi queue đã giảm xuống dưới ngưỡng thấp (low watermark).
5. Có deadline/timeout để tránh kết nối chiếm một slot mãi.

### Outbound: peer nhận chậm

Một `AsynchronousSocketChannel.write` có thể hoàn tất với ít byte hơn `src.remaining()` ban đầu. Nhiều response có thể tích lũy trong outbound queue; cần giữ thứ tự và chỉ có **một write pending trên mỗi socket**.

```text
outbound queue: [Frame A còn dư, Frame B, Frame C]
      |
      v
write(Frame A) -> completion
    nếu còn bytes: write tiếp Frame A
    nếu hết Frame A: lấy Frame B
      |
      v
queue rỗng -> chờ message mới
```

Nếu application enqueue response nhanh hơn socket gửi, quota **outboundBytes** quan trọng hơn quota số frame. Có thể ngừng xử lý request mới, áp dụng flow control hoặc đóng các connection quá chậm theo chính sách.

Đối với async socket, đừng thử gọi `write` thứ hai khi write trước còn pending: ngoài lỗi `WritePendingException`, các buffer có thể bị quản lý sai thứ tự.

## 12. Thread pool, channel group và hàng đợi

Không chọn số worker bằng công thức “càng nhiều càng nhanh”. Tác vụ CPU-bound và I/O-bound có đặc tính khác nhau; một provider có thể dùng thread để thực hiện file I/O, một provider khác có mô hình riêng.

- Dùng thread pool phù hợp với loại workload và profile.
- Nếu callback nhẹ, một số luồng tương đối nhỏ có thể phục vụ nhiều completions.
- Nếu callback có tính toán CPU dài, chuyển chúng sang **bounded worker queue** riêng để tránh chiếm completion threads.
- Không tạo channel group hoặc executor mới cho từng request.
- Quản lý lifecycle của group/pool theo toàn dịch vụ; shutdown có trật tự, bảo đảm không nộp job mới sau khi ngừng nhận.
- `AsynchronousChannelGroup.shutdown()` và `shutdownNow()` có ngữ nghĩa khác nhau; đóng channels và chờ termination theo API khi cần. Tránh dựa vào daemon thread tự kết thúc.
- Default group/thread pool của JDK có hành vi cấu hình theo hệ thống; ứng dụng có nhu cầu tài nguyên rõ ràng nên chỉ định và đo cấu hình phù hợp.

**Điểm hay nhầm:** một executor dùng 8 thread không có nghĩa chỉ có tối đa 8 outstanding socket reads; số luồng completion cũng không phải giới hạn tổng số kết nối.

## 13. Cancellation, timeout và đóng channel

Với `Future.cancel(true)`, không nên suy luận rằng I/O đã dừng hoàn toàn và buffer ngay lập tức an toàn để sửa từ thread khác. Hành vi cancel phụ thuộc channel/provider, và cancellation có thể dẫn tới đóng channel hoặc hoàn thành với ngoại lệ.

Một số overload của `AsynchronousSocketChannel.read/write` có timeout; khi timeout xảy ra, buffer hoặc channel có thể có trạng thái phụ thuộc provider. Tài liệu API cảnh báo rằng một timeout có thể khiến kênh rơi vào trạng thái lỗi, vì hệ thống không thể xác định chắc chắn dữ liệu đã được chuyển hay chưa.

Quy trình cleanup cần phân biệt:

1. **Ngừng nhận yêu cầu mới** và dừng phát động I/O mới.
2. **Cho phép các thao tác đang có hoàn thành** trong một deadline hợp lý, hoặc áp dụng chính sách hủy.
3. **Kết thúc/đóng channel** khi đã quyết định shutdown.
4. **Bảo đảm completion/failure path cập nhật trạng thái và giải phóng permit**; không double-release.
5. **Giải phóng buffer và request state** khi chắc chắn không còn thao tác truy cập tới vùng nhớ đó.
6. Đóng channel group/executor thuộc quyền sở hữu của component; không đóng pool được thành phần khác chia sẻ một cách tùy tiện.

Với sự cố “thiếu byte” hoặc partial write, retry cả request có thể gây duplicate ở tầng ứng dụng. Nếu dữ liệu đã được gửi một phần rồi timeout, cần idempotency key, sequence number hoặc protocol xác nhận phù hợp; chỉ retry khi nghiệp vụ cho phép.

## 14. Quan sát và điều chỉnh giới hạn bằng số đo

Một hệ thống async nên có ít nhất các số liệu:

| Metric | Công dụng |
| --- | --- |
| `io.inflight` | Số thao tác thực sự chưa hoàn tất |
| `io.maxInFlight` | Giới hạn cấu hình hiện tại |
| `io.pending.requests` | Kích thước bounded queue |
| `io.pending.bytes` | Bộ nhớ/chất lượng backpressure thực |
| `io.complete.latency` | Độ trễ phát động → hoàn thành |
| `io.callback.duration` | Callback có chiếm worker quá lâu không |
| `io.errors`, `io.timeouts` | Chẩn đoán thất bại và quá tải |
| `io.rejected` | Số yêu cầu bị từ chối do hết quota |
| `process.openFiles` / sockets | Áp lực file descriptors |
| Heap/direct memory/GC | Nhìn thấy áp lực cấp phát và vùng native |

Theo dõi `p95`/`p99` latency bên cạnh throughput. Nếu tăng `MAX_IN_FLIGHT` từ 32 lên 1.024 mà throughput hầu như không tăng, trong khi queue/memory/timeout tăng mạnh, đó là dấu hiệu ứng dụng đã quá ngưỡng có lợi.

### 14.1. Little's Law và giới hạn

Với hệ thống gần trạng thái ổn định, số yêu cầu đang ở trong hệ thống xấp xỉ:

```text
L = λ × W
```

Trong đó `L` là số yêu cầu trung bình đang tồn tại, `λ` là tốc độ đến/hoàn thành trung bình, `W` là thời gian trung bình một yêu cầu ở trong hệ thống (bao gồm thời gian chờ theo định nghĩa đo). Ví dụ 500 yêu cầu/giây và latency trung bình 0,2 giây tương ứng khoảng 100 yêu cầu đang tồn tại.

Công thức **không tự cho biết số permit tối ưu**. Nó giúp ước lượng nhu cầu ở tải thực tế và nhận biết khi độ trễ tăng khiến số yêu cầu đang chờ phình to. Chọn quota còn cần xét spike, file descriptor, memory, fairness, SLA và failure modes.

## 15. Lỗi triển khai phổ biến

| Sai lầm | Hậu quả | Cách sửa |
| --- | --- | --- |
| Phát động async read không có quota | Cạn buffer, quá tải kernel/worker | Giới hạn in-flight và byte budget |
| Tưởng 8 worker = tối đa 8 I/O | Hàng nghìn I/O vẫn có thể đang pending | Đo/giới hạn operations riêng |
| `release()` ngay sau `channel.read()` | Slot được tái sử dụng khi I/O chưa xong | Release ở completion/failure |
| Quên release trong `failed` | Rò permit, hệ thống dừng nhận công việc | Một cleanup path duy nhất |
| Callback gọi `Semaphore.acquire()` | Chặn pool, nguy cơ starvation | `tryAcquire()` hoặc scheduler có queue giới hạn |
| Tạo `ByteBuffer` rất lớn cho mỗi request | Heap/direct memory áp lực | Quota bộ nhớ và buffer phù hợp |
| Sửa/clear buffer khi Future chưa xong | Data race/corrupt byte | Quyền sở hữu buffer tới completion |
| Đọc hai lần cùng lúc trên async socket | `ReadPendingException` | Chain read sau completion |
| Ghi hai lần cùng lúc trên async socket | `WritePendingException` | Queue và serialize writes |
| Chỉ quota số item trong queue | Vài frame cực lớn vẫn làm cạn RAM | Quota theo tổng byte |
| `newFixedThreadPool` coi là queue có giới hạn | Queue task có thể tăng mãi | Tạo bounded work queue hoặc backpressure |
| Gọi `future.get()` trên thread event loop | Block event loop | CompletionHandler/callback hoặc mô hình chờ khác |
| Coi `cancel(true)` là buffer đã an toàn | Có thể vẫn còn I/O/state phụ thuộc provider | Điều phối cancel/close theo hợp đồng |
| Retry write sau timeout không xét duplicate | Gửi lặp dữ liệu/bản tin | Idempotency/sequence/acknowledgment |
| Mở executor/group cho mỗi request | Bùng nổ luồng/tài nguyên | Dùng nhóm được quản lý tập trung |

## 16. Câu hỏi tự kiểm tra

**1.** Có executor 4 thread. Có chắc chỉ tối đa 4 I/O pending?  
**Đáp án:** Không. Số thread và số I/O pending khác nhau; cần giới hạn riêng.

**2.** Phát động `read` async thành công rồi lập tức `permits.release()` có đúng không?  
**Đáp án:** Sai nếu permit đại diện cho in-flight operation; phải giữ tới completion/failure hoặc kết thúc job logic.

**3.** Có thể có hai read outstanding trên một `AsynchronousSocketChannel` không?  
**Đáp án:** Không; thao tác thứ hai ném `ReadPendingException`. Có thể có một read và một write đồng thời.

**4.** Một `AsynchronousFileChannel` có thể có nhiều read outstanding không?  
**Đáp án:** Có; API cho phép, nhưng kết quả có thể hoàn thành không theo thứ tự phát động.

**5.** Có 1.000 request với mỗi buffer 1 MiB, memory buffer xấp xỉ bao nhiêu?  
**Đáp án:** 1.000 MiB (gần 0,98 GiB), chưa tính overhead và queue khác.

**6.** Vì sao `ByteBuffer.clear()` không được gọi khi async read còn pending?  
**Đáp án:** Nó đổi `position/limit`; I/O đang dùng buffer có thể bị ảnh hưởng. Buffer không an toàn cho truy cập đồng thời khi chưa completion.

**7.** Callback chạy chậm nhưng in-flight limiter vẫn thấp. Hệ thống có thể quá tải không?  
**Đáp án:** Có. Completion pool và downstream queue/CPU cũng là nút thắt; limiter là một phần của chiến lược backpressure.

**8.** Chỉ giới hạn queue 100 message có đủ chống cạn bộ nhớ?  
**Đáp án:** Chưa chắc. Một message có thể lớn hàng chục MiB; cần giới hạn tổng byte.

**9.** `write` async hoàn thành với một phần buffer còn lại thì xử lý thế nào?  
**Đáp án:** Tiếp tục cùng buffer từ `position` còn lại khi write trước hoàn tất, không khởi tạo write chồng lấp.

**10.** `Semaphore.acquire()` trong event loop là thực hành phù hợp?  
**Đáp án:** Không. Nó có thể chặn thread; dùng `tryAcquire()` và điều phối queue/backpressure không blocking.

## 17. Checklist vận hành

1. Chọn đơn vị quota: **I/O vật lý**, **request logic**, **connection**, hoặc cả ba.
2. Tính ngân sách buffer theo số byte/operation và số slot; để dư cho heap, direct memory, TLS, outbound queues.
3. Giới hạn đồng thời cùng với bounded queue, timeout và chính sách overload.
4. Giữ permit tới completion hoặc tới khi job logic chấm dứt; cleanup đúng một lần.
5. Với asynchronous socket, duy trì tối đa một read và một write pending trên mỗi connection.
6. Quản lý quyền sở hữu `ByteBuffer` qua toàn bộ vòng đời tác vụ.
7. Không block completion handler; chuyển xử lý nặng qua bounded worker pool.
8. Kiểm soát backpressure cả inbound/outbound, nhất là khi peer gửi hoặc nhận chậm.
9. Định nghĩa shutdown/cancellation, tránh double-release và premature buffer reuse.
10. Quan sát latency p95/p99, in-flight, backlog bytes, lỗi timeout, rejection và open handles; điều chỉnh giới hạn bằng thử tải.

## Tài liệu chính thức

- Java SE 17 — `AsynchronousFileChannel`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/AsynchronousFileChannel.html
- Java SE 17 — `AsynchronousSocketChannel`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/AsynchronousSocketChannel.html
- Java SE 17 — `AsynchronousChannelGroup`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/AsynchronousChannelGroup.html
- Java SE 17 — `CompletionHandler`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/CompletionHandler.html
- Java SE 17 — `AsynchronousByteChannel`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/AsynchronousByteChannel.html
- Java SE 17 — `ByteBuffer`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html
- Java SE 17 — `Semaphore`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Semaphore.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="asynchronous-io-010">Lựa chọn blocking, selector hoặc API bất đồng bộ theo bài toán</a>

<details>
<summary>Xem chi tiết</summary>

# Lựa chọn blocking, selector hoặc API bất đồng bộ theo bài toán

## 1. Bài toán chọn mô hình I/O

Java cho phép triển khai cùng một thao tác đọc/ghi theo nhiều cách. Với một TCP client gửi `PING` và chờ `PONG`, ta có thể:

- Gọi `SocketChannel.read()`/`write()` trong **blocking mode**: luồng có thể đợi tại lời gọi.
- Dùng `SocketChannel.configureBlocking(false)` kết hợp **`Selector`**: luồng event loop đợi tín hiệu *readiness*, rồi chủ động gọi read/write.
- Dùng **`AsynchronousSocketChannel`**: khởi tạo thao tác, lấy kết quả qua `Future` hoặc `CompletionHandler` (*completion*), không nhất thiết để luồng gọi đợi.

Điểm khác nhau không nằm ở “API nào hiện đại hơn”, mà là **ai giữ trạng thái I/O trong khi chưa hoàn thành** và **luồng nào chịu chi phí chờ**.

~~~text
Blocking:
  thread -> connect -> write -> read[đợi] -> xử lý -> đóng

Selector / readiness:
  event loop -> select -> channel sẵn sàng -> thử read/write
             -> còn byte chưa xử lý? lưu state -> select lần sau

Asynchronous / completion:
  caller -> phát hành read/write -> tiếp tục việc khác
                                      |
                     Future hoặc CompletionHandler báo kết quả
~~~

**Chọn theo workload thực tế**: số kết nối đang mở, tỉ lệ chờ/CPU, throughput, tail latency, độ phức tạp cho phép, yêu cầu timeout/cancel và giới hạn RAM/file descriptor. Blocking, selector và async đều có thể đúng trong những hoàn cảnh khác nhau.

## 2. Ba khái niệm phải phân biệt

### 2.1. Blocking: lời gọi có thể chờ

~~~java
try (SocketChannel socket = SocketChannel.open(remote)) {
    ByteBuffer dst = ByteBuffer.allocate(1024);
    int n = socket.read(dst);
    // Luồng gọi có thể đợi ở read().
}
~~~

Đây là **mảnh minh họa**, cần `SocketChannel`, `ByteBuffer` và một `remote` hợp lệ; phải khai báo hoặc xử lý `IOException`. `SocketChannel.open(remote)` dùng kết nối TCP blocking theo mặc định.

Mã blocking có luồng điều khiển tuyến tính, dễ theo dõi bằng debugger và dễ quản lý tài nguyên bằng `try-with-resources`. Khi ứng dụng có nhiều platform threads bị chặn lâu trên mạng, chi phí stack, scheduler và giới hạn tài nguyên có thể quan trọng.

Blocking **không bảo đảm một lần `read` trả đủ buffer**. Channel có thể nhận được ít byte hơn ứng dụng cần; `write` cũng có thể tiêu thụ chưa hết `remaining()`. Cần vòng lặp theo giao thức ở mọi mô hình.

### 2.2. Selector: readiness, không phải completion

~~~java
socket.configureBlocking(false);
SelectionKey key = socket.register(selector, SelectionKey.OP_READ);
selector.select();
~~~

Đây là **mảnh minh họa**: `socket` phải được khởi tạo, `selector` đã mở. `SelectionKey.OP_READ` nghĩa là chương trình quan tâm đến lúc socket **có thể thử đọc**.

Khi `key.isReadable()`, ứng dụng gọi `socket.read(buffer)`. Lệnh có thể trả:

- `n > 0`: đã nhận `n` byte;
- `n == 0`: chưa byte nào được chuyển trong lần này;
- `n == -1`: cuối luồng (EOF) ở hướng đọc.

Readiness không có nghĩa toàn bộ thông điệp TCP đã đến. Bởi vì một event loop có thể quản lý nhiều socket, nó thường phải giữ input buffer, parser state và output queue **riêng cho từng kết nối**.

### 2.3. AsynchronousChannel: completion của một thao tác

~~~java
try (AsynchronousSocketChannel socket =
        AsynchronousSocketChannel.open()) {
    Future<Void> future = socket.connect(remote);
    future.get(); // Caller vẫn BLOCK nếu đợi tại get().
}
~~~

Đây là **mảnh minh họa**; cần `AsynchronousSocketChannel`, `Future`, một `remote` hợp lệ và xử lý checked exceptions.

API async có hai lối:

- **`Future`**: thao tác khởi động và Future dùng để hỏi/đợi kết quả. Gọi `get()` ngay sau mỗi lệnh có thể biến caller thành luồng chờ như mã tuần tự.
- **`CompletionHandler`**: chuyển callback cho API và nhận `completed` hoặc `failed` khi thao tác hoàn tất. Phải quản lý lifetime của dữ liệu, error flow và callback thread.

“Async” không có nghĩa không sử dụng luồng: implementation có thể dùng thread pool, completion port hoặc cơ chế nền tảng khác. Nó cũng không đồng nghĩa zero-copy, tự có transaction hoặc tự giải quyết partial I/O.

## 3. Bảng so sánh tổng quan

| Tiêu chí | Blocking I/O | Selector non-blocking | Asynchronous API |
| --- | --- | --- | --- |
| Ngữ nghĩa | Caller có thể đợi cho tới khi thao tác tiến triển/hoàn tất theo hợp đồng. | Selector báo channel ready, caller tự thử read/write. | Future/callback báo thao tác cụ thể đã hoàn tất/thất bại. |
| Mã nguồn | Tuyến tính, ít state machine. | Event loop và state cho mỗi connection. | Continuation, callback/Future và state in-flight. |
| Concurrency | Thường dùng nhiều platform threads hoặc pool. | Một event loop có thể theo dõi nhiều selectable channels. | Provider/pool xử lý nhiều operations; số thread phụ thuộc implementation. |
| Khả năng xử lý file | `Files`/`FileChannel` rất thuận tiện. | `FileChannel` không thể đăng ký Selector như socket. | `AsynchronousFileChannel` hỗ trợ positional file I/O. |
| Partial read/write | Có. | Có. | Có, cả khi một Future/completion đã trả kết quả. |
| Timeout | Cấu hình và deadline theo API/protocol. | Phải giữ deadline trong event loop; `select(timeout)` chỉ là thời gian chờ readiness. | `Future.get(timeout)`/handler và chính sách cancel/close. |
| Backpressure | Giới hạn worker/queue, chờ write. | Quản lý `OP_READ`/`OP_WRITE` và per-connection queues. | Giới hạn số operation in-flight và lifetime buffer. |
| Chi phí triển khai | Thường thấp nhất. | Phải tự giải quyết state, fairness, readiness và cleanup. | Phải xử lý callback, ownership, cancellation và thứ tự completion. |
| Phù hợp | I/O đơn giản, ít/vừa kết nối, công cụ batch. | Nhiều socket đồng thời, đa số idle, event-driven services. | Pipeline dựa trên completion, file offset và callbacks/Future. |

Không diễn giải bảng này như kết quả benchmark. Hiệu năng đo được còn phụ thuộc hệ điều hành, cấu hình provider, payload, server behavior, GC, TLS, số kết nối và thời gian xử lý ứng dụng.

## 4. Khi nào blocking là lựa chọn hợp lý?

Blocking phù hợp khi:

1. Giao tiếp chỉ vài kết nối hoặc số kết nối đang chờ nằm trong khả năng thread pool hiện tại.
2. Logic request/response cần mã tuyến tính, dễ đọc và dễ xử lý ngoại lệ.
3. Ứng dụng CLI, batch, dịch vụ nhỏ, đọc/ghi tệp cục bộ có khối lượng vừa phải.
4. Thư viện phụ trợ chủ yếu blocking; chưa có yêu cầu số lượng kết nối đồng thời cực lớn.
5. Cách hiện tại đạt throughput/latency, chưa có profiling chứng minh thread waiting là nút thắt.

### 4.1. Gửi và nhận đầy đủ với blocking SocketChannel

~~~java
static void writeFully(SocketChannel channel, ByteBuffer source)
        throws IOException {
    while (source.hasRemaining()) {
        int n = channel.write(source);
        if (n == 0) {
            throw new IOException("Blocking write không tiến triển");
        }
    }
}

static void readFully(SocketChannel channel, ByteBuffer target)
        throws IOException {
    while (target.hasRemaining()) {
        int n = channel.read(target);
        if (n == -1) {
            throw new EOFException("EOF trước khi đủ dữ liệu");
        }
        if (n == 0) {
            throw new IOException("Blocking read không tiến triển");
        }
    }
}
~~~

Hai phương thức trên là **mảnh mã cần đặt trong một lớp**, với import `IOException`, `EOFException`, `ByteBuffer` và `SocketChannel`. Chúng có chủ đích dành cho *blocking channel* với buffer có chỗ và kỳ vọng tiến triển. Đừng dùng nguyên xi cho socket non-blocking: giá trị `0` khi ấy có thể hoàn toàn bình thường.

`readFully` chỉ nên dùng nếu giao thức thực sự đòi hỏi **đúng một số byte**. Đọc một tệp đến EOF hoặc đọc văn bản phân cách dấu xuống dòng có quy trình khác.

### 4.2. Virtual threads có thay đổi quyết định không?

Từ **Java 21**, virtual threads là một lựa chọn quan trọng cho lượng lớn tác vụ *blocking-style*. Với Java 17, virtual threads chưa có ở dạng tính năng chuẩn, nên mã ví dụ trong bài chỉ dùng những API thuộc Java 17.

Virtual threads không thay thế nhu cầu giới hạn số socket, downstream database connections, memory buffers hay deadline. Nếu dự án được phép nâng lên Java 21+, hãy đưa virtual threads vào phương án so sánh bên cạnh selector và async.

## 5. Khi nào chọn Selector?

Chọn `Selector` khi:

- Có hàng nghìn kết nối TCP mở đồng thời, phần lớn ở trạng thái chờ dữ liệu.
- Muốn **một hoặc vài I/O event-loop threads** quản lý nhiều socket.
- Mỗi lượt xử lý event ngắn, có khả năng thiết kế parser theo state machine.
- Muốn kiểm soát readiness, outbound queues, fairness và backpressure ở cấp kết nối.
- Hệ thống đã theo kiến trúc non-blocking hoặc đang dùng framework dựa trên selector.

Mẫu khung event loop:

~~~java
while (running) {
    selector.select();
    Iterator<SelectionKey> iterator =
            selector.selectedKeys().iterator();

    while (iterator.hasNext()) {
        SelectionKey key = iterator.next();
        iterator.remove(); // Bắt buộc để không giữ selected key cũ.

        if (!key.isValid()) {
            continue;
        }
        if (key.isAcceptable()) {
            // ServerSocketChannel.accept(): có thể trả null.
        } else if (key.isConnectable()) {
            // SocketChannel.finishConnect(): kết thúc pending connect.
        } else if (key.isReadable()) {
            // SocketChannel.read(): >0, 0 hoặc -1.
        } else if (key.isWritable()) {
            // SocketChannel.write(): có thể ghi một phần hoặc 0.
        }
    }
}
~~~

Đây là **pseudocode event loop**, chưa chứa khai báo `running`, `selector`, per-connection state, try/catch và cleanup.

Nguyên tắc quan trọng:

- Chỉ channel ở **non-blocking mode** mới đăng ký với Selector. `SocketChannel` từ `ServerSocketChannel.accept()` ban đầu là blocking; phải `configureBlocking(false)` trước khi đăng ký.
- Một selector chỉ báo *ready*. `read/write` vẫn có thể chuyển ít dữ liệu. Trạng thái position/limit của mỗi `ByteBuffer` phải được giữ giữa nhiều lượt.
- `SelectionKey.attachment()` có thể gắn input parser, output queue và connection metadata.
- `OP_WRITE` không nên bật liên tục khi không có byte chờ gửi; điều đó có thể làm event loop bị đánh thức dày đặc.
- Đừng chạy xử lý CPU-heavy, truy vấn database blocking hoặc sleep trong event loop; một callback chậm sẽ trì hoãn mọi socket trên cùng luồng.
- `selector.close()` không tương đương đóng mọi peer socket; ứng dụng phải đóng những channel mình sở hữu.

Đặc biệt, **`FileChannel` không phải `SelectableChannel`**. Không sử dụng Selector để “làm FileChannel bất đồng bộ”; hãy dùng file API thích hợp.

## 6. Khi nào chọn AsynchronousChannel?

Các lớp tiêu biểu của Java NIO.2:

| API | Chức năng |
| --- | --- |
| `AsynchronousSocketChannel` | TCP connect/read/write bất đồng bộ. |
| `AsynchronousServerSocketChannel` | Bind và accept TCP bất đồng bộ. |
| `AsynchronousFileChannel` | Đọc/ghi vùng tệp tại offset xác định bằng Future/callback. |
| `CompletionHandler<V, A>` | Callback `completed(result, attachment)` hoặc `failed(error, attachment)`. |
| `AsynchronousChannelGroup` | Nhóm channel và tài nguyên thực thi liên quan. |

Đây là lựa chọn hữu ích khi ứng dụng đã dựa trên **completion pipeline**, muốn caller phát hành operation và làm việc khác, hoặc muốn file I/O có callback/Future tại offset riêng.

### 6.1. Future không khiến mã chờ biến mất

~~~java
try (AsynchronousSocketChannel client =
        AsynchronousSocketChannel.open()) {

    client.connect(remote).get(5, TimeUnit.SECONDS);

    ByteBuffer data = ByteBuffer.allocate(256);
    Future<Integer> operation = client.read(data);
    int n = operation.get(5, TimeUnit.SECONDS);
}
~~~

Mảnh minh họa cần `remote` hợp lệ và import `AsynchronousSocketChannel`, `ByteBuffer`, `Future`, `TimeUnit`. **`get(...)` vẫn block caller trong lúc đợi**; phương thức `read` bên dưới được phát hành bất đồng bộ.

Khi Future hoàn tất, `n` là **số byte thực nhận**, không phải bằng `data.capacity()` mặc định. `n == -1` báo EOF; `n == 0` không đồng nghĩa một thông điệp đã hoàn chỉnh.

### 6.2. CompletionHandler thực sự tổ chức flow theo callback

~~~java
ByteBuffer input = ByteBuffer.allocate(1024);

channel.read(input, input,
        new CompletionHandler<Integer, ByteBuffer>() {
    @Override
    public void completed(Integer n, ByteBuffer buffer) {
        if (n == -1) {
            closeConnection();
            return;
        }
        if (n > 0) {
            buffer.flip();
            processAvailableBytes(buffer);
            buffer.compact();
        }
        // Nếu cần tiếp tục, phát hành lần read kế tiếp.
    }

    @Override
    public void failed(Throwable error, ByteBuffer buffer) {
        reportFailure(error);
        closeConnection();
    }
});
~~~

Đây là **pseudocode tích hợp**, không phải Java độc lập; `channel`, `closeConnection`, `processAvailableBytes`, `reportFailure` phải được ứng dụng định nghĩa.

Trạng thái input/output và buffer phải sống ít nhất đến lúc completion kết thúc. Không để luồng khác thay đổi position/limit của buffer đang được operation sử dụng. Handler thường được gọi thông qua cơ chế thực thi của provider/channel group; tránh thực hiện xử lý CPU hoặc blocking kéo dài trong handler.

### 6.3. File I/O bất đồng bộ

~~~java
try (AsynchronousFileChannel file =
        AsynchronousFileChannel.open(path, StandardOpenOption.READ)) {

    ByteBuffer target = ByteBuffer.allocate(4096);
    Future<Integer> op = file.read(target, 0L);
    int n = op.get();
}
~~~

Đây là mảnh minh họa, cần `path` hợp lệ và các import tương ứng. `AsynchronousFileChannel.read(dst, position)` nhận một **vị trí tệp rõ ràng** cho từng operation. Điều này khác với việc dùng một file-position chung trên `FileChannel`; các operation hoàn tất có thể không theo thứ tự phát hành. Không coi việc callback đến trước là bằng chứng byte offset nhỏ hơn đã ghi hoàn tất trước.

Implementation của asynchronous file I/O có thể dùng thread pool; không mặc định là kernel-native async hoặc nhanh hơn mọi `FileChannel` blocking.

## 7. Cây quyết định theo workload

~~~text
Bắt đầu
   |
   +-- I/O chủ yếu là đọc/ghi file?
   |     |
   |     +-- Tuần tự và đơn giản -> Files / FileChannel blocking
   |     |
   |     +-- Nhiều offset độc lập / cần completion?
   |             -> AsynchronousFileChannel
   |
   +-- I/O là nhiều socket TCP?
         |
         +-- Ít/vừa, logic tuần tự, dễ bảo trì?
         |       -> Blocking SocketChannel
         |
         +-- Nhiều socket idle, cần readiness event loop?
         |       -> Non-blocking SocketChannel + Selector
         |
         +-- Hệ thống xử lý theo callback/completion?
                 -> AsynchronousSocketChannel
~~~

Một thiết kế có thể **phối hợp** các hướng: selector quản lý network, worker pool xử lý CPU, AsynchronousFileChannel cho file offset, và API blocking cho thao tác nhỏ ngoài event loop. Không cần biến mọi lời gọi trong một ứng dụng thành cùng một kiểu API.

Bảng tình huống:

| Tình huống | Lựa chọn khởi đầu | Lý do |
| --- | --- | --- |
| Chương trình CLI tạo báo cáo từ tệp nhỏ | Blocking `Files` hoặc `FileChannel` | Chi phí trạng thái async lớn hơn lợi ích. |
| Một client gọi một vài TCP service | Blocking `SocketChannel` | Request-response rõ ràng, dễ debug. |
| Gateway có rất nhiều kết nối idle | Selector hoặc framework event-driven có kiểm chứng | Nhiều socket được theo dõi bởi ít I/O threads. |
| Ứng dụng callback-first trên mạng | `AsynchronousSocketChannel` | Flow theo completion, không cần caller chờ. |
| Xử lý nhiều vùng tệp tại các offset | `AsynchronousFileChannel` hoặc worker pool | Có thể overlap tác vụ nhưng phải xét ordering. |
| Server làm nén/mã hóa CPU nặng | Worker executor riêng | Selector không tự song song hóa CPU. |
| I/O blocking kiểu tuyến tính nhưng concurrency lớn trên Java 21+ | Virtual threads là một ứng viên | Vẫn cần giới hạn sockets và downstream resources. |
| Tệp cần snapshot nhất quán khi writer khác cập nhật | Versioning, transaction, snapshot | Đổi API I/O không tự tạo consistency. |

## 8. Ví dụ Java 17+ chạy được: cùng một PING/PONG bằng ba mô hình

Ví dụ có **một server TCP loopback blocking** phục vụ ba kết nối, mỗi lần nhận đúng `PING` và gửi đúng `PONG`. Cả ba client gọi nối tiếp để stdout có thứ tự cố định:

- Client 1: blocking `SocketChannel`.
- Client 2: non-blocking `SocketChannel` + `Selector`, chuyển `OP_CONNECT → OP_WRITE → OP_READ`.
- Client 3: `AsynchronousSocketChannel` với Future và `get(timeout)`. Vì gọi `get`, luồng main vẫn chờ các completion.

Code không giả định một lần `read/write` chuyển đủ bốn byte và có timeout để tránh vòng đợi vô hạn trong client selector/Future.

Lưu file `IoModelChoiceDemo.java`:

~~~java
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class IoModelChoiceDemo {

    private static final byte[] REQUEST =
            "PING".getBytes(StandardCharsets.US_ASCII);

    static void sendBlocking(SocketChannel channel, ByteBuffer src)
            throws IOException {
        while (src.hasRemaining()) {
            if (channel.write(src) == 0) {
                throw new IOException("Blocking write không tiến triển");
            }
        }
    }

    static void receiveBlocking(SocketChannel channel, ByteBuffer dst)
            throws IOException {
        while (dst.hasRemaining()) {
            int n = channel.read(dst);
            if (n == -1) {
                throw new EOFException("EOF trước khi đủ dữ liệu");
            }
            if (n == 0) {
                throw new IOException("Blocking read không tiến triển");
            }
        }
    }

    static String decode(ByteBuffer data) {
        data.flip();
        return StandardCharsets.US_ASCII.decode(data).toString();
    }

    static String blockingClient(SocketAddress address)
            throws IOException {
        try (SocketChannel client = SocketChannel.open(address)) {
            sendBlocking(client, ByteBuffer.wrap(REQUEST));
            ByteBuffer reply = ByteBuffer.allocate(4);
            receiveBlocking(client, reply);
            return decode(reply);
        }
    }

    static String selectorClient(SocketAddress address)
            throws IOException {
        try (Selector selector = Selector.open();
             SocketChannel client = SocketChannel.open()) {

            client.configureBlocking(false);
            boolean connectedNow = client.connect(address);
            client.register(selector, connectedNow
                    ? SelectionKey.OP_WRITE
                    : SelectionKey.OP_CONNECT);

            ByteBuffer outgoing = ByteBuffer.wrap(REQUEST);
            ByteBuffer incoming = ByteBuffer.allocate(4);
            long deadline = System.nanoTime()
                    + TimeUnit.SECONDS.toNanos(5);

            while (incoming.hasRemaining()) {
                long remainingNanos = deadline - System.nanoTime();
                if (remainingNanos <= 0) {
                    throw new SocketTimeoutException("Selector deadline");
                }
                long timeoutMs = Math.max(1,
                        TimeUnit.NANOSECONDS.toMillis(remainingNanos));

                if (selector.select(timeoutMs) == 0) {
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

                    if (key.isConnectable()) {
                        if (client.finishConnect()) {
                            key.interestOps(SelectionKey.OP_WRITE);
                        }
                    } else if (key.isWritable()) {
                        client.write(outgoing);
                        if (!outgoing.hasRemaining()) {
                            key.interestOps(SelectionKey.OP_READ);
                        }
                    } else if (key.isReadable()) {
                        int n = client.read(incoming);
                        if (n == -1) {
                            throw new EOFException("Selector EOF sớm");
                        }
                    }
                }
            }
            return decode(incoming);
        }
    }

    static String asyncClient(SocketAddress address)
            throws Exception {
        try (AsynchronousSocketChannel client =
                AsynchronousSocketChannel.open()) {

            client.connect(address).get(5, TimeUnit.SECONDS);

            ByteBuffer outgoing = ByteBuffer.wrap(REQUEST);
            while (outgoing.hasRemaining()) {
                int n = client.write(outgoing).get(5, TimeUnit.SECONDS);
                if (n == 0) {
                    throw new IOException("Async write không tiến triển");
                }
            }

            ByteBuffer incoming = ByteBuffer.allocate(4);
            while (incoming.hasRemaining()) {
                int n = client.read(incoming).get(5, TimeUnit.SECONDS);
                if (n == -1) {
                    throw new EOFException("Async EOF sớm");
                }
                if (n == 0) {
                    throw new IOException("Async read không tiến triển");
                }
            }
            return decode(incoming);
        }
    }

    static void serveThree(ServerSocketChannel server)
            throws IOException {
        for (int i = 0; i < 3; i++) {
            try (SocketChannel peer = server.accept()) {
                ByteBuffer request = ByteBuffer.allocate(4);
                receiveBlocking(peer, request);

                if (!"PING".equals(decode(request))) {
                    throw new IOException("Request không hợp lệ");
                }

                ByteBuffer answer = ByteBuffer.wrap(
                        "PONG".getBytes(StandardCharsets.US_ASCII));
                sendBlocking(peer, answer);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        try (ServerSocketChannel server = ServerSocketChannel.open()) {
            server.bind(new InetSocketAddress(
                    InetAddress.getLoopbackAddress(), 0));

            SocketAddress address = server.getLocalAddress();
            ExecutorService executor =
                    Executors.newSingleThreadExecutor();

            try {
                Future<?> serving = executor.submit(() -> {
                    try {
                        serveThree(server);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

                String blocking = blockingClient(address);
                String selector = selectorClient(address);
                String async = asyncClient(address);

                serving.get(5, TimeUnit.SECONDS);

                System.out.println("blocking=" + blocking);
                System.out.println("selector=" + selector);
                System.out.println("async=" + async);
            } finally {
                executor.shutdownNow();
            }
        }
    }
}
~~~

Lệnh biên dịch/chạy bằng JDK 17 trở lên:

~~~bash
javac IoModelChoiceDemo.java
java IoModelChoiceDemo
~~~

**Stdout dự kiến** khi môi trường cho phép mở TCP socket trên loopback:

~~~text
blocking=PONG
selector=PONG
async=PONG
~~~

Giải thích:

1. Server bind loopback với port 0 (hệ điều hành tự chọn), rồi một executor thread lần lượt `accept` ba client.
2. `blockingClient` thực hiện `sendBlocking`/`receiveBlocking` trong cùng flow; vẫn lặp đến khi đủ 4 byte.
3. `selectorClient` tự quản lý connection state. Nếu `connect` chưa xong, chờ `OP_CONNECT` và gọi `finishConnect`; tiếp theo gửi PING khi `OP_WRITE`, đọc PONG khi `OP_READ`. Phần chưa xử lý vẫn nằm trong `ByteBuffer`.
4. `asyncClient` phát hành `connect`, `write`, `read` và nhận completion count qua Future. Trong ví dụ caller gọi `get` có timeout, nên vẫn chờ để giữ luồng minh họa đơn giản.
5. Các client lần lượt hoàn tất mới in kết quả. Server luôn phản hồi PONG khi nhận PING, nên ba dòng là cố định.

**Ví dụ không phải benchmark.** Nó chỉ đặt ba phong cách API cạnh nhau trên cùng một protocol. Server chỉ phục vụ ba request nhỏ, không đại diện cho tải hàng nghìn kết nối. Để đánh giá hiệu năng cần workload thật, kiểm tra throughput, P95/P99, CPU, RAM và lỗi timeout.

## 9. Partial I/O và trạng thái buffer: rủi ro chung

Tất cả ba mô hình cần quản lý `ByteBuffer.position()`, `limit()` và `remaining()` đúng:

~~~text
Buffer chứa 8 byte A B C D E F G H, position=0 limit=8
write lần 1 trả 3 -> position=3, remaining=5
write lần 2 trả 2 -> position=5, remaining=3
write lần 3 trả 3 -> position=8, remaining=0
Lúc này mới thực sự hết byte cần gửi.
~~~

- **Blocking:** lặp write đến khi hết, hoặc xử lý lỗi/timeout.
- **Selector:** nếu non-blocking write trả `0`, giữ buffer và đợi `OP_WRITE`; nếu write một phần, không `flip()` hoặc `clear()` nhầm.
- **Async:** sau completion, xem `n` byte thực chuyển; phát hành operation kế tiếp nếu còn dữ liệu. Không sửa buffer khi operation trước đang dùng nó.

Với read, khi đọc một protocol length-prefix, 4 byte header có thể đến qua nhiều lần đọc. Cần parser state machine hoặc tích lũy cho đủ. TCP là luồng byte, **không bảo toàn ranh giới giữa các lần write** của phía gửi.

## 10. Timeout, cancellation và backpressure

### 10.1. Timeout không được suy đoán

Xác định giới hạn **connect**, **đọc một frame**, **ghi phản hồi**, **idle** và **tổng thời gian request**. Ví dụ `selector.select(5_000)` chỉ giới hạn một lần chờ readiness; nó không tự tạo timeout cho toàn bộ request. Cần deadline tích lũy như `System.nanoTime()`.

`Future.get(timeout)` chỉ giới hạn **thời gian chờ của caller**; khi ném `TimeoutException`, không mặc định operation đã bị hủy. Cần đóng socket hoặc yêu cầu cancel có kiểm soát, tránh để callback đến trễ xử lý dữ liệu của request đã bị loại bỏ.

### 10.2. Cancellation và cleanup

Khi client bỏ cuộc, có thể có một I/O operation đã phát hành mà chưa hoàn thành. Cần định nghĩa state như OPEN, CLOSING, CLOSED; đóng tài nguyên và bỏ buffer đang chờ theo chính sách; callback/future lỗi phải được xử lý **idempotent**, không đóng hoặc hoàn tất request nhiều lần.

Một `CompletionHandler` có thể chạy trên thread do implementation quản lý. Nếu callback gọi phương thức blocking lâu, một số completion khác có thể bị trì hoãn. Hãy chuyển tác vụ nặng sang worker pool hoặc pipeline thích hợp.

### 10.3. Backpressure

Giả sử ứng dụng tạo phản hồi nhanh gấp nhiều lần tốc độ client đọc: outbound queue lớn dần và có thể làm cạn RAM.

- Blocking: giới hạn số worker, số request chờ và thời gian gửi; không cho queue tăng vô hạn.
- Selector: chỉ bật `OP_WRITE` khi có byte chờ; có thể tạm ngừng `OP_READ` hoặc đóng socket khi queue vượt ngưỡng.
- Async: đặt hạn mức số operation in-flight và bộ nhớ giữ bởi callback/Future; chưa hoàn thành một write thì không tùy tiện xếp thêm vô hạn các write tương ứng.

Không có API nào tự cung cấp backpressure theo yêu cầu nghiệp vụ nếu ứng dụng không định nghĩa giới hạn.

## 11. Khi nào phối hợp nhiều mô hình?

Một hệ thống có thể sử dụng Selector cho mạng mà vẫn có worker pool thực hiện các công việc blocking hoặc CPU-heavy:

~~~text
Selector thread                        Worker pool
      |                                     |
      +-- read/parse request                |
      +-- submit job ---------------------->|
      |   phục vụ những socket khác         +-- xử lý nghiệp vụ
      |<-------------- kết quả qua queue ---+
      +-- selector.wakeup()
      +-- đẩy response vào output queue
      +-- bật OP_WRITE
~~~

Ở đây queue phải thread-safe. Tốt nhất chỉ event-loop thread sở hữu và cập nhật per-connection buffer/attachment; worker trả kết quả bất biến hoặc message để loop xử lý. `selector.wakeup()` đánh thức select, **không có nghĩa socket tự nhiên đã có readiness**.

Nếu tác vụ là `AsynchronousFileChannel`, có thể nhận Future/callback rồi chuyển kết quả tới event loop. Nhưng không vì trộn mô hình mà bỏ qua ownership, cancellation hay thứ tự ghi.

## 12. Các sai lầm khi lựa chọn API

| Sai lầm | Hệ quả / cách sửa |
| --- | --- |
| Cho rằng asynchronous luôn nhanh hơn blocking | Cần đo workload: scheduling, callback, pool và buffer có chi phí. |
| Dùng Selector cho FileChannel thông thường | `FileChannel` không selectable; dùng `FileChannel` hoặc `AsynchronousFileChannel`. |
| Dùng Future rồi gọi get ngay và tuyên bố caller non-blocking | `get` có thể block; muốn non-blocking caller cần completion/callback phù hợp. |
| Gắn mọi socket vào một event loop rồi làm CPU-heavy trong callback | Một callback lâu cản trở các socket khác; chuyển việc nặng ra pool. |
| Dùng một ByteBuffer cho nhiều async operation đồng thời | Rủi ro tranh chấp position/limit; cần ownership và thời điểm giải phóng rõ ràng. |
| Bỏ qua read/write count vì nghĩ blocking đảm bảo toàn bộ | Vẫn có partial I/O; kiểm tra `remaining()` và EOF. |
| Gọi `clear()` sau một non-blocking write chưa hoàn tất | Mất các byte chưa gửi; giữ buffer chờ OP_WRITE. |
| Giữ OP_WRITE trong interestOps khi output queue rỗng | Loop có thể thức dậy liên tục; chỉ bật khi có dữ liệu. |
| Nghĩ `Future.get(timeout)` chắc chắn đã hủy I/O | Timeout chỉ báo caller hết thời gian chờ; cần cancel/close. |
| Không giới hạn kết nối, hàng đợi hoặc bộ nhớ | Bất kỳ mô hình nào cũng có thể hết tài nguyên. |
| Cho rằng Java 17 có virtual threads chuẩn | Tính năng chính thức bắt đầu ở Java 21. |
| Dùng cùng một lựa chọn cho socket, file, DB, CPU | Các loại tài nguyên có API và chi phí khác nhau; có thể phối hợp. |

## 13. Checklist lựa chọn theo bài toán

Trả lời các câu hỏi sau trước khi triển khai:

1. **Loại tài nguyên?** TCP, UDP, file, database, pipe hay CPU? Selector chỉ áp dụng selectable channels.
2. **Bao nhiêu kết nối đồng thời?** Đừng chỉ đo request mỗi giây mà bỏ qua số socket idle.
3. **Chờ I/O chiếm bao nhiêu thời gian so với xử lý CPU?** Event loop đặc biệt hợp với callback ngắn.
4. **Java phiên bản nào?** Trên Java 17 dùng ba mô hình chuẩn; trên Java 21+ xem thêm virtual threads.
5. **Yêu cầu latency và throughput?** Đo P95/P99, không chỉ trung bình.
6. **Có cần thao tác theo completion callback không?** Nếu không, blocking có thể đủ và dễ hơn.
7. **Có state machine cho framing và partial reads/writes không?** Bất kể API nào đều phải đúng giao thức.
8. **Timeout, cancellation, close được định nghĩa chưa?** Đừng để request hoặc in-flight operation tồn tại vô hạn.
9. **Ai sở hữu buffer và kết nối?** Tránh race khi callback/worker cùng cập nhật.
10. **Có giới hạn output queue và số operation in-flight không?** Đây là nền tảng backpressure.
11. **Đã thử tải với client chậm, EOF, ngắt kết nối và mạng lỗi chưa?** Tải lý tưởng thường che giấu lỗi.
12. **Có thật sự cần đổi kiến trúc?** Nếu cách đơn giản đáp ứng SLO, lợi ích của một mô hình phức tạp cần có số liệu chứng minh.

## 14. Câu hỏi tự kiểm tra

1. **Một CLI đọc tệp nhỏ rồi ghi kết quả, có cần Selector không?** Thông thường không. `Files` hoặc `FileChannel` blocking đơn giản hơn.
2. **Một gateway có rất nhiều TCP socket chờ dữ liệu, mô hình nào đáng cân nhắc?** Selector hoặc async socket, với benchmark và giới hạn tài nguyên.
3. **`Selector.isReadable` báo gì?** Readiness để thử đọc; không hứa một message hoàn chỉnh.
4. **Asynchronous Future mà gọi `get()` thì caller có thể bị chặn không?** Có.
5. **`AsynchronousFileChannel` có thể đăng ký OP_READ với Selector không?** Không; đây là API completion cho file.
6. **Một read completion nhận 2/4 byte header có hoàn tất giao thức chưa?** Chưa, phải nhận tiếp hoặc xử lý EOF/lỗi.
7. **Một non-blocking write trả 0 là lỗi chắc chắn không?** Không; có thể tạm chưa writable.
8. **`OP_WRITE` nên bật khi nào?** Khi có dữ liệu chờ gửi và cần đợi writable; tắt khi hết.
9. **Một loop chỉ có một thread có tự chạy song song các CPU tasks không?** Không.
10. **Timeout của Future.get tự hủy socket operation không?** Không bảo đảm; cần chính sách cancel/close.
11. **Tại sao AsynchronousFileChannel không nhất thiết nhanh hơn FileChannel blocking?** Provider có thể dùng thread pool; tải và overhead quyết định.
12. **Nếu hệ thống cần data consistency, đổi sang async có giải quyết transaction không?** Không; consistency/transaction thuộc giao thức hoặc tầng lưu trữ.

## 15. Tài liệu Java 17 tham khảo

- [SocketChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/SocketChannel.html): blocking/non-blocking, connect/read/write.
- [Selector](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/Selector.html) và [SelectionKey](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/SelectionKey.html): readiness/interest operations.
- [AsynchronousSocketChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/AsynchronousSocketChannel.html): Future và completion handler cho TCP.
- [AsynchronousFileChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/AsynchronousFileChannel.html): file I/O với offset rõ ràng.
- [CompletionHandler](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/CompletionHandler.html): kết quả thành công hoặc thất bại của operation.

**Ghi nhớ:** **Blocking** tối ưu sự đơn giản của flow tuần tự; **Selector** tối ưu khả năng điều phối nhiều selectable channels theo readiness với event loop; **AsynchronousChannel** cho completion qua Future/callback. Chọn theo workload, phiên bản JDK, khả năng quản lý state và SLO; cả ba đều phải giải quyết partial I/O, timeout, cancellation, buffer lifetime và backpressure.


</details>

- [Quay lại đầu trang](#back-to-top)
