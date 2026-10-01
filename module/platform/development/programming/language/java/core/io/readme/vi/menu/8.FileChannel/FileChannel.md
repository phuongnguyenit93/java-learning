# FileChannel và I/O tệp nâng cao

`FileChannel` là một channel dành cho tệp. Nó vẫn đọc/ghi byte qua `ByteBuffer`, nhưng còn có **vị trí trong tệp**, thao tác tại **độ lệch (offset)** cụ thể, truyền dữ liệu giữa các channel, khóa tệp (file lock) và ánh xạ bộ nhớ (memory mapping). Đây là lúc mô hình Buffer/Channel ở chương trước bắt đầu cho thấy các khả năng mà luồng tuần tự không biểu diễn trực tiếp.

~~~java
Path file = Files.createTempFile("channel-demo-", ".txt");

try (FileChannel channel = FileChannel.open(
        file,
        StandardOpenOption.READ,
        StandardOpenOption.WRITE
)) {
    // làm việc với channel
}
~~~

## <a id="filechannel-random-access">Truy cập ngẫu nhiên với FileChannel</a>

Luồng (stream) thường dẫn ta tới cách đọc tuần tự từ đầu đến cuối. `FileChannel` có một **vị trí tệp (file position)**:

~~~java
long current = channel.position();
channel.position(10);
~~~

`read(buffer)` đọc từ `position` hiện tại rồi tăng `position` theo số byte đã đọc. `write(buffer)` ghi từ `position` hiện tại rồi cũng tăng `position` theo số byte thực tế đã ghi.

Ví dụ ghi một đoạn dữ liệu UTF-8 rồi quay lại đầu:

~~~java
ByteBuffer data =
        StandardCharsets.UTF_8.encode("Hello FileChannel");

while (data.hasRemaining()) {
    channel.write(data);
}

channel.position(0);
~~~

Truy cập ngẫu nhiên trở nên rõ hơn với thao tác đọc/ghi theo vị trí:

~~~java
ByteBuffer part = ByteBuffer.allocate(5);
int read = channel.read(part, 6);
~~~

Phiên bản nạp chồng (overload) `read(buffer, position)` đọc từ độ lệch chỉ định nhưng **không thay đổi vị trí tệp chung của channel**. `write(buffer, position)` có cùng đặc điểm với channel ghi thông thường. Không được dựa vào ngữ nghĩa ghi theo vị trí khi channel được mở với `APPEND`: JDK không quy định kết quả của thao tác ghi tại một vị trí chỉ định trong chế độ append.

Khi thao tác ghi theo vị trí có thể chỉ ghi một phần buffer, độ lệch của lần sau phải tăng theo số byte đã ghi:

~~~java
ByteBuffer patch = StandardCharsets.UTF_8.encode("JAVA");
long offset = 0;

while (patch.hasRemaining()) {
    int written = channel.write(patch, offset);
    if (written <= 0) {
        throw new IOException("Positional write made no progress");
    }
    offset += written;
}
~~~

Ngoài `position`, `FileChannel` còn cho biết kích thước và có thể cắt ngắn tệp bằng `truncate`:

~~~java
long size = channel.size();
channel.truncate(100);
~~~

`truncate(100)` làm tệp ngắn lại nếu tệp đang lớn hơn 100 byte. Nó không tự mở rộng tệp nhỏ thành 100 byte.

Khi ứng dụng có yêu cầu về **độ bền dữ liệu (durability)** — mức bảo đảm rằng dữ liệu đã ghi vẫn còn sau sự cố như tiến trình/JVM dừng hoặc máy mất điện, tùy khả năng của hệ thống lưu trữ — `force(...)` có thể yêu cầu các thay đổi được đẩy xuống thiết bị lưu trữ:

~~~java
channel.force(true);
~~~

