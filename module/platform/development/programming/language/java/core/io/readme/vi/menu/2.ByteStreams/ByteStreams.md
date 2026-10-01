# Luồng byte và dữ liệu nhị phân có cấu trúc

Sau chương mở đầu, ta đã biết I/O là luồng dữ liệu giữa nguồn và đích. Câu hỏi tiếp theo là: khi dữ liệu phải được giữ nguyên ở dạng nhị phân, Java biểu diễn luồng đó như thế nào?

## <a id="inputstream-outputstream">InputStream và OutputStream</a>

Trước hết, **luồng (stream)** là cách nhìn dữ liệu như một dòng giá trị đi tuần tự từ nguồn tới chương trình hoặc từ chương trình tới đích. Luồng không có nghĩa toàn bộ dữ liệu đã nằm sẵn trong RAM; mã thường xử lý dần khi dữ liệu đi qua.

Đừng nhầm **luồng I/O** ở `java.io` với **Stream API** ở `java.util.stream`. Luồng I/O là mô hình để **đọc/ghi dữ liệu từ nguồn/đích**; `Stream<T>` của Stream API là chuỗi xử lý phần tử bằng `map`, `filter`, `reduce`... Hai khái niệm có cùng từ “stream” nhưng giải quyết hai bài toán khác nhau.

`InputStream` là **mô hình chung (abstraction)** cho việc **đọc byte từ một nguồn**. `OutputStream` là mô hình chung cho việc **ghi byte đến một đích**.

```text
nguồn ──> InputStream ──> ứng dụng
ứng dụng ──> OutputStream ──> đích
```

Hai kiểu này là **lớp trừu tượng (abstract class)**: lớp nền dùng để mô tả hành vi chung chứ không đại diện sẵn cho một nguồn/đích cụ thể. **Quy tắc API (contract)** là hành vi mà mã gọi API có thể dựa vào; **lớp con (subclass)** cung cấp phần cài đặt cho nguồn/đích thật sự:

- `FileInputStream` / `FileOutputStream`: tệp.
- `ByteArrayInputStream` / `ByteArrayOutputStream`: vùng nhớ.
- luồng từ socket hoặc API khác: nguồn/đích khác nhưng vẫn dùng cùng quy tắc luồng byte.

Java cũng có ba **luồng chuẩn (standard stream)** gắn với tiến trình: `System.in` là `InputStream` cho đầu vào chuẩn, còn `System.out` và `System.err` là `PrintStream` cho đầu ra chuẩn và đầu ra lỗi chuẩn. `PrintStream` tiện cho `print/println/printf`, nhưng các phương thức ghi của nó không truyền `IOException` ra bên gọi; lỗi được giữ trong trạng thái lỗi và có thể kiểm tra bằng `checkError()`.

Nhờ đó mã xử lý byte có thể phụ thuộc vào `InputStream` thay vì phải biết dữ liệu đến từ tệp hay vùng nhớ.

Ví dụ ghi rồi đọc lại UTF-8 bằng luồng byte:

Ví dụ dùng tệp tạm làm nguồn/đích có kiểm soát. `Path` và `Files` chỉ là phần phụ trợ để dựng ví dụ ở thời điểm này; chương về hệ thống tệp phía sau sẽ giải thích chúng riêng.

```java
java.nio.file.Path temp = java.nio.file.Files.createTempFile("bytes-", ".bin");
byte[] expected = "Xin chào Java ☕"
    .getBytes(java.nio.charset.StandardCharsets.UTF_8);

try (java.io.OutputStream out =
         new java.io.FileOutputStream(temp.toFile())) {
    out.write(expected);
}

byte[] actual;
try (java.io.InputStream in =
         new java.io.FileInputStream(temp.toFile())) {
    actual = in.readAllBytes();
}

System.out.println(java.util.Arrays.equals(expected, actual)); // true
java.nio.file.Files.deleteIfExists(temp);
```

`readAllBytes()` làm ví dụ ngắn gọn, nhưng nó đưa toàn bộ phần dữ liệu còn lại vào bộ nhớ. Với dữ liệu lớn hoặc không biết trước kích thước, nên đọc tăng dần theo từng khối; chương I/O có bộ đệm sẽ giải thích kỹ hơn cách dùng bộ đệm để giảm số thao tác I/O bên dưới.

## <a id="standard-console-io">I/O chuẩn và Console</a>

Ba luồng `System.in`, `System.out` và `System.err` là các **luồng chuẩn của tiến trình**. Chúng cho chương trình một quy ước chung để nhận đầu vào và phát đầu ra mà không cần biết dữ liệu đang nối trực tiếp với terminal, được chuyển hướng từ tệp hay được một tiến trình khác nối qua pipe.

