# Quản lý vòng đời tài nguyên và an toàn khi lỗi

Một `byte[]` hoặc `String` thông thường sống trong bộ nhớ do JVM quản lý. Stream, channel, `DirectoryStream` hay `FileLock` thì khác: chúng thường đại diện cho một **tài nguyên bên ngoài** như file descriptor (định danh của hệ điều hành cho một tài nguyên I/O đang mở như tệp, socket hoặc pipe) hoặc một **handle gốc (native handle)** tương đương do nền tảng cung cấp.

Vì vậy vòng đời I/O không thể dựa vào suy nghĩ “không còn tham chiếu thì GC sẽ lo”. GC quản lý bộ nhớ Java; mã vẫn cần đóng tài nguyên I/O một cách xác định.

~~~text
mở tài nguyên
→ sử dụng
→ đóng/giải phóng

phải đúng cả khi:
→ thành công
→ return sớm
→ có ngoại lệ
~~~

## <a id="closeable-lifecycle">Closeable và AutoCloseable</a>

Java biểu diễn khả năng “có thể đóng” bằng `AutoCloseable`:

~~~java
public interface AutoCloseable {
    void close() throws Exception;
}
~~~

`Closeable` là dạng chuyên biệt thường gặp trong I/O. Nó kế thừa `AutoCloseable` và quy ước `close()` của nó dùng `IOException`.

Các kiểu quen thuộc như `InputStream`, `OutputStream`, `Reader`, `Writer`, `FileChannel`, `DirectoryStream` và `FileLock` đều có vòng đời cần quan tâm theo API của chúng.

Mô hình cần nhớ:

~~~text
new/open
→ tài nguyên bên ngoài được tạo hoặc thu nhận

đối tượng Java còn dùng được
→ tài nguyên bên ngoài vẫn có thể đang được giữ

close
→ kết thúc quyền sử dụng và giải phóng theo quy ước
~~~

Sau khi đã đóng, đừng tiếp tục sử dụng tài nguyên trừ khi API nói rõ thao tác nào còn hợp lệ. Ví dụ phần lớn thao tác trên stream/channel đã đóng sẽ thất bại.

Với `Closeable`, `close()` được thiết kế để có thể gọi lặp mà không gây hiệu ứng mới. Tuy vậy, điều này không phải quy tắc chung cho mọi phần triển khai của `AutoCloseable`. Thiết kế đúng vẫn là có **một bên sở hữu rõ ràng** đóng tài nguyên đúng một lần trong vòng đời bình thường.

## <a id="resource-ownership">Ai sở hữu và ai đóng tài nguyên?</a>

`try-with-resources` giải quyết **cách đóng**, nhưng trước đó mã phải trả lời **ai có trách nhiệm đóng**.

Một quy ước thiết kế hữu ích:

~~~text
mã tạo/mở tài nguyên
→ mặc định mã đó sở hữu
→ mã đó phải đóng

mã chỉ nhận tài nguyên do bên gọi truyền vào
→ không tự suy đoán quyền sở hữu
→ quy ước phải nói rõ bên nào đóng
~~~

Ví dụ phương thức tự mở tệp:

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

Phương thức này tạo `InputStream` nên quyền sở hữu rõ ràng: chính phương thức đóng nó.

Ngược lại:

~~~java
static void writeMessage(
        OutputStream out,
        String message
) throws IOException {
    out.write(message.getBytes(StandardCharsets.UTF_8));
}
~~~

`out` đến từ bên gọi. Nếu phương thức tự đóng mà quy ước không nói trước, bên gọi có thể mất quyền ghi tiếp vào socket/tệp/response stream. API nên quyết định rõ một trong hai kiểu:

~~~text
tài nguyên mượn (borrowed resource)
→ phương thức dùng nhưng không đóng

tài nguyên được chuyển quyền sở hữu
→ phương thức nhận quyền sở hữu và sẽ đóng
~~~

Quyền sở hữu cũng áp dụng cho các API dễ bị bỏ sót. `Files.list`, `Files.walk`, `Files.find` và `Files.lines` trả về `java.util.stream.Stream` nhưng stream đó gắn với tài nguyên I/O bên dưới; mã tiêu thụ nó phải bảo đảm `close()`, thường bằng `try-with-resources`. “Là Stream API” không có nghĩa “không còn vòng đời tài nguyên”.

Một đối tượng “có thể được GC” không đồng nghĩa tài nguyên đã được giải phóng đúng thời điểm. Với máy chủ chạy lâu, rò rỉ file descriptor có thể tích lũy cho tới khi tiến trình không mở thêm tệp/socket được nữa.