Tham số boolean cho biết Java có cần yêu cầu đẩy cả siêu dữ liệu (metadata) hay không: `force(false)` tập trung vào thay đổi nội dung tệp, còn `force(true)` yêu cầu thêm các cập nhật metadata cần thiết. Đây là thao tác có chi phí và bảo đảm cuối cùng vẫn phụ thuộc **chuỗi tầng lưu trữ (storage stack)**, tức các lớp từ JVM, hệ điều hành, hệ thống tệp, bộ nhớ đệm cho tới thiết bị lưu trữ vật lý. Không nên gọi sau từng lần ghi nhỏ chỉ vì muốn “an toàn hơn”; yêu cầu về độ bền dữ liệu phải được xác định rõ.

`FileChannel.force(...)` cũng không thay thế `MappedByteBuffer.force()`. Nếu dữ liệu được sửa thông qua buffer ánh xạ bộ nhớ, chính `MappedByteBuffer` có `force()` để yêu cầu đẩy thay đổi của vùng ánh xạ xuống tầng lưu trữ.

`RandomAccessFile` là API `java.io` cũ hơn cho cùng nhóm bài toán truy cập tệp tại vị trí tùy ý. Nó có một con trỏ tệp (file pointer) có thể di chuyển bằng `seek(...)` và còn triển khai `DataInput`/`DataOutput`, nên thuận tiện trong mã cũ hoặc khi cần đọc/ghi giá trị nguyên thủy theo mô hình đó. `FileChannel` phù hợp hơn khi bài toán cần `ByteBuffer`, thao tác theo vị trí không làm đổi vị trí dùng chung, truyền trực tiếp, khóa hoặc ánh xạ. `RandomAccessFile.getChannel()` trả về channel gắn với cùng tệp và vị trí tệp của hai API liên kết với nhau, vì vậy trộn cả hai mức trừu tượng trong cùng luồng xử lý cần đặc biệt cẩn thận.

## <a id="filechannel-transfer">transferTo và transferFrom</a>

Khi mục tiêu chỉ là chuyển byte giữa các channel, việc tự sao chép từng khối qua một `ByteBuffer` trong Java có thể không cần thiết. `FileChannel` cung cấp:

~~~java
long moved = source.transferTo(position, count, target);
~~~

và:

~~~java
long moved = target.transferFrom(source, position, count);
~~~

Một số JVM/hệ điều hành có thể tối ưu đường truyền này để giảm việc sao chép dữ liệu qua không gian người dùng (user space). Tuy nhiên, “có thể tối ưu” không phải cam kết rằng mọi môi trường đều dùng zero-copy.

Điều quan trọng hơn về tính đúng đắn: **một lần truyền có thể chuyển ít byte hơn số yêu cầu**. mã phải dùng giá trị trả về:

~~~java
long position = 0;
long size = source.size();

while (position < size) {
    long moved =
            source.transferTo(position, size - position, target);

    if (moved <= 0) {
        throw new IOException("Transfer made no progress");
    }

    position += moved;
}
~~~

Điểm của ví dụ là không được coi `moved == 0` như “đã sao chép xong”. Với loại đích có thể tạm thời không nhận dữ liệu, chính sách thực tế có thể là thử lại hoặc dùng phương án dự phòng; với ví dụ tệp-sang-tệp dạng blocking ở đây, báo lỗi khi không có tiến triển giúp tránh vòng lặp vô hạn và tránh trả về một bản sao thiếu mà tưởng là thành công.

Với sao chép tệp thông thường, `Files.copy` thường đơn giản và truyền đạt ý định tốt hơn. `transferTo/transferFrom` đáng quan tâm khi mã đã làm việc ở tầng channel hoặc khi đo đạc/profiling cho thấy đường truyền dữ liệu là điểm nóng cần tối ưu.

## <a id="file-lock-boundary">Ranh giới của khóa tệp</a>

**Khóa tệp (file lock)** là một yêu cầu phối hợp với hệ điều hành/hệ thống tệp để đánh dấu rằng một JVM/tiến trình đang giữ quyền truy cập đã thỏa thuận trên toàn bộ tệp hoặc một vùng byte của tệp. Vai trò chính của nó là giúp **nhiều tiến trình phối hợp truy cập cùng một tệp** khi các bên tham gia cùng tôn trọng quy ước khóa.

Hai kiểu thường gặp là:

