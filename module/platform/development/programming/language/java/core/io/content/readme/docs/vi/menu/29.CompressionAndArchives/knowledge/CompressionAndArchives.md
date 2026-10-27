<a id="back-to-top"></a>

# Nén và đóng gói

## Menu
- [Phân biệt nén GZIP với archive ZIP](#compression-and-archives-001)
- [GZIPInputStream và GZIPOutputStream](#compression-and-archives-002)
- [ZipInputStream, ZipOutputStream và ZipEntry](#compression-and-archives-003)
- [putNextEntry, closeEntry và hoàn tất archive](#compression-and-archives-004)
- [finish khác flush và close](#compression-and-archives-005)
- [ZipFile truy cập từng entry và quản lý vòng đời](#compression-and-archives-006)
- [Charset tên entry và metadata của archive](#compression-and-archives-007)
- [JarFile, JarInputStream và manifest ở mức I/O](#compression-and-archives-008)
- [Deflater, Inflater và phạm vi điều khiển mức nén](#compression-and-archives-009)
- [CRC32, Adler32 và giới hạn của checksum](#compression-and-archives-010)
- [Giới hạn dung lượng, số entry và tỷ lệ giải nén](#compression-and-archives-011)
- [Giải nén đúng thư mục đích và xử lý đường dẫn entry](#compression-and-archives-012)

## <a id="compression-and-archives-001">Phân biệt nén GZIP với archive ZIP</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt nén GZIP với archive ZIP trong Java I/O

## 1. Hai tên gần giống nhưng giải quyết hai bài toán khác nhau

**GZIP** và **ZIP** đều xuất hiện trong Java qua package `java.util.zip`, đều có thể dùng thuật toán nén **DEFLATE**, nhưng **không cùng định dạng** và **không có chung cấu trúc dữ liệu**.

- **GZIP (`.gz`):** định dạng **nén một luồng byte**. `GZIPOutputStream` nhận các byte và tạo luồng GZIP; `GZIPInputStream` đọc luồng GZIP, trả lại byte chưa nén. Nó không xây danh sách file/thư mục theo entry.
- **ZIP (`.zip`):** định dạng **archive/container** có thể chứa nhiều **entry** — tên file, đường dẫn tương đối, metadata và dữ liệu — trong một tập tin ZIP. `ZipOutputStream` tạo entry; `ZipInputStream` duyệt entry tuần tự; `ZipFile` tìm và đọc entry từ file archive.

Nói ngắn gọn: **GZIP nén nội dung của một luồng; ZIP đóng gói nhiều mục có tên và có thể nén riêng từng mục.**

Ví dụ sử dụng:

~~~text
report.txt
   |
   +-- GZIP --> report.txt.gz
                 (một luồng chứa dữ liệu report.txt đã nén)

docs/a.txt ----+
docs/b.txt ----+-- ZIP --> docs.zip
images/x.png --+             |
                             +-- docs/a.txt
                             +-- docs/b.txt
                             +-- images/x.png
~~~

Tên `report.txt.gz` là quy ước đặt tên bên ngoài; bản thân API `GZIPOutputStream` **không tạo file entry `report.txt` có thể duyệt như ZIP**. Bản GZIP không tự lưu cây thư mục mà chương trình giải nén dùng để tái tạo nhiều file.

## 2. Bảng so sánh tổng quan

| Tiêu chí | GZIP | ZIP |
|---|---|---|
| Mục đích | Nén **luồng dữ liệu** | **Đóng gói file/thư mục** vào archive, thường kết hợp nén |
| Nhiều file có tên riêng? | Không theo mô hình `GZIPInputStream` | Có, mỗi mục là một `ZipEntry` |
| Giữ cấu trúc thư mục | Không trực tiếp | Có thông qua tên entry/đường dẫn và metadata tương ứng |
| Kiểu dữ liệu ở Java | `GZIPInputStream`, `GZIPOutputStream` | `ZipInputStream`, `ZipOutputStream`, `ZipFile`, `ZipEntry` |
| Nén | Thường dùng DEFLATE bên trong GZIP wrapper | Entry có thể `DEFLATED` hoặc `STORED`; ZIP format có nhiều phương thức khác |
| Đọc theo thứ tự | Tự nhiên là đọc một stream | `ZipInputStream` đọc từng entry tuần tự |
| Tìm entry bằng tên | Không có entry/file index | `ZipFile.getEntry(name)` |
| Metadata riêng từng file | Không có `ZipEntry` | Tên, kích thước, CRC-32, thời gian, comment, extra fields tùy entry |
| Cấu trúc kết thúc | Trailer CRC-32 và kích thước nội dung (modulo 2³²) | Central directory / end-of-central-directory của archive |
| Đuôi thường gặp | `.gz`, `.tgz`, `.tar.gz` | `.zip`, `.jar` |
| Phù hợp | Nén log, response body, một luồng lớn | Gói nhiều tài liệu, backup/archive, thư mục tải về |
| Bảo mật dữ liệu | Không mặc nhiên mã hóa/xác thực | Không mặc nhiên mã hóa/xác thực |

**Lưu ý:** một số ứng dụng có thể ghép nhiều **GZIP members** liên tiếp thành một luồng GZIP hợp lệ. Điều đó **không biến GZIP thành ZIP archive**: vẫn không có directory các entry, không có API tìm file theo tên như `ZipFile`.

## 3. Cấu trúc định dạng: GZIP wrapper và ZIP archive

### 3.1. GZIP

Một GZIP member thông thường được mô tả:

~~~text
+--------------------+
| GZIP header        |  magic bytes 1F 8B, flags, method, ...
+--------------------+
| Compressed payload |  DEFLATE-compressed bytes
+--------------------+
| GZIP trailer       |  CRC-32 + ISIZE (size mod 2^32)
+--------------------+
~~~

Phần header/trailer tạo **GZIP wrapper** xung quanh nội dung nén, giúp bên đọc biết cách nhận diện định dạng và kiểm tra checksum. GZIP không có danh sách file mang tên `docs/a.txt`, `docs/b.txt` như ZIP central directory. Định dạng GZIP có thể có một số optional header fields, nhưng không nên đồng nhất chúng với hệ thống entry của ZIP.

**CRC-32 là cơ chế kiểm tra lỗi vô tình, không phải mật mã xác thực**. Nếu file bị chỉnh sửa có chủ ý, checksum không cung cấp đảm bảo chống giả mạo.

### 3.2. ZIP

ZIP thường chứa nhiều record entry và một **central directory** gần cuối archive:

~~~text
+------------------------------+
| Local header: docs/a.txt     |
| Compressed data: a.txt       |
+------------------------------+
| Local header: docs/b.txt     |
| Compressed data: b.txt       |
+------------------------------+
| ...                          |
+------------------------------+
| Central directory            |
|   - tên và metadata entries  |
|   - offsets tới local records|
+------------------------------+
| End-of-central-directory     |
+------------------------------+
~~~

Mỗi entry có thể được **DEFLATED** hoặc **STORED** (không nén), tùy cấu hình. Một `ZipOutputStream` chưa được `finish()`/`close()` đầy đủ có thể chưa ghi xong directory, nên archive có thể không sử dụng được dù một số dữ liệu entry đã được xuất ra.

### 3.3. Magic bytes để nhận diện sơ bộ

- GZIP thường mở đầu bằng `1F 8B` (hex).
- ZIP có local file header thường mở đầu bằng `50 4B 03 04`, biểu diễn `PK` và phần còn lại. Một file ZIP rỗng có thể bắt đầu bằng end-of-central-directory signature khác.

**Không dùng magic bytes như một phép xác thực đầy đủ.** Chúng chỉ là kiểm tra định dạng sơ bộ; nội dung vẫn có thể bị lỗi, thiếu, giả mạo hoặc không tương thích.

## 4. Java API tương ứng: cùng package nhưng khác semantics

Package `java.util.zip` có các lớp sau:

| API | Vai trò |
|---|---|
| `GZIPOutputStream` | Nén các byte ghi vào thành một GZIP stream |
| `GZIPInputStream` | Giải nén một GZIP input stream thành byte |
| `ZipOutputStream` | Ghi ZIP archive với một hoặc nhiều `ZipEntry` |
| `ZipInputStream` | Đọc lần lượt các `ZipEntry` từ stream ZIP |
| `ZipFile` | Mở một file ZIP để liệt kê/tìm entry và mở stream đọc entry |
| `ZipEntry` | Đại diện một entry có tên/metadata trong ZIP |
| `Deflater`/`Inflater` | Nén/giải nén ở lớp thuật toán thấp hơn |
| `CRC32` | Tính checksum CRC-32 |

Không dùng `GZIPInputStream` để giải nén trực tiếp `.zip` và không dùng `ZipInputStream` để đọc trực tiếp `.gz`. Dù cả hai dùng DEFLATE trong nhiều trường hợp, **wrapper/container khác nhau**.

### ZIP có thể không nén

Theo Java 17, `ZipEntry` có `DEFLATED` và `STORED`:

~~~java
ZipEntry entry = new ZipEntry("image.png");
entry.setMethod(ZipEntry.STORED);
~~~

Đây là **mã minh họa, không độc lập**. Với `STORED`, `ZipOutputStream` yêu cầu đặt đúng size, compressed size và CRC trước khi `putNextEntry`; chỉ gọi `setMethod(STORED)` như trên rồi viết mà chưa có metadata sẽ gây lỗi. `DEFLATED` là phương thức mặc định của `ZipOutputStream` và thường tự tính metadata cần thiết sau khi ghi entry.

Vì vậy **ZIP không đồng nghĩa tất cả entry đều nén**; một archive có thể có entry lưu nguyên byte, hợp lý với ảnh/video đã nén sẵn.

## 5. GZIP trong Java: nén và giải nén một nội dung

Một chuỗi thao tác thông thường:

~~~java
// Tạo .gz từ một luồng byte:
try (InputStream in = Files.newInputStream(source);
     OutputStream out = new GZIPOutputStream(
             Files.newOutputStream(gzipPath))) {
    in.transferTo(out);
}

// Giải nén .gz về một luồng byte:
try (InputStream in = new GZIPInputStream(
             Files.newInputStream(gzipPath));
     OutputStream out = Files.newOutputStream(restoredPath)) {
    in.transferTo(out);
}
~~~

Đây là **mã minh họa trong một phương thức**, với `Path source`, `Path gzipPath`, `Path restoredPath` đã tồn tại và các import phù hợp. Nó xử lý **nội dung của một file**, không duyệt cây thư mục hay tự tạo những file con theo tên trong compressed stream.

**Tại sao cần `try-with-resources`?** `GZIPOutputStream` phải ghi đủ dữ liệu nén và trailer khi `finish()`/`close()`; đóng stream còn giải phóng tài nguyên của stream bên dưới. Nếu đọc `gzipPath` khi writer chưa finish/close, có thể gặp luồng chưa đầy đủ.

### `finish()` khác `flush()`

`GZIPOutputStream.finish()` hoàn tất GZIP stream nhưng **không đóng underlying OutputStream**. `close()` sẽ đóng filter và underlying stream. `flush()` không thay thế `finish()`: tùy cấu hình `syncFlush`, flush có thể chỉ đẩy output buffer hoặc yêu cầu `SYNC_FLUSH`, nhưng chưa nhất thiết ghi trailer kết thúc member.

Trong pipeline bình thường, **đóng GZIPOutputStream** ở cuối scope là lựa chọn đơn giản nhất; chỉ cần gọi `finish()` thủ công khi muốn hoàn tất GZIP mà còn giữ underlying stream cho tác vụ khác.

### Một file `.tar.gz` chứa nhiều file vì sao?

**TAR là archive; GZIP là nén.** Luồng xử lý thường là:

~~~text
docs/a.txt + docs/b.txt + dirs
        |
        v
TAR: đóng gói nhiều file/metadata thành một byte stream
        |
        v
GZIP: nén byte stream TAR
        |
        v
backup.tar.gz
~~~

Khi giải nén `.tar.gz`, ta phải **GZIP-decompress trước**, rồi dùng **TAR reader** để lấy từng file. Java standard library (`java.util.zip`) có GZIP và ZIP nhưng **không cung cấp TAR parser/writer chuyên dụng**. Cần thư viện ngoài hoặc công cụ TAR phù hợp nếu muốn đọc TAR entries. Đặt đuôi `.tar.gz` không biến `GZIPInputStream` thành TAR parser.

## 6. ZIP trong Java: tạo nhiều entry

Muốn tạo ZIP bằng `ZipOutputStream`, mỗi file phải được đặt thành một `ZipEntry`:

~~~java
try (ZipOutputStream zip = new ZipOutputStream(
        Files.newOutputStream(zipPath), StandardCharsets.UTF_8)) {

    zip.putNextEntry(new ZipEntry("docs/a.txt"));
    Files.copy(pathA, zip);
    zip.closeEntry();

    zip.putNextEntry(new ZipEntry("docs/b.txt"));
    Files.copy(pathB, zip);
    zip.closeEntry();
}
~~~

Đây là **đoạn mã minh họa** với `Path zipPath`, `pathA`, `pathB` hợp lệ và import tương ứng. Các tên `docs/a.txt`/`docs/b.txt` trong ZIP là **đường dẫn entry**, không yêu cầu source files nằm trong một cây thư mục cùng tên.

Thứ tự:

1. `putNextEntry(entry)` mở entry mới.
2. `write(...)` hoặc `Files.copy(path, zip)` ghi byte của entry đó.
3. `closeEntry()` kết thúc entry hiện tại và chuẩn bị entry kế.
4. `close()`/`finish()` hoàn tất archive, bao gồm metadata directory.

Với entry thư mục, quy ước phổ biến là thêm dấu `/` cuối tên, ví dụ `docs/`, và không ghi dữ liệu như một file thường. Các tên entry nên dùng dấu `/` và đường dẫn **tương đối**, phù hợp tính di động.

### Metadata entry

`ZipEntry` hỗ trợ `getName()`, `isDirectory()`, `getSize()`, `getCompressedSize()`, `getCrc()`, `getTime()`, `getComment()` và các phương thức set tương ứng. Một số giá trị có thể là **-1** hoặc `null` nếu không biết. Với `ZipInputStream` đọc tuần tự, không được giả định toàn bộ metadata có sẵn từ local header tại thời điểm `getNextEntry()`.

Không suy luận nội dung file từ mỗi `ZipEntry.getName()` là đã được kiểm tra an toàn. Tên entry là **dữ liệu do archive cung cấp**, cần xác thực trước khi dùng làm file path.

## 7. ZIP: `ZipInputStream` khác `ZipFile`

Cả hai dùng đọc ZIP nhưng có khả năng khác:

### 7.1. ZipInputStream — đọc tuần tự

~~~java
try (ZipInputStream in = new ZipInputStream(
        Files.newInputStream(zipPath), StandardCharsets.UTF_8)) {

    ZipEntry entry;
    while ((entry = in.getNextEntry()) != null) {
        System.out.println("entry=" + entry.getName());

        // Đọc byte từ entry hiện tại tới EOF của entry.
        byte[] bytes = in.readAllBytes();
        // Chỉ dùng readAllBytes với entry nhỏ, đáng tin.
        in.closeEntry();
    }
}
~~~

Đoạn minh họa có `Path zipPath`, import thích hợp. `getNextEntry()` chuyển đến entry tiếp theo và trả `null` khi hết. `read(...) == -1` thể hiện **hết dữ liệu của entry đang đọc**, chưa chắc đã hết cả archive. Không dùng `available()` để xác định kích thước còn lại: với `ZipInputStream`, giá trị thường là 1 trước EOF entry và 0 sau EOF, **không phải số byte chưa đọc**.

Phù hợp khi đầu vào là **InputStream**, ví dụ upload ZIP qua network, hoặc muốn duyệt lần lượt mọi entry. Nó không phải API tra cứu ngẫu nhiên một entry theo tên từ trung tâm archive.

### 7.2. ZipFile — tra cứu và mở stream cho entry

~~~java
try (ZipFile zip = new ZipFile(zipPath.toFile(),
                              StandardCharsets.UTF_8)) {
    ZipEntry entry = zip.getEntry("docs/a.txt");
    if (entry != null) {
        try (InputStream in = zip.getInputStream(entry)) {
            // Đọc riêng nội dung docs/a.txt.
        }
    }
}
~~~

Đoạn minh họa có `Path zipPath`. `ZipFile` đọc metadata archive để tìm entry, gọi `getEntry(name)`, `entries()` hay `stream()`, và mở `InputStream` cho từng entry được chọn. Nó thường phù hợp nếu cần **chọn file theo tên** hoặc cần duyệt danh sách entry trong archive trên đĩa.

Đóng `ZipFile` cũng đóng các `InputStream` đã lấy qua `getInputStream(entry)`. Không giữ các stream đó để đọc sau khi `ZipFile` đã đóng. Trong thực tế, `ZipInputStream` duyệt các local entry header, còn `ZipFile` dùng central directory: hai cách có thể thể hiện **metadata khác nhau với archive được chế tạo bất thường**, cần chú ý khi kiểm tra archive không tin cậy.

### Chọn API nào?

| Đầu vào và nhu cầu | Phương án |
|---|---|
| Một luồng bytes GZIP | `GZIPInputStream` |
| Ghi một luồng GZIP | `GZIPOutputStream` |
| Nhận ZIP từ stream/upload và duyệt entry tuần tự | `ZipInputStream` |
| Tạo ZIP nhiều file | `ZipOutputStream` |
| Đọc file ZIP trên đĩa, cần lấy entry theo tên | `ZipFile` |
| Muốn xem ZIP như file system để duyệt `Path` | ZIP file system provider nếu môi trường có `jdk.zipfs` |

Không dùng `ZipFile` trực tiếp với một `InputStream` thuần túy; nó nhận tên file/`File` (và các overload liên quan), nên đầu vào mạng thường cần stream parse hoặc lưu tạm một file tùy nhu cầu.

## 8. Demo Java 17+ hoàn chỉnh: hai định dạng cạnh nhau

Để dễ chạy trên mọi máy có Java 17, ví dụ làm việc **trong bộ nhớ**, không cần tạo file/thư mục trên đĩa. Nó tạo và giải nén GZIP từ một payload, rồi tạo ZIP gồm hai file named entry và đọc chúng lại.

**Tên tệp: `GzipZipDemo.java`**

~~~java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class GzipZipDemo {
    public static void main(String[] args) throws Exception {
        byte[] payload =
                "Xin chao NIO".getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream gzipData = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(gzipData)) {
            gzip.write(payload);
        }

        try (GZIPInputStream gzip = new GZIPInputStream(
                new ByteArrayInputStream(gzipData.toByteArray()))) {
            System.out.println("gzip=" +
                    new String(gzip.readAllBytes(), StandardCharsets.UTF_8));
        }

        System.out.printf("gzip magic=%02X %02X%n",
                gzipData.toByteArray()[0] & 0xFF,
                gzipData.toByteArray()[1] & 0xFF);

        ByteArrayOutputStream zipData = new ByteArrayOutputStream();
        try (ZipOutputStream zip =
                new ZipOutputStream(zipData, StandardCharsets.UTF_8)) {

            zip.putNextEntry(new ZipEntry("docs/a.txt"));
            zip.write("Alpha".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();

            zip.putNextEntry(new ZipEntry("docs/b.txt"));
            zip.write("Beta".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        try (ZipInputStream zip = new ZipInputStream(
                new ByteArrayInputStream(zipData.toByteArray()),
                StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String content = new String(zip.readAllBytes(),
                        StandardCharsets.UTF_8);
                System.out.println(entry.getName() + "=" + content);
                zip.closeEntry();
            }
        }

        byte[] zipBytes = zipData.toByteArray();
        System.out.printf("zip magic=%02X %02X %02X %02X%n",
                zipBytes[0] & 0xFF, zipBytes[1] & 0xFF,
                zipBytes[2] & 0xFF, zipBytes[3] & 0xFF);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac GzipZipDemo.java
java GzipZipDemo
~~~

**Stdout đã kiểm chứng bằng JDK:**

~~~text
gzip=Xin chao NIO
gzip magic=1F 8B
docs/a.txt=Alpha
docs/b.txt=Beta
zip magic=50 4B 03 04
~~~

Giải thích:

1. `GZIPOutputStream` chỉ nhận `payload` — một luồng nội dung; `GZIPInputStream` trả lại bytes của `Xin chao NIO`. Không có `ZipEntry` hay tên file bên trong dữ liệu ví dụ GZIP.
2. `ZipOutputStream` dùng `putNextEntry` để tạo **hai file mang tên riêng** trong archive; mỗi entry có dữ liệu và ranh giới.
3. `ZipInputStream.getNextEntry()` lần lượt trả `docs/a.txt` và `docs/b.txt`. `readAllBytes()` ở đây an toàn cho demo vì **chúng ta tự tạo dữ liệu rất nhỏ**; không áp dụng trực tiếp cho upload không đáng tin.
4. `closeEntry()` hoàn tất đọc/ghi entry theo vị trí hiện tại. `try-with-resources` hoàn tất GZIP/ZIP stream và đóng tài nguyên.
5. Magic bytes khác nhau (`1F 8B` vs `50 4B 03 04`) cho thấy không thể coi cả hai là cùng định dạng chỉ vì đều dùng DEFLATE.

## 9. Định dạng nào nén tốt hơn?

Không có đáp án tuyệt đối. Cả GZIP và ZIP thường dùng DEFLATE, nhưng **đơn vị nén và metadata** khác nhau:

- GZIP nén **một stream liên tục**; nếu kết hợp nhiều file bằng TAR rồi GZIP, compressor có thể tận dụng sự lặp lại dữ liệu gần nhau trên stream (trong giới hạn cửa sổ thuật toán).
- ZIP thường nén **mỗi entry riêng**, nên dễ duyệt hoặc lấy riêng entry, nhưng không tự dùng lịch sử dữ liệu của một entry khác cho entry hiện tại.
- ZIP phải lưu local headers, tên file và central directory, nên archive gồm rất nhiều file nhỏ có thể có overhead đáng kể.
- GZIP cũng có header/trailer; với dữ liệu rất nhỏ hoặc đã nén sẵn (PNG, JPEG, MP4…), kích thước sau nén có thể bằng hoặc **lớn hơn** ban đầu.
- Kết quả phụ thuộc compressor level, loại dữ liệu, kích thước, số lượng file và khả năng nén riêng từng file. So sánh **cùng tập dữ liệu và cùng yêu cầu chức năng** mới có ý nghĩa.

Đừng so `data.gz` nén một file với `bundle.zip` gồm nhiều file rồi kết luận thuật toán nào tốt hơn chỉ dựa vào kích thước.

### Chọn định dạng theo use case

**Chọn GZIP** nếu đầu vào đã là **một stream**: log stream, file text đơn lẻ, HTTP response content coding `gzip`, backup TAR đã đóng gói. Với HTTP `Content-Encoding: gzip`, response body được nén dưới dạng một luồng dữ liệu HTTP; header encoding không có ý rằng body trở thành ZIP archive chứa files.

**Chọn ZIP** nếu người dùng cần tải về **nhiều tệp**, giữ tên/đường dẫn và giải nén riêng từng tệp, hoặc cần API tìm một entry bằng tên. File `.jar` cũng dựa trên định dạng ZIP, bổ sung cấu trúc `META-INF` và semantics Java archive.

Khi yêu cầu **streaming xử lý** nhiều entry ngay từ network, `ZipInputStream` có thể thuận tiện; khi cần **random lookup** trên file đã lưu, `ZipFile` thường thích hợp hơn.

## 10. Charset và tên entry

Tên `ZipEntry` là **vấn đề riêng** với byte nội dung file:

- `new ZipOutputStream(out, StandardCharsets.UTF_8)` mã hóa tên entry theo UTF-8 nếu phù hợp.
- `new ZipInputStream(in, StandardCharsets.UTF_8)`/`new ZipFile(file, StandardCharsets.UTF_8)` giải mã tên entry theo charset đã chọn, nhưng nếu ZIP **language encoding flag** báo UTF-8 thì Javadoc áp dụng UTF-8 bất kể charset tùy chọn.
- Nội dung `docs/a.txt` có thể là UTF-8, UTF-16 hoặc dữ liệu nhị phân, **không bị quyết định** bởi charset truyền cho `ZipInputStream`.

Ví dụ tên entry `"tiếng-việt.txt"` có thể được mã hóa UTF-8, nhưng byte nội dung phải tự giải mã theo charset của file đó.

Đừng coi `ZipEntry.getName()` là chuỗi path an toàn chỉ vì đã giải mã đúng Unicode. Vẫn phải xác thực path trước khi giải nén xuống filesystem.

## 11. An toàn khi giải nén ZIP/GZIP từ nguồn không tin cậy

### 11.1. Zip Slip — đường dẫn traversal

Archive có thể cố tình chứa entry như `../../outside.txt` hoặc tên tuyệt đối `/tmp/target`. Nếu dùng trực tiếp `outputRoot.resolve(entry.getName())` mà không kiểm tra, chương trình có thể ghi **ra ngoài thư mục dự kiến**.

Kiểm tra cơ bản:

~~~java
Path root = outputDir.toAbsolutePath().normalize();
Path target = root.resolve(entry.getName()).normalize();

if (!target.startsWith(root) || target.equals(root)) {
    throw new IOException("Unsafe ZIP entry: " + entry.getName());
}
~~~

Đây là **đoạn mã minh họa**, biến `outputDir` và `ZipEntry entry` phải tồn tại. Kiểm tra này là **lớp bảo vệ cơ bản**, không phải toàn bộ giải pháp chống Zip Slip trên mọi OS. Phải cấm entry path tuyệt đối/drive prefix theo chính sách, kiểm tra symlink/hardlink/path components trong thư mục đích, tránh các thao tác tạo file race và dùng cơ chế filesystem an toàn. Với archive không đáng tin, nên giải nén vào thư mục riêng do ứng dụng kiểm soát, có chính sách chống symlink và giới hạn ghi.

### 11.2. Zip bomb/GZIP bomb

Một luồng nén nhỏ có thể bung thành dữ liệu cực lớn. Với ZIP còn có thể chứa **rất nhiều entry** làm cạn CPU, RAM hoặc storage.

Cần giới hạn:

1. **Tổng số byte giải nén** cho cả archive.
2. **Số byte giải nén tối đa trên mỗi entry**.
3. **Số lượng entry**, độ dài tên/path và độ sâu thư mục.
4. **Thời gian xử lý/deadline**, quota disk và memory.
5. Khi thích hợp, **tỷ lệ nén bất thường**, nhưng không chỉ tin vào ratio hoặc metadata khai báo.
6. Số byte đếm được **thực sự trong lúc stream đọc**, vì `ZipEntry.getSize()` có thể là `-1` hoặc dữ liệu khai báo không đáng tin.

Dùng `readAllBytes()` trên upload nén không có giới hạn có thể làm hết heap. Thay vào đó, đọc theo chunk cố định với `read(byte[])`, kiểm đếm rồi mới ghi ra output. Nguyên tắc này đúng cả với `GZIPInputStream` và `ZipInputStream`.

### 11.3. CRC-32 không xác thực nguồn

CRC-32 trong GZIP/ZIP dùng phát hiện lỗi dữ liệu truyền/lưu trữ; **không chống giả mạo có chủ đích**. Khi yêu cầu bảo đảm file đến từ nguồn đáng tin và chưa bị sửa có chủ đích, dùng cơ chế chữ ký số/MAC hoặc checksum cryptographic đã được xác thực bằng kênh tin cậy khác. ZIP encryption không có nghĩa `java.util.zip` cung cấp sẵn API password giải mã mọi ZIP có mật khẩu; JDK 17 standard ZIP API không phải thư viện encryption ZIP tổng quát.

### 11.4. Đọc/ghi đúng tài nguyên

- Dùng `try-with-resources` để close các stream và `ZipFile`.
- Nếu tạo ZIP/GZIP và cần đọc output ngay, hoàn tất `close()`/`finish()` trước.
- Tránh ghi archive dưới cùng path của tệp nguồn mà bạn đang đọc; có thể làm hỏng dữ liệu.
- Với ZIP streaming, đóng entry bằng `closeEntry()` đúng lúc.
- Với ZIP entry có metadata thiếu, **không tự tin** vào kích thước để cấp phát mảng lớn.

## 12. Lỗi dễ gặp

1. **Cho rằng ZIP = GZIP chỉ khác phần mở rộng.** Sai: định dạng và cấu trúc khác.
2. **Dùng `GZIPInputStream` để đọc `.zip`.** Sai: GZIP wrapper không phải ZIP archive.
3. **Dùng `ZipInputStream` để đọc `.gz`.** Sai: một GZIP stream không có các local entry ZIP.
4. **Cho rằng mọi ZIP entry đều bị nén.** Sai: `STORED` lưu nội dung không nén.
5. **Cho rằng GZIP tự giữ nhiều file và tên thư mục.** Sai: GZIP là nén stream; muốn nhiều file thường dùng TAR trước.
6. **Nhầm `.tar.gz` là ZIP.** Sai: TAR đóng gói, GZIP nén TAR.
7. **Quên `putNextEntry()` trước khi ghi byte vào ZIP.** Sai thứ tự cấu trúc entry.
8. **Quên finish/close ZIP output rồi cố đọc ngay.** Archive có thể thiếu central directory.
9. **Cho rằng `ZipInputStream.read()==-1` tức hết cả archive.** Sai: thường là EOF của entry hiện tại.
10. **Dùng `available()` trên ZipInputStream để tính byte còn lại.** Sai: không phải remaining byte count.
11. **Cho rằng charset entry name là charset file content.** Sai: metadata tên và payload là hai việc khác.
12. **Giải nén `entry.getName()` không kiểm tra.** Dễ có Zip Slip/path traversal.
13. **Dùng `readAllBytes()` trên archive không tin cậy.** Dễ gây OOM/zip bomb.
14. **Coi CRC-32 là bảo mật hoặc chữ ký số.** Sai: checksum không bảo đảm tính xác thực.
15. **Cho rằng `ZipFile` nhận tùy ý InputStream.** Sai: `ZipFile` đọc file ZIP; với InputStream dùng `ZipInputStream`.
16. **Cho rằng file đã nén sẵn chắc chắn nhỏ thêm khi gzip/zip.** Sai: header và overhead có thể làm kích thước tăng.

## 13. Tự kiểm tra kiến thức

**Câu 1.** Cần nén riêng một file log để truyền trên HTTP, không cần giữ nhiều file. Chọn gì?

**Đáp án:** GZIP là lựa chọn tự nhiên cho stream; `GZIPOutputStream` hoặc HTTP `Content-Encoding: gzip` theo giao thức phù hợp.

**Câu 2.** Cần gửi cho người dùng thư mục chứa 30 file, khi giải nén cần giữ tên và cấu trúc. Chọn gì?

**Đáp án:** ZIP archive với `ZipOutputStream` tạo `ZipEntry` cho từng file/thư mục (hoặc TAR rồi GZIP nếu hệ sinh thái phù hợp), không dùng GZIP trực tiếp để ghi 30 file có tên riêng.

**Câu 3.** Tại sao `.tar.gz` có nhiều file dù GZIP nén một stream?

**Đáp án:** TAR đóng gói nhiều file thành một stream; GZIP nén stream TAR đó.

**Câu 4.** Với file ZIP trên đĩa, muốn đọc duy nhất entry `config/settings.json`, nên dùng gì?

**Đáp án:** `ZipFile.getEntry("config/settings.json")`, sau đó `getInputStream(entry)`. Với dữ liệu ZIP đang stream, dùng `ZipInputStream` duyệt entry.

**Câu 5.** Một ZIP có entry `../../secret.txt` có thể ghi thẳng xuống thư mục đích không?

**Đáp án:** Không. Phải xác thực path, chống traversal/symlinks và đặt giới hạn tài nguyên trước khi giải nén.

**Câu 6.** ZIP đang tạo với `ZipOutputStream`, đã ghi hết entry nhưng chưa đóng/finish. Có nên coi file ZIP đã hoàn chỉnh?

**Đáp án:** Không. Central directory và metadata kết thúc archive có thể chưa được ghi đầy đủ.

### Tài liệu chính thức Java 17

- [java.util.zip package](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/package-summary.html)
- [GZIPInputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/GZIPInputStream.html)
- [GZIPOutputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/GZIPOutputStream.html)
- [ZipInputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/ZipInputStream.html)
- [ZipOutputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/ZipOutputStream.html)
- [ZipFile](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/ZipFile.html)
- [ZipEntry](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/ZipEntry.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="compression-and-archives-002">GZIPInputStream và GZIPOutputStream</a>

<details>
<summary>Xem chi tiết</summary>

# Java GZIPInputStream và GZIPOutputStream: nén, giải nén dữ liệu theo luồng

## 1. Mục tiêu và bối cảnh

Trong Java Core I/O, package `java.util.zip` cung cấp hai lớp quan trọng để làm việc với định dạng **GZIP**:

- **`GZIPOutputStream`**: nhận byte chưa nén, nén bằng cơ chế DEFLATE và ghi ra một luồng ở **định dạng GZIP**.
- **`GZIPInputStream`**: nhận luồng byte GZIP, giải nén để ứng dụng đọc được byte gốc.

Cả hai là **filter stream**: chúng bọc một `OutputStream` hoặc `InputStream` khác. Vì vậy bạn có thể nén file, dữ liệu từ mạng, hoặc dữ liệu trong bộ nhớ mà không phải tự triển khai thuật toán DEFLATE.

Mục tiêu của bài:

1. Hiểu cách GZIP đóng gói dữ liệu nén, khác gì với ZIP và raw DEFLATE.
2. Tạo `.gz` từ file hoặc từ các byte và giải nén lại.
3. Nắm chu trình stream: `write` → `finish`/`close` và `read` tới EOF.
4. Sử dụng buffer để xử lý file lớn thay vì luôn đưa toàn bộ dữ liệu lên heap.
5. Phân biệt lỗi dữ liệu nén, CRC32, stream chưa đóng, charset và dữ liệu không tin cậy.
6. Có chương trình **Java 17+ hoàn chỉnh** minh họa round-trip và stdout rõ ràng.

> **GZIP là phương pháp nén/đóng gói byte, không phải cơ chế mã hóa hay xác thực danh tính.** Nó có checksum để phát hiện một số lỗi dữ liệu, nhưng không bảo đảm an toàn trước người cố ý sửa dữ liệu.

## 2. GZIP hoạt động như thế nào?

Về mặt logic, một luồng GZIP có các thành phần:

```text
Byte nguồn
   |
   v
GZIPOutputStream
   |
   +-- GZIP header
   +-- DEFLATE-compressed payload
   +-- GZIP trailer (CRC32 + ISIZE)
   |
   v
Byte GZIP được ghi ra tệp/luồng
   |
   v
GZIPInputStream
   |
   +-- đọc header, giải nén DEFLATE
   +-- kiểm tra trailer/CRC khi đọc đến phần kết thúc hợp lệ
   |
   v
Byte nguồn được khôi phục
```

Thông thường:

- **Header** chứa chữ ký định dạng GZIP: hai byte đầu là `0x1F` và `0x8B`, cùng các thông tin định dạng khác.
- **Payload** sử dụng DEFLATE để nén. DEFLATE là cơ chế nén, còn GZIP là định dạng bọc quanh dữ liệu DEFLATE và metadata.
- **Trailer** chứa CRC32 của dữ liệu **chưa nén** và kích thước dữ liệu gốc modulo `2^32` (`ISIZE`).

Checksum là cách kiểm tra lỗi ngẫu nhiên/truncation ở định dạng; nó không phải chữ ký số, HMAC hay cơ chế chống giả mạo mật mã.

### GZIP có phải định dạng ZIP không?

**Không.**

| Tiêu chí | GZIP (`.gz`) | ZIP (`.zip`) |
|---|---|---|
| Ý tưởng chính | Nén một luồng dữ liệu byte | Container chứa nhiều entry/file có tên |
| API Java | `GZIPInputStream`, `GZIPOutputStream` | `ZipInputStream`, `ZipOutputStream`, `ZipFile` |
| Đường dẫn/tên file nội bộ | Không phải API quản lý cây entry | Có tên entry và metadata của từng entry |
| Dùng cho một file văn bản/binary | Rất phù hợp | Được, nhưng container phức tạp hơn |
| Đóng gói cả thư mục | Không trực tiếp | Phù hợp |
| Ví dụ phổ biến | `log.txt.gz`, phản hồi HTTP gzip | `backup.zip` chứa nhiều tệp |

Định dạng gzip cho phép những cấu trúc như nhiều gzip member nối tiếp theo đặc tả, nhưng **`GZIPOutputStream` không phải công cụ tạo ZIP entries** và ứng dụng không nên giả định một file `.gz` biểu diễn một thư mục. Kết hợp `tar + gzip` tạo `.tar.gz` là hai lớp định dạng: tar đóng gói nhiều entry, gzip nén luồng tar.

## 3. Hai lớp filter stream và cách lồng luồng

`GZIPOutputStream` mở rộng `DeflaterOutputStream`. Nó bọc một `OutputStream` đích:

```java
try (GZIPOutputStream gzip =
         new GZIPOutputStream(Files.newOutputStream(Path.of("note.txt.gz")))) {
    gzip.write("Xin chào GZIP!\n".getBytes(StandardCharsets.UTF_8));
}
```

Đây là **đoạn mã minh họa**; phải đặt trong method xử lý `IOException`, import `GZIPOutputStream`, `Files`, `Path`, `StandardCharsets`.

Luồng xử lý:

```text
"Xin chào GZIP!\n"
       |
  UTF-8 encode → byte[]
       |
 gzip.write(byte[])
       |
  DEFLATE + header/trailer
       |
 Files.newOutputStream(...)
       |
 note.txt.gz (byte nhị phân)
```

Ngược lại, `GZIPInputStream` bọc `InputStream` nguồn:

```java
try (GZIPInputStream gzip =
         new GZIPInputStream(Files.newInputStream(Path.of("note.txt.gz")))) {
    byte[] bytes = gzip.readAllBytes();
    String text = new String(bytes, StandardCharsets.UTF_8);
}
```

**`GZIPInputStream` trả byte gốc**, chưa tự diễn giải chúng là text UTF-8. Charset là lựa chọn của định dạng dữ liệu hoặc giao thức ứng dụng; dùng `StandardCharsets.UTF_8` minh bạch ở cả hai chiều.

`readAllBytes()` chỉ phù hợp khi chắc dữ liệu **sau giải nén** đủ nhỏ cho bộ nhớ. Với file lớn hoặc đầu vào không tin cậy, dùng vòng lặp với buffer và giới hạn kích thước.

## 4. Nén file theo streaming: không cần nạp toàn bộ vào RAM

Mẫu nên dùng khi nén một file lớn:

```java
static void gzipFile(Path source, Path target) throws IOException {
    try (InputStream in = Files.newInputStream(source);
         OutputStream rawOut = Files.newOutputStream(target);
         GZIPOutputStream gzip = new GZIPOutputStream(rawOut)) {

        byte[] buffer = new byte[16 * 1024];
        int count;

        while ((count = in.read(buffer)) != -1) {
            gzip.write(buffer, 0, count);
        }
    }
}
```

Đây là **một phương thức minh họa**, không phải chương trình độc lập; cần import `InputStream`, `OutputStream`, `IOException`, `Files`, `Path`, `GZIPOutputStream`.

Giải thích:

1. `Files.newInputStream(source)` đọc file nguồn.
2. `Files.newOutputStream(target)` là đích lưu byte đã nén. Tệp đích thông thường được tạo hoặc ghi đè theo options mặc định.
3. `GZIPOutputStream` mã hóa các byte được `write` thành stream GZIP.
4. Vòng lặp đọc tối đa 16 KiB mỗi lần, không phụ thuộc tổng dung lượng file.
5. Khi rời try-with-resources, `gzip.close()` hoàn tất luồng gzip và đóng underlying output stream.

Trong mã trên, `rawOut` vừa có biến riêng vừa được bọc bởi `gzip`. Việc đóng hai lần một số stream tiêu chuẩn thường không gây vấn đề, nhưng **mẫu ownership gọn hơn** là chỉ khai báo `GZIPOutputStream gzip = new GZIPOutputStream(Files.newOutputStream(target))`, để wrapper sở hữu stream dưới. Mẫu có biến `rawOut` được trình bày để giúp thấy rõ pipeline.

**Không nén đè lên chính source path đang đọc.** Nếu output mặc định truncate source trước khi đọc hết, dữ liệu có thể mất. Hãy tạo tệp đích riêng, hoặc ghi tạm rồi thay thế với chính sách atomic/durability phù hợp nếu cập nhật một file quan trọng.

## 5. Giải nén file theo streaming

Mẫu tương ứng:

```java
static void gunzipFile(Path sourceGzip, Path target) throws IOException {
    try (GZIPInputStream gzip =
             new GZIPInputStream(Files.newInputStream(sourceGzip));
         OutputStream out = Files.newOutputStream(target)) {

        byte[] buffer = new byte[16 * 1024];
        int count;

        while ((count = gzip.read(buffer)) != -1) {
            out.write(buffer, 0, count);
        }
    }
}
```

Ở đây mỗi `read` lấy các byte đã giải nén từ `gzip`. `write` của output thường ghi byte vào tệp đích mà không cần lưu cả nội dung vào một mảng khổng lồ.

Điểm lưu ý:

- Chương trình nên xác định policy trước khi ghi: có ghi đè file đích cũ không? Nếu cần tránh file đích bị dang dở khi dữ liệu gzip bị hỏng, **giải nén vào file tạm**, validate rồi thay tên đích.
- `GZIPInputStream.read` có thể ném `ZipException` (một dạng `IOException`) nếu header/trailer hoặc nội dung nén không hợp lệ.
- Nếu không đọc tới EOF và đóng quá sớm, chương trình có thể **chưa đi đến trailer để kiểm tra CRC32/ISIZE**. Với file cần được xác thực về tính toàn vẹn theo format, phải giải nén và kiểm tra đến kết thúc hợp lệ.
- Không lấy số byte đã nén làm giới hạn số byte giải nén. Một file gzip nhỏ có thể nở ra thành dữ liệu rất lớn.

## 6. Chu trình `finish()`, `flush()` và `close()`

Đây là phần quan trọng nhất khi viết gzip.

### `GZIPOutputStream.finish()`

`finish()` hoàn tất việc nén, ghi phần dữ liệu còn lại và trailer GZIP xuống stream đích **mà không nhất thiết đóng underlying stream**.

Khi nào hữu ích?

- Cần hoàn tất một member GZIP, nhưng còn muốn thao tác hợp lệ khác trên stream dưới theo một định dạng container chuyên biệt.
- Cần bảo đảm khối GZIP đã được hoàn tất trước một bước xử lý tiếp theo, trong khi vẫn kiểm soát vòng đời stream bên dưới.

Với nhu cầu ghi một file `.gz` thông thường, **dùng try-with-resources rồi đóng GZIPOutputStream** là cách dễ và an toàn nhất.

### `flush()` không thay thế `finish()`

`flush()` chuyển dữ liệu đang được đệm theo cơ chế stream/deflater, nhưng **không kết thúc một file gzip hợp lệ**. Không thể gọi `gzip.flush()` rồi giả định trailer đã có.

`GZIPOutputStream` có constructor `GZIPOutputStream(OutputStream out, int size, boolean syncFlush)`. Khi `syncFlush=true`, lời gọi `flush` có thể yêu cầu DEFLATE thực hiện `SYNC_FLUSH` để bên đọc xử lý dữ liệu đang có với độ trễ thấp hơn; đổi lại có thể ảnh hưởng hiệu quả nén.

Ví dụ minh họa:

```java
try (GZIPOutputStream gzip =
         new GZIPOutputStream(outputStream, 8192, true)) {
    gzip.write(chunk1);
    gzip.flush(); // SYNC_FLUSH, chưa tạo trailer kết thúc member.
    gzip.write(chunk2);
} // close hoàn tất gzip và đóng stream dưới.
```

`outputStream`, `chunk1`, `chunk2` là biến giả định của đoạn mã minh họa. Không nhầm `flush()` với **fsync**: việc buộc dữ liệu xuống thiết bị lưu trữ cần API/cơ chế riêng và không được `GZIPOutputStream.flush` tự bảo đảm.

### `close()`

`close()` của `GZIPOutputStream` kết thúc luồng nén chưa hoàn tất, phát hành dữ liệu còn lại/trailer và đóng luồng được bọc. `close()` có thể ném `IOException`, nên không bỏ qua ngoại lệ rồi coi file là thành công.

**Bẫy:** không làm mất reference đến gzip wrapper trước khi `finish` hoặc `close`. Chỉ đóng `FileOutputStream` ở dưới trong khi gzip chưa kết thúc có thể tạo file `.gz` bị cắt/thiếu trailer.

## 7. Chương trình Java 17+ hoàn chỉnh: nén, nhận diện và giải nén

Chương trình sau sử dụng một file tạm:

1. Tạo chuỗi ASCII để có độ dài byte xác định.
2. Dùng `GZIPOutputStream` nén vào file `.gz`.
3. Kiểm tra hai byte signature GZIP ở đầu file.
4. Dùng `GZIPInputStream` giải nén lại.
5. So sánh bytes gốc và bytes kết quả bằng `Arrays.equals`.
6. Dọn file tạm.

Tạo file **`GzipStreamsDemo.java`**:

```java
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GzipStreamsDemo {

    public static void main(String[] args) throws Exception {
        String message = "Java IO: GZIP\n";
        byte[] original = message.getBytes(StandardCharsets.US_ASCII);
        Path gzipFile = Files.createTempFile("gzip-demo-", ".gz");

        try {
            // Ghi toàn bộ byte nguồn vào một GZIP member.
            try (GZIPOutputStream gzip =
                     new GZIPOutputStream(Files.newOutputStream(gzipFile))) {
                gzip.write(original);
            } // close ghi phần kết thúc/trailer.

            boolean hasGzipSignature;
            try (InputStream raw = Files.newInputStream(gzipFile)) {
                int first = raw.read();
                int second = raw.read();
                hasGzipSignature = first == 0x1f && second == 0x8b;
            }

            byte[] restored;
            try (GZIPInputStream gzip =
                     new GZIPInputStream(Files.newInputStream(gzipFile))) {
                restored = gzip.readAllBytes();
            }

            String decoded = new String(
                    restored, StandardCharsets.US_ASCII);

            System.out.println("Original bytes: " + original.length);
            System.out.println("GZIP signature: " + hasGzipSignature);
            System.out.println("Decoded: " + decoded.stripTrailing());
            System.out.println("Round-trip: " + Arrays.equals(original, restored));
        } finally {
            Files.deleteIfExists(gzipFile);
        }
    }
}
```

Biên dịch/chạy trong thư mục chứa file:

```bash
javac GzipStreamsDemo.java
java GzipStreamsDemo
```

**Stdout chính xác khi tạo/đọc/ghi/xóa file tạm thành công:**

```text
Original bytes: 14
GZIP signature: true
Decoded: Java IO: GZIP
Round-trip: true
```

Giải thích:

- `"Java IO: GZIP\n"` có 14 byte ASCII, nên `original.length == 14`.
- Signature GZIP bắt đầu bằng `0x1f 0x8b`, bất kể độ lớn file nén cụ thể.
- `close()` trong try-with-resources hoàn tất stream gzip trước khi chương trình đọc file.
- `GZIPInputStream.readAllBytes()` phù hợp ở ví dụ vì đầu ra đã biết rất nhỏ; với dữ liệu người dùng cung cấp hoặc file lớn, cần giới hạn streaming.
- `stripTrailing()` chỉ bỏ ký tự xuống dòng khi in message, không thay đổi mảng `restored` dùng để so sánh.
- `Arrays.equals` kiểm tra nội dung byte khôi phục giống hệt byte ban đầu. Điều này **không** chứng minh GZIP mã hóa/chống sửa đổi do đối phương; nó xác nhận round-trip của ví dụ.

Chương trình không in size của `gzipFile` vì kích thước nén có thể chịu ảnh hưởng lựa chọn header/implementation, phiên bản thư viện và nội dung; stdout tập trung vào các giá trị có thể xác định một cách đáng tin.

## 8. Buffer và giải thích cơ chế streaming

Với một file lớn, chu trình thông dụng:

```text
NÉN
┌──────────────┐    16 KiB     ┌──────────────────┐
│ InputStream  │ ────────────> │ GZIPOutputStream │ ──> file.gz
└──────────────┘               └──────────────────┘

GIẢI NÉN
file.gz ──> ┌─────────────────┐   16 KiB      ┌───────────────┐
            │ GZIPInputStream │ ────────────> │ OutputStream  │
            └─────────────────┘               └───────────────┘
```

Kích thước buffer ảnh hưởng số lần gọi I/O và chi phí xử lý, **không bắt buộc lớn bằng toàn bộ file**. Với nhiều ứng dụng, buffer vài KiB đến vài chục KiB là điểm khởi đầu hợp lý; benchmark với dạng dữ liệu, thiết bị và throughput thực tế.

Một số cách viết khác:

```java
try (InputStream source = Files.newInputStream(inputFile);
     GZIPOutputStream output =
         new GZIPOutputStream(Files.newOutputStream(gzipFile))) {
    source.transferTo(output);
}
```

`InputStream.transferTo(OutputStream)` có từ Java 9, dùng được trên Java 17. Nó sao chép byte từ input tới output theo luồng, không bắt buộc ứng dụng tự viết vòng lặp `read/write`. Nhưng **không tự cung cấp giới hạn số byte giải nén hay kiểm tra nội dung nghiệp vụ**; không nên thay bằng `transferTo` mà bỏ các giới hạn dành cho dữ liệu không tin cậy.

Đối với input lớn, tránh:

```java
byte[] whole = Files.readAllBytes(veryLargePath);
gzip.write(whole); // Dễ gây áp lực heap khi file quá lớn.
```

Bản thân `gzip.write(byte[])` vẫn có thể hoạt động đúng; vấn đề nằm ở việc `readAllBytes` nạp toàn bộ file vào bộ nhớ trước.

## 9. Nén văn bản: Charset là trách nhiệm của ứng dụng

`GZIPOutputStream` và `GZIPInputStream` hoạt động ở **mức byte**, không ở mức `String` hay ký tự Unicode.

Khi nén văn bản, phải mã hóa tường minh:

```java
String text = "Tiếng Việt: cà phê ☕\n";
byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);

try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
    gzip.write(utf8);
}
```

Đoạn này là minh họa, `output` phải là `OutputStream` hợp lệ. Lúc giải nén:

```java
try (GZIPInputStream gzip = new GZIPInputStream(input)) {
    byte[] originalUtf8 = gzip.readAllBytes();
    String textAgain =
            new String(originalUtf8, StandardCharsets.UTF_8);
}
```

Tương tự, `input` phải là `InputStream` hợp lệ. Không dùng charset mặc định môi trường khi format đã quy định UTF-8. Nếu file gzip chứa ảnh, dữ liệu nhị phân hoặc serialize object thì **không** tự động biến nó thành String sau giải nén.

### Có thể dùng BufferedReader/Writer không?

Có, khi bạn biết dữ liệu bên trong GZIP thực sự là văn bản. Ví dụ:

```java
try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(
                new GZIPInputStream(Files.newInputStream(path)),
                StandardCharsets.UTF_8))) {
    String line;
    while ((line = reader.readLine()) != null) {
        // Xử lý từng dòng.
    }
}
```

`reader.readLine()` bỏ dấu xuống dòng khỏi giá trị trả về; nếu tái xuất văn bản, việc tự nối `\n` có thể thay đổi kiểu newline so với bản gốc. Nếu yêu cầu **đúng byte gốc**, nên giải nén dưới dạng byte stream.

## 10. Dữ liệu hỏng: header, payload, CRC32 và EOF

Những lỗi thường gặp khi đọc gzip:

- File không phải GZIP nhưng bị đặt tên `.gz` → header có thể sai và `GZIPInputStream` ném lỗi.
- Tệp bị cắt trước khi DEFLATE hoàn tất → giải nén có thể ném `EOFException` hoặc `ZipException`.
- Trailer CRC32/ISIZE không đúng → lỗi kiểm tra dữ liệu khi đọc đến điểm kiểm tra.
- Tệp đang được writer ghi dở → reader có thể thấy thiếu dữ liệu hoặc trailer chưa xuất hiện.
- Underlying stream có lỗi disk/network → phát sinh `IOException`.

`GZIPInputStream` thường báo những lỗi định dạng qua `ZipException`, vốn kế thừa `IOException`. Ứng dụng nên bắt `IOException` ở ranh giới I/O và bổ sung thông tin đường dẫn/nguồn dữ liệu phù hợp.

Ví dụ:

```java
try (GZIPInputStream gzip =
         new GZIPInputStream(Files.newInputStream(path))) {
    byte[] bytes = gzip.readAllBytes(); // Nhỏ và nguồn đáng tin.
    // Chỉ dùng bytes sau khi giải nén tới EOF thành công.
} catch (ZipException ex) {
    // GZIP không hợp lệ hoặc dữ liệu/trailer không khớp.
    throw new IOException("GZIP hỏng: " + path, ex);
}
```

Đây là đoạn minh họa. Không khẳng định **mọi dạng tệp hỏng** đều ném đúng một subclass ngoại lệ; nên quản lý `IOException` tổng quát và kiểm tra dữ liệu ứng dụng sau khi giải nén.

Một tệp có hai byte signature `0x1f 0x8b` **chưa đủ chứng minh file gzip hợp lệ**. Kiểm tra signature chỉ là bước nhận diện sơ bộ; chỉ sau giải nén/kiểm tra tới EOF và kiểm tra nội dung ứng dụng mới có cơ sở tốt hơn.

### CRC32 khác chữ ký bảo mật

CRC32 phát hiện nhiều lỗi dữ liệu ngẫu nhiên; một đối tượng cố ý sửa byte có thể tính lại CRC32. Do đó không dùng CRC để:

- Xác thực người gửi.
- Kiểm tra nội dung có bị thay đổi bởi kẻ tấn công.
- Thay thế TLS, HMAC hoặc chữ ký số.
- Quyết định một file cấu hình không tin cậy có đủ quyền chạy nội dung hay không.

Dữ liệu bảo mật cần cơ chế xác thực/kiểm tra riêng, cộng với kiểm tra schema và giới hạn tài nguyên sau giải nén.

## 11. Zip bomb/GZIP bomb và giới hạn bộ nhớ

**Tỷ lệ nén cao** có thể khiến một file đầu vào nhỏ giải nén thành hàng gigabyte dữ liệu. Đây là dạng **decompression bomb**, có thể tiêu thụ đĩa, RAM, CPU hoặc làm nghẽn dịch vụ.

Đặc biệt nguy hiểm:

```java
// Phản ví dụ với đầu vào không tin cậy.
byte[] all = new GZIPInputStream(untrustedInput).readAllBytes();
```

`readAllBytes` có thể cố cấp phát mảng rất lớn. Thay vào đó, kiểm tra số byte **đã giải nén** trong khi streaming.

### Ví dụ helper giải nén có giới hạn

```java
static long copyGzipWithLimit(
        InputStream compressed, OutputStream output, long maxUncompressedBytes)
        throws IOException {

    if (maxUncompressedBytes < 0) {
        throw new IllegalArgumentException("Negative limit");
    }

    long total = 0L;

    try (GZIPInputStream gzip = new GZIPInputStream(compressed)) {
        byte[] buffer = new byte[8192];
        int count;

        while ((count = gzip.read(buffer)) != -1) {
            if (count > maxUncompressedBytes - total) {
                throw new IOException("Uncompressed data exceeds limit");
            }

            output.write(buffer, 0, count);
            total += count;
        }
    }

    return total;
}
```

Đây là một **phương thức minh họa** cần import `InputStream`, `OutputStream`, `IOException`, `GZIPInputStream`. Nó giả định `maxUncompressedBytes` không âm. Mẫu `count > maxUncompressedBytes - total` tránh tràn số khi cộng, với bất biến `0 <= total <= maxUncompressedBytes`.

Lưu ý:

- Phương thức **đóng `compressed`** gián tiếp khi đóng `GZIPInputStream` vì wrapper sở hữu nguồn. Nó **không đóng `output`**; caller quyết định đóng output.
- Nếu phát hiện vượt hạn mức sau khi đã ghi một phần output, file đích có thể đang chứa dữ liệu chưa hoàn chỉnh. Trong hệ thống thực nên ghi ra file tạm rồi chỉ công bố khi copy và validation thành công.
- Hạn mức số byte **không tự giới hạn CPU/time**. Với ứng dụng nhận file từ người lạ, cần thêm timeout, quota, số phiên xử lý đồng thời, dung lượng đĩa và giới hạn độ sâu/loại định dạng dữ liệu sau giải nén.
- Với buffer có `length > 0`, `InputStream` tuân thủ hợp đồng trả số byte dương khi có dữ liệu hoặc `-1` khi EOF; code giả định stream hợp lệ.
- Nếu nguồn gzip là stream rất chậm, chỉ một giới hạn byte chưa đủ tránh chiếm worker lâu.

### Giới hạn tỷ lệ nén

Tùy bài toán, có thể giới hạn cả kích thước file nén (`Files.size(path)`) lẫn kích thước output. Nhưng tỷ lệ nén không cố định; file text lặp có thể nén rất tốt, còn file đã nén/đã mã hóa gần như không giảm. Đừng từ chối mọi file có tỷ lệ cao nếu ứng dụng cần dữ liệu đó; hãy xây chính sách dựa trên nguồn tin cậy và quota thực tế.

## 12. Chọn mức nén và buffer

`GZIPOutputStream` sử dụng Deflater. Java cung cấp cấu hình ở mức `Deflater` (ví dụ `BEST_SPEED`, `DEFAULT_COMPRESSION`, `BEST_COMPRESSION`) thông qua API liên quan, nhưng constructor `GZIPOutputStream` thường dùng cấu hình mặc định.

Tham số `size` trong constructor:

```java
new GZIPOutputStream(output, 8192);
```

là **kích thước buffer nội bộ**, không phải “compression level 8192”. Không nhầm kích thước buffer với mức nén hay kích thước file nguồn. Với ứng dụng cần mức nén tùy chỉnh, cần xem xét API `Deflater`/cách xây dựng stream phù hợp; đừng sửa thuộc tính nội bộ không public hoặc dựa vào reflection như một API ổn định.

GZIP thường phù hợp cho:

- Văn bản lặp, JSON, XML, log.
- Payload truyền qua hệ thống chấp nhận `Content-Encoding: gzip`.
- Một số dữ liệu định dạng nhị phân có dư thừa.

GZIP thường ít giúp ích hoặc có thể tăng dung lượng đối với:

- JPEG, PNG, MP4 và file đã nén tốt sẵn.
- Dữ liệu được mã hóa với entropy cao.
- Payload quá nhỏ, nơi chi phí header/trailer chiếm đáng kể.

Không khẳng định GZIP **luôn nhỏ hơn** dữ liệu gốc. Với dữ liệu ngắn hoặc không nén được, output gzip có thể lớn hơn input.

## 13. GZIP trong HTTP và trao đổi mạng

Header `Content-Encoding: gzip` trong HTTP chỉ cách body đã được mã hóa/nén; nó khác với extension file `.gz` trên đĩa.

Ví dụ về giao thức:

```text
Client request:
Accept-Encoding: gzip

Server response:
Content-Encoding: gzip
Content-Type: application/json

[body bytes ở định dạng gzip]
```

- Bên gửi phải thực sự gửi byte gzip tương thích với `Content-Encoding` đã khai báo.
- Bên nhận phải giải nén **trước** khi parse JSON/text.
- Một số HTTP client Java hoặc thư viện web có thể tự xử lý content encoding; **không** bọc thêm `GZIPInputStream` lần nữa nếu response đã được giải nén bởi stack HTTP.
- Header nén không xác thực dữ liệu; bảo mật và độ tin cậy kết nối cần công cụ riêng như TLS và policy kiểm tra nội dung.
- Đối với dữ liệu lấy từ mạng, giới hạn output giải nén vẫn rất quan trọng vì content length nén không phải kích thước dữ liệu gốc.

Nên để tầng HTTP/application framework quản lý negotiation và content encoding khi có sẵn, thay vì tự thêm `Accept-Encoding` rồi đoán mọi server đều trả gzip.

## 14. Khi nào nên dùng GZIPInputStream/GZIPOutputStream?

| Tình huống | Hướng tiếp cận |
|---|---|
| Nén một file log thành `.gz` | `Files.newInputStream` → `GZIPOutputStream` |
| Giải nén file `.gz` vào file khác | `GZIPInputStream` → `Files.newOutputStream` |
| Giải nén vài KB vào bộ nhớ | `GZIPInputStream.readAllBytes` nếu biết chắc giới hạn |
| Giải nén file lớn | Vòng lặp buffer và streaming |
| Nhận dữ liệu gzip từ nguồn không tin cậy | Streaming có giới hạn, validate, timeout/quota |
| Nén String UTF-8 | `text.getBytes(UTF_8)` rồi ghi vào gzip |
| Ghi nhiều file có tên vào một archive | `ZipOutputStream`/`ZipFile`, không phải GZIP stream đơn giản |
| Tạo `.tar.gz` | Tạo tar stream trước rồi nén toàn bộ tar bằng GZIP |
| Xử lý HTTP gzip | Dựa vào `Content-Encoding` và khả năng của HTTP stack |
| Muốn giảm file đã là JPEG/MP4/ZIP | Đo trước; gzip thường đem lại ít lợi ích |
| Cần đảm bảo file đích không bị ghi dở | Ghi file tạm, hoàn tất gzip, validate rồi công bố theo chính sách |

## 15. Sai lầm thường gặp

1. **Tưởng GZIP = ZIP.** GZIP không tự cung cấp danh sách named entries và cây thư mục.
2. **Không đóng `GZIPOutputStream`.** Luồng có thể thiếu trailer/byte cuối và không giải nén hợp lệ.
3. **Chỉ `flush()` rồi coi file đã hoàn tất.** `flush` không thay `finish`/`close`.
4. **Gọi `finish()` rồi tiếp tục ghi thêm dữ liệu như thể cùng một gzip member vẫn đang mở.** Member đã hoàn tất; hãy thiết kế định dạng/lifecycle phù hợp.
5. **Đóng underlying output trước khi đóng wrapper gzip.** Wrapper có thể không ghi được trailer.
6. **Đọc không hết stream rồi mặc định CRC đã được kiểm tra.** Kiểm tra trailer phụ thuộc việc đọc đến điểm kết thúc hợp lệ.
7. **Dùng `readAllBytes` với file không tin cậy.** Có nguy cơ decompression bomb hoặc hết heap.
8. **Dùng charset mặc định cho văn bản.** GZIP xử lý byte; phải thống nhất encoding/decoding.
9. **Dựa vào phần mở rộng `.gz` để tin dữ liệu hợp lệ.** Kiểm tra format và giải nén thực tế.
10. **Tưởng hai byte signature đủ chứng minh file hoàn chỉnh.** File có thể bị cắt hoặc CRC sai.
11. **Tin CRC32 có khả năng chống giả mạo.** CRC không phải primitive mật mã.
12. **Dùng GZIP để mã hóa dữ liệu nhạy cảm.** Nén không làm bí mật dữ liệu; cần mã hóa/bảo mật riêng.
13. **Nén trực tiếp vào chính file đang đọc.** Truncate output có thể làm mất input.
14. **Giải nén trực tiếp vào file đích quan trọng.** Khi input lỗi giữa chừng, file đích có thể chứa dữ liệu dang dở.
15. **Không có giới hạn byte giải nén.** File nén nhỏ có thể tạo output rất lớn.
16. **Tưởng file gzip luôn nhỏ hơn file gốc.** Header/DEFLATE overhead có thể làm dữ liệu ngắn hoặc không nén được lớn hơn.
17. **Quên quyền sở hữu stream.** Đóng gzip wrapper thường đóng stream bên dưới; API cần nêu rõ ownership.
18. **Dùng buffer khổng lồ vì nghĩ mới xử lý được file lớn.** Streaming với buffer vừa phải thường phù hợp hơn.
19. **Bỏ qua lỗi `IOException` khi close.** Việc hoàn tất gzip có thể lỗi lúc ghi footer.
20. **Giải nén lại dữ liệu đã được HTTP client tự decode.** Có thể gặp lỗi “not in GZIP format” vì xử lý hai lần.
21. **Cho rằng `flush` đồng nghĩa dữ liệu đã fsync ra đĩa.** Hai tầng trách nhiệm khác nhau.
22. **Lấy số byte nén làm số byte gốc mà không kiểm tra.** Compression ratio không cố định.

## 16. Thiết kế file đầu ra an toàn hơn

Trong hệ thống thực, nén file log hoặc sao lưu quan trọng nên có quy trình:

1. Chọn **file đích riêng** hoặc tạo **file tạm trong cùng thư mục** với đích.
2. Nén theo buffer tới tệp tạm, bảo đảm mọi `write` cần thiết thành công.
3. Đóng `GZIPOutputStream` để hoàn tất footer.
4. Nếu có yêu cầu, kiểm tra lại file gzip hoặc metadata/checksum nghiệp vụ.
5. Chỉ sau khi file đã hoàn chỉnh mới công bố bằng thao tác di chuyển/đổi tên theo khả năng filesystem, hoặc giữ tên output mới độc lập.
6. Dọn temp khi lỗi; có chính sách phục hồi file temp còn sót nếu tiến trình chết bất ngờ.

Không coi rename là giao dịch tự động bền trên mọi filesystem. Mẫu `temp + move` giải quyết rủi ro reader nhìn thấy file bị ghi dở khi thao tác move có tính nguyên tử phù hợp; còn durability qua mất điện và đồng bộ thư mục cần phân tích riêng.

### Với pipeline nhiều lớp filter

Ví dụ ghi văn bản UTF-8 qua writer:

```java
try (Writer writer = new OutputStreamWriter(
        new GZIPOutputStream(Files.newOutputStream(gzipPath)),
        StandardCharsets.UTF_8)) {
    writer.write("Dữ liệu UTF-8\n");
}
```

Đoạn minh họa cần `Writer`, `OutputStreamWriter` và các import tương ứng. Thứ tự đóng: đóng `Writer` → writer đóng gzip → gzip kết thúc member và đóng file output. Đây là cách thuận tiện khi nguồn dữ liệu là ký tự thay vì byte.

Lưu ý `OutputStreamWriter` có encoder charset; không trộn trực tiếp `gzip.write(byte[])` với `writer.write(String)` cùng một luồng khi chưa quản lý bộ đệm/chính sách flush rõ ràng.

## 17. Câu hỏi tự kiểm tra

**Câu 1.** `GZIPOutputStream` nhận String hay byte?  
**Nhận byte.** Mã hóa String bằng charset xác định trước khi ghi, hoặc dùng `OutputStreamWriter` bọc gzip.

**Câu 2.** Có thể giải nén file `.gz` bằng `ZipInputStream` rồi lặp `ZipEntry` không?  
**Không nên.** GZIP và ZIP là hai định dạng khác; dùng `GZIPInputStream` cho gzip.

**Câu 3.** Vì sao cần `close()` của GZIPOutputStream?  
**Để hoàn tất dữ liệu nén và trailer** cùng việc đóng stream bên dưới; nếu bỏ qua có thể tạo gzip chưa hoàn chỉnh.

**Câu 4.** `gzip.flush()` có luôn tạo CRC32 trailer và kết thúc file không?  
**Không.** Muốn kết thúc, dùng `finish()` hoặc `close()`.

**Câu 5.** File gzip 50 KB có bảo đảm giải nén ra ít hơn 50 MB không?  
**Không.** Một số dữ liệu có thể nén cực tốt; phải giới hạn output dựa trên số byte đã giải nén.

**Câu 6.** Nếu `GZIPInputStream` đọc được một phần văn bản nhưng chưa tới EOF, CRC trailer đã chắc chắn được kiểm tra chưa?  
**Chưa chắc.** Cần đọc tới điểm kết thúc hợp lệ để kiểm tra toàn vẹn theo định dạng.

**Câu 7.** Hai byte đầu `1F 8B` có đủ để tin nội dung gzip an toàn không?  
**Không.** Đó là signature nhận diện sơ bộ; vẫn phải giải nén, kiểm tra lỗi/trailer và validate dữ liệu ứng dụng.

**Câu 8.** GZIP có mã hóa bảo mật không?  
**Không.** Không được dùng nén thay cho bảo mật hay chữ ký.

**Câu 9.** Một file `.tar.gz` chứa nhiều file bên trong có mâu thuẫn với GZIP là stream không?  
**Không.** Tar là lớp đóng gói nhiều entry thành **một stream**; GZIP nén stream tar đó.

**Câu 10.** Nén JPEG bằng gzip chắc chắn giảm dung lượng?  
**Không.** File JPEG đã nén có thể hầu như không giảm, thậm chí lớn hơn do overhead.

**Câu 11.** Khi giải nén vào file đích quan trọng, cách nào giúp tránh công bố file dở nếu gzip hỏng?  
**Giải nén vào file tạm**, kiểm tra thành công đến EOF rồi mới công bố theo chính sách filesystem phù hợp.

## 18. Tóm tắt

- **`GZIPOutputStream` nén byte sang định dạng GZIP; `GZIPInputStream` giải nén từ định dạng đó.**
- **GZIP là luồng nén, không phải ZIP archive có named entries.** Muốn lưu nhiều file có cấu trúc, chọn ZIP hoặc tar trước khi gzip.
- **Luôn hoàn tất `GZIPOutputStream` bằng `close()` hoặc `finish()`**; `flush()` đơn thuần không thay thế trailer.
- **Dùng streaming với buffer** cho file lớn, và **đặt giới hạn byte giải nén** cho nguồn không tin cậy.
- **CRC32 giúp phát hiện lỗi, không xác thực bảo mật.** Cần validation, hạn mức tài nguyên và cơ chế bảo vệ bổ sung.
- **Charset không nằm trong trách nhiệm của GZIP stream**; văn bản cần encoding/decoding thống nhất.
- Với file quan trọng, **ghi file tạm rồi công bố sau khi hoàn tất** thay vì ghi đè trực tiếp vào đích.

### Tài liệu Java SE 17

- [GZIPInputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/GZIPInputStream.html)
- [GZIPOutputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/GZIPOutputStream.html)
- [DeflaterOutputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/DeflaterOutputStream.html)
- [ZipInputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/ZipInputStream.html)
- [InputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStream.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="compression-and-archives-003">ZipInputStream, ZipOutputStream và ZipEntry</a>

<details>
<summary>Xem chi tiết</summary>

# ZipInputStream, ZipOutputStream và ZipEntry trong Java Core I/O

## 1. ZIP là gì và vì sao cần ba lớp này?

ZIP là định dạng **archive**: một tệp chứa nhiều entry (tệp, thư mục và metadata), có thể sử dụng nén cho từng entry. Java cung cấp thư viện chuẩn `java.util.zip` để đọc và tạo ZIP mà không cần một thư viện bên thứ ba.

Ba lớp quan trọng:

- **`ZipInputStream`**: đọc tuần tự từng entry từ một `InputStream` chứa dữ liệu ZIP. Nó phù hợp khi nguồn là stream và không cần truy cập ngẫu nhiên.
- **`ZipOutputStream`**: tạo archive ZIP và ghi lần lượt từng entry ra một `OutputStream`. Nó tự viết các cấu trúc ZIP cần thiết khi hoàn tất archive.
- **`ZipEntry`**: mô tả **một mục** trong archive: tên, phương thức nén, kích thước (nếu biết), CRC-32, thời gian, comment/extra field... Nó là metadata, **không phải stream byte nội dung**.

Hình dung:

```text
Ghi ZIP                                 Đọc ZIP
-------                                 -------
OutputStream (tệp/network/memory)       InputStream (tệp/network/memory)
       ^                                       |
       |                                       v
ZipOutputStream                         ZipInputStream
       ^                                       |
 putNextEntry(ZipEntry)                  getNextEntry() -> ZipEntry
 write(payload bytes)                   read(payload bytes)
 closeEntry()                            closeEntry()
       |                                       |
   [a.txt][b.bin][folder/]              xử lý lần lượt từng entry
```

Một ZIP có thể chứa nhiều entry. **Dấu phân cách entry là cấu trúc định dạng ZIP**, không phải một delimiter văn bản tự chọn. Các entry có thể dùng phương thức `DEFLATED` (nén) hoặc `STORED` (không nén).

## 2. Sự khác nhau giữa tệp ZIP, entry và stream

Giả sử `backup.zip` chứa:

```text
backup.zip
├── docs/
│   ├── hello.txt
│   └── guide.md
└── image.bin
```

- `backup.zip` là **tệp ZIP bên ngoài**, mở qua `Files.newInputStream` hoặc `Files.newOutputStream`.
- `docs/`, `docs/hello.txt`, `docs/guide.md`, `image.bin` là các **entry**, nhận diện bằng `ZipEntry.getName()`.
- `ZipInputStream.read()` đọc **nội dung của entry hiện tại**, chứ không tự trả một `InputStream` độc lập cho từng entry.
- `ZipOutputStream.write()` ghi **byte cho entry hiện tại** sau khi `putNextEntry()`.
- `ZipEntry` chứa tên và thuộc tính; muốn lấy byte nội dung phải dùng stream phù hợp.

Quy ước phổ biến là tên entry dùng dấu `/` ngay cả trên Windows. Directory entry thường có tên kết thúc bằng `/`, ví dụ `docs/`, và `ZipEntry.isDirectory()` nhận diện theo quy ước này. Có thể có ZIP không chứa entry thư mục riêng nhưng vẫn có tệp với tên `docs/hello.txt`; khi giải nén vẫn phải tự tạo thư mục cha.

## 3. Luồng ghi bằng `ZipOutputStream`

Quy trình cơ bản:

```java
try (ZipOutputStream zout = new ZipOutputStream(
        Files.newOutputStream(zipPath), StandardCharsets.UTF_8)) {

    ZipEntry entry = new ZipEntry("docs/hello.txt");
    zout.putNextEntry(entry); // Mở entry mới trong archive
    zout.write(contentBytes); // Ghi dữ liệu thuộc entry hiện tại
    zout.closeEntry();        // Kết thúc entry
} // close() hoàn tất archive và đóng stream bên dưới
```

Đây là **đoạn minh họa trong phương thức**: `zipPath` và `contentBytes` cần được khai báo từ trước, đồng thời cần import `java.nio.file.*`, `java.nio.charset.StandardCharsets` và `java.util.zip.*`.

Hợp đồng quan trọng:

1. **`putNextEntry(entry)`** mở một mục mới, viết header tương ứng và chuẩn bị trạng thái ghi. Nếu trước đó còn entry chưa đóng, `putNextEntry` có thể kết thúc entry trước theo hợp đồng triển khai; tuy vậy nên gọi `closeEntry()` tường minh để code rõ ràng.
2. **`write(byte[])` hoặc `write(byte[],off,len)`** ghi byte của entry đang mở. Nếu chưa có entry được mở, không được tùy tiện ghi payload.
3. **`closeEntry()`** hoàn tất entry hiện tại: flush/trình bày metadata cần thiết như size và CRC, kết thúc phần dữ liệu nén. Nó **không đóng toàn archive**.
4. **`finish()`** viết xong cấu trúc kết thúc ZIP, nhưng không đóng underlying stream. Hữu ích nếu muốn hoàn thành ZIP mà vẫn giữ `OutputStream` bên dưới. Sau `finish()` không ghi thêm entry.
5. **`close()`** đóng `ZipOutputStream`, hoàn tất ZIP nếu cần và đóng stream bên dưới. Sử dụng `try-with-resources` là lựa chọn mặc định.

**Nếu chỉ gọi `flush()` trên output stream rồi gửi tệp đi**, ZIP có thể thiếu central directory và không đọc được hoàn chỉnh. Hoàn tất ZIP bằng `finish()` hoặc `close()`.

### 3.1. Ghi thư mục

```java
zout.putNextEntry(new ZipEntry("docs/"));
zout.closeEntry();
```

Đây là snippet trong phạm vi có `ZipOutputStream zout`. Tên có dấu `/` cuối biểu thị directory entry và thường không chứa payload. ZIP không buộc mọi thư mục phải có entry riêng; có thể chỉ ghi các file bên dưới `docs/`.

### 3.2. Ghi hai entry tệp liên tiếp

```java
zout.putNextEntry(new ZipEntry("first.txt"));
zout.write("One".getBytes(StandardCharsets.UTF_8));
zout.closeEntry();

zout.putNextEntry(new ZipEntry("second.txt"));
zout.write("Two".getBytes(StandardCharsets.UTF_8));
zout.closeEntry();
```

Không ghi byte tệp thứ hai trước khi mở `second.txt`, nếu không byte sẽ bị gắn vào entry đang mở.

## 4. Luồng đọc bằng `ZipInputStream`

Quy trình đọc theo thứ tự entry:

```java
try (ZipInputStream zin = new ZipInputStream(
        Files.newInputStream(zipPath), StandardCharsets.UTF_8)) {

    ZipEntry entry;
    byte[] buffer = new byte[8192];

    while ((entry = zin.getNextEntry()) != null) {
        System.out.println("entry=" + entry.getName());

        int n;
        while ((n = zin.read(buffer)) != -1) {
            // Chỉ buffer[0..n) chứa byte mới của entry này.
            processBytes(buffer, 0, n); // callback do ứng dụng định nghĩa
        }

        zin.closeEntry();
    }
}
```

Đây là **mẫu thuật toán minh họa**, không chạy độc lập vì `zipPath` và `processBytes` là thành phần do ứng dụng cung cấp.

Quy tắc sử dụng:

- **`getNextEntry()`** di chuyển tới entry kế và trả metadata, hoặc `null` khi không còn entry.
- **`read(byte[])`** đọc byte đã giải nén của entry hiện tại; trả **số byte dương** (hoặc có thể 0 theo hợp đồng overload trong trường hợp yêu cầu độ dài 0), và **`-1` khi đã hết nội dung entry đó**.
- `-1` từ `read()` là **cuối entry hiện tại**. Nó **không chứng minh archive hết entry**. Chỉ `getNextEntry()==null` mới báo hết danh sách entry trong luồng đọc này.
- **`closeEntry()`** kết thúc việc xử lý entry hiện tại; nếu chưa đọc hết, việc bỏ qua/drain phần còn lại có thể phát sinh I/O và phát hiện lỗi ZIP.
- **`close()`** đóng ZIP input stream và stream bên dưới.

Một lần `read(buffer)` không bảo đảm nhận đủ kích thước buffer hoặc toàn bộ entry. Luôn lặp đến `-1`; không dùng `available()` làm “số byte còn lại của ZIP”. `ZipInputStream.available()` không phải hàm đo đầy đủ kích thước dữ liệu còn lại.

## 5. Ví dụ Java 17+ đầy đủ: tạo ZIP và đọc lại

Ví dụ này tạo ZIP thật trong thư mục tạm, chứa một directory entry, một tệp văn bản UTF-8 và một tệp nhị phân. Sau khi `ZipOutputStream` đóng hoàn chỉnh archive, `ZipInputStream` đọc từng entry và in kết quả. Dữ liệu hiển thị có thứ tự xác định.

Tạo file `ZipStreamsDemo.java`:

```java
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipStreamsDemo {
    public static void main(String[] args) throws IOException {
        Path archive = Files.createTempFile("zip-streams-", ".zip");

        try {
            try (OutputStream raw = Files.newOutputStream(archive);
                 ZipOutputStream zout = new ZipOutputStream(
                         raw, StandardCharsets.UTF_8)) {

                zout.putNextEntry(new ZipEntry("docs/"));
                zout.closeEntry();

                zout.putNextEntry(new ZipEntry("docs/hello.txt"));
                zout.write("Xin chào ZIP".getBytes(StandardCharsets.UTF_8));
                zout.closeEntry();

                zout.putNextEntry(new ZipEntry("data.bin"));
                zout.write(new byte[] {0, 1, 2, (byte) 255});
                zout.closeEntry();
            }

            try (InputStream raw = Files.newInputStream(archive);
                 ZipInputStream zin = new ZipInputStream(
                         raw, StandardCharsets.UTF_8)) {

                ZipEntry entry;
                byte[] buffer = new byte[1024];

                while ((entry = zin.getNextEntry()) != null) {
                    ByteArrayOutputStream content = new ByteArrayOutputStream();
                    int n;

                    while ((n = zin.read(buffer)) != -1) {
                        content.write(buffer, 0, n);
                    }

                    if (entry.isDirectory()) {
                        System.out.println("directory: " + entry.getName());
                    } else if (entry.getName().endsWith(".txt")) {
                        String text = new String(
                                content.toByteArray(), StandardCharsets.UTF_8);
                        System.out.println(
                                entry.getName() + " = " + text);
                    } else {
                        System.out.println(
                                entry.getName() + " = "
                                + Arrays.toString(content.toByteArray()));
                    }

                    zin.closeEntry();
                }
            }
        } finally {
            Files.deleteIfExists(archive);
        }
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac ZipStreamsDemo.java
java ZipStreamsDemo
```

Stdout dự kiến:

```text
directory: docs/
docs/hello.txt = Xin chào ZIP
data.bin = [0, 1, 2, -1]
```

**Giải thích từng bước:**

1. Tạo ZIP bằng `Files.createTempFile`, sau đó ghi qua `ZipOutputStream`.
2. Ghi directory entry `docs/` với payload rỗng.
3. Ghi `docs/hello.txt`, mã hóa chuỗi thành byte UTF-8 **trước khi nén**. Tên entry và nội dung là hai loại dữ liệu khác nhau.
4. Ghi `data.bin` gồm byte 0, 1, 2, `0xFF`. Java `byte` là số có dấu, nên `Arrays.toString` in byte `0xFF` thành `-1`; đây **không phải dấu hiệu EOF** của `ZipInputStream.read()`.
5. Khi output ZIP được đóng, archive đã có các thành phần ZIP hoàn chỉnh để mở bằng input ZIP.
6. `getNextEntry()` lần lượt trả ba entry. Vòng `zin.read(buffer)` lấy **đúng byte đã giải nén của một entry** tới `-1`.
7. Ví dụ dùng `ByteArrayOutputStream` để đơn giản hóa cách in. Với entry lớn hoặc ZIP không tin cậy, **không gom toàn bộ nội dung vào RAM**; phải xử lý theo luồng và áp giới hạn số byte (xem phần bảo mật).

Dùng `try-with-resources` giúp đóng cả wrapper ZIP và underlying streams khi bình thường hoặc khi có lỗi. Đoạn `finally` xóa tệp tạm trong điều kiện hệ điều hành cho phép.

## 6. `ZipEntry`: đọc và đặt metadata đúng cách

`ZipEntry` biểu diễn thông tin về một thành phần ZIP. Các API hữu ích:

| API | Ý nghĩa | Lưu ý |
| --- | --- | --- |
| `getName()` | Tên entry bên trong ZIP | **Không được tin cậy** khi dùng làm đường dẫn hệ điều hành |
| `isDirectory()` | Có tên kết thúc bằng `/` hay không | Không có entry thư mục riêng vẫn có thể có thư mục ngầm |
| `getSize()` | Kích thước dữ liệu **sau giải nén** | Có thể trả `-1` khi không được biết trong local header |
| `getCompressedSize()` | Kích thước dữ liệu nén | Có thể trả `-1` khi chưa biết |
| `getCrc()` | CRC-32 32-bit của nội dung chưa nén | Có thể trả `-1` khi chưa biết |
| `getMethod()` | `STORED` hoặc `DEFLATED` | Phụ thuộc entry và định dạng ZIP |
| `getTime()`, `getLastModifiedTime()` | Timestamp entry nếu có | Độ chính xác và khả năng bảo toàn có thể khác |
| `getComment()`, `getExtra()` | Comment/extra fields nếu được cung cấp | Không phải lúc nào stream reader cũng nhận đủ central-directory metadata |
| `setMethod()`, `setSize()`, `setCrc()` | Metadata cho entry khi ghi | Phải hợp lệ, đặc biệt với `STORED` |

Một `ZipEntry` có thể được đọc từ `zin.getNextEntry()` hoặc tạo bằng `new ZipEntry("path/in/archive")`. Nó không nắm quyền sở hữu `ZipInputStream` hoặc `ZipOutputStream`.

### 6.1. Vì sao `getSize()` có thể là `-1`?

Khi ghi ZIP với DEFLATED, writer có thể chưa biết trước độ dài giải nén, độ dài nén và CRC của entry. Các giá trị ấy có thể được đặt trong **data descriptor** sau payload hoặc central directory cuối archive.

`ZipInputStream` là reader **tuần tự**, thường lấy metadata từ local file header. Vì thế `entry.getSize()` hoặc `entry.getCrc()` có thể bằng `-1` khi mới bắt đầu đọc entry, mặc dù entry đó hoàn toàn hợp lệ.

Không viết code kiểu `new byte[(int) entry.getSize()]` khi `getSize()` chưa biết. Đếm **byte thực tế khi giải nén**, vừa để xử lý dữ liệu, vừa để chống ZIP bomb.

### 6.2. Tên entry không nhất thiết là đường dẫn an toàn

Tên `../secret.txt`, `/etc/config`, hoặc các chuỗi đặc biệt khác có thể có trong ZIP nhận từ nguồn ngoài. Khi dùng chúng để ghi lên đĩa phải kiểm tra giới hạn root (phần bảo mật). Không lấy `ZipEntry.getName()` rồi gọi trực tiếp `destination.resolve(name)` và ghi file mà không kiểm tra.

## 7. `DEFLATED` và `STORED`: hai chế độ phổ biến

`ZipEntry.DEFLATED` là phương thức nén DEFLATE, thường được `ZipOutputStream` dùng mặc định. `ZipEntry.STORED` lưu byte không nén.

| Thuộc tính | `DEFLATED` | `STORED` |
| --- | --- | --- |
| Nén nội dung | Có | Không |
| CPU khi ghi/đọc | Có chi phí nén/giải nén | Không cần DEFLATE |
| Dung lượng archive | Có thể nhỏ hơn hoặc lớn hơn chút với dữ liệu khó nén | Gần dung lượng nguyên bản cộng metadata ZIP |
| Phải biết size/CRC **trước** `putNextEntry()` | Thường không cần | **Có**; cần size, compressed size và CRC |
| Khi phù hợp | Text, JSON, dữ liệu lặp | Dữ liệu đã nén, cần tránh nén lại, hoặc yêu cầu định dạng |

Ví dụ DEFLATED:

```java
ZipEntry entry = new ZipEntry("notes.txt");
entry.setMethod(ZipEntry.DEFLATED);
zout.putNextEntry(entry);
zout.write(data);
zout.closeEntry();
```

Đây là snippet với `ZipOutputStream zout` và `byte[] data` đã có. Khi không đặt trước kích thước/CRC, writer có thể tính sau khi hoàn tất entry.

### 7.1. Ghi STORED phải tính trước CRC và kích thước

```java
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;

// Bên trong phương thức có ZipOutputStream zout và byte[] data:
CRC32 crc = new CRC32();
crc.update(data);

ZipEntry entry = new ZipEntry("already-compressed.bin");
entry.setMethod(ZipEntry.STORED);
entry.setSize(data.length);
entry.setCompressedSize(data.length);
entry.setCrc(crc.getValue());

zout.putNextEntry(entry);
zout.write(data);
zout.closeEntry();
```

`STORED` yêu cầu **size bằng compressed size** và CRC-32 đã xác định **trước khi ghi entry**. Thiếu metadata có thể làm `putNextEntry()` ném `ZipException`. Nếu byte ghi thực tế không khớp size/CRC đã khai báo, quá trình ghi/đóng entry cũng có thể thất bại.

Với dữ liệu rất lớn mà chưa biết CRC, có thể phải đọc một lượt để tính CRC rồi đọc lại lượt hai, hoặc chuyển sang DEFLATED để cho writer tính metadata sau. Không nên đọc toàn bộ file lớn vào một `byte[]` chỉ để tính CRC.

### 7.2. Compression level

`ZipOutputStream.setLevel(int level)` đặt mức nén DEFLATED cho các entry tiếp theo, thường từ `Deflater.NO_COMPRESSION` (0) tới `Deflater.BEST_COMPRESSION` (9), hoặc `Deflater.DEFAULT_COMPRESSION` (-1). Mức cao có thể tốn CPU hơn mà chưa chắc làm file nhỏ đáng kể; cần benchmark trên dữ liệu thực tế. Level không phải cờ bảo mật hoặc độ “chuẩn” của ZIP.

## 8. ZIP entry name và character encoding

Tên entry là metadata có thể chứa Unicode; Java hỗ trợ constructor có `Charset`:

```java
ZipOutputStream zout = new ZipOutputStream(
        Files.newOutputStream(zipPath), StandardCharsets.UTF_8);

ZipInputStream zin = new ZipInputStream(
        Files.newInputStream(zipPath), StandardCharsets.UTF_8);
```

Đây là **hai đoạn tạo stream riêng biệt**; không mở ghi và đọc chồng lên cùng tệp ZIP đang được tạo như một quy trình thường lệ. Hãy hoàn tất/đóng writer trước khi mở reader.

Phân biệt:

- `Charset` của `ZipInputStream`/`ZipOutputStream` điều khiển **mã hóa tên entry và thông tin ZIP liên quan** theo quy tắc format.
- Nội dung một entry là **mảng byte tùy ý**. Nếu đó là text, code phải tự chọn charset khi `String.getBytes` hoặc `new String(bytes, charset)`.
- ZIP từ các công cụ cũ đôi khi dùng code page khác UTF-8; nếu metadata/flag ZIP không tương thích, tên entry có thể đọc sai hoặc ném lỗi. Chọn charset dựa trên đặc tả/nguồn archive, không thử đoán bằng tên ngẫu nhiên.
- Khi ZIP đánh dấu tên entry là UTF-8 theo chuẩn, cờ này ảnh hưởng cách reader giải mã; constructor `Charset` không phải “ép mọi entry bất chấp flag”.

Tên entry nên sử dụng dấu `/` theo quy ước ZIP. Không dựa vào `File.separator` của hệ điều hành khi dựng tên nội bộ trong archive.

## 9. `ZipInputStream` hay `ZipFile`?

Cùng đọc ZIP nhưng phục vụ nhu cầu khác nhau:

| Tiêu chí | `ZipInputStream` | `ZipFile` |
| --- | --- | --- |
| Nguồn dữ liệu | Một `InputStream` | Một tệp ZIP truy cập được qua đường dẫn hoặc `File` |
| Truy cập entry | Tuần tự qua `getNextEntry()` | Liệt kê và lấy entry cụ thể qua `getEntry()`/`entries()` |
| Đọc dữ liệu | `read()` trên stream đang trỏ tới entry hiện tại | `getInputStream(entry)` trả stream nội dung entry |
| Metadata | Chủ yếu từ local file header | Có thể dựa vào central directory, đầy đủ hơn |
| Phù hợp | Upload/network stream, giải nén tuần tự | Chọn file bất kỳ trong archive, truy cập lặp lại |
| Vòng đời | Một stream duy nhất; xử lý entry theo thứ tự | Đóng `ZipFile` và các entry streams đúng cách |

`ZipInputStream` không phải công cụ tốt nếu cần mở `report.csv` ở cuối archive ngay mà không duyệt các entry đứng trước. Khi file ZIP đã nằm trên đĩa và cần random access theo tên, `ZipFile` thường phù hợp hơn.

Cũng có lựa chọn **Zip File System** (`FileSystems.newFileSystem` với provider `jar`) để thao tác `Path` bên trong archive; đó là mô hình khác với đọc/ghi tuần tự ở bài này.

## 10. Giải nén an toàn: chống Zip Slip

Khi nhận ZIP từ người dùng, một entry có thể tên `../../outside.txt`. Nếu ứng dụng nối tên đó với thư mục đích mà không kiểm tra, nó có thể ghi **ra ngoài thư mục được phép**. Đây là lỗ hổng **Zip Slip**.

Luôn xác định root hợp lệ, chuẩn hóa path và từ chối path vượt ra ngoài root:

```java
Path root = destination.toAbsolutePath().normalize();
Path target = root.resolve(entry.getName()).normalize();

if (!target.startsWith(root)) {
    throw new IOException("ZIP entry escapes destination");
}
```

Đây là **đoạn minh họa**; `destination`, `entry` là biến đã có. Có thêm các yêu cầu:

1. Không chấp nhận đường dẫn tuyệt đối hoặc tên entry không hợp lệ.
2. Không chấp nhận thành phần `..` khiến target vượt root.
3. Từ chối các tên đặc biệt/định dạng path không hợp lệ trên hệ điều hành đích.
4. Tạo thư mục cha trước khi mở file đích.
5. Chọn chính sách trùng tên: `CREATE_NEW` để không âm thầm ghi đè file tồn tại; kiểm tra các entry trùng tên trong ZIP.
6. **Quan trọng:** kiểm tra `normalize()/startsWith()` chỉ bảo vệ ở mức đường dẫn lexical. Nếu thư mục giải nén đã có **symlink** hoặc có tiến trình độc hại cùng sửa cây thư mục, việc đi xuyên symlink/TOCTOU vẫn có thể khiến ghi ra ngoài root. An toàn hơn là giải nén vào **thư mục mới, riêng tư, không có symlink và không cho tác nhân khác sửa đồng thời**; với threat model cao cần cơ chế mở path tương đối với directory handle/an toàn symlink của nền tảng.

Không xem `ZipEntry.isDirectory()` là bằng chứng một entry an toàn: `ZipEntry` chỉ mang metadata, còn phép ghi thực tế dựa trên đường dẫn đã giải quyết.

## 11. Chống ZIP bomb: đếm byte thực tế, không tin metadata

ZIP bomb là archive rất nhỏ sau nén nhưng giải nén thành lượng dữ liệu cực lớn, hoặc có hàng triệu entry. `getSize()` có thể không biết trước hoặc không đáng tin cậy với input không xác thực.

Giới hạn tối thiểu cho một extraction service:

- **Số entry tối đa**, ví dụ 1.000.
- **Dung lượng sau giải nén của mỗi entry**, ví dụ 20 MiB.
- **Tổng dung lượng sau giải nén của mọi entry**, ví dụ 100 MiB.
- **Độ sâu/tổng độ dài đường dẫn** và số thư mục.
- **Thời gian CPU/timeout**, số lần truy cập, dung lượng đĩa và quota xử lý đồng thời.
- Có thể thêm ngưỡng tỷ lệ giải nén với định nghĩa đo rõ ràng, nhưng đừng chỉ dựa vào ratio: một archive hợp lệ nén rất tốt vẫn có ratio lớn.

Ví dụ vòng xử lý byte với quota thực tế:

```java
long currentSize = 0;
int n;

while ((n = zin.read(buffer)) != -1) {
    if (n > MAX_ENTRY_BYTES - currentSize
            || n > MAX_TOTAL_BYTES - totalSize) {
        throw new IOException("ZIP extraction size limit exceeded");
    }

    currentSize += n;
    totalSize += n;
    output.write(buffer, 0, n);
}
```

Đây là **snippet minh họa bên trong vòng xử lý một entry**, với `ZipInputStream zin`, `OutputStream output`, `byte[] buffer`, `long totalSize` và hằng số quota đã định nghĩa. Cách kiểm tra `n > quota - used` giúp tránh overflow khi cộng, với điều kiện các biến `used` ban đầu không âm và không vượt quota.

`Files.copy(zin, target)` hoặc `zin.transferTo(output)` rất tiện nhưng không tự gắn giới hạn byte sau giải nén theo chính sách nghiệp vụ của bạn. Với archive không tin cậy, nên đọc theo block và kiểm tra quota trước mỗi lần ghi.

### 11.1. Mẫu extraction có quota và kiểm tra đường dẫn

Phương thức dưới đây mang tính **minh họa theo phạm vi thiết kế**, không phải bảo đảm chống mọi đường tấn công filesystem đồng thời:

```java
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

static void extractIntoPrivateDirectory(
        InputStream zipData, Path privateRoot) throws IOException {

    final long MAX_ENTRY_BYTES = 20L * 1024 * 1024;
    final long MAX_TOTAL_BYTES = 100L * 1024 * 1024;
    final int MAX_ENTRIES = 1000;

    // privateRoot phải là thư mục mới, không có symlink và
    // không bị tác nhân khác sửa đồng thời.
    Path root = privateRoot.toAbsolutePath().normalize();
    long total = 0;
    int count = 0;
    byte[] buffer = new byte[8192];

    try (ZipInputStream zin = new ZipInputStream(zipData)) {
        ZipEntry entry;
        while ((entry = zin.getNextEntry()) != null) {
            if (++count > MAX_ENTRIES) {
                throw new IOException("Too many ZIP entries");
            }

            Path relative = Path.of(entry.getName());
            Path target = root.resolve(relative).normalize();
            if (relative.isAbsolute() || !target.startsWith(root)
                    || target.equals(root)) {
                throw new IOException("Unsafe ZIP entry path");
            }

            if (entry.isDirectory()) {
                Files.createDirectories(target);
            } else {
                Files.createDirectories(target.getParent());
                long entryBytes = 0;

                try (OutputStream out = Files.newOutputStream(
                        target, StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE)) {
                    int n;
                    while ((n = zin.read(buffer)) != -1) {
                        if (n > MAX_ENTRY_BYTES - entryBytes
                                || n > MAX_TOTAL_BYTES - total) {
                            throw new IOException("ZIP content too large");
                        }
                        out.write(buffer, 0, n);
                        entryBytes += n;
                        total += n;
                    }
                }
            }
            zin.closeEntry();
        }
    }
}
```

**Điều kiện sử dụng mẫu:**

- Caller phải **tạo sẵn một thư mục trống, riêng tư** rồi truyền `privateRoot` vào. Phương thức này không tự tạo root và không bảo vệ tuyệt đối nếu tác nhân khác có thể tạo symlink trong tree giữa các thao tác. Với nguồn untrusted có threat model mạnh, hãy dùng sandbox/cơ chế path traversal chống symlink phù hợp hệ điều hành.
- `Path.of(entry.getName())` có thể ném `InvalidPathException` với tên không hợp lệ; có thể bắt và chuyển thành lỗi archive không hợp lệ ở ranh giới API.
- `CREATE_NEW` từ chối việc ghi đè tệp đã tồn tại, kể cả khi entry khác trong ZIP có cùng tên. Đây là lựa chọn chính sách; với tệp tạm đang giải nén dở, khi xảy ra lỗi ứng dụng cần cleanup theo quy trình giao dịch riêng.
- Không dùng `closeEntry()` thay cho quota: nếu gặp entry quá lớn, ném exception để dừng giải nén thay vì cố drain toàn bộ archive.
- ZIP có thể chứa tên path mang semantics đặc thù OS; trên Windows cần chú ý tên thiết bị, ký tự `:`, trailing dots/spaces, phân biệt hoa thường và giới hạn path. Luôn kiểm tra trong môi trường đích.

Phương thức được trình bày để dạy các lớp bảo vệ chính, không phải một trình giải nén production hoàn chỉnh. Ứng dụng thực cần thêm quota về thời gian, số thư mục, file permissions, không gian đĩa, báo cáo tiến độ, cancellation, antivirus khi phù hợp, và kiểm thử cạnh tranh/symlink.

## 12. CRC-32 và tính toàn vẹn dữ liệu

CRC-32 trong ZIP dùng để phát hiện hư hỏng do truyền/lưu trữ không chủ ý. Khi đọc đầy đủ entry qua `ZipInputStream`, implementation có thể kiểm tra CRC và kích thước khai báo khi gặp phần kết thúc entry, rồi báo `ZipException` nếu không khớp.

Tuy nhiên:

- CRC-32 **không phải** chữ ký số, MAC hoặc mã xác thực; kẻ tấn công có thể thay nội dung rồi tính lại CRC.
- Không dùng CRC-32 để quyết định rằng ZIP an toàn hoặc đến từ nguồn đáng tin.
- Không tin rằng `getCrc()` đã sẵn sàng ngay tại đầu entry.
- Việc đọc/đóng ZIP có thể phát sinh `ZipException`, `EOFException` hoặc `IOException` nếu dữ liệu bị cắt hoặc hỏng. Phải xử lý đường lỗi, không lặng lẽ công bố tệp giải nén dở là đã hoàn tất.

Nếu cần bảo đảm tính xác thực, sử dụng một cơ chế chữ ký/MAC ở tầng ứng dụng hoặc định dạng container hỗ trợ xác thực, kèm quy trình kiểm chứng phù hợp.

## 13. ZIP không mặc định là mã hóa hay backup an toàn

`ZipOutputStream` trong Java chuẩn **không cung cấp tính năng tạo ZIP có mật khẩu/mã hóa**. Nó nén và đóng gói dữ liệu, không bảo mật nội dung trước người đọc. Tương tự, `ZipInputStream` của JDK không phải giải pháp tổng quát cho ZIP được mã hóa bằng các biến thể password/encryption phổ biến.

Không nên xem file `.zip` là an toàn chỉ vì khó đọc trực tiếp trong text editor. Nếu cần bảo mật, dùng mã hóa/authentication phù hợp ở lớp khác hoặc thư viện ZIP hỗ trợ format đã được thẩm định, kiểm tra nguồn gốc và cấu hình đúng cách.

Ngoài ra ZIP không phải định dạng lưu trữ toàn bộ metadata hệ điều hành theo cách thống nhất. Quyền POSIX, symlink, owner/group và các thuộc tính nâng cao có thể nằm trong extra fields hoặc convention do các chương trình khác nhau dùng; chỉ sử dụng `ZipEntry` của JDK không có nghĩa sẽ khôi phục chính xác mọi permission/symlink khi giải nén.

## 14. Stream I/O, buffer và việc dùng bộ nhớ

Ưu điểm của `ZipInputStream`/`ZipOutputStream` là **xử lý tuần tự theo stream**. Với tệp lớn, có thể dùng buffer 8 KiB hoặc 64 KiB thay vì đọc toàn bộ vào `byte[]`.

Ví dụ thêm tệp vào ZIP theo luồng (mẫu trong phương thức):

```java
try (InputStream fileIn = Files.newInputStream(sourceFile)) {
    zout.putNextEntry(new ZipEntry(entryName));
    byte[] buffer = new byte[8192];
    int n;
    while ((n = fileIn.read(buffer)) != -1) {
        zout.write(buffer, 0, n);
    }
    zout.closeEntry();
}
```

Giả sử `zout` là `ZipOutputStream` đang mở; `sourceFile` và `entryName` đã được kiểm tra. Không đưa một file vào ZIP dưới đường dẫn tuyệt đối của hệ điều hành. Tên entry nên là path tương đối, dùng dấu `/` và không chứa thông tin nhạy cảm.

Khi giải nén, cũng dùng một buffer cố định và ghi `output.write(buffer,0,n)`. Điều này giới hạn dung lượng **buffer trung gian**, nhưng phải cộng thêm quota thực tế của đích và số tác vụ cùng chạy.

Nếu tạo rất nhiều ZIP đồng thời, tổng bộ nhớ cần xem số `ZipOutputStream`/`ZipInputStream`, buffer do `Deflater`/`Inflater` sở hữu, file handles, số byte đang chờ và executor threads. Đóng stream khi xong để giải phóng tài nguyên kịp thời.

## 15. Các lỗi thường gặp

| Sai lầm | Hậu quả | Cách đúng |
| --- | --- | --- |
| Không gọi `putNextEntry()` trước khi `write()` | Không có entry hợp lệ để ghi payload | Mỗi entry: mở → ghi → đóng |
| Chỉ `flush()` rồi sử dụng archive chưa hoàn thành | ZIP thiếu central directory | `finish()` hoặc `close()` |
| Cho rằng `read()==-1` là hết toàn ZIP | Dừng sau entry đầu | Gọi `getNextEntry()` đến `null` |
| Đọc `getSize()` luôn phải dương | Metadata có thể chưa biết | Đếm byte thực tế |
| Tạo `byte[(int)getSize()]` không kiểm tra | Âm, overflow hoặc cạn RAM | Streaming + quota |
| Dùng `STORED` nhưng không đặt size/CRC | `ZipException` khi ghi | Tính trước metadata |
| Đặt `compressedSize` bằng `size` cho DEFLATED | Có thể sai vì dữ liệu nén dài khác | Để writer tính hoặc cung cấp metadata đúng |
| Viết tên entry bằng `File.separator` | ZIP không portable | Dùng dấu `/` |
| Coi `ZipEntry.getName()` là path an toàn | Zip Slip, ghi đè ra ngoài thư mục | Normalize + root check + chống symlink |
| Giải nén không giới hạn | ZIP bomb và đầy đĩa | Per-entry/total quotas và timeout |
| Dùng `available()` để biết entry đã hết | Sai ngữ nghĩa | Lặp `read` tới `-1` |
| Đọc text bằng charset mặc định | Chữ Unicode sai | Charset rõ ràng, ví dụ UTF-8 |
| Coi CRC như xác thực | Không chống sửa đổi có chủ ý | Chữ ký/MAC khi cần |
| Nghĩ ZIP mặc định mã hóa | Nội dung có thể đọc công khai | Mã hóa riêng hoặc giải pháp có xác thực |
| Bỏ qua lỗi khi đọc/đóng entry | Có thể chấp nhận tệp hỏng | Propagate lỗi, cleanup tệp dở |
| Giữ `ByteArrayOutputStream` cho mọi entry lớn | Heap phình to | Xử lý theo block/stream |
| Tên trùng nhau trong archive | Trích xuất ghi đè hoặc lỗi không thống nhất | Quy định duplicate policy từ đầu |

## 16. Bài tập và đáp án

**Bài 1.** Một ZIP có hai entry. Sau khi đọc `zin.read()` trả `-1`, có phải hết ZIP không?

**Đáp án:** Không; chỉ hết nội dung **entry hiện tại**. Cần `zin.getNextEntry()` để tới entry sau; `null` mới báo hết entry.

**Bài 2.** `ZipEntry.getSize()` trả `-1`. Archive có chắc bị hỏng không?

**Đáp án:** Không. Reader tuần tự có thể chưa biết size tại local file header; đếm dữ liệu đã giải nén khi đọc tới cuối entry.

**Bài 3.** Vì sao `ZipOutputStream` với `STORED` phải biết CRC-32 trước khi ghi?

**Đáp án:** Theo hợp đồng Java ZIP writer, entry STORED phải có size, compressed size và CRC được xác định trước khi `putNextEntry()`.

**Bài 4.** Có nên lấy `entry.getName()` rồi viết thẳng vào `outputDir.resolve(name)`?

**Đáp án:** Không. Tên có thể chứa `..`, path tuyệt đối hoặc tấn công qua symlink; cần giải nén vào root an toàn, normalize/check và phối hợp chống symlink.

**Bài 5.** ZIP có một entry giải nén thành 5 GiB dù file nén chỉ vài MiB. Dùng buffer 8 KiB có tự giải quyết nguy cơ không?

**Đáp án:** Không. Buffer trung gian nhỏ chỉ giảm RAM tức thời; vẫn có thể làm đầy đĩa hoặc tiêu tốn CPU. Cần quota tổng byte, byte mỗi entry, thời gian và số entry.

**Bài 6.** `ZipEntry` có tự chứa nội dung tệp không?

**Đáp án:** Không. Nó chỉ mô tả metadata; nội dung được đọc/ghi bằng các stream ZIP khi entry đang active.

**Bài 7.** Tại sao sau khi ghi các entry phải đóng `ZipOutputStream` hoặc gọi `finish()`?

**Đáp án:** Để hoàn tất các cấu trúc định dạng ZIP, đặc biệt central directory cuối archive, giúp reader nhận đủ entry.

**Bài 8.** ZIP có tên `docs/hello.txt` nhưng không có `docs/`. ZIP có nhất thiết không hợp lệ không?

**Đáp án:** Không. Entry tệp có thể mô tả thư mục cha ngầm; khi giải nén cần tự tạo các thư mục cha.

**Bài 9.** Nếu dữ liệu entry là tiếng Việt, constructor `ZipInputStream(..., UTF_8)` có tự giải mã nội dung thành `String` không?

**Đáp án:** Không. Charset của stream ZIP áp dụng cho tên entry theo quy tắc metadata. Payload vẫn là byte, cần decode bằng charset đã thỏa thuận.

**Bài 10.** Có thể dùng CRC-32 để xác minh ZIP đến từ người gửi đáng tin không?

**Đáp án:** Không. CRC phát hiện lỗi ngẫu nhiên, không phải cơ chế chứng thực chống kẻ tấn công.

## 17. Checklist khi ứng dụng tạo hoặc nhận ZIP

1. Chọn `ZipOutputStream` cho ghi ZIP tuần tự, `ZipInputStream` cho đọc tuần tự, `ZipFile` cho truy cập ngẫu nhiên một archive trên đĩa.
2. Mỗi entry dùng đúng thứ tự `putNextEntry` → `write` → `closeEntry`; hoàn tất archive bằng `close` hoặc `finish`.
3. Đọc bằng `getNextEntry()` đến `null`, và `read()` đến `-1` trong **mỗi entry**.
4. Chọn charset rõ ràng cho tên entry và cho nội dung văn bản.
5. Khi dùng `STORED`, thiết lập size, compressed size, CRC trước khi ghi; `DEFLATED` phù hợp khi không biết trước metadata.
6. Không giả định `getSize/getCrc` luôn biết khi đọc stream.
7. Với ZIP untrusted, chặn Zip Slip, path tuyệt đối, duplicate entries, symlink/TOCTOU và file-name đặc thù hệ điều hành.
8. Giới hạn số entry, byte mỗi entry, tổng byte giải nén, chiều dài path, thời gian và số archive xử lý đồng thời.
9. Xử lý `ZipException`, `IOException`, lỗi CRC và cleanup tệp giải nén chưa hoàn chỉnh; không publish kết quả trước khi toàn bộ policy thành công.
10. Không coi nén là mã hóa, không coi CRC là xác thực, và không giả định metadata filesystem được phục hồi đầy đủ.

## Tài liệu Java chính thức

- Java SE 17 — `ZipInputStream`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/ZipInputStream.html
- Java SE 17 — `ZipOutputStream`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/ZipOutputStream.html
- Java SE 17 — `ZipEntry`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/ZipEntry.html
- Java SE 17 — `ZipFile`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/ZipFile.html
- Java SE 17 — `CRC32`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/CRC32.html
- Java SE 17 — `Files`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="compression-and-archives-004">putNextEntry, closeEntry và hoàn tất archive</a>

<details>
<summary>Xem chi tiết</summary>

# `putNextEntry`, `closeEntry` và hoàn tất ZIP archive trong Java

## 1. Bài toán: một ZIP hợp lệ cần nhiều hơn việc ghi byte

Trong Java Core, package `java.util.zip` hỗ trợ tạo và đọc tệp ZIP. Khi đóng gói nhiều file vào một archive, ứng dụng không thể chỉ nối byte của từng file rồi đổi phần mở rộng thành `.zip`. Định dạng ZIP chứa **các entry**, metadata, thông tin kiểm tra tính toàn vẹn và **central directory** cho phép công cụ đọc biết archive gồm những gì.

`ZipOutputStream` là lớp dùng để **ghi tuần tự ZIP archive** vào một `OutputStream`. Ba thao tác trọng tâm:

- **`putNextEntry(ZipEntry entry)`**: bắt đầu ghi một entry mới, đưa stream đến phần dữ liệu của entry đó.
- **`closeEntry()`**: kết thúc entry hiện tại, hoàn thành thông tin cần thiết cho entry và chuẩn bị trạng thái để ghi entry kế tiếp.
- **`finish()`**: hoàn tất **toàn bộ ZIP archive** (kể cả central directory) nhưng **không đóng output stream bên dưới**.
- Ngoài ra, **`close()`** của `ZipOutputStream` hoàn tất archive (nếu chưa hoàn tất) **và đóng cả stream bên dưới**.

**Điểm mấu chốt:** `closeEntry()` đóng **một thành phần** của ZIP; `finish()` và `close()` hoàn thành **cấu trúc toàn archive**. Tệp ZIP có thể có mọi entry đã được `closeEntry()` nhưng vẫn chưa là archive ZIP hoàn chỉnh nếu chưa ghi phần kết thúc/central directory.

Ví dụ sử dụng chuẩn:

```java
try (ZipOutputStream zip =
         new ZipOutputStream(Files.newOutputStream(zipPath))) {

    zip.putNextEntry(new ZipEntry("a.txt"));
    zip.write("A".getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();

    zip.putNextEntry(new ZipEntry("b.txt"));
    zip.write("B".getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();

} // close() tự hoàn tất archive và đóng stream bên dưới.
```

Đây là **đoạn minh họa**, chưa phải chương trình độc lập: cần khai báo `zipPath` và import API. Các ví dụ chạy hoàn chỉnh xuất hiện ở những phần sau.

## 2. `ZipOutputStream`, `ZipEntry` và vòng đời archive

Sơ đồ các trạng thái thao tác thường dùng:

```text
Mở ZipOutputStream
       |
       v
putNextEntry("a.txt")
       |
       v
write(bytes của a.txt)
       |
       v
closeEntry()
       |
       +------------------------------+
       |                              |
       v                              |
putNextEntry("b.txt")                 |
       |                              |
       v                              |
write(bytes của b.txt)                |
       |                              |
       v                              |
closeEntry()                          |
       |                              |
       +--> ... entry tiếp theo ... --+
       |
       v
finish() hoặc close()
       |
       v
Central directory + thông tin kết thúc
       |
       v
ZIP archive hoàn chỉnh
```

**`ZipEntry`** mô tả một thành phần trong archive. Nó mang tên entry (ví dụ `docs/readme.txt`), thời gian sửa đổi, phương pháp nén, kích thước và CRC-32 khi có. **`ZipOutputStream`** quản lý cách dữ liệu entry cùng metadata được ghi vào luồng ZIP.

Một ZIP nhiều file không phải là nhiều `ZipOutputStream` nối liền nhau: nó là **một `ZipOutputStream`**, có **nhiều lần `putNextEntry`/`closeEntry`**, rồi **một lần hoàn tất archive**.

| Phạm vi | Đối tượng/thao tác | Sự kiện kết thúc |
| --- | --- | --- |
| Một file/thư mục trong ZIP | `ZipEntry` | `closeEntry()` |
| Cả tệp ZIP gồm nhiều entry | `ZipOutputStream` | `finish()` hoặc `close()` |
| Đích nhận byte vật lý | `OutputStream` bên dưới | `close()` nếu quyền sở hữu thuộc về ZIP stream; hoặc bên sở hữu tự đóng |

**Thứ tự rất quan trọng**: `write` ngay trước `putNextEntry` không mang nghĩa “ghi một file ZIP”. Muốn ghi dữ liệu entry, phải mở entry trước.

## 3. ZIP bên trong chứa những phần nào?

Ở mức khái niệm, một ZIP archive thường có:

```text
+----------------------------+
| Local file header: a.txt   |
+----------------------------+
| Compressed data: a.txt     |
+----------------------------+
| (optional data descriptor) |
+----------------------------+
| Local file header: b.txt   |
+----------------------------+
| Compressed data: b.txt     |
+----------------------------+
| (optional data descriptor) |
+----------------------------+
| Central directory records  |
| a.txt, b.txt, offsets ...  |
+----------------------------+
| End of central directory   |
+----------------------------+
```

Đây là sơ đồ đơn giản, không đầy đủ tất cả biến thể của chuẩn ZIP (ví dụ ZIP64, extra fields, comments). Nhưng nó cho thấy vì sao **đóng entry cuối không đồng nghĩa archive được kết thúc**.

- **Local file header** đứng gần dữ liệu từng entry.
- **Data descriptor** có thể xuất hiện khi kích thước/CRC chưa biết trước và được ghi sau dữ liệu.
- **Central directory** chứa bảng metadata tổng hợp, offset và số entry.
- **End of central directory** kết thúc cấu trúc ZIP.

`ZipFile` thường dựa vào central directory để truy cập entry ngẫu nhiên. Vì thế nếu bạn chưa `finish()` hoặc `close()` mà cố mở archive qua `ZipFile`, không có cơ sở bảo đảm archive hợp lệ. Trái lại, `ZipInputStream` có thể đọc **một số local entry data** trong luồng chưa hoàn chỉnh, nhưng việc đọc một phần như vậy **không chứng minh toàn archive đúng cấu trúc**.

**Không dùng `flush()` như thay thế cho `finish()`.** `flush` chỉ chuyển các byte đã có qua một số tầng đệm theo hợp đồng stream, không có nghĩa central directory đã được ghi.

## 4. `putNextEntry(ZipEntry)`: bắt đầu entry mới

Cú pháp:

```java
ZipEntry entry = new ZipEntry("docs/readme.txt");
zip.putNextEntry(entry);
zip.write(content);
zip.closeEntry();
```

Đây là đoạn minh họa với `zip` và `content` đã được khai báo ở nơi khác.

Khi gọi `putNextEntry`:

1. `ZipOutputStream` bắt đầu entry có tên xác định.
2. Ghi thông tin cần thiết để xác định entry trong ZIP.
3. Thiết lập stream để những lời gọi `write(...)` tiếp theo thuộc về **entry mới**.
4. Nếu trước đó còn một entry đang hoạt động, `putNextEntry` sẽ **đóng entry trước** trước khi bắt đầu entry mới theo hợp đồng API Java.

Bước 4 giúp code có thể tự chuyển sang entry mới, nhưng **không phải lý do để bỏ `closeEntry()` trong code rõ ràng**. Gọi `closeEntry` tường minh giúp quản lý vòng đời, nơi xử lý lỗi và điểm kiểm tra metadata dễ hiểu hơn.

### Tên entry là đường dẫn logic trong ZIP

`new ZipEntry("docs/readme.txt")` có tên `docs/readme.txt`. Dấu gạch chéo `/` là ký hiệu thư mục thường dùng trong ZIP và **không phụ thuộc dấu phân cách đường dẫn hệ điều hành**.

| Tên entry | Ý nghĩa thông dụng |
| --- | --- |
| `report.txt` | File ở gốc ZIP |
| `docs/readme.txt` | File trong thư mục logic `docs/` |
| `docs/` | Entry biểu diễn thư mục |
| `images/a.png` | File bên trong thư mục logic `images/` |

`ZipEntry` **không tự tạo thư mục thật trên ổ đĩa**; nó chỉ lưu tên và metadata vào archive. Khi giải nén, chương trình đọc ZIP quyết định tạo cấu trúc thư mục tương ứng.

Nếu muốn thể hiện **thư mục rỗng** trong ZIP, cần một entry có tên kết thúc `/`, ví dụ `new ZipEntry("empty/")`, thường không ghi byte dữ liệu. Nếu chỉ chứa entry `docs/readme.txt` mà không chứa `docs/`, nhiều công cụ giải nén vẫn tạo thư mục cha khi cần. Nhưng một **thư mục rỗng** sẽ không được biểu diễn nếu không có entry thư mục riêng.

### Tên trùng và metadata

Trong một `ZipOutputStream`, các tên entry trùng nhau không được chấp nhận; có thể gặp `ZipException: duplicate entry: ...`. Ứng dụng đóng gói cần xác định chiến lược với tên trùng: từ chối, đổi tên hoặc chọn một nguồn duy nhất trước khi ghi. **Không thể “mở lại cùng entry để ghi thêm” sau khi đã kết thúc nó** chỉ bằng cách `putNextEntry` lần nữa cùng tên.

Nếu không đặt thời gian sửa đổi cho `ZipEntry`, implementation có thể dùng thời gian hiện tại. Vì vậy **byte ZIP đầu ra không nhất thiết giống hệt nhau qua các lần chạy** dù nội dung các file giống nhau. Muốn tạo ZIP có byte output tái lập trong build pipeline, phải kiểm soát thứ tự entry, timestamps, tên, metadata, charset, thuật toán/mức nén và môi trường tương thích.

## 5. `write` chỉ ghi dữ liệu thuộc entry hiện tại

Giữa `putNextEntry` và `closeEntry`, `ZipOutputStream.write(...)` ghi nội dung **của entry đang mở**.

```java
zip.putNextEntry(new ZipEntry("hello.txt"));
zip.write("Hello".getBytes(StandardCharsets.UTF_8));
zip.write(" World".getBytes(StandardCharsets.UTF_8));
zip.closeEntry();
```

Hai `write` liên tiếp tạo nội dung giải nén là `Hello World` **trong cùng entry**, không phải hai file mới.

Đối với file lớn, nên truyền byte theo từng khối từ `InputStream`:

```java
try (InputStream input = Files.newInputStream(source)) {
    zip.putNextEntry(new ZipEntry("payload.bin"));

    byte[] buffer = new byte[8192];
    int count;
    while ((count = input.read(buffer)) != -1) {
        zip.write(buffer, 0, count);
    }

    zip.closeEntry();
}
```

Đây là đoạn minh họa: `zip`, `source` cần khai báo, kèm import và xử lý ngoại lệ. Dùng vòng lặp giúp không cần `Files.readAllBytes` với file lớn. `closeEntry` phải được gọi **sau khi sao chép đủ dữ liệu**; không đóng entry trong mỗi lượt đọc.

### Không dùng Writer tùy tiện trực tiếp trên ZIP stream

`ZipOutputStream` là luồng **byte**, không phải luồng ký tự. Nếu nội dung là text, hãy chỉ định charset:

```java
byte[] utf8 = "Xin chào".getBytes(StandardCharsets.UTF_8);
zip.write(utf8);
```

Không dựa vào charset mặc định của máy. Nếu bọc `OutputStreamWriter` hoặc `BufferedWriter` bên trên `ZipOutputStream`, cần hiểu việc **buffered writer có thể giữ byte chưa flush** và `writer.close()` sẽ đóng cả ZIP stream trước khi ghi các entry tiếp theo. Đối với nhiều entry nhỏ, dùng `getBytes(StandardCharsets.UTF_8)` hoặc một tầng adapter được quản lý lifecycle cẩn thận thường đơn giản hơn.

## 6. `closeEntry()`: kết thúc file, chưa kết thúc archive

Chữ ký:

```java
void closeEntry() throws IOException;
```

`closeEntry()` hoàn tất entry hiện tại và đưa `ZipOutputStream` vào trạng thái có thể bắt đầu entry kế tiếp. Với entry dùng phương pháp nén `DEFLATED`, việc kết thúc entry yêu cầu hoàn tất luồng nén của entry đó và ghi các thông tin cần thiết để giải nén/kiểm tra dữ liệu. Với `STORED`, việc kết thúc có thể kiểm tra kích thước/CRC theo metadata đã khai báo.

**Điều `closeEntry()` không làm:**

- Không tạo ra entry tiếp theo.
- Không tự đóng `OutputStream` bên dưới.
- Không hoàn tất central directory toàn archive.
- Không hủy hoặc mở lại `ZipOutputStream`.
- Không bảo đảm file đích đã được ghi bền vững xuống ổ đĩa.

Có thể gọi nhiều lần `putNextEntry`, `write`, `closeEntry` trên **cùng** stream cho các tên entry khác nhau.

Đặt tên biến giúp tách hai cấp rõ ràng:

```text
putNextEntry("a.txt")  -> mở entry a
write(bytes)           -> ghi data cho a
closeEntry()           -> đóng a

putNextEntry("b.txt")  -> mở entry b
write(bytes)           -> ghi data cho b
closeEntry()           -> đóng b

finish() / close()     -> hoàn tất ZIP chứa a và b
```

Nếu không còn entry đang hoạt động, gọi `closeEntry()` không tạo ra một archive mới. Đừng dùng nó thay vì `finish()`.

## 7. `finish()` so với `close()` và `flush()`

Ba thao tác ở cuối thường bị nhầm:

| Lệnh | Hoàn tất ZIP? | Đóng `ZipOutputStream`? | Đóng stream bên dưới? | Khi dùng |
| --- | --- | --- | --- | --- |
| `closeEntry()` | **Không** | Không | Không | Sau mỗi entry |
| `flush()` | **Không** | Không | Không | Đẩy byte qua luồng theo hợp đồng flush; không hoàn tất archive |
| `finish()` | **Có** | Không đóng stream bên dưới | **Không** | Cần hoàn tất ZIP nhưng vẫn giữ quyền sở hữu sink |
| `close()` | **Có** | **Có** | **Có** | ZIP stream sở hữu sink; thích hợp với try-with-resources |

Cần đọc cột `finish()` chính xác: nó **kết thúc phần dữ liệu ZIP** và không đóng stream được bọc. Đối tượng `ZipOutputStream` không nên được tái sử dụng để bắt đầu thêm entry sau khi đã `finish()`, vì cấu trúc ZIP đã kết thúc. `finish()` **không phải một “checkpoint” để có thể append entry**.

`close()` vừa hoàn tất ZIP vừa đóng chính nó và stream đích. Nếu đang viết ra file qua `Files.newOutputStream(zipPath)`, **`try-with-resources` là lựa chọn mặc định**: code thoát scope bình thường hay có ngoại lệ đều đóng tài nguyên; nếu có lỗi I/O khi hoàn tất/đóng thì vẫn có thể phát sinh `IOException`, và archive lúc đó có thể không hợp lệ.

**Không nên gọi `finish()` bắt buộc trước mọi `close()`.** `close()` có trách nhiệm hoàn tất archive, vì vậy cách làm phổ biến chỉ cần đóng ZIP stream đúng cách. Gọi `finish()` tường minh hữu ích khi bạn cần xác định thời điểm kết thúc archive nhưng **chưa muốn đóng đích**.

### Khi nào dùng `finish()` thực tế?

Nếu một thư viện nhận `OutputStream` do caller sở hữu và trả về một ZIP hoàn chỉnh **mà không được phép đóng stream của caller**, có thể:

1. Tạo `ZipOutputStream` bọc output đã được cung cấp.
2. Ghi toàn bộ entry.
3. Gọi `finish()`.
4. **Không** gọi `close()` trên ZipOutputStream nếu điều đó sẽ đóng output của caller.
5. Để caller quản lý việc flush/close output theo hợp đồng của API.

Cần thiết kế rõ ownership và cleanup nếu có ngoại lệ khi ghi archive. Nếu cần giải phóng cả tài nguyên nén mà vẫn giữ đích mở, cách an toàn còn phụ thuộc cấu trúc lớp/adapter và vòng đời mong muốn; **đừng mặc định `close()` sẽ giữ stream bên dưới mở**.

## 8. Ví dụ Java 17+ độc lập: tạo ZIP nhiều entry và kiểm tra central directory

Ví dụ đầu tiên viết một ZIP vào **file tạm**, gồm một entry thư mục và hai file text. Nó gọi `closeEntry()` rõ ràng sau mỗi entry. Sau khi `try-with-resources` đóng `ZipOutputStream`, dùng `ZipFile` đọc lại từ ZIP đã hoàn tất.

Chương trình kiểm chứng:

- ZIP có đúng ba entry.
- Hai entry text đọc được nội dung mong đợi.
- Entry thư mục được nhận dạng bằng `isDirectory()`.

**Tệp `ZipLifecycleDemo.java`:**

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ZipLifecycleDemo {
    public static void main(String[] args) throws IOException {
        Path zipPath = Files.createTempFile("zip-lifecycle-", ".zip");

        try {
            try (ZipOutputStream zip = new ZipOutputStream(
                    Files.newOutputStream(zipPath),
                    StandardCharsets.UTF_8)) {

                zip.putNextEntry(new ZipEntry("docs/"));
                zip.closeEntry();

                zip.putNextEntry(new ZipEntry("docs/a.txt"));
                zip.write("Xin chao".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();

                zip.putNextEntry(new ZipEntry("docs/b.txt"));
                zip.write("Java 17".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            } // close(): hoàn tất central directory rồi đóng file.

            try (ZipFile archive = new ZipFile(
                    zipPath.toFile(), StandardCharsets.UTF_8)) {

                System.out.println(
                        "So entry: " + archive.size());

                ZipEntry directory = archive.getEntry("docs/");
                System.out.println(
                        "Entry thu muc: " + directory.isDirectory());

                ZipEntry a = archive.getEntry("docs/a.txt");
                try (var input = archive.getInputStream(a)) {
                    System.out.println("a.txt: "
                            + new String(input.readAllBytes(),
                                    StandardCharsets.UTF_8));
                }

                ZipEntry b = archive.getEntry("docs/b.txt");
                try (var input = archive.getInputStream(b)) {
                    System.out.println("b.txt: "
                            + new String(input.readAllBytes(),
                                    StandardCharsets.UTF_8));
                }
            }
        } finally {
            Files.deleteIfExists(zipPath);
        }
    }
}
```

Chạy với **JDK 17+**:

```bash
javac ZipLifecycleDemo.java
java ZipLifecycleDemo
```

**Stdout khi tạo/đọc ZIP thành công:**

```text
So entry: 3
Entry thu muc: true
a.txt: Xin chao
b.txt: Java 17
```

Giải thích từng bước:

1. `Files.createTempFile` tạo nơi lưu ZIP thử nghiệm.
2. `ZipOutputStream` bọc stream ghi file và dùng `UTF-8` cho **tên entry**.
3. `docs/` có tên kết thúc `/`, không chứa nội dung; `closeEntry` kết thúc entry thư mục.
4. `docs/a.txt`, `docs/b.txt` lần lượt được mở bằng `putNextEntry`, ghi byte UTF-8 rồi đóng bằng `closeEntry`.
5. Thoát `try` gọi `zip.close()` tự động, giúp hoàn tất archive.
6. `ZipFile` đọc một ZIP hợp lệ với central directory; `getEntry` lấy đúng tên và `getInputStream` trả nội dung giải nén.
7. `finally` xóa file tạm sau khi `ZipFile` đã đóng. Hành vi xóa có thể bị ảnh hưởng nếu tiến trình khác can thiệp vào file, nhưng chương trình không phụ thuộc tên file temp ngẫu nhiên.

Vì ví dụ kiểm tra entry theo tên, không in thứ tự mà `ZipFile.entries()` liệt kê, đầu ra không phụ thuộc thời gian tạo file hay kích thước ZIP nén thực tế.

## 9. Ví dụ Java 17+: chứng minh `finish()` không đóng sink

Để thấy khác biệt giữa `finish()` và `close()`, ta tạo một stream bên dưới có cờ `closed`. Sau `finish()`, cờ chưa đổi. Chỉ khi gọi `close()` trên ZIP stream, sink mới bị đóng.

**Tệp `ZipFinishDemo.java`:**

```java
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.io.ByteArrayInputStream;

public class ZipFinishDemo {
    private static class TrackedOutputStream extends FilterOutputStream {
        boolean closed = false;

        TrackedOutputStream(OutputStream destination) {
            super(destination);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    public static void main(String[] args) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        TrackedOutputStream destination = new TrackedOutputStream(bytes);
        ZipOutputStream zip = new ZipOutputStream(
                destination, StandardCharsets.UTF_8);

        try {
            zip.putNextEntry(new ZipEntry("one.txt"));
            zip.write("OK".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();

            zip.finish();
            System.out.println("Sink closed after finish: "
                    + destination.closed);

            try (ZipInputStream input = new ZipInputStream(
                    new ByteArrayInputStream(bytes.toByteArray()),
                    StandardCharsets.UTF_8)) {
                ZipEntry entry = input.getNextEntry();
                System.out.println("First entry: " + entry.getName());
                System.out.println("Body: " + new String(
                        input.readAllBytes(), StandardCharsets.UTF_8));
                input.closeEntry();
            }
        } finally {
            zip.close();
        }

        System.out.println("Sink closed after close: "
                + destination.closed);
    }
}
```

Biên dịch/chạy:

```bash
javac ZipFinishDemo.java
java ZipFinishDemo
```

**Stdout khi thành công:**

```text
Sink closed after finish: false
First entry: one.txt
Body: OK
Sink closed after close: true
```

**Phân tích:**

- `zip.finish()` ghi phần kết thúc ZIP; `TrackedOutputStream.closed` vẫn `false`.
- `ZipInputStream` đọc từ bản sao `byte[]` lúc ZIP đã được hoàn tất.
- `zip.close()` được gọi trong `finally`, đóng ZIP stream và stream bên dưới; cờ `closed` thành `true`.
- `ByteArrayOutputStream.close()` trên JDK chuẩn không khiến dữ liệu đã ghi không còn truy cập được; ở đây ta dùng wrapper theo dõi để quan sát việc phương thức `close` thực sự được gọi xuống sink.

**Đây là ví dụ minh họa ownership, không phải khuyến nghị luôn gọi cả `finish()` và `close()`.** Nếu mục tiêu là giữ stream bên dưới mở cho caller, mã production phải xác định rõ ai sở hữu và ai có quyền đóng stream, thay vì dùng chính mẫu `finally { zip.close(); }` khi caller không cho phép đóng sink.

## 10. `DEFLATED` và `STORED`: metadata khác nhau

Hai phương pháp phổ biến do `ZipEntry` hỗ trợ:

| Phương pháp | Đặc điểm | Metadata phải cung cấp trước? |
| --- | --- | --- |
| `ZipEntry.DEFLATED` | Nén DEFLATE, mặc định của `ZipOutputStream` | Thường có thể để stream tự tính size/CRC khi ghi |
| `ZipEntry.STORED` | Lưu byte nguyên dạng, không nén | **Size, compressed size và CRC-32 phải xác định trước** |

Với `DEFLATED`, nếu chưa đặt size, compressed size hoặc CRC, `ZipOutputStream` có thể tính toán và ghi thông tin thích hợp sau khi xử lý dữ liệu. Bạn vẫn phải gọi `closeEntry()` để kết thúc entry; không thể dựa vào kích thước mảng đầu vào làm kích thước đã nén.

Với **`STORED`**, ZIP không nén byte nên compressed size bằng size. Cần khai báo kích thước và CRC-32 đúng trước khi ghi; nếu thiếu hoặc dữ liệu ghi không khớp, có thể gặp `ZipException`.

Ví dụ phần metadata của một STORED entry:

```java
byte[] payload = new byte[] {10, 20, 30};
CRC32 checksum = new CRC32();
checksum.update(payload);

ZipEntry raw = new ZipEntry("raw.bin");
raw.setMethod(ZipEntry.STORED);
raw.setSize(payload.length);
raw.setCompressedSize(payload.length);
raw.setCrc(checksum.getValue());

zip.putNextEntry(raw);
zip.write(payload);
zip.closeEntry();
```

Đây là đoạn mã minh họa, giả định đã import `java.util.zip` và có biến `zip` đang mở. Hãy tính CRC từ **chính các byte đầu vào thực tế**. `CRC32` là mã kiểm tra phát hiện lỗi tình cờ, **không phải** chữ ký mật mã hoặc công cụ chống sửa đổi có chủ ý.

### Vì sao `STORED` cần CRC trước?

Với một số cách ghi nội dung ZIP không nén, header phải chứa chính xác thông tin size/CRC trước khi ghi dữ liệu. `ZipOutputStream` thực hiện xác thực các giá trị này theo hợp đồng phương pháp STORED.

Nếu không biết kích thước hoặc phải stream dữ liệu nguồn không thể đọc lại, có thể sử dụng `DEFLATED` để ZIP tự tính thông tin trong quá trình ghi (dù dữ liệu có thể không nén nhỏ được), hoặc cân nhắc phương án spool dữ liệu ra vùng tạm trước khi tạo STORED entry.

## 11. Ví dụ Java 17+: tạo STORED entry với size/CRC chính xác

Chương trình sau tạo ZIP trong bộ nhớ, lưu `raw.bin` **không nén** rồi đọc lại, kiểm tra phương pháp và byte dữ liệu.

**Tệp `StoredEntryDemo.java`:**

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class StoredEntryDemo {
    public static void main(String[] args) throws IOException {
        byte[] payload = new byte[] {10, 20, 30, 40};
        CRC32 crc = new CRC32();
        crc.update(payload);

        ZipEntry raw = new ZipEntry("raw.bin");
        raw.setMethod(ZipEntry.STORED);
        raw.setSize(payload.length);
        raw.setCompressedSize(payload.length);
        raw.setCrc(crc.getValue());

        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(
                sink, StandardCharsets.UTF_8)) {
            zip.putNextEntry(raw);
            zip.write(payload);
            zip.closeEntry();
        }

        try (ZipInputStream input = new ZipInputStream(
                new ByteArrayInputStream(sink.toByteArray()),
                StandardCharsets.UTF_8)) {
            ZipEntry read = input.getNextEntry();
            byte[] actual = input.readAllBytes();

            System.out.println("Method STORED: "
                    + (read.getMethod() == ZipEntry.STORED));
            System.out.println("Uncompressed size: " + read.getSize());
            System.out.println("Bytes match: "
                    + Arrays.equals(payload, actual));
            input.closeEntry();
        }
    }
}
```

Lệnh:

```bash
javac StoredEntryDemo.java
java StoredEntryDemo
```

**Stdout khi thành công:**

```text
Method STORED: true
Uncompressed size: 4
Bytes match: true
```

Ví dụ minh họa đầy đủ các bước cần cho một STORED entry:

1. Tính CRC32 trên payload trước khi tạo entry.
2. Đặt `method=STORED`, size, compressed size và CRC.
3. Gọi `putNextEntry`, ghi chính xác số byte đã khai báo.
4. `closeEntry` hoàn tất entry và xác minh metadata.
5. Đóng ZIP stream để hoàn tất archive.
6. Giải nén ra byte rồi so sánh nội dung.

Nếu dữ liệu là byte từ nguồn bên ngoài, **không được tính CRC trên bản dữ liệu khác** rồi ghi payload đã thay đổi. Sai CRC hoặc kích thước có thể gây `ZipException` lúc đóng entry.

## 12. Thứ tự gọi API đúng trong các tình huống thực tế

### 12.1. Nhiều file trong một ZIP

```text
ZipOutputStream.open
  putNextEntry(file 1)
    write...
  closeEntry()
  putNextEntry(file 2)
    write...
  closeEntry()
  ...
ZipOutputStream.close()  => archive hoàn chỉnh + sink đóng
```

Trường hợp thông dụng nhất: dùng một `ZipOutputStream` trong `try-with-resources`, đóng entry rõ ràng, để `close()` hoàn tất ZIP.

### 12.2. Caller sở hữu OutputStream

```text
Caller tạo OutputStream
  thư viện tạo ZipOutputStream(output)
    putNextEntry / write / closeEntry ...
    finish()
  trả lại quyền kiểm soát sink cho caller
Caller quyết định flush/close sink
```

Lưu ý phải có quy ước rất rõ về **quyền đóng**. `ZipOutputStream.close()` không phù hợp nếu caller yêu cầu sink vẫn mở. Đôi khi có thể dùng một wrapper non-closing, nhưng cần kiểm soát vòng đời deflater và mọi lỗi I/O; không áp dụng workaround chỉ vì muốn tránh thiết kế ownership rõ ràng.

### 12.3. Dữ liệu chưa ghi hết mà đã closeEntry

```text
putNextEntry("large.bin")
write(chunk 1)
closeEntry()
write(chunk 2)  <-- KHÔNG thuộc large.bin; đây là lỗi quy trình
```

Sau khi `closeEntry()`, nội dung entry đã kết thúc. Chương trình phải đọc/ghi **đầy đủ** payload trước khi đóng entry. Không có API chuẩn để tiếp tục append vào cùng entry sau khi đã đóng nó.

### 12.4. File input gặp IOException khi đang đóng gói

Nếu đọc file nguồn hoặc ghi ZIP phát sinh lỗi trước khi entry hoàn chỉnh, **đừng âm thầm bỏ qua lỗi rồi công bố archive hợp lệ**. Có thể không hoàn tất được entry, hoặc dù ghi xong central directory thì dữ liệu thành phần không đại diện cho nội dung mong muốn.

Quy tắc production nên là:

1. Ghi ZIP vào **tệp tạm**.
2. Dùng `try-with-resources` bảo đảm đóng stream.
3. Nếu quá trình ghi hoàn toàn thành công, **kiểm tra archive nếu cần**.
4. Chỉ khi thành công mới publish/rename tệp tạm sang tên chính thức.
5. Nếu có lỗi, loại bỏ artifact dở dang theo chính sách; không tiếp tục sử dụng như archive thành công.

Khi sử dụng `Files.move` với `ATOMIC_MOVE`, tính khả dụng phụ thuộc provider/filesystem; cần có phương án khi không hỗ trợ. Việc publish atomically không tự thay thế `fsync` hoặc cơ chế độ bền dữ liệu khi cần bảo đảm chống mất điện.

## 13. `closeEntry` có bắt buộc trước `putNextEntry` tiếp theo không?

Theo Java SE 17 API, `putNextEntry` **đóng entry hiện tại nếu nó vẫn còn hoạt động**. Tương tự, `finish()` có trách nhiệm kết thúc ZIP archive và có thể hoàn tất entry đang mở.

Ví dụ về mặt ý tưởng:

```java
zip.putNextEntry(new ZipEntry("a.txt"));
zip.write(new byte[] {65});

// putNextEntry sẽ đóng a.txt trước khi mở b.txt.
zip.putNextEntry(new ZipEntry("b.txt"));
zip.write(new byte[] {66});

zip.finish();
```

Đây là đoạn minh họa sử dụng biến `zip` đã tồn tại. **Không khuyến khích dùng sự tự động này để che giấu trạng thái code.** Lập trình viên nên đóng entry rõ ràng ở điểm dữ liệu hoàn tất, và dùng `try-with-resources` để đảm bảo hoàn tất archive.

Lợi ích của cách tường minh là:
- Có thể đặt logging và số byte đã ghi cạnh entry tương ứng.
- Lỗi CRC/size của entry dễ được quy về entry đang xử lý.
- Phân biệt rõ lỗi đọc nguồn, lỗi ghi ZIP và lỗi finalize.
- Dễ refactor thành hàm `addEntry(zip, source)` có hợp đồng cụ thể.

`closeEntry()` và `close()` không có cùng quyền tác động; nhầm hai tên này sẽ làm hỏng luồng xử lý nhiều file.

## 14. Ghi entry thư mục và đường dẫn tương đối an toàn

Khi tạo archive từ một thư mục nguồn, phải xây tên entry **tương đối so với thư mục gốc**:

```java
Path root = Path.of("/source-folder").toAbsolutePath().normalize();
Path file = root.resolve("images/logo.png");
String entryName = root.relativize(file).toString().replace('\\', '/');
```

Đây là minh họa, không chạy độc lập trên mọi hệ điều hành vì đường dẫn ví dụ cần tồn tại. Quy tắc chuyển `\\` sang `/` hữu ích cho cách biểu diễn tên entry ZIP nhất quán.

Nhưng **chuẩn hóa dấu phân cách chưa đủ để bảo mật**. Không nên nhận tên entry không đáng tin rồi lưu vào ZIP mà bỏ kiểm tra:

- Tránh tên tuyệt đối, traversal `../`, đường dẫn rỗng, các entry gây xung đột.
- Tránh nhúng tên file có thông tin nhạy cảm hoặc đường dẫn máy chủ tuyệt đối.
- Xác định chính sách với symbolic links (bỏ qua, ghi nội dung đích hay biểu diễn link theo một cách đặc biệt).
- Không mặc định ZIP entry tự có quyền/mode metadata tương thích giữa mọi công cụ.

**Khi giải nén ZIP từ người khác** (không phải phần tạo ZIP cốt lõi ở bài này), cần ngăn **Zip Slip**: giải quyết entry name dưới thư mục đích, chuẩn hóa, kiểm tra nó không thoát khỏi thư mục được cho phép, và xử lý symbolic link/TOCTOU theo mô hình đe dọa. Đồng thời giới hạn tổng dung lượng giải nén, số entry, tỉ lệ nén để tránh zip bomb.

Mục tiêu ở phần tạo ZIP là đặt tên entry rõ ràng và tránh tạo archive gây nguy hiểm hoặc khó giải nén.

## 15. Charset, compression level và metadata

### 15.1. Charset cho **tên entry**

`ZipOutputStream(OutputStream out, Charset charset)` cho phép chọn cách mã hóa **tên entry và comment**. Ví dụ:

```java
ZipOutputStream zip = new ZipOutputStream(
        output, StandardCharsets.UTF_8);
```

Đây là đoạn minh họa, biến `output` phải là `OutputStream` đang mở.

**Charset trong constructor không tự mã hóa nội dung file.** Nếu bạn muốn lưu văn bản UTF-8, phải mã hóa payload riêng:

```java
zip.write("Tiếng Việt".getBytes(StandardCharsets.UTF_8));
```

Khi giải nén text, phía đọc cũng phải dùng đúng charset của **nội dung**. Đừng đánh đồng charset cho entry names với charset của từng file.

### 15.2. `setLevel(int)` chỉ là mức nén

`zip.setLevel(Deflater.BEST_COMPRESSION)` thay đổi mức nén dùng cho các entry **DEFLATED tiếp theo**. Nó không thay nội dung giải nén, không biến ZIP thành định dạng khác và không thể được dùng để làm archive “hoàn chỉnh” khi chưa `finish/close`.

`ZipEntry.STORED` giữ nguyên dữ liệu, không nén; vì vậy mức nén DEFLATE không có ý nghĩa với entry STORED.

### 15.3. Comment và timestamp

`ZipEntry.setTime(long)` cho phép đặt thời gian sửa đổi. `ZipOutputStream.setComment(String)` đặt comment cho archive. Những metadata này có thể ảnh hưởng byte ZIP và khả năng tái lập khi build.

Trong các pipeline yêu cầu **reproducible builds**, cần sắp xếp thứ tự entry và chuẩn hóa metadata; không tự suy ra ZIP có cùng byte chỉ vì payload logic giống nhau.

## 16. Lưu ý dành cho JAR và GZIP

- **JAR** cũng dựa trên ZIP. `JarOutputStream` kế thừa `ZipOutputStream`, có thể dùng `putNextEntry`/`closeEntry` và `finish/close`. JAR có các quy ước bổ sung như `META-INF/MANIFEST.MF`, chữ ký số và metadata nên không coi một ZIP bất kỳ là JAR triển khai đúng mọi yêu cầu.
- **GZIP** là định dạng khác, thường biểu diễn một luồng nén thay vì archive gồm nhiều entry ZIP. `GZIPOutputStream` có `finish()`/`close()` nhưng **không có `putNextEntry(ZipEntry)`** giống ZIP.

Đừng nhầm:
- `ZipOutputStream`: nhiều tên entry, metadata và central directory.
- `GZIPOutputStream`: luồng dữ liệu nén GZIP, không hỗ trợ danh sách entry theo mô hình ZIP.
- `JarOutputStream`: ZIP có các quy ước JAR bổ sung.

Sự khác biệt này quan trọng khi chọn API phù hợp: đóng gói nhiều file thành một archive dùng ZIP/JAR; nén một luồng dữ liệu thô có thể dùng GZIP hoặc `DeflaterOutputStream` tùy format yêu cầu.

## 17. Lỗi thường gặp và cách sửa

| Lỗi | Hậu quả hoặc nguyên nhân | Cách khắc phục |
| --- | --- | --- |
| Gọi `write` khi chưa `putNextEntry` | Không có entry hiện tại để chứa dữ liệu | Mở entry trước |
| Gọi `closeEntry` rồi tin ZIP hoàn tất | Chưa có central directory toàn archive | Gọi `finish()` hoặc `close()` |
| Chỉ `flush()` rồi publish ZIP | Stream chưa chắc ghi cấu trúc kết thúc | Hoàn tất ZIP trước khi publish |
| Dùng `close()` rồi muốn thêm entry | Stream đã đóng | Tạo archive mới hoặc thiết kế lại luồng |
| Gọi `finish()` rồi muốn `putNextEntry` | Archive đã hoàn tất | Chỉ finish sau entry cuối |
| Đóng zip bên trong hàm tiện ích khi caller sở hữu sink | Sink bị đóng ngoài ý muốn | Hợp đồng ownership rõ; dùng finish nếu cần giữ sink mở |
| Quên `closeEntry` khi ghi liên tiếp | Có thể vẫn tự đóng khi putNextEntry, nhưng khó kiểm soát vòng đời | Đóng entry rõ ràng sau khi ghi đủ |
| Đặt `STORED` không khai báo size/CRC | `ZipException` | Tính và khai báo trước khi putNextEntry |
| Khai báo sai size hoặc CRC | Lỗi khi đóng/ghi entry | Tính từ byte thực tế, đảm bảo dữ liệu không đổi |
| Dùng lại tên entry | Duplicate entry | Kiểm tra xung đột tên trước khi ghi |
| Coi tên `folder/file.txt` là đường dẫn OS hoàn chỉnh | Không chuẩn, có thể làm lộ đường dẫn host | Dùng đường dẫn tương đối với `/` |
| Tưởng constructor charset mã hóa payload text | File text giải nén sai mã | Tự encode nội dung bằng UTF-8 |
| In kích thước ZIP và tin luôn cố định | Compression và metadata có thể thay đổi | Kiểm tra số entry/nội dung thay vì byte length |
| Ghi ZIP trực tiếp vào artifact chính thức trước khi thành công | Có thể để lại ZIP hỏng sau lỗi | Ghi tệp tạm, xác minh, sau đó publish |
| Dùng ZipInputStream đọc được một entry rồi kết luận ZIP hoàn chỉnh | Local entry có thể đọc trước central directory | Hoàn tất bằng finish/close và kiểm tra bằng ZipFile khi cần |

## 18. Bài tập tự kiểm tra

### Câu 1. Hai entry đã `closeEntry()`, nhưng chưa `finish()`/`close()`

Có bảo đảm `ZipFile` đọc được archive không?

**Không.** `closeEntry()` kết thúc từng entry, trong khi ZIP archive thường cần central directory và phần kết thúc. `ZipFile` dựa trên metadata tổng hợp; cần hoàn tất ZIP đúng cách.

### Câu 2. Đã gọi `zip.finish()` khi sink do caller sở hữu

Sink có tự đóng không, và có nên gọi `putNextEntry` tiếp không?

**Không.** `finish()` không đóng stream bên dưới. Nhưng ZIP đã được hoàn tất, không nên ghi thêm entry; caller quản lý sink theo hợp đồng của mình.

### Câu 3. Dùng `ZipEntry.STORED` với payload đã biết

Những metadata nào phải có?

**Size**, **compressed size** (bằng size) và **CRC-32** chính xác của payload. Nếu thiếu hoặc dữ liệu không khớp có thể gặp `ZipException`. CRC-32 không thay thế xác thực mật mã.

### Câu 4. Lưu `src/docs/readme.txt` thành entry `docs/readme.txt`

Có bắt buộc tạo entry `docs/` trước không?

Không bắt buộc cho **mọi công cụ giải nén** nếu chỉ muốn giải nén file trong thư mục logic; nhiều công cụ tạo thư mục cha theo tên file. Nhưng muốn ZIP biểu diễn **thư mục rỗng** hoặc kiểm soát cấu trúc thư mục tường minh, thêm entry kết thúc `/` là cách thông dụng.

### Câu 5. Dùng `zip.close()` trong try-with-resources có cần `zip.finish()` trước không?

**Không bắt buộc.** `close()` hoàn tất ZIP rồi đóng luồng bên dưới. `finish()` được dùng khi muốn hoàn tất archive nhưng giữ sink mở, hoặc cần đánh dấu điểm kết thúc archive rõ ràng.

### Câu 6. Đã gọi `putNextEntry("x.txt")` rồi ghi nhiều khối byte

Có nên gọi `closeEntry()` sau mỗi khối không?

**Không.** Toàn bộ các khối của `x.txt` thuộc cùng entry đang mở. Gọi `closeEntry()` **một lần sau khi đã ghi đủ**; nếu đóng sớm sẽ kết thúc dữ liệu entry.

### Câu 7. ZipInputStream đọc được `one.txt` từ luồng byte chưa hoàn tất ZIP

Có thể kết luận ZIP hợp lệ hoàn toàn?

**Không.** Đọc được một local entry không chứng minh central directory và end-of-central-directory hợp lệ. Phải `finish` hoặc `close` và kiểm tra tính toàn vẹn của toàn archive theo yêu cầu ứng dụng.

## 19. Checklist đóng gói an toàn

Trước khi bắt đầu:

- Xác định **ai sở hữu `OutputStream`** và ai chịu trách nhiệm gọi `close`.
- Xác định tên entry **tương đối**, không trùng và phù hợp nền tảng giải nén.
- Chọn `DEFLATED` mặc định hoặc `STORED` với metadata đã được tính đúng.
- Xác định charset cho entry name/comment và cách mã hóa payload văn bản.

Khi ghi mỗi entry:

- Gọi `putNextEntry(entry)`.
- Ghi **đầy đủ** nội dung entry trước khi đóng.
- Gọi `closeEntry()`, xử lý `IOException` và `ZipException`.
- Không kết luận có archive hợp lệ nếu quá trình bị lỗi giữa chừng.

Khi hoàn tất:

- Dùng `close()` / `try-with-resources` nếu ZIP stream sở hữu sink.
- Dùng `finish()` nếu phải hoàn tất archive **mà vẫn giữ sink của caller mở**.
- Không publish tệp ZIP dở dang; với file quan trọng, ghi tạm rồi xác minh và publish.
- Khi cần kiểm tra đầy đủ danh sách entry và cấu trúc tổng hợp, mở bằng `ZipFile` sau khi ZIP đã hoàn tất.
- Khi giải nén dữ liệu không tin cậy, có chính sách kiểm tra đường dẫn, độ lớn, số entry và tài nguyên.

## 20. Tổng kết

**Ba thao tác phải nhớ theo đúng phạm vi:**

```text
putNextEntry(entry) -> bắt đầu một entry
write(bytes)        -> ghi nội dung entry ấy
closeEntry()        -> kết thúc entry ấy

lặp lại cho các entry khác

finish()            -> kết thúc TOÀN BỘ ZIP,
                       nhưng giữ OutputStream bên dưới mở

hoặc

close()             -> kết thúc TOÀN BỘ ZIP
                       và đóng cả OutputStream bên dưới
```

`putNextEntry` và `closeEntry` tạo ra ranh giới cho từng file/thư mục bên trong ZIP. `finish` tạo ranh giới kết thúc **archive**, còn `close` đồng thời quản lý vòng đời tài nguyên bên dưới. Không trộn ba lớp trách nhiệm này. Với cách hiểu rõ entry-level và archive-level, bạn có thể viết code đóng gói nhiều file, dọn tài nguyên đúng và tránh tạo ZIP hỏng hoặc để lại dữ liệu dở dang.

**Tài liệu tham khảo chính thức cho Java SE 17:**

- [ZipOutputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/ZipOutputStream.html) — `putNextEntry`, `closeEntry`, `finish`, `close`.
- [ZipEntry](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/ZipEntry.html) — tên, method, kích thước, CRC-32.
- [ZipFile](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/ZipFile.html) — đọc ZIP hoàn chỉnh với chỉ mục entry.
- [ZipInputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/ZipInputStream.html) — đọc entry tuần tự.
- [JarOutputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/jar/JarOutputStream.html) — JAR dựa trên ZIP.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="compression-and-archives-005">finish khác flush và close</a>

<details>
<summary>Xem chi tiết</summary>

# finish khác flush và close trong Java: nén và đóng gói dữ liệu

## 1. Ba hành động khác nhau trên một output stream

Các lớp nén và đóng gói trong `java.util.zip` có thể nhận một `OutputStream` bên dưới rồi biến dữ liệu ứng dụng thành ZIP, GZIP hoặc dữ liệu DEFLATE/zlib. Người học thường nhầm ba phương thức:

- **`flush()`**: yêu cầu chuyển các byte đang được đệm theo hợp đồng của stream hiện tại xuống tầng bên dưới. Không đánh dấu định dạng nén đã kết thúc, không đóng stream.
- **`finish()`**: hoàn tất *luồng nén hoặc cấu trúc archive hiện tại*, ghi đủ dữ liệu nén còn lại và metadata cuối cần thiết; **không đóng stream đích bên dưới**.
- **`close()`**: đóng stream nén, thường gọi logic hoàn tất định dạng nếu còn cần, giải phóng tài nguyên và **đóng stream đích được bọc**.

Chỉ gọi `flush()` không khiến một ZIP archive có đủ central directory hay một GZIP member có trailer. Ngược lại, gọi `close()` trên wrapper có thể đóng luôn luồng mà caller hay framework vẫn còn cần sử dụng. `finish()` có ích khi muốn định dạng nén hoàn chỉnh nhưng phải để stream nền tiếp tục mở.

Mục tiêu bài học là xác định đúng vòng đời khi dùng `ZipOutputStream`, `GZIPOutputStream` và `DeflaterOutputStream`; hiểu `ZipOutputStream.closeEntry()`; xây dựng ví dụ Java 17+ chạy được; nhận diện lỗi nén thiếu footer, dùng nhầm quyền sở hữu và nhầm flush với độ bền dữ liệu.

## 2. Một pipeline gồm nhiều tầng

Ví dụ luồng dữ liệu:

~~~text
Application writes bytes
       |
       v
ZipOutputStream / GZIPOutputStream / DeflaterOutputStream
       |    compression / archive metadata
       v
BufferedOutputStream (optional)
       |    Java-side buffering
       v
FileOutputStream / socket OutputStream / ByteArrayOutputStream
       |
       v
Storage / network / memory
~~~

Mỗi tầng quản lý **một kiểu trạng thái khác nhau**. Output của bộ nén có thể chưa tạo đủ byte cuối để dữ liệu thành một archive hợp lệ, dù tầng dưới đã flush mọi byte đang nắm giữ. Ngược lại, sau khi bộ nén finish, tầng dưới vẫn có thể đang mở và còn dữ liệu chưa được lưu bền trên ổ đĩa hay chưa được đối tác mạng xác nhận.

| Yêu cầu | Công cụ phù hợp | Điều không được suy luận |
| --- | --- | --- |
| Muốn đẩy phần đệm nhưng vẫn ghi tiếp | `flush()` | Không có nghĩa archive hoàn tất |
| Muốn ZIP/GZIP hoàn chỉnh nhưng giữ destination mở | `finish()` | Không có nghĩa destination đã đóng |
| Hoàn tất và giải phóng chuỗi stream do mình sở hữu | `close()`, thường qua `try-with-resources` | Không có nghĩa tất cả consumer đã nhận/xử lý |
| Muốn dữ liệu bền trên thiết bị lưu trữ | Giao thức lưu trữ/`FileChannel.force(...)` khi thích hợp | Không phải chỉ gọi `flush()` hoặc `finish()` |
| Muốn chắc consumer đọc thành công | Xác thực archive và acknowledgment của ứng dụng khi cần | Không thay bằng trạng thái close phía gửi |

Vì thế phải trả lời hai câu độc lập: **định dạng đã hoàn chỉnh chưa?** và **ai sở hữu tài nguyên đích?**

## 3. flush() không phải dấu kết thúc dữ liệu nén

`OutputStream.flush()` có tác dụng tùy implementation. Ví dụ `BufferedOutputStream.flush()` chuyển buffer Java xuống stream kế; `DeflaterOutputStream.flush()` có thể chuyển phần dữ liệu đã nén ra ngoài và gọi flush trên output bên dưới.

Với `DeflaterOutputStream` và `GZIPOutputStream`, constructor có tham số `syncFlush=true` để khi flush thực hiện chế độ `Deflater.SYNC_FLUSH`. Nó hỗ trợ việc bên nhận giải nén **một phần luồng đang tiếp diễn**, dù bộ nén chưa nhận tín hiệu kết thúc. Điều này quan trọng với streaming, nhưng có thể giảm tỷ lệ nén nếu flush quá thường xuyên.

Tuy nhiên:

1. `flush()` không đặt tín hiệu *end-of-input* cho bộ nén.
2. `flush()` không viết ZIP central directory để kết thúc toàn archive.
3. `flush()` không bảo đảm ghi GZIP trailer chứa CRC32 và ISIZE.
4. `flush()` không tự giải phóng tài nguyên nén hay đóng destination.
5. `flush()` không thay cho việc yêu cầu dữ liệu bền trên đĩa hoặc TCP acknowledgment.

Đoạn mã minh họa:

~~~java
GZIPOutputStream gzip = new GZIPOutputStream(destination, true);
gzip.write(payloadPart1);
gzip.flush();                // Cho consumer nhận được thêm dữ liệu đang nén.
gzip.write(payloadPart2);   // Vẫn có thể tiếp tục thêm payload.
gzip.finish();               // Chỉ bây giờ mới hoàn tất GZIP member.
~~~

Các biến `destination`, `payloadPart1` và `payloadPart2` phải được khai báo, code cần import và xử lý `IOException`. Đây không phải chương trình độc lập. Nếu muốn đóng destination vì hàm đang sở hữu nó, cần gọi `close()` hoặc có cấu trúc quản lý tài nguyên thích hợp.

**Phân biệt:** `flush()` có thể làm bytes trước mắt giải nén được nhưng **không chứng minh compressed stream đã hợp lệ như một đơn vị hoàn chỉnh**. Một số decoder sẽ chờ hoặc báo EOF bất thường khi trailer/metadata cuối bị thiếu.

## 4. finish(): hoàn tất luồng nén nhưng giữ stream đích mở

`finish()` là tín hiệu **đã viết đủ mọi byte thuộc nội dung nén này**. Bộ nén phải xả phần output cuối, kết thúc compressed stream và ghi metadata phụ thuộc định dạng. Ví dụ:

- `ZipOutputStream.finish()`: hoàn tất ZIP archive, viết central directory và các record kết thúc.
- `GZIPOutputStream.finish()`: hoàn tất DEFLATE payload rồi viết GZIP trailer với checksum và kích thước.
- `DeflaterOutputStream.finish()`: hoàn tất luồng nén từ `Deflater`, đẩy dữ liệu cuối ra bên dưới.

Theo hợp đồng các lớp này, `finish()` **không đóng output stream nền**. Điều đó giúp caller quyết định khi nào đóng destination sau khi xử lý thêm các bước hợp lệ của pipeline.

**Không thể tiếp tục thêm dữ liệu vào cùng compressed stream/ZIP archive sau finish.** Để ghi nội dung còn lại, phải thực hiện nó **trước** finish. Nếu có nhu cầu nhiều compressed messages trên cùng kết nối, cần một giao thức framing hoặc cấu trúc container riêng và các object writer/reader tương ứng. Không giả định nối bytes tùy ý sau ZIP/GZIP đã hoàn thành sẽ được mọi consumer hỗ trợ.

Khi `finish()` ném `IOException`, không được đánh dấu output là thành công. Bộ nén có thể đã ghi phần lớn payload nhưng chưa ghi đủ metadata cuối. Archive có thể bị hỏng ngay cả khi các lệnh `write()` trước đó đã thành công.

## 5. close(): kết thúc vòng đời và truyền thao tác đóng xuống dưới

Với các output stream nén chuẩn trong `java.util.zip`, `close()` thực hiện việc hoàn tất định dạng khi còn cần, đóng stream nén và đóng stream bên dưới theo hợp đồng lớp. Nó còn giúp giải phóng những tài nguyên mà stream nén sở hữu.

Khi **chính hàm mở output file** để tạo ZIP, cách thông thường:

~~~java
Path output = Path.of("archive.zip");
try (ZipOutputStream zip = new ZipOutputStream(
        Files.newOutputStream(output))) {
    zip.putNextEntry(new ZipEntry("hello.txt"));
    zip.write("hello".getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
} // zip.close() hoàn tất ZIP nếu cần và đóng file stream.
~~~

Đây là đoạn code minh họa cần đặt trong method có xử lý `IOException` và import các lớp tương ứng. Nếu ngay sau khi ghi entry đã định đóng ZIP, **không cần gọi `finish()` tường minh** vì `close()` đã có trách nhiệm hoàn tất.

Tuy nhiên, khi **destination do caller cung cấp** (ví dụ output stream do web framework quản lý), bạn không được tùy tiện đóng nó chỉ vì muốn ZIP hoàn tất. Có thể dùng `finish()` và giao quyền đóng destination cho caller; nếu cần đóng wrapper để giải phóng trạng thái nén nhưng không đóng destination, cần một adapter non-closing có hợp đồng rõ ràng. Phần ownership ở bên dưới trình bày chi tiết.

## 6. ZIP: closeEntry() khác finish() như thế nào?

Một ZIP archive chứa một hoặc nhiều `ZipEntry`. Quy trình thường gặp:

~~~text
putNextEntry("a.txt")
write bytes of a.txt
closeEntry()            [hoàn tất entry thứ nhất]
putNextEntry("b.txt")
write bytes of b.txt
closeEntry()            [hoàn tất entry thứ hai]
finish()                [viết metadata cuối cho cả ZIP archive]
close()                 [đóng stream và stream đích nếu có quyền]
~~~

`ZipOutputStream.closeEntry()` chỉ kết thúc **entry hiện tại**. Đối với entry DEFLATED, code nén/metadata như kích thước và CRC có thể được hoàn tất ở bước này theo ZIP format. Nhưng ZIP có thêm **central directory** ở cuối archive dùng để mô tả các entry và record *End of Central Directory*. Chúng được viết khi `finish()`/`close()` hoàn tất archive.

Vì vậy, một file ZIP đã `closeEntry()` nhưng chưa `finish()` **có thể chưa mở được bằng `ZipFile`** hoặc các công cụ đọc ZIP dựa vào central directory. Không dùng việc `ZipInputStream` đôi khi đọc được entry đầu tiên như bằng chứng toàn archive đã hoàn chỉnh: `ZipInputStream` đọc tuần tự; `ZipFile` cần thông tin thư mục trung tâm.

### Ví dụ xây archive có hai entry

~~~java
ZipOutputStream zip = new ZipOutputStream(destination);

zip.putNextEntry(new ZipEntry("a.txt"));
zip.write("A".getBytes(StandardCharsets.UTF_8));
zip.closeEntry();

zip.putNextEntry(new ZipEntry("b.txt"));
zip.write("B".getBytes(StandardCharsets.UTF_8));
zip.closeEntry();

zip.finish(); // Có central directory; destination vẫn còn mở.
~~~

Đây là đoạn minh họa. `destination` phải là `OutputStream` hợp lệ và các import được cung cấp khi dùng thật. Không thêm entry vào object ZIP đã `finish()`; muốn nhiều file trong **một** ZIP, đưa chúng thành nhiều entries trước khi finish, chứ không nối thêm ZIP khác một cách tùy tiện.

Với `ZipEntry.STORED` (không nén), entry cần thông tin kích thước và CRC phù hợp trước khi ghi theo API, khác với trường hợp `DEFLATED`. Quy định ở mức entry không thay đổi nguyên tắc `closeEntry()` khác `finish()`.

## 7. GZIP: flush không ghi GZIP trailer

Một GZIP member thường có cấu trúc:

~~~text
+-------------+-------------------+------------------+
| GZIP header | DEFLATE payload   | GZIP trailer     |
+-------------+-------------------+------------------+
                                    CRC32 + ISIZE
~~~

`GZIPOutputStream.finish()` hoàn tất compressed data rồi viết trailer. `CRC32` là checksum cho dữ liệu chưa nén; `ISIZE` lưu kích thước chưa nén modulo 2^32. Vì vậy receiver chỉ nên coi member đã hoàn chỉnh khi cấu trúc cuối được ghi và đọc đúng, không chỉ dựa vào `gzip.flush()`.

Khi dùng:

~~~java
GZIPOutputStream gzip = new GZIPOutputStream(destination, true);
gzip.write("hello".getBytes(StandardCharsets.UTF_8));
gzip.flush();  // syncFlush: hữu ích cho streaming, vẫn chưa có trailer kết thúc.
gzip.finish(); // Hoàn tất member và ghi trailer, không đóng destination.
~~~

Đây là mảnh minh họa, không phải chương trình độc lập. Có thể gọi `gzip.close()` sau finish nếu thực sự sở hữu stream đích.

Khác ZIP, GZIP thường đóng gói **một luồng dữ liệu** chứ không có nhiều entry với tên path. Mặc dù có những decoder hỗ trợ ghép nối nhiều GZIP members, không được mặc định mọi consumer hoặc protocol của bạn chấp nhận cách này. Nếu cần gửi nhiều message, định nghĩa framing rõ ràng và kiểm thử receiver.

## 8. DeflaterOutputStream: tầng nén, không phải ZIP container

`DeflaterOutputStream` cung cấp luồng dữ liệu nén bằng `Deflater`. Một `Deflater` mặc định thường tạo luồng zlib-wrapped DEFLATE; `GZIPOutputStream` và `ZipOutputStream` có metadata khác bao quanh DEFLATE.

| Đối tượng writer | Nội dung | Khi finish cần ghi gì |
| --- | --- | --- |
| `DeflaterOutputStream` | Luồng nén do `Deflater` cấu hình, mặc định zlib-wrapped DEFLATE | Kết thúc compressed stream của deflater, gồm trailer/checksum zlib khi áp dụng |
| `GZIPOutputStream` | Một GZIP member | Phần DEFLATE cuối và GZIP trailer |
| `ZipOutputStream` | Một archive nhiều entry ZIP | Metadata kết thúc entry và central directory/end record |

`DeflaterOutputStream.finish()` không thể thay thế `ZipOutputStream.finish()` khi mục tiêu là file ZIP hoàn chỉnh. Cũng không thể lấy bytes của `DeflaterOutputStream` mặc định cho `GZIPInputStream` hoặc `ZipFile` mà mong hai reader tự nhận ra định dạng: zlib, GZIP và ZIP không phải cùng file format.

Với `DeflaterOutputStream`, `syncFlush=true` giúp yêu cầu `Deflater.SYNC_FLUSH` trong `flush()` để streaming có thể tiến triển, nhưng điểm kết thúc chính thức vẫn là `finish()`/`close()`.

## 9. Ví dụ hoàn chỉnh Java 17+: ZIP và GZIP cùng ba phương thức

Chương trình `CompressionLifecycleDemo.java` dùng `ByteArrayOutputStream` làm storage và một `TrackedOutput` để quan sát xem `close()` đã được chuyển xuống destination chưa. Cần tracker vì `ByteArrayOutputStream.close()` là no-op: không thể chỉ hỏi một `ByteArrayOutputStream` còn cho phép truy cập mảng để suy ra wrapper đã đóng luồng hay chưa.

Lưu tệp **`CompressionLifecycleDemo.java`**:

~~~java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class CompressionLifecycleDemo {
    private static final byte[] HELLO =
            "hello".getBytes(StandardCharsets.UTF_8);

    static final class TrackedOutput extends FilterOutputStream {
        boolean closed;
        TrackedOutput(OutputStream out) { super(out); }
        @Override public void close() throws IOException {
            try { super.close(); } finally { closed = true; }
        }
    }

    public static void main(String[] args) throws IOException {
        zipExample();
        gzipExample();
    }

    private static void zipExample() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        TrackedOutput destination = new TrackedOutput(bytes);
        ZipOutputStream zip = new ZipOutputStream(destination);
        zip.putNextEntry(new ZipEntry("hello.txt"));
        zip.write(HELLO);
        zip.closeEntry();
        zip.flush();
        int sizeAfterFlush = bytes.size();
        System.out.println("ZIP flush closed destination: " + destination.closed);
        zip.finish();
        System.out.println("ZIP finish added bytes: " + (bytes.size() > sizeAfterFlush));
        System.out.println("ZIP finish closed destination: " + destination.closed);
        zip.close();
        System.out.println("ZIP close closed destination: " + destination.closed);

        try (ZipInputStream in = new ZipInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            ZipEntry entry = in.getNextEntry();
            System.out.println("ZIP entry: " + entry.getName());
            System.out.println("ZIP content: " +
                    new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static void gzipExample() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        TrackedOutput destination = new TrackedOutput(bytes);
        GZIPOutputStream gzip = new GZIPOutputStream(destination, true);
        gzip.write(HELLO);
        gzip.flush();
        int sizeAfterFlush = bytes.size();
        System.out.println("GZIP flush closed destination: " + destination.closed);
        gzip.finish();
        System.out.println("GZIP finish added bytes: " + (bytes.size() > sizeAfterFlush));
        System.out.println("GZIP finish closed destination: " + destination.closed);
        gzip.close();
        System.out.println("GZIP close closed destination: " + destination.closed);

        try (GZIPInputStream in = new GZIPInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            System.out.println("GZIP content: " +
                    new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
~~~

Lệnh biên dịch và chạy bằng **JDK 17+**:

~~~bash
javac CompressionLifecycleDemo.java
java CompressionLifecycleDemo
~~~

Stdout đã được kiểm tra thực tế trên JDK:

~~~text
ZIP flush closed destination: false
ZIP finish added bytes: true
ZIP finish closed destination: false
ZIP close closed destination: true
ZIP entry: hello.txt
ZIP content: hello
GZIP flush closed destination: false
GZIP finish added bytes: true
GZIP finish closed destination: false
GZIP close closed destination: true
GZIP content: hello
~~~

Giải thích:

1. Sau `zip.flush()`, tracker cho thấy destination chưa đóng. Archive khi ấy chưa được bảo đảm có central directory.
2. Sau `zip.finish()`, số byte tăng do metadata kết thúc ZIP được ghi, nhưng `destination.closed` vẫn false.
3. `zip.close()` đóng wrapper và chuyển `close()` tới tracker, khiến flag thành true. Giải nén phần hoàn chỉnh thành công với entry `hello.txt` và text `hello`.
4. Tương tự, `gzip.flush()` (ở đây bật syncFlush) chưa ghi trailer kết thúc member; `gzip.finish()` thêm bytes cuối mà không đóng destination.
5. `gzip.close()` đóng destination; `GZIPInputStream` giải nén thành `hello` sau khi format hoàn chỉnh.
6. Các dòng `finish added bytes: true` là **kết quả cụ thể của ví dụ**. Không suy luận mỗi lần finish ở mọi trạng thái/implementation luôn tăng kích thước đúng như vậy. Điều cần dựa vào là hợp đồng hoàn tất định dạng, không phải số byte cố định.

Demo để từng đối tượng compressed writer có destination riêng. Nó không gửi ra mạng và không đưa bytes chưa finish vào reader rồi kết luận reader phải luôn báo lỗi; chi tiết decoder với input dở dang có thể phụ thuộc cách kiểm tra.

## 10. Ai có quyền đóng destination?

**Quy tắc ưu tiên:** nơi **tạo và sở hữu** resource quyết định khi nào resource được đóng; nơi **mượn** stream phải tuân thủ hợp đồng của caller.

Trường hợp tạo file độc lập:

~~~java
Path file = Path.of("backup.zip");
try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
    zip.putNextEntry(new ZipEntry("metadata.txt"));
    zip.write("done".getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
} // Có quyền đóng, nên try-with-resources phù hợp.
~~~

Đây là mảnh code trong method, cần imports và xử lý IOException. Nếu `close()` thành công, ZIP được kết thúc rồi file output đóng. Nếu close báo lỗi, không được publish file đó như output hợp lệ.

Trường hợp một method chỉ nhận `OutputStream destination`:

~~~java
static void writeZip(OutputStream destination) throws IOException {
    ZipOutputStream zip = new ZipOutputStream(destination);
    zip.putNextEntry(new ZipEntry("note.txt"));
    zip.write("sample".getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
    zip.finish();
    // Không gọi zip.close() ở đây theo hợp đồng destination là của caller.
}
~~~

Đây là method minh họa. Nó hoàn tất archive nhưng tránh đóng destination của caller. Nhược điểm: wrapper nén không được close ngay; với code production muốn cleanup chắc chắn mà vẫn giữ destination mở, có thể dùng wrapper **non-closing** để zip.close chỉ đóng lớp nén và flush xuống caller, không đóng destination thực.

Adapter minh họa (không thuộc thư viện chuẩn):

~~~java
final class NonClosingOutputStream extends FilterOutputStream {
    NonClosingOutputStream(OutputStream wrapped) { super(wrapped); }

    @Override
    public void close() throws IOException {
        flush(); // Không đóng wrapped.
    }
}
~~~

Khi dùng:

~~~java
try (ZipOutputStream zip =
        new ZipOutputStream(new NonClosingOutputStream(destination))) {
    zip.putNextEntry(new ZipEntry("note.txt"));
    zip.write("sample".getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
} // zip.close: finish ZIP; adapter close chỉ flush destination.
~~~

Đây cũng là mã minh họa, các biến/import cần phù hợp. Trách nhiệm cuối cùng của caller là flush/close destination vào thời điểm nó sở hữu. Không biến non-closing wrapper thành mặc định cho mọi stream, vì như vậy có thể che giấu resource leak.

Nếu hàm mượn một stream từ servlet/HTTP framework, cần kiểm tra chính sách của framework. Không tự ý đóng HTTP response output nếu framework còn phải ghi framing/headers/body khác. Ngược lại, không bỏ qua lỗi finish trong một response nén: client có thể nhận GZIP/ZIP thiếu cấu trúc cuối dù HTTP status là thành công.

## 11. finish không đồng nghĩa flush xuống ổ đĩa và không bảo đảm transaction

Có ít nhất bốn cột mốc độc lập:

~~~text
Application writes content
          |
          v
Compression finish   -> archive/member có cấu trúc cuối
          |
          v
Output buffer flush  -> bytes được giao cho tầng I/O dưới
          |
          v
OS/storage sync      -> yêu cầu ghi bền dữ liệu theo hợp đồng storage
          |
          v
Application ACK      -> bên nhận/tiêu thụ xác nhận nghiệp vụ
~~~

Thứ tự thật tùy kiến trúc nhưng **không bước nào tự thay thế những bước còn lại**. Đóng một ZIP stream có thể hoàn tất format và đóng file descriptor, nhưng không biến chuỗi cập nhật nhiều tệp thành transaction nguyên tử. `FileChannel.force(true)` được thiết kế cho yêu cầu đồng bộ dữ liệu/metadata của channel, nhưng cũng không tự tạo atomic transaction cho nhiều lần ghi.

Nếu cần xuất tệp ZIP an toàn cho consumer:

1. Tạo file tạm trong nơi dự định lưu trữ.
2. Ghi nội dung qua `ZipOutputStream`.
3. Hoàn tất và đóng writer, xử lý mọi lỗi khi finish/close.
4. Nếu yêu cầu, xác thực archive và yêu cầu durability qua API/lớp storage thích hợp.
5. Chỉ khi đã đạt yêu cầu mới publish tên file chính thức, ví dụ move atomically **khi nền tảng hỗ trợ**.

Không áp dụng move atomic như một lời hứa tuyệt đối trên mọi network filesystem hay provider. Nếu `finish()`/`close()` lỗi, nên xử lý file tạm thất bại và không cập nhật metadata nghiệp vụ thành trạng thái “hoàn thành”.

## 12. Flush trên socket/HTTP: có giúp streaming không?

Trên mạng, `GZIPOutputStream(..., true)` với syncFlush có thể giúp consumer giải nén các byte đã nhận trong lúc luồng vẫn mở. Nhưng thời điểm nhận ở peer còn phụ thuộc network stack, HTTP framing, TLS, flow control và cách receiver đọc. Không có lời hứa rằng mỗi flush tạo một packet hay consumer đã nhìn thấy một message nghiệp vụ hoàn chỉnh.

Với ZIP, nếu định dạng consumer cần central directory thì không được sử dụng `flush()` làm tín hiệu “ZIP tải xuống đã đầy đủ”. Hãy finish archive trước khi kết thúc response. Cũng không nên nối thêm bytes không có framing vào cuối ZIP/GZIP với hy vọng reader sẽ bỏ qua phần dư.

Nếu server gửi nhiều message trên một socket, hãy thiết kế một trong các phương án: length-prefix, các bản ghi được đóng gói theo protocol, hoặc một container nén có các entry rõ ràng. `finish()` của một object không tự định nghĩa message boundary cho mọi protocol.

Một mẹo đo hiệu suất: flush quá nhiều lần trong lúc compression có thể tạo overhead và giảm tỷ lệ nén; chỉ yêu cầu syncFlush khi latency cần thiết, không dựa vào thói quen “gọi flush sau mỗi write”.

## 13. Xử lý ngoại lệ: write thành công không có nghĩa finish thành công

Các bước `finish()` và `close()` cũng phải ghi thêm bytes metadata, nên có thể ném `IOException`/`ZipException`. Ví dụ ổ đĩa đầy đúng lúc ghi central directory thì các entry đã nén vẫn có thể nằm trong file nhưng ZIP không hoàn chỉnh.

Sai:

~~~java
try {
    zip.finish();
} catch (IOException ignored) {
    // Publish ZIP dù finish thất bại: sai.
}
~~~

Đây là đoạn minh họa **anti-pattern**. Cần phát tín hiệu lỗi, cleanup tệp tạm, không chuyển trạng thái thành thành công. Với `try-with-resources`, Java vẫn thử close khi có exception; exception lúc close có thể xuất hiện dưới dạng *suppressed exception* nếu đã có lỗi chính.

Cũng nên giới hạn tài nguyên khi giải nén archive từ nguồn không tin cậy. GZIP/ZIP có thể có tỷ lệ nén cao bất thường; `ZipInputStream` đọc data không giới hạn có thể khiến ứng dụng tốn RAM/thời gian vì decompression bomb. `ByteArrayOutputStream` trong demo chỉ dành cho input nhỏ để kiểm tra round-trip; với file lớn nên dùng streaming và giới hạn đầu ra.

## 14. Lỗi điển hình và cách xử lý

| Sai lầm | Hậu quả | Hướng sửa |
| --- | --- | --- |
| Chỉ `zip.flush()` rồi publish ZIP | Thiếu central directory | `zip.finish()` hoặc `close()` |
| Chỉ `closeEntry()` cuối cùng | Entry xong, chưa chắc archive xong | `finish()` cho toàn ZIP |
| Chỉ `gzip.flush()` rồi báo hoàn thành | Có thể thiếu GZIP trailer | `finish()`/`close()` |
| Gọi `close()` khi chỉ mượn destination | Đóng tài nguyên do caller sở hữu | `finish()` và quy tắc ownership/non-closing adapter |
| Gọi `finish()` rồi thêm ZIP entry | Sai vòng đời archive | Thêm mọi entry trước khi finish |
| Đóng stream tự mở không được bảo đảm | Có thể rò rỉ resource và output chưa hoàn chỉnh | `try-with-resources` |
| Tưởng `flush()` là fsync | Hiểu sai durability | Cơ chế storage sync riêng |
| Cho rằng flush là ACK từ client | Client có thể chưa nhận hay xử lý | Giao thức xác nhận ứng dụng |
| Luôn bật syncFlush | Tăng overhead, giảm hiệu quả nén | Chỉ bật khi streaming latency cần |
| Nuốt `IOException` của finish/close | Publish archive hỏng | Báo lỗi và cleanup |
| Dùng `ByteArrayOutputStream.close()` để kiểm tra sự lan truyền close | close của BAOS là no-op | Dùng tracking wrapper trong test |
| Đưa zlib bytes vào GZIP/ZIP reader | Sai file format | Dùng đúng reader cho định dạng |
| Nối bytes tùy ý sau ZIP/GZIP | Không portable | Định nghĩa container/framing |

## 15. Câu hỏi tự kiểm tra

1. Gọi `zip.flush()` rồi đọc ZIP bằng `ZipFile` có bảo đảm đọc được toàn archive không? **Không**, central directory có thể chưa tồn tại.
2. `closeEntry()` và `finish()` khác nhau như thế nào? **Một cái hoàn tất entry hiện tại; một cái kết thúc ZIP archive**.
3. `zip.finish()` có đóng stream đích không? **Không**.
4. `zip.close()` có tự hoàn tất ZIP nếu chưa finish không? **Có**, trước khi đóng output.
5. `gzip.flush()` với `syncFlush=true` có ghi CRC32/ISIZE trailer kết thúc member không? **Không**, cần `finish()`/`close()`.
6. Trailer của GZIP chứa gì? **CRC32 và ISIZE** của dữ liệu chưa nén (ISIZE modulo 2^32).
7. Có thể gọi `putNextEntry()` sau `zip.finish()` không? **Không**; archive đã kết thúc.
8. Hàm nhận output do framework quản lý có nên luôn close `ZipOutputStream` bọc trực tiếp output đó không? **Không mặc định**; cần tuân thủ ownership, có thể finish hoặc dùng non-closing adapter.
9. Trong code tự tạo ZIP file, `try-with-resources` có đủ để hoàn tất archive và đóng file không? **Có**, nếu close thành công.
10. Vì sao finish có thể lỗi dù write trước đó thành công? **Footer/central directory vẫn cần được ghi ra destination**.
11. `flush()` bảo đảm dữ liệu bền trên SSD chưa? **Không**; cần hợp đồng durability riêng.
12. Vì sao demo cần `TrackedOutput`? Vì close của `ByteArrayOutputStream` không cho ta biết wrapper đã gọi close tới stream bên dưới hay chưa.
13. `GZIPOutputStream` và `ZipOutputStream` có cùng định dạng byte không? **Không**, dù cùng có thể dùng thuật toán DEFLATE.
14. Có nên coi file nén đã hợp lệ nếu giải nén được một phần payload? **Không**, cần hoàn tất format và xác thực toàn bộ theo yêu cầu.
15. Nếu cần nhiều message trên cùng socket thì chỉ việc finish một ZIP/GZIP và nối thêm bytes khác là đủ chưa? **Chưa**, protocol phải quy định framing và reader tương thích.

## Tài liệu API tham khảo

- [Java SE 17 — ZipOutputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/ZipOutputStream.html): `putNextEntry`, `closeEntry`, `finish`, `close`.
- [Java SE 17 — GZIPOutputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/GZIPOutputStream.html): `finish`, constructor `syncFlush` và metadata GZIP.
- [Java SE 17 — DeflaterOutputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/DeflaterOutputStream.html): `finish`, `flush`, `close` và `Deflater.SYNC_FLUSH`.
- [Java SE 17 — OutputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/OutputStream.html): hợp đồng `flush()` và `close()`.
- [Java SE 17 — ZipInputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/ZipInputStream.html), [ZipFile](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/ZipFile.html), [GZIPInputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/GZIPInputStream.html): đọc và xác minh định dạng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="compression-and-archives-006">ZipFile truy cập từng entry và quản lý vòng đời</a>

<details>
<summary>Xem chi tiết</summary>

# ZipFile: truy cập từng entry và quản lý vòng đời trong Java

Một tệp ZIP có thể chứa nhiều **entry**: tệp, thư mục và metadata liên quan. Nếu chương trình chỉ cần lấy một tệp cụ thể từ một archive có hàng trăm entry, không nhất thiết phải đọc lần lượt toàn bộ ZIP theo thứ tự. Lớp `java.util.zip.ZipFile` cung cấp cách **mở ZIP đã tồn tại và tra cứu từng entry**, lấy thông tin qua `ZipEntry`, rồi mở `InputStream` để giải nén nội dung entry mong muốn.

Nhưng các đối tượng này có **vòng đời gắn nhau**: `ZipFile` giữ tài nguyên của archive, còn các stream lấy từ `getInputStream()` phụ thuộc vào archive đang mở. Đóng archive quá sớm, để rò rỉ stream hoặc giải nén đường dẫn không tin cậy đều có thể gây lỗi. Bài viết giải thích cách dùng đúng API, sự khác nhau với `ZipInputStream`, xử lý dữ liệu lớn, lỗi phổ biến và biện pháp an toàn.

## 1. ZipFile, ZipEntry và InputStream có vai trò gì?

| Thành phần | Chức năng |
| --- | --- |
| `ZipFile` | Mở archive ZIP đã tồn tại, duyệt danh sách entry và tra cứu entry theo tên; giữ tài nguyên tới khi đóng |
| `ZipEntry` | Mô tả một entry: tên, thư mục/tệp, kích thước, phương pháp nén, CRC, thời gian... nếu metadata hiện diện |
| `ZipFile.getEntry(name)` | Tìm một entry có tên khớp; trả `null` khi không tìm thấy |
| `ZipFile.entries()` | Trả `Enumeration<? extends ZipEntry>` để duyệt entry |
| `ZipFile.stream()` | Trả `Stream<? extends ZipEntry>` để lọc/sắp xếp entry bằng Stream API |
| `ZipFile.getInputStream(entry)` | Mở một luồng đọc dữ liệu đã giải nén của entry; luồng cần được đóng |
| `ZipInputStream` | Đọc một luồng ZIP theo thứ tự entry, thường dùng khi dữ liệu đến từ `InputStream` thay vì một tệp ZIP có thể truy cập ngẫu nhiên |
| `ZipOutputStream` | Tạo tệp/luồng ZIP và ghi lần lượt các entry |

**ZipFile dùng cho tệp ZIP đã có trên đĩa.** Nó đọc cấu trúc của archive để tìm entry và cho phép ứng dụng mở luồng của một entry được chọn, thay vì phải tiêu thụ toàn bộ các entry trước đó như `ZipInputStream`. Tuy vậy, việc gọi `getEntry()` không có nghĩa toàn bộ nội dung entry đã được giải nén vào RAM: nội dung được đọc khi chương trình đọc `InputStream` tương ứng.

Một mô hình ZIP đơn giản:

```text
backup.zip
├── docs/                 (directory entry, có thể tồn tại tường minh)
│   ├── guide.txt         (file entry)
│   └── notes.txt         (file entry)
└── assets/
    └── logo.png          (file entry)
```

ZIP không nhất thiết chứa một entry riêng cho mọi thư mục cha. Chẳng hạn có thể tồn tại `docs/guide.txt` mà không có entry `docs/`. Vì thế `isDirectory()` chỉ cho biết **entry đang xét** có dạng directory, không bảo đảm mọi thư mục hiện diện thành entry riêng.

## 2. Mở ZipFile và trách nhiệm đóng tài nguyên

Những constructor thường dùng:

```java
ZipFile zip = new ZipFile("backup.zip");

ZipFile zipUtf8 = new ZipFile(
        new File("backup.zip"), StandardCharsets.UTF_8);
```

Đây là đoạn minh họa, cần các import `java.util.zip.ZipFile`, `java.io.File` và `java.nio.charset.StandardCharsets`; chương trình phải xử lý `IOException`. Khi muốn dùng `Path`, có thể gọi `path.toFile()` trên default filesystem tương thích với `File`.

`ZipFile` triển khai `Closeable`, nên dùng `try-with-resources`:

```java
try (ZipFile zip = new ZipFile(archive.toFile())) {
    ZipEntry entry = zip.getEntry("docs/guide.txt");
    if (entry == null) {
        throw new FileNotFoundException("Missing ZIP entry");
    }
    try (InputStream in = zip.getInputStream(entry)) {
        // Đọc nội dung của entry trong lúc ZipFile còn mở.
    }
}
```

Đây là mẫu minh họa với `archive` kiểu `Path`, cùng import `FileNotFoundException` / `InputStream`, chưa phải lớp Java độc lập.

Quy tắc vòng đời:

1. **Mở `ZipFile`** trước, giữ nó sống trong toàn bộ thời gian sử dụng các entry/stream.
2. **Tìm `ZipEntry`**; kiểm tra `null` nếu dùng tên entry từ đầu vào.
3. **Mở `InputStream`** cho entry cần đọc, tiêu thụ nội dung, rồi đóng stream bằng `try-with-resources`.
4. **Đóng `ZipFile`** ở ngoài cùng, sau khi các thao tác đọc đã kết thúc.

Theo hợp đồng API, đóng `ZipFile` cũng đóng những `InputStream` được trả về bởi `getInputStream()`. Tuy vậy, **đừng dựa vào điều này để cố ý bỏ qua đóng các stream**: đóng từng stream ở phạm vi nhỏ nhất giúp rõ quyền sở hữu, sớm thu hồi tài nguyên và tránh lỗi đọc trên archive đã đóng.

Sau khi `ZipFile.close()`, không tiếp tục gọi `getEntry()`, `entries()` hoặc đọc những stream gắn với archive như thể file vẫn còn mở. Nếu cần dữ liệu tồn tại độc lập sau khi đóng, phải **sao chép bytes hoặc xử lý hết nội dung** trong phạm vi `ZipFile` còn hoạt động.

## 3. Truy cập trực tiếp một entry theo tên

Ví dụ muốn đọc `config/app.properties` trong `settings.zip`:

```java
// Minh họa trong phương thức có xử lý IOException.
try (ZipFile zip = new ZipFile("settings.zip")) {
    ZipEntry entry = zip.getEntry("config/app.properties");
    if (entry == null) {
        System.out.println("Entry không tồn tại");
    } else if (entry.isDirectory()) {
        System.out.println("Đường dẫn chỉ một thư mục");
    } else {
        try (InputStream in = zip.getInputStream(entry)) {
            in.transferTo(System.out);
        }
    }
}
```

`getEntry(name)` nhận **tên entry được lưu trong ZIP**, không nhận đường dẫn filesystem bên ngoài. Trên ZIP thông thường, dấu `/` dùng phân tách thành phần tên, ví dụ `config/app.properties`. Không đổi nó thành `config\app.properties` chỉ vì hệ điều hành đang chạy là Windows.

Một số điểm dễ bỏ sót:

- **Tên phân biệt chữ hoa/chữ thường** theo chuỗi tên entry: `Guide.txt` và `guide.txt` không nên coi là một.
- `getEntry()` có thể trả `null`; phải kiểm tra trước `getInputStream()`.
- `entry.isDirectory()` dựa vào dấu `/` cuối tên theo quy ước `ZipEntry`; không nên đọc thư mục như file dữ liệu.
- Archive có thể chứa **tên entry trùng nhau**. `getEntry(name)` chỉ cho một entry phù hợp; nếu nghiệp vụ đòi hỏi xác định duy nhất, hãy duyệt toàn bộ và **từ chối tên trùng** thay vì suy đoán entry nào là “đúng”.
- `ZipFile` không tự trích xuất file ra ổ đĩa: đọc `InputStream` khác với ghi bytes vào đường dẫn đích.

## 4. Duyệt tất cả entry: Enumeration và Stream

Duyệt với `Enumeration`:

```java
try (ZipFile zip = new ZipFile("backup.zip")) {
    Enumeration<? extends ZipEntry> entries = zip.entries();
    while (entries.hasMoreElements()) {
        ZipEntry e = entries.nextElement();
        System.out.println(e.getName());
    }
}
```

Với `Stream` (Java 9+):

```java
try (ZipFile zip = new ZipFile("backup.zip")) {
    zip.stream()
            .filter(e -> !e.isDirectory())
            .map(ZipEntry::getName)
            .sorted()
            .forEach(System.out::println);
}
```

Cả hai là **đoạn minh họa** với import `ZipFile`, `ZipEntry` và `Enumeration`. `Enumeration` thích hợp cho vòng lặp cơ bản; `Stream` thuận tiện khi cần lọc và sắp xếp. **Hãy duyệt trong khi ZipFile đang mở**. Đóng `Stream` (nếu dùng `try-with-resources` cho stream) không thay thế `ZipFile.close()`.

Không giả định thứ tự entry do `entries()` hoặc `stream()` trả về giống thứ tự danh sách mong muốn. Nếu cần output ổn định, **tự sắp xếp** theo tên hoặc tiêu chí nghiệp vụ. Nếu ZIP cực lớn, cách `stream().sorted()` cần giữ metadata của nhiều entry để sắp xếp; duyệt tuần tự không sắp xếp sẽ tiết kiệm bộ nhớ hơn.

### ZipEntry chứa những metadata nào?

| API | Thông tin |
| --- | --- |
| `getName()` | Tên entry trong ZIP |
| `isDirectory()` | Entry có dạng thư mục theo quy ước tên kết thúc bằng `/` |
| `getSize()` | Số byte dữ liệu sau giải nén, có thể `-1` nếu chưa biết |
| `getCompressedSize()` | Số byte dữ liệu đã nén, có thể `-1` nếu chưa biết |
| `getCrc()` | CRC-32 lưu trong ZIP, có thể `-1` nếu chưa biết |
| `getMethod()` | Phương pháp nén, thường `ZipEntry.STORED` hoặc `ZipEntry.DEFLATED` |
| `getTime()` | Thời điểm sửa đổi, nếu có; có thể `-1` |
| `getComment()` | Ghi chú cho entry nếu có |
| `getExtra()` | Dữ liệu bổ sung trong phần metadata nếu có |

Các trường này **không đồng nghĩa với việc đã đọc/giải nén thành công payload**. Metadata trong ZIP có thể thiếu, không đáng tin hoặc không phù hợp kỳ vọng của ứng dụng. CRC-32 giúp phát hiện một số sai lệch ngẫu nhiên, **không phải chữ ký số, MAC hay cơ chế xác thực chống kẻ tấn công**.

## 5. Charset tên entry: dữ liệu byte khác với tên file

Các overload `ZipFile(File, Charset)` hoặc `ZipFile(File, int, Charset)` cho phép chỉ định charset để giải mã **tên entry và comment** khi định dạng tên trong archive không được đánh dấu UTF-8. Nếu ZIP entry có cờ UTF-8 theo đặc tả, tên của entry đó được giải mã bằng UTF-8 bất kể charset truyền vào.

Ví dụ khi phải đọc archive cũ được tạo bằng charset di sản:

```java
// Minh họa, cần biết chính xác encoding mà bên tạo ZIP đã dùng.
Charset legacy = Charset.forName("windows-1252");
try (ZipFile zip = new ZipFile(archive.toFile(), legacy)) {
    for (ZipEntry entry : java.util.Collections.list(zip.entries())) {
        System.out.println(entry.getName());
    }
}
```

Không chọn charset bằng cách thử ngẫu nhiên rồi dùng kết quả sai lệch cho tên file. Charset trong constructor **không tự động quyết định encoding nội dung văn bản bên trong một entry**; payload cần decoder đúng theo định dạng file hoặc giao thức riêng. Ví dụ một entry chứa văn bản UTF-16 vẫn có thể có tên ZIP được giải mã bằng UTF-8.

Với archive mới, nên ưu tiên UTF-8 nhất quán. Với archive nhận từ bên ngoài, đôi khi phải biết tool đã tạo ZIP và quy ước encoding để tránh tên bị lỗi.

## 6. Ví dụ Java 17+ hoàn chỉnh: tạo ZIP, chọn entry và đóng đúng vòng đời

Ví dụ sau tạo một ZIP tạm bằng `ZipOutputStream`, sau đó mở nó bằng `ZipFile` và đọc **một entry xác định**. Archive có ba entry, gồm một directory entry, một tệp trong thư mục và một tệp ở gốc.

Tạo file **`ZipFileEntryDemo.java`**:

```java
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class ZipFileEntryDemo {
    private ZipFileEntryDemo() {}

    public static void main(String[] args) throws IOException {
        Path archive = Files.createTempFile("zip-file-demo-", ".zip");

        try {
            try (ZipOutputStream output = new ZipOutputStream(
                    Files.newOutputStream(archive), StandardCharsets.UTF_8)) {
                output.putNextEntry(new ZipEntry("docs/"));
                output.closeEntry();
                addText(output, "docs/hello.txt", "Hello ZIP!\n");
                addText(output, "readme.txt", "Sample archive\n");
            }

            try (ZipFile zip = new ZipFile(
                    archive.toFile(), StandardCharsets.UTF_8)) {
                System.out.println("entries=" + zip.size());

                String names = zip.stream()
                        .map(ZipEntry::getName)
                        .sorted()
                        .collect(Collectors.joining(","));
                System.out.println("names=" + names);

                ZipEntry entry = zip.getEntry("docs/hello.txt");
                if (entry == null || entry.isDirectory()) {
                    throw new IOException("Expected text entry missing");
                }

                System.out.println("chosen=" + entry.getName());
                System.out.println("uncompressed bytes=" + entry.getSize());

                try (InputStream input = zip.getInputStream(entry)) {
                    byte[] content = input.readAllBytes();
                    System.out.println("text=" + new String(
                            content, StandardCharsets.UTF_8).strip());
                }

                System.out.println("missing="
                        + (zip.getEntry("missing.txt") == null));
            }
        } finally {
            Files.deleteIfExists(archive);
        }
    }

    private static void addText(ZipOutputStream output,
                                String name,
                                String content) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(content.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }
}
```

Biên dịch và chạy với JDK 17+:

```bash
javac ZipFileEntryDemo.java
java ZipFileEntryDemo
```

Đầu ra trong môi trường I/O bình thường:

```text
entries=3
names=docs/,docs/hello.txt,readme.txt
chosen=docs/hello.txt
uncompressed bytes=11
text=Hello ZIP!
missing=true
```

**Giải thích:** `ZipOutputStream` tạo đúng ba entry: `docs/` (thư mục), `docs/hello.txt` và `readme.txt`. Lời gọi `zip.size()` đếm **entry**, không phải byte. `zip.stream()` lấy tên rồi `sorted()` để output không phụ thuộc thứ tự duyệt. `getEntry("docs/hello.txt")` chọn tệp dài 11 byte ASCII `Hello ZIP!\n`. `getInputStream(entry)` trả luồng byte đã giải nén để đọc; `strip()` bỏ ký tự xuống dòng trước khi in. `getEntry("missing.txt")` trả `null`.

Ví dụ chỉ gọi `readAllBytes()` với entry mà **chính chương trình đã tạo** và biết trước dài 11 byte. **Không dùng `readAllBytes()` vô điều kiện với ZIP không tin cậy**: dữ liệu sau giải nén có thể rất lớn dù ZIP nén nhỏ. Phần tiếp theo trình bày cách đọc theo luồng và đặt giới hạn.

### Kiểm soát vòng đời của ví dụ

- `ZipOutputStream` đóng trước khi `ZipFile` mở archive; điều này bảo đảm ZIP đã được ghi hoàn chỉnh phần cấu trúc cuối.
- `InputStream` của entry được đóng **trước** `ZipFile`.
- `ZipFile` được đóng trước khi xóa file tạm.
- Khối `finally` cố gắng dọn ZIP tạm ngay cả khi thao tác đọc thất bại.
- Không giữ tham chiếu stream hoặc dùng `ZipFile` sau khi đã đóng.

## 7. Đọc entry lớn theo luồng thay vì nạp toàn bộ vào heap

`ZipFile.getInputStream(entry)` cho phép đọc incremental bằng bộ đệm cố định. Ví dụ trích dữ liệu của một entry ra file đích với vòng lặp:

```java
// Đoạn minh họa, zip và entry còn hợp lệ;
// destination được ứng dụng kiểm soát và không trùng file ZIP.
try (InputStream input = zip.getInputStream(entry);
     OutputStream output = Files.newOutputStream(destination)) {

    byte[] buffer = new byte[8192];
    int read;
    while ((read = input.read(buffer)) != -1) {
        output.write(buffer, 0, read);
    }
}
```

Đây là đoạn minh họa cần import `OutputStream`/`Files`, xử lý `IOException`, và phải có chính sách bảo vệ `destination`. Nó tránh tạo mảng bằng toàn bộ uncompressed size, nhưng **không tự giới hạn tổng số byte được ghi ra đĩa**: nếu entry là ZIP bomb, vẫn có thể làm đầy thiết bị.

Một phiên bản kiểm soát tối đa số byte cho **một entry**:

```java
// Hàm độc lập về mặt logic, đặt trong một lớp Java đã import InputStream,
// OutputStream, IOException; dùng khi nguồn là entry không tin cậy.
static long copyAtMost(InputStream input,
                       OutputStream output,
                       long maxBytes) throws IOException {
    if (maxBytes < 0) {
        throw new IllegalArgumentException("maxBytes < 0");
    }

    byte[] bytes = new byte[8192];
    long total = 0;
    int n;
    while ((n = input.read(bytes)) != -1) {
        if (n > maxBytes - total) {
            throw new IOException("Entry exceeds size limit");
        }
        output.write(bytes, 0, n);
        total += n;
    }
    return total;
}
```

Hàm này giới hạn byte **thực đọc/ghi**; phép `maxBytes - total` không tràn nếu `maxBytes` không âm và `total` được giữ không vượt ngưỡng. Với output file, nếu vượt ngưỡng, **phải xóa file tạm đang ghi dở** trong quy trình cleanup. Muốn xử lý archive nhiều entry còn cần tổng ngân sách toàn archive, giới hạn số entry, hạn mức thời gian/CPU và chính sách file name.

`ZipEntry.getSize()` có thể dùng để từ chối sớm entry được khai báo quá lớn nhưng **không thay thế kiểm soát số byte thực giải nén**. Metadata có thể thiếu (`-1`) hoặc không đáng tin; giới hạn phải áp dụng ngay khi đọc stream.

## 8. Bảo mật khi trích xuất: Zip Slip và đường dẫn đích

Một ZIP từ bên ngoài có thể chứa entry tên:

```text
../../outside.txt
/absolute/path.txt
safe/../outside.txt
```

Nếu chương trình nối tên entry trực tiếp vào đường dẫn đích mà không kiểm tra, dữ liệu có thể bị ghi **ra ngoài thư mục giải nén dự kiến** (*Zip Slip/path traversal*). Một bước bảo vệ căn bản là chuẩn hóa đường dẫn và xác minh vẫn nằm dưới thư mục đích:

```java
// Đoạn minh họa về kiểm tra đường dẫn, chưa phải bộ giải nén an toàn đầy đủ.
Path root = destinationRoot.toAbsolutePath().normalize();
Path target = root.resolve(entry.getName()).normalize();

if (!target.startsWith(root) || target.equals(root)) {
    throw new IOException("Unsafe entry path: " + entry.getName());
}
```

Đừng dùng `String.startsWith(root.toString())` để kiểm tra containment: tiền tố chuỗi như `/safe/out` và `/safe/outside` có thể gây nhầm. Hãy dùng `Path.startsWith(Path)` sau `normalize()`, theo ngữ nghĩa đường dẫn của filesystem.

**Lưu ý cực kỳ quan trọng:** Kiểm tra lexical `normalize()` / `startsWith()` **không đủ an toàn khi thư mục đích có symbolic link hoặc bị tác nhân khác thay đổi đồng thời**. Ví dụ `root/link` là symlink trỏ ra ngoài, thì `root/link/file.txt` vượt qua kiểm tra lexical nhưng việc ghi thực có thể đi ra ngoài root. Nếu trích xuất ZIP không tin cậy:

1. Ưu tiên giải nén vào **thư mục riêng mới tạo, không cho bên không tin cậy chỉnh sửa**, với quyền truy cập tối thiểu.
2. Từ chối entry có tên không hợp lệ, đường dẫn tuyệt đối, thành phần đi ngược cha (`..`) hoặc tên không tương thích quy ước triển khai. Khi chạy đa nền tảng, xử lý dấu phân cách và tên đặc biệt trên hệ điều hành đích.
3. Kiểm tra đường dẫn sau chuẩn hóa, nhưng **không coi đây là lá chắn duy nhất** trước symlink/race. Với yêu cầu đối kháng mạnh, dùng thao tác filesystem không đi theo symlink hoặc cơ chế truy cập directory descriptor phù hợp nền tảng.
4. Chỉ tạo thư mục/tệp dưới root đã được kiểm soát; có chính sách xử lý entry trùng tên, file đã tồn tại và tranh chấp.
5. Giới hạn số entry, kích thước mỗi entry, tổng kích thước giải nén và độ sâu đường dẫn; dùng stream để kiểm soát tài nguyên.
6. Không coi ZIP là định dạng đã được xác thực về nguồn gốc hoặc tính toàn vẹn đối kháng. Nếu cần xác thực, dùng chữ ký hoặc cơ chế integrity/authentication riêng.

Những quy tắc này áp dụng khi **ghi dữ liệu ra filesystem**. Nếu chỉ đọc byte của một entry trong bộ nhớ, không có path traversal từ việc gọi `getEntry()`, nhưng vẫn có rủi ro về tiêu thụ bộ nhớ/CPU do dữ liệu nén độc hại.

## 9. ZipFile và ZipInputStream: chọn API theo nhu cầu

| Tiêu chí | `ZipFile` | `ZipInputStream` |
| --- | --- | --- |
| Nguồn dữ liệu | File ZIP có thể mở bằng `File` | `InputStream` chứa luồng ZIP |
| Truy cập entry theo tên | `getEntry(name)` trực tiếp | Thường phải đọc từng entry theo thứ tự bằng `getNextEntry()` |
| Duyệt danh sách entry | `entries()`, `stream()` | Vòng `getNextEntry()` / `closeEntry()` |
| Luồng đọc nội dung | `zip.getInputStream(entry)` cho entry được chọn | Đọc trực tiếp từ `ZipInputStream` của entry hiện hành |
| Metadata archive | Có thể tra từ cấu trúc ZIP, gồm central directory | Có thể chỉ biết một phần metadata trước khi đọc entry |
| Quản lý tài nguyên | `ZipFile` và từng `InputStream` | `ZipInputStream` làm tài nguyên chính |
| Tình huống nên dùng | Đọc một vài entry được biết tên trong ZIP đã lưu | Xử lý ZIP đến từ network stream hoặc luồng tuần tự |

Không có lựa chọn “luôn nhanh hơn”. Với archive nằm trên đĩa và chỉ cần một entry, `ZipFile` tránh phải duyệt payload của mọi entry theo trình tự. Nếu dữ liệu ZIP đang đến qua HTTP/socket dưới dạng luồng và không được lưu thành tệp, `ZipInputStream` phù hợp hơn.

`ZipFile` cũng không đồng nghĩa với ánh xạ toàn bộ archive vào bộ nhớ. Điều đáng quan tâm khi chọn API là **có cần tra entry theo tên và có khả năng truy cập một tệp ZIP đã tồn tại hay không**.

## 10. Lỗi thường gặp và cách khắc phục

| Lỗi | Hệ quả | Cách xử lý |
| --- | --- | --- |
| Quên đóng `ZipFile` | Giữ file descriptor/tài nguyên, có thể cản trở xóa/đổi tên file tùy hệ điều hành | `try-with-resources` cho archive |
| Lấy stream rồi đóng `ZipFile` trước khi đọc | Đọc thất bại vì tài nguyên nguồn đã đóng | Giữ ZipFile sống lâu hơn tất cả stream |
| Không đóng `InputStream` của entry | Khó kiểm soát giải phóng tài nguyên và phạm vi đọc | `try-with-resources` lồng bên trong ZipFile |
| Không kiểm tra `getEntry(name) == null` | `NullPointerException` hoặc logic lỗi | Kiểm tra missing entry trước khi mở stream |
| Đọc entry thư mục như file thường | Xử lý dữ liệu sai mục đích | Kiểm tra `isDirectory()` |
| Nghĩ `getEntry()` đã đọc toàn bộ payload | Ước lượng sai I/O/bộ nhớ | Đọc `InputStream` và đóng đúng lúc |
| Dùng `readAllBytes()` cho ZIP không tin cậy | Có thể cạn RAM vì ZIP bomb | Đọc theo luồng, đặt hạn mức bytes |
| Dựa duy nhất vào `getSize()` | Metadata có thể thiếu hoặc không đáng tin | Kiểm soát byte thực giải nén |
| Dùng tên entry làm đường dẫn đích mà không kiểm tra | Zip Slip ghi ra ngoài thư mục dự kiến | Normalize, containment và phòng symlink/race |
| Tự giả định thứ tự duyệt entry | Output không ổn định hoặc sai thứ tự nghiệp vụ | Sort khi cần hoặc thiết kế xử lý không dựa vào thứ tự |
| Coi tên entry giống path của OS | Sai dấu phân cách, sai tên hoặc trích xuất không an toàn | Phân biệt tên ZIP với path ở filesystem đích |
| Coi CRC-32 là chữ ký xác thực | Không phát hiện sửa đổi có chủ đích theo mô hình bảo mật mạnh | Dùng chữ ký/kiểm tra xác thực phù hợp |
| Gọi `ZipFile.getInputStream()` với entry tên trùng mà không có chính sách | Có thể lấy sai bản khi ZIP có tên trùng | Duyệt, từ chối trùng nếu cần tính duy nhất |
| Xóa/di chuyển ZIP khi `ZipFile` đang mở | Hành vi khác nhau theo OS, có thể thất bại | Đóng archive rồi mới thay đổi file |

## 11. Câu hỏi tự kiểm tra

**1. Nếu chỉ cần đọc `docs/readme.txt` từ một ZIP lớn trên đĩa, dùng gì?**  
Mở `ZipFile`, gọi `getEntry("docs/readme.txt")`, kiểm tra kết quả rồi `getInputStream()` cho đúng entry. Không cần lần lượt giải nén các entry trước nó.

**2. `ZipFile.close()` có tương đương `InputStream.close()` không?**  
Đóng `ZipFile` sẽ đóng những stream mà nó cấp, nhưng quản lý stream bằng khối `try-with-resources` riêng là thiết kế rõ ràng hơn. Ngược lại, đóng **một** stream entry không có nghĩa đóng toàn bộ `ZipFile`.

**3. Có thể trả `InputStream` của entry từ một phương thức đã đóng `ZipFile` trong `try-with-resources` không?**  
Không nên: stream phụ thuộc archive còn mở. Hãy xử lý nội dung trong phạm vi ZipFile còn sống, hoặc thiết kế một tài nguyên sở hữu cả ZipFile và stream và phải được caller đóng đúng cách.

**4. Khi `entry.getSize()` nhỏ, đã đủ chắc chắn an toàn để giải nén vào RAM chưa?**  
Chưa. ZIP không tin cậy cần giới hạn số byte thực đọc, số entry, tổng byte, nguồn dữ liệu và tài nguyên khác.

**5. `zip.stream().close()` có thay thế `zip.close()` không?**  
Không. `ZipFile` vẫn là chủ sở hữu tài nguyên archive, cần đóng rõ ràng.

**Ghi nhớ:** `ZipFile` cho phép tra cứu và đọc **từng entry** trong archive ZIP đã tồn tại. Mở `ZipFile` ở phạm vi ngoài, mở/đóng `InputStream` của entry ở phạm vi trong, luôn kiểm tra entry thiếu, không giả định metadata/trật tự duyệt và giới hạn việc giải nén dữ liệu không tin cậy. Quản lý vòng đời đúng giúp tránh lỗi đọc archive đã đóng, rò rỉ tài nguyên và các lỗ hổng khi trích xuất.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="compression-and-archives-007">Charset tên entry và metadata của archive</a>

<details>
<summary>Xem chi tiết</summary>

# ZIP archive: charset của tên entry và metadata trong Java

Trong Java Core I/O, các lớp `java.util.zip.ZipFile`, `ZipInputStream`, `ZipOutputStream` và `ZipEntry` giúp đọc, ghi và kiểm tra file ZIP. Một archive không chỉ chứa **bytes nội dung**: mỗi entry còn có **tên đường dẫn, comment, thời gian, kích thước, phương thức nén, CRC và các extra fields**. Nếu thiết kế sai charset tên entry hoặc diễn giải metadata thiếu thận trọng, file ZIP có thể hiển thị tên tiếng Việt bị lỗi, mất dấu thời gian, hoặc gây lỗi và rủi ro khi giải nén.

**Ý chính:** Charset để mã hóa **tên entry/comment** và charset của **nội dung file bên trong entry** là hai việc độc lập. Với archive mới nên chọn **UTF-8** nhất quán. Với ZIP legacy, cần biết định dạng thực tế và bit đánh dấu UTF-8 trong ZIP trước khi đọc bằng charset khác. Metadata là **thông tin có điều kiện**, không phải bằng chứng nội dung an toàn, nguyên vẹn hay đã được xác thực.

## 1. Archive, entry và nội dung là ba tầng khác nhau

Một ZIP gồm nhiều entry và các cấu trúc như local file header, dữ liệu nén, central directory và phần kết thúc archive. Mỗi entry đại diện một file hoặc một đường dẫn thư mục. Ví dụ:

```text
documents.zip
├── tài-liệu/                     # Có thể là directory entry, nếu được ghi
│   └── đề-cương.txt               # Tên ZIP entry: "tài-liệu/đề-cương.txt"
├── images/
│   └── logo.png
└── README.md

Mỗi file entry gồm các khái niệm:
  name                tên/đường dẫn nội bộ archive
  comment             ghi chú tùy chọn của entry
  compressed data     bytes file sau khi nén
  CRC-32              checksum của bytes gốc
  size                kích thước không nén
  compressedSize      kích thước dữ liệu nén
  method              ví dụ STORED hoặc DEFLATED
  timestamps/extra    metadata và các trường mở rộng
```

Tên entry sử dụng quy ước ZIP với dấu phân cách đường dẫn `/`. Đừng đồng nhất nó với `Path` của hệ điều hành hiện tại: trên Windows, ZIP entry thường vẫn dùng `/`; archive có thể chứa tên lạ hoặc độc hại không thể ánh xạ trực tiếp sang file hợp lệ.

Một entry thư mục thường có tên **kết thúc bằng `/`**, ví dụ `"documents/"`, và `ZipEntry.isDirectory()` kiểm tra quy ước này. ZIP có thể chỉ chứa file entry `"documents/report.txt"` mà **không có entry thư mục `"documents/"` riêng**. Khi giải nén, ứng dụng có thể cần tạo thư mục cha.

## 2. Charset tên entry không phải charset của payload

Phân biệt rõ bốn khái niệm:

| Thành phần | Được mã hóa hoặc diễn giải ra sao? | Java API tiêu biểu |
| --- | --- | --- |
| Tên entry | Một chuỗi ký tự được chuyển thành các byte trong ZIP header/central directory. | `new ZipOutputStream(out, charset)`; `new ZipFile(file, charset)` |
| Comment của entry | Metadata dạng chuỗi; encoding phải tương thích với bên đọc. | `ZipEntry.setComment()` / `getComment()` |
| Comment toàn archive | Ghi chú lưu ở vùng kết thúc central directory. | `ZipOutputStream.setComment()`; `ZipFile.getComment()` |
| Nội dung file | Dãy byte tùy loại dữ liệu; ZIP không ép nó là văn bản UTF-8. | `out.write(bytes)`; `ZipFile.getInputStream(entry)` |

Ví dụ một ZIP lưu tên `"tài-liệu/đề-cương.txt"` bằng UTF-8. Bên trong có thể là một ảnh JPEG, tập tin PDF, hoặc một `.txt` với nội dung dùng UTF-8, UTF-16 hay một charset legacy khác. Tham số `Charset` của `ZipOutputStream` chỉ hướng tới mã hóa **tên và comment thuộc cấu trúc ZIP**; nó không tự chuyển mọi bytes nội dung thành UTF-8.

Mẫu đúng cho file text UTF-8:

```java
byte[] payload = "Xin chào".getBytes(StandardCharsets.UTF_8);
out.putNextEntry(new ZipEntry("tài-liệu/chào.txt"));
out.write(payload);
out.closeEntry();
```

Đây là snippet giả định `out` là `ZipOutputStream` đã được mở với UTF-8, có import `StandardCharsets` và `ZipEntry`. Khi đọc, giải mã **payload** bằng `new String(bytes, StandardCharsets.UTF_8)` hoặc dùng `InputStreamReader(in, UTF_8)` nếu biết file thật sự là văn bản UTF-8.

## 3. UTF-8 flag (bit 11) và charset legacy trong ZIP

Ở định dạng ZIP, **general purpose bit flag bit 11** còn được gọi là *language encoding flag* hoặc *EFS flag*. Khi được bật, tên entry và comment tương ứng được hiểu theo **UTF-8**. Bên đọc tương thích phải tôn trọng bit này, không ép tên UTF-8 thành CP437 hay một charset khác.

Khi bit này **không được bật**, tên/comment có thể được mã hóa theo một charset truyền thống. ZIP cổ điển thường gắn với **IBM437 (CP437)**; ngoài thực tế còn gặp tên do công cụ cũ ghi bằng Windows code page hoặc encoding địa phương. Vì vậy:

- Với ZIP do ứng dụng **mới** tạo, ưu tiên `StandardCharsets.UTF_8` và một công cụ phát hành thống nhất.
- Với ZIP **legacy**, cần biết charset mà nhà sản xuất ZIP thực sự sử dụng; chỉ vì file hiển thị lỗi dấu không đủ kết luận nó thuộc charset nào.
- Nếu ZIP có **UTF-8 flag**, charset legacy truyền vào các constructor đọc tên không được dùng để diễn giải những tên đã đánh dấu UTF-8. Đọc theo bit flag của ZIP.
- Nếu ZIP không có flag và tên đã được ghi bằng một bảng mã khác, dùng `Charset` phù hợp để diễn giải tên entry.
- Một số định dạng mở rộng ZIP có Unicode Path Extra Field (`0x7075`) và Unicode Comment Extra Field (`0x6375`), thường đi kèm CRC để liên hệ với tên/comment gốc. **Không mặc định** mọi thư viện ZIP đều tự xử lý những extra fields ấy theo cùng cách.

**Quan trọng về khả năng liên thông:** API `java.util.zip` không hứa tự phát hiện mọi charset legacy qua heuristic. Nếu cần hỗ trợ file ZIP từ nhiều nguồn, hãy đặt một chính sách chọn charset có cấu hình, ưu tiên bit flag/hợp đồng nguồn, thử các fixture thực tế, và xử lý lỗi giải mã thay vì âm thầm làm sai tên file.

## 4. Chọn charset đúng trong Java

Java 17+ có các constructor hữu ích:

```java
// Mặc định hiện đại: UTF-8 cho tên ZIP.
ZipOutputStream out1 = new ZipOutputStream(outputStream);
ZipInputStream in1 = new ZipInputStream(inputStream);
ZipFile zip1 = new ZipFile(file);

// Chỉ định rõ UTF-8 (phù hợp archive mới).
ZipOutputStream out2 = new ZipOutputStream(
        outputStream2, StandardCharsets.UTF_8);
ZipFile zip2 = new ZipFile(file2, StandardCharsets.UTF_8);

// Đọc/ghi ZIP legacy có charset tương thích.
Charset cp437 = Charset.forName("IBM437");
ZipInputStream legacyStream = new ZipInputStream(inputStream3, cp437);
ZipFile legacyZip = new ZipFile(legacyFile, cp437);
```

**Đây là các snippet riêng**, không phải một đoạn chạy độc lập: các biến stream/file phải đã được khởi tạo, và từng đối tượng sau khi dùng cần đóng đúng vòng đời bằng `try-with-resources`. Khi đặt charset khác UTF-8, phải chắc chắn charset đó mã hóa được những tên/comment dự kiến. Chẳng hạn `IBM437` có thể mã hóa `"café.txt"`, nhưng không thể mã hóa đầy đủ một tên file tiếng Việt hiện đại.

Ghi chú API:

| Lớp | Chức năng | Điểm cần lưu ý |
| --- | --- | --- |
| `ZipOutputStream(OutputStream, Charset)` | Tạo ZIP, mã hóa tên/comment theo quy định và charset đã chọn. | Phù hợp khi cần kiểm soát archive mới; encoding không hợp lệ có thể gây exception. |
| `ZipInputStream(InputStream, Charset)` | Đọc entry tuần tự, dùng charset thích hợp cho tên. | Truy cập tuần tự qua local file headers; không cung cấp trải nghiệm metadata/tra cứu toàn bộ như ZipFile. |
| `ZipFile(File, Charset)` | Mở central directory để tra cứu entry, comment và metadata. | Đọc tên đúng charset; phải đóng để giải phóng resource. |
| `ZipEntry(String)` | Đặt tên logic của một entry. | Không nhận `Charset`; việc mã hóa tên diễn ra ở ZipOutputStream. |

Trong Java 17, các overload không truyền charset của thư viện Java tiêu chuẩn dùng UTF-8 theo hợp đồng JDK. Tránh giả định archive được giải nén ở một phần mềm **khác** sẽ luôn dùng UTF-8 nếu file legacy không có flag.

## 5. Metadata thường gặp trên ZipEntry

`ZipEntry` đưa ra một số thông tin:

| API | Ý nghĩa | Lưu ý |
| --- | --- | --- |
| `getName()` | Tên entry đã giải mã. | Có thể là đường dẫn tương đối, tuyệt đối hoặc chuỗi chứa traversal do archive không tin cậy cung cấp. |
| `getComment()` / `setComment()` | Ghi chú tùy chọn của entry. | Có thể null; không dùng như dữ liệu có bảo đảm xác thực. |
| `getMethod()` / `setMethod()` | Phương thức ZIP; JDK tiêu biểu `STORED` và `DEFLATED`. | Không phải mọi compression method từ công cụ bên ngoài đều được JDK hỗ trợ. |
| `getSize()` / `setSize()` | Kích thước uncompressed, byte. | Có thể trả `-1` nếu chưa biết. |
| `getCompressedSize()` / `setCompressedSize()` | Số byte nén. | Có thể trả `-1` nếu chưa biết; phụ thuộc phương thức và thời điểm tạo/đọc. |
| `getCrc()` / `setCrc()` | CRC-32 của nội dung trước nén. | Có thể trả `-1` nếu chưa biết; CRC kiểm tra lỗi ngẫu nhiên, **không** phải chữ ký mật mã. |
| `getTime()` / `setTime(long)` | Thời điểm sửa đổi theo timestamp dạng milliseconds. | Giá trị có thể thiếu, phạm vi/độ chính xác phụ thuộc metadata ZIP. |
| `getLastModifiedTime()` | `FileTime` của mốc sửa đổi nếu có. | Có thể null; độ chính xác và cách biểu diễn phụ thuộc extra fields. |
| `getLastAccessTime()` / `getCreationTime()` | Mốc truy cập/tạo nếu archive cung cấp. | Có thể null; không suy diễn thiếu thành `now()`. |
| `getExtra()` / `setExtra(byte[])` | Các extra fields theo định dạng nhị phân ZIP. | Có thể null; cần parse theo ID/độ dài và kiểm tra biên nghiêm ngặt. |
| `isDirectory()` | Kiểm tra tên kết thúc bằng `/`. | Không xác nhận file ngoài archive thật sự là thư mục an toàn. |

Thông số metadata có thể không được ghi đầy đủ trong mọi ZIP. Với ZIP được tạo từ một công cụ bên ngoài, một entry có thể thiếu comment, missing creation time, hoặc lưu thời gian với độ chính xác khác kỳ vọng. Khi copy/repack ZIP, không nên vô tình thay mọi timestamp về thời điểm hiện tại nếu ứng dụng cần bảo tồn thời gian nguồn.

### Metadata toàn archive

`ZipOutputStream.setComment(String)` đặt **archive comment**, còn `ZipEntry.setComment(String)` đặt **entry comment**. `ZipFile.getComment()` đọc archive comment. Hai loại này là hai cấu trúc riêng, không được nhầm với payload của file `README.txt`.

Giới hạn ghi chú ZIP thường tính **theo số byte được mã hóa**, tối đa 65.535 byte cho trường comment tương ứng. Chuỗi Java có 30.000 ký tự Unicode không đảm bảo nằm trong giới hạn bytes khi mã hóa UTF-8. Với archive do bên ngoài cung cấp, comment có thể null hoặc có ký tự ngoài charset.

## 6. Timestamps và độ chính xác

ZIP cổ điển có trường thời gian ngày/giờ kiểu MS-DOS, với một số giới hạn: độ phân giải giây có thể là **2 giây**, dải ngày bị giới hạn và thông tin múi giờ không luôn đầy đủ. Các extra fields thường được dùng để bổ sung timestamp có độ chính xác/ý nghĩa tốt hơn; nhưng các tool ZIP khác nhau có thể ghi hoặc đọc chúng không giống nhau.

Java cho phép đặt:

```java
ZipEntry entry = new ZipEntry("notes.txt");
entry.setLastModifiedTime(
        FileTime.from(Instant.parse("2024-01-02T03:04:06Z")));
entry.setLastAccessTime(
        FileTime.from(Instant.parse("2024-01-02T03:04:06Z")));
entry.setCreationTime(
        FileTime.from(Instant.parse("2024-01-02T03:04:06Z")));
```

Đây là snippet dùng imports `java.util.zip.ZipEntry`, `java.nio.file.attribute.FileTime`, `java.time.Instant`; giá trị trong ví dụ chỉ là timestamp mẫu.

**Phân biệt:**

- `setTime(long)` và `setLastModifiedTime(FileTime)` đều ảnh hưởng mốc sửa đổi, nhưng cách biểu diễn khi serialize tùy API/extra fields và khả năng của format.
- Chọn một nguồn dữ liệu có ý nghĩa rõ, tránh vô tình đặt hai mốc sửa đổi mâu thuẫn.
- Mốc creation/access không bắt buộc có trong mọi archive; `getCreationTime()`/`getLastAccessTime()` có thể trả null.
- Khi đối chiếu archive chạy trên nhiều OS hoặc timezone, nên so thời điểm qua `Instant` và chấp nhận độ chính xác theo format. **Không bắt buộc** mọi file ZIP giữ được chính xác nanosecond.
- Các timestamp trong một archive không phải bằng chứng an ninh: người tạo ZIP có thể đặt chúng thành giá trị tùy ý.

Nếu bảo toàn metadata có ý nghĩa pháp lý/nghiệp vụ, hãy định nghĩa schema manifest riêng, kiểm soát nguồn và chữ ký; đừng dựa hoàn toàn vào timestamp ZIP.

## 7. Ví dụ Java 17+ hoàn chỉnh: ZIP UTF-8 và ZIP IBM437

Ví dụ tạo **hai archive độc lập**:

1. ZIP UTF-8 với entry `"tài-liệu/đề-cương.txt"`, comment `"Bản mẫu"`, timestamp cố định.
2. ZIP legacy dùng `IBM437` với entry `"café.txt"`, comment ASCII.
3. Nội dung payload của **cả hai** đều mã hóa UTF-8, bất kể charset của entry name.
4. Mở lại bằng `ZipFile(..., charset)` đúng với archive đã tạo; đọc metadata, tính lại CRC-32 và in ra payload.

Lưu thành **`ZipEntryMetadataDemo.java`**:

```java
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ZipEntryMetadataDemo {
    private static final Instant FIXED_TIME =
            Instant.parse("2024-01-02T03:04:06Z");
    private static final byte[] PAYLOAD =
            "Xin chào".getBytes(StandardCharsets.UTF_8);

    private static void writeArchive(
            Path path, Charset nameCharset, String name, String entryComment)
            throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(
                Files.newOutputStream(path), nameCharset)) {
            out.setComment("Archive note");
            ZipEntry entry = new ZipEntry(name);
            entry.setLastModifiedTime(FileTime.from(FIXED_TIME));
            entry.setComment(entryComment);
            out.putNextEntry(entry);
            out.write(PAYLOAD);
            out.closeEntry();
        }
    }

    private static void inspect(Path path, Charset nameCharset, String name)
            throws IOException {
        try (ZipFile zip = new ZipFile(path.toFile(), nameCharset)) {
            ZipEntry entry = zip.getEntry(name);
            if (entry == null) {
                throw new IOException("Missing entry: " + name);
            }
            byte[] bytes;
            try (InputStream in = zip.getInputStream(entry)) {
                bytes = in.readAllBytes();
            }
            CRC32 check = new CRC32();
            check.update(bytes);
            System.out.println("name=" + entry.getName());
            System.out.println("entry-comment=" + entry.getComment());
            System.out.println("archive-comment=" + zip.getComment());
            System.out.println("mtime=" + entry.getLastModifiedTime().toInstant());
            System.out.println("crc-valid=" + (check.getValue() == entry.getCrc()));
            System.out.println("payload=" +
                    new String(bytes, StandardCharsets.UTF_8));
        }
    }

    public static void main(String[] args) throws IOException {
        Path modern = Files.createTempFile("utf8-zip-", ".zip");
        Path legacy = Files.createTempFile("legacy-zip-", ".zip");
        try {
            writeArchive(modern, StandardCharsets.UTF_8,
                    "tài-liệu/đề-cương.txt", "Bản mẫu");
            writeArchive(legacy, Charset.forName("IBM437"),
                    "café.txt", "Example");
            System.out.println("[UTF-8]");
            inspect(modern, StandardCharsets.UTF_8,
                    "tài-liệu/đề-cương.txt");
            System.out.println("[IBM437]");
            inspect(legacy, Charset.forName("IBM437"), "café.txt");
        } finally {
            Files.deleteIfExists(modern);
            Files.deleteIfExists(legacy);
        }
    }
}
```

Biên dịch/chạy (JDK 17 trở lên, có charset IBM437):

```bash
javac -encoding UTF-8 ZipEntryMetadataDemo.java
java ZipEntryMetadataDemo
```

Stdout đã được biên dịch và chạy kiểm chứng:

```text
[UTF-8]
name=tài-liệu/đề-cương.txt
entry-comment=Bản mẫu
archive-comment=Archive note
mtime=2024-01-02T03:04:06Z
crc-valid=true
payload=Xin chào
[IBM437]
name=café.txt
entry-comment=Example
archive-comment=Archive note
mtime=2024-01-02T03:04:06Z
crc-valid=true
payload=Xin chào
```

Diễn giải:

- `ZipOutputStream(..., StandardCharsets.UTF_8)` ghi tên/comment UTF-8. Với tên tiếng Việt, các byte thường có độ dài khác `String.length()`; bên đọc phải hiểu encoding đúng.
- `Charset.forName("IBM437")` là lựa chọn cho archive thứ hai, nơi `é` được biểu diễn trong bảng mã đó; entry name vẫn được API Java trả về dưới dạng `String` Unicode đúng nếu dùng đúng charset.
- `PAYLOAD` luôn được tạo bằng `getBytes(UTF_8)`. Charset ZIP header không tác động lên nó, vì `ZipOutputStream.write(byte[])` làm việc trên bytes.
- `ZipEntry.setLastModifiedTime(FileTime)` đặt mốc sửa đổi cố định. JDK được kiểm thử trả lại đúng `Instant` trong ví dụ, nhưng các archive từ công cụ khác có thể thiếu extra fields hoặc có độ phân giải khác.
- `ZipEntry.setComment()` và `ZipOutputStream.setComment()` là metadata riêng; đọc qua `ZipFile` cho thấy hai cấp comment.
- `CRC32` tính trên **bytes đã giải nén**, được so với `entry.getCrc()`. Kết quả `true` xác nhận checksum trùng; không chứng minh archive không bị sửa một cách cố ý.
- Ví dụ chỉ có hai archive một-entry, vì thế không cần code xử lý trùng tên, quota, zip bomb hay giải nén ra filesystem; các phần sau sẽ mô tả yêu cầu cho đầu vào không tin cậy.

**Giới hạn của demo:** `ZipFile` mở central directory hoàn chỉnh và ví dụ dùng `readAllBytes()` vì payload nhỏ, do chính chương trình tạo. Với ZIP lớn, không được gọi `readAllBytes()` cho từng entry tùy ý nếu chưa giới hạn kích thước thực tế. Hãy đọc theo chunk với quota.

## 8. ZipFile so với ZipInputStream: metadata không giống nhau ở mọi thời điểm

Hai mô hình đọc:

**`ZipFile`** truy cập thông tin central directory của ZIP. Nó thường phù hợp khi cần liệt kê nhiều entries, lấy comment, size, CRC, compressed size, hoặc truy cập một entry theo tên:

```java
try (ZipFile zip = new ZipFile(archive.toFile(),
        StandardCharsets.UTF_8)) {
    ZipEntry found = zip.getEntry("README.md");
    if (found != null) {
        System.out.println("size=" + found.getSize());
        try (InputStream input = zip.getInputStream(found)) {
            // Xử lý input theo chunk/quota.
        }
    }
}
```

Đây là snippet; `archive` phải là `Path` hợp lệ. `ZipFile.close()` sẽ giải phóng resource gắn với file nén; đóng stream từng entry khi dùng xong giúp code rõ ràng.

**`ZipInputStream`** đọc tuần tự, entry theo entry:

```java
try (ZipInputStream in = new ZipInputStream(
        Files.newInputStream(archive), StandardCharsets.UTF_8)) {
    ZipEntry entry;
    while ((entry = in.getNextEntry()) != null) {
        System.out.println(entry.getName());
        byte[] buf = new byte[8192];
        long expanded = 0;
        for (int n; (n = in.read(buf)) != -1; ) {
            expanded += n;
            if (expanded > MAX_UNCOMPRESSED_BYTES) {
                throw new IOException("Entry quá lớn");
            }
            // Xử lý buf[0..n) với sink theo yêu cầu.
        }
        in.closeEntry();
    }
}
```

Đây là **snippet**, cần định nghĩa hằng `MAX_UNCOMPRESSED_BYTES` theo quota và import phù hợp. `ZipInputStream` đọc local file headers; một số metadata (đặc biệt các trường chỉ có trong central directory) có thể chưa biết trước khi đọc xong entry hoặc không được cung cấp giống `ZipFile`. Vì thế không mặc định `getComment()` hay `getSize()` luôn có giá trị cho mỗi entry khi đang streaming.

Nếu cần comment của archive và metadata đầy đủ từ central directory, thường chọn `ZipFile`. Với file được nhận từ một stream không seekable, `ZipInputStream` có thể phù hợp nhưng ứng dụng vẫn phải thiết kế xác thực, quota, xử lý lỗi và chính sách EOF.

## 9. DEFLATED so với STORED và các trường phải khai báo

`ZipEntry` có hai method phổ biến được API Java tiêu chuẩn hỗ trợ:

- **`ZipEntry.DEFLATED`**: dữ liệu được nén DEFLATE. Với `ZipOutputStream`, JDK có thể tính CRC/kích thước khi hoàn tất entry nếu chưa khai báo trước; writer thường không cần biết mọi thông tin từ đầu.
- **`ZipEntry.STORED`**: không nén nội dung. Khi dùng `ZipOutputStream` với STORED, trước `putNextEntry` phải khai báo đầy đủ `size`, `compressedSize` và `CRC-32` theo yêu cầu API; cả hai kích thước bằng nhau.

Ví dụ STORED cho một mảng byte đã có:

```java
byte[] data = "Hello".getBytes(StandardCharsets.UTF_8);
CRC32 checksum = new CRC32();
checksum.update(data);

ZipEntry entry = new ZipEntry("hello.txt");
entry.setMethod(ZipEntry.STORED);
entry.setSize(data.length);
entry.setCompressedSize(data.length);
entry.setCrc(checksum.getValue());

out.putNextEntry(entry);
out.write(data);
out.closeEntry();
```

Đây là **snippet**: `out` phải là `ZipOutputStream` đang mở và có import `ZipEntry`, `CRC32`, `StandardCharsets`. Với STORED, cung cấp sai size/CRC hoặc ghi sai số byte có thể khiến thư viện ném `ZipException`. Với DEFLATED, size/compressed size phụ thuộc mức nén và nội dung; không suy kích thước nén từ số ký tự.

Đừng tự tin rằng mọi entry đọc từ nguồn ngoài đều dùng DEFLATED/STORED mà bỏ qua `getMethod()`. ZIP có nhiều extension và method khác; Java `java.util.zip` không phải thư viện hỗ trợ mọi biến thể ZIP (ví dụ nhiều định dạng mã hóa hoặc method nén mở rộng).

## 10. Extra fields: khi nào cần và vì sao phải parse cẩn thận?

ZIP extra fields là những vùng byte mở rộng trong header/central directory. Mỗi mục thường có header ID, kích thước dữ liệu và payload riêng. Ví dụ các nhóm thường gặp:

| ID minh họa | Ý nghĩa phổ biến | Lưu ý |
| --- | --- | --- |
| `0x5455` | Extended timestamp (thường gọi UT). | Có thể chứa modification/access/creation theo độ dài và vị trí. |
| `0x000A` | NTFS extra field. | Có thể chứa timestamp dạng Windows FILETIME. |
| `0x0001` | ZIP64 extended information. | Dùng để biểu diễn size/offset vượt giới hạn header cổ điển. |
| `0x7075` | Unicode Path Extra Field. | Bổ sung tên Unicode, có version và CRC kiểm tra tên gốc. |
| `0x6375` | Unicode Comment Extra Field. | Bổ sung comment Unicode, không đồng nghĩa mọi reader sẽ tự dùng. |

Ví dụ truy xuất raw extras:

```java
byte[] extra = entry.getExtra();
if (extra != null) {
    System.out.println("extra bytes=" + extra.length);
}
```

Đây là snippet; `entry` là `ZipEntry`. **Không giả định** mỗi extra field luôn có độ dài mong đợi. Khi cần tự parse:

1. Giới hạn tổng số byte extra theo cấu trúc ZIP.
2. Với mỗi field, kiểm tra còn ít nhất 4 byte header (ID và data length).
3. Đọc số nguyên header theo thứ tự byte **little-endian** của ZIP.
4. Trước khi đọc payload, kiểm tra data length không vượt vùng còn lại.
5. Chấp nhận các field lạ bằng cách bỏ qua có giới hạn; không cast hoặc cấp phát bộ nhớ không kiểm soát.
6. Đối với Unicode extras, kiểm tra version, CRC và quan hệ với tên gốc trước khi tin dữ liệu đó.
7. Không mặc định `ZipEntry.getExtra()` phản ánh y nguyên toàn bộ dữ liệu raw ban đầu sau khi JVM đã diễn giải các timestamp/field đặc biệt.

`setExtra(byte[])` dành cho metadata nhị phân có cấu trúc cụ thể; nó không tự biến bytes bất kỳ thành những thuộc tính được ZIP và mọi công cụ hiểu đúng. Nếu cần bảo tồn đầy đủ các external attributes/permission/ownership đặc thù của ZIP, có thể phải dùng công cụ/libraries chuyên dụng và kiểm tra khả năng tương thích ngoài `java.util.zip`.

## 11. Comment và encoding: các lỗi thường xuất hiện

Những lỗi điển hình:

- Ghi tên UTF-8 nhưng bên đọc legacy cố decode bằng code page khác ⇒ chữ có dấu biến thành chuỗi rác hoặc lỗi.
- Ghi entry name có ký tự không biểu diễn được trong charset legacy ⇒ writer có thể ném exception hoặc không tạo được archive như dự kiến.
- Nhầm `ZipEntry.comment` với `ZipOutputStream.setComment` ⇒ chỉ đọc archive comment không thấy chú thích riêng của entry.
- Cho rằng `ZipInputStream.getNextEntry()` cung cấp đủ comment metadata của central directory ⇒ có thể nhận `null`.
- Ghi comment vượt 65.535 **byte mã hóa** ⇒ writer từ chối hoặc archive không hợp lệ.
- Lưu tên tiếng Việt nhưng quên rằng một số phần mềm archive cũ không có cơ chế xác định encoding phù hợp.
- Tạo file ZIP bằng một thư viện rồi đọc lại bằng một thư viện khác; Unicode extras, bit flag, timestamp và external attributes không thống nhất ⇒ kết quả khác kỳ vọng.

Để có khả năng liên thông tốt, kiểm thử **cả hai chiều**: Java tạo ZIP → công cụ đích đọc; công cụ đích tạo ZIP → Java đọc. Bộ fixture nên gồm tên ASCII, tiếng Việt có dấu, ký tự bổ sung (emoji nếu được hỗ trợ), tên dài, comment có dấu, timestamp, thư mục rỗng và entries không có directory entry cha.

## 12. Giải nén an toàn: tên entry không phải đường dẫn đáng tin cậy

Một archive do người ngoài cung cấp có thể chứa tên như:

```text
../outside.txt
/absolute/path.txt
C:/Windows/target.txt
safe/../../escape.txt
folder\..\escape.txt
```

**Không được** nối trực tiếp tên entry với root rồi ghi file mà không kiểm tra. Hành vi ánh xạ tên ZIP sang đường dẫn thực phụ thuộc OS và chính sách thư mục.

Một chiến lược cơ bản:

1. Định nghĩa một thư mục đích cố định, được kiểm soát, ví dụ `targetRoot.toAbsolutePath().normalize()`.
2. Chỉ chấp nhận entry name theo định dạng tương đối với dấu `/`; từ chối absolute path, drive prefix, dấu `\` nếu không có lý do hỗ trợ và những thành phần `..`.
3. Dùng `Path.resolve(name).normalize()` và xác minh đường dẫn kết quả vẫn nằm dưới root **bằng `startsWith(Path)` trên các Path đã normalize**, không so prefix của string.
4. Trước khi ghi, bảo vệ khỏi symlink đã có trong đường dẫn đích; `normalize()` là kiểm tra **cú pháp**, không loại bỏ race symlink/TOCTOU.
5. Dùng thư mục giải nén riêng, permission phù hợp, chính sách ghi đè rõ ràng; khi có nguồn không tin cậy cần giới hạn tài nguyên và cân nhắc API an toàn hơn (ví dụ cơ chế resolve tương đối có kiểm soát / `SecureDirectoryStream` nếu provider hỗ trợ).
6. Từ chối tên trùng hoặc trùng sau normalization/case-fold trên filesystem mục tiêu, tránh ghi đè file không mong muốn.

Ví dụ minh họa:

```java
// Snippet: trustedRoot là Path được ứng dụng kiểm soát.
Path root = trustedRoot.toAbsolutePath().normalize();
String rawName = entry.getName();

if (rawName.startsWith("/") || rawName.contains("\\") ||
        rawName.contains("..") || rawName.indexOf('\0') >= 0) {
    throw new IOException("Tên entry không được phép");
}

Path destination = root.resolve(rawName).normalize();
if (!destination.startsWith(root) || destination.equals(root)) {
    throw new IOException("Entry nằm ngoài thư mục đích");
}
```

Đây là **snippet phòng thủ sơ cấp** và có chủ đích từ chối rộng (ví dụ chuỗi chứa `..` trong một tên file bình thường cũng có thể bị từ chối). Nó không tự ngăn mọi symlink/TOCTOU khi filesystem bị thay đổi đồng thời; trước khi dùng production, cần chính sách phù hợp platform/provider và thư mục đích do ứng dụng sở hữu. Đừng coi ví dụ này là bộ giải nén hoàn chỉnh.

## 13. ZIP bomb, CRC và metadata không đáng tin

Ngay cả khi entry không có path traversal, archive có thể có **compressed size nhỏ nhưng uncompressed size rất lớn**. Một file vài KB có thể nở thành nhiều GB khi giải nén. Ngoài ra còn có archive nhiều entry, entry lồng archive khác, tên rất dài và cấu trúc hỏng.

Các giới hạn nên định nghĩa:

| Rủi ro | Biện pháp |
| --- | --- |
| Entry không nén quá lớn | Đặt quota bytes sau giải nén **trong lúc streaming**. |
| Tổng bytes toàn ZIP vượt hạn mức | Đếm tổng bytes cho mọi entry và dừng khi vượt quota. |
| Quá nhiều entry | Giới hạn số entry và độ sâu thư mục. |
| Tỷ lệ nén bất thường | Đặt ngưỡng/rủi ro compression ratio, nhưng **không chỉ dựa một ratio**. |
| Thời gian giải nén quá lâu | Deadline/quota CPU, xử lý có giới hạn, không block dịch vụ. |
| Entry hỏng hoặc CRC sai | Kiểm tra, hủy toàn bộ dữ liệu đầu ra chưa được commit và ghi log hợp lý. |
| Extra fields/metadata kỳ lạ | Validate độ dài và bỏ qua field không hỗ trợ theo chính sách. |
| File trùng tên hoặc ghi đè | Kiểm tra tập tên đã normalize và dùng chế độ tạo file an toàn. |

**CRC-32 không phải cơ chế xác thực nguồn gốc.** Nó giúp phát hiện lỗi dữ liệu thông thường, nhưng người tạo archive có thể chỉnh payload và tính lại CRC. Khi cần tin cậy nội dung, dùng chữ ký số/hàm băm mật mã gắn với manifest do nguồn đáng tin xác thực, hoặc cơ chế bảo vệ riêng của hệ thống. ZIP cũng không cung cấp giao ước rằng timestamp/comment/file name đáng tin để ra quyết định bảo mật.

Nếu `entry.getSize()` là `-1` hoặc bị giả mạo, không được cấp phát theo size và tin rằng mọi thứ an toàn; quota phải dựa trên **số byte thực tế** đang đi qua stream.

## 14. Cách giữ metadata khi sao chép hoặc repack ZIP

Khi đọc archive đầu vào rồi tạo archive khác, ứng dụng có thể muốn giữ:

- Tên entry đã được decode chính xác.
- Quan hệ thư mục và dấu `/` khi là directory entry.
- Comment riêng của entry và comment archive.
- Modification/access/creation times nếu có.
- Compression method, size, CRC và extra fields khi tương thích.
- Permission/owner/external attributes nếu nghiệp vụ yêu cầu, thường cần giải pháp chuyên dụng vì API tiêu chuẩn không bao quát mọi trường.

Tuy nhiên **không nên copy tất cả metadata bằng cách gọi setter vô điều kiện**:

1. Nếu tên đã decode sai do charset, ghi lại bằng UTF-8 sẽ “đóng băng” tên sai.
2. Đừng ép `STORED` nếu chưa có size/CRC tương thích.
3. ZipEntry của một archive đã nén có thể có size/compressed size/CRC không còn phù hợp nếu payload được thay đổi hoặc đổi compression level.
4. Extra fields có thể chứa dữ liệu gắn với layout cũ, offsets, tên file hoặc timestamp; copy raw bytes mù quáng có thể tạo metadata bất nhất.
5. Metadata không hợp lệ cần được từ chối/làm sạch với chính sách rõ. Giữ external symlink/permission ngoài ý muốn có thể gây rủi ro khi extract.

Với archive chuyển đổi sang chuẩn UTF-8, hãy **giải mã tên cũ đúng trước, chuẩn hóa quy tắc nội bộ, rồi ghi lại ZIP UTF-8 mới**. Với yêu cầu bảo tồn bit-identical archives, quá trình unpack/repack bằng API mức cao không phù hợp; cần copy bytes gốc hoặc công cụ biết bảo toàn cấu trúc.

## 15. Chọn API và quy ước khi thiết kế hệ thống

**Tạo ZIP mới dùng cho nhiều nền tảng:**

- Ưu tiên `StandardCharsets.UTF_8` và `ZipOutputStream`.
- Đặt tên entry dùng dấu `/`, không dùng đường dẫn tuyệt đối của hệ điều hành.
- Chỉ thêm metadata cần thiết, đồng thời mô tả rõ yêu cầu bên nhận.
- Đóng từng entry bằng `closeEntry()` và đóng `ZipOutputStream` qua `try-with-resources` để central directory được ghi hoàn chỉnh.
- Đặt giới hạn input/entry count/size; chuẩn hóa trùng tên và loại trừ các path không an toàn.

**Đọc ZIP legacy:**

- Dùng `ZipFile(file, charset)` nếu cần đầy đủ central directory và truy cập theo tên.
- Tôn trọng UTF-8 bit flag và chọn charset legacy đúng từ hợp đồng nguồn khi flag không bật.
- Kiểm tra tên, charset, comments, timestamps và method qua fixture thực tế.
- Xem metadata là tùy chọn/không tin cậy; không đưa raw entry name thẳng tới filesystem.

**Đọc ZIP streaming:**

- Dùng `ZipInputStream(input, charset)` khi cần xử lý tuần tự.
- Không dựa vào `getSize()`/comment luôn sẵn ngay tại `getNextEntry()`; thống kê bytes giải nén thực tế.
- Đảm bảo đọc hết/đóng entry và xử lý lỗi/EOF đúng.
- Cần entry count, quota toàn ZIP và giới hạn thời gian; không tạo một mảng kích thước theo metadata không tin cậy.

**Archive yêu cầu metadata giàu hơn chuẩn ZIP cơ bản:** cân nhắc library hỗ trợ external attributes, symlinks, encryption, ZIP64/Unicode extras theo nhu cầu cụ thể. Không cố làm `java.util.zip` hỗ trợ chức năng không được API cam kết.

## 16. Lỗi thường gặp và cách xử lý

| Hiện tượng | Nguyên nhân có thể | Cách xử lý |
| --- | --- | --- |
| Tên entry tiếng Việt biến thành ký tự lạ | Giải mã sai charset khi ZIP legacy không bật bit UTF-8. | Biết charset nguồn, truyền rõ vào `ZipFile/ZipInputStream`. |
| Reader vẫn dùng UTF-8 dù truyền IBM437 | Entry có UTF-8/EFS flag. | Tuân thủ flag; không ép decode legacy khi flag chỉ định UTF-8. |
| Không tạo được ZIP với tên Unicode | Charset được chọn không biểu diễn được tên/comment. | Chọn UTF-8 cho ZIP mới. |
| Payload text đọc sai dù tên đúng | Dùng charset tên entry để giải mã nội dung. | Tách charset header và charset payload; xác định encoding file bên trong. |
| `getComment()` trả null | Không có comment hoặc đang đọc từ nguồn thiếu metadata central directory. | Coi comment tùy chọn, dùng `ZipFile` khi cần. |
| `getSize()` trả -1 | Metadata chưa biết trong bối cảnh streaming. | Đếm bytes thực tế và giới hạn quota. |
| CRC mismatch / `ZipException` | File nén hỏng, thiếu bytes hoặc metadata không khớp. | Từ chối dữ liệu, không commit output dở dang. |
| Timestamp sai lệch | DOS time, múi giờ, độ phân giải hoặc extra fields khác nhau. | Thống nhất time policy, dùng Instant/FileTime, test roundtrip. |
| Extra fields không nhận biết | Metadata extension của công cụ ZIP khác. | Parse theo ID/length có giới hạn hoặc dùng thư viện phù hợp. |
| Giải nén thoát khỏi thư mục đích | Entry name có `..`/absolute path/symlink. | Validate path, root-boundary và bảo vệ khỏi TOCTOU. |
| Disk đầy khi extract | Zip bomb hoặc không có quota. | Giới hạn bytes giải nén thực và tổng dung lượng. |
| Không thấy thư mục rỗng | ZIP chỉ ghi file hoặc thiếu directory entry. | Ghi explicit directory entry khi cần bảo toàn thư mục rỗng. |
| ZIP mở được trong Java nhưng không ở công cụ khác | Hỗ trợ method, charset, extras hoặc ZIP64 khác nhau. | Xác minh compatibility trên tool/platform đích. |

## 17. Bài tập thực hành

**Bài 1 — Charset của tên và payload.** Đổi tên entry UTF-8 thành `"ảnh/đồ-thị.txt"` và payload thành `"Đồ thị mẫu"`. Kiểm tra việc đổi charset của payload **không** làm đổi cách decode tên entry; nêu nơi mỗi charset được thiết lập.

**Bài 2 — ZIP IBM437.** Với entry `"café.txt"`, đọc bằng charset `IBM437` và so sánh với trường hợp dùng constructor UTF-8. Dự đoán việc đọc sai có thể báo lỗi hoặc tên không đúng. Không dùng tên có dấu tiếng Việt không tồn tại trong IBM437 để thử ghi legacy mà không xử lý exception.

**Bài 3 — Metadata STORED.** Dùng `CRC32` và `ZipEntry.STORED` để thêm entry `"raw.bin"` chứa 10 byte. Tạo đúng size, compressed size, CRC trước `putNextEntry`; thử khai báo CRC sai để quan sát lỗi và giải thích vì sao CRC chưa phải chữ ký số.

**Bài 4 — Timestamp/extra.** Tạo entry có thời gian sửa đổi cố định, đọc lại bằng `ZipFile` và thống kê `getTime()`/`getLastModifiedTime()`/`getExtra()`. So sánh trên hai chương trình ZIP khác nhau để tìm khác biệt về độ chính xác và extra fields.

**Bài 5 — Directory entries.** Tạo ZIP chỉ có `"empty/"` và `"docs/info.txt"`. Kiểm tra `isDirectory()`, nội dung và cách giải nén giữ thư mục rỗng. Vì sao `"docs/info.txt"` không đòi archive phải có entry riêng `"docs/"`?

**Bài 6 — Giải nén an toàn.** Xây một hàm `safeExtract` có kiểm tra absolute path, thành phần `..`, symlink, trùng tên và giới hạn 100 MiB uncompressed tổng. Dùng archive thử nghiệm có `"../escape.txt"` để kiểm tra bị từ chối mà không ghi bất kỳ file ngoài root.

**Bài 7 — ZIP bomb.** Mô phỏng entry nhỏ khi nén nhưng có nhiều byte lặp khi giải nén. Đếm bytes thực tế trong vòng đọc, dừng theo quota dù `entry.getSize()` thiếu hoặc sai. Chỉ thử trong thư mục tạm có giới hạn dung lượng, không dùng dữ liệu production.

**Bài 8 — Chuyển encoding legacy sang UTF-8.** Nhận file ZIP có tên IBM437, giải mã đúng, tạo ZIP mới UTF-8 với tên Unicode và comment phù hợp. Đo khả năng đọc trên các công cụ mục tiêu và kiểm tra metadata đã được giữ/chuyển đổi theo chính sách.

### Câu hỏi tự kiểm tra

1. Charset nào điều khiển tên entry và charset nào điều khiển dữ liệu trong entry?
2. EFS/UTF-8 bit 11 cho bên đọc biết điều gì?
3. Vì sao truyền charset IBM437 không nên override tên đã được đánh dấu UTF-8?
4. ZIP entry comment khác archive comment ở đâu?
5. Khi `ZipInputStream.getNextEntry()` trả một entry, mọi size/CRC/comment đã chắc chắn sẵn sàng chưa?
6. Khi nào cần đặt trước size/compressed size/CRC cho STORED?
7. `FileTime` và các extra fields có bảo toàn timestamp nanosecond trên mọi OS không?
8. CRC-32 có chống được người tấn công chỉnh sửa archive rồi tính lại CRC không?
9. Vì sao `Path.normalize()` chưa đủ để chống mọi symlink race khi giải nén?
10. Nên dùng `ZipFile` hay `ZipInputStream` khi cần archive comment và tra cứu entry nhanh?

## 18. Tài liệu tham khảo

- [Java SE 21 — ZipEntry](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/zip/ZipEntry.html): name, comment, time, method, size, CRC và extra.
- [Java SE 21 — ZipFile](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/zip/ZipFile.html): mở ZIP với charset, tra cứu entry và archive comment.
- [Java SE 21 — ZipInputStream](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/zip/ZipInputStream.html): đọc tuần tự entry từ InputStream.
- [Java SE 21 — ZipOutputStream](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/zip/ZipOutputStream.html): ghi entry, charset, comments và hoàn tất central directory.
- [Java SE 21 — Charset](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/charset/Charset.html): lựa chọn và xử lý mã hóa ký tự.
- [PKWARE APPNOTE — ZIP format specification](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT): trường flag, metadata và extra field conventions của ZIP.

**Ghi nhớ:** Tên entry/comment là metadata của ZIP và có charset/flags riêng; payload là bytes độc lập. Chọn UTF-8 cho archive mới, đọc archive legacy bằng charset được xác minh, hiểu rằng timestamp/extra/size có thể thiếu hoặc khác nhau giữa công cụ, và luôn kiểm soát tên đường dẫn, quota và độ tin cậy trước khi giải nén.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="compression-and-archives-008">JarFile, JarInputStream và manifest ở mức I/O</a>

<details>
<summary>Xem chi tiết</summary>

# `JarFile`, `JarInputStream` và `Manifest` ở mức Java I/O

## 1. Mục tiêu: đọc một tệp JAR như một kho ZIP có metadata

JAR (*Java ARchive*) là một dạng archive dựa trên ZIP: nó chứa nhiều entry có tên như `com/example/App.class`, `config/settings.properties`, `docs/readme.txt` và có thể chứa file metadata `META-INF/MANIFEST.MF`. Khi thao tác ở **mức I/O**, chương trình không nhất thiết phải tải class hoặc thực thi JAR; nó có thể chỉ cần liệt kê entry, lấy tài nguyên, đọc manifest, kiểm tra thông tin về entry và đóng stream đúng cách.

Hai lớp thường gặp:

- **`java.util.jar.JarFile`**: mở một **tệp JAR đã có trên filesystem**, tra cứu entry theo tên, liệt kê các entry và mở `InputStream` cho từng entry; extends `java.util.zip.ZipFile`.
- **`java.util.jar.JarInputStream`**: đọc một JAR **tuần tự từ `InputStream`**; extends `java.util.zip.ZipInputStream`. Phù hợp khi nguồn là dữ liệu truyền qua stream mà không có tệp để truy cập tùy ý.
- **`java.util.jar.Manifest`**: mô hình dữ liệu cho `META-INF/MANIFEST.MF`, gồm **main attributes** và **per-entry attributes**. Đây là metadata về archive và từng entry, không phải nội dung của mọi file bên trong JAR.

Bài tập trung vào các API Java SE 17+ ở tầng I/O: cách đọc đúng, vị trí manifest, vòng đời entry/stream, so sánh hiệu năng và các giới hạn bảo mật. Những khái niệm về class loading, launcher và chữ ký JAR chỉ được trình bày đủ để tránh suy diễn sai từ dữ liệu I/O.

## 2. Một JAR trông như thế nào?

Một archive có thể chứa:

```text
sample.jar
├── META-INF/
│   └── MANIFEST.MF
├── com/example/Main.class
├── docs/
│   └── readme.txt
└── config/
    └── app.properties
```

Các “thư mục” trong JAR thường là entry có tên kết thúc bằng dấu `/` (ví dụ `docs/`). Không bắt buộc mọi đường dẫn cha đều có một directory entry riêng; có thể tồn tại `docs/readme.txt` mà không có entry `docs/`.

Ví dụ nội dung manifest:

```text
Manifest-Version: 1.0
Main-Class: com.example.Main
Created-By: Example

Name: docs/readme.txt
X-Role: documentation

```

Ý nghĩa:

- `Manifest-Version`: thuộc tính phiên bản định dạng manifest.
- `Main-Class`: tên lớp chính dùng trong trường hợp JAR được launcher chạy theo cơ chế có hỗ trợ; giá trị là tên lớp theo cú pháp Java, không phải tên `.class` trong ZIP.
- `Created-By`: ví dụ thuộc tính metadata do công cụ tạo JAR cung cấp.
- `Name: docs/readme.txt` bắt đầu **một section theo entry**; thuộc tính `X-Role` bên dưới thuộc section của file đó, không thuộc main attributes.

Dòng trắng ngăn cách các section. Định dạng manifest có các quy tắc riêng về kết thúc dòng, mã hóa và xuống dòng tiếp nối những giá trị dài; để tạo manifest hợp lệ, nên dùng lớp `Manifest` thay vì nối chuỗi tùy ý. Tên entry trong manifest được dùng để liên hệ metadata với entry tương ứng; **việc tồn tại một section không tự chứng minh archive chứa entry ấy**, và một entry bình thường có thể không có section metadata riêng.

## 3. So sánh `JarFile` và `JarInputStream`

| Tiêu chí | `JarFile` | `JarInputStream` |
| --- | --- | --- |
| Cơ sở API | extends `ZipFile` | extends `ZipInputStream` |
| Đầu vào | File/JAR có thể mở dưới dạng file | Bất kỳ `InputStream` phù hợp |
| Kiểu duyệt | Tra cứu entry theo tên, liệt kê các entry | Đọc lần lượt bằng `getNextJarEntry()` |
| Lấy manifest | `getManifest()`, có thể tìm manifest trong archive | `getManifest()` với **điều kiện vị trí manifest đầu stream** |
| Đọc nội dung entry | `jar.getInputStream(entry)` | Đọc `jarInput.read(...)` trên entry hiện tại |
| Chuyển tới entry khác | Tra cứu entry mới độc lập | `getNextJarEntry()` dịch sang entry kế tiếp |
| Nguồn mạng/pipe chưa lưu file | Cần có bản JAR dạng file phù hợp trước | Có thể dùng trực tiếp stream |
| Quản lý tài nguyên | Đóng `JarFile` và các entry streams | Đóng `JarInputStream` bao bọc nguồn |
| Có thể truy cập entry thứ hai trước entry đầu? | Có thể, qua `getJarEntry(name)` | Không; dữ liệu được đọc tuần tự |

**Đừng nhầm chữ “random access” với tùy ý sửa bytes.** `JarFile` hỗ trợ *tra cứu và mở stream theo tên entry* mà không phải đọc lần lượt toàn bộ entry trước đó, nhưng đây vẫn là API **đọc archive**, không phải `RandomAccessFile` cho phép thay đổi tùy ý byte của ZIP. Khi cần tạo JAR hoặc thay nội dung, dùng `JarOutputStream`/công cụ đóng gói và tạo lại archive theo kế hoạch phù hợp.

Một JAR có thể có entry nén theo `DEFLATED` hoặc không nén theo `STORED`. Khi đọc entry, API trả ra **byte nội dung sau giải nén**, chứ không phải chuỗi byte nén trong archive.

## 4. `JarFile`: tra cứu và đọc entry bằng tên

API thường gặp:

```java
try (JarFile jar = new JarFile(jarPath.toFile())) {
    Manifest manifest = jar.getManifest(); // có thể null
    JarEntry entry = jar.getJarEntry("docs/readme.txt");

    if (entry != null && !entry.isDirectory()) {
        try (InputStream in = jar.getInputStream(entry)) {
            byte[] bytes = in.readAllBytes();
            // Chỉ dùng readAllBytes() nếu đã giới hạn kích thước dữ liệu!
        }
    }
}
```

Đây là snippet, **không phải chương trình độc lập**; cần `jarPath` có giá trị `Path` đúng, import `JarFile`, `JarEntry`, `Manifest`, `InputStream`, và xử lý `IOException`.

### `getJarEntry(name)` và `getEntry(name)` khác gì?

- `JarFile.getJarEntry(String name)` trả `JarEntry` hoặc `null` nếu không tìm thấy.
- `JarFile.getEntry(String name)` trả kiểu `ZipEntry` hoặc `null`; `JarEntry` là một dạng entry mở rộng của `ZipEntry`.
- `jar.entries()` trả `Enumeration<JarEntry>` để duyệt các entry. Có thể dùng `jar.stream()` để nhận stream entry theo hỗ trợ của `ZipFile`.
- `jar.getInputStream(entry)` mở một luồng để **đọc nội dung của entry đã được xác định**.

Tránh nhầm `JarFile.getInputStream(entry)` với việc đọc từ một `JarInputStream` đang đứng tại entry hiện tại. Với `JarFile`, bạn có thể chọn `docs/readme.txt` trước, rồi chọn `config/app.properties`, không phụ thuộc trình tự vật lý của ZIP. Với `JarInputStream`, muốn tới entry ở gần cuối phải đi qua entry trước theo thứ tự stream.

### Đọc theo chunk, không nạp vô hạn vào RAM

`InputStream.readAllBytes()` trong snippet thích hợp với file nhỏ có giới hạn do ứng dụng kiểm soát. Với JAR từ nguồn không tin cậy hoặc resource có thể rất lớn:

```java
byte[] block = new byte[8192];
long total = 0;
int n;
while ((n = in.read(block)) != -1) {
    total += n;
    if (total > MAX_ALLOWED_BYTES) {
        throw new IOException("Entry too large");
    }
    // Gửi block[0..n) đến bộ xử lý theo stream.
}
```

Đây là snippet yêu cầu `in`, `MAX_ALLOWED_BYTES` và hàm xử lý do chương trình định nghĩa. Trong code thật nên hạn chế thêm tốc độ, số entry, tổng byte giải nén và thời gian xử lý; tránh overflow số học bằng cách kiểm tra giới hạn trước khi cộng khi cần.

## 5. `JarInputStream`: duyệt tuần tự và đọc entry hiện tại

Tạo từ một input stream:

```java
try (JarInputStream jis =
        new JarInputStream(Files.newInputStream(jarPath))) {

    Manifest manifest = jis.getManifest();

    JarEntry entry;
    while ((entry = jis.getNextJarEntry()) != null) {
        System.out.println("entry=" + entry.getName());

        if (!entry.isDirectory()) {
            byte[] bytes = jis.readAllBytes();
            // Chỉ dùng cách này khi file có kích thước nhỏ/giới hạn rõ.
        }

        jis.closeEntry();
    }
}
```

Snippet cần các import và xử lý `IOException`. `getNextJarEntry()` di chuyển tới đầu entry tiếp theo và trả `null` khi hết archive. Trong khi đang ở entry, `jis.read(...)` đọc **byte giải nén của entry ấy**. Khi tới cuối entry, `read` trả `-1` cho entry hiện tại; điều đó không có nghĩa toàn bộ JAR đã hết — gọi `getNextJarEntry()` để lấy entry kế tiếp.

**Không được** tạo một `new JarInputStream` cho mỗi entry trong cùng input stream và mong quay lại đầu JAR; stream đã tiến về phía trước. Nếu cần đọc lại, nguồn phải có cách tái tạo stream (hoặc ghi ra file rồi dùng `JarFile`).

### `closeEntry()` có tác dụng gì?

`closeEntry()` kết thúc việc đọc entry hiện tại, bỏ qua những byte nội dung entry còn chưa tiêu thụ nếu có và chuẩn bị chuyển tiếp. `getNextJarEntry()` cũng xử lý việc tiến đến entry kế tiếp theo hợp đồng của ZIP stream. Trong vòng xử lý có kiểm chứng chữ ký hoặc integrity, **đọc hết entry** giúp cho verification/integrity checks có cơ hội hoàn tất; không giả định chỉ đọc header là đủ để kiểm tra toàn bộ nội dung.

### `JarInputStream` không tự tạo `InputStream` độc lập cho từng entry

`JarInputStream` **chính là** luồng đang đọc entry hiện tại. Bạn không gọi `jis.getInputStream(entry)` như `JarFile`. Nếu muốn tách riêng nội dung từng entry, phải copy các byte của entry sang đích hoặc xử lý ngay trong lúc đang đứng ở entry đó.

## 6. `Manifest`: main attributes và per-entry attributes

Các API cần nhớ:

| API | Trả về / ý nghĩa |
| --- | --- |
| `jar.getManifest()` | `Manifest` hoặc `null` nếu archive không có manifest |
| `jis.getManifest()` | `Manifest` khi manifest được nhận ở vị trí đầu hợp lệ, hoặc `null` |
| `manifest.getMainAttributes()` | `Attributes` của phần main |
| `manifest.getEntries()` | `Map<String, Attributes>` chứa các section theo tên entry |
| `manifest.getAttributes("docs/readme.txt")` | `Attributes` của section chỉ định, hoặc `null` |
| `attrs.getValue(Attributes.Name.MAIN_CLASS)` | Giá trị attribute được định danh bằng `Attributes.Name`, hoặc `null` |
| `attrs.getValue("X-Role")` | Giá trị attribute theo tên chuỗi, hoặc `null` |

Ví dụ:

```java
Manifest manifest = jar.getManifest();
if (manifest != null) {
    Attributes main = manifest.getMainAttributes();

    String version = main.getValue(Attributes.Name.MANIFEST_VERSION);
    String mainClass = main.getValue(Attributes.Name.MAIN_CLASS);

    Attributes docs = manifest.getAttributes("docs/readme.txt");
    String role = docs == null ? null : docs.getValue("X-Role");
}
```

Snippet giả định `jar` là `JarFile` hợp lệ. **`Manifest.getEntries()` không liệt kê tất cả file trong JAR**: nó chỉ liệt kê các section có metadata trong manifest. Muốn liệt kê archive entries, dùng `JarFile.entries()` hoặc `JarInputStream.getNextJarEntry()`.

Tương tự, **`Main-Class` là metadata**, không phải bằng chứng rằng lớp được chỉ định thực sự tồn tại, có hàm `public static void main(String[])` đúng, hoặc JAR sẽ chạy thành công với `java -jar`. Muốn xác minh cần kiểm tra archive và quá trình launch theo yêu cầu.

Các giá trị tên section thường sử dụng dạng tên entry ZIP với dấu `/`, không phải đường dẫn tuyệt đối của máy. Đừng chuyển thẳng tên entry vào `Path` ngoài archive mà không kiểm soát mục tiêu.

## 7. Điểm khác biệt quan trọng: vị trí manifest đối với `JarInputStream`

Không phải mọi file có tên `META-INF/MANIFEST.MF` ở bất kỳ chỗ nào trong archive đều được `JarInputStream.getManifest()` nhận tự động.

Theo hợp đồng API Java 17:

- `JarInputStream.getManifest()` trả manifest nếu `META-INF/MANIFEST.MF` là **entry đầu tiên** trong luồng ZIP/JAR.
- Hoặc nếu entry đầu tiên là thư mục `META-INF/` và **entry thứ hai** là `META-INF/MANIFEST.MF`.
- Nếu manifest nằm ở vị trí khác, `getManifest()` **không bảo đảm tìm/đọc nó** như manifest đầu luồng. Hành vi có bỏ qua nó ở những vị trí muộn hơn hay không cũng không nên dựa vào.

Khi manifest ở đầu hợp lệ, `getNextJarEntry()` / `getNextEntry()` **bỏ qua entry manifest** đã được constructor xử lý; nếu entry đầu là `META-INF/` rồi manifest, cả hai entry đó được bỏ qua trong quá trình duyệt bình thường.

Sơ đồ:

```text
JAR A:
  META-INF/
  META-INF/MANIFEST.MF
  docs/readme.txt
             ^
  getManifest() đọc metadata;
  getNextJarEntry() bắt đầu bằng docs/readme.txt

JAR B:
  docs/readme.txt
  META-INF/MANIFEST.MF
             ^
  Không giả định getManifest() sẽ đọc manifest xuất hiện muộn!
```

Trái lại, `JarFile` đọc cấu trúc archive như một file ZIP và có thể truy cập entry manifest theo tên; `getManifest()` không phụ thuộc việc các entry nằm ở đầu stream như `JarInputStream`.

**Cách tạo JAR đúng thứ tự:** dùng `new JarOutputStream(output, manifest)`. Constructor này hỗ trợ tạo entry manifest sớm theo hợp đồng API, giúp `JarInputStream` nhận `Manifest` trong ví dụ học tập. Khi tạo JAR bằng công cụ ngoài, cần kiểm tra quy cách đóng gói nếu logic xử lý stream yêu cầu manifest đầu luồng.

## 8. Ví dụ Java 17+ hoàn chỉnh: tạo rồi đọc JAR bằng cả hai API

Chương trình sau tạo JAR tạm từ Java, bổ sung manifest và hai file nội dung nhỏ. Sau đó nó đọc cùng archive theo hai cách để thể hiện **tra cứu entry theo tên** với `JarFile` và **duyệt tuần tự** với `JarInputStream`.

Lưu thành **`JarFileVsInputStreamDemo.java`**:

```java
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class JarFileVsInputStreamDemo {
    private static void addFile(
            JarOutputStream out, String name, String content)
            throws IOException {
        JarEntry entry = new JarEntry(name);
        out.putNextEntry(entry);
        out.write(content.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }

    public static void main(String[] args) throws Exception {
        Path path = Files.createTempFile("jar-io-demo-", ".jar");

        try {
            Manifest manifest = new Manifest();
            Attributes main = manifest.getMainAttributes();
            main.put(Attributes.Name.MANIFEST_VERSION, "1.0");
            main.put(Attributes.Name.MAIN_CLASS, "example.Main");

            Attributes document = new Attributes();
            document.putValue("X-Role", "documentation");
            manifest.getEntries().put("docs/readme.txt", document);

            try (JarOutputStream out = new JarOutputStream(
                    Files.newOutputStream(path), manifest)) {
                addFile(out, "docs/readme.txt", "HELLO");
                addFile(out, "config/app.properties", "mode=demo\n");
            }

            // CÁCH A: JarFile cho phép tra cứu entry bằng tên.
            try (JarFile jar = new JarFile(path.toFile())) {
                Manifest meta = jar.getManifest();
                System.out.println("jarfile-main="
                        + meta.getMainAttributes().getValue(
                                Attributes.Name.MAIN_CLASS));

                JarEntry readme = jar.getJarEntry("docs/readme.txt");
                try (InputStream in = jar.getInputStream(readme)) {
                    System.out.println("jarfile-readme="
                            + new String(in.readAllBytes(),
                                    StandardCharsets.UTF_8));
                }

                Attributes perEntry =
                        meta.getAttributes("docs/readme.txt");
                System.out.println("jarfile-role="
                        + perEntry.getValue("X-Role"));

                System.out.println("jarfile-config="
                        + (jar.getJarEntry("config/app.properties") != null));
            }

            // CÁCH B: JarInputStream chỉ đọc lần lượt các entry.
            try (JarInputStream stream = new JarInputStream(
                    Files.newInputStream(path))) {
                Manifest meta = stream.getManifest();
                System.out.println("stream-main="
                        + meta.getMainAttributes().getValue(
                                Attributes.Name.MAIN_CLASS));

                List<String> names = new ArrayList<>();
                JarEntry entry;
                while ((entry = stream.getNextJarEntry()) != null) {
                    if (!entry.isDirectory()) {
                        names.add(entry.getName());
                        // Chỉ vì ví dụ có hai file rất nhỏ.
                        byte[] content = stream.readAllBytes();
                        if (entry.getName().equals("docs/readme.txt")) {
                            System.out.println("stream-readme="
                                    + new String(content,
                                            StandardCharsets.UTF_8));
                        }
                    }
                    stream.closeEntry();
                }

                Collections.sort(names); // không dựa vào thứ tự ZIP
                System.out.println("stream-files=" + names);
            }
        } finally {
            Files.deleteIfExists(path);
        }
    }
}
```

Lệnh biên dịch và chạy:

```bash
javac JarFileVsInputStreamDemo.java
java JarFileVsInputStreamDemo
```

**Stdout khi tạo/đọc file thành công (Java 17+):**

```text
jarfile-main=example.Main
jarfile-readme=HELLO
jarfile-role=documentation
jarfile-config=true
stream-main=example.Main
stream-readme=HELLO
stream-files=[config/app.properties, docs/readme.txt]
```

Điều gì xảy ra theo thứ tự?

1. `Manifest` được tạo bằng API chính thức; `Manifest-Version` là `1.0` và `Main-Class` là `example.Main`.
2. `manifest.getEntries()` có một section riêng cho `docs/readme.txt` với metadata `X-Role=documentation`.
3. `JarOutputStream` nhận manifest ngay khi khởi tạo và viết các entry cần thiết; sau đó chương trình thêm hai file nội dung nhỏ.
4. `JarFile` lấy manifest không phải duyệt lần lượt entry, rồi tra tên `docs/readme.txt` và đọc byte UTF-8. Nó cũng kiểm tra một entry khác có tồn tại không.
5. `JarInputStream` lấy manifest ban đầu, sau đó `getNextJarEntry()` tuần tự đọc các entry file; nó không cần/không cung cấp chức năng tra theo tên tức thì. Danh sách được sắp xếp **trước khi in** để không phụ thuộc thứ tự metadata/entry trong archive.
6. `JarInputStream.readAllBytes()` trong vòng lặp đọc **nội dung entry hiện tại**, không nuốt luôn toàn bộ những entry còn lại. Ví dụ chỉ chứa vài byte nên sử dụng `readAllBytes()` có thể chấp nhận; với JAR không tin cậy phải xử lý theo block và áp giới hạn.
7. Hai đối tượng đọc và các entry streams đều được đóng đúng cách trước khi xóa tệp tạm.

Việc JAR chứa `Main-Class: example.Main` trong bài **không** làm nó trở thành ứng dụng Java có thể chạy: JAR mẫu không chứa `example/Main.class`. Ví dụ kiểm tra **đọc metadata ở tầng I/O**, không kiểm thử `java -jar`.

## 9. Khi nguồn là mạng hoặc stream một lần

Ví dụ bạn nhận dữ liệu JAR qua một HTTP response body dạng `InputStream`, hoặc một file được giải nén từ một tầng khác:

```java
try (JarInputStream jar = new JarInputStream(responseBody)) {
    JarEntry entry;
    while ((entry = jar.getNextJarEntry()) != null) {
        if (!entry.isDirectory()) {
            // Xử lý byte nội dung entry hiện tại ở đây.
        }
        jar.closeEntry();
    }
}
```

Đoạn này **không độc lập**: `responseBody` là một `InputStream` do ứng dụng cung cấp, và cần xử lý `IOException`. Quan trọng:

- `JarInputStream` đọc tuần tự, phù hợp khi bạn không thể seek ngược hoặc không muốn lưu toàn bộ archive ra file trước.
- Đóng `JarInputStream` sẽ đóng luồng nguồn phía dưới. Nếu HTTP client còn cần stream cho mục đích khác, cần thiết kế quyền sở hữu rõ ràng.
- Chỉ vì có byte đến từ mạng không có nghĩa JAR đã nhận đủ. Nếu kết nối ngắt giữa chừng, code phải xử lý `IOException`, EOF bất thường, và kiểm tra integrity theo nhu cầu.
- Nếu muốn tìm nhanh một entry ở cuối archive nhiều lần, cân nhắc **ghi tạm có giới hạn dung lượng** rồi sử dụng `JarFile`; không sao chép toàn bộ input stream vào RAM vô hạn chỉ để mở lại.
- Manifest **có thể là `null`** dù archive là ZIP hợp lệ, đặc biệt khi không có manifest ở đầu luồng đúng vị trí.

Khi chuyển stream sang file tạm, tránh đặt niềm tin vào tên file do phía gửi cung cấp để tạo path truy cập ngoài thư mục cho phép; và giới hạn tổng byte nhận.

## 10. Metadata của `JarEntry`: cái gì đọc được và cái gì chưa chắc có?

`JarEntry` extends `ZipEntry`, nên có các trường:

| API | Ý nghĩa / lưu ý |
| --- | --- |
| `getName()` | Đường dẫn logic của entry bên trong archive |
| `isDirectory()` | True với directory entry (thường tên kết thúc bằng `/`) |
| `getSize()` | Kích thước giải nén nếu biết, có thể `-1` |
| `getCompressedSize()` | Kích thước dữ liệu nén nếu biết, có thể `-1` |
| `getCrc()` | CRC-32 nếu biết, có thể `-1` |
| `getMethod()` | Phương thức nén ZIP (thường `STORED` hoặc `DEFLATED`) |
| `getTime()` | Thời gian sửa đổi nếu có, chi tiết phụ thuộc metadata archive |
| `getComment()` | Comment entry nếu có |
| `getAttributes()` | Main? **Không**: đây là API của `Manifest`, không phải `JarEntry` |
| `getCertificates()` / `getCodeSigners()` | Metadata chữ ký sau khi xác thực nội dung đã đọc đầy đủ, có thể `null` |

Lưu ý đặc biệt: không có phương thức `JarEntry.getAttributes()` để trả về section manifest. Muốn metadata theo entry, dùng `Manifest.getAttributes(entry.getName())`; còn `getCertificates()/getCodeSigners()` liên quan đến chữ ký nếu JAR có ký và quá trình xác minh entry đã diễn ra.

Giá trị `size` hoặc `compressedSize` của entry là metadata archive, **không phải giới hạn an toàn tuyệt đối**. Khi stream đọc một archive không tin cậy, kích thước có thể chưa biết trước khi đọc xong, hoặc header không đủ để bảo vệ khỏi việc giải nén dữ liệu khổng lồ. Luôn đếm **byte giải nén thực tế** và giới hạn trong quá trình đọc.

## 11. Xác minh chữ ký JAR không đồng nghĩa tin tưởng nguồn phát

Java hỗ trợ JAR được ký và metadata liên quan tới chữ ký. Một số constructor của `JarFile` nhận flag `verify`; `JarInputStream` cũng có overload nhận `boolean verify`.

Điều quan trọng:

1. `verify=true` yêu cầu kiểm tra chữ ký khi nội dung được đọc theo những điều kiện có hỗ trợ, **không** có nghĩa toàn bộ archive đã được kiểm tra ngay lúc constructor trả về.
2. Với một entry đã ký, metadata `getCertificates()/getCodeSigners()` thường chỉ có sau khi **đọc hết nội dung entry và xác minh xong**.
3. Một JAR không có chữ ký có thể có `getCertificates()==null` hoặc `getCodeSigners()==null`, dù quá trình đọc thành công.
4. **Xác minh chữ ký/đối chiếu digest không tự xác nhận publisher được tin cậy**. Ứng dụng phải có chính sách tin cậy chứng thư, chuỗi chứng chỉ, hạn dùng và danh tính bên ký phù hợp, thay vì chỉ kiểm tra “có signers”.
5. Đối với `JarInputStream`, xử lý signed JAR còn phụ thuộc manifest đúng vị trí đầu và các entry chữ ký xuất hiện theo thứ tự phù hợp. Không suy diễn signed-JAR verification tùy ý như một bảo đảm dành cho mọi JAR bị đóng gói sai trật tự.
6. `JarFile` hỗ trợ việc xử lý chữ ký khi dùng đúng API; nhưng lỗi đọc, entry hỏng hoặc kiểm tra chữ ký thất bại phải được coi là lỗi, không được lặng lẽ bỏ qua.

Snippet kiểm tra một entry đã được đọc hết:

```java
try (JarFile jar = new JarFile(file, true)) {
    JarEntry entry = jar.getJarEntry("docs/readme.txt");
    if (entry == null) {
        throw new IOException("Entry not found");
    }

    try (InputStream in = jar.getInputStream(entry)) {
        in.transferTo(OutputStream.nullOutputStream());
    }

    // Nếu jar được ký phù hợp, chỉ khi đọc hết entry
    // mới có cơ sở xét signer/certificate metadata.
    var signers = entry.getCodeSigners();
    // signers == null không chứng minh JAR độc hại;
    // signers != null chưa chứng minh nguồn phát được tin cậy.
}
```

Đây là snippet **không độc lập**: `file` là `File`, cần import `JarFile`/`JarEntry`/`InputStream`/`OutputStream`/`IOException` và xử lý theo chính sách bảo mật. Đoạn mã không đưa ra kết luận trust và cũng không kiểm chứng chữ ký của mọi entry nếu chỉ đọc một entry.

Khi xử lý plug-in hoặc artifact tải từ nguồn không tin cậy, nên có một quy trình trust policy riêng (checksum/digest qua kênh đáng tin, chữ ký và chain validation, repository signing, sandbox, phân quyền). JarFile/JarInputStream chỉ là công cụ I/O và kiểm chứng theo hợp đồng API, không phải hệ thống quản lý trust hoàn chỉnh.

## 12. `Manifest` và multi-release JAR

Java 9+ hỗ trợ **multi-release JAR**, nơi có thể chứa các lớp/tài nguyên phiên bản:

```text
META-INF/MANIFEST.MF
com/example/Lib.class
META-INF/versions/9/com/example/Lib.class
META-INF/versions/17/com/example/Lib.class
```

Trong main attributes của manifest có thể có:

```text
Multi-Release: true
```

Điều này cho phép các cơ chế đọc phù hợp runtime chọn versioned entry trong điều kiện được API hỗ trợ. `JarFile` có các constructor/method hỗ trợ chọn version của multi-release archive (ví dụ overload có `Runtime.Version`) và cách nhìn các entry vật lý/logic có thể khác nhau theo cấu hình.

**Không** đồng nhất `JarInputStream` với một runtime class loader: nó duyệt **stream entry vật lý**, không tự hứa chọn entry theo phiên bản Java mà bạn cần. Khi chương trình chỉ muốn liệt kê archive bytes, cần quyết định mục tiêu là xem **entry được lưu vật lý** hay **view tương ứng version**. Dùng overload của `JarFile` theo tài liệu khi yêu cầu multi-release rõ ràng.

Trong ví dụ đầu bài, manifest không có `Multi-Release` nên không có vấn đề chọn phiên bản. Không nên thêm logic multi-release vào bài toán đơn giản chỉ đọc `docs/readme.txt` khi không cần.

## 13. Một số phương pháp liệt kê khác

### Duyệt với `JarFile.entries()`

```java
try (JarFile jar = new JarFile(path.toFile())) {
    var entries = jar.entries();
    while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        if (!entry.isDirectory()) {
            System.out.println(entry.getName());
        }
    }
}
```

Đây là snippet cần `Path path` và import; thứ tự liệt kê **không phải hợp đồng sắp xếp theo tên**. Nếu muốn output ổn định, gom danh sách các tên entry rồi `Collections.sort(...)` trước khi in.

### Duyệt bằng `JarInputStream` và giới hạn byte

```java
JarEntry entry;
while ((entry = jis.getNextJarEntry()) != null) {
    if (entry.isDirectory()) {
        jis.closeEntry();
        continue;
    }

    long bytesForThisEntry = 0;
    int count;
    byte[] chunk = new byte[8192];
    while ((count = jis.read(chunk)) != -1) {
        bytesForThisEntry += count;
        if (bytesForThisEntry > limitPerEntry) {
            throw new IOException("Entry too large");
        }
        // Xử lý chunk[0..count) một cách có giới hạn.
    }

    jis.closeEntry();
}
```

Đây là snippet, giả định `jis` và `limitPerEntry` đã khai báo. Với archive độc hại, vẫn cần giới hạn **tổng kích thước sau giải nén, số entry và thời gian**. Không chỉ dựa trên `getSize()`.

## 14. Đọc metadata không cần “extract” ra filesystem

Không phải nhu cầu nào cũng cần giải nén JAR thành nhiều file thật:

- Đọc `config/app.properties`: tra `JarEntry` hoặc duyệt stream, mở nội dung trong `InputStream` rồi truyền cho `Properties.load` nếu dữ liệu ở đúng định dạng.
- Đọc `docs/readme.txt`: lấy byte, chuyển sang text bằng charset cụ thể.
- Kiểm tra version build: đọc một thuộc tính main hoặc per-entry trong `Manifest`, nếu có.
- Liệt kê tài nguyên: duyệt danh sách tên entry, tránh tạo file tạm không cần thiết.
- Kiểm tra dấu hiệu entry có tồn tại: `JarFile.getJarEntry("...") != null`.

Nếu mục tiêu chỉ là tra cứu một tài nguyên đã biết trong một archive trên đĩa, `JarFile` thường đơn giản hơn giải nén toàn bộ. Nếu archive đến từ một luồng một lần, `JarInputStream` tránh bước ghi xuống file nhưng yêu cầu duyệt tuần tự.

### Đọc `Properties` từ entry

```java
try (JarFile jar = new JarFile(jarPath.toFile())) {
    JarEntry entry = jar.getJarEntry("config/app.properties");
    if (entry == null) {
        throw new IOException("Missing config entry");
    }

    Properties props = new Properties();
    try (InputStream in = jar.getInputStream(entry)) {
        props.load(in);
    }

    String mode = props.getProperty("mode");
}
```

Đây là snippet cần `jarPath` và các import. `Properties.load(InputStream)` dùng quy tắc định dạng properties riêng; không mặc định tương đương parser UTF-8 cho mọi ký tự trực tiếp trong file. Nếu dữ liệu được định nghĩa là UTF-8, dùng `Reader` với `StandardCharsets.UTF_8` để gọi `Properties.load(Reader)`.

Trong ví dụ `JarFileVsInputStreamDemo`, `config/app.properties` chỉ có ASCII `mode=demo`, nên không nảy sinh khác biệt encoding.

## 15. Không dùng tên entry chưa tin cậy để giải nén tùy tiện

Một JAR có thể chứa tên entry không an toàn, ví dụ:

```text
../outside.txt
../../etc/config
/absolute/path.txt
dir/../../escape.txt
```

Nếu chương trình chuyển `entry.getName()` thẳng thành `Path` dưới thư mục đích rồi ghi file, có nguy cơ **Zip Slip / path traversal**: file có thể được ghi bên ngoài thư mục mong muốn.

Cách bắt đầu kiểm tra:

```java
Path root = extractionDir.toAbsolutePath().normalize();
Path target = root.resolve(entry.getName()).normalize();

if (!target.startsWith(root)) {
    throw new IOException("Entry escapes extraction directory");
}
```

Đây là snippet, chưa phải phương pháp extract bảo mật hoàn chỉnh: `extractionDir` và `entry` cần khai báo. Kiểm tra thêm:

- Entry absolute path, directory traversal và tên không hợp lệ với OS.
- **Symbolic links** đã tồn tại trong thư mục đích có thể làm đường dẫn thực khác đường dẫn lexical. `normalize()` và `startsWith(root)` chưa bảo vệ đầy đủ khỏi symlink/race; nên extract trong thư mục mới có quyền phù hợp, có cơ chế kiểm tra/mở an toàn và chính sách cấm symlink khi cần.
- Giới hạn tổng byte sau giải nén, độ sâu path, số entry, thời gian, quota.
- Hai entry có cùng tên hoặc tên chuẩn hóa trùng nhau; không tự động ghi đè dữ liệu người dùng.
- Lỗi CRC, lỗi cấu trúc ZIP và các entry có khả năng gây tiêu tốn tài nguyên quá mức.
- Không khởi chạy code trong JAR vừa nhận chỉ vì metadata manifest ghi tên `Main-Class`.

Nếu chỉ cần đọc metadata/entry trong memory, không extract ra đĩa sẽ tránh một phần rủi ro path traversal, nhưng vẫn cần giới hạn tài nguyên khi giải nén.

## 16. Vòng đời tài nguyên và lỗi I/O

### Với `JarFile`

`JarFile` giữ tài nguyên liên quan đến file ZIP đã mở. Dùng `try-with-resources` để đóng `JarFile`. Các `InputStream` của entry cũng nên được đóng sau khi dùng. Đừng lưu một entry stream rồi đóng `JarFile` trước và kỳ vọng stream ấy đọc tiếp bình thường.

### Với `JarInputStream`

`JarInputStream` bọc `InputStream` nền. Đóng `JarInputStream` sẽ đóng stream nền theo cơ chế `FilterInputStream`, do đó chỉ trao quyền sở hữu stream nếu thực sự cho phép đóng.

Không trộn nhiều consumer đọc cùng `JarInputStream` trong các thread khác nhau mà không phối hợp: vị trí entry và trạng thái giải nén là tuần tự. Nếu muốn đọc hai entry độc lập, `JarFile` với các entry stream riêng biệt thường phù hợp hơn, nhưng vẫn cần quản lý lifetime `JarFile` và thread-safety của phần ứng dụng.

### Lỗi thường gặp

- `IOException`: lỗi đọc filesystem, EOF bất thường, archive bị hỏng hoặc provider từ chối thao tác.
- `ZipException` (subclass của `IOException`): cấu trúc ZIP không hợp lệ, CRC hoặc lỗi giải nén.
- `SecurityException` trong một số trường hợp xác minh signed JAR, quyền truy cập hoặc xử lý bảo mật.
- `NullPointerException` có thể xảy ra nếu bạn gọi `jar.getInputStream(jar.getJarEntry(name))` mà entry không tồn tại; hãy kiểm tra `null` trước.
- `jar.getManifest() == null` là trường hợp **bình thường** với JAR không có manifest; không được mặc định luôn tồn tại.
- `entry.isDirectory()` true nghĩa là entry mô tả thư mục; không cố giải mã nó thành text resource.
- `jarInput.getNextJarEntry() == null` mới là điều kiện hết các entry được duyệt, khác `read() == -1` đối với **entry hiện tại**.

Khi đọc archive từ nguồn bên ngoài, không coi một ngoại lệ là “file rỗng” rồi bỏ qua; phải xử lý lỗi phù hợp với mức độ an toàn của dữ liệu.

## 17. Ví dụ nhỏ khác: đọc `Manifest` từ JAR trong bộ nhớ

Nếu bạn chỉ có `byte[]` mô tả JAR (ví dụ do một API vừa tạo trong memory), `JarInputStream` làm việc trực tiếp với `ByteArrayInputStream`.

Lưu **`InMemoryJarDemo.java`**:

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class InMemoryJarDemo {
    public static void main(String[] args) throws Exception {
        Manifest m = new Manifest();
        m.getMainAttributes().put(
                Attributes.Name.MANIFEST_VERSION, "1.0");
        m.getMainAttributes().putValue("X-Label", "memory-demo");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JarOutputStream out = new JarOutputStream(bytes, m)) {
            out.putNextEntry(new JarEntry("hello.txt"));
            out.write("OK".getBytes(StandardCharsets.US_ASCII));
            out.closeEntry();
        }

        try (JarInputStream input = new JarInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            Manifest manifest = input.getManifest();
            System.out.println("label="
                    + manifest.getMainAttributes().getValue("X-Label"));

            JarEntry entry = input.getNextJarEntry();
            System.out.println("entry=" + entry.getName());
            System.out.println("content="
                    + new String(input.readAllBytes(),
                            StandardCharsets.US_ASCII));
            input.closeEntry();

            System.out.println("next=" + input.getNextJarEntry());
        }
    }
}
```

Chạy:

```bash
javac InMemoryJarDemo.java
java InMemoryJarDemo
```

Stdout khi thành công:

```text
label=memory-demo
entry=hello.txt
content=OK
next=null
```

Ví dụ này **không cần file tạm**. `JarOutputStream(bytes, manifest)` đã ghi manifest theo vị trí đầu đúng quy tắc của `JarInputStream`, và chỉ có một entry nội dung do ứng dụng bổ sung. `getNextJarEntry()` sau khi đọc `hello.txt` trả `null` khi không còn entry nào. `ByteArrayOutputStream` không làm mất byte khi `close()`; tuy nhiên với JAR lớn, tạo `byte[]` cho toàn bộ archive sẽ tốn RAM, nên ưu tiên stream hoặc file theo yêu cầu.

## 18. Checklist lựa chọn và thực hành

Khi triển khai logic đọc JAR, tự hỏi theo thứ tự:

1. **Nguồn ở đâu?** Nếu là file, `JarFile` phù hợp cho tra cứu; nếu là stream một lần, `JarInputStream` phù hợp cho đọc tuần tự.
2. **Chỉ cần metadata hay byte nội dung?** Đọc `Manifest` nếu chỉ cần attributes; dùng entry `InputStream`/`JarInputStream.read` nếu cần payload.
3. **Manifest có bảo đảm đầu stream không?** Với `JarInputStream`, điều này quyết định khả năng nhận `getManifest()`.
4. **Có cần duyệt tất cả entry không?** Với `JarFile` có thể tra trực tiếp theo tên; stream tuần tự phải đi lần lượt.
5. **Có xử lý file không tin cậy không?** Giới hạn số entry, byte giải nén, dung lượng tạm và thời gian; kiểm soát đường dẫn khi extract.
6. **Có yêu cầu chữ ký không?** Đọc đủ entry để hoàn tất verification và có chính sách trust độc lập, không chỉ nhìn metadata `Main-Class`/`signers`.
7. **Có multi-release JAR không?** Xác định muốn đọc entry vật lý hay versioned view và chọn API/overload phù hợp.
8. **Ai đóng tài nguyên?** `try-with-resources` cho `JarFile`, từng entry stream và `JarInputStream`. Không đóng archive trong lúc code còn đọc entry.
9. **Charset là gì?** Byte trong entry không mặc định là UTF-8 hoặc văn bản; chỉ decode bằng charset khi định dạng quy định.
10. **Test những gì?** JAR có và không có manifest, manifest đầu/muộn, entry vắng mặt, directory entry, UTF-8 text, archive hỏng, entry nén lớn, null metadata và signed JAR nếu nghiệp vụ dùng chữ ký.

## 19. Bài tập tự kiểm tra

1. Vì sao `JarFile` có thể mở `config/app.properties` mà không cần đọc toàn bộ `docs/readme.txt` trước?
2. Khi đã dùng `JarInputStream`, API nào đưa con trỏ đến entry tiếp theo? API nào đọc byte của entry hiện tại?
3. Khi `JarInputStream.read()` trả `-1` ở một entry, có được kết luận cả archive đã hết không?
4. Tại sao `JarInputStream.getManifest()` có thể trả `null` dù archive có file `META-INF/MANIFEST.MF` nằm ở vị trí muộn?
5. `Manifest.getEntries()` có liệt kê tất cả `.class` và resource trong JAR không? Phải dùng API nào?
6. `Main-Class` có đủ để chứng minh JAR chạy được bằng `java -jar` không?
7. `Manifest.getAttributes("docs/readme.txt")` khác với `Manifest.getMainAttributes()` ở đâu?
8. Tại sao phải đóng `JarFile` sau khi đã đóng các `InputStream` cho entry?
9. Khi nhận một JAR 2 GB từ mạng, vì sao `Files.readAllBytes` hoặc `JarInputStream.readAllBytes` trên entry không có giới hạn có thể rủi ro?
10. `entry.getSize() == -1` nghĩa là gì? Có được tin tuyệt đối vào size do ZIP header cung cấp khi dữ liệu không tin cậy không?
11. Khi nào `JarEntry.getCodeSigners()` mới có thể mang dữ liệu chữ ký, và vì sao `signers != null` vẫn chưa xác nhận nguồn đáng tin?
12. `JarInputStream` có tự chọn entry `META-INF/versions/17` như class loader cho multi-release JAR không?
13. Nếu một ZIP entry có tên `../secrets.txt` và chương trình extract ra ổ đĩa, cần ngăn rủi ro gì? `Path.normalize()` đã đủ chống symlink chưa?
14. Hãy chạy hai chương trình thực hành, đối chiếu stdout và sửa `JarFileVsInputStreamDemo` để thêm entry `images/icon.bin`. Vì sao không nên ép decode một tệp ảnh thành text?
15. Nếu một ứng dụng chỉ kiểm tra tồn tại `META-INF/MANIFEST.MF` mà không kiểm tra `Manifest-Version` và định dạng manifest, những giả định nào có thể sai?

## 20. Tài liệu Java SE 17

- [`JarFile` — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/jar/JarFile.html): truy cập entry, manifest, đa phiên bản và chữ ký.
- [`JarInputStream` — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/jar/JarInputStream.html): đọc tuần tự, `getManifest()`, `getNextJarEntry()`.
- [`JarEntry` — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/jar/JarEntry.html): thông tin entry, certificate và code signer.
- [`Manifest` — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/jar/Manifest.html): main/per-entry attributes.
- [`JarOutputStream` — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/jar/JarOutputStream.html): tạo archive và thêm manifest.
- [`ZipFile` và `ZipInputStream` — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/package-summary.html): nền tảng định dạng ZIP và semantics entry.

## 21. Ghi nhớ

**`JarFile`** mở JAR trên filesystem, tra cứu entry theo tên và cấp stream đọc từng entry; **`JarInputStream`** đọc tuần tự archive từ một `InputStream`. **`Manifest`** là metadata ở `META-INF/MANIFEST.MF` gồm main attributes và per-entry attributes, không phải danh sách mọi file.

Khi dùng `JarInputStream`, vị trí manifest **đầu archive** là điều kiện quan trọng để `getManifest()` hoạt động theo hợp đồng; manifest đã được nhận ở đầu sẽ không hiện lại như entry thông thường qua `getNextJarEntry()`. Luôn kiểm tra `null`, phân biệt EOF của entry với EOF của archive, giới hạn byte giải nén, đóng tài nguyên đúng thứ tự và không đồng nhất việc đọc manifest/chữ ký với thực thi hay tin tưởng nội dung JAR.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="compression-and-archives-009">Deflater, Inflater và phạm vi điều khiển mức nén</a>

<details>
<summary>Xem chi tiết</summary>

# Deflater, Inflater và phạm vi điều khiển mức nén trong Java

## 1. Vấn đề: nén và giải nén là hai chiều của một luồng dữ liệu

Trong `java.util.zip`, cặp lớp `Deflater` và `Inflater` cung cấp giao diện ở mức thấp cho thuật toán **DEFLATE**. DEFLATE nén byte bằng kỹ thuật kết hợp LZ77 và mã Huffman; mục tiêu là giảm số byte lưu trữ hoặc truyền đi khi dữ liệu có tính lặp.

~~~text
Dữ liệu gốc: byte[] hoặc ByteBuffer
                  |
                  v
            Deflater (nén)
        level + strategy + trạng thái
                  |
                  v
         Luồng byte đã nén
                  |
       lưu file / truyền mạng
                  |
                  v
            Inflater (giải nén)
                  |
                  v
             Byte gốc
~~~

Hai lớp xử lý **byte**, không hiểu trực tiếp ký tự, dòng văn bản hay object Java. Trước khi nén chuỗi, hãy mã hóa bằng charset cụ thể, ví dụ `StandardCharsets.UTF_8`. Khi giải nén xong, chỉ giải mã byte thành text nếu biết đúng charset.

**Câu hỏi chính của bài:** Java cho phép kiểm soát *mức nén* tới đâu, mức đó ảnh hưởng tới điều gì, và điều gì nằm ngoài khả năng bảo đảm của `Deflater`/`Inflater`?

Câu trả lời ngắn: **mức nén là một tham số của bên nén để cân bằng tốc độ CPU và khả năng giảm kích thước**. Nó không quyết định kích thước đầu ra tuyệt đối, không bảo đảm một tỷ lệ nén, không điều khiển giải nén theo các mức tương ứng và không tự biến luồng byte thành một tệp ZIP hay GZIP.

## 2. Phân biệt thuật toán DEFLATE với zlib, gzip và ZIP

Đây là bốn tên gần nhau nhưng **không đồng nghĩa**:

| Thuật ngữ | Bản chất | Điều cần nhớ |
| --- | --- | --- |
| DEFLATE | Thuật toán/định dạng mã hóa dữ liệu nén | Có thể sử dụng dưới dạng raw DEFLATE |
| zlib | Cách đóng gói DEFLATE với phần header/trailer và kiểm tra dữ liệu | `new Deflater(level)` tạo luồng có zlib wrapper; `new Inflater()` giải nén định dạng tương ứng |
| gzip | Định dạng có header/trailer gzip, hỗ trợ metadata thích hợp | Java cung cấp `GZIPOutputStream` và `GZIPInputStream` |
| ZIP | Định dạng archive gồm entry, metadata và bảng thư mục archive | Java cung cấp `ZipOutputStream`, `ZipInputStream`, `ZipFile` và ZIP filesystem |

Ví dụ:

~~~text
raw DEFLATE: [deflate payload]

zlib:        [zlib header][deflate payload][Adler-32]

gzip:        [gzip header][deflate payload][gzip trailer]

ZIP:         [local entry headers + data][central directory + metadata]
~~~

Sơ đồ giản lược, không mô tả đầy đủ mọi field hay biến thể định dạng.

**Không truyền nguyên một file `.gz` hoặc `.zip` vào `new Inflater()` và kỳ vọng giải nén thành công.** `Inflater()` mặc định mong luồng định dạng zlib, không tự đọc header gzip hoặc cấu trúc archive ZIP. Với gzip dùng `GZIPInputStream`; với archive ZIP dùng API ZIP phù hợp.

Nếu cần **raw DEFLATE** không có zlib header/checksum wrapper, có overload `new Deflater(level, true)` và `new Inflater(true)`. Hai đầu phải thống nhất định dạng. `nowrap=true` không có nghĩa tạo được gzip/ZIP hoàn chỉnh; các cấu trúc chứa vẫn phải được xử lý ở tầng thích hợp.

## 3. Các mức nén có thể lựa chọn

`Deflater` cho phép chỉ định mức nén khi khởi tạo hoặc thay đổi mức cho phần dữ liệu được nén tiếp theo:

~~~java
Deflater compressor = new Deflater(Deflater.BEST_SPEED);
compressor.setLevel(Deflater.BEST_COMPRESSION);
~~~

Đây là **mảnh Java minh họa**; phải import `java.util.zip.Deflater` và gọi `compressor.end()` sau khi không sử dụng nữa.

| Hằng số | Giá trị | Ý nghĩa |
| --- | ---: | --- |
| `Deflater.NO_COMPRESSION` | 0 | Không áp dụng chiến lược giảm kích thước DEFLATE thông thường; luồng vẫn có framing phù hợp |
| `Deflater.BEST_SPEED` | 1 | Ưu tiên tốc độ nén |
| Mức từ 2 tới 8 | 2–8 | Các lựa chọn đánh đổi giữa tốc độ và khả năng nén |
| `Deflater.BEST_COMPRESSION` | 9 | Ưu tiên khả năng nén |
| `Deflater.DEFAULT_COMPRESSION` | -1 | Để implementation dùng mức mặc định |

Constructor thông dụng:

~~~java
new Deflater();                              // Mức mặc định, zlib wrapper
new Deflater(Deflater.BEST_SPEED);           // Level 1, zlib wrapper
new Deflater(Deflater.BEST_COMPRESSION);     // Level 9, zlib wrapper
new Deflater(Deflater.NO_COMPRESSION);       // Level 0, zlib wrapper
new Deflater(6, true);                       // Raw DEFLATE với level 6
~~~

Những giá trị ngoài phạm vi được phép, chẳng hạn `-2` hoặc `10`, dẫn đến `IllegalArgumentException`. Giá trị `-1` là hằng số dành cho mặc định, **không phải mức nén mạnh hơn 9**.

### Level 0 có đồng nghĩa trả nguyên input không?

Không. `NO_COMPRESSION` dùng chế độ không nén payload theo lựa chọn DEFLATE, nhưng **đầu ra vẫn là một luồng nén được đóng gói** theo định dạng đã chọn. Với zlib wrapper, có overhead header/trailer và thông tin block. Byte đầu ra không mặc nhiên bằng đúng byte đầu vào; vẫn phải giải nén đúng định dạng để lấy byte gốc.

Với dữ liệu nhỏ hoặc khó nén, kết quả đầu ra có thể **lớn hơn input** vì overhead. Vì vậy mức nén 9 cũng **không bảo đảm** kích thước tệp sẽ nhỏ hơn mức 1 trong mọi trường hợp.

### Level 9 có luôn tạo file nhỏ nhất?

Không có bảo đảm phổ quát như vậy trong API. Kích thước phụ thuộc:

- Loại dữ liệu: text lặp lại thường dễ nén hơn dữ liệu đã nén/mã hóa ngẫu nhiên.
- Kích thước khối input và cách cung cấp dữ liệu theo thời gian.
- Phiên bản/thư viện zlib bên dưới và implementation.
- Mức nén, strategy và cách flush stream.
- Định dạng chứa, metadata và overhead.

Level 9 thường tiêu tốn nhiều CPU hơn để cố gắng giảm số byte, nhưng **không có cam kết tỷ lệ nén cụ thể**. Level 1 thường nhanh hơn, phù hợp khi độ trễ nén quan trọng, nhưng cũng không có cam kết thời gian cố định.

## 4. Ranh giới kiểm soát của mức nén

Cần phân biệt những thứ mức nén **có thể ảnh hưởng** và những thứ nó **không trực tiếp bảo đảm**:

| Khía cạnh | Mức nén có ảnh hưởng? | Giải thích |
| --- | --- | --- |
| Chi phí CPU khi nén | Có, thường rõ rệt | Mức cao thường tìm kiếm phương án nén nhiều hơn |
| Kích thước output | Có thể | Kết quả phụ thuộc dữ liệu, overhead, flush, implementation |
| Tốc độ giải nén | Có thể có khác biệt gián tiếp | Không có một mức giải nén đối xứng để chọn |
| Tính đúng đắn của dữ liệu giải nén | Không nên thay đổi | Luồng hợp lệ phải giải nén ra byte gốc |
| Charset/encoding của văn bản | Không | Encoder ký tự được chọn trước/ngoài bộ nén |
| Mã hóa bảo mật | Không | DEFLATE không phải encryption |
| Metadata ZIP/GZIP | Không | Định dạng chứa quyết định metadata |
| Bảo đảm archive có CRC và entries | Không | Được quản lý bởi container/API ZIP/GZIP |
| Dung lượng lớn nhất sau giải nén | Không | Phải tự áp hạn mức output để tránh decompression bomb |
| Kết quả byte nén giống nhau trên mọi JDK | Không | API không bảo đảm tính tái lập byte-for-byte giữa implementations |

Phạm vi này quyết định cách thiết kế API. Nếu muốn cho người dùng lựa chọn "nhanh" hay "nén kỹ", có thể map tới level 1 hoặc 9. Nhưng nếu nghiệp vụ yêu cầu kích thước không vượt 100 KiB, **chọn level 9 không thay thế việc kiểm tra dung lượng thực tế**.

Một thiết kế tốt nên đo thời gian CPU, output size và tỷ lệ nén với **chính loại dữ liệu ứng dụng** thay vì dựa vào nhãn mức nén để dự đoán tuyệt đối.

## 5. `Deflater` là bộ nén có trạng thái

Chu trình làm việc:

~~~text
new Deflater(level)
        |
        v
setInput(bytes)
        |
        v
deflate(outBuffer) ── trả số byte đã nén ra ──> lưu/chuyển đi
        |
        +── needsInput() ? cung cấp input kế tiếp
        |
        +── finish() khi không còn input
        |
        v
lặp deflate(...) cho đến finished() == true
        |
        v
end() giải phóng tài nguyên native
~~~

Các API quan trọng:

- `setInput(byte[])` hoặc overload có offset/length: đưa byte nguồn vào cho compressor.
- `needsInput()`: cho biết compressor đã tiêu thụ input hiện có và có thể cần thêm.
- `deflate(byte[])`, overload theo offset/length: ghi byte đã nén vào output và trả **số byte thực sự sinh ra**.
- `finish()`: báo **đã cung cấp hết input**, yêu cầu kết thúc luồng ở các lần `deflate` tiếp theo.
- `finished()`: chỉ đúng khi compressor đã kết thúc toàn bộ việc sinh output.
- `reset()`: reset trạng thái để bắt đầu một luồng nén mới với cùng object; không nối liền hai luồng cũ/mới như một luồng duy nhất.
- `end()`: giải phóng tài nguyên; không tiếp tục sử dụng sau khi kết thúc.

**`finish()` không tự trả ra toàn bộ byte nén.** Sau khi gọi `finish()` vẫn phải lặp `deflate(...)` cho đến `finished()`. Nếu output buffer nhỏ, nhiều lượt mới lấy đủ dữ liệu.

Tương tự, một lần `deflate(...)` không bảo đảm đã xử lý hết input hoặc ghi hết luồng nén. Kiểm tra số byte trả về và trạng thái compressor; không viết một chương trình chỉ gọi đúng một lần khi dữ liệu có thể lớn.

Khi cần nén theo streaming, thường gọi `setInput` mỗi lần `needsInput()` trở thành true, ghi toàn bộ output phát sinh ra tệp/mạng rồi lặp lại; khi nguồn EOF thì `finish()`, tiếp tục drain output. Trong ứng dụng nhỏ, có thể đưa toàn bộ byte input một lần như ví dụ ở phần sau.

## 6. `Inflater` giải nén như thế nào?

`Inflater` tiếp nhận **luồng byte đã nén** và tạo byte gốc. Nó không nhận một mức nén như `Deflater`.

~~~text
new Inflater()                  // zlib wrapper
        |
setInput(compressedBytes)
        |
inflate(uncompressedBuffer)
        |
        +── n > 0: có byte gốc mới
        |
        +── needsInput(): cần thêm input nén
        |
        +── needsDictionary(): cần dictionary phù hợp
        |
        +── DataFormatException: dữ liệu nén không hợp lệ
        |
        v
finished() == true
        |
        v
end()
~~~

Các trạng thái cần hiểu:

- `inflate(byte[])` trả số byte gốc được sinh ra trong lần gọi.
- `finished()` xác nhận đã đạt điểm kết thúc luồng nén.
- `needsInput()` nghĩa là Inflater không còn input nén để dùng; nếu nguồn file/mạng vẫn còn thì đưa thêm. Nếu nguồn đã EOF mà `finished()` chưa đúng, dữ liệu có thể bị cắt cụt.
- `needsDictionary()` báo luồng yêu cầu một preset dictionary; ứng dụng phải có đúng dictionary đã dùng lúc nén.
- `getRemaining()` cho biết số byte input nén còn nằm trong Inflater sau khi giải nén, hữu ích khi xử lý trailer hoặc nhiều phần dữ liệu nối tiếp.
- `DataFormatException` báo luồng không hợp lệ hoặc có vấn đề định dạng. Không đồng nhất mọi lỗi giải nén với lỗi charset.

**Không có `new Inflater(level)`**. Decoder phải đọc một luồng DEFLATE hợp lệ bất kể bên nén đã chọn mức 0, 1 hay 9. Mức nén không làm mất mát dữ liệu gốc; đây là cơ chế nén **lossless**.

Nếu đã giải nén hết một luồng và muốn dùng lại cùng object, gọi `reset()` rồi cấp input của **luồng mới** theo định dạng thích hợp. Không gọi `reset()` giữa một luồng đang xử lý dở.

## 7. Chương trình Java 17+ đầy đủ: nén nhiều mức và giải nén đúng dữ liệu

Chương trình này tạo dữ liệu ASCII lặp lại, nén cùng một payload với ba mức nén khác nhau, giải nén từng kết quả và so sánh byte-for-byte với nguồn.

Ví dụ cố ý **không in số byte compressed**, vì số byte cụ thể có thể khác giữa runtime/zlib implementation. Kết quả round-trip boolean mới là điều cần xác minh.

Lưu tên file `DeflaterInflaterDemo.java`:

~~~java
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class DeflaterInflaterDemo {
    private static final int MAX_DECOMPRESSED_BYTES = 1_000_000;

    static byte[] compress(byte[] input, int level) throws IOException {
        Deflater encoder = new Deflater(level); // zlib wrapper
        try {
            encoder.setInput(input);
            encoder.finish();

            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[128];

            while (!encoder.finished()) {
                int n = encoder.deflate(buffer);
                if (n == 0) {
                    throw new IOException("Deflater made no progress");
                }
                result.write(buffer, 0, n);
            }
            return result.toByteArray();
        } finally {
            encoder.end();
        }
    }

    static byte[] decompress(byte[] compressed)
            throws IOException, DataFormatException {
        Inflater decoder = new Inflater(); // zlib wrapper
        try {
            decoder.setInput(compressed);
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[128];

            while (!decoder.finished()) {
                int n = decoder.inflate(buffer);

                if (n > 0) {
                    if (result.size() + n > MAX_DECOMPRESSED_BYTES) {
                        throw new IOException("Decompressed data too large");
                    }
                    result.write(buffer, 0, n);
                    continue;
                }

                if (decoder.needsDictionary()) {
                    throw new IOException("Preset dictionary required");
                }
                if (decoder.needsInput()) {
                    throw new EOFException("Compressed stream truncated");
                }
                throw new IOException("Inflater made no progress");
            }
            return result.toByteArray();
        } finally {
            decoder.end();
        }
    }

    public static void main(String[] args)
            throws IOException, DataFormatException {
        byte[] source = "JAVA-NIO-COMPRESSION;".repeat(500)
                .getBytes(StandardCharsets.UTF_8);

        byte[] noCompression =
                compress(source, Deflater.NO_COMPRESSION);
        byte[] fast = compress(source, Deflater.BEST_SPEED);
        byte[] dense = compress(source, Deflater.BEST_COMPRESSION);

        System.out.println("level 0 round-trip: "
                + Arrays.equals(source, decompress(noCompression)));
        System.out.println("level 1 round-trip: "
                + Arrays.equals(source, decompress(fast)));
        System.out.println("level 9 round-trip: "
                + Arrays.equals(source, decompress(dense)));
        System.out.println("same original bytes: "
                + Arrays.equals(decompress(fast), decompress(dense)));
        System.out.println("input bytes: " + source.length);
    }
}
~~~

Biên dịch và chạy với JDK 17+:

~~~bash
javac DeflaterInflaterDemo.java
java DeflaterInflaterDemo
~~~

Stdout:

~~~text
level 0 round-trip: true
level 1 round-trip: true
level 9 round-trip: true
same original bytes: true
input bytes: 10500
~~~

Lý giải kết quả:

1. Chuỗi ASCII `JAVA-NIO-COMPRESSION;` gồm **21 byte**; lặp 500 lần được **10.500 byte** input.
2. `NO_COMPRESSION` (level 0) vẫn tạo ra một **luồng zlib hợp lệ**, có thể giải nén lại thành đúng input.
3. `BEST_SPEED` (level 1) và `BEST_COMPRESSION` (level 9) tạo các luồng nén với ưu tiên khác nhau; cả hai phải khôi phục cùng payload.
4. `Arrays.equals` so sánh byte gốc, không chỉ kiểm tra kích thước hoặc text được giải mã.
5. Buffer output chỉ có 128 byte; cả compressor và decoder đều được gọi lặp, tránh giả định một lượt nén/giải nén luôn hoàn thành.
6. `end()` trong `finally` giải phóng tài nguyên kể cả khi có lỗi.
7. Phần giải nén giới hạn output ở 1.000.000 byte để minh họa phòng tránh việc nhận một luồng nén rất nhỏ nhưng giải nén thành dữ liệu quá lớn.

Ví dụ dùng input đã biết và không cần dictionary, nên không phát sinh các trường hợp lỗi dictionary hay truncated stream khi chạy bình thường. Nếu tự sửa compressed bytes hoặc cắt giữa dòng, decoder có thể ném `DataFormatException`, `EOFException` hoặc ngoại lệ liên quan, thay vì in đủ năm dòng.

**Vì sao không in "level 9 chắc chắn nhỏ hơn level 1"?** Trên dữ liệu này thường có khác biệt kích thước, nhưng API không bảo đảm thứ tự tuyệt đối cho mọi input và mọi implementation. Nếu đang tối ưu hệ thống, hãy đo `compressed.length` thực tế và thời gian nén trên bộ dữ liệu đại diện.

## 8. `setLevel()` thay đổi những gì?

`Deflater.setLevel(int level)` cho phép điều chỉnh level của **những phần dữ liệu sẽ được nén tiếp theo**. Cách làm đúng là hiểu object Deflater là một state machine:

~~~java
Deflater encoder = new Deflater(Deflater.BEST_SPEED);
try {
    encoder.setInput(firstChunk);
    // Drain dữ liệu được tạo theo quy trình streaming.
    // Sau khi xử lý đầu vào này, có thể đổi ưu tiên:
    encoder.setLevel(Deflater.BEST_COMPRESSION);
    encoder.setInput(nextChunk);
    // Tiếp tục deflate(...) cho đến khi hết.
} finally {
    encoder.end();
}
~~~

Đây là **pseudocode dạng mảnh Java**, không phải chương trình chạy độc lập: `firstChunk`, `nextChunk` và hàm drain cần được định nghĩa. Không sao chép nguyên đoạn mà bỏ qua các bước tiêu thụ input, kiểm tra `needsInput()` và lấy output.

Các giới hạn quan trọng:

- `setLevel` không nén lại các byte đã được sinh ra trước đó; nó ảnh hưởng tới quá trình nén tiếp theo.
- Thay level không tự chuyển codec, không thay zlib sang gzip và không tự tạo một ZIP entry mới.
- Một luồng DEFLATE hợp lệ vẫn giải nén được mà không cần truyền mức nén cho `Inflater`.
- Khi đang thay level giữa luồng, cần tuân thủ thứ tự cấp input/deflate và flush phù hợp để không làm rối state; không dùng setter như lệnh "đã áp dụng ngay cho mọi byte trước đó".
- Nếu nhu cầu là "mỗi tệp một level" trong một archive ZIP, dùng API archive hỗ trợ level cho các entry theo đúng ngữ nghĩa, không đơn giản ghép các luồng Deflater tùy ý vào tệp ZIP.

Trong thực tế, **lựa chọn một mức nén khi bắt đầu luồng** rồi giữ cố định thường dễ kiểm thử hơn thay đổi liên tục. Thay level giữa một luồng chỉ khi đã xác định nhu cầu streaming và đo hiệu quả.

## 9. Strategy không đồng nghĩa mức nén

Ngoài `setLevel`, `Deflater` có `setStrategy(int strategy)`. Các strategy thường dùng:

| Strategy | Mục tiêu |
| --- | --- |
| `DEFAULT_STRATEGY` | Lựa chọn mặc định cho dữ liệu tổng quát |
| `FILTERED` | Có thể hữu ích với một số dữ liệu đã qua bộ lọc hoặc có phân bố ký hiệu đặc thù |
| `HUFFMAN_ONLY` | Sử dụng mã Huffman, không dựa trên tìm kiếm chuỗi lặp kiểu LZ77 như strategy mặc định |

Ví dụ:

~~~java
Deflater encoder = new Deflater(Deflater.BEST_SPEED);
try {
    encoder.setStrategy(Deflater.FILTERED);
    // Nén dữ liệu phù hợp bằng setInput/deflate/finish.
} finally {
    encoder.end();
}
~~~

Đây là mảnh code minh họa, không nén dữ liệu hoàn chỉnh. Strategy **không thay thế level**: có thể có level cao nhưng strategy không phù hợp khiến kết quả kém hơn kỳ vọng.

Tùy input, thay strategy có thể tăng/giảm tốc độ hoặc output size. Không nên chọn `FILTERED` hay `HUFFMAN_ONLY` chỉ vì tên nghe nhanh hơn hoặc "nén sâu" hơn. Hãy benchmark trên dữ liệu thực và dùng strategy mặc định nếu chưa có lý do rõ ràng.

## 10. Flush mode cũng ảnh hưởng hiệu quả nén

`Deflater` có các overload `deflate(...)` nhận một flush mode, ví dụ:

- `NO_FLUSH`: trạng thái thông thường; ưu tiên tiếp tục khai thác dữ liệu có sẵn trong stream.
- `SYNC_FLUSH`: làm output có thể được đồng bộ/giải nén tới ranh giới phù hợp mà không bắt buộc kết thúc stream.
- `FULL_FLUSH`: ngoài việc flush, reset thông tin nén cần thiết để bên giải nén có thể khôi phục từ ranh giới thích hợp; có thể làm giảm tỷ lệ nén.

Đây là **điều khiển quá trình streaming**, không phải ba mức nén mới. Flush quá thường xuyên tạo thêm overhead và giảm khả năng khai thác dữ liệu lặp xuyên ranh giới chunk.

Một số ứng dụng truyền dữ liệu realtime muốn giảm độ trễ bằng flush thường xuyên; ứng dụng lưu trữ tệp lớn muốn size tốt hơn thường tránh flush không cần thiết. Việc lựa chọn phải dựa vào giao thức phía nhận, latency mục tiêu và benchmark.

**Phân biệt `finish()` với flush:** `finish()` báo chấm dứt input và cuối stream. `SYNC_FLUSH`/`FULL_FLUSH` phục vụ tạo điểm đồng bộ trong **một stream còn tiếp tục**; chúng không thay thế `finish()`.

## 11. Dictionary là một cơ chế khác

Với dữ liệu có các mẫu lặp nhỏ và biết trước, DEFLATE hỗ trợ **preset dictionary** ở những dạng stream phù hợp. Phía nén có thể dùng `Deflater.setDictionary(...)`; phía giải nén có thể gặp `Inflater.needsDictionary()` rồi cần gọi `Inflater.setDictionary(...)` với đúng dữ liệu từ điển.

Việc đặt mức nén 9 **không tự truyền dictionary** cho decoder. Nếu decoder thiếu dictionary cần thiết, một luồng có thể không được giải nén dù payload không bị hỏng.

Trong thiết kế giao thức:

1. Xác định dictionary có dùng hay không.
2. Phía nhận phải biết cùng phiên bản dictionary, bằng cấu hình chung hoặc metadata/giao thức riêng.
3. Xử lý `needsDictionary()` rõ ràng.
4. Đừng kỳ vọng chiến lược dictionary áp dụng được nguyên vẹn cho mọi container/định dạng; ví dụ ZIP entry thông thường có những ràng buộc riêng.

Preset dictionary là tối ưu nâng cao, không phải yêu cầu mặc định của phần lớn nhu cầu nén Java.

## 12. DeflaterOutputStream và các API mức cao

Nếu chỉ muốn nén dữ liệu ghi ra stream, `DeflaterOutputStream` giúp quản lý chu trình input/output:

~~~java
import java.io.ByteArrayOutputStream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

ByteArrayOutputStream destination = new ByteArrayOutputStream();
Deflater encoder = new Deflater(Deflater.BEST_SPEED);

try (DeflaterOutputStream compressed =
        new DeflaterOutputStream(destination, encoder)) {
    compressed.write(payload);
}

// Dữ liệu nén có trong destination.toByteArray().
// Quản lý vòng đời Deflater theo hợp đồng của stream/constructor.
~~~

Đây là **mảnh code minh họa** dùng biến `payload` phải được tạo trước; nó không phải một chương trình độc lập. Khi tự truyền object Deflater cho stream, hãy xác định ai chịu trách nhiệm đóng/`end()` theo overload đang dùng, tránh bỏ quên native resource. `close()` hoặc `finish()` trên stream phải hoàn tất output nén trước khi đọc kết quả cuối.

Để giải nén stream zlib tương ứng, có `InflaterInputStream`. Với định dạng gzip hãy dùng `GZIPOutputStream`/`GZIPInputStream`. Với định dạng ZIP, dùng `ZipOutputStream` hoặc `ZipFile`/`ZipInputStream`.

### Điều khiển mức nén khi đóng gói ZIP

`ZipOutputStream` có `setLevel(int)` để chọn mức nén của các **entry DEFLATED tiếp theo** theo ngữ nghĩa API. Nó khác với việc tạo một `Deflater` độc lập rồi ghi raw bytes thẳng vào archive.

Một ZIP archive còn có entry names, timestamps, metadata, CRC và central directory. `Deflater` đơn thuần không tạo các cấu trúc đó. Đối với dữ liệu chỉ cần nén thành một blob zlib, `Deflater` phù hợp; đối với nhiều file trong cùng archive, hãy dùng API archive.

## 13. An toàn dữ liệu và hiệu năng khi giải nén

Nén mạnh không đồng nghĩa giải nén an toàn với dữ liệu không tin cậy. Một payload compressed nhỏ có thể tạo output cực lớn. Nếu ứng dụng nhận dữ liệu nén từ bên ngoài, cần giới hạn:

- **Kích thước output sau giải nén** tính bằng byte, không chỉ giới hạn compressed input.
- **Thời gian/CPU** tối đa cho việc giải nén theo chính sách.
- **Dung lượng bộ nhớ**, kích thước các buffer và số tác vụ đồng thời.
- **Số luồng hoặc entry archive** được phép xử lý nếu input là container ZIP.
- **Độ sâu giải nén lồng nhau** nếu giao thức chấp nhận nhiều lớp nén.

Ví dụ ở trên giới hạn output ở 1.000.000 byte. Trong hệ thống thực tế, ngưỡng phụ thuộc domain, có thể phải dùng streaming ra nơi lưu trữ và dừng ngay khi vượt quota thay vì giữ toàn bộ output trong RAM.

Kiểm tra kết quả `Inflater.finished()`. Nếu hết input mà chưa finished, có thể dữ liệu bị cắt cụt; nếu `DataFormatException` thì input/định dạng không hợp lệ. Ngoài ra, nếu giao thức yêu cầu **toàn bộ compressed buffer phải được tiêu thụ**, kiểm tra `getRemaining()` hoặc kiểm tra trailer/container theo chuẩn đang dùng; đừng bỏ qua byte dư tùy tiện.

**Nén không phải bảo mật.** DEFLATE không mã hóa và không xác thực người gửi. Khi cần confidentiality/integrity, kết hợp giao thức mã hóa/xác thực riêng, không kỳ vọng mức nén cao giúp che giấu nội dung.

Trong giao thức có bí mật và dữ liệu do kẻ tấn công điều khiển nằm chung trong một ngữ cảnh nén, kích thước output còn có thể làm lộ thông tin; hãy đánh giá rủi ro compression side-channel theo kiến trúc cụ thể.

## 14. Cách chọn level trong ứng dụng

| Nhu cầu | Điểm khởi đầu nên thử | Điều vẫn phải đo |
| --- | --- | --- |
| Độ trễ thấp, CPU giới hạn | `BEST_SPEED` | Latency, CPU, số byte truyền |
| File lưu trữ lớn và ít thay đổi | `BEST_COMPRESSION` hoặc level trung bình-cao | Thời gian nén, tỉ lệ nén, chi phí lưu trữ |
| Dữ liệu đã nén hoặc khó nén | Level thấp hoặc bỏ qua nén | Overhead, kích thước có tăng không |
| Cân bằng thông thường | `DEFAULT_COMPRESSION` | Dữ liệu thật, khả năng CPU của hệ thống |
| Streaming realtime | Level thấp/trung bình và flush có chủ đích | Độ trễ từng chunk, overhead flush |
| Đóng gói nhiều file | API ZIP với level cho entry phù hợp | Metadata/CRC/archive, CPU và size từng entry |

Đây là **điểm bắt đầu để thử**, không phải kết luận tuyệt đối. Nên đo trên dữ liệu đại diện: tổng byte trước/sau, thời gian nén, thời gian giải nén, RAM peak và tính đúng đắn của round-trip.

Ví dụ với log văn bản lặp lại, level cao có thể giảm dung lượng đáng kể; với JPEG, MP4 hoặc dữ liệu đã nén, level cao có thể ít lợi ích mà tăng CPU. Bỏ qua nén hoàn toàn ở tầng ứng dụng đôi khi tiết kiệm tài nguyên hơn.

## 15. Lỗi thường gặp

**Lỗi 1 — Nghĩ Inflater cần được cấu hình cùng level.** Decoder không có tham số level đối xứng. Chọn `Inflater` đúng **format wrapper và dictionary** mới là điều quan trọng.

**Lỗi 2 — Nghĩ level 9 luôn cho output nhỏ hơn level 1.** Không có đảm bảo chung. Phải đo output size trên dữ liệu cụ thể.

**Lỗi 3 — Gọi finish() rồi không drain hết output.** `finish()` không tự xuất toàn bộ byte; tiếp tục `deflate(...)` đến `finished()`.

**Lỗi 4 — Chỉ gọi deflate/inflate một lần.** Buffer đích có thể nhỏ hơn dữ liệu. Phải lặp theo trạng thái và số byte trả về.

**Lỗi 5 — Nhầm zlib wrapper với gzip hoặc ZIP archive.** `new Deflater(level)` không tạo file `.gz` hoặc `.zip` hoàn chỉnh.

**Lỗi 6 — Một bên nowrap=true, bên kia nowrap=false.** Hai phía dùng định dạng không tương thích có thể giải nén thất bại.

**Lỗi 7 — Coi NO_COMPRESSION là giữ nguyên từng byte input.** Dù không nén payload, stream vẫn có framing/overhead.

**Lỗi 8 — Quên end().** `Deflater` và `Inflater` có thể nắm tài nguyên native; dùng `finally` hoặc cấu trúc lifecycle phù hợp.

**Lỗi 9 — Không xử lý needsInput/needsDictionary.** Decoder có thể cần thêm compressed input hoặc dictionary; quay vòng inflate khi liên tục trả 0 sẽ treo CPU.

**Lỗi 10 — Giải nén input không tin cậy không giới hạn kích thước.** Nguy cơ decompression bomb gây cạn RAM/CPU.

**Lỗi 11 — Flush mọi chunk để mong file nhỏ hơn.** Flush thường xuyên tạo overhead, có thể giảm hiệu quả nén.

**Lỗi 12 — Cho rằng mức nén thay thế checksum hoặc encryption.** Tính bảo mật và độ toàn vẹn thuộc tầng khác; wrapper như zlib chỉ có kiểm tra lỗi dữ liệu ở mức định dạng, không phải mã xác thực an toàn trước tấn công.

## 16. Tự kiểm tra

1. `BEST_SPEED` và `BEST_COMPRESSION` có giá trị bao nhiêu? **1** và **9**.
2. `DEFAULT_COMPRESSION` có giá trị bao nhiêu? **-1**, biểu diễn cấu hình mặc định.
3. `NO_COMPRESSION` có trả lại đúng nguyên input bytes không? **Không nhất thiết**, vì output vẫn có framing.
4. `Inflater` có cần biết level đã dùng khi nén? **Không**.
5. Vì sao phải lặp `deflate` sau `finish()`? **Vì output nén có thể còn trong compressor**, chưa được drain hết.
6. `needsDictionary()` có nghĩa gì? **Input nén cần một preset dictionary**, không phải yêu cầu thử level lớn hơn.
7. `nowrap=true` có tạo file gzip không? **Không**; nó chọn raw DEFLATE không có zlib wrapper, còn gzip có header/trailer riêng.
8. `setStrategy(HUFFMAN_ONLY)` có giống `setLevel(9)` không? **Không**; strategy và level là hai điều khiển khác nhau.
9. Nén xong 100 KiB thành 4 KiB có chứng minh giải nén luôn dưới 100 KiB RAM không? **Không**; còn nhiều overhead và cần giới hạn output thực tế.
10. Một luồng bị cắt cụt khiến `Inflater.needsInput() == true` nhưng chưa `finished()`. Nên làm gì? **Thử đọc thêm nếu nguồn còn dữ liệu; nếu EOF thật thì báo lỗi input chưa hoàn chỉnh**.
11. Nếu muốn archive nhiều file, có nên tự nối nhiều output `Deflater` thành file `.zip` không? **Không**; dùng API ZIP tạo entry và metadata hợp lệ.
12. Nếu level 9 nén được ít hơn chỉ 1% nhưng tốn CPU gấp nhiều lần, lựa chọn thực tế ra sao? **Đo theo ngân sách CPU, latency, băng thông và chi phí lưu trữ của ứng dụng**, không mặc định level cao nhất là tốt nhất.

**Ghi nhớ:** `Deflater` điều khiển quá trình nén DEFLATE, trong đó **level 0–9 hoặc DEFAULT_COMPRESSION** chủ yếu điều chỉnh đánh đổi tốc độ/khả năng giảm byte. `Inflater` giải nén luồng hợp lệ **không cần biết level**. Ngoài level còn có strategy, flush, dictionary và format wrapper, mỗi yếu tố có hợp đồng riêng. Luôn hoàn tất chu trình `setInput → deflate → finish/finished → end`, giải nén có giới hạn và xác minh round-trip; không lấy mức nén làm cam kết kích thước, hiệu năng hay bảo mật.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="compression-and-archives-010">CRC32, Adler32 và giới hạn của checksum</a>

<details>
<summary>Xem chi tiết</summary>

# CRC32, Adler32 và giới hạn của checksum trong Java I/O

## 1. Checksum dùng để làm gì?

**Checksum** là một giá trị ngắn được tính từ dãy byte nhằm hỗ trợ phát hiện dữ liệu bị thay đổi trong lúc truyền, lưu trữ hoặc giải nén. Thay vì so sánh lại từng byte với bản gốc, bên nhận có thể tính checksum từ dữ liệu thực nhận rồi so với giá trị được lưu hoặc gửi trước đó.

Ví dụ:

~~~text
Người gửi:   "HELLO" --[tính checksum]--> số 32 bit X
               |                           |
               +----------- truyền --------+
                                           |
Người nhận:  "HELXO" --[tính checksum]--> số 32 bit Y
                                           |
                                 X != Y => phát hiện sai khác

Nếu X == Y: dữ liệu CÓ THỂ giống, nhưng KHÔNG được
             chứng minh chắc chắn giống dữ liệu gốc.
~~~

Trong Java Core I/O, `CRC32` và `Adler32` là hai checksum 32 bit thuộc gói `java.util.zip`. Chúng thường xuất hiện trong ngữ cảnh nén và đóng gói vì các định dạng như ZIP, GZIP và zlib dùng checksum để kiểm tra dữ liệu đã giải nén.

Ba ý cần nắm:

1. **Checksum hoạt động trên byte**, nên mã hóa ký tự, thứ tự byte và phạm vi byte đem tính phải được hai phía thống nhất.
2. **CRC32 và Adler32 phát hiện nhiều lỗi vô tình**, nhưng vì chỉ có 32 bit kết quả nên luôn tồn tại va chạm (*collision*): dữ liệu khác nhau nhưng checksum trùng nhau.
3. **Checksum không phải chữ ký số, HMAC hay cơ chế xác thực**. Đối thủ có thể sửa dữ liệu và tính lại checksum tương ứng, đặc biệt khi giá trị checksum nằm cùng một tệp không được bảo vệ.

## 2. So sánh CRC32 và Adler32

| Tiêu chí | CRC32 | Adler32 |
| --- | --- | --- |
| Lớp trong JDK | `java.util.zip.CRC32` | `java.util.zip.Adler32` |
| Độ dài giá trị | 32 bit | 32 bit |
| Cơ chế chính | Phép chia đa thức trên GF(2), triển khai phổ biến bằng bảng tra cứu hoặc phần cứng. | Hai tổng tích lũy modulo 65521, ghép thành số 32 bit. |
| Đặc tính | Được thiết kế để phát hiện nhiều kiểu lỗi bit, nhất là các mẫu lỗi theo cấu trúc truyền dữ liệu. | Công thức đơn giản, thường nhanh trên một số môi trường; tính phát hiện lỗi trên thông điệp ngắn có thể yếu hơn CRC32. |
| Liên hệ định dạng | ZIP entry và GZIP trailer dùng CRC32 kiểu IEEE. | zlib wrapper của luồng DEFLATE dùng Adler-32. |
| Java API | `update(...)`, `getValue()`, `reset()` | Cùng hợp đồng `Checksum`. |
| Bảo vệ chống sửa cố ý | Không | Không |

**Không có thuật toán nào “mạnh như SHA-256” chỉ vì cho ra số trông ngẫu nhiên.** Giá trị 32 bit không đủ làm định danh duy nhất hay kiểm tra tính toàn vẹn trước tác nhân có chủ đích; mục tiêu của các thuật toán này là **phát hiện sai hỏng thông thường** với chi phí thấp.

### 2.1. Checksum khác hash, CRC và chữ ký như thế nào?

Thuật ngữ *hash* đôi khi được dùng rộng cho mọi hàm ánh xạ dữ liệu sang giá trị ngắn. Nhưng khi thiết kế hệ thống, cần phân biệt:

- **Checksum không mật mã:** CRC32, Adler32; thích hợp đối chiếu dữ liệu sau truyền/giải nén.
- **Cryptographic hash:** SHA-256, SHA-512; chống tìm va chạm tốt hơn rất nhiều so với checksum 32 bit, nhưng tự nó **không chứng minh ai tạo dữ liệu** nếu attacker được sửa cả dữ liệu lẫn hash.
- **HMAC:** ví dụ HmacSHA256, dùng khóa bí mật chung để kiểm tra nội dung và nguồn phát biết khóa; cần quản lý khóa an toàn.
- **Digital signature:** dùng khóa riêng để ký và khóa công khai để xác minh; phục vụ xác thực nguồn gốc và phát hiện sửa đổi theo mô hình tin cậy phù hợp.

Nếu cần bảo vệ dữ liệu truyền qua mạng trước đối thủ, dùng các giao thức có xác thực và bảo vệ toàn vẹn như TLS, không chỉ gắn CRC32 vào cuối packet.

## 3. CRC32 hoạt động như thế nào?

CRC là viết tắt của **Cyclic Redundancy Check**. CRC coi chuỗi bit như một đa thức trên trường nhị phân `GF(2)` và thực hiện phép chia theo một đa thức sinh để lấy phần dư. Các phép cộng/trừ ở đây tương ứng phép XOR.

`java.util.zip.CRC32` cài CRC-32 được dùng phổ biến trong ZIP/GZIP (thường gọi CRC-32/ISO-HDLC, CRC-32/IEEE). Các tham số thông dụng của biến thể này gồm:

- Độ rộng: **32 bit**.
- Đa thức ở dạng chuẩn: `0x04C11DB7`; dạng phản chiếu dùng trong triển khai theo bit thấp trước: `0xEDB88320`.
- Giá trị khởi tạo nội bộ: `0xFFFFFFFF`.
- Input và output được phản chiếu theo biến thể CRC-32 thông dụng.
- Kết quả cuối được XOR với `0xFFFFFFFF`.

Không phải thuật toán nào có tên “CRC32” đều dùng cùng **tham số CRC**. Một biến thể khác như CRC32C (Castagnoli) có đa thức khác và thường cho checksum khác ngay cả khi byte đầu vào giống nhau. Khi đọc đặc tả giao thức, cần xác định **chính xác CRC variant**, không chỉ tên “CRC”.

### 3.1. Dữ liệu kiểm thử chuẩn

Với dãy byte ASCII:

~~~text
123456789
~~~

`CRC32` theo JDK cho ra:

~~~text
0xCBF43926
~~~

Đây là *test vector* rất phổ biến để phát hiện nhầm polynomial, initial value, reflection hoặc final XOR. Trong Java, `getValue()` trả `long` không âm chứa phần thấp 32 bit; dùng `%08X` nếu muốn hiển thị đúng tám chữ số hex có các số 0 đầu.

### 3.2. Khả năng phát hiện lỗi của CRC

CRC với đa thức được lựa chọn phù hợp có những tính chất phát hiện lỗi bit tốt hơn cách cộng byte đơn giản, nhất là trong các giao thức truyền/lưu trữ có lỗi dạng *burst*. Nhưng mức bảo đảm cụ thể còn phụ thuộc:

- Đa thức/biến thể CRC.
- Độ dài thông điệp tối đa.
- Phân bố lỗi; lỗi bit độc lập khác lỗi có cấu trúc.
- Số bit thay đổi và khoảng cách Hamming tối thiểu của bộ mã trên chiều dài thông điệp đang xét.

Không được mặc định “CRC32 luôn phát hiện mọi lỗi ba bit” hay “cứ đổi ít bit là chắc chắn phát hiện”, nếu chưa chứng minh thuộc tính đó cho đúng variant và chiều dài dữ liệu. CRC không được thiết kế để chống kẻ sửa dữ liệu có chủ đích.

## 4. Adler32 hoạt động như thế nào?

Adler-32 sử dụng **hai tổng modulo số nguyên tố 65521** trên các byte không dấu. Khởi tạo:

~~~text
A = 1
B = 0
MOD = 65521
~~~

Với mỗi byte có giá trị từ 0 đến 255:

~~~text
A = (A + byte) mod 65521
B = (B + A) mod 65521

Adler32 = (B << 16) | A
~~~

Trong mã Java minh họa, cần xử lý byte không dấu bằng `byteValue & 0xFF`. Hai tổng tích lũy bị ảnh hưởng bởi thứ tự byte; cùng tập byte đảo thứ tự có thể cho kết quả khác.

Ví dụ một byte ASCII `'A'` có giá trị 65:

~~~text
A = (1 + 65) mod 65521 = 66 = 0x0042
B = (0 + 66) mod 65521 = 66 = 0x0042
Adler32 = 0x00420042
~~~

Với chuỗi ASCII `123456789`:

~~~text
Adler32 = 0x091E01DE
~~~

Adler32 được chọn vì dễ tính nhanh, nhưng nó có phân bố kém hơn CRC32 trên nhiều thông điệp rất ngắn. Không kết luận nó luôn nhanh hơn trong mọi JDK/CPU hiện đại: hiệu năng phụ thuộc cách triển khai, SIMD/phần cứng, kích thước block và bộ nhớ.

### 4.1. Tại sao A bắt đầu bằng 1?

Nếu cả A và B bắt đầu bằng 0, một chuỗi chỉ gồm các byte 0 sẽ cho checksum 0 ở mọi độ dài và có nhiều tình huống suy biến không mong muốn. Khởi tạo A=1 giúp thay đổi đặc tính đó; đây là tham số của thuật toán Adler-32 chuẩn chứ không phải tùy chọn người gọi Java tự sửa.

## 5. API `Checksum` trong Java

Các lớp `CRC32` và `Adler32` triển khai interface `java.util.zip.Checksum`. Các phương thức thường dùng:

| API | Ý nghĩa |
| --- | --- |
| `update(int b)` | Cập nhật với **một byte**, lấy 8 bit thấp của tham số `int`. |
| `update(byte[] b)` | Cập nhật trên toàn bộ mảng byte. |
| `update(byte[] b, int off, int len)` | Cập nhật trên đoạn mảng từ offset, dài len byte. |
| `update(ByteBuffer buffer)` | Cập nhật với phần byte còn lại; thay đổi position của buffer theo hợp đồng API. |
| `getValue()` | Lấy checksum hiện tại kiểu `long`; với CRC32/Adler32, giá trị nằm trong dải 32 bit không dấu. |
| `reset()` | Đặt checksum về trạng thái khởi tạo để tính dữ liệu khác. |

Ví dụ nhanh:

~~~java
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

byte[] bytes = "abc".getBytes(StandardCharsets.US_ASCII);
CRC32 crc = new CRC32();
crc.update(bytes, 0, bytes.length);
long result = crc.getValue();
System.out.printf("CRC32=%08X%n", result);
~~~

Đây là **mảnh mã đặt trong một phương thức**. `getValue()` không reset trạng thái: có thể lấy giá trị tạm thời sau mỗi chunk. `update(...)` nhiều lần trên các đoạn liên tiếp tương đương tính trên toàn bộ chuỗi byte theo cùng thứ tự.

**Lưu ý quan trọng về `update(int)`:** `crc.update(256)` không nạp bốn byte của một số nguyên 256; theo hợp đồng, nó chỉ đưa byte thấp `0x00`. Muốn checksum của dữ liệu nhị phân chứa một số nguyên 32 bit, hãy quyết định byte order, chuyển thành đúng bốn byte, rồi cập nhật với mảng/ByteBuffer tương ứng.

## 6. Ví dụ Java 17+ hoàn chỉnh: CRC32, Adler32, chia chunk và phát hiện sửa đổi

Ví dụ dùng test vector `123456789`, cố ý đọc qua `ByteArrayInputStream` với buffer 4 byte để có ba chunk: 4 + 4 + 1. Sau đó đối chiếu giá trị khi tính một lần với giá trị khi tính dần, kiểm tra `reset()` và sửa một byte nhằm mô phỏng lỗi ngẫu nhiên.

Lưu file `ChecksumDemo.java`:

~~~java
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.Adler32;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

public class ChecksumDemo {

    static long calculate(Checksum checksum, byte[] data, int chunkSize)
            throws IOException {
        checksum.reset();

        try (ByteArrayInputStream input = new ByteArrayInputStream(data)) {
            byte[] buffer = new byte[chunkSize];
            int n;
            while ((n = input.read(buffer)) != -1) {
                checksum.update(buffer, 0, n);
            }
        }
        return checksum.getValue();
    }

    static String hex32(long value) {
        return String.format(Locale.ROOT, "%08X", value);
    }

    public static void main(String[] args) throws IOException {
        byte[] original =
                "123456789".getBytes(StandardCharsets.US_ASCII);

        long crc = calculate(new CRC32(), original, 4);
        long adler = calculate(new Adler32(), original, 4);

        System.out.println("bytes=" + original.length);
        System.out.println("crc32=" + hex32(crc));
        System.out.println("adler32=" + hex32(adler));

        // Chunk khác nhau, nhưng thứ tự và nội dung byte giữ nguyên.
        long crcOneChunk =
                calculate(new CRC32(), original, original.length);
        long adlerOneByteAtATime =
                calculate(new Adler32(), original, 1);

        System.out.println("crcChunksEqual=" + (crc == crcOneChunk));
        System.out.println("adlerChunksEqual=" +
                (adler == adlerOneByteAtATime));

        byte[] changed = original.clone();
        changed[0] = (byte) 'X';

        long crcChanged = calculate(new CRC32(), changed, 4);
        long adlerChanged = calculate(new Adler32(), changed, 4);

        System.out.println("crcDetectsChange=" + (crc != crcChanged));
        System.out.println("adlerDetectsChange=" + (adler != adlerChanged));

        CRC32 reusable = new CRC32();
        reusable.update(changed, 0, changed.length);
        reusable.reset();
        reusable.update(original, 0, original.length);

        System.out.println("resetReusesInstance=" +
                (reusable.getValue() == crc));
    }
}
~~~

Lệnh biên dịch và chạy:

~~~bash
javac ChecksumDemo.java
java ChecksumDemo
~~~

**Stdout xác định cho dữ liệu trong chương trình:**

~~~text
bytes=9
crc32=CBF43926
adler32=091E01DE
crcChunksEqual=true
adlerChunksEqual=true
crcDetectsChange=true
adlerDetectsChange=true
resetReusesInstance=true
~~~

### 6.1. Giải thích code

1. `ByteArrayInputStream` trả dữ liệu từ mảng có chín byte. Buffer tạm 4 byte được đọc ba lượt; mỗi lần gọi `checksum.update(buffer, 0, n)` **chỉ sử dụng n byte thực nhận**.
2. `Checksum.reset()` trước khi xử lý bảo đảm không trộn dữ liệu của lượt tính trước vào lượt mới.
3. `crc32=CBF43926` và `adler32=091E01DE` khớp test vector chuẩn cho chuỗi ASCII `123456789`.
4. Cùng nội dung được đưa vào `update` theo các kích thước chunk khác nhau nhưng cùng thứ tự nên kết quả bằng nhau.
5. Đổi ký tự đầu tiên từ `'1'` sang `'X'` làm cả hai checksum trong **trường hợp cụ thể này** thay đổi. Điều đó minh họa phát hiện lỗi, **không phải chứng minh mọi thay đổi byte đều được phát hiện**.
6. `resetReusesInstance=true` xác nhận có thể dùng lại đối tượng `CRC32` sau `reset` mà không dính dữ liệu đã tính.

Trong ví dụ, hàm `calculate` nhận một đối tượng `Checksum` bất kỳ tương thích với interface này. Nhờ đó có thể dùng cùng logic streaming cho `CRC32` và `Adler32`, tránh viết hai vòng lặp khác nhau.

## 7. Tính checksum của một file lớn mà không nạp hết vào RAM

Đối với file thực tế, tránh `Files.readAllBytes(path)` khi tệp rất lớn vì nó cần bộ nhớ tương ứng toàn bộ nội dung. Tính theo luồng cho phép sử dụng một buffer cố định.

~~~java
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.Checksum;
import java.util.zip.CRC32;

public final class FileChecksumExample {
    public static long crc32(Path file) throws IOException {
        Checksum checksum = new CRC32();
        byte[] chunk = new byte[8192];

        try (InputStream input = Files.newInputStream(file)) {
            int n;
            while ((n = input.read(chunk)) != -1) {
                if (n > 0) {
                    checksum.update(chunk, 0, n);
                }
            }
        }

        return checksum.getValue();
    }
}
~~~

Đoạn này là **một lớp utility có thể biên dịch** sau khi lưu đúng tên `FileChecksumExample.java`; chưa có `main` nên không có lệnh `java FileChecksumExample` để chạy độc lập. Muốn dùng, gọi `FileChecksumExample.crc32(path)` trong chương trình phù hợp.

Quan trọng:

- Dùng `n` trả về thay vì luôn truyền `chunk.length`. Lượt đọc cuối có thể ít hơn 8192 byte; phần còn lại trong mảng là dữ liệu cũ.
- Bộ nhớ tạm là `O(chunkSize)`, không tăng theo kích thước file.
- Checksum được tính trên **những byte thực đọc được**. Nếu file bị thay đổi đồng thời khi đọc, kết quả có thể là dữ liệu trộn từ nhiều thời điểm; checksum không tạo snapshot nhất quán.
- Hàm tính lại checksum của cùng một tệp nhiều lần chỉ có giá trị khi dữ liệu được giữ ổn định hoặc quy trình chấp nhận điều kiện thay đổi.
- Nếu ứng dụng cần kiểm tra tệp được tải về, phải có giá trị checksum **kỳ vọng** đáng tin hoặc một nguồn xác thực khác. Việc vừa đọc tệp vừa tự tính checksum rồi lưu cùng chỗ không cho biết tệp đã đúng so với bản gốc.

## 8. Checksum và nén/đóng gói: ZIP, GZIP và zlib

Các định dạng nén thường thêm metadata để phát hiện dữ liệu hỏng sau quá trình giải nén. CRC32 và Adler32 **không phải thuật toán nén**: chúng không làm dữ liệu nhỏ hơn; chúng chỉ tính dấu kiểm ngắn.

### 8.1. ZIP: CRC32 cho từng entry

Trong định dạng ZIP, mỗi entry có metadata gồm CRC32 của **dữ liệu chưa nén**, kích thước đã nén và kích thước chưa nén. Java hỗ trợ ZIP qua `ZipOutputStream`, `ZipInputStream`, `ZipFile` và `ZipEntry`.

~~~text
archive.zip
   |
   +-- notes.txt
   |     CRC32 = checksum của bytes notes.txt sau giải nén
   |
   +-- image.bin
         CRC32 = checksum của bytes image.bin sau giải nén
~~~

Với `ZipOutputStream` dùng phương thức DEFLATED, JDK có thể tính CRC32 của dữ liệu entry trong lúc ghi. Với entry kiểu STORED (không nén), caller phải cung cấp metadata cần thiết như size/CRC trước khi ghi entry theo yêu cầu API.

`ZipInputStream` kiểm tra CRC/size của entry khi đã đọc hết nội dung entry và hoàn tất kiểm tra entry tương ứng. Nếu chỉ đọc một phần rồi bỏ dở, không được mặc định quá trình kiểm tra dữ liệu toàn entry đã diễn ra. `ZipFile` cung cấp truy cập ngẫu nhiên tới entry, nhưng nếu quy trình yêu cầu xác minh nghiêm ngặt, ứng dụng nên chủ động đối chiếu dữ liệu đã đọc với checksum kỳ vọng của entry theo hợp đồng API đang dùng.

### 8.2. Ví dụ tạo ZIP và tự đối chiếu CRC32

~~~java
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

byte[] text = "hello".getBytes(StandardCharsets.UTF_8);
CRC32 expected = new CRC32();
expected.update(text);

ByteArrayOutputStream archive = new ByteArrayOutputStream();
try (ZipOutputStream zip = new ZipOutputStream(archive)) {
    ZipEntry entry = new ZipEntry("hello.txt");
    zip.putNextEntry(entry);
    zip.write(text);
    zip.closeEntry();
}

System.out.printf("uncompressed-crc=%08X%n", expected.getValue());
~~~

Đây là **mảnh minh họa đặt trong một phương thức**, có các import cần thiết. `expected` được tính từ byte gốc; `ZipOutputStream` tự tạo metadata CRC phù hợp cho entry DEFLATED. Không cần tự thêm checksum bytes vào nội dung `hello.txt`. Ví dụ không in kích thước archive vì nó có thể phụ thuộc metadata và lựa chọn của runtime.

### 8.3. GZIP dùng CRC32

Luồng GZIP có trailer bao gồm CRC32 của **dữ liệu chưa nén** và giá trị ISIZE (kích thước chưa nén modulo `2^32`), với cách lưu các trường nhiều byte theo định dạng GZIP. `GZIPOutputStream` tạo luồng GZIP; `GZIPInputStream` giải nén và kiểm tra checksum/trailer khi đọc đủ.

Không lấy CRC32 của **file .gz đã nén** rồi so với giá trị CRC lưu trong GZIP trailer. Trailer bảo vệ dữ liệu **trước khi nén**; hai dãy byte đó khác nhau.

### 8.4. zlib wrapper dùng Adler32

Một luồng theo định dạng zlib thường gồm header, dữ liệu DEFLATE và trường **ADLER32** của dữ liệu chưa nén. Trong Java, `DeflaterOutputStream`/`InflaterInputStream` có thể làm việc với dạng được `Deflater`/`Inflater` cấu hình; phân biệt:

- **Raw DEFLATE**: luồng nén lõi, không tự kèm trường checksum của wrapper.
- **zlib wrapper**: chứa metadata wrapper và Adler32.
- **GZIP wrapper**: chứa metadata GZIP và CRC32; không đổi chỗ với Adler32.
- **ZIP archive**: cấu trúc nhiều entry, mỗi entry có CRC32 và metadata riêng.

Lẫn lộn *wrapper format* thường tạo lỗi “incorrect header check” hoặc “incorrect data check” khi giải nén. Phải chọn đúng lớp/định dạng, không chỉ đoán theo tên “DEFLATE”.

## 9. Checksum có giới hạn gì?

### 9.1. Không thể bảo đảm dữ liệu khác nhau sẽ cho checksum khác

CRC32 và Adler32 có **2^32** giá trị đầu ra tối đa. Trong khi đó số dãy byte có thể có dài hơn vài byte lớn hơn rất nhiều, nên theo nguyên lý Dirichlet chắc chắn tồn tại hai đầu vào khác nhau cùng checksum.

~~~text
Dữ liệu A  -> CRC32 X
Dữ liệu B  -> CRC32 X   (collision)
A != B nhưng CRC32(A) == CRC32(B)
~~~

Đối với dữ liệu lỗi ngẫu nhiên được mô hình hóa gần đồng đều, xác suất trùng một checksum 32 bit cố định thường được mô tả xấp xỉ **1 / 2^32**. Nhưng đây **không phải một bảo đảm độc lập với dữ liệu, loại lỗi hoặc cơ chế tạo lỗi**. Cụ thể với CRC, các đặc tính phát hiện lỗi phụ thuộc cấu trúc đa thức và độ dài thông điệp.

Nếu tạo nhiều đối tượng rồi gán checksum làm ID duy nhất, nguy cơ có **ít nhất một cặp va chạm** tăng nhanh do hiện tượng *birthday paradox*. Với output 32 bit gần đồng đều, ở quy mô khoảng vài chục nghìn đối tượng nguy cơ đã không còn nhỏ; ước lượng gần 50% khi có khoảng 77 nghìn mẫu độc lập. CRC/Adler thực tế không bắt buộc tuân theo giả định phân bố đều trong mọi tập dữ liệu.

### 9.2. Không chống sửa đổi có chủ đích

Nếu attacker sửa một file ZIP và cập nhật metadata CRC32 tương ứng, phép kiểm tra CRC có thể vẫn qua. Không có bí mật nào trong công thức CRC32 hay Adler32: ai biết byte dữ liệu đều có thể tính lại checksum.

~~~text
Tệp gốc + CRC32(gốc)
          |
       attacker sửa payload
          |
Tệp đã sửa + CRC32(mới)
          |
       Kiểm tra CRC32 vẫn KHỚP
       nhưng tính toàn vẹn/an toàn KHÔNG được bảo đảm.
~~~

Do đó:

- Với mục tiêu phát hiện lỗi bit do truyền/lưu trữ, CRC32 hoặc Adler32 có thể phù hợp.
- Với chống sửa trái phép, dùng HMAC/digital signature hoặc một giao thức xác thực như TLS, tùy mô hình tin cậy.
- Với gói cài đặt hoặc bản phát hành, kiểm tra signature từ nguồn đáng tin quan trọng hơn việc chỉ đối chiếu checksum được đăng trên cùng kênh không đáng tin.

### 9.3. Không sửa lỗi và không chỉ ra byte hỏng

Checksum thông báo **có khả năng dữ liệu không trùng**, chứ không cho biết byte nào sai hoặc tự sửa dữ liệu. Các mã phát hiện/sửa lỗi như ECC/FEC có mục tiêu và cấu trúc khác.

Một số giao thức kết hợp checksum với cơ chế truyền lại dữ liệu; việc truyền lại là phần của giao thức, không phải khả năng của `CRC32`/`Adler32`.

### 9.4. Không đảm bảo thứ tự hay đúng phạm vi byte

Nếu bên gửi tính CRC của payload nhưng bên nhận tính cả header; hoặc một bên dùng UTF-8, bên kia dùng UTF-16LE; kết quả có thể khác dù nội dung hiển thị giống nhau. Phải thống nhất:

- Dãy byte gốc và charset.
- Bao gồm hay loại trừ header, padding, delimiters, metadata.
- Thứ tự byte và serialization đối với số nguyên.
- Thuật toán/variant và cách biểu diễn trường checksum trong định dạng.
- Thời điểm tính: trên dữ liệu nén hay dữ liệu chưa nén.

Trong ZIP/GZIP/zlib, nên dùng API đúng định dạng và hiểu **checksum của nội dung nào** thay vì tự tính theo một giả định không được tài liệu bảo đảm.

## 10. Khi nào chọn thuật toán nào?

| Tình huống | Lựa chọn hợp lý | Lý do |
| --- | --- | --- |
| Đọc/ghi entry ZIP theo chuẩn | CRC32 qua ZIP API | Metadata ZIP quy định CRC32 cho entry. |
| Đọc/ghi GZIP theo chuẩn | CRC32 qua GZIP API | Trailer GZIP quy định CRC32. |
| Làm việc với zlib wrapper | Adler32 theo định dạng | zlib wrapper sử dụng Adler32 cho dữ liệu chưa nén. |
| Giao thức nội bộ cần phát hiện lỗi ngẫu nhiên, không có adversary | Checksum theo **đặc tả giao thức**, có thể CRC32/CRC32C hoặc loại khác. | Chọn đồng bộ thuật toán và độ dài thông điệp. |
| Cần đối chiếu bản tải khi checksum đến từ cùng kênh đáng tin | SHA-256 hoặc checksum theo đặc tả; vẫn xét nguồn xác thực. | Mức độ rủi ro và cơ chế phân phối quyết định. |
| Cần xác nhận người gửi và nội dung không bị sửa | HMAC hoặc chữ ký số, TLS phù hợp | CRC32 và Adler32 không có khóa/chứng thực. |
| Dùng checksum làm ID duy nhất cho nhiều đối tượng | Không nên | Chỉ 32 bit, va chạm là không tránh khỏi. |
| Dữ liệu có thể bị sửa đồng thời khi đọc | Đảm bảo snapshot/locking/versioning trước | Checksum không giải quyết consistency khi đọc. |

Nếu giao thức yêu cầu **CRC32C**, dùng `java.util.zip.CRC32C` (có từ Java 9), không dùng `CRC32` và hy vọng kết quả tương đương. Trong Java 17, cả hai đều có lớp triển khai riêng.

## 11. Những lỗi lập trình phổ biến

### 11.1. Quên `reset()` khi dùng lại đối tượng

~~~java
CRC32 crc = new CRC32();
crc.update(firstBytes);
long first = crc.getValue();

crc.update(secondBytes); // Tính trên firstBytes + secondBytes.
long combined = crc.getValue();
~~~

Nếu cần CRC riêng cho secondBytes thì phải `crc.reset()` trước khi cập nhật chúng, hoặc tạo một instance mới. `getValue()` chỉ lấy giá trị hiện tại; không làm trạng thái quay về ban đầu.

### 11.2. Luôn cập nhật cả buffer thay vì số byte thực nhận

~~~java
int n = input.read(chunk);
checksum.update(chunk); // Sai nếu n < chunk.length.
~~~

Cách đúng là `checksum.update(chunk, 0, n)` sau khi kiểm tra `n > 0`. Đoạn minh họa trên không phải chương trình độc lập: `input`, `chunk` và `checksum` đã được tạo ở chỗ khác.

Nếu không kiểm tra n, phần mảng không được ghi trong lượt đọc cuối vẫn chứa byte của lượt trước, khiến checksum sai.

### 11.3. So sánh với kết quả âm của int

CRC32 là 32 bit không dấu; Java `int` là signed, trong khi `getValue()` trả `long` trong khoảng 0 tới `0xFFFFFFFFL`. Nếu metadata lưu trong một `int` có bit dấu bật, phải chuyển đúng cách:

~~~java
long unsigned = Integer.toUnsignedLong(storedInt);
boolean matches = (checksum.getValue() == unsigned);
~~~

Không so sánh trực tiếp signed int âm với long không âm mà quên chuyển dạng.

### 11.4. Nhầm CRC của file nén với CRC dữ liệu đã giải nén

Trong ZIP/GZIP, checksum metadata thường được định nghĩa cho **uncompressed bytes**. Nếu gọi `CRC32.update` trên toàn bộ byte của `archive.zip` hoặc `file.gz`, kết quả thường không phải CRC của một ZIP entry hay GZIP trailer.

### 11.5. Nhầm CRC32 với CRC32C

Hai biến thể này có polynomial khác; cùng payload cho checksum khác. Đọc trường checksum 32 bit mà không biết biến thể khiến kiểm tra luôn báo lỗi sai hoặc mã hóa giao thức không tương thích.

### 11.6. Dùng checksum làm bảo mật

Không dùng checksum làm “chữ ký” cho file tải từ nguồn không đáng tin. Cần nguồn tin cậy cho expected value và cơ chế authentication như HMAC hoặc chữ ký số.

### 11.7. Giả định checksum bằng nhau thì bytes chắc chắn bằng nhau

Kết quả bằng nhau **không phải chứng minh đồng nhất**. Với dữ liệu quan trọng, tùy yêu cầu có thể cần kiểm tra độ dài, hash mật mã, so sánh byte-by-byte hoặc signature.

### 11.8. Trộn ByteBuffer state với phạm vi checksum

Khi gọi `checksum.update(ByteBuffer)`, chỉ **các byte còn lại từ position tới limit** được dùng. Nếu buffer vừa được `put` để ghi dữ liệu, cần `flip()` trước khi tính checksum của phần đã ghi; nếu không có thể tính nhầm vùng còn trống. Và vì update có thể tiến vị trí buffer, nếu vẫn cần đọc nội dung đó, hãy dùng `duplicate()` hoặc quản lý position có chủ đích.

### 11.9. Chia dữ liệu thành chunk rồi reset giữa chừng

Nếu muốn checksum toàn file, chỉ `reset()` **một lần trước khi bắt đầu**. Reset ở mỗi chunk khiến kết quả cuối chỉ tương ứng chunk cuối cùng, không còn phản ánh toàn file.

### 11.10. Giải nén ZIP mà không kiểm soát lượng dữ liệu

CRC không ngăn **ZIP bomb** hoặc nội dung có kích thước giải nén cực lớn. Khi nhận archive từ nguồn không đáng tin, cần giới hạn số entry, kích thước tổng sau giải nén, tỉ lệ nén, độ sâu/thời gian xử lý, và kiểm soát đường dẫn entry để tránh Zip Slip. Checksum hợp lệ không có nghĩa file an toàn.

## 12. Bài thực hành và câu hỏi tự kiểm tra

1. **CRC32/Adler32 có phải hai thuật toán nén không?** Không. Chúng là checksum của dãy byte và không làm giảm dung lượng dữ liệu.
2. **Khi tính checksum cho “123456789” dưới dạng ASCII, CRC32 bằng gì?** `CBF43926`.
3. **Adler32 cho test vector đó bằng gì?** `091E01DE`.
4. **Tính CRC theo chunk 4, 4, 1 byte có khác tính một lần 9 byte không?** Không, nếu thứ tự và byte giống nhau, chỉ reset ở đầu.
5. **`getValue()` tự reset checksum không?** Không. Phải gọi `reset()` hoặc tạo instance mới.
6. **`update(0x12345678)` nạp bao nhiêu byte?** Một byte thấp `0x78`, không phải bốn byte.
7. **GZIP trailer dùng checksum gì?** CRC32 của dữ liệu chưa nén.
8. **zlib wrapper dùng checksum gì?** Adler32 của dữ liệu chưa nén.
9. **CRC32 của một ZIP entry được tính trên byte đã nén hay chưa nén?** Dữ liệu **chưa nén** của entry.
10. **Hai tệp có cùng CRC32 có bắt buộc giống nhau không?** Không, luôn có thể có collision.
11. **Một kẻ sửa file rồi tính lại checksum có thể vượt qua kiểm tra checksum không?** Có; vì checksum không có khóa hoặc cơ chế xác thực.
12. **Nếu cần kiểm chứng nguồn phát và chống sửa đổi, nên cân nhắc gì?** HMAC, chữ ký số hoặc giao thức có xác thực như TLS, cùng quy trình quản lý khóa tin cậy.
13. **Vì sao `Files.readAllBytes` không phải cách mặc định cho file rất lớn?** Nó cần bộ nhớ tương ứng toàn nội dung; streaming qua buffer cố định tiết kiệm RAM hơn.
14. **Tại sao đọc file liên tục đang bị writer khác chỉnh sửa có thể cho checksum không đáng tin?** Những byte đọc được có thể thuộc các phiên bản khác nhau; checksum không tạo snapshot.
15. **Có nên dùng CRC32 cho mã nhận diện duy nhất của hàng triệu tệp?** Không, số giá trị 32 bit quá ít, nguy cơ trùng lớn.
16. **CRC32 và CRC32C có hoán đổi được không?** Không, đó là hai biến thể khác nhau.
17. **Đọc một phần ZIP entry rồi dừng có chứng minh checksum entry đã được kiểm tra đầy đủ?** Không. Phải đọc hết và kiểm tra theo hợp đồng của thư viện/định dạng.
18. **Checksum có giúp chống file ZIP chứa payload nguy hiểm không?** Không. Cần validate nội dung, giới hạn tài nguyên và bảo vệ path extraction.

## 13. Tài liệu Java 17 tham khảo

- [Checksum](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/Checksum.html): `update`, `getValue`, `reset`.
- [CRC32](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/CRC32.html) và [CRC32C](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/CRC32C.html): phân biệt hai biến thể CRC.
- [Adler32](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/Adler32.html): checksum Adler-32.
- [ZipInputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/ZipInputStream.html), [ZipOutputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/ZipOutputStream.html), [GZIPInputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/GZIPInputStream.html): thao tác định dạng nén tương ứng.

**Ghi nhớ:** `CRC32` và `Adler32` là checksum 32 bit để phát hiện nhiều lỗi dữ liệu không chủ ý, không phải cơ chế bảo mật. Chúng cùng triển khai `Checksum` nên có thể tính incrementally trên stream; ZIP/GZIP thường dùng CRC32, còn zlib wrapper dùng Adler32. Luôn đối chiếu **đúng dãy byte, đúng variant, đúng expected value**, và dùng HMAC/chữ ký số khi cần chống sửa đổi có chủ đích.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="compression-and-archives-011">Giới hạn dung lượng, số entry và tỷ lệ giải nén</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn dung lượng, số entry và tỷ lệ giải nén — bảo vệ Java ZIP extraction

## 1. Vì sao phải đặt giới hạn khi giải nén?

Trong Java Core I/O, các API như `ZipFile`, `ZipInputStream` và `ZipEntry` cho phép đọc tệp ZIP tương đối dễ dàng. Nhưng một tệp ZIP đến từ nguồn không tin cậy có thể rất nhỏ khi lưu trữ và tạo ra lượng dữ liệu cực lớn, rất nhiều tệp con hoặc cấu trúc thư mục phức tạp khi giải nén.

Một số rủi ro thường gặp:

- **ZIP bomb / decompression bomb:** dữ liệu nén chỉ vài kilobyte hoặc megabyte nhưng có thể nở thành hàng gigabyte, gây cạn dung lượng đĩa, CPU, RAM hoặc thời gian xử lý.
- **Quá nhiều entry:** hàng trăm nghìn tệp nhỏ có thể gây cạn inode, handle, metadata và thời gian tạo tệp, dù tổng byte giải nén không lớn.
- **Một entry quá lớn:** một tệp đơn lẻ vượt ngưỡng nghiệp vụ, khiến hệ thống lưu trữ hoặc xử lý phía sau bị quá tải.
- **Tỷ lệ giải nén quá cao:** một entry rất nhỏ sau nén nhưng có lượng byte không nén lớn bất thường.
- **Zip Slip / path traversal:** entry có tên như `../../outside.txt` cố ghi ra ngoài thư mục được phép.
- **File overwrite, tên trùng, symlink và file system quirks:** gây ghi đè tài liệu đang có hoặc làm đường dẫn cuối cùng trỏ đến vị trí không mong muốn.

**Không có một ngưỡng duy nhất giải quyết mọi rủi ro.** Hệ thống nên đặt giới hạn độc lập về kích thước ZIP đầu vào, số entry, kích thước từng entry sau giải nén, tổng kích thước đã giải nén và tỷ lệ nén; sau đó bổ sung giới hạn tài nguyên và kiểm tra đường dẫn.

Mục tiêu bài này: thiết kế chính sách bảo vệ, hiểu công thức tỷ lệ giải nén, xử lý metadata chưa biết/không đáng tin, áp dụng giới hạn **trong lúc stream byte**, và viết ví dụ Java 17+ chạy độc lập.

## 2. Ba giới hạn chính và cách tính

### 2.1. Dung lượng (size)

Có ít nhất ba con số khác nhau:

1. **Dung lượng ZIP đầu vào:** kích thước tệp archive trên đĩa, ví dụ `Files.size(zipPath)`. Chặn archive đầu vào khổng lồ trước khi đọc sâu.
2. **Dung lượng giải nén của một entry:** số byte thực tế `InputStream.read(...)` tạo ra cho riêng entry đó. Chặn một file quá lớn.
3. **Tổng dung lượng giải nén của archive:** cộng các byte thực tế của mọi entry đã đọc. Chặn trường hợp nhiều file nhỏ, mỗi file trong giới hạn nhưng tổng cộng lại quá lớn.

Ví dụ chính sách **minh họa** cho một dịch vụ nhận tệp ZIP cấu hình:

| Thuộc tính | Giới hạn ví dụ | Mục đích |
| --- | ---: | --- |
| Kích thước archive đầu vào | 4 MiB | Giới hạn tải lên và xử lý tệp ZIP |
| Số entry (gồm cả thư mục) | 100 | Hạn chế số tệp/thư mục và chi phí metadata |
| Dung lượng giải nén một entry | 1 MiB | Không cho một file đơn lẻ quá lớn |
| Tổng byte giải nén | 2 MiB | Không cho tổng dữ liệu bùng nổ |
| Tỷ lệ giải nén tối đa một file | 100:1 | Chặn dữ liệu nở bất thường |

Các con số này **không phải tiêu chuẩn an toàn chung của Java**. Trong thực tế, cần thiết lập theo nghiệp vụ, hạ tầng, quota, SLA, loại file hợp lệ và kết quả benchmark. Nếu dịch vụ nhận bản backup nhiều gigabyte, các ngưỡng ví dụ rõ ràng không phù hợp.

**MiB = 1.048.576 byte** (2^20); không nhầm với MB thập phân = 1.000.000 byte.

### 2.2. Giới hạn số entry

Một entry trong ZIP có thể đại diện cho file hoặc thư mục. Ví dụ:

~~~text
project/
project/docs/
project/docs/readme.md
project/config.json
~~~

Archive trên có **4 entry** nếu cả hai thư mục được ghi thành các entry tường minh. Một archive khác có thể chỉ ghi hai file mà không có các entry thư mục riêng; số entry phụ thuộc cách đóng gói.

Nên đếm toàn bộ các entry mình duyệt, **kể cả entry thư mục**, thay vì chỉ đếm những file có payload. Nếu tên trùng xuất hiện, cần xác định chính sách từ chối, không để entry sau âm thầm ghi đè entry trước. Nếu cần giới hạn số **file thực sự** riêng với số entry, có thể theo dõi thêm một bộ đếm; không thay thế bộ đếm tổng entry.

Giới hạn số entry giúp giảm nguy cơ archive chứa hàng chục nghìn file kích thước 0. Giới hạn tổng byte **không** đủ để chặn trường hợp đó.

### 2.3. Tỷ lệ giải nén (decompression ratio)

Với một entry:

\[
\text{ratio} = \frac{\text{số byte sau giải nén}}{\text{số byte dữ liệu đã nén}}
\]

Ví dụ:

- Entry có 500.000 byte sau giải nén, 100.000 byte dữ liệu nén: tỷ lệ **5:1**.
- Entry có 500.000 byte sau giải nén, 1.000 byte dữ liệu nén: tỷ lệ **500:1**.
- Nếu chính sách cho tối đa 100:1, trường hợp thứ hai bị từ chối.

Để tránh phép chia không cần thiết, có thể kiểm tra:

~~~java
// Chỉ hợp lệ khi compressedBytes > 0 và phép nhân không tràn long.
boolean tooHigh = uncompressedBytes > compressedBytes * maxRatio;
~~~

Nếu số byte nén bằng **0** nhưng số byte sau giải nén lại **dương**, ratio không xác định/vô hạn: phải xem là bất thường và từ chối. Nếu cả hai bằng 0, đây có thể là entry rỗng hợp lệ.

**Cảnh báo:** tỷ lệ nén tự nó không nói toàn bộ mức độ nguy hiểm. Một file rất lớn nhưng có tỷ lệ 2:1 vẫn có thể lấp đĩa; ngược lại một file nhỏ hợp lệ có tỷ lệ 200:1 có thể bị chính sách chặn. Do đó phải kết hợp ratio với **giới hạn byte tuyệt đối**, thời gian và tài nguyên.

## 3. Chỉ kiểm tra ZipEntry.getSize() có đủ không?

**Không.** `ZipEntry.getSize()` cho biết kích thước chưa nén được khai báo trong metadata, và `ZipEntry.getCompressedSize()` cho biết kích thước đã nén theo metadata ZIP. Trong một số đường đọc, chúng có thể là `-1`, tức chưa biết.

~~~java
ZipEntry entry = /* một entry đã đọc */;
long declaredSize = entry.getSize();
long declaredCompressedSize = entry.getCompressedSize();
~~~

Đây chỉ là **đoạn minh họa API**, không phải chương trình độc lập. Hai giá trị trên có ích để **từ chối sớm** những entry có thông tin rõ ràng vượt ngưỡng. Nhưng metadata do dữ liệu đầu vào cung cấp, nên không được coi là bằng chứng an toàn cuối cùng khi xử lý archive không tin cậy.

Hãy phân biệt hai lớp bảo vệ:

- **Preflight metadata:** kiểm tra số entry, tên, kích thước khai báo, kích thước nén khai báo, tỷ lệ khai báo. Nếu vi phạm có thể từ chối trước khi tạo file.
- **Streaming enforcement:** khi gọi `read(...)`, cộng **số byte thực tế** và từ chối ngay nếu một entry hoặc tổng archive vượt quota. Đây là lớp bắt buộc để tránh tin tuyệt đối vào khai báo.

Với `ZipFile`, thông tin lấy từ central directory thường có kích thước cụ thể; tuy nhiên mã vẫn phải quyết định cách xử lý trường hợp không có/không hợp lệ. Ví dụ trong bài **từ chối metadata kích thước không xác định** để thực thi ratio một cách bảo thủ. Ứng dụng khác có thể hỗ trợ chế độ stream `ZipInputStream` mà chưa biết kích thước từ đầu, nhưng lúc đó càng phải áp giới hạn byte thực tế; việc tính tỷ lệ nén chính xác cần đo đúng lượng byte nén đã tiêu thụ cho từng entry, không suy đoán từ số byte trả về của luồng giải nén.

**Đừng tự lấy `Files.size(zipPath)` làm mẫu số tỷ lệ cho mỗi entry.** Kích thước archive còn chứa header, tên file, central directory và dữ liệu của các entry khác. Tỷ lệ từng entry dùng kích thước dữ liệu nén của **chính entry** đó.

## 4. Các loại ZIP bomb cần hiểu

### 4.1. Entry lớn bất thường

Một entry ghi nhiều byte giống nhau, chẳng hạn hàng triệu ký tự `A`, có thể nén rất tốt. Trên đĩa, nó nhỏ; sau giải nén, nó gây áp lực lên ổ lưu trữ hoặc bước parse.

**Bảo vệ:** giới hạn kích thước từng entry và tỷ lệ nén, kiểm tra trong mỗi vòng `read`.

### 4.2. Rất nhiều file nhỏ

Archive có 100.000 entry, mỗi entry chỉ vài byte. Tổng byte có thể nhỏ nhưng thao tác mở/tạo/đóng file, metadata, inode, quyền và quét virus tiêu tốn đáng kể.

**Bảo vệ:** giới hạn số entry, độ dài tên, độ sâu thư mục, số tệp được tạo; kết hợp quota và timeout. Giới hạn 100 entry ở ví dụ sau là một lựa chọn minh họa.

### 4.3. ZIP lồng ZIP

Archive ngoài chứa nhiều archive bên trong; ứng dụng sau đó tự động giải nén từng archive con. Nếu chỉ đặt quota **mỗi tầng**, tổng chi phí có thể tăng rất nhanh.

**Bảo vệ:** tốt nhất **không tự động giải nén đệ quy** trừ khi nghiệp vụ yêu cầu. Nếu có, áp ngân sách **toàn cục** cho cả cây (độ sâu, tổng entry, tổng byte, tổng thời gian, tỷ lệ) và từ chối khi vượt giới hạn. Đừng reset quota về mặc định không giới hạn ở mỗi archive con.

### 4.4. Entry có metadata/tên cố tình gây lỗi

Tên lạ, tên quá dài, đường dẫn trùng, đường dẫn cha `..`, đường dẫn tuyệt đối, dấu phân cách của hệ điều hành khác, symlink hoặc file hỏng có thể gây lỗi hoặc tạo tác dụng phụ ngoài phạm vi.

**Bảo vệ:** kiểm tra path, chính sách tên và kiểu entry; giải nén vào thư mục mới, độc lập, không có file sẵn. `ZipFile` chỉ cung cấp phần thông tin ZIP mà API công khai có thể đọc; không mặc định nó là trình xác thực hoàn chỉnh mọi loại metadata, permission hay symbolic link của ZIP.

## 5. Mô hình giải nén nhiều lớp bảo vệ

Một pipeline an toàn hơn:

~~~text
Đầu vào archive
      |
      v
Giới hạn upload + kích thước ZIP + quyền sở hữu
      |
      v
Mở ZIP, duyệt metadata: count, path, size, ratio
      |
      +-- Vi phạm --> REJECT (không công bố output)
      |
      v
Tạo thư mục đích mới trong vùng staging
      |
      v
Lần lượt stream từng entry theo buffer cố định
      |
      +-- Quota byte/ratio/time bị vượt --> ABORT & CLEANUP
      |
      v
Kiểm tra schema, CRC khi thư viện đọc, nội dung, chính sách
      |
      v
Công bố kết quả đã kiểm tra, ghi trạng thái/audit
~~~

**Điểm thiết kế:** việc giải nén **không nên ghi trực tiếp vào thư mục ứng dụng đang hoạt động**. Nếu phát hiện bomb ở giữa chừng, các file đã ghi một phần phải được coi là staging chưa tin cậy. Chỉ công bố sau khi toàn bộ archive vượt qua các kiểm tra liên quan.

### Ngân sách (budget) dùng chung

Giới hạn tổng byte phải **tích lũy qua mọi entry**, không reset về 0 ở mỗi file. Dạng kiểm tra tránh tràn số:

~~~java
if (n > maxEntryBytes - entryBytes
        || n > maxTotalBytes - totalBytes) {
    throw new IOException("Exceeded decompression quota");
}

entryBytes += n;
totalBytes += n;
~~~

Đây là đoạn minh họa với `n` là số byte mới đọc **dương**, các bộ đếm ban đầu không âm và luôn nằm trong giới hạn. So sánh theo dạng `limit - used` giúp tránh phép cộng `used + n` tràn `long`.

Việc đọc bằng bộ đệm `byte[8192]` chỉ giữ khoảng 8 KiB dữ liệu của lượt đọc **không** tự giới hạn file đầu ra. Phải cộng và chặn theo tổng byte thật.

## 6. Ví dụ Java 17+ hoàn chỉnh: ZipFile với giới hạn chặt

Ví dụ tạo một ZIP hợp lệ gồm hai file, giải nén vào thư mục mới rồi kiểm tra nội dung. Sau đó tạo một ZIP có tên entry `../outside.txt` để cho thấy bộ kiểm tra path từ chối trước khi ghi ra ngoài.

Ví dụ dùng policy nhỏ để dễ học:

- Tệp ZIP gốc tối đa 4 MiB.
- Tối đa 100 entry.
- Mỗi file sau giải nén tối đa 1 MiB.
- Tổng dữ liệu sau giải nén tối đa 2 MiB.
- Mỗi file tối đa 100:1; từ chối metadata size không xác định.
- Không nhận entry có `..`, đường dẫn tuyệt đối, dấu `\`, ổ đĩa dạng Windows, tên trống hoặc đường dẫn logic trùng.
- Tạo **thư mục output chưa tồn tại**, dùng `CREATE_NEW` để tránh ghi đè.

Lưu thành **`SafeZipExtractionDemo.java`**:

~~~java
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class SafeZipExtractionDemo {
    private static final long MAX_ZIP = 4L * 1024 * 1024;
    private static final long MAX_ENTRY = 1L * 1024 * 1024;
    private static final long MAX_TOTAL = 2L * 1024 * 1024;
    private static final long MAX_RATIO = 100;
    private static final int MAX_COUNT = 100;

    public static void main(String[] args) throws Exception {
        Path work = Files.createTempDirectory("zip-limits-");
        try {
            Path goodZip = work.resolve("good.zip");
            try (ZipOutputStream out =
                         new ZipOutputStream(Files.newOutputStream(goodZip))) {
                addEntry(out, "docs/readme.txt", "hello");
                addEntry(out, "metadata.txt", "v1");
            }

            Path goodOutput = work.resolve("unpacked");
            int files = extract(goodZip, goodOutput);
            System.out.println("Files extracted: " + files);
            System.out.println("Readme: " + Files.readString(
                    goodOutput.resolve("docs/readme.txt"),
                    StandardCharsets.UTF_8));

            Path badZip = work.resolve("bad.zip");
            try (ZipOutputStream out =
                         new ZipOutputStream(Files.newOutputStream(badZip))) {
                addEntry(out, "../outside.txt", "forbidden");
            }

            boolean rejected = false;
            try {
                extract(badZip, work.resolve("blocked"));
            } catch (IOException expected) {
                rejected = true;
            }
            System.out.println("Traversal rejected: " + rejected);
        } finally {
            cleanup(work);
        }
    }

    private static void addEntry(
            ZipOutputStream out, String name, String text)
            throws IOException {
        out.putNextEntry(new ZipEntry(name));
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }

    private static int extract(Path zipPath, Path outputRoot)
            throws IOException {
        if (Files.size(zipPath) > MAX_ZIP) {
            throw new IOException("ZIP input too large");
        }

        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            List<ZipEntry> entries = new ArrayList<>();
            Set<Path> usedNames = new HashSet<>();
            Enumeration<? extends ZipEntry> it = zip.entries();

            // First pass: do not write before validating all entries.
            while (it.hasMoreElements()) {
                ZipEntry entry = it.nextElement();
                if (entries.size() >= MAX_COUNT) {
                    throw new IOException("Too many ZIP entries");
                }

                Path relative = validateName(entry.getName());
                if (!usedNames.add(relative)) {
                    throw new IOException("Duplicate path: " + relative);
                }

                long size = entry.getSize();
                long compressed = entry.getCompressedSize();
                if (size < 0 || compressed < 0) {
                    throw new IOException("Unknown ZIP entry size");
                }
                if (size > MAX_ENTRY || compressed > MAX_ZIP) {
                    throw new IOException("Entry size exceeded");
                }
                if (exceedsRatio(size, compressed)) {
                    throw new IOException("Ratio exceeded");
                }
                if (entry.isDirectory() && size != 0) {
                    throw new IOException("Directory entry with data");
                }
                entries.add(entry);
            }

            // A fresh, unshared output directory limits overwrite risk.
            Files.createDirectory(outputRoot);
            long total = 0;
            int fileCount = 0;
            byte[] chunk = new byte[8192];

            for (ZipEntry entry : entries) {
                Path relative = validateName(entry.getName());
                Path output = outputRoot.resolve(relative).normalize();
                if (!output.startsWith(outputRoot)) {
                    throw new IOException("Path escaped target directory");
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                    continue;
                }

                Files.createDirectories(output.getParent());
                long entryBytes = 0;
                long compressed = entry.getCompressedSize();

                try (InputStream in = zip.getInputStream(entry);
                     OutputStream out = Files.newOutputStream(
                             output,
                             StandardOpenOption.CREATE_NEW,
                             StandardOpenOption.WRITE)) {

                    int n;
                    while ((n = in.read(chunk)) != -1) {
                        if (n == 0) {
                            throw new IOException("Input made no progress");
                        }
                        if (n > MAX_ENTRY - entryBytes
                                || n > MAX_TOTAL - total) {
                            throw new IOException("Byte quota exceeded");
                        }

                        entryBytes += n;
                        total += n;
                        if (exceedsRatio(entryBytes, compressed)) {
                            throw new IOException("Ratio exceeded");
                        }
                        out.write(chunk, 0, n);
                    }
                }

                // Count only files written successfully.
                fileCount++;
            }
            return fileCount;
        }
    }

    private static boolean exceedsRatio(
            long uncompressed, long compressed) {
        if (uncompressed == 0) {
            return false;
        }
        if (compressed == 0) {
            return true;
        }
        // MAX_ZIP constrains compressed values before calling here.
        return compressed <= Long.MAX_VALUE / MAX_RATIO
                && uncompressed > compressed * MAX_RATIO;
    }

    private static Path validateName(String name) throws IOException {
        if (name.isBlank()
                || name.indexOf('\\') >= 0
                || name.indexOf('\0') >= 0
                || name.startsWith("/")
                || name.matches("^[A-Za-z]:.*")) {
            throw new IOException("Unsafe name: " + name);
        }

        Path relative;
        try {
            relative = Path.of(name);
        } catch (RuntimeException invalidName) {
            throw new IOException("Invalid ZIP path", invalidName);
        }
        if (relative.isAbsolute()) {
            throw new IOException("Absolute ZIP path");
        }
        for (Path part : relative) {
            if (part.toString().equals("..")) {
                throw new IOException("Parent traversal forbidden");
            }
        }
        relative = relative.normalize();
        if (relative.toString().isEmpty()
                || relative.toString().equals(".")) {
            throw new IOException("Empty path");
        }
        return relative;
    }

    private static void cleanup(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(
                    Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
~~~

Biên dịch/chạy bằng JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 SafeZipExtractionDemo.java
java SafeZipExtractionDemo
~~~

**Stdout kỳ vọng trên file system thông thường:**

~~~text
Files extracted: 2
Readme: hello
Traversal rejected: true
~~~

### Giải thích mã

1. `Files.size(zipPath)` chặn kích thước archive đầu vào trước khi đọc nội dung sâu. Đây **không phải** giới hạn dung lượng đã giải nén; nó là một quota riêng.
2. Vòng `zip.entries()` duyệt entry một lần để xác thực toàn bộ tên, số entry và metadata trước khi ghi. `entries.size() >= MAX_COUNT` chặn entry thứ 101.
3. `usedNames` từ chối tên đã chuẩn hóa bị lặp, không để entry mới cố ý ghi đè entry cũ. Trường hợp tên có cùng ý nghĩa trên file system case-insensitive còn phụ thuộc nền tảng; `CREATE_NEW` là một lớp bảo vệ bổ sung.
4. `validateName` từ chối tên rỗng, NUL, backslash, đường dẫn dạng ổ đĩa, tuyệt đối và bất kỳ thành phần `..`. Nó chặn những hình thức traversal cơ bản và yêu cầu đường dẫn dùng dấu `/` cho ZIP minh họa.
5. `Files.createDirectory(outputRoot)` yêu cầu output chưa tồn tại. Không ghi dữ liệu vào thư mục hệ thống đang sử dụng hoặc đường dẫn có sẵn với các file quan trọng.
6. Với mỗi file, chương trình đọc từng chunk tối đa 8192 byte rồi **kiểm tra quota trước khi ghi chunk đó**. Hai bộ đếm `entryBytes` và `total` phản ánh byte thực tế; không chỉ tin `entry.getSize()`.
7. Hàm `exceedsRatio` xử lý trường hợp mẫu số bằng 0 và tránh tràn `long`. Trong ví dụ, metadata `compressed` đã bị giới hạn ≤ 4 MiB.
8. `ZipFile.getInputStream(entry)` chỉ mở dữ liệu của entry cụ thể; `try-with-resources` đóng luồng kể cả khi phát hiện vượt quota.
9. Sau khi xử lý đủ, chương trình in số file và nội dung đã giải nén. Một archive độc hại có tên `../outside.txt` bị từ chối ở vòng kiểm tra đầu tiên.
10. `cleanup` xóa cây thư mục tạm trong demo. Mã demo không phải một hệ thống lưu trữ lâu dài; production cần quy trình staging, cleanup lỗi, quản lý race/symlink và công bố kết quả an toàn hơn.

**Giới hạn chủ đích:** Ví dụ dạy nguyên tắc đếm dung lượng, entry, ratio và ngăn Zip Slip cơ bản. Nó **không phải ZIP sandbox đầy đủ**. Không có watchdog timeout, sandbox OS, xác thực format nghiệp vụ, xử lý POSIX symlink mode trong ZIP, hạn chế độ sâu/tên, quota đồng thời giữa nhiều request, hay cơ chế xuất bản nguyên tử. Không sử dụng nguyên xi nó để xử lý archive thù địch trên môi trường nhiều người dùng mà không bổ sung các lớp bảo vệ tương ứng.

## 7. Tại sao phải kiểm tra ratio cả trước và sau giải nén?

### Kiểm tra trước (declared ratio)

Nếu ZIP khai báo `uncompressedSize = 10_000_000` và `compressedSize = 1_000`, tỷ lệ 10.000:1 vượt ngưỡng 100:1; có thể từ chối ngay, không cần tạo file hay giải nén payload.

Việc từ chối sớm tiết kiệm tài nguyên nhưng **chưa đủ**: metadata không nhất thiết đáng tin; parser còn có thể đối mặt với dữ liệu hỏng hoặc cấu trúc archive phức tạp.

### Kiểm tra trong vòng đọc (actual output)

Nếu metadata khai báo một entry nhỏ nhưng luồng thực tế tạo ra nhiều byte hơn, bộ đếm thật sẽ vượt `MAX_ENTRY` hoặc `MAX_TOTAL`. Kiểm tra mỗi chunk giới hạn lượng dữ liệu ứng dụng ghi xuống disk.

Đối với ratio, ví dụ sử dụng số byte **thực sự đã giải nén** và số byte nén **khai báo** trong central directory. Đây là **lớp ngăn sớm**, không phải bộ đo chính xác số byte nén vật lý đã đọc tại mỗi khoảnh khắc. Nếu metadata compressed size không đáng tin, giới hạn output tuyệt đối vẫn là biện pháp chắc chắn hơn.

Để đo tỷ lệ nén chính xác trong một pipeline streaming không có metadata tin cậy, phải instrument nguồn byte nén thật theo từng entry, bao gồm lưu ý buffering, data descriptor và ranh giới ZIP; không lấy tổng byte đọc từ một luồng wrapper bất kỳ rồi gọi đó là compressedSize per entry.

### Ratio của toàn archive

Có thể tính:

\[
\text{archive ratio} =
\frac{\text{tổng byte chưa nén}}{\text{kích thước ZIP trên đĩa}}
\]

như một chỉ số bổ sung, nhưng tỷ lệ này khác tỷ lệ **mỗi entry** vì ZIP có header, metadata và thư mục trung tâm. Nó có thể bỏ qua một entry cực đoan khi các entry khác làm mẫu số lớn. Do đó, nếu cần, dùng cả giới hạn tỷ lệ từng entry và tổng archive, không thay thế các giới hạn byte tuyệt đối.

## 8. ZipFile hay ZipInputStream?

| Yếu tố | `ZipFile` | `ZipInputStream` |
| --- | --- | --- |
| Cách truy cập | Mở archive, duyệt entry trong central directory, lấy luồng cho từng entry | Đọc tuần tự các entry từ một `InputStream` |
| Metadata kích thước | Thường có từ central directory trước khi mở payload | Có thể `-1` trước khi đọc xong entry nếu dùng data descriptor |
| Thuận tiện cho preflight toàn bộ entry | **Có**, nhưng metadata vẫn cần kiểm tra và runtime quota | Khó hơn khi phải biết tất cả entry trước khi giải nén |
| Đầu vào không seek được | Không phải lựa chọn trực tiếp | Phù hợp với luồng input, nhưng vẫn cần quota đầu vào |
| Chặn ZIP bomb | Cần giới hạn streaming byte thực tế | Cũng phải giới hạn streaming byte thực tế |
| Quy tắc ghi tệp | Ứng dụng quyết định, phải chống traversal/overwrite | Ứng dụng quyết định, không tự an toàn hơn |

**Không nhầm `getNextEntry()` với việc một entry đã giải nén hoàn tất.** Với `ZipInputStream`, phải đọc tới hết entry hoặc đóng entry trước khi chuyển sang entry tiếp theo theo hợp đồng API. Vì metadata có thể chưa biết, giới hạn byte thực tế càng quan trọng.

Nếu ZIP được upload qua mạng, giới hạn dung lượng upload cũng phải được thực thi **trong khi nhận dữ liệu**, không đợi toàn bộ upload xong rồi mới kiểm tra. Với `ZipFile`, thường nhận upload vào file staging đã giới hạn trước, sau đó mới mở ZIP.

## 9. Phải tính cả tài nguyên ngoài số byte

Các giới hạn size/count/ratio là lõi bài học, nhưng không giải quyết tất cả kiểu từ chối dịch vụ.

### 9.1. CPU và thời gian

Một archive có thể tốn CPU đáng kể để giải nén/kiểm tra, kể cả khi output cuối cùng dưới quota. Không có timeout tích hợp chung cho mọi lệnh `InputStream.read` trong ZIP. Trong hệ thống production, đặt deadline cho request, theo dõi thời gian, và cân nhắc cô lập bằng tiến trình/container có quota CPU, RAM, đĩa.

Kiểm tra thời gian trong vòng đọc có thể giúp dừng giữa các chunk, nhưng nếu **một lời gọi đọc bị kẹt**, bộ đếm thời gian ngoài luồng hoặc cơ chế giám sát ở mức tiến trình sẽ cần thiết. `Thread.interrupt()` không phải bảo đảm dừng mọi hoạt động decompression ngay lập tức.

### 9.2. Số lượng yêu cầu đồng thời

Giới hạn mỗi ZIP 2 MiB output nhưng nhận đồng thời 10.000 archive có thể dẫn đến tổng lượng dữ liệu và I/O lớn. Cần giới hạn **concurrency**, queue, tổng byte staging, số file đang mở và tốc độ nhận request. Các quota nên có cả phạm vi request và tenant/service.

### 9.3. Độ dài tên và độ sâu thư mục

Tên entry rất dài hoặc cấu trúc thư mục sâu có thể gây lỗi đường dẫn, quá tải metadata hoặc tạo cây khó dọn. Hệ thống thực tế nên định nghĩa giới hạn `MAX_NAME_LENGTH`, `MAX_DEPTH` và các ký tự/tên thiết bị bị cấm theo nền tảng.

Các đường dẫn có ký tự đặc biệt cần được chuẩn hóa theo chính sách xuyên nền tảng. Ví dụ, từ chối dấu `\` trong tên ZIP là chính sách đơn giản để tránh việc một hệ điều hành xem nó là dấu phân cách trong khi hệ điều hành khác xem là ký tự.

### 9.4. File permissions, symlink và race condition

ZIP có thể chứa thông tin quyền Unix hoặc metadata biểu diễn symbolic link. API `ZipEntry` chuẩn không cung cấp mọi thuộc tính POSIX kiểu archive cho một hệ thống extraction có yêu cầu bảo mật cao. Không suy ra rằng mọi entry không kết thúc bằng `/` chắc chắn là file thường an toàn.

Ngoài ra, kiểm tra `output.startsWith(outputRoot)` là kiểm tra đường dẫn **về mặt ký tự**, không tự bảo vệ trước khi một tiến trình khác tạo symlink ở thư mục cha ngay sau kiểm tra. Vì vậy:

- Dùng output staging mới, riêng biệt, chỉ có tiến trình/trust domain được phép sửa.
- Không theo các symlink do phía không tin cậy có thể đặt vào staging.
- Tránh giải nén trực tiếp vào đường dẫn đang có file hoặc đang được nhiều actor ghi đồng thời.
- Đối với threat model nghiêm ngặt, sử dụng cơ chế thao tác path dựa trên handle, sandbox và kiểm tra attribute thích hợp.

`CREATE_NEW` chặn ghi đè file đã tồn tại tại tên đích cuối cùng, nhưng **không phải bảo đảm đầy đủ** chống các cuộc đua symlink ở thư mục cha.

## 10. Kiểm tra archive hỏng và CRC

ZIP có CRC-32, và các API giải nén có thể phát hiện một số loại dữ liệu hỏng qua cấu trúc hoặc CRC. Tuy nhiên:

- **CRC-32 không phải chữ ký mật mã hoặc cơ chế xác thực người gửi.**
- Việc giải nén thành công không có nghĩa dữ liệu hợp lệ theo schema nghiệp vụ.
- Không vì CRC đúng mà bỏ qua quota byte/count/ratio.
- Dữ liệu có thể hỏng, có encoding sai, hoặc được cố ý dựng để khai thác bước xử lý tiếp theo.

Nếu cần bảo đảm nội dung đáng tin từ một nhà phát hành, dùng hash/chữ ký số và chuỗi xác minh nguồn phù hợp, không chỉ dựa CRC ZIP.

## 11. Công bố dữ liệu sau khi giải nén và khôi phục lỗi

Một request nên có các trạng thái rõ ràng:

~~~text
RECEIVED
   |
   v
VALIDATING_METADATA
   |
   v
EXTRACTING_IN_STAGING
   |
   +---- error/quota/path failure ---> REJECT + CLEANUP
   |
   v
VALIDATING_CONTENT
   |
   +---- schema/hash failure -------> REJECT + CLEANUP
   |
   v
PUBLISHING
   |
   v
READY
~~~

**Quan trọng:** nếu quota vượt giữa lúc ghi entry thứ ba, hai entry trước có thể đã tồn tại trong staging. Không được coi staging là bản giải nén thành công; phải đánh dấu reject và dọn dẹp. Ví dụ phía trên được thiết kế để xóa toàn bộ cây tạm khi chương trình kết thúc, kể cả đường lỗi.

Trong ứng dụng thật, upload có thể đến từ người dùng không tin cậy. Nên phân biệt rõ namespace staging và namespace đã công bố. Việc di chuyển/đổi tên toàn bộ thư mục có nguyên tử hay không phụ thuộc file system; nếu cần atomic publication, phải kiểm tra tính hỗ trợ và thiết kế giao thức phù hợp, không mặc định mọi `Files.move` là atomic.

Nếu có retry, phải tránh công bố dữ liệu từng phần hoặc ghi đè kết quả của request khác. Có thể dùng request ID/phiên bản riêng, checkpoint/audit và cleanup chạy lại an toàn.

## 12. Sai lầm phổ biến và cách sửa

| Sai lầm | Tại sao nguy hiểm | Cách đúng |
| --- | --- | --- |
| Chỉ giới hạn dung lượng tệp `.zip` | ZIP nhỏ có thể giải nén rất lớn | Giới hạn tổng byte sau giải nén và từng entry |
| Chỉ giới hạn tổng dung lượng đã giải nén | Nhiều file rỗng vẫn làm cạn inode/metadata | Giới hạn số entry và tài nguyên tạo file |
| Chỉ giới hạn một entry | Nhiều entry nhỏ cộng dồn vượt quota | Dùng bộ đếm tổng qua mọi entry |
| Tin `ZipEntry.getSize()` là chính xác tuyệt đối | Metadata có thể thiếu/không đáng tin | Kiểm tra metadata **và** byte thực tế |
| Kiểm tra size sau khi ghi hết | Disk đã có thể đầy trước khi kiểm tra | Kiểm tra trong từng vòng đọc trước khi ghi |
| Chỉ kiểm tra compression ratio | Ratio thấp vẫn có thể nở thành dữ liệu rất lớn | Kết hợp ratio với giới hạn tuyệt đối |
| Chia cho compressedSize=0 | Lỗi chia 0 hoặc tính ratio sai | Phân biệt payload rỗng và payload dương |
| Dùng `int` đếm tổng byte | Tràn số khi dữ liệu lớn | Dùng `long` và phép so sánh tránh overflow |
| Không kiểm tra đường dẫn ZIP | `../...` có thể ghi ra ngoài đích | Chuẩn hóa, từ chối traversal và sandbox output |
| Giải nén vào thư mục đang sử dụng | Đè file hoặc công bố dữ liệu chưa được xác thực | Dùng staging mới, `CREATE_NEW`, kiểm tra và publish |
| Chấp nhận đường dẫn trùng không chủ đích | Ghi đè, hành vi khó dự đoán | Kiểm tra duplicate và chính sách file system |
| Tự động giải nén ZIP bên trong ZIP vô hạn | Quota từng tầng không ngăn bùng nổ toàn cây | Giới hạn độ sâu và ngân sách toàn cục |
| Không giới hạn thời gian/concurrency | CPU, I/O hoặc request backlog bị quá tải | Timeout, queue bounded và sandbox khi cần |
| Tin CRC là chữ ký bảo mật | Dữ liệu độc hại vẫn có CRC hợp lệ | Xác minh nguồn/schema/chữ ký theo threat model |
| Trả thành công dù extraction ném exception | Thư mục đích có thể chỉ chứa dữ liệu một phần | Không publish trước khi verify và cleanup đường lỗi |

## 13. Các tình huống tính quota

### Tình huống A: ZIP nhỏ, nội dung khổng lồ

Archive 200 KiB, có một file 50 MiB sau giải nén. Nếu MAX_ENTRY = 1 MiB, phải dừng ngay khi bộ đếm uncompressed byte vượt 1 MiB, kể cả metadata đầu vào khai báo khác.

Tỷ lệ xấp xỉ 250:1 cũng vượt mức 100:1; nhưng chỉ cần **một** trong các quota bị vượt là đủ để từ chối.

### Tình huống B: 200 file rỗng

Tổng dung lượng đã giải nén là 0 byte, nhưng 200 entry vượt MAX_COUNT = 100. Phải từ chối dù ratio và size hoàn toàn nhỏ.

### Tình huống C: 10 file, mỗi file 400 KiB

Không file nào vượt MAX_ENTRY = 1 MiB, nhưng tổng 4.000 KiB lớn hơn MAX_TOTAL = 2 MiB. Chính sách phải chặn khi tổng thực tế vượt ngưỡng.

### Tình huống D: File 500 KiB sau giải nén và 1 KiB nén

Ratio = 500:1. Nếu MAX_RATIO = 100:1, phải từ chối dù byte output có thể vẫn dưới MAX_ENTRY.

### Tình huống E: ZIP lồng nhau

ZIP ngoài giải nén thành ZIP con 1 MiB, sau đó ZIP con tạo 50 MiB. Nếu ứng dụng tự giải nén ZIP con, phải tính quota của **cả chuỗi**; nếu không hỗ trợ nested archives thì nên giữ ZIP con như một file dữ liệu và không mở thêm.

## 14. Checklist triển khai production

Trước khi nhận ZIP từ bên ngoài, xác nhận:

1. **Chính sách số lượng:** giới hạn archive size, entry count, total unpacked bytes, per-entry bytes và tỷ lệ nén; có quota toàn hệ thống/tenant.
2. **Thực thi streaming:** kiểm tra số byte thực tế theo chunk; không dùng `readAllBytes()` hay `ByteArrayOutputStream` không giới hạn cho payload lạ.
3. **Path safety:** chống Zip Slip, absolute path, tên trùng, separator khác hệ điều hành, symlink, giới hạn độ sâu và tên.
4. **Staging:** tạo thư mục mới, không cho người dùng khác sửa trong quá trình giải nén, cấm ghi đè, không public dữ liệu trước khi verify.
5. **Format/schema:** chỉ chấp nhận loại file cần thiết, encoding/structure hợp lệ; không suy diễn từ extension.
6. **Resource isolation:** deadline, giới hạn CPU/RAM/disk/inode/open file descriptor và số request đồng thời.
7. **Error handling:** xử lý `IOException`, file hỏng, CRC lỗi, không tiến triển; cleanup staging và báo trạng thái reject.
8. **Observability:** log request id và lý do từ chối, đếm quota exceeded, thời gian/byte/entry, theo dõi ổ đĩa.
9. **Test âm tính:** archive nhiều file rỗng, file nén cực tốt, metadata không hợp lệ, entry lồng sâu, traversal, duplicate, ZIP lồng ZIP, upload ngắt giữa chừng.
10. **Tài liệu hợp đồng:** công bố giới hạn hợp lệ cho người gửi và thông báo lỗi rõ ràng; không âm thầm cắt bớt dữ liệu giải nén.

## 15. Câu hỏi tự kiểm tra

**Câu 1.** ZIP 5 MiB, sau giải nén 6 MiB. MAX_ZIP = 4 MiB, MAX_TOTAL = 10 MiB. Có được chấp nhận không?

**Đáp án:** Không. ZIP đầu vào đã vượt MAX_ZIP dù tổng output chưa vượt giới hạn.

**Câu 2.** Có 101 entry, mỗi entry 0 byte; MAX_COUNT = 100. Có phải ZIP bomb đáng lo không?

**Đáp án:** Archive vượt chính sách số entry. Không cần dữ liệu lớn mới gây áp lực metadata/inode. Từ chối theo MAX_COUNT.

**Câu 3.** `getSize() == -1` nghĩa là kích thước entry bằng -1 byte?

**Đáp án:** Không. `-1` nghĩa là kích thước chưa biết. Phải có chính sách xử lý; không coi là an toàn. Nếu hỗ trợ stream, phải đếm byte thực tế và có cơ chế đánh giá ratio phù hợp.

**Câu 4.** Ratio 10:1 nhưng entry giải nén 50 GiB, giới hạn mỗi entry 1 GiB. Có cho qua vì ratio thấp?

**Đáp án:** Không. Giới hạn byte tuyệt đối đã bị vượt.

**Câu 5.** Đang giải nén file thứ 20, tổng byte đạt MAX_TOTAL. Lần `read()` tiếp theo trả 8192 byte. Cần làm gì?

**Đáp án:** Từ chối trước khi ghi chunk mới vì `8192 > MAX_TOTAL - totalBytes`. Không để byte vượt quota xuất hiện trên đĩa.

**Câu 6.** Tệp ZIP có entry `../../etc/config` nhưng tổng byte rất nhỏ. Có an toàn không?

**Đáp án:** Không. Đường dẫn cố vượt thư mục output là rủi ro độc lập với kích thước; từ chối path traversal.

**Câu 7.** Giải nén bằng `ZipInputStream`, size chưa biết trước. Có thể chỉ nhìn metadata rồi quyết định không?

**Đáp án:** Không. Phải giám sát byte output thực tế trong lúc đọc và dùng chính sách thay thế cho ratio nếu chưa đo được compressedSize chính xác.

**Câu 8.** CRC ZIP khớp có chứng minh file đến từ nguồn đáng tin không?

**Đáp án:** Không. CRC là cơ chế phát hiện lỗi dữ liệu thông thường, không xác thực nguồn hoặc chống sửa đổi có chủ đích như chữ ký số.

## 16. Ghi nhớ

- **Dung lượng ZIP đầu vào, byte sau giải nén của từng entry và tổng byte sau giải nén là ba quota riêng.**
- **Số entry** chặn archive có quá nhiều file/thư mục, kể cả khi tổng byte gần bằng 0.
- **Tỷ lệ giải nén** giúp phát hiện dữ liệu nở cực mạnh nhưng **không thay thế giới hạn byte tuyệt đối**.
- **Không tin metadata ZIP một cách tuyệt đối**: kiểm tra cả khai báo trước khi đọc và byte thực tế khi giải nén.
- **Không chỉ phòng bomb:** phải từ chối traversal, kiểm soát symlink/overwrite, staging và tài nguyên đồng thời.
- **Tính đúng đắn cần được chứng minh bằng quota được thực thi trước mỗi lần ghi**, xử lý lỗi và chỉ công bố output khi toàn bộ quy trình thành công.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="compression-and-archives-012">Giải nén đúng thư mục đích và xử lý đường dẫn entry</a>

<details>
<summary>Xem chi tiết</summary>

# Giải nén đúng thư mục đích và xử lý đường dẫn entry trong Java

## 1. Mục tiêu: giải nén đúng chỗ, không ghi ra ngoài vùng được phép

Trong Java I/O, một ZIP archive có thể chứa nhiều `ZipEntry`. Mỗi entry có **tên** (ví dụ `docs/manual.txt`), dữ liệu nén và metadata. Khi giải nén xuống ổ đĩa, chương trình phải chuyển **tên entry không đáng tin cậy** thành một `Path` trên máy đích, tạo thư mục cha, rồi ghi dữ liệu vào file được phép.

Lỗi phổ biến là nối tên entry vào thư mục đích mà không kiểm tra:

~~~java
Path target = outputDir.resolve(entry.getName());
Files.copy(zip, target); // nguy hiểm nếu entry.getName() chưa kiểm tra
~~~

Đây là **mã phản ví dụ**, không chạy độc lập. Nếu entry có tên `../../outside.txt`, thao tác ghi có thể vượt khỏi thư mục `outputDir`. Lỗ hổng này được gọi là **Zip Slip** (path traversal khi giải nén).

Một chương trình giải nén phù hợp phải đảm bảo:

1. Mọi file và thư mục nằm **bên trong một thư mục gốc đích** do ứng dụng chỉ định.
2. Entry không chứa đường dẫn tuyệt đối, `..`, ký tự phân tách nguy hiểm hoặc tên không tương thích chính sách.
3. Không đi xuyên **symbolic link** hoặc thành phần đường dẫn do người khác kiểm soát.
4. Không ghi đè file đã tồn tại nếu người dùng/chính sách không cho phép.
5. Có giới hạn **số lượng entry, độ dài tên, số byte giải nén và tổng tài nguyên**.
6. Giải phóng stream/channel đúng cách, phát hiện lỗi archive và không coi một lần copy lỗi là thành công.
7. Có kế hoạch xử lý file mới tạo dở khi thất bại; giải nén nhiều entry **không tự là transaction nguyên tử**.

Bài này tập trung giải nén ZIP bằng `java.util.zip` và `java.nio.file` trên **Java 17+**. Với `.tar.gz`, GZIP chỉ giải nén stream bên ngoài, còn TAR cần parser phù hợp; các nguyên tắc xử lý tên entry bên dưới vẫn có ích nhưng API TAR không thuộc `java.util.zip`.

## 2. Hiểu đúng tên ZipEntry và thư mục đích

Ví dụ trong một ZIP:

~~~text
Archive backup.zip
  docs/
  docs/readme.txt
  docs/config/app.properties
  images/logo.png
~~~

Giả sử ứng dụng muốn giải nén vào:

~~~text
/opt/myapp/imports/job-001/
~~~

Sau khi xử lý an toàn, kết quả mong đợi là:

~~~text
/opt/myapp/imports/job-001/
  docs/
    readme.txt
    config/
      app.properties
  images/
    logo.png
~~~

Trên Windows, thư mục gốc có thể là `C:\data\imports\job-001` thay vì đường dẫn Linux. **Tên trong ZIP thường dùng dấu slash `/` làm ký tự phân tách**; không nên tự ý xem `\` là đường dẫn con hợp lệ khi nhập archive không tin cậy.

### 2.1. ZipEntry không phải java.nio.file.Path

`ZipEntry.getName()` trả một **String lấy từ archive**, không phải một đường dẫn đã được hệ điều hành chuẩn hóa hoặc xác nhận quyền truy cập. Ví dụ:

~~~java
ZipEntry entry = zip.getNextEntry();
String name = entry.getName(); // dữ liệu lấy từ archive
~~~

`name` có thể đại diện file thường, thư mục (quy ước tên kết thúc bằng `/`), hoặc tên không an toàn. Java không tự động gắn tên đó vào đúng đích cho bạn.

**Không dùng `entry.getName()` trực tiếp làm đầu ra** trong `new File(...)`, `Files.copy(...)`, `Files.newOutputStream(...)` hoặc `Files.createDirectories(...)` nếu chưa xác thực.

### 2.2. Đường dẫn tương đối phải thuộc thư mục gốc

Với thư mục đích `root`, ta thường xây dựng:

~~~java
Path target = root.resolve(entry.getName()).normalize();
if (!target.startsWith(root)) {
    throw new IOException("Entry outside output directory");
}
~~~

Đây là **biện pháp bắt buộc cơ bản nhưng chưa đầy đủ**. Nó phải đi cùng:

- `root` là đường dẫn **tuyệt đối đã được xác định an toàn**, không so sánh một relative root với absolute target.
- Tên entry được kiểm tra **trước khi dùng**, nhất là absolute path, `..`, backslash, Windows drive/UNC và null byte.
- Kiểm tra symbolic link ở các thành phần thư mục cha và ngăn đổi đường dẫn qua race khi có đối tượng khác được quyền sửa filesystem.
- Chính sách overwrite và giới hạn tài nguyên.

Nếu `root` là absolute normalized, `target.startsWith(root)` là phép so sánh thành phần `Path`, **không phải** so chuỗi bằng `String.startsWith(...)`. Ví dụ đường dẫn `/safe/outside.txt` có thể bắt đầu bằng chuỗi `"/safe/out"` nhưng **không nằm dưới** path `/safe/out` theo thành phần Path.

## 3. Những kiểu tên entry không được tin tưởng

Bảng sau minh họa các trường hợp cần chặn hoặc áp dụng chính sách đặc biệt:

| Tên entry | Rủi ro | Cách xử lý an toàn điển hình |
|---|---|---|
| `docs/readme.txt` | Đường dẫn tương đối bình thường | Có thể chấp nhận nếu các kiểm tra khác đạt |
| `docs/config/app.yml` | Cần tạo thư mục cha | Xác thực rồi tạo directory an toàn |
| `../outside.txt` | Vượt một cấp lên trên | **Từ chối** |
| `../../secret.txt` | Vượt nhiều cấp | **Từ chối** |
| `/etc/passwd` | Absolute path Unix | **Từ chối** |
| `C:/Windows/temp.txt` | Windows drive-absolute | **Từ chối** |
| `C:relative.txt` | Windows drive-relative / tên có colon | **Từ chối** theo policy portable |
| `\\server\share\file.txt` | Windows UNC hoặc separator khác | **Từ chối** backslash/UNC |
| `docs\..\outside.txt` | Backslash traversal trên Windows | **Từ chối** |
| `docs/./a.txt` | Thành phần `.` không cần thiết | Nên từ chối theo policy chặt |
| `docs//a.txt` | Thành phần rỗng/đường dẫn bất thường | Nên từ chối |
| `docs/../a.txt` | Có `..` dù normalize vẫn bên trong | Nên từ chối ngay tên |
| `docs/a.txt:stream` | Alternate Data Stream (Windows) | Từ chối dấu colon |
| `docs/a.txt` lặp lại | Overwrite/collision | Từ chối duplicate hoặc có chính sách tường minh |
| Tên rất dài/nhiều cấp | Exhaustion, lỗi filesystem | Giới hạn độ dài và độ sâu |

Các ví dụ `C:`, `\`, ADS có tính đặc thù Windows. Để viết chương trình giải nén đa nền tảng và tránh khác biệt về cách parse của các filesystem provider, **không nên chấp nhận các dạng này chỉ vì chúng trông vô hại trên Linux**.

### Kiểm tra ngay cả khi normalize vẫn cho đường dẫn bên trong

Tên `docs/../file.txt` sau `normalize()` có thể thành `file.txt`, vẫn nằm dưới root. Nhưng chấp nhận dấu `..` làm tăng độ phức tạp, khiến kiểm tra tên không nhất quán và dễ bị khác biệt OS. Với một extractor chính sách chặt, **từ chối mọi path component `..` và `.`**, rồi mới resolve/normalize như lớp kiểm tra dự phòng.

### Directory entry

`ZipEntry.isDirectory()` thường nhận biết tên có dấu `/` cuối. Entry `docs/` là thư mục; code phải tạo directory thay vì mở output stream để ghi nội dung như file thường. Nhưng ZIP hợp lệ có thể **không chứa entry `docs/` riêng** mà chỉ chứa `docs/readme.txt`, vì vậy extractor cần tự tạo thư mục cha khi cần, sau khi đã xác thực đường dẫn.

Không dựa hoàn toàn vào đuôi `/` để xác định **mọi loại filesystem object trong ZIP do bên thứ ba tạo**; một số archive giữ Unix file modes/symlink metadata ngoài phạm vi đơn giản mà `ZipInputStream` biểu lộ.

## 4. Zip Slip: normalize, startsWith và ranh giới bảo vệ

Một extractor sai có thể làm:

~~~java
Path target = root.resolve(entry.getName()); // Chưa normalize/chưa validate.
Files.createDirectories(target.getParent());
Files.copy(zip, target);
~~~

Khi entry là `../attack.txt`, `resolve` tạo ra một path đi ra ngoài root theo nghĩa đường dẫn. `Files.createDirectories` và `Files.copy` có thể ghi ra nơi không mong muốn hoặc tác động một tài nguyên bên ngoài phạm vi extraction.

Một hướng kiểm tra tốt hơn:

~~~java
Path root = outputDir.toRealPath();
String name = entry.getName();

// Chặn dạng không đáng tin trước khi dùng Path API.
if (name.isEmpty() || name.startsWith("/")
        || name.indexOf('\\') >= 0
        || name.indexOf('\0') >= 0) {
    throw new IOException("Unsafe entry name");
}

Path target = root.resolve(name).normalize();
if (target.equals(root) || !target.startsWith(root)) {
    throw new IOException("ZIP entry escaped target directory");
}
~~~

Đây là **mã minh họa**, chưa bao gồm tách component để chặn `..`, `.`, colon, empty segments, symlink và quota. Mẫu chạy đầy đủ ở mục 8 triển khai những điểm đó.

### Vì sao không so chuỗi thô?

Đoạn code như:

~~~java
if (!target.toString().startsWith(root.toString())) {
    throw new IOException("Unsafe target");
}
~~~

là **mã phản ví dụ**. Nếu root là `/srv/out` thì `/srv/outside/a.txt` cũng bắt đầu bằng cùng chuỗi. `Path.startsWith(Path)` kiểm tra theo thành phần tên đường dẫn, giúp tránh nhầm lẫn này.

### Vì sao toAbsolutePath + normalize chưa chống được symlink?

`normalize()` xử lý các thành phần cú pháp `.` và `..`; nó **không duyệt filesystem để phát hiện symbolic link**. Giả sử dưới root đã có `docs` là symlink trỏ tới `/tmp/other`. Path `root/docs/file.txt` vẫn có thể vượt ngoài root **theo vị trí vật lý thực tế** dù phép kiểm tra tên/normalize/startsWith đều đạt.

Vì vậy phải chú ý đến **trạng thái filesystem**, chứ không chỉ String và Path.

## 5. Symlink, hardlink và TOCTOU

### 5.1. Directory symlink trong thư mục đích

Nếu thư mục đích cho phép những thành phần không đáng tin cậy, mã kiểu:

~~~java
Files.createDirectories(target.getParent());
Files.newOutputStream(target);
~~~

có thể đi qua một symlink đã có trên ổ đĩa và ghi vào vị trí khác. Cách phòng vệ cơ bản:

- Giải nén vào **thư mục mới, rỗng, private**, dưới một parent do ứng dụng kiểm soát.
- Khi duyệt các thành phần thư mục, dùng `Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)` để **không coi symlink là directory**.
- Khi tạo file, dùng `StandardOpenOption.CREATE_NEW` để không ghi đè file hoặc symlink đã tồn tại ở path cuối.
- Không tự động tạo symlink/hardlink từ metadata entry nếu chưa có policy nghiêm ngặt.

### 5.2. Giới hạn của check-then-use

Kiểm tra `NOFOLLOW_LINKS` rồi sau đó gọi `Files.newOutputStream(...)` **không tạo một transaction bảo mật nguyên tử** giữa các bước. Kẻ tấn công có quyền sửa thư mục đích có thể đổi một directory thành symlink **giữa lúc kiểm tra và lúc mở file**. Đây là lỗi **TOCTOU — time of check to time of use**.

Nếu threat model có tiến trình khác **đồng thời sửa trực tiếp thư mục đích**, cần cơ chế mạnh hơn: isolation/quyền thư mục riêng, OS sandbox, hoặc API tương đương descriptor-relative operation với `SecureDirectoryStream` khi provider hỗ trợ. Không được tuyên bố `normalize()`, `NOFOLLOW_LINKS` hay `CREATE_NEW` đơn lẻ làm code miễn nhiễm với mọi race.

### 5.3. Mã minh họa sử dụng thư mục mới

Ví dụ ở mục 8 chủ động yêu cầu `Files.createDirectory(destination)` **phải tạo directory mới**. Nếu directory đã tồn tại, chương trình từ chối thay vì trộn entry vào dữ liệu cũ. Điều này vừa tránh overwrite, vừa giảm nguy cơ có symlink do người khác để lại trong cây thư mục. Tuy nhiên, **parent của destination vẫn phải đáng tin cậy**, và không được cho actor không tin cậy sửa trong lúc giải nén.

### 5.4. Tên trùng và collision theo filesystem

Các entry `a.txt` lặp lại có thể khiến file sau ghi đè file trước. Ngoài ra, filesystem có thể coi `A.txt` và `a.txt` là cùng một file, hoặc chuẩn hóa Unicode/tên đặc biệt theo quy tắc OS. Vì vậy ngoài việc giữ `Set<Path>` để phát hiện tên trùng sau normalize, vẫn cần **CREATE_NEW** để file tồn tại không bị ghi đè. Một extractor dành cho môi trường có tên file đặc biệt cần chính sách kiểm tra bổ sung về tên reserved, trailing dots/spaces và Unicode normalization.

## 6. Giải nén từng entry bằng ZipInputStream

`java.util.zip.ZipInputStream` hoạt động trên một input stream chứa ZIP, tiến qua từng entry bằng `getNextEntry()`:

~~~java
try (ZipInputStream zip = new ZipInputStream(
        Files.newInputStream(zipPath), StandardCharsets.UTF_8)) {
    ZipEntry entry;
    while ((entry = zip.getNextEntry()) != null) {
        // 1. Kiểm tra tên và root-bound path.
        // 2. Nếu là directory: tạo directory an toàn.
        // 3. Nếu là file: tạo file mới, copy theo chunk và đếm byte.
        // 4. closeEntry() sau khi xử lý xong entry.
        zip.closeEntry();
    }
}
~~~

Đây là **khung minh họa, không phải extractor độc lập**; trong code thật phải giải nén dữ liệu entry, không được chỉ gọi `closeEntry()` rồi coi file đã được trích xuất.

Các điểm API:

- `getNextEntry()` chuyển đến entry kế tiếp và trả `null` khi hết.
- `zip.read(buffer)` khi đang trong một entry trả **số byte đã giải nén**; `-1` là hết dữ liệu của entry ấy, **không phải** hết archive.
- `closeEntry()` kết thúc đọc entry hiện tại và chuẩn bị di chuyển đến entry tiếp.
- `ZipInputStream` không cho random lookup bằng tên như `ZipFile.getEntry(name)`.
- Mỗi entry nên có file đích riêng, nhưng không nhất thiết có directory entry cha trong ZIP.
- Với file upload/ZIP không đáng tin, không dùng `zip.readAllBytes()` rồi ghi: lượng giải nén có thể rất lớn.

### ZIP bomb và quota

Một archive có thể nén rất gọn nhưng bung thành gigabytes. **Không tin vào `ZipEntry.getSize()`** vì metadata có thể không có hoặc không đáng tin. Phải đếm **số byte thật sự trả về từ `zip.read(...)`** trong quá trình giải nén.

Các quota nên đặt rõ:

| Quota | Vì sao? |
|---|---|
| Số entry tối đa | Tránh hàng triệu file rỗng |
| Số byte giải nén mỗi entry | Tránh một file phình cực lớn |
| Tổng byte giải nén | Giới hạn dung lượng toàn archive |
| Độ dài tên entry | Chặn tên quá dài/path abuse |
| Độ sâu thư mục | Giảm rủi ro cây path cực sâu |
| Thời gian/deadline và CPU | Chống xử lý vô hạn/quá tốn CPU |
| Số lượng thư mục/file thực tế | Tránh inode exhaustion |
| Dung lượng trên disk quota | Tránh đầy phân vùng |

Các giới hạn trong demo là **giá trị minh họa**, không phải ngưỡng bảo mật chung cho mọi ứng dụng.

## 7. Chiến lược extractor an toàn: dữ liệu, kiểm tra, ghi, hoàn tất

Một quy trình có thể triển khai gồm:

1. Xác định đường dẫn ZIP đầu vào (nguồn) và chọn **parent đáng tin** cho output.
2. Tạo **thư mục output mới**; nếu đã có, từ chối hoặc thực hiện chính sách staging/merge riêng.
3. Đọc `ZipEntry.getName()`, kiểm tra chiều dài và từng segment, từ chối ký tự phân tách/`..`/đường dẫn tuyệt đối/drive.
4. Resolve path vào root đã canonicalize, normalize, xác minh `target.startsWith(root)` và không bằng root.
5. Dùng set để phát hiện entry path trùng sau normalize.
6. Với directory: tạo từng directory component, không đi qua symlink.
7. Với regular file: tạo file mới bằng `CREATE_NEW` rồi stream-decompress theo chunk.
8. Mỗi chunk đọc được phải cộng vào per-entry và total budget; dừng **trước khi ghi vượt quota**.
9. Gọi `closeEntry()` và xử lý ngoại lệ CRC/corrupt ZIP theo chính sách; nếu lỗi file, dọn file dở.
10. Sau toàn bộ archive: hoàn tất stream; quyết định publish output vào vị trí cuối chỉ khi archive và cấu trúc đã được xác nhận đầy đủ.

**Chú ý:** nếu fail ở entry thứ mười thì chín entry trước có thể đã được tạo. Vì thế phương án production thường dùng **staging directory**, chỉ công bố/di chuyển sang đích chính khi toàn bộ giải nén thành công; phải xác định atomicity/overwrite của thao tác di chuyển trên filesystem thực tế. Các thay đổi đa file không tự trở thành transaction.

## 8. Chương trình Java 17+ hoàn chỉnh: giải nén an toàn ở mức cơ bản

Ví dụ sau tạo một ZIP tốt có hai file, một ZIP độc hại có tên `../escape.txt`, và thử giải nén cả hai. Chương trình yêu cầu **thư mục đích mới và trống**, chặn path traversal và tên bất thường, không theo symlink directory đã tồn tại, không overwrite file, giới hạn kích thước giải nén và dọn tệp tạm.

Đây là ví dụ thiết kế **đơn giản, có thể chạy**, không phải sandbox chống lại đối thủ có quyền race sửa chính filesystem; xem mục 5 về TOCTOU.

**Tệp: `SafeZipExtractDemo.java`**

~~~java
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class SafeZipExtractDemo {
    private static final int MAX_ENTRIES = 100;
    private static final int MAX_NAME_LENGTH = 240;
    private static final int MAX_DEPTH = 16;
    private static final long MAX_ENTRY_BYTES = 1_000_000L;
    private static final long MAX_TOTAL_BYTES = 3_000_000L;

    // Kiểm tra tên ZIP theo policy portable, rồi tạo path dưới root.
    private static Path safeTarget(Path root, String name)
            throws IOException {
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH
                || name.startsWith("/") || name.indexOf('\\') >= 0
                || name.indexOf('\0') >= 0) {
            throw new IOException("Unsafe ZIP entry path");
        }

        String body = name.endsWith("/")
                ? name.substring(0, name.length() - 1)
                : name;
        String[] parts = body.split("/", -1);
        if (parts.length > MAX_DEPTH) {
            throw new IOException("Unsafe ZIP entry path");
        }

        for (String part : parts) {
            if (part.isEmpty() || part.equals(".")
                    || part.equals("..") || part.indexOf(':') >= 0) {
                throw new IOException("Unsafe ZIP entry path");
            }
        }

        Path target = root.resolve(name).normalize();
        if (target.equals(root) || !target.startsWith(root)) {
            throw new IOException("Unsafe ZIP entry path");
        }
        return target;
    }

    // Duyệt từng directory component và không chấp nhận symlink.
    private static void ensureDirs(Path root, Path dir)
            throws IOException {
        Path current = root;
        for (Path component : root.relativize(dir)) {
            current = current.resolve(component);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isDirectory(
                        current, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Unsafe directory component");
                }
            } else {
                Files.createDirectory(current);
            }
        }
    }

    static long extract(Path archive, Path newDestination)
            throws IOException {
        // Yêu cầu directory chưa tồn tại. Parent phải đáng tin.
        Path root = Files.createDirectory(newDestination).toRealPath();
        Set<Path> seen = new HashSet<>();
        int entries = 0;
        long total = 0;

        try (ZipInputStream zip = new ZipInputStream(
                Files.newInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;

            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) {
                    throw new IOException("Too many entries");
                }

                Path target = safeTarget(root, entry.getName());
                if (!seen.add(target)) {
                    throw new IOException("Duplicate ZIP path");
                }

                if (entry.isDirectory()) {
                    ensureDirs(root, target);
                    zip.closeEntry();
                    continue;
                }

                ensureDirs(root, target.getParent());
                boolean created = false;
                try {
                    try (OutputStream out = Files.newOutputStream(
                            target, StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE)) {
                        created = true;
                        byte[] buffer = new byte[8192];
                        long count = 0;

                        for (int n; (n = zip.read(buffer)) != -1; ) {
                            if (n == 0) {
                                throw new IOException(
                                        "No decompression progress");
                            }
                            if (n > MAX_ENTRY_BYTES - count
                                    || n > MAX_TOTAL_BYTES - total) {
                                throw new IOException(
                                        "Decompressed data limit");
                            }

                            out.write(buffer, 0, n);
                            count += n;
                            total += n;
                        }
                    }
                    zip.closeEntry();
                } catch (IOException | RuntimeException ex) {
                    if (created) {
                        try {
                            Files.deleteIfExists(target);
                        } catch (IOException cleanup) {
                            ex.addSuppressed(cleanup);
                        }
                    }
                    throw ex;
                }
            }
        }
        return total;
    }

    // Chỉ phục vụ demo: tạo hai archive nhỏ để kiểm thử.
    private static void createZip(
            Path archive, String[] names, String[] bodies)
            throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(
                Files.newOutputStream(archive),
                StandardCharsets.UTF_8)) {
            for (int i = 0; i < names.length; i++) {
                zip.putNextEntry(new ZipEntry(names[i]));
                zip.write(bodies[i].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }

    public static void main(String[] args) throws IOException {
        Path work = Files.createTempDirectory("zip-safe-example-");
        try {
            Path good = work.resolve("good.zip");
            Path evil = work.resolve("evil.zip");

            createZip(
                    good,
                    new String[]{"docs/a.txt", "docs/b.txt"},
                    new String[]{"Hello ZIP", "Java 17"});
            createZip(
                    evil,
                    new String[]{"../escape.txt"},
                    new String[]{"not allowed"});

            Path goodOut = work.resolve("good-out");
            long copied = extract(good, goodOut);
            System.out.println("bytes=" + copied);
            System.out.println(
                    "a=" + Files.readString(
                            goodOut.resolve("docs/a.txt")));
            System.out.println(
                    "b=" + Files.readString(
                            goodOut.resolve("docs/b.txt")));

            try {
                extract(evil, work.resolve("bad-out"));
                System.out.println("blocked=false");
            } catch (IOException rejected) {
                System.out.println(
                        "blocked=" + rejected.getMessage());
            }

            System.out.println(
                    "escaped=" + Files.exists(
                            work.resolve("escape.txt")));
        } finally {
            // Work là thư mục tạm mới do demo sở hữu.
            try (Stream<Path> paths = Files.walk(work)) {
                for (Path p : paths
                        .sorted(Comparator.reverseOrder())
                        .toList()) {
                    Files.deleteIfExists(p);
                }
            }
        }
    }
}
~~~

**Biên dịch/chạy với JDK 17+:**

~~~bash
javac SafeZipExtractDemo.java
java SafeZipExtractDemo
~~~

**Stdout đã chạy kiểm tra trên JDK:**

~~~text
bytes=16
a=Hello ZIP
b=Java 17
blocked=Unsafe ZIP entry path
escaped=false
~~~

### Giải thích từng kết quả

- File `docs/a.txt` chứa chuỗi UTF-8 `Hello ZIP` (9 byte); `docs/b.txt` chứa `Java 17` (7 byte), nên tổng giải nén `16` byte.
- Dù archive không có entry `docs/` riêng, `ensureDirs` tạo directory cần thiết trước khi ghi file.
- `Files.newOutputStream(..., CREATE_NEW, WRITE)` yêu cầu path file đích chưa tồn tại; nếu đã là file hoặc symlink, thao tác tạo mới không ghi đè.
- `../escape.txt` bị từ chối **trước khi tạo file** nhờ kiểm tra từng path component; thông báo `blocked=Unsafe ZIP entry path`.
- `escaped=false` xác nhận chương trình không tạo `escape.txt` ngoài thư mục `bad-out` trong demo này.
- `Files.createTempDirectory` cấp cho demo một parent tạm riêng, và `finally` xóa các file mà chính demo tạo.

### Giới hạn an toàn của demo

1. **Không bảo đảm chống race của tiến trình khác** được quyền sửa parent/output cùng lúc; đây là vấn đề `TOCTOU`. Trên server, chỉ giải nén vào thư mục riêng do service sở hữu với quyền hạn thích hợp, hoặc dùng primitive chống race.
2. **Không atomic cho cả archive:** nếu entry sau bị lỗi, entry trước có thể vẫn tồn tại trong `newDestination`. Trong production, giải nén vào staging, kiểm tra toàn bộ, rồi publish theo chính sách. Phải cleanup staging khi thất bại.
3. **Không tái hiện symlink/hardlink/permission/ownership metadata** từ ZIP. Đây là quyết định giảm bề mặt tấn công của ví dụ: entry được xử lý là directory hoặc regular file, không tạo link từ metadata. Nếu yêu cầu bảo tồn symlink, cần kiểm tra loại entry, link target, permission và ranh giới root theo threat model riêng.
4. **Quota là ngưỡng minh họa**: tối đa 100 entries, 240 ký tự tên, 16 levels, 1.000.000 byte/entry và tổng 3.000.000 byte. Với server thật còn cần deadline, CPU quota, số directory tạo thực tế, disk quota và hạn mức request.
5. **Dọn file tạm trong finally** có thể thất bại trên môi trường filesystem đặc biệt hoặc nếu file bị giữ bởi actor khác. Production cần logging và retry/retention chính sách thích hợp, không xóa thư mục không thuộc quyền sở hữu của mình.
6. **Dữ liệu source do demo tự tạo** và output đúng charset UTF-8. Với ZIP của bên thứ ba, charset entry names và payload có thể khác nhau; cần áp dụng chính sách hỗ trợ thực tế.

## 9. Một số cải tiến cho hệ thống production

### 9.1. Staging trước khi publish

Thay vì ghi trực tiếp vào thư mục hiển thị cho người dùng, nên:

1. Tạo staging directory mới trong khu vực service kiểm soát.
2. Giải nén vào staging sau khi validate entry.
3. Kiểm tra số file, kích thước, định dạng, checksum hay chữ ký nếu cần.
4. Chỉ khi toàn bộ archive thành công mới chuyển/đổi tên staging sang vị trí đích cuối.
5. Có cách rollback/delete staging khi fail và cleanup khi service bị crash.

**Không tự cho rằng `Files.move(..., ATOMIC_MOVE)` luôn được hỗ trợ** hoặc có nghĩa transaction trên mọi file store. Nó có thể ném `AtomicMoveNotSupportedException`; nếu target đã tồn tại, yêu cầu overwrite khác nhau theo policy. Trên nhiều filesystem, rename thư mục chỉ atomic trong những điều kiện cụ thể và vẫn không thay thế các bước xác nhận integrity.

### 9.2. Sử dụng thư mục đích đã tồn tại

Nếu product bắt buộc giải nén vào thư mục có sẵn, policy phải rõ:

- Có được ghi đè file cùng tên không?
- Entry trùng tên trong chính archive được chấp nhận không?
- Nếu target có symlink, junction/reparse point (Windows) hay hardlink thì làm gì?
- Các file ngoài archive đang có trong thư mục cần giữ nguyên chứ?
- Có cần đối chiếu file permission, ownership hoặc timestamps không?
- Có tiến trình khác được quyền tạo/xóa/đổi đường dẫn trong lúc giải nén không?

Mẫu demo **không hỗ trợ merge vào existing directory theo thiết kế**; phải có logic bổ sung thay vì thay `createDirectory` bằng `createDirectories` rồi coi mọi thứ vẫn an toàn.

### 9.3. Kiểm tra Unicode, case folding và tên hệ điều hành

Trên các filesystem khác nhau, nhiều tên entry có thể chỉ về cùng file hoặc tên bị từ chối:

- `a.txt` và `A.txt` có thể đụng nhau khi hệ thống không phân biệt hoa/thường.
- Unicode có thể có nhiều biểu diễn normalization.
- Trên Windows, các tên device reserved như `CON`, `PRN`, `AUX` hoặc `NUL` có giới hạn riêng, kể cả khi thêm extension.
- Tên có dấu chấm/khoảng trắng cuối có thể bị xử lý đặc biệt tùy provider.
- `:` có thể biểu diễn Alternate Data Stream trên NTFS, nên policy portable của ví dụ cấm nó.

`CREATE_NEW` từ chối file thực tế đã tồn tại, nhưng hệ thống production nên xác thực trước tên không hợp lệ và báo lỗi rõ ràng.

### 9.4. Chống Zip bomb và disk exhaustion

Ngay cả khi có quota byte, archive có thể khai báo hoặc tạo vô số entry rỗng. Phải kết hợp **count quota + byte quota + depth/path quota + CPU/deadline + disk quota**. Đếm byte được giải nén thực tế quan trọng vì kích thước metadata của ZIP có thể không biết trước (`ZipEntry.getSize() == -1`) hoặc không nên tin.

Không dùng `ZipInputStream.readAllBytes()` cho entry không đáng tin. Trong demo, mảng đệm cố định 8.192 byte, mỗi chunk được kiểm tra giới hạn **trước khi ghi**. Phép so sánh `n > MAX_TOTAL_BYTES - total` cũng tránh cộng tràn số khi dùng quota dương.

### 9.5. Integrity và chữ ký

ZIP thường có **CRC-32** để phát hiện lỗi truyền/lưu trữ. CRC-32 **không phải cơ chế chống sửa đổi có chủ đích**. Nếu archive được dùng làm gói triển khai/phần mềm/cấu hình quan trọng, cần hệ thống xác thực chữ ký số/MAC hay hash được đối chiếu từ nguồn tin cậy. File hợp lệ theo CRC vẫn có thể chứa payload nguy hiểm hoặc file path gây rủi ro nếu không kiểm tra.

`ZipInputStream` đọc local entry headers; `ZipFile` dùng central directory để truy cập entry theo tên. Với archive không tin cậy có metadata không nhất quán giữa local và central directory, việc chọn parser/API có thể ảnh hưởng chính sách xử lý. Không tự suy luận chỉ cần scan một danh sách tên là đã đảm bảo đúng mọi byte/entry mà extractor sẽ xử lý. **Validate ngay khi xử lý từng entry thực tế**.

## 10. Những lỗi thường gặp

1. **Lấy `entry.getName()` ghép vào output và ghi ngay.** Dễ có Zip Slip.
2. **Chỉ so `target.toString().startsWith(root.toString())`.** Có thể nhầm `/root/outside` với `/root/out`.
3. **Chỉ dùng `normalize()` rồi tin rằng đã xử lý symlink.** Normalize không duyệt filesystem.
4. **Không từ chối `..` vì sau normalize vẫn nằm trong root.** Làm policy không nhất quán, tăng nguy cơ bypass.
5. **Chỉ lọc dấu `/` mà quên backslash trên Windows.** Có thể bỏ lọt traversal.
6. **Tin rằng `Path.isAbsolute()` trên Linux chặn được `C:\...` trên Windows.** Cần kiểm tra định dạng tên portable trước.
7. **Dùng `Files.createDirectories` cho bất kỳ parent nào trong cây đích cũ.** Có thể đi qua symlink.
8. **Dùng `Files.newOutputStream(target)` mặc định.** Có thể truncate file đã tồn tại; dùng `CREATE_NEW` khi không cho overwrite.
9. **Cho phép nhiều entry cùng tên tự ghi đè.** Có thể thay thế payload hợp lệ bằng payload nguy hiểm.
10. **Dùng `readAllBytes()` hoặc `Files.copy` không có hạn mức với ZIP không đáng tin.** Có thể hết RAM/disk vì Zip bomb.
11. **Tin `ZipEntry.getSize()` luôn có giá trị đúng.** Có thể `-1` hoặc metadata không đáng tin.
12. **Gọi `zip.read()==-1` rồi cho rằng hết cả archive.** Đó là EOF entry; phải tiếp tục `getNextEntry()`.
13. **Không đóng `ZipInputStream`/output stream.** Gây leak hoặc file ghi thiếu.
14. **Nhầm entry `docs/` là file regular.** Phải tạo directory, không ghi payload thành file.
15. **Coi extraction thất bại là đã rollback hết file.** Các entry ghi trước đó có thể còn tồn tại.
16. **Dùng `NOFOLLOW_LINKS` + kiểm tra rồi tuyên bố loại bỏ hoàn toàn TOCTOU.** Nếu actor khác có quyền thay filesystem, vẫn cần cách ly và primitive mạnh hơn.
17. **Tin CRC-32 là chữ ký số của nhà phát hành.** CRC không chống giả mạo.
18. **Giải nén từ archive ZIP được upload vào một thư mục chung có quyền ghi công khai.** Tăng nguy cơ đường dẫn bị thay đổi trong lúc chạy.

## 11. Tự kiểm tra

**Câu 1.** Vì sao `root.resolve(entry.getName()).normalize().startsWith(root)` cần thiết nhưng chưa đủ?

**Đáp án:** Nó kiểm tra containment theo thành phần `Path`, nhưng chưa chặn đủ tên platform-specific, symlink trong filesystem, TOCTOU, overwrite, duplicate và resource exhaustion.

**Câu 2.** Entry tên `../../escape.txt` được đặt dưới `/data/uploads/job1`. Có được tạo file không?

**Đáp án:** Không. Tên chứa `..` phải bị từ chối. Nếu dùng trực tiếp, nó có thể thoát khỏi `job1` rồi ghi vào thư mục khác.

**Câu 3.** ZIP chỉ có `docs/a.txt` mà không có entry `docs/`. Có thể giải nén đúng không?

**Đáp án:** Có. Sau khi validate tên, extractor tạo an toàn directory cha `docs` rồi tạo file `a.txt`.

**Câu 4.** Vì sao `CREATE_NEW` hữu ích hơn mở output stream mặc định?

**Đáp án:** Vì nó yêu cầu tạo file mới và thất bại nếu file/path cuối đã tồn tại (kể cả symlink), tránh ghi đè/truncate theo cơ chế mở mặc định.

**Câu 5.** Vì sao vẫn cần chống Zip bomb dù kiểm tra path đã an toàn?

**Đáp án:** Path validation chỉ giới hạn nơi ghi; archive nhỏ có thể tạo quá nhiều file hoặc dữ liệu giải nén khổng lồ, làm cạn CPU/RAM/disk.

**Câu 6.** Thư mục output root do ứng dụng sở hữu nhưng tiến trình khác được phép đổi tên thành phần con đồng thời. Kiểm tra `NOFOLLOW_LINKS` có đủ an toàn không?

**Đáp án:** Không. Check-then-open có thể bị race; cần isolation, quyền directory riêng hoặc API chống TOCTOU phù hợp.

**Câu 7.** Extraction thất bại sau khi ghi được 5/10 entry. Các file trước đó có tự mất đi không?

**Đáp án:** Không. Muốn hành vi gần transaction cần staging, chính sách cleanup và publish sau khi kiểm tra thành công.

### Tài liệu Java 17 chính thức

- [ZipInputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/ZipInputStream.html)
- [ZipEntry](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/ZipEntry.html)
- [ZipOutputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/ZipOutputStream.html)
- [ZipFile](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/ZipFile.html)
- [Path: resolve, normalize, startsWith](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Path.html)
- [Files: createDirectory, newOutputStream, exists, isDirectory](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html)
- [StandardOpenOption.CREATE_NEW](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/StandardOpenOption.html#CREATE_NEW)
- [SecureDirectoryStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/SecureDirectoryStream.html)


</details>

- [Quay lại đầu trang](#back-to-top)
