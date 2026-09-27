# Mô hình I/O trong Java

## <a id="io-data-flow">I/O là gì?</a>

Một chương trình chỉ tính toán trong bộ nhớ thì mọi dữ liệu của nó chỉ tồn tại trong **tiến trình (process)** đang chạy, tức một phiên bản cụ thể của chương trình đang được hệ điều hành thực thi. Khi tiến trình dừng, các object, biến cục bộ và mảng trong RAM không tự trở thành dữ liệu bền vững. Chương trình cũng không thể tự nhận nội dung từ bàn phím, đọc một file, gửi dữ liệu qua mạng hay ghi kết quả ra nơi khác nếu không có cơ chế trao đổi dữ liệu với thế giới bên ngoài. **Ranh giới tiến trình (process boundary)** là ranh giới giữa dữ liệu/tài nguyên nằm trong tiến trình đó và tài nguyên bên ngoài mà chương trình phải giao tiếp qua I/O.

**I/O (Input/Output)** là nhóm cơ chế dùng để đưa dữ liệu **vào** chương trình và đưa dữ liệu **ra khỏi** chương trình.

Có thể bắt đầu bằng mô hình rất đơn giản:

```text
source ── input ──> chương trình ── output ──> sink
```

- **source (nguồn)** là nơi dữ liệu đi ra, ví dụ file, **socket** (endpoint mà chương trình dùng để trao đổi dữ liệu qua mạng), bàn phím hoặc một vùng nhớ.
- **sink (đích)** là nơi nhận dữ liệu, ví dụ file, socket, màn hình hoặc một vùng nhớ.
- **payload** là dữ liệu đang được truyền.
- **resource boundary (ranh giới tài nguyên)** là chỗ mã Java bắt đầu làm việc với một tài nguyên có vòng đời riêng, thường do hệ điều hành hoặc một thành phần bên ngoài tiến trình quản lý.

Ví dụ một ứng dụng ghi dòng chữ `Xin chào Java ☕` vào file rồi đọc lại có luồng:

```text
String trong RAM
    ↓ encode thành bytes
file tạm trên filesystem
    ↓ đọc bytes + decode
String mới trong RAM
```

Nếu chỉ giữ chuỗi ban đầu trong RAM, dữ liệu biến mất khi tiến trình kết thúc. Khi ghi xuống file, chương trình tạo ra một biểu diễn có thể tồn tại độc lập với object đang nằm trong **heap**, vùng bộ nhớ JVM thường dùng để chứa các object Java.

**Hệ thống tệp (filesystem)** là cơ chế của hệ điều hành dùng để tổ chức file, directory và thông tin đi kèm của chúng. Thông tin đi kèm đó gọi là **siêu dữ liệu (metadata)**, ví dụ file có tồn tại hay không, loại entry, kích thước hoặc thời điểm sửa đổi; metadata khác với chính nội dung bytes của file.

**Buffering (đệm dữ liệu)** khác với **nơi lưu/tài nguyên (storage/resource)**. Buffer chỉ là vùng nhớ tạm dùng để gom dữ liệu và giảm số thao tác I/O nhỏ; nó không tự làm dữ liệu trở nên bền vững. File, socket hoặc thiết bị mới là tài nguyên nguồn/đích mà buffer hỗ trợ giao tiếp.

Trong module này, các thuật ngữ sẽ xuất hiện theo một lộ trình có quan hệ với nhau:

```text
I/O data flow
    ↓ dữ liệu phải có đơn vị truyền
byte stream / character stream
    ↓ nhiều thao tác nhỏ có thể tốn chi phí
buffering
    ↓ file còn có tên, đường dẫn và metadata
File → Path / Files
    ↓ NIO tách vùng chứa dữ liệu khỏi kênh truyền
Buffer / Channel / FileChannel
    ↓ tài nguyên bên ngoài phải có người sở hữu vòng đời
resource management
    ↓ một số dữ liệu có thể được biểu diễn thành object graph
serialization
    ↓ cuối cùng phải chọn abstraction phù hợp với bài toán
choosing I/O
```

Các thành phần chính và vai trò của chúng:

| Thành phần | Vai trò |
| --- | --- |
| Byte stream | Đọc/ghi dữ liệu thô dưới dạng byte; khi byte mang các primitive có cấu trúc thì cần thêm contract về field order/kiểu dữ liệu. |
| Character stream + `Charset` | Làm việc với text và chuyển đổi đúng giữa ký tự với byte. |
| Buffering | Gom nhiều thao tác nhỏ thành các khối lớn hơn để giảm chi phí I/O. |
| `File` vs `Path` / `Files` | Biểu diễn và thao tác với vị trí/metadata trên filesystem; `File` là API cũ, `Path`/`Files` là mô hình hiện đại hơn. |
| `WatchService` | Nhận thông báo thay đổi filesystem như create/modify/delete với boundary phụ thuộc provider/nền tảng. |
| `Buffer` / `Channel` / `FileChannel` | Mô hình NIO tách vùng dữ liệu khỏi kênh truyền; `ByteOrder` quyết định cách hiểu primitive nhiều byte và channel có thể hỗ trợ multi-buffer transfer. |
| `AsynchronousFileChannel` | File I/O theo vị trí nhưng completion xảy ra bất đồng bộ thay vì giữ thread gọi chờ tới khi xong. |
| Resource management | Xác định ai sở hữu tài nguyên và khi nào phải `close()`. |
| Serialization | Chuyển trạng thái object theo một format tuần tự hóa cụ thể. |
| Choosing I/O | Chọn mô hình/API đơn giản và đúng với loại dữ liệu, quy mô và kiểu thao tác. |

**NIO (New I/O)** là nhóm API I/O mới hơn của Java, gồm các khái niệm như `Buffer`, `Channel` và `java.nio.file`. Ở đây chỉ cần xem nó như một nhánh abstraction khác sẽ được học sau; không phải mọi API NIO đều là non-blocking.

Điểm cần giữ từ đầu là: I/O không đồng nghĩa với “đọc file”. File chỉ là một loại nguồn/đích. Cùng mental model nguồn → dữ liệu → đích còn áp dụng cho network, console, memory stream và nhiều thiết bị khác.

## <a id="bytes-vs-characters">Byte và character</a>

Máy lưu trữ và truyền dữ liệu vật lý dưới dạng byte. Tuy nhiên, mã ứng dụng thường muốn làm việc với text. Hai nhu cầu này tạo ra hai mức abstraction quan trọng:

```text
binary data            text
    ↓                    ↓
byte abstraction     character abstraction
```

**Byte stream** xem payload là dãy byte và không tự gán ý nghĩa “chữ” cho chúng. Đây là lựa chọn tự nhiên cho ảnh, ZIP, PDF, dữ liệu mã hóa hoặc bất kỳ format nhị phân nào.

**Character stream** xem payload là dữ liệu ký tự. Khi ký tự phải đi qua file hoặc network, cần một **charset** để chuyển giữa ký tự Java và byte. Với UTF-8:

```text
characters ── encode UTF-8 ──> bytes
bytes      ── decode UTF-8 ──> characters
```

Vì vậy không nên lấy một dãy byte tùy ý rồi coi mỗi byte là một ký tự. Một ký tự Unicode có thể cần nhiều byte trong UTF-8, và một `char` Java cũng chỉ là một UTF-16 code unit, không phải lúc nào cũng tương ứng với một Unicode code point hoàn chỉnh.

Ví dụ:

```java
String text = "Xin chào Java ☕";
byte[] utf8 = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
String restored = new String(utf8, java.nio.charset.StandardCharsets.UTF_8);

System.out.println(text.equals(restored)); // true
```

Byte stream và character stream không cạnh tranh nhau. Character stream thường nằm **trên** một nguồn/đích byte và thêm bước encode/decode. Chapter tiếp theo sẽ bắt đầu từ tầng thấp hơn là `InputStream` và `OutputStream`, sau đó mới đặt `Reader`/`Writer` lên trên.

## <a id="blocking-io-boundary">Mô hình blocking I/O</a>