## <a id="try-with-resources-io">Try-with-resources cho I/O</a>

Tự viết `close()` thủ công cho mọi đường chạy rất dễ sai:

~~~java
InputStream in = Files.newInputStream(path);
byte[] data = in.readAllBytes();
in.close();
~~~

Nếu `readAllBytes()` ném ngoại lệ, dòng `close()` không chạy. `try-with-resources` gắn việc đóng tài nguyên vào cấu trúc ngôn ngữ:

~~~java
try (InputStream in = Files.newInputStream(path)) {
    byte[] data = in.readAllBytes();
    System.out.println(data.length);
}
~~~

Khi rời khối lệnh, Java gọi `close()` dù phần xử lý hoàn tất bình thường hay ném ngoại lệ.

Nhiều tài nguyên có thể được khai báo cùng lúc:

~~~java
try (
        InputStream in = Files.newInputStream(source);
        OutputStream out = Files.newOutputStream(target)
) {
    in.transferTo(out);
}
~~~

Chúng được đóng theo thứ tự ngược với thứ tự khai báo: `out` trước, rồi `in`. Quy tắc này đặc biệt hữu ích khi tài nguyên khai báo sau phụ thuộc vào tài nguyên khai báo trước.

Nếu phần xử lý ném ngoại lệ và `close()` cũng ném ngoại lệ, Java giữ ngoại lệ chính và gắn lỗi đóng tài nguyên dưới dạng **ngoại lệ bị nén (suppressed exception)**. Biết quy tắc này giúp đọc log đúng; cơ chế ngoại lệ và ngoại lệ bị nén đầy đủ thuộc module Exception.

Từ Java 9, một biến `final` hoặc effectively final đã tồn tại cũng có thể được dùng trong phần khai báo tài nguyên:

~~~java
InputStream in = Files.newInputStream(path);

try (in) {
    System.out.println(in.read());
}
~~~

Với I/O, `try-with-resources` nên là lựa chọn mặc định khi phạm vi sở hữu tài nguyên có thể biểu diễn rõ bằng một khối lệnh.

## <a id="close-wrapper-chain">Đóng chuỗi lớp bọc</a>

Các chương trước đã dùng các lớp bọc theo kiểu decorator:

~~~text
FileInputStream
→ InputStreamReader
→ BufferedReader
~~~

Các lớp bọc (wrapper) I/O chuẩn thường **lan truyền lời gọi `close()` xuống đối tượng được bọc**, tức lớp bên dưới mà lớp bọc chuyển tiếp thao tác đọc/ghi tới, theo quy ước của API. Quy tắc lan truyền khi đóng này tách biệt với quyền sở hữu ở mức ứng dụng: mã vẫn phải xác định ngay từ đầu mình có trách nhiệm đóng tài nguyên được bọc hay không.

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

Đóng `buffered` sẽ đóng `decoder`, rồi tiếp tục đóng đối tượng bên dưới theo quy ước của các lớp bọc chuẩn. Khi quy ước sở hữu của ứng dụng xác định phạm vi hiện tại sở hữu toàn bộ chuỗi, thường chỉ cần đưa lớp bọc ngoài cùng vào `try-with-resources`.

Ở chiều ghi còn có một lý do khác: lớp bọc có thể giữ dữ liệu chưa được đẩy xuống lớp dưới. `close()` của các lớp bọc `Writer`/`OutputStream` chuẩn thường hoàn tất phần `flush` cần thiết rồi đóng đối tượng được bọc:

~~~java
try (BufferedWriter writer = Files.newBufferedWriter(
        path,
        StandardCharsets.UTF_8
)) {
    writer.write("hello");
}
~~~

Không nên dựa vào việc “tiến trình sắp kết thúc” để thay cho `close()`.

Điểm cần cẩn thận xuất hiện khi lớp bọc bao quanh một tài nguyên mà phương thức **không sở hữu**. Nếu phương thức đóng lớp bọc, lớp bọc thường sẽ đóng luôn đối tượng do bên gọi truyền vào. Đây không phải lỗi của lớp bọc; vấn đề là quy ước sở hữu chưa rõ.

Sau khi đã kiểm soát được vòng đời, ta có thể xem một tính năng I/O khác có ranh giới lớn hơn nhiều: cơ chế tuần tự hóa đối tượng nguyên bản của Java (`Java Object Serialization`). Nó biến cả đồ thị đối tượng thành byte và vì thế kéo theo cả vấn đề tương thích lẫn bảo mật.
