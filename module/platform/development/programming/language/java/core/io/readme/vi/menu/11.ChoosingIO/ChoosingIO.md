# Chọn cách I/O đơn giản nhưng đúng

Java có nhiều I/O API vì các bài toán khác nhau cần **mô hình xử lý khác nhau**. Mục tiêu không phải học thuộc một “API mạnh nhất”, mà là chọn lớp đơn giản nhất vẫn biểu diễn đúng loại dữ liệu, kích thước, kiểu truy cập và vòng đời tài nguyên.

Một câu hỏi tốt thường đi theo thứ tự:

~~~text
Đang xử lý byte, văn bản hay dữ liệu nhị phân có cấu trúc?
→ dữ liệu nhỏ có giới hạn hay có thể rất lớn?
→ xử lý tuần tự hay cần truy cập ngẫu nhiên/Channel?
→ định dạng nhị phân quy định thứ tự byte/framing nào?
→ charset nào?
→ ai sở hữu tài nguyên?
→ nếu có lỗi thì dữ liệu có thể mới xử lý được một phần không?
→ có bằng chứng đo đạc nào cho thấy cần tối ưu hiệu năng không?
~~~

## <a id="choose-stream-reader-channel">Chọn InputStream/OutputStream, Reader/Writer, Files, Channel hay tài nguyên ClassLoader</a>

Điểm bắt đầu là **ý nghĩa của dữ liệu**, không phải API nào nghe hiện đại hơn.

| Bài toán | Mô hình/API thường phù hợp |
| --- | --- |
| Byte nhị phân tuần tự | `InputStream` / `OutputStream` |
| Giá trị nguyên thủy nhị phân theo trường/lược đồ (schema) cố định | `DataInput` / `DataOutput`, thường qua `DataInputStream` / `DataOutputStream` |
| Văn bản theo ký tự | `Reader` / `Writer` |
| Thao tác hệ thống tệp ở mức đường dẫn | `Path` + `Files` |
| Cần hiểu khả năng, vùng lưu trữ và provider của đường dẫn | `Path.getFileSystem()` + `FileSystem` / `FileStore` |
| Theo dõi thay đổi thư mục | `WatchService` |
| Tệp nhỏ, **có giới hạn kích thước rõ ràng**, cần tiện ích đọc/ghi một lần | `Files.readString`, `writeString`, `readAllBytes`... |
| I/O byte cần mô hình Buffer/Channel | `Channel` + `ByteBuffer` |
| Tệp cần vị trí, truy cập ngẫu nhiên, truyền trực tiếp, khóa hoặc ánh xạ | `FileChannel` |
| Tệp cần hoàn tất bất đồng bộ theo vị trí | `AsynchronousFileChannel` |
| Tài nguyên đóng gói trên classpath/module path | `Class.getResource(...)`, `Class.getResourceAsStream(...)`, `ClassLoader.getResource(...)`, `ClassLoader.getResourceAsStream(...)` |

Ví dụ, sao chép ảnh không nên đi qua `Reader` vì ảnh là byte nhị phân:

~~~java
try (
        InputStream in = Files.newInputStream(source);
        OutputStream out = Files.newOutputStream(target)
) {
    in.transferTo(out);
}
~~~

Đọc văn bản UTF-8 theo từng dòng phù hợp với `BufferedReader`:

~~~java
try (BufferedReader reader =
             Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
    String line;

    while ((line = reader.readLine()) != null) {
        System.out.println(line);
    }
}
~~~

Khi chỉ cần thao tác ở mức hệ thống tệp, `Files` thường rõ hơn việc tự dựng stream:

~~~java
Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
~~~

Khi cần đọc tại offset cụ thể trong tệp:

~~~java
try (FileChannel channel =
             FileChannel.open(path, StandardOpenOption.READ)) {
    ByteBuffer header = ByteBuffer.allocate(16);
    channel.read(header, 128);
}
~~~

Ví dụ này chỉ minh họa **đọc tại offset**. Nếu quy ước yêu cầu đủ 16 byte header thì vẫn phải lặp theo số byte thực tế đọc được; trước khi đọc dữ liệu từ `header` bằng `get(...)`, cần `flip()` để chuyển buffer sang trạng thái đọc.

Quy tắc thực dụng:

~~~text
chọn mức trừu tượng theo bài toán
→ giữ giải pháp nhỏ và rõ
→ chỉ xuống tầng thấp hơn khi cần khả năng của tầng đó
~~~

Channel không phải “phiên bản nâng cấp luôn tốt hơn stream”. Reader/Writer cũng không phải “stream cho mọi dữ liệu”. Mỗi mức trừu tượng trả lời một loại câu hỏi I/O khác nhau.

