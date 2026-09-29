# Byte Stream

Sau chapter mở đầu, ta đã biết I/O là luồng dữ liệu giữa source và sink. Câu hỏi tiếp theo là: khi payload phải được giữ nguyên ở dạng nhị phân, Java biểu diễn luồng đó như thế nào?

## <a id="inputstream-outputstream">InputStream và OutputStream</a>

Trước hết, **stream** là cách nhìn dữ liệu như một dòng giá trị đi tuần tự từ nguồn tới chương trình hoặc từ chương trình tới đích. Stream không có nghĩa toàn bộ dữ liệu đã nằm sẵn trong RAM; code thường xử lý dần khi dữ liệu đi qua.

Đừng nhầm **I/O stream** ở `java.io` với **Stream API** ở `java.util.stream`. I/O stream là abstraction để **đọc/ghi dữ liệu từ source/sink**; `Stream<T>` của Stream API là pipeline xử lý các phần tử bằng `map`, `filter`, `reduce`... Hai khái niệm có cùng từ “stream” nhưng giải quyết hai bài toán khác nhau.

`InputStream` là **mô hình chung (abstraction)** cho việc **đọc byte từ một nguồn**. `OutputStream` là mô hình chung cho việc **ghi byte đến một đích**.

```text
source ──> InputStream ──> application
application ──> OutputStream ──> sink
```

Hai type này là **abstract class**: lớp nền dùng để mô tả hành vi chung chứ không đại diện sẵn cho một nguồn/đích cụ thể. **Contract** ở đây nghĩa là quy tắc mà code gọi API có thể dựa vào; **subclass** là lớp con cung cấp implementation cho nguồn/đích thật sự:

- `FileInputStream` / `FileOutputStream`: file.
- `ByteArrayInputStream` / `ByteArrayOutputStream`: vùng nhớ.
- stream từ socket hoặc API khác: nguồn/đích khác nhưng vẫn dùng cùng contract byte.

Java cũng có ba standard stream gắn với process: `System.in` là `InputStream` cho standard input; `System.out` và `System.err` là `PrintStream` cho standard output/error. `PrintStream` tiện cho `print/println/printf`, nhưng các method ghi của nó không ném `IOException` ra caller; lỗi được ghi vào error state và có thể kiểm tra bằng `checkError()`.

Nhờ đó code xử lý bytes có thể phụ thuộc vào `InputStream` thay vì phải biết dữ liệu đến từ file hay memory.

Ví dụ ghi rồi đọc lại UTF-8 bằng byte stream:

Ví dụ dùng file tạm làm source/sink có kiểm soát. `Path` và `Files` chỉ là scaffolding của ví dụ ở thời điểm này; chapter filesystem phía sau sẽ giải thích chúng riêng.

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

`readAllBytes()` làm ví dụ ngắn gọn, nhưng nó đưa toàn bộ phần dữ liệu còn lại vào bộ nhớ. Với dữ liệu lớn hoặc không biết trước kích thước, nên đọc tăng dần theo từng khối; chương Buffered I/O sẽ giải thích kỹ hơn cách dùng buffer để giảm chi phí I/O.

## <a id="standard-console-io">Standard I/O và Console</a>

Ba stream `System.in`, `System.out` và `System.err` là các **standard stream của process**. Chúng cho chương trình một convention chung để nhận input và phát output mà không phải biết dữ liệu đang nối trực tiếp với terminal, được redirect từ file, hay được một process khác pipe vào/ra.

```text
standard input   → System.in  → InputStream
standard output  → System.out → PrintStream
standard error   → System.err → PrintStream
```

Ví dụ khi chạy trực tiếp trong terminal, `System.in` có thể nhận dữ liệu người dùng gõ và `System.out` hiển thị ra màn hình. Nhưng shell có thể redirect cùng chương trình:

```text
java App < input.txt > output.txt 2> error.txt
```

Lúc đó code Java vẫn dùng `System.in/out/err`, nhưng source/sink thật sự đã thay đổi. Vì vậy **standard I/O không đồng nghĩa với terminal I/O**. Đây cũng là lý do library code thường không nên tự ý `close()` các standard stream: lifecycle của chúng thuộc process/launcher, và đóng `System.out` có thể làm những phần code chạy sau không còn ghi output được nữa.

Khi ứng dụng thật sự cần tương tác với terminal, Java có `java.io.Console`:

```java
java.io.Console console = System.console();

if (console != null) {
    String name = console.readLine("Tên của bạn: ");
    char[] password = console.readPassword("Mật khẩu: ");
    console.printf("Xin chào %s%n", name);

    java.util.Arrays.fill(password, '\0');
}
```

