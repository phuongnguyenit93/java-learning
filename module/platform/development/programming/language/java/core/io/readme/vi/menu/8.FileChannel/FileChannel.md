# FileChannel và truy cập ngẫu nhiên

`FileChannel` là channel dành cho tệp. Nó vẫn đọc/ghi byte qua `ByteBuffer`, nhưng còn có **vị trí trong tệp**, thao tác tại offset cụ thể, truyền dữ liệu giữa channel, file lock và memory mapping. Đây là lúc mô hình Buffer/Channel ở chương trước bắt đầu cho thấy các khả năng mà stream tuần tự không biểu diễn trực tiếp.

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

Stream thường dẫn ta tới cách đọc tuần tự từ đầu đến cuối. `FileChannel` có một **file position**:

~~~java
long current = channel.position();
channel.position(10);
~~~

`read(buffer)` đọc từ position hiện tại rồi tăng position theo số byte đã đọc. `write(buffer)` ghi từ position hiện tại rồi cũng tăng position theo số byte thực tế đã ghi.

Ví dụ ghi một payload UTF-8 rồi quay lại đầu:

~~~java
ByteBuffer data =
        StandardCharsets.UTF_8.encode("Hello FileChannel");

while (data.hasRemaining()) {
    channel.write(data);
}

channel.position(0);
~~~

Random access trở nên rõ hơn với positional read/write:

~~~java
ByteBuffer part = ByteBuffer.allocate(5);
int read = channel.read(part, 6);
~~~

Overload `read(buffer, position)` đọc từ offset chỉ định nhưng **không thay đổi file position chung của channel**. `write(buffer, position)` có cùng đặc điểm ở chiều ghi.

Khi positional write có thể chỉ ghi một phần buffer, offset của lần sau phải tăng theo số byte đã ghi:

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

Ngoài position, `FileChannel` còn cho biết kích thước và có thể truncate:

~~~java
long size = channel.size();
channel.truncate(100);
~~~

`truncate(100)` làm tệp ngắn lại nếu tệp đang lớn hơn 100 byte. Nó không tự mở rộng tệp nhỏ thành 100 byte.

Khi ứng dụng có yêu cầu về **độ bền dữ liệu (durability)** — mức bảo đảm rằng dữ liệu đã ghi vẫn còn sau sự cố như process/JVM dừng hoặc máy mất điện, tùy khả năng của hệ thống lưu trữ — `force(...)` có thể yêu cầu các thay đổi được đẩy xuống thiết bị lưu trữ:

~~~java
channel.force(true);
~~~

Tham số boolean nói Java có cần yêu cầu đẩy cả metadata hay không: `force(false)` tập trung vào thay đổi nội dung file, còn `force(true)` còn yêu cầu cập nhật metadata cần thiết. Đây là thao tác có chi phí và bảo đảm cuối cùng vẫn phụ thuộc **storage stack**, tức các lớp từ JVM, hệ điều hành, filesystem, cache cho tới thiết bị lưu trữ vật lý. Không nên gọi sau từng ghi nhỏ chỉ vì muốn “an toàn hơn”; yêu cầu bền vững dữ liệu phải được xác định rõ.

`FileChannel.force(...)` cũng không phải API thay thế cho `MappedByteBuffer.force()`. Nếu dữ liệu được sửa thông qua một memory-mapped buffer, chính mapped buffer có API `force()` dành cho việc yêu cầu đẩy thay đổi của mapping xuống storage.

`RandomAccessFile` là API `java.io` cũ hơn cho cùng nhóm bài toán truy cập tệp tại vị trí tùy ý. Nó có một file pointer có thể di chuyển bằng `seek(...)` và còn triển khai `DataInput`/`DataOutput`, nên thuận tiện trong code cũ hoặc khi cần đọc/ghi primitive theo style đó. `FileChannel` phù hợp hơn khi bài toán cần `ByteBuffer`, positional operation không thay đổi shared position, transfer, lock hoặc mapping. `RandomAccessFile.getChannel()` trả về channel gắn với cùng tệp và file position của hai API liên kết với nhau, vì vậy trộn cả hai abstraction trong cùng luồng xử lý cần đặc biệt cẩn thận.

## <a id="asynchronous-filechannel-boundary">Ranh giới của AsynchronousFileChannel</a>

`AsynchronousFileChannel` vẫn là channel dành cho tệp, nhưng operation đọc/ghi của nó được **khởi chạy rồi hoàn tất bất đồng bộ** thay vì buộc caller chờ ngay tại lời gọi `read` hoặc `write`.

Khác với `FileChannel`, API này không dựa vào một mutable file position chung. Mỗi read/write chỉ rõ offset trong file:

~~~java
try (AsynchronousFileChannel channel =
             AsynchronousFileChannel.open(path, StandardOpenOption.READ)) {
    ByteBuffer buffer = ByteBuffer.allocate(4096);
    Future<Integer> pending = channel.read(buffer, 0);

    // Chương trình có thể làm việc khác ở đây.
    int read = pending.get();
}
~~~