- **khóa độc quyền (exclusive lock)**: yêu cầu quyền độc quyền trên vùng đó, nên một khóa xung đột khác không nên cùng tồn tại;
- **khóa dùng chung (shared lock)**: cho phép nhiều bên cùng giữ khóa dùng chung tương thích trên vùng đó khi nền tảng hỗ trợ, nhưng vẫn xung đột với khóa độc quyền.

Một yêu cầu khóa dùng chung **có thể bị nền tảng chuyển thành khóa độc quyền** nếu hệ điều hành/hệ thống tệp không hỗ trợ khóa dùng chung. Vì vậy nếu loại khóa thực tế quan trọng, kiểm tra `FileLock.isShared()` trên đối tượng `FileLock` đã nhận được thay vì chỉ nhớ giá trị boolean đã yêu cầu.

`FileChannel` có thể yêu cầu khóa một vùng tệp:

~~~java
try (FileLock lock = channel.lock()) {
    // thao tác cần phối hợp qua khóa tệp
}
~~~

Có thể khóa một vùng byte và yêu cầu chế độ dùng chung/độc quyền:

~~~java
try (FileLock lock =
             channel.lock(position, size, false)) {
    // false: yêu cầu exclusive lock
}
~~~

Sau khi đã hiểu vai trò phối hợp đó, cần nhớ các ranh giới:

- Khóa tệp do Java lấy được giữ thay mặt cho **toàn JVM**, không phải một monitor dành riêng cho một Java thread. Vì vậy không dùng nó thay cho cơ chế đồng bộ giữa các thread trong cùng JVM.
- Cách hệ điều hành/hệ thống tệp cưỡng chế hoặc xem khóa chỉ mang tính thỏa thuận (advisory) có thể khác nhau. Để có tính đúng đắn đa nền tảng, hãy coi đây là cơ chế phối hợp mà các bên tham gia phải tuân thủ.
- Các khóa chồng lấn trong cùng JVM có thể gây `OverlappingFileLockException`.
- Trên một số hệ thống, đóng một channel của tệp có thể làm mất **toàn bộ khóa mà JVM đang giữ trên cùng tệp bên dưới**, kể cả khóa được lấy qua channel khác. Vì vậy mã cần tính di động không nên giả định vòng đời khóa của từng channel luôn tách biệt hoàn toàn với các channel khác mở cùng tệp.
- Khóa không biến nhiều thao tác tệp thành giao dịch cơ sở dữ liệu và không cung cấp giao dịch nghiệp vụ.
- `FileLock` là tài nguyên có vòng đời; phải giải phóng nó, thường bằng `try-with-resources`.

`tryLock()` cho phép thử lấy lock mà không chờ:

~~~java
FileLock lock = channel.tryLock();

if (lock != null) {
    try (lock) {
        // protected work
    }
}
~~~

Nếu bài toán là phối hợp phân tán giữa các dịch vụ, giao dịch qua nhiều tài nguyên hoặc đồng bộ dữ liệu nghiệp vụ, khóa tệp không phải mức trừu tượng phù hợp. Phạm vi của nó là phối hợp quanh một tệp/channel.

## <a id="memory-mapped-boundary">Ranh giới của tệp ánh xạ bộ nhớ</a>

`FileChannel.map` ánh xạ một vùng tệp vào không gian bộ nhớ và trả về `MappedByteBuffer`. Ví dụ dưới đây cố ý chỉ ánh xạ tối đa một cửa sổ 64 MiB:

Các chế độ chính là `READ_ONLY` (chỉ đọc), `READ_WRITE` (thay đổi có thể ghi về tệp) và `PRIVATE` (copy-on-write: thay đổi của tiến trình không trở thành thay đổi chung của tệp).

~~~java
long regionSize = Math.min(channel.size(), 64L * 1024 * 1024);

if (regionSize > 0) {
    MappedByteBuffer mapped = channel.map(
            FileChannel.MapMode.READ_ONLY,
            0,
            regionSize
    );

    byte first = mapped.get(0);
}
~~~