```text
đầu vào chuẩn      → System.in  → InputStream
đầu ra chuẩn       → System.out → PrintStream
đầu ra lỗi chuẩn   → System.err → PrintStream
```

Ví dụ khi chạy trực tiếp trong terminal, `System.in` có thể nhận dữ liệu người dùng gõ và `System.out` hiển thị ra màn hình. Nhưng **trình bao lệnh (shell)** có thể chuyển hướng chính chương trình đó:

```text
java App < input.txt > output.txt 2> error.txt
```

Lúc đó mã Java vẫn dùng `System.in/out/err`, nhưng nguồn/đích thật sự đã thay đổi. Vì vậy **I/O chuẩn không đồng nghĩa với I/O terminal**. Đây cũng là lý do mã thư viện thường không nên tự ý `close()` các luồng chuẩn: vòng đời của chúng thuộc tiến trình hoặc chương trình khởi chạy, và đóng `System.out` có thể làm những phần mã chạy sau không còn ghi đầu ra được nữa.

Khi ứng dụng thật sự cần tương tác với terminal, Java có `java.io.Console`:

```java
java.io.Console console = System.console();

if (console != null) {
    String name = console.readLine("Tên của bạn: ");
    char[] password = console.readPassword("Mật khẩu: ");

    if (name != null) {
        console.printf("Xin chào %s%n", name);
    }
    if (password != null) {
        java.util.Arrays.fill(password, '\0');
    }
}
```

`Console` cung cấp các thao tác hướng tới terminal như `readLine`, `printf`, `reader()`, `writer()` và `readPassword()`. `readPassword()` cố gắng không hiển thị lại ký tự ra terminal và trả `char[]` để bên gọi có thể xóa dữ liệu nhạy cảm khỏi mảng sau khi dùng. Cả `readLine(...)` và `readPassword(...)` đều có thể trả `null` khi đầu vào console đi tới cuối luồng (EOF), vì vậy mã không được dùng trực tiếp kết quả mà không kiểm tra.

Điểm quan trọng nhất là `System.console()` **có thể trả `null`**. Điều này thường xảy ra khi JVM không có console tương tác phù hợp, ví dụ một số IDE, trình chạy kiểm thử, dịch vụ/tiến trình nền hoặc khi các luồng chuẩn đang được nối pipe/chuyển hướng. Vì vậy mã cần phương án thay thế hoặc một mô hình đầu vào khác thay vì giả định `Console` luôn tồn tại.

`System.out`/`System.err` là `PrintStream`, còn `Console.writer()` trả `PrintWriter`. Hai nhóm API in văn bản này thuận tiện, nhưng chúng giữ lỗi I/O trong trạng thái lỗi thay vì ném `IOException` từ các phương thức `print/println/printf`; khi cần quan sát lỗi, dùng `checkError()` theo quy tắc tương ứng.

## <a id="read-contract">Quy tắc của read()</a>

Điểm dễ sai nhất với `InputStream` là hiểu sai giá trị trả về.

`read()` không trả về `byte`; nó trả về `int`:

- `0..255`: giá trị của byte vừa đọc.
- `-1`: kết thúc luồng (end-of-stream, EOF), nghĩa là không còn byte nào.

Kiểu `int` cần thiết để giữ được cả 256 giá trị byte không dấu và thêm giá trị đặc biệt `-1`.

```java
try (java.io.InputStream in =
         new java.io.ByteArrayInputStream(new byte[] {10, 20})) {

    int first = in.read();   // 10
    int second = in.read();  // 20
    int eof = in.read();     // -1
}
```

Với `read(byte[] buffer)`, giá trị trả về là **số byte thực sự đã đặt vào bộ đệm**, hoặc `-1` nếu luồng đã ở EOF và không đọc được byte nào. Mã phải dùng con số này; không được mặc định toàn bộ bộ đệm đều chứa dữ liệu mới.

```java
byte[] buffer = new byte[4096];
int count;

while ((count = in.read(buffer)) != -1) {
    consume(buffer, 0, count);
}
```

`consume(...)` trong ví dụ chỉ là **hàm minh họa do ứng dụng tự viết** để xử lý `count` byte vừa đọc; nó không phải API của JDK.

Nếu truyền một vùng có độ dài bằng 0 thì thao tác đọc theo khối có thể trả `0`; với vùng có độ dài dương, vòng lặp thông thường xử lý `> 0` hoặc `-1`.

Hai nhóm API khác cũng dễ bị hiểu sai:

- `available()` **không trả “tổng số byte còn lại”**. Nó chỉ ước lượng số byte có thể đọc hoặc bỏ qua mà không bị chặn tại thời điểm gọi và hoàn toàn có thể trả `0`. Không dùng nó để suy ra kích thước toàn bộ luồng hoặc cấp phát bộ đệm cho toàn bộ dữ liệu.
- `mark(...)` / `reset()` chỉ dùng được khi luồng hỗ trợ. Hãy kiểm tra `markSupported()` trước khi dựa vào khả năng quay lại vị trí đã đánh dấu; `InputStream` cơ sở mặc định không hỗ trợ `reset()`.

## <a id="partial-read-write">Đọc từng phần và quy tắc ghi</a>

Một lời gọi `read(buffer)` **không hứa sẽ lấp đầy bộ đệm**. Nó có thể trả về ít byte hơn kích thước mảng dù luồng chưa EOF. Điều này rất quan trọng với mạng, luồng nén, pipe và các nguồn có dữ liệu đến theo từng đợt.

Vì vậy nếu định dạng yêu cầu **đúng một số byte xác định**, mã phải tự lặp hoặc dùng API có quy tắc hoàn tất phù hợp. `readNBytes(...)` tự thực hiện nhiều lần đọc cho tới tối đa độ dài yêu cầu, nhưng nếu gặp EOF sớm nó vẫn có thể trả số lượng nhỏ hơn yêu cầu, nên mã phải kiểm tra giá trị trả về. `DataInput.readFully(...)`, được giới thiệu bên dưới cho dữ liệu nhị phân có cấu trúc, sẽ ném `EOFException` nếu không lấy đủ số byte cần thiết.

Một vòng lặp thủ công để yêu cầu đủ byte có dạng:

```java
byte[] header = new byte[8];
int offset = 0;

while (offset < header.length) {
    int count = in.read(header, offset, header.length - offset);
    if (count == -1) {
        throw new java.io.EOFException("Thiếu dữ liệu header");
    }
    offset += count;
}
```

Ở phía `OutputStream`, quy tắc khác với đọc theo khối. `write(byte[])` và `write(byte[], off, len)` có trách nhiệm ghi vùng byte được yêu cầu hoặc báo lỗi bằng ngoại lệ. Không nên áp mô hình “một lần ghi có thể báo đã ghi một phần” của `Channel` vào `OutputStream`; thao tác ghi từng phần theo số lượng trả về sẽ xuất hiện rõ hơn khi học channel của NIO.

`write(int value)` chỉ ghi 8 bit thấp của `value`, nên khi muốn ghi nhiều byte ta thường dùng **nạp chồng (overload)** nhận mảng. Nạp chồng nghĩa là nhiều phương thức cùng tên nhưng có danh sách tham số khác nhau.

## <a id="typed-binary-io">I/O nhị phân có cấu trúc với DataInput/DataOutput</a>

Luồng byte thô chỉ biết “đây là các byte”. Nhiều định dạng nhị phân lại cần quy tắc chặt chẽ hơn, ví dụ:

```text
4 byte  → số lượng bản ghi
8 byte  → dấu thời gian kiểu long
1 byte  → cờ boolean
N byte  → phần dữ liệu tiếp theo
```

`DataInput` và `DataOutput` là hai giao diện mô tả việc đọc/ghi các kiểu nguyên thủy Java theo một quy tắc nhị phân xác định. `DataInputStream` bọc một `InputStream` để cung cấp `DataInput`; `DataOutputStream` bọc một `OutputStream` để cung cấp `DataOutput`.

```text
InputStream  → DataInputStream  → readInt/readLong/readBoolean/...
OutputStream → DataOutputStream → writeInt/writeLong/writeBoolean/...
```

Ví dụ một định dạng nhỏ tự định nghĩa thứ tự trường là `int version` → `long id` → `boolean active`:

```java
byte[] encoded;

try (java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
     java.io.DataOutputStream out = new java.io.DataOutputStream(bytes)) {

    out.writeInt(1);
    out.writeLong(42L);
    out.writeBoolean(true);
    encoded = bytes.toByteArray();
}

try (java.io.DataInputStream in = new java.io.DataInputStream(
        new java.io.ByteArrayInputStream(encoded))) {

    int version = in.readInt();
    long id = in.readLong();
    boolean active = in.readBoolean();
}
```

Các phương thức số nguyên của `DataOutput` ghi byte theo **big-endian** (byte quan trọng hơn trước), và `DataInput` đọc lại theo cùng quy tắc. Nhưng API không tự biết “trường nào đứng trước trường nào”. **Bên ghi và bên đọc phải thống nhất chính xác về lược đồ và cách xác định ranh giới dữ liệu (schema/framing)**: thứ tự trường, kiểu trường, phiên bản, độ dài và nơi một bản ghi kết thúc. Nếu bên ghi ghi `long` trước nhưng bên đọc gọi `readInt()` trước, luồng vẫn chỉ là byte; Java không có siêu dữ liệu để tự sửa sai định dạng.

