# Quản lý tài nguyên I/O

Một `byte[]` hoặc `String` thông thường sống trong bộ nhớ do JVM quản lý. Stream, channel, directory stream hay file lock thì khác: chúng thường đại diện cho một **tài nguyên bên ngoài** như file descriptor (định danh mà hệ điều hành dùng để theo dõi một file/socket đang mở), socket hoặc handle của hệ điều hành (tham chiếu do hệ điều hành cấp để đại diện cho một tài nguyên).

Vì vậy vòng đời I/O không thể dựa vào suy nghĩ “không còn reference thì GC sẽ lo”. GC quản lý bộ nhớ Java; code vẫn cần đóng tài nguyên I/O một cách xác định.

~~~text
mở tài nguyên
→ sử dụng
→ đóng/release

phải đúng cả khi:
→ thành công
→ return sớm
→ có exception
~~~

## <a id="closeable-lifecycle">Closeable và AutoCloseable</a>

Java biểu diễn khả năng “có thể đóng” bằng `AutoCloseable`:

~~~java
public interface AutoCloseable {
    void close() throws Exception;
}
~~~

`Closeable` là specialization thường gặp trong I/O. Nó extends `AutoCloseable` và hợp đồng `close()` của nó dùng `IOException`.

Các kiểu quen thuộc như `InputStream`, `OutputStream`, `Reader`, `Writer`, `FileChannel`, `DirectoryStream` và `FileLock` đều có lifecycle cần quan tâm theo API của chúng.

Mental model quan trọng:

~~~text
new/open
→ tài nguyên external được acquire

đối tượng Java còn dùng được
→ tài nguyên external vẫn có thể đang được giữ

close
→ kết thúc quyền sử dụng và release theo contract
~~~

Sau khi đã đóng, đừng tiếp tục sử dụng tài nguyên trừ khi API nói rõ operation nào còn hợp lệ. Ví dụ phần lớn thao tác trên stream/channel đã đóng sẽ thất bại.

Với `Closeable`, `close()` được thiết kế để có thể gọi lặp mà không gây hiệu ứng mới. Tuy vậy, điều này không phải quy tắc chung cho mọi implementation của `AutoCloseable`. Thiết kế đúng vẫn là có **một owner rõ ràng** đóng tài nguyên đúng một lần trong lifecycle bình thường.

## <a id="try-with-resources-io">Try-with-resources cho I/O</a>

Viết `close()` thủ công trong mọi đường chạy rất dễ sai:

~~~java
InputStream in = Files.newInputStream(path);
byte[] data = in.readAllBytes();
in.close();
~~~

Nếu `readAllBytes()` ném exception, dòng `close()` không chạy. `try-with-resources` gắn việc đóng tài nguyên vào cấu trúc ngôn ngữ:

~~~java
try (InputStream in = Files.newInputStream(path)) {
    byte[] data = in.readAllBytes();
    System.out.println(data.length);
}
~~~

Khi rời block, Java gọi `close()` dù thân block hoàn tất bình thường hay ném exception.

Nhiều tài nguyên có thể được khai báo cùng lúc:

~~~java
try (
        InputStream in = Files.newInputStream(source);
        OutputStream out = Files.newOutputStream(target)
) {
    in.transferTo(out);
}
~~~

Chúng được đóng theo thứ tự ngược với thứ tự khai báo: `out` trước, rồi `in`. Quy tắc này đặc biệt hữu ích khi tài nguyên sau phụ thuộc vào tài nguyên trước.

Nếu phần xử lý ném exception và `close()` cũng ném exception, Java giữ exception chính và gắn lỗi đóng tài nguyên dưới dạng suppressed exception. Biết quy tắc này giúp đọc log đúng; cơ chế exception/suppressed exception đầy đủ thuộc phần học về exception.

Từ Java 9, một biến final hoặc effectively final đã tồn tại cũng có thể được dùng trong resource header:

~~~java
InputStream in = Files.newInputStream(path);

try (in) {
    System.out.println(in.read());
}
~~~

Với I/O, try-with-resources nên là lựa chọn mặc định khi scope sở hữu tài nguyên có thể biểu diễn bằng lexical block.

## <a id="resource-ownership">Ai sở hữu và ai đóng tài nguyên?</a>

