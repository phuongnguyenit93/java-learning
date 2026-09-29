# Lựa chọn I/O API và các lỗi thường gặp

Java có nhiều I/O API vì các bài toán khác nhau cần **mô hình xử lý khác nhau**. Mục tiêu không phải học thuộc một “API mạnh nhất”, mà là chọn lớp đơn giản nhất vẫn biểu diễn đúng loại dữ liệu, kích thước, kiểu truy cập và vòng đời tài nguyên.

Một câu hỏi tốt thường đi theo thứ tự:

~~~text
Đang xử lý byte, text hay binary có cấu trúc?
→ dữ liệu nhỏ có giới hạn hay có thể rất lớn?
→ xử lý tuần tự hay cần random access/Channel?
→ format binary có byte order/framing nào?
→ charset nào?
→ ai sở hữu tài nguyên?
→ nếu có lỗi thì dữ liệu có thể mới xử lý được một phần không?
→ có bằng chứng đo đạc nào cho thấy cần tối ưu hiệu năng không?
~~~

## <a id="choose-stream-reader-channel">Chọn Stream, Reader/Writer, Files hay Channel</a>

Điểm bắt đầu là **ý nghĩa của dữ liệu**, không phải API nào nghe hiện đại hơn.

| Bài toán | Mô hình/API thường phù hợp |
| --- | --- |
| Byte nhị phân tuần tự | `InputStream` / `OutputStream` |
| Primitive binary theo field/schema cố định | `DataInput` / `DataOutput`, thường qua `DataInputStream` / `DataOutputStream` |
| Text theo character | `Reader` / `Writer` |
| Thao tác filesystem ở mức đường dẫn | `Path` + `Files` |
| Cần hiểu capability/storage/provider của path | `Path.getFileSystem()` + `FileSystem` / `FileStore` |
| Theo dõi thay đổi directory | `WatchService` |
| Tệp nhỏ, **có giới hạn kích thước rõ ràng**, cần tiện ích đọc/ghi một lần | `Files.readString`, `writeString`, `readAllBytes`... |
| Byte I/O cần Buffer/Channel model | `Channel` + `ByteBuffer` |
| File cần position, random access, transfer, lock, map | `FileChannel` |
| File cần completion bất đồng bộ theo vị trí | `AsynchronousFileChannel` |

Ví dụ, copy ảnh không nên đi qua `Reader` vì ảnh là byte nhị phân:

~~~java
try (
        InputStream in = Files.newInputStream(source);
        OutputStream out = Files.newOutputStream(target)
) {
    in.transferTo(out);
}
~~~

Đọc text UTF-8 line-by-line phù hợp với `BufferedReader`:

~~~java
try (BufferedReader reader =
             Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
    String line;

    while ((line = reader.readLine()) != null) {
        System.out.println(line);
    }
}
~~~

Khi chỉ cần thao tác cấp filesystem, `Files` thường rõ hơn tự dựng stream:

~~~java
Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
~~~

Khi cần đọc tại offset cụ thể trong file:

~~~java
try (FileChannel channel =
             FileChannel.open(path, StandardOpenOption.READ)) {
    ByteBuffer header = ByteBuffer.allocate(16);
    channel.read(header, 128);
}
~~~

Ví dụ này chỉ minh họa **đọc tại offset**. Nếu contract yêu cầu đủ 16 byte header thì vẫn phải lặp theo số byte thực tế đọc được; trước khi đọc dữ liệu từ `header` bằng `get(...)`, cần `flip()` để chuyển buffer sang read mode.

Quy tắc thực dụng:

~~~text
chọn abstraction theo bài toán
→ giữ solution nhỏ và rõ
→ chỉ xuống tầng thấp hơn khi cần khả năng của tầng đó
~~~

Channel không phải “phiên bản nâng cấp luôn tốt hơn stream”. Reader/Writer không phải “stream cho mọi dữ liệu”. Mỗi mô hình trả lời một loại câu hỏi I/O khác nhau.

Tương tự, `DataInputStream` không thay thế `ObjectInputStream`: nó đọc **primitive theo format mà hai bên đã thống nhất**, còn object serialization mang theo contract object graph/version riêng. `AsynchronousFileChannel` cũng không “nhanh hơn mặc định” `FileChannel`; nó thay đổi cách completion được tổ chức và chỉ hữu ích khi workload/architecture thực sự cần mô hình bất đồng bộ.

Với filesystem, đừng mặc định mọi `Path` đều là cùng một loại đường dẫn OS. Nếu code phụ thuộc POSIX permission, atomic move, `WatchService`, `toFile()` hoặc capability cụ thể, hãy nhớ `Path` thuộc về một `FileSystem`/provider và kiểm tra capability thay vì suy luận từ máy đang chạy lúc development.

## <a id="memory-vs-streaming">Nạp toàn bộ hay xử lý streaming?</a>

Các API tiện lợi như `readAllBytes` và `readString` rất tốt khi input **nhỏ và có giới hạn kích thước rõ ràng**:

~~~java
String config =
        Files.readString(path, StandardCharsets.UTF_8);
~~~

Ưu điểm là code ngắn, dễ hiểu, dễ xử lý toàn bộ nội dung. Nhưng chi phí bộ nhớ cũng rõ:

~~~text
kích thước file N
→ cần giữ xấp xỉ N byte hoặc hơn trong bộ nhớ
→ với text còn có biểu diễn String/char nội bộ
~~~

Nếu file có thể lớn, không tin cậy về kích thước hoặc đến từ nguồn dài liên tục, streaming an toàn hơn về bộ nhớ:

~~~java
try (InputStream in = Files.newInputStream(path)) {
    byte[] buffer = new byte[8192];
    int read;

    while ((read = in.read(buffer)) != -1) {
        process(buffer, 0, read);
    }
}
~~~

Streaming không có nghĩa “không dùng buffer”. Thực tế code thường xử lý **một vùng dữ liệu tạm có kích thước giới hạn** thay vì giữ toàn bộ nội dung cùng lúc.

~~~text
load-all
→ đơn giản
→ phù hợp dữ liệu nhỏ, có giới hạn rõ ràng

streaming
→ lượng bộ nhớ sử dụng được giới hạn theo từng khối
→ phù hợp dữ liệu lớn/không chắc kích thước
→ logic phải xử lý từng khối và trường hợp dữ liệu mới có một phần
~~~

Một lỗi phổ biến là dùng `readAllBytes()` cho file upload chỉ vì lúc thử trên máy cá nhân chỉ có file 20 KB. Khi môi trường thực tế nhận file 2 GB, lựa chọn API đã sai từ đầu.

Hãy xác định **giới hạn kích thước** của dữ liệu trước khi chọn API. “File hiện tại nhỏ” khác với “quy ước của hệ thống đảm bảo file luôn nhỏ”.

## <a id="charset-explicit">Charset phải được thể hiện rõ</a>

Text I/O luôn có một cầu nối:

~~~text
bytes
↔ Charset encode/decode
↔ characters / String
~~~

Nếu dữ liệu là text, charset là một phần của contract dữ liệu. Với format đã thống nhất UTF-8, hãy viết rõ:

~~~java
String text =
        Files.readString(path, StandardCharsets.UTF_8);

Files.writeString(
        target,
        text,
        StandardCharsets.UTF_8
);
~~~

Với stream bridge:

~~~java
try (
        InputStream in = Files.newInputStream(path);
        Reader reader =
                new InputStreamReader(in, StandardCharsets.UTF_8)
) {
    // đọc character theo UTF-8
}
~~~

Trong Java 21, default charset là UTF-8 trừ khi implementation được cấu hình theo cơ chế cho phép thay đổi nó. Tuy vậy, khi file/protocol đã định nghĩa một charset cụ thể, code vẫn nên truyền charset đó rõ ràng: đây là contract của dữ liệu chứ không phải một quyết định nên để phụ thuộc vào default của runtime.

Charset cũng giải thích tại sao không nên convert binary tùy ý thành `String`:

~~~java
byte[] png = Files.readAllBytes(imagePath);
// new String(png, UTF_8) không biến PNG thành text hợp lệ
~~~

Nếu payload là binary, giữ nó ở dạng byte. Chỉ dùng Reader/Writer/String khi payload thực sự là text có encoding xác định.

## <a id="io-error-handling">Xử lý lỗi và thao tác partial</a>

I/O giao tiếp với hệ thống bên ngoài nên lỗi là một phần bình thường của hoạt động: file không tồn tại, không đủ quyền truy cập, ổ đĩa đầy, kết nối đóng, đường dẫn bị thay đổi hoặc filesystem không hỗ trợ một tùy chọn nào đó.

`IOException` và các subtype như `NoSuchFileException`, `AccessDeniedException` giúp code phân biệt nguyên nhân khi thực sự cần cách xử lý khác nhau:

~~~java
try {
    return Files.readString(path, StandardCharsets.UTF_8);
} catch (NoSuchFileException e) {
    // Chính sách của ứng dụng có thể là dùng cấu hình mặc định,
    // báo lỗi rõ ràng cho người dùng, hoặc ném lại exception.
    throw e;
}
~~~

Điểm quan trọng là **không tự động biến “file không tồn tại” thành chuỗi rỗng**, vì như vậy “không có file” và “file tồn tại nhưng rỗng” trở thành cùng một kết quả. Chỉ bắt exception khi code có quyết định hợp lý cho lỗi đó. Việc bắt `IOException` rồi bỏ qua thường biến lỗi I/O thành dữ liệu thiếu hoặc trạng thái khó chẩn đoán.

Một nguyên tắc quan trọng khác là **failure không có nghĩa “không có gì đã xảy ra”**. I/O có thể tiến triển một phần:

~~~text
write 100 KB
→ 40 KB đã được ghi
→ lỗi xảy ra
→ target có thể đã thay đổi
~~~