Với trường có độ dài cố định, `readFully(byte[])` hữu ích khi định dạng yêu cầu **đúng N byte**. Phương thức này tiếp tục đọc cho tới khi mảng được lấp đầy; nếu EOF xuất hiện trước khi đủ dữ liệu, nó ném `EOFException`:

```java
byte[] magic = new byte[4];
in.readFully(magic); // hoặc đủ 4 byte, hoặc EOFException
```

Các phương thức như `readInt()`, `readLong()` cũng ném `EOFException` nếu luồng kết thúc giữa giá trị nguyên thủy đang đọc. Điều này khác với `InputStream.read(...)`, nơi bên gọi nhận số byte thực tế và tự quyết định có cần đọc tiếp hay không.

Một định dạng nhị phân có bản ghi độ dài biến đổi thường phải có **quy tắc xác định ranh giới dữ liệu (framing)**, ví dụ ghi độ dài trước phần dữ liệu:

```java
byte[] payload = ...;
out.writeInt(payload.length);
out.write(payload);

int length = in.readInt();
if (length < 0 || length > 1_000_000) {
    throw new java.io.IOException("Độ dài payload không hợp lệ: " + length);
}
byte[] payloadRead = new byte[length];
in.readFully(payloadRead);
```

Việc kiểm tra độ dài trước khi cấp phát bộ nhớ rất quan trọng khi dữ liệu đến từ nguồn không tin cậy; một giá trị độ dài tùy ý không nên được phép ép chương trình cấp phát bộ nhớ không giới hạn.

`writeUTF(String)` / `readUTF()` là một trường hợp đặc biệt cần nhớ. Chúng **không dùng UTF-8 chuẩn như `StandardCharsets.UTF_8`**; chúng dùng định dạng **modified UTF-8** của `DataInput`/`DataOutput` và đặt độ dài không dấu 16 bit ở trước chuỗi. Vì vậy dạng mã hóa bị giới hạn khoảng 65.535 byte và không nên được coi là định dạng UTF-8 thông thường để trao đổi tùy ý với hệ thống khác. Nếu định dạng yêu cầu UTF-8 chuẩn, hãy tự mã hóa bằng `StandardCharsets.UTF_8` và thiết kế ranh giới/độ dài rõ ràng.

I/O nhị phân có cấu trúc cũng **khác tuần tự hóa đối tượng Java**. `DataInputStream`/`DataOutputStream` chỉ ghi các giá trị nguyên thủy/byte theo lược đồ do ứng dụng tự định nghĩa; chúng không tự ghi siêu dữ liệu lớp hay đồ thị đối tượng. Tuần tự hóa sẽ là một chương riêng ở phía sau.

## <a id="byte-stream-use-cases">Khi nào dùng luồng byte?</a>

Luồng byte phù hợp khi nội dung cần được giữ ở dạng byte hoặc khi tầng hiện tại chưa nên quyết định cách mã hóa văn bản:

- ảnh, audio, video;
- tệp ZIP, PDF hoặc định dạng nhị phân;
- dữ liệu đã mã hóa/nén;
- sao chép dữ liệu nguyên trạng;
- giao thức mà bố cục byte có ý nghĩa riêng.

Ví dụ sao chép dữ liệu mà không diễn giải nó là văn bản:

```java
try (java.io.InputStream in = source();
     java.io.OutputStream out = destination()) {
    in.transferTo(out);
}
```

`source()` và `destination()` ở đây là **hàm minh họa do ứng dụng tự cung cấp** để đại diện cho mã lấy luồng thật từ tệp, socket hoặc nơi khác; chúng không phải phương thức có sẵn trong JDK.

`transferTo` vẫn làm việc theo quy tắc của luồng byte. Nó tiện cho trường hợp chuyển toàn bộ phần dữ liệu còn lại từ một luồng sang luồng khác, nhưng không thay đổi quy tắc sở hữu tài nguyên: đoạn mã nào tạo/sở hữu các luồng vẫn phải quyết định khi nào đóng chúng.

Khi dữ liệu thật sự là văn bản, xử lý trực tiếp từng byte khiến mã phải tự lo giải mã và dễ cắt sai ranh giới ký tự nhiều byte. Chương tiếp theo thêm mô hình `Reader`/`Writer` để đưa bộ mã ký tự vào đúng vị trí.
