<a id="back-to-top"></a>

# Hiệu năng và tệp lớn

## Menu
- [Xử lý từng khối thay vì đưa toàn bộ tệp vào bộ nhớ](#performance-and-large-files-001)
- [Chọn kích thước buffer theo phép đo](#performance-and-large-files-002)
- [Chi phí syscall, chuyển đổi charset và số lớp bọc](#performance-and-large-files-003)
- [So sánh stream có bộ đệm, Files.copy và FileChannel](#performance-and-large-files-004)
- [Heap buffer, direct memory và áp lực bộ nhớ](#performance-and-large-files-005)
- [Giới hạn đọc song song và thứ tự ghép kết quả](#performance-and-large-files-006)
- [Đo throughput, latency và mức sử dụng bộ nhớ](#performance-and-large-files-007)
- [Ảnh hưởng của cache hệ điều hành khi benchmark](#performance-and-large-files-008)
- [Không mặc định NIO hoặc memory mapping luôn nhanh hơn](#performance-and-large-files-009)
- [Giới hạn file descriptor và số tài nguyên đang mở](#performance-and-large-files-010)

## <a id="performance-and-large-files-001">Xử lý từng khối thay vì đưa toàn bộ tệp vào bộ nhớ</a>

<details>
<summary>Xem chi tiết</summary>

# Xử lý tệp lớn theo từng khối thay vì đưa toàn bộ tệp vào bộ nhớ (Java Core I/O)

## 1. Vấn đề: tệp lớn hơn ngân sách bộ nhớ

Khi tệp chỉ vài kilobyte, cách đọc toàn bộ nội dung có thể rất tiện:

~~~java
byte[] bytes = Files.readAllBytes(path);
String text = Files.readString(path, StandardCharsets.UTF_8);
~~~

Đây là **đoạn minh họa**, với `path` là `Path` đã có và cần import `Files`, `StandardCharsets`. Hai lời gọi trên thuận tiện cho tệp nhỏ có giới hạn rõ ràng; nhưng không nên tự động áp dụng cho file vài gigabyte hoặc dữ liệu đầu vào không tin cậy.

Khi đọc toàn bộ vào `byte[]`, chương trình cần một mảng đủ chứa nội dung tệp và có thể cần thêm các bản sao trong quá trình giải mã, chuyển đổi hoặc xử lý. Một `byte[]` dùng chỉ số `int`; việc nạp tệp rất lớn vào một mảng cũng bị giới hạn bởi kích thước mảng và heap JVM. `Files.readAllBytes` có thể ném `OutOfMemoryError` hoặc `IOException` với tệp quá lớn; `Files.readString` cũng cần materialize cả chuỗi nội dung trong bộ nhớ.

**Cách tiếp cận theo khối (chunked/streaming I/O)** là chỉ giữ một vùng đệm có kích thước giới hạn, ví dụ 8 KiB, 64 KiB hoặc 1 MiB; đọc dữ liệu vào đó, xử lý/ghi ra, rồi tái sử dụng vùng đệm. Khi thiết kế đúng, bộ nhớ đệm I/O **không tăng theo tổng kích thước tệp**.

Bài này giúp bạn:

- So sánh mô hình đọc toàn bộ và đọc theo khối, nhận diện ưu/nhược điểm.
- Viết vòng lặp đọc/ghi Java I/O chuẩn cho tệp lớn, xử lý EOF và số byte thực tế.
- Phân biệt `InputStream`/`OutputStream` với `FileChannel`/`ByteBuffer`, nhất là ghi từng phần.
- Chọn buffer và cơ chế chuyển dữ liệu phù hợp workload.
- Tránh lỗi tràn số, mất dữ liệu ở chunk cuối, hỏng UTF-8, tích lũy bộ nhớ ngầm và thiếu kiểm soát tài nguyên.
- Hiểu khi nào streaming đơn thuần chưa đủ, chẳng hạn thuật toán cần random access hoặc sắp xếp toàn bộ dữ liệu.

## 2. Hai mô hình xử lý

### 2.1. Đọc toàn bộ rồi xử lý

~~~text
Tệp trên đĩa: [................................ nhiều GB ................................]
                            |
                            v
                       readAllBytes()
                            |
                            v
Heap:              [......... toàn bộ file .........]
                            |
                            v
                      Xử lý / ghi ra
~~~

Các bước thường gặp là đọc tất cả byte, tạo đối tượng biểu diễn nội dung, biến đổi rồi ghi ra. Ưu điểm: mã có thể ngắn, dễ truy cập ngẫu nhiên trong mảng đối với dữ liệu đủ nhỏ. Nhược điểm: peak heap và thời gian chờ trước khi có dữ liệu để xử lý tăng theo kích thước file.

Một bộ chuyển đổi vô ý giữ `byte[]` đầu vào, `String` trung gian và `byte[]` đầu ra cùng lúc có thể làm nhu cầu RAM lớn hơn nhiều lần kích thước tệp. Không có công thức nhân cố định cho mọi chương trình; phụ thuộc encoding, cấu trúc đối tượng, thuật toán và GC.

### 2.2. Đọc từng khối rồi xử lý/ghi ngay

~~~text
Tệp nguồn [block 1][block 2][block 3]...[block N]
             |          |         |          |
             v          v         v          v
         [buffer 8 KiB tái sử dụng] ---> [tệp đích / consumer]
~~~

Đường đi điển hình:

1. Cấp phát một `byte[]` hoặc `ByteBuffer` có dung lượng cố định.
2. Đọc tối đa một lượng byte vào buffer.
3. Sử dụng **đúng lượng byte được báo đã đọc**, không phải luôn cả kích thước buffer.
4. Ghi, kiểm tra hoặc biến đổi dữ liệu.
5. Lặp lại đến EOF; đóng luồng/kênh và xử lý lỗi.

Nếu chỉ dùng một buffer 8 KiB, việc tệp dài 1 MiB hay 100 GiB **không buộc buffer đó lớn thêm**. Tuy nhiên, bộ nhớ toàn chương trình còn bao gồm JVM, thư viện, page cache, hàng đợi, dữ liệu do nghiệp vụ tích lũy; streaming **không tự động bảo đảm** tổng RAM luôn thấp nếu mỗi khối bị giữ lại trong `List` hoặc cache không giới hạn.

## 3. API thường dùng trong Java 17+

| API | Đặc điểm | Khi nào cân nhắc |
| --- | --- | --- |
| `Files.newInputStream(path)` | Mở `InputStream` đọc byte từ tệp | Đọc tuần tự theo khối, tích hợp parser/transform |
| `Files.newOutputStream(path, options...)` | Mở `OutputStream` ghi byte | Ghi tuần tự, sao chép/biến đổi |
| `BufferedInputStream`/`BufferedOutputStream` | Tăng buffering cho nhiều thao tác nhỏ | Khi nguồn/đích có nhiều lời gọi I/O byte nhỏ |
| `FileChannel.read/write(ByteBuffer)` | API NIO với `ByteBuffer`, hỗ trợ offset/channel | Khi cần random access, position hoặc kiểm soát buffer |
| `FileChannel.transferTo/transferFrom` | Chuyển dữ liệu giữa channel | Sao chép/đẩy dữ liệu khối lớn; phải kiểm tra số byte thực tế |
| `Files.copy(source, destination, options...)` | API sao chép tệp mức cao | Khi chỉ cần copy, không cần xử lý từng chunk |
| `BufferedReader`/`Files.newBufferedReader` | Đọc văn bản theo ký tự/dòng | Khi parser logic hoạt động theo dòng; cần giới hạn độ dài dòng |
| `Files.lines(path)` | Trả về stream dòng đọc lazily | Khi xử lý tuần tự các dòng; đóng stream, tránh terminal operations tích lũy |

**Chọn công cụ theo yêu cầu**, không mặc định `FileChannel` luôn nhanh hơn stream hoặc `transferTo` luôn là zero-copy. Chúng có các chi phí, tối ưu hệ điều hành và hành vi khác nhau.

## 4. Vòng lặp byte stream đúng

Mẫu phổ biến:

~~~java
byte[] buffer = new byte[8192];
long totalBytes = 0;

try (InputStream in = Files.newInputStream(source);
     OutputStream out = Files.newOutputStream(
             target, StandardOpenOption.CREATE_NEW,
             StandardOpenOption.WRITE)) {

    int n;
    while ((n = in.read(buffer)) != -1) {
        if (n == 0) {
            throw new IOException("Không có tiến triển khi đọc file");
        }
        out.write(buffer, 0, n);
        totalBytes += n;
    }
}
~~~

Đây là **đoạn minh họa** với `source`/`target` là `Path` đã có, cần import `InputStream`, `OutputStream`, `Files`, `StandardOpenOption` và `IOException`. `target` phải chưa tồn tại do dùng `CREATE_NEW`. Chương trình chạy đầy đủ sẽ xuất hiện ở phần 7.

Các điểm thiết yếu:

- `in.read(buffer)` trả **số byte thực tế** trong khoảng cho phép, hoặc `-1` khi hết luồng.
- Một lời gọi đọc **không** bảo đảm điền đầy 8192 byte; có thể chỉ đọc vài trăm byte dù tệp còn dữ liệu.
- `out.write(buffer, 0, n)` dùng đúng `n` byte. Nếu cứ ghi `buffer.length` sẽ đưa byte cũ của lần đọc trước vào đầu ra ở lần đọc cuối.
- Với `OutputStream` đồng bộ, `write(byte[],off,len)` thành công nghĩa là phương thức đã ghi đủ `len` byte theo hợp đồng của API; khi không thể hoàn thành, nó ném `IOException`. Không cần vòng lặp ghi từng phần **cho chính lời gọi OutputStream.write này**; nhưng API channel có hợp đồng khác.
- `long totalBytes` cho phép đếm lượng dữ liệu lớn hơn 2 GiB; trong các vòng lặp xử lý rất dài còn phải cân nhắc overflow của `long` và quota nghiệp vụ.
- `try-with-resources` đóng cả đầu vào và đầu ra kể cả khi gặp exception. Đóng/flush và độ bền trên thiết bị lưu trữ là những vấn đề riêng nếu ứng dụng cần bảo đảm dữ liệu sống sót qua sự cố điện.

### EOF và số byte 0

Với `InputStream.read(byte[])` và buffer có chiều dài dương, một luồng file blocking thông thường tiến triển bằng số byte dương hoặc `-1`. Tuy nhiên, mã làm việc qua một wrapper/provider bất thường không nên biến kết quả `0` thành vòng lặp quay vô hạn; ví dụ trên chọn báo lỗi.

**Không dùng `in.available() == 0` để nhận biết EOF.** `available()` chỉ ước lượng lượng byte có thể đọc mà không chặn, **không phải** số byte còn lại của tệp, cũng không phải điều kiện kết thúc chính xác cho luồng bất kỳ.

**Không dùng `read(...) == buffer.length` để đoán rằng mọi file đã đọc xong.** Mỗi lần đọc có thể trả ít byte; chỉ `-1` là EOF với `InputStream.read`.

## 5. Vì sao khối cuối rất dễ bị ghi sai?

Giả sử tệp có 20.003 byte và buffer dài 8.192 byte. Tùy hành vi đọc, một kịch bản có thể là:

~~~text
Đọc 1: 8192 byte --> ghi 8192
Đọc 2: 8192 byte --> ghi 8192
Đọc 3: 3619 byte --> ghi 3619
Đọc 4: -1        --> kết thúc
Tổng: 20003 byte
~~~

**Không bảo đảm** ba lần đọc đầu đúng như ví dụ: API có thể trả các khối ngắn hơn. Tuy nhiên, tổng các lần đọc dương phải bằng số byte mà ứng dụng thực sự đã nhận trước EOF. Không được suy ra lượng byte từ số vòng lặp nhân kích thước buffer.

Lỗi thường thấy:

~~~java
int n = in.read(buffer);
if (n > 0) {
    out.write(buffer); // SAI: ghi luôn 8192 byte, kể cả byte cũ.
}
~~~

Sửa thành:

~~~java
int n = in.read(buffer);
if (n > 0) {
    out.write(buffer, 0, n); // Chỉ ghi byte vừa đọc.
}
~~~

Hai đoạn này là **minh họa**, không phải chương trình độc lập. Với vòng lặp thật phải xử lý `-1` và trường hợp lỗi theo API.

## 6. Chọn kích thước buffer: không có con số thần kỳ

Một số kích thước thường thử khi benchmark: 4 KiB, 8 KiB, 64 KiB, 256 KiB hoặc 1 MiB. Không có quy tắc “buffer càng lớn càng tốt”.

| Buffer | Lợi ích tiềm năng | Chi phí/điểm cân nhắc |
| --- | --- | --- |
| Rất nhỏ (vài chục byte) | Ít heap cho từng luồng | Rất nhiều lời gọi đọc/ghi, overhead cao |
| 4–16 KiB | Dễ dùng, ít bộ nhớ cho mỗi thao tác | Có thể chưa tối ưu với throughput cao |
| 64–256 KiB | Ít vòng đọc/ghi hơn trong copy tuần tự | Tăng bộ nhớ theo số tác vụ đồng thời |
| 1 MiB trở lên | Có thể có ích trong một số workload lớn | Tốn RAM nếu hàng nghìn request cùng giữ buffer |

Ví dụ 2.000 tác vụ đồng thời, mỗi tác vụ giữ một buffer 1 MiB, riêng các buffer đã có thể chiếm xấp xỉ **2.000 MiB** (~1,95 GiB), chưa kể phần khác. Nếu mỗi buffer là 8 KiB thì tổng buffer khoảng **15,6 MiB**. Vì vậy phải tính ngân sách theo **buffer size × concurrency × số buffer/tác vụ**.

Benchmark cần cố định dữ liệu, môi trường, thiết bị lưu trữ, tình trạng page cache và mức độ đồng thời. Hãy đo throughput, CPU, garbage collection, heap/RSS, thời gian P95/P99 thay vì chỉ một lần chạy có cache nóng.

### Buffering lớp ngoài

`BufferedInputStream` hoặc `BufferedOutputStream` hữu ích khi nhiều lời gọi đọc/ghi nhỏ (ví dụ từng byte) gây overhead. Nếu đã tự gọi `in.read(buffer)` bằng các khối 64 KiB, thêm một lớp buffer nữa không đương nhiên cải thiện hiệu năng; đôi khi chỉ tạo thêm một lớp copy. Đo trên workload thật.

### Có nên cấp phát buffer trong mỗi vòng lặp?

Tránh:

~~~java
while (moreData) {
    byte[] data = new byte[8192]; // Cấp phát lại liên tục.
    // Đọc và xử lý.
}
~~~

Đây là **mẫu phản ví dụ** với `moreData` tượng trưng cho điều kiện của vòng lặp. Cấp phát mới trên mỗi lần đọc tạo áp lực GC không cần thiết. Với tác vụ đơn giản, cấp phát buffer **một lần trước vòng lặp** rồi tái sử dụng.

Nếu dùng pool buffer giữa nhiều tác vụ, phải xác định rõ ownership. Không cấp cùng buffer mutable cho hai thread đang đọc/ghi đồng thời nếu không có điều phối an toàn.

## 7. Ví dụ Java 17+ hoàn chỉnh và kiểm chứng được

Chương trình tạo một tệp nguồn chứa **12 khối 8.192 byte chữ A** và **3 byte XYZ**, tổng cộng **98.307 byte**. Sau đó nó sao chép sang tệp mới bằng buffer 8 KiB, đếm số byte sao chép, kiểm tra nội dung đích và xóa dữ liệu tạm khi kết thúc.

Để stdout không phụ thuộc việc `InputStream.read` tùy môi trường trả các khối ngắn hay dài, chương trình chỉ in **tổng byte**, **kích thước buffer** và **so sánh nội dung**; không cam kết số lần đọc cố định.

Lưu là **`ChunkedFileCopyDemo.java`**:

~~~java
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

public class ChunkedFileCopyDemo {
    private static final int BUFFER_SIZE = 8192;

    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("chunked-copy-");
        Path source = directory.resolve("source.bin");
        Path target = directory.resolve("target.bin");

        try {
            makeSource(source);
            long copied = copyByChunks(source, target);

            System.out.println("Source bytes: " + Files.size(source));
            System.out.println("Copied bytes: " + copied);
            System.out.println("Buffer bytes: " + BUFFER_SIZE);
            System.out.println("Content equal: "
                    + (Files.mismatch(source, target) == -1));
        } finally {
            Files.deleteIfExists(target);
            Files.deleteIfExists(source);
            Files.deleteIfExists(directory);
        }
    }

    private static void makeSource(Path source) throws IOException {
        byte[] block = new byte[BUFFER_SIZE];
        Arrays.fill(block, (byte) 'A');

        try (OutputStream out = Files.newOutputStream(
                source,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
            for (int i = 0; i < 12; i++) {
                out.write(block);
            }
            out.write("XYZ".getBytes(StandardCharsets.US_ASCII));
        }
    }

    private static long copyByChunks(Path source, Path target)
            throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long total = 0;

        try (InputStream in = Files.newInputStream(source);
             OutputStream out = Files.newOutputStream(
                     target,
                     StandardOpenOption.CREATE_NEW,
                     StandardOpenOption.WRITE)) {

            int n;
            while ((n = in.read(buffer)) != -1) {
                if (n == 0) {
                    throw new IOException("No progress while reading");
                }
                out.write(buffer, 0, n);
                total = Math.addExact(total, n);
            }
        }
        return total;
    }
}
~~~

Lệnh biên dịch và chạy:

~~~bash
javac -encoding UTF-8 ChunkedFileCopyDemo.java
java ChunkedFileCopyDemo
~~~

**Stdout kỳ vọng khi tệp tạm tạo và sao chép thành công:**

~~~text
Source bytes: 98307
Copied bytes: 98307
Buffer bytes: 8192
Content equal: true
~~~

### Giải thích chi tiết

1. `makeSource` ghi 12 × 8.192 = 98.304 byte `A`, rồi thêm đúng ba byte ASCII `XYZ`. Tổng là 98.307 byte; nguồn được tạo trên đĩa bằng nhiều lần ghi, không phải bằng mảng 98.307 byte.
2. `copyByChunks` cấp phát **một** `byte[8192]`. Kích thước file không ảnh hưởng dung lượng buffer; chương trình tái sử dụng nó sau mỗi lần ghi xong.
3. `in.read(buffer)` có thể trả số byte bất kỳ phù hợp hợp đồng API. `out.write(buffer, 0, n)` đảm bảo không ghi byte cũ còn sót trong buffer, đặc biệt ở khối cuối.
4. `Math.addExact(total, n)` phát hiện tràn số `long` bằng exception thay vì âm thầm vòng về số âm. Với tệp demo ngắn, không thể tràn.
5. `CREATE_NEW` từ chối ghi đè một tệp đích đã tồn tại.
6. `Files.mismatch(source, target) == -1` xác nhận nội dung byte của hai tệp giống nhau sau khi đóng các luồng. Thao tác này đọc thêm dữ liệu để so sánh, phù hợp kiểm tra demo nhưng có chi phí I/O cho tệp sản xuất rất lớn.
7. `finally` dọn hai tệp và thư mục tạm sau khi hoàn tất. Nếu I/O lỗi, phương thức sẽ ném exception; chương trình không in một kết quả thành công giả.
8. Khối dữ liệu cuối có thể được trả về trong một hoặc nhiều lần `read`; **stdout vẫn giống nhau** do cách đếm byte thực tế và so sánh nội dung, không phải vì API bảo đảm lần đọc cuối đúng 3 byte.

## 8. Phiên bản dùng FileChannel và ByteBuffer

`FileChannel` phù hợp khi cần thao tác theo vị trí, đọc/ghi ngẫu nhiên hoặc dùng `ByteBuffer` trong NIO. Mẫu streaming tuần tự:

~~~java
ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);
long total = 0;

try (FileChannel src = FileChannel.open(source, StandardOpenOption.READ);
     FileChannel dst = FileChannel.open(
             target, StandardOpenOption.CREATE_NEW,
             StandardOpenOption.WRITE)) {

    while (src.read(buffer) != -1) {
        buffer.flip(); // Đọc byte vừa nhận từ buffer để ghi đi.

        while (buffer.hasRemaining()) {
            int n = dst.write(buffer);
            if (n == 0) {
                throw new IOException("Channel write made no progress");
            }
            total = Math.addExact(total, n);
        }
        buffer.clear(); // Sẵn sàng nhận khối tiếp theo.
    }
}
~~~

Đây là **đoạn minh họa**, cần các import `java.nio.ByteBuffer`, `java.nio.channels.FileChannel`, `java.nio.file.Files`, `java.nio.file.Path`, `StandardOpenOption` và `java.io.IOException`; `source`/`target` là các `Path` đã có. Để dùng trong môi trường có provider đặc biệt, cũng nên xử lý `src.read(...) == 0` theo chính sách không tiến triển thay vì vòng lặp nóng.

Khác biệt quan trọng so với `OutputStream`:

- `FileChannel.write(ByteBuffer)` có thể trả về **ít hơn** `buffer.remaining()`; vòng `while (buffer.hasRemaining())` đảm bảo đẩy hết khối vừa đọc trước khi tái sử dụng buffer.
- `ByteBuffer.flip()` chuyển từ trạng thái ghi dữ liệu vào buffer sang trạng thái đọc dữ liệu ra để gửi đi.
- `ByteBuffer.clear()` đặt lại `position/limit`, **không xóa byte**. Chỉ làm sau khi toàn bộ khối đã được ghi xong.
- Nếu `FileChannel.read(buffer)` trả `0`, `buffer.flip()` có thể tạo vùng không còn byte; chương trình ví dụ không busy-spin vì một lần ghi vòng trong không chạy, nhưng vòng ngoài có thể tiếp tục ngay. Với provider bất thường hoặc non-blocking channel, phải có chính sách backoff/readiness tương ứng.

### Có nên dùng direct buffer?

`ByteBuffer.allocateDirect(size)` tạo vùng bộ nhớ trực tiếp ngoài Java heap theo khả năng JVM/provider và có thể giảm một số copy khi I/O native. Đổi lại, allocation/cleanup có chi phí và bộ nhớ native vẫn cần được giám sát. Không tạo direct buffer mới ở mỗi vòng đọc; nên tái sử dụng theo vòng đời phù hợp. Không mặc định direct buffer nhanh hơn heap buffer trên mọi nền tảng.

## 9. FileChannel.transferTo/transferFrom và tệp lớn

Nếu mục tiêu chỉ là chuyển byte từ tệp sang channel khác, có thể cân nhắc:

~~~java
long remaining = sourceChannel.size();
long offset = 0;

while (remaining > 0) {
    long n = sourceChannel.transferTo(
            offset, remaining, destinationChannel);
    if (n == 0) {
        throw new IOException("Transfer made no progress");
    }
    offset += n;
    remaining -= n;
}
~~~

Đây là **đoạn minh họa**, `sourceChannel` là `FileChannel` nguồn đã mở, `destinationChannel` là `WritableByteChannel` đích đã mở, cần `IOException`. Gọi một lần với `count` lớn **không bảo đảm** chuyển đủ byte: hệ điều hành có thể giới hạn mỗi lần chuyển, EOF có thể đến sớm và đích có thể chịu backpressure.

Một số provider dùng kỹ thuật giảm copy (thường gọi là *zero-copy*) cho những cặp channel nhất định, nhưng Java không cam kết mọi trường hợp `transferTo` đều zero-copy. Cũng phải cân nhắc nguồn bị thay đổi trong lúc copy và đích không được chứa dữ liệu cũ còn thừa.

`Files.copy(source, target)` là lựa chọn đơn giản hơn khi không cần xử lý từng khối; ưu tiên API mức cao trước khi tự tối ưu nếu yêu cầu nghiệp vụ chỉ là sao chép.

## 10. Xử lý tệp văn bản theo khối: ranh giới byte khác ranh giới ký tự

Sao chép byte thuần có thể chia tùy ý: khi ghép đúng thứ tự, byte đầu ra sẽ vẫn giống đầu vào. Nhưng **giải mã văn bản** có ràng buộc mới.

UTF-8 biểu diễn một ký tự bằng 1–4 byte. Ví dụ chữ có dấu hoặc emoji có thể bị chia làm hai phần ở ranh giới giữa hai khối. Nếu gọi:

~~~java
String text = new String(buffer, 0, n, StandardCharsets.UTF_8);
~~~

trên **từng chunk độc lập**, bạn có thể giải mã một phần ký tự thành ký tự thay thế hoặc dữ liệu không chính xác. Đây là đoạn minh họa cho lỗi tiềm ẩn nếu byte UTF-8 bị cắt ngang.

### Các cách đúng theo bài toán

**Cách A — xử lý văn bản theo dòng bằng Reader:**

~~~java
try (BufferedReader reader = Files.newBufferedReader(
        path, StandardCharsets.UTF_8)) {
    String line;
    while ((line = reader.readLine()) != null) {
        process(line);
    }
}
~~~

Đây là đoạn minh họa với `path` và phương thức `process(String)` do ứng dụng định nghĩa. `BufferedReader` quản lý giải mã ký tự xuyên qua các lần đọc byte bên trong; tuy nhiên `readLine()` vẫn xây dựng **cả một dòng thành `String`**. Một dòng dài hàng gigabyte vẫn có nguy cơ hết bộ nhớ. Nếu đầu vào không tin cậy, áp giới hạn chiều dài dòng hoặc dùng parser streaming giữ state hữu hạn.

**Cách B — xử lý định dạng nhị phân hoặc văn bản tự định nghĩa theo chunk:** dùng `CharsetDecoder` với `CoderResult` và giữ lại byte chưa giải mã ở cuối buffer bằng `compact()` hoặc state tương ứng. Chỉ công bố các ký tự đã giải mã hợp lệ; quản lý EOF và dữ liệu không đầy đủ cuối luồng. Cách này phức tạp hơn nhưng phù hợp khi cần kiểm soát chặt mức RAM, framing hay chính sách lỗi encoding.

**Cách C — chỉ sao chép nguyên byte:** không cần giải mã; dùng `InputStream`/`OutputStream` hoặc `FileChannel`. Đây là lựa chọn đơn giản, tránh việc biến đổi Unicode ngoài ý muốn.

### Ranh giới record có thể bắc qua chunk

Ngay cả khi dữ liệu là ASCII, một dòng CSV, khối JSON hoặc record nhị phân có thể bị cắt giữa hai lần đọc. Parser phải giữ **phần chưa xử lý** từ khối trước và nối với khối sau, hoặc thiết kế state machine đọc token/record tăng dần.

Nếu một record đơn lẻ có thể lớn không giới hạn, chỉ dùng buffer cố định không đủ để đảm bảo RAM: phải áp `MAX_RECORD_BYTES`, xuất record ra chỗ lưu tạm, hoặc thay đổi giao thức/thuật toán.

## 11. Xử lý lớn không chỉ là sao chép: checksum, nén, chuyển đổi

### 11.1. Tính SHA-256 streaming

Có thể tính hash mà không nạp toàn file:

~~~java
MessageDigest digest = MessageDigest.getInstance("SHA-256");
byte[] buffer = new byte[64 * 1024];

try (InputStream in = Files.newInputStream(path)) {
    int n;
    while ((n = in.read(buffer)) != -1) {
        if (n > 0) {
            digest.update(buffer, 0, n);
        }
    }
}
byte[] sha256 = digest.digest();
~~~

Đây là **đoạn minh họa** với `path` đã có; cần import `InputStream`, `Files`, `MessageDigest` và xử lý `NoSuchAlgorithmException`/`IOException`. Độ dài bộ đệm và state digest không tăng theo kích thước file; kết quả SHA-256 có 32 byte.

**Cẩn trọng:** hash xác nhận cùng nội dung khi so sánh với một digest **đáng tin**; tự tính hash trên file không tin cậy không chứng minh nguồn gửi hợp pháp. Nếu mục tiêu là phát hiện sửa đổi, cần quy trình nhận/phân phối digest độc lập và an toàn.

### 11.2. Nén và giải nén streaming

`GZIPInputStream`/`GZIPOutputStream` có thể đặt quanh luồng I/O để nén/giải nén theo dòng byte. Tuy nhiên, giải nén là nơi cần quan tâm **zip/decompression bomb**: dữ liệu nén nhỏ có thể giải nén rất lớn. Phải giới hạn **byte đầu ra thực tế**, ratio, thời gian và tài nguyên, không dùng streaming như lý do bỏ quota.

### 11.3. Chuyển đổi có trạng thái

Nếu biến đổi dữ liệu cần biết record trước/sau, phải giữ trạng thái đủ cho thuật toán. Đừng lén đưa toàn bộ file vào bộ nhớ bằng `List<String> allLines`, `Collectors.toList()` hoặc `ByteArrayOutputStream` không giới hạn rồi vẫn gọi chương trình là streaming.

Một thuật toán như sort toàn bộ file không thể chỉ dùng một buffer nhỏ mà giữ dữ liệu xếp hạng trong RAM nếu file lớn hơn RAM. Khi đó cân nhắc **external merge sort**: chia file thành các run vừa bộ nhớ, sắp xếp từng run ra tệp tạm rồi merge bằng queue giới hạn.

## 12. Backpressure và đường ống nhiều tầng

Đọc theo khối giúp giới hạn bộ nhớ của **một bước**, nhưng hệ thống có thể gồm nhiều bước: đọc → giải mã → parse → xử lý → ghi.

Nếu consumer chậm hơn producer và bạn đẩy mọi chunk vào một queue vô hạn, RAM vẫn tăng theo số chunk tồn đọng. Cần **backpressure**:

- Dùng queue có capacity cụ thể; khi queue đầy, nguồn phải chờ/tạm dừng hoặc từ chối/tải xuống chậm hơn.
- Hạn chế số worker và số buffer đồng thời.
- Chỉ tái sử dụng buffer sau khi consumer đã dùng xong.
- Định nghĩa timeout, cancellation và cleanup khi một tầng thất bại.
- Giám sát queue depth, tốc độ đọc/ghi, thời gian chờ, throughput và memory.

Với mô hình đơn luồng `read -> process -> write`, backpressure khá tự nhiên: chỉ đọc khối tiếp theo sau khi đã xử lý xong khối hiện tại. Với I/O async hoặc reactive pipeline, phải điều phối yêu cầu/khả năng nhận để tránh vượt ngân sách.

## 13. Tính đúng đắn khi tệp nguồn có thể thay đổi

Khi xử lý tệp lớn trong nhiều giây/phút, nguồn có thể bị ứng dụng khác sửa trong lúc đọc. Hãy xác định nhu cầu consistency:

- Nếu chỉ cần copy **byte đã đọc được trong lần chạy**, streaming tuần tự phù hợp; nhưng một file đang thay đổi có thể tạo kết quả pha trộn không nhất quán.
- Nếu cần bản sao nguyên vẹn của **một phiên bản cụ thể**, cần snapshot, phối hợp producer, phiên bản bất biến hoặc giao thức khóa thích hợp. `Files.size()` đọc trước không tự đóng băng nội dung.
- Nếu cần publish kết quả chỉ khi copy thành công, ghi vào **file staging** rồi kiểm tra và chuyển sang đường dẫn công bố theo khả năng file system. `Files.move(..., ATOMIC_MOVE)` chỉ có bảo đảm nếu provider hỗ trợ; phải xử lý `AtomicMoveNotSupportedException` theo thiết kế.
- Nếu đích đã tồn tại, lựa chọn `CREATE_NEW` giúp từ chối ghi đè. Nếu nghiệp vụ cho phép replace, phải định nghĩa quy trình overwrite/truncate và rollback khi copy lỗi.

### Quota và dữ liệu không tin cậy

Streaming không có nghĩa tệp vô hạn là an toàn. Khi nhận tệp từ bên ngoài, giới hạn tổng byte, thời gian, số file, tốc độ và dung lượng đĩa. Ví dụ kiểm tra quota tránh tràn:

~~~java
if (n > allowedBytes - totalBytes) {
    throw new IOException("Byte quota exceeded");
}
totalBytes += n;
~~~

Đây là đoạn minh họa, yêu cầu `n > 0`, `totalBytes >= 0` và `totalBytes <= allowedBytes` trước phép so sánh. Nếu vượt quota, **đừng ghi chunk đó**. Không chờ đến cuối mới kiểm tra vì disk/RAM có thể đã cạn.

## 14. Hiệu năng: cần đo điều gì?

Nếu dùng tệp rất lớn để benchmark, so sánh trên đúng storage workload. Các con số quan trọng:

| Chỉ số | Ý nghĩa |
| --- | --- |
| Throughput (MiB/s) | Lượng byte xử lý thực tế mỗi giây |
| Peak heap / native RSS | Đánh giá memory pressure và allocation |
| CPU utilization | Phát hiện chi phí giải mã, copy hoặc hashing |
| GC pauses / allocation rate | Phát hiện cấp phát quá nhiều object mỗi chunk |
| Disk I/O, page cache | Phân biệt đọc từ cache với storage thực |
| P95/P99 latency | Quan trọng khi xử lý nhiều request đồng thời |
| Queue depth / wait time | Đo backpressure và cân bằng producer/consumer |
| Số byte/record xử lý thành công | Phát hiện mất dữ liệu hoặc xử lý thiếu |

**Đừng chỉ chọn khối lớn nhất vì một lần benchmark thấy nhanh.** Với 1.000 request đồng thời, RAM và GC có thể thay đổi thứ tự xếp hạng. Đo cold/warm cache riêng nếu mục tiêu là hiệu năng ổ đĩa.

Với tệp ổn định lớn, `Files.mismatch` hoặc hash sau khi copy là cách kiểm tra nội dung, nhưng bản thân bước verify tạo thêm I/O. Phải quyết định yêu cầu toàn vẹn của nghiệp vụ và tính chi phí này vào benchmark.

## 15. Những lỗi phổ biến

| Sai lầm | Hậu quả | Cách đúng |
| --- | --- | --- |
| Gọi `Files.readAllBytes` cho file kích thước không kiểm soát | OOM, tốn heap | Đọc chunk, giới hạn input |
| Ghi đủ `buffer.length` dù lần đọc cuối ngắn hơn | Byte cũ ở đuôi bản sao | Ghi đúng `n` byte thực tế |
| Dùng `available() == 0` làm EOF | Dừng sớm hoặc logic sai | Dựa vào `read() == -1` |
| Dùng `int` đếm tổng byte | Tràn trên file >2 GiB | Dùng `long`, kiểm tra overflow/quota |
| Gọi `new byte[8192]` trong mọi vòng lặp | Allocation/GC tăng | Tái sử dụng buffer |
| Dùng `ByteBuffer.clear()` trước khi ghi hết | Mất phần chưa ghi | `flip` rồi `while(hasRemaining)`, sau đó `clear` |
| Giả định một lần `FileChannel.write` ghi hết | Bản sao thiếu dữ liệu | Kiểm tra giá trị trả về và lặp |
| Tự giải mã UTF-8 từng chunk byte độc lập | Hỏng ký tự đa byte | Reader/CharsetDecoder có trạng thái |
| `readLine()` trên văn bản không giới hạn độ dài dòng | Có thể tích lũy một dòng cực lớn | Quota per-line hoặc parser giới hạn |
| Gom mọi chunk vào `List` không giới hạn | Streaming giả, RAM tăng theo file | Xử lý và giải phóng từng chunk |
| Hàng đợi pipeline vô hạn | Consumer chậm làm RAM tăng | Bounded queue/backpressure |
| Không đóng stream/channel | Rò file descriptor, file lock | `try-with-resources` |
| Tự tăng offset theo kích thước yêu cầu của `transferTo` | Bỏ sót dữ liệu khi chuyển từng phần | Tăng theo byte thực tế |
| Tin file ổn định khi đang bị ghi | Copy phiên bản hỗn hợp | Snapshot/version/publish protocol |
| Không có quota và cleanup khi gặp lỗi | Disk đầy, file đích dang dở | Quota trong loop, staging và cleanup |

## 16. Câu hỏi tự kiểm tra

**Câu 1.** Bạn có tệp 30 GiB và JVM heap 512 MiB. Có nên gọi `Files.readAllBytes`?

**Đáp án:** Không. Không thể nhét tệp 30 GiB vào một `byte[]`/heap 512 MiB. Hãy xử lý theo chunk hoặc dùng API file-level khác phù hợp.

**Câu 2.** Buffer 8192 byte; lần `read` cuối chỉ trả 27. `out.write(buffer)` có đúng không?

**Đáp án:** Không. Phải dùng `out.write(buffer, 0, 27)` để tránh ghi byte cũ.

**Câu 3.** `FileChannel.write(buffer)` trả 2000 trong khi còn 8192 byte. Cần làm gì?

**Đáp án:** Còn 6192 byte trong buffer. Tiếp tục ghi đến khi `!buffer.hasRemaining()` rồi mới tái sử dụng buffer.

**Câu 4.** Streaming với buffer 64 KiB có bảo đảm ứng dụng dùng đúng 64 KiB RAM?

**Đáp án:** Không. Đó chỉ là một buffer. JVM, parser, queue, cache, native memory và các task khác vẫn cần RAM.

**Câu 5.** Tại sao `new String(bytes, UTF_8)` cho từng khối 8 KiB có thể lỗi dù file text hợp lệ?

**Đáp án:** Ranh giới khối có thể cắt giữa các byte UTF-8 của cùng ký tự. Dùng Reader hoặc CharsetDecoder có trạng thái.

**Câu 6.** Nếu có 500 tác vụ, mỗi tác vụ giữ 1 MiB input buffer và 1 MiB output buffer, riêng buffer tốn khoảng bao nhiêu?

**Đáp án:** 500 × 2 × 1 MiB = 1000 MiB (~0,98 GiB), chưa tính heap/runtime khác.

**Câu 7.** Đọc file theo khối giúp tự động có snapshot nhất quán khi producer đang ghi không?

**Đáp án:** Không. Phải phối hợp phiên bản/snapshot hoặc giao thức công bố an toàn.

**Câu 8.** Khi nào `Files.copy` tốt hơn tự viết loop streaming?

**Đáp án:** Khi chỉ cần sao chép file và không phải phân tích, giới hạn theo chunk hay chuyển đổi dữ liệu đặc biệt; API mức cao ngắn, dễ bảo trì hơn.

## 17. Ghi nhớ

- **Xử lý từng khối** cho phép bộ nhớ đệm I/O không tăng theo kích thước file, nếu dữ liệu được xử lý rồi giải phóng thay vì tích lũy.
- **Luôn dựa vào số byte đọc thực tế**; `-1` là EOF của `InputStream`, `out.write(buffer,0,n)` tránh ghi byte cũ ở khối cuối.
- Với **`FileChannel`**, xử lý cả ghi từng phần, `flip`/`clear` và kiểm tra tiến triển.
- Với **văn bản/record**, đừng nhầm ranh giới khối byte với ranh giới ký tự hay bản ghi.
- **Buffer size × concurrency** ảnh hưởng ngân sách RAM; queue phải có backpressure.
- Nếu dữ liệu lớn hoặc không tin cậy, kết hợp **quota, cleanup, staging, kiểm tra tính toàn vẹn và đo đạc** thay vì chỉ tăng heap JVM.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="performance-and-large-files-002">Chọn kích thước buffer theo phép đo</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn kích thước buffer theo phép đo trong Java I/O và xử lý tệp lớn

## 1. Tại sao không có một kích thước buffer tối ưu chung?

Khi đọc và ghi tệp lớn, chương trình Java có thể sử dụng `InputStream`, `BufferedInputStream`, `BufferedOutputStream`, `FileChannel` và `ByteBuffer`. Kích thước vùng đệm (buffer size) là số byte có thể được gom hoặc chuyển trong một lượt thao tác. Nó tác động đến **số lần gọi I/O**, **CPU**, **throughput**, **latency**, **heap/direct memory** và khả năng phục vụ nhiều tác vụ đồng thời.

Buffer quá nhỏ dễ tăng overhead vì chương trình phải thực hiện nhiều lượt đọc/ghi. Tuy nhiên, **tăng buffer mãi không đồng nghĩa tốc độ tăng mãi**: khi thiết bị lưu trữ, hệ điều hành, cache hoặc CPU đã đạt ngưỡng, vùng đệm lớn hơn có thể gần như không cải thiện thông lượng mà vẫn tiêu tốn thêm RAM.

Do đó, câu trả lời đúng cho câu hỏi “nên dùng 4 KiB, 8 KiB, 64 KiB, 256 KiB hay 1 MiB?” là **chọn bằng phép đo trên workload thực tế**. Không có một con số cố định áp dụng được cho SSD/HDD, NFS, file vài kilobyte hay file hàng trăm gigabyte.

Bài thuộc **Java Core I/O → Hiệu năng và tệp lớn**. Nội dung hướng dẫn xác định biến đo, thiết kế benchmark, phân biệt heap/direct buffer, chạy chương trình Java 17+, phân tích độ bão hòa và lựa chọn kích thước cân bằng giữa hiệu năng và tài nguyên.

## 2. Cơ chế: buffer ảnh hưởng số lượt gọi I/O như thế nào?

Giả sử cần đọc tuần tự một tệp đúng 1 GiB (1.073.741.824 byte). **Nếu mỗi lần đọc đều lấp đầy buffer**, số lần đọc lý thuyết là:

| Buffer | Số lượt đọc đầy buffer cho 1 GiB |
| ---: | ---: |
| 4 KiB | 262.144 |
| 8 KiB | 131.072 |
| 16 KiB | 65.536 |
| 64 KiB | 16.384 |
| 256 KiB | 4.096 |
| 1 MiB | 1.024 |

Công thức tổng quát:

```text
estimatedCalls = ceil(fileBytes / bufferCapacity)
```

Đây **không phải số syscall thực tế được API cam kết**: một lời gọi `read()` có thể nhận ít byte hơn yêu cầu, và một số tầng stream hay filesystem provider có thể thực hiện nhiều thao tác ngầm. Nếu buffer 64 KiB còn đủ chỗ nhưng `read()` chỉ trả 1 KiB, lần đó vẫn hoàn toàn hợp lệ.

Với `FileChannel.read(ByteBuffer)`, một mẫu cơ bản là:

```java
ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);

try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
    while (true) {
        buffer.clear();
        int n = channel.read(buffer);

        if (n == -1) break;
        if (n == 0) continue;

        buffer.flip();
        while (buffer.hasRemaining()) {
            byte value = buffer.get();
            // Xử lý byte thuộc lượt đọc hiện tại.
        }
    }
}
```

Đây là **snippet trong phương thức**, cần `import java.nio.*`, `import java.nio.channels.FileChannel`, `import java.nio.file.*`, biến `path` và xử lý `IOException`. Nó minh họa cách đọc đúng, không phải benchmark. Nếu một channel có thể liên tục trả `0`, không nên dùng vòng `continue` không có cơ chế chờ phù hợp. Với một file channel đọc vào buffer còn chỗ, kết quả `0` thường không phải trạng thái lặp mãi.

`clear()` **chỉ đặt lại** `position=0`, `limit=capacity`, không xóa dữ liệu cũ. `flip()` giới hạn phần đọc đúng số byte mới nhận; parser không được đọc toàn bộ mảng backing rồi vô tình dùng byte của lượt trước.

### 2.1. Khi buffer nhỏ gây overhead

- Nhiều lần gọi phương thức Java và có thể nhiều thao tác native I/O hơn.
- Nhiều lần quản lý `position`, `limit`, loop branch hoặc gọi parser/decoder.
- Nếu application flush hoặc ghi những block quá nhỏ, chi phí đồng bộ hóa/flush có thể cao.
- Với kết nối có latency hoặc server có nhiều request, số lượt xử lý tăng làm throughput/CPU xấu đi.

### 2.2. Khi buffer lớn đem lại hiệu quả giảm dần

- Đĩa, OS cache hoặc CPU parser có thể đã là bottleneck.
- Nhiều request đồng thời giữ các vùng đệm lớn, gây heap/direct-memory pressure.
- Copy, giải mã, checksum hay logic nghiệp vụ có thể áp đảo lợi ích giảm lượt đọc.
- Nếu tầng ứng dụng cố gom đầy buffer trước khi xử lý thì **time-to-first-result** có thể tăng, nhưng không được nhầm với hành vi mặc định của mọi `read()`: Java không luôn đợi buffer đầy.
- File nhỏ hơn buffer có thể không được lợi từ tăng kích thước.

Từ đây hình thành nguyên tắc: **tìm điểm hiệu năng bão hòa, sau đó ưu tiên cấu hình nhỏ và ổn định**.

## 3. Những loại buffer cần phân biệt

### 3.1. Heap ByteBuffer

```java
ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);
```

Heap buffer lưu byte trong heap Java, có backing array và thường thuận tiện cho code xử lý `byte[]`. Cấp phát nhiều buffer mới có thể tạo thêm object và áp lực GC.

Với đọc file theo stream, **tái sử dụng một buffer** ngoài vòng lặp thường quan trọng hơn việc tranh luận 64 KiB hay 128 KiB.

### 3.2. Direct ByteBuffer

```java
ByteBuffer buffer = ByteBuffer.allocateDirect(64 * 1024);
```

Direct buffer thường sử dụng memory ngoài Java heap. Theo tài liệu `ByteBuffer`, JVM sẽ **best effort** tận dụng native I/O trực tiếp để tránh một số bước copy trung gian. Điều đó **không bảo đảm** direct luôn nhanh hơn heap.

Đặc điểm cần đo:

- Chi phí cấp phát/giải phóng direct thường cao hơn non-direct.
- Dung lượng direct memory vẫn là tài nguyên thật, có quota và rủi ro `OutOfMemoryError`.
- `buffer.array()` không thể giả định hoạt động với direct buffer.
- `clear()` không giải phóng hay xóa vùng nhớ.
- Tạo direct buffer một lần rồi tái sử dụng là lựa chọn khác hẳn tạo direct buffer ở mỗi request.

Nếu cần so heap và direct, **giữ nguyên các tham số khác**: cùng file, kích thước, kiểu truy cập, số luồng, warm-up và cách xử lý byte.

### 3.3. BufferedInputStream/BufferedOutputStream

```java
try (InputStream in = new BufferedInputStream(
        Files.newInputStream(path), 64 * 1024)) {
    // Đọc file theo API InputStream.
}
```

Đoạn này cần import `java.io.*`, `java.nio.file.*`, cùng biến `path`.

`BufferedInputStream` hữu ích khi ứng dụng thực hiện nhiều lần đọc nhỏ, đặc biệt đọc từng byte hoặc khối rất ngắn. Nhưng nếu ứng dụng đã đọc block lớn bằng `FileInputStream.read(byte[])`, thêm một wrapper với buffer nhỏ hơn **không bảo đảm** tăng throughput. Đường xử lý của từng overload, chi phí copy và dữ liệu thực tế mới quyết định kết quả.

Phải phân biệt **buffer của wrapper**, **byte[] do ứng dụng dùng**, **OS page cache** và **buffer của thiết bị**. Chúng tồn tại ở những tầng khác nhau.

### 3.4. Memory-mapped file và transferTo

`FileChannel.map()` cùng `MappedByteBuffer` thay cơ chế truy cập thành vùng nhớ được ánh xạ; `FileChannel.transferTo()` có thể tối ưu việc chuyển byte giữa các channel. Chúng **không đơn giản là chọn một buffer size khác** trong vòng `read()`. Nếu bài toán thực sự là copy file hay random access, hãy benchmark mapping/transfer như những phương án riêng trên cùng đầu ra nghiệp vụ.

## 4. Chọn mục tiêu phép đo trước khi viết code

Một ứng dụng sao lưu có thể coi **throughput** là chỉ số hàng đầu; ứng dụng phục vụ request có thể coi **p99 latency** và memory per connection quan trọng hơn.

| Đại lượng | Ý nghĩa | Cách xem |
| --- | --- | --- |
| Throughput | Tổng MiB xử lý trên một giây | Đo bytes thực nhận / thời gian |
| Per-file latency | Thời gian hoàn thành một tệp | Thống kê median, p95/p99 |
| CPU | Xử lý, copy, parser, checksum | Quan sát CPU utilization/profiler |
| GC | Allocation và pause do heap buffers/objects | JFR, GC log, profiler |
| Direct memory | Bộ nhớ native/direct buffer còn sống | Quota JVM, native memory/profiler |
| I/O calls | Số lần gọi đọc/ghi thực tế | Instrument code hoặc system tracer |
| Peak concurrency | Số request đồng thời giữ buffer | Counter/quota |
| Correctness | Số byte và nội dung thực sự đúng | Byte count, checksum/hash, test |

Công thức tính throughput:

```text
MiB/s = bytesProcessed / (1024 × 1024) / seconds
```

**MiB** (2²⁰ byte) khác **MB** (10⁶ byte). Khi so sánh các bảng kết quả, hãy luôn ghi đúng đơn vị và không dùng số lượng byte theo giả định khi thực tế có partial read/write.

Trước khi chạy, tự trả lời:

- Đọc, ghi, copy, parse hay giải nén?
- SSD local, HDD, NFS, network share hay dữ liệu đã nằm trong cache?
- Tệp điển hình là 1 KiB, 32 MiB, 10 GiB hay hỗn hợp?
- Một job hay hàng nghìn job đồng thời?
- Có cần `force()/fsync` khi ghi để đáp ứng durability?
- Dữ liệu có thể bị thay đổi giữa các lượt đo không?
- Cần ưu tiên throughput, latency hay giới hạn RAM?

## 5. Dải buffer size cần khảo sát

Dải thử ban đầu thường có ích:

```text
4 KiB → 16 KiB → 64 KiB → 256 KiB → 1 MiB
```

Đây là **dãy ứng viên**, không phải một khuyến cáo rằng 1 MiB luôn là tốt nhất. Đo thô theo bậc lớn giúp tìm vùng cải thiện nhanh; sau đó có thể thử thêm 32, 96, 128 hoặc 192 KiB quanh vùng bão hòa.

**Không chọn một giá trị chỉ vì lớn nhất trong một lần benchmark**. Nếu 64 KiB đạt 95% hiệu năng tốt nhất và 1 MiB chỉ nhỉnh hơn trong sai số, 64 KiB có thể là quyết định tốt hơn vì ít tốn memory và dễ hỗ trợ nhiều tác vụ.

Ngưỡng 95% chỉ là một ví dụ về **tiêu chí chọn cấu hình của dự án**, không phải một định luật của JVM.

## 6. Java 17+ chạy được: benchmark đọc file với nhiều kích thước buffer

Chương trình sau tạo một file tạm **16 MiB**, sau đó đo các lượt quét tuần tự bằng `FileChannel.read(ByteBuffer)` với heap và direct buffers. Mỗi cấu hình có hai lượt warm-up và năm mẫu đo; chọn median để giảm ảnh hưởng của một lần bất thường.

**Thiết kế bài đo:**

1. Ghi fixture **ngoài** vùng tính thời gian.
2. Mở channel và cấp buffer **trước** khi bắt đầu đếm `nanoTime()`, để cô lập thời gian đọc loop.
3. Tái sử dụng buffer trong suốt một lượt.
4. Mỗi lượt đọc luôn tới EOF, kiểm tra tổng byte đúng `FILE_BYTES`.
5. Không parse byte, chỉ đếm lượng byte `read()` trả về; đây là benchmark **đọc dữ liệu**, không phải xử lý nghiệp vụ.
6. Không ép làm lạnh OS page cache; các lượt sau có thể chủ yếu đo warm-cache.

Tạo file `BufferSizeBenchmark.java`:

```java
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Locale;

public class BufferSizeBenchmark {
    private static final int FILE_BYTES = 16 * 1024 * 1024;
    private static final int WARMUP = 2;
    private static final int SAMPLES = 5;
    private static final int[] CAPACITIES = {
        4 * 1024, 16 * 1024, 64 * 1024,
        256 * 1024, 1024 * 1024
    };

    private static void createFixture(Path file) throws IOException {
        byte[] block = new byte[8192];
        for (int i = 0; i < block.length; i++) {
            block[i] = (byte) (i & 0xff);
        }

        try (OutputStream out = Files.newOutputStream(file)) {
            int remaining = FILE_BYTES;
            while (remaining > 0) {
                int n = Math.min(remaining, block.length);
                out.write(block, 0, n);
                remaining -= n;
            }
        }
    }

    private static long oneRun(
            Path file, int capacity, boolean direct) throws IOException {

        ByteBuffer buffer = direct
                ? ByteBuffer.allocateDirect(capacity)
                : ByteBuffer.allocate(capacity);

        try (FileChannel channel = FileChannel.open(
                file, StandardOpenOption.READ)) {

            long bytesRead = 0;
            long started = System.nanoTime();

            while (true) {
                buffer.clear();
                int n = channel.read(buffer);

                if (n == -1) {
                    break;
                }
                if (n == 0) {
                    throw new IOException(
                            "No progress with nonempty buffer");
                }
                bytesRead += n;
            }

            long elapsed = System.nanoTime() - started;
            if (bytesRead != FILE_BYTES) {
                throw new IOException("Read " + bytesRead
                        + " instead of " + FILE_BYTES);
            }
            return elapsed;
        }
    }

    private static void measure(
            Path file, int capacity, boolean direct) throws IOException {

        for (int i = 0; i < WARMUP; i++) {
            oneRun(file, capacity, direct);
        }

        long[] times = new long[SAMPLES];
        for (int i = 0; i < times.length; i++) {
            times[i] = oneRun(file, capacity, direct);
        }

        Arrays.sort(times);
        long medianNs = times[times.length / 2];
        double elapsedSeconds = medianNs / 1_000_000_000.0;
        double throughputMiB = (FILE_BYTES / 1_048_576.0)
                / elapsedSeconds;
        double millis = medianNs / 1_000_000.0;

        System.out.printf(Locale.ROOT,
                "%-6s %5d KiB: median %.3f ms; %.2f MiB/s%n",
                direct ? "direct" : "heap",
                capacity / 1024, millis, throughputMiB);
    }

    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("buffer-benchmark-", ".dat");
        try {
            createFixture(file);
            System.out.println("Fixture bytes: " + Files.size(file));

            for (boolean direct : new boolean[] {false, true}) {
                for (int capacity : CAPACITIES) {
                    measure(file, capacity, direct);
                }
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

Biên dịch và chạy:

```bash
javac BufferSizeBenchmark.java
java BufferSizeBenchmark
```

Kết quả **dòng đầu tiên cố định** khi file tạm được ghi đúng và đọc được:

```text
Fixture bytes: 16777216
```

Sau đó chương trình in mười dòng, một dòng cho mỗi cấu hình heap/direct và kích thước 4, 16, 64, 256, 1024 KiB. **Các con số thời gian và MiB/s thay đổi theo máy chạy**, vì vậy không có stdout số học cố định đáng tin để chép vào tài liệu. Dạng kết quả là:

```text
heap       4 KiB: median <thời gian> ms; <thông lượng> MiB/s
...
direct  1024 KiB: median <thời gian> ms; <thông lượng> MiB/s
```

Dấu `<...>` và dòng `...` ở trên biểu diễn vị trí **số đo thực tế**, không phải output nguyên văn hoặc kết quả đã đo sẵn. Không trình bày những số liệu giả như benchmark thực.

### 6.1. Phân tích chương trình

- `createFixture()` ghi theo từng block 8 KiB để tạo file 16 MiB mà không cần `byte[]` dài bằng file.
- `oneRun()` cấp một `ByteBuffer` trước khi bắt đầu đo rồi mở `FileChannel` trong `try-with-resources`.
- `buffer.clear()` mở phạm vi nhận dữ liệu từ `position=0` tới `capacity`. Vì bài đo **chỉ đếm byte**, không xử lý nội dung vừa đọc, không cần `flip()` giữa các lượt.
- `read()` trả số byte thực chuyển, `-1` nghĩa là EOF; code từ chối `0` nếu file channel và buffer còn chỗ không tạo tiến triển, tránh một busy loop vô hạn trong demo.
- `bytesRead` được cộng theo **kết quả mỗi lần read**, không ước lượng bằng capacity. Kiểm tra cuối vòng giúp phát hiện fixture bị thiếu hoặc thay đổi.
- Mỗi cấu hình có warm-up và các mẫu measured. Việc sort rồi lấy phần tử giữa là cách tính median đơn giản với năm mẫu.
- `System.nanoTime()` là đồng hồ đo khoảng thời gian, không phải wall-clock dùng để xác định ngày giờ.
- Tổng throughput đo **lượng byte đi qua FileChannel trong timed loop**, không bao gồm tạo file, cấp phát ByteBuffer, mở/đóng channel hoặc parse dữ liệu.

**Đây không phải benchmark end-to-end hay benchmark cold-disk.** Nó minh họa phương pháp lựa chọn cấu hình. Nếu production liên tục cấp phát buffer theo request, phải làm thêm thí nghiệm gồm chi phí allocation và lifecycle.

### 6.2. Vì sao ví dụ chưa đủ để công bố bảng xếp hạng?

- Thứ tự chạy là heap trước, direct sau. Nhóm sau có thể hưởng lợi từ page cache đã nóng hơn; cần đảo hoặc random hóa thứ tự trong thí nghiệm nghiêm túc.
- File 16 MiB có thể nằm trọn trong OS page cache. Con số cao không chứng minh ổ đĩa vật lý đạt tốc độ đó.
- `read` loop không parse nội dung, chưa đo CPU xử lý CSV/JSON/UTF-8 hay copy dữ liệu sang đầu ra.
- Buffer được cấp trước đồng hồ đo; chi phí cấp phát direct không nằm trong elapsed time.
- Số mẫu nhỏ, điều kiện nhiệt/CPU và tiến trình khác có thể làm nhiễu.
- `FileChannel` được mở mới cho từng lượt nhưng điều đó **không làm lạnh OS page cache**.

Nếu kết quả có chênh lệch nhỏ, cần lặp lại phép đo với thứ tự đổi ngẫu nhiên, file lớn hơn và cùng cấu hình JVM/OS. Đối với quyết định production, benchmark đúng đường đi đọc và xử lý **thực sự được chạy**.

## 7. Cách chọn từ bảng số đo: điểm bão hòa

Giả sử **chỉ để minh họa** một hệ thống cho bảng sau (không phải kết quả thực tế của chương trình ở trên):

| Buffer | Throughput giả định | Buffer memory / 1 tác vụ |
| ---: | ---: | ---: |
| 4 KiB | 320 MiB/s | 4 KiB |
| 16 KiB | 760 MiB/s | 16 KiB |
| 64 KiB | 940 MiB/s | 64 KiB |
| 256 KiB | 970 MiB/s | 256 KiB |
| 1 MiB | 973 MiB/s | 1 MiB |

Từ 4 KiB tới 16 KiB tăng nhiều; từ 64 KiB tới 1 MiB chỉ tăng khoảng 3,5% trong bảng giả định, nhưng tiêu thụ buffer lớn gấp 16 lần. Nếu sai số benchmark đã gần 5%, không có đủ bằng chứng rằng 1 MiB thực sự tốt hơn.

**Cách quyết định có căn cứ:**

1. Loại giá trị không đáp ứng correctness, gây quá tải, tốn RAM hoặc p99 latency xấu.
2. Tìm kích thước nhỏ nhất đạt mục tiêu hiệu năng, ví dụ trong vòng 95% throughput tốt nhất đã thấy.
3. Nếu chênh lệch nằm trong nhiễu, đo thêm thay vì khẳng định có “winner”.
4. Nếu một cấu hình nhanh hơn nhưng tiêu thụ bộ nhớ nhiều, đặt nó vào bối cảnh **số tác vụ đồng thời**.
5. Chốt giá trị rồi benchmark lại với file size, concurrency và môi trường production.

**Giá trị “95%” là ngưỡng chọn có thể áp dụng của dự án, không phải hằng số của Java.** Với nhiệm vụ nhạy cảm latency, hãy chọn theo p95/p99 và throughput tối thiểu thay vì chỉ tối đa hóa MB/s.

## 8. Bộ nhớ, GC và tác vụ song song

Nếu mỗi tác vụ giữ một buffer riêng:

```text
estimatedBufferMemory = activeOperations × bufferCapacity
```

Ví dụ 512 job cùng lúc:

| Buffer mỗi job | Tổng vùng byte trước overhead |
| ---: | ---: |
| 8 KiB | 4 MiB |
| 64 KiB | 32 MiB |
| 256 KiB | 128 MiB |
| 1 MiB | 512 MiB |

Đây chỉ là tổng buffer, **chưa kể** object headers, queue, file descriptors, TLS, page cache, parser objects, class metadata và native resources.

Với heap buffers, nhiều lần allocate có thể tạo GC pressure. Với direct buffers, phần memory có thể không hiển thị đầy đủ trong heap metrics, vì thế cần đo native/direct memory riêng.

**Không dùng một buffer chung cho nhiều thread cùng ghi nếu không đồng bộ.** `ByteBuffer` có state `position/limit` không thread-safe. Nếu dùng buffer pool, bảo đảm một buffer chỉ có một owner tại mỗi thời điểm, chỉ trả lại pool khi I/O đã kết thúc và consumer đã xử lý xong.

### 8.1. Buffer và giới hạn in-flight

Khi server có nhiều upload/download hoặc asynchronous file requests, chỉ tăng buffer size mà không giới hạn số operations đang chạy có thể làm cạn tài nguyên.

Một thiết kế cân bằng có thể kết hợp:

- Buffer 64 KiB hoặc 128 KiB cho mỗi job (sau khi đo).
- Số job in-flight tối đa theo `Semaphore`/bounded queue.
- Quota số byte pending ở đầu vào và đầu ra.
- Giới hạn số file handles/sockets mở.
- Backpressure nếu consumer không xử lý kịp.

Ở hệ thống nhiều request, **buffer size tối ưu riêng cho một job** có thể không phải giá trị tối ưu cho **toàn dịch vụ**.

## 9. Bài toán đọc tệp khác sao chép tệp

Benchmark `FileChannel.read(buffer)` không thay thế benchmark **copy**, vì copy còn phải ghi sang đích.

Snippet sao chép qua hai channel:

```java
ByteBuffer buffer = ByteBuffer.allocateDirect(64 * 1024);

while (true) {
    buffer.clear();
    int n = source.read(buffer);
    if (n == -1) {
        break;
    }
    if (n == 0) {
        continue;
    }

    buffer.flip();
    while (buffer.hasRemaining()) {
        destination.write(buffer);
    }
}
```

Đoạn này minh họa trong phương thức có `FileChannel source` và `FileChannel destination` đã mở. Với channel có thể liên tục trả `0`, cần cơ chế chờ/giới hạn thích hợp để tránh busy loop. **Partial write** là bình thường: một lần `write()` có thể tiêu thụ ít byte hơn phần còn lại của buffer. Không được `clear()` ngay nếu còn byte chưa gửi/ghi.

Đối với bài toán copy file, benchmark thêm `FileChannel.transferTo`/`transferFrom` theo giới hạn provider và kết quả thực chuyển. Việc truyền trực tiếp có thể hiệu quả hơn tự copy qua buffer, nhưng không được mặc định.

### 9.1. Ghi file và durability

Một lời gọi `write()` thành công có thể chỉ có nghĩa dữ liệu đã được nhận trong các tầng I/O/cache, chưa chứng minh dữ liệu đã nằm trên thiết bị lưu trữ một cách bền vững.

Nếu nghiệp vụ yêu cầu durability, phải định nghĩa chính sách `FileChannel.force(true)` hoặc cơ chế sync tương đương theo hợp đồng API và đo chúng **công bằng**. Không so một phép đo có `force()` sau mỗi block với phép đo không `force()` rồi kết luận do buffer size.

Đối với file ghi mới, việc cấp phát dung lượng, filesystem journaling, metadata updates và storage cache có thể là nhân tố lớn hơn cỡ buffer.

## 10. Workload xử lý văn bản/JSON/CSV

Nếu nhiệm vụ thực là đọc tệp CSV, log, JSON Lines hoặc dữ liệu UTF-8:

- Kích cỡ buffer ảnh hưởng số lượt lấy byte, nhưng parser vẫn phải xử lý delimiters, quotes, escape và ký tự nhiều byte.
- Một ký tự UTF-8 có thể nằm qua ranh giới buffer; cần decoder streaming phù hợp.
- `BufferedReader.readLine()` có logic vùng đệm riêng; không thể áp trực tiếp kết quả đo vòng `FileChannel.read` vào nó.
- Nếu code giữ mọi dòng vào `List<String>`, heap có thể bị chi phối bởi dữ liệu đã parse chứ không phải read buffer.
- Parsing/validation/checksum có thể là bottleneck sau khi I/O đã đủ nhanh.

Khi đo parser thật, thời gian phải bao gồm **đọc, giải mã, parse và tiêu thụ dữ liệu** theo đúng yêu cầu. Nên có một phép đo end-to-end và một phép đo read-only để biết đang tối ưu đúng chỗ hay chưa.

## 11. Quy trình benchmark đáng tin cậy

1. **Xác định câu hỏi**: tìm size giúp tăng throughput, giảm latency, hay giảm áp lực RAM?
2. **Chọn dữ liệu đại diện**: file sizes và định dạng gần production, số lượng file tương ứng.
3. **Định nghĩa điều kiện cache**: warm-cache, cold-ish hoặc môi trường test được kiểm soát; đừng gọi file cache lạnh chỉ vì mở lại stream.
4. **Warm-up JVM**: khởi động, load class/JIT, thực thi nhiều lượt trước phần measured.
5. **Thử nhiều sizes**: ví dụ 4/16/64/256/1024 KiB.
6. **Đổi thứ tự**: random hóa thứ tự cấu hình trong các vòng lặp để giảm bias do cache và drift theo thời gian.
7. **Đo nhiều mẫu**: median, phân phối, min/max, p95/p99 khi đủ mẫu; không chỉ lấy một kết quả tốt nhất.
8. **Đo heap và direct riêng**: cần cùng kiểu công việc, allocation policy và quota rõ ràng.
9. **Quan sát tài nguyên**: CPU, GC, heap, native/direct memory, queue length, I/O counters.
10. **Kiểm tra correctness**: đúng số byte, checksum/hash hoặc kết quả parser; benchmark làm sai dữ liệu không có giá trị.
11. **Đo end-to-end**: nếu nghiệp vụ cần parse, copy, nén hoặc force, đưa những thao tác ấy vào thí nghiệm ứng dụng.
12. **Ghi metadata**: vendor/version JDK, JVM flags, OS, filesystem, CPU, RAM, thiết bị, kích thước file, concurrency và cấu hình cache.
13. **Chốt lựa chọn có ngân sách**: chọn size nhỏ nhất đáp ứng throughput/latency và tài nguyên.
14. **Lặp lại khi môi trường đổi**: nâng JDK, đổi SSD, tăng concurrency hoặc thay parser có thể làm optimum thay đổi.

### 11.1. Warm-cache và cold-cache

Hệ điều hành có page cache. Sau lần đọc đầu, file thường được cache nên lượt sau có thể nhanh hơn mà không liên quan hoàn toàn tới kích thước buffer.

- **Warm-cache test** giúp kiểm tra chi phí đường đi từ cache tới ứng dụng.
- **Cold-storage test** nhằm đo ảnh hưởng của thiết bị lưu trữ, nhưng phải kiểm soát cache bằng phương án phù hợp môi trường test.
- Chỉ gọi `FileChannel.close()` rồi mở lại **không xóa OS page cache**.
- File lớn hơn RAM có thể hạn chế khả năng cache toàn bộ, nhưng không bảo đảm mọi byte của lượt đọc là “cold”.

Không nên sử dụng lệnh drop caches hay điều chỉnh hệ thống production một cách tùy tiện chỉ để tạo kết quả benchmark.

### 11.2. JIT, CPU và GC

Dùng `System.nanoTime()` để đo khoảng thời gian, không dùng đồng hồ ngày giờ. JIT compilation, CPU governor, thermal throttling, background processes và scheduler đều có thể ảnh hưởng phép đo.

Java Flight Recorder (JFR), JDK Mission Control hoặc profiler phù hợp giúp xem allocation, CPU hotspots và GC pauses. Đừng kết luận direct buffers nhanh hơn nếu mọi thứ khác thay đổi giữa hai lượt thử.

### 11.3. JMH có đủ không?

JMH (Java Microbenchmark Harness) hữu ích để đo **đoạn tính toán trong JVM** với warm-up, forks và các kiểm soát microbenchmark. Tuy nhiên, I/O với filesystem còn phụ thuộc OS page cache, storage, network và cả máy chủ đang chạy. Dùng JMH **không tự biến** một phép đo file đã cache thành benchmark ổ đĩa lạnh hoặc kiểm định hiệu năng production.

Nên kết hợp microbenchmark cho parser/codec với phép đo I/O tích hợp và tải thật.

## 12. Kích thước buffer cho network không phải kích thước TCP packet

Với `SocketChannel`, `AsynchronousSocketChannel`, buffer cần cân đối:

- Kích thước message/frame của protocol.
- Số lượng client/connections.
- Inbound/outbound queues và backpressure.
- Partial read/write, `read()==0` và `read()==-1`.
- Độ trễ phản hồi, số lần selector/completion callback.
- TLS/encoding overhead khi có tầng bảo mật.
- Memory quota nếu mỗi connection giữ một buffer.

Không lấy MTU hoặc một TCP segment làm “buffer size bắt buộc”. `ByteBuffer.capacity()` và `SO_RCVBUF`/`SO_SNDBUF` thuộc những tầng khác nhau.

Nếu một server có 20.000 connections và cấp 1 MiB mỗi connection, chỉ riêng vùng buffer có thể lên tới khoảng 19,5 GiB. Con số này có thể là vấn đề lớn dù benchmark file đơn luồng cho thấy 1 MiB nhanh hơn đôi chút.

## 13. Sai lầm phổ biến

| Sai lầm | Hậu quả | Cách khắc phục |
| --- | --- | --- |
| Chọn 1 MiB vì “to là nhanh” | Lãng phí RAM, không chắc tăng tốc | Đo nhiều kích thước và điểm bão hòa |
| Chỉ chạy benchmark một lượt | Nhiễu JIT/cache/scheduler | Warm-up, lặp mẫu, đổi thứ tự |
| Dùng file quá nhỏ | Chủ yếu đo cache/overhead khác | Dataset đại diện |
| Mở lại channel và gọi là cold-cache | OS cache còn giữ dữ liệu | Ghi rõ warm-cache/cold-storage điều kiện |
| So heap với direct nhưng đổi cả workload | Không biết tác nhân gây khác biệt | Chỉ đổi một biến mỗi lần |
| Allocate direct buffer trong vòng read | Tốn native memory và allocation cost | Reuse buffer, quota |
| Chỉ nhìn throughput | Có thể làm p99 hoặc GC xấu | Đo latency/memory/CPU cùng lúc |
| Lấy `buffer.array()` như toàn bộ dữ liệu đọc | Có byte cũ và vùng ngoài limit | `flip()` rồi dùng `remaining()` |
| Coi `read()==0` là EOF | Bỏ dở dữ liệu/đóng sớm | EOF là `-1` |
| Gọi `clear()` khi write chưa hết byte | Mất trạng thái pending | Lặp `hasRemaining()` hoặc chờ khả năng ghi |
| Dùng `(int) fileSize` để cấp buffer | Overflow với file lớn | Dùng buffer cố định, fileSize/offset kiểu `long` |
| Đo read rồi suy ra tốc độ CSV parser | Bỏ qua CPU decode/parse/alloc | Benchmark end-to-end |
| Đo write nhưng không force rồi kết luận durability | Không đo đúng hợp đồng bền vững | Định nghĩa flush/force policy |
| Thấy khác biệt 1% và tuyên bố chiến thắng | Có thể nằm trong nhiễu | Đo phân phối, chạy lặp |
| Không tính buffers × concurrency | Hết heap/direct memory khi nhiều job | Quota và backpressure |

## 14. Thực hành và lời giải

**Câu 1.** Tệp 1 GiB với buffer 4 KiB và 64 KiB có số lần đọc *đầy buffer* lý thuyết lần lượt bao nhiêu?

**Đáp án:** 262.144 và 16.384, chênh 16 lần. Số syscall thực tế có thể khác vì mỗi read có thể ngắn.

**Câu 2.** Vì sao buffer 256 KiB không chắc nhanh gấp bốn lần buffer 64 KiB?

**Đáp án:** Overhead giảm theo cách không tuyến tính; thiết bị lưu trữ, page cache, CPU và parser có thể đã bão hòa.

**Câu 3.** `ByteBuffer.allocateDirect()` có chắc ít tốn RAM hơn `allocate()`?

**Đáp án:** Không. Nó có thể dùng native/direct memory ngoài heap, vẫn chiếm tài nguyên và có quota riêng.

**Câu 4.** Sau năm lượt đọc cùng một file, lượt thứ sáu rất nhanh. Đó có chắc là tốc độ SSD thật không?

**Đáp án:** Không. OS page cache có thể làm nhiều byte đến từ RAM; cần hiểu điều kiện đo.

**Câu 5.** 512 operations đồng thời, buffer mỗi operation 1 MiB. Tổng buffer byte bao nhiêu?

**Đáp án:** 512 MiB, chưa tính overhead. Nếu dùng 64 KiB chỉ là 32 MiB.

**Câu 6.** Tại sao benchmark chỉ đếm byte trong ví dụ không dùng `flip()`?

**Đáp án:** Vì nó không đọc nội dung từ ByteBuffer sau `FileChannel.read`, chỉ đọc số byte trả về. Code thực sự cần parse byte phải `flip()` trước khi consume.

**Câu 7.** Buffer được cấp trước `System.nanoTime()`. Benchmark đo được chi phí allocation không?

**Đáp án:** Không. Muốn đánh giá workload liên tục tạo buffer, cần thử nghiệm thứ hai có tính allocation và GC/native memory.

**Câu 8.** Nếu throughput giữa 64 KiB và 1 MiB khác 2%, nhưng độ biến động phép đo khoảng ±5%, nên làm gì?

**Đáp án:** Không tuyên bố 1 MiB chắc nhanh hơn. Đo thêm, giảm nhiễu và cân nhắc dùng buffer ít tốn RAM.

**Câu 9.** Tại sao cần dùng `hasRemaining()` khi ghi qua channel?

**Đáp án:** `write()` có thể tiêu thụ một phần dữ liệu. Nếu còn byte trong buffer phải tiếp tục/chờ, không gọi `clear()` sớm.

**Câu 10.** Benchmark file read-only có thể dùng để kết luận buffer tốt nhất cho parser JSON Lines không?

**Đáp án:** Không đủ. Parser còn decode, tạo object, xử lý ranh giới và tiêu thụ CPU/GC; cần benchmark đúng luồng nghiệp vụ.

## 15. Checklist đưa vào production

1. Đã biết workload đọc/ghi/copy/parse và thước đo mục tiêu?
2. Đã thử ít nhất một dải kích thước có tính hệ thống?
3. Đã phân biệt heap/direct/wrapper/mapped/transfer?
4. Đã dùng warm-up, nhiều mẫu, đổi thứ tự để tránh bias?
5. Đã ghi điều kiện page cache và tính năng storage?
6. Đã kiểm tra tính đúng của số byte, payload, EOF/partial write?
7. Đã tính `bufferSize × maxConcurrentOperations` và các queue khác?
8. Đã đo throughput **và** latency/GC/memory ở tải dự kiến?
9. Đã chọn kích thước dựa trên điểm bão hòa cùng memory budget?
10. Đã lưu kết quả benchmark và điều kiện để lần nâng JDK/thiết bị đo lại?

**Ghi nhớ:** Đừng chọn buffer lớn nhất; hãy chọn **buffer đủ lớn**, được chứng minh bằng phép đo đại diện và phù hợp ngân sách tài nguyên. Với tệp lớn, một buffer cỡ vừa được tái sử dụng và lặp `read()/write()` đúng thường là điểm khởi đầu tốt hơn việc cố cấp phát vùng nhớ bằng toàn bộ file.

## Tài liệu tham khảo

- Java SE 17 — `ByteBuffer` (heap/direct, chi phí cấp phát và native I/O): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html
- Java SE 17 — `FileChannel` (`read`, `write`, `force`, `map`, `transferTo`): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileChannel.html
- Java SE 17 — `ReadableByteChannel`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/ReadableByteChannel.html
- Java SE 17 — `BufferedInputStream`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedInputStream.html
- Java SE 17 — `BufferedOutputStream`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedOutputStream.html
- Java SE 17 — `Files`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html
- OpenJDK JMH — Java Microbenchmark Harness: https://openjdk.org/projects/code-tools/jmh/


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="performance-and-large-files-003">Chi phí syscall, chuyển đổi charset và số lớp bọc</a>

<details>
<summary>Xem chi tiết</summary>

# Chi phí syscall, chuyển đổi charset và số lớp bọc trong Java I/O

## 1. Bức tranh tổng thể: chậm ở đâu khi đọc/ghi dữ liệu lớn?

Hiệu năng Java I/O không chỉ phụ thuộc tốc độ SSD, ổ mạng hay băng thông socket. Trên đường đi của dữ liệu, một ứng dụng có thể phải trả nhiều loại chi phí khác nhau:

1. **Lời gọi từ code ứng dụng tới Java API:** gọi `read()`/`write()`, kiểm tra điều kiện, tạo object, dispatch qua các lớp wrapper.
2. **Chi phí hệ điều hành:** những lần cần đi qua ranh giới user mode ↔ kernel mode để yêu cầu I/O; thường được gọi là **system call (syscall)**.
3. **Chi phí copy và buffering:** di chuyển byte giữa Java arrays, ByteBuffer, buffer của wrapper, kernel buffers và thiết bị/network stack.
4. **Chuyển đổi ký tự:** decode `byte[]/ByteBuffer` thành text, hoặc encode `char/String` thành bytes bằng `Charset`.
5. **Xử lý chức năng khác:** nén/giải nén, checksum, mã hóa, TLS, phân tích cú pháp, kiểm tra khung bản tin.
6. **Cấp phát/GC:** nhiều buffer hay String trung gian có thể tăng allocation, memory pressure và garbage collection.
7. **Chờ thực tế:** disk seek, page cache miss, filesystem, mạng, locking, backpressure hoặc xử lý của peer.

Một thao tác được gọi là “I/O chậm” có thể bị giới hạn bởi **IOPS, syscall overhead, throughput, CPU charset conversion, memory bandwidth, GC hoặc độ trễ của thiết bị**. Cần đo trước khi tối ưu.

Ví dụ đường đi khi đọc một file text:

~~~text
Java application: đọc từng dòng
      |
      v
BufferedReader: char buffer / readLine
      |
      v
InputStreamReader: UTF-8 decoder / byte buffer
      |
      v
FileInputStream: đọc byte
      |
      v
JDK/native/OS: những yêu cầu read tới kernel
      |
      v
Page cache, filesystem, block device

Lưu ý: không phải mỗi mũi tên hoặc mỗi Java read() là một syscall.
~~~

Nội dung dưới đây tập trung **chi phí syscall, charset conversion và số lớp wrapper** trong nhóm “Hiệu năng và tệp lớn”, đồng thời chỉ ra cách đo và thiết kế pipeline an toàn trên Java 17+.

## 2. Phân biệt Java read/write call và syscall thật

### 2.1. Ba cấp thao tác

Giả sử ứng dụng gọi:

~~~java
int one = input.read();
~~~

Đây là **đoạn minh họa** với `InputStream input` đã mở. Một invocation của `read()` là **lời gọi Java**, không tự động bằng một system call. Với `ByteArrayInputStream`, toàn bộ byte nằm trong RAM của JVM và việc đọc đó thường không cần truy cập kernel. Với `BufferedInputStream`, lời gọi `read()` có thể chỉ lấy byte đã được cache ở buffer. Với file/socket stream thực, lời gọi đến lớp dưới có thể phải chờ dữ liệu và dẫn tới syscall hoặc một cơ chế native/OS khác.

Bảng cần nhớ:

| Đơn vị | Ai thực hiện? | Ý nghĩa | Đo ở đâu? |
|---|---|---|---|
| `input.read()` của ứng dụng | Java code | Một yêu cầu đọc mức API | Instrumentation trong JVM |
| `underlying.read(...)` ở stream bên dưới | Wrapper/JDK code | Một yêu cầu xuống lớp kế | Wrapper đếm call, profiler |
| Kernel `read` / `write` / các I/O syscall khác | JVM/native/OS | Yêu cầu đi qua ranh giới OS | Tracing, performance counters |
| Physical disk I/O | Storage stack | Trao đổi thật với thiết bị | Disk metrics, OS telemetry |

**Không đồng nhất bốn chỉ số.** Đặc biệt khi file đã nằm trong **OS page cache**, kernel vẫn có thể xử lý syscall đọc mà không phải chờ physical disk read. Với memory-mapped file, ứng dụng có thể gặp page fault thay vì đường đi syscall `read` thông thường cho từng lần truy cập.

### 2.2. Tại sao nhiều syscall nhỏ có thể đắt?

Khi một chương trình yêu cầu một byte mỗi lần trên đường I/O không được buffer, tổng chi phí có thể bao gồm:

- Nhiều lần qua ranh giới user/kernel.
- Kiểm tra file descriptor/socket state và dispatch native.
- Phí lập lịch, synchronization và chờ I/O.
- Nhiều thao tác copy/metadata nhỏ.
- Độ trễ lặp của filesystem hoặc network stack.

Với dữ liệu kích thước `N` và chunk `B`, số **yêu cầu đọc bulk của ứng dụng** có thể xấp xỉ `ceil(N/B)` nếu mỗi lần thật sự trả đủ B byte. Nhưng đây **không phải công thức cam kết cho syscall**: `read` có thể trả ngắn hơn yêu cầu, EOF cần được kiểm tra, và wrapper/JDK/OS có thể dùng chiến lược buffering riêng.

~~~text
Dữ liệu:      [--------------------------------] 32 KiB

Cách 1:      32768 yêu cầu đọc 1 byte (ở cấp ứng dụng)
Cách 2:          4 yêu cầu đọc 8 KiB (nếu tất cả đọc đầy)
Cách 3:          1 yêu cầu đọc 32 KiB (nếu đầy)

Số syscall tương ứng: KHÔNG thể suy ra chỉ từ sơ đồ này.
~~~

Với socket, một lần `read(byte[])` có thể nhận ít byte hơn `len`, ngay cả ở blocking mode. Không viết parser giả định một lần bulk read trả đúng số byte đã yêu cầu.

### 2.3. Đọc bulk thay vì read từng byte

Ví dụ kiểu đọc từng byte, thường không lý tưởng với file lớn:

~~~java
int b;
while ((b = in.read()) != -1) {
    out.write(b);
}
~~~

Cách xử lý theo buffer:

~~~java
byte[] buffer = new byte[16 * 1024];
for (int n; (n = in.read(buffer)) != -1; ) {
    if (n == 0) {
        continue; // hoặc xử lý theo đặc điểm stream để tránh busy loop
    }
    out.write(buffer, 0, n);
}
~~~

Đây là **hai đoạn mã minh họa** với `InputStream in` và `OutputStream out` đã quản lý vòng đời; không phải chương trình độc lập. Với một số stream đặc biệt/non-blocking bridge có thể trả 0, không được tạo busy spin vô hạn. Thư viện `InputStream.transferTo(OutputStream)` của Java 17 cũng có thể giúp copy byte stream bằng buffer mà không cần tự viết vòng lặp.

**Điểm quan trọng:** Nếu `in` đã là `BufferedInputStream` thì vòng đọc từng byte có thể chỉ gọi xuống lớp nguồn ít lần. Nhưng vòng lặp từng byte vẫn trả chi phí Java dispatch, kiểm tra và ghi từng byte. Vì thế thiết kế bulk read/write rõ ràng thường dễ kiểm soát hơn.

## 3. BufferedInputStream/BufferedOutputStream giảm loại chi phí nào?

`BufferedInputStream` giữ một vùng đệm trong Java để phục vụ nhiều lần đọc nhỏ mà không phải gọi lớp bên dưới tương ứng từng byte. `BufferedOutputStream` gom nhiều lần ghi nhỏ để tạo các write xuống stream bên dưới theo khối.

Ví dụ:

~~~java
try (InputStream in = new BufferedInputStream(
        Files.newInputStream(source), 64 * 1024);
     OutputStream out = new BufferedOutputStream(
        Files.newOutputStream(target), 64 * 1024)) {
    in.transferTo(out);
}
~~~

Đoạn minh họa yêu cầu `Path source`, `Path target` và import `java.io.*`, `java.nio.file.*`. `transferTo` đi qua `InputStream`/`OutputStream` tương ứng, không tự cam kết zero-copy hoặc một syscall cho toàn file. Đóng outermost stream sẽ flush/đóng lớp trong theo hợp đồng, vì vậy `try-with-resources` giúp quản lý vòng đời.

### Khi buffering có lợi

- Ứng dụng thường yêu cầu nhiều read/write rất nhỏ.
- Lớp bên dưới có chi phí I/O cao khi gọi lặp.
- Ghi được gom thành khối, tránh phát sinh nhiều lần dispatch xuống lớp nguồn.
- Parser đọc từng ký tự/dòng, nhưng nguồn thực là byte stream.

### Khi buffering có thể không giúp nhiều

- Đường gọi đã xử lý theo buffer lớn sẵn (ví dụ `Files.copy` hay vòng đọc bulk).
- Lớp byte stream bên dưới/JDK đã có internal buffer/readahead.
- I/O bị giới hạn bởi disk/network bandwidth thay vì syscall dispatch.
- Kích thước buffer lớn gây áp lực heap, tăng allocation/GC hoặc vượt nhu cầu.
- Ứng dụng cần flush thật sự sau mỗi record; gom buffer quá lâu có thể tăng latency.
- Wrapping thêm tạo copy và các nhánh xử lý trong trường hợp không còn chi phí nhỏ để tối ưu.

**Không có quy tắc “càng nhiều lớp BufferedInputStream càng nhanh”.** Một layer buffer được chọn đúng có thể đủ. Các buffer nằm ở **những cấp khác nhau** đôi khi có chức năng khác nhau, đặc biệt byte buffer và char buffer.

### Read-ahead và write-behind

Buffer đọc có thể nạp nhiều byte hơn ứng dụng đang cần để các read kế tiếp rẻ hơn. Buffer ghi có thể trì hoãn việc gửi byte xuống underlying stream tới khi buffer đầy, `flush()` hoặc `close()`.

Điều này quan trọng với protocol tương tác: nếu bạn gửi một lệnh ngắn qua `BufferedOutputStream` rồi đợi phản hồi, **hãy flush đúng thời điểm** để dữ liệu không chỉ nằm ở user-space buffer. `flush()` không có nghĩa dữ liệu đã được lưu bền trên storage hoặc peer đã xử lý; nó chỉ yêu cầu đẩy output đã buffer theo hợp đồng các lớp.

## 4. Lớp chuyển đổi charset: bytes khác characters

**Charset** định nghĩa cách chuyển giữa chuỗi ký tự Unicode của Java và byte của dữ liệu ngoài. Hai chiều:

~~~text
bytes  -- CharsetDecoder --> Unicode chars/String
chars  -- CharsetEncoder --> bytes
~~~

Trong Java:

- `InputStream` đọc **bytes**.
- `Reader` đọc **UTF-16 code units** (Java `char`), không trực tiếp là byte.
- `OutputStream` ghi **bytes**.
- `Writer` ghi **chars/String** và lớp bridge có thể encode thành bytes.
- `InputStreamReader` nối byte input với char input thông qua decoder.
- `OutputStreamWriter` nối char output với byte output thông qua encoder.

Ví dụ cấu trúc hợp lý để đọc file text:

~~~java
try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(
                Files.newInputStream(path),
                StandardCharsets.UTF_8))) {
    String line;
    while ((line = reader.readLine()) != null) {
        // Xử lý text đã được decode.
    }
}
~~~

Đoạn mã minh họa cần `Path path`, `StandardCharsets` và các import Java tương ứng. `InputStreamReader` đã thực hiện decoding. `BufferedReader` buffer chars để đọc theo dòng hiệu quả, chứ **không decode lại**.

### UTF-8: byte, char, code point không cùng đơn vị

Với string `"Java: tiếng Việt 🌍"`:

- `String.length()` đếm **UTF-16 code units**, không phải số Unicode code points.
- Một emoji ngoài BMP như `🌍` dùng **surrogate pair** (2 `char`).
- UTF-8 mã hóa dấu tiếng Việt và emoji bằng nhiều byte.
- Một số tiếng Việt có thể biểu diễn dưới dạng precomposed hoặc combining sequence; byte length còn phụ thuộc cách chuẩn hóa Unicode.

Không được suy luận `data.length == text.length()` sau `text.getBytes(UTF_8)`. Nếu thao tác xử lý là **byte protocol**, đặt giới hạn bằng byte; nếu là **ký tự người dùng nhìn thấy**, còn có khái niệm grapheme cluster ngoài code point.

### Chi phí của conversion

Decoding/encoding có thể tốn:

1. Quét byte hoặc UTF-16 code units.
2. Duy trì trạng thái cho multibyte/escape sequences của charset.
3. Kiểm tra malformed/unmappable sequences.
4. Ghi dữ liệu kết quả vào `char[]`, `String`, `byte[]` hoặc buffer.
5. Cấp phát các đối tượng trung gian nếu ứng dụng chuyển kiểu lặp đi lặp lại.

Vì vậy pipeline như **bytes → String → bytes → String** không có lợi nếu chương trình không hề cần hiểu nội dung text. Ví dụ copy ZIP, PNG hoặc file bất kỳ nên giữ **byte-oriented**; chuyển sang String bằng charset có thể phá dữ liệu nhị phân.

## 5. Ranh giới chunk và ký tự nhiều byte

UTF-8 có thể mã hóa một Unicode code point thành **nhiều byte**. Một network/file chunk có thể kết thúc **giữa một ký tự**, ví dụ emoji 4 byte bị chia thành 2 + 2 byte giữa hai lần đọc.

~~~text
UTF-8 emoji 🌍: [F0 9F 8C 8D]

Network read #1: F0 9F
Network read #2: 8C 8D
~~~

Lỗi thường thấy là:

~~~java
// PHẢN VÍ DỤ nếu chunk có thể cắt giữa UTF-8 sequence:
String left  = new String(chunk1, StandardCharsets.UTF_8);
String right = new String(chunk2, StandardCharsets.UTF_8);
String text  = left + right;
~~~

Nếu `chunk1` và `chunk2` cắt giữa một multibyte sequence, mỗi lần decode độc lập có thể **thay byte chưa đầy đủ bằng replacement character** hoặc gây lỗi tùy decoder configuration. Ghép các string bị lỗi sau đó không phục hồi được byte gốc.

**Giải pháp đúng:**

- Với stream, dùng **một `InputStreamReader` liên tục** cho toàn luồng để decoder giữ trạng thái.
- Với NIO `ByteBuffer`, dùng `CharsetDecoder` có state, giữ byte chưa tiêu thụ qua `compact()` giữa các lần `decode(input, output, endOfInput)`, cuối stream thực hiện `decode(..., true)` và `flush(output)`.
- Không giả định vị trí chia byte trùng code point boundary hoặc line/message boundary.
- Với protocol có frame chứa UTF-8, nhận **đủ byte của frame** rồi decode một lần theo charset đã thống nhất.

Điều này vừa là vấn đề **tính đúng đắn**, vừa là vấn đề hiệu năng: xây String cho từng chunk quá nhỏ tạo allocation và decoder setup lặp lại.

### CharsetDecoder và xử lý lỗi

`CharsetDecoder` hỗ trợ `CodingErrorAction.REPORT`, `REPLACE` và `IGNORE` cho malformed/unmappable input. Với dữ liệu protocol cần xác thực nghiêm ngặt, `REPORT` thường giúp phát hiện lỗi thay vì âm thầm làm biến dạng tên định danh, log hay payload.

~~~java
CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);

String decoded = decoder.decode(ByteBuffer.wrap(payload)).toString();
~~~

Đây là đoạn mã minh họa có `byte[] payload` và các import `java.nio.*`, `java.nio.charset.*`. Khi byte không hợp lệ, `decode(ByteBuffer)` có thể ném `CharacterCodingException`. Đối với API tiện lợi như `Charset.decode(ByteBuffer)`, Java 17 có thể dùng **replacement behavior** cho invalid sequences; đừng mặc định chúng luôn ném lỗi.

**Đừng lạm dụng `IGNORE`** cho dữ liệu có ý nghĩa bảo mật: bỏ byte lỗi có thể biến hai tên/ID khác nhau thành cùng một chuỗi.

## 6. Số lớp wrapper: không chỉ là đếm object

Java I/O dùng **decorator/wrapper** để kết hợp các chức năng. Ví dụ:

~~~text
BufferedReader
     |
InputStreamReader(UTF-8)
     |
BufferedInputStream
     |
FileInputStream
~~~

Các lớp làm những công việc khác nhau:

| Lớp | Công việc chính | Chi phí có thể thêm |
|---|---|---|
| `FileInputStream` | Lấy byte từ file | Native I/O, copy, synchronization tùy đường đi |
| `BufferedInputStream` | Buffer byte | Java byte array, copy, logic buffer |
| `InputStreamReader` | Decode byte thành UTF-16 chars | Decoder CPU, char output buffer |
| `BufferedReader` | Buffer char và cung cấp `readLine` | Char array, scanning line separators |
| `DataInputStream` | Đọc primitive/binary structures | Nhiều read nhỏ, các phép ghép byte |
| `GZIPInputStream` | Giải nén gzip | CPU decompression, buffer nội bộ |
| `CipherInputStream` | Giải mã theo cipher | CPU crypto, padding/state và buffer |
| `CheckedInputStream` | Cập nhật checksum | Phép tính checksum trên byte |
| `ObjectInputStream` | Deserialize object graph | Reflection/metadata, allocation, security risks |

**Số lớp wrapper không tỷ lệ tuyến tính với số syscalls.** Một lớp `BufferedInputStream` có thể giảm underlying calls mạnh; thêm `BufferedReader` có thể giúp đọc dòng hiệu quả. Ngược lại, thêm hai hay ba buffer ở cùng một tầng mà không có lý do có thể tăng copy, allocations và độ phức tạp mà không giảm syscall thêm.

### Wrapper đúng thứ tự có ý nghĩa

Với text:

~~~java
Reader reader = new BufferedReader(
        new InputStreamReader(
                Files.newInputStream(file),
                StandardCharsets.UTF_8));
~~~

Với ghi text:

~~~java
Writer writer = new BufferedWriter(
        new OutputStreamWriter(
                Files.newOutputStream(file),
                StandardCharsets.UTF_8));
~~~

Đây là hai **đoạn mã minh họa**, phải đóng outermost `Reader`/`Writer` sau khi dùng; `file` là `Path` hợp lệ. Có thể dùng gọn:

~~~java
try (BufferedReader in =
        Files.newBufferedReader(file, StandardCharsets.UTF_8);
     BufferedWriter out =
        Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
    String line;
    while ((line = in.readLine()) != null) {
        out.write(line);
        out.newLine();
    }
}
~~~

Mã minh họa có `Path file` và `Path output`. **Lưu ý semantics:** `readLine()` loại ký tự kết thúc dòng, `newLine()` tạo line separator theo hệ thống; ví dụ này **không copy text file bit-for-bit**. Nếu cần giữ chính xác CRLF/LF, BOM, trailing newline hay bytes gốc, hãy copy bytes thay vì line-oriented conversion.

### BufferedInputStream có cần thêm dưới InputStreamReader?

**Không có đáp án luôn đúng.** `InputStreamReader` tự có cơ chế đọc byte/readahead cần cho charset decoder; thêm `BufferedInputStream` có thể không giúp gì đáng kể và đôi khi tạo thêm copy. Nếu chương trình đã dùng `BufferedReader(InputStreamReader(...))`, hãy đo trước khi thêm lớp byte buffer nữa. Nhưng nếu một phần pipeline khác cũng đọc byte từ nguồn, có thể cần chiến lược buffering thống nhất — tuyệt đối không dùng đồng thời hai reader trên cùng stream mà không điều phối vì readahead sẽ làm rối vị trí dữ liệu.

### Lớp chức năng không phải "thừa" chỉ vì tốn CPU

`GZIPInputStream` hoặc `CipherInputStream` bổ sung **nghĩa vụ giải nén/giải mã**. Bỏ chúng có thể nhanh hơn nhưng **đổi hẳn semantics**, không còn cùng tác vụ. Tối ưu hợp lý là chọn đúng vị trí buffer và hạn chế intermediate copies **trong cùng một chức năng**, không cắt bỏ tính đúng đắn.

## 7. Ví dụ Java 17+ chạy được: đếm underlying calls và kiểm tra charset

Ví dụ này mô phỏng một nguồn byte bằng `ByteArrayInputStream`, cố ý **không dùng file** để các số đếm Java calls xác định trên chính class fixture. Nó cho thấy hiệu ứng buffer 8 byte, đồng thời chứng minh khác biệt số byte UTF-8 với UTF-16/code points và việc strict decoder từ chối UTF-8 lỗi.

**Tệp: `IoCostDemo.java`**

~~~java
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public class IoCostDemo {
    static class MeasuredInputStream extends FilterInputStream {
        int singleCalls, bulkCalls;

        MeasuredInputStream(byte[] bytes) {
            super(new ByteArrayInputStream(bytes));
        }

        @Override
        public int read() throws IOException {
            singleCalls++;
            return in.read();
        }

        @Override
        public int read(byte[] b, int off, int len)
                throws IOException {
            bulkCalls++;
            return in.read(b, off, len);
        }
    }

    public static void main(String[] args) throws Exception {
        // Unicode escapes giữ dữ liệu fixture ổn định trên console khác nhau.
        String original = "Java: ti\u1EBFng Vi\u1EC7t \uD83C\uDF0D";
        byte[] payload = original.getBytes(StandardCharsets.UTF_8);

        // Đọc từng byte trực tiếp, gồm cả lần cuối phát hiện EOF.
        MeasuredInputStream direct = new MeasuredInputStream(payload);
        int directBytes = 0;
        while (direct.read() != -1) {
            directBytes++;
        }

        // Ứng dụng vẫn read từng byte; buffer gom các request xuống nguồn.
        MeasuredInputStream backing = new MeasuredInputStream(payload);
        int bufferedBytes = 0;
        try (BufferedInputStream buffered =
                     new BufferedInputStream(backing, 8)) {
            while (buffered.read() != -1) {
                bufferedBytes++;
            }
        }

        System.out.println("utf8Bytes=" + payload.length);
        System.out.println("singleReadCalls=" + direct.singleCalls);
        System.out.println(
                "bufferedBulkCalls=" + backing.bulkCalls);
        System.out.println(
                "sameByteCount=" + (directBytes == bufferedBytes));

        // Decode đúng UTF-8 và phát hiện input malformed thay vì thay chữ.
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        String restored = decoder.decode(
                ByteBuffer.wrap(payload)).toString();

        System.out.println("utf16Units=" + restored.length());
        System.out.println("unicodeCodePoints=" +
                restored.codePointCount(0, restored.length()));
        System.out.println("roundTrip=" + original.equals(restored));

        try {
            decoder.reset();
            decoder.decode(ByteBuffer.wrap(
                    new byte[]{(byte) 0xC3, 0x28}));
            System.out.println("badUtf8Rejected=false");
        } catch (CharacterCodingException expected) {
            System.out.println("badUtf8Rejected=true");
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac IoCostDemo.java
java IoCostDemo
~~~

**Stdout đã thử trên JDK 17 (với `ByteArrayInputStream` và buffer 8 byte):**

~~~text
utf8Bytes=25
singleReadCalls=26
bufferedBulkCalls=5
sameByteCount=true
utf16Units=19
unicodeCodePoints=18
roundTrip=true
badUtf8Rejected=true
~~~

### Giải thích kết quả

1. Chuỗi `Java: tiếng Việt 🌍` có **25 byte UTF-8**, nhưng `String.length() == 19` UTF-16 code units và **18 Unicode code points**. Emoji `🌍` chiếm 2 UTF-16 units, 1 code point và 4 byte UTF-8; tiếng Việt có hai ký tự được mã hóa nhiều byte.
2. 25 byte được đọc từng byte; vòng lặp còn gọi `read()` một lần để nhận `-1` (EOF). Vì vậy `singleReadCalls=26`.
3. `BufferedInputStream(backing, 8)` dùng các lần đọc 8 + 8 + 8 + 1 byte và một lần thấy EOF: **5 lần đọc bulk** xuống `MeasuredInputStream`, trong khi ứng dụng vẫn gọi 26 lần `buffered.read()`. Đây là con số của fixture cụ thể, **không phải 5 syscall**.
4. `sameByteCount=true` xác nhận buffer không làm mất hay nhân đôi byte.
5. CharsetDecoder `REPORT` decode lại dữ liệu đúng và từ chối cặp byte UTF-8 lỗi `C3 28`. Việc dùng `reset()` trước lần decode thứ hai tuân thủ vòng đời state của decoder.

### Những gì demo *không* đo

- Không đo physical disk read hay kernel syscall: nguồn chỉ là `ByteArrayInputStream`.
- Không đo throughput/tốc độ: stdout là **counter và kiểm tra đúng đắn**, không là kết quả benchmark.
- Không đo chi phí của tất cả wrapper khác: không phải mọi stream có hành vi giống `ByteArrayInputStream`.
- Không đo tác dụng của page cache, JIT, thiết bị, filesystem hoặc network.
- Không chứng minh cấu hình buffer 8 byte là tối ưu; đó chỉ là kích thước nhỏ giúp nhìn được số lần read.

**Quy tắc đo lường:** nếu muốn đánh giá syscall, phải dùng nguồn file/socket thật và công cụ OS/JVM phù hợp, không chỉ nhìn vào counter Java trong một stream giả lập.

## 8. Tối ưu pipeline text: tránh decode/encode lặp

Một pipeline không hiệu quả thường có kiểu:

~~~text
raw bytes
  -> UTF-8 decode thành String
  -> UTF-8 encode lại byte[]
  -> decode lại thành String
  -> parse rồi format
  -> encode sang UTF-8 trước khi ghi
~~~

Nếu chương trình chỉ **chuyển tiếp nguyên nội dung bytes**, các lần round-trip text có thể là công việc không cần thiết:

~~~java
try (InputStream in = Files.newInputStream(source);
     OutputStream out = Files.newOutputStream(target)) {
    in.transferTo(out); // Byte-to-byte copy
}
~~~

Đây là mã minh họa với `Path source`/`Path target` hợp lệ. Nó **giữ nguyên byte** và không cần charset. Việc copy bytes không có nghĩa kernel zero-copy; `InputStream.transferTo` được phép dùng buffer ở Java.

Ngược lại, nếu cần **lọc dòng, chuyển format, kiểm tra text**, hãy decode một lần ở ranh giới vào và encode một lần ở ranh giới ra:

~~~text
InputStream --> InputStreamReader(UTF-8)
            --> BufferedReader / parser / business logic
            --> BufferedWriter / OutputStreamWriter(UTF-8)
            --> OutputStream
~~~

### Tránh các String trung gian không cần

- `new String(chunk, UTF_8)` cho từng read nhỏ có thể tạo nhiều allocation và làm hỏng split multibyte sequences.
- Gọi `text.getBytes(UTF_8)` trong vòng lặp khi `text` không đổi có thể tạo byte arrays lặp lại; encode trước nếu phù hợp.
- `readLine()` tạo một String cho mỗi dòng. Với file log chứa hàng triệu dòng rất ngắn, chi phí allocation và parse có thể đáng kể.
- `Files.readString`/`readAllBytes` tiện nhưng tải toàn file vào bộ nhớ; không phù hợp file rất lớn nếu không có memory budget.
- Charset conversion không phải thứ nên xóa nếu cần giữ đúng encoding; tối ưu bằng streaming decoder và buffering hợp lý.

### CharsetEncoder/Decoder không dùng chung tùy ý giữa threads

`CharsetDecoder` và `CharsetEncoder` có **state** cho nhiều bước chuyển đổi. Không chia sẻ cùng một instance stateful giữa nhiều threads/luồng độc lập mà không có synchronization và reset protocol phù hợp. `Charset` và `StandardCharsets.UTF_8` là đối tượng mô tả charset; có thể dùng chúng để tạo coder riêng cho mỗi stream.

Với UTF-8, phải xác định **malformed input policy**. Thay thế lỗi bằng `U+FFFD` có thể phù hợp xử lý text không quan trọng, nhưng không nên mặc định cho dữ liệu identity, protocol keys hoặc trường cần so khớp byte/chữ chính xác.

## 9. Chọn kích thước buffer cho file lớn

Không có “magic buffer size” tối ưu cho mọi thiết bị, file system và ứng dụng. Một số điểm thực tế:

- **Quá nhỏ:** tăng số lần vào Java layer và thường làm tăng số underlying calls/syscalls.
- **Vừa đủ:** thường giảm call overhead mà không chiếm quá nhiều RAM.
- **Quá lớn:** có thể tăng per-connection memory, allocation/GC, cache pressure, latency time-to-first-byte hoặc mất cân bằng concurrency.
- **Nhiều stream đồng thời:** memory ≈ số stream × tổng dung lượng buffer mà mỗi stream giữ, chưa tính page cache và network buffers.
- **File I/O:** OS có page cache và readahead; tăng Java buffer mãi không tự làm storage nhanh hơn.
- **Mạng:** nhiều ghi nhỏ có thể tương tác với TCP buffering/Nagle/backpressure/TLS records, không chỉ syscall count.

Ví dụ nếu có 10.000 kết nối và mỗi kết nối giữ một byte buffer 64 KiB, riêng các buffer này chiếm xấp xỉ **625 MiB** (10.000 × 65.536 bytes), chưa tính objects và buffers ở tầng khác. Đây là **phép tính kích thước thô**, không phải đo memory thực tế.

Với application, có thể benchmark các chunk như **4 KiB, 16 KiB, 64 KiB, 256 KiB**, nhưng không gán chúng là giá trị chuẩn. Chọn buffer theo workload, latency, số channel mở đồng thời và memory budget.

### ByteBuffer heap và direct

Trong NIO:

- `ByteBuffer.allocate(size)` cấp buffer heap.
- `ByteBuffer.allocateDirect(size)` cấp direct buffer ngoài Java heap; nhiều native I/O implementation có thể tận dụng nó để giảm một số intermediate copies.
- Direct buffer có chi phí cấp phát/thu hồi và gây memory pressure ở khu vực ngoài heap; không nên cấp mới cho từng read rất nhỏ.
- `FileChannel.map` là memory mapping, có cơ chế page fault riêng; không đồng nghĩa luôn không copy hoặc luôn ít syscall/nhanh hơn.

**Không có cam kết “direct luôn nhanh hơn heap” hay “mmap luôn nhanh hơn buffered stream”**. Cần đo trên JDK/OS/filesystem và pattern truy cập thực.

### FileChannel.transferTo/transferFrom

`FileChannel.transferTo(position, count, target)` và `transferFrom` có thể tận dụng các đường native transfer trên một số nền tảng, nhưng không bảo đảm **mọi byte trong count được chuyển trong một invocation**. Kết quả trả về là số byte thực sự chuyển, nên cần tăng file position theo số byte đã chuyển và xử lý zero-progress/EOF có chính sách. Không dùng API này cho text nếu còn cần charset conversion ở tầng ứng dụng mà không giải quyết semantics byte.

## 10. Benchmark đúng để xác định nút thắt

### 10.1. Xác định workload và đại lượng

Một phép đo hữu ích cần nêu:

- Kích thước dữ liệu, số lượng file/record, độ dài record, tỷ lệ ASCII/Unicode.
- Loại thiết bị (local SSD, network filesystem, memory cache), trạng thái cold/warm.
- Read sequential hay random; sync hay async; số luồng và số kết nối.
- Charset, malformed input policy và có cần parse line/content không.
- Dung lượng buffer ở **mỗi lớp**.
- CPU time, wall time, throughput, latency p50/p95/p99, allocations/GC.
- Số Java calls, OS syscalls và disk/network I/O nếu có instrumentation.

Phải đo **end-to-end**: nếu tăng buffer giúp giảm syscalls nhưng làm latency/tổng CPU tăng vì copy nhiều hơn hoặc trì hoãn flush, chưa chắc là tối ưu cho sản phẩm.

### 10.2. Các baseline nên thử

| Baseline | Ý nghĩa |
|---|---|
| `InputStream.read()` từng byte | Điểm đối chứng API-call-heavy, không mặc định là mỗi byte = syscall |
| `read(byte[16KiB])` | Bulk read với buffer một tầng |
| `BufferedInputStream` + read() từng byte | Quan sát giảm underlying calls nhờ byte buffering |
| `BufferedReader(InputStreamReader(UTF_8))` | Workload text/line parsing |
| `InputStreamReader` không BufferedReader | Đối chứng cho chi phí char buffering |
| `Files.newBufferedReader(UTF_8)` | API idiomatic đọc text |
| `FileChannel` + `ByteBuffer` | I/O channel với buffer do ứng dụng quản lý |
| `FileChannel.transferTo` | Byte-transfer path khi không cần parse nội dung |

**Không so hai baseline nếu chúng không làm cùng một việc**. Ví dụ bulk byte copy không thể so trực tiếp với decode UTF-8 + parse JSON, vì công việc khác nhau.

### 10.3. Công cụ quan sát

- **JMH:** microbenchmark phần Java CPU, buffer, charset conversion có warm-up và fork; tránh tự lấy một kết quả `System.nanoTime()` làm kết luận.
- **Java Flight Recorder (JFR):** giúp quan sát allocation, Java events, file/socket I/O và thread activity tùy cấu hình JDK.
- **Async profiler/CPU profiler:** xác định thời gian CPU ở decoding, encoding, buffer copy, parsing và native methods.
- **Linux strace/perf hoặc OS-specific tools:** quan sát syscalls và counters cấp hệ điều hành, với overhead do tracing.
- **Disk/network metrics:** giúp phân biệt data cached và physical I/O.
- **GC logs/JFR allocation profiling:** xác định nhiều String/byte[] hoặc wrapper objects có trở thành nút thắt không.

Ví dụ chạy trên Linux **nếu đã cài và được phép sử dụng strace**:

~~~bash
strace -c -e trace=read,write,openat java IoCostDemo
~~~

Lệnh **chỉ minh họa công cụ**, và demo `IoCostDemo` dùng `ByteArrayInputStream` nên không đại diện workload file I/O. Nếu đo Java process bằng tracing syscalls, các syscall của **JVM startup, runtime, class loading và stdout** cũng được tính. Cần tách workload mục tiêu (ví dụ qua tracing scope/tiến trình chạy dài hoặc đối chiếu baseline), tránh kết luận từ số tổng.

### 10.4. Sai lầm benchmark thường gặp

1. Chỉ chạy một lần và đo thời gian khi JIT chưa warm.
2. Lần một cold file cache, lần hai warm rồi so hai chiến lược khác nhau.
3. Tạo input/encode data trong đoạn benchmark đo đọc file.
4. Không kiểm tra dữ liệu đầu ra, khiến một phương án nhanh vì **làm ít việc hơn**.
5. Không giới hạn memory, dẫn đến GC/swap làm sai mục tiêu.
6. Chỉ xem CPU khi bottleneck thật là device I/O hoặc network backpressure.
7. Chỉ xem số syscall khi overhead lớn hơn nằm ở UTF-8 decoding hoặc parsing.
8. Dùng output trên console trong hot loop: `println` có chi phí đồng bộ/format/I/O lớn.
9. Coi direct buffer hay thêm wrapper là tối ưu mặc định.
10. Không đo p99 latency trong hệ thống phục vụ nhiều request đồng thời.

## 11. Checklist thiết kế pipeline cho file lớn

Để vừa **đúng dữ liệu** vừa có hiệu năng tốt, nên hỏi theo thứ tự:

1. **Dữ liệu là bytes hay text?** Nếu binary hoặc copy nguyên file, không decode charset không cần thiết.
2. **Ứng dụng đọc theo block, line hay message?** Chọn `read(byte[])`/`ByteBuffer` hoặc `BufferedReader` đúng yêu cầu.
3. **Ai sở hữu charset?** Chỉ định `StandardCharsets.UTF_8` (hoặc encoding thực của dữ liệu), không phỏng đoán từ platform default.
4. **Decoder state được giữ qua chunk?** Dùng `InputStreamReader` hoặc stateful `CharsetDecoder` đúng cách.
5. **Có wrapper trùng chức năng?** Vẽ sơ đồ các byte/char buffers và vai trò thực tế của từng lớp.
6. **Buffer/chunk bao lớn?** Đo với workload thực, cân đối concurrency memory.
7. **Khi nào phải flush?** Ghi text/binary có protocol boundary khác với flush về storage; đừng nhầm `flush` và `FileChannel.force`.
8. **Đã xử lý partial read/write và EOF?** Không giả định một lần I/O trả hết bytes; tránh busy spin khi không tiến triển.
9. **Có giới hạn memory/CPU?** Không `readAllBytes` với file khổng lồ nếu thiếu quota; stream theo chunk.
10. **Đã đo đúng nút thắt?** Quan sát syscall, charset CPU, allocations, device latency thay vì chọn giải pháp theo cảm giác.

## 12. Những hiểu lầm phổ biến

1. **“Mỗi lần gọi `InputStream.read()` là một syscall.”** Sai: nguồn in-memory hoặc wrapper có thể không xuống kernel.
2. **“Mỗi syscall đọc file đều gây physical disk read.”** Sai: OS page cache có thể phục vụ dữ liệu.
3. **“Đọc 1 byte từng lần luôn gây một syscall/byte.”** Không đúng khi đã buffer; số Java calls vẫn có thể lớn.
4. **“Dùng buffer càng lớn càng tốt.”** Sai: bộ nhớ/copy/latency và concurrency có thể bị ảnh hưởng.
5. **“Hai lớp BufferedInputStream luôn nhanh hơn một.”** Sai: có thể tăng overhead mà không giảm underlying calls.
6. **“InputStreamReader và BufferedReader cùng decode UTF-8 hai lần.”** Sai: InputStreamReader decode, BufferedReader buffer ký tự.
7. **“UTF-8 byte count bằng String.length().”** Sai: length là số UTF-16 code units.
8. **“Có thể new String riêng cho mọi byte chunk rồi ghép.”** Sai: split UTF-8 sequence có thể bị lỗi.
9. **“Charset.defaultCharset() giống nhau trên mọi Java 17 runtime.”** Không bảo đảm: JDK 17 còn có khác biệt platform/locale; chỉ định charset rõ ràng.
10. **“Chuyển binary thành String rồi lại getBytes không mất gì.”** Sai: charset conversion có thể thay byte invalid.
11. **“`CharsetDecoder.REPORT` và `REPLACE` cùng trả dữ liệu như nhau.”** Sai: REPORT báo lỗi, REPLACE thay byte lỗi bằng ký tự thay thế.
12. **“`flush()` bảo đảm dữ liệu lưu bền hoặc peer đã nhận.”** Sai: nghĩa flush tùy wrapper, không thay fsync/ACK.
13. **“`Files.readString` thích hợp với mọi file vài GiB.”** Sai: có thể dùng quá nhiều heap; stream theo chunk.
14. **“Direct ByteBuffer luôn nhanh hơn heap ByteBuffer.”** Sai: còn phụ thuộc operations, allocations và I/O backend.
15. **“`transferTo` luôn chuyển đủ count một lần.”** Sai: kiểm tra số byte trả về.
16. **“Giảm số Java calls = giảm bằng số syscalls.”** Sai: mối quan hệ tùy implementation/wrapper.
17. **“Đo `System.nanoTime()` một lần là đủ benchmark.”** Sai: JIT, OS cache, allocation và noise ảnh hưởng kết quả.

## 13. Tự kiểm tra kiến thức

**Câu 1:** Nếu đọc một `ByteArrayInputStream` 10.000 lần bằng `read()`, có chắc đã gọi 10.000 syscall không?

**Đáp án:** Không. Dữ liệu nằm trong Java heap; các lời gọi read cấp Java không nhất thiết gây OS syscall.

**Câu 2:** Vì sao `BufferedInputStream` giảm underlying read calls nhưng vẫn có thể không giúp nhiều với `Files.copy` đã dùng bulk I/O?

**Đáp án:** Khi ứng dụng đã đọc theo block và backend đã buffer, thêm một layer có thể ít giảm call thực tế nhưng tăng chi phí copy và object.

**Câu 3:** Chuỗi có ký tự `🌍` dài mấy Java `char` và bao nhiêu UTF-8 byte cho riêng emoji đó?

**Đáp án:** 2 UTF-16 code units (`String.length` tăng 2) và 4 byte UTF-8, nhưng chỉ 1 Unicode code point.

**Câu 4:** Một emoji UTF-8 4 byte bị tách qua hai lần socket read. Có nên gọi `new String(chunk, UTF_8)` riêng mỗi chunk?

**Đáp án:** Không. Phải giữ decoder state hoặc gom đủ byte frame, tránh malformed/replacement do tách sequence.

**Câu 5:** Ghi các message ngắn qua BufferedOutputStream mà không flush rồi đợi response có thể gặp vấn đề gì?

**Đáp án:** Byte có thể vẫn ở buffer ứng dụng, chưa được đẩy xuống underlying stream, nên peer chưa thấy yêu cầu. Cần flush đúng protocol boundary; nhưng flush không thay ACK của ứng dụng.

**Câu 6:** Nếu phần lớn CPU nằm ở UTF-8 decoding, giảm 50% số syscall có chắc làm latency giảm 50% không?

**Đáp án:** Không. Tổng latency còn phụ thuộc phần việc khác; syscall chỉ là một thành phần. Cần đo end-to-end và CPU profile.

**Câu 7:** Tại sao không thể suy ra số syscalls từ `bufferedBulkCalls=5` trong ví dụ?

**Đáp án:** Đó chỉ là số lời gọi `read(byte[],off,len)` vào `MeasuredInputStream` bọc `ByteArrayInputStream`. Không có disk I/O; giữa Java calls và OS syscalls không có quan hệ 1:1 bắt buộc.

### Tài liệu chính thức Java 17

- [InputStream và transferTo](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStream.html)
- [BufferedInputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedInputStream.html)
- [BufferedOutputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedOutputStream.html)
- [InputStreamReader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStreamReader.html)
- [OutputStreamWriter](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/OutputStreamWriter.html)
- [BufferedReader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedReader.html)
- [BufferedWriter](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedWriter.html)
- [Charset, CharsetDecoder và CodingErrorAction](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/CharsetDecoder.html)
- [ByteBuffer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html)
- [FileChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileChannel.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="performance-and-large-files-004">So sánh stream có bộ đệm, Files.copy và FileChannel</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh stream có bộ đệm, Files.copy và FileChannel khi xử lý tệp lớn

## 1. Vấn đề cần giải quyết

Sao chép một tệp có vẻ đơn giản: đọc byte từ nguồn rồi ghi byte vào đích. Tuy nhiên, với tệp hàng trăm MB, nhiều GB hoặc một luồng dữ liệu hoạt động liên tục, lựa chọn API ảnh hưởng đến **độ phức tạp code, lượng bộ nhớ Java cần cấp phát, số lần gọi I/O, khả năng tận dụng tối ưu hóa hệ điều hành, tính đúng đắn khi có lỗi và cách kiểm thử**.

Trong Java 17+, ba nhóm cách tiếp cận thường gặp:

1. **Stream có bộ đệm:** `BufferedInputStream` + `BufferedOutputStream`, đọc/ghi theo khối bytes.
2. **API cấp cao:** `Files.copy(...)`, yêu cầu Java/provider sao chép tệp hoặc stream theo chính sách options.
3. **NIO channel:** `FileChannel`, với `read`/`write` qua `ByteBuffer`, hoặc `transferTo`/`transferFrom` để yêu cầu chuyển bytes trực tiếp giữa các channel.

Điểm quan trọng nhất:

> **Không có một API luôn nhanh nhất cho mọi môi trường.** `Files.copy` thường là lựa chọn đầu tiên cho copy file đơn giản; buffered stream phù hợp khi cần xử lý byte stream/biến đổi dữ liệu; `FileChannel` hữu ích khi cần offset, chuyển từng vùng, tối ưu I/O theo trường hợp và kiểm soát tiến độ. Hiệu năng thực tế cần được đo trên tệp, thiết bị, OS, provider và workload cụ thể.

Bài này tập trung vào **copy dữ liệu nhị phân đúng nguyên trạng**. Nếu mục tiêu là chuyển đổi charset hoặc xử lý từng dòng văn bản, dùng `Reader`/`Writer` và thiết kế encoding riêng; không biến binary bytes thành String để sao chép.

### 1.1. Luồng dữ liệu tổng quát

```text
Cách A — buffered streams:
Disk/FileSystem → InputStream → [buffer byte] → mã ứng dụng
                                → [buffer byte] → OutputStream → Disk/FileSystem

Cách B — Files.copy:
Path nguồn ───── Files.copy(source, target, options) ─────→ Path đích
                   provider có quyền chọn cơ chế triển khai

Cách C — FileChannel:
FileChannel nguồn → transferTo / transferFrom → Channel đích
       hoặc
FileChannel nguồn → ByteBuffer → FileChannel đích
```

Các sơ đồ nói về **lớp API** chứ không cam kết bao nhiêu bản copy RAM, syscall hay DMA xảy ra dưới nền. Ngay cả cơ chế được gọi là “zero-copy” vẫn chịu điều kiện OS, filesystem, provider, thiết bị và loại channel đích.

## 2. Bảng so sánh nhanh

| Tiêu chí | BufferedInputStream/BufferedOutputStream | `Files.copy` | `FileChannel` |
|---|---|---|---|
| Mức trừu tượng | Đọc/ghi byte stream | Copy file/path hoặc một đầu stream ở mức cao | Truy cập channel byte với position, size, transfer |
| Mức code cần viết | Vòng `read/write` và xử lý EOF | Thường chỉ một lời gọi | Vòng lặp transfer hoặc đọc/ghi ByteBuffer |
| Bộ nhớ ứng dụng | Mảng tạm/buffer kích thước xác định | Implementation quyết định; không yêu cầu caller chứa cả file | Một `ByteBuffer` tái sử dụng, hoặc transfer với bộ đệm do provider/OS chọn |
| Kiểm soát offset | Phải skip/position qua API nguồn thích hợp | Không có tham số offset cho `Files.copy(Path,Path)` | Rõ ràng qua `position` và positional `read/write`/`transferTo` |
| Copy một file sang file | Được, nhưng nhiều code hơn | **Mặc định nên cân nhắc trước** | Được, hữu ích khi cần kiểm soát chi tiết |
| Copy một đoạn file | Có thể nhưng phải tự giới hạn | Không phải mục tiêu của overload Path→Path | Thuận tiện dùng `transferTo(start,count,...)` |
| Native transfer/zero-copy | Không có bảo đảm loại bỏ copy qua buffer user | Tùy provider/implementation | `transferTo` có thể dùng cơ chế tối ưu OS, **không bảo đảm** |
| Thuộc tính tệp | Tự xử lý metadata | Có `COPY_ATTRIBUTES` khi hỗ trợ | Không tự copy metadata chỉ vì chuyển content |
| File rất lớn | Xử lý theo khối, không cần giữ toàn file | Được thiết kế cho copy file thông thường | `size` và offset dùng `long`; vòng lặp xử lý partial transfer |
| Xử lý lỗi/cleanup | Tự kiểm soát hoàn toàn | Ngắn gọn nhưng vẫn có partial target khi thất bại | Cần kiểm soát số byte chuyển và dừng khi không tiến độ |
| Độ bền sau mất điện | Không tự bảo đảm | Không tự bảo đảm | Không tự bảo đảm; có `FileChannel.force` với phạm vi riêng |

**Chọn API theo yêu cầu trước, tối ưu sau.** Bài toán sao chép một ảnh 3 MB khác với gửi từng phần một log vài trăm GB tới socket, hoặc nhân bản hàng nghìn file nhỏ kèm metadata.

## 3. Buffered stream: đọc và ghi theo khối

### 3.1. Cách hoạt động

`InputStream.read()` từng byte có thể làm chương trình mất nhiều lượt chuyển giữa các tầng I/O. `BufferedInputStream` gom dữ liệu đọc vào buffer nội bộ; `BufferedOutputStream` gom những lần write nhỏ trước khi chuyển cho stream bên dưới.

Một copy theo khối không cần đọc toàn file:

```java
try (InputStream in = new BufferedInputStream(Files.newInputStream(source));
     OutputStream out = new BufferedOutputStream(
             Files.newOutputStream(
                     target,
                     StandardOpenOption.CREATE_NEW,
                     StandardOpenOption.WRITE))) {

    byte[] chunk = new byte[64 * 1024];
    int n;

    while ((n = in.read(chunk)) != -1) {
        out.write(chunk, 0, n);
    }
}
```

Đây là **đoạn mã trong method có xử lý `IOException`**, giả định `source` và `target` là `Path` đã xác định, với import `java.io.*`, `java.nio.file.Files` và `StandardOpenOption`. Nó minh họa luồng control chính, còn ví dụ hoàn chỉnh ở phần 7.

Ý nghĩa:

- `chunk` là buffer do caller cấp phát một lần, kích thước 64 KiB.
- `read(chunk)` trả số byte **thực tế** đọc được, không nhất thiết bằng 64 KiB.
- `-1` biểu thị EOF; khi đọc được `n` byte, chỉ có `chunk[0..n-1]` hợp lệ cho lượt đó.
- `out.write(chunk,0,n)` ghi đúng số byte đã đọc; bỏ tham số `n` có thể làm dữ liệu cuối bị lặp từ lượt trước.
- `try-with-resources` đóng các stream kể cả khi lỗi; đóng buffered output sẽ flush dữ liệu còn nằm trong bộ đệm Java trước khi đóng đầu ra bên dưới.
- `CREATE_NEW` tránh tình cờ ghi đè đường dẫn đích đã tồn tại; nếu đích đã có, lời gọi mở sẽ báo lỗi theo contract.

### 3.2. Buffered stream và mảng truyền vào có thể đều là buffer

Trong ví dụ có **ba mức** liên quan bộ nhớ:

1. Buffer nội bộ của `BufferedInputStream`.
2. Mảng `byte[] chunk` 64 KiB ứng dụng tái sử dụng.
3. Buffer nội bộ của `BufferedOutputStream`.

Vì ứng dụng đã đọc và ghi theo khối 64 KiB, việc bọc thêm hai `Buffered*Stream` đôi khi **không mang lại lợi ích rõ rệt** so với `Files.newInputStream`/`newOutputStream` với cùng mảng lớn. Buffered wrapper đặc biệt hữu ích nếu phần xử lý phía trên gọi `read/write` nhiều lần với khối nhỏ.

Không kết luận “càng nhiều lớp buffer càng nhanh”. Bộ nhớ dư, sao chép thêm qua các tầng và flush pattern có thể làm khác hiệu năng. **Benchmark cả phương án có/không có wrapper** nếu tối ưu throughput là mục tiêu.

### 3.3. Chọn kích thước buffer

Những giá trị như 8 KiB, 32 KiB, 64 KiB, 256 KiB hoặc 1 MiB chỉ là **ứng viên benchmark**, không phải đáp án chung. Buffer quá nhỏ thường tăng số lời gọi I/O; buffer quá lớn có thể tăng footprint, pressure lên cache/bộ nhớ và số byte phải cấp phát mỗi worker.

Với vòng lặp copy, tài nguyên nhớ do ứng dụng giữ là **O(B)** với B là kích thước buffer, trong khi dung lượng file có thể lớn hơn RAM. Nếu 100 worker đồng thời mỗi worker giữ một mảng 1 MiB, riêng mảng tạm đã khoảng 100 MiB chưa kể buffers, queue và runtime overhead.

Lưu ý `InputStream.read(byte[])` trả dữ liệu từng phần theo trạng thái nguồn. Không được giả định một lần đọc tương ứng một file block, một bản ghi hay một TCP message.

### 3.4. `flush()`, `close()` và độ bền

`out.flush()` yêu cầu đẩy các byte đang đệm xuống lớp output bên dưới. `close()` của buffered output sẽ xử lý flush rồi đóng stream bên dưới. Nhưng:

- `flush()` **không đồng nghĩa** dữ liệu đã fsync tới thiết bị.
- `close()` **không tự bảo đảm** file an toàn sau mất nguồn trên mọi filesystem.
- Nếu cần durability mạnh, phải thiết kế thêm sync/force và thường cả cơ chế công bố file đích an toàn.
- Nếu xảy ra `IOException` giữa chừng, file đích có thể đã có **một phần** nội dung, không được coi là bản copy hoàn chỉnh.

Dữ liệu có thể đã đọc đúng 100% nhưng ghi ra đích bị lỗi hoặc đĩa đầy; code vẫn phải để exception đi ra ngoài và xử lý trạng thái đích.

## 4. `Files.copy`: cách trực tiếp cho thao tác copy file

### 4.1. Overload Path → Path

Ví dụ đơn giản:

```java
Path source = Path.of("data.bin");
Path target = Path.of("backup.bin");

Files.copy(source, target);
```

Đây là đoạn minh họa trong method xử lý `IOException` với `Path`/`Files` đã import. Theo contract, nếu `target` đã tồn tại thì mặc định có thể ném `FileAlreadyExistsException`. Nếu chủ động cho phép thay thế:

```java
Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
```

`REPLACE_EXISTING` thay đổi chính sách khi target đã có; **không** biến toàn bộ copy thành thao tác atomic. Nếu cần giữ metadata khi được hỗ trợ:

```java
Files.copy(
        source,
        target,
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.COPY_ATTRIBUTES);
```

Các snippet nằm trong method có xử lý `IOException`, dùng các loại `java.nio.file.*`. `COPY_ATTRIBUTES` yêu cầu copy thuộc tính file, nhưng tập attributes được copy và độ chính xác của chúng phụ thuộc file system/provider. Đừng suy ra mọi ACL, owner, extended attributes và timestamp đều được bảo toàn tuyệt đối trên mọi OS.

**Ưu điểm lớn:** code ngắn, dễ đọc, ít cơ hội mắc lỗi vòng lặp/EOF và cho provider quyền tối ưu con đường sao chép. Nếu chỉ cần copy một file bình thường, đây thường là API nên thử đầu tiên.

### 4.2. Không suy luận implementation của `Files.copy`

Việc gọi `Files.copy(Path,Path)` **không cam kết** Java luôn dùng:

- Một `BufferedInputStream` cụ thể;
- Một buffer có size cố định;
- Một syscall `sendfile` / `copy_file_range` cụ thể;
- Zero-copy trong mọi trường hợp;
- Một chiến lược tối ưu giống nhau giữa các OS/JDK/provider.

`Files.copy` xác định **hành vi cấp API**, còn cách thực hiện tùy implementation, kiểu file, quyền truy cập, hệ thống tệp và môi trường. Các nhận xét hiệu năng phải gắn phiên bản JDK và platform đã đo.

### 4.3. `Files.copy` không phải giao dịch nguyên tử

Tài liệu Java không bảo đảm thao tác copy tổng thể diễn ra nguyên tử. Khi lỗi I/O xảy ra, file đích có thể tồn tại với dữ liệu không đầy đủ. Không dùng trực tiếp đường dẫn đích quan trọng rồi giả định người đọc không bao giờ thấy phiên bản đang được tạo.

Một cách tổ chức thường dùng khi cập nhật một file quan trọng:

```text
source
  │
  └── Files.copy(...) → staging file trong cùng directory với target
                              │
                              ├── kiểm tra length/checksum/điều kiện
                              ├── sync file staging nếu yêu cầu durability
                              └── publish qua rename nguyên tử nếu hỗ trợ
                                           │
                                           └── sync directory theo platform nếu cần crash durability
```

Cần phân biệt: `Files.copy` không tự thực hiện tất cả các bước này. `Files.move(..., ATOMIC_MOVE)` chỉ có contract riêng cho atomic move khi hỗ trợ; nó **không tự đồng nghĩa** directory entry đã bền sau mất điện. Việc thay target đã tồn tại với `ATOMIC_MOVE` còn phụ thuộc implementation và không nên giả định có behavior giống nhau mọi provider.

### 4.4. Overload liên quan InputStream và OutputStream

`Files` còn có hai overload tiện lợi:

```java
long n1 = Files.copy(inputStream, target,
        StandardCopyOption.REPLACE_EXISTING);

long n2 = Files.copy(source, outputStream);
```

Đoạn trên là **minh họa** với `inputStream`/`outputStream` đã mở, `source`/`target` là các `Path` và caller xử lý `IOException`. Cả hai overload này trả `long` là số byte đã copy.

Điểm khác với Path→Path:

- `Files.copy(InputStream, Path, ...)` lấy nguồn là stream và tạo/ghi target.
- `Files.copy(Path, OutputStream)` đọc file rồi gửi byte tới output stream.
- Hai overload **không tự đóng stream do caller truyền vào**; người sở hữu phải quản lý `close`/`flush`.
- `Files.copy(Path,Path,...)` trả về **`Path` đích**, không phải byte count. Nếu cần số byte, dùng `Files.size` trước/sau hoặc copy qua API trả số byte theo contract.
- Cùng tên `copy` nhưng options và semantics về attributes có khác theo overload; đọc chữ ký mình thực sự gọi.

Không cố truyền `COPY_ATTRIBUTES` cho overload `InputStream→Path` như thể nó hỗ trợ sao chép metadata từ một stream vốn không có đường dẫn hay thuộc tính file tương đương.

## 5. `FileChannel`: xử lý bằng `ByteBuffer`

`FileChannel` thuộc Java NIO, cung cấp file position, size, random access và một số thao tác transfer. Nó thích hợp khi cần sao chép từng đoạn hoặc tích hợp với các pipeline channel.

### 5.1. Copy thủ công bằng `read`/`write`

Một vòng copy channel có thể trông như:

```java
try (FileChannel in = FileChannel.open(source, StandardOpenOption.READ);
     FileChannel out = FileChannel.open(target,
             StandardOpenOption.CREATE_NEW,
             StandardOpenOption.WRITE)) {

    ByteBuffer buffer = ByteBuffer.allocateDirect(64 * 1024);

    while (in.read(buffer) != -1) {
        buffer.flip();
        while (buffer.hasRemaining()) {
            out.write(buffer);
        }
        buffer.clear();
    }
}
```

Đây là **đoạn minh họa trong method xử lý `IOException`**, với `source`/`target` hợp lệ và imports `FileChannel`, `ByteBuffer`, `StandardOpenOption`. Để bảo vệ trước provider bất thường mà một lượt `read`/`write` báo không tiến độ kéo dài, production nên theo dõi số byte và chính sách retry/break thay vì vòng bận vô hạn.

**Quy tắc ByteBuffer bắt buộc:**

1. Ban đầu `position=0`, `limit=capacity`, sẵn sàng nhận byte.
2. `in.read(buffer)` đưa bytes vào vùng từ position tới limit; position tăng theo lượng đọc.
3. `buffer.flip()` đặt limit theo vị trí đã ghi và position về 0 để xuất byte.
4. `out.write(buffer)` có thể ghi **ít hơn** số byte còn lại; dùng `while(buffer.hasRemaining())`.
5. `buffer.clear()` chuẩn bị buffer nhận đợt mới; **không xóa byte vật lý** trong buffer, chỉ đặt lại cursor.

Nếu bỏ `flip()`, có thể ghi byte cũ hoặc không đúng vùng. Nếu quên `clear()`, buffer sẽ hết không gian ghi. Nếu chỉ gọi `out.write(buffer)` một lần và mặc định hết `remaining()`, file đích có thể mất một phần dữ liệu.

### 5.2. `allocateDirect` có luôn tốt hơn `allocate`?

Không. `ByteBuffer.allocateDirect(n)` dùng một vùng direct memory, có thể thuận tiện cho native channel I/O; `ByteBuffer.allocate(n)` dùng heap buffer thông thường. Sự khác biệt hiệu năng phụ thuộc kích thước transfer, JDK, GC, CPU, OS và memory pressure.

Direct buffers có chi phí cấp phát và lifecycle riêng, không phải “miễn phí RAM”. **Cấp phát một lần rồi tái sử dụng** trong vòng copy thay vì `allocateDirect` ở mỗi lượt. Với job rất nhỏ, heap buffer có thể đơn giản và đủ nhanh.

### 5.3. Vòng lặp phải xử lý partial writes

`FileChannel.write(ByteBuffer)` trả số byte thực tế đã ghi. Kể cả file channel đồng bộ, contract vẫn không hứa mọi `remaining()` sẽ hết trong một lời gọi. Vì vậy:

```java
while (buffer.hasRemaining()) {
    int n = targetChannel.write(buffer);
    if (n <= 0) {
        throw new IOException("Target did not make progress");
    }
}
```

Đoạn minh họa giả định `targetChannel` đang mở ghi và `buffer` chứa byte để gửi. Nếu môi trường thực yêu cầu chấp nhận `n=0` một cách tạm thời, thiết kế retry/backoff/timeout riêng; không spin vô hạn.

## 6. `FileChannel.transferTo` và `transferFrom`

### 6.1. `transferTo(position,count,target)`

Ví dụ:

```java
long copied = sourceChannel.transferTo(
        offset, requestedBytes, targetChannel);
```

Đây là snippet với `sourceChannel` kiểu `FileChannel` đã mở đọc và `targetChannel` kiểu `WritableByteChannel` hợp lệ. Ý nghĩa:

- `offset` là vị trí **byte trong file nguồn**, kiểu `long`.
- `requestedBytes` là số byte **tối đa muốn chuyển** trong lời gọi, kiểu `long`.
- Kết quả `copied` là số byte **thực tế đã chuyển** trong lần đó, có thể nhỏ hơn số yêu cầu.
- File position của **source FileChannel** không bị thay đổi bởi `transferTo` vì offset được truyền tường minh.
- Nếu target channel có position, bytes được ghi từ vị trí hiện tại của **target**, và target position tăng tương ứng.

**Không được kết luận** một lần `transferTo(0, source.size(), target)` luôn chuyển toàn bộ file. Provider/OS có thể giới hạn kích thước một lượt, target có thể không nhận hết hoặc có điều kiện khiến phương thức trả 0.

### 6.2. Loop cho file lớn

Mẫu phổ biến:

```java
long size = sourceChannel.size();
long position = 0L;

while (position < size) {
    long n = sourceChannel.transferTo(
            position, size - position, targetChannel);

    if (n <= 0) {
        // Không được quay lại vòng lặp y nguyên mãi mãi.
        throw new IOException(
                "No progress at file offset " + position);
    }

    position += n;
}
```

Đoạn này là **minh họa trong method**; `sourceChannel` và `targetChannel` đã mở. Nó phát hiện và báo lỗi khi transfer không tiến được; tùy use case, có thể chuyển sang phương thức read/write dự phòng cho phần còn lại.

### 6.3. Vì sao có thể nhận 0 hoặc ít bytes?

Một số tình huống:

- Requested region đến cuối file; nếu tính offset sai, không còn bytes để chuyển.
- Target channel không thể nhận thêm byte ngay (đặc biệt non-blocking target).
- Cơ chế native transfer bị giới hạn theo OS hoặc provider.
- File nguồn bị thay đổi kích thước trong khi copy.
- Bất kỳ giới hạn implementation nào khiến số bytes trong một lời gọi nhỏ hơn số yêu cầu.

Cách xử lý không thể chỉ `while(n==0) continue` mãi mãi. Có thể dùng fallback `FileChannel.read(ByteBuffer,offset)` rồi `target.write(ByteBuffer)` với kiểm soát tiến độ, hoặc fail rõ để caller xử lý.

### 6.4. `transferFrom` chuyển theo chiều ngược lại

```java
long n = targetFileChannel.transferFrom(
        readableSourceChannel, fileOffset, maxBytes);
```

Đoạn trên giả định `targetFileChannel` là FileChannel mở ghi và `readableSourceChannel` là `ReadableByteChannel`. `transferFrom` chuyển bytes từ **nguồn** vào FileChannel đích từ offset được chỉ định.

Lưu ý quan hệ position khác với `transferTo`:

- `transferFrom` truyền **position của file đích** tường minh; position hiện tại của FileChannel đích không thay đổi.
- Nếu source channel có position, vị trí nguồn có thể tiến khi byte được đọc.
- Kết quả thực tế cũng có thể nhỏ hơn `maxBytes`; vẫn phải lập kế hoạch loop và xử lý tiến độ.
- Một nguồn socket/pipe khác file có semantics readiness và EOF riêng; không thể áp mọi giả định file-to-file sang non-blocking network.

### 6.5. “Zero-copy” là khả năng, không phải cam kết API

Trên những tổ hợp OS/file/channel cụ thể, `transferTo` có thể dùng đường truyền native giúp giảm số lần sao chép dữ liệu qua user space hoặc số lần syscall. Nhưng Java API **không cam kết rằng mỗi `transferTo` sẽ luôn là zero-copy**.

Đừng suy diễn:

- Chắc chắn không có dữ liệu nào được copy ở RAM/OS.
- Chắc chắn không sử dụng page cache.
- Chắc chắn không có CPU overhead hoặc context switches.
- Chắc chắn nhanh hơn buffered streams với file nhỏ.
- Chắc chắn có cùng semantics trên Windows, Linux, macOS và network filesystem.

Việc `FileChannel` cho phép provider triển khai tối ưu là **lợi thế tiềm năng**. Để khẳng định hiệu quả thật, phải đo với tổ hợp triển khai thực.

## 7. Ví dụ Java 17+ chạy được: ba cách copy và kiểm tra byte

Ví dụ độc lập sau:

- Tạo một thư mục riêng và file nguồn có **131.075 byte** (cố ý không chia hết cho 64 KiB).
- Copy bằng buffered streams.
- Copy bằng `Files.copy(Path,Path)`.
- Copy bằng `FileChannel.transferTo`, với fallback sang `read/write(ByteBuffer)` nếu transfer không tiến độ.
- So sánh byte với `Files.mismatch` (Java 12+, nên có trong Java 17+).
- Dọn file của chính ví dụ, không chạm file của người dùng.

**Tệp:** `CopyStrategiesDemo.java`

```java
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.stream.Stream;

public class CopyStrategiesDemo {

    private static long copyBuffered(Path source, Path target)
            throws IOException {

        long bytesCopied = 0;

        try (InputStream in = new BufferedInputStream(
                    Files.newInputStream(source));
             OutputStream out = new BufferedOutputStream(
                    Files.newOutputStream(
                            target,
                            StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE))) {

            byte[] chunk = new byte[64 * 1024];
            int n;

            while ((n = in.read(chunk)) != -1) {
                out.write(chunk, 0, n);
                bytesCopied += n;
            }
        }

        return bytesCopied;
    }

    private static long copyWithChannel(Path source, Path target)
            throws IOException {

        try (FileChannel in = FileChannel.open(
                    source, StandardOpenOption.READ);
             FileChannel out = FileChannel.open(
                    target,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE)) {

            long size = in.size();
            long position = 0L;
            ByteBuffer fallback = ByteBuffer.allocateDirect(64 * 1024);

            while (position < size) {
                long moved = in.transferTo(
                        position, size - position, out);

                if (moved > 0) {
                    position += moved;
                    continue;
                }

                // Native transfer không tiến được:
                // đọc đúng vùng còn lại bằng positional read.
                fallback.clear();
                fallback.limit((int) Math.min(
                        (long) fallback.capacity(),
                        size - position));

                int read = in.read(fallback, position);
                if (read <= 0) {
                    throw new IOException(
                            "Cannot make progress at " + position);
                }

                fallback.flip();

                while (fallback.hasRemaining()) {
                    int written = out.write(fallback);

                    if (written <= 0) {
                        throw new IOException("Target stalled");
                    }
                }

                position += read;
            }

            return position;
        }
    }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory(
                "copy-strategies-");

        try {
            Path source = directory.resolve("source.bin");

            byte[] data = new byte[131_075];
            for (int i = 0; i < data.length; i++) {
                data[i] = (byte) (i * 31 + 7);
            }

            Files.write(source, data);

            Path buffered = directory.resolve("buffered.bin");
            Path filesCopy = directory.resolve("files-copy.bin");
            Path channel = directory.resolve("channel.bin");

            System.out.println(
                    "source bytes: " + Files.size(source));

            System.out.println(
                    "buffered bytes: "
                    + copyBuffered(source, buffered));

            Files.copy(source, filesCopy);

            System.out.println(
                    "Files.copy equal: "
                    + (Files.mismatch(source, filesCopy) == -1));

            System.out.println(
                    "channel bytes: "
                    + copyWithChannel(source, channel));

            boolean same =
                    Files.mismatch(source, buffered) == -1
                    && Files.mismatch(source, filesCopy) == -1
                    && Files.mismatch(source, channel) == -1;

            System.out.println("all equal: " + same);

        } finally {
            // Chỉ dọn các file được tạo trong thư mục của demo.
            try (Stream<Path> entries = Files.list(directory)) {
                for (Path entry : entries.toList()) {
                    Files.deleteIfExists(entry);
                }
            }
            Files.deleteIfExists(directory);
        }
    }
}
```

Biên dịch/chạy với JDK 17+:

```bash
javac CopyStrategiesDemo.java
java CopyStrategiesDemo
```

**Stdout dự kiến khi filesystem thông thường hoạt động đúng:**

```text
source bytes: 131075
buffered bytes: 131075
Files.copy equal: true
channel bytes: 131075
all equal: true
```

### 7.1. Giải thích lựa chọn trong demo

- Dùng dữ liệu byte **định trước** thay cho Random để stdout/so sánh tái lập được.
- `131_075` không chia hết cho `65_536`; lượt đọc cuối chỉ có 3 byte sau hai khối đầy, giúp kiểm tra code không ghi dư byte.
- `CREATE_NEW` yêu cầu file đích chưa tồn tại và ngăn ghi đè không chủ ý.
- `Files.copy` được gọi không có option vì đích là đường dẫn mới.
- `Files.mismatch(a,b)` trả `-1` khi toàn bộ bytes và chiều dài bằng nhau; có thể dùng để kiểm tra file copy **tại thời điểm đọc**, không phải hash hay chứng nhận dữ liệu sau mất điện.
- Hàm channel khởi tạo `position=0`, chuyển tới khi đủ `size`. Nếu `transferTo` không tiến, nó dùng positional `read` vào một buffer direct tái sử dụng rồi ghi đủ bytes sang FileChannel đích.
- Vì không có thread nào thay đổi source/target trong demo, cách đếm `position` và file size là ổn định. **Trong hệ thống có writer đồng thời**, cần cơ chế bảo đảm snapshot/độ ổn định riêng.
- `finally` chỉ dọn file trong **thư mục tạm do demo tạo**. Nếu một bước trước đó lỗi, cleanup cũng có thể ném IOException; trong ứng dụng thật cần chính sách log/giữ file lỗi tùy nhu cầu khôi phục.

### 7.2. Tại sao không in số millisecond “benchmark”?

Tốc độ copy bị ảnh hưởng bởi page cache, trạng thái filesystem và tải máy. In `System.nanoTime()` một lần rồi gắn nhãn “API X nhanh nhất” là kết luận không có ý nghĩa phổ quát. Ví dụ cố tình chỉ xác nhận **tính đúng đắn** bằng byte counts và `Files.mismatch`. Phần đo hiệu năng được giải thích riêng bên dưới.

### 7.3. Vì sao fallback channel không gọi `read`/`write` đồng thời với một transfer đang chạy?

`FileChannel.transferTo` đồng bộ đối với lời gọi này: nó đã trả về số byte chuyển được (hoặc 0) trước khi fallback bắt đầu. Trong demo không có transfer bất đồng bộ pending giữ buffer; fallback đọc lại đúng `position` còn lại rồi gửi tới output hiện tại.

**Điều kiện tiên quyết:** cả source và target không bị thread khác thay đổi, và output channel được mở mới với position 0. Nếu bạn chuyển `transferTo` đến socket non-blocking, phải thêm cơ chế readiness/backpressure riêng, không sao chép nguyên vòng lặp fallback file-to-file này.

## 8. Tệp lớn hơn 2 GiB: dùng `long`, xử lý từng phần

Các file rất lớn không thể được biểu diễn bằng một `byte[]` có độ dài vượt giới hạn array JVM, và `ByteBuffer` có `capacity`/`position` kiểu `int`. Nhưng `FileChannel.size()`, positional offsets và các tham số `transferTo`/`transferFrom` dùng `long`.

Vì vậy:

- Không chuyển `channel.size()` về `int` khi tệp có thể lớn hơn 2 GiB.
- Không dùng `Files.readAllBytes` chỉ để copy một file hàng chục GB: có thể thất bại do giới hạn array hoặc cạn heap.
- Dùng buffer kích thước hữu hạn và vòng lặp, để bộ nhớ không tăng theo kích thước file.
- Tính offset bằng `long`, kiểm tra overflow khi cộng `position + n`.
- Tránh cast `(int)(size - position)` nếu phần còn lại có thể lớn hơn `Integer.MAX_VALUE`. Chỉ cast sau khi giới hạn bằng `Math.min(remaining, (long) buffer.capacity())`.
- Với `transferTo`, đừng đưa ra giả định “count kiểu long thì OS chuyển hàng TB trong một syscall”; vẫn phải lặp theo giá trị thực trả về.

### 8.1. Chia copy thành các đoạn có kích thước logic

Ví dụ muốn giới hạn mỗi lần yêu cầu transfer ở 64 MiB:

```java
long offset = 0L;
long size = sourceChannel.size();
long maxChunk = 64L * 1024 * 1024;

while (offset < size) {
    long requested = Math.min(maxChunk, size - offset);
    long n = sourceChannel.transferTo(
            offset, requested, targetChannel);

    if (n <= 0) {
        throw new IOException("No transfer progress at " + offset);
    }

    offset += n;
}
```

Đây là **snippet minh họa trong method**, với hai channel đã mở. 64 MiB là một kích thước tham khảo, không phải giới hạn bắt buộc của Java. Nếu source thay đổi concurrent, `size` có thể không còn phản ánh cuối file thật, nên cần quyết định copy **snapshot tại đầu** hay theo dõi thay đổi theo giao thức riêng.

### 8.2. Copy một vùng của file

`FileChannel` tiện khi chỉ cần byte [start,start+length):

```java
long start = 1_048_576L;
long length = 4_194_304L;
long copied = 0L;

while (copied < length) {
    long n = sourceChannel.transferTo(
            start + copied,
            length - copied,
            targetChannel);

    if (n <= 0) {
        throw new IOException("Could not copy requested region");
    }

    copied += n;
}
```

Đây là snippet trong method, giả định vùng byte yêu cầu tồn tại hoàn toàn trong source và target đang ở đúng position. Nếu file nhỏ hơn `start+length`, không coi copy thiếu byte là thành công; kiểm tra `size`/source integrity phù hợp và xử lý EOF.

### 8.3. Với copy nhiều file, metadata và directory tree cũng quan trọng

`Files.copy` một Path đến một Path **không tự đệ quy copy toàn bộ cây thư mục có nội dung** nếu source là directory; khi copy directory, hành vi chỉ tạo/copy directory entry hoặc thuộc tính theo contract, còn entries bên trong cần được xử lý riêng. Copy một cây cần thuật toán duyệt, xử lý symlink/cycle, file permissions, metadata và lỗi từng tệp.

Cũng đừng nhầm `Files.copy` một lần là giải pháp incremental backup: nó không tính diff, version, deduplication, checksum lịch sử hoặc transaction toàn cây.

## 9. Hiệu năng: đọc số đo đúng cách

### 9.1. Throughput và latency đo các khía cạnh khác nhau

- **Throughput:** số byte copy thành công trên mỗi giây, ví dụ MiB/s.
- **Latency:** thời gian từ lúc bắt đầu copy tới lúc API hoàn tất cho một file.
- **Tail latency:** p95/p99 khi có nhiều file/jobs, quan trọng với hệ thống phục vụ request.
- **Memory footprint:** heap/direct memory/temporary buffers trên mỗi operation.
- **CPU utilization:** CPU bị tiêu thụ khi copy.
- **Syscall count/context switching:** đôi khi làm rõ ích lợi của buffered/transfer.
- **Durability time:** nếu yêu cầu `force`/sync, phải đo cả phần đó, không chỉ thời gian bytes vào cache.

Một API có throughput cao với một file 20 GB có thể lại kém đối với hàng triệu file 1 KiB do overhead mở/đóng, metadata lookup và scheduling. Cũng có thể Files.copy đã tận dụng tối ưu hóa của provider khiến vòng loop tự viết không nhanh hơn.

### 9.2. Bộ nhớ và tần suất gọi I/O

Một vòng `read(chunk)` với `chunk.length = B` cần khoảng `ceil(fileSize/B)` lần đọc trong trường hợp mỗi read đầy đủ, **nhưng thực tế có thể nhiều hơn** do partial reads. Số lần ghi cũng phụ thuộc việc ghi partial và buffering.

Ví dụ tính toán minh họa: một file 1 GiB với khối 64 KiB tương ứng 16.384 khối nếu tất cả khối trừ cuối đều đầy. Đây chỉ là **ước lượng số chunk**, không phải số syscall chính xác của OS, vì lớp buffering, provider và native transfer có thể hoạt động khác.

Điều quan trọng là **không gọi `read()`/`write()` từng byte** khi copy file lớn nếu không có yêu cầu đặc biệt; lập chiến lược buffer phù hợp và đo.

### 9.3. Page cache khiến benchmark dễ sai

Trong một hệ thống tệp điển hình, OS có page cache và read-ahead. Nếu chạy:

```text
Lượt 1: buffered copy
Lượt 2: Files.copy
Lượt 3: FileChannel
```

thì lượt 2/3 có thể được hưởng dữ liệu nguồn đã nằm trong cache, dẫn đến kết luận thiên lệch rằng “lượt sau nhanh hơn”.

Các yếu tố gây nhiễu:

- Warm/cold cache của hệ điều hành và JVM.
- Antivirus/indexing/background I/O.
- SSD/HDD/NVMe, network mount, filesystem/compression/deduplication.
- Khả năng copy nội bộ cùng filesystem hoặc giữa hai thiết bị.
- Direct buffer allocation và GC.
- Độ dài/lượng file, kích thước buffer, số worker đồng thời.
- Sync/durability option và tốc độ thực của thiết bị.
- File hệ thống thưa (sparse file), symlink và thuộc tính file.

### 9.4. Quy trình benchmark có ý nghĩa hơn

1. **Xác định workload:** file nhỏ, file 100 MB, 5 GB; một file hay nhiều file; copy cùng volume hay khác volume; sequential hay chunked.
2. **Định nghĩa cùng semantics:** cả ba phương án đều copy bytes giống hệt, xử lý overwrite/attributes/flush giống nhau nếu những điều đó thuộc yêu cầu.
3. **Warm-up:** chạy nhiều lượt cho JIT và hệ thống ổn định; đừng chọn lần đầu làm kết luận cuối.
4. **Đổi thứ tự chạy:** luân phiên các chiến lược để giảm hiệu ứng cache.
5. **Lặp nhiều mẫu:** lấy median và phân bố, ghi môi trường JDK/OS/filesystem/media.
6. **Theo dõi correctness:** size, `Files.mismatch` hoặc checksum trong test thích hợp. Không dùng throughput của file copy bị thiếu byte.
7. **Tách thời gian copy và verify:** việc đọc lại toàn file để `mismatch` có chi phí; báo hai thước đo riêng.
8. **Đo memory/CPU khi cần:** dùng profiler, JFR hoặc các công cụ OS thích hợp, không chỉ thời gian nanoTime.
9. **Kiểm tra failure scenarios:** disk full, permission errors, target tồn tại, source bị xóa/thay đổi, network filesystem bị ngắt.
10. **Báo kết luận có phạm vi:** “trong máy thử và loại file này”, không nói API nào nhanh nhất cho mọi JVM.

`System.nanoTime()` phù hợp đo khoảng thời gian đơn lẻ trong một test tự kiểm soát; với microbenchmark cực ngắn, dùng framework như JMH và cẩn thận tác động của OS I/O vì JMH không loại bỏ mọi yếu tố cache/device.

### 9.5. Mẫu đo đơn giản, không tuyên bố benchmark chuẩn

```java
long startNanos = System.nanoTime();

Files.copy(source, destination);

long elapsedNanos = System.nanoTime() - startNanos;
long byteCount = Files.size(destination);

double seconds = elapsedNanos / 1_000_000_000.0;
double mibPerSecond = seconds > 0
        ? (byteCount / 1_048_576.0) / seconds
        : Double.POSITIVE_INFINITY;
```

Đây là **snippet minh họa** trong method xử lý `IOException`; `source`/`destination` là Path, destination chưa tồn tại. Nó không có stdout cố định, không bao gồm file verify và không phản ánh chắc chắn lưu bền. Không lấy một con số MiB/s từ đoạn này để áp cho môi trường khác.

## 10. Correctness: làm thế nào xác minh bản sao?

Một file copy “thành công” cần định nghĩa rõ điều gì được bảo toàn:

| Yêu cầu | Cách kiểm tra |
|---|---|
| Nội dung bytes chính xác | `Files.mismatch(source,target)==-1` khi cả hai không bị thay trong lúc kiểm tra |
| Kích thước bằng nhau | `Files.size(source)==Files.size(target)`, cần thiết nhưng không đủ |
| Thời gian sửa đổi, quyền, owner | Đọc `BasicFileAttributes` hoặc view phù hợp; copy metadata riêng |
| Không ghi đè dữ liệu có sẵn | `CREATE_NEW` hoặc default `Files.copy` khi target đã tồn tại |
| Chấp nhận overwrite có chủ đích | `REPLACE_EXISTING` cho `Files.copy` hoặc open options tương ứng |
| Crash durability | Thiết kế `force`/sync, staging và publish theo OS/filesystem |
| Copy từ một phiên bản nhất quán | Snapshot, lock/protocol, version/checksum hoặc cơ chế lưu trữ tương đương |
| Bảo mật đường dẫn | Validate/canonicalize theo trust boundary, xử lý symlink và quyền |

`Files.mismatch` trả **vị trí byte đầu tiên khác** hoặc `-1` nếu không có khác biệt; nếu file sizes khác nhau, trả vị trí đầu tiên mà file ngắn không còn byte. Nó không phải thuật toán hash bảo mật và không phát hiện/giải quyết mọi race khi nguồn/đích có thể bị sửa đồng thời.

### 10.1. Vì sao kích thước bằng nhau chưa đủ?

Hai file đều 1 MB có thể khác toàn bộ bytes. Nếu chỉ kiểm tra `Files.size`, bug copy sai thứ tự, ghi dữ liệu cũ hoặc thay đổi offset sẽ bị bỏ qua. `mismatch` hoặc checksum phù hợp giúp xác minh nội dung trong bài test.

Ngược lại, nếu tệp có metadata quan trọng, bytes giống nhau vẫn chưa bảo đảm file đích có đúng quyền, attributes hay link semantics.

## 11. Tệp đang bị thay đổi trong lúc copy

Cả ba cách không tự tạo một **snapshot có tính giao dịch** của file nguồn đang được writer khác chỉnh sửa.

Giả sử:

```text
T0: source.bin = version A
T1: copy đọc nửa đầu version A
T2: writer khác sửa nửa sau thành version B
T3: copy đọc nửa sau version B
T4: target có thể là hỗn hợp A/B
```

Ngay cả khi `Files.copy` thành công, dữ liệu đích chưa chắc tương ứng một thời điểm logic duy nhất. Khi tính nhất quán quan trọng:

- Dừng writer hoặc dùng cơ chế lock/protocol giữa các bên **có hợp tác**.
- Dùng snapshots của filesystem/storage nếu có và được xác nhận.
- Thiết kế copy từ **file bất biến** theo phiên bản; ghi bản mới riêng và publish pointer sau.
- Ghi checksum/version khi phát hành và validate sau khi copy; kiểm tra lại source nếu policy yêu cầu.
- Đừng cho rằng `FileLock` mang lại transaction hoàn chỉnh giữa mọi process hoặc trên mọi filesystem; semantics tùy OS/provider.

### 11.1. Một byte-stream copy không biết cấu trúc file nghiệp vụ

Với database file, WAL, log nhiều file hay cấu trúc được cập nhật nhiều bước, chỉ copy các bytes trong lúc dịch vụ đang chạy có thể tạo snapshot logic sai. Nên dùng API backup/snapshot chính thức của hệ thống lưu trữ đó hoặc quy trình application-consistent backup, thay vì tự copy file đang được thay đổi mà không hiểu cấu trúc.

## 12. Overwrite, symlink và metadata

### 12.1. Copy file mới khác cập nhật file đang tồn tại

`Files.copy(source,target)` mặc định không thay target hiện hữu. `Files.copy(source,target,REPLACE_EXISTING)` yêu cầu thay target nếu được phép. Nhưng thay đích **không phải atomic publish**; người đọc có thể nhìn thấy trạng thái không phù hợp khi copy đang diễn ra hoặc gặp lỗi.

Khi copy theo buffered stream/channel, các option:

- `CREATE_NEW`: tạo mới và fail nếu file đã tồn tại.
- `CREATE`: tạo nếu chưa tồn tại, không tự ngụ ý truncate nếu thiếu option liên quan.
- `TRUNCATE_EXISTING`: cắt ngắn nếu mở ghi và file tồn tại theo contract.
- `APPEND`: thêm vào cuối, **không phải copy ghi đè từ đầu**.

Dùng chính sách thích hợp; không kết hợp `APPEND` với công việc copy nguyên trạng vào một file trống một cách vô ý.

### 12.2. Symlink và đường dẫn

Với `Files.copy(Path,Path)`, nếu source là symbolic link, mặc định thường **theo link** và copy nội dung target được tham chiếu; `LinkOption.NOFOLLOW_LINKS` có thể dùng khi muốn copy chính link theo contract của provider.

```java
Files.copy(
        source,
        target,
        LinkOption.NOFOLLOW_LINKS);
```

Đây là snippet minh họa trong method xử lý `IOException`; hành vi tạo/copy symlink và quyền hỗ trợ phụ thuộc filesystem/provider. Nếu đường dẫn được người dùng cung cấp và chương trình có quyền cao, kiểm tra path traversal, symlink races và thư mục cho phép trước khi thao tác. Một check `Files.exists` rồi write riêng **không phải** rào chắn race tuyệt đối; dùng atomic create options và chính sách OS phù hợp khi cần.

### 12.3. Copy attributes và permissions

`StandardCopyOption.COPY_ATTRIBUTES` áp dụng trong overload `Files.copy(Path,Path)`; tập thuộc tính được bảo toàn tùy provider và filesystem. Trên một số nguồn/đích, quyền, owner, ACL, extended attributes, timestamp precision hoặc creation time có thể khác.

Khi làm backup, metadata đôi khi quan trọng ngang nội dung; cần liệt kê attrs bắt buộc và kiểm tra sau copy. Đừng chấm hiệu năng của cách chỉ copy content rồi so với cách cũng sao chép permissions/metadata mà không ghi rõ sự khác biệt.

## 13. Độ bền file sau khi copy

**“Method trả về thành công” không tự có nghĩa “file sống sót qua mất điện”.** File system và OS có thể buffer dữ liệu. Các API:

- `BufferedOutputStream.flush()`: đẩy bytes ở buffer Java xuống stream bên dưới; không mặc định fsync.
- `Files.copy`: không phải lệnh sync durability có contract chung cho mọi thiết bị.
- `FileChannel.force(false)`: yêu cầu flush thay đổi content theo phạm vi API.
- `FileChannel.force(true)`: yêu cầu flush content và metadata theo phạm vi API; bảo đảm mạnh hơn trên thiết bị lưu trữ cục bộ theo điều kiện contract.
- `Files.move(...,ATOMIC_MOVE)`: nếu hỗ trợ thì atomicity của thao tác move, **khác** durability sau crash.
- Đồng bộ parent directory entry sau rename: phụ thuộc OS/provider và có thể cần API nền tảng ngoài Java SE portable.

Nếu bản copy sẽ thay một file cấu hình quan trọng, thiết kế thường có:

```text
tạo file tạm trong cùng thư mục
        │
        ▼
copy toàn bộ bytes vào staging
        │
        ▼
kiểm tra bản sao (size/checksum/schema)
        │
        ▼
force/sync staging khi yêu cầu durable
        │
        ▼
rename nguyên tử nếu provider hỗ trợ
        │
        ▼
sync directory theo contract nền tảng nếu cần crash consistency
        │
        ▼
chỉ xác nhận commit ở mức đã thực sự bảo đảm
```

Không tự suy diễn rằng `FileChannel.force(true)` trên staging trước rename cũng đồng bộ **directory entry sau rename**. Với dữ liệu quan trọng, cần chính sách backup/recovery và kiểm thử failure mode trên storage thực.

## 14. Xử lý lỗi và cleanup khi copy thất bại

Một số tình huống phải tính:

| Tình huống | Rủi ro | Hướng xử lý |
|---|---|---|
| Source không tồn tại | `NoSuchFileException` hoặc IOException | Không tạo bản sao trống giả thành công |
| Target đã tồn tại | `FileAlreadyExistsException` theo chính sách mặc định/CREATE_NEW | Chọn rõ overwrite hay không |
| Disk full/quota | Bản đích có thể đã được ghi một phần | Dọn staging/target theo policy, không publish |
| Permission denied | Không mở/ghi được file | Kiểm tra quyền, trả lỗi rõ |
| File nguồn đổi kích thước | Copy thiếu hoặc dữ liệu pha nhiều phiên bản | Lock/snapshot/phiên bản bất biến |
| `transferTo` trả 0 khi còn bytes | Loop không tiến, nguy cơ spin | Fallback hoặc fail |
| `write(ByteBuffer)` ghi một phần | Truncation dữ liệu | Lặp tới hết `remaining()` |
| Source/target là cùng file | Có thể tự ghi đè/cắt ngắn nguồn | Validate identity; tránh copy lên chính nguồn |
| I/O giữa các volumes/FS | Tốc độ và semantics khác | Benchmark/tính đúng trên cặp đích thực |
| Process bị dừng đột ngột | File chưa copy hết | Copy staging, trạng thái phục hồi và cleanup |

Nếu sao chép tới file tạm mà thất bại, không đổi tên file tạm thành target. Cách cleanup tùy nghiệp vụ: có thể xóa staging ngay, hoặc giữ bản sao dở dang để resume có kiểm tra checksum/chunk map. Tuyệt đối không đánh dấu file đích “completed” chỉ vì tồn tại.

### 14.1. Thời gian chờ và hủy copy

Với file lớn hoặc copy qua network filesystem, một operation có thể mất nhiều thời gian. Vòng buffered stream/channel tự viết có thể kiểm tra cờ dừng giữa các chunk, nhưng cần hiểu những lệnh I/O đang block có thể chưa trả ngay khi cờ đổi.

`Files.copy` cấp cao không cung cấp trực tiếp callback tiến độ cho mỗi block ở overload Path→Path. Nếu cần progress UI hoặc cancellation từng khối, việc tự quản lý chunk/byte count ở stream/channel có thể phù hợp hơn.

Không ghi progress “100%” trước khi flush/close/verify theo định nghĩa hoàn tất của sản phẩm. Với các yêu cầu durability, trạng thái “byte đã chuyển” và “commit đã bền” là hai mốc khác nhau.

### 14.2. Log và metrics

Đối với job copy lớn, nên ghi:

- Kích thước nguồn đã xác minh, tên job hoặc ID an toàn.
- Số byte đã chuyển, thời gian, throughput đo được với nhãn môi trường.
- API/chiến lược thực tế được dùng và fallback đã xảy ra hay chưa.
- Phân loại lỗi: permissions, disk full, source truncated, target stalled, checksum mismatch.
- Trạng thái: STARTED / COPYING / VERIFYING / COMMITTED / FAILED.

Không log nguyên nội dung tệp nhạy cảm hoặc đường dẫn chứa token/secret nếu không cần. Tại thời điểm báo COMMITTED, các điều kiện “đã copy / đã verify / đã sync / đã publish” phải được ghi nhận chính xác.

## 15. Nên dùng API nào trong từng tình huống?

### Trường hợp A: copy một file thông thường

```java
Files.copy(source, target);
```

Ưu tiên vì ngắn, rõ mục đích, dễ xử lý options. Chỉ chuyển sang tự viết loop khi cần behavior mà API cấp cao không cung cấp hoặc benchmark đáng tin chứng minh có ích.

### Trường hợp B: chuyển InputStream nhận từ API khác thành file

```java
long bytes = Files.copy(
        inputStream,
        target,
        StandardCopyOption.REPLACE_EXISTING);
```

Khi stream thuộc caller, caller phải đóng nó theo ownership contract. Với nguồn không tin cậy, **giới hạn bytes khi nhận** bằng một wrapper/loop có hard cap; một lời gọi copy thẳng không tự đặt quota do ứng dụng mong muốn.

### Trường hợp C: cần xử lý từng chunk (checksum/progress/cancel)

Dùng stream loop hoặc `FileChannel.read/write` để đo bytes, cập nhật checksum và kiểm tra cờ stop tại những điểm rõ ràng. Khi muốn thay đổi nội dung, buffered stream cho từng chunk có thể dễ hiểu hơn. `FileChannel` có lợi nếu cũng cần random access hoặc positional I/O.

### Trường hợp D: cần copy một đoạn của file rất lớn

Dùng `FileChannel.transferTo(start,length,target)` với loop xử lý partial transfer; hoặc positional reads/writes qua ByteBuffer nếu native transfer không phù hợp. Đảm bảo offsets là `long` và không vượt file size theo dữ liệu đã kiểm chứng.

### Trường hợp E: tối ưu truyền bytes từ file tới socket

`FileChannel.transferTo` có thể tận dụng đường tối ưu từ file tới một `WritableByteChannel` phù hợp, nhưng với socket non-blocking phải quản lý readiness/backpressure và kết quả transfer partial hoặc 0. Không áp đoạn loop file-to-file có giả định write luôn tiến lên socket theo cách thiếu kiểm soát.

### Trường hợp F: backup với metadata và crash consistency

Không có một lời gọi nào trong ba cách tự mang toàn bộ semantics backup. Cần kiểm kê metadata, snapshot logic, staging/verify, durability và atomic publish theo filesystem, cùng policy recovery.

## 16. Lỗi phổ biến

1. **`Files.readAllBytes` toàn bộ file nhiều GB chỉ để copy.** Có thể vượt giới hạn mảng/heap.
2. **Đọc/ghi từng byte bằng I/O không buffer** trong vòng lớn. Tăng overhead; nên dùng chunk.
3. **Nghĩ `read(chunk)` luôn lấp đầy mảng.** Phải dùng `n` được trả về.
4. **Ghi `out.write(chunk)` ở lượt cuối dù chỉ đọc ít byte.** Có thể ghi lại dữ liệu cũ.
5. **Quên `close`/`try-with-resources`.** Rò rỉ file descriptor và bỏ sót flush.
6. **Cho rằng `flush` tương đương fsync.** Không có bảo đảm durability như vậy.
7. **Gọi `Files.copy` lên target đã tồn tại mà không đặt overwrite policy.** Có thể ném `FileAlreadyExistsException`.
8. **Dùng `REPLACE_EXISTING` rồi nghĩ copy atomic.** Replace option không biến copy thành transaction.
9. **Cho rằng `COPY_ATTRIBUTES` giữ mọi ACL/metadata trên mọi FS.** Provider có giới hạn.
10. **Cho rằng `Files.copy` luôn zero-copy.** Implementation cụ thể quyết định.
11. **Cho rằng `FileChannel.transferTo` luôn zero-copy.** Java API không cam kết.
12. **Chỉ gọi `transferTo` một lần và nghĩ đã copy hết.** Có thể partial transfer.
13. **Khi `transferTo` trả 0, cứ loop ngay vô hạn.** Cần xử lý progress/fallback/fail.
14. **Nhầm offset file với `ByteBuffer.position`.** Hai vị trí khác nhau; transferTo có positional source offset.
15. **Dùng `int` cho size/offset file nhiều GB.** Tràn số và copy sai vùng.
16. **Cast kích thước còn lại sang `int` trước khi giới hạn.** Có thể thành số âm/sai.
17. **Quên `flip()` sau channel read.** Buffer chưa sẵn sàng cho write.
18. **Quên `clear()` sau đã consume bytes.** Vòng tiếp thiếu chỗ nhận.
19. **Cho rằng một `FileChannel.write` ghi hết `remaining()`.** Có thể partial write.
20. **Cấp phát direct ByteBuffer mới mỗi chunk.** Tăng chi phí/tài nguyên không cần thiết.
21. **Benchmark một lần theo thứ tự cố định.** Dễ bị cache và tải nền bóp méo.
22. **So cách copy bytes thuần với cách vừa copy attributes/force rồi kết luận nhanh-chậm.** Khác semantics nên so sánh không công bằng.
23. **Đo bao gồm verify ở cách này nhưng không ở cách khác.** Kết quả throughput thiên lệch.
24. **Nghĩ file size bằng nhau là bytes giống nhau.** Cần `Files.mismatch` hoặc kiểm tra nội dung thích hợp.
25. **Nghĩ `Files.mismatch==-1` chứng minh durability.** Nó chỉ so sánh bytes ở thời điểm đọc.
26. **Copy file đang bị sửa đồng thời và mặc định có snapshot nhất quán.** Có thể trộn phiên bản.
27. **Copy trực tiếp vào file quan trọng rồi không xử lý trạng thái lỗi một phần.** Nên staging/verify/publish theo policy.
28. **Quên xử lý symbolic links** trong tree copy hoặc input path không tin cậy.
29. **Nghĩ `Files.copy` copy đệ quy cả cây directory.** Nó không tự sao chép mọi entry con.
30. **Dùng `APPEND` trong lúc muốn file đích bằng đúng source.** Tạo nội dung sai.
31. **Sao chép source lên chính source với TRUNCATE_EXISTING.** Nguy cơ mất dữ liệu.
32. **Coi “method return” = “đã ghi bền trên đĩa”.** Cần force/sync riêng khi durability là yêu cầu.
33. **Tin FileChannel luôn nhanh hơn buffered streams với file nhỏ.** Chi phí mở, transfer, caching có thể đảo chiều.
34. **Không xác định ai sở hữu InputStream/OutputStream truyền cho API.** Dễ đóng sớm hoặc rò rỉ.
35. **Không giới hạn byte từ nguồn InputStream do user kiểm soát.** Có thể fill disk/quota.
36. **Cho rằng JVM/GC tự dọn file tạm khi job lỗi.** Cần cleanup/recovery rõ ràng.

## 17. Câu hỏi tự kiểm tra

**Câu 1.** Chỉ copy một file trên cùng máy, không cần offset/progress/biến đổi, nên cân nhắc API nào trước?  
**Trả lời:** `Files.copy(source,target)` vì rõ nghĩa và ít code. Benchmark nếu hiệu năng có yêu cầu đặc biệt.

**Câu 2.** Một lượt `in.read(chunk)` trả 123 byte, `chunk.length=65536`. Phải ghi bao nhiêu?  
**Trả lời:** `out.write(chunk,0,123)`, không ghi toàn mảng.

**Câu 3.** `FileChannel.transferTo(0,size,target)` trả nhỏ hơn size có phải bug Java không?  
**Không.** API cho phép partial transfer; phải lặp theo số byte thực chuyển.

**Câu 4.** Nếu `transferTo` trả 0 khi vẫn còn phần cần copy, nên làm gì?  
**Trả lời:** Không spin vô hạn. Xem nguyên nhân, fallback qua ByteBuffer hoặc báo lỗi/đợi readiness tùy loại target.

**Câu 5.** Với file 5 GiB, có được cast `channel.size()` sang `int` không?  
**Không.** Size và offset cần `long`; giới hạn buffer bằng int riêng.

**Câu 6.** `Files.copy(Path,Path)` trả về `long` số bytes phải không?  
**Không.** Nó trả `Path` đích. Các overload từ InputStream hoặc tới OutputStream trả byte count kiểu `long`.

**Câu 7.** BufferedInputStream có luôn tăng tốc khi code đã tự đọc mảng 1 MiB không?  
**Không bảo đảm.** Buffering bổ sung có thể dư; phải đo workload thực.

**Câu 8.** `Files.copy(source,target,COPY_ATTRIBUTES)` có copy mọi extended attributes/ACL trên mọi OS không?  
**Không.** Hỗ trợ và mức chính xác metadata phụ thuộc provider và filesystem.

**Câu 9.** `Files.copy(source,target,REPLACE_EXISTING)` có atomic không?  
**Không được bảo đảm.** Copy có thể là thao tác nhiều bước và để lại partial target khi lỗi.

**Câu 10.** `Files.mismatch(source,target)==-1` chứng minh điều gì?  
**Trả lời:** Hai file có bytes giống nhau tại thời điểm được so sánh theo API; không chứng minh crash durability hay snapshot consistency nếu file bị đổi đồng thời.

**Câu 11.** Vì sao benchmark copy thứ tự cố định có thể không công bằng?  
**Vì** page cache, JIT, hoạt động nền và trạng thái lưu trữ thay đổi giữa lượt, khiến kết quả phụ thuộc thứ tự.

**Câu 12.** `FileChannel.transferTo` có bắt buộc tránh mọi copy trong OS không?  
**Không.** Native/zero-copy là tối ưu khả dĩ, không phải cam kết portable của API.

**Câu 13.** Đã gọi `BufferedOutputStream.flush()`, sau mất điện file chắc chắn đủ bytes không?  
**Không.** Flush tầng Java không đồng nghĩa độ bền storage; cần sync/force phù hợp.

**Câu 14.** Nếu source thay đổi trong lúc copy, Files.copy có cho một snapshot version duy nhất không?  
**Không có bảo đảm chung.** Cần filesystem snapshot, immutable files hoặc giao thức điều phối.

**Câu 15.** Khi cần copy nội dung một directory cùng các subdirectories thì gọi `Files.copy(dir,dest)` một lần đủ không?  
**Không.** Cần duyệt cây và xử lý từng entry, symlink, attributes và lỗi.

**Câu 16.** FileChannel positional `transferTo` có thay đổi position của source FileChannel không?  
**Không.** Offset nguồn được truyền vào, source position không đổi; target có position thì position được cập nhật theo bytes ghi.

## 18. Ghi nhớ và tài liệu chính thức

**Tóm tắt quyết định:**

```text
Cần copy nguyên một file?
    ├── Có, không cần progress/chunk transformation
    │       └── Ưu tiên thử Files.copy(Path,Path,options)
    └── Cần pipeline riêng?
            ├── Chỉ có InputStream/OutputStream hoặc xử lý từng chunk
            │       └── Buffered streams với mảng byte tái sử dụng
            └── Cần offset/random access/transfer giữa channels
                    └── FileChannel + loop partial transfer
                              │
                              └── Đo workload thật trước khi chọn tối ưu
```

Dù chọn cách nào cũng nhớ: **không đọc hết file lớn vào heap chỉ để copy; kiểm tra giá trị số byte thực chuyển; bảo đảm đóng tài nguyên; nhận diện partial copy; phân biệt bytes đã copy, bytes đã kiểm tra và bytes đã ghi bền.**

Tài liệu Java SE 21 chính thức:

- `Files.copy` và các overload/semantics: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html
- `StandardCopyOption` và `LinkOption`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/StandardCopyOption.html và https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/LinkOption.html
- `StandardOpenOption`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/StandardOpenOption.html
- `FileChannel` — `read`, `write`, `transferTo`, `transferFrom`, `size`, `force`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/FileChannel.html
- `ByteBuffer` — `flip`, `clear`, `remaining` và direct buffers: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/ByteBuffer.html
- `BufferedInputStream` / `BufferedOutputStream`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/BufferedInputStream.html và https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/BufferedOutputStream.html
- `Files.mismatch` — so sánh contents theo bytes: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html#mismatch(java.nio.file.Path,java.nio.file.Path)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="performance-and-large-files-005">Heap buffer, direct memory và áp lực bộ nhớ</a>

<details>
<summary>Xem chi tiết</summary>

# Heap buffer, direct memory và áp lực bộ nhớ trong Java NIO

## 1. Bài toán thực tế: dữ liệu tệp lớn và bộ nhớ không chỉ nằm ở heap

Khi đọc hoặc ghi tệp lớn bằng Java NIO, chương trình thường tạo các đối tượng `ByteBuffer`. Có hai cách cấp phát dễ thấy:

- `ByteBuffer.allocate(capacity)`: tạo **heap byte buffer**, vùng dữ liệu nằm trong bộ nhớ heap của JVM.
- `ByteBuffer.allocateDirect(capacity)`: yêu cầu tạo **direct byte buffer**, dữ liệu nằm trong vùng bộ nhớ có thể được I/O native truy cập trực tiếp hơn, thường được gọi là *off-heap/direct memory*.

Một sai lầm thường gặp là cho rằng tăng `-Xmx` sẽ giải quyết tất cả lỗi bộ nhớ. Thực tế ứng dụng Java còn tiêu thụ direct buffers, memory-mapped files, thread stacks, metaspace, JIT code cache, thư viện native, page cache của hệ điều hành và nhiều tài nguyên khác. Một chương trình có **Java heap còn dư** vẫn có thể không cấp phát được direct buffer hoặc bị hệ điều hành/container giới hạn tổng mức sử dụng bộ nhớ.

Ngược lại, cũng không nên kết luận “direct luôn nhanh hơn heap”. Direct buffer có lợi trong một số đường truyền dữ liệu qua native I/O, nhưng chi phí cấp phát/thu hồi, cache locality, việc copy dữ liệu từ đối tượng Java và mô hình sử dụng có thể làm nó kém hiệu quả hơn heap buffer.

Mục tiêu bài học:

1. Phân biệt cách tổ chức heap/direct buffer và API nhận biết.
2. Hiểu direct memory được quản lý khác heap như thế nào, cách vòng đời buffer ảnh hưởng áp lực bộ nhớ.
3. Biết khi nào direct buffer có khả năng giảm copy trong FileChannel/SocketChannel và khi nào không.
4. Đọc đúng các chỉ số `BufferPoolMXBean`, Java heap, RSS/container memory.
5. Thiết kế pool, giới hạn kích thước buffer và backpressure cho tệp lớn/tải cao.
6. Viết Java 17+ có ví dụ chạy được, vòng lặp read/write đúng và cleanup rõ ràng.

## 2. Heap ByteBuffer là gì?

`ByteBuffer.allocate(n)` cấp phát một `ByteBuffer` có dung lượng `n` byte. Với heap buffer từ `allocate`, backing storage thông thường là một mảng `byte[]` do GC heap quản lý.

~~~java
ByteBuffer heap = ByteBuffer.allocate(1024);
System.out.println(heap.isDirect()); // false
System.out.println(heap.hasArray()); // true
~~~

Đây là đoạn mã minh họa trong method có import `java.nio.ByteBuffer`. Các tính chất:

- Dữ liệu thực được lưu trong một mảng `byte[]` trên Java heap; heap limit `-Xmx` có liên quan tới dung lượng có thể cấp.
- `hasArray()` cho biết có thể lấy backing array của buffer hiện tại qua `array()`; với writable heap buffer từ `allocate` là `true`.
- `arrayOffset()` và `position()` cùng quan trọng khi chuyển một vùng buffer sang API nhận mảng. Không được mặc định byte hợp lệ bắt đầu tại offset 0.
- `ByteBuffer.wrap(byte[])` tạo một buffer gắn với **mảng do caller cung cấp**; nó không tạo bản sao của mảng đó.
- GC quản lý mảng backing; khi không còn reachable, dữ liệu có thể được thu hồi qua các chu kỳ GC theo chính sách runtime, không phải ngay khi gọi `clear()`.

Heap buffer phù hợp với parser thuần Java, tích hợp API dùng `byte[]`, các buffer sống ngắn hoặc workload mà lợi ích direct I/O không vượt chi phí quản lý native memory.

**Không phải mọi buffer có `isDirect()==false` đều có `hasArray()==true` có thể sử dụng từ ngoài.** Ví dụ read-only heap buffer có thể không cho truy cập backing array. Hãy hỏi `hasArray()` trước khi gọi `array()`, thay vì dựa vào `!isDirect()`.

## 3. Direct ByteBuffer là gì?

`ByteBuffer.allocateDirect(n)` tạo một direct buffer, có backing storage dùng cho I/O native, không nằm trong mảng `byte[]` trên Java heap.

~~~java
ByteBuffer direct = ByteBuffer.allocateDirect(1024);
System.out.println(direct.isDirect()); // true
System.out.println(direct.hasArray()); // false
~~~

Direct buffer vẫn là một đối tượng Java có `position`, `limit`, `capacity` và các phương thức đọc/ghi, nhưng storage dữ liệu thường ở vùng native memory. Hãy phân biệt:

- **Java wrapper object**: có thể được GC nhìn thấy và quản lý vòng đời đối tượng.
- **Off-heap backing memory**: phục vụ bytes của buffer, nằm ngoài phần heap được giới hạn bởi `-Xmx`.
- **Reference graph**: nếu ứng dụng giữ một tham chiếu đến direct buffer hoặc một view như `slice()/duplicate()`, bộ nhớ bên dưới có thể vẫn cần được giữ.
- **Native address**: là chi tiết implementation; API `ByteBuffer` không công khai một giao diện portable để giải phóng hoặc thao tác địa chỉ native tùy ý.

`direct.array()` không dùng được vì direct buffer không có backing `byte[]` công khai. Nếu code cần dùng một API chỉ nhận mảng, phải tổ chức copy bytes sang mảng, hoặc dùng một API chấp nhận `ByteBuffer`.

### Direct không đồng nghĩa “bên ngoài GC hoàn toàn”

Bộ nhớ direct **không chiếm dung lượng mảng trong heap** nhưng vòng đời của nó thường được liên kết với khả năng reachability/cleaner của các object Java. Trên các JDK triển khai phổ biến, bộ nhớ native được giải phóng khi runtime xác định không còn tham chiếu phù hợp và tiến hành cleanup. **Không có cam kết nó được thu hồi ngay khi biến cục bộ ra khỏi scope.**

Vì thế, trong ứng dụng cần cấp phát/thu hồi nhanh, việc tạo `allocateDirect` mới cho mọi request có thể gây áp lực bộ nhớ và GC, dù heap sử dụng ít. Dùng direct buffer có giới hạn số lượng và tái sử dụng có thể giúp giảm áp lực này.

### Direct allocation có thể thất bại khi heap còn dư

Khi vượt hạn mức direct memory hoặc runtime/OS không thể cấp phát native backing, quá trình cấp phát có thể ném `OutOfMemoryError` liên quan đến direct buffer memory. Không được kết luận `OutOfMemoryError` chỉ liên quan đến `Java heap space`. Cũng không được tự động tăng `-Xmx` và coi là đã giải quyết.

## 4. ByteBuffer vẫn có position, limit, capacity và mark

Heap và direct buffer tuân theo cùng mô hình con trỏ:

- `capacity`: kích thước vùng dữ liệu được cấp ban đầu, không đổi trong đời của buffer.
- `position`: vị trí đọc/ghi kế tiếp của các thao tác tương đối.
- `limit`: ranh giới các vị trí hiện tại được phép thao tác.
- `mark`: điểm position được đánh dấu tùy chọn.

Mẫu ghi rồi đọc:

~~~java
ByteBuffer buffer = ByteBuffer.allocateDirect(16);
buffer.put(new byte[] {65, 66, 67}); // Viết A B C; position=3.
buffer.flip();                       // limit=3, position=0.
while (buffer.hasRemaining()) {
    System.out.print((char) buffer.get());
}
buffer.clear(); // Chuẩn bị ghi tiếp, KHÔNG giải phóng native memory.
~~~

Đây là đoạn minh họa. Việc `clear()` **không xóa bytes**, không bảo đảm zeroize dữ liệu nhạy cảm và **không giải phóng backing storage**. Nó chỉ thay đổi các chỉ số trạng thái.

Tương tự, `compact()` di chuyển các byte chưa đọc về đầu buffer rồi thiết lập lại chỉ số để tiếp tục ghi. Trên buffer lớn, di chuyển bytes cũng có chi phí. Thiết kế parser/queue nên tránh compact dữ liệu quá nhiều lần không cần thiết.

### View chia sẻ storage dù cursor độc lập

`duplicate()`, `slice()` và `asReadOnlyBuffer()` tạo các view có trạng thái con trỏ riêng nhưng vẫn chia sẻ backing bytes với buffer gốc.

~~~java
ByteBuffer root = ByteBuffer.allocateDirect(4);
root.put(new byte[] {1, 2, 3, 4});
ByteBuffer view = root.duplicate();
view.put(0, (byte) 9);
System.out.println(root.get(0)); // 9
~~~

Đây là đoạn mã minh họa. Hai object khác nhau không phải hai allocation native độc lập. Nếu một thread đang thao tác vùng bytes qua `root` và thread khác qua `view`, đồng bộ con trỏ riêng **không** tự bảo đảm tính an toàn của backing storage.

View read-only cấm write qua view đó nhưng không làm backing bytes bất biến nếu một alias ghi được vẫn còn tồn tại. Vì vậy, khi đưa buffer cho worker hoặc socket operation, phải xác định ownership và thời điểm producer được phép reuse.

## 5. Heap và direct ảnh hưởng I/O performance ra sao?

Một số channel/native I/O API có thể truyền dữ liệu từ/to direct memory mà không phải sao chép qua một mảng heap trung gian. Đây là **lý do direct buffer được khuyến nghị cân nhắc với các I/O operation cường độ cao**, không phải lời hứa mọi thao tác đều zero-copy.

Trong một số implementation, khi truyền một heap buffer qua FileChannel/SocketChannel, runtime có thể dùng một vùng native tạm để đưa dữ liệu vào syscall, kéo theo copy. Khi dùng direct buffer, một số copy nội bộ đó có thể được tránh.

Tuy nhiên:

1. Nếu dữ liệu ban đầu là `byte[]` hoặc `String`/object trên heap, copy vào direct buffer vẫn là copy. Đừng bỏ qua chi phí chuyển dữ liệu ban đầu.
2. Đọc file từ ổ đĩa thường liên quan đến **kernel page cache** và copy giữa kernel/user memory; direct `ByteBuffer` không tự động bật kernel zero-copy.
3. Các API như `FileChannel.transferTo/transferFrom` có thể dùng đường truyền tối ưu hóa hoặc fallback tùy OS, filesystem, channel và điều kiện runtime. Chúng khác với chỉ dùng `allocateDirect`.
4. Direct allocation/cleanup đắt hơn heap allocation ở nhiều tình huống. Buffer nhỏ sống ngắn có thể chạy chậm hơn khi dùng direct.
5. CPU cache, paging, memory pressure, container memory và cách truy cập `get/put` đều ảnh hưởng throughput.
6. Với tệp nhỏ hoặc parser xử lý dữ liệu trên heap, direct không chắc đem lại lợi ích đo được.
7. Đọc một file rất lớn không có nghĩa phải cấp phát một direct buffer cực lớn. Thường hiệu quả hơn là streaming bằng các chunk vừa phải và có bounded queue.

Do vậy, hãy benchmark **đúng pipeline sản phẩm**: file size, I/O pattern, OS, filesystem, SSD/HDD, concurrency, buffer size và tải GC. Không dùng một microbenchmark đơn giản để khẳng định “direct nhanh hơn X%” trong mọi hệ thống.

## 6. Một ví dụ Java 17+ độc lập, biên dịch/chạy được

Chương trình dưới đây cho thấy: heap/direct buffer nhận biết qua `isDirect()/hasArray()`; `duplicate()` dùng chung byte nhưng có position riêng; direct buffer được dùng với `FileChannel.write` và heap buffer dùng để đọc lại; cuối cùng lấy metric về direct buffer pool từ management API.

Lưu tệp **`BufferMemoryDemo.java`**:

~~~java
import java.io.IOException;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class BufferMemoryDemo {
    public static void main(String[] args) throws IOException {
        ByteBuffer heap = ByteBuffer.allocate(16);
        ByteBuffer direct = ByteBuffer.allocateDirect(16);
        System.out.println("Heap: direct=" + heap.isDirect()
                + ", hasArray=" + heap.hasArray());
        System.out.println("Direct: direct=" + direct.isDirect()
                + ", hasArray=" + direct.hasArray());

        heap.put("ABCD".getBytes(StandardCharsets.UTF_8));
        ByteBuffer duplicate = heap.duplicate();
        duplicate.put(0, (byte) 'Z');
        duplicate.position(2);
        System.out.println("Shared bytes: " + (char) heap.get(0));
        System.out.println("Independent positions: " +
                (heap.position() != duplicate.position()));

        Path file = Files.createTempFile("nio-direct-", ".dat");
        try {
            direct.put("DATA".getBytes(StandardCharsets.UTF_8));
            direct.flip();
            try (FileChannel channel = FileChannel.open(file,
                    StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                while (direct.hasRemaining()) {
                    channel.write(direct);
                }
                channel.position(0);
                ByteBuffer received = ByteBuffer.allocate(16);
                while (true) {
                    int n = channel.read(received);
                    if (n == -1 || !received.hasRemaining()) break;
                }
                received.flip();
                System.out.println("File: " +
                        StandardCharsets.UTF_8.decode(received));
            }
        } finally {
            Files.deleteIfExists(file);
        }

        for (BufferPoolMXBean bean : ManagementFactory
                .getPlatformMXBeans(BufferPoolMXBean.class)) {
            if ("direct".equals(bean.getName())) {
                System.out.println("Direct pool observable: " +
                        (bean.getCount() >= 1));
            }
        }
    }
}
~~~

Lệnh chạy:

~~~bash
javac BufferMemoryDemo.java
java BufferMemoryDemo
~~~

**Stdout thực tế đã biên dịch và chạy trên JDK thử nghiệm:**

~~~text
Heap: direct=false, hasArray=true
Direct: direct=true, hasArray=false
Shared bytes: Z
Independent positions: true
File: DATA
Direct pool observable: true
~~~

Diễn giải:

- `allocate(16)` tạo heap buffer có backing array truy cập được; `allocateDirect(16)` tạo direct buffer không đưa `byte[]` ra ngoài.
- Sau `heap.put("ABCD")`, `heap.position()` là 4. `duplicate.position(2)` không sửa position của heap gốc, nhưng `duplicate.put(0, 'Z')` thay cùng storage, nên `heap.get(0)` trả chữ `Z`.
- `FileChannel.write(direct)` có thể chỉ ghi **một phần** số byte còn lại, vì thế vòng `while (direct.hasRemaining())` là đúng thay vì tin một lệnh write luôn đủ. Sau ghi, code đặt `channel.position(0)` rồi đọc vào heap buffer.
- `received.flip()` chuyển từ ghi vào buffer sang giải mã; `StandardCharsets.UTF_8.decode(received)` trả dữ liệu `DATA`.
- `Files.createTempFile` tạo file test riêng; `finally` xóa file, `try-with-resources` đóng channel.
- `BufferPoolMXBean` tên `direct` quan sát native/direct buffer pool do JDK quản lý. Dòng cuối chỉ kiểm tra đã có ít nhất một direct buffer trong pool tại thời điểm quan sát; nó **không được dùng làm quy tắc số lượng cố định trên mọi JDK/phiên bản**.

Ở demo, `FileChannel` là blocking file I/O với tệp rất nhỏ, nên vòng đọc cuối không cần một bộ selector hay thuật toán riêng cho mạng non-blocking. Trong production, muốn giảm rủi ro vòng lặp không tiến triển nếu provider trả 0 nhiều lần, cần kiểm tra `n==0`, timeout/budget hoặc quy tắc mode/provider cụ thể. Code ở đây không dùng `FileChannel.force()`, nên không trình bày tính bền dữ liệu.

## 7. Đếm bộ nhớ đúng: heap, direct, mapped và native khác

Một sơ đồ đơn giản hóa:

~~~text
Java process
  |
  +-- Java heap: objects, byte[], heap ByteBuffer backing arrays
  |
  +-- Native/off-heap:
  |     +-- Direct ByteBuffer backing memory
  |     +-- MappedByteBuffer mappings (memory-mapped regions)
  |     +-- Thread stacks
  |     +-- Metaspace / code cache / JVM internals
  |     +-- JNI/native libraries, allocators, buffers khác
  |
  +-- Process virtual address space / resident memory (RSS)
  |
OS:
  +-- Filesystem page cache và phần memory do kernel quản lý
  +-- Container/cgroup memory accounting tùy hệ thống
~~~

Sơ đồ chỉ để định hướng, không phải một bảng cộng số học tuyệt đối. Memory mapping có thể khiến cùng những physical pages của file xuất hiện trong thống kê RSS và page cache theo quy tắc kernel; không được cộng mọi con số từ công cụ giám sát như các bộ nhớ độc lập rồi kết luận tổng.

Một vài khái niệm quan trọng:

- **Heap used:** lượng object/data dùng trong heap được GC quản lý.
- **Heap committed:** dung lượng heap JVM đã commit; có thể khác `-Xmx`.
- **Direct buffer capacity:** tổng dung lượng logic của direct buffers được JVM buffer pool theo dõi; không nhất thiết bằng toàn bộ native memory của process.
- **Direct buffer memory used:** thống kê bytes native backing theo management bean; có thể khác tổng capacity do alignment/implementation.
- **Mapped buffer capacity/used:** thống kê mapping mà JVM theo dõi; nó không nói toàn bộ pages đều resident trong RAM.
- **RSS:** số trang resident của process theo OS, chứa nhiều nhóm memory khác nhau, không phải riêng heap/direct.
- **Container memory usage:** có thể phản ánh thêm cách kernel tính page cache/anonymous memory; thay đổi theo container/cgroup runtime.

**Không suy luận “heap usage 30% thì còn 70% RAM cho direct”.** Heap limit, direct limit, process RSS và container memory limit là các ràng buộc khác nhau.

## 8. Giới hạn và cấp phát direct memory

Tùy runtime, HotSpot có lựa chọn JVM `-XX:MaxDirectMemorySize=<size>` để kiểm soát giới hạn direct buffer allocations mà cơ chế direct buffer của JDK quản lý. Ví dụ cách chỉ định:

~~~bash
java -XX:MaxDirectMemorySize=256m -Xmx512m MyApplication
~~~

Đây là lệnh **minh họa**, `MyApplication` phải là main class thực tế. Flag này là tùy chọn của HotSpot/implementation, không phải đặc tả portable cho mọi JVM.

Không tự giả định giá trị mặc định của `MaxDirectMemorySize` giống nhau giữa các nhà cung cấp JDK, các phiên bản và cách cấu hình. Kiểm tra runtime thực tế và tài liệu phù hợp.

Tăng giới hạn direct memory có thể cho phép ứng dụng dùng thêm direct buffer nhưng **không tạo thêm RAM vật lý**. Trong container bị giới hạn memory, tăng heap + direct limit quá cao so với budget có thể khiến process bị OOM killer kill trước khi ứng dụng kịp bắt lỗi hoặc chạy cleanup.

Ví dụ ngân sách cần tính:

~~~text
Total process / container budget:
    Java heap committed
  + direct buffer backing allocations
  + metaspace and code cache
  + thread stacks
  + native/JNI allocations
  + mapped/resident pages and OS overhead
  + headroom for burst, GC, runtime, monitoring
~~~

Đây là mô hình lập ngân sách, không phải công thức cộng chính xác từ các chỉ số đo vì chúng có thể chồng lấn cách hạch toán. Nên có **headroom thực tế** thay vì đặt mỗi giới hạn độc lập sát trần container.

### Lỗi khi cấp phát quá nhiều

`ByteBuffer.allocateDirect(n)` có thể gây `OutOfMemoryError` khi đạt hạn mức direct memory được JDK quản lý hoặc native allocation thất bại. Triệu chứng khác có thể là latency spike do JVM cố dọn vùng direct không còn sử dụng.

Nếu thấy OOME kiểu *Cannot reserve ... bytes of direct buffer memory* trên một bản HotSpot, cần điều tra size/count/lifetime của buffer và giới hạn hiện tại thay vì chỉ tăng heap. Thông điệp chính xác không phải phần của Java SE API contract; đừng parse log theo một chuỗi duy nhất để xác định mọi nguyên nhân.

## 9. Vòng đời và vì sao pool thường quan trọng

Có hai cách dùng direct buffer:

**Không tái sử dụng, allocation per request:**

~~~java
ByteBuffer buffer = ByteBuffer.allocateDirect(64 * 1024);
try {
    // Xử lý một request.
} finally {
    // Không có method close() portable trên ByteBuffer.
}
~~~

Đây là mã minh họa. Tạo nhiều buffer cho các request nhanh có thể sinh hàng loạt native allocations. `ByteBuffer` **không triển khai `AutoCloseable`**, nên không thể dùng `try (ByteBuffer buffer = ...)` để đóng direct memory bằng API chuẩn.

**Dùng pool với giới hạn:**

~~~text
Application starts:
  allocate N direct buffers of size S
Request:
  acquire one buffer -> use -> clear -> return to pool
On overload:
  wait bounded / reject / backpressure
Application shutdown:
  stop using buffers, drop references, let supported runtime clean up
~~~

Pool giúp tái sử dụng allocation, nhưng **pool không tự giảm usage**: giữ 100 direct buffers 1 MiB nghĩa là giữ lượng backing memory tương ứng ngay cả khi tải đã xuống thấp. Dùng size classes, high-watermark và chính sách trim/evict khi phù hợp. Không tạo pool unbounded.

Một ví dụ tính capacity của pool (chỉ là phép tính ngân sách): 128 buffers × 256 KiB = **32 MiB** tổng dung lượng data backing theo capacity, chưa tính object/native overhead/alignment và các buffer khác của ứng dụng. Nếu concurrency thực tế vượt 128, phải chờ/trả lỗi/backpressure thay vì cứ tự cấp phát vô hạn.

### Ownership khi trả buffer vào pool

Không được trả buffer về pool **trước khi I/O async hoàn thành**. Nếu một task đang viết từ buffer mà thread khác gọi `clear()` rồi dùng buffer ấy cho request kế tiếp, dữ liệu truyền có thể bị đổi hoặc sai thứ tự.

Một protocol đúng cần:

1. Buffer được lease độc quyền cho request/operation.
2. Trong lúc đã giao cho channel operation/worker, producer không được sửa nội dung/cursor.
3. Với non-blocking `write`, buffer vẫn có bytes pending cho tới khi `hasRemaining()==false`.
4. Khi tất cả bên dùng đã hoàn tất và không còn view còn sống cần tham chiếu, owner mới trả buffer vào pool.
5. Buffer được `clear()`/zeroize nếu yêu cầu bảo mật trước khi cấp cho tenant/request mới.
6. Pool bounded có xử lý overload, shutdown và leak detection (lease chưa trả).

Đặc biệt, `duplicate()/slice()/asReadOnlyBuffer()` chia sẻ backing memory. Nếu một view còn được worker dùng mà root buffer đã trả về pool thì có nguy cơ dữ liệu bị ghi đè. Cursor độc lập không thay thế ownership của bytes.

## 10. Áp lực bộ nhớ với nhiều connection và tệp lớn

Một thiết kế như “mỗi kết nối có một direct buffer 1 MiB” có thể nhanh chóng tiêu tốn rất nhiều native memory. Với 10.000 kết nối, chỉ riêng danh nghĩa input buffer 1 MiB mỗi kết nối đã xấp xỉ **9,77 GiB** dữ liệu buffer, chưa tính outbound queue, TLS buffers, heap, thread stacks và native runtime.

Không cần một buffer đầy đủ cho mọi file:

- Dùng **bounded chunk buffers** 8 KiB–1 MiB tùy benchmark/load, không ép cứng kích thước.
- **Stream** file qua các chunks, xử lý xong tới đâu gửi/ghi tới đó.
- Với per-connection buffers, cấp phát lazy khi cần, giới hạn số bytes queued và có chính sách reclaim.
- Nếu dữ liệu có thể rất lớn, không gọi `Files.readAllBytes(path)` trên toàn tệp nếu vượt memory budget.
- Khi producer nhanh hơn consumer, **backpressure**: dừng đọc tạm thời, chặn bounded queue, giới hạn đồng thời hoặc fail fast.
- Nếu dùng async/non-blocking I/O, quản lý partial reads/writes và không tái sử dụng buffer khi operation còn pending.

Điểm cốt lõi là **số lượng buffer × capacity × concurrency × thời gian giữ** quyết định native footprint, không chỉ kích thước từng file.

### Áp lực heap gián tiếp dù dùng direct

Direct buffers có wrapper objects trên heap và references trong các collection/pools. Các object giữ native memory sống dài có thể nhỏ trên heap nhưng “ghim” vùng off-heap lớn. Do vậy một heap dump với rất ít byte object có thể chưa giải thích vì sao direct memory đang gần hết.

Ngoài ra, chuyển đổi bytes `String`, mã hóa UTF-8, parsing JSON, tạo array copies vẫn cấp phát trên heap. Dùng direct buffer cho FileChannel không loại bỏ toàn bộ heap allocations của ứng dụng.

## 11. FileChannel: read/write đúng và không nhầm zero-copy

Khi ghi từ `ByteBuffer`:

~~~java
while (buffer.hasRemaining()) {
    int written = channel.write(buffer);
    if (written == 0) {
        // Chưa có tiến triển: tùy loại channel/mode mà chờ hoặc đổi chiến lược.
    }
}
~~~

Đây là đoạn minh họa. Một lời gọi `write` **không được mặc định** chuyển hết `buffer.remaining()`. Với `SocketChannel` non-blocking, `write()==0` có thể xảy ra thường xuyên; phải đợi `OP_WRITE` thay vì busy-spin. Với `FileChannel` truyền thống, behavior phụ thuộc provider/mode và điều kiện I/O; code production vẫn nên xử lý lỗi và progress.

Khi đọc:

~~~java
int n = channel.read(buffer);
if (n == -1) {
    // EOF.
} else if (n > 0) {
    buffer.flip();
    // Xử lý bytes đã nhận.
    buffer.compact(); // Giữ bytes frame chưa được tiêu thụ, nếu có.
} else {
    // 0 byte lần này: không kết luận EOF.
}
~~~

Đây cũng là mã minh họa, cần `channel` và `buffer` hợp lệ. Nếu đã tiêu thụ hết bytes nhận được thì `clear()` có thể phù hợp; nếu còn frame chưa đủ, `compact()` giữ phần còn lại.

**Zero-copy không đồng nghĩa direct buffer:** thuật ngữ thường nói tới đường dữ liệu có thể tránh những lần copy giữa kernel/user space, chẳng hạn một số triển khai của `FileChannel.transferTo/transferFrom`. Cần kiểm tra giới hạn và return count của những phương thức này; có thể chỉ chuyển một phần dữ liệu hoặc dùng fallback. `allocateDirect` không bảo đảm luôn đi qua kernel zero-copy.

## 12. Memory-mapped file khác direct allocation thông thường

`FileChannel.map(...)` tạo `MappedByteBuffer` đại diện một vùng được ánh xạ tới file. Nó là một loại direct buffer nhưng **khác về mô hình bộ nhớ** với `ByteBuffer.allocateDirect()`:

- Storage gắn với memory-mapped region do hệ điều hành quản lý.
- Truy cập buffer có thể kéo các file-backed pages vào bộ nhớ resident theo nhu cầu (page faults).
- Mapping rất lớn không nhất thiết làm toàn bộ file lập tức resident trong RAM.
- Vòng đời mapping và giải phóng vùng nhớ phụ thuộc reachability/runtime; API Java 17 `MappedByteBuffer` không cung cấp một `close()` chuẩn cho mapping.
- Đóng `FileChannel` không có nghĩa mapping đã bị unmapped ngay.
- Khi file bị truncate/change bất thường hoặc hệ điều hành trả lỗi mapping, hành vi có thể phụ thuộc nền tảng và vùng truy cập.

Ví dụ minh họa:

~~~java
try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
    MappedByteBuffer mapped = channel.map(FileChannel.MapMode.READ_ONLY, 0, size);
    // Truy cập các byte trong mapped; không tự assume toàn bộ file đã resident.
}
~~~

Đây là mã minh họa; `path` phải tồn tại, `size` hợp lệ trong giới hạn buffer mapping, import `MappedByteBuffer`, `FileChannel` và `StandardOpenOption`. Nếu muốn xử lý một file lớn vượt giới hạn addressable của một `ByteBuffer`, phải map thành nhiều vùng với windowing hoặc dùng read/write theo chunk. Không map cả file nhiều terabyte thành một buffer duy nhất rồi hy vọng `capacity()` chứa được số lớn hơn phạm vi int.

### Khi nào dùng mapped file?

Memory mapping có thể hữu ích với random access, index lớn, hoặc truy cập lặp nhiều vùng file với pattern hợp lý. Nhưng với tệp chỉ cần copy tuần tự một lần, `FileChannel.read` hoặc `transferTo` có thể đơn giản hơn và đôi khi nhanh hơn; cần đo thực tế.

Mapped buffer còn tạo ra các quan tâm riêng: kiểm soát page cache, mapping lifecycle, truncate races, virtual memory address usage và cơ chế flush metadata/durability. `MappedByteBuffer.force()` yêu cầu thay đổi ở vùng mapped được ghi xuống storage theo hợp đồng API; không phải transaction và không áp dụng tương đương cho mọi loại direct buffer.

## 13. Quan sát direct memory qua BufferPoolMXBean

Java Management API có `java.lang.management.BufferPoolMXBean` để quan sát các pool buffer mà VM quản lý. Có thể lấy danh sách bằng:

~~~java
for (BufferPoolMXBean pool : ManagementFactory.getPlatformMXBeans(
        BufferPoolMXBean.class)) {
    System.out.printf("%s: count=%d, capacity=%d, used=%d%n",
            pool.getName(),
            pool.getCount(),
            pool.getTotalCapacity(),
            pool.getMemoryUsed());
}
~~~

Đây là đoạn minh họa cần import `BufferPoolMXBean` và `ManagementFactory`, có thể đặt trong method. Tên pool thường gặp ở HotSpot gồm `direct` và `mapped`, nhưng tập tên/counters là đặc tính của implementation và phiên bản JDK.

- `getCount()`: số buffer được pool quản lý.
- `getTotalCapacity()`: tổng dung lượng `capacity` của buffer trong pool tính bằng byte.
- `getMemoryUsed()`: ước lượng số bytes bộ nhớ được pool sử dụng, có thể lớn/nhỏ khác capacity do cách quản lý/native alignment. **Giá trị có thể là -1** nếu implementation không có số đo.
- Những chỉ số này **không bao phủ mọi native memory**. JNI `malloc`, thread stack hoặc allocations của một thư viện C có thể không được cộng trong direct buffer pool.
- Con số có thể thay đổi do GC/cleaners, cache direct buffer tạm, hoạt động I/O và thời điểm đo.

Dùng số đo này cùng heap metrics, GC logs, RSS/container metrics thay vì dựa một chỉ số để quyết định vấn đề là leak hay workload peak.

### Native Memory Tracking (NMT)

Trên một số bản HotSpot có **Native Memory Tracking**, có thể chạy JVM với cấu hình như:

~~~bash
java -XX:NativeMemoryTracking=summary -Xmx512m MyApplication
~~~

Sau đó có thể dùng công cụ JDK như `jcmd <pid> VM.native_memory summary` trên một process phù hợp, nếu cấu hình và quyền truy cập cho phép. Đây là lệnh minh họa với PID giả định, không phải kết quả đo cho chương trình demo.

NMT phân loại nhiều allocations do JVM theo dõi nhưng **không phải hệ thống đo mọi native allocation của thư viện ngoài JVM**. RSS, procfs, container metrics, profiler native và allocation tracing có thể cần thiết để truy dấu các vùng ngoài phạm vi.

## 14. Đọc biểu hiện OOME và áp lực bộ nhớ

Một số hiện tượng:

| Hiện tượng | Khả năng cần kiểm tra | Hướng điều tra |
| --- | --- | --- |
| `OutOfMemoryError: Java heap space` | Heap objects/mảng chiếm quá mức `-Xmx` | Heap dump, GC logs, object retaining paths |
| OOME liên quan `direct buffer memory` | Direct buffer allocations vượt budget hoặc không giải phóng kịp | BufferPoolMXBean, pool leases, `MaxDirectMemorySize`, retention |
| Container bị kill không có Java stacktrace rõ | Tổng RSS/cgroup memory vượt limit | Container events, RSS, page cache, heap+native budget |
| Heap thấp nhưng RSS cao | Direct/native/mmap/code cache/thread stacks | JMX buffer pools, NMT, OS profiler |
| GC hoạt động nhiều khi direct allocations tăng | Native cleanup/allocator pressure hoặc heap refs | GC logs, allocation profile, object references |
| Latency tăng lúc tải cao | Pool exhaustion, allocation cost, page faults, backpressure thiếu | Metrics theo connection, queue depth, timeouts |
| Mapped memory lớn nhưng heap thấp | Mapping virtual space/file-backed pages | Mapped buffer counters, RSS/maps, page residency |

Không phải cứ thấy direct buffer count tăng là memory leak. Một pool được thiết kế để giữ buffers tới mức high-watermark sẽ khiến count tăng rồi ổn định. **Leak** thường thể hiện ở buffer/lease/retained references tăng mà không giảm theo vòng đời mong đợi, hoặc số buffers đang được giữ không tương ứng với workload.

### Thứ tự xử lý sự cố

1. Đo heap used/committed/max, GC pauses và object retention.
2. Đo direct/mapped buffer count/capacity/memory used theo thời gian.
3. Đo RSS/process virtual memory và container limit/usage.
4. Kiểm tra tham số JVM như `-Xmx` và `-XX:MaxDirectMemorySize` ở runtime thật.
5. Tìm nơi `allocateDirect` được gọi nhiều hoặc pool không được trả.
6. Đối chiếu số connection, buffers leased, pending output, worker queue và file mapping count.
7. Thử giới hạn concurrent operations và buffer size để xác nhận causal relationship.
8. Chỉ sau khi hiểu nguyên nhân mới điều chỉnh memory budgets hoặc cấu trúc allocator.

Đặt `System.gc()` thành cách giải quyết thường xuyên để “dọn direct memory” là một anti-pattern: GC không phải API thu hồi direct buffer có thời điểm xác định, có thể tăng latency và che giấu quản lý buffer sai.

## 15. ByteBuffer và an toàn đa luồng

`ByteBuffer` là mutable. Heap hay direct không thay đổi điều này. Hai thread thao tác `position/limit` của cùng object mà không có ownership/đồng bộ có thể gây lỗi cursor. Hai view khác object cũng có thể chia sẻ byte vùng backing, nên ghi đồng thời vẫn có nguy cơ race.

Các lựa chọn an toàn:

- **Thread confinement**: một buffer do một event loop/thread sở hữu ở từng thời điểm.
- **Per-connection buffers**: mỗi kết nối có input/decoder state riêng; output queue riêng.
- **Ownership transfer**: chuyển giao buffer cho worker, không tái sử dụng cho tới khi worker hoàn tất.
- **Copy immutable payload**: copy bytes nhỏ sang `byte[]` riêng hoặc object immutable khi cần giảm coupling.
- **Read-only views**: ngăn sửa qua view đó nhưng vẫn phải đảm bảo không có alias ghi từ thread khác.
- **Buffer slices phân vùng**: hai views trên vùng byte không chồng lấn có thể an toàn hơn khi ownership được xác định; nếu storage chung và lifetime chung cần quản lý đầy đủ.

Đặc biệt khi một buffer được truyền cho `AsynchronousFileChannel` hoặc API I/O bất đồng bộ, đừng mutate nội dung/position hay trả về pool trước khi operation kết thúc. Một buffer bị reuse sớm có thể khiến byte được ghi ra file không còn là payload ban đầu, dù không có exception.

## 16. Khi nào nên chọn loại buffer nào?

| Tình huống | Điểm khởi đầu hợp lý | Điều cần thử nghiệm |
| --- | --- | --- |
| Parser Java xử lý bytes nhỏ, cần `byte[]` | Heap `ByteBuffer.allocate` hoặc mảng byte | Allocation/GC, parser throughput |
| Socket/FileChannel throughput cao, buffer tái dùng | Direct ByteBuffer có pool bounded | Native memory pressure, copy count, throughput |
| Mỗi request rất nhỏ, sống cực ngắn | Heap buffer, hoặc pool phù hợp | Direct allocation cost có vượt benefit không |
| Copy file tuần tự rất lớn | FileChannel streaming, cân nhắc `transferTo` | Copy path, OS/filesystem behavior |
| Random access vào file lớn | `MappedByteBuffer` theo window hoặc FileChannel positional reads | Page faults, map lifetime, truncation risk |
| Nhiều client chậm | Queue/buffer bounded và backpressure | Memory per connection, high/low watermarks |
| Cross-language library cần `byte[]` | Heap hoặc explicit copy từ direct | Tổng copy và API integration |
| Container memory thấp | Đặt budget cho heap và native; đo trực tiếp | RSS, direct pool metrics, OS kill |

**Không chọn direct chỉ vì từ “performance”.** Khi source/consumer đều làm việc với object/mảng heap, direct có thể tăng số lần copy. Khi I/O native lặp nhiều trên cùng buffer, direct có thể hữu ích. Cần quyết định dựa trên số liệu.

## 17. Benchmark thực tế: tránh kết luận sai

Một benchmark NIO nên đo:

1. **Warmup/JIT**: tránh so sánh code chưa được JIT tối ưu với code đã warm.
2. **Allocation path**: phân biệt tốc độ cấp phát direct/heap với tốc độ read/write khi buffer đã được reuse.
3. **I/O thật**: dùng file sizes, access pattern, storage và concurrency gần sản phẩm.
4. **Cold vs warm page cache**: lần đọc đầu có thể chủ yếu đo storage, lần sau đo cache.
5. **GC + native stats**: throughput tốt nhưng OOME/latency spike thì không phải kết quả tốt.
6. **CPU, RSS và memory bandwidth**: chi phí copy, context switch và page fault có thể thay đổi.
7. **Độ lớn buffer**: thử nhiều kích thước, ví dụ các dải 8 KiB, 64 KiB, 1 MiB trên workload; không xem một kích thước là tối ưu phổ quát.
8. **Kịch bản overload**: client chậm, file lớn, request nhiều, queue đầy, cancellation.
9. **JMH cho microbenchmark CPU** và harness I/O riêng cho benchmark end-to-end; không suy luận từ một vòng đo `System.nanoTime()` đơn lẻ.

Nếu benchmark dùng `ByteArrayOutputStream` làm destination, nó không đo thực tế FileChannel/SocketChannel và không xác nhận lợi ích native I/O của direct buffer. Nếu data hoàn toàn nằm trong page cache, kết quả không thể đại diện cho cold disk.

## 18. Checklist thiết kế và vận hành

- [ ] Đã phân biệt heap limit, direct buffer limit và total container/RSS budget?
- [ ] Đã biết số connection/operations đồng thời và số buffer cần giữ tối đa?
- [ ] Đã xác định mỗi buffer cần capacity bao nhiêu, có size classes và giới hạn queue?
- [ ] Đã có backpressure khi producer nhanh hơn consumer?
- [ ] Đã đảm bảo `flip/clear/compact` theo trạng thái data, không dùng `clear()` để giải phóng memory?
- [ ] Đã rõ view nào cùng chia sẻ backing storage và vòng đời pool lease?
- [ ] Đã không tái sử dụng buffer trước khi async/partial write hoàn tất?
- [ ] Đã có metrics cho `direct`/`mapped` buffer pools và RSS/container memory?
- [ ] Đã tính chi phí mapped pages, thread stacks, metaspace, native libraries, không chỉ heap?
- [ ] Đã kiểm tra lỗi và cleanup cho native allocation, channel close, mapping lifecycle?
- [ ] Đã benchmark workload thật và so sánh heap/direct/mapped/transfer methods?
- [ ] Đã có giới hạn theo tệp lớn, không gom mọi bytes vào một mảng hoặc direct buffer?
- [ ] Đã tránh dùng `System.gc()`/reflection/private Cleaner API như workaround mặc định?
- [ ] Đã xét nguy cơ dữ liệu cũ khi trả buffer cho request/tenant khác và cần zeroization?
- [ ] Đã đặt headroom cho burst/GC/JVM/OS thay vì cộng các giới hạn sát trần?

## 19. Câu hỏi tự kiểm tra

1. `ByteBuffer.allocateDirect(1024)` có cấp backing array `byte[]` trên Java heap không? **Không**, đây là direct buffer với backing memory dùng cho native I/O.
2. `direct.isDirect()` và `direct.hasArray()` thường lần lượt là gì? **true và false**.
3. Heap buffer từ `ByteBuffer.allocate(1024)` thường cho `hasArray()` gì? **true**; nhưng read-only hoặc buffer đặc biệt không nhất thiết có backing array truy cập được.
4. `clear()` trên direct buffer có giải phóng native memory không? **Không**, nó chỉ đặt lại các cursor.
5. Có thể sử dụng direct buffer sau khi method scope kết thúc nếu view vẫn còn tham chiếu không? **Có thể**, vì lifetime phụ thuộc reachability, không phụ thuộc riêng scope lexical.
6. Vì sao `duplicate()` không giúp tách hoàn toàn dữ liệu giữa hai thread? Vì view có cursor riêng nhưng **cùng backing bytes**.
7. Direct buffer có luôn zero-copy với disk không? **Không**; lợi ích tùy đường native I/O, OS và API thực tế.
8. Heap used thấp mà container RSS cao có thể do đâu? Direct/mapped memory, native allocations, thread stacks, code cache và các nhóm khác.
9. `BufferPoolMXBean.getMemoryUsed()` có bao phủ mọi JNI `malloc` không? **Không**; chỉ là thống kê pool tương ứng theo JVM.
10. `-XX:MaxDirectMemorySize` có làm máy có thêm RAM không? **Không**; nó là một giới hạn/cấu hình cấp phát ở JVM triển khai có hỗ trợ.
11. Vì sao pool 256 MiB buffers có thể giữ native memory ngay cả khi không có request? Vì các buffer vẫn reachable qua pool để tái sử dụng.
12. Khi nào mới trả output buffer vào pool trong non-blocking write? **Sau khi mọi byte pending đã được chuyển/operation hoàn tất**, theo ownership protocol.
13. FileChannel đọc tệp 100 GiB có cần một direct buffer 100 GiB không? **Không**, dùng streaming/chunking hoặc mapping theo windows.
14. `MappedByteBuffer` khác `allocateDirect` ở đâu? Nó ánh xạ **vùng file** qua cơ chế memory mapping của OS và có lifecycle/page residency khác.
15. `FileChannel.transferTo` có phải cách khác tên của `allocateDirect` không? **Không**, đây là API truyền bytes giữa channels, có thể dùng tối ưu hóa khác.
16. Nếu hàng đợi output tăng vô hạn, có thể chữa triệt để bằng thêm direct buffers không? **Không**; cần backpressure và bounded queues.
17. Một Java heap dump có đủ để giải thích mọi native leak không? **Không**, cần buffer pool/NMT/OS metrics và phân tích references tương ứng.
18. Có nên benchmark chỉ một lần trên file nhỏ sau JIT rồi kết luận direct tốt hơn heap không? **Không**, phải kiểm tra nhiều workloads, allocation patterns và native memory.
19. Nếu direct buffer được lấy từ pool và một `slice()` vẫn được worker đọc, có an toàn để trả root về pool ngay không? **Không**, alias vẫn tham chiếu cùng backing storage.
20. Thực hành quan trọng nhất với direct memory là gì? **Giới hạn capacity × concurrency × lifetime, phân quyền ownership, đo native memory và chỉ dùng direct khi có lợi ích được xác nhận**.

## Tài liệu API tham khảo

- [Java SE 17 — ByteBuffer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html): `allocate`, `allocateDirect`, `isDirect`, `hasArray`, views và buffer cursor semantics.
- [Java SE 17 — Buffer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/Buffer.html): `position`, `limit`, `capacity`, `flip`, `clear`.
- [Java SE 17 — FileChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileChannel.html): `read/write`, `map`, `transferTo/transferFrom`.
- [Java SE 17 — MappedByteBuffer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/MappedByteBuffer.html): force, mapping-backed memory và cách đồng bộ.
- [Java SE 17 — BufferPoolMXBean](https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/BufferPoolMXBean.html): `getCount`, `getMemoryUsed` và `getTotalCapacity`.
- [Java SE 17 — ManagementFactory](https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/ManagementFactory.html): lấy platform management beans.
- [Oracle Java Troubleshooting Guide — Native Memory Tracking](https://docs.oracle.com/en/java/javase/17/troubleshoot/diagnostic-tools.html): công cụ quan sát native memory của HotSpot.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="performance-and-large-files-006">Giới hạn đọc song song và thứ tự ghép kết quả</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn đọc song song và thứ tự ghép kết quả với Java IO/NIO.2

Khi một tệp lớn cần được đọc và xử lý theo từng khối, ta có thể phân chia công việc cho nhiều luồng: khối đầu tiên đọc từ offset 0, khối tiếp theo từ offset `chunkSize`, v.v. Tuy nhiên, **đọc hoặc xử lý song song** không đồng nghĩa với **kết quả đến theo thứ tự gốc**. Khối ở cuối file có thể hoàn thành trước khối đầu; nếu ghi ngay theo thứ tự hoàn thành, dữ liệu đầu ra sẽ bị đảo chỗ.

Ngoài tính đúng đắn, đọc song song không giới hạn dễ sinh hàng nghìn tác vụ, nhiều buffer chứa dữ liệu và quá nhiều lời gọi IO cùng lúc. Thiết kế tốt phải đồng thời bảo đảm:

1. **Đúng phạm vi đọc:** mỗi khối đọc một khoảng byte xác định, không chồng lấp, không bỏ sót.
2. **Giới hạn tài nguyên:** giới hạn số tác vụ thực thi, số tác vụ đã gửi nhưng chưa tiêu thụ kết quả, lượng dữ liệu đệm và số file/channel mở.
3. **Ghép theo thứ tự:** kết quả được đưa vào đầu ra đúng chỉ số khối/offset, bất kể luồng nào xong trước.
4. **Xử lý lỗi và kết thúc:** lỗi đọc được truyền về phía điều phối; tài nguyên và tác vụ còn lại được dừng, không tiếp tục công bố một file đầu ra chưa hoàn chỉnh.

Bài này tập trung vào cơ chế Java 17+ dùng `FileChannel`, `ExecutorService`, `Future` và hàng đợi kết quả có giới hạn. Các nguyên tắc tương tự áp dụng cho việc tính checksum theo khối, giải mã/biến đổi khối độc lập hoặc ghép những phần đã xử lý.

## 1. Ba thứ tự khác nhau

Giả sử file có 4 khối:

```text
Tệp theo offset:
[0: AAAA] [1: BBBB] [2: CCCC] [3: DDDD]
```

Nếu hai worker cùng hoạt động, lịch chạy có thể là:

```text
Lệnh submit:        0 → 1 → 2 → 3
Lệnh hoàn thành:    1 → 0 → 3 → 2
Thứ tự phải ghi:    0 → 1 → 2 → 3
Kết quả đúng:       AAAABBBBCCCCDDDD
```

- **Submit order:** thứ tự gửi công việc vào executor; hoàn toàn không cam kết thứ tự hoàn thành.
- **Completion order:** thứ tự các tác vụ thực sự xong; phụ thuộc scheduler, tốc độ thiết bị, cache và thời lượng xử lý.
- **Output order:** thứ tự nghiệp vụ cần giữ; với ghép file thường là thứ tự offset tăng dần.

`Future.get()` chờ tác vụ cụ thể hoàn thành. Nếu lưu các future theo thứ tự khối rồi lần lượt `get()` và ghi, đầu ra có thể đúng thứ tự. Nếu dùng `ExecutorCompletionService.take()`, kết quả sẽ đến theo thứ tự hoàn thành, cần một cơ chế sắp/xếp lại trước khi ghép.

**Một sai lầm phổ biến:** `ExecutorService.submit` nhiều lần rồi cho mỗi tác vụ tự ghi vào cùng `OutputStream` bằng `out.write(data)`. Thứ tự ghi lúc đó do lịch chạy quyết định; ngay cả khi từng `write` không bị chen byte, các khối vẫn có thể đảo thứ tự.

## 2. Phân chia file theo vị trí byte

Với file kích thước `fileSize` byte và độ lớn khối `chunkSize > 0`, số khối là:

\[
\text{chunkCount}=\left\lceil\frac{\text{fileSize}}{\text{chunkSize}}\right\rceil.
\]

Khối `i` (bắt đầu từ 0) có:

- `offset = i * chunkSize` (dùng `long` cho vị trí file lớn).
- `length = min(chunkSize, fileSize - offset)`.
- Phạm vi byte là `[offset, offset + length)`, tức chứa đầu, không chứa cuối.

Ví dụ: file 15 byte, `chunkSize = 4` → bốn khối ở các offset 0, 4, 8, 12; độ dài lần lượt 4, 4, 4, 3. File rỗng có 0 khối; không tạo công việc đọc ngoài EOF.

Với `FileChannel.read(ByteBuffer dst, long position)`, `position` là vị trí byte **tuyệt đối**. Đây là API phù hợp để nhiều worker đọc các vùng riêng của cùng một channel mà không phải cạnh tranh trên vị trí đọc tuần tự của channel. Mỗi worker phải dùng **ByteBuffer riêng**, không chia sẻ `position/limit` của một buffer giữa các luồng.

Cần lưu ý:

- Một lời gọi `read` **không bảo đảm lấp đầy buffer**. Luôn lặp cho tới khi đủ byte của khối, gặp EOF, hoặc lỗi.
- `FileChannel.size()` và offset đầu vào phải khớp với **một file ổn định** trong lúc xử lý. Nếu bên khác truncate, append hoặc ghi đè file đang đọc, phép chia khối dựa trên kích thước ban đầu không còn là snapshot nhất quán.
- `FileChannel` có API an toàn để dùng giữa nhiều luồng theo hợp đồng của channel, nhưng việc gọi read song song **không bảo đảm tăng thông lượng**: provider, filesystem, cache, ổ HDD/SSD và tải I/O có thể giới hạn hoặc tuần tự hóa các thao tác.

Không nên chia byte tùy ý rồi gọi `new String(bytes, UTF_8)` cho **từng khối** nếu cần giữ nguyên ký tự: một code point UTF-8 có thể bị cắt giữa hai khối. Đọc/ghép byte trước khi giải mã, hoặc tự thiết kế cách chia tại ranh giới ký tự/dòng và decoder có trạng thái. Ví dụ bên dưới ghép **byte nguyên vẹn**; không biến đổi từng khối như văn bản.

## 3. Hai loại giới hạn song song khác nhau

`Executors.newFixedThreadPool(3)` giới hạn khoảng 3 tác vụ chạy đồng thời, nhưng **không tự giới hạn số lượng đã submit**: executor fixed pool sử dụng hàng đợi công việc có thể tăng rất lớn. Nếu chương trình tạo `Future` cho từng khối của file nhiều terabyte, bộ nhớ vẫn có thể tăng theo số lượng tác vụ dù chỉ có 3 worker đang chạy.

Cần tách hai tham số:

| Giới hạn | Mục đích | Ví dụ |
| --- | --- | --- |
| `WORKERS` | Số worker thực thi task cùng lúc, ảnh hưởng CPU, IOPS và mức cạnh tranh đọc | `2` |
| `MAX_IN_FLIGHT` | Số task đã submit nhưng chưa được lấy/ghi kết quả; giới hạn task queue, future và buffer chờ | `2` |
| `CHUNK_BYTES` | Kích thước bộ nhớ dữ liệu cho mỗi khối; ảnh hưởng số syscall và memory pressure | `4` trong demo, thường lớn hơn trong thực tế |

Với cách ghép byte có giới hạn, lượng byte của các khối đã submit mà còn chờ tiêu thụ xấp xỉ **`MAX_IN_FLIGHT × CHUNK_BYTES`** ở trạng thái xấu nhất, cộng buffer của `OutputStream`, cấu trúc task, buffer hệ thống và các thành phần xử lý khác. Đây là ước lượng theo ứng dụng, không phải giới hạn RSS chính xác của JVM.

Nếu mỗi task tạo thêm kết quả kích thước khác (ví dụ giải nén hoặc tạo nhiều đối tượng), phải ràng buộc **kích thước kết quả** nữa: giới hạn số tác vụ chưa chắc giới hạn tổng bộ nhớ nếu một tác vụ có thể tạo hàng gigabyte.

Cơ chế cấp việc hợp lý là **cửa sổ trượt**: chỉ submit khi số task chưa tiêu thụ nhỏ hơn `MAX_IN_FLIGHT`; nhận một kết quả theo thứ tự, ghi nó xuống output, bỏ dữ liệu của khối đó, rồi mới cấp chỗ cho một task mới. Khi đầu ra chậm, tốc độ đọc bị kéo xuống theo (*backpressure*), thay vì tích lũy vô hạn.

## 4. Ví dụ Java 17+ chạy được: đọc đồng thời, ghi theo offset

Chương trình dưới đây tạo file nguồn nhỏ để stdout xác định, sau đó copy qua nhiều lần đọc có vị trí tuyệt đối. Phương thức `copyOrdered` có thể dùng cùng mẫu cho file lớn; không cần giữ toàn bộ nội dung file trong RAM.

Tạo file `OrderedParallelRead.java`:

```java
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class OrderedParallelRead {
    private static final int CHUNK_BYTES = 4;
    private static final int WORKERS = 2;
    private static final int MAX_IN_FLIGHT = 2;

    private record Block(long index, byte[] bytes) { }

    private static Block readBlock(
            FileChannel channel, long index, long offset, int length)
            throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(length);

        while (buffer.hasRemaining()) {
            long position = offset + buffer.position();
            int n = channel.read(buffer, position);
            if (n < 0) {
                throw new EOFException("File changed or ended early");
            }
            if (n == 0) {
                throw new IOException("No progress reading a file block");
            }
        }
        return new Block(index, buffer.array());
    }

    private static long copyOrdered(Path source, Path destination)
            throws Exception {
        // Tạo stream output bằng CREATE_NEW để không ghi đè file có sẵn.
        try (FileChannel channel =
                     FileChannel.open(source, StandardOpenOption.READ);
             OutputStream out = Files.newOutputStream(
                     destination, StandardOpenOption.CREATE_NEW,
                     StandardOpenOption.WRITE)) {

            ExecutorService executor =
                    Executors.newFixedThreadPool(WORKERS);
            Deque<Future<Block>> inFlight = new ArrayDeque<>();

            long fileSize = channel.size();
            long nextOffset = 0;
            long nextIndex = 0;
            long expectedIndex = 0;

            try {
                while (nextOffset < fileSize || !inFlight.isEmpty()) {

                    // Chỉ gửi thêm tác vụ khi cửa sổ còn chỗ.
                    while (nextOffset < fileSize
                            && inFlight.size() < MAX_IN_FLIGHT) {
                        final long offset = nextOffset;
                        final long index = nextIndex++;
                        final int length = (int) Math.min(
                                (long) CHUNK_BYTES, fileSize - nextOffset);

                        inFlight.addLast(executor.submit(
                                () -> readBlock(
                                        channel, index, offset, length)));
                        nextOffset += length;
                    }

                    // Future đầu hàng đợi là khối có index nhỏ nhất
                    // chưa được ghi. Chờ nó dù khối sau đã chạy xong.
                    Future<Block> oldest = inFlight.removeFirst();
                    Block block;
                    try {
                        block = oldest.get();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }

                    if (block.index() != expectedIndex) {
                        throw new IOException("Unexpected block order");
                    }
                    out.write(block.bytes());
                    expectedIndex++;
                }

                return expectedIndex;
            } finally {
                // Hủy phần chưa dùng nếu có lỗi; đóng pool khi hoàn tất.
                for (Future<Block> future : inFlight) {
                    future.cancel(true);
                }
                executor.shutdownNow();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("ordered-read-");
        Path input = dir.resolve("source.bin");
        Path output = dir.resolve("copy.bin");

        Files.writeString(input, "ABCDEFGHIJKLMNO",
                StandardCharsets.UTF_8);

        long blocks = copyOrdered(input, output);

        System.out.println("blocks=" + blocks);
        System.out.println("output="
                + Files.readString(output, StandardCharsets.UTF_8));
        System.out.println("equal=" + Arrays.equals(
                Files.readAllBytes(input),
                Files.readAllBytes(output)));
    }
}
```

Biên dịch/chạy ở thư mục chứa file bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 OrderedParallelRead.java
java OrderedParallelRead
```

Stdout xác định:

```text
blocks=4
output=ABCDEFGHIJKLMNO
equal=true
```

**Vì sao kết quả đúng dù task hoàn thành đảo thứ tự?**

1. Task số 0 đọc 4 byte từ offset 0; số 1 đọc từ offset 4; tiếp theo là 8 và 12 (độ dài cuối chỉ 3 byte).
2. Mỗi `readBlock` tạo `ByteBuffer` riêng, lặp positional read cho tới khi đủ `length`, sau đó trả `Block(index, bytes)`.
3. `inFlight` lưu future **theo thứ tự submit**, cũng là thứ tự offset; chỉ có tối đa 2 future chưa tiêu thụ.
4. Consumer lấy `removeFirst().get()` và chỉ ghi khối có index đúng `expectedIndex`. Nếu khối 1 xong sớm hơn khối 0, nó nằm chờ; không được viết trước khối 0.
5. Dữ liệu từng khối được ghi xong trước khi mở chỗ nhận công việc tiếp theo; `OutputStream` chỉ có **một thread** viết, tránh tranh chấp vị trí output.
6. Dòng `Arrays.equals` chỉ dùng để chứng minh cho **fixture 15 byte**; với file lớn nên xác nhận bằng checksum dạng streaming, không dùng `Files.readAllBytes` cho cả file.

**Lưu ý về quản lý lỗi:** Ví dụ hủy các future còn trong cửa sổ và gọi `shutdownNow()` khi thoát. `cancel(true)` hoặc `shutdownNow()` chỉ yêu cầu ngắt, không bảo đảm task hoặc I/O đang chặn dừng tức thì. Trong hệ thống có yêu cầu dừng sạch, hãy đợi pool kết thúc bằng `awaitTermination` có timeout, xử lý cờ interrupted và bảo đảm không đóng tài nguyên mà task còn sử dụng. Trên thất bại, file output đã tạo có thể chỉ chứa phần đầu; không công bố file đó như thành phẩm.

Trong demo, hai tham số `CHUNK_BYTES=4` và `MAX_IN_FLIGHT=2` được cố ý chọn nhỏ để nhìn thấy số khối. Với dữ liệu lớn cần đo đạc trước khi chọn kích thước thực tế.

## 5. Vì sao không submit toàn bộ khối cùng lúc?

Một phương án dễ viết nhưng kém khả năng mở rộng:

```java
// Mã minh họa, không phải chương trình độc lập.
List<Future<byte[]>> futures = new ArrayList<>();
for (long offset = 0; offset < size; offset += chunkSize) {
    final long start = offset;
    futures.add(pool.submit(() -> readAt(start)));
}
for (Future<byte[]> f : futures) {
    output.write(f.get());
}
```

Cách này có thể **giữ đúng thứ tự output** vì duyệt future theo thứ tự, nhưng toàn bộ task và future đều được tạo ngay. Nếu không có giới hạn submit, hàng đợi của fixed thread pool, tham chiếu tới các `Future` và kết quả hoàn tất đang chờ có thể tăng theo kích thước file.

Các lỗi khác thường đi kèm: dùng `int` cho offset file lớn rồi overflow; `byte[] data = Files.readAllBytes(bigPath)` vô tình nạp cả file; tăng số luồng rất cao và làm thiết bị IO tranh chấp; ghi nhiều khối vào một `OutputStream` từ nhiều worker mà không có đánh số/điều phối.

Cửa sổ trượt trong ví dụ có thể làm tác vụ sau chờ vì tác vụ đầu tiên chậm (**head-of-line blocking**). Đây là lựa chọn đánh đổi để đầu ra giữ đúng thứ tự và bộ nhớ bị chặn bởi số khối tối đa. Nếu xử lý từng khối có thời gian rất lệch nhau, có thể cân nhắc kích thước khối đồng đều hơn, tách bước xử lý khỏi bước đọc, hoặc cơ chế cấp tín chỉ hợp lý.

## 6. Khi dùng CompletionService và bộ đệm sắp lại

`ExecutorCompletionService` hữu ích nếu muốn biết **task nào xong trước**, ví dụ phục vụ metric, chạy downstream độc lập hoặc giảm thời gian nhàn rỗi. Nhưng `take()` không trả theo chỉ số khối.

Mô hình ghép đúng khi nhận theo thứ tự hoàn thành:

```text
workers → completed Block(index, bytes)
                   ↓
        pendingByIndex (map)
                   ↓
nextExpected=0 → xuất khối 0 nếu sẵn có → tăng nextExpected
                   ↓
        tiếp tục xuất các khối liên tiếp đã có
```

Giữ `Map<Long, Block> pendingByIndex`, thêm kết quả theo `index`, chỉ ghi khi đã có `nextExpected`. Sau khi ghi, lấy các block nối tiếp đang chờ.

**Bẫy về giới hạn tài nguyên:** Nếu cứ nhận một completion rồi submit thêm một task mới, nhưng khối đầu đang chậm, `pendingByIndex` có thể phình ra vô hạn: tất cả các khối sau đã xong nhưng không khối nào được xuất. Phải giới hạn **tổng số task đã gửi nhưng chưa được ghi theo thứ tự** (bao gồm cả đang chạy, queued và đã hoàn thành trong reorder buffer). Chỉ hoàn lại một “slot” sau khi kết quả thực sự được tiêu thụ/ghi xong, không chỉ sau khi một task kết thúc.

Nếu mỗi khối được **ghi vào vị trí offset riêng** bằng `FileChannel.write(buffer, position)`, bước ghép tuần tự về mặt vật lý có thể không cần thiết. Tuy nhiên khi đó phải bảo đảm khoảng ghi không chồng lấp, xử lý partial write, kích thước file đích, lỗi giữa chừng và xác nhận hoàn tất toàn bộ trước khi công bố file. Với định dạng nén/mã hóa có header chung hoặc biến đổi làm thay đổi độ dài mỗi khối, offset đầu ra không còn đơn giản bằng offset đầu vào.

## 7. Đọc theo dòng, ký tự và bản ghi: đừng lấy byte chunk làm ranh giới dữ liệu

Chia tệp byte thành những đoạn `64 KiB` có thể cắt đôi một ký tự UTF-8, một dòng CSV, một bản ghi có độ dài thay đổi hoặc một thông điệp trong log. Nếu worker giải mã độc lập từng đoạn, bạn có thể gặp ký tự thay thế, bản ghi bị chia đôi hoặc kết quả ghép không đúng về ngữ nghĩa.

Các hướng xử lý:

- **Cần bảo toàn file nhị phân:** đọc và ghép đúng byte theo offset; không giải mã từng khối. Đây là yêu cầu/thiết kế của ví dụ.
- **Cần xử lý từng dòng:** dùng một reader tuần tự chịu trách nhiệm phân tách dòng hoàn chỉnh rồi gửi **batch các dòng/bản ghi** cho worker. Đánh số batch, giới hạn queue/in-flight và phát kết quả theo batch index; tránh dùng một `BufferedReader` chung cho nhiều luồng gọi `readLine` không điều phối.
- **Cần chia file text tại offset:** đọc thêm một phần biên để tìm dấu kết thúc bản ghi hợp lệ, quy định rõ ai sở hữu byte của record nằm ở ranh giới; decoder UTF-8 phải xử lý byte multi-byte ở biên. Đừng tự cộng byte mù quáng và bỏ mất dữ liệu trùng.
- **Cần streaming từng record qua mạng:** phải xét backpressure, timeout, retry và semantic ordering của giao thức; “song song nội bộ” không tự có nghĩa là response luôn đúng thứ tự.
- **Cần parallel Stream:** `Files.lines(path)` trả `Stream<String>` và phải được đóng; `parallel()` không thay thế chính sách giới hạn bộ nhớ/IO của bài toán. `forEach` không giữ encounter order, còn `forEachOrdered` có bảo đảm thứ tự phần tử nhưng có thể hạn chế concurrency và không tự bảo đảm bounded buffering cho pipeline tùy biến.

Ngay cả khi dữ liệu là text, đôi khi tách “đọc tuần tự + xử lý CPU song song + ghép có thứ tự” đơn giản và hiệu quả hơn “nhiều luồng cùng seek/read text file”.

## 8. Xử lý lỗi, hủy tác vụ và xuất file nguyên tử

Với file quan trọng, đầu ra cần có một vòng đời rõ ràng:

1. Đọc metadata input đã được phê duyệt; nếu có thể, sử dụng snapshot/file bất biến trong thời gian xử lý. Không để tác vụ khác ghi/truncate cùng file.
2. Tạo **file tạm trong cùng filesystem/thư mục phù hợp**, không ghi trực tiếp vào tên đích được công bố.
3. Chỉ có một coordinator quản lý submit và output order. Mọi worker trả kết quả hoặc lỗi; không âm thầm nuốt exception.
4. Khi `Future.get()` ném `ExecutionException`, nguyên nhân gốc nằm ở `getCause()`. Dừng cấp việc, hủy tác vụ đang chờ và đưa hệ thống vào trạng thái lỗi. Với `InterruptedException`, phục hồi interrupt flag rồi thoát.
5. Chỉ khi tất cả block đã xử lý, `OutputStream` đóng thành công và các bước kiểm tra (ví dụ kích thước/checksum) đạt yêu cầu, mới công bố file tạm bằng move/rename. Nếu cần nguyên tử, kiểm tra hỗ trợ `Files.move(..., ATOMIC_MOVE)`; nền tảng có thể ném `AtomicMoveNotSupportedException`.
6. Nếu có lỗi, xóa file tạm chưa hoàn chỉnh theo chính sách; ghi log offset/index và nguyên nhân nhưng tránh lộ dữ liệu nhạy cảm. Có thể cân nhắc retry **tác vụ chỉ đọc idempotent** với số lần giới hạn khi input bất biến; không retry bừa bãi khi đang ghi side effect.

Ví dụ hoàn chỉnh ở mục 4 cố ý chưa xây protocol công bố file, checksum streaming hay retry. Nó minh họa cơ chế giới hạn và thứ tự; khi dùng trong hệ thống cần lưu trữ bền vững, phải thêm vòng đời output nói trên.

Một chi tiết quan trọng: nếu worker làm biến đổi không idempotent (ví dụ đếm/tăng trạng thái chung, gửi thông báo), retry sẽ có nguy cơ áp dụng tác dụng phụ hai lần. Phải tách phần tính toán thuần của khối khỏi phần commit có thứ tự, hoặc dùng cơ chế idempotency.

## 9. Chọn số luồng và kích thước khối theo đo đạc

Không có con số `WORKERS=8` hoặc `CHUNK_BYTES=1MiB` đúng cho mọi hệ thống.

- Với **HDD**, đọc tuần tự thường tốt hơn nhiều truy cập ngẫu nhiên; đọc song song cùng một ổ có thể tăng seek và làm chậm.
- Với **SSD/NVMe**, vài yêu cầu đọc đồng thời có thể cải thiện throughput, nhưng khi cache đã nóng hoặc giới hạn khác xuất hiện, thêm luồng không giúp.
- Nếu mỗi khối có **xử lý CPU nặng** độc lập, tách pool đọc IO khỏi pool CPU hoặc cân bằng tổng số worker theo profile. Nút thắt có thể là CPU, memory allocation/GC hoặc output disk thay vì input disk.
- Với **nhiều file khác nhau**, đọc song song theo file có thể cho lợi ích và cấu trúc đơn giản hơn so với chia một file thành vô số khối.
- Nếu output là một luồng tuần tự chậm, nó có thể là giới hạn tốc độ tổng thể; tăng số reader chỉ khiến kết quả chờ lâu hơn.
- Dùng `long` cho size/offset; giới hạn mỗi buffer byte[] ở cỡ vừa đủ (`int`), kiểm tra phép nhân/cộng khi tính offset của hàng tỉ block.

Các chỉ số cần đo là throughput (MiB/s), tổng thời gian, p95/p99 latency mỗi block, số task in-flight, tổng byte đang chờ, CPU, GC, số lượng context switch, mức sử dụng thiết bị IO và thời gian coordinator chờ khối đầu. Benchmark với file có kích thước đủ lớn, cache lạnh/nóng rõ ràng, dữ liệu thực tế và cùng điều kiện thử nghiệm. Đừng suy ra “nhiều worker hơn thì luôn nhanh hơn” chỉ từ ví dụ nhỏ.

Tùy nhu cầu, `AsynchronousFileChannel` có thể cung cấp API hoàn thành IO bất đồng bộ, nhưng callback/future vẫn có thể hoàn thành sai thứ tự offset và vẫn phải áp dụng giới hạn in-flight, reorder buffer, xử lý lỗi và close. Nó không xóa bỏ các yêu cầu trên.

## 10. Lỗi thường gặp và hướng sửa

| Triệu chứng | Nguyên nhân khả dĩ | Khắc phục |
| --- | --- | --- |
| Output chứa các khối đúng nhưng bị đảo | Ghi theo completion order hoặc worker cùng ghi `OutputStream` | Đánh số khối và ghép theo offset/sequence; dùng một writer |
| Vùng byte bị lặp hoặc mất | Tính offset/length sai, block cuối không đủ, dùng shared channel position không điều phối | Xác định khoảng `[offset, offset+length)`, đọc đủ byte với positional read |
| Chỉ có 2 worker nhưng heap vẫn tăng | Submit toàn bộ hàng triệu task/future hoặc lưu mọi kết quả | Dùng bounded in-flight window và ghi/giải phóng từng khối |
| File text bị lỗi ký tự ở biên | Giải mã UTF-8 theo chunk byte không căn chỉnh | Decode có trạng thái hoặc tách record trước khi xử lý |
| `Future.get()` treo lâu ở khối đầu | Khối đầu chậm hoặc blocked; head-of-line | Đặt timeout/cancellation policy, theo dõi metric, cân nhắc chiến lược scheduling |
| Kết quả thiếu cuối file | Chỉ đọc một lần rồi tưởng buffer đã đầy, hoặc dùng `int` tràn | Lặp đọc, xử lý EOF và dùng `long` offset |
| Copy đúng với file nhỏ nhưng sai khi nguồn thay đổi | File bị truncate/ghi đè khi đang đọc | Dùng input bất biến/snapshot, kiểm tra lỗi/validation |
| Output file bị bỏ dở khi một task thất bại | Ghi trực tiếp vào file công bố và không cleanup | Ghi temp, commit sau thành công, xử lý fail và cleanup |
| Thêm luồng khiến đọc chậm hơn | IO contention, seek, cache pressure hoặc output là bottleneck | Đo trên thiết bị thật; thử tuần tự và thay đổi worker/chunk có kiểm soát |

## 11. Câu hỏi tự kiểm tra

1. File 10 byte, chunk size 4. Có bao nhiêu khối và độ dài từng khối? **3 khối**, lần lượt 4, 4, 2; offset 0, 4, 8.
2. Fixed thread pool 4 luồng có tự ngăn 1 triệu task được submit không? **Không.** Cần giới hạn in-flight/queue ngoài số luồng.
3. Task số 3 hoàn thành trước task số 2. Có thể nối byte của số 3 vào `OutputStream` ngay không? **Không**, nếu phải khôi phục thứ tự file và chưa có block 2.
4. Vì sao `channel.read(buffer, position)` phải chạy trong vòng lặp? **Một lần đọc có thể trả ít byte hơn yêu cầu.**
5. `CompletionService.take()` có tự trả kết quả theo thứ tự submit không? **Không**, nó trả tác vụ đã hoàn thành; cần đánh số và reorder nếu muốn ghép đúng.
6. Tăng số worker từ 2 lên 32 có chắc tăng throughput file lớn không? **Không.** Giới hạn có thể nằm ở thiết bị IO, cache, CPU, RAM/GC hoặc writer.
7. File văn bản UTF-8 có thể giải mã riêng từng block tùy ý không? **Không**, cần giữ trạng thái giải mã hoặc chia theo ranh giới record/ký tự hợp lệ.

**Tài liệu API chính thức:** Java SE 17 `java.nio.channels.FileChannel` (positional `read`/`write`), `java.nio.ByteBuffer`, `java.util.concurrent.ExecutorService`, `Future`, `ExecutorCompletionService`, `java.nio.file.Files` và `StandardOpenOption`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="performance-and-large-files-007">Đo throughput, latency và mức sử dụng bộ nhớ</a>

<details>
<summary>Xem chi tiết</summary>

# Đo throughput, latency và mức sử dụng bộ nhớ khi xử lý I/O và tệp lớn trong Java

## 1. Vì sao phải đo cả ba nhóm chỉ số?

Tối ưu xử lý tệp lớn không thể dựa vào một con số “chạy nhanh”. Cùng một chương trình có thể đạt throughput cao nhưng làm các yêu cầu đơn lẻ chờ lâu, hoặc đọc rất nhanh vì dùng quá nhiều RAM. Ba nhóm chỉ số cần được theo dõi cùng nhau:

- **Throughput (thông lượng):** lượng dữ liệu hoặc số thao tác hoàn tất trên một đơn vị thời gian; ví dụ `MiB/s`, `MB/s` hay `operations/s`.
- **Latency (độ trễ):** thời gian hoàn tất **một thao tác được xác định rõ**; ví dụ mỗi lần `InputStream.read(...)`, mỗi lần đọc toàn bộ tệp hoặc mỗi yêu cầu tải lên. Cần xem phân phối độ trễ, đặc biệt `p95`/`p99`, không chỉ trung bình.
- **Memory usage (mức dùng bộ nhớ):** bộ nhớ mà chương trình chiếm trong quá trình làm việc, bao gồm Java heap, direct buffer, mapped memory, native memory và bộ nhớ cấp tiến trình; phải phân biệt các phạm vi đo để không kết luận nhầm.

Trong Java I/O, kết quả còn chịu ảnh hưởng của kích thước buffer, trạng thái page cache của hệ điều hành, đặc tính SSD/HDD/network filesystem, số luồng đồng thời, GC và hành vi của thư viện. Bài này tập trung vào **cách đo đúng**, cách đọc kết quả và cách lựa chọn giải pháp phù hợp trong Java 17+.

## 2. Định nghĩa chỉ số trước khi bấm đồng hồ

### 2.1. Throughput: bytes trên thời gian thực

Nếu đã đọc/ghi thành công `B` bytes trong thời gian đo `T` giây:

\[
\text{Throughput (MiB/s)}=\frac{B}{1024^2 \times T}
\]

Ví dụ đọc `512 MiB` trong `4 s` cho throughput trung bình `128 MiB/s`. Nếu cần dùng MB theo hệ thập phân, `1 MB = 1\,000\,000 bytes`; `1 MiB = 1\,048\,576 bytes`. **Ghi rõ đơn vị** khi đối chiếu công cụ đo hoặc cấu hình thiết bị.

Hai dạng throughput có thể cho những câu trả lời khác nhau:

- **Data throughput:** `MiB/s` có ý nghĩa với sao chép, đọc/ghi tệp hoặc truyền dữ liệu lớn.
- **Operation throughput:** `ops/s` có ý nghĩa với nhiều thao tác nhỏ. Một hệ thống `10\,000 ops/s` nhưng mỗi thao tác chỉ 100 bytes chuyển ít dữ liệu hơn một hệ thống có `500 ops/s` nhưng mỗi thao tác 1 MiB.

Khi đo đọc tệp, đếm **số byte thực sự trả về** từ `read`, không lấy kích thước buffer nhân số lần gọi; một lần `read(byte[])` có thể trả số byte nhỏ hơn buffer. Với đọc song song, throughput tổng là **tổng byte hoàn tất của tất cả worker chia cho cùng khoảng thời gian thực**; không cộng các giá trị throughput đo trên những khoảng thời gian khác nhau.

### 2.2. Latency: xác định ranh giới của một thao tác

Latency = thời điểm kết thúc − thời điểm bắt đầu thao tác. Trong I/O có ít nhất ba phạm vi hợp lệ:

| Phạm vi | Cách đo | Diễn giải |
| --- | --- | --- |
| Một lệnh `read(...)` | `nanoTime()` trước và sau lần gọi | Độ trễ của lời gọi API, có thể nhận bytes từ bộ đệm |
| Một lần xử lý tệp | Bắt đầu từ mở file đến đóng/đọc xong | Độ trễ end-to-end của công việc đó |
| Một yêu cầu hệ thống | Từ lúc yêu cầu đến lúc trả kết quả | Bao gồm hàng đợi, parse, xử lý nghiệp vụ, I/O và lập lịch |

Không lấy p95 của từng `read` rồi gọi đó là “p95 xử lý tệp”; hàng trăm thao tác `read` có thể tạo thành **một** lần xử lý tệp.

Các percentile thường dùng:

- `p50` (median): ít nhất khoảng 50% số mẫu có latency không lớn hơn giá trị này.
- `p95`: khoảng 95% số mẫu có latency không lớn hơn giá trị này.
- `p99`: khoảng 99% số mẫu có latency không lớn hơn giá trị này.

Để percentile đáng tin, cần số lượng mẫu đủ lớn và các mẫu có cùng định nghĩa thao tác. Với vài mẫu, `p99` gần như chỉ phản ánh giá trị cực đại. Nên báo thêm kích thước mẫu, số lần lặp, trung bình/trung vị, min/max khi có ích và khoảng biến thiên giữa các lượt đo.

### 2.3. Dùng `System.nanoTime()` để đo elapsed time

`System.nanoTime()` được thiết kế để lấy chênh lệch thời gian trong cùng quá trình JVM, không phải timestamp gắn với thời gian lịch. Ví dụ:

~~~java
long start = System.nanoTime();
long transferred = doRead(); // Đoạn minh họa: doRead() là hàm do ứng dụng cung cấp.
long elapsedNanos = System.nanoTime() - start;

double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
double mibPerSecond = (transferred / (1024.0 * 1024.0))
        / elapsedSeconds;
~~~

Đây là **đoạn mã minh họa** bên trong một phương thức, không phải chương trình hoàn chỉnh: `doRead()` phải được triển khai và trả về số byte thực đọc. Trong mã thực tế, kiểm tra trường hợp thời gian đo quá nhỏ, đo nhiều lượt và không tối ưu dựa vào một phép đo nhiễu. Không dùng `System.currentTimeMillis()` để benchmark đoạn code ngắn: đồng hồ lịch có thể bị điều chỉnh và độ phân giải thường không phù hợp.

## 3. Thí nghiệm Java 17+: đo throughput, p50/p95/p99 và heap snapshot

Ví dụ sau:

1. Tạo một **tệp thử 32 MiB** trước khi bắt đầu đo.
2. Đọc tệp bằng `BufferedInputStream` và buffer ứng dụng 64 KiB.
3. Chạy 2 lượt warm-up, **không đưa kết quả warm-up vào thống kê**.
4. Chạy 5 lượt đo, cộng số byte và thời gian để tính data throughput.
5. Ghi latency của từng `read(byte[])`, sắp xếp và tính `p50/p95/p99`.
6. Lấy hai ảnh chụp Java heap trước/sau pha đo, ghi rõ chúng **không phải đỉnh heap**.

Tạo file `IoMetricsDemo.java`:

~~~java
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class IoMetricsDemo {
    private static final int BUFFER_BYTES = 64 * 1024;
    private static final long FILE_BYTES = 32L * 1024 * 1024;
    private static final int WARMUP = 2;
    private static final int MEASURED = 5;

    private record Trial(long bytes, long elapsedNanos, long readCalls) {}

    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("io-metrics-", ".bin");
        try {
            prepareFile(file);

            for (int i = 0; i < WARMUP; i++) {
                readOnce(file, null);
            }

            long heapBefore = usedHeapBytes();
            long totalBytes = 0;
            long totalNanos = 0;
            long totalCalls = 0;
            List<Long> callLatencies = new ArrayList<>();

            for (int i = 0; i < MEASURED; i++) {
                Trial trial = readOnce(file, callLatencies);
                totalBytes += trial.bytes();
                totalNanos += trial.elapsedNanos();
                totalCalls += trial.readCalls();
            }
            long heapAfter = usedHeapBytes();

            callLatencies.sort(Long::compareTo);
            double seconds = totalNanos / 1_000_000_000.0;
            double mib = totalBytes / (1024.0 * 1024.0);

            System.out.println("File bytes: " + Files.size(file));
            System.out.println("Measured passes: " + MEASURED);
            System.out.println("Total read bytes: " + totalBytes);
            System.out.println("Read calls: " + totalCalls);
            System.out.printf(Locale.ROOT,
                    "Throughput: %.2f MiB/s%n", mib / seconds);
            System.out.printf(Locale.ROOT,
                    "read() p50/p95/p99: %.3f / %.3f / %.3f ms%n",
                    percentile(callLatencies, 0.50) / 1_000_000.0,
                    percentile(callLatencies, 0.95) / 1_000_000.0,
                    percentile(callLatencies, 0.99) / 1_000_000.0);
            System.out.println("Heap before (bytes): " + heapBefore);
            System.out.println("Heap after  (bytes): " + heapAfter);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static void prepareFile(Path file) throws IOException {
        byte[] block = new byte[BUFFER_BYTES];
        Arrays.fill(block, (byte) 0x5A);
        try (OutputStream out = Files.newOutputStream(file,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            long written = 0;
            while (written < FILE_BYTES) {
                int n = (int) Math.min(block.length, FILE_BYTES - written);
                out.write(block, 0, n);
                written += n;
            }
        }
    }

    private static Trial readOnce(Path file, List<Long> samples)
            throws IOException {
        byte[] buffer = new byte[BUFFER_BYTES];
        long bytes = 0;
        long calls = 0;
        long start = System.nanoTime();

        try (InputStream in = new BufferedInputStream(
                Files.newInputStream(file), BUFFER_BYTES)) {
            while (true) {
                long before = System.nanoTime();
                int n = in.read(buffer);
                long after = System.nanoTime();
                if (n == -1) {
                    break;
                }
                if (n == 0) {
                    continue;
                }
                bytes += n;
                calls++;
                if (samples != null) {
                    samples.add(after - before);
                }
            }
        }

        return new Trial(bytes, System.nanoTime() - start, calls);
    }

    private static long percentile(List<Long> sorted, double fraction) {
        int index = (int) Math.ceil(fraction * sorted.size()) - 1;
        return sorted.get(Math.max(0, index));
    }

    private static long usedHeapBytes() {
        return ManagementFactory.getMemoryMXBean()
                .getHeapMemoryUsage().getUsed();
    }
}
~~~

Lệnh chạy với JDK 17+:

~~~bash
javac IoMetricsDemo.java
java IoMetricsDemo
~~~

**Ba dòng đầu là cố định** khi chạy hoàn tất (kích thước được chọn trong code):

~~~text
File bytes: 33554432
Measured passes: 5
Total read bytes: 167772160
~~~

Các dòng `Read calls`, `Throughput`, `read() p50/p95/p99` và `Heap before/after` đều **phụ thuộc máy, JVM, OS, trạng thái cache và tải hệ thống**; không có một stdout số học cố định cho chúng. Những dòng này luôn dùng nhãn và đơn vị đúng như trong mã, còn giá trị cần lấy từ lần chạy thật.

### 3.1. Vì sao code đo theo cách này?

- **Tạo dữ liệu trước khi đo:** không tính thời gian ghi file test vào throughput đọc.
- **Warm-up:** giảm ảnh hưởng của việc nạp class, JIT compilation và phần nào trạng thái chưa ổn định. Hai lượt chỉ đủ cho ví dụ học tập, chưa đủ để khẳng định benchmark đại diện.
- **Đếm byte thật:** biến `bytes` tăng theo `n` mà `read` trả về, không mặc định lần nào cũng nhận 64 KiB.
- **Tính throughput từ tổng byte / tổng elapsed:** cộng thời gian các lượt đo, tránh lấy trung bình số học đơn giản của những throughput vốn có thời lượng khác nhau.
- **Latency theo từng call:** danh sách `callLatencies` đo những lời gọi `read` thành công, không ghi riêng lần `read` cuối trả `-1`. Percentile dùng quy tắc *nearest-rank* với chỉ số bắt đầu từ 0.
- **Hai loại clock window:** latency chỉ bao phần thực thi lời gọi `read`; throughput tính cả mở/đóng stream, duyệt vòng lặp và lưu sample trong mỗi lần đọc. Hai chỉ số có phạm vi khác nhau nên không kỳ vọng lấy tổng latency các call để ra chính xác elapsed.
- **Memory snapshots:** `MemoryMXBean` trả `heap.used` tại hai thời điểm; giá trị có thể tăng hoặc giảm do GC và các cấp phát khác. Chênh lệch `after - before` **không phải** tổng bytes đã cấp phát và cũng **không phải** peak heap.

### 3.2. Giới hạn quan trọng của chính ví dụ

Bài demo vừa đo latency, vừa ghi từng sample vào `ArrayList<Long>` nên có **observer overhead**: boxing `Long`, lời gọi `nanoTime()`, lưu sample và GC có thể làm throughput chậm hơn. Với phép đo nghiêm túc, chạy **một lượt đo throughput ít instrumentation** và **một lượt đo latency có sampling**, rồi so sánh kết quả. Có thể thay danh sách boxing bằng `long[]` được cấp phát đủ trước khi đo, nhưng vẫn còn overhead từ đồng hồ.

Ngoài ra, 5 lượt đọc cùng một file 32 MiB có khả năng chạy chủ yếu từ **OS page cache**, không phải throughput truy cập vật lý lạnh của SSD/HDD. Vì vậy kết quả phản ánh **cấu hình và workload cụ thể của ví dụ**, không phải thông số công bố của ổ đĩa.

## 4. Đo độ trễ end-to-end và tình huống ghi file

Đối với yêu cầu “đọc xong một file rồi trả dữ liệu”, nên đo một window bao toàn bộ đường đi tương ứng:

~~~java
long startedAt = System.nanoTime();
long count = 0;
try (InputStream in = Files.newInputStream(path)) {
    byte[] chunk = new byte[64 * 1024];
    int n;
    while ((n = in.read(chunk)) != -1) {
        count += n;
        // Nếu nghiệp vụ có parse/transform, đặt ở đây để đo chung end-to-end.
    }
}
long elapsed = System.nanoTime() - startedAt;
~~~

Đây là **đoạn mã đặt trong một phương thức** có biến `Path path` và xử lý `IOException`; không phải lớp standalone. `elapsed` bao gồm thao tác mở, đọc đến hết, đóng tệp và xử lý trong vòng lặp.

Đo **ghi** lại có nhiều tầng hoàn thành khác nhau:

1. `OutputStream.write(...)` trả về: bytes đã được giao cho các tầng buffer/provider tương ứng; chưa đồng nghĩa dữ liệu nằm bền vững trên thiết bị.
2. `flush()` của stream có buffer: đẩy dữ liệu khỏi buffer của ứng dụng xuống tầng tiếp theo; chưa tương đương cam kết durable trên ổ đĩa.
3. `FileChannel.force(true)` hoặc `FileDescriptor.sync()`: yêu cầu các thay đổi thích hợp được đẩy xuống thiết bị lưu trữ theo hợp đồng API/hệ điều hành, với caveat về thiết bị và hệ thống tệp.

Nếu benchmark tuyên bố đo **durable write latency**, phải bao cả bước `force`/`sync` trong ranh giới latency. Nếu chỉ đo throughput của `write` vào bộ đệm mà đem so với giải pháp khác có `fsync` ở mỗi giao dịch, kết luận sẽ không công bằng.

Ví dụ **minh họa cách đặt ranh giới** khi dùng `FileChannel`:

~~~java
long start = System.nanoTime();
try (FileChannel channel = FileChannel.open(path,
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.TRUNCATE_EXISTING)) {
    ByteBuffer data = ByteBuffer.wrap(payload);
    while (data.hasRemaining()) {
        channel.write(data);
    }
    channel.force(true);
}
long durableWriteNanos = System.nanoTime() - start;
~~~

Các biến `path` (`Path`) và `payload` (`byte[]`) được truyền từ mã gọi. Cần import `java.nio.ByteBuffer`, `java.nio.channels.FileChannel` và `java.nio.file.StandardOpenOption`. Đây là đoạn minh họa trong phương thức có xử lý `IOException`. `channel.write` có thể ghi ít hơn `ByteBuffer.remaining()`, do đó cần vòng `while (data.hasRemaining())`.

## 5. Đo bộ nhớ: Java heap không đại diện cho toàn bộ RAM

### 5.1. Những phạm vi bộ nhớ phải phân biệt

| Chỉ số | Ý nghĩa | Cách quan sát điển hình |
| --- | --- | --- |
| `heap.used` | Số bytes đang dùng trong Java heap theo cơ chế đo của JVM | `MemoryMXBean`, JFR, GC logs |
| `heap.committed` | Bộ nhớ JVM đã cam kết cho heap | `MemoryUsage.getCommitted()` |
| `heap.max` | Giới hạn heap, có thể là giá trị “không xác định” tùy pool/JVM | `MemoryUsage.getMax()`, tùy cấu hình |
| Direct buffer pool | Bộ nhớ liên quan `ByteBuffer.allocateDirect` được quản lý bởi pool | `BufferPoolMXBean` với tên `direct` |
| Mapped buffer pool | Số lượng/kích thước mapped buffer được theo dõi | `BufferPoolMXBean` với tên `mapped` / pool hiện hữu |
| Native memory khác | Thread stacks, metadata, code cache, vùng native của JVM/thư viện | Native Memory Tracking/JFR/OS tùy nguồn |
| RSS / working set | Trang bộ nhớ vật lý đang resident của tiến trình theo OS | Công cụ giám sát tiến trình của OS |
| OS page cache | Trang file cache được OS giữ, có thể phục vụ I/O mà không đọc đĩa | Thống kê hệ điều hành, không phải heap Java |

**Không cộng bừa** `heap.used + direct pool + RSS`: RSS đã bao gồm một số phần của heap/native resident, nên phép cộng có thể đếm trùng. `heap.committed` là khái niệm khác với số trang vật lý thực tế đang resident. File-backed mapping cũng có quan hệ riêng với page cache.

### 5.2. Hai chiến lược xử lý tệp lớn có dấu chân bộ nhớ khác nhau

**Đọc toàn bộ file:**

~~~java
byte[] all = Files.readAllBytes(path);
process(all); // process là hàm của ứng dụng; đoạn này không standalone.
~~~

`readAllBytes` yêu cầu một mảng chứa nội dung file trong heap, có thể phát sinh thêm mảng tạm/chi phí JVM tùy hiện thực. Với file rất lớn, có thể gặp `OutOfMemoryError` hoặc giới hạn kích thước mảng Java. Thích hợp khi file nhỏ, nằm trong giới hạn bộ nhớ và thực sự cần giữ toàn bộ nội dung.

**Đọc streaming theo chunk:**

~~~java
try (InputStream in = Files.newInputStream(path)) {
    byte[] chunk = new byte[64 * 1024];
    int n;
    while ((n = in.read(chunk)) != -1) {
        process(chunk, n); // Ứng dụng xử lý ngay n bytes hợp lệ.
    }
}
~~~

Hai đoạn trên là **mã minh họa** đặt trong phương thức có biến `Path path` và hàm `process` phù hợp, không phải chương trình độc lập. Streaming có thể giữ working set heap gần với kích thước buffer nếu `process` không tích lũy dữ liệu. Nếu `process` giữ tất cả chunk vào `List` hoặc tạo `String` lớn thì **streaming không còn bảo đảm footprint thấp**.

### 5.3. Theo dõi direct/mapped buffer bằng `BufferPoolMXBean`

Chương trình độc lập `BufferPoolDemo.java`:

~~~java
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;

public class BufferPoolDemo {
    public static void main(String[] args) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(8 * 1024 * 1024);
        System.out.println("Direct: " + buffer.isDirect());

        for (BufferPoolMXBean pool :
                ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
            if (pool.getName().equals("direct")
                    || pool.getName().equals("mapped")) {
                System.out.printf(
                        "%s: count=%d, used=%d, capacity=%d%n",
                        pool.getName(),
                        pool.getCount(),
                        pool.getMemoryUsed(),
                        pool.getTotalCapacity());
            }
        }
    }
}
~~~

~~~bash
javac BufferPoolDemo.java
java BufferPoolDemo
~~~

Dòng đầu cố định:

~~~text
Direct: true
~~~

Các dòng còn lại ghi số buffer, số byte bộ nhớ ước tính pool quản lý và tổng capacity. **Số cụ thể thay đổi giữa các JDK/môi trường và lúc đo**; không nên coi chúng là toàn bộ native memory. `BufferPoolMXBean` còn có thể trả giá trị không xác định cho vài thuộc tính/tình huống; tên các pool khả dụng phụ thuộc JVM. Việc giải phóng direct buffer thường phụ thuộc cơ chế quản lý vòng đời và GC; không giả định rằng một object local hết scope làm native memory lập tức được trả lại.

### 5.4. Cách đo memory peak đúng hơn

Hai snapshot trước/sau như trong `IoMetricsDemo` có thể **bỏ sót đỉnh dùng bộ nhớ ở giữa**. Với ứng dụng dài hạn hoặc áp lực cấp phát lớn:

- Ghi nhiều mẫu `heap.used` trong suốt giai đoạn chạy, kèm timestamp và mức tải; đỉnh trong chuỗi mẫu chỉ là **sampled peak**, không phải tuyệt đối nếu có spike giữa hai lần lấy mẫu.
- Dùng **JDK Flight Recorder (JFR)** hoặc công cụ profiling để quan sát allocation, heap, GC pause và tiến trình chạy. Allocation rate (bytes cấp phát mỗi giây) khác với heap giữ lại.
- Dùng `jcmd <pid> GC.heap_info` hoặc JVM/JMX metrics tùy runtime để xem trạng thái heap tại thời điểm gọi.
- Nếu cần hiểu phần native bên trong JVM, bật **Native Memory Tracking** từ lúc khởi động (`-XX:NativeMemoryTracking=summary`) rồi dùng `jcmd <pid> VM.native_memory summary`; NMT có overhead, không đại diện toàn bộ bộ nhớ bên ngoài JVM mà thư viện native tùy ý quản lý.
- Dùng công cụ OS cho **RSS/working set**, page cache, I/O bytes và page faults; không suy toàn bộ RAM tiến trình từ riêng `-Xmx`.

Các lệnh `jcmd` cần chạy với process/JDK phù hợp và quyền truy cập tiến trình. Nếu không bật NMT khi khởi động, lệnh summary sẽ không cung cấp cùng thông tin chi tiết như phiên đã bật.

## 6. Tránh benchmark sai vì cache, warm-up và cấu hình đo

### 6.1. Cold cache và warm cache

Đọc cùng một file nhiều lần có thể chuyển từ disk-backed I/O sang đọc từ **OS page cache**. Kết quả warm-cache vẫn hữu ích nếu workload sản xuất thật có đọc lặp, nhưng đừng dùng nó đại diện cho tốc độ đọc file mới từ thiết bị. “Đóng stream” hoặc `FileChannel.force(...)` không đồng nghĩa xóa page cache.

Thực hành tốt là báo rõ:

- File mới hay file lặp lại, kích thước file so với RAM, có đọc lại cùng offset hay không.
- Thiết bị và filesystem cục bộ/network, giới hạn cgroup/container và tải nền.
- Kết quả lần chạy đầu so với các lần tiếp theo; nếu cần đo cold cache, sử dụng **phương pháp kiểm soát cache phù hợp với hệ điều hành/thử nghiệm**, chỉ khi có quyền và khi không ảnh hưởng hệ thống dùng chung.
- Đọc tuần tự hay random; access pattern có thể thay đổi throughput rất mạnh do read-ahead và seek.

### 6.2. Warm-up và JIT/GC

JVM có thời gian nạp lớp và biên dịch tối ưu; GC và scheduler có thể tạo spike. Nên chạy warm-up riêng, nhiều lượt đo độc lập, báo median/tail giữa các lượt và giữ cấu hình JVM nhất quán. Không gọi `System.gc()` liên tục để “ổn định” benchmark: không bảo đảm thực hiện như mong muốn và làm méo điều kiện thực.

Đối với microbenchmark CPU hoặc API cực ngắn, dùng **JMH** với fork/warm-up/measurement được cấu hình rõ ràng. Với I/O thật, JMH vẫn không thể tự kiểm soát ổ đĩa, filesystem cache, network, durable write hay phân phối request; phải thiết kế workload và tầng hạ tầng tương ứng. Đừng để bộ đo gây cấp phát nhiều đến mức đo GC thay cho I/O.

### 6.3. Giữ workload công bằng

Khi so `Files.readAllBytes`, `BufferedInputStream`, `FileChannel` hay `MappedByteBuffer`:

1. Đảm bảo cùng dữ liệu, cùng kích thước tổng, cùng điều kiện cache và bộ đo.
2. Phân biệt read-only, write-only, read+parse, copy, checksum, durable write.
3. Có kiểm tra **đúng dữ liệu** hoặc byte count, tránh benchmark xử lý thiếu/tự bỏ công việc.
4. Kiểm soát buffer size, số thread, số thao tác đồng thời và chính sách mở/đóng.
5. Chạy nhiều lần theo thứ tự hoán đổi phù hợp; nếu luôn đo A trước B, lợi thế cache/JIT có thể thuộc về B.
6. Giám sát CPU, GC, I/O wait, page faults, heap, direct memory và RSS song song để giải thích throughput/latency.

Không có API “nhanh nhất” trong mọi workload. `Files.readAllBytes` có thể nhanh và thuận tiện với file nhỏ nhưng tăng peak heap; `FileChannel` và streaming thường hợp lý cho file lớn; memory mapping hữu ích ở một số kiểu random access nhưng cần tính đến page faults, lifetime mapping và virtual address space.

## 7. Kết hợp throughput, latency và memory để chẩn đoán

| Quan sát | Giả thuyết có thể kiểm tra | Phép đo tiếp theo |
| --- | --- | --- |
| Throughput cao, `p99` tăng mạnh | Hàng đợi, GC, cạnh tranh thread/lock, jitter I/O | p99 ở request-level, queue wait, GC pause, I/O latency |
| Throughput không tăng khi tăng concurrency | Thiết bị đã bão hòa, contention, scheduling overhead | Disk utilization, CPU, context switching, queue depth |
| Throughput tăng nhưng RSS tăng nhanh | Direct/mapped buffers, page cache, native allocation | Buffer pools, NMT, RSS, heap snapshots |
| Heap tăng tuyến tính theo kích thước file | `readAllBytes`, tích lũy record/byte arrays, giữ reference | Heap allocations và retained objects |
| Heap ổn định nhưng tiến trình vẫn dùng nhiều RAM | Direct memory/native/threads/mapping | BufferPoolMXBean, NMT, RSS |
| Lượt chạy đầu chậm, lượt sau nhanh | Page cache hoặc warm-up | So cold/warm, quan sát disk read bytes |
| `write` nhanh nhưng dữ liệu chưa durable | Đo trước `flush/force/sync` | Đo thời gian hoàn thành sau force/sync |
| `p50` ổn nhưng `p99` rất xấu | Tail bị ảnh hưởng bởi số ít yêu cầu chậm | Histogram đủ mẫu, trace mẫu chậm nhất, đo từng giai đoạn |

Các hàng chỉ là **giả thuyết cần xác minh**; một triệu chứng có thể có nhiều nguyên nhân. Ví dụ một thao tác chậm do network filesystem có thể khiến throughput suy giảm nhưng heap không đổi. Đặt các metrics lên cùng timeline thường hiệu quả hơn đọc một ảnh chụp rời rạc.

## 8. Cách lập báo cáo benchmark có thể tái lập

Ví dụ cấu trúc báo cáo (đây là **mẫu điền kết quả**, không phải số benchmark thực tế):

| Mục | Nội dung cần ghi |
| --- | --- |
| Mục tiêu | Đọc tuần tự tệp lớn, hoặc xử lý một request tải lên |
| Phạm vi latency | Một lần `read` / một file / một request |
| Data size và pattern | Kích thước tệp, chunk size, tuần tự/random, nén hay không |
| Runtime | JDK version, heap flags, GC, số worker |
| Máy và I/O | CPU, RAM, ổ đĩa/network filesystem, OS, page cache |
| Phương pháp | Số lượt warm-up, số lượt đo, số mẫu, cách tính percentile |
| Throughput | MiB/s và tổng bytes/thời gian |
| Latency | p50/p95/p99, đơn vị ms hoặc µs, rõ cấp đo |
| Memory | heap used/committed/peak sampled, direct pool, RSS, allocation rate |
| Độ tin cậy | Độ biến thiên, điều kiện cache, giới hạn và observer overhead |

Khi tối ưu, cần giữ lại **baseline trước thay đổi**, sau đó chạy cùng workload với phiên bản mới. Hãy ghi cả tác động trade-off: tăng buffer từ 8 KiB lên 256 KiB có thể tăng throughput trên một máy nhưng làm tốn thêm RAM cho mỗi kết nối đồng thời, hoặc giảm tốc độ do GC/cache pressure ở quy mô khác.

## 9. Câu hỏi tự kiểm tra

**Câu 1.** Chương trình đọc 200 MiB trong 2,5 giây. Throughput là bao nhiêu?

**Đáp án:** `200 / 2.5 = 80 MiB/s`. Đơn vị là MiB nếu đầu vào tính theo MiB; chuyển sang MB/s cần đổi đơn vị trước khi tính.

**Câu 2.** Có thể lấy p95 của từng lệnh `read` để gọi là p95 “thời gian upload request” không?

**Đáp án:** Không. Request latency có thể gồm hàng đợi, nhiều lần read/write, xử lý và phản hồi. Phải đo từ đầu đến cuối yêu cầu nếu đó là SLA cần đánh giá.

**Câu 3.** Hai snapshot heap trước và sau đều 70 MiB. Có thể kết luận peak heap chỉ 70 MiB?

**Đáp án:** Không. Có thể đã cấp phát rất nhiều và GC thu gom giữa hai ảnh chụp, hoặc có spike ở thời điểm không lấy mẫu; cần lấy mẫu xuyên suốt/JFR/profiler. Direct memory và RSS còn nằm ngoài `heap.used`.

**Câu 4.** Vì sao đọc 5 lượt file 32 MiB có thể cho throughput cao hơn đọc lần đầu tệp mới?

**Đáp án:** OS page cache, readahead, warm-up JVM và nhiều yếu tố khác. Khi buffer file đã resident trong RAM, kết quả không còn đo thuần tốc độ ổ đĩa lạnh.

**Câu 5.** Vì sao `flush()` trên buffered stream chưa đủ để báo “ghi dữ liệu bền vững”?

**Đáp án:** `flush` chủ yếu đẩy dữ liệu khỏi buffer của tầng stream xuống tầng dưới. Durability có thể cần `FileChannel.force` hoặc `FileDescriptor.sync`, tính đến giới hạn thiết bị/OS và yêu cầu nghiệp vụ.

**Câu 6.** Nếu throughput tăng 20% nhưng p99 yêu cầu tăng gấp ba và RSS vượt giới hạn container, giải pháp đã tốt hơn chưa?

**Đáp án:** Chưa thể kết luận. Phải đánh giá đồng thời SLA latency, budget bộ nhớ, tải mục tiêu và chi phí vận hành. Nếu yêu cầu về latency/RAM bị vi phạm thì throughput cao hơn không đồng nghĩa tối ưu thành công.

## 10. Tóm lược thực hành

1. **Nói rõ đang đo thao tác gì và theo đơn vị nào.** `MiB/s`, `ops/s`, `p95 read()` hay `p95 request` có nghĩa khác nhau.
2. **Chỉ đếm công việc đã hoàn tất và dữ liệu đã thực sự truyền.** Dùng `System.nanoTime()` cho elapsed, đếm bytes theo kết quả `read`/`write` phù hợp.
3. **Đo đồng thời nhưng hiểu nguồn đo:** heap Java, direct/mapped, native và RSS là các lát cắt khác nhau; snapshot không phải peak.
4. **Tách workload và nhiễu:** khâu chuẩn bị tệp, cache, warm-up, JIT, GC, instrumentation và durability đều ảnh hưởng đến số đo.
5. **So sánh có kiểm soát, nhiều lượt, có tail percentile và điều kiện môi trường.** Kết quả đáng tin là kết quả người khác có thể tái lập và giải thích.

Với tệp lớn, cách tối ưu đáng tin nhất thường bắt đầu bằng benchmark theo workload thật, xác định nút nghẽn, rồi chỉ thay buffer/API/concurrency khi có số đo chứng minh thay đổi đáp ứng đồng thời mục tiêu tốc độ, độ trễ và bộ nhớ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="performance-and-large-files-008">Ảnh hưởng của cache hệ điều hành khi benchmark</a>

<details>
<summary>Xem chi tiết</summary>

# Ảnh hưởng của cache hệ điều hành khi benchmark Java I/O

Khi đo hiệu năng đọc/ghi tệp lớn bằng Java, kết quả có thể chênh lệch nhiều lần dù **không thay đổi một dòng code**. Nguyên nhân thường không nằm riêng ở `FileInputStream`, `BufferedInputStream`, `FileChannel` hay `MappedByteBuffer`, mà còn ở **page cache của hệ điều hành**, readahead, cơ chế write-back, cache của thiết bị lưu trữ, hoạt động của các tiến trình khác và trạng thái bộ nhớ tại thời điểm đo.

Ví dụ, đọc một tệp 2 GiB từ SSD ở lần đầu có thể phụ thuộc lượng dữ liệu phải thực sự tải từ thiết bị. Nếu dữ liệu đã nằm trong RAM của hệ điều hành, lượt đọc sau chủ yếu truyền dữ liệu từ page cache về ứng dụng. So sánh hai lượt này rồi tuyên bố “API A nhanh hơn API B” là sai nếu chúng không được đo trong **điều kiện cache tương đương**.

Bài học này tập trung vào cách cache hệ điều hành làm sai lệch benchmark, phân biệt **cold-cache** và **warm-cache**, các đặc điểm của đo đọc/ghi, cách thiết kế thử nghiệm Java I/O và cách diễn giải kết quả mà không nhầm tốc độ truy cập RAM với thông lượng thiết bị.

## 1. Page cache nằm ở đâu trong đường đi I/O?

Trên hệ điều hành thông thường, hoạt động đọc một tệp nằm trên hệ thống tệp có thể đi qua các tầng:

```text
Java application
   |
   | FileInputStream / FileChannel.read / Files.read...
   v
Kernel filesystem interface
   |
   v
OS page cache (RAM dùng cache các trang dữ liệu tệp)
   |                         \
   | cache hit                 \ cache miss
   |                            v
   |                      File system + block I/O
   |                            |
   |                            v
   |                      Storage device (SSD/HDD)
   |                            |
   +<---------------------------+
   |
   v
ByteBuffer / byte[] / ứng dụng
```

Đây là mô hình **khái niệm**, không phải đường đi cố định của mọi hệ thống tệp, OS hoặc provider. Hệ điều hành có thể cache metadata, thực hiện readahead, copy hoặc map trang, gom nhiều yêu cầu I/O; thiết bị SSD/HDD cũng có cache riêng.

**Page cache** là RAM mà hệ điều hành tận dụng để lưu những trang dữ liệu của file. Khi một thao tác đọc cần những trang đã có trong cache (*cache hit*), kernel có thể phục vụ mà không phải đọc lại toàn bộ dữ liệu từ thiết bị lưu trữ. Khi chưa có trang phù hợp (*cache miss*), hệ thống phải lấy dữ liệu từ các tầng dưới, thường chậm hơn và biến động hơn.

Hai khái niệm cần phân biệt:

- **Cache của ứng dụng Java:** `byte[]`, `ByteBuffer`, `BufferedInputStream`, cache tự xây dựng hoặc cache của thư viện. Dữ liệu sống theo quy tắc của JVM/ứng dụng.
- **Cache của hệ điều hành:** thường do kernel quản lý và chia sẻ giữa các tiến trình. Đóng `FileChannel` hoặc tạo lại đối tượng Java không có nghĩa kernel đã bỏ các trang tệp tương ứng khỏi cache.

Đọc xong một file rồi đóng stream, mở một stream mới tới cùng file vẫn có thể **đọc từ cache kernel**. Vì thế việc “tạo `FileChannel` mới cho mỗi lượt benchmark” không phải cách bảo đảm mọi lượt bắt đầu trong trạng thái cold.

## 2. Cold cache và warm cache là hai bài toán khác nhau

| Tình huống | Ý nghĩa thực tế | Đo được điều gì? |
| --- | --- | --- |
| **Cold-ish read** | Phần lớn dữ liệu chưa nằm trong page cache cần thiết | Đọc có thể chịu chi phí thiết bị, metadata và readahead |
| **Warm read** | Phần lớn dữ liệu đã có trong page cache | Chi phí kernel/cache, sao chép dữ liệu, xử lý Java, băng thông bộ nhớ |
| **Mixed read** | Một số trang cache hit, một số phải tải lại | Hỗn hợp có thể khác nhau giữa từng lượt |
| **Working set lớn hơn RAM cache khả dụng** | Có thể phát sinh eviction/cache thrashing | Bị chi phối bởi luồng I/O, lượng RAM và tác vụ cạnh tranh |
| **Nhiều tiến trình đọc cùng tệp** | Một tiến trình làm cache nóng cho tiến trình khác | Phụ thuộc hoạt động hệ thống chung, không chỉ một JVM |

Từ “cold” cần định nghĩa **theo phạm vi dữ liệu và trạng thái hệ thống**. Một file chưa từng được mở bởi chính chương trình chưa chắc cold: antivirus, trình index, tiến trình khác hoặc lệnh tạo file trước đó có thể đã đưa các trang của nó vào cache.

Ngược lại, “warm” không có nghĩa mọi byte luôn nằm trong RAM. Kernel có thể thu hồi trang bất cứ lúc nào để cấp RAM cho công việc khác. Nếu file lớn hoặc máy đang chịu memory pressure, lần đọc thứ hai vẫn có thể gặp nhiều cache miss.

**Không được lấy một phép đo cold và một phép đo warm làm hai mẫu tương đương để so sánh thư viện hoặc API.** Nếu sản phẩm đọc cùng file nhiều lần, warm-cache performance có thể là chỉ tiêu đúng. Nếu sản phẩm quét tệp mới sau khi khởi động hoặc đọc các tập dữ liệu vượt RAM, cần kiểm tra cold/mixed-cache và thông lượng lưu trữ thực tế.

## 3. Vì sao lần đọc đầu tiên chưa chắc là cold?

Một quy trình nghe có vẻ hợp lý:

```java
Files.write(file, testData);
long first = timeRead(file);
long second = timeRead(file);
```

nhưng lại không tạo ra kết quả **cold versus warm** đúng nghĩa. Khi `Files.write()` vừa ghi `testData`, nhiều hệ điều hành giữ các trang đã ghi trong page cache. Ngay cả khi `first` là lần đầu ứng dụng **đọc**, dữ liệu có thể **đã warm do thao tác ghi**.

Các lý do khác khiến lần đọc đầu không cold:

1. File đã được tạo, copy hoặc kiểm tra checksum ngay trước benchmark.
2. Một tiến trình khác vừa đọc file, đưa dữ liệu vào cache chung.
3. OS có cơ chế readahead: trong một lần đọc tuần tự, các trang **ở đoạn sau** có thể được tải sớm trước khi code gọi `read()` tới vị trí đó.
4. Filesystem cache metadata/inode/dentry hoặc cache của storage device làm ảnh hưởng thời gian ngay cả khi data page chưa warm.
5. Benchmark framework đã chạy một số vòng warmup, vô tình “làm nóng” dữ liệu cần dùng cho bài đo cold.
6. Một container hoặc JVM mới dùng chung kernel với các workload trước đó; khởi động container mới **không đồng nghĩa xóa cache kernel**.

Do đó, kết quả đo ban đầu chỉ nên được gọi là **first measured pass**, trừ khi có bằng chứng rằng working set bắt đầu ở điều kiện cache đã được kiểm soát.

## 4. BufferedInputStream không phải page cache

`BufferedInputStream` giữ một bộ đệm ở phía Java, giúp giảm số lần gọi xuống `InputStream` bên dưới khi ứng dụng đọc từng byte hoặc các đoạn nhỏ. `FileChannel.read(ByteBuffer)` có thể đọc một khối lớn trong một lời gọi. Nhưng **cả hai vẫn thường được phục vụ qua page cache của kernel** khi đọc file trên filesystem mặc định.

Ví dụ:

```java
try (InputStream input = new BufferedInputStream(
        Files.newInputStream(file), 64 * 1024)) {
    int value;
    while ((value = input.read()) != -1) {
        consume(value); // Hàm nghiệp vụ minh họa.
    }
}
```

Đoạn này **không phải chương trình độc lập**: cần khai báo `file` và hàm `consume`, import `java.io.*`, `java.nio.file.*` cùng xử lý `IOException`. Nó minh họa buffer tầng ứng dụng, không chứng minh rằng kernel sẽ không cache file.

Thêm một tầng buffer **không luôn làm chương trình nhanh hơn**. Nếu ứng dụng vốn đã đọc mảng `byte[65536]` theo khối, bọc thêm `BufferedInputStream` có thể không mang lợi ích tương xứng. Nếu ứng dụng gọi `read()` từng byte, buffer có thể giảm overhead. Để kết luận cần đo với **kích thước buffer, trạng thái page cache, workload và thuật toán xử lý tương đương**.

### Direct ByteBuffer không tự bypass cache hệ điều hành

`ByteBuffer.allocateDirect()` tạo một buffer trực tiếp ở vùng bộ nhớ ngoài heap; nó có thể giảm chi phí copy giữa một số đường đi I/O native và JVM. Tuy nhiên **direct buffer không đồng nghĩa với `O_DIRECT` của Linux**, không bảo đảm bỏ qua OS page cache và cũng không bảo đảm “zero-copy” trong mọi tình huống.

So sánh heap `ByteBuffer` và direct `ByteBuffer` cần tách rõ chi phí cấp phát, reuse, số lần copy, mức cache hit và chi phí đọc trên thiết bị. Nếu không kiểm soát page cache, kết luận “direct nhanh hơn ổ đĩa” dễ là phép đo về cache/bộ nhớ thay vì thiết bị.

## 5. Sequential read, random read và read-ahead

Hệ điều hành thường tối ưu việc đọc tuần tự bằng **readahead**: khi phát hiện mẫu truy cập phù hợp, kernel có thể đọc trước những block chưa được yêu cầu. Vì thế hai benchmark đọc cùng dung lượng nhưng thứ tự khác nhau có thể cho kết quả rất khác:

- **Sequential scan:** Đi từ đầu tới cuối file, kích thước khối tương đối lớn, locality cao. Readahead có thể giúp che giấu độ trễ thiết bị.
- **Random access:** Nhảy giữa các offset phân bố rộng, locality thấp. Có thể làm giảm hiệu quả readahead và tạo nhiều I/O nhỏ.
- **Repeated hot small region:** Liên tục đọc vài MiB đầu của một file vài GiB. Dữ liệu có thể hoàn toàn nằm trong RAM ngay cả khi tổng file lớn hơn RAM.
- **Many-file metadata walk:** Đọc thuộc tính và mở hàng nghìn tệp nhỏ. Cache metadata, đường dẫn và số syscall có thể quan trọng hơn throughput dữ liệu.
- **Mixed read/write:** Workload thật có ghi, đọc lại, cạnh tranh RAM và background writeback; không thể suy ra trực tiếp từ một sequential read-only benchmark.

Nếu muốn kiểm tra hành vi I/O cho file lớn, hãy định nghĩa **working set** là các byte thực sự được chạm tới trong cửa sổ đo; không chỉ nói file trên đĩa dài bao nhiêu. Một file 100 GiB mà bài đo chỉ chạm 4 MiB có working set truy cập khác hoàn toàn với quét đủ 100 GiB.

Khi đọc ngẫu nhiên, cũng cần cố định seed/nguyên tắc sinh offset để hai chiến lược đọc gặp cùng khối dữ liệu. Nếu mỗi lần chạy dùng offset tùy ý khác nhau, các mức cache hit không thể so trực tiếp.

## 6. Write-back cache: ghi “xong” không đồng nghĩa dữ liệu đã bền vững

Khi `FileChannel.write()` hoặc `Files.write()` trả về thành công, nhiều hệ thống tệp đã chấp nhận dữ liệu vào các buffer/cache của hệ điều hành. Điều đó **không chứng minh các byte đã tới bộ nhớ lưu trữ bền vững**. Kernel có thể **write-back** các trang đã thay đổi (*dirty pages*) xuống thiết bị ở thời điểm sau.

Vì vậy một benchmark ghi đơn giản có thể đo chủ yếu tốc độ copy byte vào page cache, rồi kết thúc trước khi kernel hoàn tất ghi thiết bị. Nếu báo kết quả đó là “SSD write throughput” sẽ dễ phóng đại.

| Kiểu đo | Điều đang đo | Điều chưa chắc đo |
| --- | --- | --- |
| `write()` đến khi hết buffer | Thời gian ứng dụng được OS chấp nhận byte | Dữ liệu đã ổn định trên thiết bị |
| `Files.write(...)` rồi đóng stream | Hoàn thành đường ghi ở mức API và close | Độ bền qua mất điện theo mọi filesystem |
| `FileChannel.force(false)` | Yêu cầu đồng bộ nội dung (và metadata cần để lấy lại nội dung) theo hợp đồng API | Không trực tiếp báo cache đã bị xóa |
| `FileChannel.force(true)` | Yêu cầu đồng bộ nội dung và metadata liên quan | Không tự làm file cold cho lần đọc sau |
| `StandardOpenOption.SYNC/DSYNC` | Thay chính sách đồng bộ lúc mở file để ghi | Không miễn phí về latency hoặc tương đương trong mọi filesystem |

`FileChannel.force(true)` là yêu cầu **đẩy cập nhật về thiết bị theo phạm vi cam kết của API**, không phải API xóa page cache. Trên các loại storage/provider khác nhau, bảo đảm thực tế và chi phí có thể khác nhau. Việc force không hứa đồng bộ mọi thay đổi thư mục, rename hoặc thiết bị trong mọi tình huống mất điện.

Để benchmark **durable writes**, hãy mô tả rõ có tính thời gian của `force()`/close, có dùng `SYNC/DSYNC`, file đã tồn tại hay mới tạo, số byte và cách xử lý lỗi hay không. Để benchmark **write acceptance** của API, có thể không force nhưng phải gọi đúng tên chỉ số đó.

## 7. MappedByteBuffer và file memory mapping cũng chịu ảnh hưởng cache

`FileChannel.map()` có thể ánh xạ vùng tệp vào không gian địa chỉ của tiến trình; đọc từ `MappedByteBuffer` có thể dẫn tới **page fault** khi trang tương ứng chưa sẵn có. Những truy cập sau có thể sử dụng các trang đã được ánh xạ/cached.

Những cách đo sai thường thấy:

1. Map file ở ngoài timer, sau đó đo đọc trang trong timer cho chiến lược memory-mapped; nhưng với `FileChannel.read()` lại đo cả thời gian mở file và cấp phát buffer.
2. Một bài benchmark gọi `mapped.load()` trước khi đo, bài kia không warmup dữ liệu tương đương.
3. Đo một lượt mapping cold, lượt mapping khác warm mà không báo state.
4. Xem `MappedByteBuffer.isLoaded()` là công cụ xác nhận chính xác rằng **mọi trang luôn nằm trong RAM**. API chỉ mang tính chỉ báo; hệ điều hành có thể thay đổi trạng thái sau kiểm tra.
5. Gọi `mapped.force()` và cho rằng map đã bị loại khỏi cache. `force()` của mapped buffer là thao tác hướng về **đồng bộ thay đổi**, không phải eviction.

Mapping có các đặc điểm riêng: page faults, độ hạt trang, truy cập ngẫu nhiên và chi phí quản lý vùng ánh xạ. Benchmark cần đo cả thời điểm mapping nếu workload thực tế trả giá đó và so sánh trong tình trạng cache ngang nhau.

**Mapping không phải zero-cost I/O.** Với file khổng lồ, còn cần cân nhắc kích thước vùng map, giới hạn địa chỉ, bộ nhớ khả dụng, vòng đời mapping và việc file bị thay đổi/thu ngắn trong lúc đang truy cập.

## 8. Ví dụ Java 17+ hoàn chỉnh: đo nhiều lượt đọc nhưng không gọi lượt đầu là cold

Ví dụ sau:

- Tạo tệp tạm **8 MiB** chứa dữ liệu byte xác định (không đụng tới tệp người dùng).
- Ghi bằng `FileChannel`, gọi `force(true)` để minh họa khác biệt giữa sync nội dung và cache eviction.
- Đọc tuần tự cùng tệp **bốn lần**, mỗi lần mở `FileChannel` mới và đọc qua một `ByteBuffer` 64 KiB.
- Đo thời gian bằng `System.nanoTime()`, tính throughput theo MiB/s và in lên **stderr** vì số đo phụ thuộc máy.
- Kiểm tra tổng byte và checksum của các lượt đọc rồi in kết quả **cố định trên stdout**.

**Đây là demo quan sát warm-up/cache, không phải phép thử cold-cache có kiểm soát và không phải bằng chứng API I/O nào nhanh hơn.**

Tạo file **`OsCacheBenchmarkDemo.java`**:

```java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class OsCacheBenchmarkDemo {
    private static final int BLOCK = 64 * 1024;
    private static final int BLOCKS = 128; // 8 MiB.
    private static final int PASSES = 4;

    private OsCacheBenchmarkDemo() {}

    private record Result(long bytes, long checksum, long elapsedNs) {}

    public static void main(String[] args) throws IOException {
        Path path = Files.createTempFile("os-cache-benchmark-", ".bin");
        try {
            generate(path);

            Result[] results = new Result[PASSES];
            for (int i = 0; i < PASSES; i++) {
                results[i] = readAndMeasure(path);
                double seconds = results[i].elapsedNs() / 1_000_000_000.0;
                double mibPerSecond =
                        (results[i].bytes() / (1024.0 * 1024.0))
                        / Math.max(seconds, 1e-9);
                System.err.printf(
                        "pass=%d elapsed=%.3f ms throughput=%.1f MiB/s%n",
                        i + 1, results[i].elapsedNs() / 1_000_000.0,
                        mibPerSecond);
            }

            boolean sameBytes = true;
            boolean sameChecksum = true;
            for (Result result : results) {
                sameBytes &= result.bytes() == BLOCKS * (long) BLOCK;
                sameChecksum &=
                        result.checksum() == results[0].checksum();
            }

            System.out.println("file bytes=" + Files.size(path));
            System.out.println("all passes have full size=" + sameBytes);
            System.out.println("all passes have same checksum="
                    + sameChecksum);
        } finally {
            Files.deleteIfExists(path);
        }
    }

    private static void generate(Path path) throws IOException {
        byte[] bytes = new byte[BLOCK];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i & 0xff);
        }

        try (FileChannel out = FileChannel.open(path,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer source = ByteBuffer.wrap(bytes);
            for (int block = 0; block < BLOCKS; block++) {
                source.rewind();
                while (source.hasRemaining()) {
                    if (out.write(source) <= 0) {
                        throw new IOException("No write progress");
                    }
                }
            }
            out.force(true); // Sync request, KHÔNG evict page cache.
        }
    }

    private static Result readAndMeasure(Path path) throws IOException {
        ByteBuffer data = ByteBuffer.allocate(BLOCK);
        try (FileChannel in = FileChannel.open(
                path, StandardOpenOption.READ)) {
            long bytes = 0;
            long checksum = 0;
            long started = System.nanoTime();

            while (true) {
                data.clear();
                int n = in.read(data);
                if (n < 0) {
                    break;
                }
                if (n == 0) {
                    throw new IOException("No read progress");
                }

                bytes += n;
                data.flip();
                while (data.hasRemaining()) {
                    checksum += data.get() & 0xff;
                }
            }

            return new Result(
                    bytes, checksum, System.nanoTime() - started);
        }
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac OsCacheBenchmarkDemo.java
java OsCacheBenchmarkDemo
```

**Stdout** khi tạo và đọc tệp thành công:

```text
file bytes=8388608
all passes have full size=true
all passes have same checksum=true
```

**Stderr** sẽ có bốn dòng `pass=1` đến `pass=4`, mỗi dòng chứa milliseconds và throughput tính từ số đo thực tế; **không có giá trị thời gian hoặc MiB/s cố định**. Khi chạy từ terminal, stderr có thể hiển thị xen kẽ stdout.

### Demo chứng minh điều gì và không chứng minh điều gì?

1. `Files.createTempFile()` cấp tệp mới; `generate()` ghi đúng 128 block × 65.536 byte = 8.388.608 byte. Dữ liệu mỗi block có pattern từ 0 đến 255.
2. `out.force(true)` yêu cầu đồng bộ trước khi đo đọc; tuy nhiên dữ liệu vừa ghi **có thể vẫn ở page cache**. Lượt `pass=1` **không được gọi là cold**.
3. Mỗi `readAndMeasure()` mở channel mới nhưng không yêu cầu OS loại các trang vừa đọc khỏi cache. Vì vậy nhiều lượt có thể được phục vụ từ RAM.
4. Vòng `read()` đọc khối tối đa 64 KiB; checksum khiến nội dung thực sự được tiêu thụ, tránh một phép đo chỉ mở file rồi đóng.
5. Thời gian bao gồm `FileChannel.read()`, các thao tác `ByteBuffer` và phép cộng checksum, nhưng **không bao gồm** khởi tạo buffer, mở/đóng channel hay thời gian tạo tệp. Như vậy nó **không đo riêng throughput thiết bị**.
6. Các dòng stdout xác minh mọi pass đọc đủ file và cùng checksum. Dữ liệu đọc giống nhau không cho biết mỗi pass hit page cache bao nhiêu phần trăm.

Nếu muốn so sánh các API khác, phải áp dụng cùng cách tạo dữ liệu, trạng thái cache, lượng byte, mức xác minh, phần timer được bao gồm và số lần lặp. Đo thời gian dài hơn, báo median/p95/phân phối và quan sát disk I/O sẽ hữu ích hơn một lượt đo 8 MiB.

## 9. Thiết kế benchmark công bằng với trạng thái cache

### Bước 1: Xác định câu hỏi muốn trả lời

Hai mục tiêu thường khác nhau:

**A. Người dùng thực tế đọc đi đọc lại một tập file đang nằm trong cache:** Hãy đo warm-cache workload vì nó phản ánh thực tế. Không cần cố gắng loại page cache nếu dịch vụ sản xuất luôn tận dụng cache.

**B. Hệ thống phải xử lý nhiều file mới/lớn, vượt bộ nhớ và gây disk I/O:** Hãy xây dựng dữ liệu và chu kỳ truy cập sao cho đo được cold/mixed cache tương ứng, giám sát các chỉ số storage bên ngoài JVM và báo rõ các giới hạn tái lập.

Không nên đặt tiêu đề “đo hiệu năng ổ đĩa” khi bài đo chỉ lặp đọc cùng một buffer nhỏ từ RAM.

### Bước 2: Kiểm soát working set

Ghi lại:

- Tổng kích thước tập dữ liệu cần chạm tới (không chỉ tên/kích thước một file).
- RAM vật lý, RAM sẵn có, memory pressure và cache capacity trong thời gian đo.
- Kiểu tệp và filesystem: local SSD/HDD, network share, tmpfs, filesystem ảo; nếu đo trên **tmpfs/RAM disk** thì dữ liệu vốn ở RAM, không thể suy diễn về SSD/HDD.
- Sequential hay random, kích thước block, số luồng, mức song song, khoảng cách giữa các pass.
- Có file nào vừa được tạo/copy/ghi/kiểm checksum làm warm cache trước khi đo không.
- Có workload khác sử dụng RAM hoặc disk cùng lúc không.

Một tập dữ liệu **lớn hơn RAM** làm khả năng hit page cache giảm trong nhiều tình huống, nhưng không bảo đảm hoàn toàn cold. OS vẫn có thể cache các vùng được chạm lặp lại, cache metadata hoặc tối ưu hóa đường đi truy cập. Không mô tả quy tắc “file > RAM ⇒ chắc chắn không có cache” như một định luật.

### Bước 3: Warm-up đúng mục đích

Đối với benchmark warm-cache:

1. Chạy đủ lượt ban đầu để JIT/JVM, class loading và dữ liệu I/O có cơ hội ổn định.
2. Tách số đo warm-up khỏi các số đo chính.
3. Chạy nhiều lượt chính, tính phân phối, kiểm tra checksum/số byte.
4. Nếu so sánh A/B, thay đổi thứ tự thử nghiệm (ví dụ ABBA hoặc randomized) nhằm hạn chế việc luôn cho B hưởng cache của A.

Đối với **cold-cache**: warm-up Java code có thể cần thiết, nhưng không được dùng warm-up đọc chính tập file cần giữ cold cho lần đo. Có thể warm JIT bằng một tập dữ liệu khác tương đương mà không chạm vào tập đo chính. Ngay cả vậy, cache dùng chung và hoạt động bên ngoài JVM vẫn phải được quan sát.

### Bước 4: Phân biệt phép đo Java và thiết bị lưu trữ

Bên cạnh `System.nanoTime()`, hãy thu thập số liệu cấp OS phù hợp:

| Nhóm chỉ số | Vì sao hữu ích |
| --- | --- |
| Tổng byte ứng dụng đọc/ghi | Xác minh khối lượng công việc tương đương |
| Disk I/O bytes thực tế, số request, queue và latency | Phân biệt throughput thiết bị với phục vụ từ page cache |
| Page faults / major faults (khi liên quan) | Gợi ý hành vi memory mapping và truy cập trang |
| CPU user/system, context switches | Thấy benchmark bị giới hạn bởi Java parsing/copy/syscalls |
| Page cache/reclaim/memory pressure | Giải thích vì sao các pass có mức cache hit khác nhau |
| Dirty pages và writeback activity | Phân biệt ghi vào RAM với ghi ra thiết bị |
| Per-process và toàn hệ thống I/O | Tránh quy nhầm disk I/O của tiến trình khác cho benchmark |

Các số liệu này không phải lúc nào cũng trực tiếp ánh xạ từng page cache hit của Java sang một con số đơn giản. Trên Linux có thể sử dụng công cụ giám sát hệ thống như `iostat`, `pidstat`, `vmstat`, `perf` (nếu sẵn có và có quyền); trên Windows/macOS sử dụng công cụ đo tương ứng. Hãy ghi rõ công cụ, phiên bản OS và cách thu thập.

## 10. Có thể “xóa page cache” để đo cold bằng Java không?

**Java chuẩn không cung cấp một API portable để xóa page cache của hệ điều hành cho một file hoặc toàn máy.** `FileChannel.close()`, `FileChannel.force()`, `Files.delete()`, `MappedByteBuffer.force()` hay `ByteBuffer.allocateDirect()` không tương đương thao tác xóa cache kernel.

Một số hệ điều hành có công cụ/hint native để điều chỉnh page cache, nhưng chúng:

- Khác nhau giữa Linux, Windows, macOS và các filesystem.
- Có thể yêu cầu quyền quản trị hoặc hỗ trợ ở cấp kernel/filesystem.
- Có thể tác động tới toàn hệ thống, ảnh hưởng tiến trình khác, làm thay đổi điều kiện đo và gây gián đoạn không mong muốn.
- Không đảm bảo trạng thái cold tuyệt đối vì còn cache của thiết bị, metadata, readahead hoặc workload khác.

**Không dùng thao tác thô bạo xóa page cache toàn máy trên môi trường dùng chung hoặc production chỉ để tạo con số benchmark.** Nếu cần thử nghiệm cold có tính kiểm soát, sử dụng máy thử nghiệm riêng, dữ liệu/thiết bị được quản lý, quy trình thiết lập và giám sát được ghi chép; thao tác native phải do người có trách nhiệm hệ thống phê duyệt.

Một phương án giảm cache reuse là thử nghiệm với một tập file đủ lớn, được đọc theo mẫu đã thiết kế, đổi thứ tự dữ liệu và quan sát disk I/O thực. Dù vậy hãy gọi đây là **cache-mitigated/mixed-cache test** nếu chưa xác nhận điều kiện cold.

## 11. Những sai lệch benchmark Java thường đi cùng page cache

Page cache không phải tác nhân duy nhất. Khi số liệu thay đổi mạnh, kiểm tra thêm:

| Sai lệch | Ví dụ | Cách giảm |
| --- | --- | --- |
| **JIT warmup** | Lần đầu có class loading/JIT compilation | Chạy warm-up code tách khỏi tập dữ liệu cold nếu cần |
| **GC/heap pressure** | Dùng `Files.readAllBytes()` làm tệp rất lớn thành array | Chọn khối đọc phù hợp, ghi nhận GC/heap và giữ bài test công bằng |
| **Buffer allocation** | A cấp phát `allocateDirect()` trong timer, B reuse heap buffer ngoài timer | Định nghĩa có/không tính cấp phát giống nhau |
| **Checksum/parsing** | Mỗi byte được xử lý bằng CPU, che mất khác biệt I/O | Đo cả tổng workload và đường đọc tối giản trong những phép thử riêng |
| **JVM/OS background load** | Antivirus, indexing, cron backup, build/test khác | Cách ly thời gian thử, ghi nhận nhiễu |
| **Thermal/power behavior** | CPU/SSD throttling, hiệu năng laptop theo chế độ pin | Ghi nhận power state, nhiệt độ và điều kiện ổn định |
| **File allocation/compression** | Sparse file, compression filesystem, deduplication, zero-filled pattern | Dùng dữ liệu phản ánh thực tế; ghi đặc điểm filesystem |
| **Disk cache/device cache** | Lần đầu đi qua cache của bộ điều khiển SSD | Không xem page cache là tầng cache duy nhất |
| **Small sample** | Đọc 100 KiB và suy tốc độ GiB/s | Tăng khối lượng đo, báo variance và sai số |
| **Measurement scope** | A tính `open()`, B không tính | Đặt timer cùng ranh giới |
| **Contention** | Nhiều benchmark thread cùng đọc file | Kiểm soát concurrency và băng thông chung |
| **Trộn state A/B** | Chạy A trước để warm file rồi B sau | Đảo thứ tự hoặc đưa cả hai vào các state tương đương |

Với microbenchmark thao tác I/O hoặc buffer, có thể dùng **JMH** để quản lý warm-up/measurement/fork và tránh nhiều lỗi liên quan JIT. Tuy nhiên **JMH không tự reset page cache hay biến benchmark storage thành cold-cache test**; cần cấu hình môi trường/working set ngoài harness.

### Đừng dùng tốc độ vượt ổ đĩa làm bằng chứng “benchmark lỗi”

Nếu workload hoàn toàn warm, thông lượng quan sát được có thể vượt nhiều lần tốc độ đọc danh nghĩa của SSD vì phần lớn byte đang đến từ RAM. Điều đó **có thể đúng đối với warm-cache benchmark**. Lỗi là diễn giải kết quả thành băng thông vật lý của SSD hoặc dùng nó làm dự đoán cho ứng dụng thường xuyên đọc cold data.

### Throughput và latency không thay thế nhau

Thông lượng có thể tính theo `MiB/s = bytes / (1024×1024) / seconds`. Nhưng còn cần latency per operation và percentile khi workload gồm nhiều request nhỏ. Một hệ thống có throughput cao khi đọc file tuần tự vẫn có thể có p99 rất tệ do major page fault, queue dài hoặc writeback gây contention.

## 12. Ví dụ thiết kế phép thử A/B có chú giải

Giả sử muốn so sánh `Files.newInputStream()` + byte array với `FileChannel.read(ByteBuffer)`:

**Không công bằng:**

```text
A: đọc file X ngay sau khi máy khởi động
B: đọc lại file X sau A
Kết luận: B luôn nhanh hơn A.
```

A gặp nhiều miss hơn B và có thể gánh thêm khởi tạo JIT. Dữ liệu không chứng minh API B tốt hơn.

**Thiết kế hợp lý hơn:**

1. Mô tả câu hỏi: tốc độ đọc tuần tự **warm-cache** hay cold/mixed-cache? Chỉ công bố kết quả cho một điều kiện được kiểm soát.
2. Chọn cùng tệp, cùng byte range, cùng block size hợp lý và cùng checksum/processing.
3. Làm nóng code Java và dữ liệu theo mục tiêu; nếu là cold/mixed, thiết kế bộ dataset riêng và quy trình kiểm soát bên ngoài JVM.
4. Chạy nhiều lượt với thứ tự hoán đổi, ví dụ A→B→B→A, không để một API luôn chạy trước.
5. Ghi bytes, duration, throughput, CPU, disk I/O, GC và điều kiện test; báo median và phân phối thay vì chỉ tốc độ tốt nhất.
6. Kiểm tra cache state có biến động giữa các lượt, cân nhắc bỏ những lượt bị nhiễu và công bố tiêu chí loại bỏ mẫu trước khi chạy.
7. Khi triển khai thực tế dùng nhiều client, cần benchmark thêm sự cạnh tranh, file sizes và concurrency giống sản phẩm.

Đừng thay đổi cả API, kích thước buffer, cách đọc random/sequential, số luồng và thời điểm flush trong một thử nghiệm rồi quy toàn bộ thay đổi vào API.

## 13. Những lỗi phổ biến và cách sửa

| Ngộ nhận / lỗi | Hậu quả | Cách làm đúng |
| --- | --- | --- |
| Gọi lần đọc đầu tiên là cold vì đó là lần đọc đầu của Java | File có thể đã warm do thao tác tạo/copy | Mô tả first measured pass, kiểm soát dữ liệu và đo disk I/O |
| Đóng `FileChannel` rồi tưởng xóa cache | Lượt đọc sau vẫn được kernel phục vụ từ RAM | Phân biệt vòng đời channel và page cache |
| Gọi `force(true)` rồi tin lần đọc tiếp theo là cold | Force không có ngữ nghĩa eviction | Chỉ dùng force khi muốn đo sync/durability |
| Dùng `allocateDirect()` để bỏ page cache | Direct buffer không tương đương O_DIRECT | Đo copy/alloc riêng, nêu cache path |
| Chạy A trước, B sau trên cùng file | B thường hưởng warm-cache từ A | Đảo thứ tự hoặc tách cache state |
| Chỉ tính thời gian `write()` rồi công bố disk write throughput bền vững | Đo chủ yếu write-back buffering | Công bố write acceptance hoặc tính `force()` khi phù hợp |
| Tạo file test ngay trước “cold read” | Dữ liệu có thể được giữ trong page cache | Có cách thiết lập cold riêng và xác nhận bằng chỉ số |
| Đọc vài MiB rồi suy cho file nhiều GiB | Working set nhỏ có thể nằm trong RAM | Đo theo working set của sản phẩm |
| Dùng `MappedByteBuffer.load()` chỉ cho một chiến lược | Ưu đãi warm-up không công bằng | Giữ phạm vi warm-up tương đương |
| Dùng JMH và giả định cache được reset ở mỗi fork | Fork JVM không reset kernel cache | Kiểm soát cache ngoài JMH |
| Không đọc hết/không xác minh nội dung | Hai phương án không xử lý cùng khối lượng | Đếm bytes, dùng checksum hoặc blackhole hợp lý |
| Dùng file nén/sparse/toàn zero không giống sản phẩm | Tốc độ file system/storage bị bóp méo | Sinh workload dữ liệu tương tự thực tế |
| Chỉ công bố một số MiB/s đẹp nhất | Không biết variance, cold/warm hay nhiễu | Báo phân phối và cấu hình môi trường |
| Thử xóa page cache toàn host đang hoạt động | Ảnh hưởng workload khác và làm biến dạng phép đo | Dùng môi trường thử riêng, quy trình có phê duyệt |

## 14. Checklist báo cáo benchmark có thể tái lập

Một bản báo cáo đọc/ghi file đáng tin cậy nên trả lời:

1. **Mục tiêu:** Đọc tuần tự, ngẫu nhiên, metadata hay durable write? Đang kiểm tra API hay thiết bị?
2. **Bộ dữ liệu:** Tổng byte thật sự đọc/ghi, số file, kích thước mỗi file, mức entropy/compression, file vừa tạo hay đã tồn tại.
3. **Cache condition:** Warm, cold được xác nhận hay mixed/unknown? Trạng thái được chuẩn bị và xác minh bằng cách nào?
4. **Môi trường:** OS/kernel, JVM/vendor/version, CPU, RAM, filesystem, storage device, container/VM hay máy vật lý.
5. **Cấu hình Java:** API, heap/direct buffer, kích thước khối, số luồng, GC/heap size, cách mở/đóng file.
6. **Timer:** Bao gồm open, allocation, read/write, parsing/checksum, force, close và cleanup hay không?
7. **Phương pháp:** Số warm-up pass, số measured pass, cách thay đổi thứ tự A/B, seed random access.
8. **Chỉ số:** Bytes, MiB/s, latency, median/p95, GC/CPU, disk I/O, bộ nhớ và mức biến động.
9. **Độ chính xác:** Kiểm tra checksum/đếm byte, xử lý EOF, partial read/write, lỗi IOException.
10. **Giới hạn:** Các giả định chưa kiểm soát, cache thiết bị hoặc tác vụ nền có thể làm kết quả biến động.
11. **An toàn thử nghiệm:** Không xóa cache toàn máy sản xuất; không chạy benchmark phá dữ liệu thật.
12. **Diễn giải:** Kết luận giới hạn theo workload và cache state đã đo, không khái quát thành tốc độ tuyệt đối của “Java I/O”.

**Ghi nhớ:** Page cache của hệ điều hành có thể biến phép thử đọc tệp thành phép đo truy cập RAM, còn write-back có thể làm phép thử ghi kết thúc trước khi dữ liệu nằm bền vững trên thiết bị. **Đóng file, gọi `force()`, khởi động JVM/container mới hoặc dùng direct `ByteBuffer` không tự xóa page cache**. Benchmark đúng cần định nghĩa mục tiêu cold/warm, kiểm soát working set, đo nhiều lượt công bằng, xác minh khối lượng byte và đối chiếu với số liệu I/O cấp hệ điều hành trước khi kết luận về hiệu năng file hoặc thiết bị.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="performance-and-large-files-009">Không mặc định NIO hoặc memory mapping luôn nhanh hơn</a>

<details>
<summary>Xem chi tiết</summary>

# Không mặc định NIO hoặc memory mapping luôn nhanh hơn

## 1. Tư duy nền tảng: API không phải lời hứa về tốc độ

Khi làm việc với **tệp lớn** trong Java, thường nghe những nhận định như:

- “`FileChannel` thuộc NIO nên luôn nhanh hơn `FileInputStream`.”
- “`MappedByteBuffer` ánh xạ file vào bộ nhớ nên gần như không mất chi phí I/O.”
- “`transferTo()` luôn là zero-copy nên chắc chắn nhanh nhất.”
- “Direct `ByteBuffer` luôn tốt hơn heap `ByteBuffer`.”
- “File lớn thì cứ map hết file vào RAM.”

**Không nhận định nào đúng trong mọi điều kiện.** Các API Java cung cấp **cách truy cập và khả năng tối ưu hóa khác nhau**. Hiệu năng thực tế phụ thuộc workload, OS, filesystem, thiết bị, caching, kiểu đọc, kích thước khối, chi phí khởi tạo, bộ nhớ sẵn có, CPU, thiết lập JVM và cách chương trình đo.

Cần đánh giá ít nhất ba câu hỏi độc lập:

1. **Đúng chức năng không?** Copy xong dữ liệu có đầy đủ, đúng byte, không mất phần cuối, xử lý được EOF/partial read/write?
2. **Dùng tài nguyên như thế nào?** Heap, native memory, page cache, file descriptor, CPU, số syscall, GC pressure, page faults, disk I/O?
3. **Nhanh trong điều kiện nào?** File nhỏ hay lớn, đọc tuần tự hay ngẫu nhiên, đọc một lần hay lặp lại, cache nóng hay lạnh, một hay nhiều luồng, SSD hay network filesystem?

**Kết luận của bài:** chọn cách tiếp cận phù hợp dựa trên đặc tính dữ liệu và phép đo có kiểm soát. Không thay vòng lặp stream bằng NIO/mmap chỉ vì tên API nghe “hiện đại” hơn.

## 2. Phân biệt Java I/O, NIO và memory mapping

Java có nhiều API I/O tồn tại song song, không có ranh giới “cũ chậm / mới nhanh” tuyệt đối:

| Cách làm | API đại diện | Cơ chế thường dùng | Thế mạnh thường gặp |
| --- | --- | --- | --- |
| Stream tuần tự | `InputStream`, `OutputStream` | Đọc/ghi byte theo thứ tự | Đơn giản, xử lý pipeline, dễ giới hạn dữ liệu |
| Buffered stream | `BufferedInputStream`, `BufferedOutputStream` | Bộ đệm ứng dụng quanh stream | Giảm số lời gọi I/O nhỏ, xử lý tuần tự |
| Files utilities | `Files.copy`, `Files.newInputStream` | Implementation chọn cơ chế | Code ngắn, biểu đạt ý định copy rõ |
| NIO channel | `FileChannel` | Đọc/ghi với `ByteBuffer`, positional I/O | Truy cập theo offset, scattering/gathering, chuyển giữa channel |
| Channel transfer | `FileChannel.transferTo`/`transferFrom` | Cơ hội sử dụng optimized transfer | Chuyển dữ liệu giữa channel, có thể tránh copy trung gian |
| Memory-mapped file | `FileChannel.map` → `MappedByteBuffer` | Ánh xạ vùng tệp vào không gian địa chỉ | Truy cập nhiều vị trí bằng phép `get/put`, đọc ngẫu nhiên |
| Asynchronous file | `AsynchronousFileChannel` | I/O biểu diễn bằng completion/Future | Tổ chức ứng dụng bất đồng bộ |

**NIO** là tên một nhóm API (*New I/O*) chứ không phải một thuật toán nén, một kiểu bộ đệm hay bảo đảm giảm số syscall trên mọi JVM. Tương tự, “mmap” là kỹ thuật ánh xạ vào bộ nhớ ảo: **không đồng nghĩa toàn bộ file đã được tải lên RAM**.

Một chương trình dùng `FileChannel` có thể vẫn thực hiện **blocking file I/O**. `FileChannel` không phải `SelectableChannel`, không thể đăng ký `Selector` như một `SocketChannel`. Không nên nhầm NIO file channel với non-blocking socket.

## 3. Cơ chế liên quan tới hiệu năng

### 3.1. Bộ đệm ứng dụng và chi phí mỗi lời gọi

Nếu đọc từng byte bằng:

```java
int value;
while ((value = input.read()) != -1) {
    output.write(value);
}
```

thì khi stream không được buffer phù hợp, số lần gọi qua các tầng Java/OS có thể rất lớn. Một bước tối ưu thường hiệu quả hơn việc chuyển ngay sang API khác là **đọc theo khối**:

```java
byte[] buffer = new byte[64 * 1024];
int n;
while ((n = input.read(buffer)) != -1) {
    if (n == 0) {
        continue; // Cần chính sách không tiến triển tùy nguồn.
    }
    output.write(buffer, 0, n);
}
```

Hai đoạn chỉ minh họa; cần khai báo input/output, kiểm soát quyền sở hữu stream và lỗi I/O. Với `InputStream` chuẩn và độ dài > 0, `read` thông thường trả số dương hoặc `-1`; nếu một nguồn đặc biệt liên tục trả 0 thì vòng lặp trên sẽ busy-spin và cần xử lý khác.

**Đọc theo khối** thường giúp giảm overhead so với đọc từng byte, nhưng từ “thường” quan trọng: kích thước khối tối ưu vẫn phải đo và phụ thuộc API/tầng buffer bên dưới.

### 3.2. Page cache của hệ điều hành

Với file thông thường, OS thường cache dữ liệu trang tệp trong **page cache**. Do đó:

- Lần đọc đầu tiên (*cold-ish*) có thể cần đợi storage.
- Lần đọc sau (*warm*) có thể lấy phần lớn byte từ RAM của hệ thống.
- Hai chương trình dùng API khác nhau nhưng chạy liên tiếp trên cùng file có thể nhận mức cache **khác nhau**.
- File lớn hơn dung lượng cache và workload tranh chấp có thể thay đổi kết quả rất mạnh.

`Files.readAllBytes`, `FileChannel.read` và `MappedByteBuffer.get` đều có thể hưởng lợi từ page cache. Mapping **không loại bỏ** chi phí đưa trang từ storage vào cache. Nó thay cách truy cập dữ liệu, không vượt qua giới hạn vật lý của SSD/HDD/mạng.

### 3.3. System call, memory copy và page fault

Một `FileChannel.read(ByteBuffer)` có thể đi qua lời gọi hệ điều hành và sao chép byte tới buffer tương ứng. Memory mapping cho phép chương trình đọc vùng mapped bằng thao tác bộ nhớ sau khi mapping được thiết lập. Tuy nhiên, mapping có thể phát sinh **page faults**, chi phí ánh xạ và quản lý trang, TLB pressure, chi phí tạo/bỏ mapping, áp lực địa chỉ ảo và tương tác cache.

Giảm số lần copy **không có nghĩa tổng chi phí giảm** nếu chuyển khối nhỏ một lần rồi hủy mapping. Với truy cập ngẫu nhiên lặp đi lặp lại trên những trang đã có trong cache, mapping có thể thuận lợi; với một lần đọc tuần tự, buffer stream hoặc channel đôi khi dễ đạt throughput cao với ít phức tạp hơn.

### 3.4. Chi phí allocation và GC

`ByteBuffer.allocate(n)` tạo heap buffer; `ByteBuffer.allocateDirect(n)` tạo direct buffer có backing memory ngoài Java heap. Direct buffer có thể thuận lợi cho một số đường I/O native, nhưng:

- Cấp phát/thu hồi direct buffer có chi phí.
- Native memory không hiện toàn bộ ở biểu đồ heap.
- Tạo nhiều direct buffer ngắn hạn có thể gây áp lực tài nguyên.
- Một số thao tác vẫn cần copy hoặc tạo view tùy API.
- Không có hợp đồng “direct luôn nhanh hơn heap”.

**Đừng liên tục cấp phát buffer mới trong vòng copy nếu có thể tái sử dụng một buffer với vòng đời rõ ràng.**

## 4. Hiệu năng phụ thuộc workload

Một cách chọn hợp lý là mô tả workload trước:

| Workload | Ứng viên thường đáng thử | Không nên kết luận sớm |
| --- | --- | --- |
| File vài KiB, đọc một lần | `Files.readAllBytes` (nếu đã giới hạn), buffered stream | Mapping chắc chắn nhanh |
| File rất lớn, đọc tuần tự | Buffered stream, `FileChannel` + buffer, `Files.copy` | NIO chắc chắn hơn buffered stream |
| Sao chép nguyên file | `Files.copy`, `transferTo`/`transferFrom`, buffered copy | `transferTo` luôn zero-copy |
| Truy cập ngẫu nhiên nhiều offset | Positional `FileChannel.read`, memory mapping | Mmap luôn tốt hơn positional read |
| Truy cập lặp lại một vùng nhỏ | Cache ở ứng dụng, buffer reuse, mmap vùng nhỏ | Map toàn bộ file là tối ưu |
| Đọc một lần và bỏ file | Streaming buffer cố định | Luôn map rồi GC |
| Nhiều file nhỏ | Stream/`Files` đơn giản; đo overhead mở file | API tối ưu trên một file lớn vẫn tối ưu |
| Dữ liệu chưa biết kích thước | Streaming + byte limit | `readAllBytes` rồi mới kiểm tra |
| File luân phiên ghi, truncate, rotate | Channel đọc/ghi thường với state rõ | Mapping an toàn trước mọi thay đổi kích thước |

Các gợi ý trên chỉ để tạo **danh sách phương án benchmark**, không phải bảng xếp hạng tốc độ.

## 5. Buffered stream: baseline đơn giản nhưng mạnh

Một thiết kế dễ hiểu để copy file:

```java
try (InputStream input = new BufferedInputStream(
         Files.newInputStream(source), 64 * 1024);
     OutputStream output = new BufferedOutputStream(
         Files.newOutputStream(target), 64 * 1024)) {

    byte[] buffer = new byte[64 * 1024];
    int read;
    while ((read = input.read(buffer)) != -1) {
        output.write(buffer, 0, read);
    }
}
```

Đây là đoạn mã minh họa, giả định `source`/`target` là `Path` phù hợp và đã import lớp cần dùng. `close()` của buffered output sẽ flush dữ liệu còn trong buffer theo hợp đồng; lỗi trong close vẫn cần xử lý. Một số cách bố trí buffer ứng dụng có thể **thừa nhiều tầng đệm**, ví dụ có cả `BufferedInputStream` lẫn mảng đọc 64 KiB, nhưng vẫn là baseline dễ triển khai. Có thể thử `Files.newInputStream` + mảng lớn trực tiếp để xem buffered wrapper có lợi trong workload cụ thể hay không.

### Khi stream là lựa chọn tốt

- Dữ liệu xử lý **tuần tự**, không cần seek.
- Input lớn hoặc không giới hạn: muốn kiểm soát bộ nhớ bằng bộ đệm cố định.
- Dữ liệu cần xử lý qua pipeline nén, checksum, parser, encryption.
- Cần tương thích rộng, code đơn giản, dễ bảo trì.

### Khi stream chưa đủ

- Cần đọc/ghi ở offset tuyệt đối mà không đổi file position.
- Cần thao tác trên đoạn lớn hoặc nhiều vị trí của cùng file.
- Có cơ hội tối ưu chuyển byte giữa hai channel.
- Cần tính năng như `FileLock`, `force`, scattering/gathering.

Ngay cả khi nhu cầu dẫn đến NIO, vẫn phải xét **tổng chi phí** chứ không coi đó là bằng chứng tốc độ.

## 6. `FileChannel` và `ByteBuffer`: linh hoạt không đồng nghĩa miễn phí

`FileChannel` hỗ trợ các overload điển hình:

```java
int read(ByteBuffer dst) throws IOException;
int read(ByteBuffer dst, long position) throws IOException;
int write(ByteBuffer src) throws IOException;
int write(ByteBuffer src, long position) throws IOException;
long position() throws IOException;
FileChannel position(long newPosition) throws IOException;
```

Đây là chữ ký API rút gọn để minh họa, không phải class hoàn chỉnh.

`read` và `write` có thể hoàn tất **một phần** dữ liệu; không mặc định `read` lấp đầy `ByteBuffer` hoặc `write` tiêu thụ hết `remaining()` trong mọi tình huống. Với positional I/O, tham số `position` là offset **tuyệt đối trên file**; các overload có đối số offset **không sửa `channel.position()`**, nhưng vẫn thay đổi `ByteBuffer.position()` theo số byte truyền thực.

Mẫu copy theo channel:

```java
ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);

while (true) {
    buffer.clear();
    int n = sourceChannel.read(buffer);
    if (n == -1) {
        break;
    }
    if (n == 0) {
        throw new IOException("Read made no progress");
    }

    buffer.flip();
    while (buffer.hasRemaining()) {
        int written = targetChannel.write(buffer);
        if (written == 0) {
            throw new IOException("Write made no progress");
        }
    }
}
```

Đây là đoạn minh họa, giả định file channels đã mở và có import. Nó xử lý **partial write**, một yếu tố quan trọng về correctness. Với file thông thường trong chế độ blocking, không tiến triển thường là tình huống bất thường; chính sách cụ thể cần tùy loại channel.

Thêm một lớp buffer không tự đảm bảo tốc độ cao: nếu bạn chọn buffer quá nhỏ, phát sinh nhiều lời gọi; nếu chọn quá lớn, tăng chi phí cấp phát và ảnh hưởng cache/memory. Kích thước `4 KiB`, `64 KiB` hay `1 MiB` đều chỉ là **candidate cho phép đo**, không phải con số phổ quát.

## 7. `FileChannel.transferTo` và `transferFrom`: có thể tối ưu, không cam kết zero-copy

`FileChannel.transferTo(long position, long count, WritableByteChannel target)` chuyển một vùng dữ liệu từ file source tới channel đích.

Ưu điểm tiềm năng: implementation có thể dùng cơ chế native để hạn chế các bước trung gian khi chuyển giữa file và socket hoặc giữa những channel được hỗ trợ. Nhưng Javadoc nói rõ số byte chuyển **có thể ít hơn `count`**, và tùy hệ thống/loại channel, implementation **có thể không chuyển được tất cả hoặc có giới hạn riêng**.

Mẫu đúng khi cần copy toàn bộ:

```java
long size = source.size();
long copied = 0;

while (copied < size) {
    long n = source.transferTo(
            copied, size - copied, destination);

    if (n <= 0) {
        throw new IOException("transferTo made no progress");
    }
    copied += n;
}
```

Đoạn này cần khai báo `source` là `FileChannel` và `destination` là `WritableByteChannel`. Khi gặp 0, **không spin vô hạn**. Tùy API và thiết bị có thể chuyển sang buffer-based fallback, chờ điều kiện thích hợp hoặc báo lỗi; ví dụ ném ngoại lệ để không che lỗi copy thiếu.

**Những điều không được suy diễn:**

- Không có bảo đảm **mọi lần transferTo đều là zero-copy**.
- “Zero-copy” không có nghĩa CPU bằng 0, I/O storage bằng 0 hoặc không có bộ nhớ kernel tham gia.
- Không có bảo đảm lần truyền `count` sẽ chuyển đủ `count`.
- Thay đổi OS, JDK, filesystem, loại đích hoặc kích thước file có thể thay đổi đường tối ưu.
- Copy từ file vào **file khác** và truyền từ file vào **socket** có đường kernel khác nhau; không gộp thành một benchmark vô danh.

Nếu hệ thống cần copy file theo `Path` mà không phải xử lý dữ liệu từng byte, **`Files.copy`** cũng là candidate quan trọng. Implementation của nó có thể chọn tối ưu riêng, nên không nên buộc mọi trường hợp đi qua mã `transferTo` tự viết.

## 8. Ví dụ Java 17 chạy được: ba cách copy, cùng dữ liệu

Ví dụ này copy một file 8 KiB bằng ba kỹ thuật:

1. Buffered stream.
2. `FileChannel` + `ByteBuffer`.
3. `FileChannel.transferTo`.

Nó **không in thời gian** vì stdout cố định ở đây dùng để kiểm tra tính đúng đắn. Một lần chạy nhỏ trên file đã cache không đủ làm benchmark.

**Tệp `CopyStrategiesDemo.java`:**

```java
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

public class CopyStrategiesDemo {
    static void bufferedCopy(Path source, Path target)
            throws IOException {
        try (InputStream input = new BufferedInputStream(
                Files.newInputStream(source));
             OutputStream output = new BufferedOutputStream(
                Files.newOutputStream(target))) {
            byte[] bytes = new byte[4096];
            int n;
            while ((n = input.read(bytes)) != -1) {
                output.write(bytes, 0, n);
            }
        }
    }

    static void channelCopy(Path source, Path target)
            throws IOException {
        try (FileChannel input = FileChannel.open(
                    source, StandardOpenOption.READ);
             FileChannel output = FileChannel.open(
                    target, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.allocate(4096);
            while (true) {
                buffer.clear();
                int n = input.read(buffer);
                if (n == -1) {
                    break;
                }
                if (n == 0) {
                    throw new IOException("No read progress");
                }
                buffer.flip();
                while (buffer.hasRemaining()) {
                    if (output.write(buffer) == 0) {
                        throw new IOException("No write progress");
                    }
                }
            }
        }
    }

    static void transferCopy(Path source, Path target)
            throws IOException {
        try (FileChannel input = FileChannel.open(
                    source, StandardOpenOption.READ);
             FileChannel output = FileChannel.open(
                    target, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
            long size = input.size();
            long copied = 0;
            while (copied < size) {
                long n = input.transferTo(
                        copied, size - copied, output);
                if (n <= 0) {
                    throw new IOException("No transfer progress");
                }
                copied += n;
            }
        }
    }

    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("copy-demo-");
        Path source = directory.resolve("source.bin");
        Path a = directory.resolve("buffered.bin");
        Path b = directory.resolve("channel.bin");
        Path c = directory.resolve("transfer.bin");

        try {
            byte[] data = new byte[8192];
            for (int i = 0; i < data.length; i++) {
                data[i] = (byte) (i % 251);
            }
            Files.write(source, data);

            bufferedCopy(source, a);
            channelCopy(source, b);
            transferCopy(source, c);

            System.out.println("Buffered correct: "
                    + (Files.mismatch(source, a) == -1));
            System.out.println("Channel correct: "
                    + (Files.mismatch(source, b) == -1));
            System.out.println("Transfer correct: "
                    + (Files.mismatch(source, c) == -1));
            System.out.println("Original size: " + Files.size(source));
        } finally {
            Files.deleteIfExists(c);
            Files.deleteIfExists(b);
            Files.deleteIfExists(a);
            Files.deleteIfExists(source);
            Files.deleteIfExists(directory);
        }
    }
}
```

Biên dịch/chạy:

```bash
javac CopyStrategiesDemo.java
java CopyStrategiesDemo
```

**Stdout trong điều kiện I/O thành công:**

```text
Buffered correct: true
Channel correct: true
Transfer correct: true
Original size: 8192
```

`Files.mismatch` (có từ Java 12) trả `-1` nếu hai file giống nhau về byte. Phép kiểm tra này xác minh **đầu ra**, không chứng minh phương thức nào nhanh hơn.

### Phân tích ví dụ

- Tệp nguồn được tạo với 8.192 byte có quy luật, không phụ thuộc dữ liệu mạng.
- Buffered copy đọc/ghi theo khối 4.096 byte.
- Channel copy dùng đúng chu kỳ `clear → read → flip → write hết remaining`.
- Transfer copy dùng vòng lặp với `copied` để không bỏ phần nếu `transferTo` trả số nhỏ hơn `count`.
- Các file đích có tên khác nhau; code không ghi đè nguồn.
- `try-with-resources` đóng tất cả handle trước khi xóa file tạm.

**Nếu thay thành file rất lớn:** bản mẫu `Files.mismatch` sẽ đọc tệp để đối chiếu, làm thay đổi tình trạng cache. Khi đo throughput, phải tách **thời gian copy** khỏi **thời gian verify** (hoặc báo rõ cả hai), và bảo đảm các phương án được so trên **cùng điều kiện đầu vào**. Mỗi lượt chạy có thể cần reset file đích, cache policy và chế độ lưu trữ.

## 9. Memory mapping là gì — và không phải gì?

`FileChannel.map` ánh xạ một vùng tệp vào bộ nhớ ảo của tiến trình, trả về `MappedByteBuffer` (API thông dụng trên Java 17):

```java
MappedByteBuffer mapped = channel.map(
        FileChannel.MapMode.READ_ONLY,
        offset,
        length);
```

Đây là đoạn minh họa; `channel` phải mở đúng mode, `offset`/`length` hợp lệ. Sau khi map, `get(index)`/`getInt(index)` có thể truy cập vị trí trong vùng mapped mà không cần gọi `channel.read` cho mỗi phần tử.

**Nhưng mapping không có nghĩa:**

- File đã được tải toàn bộ vào RAM ngay lúc `map()` trả về.
- Truy cập byte đầu tiên không thể xảy ra page fault.
- Dữ liệu luôn nằm trong CPU cache.
- Không còn kernel, OS hoặc filesystem tham gia.
- Không bao giờ gây lỗi nếu file bị truncate từ tiến trình khác.
- Đã có API unmap đồng bộ tường minh trên `MappedByteBuffer` Java 17.
- Luôn nhanh hơn `FileChannel.read`, kể cả file nhỏ, mở/đọc một lần rồi đóng.

### Khi nào mapping đáng xem xét?

- Truy cập **nhiều lần** các vùng tệp khác nhau bằng offset.
- Bảng tra cứu hoặc cấu trúc dữ liệu nhị phân có địa chỉ bản ghi dễ tính.
- Nhiều lần random read trên vùng mapping có vòng đời đủ dài.
- Dữ liệu phù hợp để xem như một **vùng byte trong bộ nhớ** với index và format rõ ràng.

### Khi nào nên cân nhắc tránh?

- Tệp rất nhỏ, mở/đọc một lần.
- File cần thay thế/truncate/xóa ngay và quan tâm tính portable giữa OS.
- Pipeline tuần tự vốn đã đạt throughput phù hợp.
- Ứng dụng tạo/hủy hàng nghìn mapping ngắn hạn.
- Dữ liệu đầu vào có thể bị tiến trình khác thay đổi kích thước bất ngờ.
- Môi trường có giới hạn address space, native memory hoặc mapping count chặt.

**Map phù hợp workload, không phải “chế độ turbo” cho mọi I/O.**

## 10. Không gian địa chỉ ảo, RAM và page faults là ba thứ khác nhau

Khi mmap một tệp, tiến trình có **vùng ánh xạ địa chỉ ảo** để truy cập trang tệp. Việc một vùng rộng được ánh xạ không đồng nghĩa bằng ấy byte đã được copy vào Java heap hoặc đều resident trong RAM.

Phân biệt:

| Khái niệm | Ý nghĩa |
| --- | --- |
| Java heap | Nơi chứa object, array, một phần metadata do GC quản lý |
| Virtual address space | Không gian địa chỉ process, nơi mapping đặt vùng truy cập |
| Resident memory | Trang bộ nhớ vật lý hiện đang resident, phụ thuộc OS |
| Page cache | Bộ nhớ đệm của OS cho trang tệp |
| Page fault | Truy cập trang chưa sẵn sàng trong mapping hoặc cần thiết lập mapping/đọc từ storage |
| MappedByteBuffer | Java view tới vùng file mapped, có giới hạn `int` index |
| File descriptor/channel | Tài nguyên I/O để mở file; đóng channel không nhất thiết unmap vùng đã tạo |

Tốc độ có thể khác nhau tùy dữ liệu đã nằm trong page cache hay chưa. Đọc ngẫu nhiên qua mapping có thể phát sinh nhiều page faults không đều; đọc tuần tự qua buffered stream cho phép OS nhận diện pattern tốt và dùng prefetch/read-ahead. Với một workload khác, mapping có thể giảm overhead của nhiều thao tác positional read nhỏ.

**Đo cả CPU, page faults và I/O latency**, không chỉ tổng số byte đọc trên lý thuyết.

## 11. Ví dụ Java 17: random access bằng FileChannel và mmap đều đúng

Ví dụ sau tạo file nhị phân gồm bốn số nguyên big-endian và đọc số thứ ba (index 2) bằng:

1. `FileChannel.read(dst, offset)` từ vị trí tuyệt đối.
2. `MappedByteBuffer.getInt(index)` từ mapping read-only.

Mục tiêu: chứng minh **tính tương đương kết quả** trên dữ liệu này và phân biệt mô hình state. Không có khẳng định cách nào nhanh hơn từ vài byte.

**Tệp `RandomAccessStrategiesDemo.java`:**

```java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class RandomAccessStrategiesDemo {
    static int readIntAt(FileChannel channel, long offset)
            throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        buffer.order(ByteOrder.BIG_ENDIAN);

        int received = 0;
        while (buffer.hasRemaining()) {
            int n = channel.read(buffer, offset + received);
            if (n == -1) {
                throw new IOException("Unexpected EOF");
            }
            if (n == 0) {
                throw new IOException("No progress");
            }
            received += n;
        }

        buffer.flip();
        return buffer.getInt();
    }

    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("random-access-", ".bin");
        file.toFile().deleteOnExit();

        ByteBuffer source = ByteBuffer.allocate(4 * Integer.BYTES);
        source.order(ByteOrder.BIG_ENDIAN);
        source.putInt(10).putInt(20).putInt(30).putInt(40);
        Files.write(file, source.array());

        try (FileChannel channel = FileChannel.open(
                file, StandardOpenOption.READ)) {

            channel.position(1);
            int positional = readIntAt(channel, 2L * Integer.BYTES);

            MappedByteBuffer mapped = channel.map(
                    FileChannel.MapMode.READ_ONLY, 0, channel.size());
            mapped.order(ByteOrder.BIG_ENDIAN);
            int viaMap = mapped.getInt(2 * Integer.BYTES);

            System.out.println("FileChannel: " + positional);
            System.out.println("MappedByteBuffer: " + viaMap);
            System.out.println("Same value: "
                    + (positional == viaMap));
            System.out.println("Channel position: "
                    + channel.position());
        }
    }
}
```

Biên dịch/chạy bằng JDK 17+:

```bash
javac RandomAccessStrategiesDemo.java
java RandomAccessStrategiesDemo
```

**Stdout khi I/O thành công:**

```text
FileChannel: 30
MappedByteBuffer: 30
Same value: true
Channel position: 1
```

**Giải thích:**

- Số thứ ba nằm từ offset 8 đến offset 11 (mỗi `int` dài 4 byte).
- `readIntAt` lặp đến khi lấy đủ 4 byte và dùng `offset + received`; overload positional read không thay đổi `channel.position()`.
- `mapped.getInt(8)` là truy cập tuyệt đối trong buffer mapped; không cần gọi `channel.read` cho lần `getInt`.
- Trước thao tác, channel được đặt `position(1)`, và hai cách đọc không làm thay đổi giá trị này.
- Cả hai trả 30 nhưng **không suy ra hiệu năng tương đương hay ưu việt** chỉ từ correctness.

**Lưu ý vòng đời:** ví dụ dùng `deleteOnExit()` thay vì yêu cầu xóa file tạm ngay sau khi tạo mapped buffer, vì trên một số nền tảng mapping còn sống có thể ảnh hưởng thao tác xóa/rename. `deleteOnExit` cũng chỉ là nỗ lực dọn dẹp khi JVM thoát, không bảo đảm file sẽ được xóa trong mọi tình huống; ứng dụng chạy lâu cần chính sách quản lý lifecycle phù hợp.

### Vấn đề byte order

`MappedByteBuffer` và `ByteBuffer` có `ByteOrder`. Với dữ liệu định dạng big-endian, phải thiết lập/diễn giải nhất quán. Nếu sai endianness, hai cách đọc có thể trả giá trị khác nhau dù cả hai hoàn toàn đúng về byte. Đừng dùng kết quả parser sai để kết luận về API nhanh/chậm.

## 12. Giới hạn riêng của `MappedByteBuffer` trên Java 17

`MappedByteBuffer` kế thừa `ByteBuffer`, mà `capacity`, `position` và `limit` được biểu diễn bằng `int`. Vì vậy **một mapping đơn theo API trả MappedByteBuffer không thể chứa tùy ý cả file nhiều terabyte**. Java SE 17 `FileChannel.map` ràng buộc `size` theo giới hạn kiểu buffer; thực tế thường giới hạn mỗi vùng mapping ở cỡ tối đa `Integer.MAX_VALUE` byte.

**Muốn xử lý file lớn hơn giới hạn một buffer**, chia thành các cửa sổ (*windows*):

```text
File 10 GiB
   |
   +-- map window #1 (ví dụ 256 MiB)
   +-- map window #2 (256 MiB)
   +-- ...
   +-- map window N (phần cuối)
```

Nhưng mapping nhiều cửa sổ có chi phí tạo/quản lý; không nên mặc định **càng nhiều mapping càng nhanh**. Hệ thống có thể giới hạn số mapping, file descriptors hoặc address space, đặc biệt trên môi trường 32-bit hoặc container bị hạn chế.

### Cleanup/unmap không do `FileChannel.close()` quyết định

Với API `FileChannel.map(MapMode, long, long)` truyền thống trên Java 17:

- Mapping có thể tiếp tục hợp lệ **sau khi FileChannel đóng**.
- `MappedByteBuffer` không cung cấp `close()`/`unmap()` chuẩn công khai.
- Thời gian unmap phụ thuộc lifecycle bộ nhớ/GC, không phải thao tác `clear()` hoặc `force()`.
- Đổi tên/xóa/truncate file đang được map có thể gặp hành vi khác nhau giữa hệ điều hành và file system.

Điều này ảnh hưởng trực tiếp tới benchmark và hệ thống sản xuất: nếu mỗi lượt benchmark map một vùng mới nhưng không kiểm soát được vòng đời các mapping cũ, phép đo có thể tích lũy tài nguyên và trở nên không công bằng.

**Ghi chú theo phiên bản:** Từ Java 22, API chuẩn có overload `FileChannel.map(..., Arena)` trả `MemorySegment`, cho phép quản lý vòng đời qua arena có thể đóng. Đó là **API khác**, không khiến `MappedByteBuffer` Java 17 tự có `close()`. Khi cần tương thích Java 17, không dùng overload chỉ có ở Java 22.

## 13. Memory mapping có thể làm công việc chậm hơn ra sao?

Một số tình huống phản trực giác:

### 13.1. Một file nhỏ được đọc một lần

Chi phí thiết lập mapping, page faults và cleanup có thể lớn hơn lợi ích giảm số lời gọi `read`. Một lệnh `Files.readAllBytes` hoặc buffered stream có thể đơn giản và nhanh hơn trong bài đo cụ thể.

### 13.2. Đọc tuần tự lượng rất lớn

OS có thể thực hiện read-ahead hiệu quả với các lệnh đọc tuần tự. Memory mapping cũng có thể được tối ưu theo pattern, nhưng nó phát sinh page faults và chịu cách CPU/OS xử lý truy cập. Không có kết quả cố định về bên thắng.

### 13.3. Nhiều mapping ngắn hạn

Thay vì tái sử dụng một mapping lâu dài, chương trình map/unmap liên tục có thể chịu overhead metadata và native memory. Với API legacy, khi nào unmap còn phụ thuộc GC. Do đó pattern “mở → map → đọc 1 byte → bỏ mapping” thường không phải cách dùng tối ưu.

### 13.4. File thường xuyên bị truncate hoặc rotate

Mapping sống trong lúc file bị thay đổi kích thước có thể làm một phần vùng không truy cập được; hành vi lỗi có thể phụ thuộc OS. Nếu hệ thống là log rotate, temp file hoặc spool thường xuyên xóa, buffered FileChannel/read có thể dễ quản lý vòng đời hơn.

### 13.5. Áp lực bộ nhớ và cạnh tranh tài nguyên

Mapping không tự copy toàn bộ file lên heap nhưng có thể làm tăng pressure lên address space, page tables và cache. Trên máy nhiều service, một workload mmap có thể làm cache của workload khác bị đẩy ra, hoặc chính nó bị page faults nhiều hơn dự kiến.

**Đánh giá hiệu năng không chỉ nhìn time của một tác vụ mà còn nhìn ảnh hưởng tới toàn hệ thống.**

## 14. `Files.copy`: đừng bỏ qua API mức cao

Nếu mục tiêu chỉ là copy tệp:

```java
Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
```

`Files.copy` thường đơn giản hơn code tự viết nhiều vòng `read`/`write`, với ý định rõ và hỗ trợ các option phù hợp. Implementation có thể tận dụng các tối ưu dựa trên filesystem/provider.

**Nhưng**:

- Đừng mặc định mọi provider xử lý copy giống nhau.
- `COPY_ATTRIBUTES` và `REPLACE_EXISTING` liên quan hành vi metadata/xung đột tên, không phải bảo đảm tốc độ.
- Đừng tính kết quả benchmark nếu không kiểm tra file đích đã hợp lệ.
- Copy metadata, permission và symlink có khác biệt theo API/option và filesystem.
- Một số thao tác copy có thể thất bại giữa chừng; code nghiệp vụ cần quyết định cleanup/rollback.

Với dữ liệu cần kiểm tra/checksum hoặc biến đổi, tự stream theo chunk có thể là lựa chọn rõ hơn. Với thao tác copy nguyên file bình thường, nên **đưa Files.copy vào danh sách phương án đo**.

## 15. Cách thiết kế một benchmark công bằng

### 15.1. Xác định câu hỏi trước khi đo

Ví dụ câu hỏi có ý nghĩa:

> “Trên SSD này, với file 4 GiB và đọc tuần tự vào một checksum, throughput của buffered stream 64 KiB, FileChannel 64 KiB và mapping cửa sổ 256 MiB như thế nào khi cache nóng và cache lạnh?”

Một câu hỏi không rõ:

> “NIO có nhanh hơn IO không?”

Câu thứ hai thiếu workload, thiết bị, kích thước, tình trạng cache, điều kiện đo và tiêu chí thành công.

### 15.2. Kiểm soát các biến ảnh hưởng

| Biến | Tại sao cần kiểm soát |
| --- | --- |
| JVM/JDK và flags | JIT, GC, runtime implementation có thể khác |
| OS, filesystem, storage | Ảnh hưởng native I/O path, page cache, throughput |
| File size và phân bố dữ liệu | File nhỏ/ lớn và mức nén/file sparsity khác nhau |
| Kiểu truy cập | Sequential vs random, tỷ lệ read/write |
| Warm/cold cache | OS page cache có thể chi phối kết quả |
| Số lần lặp và warmup | JIT/GC/OS scheduler tạo nhiễu |
| Concurrency | I/O queue depth, contention và memory pressure |
| Buffer size và allocation | Có thể biến kết quả từ lợi ích API thành khác biệt kích thước khối |
| Đích copy | SSD cùng volume, volume khác, socket, network mount |
| Độ bền ghi | `force/fsync` thay đổi rất mạnh latency |
| Verification và setup | Tốn CPU/I/O, nên tách khỏi thời gian thao tác chính |
| Error/partial I/O handling | Code sai nhưng nhanh không phải chiến thắng |

Khi đo, chỉ nên thay **một chiều thiết kế** trong một nhóm so sánh (ví dụ thay cơ chế I/O, giữ kích thước block và input tương đương), rồi thử nghiệm thêm các chiều khác. So sánh một stream đọc từng byte với FileChannel buffer 1 MiB rồi kết luận “NIO nhanh hơn” là so sánh **hai thuật toán có số lần thao tác khác nhau**, không cô lập hiệu ứng của API.

### 15.3. Warmup và thứ tự chạy

Một vài lần benchmark đầu có thể gồm:
- Tải class và khởi tạo JVM/JIT.
- Cấp phát buffer, mở file và tạo metadata.
- Trang file chưa nằm trong cache.
- Các background processes và GC.

Nếu luôn chạy Stream trước rồi Mmap sau, Mmap có thể hưởng cache đã được Stream làm nóng. Nếu luôn đảo lại, người chạy trước chịu bất lợi. Nên **thay đổi/đảo thứ tự chạy, lặp nhiều lượt và báo phân bố kết quả**. Khi benchmark cold cache, chỉ dùng phương pháp thực sự kiểm soát được trạng thái cache và báo rõ cách thực hiện; đọc file với một tên mới không chắc làm dữ liệu lạnh về mặt OS.

**Không xóa cache toàn hệ thống trên môi trường đang chạy dịch vụ** chỉ để lấy một con số đo đẹp: tác động tới tác vụ khác và không phản ánh workload thực.

### 15.4. Tách throughput và latency

Một phương pháp có thể đạt **throughput MB/s** cao khi copy file lớn nhưng có **latency per lookup** không tốt nếu mỗi lần lại map/unmap. Một phương pháp khác có thể tối ưu truy cập random khi mapping đã tồn tại nhưng không phù hợp startup lạnh.

Các metric hữu ích:

- Tổng thời gian copy hoặc scan, đơn vị ms/s.
- Throughput theo MiB/s: `bytes / elapsedSeconds / 2^20`.
- Latency theo từng thao tác, median, p95, p99 khi phù hợp.
- CPU user/system, page faults, số syscall, context switches.
- GC pause/allocation rate, direct/native memory, resident memory.
- Disk reads/writes thực tế, IOPS, filesystem cache hit/miss nếu đo được.
- Tỷ lệ lỗi, số lần partial transfer, thời gian setup/cleanup.

Đừng công bố “nhanh gấp 3” nếu không có số liệu lặp, điều kiện đo và correctness check.

## 16. Một ví dụ đo thời gian đơn giản — chỉ để khám phá, không phải kết luận

`System.nanoTime()` thích hợp để đo khoảng thời gian đã trôi qua trong JVM, không phải đồng hồ ngày giờ. Một hàm đo rất đơn giản:

```java
long start = System.nanoTime();
copyOperation.run();
long elapsedNanos = System.nanoTime() - start;

double millis = elapsedNanos / 1_000_000.0;
System.out.printf("Elapsed: %.3f ms%n", millis);
```

Đây là đoạn **minh họa không độc lập**, giả định `copyOperation` là tác vụ đã khai báo. Con số `Elapsed` **không cố định** và tuyệt đối không được xem là kết quả hứa trước.

Nếu tự viết benchmark đơn giản:

1. Tạo sẵn input ở ngoài vùng đo.
2. Giữ mỗi phương án làm **cùng công việc**.
3. Đưa setup/cấp phát/open/close vào hay ra vùng đo **theo mục tiêu đo**; nếu hai phương án được so end-to-end trong production, nên tính cả overhead của chúng.
4. Tránh in log trong vùng đo.
5. Sau mỗi lượt, verify dữ liệu (có thể ở ngoài vùng đo).
6. Chạy nhiều vòng, xen kẽ thứ tự và thống kê median/dispersion.
7. Theo dõi cache/GC/paging để giải thích outlier.
8. Chỉ kết luận cho cấu hình đo, tránh tổng quát hóa.

JMH (Java Microbenchmark Harness) rất hữu ích cho mã thuần CPU/JIT; nhưng với file I/O, dùng JMH cũng **không tự kiểm soát OS page cache, thiết bị, filesystem và cạnh tranh tài nguyên**. Cần thiết kế benchmark I/O ở cấp hệ thống, có thể kết hợp công cụ profiling như JFR, `jcmd` và counters của OS.

## 17. Một cách thử nghiệm có ích hơn câu “NIO nhanh hơn”

Đặt ra ma trận đo, thay vì chỉ chọn 2 API:

| Workload | Cỡ dữ liệu | Kiểu | Candidates |
| --- | --- | --- | --- |
| Rất nhỏ | 1–16 KiB | Copy nguyên file | `Files.copy`, buffered stream, channel |
| Trung bình | 1–32 MiB | Copy tuần tự | buffered stream, channel, transferTo |
| Lớn | 1–8 GiB | Copy tuần tự | buffer/transferTo/Files.copy |
| Lookup ngẫu nhiên | 1 GiB | 8-byte records | positional FileChannel, mmap windows |
| Truy cập lặp lại | 256 MiB | Hot random reads | channel với cache tự quản, mmap |
| Nhiều file nhỏ | 10.000 file | Open+read+close | Files/stream, channel |
| Đọc dữ liệu có transform | Tùy ý | Hash/parse | streaming buffer, channel với buffer |

**Cần thử warm và cold-ish riêng**, và với file lớn phải có chiến lược không làm benchmark tự biến thành phép đo bộ nhớ cache (ví dụ dữ liệu quá nhỏ so với RAM có thể tạo kết quả “disk throughput” giả).

Mỗi workload cần thước đo riêng: số file/s với nhiều file nhỏ, MiB/s với copy tuần tự, p95 latency với random lookup, memory/time-to-cleanup với mapping ngắn hạn.

## 18. Những yếu tố có thể làm benchmark sai lệch

### 18.1. Chạy nhanh hơn vì không đọc đủ byte

Một đoạn `FileChannel.read(buffer)` có thể trả ít hơn `remaining()`. Nếu code không loop và chỉ xử lý số byte đầu, nó sẽ nhanh hơn đơn giản vì **bỏ sót dữ liệu**. Tương tự, `transferTo` có thể chuyển một phần; code không loop có thể trả về nhanh trước khi file đích đủ.

Luôn verify byte count và checksum/`Files.mismatch` sau khi chạy.

### 18.2. Thay đổi tốc độ bằng cách vô tình thay kích thước buffer

`BufferedInputStream` với mặc định buffer, `FileChannel` với 1 MiB direct buffer và mmap toàn file có chi phí rất khác. Muốn quy phần lợi ích cho API, phải kiểm soát hoặc ít nhất báo rõ buffer sizes và allocation reuse.

### 18.3. Trộn hot và cold cache

Một phương án chạy sau hưởng lợi từ phương án chạy trước. Không dùng một lần chạy duy nhất để đại diện cho cả hệ thống.

### 18.4. Đưa setup ra ngoài một cách thiên vị

Nếu mapping được tạo trước vùng đo, còn stream phải mở file trong vùng đo, bài đo có thể thiên vị mmap. Nhưng nếu ứng dụng thực reuse mapping trong nhiều giờ, **bài đo steady-state không tính startup** lại có lý do chính đáng. Vấn đề không phải đúng/sai tuyệt đối, mà là **định nghĩa rõ câu hỏi** và đo cả startup/end-to-end nếu cần.

### 18.5. Đếm `write()` là durability

Khi `write` trả về, dữ liệu thường đi vào cache hoặc hàng đợi của OS, không nhất thiết đã xuống thiết bị lưu trữ bền vững. `FileChannel.force(...)` hoặc `MappedByteBuffer.force()` có thể cần thiết tùy yêu cầu durability.

Nếu phương án A gọi `force` còn B không gọi, không thể kết luận B “nhanh hơn” cho cùng mức bảo đảm dữ liệu. Phải so theo cùng mức durability và error policy.

### 18.6. Bộ nhớ native không hiện đủ trong heap metric

Mmap và direct buffers có thể tiêu thụ native memory/virtual address, hoặc tăng page cache pressure, không hiện trực tiếp trong `Runtime.totalMemory()-freeMemory()`. Theo dõi RSS/process memory, Native Memory Tracking khi phù hợp, OS mapping statistics, page faults.

### 18.7. File bị nén, sparse hoặc copy-on-write

Một số filesystem có nén, sparse file hoặc *reflink/copy-on-write*. Lệnh copy có thể nhanh vì filesystem tối ưu metadata/copy semantics thay vì truyền toàn bộ byte qua storage. Điều này có giá trị trong workload thực, nhưng phải báo rõ bởi nó không chứng minh Java API đó luôn nhanh trên mọi filesystem.

## 19. Vấn đề correctness riêng của mapping

### 19.1. Khi file bị truncate trong lúc mapped buffer còn sống

Nếu file bị thay đổi kích thước hoặc truncate trong lúc chương trình đang truy cập mapping, một phần vùng mapped có thể trở nên không hợp lệ. Hành vi truy cập khi ấy có yếu tố phụ thuộc OS. Không mặc định `mapped.get(index)` luôn cho byte hợp lệ chỉ vì `index < mapped.capacity()`.

Khi file có writer khác, cần cơ chế phối hợp, snapshot hoặc chính sách versioning/locking thích hợp; mapping không tự khóa nội dung file.

### 19.2. `force()` không phải `unmap()`

`MappedByteBuffer.force()` yêu cầu đẩy thay đổi mapping (trong điều kiện phù hợp) theo hợp đồng, không giải phóng mapping. `clear()` chỉ đặt lại `position`/`limit` của buffer. `FileChannel.close()` đóng channel, không đảm bảo mapping legacy đã được tháo.

Sự nhầm lẫn về vòng đời có thể gây vấn đề xóa file trên một số OS, làm leak tài nguyên địa chỉ ảo trong chương trình chạy dài và khiến benchmark ngắn hạn không đại diện cho production.

### 19.3. Mapping không cho atomic update tùy ý

Ghi một cấu trúc nhiều field qua mapped buffer không tự tạo giao dịch nguyên tử. Reader ở tiến trình khác có thể thấy trạng thái cập nhật dở nếu không có protocol phối hợp. Với file dữ liệu nhạy cảm hoặc đòi consistency, cần cơ chế lock, versioning, checksum, journal hoặc transactional storage phù hợp.

Không nên chọn mmap chỉ để “ghi nhanh hơn” rồi bỏ qua tính nhất quán.

## 20. Chọn buffer size theo phép đo và giới hạn bộ nhớ

Mỗi buffer tác động tới:

- **Overhead mỗi lần read/write**: buffer nhỏ có thể gây nhiều lần gọi.
- **Heap/native allocation**: buffer lớn chiếm tài nguyên nhiều hơn.
- **CPU cache locality**: pattern có thể ảnh hưởng cache.
- **Concurrency**: nếu mỗi kết nối/tác vụ giữ buffer riêng, buffer size nhân lên theo lượng đồng thời.
- **Latency**: buffer quá lớn có thể tăng độ trễ xử lý một số pipeline.
- **I/O scheduler/readahead**: OS và thiết bị có hành vi khác nhau.

Ví dụ ngân sách đơn giản: nếu 200 tác vụ mỗi tác vụ giữ một buffer 1 MiB thì chỉ riêng buffer đã khoảng **200 MiB**. Nếu là hai buffer mỗi tác vụ, khoảng 400 MiB, chưa tính parser và object khác. `ByteBuffer.allocateDirect` không biến tài nguyên thành miễn phí chỉ vì nó ngoài heap.

**Kỹ thuật nên cân nhắc:** pool buffer có giới hạn, reuse buffer, bounded concurrency, admission control, chunked streaming; kết hợp đo GC/native memory.

## 21. Giới hạn đầu vào và khả năng chống resource exhaustion

Hiệu năng tốt không chỉ là copy nhanh trong điều kiện bình thường. Đầu vào lớn hoặc không tin cậy còn cần:

1. **Byte limit** trước và trong lúc đọc — không chỉ tin metadata.
2. **Timeout/deadline** để tránh luồng vô hạn hoặc client gửi rất chậm.
3. **Dung lượng file tạm và disk quota** nếu copy/giải nén ra đĩa.
4. **Giới hạn concurrency và hàng đợi** để không tạo hàng nghìn buffer lớn.
5. **Cơ chế rollback** khi copy chưa hoàn tất.
6. **Kiểm tra integrity và publish** sau khi dữ liệu đầy đủ.
7. **Giới hạn giải nén sau compression** nếu nguồn là ZIP/GZIP.

`Files.readAllBytes` thuận tiện nhưng cấp bộ nhớ tỷ lệ với kích thước file; không dùng trên đầu vào không có hạn mức rồi mới kiểm tra. Với memory mapping, không được xem file mmap là “không dùng RAM”: nó vẫn tham gia page cache, RSS, address-space management và page faults.

Đôi khi phương án có benchmark trung bình chậm hơn chút nhưng **ổn định, dễ cleanup, memory bounded** lại phù hợp production hơn phương án đạt peak throughput cao nhưng có rủi ro tăng tài nguyên không kiểm soát.

## 22. Những sai lầm thường gặp

| Sai lầm | Vì sao sai | Cách sửa |
| --- | --- | --- |
| “NIO luôn nhanh hơn IO.” | API không quyết định mọi chi phí | Benchmark theo workload |
| “mmap là đọc từ RAM, không dùng disk.” | Page fault có thể phải tải dữ liệu từ storage | Xem page cache, warm/cold |
| “transferTo luôn zero-copy.” | Implementation/path/target có thể khác | Đo theo OS/channel, xử lý partial |
| “Một lần transferTo copy đủ count.” | Có thể chuyển ít hơn yêu cầu | Vòng lặp cộng dồn bytes |
| “Một lần FileChannel.write gửi hết buffer.” | Partial write có thể xảy ra | Loop theo `hasRemaining()` |
| “Đổi heap ByteBuffer sang direct luôn nhanh.” | Allocation/native memory có chi phí | Đo, reuse, kiểm soát memory |
| “Mmap cho file nhỏ sẽ nhanh hơn.” | Setup/map/page fault có thể chiếm ưu thế | Thử buffered/Files baseline |
| “Map toàn bộ file nhiều terabyte bằng một buffer Java 17.” | `ByteBuffer` index `int` | Chia window hoặc API khác |
| “Đóng channel là unmap.” | Mapping legacy còn vòng đời riêng | Quản lý mapping lifecycle |
| “`mapped.force()` giải phóng mapping.” | Force là persistence, không phải release | Phân biệt flush/unmap |
| “Đo file thứ hai nhanh hơn chứng minh API tốt.” | File thứ hai có thể cache nóng | Xen kẽ, kiểm soát cache |
| “Đo 1 lần và in 1 số milliseconds là kết luận.” | Nhiễu JIT/GC/OS | Warmup, nhiều lần, thống kê |
| “Không cần verify khi benchmark.” | Code thiếu dữ liệu có vẻ nhanh | So byte count/hash/mismatch |
| “Chỉ xem Java heap là đủ.” | Native mapping/buffers dùng tài nguyên khác | Theo dõi RSS/page faults/native |
| “Benchmark không tính cleanup nhưng production có.” | Đang đo workload khác | Báo cả steady-state và end-to-end |
| “Write xong là chắc chắn durable.” | Cache/flush semantics khác nhau | So cùng mức force/durability |
| “Cứ map file đang bị ghi/truncate liên tục.” | Rủi ro invalid mapping/OS khác nhau | Phối hợp writer, snapshot hoặc I/O thường |
| “Buffer 1 MiB tốt cho mọi workload.” | Overhead/memory/concurrency khác nhau | Sweep kích thước buffer |

## 23. Bài tập thực hành

### Bài 1: một lần copy file 4 KiB

Bạn đang copy một file 4 KiB, kết quả phải được ghi đúng byte. Có nên mở `MappedByteBuffer` mặc định?

**Gợi ý:** bắt đầu với `Files.copy` hoặc buffered stream và đo nếu có nhu cầu tối ưu rõ ràng. Với file nhỏ, overhead thiết lập mapping có thể đáng kể so với lượng dữ liệu cần chuyển. Không khẳng định mmap luôn chậm; chỉ không mặc định chọn nó.

### Bài 2: kiểm tra đầu ra `transferTo`

Một lệnh `source.transferTo(0, 10_000, target)` trả về `4096`. File target đã có đủ 10.000 byte chưa?

**Chưa.** Chỉ có 4.096 byte được báo chuyển trong lần gọi ấy. Phải tiếp tục ở offset 4.096 và còn 5.904 byte, kiểm tra progress/lỗi và verify đích.

### Bài 3: cache làm sai kết luận

Bạn chạy buffered stream trước, lần sau chạy mapping và thấy mapping nhanh hơn nhiều. Có thể kết luận mapping tốt hơn không?

**Không.** Có thể file đã được stream đầu tiên làm nóng trong OS page cache, hoặc JIT/CPU/storage có trạng thái khác. Lặp, đảo thứ tự, kiểm soát cache và báo điều kiện.

### Bài 4: random lookup trên file đã mapped

Một ứng dụng đọc một record 8 byte ở 100.000 offset ngẫu nhiên của file dữ liệu lớn, lặp lại nhiều lần. Candidate nào đáng benchmark?

**Positional FileChannel.read và memory mapping** đều đáng thử, có thể thêm application-level caching. Mapping có thể giảm overhead gọi `read` lặp lại nhưng có thể tăng page faults/cache pressure. Quyết định bằng kết quả đo trên workload thật.

### Bài 5: đóng channel sau map

`FileChannel.close()` đã gọi sau khi map read-only trên Java 17. Bạn có thể bảo đảm file có thể xóa ngay lập tức trên mọi OS không?

**Không.** Legacy `MappedByteBuffer` có vòng đời mapping riêng, và Windows cùng một số môi trường có thể hạn chế xóa/rename file đang mapped. Thiết kế cleanup không được phụ thuộc giả định portable sai.

### Bài 6: “chạy nhanh hơn” vì không fsync

Hai phương án ghi file: A gọi `force(true)` sau ghi, B không gọi. B có thời gian ngắn hơn. Kết luận B tối ưu cho cùng yêu cầu durability được không?

**Không.** Hai phương án thực hiện **khác mức bảo đảm đồng bộ dữ liệu**. Cần so cùng hợp đồng durability.

### Bài 7: FileChannel có phải non-blocking giống SocketChannel?

**Không.** `FileChannel` là channel tệp có các thao tác `read/write` bình thường và không có `configureBlocking(false)`/`Selector` như `SelectableChannel` mạng. NIO không đồng nghĩa non-blocking với mọi channel.

### Bài 8: Cùng throughput, RAM khác nhau

Stream-copy 64 KiB buffer và `Files.readAllBytes` trên file 3 GiB có throughput gần tương đương trên máy thử nghiệm. Phương án nào thường dễ kiểm soát bộ nhớ hơn?

**Streaming với buffer giới hạn**, vì `readAllBytes` cố tạo mảng chứa toàn bộ tệp, có thể không cấp phát được và chiếm heap lớn; còn stream-copy không phải giữ mọi byte đầu vào trong một mảng. Vẫn cần kiểm soát output và dữ liệu trung gian.

## 24. Checklist quyết định trong dự án thật

**Trước khi tối ưu:**

- Mô tả workload: file size, số file, sequential/random, read/write, một lần hay lặp, concurrency.
- Xác định correctness: byte count, integrity, EOF, partial reads, cleanup và data durability.
- Xác định ràng buộc: RAM, address space, file descriptors, latency SLO, platform/JDK.
- Chọn baseline đơn giản: `Files.copy`, buffered stream hoặc `Files.newInputStream`.

**Khi thử NIO và mmap:**

- Với channel: dùng buffer hợp lý, xử lý partial I/O, tránh cấp phát lại mỗi lần.
- Với transfer: kiểm tra số byte thực chuyển, có fallback/error khi không tiến triển.
- Với mmap: xem cả phí setup, page faults, unmap lifecycle, truncate/rename, window size.
- So sánh theo cùng input và durability policy.
- Đo cả total time, throughput, latency, CPU, heap/native memory, page cache/paging.

**Khi báo kết quả:**

- Ghi version JDK, OS, filesystem, thiết bị, kích thước file, cache state, buffer size.
- Công bố số lượt chạy, phương pháp warmup và thống kê kết quả.
- Tách cold/warm, steady-state/end-to-end nếu cần.
- Xác minh mọi phương án đều tạo kết quả giống nhau.
- Chỉ khuyến nghị phương án “nhanh hơn” **cho workload đã đo**, không nâng thành quy luật chung.

## 25. Tổng kết

Không có một lựa chọn duy nhất luôn chiến thắng giữa **Buffered IO**, **FileChannel**, **`transferTo`**, **`Files.copy`** và **`MappedByteBuffer`**.

- **Buffered stream** đơn giản, phù hợp tuần tự và có thể rất hiệu quả.
- **FileChannel** hữu ích cho positional I/O và thao tác với `ByteBuffer`; không có bảo đảm nhanh hơn chỉ vì thuộc NIO.
- **`transferTo`/`transferFrom`** mở cơ hội tối ưu chuyển dữ liệu; phải xử lý partial transfer và không giả định luôn zero-copy.
- **Memory mapping** có thể thuận tiện, đặc biệt với random access lặp lại, nhưng có chi phí page faults, metadata, tài nguyên native và vòng đời mapping cần quản lý.
- **Page cache, filesystem, OS, JVM và workload** là những biến chi phối thực tế; điều kiện warm/cold quan trọng.
- **Benchmark có kiểm soát + correctness verification** là cơ sở lựa chọn, không phải tên API hoặc kết quả một lần chạy.

**Nguyên tắc thực tế:** bắt đầu bằng phương án **đúng, dễ hiểu và bounded**, xác định bottleneck bằng phép đo, rồi chỉ tăng độ phức tạp khi lợi ích có thể chứng minh và vẫn đáp ứng yêu cầu về độ ổn định, tính portable và khả năng bảo trì.

**Tài liệu Java SE 17:**

1. [FileChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileChannel.html) — `read/write`, positional I/O, `transferTo`, `transferFrom`, `map`, `force`.
2. [MappedByteBuffer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/MappedByteBuffer.html) — mapping semantics, `load/isLoaded/force`.
3. [ByteBuffer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html) — heap/direct buffer, `position/limit`.
4. [Files](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html) — `copy`, `newInputStream`, `mismatch`.
5. [BufferedInputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedInputStream.html) và [BufferedOutputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedOutputStream.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="performance-and-large-files-010">Giới hạn file descriptor và số tài nguyên đang mở</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn file descriptor và số tài nguyên đang mở trong Java I/O

## 1. Tại sao một chương trình Java có thể mở được file nhưng lại hết tài nguyên?

Một ứng dụng xử lý tệp lớn thường không bị giới hạn chỉ bởi dung lượng RAM hoặc tốc độ đọc/ghi. Nó còn phải chia sẻ các **tài nguyên hệ điều hành**: file descriptors, handles, sockets, directory streams, bộ nhớ native và các cấu trúc quản lý I/O.

Khi một chương trình mở tệp, thư mục, kết nối mạng hoặc một số loại channel, hệ điều hành thường cần cấp tài nguyên để theo dõi đối tượng đang mở. Trên các hệ thống giống Unix, ứng dụng tương tác với **file descriptor (FD)**, thường là một số nguyên trong bảng descriptor của tiến trình. Trên Windows, cách biểu diễn thường liên quan đến **handles**; không nên giả định mọi tài nguyên đều có một số FD Unix.

~~~text
Java process / JVM
    |
    +-- FileInputStream ─────┐
    +-- FileChannel ─────────┤
    +-- SocketChannel ───────┤
    +-- ServerSocket ────────┤
    +-- DirectoryStream ─────┤ → tài nguyên của OS / provider
    +-- ZipFile ─────────────┤ → giới hạn tiến trình hoặc hệ thống
    +-- Selector / watcher ──┘
    |
    +-- Threads / ByteBuffers / native memory (tài nguyên khác)
~~~

Không có quy tắc phổ quát **mỗi object Java tương ứng đúng một descriptor**. Một object có thể dùng chung tài nguyên nền với object khác; một abstraction có thể cần nhiều handle hoặc tài nguyên native; filesystem provider không phải lúc nào cũng triển khai bằng file descriptor của hệ điều hành. Vì vậy, hãy xem số FD/handle là **tài nguyên hữu hạn cần giám sát**, không phải suy ra trực tiếp bằng cách đếm object trong heap.

**Mục tiêu của bài học:** hiểu ý nghĩa các giới hạn mở tệp, hậu quả khi vượt giới hạn, cách đóng tài nguyên trong Java 17+, công cụ chẩn đoán và cách giới hạn concurrency để ứng dụng phục vụ tải lớn mà không tự làm cạn FD.

## 2. File descriptor là gì?

Trên Unix/Linux, một file descriptor là một số nguyên mà tiến trình dùng để tham chiếu một mục trong bảng descriptor. Hệ điều hành quản lý thông tin nền về tài nguyên tương ứng, chẳng hạn file đang mở, socket hoặc pipe.

Các descriptor chuẩn thường gặp:

| FD | Tên | Vai trò thông thường |
| ---: | --- | --- |
| 0 | stdin | Đầu vào chuẩn của tiến trình |
| 1 | stdout | Đầu ra chuẩn |
| 2 | stderr | Đầu ra báo lỗi |

Các descriptor khác có thể được cấp khi ứng dụng mở file, socket, pipe, thư mục hoặc một tài nguyên tương ứng. **Đừng mặc định các số 0–2 luôn trỏ tới terminal**: trong container, service hoặc chương trình có redirect, chúng có thể trỏ tới pipe, file log hoặc bị thay thế.

Ví dụ server Java có 800 kết nối TCP đồng thời và vài chục file log/dữ liệu đang mở sẽ tiêu thụ một lượng tài nguyên OS đáng kể, dù các request rất nhỏ. Cài đặt một `Selector` để quản lý nhiều socket bằng một thread **không xóa nhu cầu tài nguyên OS của chính từng socket**.

### FD và Java FileDescriptor không hoàn toàn đồng nghĩa

Java có `java.io.FileDescriptor` và những lớp như `FileInputStream`, `RandomAccessFile`, `FileChannel`. Nhưng việc tồn tại class `FileDescriptor` không có nghĩa đây là một công cụ portable để **đếm mọi file descriptor** đang mở trong JVM.

Tương tự, số lượng object `FileChannel` trong heap không phải phép đo chính xác tổng số FD của tiến trình. Muốn biết áp lực tài nguyên thực, dùng metric hoặc công cụ phù hợp với OS, kết hợp giám sát ở tầng ứng dụng.

## 3. Những giới hạn nào có thể gây ra lỗi?

Giới hạn thay đổi theo hệ điều hành, user, container, chính sách service và loại tài nguyên.

### 3.1. Giới hạn trên mỗi tiến trình

Trên nhiều hệ thống Unix, giới hạn số file descriptor đang mở đồng thời được kiểm soát bởi tài nguyên `RLIMIT_NOFILE`, với các khái niệm **soft limit** và **hard limit**:

- **Soft limit:** ngưỡng mà tiến trình đang chịu khi yêu cầu thêm FD.
- **Hard limit:** trần điều chỉnh soft limit mà tiến trình không có đặc quyền phù hợp không được vượt qua.

Các giá trị thực tế khác nhau giữa máy phát triển, CI, container và server. Không nên ghi cứng rằng “Linux cho phép tối đa 1.024 file” hay “mọi JVM có 65.535 FD”.

Trên Unix/Linux, có thể kiểm tra trong shell:

~~~bash
ulimit -Sn   # soft limit của shell hiện tại
ulimit -Hn   # hard limit của shell hiện tại
~~~

**Chú ý:** hai lệnh này nói về **shell đang chạy**, không nhất thiết phản ánh một JVM đã khởi động từ một service manager khác. Với Linux, xem giới hạn của **tiến trình Java đang chạy** bằng công cụ OS, chẳng hạn `/proc/<PID>/limits` nếu procfs được cho phép.

### 3.2. Giới hạn toàn hệ thống và tài nguyên khác

Ngoài giới hạn per-process, OS còn có giới hạn hoặc áp lực toàn hệ thống, quota người dùng, bộ nhớ kernel, bộ nhớ native và giới hạn của filesystem/provider. Một chương trình có thể thất bại dù chưa đạt tới con số soft limit tưởng tượng, ví dụ khi:

- Nguồn lực chung của kernel không đủ.
- Container hoặc service có chính sách hạn chế riêng.
- File system hoặc thiết bị bị lỗi.
- Quá nhiều socket, mapped regions, native buffers hoặc các tài nguyên đồng thời khác.
- Permission/quota cấm cấp tài nguyên.

Trên Unix, hai mã lỗi nổi tiếng là:

| Mã lỗi OS | Ý nghĩa khái quát |
| --- | --- |
| `EMFILE` | Tiến trình đã đạt giới hạn file descriptor của nó |
| `ENFILE` | Hệ thống đã đạt giới hạn tài nguyên file-open liên quan trên phạm vi rộng hơn |

Java có thể chuyển các lỗi này thành `IOException`, `FileSystemException`, `SocketException` hoặc một ngoại lệ I/O có thông báo chứa `Too many open files`. **Không dựa duy nhất vào text của exception để phân loại lỗi trên mọi hệ điều hành**.

Windows sử dụng cơ chế handle với giới hạn và triệu chứng khác; một số công cụ chỉ hiển thị tổng handle count bao gồm nhiều loại tài nguyên **không phải file**. Do đó không so sánh máy Windows và Linux bằng một con số đơn giản mà không biết phép đo là gì.

## 4. Khi quá nhiều file đang mở, điều gì xảy ra?

Một lỗi không chỉ ảnh hưởng thao tác mở file gây ra nó. Khi JVM hết khả năng lấy tài nguyên mới:

- `Files.newInputStream(path)` hoặc `FileChannel.open(path)` có thể thất bại.
- Server có thể không `accept()` được socket mới hoặc không mở được kết nối outbound.
- Bộ ghi log có thể không mở/rotate file.
- Thread kiểm tra health hay bộ giám sát cũng có thể cần mở socket/file và gặp lỗi.
- Tác vụ xử lý tệp theo batch có thể bị lỗi dây chuyền, retry liên tục làm tình hình tệ hơn.

~~~text
Nhu cầu xử lý tăng
        |
        v
Mở 1 channel cho mỗi task
        |
        v
Task bị giữ lâu / quên close
        |
        v
Số tài nguyên đang mở tăng
        |
        v
Chạm giới hạn OS
        |
        +--> open() thất bại
        +--> socket mới thất bại
        +--> logger / healthcheck có thể thất bại
        |
        v
Retry không giới hạn → càng thêm tải
~~~

**Không nên chữa nguyên nhân bằng cách chỉ tăng `ulimit`.** Tăng giới hạn có thể cần thiết khi tải hợp lệ thực sự cao, nhưng nếu ứng dụng rò rỉ resource thì một mức trần lớn hơn chỉ làm quá trình lỗi diễn ra chậm hơn.

## 5. Những Java API nào cần đóng tài nguyên?

Một quy tắc học tập quan trọng: **không phải cứ object Java không còn tham chiếu là tài nguyên OS được trả lại ngay**. Garbage collector giải phóng bộ nhớ quản lý, còn thời điểm đóng file/socket không được dùng GC như cơ chế điều khiển vòng đời đáng tin cậy.

Một số loại cần quản lý rõ ràng:

| API / tài nguyên | Cách giải phóng thông thường | Lưu ý |
| --- | --- | --- |
| `FileInputStream`, `FileOutputStream` | `close()` / try-with-resources | Giải phóng tài nguyên luồng |
| `BufferedInputStream`, `BufferedOutputStream` | Đóng wrapper | Thường đóng cả stream được bọc; xác định quyền sở hữu |
| `FileReader`, `FileWriter`, `BufferedReader`, `BufferedWriter` | `close()` | Không để reader/writer sống vô hạn |
| `FileChannel`, `SeekableByteChannel` | `close()` | Channel đang mở chiếm tài nguyên tương ứng |
| `RandomAccessFile` | `close()` | Có thể chia sẻ tài nguyên nền với channel liên quan |
| `Socket`, `ServerSocket`, `SocketChannel`, `ServerSocketChannel` | `close()` | Socket cũng sử dụng tài nguyên OS |
| `AsynchronousFileChannel` | `close()` | Xử lý tác vụ pending và executor theo thiết kế |
| `DirectoryStream<Path>` | `close()` | Có thể giữ handle để duyệt thư mục |
| `Stream<Path>` từ `Files.list/walk/find` | `Stream.close()` | Đây là stream gắn với filesystem, không phải stream dữ liệu thuần RAM |
| `Stream<String>` từ `Files.lines` | `Stream.close()` | Giữ file mở trong khi stream tồn tại |
| `ZipFile` | `close()` | Quản lý file archive và dữ liệu nội bộ |
| `WatchService`, `Selector` | `close()` | Giải phóng tài nguyên theo dõi I/O |
| `FileSystem` mở mới như ZIP filesystem | `close()` khi sở hữu | Không gọi `close()` bừa bãi lên default filesystem |
| `MappedByteBuffer` | Không có `close()` chuẩn trực tiếp | Mapping có vòng đời đặc biệt; đóng channel không bảo đảm lập tức unmap |

Các phương thức tiện ích như `Files.readString` và `Files.readAllBytes` tự quản lý luồng bên dưới trong quá trình gọi; bạn không giữ một `InputStream` cần đóng do các phương thức đó trả về. Nhưng chúng có thể cần nhiều RAM nếu tệp rất lớn, là **vấn đề riêng** so với FD.

**Nguyên tắc ownership:** ai mở resource thì phải có trách nhiệm bảo đảm resource được đóng, trừ khi quyền sở hữu đã được bàn giao rõ ràng cho thành phần khác.

## 6. try-with-resources: quy tắc quan trọng nhất

Java `try-with-resources` tự gọi `close()` khi ra khỏi khối, kể cả khi có ngoại lệ:

~~~java
Path path = Path.of("input.dat");

try (InputStream input = Files.newInputStream(path)) {
    byte[] buffer = new byte[8192];
    int n;
    while ((n = input.read(buffer)) != -1) {
        // Xử lý đúng n byte của từng lần đọc.
    }
}
~~~

Đoạn trên là **mảnh Java minh họa trong phương thức**, yêu cầu import `java.io.InputStream` và `java.nio.file.*`; file phải tồn tại nếu chạy trực tiếp.

Nếu mở nhiều resource trong một khối, Java đóng theo **thứ tự ngược với thứ tự khai báo**:

~~~java
try (InputStream raw = Files.newInputStream(path);
     BufferedInputStream buffered = new BufferedInputStream(raw)) {
    // Đọc buffered.
}
~~~

Trong ví dụ này, `buffered.close()` thường đóng cả `raw`; cơ chế try-with-resources vẫn gọi `raw.close()` khi đến lượt. Hầu hết lớp stream chuẩn xử lý close lặp an toàn, nhưng không nên tự tạo wrapper bất thường rồi giả định mọi API đều có cùng semantics.

### Ngoại lệ khi close và suppressed exceptions

Nếu code trong khối ném lỗi và `close()` cũng ném lỗi, try-with-resources giữ lỗi chính và lưu những lỗi close liên quan dưới dạng **suppressed exceptions**.

~~~java
try (InputStream input = Files.newInputStream(path)) {
    // Xử lý có thể ném IOException.
} catch (IOException ex) {
    System.err.println("I/O error: " + ex.getMessage());
    for (Throwable suppressed : ex.getSuppressed()) {
        System.err.println("Close error: " + suppressed);
    }
}
~~~

Đây là mảnh minh họa. Không nên cố ý nuốt mọi ngoại lệ trong `finally` vì có thể mất thông tin về tình trạng tài nguyên và dữ liệu chưa được flush.

### Không giao trả một resource sắp bị đóng

Sai về ownership:

~~~java
InputStream openWrong(Path path) throws IOException {
    try (InputStream in = Files.newInputStream(path)) {
        return in; // Khi caller nhận thì stream đã đóng!
    }
}
~~~

Mảnh trên chỉ là minh họa anti-pattern. Nếu một phương thức trả về resource mở để caller dùng tiếp, **không đóng ở callee**; phải quy định caller sở hữu và đóng:

~~~java
InputStream openForCaller(Path path) throws IOException {
    return Files.newInputStream(path);
}
~~~

Caller sau đó phải dùng `try-with-resources`. Lựa chọn an toàn hơn nữa là phương thức nhận callback xử lý bên trong khối và không để resource thoát ra ngoài.

## 7. Đừng bỏ sót resource ẩn trong các stream duyệt file

Một lỗi khó phát hiện là nghĩ rằng Java `Stream` luôn chỉ là pipeline tính toán trong bộ nhớ:

~~~java
// Có nguy cơ để directory stream mở cho tới khi được giải phóng muộn.
Stream<Path> paths = Files.list(root);
long files = paths.count();
~~~

`Files.list(root)` trả `Stream<Path>` **gắn với directory đang mở**; stream đó cần được đóng ngay khi dùng xong. Cách đúng:

~~~java
try (Stream<Path> paths = Files.list(root)) {
    long files = paths.count();
    System.out.println(files);
}
~~~

Tương tự:

~~~java
try (Stream<Path> paths = Files.walk(root)) {
    paths.filter(Files::isRegularFile)
         .forEach(path -> {
             // Xử lý từng path.
         });
}
~~~

`Files.walk(...)` và `Files.find(...)` cũng trả filesystem-backed stream cần đóng. `Files.lines(path)` trả stream các dòng dựa trên file đang mở:

~~~java
try (Stream<String> lines =
        Files.lines(path, StandardCharsets.UTF_8)) {
    lines.forEach(System.out::println);
}
~~~

Những đoạn trên là **mảnh mã minh họa**, cần import `java.util.stream.Stream`, `java.nio.file.Files`, `java.nio.file.Path` và `java.nio.charset.StandardCharsets` phù hợp.

Không nên gọi `Files.list(...)` hàng chục lần trong vòng lặp rồi giữ tất cả stream trong một collection cho tới cuối job. Tốt hơn là mở–duyệt–đóng từng thư mục, hoặc xử lý số lượng stream cùng lúc có giới hạn.

### DirectoryStream khác gì?

`DirectoryStream<Path>` cho phép duyệt entry trực tiếp:

~~~java
try (DirectoryStream<Path> entries =
        Files.newDirectoryStream(root)) {
    for (Path entry : entries) {
        // Xử lý entry.
    }
}
~~~

Cả `DirectoryStream` lẫn stream từ `Files.list` có thể giữ tài nguyên hệ điều hành khi duyệt. Chúng cần vòng đời ngắn, nhất là trong bộ quét cây thư mục lớn.

## 8. Tệp lớn: không nên mở toàn bộ file cùng lúc

Giả sử batch xử lý 50.000 file:

~~~text
Cách dễ thất bại:
- lấy danh sách 50.000 file
- mở FileChannel cho cả 50.000
- để mở trong khi tính toán
- đóng sau khi mọi task hoàn thành

Cách có kiểm soát:
- duyệt file
- mở tối đa K channel cùng lúc
- đọc/ghi theo streaming
- đóng channel trước khi lấy thêm công việc
- giữ queue công việc có giới hạn
~~~

Tăng concurrency không tạo tăng thông lượng vô hạn. Ổ lưu trữ có throughput và IOPS hữu hạn; quá nhiều file mở có thể tăng context switching, cache pressure, memory mapping pressure và chi phí scheduling. Với nhiều tệp nhỏ, việc đọc song song ở mức vừa phải thường tốt hơn việc mở tất cả tài nguyên và giữ chúng.

Để xử lý dữ liệu lớn, ưu tiên:

1. **Bounded concurrency:** giới hạn số file/channel/socket được mở đồng thời.
2. **Streaming:** đọc theo khối thay vì `readAllBytes` vào RAM nếu tệp quá lớn.
3. **Close sớm:** không giữ descriptor trong lúc CPU-bound processing nếu có thể đóng trước.
4. **Không lưu stream/channel trong cache vô hạn:** cache phải có max size, eviction và cơ chế đóng.
5. **Backpressure:** khi worker bận, giảm tốc độ lấy job mới thay vì mở thêm file.
6. **Dự phòng headroom:** đừng dùng hết mọi FD còn có thể cấp; logging, sockets và thư viện hệ thống cũng cần tài nguyên.

Con số K không có một mức tối ưu cho mọi máy. Phải đo throughput, latency, active FD, lỗi I/O và memory footprint trên môi trường vận hành thực tế.

## 9. Semaphore: tạo ngân sách riêng cho số channel đang mở

`java.util.concurrent.Semaphore` là một primitive để điều khiển số permit được sử dụng đồng thời. Một permit có thể đại diện cho quyền **mở một file channel** trong subsystem do ứng dụng quản lý.

~~~text
Budget = 3 permits

Task 1 lấy 1 permit → mở channel A
Task 2 lấy 1 permit → mở channel B
Task 3 lấy 1 permit → mở channel C
Task 4 không còn permit → chờ, trả lỗi hoặc retry có kiểm soát

A đóng channel và trả permit
Task 4 có thể mở file tiếp
~~~

**Semaphore không thay đổi `ulimit` và không đo FD của hệ điều hành.** Nó chỉ giúp ứng dụng tuân thủ một ngân sách do mình đặt ra. Những file/socket được mở ngoài subsystem vẫn cần tính vào headroom tổng thể.

Một mô hình tốt có ba thuộc tính:

- Lấy permit **trước khi mở** tài nguyên.
- Nếu mở thất bại, **trả permit** ngay; không giữ permit bị thất lạc.
- Nếu mở thành công, resource wrapper trả permit trong `close()`, ngay cả khi quá trình sử dụng có lỗi.

Nếu dùng `Semaphore.acquire()` để đợi permit, cần xác định timeout/interrupt/cancellation. Nếu dùng `tryAcquire()` để trả lỗi nhanh, upstream cần chính sách retry hoặc backpressure thích hợp, không retry tight loop.

## 10. Ví dụ Java 17+ chạy độc lập: giới hạn hai FileChannel

Ví dụ dưới dùng một **ngân sách nhân tạo** gồm hai permit, không cố tạo hàng nghìn FD hoặc đẩy máy tới giới hạn thật. Nó mở hai file channel, thử mở cái thứ ba (bị từ chối an toàn), đóng một channel rồi mở cái thứ ba thành công.

Kết quả là deterministic và không phụ thuộc giới hạn file descriptor của hệ điều hành, miễn là môi trường cho phép tạo ba file tạm và mở hai channel thông thường.

Lưu thành `FileDescriptorBudgetDemo.java`:

~~~java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Semaphore;

public class FileDescriptorBudgetDemo {

    static final class Budget {
        private final Semaphore permits;

        Budget(int maximumOpenFiles) {
            if (maximumOpenFiles <= 0) {
                throw new IllegalArgumentException("maximum must be positive");
            }
            this.permits = new Semaphore(maximumOpenFiles);
        }

        Lease open(Path path) throws IOException {
            if (!permits.tryAcquire()) {
                throw new IllegalStateException("Application budget exhausted");
            }

            boolean successful = false;
            try {
                FileChannel channel =
                        FileChannel.open(path, StandardOpenOption.READ);
                successful = true;
                return new Lease(channel, permits);
            } finally {
                if (!successful) {
                    permits.release();
                }
            }
        }

        int available() {
            return permits.availablePermits();
        }
    }

    static final class Lease implements AutoCloseable {
        private final FileChannel channel;
        private final Semaphore permits;
        private boolean closed;

        Lease(FileChannel channel, Semaphore permits) {
            this.channel = channel;
            this.permits = permits;
        }

        FileChannel channel() {
            if (closed) {
                throw new IllegalStateException("Lease already closed");
            }
            return channel;
        }

        @Override
        public void close() throws IOException {
            if (!closed) {
                closed = true;
                try {
                    channel.close();
                } finally {
                    permits.release();
                }
            }
        }
    }

    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("fd-budget-");
        Path first = dir.resolve("one.txt");
        Path second = dir.resolve("two.txt");
        Path third = dir.resolve("three.txt");

        try {
            Files.writeString(first, "one", StandardCharsets.US_ASCII);
            Files.writeString(second, "two", StandardCharsets.US_ASCII);
            Files.writeString(third, "three", StandardCharsets.US_ASCII);

            Budget budget = new Budget(2);

            try (Lease one = budget.open(first)) {
                try (Lease two = budget.open(second)) {
                    System.out.println("available with two open="
                            + budget.available());

                    boolean rejected = false;
                    try (Lease extra = budget.open(third)) {
                        // Không tới đây khi budget đã hết.
                    } catch (IllegalStateException ex) {
                        rejected = true;
                    }
                    System.out.println("third rejected=" + rejected);
                }

                System.out.println("available after second closes="
                        + budget.available());

                try (Lease three = budget.open(third)) {
                    ByteBuffer buffer = ByteBuffer.allocate(16);
                    three.channel().read(buffer);
                    buffer.flip();
                    System.out.println("third content="
                            + StandardCharsets.US_ASCII.decode(buffer));
                }
            }

            System.out.println("available after all closes="
                    + budget.available());
        } finally {
            Files.deleteIfExists(first);
            Files.deleteIfExists(second);
            Files.deleteIfExists(third);
            Files.deleteIfExists(dir);
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17+:

~~~bash
javac FileDescriptorBudgetDemo.java
java FileDescriptorBudgetDemo
~~~

Stdout trong môi trường đọc/ghi tệp tạm bình thường:

~~~text
available with two open=0
third rejected=true
available after second closes=1
third content=three
available after all closes=2
~~~

Diễn giải:

1. `Budget(2)` tạo hai permit: tối đa hai channel do **subsystem này** sở hữu được mở đồng thời.
2. Mở `one` lấy permit đầu; mở `two` lấy permit thứ hai. `available() == 0`.
3. Lần mở `third` tiếp theo bị từ chối **trước khi gọi FileChannel.open** vì hết permit, tránh cố chạm ngưỡng OS.
4. Khối try-with-resources của `two` kết thúc, `Lease.close()` đóng channel và trả một permit. `available() == 1` trong khi `one` vẫn mở.
5. Mở `three` dùng lại permit vừa được trả; đọc file `three.txt` và in `three`.
6. `three` rồi `one` được đóng; tất cả permit được trả về, `available() == 2`.
7. Nếu `FileChannel.open` thất bại, khối `finally` trong `Budget.open` cũng trả permit để không gây rò rỉ ngân sách.
8. Nếu thao tác trong khối try ném lỗi, try-with-resources vẫn gọi `close()`. Ví dụ dùng `boolean closed` đơn giản phù hợp khi mỗi `Lease` thuộc quyền sở hữu một luồng; trong hệ thống đa luồng có thể dùng `AtomicBoolean` nếu nhiều thread có khả năng đóng cùng lease.

**Giới hạn ví dụ:** permit không tương đương số lượng FD chính xác của OS. `available()==2` chỉ chứng minh không còn lease mở do wrapper này theo dõi, **không** khẳng định JVM không có file descriptor nào khác. Nếu `channel.close()` ném IOException, mã vẫn hoàn trả permit của ngân sách ứng dụng; ứng dụng production cần log/chẩn đoán lỗi close và không tự khẳng định tài nguyên OS đã giải phóng thành công trong mọi trường hợp.

## 11. Lỗi mở file thất bại giữa chừng: phải trả permit và đóng thứ đã mở

Khi job cần nhiều resource, các lỗi có thể xuất hiện ở bước thứ hai hoặc thứ ba:

~~~text
acquire permit A → open file A thành công
acquire permit B → open file B thất bại
                    |
                    └── nếu không finally:
                           permit B bị mất
                           file A có thể bị giữ mở
~~~

Một cách tổ chức tốt là **cấu trúc ownership theo lexical scope**:

~~~java
try (FileChannel input = FileChannel.open(inPath,
             StandardOpenOption.READ);
     FileChannel output = FileChannel.open(outPath,
             StandardOpenOption.CREATE,
             StandardOpenOption.WRITE)) {
    // Chuyển dữ liệu với vòng read/write kiểm tra số byte.
}
~~~

Đây là mảnh minh họa, có `Path inPath`/`outPath` hợp lệ. Nếu lần mở `output` thất bại, resource `input` đã mở sẽ được đóng tự động theo ngữ nghĩa try-with-resources.

Nếu job giữ channel trong queue tới lúc worker chạy thì việc đếm resource cần bao gồm **thời gian chờ trong queue**. Thường tốt hơn là đưa **Path**/ID vào queue, và chỉ mở channel **khi worker thực sự sẵn sàng xử lý**.

## 12. Giám sát số FD trên Linux

Trên Linux, một số nguồn quan sát hữu ích (cần quyền truy cập phù hợp):

~~~bash
# Xem soft/hard limit của shell đang chạy:
ulimit -Sn
ulimit -Hn

# Xem giới hạn được hệ điều hành công bố cho tiến trình:
cat /proc/<PID>/limits

# Liệt kê descriptor hiện đang mở của tiến trình:
ls -l /proc/<PID>/fd

# Đếm các entry trong /proc/<PID>/fd:
ls -1 /proc/<PID>/fd | wc -l

# Lệnh lsof (nếu được cài) để xem tài nguyên của tiến trình:
lsof -p <PID>
~~~

`<PID>` là placeholder cho process ID thực tế, không gõ nguyên chuỗi có dấu ngoặc nhọn vào shell. Những lệnh này là hướng dẫn cho Linux/Unix, **không chạy trực tiếp trên Windows PowerShell**.

Cẩn thận với phép đếm từ `/proc/<PID>/fd`: số lượng có thể thay đổi trong lúc đọc; permission hoặc container namespace có thể che một phần dữ liệu. Đếm bằng `lsof` và bằng procfs đôi khi cho kết quả khác do cách hiển thị resource và thời điểm quan sát. Dùng một nguồn metric nhất quán để xem **xu hướng**, không kết luận “không có leak” từ một snapshot duy nhất.

### Trên macOS và Windows

- **macOS/Unix:** `lsof -p PID` thường hữu ích để xem open files/sockets; giới hạn launch context có thể khác giữa shell và service.
- **Windows:** dùng Process Explorer, Resource Monitor hoặc công cụ giám sát handle của tiến trình. **Handle count** thường bao gồm cả loại handle khác file; đừng gọi số đó là “số file descriptor Unix”.
- **Trong container/service:** so sánh đúng process limit đang áp dụng, không chỉ cấu hình của interactive shell mà lập trình viên đang đăng nhập.

Khi xảy ra lỗi “Too many open files”, thu thập **thời điểm**, **PID**, **loại resource tăng**, **số resource đang mở**, **giới hạn của chính process** và **stack trace của thao tác thất bại** để khoanh vùng nguyên nhân.

## 13. Quan sát FD từ Java trên hệ thống Unix hỗ trợ

JDK có một extension monitoring bean trên các nền tảng hỗ trợ Unix, thường qua `com.sun.management.UnixOperatingSystemMXBean`:

~~~java
import java.lang.management.ManagementFactory;

public class FdMetricDemo {
    public static void main(String[] args) {
        java.lang.management.OperatingSystemMXBean os =
                ManagementFactory.getOperatingSystemMXBean();

        if (os instanceof com.sun.management.UnixOperatingSystemMXBean unix) {
            System.out.println("open FDs="
                    + unix.getOpenFileDescriptorCount());
            System.out.println("max FDs="
                    + unix.getMaxFileDescriptorCount());
        } else {
            System.out.println("Unix FD metric unavailable");
        }
    }
}
~~~

Đây là **chương trình Java 17+ chạy độc lập**, nhưng stdout phụ thuộc hệ điều hành và trạng thái tiến trình:

~~~bash
javac FdMetricDemo.java
java FdMetricDemo
~~~

Trên một runtime có hỗ trợ Unix bean, kết quả là hai con số động `open FDs=...` và `max FDs=...`. Trên platform không có extension đó, output là:

~~~text
Unix FD metric unavailable
~~~

Các phương thức `getOpenFileDescriptorCount()` và `getMaxFileDescriptorCount()` là extension JDK/platform, **không phải phần có thể giả định luôn tồn tại trên mọi Java implementation**. Ở một số nền tảng, metric có thể không khả dụng hoặc có cách diễn giải/giá trị đặc biệt theo implementation.

Đừng log hai con số mỗi microsecond chỉ để kiểm tra leak; sampling theo chu kỳ có mục tiêu, alert theo tỉ lệ `open/max` và theo xu hướng sau khi tải ngừng sẽ hữu ích hơn.

## 14. Làm sao phân biệt FD leak với tải đồng thời hợp lệ?

Giả sử monitor báo:

~~~text
Thời gian    Active requests    Open FDs
10:00             10               120
10:05            500               610
10:10             10               125
~~~

Số FD tăng khi tải tăng, rồi gần trở về baseline có thể là hành vi bình thường. Nhưng:

~~~text
Thời gian    Active requests    Open FDs
10:00             10               120
10:05            500               610
10:10             10               605
10:20             10               900
~~~

Nếu số tài nguyên tiếp tục cao hoặc tăng khi lưu lượng đã thấp, cần điều tra leak hoặc cache/pool mở quá rộng. Tuy nhiên, **chỉ ba số đo không tự chứng minh leak**: resource pooling, idle socket timeout hoặc startup behavior cũng có thể khiến baseline thay đổi. Phải kết hợp với stack traces, thông tin active resources và thiết kế ứng dụng.

Một quy trình:

1. Ghi nhận baseline sau startup và sau warm-up.
2. Chạy workload có mức đồng thời biết trước.
3. Đo active FD/handle và số channel/socket do ứng dụng quản lý.
4. Chờ job hoàn tất, theo dõi các resource có quay về mức kỳ vọng hay không.
5. Nếu không, khoanh vùng theo API (file, socket, directory stream, ZipFile...).
6. Dùng profiling, metrics ownership hoặc log tạm có stack mở/đóng với sampling để truy vị trí quên close.
7. Xác minh bằng bài test lặp nhiều vòng mà không phải tăng tải vô hạn.

Nếu cần instrument việc tạo resource, nên để một factory/wrapper hoặc resource manager chịu trách nhiệm cấp và đóng tài nguyên, tránh rải lệnh `FileChannel.open` trong hàng chục lớp mà không có nơi ghi nhận.

## 15. File descriptor có liên quan gì đến memory mapping?

`FileChannel.map(...)` tạo một `MappedByteBuffer` gắn với một vùng dữ liệu file thông qua cơ chế memory mapping. Đây là kỹ thuật hữu ích khi xử lý file lớn nhưng nó có giới hạn riêng.

Điều cần tránh:

- Không giả định mỗi mapping dùng đúng một FD suốt vòng đời; OS có cơ chế mapping khác nhau.
- `FileChannel.close()` **không bảo đảm** mapping tự biến mất ngay khi channel đóng.
- `MappedByteBuffer` không cung cấp `close()` chuẩn như `FileChannel`.
- Vòng đời mapping có thể liên quan đến GC, bộ nhớ địa chỉ, tài nguyên native và khả năng sửa/xóa tệp trên một số hệ điều hành.
- Mapping hàng nghìn vùng rất lớn có thể gây áp lực tài nguyên khác ngay cả khi không tăng open FD theo cách trực tiếp.

Nếu không thật sự cần random access mapping, streaming `FileChannel.read(ByteBuffer)` với buffer hữu hạn thường có vòng đời tài nguyên dễ kiểm soát hơn. Nếu cần mapping, giới hạn số vùng map đồng thời và thiết kế ownership/vòng đời rõ ràng; không ép gọi những API unmap nội bộ không được hỗ trợ mà không hiểu rủi ro.

## 16. Connection pools, Selector và giới hạn tài nguyên

### Một Selector quản lý nhiều SocketChannel

NIO Selector giúp một thread xử lý readiness của nhiều socket, cải thiện mô hình thread-per-connection trong một số workload. Tuy nhiên **mỗi SocketChannel vẫn có tài nguyên kết nối**. Đừng suy luận “một Selector nghĩa là chỉ một FD cho mọi client”.

Nên có các giới hạn:

- Số kết nối TCP đang hoạt động tối đa.
- Số kết nối pending/handshake đang chờ tối đa.
- Timeout cho kết nối idle và tác vụ không tiến triển.
- Queue outbound/backpressure per connection.
- Phạm vi nhận client mới khi nguồn lực gần ngưỡng.
- Đóng `SocketChannel` ở tất cả đường thoát, kể cả handshake lỗi, timeout hoặc `SelectionKey` bị cancel.

`key.cancel()` chỉ hủy đăng ký selection; nếu vòng đời socket kết thúc, chương trình còn phải đóng channel phù hợp. Không dùng cancel như sự thay thế mặc định cho close.

### Pool không phải leak, nhưng cũng có giới hạn

Một pool socket/file handle giữ resource mở để tái sử dụng có thể giảm chi phí open/close nhưng tiêu tốn FD lâu hơn. Pool cần max size, eviction, idle timeout và policy khi hệ thống quá tải.

Nếu mỗi tenant hoặc request tự tạo một pool riêng không có giới hạn tổng, sự cố trông giống resource leak ngay cả khi từng pool có cơ chế close đầy đủ trên lý thuyết.

## 17. Khi gặp Too many open files nên làm gì?

Một quy trình ứng phó an toàn:

1. **Giữ thông tin chẩn đoán**: stack trace của các thao tác mở thất bại, OS error, PID, FD count/limit, active requests, loại tài nguyên.
2. **Dừng/giảm việc tạo resource mới** nếu có thể: áp dụng backpressure, từ chối job mới, giới hạn accept/retry.
3. **Không retry vòng lặp nóng**: retry liên tục khi không có FD trống sẽ làm CPU và log pressure tăng.
4. **Xác định resource được giữ bởi ai**: thử tìm stream/channel/directory listing chưa close, socket idle, pool không giới hạn, watcher/selector chưa giải phóng.
5. **Sửa ownership**: try-with-resources, close ở failure path, factory/wrapper quản lý tài nguyên, timeout/eviction.
6. **Đo lại dưới tải đại diện**: open count có quay về baseline thích hợp, số lỗi có giảm, throughput ổn không.
7. **Chỉ điều chỉnh giới hạn OS khi thật sự cần**: cân nhắc mức concurrent workload, dự phòng hệ thống, container/service policy và khả năng tài nguyên khác.

Một kỹ thuật production nâng cao là giữ một ít headroom hoặc một descriptor dự phòng để có thể xử lý tình huống hết FD khi accept socket; cách làm cụ thể phụ thuộc OS và server architecture. Đây không phải phép sửa căn nguyên thay cho giới hạn admission và xử lý leak.

## 18. Những lỗi thường gặp

**Lỗi 1 — Nghĩ JVM có GC nên không cần close().** GC không cung cấp thời điểm đóng file/socket đáng tin. Dùng try-with-resources và ownership rõ ràng.

**Lỗi 2 — Đếm số FileChannel object để suy ra toàn bộ FD.** Một số tài nguyên có thể dùng chung, nằm ngoài Java heap hoặc thuộc các API khác. Dùng OS/JMX metric phù hợp.

**Lỗi 3 — Mở stream Files.list/Files.walk/Files.lines mà không đóng.** Đây là filesystem-backed stream; phải try-with-resources.

**Lỗi 4 — Mở hết hàng chục nghìn file cùng lúc rồi mới xử lý.** Chạm giới hạn FD và tăng áp lực hệ thống. Giới hạn concurrency, mở resource sát lúc dùng.

**Lỗi 5 — Tăng ulimit là xong.** Với leak, chỉ kéo dài thời gian tới khi lỗi. Trước tiên sửa ownership/close và đo lại.

**Lỗi 6 — Không trả permit khi FileChannel.open thất bại.** Semaphore mất permit dần, tự làm nghẽn ứng dụng dù OS còn tài nguyên.

**Lỗi 7 — Dùng semaphore nhưng code khác mở FD ngoài budget không kiểm soát.** Budget chỉ bảo vệ subsystem đi qua nó; phải dành headroom cho socket, logger, thư viện và hệ thống.

**Lỗi 8 — Chỉ đóng khi xử lý thành công.** Exception path là nơi phổ biến bị leak. Dùng finally/try-with-resources.

**Lỗi 9 — Đóng FileChannel rồi kết luận mọi MappedByteBuffer đã unmap.** Mapping có vòng đời riêng, không được kiểm soát chỉ bằng việc channel đóng.

**Lỗi 10 — Chỉ dựa vào handle count trên Windows rồi cho rằng đó là số Unix FD.** Handle count bao gồm nhiều loại tài nguyên, cần giải thích metric đúng nền tảng.

**Lỗi 11 — Cancel SelectionKey nhưng quên close channel.** Cancel chỉ thay đăng ký với selector; channel có thể vẫn mở.

**Lỗi 12 — Retry liên tục ngay khi EMFILE.** Retry không giới hạn làm tăng tải/log mà không giải phóng FD. Cần điều phối admission, backoff và khắc phục resource owner.

## 19. Câu hỏi tự kiểm tra

1. File descriptor là gì? **Tham chiếu số nguyên của tiến trình tới tài nguyên I/O trên Unix/Linux**, với chi tiết quản lý do OS thực hiện.
2. Mọi `FileChannel` có tương ứng đúng một FD không? **Không thể khẳng định chung cho mọi provider/implementation**.
3. `EMFILE` khác `ENFILE` thế nào? **EMFILE thường liên quan giới hạn descriptor của tiến trình; ENFILE liên quan giới hạn tài nguyên file-open rộng hơn ở cấp hệ thống**.
4. Vì sao tạo một `Selector` không làm vô hạn số socket có thể mở? **Mỗi kết nối vẫn sử dụng tài nguyên OS**, Selector chủ yếu thay cách theo dõi readiness.
5. `Files.list` có cần đóng stream trả về không? **Có**, vì nó dựa trên tài nguyên thư mục.
6. Dùng `Files.readString` có trả ra InputStream phải tự close không? **Không**; phương thức trả về String và tự quản lý resource nội bộ.
7. Tại sao `try-with-resources` tốt hơn close() chỉ ở dòng cuối hàm? **Resource được đóng trên các đường thoát có ngoại lệ**, và lỗi close được lưu phù hợp.
8. Budget semaphore có nâng `RLIMIT_NOFILE` không? **Không**; nó giới hạn số resource do ứng dụng tự cấp qua subsystem.
9. Nếu lấy permit rồi `FileChannel.open` thất bại, phải làm gì? **Trả permit trong finally/catch**.
10. Số FD tăng từ 100 lên 600 khi tải tăng rồi trở về 110 khi tải giảm có tự chứng minh leak không? **Không**; có thể là tải đồng thời hợp lệ.
11. `FileChannel.close()` có luôn unmap ngay mọi `MappedByteBuffer` không? **Không có bảo đảm đó**.
12. Trên Linux, muốn biết soft limit của **JVM đang chạy** thì chỉ nhìn `ulimit -Sn` trong shell khác có đủ không? **Không**; cần xem giới hạn của chính process hoặc service context.
13. Vì sao xử lý 10.000 file theo batch nên giới hạn số channel mở đồng thời? **Để không chạm giới hạn FD, kiểm soát áp lực OS và đạt throughput có thể dự đoán**.
14. Chạm giới hạn FD trong một server có thể ảnh hưởng logger/healthcheck không? **Có**, vì những thành phần đó cũng có thể cần mở file hoặc socket.
15. Lựa chọn ưu tiên khi ứng dụng thường xuyên báo “Too many open files” là gì? **Đo FD usage, tìm resource bị giữ quá lâu/không đóng, giới hạn concurrency và chỉ sau đó cân nhắc tăng OS limit**.

**Ghi nhớ:** file descriptor/handle là tài nguyên hữu hạn của hệ điều hành. Khi xử lý file lớn hoặc phục vụ nhiều kết nối đồng thời, ứng dụng Java phải **quản lý vòng đời bằng try-with-resources**, đóng cả các filesystem-backed streams, giới hạn số resource mở đồng thời, chừa headroom cho thành phần khác và theo dõi FD theo đúng môi trường. **Tăng giới hạn OS không thay thế sửa leak hoặc thiết kế concurrency có kiểm soát.**


</details>

- [Quay lại đầu trang](#back-to-top)