Channel API làm điều này rất rõ qua số byte trả về. Ví dụ dưới đây giả sử đây là **blocking channel**; nếu là non-blocking channel, `write()` có thể trả `0` và code cần cơ chế chờ readiness thay vì quay vòng liên tục:

~~~java
while (buffer.hasRemaining()) {
    int written = channel.write(buffer);
    if (written <= 0) {
        throw new IOException("Blocking write made no progress");
    }
    // position tăng theo số byte thực tế đã ghi
}
~~~

Copy/move/delete cũng có contract filesystem riêng. Nếu ứng dụng cần publish một file theo kiểu “hoặc version cũ hoặc version mới”, có thể ghi vào temp file rồi dùng atomic move khi filesystem hỗ trợ. Nhưng `ATOMIC_MOVE` cũng có boundary hỗ trợ: nếu không thực hiện được atomically thì có `AtomicMoveNotSupportedException`; khi target đã tồn tại, replace hay fail còn có thể phụ thuộc implementation. Vì vậy policy publish phải nói rõ ứng dụng chấp nhận hành vi nào thay vì giả định `REPLACE_EXISTING` luôn có cùng semantics khi đi kèm atomic move.

Retry cũng cần ngữ nghĩa rõ. Đọc lại từ nguồn có thể đơn giản; retry một write đã tiến triển một phần có thể nhân đôi dữ liệu nếu không biết offset/trạng thái đích. Một thao tác **idempotent** là thao tác có thể lặp lại mà vẫn cho cùng kết quả cuối mong muốn; một thao tác **resumable** là thao tác có thể tiếp tục từ vị trí/trạng thái đã hoàn thành thay vì bắt đầu lại. Đừng thêm retry chung cho mọi `IOException` nếu chưa biết thao tác có các đặc tính này hay không.

Resource cleanup vẫn áp dụng khi lỗi xảy ra, vì vậy ownership và try-with-resources ở chương trước là một phần của error handling I/O, không chỉ là style.

## <a id="io-performance-boundary">Ranh giới tối ưu hiệu năng I/O</a>

I/O thường chậm hơn CPU vì phải đi qua filesystem, storage hoặc network. Điều đó không có nghĩa cứ dùng API phức tạp hơn là nhanh hơn.

Các nguồn chi phí thường gặp:

~~~text
nhiều system call nhỏ
→ buffering có thể giúp

copy dữ liệu qua nhiều tầng
→ channel transfer/direct buffer có thể giúp ở workload phù hợp

load toàn bộ file lớn
→ streaming giảm áp lực bộ nhớ

random access lớn
→ FileChannel/memory mapping có thể phù hợp

flush/force quá thường xuyên
→ tăng chi phí bảo đảm dữ liệu được đẩy xuống storage
~~~

Ở đây **system call** là lời gọi từ chương trình sang hệ điều hành để thực hiện công việc như đọc/ghi file; **memory pressure** là áp lực lên bộ nhớ khi ứng dụng giữ quá nhiều dữ liệu cùng lúc; **contention** là nhiều thread/process cùng cạnh tranh một tài nguyên; còn **durability** nói về mức độ dữ liệu đã được ghi bền vững xuống tầng lưu trữ chứ không chỉ còn trong buffer/cache.

Thứ tự quyết định tốt:

~~~text
1. Chọn abstraction đúng và code đúng.
2. Đặt giới hạn memory/resource rõ ràng.
3. Đo workload thực bằng profiling/benchmark/metrics.
4. Xác định bottleneck thật: CPU, cấp phát bộ nhớ, system call, disk, network hay tranh chấp tài nguyên.
5. Chỉ tối ưu phần đã có bằng chứng.
~~~

Ví dụ, buffer 8 KB không có một con số “tối ưu cho mọi hệ thống”. Kích thước phù hợp phụ thuộc tầng lưu trữ, tải công việc, mức độ xử lý đồng thời và kiểu đọc/ghi. Tương tự, direct buffer, `transferTo` hay memory mapping đều có chi phí riêng.

Với hầu hết code nghiệp vụ, một cách triển khai rõ ràng dựa trên `Files`, buffered stream hoặc Reader/Writer là điểm khởi đầu tốt. Khi phép đo chứng minh đường I/O là điểm nghẽn và chỉ ra nguyên nhân cụ thể, lúc đó mới có đủ thông tin để quyết định Channel, direct buffer, transfer hoặc mapping có đáng dùng hay không.

Toàn bộ module có thể quay về một mental model thống nhất:

~~~text
payload là gì?
→ bytes hay characters

nguồn và đích là gì?
→ file, memory, network, ...

dữ liệu di chuyển theo kiểu nào?
→ tuần tự, buffered, channel, random access

ai sở hữu external resource?
→ ai mở thì thường đóng, trừ khi contract chuyển ownership

boundary nào phải explicit?
→ charset, size, partial operation, failure, compatibility, security
~~~

Chọn I/O tốt nghĩa là làm các quyết định này rõ ràng trong code, rồi chỉ thêm độ phức tạp khi bài toán thực sự cần.