Try-with-resources giải quyết **cách đóng**, nhưng trước đó code phải trả lời **ai có trách nhiệm đóng**.

Một quy ước thiết kế hữu ích:

~~~text
code tạo/mở tài nguyên
→ mặc định code đó sở hữu
→ code đó phải đóng

code chỉ nhận tài nguyên do caller truyền vào
→ không tự suy đoán quyền sở hữu
→ contract phải nói rõ bên nào đóng
~~~

Ví dụ method tự mở file:

~~~java
static long countBytes(Path path) throws IOException {
    try (InputStream in = Files.newInputStream(path)) {
        long count = 0;
        byte[] buffer = new byte[8192];
        int read;

        while ((read = in.read(buffer)) != -1) {
            count += read;
        }

        return count;
    }
}
~~~

Method này tạo `InputStream` nên ownership rõ: method đóng nó.

Ngược lại:

~~~java
static void writeMessage(
        OutputStream out,
        String message
) throws IOException {
    out.write(message.getBytes(StandardCharsets.UTF_8));
}
~~~

`out` đến từ caller. Nếu method tự đóng mà contract không nói trước, caller có thể mất quyền ghi tiếp vào socket/file/response stream. API nên quyết định rõ một trong hai kiểu:

~~~text
borrowed resource
→ method dùng nhưng không đóng

owned/transferred resource
→ method nhận ownership và sẽ đóng
~~~

Ownership cũng áp dụng cho các API dễ bị bỏ sót. `Files.list`, `Files.walk`, `Files.find` và `Files.lines` trả về `java.util.stream.Stream` nhưng stream đó gắn với tài nguyên I/O bên dưới; code tiêu thụ nó phải bảo đảm `close()`, thường bằng try-with-resources. “Là Stream API” không có nghĩa “không còn resource lifecycle”.

Một object “có thể được GC” không đồng nghĩa tài nguyên đã được release đúng thời điểm. Với server chạy lâu, leak file descriptor có thể tích lũy cho tới khi process không mở thêm file/socket được nữa.

## <a id="close-wrapper-chain">Đóng chuỗi wrapper</a>

Các chương trước đã dùng decorator/wrapper:

~~~text
FileInputStream
→ InputStreamReader
→ BufferedReader
~~~

Wrapper thường sở hữu **delegate**, tức đối tượng bên dưới mà wrapper chuyển tiếp thao tác đọc/ghi tới, theo contract của API chuẩn. Vì vậy đóng lớp ngoài cùng sẽ lan xuống chuỗi:

~~~java
try (BufferedReader reader = Files.newBufferedReader(
        path,
        StandardCharsets.UTF_8
)) {
    System.out.println(reader.readLine());
}
~~~

Với một chuỗi tạo thủ công:

~~~java
InputStream file = Files.newInputStream(path);
Reader decoder =
        new InputStreamReader(file, StandardCharsets.UTF_8);
BufferedReader buffered = new BufferedReader(decoder);
~~~

đóng `buffered` sẽ đóng `decoder`, rồi delegate phía dưới theo contract của các wrapper chuẩn. Vì vậy thường chỉ cần để outermost resource làm resource chính trong try-with-resources khi nó sở hữu toàn chuỗi.

Chiều output còn có một lý do khác: wrapper có thể giữ dữ liệu chưa flush. `close()` của writer/output wrapper chuẩn thường hoàn tất phần flush cần thiết rồi đóng delegate:

~~~java
try (BufferedWriter writer = Files.newBufferedWriter(
        path,
        StandardCharsets.UTF_8
)) {
    writer.write("hello");
}
~~~

Không nên dựa vào việc “process sắp kết thúc” để thay cho `close()`.

Điểm cần cẩn thận xuất hiện khi wrapper bọc một resource mà method **không sở hữu**. Nếu method đóng wrapper, wrapper thường sẽ đóng luôn delegate của caller. Đây không phải lỗi của wrapper; đây là lỗi ownership contract chưa rõ.

Sau khi đã kiểm soát được lifecycle, ta có thể xem một tính năng I/O khác có ranh giới lớn hơn nhiều: Java native object serialization. Nó biến cả object graph thành byte và vì thế kéo theo compatibility lẫn security concerns.