Tương tự, `DataInputStream` không thay thế `ObjectInputStream`: nó đọc **giá trị nguyên thủy theo định dạng mà hai bên đã thống nhất**, còn tuần tự hóa đối tượng mang theo quy ước riêng về đồ thị đối tượng và phiên bản. `AsynchronousFileChannel` cũng không “nhanh hơn mặc định” `FileChannel`; nó thay đổi cách hoàn tất được tổ chức và chỉ hữu ích khi tải công việc/kiến trúc thực sự cần mô hình bất đồng bộ.

Với hệ thống tệp, đừng mặc định mọi `Path` đều là cùng một loại đường dẫn của hệ điều hành. Nếu mã phụ thuộc quyền POSIX, di chuyển nguyên tử (atomic move), `WatchService`, `toFile()` hoặc một khả năng cụ thể, hãy nhớ `Path` thuộc về một `FileSystem`/provider và kiểm tra khả năng đó thay vì suy luận từ máy phát triển.

**Tài nguyên trên classpath hoặc module path** thuộc một không gian tên khác với hệ thống tệp. `Class.getResourceAsStream(...)` hoặc `ClassLoader.getResourceAsStream(...)` có thể đọc nội dung nằm bên trong JAR hoặc một nguồn khác do class loader quản lý, vì vậy không được giả định tài nguyên tìm thấy theo cách này luôn có thể chuyển thành `File` hoặc `Path` cục bộ. Khi API trả về stream, các quy tắc vòng đời stream thông thường vẫn áp dụng. Quy tắc tra cứu tương đối/gốc, delegation và liệt kê tài nguyên thuộc module **ClassLoader**; ranh giới cần nhớ trong I/O là **tài nguyên classpath không đồng nghĩa với đường dẫn hệ thống tệp**.

## <a id="memory-vs-streaming">Nạp toàn bộ hay xử lý theo luồng?</a>

Các API tiện lợi như `readAllBytes` và `readString` rất tốt khi dữ liệu đầu vào **nhỏ và có giới hạn kích thước rõ ràng**:

~~~java
String config =
        Files.readString(path, StandardCharsets.UTF_8);
~~~

Ưu điểm là mã ngắn, dễ hiểu, dễ xử lý toàn bộ nội dung. Mô hình bộ nhớ quan trọng cần nhớ là **toàn bộ nội dung cần xử lý phải vừa trong bộ nhớ cùng lúc**:

~~~text
readAllBytes(tệp kích thước N)
→ trả về byte[] chứa toàn bộ tệp

readString(tệp)
→ trả về một String chứa toàn bộ văn bản sau giải mã
→ trong lúc giải mã, phần triển khai còn có thể cần bộ đệm byte/ký tự tạm thời
~~~

Nếu tệp có thể lớn, không tin cậy về kích thước hoặc đến từ nguồn dài liên tục, xử lý theo luồng (streaming) an toàn hơn về bộ nhớ:

~~~java
try (InputStream in = Files.newInputStream(path)) {
    byte[] buffer = new byte[8192];
    int read;

    while ((read = in.read(buffer)) != -1) {
        process(buffer, 0, read);
    }
}
~~~

Xử lý theo luồng không có nghĩa “không dùng buffer”. Thực tế mã thường xử lý **một vùng dữ liệu tạm có kích thước giới hạn** thay vì giữ toàn bộ nội dung cùng lúc.

~~~text
nạp toàn bộ
→ đơn giản
→ phù hợp dữ liệu nhỏ, có giới hạn rõ ràng

streaming
→ lượng bộ nhớ sử dụng được giới hạn theo từng khối
→ phù hợp dữ liệu lớn/không chắc kích thước
→ mã phải xử lý từng khối và trường hợp dữ liệu mới có một phần
~~~

Một lỗi phổ biến là dùng `readAllBytes()` cho tệp tải lên chỉ vì lúc thử trên máy cá nhân chỉ có tệp 20 KB. Khi môi trường thực tế nhận tệp 2 GB, lựa chọn API đã sai từ đầu.

Hãy xác định **giới hạn kích thước** của dữ liệu trước khi chọn API. “Tệp hiện tại nhỏ” khác với “quy ước của hệ thống đảm bảo tệp luôn nhỏ”.

## <a id="charset-explicit">Bộ mã ký tự (Charset) phải được thể hiện rõ</a>

Khi văn bản đi qua một **ranh giới dựa trên byte** như tệp hoặc luồng byte, bước mã hóa/giải mã tạo thành cầu nối:

~~~text
bytes
↔ Charset mã hóa/giải mã
↔ ký tự / String
~~~