`Console` cung cấp các thao tác terminal-oriented như `readLine`, `printf`, `reader()`, `writer()` và `readPassword()`. `readPassword()` cố gắng không echo ký tự ra terminal và trả `char[]` để caller có thể xóa dữ liệu nhạy cảm khỏi mảng sau khi dùng.

Điểm quan trọng nhất là `System.console()` **có thể trả `null`**. Điều này thường xảy ra khi JVM không có console tương tác phù hợp, ví dụ một số IDE, test runner, service/background process hoặc khi standard streams đang được pipe/redirect. Vì vậy code cần fallback hoặc một input abstraction khác thay vì giả định `Console` luôn tồn tại.

`System.out`/`System.err` là `PrintStream`, còn `Console.writer()` trả `PrintWriter`. Hai nhóm print API thuận tiện cho text nhưng giữ lỗi I/O trong error state thay vì ném `IOException` từ các method `print/println/printf`; khi cần quan sát lỗi, dùng `checkError()` theo contract tương ứng.

## <a id="read-contract">Contract của read()</a>

Điểm dễ sai nhất với `InputStream` là hiểu sai giá trị trả về.

`read()` không trả về `byte`; nó trả về `int`:

- `0..255`: giá trị của byte vừa đọc.
- `-1`: end-of-stream (EOF), nghĩa là không còn byte nào.

Kiểu `int` cần thiết để giữ được cả 256 giá trị byte không dấu và thêm giá trị đặc biệt `-1`.

```java
try (java.io.InputStream in =
         new java.io.ByteArrayInputStream(new byte[] {10, 20})) {

    int first = in.read();   // 10
    int second = in.read();  // 20
    int eof = in.read();     // -1
}
```

Với `read(byte[] buffer)`, giá trị trả về là **số byte thực sự đã đặt vào buffer**, hoặc `-1` nếu stream đã ở EOF và không đọc được byte nào. Code phải dùng con số này; không được mặc định toàn bộ buffer đều chứa dữ liệu mới.

```java
byte[] buffer = new byte[4096];
int count;

while ((count = in.read(buffer)) != -1) {
    consume(buffer, 0, count);
}
```

`consume(...)` trong ví dụ chỉ là **hàm minh họa do ứng dụng tự viết** để xử lý `count` byte vừa đọc; nó không phải API của JDK.

Nếu truyền một vùng có độ dài bằng 0 thì bulk-read có thể trả `0`; với vùng có độ dài dương, vòng lặp thông thường xử lý `> 0` hoặc `-1`.

Hai nhóm API khác cũng dễ bị hiểu sai:

- `available()` **không trả “tổng số byte còn lại”**. Nó chỉ ước lượng số byte có thể đọc hoặc skip mà không block tại thời điểm gọi và hoàn toàn có thể trả `0`. Không dùng nó để suy ra kích thước toàn bộ stream hoặc allocate buffer cho toàn bộ payload.
- `mark(...)` / `reset()` chỉ dùng được khi stream hỗ trợ. Hãy kiểm tra `markSupported()` trước khi dựa vào khả năng quay lại vị trí đã đánh dấu; `InputStream` cơ sở mặc định không hỗ trợ reset.

## <a id="partial-read-write">Đọc từng phần và contract ghi</a>

Một lời gọi `read(buffer)` **không hứa sẽ lấp đầy buffer**. Nó có thể trả về ít byte hơn kích thước mảng dù stream chưa EOF. Điều này rất quan trọng với network, compressed stream, pipe và các nguồn có dữ liệu đến theo từng đợt.

Vì vậy muốn đọc đủ một số byte xác định, code phải lặp hoặc dùng API có contract phù hợp như `readNBytes`:

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

Ở phía `OutputStream`, contract khác với bulk read. `write(byte[])` và `write(byte[], off, len)` có trách nhiệm ghi vùng byte được yêu cầu hoặc báo lỗi bằng exception. Không nên áp mental model “một lần write có thể báo đã ghi một phần” của `Channel` vào `OutputStream`; partial write theo số lượng trả về sẽ xuất hiện rõ hơn khi học NIO channel.

`write(int value)` chỉ ghi 8 bit thấp của `value`, nên khi muốn ghi nhiều byte ta thường dùng **overload** nhận mảng. Overload nghĩa là nhiều method cùng tên nhưng có danh sách tham số khác nhau.

## <a id="typed-binary-io">Typed binary I/O với DataInput/DataOutput</a>

Raw byte stream chỉ biết “đây là các byte”. Nhiều binary format lại cần một contract mạnh hơn, ví dụ:

```text
4 bytes  → số lượng record
8 bytes  → timestamp long
1 byte   → boolean flag
N bytes  → payload tiếp theo
```

`DataInput` và `DataOutput` là hai interface mô tả việc đọc/ghi các kiểu primitive Java theo một binary contract xác định. `DataInputStream` bọc một `InputStream` để cung cấp `DataInput`; `DataOutputStream` bọc một `OutputStream` để cung cấp `DataOutput`.

```text
InputStream  → DataInputStream  → readInt/readLong/readBoolean/...
OutputStream → DataOutputStream → writeInt/writeLong/writeBoolean/...
```

Ví dụ một format nhỏ tự định nghĩa thứ tự field là `int version` → `long id` → `boolean active`:

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

Các method số nguyên của `DataOutput` ghi byte theo **big-endian** (byte quan trọng hơn trước), và `DataInput` đọc lại theo cùng contract. Nhưng API không tự biết “field nào đứng trước field nào”. **Writer và reader phải đồng ý chính xác về schema/framing**: thứ tự field, kiểu field, version, length và nơi một record kết thúc. Nếu writer ghi `long` trước nhưng reader gọi `readInt()` trước, stream vẫn chỉ là bytes; Java không có metadata để tự sửa sai format.

Với field có độ dài cố định, `readFully(byte[])` hữu ích khi format yêu cầu **đúng N byte**. Method này tiếp tục đọc cho tới khi mảng được lấp đầy; nếu EOF xuất hiện trước khi đủ dữ liệu, nó ném `EOFException`:

```java
byte[] magic = new byte[4];
in.readFully(magic); // hoặc đủ 4 byte, hoặc EOFException
```

Các method như `readInt()`, `readLong()` cũng ném `EOFException` nếu stream kết thúc giữa primitive đang đọc. Điều này khác với `InputStream.read(...)`, nơi caller nhận số byte thực tế và tự quyết định có cần đọc tiếp hay không.

Một binary format có record độ dài biến đổi thường phải có **framing**, ví dụ ghi length trước payload:

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

Việc validate length trước khi allocate rất quan trọng khi dữ liệu đến từ nguồn không tin cậy; một giá trị length tùy ý không nên được phép ép chương trình cấp phát bộ nhớ không giới hạn.

`writeUTF(String)` / `readUTF()` là một trường hợp đặc biệt cần nhớ. Chúng **không dùng UTF-8 chuẩn như `StandardCharsets.UTF_8`**; chúng dùng format **modified UTF-8** của `DataInput`/`DataOutput` và prefix chuỗi bằng độ dài unsigned 16-bit. Vì vậy encoded form bị giới hạn khoảng 65,535 byte và không nên được coi là format UTF-8 thông thường để trao đổi tùy ý với hệ thống khác. Nếu format yêu cầu UTF-8 chuẩn, hãy tự encode bằng `StandardCharsets.UTF_8` và thiết kế framing/length rõ ràng.

Typed binary I/O cũng **khác Java object serialization**. `DataInputStream`/`DataOutputStream` chỉ ghi các primitive/bytes theo schema do ứng dụng tự định nghĩa; chúng không tự ghi class metadata hay object graph. Serialization sẽ là một chapter riêng ở phía sau.

## <a id="byte-stream-use-cases">Khi nào dùng byte stream?</a>

Byte stream phù hợp khi nội dung cần được giữ ở dạng byte hoặc khi tầng hiện tại chưa nên quyết định encoding text:

- ảnh, audio, video;
- file ZIP, PDF hoặc format nhị phân;
- payload mã hóa/nén;
- copy dữ liệu nguyên trạng;
- giao thức mà byte layout có ý nghĩa riêng.

Ví dụ copy dữ liệu mà không diễn giải nó là text:

```java
try (java.io.InputStream in = source();
     java.io.OutputStream out = destination()) {
    in.transferTo(out);
}
```

`source()` và `destination()` ở đây là **hàm minh họa do ứng dụng tự cung cấp** để đại diện cho code lấy stream thật từ file, socket hoặc nơi khác; chúng không phải method có sẵn trong JDK.

`transferTo` vẫn làm việc theo contract byte stream. Nó tiện cho trường hợp chuyển toàn bộ phần dữ liệu còn lại từ một stream sang stream khác, nhưng không thay đổi quy tắc ownership: đoạn code nào tạo/sở hữu các stream vẫn phải quyết định khi nào đóng chúng.

Khi payload thật sự là text, xử lý trực tiếp từng byte khiến code phải tự lo decoding và dễ cắt sai ranh giới ký tự nhiều byte. Chapter tiếp theo thêm abstraction `Reader`/`Writer` để đưa charset vào đúng vị trí.