Ở đây, **bộ nhớ ảo (virtual memory)** là cơ chế của hệ điều hành ánh xạ không gian địa chỉ mà tiến trình nhìn thấy tới RAM và dữ liệu trên tầng lưu trữ. Hệ điều hành quản lý dữ liệu theo các khối gọi là **trang (page)**; khi mã truy cập một trang chưa có sẵn trong RAM, **lỗi trang (page fault)** xảy ra để hệ điều hành nạp hoặc ánh xạ trang cần thiết.

Mô hình cần nhớ:

~~~text
tệp trên tầng lưu trữ
↕ cơ chế bộ nhớ ảo của hệ điều hành
MappedByteBuffer
↕
mã Java đọc/ghi như một Buffer
~~~

Ánh xạ bộ nhớ có thể hữu ích với tệp lớn cần truy cập ngẫu nhiên nhiều vùng vì hệ điều hành quản lý việc nạp trang khi cần. Nhưng chi phí I/O không biến mất; nó chỉ xuất hiện qua cơ chế bộ nhớ ảo, ví dụ lỗi trang khi một trang chưa có trong RAM.

Các ranh giới cần nhớ:

- Vùng ánh xạ có offset và kích thước cụ thể; mã vẫn phải kiểm soát phạm vi.
- Với `FileChannel.map(MapMode, long, long)`, một vùng ánh xạ đơn lẻ không thể vượt quá `Integer.MAX_VALUE` byte vì `MappedByteBuffer` dùng chỉ số/capacity kiểu `int`; tệp lớn hơn có thể cần được ánh xạ theo nhiều cửa sổ. Đây là giới hạn của API, không phải đặc điểm riêng của Java 21.
- Sau khi tạo thành công, vùng ánh xạ tồn tại độc lập với việc `FileChannel` còn mở hay đã đóng; đóng channel **không tự hủy ánh xạ hoặc làm `MappedByteBuffer` đã tạo mất hiệu lực**.
- Nếu tệp bị cắt ngắn hoặc thay đổi đồng thời bởi thành phần khác, việc truy cập vùng đã ánh xạ có thể tạo lỗi hoặc hành vi phụ thuộc hệ thống.
- `MappedByteBuffer` không có API `close()` tiêu chuẩn để ứng dụng chủ động hủy ánh xạ chính xác tại một dòng lệnh; vòng đời vùng ánh xạ vì thế kém trực tiếp hơn channel.
- Với vùng ánh xạ `READ_WRITE`, `force()` yêu cầu đẩy các thay đổi xuống thiết bị lưu trữ chứa tệp ánh xạ; JDK đưa ra cam kết mạnh nhất khi tệp nằm trên thiết bị lưu trữ cục bộ. `force()` **không có tác dụng** với ánh xạ `READ_ONLY` hoặc `PRIVATE`, vì vậy ánh xạ `PRIVATE` theo cơ chế copy-on-write không được xem như vùng ánh xạ ghi bền vững trở lại tệp.
- Ánh xạ thường phù hợp hơn khi tải công việc thực sự có truy cập ngẫu nhiên lớn; với tệp nhỏ hoặc đọc tuần tự, stream/channel thông thường đơn giản hơn.

Ánh xạ bộ nhớ vì thế là công cụ chuyên biệt, không phải mặc định cho mọi I/O tệp.

## <a id="asynchronous-filechannel-boundary">Ranh giới của AsynchronousFileChannel</a>

`AsynchronousFileChannel` vẫn là channel dành cho tệp, nhưng thao tác đọc/ghi của nó được **khởi chạy rồi hoàn tất bất đồng bộ** thay vì buộc bên gọi chờ ngay tại lời gọi `read` hoặc `write`.

Khác với `FileChannel`, API này không dựa vào một vị trí tệp dùng chung có thể thay đổi. Mỗi lần đọc/ghi chỉ rõ offset trong tệp:

~~~java
try (AsynchronousFileChannel channel =
             AsynchronousFileChannel.open(path, StandardOpenOption.READ)) {
    ByteBuffer buffer = ByteBuffer.allocate(4096);
    Future<Integer> pending = channel.read(buffer, 0);

    // Chương trình có thể làm việc khác ở đây.
    int read = pending.get();
}
~~~