Với định dạng văn bản được lưu/truyền bằng byte, charset là một phần của quy ước dữ liệu. Nguồn ký tự thuần trong bộ nhớ như `StringReader` không thực hiện bước chuyển đổi byte/charset. Với định dạng tệp đã thống nhất UTF-8, hãy viết rõ:

~~~java
String text =
        Files.readString(path, StandardCharsets.UTF_8);

Files.writeString(
        target,
        text,
        StandardCharsets.UTF_8
);
~~~

Với cầu nối từ byte stream:

~~~java
try (
        InputStream in = Files.newInputStream(path);
        Reader reader =
                new InputStreamReader(in, StandardCharsets.UTF_8)
) {
    // đọc ký tự theo UTF-8
}
~~~

Từ JDK 18, bộ mã ký tự mặc định của các API Java chuẩn là UTF-8, trừ trường hợp phần triển khai được cấu hình theo cơ chế được hỗ trợ để dùng bộ mã khác. Tuy vậy, khi tệp hoặc giao thức đã định nghĩa một bộ mã cụ thể, mã vẫn nên truyền `Charset` đó rõ ràng: đây là quy ước của dữ liệu chứ không phải quyết định nên phụ thuộc vào giá trị mặc định của môi trường chạy.

Charset cũng giải thích tại sao không nên chuyển dữ liệu nhị phân tùy ý thành `String`:

~~~java
byte[] png = Files.readAllBytes(imagePath);
// new String(png, UTF_8) không biến PNG thành văn bản hợp lệ
~~~

Nếu dữ liệu là nhị phân, giữ nó ở dạng byte. Chỉ dùng Reader/Writer/String khi dữ liệu thực sự là văn bản có bộ mã xác định.

## <a id="io-error-handling">Xử lý lỗi và thao tác từng phần</a>

I/O giao tiếp với hệ thống bên ngoài nên lỗi là một phần bình thường của hoạt động: tệp không tồn tại, không đủ quyền truy cập, ổ đĩa đầy, kết nối đóng, đường dẫn bị thay đổi hoặc hệ thống tệp không hỗ trợ một tùy chọn nào đó.

`IOException` và các kiểu con như `NoSuchFileException`, `AccessDeniedException` giúp mã phân biệt nguyên nhân khi thực sự cần cách xử lý khác nhau:

~~~java
try {
    return Files.readString(path, StandardCharsets.UTF_8);
} catch (NoSuchFileException e) {
    // Chính sách của ứng dụng có thể là dùng cấu hình mặc định,
    // báo lỗi rõ ràng cho người dùng, hoặc ném lại ngoại lệ.
    throw e;
}
~~~

Điểm quan trọng là **không tự động biến “tệp không tồn tại” thành chuỗi rỗng**, vì như vậy “không có tệp” và “tệp tồn tại nhưng rỗng” trở thành cùng một kết quả. Chỉ bắt ngoại lệ khi mã có quyết định hợp lý cho lỗi đó. Việc bắt `IOException` rồi bỏ qua thường biến lỗi I/O thành dữ liệu thiếu hoặc trạng thái khó chẩn đoán.

Một nguyên tắc quan trọng khác là **lỗi không có nghĩa “không có gì đã xảy ra”**. I/O có thể tiến triển một phần:

~~~text
write 100 KB
→ 40 KB đã được ghi
→ lỗi xảy ra
→ đích có thể đã thay đổi
~~~

API Channel thể hiện điều này rất rõ qua số byte trả về. Ví dụ dưới đây giả sử đây là **channel dạng blocking**; nếu là channel dạng non-blocking, `write()` có thể trả `0` và mã cần cơ chế chờ trạng thái sẵn sàng thay vì quay vòng liên tục:

~~~java
while (buffer.hasRemaining()) {
    int written = channel.write(buffer);
    if (written <= 0) {
        throw new IOException("Blocking write made no progress");
    }
    // position tăng theo số byte thực tế đã ghi
}
~~~

Sao chép/di chuyển/xóa cũng có quy ước riêng của hệ thống tệp. Nếu ứng dụng cần công bố một tệp theo kiểu “người đọc thấy phiên bản cũ hoặc phiên bản mới”, có thể ghi vào tệp tạm rồi dùng atomic move khi hệ thống tệp hỗ trợ. Nhưng `ATOMIC_MOVE` cũng có giới hạn hỗ trợ: nếu không thực hiện được nguyên tử thì có `AtomicMoveNotSupportedException`; khi đích đã tồn tại, thay thế hay báo lỗi còn có thể phụ thuộc phần triển khai. Vì vậy chính sách công bố phải nói rõ ứng dụng chấp nhận hành vi nào thay vì giả định `REPLACE_EXISTING` luôn có cùng ngữ nghĩa khi đi kèm atomic move.

