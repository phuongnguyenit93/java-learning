# Mô hình luồng dữ liệu I/O và vòng đời tài nguyên

## <a id="io-data-flow">I/O là gì?</a>

Một chương trình cần một mô hình thống nhất để **đọc dữ liệu từ nguồn (source)** và **ghi dữ liệu tới đích (sink)**. Nguồn hoặc đích có thể là tệp, bảng điều khiển, socket, thiết bị hoặc thậm chí một vùng nhớ nằm ngay trong tiến trình hiện tại. I/O tồn tại để mô hình hóa những lần trao đổi dữ liệu đó mà không buộc mã ứng dụng phải tạo một cơ chế hoàn toàn khác cho từng loại đầu cuối.

Khi nguồn hoặc đích là tài nguyên nằm ngoài vùng nhớ Java thông thường, chẳng hạn tệp hoặc socket, chương trình còn đi qua một **ranh giới tài nguyên (resource boundary)** có vòng đời riêng. Đối tượng, biến cục bộ và mảng trong RAM không tự trở thành dữ liệu bền vững khi tiến trình kết thúc; dữ liệu cần tồn tại lâu hơn các đối tượng đó phải được ghi tới một nơi lưu trữ phù hợp.

**I/O (Input/Output - vào/ra)** là nhóm cơ chế dùng để đưa dữ liệu **vào** chương trình và đưa dữ liệu **ra khỏi** chương trình.

Có thể bắt đầu bằng mô hình rất đơn giản:

```text
nguồn ── dữ liệu vào ──> chương trình ── dữ liệu ra ──> đích
```

- **Nguồn (source)** là nơi dữ liệu đi ra, ví dụ tệp, **socket** (đầu cuối mạng mà chương trình dùng để trao đổi dữ liệu), bàn phím hoặc một vùng nhớ.
- **Đích (sink)** là nơi nhận dữ liệu, ví dụ tệp, socket, màn hình hoặc một vùng nhớ.
- **Dữ liệu truyền (payload)** là phần dữ liệu đang được di chuyển.
- **Ranh giới tài nguyên (resource boundary)** là chỗ mã Java bắt đầu làm việc với một tài nguyên có vòng đời riêng, thường do hệ điều hành hoặc một thành phần bên ngoài tiến trình quản lý. Không phải mọi mô hình I/O đều đi qua ranh giới này; luồng đọc từ mảng byte trong RAM là một ví dụ.

Ví dụ một ứng dụng ghi dòng chữ `Xin chào Java ☕` vào tệp rồi đọc lại có luồng:

```text
String trong RAM
    ↓ mã hóa thành byte
tệp tạm trên hệ thống tệp
    ↓ đọc byte + giải mã
String mới trong RAM
```

Nếu chỉ giữ chuỗi ban đầu trong RAM, dữ liệu biến mất khi tiến trình kết thúc. Khi ghi xuống tệp, chương trình tạo ra một biểu diễn có thể tồn tại độc lập với đối tượng đang nằm trong **heap**, vùng bộ nhớ JVM thường dùng để chứa các đối tượng Java.

**Hệ thống tệp (filesystem)** là cơ chế của hệ điều hành dùng để tổ chức tệp, thư mục và thông tin đi kèm của chúng. Thông tin đi kèm đó gọi là **siêu dữ liệu (metadata)**, ví dụ một mục có tồn tại hay không, loại mục, kích thước hoặc thời điểm sửa đổi; siêu dữ liệu khác với chính nội dung byte của tệp.

**Đệm dữ liệu (buffering)** khác với **nơi lưu trữ hoặc tài nguyên bên dưới**. Bộ đệm chỉ là vùng nhớ tạm dùng để gom dữ liệu và giảm số thao tác I/O nhỏ; nó không tự làm dữ liệu trở nên bền vững. Tệp, socket hoặc thiết bị mới là nguồn/đích mà bộ đệm hỗ trợ giao tiếp.

Trong module này, các thuật ngữ sẽ xuất hiện theo một lộ trình có quan hệ với nhau:

```text
luồng dữ liệu I/O
    ↓ dữ liệu cần một đơn vị truyền
luồng byte
    ↓ văn bản cần tầng chuyển đổi ký tự
luồng ký tự
    ↓ nhiều thao tác nhỏ có thể tốn chi phí
lớp bọc và bộ đệm
    ↓ tệp còn có tên, đường dẫn và siêu dữ liệu
File → Path / Files
    ↓ NIO tách vùng chứa dữ liệu khỏi đường vận chuyển
Buffer / Channel / FileChannel
    ↓ tài nguyên bên ngoài cần bên chịu trách nhiệm cho vòng đời
quản lý tài nguyên
    ↓ một số dữ liệu có thể biểu diễn thành đồ thị đối tượng
tuần tự hóa
    ↓ cuối cùng chọn mô hình đơn giản nhưng đúng
lựa chọn I/O
```