Khi mã gọi một thao tác I/O, dữ liệu không nhất thiết đã sẵn sàng trong RAM. Chương trình có thể phải chờ filesystem, thiết bị hoặc peer mạng.

Với blocking I/O truyền thống, lời gọi như `read()` có thể chưa trả về ngay. Từ góc nhìn của đoạn mã đang chạy:

```text
gọi read()
    ↓
dữ liệu chưa có
    ↓
thread chờ
    ↓
có dữ liệu / EOF / lỗi
    ↓
read() trả về hoặc ném exception
```

**Thread** là một luồng thực thi bên trong process. “Blocking” mô tả việc thread gọi API phải chờ lời gọi hoàn tất; nó không có nghĩa CPU luôn bận quay vòng. Trong thời gian chờ I/O, hệ điều hành/JVM có thể để thread ngủ và dùng CPU cho việc khác.

Ngược lại, với **non-blocking I/O**, thao tác được thiết kế để không giữ thread đứng chờ cho tới khi tài nguyên sẵn sàng. Lời gọi có thể trả quyền điều khiển lại sớm để chương trình tiếp tục việc khác và xử lý dữ liệu khi cơ chế readiness/event báo tài nguyên đã sẵn sàng. Đây là khác biệt về cách chờ và điều phối công việc, không phải lời hứa rằng thao tác luôn nhanh hơn.

Điều này tạo ra ranh giới quan trọng giữa **CPU-bound work** và **I/O-bound work**. CPU-bound nghĩa là thời gian chủ yếu bị chi phối bởi tính toán trên CPU; I/O-bound nghĩa là thời gian chủ yếu bị chi phối bởi việc chờ file, thiết bị, socket hoặc tài nguyên bên ngoài.

Không phải mọi API I/O của Java đều bắt buộc dùng blocking model. NIO còn có channel và một số cơ chế non-blocking cho những bài toán phù hợp. Ở các chapter đầu, ta tập trung vào stream/file I/O đồng bộ để xây mental model nền tảng.

## <a id="resource-lifecycle">Vòng đời tài nguyên I/O</a>

Một object Java có thể được garbage collector thu hồi khi không còn tham chiếu mạnh. Nhưng **file descriptor** là mã định danh mà hệ điều hành dùng cho một file đang mở, còn **native handle** là tên gọi rộng hơn cho tham chiếu tài nguyên ở tầng hệ điều hành như file, socket hoặc thiết bị. Những tài nguyên này nằm ngoài heap Java, nên chương trình cần kết thúc việc sử dụng chúng theo thời điểm xác định.

Vì vậy nhiều abstraction I/O có vòng đời:

```text
acquire/open
    ↓
read/write
    ↓
flush nếu cần
    ↓
close
```

Ví dụ cơ bản:

`Path`/`Files` xuất hiện trong ví dụ chỉ để tạo và xóa một file tạm an toàn; người học **chưa cần hiểu API filesystem này ở đây**. Chapter `File` và `Path/Files` phía sau sẽ định nghĩa chúng từ đầu.

```java
java.nio.file.Path temp = java.nio.file.Files.createTempFile("io-", ".txt");

try (java.io.OutputStream out =
         new java.io.FileOutputStream(temp.toFile())) {
    out.write("Xin chào Java ☕"
        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
}

java.nio.file.Files.deleteIfExists(temp);
```

`try-with-resources` gọi `close()` tự động kể cả khi phần thân ném exception. Đây là công cụ chính để ràng buộc vòng đời tài nguyên với một **phạm vi code (scope)** rõ ràng.

Không phải mọi stream đều giữ tài nguyên hệ điều hành. Ví dụ `ByteArrayInputStream` chỉ đọc từ mảng byte trong RAM và `close()` của nó không giải phóng file descriptor. Tuy vậy, khi code nhận một `InputStream` chung, cần hiểu **ai sở hữu stream và ai chịu trách nhiệm đóng nó**. Chapter Resource Management sau này sẽ đào sâu ownership và chuỗi wrapper.

Từ mental model này, chapter kế tiếp có thể đi vào lớp abstraction thấp nhất của stream I/O: dữ liệu được đọc và ghi dưới dạng byte như thế nào.