Thử lại (retry) cũng cần ngữ nghĩa rõ. Đọc lại từ nguồn có thể đơn giản; thử lại một lần ghi đã tiến triển một phần có thể nhân đôi dữ liệu nếu không biết offset/trạng thái đích. Một thao tác **idempotent** là thao tác có thể lặp lại mà vẫn cho cùng kết quả cuối mong muốn; một thao tác **resumable** là thao tác có thể tiếp tục từ vị trí/trạng thái đã hoàn thành thay vì bắt đầu lại. Đừng thêm retry chung cho mọi `IOException` nếu chưa biết thao tác có các đặc tính này hay không.

Việc dọn dẹp tài nguyên vẫn áp dụng khi lỗi xảy ra, vì vậy quyền sở hữu và `try-with-resources` ở chương trước là một phần của xử lý lỗi I/O, không chỉ là phong cách viết mã.

## <a id="io-performance-boundary">Ranh giới tối ưu hiệu năng I/O</a>

I/O thường chậm hơn công việc CPU vì phải đi qua hệ thống tệp, tầng lưu trữ hoặc mạng. Điều đó không có nghĩa cứ dùng API phức tạp hơn là nhanh hơn.

Các nguồn chi phí thường gặp:

~~~text
nhiều lời gọi hệ thống nhỏ
→ buffering có thể giúp

sao chép dữ liệu qua nhiều tầng
→ channel transfer/direct buffer có thể giúp ở tải công việc phù hợp

nạp toàn bộ tệp lớn
→ streaming giảm áp lực bộ nhớ

random access lớn
→ FileChannel/ánh xạ bộ nhớ có thể phù hợp

flush/force quá thường xuyên
→ tăng chi phí bảo đảm dữ liệu được đẩy xuống tầng lưu trữ
~~~

Ở đây **lời gọi hệ thống (system call)** là lời gọi từ chương trình sang hệ điều hành để thực hiện công việc như đọc/ghi tệp; **áp lực bộ nhớ (memory pressure)** là chi phí/rủi ro khi ứng dụng giữ quá nhiều dữ liệu cùng lúc; **tranh chấp tài nguyên (contention)** là nhiều thread/tiến trình cùng cạnh tranh một tài nguyên; còn **độ bền dữ liệu (durability)** nói về mức độ dữ liệu đã được đẩy đủ sâu xuống tầng lưu trữ để tồn tại qua các sự cố mà ứng dụng quan tâm.

Thứ tự quyết định tốt:

~~~text
1. Chọn mức trừu tượng đúng và viết mã cho đúng.
2. Đặt giới hạn bộ nhớ/tài nguyên rõ ràng.
3. Đo tải công việc thực bằng phân tích hiệu năng (profiling), benchmark hoặc số liệu vận hành.
4. Xác định điểm nghẽn thật: CPU, cấp phát bộ nhớ, lời gọi hệ thống, đĩa, mạng hay tranh chấp tài nguyên.
5. Chỉ tối ưu phần đã có bằng chứng.
~~~

Ví dụ, bộ đệm 8 KB không có một con số “tối ưu cho mọi hệ thống”. Kích thước phù hợp phụ thuộc tầng lưu trữ, tải công việc, mức độ xử lý đồng thời và kiểu đọc/ghi. Tương tự, bộ đệm trực tiếp (direct buffer), `transferTo` hay ánh xạ bộ nhớ đều có chi phí riêng.

Với hầu hết mã nghiệp vụ, một cách triển khai rõ ràng dựa trên `Files`, luồng có bộ đệm hoặc Reader/Writer là điểm khởi đầu tốt. Khi phép đo chứng minh đường I/O là điểm nghẽn và chỉ ra nguyên nhân cụ thể, lúc đó mới có đủ thông tin để quyết định Channel, bộ đệm trực tiếp, truyền trực tiếp hoặc ánh xạ có đáng dùng hay không.

Toàn bộ module có thể quay về một mô hình thống nhất:

~~~text
dữ liệu là gì?
→ byte hay ký tự

nguồn và đích là gì?
→ tệp, bộ nhớ, mạng, ...

dữ liệu di chuyển theo kiểu nào?
→ tuần tự, có bộ đệm, channel, truy cập ngẫu nhiên

ai sở hữu tài nguyên bên ngoài?
→ ai mở thì thường đóng, trừ khi quy ước chuyển quyền sở hữu

ranh giới nào phải được nói rõ?
→ charset, kích thước, thao tác từng phần, lỗi, tương thích, bảo mật
~~~

Chọn I/O tốt nghĩa là làm các quyết định này rõ ràng trong mã, rồi chỉ thêm độ phức tạp khi bài toán thực sự cần.