Các thành phần chính và vai trò của chúng:

| Thành phần | Vai trò |
| --- | --- |
| Luồng byte | Đọc/ghi dữ liệu thô dưới dạng byte; khi byte mang các giá trị nguyên thủy có cấu trúc thì cần thêm quy tắc rõ ràng về thứ tự trường và kiểu dữ liệu. |
| Luồng ký tự + `Charset` | Làm việc với văn bản và chuyển đổi đúng giữa ký tự với byte. |
| Đệm dữ liệu | Gom nhiều thao tác nhỏ thành các khối lớn hơn để giảm chi phí I/O. |
| `File` so với `Path` / `Files` | Biểu diễn và thao tác với vị trí/siêu dữ liệu trên hệ thống tệp; `File` là API cũ, `Path`/`Files` là mô hình hiện đại hơn. |
| `WatchService` | Nhận thông báo thay đổi hệ thống tệp như tạo/sửa/xóa với ranh giới phụ thuộc nhà cung cấp hệ thống tệp và nền tảng. |
| `Buffer` / `Channel` / `FileChannel` | Mô hình NIO tách vùng dữ liệu khỏi kênh truyền; `ByteOrder` quyết định cách hiểu giá trị nguyên thủy nhiều byte và channel có thể truyền qua nhiều bộ đệm. |
| `AsynchronousFileChannel` | I/O tệp theo vị trí với kết quả hoàn tất bất đồng bộ thay vì giữ luồng thực thi gọi API chờ tới khi xong. |
| Quản lý tài nguyên | Xác định ai chịu trách nhiệm cho tài nguyên và khi nào phải `close()`. |
| Tuần tự hóa | Chuyển trạng thái đối tượng theo một định dạng tuần tự hóa cụ thể. |
| Lựa chọn I/O | Chọn mô hình/API đơn giản và đúng với loại dữ liệu, quy mô và kiểu thao tác. |

**NIO (New I/O)** là nhóm API I/O mới hơn của Java, gồm các khái niệm như `Buffer`, `Channel` và `java.nio.file`. Ở đây chỉ cần xem nó như một họ mô hình I/O khác sẽ được học sau; không phải mọi API NIO đều là không chặn.

Điểm cần giữ từ đầu là: I/O không đồng nghĩa với “đọc tệp”. Tệp chỉ là một loại nguồn/đích. Cùng mô hình nguồn → dữ liệu → đích còn áp dụng cho mạng, bảng điều khiển, luồng trong bộ nhớ và nhiều thiết bị khác.

## <a id="bytes-vs-characters">Byte và ký tự</a>

Máy lưu trữ và truyền dữ liệu vật lý dưới dạng byte. Tuy nhiên, mã ứng dụng thường muốn làm việc với văn bản. Hai nhu cầu này tạo ra hai mức mô hình quan trọng:

```text
dữ liệu nhị phân       văn bản
    ↓                    ↓
mô hình byte          mô hình ký tự
```

**Luồng byte (byte stream)** xem dữ liệu là dãy byte và không tự gán ý nghĩa “chữ” cho chúng. Đây là lựa chọn tự nhiên cho ảnh, ZIP, PDF, dữ liệu mã hóa hoặc bất kỳ định dạng nhị phân nào.

**Luồng ký tự (character stream)** xem dữ liệu là ký tự. Khi ký tự phải đi qua tệp hoặc mạng, cần một **bộ mã ký tự (charset)** để chuyển giữa ký tự Java và byte. Với UTF-8:

```text
ký tự ── mã hóa UTF-8 ──> byte
byte  ── giải mã UTF-8 ──> ký tự
```

Vì vậy không nên lấy một dãy byte tùy ý rồi coi mỗi byte là một ký tự. Một ký tự Unicode có thể cần nhiều byte trong UTF-8, và một `char` Java cũng chỉ là một đơn vị mã UTF-16 (code unit), không phải lúc nào cũng tương ứng với một điểm mã Unicode (code point) hoàn chỉnh.

Ví dụ:

```java
String text = "Xin chào Java ☕";
byte[] utf8 = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
String restored = new String(utf8, java.nio.charset.StandardCharsets.UTF_8);

System.out.println(text.equals(restored)); // true
```

Luồng byte và luồng ký tự là hai tầng có quan hệ với nhau. Luồng ký tự thường nằm **trên** một nguồn/đích byte và thêm bước mã hóa/giải mã. Chương tiếp theo sẽ bắt đầu từ tầng thấp hơn là `InputStream` và `OutputStream`, sau đó mới đặt `Reader`/`Writer` lên trên.

## <a id="blocking-io-boundary">I/O chặn, không chặn và bất đồng bộ</a>

Khi mã gọi một thao tác I/O, dữ liệu không nhất thiết đã sẵn sàng trong RAM. Chương trình có thể phải chờ hệ thống tệp, thiết bị hoặc đầu bên kia của kết nối mạng.