`Future<Integer>` là một cách nhận kết quả. Gọi `get()` sẽ chờ nếu operation chưa hoàn tất, nên nếu code gọi `get()` ngay sau `read(...)`, lợi ích của việc tách thời điểm khởi chạy và thời điểm chờ gần như mất đi.

API còn hỗ trợ `CompletionHandler`, nơi Java gọi callback khi operation thành công hoặc thất bại:

~~~java
channel.read(buffer, 0, null,
        new CompletionHandler<Integer, Void>() {
            @Override
            public void completed(Integer count, Void ignored) {
                // dùng kết quả sau khi read hoàn tất
            }

            @Override
            public void failed(Throwable error, Void ignored) {
                // xử lý failure
            }
        });
~~~

Write cũng có hai style tương tự và luôn nhận một file position rõ ràng. Giống channel đồng bộ, số byte hoàn tất có thể nhỏ hơn số byte còn lại trong buffer, nên logic nhiều bước vẫn phải dựa trên count/`position` thực tế thay vì giả định một operation xử lý toàn bộ payload.

Lifecycle vẫn phải rõ ràng: `AsynchronousFileChannel` là `AutoCloseable`; owner phải giữ channel mở đủ lâu cho những operation mà ứng dụng còn cần và đóng nó khi kết thúc. Cũng không được sửa hoặc tái sử dụng vùng `ByteBuffer` đang tham gia một asynchronous operation cho tới khi operation đó đã hoàn tất theo contract của ứng dụng.

Ranh giới lựa chọn:

~~~text
FileChannel
→ caller thực hiện blocking/synchronous file I/O
→ control flow thường đơn giản hơn

AsynchronousFileChannel
→ operation có thể hoàn tất sau
→ phù hợp khi kiến trúc thực sự tận dụng thời gian chờ để làm việc khác
→ completion, cancellation và coordination trở thành một phần của thiết kế
~~~

Asynchronous I/O không tự động làm chương trình nhanh hơn và cũng không tự giải quyết concurrent access. Khi nhiều operation chồng lấn trên cùng vùng file hoặc cùng dữ liệu in-memory, ứng dụng vẫn phải định nghĩa coordination rõ ràng. Executor, callback composition, cancellation, memory visibility và các mô hình concurrency sâu hơn thuộc module concurrency; ở đây điều cần nắm là contract I/O bất đồng bộ và vòng đời của channel/buffer.

## <a id="filechannel-transfer">transferTo và transferFrom</a>

Khi mục tiêu chỉ là chuyển byte giữa các channel, việc tự copy từng chunk qua một `ByteBuffer` trong Java có thể không cần thiết. `FileChannel` cung cấp:

~~~java
long moved = source.transferTo(position, count, target);
~~~

và:

~~~java
long moved = target.transferFrom(source, position, count);
~~~

Một số JVM/hệ điều hành có thể tối ưu đường truyền này để giảm việc copy dữ liệu qua user space. Tuy nhiên, “có thể tối ưu” không phải cam kết rằng mọi môi trường đều dùng zero-copy.

Điều quan trọng hơn về tính đúng đắn: **một lần transfer có thể chuyển ít byte hơn số yêu cầu**. Mã phải đọc giá trị trả về:

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

Điểm của ví dụ là không được coi `moved == 0` như “đã copy xong”. Với loại target có thể tạm thời không nhận dữ liệu, policy thực tế có thể là retry/fallback; với ví dụ file-to-file blocking ở đây, báo lỗi khi không có tiến triển giúp tránh vòng lặp vô hạn và tránh trả về một bản copy thiếu mà tưởng là thành công.

Với copy tệp thông thường, `Files.copy` thường đơn giản và truyền đạt ý định tốt hơn. `transferTo/transferFrom` đáng quan tâm khi code đã làm việc ở tầng channel hoặc khi profiling cho thấy đường truyền dữ liệu là điểm nóng cần tối ưu.

## <a id="file-lock-boundary">Ranh giới của File Lock</a>

**File lock** là một yêu cầu phối hợp với hệ điều hành/filesystem để đánh dấu rằng một JVM/process đang giữ quyền truy cập đã thỏa thuận trên toàn bộ tệp hoặc một vùng byte của tệp. Vai trò chính của nó là giúp **nhiều process phối hợp truy cập cùng một tệp** khi các bên tham gia cùng tôn trọng quy ước lock.

Hai kiểu thường gặp là:

- **exclusive lock**: yêu cầu quyền độc quyền trên vùng đó, nên một lock xung đột khác không nên cùng tồn tại;
- **shared lock**: cho phép nhiều bên cùng giữ shared lock tương thích trên vùng đó khi nền tảng hỗ trợ, nhưng vẫn xung đột với exclusive lock.

Một yêu cầu shared lock **có thể bị nền tảng chuyển thành exclusive lock** nếu hệ điều hành/filesystem không hỗ trợ shared lock. Vì vậy nếu loại lock thực tế quan trọng, kiểm tra `FileLock.isShared()` trên object lock đã nhận được thay vì chỉ nhớ giá trị boolean đã request.