`Future<Integer>` là một cách nhận kết quả. Gọi `get()` sẽ chờ nếu thao tác chưa hoàn tất, nên nếu mã gọi `get()` ngay sau `read(...)`, lợi ích của việc tách thời điểm khởi chạy và thời điểm chờ gần như mất đi.

API còn hỗ trợ `CompletionHandler`, nơi Java gọi hàm callback khi thao tác thành công hoặc thất bại:

~~~java
channel.read(buffer, 0, null,
        new CompletionHandler<Integer, Void>() {
            @Override
            public void completed(Integer count, Void ignored) {
                // dùng kết quả sau khi read hoàn tất
            }

            @Override
            public void failed(Throwable error, Void ignored) {
                // xử lý lỗi
            }
        });
~~~

Ghi cũng có hai cách tương tự và luôn nhận một vị trí tệp rõ ràng. Giống channel đồng bộ, số byte hoàn tất có thể nhỏ hơn số byte còn lại trong buffer, nên xử lý nhiều bước vẫn phải dựa trên số lượng/`position` thực tế thay vì giả định một thao tác xử lý toàn bộ dữ liệu.

Vòng đời vẫn phải rõ ràng: `AsynchronousFileChannel` là `AutoCloseable`; bên sở hữu phải giữ channel mở đủ lâu cho những thao tác mà ứng dụng còn cần và đóng nó khi kết thúc. Nếu đóng channel khi vẫn còn thao tác bất đồng bộ đang chờ, các thao tác đó hoàn tất với `AsynchronousCloseException`. Cũng không được sửa hoặc tái sử dụng vùng `ByteBuffer` đang tham gia một thao tác bất đồng bộ cho tới khi thao tác đó đã hoàn tất theo quy ước của ứng dụng.

Nhiều thao tác đọc/ghi có thể cùng ở trạng thái chưa hoàn tất. **JDK không quy định thứ tự hoàn tất I/O hoặc thứ tự các `CompletionHandler` được gọi**, và không đảm bảo chúng trùng với thứ tự khởi chạy. Nếu thuật toán cần thứ tự, mã phải tự tạo quy tắc phối hợp thay vì suy ra từ thứ tự gọi phương thức.

Hủy thao tác cũng là vấn đề của I/O, không chỉ là hành vi chung của `Future`. Gọi `cancel(true)` có thể ngắt thao tác bằng cách **đóng channel**; khi đó các thao tác bất đồng bộ khác còn đang chờ trên cùng channel có thể thất bại với `AsynchronousCloseException`. Nếu phần triển khai không thể khẳng định byte đã được đọc/ghi tới đâu trước lúc hủy, trạng thái sau hủy phụ thuộc phần triển khai; vì vậy không được coi buffer/trạng thái của thao tác bị hủy như thể “chưa có gì xảy ra”.

Ranh giới lựa chọn:

~~~text
FileChannel
→ bên gọi thực hiện I/O tệp đồng bộ/blocking
→ luồng điều khiển thường đơn giản hơn

AsynchronousFileChannel
→ thao tác có thể hoàn tất sau
→ phù hợp khi kiến trúc thực sự tận dụng thời gian chờ để làm việc khác
→ cách hoàn tất, hủy và phối hợp trở thành một phần của thiết kế
~~~

I/O bất đồng bộ không tự động làm chương trình nhanh hơn và cũng không tự giải quyết truy cập đồng thời. Khi nhiều thao tác chồng lấn trên cùng vùng tệp hoặc cùng dữ liệu trong bộ nhớ, ứng dụng vẫn phải định nghĩa cách phối hợp rõ ràng. Executor, ghép callback, hủy thao tác, khả năng nhìn thấy thay đổi bộ nhớ và các mô hình đồng thời sâu hơn thuộc module Concurrency; ở đây điều cần nắm là quy ước I/O bất đồng bộ và vòng đời của channel/buffer.

Sau `FileChannel` và `AsynchronousFileChannel`, ta đã gặp nhiều đối tượng gắn với tài nguyên bên ngoài JVM: stream, channel, `DirectoryStream` và `FileLock`. Chương tiếp theo tập trung vào câu hỏi thiết kế quan trọng hơn cú pháp: **ai sở hữu tài nguyên và khi nào tài nguyên phải được đóng?**