Với **I/O chặn (blocking I/O)** truyền thống, lời gọi như `read()` có thể chưa trả về ngay:

```text
gọi read()
    ↓
dữ liệu chưa có
    ↓
luồng thực thi gọi API chờ
    ↓
có dữ liệu / EOF / lỗi
    ↓
read() trả về hoặc ném ngoại lệ
```

**Luồng thực thi (thread)** là một dòng thực thi bên trong tiến trình. “Chặn” nghĩa là luồng thực thi gọi API phải chờ thao tác hoàn tất. Điều đó không có nghĩa CPU phải luôn bận quay vòng; JVM và hệ điều hành có thể tạm dừng luồng thực thi trong khi CPU làm việc khác.

Với **I/O không chặn (non-blocking I/O)**, thao tác được thiết kế để không giữ luồng thực thi gọi API đứng chờ cho tới khi tài nguyên sẵn sàng. Lời gọi có thể trả quyền điều khiển sớm để chương trình tiếp tục việc khác và phản ứng khi cơ chế báo sẵn sàng/sự kiện cho biết dữ liệu có thể được xử lý. Đây là khác biệt về cách chờ và phối hợp công việc, không phải lời hứa rằng mọi thao tác đều nhanh hơn.

**I/O bất đồng bộ (asynchronous I/O)** là một chiều khác: thao tác được khởi chạy trước, còn kết quả được nhận sau khi thao tác hoàn tất, chẳng hạn qua `Future` hoặc hàm gọi lại (callback). Vì vậy “bất đồng bộ” không đồng nghĩa với “không chặn”; cơ chế thực thi bên dưới có thể khác nhau tùy nền tảng. Chương `FileChannel` phía sau sẽ dùng `AsynchronousFileChannel` để làm rõ ranh giới này.

Điều này cũng tạo ra ranh giới hữu ích giữa **công việc thiên về CPU (CPU-bound)** và **công việc thiên về I/O (I/O-bound)**. Loại thứ nhất dành phần lớn thời gian cho tính toán trên CPU; loại thứ hai dành phần lớn thời gian chờ tệp, thiết bị, socket hoặc tài nguyên bên ngoài.

Không phải mọi API I/O của Java đều chặn. NIO còn có channel và các cơ chế không chặn cho những bài toán phù hợp. Các chương đầu chủ ý dùng luồng/tệp I/O đồng bộ để xây dựng mô hình nền tảng trước.

## <a id="resource-lifecycle">Vòng đời tài nguyên I/O</a>

Một đối tượng Java có thể được **bộ thu gom rác (garbage collector)** thu hồi khi không còn tham chiếu mạnh. Trên các hệ thống dùng **file descriptor**, đây là mã định danh của hệ điều hành cho một tài nguyên I/O đang mở như tệp, socket hoặc pipe; trên nền tảng khác có thể gặp **handle gốc (native handle)** tương ứng cho tài nguyên như tệp, socket hoặc thiết bị. Những tài nguyên này nằm ngoài heap Java, nên chương trình cần kết thúc việc sử dụng chúng ở một thời điểm xác định.

Vì vậy nhiều mô hình I/O có vòng đời:

```text
mở/nhận tài nguyên
    ↓
đọc/ghi
    ↓
flush nếu cần
    ↓
close
```

Ví dụ cơ bản:

`Path`/`Files` xuất hiện trong ví dụ chỉ để tạo và xóa một tệp tạm an toàn; người học **chưa cần hiểu API hệ thống tệp này ở đây**. Các chương `File` và `Path/Files` phía sau sẽ định nghĩa chúng từ đầu.

```java
java.nio.file.Path temp = java.nio.file.Files.createTempFile("io-", ".txt");

try (java.io.OutputStream out =
         new java.io.FileOutputStream(temp.toFile())) {
    out.write("Xin chào Java ☕"
        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
}

java.nio.file.Files.deleteIfExists(temp);
```

`try-with-resources` gọi `close()` tự động kể cả khi phần thân ném ngoại lệ. Đây là cơ chế chính của Java để gắn vòng đời tài nguyên với một **phạm vi mã (scope)** rõ ràng.

Không phải mọi luồng I/O đều giữ tài nguyên hệ điều hành. Ví dụ `ByteArrayInputStream` chỉ đọc từ mảng byte trong RAM và `close()` của nó không giải phóng file descriptor. Tuy vậy, khi mã nhận một `InputStream` chung, cần hiểu **ai sở hữu luồng và ai chịu trách nhiệm đóng nó**. Chương Quản lý tài nguyên phía sau sẽ phát triển rõ mô hình quyền sở hữu này.

Từ mô hình này, chương kế tiếp có thể đi vào tầng thấp nhất của luồng I/O: Java đọc và ghi byte thô như thế nào.