`FileChannel` có thể yêu cầu khóa một vùng tệp:

~~~java
try (FileLock lock = channel.lock()) {
    // thao tác cần phối hợp qua file lock
}
~~~

Có thể khóa một range và yêu cầu shared/exclusive:

~~~java
try (FileLock lock =
             channel.lock(position, size, false)) {
    // false: yêu cầu exclusive lock
}
~~~

Sau khi đã hiểu vai trò phối hợp đó, cần nhớ các ranh giới:

- File lock do Java lấy được giữ thay mặt cho **toàn JVM**, không phải một monitor dành riêng cho một Java thread. Vì vậy không dùng nó thay cho cơ chế đồng bộ giữa các thread trong cùng JVM.
- Cách hệ điều hành/filesystem cưỡng chế hoặc xem lock như advisory có thể khác nhau. Để có tính đúng đắn đa nền tảng, hãy coi đây là cơ chế phối hợp mà các bên tham gia phải tuân thủ.
- Các lock chồng lấn trong cùng JVM có thể gây `OverlappingFileLockException`.
- Lock không biến nhiều thao tác file thành database transaction và không cung cấp transaction nghiệp vụ.
- `FileLock` là tài nguyên có vòng đời; phải release nó, thường bằng try-with-resources.

`tryLock()` cho phép thử lấy lock mà không chờ:

~~~java
FileLock lock = channel.tryLock();

if (lock != null) {
    try (lock) {
        // protected work
    }
}
~~~

Nếu bài toán là distributed coordination giữa service, transaction qua nhiều tài nguyên hoặc đồng bộ dữ liệu nghiệp vụ, file lock không phải abstraction tương ứng. Nó thuộc biên phối hợp quanh một file/channel.

## <a id="memory-mapped-boundary">Ranh giới của Memory-Mapped File</a>

`FileChannel.map` ánh xạ một vùng tệp vào không gian bộ nhớ và trả về `MappedByteBuffer`. Ví dụ dưới đây cố ý chỉ map tối đa một cửa sổ 64 MiB:

Các mode chính là `READ_ONLY` (chỉ đọc), `READ_WRITE` (thay đổi có thể ghi về file) và `PRIVATE` (copy-on-write: thay đổi của process không trở thành thay đổi chung của file).

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

Ở đây, **bộ nhớ ảo (virtual memory)** là cơ chế của hệ điều hành ánh xạ không gian địa chỉ mà process nhìn thấy tới RAM và dữ liệu trên storage. Hệ điều hành quản lý dữ liệu theo các khối gọi là **page**; khi code truy cập một page chưa có sẵn trong RAM, **page fault** xảy ra để hệ điều hành nạp hoặc ánh xạ page cần thiết.

Mental model:

~~~text
tệp trên storage
↕ cơ chế virtual memory của hệ điều hành
MappedByteBuffer
↕
mã Java đọc/ghi như buffer
~~~

Memory mapping có thể hữu ích với tệp lớn cần truy cập ngẫu nhiên nhiều vùng vì hệ điều hành quản lý việc nạp page khi cần. Nhưng chi phí I/O không biến mất; nó chỉ xuất hiện qua cơ chế bộ nhớ ảo, ví dụ page fault khi một page chưa có trong RAM.

Các ranh giới cần nhớ:

- Mapping có offset và kích thước cụ thể; code vẫn phải kiểm soát range.
- Với overload `map(MapMode, long, long)` của Java 21, kích thước một vùng mapping không được lớn hơn `Integer.MAX_VALUE`; file rất lớn có thể cần chia thành nhiều cửa sổ mapping.
- Sau khi tạo thành công, mapping tồn tại độc lập với việc `FileChannel` còn mở hay đã đóng; đóng channel **không tự unmap hoặc làm invalid** `MappedByteBuffer` đã tạo.
- Nếu tệp bị truncate hoặc thay đổi đồng thời bởi thành phần khác, việc truy cập vùng đã map có thể tạo lỗi/hành vi phụ thuộc hệ thống.
- `MappedByteBuffer` không có một API `close()` tiêu chuẩn để application chủ động unmap chính xác tại một dòng lệnh; vòng đời mapping vì thế kém trực tiếp hơn channel.
- Với mapping ghi được, `force()` có thể yêu cầu các thay đổi được ghi xuống storage, nhưng durability thực tế vẫn phải được thiết kế theo yêu cầu của hệ thống.
- Mapping thường phù hợp hơn khi workload thực sự có random access lớn; với file nhỏ hoặc đọc tuần tự, stream/channel thông thường đơn giản hơn.

Memory mapping vì thế là công cụ chuyên biệt, không phải mặc định cho mọi file I/O.

Sau `FileChannel`, ta đã gặp nhiều đối tượng gắn với tài nguyên bên ngoài JVM: stream, channel, directory stream và file lock. Chương tiếp theo tập trung vào câu hỏi thiết kế quan trọng hơn cú pháp: **ai sở hữu tài nguyên và khi nào tài nguyên phải được đóng?**
